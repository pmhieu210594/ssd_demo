package com.sdd.platform.application.usecase.docparse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.port.out.persistence.AcCoveragePort;
import com.sdd.platform.application.port.out.persistence.CiRunRepositoryPort;
import com.sdd.platform.application.port.out.persistence.DocParsePersistencePort;
import com.sdd.platform.application.port.out.persistence.TestEvidencePersistencePort;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseField;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseMode;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseRequest;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseResult;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseSnapshot;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseStatus;
import com.sdd.platform.application.usecase.ingestion.CiRunModels;
import com.sdd.platform.domain.service.ArtifactNormalizer;
import org.mockito.Mockito;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestResultsParseServiceTest {

    private TestResultsParseService service;
    private InMemoryDocParsePersistencePort repository;
    private AcCoveragePort acCoveragePort;
    private TestEvidencePersistencePort testEvidencePersistencePort;
    private CiRunRepositoryPort ciRunRepositoryPort;
    private UUID projectId;
    private UUID repositoryId;
    private UUID ticketId;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        repositoryId = UUID.randomUUID();
        ticketId = UUID.randomUUID();
        repository = new InMemoryDocParsePersistencePort();
        acCoveragePort = Mockito.mock(AcCoveragePort.class);
        Mockito.when(acCoveragePort.findActiveAcKeys(Mockito.any())).thenReturn(List.of());
        testEvidencePersistencePort = Mockito.mock(TestEvidencePersistencePort.class);
        Mockito.when(testEvidencePersistencePort.upsertTestRun(Mockito.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ciRunRepositoryPort = Mockito.mock(CiRunRepositoryPort.class);
        Mockito.when(ciRunRepositoryPort.findLatestCiRunByRepositoryAndTicket(Mockito.any(), Mockito.any()))
                .thenReturn(Optional.empty());
        Mockito.when(ciRunRepositoryPort.findLatestPullRequestByTicket(Mockito.any()))
                .thenReturn(Optional.empty());
        service = new TestResultsParseService(
                new ArtifactNormalizer(),
                repository,
                new ObjectMapper(),
                new TestCoverageValidationService(),
                acCoveragePort,
                testEvidencePersistencePort,
                ciRunRepositoryPort);
    }

    @Test
    void parse_success_persists_all_sections_and_allows_detail_lookup() {
        ParseResult result = service.parseAndStore(request(fullMarkdown()));

        assertEquals(ParseStatus.SUCCESS, result.parseStatus());
        assertNotNull(result.snapshot());
        assertEquals(10, result.fields().size());
        assertTrue(result.fields().stream().allMatch(ParseField::presentFlag));
        assertEquals(1, repository.snapshotCount());
        assertTrue(service.latestSnapshot(ticketId, ParseMode.DRAFT).isPresent());

        UUID snapshotId = result.snapshot().artifactSnapshotId();
        ParseResult detail = service.detail(snapshotId).orElseThrow();
        assertEquals(ParseStatus.SUCCESS, detail.parseStatus());
        assertEquals(10, detail.fields().size());
        assertEquals(result.sourceHash(), detail.sourceHash());
        Mockito.verify(testEvidencePersistencePort).upsertTestRun(Mockito.any());
    }

    @Test
    void parse_missing_required_section_returns_partial_and_records_missing_field() {
        ParseResult result = service.parseAndStore(request(missingSummaryMarkdown()));

        assertEquals(ParseStatus.PARTIAL, result.parseStatus());
        assertNotNull(result.snapshot());
        assertTrue(result.missingFields().contains("summary_of_results"));
        assertTrue(result.fields().stream()
                .anyMatch(f -> "summary_of_results".equals(f.sectionKey()) && !f.presentFlag()));
    }

    @Test
    void parse_duplicate_heading_returns_partial() {
        ParseResult result = service.parseAndStore(request(duplicateVerdictMarkdown()));

        assertEquals(ParseStatus.PARTIAL, result.parseStatus());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("duplicate")));
    }

    @Test
    void parse_coverage_uses_pass_list_only_not_summary_or_fail_sections() {
        Mockito.when(acCoveragePort.findActiveAcKeys(Mockito.any()))
                .thenReturn(List.of("AC-1"));

        ParseResult result = service.parseAndStore(request(markdownWithAcOnlyOutsidePasses()));

        assertEquals(ParseStatus.PARTIAL, result.parseStatus());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("AC_NOT_COVERED:AC-1")));
    }

    @Test
    void parse_not_found_persists_snapshot_for_ui_visibility() {
        ParseResult result = service.parseAndStore(request(null));

        assertEquals(ParseStatus.NOT_FOUND, result.parseStatus());
        assertNotNull(result.snapshot());
        assertTrue(result.fields().stream().allMatch(f -> !f.presentFlag()));
        assertEquals(1, repository.snapshotCount());
    }

    @Test
    void parse_same_hash_upserts_existing_snapshot_instead_of_duplication() {
        ParseResult first = service.parseAndStore(request(fullMarkdown()));
        ParseResult second = service.parseAndStore(request(fullMarkdown()));

        assertEquals(first.sourceHash(), second.sourceHash());
        assertEquals(1, repository.snapshotCount());
        assertEquals(first.snapshot().artifactSnapshotId(), second.snapshot().artifactSnapshotId());
    }

    @Test
    void parse_empty_source_returns_parse_error() {
        ParseResult result = service.parseAndStore(request(""));

        assertEquals(ParseStatus.PARSE_ERROR, result.parseStatus());
        assertNotNull(result.snapshot());
        assertTrue(result.fields().stream().allMatch(f -> !f.presentFlag()));
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    void parse_malformed_markdown_safe_summary_does_not_leak_content() {
        String malformed = "## broken\n%%% garbage %%%\n## ##\n## ";
        ParseResult result = service.parseAndStore(request(malformed));

        assertNotNull(result.parseStatus());
        assertNotNull(result.snapshot());
        result.warnings().forEach(w ->
                assertFalse(w.contains(malformed), "Warning must not echo raw source"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void parse_test_results_updates_test_case_statuses() {
        service.parseAndStore(request(markdownWithTcIds()));

        ArgumentCaptor<List<TestEvidencePersistencePort.TestCaseResultRecord>> captor =
                ArgumentCaptor.forClass(List.class);
        Mockito.verify(testEvidencePersistencePort).updateTestCaseResults(captor.capture());

        List<TestEvidencePersistencePort.TestCaseResultRecord> records = captor.getValue();
        assertEquals(3, records.size());

        TestEvidencePersistencePort.TestCaseResultRecord pass = records.stream()
                .filter(r -> "TC-1".equals(r.testCaseKey())).findFirst().orElseThrow();
        assertEquals("SUCCESS", pass.status());
        assertFalse(pass.failureSummary() != null && !pass.failureSummary().isBlank(),
                "Pass row must not have failure summary");

        TestEvidencePersistencePort.TestCaseResultRecord fail = records.stream()
                .filter(r -> "TC-2".equals(r.testCaseKey())).findFirst().orElseThrow();
        assertEquals("FAILED", fail.status());
        assertEquals("assertion failed", fail.failureSummary());

        TestEvidencePersistencePort.TestCaseResultRecord skipped = records.stream()
                .filter(r -> "TC-3".equals(r.testCaseKey())).findFirst().orElseThrow();
        assertEquals("SKIPPED", skipped.status());
    }

    private ParseRequest request(String sourceText) {
        return new ParseRequest(
                projectId, repositoryId, ticketId, ParseMode.DRAFT,
                "docs/changes/PARSE-TEST-PLAN-RESULTS/test-results.md",
                sourceText, "test-results-parser", "v1", "trace-1", "NOT_APPLICABLE");
    }

    private String fullMarkdown() {
        return """
                # Test Results

                ## 1. Execution Environment
                | item | value |
                |---|---|
                | env | local |
                | branch | feature/parse |

                ## 2. Executed Command
                | command | result | log/evidence | note |
                |---|---|---|---|
                | mvn test | PASS | console | unit only |

                ## 3. Summary of Results
                All unit tests passed. No integration tests run.

                ## 4. List of Passes
                | test | result | note |
                |---|---|---|
                | parse_success | PASS | |
                | parse_not_found | PASS | |

                ## 5. List of Fails
                | test | cause | action | status |
                |---|---|---|---|

                ## 6. Bugs Fixed
                | bug | fix | evidence |
                |---|---|---|

                ## 7. Not yet fixed / Pending
                None.

                ## 8. Test cannot be executed and reason
                | test/command | reason | risk | alternative evidence |
                |---|---|---|---|
                | DB integration | no Docker | low | unit test |

                ## 9. Remaining risk
                Integration tests not verified against real DB.

                ## 10. Final Test Verdict
                PASS
                """;
    }

    private String missingSummaryMarkdown() {
        return fullMarkdown().replace("""
                ## 3. Summary of Results
                All unit tests passed. No integration tests run.

                """, "");
    }

    private String duplicateVerdictMarkdown() {
        return fullMarkdown().replace("""
                ## 10. Final Test Verdict
                PASS
                """, """
                ## 10. Final Test Verdict
                PASS

                ## 10. Final Test Verdict
                Duplicate verdict.
                """);
    }

    private String markdownWithAcOnlyOutsidePasses() {
        return """
                # Test Results

                ## 1. Execution Environment
                | item | value |
                |---|---|
                | env | local |

                ## 2. Executed Command
                | command | result | log/evidence | note |
                |---|---|---|---|
                | mvn test | PASS | console | unit only |

                ## 3. Summary of Results
                AC-1 is mentioned here, but this section must not count for coverage.

                ## 4. List of Passes
                | test | result | note |
                |---|---|---|
                | parse_success | PASS | |

                ## 5. List of Fails
                | test | cause | action | status |
                |---|---|---|---|
                | parse_coverage | missing AC-1 | update matrix | FAIL |

                ## 6. Bugs Fixed
                None.

                ## 7. Not yet fixed / Pending
                None.

                ## 8. Test cannot be executed and reason
                None.

                ## 9. Remaining risk
                None.

                ## 10. Final Test Verdict
                PASS
                """;
    }

    private String markdownWithTcIds() {
        return """
                # Test Results

                ## 1. Execution Environment
                | item | value |
                |---|---|
                | env | local |

                ## 2. Executed Command
                | command | result | log/evidence | note |
                |---|---|---|---|
                | mvn test | PASS | console | unit only |

                ## 3. Summary of Results
                All tests done.

                ## 4. List of Passes
                | TC ID | test | result | note |
                |---|---|---|---|
                | TC-1 | parse_success | PASS | |

                ## 5. List of Fails
                | TC ID | test | cause | action | status |
                |---|---|---|---|---|
                | TC-2 | parse_fail | assertion failed | fix | FAIL |

                ## 6. Bugs Fixed
                None.

                ## 7. Not yet fixed / Pending
                None.

                ## 8. Test cannot be executed and reason
                | TC ID | test/command | reason | risk | alternative evidence |
                |---|---|---|---|---|
                | TC-3 | DB integration | no Docker | low | unit test |

                ## 9. Remaining risk
                None.

                ## 10. Final Test Verdict
                FAIL
                """;
    }

    private static final class InMemoryDocParsePersistencePort implements DocParsePersistencePort {
        private final Map<UUID, ParseSnapshot> snapshotsById = new LinkedHashMap<>();
        private final Map<String, ParseSnapshot> snapshotsByKey = new LinkedHashMap<>();
        private final Map<UUID, List<ParseField>> sectionsBySnapshotId = new LinkedHashMap<>();

        int snapshotCount() {
            return snapshotsById.size();
        }

        @Override
        public ParseSnapshot upsertSnapshot(ParseSnapshot snapshot) {
            String key = snapshot.repositoryId() + "|" + snapshot.sourcePath() + "|" + snapshot.contentHash();
            ParseSnapshot existing = snapshotsByKey.get(key);
            UUID snapshotId = existing == null ? UUID.randomUUID() : existing.artifactSnapshotId();
            ParseSnapshot saved = new ParseSnapshot(snapshotId, snapshot.ticketId(), snapshot.repositoryId(),
                    UUID.randomUUID(), UUID.randomUUID(), "TEST_RESULTS", "Test Results",
                    snapshot.parseMode(), snapshot.parseStatus(), snapshot.sourcePath(),
                    snapshot.contentHash(), snapshot.schemaVersion(), snapshot.schemaValid(),
                    snapshot.templateEmptyFlag(), snapshot.requiredFieldsMissing(),
                    snapshot.parsedSummaryJson(), snapshot.parserVersion(),
                    snapshot.collectedAt() == null ? OffsetDateTime.now(ZoneOffset.UTC) : snapshot.collectedAt());
            snapshotsByKey.put(key, saved);
            snapshotsById.put(snapshotId, saved);
            return saved;
        }

        @Override
        public void replaceSections(UUID snapshotId, UUID ticketId, List<ParseField> sections) {
            sectionsBySnapshotId.put(snapshotId, new ArrayList<>(sections.stream()
                    .map(f -> new ParseField(
                            f.parsedSectionId() == null ? UUID.randomUUID() : f.parsedSectionId(),
                            snapshotId, ticketId, f.sectionType(), f.sectionKey(),
                            f.sectionTextHash(), f.sectionSummary(), f.requiredFlag(),
                            f.presentFlag(), f.validFlag(), f.parseWarning()))
                    .toList()));
        }

        @Override
        public Optional<ParseSnapshot> findLatestSnapshot(UUID ticketId, String artifactTypeCode, ParseMode parseMode) {
            return snapshotsById.values().stream()
                    .filter(s -> s.ticketId().equals(ticketId))
                    .filter(s -> artifactTypeCode.equals(s.artifactTypeCode()))
                    .filter(s -> parseMode.name().equals(s.parseMode()))
                    .reduce((a, b) -> b);
        }

        @Override
        public Optional<ParseSnapshot> findSnapshotById(UUID snapshotId) {
            return Optional.ofNullable(snapshotsById.get(snapshotId));
        }

        @Override
        public List<ParseField> findSections(UUID snapshotId) {
            return sectionsBySnapshotId.getOrDefault(snapshotId, List.of());
        }

        @Override
        public List<ParseSnapshot> findSnapshots(UUID ticketId, String artifactTypeCode, ParseMode parseMode, int limit) {
            return snapshotsById.values().stream()
                    .filter(s -> s.ticketId().equals(ticketId))
                    .filter(s -> artifactTypeCode.equals(s.artifactTypeCode()))
                    .filter(s -> parseMode.name().equals(s.parseMode()))
                    .sorted((a, b) -> b.collectedAt().compareTo(a.collectedAt()))
                    .limit(limit).toList();
        }

        @Override
        public void persistEvidenceEvent(DocParseModels.ParseEvidenceEvent event) { /* no-op in unit tests */ }

        @Override
        public void persistDataQuality(DocParseModels.ParseDataQuality quality) { /* no-op in unit tests */ }
    }
}
