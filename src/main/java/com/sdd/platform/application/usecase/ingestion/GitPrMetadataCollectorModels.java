package com.sdd.platform.application.usecase.ingestion;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class GitPrMetadataCollectorModels {

    private GitPrMetadataCollectorModels() {
    }

    public enum TriggerSource {
        MANUAL,
        WEBHOOK
    }

    public record CollectorRun(
            UUID connectorRunId,
            UUID connectorId,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            String status,
            int recordsRead,
            int recordsWritten,
            int failureCount,
            String errorMessage,
            String traceId
    ) {
    }

    public record CollectorRunResult(
            UUID runId,
            UUID repositoryId,
            Integer prNumber,
            String status,
            int processedPrCount,
            int processedCommitCount,
            int processedChangedFileCount,
            int failureCount,
            String traceId
    ) {
    }

    public record RepositoryScanRequest(
            UUID repositoryId,
            Integer prNumber,
            LocalDate fromDate,
            LocalDate toDate,
            boolean includeClosed,
            String requestedBy,
            String traceId
    ) {
    }

    public record PullRequestSummary(
            int number,
            String state,
            boolean merged,
            OffsetDateTime updatedAt,
            OffsetDateTime mergedAt,
            OffsetDateTime closedAt
    ) {
    }

    public record PullRequestGraph(
            int number,
            String title,
            String body,
            String htmlUrl,
            String state,
            boolean merged,
            String headRef,
            String headSha,
            String baseRef,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime mergedAt,
            OffsetDateTime closedAt,
            String authorLogin,
            List<String> labels,
            String reviewState,
            List<CommitSnapshot> commits,
            List<ChangedFileSnapshot> changedFiles,
            List<ReviewSnapshot> reviews,
            List<ReviewCommentSnapshot> reviewComments
    ) {
    }

    public record ReviewSnapshot(
            String externalReviewId,
            String reviewerLogin,
            String state,
            String body,
            String filePath,
            OffsetDateTime submittedAt,
            String submittedBy
    ) {
    }

    public record ReviewCommentSnapshot(
            String externalCommentId,
            String externalReviewId,
            String commenterLogin,
            String body,
            String filePath,
            Integer line,
            OffsetDateTime createdAt
    ) {
    }

    public record ReviewUpsert(
            UUID prId,
            UUID ticketId,
            UUID reviewerMemberKey,
            String state,
            OffsetDateTime submittedAt,
            String submittedBy,
            int commentCount,
            OffsetDateTime collectedAt
    ) {
    }

    public record ReviewCommentUpsert(
            UUID reviewId,
            UUID prId,
            UUID ticketId,
            String commentHash,
            String filePathHash,
            Integer lineNumber,
            OffsetDateTime collectedAt
    ) {
    }

    public record CommitSnapshot(
            String sha,
            String message,
            String authorLogin,
            String authorName,
            OffsetDateTime committedAt,
            String htmlUrl
    ) {
    }

    public record ChangedFileSnapshot(
            String filePath,
            String status,
            int additions,
            int deletions,
            String sha
    ) {
    }

    public record PullRequestUpsert(
            UUID repositoryId,
            UUID ticketId,
            int externalPrNumber,
            String externalPrId,
            String title,
            String descriptionHash,
            String status,
            String sourceBranch,
            String targetBranch,
            OffsetDateTime openedAt,
            OffsetDateTime updatedAt,
            OffsetDateTime mergedAt,
            OffsetDateTime closedAt,
            String authorDisplayName,
            UUID authorMemberKey,
            String labelsJson,
            String linkedIssueKey,
            String externalPrUrl,
            String reviewState,
            OffsetDateTime collectedAt
    ) {
    }

    public record CommitUpsert(
            UUID repositoryId,
            UUID ticketId,
            String commitHash,
            String authorPseudonym,
            OffsetDateTime committedAt,
            String branchName,
            String messageHash,
            int changedFileCount,
            int addedLines,
            int deletedLines,
            String commitUrl,
            OffsetDateTime collectedAt
    ) {
    }

    public record PullRequestChangedFileUpsert(
            UUID prId,
            UUID repositoryId,
            String filePath,
            String filePathHash,
            String fileExtension,
            String changeType,
            int additions,
            int deletions,
            OffsetDateTime collectedAt
    ) {
    }

    public record TraceabilityLinkUpsert(
            UUID ticketId,
            String sourceType,
            String sourceId,
            String targetType,
            String targetId,
            String ruleName,
            String evidenceJson
    ) {
    }
}
