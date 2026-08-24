package com.sdd.platform.infrastructure.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.port.out.persistence.EvidenceQualityScoreRepositoryPort;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ArtifactSignal;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.CiSignal;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.LineageEntry;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ReviewSignal;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ScoreResult;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.SourceSnapshot;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.TestSignal;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.TraceabilitySignal;
import com.sdd.platform.application.usecase.quality.ScoreThresholdConfigService;
import com.sdd.platform.infrastructure.persistence.mapper.EvidenceQualityScoreMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;
import java.util.UUID;

@Repository
public class EvidenceQualityScoreRepositoryAdapter implements EvidenceQualityScoreRepositoryPort {

    private static final String DEFAULT_RULE_VERSION = "v0";
    private static final int HISTORY_LIMIT_DEFAULT = 20;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ScoreThresholdConfigService scoreThresholdConfigService;

    public EvidenceQualityScoreRepositoryAdapter(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper,
            ScoreThresholdConfigService scoreThresholdConfigService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.scoreThresholdConfigService = scoreThresholdConfigService;
    }

    @Override
    public SourceSnapshot loadSourceSnapshot(UUID ticketId) {
        if (ticketId == null) {
            throw new IllegalArgumentException("ticketId is required");
        }

        TicketIdentity identity = loadTicketIdentity(ticketId);
        UUID repositoryId = resolveRepositoryId(ticketId).orElse(identity.repositoryId());
        ArtifactSignal specPack = loadArtifactSignal(ticketId, "SPEC_PACK",
                List.of("SCOPE", "SCOPE_WITHIN_RANGE", "SCOPE_OUT_OF_RANGE", "OPEN_ISSUES", "SECURITY_PRIVACY_IMPACT",
                        "OPERATION_MAINTENANCE_IMPACT"));
        ArtifactSignal implPlan = loadArtifactSignal(ticketId, "IMPL_PLAN",
                List.of("IMPLEMENTATION_PRINCIPLE", "ALTERNATIVE_PLAN", "MIGRATION_ROLLBACK_POLICY",
                        "CORRESPONDING_AC_TABLE", "STEP_IMPLEMENTATION"));
        ArtifactSignal reviewChecklist = loadArtifactSignal(ticketId, "REVIEW_CHECKLIST", List.of());
        ArtifactSignal selfReview = loadArtifactSignal(ticketId, "SELF_REVIEW",
                List.of("RUN_COMMAND_AND_RESULTS", "SELF_CHECK_USING_REVIEW_CHECKLIST",
                        "UNPROCESSED_PENDING_ACCEPTED_RISK", "ITEMS_REVIEWED_BY_HUMANS", "FINAL_SELF_VERDICT"));
        ArtifactSignal testPlan = loadArtifactSignal(ticketId, "TEST_PLAN",
                List.of("AC_MATRIX_TEST_TYPE", "ADDITIONAL_TEST_THIS_TIME", "EXECUTION_COMMAND", "STOP_CONDITION"));
        ArtifactSignal testResults = loadArtifactSignal(ticketId, "TEST_RESULTS",
                List.of("EXECUTION_ENVIRONMENT", "EXECUTED_COMMAND", "SUMMARY_OF_RESULTS", "FINAL_TEST_VERDICT"));
        ArtifactSignal blackboxTestcases = loadArtifactSignal(ticketId, "BLACKBOX_TESTCASES", List.of());
        ArtifactSignal report = loadArtifactSignal(ticketId, "REPORT", List.of(
                "EDITED_SUMMARY",
                "SCOPE_OF_INFLUENCE",
                "REVIEW_RESULTS",
                "TEST_RESULTS",
                "OPEN_ISSUES"));
        ReviewSignal review = loadReviewSignal(ticketId);
        CiSignal ci = loadCiSignal(ticketId);
        TestSignal test = loadTestSignal(ticketId);
        TraceabilitySignal traceability = loadTraceabilitySignal(ticketId);
        OffsetDateTime latestSourceAt = findLatestSourceTimestamp(ticketId);

        return new SourceSnapshot(
                ticketId,
                identity.projectId(),
                repositoryId,
                specPack,
                implPlan,
                reviewChecklist,
                selfReview,
                testPlan,
                testResults,
                blackboxTestcases,
                report,
                review,
                ci,
                test,
                traceability,
                latestSourceAt);
    }

