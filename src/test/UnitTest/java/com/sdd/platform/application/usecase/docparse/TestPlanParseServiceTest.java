package com.sdd.platform.application.usecase.docparse;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        testEvidencePersistencePort = Mockito.mock(TestEvidencePersistencePort.class);
        ciRunRepositoryPort = Mockito.mock(CiRunRepositoryPort.class);
        Mockito.when(ciRunRepositoryPort.findLatestCiRunByRepositoryAndTicket(Mockito.any(), Mockito.any()))
                .thenReturn(Optional.empty());
        service = new TestPlanParseService(new ArtifactNormalizer(), repository, new ObjectMapper(),
                testEvidencePersistencePort, ciRunRepositoryPort);
    }

    @Test
    void parse_success_persists_all_sections_and_allows_detail_lookup() {
        ParseResult result = service.parseAndStore(request(fullMarkdown()));

        assertEquals(ParseStatus.SUCCESS, result.parseStatus());
        assertNotNull(result.snapshot());
        assertEquals(7, result.fields().size());
        assertTrue(result.fields().stream().allMatch(ParseField::presentFlag));
        assertEquals(1, repository.snapshotCount());
        assertTrue(service.latestSnapshot(ticketId, ParseMode.DRAFT).isPresent());

        UUID snapshotId = result.snapshot().artifactSnapshotId();
        ParseResult detail = service.detail(snapshotId).orElseThrow();
        assertEquals(ParseStatus.SUCCESS, detail.parseStatus());
        assertEquals(7, detail.fields().size());
        assertEquals(result.sourceHash(), detail.sourceHash());
        Mockito.verify(testEvidencePersistencePort).replacePlannedCoverage(
                Mockito.any(),
                Mockito.eq(ticketId),
                Mockito.any(),
                Mockito.argThat(list -> list.contains("AC-1")));
    }

    @Test
    void parse_missing_required_section_returns_partial_and_records_missing_field() {
        ParseResult result = service.parseAndStore(request(missingBeUnitTestMarkdown()));

        assertEquals(ParseStatus.PARTIAL, result.parseStatus());
        assertNotNull(result.snapshot());
        assertTrue(result.missingFields().contains("be_unit_test"));
        assertTrue(result.fields().stream()
                .anyMatch(f -> "be_unit_test".equals(f.sectionKey()) && !f.presentFlag()));
    }

    @Test
    void parse_duplicate_heading_returns_partial() {
        ParseResult result = service.parseAndStore(request(duplicateBeUnitTestMarkdown()));

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
                sourceText, "test-plan-parser", "v1", "trace-1", "NOT_APPLICABLE", null, null);
    }

    private String fullMarkdown() {
        return """
                # Kế hoạch kiểm thử — TICKET-1 (Feature)

                - **Ticket:** TICKET-1
                - **Trạng thái:** Draft
                - **Tạo ngày:** 2026-08-22

                > Mỗi AC phải được bao phủ bởi ít nhất một loại kiểm thử.

                ## 1. Ma trận bao phủ

                | #   | AC   | FE UT | BE UT | API IT | E2E | Black-box |
                |---|---|---|---|---|---|---|
                | 1   | AC-1 | ✅ | ✅ |  |  |  |

                ## 2. Unit test FE

                | #   | Tệp kiểm thử | Nội dung kiểm thử | AC  |
                |---|---|---|---|
                | 1   | Foo.test.tsx | Render component | AC-1 |

                ## 3. Unit test BE

                | #   | Lớp kiểm thử | Nội dung kiểm thử | AC  |
                |---|---|---|---|
                | 1   | FooServiceTest | Verify parse logic | AC-1 |

                ## 4. Integration test API

                | #   | Endpoint | Kịch bản | AC  |
                |---|---|---|---|
                | 1   | GET /api/foo | Trả về danh sách | AC-1 |

                ## 5. Kiểm thử E2E (Playwright)

                | #   | Kịch bản | Các bước | Kết quả mong đợi | AC  |
                |---|---|---|---|---|
                | 1   | Luồng chính | Mở trang, click nút | Hiển thị kết quả | AC-1 |

                ## 6. Các lệnh chạy kiểm thử

                ```bash
                mvn test
                ```

                ## 7. Ghi chú / Ràng buộc

                Không có ràng buộc đặc biệt.
                """;
    }

    private String missingBeUnitTestMarkdown() {
        return fullMarkdown().replace("""
                ## 3. Unit test BE

                | #   | Lớp kiểm thử | Nội dung kiểm thử | AC  |
                |---|---|---|---|
                | 1   | FooServiceTest | Verify parse logic | AC-1 |

                """, "");
    }

    private String duplicateBeUnitTestMarkdown() {
        return fullMarkdown().replace("""
                ## 3. Unit test BE

                | #   | Lớp kiểm thử | Nội dung kiểm thử | AC  |
                |---|---|---|---|
                | 1   | FooServiceTest | Verify parse logic | AC-1 |
                """, """
                ## 3. Unit test BE

                | #   | Lớp kiểm thử | Nội dung kiểm thử | AC  |
                |---|---|---|---|
                | 1   | FooServiceTest | Verify parse logic | AC-1 |

                ## 3. Unit test BE
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
                    snapshot.parsedSummaryJson(), snapshot.parserVersion(), null, null,
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

    @Test
    void planned_test_cases_seeded_from_fe_be_api_sections_with_synthesized_tc_id() {
        service.parseAndStore(request(fullMarkdown()));

        Mockito.verify(testEvidencePersistencePort).upsertPlannedTestCases(
                Mockito.argThat(list -> list.size() == 3
                        && list.stream().anyMatch(tc -> "TC-FE-1".equals(tc.testCaseKey())
                                && "Render component".equals(tc.testCaseName()))
                        && list.stream().anyMatch(tc -> "TC-BE-1".equals(tc.testCaseKey())
                                && "Verify parse logic".equals(tc.testCaseName()))
                        && list.stream().anyMatch(tc -> "TC-API-1".equals(tc.testCaseKey())
                                && "Trả về danh sách".equals(tc.testCaseName()))));
        Mockito.verify(testEvidencePersistencePort).upsertTestCaseAcMappings(
                Mockito.argThat(list -> list.size() == 3
                        && list.stream().allMatch(m -> "AC-1".equals(m.acKey()))));
    }
}
