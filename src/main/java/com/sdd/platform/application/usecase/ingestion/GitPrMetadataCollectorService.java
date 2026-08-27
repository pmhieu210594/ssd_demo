package com.sdd.platform.application.usecase.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.integration.GithubPullRequestMetadataPort;
import com.sdd.platform.application.port.out.persistence.GitPrMetadataCollectorPersistencePort;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.ChangedFileSnapshot;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.CommitSnapshot;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.CommitUpsert;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.CollectorRun;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.CollectorRunResult;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.PullRequestGraph;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.PullRequestSummary;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.PullRequestUpsert;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.RepositoryScanRequest;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.ReviewCommentSnapshot;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.ReviewCommentUpsert;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.ReviewSnapshot;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.ReviewUpsert;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.TraceabilityLinkUpsert;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreService;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ConnectorScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.RepositoryScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.TicketScope;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AppUser;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GitPrMetadataCollectorService {

    private static final Pattern TICKET_PATTERN = Pattern.compile("(?<![A-Z0-9])([A-Z][A-Z0-9-]{2,})(?![A-Z0-9-])");
    private static final List<String> TICKET_STOP_WORDS = List.of(
            "FEATURE",
            "FIX",
            "HOTFIX",
            "BUGFIX",
            "REFACTOR",
            "CHORE",
            "DOC",
            "DOCS",
            "WIP",
            "TEMP",
            "MAIN",
            "DEVELOP",
            "MASTER",
            "RELEASE",
            "TEST"
    );
    private static final String CONNECTOR_TYPE = "GIT_PR_METADATA_COLLECTOR";
    private static final String CONNECTOR_NAME = "Git PR Metadata Collector";

    private final GitPrMetadataCollectorPersistencePort persistence;
    private final GithubPullRequestMetadataPort githubPort;
    private final ObjectMapper objectMapper;
    private final EvidenceQualityScoreService evidenceQualityScoreService;

    public GitPrMetadataCollectorService(GitPrMetadataCollectorPersistencePort persistence,
                                         GithubPullRequestMetadataPort githubPort,
                                         ObjectMapper objectMapper,
                                         EvidenceQualityScoreService evidenceQualityScoreService) {
        this.persistence = persistence;
        this.githubPort = githubPort;
        this.objectMapper = objectMapper;
        this.evidenceQualityScoreService = evidenceQualityScoreService;
    }

    public CollectorRunResult collectPullRequest(UUID repositoryId, int pullRequestNumber, String requestedBy) {
        return executeCollect(new RepositoryScanRequest(
                repositoryId,
                pullRequestNumber,
                null,
                null,
                true,
                requestedBy,
                resolveTraceId()
        ));
    }

    public CollectorRunResult collectRepository(UUID repositoryId,
                                                LocalDate fromDate,
                                                LocalDate toDate,
                                                boolean includeClosed,
                                                String requestedBy) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must be <= toDate");
        }
        return executeCollect(new RepositoryScanRequest(
                repositoryId,
                null,
                fromDate,
                toDate,
                includeClosed,
                requestedBy,
                resolveTraceId()
        ));
    }

    public CollectorRunResult collectFromWebhook(UUID repositoryId, int pullRequestNumber, String deliveryId) {
        return executeCollect(new RepositoryScanRequest(
                repositoryId,
                pullRequestNumber,
                null,
                null,
                true,
                "github-webhook",
                deliveryId == null || deliveryId.isBlank() ? resolveTraceId() : deliveryId
        ));
    }

    public CollectorRunResult collectFromWebhook(UUID repositoryId, String deliveryId) {
        return executeCollect(new RepositoryScanRequest(
                repositoryId,
                null,
                null,
                null,
                true,
                "github-webhook",
                deliveryId == null || deliveryId.isBlank() ? resolveTraceId() : deliveryId
        ));
    }

    public CollectorRunResult collectManual(RepositoryScanRequest request, AppUser caller) {
        requireAdmin(caller);
        if (request.repositoryId() == null) {
            throw new IllegalArgumentException("repositoryId is required");
        }
        if (request.prNumber() != null && request.prNumber() <= 0) {
            throw new IllegalArgumentException("prNumber must be positive");
        }
        if (request.fromDate() != null && request.toDate() != null && request.fromDate().isAfter(request.toDate())) {
            throw new IllegalArgumentException("fromDate must be <= toDate");
        }
        return executeCollect(new RepositoryScanRequest(
                request.repositoryId(),
                request.prNumber(),
                request.fromDate(),
                request.toDate(),
                request.includeClosed(),
                resolveCaller(caller),
                request.traceId() == null || request.traceId().isBlank() ? resolveTraceId() : request.traceId()
        ));
    }

    private CollectorRunResult executeCollect(RepositoryScanRequest request) {
        ConnectorScope connector = persistence.ensureConnector(CONNECTOR_TYPE, CONNECTOR_NAME);
        CollectorRun run = persistence.insertRun(new CollectorRun(
                null,
                connector.connectorId(),
                OffsetDateTime.now(ZoneOffset.UTC),
                null,
                "RUNNING",
                0,
                0,
                0,
                null,
                request.traceId()
        ));

        int processedPr = 0;
        int processedCommit = 0;
        int processedFile = 0;
        int failureCount = 0;
        String errorMessage = null;
        String status = "SUCCESS";
        RepositoryScope repository = null;

        try {
            repository = requireActiveRepository(request.repositoryId());
            List<Integer> prNumbers = resolvePullRequestNumbers(request, repository);
            if (prNumbers.isEmpty()) {
                run = finishRun(run, repository, status, processedPr, processedCommit, processedFile, failureCount, null, request.traceId());
                return toResult(run, repository.repositoryId(), null, status, processedPr, processedCommit, processedFile, failureCount);
            }

            for (Integer prNumber : prNumbers) {
                try {
                    PullRequestGraph graph = githubPort.fetchPullRequest(repository.repoNameMasked(), prNumber);
                    CollectCounts counts = persistPullRequest(repository, graph, request.traceId());
                    processedPr += counts.pr();
                    processedCommit += counts.commit();
                    processedFile += counts.file();
                    if (evidenceQualityScoreService != null && counts.ticketId() != null) {
                        evidenceQualityScoreService.recalculateFromSourceChange(counts.ticketId(), null, request.traceId());
                    }
                } catch (RuntimeException ex) {
                    failureCount++;
                    status = status.equals("SUCCESS") && (processedPr > 0 || processedCommit > 0 || processedFile > 0)
                            ? "PARTIAL_SUCCESS"
                            : "FAILED";
                    errorMessage = safeError(ex);
                }
            }

            if (failureCount > 0 && (processedPr > 0 || processedCommit > 0 || processedFile > 0)) {
                status = "PARTIAL_SUCCESS";
            }
            if (failureCount > 0 && processedPr == 0 && processedCommit == 0 && processedFile == 0) {
                status = "FAILED";
            }
            run = finishRun(run, repository, status, processedPr, processedCommit, processedFile, failureCount, errorMessage, request.traceId());
            return toResult(run, repository.repositoryId(), request.prNumber(), status, processedPr, processedCommit, processedFile, failureCount);
        } catch (RuntimeException ex) {
            failureCount++;
            status = "FAILED";
            errorMessage = safeError(ex);
            run = finishRun(run, repository, status, processedPr, processedCommit, processedFile, failureCount, errorMessage, request.traceId());
            return toResult(run, repository != null ? repository.repositoryId() : request.repositoryId(), request.prNumber(), status, processedPr, processedCommit, processedFile, failureCount);
        }
    }

    private CollectCounts persistPullRequest(RepositoryScope repository, PullRequestGraph graph, String traceId) {
        String inferredTicketKey = inferTicketKey(graph);
        TicketResolution ticketResolution = resolveTicket(repository, graph);
        TicketScope ticket = ticketResolution.ticket();
        UUID ticketId = ticket != null ? ticket.ticketId() : null;
        String linkedTicketKey = ticket != null ? ticket.externalTicketKey() : null;
        OffsetDateTime collectedAt = OffsetDateTime.now(ZoneOffset.UTC);
        String labelsJson = toJson(graph.labels());
        String descriptionHash = hashNullable(graph.body());
        String authorPseudonym = pseudonym(graph.authorLogin());
        UUID authorMemberKey = resolveMemberKey(graph.authorLogin());
        String status = normalizePullRequestStatus(graph.state(), graph.merged());
        String reviewState = normalizeReviewState(graph.reviewState());
        OffsetDateTime lastCommitAt = graph.commits().stream()
                .map(CommitSnapshot::committedAt)
                .filter(java.util.Objects::nonNull)
                .max(OffsetDateTime::compareTo)
                .orElse(null);
        String upsertKey = linkedTicketKey != null ? linkedTicketKey : inferredTicketKey;
        if (upsertKey != null && !upsertKey.isBlank()) {
            ticket = persistence.upsertMinimalTicket(repository.projectId(), upsertKey, graph.title(), status, lastCommitAt);
            ticketId = ticket != null ? ticket.ticketId() : ticketId;
            linkedTicketKey = ticket != null ? ticket.externalTicketKey() : linkedTicketKey;
        }

        UUID prId = persistence.upsertPullRequest(new PullRequestUpsert(
                repository.repositoryId(),
                ticketId,
                graph.number(),
                String.valueOf(graph.number()),
                graph.title(),
                descriptionHash,
                status,
                graph.headRef(),
                graph.baseRef(),
                graph.createdAt(),
                graph.updatedAt(),
                graph.mergedAt(),
                graph.closedAt(),
                authorPseudonym,
                authorMemberKey,
                labelsJson,
                linkedTicketKey,
                graph.htmlUrl(),
                reviewState,
                collectedAt
        ));

        UUID traceabilityTicketId = ticketId != null
                ? ticketId
                : persistence.findTicketIdByPullRequestId(prId).orElse(null);

        if (traceabilityTicketId != null) {
            persistence.upsertTraceabilityLink(new TraceabilityLinkUpsert(
                    traceabilityTicketId,
                    "TICKET",
                    linkedTicketKey,
                    "PULL_REQUEST",
                    prKey(repository.repositoryId(), graph.number()),
                    "ticket-inference",
                    "{\"source\":\"collector\"}"
            ));
        }

        int commitCount = 0;
        int fileCount = 0;
        for (CommitSnapshot commit : graph.commits()) {
            UUID commitId = persistence.upsertCommit(new CommitUpsert(
                    repository.repositoryId(),
                    ticketId,
                    commit.sha(),
                    pseudonym(commit.authorLogin() != null && !commit.authorLogin().isBlank() ? commit.authorLogin() : commit.authorName()),
                    commit.committedAt(),
                    graph.headRef(),
                    hashNullable(commit.message()),
                    0,
                    0,
                    0,
                    commit.htmlUrl(),
                    collectedAt
            ));
            persistence.upsertPullRequestCommit(prId, commitId);
            persistTraceabilityLink(traceabilityTicketId,
                    "PULL_REQUEST",
                    prKey(repository.repositoryId(), graph.number()),
                    "COMMIT",
                    commit.sha(),
                    "pr-commit",
                    "{\"source\":\"collector\"}");
            commitCount++;
        }

        for (ChangedFileSnapshot file : graph.changedFiles()) {
            persistence.upsertPullRequestChangedFile(new GitPrMetadataCollectorModels.PullRequestChangedFileUpsert(
                    prId,
                    repository.repositoryId(),
                    file.filePath(),
                    hash(file.filePath()),
                    fileExtension(file.filePath()),
                    file.status(),
                    file.additions(),
                    file.deletions(),
                    collectedAt
            ));
            persistTraceabilityLink(traceabilityTicketId,
                    "PULL_REQUEST",
                    prKey(repository.repositoryId(), graph.number()),
                    "CHANGED_FILE",
                    hash(file.filePath()),
                    "pr-file",
                    "{\"source\":\"collector\"}");
            fileCount++;
        }

        persistence.deleteReviewsByPrId(prId);

        int reviewCount = 0;
        java.util.Map<String, UUID> externalReviewIdToDbId = new java.util.HashMap<>();
        for (ReviewSnapshot review : graph.reviews()) {
            long commentCount = graph.reviewComments().stream()
                    .filter(c -> review.externalReviewId().equals(c.externalReviewId()))
                    .count();
            UUID reviewId = persistence.insertReview(new ReviewUpsert(
                    prId,
                    ticketId,
                    normalizeReviewState(review.state()),
                    review.submittedAt(),
                    (int) commentCount,
                    collectedAt
            ));
            externalReviewIdToDbId.put(review.externalReviewId(), reviewId);
            reviewCount++;
        }

        for (ReviewCommentSnapshot comment : graph.reviewComments()) {
            UUID reviewId = comment.externalReviewId() != null
                    ? externalReviewIdToDbId.get(comment.externalReviewId())
                    : null;
            persistence.insertReviewComment(new ReviewCommentUpsert(
                    reviewId,
                    prId,
                    ticketId,
                    hashNullable(comment.body()),
                    comment.filePath() != null && !comment.filePath().isBlank() ? hash(comment.filePath()) : null,
                    comment.line() != null && comment.line() > 0 ? comment.line() : null,
                    collectedAt
            ));
        }

        return new CollectCounts(1, commitCount, fileCount, reviewCount, ticketId);
    }

    private TicketResolution resolveTicket(RepositoryScope repository, PullRequestGraph graph) {
        List<String> candidates = inferTicketKeys(graph);
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            TicketScope ticket = persistence.findTicketByProjectIdAndExternalKey(repository.projectId(), candidate)
                    .orElse(null);
            if (ticket != null) {
                return new TicketResolution(ticket, ticket.externalTicketKey());
            }
        }
        return new TicketResolution(null, null);
    }

    private void persistTraceabilityLink(
            UUID ticketId,
            String sourceType,
            String sourceId,
            String targetType,
            String targetId,
            String ruleName,
            String evidenceJson
    ) {
        persistence.upsertTraceabilityLink(new TraceabilityLinkUpsert(
                ticketId,
                sourceType,
                sourceId,
                targetType,
                targetId,
                ruleName,
                evidenceJson
        ));
    }

    private List<Integer> resolvePullRequestNumbers(RepositoryScanRequest request, RepositoryScope repository) {
        if (request.prNumber() != null) {
            return List.of(request.prNumber());
        }
        List<PullRequestSummary> summaries = githubPort.listPullRequests(repository.repoNameMasked(), request.includeClosed());
        List<Integer> prNumbers = new ArrayList<>();
        for (PullRequestSummary summary : summaries) {
            if (!request.includeClosed() && !"open".equalsIgnoreCase(summary.state())) {
                continue;
            }
            if (!withinDateRange(summary.updatedAt(), request.fromDate(), request.toDate())) {
                continue;
            }
            prNumbers.add(summary.number());
        }
        return prNumbers.stream().distinct().sorted(Comparator.naturalOrder()).toList();
    }

    private boolean withinDateRange(OffsetDateTime updatedAt, LocalDate fromDate, LocalDate toDate) {
        if (updatedAt == null) {
            return true;
        }
        LocalDate updatedDate = updatedAt.atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
        if (fromDate != null && updatedDate.isBefore(fromDate)) {
            return false;
        }
        if (toDate != null && updatedDate.isAfter(toDate)) {
            return false;
        }
        return true;
    }

    private RepositoryScope requireActiveRepository(UUID repositoryId) {
        return persistence.findActiveRepository(repositoryId)
                .orElseThrow(() -> new NotFoundException("REPOSITORY_NOT_FOUND"));
    }

    private void requireAdmin(AppUser caller) {
        if (caller == null || caller.getRole() != AppUser.Role.ADMIN) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }

    private String resolveCaller(AppUser caller) {
        if (caller == null) {
            return "SYSTEM";
        }
        if (caller.getEmail() != null && !caller.getEmail().isBlank()) {
            return caller.getEmail().trim();
        }
        if (caller.getDisplayName() != null && !caller.getDisplayName().isBlank()) {
            return caller.getDisplayName().trim();
        }
        return "SYSTEM";
    }

    private CollectorRun finishRun(CollectorRun run,
                                   RepositoryScope repository,
                                   String status,
                                   int processedPr,
                                   int processedCommit,
                                   int processedFile,
                                   int failureCount,
                                   String errorMessage,
                                   String traceId) {
        CollectorRun finished = new CollectorRun(
                run.connectorRunId(),
                run.connectorId(),
                run.startedAt(),
                OffsetDateTime.now(ZoneOffset.UTC),
                status,
                processedPr,
                processedCommit + processedFile,
                failureCount,
                errorMessage,
                traceId
        );
        persistence.updateRun(finished);
        return finished;
    }

    private CollectorRunResult toResult(CollectorRun run,
                                        UUID repositoryId,
                                        Integer prNumber,
                                        String status,
                                        int processedPr,
                                        int processedCommit,
                                        int processedFile,
                                        int failureCount) {
        return new CollectorRunResult(
                run.connectorRunId(),
                repositoryId,
                prNumber,
                status,
                processedPr,
                processedCommit,
                processedFile,
                failureCount,
                run.traceId()
        );
    }

    private String inferTicketKey(PullRequestGraph graph) {
        for (ChangedFileSnapshot file : graph.changedFiles()) {
            String fromPath = extractTicketKeyFromPath(file.filePath());
            if (fromPath != null) {
                return fromPath;
            }
        }
        String fromBranch = extractTicketKey(graph.headRef());
        if (fromBranch != null) {
            return fromBranch;
        }
        String fromTitle = extractTicketKey(graph.title());
        if (fromTitle != null) {
            return fromTitle;
        }
        return null;
    }

    private List<String> inferTicketKeys(PullRequestGraph graph) {
        List<String> candidates = new ArrayList<>();
        addTicketKeyCandidates(candidates, extractTicketKeys(graph.body()));
        addTicketKeyCandidates(candidates, extractTicketKeys(graph.headRef()));
        addTicketKeyCandidates(candidates, extractTicketKeys(graph.title()));
        for (CommitSnapshot commit : graph.commits()) {
            addTicketKeyCandidates(candidates, extractTicketKeys(commit.message()));
        }
        for (ChangedFileSnapshot file : graph.changedFiles()) {
            addTicketKeyCandidate(candidates, extractTicketKeyFromPath(file.filePath()));
        }
        return candidates;
    }

    private void addTicketKeyCandidates(List<String> candidates, List<String> extractedCandidates) {
        if (extractedCandidates == null || extractedCandidates.isEmpty()) {
            return;
        }
        for (String candidate : extractedCandidates) {
            addTicketKeyCandidate(candidates, candidate);
        }
    }

    private void addTicketKeyCandidate(List<String> candidates, String candidate) {
        if (candidate == null || candidate.isBlank() || candidates.contains(candidate)) {
            return;
        }
        candidates.add(candidate);
    }

    private String extractTicketKey(String text) {
        List<String> candidates = extractTicketKeys(text);
        return candidates.isEmpty() ? null : candidates.getFirst();
    }

    private List<String> extractTicketKeys(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] tokens = text.toUpperCase(Locale.ROOT).split("[^A-Z0-9-]+");
        List<String> candidates = new ArrayList<>();
        for (String token : tokens) {
            String candidate = normalizeTicketCandidate(token);
            if (candidate != null) {
                addTicketKeyCandidate(candidates, candidate);
            }
        }
        return candidates;
    }

    private String normalizeTicketCandidate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String normalized = token.trim().toUpperCase(Locale.ROOT);
        if (TICKET_STOP_WORDS.contains(normalized)) {
            return null;
        }
        Matcher matcher = TICKET_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return null;
        }
        String candidate = matcher.group(1);
        if (TICKET_STOP_WORDS.contains(candidate)) {
            return null;
        }
        if (candidate.contains("-")) {
            String[] parts = candidate.split("-");
            for (String part : parts) {
                if (TICKET_STOP_WORDS.contains(part)) {
                    return null;
                }
            }
            if (parts.length >= 2 && containsDigit(parts[1])) {
                return parts[0] + "-" + parts[1];
            }
            return candidate;
        }
        return candidate;
    }

    private boolean containsDigit(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (char c : value.toCharArray()) {
            if (Character.isDigit(c)) {
                return true;
            }
        }
        return false;
    }


    private String extractTicketKeyFromPath(String path) {
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
        String ticket = path.substring(start, slashIndex).trim();
        return ticket.isBlank() ? null : ticket;
    }

    private record TicketResolution(TicketScope ticket, String linkedTicketKey) {
    }

    private String normalizePullRequestStatus(String state, boolean merged) {
        if (merged) {
            return "MERGED";
        }
        if (state == null || state.isBlank()) {
            return "UNKNOWN";
        }
        String normalized = state.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "OPEN" -> "OPEN";
            case "CLOSED" -> "CLOSED";
            case "MERGED" -> "MERGED";
            default -> "UNKNOWN";
        };
    }

    private String normalizeReviewState(String reviewState) {
        if (reviewState == null || reviewState.isBlank()) {
            return "UNKNOWN";
        }
        String normalized = reviewState.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "APPROVED", "CHANGES_REQUESTED", "REVIEW_REQUIRED", "UNKNOWN" -> normalized;
            case "REQUESTED", "COMMENTED", "DISMISSED", "PENDING" -> "REVIEW_REQUIRED";
            default -> "UNKNOWN";
        };
    }

    private String fileExtension(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        int slash = filePath.lastIndexOf('/');
        String name = slash >= 0 ? filePath.substring(slash + 1) : filePath;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String prKey(UUID repositoryId, int pullRequestNumber) {
        return repositoryId + "#PR-" + pullRequestNumber;
    }

    private String hashNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return hash(value);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash value", ex);
        }
    }

    private String pseudonym(String value) {
        String normalized = value == null || value.isBlank() ? "unknown" : value.trim();
        return "p_" + hash(normalized).substring(0, 16);
    }

    private UUID resolveMemberKey(String authorLogin) {
        if (authorLogin == null || authorLogin.isBlank()) {
            return null;
        }
        return persistence.findMemberKeyByExternalUserHash(hash(authorLogin.trim().toLowerCase(Locale.ROOT))).orElse(null);
    }

    private String toJson(List<String> labels) {
        try {
            return objectMapper.writeValueAsString(labels == null ? List.of() : labels);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize labels", ex);
        }
    }

    private String safeError(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message.length() > 120 ? message.substring(0, 120) : message;
    }

    private String resolveTraceId() {
        String traceId = MDC.get("traceId");
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }

    private record CollectCounts(int pr, int commit, int file, int review, UUID ticketId) {
    }
}
