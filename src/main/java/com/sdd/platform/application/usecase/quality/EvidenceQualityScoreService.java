package com.sdd.platform.application.usecase.quality;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.EvidenceQualityScoreRepositoryPort;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ArtifactSignal;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.CiSignal;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.LineageEntry;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ReviewSignal;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ScoreBand;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ScoreCriterion;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ScoreRequest;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ScoreResult;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.SourceSnapshot;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.TestSignal;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.TraceabilitySignal;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class EvidenceQualityScoreService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceQualityScoreService.class);
    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal MAX_SPEC = BigDecimal.valueOf(15);
    private static final BigDecimal MAX_PLAN = BigDecimal.TEN;
    private static final BigDecimal MAX_REVIEW = BigDecimal.valueOf(10);
    private static final BigDecimal MAX_SELF_REVIEW = BigDecimal.valueOf(15);
    private static final BigDecimal MAX_TEST = BigDecimal.valueOf(10);
    private static final BigDecimal MAX_CI = BigDecimal.valueOf(5);
    private static final BigDecimal MAX_BLACKBOX = BigDecimal.valueOf(5);
    private static final BigDecimal MAX_REPORT = BigDecimal.valueOf(10);
    private static final BigDecimal MAX_REPORT_PRESENCE = BigDecimal.valueOf(2);
    private static final BigDecimal MAX_REPORT_CONTENT = BigDecimal.valueOf(8);
    private static final List<String> REPORT_SECTION_KEYS = List.of(
            "EDITED_SUMMARY",
            "SCOPE_OF_INFLUENCE",
            "REVIEW_RESULTS",
            "TEST_RESULTS",
            "ACCEPTED_RISK",
            "OPEN_ISSUES"
    );

    private final EvidenceQualityScoreRepositoryPort repositoryPort;
    private final ObjectMapper objectMapper;

    public EvidenceQualityScoreService(EvidenceQualityScoreRepositoryPort repositoryPort, ObjectMapper objectMapper) {
        this.repositoryPort = repositoryPort;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ScoreResult latest(UUID ticketId) {
        log.debug("Evidence quality score latest lookup ticketId={}", ticketId);
        ScoreResult persisted = repositoryPort.findLatest(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("evidence_quality_score_not_found"));
        SourceSnapshot sourceSnapshot = repositoryPort.loadSourceSnapshot(ticketId);
        return withSnapshotState(persisted, sourceSnapshot);
    }

    @Transactional(readOnly = true)
    public List<ScoreResult> history(UUID ticketId, int limit) {
        return repositoryPort.findHistory(ticketId, limit);
    }

    @Transactional
    public ScoreResult recalculate(UUID ticketId, String scoreRuleVersion, String requestedBy) {
        return recalculateFromCi(ticketId, scoreRuleVersion, requestedBy);
    }

    @Transactional
    public ScoreResult recalculateFromParser(UUID ticketId, String scoreRuleVersion, String requestedBy) {
        log.info("Evidence quality score recalculation requested mode=partial ticketId={} ruleVersion={} requestedBy={}",
                ticketId, normalizeRuleVersion(scoreRuleVersion), normalizeActor(requestedBy));
        ScoreResult result = recalculate(ticketId, scoreRuleVersion, requestedBy, true, "partial");
        log.info("Evidence quality score recalculation finished mode=partial ticketId={} score={} band={} snapshotState={} calculatedAt={}",
                ticketId, result.score(), result.band(), result.snapshotState(), result.calculatedAt());
        return result;
    }

    @Transactional
    public ScoreResult recalculateFromCi(UUID ticketId, String scoreRuleVersion, String requestedBy) {
        log.info("Evidence quality score recalculation requested mode=final ticketId={} ruleVersion={} requestedBy={}",
                ticketId, normalizeRuleVersion(scoreRuleVersion), normalizeActor(requestedBy));
        ScoreResult result = recalculate(ticketId, scoreRuleVersion, requestedBy, true, "final");
        log.info("Evidence quality score recalculation finished mode=final ticketId={} score={} band={} snapshotState={} calculatedAt={}",
                ticketId, result.score(), result.band(), result.snapshotState(), result.calculatedAt());
        return result;
    }

    @Transactional
    public ScoreResult recalculateFromSourceChange(UUID ticketId, String scoreRuleVersion, String requestedBy) {
        log.info("Evidence quality score recalculation requested mode=source-change ticketId={} ruleVersion={} requestedBy={}",
                ticketId, normalizeRuleVersion(scoreRuleVersion), normalizeActor(requestedBy));
        ScoreResult result = recalculate(ticketId, scoreRuleVersion, requestedBy, false, null);
        log.info("Evidence quality score recalculation finished mode=source-change ticketId={} score={} band={} snapshotState={} calculatedAt={}",
                ticketId, result.score(), result.band(), result.snapshotState(), result.calculatedAt());
        return result;
    }

    @Transactional
    public List<ScoreResult> recalculate(List<UUID> ticketIds, String scoreRuleVersion, String requestedBy) {
        if (ticketIds == null || ticketIds.isEmpty()) {
            return List.of();
        }
        List<ScoreResult> results = new ArrayList<>();
        for (UUID ticketId : ticketIds) {
            results.add(recalculate(ticketId, scoreRuleVersion, requestedBy, true));
        }
        return results;
    }

    @Transactional
    public ScoreResult recalculate(String ticketId, String scoreRuleVersion, String requestedBy, boolean forceRecalculate) {
        UUID parsedTicketId = parseTicketId(ticketId);
        return recalculateFromCi(parsedTicketId, scoreRuleVersion, requestedBy);
    }

    @Transactional
    public ScoreResult recalculate(UUID ticketId, String scoreRuleVersion, String requestedBy, boolean forceRecalculate) {
        return recalculate(ticketId, scoreRuleVersion, requestedBy, forceRecalculate, "final");
    }

    @Transactional
    private ScoreResult recalculate(UUID ticketId, String scoreRuleVersion, String requestedBy, boolean forceRecalculate, String snapshotStateOverride) {
        validateTicketId(ticketId);
        String ruleVersion = normalizeRuleVersion(scoreRuleVersion);
        SourceSnapshot sourceSnapshot = repositoryPort.loadSourceSnapshot(ticketId);
        ScoreResult calculated = calculate(sourceSnapshot, ruleVersion, snapshotStateOverride);
        return withSnapshotState(repositoryPort.save(calculated, normalizeActor(requestedBy)), sourceSnapshot);
    }

    private ScoreResult calculate(SourceSnapshot source, String ruleVersion, String snapshotStateOverride) {
        List<ScoreCriterion> breakdown = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> parseErrors = new ArrayList<>();
        List<String> traceIds = new ArrayList<>();
        List<LineageEntry> lineage = new ArrayList<>();

        traceIds.add(source.ticketId().toString());
        if (source.specPack() != null && source.specPack().artifactSnapshotId() != null) {
            traceIds.add(source.specPack().artifactSnapshotId().toString());
        }
        if (source.implPlan() != null && source.implPlan().artifactSnapshotId() != null) {
            traceIds.add(source.implPlan().artifactSnapshotId().toString());
        }
        if (source.selfReview() != null && source.selfReview().artifactSnapshotId() != null) {
            traceIds.add(source.selfReview().artifactSnapshotId().toString());
        }
        if (source.testPlan() != null && source.testPlan().artifactSnapshotId() != null) {
            traceIds.add(source.testPlan().artifactSnapshotId().toString());
        }
        if (source.testResults() != null && source.testResults().artifactSnapshotId() != null) {
            traceIds.add(source.testResults().artifactSnapshotId().toString());
        }
        if (source.review() != null && source.review().latestReviewId() != null) {
            traceIds.add(source.review().latestReviewId().toString());
        }
        if (source.ci() != null && source.ci().ciRunId() != null) {
            traceIds.add(source.ci().ciRunId().toString());
        }

        ScoreCriterion specACs = criterion(
                "spec_pack_ac_numbering",
                "spec-pack.md exists and has numbered ACs",
                MAX_SPEC,
                source.specPack() != null && source.specPack().isPresent(),
                scoreByRatio(source.specPack() == null ? 0 : positive(source.specPack().acValidFormatCount()), source.specPack() == null ? 0 : positive(source.specPack().acCount()), MAX_SPEC),
                sourceRefs(source.specPack()));
        appendCriterion(breakdown, missing, lineage, specACs, source.specPack(), "tbl_fact_artifact_snapshot");

        ScoreCriterion specScope = criterion(
                "spec_pack_scope",
                "spec-pack.md contains Scope child sections, Open Issues, and Risk controls",
                BigDecimal.TEN,
                hasAnySection(source.specPack(), "SCOPE_WITHIN_RANGE", "SCOPE_OUT_OF_RANGE", "OPEN_ISSUES", "SECURITY_PRIVACY_IMPACT", "OPERATION_MAINTENANCE_IMPACT"),
                scoreSpecPackScope(source.specPack()),
                sourceRefs(source.specPack()));
        appendCriterion(breakdown, missing, lineage, specScope, source.specPack(), "tbl_fact_artifact_parsed_section");

        ScoreCriterion implPlan = criterion(
                "impl_plan_impact_rollback_ac",
                "impl-plan.md contains impact scope, rollback, and AC mapping",
                MAX_PLAN,
                hasAnySection(source.implPlan(), "IMPLEMENTATION_PRINCIPLE", "ALTERNATIVE_PLAN", "MIGRATION_ROLLBACK_POLICY", "CORRESPONDING_AC_TABLE", "STEP_IMPLEMENTATION"),
                scoreImplPlan(source.implPlan(), source.specPack()),
                sourceRefs(source.implPlan()));
        appendCriterion(breakdown, missing, lineage, implPlan, source.implPlan(), "tbl_fact_artifact_parsed_section");

        ScoreCriterion reviewSource = criterion(
                "pr_review_canonical_source",
                "PR review metadata/comments are the canonical review source and the review state is present",
                MAX_REVIEW,
                source.review() != null && source.review().present(),
                scoreReviewSource(source.review()),
                source.review() == null ? List.of() : source.review().sourceRefs());
        appendCriterion(breakdown, missing, lineage, reviewSource, source.review(), "tbl_fact_review");

        ScoreCriterion reviewChecklist = criterion(
                "review_checklist_security_test",
                "review-checklist.md exists and covers security/test viewpoints",
                BigDecimal.TEN,
                source.reviewChecklist() != null && source.reviewChecklist().isPresent(),
                scoreArtifactPresence(source.reviewChecklist(), BigDecimal.TEN),
                sourceRefs(source.reviewChecklist()));
        appendCriterion(breakdown, missing, lineage, reviewChecklist, source.reviewChecklist(), "tbl_fact_artifact_snapshot");

        ScoreCriterion selfReview = criterion(
                "self_review_commands_results_concerns",
                "self-review.md contains commands run, results, and concerns",
                MAX_SELF_REVIEW,
                source.selfReview() != null && source.selfReview().isPresent(),
                scoreSelfReview(source.selfReview()),
                sourceRefs(source.selfReview()));
        appendCriterion(breakdown, missing, lineage, selfReview, source.selfReview(), "tbl_fact_artifact_parsed_section");

        ScoreCriterion testLinkage = criterion(
                "test_plan_results_ac_linkage",
                "test-plan.md and test-results.md are linked to ACs",
                MAX_TEST,
                source.testPlan() != null && source.testPlan().isPresent() && source.testResults() != null && source.testResults().isPresent(),
                scoreTestLinkage(source),
                mergeSourceRefs(source.testPlan(), source.testResults()));
        appendCriterion(breakdown, missing, lineage, testLinkage, source.testPlan(), "tbl_fact_artifact_parsed_section");

        ScoreCriterion ciLink = criterion(
                "ci_link_present",
                "CI run ID or CI link is present",
                MAX_CI,
                source.ci() != null && source.ci().present() && (notBlank(source.ci().ciUrl()) || notBlank(source.ci().externalRunId())),
                scoreCiLink(source.ci()),
                source.ci() == null ? List.of() : source.ci().sourceRefs());
        appendCriterion(breakdown, missing, lineage, ciLink, source.ci(), "tbl_fact_ci_run");

        ScoreCriterion blackbox = criterion(
                "blackbox_testcases_main_acs",
                "blackbox-testcases.md covers the main ACs",
                MAX_BLACKBOX,
                source.blackboxTestcases() != null && source.blackboxTestcases().isPresent(),
                scoreArtifactPresence(source.blackboxTestcases(), MAX_BLACKBOX),
                sourceRefs(source.blackboxTestcases()));
        appendCriterion(breakdown, missing, lineage, blackbox, source.blackboxTestcases(), "tbl_fact_artifact_snapshot");

        ScoreCriterion report = criterion(
                "report_overview_impact_review_test_risk_remaining",
                "report.md contains overview, impact, review, test, risk, and remaining issues",
                MAX_REPORT,
                source.report() != null && source.report().isPresent(),
                scoreReport(source.report(), source),
                sourceRefs(source.report()));
        appendCriterion(breakdown, missing, lineage, report, source.report(), "tbl_fact_artifact_snapshot");

        BigDecimal total = breakdown.stream()
                .map(ScoreCriterion::score)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(HUNDRED) > 0) {
            total = HUNDRED.setScale(2, RoundingMode.HALF_UP);
        }

        addMissing(missing, source, total);
        collectParseErrors(parseErrors, source);
        collectTraceIds(traceIds, source);

        String band = ScoreBand.fromScore(total).displayName();
        String snapshotState = snapshotStateOverride == null || snapshotStateOverride.isBlank()
                ? determineSnapshotState(source, parseErrors, missing)
                : snapshotStateOverride;
        OffsetDateTime calculatedAt = source.latestSourceAt() != null && source.latestSourceAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC))
                ? source.latestSourceAt()
                : OffsetDateTime.now(ZoneOffset.UTC);

        return new ScoreResult(
                null,
                null,
                source.ticketId(),
                total,
                band,
                breakdown,
                dedupe(missing),
                dedupe(parseErrors),
                dedupe(traceIds),
                ruleVersion,
                snapshotState,
                calculatedAt,
                specACs.score(),
                implPlan.score(),
                reviewSource.score(),
                selfReview.score(),
                testLinkage.score(),
                ciLink.score(),
                blackbox.score(),
                report.score(),
                lineage
        );
    }

    private ScoreResult withSnapshotState(ScoreResult result, SourceSnapshot sourceSnapshot) {
        if (result == null) {
            return null;
        }
        String snapshotState = determineReadState(result, sourceSnapshot);
        if (Objects.equals(snapshotState, result.snapshotState())) {
            return result;
        }
        return new ScoreResult(
                result.evidenceQualityScoreId(),
                result.metricValueId(),
                result.ticketId(),
                result.score(),
                result.band(),
                result.breakdown(),
                result.missing(),
                result.parseErrors(),
                result.traceIds(),
                result.scoreRuleVersion(),
                snapshotState,
                result.calculatedAt(),
                result.specScore(),
                result.planScore(),
                result.reviewScore(),
                result.selfReviewScore(),
                result.testScore(),
                result.ciScore(),
                result.blackboxScore(),
                result.reportScore(),
                result.lineage()
        );
    }

    private String determineReadState(ScoreResult result, SourceSnapshot source) {
        if (result == null) {
            return "partial";
        }
        if (source == null) {
            return result.snapshotState();
        }
        if (source.latestSourceAt() != null && result.calculatedAt() != null && source.latestSourceAt().isAfter(result.calculatedAt())) {
            return "stale";
        }
        return result.snapshotState();
    }

    private String determineSnapshotState(SourceSnapshot source, List<String> parseErrors, List<String> missing) {
        if (source == null) {
            return "partial";
        }
        if ((source.latestSourceAt() != null && source.latestSourceAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC)))
                || !parseErrors.isEmpty() || !missing.isEmpty()) {
            return "partial";
        }
        return "final";
    }

    private void appendCriterion(List<ScoreCriterion> breakdown,
                                 List<String> missing,
                                 List<LineageEntry> lineage,
                                 ScoreCriterion criterion,
                                 Object source,
                                 String tableName) {
        breakdown.add(criterion);
        if (criterion.score().compareTo(criterion.maxScore()) < 0) {
            missing.add(criterion.criterionId());
        }
        if (source == null) {
            return;
        }
        String sourceId = sourceId(source);
        if (sourceId != null) {
            lineage.add(new LineageEntry(tableName, sourceId, hash(sourceId), criterion.criterionId()));
        }
    }

    private ScoreCriterion criterion(String id, String label, BigDecimal maxScore, boolean complete, BigDecimal score, List<String> sourceRefs) {
        BigDecimal normalized = score.setScale(2, RoundingMode.HALF_UP);
        String status = normalized.compareTo(maxScore.setScale(2, RoundingMode.HALF_UP)) >= 0 ? "complete"
                : normalized.compareTo(ZERO) > 0 ? "partial" : "missing";
        return new ScoreCriterion(id, label, normalized, maxScore.setScale(2, RoundingMode.HALF_UP), status, sourceRefs);
    }

    private BigDecimal scoreArtifactPresence(ArtifactSignal signal, BigDecimal maxScore) {
        if (signal == null || !signal.isPresent()) {
            return ZERO;
        }
        if (signal.templateEmptyFlag()) {
            return maxScore.multiply(BigDecimal.valueOf(0.25)).setScale(2, RoundingMode.HALF_UP);
        }
        return maxScore.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scoreBySections(ArtifactSignal signal, BigDecimal maxScore, String... sectionKeys) {
        if (signal == null || !signal.isPresent() || sectionKeys == null || sectionKeys.length == 0) {
            return ZERO;
        }
        int present = 0;
        for (String key : sectionKeys) {
            if (hasAnySection(signal, key)) {
                present++;
            }
        }
        return maxScore.multiply(BigDecimal.valueOf((double) present / (double) sectionKeys.length)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scoreImplPlan(ArtifactSignal implPlan, ArtifactSignal specPack) {
        if (implPlan == null || !implPlan.isPresent()) {
            return ZERO;
        }
        BigDecimal score = scoreBySections(implPlan, BigDecimal.valueOf(8), "IMPLEMENTATION_PRINCIPLE", "ALTERNATIVE_PLAN", "MIGRATION_ROLLBACK_POLICY", "STEP_IMPLEMENTATION");
        if (hasCompleteCorrespondingAcMapping(implPlan, specPack)) {
            score = score.add(BigDecimal.valueOf(2));
        }
        return score.min(MAX_PLAN).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean hasCompleteCorrespondingAcMapping(ArtifactSignal implPlan, ArtifactSignal specPack) {
        if (implPlan == null || !implPlan.isPresent() || specPack == null || !specPack.isPresent()) {
            return false;
        }
        if (!hasAnySection(implPlan, "CORRESPONDING_AC_TABLE")) {
            return false;
        }
        int requiredAcCount = positive(specPack.acCount());
        int mappedAcCount = parseSummaryInt(implPlan.parsedSummary(), "correspondingAcTableMappedAcCount");
        return requiredAcCount > 0 && mappedAcCount >= requiredAcCount;
    }

    private int parseSummaryInt(Map<String, Object> summary, String key) {
        if (summary == null || key == null || key.isBlank()) {
            return 0;
        }
        Object value = summary.get(key);
        if (value instanceof Number number) {
            return Math.max(number.intValue(), 0);
        }
        if (value instanceof String stringValue) {
            try {
                return Math.max(Integer.parseInt(stringValue.trim()), 0);
            } catch (NumberFormatException ex) {
                return 0;
            }
        }
        return 0;
    }

    private BigDecimal scoreByRatio(int numerator, int denominator, BigDecimal maxScore) {
        if (denominator <= 0 || numerator <= 0) {
            return ZERO;
        }
        BigDecimal ratio = BigDecimal.valueOf((double) numerator / (double) denominator);
        return maxScore.multiply(ratio.min(BigDecimal.ONE)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scoreReviewSource(ReviewSignal signal) {
        if (signal == null || !signal.present()) {
            return ZERO;
        }
        BigDecimal score = MAX_REVIEW;
        if (signal.state() == null || signal.state().isBlank()) {
            score = score.multiply(BigDecimal.valueOf(0.5));
        }
        if (signal.commentCount() <= 0) {
            score = score.multiply(BigDecimal.valueOf(0.75));
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scoreSelfReview(ArtifactSignal signal) {
        if (signal == null || !signal.isPresent()) {
            return ZERO;
        }
        return scoreBySections(signal, MAX_SELF_REVIEW, "RUN_COMMAND_AND_RESULTS", "UNPROCESSED_PENDING_ACCEPTED_RISK", "FINAL_SELF_VERDICT");
    }

    private BigDecimal scoreTestLinkage(SourceSnapshot source) {
        BigDecimal score = ZERO;
        if (source.testPlan() != null && source.testPlan().isPresent()) {
            score = score.add(BigDecimal.valueOf(2));
        }
        if (source.testResults() != null && source.testResults().isPresent()) {
            score = score.add(BigDecimal.valueOf(2));
        }
        if (hasFullAcCoverage(source)) {
            score = score.add(BigDecimal.valueOf(6));
        }
        return score.min(MAX_TEST).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean hasFullAcCoverage(SourceSnapshot source) {
        if (source == null || source.test() == null || !source.test().present()) {
            return false;
        }
        ArtifactSignal specPack = source.specPack();
        if (specPack == null || !specPack.isPresent()) {
            return false;
        }
        int totalAcCount = positive(specPack.acCount());
        int coveredAcCount = positive(source.test().distinctCoveredAcCount());
        if (totalAcCount <= 0 || coveredAcCount < totalAcCount) {
            return false;
        }
        return "SUCCESS".equalsIgnoreCase(source.test().status());
    }

    private BigDecimal scoreCiLink(CiSignal signal) {
        if (signal == null || !signal.present()) {
            return ZERO;
        }
        return scoreByRatio(signal.linkedJobCount(), signal.jobCount(), MAX_CI);
    }

    private BigDecimal scoreReport(ArtifactSignal signal, SourceSnapshot source) {
        if (signal == null || !signal.isPresent()) {
            return ZERO;
        }
        BigDecimal score = MAX_REPORT_PRESENCE.add(scoreBySections(signal, MAX_REPORT_CONTENT, REPORT_SECTION_KEYS.toArray(String[]::new)));
        return score.min(MAX_REPORT).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scoreSpecPackScope(ArtifactSignal signal) {
        if (signal == null || !signal.isPresent()) {
            return ZERO;
        }
        BigDecimal sectionWeight = BigDecimal.valueOf(2);
        BigDecimal score = ZERO;
        if (hasAnySection(signal, "SCOPE_WITHIN_RANGE")) {
            score = score.add(sectionWeight);
        }
        if (hasAnySection(signal, "SCOPE_OUT_OF_RANGE")) {
            score = score.add(sectionWeight);
        }
        if (hasAnySection(signal, "OPEN_ISSUES") && !hasOpenIssueRowsOpen(signal)) {
            score = score.add(sectionWeight);
        }
        if (hasAnySection(signal, "SECURITY_PRIVACY_IMPACT")) {
            score = score.add(sectionWeight);
        }
        if (hasAnySection(signal, "OPERATION_MAINTENANCE_IMPACT")) {
            score = score.add(sectionWeight);
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private void addMissing(List<String> missing, SourceSnapshot source, BigDecimal total) {
        if (source.specPack() == null || !source.specPack().isPresent()) {
            missing.add("spec-pack.md");
        }
        if (source.implPlan() == null || !source.implPlan().isPresent()) {
            missing.add("impl-plan.md");
        }
        if (source.reviewChecklist() == null || !source.reviewChecklist().isPresent()) {
            missing.add("review-checklist.md");
        }
        if (source.selfReview() == null || !source.selfReview().isPresent()) {
            missing.add("self-review.md");
        }
        if (source.testPlan() == null || !source.testPlan().isPresent()) {
            missing.add("test-plan.md");
        }
        if (source.testResults() == null || !source.testResults().isPresent()) {
            missing.add("test-results.md");
        }
        if (source.blackboxTestcases() == null || !source.blackboxTestcases().isPresent()) {
            missing.add("blackbox-testcases.md");
        }
        if (source.report() == null || !source.report().isPresent()) {
            missing.add("report.md");
        }
        if (source.review() == null || !source.review().present()) {
            missing.add("pr_review");
        }
        if (source.ci() == null || !source.ci().present()) {
            missing.add("ci_run");
        }
        if (source.traceability() == null || !source.traceability().chainComplete()) {
            missing.add("traceability_chain");
        }
        if (source.test() == null || !source.test().present()) {
            missing.add("test_coverage");
        }
        if (total.compareTo(ZERO) <= 0) {
            missing.add("score_zero");
        }
    }

    private void collectParseErrors(List<String> parseErrors, SourceSnapshot source) {
        addParseErrors(parseErrors, source.specPack());
        addParseErrors(parseErrors, source.implPlan());
        addParseErrors(parseErrors, source.selfReview());
        addParseErrors(parseErrors, source.testPlan());
        addParseErrors(parseErrors, source.testResults());
        addParseErrors(parseErrors, source.reviewChecklist());
        addParseErrors(parseErrors, source.blackboxTestcases());
        addParseErrors(parseErrors, source.report());
        if (source.review() == null || !source.review().present()) {
            parseErrors.add("review_source_unavailable");
        }
    }

    private void addParseErrors(List<String> parseErrors, ArtifactSignal signal) {
        if (signal == null) {
            return;
        }
        if (signal.parseStatus() != null && !signal.parseStatus().isBlank()
                && !signal.parseStatus().equalsIgnoreCase("SUCCESS")
                && !signal.parseStatus().equalsIgnoreCase("OFFICIAL")
                && !signal.parseStatus().equalsIgnoreCase("DRAFT")) {
            parseErrors.add(signal.artifactTypeCode() + ":" + signal.parseStatus());
        }
        if (signal.parseErrors() != null) {
            for (String error : signal.parseErrors()) {
                if (error != null && !error.isBlank()) {
                    parseErrors.add(signal.artifactTypeCode() + ":" + error);
                }
            }
        }
    }

    private void collectTraceIds(List<String> traceIds, SourceSnapshot source) {
        addTraceId(traceIds, source.specPack());
        addTraceId(traceIds, source.implPlan());
        addTraceId(traceIds, source.reviewChecklist());
        addTraceId(traceIds, source.selfReview());
        addTraceId(traceIds, source.testPlan());
        addTraceId(traceIds, source.testResults());
        addTraceId(traceIds, source.blackboxTestcases());
        addTraceId(traceIds, source.report());
        if (source.review() != null && source.review().sourceRefs() != null) {
            traceIds.addAll(source.review().sourceRefs());
        }
        if (source.ci() != null && source.ci().sourceRefs() != null) {
            traceIds.addAll(source.ci().sourceRefs());
        }
        if (source.test() != null && source.test().sourceRefs() != null) {
            traceIds.addAll(source.test().sourceRefs());
        }
        if (source.traceability() != null && source.traceability().sourceRefs() != null) {
            traceIds.addAll(source.traceability().sourceRefs());
        }
    }

    private void addTraceId(List<String> traceIds, ArtifactSignal signal) {
        if (signal != null && signal.artifactSnapshotId() != null) {
            traceIds.add(signal.artifactSnapshotId().toString());
        }
    }

    private boolean hasAnySection(ArtifactSignal signal, String... keys) {
        if (signal == null || signal.sectionPresence() == null || keys == null) {
            return false;
        }
        for (String key : keys) {
            if (Boolean.TRUE.equals(signal.sectionPresence().get(key))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasOpenIssueRowsOpen(ArtifactSignal signal) {
        if (signal == null || signal.parsedSummary() == null) {
            return false;
        }
        Object value = signal.parsedSummary().get("open_issue_open_count");
        if (value instanceof Number number) {
            return number.intValue() > 0;
        }
        if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue.trim()) > 0;
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        return false;
    }

    private List<String> sourceRefs(ArtifactSignal signal) {
        if (signal == null || signal.artifactSnapshotId() == null) {
            return List.of();
        }
        return List.of("tbl_fact_artifact_snapshot:" + signal.artifactSnapshotId());
    }

    private List<String> mergeSourceRefs(ArtifactSignal left, ArtifactSignal right) {
        Set<String> refs = new LinkedHashSet<>();
        refs.addAll(sourceRefs(left));
        refs.addAll(sourceRefs(right));
        return List.copyOf(refs);
    }

    private UUID parseTicketId(String ticketId) {
        validateTicketId(ticketId);
        return UUID.fromString(ticketId);
    }

    private void validateTicketId(UUID ticketId) {
        if (ticketId == null) {
            throw new IllegalArgumentException("invalid_ticket_id");
        }
    }

    private void validateTicketId(String ticketId) {
        if (ticketId == null || ticketId.isBlank() || !UUID_PATTERN.matcher(ticketId.trim()).matches()) {
            throw new IllegalArgumentException("invalid_ticket_id");
        }
    }

    private String normalizeRuleVersion(String ruleVersion) {
        if (ruleVersion == null || ruleVersion.isBlank()) {
            return EvidenceQualityScoreModels.DEFAULT_RULE_VERSION;
        }
        String normalized = ruleVersion.trim().toLowerCase(Locale.ROOT);
        if (!EvidenceQualityScoreModels.DEFAULT_RULE_VERSION.equals(normalized)) {
            throw new IllegalArgumentException("unsupported_score_rule_version");
        }
        return normalized;
    }

    private String normalizeActor(String actor) {
        if (actor == null || actor.isBlank()) {
            return "SYSTEM";
        }
        return actor.trim();
    }

    private static int positive(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static <T> List<T> dedupe(List<T> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().filter(Objects::nonNull).distinct().toList();
    }

    private static String sourceId(Object source) {
        if (source instanceof ArtifactSignal artifact && artifact.artifactSnapshotId() != null) {
            return artifact.artifactSnapshotId().toString();
        }
        if (source instanceof ReviewSignal review && review.latestReviewId() != null) {
            return review.latestReviewId().toString();
        }
        if (source instanceof CiSignal ci && ci.ciRunId() != null) {
            return ci.ciRunId().toString();
        }
        if (source instanceof TestSignal) {
            return "test-signal";
        }
        if (source instanceof TraceabilitySignal) {
            return "traceability-signal";
        }
        return null;
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return value == null ? "" : value;
        }
    }

    private static String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
