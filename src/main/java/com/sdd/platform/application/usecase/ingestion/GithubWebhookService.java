package com.sdd.platform.application.usecase.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.CollectorRunResult;
import com.sdd.platform.application.port.out.integration.GithubPullRequestFilesPort;
import com.sdd.platform.application.port.out.persistence.ArtifactScannerPersistencePort;
import com.sdd.platform.application.port.out.integration.ArtifactScannerSourcePort;
import com.sdd.platform.application.usecase.docparse.ImplPlanParseService;
import com.sdd.platform.application.usecase.docparse.TestPlanParseService;
import com.sdd.platform.application.usecase.docparse.TestResultsParseService;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactScanMode;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactScanRequest;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactScanTriggerType;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactTypeScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.RepositoryScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ScanRun;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerService;
import com.sdd.platform.application.port.out.persistence.AiFindingStatPort.AiFindingStatRecord;
import com.sdd.platform.config.AppProperties;
import com.sdd.platform.domain.service.markdown.aireviewstats.AiReviewStatsParser;
import com.sdd.platform.domain.service.markdown.aireviewstats.AiReviewStatsParser.AiReviewStats;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Receives raw bytes from {@link com.sdd.platform.web.webhook.GithubWebhookController}
 * and turns them into DB rows.
 *
 * Why split controller/service? — controller's job is HTTP plumbing (read body,
 * read headers, return status). Everything that touches our own domain lives here.
 *
 * Security:
 *   1. Verify the {@code X-Hub-Signature-256} HMAC BEFORE parsing the body.
 *      Constant-time comparison protects against timing-attack signature probing.
 *   2. Refuse to log payload contents on failure — could contain repo names,
 *      author handles, branch names a third party shouldn't see.
 *
 * Idempotency:
 *   GitHub retries failed deliveries. Our DB has UNIQUE (repository_id, external_pr_number)
 *   so duplicate deliveries upsert the same row instead of creating two.
 */
@Service
public class GithubWebhookService {

    private static final Logger log = LoggerFactory.getLogger(GithubWebhookService.class);

    private final AppProperties props;
    private final ArtifactScannerPersistencePort artifactScannerPersistence;
    private final ArtifactScannerService artifactScannerService;
    private final ArtifactScannerSourcePort artifactScannerSourcePort;
    private final GithubPullRequestFilesPort pullRequestFilesPort;
    private final GitPrMetadataCollectorService gitPrMetadataCollectorService;
    private final GithubSecurityEvidenceSnapshotService githubSecurityEvidenceSnapshotService;
    private final ImplPlanParseService implPlanParseService;
    private final TestPlanParseService testPlanParseService;
    private final TestResultsParseService testResultsParseService;
    private final ObjectMapper objectMapper;
    private final TemplateUsageStatWriter templateUsageStatWriter;
    private final AiFindingStatWriter aiFindingStatWriter;
    private final MarkdownParserCore markdownParserCore = new MarkdownParserCore();
    private final AiReviewStatsParser aiReviewStatsParser = new AiReviewStatsParser();

    public GithubWebhookService(AppProperties props,
                                ArtifactScannerPersistencePort artifactScannerPersistence,
                                ArtifactScannerService artifactScannerService,
                                ArtifactScannerSourcePort artifactScannerSourcePort,
                                GithubPullRequestFilesPort pullRequestFilesPort,
                                GitPrMetadataCollectorService gitPrMetadataCollectorService,
                                GithubSecurityEvidenceSnapshotService githubSecurityEvidenceSnapshotService,
                                ImplPlanParseService implPlanParseService,
                                TestPlanParseService testPlanParseService,
                                TestResultsParseService testResultsParseService,
                                ObjectMapper objectMapper,
                                TemplateUsageStatWriter templateUsageStatWriter,
                                AiFindingStatWriter aiFindingStatWriter) {
        this.props = props;
        this.artifactScannerPersistence = artifactScannerPersistence;
        this.artifactScannerService = artifactScannerService;
        this.artifactScannerSourcePort = artifactScannerSourcePort;
        this.pullRequestFilesPort = pullRequestFilesPort;
        this.gitPrMetadataCollectorService = gitPrMetadataCollectorService;
        this.githubSecurityEvidenceSnapshotService = githubSecurityEvidenceSnapshotService;
        this.implPlanParseService = implPlanParseService;
        this.testPlanParseService = testPlanParseService;
        this.testResultsParseService = testResultsParseService;
        this.objectMapper = objectMapper;
        this.templateUsageStatWriter = templateUsageStatWriter;
        this.aiFindingStatWriter = aiFindingStatWriter;
    }