    @Override
    public Optional<ScoreResult> findLatest(UUID ticketId) {
        List<ScoreResult> rows = jdbc.query(
                """
                        SELECT
                            sq.evidence_quality_score_id,
                            sq.metric_value_id,
                            sq.ticket_id,
                            sq.score,
                            to_jsonb(sq.missing_items)::text AS missing_items,
                            mv.breakdown::text AS breakdown,
                            mv.metric_code,
                            mv.definition_version,
                            sq.score_rule_version,
                            sq.spec_score,
                            sq.plan_score,
                            sq.review_score,
                            sq.self_review_score,
                            sq.test_score,
                            sq.ci_score,
                            sq.blackbox_score,
                            sq.report_score,
                            to_jsonb(COALESCE(lineage.lineage_rows, '[]'::jsonb))::text AS lineage,
                            sq.calculated_at,
                            CASE
                                WHEN sq.score_rule_version IS NULL THEN 'partial'
                                WHEN sq.score_rule_version = :ruleVersion
                                     AND sq.calculated_at >= COALESCE(source.latest_source_at, sq.calculated_at)
                                     AND COALESCE(jsonb_array_length(sq.missing_items), 0) = 0
                                     THEN 'final'
                                WHEN sq.calculated_at < COALESCE(source.latest_source_at, sq.calculated_at) THEN 'stale'
                                ELSE 'partial'
                            END AS snapshot_state,
                            sq.created_at,
                            sq.updated_at,
                            sq.created_by,
                            sq.updated_by
                        FROM tbl_fact_evidence_quality_score sq
                        LEFT JOIN tbl_fact_metric_value mv ON mv.metric_value_id = sq.metric_value_id
                        LEFT JOIN LATERAL (
                            SELECT jsonb_agg(
                                jsonb_build_object(
                                    'input_table', l.input_table,
                                    'input_record_id', l.input_record_id,
                                    'input_hash', l.input_hash,
                                    'contribution_type', l.contribution_type
                                )
                            ) AS lineage_rows
                            FROM tbl_fact_metric_input_lineage l
                            WHERE l.metric_value_id = sq.metric_value_id
                        ) lineage ON TRUE
                        LEFT JOIN LATERAL (
                            SELECT max(ts) AS latest_source_at
                            FROM (
                                SELECT max(a.collected_at) AS ts FROM tbl_fact_artifact_snapshot a WHERE a.ticket_id = sq.ticket_id
                                UNION ALL SELECT max(r.collected_at) AS ts FROM tbl_fact_review r WHERE r.ticket_id = sq.ticket_id
                                UNION ALL SELECT max(c.collected_at) AS ts FROM tbl_fact_ci_run c WHERE c.ticket_id = sq.ticket_id
                                UNION ALL SELECT max(t.collected_at) AS ts FROM tbl_fact_test_run t WHERE t.ticket_id = sq.ticket_id
                                UNION ALL SELECT max(e.generated_at) AS ts FROM tbl_fact_evidence_report e WHERE e.ticket_id = sq.ticket_id
                            ) source_times
                        ) source ON TRUE
                        WHERE sq.ticket_id = :ticketId
                          AND sq.score_rule_version = :ruleVersion
                        ORDER BY sq.calculated_at DESC, sq.evidence_quality_score_id DESC
                        LIMIT 1
                        """,
                new MapSqlParameterSource("ticketId", ticketId).addValue("ruleVersion", DEFAULT_RULE_VERSION),
                EvidenceQualityScoreMapper.scoreResultRowMapper(objectMapper, scoreThresholdConfigService));
        return rows.stream().findFirst();
    }

