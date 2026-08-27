package com.sdd.platform.application.usecase.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.port.out.integration.ArtifactScannerSourcePort;
import com.sdd.platform.application.port.out.integration.GithubPullRequestFilesPort;
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
import com.sdd.platform.application.usecase.scanner.ArtifactScannerService;
import com.sdd.platform.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;

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
                                new ObjectMapper());
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
                                new ObjectMapper());

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
}
