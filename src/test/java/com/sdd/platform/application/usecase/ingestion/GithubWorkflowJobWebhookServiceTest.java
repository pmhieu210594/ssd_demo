package com.sdd.platform.application.usecase.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.port.out.persistence.CiRunRepositoryPort;
import com.sdd.platform.application.port.out.persistence.EvidenceRepositoryPort;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreService;
import com.sdd.platform.config.AppProperties;
import com.sdd.platform.domain.model.CiRun;
import com.sdd.platform.domain.model.ConnectorRun;
import com.sdd.platform.domain.model.EvidenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class GithubWorkflowJobWebhookServiceTest {

    private static final String SECRET = "test-webhook-secret";
    private static final UUID REPOSITORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID CONNECTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID CONNECTOR_RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID PULL_REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000401");
    private static final UUID TICKET_ID = UUID.fromString("00000000-0000-0000-0000-000000000501");
    private static final UUID EXISTING_CI_RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000000601");

    private EvidenceRepositoryPort repositoryPort;
    private CiRunRepositoryPort ciRunRepositoryPort;
    private EvidenceQualityScoreService evidenceQualityScoreService;
    private GithubSecurityEvidenceSnapshotService githubSecurityEvidenceSnapshotService;
    private GithubWorkflowJobWebhookService service;

    @BeforeEach
    void setUp() {
        repositoryPort = mock(EvidenceRepositoryPort.class);
        ciRunRepositoryPort = mock(CiRunRepositoryPort.class);
        evidenceQualityScoreService = mock(EvidenceQualityScoreService.class);
        githubSecurityEvidenceSnapshotService = mock(GithubSecurityEvidenceSnapshotService.class);
        service = new GithubWorkflowJobWebhookService(
                propsWithSecret(),
                new ObjectMapper(),
                repositoryPort,
                ciRunRepositoryPort,
                evidenceQualityScoreService,
                githubSecurityEvidenceSnapshotService
        );

        when(ciRunRepositoryPort.findConnectorByType("CI_RUN_METADATA"))
                .thenReturn(Optional.of(new CiRunModels.ConnectorScope(CONNECTOR_ID, "CI_RUN_METADATA", "CI Run Metadata")));
        when(ciRunRepositoryPort.insertConnectorRun(any())).thenAnswer(invocation -> {
            ConnectorRun run = invocation.getArgument(0);
            run.setId(CONNECTOR_RUN_ID);
            return run;
        });
    }

    @Test
    void handle_inserts_new_ci_run_and_records_optional_linkages() {
        EvidenceRepository repository = repository();
        when(repositoryPort.findByRepositoryNameMaskedAndHostType("acme/widget", "GITHUB"))
                .thenReturn(Optional.of(repository));
        when(ciRunRepositoryPort.findPullRequestByRepositoryAndExternalNumber(REPOSITORY_ID, 42))
                .thenReturn(Optional.of(new CiRunModels.PullRequestScope(PULL_REQUEST_ID, TICKET_ID, 42, "feature/PROJ-123-add-ci")));
        when(ciRunRepositoryPort.findCiRunIdByIdentity("GITHUB_ACTIONS", REPOSITORY_ID, "27660577827", "77123456789"))
                .thenReturn(Optional.empty());
        when(ciRunRepositoryPort.insertCiRun(any())).thenAnswer(invocation -> {
            CiRun row = invocation.getArgument(0);
            row.setId(EXISTING_CI_RUN_ID);
            return row;
        });
        when(githubSecurityEvidenceSnapshotService.collectFromWorkflowJob(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new GithubSecurityEvidenceSnapshotService.Result("security_evidence", 4));

        var result = service.handle(body().getBytes(StandardCharsets.UTF_8), signature(body()), "workflow_job", "delivery-1");

        assertEquals("workflow_job", result.handled());
        assertEquals(1, result.recordsAffected());

        ArgumentCaptor<CiRun> rowCaptor = ArgumentCaptor.forClass(CiRun.class);
        verify(ciRunRepositoryPort).insertCiRun(rowCaptor.capture());
        CiRun row = rowCaptor.getValue();
        assertEquals(PROJECT_ID, row.getProjectId());
        assertEquals(REPOSITORY_ID, row.getRepositoryId());
        assertEquals(PULL_REQUEST_ID, row.getPullRequestId());
        assertEquals(TICKET_ID, row.getTicketId());
        assertEquals(CONNECTOR_RUN_ID, row.getConnectorRunId());
        assertEquals("GITHUB_ACTIONS", row.getCiProvider());
        assertEquals("27660577827", row.getExternalRunId());
        assertEquals("77123456789", row.getExternalJobId());
        assertEquals("CI", row.getWorkflowName());
        assertEquals("build", row.getJobName());
        assertEquals("SUCCESS", row.getStatus());
        assertEquals("https://github.com/acme/widget/actions/runs/27660577827/job/77123456789", row.getCiUrl());
        assertEquals(OffsetDateTime.parse("2026-06-17T02:00:00Z"), row.getStartedAt());
        assertEquals(OffsetDateTime.parse("2026-06-17T02:05:00Z"), row.getCompletedAt());
        verify(githubSecurityEvidenceSnapshotService).collectFromWorkflowJob(
                eq("acme/widget"),
                eq("feature/PROJ-123-add-ci"),
                eq("b2c2ca2306a749aecfa44aa68c30ebd34d294d57"),
                eq(42),
                eq("27660577827"),
                eq("77123456789"),
                eq("build"),
                eq("delivery-1"));

        ArgumentCaptor<ConnectorRun> connectorCaptor = ArgumentCaptor.forClass(ConnectorRun.class);
        verify(ciRunRepositoryPort).updateConnectorRun(connectorCaptor.capture());
        ConnectorRun connectorRun = connectorCaptor.getValue();
        assertEquals(CONNECTOR_RUN_ID, connectorRun.getId());
        assertEquals(ConnectorRun.Status.SUCCESS, connectorRun.getStatus());
        assertEquals(1, connectorRun.getRecordsReceived());
        assertEquals(1, connectorRun.getRecordsInserted());
        assertEquals(0, connectorRun.getRecordsUpdated());
        assertEquals(0, connectorRun.getRecordsSkipped());
        assertEquals(0, connectorRun.getRecordsError());
        verify(evidenceQualityScoreService).recalculateFromCi(TICKET_ID, null, "delivery-1");
    }

    @Test
    void handle_backfills_ticket_from_pr_changed_files_when_pr_scope_ticket_is_missing() {
        EvidenceRepository repository = repository();
        when(repositoryPort.findByRepositoryNameMaskedAndHostType("acme/widget", "GITHUB"))
                .thenReturn(Optional.of(repository));
        when(ciRunRepositoryPort.findPullRequestByRepositoryAndExternalNumber(REPOSITORY_ID, 42))
                .thenReturn(Optional.of(new CiRunModels.PullRequestScope(PULL_REQUEST_ID, null, 42, null)));
        when(ciRunRepositoryPort.findTicketKeyByPullRequestId(PULL_REQUEST_ID))
                .thenReturn(Optional.of("PARSER-SPEC-PACK"));
        when(ciRunRepositoryPort.findTicketIdByProjectIdAndExternalKey(PROJECT_ID, "PARSER-SPEC-PACK"))
                .thenReturn(Optional.of(TICKET_ID));
        when(ciRunRepositoryPort.findCiRunIdByIdentity("GITHUB_ACTIONS", REPOSITORY_ID, "27660577827", "77123456789"))
                .thenReturn(Optional.empty());
        when(ciRunRepositoryPort.insertCiRun(any())).thenAnswer(invocation -> {
            CiRun row = invocation.getArgument(0);
            row.setId(EXISTING_CI_RUN_ID);
            return row;
        });
        when(githubSecurityEvidenceSnapshotService.collectFromWorkflowJob(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new GithubSecurityEvidenceSnapshotService.Result("security_evidence", 0));

        var result = service.handle(body().getBytes(StandardCharsets.UTF_8), signature(body()), "workflow_job", "delivery-1");

        assertEquals("workflow_job", result.handled());
        assertEquals(1, result.recordsAffected());

        ArgumentCaptor<CiRun> rowCaptor = ArgumentCaptor.forClass(CiRun.class);
        verify(ciRunRepositoryPort).insertCiRun(rowCaptor.capture());
        CiRun row = rowCaptor.getValue();
        assertEquals(PULL_REQUEST_ID, row.getPullRequestId());
        assertEquals(TICKET_ID, row.getTicketId());
        verify(evidenceQualityScoreService).recalculateFromCi(TICKET_ID, null, "delivery-1");
    }

    @Test
    void handle_updates_existing_ci_run_and_normalizes_time_order() {
        EvidenceRepository repository = repository();
        when(repositoryPort.findByRepositoryNameMaskedAndHostType("acme/widget", "GITHUB"))
                .thenReturn(Optional.of(repository));
        when(ciRunRepositoryPort.findPullRequestByRepositoryAndExternalNumber(REPOSITORY_ID, 42))
                .thenReturn(Optional.of(new CiRunModels.PullRequestScope(PULL_REQUEST_ID, TICKET_ID, 42, "feature/PROJ-123-add-ci")));
        when(ciRunRepositoryPort.findCiRunIdByIdentity("GITHUB_ACTIONS", REPOSITORY_ID, "27660577827", "77123456789"))
                .thenReturn(Optional.of(EXISTING_CI_RUN_ID));

        var result = service.handle(bodyWithReversedTime().getBytes(StandardCharsets.UTF_8), signature(bodyWithReversedTime()), "workflow_job", "delivery-2");

        assertEquals("workflow_job", result.handled());
        assertEquals(1, result.recordsAffected());

        ArgumentCaptor<CiRun> rowCaptor = ArgumentCaptor.forClass(CiRun.class);
        verify(ciRunRepositoryPort).updateCiRun(rowCaptor.capture());
        CiRun row = rowCaptor.getValue();
        assertEquals(EXISTING_CI_RUN_ID, row.getId());
        assertEquals(OffsetDateTime.parse("2026-06-17T03:00:00Z"), row.getStartedAt());
        assertEquals(OffsetDateTime.parse("2026-06-17T03:00:00Z"), row.getCompletedAt());
        assertEquals("https://github.com/acme/widget/actions/runs/27660577827", row.getCiUrl());

        ArgumentCaptor<ConnectorRun> connectorCaptor = ArgumentCaptor.forClass(ConnectorRun.class);
        verify(ciRunRepositoryPort).updateConnectorRun(connectorCaptor.capture());
        ConnectorRun connectorRun = connectorCaptor.getValue();
        assertEquals(ConnectorRun.Status.SUCCESS, connectorRun.getStatus());
        assertEquals(0, connectorRun.getRecordsInserted());
        assertEquals(1, connectorRun.getRecordsUpdated());
        assertEquals(0, connectorRun.getRecordsError());
    }

    @Test
    void handle_records_connector_failure_when_repository_is_missing() {
        when(repositoryPort.findByRepositoryNameMaskedAndHostType("acme/widget", "GITHUB"))
                .thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.handle(body().getBytes(StandardCharsets.UTF_8), signature(body()), "workflow_job", "delivery-3"));

        assertEquals("Repository not found", ex.getMessage());

        verify(ciRunRepositoryPort).findConnectorByType("CI_RUN_METADATA");
        verify(ciRunRepositoryPort).insertConnectorRun(any());
        verify(repositoryPort).findByRepositoryNameMaskedAndHostType("acme/widget", "GITHUB");
        ArgumentCaptor<ConnectorRun> connectorCaptor = ArgumentCaptor.forClass(ConnectorRun.class);
        verify(ciRunRepositoryPort).updateConnectorRun(connectorCaptor.capture());
        ConnectorRun connectorRun = connectorCaptor.getValue();
        assertEquals(ConnectorRun.Status.FAILED, connectorRun.getStatus());
        assertEquals(1, connectorRun.getRecordsReceived());
        assertEquals(0, connectorRun.getRecordsInserted());
        assertEquals(0, connectorRun.getRecordsUpdated());
        assertEquals(1, connectorRun.getRecordsError());
        assertTrue(connectorRun.getErrorMessage().contains("Repository not found"));
        verifyNoMoreInteractions(repositoryPort, ciRunRepositoryPort);
    }

    private static AppProperties propsWithSecret() {
        return new AppProperties(
                null,
                null,
                null,
                null,
                new AppProperties.Connectors(
                        null,
                        new AppProperties.Connectors.GitHub("https://api.github.com", "", SECRET),
                        null,
                        null
                )
        );
    }

    private static EvidenceRepository repository() {
        return EvidenceRepository.builder()
                .repositoryId(REPOSITORY_ID)
                .projectId(PROJECT_ID)
                .repositoryNameMasked("acme/widget")
                .hostType("GITHUB")
                .build();
    }

    private static String body() {
        return """
                {
                  "repository": { "full_name": "acme/widget" },
                  "workflow_run": {
                    "id": "27660577827",
                    "name": "CI",
                    "html_url": "https://github.com/acme/widget/actions/runs/27660577827",
                    "head_branch": "feature/PROJ-123-add-ci",
                    "head_sha": "b2c2ca2306a749aecfa44aa68c30ebd34d294d57"
                  },
                  "workflow_job": {
                    "id": "77123456789",
                    "name": "build",
                    "status": "completed",
                    "conclusion": "success",
                    "html_url": "https://github.com/acme/widget/actions/runs/27660577827/job/77123456789",
                    "started_at": "2026-06-17T02:00:00Z",
                    "completed_at": "2026-06-17T02:05:00Z"
                  },
                  "pull_request": {
                    "number": 42
                  }
                }
                """;
    }

    private static String bodyWithReversedTime() {
        return """
                {
                  "repository": { "full_name": "acme/widget" },
                  "workflow_run": {
                    "id": "27660577827",
                    "name": "CI",
                    "html_url": "https://github.com/acme/widget/actions/runs/27660577827"
                  },
                  "workflow_job": {
                    "id": "77123456789",
                    "name": "build",
                    "status": "in_progress",
                    "started_at": "2026-06-17T03:00:00Z",
                    "completed_at": "2026-06-17T02:59:59Z"
                  }
                }
                """;
    }

    private static String signature(String body) {
        return "sha256=" + hmacSha256Hex(SECRET, body.getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacSha256Hex(String secret, byte[] body) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
