package com.sdd.platform.application.usecase.pmdashboard;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class PmDashboardModels {

    private PmDashboardModels() {
    }

    public record DashboardFilter(
            UUID projectId,
            String periodKey,
            UUID repositoryId,
            String phaseCode,
            String scoreBand,
            String riskLevel,
            String search,
            int page,
            int size
    ) {
    }

    public record DashboardOption(
            String value,
            String label
    ) {
    }

    public record DashboardPhaseOption(
            String value,
            String label,
            int order
    ) {
    }

    public record DashboardOptions(
            List<DashboardOption> projects,
            List<DashboardOption> periods,
            List<DashboardOption> repositories,
            List<DashboardPhaseOption> phases
    ) {
    }

    public record DashboardSummary(
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
    }

    public record DashboardEvidenceBottleneckBucket(
            String bucketKey,
            String bucketName,
            long missingEvidenceCount
    ) {
    }

    public record DashboardTicketRow(
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
    }

    public record DashboardInsights(
            List<DashboardTicketRow> attentionItems,
            List<DashboardEvidenceBottleneckBucket> evidenceBottleneckBuckets
    ) {
    }

    public record MissingEvidenceItem(
            String artifactTypeCode,
            String artifactName,
            String defaultFileName,
            String sourcePath,
            boolean requiredFlag,
            boolean existsFlag
    ) {
    }

    public record RiskItem(
            UUID riskId,
            String riskKey,
            String riskSummary,
            String severity,
            String status,
            boolean mitigationPresent,
            String mitigationSummary
    ) {
    }

    public record ExceptionItem(
            UUID exceptionId,
            String exceptionType,
            String reason,
            String followUpStatus,
            boolean approved,
            String linkedReportPath
    ) {
    }

    public record ScoreBreakdown(
            BigDecimal specScore,
            BigDecimal planScore,
            BigDecimal reviewScore,
            BigDecimal selfReviewScore,
            BigDecimal testScore,
            BigDecimal ciScore,
            BigDecimal blackboxScore,
            BigDecimal reportScore
    ) {
    }

    public record DashboardTicketDetail(
            DashboardTicketRow row,
            List<MissingEvidenceItem> missingEvidenceItems,
            List<RiskItem> riskItems,
            List<ExceptionItem> exceptionItems,
            ScoreBreakdown scoreBreakdown,
            String traceabilityUrl
    ) {
    }

    public record DashboardRefreshResult(long refreshedRows, OffsetDateTime refreshedAt) {
    }
}
