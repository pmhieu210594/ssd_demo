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
import com.sdd.platform.domain.service.ArtifactNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestPlanParseServiceTest {

    private TestPlanParseService service;
    private InMemoryDocParsePersistencePort repository;
    private InMemoryAcCoveragePort acCoveragePort;
    private TestEvidencePersistencePort testEvidencePersistencePort;
    private CiRunRepositoryPort ciRunRepositoryPort;
    private UUID projectId;
    private UUID repositoryId;
    private UUID ticketId;
    private TestCoverageValidationService coverageValidationService;

    @BeforeEach
    void setUp() {
        coverageValidationService = new TestCoverageValidationService();
        projectId = UUID.randomUUID();
        repositoryId = UUID.randomUUID();
        ticketId = UUID.randomUUID();
        repository = new InMemoryDocParsePersistencePort();
        acCoveragePort = new InMemoryAcCoveragePort();
        testEvidencePersistencePort = Mockito.mock(TestEvidencePersistencePort.class);
        ciRunRepositoryPort = Mockito.mock(CiRunRepositoryPort.class);
        Mockito.when(ciRunRepositoryPort.findLatestCiRunByRepositoryAndTicket(Mockito.any(), Mockito.any()))
                .thenReturn(Optional.empty());
        service = new TestPlanParseService(new ArtifactNormalizer(), repository, new ObjectMapper(),
                coverageValidationService, acCoveragePort, testEvidencePersistencePort, ciRunRepositoryPort);
    }

    @Test
    void parse_success_persists_all_sections_and_allows_detail_lookup() {
        ParseResult result = service.parseAndStore(request(fullMarkdown()));

        assertEquals(ParseStatus.SUCCESS, result.parseStatus());
        assertNotNull(result.snapshot());
        assertEquals(11, result.fields().size());
        assertTrue(result.fields().stream().allMatch(ParseField::presentFlag));
        assertEquals(1, repository.snapshotCount());
        assertTrue(service.latestSnapshot(ticketId, ParseMode.DRAFT).isPresent());

        UUID snapshotId = result.snapshot().artifactSnapshotId();
        ParseResult detail = service.detail(snapshotId).orElseThrow();
        assertEquals(ParseStatus.SUCCESS, detail.parseStatus());
        assertEquals(11, detail.fields().size());
        assertEquals(result.sourceHash(), detail.sourceHash());
        Mockito.verify(testEvidencePersistencePort).replacePlannedCoverage(
                Mockito.any(),
                Mockito.eq(ticketId),
                Mockito.any(),
                Mockito.argThat(list -> list.contains("AC-1")));
    }

    @Test
    void parse_missing_required_section_returns_partial_and_records_missing_field() {
        ParseResult result = service.parseAndStore(request(missingPurposeMarkdown()));

        assertEquals(ParseStatus.PARTIAL, result.parseStatus());
        assertNotNull(result.snapshot());
        assertTrue(result.missingFields().contains("purpose"));
        assertTrue(result.fields().stream()
                .anyMatch(f -> "purpose".equals(f.sectionKey()) && !f.presentFlag()));
    }

    @Test
    void parse_duplicate_heading_returns_partial() {
        ParseResult result = service.parseAndStore(request(duplicatePriorityMarkdown()));

        assertEquals(ParseStatus.PARTIAL, result.parseStatus());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("duplicate")));
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
        result.warnings().forEach(w -> assertFalse(w.contains(malformed), "Warning must not echo raw source"));
    }

    private ParseRequest request(String sourceText) {
        return new ParseRequest(
                projectId, repositoryId, ticketId, ParseMode.DRAFT,
                "docs/changes/PARSE-TEST-PLAN-RESULTS/test-plan.md",
                sourceText, "test-plan-parser", "v1", "trace-1", "NOT_APPLICABLE");
    }

    private String fullMarkdown() {
        return """
                # Test Plan

                ## 1. Purpose
                Verify parsing of test-plan.md.

                ## 2. AC Matrix ↔ Test Type
                | AC ID | BE UT |
                |---|---|
                | AC-1 | ✅ |

                ## 3. Priority
                | test item | priority |
                |---|---|
                | Parse | P0 |

                ## 4. Reuse Existing Test
                | existing test | path | covers | gap |
                |---|---|---|---|

                ## 5. Additional Test This Time
                | TC ID | test | type | target | related AC |
                |---|---|---|---|---|
                | TC-1 | Parse test | BE UT | fields | AC-1 |

                ### E2E Step-by-step Scenarios
                | scenario | precondition | steps | expected |
                |---|---|---|---|
                | View pair | snapshot exists | open ticket | both tabs |

                ## 6. Areas intentionally left untested this time
                | area | reason | risk |
                |---|---|---|
                | Raw editor | out of scope | low |

                ## 7. Data testing principles
                Use dummy data only.

                ## 8. Execution command
                | command | purpose |
                |---|---|
                | mvn test | Run tests |

                ## 9. Stop Condition
                Stop if mapping is ambiguous.

                ## 10. Required Human Decision
                Confirm artifact tables.
                """;
    }

    private String missingPurposeMarkdown() {
        return fullMarkdown().replace("""
                ## 1. Purpose
                Verify parsing of test-plan.md.

                """, "");
    }

    private String duplicatePriorityMarkdown() {
        return fullMarkdown().replace("""
                ## 3. Priority
                | test item | priority |
                |---|---|
                | Parse | P0 |
                """, """
                ## 3. Priority
                | test item | priority |
                |---|---|
                | Parse | P0 |

                ## 3. Priority
                Duplicate section.
                """);
    }

    private static final class InMemoryDocParsePersistencePort implements DocParsePersistencePort {
        private final Map<UUID, ParseSnapshot> snapshotsById = new LinkedHashMap<>();
        private final Map<String, ParseSnapshot> snapshotsByKey = new LinkedHashMap<>();
        private final Map<UUID, List<ParseField>> sectionsBySnapshotId = new LinkedHashMap<>();
        final List<DocParseModels.ParseDataQuality> dataQualityWrites = new ArrayList<>();

        int snapshotCount() {
            return snapshotsById.size();
        }

        @Override
        public ParseSnapshot upsertSnapshot(ParseSnapshot snapshot) {
            String key = snapshot.repositoryId() + "|" + snapshot.sourcePath() + "|" + snapshot.contentHash();
            ParseSnapshot existing = snapshotsByKey.get(key);
            UUID snapshotId = existing == null ? UUID.randomUUID() : existing.artifactSnapshotId();
            ParseSnapshot saved = new ParseSnapshot(snapshotId, snapshot.ticketId(), snapshot.repositoryId(),
                    UUID.randomUUID(), UUID.randomUUID(), "TEST_PLAN", "Test Plan",
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
        public List<ParseSnapshot> findSnapshots(UUID ticketId, String artifactTypeCode, ParseMode parseMode,
                int limit) {
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
        public void persistDataQuality(DocParseModels.ParseDataQuality quality) {
            dataQualityWrites.add(quality);
        }
    }

    private static final class InMemoryAcCoveragePort implements AcCoveragePort {
        private List<String> acKeys = new ArrayList<>();

        void setAcKeys(String... keys) {
            this.acKeys = List.of(keys);
        }

        @Override
        public List<String> findActiveAcKeys(UUID ticketId) {
            return acKeys;
        }
    }

    @Test
    void coverage_partial_when_specpack_ac_not_in_matrix() {
        acCoveragePort.setAcKeys("AC-1", "AC-2");

        ParseResult result = service.parseAndStore(request(fullMarkdown()));

        assertEquals(ParseStatus.PARTIAL, result.parseStatus());
        assertTrue(result.warnings().stream().anyMatch(w -> w.equals("AC_NOT_COVERED:AC-2")));
        assertFalse(result.warnings().stream().anyMatch(w -> w.equals("AC_NOT_COVERED:AC-1")));
    }

    @Test
    void coverage_success_when_all_specpack_acs_present_in_matrix() {
        acCoveragePort.setAcKeys("AC-1");

        ParseResult result = service.parseAndStore(request(fullMarkdown()));

        assertEquals(ParseStatus.SUCCESS, result.parseStatus());
        assertTrue(result.warnings().stream().noneMatch(w -> w.startsWith("AC_NOT_COVERED")));
        assertTrue(result.warnings().stream().noneMatch(w -> w.startsWith("UNKNOWN_AC_REFERENCE")));
    }

    @Test
    void coverage_partial_when_matrix_references_ac_not_in_specpack() {
        acCoveragePort.setAcKeys("AC-99");

        ParseResult result = service.parseAndStore(request(fullMarkdown()));

        assertEquals(ParseStatus.PARTIAL, result.parseStatus());
        assertTrue(result.warnings().stream().anyMatch(w -> w.equals("UNKNOWN_AC_REFERENCE:AC-1")));
    }

    @Test
    void coverage_skipped_when_specpack_has_no_acs_yet() {
        // acCoveragePort returns empty list (default) — spec-pack not yet parsed
        ParseResult result = service.parseAndStore(request(fullMarkdown()));

        assertEquals(ParseStatus.SUCCESS, result.parseStatus());
        assertTrue(result.warnings().stream().noneMatch(w -> w.startsWith("AC_NOT_COVERED")));
    }

    @Test
    void coverage_violations_trigger_data_quality_write_with_schema_violation_count() {
        acCoveragePort.setAcKeys("AC-1", "AC-2");

        service.parseAndStore(request(fullMarkdown()));

        assertEquals(1, repository.dataQualityWrites.size());
        DocParseModels.ParseDataQuality dq = repository.dataQualityWrites.get(0);
        assertTrue(dq.schemaViolationCount() > 0);
        assertTrue(dq.errorSummary().contains("AC_NOT_COVERED:AC-2"));
    }

    @Test
    void planned_test_cases_seeded_from_section5_with_tc_id() {
        service.parseAndStore(request(fullMarkdown()));

        Mockito.verify(testEvidencePersistencePort).upsertPlannedTestCases(
                Mockito.argThat(list -> list.size() == 1
                        && "TC-1".equals(list.get(0).testCaseKey())
                        && "Parse test".equals(list.get(0).testCaseName())));
        Mockito.verify(testEvidencePersistencePort).upsertTestCaseAcMappings(
                Mockito.argThat(list -> list.size() == 1
                        && "AC-1".equals(list.get(0).acKey())));
    }
}
