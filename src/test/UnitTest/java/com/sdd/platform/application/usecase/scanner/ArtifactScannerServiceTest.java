package com.sdd.platform.application.usecase.scanner;

import com.sdd.platform.application.port.out.integration.ArtifactScannerSourcePort;
import com.sdd.platform.application.port.out.persistence.ArtifactScannerPersistencePort;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactScanMode;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactScanRequest;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactScanTriggerType;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactSnapshot;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactTypeScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ConnectorScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.RepositoryScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ScanRun;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.TicketArtifactEvidence;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.TicketScope;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreService;
import com.sdd.platform.application.usecase.phase.TicketPhaseEvaluatorService;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.service.markdown.report.ReportMarkdownParser;
import com.sdd.platform.domain.service.markdown.reviewchecklist.ReviewChecklistMarkdownParser;
import com.sdd.platform.domain.service.markdown.specpack.SpecPackMarkdownParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ArtifactScannerServiceTest {

        private static final OffsetDateTime COMMITTED_AT = OffsetDateTime.parse("2026-06-16T06:42:28Z");

        private InMemoryArtifactScannerPersistencePort persistence;
        private FakeGitHubArtifactScannerSource source;
        private ArtifactScannerService service;
        private UUID repositoryId;
        private UUID projectId;
        private UUID ticketId;

        @BeforeEach
        void setUp() {
                repositoryId = UUID.randomUUID();
                projectId = UUID.randomUUID();
                ticketId = UUID.randomUUID();

                persistence = new InMemoryArtifactScannerPersistencePort(
                                new RepositoryScope(repositoryId, projectId, "nktrung/Demo-Project", "main"),
                                List.of(new TicketScope(ticketId, "ARTIFACT-SCANNER")),
                                artifactTypes());
                source = new FakeGitHubArtifactScannerSource("0123456789abcdef0123456789abcdef01234567", COMMITTED_AT);
                service = new ArtifactScannerService(source, persistence);
        }

        @Test
        void full_scan_creates_snapshots_and_detects_hash_changes() {
                putFullArtifactSet("spec v1");

                ScanRun first = service.scan(request(ArtifactScanMode.FULL, null));

                assertEquals("SUCCESS", first.status());
                assertEquals(15, first.recordsWritten());
                List<ArtifactSnapshot> firstArtifacts = service.getRunArtifacts(first.connectorRunId());
                assertEquals(15, firstArtifacts.size());

                ArtifactSnapshot specV1 = firstArtifacts.stream()
                                .filter(item -> "spec-pack.md".equals(item.defaultFileName()))
                                .findFirst()
                                .orElseThrow();
                assertTrue(specV1.needParse());
                assertEquals("FOUND", specV1.scanStatus());
                assertEquals(1, specV1.schemaVersion());

                putChangeArtifact("spec v2");
                ScanRun second = service.scan(request(ArtifactScanMode.FULL, null));

                assertEquals("SUCCESS", second.status());
                assertEquals(15, second.recordsWritten());
                List<ArtifactSnapshot> secondArtifacts = service.getRunArtifacts(second.connectorRunId());
                assertEquals(15, secondArtifacts.size());

                ArtifactSnapshot specV2 = secondArtifacts.stream()
                                .filter(item -> "spec-pack.md".equals(item.defaultFileName()))
                                .findFirst()
                                .orElseThrow();
                assertTrue(specV2.needParse());
                assertEquals(2, specV2.schemaVersion());

                ArtifactSnapshot impl = secondArtifacts.stream()
                                .filter(item -> "impl-plan.md".equals(item.defaultFileName()))
                                .findFirst()
                                .orElseThrow();
                assertFalse(impl.needParse());
                assertNotEquals(specV1.contentHash(), specV2.contentHash());
        }

        @Test
        void full_scan_marks_missing_required_artifacts_and_optional_phase0_as_skipped() {
                source.put("docs/changes/ARTIFACT-SCANNER/spec-pack.md", "spec only");

                ScanRun run = service.scan(request(ArtifactScanMode.FULL, null));

                assertEquals("SUCCESS", run.status());
                assertEquals(15, run.recordsWritten());

                List<ArtifactSnapshot> artifacts = service.getRunArtifacts(run.connectorRunId());
                ArtifactSnapshot missingImpl = artifacts.stream()
                                .filter(item -> "impl-plan.md".equals(item.defaultFileName()))
                                .findFirst()
                                .orElseThrow();
                assertFalse(missingImpl.existsFlag());
                assertEquals("MISSING", missingImpl.scanStatus());
                assertEquals("REQUIRED_ARTIFACT_MISSING", missingImpl.scanMessage());
                assertFalse(missingImpl.needParse());
                assertNull(missingImpl.contentHash());
                assertNull(missingImpl.sourceUpdatedAt());

                ArtifactSnapshot missingPhase0 = artifacts.stream()
                                .filter(item -> "README.md".equals(item.defaultFileName()))
                                .findFirst()
                                .orElseThrow();
                assertFalse(missingPhase0.existsFlag());
                assertEquals("SKIPPED", missingPhase0.scanStatus());
                assertEquals("OPTIONAL_ARTIFACT_MISSING", missingPhase0.scanMessage());
                assertNull(missingPhase0.ticketId());
                assertNull(missingPhase0.sourceUpdatedAt());
        }

        @Test
        void ticket_scoped_scan_parses_spec_pack_and_records_parse_results() throws Exception {
                var resource = ArtifactScannerServiceTest.class.getClassLoader()
                                .getResource("test-fixtures/PARSER-SPEC-PACK/spec-pack.md");
                String specPack = Files.readString(Path.of(resource.toURI()));
                SpecPackMarkdownParser parser = new SpecPackMarkdownParser();
                FakeGitHubArtifactScannerSource localSource = new FakeGitHubArtifactScannerSource(
                                "fedcba9876543210fedcba9876543210fedcba98", COMMITTED_AT);
                localSource.put("changes/PARSER-SPEC-PACK/spec-pack.md", specPack);

                InMemoryArtifactScannerPersistencePort localPersistence = new InMemoryArtifactScannerPersistencePort(
                                new RepositoryScope(repositoryId, projectId, "nktrung/Demo-Project", "main"),
                                List.of(new TicketScope(ticketId, "PARSER-SPEC-PACK")),
                                artifactTypes());
                ArtifactScannerService localService = new ArtifactScannerService(localSource, localPersistence);

                ScanRun run = localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("PARSER-SPEC-PACK"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-spec-pack"));

                assertEquals("SUCCESS", run.status());
                ArtifactSnapshot specSnapshot = localService.getRunArtifacts(run.connectorRunId()).stream()
                                .filter(item -> "spec-pack.md".equals(item.defaultFileName()))
                                .findFirst()
                                .orElseThrow();
                assertTrue(specSnapshot.needParse());
                assertEquals("FOUND", specSnapshot.scanStatus());
                assertEquals(3, localPersistence.parsedAcceptanceCriteria().size());
                long expectedPersistedSections = SpecPackMarkdownParser.requiredSectionKeys().size()
                                + parser.parse(specPack, "changes/PARSER-SPEC-PACK/spec-pack.md", "draft").sections()
                                                .entrySet().stream()
                                                .filter(entry -> !SpecPackMarkdownParser.requiredSectionKeys()
                                                                .contains(entry.getKey()))
                                                .filter(entry -> entry.getValue() != null
                                                                && !entry.getValue().isBlank())
                                                .count();
                assertEquals(expectedPersistedSections, localPersistence.parsedSections().size());
                assertTrue(localPersistence.parsedSections().stream()
                                .allMatch(section -> section.requiredFlag() || (section.sectionSummary() != null
                                                && !section.sectionSummary().isBlank())));
                assertTrue(localPersistence.parsedAcceptanceCriteria().stream()
                                .allMatch(ac -> specSnapshot.artifactSnapshotId().equals(ac.artifactSnapshotId())));
                assertFalse(localPersistence.parsedSections().stream()
                                .anyMatch(section -> "ACCEPTANCE_CRITERIA".equals(section.sectionType())));
                assertTrue(localPersistence.evidenceEvents().stream()
                                .anyMatch(event -> "PARSE_COMPLETED".equals(event.eventType())
                                                && "SUCCESS".equals(event.eventResult())
                                                && event.eventSummary().contains("PARTIAL")));
                assertTrue(localPersistence.dataQualityRecords().size() > 0);
        }

        @Test
        void ticket_scoped_scan_inserts_all_required_spec_pack_sections_even_when_some_are_missing() throws Exception {
                var resource = ArtifactScannerServiceTest.class.getClassLoader()
                                .getResource("test-fixtures/PARSER-SPEC-PACK/spec-pack.md");
                String fullSpecPack = Files.readString(Path.of(resource.toURI()));
                String missingOpenIssues = fullSpecPack.replaceFirst(
                                "(?s)##\\s*\\d+\\.\\s*Các vấn đề mở.*?(?=\\n## \\d)",
                                "");

                FakeGitHubArtifactScannerSource localSource = new FakeGitHubArtifactScannerSource(
                                "fedcba9876543210fedcba9876543210fedcba98", COMMITTED_AT);
                localSource.put("changes/PARSER-SPEC-PACK/spec-pack.md", missingOpenIssues);

                InMemoryArtifactScannerPersistencePort localPersistence = new InMemoryArtifactScannerPersistencePort(
                                new RepositoryScope(repositoryId, projectId, "nktrung/Demo-Project", "main"),
                                List.of(new TicketScope(ticketId, "PARSER-SPEC-PACK")),
                                artifactTypes());
                ArtifactScannerService localService = new ArtifactScannerService(localSource, localPersistence);

                localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("PARSER-SPEC-PACK"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-spec-pack-missing"));

                assertTrue(SpecPackMarkdownParser.requiredSectionKeys().stream()
                                .allMatch(requiredKey -> localPersistence.parsedSections().stream()
                                                .anyMatch(section -> requiredKey.toLowerCase(Locale.ROOT).equals(section.sectionKey()))));

                assertTrue(localPersistence.parsedSections().stream()
                                .anyMatch(section -> "các_vấn_đề_mở".equals(section.sectionKey())
                                                && !section.presentFlag()));
        }

        @Test
        void scanner_calls_phase_evaluator_after_scan() throws Exception {
                var resource = ArtifactScannerServiceTest.class.getClassLoader()
                                .getResource("test-fixtures/PARSER-SPEC-PACK/spec-pack.md");
                String specPack = Files.readString(Path.of(resource.toURI()));
                FakeGitHubArtifactScannerSource localSource = new FakeGitHubArtifactScannerSource(
                                "fedcba9876543210fedcba9876543210fedcba98", COMMITTED_AT);
                localSource.put("changes/TICKET-EVIDENCE-DETAIL/spec-pack.md", specPack);

                InMemoryArtifactScannerPersistencePort localPersistence = new InMemoryArtifactScannerPersistencePort(
                                new RepositoryScope(repositoryId, projectId, "nktrung/Demo-Project", "main"),
                                List.of(new TicketScope(ticketId, "TICKET-EVIDENCE-DETAIL")),
                                artifactTypes());
                TicketPhaseEvaluatorService evaluator = Mockito.mock(TicketPhaseEvaluatorService.class);
                ArtifactScannerService localService = new ArtifactScannerService(
                                localSource,
                                localPersistence,
                                new SpecPackMarkdownParser(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                new ReviewChecklistMarkdownParser(),
                                new com.sdd.platform.domain.service.markdown.report.ReportMarkdownParser(),
                                evaluator);

                ScanRun run = localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("TICKET-EVIDENCE-DETAIL"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-phase-evaluator"));

                assertEquals("SUCCESS", run.status());
                Mockito.verify(evaluator).evaluateAndPersist(Mockito.eq(ticketId), Mockito.eq("trace-phase-evaluator"));
        }

        @Test
        void ticket_scoped_scan_replaces_previous_acceptance_criteria_when_spec_pack_changes() throws Exception {
                var resource = ArtifactScannerServiceTest.class.getClassLoader()
                                .getResource("test-fixtures/PARSER-SPEC-PACK/spec-pack.md");
                String fullSpecPack = Files.readString(Path.of(resource.toURI()));
                String noAcSpecPack = fullSpecPack.replaceFirst(
                                "(?s)## 7\\. Tiêu chí chấp nhận.*?(?=## 8\\.)",
                                """
                                                ## 7. Tiêu chí chấp nhận
                                                No acceptance criteria remain in this commit.

                                                """);

                FakeGitHubArtifactScannerSource localSource = new FakeGitHubArtifactScannerSource(
                                "fedcba9876543210fedcba9876543210fedcba98", COMMITTED_AT);
                localSource.put("changes/PARSER-SPEC-PACK/spec-pack.md", fullSpecPack);

                InMemoryArtifactScannerPersistencePort localPersistence = new InMemoryArtifactScannerPersistencePort(
                                new RepositoryScope(repositoryId, projectId, "nktrung/Demo-Project", "main"),
                                List.of(new TicketScope(ticketId, "PARSER-SPEC-PACK")),
                                artifactTypes());
                ArtifactScannerService localService = new ArtifactScannerService(localSource, localPersistence);

                localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("PARSER-SPEC-PACK"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-spec-pack-1"));
                assertEquals(3, localPersistence.activeAcceptanceCriteria().size());

                localSource.put("changes/PARSER-SPEC-PACK/spec-pack.md", noAcSpecPack);
                localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("PARSER-SPEC-PACK"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-spec-pack-2"));

                assertEquals(0, localPersistence.activeAcceptanceCriteria().size());
        }

        @Test
        void ticket_scoped_scan_replaces_previous_parsed_sections_when_spec_pack_changes() throws Exception {
                var resource = ArtifactScannerServiceTest.class.getClassLoader()
                                .getResource("test-fixtures/PARSER-SPEC-PACK/spec-pack.md");
                String fullSpecPack = Files.readString(Path.of(resource.toURI()));
                String updatedSpecPack = fullSpecPack.replace(
                                "Parser đọc file theo đường dẫn chuẩn, trích xuất section/table theo template",
                                "Parser đọc file theo đường dẫn chuẩn, trích xuất section/table theo template mới nhất");

                FakeGitHubArtifactScannerSource localSource = new FakeGitHubArtifactScannerSource(
                                "fedcba9876543210fedcba9876543210fedcba98", COMMITTED_AT);
                localSource.put("changes/PARSER-SPEC-PACK/spec-pack.md", fullSpecPack);

                InMemoryArtifactScannerPersistencePort localPersistence = new InMemoryArtifactScannerPersistencePort(
                                new RepositoryScope(repositoryId, projectId, "nktrung/Demo-Project", "main"),
                                List.of(new TicketScope(ticketId, "PARSER-SPEC-PACK")),
                                artifactTypes());
                ArtifactScannerService localService = new ArtifactScannerService(localSource, localPersistence);

                localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("PARSER-SPEC-PACK"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-spec-pack-sections-1"));
                assertEquals(countPersistedSpecPackSections(fullSpecPack), localPersistence.parsedSections().size());

                localSource.put("changes/PARSER-SPEC-PACK/spec-pack.md", updatedSpecPack);
                localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("PARSER-SPEC-PACK"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-spec-pack-sections-2"));

                assertEquals(countPersistedSpecPackSections(updatedSpecPack), localPersistence.parsedSections().size());
                assertTrue(localPersistence.parsedSections().stream()
                                .allMatch(section -> "spec-pack".equals(section.sectionType())));
        }

        @Test
        void ticket_scoped_scan_removes_acceptance_criteria_when_spec_pack_file_is_deleted() throws Exception {
                var resource = ArtifactScannerServiceTest.class.getClassLoader()
                                .getResource("test-fixtures/PARSER-SPEC-PACK/spec-pack.md");
                String fullSpecPack = Files.readString(Path.of(resource.toURI()));

                FakeGitHubArtifactScannerSource localSource = new FakeGitHubArtifactScannerSource(
                                "fedcba9876543210fedcba9876543210fedcba98", COMMITTED_AT);
                localSource.put("changes/PARSER-SPEC-PACK/spec-pack.md", fullSpecPack);

                InMemoryArtifactScannerPersistencePort localPersistence = new InMemoryArtifactScannerPersistencePort(
                                new RepositoryScope(repositoryId, projectId, "nktrung/Demo-Project", "main"),
                                List.of(new TicketScope(ticketId, "PARSER-SPEC-PACK")),
                                artifactTypes());
                ArtifactScannerService localService = new ArtifactScannerService(localSource, localPersistence);

                ScanRun firstRun = localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("PARSER-SPEC-PACK"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-spec-pack-delete-1"));

                assertEquals("SUCCESS", firstRun.status());
                assertEquals(3, localPersistence.activeAcceptanceCriteria().size());
                assertNotNull(localPersistence.ticketStartedAt(ticketId));

                localSource.remove("changes/PARSER-SPEC-PACK/spec-pack.md");
                ScanRun secondRun = localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("PARSER-SPEC-PACK"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-spec-pack-delete-2"));

                assertEquals("SUCCESS", secondRun.status());
                assertEquals(0, localPersistence.activeAcceptanceCriteria().size());
                assertEquals(0, localPersistence.parsedSections().size());
                ArtifactSnapshot deletedSpecSnapshot = localService.getRunArtifacts(secondRun.connectorRunId()).stream()
                                .filter(item -> "spec-pack.md".equals(item.defaultFileName()))
                                .findFirst()
                                .orElseThrow();
                assertFalse(deletedSpecSnapshot.existsFlag());
                assertEquals("MISSING", deletedSpecSnapshot.scanStatus());
                assertNull(localPersistence.ticketStartedAt(ticketId));
        }

        @Test
        void ticket_scoped_scan_clears_completed_at_when_report_file_is_deleted() {
                FakeGitHubArtifactScannerSource localSource = new FakeGitHubArtifactScannerSource(
                                "fedcba9876543210fedcba9876543210fedcba98", COMMITTED_AT);
                localSource.put("docs/changes/ARTIFACT-SCANNER/report.md", """
                                # Report

                                **Update date**: 2026-08-21 13:44:23

                                ## 1. Edited summary

                                Updated implementation notes.
                                """);

                InMemoryArtifactScannerPersistencePort localPersistence = new InMemoryArtifactScannerPersistencePort(
                                new RepositoryScope(repositoryId, projectId, "nktrung/Demo-Project", "main"),
                                List.of(new TicketScope(ticketId, "ARTIFACT-SCANNER")),
                                artifactTypes());
                ArtifactScannerService localService = new ArtifactScannerService(localSource, localPersistence);

                ScanRun firstRun = localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("ARTIFACT-SCANNER"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-report-delete-1"));

                assertEquals("SUCCESS", firstRun.status());
                assertNotNull(localPersistence.ticketCompletedAt(ticketId));

                localSource.remove("docs/changes/ARTIFACT-SCANNER/report.md");
                ScanRun secondRun = localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("ARTIFACT-SCANNER"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-report-delete-2"));

                assertEquals("SUCCESS", secondRun.status());
                assertNull(localPersistence.ticketCompletedAt(ticketId));
        }

        @Test
        void ticket_scoped_scan_sets_ticket_dates_to_null_when_header_dates_are_malformed() {
                FakeGitHubArtifactScannerSource localSource = new FakeGitHubArtifactScannerSource(
                                "fedcba9876543210fedcba9876543210fedcba98", COMMITTED_AT);
                localSource.put("docs/changes/ARTIFACT-SCANNER/spec-pack.md", """
                                # Spec Pack

                                **Create date**: 2026-08-21 13:10:56

                                ## 1. Context / Purpose

                                Sample.
                                """);
                localSource.put("docs/changes/ARTIFACT-SCANNER/report.md", """
                                # Report

                                **Update date**: 2026-08-21 13:44:23

                                ## 1. Edited summary

                                Sample.
                                """);

                InMemoryArtifactScannerPersistencePort localPersistence = new InMemoryArtifactScannerPersistencePort(
                                new RepositoryScope(repositoryId, projectId, "nktrung/Demo-Project", "main"),
                                List.of(new TicketScope(ticketId, "ARTIFACT-SCANNER")),
                                artifactTypes());
                ArtifactScannerService localService = new ArtifactScannerService(localSource, localPersistence);

                ScanRun firstRun = localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("ARTIFACT-SCANNER"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-malformed-dates-1"));

                assertEquals("SUCCESS", firstRun.status());
                assertNotNull(localPersistence.ticketStartedAt(ticketId));
                assertNotNull(localPersistence.ticketCompletedAt(ticketId));

                localSource.put("docs/changes/ARTIFACT-SCANNER/spec-pack.md", """
                                # Spec Pack

                                **Create date**: TBD

                                ## 1. Context / Purpose

                                Sample.
                                """);
                localSource.put("docs/changes/ARTIFACT-SCANNER/report.md", """
                                # Report

                                **Update date**: TBD

                                ## 1. Edited summary

                                Sample.
                                """);

                ScanRun secondRun = localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("ARTIFACT-SCANNER"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-malformed-dates-2"));

                assertEquals("SUCCESS", secondRun.status());
                assertNull(localPersistence.ticketStartedAt(ticketId));
                assertNull(localPersistence.ticketCompletedAt(ticketId));
        }

        @Test
        void ticket_scoped_scan_overwrites_ticket_dates_on_rescan_when_header_dates_change() {
                FakeGitHubArtifactScannerSource localSource = new FakeGitHubArtifactScannerSource(
                                "fedcba9876543210fedcba9876543210fedcba98", COMMITTED_AT);
                localSource.put("docs/changes/ARTIFACT-SCANNER/spec-pack.md", """
                                # Spec Pack

                                **Create date**: 2026-08-20 10:00:00

                                ## 1. Context / Purpose

                                Sample.
                                """);
                localSource.put("docs/changes/ARTIFACT-SCANNER/report.md", """
                                # Report

                                **Update date**: 2026-08-21 11:00:00

                                ## 1. Edited summary

                                Sample.
                                """);

                InMemoryArtifactScannerPersistencePort localPersistence = new InMemoryArtifactScannerPersistencePort(
                                new RepositoryScope(repositoryId, projectId, "nktrung/Demo-Project", "main"),
                                List.of(new TicketScope(ticketId, "ARTIFACT-SCANNER")),
                                artifactTypes());
                ArtifactScannerService localService = new ArtifactScannerService(localSource, localPersistence);

                ScanRun firstRun = localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("ARTIFACT-SCANNER"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-overwrite-dates-1"));

                assertEquals("SUCCESS", firstRun.status());
                OffsetDateTime firstStartedAt = localPersistence.ticketStartedAt(ticketId);
                OffsetDateTime firstCompletedAt = localPersistence.ticketCompletedAt(ticketId);
                assertNotNull(firstStartedAt);
                assertNotNull(firstCompletedAt);

                localSource.put("docs/changes/ARTIFACT-SCANNER/spec-pack.md", """
                                # Spec Pack

                                **Create date**: 2026-08-22 08:00:00

                                ## 1. Context / Purpose

                                Sample.
                                """);
                localSource.put("docs/changes/ARTIFACT-SCANNER/report.md", """
                                # Report

                                **Update date**: 2026-08-23 09:30:00

                                ## 1. Edited summary

                                Sample.
                                """);

                ScanRun secondRun = localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("ARTIFACT-SCANNER"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-overwrite-dates-2"));

                assertEquals("SUCCESS", secondRun.status());
                OffsetDateTime secondStartedAt = localPersistence.ticketStartedAt(ticketId);
                OffsetDateTime secondCompletedAt = localPersistence.ticketCompletedAt(ticketId);
                assertNotNull(secondStartedAt);
                assertNotNull(secondCompletedAt);
                assertNotEquals(firstStartedAt, secondStartedAt);
                assertNotEquals(firstCompletedAt, secondCompletedAt);
        }

        @Test
        void ticket_scoped_scan_marks_self_review_table_sections_missing_when_table_has_no_rows() {
                String selfReview = """
                                # Self Review

                                **Ticket ID**: PARSER-SELF-REVIEW

                                ## 4. Run Command and Results
                                | command | result |
                                |---|---|

                                ## 8. Unprocessed / Pending / Accepted Risk
                                | item | reason | impact | owner | deadline |
                                |---|---|---|---|---|

                                ## 11. Final Self-Verdict
                                PASS
                                """;

                FakeGitHubArtifactScannerSource localSource = new FakeGitHubArtifactScannerSource(
                                "fedcba9876543210fedcba9876543210fedcba98", COMMITTED_AT);
                localSource.put("changes/PARSER-SELF-REVIEW/self-review.md", selfReview);

                InMemoryArtifactScannerPersistencePort localPersistence = new InMemoryArtifactScannerPersistencePort(
                                new RepositoryScope(repositoryId, projectId, "nktrung/Demo-Project", "main"),
                                List.of(new TicketScope(ticketId, "PARSER-SELF-REVIEW")),
                                artifactTypes());
                ArtifactScannerService localService = new ArtifactScannerService(localSource, localPersistence);

                ScanRun run = localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("PARSER-SELF-REVIEW"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-self-review"));

                assertEquals("SUCCESS", run.status());
                assertTrue(localPersistence.parsedSections().stream()
                                .filter(section -> "RUN_COMMAND_AND_RESULTS".equals(section.sectionKey()))
                                .allMatch(section -> !section.presentFlag() && !section.validFlag()));
                assertTrue(localPersistence.parsedSections().stream()
                                .filter(section -> "UNPROCESSED_PENDING_ACCEPTED_RISK".equals(section.sectionKey()))
                                .allMatch(section -> !section.presentFlag() && !section.validFlag()));
        }

        @Test
        void ticket_scoped_scan_refreshes_self_review_sections_and_persists_required_missing_sections() {
                String initialSelfReview = """
                                # Self Review

                                **Ticket ID**: PARSER-SELF-REVIEW

                                ## 4. Run Command and Results
                                | command | result |
                                |---|---|
                                | build | PASS |

                                ## 11. Final Self-Verdict
                                PASS
                                """;

                String updatedSelfReview = """
                                # Self Review

                                **Ticket ID**: PARSER-SELF-REVIEW

                                ## 7. Bugs Found and Resolved
                                | bug | fixed | note |
                                |---|---|---|
                                | issue | yes | done |

                                ## 11. Final Self-Verdict
                                PASS
                                """;

                FakeGitHubArtifactScannerSource localSource = new FakeGitHubArtifactScannerSource(
                                "fedcba9876543210fedcba9876543210fedcba98", COMMITTED_AT);
                localSource.put("changes/PARSER-SELF-REVIEW/self-review.md", initialSelfReview);

                InMemoryArtifactScannerPersistencePort localPersistence = new InMemoryArtifactScannerPersistencePort(
                                new RepositoryScope(repositoryId, projectId, "nktrung/Demo-Project", "main"),
                                List.of(new TicketScope(ticketId, "PARSER-SELF-REVIEW")),
                                artifactTypes());
                ArtifactScannerService localService = new ArtifactScannerService(localSource, localPersistence);

                localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("PARSER-SELF-REVIEW"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-self-review-1"));

                localSource.put("changes/PARSER-SELF-REVIEW/self-review.md", updatedSelfReview);
                localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("PARSER-SELF-REVIEW"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-self-review-2"));

                assertEquals(1, localPersistence.parsedSections().stream()
                                .filter(section -> "run_command_and_results".equals(section.sectionKey()))
                                .count());
                assertTrue(localPersistence.parsedSections().stream()
                                .filter(section -> "run_command_and_results".equals(section.sectionKey()))
                                .allMatch(section -> !section.presentFlag()));
                assertTrue(localPersistence.parsedSections().stream()
                                .filter(section -> "bugs_found_and_resolved".equals(section.sectionKey()))
                                .anyMatch(section -> section.presentFlag()));
                assertTrue(localPersistence.parsedSections().stream()
                                .filter(section -> "bugs_found_and_resolved".equals(section.sectionKey()))
                                .allMatch(section -> section.requiredFlag()));
        }

        @Test
        void ticket_scoped_scan_recalculates_scores_after_blackbox_and_report_are_deleted() {
                EvidenceQualityScoreService scoreService = Mockito.mock(EvidenceQualityScoreService.class);
                FakeGitHubArtifactScannerSource localSource = new FakeGitHubArtifactScannerSource(
                                "fedcba9876543210fedcba9876543210fedcba98", COMMITTED_AT);
                localSource.put("changes/PARSER-SPEC-PACK/blackbox-testcases.md", "blackbox");
                localSource.put("changes/PARSER-SPEC-PACK/report.md", "report");

                InMemoryArtifactScannerPersistencePort localPersistence = new InMemoryArtifactScannerPersistencePort(
                                new RepositoryScope(repositoryId, projectId, "nktrung/Demo-Project", "main"),
                                List.of(new TicketScope(ticketId, "PARSER-SPEC-PACK")),
                                artifactTypes());
                ArtifactScannerService localService = new ArtifactScannerService(
                                localSource,
                                localPersistence,
                                new SpecPackMarkdownParser(),
                                new com.sdd.platform.domain.service.markdown.selfreview.SelfReviewMarkdownParser(),
                                null,
                                null,
                                null,
                                scoreService,
                                null,
                                null,
                                null);

                localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("PARSER-SPEC-PACK"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-score-1"));

                localSource.remove("changes/PARSER-SPEC-PACK/blackbox-testcases.md");
                localSource.remove("changes/PARSER-SPEC-PACK/report.md");
                localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("PARSER-SPEC-PACK"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-score-2"));

                Mockito.verify(scoreService, Mockito.times(2))
                                .recalculateFromSourceChange(Mockito.eq(ticketId), Mockito.isNull(),
                                                Mockito.anyString());
        }

        @Test
        void ticket_scoped_scan_auto_creates_missing_ticket_and_continues() {
                InMemoryArtifactScannerPersistencePort missingTicketPersistence = new InMemoryArtifactScannerPersistencePort(
                                new RepositoryScope(repositoryId, projectId, "nktrung/Demo-Project", "main"),
                                List.of(),
                                artifactTypes());
                FakeGitHubArtifactScannerSource missingSource = new FakeGitHubArtifactScannerSource(
                                "fedcba9876543210fedcba9876543210fedcba98", COMMITTED_AT);
                missingSource.put("docs/changes/UNKNOWN-TICKET/spec-pack.md", "spec");
                missingSource.put("docs/changes/UNKNOWN-TICKET/impl-plan.md", "impl");
                missingSource.put("docs/changes/UNKNOWN-TICKET/review-checklist.md", "review");
                missingSource.put("docs/changes/UNKNOWN-TICKET/self-review.md", "self");
                missingSource.put("docs/changes/UNKNOWN-TICKET/test-plan.md", "test-plan");
                missingSource.put("docs/changes/UNKNOWN-TICKET/test-results.md", "test-results");
                missingSource.put("docs/changes/UNKNOWN-TICKET/report.md", "report");
                missingSource.put("docs/changes/UNKNOWN-TICKET/blackbox-testcases.md", "blackbox");
                ArtifactScannerService missingTicketService = new ArtifactScannerService(
                                missingSource,
                                missingTicketPersistence);

                ScanRun run = missingTicketService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("UNKNOWN-TICKET"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-1"));

                assertEquals("SUCCESS", run.status());
                assertEquals(15, run.recordsWritten());
                assertTrue(
                                missingTicketPersistence
                                                .findTicketByProjectIdAndExternalKey(projectId, "UNKNOWN-TICKET")
                                                .isPresent());
                assertEquals(15, missingTicketService.getRunArtifacts(run.connectorRunId()).size());
        }

        @Test
        void ticket_scoped_scan_without_existing_directory_still_records_missing_artifacts() {
                InMemoryArtifactScannerPersistencePort missingTicketPersistence = new InMemoryArtifactScannerPersistencePort(
                                new RepositoryScope(repositoryId, projectId, "nktrung/Demo-Project", "main"),
                                List.of(),
                                artifactTypes());
                FakeGitHubArtifactScannerSource missingSource = new FakeGitHubArtifactScannerSource(
                                "fedcba9876543210fedcba9876543210fedcba98", COMMITTED_AT);
                ArtifactScannerService missingTicketService = new ArtifactScannerService(
                                missingSource,
                                missingTicketPersistence);

                ScanRun run = missingTicketService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("UNKNOWN-TICKET"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-2"));

                assertEquals("SUCCESS", run.status());
                assertEquals(15, run.recordsWritten());
                assertTrue(missingTicketPersistence.findTicketByProjectIdAndExternalKey(projectId, "UNKNOWN-TICKET")
                                .isPresent());

                List<ArtifactSnapshot> artifacts = missingTicketService.getRunArtifacts(run.connectorRunId());
                assertEquals(15, artifacts.size());
                ArtifactSnapshot missingSpec = artifacts.stream()
                                .filter(item -> "spec-pack.md".equals(item.defaultFileName()))
                                .findFirst()
                                .orElseThrow();
                assertFalse(missingSpec.existsFlag());
                assertEquals("MISSING", missingSpec.scanStatus());
                assertEquals("REQUIRED_ARTIFACT_MISSING", missingSpec.scanMessage());

                ArtifactSnapshot missingPhase0 = artifacts.stream()
                                .filter(item -> "README.md".equals(item.defaultFileName()))
                                .findFirst()
                                .orElseThrow();
                assertFalse(missingPhase0.existsFlag());
                assertEquals("SKIPPED", missingPhase0.scanStatus());
                assertEquals("OPTIONAL_ARTIFACT_MISSING", missingPhase0.scanMessage());
        }

        @Test
        void report_open_issues_default_to_open_status_when_missing() {
                FakeGitHubArtifactScannerSource localSource = new FakeGitHubArtifactScannerSource(
                                "fedcba9876543210fedcba9876543210fedcba98", COMMITTED_AT);
                localSource.put("docs/changes/ARTIFACT-SCANNER/report.md", """
                                # Report

                                ## Open issues

                                - Follow up with QA on edge cases
                                - Confirm rollout checklist with PM
                                """);

                InMemoryArtifactScannerPersistencePort localPersistence = new InMemoryArtifactScannerPersistencePort(
                                new RepositoryScope(repositoryId, projectId, "nktrung/Demo-Project", "main"),
                                List.of(new TicketScope(ticketId, "ARTIFACT-SCANNER")),
                                artifactTypes());
                ArtifactScannerService localService = new ArtifactScannerService(localSource, localPersistence);

                ScanRun run = localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("ARTIFACT-SCANNER"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-report-open-issues"));

                assertEquals("SUCCESS", run.status());
                assertTrue(localPersistence.parsedIssues().stream()
                                .anyMatch(issue -> "REPORT".equals(issue.sourceType())
                                                && issue.issueStatus() == null
                                                && "Follow up with QA on edge cases".equals(issue.issueSummary())));
        }

        @Test
        void ticket_scoped_scan_uses_phase0_from_pull_request_when_available() {
                BranchAwareFakeGitHubArtifactScannerSource branchAwareSource = new BranchAwareFakeGitHubArtifactScannerSource(
                                "feature-sha",
                                "main-sha",
                                OffsetDateTime.parse("2026-06-16T06:42:28Z"),
                                OffsetDateTime.parse("2026-06-15T06:42:28Z"));
                branchAwareSource.putTicketArtifact("feature-sha", "docs/changes/ARTIFACT-SCANNER/spec-pack.md",
                                "spec");
                branchAwareSource.putPhase0Artifact("feature-sha", "docs/maintenance/phase0/README.md", "phase0-pr");
                branchAwareSource.putPhase0Artifact("main-sha", "docs/maintenance/phase0/README.md", "phase0-main");

                InMemoryArtifactScannerPersistencePort branchAwarePersistence = new InMemoryArtifactScannerPersistencePort(
                                new RepositoryScope(repositoryId, projectId, "nktrung/Demo-Project", "main"),
                                List.of(new TicketScope(ticketId, "ARTIFACT-SCANNER")),
                                artifactTypes());
                ArtifactScannerService branchAwareService = new ArtifactScannerService(branchAwareSource,
                                branchAwarePersistence);

                ScanRun run = branchAwareService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "feature-pr",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("ARTIFACT-SCANNER"),
                                ArtifactScanTriggerType.WEBHOOK,
                                "tester",
                                "trace-pr-phase0"));

                assertEquals("SUCCESS", run.status());
                List<ArtifactSnapshot> artifacts = branchAwareService.getRunArtifacts(run.connectorRunId());
                ArtifactSnapshot phase0 = artifacts.stream()
                                .filter(item -> "README.md".equals(item.defaultFileName()))
                                .findFirst()
                                .orElseThrow();
                assertEquals("FOUND", phase0.scanStatus());
                assertEquals(OffsetDateTime.parse("2026-06-16T06:42:28Z"), phase0.sourceUpdatedAt());
        }

        @Test
        void ticket_scoped_scan_falls_back_to_main_for_phase0_when_missing_on_pr() {
                BranchAwareFakeGitHubArtifactScannerSource branchAwareSource = new BranchAwareFakeGitHubArtifactScannerSource(
                                "feature-sha",
                                "main-sha",
                                OffsetDateTime.parse("2026-06-16T06:42:28Z"),
                                OffsetDateTime.parse("2026-06-15T06:42:28Z"));
                branchAwareSource.putTicketArtifact("feature-sha", "docs/changes/ARTIFACT-SCANNER/spec-pack.md",
                                "spec");
                branchAwareSource.putPhase0Artifact("main-sha", "docs/maintenance/phase0/README.md", "phase0-main");

                InMemoryArtifactScannerPersistencePort branchAwarePersistence = new InMemoryArtifactScannerPersistencePort(
                                new RepositoryScope(repositoryId, projectId, "nktrung/Demo-Project", "main"),
                                List.of(new TicketScope(ticketId, "ARTIFACT-SCANNER")),
                                artifactTypes());
                ArtifactScannerService branchAwareService = new ArtifactScannerService(branchAwareSource,
                                branchAwarePersistence);

                ScanRun run = branchAwareService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "feature-pr",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("ARTIFACT-SCANNER"),
                                ArtifactScanTriggerType.WEBHOOK,
                                "tester",
                                "trace-main-phase0"));

                assertEquals("SUCCESS", run.status());
                List<ArtifactSnapshot> artifacts = branchAwareService.getRunArtifacts(run.connectorRunId());
                ArtifactSnapshot phase0 = artifacts.stream()
                                .filter(item -> "README.md".equals(item.defaultFileName()))
                                .findFirst()
                                .orElseThrow();
                assertEquals("FOUND", phase0.scanStatus());
                assertEquals(OffsetDateTime.parse("2026-06-15T06:42:28Z"), phase0.sourceUpdatedAt());
        }

        @Test
        void ticket_scoped_scan_inserts_all_required_report_sections_even_when_missing() {
                FakeGitHubArtifactScannerSource localSource = new FakeGitHubArtifactScannerSource(
                                "fedcba9876543210fedcba9876543210fedcba98", COMMITTED_AT);
                localSource.put("docs/changes/ARTIFACT-SCANNER/report.md", """
                                # Final Report

                                ## 1. Edited summary

                                ## 2. Corresponding specification / AC
                                | ACID | status | evidence |
                                |---|---|---|

                                ## 3. Scope of influence
                                """);

                InMemoryArtifactScannerPersistencePort localPersistence = new InMemoryArtifactScannerPersistencePort(
                                new RepositoryScope(repositoryId, projectId, "nktrung/Demo-Project", "main"),
                                List.of(new TicketScope(ticketId, "ARTIFACT-SCANNER")),
                                artifactTypes());
                ArtifactScannerService localService = new ArtifactScannerService(localSource, localPersistence);

                localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("ARTIFACT-SCANNER"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-report-required-sections"));

                assertTrue(localPersistence.parsedSections().stream()
                                .anyMatch(section -> "report".equals(section.sectionType())));
                assertTrue(localPersistence.parsedSections().stream()
                                .filter(section -> "report".equals(section.sectionType()))
                                .anyMatch(section -> !section.presentFlag()));
        }

        @Test
        void ticket_scoped_scan_requires_non_empty_ticket_ids() {
                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                () -> service.scan(request(ArtifactScanMode.TICKET_SCOPED, List.of())));

                assertEquals("ticket_ids are required for TICKET_SCOPED", ex.getMessage());
        }

        @Test
        void scan_rejects_unknown_repository() {
                ArtifactScanRequest request = new ArtifactScanRequest(
                                UUID.randomUUID(),
                                "main",
                                ArtifactScanMode.FULL,
                                null,
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-1");

                NotFoundException ex = assertThrows(NotFoundException.class, () -> service.scan(request));

                assertEquals("REPOSITORY_NOT_FOUND", ex.getMessage());
        }

        @Test
        void scan_records_run_before_source_resolution_failures() {
                ArtifactScannerSourcePort failingSource = Mockito.mock(ArtifactScannerSourcePort.class);
                ArtifactScannerPersistencePort mockPersistence = Mockito.mock(ArtifactScannerPersistencePort.class);
                RepositoryScope repository = new RepositoryScope(repositoryId, projectId, "nktrung/Demo-Project",
                                "main");
                ConnectorScope connector = new ConnectorScope(UUID.randomUUID(), "ARTIFACT_SCANNER",
                                "Artifact Scanner");
                ScanRun insertedRun = new ScanRun(
                                UUID.randomUUID(),
                                connector.connectorId(),
                                OffsetDateTime.now(ZoneOffset.UTC),
                                null,
                                "RUNNING",
                                0,
                                0,
                                null,
                                "trace-fail");

                Mockito.when(mockPersistence.findRepository(repositoryId)).thenReturn(Optional.of(repository));
                Mockito.when(mockPersistence.findConnectorByType("ARTIFACT_SCANNER"))
                                .thenReturn(Optional.of(connector));
                Mockito.when(mockPersistence.insertRun(Mockito.any())).thenReturn(insertedRun);
                Mockito.when(failingSource.resolveRevision("nktrung/Demo-Project", "main"))
                                .thenThrow(new IllegalStateException("boom"));

                ArtifactScannerService failingService = new ArtifactScannerService(failingSource, mockPersistence);

                IllegalStateException ex = assertThrows(IllegalStateException.class,
                                () -> failingService.scan(new ArtifactScanRequest(
                                                repositoryId,
                                                "main",
                                                ArtifactScanMode.FULL,
                                                null,
                                                ArtifactScanTriggerType.MANUAL,
                                                "tester",
                                                "trace-fail")));

                assertEquals("boom", ex.getMessage());
                InOrder inOrder = Mockito.inOrder(mockPersistence, failingSource);
                inOrder.verify(mockPersistence).insertRun(Mockito.any());
                inOrder.verify(failingSource).resolveRevision("nktrung/Demo-Project", "main");
                Mockito.verify(mockPersistence).updateRun(Mockito.argThat(run -> run != null
                                && insertedRun.connectorRunId().equals(run.connectorRunId())
                                && "FAILED".equals(run.status())
                                && "boom".equals(run.errorMessage())));
        }

        @Test
        void ticket_scoped_scan_parses_review_checklist_and_records_parse_results() {

                FakeGitHubArtifactScannerSource localSource = new FakeGitHubArtifactScannerSource(
                                "fedcba9876543210fedcba9876543210fedcba98", COMMITTED_AT);

                String checklist = """
                                    # Security

                                    - [ ] validate auth
                                    - [x] check secret

                                    # Test

                                    - [ ] add unit test
                                """;

                localSource.put("changes/PARSER-REVIEW-CHECKLIST/review-checklist.md", checklist);

                InMemoryArtifactScannerPersistencePort localPersistence = new InMemoryArtifactScannerPersistencePort(
                                new RepositoryScope(repositoryId, projectId, "nvtDung/Demo-Project", "main"),
                                List.of(new TicketScope(ticketId, "PARSER-REVIEW-CHECKLIST")),
                                artifactTypes());

                ArtifactScannerService localService = new ArtifactScannerService(localSource, localPersistence);

                ScanRun run = localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("PARSER-REVIEW-CHECKLIST"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-review-checklist"));

                assertEquals("SUCCESS", run.status());

                ArtifactSnapshot snapshot = localService.getRunArtifacts(run.connectorRunId()).stream()
                                .filter(item -> "review-checklist.md".equals(item.defaultFileName()))
                                .findFirst()
                                .orElseThrow();

                assertTrue(snapshot.needParse());
                assertEquals("FOUND", snapshot.scanStatus());

                assertTrue(localPersistence.evidenceEvents().stream()
                                .anyMatch(event -> "REVIEW_CHECKLIST_PARSE".equals(event.sourceType())
                                                && "PARSE_COMPLETED".equals(event.eventType())
                                                && ("SUCCESS".equals(event.eventResult())
                                                                || "PARTIAL".equals(event.eventResult()))));
        }

        @Test
        void ticket_scoped_scan_marks_heading_only_general_system_review_as_present_when_children_have_body() {
                // Regression test: GENERAL_SYSTEM_REVIEW has no body of its own (it's a
                // heading-only parent whose real content lives in nested child sections).
                // The parser's own detectMissingFields() correctly treats this as present,
                // but persistReviewChecklistParse used to persist present_flag=false while
                // valid_flag=true for this row - an internally contradictory row that
                // downstream traceability code read as "missing required section".
                FakeGitHubArtifactScannerSource localSource = new FakeGitHubArtifactScannerSource(
                                "fedcba9876543210fedcba9876543210fedcba98", COMMITTED_AT);

                String checklist = """
                                # Review Checklist
                                **Ticket ID**: PARSER-REVIEW-CHECKLIST

                                ## 2. General System Review

                                ### 2.1. Number/Input Check
                                - [x] Count validation is present
                                """;

                localSource.put("changes/PARSER-REVIEW-CHECKLIST/review-checklist.md", checklist);

                InMemoryArtifactScannerPersistencePort localPersistence = new InMemoryArtifactScannerPersistencePort(
                                new RepositoryScope(repositoryId, projectId, "nvtDung/Demo-Project", "main"),
                                List.of(new TicketScope(ticketId, "PARSER-REVIEW-CHECKLIST")),
                                artifactTypes());

                ArtifactScannerService localService = new ArtifactScannerService(localSource, localPersistence);

                ScanRun run = localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("PARSER-REVIEW-CHECKLIST"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-general-system-review"));

                assertEquals("SUCCESS", run.status());

                assertTrue(localPersistence.parsedSections().stream()
                                .anyMatch(section -> "review-checklist".equals(section.sectionType())));
                assertTrue(localPersistence.parsedSections().stream()
                                .filter(section -> "review-checklist".equals(section.sectionType()))
                                .anyMatch(section -> section.presentFlag() || section.validFlag()));
        }

        @Test
        void ticket_scoped_scan_removes_parsed_sections_when_review_checklist_file_is_deleted() {
                FakeGitHubArtifactScannerSource localSource = new FakeGitHubArtifactScannerSource(
                                "fedcba9876543210fedcba9876543210fedcba98", COMMITTED_AT);

                String checklist = """
                                    # Security

                                    - [ ] validate auth
                                    - [x] check secret

                                    # Test

                                    - [ ] add unit test
                                """;

                localSource.put("changes/PARSER-REVIEW-CHECKLIST/review-checklist.md", checklist);

                InMemoryArtifactScannerPersistencePort localPersistence = new InMemoryArtifactScannerPersistencePort(
                                new RepositoryScope(repositoryId, projectId, "nvtDung/Demo-Project", "main"),
                                List.of(new TicketScope(ticketId, "PARSER-REVIEW-CHECKLIST")),
                                artifactTypes());

                ArtifactScannerService localService = new ArtifactScannerService(localSource, localPersistence);

                ScanRun firstRun = localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("PARSER-REVIEW-CHECKLIST"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-review-checklist-delete-1"));

                assertEquals("SUCCESS", firstRun.status());
                assertTrue(localPersistence.parsedSections().stream()
                                .anyMatch(section -> "review-checklist".equals(section.sectionType())));

                localSource.remove("changes/PARSER-REVIEW-CHECKLIST/review-checklist.md");
                ScanRun secondRun = localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("PARSER-REVIEW-CHECKLIST"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-review-checklist-delete-2"));

                assertEquals("SUCCESS", secondRun.status());
                assertTrue(localPersistence.parsedSections().stream()
                                .noneMatch(section -> "review-checklist".equals(section.sectionType())));
                ArtifactSnapshot deletedSnapshot = localService.getRunArtifacts(secondRun.connectorRunId()).stream()
                                .filter(item -> "review-checklist.md".equals(item.defaultFileName()))
                                .findFirst()
                                .orElseThrow();
                assertFalse(deletedSnapshot.existsFlag());
                assertEquals("MISSING", deletedSnapshot.scanStatus());
        }

        @Test
        void ticket_scoped_scan_removes_stale_parsed_sections_when_impl_plan_file_is_deleted() {
                // impl-plan.md is parsed via ImplPlanParseService/DocParsePersistencePort in
                // production (a different port than ArtifactScannerPersistencePort), but both
                // write into the same tbl_fact_artifact_parsed_section table keyed by
                // (ticket_id, section_type="impl-plan"). This test seeds a stale row directly
                // to simulate data left over from a prior run, then verifies the scanner
                // cleans it up once it observes the file is gone from the tree - mirroring the
                // existing cleanup for spec-pack/report/self-review.
                FakeGitHubArtifactScannerSource localSource = new FakeGitHubArtifactScannerSource(
                                "fedcba9876543210fedcba9876543210fedcba98", COMMITTED_AT);

                InMemoryArtifactScannerPersistencePort localPersistence = new InMemoryArtifactScannerPersistencePort(
                                new RepositoryScope(repositoryId, projectId, "nvtDung/Demo-Project", "main"),
                                List.of(new TicketScope(ticketId, "PARSER-IMPL-PLAN")),
                                artifactTypes());

                localPersistence.insertParsedSection(new ArtifactScannerModels.ParsedSection(
                                UUID.randomUUID(),
                                ticketId,
                                "impl-plan",
                                "implementation_principle",
                                "",
                                "hash",
                                true,
                                false,
                                false,
                                null));

                ArtifactScannerService localService = new ArtifactScannerService(localSource, localPersistence);

                ScanRun run = localService.scan(new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                ArtifactScanMode.TICKET_SCOPED,
                                List.of("PARSER-IMPL-PLAN"),
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-impl-plan-delete"));

                assertEquals("SUCCESS", run.status());
                assertTrue(localPersistence.parsedSections().stream()
                                .noneMatch(section -> "impl-plan".equals(section.sectionType())));
                ArtifactSnapshot missingSnapshot = localService.getRunArtifacts(run.connectorRunId()).stream()
                                .filter(item -> "impl-plan.md".equals(item.defaultFileName()))
                                .findFirst()
                                .orElseThrow();
                assertFalse(missingSnapshot.existsFlag());
                assertEquals("MISSING", missingSnapshot.scanStatus());
        }

        private ArtifactScanRequest request(ArtifactScanMode mode, List<String> ticketIds) {
                return new ArtifactScanRequest(
                                repositoryId,
                                "main",
                                mode,
                                ticketIds,
                                ArtifactScanTriggerType.MANUAL,
                                "tester",
                                "trace-1");
        }

        private void putFullArtifactSet(String specPackContent) {
                putChangeArtifact(specPackContent);
                putPhase0Artifact("README.md", "phase0");
                putPhase0Artifact("phase0-plan.md", "phase0-plan");
                putPhase0Artifact("phase0-execution-log.md", "phase0-log");
                putPhase0Artifact("phase0-decisions.md", "phase0-decisions");
                putPhase0Artifact("phase0-risk-register.md", "phase0-risk");
                putPhase0Artifact("phase0-review.md", "phase0-review");
                putPhase0Artifact("source-availability.md", "phase0-source");
        }

        private void putChangeArtifact(String specPackContent) {
                source.put("docs/changes/ARTIFACT-SCANNER/spec-pack.md", specPackContent);
                source.put("docs/changes/ARTIFACT-SCANNER/impl-plan.md", "impl");
                source.put("docs/changes/ARTIFACT-SCANNER/review-checklist.md", "review");
                source.put("docs/changes/ARTIFACT-SCANNER/self-review.md", "self");
                source.put("docs/changes/ARTIFACT-SCANNER/test-plan.md", "test-plan");
                source.put("docs/changes/ARTIFACT-SCANNER/test-results.md", "test-results");
                source.put("docs/changes/ARTIFACT-SCANNER/report.md", "report");
                source.put("docs/changes/ARTIFACT-SCANNER/blackbox-testcases.md", "blackbox");
        }

        private void putPhase0Artifact(String fileName, String content) {
                source.put("docs/maintenance/phase0/" + fileName, content);
        }

        private long countPersistedSpecPackSections(String markdown) {
                SpecPackMarkdownParser parser = new SpecPackMarkdownParser();
                Map<String, String> sections = parser.parse(markdown, "changes/PARSER-SPEC-PACK/spec-pack.md", "draft")
                                .sections();
                return SpecPackMarkdownParser.requiredSectionKeys().size()
                                + sections.entrySet().stream()
                                                .filter(entry -> !SpecPackMarkdownParser.requiredSectionKeys()
                                                                .contains(entry.getKey()))
                                                .filter(entry -> entry.getValue() != null
                                                                && !entry.getValue().isBlank())
                                                .count();
        }

        private List<ArtifactTypeScope> artifactTypes() {
                UUID phase0 = UUID.randomUUID();
                UUID spec = UUID.randomUUID();
                UUID impl = UUID.randomUUID();
                UUID review = UUID.randomUUID();
                UUID self = UUID.randomUUID();
                UUID test = UUID.randomUUID();
                UUID testResults = UUID.randomUUID();
                UUID blackbox = UUID.randomUUID();
                UUID report = UUID.randomUUID();

                List<ArtifactTypeScope> types = new ArrayList<>();
                types.add(new ArtifactTypeScope(spec, spec, "1", "SPEC_PACK", "Spec Pack", "spec-pack.md", true));
                types.add(new ArtifactTypeScope(impl, impl, "3", "IMPL_PLAN", "Implementation Plan", "impl-plan.md",
                                true));
                types.add(new ArtifactTypeScope(review, review, "4", "REVIEW_CHECKLIST", "Review Checklist",
                                "review-checklist.md", true));
                types.add(new ArtifactTypeScope(self, self, "5", "SELF_REVIEW", "Self Review", "self-review.md", true));
                types.add(new ArtifactTypeScope(test, test, "6", "TEST_PLAN", "Test Plan", "test-plan.md", true));
                types.add(new ArtifactTypeScope(testResults, testResults, "6", "TEST_RESULTS", "Test Results",
                                "test-results.md", true));
                types.add(new ArtifactTypeScope(blackbox, blackbox, "7", "BLACKBOX_TESTCASES", "Blackbox Testcases",
                                "blackbox-testcases.md", true));
                types.add(new ArtifactTypeScope(report, report, "8", "REPORT", "Report", "report.md", true));

                types.add(new ArtifactTypeScope(UUID.randomUUID(), phase0, "0-A", "PHASE0_README", "Phase 0 README",
                                "README.md", false));
                types.add(new ArtifactTypeScope(UUID.randomUUID(), phase0, "0-A", "PHASE0_PLAN", "Phase 0 Plan",
                                "phase0-plan.md", false));
                types.add(new ArtifactTypeScope(UUID.randomUUID(), phase0, "0-A", "PHASE0_EXECUTION_LOG",
                                "Phase 0 Execution Log", "phase0-execution-log.md", false));
                types.add(new ArtifactTypeScope(UUID.randomUUID(), phase0, "0-A", "PHASE0_DECISIONS",
                                "Phase 0 Decisions",
                                "phase0-decisions.md", false));
                types.add(new ArtifactTypeScope(UUID.randomUUID(), phase0, "0-A", "PHASE0_RISK_REGISTER",
                                "Phase 0 Risk Register", "phase0-risk-register.md", false));
                types.add(new ArtifactTypeScope(UUID.randomUUID(), phase0, "0-A", "PHASE0_REVIEW", "Phase 0 Review",
                                "phase0-review.md", false));
                types.add(new ArtifactTypeScope(UUID.randomUUID(), phase0, "0-A", "PHASE0_SOURCE_AVAILABILITY",
                                "Phase 0 Source Availability", "source-availability.md", false));
                return types;
        }

        private static final class FakeGitHubArtifactScannerSource implements ArtifactScannerSourcePort {
                private final String revisionSha;
                private final OffsetDateTime committedAt;
                private final Map<String, byte[]> contentByPath = new LinkedHashMap<>();
                private final Map<String, byte[]> blobBySha = new HashMap<>();

                private FakeGitHubArtifactScannerSource(String revisionSha, OffsetDateTime committedAt) {
                        this.revisionSha = revisionSha;
                        this.committedAt = committedAt;
                }

                private void put(String path, String content) {
                        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                        contentByPath.put(path, bytes);
                        blobBySha.put(sha256(bytes), bytes);
                }

                private void remove(String path) {
                        contentByPath.remove(path);
                }

                @Override
                public ResolvedRevision resolveRevision(String repositoryFullName, String refOrSha) {
                        return new ResolvedRevision(revisionSha, committedAt);
                }

                @Override
                public Map<String, GitHubTreeEntry> listTree(String repositoryFullName, String revisionSha) {
                        Map<String, GitHubTreeEntry> entries = new LinkedHashMap<>();
                        for (Map.Entry<String, byte[]> entry : contentByPath.entrySet()) {
                                byte[] bytes = entry.getValue();
                                String blobSha = sha256(bytes);
                                entries.put(entry.getKey(), new GitHubTreeEntry(entry.getKey(), blobSha, "blob",
                                                (long) bytes.length));
                        }
                        return entries;
                }

                @Override
                public byte[] readBlob(String repositoryFullName, String blobSha) {
                        byte[] bytes = blobBySha.get(blobSha);
                        if (bytes == null) {
                                throw new IllegalStateException("Missing blob " + blobSha);
                        }
                        return bytes;
                }

                private static String sha256(byte[] bytes) {
                        try {
                                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                                return java.util.HexFormat.of().formatHex(digest.digest(bytes));
                        } catch (Exception ex) {
                                throw new IllegalStateException("Unable to hash content", ex);
                        }
                }
        }

        private static final class BranchAwareFakeGitHubArtifactScannerSource implements ArtifactScannerSourcePort {
                private final String featureRevisionSha;
                private final String mainRevisionSha;
                private final OffsetDateTime featureCommittedAt;
                private final OffsetDateTime mainCommittedAt;
                private final Map<String, Map<String, byte[]>> contentByRevision = new LinkedHashMap<>();
                private final Map<String, Map<String, byte[]>> blobByRevision = new LinkedHashMap<>();

                private BranchAwareFakeGitHubArtifactScannerSource(String featureRevisionSha,
                                String mainRevisionSha,
                                OffsetDateTime featureCommittedAt,
                                OffsetDateTime mainCommittedAt) {
                        this.featureRevisionSha = featureRevisionSha;
                        this.mainRevisionSha = mainRevisionSha;
                        this.featureCommittedAt = featureCommittedAt;
                        this.mainCommittedAt = mainCommittedAt;
                        contentByRevision.put(featureRevisionSha, new LinkedHashMap<>());
                        contentByRevision.put(mainRevisionSha, new LinkedHashMap<>());
                        blobByRevision.put(featureRevisionSha, new LinkedHashMap<>());
                        blobByRevision.put(mainRevisionSha, new LinkedHashMap<>());
                }

                private void putTicketArtifact(String revisionSha, String path, String content) {
                        put(revisionSha, path, content);
                }

                private void putPhase0Artifact(String revisionSha, String path, String content) {
                        put(revisionSha, path, content);
                }

                private void put(String revisionSha, String path, String content) {
                        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                        contentByRevision.get(revisionSha).put(path, bytes);
                        blobByRevision.get(revisionSha).put(sha256(bytes), bytes);
                }

                @Override
                public ResolvedRevision resolveRevision(String repositoryFullName, String refOrSha) {
                        if ("main".equals(refOrSha)) {
                                return new ResolvedRevision(mainRevisionSha, mainCommittedAt);
                        }
                        return new ResolvedRevision(featureRevisionSha, featureCommittedAt);
                }

                @Override
                public Map<String, GitHubTreeEntry> listTree(String repositoryFullName, String revisionSha) {
                        Map<String, GitHubTreeEntry> entries = new LinkedHashMap<>();
                        Map<String, byte[]> contentByPath = contentByRevision.get(revisionSha);
                        if (contentByPath == null) {
                                return entries;
                        }
                        for (Map.Entry<String, byte[]> entry : contentByPath.entrySet()) {
                                byte[] bytes = entry.getValue();
                                String blobSha = sha256(bytes);
                                entries.put(entry.getKey(), new GitHubTreeEntry(entry.getKey(), blobSha, "blob",
                                                (long) bytes.length));
                        }
                        return entries;
                }

                @Override
                public byte[] readBlob(String repositoryFullName, String blobSha) {
                        for (Map<String, byte[]> blobs : blobByRevision.values()) {
                                byte[] bytes = blobs.get(blobSha);
                                if (bytes != null) {
                                        return bytes;
                                }
                        }
                        throw new IllegalStateException("Missing blob " + blobSha);
                }

                private static String sha256(byte[] bytes) {
                        try {
                                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                                return java.util.HexFormat.of().formatHex(digest.digest(bytes));
                        } catch (Exception ex) {
                                throw new IllegalStateException("Unable to hash content", ex);
                        }
                }
        }

        private static final class InMemoryArtifactScannerPersistencePort implements ArtifactScannerPersistencePort {
                private final RepositoryScope repository;
                private final Map<String, TicketScope> tickets = new LinkedHashMap<>();
                private final List<ArtifactTypeScope> types;
                private final Map<UUID, ScanRun> runs = new LinkedHashMap<>();
                private final List<ArtifactSnapshot> snapshots = new ArrayList<>();
                private final Map<String, ArtifactScannerModels.ParsedAcceptanceCriteria> parsedAcceptanceCriteria = new LinkedHashMap<>();
                private final List<ArtifactScannerModels.ParsedSection> parsedSections = new ArrayList<>();
                private final List<ArtifactScannerModels.EvidenceEvent> evidenceEvents = new ArrayList<>();
                private final List<ArtifactScannerModels.DataQualityRecord> dataQualityRecords = new ArrayList<>();
                private final List<ArtifactScannerModels.ParsedIssue> parsedIssues = new ArrayList<>();
                private final Map<UUID, OffsetDateTime> ticketStartedAt = new LinkedHashMap<>();
                private final Map<UUID, OffsetDateTime> ticketCompletedAt = new LinkedHashMap<>();

                private InMemoryArtifactScannerPersistencePort(RepositoryScope repository,
                                List<TicketScope> tickets,
                                List<ArtifactTypeScope> types) {
                        this.repository = repository;
                        for (TicketScope ticket : tickets) {
                                this.tickets.put(ticket.externalTicketKey(), ticket);
                        }
                        this.types = types;
                }

                private List<ArtifactScannerModels.ParsedAcceptanceCriteria> parsedAcceptanceCriteria() {
                        return new ArrayList<>(parsedAcceptanceCriteria.values());
                }

                private List<ArtifactScannerModels.ParsedAcceptanceCriteria> activeAcceptanceCriteria() {
                        return parsedAcceptanceCriteria();
                }

                private List<ArtifactScannerModels.ParsedSection> parsedSections() {
                        return parsedSections;
                }

                private List<ArtifactScannerModels.EvidenceEvent> evidenceEvents() {
                        return evidenceEvents;
                }

                private List<ArtifactScannerModels.DataQualityRecord> dataQualityRecords() {
                        return dataQualityRecords;
                }

                private List<ArtifactScannerModels.ParsedIssue> parsedIssues() {
                        return parsedIssues;
                }

                @Override
                public Optional<RepositoryScope> findRepository(UUID repositoryId) {
                        return repository.repositoryId().equals(repositoryId) ? Optional.of(repository)
                                        : Optional.empty();
                }

                @Override
                public Optional<RepositoryScope> findRepositoryByMaskedName(String repoNameMasked) {
                        return repository.repoNameMasked().equals(repoNameMasked) ? Optional.of(repository)
                                        : Optional.empty();
                }

                @Override
                public Optional<TicketScope> findTicketByProjectIdAndExternalKey(UUID projectId,
                                String externalTicketKey) {
                        TicketScope ticket = tickets.get(externalTicketKey);
                        if (ticket == null || !repository.projectId().equals(projectId)) {
                                return Optional.empty();
                        }
                        return Optional.of(ticket);
                }

                @Override
                public TicketScope upsertMinimalTicket(UUID projectId, String externalTicketKey, String title,
                                String prStatus,
                                java.time.OffsetDateTime lastCommitAt) {
                        TicketScope existing = tickets.get(externalTicketKey);
                        if (existing != null && repository.projectId().equals(projectId)) {
                                return existing;
                        }
                        TicketScope saved = new TicketScope(UUID.randomUUID(), externalTicketKey);
                        tickets.put(externalTicketKey, saved);
                        return saved;
                }

                @Override
                public List<ArtifactTypeScope> findArtifactTypes() {
                        return types;
                }

                @Override
                public Optional<ConnectorScope> findConnectorByType(String connectorType) {
                        return "ARTIFACT_SCANNER".equals(connectorType)
                                        ? Optional.of(new ConnectorScope(UUID.randomUUID(), connectorType,
                                                        "Artifact Scanner"))
                                        : Optional.empty();
                }

                @Override
                public Optional<ArtifactSnapshot> findLatestSnapshot(UUID repositoryId, String sourcePath,
                                UUID artifactTypeId) {
                        for (int i = snapshots.size() - 1; i >= 0; i--) {
                                ArtifactSnapshot snapshot = snapshots.get(i);
                                if (snapshot.repositoryId().equals(repositoryId)
                                                && snapshot.sourcePath().equals(sourcePath)
                                                && snapshot.artifactTypeId().equals(artifactTypeId)) {
                                        return Optional.of(snapshot);
                                }
                        }
                        return Optional.empty();
                }

                @Override
                public ScanRun insertRun(ScanRun run) {
                        ScanRun saved = new ScanRun(UUID.randomUUID(), run.connectorId(), run.startedAt(),
                                        run.finishedAt(),
                                        run.status(), run.recordsRead(), run.recordsWritten(), run.errorMessage(),
                                        run.traceId());
                        runs.put(saved.connectorRunId(), saved);
                        return saved;
                }

                @Override
                public ScanRun updateRun(ScanRun run) {
                        runs.put(run.connectorRunId(), run);
                        return run;
                }

                @Override
                public Optional<ScanRun> findRun(UUID connectorRunId) {
                        return Optional.ofNullable(runs.get(connectorRunId));
                }

                @Override
                public ArtifactSnapshot insertSnapshot(ArtifactSnapshot snapshot) {
                        ArtifactSnapshot saved = new ArtifactSnapshot(
                                        UUID.randomUUID(),
                                        snapshot.connectorRunId(),
                                        snapshot.repositoryId(),
                                        snapshot.ticketId(),
                                        snapshot.ticketExternalKey(),
                                        snapshot.ticketStatus(),
                                        snapshot.ticketLastCommitAt(),
                                        snapshot.artifactTypeId(),
                                        snapshot.phaseId(),
                                        snapshot.artifactTypeCode(),
                                        snapshot.artifactName(),
                                        snapshot.defaultFileName(),
                                        snapshot.requiredFlag(),
                                        snapshot.phaseCode(),
                                        snapshot.sourcePath(),
                                        snapshot.existsFlag(),
                                        snapshot.contentHash(),
                                        snapshot.schemaVersion(),
                                        snapshot.sizeBytes(),
                                        snapshot.sourceUpdatedAt(),
                                        snapshot.templateEmptyFlag(),
                                        snapshot.needParse(),
                                        snapshot.scanStatus(),
                                        snapshot.scanMessage(),
                                        OffsetDateTime.now(ZoneOffset.UTC));
                        snapshots.add(saved);
                        return saved;
                }

                @Override
                public ArtifactSnapshot updateSnapshot(ArtifactSnapshot snapshot, UUID artifactSnapshotId) {
                        ArtifactSnapshot updated = new ArtifactSnapshot(
                                        artifactSnapshotId,
                                        snapshot.connectorRunId(),
                                        snapshot.repositoryId(),
                                        snapshot.ticketId(),
                                        snapshot.ticketExternalKey(),
                                        snapshot.ticketStatus(),
                                        snapshot.ticketLastCommitAt(),
                                        snapshot.artifactTypeId(),
                                        snapshot.phaseId(),
                                        snapshot.artifactTypeCode(),
                                        snapshot.artifactName(),
                                        snapshot.defaultFileName(),
                                        snapshot.requiredFlag(),
                                        snapshot.phaseCode(),
                                        snapshot.sourcePath(),
                                        snapshot.existsFlag(),
                                        snapshot.contentHash(),
                                        snapshot.schemaVersion(),
                                        snapshot.sizeBytes(),
                                        snapshot.sourceUpdatedAt(),
                                        snapshot.templateEmptyFlag(),
                                        snapshot.needParse(),
                                        snapshot.scanStatus(),
                                        snapshot.scanMessage(),
                                        OffsetDateTime.now(ZoneOffset.UTC));
                        snapshots.removeIf(s -> s.artifactSnapshotId().equals(artifactSnapshotId));
                        snapshots.add(updated);
                        return updated;
                }

                @Override
                public List<ArtifactSnapshot> findRunArtifacts(UUID connectorRunId) {
                        return snapshots.stream()
                                        .filter(snapshot -> snapshot.connectorRunId().equals(connectorRunId))
                                        .toList();
                }

                @Override
                public List<ArtifactSnapshot> findCurrentInventory(UUID repositoryId) {
                        Map<String, ArtifactSnapshot> latest = new LinkedHashMap<>();
                        for (ArtifactSnapshot snapshot : snapshots) {
                                if (!snapshot.repositoryId().equals(repositoryId)) {
                                        continue;
                                }
                                latest.put(snapshot.sourcePath() + "|" + snapshot.artifactTypeId(), snapshot);
                        }
                        return new ArrayList<>(latest.values());
                }

                @Override
                public List<TicketArtifactEvidence> findCurrentArtifactEvidence(UUID ticketId) {
                        return List.of();
                }

                @Override
                public Optional<UUID> findLatestTicketPhaseId(UUID ticketId) {
                        return Optional.empty();
                }

                @Override
                public Optional<UUID> findPhaseIdByCode(String phaseCode) {
                        return Optional.empty();
                }

                @Override
                public void upsertTicketPhaseStatus(UUID ticketId,
                                UUID phaseId,
                                String status,
                                OffsetDateTime startedAt,
                                OffsetDateTime completedAt,
                                boolean blockedFlag,
                                String blockReason) {
                        // No-op for legacy scanner tests that do not exercise the evaluator.
                }

                @Override
                public void deactivateAcceptanceCriteriaByTicketId(UUID ticketId) {
                        parsedAcceptanceCriteria.entrySet()
                                        .removeIf(entry -> entry.getValue().ticketId().equals(ticketId));
                }

                @Override
                public void updateTicketStartedAt(UUID ticketId, OffsetDateTime startedAt) {
                        ticketStartedAt.put(ticketId, startedAt);
                }

                @Override
                public void updateTicketCompletedAt(UUID ticketId, OffsetDateTime completedAt) {
                        ticketCompletedAt.put(ticketId, completedAt);
                }

                private OffsetDateTime ticketStartedAt(UUID ticketId) {
                        return ticketStartedAt.get(ticketId);
                }

                private OffsetDateTime ticketCompletedAt(UUID ticketId) {
                        return ticketCompletedAt.get(ticketId);
                }

                @Override
                public void insertParsedAcceptanceCriteria(ArtifactScannerModels.ParsedAcceptanceCriteria ac) {
                        parsedAcceptanceCriteria.put(ac.ticketId() + "|" + ac.acKey(), ac);
                }

                @Override
                public void deleteParsedSectionsByTicketIdAndSectionType(UUID ticketId, String sectionType) {
                        parsedSections.removeIf(section -> section.ticketId().equals(ticketId)
                                        && sectionType.equals(section.sectionType()));
                }

                @Override
                public void insertParsedSection(ArtifactScannerModels.ParsedSection section) {
                        parsedSections.add(section);
                }

                @Override
                public void deleteParsedIssuesByTicketIdAndSourceType(UUID ticketId, String sourceType) {
                        parsedIssues.removeIf(issue -> issue.ticketId().equals(ticketId)
                                        && sourceType.equals(issue.sourceType()));
                }

                @Override
                public void insertParsedDecision(ArtifactScannerModels.ParsedDecision decision) {
                        // No-op for test
                }

                @Override
                public void insertParsedIssue(ArtifactScannerModels.ParsedIssue issue) {
                        parsedIssues.add(issue);
                }

                @Override
                public void insertParsedRisk(ArtifactScannerModels.ParsedRisk risk) {
                        // No-op for test
                }

                @Override
                public void deleteParsedRisksByTicketId(UUID ticketId) {
                        // No-op for test
                }

                @Override
                public void insertEvidenceEvent(ArtifactScannerModels.EvidenceEvent event) {
                        evidenceEvents.add(event);
                }

                @Override
                public void insertDataQualityRecord(ArtifactScannerModels.DataQualityRecord quality) {
                        dataQualityRecords.add(quality);
                }

                @Override
                public void updateSnapshotParsedSummary(ArtifactScannerModels.ParsedSummaryPatch patch) {
                        // no-op in test fake
                }

                @Override
                public void upsertParsedExceptions(List<ArtifactScannerModels.ParsedException> exceptions) {
                        // no-op in test fake
                }
                
                @Override
                public void deleteParsedExceptionsByTicketId(UUID ticketId) {
                        // no-op in test fake
                }


                @Override
                public Optional<UUID> findRoleIdByName(String roleName) {
                        return Optional.empty();
                }

                @Override
                public void upsertArtifactDocumentDates(
                                List<com.sdd.platform.application.usecase.scanner.ArtifactDocumentDateService.DocumentDate> documentDates) {
                        // no-op in test fake
                }
        }
}
