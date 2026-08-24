package com.sdd.platform.application.usecase.devdashboard;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class DevDashboardModels {

    private DevDashboardModels() {
    }

    public record DevDashboardFilter(
            UUID projectId,
            UUID repositoryId,
            String ciStatus,
            String reviewStatus,
            String parserStatus,
            String search,
            int page,
            int size
    ) {
    }

    public record DevDashboardOption(
            String value,
            String label,
            String role
    ) {
    }

    public record DevDashboardOptions(
            List<DevDashboardOption> projects,
            List<DevDashboardOption> repositories
    ) {
    }

    public record DevDashboardSummary(
            long ciFailureCount,
            long reviewCommentCount,
            long parserErrorCount,
            OffsetDateTime updatedAt
    ) {
    }

    public record DevTicketRow(
            UUID ticketId,
            UUID projectId,
            String projectAlias,
            String externalTicketKey,
            String title,
            String ticketStatus,
            long ciFailCount,
            String latestCiStatus,
            long openFindingCount,
            int reviewRoundCount,
            long reviewCommentCount,
            boolean parserErrorFlag,
            int ageDays,
            Integer artifactVersion
    ) {
    }

    public record DevCiRunItem(
            UUID ciRunId,
            String workflowName,
            String status,
            String failureCategory,
            String ciUrl,
            OffsetDateTime startedAt
    ) {
    }

    public record DevFindingItem(
            UUID findingId,
            String severity,
            String status,
            String findingSummary
    ) {
    }

    public record DevReviewCommentItem(
            UUID reviewCommentId,
            String filePathHash,
            Integer lineNumber,
            String severity,
            String commentSummary,
            Boolean resolvedFlag,
            String commitUrl,
            String state,
            String submittedAt,
            String submittedBy
    ) {
    }

    public record DevParserSummary(
            long parseErrorCount,
            long schemaViolationCount,
            long missingCount
    ) {
    }

    public record DevTicketDetail(
            DevTicketRow row,
            List<DevCiRunItem> ciRuns,
            List<DevFindingItem> findings,
            List<DevReviewCommentItem> reviewComments,
            DevParserSummary parserSummary
    ) {
    }

    public record DevDashboardPage(
            List<DevTicketRow> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}