    @Override
    public List<ScoreResult> findHistory(UUID ticketId, int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit <= 0 ? HISTORY_LIMIT_DEFAULT : limit, 100));
        return jdbc.query("""
                SELECT
                    sq.evidence_quality_score_id,
                    sq.metric_value_id,
                    sq.ticket_id,
                    sq.score,
                    to_jsonb(sq.missing_items)::text AS missing_items,
                    mv.breakdown::text AS breakdown,
                    mv.metric_code,
                    mv.definition_version,
                    sq.score_rule_version,
                    sq.spec_score,
                    sq.plan_score,
                    sq.review_score,
                    sq.self_review_score,
                    sq.test_score,
                    sq.ci_score,
                    sq.blackbox_score,
                    sq.report_score,
                    to_jsonb(COALESCE(lineage.lineage_rows, '[]'::jsonb))::text AS lineage,
                    sq.calculated_at,
                    'final' AS snapshot_state,
                    sq.created_at,
                    sq.updated_at,
                    sq.created_by,
                    sq.updated_by
                FROM tbl_fact_evidence_quality_score sq
                LEFT JOIN tbl_fact_metric_value mv ON mv.metric_value_id = sq.metric_value_id
                LEFT JOIN LATERAL (
                    SELECT jsonb_agg(
                        jsonb_build_object(
                            'input_table', l.input_table,
                            'input_record_id', l.input_record_id,
                            'input_hash', l.input_hash,
                            'contribution_type', l.contribution_type
                        )
                    ) AS lineage_rows
                    FROM tbl_fact_metric_input_lineage l
                    WHERE l.metric_value_id = sq.metric_value_id
                ) lineage ON TRUE
                WHERE sq.ticket_id = :ticketId
                ORDER BY sq.calculated_at DESC, sq.evidence_quality_score_id DESC
                LIMIT :limit
                """,
                new MapSqlParameterSource("ticketId", ticketId).addValue("limit", normalizedLimit),
                EvidenceQualityScoreMapper.scoreResultRowMapper(objectMapper, scoreThresholdConfigService));
    }

    @Override
    public ScoreResult save(ScoreResult result, String requestedBy) {
        String actor = normalizeActor(requestedBy);
        UUID metricValueId = result.metricValueId() != null ? result.metricValueId() : UUID.randomUUID();
        UUID evidenceQualityScoreId = result.evidenceQualityScoreId() != null ? result.evidenceQualityScoreId()
                : UUID.randomUUID();
        UUID metricId = resolveMetricId(result.scoreRuleVersion());
        String breakdownJson = EvidenceQualityScoreMapper.toJson(objectMapper, result.breakdown());
        String lineageJson = EvidenceQualityScoreMapper.toJson(objectMapper, result.lineage());
        String missingJson = EvidenceQualityScoreMapper.toJson(objectMapper, result.missing());

        jdbc.update(
                """
                        INSERT INTO tbl_fact_metric_value (
                            metric_value_id, metric_id, metric_code, definition_version,
                            project_id, repository_id, ticket_id, period_type, period_start, period_end,
                            value, breakdown, calculated_at, created_at, created_by, updated_at, updated_by
                        ) VALUES (
                            :metricValueId, :metricId, :metricCode, :definitionVersion,
                            :projectId, :repositoryId, :ticketId, :periodType, :periodStart, :periodEnd,
                            :value, CAST(:breakdown AS jsonb), :calculatedAt, :createdAt, :createdBy, :updatedAt, :updatedBy
                        )
                        ON CONFLICT (metric_value_id) DO UPDATE SET
                            metric_id = EXCLUDED.metric_id,
                            metric_code = EXCLUDED.metric_code,
                            definition_version = EXCLUDED.definition_version,
                            project_id = EXCLUDED.project_id,
                            repository_id = EXCLUDED.repository_id,
                            ticket_id = EXCLUDED.ticket_id,
                            period_type = EXCLUDED.period_type,
                            period_start = EXCLUDED.period_start,
                            period_end = EXCLUDED.period_end,
                            value = EXCLUDED.value,
                            breakdown = EXCLUDED.breakdown,
                            calculated_at = EXCLUDED.calculated_at,
                            updated_at = EXCLUDED.updated_at,
                            updated_by = EXCLUDED.updated_by
                        """,
                scoreParams(result, metricValueId, metricId, breakdownJson, actor));

        jdbc.update(
                """
                        INSERT INTO tbl_fact_evidence_quality_score (
                            evidence_quality_score_id, ticket_id, metric_value_id, score, score_rule_version,
                            spec_score, plan_score, review_score, self_review_score, test_score, ci_score, blackbox_score, report_score,
                            missing_items, calculated_at, created_at, created_by, updated_at, updated_by
                        ) VALUES (
                            :evidenceQualityScoreId, :ticketId, :metricValueId, :score, :scoreRuleVersion,
                            :specScore, :planScore, :reviewScore, :selfReviewScore, :testScore, :ciScore, :blackboxScore, :reportScore,
                            CAST(:missingItems AS jsonb), :calculatedAt, :createdAt, :createdBy, :updatedAt, :updatedBy
                        )
                        """,
                scoreParams(result.withIds(evidenceQualityScoreId, metricValueId), metricValueId, metricId,
                        breakdownJson, actor)
                        .addValue("missingItems", missingJson));

        insertLineage(metricValueId, result.lineage(), actor);
        return result.withIds(evidenceQualityScoreId, metricValueId);
    }

    private void insertLineage(UUID metricValueId, List<LineageEntry> lineageEntries, String actor) {
        if (lineageEntries == null || lineageEntries.isEmpty()) {
            return;
        }
        for (LineageEntry entry : lineageEntries) {
            jdbc.update("""
                    INSERT INTO tbl_fact_metric_input_lineage (
                        metric_value_id, input_table, input_record_id, input_hash, contribution_type,
                        created_at, created_by, updated_at, updated_by
                    ) VALUES (
                        :metricValueId, :inputTable, :inputRecordId, :inputHash, :contributionType,
                        now(), :createdBy, now(), :updatedBy
                    )
                    """,
                    new MapSqlParameterSource()
                            .addValue("metricValueId", metricValueId)
                            .addValue("inputTable", entry.inputTable())
                            .addValue("inputRecordId", entry.inputRecordId())
                            .addValue("inputHash", entry.inputHash())
                            .addValue("contributionType", entry.contributionType())
                            .addValue("createdBy", actor)
                            .addValue("updatedBy", actor));
        }
    }

    private MapSqlParameterSource scoreParams(ScoreResult result,
            UUID metricValueId,
            UUID metricId,
            String breakdownJson,
            String actor) {
        return new MapSqlParameterSource()
                .addValue("evidenceQualityScoreId",
                        result.evidenceQualityScoreId() == null ? UUID.randomUUID() : result.evidenceQualityScoreId())
                .addValue("metricValueId", metricValueId)
                .addValue("metricId", metricId)
                .addValue("metricCode", "EVIDENCE_QUALITY_SCORE")
                .addValue("definitionVersion", result.scoreRuleVersion())
                .addValue("scoreRuleVersion", result.scoreRuleVersion())
                .addValue("projectId", null)
                .addValue("repositoryId", null)
                .addValue("ticketId", result.ticketId())
                .addValue("periodType", "RUN")
                .addValue("periodStart", java.time.LocalDate.now())
                .addValue("periodEnd", java.time.LocalDate.now())
                .addValue("value", result.score())
                .addValue("score", result.score())
                .addValue("breakdown", breakdownJson)
                .addValue("calculatedAt", result.calculatedAt())
                .addValue("createdAt", result.calculatedAt())
                .addValue("createdBy", actor)
                .addValue("updatedAt", result.calculatedAt())
                .addValue("updatedBy", actor)
                .addValue("specScore", result.specScore())
                .addValue("planScore", result.planScore())
                .addValue("reviewScore", result.reviewScore())
                .addValue("selfReviewScore", result.selfReviewScore())
                .addValue("testScore", result.testScore())
                .addValue("ciScore", result.ciScore())
                .addValue("blackboxScore", result.blackboxScore())
                .addValue("reportScore", result.reportScore());
    }

    private UUID resolveMetricId(String ruleVersion) {
        List<UUID> metricIds = jdbc.query("""
                SELECT metric_id
                FROM tbl_dim_metric_definition
                WHERE metric_code = :metricCode
                  AND version = :version
                ORDER BY valid_from DESC, created_at DESC
                LIMIT 1
                """,
                new MapSqlParameterSource()
                        .addValue("metricCode", "EVIDENCE_QUALITY_SCORE")
                        .addValue("version",
                                ruleVersion == null || ruleVersion.isBlank() ? DEFAULT_RULE_VERSION : ruleVersion),
                (rs, rowNum) -> rs.getObject("metric_id", UUID.class));
        if (metricIds.isEmpty()) {
            throw new IllegalStateException(
                    "Metric definition EVIDENCE_QUALITY_SCORE not configured for version " + ruleVersion);
        }
        return metricIds.getFirst();
    }

    private ArtifactSignal loadArtifactSignal(UUID ticketId, String artifactTypeCode, List<String> sectionKeys) {
        List<ArtifactSignal> rows = jdbc.query(
                """
                        SELECT
                            s.artifact_snapshot_id,
                            t.artifact_type_code,
                            t.default_file_name,
                            s.exists_flag,
                            COALESCE(s.template_empty_flag, FALSE) AS template_empty_flag,
                            COALESCE(s.parsed_summary->>'parseStatus', s.parsed_summary->>'parse_status', '') AS parse_status,
                            COALESCE(s.parser_version, s.parsed_summary->>'parser_version', '') AS parser_version,
                            s.collected_at,
                            COALESCE((s.parsed_summary->>'section_count')::int, 0) AS section_count,
                            COALESCE((s.parsed_summary->>'table_count')::int, 0) AS table_count,
                            COALESCE((s.parsed_summary->>'ac_count')::int, 0) AS ac_count,
                            COALESCE((s.parsed_summary->>'ac_valid_format_count')::int, 0) AS ac_valid_format_count,
                            COALESCE(s.parsed_summary->>'final_verdict', s.parsed_summary->>'finalVerdict', '') AS final_verdict,
                            COALESCE(to_jsonb(s.required_fields_missing), '[]'::jsonb)::text AS required_fields_missing,
                            COALESCE(to_jsonb(ARRAY(
                                SELECT p.parse_warning
                                FROM tbl_fact_artifact_parsed_section p
                                WHERE p.artifact_snapshot_id = s.artifact_snapshot_id
                                  AND p.parse_warning IS NOT NULL
                            )), '[]'::jsonb)::text AS parse_errors,
                            COALESCE(to_jsonb(s.parsed_summary), '{}'::jsonb)::text AS parsed_summary,
                            s.content_hash
                        FROM tbl_fact_artifact_snapshot s
                        JOIN tbl_dim_artifact_type t ON t.artifact_type_id = s.artifact_type_id
                        WHERE s.ticket_id = :ticketId
                          AND t.artifact_type_code = :artifactTypeCode
                        ORDER BY
                            s.collected_at DESC,
                            s.created_at DESC
                        LIMIT 1
                        """,
                new MapSqlParameterSource()
                        .addValue("ticketId", ticketId)
                        .addValue("artifactTypeCode", artifactTypeCode),
                EvidenceQualityScoreMapper.artifactSignalRowMapper(objectMapper));
        if (rows.isEmpty()) {
            return new ArtifactSignal(null, artifactTypeCode, artifactTypeCode.toLowerCase() + ".md", false, false,
                    null, null, null, 0, 0, 0, 0, null, List.of(), List.of(), Map.of(), Map.of(), null);
        }
        ArtifactSignal signal = rows.getFirst();
        if ("SPEC_PACK".equalsIgnoreCase(artifactTypeCode)) {
            AcceptanceCriteriaStats acStats = loadAcceptanceCriteriaStats(ticketId);
            signal = new ArtifactSignal(
                    signal.artifactSnapshotId(),
                    signal.artifactTypeCode(),
                    signal.fileName(),
                    signal.existsFlag(),
                    signal.templateEmptyFlag(),
                    signal.parseStatus(),
                    signal.parserVersion(),
                    signal.collectedAt(),
                    signal.sectionCount(),
                    signal.tableCount(),
                    acStats.acCount(),
                    acStats.acValidFormatCount(),
                    signal.finalVerdict(),
                    signal.requiredFieldsMissing(),
                    signal.parseErrors(),
                    Collections.emptyMap(),
                    signal.parsedSummary(),
                    signal.contentHash());
        }
        Map<String, Boolean> presence = loadSectionPresence(signal.artifactSnapshotId(), sectionKeys);
        return new ArtifactSignal(
                signal.artifactSnapshotId(),
                signal.artifactTypeCode(),
                signal.fileName(),
                signal.existsFlag(),
                signal.templateEmptyFlag(),
                signal.parseStatus(),
                signal.parserVersion(),
                signal.collectedAt(),
                signal.sectionCount(),
                signal.tableCount(),
                signal.acCount(),
                signal.acValidFormatCount(),
                signal.finalVerdict(),
                signal.requiredFieldsMissing(),
                signal.parseErrors(),
                presence,
                signal.parsedSummary(),
                signal.contentHash());
    }

    private AcceptanceCriteriaStats loadAcceptanceCriteriaStats(UUID ticketId) {
        List<AcceptanceCriteriaStats> rows = jdbc.query(
                """
                        SELECT
                            COALESCE(count(*), 0) AS ac_count,
                            COALESCE(sum(CASE WHEN COALESCE(ambiguous_flag, FALSE) = FALSE THEN 1 ELSE 0 END), 0) AS ac_valid_format_count
                        FROM tbl_fact_acceptance_criteria
                        WHERE ticket_id = :ticketId
                          AND status = 'ACTIVE'
                        """,
                new MapSqlParameterSource("ticketId", ticketId),
                (rs, rowNum) -> new AcceptanceCriteriaStats(
                        rs.getInt("ac_count"),
                        rs.getInt("ac_valid_format_count")));
        if (rows.isEmpty()) {
            return new AcceptanceCriteriaStats(0, 0);
        }
        return rows.getFirst();
    }

    private Map<String, Boolean> loadSectionPresence(UUID artifactSnapshotId, List<String> sectionKeys) {
        if (artifactSnapshotId == null || sectionKeys == null || sectionKeys.isEmpty()) {
            return Map.of();
        }
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String sectionKey : sectionKeys) {
            List<Boolean> rows = jdbc.query("""
                    SELECT COALESCE(bool_or(p.present_flag), FALSE) AS present_flag
                    FROM tbl_fact_artifact_parsed_section p
                    WHERE p.artifact_snapshot_id = :artifactSnapshotId
                      AND UPPER(p.section_key) = UPPER(:sectionKey)
                    """,
                    new MapSqlParameterSource()
                            .addValue("artifactSnapshotId", artifactSnapshotId)
                            .addValue("sectionKey", sectionKey),
                    (rs, rowNum) -> rs.getBoolean("present_flag"));
            result.put(sectionKey, !rows.isEmpty() && rows.getFirst());
        }
        return result;
    }

    private ReviewSignal loadReviewSignal(UUID ticketId) {
        List<ReviewSignal> rows = jdbc.query("""
                SELECT
                    r.review_id,
                    TRUE AS present_flag,
                    COALESCE(r.state::text, '') AS review_state,
                    COALESCE(r.comment_count, 0) AS comment_count,
                    COALESCE(f.finding_count, 0) AS finding_count,
                    COALESCE(r.collected_at, r.created_at, now()) AS collected_at
                FROM tbl_fact_review r
                LEFT JOIN LATERAL (
                    SELECT count(*) AS finding_count
                    FROM tbl_fact_finding f
                    WHERE f.review_id = r.review_id
                ) f ON TRUE
                WHERE r.ticket_id = :ticketId
                ORDER BY COALESCE(r.collected_at, r.created_at, now()) DESC, r.review_id DESC
                LIMIT 1
                """,
                new MapSqlParameterSource("ticketId", ticketId),
                (rs, rowNum) -> new ReviewSignal(
                        rs.getObject("review_id", UUID.class),
                        true,
                        rs.getString("review_state"),
                        rs.getInt("comment_count"),
                        rs.getInt("finding_count"),
                        rs.getObject("collected_at", OffsetDateTime.class),
                        List.of("tbl_fact_review:" + rs.getObject("review_id", UUID.class))));
        if (rows.isEmpty()) {
            return new ReviewSignal(null, false, null, 0, 0, null, List.of());
        }
        return rows.getFirst();
    }

    private CiSignal loadCiSignal(UUID ticketId) {
        // Step 1: Get the latest CI run for this ticket
        List<CiSignal> ciRuns = jdbc.query("""
                SELECT
                    c.ci_run_id,
                    COALESCE(c.status::text, '') AS status,
                    COALESCE(c.ci_url, '') AS ci_url,
                    COALESCE(c.external_run_id, c.external_ci_run_id, '') AS external_run_id,
                    COALESCE(c.updated_at, c.created_at, c.collected_at, now()) AS collected_at
                FROM tbl_fact_ci_run c
                WHERE c.ticket_id = :ticketId
                ORDER BY c.started_at DESC NULLS LAST, c.ci_run_id DESC
                LIMIT 1
                """,
                new MapSqlParameterSource("ticketId", ticketId),
                (rs, rowNum) -> new CiSignal(
                        rs.getObject("ci_run_id", UUID.class),
                        true,
                        rs.getString("status"),
                        rs.getString("ci_url"),
                        rs.getString("external_run_id"),
                        0, 0,
                        rs.getObject("collected_at", OffsetDateTime.class),
                        List.of("tbl_fact_ci_run:" + rs.getObject("ci_run_id", UUID.class))));

        if (ciRuns.isEmpty()) {
            return new CiSignal(null, false, null, null, null, 0, 0, null, List.of());
        }

        CiSignal latestRun = ciRuns.getFirst();

        // Step 2: Get job statuses for this run
        List<String> jobStatuses = jdbc.query("""
                SELECT status::text AS status
                FROM tbl_fact_ci_job
                WHERE ci_run_id = :ciRunId
                """,
                new MapSqlParameterSource("ciRunId", latestRun.ciRunId()),
                (rs, rowNum) -> rs.getString("status"));

        // Step 3: Compute job counts
        // Fallback to run-level status if no jobs recorded yet
        int jobCount = jobStatuses.isEmpty() ? 1 : jobStatuses.size();
        int linkedJobCount;
        if (jobStatuses.isEmpty()) {
            linkedJobCount = "SUCCESS".equalsIgnoreCase(latestRun.status())
                    || "SKIPPED".equalsIgnoreCase(latestRun.status()) ? 1 : 0;
        } else {
            linkedJobCount = (int) jobStatuses.stream()
                    .filter(s -> "SUCCESS".equalsIgnoreCase(s) || "SKIPPED".equalsIgnoreCase(s))
                    .count();
        }

        return new CiSignal(
                latestRun.ciRunId(),
                true,
                latestRun.status(),
                latestRun.ciUrl(),
                latestRun.externalRunId(),
                jobCount,
                linkedJobCount,
                latestRun.collectedAt(),
                latestRun.sourceRefs());
    }

    private TestSignal loadTestSignal(UUID ticketId) {
        List<TestSignal> rows = jdbc.query(
                """
                        WITH latest_test_run AS (
                            SELECT
                                tr.test_run_id,
                                tr.status,
                                tr.collected_at
                            FROM tbl_fact_test_run tr
                            WHERE tr.ticket_id = :ticketId
                            ORDER BY COALESCE(tr.collected_at, now()) DESC, tr.test_run_id DESC
                            LIMIT 1
                        ),
                        latest_test_case AS (
                            SELECT
                                tc.test_case_id,
                                tc.status
                            FROM tbl_fact_test_case tc
                            JOIN latest_test_run l ON l.test_run_id = tc.test_run_id
                        )
                        SELECT
                            COALESCE((SELECT count(*) FROM latest_test_run), 0) AS test_run_count,
                            COALESCE((SELECT count(*) FROM latest_test_case), 0) AS test_case_count,
                            COALESCE((SELECT count(*) FROM latest_test_case tc WHERE tc.status::text = 'SUCCESS'), 0) AS passed_count,
                            COALESCE((SELECT count(*) FROM latest_test_case tc WHERE tc.status::text = 'FAILED'), 0) AS failed_count,
                            COALESCE((SELECT count(*) FROM latest_test_case tc WHERE tc.status::text = 'SKIPPED'), 0) AS skipped_count,
                            COALESCE((SELECT count(ac.ac_test_coverage_id)
                                      FROM tbl_fact_ac_test_coverage ac
                                      JOIN latest_test_case tc ON tc.test_case_id = ac.test_case_id
                                      WHERE ac.ticket_id = :ticketId
                                        AND tc.status::text <> 'FAILED'), 0) AS ac_coverage_count,
                            COALESCE((SELECT count(DISTINCT ac.ac_key)
                                      FROM tbl_fact_ac_test_coverage ac
                                      JOIN latest_test_case tc ON tc.test_case_id = ac.test_case_id
                                      JOIN tbl_fact_acceptance_criteria criterion
                                        ON criterion.ticket_id = ac.ticket_id
                                       AND criterion.ac_key = ac.ac_key
                                       AND criterion.status = 'ACTIVE'
                                      WHERE ac.ticket_id = :ticketId
                                        AND tc.status::text <> 'FAILED'), 0) AS distinct_covered_ac_count,
                            COALESCE((SELECT bool_or(l.test_run_id IS NOT NULL)
                                      FROM latest_test_run l), FALSE) AS present_flag,
                            COALESCE((SELECT l.collected_at
                                      FROM latest_test_run l), now()) AS collected_at,
                            COALESCE((SELECT l.status::text
                                      FROM latest_test_run l), '') AS status,
                            (SELECT l.test_run_id FROM latest_test_run l) AS test_run_id
                        """,
                new MapSqlParameterSource("ticketId", ticketId),
                (rs, rowNum) -> new TestSignal(
                        rs.getInt("test_run_count"),
                        rs.getInt("test_case_count"),
                        rs.getInt("passed_count"),
                        rs.getInt("failed_count"),
                        rs.getInt("skipped_count"),
                        rs.getInt("ac_coverage_count"),
                        rs.getInt("distinct_covered_ac_count"),
                        rs.getString("status"),
                        rs.getBoolean("present_flag"),
                        rs.getObject("collected_at", OffsetDateTime.class),
                        rs.getObject("test_run_id", UUID.class) == null
                                ? List.of()
                                : List.of("tbl_fact_test_run:" + rs.getObject("test_run_id", UUID.class))));
        if (rows.isEmpty()) {
            return new TestSignal(0, 0, 0, 0, 0, 0, 0, null, false, null, List.of());
        }
        return rows.getFirst();
    }

    private TraceabilitySignal loadTraceabilitySignal(UUID ticketId) {
        List<TraceabilitySignal> rows = jdbc.query("""
                SELECT
                    COALESCE(count(*), 0) AS link_count,
                    COALESCE(count(DISTINCT target_type), 0) AS present_link_count,
                    COALESCE(bool_or(source_type = 'TICKET' AND target_type = 'SPEC_PACK'), FALSE) AS ticket_to_spec,
                    COALESCE(bool_or(source_type = 'SPEC_PACK' AND target_type = 'PULL_REQUEST'), FALSE) AS spec_to_pr,
                    COALESCE(bool_or(source_type = 'PULL_REQUEST' AND target_type = 'CI_RUN'), FALSE) AS pr_to_ci,
                    COALESCE(bool_or(source_type = 'CI_RUN' AND target_type = 'TEST_RUN'), FALSE) AS ci_to_test,
                    COALESCE(bool_or(source_type = 'TEST_RUN' AND target_type = 'REPORT'), FALSE) AS test_to_report,
                    COALESCE(max(created_at), now()) AS collected_at
                FROM tbl_fact_traceability_link
                WHERE ticket_id = :ticketId
                """,
                new MapSqlParameterSource("ticketId", ticketId),
                (rs, rowNum) -> {
                    List<String> types = new ArrayList<>();
                    if (rs.getBoolean("ticket_to_spec"))
                        types.add("ticket->spec");
                    if (rs.getBoolean("spec_to_pr"))
                        types.add("spec->pr");
                    if (rs.getBoolean("pr_to_ci"))
                        types.add("pr->ci");
                    if (rs.getBoolean("ci_to_test"))
                        types.add("ci->test");
                    if (rs.getBoolean("test_to_report"))
                        types.add("test->report");
                    int presentLinkCount = types.size();
                    return new TraceabilitySignal(
                            rs.getInt("link_count"),
                            5,
                            presentLinkCount,
                            presentLinkCount >= 4,
                            types,
                            List.of("tbl_fact_traceability_link:" + ticketId),
                            rs.getObject("collected_at", OffsetDateTime.class));
                });
        if (rows.isEmpty()) {
            return new TraceabilitySignal(0, 5, 0, false, List.of(), List.of(), null);
        }
        return rows.getFirst();
    }

    private OffsetDateTime findLatestSourceTimestamp(UUID ticketId) {
        List<OffsetDateTime> values = jdbc.query(
                """
                        SELECT max(ts) AS latest_source_at
                        FROM (
                            SELECT max(a.collected_at) AS ts FROM tbl_fact_artifact_snapshot a WHERE a.ticket_id = :ticketId
                            UNION ALL SELECT max(r.collected_at) AS ts FROM tbl_fact_review r WHERE r.ticket_id = :ticketId
                            UNION ALL SELECT max(c.collected_at) AS ts FROM tbl_fact_ci_run c WHERE c.ticket_id = :ticketId
                            UNION ALL SELECT max(t.collected_at) AS ts FROM tbl_fact_test_run t WHERE t.ticket_id = :ticketId
                            UNION ALL SELECT max(e.generated_at) AS ts FROM tbl_fact_evidence_report e WHERE e.ticket_id = :ticketId
                        ) source_times
                        """,
                new MapSqlParameterSource("ticketId", ticketId),
                (rs, rowNum) -> rs.getObject("latest_source_at", OffsetDateTime.class));
        return values.isEmpty() ? null : values.getFirst();
    }

    private TicketIdentity loadTicketIdentity(UUID ticketId) {
        List<TicketIdentity> rows = jdbc.query("""
                SELECT ticket_id, project_id
                FROM tbl_dim_ticket
                WHERE ticket_id = :ticketId
                LIMIT 1
                """,
                new MapSqlParameterSource("ticketId", ticketId),
                (rs, rowNum) -> new TicketIdentity(
                        rs.getObject("ticket_id", UUID.class),
                        rs.getObject("project_id", UUID.class),
                        null));
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("invalid_ticket_id");
        }
        return rows.getFirst();
    }

    private Optional<UUID> resolveRepositoryId(UUID ticketId) {
        List<UUID> repositoryIds = jdbc.query("""
                SELECT repository_id
                FROM tbl_fact_artifact_snapshot
                WHERE ticket_id = :ticketId
                ORDER BY collected_at DESC, created_at DESC
                LIMIT 1
                """,
                new MapSqlParameterSource("ticketId", ticketId),
                (rs, rowNum) -> rs.getObject("repository_id", UUID.class));
        if (!repositoryIds.isEmpty() && repositoryIds.getFirst() != null) {
            return Optional.of(repositoryIds.getFirst());
        }
        repositoryIds = jdbc.query("""
                SELECT repository_id
                FROM tbl_fact_ci_run
                WHERE ticket_id = :ticketId
                ORDER BY COALESCE(updated_at, created_at, collected_at, now()) DESC, ci_run_id DESC
                LIMIT 1
                """,
                new MapSqlParameterSource("ticketId", ticketId),
                (rs, rowNum) -> rs.getObject("repository_id", UUID.class));
        if (!repositoryIds.isEmpty() && repositoryIds.getFirst() != null) {
            return Optional.of(repositoryIds.getFirst());
        }
        return Optional.empty();
    }

    private String normalizeActor(String actor) {
        if (actor == null || actor.isBlank()) {
            return "SYSTEM";
        }
        return actor.trim();
    }

    private record TicketIdentity(UUID ticketId, UUID projectId, UUID repositoryId) {
    }

    private record AcceptanceCriteriaStats(int acCount, int acValidFormatCount) {
    }

    private static boolean hasAnyLink(CiSignal signal) {
        return signal != null && (notBlank(signal.ciUrl()) || notBlank(signal.externalRunId()));
    }

    private static boolean isFailedJob(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        return "FAILURE".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
