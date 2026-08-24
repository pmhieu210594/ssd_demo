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
                        String riskLevel,
                        String search,
                        int page,
                        int size) {
        }

        public record DashboardOption(
                        String value,
                        String label,
                        String role) {
        }

        public record DashboardPhaseOption(
                        String value,
                        String label,
                        int order) {
        }

        public record TemplateUsageRow(
                        String phaseCode,
                        String phaseName,
                        long totalCheckCount,
                        long templateMatchCount) {
        }

        public record AiFindingStatsRow(
                        UUID repositoryId,
                        String repositoryName,
                        long blockerMajorResolvedSum,
                        long blockerMajorTotalSum,
                        long aiReviewAdoptedSum,
                        long aiReviewFindingTotalSum,
                        long aiReviewValidSum,
                        long aiReviewFalsePositiveSum,
                        long aiReviewResolvedSum) {
        }

        public record DashboardOptions(
                        List<DashboardOption> projects,
                        List<DashboardOption> periods,
                        List<DashboardOption> repositories,
                        List<DashboardPhaseOption> phases) {
        }

        public record DashboardSummary(
                        long blockedTicketCount,
                        long missingEvidenceTicketCount,
                        long missingTraceabilitySectionTicketCount,
                        long openIssueCount,
                        long waitingReviewTicketCount,
                        long ciFailedTicketCount,
                        long firstCiPassTicketCount,
                        long ticketWithCiCount,
                        long riskTicketCount,
                        long exceptionTicketCount,
                        BigDecimal averageEvidenceQualityScore,
                        String phaseBottleneckPhaseCode,
                        String phaseBottleneckPhaseName,
                        long phaseBottleneckBlockedCount,
                        OffsetDateTime updatedAt) {
                public DashboardSummary(
                                long blockedTicketCount,
                                long missingEvidenceTicketCount,
                                long missingTraceabilitySectionTicketCount,
                                long openIssueCount,
                                long waitingReviewTicketCount,
                                long ciFailedTicketCount,
                                long firstCiPassTicketCount,
                                long ticketWithCiCount,
                                BigDecimal averageEvidenceQualityScore,
                                String phaseBottleneckPhaseCode,
                                String phaseBottleneckPhaseName,
                                long phaseBottleneckBlockedCount,
                                OffsetDateTime updatedAt) {
                        this(
                                        blockedTicketCount,
                                        missingEvidenceTicketCount,
                                        missingTraceabilitySectionTicketCount,
                                        openIssueCount,
                                        waitingReviewTicketCount,
                                        ciFailedTicketCount,
                                        firstCiPassTicketCount,
                                        ticketWithCiCount,
                                        0L,
                                        0L,
                                        averageEvidenceQualityScore,
                                        phaseBottleneckPhaseCode,
                                        phaseBottleneckPhaseName,
                                        phaseBottleneckBlockedCount,
                                        updatedAt);
                }
        }

        public record DashboardEvidenceBottleneckBucket(
                        String bucketKey,
                        String bucketName,
                        long missingEvidenceCount) {
        }

        public record DashboardTicketRow(
                        UUID ticketId,
                        UUID projectId,
                        String projectAlias,
                        UUID repositoryId,
                        String repositoryName,
                        String externalTicketKey,
                        String title,
                        String status,
                        UUID phaseId,
                        String phaseCode,
                        String phaseName,
                        String phaseDescription,
                        OffsetDateTime phaseCreatedAt,
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
                        String scoreRuleVersion,
                        int ageDays,
                        String ownerDisplay,
                        String periodKey,
                        OffsetDateTime createdAt,
                        OffsetDateTime updatedAt,
                        OffsetDateTime refreshedAt,
                        Integer artifactVersion,
                        OffsetDateTime mergedAt,
                        OffsetDateTime startedAt,
                        OffsetDateTime completedAt) {
                public DashboardTicketRow(
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
                                String phaseDescription,
                                OffsetDateTime phaseCreatedAt,
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
                                String scoreRuleVersion,
                                int ageDays,
                                String ownerDisplay,
                                String periodKey,
                                OffsetDateTime createdAt,
                                OffsetDateTime updatedAt) {
                        this(
                                        ticketId,
                                        projectId,
                                        projectAlias,
                                        repositoryId,
                                        repositoryName,
                                        externalTicketKey,
                                        title,
                                        null,
                                        phaseId,
                                        phaseCode,
                                        phaseName,
                                        phaseDescription,
                                        phaseCreatedAt,
                                        phaseOrder,
                                        blockedFlag,
                                        waitingReviewFlag,
                                        missingEvidenceCount,
                                        traceabilityIssueCount,
                                        openIssueCount,
                                        riskCount,
                                        exceptionCount,
                                        ciFailedCount,
                                        highestRiskSeverity,
                                        evidenceQualityScore,
                                        scoreRuleVersion,
                                        ageDays,
                                        ownerDisplay,
                                        periodKey,
                                        createdAt,
                                        updatedAt,
                                        updatedAt,
                                        null,
                                        null,
                                        null,
                                        null);
                }
        }

        public record DashboardInsights(
                        List<DashboardTicketRow> attentionItems,
                        List<DashboardEvidenceBottleneckBucket> evidenceBottleneckBuckets) {
        }

        public record MissingEvidenceItem(
                        String artifactTypeCode,
                        String artifactName,
                        String defaultFileName,
                        String sourcePath,
                        boolean requiredFlag,
                        boolean existsFlag) {
        }

        public record RiskItem(
                        UUID riskId,
                        String riskKey,
                        String riskSummary,
                        String severity,
                        String status,
                        boolean mitigationPresent,
                        String mitigationSummary) {
        }

        public record ExceptionItem(
                        UUID exceptionId,
                        String exceptionType,
                        String reason,
                        String followUpStatus,
                        boolean approved,
                        String linkedReportPath) {
        }

        public record DashboardIssueItem(
                        UUID ticketIssueId,
                        UUID ticketId,
                        UUID repositoryId,
                        String sourceType,
                        int issueOrder,
                        String issueKey,
                        String issueTitle,
                        String issueImpact,
                        String issueOwner,
                        String issueStatus,
                        String issueSummary,
                        String sourcePath,
                        OffsetDateTime collectedAt) {
        }

        public record ScoreBreakdown(
                        BigDecimal specScore,
                        BigDecimal planScore,
                        BigDecimal reviewScore,
                        BigDecimal selfReviewScore,
                        BigDecimal testScore,
                        BigDecimal ciScore,
                        BigDecimal blackboxScore,
                        BigDecimal reportScore) {
        }

        public record DashboardTicketDetail(
                        DashboardTicketRow row,
                        OffsetDateTime createdAt,
                        String ownerDisplay,
                        int reviewCount,
                        List<MissingEvidenceItem> missingEvidenceItems,
                        List<RiskItem> riskItems,
                        List<ExceptionItem> exceptionItems,
                        List<DashboardIssueItem> issueItems,
                        ScoreBreakdown scoreBreakdown,
                        String traceabilityUrl,
                        List<PhaseDwellTimeItem> phaseDwellTime) {
        }

        public record PhaseDwellTimeItem(
                        String phaseCode,
                        int phaseOrder,
                        String phaseName,
                        String dwellTime) {
        }

        public record DashboardRefreshResult(long refreshedRows, OffsetDateTime refreshedAt) {
        }
}
