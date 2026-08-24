package com.sdd.platform.application.usecase.traceability;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class TraceabilityModels {

    private TraceabilityModels() {
    }

    public static final List<String> REQUIRED_ARTIFACT_CODES = List.of(
            "SPEC_PACK",
            "IMPL_PLAN",
            "REVIEW_CHECKLIST",
            "SELF_REVIEW",
            "TEST_PLAN",
            "TEST_RESULTS",
            "REPORT"
    );

    public record TicketRow(
            UUID ticketId,
            UUID projectId,
            String externalTicketKey,
            String title,
            String status
    ) {
    }

    public record ArtifactCoverageRow(
            UUID artifactSnapshotId,
            String artifactTypeCode,
            String artifactName,
            String defaultFileName,
            boolean requiredFlag,
            String sourcePath,
            boolean existsFlag,
            OffsetDateTime collectedAt,
            Integer schemaVersion
    ) {
    }

    public record PullRequestCoverageRow(
            UUID prId,
            String externalPrId,
            String externalPrUrl,
            String title,
            String status,
            String sourceBranch,
            String targetBranch,
            OffsetDateTime openedAt,
            OffsetDateTime mergedAt,
            OffsetDateTime closedAt,
            OffsetDateTime collectedAt
    ) {
    }

    public record CommitCoverageRow(
            UUID commitId,
            String commitHash,
            String branchName,
            String messageHash,
            String commitUrl,
            OffsetDateTime committedAt,
            OffsetDateTime collectedAt
    ) {
    }

    public record CiRunCoverageRow(
            UUID ciRunId,
            String externalCiRunId,
            String ciUrl,
            String workflowName,
            String status,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            OffsetDateTime collectedAt
    ) {
    }

    public record TraceabilityLinkRow(
            UUID traceabilityLinkId,
            UUID ticketId,
            String sourceType,
            String sourceId,
            String targetType,
            String targetId,
            Double confidence,
            String confidenceLevel,
            String ruleName,
            String evidenceJson,
            OffsetDateTime createdAt
    ) {
    }

    public record EvidenceEventRow(
            UUID evidenceEventId,
            String eventType,
            String sourceType,
            String sourceRefId,
            String result,
            String summary,
            OffsetDateTime eventTimestamp
    ) {
    }

    public record ParsedSectionRow(
            UUID parsedSectionId,
            UUID artifactSnapshotId,
            String artifactTypeCode,
            String artifactName,
            String sectionKey,
            String sectionSummary,
            boolean requiredFlag,
            boolean presentFlag,
            Boolean validFlag,
            String parseWarning,
            OffsetDateTime collectedAt
    ) {
    }

    public record ArtifactCoverage(
            UUID artifactSnapshotId,
            String artifactTypeCode,
            String artifactName,
            String defaultFileName,
            boolean requiredFlag,
            String sourcePath,
            boolean existsFlag,
            OffsetDateTime collectedAt,
            Integer schemaVersion
    ) {
    }

    public record PullRequestCoverage(
            UUID prId,
            String externalPrId,
            String externalPrUrl,
            String title,
            String status,
            String sourceBranch,
            String targetBranch,
            OffsetDateTime openedAt,
            OffsetDateTime mergedAt,
            OffsetDateTime closedAt,
            OffsetDateTime collectedAt
    ) {
    }

    public record CommitCoverage(
            UUID commitId,
            String commitHash,
            String branchName,
            String messageHash,
            String commitUrl,
            OffsetDateTime committedAt,
            OffsetDateTime collectedAt
    ) {
    }

    public record CiRunCoverage(
            UUID ciRunId,
            String externalCiRunId,
            String ciUrl,
            String workflowName,
            String status,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            OffsetDateTime collectedAt
    ) {
    }

    public record TraceabilityLink(
            UUID traceabilityLinkId,
            UUID ticketId,
            String sourceType,
            String sourceId,
            String targetType,
            String targetId,
            double confidence,
            String confidenceLevel,
            String ruleName,
            String evidenceJson,
            OffsetDateTime createdAt
    ) {
    }

    public record TimelineEvent(
            UUID eventId,
            String eventType,
            String sourceType,
            String sourceRefId,
            String result,
            String summary,
            OffsetDateTime eventTimestamp
    ) {
    }

    public record BrokenLink(
            String code,
            String item,
            String severity,
            String message
    ) {
    }

    public record Summary(
            UUID ticketId,
            String externalTicketKey,
            String title,
            int completenessPercent,
            int foundCount,
            int expectedCount,
            int artifactCount,
            int prCount,
            int commitCount,
            int ciCount,
            int brokenLinkCount,
            int reviewRoundCount
    ) {
    }

    public record TraceabilityView(
            Summary summary,
            List<ArtifactCoverage> artifacts,
            List<PullRequestCoverage> pullRequests,
            List<CommitCoverage> commits,
            List<CiRunCoverage> ciRuns,
            List<TraceabilityLink> links,
            List<BrokenLink> brokenLinks,
            List<TimelineEvent> timelineEvents,
            List<TraceabilityReviewCommentRow> reviewComments
    ) {
    }

    public record TraceabilityReviewCommentRow(
            UUID reviewCommentId,
            String filePathHash,
            Integer lineNumber,
            String commentSummary,
            String state,
            String submittedAt,
            String submittedBy
    ) {
    }
}
