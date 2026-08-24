package com.sdd.platform.application.usecase.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.port.out.integration.ArtifactScannerSourcePort;
import com.sdd.platform.application.port.out.integration.GithubPullRequestFilesPort;
import com.sdd.platform.application.port.out.persistence.AiFindingStatPort.AiFindingStatRecord;
import com.sdd.platform.application.port.out.persistence.ArtifactScannerPersistencePort;
import com.sdd.platform.application.usecase.docparse.ImplPlanParseService;
import com.sdd.platform.application.usecase.docparse.TestPlanParseService;
import com.sdd.platform.application.usecase.docparse.TestResultsParseService;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.CollectorRunResult;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactScanMode;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactScanRequest;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.TicketScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.RepositoryScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ScanRun;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactTypeScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerService;
import com.sdd.platform.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class GithubWebhookServiceTest {

        private static final String SECRET = "test-secret";
        private static final OffsetDateTime LAST_COMMIT_AT = OffsetDateTime.parse("2026-06-16T12:34:56Z");

        private GithubWebhookService service;
        private ArtifactScannerPersistencePort scannerPersistence;
        private ArtifactScannerService scannerService;
        private ArtifactScannerSourcePort scannerSourcePort;
        private GithubPullRequestFilesPort pullRequestFilesPort;
        private GitPrMetadataCollectorService collectorService;
        private GithubSecurityEvidenceSnapshotService securityEvidenceSnapshotService;
        private ImplPlanParseService implPlanParseService;
        private TestPlanParseService testPlanParseService;
        private TestResultsParseService testResultsParseService;
        private TemplateUsageStatWriter templateUsageStatWriter;
        private AiFindingStatWriter aiFindingStatWriter;

        @BeforeEach
        void setUp() {
                testPlanParseService = Mockito.mock(TestPlanParseService.class);
                testResultsParseService = Mockito.mock(TestResultsParseService.class);
                scannerPersistence = Mockito.mock(ArtifactScannerPersistencePort.class);
                scannerService = Mockito.mock(ArtifactScannerService.class);
                scannerSourcePort = Mockito.mock(ArtifactScannerSourcePort.class);
                pullRequestFilesPort = Mockito.mock(GithubPullRequestFilesPort.class);
                collectorService = Mockito.mock(GitPrMetadataCollectorService.class);
                securityEvidenceSnapshotService = Mockito.mock(GithubSecurityEvidenceSnapshotService.class);
                implPlanParseService = Mockito.mock(ImplPlanParseService.class);
                templateUsageStatWriter = Mockito.mock(TemplateUsageStatWriter.class);
                aiFindingStatWriter = Mockito.mock(AiFindingStatWriter.class);

                AppProperties.Connectors.GitHub github = new AppProperties.Connectors.GitHub("https://api.github.com",
                                "", SECRET);
                AppProperties.Connectors connectors = new AppProperties.Connectors(
                                null, github, null, null);
                AppProperties props = new AppProperties(null, null, null, null, connectors);

                service = new GithubWebhookService(
                                props,
                                scannerPersistence,
                                scannerService,
                                scannerSourcePort,
                                pullRequestFilesPort,
                                collectorService,
                                securityEvidenceSnapshotService,
                                implPlanParseService,
                                testPlanParseService,
                                testResultsParseService,
                                new ObjectMapper(),
                                templateUsageStatWriter,
                                aiFindingStatWriter);
                Mockito.when(scannerSourcePort.resolveRevision(Mockito.anyString(), Mockito.anyString()))
                                .thenReturn(new ArtifactScannerSourcePort.ResolvedRevision(
                                                "1111111111111111111111111111111111111111", null));
                Mockito.when(scannerSourcePort.listTree(Mockito.anyString(), Mockito.anyString()))
                                .thenReturn(Map.of());
                Mockito.when(scannerSourcePort.readBlob(Mockito.anyString(), Mockito.anyString()))
                                .thenReturn(new byte[0]);
                Mockito.when(implPlanParseService.parseAndStore(Mockito.any()))
                                .thenAnswer(invocation -> invocation.getArgument(0) == null ? null : null);
                Mockito.when(collectorService.collectFromWebhook(Mockito.any(UUID.class), Mockito.anyInt(),
                                Mockito.anyString()))
                        .thenReturn(new CollectorRunResult(
                                                UUID.randomUUID(),
                                                repositoryId(),
                                                42,
                                                "SUCCESS",
                                                1,
                                                1,
                                                1,
                                                0,
                                                "d2"));
                Mockito.when(securityEvidenceSnapshotService.collectFromPullRequest(
                                Mockito.anyString(),
                                Mockito.anyString(),
                                Mockito.anyString(),
                                Mockito.any(),
                                Mockito.anyList(),
                                Mockito.anyString()))
                        .thenReturn(new GithubSecurityEvidenceSnapshotService.Result("ignored:no-security-evidence", 0));
        }

        @Test
        void rejects_when_secret_not_configured() {
                AppProperties.Connectors.GitHub noSecret = new AppProperties.Connectors.GitHub("https://api.github.com",
                                "", "");
                AppProperties.Connectors connectors = new AppProperties.Connectors(null, noSecret, null, null);
                AppProperties props = new AppProperties(null, null, null, null, connectors);
                GithubWebhookService s = new GithubWebhookService(
                                props,
                                scannerPersistence,
                                scannerService,
                                scannerSourcePort,
                                pullRequestFilesPort,
                                collectorService,
                                securityEvidenceSnapshotService,
                                implPlanParseService,
                                testPlanParseService,
                                testResultsParseService,
                                new ObjectMapper(),
                                templateUsageStatWriter,
                                aiFindingStatWriter);

                assertThrows(SecurityException.class,
                                () -> s.handle("{}".getBytes(), "sha256=anything", "ping", "d1"));
        }

        @Test
        void rejects_missing_signature_header() {
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                assertThrows(SecurityException.class,
                                () -> service.handle(body, null, "ping", "d1"));
                assertThrows(SecurityException.class,
                                () -> service.handle(body, "not-sha256-prefixed", "ping", "d1"));
        }

        @Test
        void rejects_wrong_signature() {
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                assertThrows(SecurityException.class,
                                () -> service.handle(body, "sha256=deadbeef", "ping", "d1"));
        }

        @Test
        void accepts_valid_signature_on_ping() {
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                String sig = "sha256=" + hmac(SECRET, body);

                var result = service.handle(body, sig, "ping", "d1");

                assertEquals("ping", result.handled());
                assertEquals(0, result.recordsAffected());
        }

        @Test
        void rejects_malformed_json_after_signature_check() {
                byte[] body = "not-json".getBytes(StandardCharsets.UTF_8);
                String sig = "sha256=" + hmac(SECRET, body);

                assertThrows(IllegalArgumentException.class,
                                () -> service.handle(body, sig, "pull_request", "d1"));
        }

        @Test
        void ignores_unknown_event_types() {
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                String sig = "sha256=" + hmac(SECRET, body);

                var result = service.handle(body, sig, "fork", "d1");

                assertEquals("ignored:fork", result.handled());
        }

        @Test
        void pull_request_opened_triggers_artifact_scan_and_ticket_upsert() {
                String body = """
                                {
                                  "action": "opened",
                                  "repository": { "full_name": "acme/widget" },
                                  "pull_request": {
                                    "number": 42,
                                    "state": "open",
                                    "head": { "ref": "feature/widget", "sha": "1111111111111111111111111111111111111111" },
                                    "title": "ARTIFACT-SCANNER: Update docs",
                                    "base": { "ref": "main" }
                                  }
                                }
                                """;
                byte[] raw = body.getBytes(StandardCharsets.UTF_8);
                String sig = "sha256=" + hmac(SECRET, raw);
                RepositoryScope repository = new RepositoryScope(
                                UUID.fromString("99e85aef-8cf4-4b3c-8863-90e2740e7549"),
                                UUID.fromString("578a0d3b-341c-4b8a-905d-3cb5507ea70c"),
                                "acme/widget",
                                "main");
                Mockito.when(scannerPersistence.findRepositoryByMaskedName("acme/widget"))
                                .thenReturn(Optional.of(repository));
                Mockito.when(scannerPersistence.upsertMinimalTicket(repository.projectId(), "ARTIFACT-SCANNER",
                                "ARTIFACT-SCANNER: Update docs", "OPEN", LAST_COMMIT_AT))
                                .thenReturn(new TicketScope(UUID.randomUUID(), "ARTIFACT-SCANNER"));
                Mockito.when(pullRequestFilesPort.listChangedFilePaths("acme/widget", 42))
                                .thenReturn(java.util.List.of(
                                                "docs/changes/ARTIFACT-SCANNER/spec-pack.md",
                                                "docs/changes/ARTIFACT-SCANNER/impl-plan.md"));
                Mockito.when(pullRequestFilesPort.resolveLastCommitAt("acme/widget", 42))
                                .thenReturn(Optional.of(LAST_COMMIT_AT));
                Mockito.when(scannerService.scan(any())).thenReturn(new ScanRun(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                OffsetDateTime.now(ZoneOffset.UTC),
                                OffsetDateTime.now(ZoneOffset.UTC),
                                "SUCCESS",
                                7,
                                7,
                                null,
                                "d2"));

                var result = service.handle(raw, sig, "pull_request", "d2");

                assertEquals("pull_request:opened", result.handled());
                verify(collectorService).collectFromWebhook(repository.repositoryId(), 42, "d2");
                verify(scannerPersistence).upsertMinimalTicket(repository.projectId(), "ARTIFACT-SCANNER",
                                "ARTIFACT-SCANNER: Update docs", "OPEN", LAST_COMMIT_AT);
                verify(pullRequestFilesPort).listChangedFilePaths("acme/widget", 42);
                verify(pullRequestFilesPort).resolveLastCommitAt("acme/widget", 42);
                ArgumentCaptor<ArtifactScanRequest> captor = ArgumentCaptor.forClass(ArtifactScanRequest.class);
                verify(scannerService, times(1)).scan(captor.capture());
                assertEquals(ArtifactScanMode.TICKET_SCOPED, captor.getValue().scanMode());
                assertEquals(java.util.List.of("ARTIFACT-SCANNER"), captor.getValue().ticketIds());
                assertEquals("1111111111111111111111111111111111111111", captor.getValue().branchOrRef());
        }

        @Test
        void pull_request_opened_continues_when_collector_fails() {
                String body = """
                                {
                                  "action": "opened",
                                  "repository": { "full_name": "acme/widget" },
                                  "pull_request": {
                                    "number": 42,
                                    "state": "open",
                                    "head": { "ref": "feature/widget", "sha": "1111111111111111111111111111111111111111" },
                                    "title": "ARTIFACT-SCANNER: Update docs",
                                    "base": { "ref": "main" }
                                  }
                                }
                                """;
                byte[] raw = body.getBytes(StandardCharsets.UTF_8);
                String sig = "sha256=" + hmac(SECRET, raw);
                RepositoryScope repository = new RepositoryScope(
                                UUID.fromString("99e85aef-8cf4-4b3c-8863-90e2740e7549"),
                                UUID.fromString("578a0d3b-341c-4b8a-905d-3cb5507ea70c"),
                                "acme/widget",
                                "main");
                Mockito.when(scannerPersistence.findRepositoryByMaskedName("acme/widget"))
                                .thenReturn(Optional.of(repository));
                Mockito.when(scannerPersistence.upsertMinimalTicket(repository.projectId(), "ARTIFACT-SCANNER",
                                "ARTIFACT-SCANNER: Update docs", "OPEN", LAST_COMMIT_AT))
                                .thenReturn(new TicketScope(UUID.randomUUID(), "ARTIFACT-SCANNER"));
                Mockito.when(pullRequestFilesPort.listChangedFilePaths("acme/widget", 42))
                                .thenReturn(java.util.List.of(
                                                "docs/changes/ARTIFACT-SCANNER/spec-pack.md",
                                                "docs/changes/ARTIFACT-SCANNER/impl-plan.md"));
                Mockito.when(pullRequestFilesPort.resolveLastCommitAt("acme/widget", 42))
                                .thenReturn(Optional.of(LAST_COMMIT_AT));
                Mockito.when(collectorService.collectFromWebhook(Mockito.any(UUID.class), Mockito.anyInt(),
                                Mockito.anyString()))
                                .thenReturn(new CollectorRunResult(
                                                UUID.randomUUID(),
                                                repositoryId(),
                                                42,
                                                "FAILED",
                                                0,
                                                0,
                                                0,
                                                1,
                                                "d2"));
                Mockito.when(scannerService.scan(any())).thenReturn(new ScanRun(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                OffsetDateTime.now(ZoneOffset.UTC),
                                OffsetDateTime.now(ZoneOffset.UTC),
                                "SUCCESS",
                                7,
                                7,
                                null,
                                "d2"));

                var result = service.handle(raw, sig, "pull_request", "d2");

                assertEquals("pull_request:opened", result.handled());
                verify(collectorService).collectFromWebhook(repository.repositoryId(), 42, "d2");
                verify(scannerService, times(1)).scan(any());
        }

        @Test
        void pull_request_opened_upserts_all_ticket_scopes_and_scans_once() {
                String body = """
                                {
                                  "action": "opened",
                                  "repository": { "full_name": "acme/widget" },
                                  "pull_request": {
                                    "number": 42,
                                    "state": "open",
                                    "head": { "ref": "feature/widget", "sha": "1111111111111111111111111111111111111111" },
                                    "title": "Multi scope update",
                                    "base": { "ref": "main" }
                                  }
                                }
                                """;
                byte[] raw = body.getBytes(StandardCharsets.UTF_8);
                String sig = "sha256=" + hmac(SECRET, raw);
                RepositoryScope repository = new RepositoryScope(
                                UUID.fromString("99e85aef-8cf4-4b3c-8863-90e2740e7549"),
                                UUID.fromString("578a0d3b-341c-4b8a-905d-3cb5507ea70c"),
                                "acme/widget",
                                "main");
                Mockito.when(scannerPersistence.findRepositoryByMaskedName("acme/widget"))
                                .thenReturn(Optional.of(repository));
                Mockito.when(scannerPersistence.upsertMinimalTicket(
                                repository.projectId(),
                                "CUSTOMER",
                                "Multi scope update",
                                "OPEN",
                                LAST_COMMIT_AT))
                                .thenReturn(new TicketScope(UUID.randomUUID(), "CUSTOMER"));
                Mockito.when(scannerPersistence.upsertMinimalTicket(
                                repository.projectId(),
                                "ORGANIZATION",
                                "Multi scope update",
                                "OPEN",
                                LAST_COMMIT_AT))
                                .thenReturn(new TicketScope(UUID.randomUUID(), "ORGANIZATION"));
                Mockito.when(pullRequestFilesPort.listChangedFilePaths("acme/widget", 42))
                                .thenReturn(java.util.List.of(
                                                "docs/changes/CUSTOMER/spec-pack.md",
                                                "docs/changes/CUSTOMER/impl-plan.md",
                                                "docs/changes/ORGANIZATION/spec-pack.md",
                                                "docs/changes/ORGANIZATION/impl-plan.md"));
                Mockito.when(pullRequestFilesPort.resolveLastCommitAt("acme/widget", 42))
                                .thenReturn(Optional.of(LAST_COMMIT_AT));
                Mockito.when(scannerService.scan(any())).thenReturn(new ScanRun(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                OffsetDateTime.now(ZoneOffset.UTC),
                                OffsetDateTime.now(ZoneOffset.UTC),
                                "SUCCESS",
                                14,
                                14,
                                null,
                                "d-multi"));

                var result = service.handle(raw, sig, "pull_request", "d-multi");

                assertEquals("pull_request:opened", result.handled());
                verify(collectorService).collectFromWebhook(repository.repositoryId(), 42, "d-multi");
                verify(scannerPersistence).upsertMinimalTicket(repository.projectId(), "CUSTOMER", "Multi scope update",
                                "OPEN", LAST_COMMIT_AT);
                verify(scannerPersistence).upsertMinimalTicket(repository.projectId(), "ORGANIZATION",
                                "Multi scope update", "OPEN", LAST_COMMIT_AT);
                ArgumentCaptor<ArtifactScanRequest> captor = ArgumentCaptor.forClass(ArtifactScanRequest.class);
                verify(scannerService, times(1)).scan(captor.capture());
                assertEquals(ArtifactScanMode.TICKET_SCOPED, captor.getValue().scanMode());
                assertEquals(java.util.List.of("CUSTOMER", "ORGANIZATION"), captor.getValue().ticketIds());
        }

        @Test
        void pull_request_event_for_unknown_repo_returns_zero_records() {
                String body = """
                                {
                                  "repository": { "full_name": "unknown/repo" },
                                  "pull_request": { "number": 1 }
                                }
                                """;
                byte[] raw = body.getBytes(StandardCharsets.UTF_8);
                String sig = "sha256=" + hmac(SECRET, raw);

                var result = service.handle(raw, sig, "pull_request", "d3");

                assertEquals(0, result.recordsAffected());
        }

        @Test
        void push_event_triggers_artifact_scan_for_matching_branch() {
                String body = """
                                {
                                  "ref": "refs/heads/main",
                                  "after": "2222222222222222222222222222222222222222",
                                  "repository": { "full_name": "acme/widget" }
                                }
                                """;
                byte[] raw = body.getBytes(StandardCharsets.UTF_8);
                String sig = "sha256=" + hmac(SECRET, raw);

                var result = service.handle(raw, sig, "push", "d4");

                assertEquals("ignored:push", result.handled());
                verify(scannerService, never()).scan(any());
        }

        @Test
        void pull_request_synchronize_triggers_rescan_and_refreshes_ticket_state() {
                String body = """
                                {
                                  "action": "synchronize",
                                  "repository": { "full_name": "acme/widget" },
                                  "pull_request": {
                                    "number": 42,
                                    "state": "open",
                                    "head": { "ref": "feature/widget", "sha": "3333333333333333333333333333333333333333" },
                                    "title": "ARTIFACT-SCANNER: Update docs",
                                    "base": { "ref": "main" }
                                  }
                                }
                                """;
                byte[] raw = body.getBytes(StandardCharsets.UTF_8);
                String sig = "sha256=" + hmac(SECRET, raw);
                RepositoryScope repository = new RepositoryScope(
                                UUID.fromString("99e85aef-8cf4-4b3c-8863-90e2740e7549"),
                                UUID.fromString("578a0d3b-341c-4b8a-905d-3cb5507ea70c"),
                                "acme/widget",
                                "main");
                Mockito.when(scannerPersistence.findRepositoryByMaskedName("acme/widget"))
                                .thenReturn(Optional.of(repository));
                Mockito.when(scannerPersistence.upsertMinimalTicket(repository.projectId(), "ARTIFACT-SCANNER",
                                "ARTIFACT-SCANNER: Update docs", "OPEN", LAST_COMMIT_AT))
                                .thenReturn(new TicketScope(UUID.randomUUID(), "ARTIFACT-SCANNER"));
                Mockito.when(pullRequestFilesPort.listChangedFilePaths("acme/widget", 42))
                                .thenReturn(java.util.List.of("docs/changes/ARTIFACT-SCANNER/spec-pack.md"));
                Mockito.when(pullRequestFilesPort.resolveLastCommitAt("acme/widget", 42))
                                .thenReturn(Optional.of(LAST_COMMIT_AT));
                Mockito.when(scannerService.scan(any())).thenReturn(new ScanRun(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                OffsetDateTime.now(ZoneOffset.UTC),
                                OffsetDateTime.now(ZoneOffset.UTC),
                                "SUCCESS",
                                7,
                                7,
                                null,
                                "d2"));

                var result = service.handle(raw, sig, "pull_request", "d4-sync");

                assertEquals("pull_request:synchronize", result.handled());
                verify(collectorService).collectFromWebhook(repository.repositoryId(), 42, "d4-sync");
                verify(scannerPersistence).upsertMinimalTicket(repository.projectId(), "ARTIFACT-SCANNER",
                                "ARTIFACT-SCANNER: Update docs", "OPEN", LAST_COMMIT_AT);
                verify(scannerService, times(1)).scan(any());
        }

        @Test
        void pull_request_reopened_triggers_rescan_and_reopens_ticket() {
                String body = """
                                {
                                  "action": "reopened",
                                  "repository": { "full_name": "acme/widget" },
                                  "pull_request": {
                                    "number": 42,
                                    "state": "open",
                                    "head": { "ref": "feature/widget", "sha": "4444444444444444444444444444444444444444" },
                                    "title": "ARTIFACT-SCANNER: Update docs",
                                    "base": { "ref": "main" }
                                  }
                                }
                                """;
                byte[] raw = body.getBytes(StandardCharsets.UTF_8);
                String sig = "sha256=" + hmac(SECRET, raw);
                RepositoryScope repository = new RepositoryScope(
                                UUID.fromString("99e85aef-8cf4-4b3c-8863-90e2740e7549"),
                                UUID.fromString("578a0d3b-341c-4b8a-905d-3cb5507ea70c"),
                                "acme/widget",
                                "main");
                Mockito.when(scannerPersistence.findRepositoryByMaskedName("acme/widget"))
                                .thenReturn(Optional.of(repository));
                Mockito.when(scannerPersistence.upsertMinimalTicket(repository.projectId(), "ARTIFACT-SCANNER",
                                "ARTIFACT-SCANNER: Update docs", "OPEN", LAST_COMMIT_AT))
                                .thenReturn(new TicketScope(UUID.randomUUID(), "ARTIFACT-SCANNER"));
                Mockito.when(pullRequestFilesPort.listChangedFilePaths("acme/widget", 42))
                                .thenReturn(java.util.List.of("docs/changes/ARTIFACT-SCANNER/spec-pack.md"));
                Mockito.when(pullRequestFilesPort.resolveLastCommitAt("acme/widget", 42))
                                .thenReturn(Optional.of(LAST_COMMIT_AT));
                Mockito.when(scannerService.scan(any())).thenReturn(new ScanRun(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                OffsetDateTime.now(ZoneOffset.UTC),
                                OffsetDateTime.now(ZoneOffset.UTC),
                                "SUCCESS",
                                7,
                                7,
                                null,
                                "d2"));

                var result = service.handle(raw, sig, "pull_request", "d4-reopen");

                assertEquals("pull_request:reopened", result.handled());
                verify(collectorService).collectFromWebhook(repository.repositoryId(), 42, "d4-reopen");
                verify(scannerPersistence).upsertMinimalTicket(repository.projectId(), "ARTIFACT-SCANNER",
                                "ARTIFACT-SCANNER: Update docs", "OPEN", LAST_COMMIT_AT);
                verify(scannerService, times(1)).scan(any());
        }

        @Test
        void pull_request_closed_event_updates_status_without_scanning() {
                String body = """
                                {
                                  "action": "closed",
                                  "repository": { "full_name": "acme/widget" },
                                  "pull_request": {
                                    "merged": false,
                                    "base": { "ref": "main" },
                                    "number": 42,
                                    "state": "closed"
                                  }
                                }
                                """;
                byte[] raw = body.getBytes(StandardCharsets.UTF_8);
                String sig = "sha256=" + hmac(SECRET, raw);

                Mockito.when(scannerPersistence.findRepositoryByMaskedName("acme/widget"))
                                .thenReturn(Optional.of(new RepositoryScope(
                                                UUID.fromString("99e85aef-8cf4-4b3c-8863-90e2740e7549"),
                                                UUID.fromString("578a0d3b-341c-4b8a-905d-3cb5507ea70c"),
                                                "acme/widget",
                                                "main")));
                Mockito.when(scannerPersistence.upsertMinimalTicket(
                                UUID.fromString("578a0d3b-341c-4b8a-905d-3cb5507ea70c"), "ARTIFACT-SCANNER",
                                "ARTIFACT-SCANNER", "CLOSED", LAST_COMMIT_AT))
                                .thenReturn(new TicketScope(UUID.randomUUID(), "ARTIFACT-SCANNER"));
                Mockito.when(pullRequestFilesPort.listChangedFilePaths("acme/widget", 42))
                                .thenReturn(java.util.List.of("docs/changes/ARTIFACT-SCANNER/spec-pack.md"));
                Mockito.when(pullRequestFilesPort.resolveLastCommitAt("acme/widget", 42))
                                .thenReturn(Optional.of(LAST_COMMIT_AT));
                var result = service.handle(raw, sig, "pull_request", "d5");

                assertEquals("pull_request:closed", result.handled());
                verify(collectorService).collectFromWebhook(UUID.fromString("99e85aef-8cf4-4b3c-8863-90e2740e7549"), 42,
                                "d5");
                verify(scannerService, never()).scan(any());
                verify(scannerPersistence).upsertMinimalTicket(UUID.fromString("578a0d3b-341c-4b8a-905d-3cb5507ea70c"),
                                "ARTIFACT-SCANNER", "ARTIFACT-SCANNER", "CLOSED", LAST_COMMIT_AT);
        }

        @Test
        void pull_request_closed_merged_event_updates_status_without_scanning() {
                String body = """
                                {
                                  "action": "closed",
                                  "repository": { "full_name": "acme/widget" },
                                  "pull_request": {
                                    "merged": true,
                                    "base": { "ref": "main" },
                                    "number": 42,
                                    "state": "closed"
                                  }
                                }
                                """;
                byte[] raw = body.getBytes(StandardCharsets.UTF_8);
                String sig = "sha256=" + hmac(SECRET, raw);

                Mockito.when(scannerPersistence.findRepositoryByMaskedName("acme/widget"))
                                .thenReturn(Optional.of(new RepositoryScope(
                                                UUID.fromString("99e85aef-8cf4-4b3c-8863-90e2740e7549"),
                                                UUID.fromString("578a0d3b-341c-4b8a-905d-3cb5507ea70c"),
                                                "acme/widget",
                                                "main")));
                Mockito.when(scannerPersistence.upsertMinimalTicket(
                                UUID.fromString("578a0d3b-341c-4b8a-905d-3cb5507ea70c"), "ARTIFACT-SCANNER",
                                "ARTIFACT-SCANNER", "MERGED", LAST_COMMIT_AT))
                                .thenReturn(new TicketScope(UUID.randomUUID(), "ARTIFACT-SCANNER"));
                Mockito.when(pullRequestFilesPort.listChangedFilePaths("acme/widget", 42))
                                .thenReturn(java.util.List.of("docs/changes/ARTIFACT-SCANNER/spec-pack.md"));
                Mockito.when(pullRequestFilesPort.resolveLastCommitAt("acme/widget", 42))
                                .thenReturn(Optional.of(LAST_COMMIT_AT));

                var result = service.handle(raw, sig, "pull_request", "d5-merged");

                assertEquals("pull_request:closed", result.handled());
                verify(collectorService).collectFromWebhook(UUID.fromString("99e85aef-8cf4-4b3c-8863-90e2740e7549"), 42,
                                "d5-merged");
                verify(scannerService, never()).scan(any());
                verify(scannerPersistence).upsertMinimalTicket(UUID.fromString("578a0d3b-341c-4b8a-905d-3cb5507ea70c"),
                                "ARTIFACT-SCANNER", "ARTIFACT-SCANNER", "MERGED", LAST_COMMIT_AT);
        }

        @Test
        void push_event_ignores_non_target_branch() {
                String body = """
                                {
                                  "ref": "refs/heads/feature/foo",
                                  "repository": { "full_name": "acme/widget" }
                                }
                                """;
                byte[] raw = body.getBytes(StandardCharsets.UTF_8);
                String sig = "sha256=" + hmac(SECRET, raw);

                RepositoryScope repository = new RepositoryScope(
                                UUID.fromString("99e85aef-8cf4-4b3c-8863-90e2740e7549"),
                                UUID.fromString("578a0d3b-341c-4b8a-905d-3cb5507ea70c"),
                                "acme/widget",
                                "main");
                Mockito.when(scannerPersistence.findRepositoryByMaskedName("acme/widget"))
                                .thenReturn(Optional.of(repository));

                var result = service.handle(raw, sig, "push", "d6");

                assertEquals("ignored:push", result.handled());
                verify(scannerService, never()).scan(any());
        }

        @Test
        void webhook_duplicate_delivery_same_id_treated_idempotent() {
                // This test verifies that when the same webhook delivery ID is sent twice,
                // the collector service is invoked but idempotency is maintained at the DB
                // layer.
                String body = """
                                {
                                  "action": "opened",
                                  "repository": { "full_name": "acme/widget" },
                                  "pull_request": {
                                    "number": 99,
                                    "state": "open",
                                    "head": { "ref": "feature/dup", "sha": "5555555555555555555555555555555555555555" },
                                    "title": "GIT-PR-METADATA-COLLECTOR: Implement feature",
                                    "base": { "ref": "main" }
                                  }
                                }
                                """;
                byte[] raw = body.getBytes(StandardCharsets.UTF_8);
                String sig = "sha256=" + hmac(SECRET, raw);
                RepositoryScope repository = new RepositoryScope(
                                UUID.fromString("99e85aef-8cf4-4b3c-8863-90e2740e7549"),
                                UUID.fromString("578a0d3b-341c-4b8a-905d-3cb5507ea70c"),
                                "acme/widget",
                                "main");
                Mockito.when(scannerPersistence.findRepositoryByMaskedName("acme/widget"))
                                .thenReturn(Optional.of(repository));
                Mockito.when(scannerPersistence.upsertMinimalTicket(
                                repository.projectId(),
                                "GIT-PR-METADATA-COLLECTOR",
                                "GIT-PR-METADATA-COLLECTOR: Implement feature",
                                "OPEN",
                                LAST_COMMIT_AT))
                                .thenReturn(new TicketScope(UUID.randomUUID(), "GIT-PR-METADATA-COLLECTOR"));
                Mockito.when(pullRequestFilesPort.listChangedFilePaths("acme/widget", 99))
                                .thenReturn(java.util.List.of("docs/changes/GIT-PR-METADATA-COLLECTOR/spec-pack.md"));
                Mockito.when(pullRequestFilesPort.resolveLastCommitAt("acme/widget", 99))
                                .thenReturn(Optional.of(LAST_COMMIT_AT));
                Mockito.when(scannerService.scan(any())).thenReturn(new ScanRun(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                OffsetDateTime.now(ZoneOffset.UTC),
                                OffsetDateTime.now(ZoneOffset.UTC),
                                "SUCCESS",
                                1,
                                1,
                                null,
                                "dup-delivery-1"));

                // First delivery
                var result1 = service.handle(raw, sig, "pull_request", "dup-delivery-id-1");
                assertEquals("pull_request:opened", result1.handled());
                verify(collectorService, times(1)).collectFromWebhook(repository.repositoryId(), 99,
                                "dup-delivery-id-1");

                // Second delivery with different delivery ID (simulates retry)
                var result2 = service.handle(raw, sig, "pull_request", "dup-delivery-id-2");
                assertEquals("pull_request:opened", result2.handled());
                // Both calls should go through, idempotency is at DB layer
                verify(collectorService).collectFromWebhook(eq(repository.repositoryId()), eq(99),
                                eq("dup-delivery-id-2"));
        }

        @Test
        void pull_request_event_includes_artifact_scanner_flow_for_regression() {
                // This test ensures that Artifact Scanner behavior is preserved after the
                // collector integration
                String body = """
                                {
                                  "action": "opened",
                                  "repository": { "full_name": "acme/widget" },
                                  "pull_request": {
                                    "number": 77,
                                    "state": "open",
                                    "head": { "ref": "feature/artifact-scanner-regression", "sha": "6666666666666666666666666666666666666666" },
                                    "title": "ARTIFACT-SCANNER: Regression test",
                                    "base": { "ref": "main" }
                                  }
                                }
                                """;
                byte[] raw = body.getBytes(StandardCharsets.UTF_8);
                String sig = "sha256=" + hmac(SECRET, raw);
                RepositoryScope repository = new RepositoryScope(
                                UUID.fromString("99e85aef-8cf4-4b3c-8863-90e2740e7549"),
                                UUID.fromString("578a0d3b-341c-4b8a-905d-3cb5507ea70c"),
                                "acme/widget",
                                "main");
                Mockito.when(scannerPersistence.findRepositoryByMaskedName("acme/widget"))
                                .thenReturn(Optional.of(repository));
                Mockito.when(scannerPersistence.upsertMinimalTicket(
                                repository.projectId(),
                                "ARTIFACT-SCANNER",
                                "ARTIFACT-SCANNER: Regression test",
                                "OPEN",
                                LAST_COMMIT_AT))
                                .thenReturn(new TicketScope(UUID.randomUUID(), "ARTIFACT-SCANNER"));
                Mockito.when(pullRequestFilesPort.listChangedFilePaths("acme/widget", 77))
                                .thenReturn(java.util.List.of("docs/changes/ARTIFACT-SCANNER/spec-pack.md"));
                Mockito.when(pullRequestFilesPort.resolveLastCommitAt("acme/widget", 77))
                                .thenReturn(Optional.of(LAST_COMMIT_AT));
                Mockito.when(scannerService.scan(any())).thenReturn(new ScanRun(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                OffsetDateTime.now(ZoneOffset.UTC),
                                OffsetDateTime.now(ZoneOffset.UTC),
                                "SUCCESS",
                                1,
                                1,
                                null,
                                "artifact-regression"));

                var result = service.handle(raw, sig, "pull_request", "artifact-regression");

                // Verify that BOTH the collector AND the scanner flow are triggered
                assertEquals("pull_request:opened", result.handled());
                verify(collectorService).collectFromWebhook(repository.repositoryId(), 77, "artifact-regression");
                verify(scannerPersistence).upsertMinimalTicket(repository.projectId(), "ARTIFACT-SCANNER",
                                "ARTIFACT-SCANNER: Regression test", "OPEN", LAST_COMMIT_AT);
                ArgumentCaptor<ArtifactScanRequest> captor = ArgumentCaptor.forClass(ArtifactScanRequest.class);
                verify(scannerService, times(1)).scan(captor.capture());
                assertEquals(ArtifactScanMode.TICKET_SCOPED, captor.getValue().scanMode());
                assertEquals(java.util.List.of("ARTIFACT-SCANNER"), captor.getValue().ticketIds());
        }

        private static String hmac(String secret, byte[] body) {
                try {
                        Mac mac = Mac.getInstance("HmacSHA256");
                        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                        return HexFormat.of().formatHex(mac.doFinal(body));
                } catch (Exception e) {
                        throw new RuntimeException(e);
                }
        }

        private UUID repositoryId() {
                return UUID.fromString("99e85aef-8cf4-4b3c-8863-90e2740e7549");
        }

        @Test
        void pull_request_review_edited_triggers_recollection() {
                String body = """
                                {
                                  "action": "edited",
                                  "repository": { "full_name": "acme/widget" },
                                  "pull_request": {
                                    "number": 42,
                                    "base": { "ref": "main" }
                                  }
                                }
                                """;
                byte[] raw = body.getBytes(StandardCharsets.UTF_8);
                String sig = "sha256=" + hmac(SECRET, raw);
                RepositoryScope repository = new RepositoryScope(
                                repositoryId(),
                                UUID.fromString("578a0d3b-341c-4b8a-905d-3cb5507ea70c"),
                                "acme/widget",
                                "main");
                Mockito.when(scannerPersistence.findRepositoryByMaskedName("acme/widget"))
                                .thenReturn(Optional.of(repository));

                var result = service.handle(raw, sig, "pull_request_review", "d3");

                assertEquals("pull_request_review:edited", result.handled());
                verify(collectorService).collectFromWebhook(repository.repositoryId(), 42, "d3");
        }

        @Test
        void pull_request_review_ignores_unsupported_action() {
                String body = """
                                {
                                  "action": "review_requested",
                                  "repository": { "full_name": "acme/widget" },
                                  "pull_request": {
                                    "number": 42,
                                    "base": { "ref": "main" }
                                  }
                                }
                                """;
                byte[] raw = body.getBytes(StandardCharsets.UTF_8);
                String sig = "sha256=" + hmac(SECRET, raw);

                var result = service.handle(raw, sig, "pull_request_review", "d4");

                assertEquals("pull_request_review:review_requested", result.handled());
                verify(collectorService, never()).collectFromWebhook(any(), Mockito.anyInt(), Mockito.anyString());
        }

        private static final UUID TEMPLATE_USAGE_PHASE_ID = UUID.fromString("1a2b3c4d-0000-4000-8000-000000000001");
        private static final String TEMPLATE_USAGE_CHANGED_SHA = "sha-changed-content";
        private static final String TEMPLATE_USAGE_TEMPLATE_SHA = "sha-template-content";

        private static final String TEMPLATE_MD = """
                        # Spec Pack

                        ## Overview

                        Template body.

                        ## Scope

                        Template scope body.
                        """;

        private static final String MATCHING_MD = """
                        # Spec Pack

                        ## Overview

                        Actual overview text.

                        ## Scope

                        Actual scope text.
                        """;

        private static final String MISMATCHED_MD = """
                        # Spec Pack

                        ## Overview

                        Actual overview text, missing the Scope section.
                        """;

        private RepositoryScope stubTemplateUsagePrFlow(String changedFilePath) {
                RepositoryScope repository = new RepositoryScope(
                                UUID.fromString("99e85aef-8cf4-4b3c-8863-90e2740e7549"),
                                UUID.fromString("578a0d3b-341c-4b8a-905d-3cb5507ea70c"),
                                "acme/widget",
                                "main");
                Mockito.when(scannerPersistence.findRepositoryByMaskedName("acme/widget"))
                                .thenReturn(Optional.of(repository));
                Mockito.when(scannerPersistence.upsertMinimalTicket(eq(repository.projectId()), eq("TPL-USAGE"),
                                Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                                .thenReturn(new TicketScope(UUID.randomUUID(), "TPL-USAGE"));
                Mockito.when(pullRequestFilesPort.listChangedFilePaths("acme/widget", 42))
                                .thenReturn(java.util.List.of(changedFilePath));
                Mockito.when(pullRequestFilesPort.resolveLastCommitAt("acme/widget", 42))
                                .thenReturn(Optional.of(LAST_COMMIT_AT));
                Mockito.when(scannerService.scan(any())).thenReturn(new ScanRun(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                OffsetDateTime.now(ZoneOffset.UTC),
                                OffsetDateTime.now(ZoneOffset.UTC),
                                "SUCCESS",
                                1,
                                1,
                                null,
                                "d-template-usage"));
                return repository;
        }

        private byte[] handleTemplateUsagePullRequest(String deliveryId) {
                return handleTemplateUsagePullRequest(deliveryId, "closed", true);
        }

        private byte[] handleTemplateUsagePullRequest(String deliveryId, String action, boolean merged) {
                String state = "closed".equals(action) ? "closed" : "open";
                String body = """
                                {
                                  "action": "%s",
                                  "repository": { "full_name": "acme/widget" },
                                  "pull_request": {
                                    "number": 42,
                                    "state": "%s",
                                    "merged": %s,
                                    "head": { "ref": "feature/tpl-usage", "sha": "7777777777777777777777777777777777777777" },
                                    "title": "TPL-USAGE: doc update",
                                    "base": { "ref": "main" }
                                  }
                                }
                                """.formatted(action, state, merged);
                return body.getBytes(StandardCharsets.UTF_8);
        }

        @Test
        void template_usage_records_match_when_header_structure_equals_template() {
                String changedPath = "docs/changes/TPL-USAGE/spec-pack.md";
                RepositoryScope repository = stubTemplateUsagePrFlow(changedPath);
                Mockito.when(scannerPersistence.findArtifactTypes()).thenReturn(java.util.List.of(
                                new ArtifactTypeScope(UUID.randomUUID(), TEMPLATE_USAGE_PHASE_ID, "1", "SPEC_PACK",
                                                "Spec Pack", "spec-pack.md", true)));
                Mockito.when(scannerSourcePort.listTree("acme/widget", "1111111111111111111111111111111111111111"))
                                .thenReturn(Map.of(
                                                changedPath, new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                changedPath, TEMPLATE_USAGE_CHANGED_SHA, "blob", 100L),
                                                "documents/docs/standards/templates/spec-pack.md",
                                                new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                "documents/docs/standards/templates/spec-pack.md",
                                                                TEMPLATE_USAGE_TEMPLATE_SHA, "blob", 100L)));
                Mockito.when(scannerSourcePort.readBlob("acme/widget", TEMPLATE_USAGE_CHANGED_SHA))
                                .thenReturn(MATCHING_MD.getBytes(StandardCharsets.UTF_8));
                Mockito.when(scannerSourcePort.readBlob("acme/widget", TEMPLATE_USAGE_TEMPLATE_SHA))
                                .thenReturn(TEMPLATE_MD.getBytes(StandardCharsets.UTF_8));
                byte[] raw = handleTemplateUsagePullRequest("d-template-usage-match");
                String sig = "sha256=" + hmac(SECRET, raw);

                var result = service.handle(raw, sig, "pull_request", "d-template-usage-match");

                assertEquals("pull_request:closed", result.handled());
                verify(templateUsageStatWriter).recordIndependently(
                                repository.projectId(), repository.repositoryId(), TEMPLATE_USAGE_PHASE_ID, true);
        }

        @Test
        void template_usage_records_no_match_when_header_structure_differs() {
                String changedPath = "docs/changes/TPL-USAGE/spec-pack.md";
                RepositoryScope repository = stubTemplateUsagePrFlow(changedPath);
                Mockito.when(scannerPersistence.findArtifactTypes()).thenReturn(java.util.List.of(
                                new ArtifactTypeScope(UUID.randomUUID(), TEMPLATE_USAGE_PHASE_ID, "1", "SPEC_PACK",
                                                "Spec Pack", "spec-pack.md", true)));
                Mockito.when(scannerSourcePort.listTree("acme/widget", "1111111111111111111111111111111111111111"))
                                .thenReturn(Map.of(
                                                changedPath, new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                changedPath, TEMPLATE_USAGE_CHANGED_SHA, "blob", 100L),
                                                "documents/docs/standards/templates/spec-pack.md",
                                                new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                "documents/docs/standards/templates/spec-pack.md",
                                                                TEMPLATE_USAGE_TEMPLATE_SHA, "blob", 100L)));
                Mockito.when(scannerSourcePort.readBlob("acme/widget", TEMPLATE_USAGE_CHANGED_SHA))
                                .thenReturn(MISMATCHED_MD.getBytes(StandardCharsets.UTF_8));
                Mockito.when(scannerSourcePort.readBlob("acme/widget", TEMPLATE_USAGE_TEMPLATE_SHA))
                                .thenReturn(TEMPLATE_MD.getBytes(StandardCharsets.UTF_8));
                byte[] raw = handleTemplateUsagePullRequest("d-template-usage-mismatch");
                String sig = "sha256=" + hmac(SECRET, raw);

                var result = service.handle(raw, sig, "pull_request", "d-template-usage-mismatch");

                assertEquals("pull_request:closed", result.handled());
                verify(templateUsageStatWriter).recordIndependently(
                                repository.projectId(), repository.repositoryId(), TEMPLATE_USAGE_PHASE_ID, false);
        }

        @Test
        void template_usage_skips_silently_when_no_matching_template_found() {
                String changedPath = "docs/changes/TPL-USAGE/spec-pack.md";
                stubTemplateUsagePrFlow(changedPath);
                Mockito.when(scannerPersistence.findArtifactTypes()).thenReturn(java.util.List.of(
                                new ArtifactTypeScope(UUID.randomUUID(), TEMPLATE_USAGE_PHASE_ID, "1", "SPEC_PACK",
                                                "Spec Pack", "spec-pack.md", true)));
                Mockito.when(scannerSourcePort.listTree("acme/widget", "1111111111111111111111111111111111111111"))
                                .thenReturn(Map.of(
                                                changedPath, new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                changedPath, TEMPLATE_USAGE_CHANGED_SHA, "blob", 100L)));
                byte[] raw = handleTemplateUsagePullRequest("d-template-usage-no-template");
                String sig = "sha256=" + hmac(SECRET, raw);

                var result = service.handle(raw, sig, "pull_request", "d-template-usage-no-template");

                assertEquals("pull_request:closed", result.handled());
                verify(templateUsageStatWriter, never()).recordIndependently(any(), any(), any(), Mockito.anyBoolean());
        }

        @Test
        void template_usage_skips_silently_when_file_not_mapped_to_a_phase() {
                String changedPath = "docs/changes/TPL-USAGE/spec-pack.md";
                stubTemplateUsagePrFlow(changedPath);
                // findArtifactTypes() left unstubbed -> empty list -> no phase mapping for spec-pack.md
                Mockito.when(scannerSourcePort.listTree("acme/widget", "1111111111111111111111111111111111111111"))
                                .thenReturn(Map.of(
                                                changedPath, new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                changedPath, TEMPLATE_USAGE_CHANGED_SHA, "blob", 100L),
                                                "documents/docs/standards/templates/spec-pack.md",
                                                new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                "documents/docs/standards/templates/spec-pack.md",
                                                                TEMPLATE_USAGE_TEMPLATE_SHA, "blob", 100L)));
                byte[] raw = handleTemplateUsagePullRequest("d-template-usage-unmapped");
                String sig = "sha256=" + hmac(SECRET, raw);

                var result = service.handle(raw, sig, "pull_request", "d-template-usage-unmapped");

                assertEquals("pull_request:closed", result.handled());
                verify(templateUsageStatWriter, never()).recordIndependently(any(), any(), any(), Mockito.anyBoolean());
        }

        @Test
        void template_usage_skips_silently_on_401_403_404_blob_fetch_errors() {
                String changedPath = "docs/changes/TPL-USAGE/spec-pack.md";
                stubTemplateUsagePrFlow(changedPath);
                Mockito.when(scannerPersistence.findArtifactTypes()).thenReturn(java.util.List.of(
                                new ArtifactTypeScope(UUID.randomUUID(), TEMPLATE_USAGE_PHASE_ID, "1", "SPEC_PACK",
                                                "Spec Pack", "spec-pack.md", true)));
                Mockito.when(scannerSourcePort.listTree("acme/widget", "1111111111111111111111111111111111111111"))
                                .thenReturn(Map.of(
                                                changedPath, new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                changedPath, TEMPLATE_USAGE_CHANGED_SHA, "blob", 100L),
                                                "documents/docs/standards/templates/spec-pack.md",
                                                new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                "documents/docs/standards/templates/spec-pack.md",
                                                                TEMPLATE_USAGE_TEMPLATE_SHA, "blob", 100L)));

                for (int status : new int[] { 401, 403, 404 }) {
                        Mockito.doThrow(WebClientResponseException.create(
                                        status, "error-" + status, HttpHeaders.EMPTY, new byte[0], null))
                                        .when(scannerSourcePort).readBlob("acme/widget", TEMPLATE_USAGE_CHANGED_SHA);
                        byte[] raw = handleTemplateUsagePullRequest("d-template-usage-" + status);
                        String sig = "sha256=" + hmac(SECRET, raw);

                        var result = service.handle(raw, sig, "pull_request", "d-template-usage-" + status);

                        assertEquals("pull_request:closed", result.handled());
                }
                verify(templateUsageStatWriter, never()).recordIndependently(any(), any(), any(), Mockito.anyBoolean());
        }

        @Test
        void template_usage_propagates_when_blob_fetch_fails_with_non_skippable_error() {
                String changedPath = "docs/changes/TPL-USAGE/spec-pack.md";
                stubTemplateUsagePrFlow(changedPath);
                Mockito.when(scannerPersistence.findArtifactTypes()).thenReturn(java.util.List.of(
                                new ArtifactTypeScope(UUID.randomUUID(), TEMPLATE_USAGE_PHASE_ID, "1", "SPEC_PACK",
                                                "Spec Pack", "spec-pack.md", true)));
                Mockito.when(scannerSourcePort.listTree("acme/widget", "1111111111111111111111111111111111111111"))
                                .thenReturn(Map.of(
                                                changedPath, new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                changedPath, TEMPLATE_USAGE_CHANGED_SHA, "blob", 100L),
                                                "documents/docs/standards/templates/spec-pack.md",
                                                new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                "documents/docs/standards/templates/spec-pack.md",
                                                                TEMPLATE_USAGE_TEMPLATE_SHA, "blob", 100L)));
                Mockito.doThrow(WebClientResponseException.create(
                                500, "server-error", HttpHeaders.EMPTY, new byte[0], null))
                                .when(scannerSourcePort).readBlob("acme/widget", TEMPLATE_USAGE_CHANGED_SHA);
                byte[] raw = handleTemplateUsagePullRequest("d-template-usage-500");
                String sig = "sha256=" + hmac(SECRET, raw);

                assertThrows(WebClientResponseException.class,
                                () -> service.handle(raw, sig, "pull_request", "d-template-usage-500"));
                verify(templateUsageStatWriter, never()).recordIndependently(any(), any(), any(), Mockito.anyBoolean());
        }

        @Test
        void template_usage_skips_silently_on_401_403_404_tree_fetch_errors() {
                String changedPath = "docs/changes/TPL-USAGE/spec-pack.md";
                stubTemplateUsagePrFlow(changedPath);

                for (int status : new int[] { 401, 403, 404 }) {
                        Mockito.doThrow(WebClientResponseException.create(
                                        status, "error-" + status, HttpHeaders.EMPTY, new byte[0], null))
                                        .when(scannerSourcePort).listTree("acme/widget", "1111111111111111111111111111111111111111");
                        byte[] raw = handleTemplateUsagePullRequest("d-template-usage-tree-" + status);
                        String sig = "sha256=" + hmac(SECRET, raw);

                        var result = service.handle(raw, sig, "pull_request", "d-template-usage-tree-" + status);

                        assertEquals("pull_request:closed", result.handled());
                }
                verify(templateUsageStatWriter, never()).recordIndependently(any(), any(), any(), Mockito.anyBoolean());
        }

        @Test
        void template_usage_propagates_when_tree_fetch_fails_with_non_skippable_error() {
                String changedPath = "docs/changes/TPL-USAGE/spec-pack.md";
                stubTemplateUsagePrFlow(changedPath);
                Mockito.doThrow(WebClientResponseException.create(
                                500, "server-error", HttpHeaders.EMPTY, new byte[0], null))
                                .when(scannerSourcePort).listTree("acme/widget", "1111111111111111111111111111111111111111");
                byte[] raw = handleTemplateUsagePullRequest("d-template-usage-tree-500");
                String sig = "sha256=" + hmac(SECRET, raw);

                assertThrows(WebClientResponseException.class,
                                () -> service.handle(raw, sig, "pull_request", "d-template-usage-tree-500"));
                verify(templateUsageStatWriter, never()).recordIndependently(any(), any(), any(), Mockito.anyBoolean());
        }

        @Test
        void template_usage_continues_processing_remaining_files_when_counter_write_fails() {
                String changedPath1 = "docs/changes/TPL-USAGE/spec-pack.md";
                String changedPath2 = "docs/changes/TPL-USAGE/open-issues.md";
                String changedSha2 = "sha-changed-content-2";
                String templatePath2 = "documents/docs/standards/templates/open-issues.md";
                String templateSha2 = "sha-template-content-2";
                RepositoryScope repository = new RepositoryScope(
                                UUID.fromString("99e85aef-8cf4-4b3c-8863-90e2740e7549"),
                                UUID.fromString("578a0d3b-341c-4b8a-905d-3cb5507ea70c"),
                                "acme/widget", "main");
                Mockito.when(scannerPersistence.findRepositoryByMaskedName("acme/widget"))
                                .thenReturn(Optional.of(repository));
                Mockito.when(scannerPersistence.upsertMinimalTicket(eq(repository.projectId()), eq("TPL-USAGE"),
                                Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                                .thenReturn(new TicketScope(UUID.randomUUID(), "TPL-USAGE"));
                Mockito.when(pullRequestFilesPort.listChangedFilePaths("acme/widget", 42))
                                .thenReturn(java.util.List.of(changedPath1, changedPath2));
                Mockito.when(pullRequestFilesPort.resolveLastCommitAt("acme/widget", 42))
                                .thenReturn(Optional.of(LAST_COMMIT_AT));
                Mockito.when(scannerService.scan(any())).thenReturn(new ScanRun(
                                UUID.randomUUID(), UUID.randomUUID(),
                                OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC),
                                "SUCCESS", 1, 1, null, "d-template-usage-counter-fail"));
                Mockito.when(scannerPersistence.findArtifactTypes()).thenReturn(java.util.List.of(
                                new ArtifactTypeScope(UUID.randomUUID(), TEMPLATE_USAGE_PHASE_ID, "1", "SPEC_PACK",
                                                "Spec Pack", "spec-pack.md", true),
                                new ArtifactTypeScope(UUID.randomUUID(), TEMPLATE_USAGE_PHASE_ID, "1", "OPEN_ISSUES",
                                                "Open Issues", "open-issues.md", true)));
                Mockito.when(scannerSourcePort.listTree("acme/widget", "1111111111111111111111111111111111111111"))
                                .thenReturn(Map.of(
                                                changedPath1, new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                changedPath1, TEMPLATE_USAGE_CHANGED_SHA, "blob", 100L),
                                                changedPath2, new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                changedPath2, changedSha2, "blob", 100L),
                                                "documents/docs/standards/templates/spec-pack.md",
                                                new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                "documents/docs/standards/templates/spec-pack.md",
                                                                TEMPLATE_USAGE_TEMPLATE_SHA, "blob", 100L),
                                                templatePath2, new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                templatePath2, templateSha2, "blob", 100L)));
                Mockito.when(scannerSourcePort.readBlob("acme/widget", TEMPLATE_USAGE_CHANGED_SHA))
                                .thenReturn(MATCHING_MD.getBytes(StandardCharsets.UTF_8));
                Mockito.when(scannerSourcePort.readBlob("acme/widget", TEMPLATE_USAGE_TEMPLATE_SHA))
                                .thenReturn(TEMPLATE_MD.getBytes(StandardCharsets.UTF_8));
                Mockito.when(scannerSourcePort.readBlob("acme/widget", changedSha2))
                                .thenReturn(MATCHING_MD.getBytes(StandardCharsets.UTF_8));
                Mockito.when(scannerSourcePort.readBlob("acme/widget", templateSha2))
                                .thenReturn(TEMPLATE_MD.getBytes(StandardCharsets.UTF_8));
                Mockito.doThrow(new org.springframework.dao.QueryTimeoutException("counter write timed out"))
                                .doNothing()
                                .when(templateUsageStatWriter).recordIndependently(any(), any(), any(), Mockito.anyBoolean());

                byte[] raw = handleTemplateUsagePullRequest("d-template-usage-counter-fail");
                String sig = "sha256=" + hmac(SECRET, raw);

                var result = service.handle(raw, sig, "pull_request", "d-template-usage-counter-fail");

                assertEquals("pull_request:closed", result.handled());
                verify(templateUsageStatWriter, times(2)).recordIndependently(
                                eq(repository.projectId()), eq(repository.repositoryId()), eq(TEMPLATE_USAGE_PHASE_ID),
                                Mockito.anyBoolean());
        }

        @Test
        void template_usage_skips_when_action_is_opened_synchronize_or_reopened_even_if_merged_true() {
                String changedPath = "docs/changes/TPL-USAGE/spec-pack.md";
                stubTemplateUsagePrFlow(changedPath);
                Mockito.when(scannerPersistence.findArtifactTypes()).thenReturn(java.util.List.of(
                                new ArtifactTypeScope(UUID.randomUUID(), TEMPLATE_USAGE_PHASE_ID, "1", "SPEC_PACK",
                                                "Spec Pack", "spec-pack.md", true)));
                Mockito.when(scannerSourcePort.listTree("acme/widget", "1111111111111111111111111111111111111111"))
                                .thenReturn(Map.of(
                                                changedPath, new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                changedPath, TEMPLATE_USAGE_CHANGED_SHA, "blob", 100L),
                                                "documents/docs/standards/templates/spec-pack.md",
                                                new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                "documents/docs/standards/templates/spec-pack.md",
                                                                TEMPLATE_USAGE_TEMPLATE_SHA, "blob", 100L)));
                Mockito.when(scannerSourcePort.readBlob("acme/widget", TEMPLATE_USAGE_CHANGED_SHA))
                                .thenReturn(MATCHING_MD.getBytes(StandardCharsets.UTF_8));
                Mockito.when(scannerSourcePort.readBlob("acme/widget", TEMPLATE_USAGE_TEMPLATE_SHA))
                                .thenReturn(TEMPLATE_MD.getBytes(StandardCharsets.UTF_8));

                // Duplicate-counting regression guard: a PR's diff is re-scanned on every
                // webhook delivery, so recording must only happen once, on "closed", not on
                // every lifecycle event -- even when "merged" happens to already be true.
                for (String action : new String[] { "opened", "synchronize", "reopened" }) {
                        String deliveryId = "d-template-usage-gate-" + action;
                        byte[] raw = handleTemplateUsagePullRequest(deliveryId, action, true);
                        String sig = "sha256=" + hmac(SECRET, raw);

                        service.handle(raw, sig, "pull_request", deliveryId);
                }

                verify(templateUsageStatWriter, never()).recordIndependently(any(), any(), any(), Mockito.anyBoolean());
        }

        @Test
        void template_usage_skips_file_when_tree_entry_has_blank_sha() {
                String changedPath = "docs/changes/TPL-USAGE/spec-pack.md";
                String templatePath = "documents/docs/standards/templates/spec-pack.md";
                stubTemplateUsagePrFlow(changedPath);
                Mockito.when(scannerPersistence.findArtifactTypes()).thenReturn(java.util.List.of(
                                new ArtifactTypeScope(UUID.randomUUID(), TEMPLATE_USAGE_PHASE_ID, "1", "SPEC_PACK",
                                                "Spec Pack", "spec-pack.md", true)));
                // Distinct code path from "no template found in tree" (already covered above):
                // here the template path IS a key in the tree (resolveTemplatePath finds it),
                // but its GitHubTreeEntry carries a blank sha -- fetchTemplateUsageBlob must
                // treat that as "content unavailable" via its null/blank sha guard, not by
                // attempting to read a blob with an empty sha.
                Mockito.when(scannerSourcePort.listTree("acme/widget", "1111111111111111111111111111111111111111"))
                                .thenReturn(Map.of(
                                                changedPath, new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                changedPath, TEMPLATE_USAGE_CHANGED_SHA, "blob", 100L),
                                                templatePath, new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                templatePath, "", "blob", 100L)));
                Mockito.when(scannerSourcePort.readBlob("acme/widget", TEMPLATE_USAGE_CHANGED_SHA))
                                .thenReturn(MATCHING_MD.getBytes(StandardCharsets.UTF_8));
                byte[] raw = handleTemplateUsagePullRequest("d-template-usage-blank-sha");
                String sig = "sha256=" + hmac(SECRET, raw);

                var result = service.handle(raw, sig, "pull_request", "d-template-usage-blank-sha");

                assertEquals("pull_request:closed", result.handled());
                verify(templateUsageStatWriter, never()).recordIndependently(any(), any(), any(), Mockito.anyBoolean());
        }

        @Test
        void template_usage_skips_when_pull_request_is_closed_but_not_merged() {
                String changedPath = "docs/changes/TPL-USAGE/spec-pack.md";
                stubTemplateUsagePrFlow(changedPath);
                Mockito.when(scannerPersistence.findArtifactTypes()).thenReturn(java.util.List.of(
                                new ArtifactTypeScope(UUID.randomUUID(), TEMPLATE_USAGE_PHASE_ID, "1", "SPEC_PACK",
                                                "Spec Pack", "spec-pack.md", true)));
                Mockito.when(scannerSourcePort.listTree("acme/widget", "1111111111111111111111111111111111111111"))
                                .thenReturn(Map.of(
                                                changedPath, new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                changedPath, TEMPLATE_USAGE_CHANGED_SHA, "blob", 100L),
                                                "documents/docs/standards/templates/spec-pack.md",
                                                new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                "documents/docs/standards/templates/spec-pack.md",
                                                                TEMPLATE_USAGE_TEMPLATE_SHA, "blob", 100L)));
                Mockito.when(scannerSourcePort.readBlob("acme/widget", TEMPLATE_USAGE_CHANGED_SHA))
                                .thenReturn(MATCHING_MD.getBytes(StandardCharsets.UTF_8));
                Mockito.when(scannerSourcePort.readBlob("acme/widget", TEMPLATE_USAGE_TEMPLATE_SHA))
                                .thenReturn(TEMPLATE_MD.getBytes(StandardCharsets.UTF_8));
                byte[] raw = handleTemplateUsagePullRequest("d-template-usage-closed-not-merged", "closed", false);
                String sig = "sha256=" + hmac(SECRET, raw);

                var result = service.handle(raw, sig, "pull_request", "d-template-usage-closed-not-merged");

                assertEquals("pull_request:closed", result.handled());
                verify(templateUsageStatWriter, never()).recordIndependently(any(), any(), any(), Mockito.anyBoolean());
        }

        // ── AI-REVIEW-KPI-IMPROVEMENT §8 stats hook ───────────────────────────────

        private static final String AI_REVIEW_SHA = "sha-ai-review-content";

        private static final String AI_REVIEW_MD_WITH_STATS = """
                        ## 8. Số liệu thống kê

                        | Chỉ số | Giá trị | Ghi chú |
                        | --- | --- | --- |
                        | Tổng số finding | 2 | |
                        | Tổng số finding đã fix | 2 / 2 | |
                        | Tỷ lệ xử lý finding nghiêm trọng (Blocker) | 1 / 1 (100%) | |
                        | Tỷ lệ AI finding được con người chấp nhận | 2 / 2 (100%) | |
                        | Tỷ lệ AI review finding hữu ích | 2 / 2 (100%) | |
                        | Tỷ lệ AI finding bị đánh giá false positive | 0 / 2 (0%) | |
                        | Tỷ lệ AI finding đã được xử lý | 2 / 2 (100%) | |
                        """;

        private static final String AI_REVIEW_MD_WITHOUT_SECTION_8 = """
                        ## 1. Tổng quan
                        Nothing else here.
                        """;

        private static final String AI_REVIEW_SHA_V2 = "sha-ai-review-content-v2";

        private static final String AI_REVIEW_MD_WITH_STATS_V2 = """
                        ## 8. Số liệu thống kê

                        | Chỉ số | Giá trị | Ghi chú |
                        | --- | --- | --- |
                        | Tổng số finding | 4 | |
                        | Tổng số finding đã fix | 3 / 4 | |
                        | Tỷ lệ xử lý finding nghiêm trọng (Blocker) | 1 / 2 (50%) | |
                        | Tỷ lệ AI finding được con người chấp nhận | 3 / 4 (75%) | |
                        | Tỷ lệ AI review finding hữu ích | 3 / 4 (75%) | |
                        | Tỷ lệ AI finding bị đánh giá false positive | 1 / 4 (25%) | |
                        | Tỷ lệ AI finding đã được xử lý | 3 / 4 (75%) | |
                        """;

        @Test
        void ai_finding_stats_recorded_when_ai_review_md_changed_and_pr_merged() {
                String changedPath = "docs/changes/TPL-USAGE/ai-review.md";
                RepositoryScope repository = stubTemplateUsagePrFlow(changedPath);
                Mockito.when(scannerSourcePort.listTree("acme/widget", "1111111111111111111111111111111111111111"))
                                .thenReturn(Map.of(
                                                changedPath, new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                changedPath, AI_REVIEW_SHA, "blob", 100L)));
                Mockito.when(scannerSourcePort.readBlob("acme/widget", AI_REVIEW_SHA))
                                .thenReturn(AI_REVIEW_MD_WITH_STATS.getBytes(StandardCharsets.UTF_8));
                byte[] raw = handleTemplateUsagePullRequest("d-ai-review-stats-match");
                String sig = "sha256=" + hmac(SECRET, raw);

                var result = service.handle(raw, sig, "pull_request", "d-ai-review-stats-match");

                assertEquals("pull_request:closed", result.handled());
                ArgumentCaptor<AiFindingStatRecord> captor = ArgumentCaptor.forClass(AiFindingStatRecord.class);
                verify(aiFindingStatWriter).recordStat(captor.capture());
                AiFindingStatRecord recorded = captor.getValue();
                assertEquals(repository.projectId(), recorded.projectId());
                assertEquals(repository.repositoryId(), recorded.repositoryId());
                assertEquals(1, recorded.blockerMajorResolvedCount());
                assertEquals(1, recorded.blockerMajorTotalCount());
                assertEquals(2, recorded.aiReviewAdoptedCount());
                assertEquals(2, recorded.aiReviewFindingTotalCount());
                assertEquals(2, recorded.aiReviewValidCount());
                assertEquals(0, recorded.aiReviewFalsePositiveCount());
                assertEquals(2, recorded.aiReviewResolvedCount());
        }

        @Test
        void ai_finding_stats_skipped_when_no_ai_review_md_in_diff() {
                String changedPath = "docs/changes/TPL-USAGE/spec-pack.md";
                stubTemplateUsagePrFlow(changedPath);
                Mockito.when(scannerSourcePort.listTree("acme/widget", "1111111111111111111111111111111111111111"))
                                .thenReturn(Map.of(
                                                changedPath, new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                changedPath, TEMPLATE_USAGE_CHANGED_SHA, "blob", 100L)));
                byte[] raw = handleTemplateUsagePullRequest("d-ai-review-stats-no-file");
                String sig = "sha256=" + hmac(SECRET, raw);

                var result = service.handle(raw, sig, "pull_request", "d-ai-review-stats-no-file");

                assertEquals("pull_request:closed", result.handled());
                verify(aiFindingStatWriter, never()).recordStat(any());
        }

        @Test
        void ai_finding_stats_skipped_silently_when_section_8_missing() {
                String changedPath = "docs/changes/TPL-USAGE/ai-review.md";
                stubTemplateUsagePrFlow(changedPath);
                Mockito.when(scannerSourcePort.listTree("acme/widget", "1111111111111111111111111111111111111111"))
                                .thenReturn(Map.of(
                                                changedPath, new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                changedPath, AI_REVIEW_SHA, "blob", 100L)));
                Mockito.when(scannerSourcePort.readBlob("acme/widget", AI_REVIEW_SHA))
                                .thenReturn(AI_REVIEW_MD_WITHOUT_SECTION_8.getBytes(StandardCharsets.UTF_8));
                byte[] raw = handleTemplateUsagePullRequest("d-ai-review-stats-no-section-8");
                String sig = "sha256=" + hmac(SECRET, raw);

                var result = service.handle(raw, sig, "pull_request", "d-ai-review-stats-no-section-8");

                assertEquals("pull_request:closed", result.handled());
                verify(aiFindingStatWriter, never()).recordStat(any());
        }

        @Test
        void ai_finding_stats_skipped_silently_on_401_403_404_blob_fetch_errors() {
                String changedPath = "docs/changes/TPL-USAGE/ai-review.md";
                stubTemplateUsagePrFlow(changedPath);
                Mockito.when(scannerSourcePort.listTree("acme/widget", "1111111111111111111111111111111111111111"))
                                .thenReturn(Map.of(
                                                changedPath, new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                changedPath, AI_REVIEW_SHA, "blob", 100L)));

                for (int status : new int[] { 401, 403, 404 }) {
                        Mockito.doThrow(WebClientResponseException.create(
                                        status, "error-" + status, HttpHeaders.EMPTY, new byte[0], null))
                                        .when(scannerSourcePort).readBlob("acme/widget", AI_REVIEW_SHA);
                        byte[] raw = handleTemplateUsagePullRequest("d-ai-review-stats-" + status);
                        String sig = "sha256=" + hmac(SECRET, raw);

                        var result = service.handle(raw, sig, "pull_request", "d-ai-review-stats-" + status);

                        assertEquals("pull_request:closed", result.handled());
                }
                verify(aiFindingStatWriter, never()).recordStat(any());
        }

        @Test
        void ai_finding_stats_write_failure_does_not_fail_pr_merge_flow() {
                String changedPath = "docs/changes/TPL-USAGE/ai-review.md";
                stubTemplateUsagePrFlow(changedPath);
                Mockito.when(scannerSourcePort.listTree("acme/widget", "1111111111111111111111111111111111111111"))
                                .thenReturn(Map.of(
                                                changedPath, new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                changedPath, AI_REVIEW_SHA, "blob", 100L)));
                Mockito.when(scannerSourcePort.readBlob("acme/widget", AI_REVIEW_SHA))
                                .thenReturn(AI_REVIEW_MD_WITH_STATS.getBytes(StandardCharsets.UTF_8));
                Mockito.doThrow(new org.springframework.dao.QueryTimeoutException("write timed out"))
                                .when(aiFindingStatWriter).recordStat(any());
                byte[] raw = handleTemplateUsagePullRequest("d-ai-review-stats-write-fail");
                String sig = "sha256=" + hmac(SECRET, raw);

                var result = service.handle(raw, sig, "pull_request", "d-ai-review-stats-write-fail");

                assertEquals("pull_request:closed", result.handled());
                verify(aiFindingStatWriter).recordStat(any());
        }

        @Test
        void ai_finding_stats_secondDeliveryForSameMergedPr_recordsFreshCountsNotSkippedOrStale() {
                // AC-AIRKI-5: a ticket can be re-merged (e.g. a follow-up PR) with an updated
                // ai-review.md. The service has no "already processed this PR" dedup, so it
                // must record the NEW counts on the second delivery, not silently skip (treating
                // the ticket as already-done) and not keep serving the first delivery's stale
                // captured values (e.g. from an accidentally cached/shared field).
                String changedPath = "docs/changes/TPL-USAGE/ai-review.md";
                RepositoryScope repository = stubTemplateUsagePrFlow(changedPath);
                Mockito.when(scannerSourcePort.listTree("acme/widget", "1111111111111111111111111111111111111111"))
                                .thenReturn(Map.of(
                                                changedPath, new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                changedPath, AI_REVIEW_SHA, "blob", 100L)));
                Mockito.when(scannerSourcePort.readBlob("acme/widget", AI_REVIEW_SHA))
                                .thenReturn(AI_REVIEW_MD_WITH_STATS.getBytes(StandardCharsets.UTF_8));
                byte[] firstRaw = handleTemplateUsagePullRequest("d-ai-review-stats-remerge-1");
                String firstSig = "sha256=" + hmac(SECRET, firstRaw);
                var firstResult = service.handle(firstRaw, firstSig, "pull_request", "d-ai-review-stats-remerge-1");
                assertEquals("pull_request:closed", firstResult.handled());

                Mockito.when(scannerSourcePort.listTree("acme/widget", "1111111111111111111111111111111111111111"))
                                .thenReturn(Map.of(
                                                changedPath, new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                changedPath, AI_REVIEW_SHA_V2, "blob", 100L)));
                Mockito.when(scannerSourcePort.readBlob("acme/widget", AI_REVIEW_SHA_V2))
                                .thenReturn(AI_REVIEW_MD_WITH_STATS_V2.getBytes(StandardCharsets.UTF_8));
                byte[] secondRaw = handleTemplateUsagePullRequest("d-ai-review-stats-remerge-2");
                String secondSig = "sha256=" + hmac(SECRET, secondRaw);
                var secondResult = service.handle(secondRaw, secondSig, "pull_request", "d-ai-review-stats-remerge-2");
                assertEquals("pull_request:closed", secondResult.handled());

                ArgumentCaptor<AiFindingStatRecord> captor = ArgumentCaptor.forClass(AiFindingStatRecord.class);
                verify(aiFindingStatWriter, times(2)).recordStat(captor.capture());
                AiFindingStatRecord first = captor.getAllValues().get(0);
                AiFindingStatRecord second = captor.getAllValues().get(1);
                assertEquals(repository.repositoryId(), first.repositoryId());
                assertEquals(1, first.blockerMajorResolvedCount());
                assertEquals(1, first.blockerMajorTotalCount());
                assertEquals(2, first.aiReviewAdoptedCount());
                assertEquals(2, first.aiReviewFindingTotalCount());
                assertEquals(repository.repositoryId(), second.repositoryId());
                assertEquals(1, second.blockerMajorResolvedCount());
                assertEquals(2, second.blockerMajorTotalCount());
                assertEquals(3, second.aiReviewAdoptedCount());
                assertEquals(4, second.aiReviewFindingTotalCount());
                assertEquals(3, second.aiReviewValidCount());
                assertEquals(1, second.aiReviewFalsePositiveCount());
                assertEquals(3, second.aiReviewResolvedCount());
        }

        @Test
        void ai_finding_stats_exactRedeliveryOfSamePayload_recordsIdenticalCountsBothTimesNotAccumulated() {
                // AC-AIRKI-6: GitHub's "Redeliver" resends the exact same webhook payload
                // (same delivery ID, same content). Since the service performs no delivery-ID
                // dedup (deliveryId is used only for logging) and true idempotency lives in the
                // DB-layer ON CONFLICT upsert, the *service* must still hand the writer the same
                // fresh counts both times — not double/accumulate them and not throw on a
                // repeated delivery ID.
                String changedPath = "docs/changes/TPL-USAGE/ai-review.md";
                stubTemplateUsagePrFlow(changedPath);
                Mockito.when(scannerSourcePort.listTree("acme/widget", "1111111111111111111111111111111111111111"))
                                .thenReturn(Map.of(
                                                changedPath, new ArtifactScannerSourcePort.GitHubTreeEntry(
                                                                changedPath, AI_REVIEW_SHA, "blob", 100L)));
                Mockito.when(scannerSourcePort.readBlob("acme/widget", AI_REVIEW_SHA))
                                .thenReturn(AI_REVIEW_MD_WITH_STATS.getBytes(StandardCharsets.UTF_8));
                byte[] raw = handleTemplateUsagePullRequest("d-ai-review-stats-redelivered");
                String sig = "sha256=" + hmac(SECRET, raw);

                var firstResult = service.handle(raw, sig, "pull_request", "d-ai-review-stats-redelivered");
                var secondResult = service.handle(raw, sig, "pull_request", "d-ai-review-stats-redelivered");

                assertEquals("pull_request:closed", firstResult.handled());
                assertEquals("pull_request:closed", secondResult.handled());
                ArgumentCaptor<AiFindingStatRecord> captor = ArgumentCaptor.forClass(AiFindingStatRecord.class);
                verify(aiFindingStatWriter, times(2)).recordStat(captor.capture());
                AiFindingStatRecord first = captor.getAllValues().get(0);
                AiFindingStatRecord second = captor.getAllValues().get(1);
                assertEquals(first.blockerMajorResolvedCount(), second.blockerMajorResolvedCount());
                assertEquals(first.blockerMajorTotalCount(), second.blockerMajorTotalCount());
                assertEquals(first.aiReviewAdoptedCount(), second.aiReviewAdoptedCount());
                assertEquals(first.aiReviewFindingTotalCount(), second.aiReviewFindingTotalCount());
                assertEquals(first.aiReviewValidCount(), second.aiReviewValidCount());
                assertEquals(first.aiReviewFalsePositiveCount(), second.aiReviewFalsePositiveCount());
                assertEquals(first.aiReviewResolvedCount(), second.aiReviewResolvedCount());
                assertEquals(2, second.aiReviewFindingTotalCount());
        }
}
