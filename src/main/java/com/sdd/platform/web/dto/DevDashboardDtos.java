package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.devdashboard.DevDashboardModels;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class DevDashboardDtos {

    private DevDashboardDtos() {
    }

    public record DevDashboardOptionDto(
            String value,
            String label,
            String role
    ) {
        public static DevDashboardOptionDto from(DevDashboardModels.DevDashboardOption option) {
            return new DevDashboardOptionDto(option.value(), option.label(), option.role());
        }
    }

    public record DevDashboardOptionsDto(
            List<DevDashboardOptionDto> projects,
            List<DevDashboardOptionDto> repositories
    ) {
        public static DevDashboardOptionsDto from(DevDashboardModels.DevDashboardOptions options) {
            return new DevDashboardOptionsDto(
                    options.projects().stream().map(DevDashboardOptionDto::from).toList(),
                    options.repositories().stream().map(DevDashboardOptionDto::from).toList()
            );
        }
    }

    public record DevDashboardSummaryDto(
            long ciFailureCount,
            long reviewCommentCount,
            long parserErrorCount,
            OffsetDateTime updatedAt
    ) {
        public static DevDashboardSummaryDto from(DevDashboardModels.DevDashboardSummary summary) {
            return new DevDashboardSummaryDto(
                    summary.ciFailureCount(),
                    summary.reviewCommentCount(),
                    summary.parserErrorCount(),
                    summary.updatedAt()
            );
        }
    }

    public record DevTicketRowDto(
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
        public static DevTicketRowDto from(DevDashboardModels.DevTicketRow row) {
            return new DevTicketRowDto(
                    row.ticketId(),
                    row.projectId(),
                    row.projectAlias(),
                    row.externalTicketKey(),
                    row.title(),
                    row.ticketStatus(),
                    row.ciFailCount(),
                    row.latestCiStatus(),
                    row.openFindingCount(),
                    row.reviewRoundCount(),
                    row.reviewCommentCount(),
                    row.parserErrorFlag(),
                    row.ageDays(),
                    row.artifactVersion()
            );
        }
    }

    public record DevDashboardPageDto(
            List<DevTicketRowDto> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
        public static DevDashboardPageDto from(DevDashboardModels.DevDashboardPage page) {
            return new DevDashboardPageDto(
                    page.items().stream().map(DevTicketRowDto::from).toList(),
                    page.page(),
                    page.size(),
                    page.totalElements(),
                    page.totalPages(),
                    page.page() < page.totalPages()
            );
        }
    }

    public record DevCiRunItemDto(
            UUID ciRunId,
            String workflowName,
            String status,
            String failureCategory,
            String ciUrl,
            OffsetDateTime startedAt
    ) {
        public static DevCiRunItemDto from(DevDashboardModels.DevCiRunItem item) {
            return new DevCiRunItemDto(
                    item.ciRunId(),
                    item.workflowName(),
                    item.status(),
                    item.failureCategory(),
                    item.ciUrl(),
                    item.startedAt()
            );
        }
    }

    public record DevFindingItemDto(
            UUID findingId,
            String severity,
            String status,
            String findingSummary
    ) {
        public static DevFindingItemDto from(DevDashboardModels.DevFindingItem item) {
            return new DevFindingItemDto(
                    item.findingId(),
                    item.severity(),
                    item.status(),
                    item.findingSummary()
            );
        }
    }

    public record DevReviewCommentItemDto(
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
        public static DevReviewCommentItemDto from(DevDashboardModels.DevReviewCommentItem item) {
            return new DevReviewCommentItemDto(
                    item.reviewCommentId(),
                    item.filePathHash(),
                    item.lineNumber(),
                    item.severity(),
                    item.commentSummary(),
                    item.resolvedFlag(),
                    item.commitUrl(),
                    item.state(),
                    item.submittedAt(),
                    item.submittedBy()
            );
        }
    }

    public record DevParserSummaryDto(
            long parseErrorCount,
            long schemaViolationCount,
            long missingCount
    ) {
        public static DevParserSummaryDto from(DevDashboardModels.DevParserSummary summary) {
            return new DevParserSummaryDto(
                    summary.parseErrorCount(),
                    summary.schemaViolationCount(),
                    summary.missingCount()
            );
        }
    }

    public record DevTicketDetailDto(
            DevTicketRowDto row,
            List<DevCiRunItemDto> ciRuns,
            List<DevFindingItemDto> findings,
            List<DevReviewCommentItemDto> reviewComments,
            DevParserSummaryDto parserSummary
    ) {
        public static DevTicketDetailDto from(DevDashboardModels.DevTicketDetail detail) {
            return new DevTicketDetailDto(
                    DevTicketRowDto.from(detail.row()),
                    detail.ciRuns().stream().map(DevCiRunItemDto::from).toList(),
                    detail.findings().stream().map(DevFindingItemDto::from).toList(),
                    detail.reviewComments().stream().map(DevReviewCommentItemDto::from).toList(),
                    DevParserSummaryDto.from(detail.parserSummary())
            );
        }
    }
}
