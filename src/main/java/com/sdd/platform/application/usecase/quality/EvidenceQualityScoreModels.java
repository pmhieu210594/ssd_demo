package com.sdd.platform.application.usecase.quality;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EvidenceQualityScoreModels {

    private EvidenceQualityScoreModels() {
    }

    public static final String DEFAULT_RULE_VERSION = "v0";
    public static final String SCORE_METRIC_CODE = "EVIDENCE_QUALITY_SCORE";
    public static final String SCORE_METRIC_NAME = "Evidence Quality Score";

    public record ScoreRequest(
            UUID ticketId,
            List<UUID> ticketIds,
            boolean forceRecalculate,
            String scoreRuleVersion,
            String requestedBy,
            String traceId
    ) {
    }

    public record ScoreCriterion(
            String criterionId,
            String label,
            BigDecimal score,
            BigDecimal maxScore,
            String status,
            List<String> sourceRefs
    ) {
    }

    public record LineageEntry(
            String inputTable,
            String inputRecordId,
            String inputHash,
            String contributionType
    ) {
    }

    public record ScoreResult(
            UUID evidenceQualityScoreId,
            UUID metricValueId,
            UUID ticketId,
            BigDecimal score,
            List<ScoreCriterion> breakdown,
            List<String> missing,
            List<String> parseErrors,
            List<String> traceIds,
            String scoreRuleVersion,
            String snapshotState,
            OffsetDateTime calculatedAt,
            BigDecimal specScore,
            BigDecimal planScore,
            BigDecimal reviewScore,
            BigDecimal selfReviewScore,
            BigDecimal testScore,
            BigDecimal ciScore,
            BigDecimal blackboxScore,
            BigDecimal reportScore,
            List<LineageEntry> lineage
    ) {
        public ScoreResult withIds(UUID evidenceQualityScoreId, UUID metricValueId) {
            return new ScoreResult(
                    evidenceQualityScoreId,
                    metricValueId,
                    ticketId,
                    score,
                    breakdown,
                    missing,
                    parseErrors,
                    traceIds,
                    scoreRuleVersion,
                    snapshotState,
                    calculatedAt,
                    specScore,
                    planScore,
                    reviewScore,
                    selfReviewScore,
                    testScore,
                    ciScore,
                    blackboxScore,
                    reportScore,
                    lineage
            );
        }
    }

    public record SourceSnapshot(
            UUID ticketId,
            UUID projectId,
            UUID repositoryId,
            ArtifactSignal specPack,
            ArtifactSignal implPlan,
            ArtifactSignal reviewChecklist,
            ArtifactSignal selfReview,
            ArtifactSignal testPlan,
            ArtifactSignal testResults,
            ArtifactSignal blackboxTestcases,
            ArtifactSignal report,
            ReviewSignal review,
            CiSignal ci,
            TestSignal test,
            TraceabilitySignal traceability,
            OffsetDateTime latestSourceAt
    ) {
    }

    public record ArtifactSignal(
            UUID artifactSnapshotId,
            String artifactTypeCode,
            String fileName,
            boolean existsFlag,
            boolean templateEmptyFlag,
            String parseStatus,
            String parserVersion,
            OffsetDateTime collectedAt,
            Integer sectionCount,
            Integer tableCount,
            Integer acCount,
            Integer acValidFormatCount,
            String finalVerdict,
            List<String> requiredFieldsMissing,
            List<String> parseErrors,
            Map<String, Boolean> sectionPresence,
            Map<String, Object> parsedSummary,
            String contentHash
    ) {
        public boolean isPresent() {
            return existsFlag && !templateEmptyFlag;
        }
    }

    public record ReviewSignal(
            UUID latestReviewId,
            boolean present,
            String state,
            int commentCount,
            int findingCount,
            OffsetDateTime collectedAt,
            List<String> sourceRefs
    ) {
    }

    public record CiSignal(
            UUID ciRunId,
            boolean present,
            String status,
            String ciUrl,
            String externalRunId,
            int jobCount,
            int linkedJobCount,
            OffsetDateTime collectedAt,
            List<String> sourceRefs
    ) {
    }

    public record TestSignal(
            int testRunCount,
            int testCaseCount,
            int passedCount,
            int failedCount,
            int skippedCount,
            int acCoverageCount,
            int distinctCoveredAcCount,
            String status,
            boolean present,
            OffsetDateTime collectedAt,
            List<String> sourceRefs
    ) {
    }

    public record TraceabilitySignal(
            int linkCount,
            int requiredLinkCount,
            int presentLinkCount,
            boolean chainComplete,
            List<String> linkTypes,
            List<String> sourceRefs,
            OffsetDateTime collectedAt
    ) {
    }
}
