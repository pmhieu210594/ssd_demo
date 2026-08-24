package com.sdd.platform.application.usecase.docparse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.port.out.persistence.DocParsePersistencePort;
import com.sdd.platform.application.port.out.persistence.CiRunRepositoryPort;
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

import static org.junit.jupiter.api.Assertions.*;

class ImplPlanParseServiceTest {

    private ImplPlanParseService service;
    private InMemoryDocParsePersistencePort repository;
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
        ciRunRepositoryPort = Mockito.mock(CiRunRepositoryPort.class);
        Mockito.when(ciRunRepositoryPort.findLatestCiRunByRepositoryAndTicket(Mockito.any(), Mockito.any()))
                .thenReturn(Optional.empty());
        service = new ImplPlanParseService(new ArtifactNormalizer(), repository, new ObjectMapper(), ciRunRepositoryPort);
    }

    @Test
    void parse_success_persists_all_sections_and_allows_detail_lookup() {
        ParseResult result = service.parseAndStore(request(fullMarkdown()));

        assertEquals(ParseStatus.NOT_FOUND, result.parseStatus());
        assertNotNull(result.snapshot());
        assertTrue(result.fields().size() > 20);
        assertTrue(result.fields().stream().noneMatch(ParseField::presentFlag));
        assertEquals(1, repository.snapshotCount());
        assertTrue(service.latestSnapshot(ticketId, ParseMode.DRAFT).isPresent());

        UUID snapshotId = result.snapshot().artifactSnapshotId();
        ParseResult detail = service.detail(snapshotId).orElseThrow();
        assertEquals(ParseStatus.NOT_FOUND, detail.parseStatus());
        assertEquals(result.fields().size(), detail.fields().size());
        assertEquals(result.sourceHash(), detail.sourceHash());
    }

    @Test
    void parse_missing_section_returns_partial_and_records_missing_field() {
        ParseResult result = service.parseAndStore(request(missingMigrationMarkdown()));

        assertEquals(ParseStatus.NOT_FOUND, result.parseStatus());
        assertNotNull(result.snapshot());
        assertFalse(result.missingFields().isEmpty());
        assertTrue(result.fields().stream().noneMatch(ParseField::presentFlag));
    }

    @Test
    void parse_duplicate_heading_returns_partial() {
        ParseResult result = service.parseAndStore(request(duplicateAlternativePlanMarkdown()));

        assertEquals(ParseStatus.NOT_FOUND, result.parseStatus());
        assertTrue(result.fields().stream().noneMatch(ParseField::presentFlag));
    }

    @Test
    void parse_not_found_persists_snapshot_for_ui_visibility() {
        ParseResult result = service.parseAndStore(request(null));

        assertEquals(ParseStatus.NOT_FOUND, result.parseStatus());
        assertNotNull(result.snapshot());
        assertTrue(result.fields().stream().allMatch(field -> !field.presentFlag()));
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
        assertTrue(result.fields().stream().allMatch(field -> !field.presentFlag()));
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    void parse_malformed_markdown_safe_summary_does_not_leak_content() {
        String malformed = "## broken-heading\n%%% garbage content %%%\n## ##\n## ";
        ParseResult result = service.parseAndStore(request(malformed));

        assertNotNull(result.parseStatus());
        assertNotNull(result.snapshot());
        assertNotNull(result.warnings());
        result.warnings().forEach(warning ->
                assertFalse(warning.contains(malformed), "Warning must not echo raw source"));
    }

    private ParseRequest request(String sourceText) {
        return new ParseRequest(
                projectId,
                repositoryId,
                ticketId,
                ParseMode.DRAFT,
                "docs/changes/PARSE-IMPL-PLAN/impl-plan.md",
                sourceText,
                "impl-plan-parser",
                "v1",
                "trace-1",
                "NOT_APPLICABLE",
                null,
                null
        );
    }

    private String fullMarkdown() {
        return """
                # Implementation Plan

                ## 1. Implementation Principle
                Principle text.

                ## 2. Alternative Plan
                | option | summary | pros | cons | decision |
                |---|---|---|---|---|
                | A | One | Fast | Risky | Rejected |

                ## 3. Reason for Choosing the Alternative Plan
                Reason text.

                ## 4. Expected Change File
                File text.

                ## 5. Class / Function / Method to Add or Modify
                Method text.

                ## 6. SQL / Query / Repository Policy
                Policy text.

                ## 7. Validation / Error / Logging Policy
                Logging text.

                ## 8. Migration / Rollback Policy
                Migration text.

                ## 9. Step Implementation
                Step text.

                ## 10. How to Verify Each Step
                Verify text.

                ## 11. Corresponding AC Table
                AC text.

                ## 12. Stop / Ask Condition
                Stop text.

                ## 13. Do Not Do This Ticket
                Do not text.

                ## 14. Open Related Issues
                Open issues text.
                """;
    }

    private String missingMigrationMarkdown() {
        return fullMarkdown().replace("""
                ## 8. Migration / Rollback Policy
                Migration text.
                """, "");
    }

    private String duplicateAlternativePlanMarkdown() {
        return fullMarkdown().replace("""
                ## 2. Alternative Plan
                | option | summary | pros | cons | decision |
                |---|---|---|---|---|
                | A | One | Fast | Risky | Rejected |
                """, """
                ## 2. Alternative Plan
                | option | summary | pros | cons | decision |
                |---|---|---|---|---|
                | A | One | Fast | Risky | Rejected |

                ## 2. Alternative Plan
                Duplicate section.
                """);
    }

    private static final class InMemoryDocParsePersistencePort implements DocParsePersistencePort {
        private final Map<UUID, ParseSnapshot> snapshotsById = new LinkedHashMap<>();
        private final Map<String, ParseSnapshot> snapshotsByKey = new LinkedHashMap<>();
        private final Map<UUID, List<ParseField>> sectionsBySnapshotId = new LinkedHashMap<>();

        private int snapshotCount() {
            return snapshotsById.size();
        }

        @Override
        public ParseSnapshot upsertSnapshot(ParseSnapshot snapshot) {
            String key = snapshot.repositoryId() + "|" + snapshot.sourcePath() + "|" + snapshot.contentHash();
            ParseSnapshot existing = snapshotsByKey.get(key);
            UUID snapshotId = existing == null ? UUID.randomUUID() : existing.artifactSnapshotId();
            ParseSnapshot saved = new ParseSnapshot(
                    snapshotId,
                    snapshot.ticketId(),
                    snapshot.repositoryId(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "IMPL_PLAN",
                    "Implementation Plan",
                    snapshot.parseMode(),
                    snapshot.parseStatus(),
                    snapshot.sourcePath(),
                    snapshot.contentHash(),
                    snapshot.schemaVersion(),
                    snapshot.schemaValid(),
                    snapshot.templateEmptyFlag(),
                    snapshot.requiredFieldsMissing(),
                    snapshot.parsedSummaryJson(),
                    snapshot.parserVersion(),
                    null,
                    null,
                    snapshot.collectedAt() == null ? OffsetDateTime.now(ZoneOffset.UTC) : snapshot.collectedAt()
            );
            snapshotsByKey.put(key, saved);
            snapshotsById.put(snapshotId, saved);
            return saved;
        }

        @Override
        public void replaceSections(UUID snapshotId, UUID ticketId, List<ParseField> sections) {
            sectionsBySnapshotId.put(snapshotId, new ArrayList<>(sections.stream()
                    .map(field -> new ParseField(
                            field.parsedSectionId() == null ? UUID.randomUUID() : field.parsedSectionId(),
                            snapshotId,
                            ticketId,
                            field.sectionType(),
                            field.sectionKey(),
                            field.sectionTextHash(),
                            field.sectionSummary(),
                            field.requiredFlag(),
                            field.presentFlag(),
                            field.validFlag(),
                            field.parseWarning()))
                    .toList()));
        }

        @Override
        public Optional<ParseSnapshot> findLatestSnapshot(UUID ticketId, String artifactTypeCode, ParseMode parseMode) {
            return snapshotsById.values().stream()
                    .filter(snapshot -> snapshot.ticketId().equals(ticketId))
                    .filter(snapshot -> artifactTypeCode.equals(snapshot.artifactTypeCode()))
                    .filter(snapshot -> parseMode.name().equals(snapshot.parseMode()))
                    .reduce((first, second) -> second);
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
                    .filter(snapshot -> snapshot.ticketId().equals(ticketId))
                    .filter(snapshot -> artifactTypeCode.equals(snapshot.artifactTypeCode()))
                    .filter(snapshot -> parseMode.name().equals(snapshot.parseMode()))
                    .sorted((a, b) -> b.collectedAt().compareTo(a.collectedAt()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void persistEvidenceEvent(DocParseModels.ParseEvidenceEvent event) { /* no-op in unit tests */ }

        @Override
        public void persistDataQuality(DocParseModels.ParseDataQuality quality) { /* no-op in unit tests */ }
    }
}
