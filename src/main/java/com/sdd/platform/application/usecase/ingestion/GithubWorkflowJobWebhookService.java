package com.sdd.platform.application.usecase.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.port.out.persistence.CiRunRepositoryPort;
import com.sdd.platform.application.port.out.persistence.EvidenceRepositoryPort;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreService;
import com.sdd.platform.config.AppProperties;
import com.sdd.platform.domain.model.CiJob;
import com.sdd.platform.domain.model.CiRun;
import com.sdd.platform.domain.model.ConnectorRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class GithubWorkflowJobWebhookService {

    private static final Logger log = LoggerFactory.getLogger(GithubWorkflowJobWebhookService.class);
    private static final String CONNECTOR_TYPE = "CI_RUN_METADATA";
    private static final String CI_PROVIDER = "GITHUB_ACTIONS";

    private final AppProperties props;
    private final ObjectMapper objectMapper;
    private final EvidenceRepositoryPort repositoryPort;
    private final CiRunRepositoryPort ciRunRepositoryPort;
    private final EvidenceQualityScoreService evidenceQualityScoreService;
    private final GithubSecurityEvidenceSnapshotService githubSecurityEvidenceSnapshotService;

    public GithubWorkflowJobWebhookService(AppProperties props,
                                           ObjectMapper objectMapper,
                                           EvidenceRepositoryPort repositoryPort,
                                           CiRunRepositoryPort ciRunRepositoryPort,
                                           EvidenceQualityScoreService evidenceQualityScoreService,
                                           GithubSecurityEvidenceSnapshotService githubSecurityEvidenceSnapshotService) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.repositoryPort = repositoryPort;
        this.ciRunRepositoryPort = ciRunRepositoryPort;
        this.evidenceQualityScoreService = evidenceQualityScoreService;
        this.githubSecurityEvidenceSnapshotService = githubSecurityEvidenceSnapshotService;
    }

    public CiRunModels.CollectorResult handle(byte[] rawBody, String signatureHeader, String eventType, String deliveryId) {
        verifySignature(rawBody, signatureHeader);

        if (!"workflow_job".equals(eventType)) {
            return new CiRunModels.CollectorResult("ignored:" + eventType, 0);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ConnectorRun connectorRun = startConnectorRun(deliveryId, now);

        try {
            JsonNode payload = objectMapper.readTree(rawBody);
            log.info(
                    "GitHub workflow_job payload delivery={} keys={} repoFullName={} workflowJobKeys={} workflowRunKeys={}",
                    deliveryId,
                    payload.fieldNames().hasNext() ? "present" : "empty",
                    resolveRepositoryFullName(payload),
                    payload.path("workflow_job").fieldNames().hasNext() ? "present" : "empty",
                    payload.path("workflow_run").fieldNames().hasNext() ? "present" : "empty"
            );
            int affected = processWorkflowJob(payload, deliveryId, connectorRun, now);
            log.info("GitHub workflow_job delivery={} processed records={}", deliveryId, affected);
            return new CiRunModels.CollectorResult("workflow_job", affected);
        } catch (Exception ex) {
            log.warn("GitHub workflow_job delivery={} failed: {}", deliveryId, ex.getMessage(), ex);
            failConnectorRun(connectorRun, ex.getMessage(), now);
            if (ex instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalStateException(ex.getMessage() == null ? "Workflow job ingestion failed" : ex.getMessage(), ex);
        }
    }

    private int processWorkflowJob(JsonNode payload, String deliveryId, ConnectorRun connectorRun, OffsetDateTime now) {
        String repositoryFullName = resolveRepositoryFullName(payload);
        if (repositoryFullName.isBlank()) {
            throw new IllegalArgumentException("Missing repository identity");
        }

        var repository = repositoryPort.findByRepositoryNameMaskedAndHostType(repositoryFullName, "GITHUB")
                .orElseThrow(() -> new IllegalArgumentException("Repository not found"));

        JsonNode workflowRunNode = firstObject(payload, "workflow_run");
        JsonNode workflowJobNode = firstObject(payload, "workflow_job");
        if (workflowJobNode == null || workflowJobNode.isMissingNode()) {
            workflowJobNode = payload;
        }
        if (workflowRunNode == null || workflowRunNode.isMissingNode()) {
            workflowRunNode = payload;
        }

        String workflowRunId = firstText(workflowRunNode, "id", "run_id");
        if (workflowRunId.isBlank()) {
            workflowRunId = firstText(workflowJobNode, "run_id", "workflow_run_id", "id");
        }
        if (workflowRunId.isBlank()) {
            workflowRunId = deliveryId;
        }
        String externalJobId = firstText(workflowJobNode, "id", "job_id");
        if (externalJobId.isBlank()) {
            externalJobId = deliveryId;
        }
        String workflowName = firstText(workflowRunNode, "name", "workflow_name");
        if (workflowName.isBlank()) {
            workflowName = firstText(workflowJobNode, "workflow_name", "workflow", "name", "job_name");
        }
        if (workflowName.isBlank()) {
            workflowName = "UNKNOWN_WORKFLOW";
        }
        String jobName = firstText(workflowJobNode, "name", "job_name");
        if (jobName.isBlank()) {
            jobName = "UNKNOWN_JOB";
        }
        String jobStatus = firstText(workflowJobNode, "status");
        String jobConclusion = firstText(workflowJobNode, "conclusion");
        String jobUrl = firstText(workflowJobNode, "html_url", "url", "run_url");
        String workflowRunUrl = firstText(workflowRunNode, "html_url", "url");
        if (workflowRunUrl.isBlank()) {
            workflowRunUrl = firstText(workflowJobNode, "run_url", "html_url", "url");
        }
        OffsetDateTime startedAt = firstTimestamp(workflowJobNode, "started_at");
        OffsetDateTime completedAt = firstTimestamp(workflowJobNode, "completed_at");
        if (startedAt != null && completedAt != null && completedAt.isBefore(startedAt)) {
            log.warn(
                    "GitHub workflow_job delivery={} has completed_at before started_at; normalizing completed_at to started_at. startedAt={} completedAt={}",
                    deliveryId,
                    startedAt,
                    completedAt
            );
            completedAt = startedAt;
        }
        String payloadBranchName = firstText(workflowRunNode, "head_branch");
        if (payloadBranchName.isBlank()) {
            payloadBranchName = firstText(workflowJobNode, "head_branch");
        }
        payloadBranchName = normalizeBranchName(payloadBranchName);
        String branchName = payloadBranchName;
        String headSha = firstText(workflowRunNode, "head_sha", "sha");
        if (headSha.isBlank()) {
            headSha = firstText(workflowJobNode, "head_sha", "sha");
        }
        Integer pullRequestNumber = extractPullRequestNumber(workflowRunNode, payload);
        String ciUrl = GithubWorkflowJobMapper.resolveCiUrl(
                jobUrl,
                workflowRunUrl,
                repositoryFullName,
                workflowRunId,
                externalJobId
        );
        String status = GithubWorkflowJobMapper.normalizeStatus(jobStatus, jobConclusion);

        log.debug(
                "GitHub workflow_job extracted delivery={} repo={} workflowRunId={} externalJobId={} workflowName={} jobName={} status={} jobUrl={} workflowRunUrl={} startedAt={} completedAt={}",
                deliveryId,
                repositoryFullName,
                workflowRunId,
                externalJobId,
                workflowName,
                jobName,
                status,
                jobUrl,
                workflowRunUrl,
                startedAt,
                completedAt
        );

        validateWorkflowJob(workflowRunId, externalJobId, workflowName, jobName, ciUrl, startedAt, completedAt);

        UUID pullRequestId = null;
        UUID ticketId = null;
        if (pullRequestNumber != null) {
            Optional<CiRunModels.PullRequestScope> prScope =
                    ciRunRepositoryPort.findPullRequestByRepositoryAndExternalNumber(repository.getRepositoryId(), pullRequestNumber);
            if (prScope.isPresent()) {
                pullRequestId = prScope.get().pullRequestId();
                ticketId = prScope.get().ticketId();
                if (prScope.get().sourceBranch() != null && !prScope.get().sourceBranch().isBlank()) {
                    branchName = prScope.get().sourceBranch().trim();
                }
            } else {
                branchName = "";
            }
        }
        if (pullRequestId == null && branchName != null && !branchName.isBlank()) {
            Optional<CiRunModels.PullRequestScope> prScope =
                    ciRunRepositoryPort.findPullRequestByRepositoryAndBranch(repository.getRepositoryId(), branchName);
            if (prScope.isPresent()) {
                pullRequestId = prScope.get().pullRequestId();
                if (ticketId == null) {
                    ticketId = prScope.get().ticketId();
                }
            }
        }
        if (pullRequestId == null && headSha != null && !headSha.isBlank()) {
            Optional<CiRunModels.PullRequestScope> prScope =
                    ciRunRepositoryPort.findPullRequestByRepositoryAndCommit(repository.getRepositoryId(), headSha);
            if (prScope.isPresent()) {
                pullRequestId = prScope.get().pullRequestId();
                if (ticketId == null) {
                    ticketId = prScope.get().ticketId();
                }
            }
        }

        if (ticketId == null && pullRequestId != null) {
            String ticketKeyFromPullRequest = ciRunRepositoryPort.findTicketKeyByPullRequestId(pullRequestId).orElse(null);
            if (ticketKeyFromPullRequest != null && !ticketKeyFromPullRequest.isBlank()) {
                ticketId = ciRunRepositoryPort.findTicketIdByProjectIdAndExternalKey(repository.getProjectId(), ticketKeyFromPullRequest)
                        .orElse(null);
            }
        }

        if (ticketId == null) {
            String ticketKey = GithubWorkflowJobMapper.extractTicketKey(branchName, workflowName, jobName, deliveryId);
            if (ticketKey != null && !ticketKey.isBlank()) {
                ticketId = ciRunRepositoryPort.findTicketIdByProjectIdAndExternalKey(repository.getProjectId(), ticketKey)
                        .orElse(null);
            }
        }

        boolean ciRunExisted = ciRunRepositoryPort.findCiRunIdByIdentity(CI_PROVIDER, repository.getRepositoryId(), workflowRunId)
                .isPresent();

        // Phase 1: UPSERT workflow run (1 row per external_run_id)
        CiRun ciRunRow = CiRun.builder()
                .id(null)
                .projectId(repository.getProjectId())
                .repositoryId(repository.getRepositoryId())
                .ticketId(ticketId)
                .pullRequestId(pullRequestId)
                .connectorRunId(connectorRun.getId())
                .ciProvider(CI_PROVIDER)
                .externalRunId(workflowRunId)
                .workflowName(workflowName)
                .status("IN_PROGRESS")
                .startedAt(startedAt)
                .completedAt(null)
                .ciUrl(ciUrl)
                .createdAt(now)
                .updatedAt(now)
                .build();
        CiRun savedRun = ciRunRepositoryPort.insertCiRun(ciRunRow);
        UUID ciRunId = savedRun.getId();

        // Phase 2: UPSERT individual job record
        CiJob ciJob = CiJob.builder()
                .ciRunId(ciRunId)
                .externalJobId(externalJobId)
                .jobName(jobName)
                .status(status)
                .startedAt(startedAt)
                .finishedAt(completedAt)
                .build();
        ciRunRepositoryPort.upsertCiJob(ciJob);

        // Phase 3: Recompute aggregate run status from all jobs
        String aggregateStatus = ciRunRepositoryPort.computeAggregateRunStatus(ciRunId);
        ciRunRepositoryPort.updateCiRunStatus(ciRunId, aggregateStatus);

        if (evidenceQualityScoreService != null && ticketId != null && isWorkflowJobCompleted(status, completedAt)) {
            log.info("GitHub workflow_job delivery={} triggering evidence quality score recalculation ticketId={} status={}",
                    deliveryId, ticketId, status);
            evidenceQualityScoreService.recalculateFromCi(ticketId, null, deliveryId);
        } else {
            log.debug("GitHub workflow_job delivery={} skipped evidence quality score recalculation ticketId={} status={} completedAt={}",
                    deliveryId, ticketId, status, completedAt);
        }
        if (completedAt != null && isTerminalStatus(status)) {
            String scanRevision = headSha != null && !headSha.isBlank() ? headSha : branchName;
            GithubSecurityEvidenceSnapshotService.Result evidenceResult =
                    githubSecurityEvidenceSnapshotService.collectFromWorkflowJob(
                            repositoryFullName,
                            branchName,
                            scanRevision,
                            pullRequestNumber,
                            workflowRunId,
                            externalJobId,
                            jobName,
                            deliveryId
                    );
            log.info("GitHub workflow_job delivery={} security evidence handled={} affected={}",
                    deliveryId, evidenceResult.handled(), evidenceResult.recordsAffected());
        }

        connectorRun.setProvider(CI_PROVIDER);
        connectorRun.setRepositoryId(repository.getRepositoryId());
        connectorRun.setStatus(ConnectorRun.Status.SUCCESS);
        connectorRun.setFinishedAt(now);
        connectorRun.setRecordsReceived(1);
        connectorRun.setRecordsInserted(ciRunExisted ? 0 : 1);
        connectorRun.setRecordsUpdated(ciRunExisted ? 1 : 0);
        connectorRun.setRecordsSkipped(0);
        connectorRun.setRecordsError(0);
        ciRunRepositoryPort.updateConnectorRun(connectorRun);

        return 1;
    }

    private static boolean isWorkflowJobCompleted(String status, OffsetDateTime completedAt) {
        if (completedAt != null) {
            return true;
        }
        if (status == null) {
            return false;
        }
        return switch (status.toUpperCase()) {
            case "SUCCESS", "FAILURE", "CANCELLED", "SKIPPED" -> true;
            default -> false;
        };
    }

    private ConnectorRun startConnectorRun(String traceId, OffsetDateTime now) {
        CiRunModels.ConnectorScope connector = ciRunRepositoryPort.findConnectorByType(CONNECTOR_TYPE)
                .orElseThrow(() -> new IllegalStateException("CONNECTOR_NOT_CONFIGURED"));

        ConnectorRun run = ConnectorRun.builder()
                .connectorId(connector.connectorId())
                .connectorName(connector.connectorName())
                .provider(CI_PROVIDER)
                .startedAt(now)
                .status(ConnectorRun.Status.RUNNING)
                .recordsReceived(0)
                .recordsInserted(0)
                .recordsUpdated(0)
                .recordsSkipped(0)
                .recordsError(0)
                .traceId(traceId)
                .build();
        return ciRunRepositoryPort.insertConnectorRun(run);
    }

    private void failConnectorRun(ConnectorRun run, String errorMessage, OffsetDateTime now) {
        run.setFinishedAt(now);
        run.setStatus(ConnectorRun.Status.FAILED);
        run.setErrorMessage(errorMessage);
        run.setRecordsReceived(1);
        run.setRecordsInserted(0);
        run.setRecordsUpdated(0);
        run.setRecordsSkipped(0);
        run.setRecordsError(1);
        ciRunRepositoryPort.updateConnectorRun(run);
    }

    private static void validateWorkflowJob(String workflowRunId,
                                            String externalJobId,
                                            String workflowName,
                                            String jobName,
                                            String ciUrl,
                                            OffsetDateTime startedAt,
                                            OffsetDateTime completedAt) {
        if (workflowRunId == null || workflowRunId.isBlank()) {
            throw new IllegalArgumentException("Missing workflow run ID");
        }
        if (externalJobId == null || externalJobId.isBlank()) {
            throw new IllegalArgumentException("Missing job ID");
        }
        if (workflowName == null || workflowName.isBlank()) {
            throw new IllegalArgumentException("Missing workflow name");
        }
        if (jobName == null || jobName.isBlank()) {
            throw new IllegalArgumentException("Missing job name");
        }
        if (ciUrl == null || ciUrl.isBlank()) {
            throw new IllegalArgumentException("Missing CI URL");
        }
    }

    private static boolean isTerminalStatus(String status) {
        return status != null
                && !"QUEUED".equalsIgnoreCase(status)
                && !"IN_PROGRESS".equalsIgnoreCase(status);
    }

    private Integer extractPullRequestNumber(JsonNode workflowRunNode, JsonNode payload) {
        Integer fromWorkflowRun = extractPullRequestNumberFromNode(workflowRunNode);
        if (fromWorkflowRun != null) {
            return fromWorkflowRun;
        }
        return extractPullRequestNumberFromNode(payload.path("pull_request"));
    }

    private Integer extractPullRequestNumberFromNode(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode pullRequests = node.path("pull_requests");
        if (pullRequests.isArray() && !pullRequests.isEmpty()) {
            for (JsonNode item : pullRequests) {
                Integer value = firstInteger(item, "number", "pr_number");
                if (value != null) {
                    return value;
                }
            }
        }
        return firstInteger(node, "number", "pr_number");
    }

    private static String text(JsonNode node, String... path) {
        JsonNode current = node;
        for (String segment : path) {
            if (current == null || current.isMissingNode()) {
                return "";
            }
            current = current.path(segment);
        }
        return current == null || current.isMissingNode() ? "" : current.asText("");
    }

    private static String resolveRepositoryFullName(JsonNode payload) {
        String fullName = text(payload, "repository", "full_name");
        if (!fullName.isBlank()) {
            return fullName;
        }

        String owner = text(payload, "repository", "owner", "login");
        String name = text(payload, "repository", "name");
        if (!owner.isBlank() && !name.isBlank()) {
            return owner.trim() + "/" + name.trim();
        }

        String directName = text(payload, "repository", "name");
        if (!directName.isBlank()) {
            return directName;
        }

        return "";
    }

    private static JsonNode firstObject(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode child = node.path(fieldName);
        return child.isObject() ? child : null;
    }

    private static String firstText(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode()) {
            return "";
        }
        for (String fieldName : fieldNames) {
            JsonNode child = node.path(fieldName);
            if (child != null && !child.isMissingNode()) {
                String value = child.asText("");
                if (!value.isBlank()) {
                    return value.trim();
                }
            }
        }
        return "";
    }

    private static Integer firstInteger(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode child = node.path(fieldName);
            if (child != null && !child.isMissingNode() && child.canConvertToInt()) {
                return child.asInt();
            }
            Integer parsed = GithubWorkflowJobMapper.toInteger(child == null ? null : child.asText(null));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private static OffsetDateTime firstTimestamp(JsonNode node, String... fieldNames) {
        String value = firstText(node, fieldNames);
        if (value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String normalizeBranchName(String branchName) {
        if (branchName == null) {
            return "";
        }
        String normalized = branchName.trim();
        if (normalized.startsWith("refs/heads/")) {
            normalized = normalized.substring("refs/heads/".length());
        }
        return normalized;
    }

    private void verifySignature(byte[] rawBody, String signatureHeader) {
        String secret = props.connectors().github().webhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new SecurityException("EDCAP_WEBHOOK_SECRET not configured");
        }
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            throw new SecurityException("Missing or malformed X-Hub-Signature-256");
        }

        String expectedHex = signatureHeader.substring("sha256=".length());
        String actualHex = hmacSha256Hex(secret, rawBody);

        if (!MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.UTF_8),
                actualHex.getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("HMAC mismatch");
        }
    }

    private static String hmacSha256Hex(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC computation failed", ex);
        }
    }
}
