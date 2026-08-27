package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class PmDashboardDtos {

    private PmDashboardDtos() {
    }

    public record PmDashboardOptionDto(
            String value,
            String label
    ) {
        public static PmDashboardOptionDto from(PmDashboardModels.DashboardOption option) {
            return new PmDashboardOptionDto(option.value(), option.label());
        }
    }

    public record PmDashboardPhaseOptionDto(
            String value,
            String label,
            int order
    ) {
        public static PmDashboardPhaseOptionDto from(PmDashboardModels.DashboardPhaseOption option) {
            return new PmDashboardPhaseOptionDto(option.value(), option.label(), option.order());
        }
    }

    public record PmDashboardSummaryDto(
            long blockedTicketCount,
            long missingEvidenceTicketCount,
            long missingTraceabilitySectionTicketCount,
            long openIssueCount,
            long waitingReviewTicketCount,
            long ciFailedTicketCount,
            long riskTicketCount,
            long exceptionTicketCount,
            BigDecimal averageEvidenceQualityScore,
            String averageScoreBand,
            String phaseBottleneckPhaseCode,
            String phaseBottleneckPhaseName,
            long phaseBottleneckBlockedCount,
            OffsetDateTime updatedAt
    ) {
        public static PmDashboardSummaryDto from(PmDashboardModels.DashboardSummary summary) {
            return new PmDashboardSummaryDto(
                    summary.blockedTicketCount(),
                    summary.missingEvidenceTicketCount(),
                    summary.missingTraceabilitySectionTicketCount(),
                    summary.openIssueCount(),
                    summary.waitingReviewTicketCount(),
                    summary.ciFailedTicketCount(),
                    summary.riskTicketCount(),
                    summary.exceptionTicketCount(),
                    summary.averageEvidenceQualityScore(),
                    summary.averageScoreBand(),
                    summary.phaseBottleneckPhaseCode(),
                    summary.phaseBottleneckPhaseName(),
                    summary.phaseBottleneckBlockedCount(),
                    summary.updatedAt()
            );
        }
    }

    public record PmDashboardEvidenceBottleneckBucketDto(
            String bucketKey,
            String bucketName,
            long missingEvidenceCount
    ) {
        public static PmDashboardEvidenceBottleneckBucketDto from(PmDashboardModels.DashboardEvidenceBottleneckBucket bucket) {
            return new PmDashboardEvidenceBottleneckBucketDto(
                    bucket.bucketKey(),
                    bucket.bucketName(),
                    bucket.missingEvidenceCount()
            );
        }
    }

    public record PmDashboardTicketRowDto(
            UUID ticketId,
            UUID projectId,
            String projectAlias,
            UUID repositoryId,
            String repositoryName,
            String externalTicketKey,
            String title,
            UUID phaseId,
            String phaseCode,
            String phaseName,
            int phaseOrder,
            boolean blockedFlag,
            boolean waitingReviewFlag,
            int missingEvidenceCount,
            int traceabilityIssueCount,
            int openIssueCount,
            int riskCount,
            int exceptionCount,
            int ciFailedCount,
            String highestRiskSeverity,
            BigDecimal evidenceQualityScore,
            String scoreBand,
            String scoreRuleVersion,
            int ageDays,
            String ownerDisplay,
            String periodKey,
            OffsetDateTime updatedAt,
            OffsetDateTime refreshedAt
    ) {
        public static PmDashboardTicketRowDto from(PmDashboardModels.DashboardTicketRow row) {
            return new PmDashboardTicketRowDto(
                    row.ticketId(),
                    row.projectId(),
                    row.projectAlias(),
                    row.repositoryId(),
                    row.repositoryName(),
                    row.externalTicketKey(),
                    row.title(),
                    row.phaseId(),
                    row.phaseCode(),
                    row.phaseName(),
                    row.phaseOrder(),
                    row.blockedFlag(),
                    row.waitingReviewFlag(),
                    row.missingEvidenceCount(),
                    row.traceabilityIssueCount(),
                    row.openIssueCount(),
                    row.riskCount(),
                    row.exceptionCount(),
                    row.ciFailedCount(),
                    row.highestRiskSeverity(),
                    row.evidenceQualityScore(),
                    row.scoreBand(),
                    row.scoreRuleVersion(),
                    row.ageDays(),
                    row.ownerDisplay(),
                    row.periodKey(),
                    row.updatedAt(),
                    row.refreshedAt()
            );
        }
    }

    public record PmDashboardPageDto(
            List<PmDashboardTicketRowDto> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
        public static PmDashboardPageDto from(PageResult<PmDashboardModels.DashboardTicketRow> result) {
            return new PmDashboardPageDto(
                    result.items().stream().map(PmDashboardTicketRowDto::from).toList(),
                    result.page(),
                    result.size(),
                    result.totalElements(),
                    result.totalPages(),
                    result.page() < result.totalPages()
            );
        }
    }

    public record PmDashboardInsightsDto(
            List<PmDashboardTicketRowDto> attentionItems,
            List<PmDashboardEvidenceBottleneckBucketDto> evidenceBottleneckBuckets
    ) {
        public static PmDashboardInsightsDto from(PmDashboardModels.DashboardInsights insights) {
            return new PmDashboardInsightsDto(
                    insights.attentionItems().stream().map(PmDashboardTicketRowDto::from).toList(),
                    insights.evidenceBottleneckBuckets().stream().map(PmDashboardEvidenceBottleneckBucketDto::from).toList()
            );
        }
    }

    public record MissingEvidenceItemDto(
            String artifactTypeCode,
            String artifactName,
            String defaultFileName,
            String sourcePath,
            boolean requiredFlag,
            boolean existsFlag
    ) {
        public static MissingEvidenceItemDto from(PmDashboardModels.MissingEvidenceItem item) {
            return new MissingEvidenceItemDto(
                    item.artifactTypeCode(),
                    item.artifactName(),
                    item.defaultFileName(),
                    item.sourcePath(),
                    item.requiredFlag(),
                    item.existsFlag()
            );
        }
    }

    public record RiskItemDto(
            UUID riskId,
            String riskKey,
            String riskSummary,
            String severity,
            String status,
            boolean mitigationPresent,
            String mitigationSummary
    ) {
        public static RiskItemDto from(PmDashboardModels.RiskItem item) {
            return new RiskItemDto(
                    item.riskId(),
                    item.riskKey(),
                    item.riskSummary(),
                    item.severity(),
                    item.status(),
                    item.mitigationPresent(),
                    item.mitigationSummary()
            );
        }
    }

    public record ExceptionItemDto(
            UUID exceptionId,
            String exceptionType,
            String reason,
            String followUpStatus,
            boolean approved,
            String linkedReportPath
    ) {
        public static ExceptionItemDto from(PmDashboardModels.ExceptionItem item) {
            return new ExceptionItemDto(
                    item.exceptionId(),
                    item.exceptionType(),
                    item.reason(),
                    item.followUpStatus(),
                    item.approved(),
                    item.linkedReportPath()
            );
        }
    }

    public record ScoreBreakdownDto(
            BigDecimal specScore,
            BigDecimal planScore,
            BigDecimal reviewScore,
            BigDecimal selfReviewScore,
            BigDecimal testScore,
            BigDecimal ciScore,
            BigDecimal blackboxScore,
            BigDecimal reportScore
    ) {
        public static ScoreBreakdownDto from(PmDashboardModels.ScoreBreakdown item) {
            return new ScoreBreakdownDto(
                    item.specScore(),
                    item.planScore(),
                    item.reviewScore(),
                    item.selfReviewScore(),
                    item.testScore(),
                    item.ciScore(),
                    item.blackboxScore(),
                    item.reportScore()
            );
        }
    }

    public record PmDashboardTicketDetailDto(
            PmDashboardTicketRowDto row,
            List<MissingEvidenceItemDto> missingEvidenceItems,
            List<RiskItemDto> riskItems,
            List<ExceptionItemDto> exceptionItems,
            ScoreBreakdownDto scoreBreakdown,
            String traceabilityUrl
    ) {
        public static PmDashboardTicketDetailDto from(PmDashboardModels.DashboardTicketDetail detail) {
            return new PmDashboardTicketDetailDto(
                    PmDashboardTicketRowDto.from(detail.row()),
                    detail.missingEvidenceItems().stream().map(MissingEvidenceItemDto::from).toList(),
                    detail.riskItems().stream().map(RiskItemDto::from).toList(),
                    detail.exceptionItems().stream().map(ExceptionItemDto::from).toList(),
                    ScoreBreakdownDto.from(detail.scoreBreakdown()),
                    detail.traceabilityUrl()
            );
        }
    }

    public record PmDashboardRefreshDto(
            long refreshedRows,
            OffsetDateTime refreshedAt
    ) {
        public static PmDashboardRefreshDto from(PmDashboardModels.DashboardRefreshResult result) {
            return new PmDashboardRefreshDto(result.refreshedRows(), result.refreshedAt());
        }
    }

    public record PmDashboardOptionsDto(
            List<PmDashboardOptionDto> projects,
            List<PmDashboardOptionDto> periods,
            List<PmDashboardOptionDto> repositories,
            List<PmDashboardPhaseOptionDto> phases
    ) {
        public static PmDashboardOptionsDto from(PmDashboardModels.DashboardOptions options) {
            return new PmDashboardOptionsDto(
                    options.projects().stream().map(PmDashboardOptionDto::from).toList(),
                    options.periods().stream().map(PmDashboardOptionDto::from).toList(),
                    options.repositories().stream().map(PmDashboardOptionDto::from).toList(),
                    options.phases().stream().map(PmDashboardPhaseOptionDto::from).toList()
            );
        }
    }
}