    /**
     * @param rawBody  the exact bytes GitHub sent (used for HMAC)
     * @param signatureHeader  value of {@code X-Hub-Signature-256} — format {@code sha256=<hex>}
     * @param eventType  value of {@code X-GitHub-Event} (e.g. {@code pull_request}, {@code ping})
     * @param deliveryId  value of {@code X-GitHub-Delivery} — UUID, for logging only
     * @throws SecurityException if signature invalid or secret not configured
     */
    public Result handle(byte[] rawBody, String signatureHeader, String eventType, String deliveryId) {
        verifySignature(rawBody, signatureHeader);

        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed JSON payload");
        }

        log.info("GitHub webhook received: event={} delivery={}", eventType, deliveryId);

        return switch (eventType) {
            case "ping" -> new Result("ping", 0);
            case "pull_request" -> handlePullRequest(payload, deliveryId);
            case "pull_request_review" -> handlePullRequestReview(payload, deliveryId);
            case "pull_request_review_comment" -> handlePullRequestReviewComment(payload, deliveryId);
            case "push" -> new Result("ignored:push", 0);
            default -> {
                log.debug("GitHub webhook: ignoring event '{}'", eventType);
                yield new Result("ignored:" + eventType, 0);
            }
        };
    }

    // ----- HMAC verification -----

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

        // Constant-time compare: do NOT use String.equals() — leaks timing info.
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
            byte[] digest = mac.doFinal(body);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    private Result handlePullRequest(JsonNode payload, String deliveryId) {
        String repoKey = payload.path("repository").path("full_name").asText();
        if (repoKey.isBlank()) {
            log.warn("GitHub webhook delivery={} missing repository.full_name", deliveryId);
            return new Result("missing-repo", 0);
        }

        Optional<RepositoryScope> repository = artifactScannerPersistence.findRepositoryByMaskedName(repoKey);
        if (repository.isEmpty()) {
            log.warn("GitHub webhook delivery={} for unknown scanner repo {}", deliveryId, repoKey);
            return new Result("unknown-repo", 0);
        }
        log.info("GitHub pull_request delivery={} repo={} repoId={} action={}",
                deliveryId,
                repoKey,
                repository.get().repositoryId(),
                payload.path("action").asText(""));

        String action = payload.path("action").asText("");
        if (!isSupportedPullRequestAction(action)) {
            return new Result("pull_request:" + action, 0);
        }
        JsonNode pullRequest = payload.path("pull_request");

        String targetBranch = pullRequest.path("base").path("ref").asText("");
        if (targetBranch.isBlank()) {
            log.warn("GitHub pull_request delivery={} missing base.ref", deliveryId);
            return new Result("missing-branch", 0);
        }

        int prNumber = pullRequest.path("number").asInt(0);
        if (prNumber <= 0) {
            log.warn("GitHub pull_request delivery={} missing pull_request.number", deliveryId);
            return new Result("missing-pr-number", 0);
        }

        if (!isTargetBranch(targetBranch, repository.get().defaultBranch())) {
            log.info("GitHub pull_request delivery={} ignored for repo {} branch={} defaultBranch={}",
                    deliveryId, repoKey, targetBranch, repository.get().defaultBranch());
            return new Result("ignored-branch", 0);
        }

        CollectorRunResult collectorResult = gitPrMetadataCollectorService.collectFromWebhook(repository.get().repositoryId(), prNumber, deliveryId);
        if ("FAILED".equalsIgnoreCase(collectorResult.status())) {
            log.warn("GitHub pull_request delivery={} collector failed for repo {} pr #{} status={} failures={}",
                    deliveryId, repoKey, prNumber, collectorResult.status(), collectorResult.failureCount());
        }

        List<String> changedFilePaths;
        try {
            changedFilePaths = pullRequestFilesPort.listChangedFilePaths(repoKey, prNumber);
        } catch (WebClientResponseException.NotFound ex) {
            log.warn("GitHub pull_request delivery={} cannot read PR files for repo {} pr #{} - private repo or token lacks access",
                    deliveryId, repoKey, prNumber);
            return new Result("github-pr-files-not-found", 0);
        }
        String sourceBranch = pullRequest.path("head").path("ref").asText("");
        if (sourceBranch.isBlank()) {
            sourceBranch = targetBranch;
        }
        sourceBranch = normalizeBranchName(sourceBranch);

        List<String> ticketKeys = changedFilePaths.stream()
                .map(GithubWebhookService::ticketKeyFromPath)
                .filter(key -> key != null && !key.isBlank())
                .distinct()
                .toList();
        String revision = revisionFromPullRequest(pullRequest, sourceBranch);

        GithubSecurityEvidenceSnapshotService.Result securityEvidenceResult =
                githubSecurityEvidenceSnapshotService.collectFromPullRequest(
                        repoKey,
                        sourceBranch,
                        revision,
                        prNumber,
                        changedFilePaths,
                        deliveryId
                );
        if (!"ignored:no-security-evidence".equals(securityEvidenceResult.handled())
                && !"security_evidence_failed".equals(securityEvidenceResult.handled())) {
            log.info("GitHub pull_request delivery={} security evidence handled={} affected={}",
                    deliveryId,
                    securityEvidenceResult.handled(),
                    securityEvidenceResult.recordsAffected());
        }
        if ("closed".equals(action) && pullRequest.path("merged").asBoolean(false)) {
            validateTemplateUsage(repoKey, changedFilePaths, revision,
                    repository.get().projectId(), repository.get().repositoryId(), deliveryId);
        }
        if (ticketKeys.isEmpty()) {
            log.info("GitHub pull_request delivery={} no docs/changes/<TICKET>/ files in diff for repo {} pr #{}; falling back to repo tree scan",
                    deliveryId, repoKey, prNumber);
            try {
                ArtifactScannerSourcePort.ResolvedRevision resolved = artifactScannerSourcePort.resolveRevision(repoKey, revision);
                var tree = artifactScannerSourcePort.listTree(repoKey, resolved.revisionSha());
                ticketKeys = discoverTicketKeysFromTree(tree);
            } catch (Exception ex) {
                log.warn("GitHub pull_request delivery={} fallback ticket discovery failed for repo {} pr #{} revision={}: {}",
                        deliveryId, repoKey, prNumber, revision, ex.getMessage(), ex);
                ticketKeys = List.of();
            }
            if (ticketKeys.isEmpty()) {
                log.warn("GitHub pull_request delivery={} no docs/changes/<TICKET>/ files found for repo {} pr #{}",
                        deliveryId, repoKey, prNumber);
                return new Result("missing-ticket-scope", 0);
            }
        }
        log.info("GitHub pull_request delivery={} repo={} pr#{} branch={} targetBranch={} tickets={} changedFiles={}",
                deliveryId,
                repoKey,
                prNumber,
                sourceBranch,
                targetBranch,
                ticketKeys,
                changedFilePaths.size());

        String title = pullRequest.path("title").asText(ticketKeys.get(0));
        String ticketStatus = pullRequestStatusFromAction(action, pullRequest);
        OffsetDateTime lastCommitAt = null;
        try {
            lastCommitAt = pullRequestFilesPort.resolveLastCommitAt(repoKey, prNumber).orElse(null);
        } catch (WebClientResponseException.NotFound ex) {
            log.warn("GitHub pull_request delivery={} cannot read PR commits for repo {} pr #{} - private repo or token lacks access",
                    deliveryId, repoKey, prNumber);
        }
        Map<String, com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.TicketScope> ticketScopes = new LinkedHashMap<>();
        for (String ticketKey : ticketKeys) {
            ticketScopes.put(ticketKey, artifactScannerPersistence.upsertMinimalTicket(repository.get().projectId(), ticketKey, title, ticketStatus, lastCommitAt));
        }
        if ("closed".equals(action) && pullRequest.path("merged").asBoolean(false)) {
            recordAiFindingStats(repoKey, changedFilePaths, revision,
                    repository.get().projectId(), repository.get().repositoryId(), ticketScopes, deliveryId);
        }

        if (!shouldTriggerScan(action)) {
            log.info("GitHub pull_request delivery={} updated ticket scopes {} status={} lastCommitAt={} without scan",
                    deliveryId, ticketKeys, ticketStatus, lastCommitAt);
            int affected = ticketKeys.size() + collectorResult.processedPrCount() + collectorResult.processedCommitCount() + collectorResult.processedChangedFileCount();
            return new Result("pull_request:" + action, affected);
        }

        log.info("GitHub pull_request delivery={} triggering scanner repo={} revision={} tickets={}",
                deliveryId, repoKey, revision, ticketKeys);

        ScanRun run = artifactScannerService.scan(new ArtifactScanRequest(
                repository.get().repositoryId(),
                revision,
                ArtifactScanMode.TICKET_SCOPED,
                ticketKeys,
                ArtifactScanTriggerType.WEBHOOK,
                "github-webhook",
                deliveryId
        ));

        log.info("GitHub pull_request delivery={} triggered artifact scan run={} for repo {} branch {} revision {} tickets {}",
                deliveryId, run.connectorRunId(), repoKey, sourceBranch, revision, ticketKeys);

        int affected = ticketKeys.size() + collectorResult.processedPrCount() + collectorResult.processedCommitCount() + collectorResult.processedChangedFileCount() + run.recordsWritten();
        return new Result("pull_request:" + action, affected);
    }

    private Result handlePullRequestReviewComment(JsonNode payload, String deliveryId) {
        String action = payload.path("action").asText("");
        if (!"created".equals(action) && !"edited".equals(action) && !"deleted".equals(action)) {
            return new Result("pull_request_review_comment:" + action, 0);
        }

        String repoKey = payload.path("repository").path("full_name").asText();
        if (repoKey.isBlank()) {
            log.warn("GitHub pull_request_review_comment delivery={} missing repository.full_name", deliveryId);
            return new Result("missing-repo", 0);
        }

        Optional<RepositoryScope> repository = artifactScannerPersistence.findRepositoryByMaskedName(repoKey);
        if (repository.isEmpty()) {
            log.warn("GitHub pull_request_review_comment delivery={} for unknown repo {}", deliveryId, repoKey);
            return new Result("unknown-repo", 0);
        }

        String branch = normalizeBranchName(payload.path("pull_request").path("base").path("ref").asText(""));
        if (!isTargetBranch(branch, repository.get().defaultBranch())) {
            log.info("GitHub pull_request_review_comment delivery={} ignored for repo {} branch={} defaultBranch={}",
                    deliveryId, repoKey, branch, repository.get().defaultBranch());
            return new Result("ignored-branch", 0);
        }

        int prNumber = payload.path("pull_request").path("number").asInt(0);
        if (prNumber <= 0) {
            log.warn("GitHub pull_request_review_comment delivery={} missing pull_request.number", deliveryId);
            return new Result("missing-pr-number", 0);
        }

        log.info("GitHub pull_request_review_comment delivery={} repo={} pr#{} action={} — re-collecting metadata",
                deliveryId, repoKey, prNumber, action);
        CollectorRunResult result = gitPrMetadataCollectorService.collectFromWebhook(
                repository.get().repositoryId(), prNumber, deliveryId);
        return new Result("pull_request_review_comment:" + action, result.processedPrCount());
    }

    private Result handlePullRequestReview(JsonNode payload, String deliveryId) {
        String action = payload.path("action").asText("");
        if (!"submitted".equals(action) && !"dismissed".equals(action) && !"edited".equals(action)) {
            return new Result("pull_request_review:" + action, 0);
        }

        String repoKey = payload.path("repository").path("full_name").asText();
        if (repoKey.isBlank()) {
            log.warn("GitHub pull_request_review delivery={} missing repository.full_name", deliveryId);
            return new Result("missing-repo", 0);
        }

        Optional<RepositoryScope> repository = artifactScannerPersistence.findRepositoryByMaskedName(repoKey);
        if (repository.isEmpty()) {
            log.warn("GitHub pull_request_review delivery={} for unknown repo {}", deliveryId, repoKey);
            return new Result("unknown-repo", 0);
        }

        String branch = normalizeBranchName(payload.path("pull_request").path("base").path("ref").asText(""));
        if (!isTargetBranch(branch, repository.get().defaultBranch())) {
            log.info("GitHub pull_request_review delivery={} ignored for repo {} branch={} defaultBranch={}",
                    deliveryId, repoKey, branch, repository.get().defaultBranch());
            return new Result("ignored-branch", 0);
        }

        int prNumber = payload.path("pull_request").path("number").asInt(0);
        if (prNumber <= 0) {
            log.warn("GitHub pull_request_review delivery={} missing pull_request.number", deliveryId);
            return new Result("missing-pr-number", 0);
        }

        log.info("GitHub pull_request_review delivery={} repo={} pr#{} action={} — re-collecting metadata",
                deliveryId, repoKey, prNumber, action);
        CollectorRunResult result = gitPrMetadataCollectorService.collectFromWebhook(
                repository.get().repositoryId(), prNumber, deliveryId);
        return new Result("pull_request_review:" + action, result.processedPrCount());
    }

    private static boolean isTargetBranch(String branch, String defaultBranch) {
        return branch != null && !branch.isBlank()
                && defaultBranch != null && !defaultBranch.isBlank()
                && branch.equals(defaultBranch);
    }

    private static boolean isSupportedPullRequestAction(String action) {
        return "opened".equals(action)
                || "synchronize".equals(action)
                || "reopened".equals(action)
                || "closed".equals(action);
    }

    private static boolean shouldTriggerScan(String action) {
        return "opened".equals(action) || "synchronize".equals(action) || "reopened".equals(action);
    }

    private static String pullRequestStatusFromAction(String action, JsonNode pullRequest) {
        if ("closed".equals(action)) {
            return pullRequest.path("merged").asBoolean(false) ? "MERGED" : "CLOSED";
        }
        return "OPEN";
    }

    private static String revisionFromPullRequest(JsonNode pullRequest, String fallbackBranch) {
        String headSha = pullRequest.path("head").path("sha").asText("");
        if (headSha != null && !headSha.isBlank()) {
            return headSha;
        }
        String headRef = pullRequest.path("head").path("ref").asText("");
        if (headRef != null && !headRef.isBlank()) {
            return headRef;
        }
        return fallbackBranch;
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

    private static String ticketKeyFromPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        int idx = path.indexOf("/changes/");
        int start;
        if (idx >= 0) {
            start = idx + "/changes/".length();
        } else if (path.startsWith("changes/")) {
            start = "changes/".length();
        } else {
            return null;
        }
        int slashIndex = path.indexOf('/', start);
        if (slashIndex <= start) {
            return null;
        }
        String ticketKey = path.substring(start, slashIndex).trim();
        return ticketKey.isBlank() ? null : ticketKey;
    }

    private static String resolveChangesFilePath(
            Map<String, com.sdd.platform.application.port.out.integration.ArtifactScannerSourcePort.GitHubTreeEntry> tree,
            String ticketKey, String fileName) {
        String suffix = "/changes/" + ticketKey + "/" + fileName;
        String fallback = "changes/" + ticketKey + "/" + fileName;
        return tree.keySet().stream()
                .filter(p -> p.endsWith(suffix) || p.equals(fallback))
                .findFirst()
                .orElse(fallback);
    }

    private static List<String> discoverTicketKeysFromTree(Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree) {
        if (tree == null || tree.isEmpty()) {
            return List.of();
        }
        return tree.keySet().stream()
                .map(GithubWebhookService::ticketKeyFromPath)
                .filter(key -> key != null && !key.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private static final String TEMPLATE_BASE_PATH = "documents/docs/standards/templates/";

    private void validateTemplateUsage(String repoKey, List<String> changedFilePaths, String revision,
            UUID projectId, UUID repositoryId, String deliveryId) {
        List<String> eligiblePaths = changedFilePaths == null ? List.of()
                : changedFilePaths.stream()
                        .filter(path -> path != null && !path.isBlank() && ticketKeyFromPath(path) != null)
                        .toList();
        if (eligiblePaths.isEmpty()) {
            return;
        }

        Map<String, UUID> phaseIdByFileName = artifactScannerPersistence.findArtifactTypes().stream()
                .filter(scope -> scope.defaultFileName() != null && !scope.defaultFileName().isBlank())
                .collect(Collectors.toMap(ArtifactTypeScope::defaultFileName, ArtifactTypeScope::phaseId, (a, b) -> a));

        Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree;
        try {
            ArtifactScannerSourcePort.ResolvedRevision resolved = artifactScannerSourcePort.resolveRevision(repoKey, revision);
            tree = artifactScannerSourcePort.listTree(repoKey, resolved.revisionSha());
        } catch (WebClientResponseException ex) {
            if (isSkippableFetchError(ex)) {
                log.warn("GitHub pull_request delivery={} template-usage validation skipped: cannot resolve revision/tree for repo={} revision={} due to {}: {}",
                        deliveryId, repoKey, revision, ex.getStatusCode(), ex.getMessage());
                return;
            }
            throw ex;
        }

        for (String path : eligiblePaths) {
            String fileName = fileNameOf(path);
            UUID phaseId = phaseIdByFileName.get(fileName);
            if (phaseId == null) {
                continue;
            }
            String templatePath = resolveTemplatePath(tree, fileName);
            if (templatePath == null) {
                continue;
            }
            String changedContent;
            String templateContent;
            try {
                changedContent = fetchTemplateUsageBlob(repoKey, tree, path, deliveryId);
                templateContent = fetchTemplateUsageBlob(repoKey, tree, templatePath, deliveryId);
            } catch (WebClientResponseException ex) {
                if (isSkippableFetchError(ex)) {
                    log.warn("GitHub pull_request delivery={} template-usage: skipping file={} template={} due to {}: {}",
                            deliveryId, path, templatePath, ex.getStatusCode(), ex.getMessage());
                    continue;
                }
                throw ex;
            }
            if (changedContent == null || templateContent == null) {
                continue;
            }
            boolean matched = headerStructureMatches(changedContent, path, templateContent, templatePath);
            try {
                templateUsageStatWriter.recordIndependently(projectId, repositoryId, phaseId, matched);
            } catch (DataAccessException ex) {
                log.warn("GitHub pull_request delivery={} template-usage: counter write failed for project={} repository={} phase={} path={}: {}",
                        deliveryId, projectId, repositoryId, phaseId, path, ex.getMessage());
            }
        }
    }

    private static final String AI_REVIEW_FILE_NAME = "ai-review.md";

    private void recordAiFindingStats(String repoKey, List<String> changedFilePaths, String revision,
            UUID projectId, UUID repositoryId,
            Map<String, com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.TicketScope> ticketScopes,
            String deliveryId) {
        List<String> eligiblePaths = changedFilePaths == null ? List.of()
                : changedFilePaths.stream()
                        .filter(path -> path != null && AI_REVIEW_FILE_NAME.equals(fileNameOf(path))
                                && ticketKeyFromPath(path) != null)
                        .toList();
        if (eligiblePaths.isEmpty()) {
            return;
        }

        Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree;
        try {
            ArtifactScannerSourcePort.ResolvedRevision resolved = artifactScannerSourcePort.resolveRevision(repoKey, revision);
            tree = artifactScannerSourcePort.listTree(repoKey, resolved.revisionSha());
        } catch (WebClientResponseException ex) {
            if (isSkippableFetchError(ex)) {
                log.warn("GitHub pull_request delivery={} ai-finding-stats: cannot resolve revision/tree for repo={} revision={} due to {}: {}",
                        deliveryId, repoKey, revision, ex.getStatusCode(), ex.getMessage());
                return;
            }
            throw ex;
        }

        for (String path : eligiblePaths) {
            String ticketKey = ticketKeyFromPath(path);
            var ticketScope = ticketScopes.get(ticketKey);
            if (ticketScope == null) {
                continue;
            }

            String content;
            try {
                content = fetchTemplateUsageBlob(repoKey, tree, path, deliveryId);
            } catch (WebClientResponseException ex) {
                if (isSkippableFetchError(ex)) {
                    log.warn("GitHub pull_request delivery={} ai-finding-stats: skipping file={} ticket={} due to {}: {}",
                            deliveryId, path, ticketKey, ex.getStatusCode(), ex.getMessage());
                    continue;
                }
                throw ex;
            }
            if (content == null) {
                continue;
            }

            Optional<AiReviewStats> parsed = aiReviewStatsParser.parse(content);
            if (parsed.isEmpty()) {
                log.info("GitHub pull_request delivery={} ai-finding-stats: no §8 stats table found for ticket={} path={}",
                        deliveryId, ticketKey, path);
                continue;
            }
            AiReviewStats stats = parsed.get();
            try {
                aiFindingStatWriter.recordStat(new AiFindingStatRecord(
                        projectId,
                        repositoryId,
                        ticketScope.ticketId(),
                        stats.blockerMajorResolvedCount(),
                        stats.blockerMajorTotalCount(),
                        stats.aiReviewAdoptedCount(),
                        stats.aiReviewFindingTotalCount(),
                        stats.aiReviewValidCount(),
                        stats.aiReviewFalsePositiveCount(),
                        stats.aiReviewResolvedCount()));
            } catch (DataAccessException ex) {
                log.warn("GitHub pull_request delivery={} ai-finding-stats: write failed for project={} repository={} ticket={}: {}",
                        deliveryId, projectId, repositoryId, ticketScope.ticketId(), ex.getMessage());
            }
        }
    }

    private String fileNameOf(String path) {
        int slashIndex = path.lastIndexOf('/');
        return slashIndex < 0 ? path : path.substring(slashIndex + 1);
    }

    private String resolveTemplatePath(Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree, String fileName) {
        List<String> candidates = List.of(
                TEMPLATE_BASE_PATH + fileName,
                TEMPLATE_BASE_PATH + "_ticket-template/" + fileName,
                TEMPLATE_BASE_PATH + "_light-ticket-template/" + fileName
        );
        for (String candidate : candidates) {
            if (tree.containsKey(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String fetchTemplateUsageBlob(String repoKey, Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree,
            String path, String deliveryId) {
        ArtifactScannerSourcePort.GitHubTreeEntry entry = tree.get(path);
        if (entry == null || entry.sha() == null || entry.sha().isBlank()) {
            log.warn("GitHub pull_request delivery={} template-usage: no blob sha for path={}", deliveryId, path);
            return null;
        }
        return new String(artifactScannerSourcePort.readBlob(repoKey, entry.sha()), StandardCharsets.UTF_8);
    }

    private boolean headerStructureMatches(String changedContent, String changedPath, String templateContent, String templatePath) {
        List<MarkdownSection> changedSections = markdownParserCore.parse(changedContent, changedPath).sections();
        List<MarkdownSection> templateSections = markdownParserCore.parse(templateContent, templatePath).sections();
        if (changedSections.size() != templateSections.size()) {
            return false;
        }
        for (int i = 0; i < changedSections.size(); i++) {
            MarkdownSection changed = changedSections.get(i);
            MarkdownSection template = templateSections.get(i);
            if (changed.level() != template.level() || !changed.title().equals(template.title())) {
                return false;
            }
        }
        return true;
    }

    private boolean isSkippableFetchError(WebClientResponseException ex) {
        int status = ex.getStatusCode().value();
        return status == 401 || status == 403 || status == 404;
    }

    public record Result(String handled, int recordsAffected) {}
}
