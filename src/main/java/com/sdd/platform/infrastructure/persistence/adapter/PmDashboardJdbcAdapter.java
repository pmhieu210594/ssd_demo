package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.PmDashboardRepositoryPort;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.DashboardFilter;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.DashboardRefreshResult;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.DashboardOption;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.DashboardOptions;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.DashboardPhaseOption;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.DashboardSummary;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.DashboardInsights;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.DashboardEvidenceBottleneckBucket;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.DashboardTicketDetail;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.DashboardTicketRow;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.ExceptionItem;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.MissingEvidenceItem;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.RiskItem;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.ScoreBreakdown;
import com.sdd.platform.application.usecase.traceability.TraceabilityModels;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PmDashboardJdbcAdapter implements PmDashboardRepositoryPort {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private record PhaseBottleneck(String phaseCode, String phaseName, long blockedCount) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public PmDashboardJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public DashboardRefreshResult rebuildSnapshot() {
        Long rows = jdbc.queryForObject("""
                        SELECT COUNT(*)
                        FROM tbl_fact_ticket_dashboard_snapshot
                        """,
                new MapSqlParameterSource(),
                Long.class);
        return new DashboardRefreshResult(rows == null ? 0L : rows, OffsetDateTime.now());
    }

    @Override
    public DashboardOptions findOptions(UUID projectId) {
        List<DashboardOption> projects = jdbc.query("""
                        SELECT DISTINCT project_id, project_alias
                        FROM tbl_fact_ticket_dashboard_snapshot
                        ORDER BY project_alias ASC, project_id ASC
                        """,
                new MapSqlParameterSource(),
                (rs, rowNum) -> new DashboardOption(
                        rs.getObject("project_id", UUID.class).toString(),
                        rs.getString("project_alias")));

        List<DashboardOption> periods = jdbc.query("""
                        SELECT DISTINCT period_key
                        FROM tbl_fact_ticket_dashboard_snapshot
                        WHERE period_key IS NOT NULL
                        ORDER BY period_key DESC
                        """,
                new MapSqlParameterSource(),
                (rs, rowNum) -> new DashboardOption(
                        rs.getString("period_key"),
                        rs.getString("period_key")));

        MapSqlParameterSource repositoryParams = new MapSqlParameterSource();
        StringBuilder repositorySql = new StringBuilder("""
                SELECT DISTINCT repository_id, repository_name
                FROM tbl_fact_ticket_dashboard_snapshot
                WHERE 1 = 1
                """);
        if (projectId != null) {
            repositorySql.append(" AND project_id = :projectId");
            repositoryParams.addValue("projectId", projectId);
        }
        repositorySql.append("""
                
                ORDER BY repository_name ASC, repository_id ASC
                """);
        List<DashboardOption> repositories = jdbc.query(repositorySql.toString(), repositoryParams,
                (rs, rowNum) -> new DashboardOption(
                        rs.getObject("repository_id", UUID.class).toString(),
                        rs.getString("repository_name")));

        List<DashboardPhaseOption> phases = jdbc.query("""
                        SELECT phase_code, phase_name, phase_order
                        FROM tbl_dim_phase
                        ORDER BY phase_order ASC, phase_code ASC
                        """,
                new MapSqlParameterSource(),
                (rs, rowNum) -> new DashboardPhaseOption(
                        rs.getString("phase_code"),
                        rs.getString("phase_name"),
                        rs.getInt("phase_order")));

        return new DashboardOptions(projects, periods, repositories, phases);
    }

    @Override
    public DashboardSummary findSummary(DashboardFilter filter) {
        long missingTraceabilitySectionTicketCount = countMissingTraceabilitySections(filter);
        StringBuilder sql = new StringBuilder("""
                SELECT
                    COUNT(*) FILTER (WHERE blocked_flag) AS blocked_ticket_count,
                    COUNT(*) FILTER (WHERE missing_evidence_count > 0) AS missing_evidence_ticket_count,
                    COALESCE(SUM(open_issue_count), 0) AS open_issue_count,
                    COUNT(*) FILTER (WHERE waiting_review_flag) AS waiting_review_ticket_count,
                    COUNT(*) FILTER (WHERE ci_failed_count > 0) AS ci_failed_ticket_count,
                    COUNT(*) FILTER (WHERE risk_count > 0) AS risk_ticket_count,
                    COUNT(*) FILTER (WHERE exception_count > 0) AS exception_ticket_count,
                    AVG(evidence_quality_score) AS average_evidence_quality_score,
                    MAX(refreshed_at) AS updated_at
                FROM tbl_fact_ticket_dashboard_snapshot
                WHERE 1 = 1
                """);
        MapSqlParameterSource params = filterParams(filter, sql);
        DashboardSummary result = jdbc.queryForObject(sql.toString(), params, (rs, rowNum) -> new DashboardSummary(
                rs.getLong("blocked_ticket_count"),
                rs.getLong("missing_evidence_ticket_count"),
                missingTraceabilitySectionTicketCount,
                rs.getLong("open_issue_count"),
                rs.getLong("waiting_review_ticket_count"),
                rs.getLong("ci_failed_ticket_count"),
                rs.getLong("risk_ticket_count"),
                rs.getLong("exception_ticket_count"),
                rs.getBigDecimal("average_evidence_quality_score"),
                null,
                null,
                null,
                0L,
                rs.getObject("updated_at", OffsetDateTime.class)
        ));
        if (result == null) {
            return new DashboardSummary(0, 0, 0, 0, 0, 0, 0, 0, BigDecimal.ZERO, null, null, null, 0L, null);
        }
        PhaseBottleneck bottleneck = findPhaseBottleneck(filter);
        return new DashboardSummary(
                result.blockedTicketCount(),
                result.missingEvidenceTicketCount(),
                result.missingTraceabilitySectionTicketCount(),
                result.openIssueCount(),
                result.waitingReviewTicketCount(),
                result.ciFailedTicketCount(),
                result.riskTicketCount(),
                result.exceptionTicketCount(),
                result.averageEvidenceQualityScore(),
                averageBand(result.averageEvidenceQualityScore()),
                bottleneck == null ? null : bottleneck.phaseCode(),
                bottleneck == null ? null : bottleneck.phaseName(),
                bottleneck == null ? 0L : bottleneck.blockedCount(),
                result.updatedAt()
        );
    }

    @Override
    public PageResult<DashboardTicketRow> findTickets(DashboardFilter filter) {
        int page = Math.max(filter.page(), 1);
        int size = normalizePageSize(filter.size());
        int offset = (page - 1) * size;
        List<DashboardTicketRow> items = findTickets(filter, offset, size);
        long totalElements = countTickets(filter);
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResult<>(items, page, size, totalElements, totalPages);
    }

    @Override
    public List<DashboardTicketRow> findAllTickets(DashboardFilter filter) {
        StringBuilder sql = baseTicketQuery();
        MapSqlParameterSource params = filterParams(filter, sql);
        sql.append("\n").append(orderByClause());
        return jdbc.query(sql.toString(), params, this::mapTicketRow);
    }

    @Override
    public DashboardInsights findInsights(DashboardFilter filter) {
        List<DashboardTicketRow> attentionItems = findAttentionTickets(filter, 6);
        List<DashboardEvidenceBottleneckBucket> evidenceBottleneckBuckets = findEvidenceBottleneckBuckets(filter, 6);
        return new DashboardInsights(attentionItems, evidenceBottleneckBuckets);
    }

    @Override
    public Optional<DashboardTicketDetail> findDetail(UUID ticketId) {
        List<DashboardTicketRow> rows = jdbc.query("""
                        SELECT
                            ticket_id, project_id, project_alias, repository_id, repository_name,
                            external_ticket_key, title, phase_id, phase_code, phase_name, phase_order,
                            blocked_flag, waiting_review_flag, missing_evidence_count,
                            COALESCE((
                                SELECT COUNT(*)
                                FROM tbl_fact_artifact_parsed_section p
                                WHERE p.ticket_id = ticket_id
                                  AND p.required_flag = TRUE
                                  AND (
                                      p.present_flag = FALSE
                                      OR p.valid_flag = FALSE
                                      OR (p.parse_warning IS NOT NULL AND BTRIM(p.parse_warning) <> '')
                                  )
                            ), 0) AS traceability_issue_count,
                            open_issue_count,
                            risk_count, exception_count, ci_failed_count, highest_risk_severity,
                            evidence_quality_score, score_band::text AS score_band, score_rule_version,
                            age_days, owner_display, period_key, updated_at_source, refreshed_at,
                            search_text
                        FROM tbl_fact_ticket_dashboard_snapshot
                        WHERE ticket_id = :ticketId
                        """,
                new MapSqlParameterSource("ticketId", ticketId),
                this::mapTicketRow);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        DashboardTicketRow row = rows.get(0);
        return Optional.of(new DashboardTicketDetail(
                row,
                findMissingEvidence(ticketId),
                findRisks(ticketId),
                findExceptions(ticketId),
                findScoreBreakdown(ticketId),
                "/traceability?ticketId=" + row.ticketId()
        ));
    }

    private List<DashboardTicketRow> findTickets(DashboardFilter filter, int offset, int size) {
        StringBuilder sql = baseTicketQuery();
        MapSqlParameterSource params = filterParams(filter, sql);
        sql.append("\n").append(orderByClause());
        sql.append(" LIMIT :limit OFFSET :offset");
        params.addValue("limit", size);
        params.addValue("offset", offset);
        return jdbc.query(sql.toString(), params, this::mapTicketRow);
    }

    private List<DashboardTicketRow> findAttentionTickets(DashboardFilter filter, int limit) {
        StringBuilder sql = baseTicketQuery();
        MapSqlParameterSource params = filterParams(filter, sql);
        sql.append("""
                
                AND (blocked_flag = TRUE OR waiting_review_flag = TRUE OR missing_evidence_count > 0)
                """);
        sql.append("""
                
                ORDER BY age_days DESC, blocked_flag DESC, waiting_review_flag DESC,
                         missing_evidence_count DESC, traceability_issue_count DESC,
                         external_ticket_key ASC
                LIMIT :limit
                """);
        params.addValue("limit", Math.max(1, limit));
        return jdbc.query(sql.toString(), params, this::mapTicketRow);
    }

    private List<DashboardEvidenceBottleneckBucket> findEvidenceBottleneckBuckets(DashboardFilter filter, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    COALESCE(repository_id::text, project_id::text) AS bucket_key,
                    COALESCE(repository_name, project_alias, COALESCE(repository_id::text, project_id::text)) AS bucket_name,
                    SUM(missing_evidence_count) AS missing_evidence_count,
                    MAX(age_days) AS max_age_days
                FROM tbl_fact_ticket_dashboard_snapshot
                WHERE missing_evidence_count > 0
                """);
        MapSqlParameterSource params = filterParams(filter, sql);
        sql.append("""
                
                GROUP BY COALESCE(repository_id::text, project_id::text),
                         COALESCE(repository_name, project_alias, COALESCE(repository_id::text, project_id::text))
                ORDER BY SUM(missing_evidence_count) DESC, MAX(age_days) DESC, bucket_name ASC
                LIMIT :limit
                """);
        params.addValue("limit", Math.max(1, limit));
        return jdbc.query(sql.toString(), params, (rs, rowNum) -> new DashboardEvidenceBottleneckBucket(
                rs.getString("bucket_key"),
                rs.getString("bucket_name"),
                rs.getLong("missing_evidence_count")
        ));
    }

    private long countTickets(DashboardFilter filter) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM tbl_fact_ticket_dashboard_snapshot
                WHERE 1 = 1
                """);
        MapSqlParameterSource params = filterParams(filter, sql);
        Long count = jdbc.queryForObject(sql.toString(), params, Long.class);
        return count == null ? 0L : count;
    }

    private PhaseBottleneck findPhaseBottleneck(DashboardFilter filter) {
        StringBuilder sql = new StringBuilder("""
                SELECT phase_code, phase_name, COUNT(*) AS blocked_count
                FROM tbl_fact_ticket_dashboard_snapshot
                WHERE blocked_flag = TRUE
                """);
        MapSqlParameterSource params = filterParams(filter, sql);
        sql.append("""

                GROUP BY phase_code, phase_name
                ORDER BY COUNT(*) DESC, phase_code ASC
                LIMIT 1
                """);
        List<PhaseBottleneck> rows = jdbc.query(sql.toString(), params, (rs, rowNum) -> new PhaseBottleneck(
                rs.getString("phase_code"),
                rs.getString("phase_name"),
                rs.getLong("blocked_count")
        ));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long countMissingTraceabilitySections(DashboardFilter filter) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(DISTINCT p.ticket_id)
                FROM tbl_fact_artifact_parsed_section p
                JOIN tbl_fact_ticket_dashboard_snapshot s ON s.ticket_id = p.ticket_id
                WHERE p.required_flag = TRUE
                  AND p.present_flag = FALSE
                """);
        MapSqlParameterSource params = filterParams(filter, sql);
        Long count = jdbc.queryForObject(sql.toString(), params, Long.class);
        return count == null ? 0L : count;
    }

    private StringBuilder baseTicketQuery() {
        return new StringBuilder("""
                SELECT
                    ticket_id, project_id, project_alias, repository_id, repository_name,
                    external_ticket_key, title, phase_id, phase_code, phase_name, phase_order,
                    blocked_flag, waiting_review_flag, missing_evidence_count,
                    COALESCE((
                        SELECT COUNT(*)
                        FROM tbl_fact_artifact_parsed_section p
                        WHERE p.ticket_id = ticket_id
                          AND p.required_flag = TRUE
                          AND (
                              p.present_flag = FALSE
                              OR p.valid_flag = FALSE
                              OR (p.parse_warning IS NOT NULL AND BTRIM(p.parse_warning) <> '')
                          )
                    ), 0) AS traceability_issue_count,
                    open_issue_count,
                    risk_count, exception_count, ci_failed_count, highest_risk_severity,
                    evidence_quality_score, score_band::text AS score_band, score_rule_version,
                    age_days, owner_display, period_key, updated_at_source, refreshed_at,
                    search_text
                FROM tbl_fact_ticket_dashboard_snapshot
                WHERE 1 = 1
                """);
    }

    private MapSqlParameterSource filterParams(DashboardFilter filter, StringBuilder sql) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (filter.projectId() != null) {
            sql.append(" AND project_id = :projectId");
            params.addValue("projectId", filter.projectId());
        }
        if (filter.periodKey() != null && !filter.periodKey().isBlank()) {
            sql.append(" AND period_key = :periodKey");
            params.addValue("periodKey", filter.periodKey().trim());
        }
        if (filter.repositoryId() != null) {
            sql.append(" AND repository_id = :repositoryId");
            params.addValue("repositoryId", filter.repositoryId());
        }
        if (filter.phaseCode() != null && !filter.phaseCode().isBlank()) {
            // Resolve the phase through the dimension table instead of matching the
            // snapshot text column directly. That keeps the filter stable even if the
            // read model changes how phase metadata is projected.
            sql.append(" AND phase_id IN (SELECT phase_id FROM tbl_dim_phase WHERE phase_code = :phaseCode)");
            params.addValue("phaseCode", filter.phaseCode().trim());
        }
        if (filter.scoreBand() != null && !filter.scoreBand().isBlank()) {
            sql.append(" AND score_band = CAST(:scoreBand AS score_band)");
            params.addValue("scoreBand", filter.scoreBand().trim().toUpperCase());
        }
        if (filter.riskLevel() != null && !filter.riskLevel().isBlank()) {
            sql.append(" AND highest_risk_severity = :riskLevel");
            params.addValue("riskLevel", filter.riskLevel().trim().toUpperCase());
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            sql.append("""
                     AND search_text ILIKE :searchPattern
                    """);
            params.addValue("searchPattern", "%" + filter.search().trim().toLowerCase() + "%");
        }
        return params;
    }

    private String orderByClause() {
        return """
                ORDER BY blocked_flag DESC, missing_evidence_count DESC, traceability_issue_count DESC,
                         evidence_quality_score ASC NULLS LAST, age_days DESC, external_ticket_key ASC
                """;
    }

    private DashboardTicketRow mapTicketRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new DashboardTicketRow(
                rs.getObject("ticket_id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("project_alias"),
                rs.getObject("repository_id", UUID.class),
                rs.getString("repository_name"),
                rs.getString("external_ticket_key"),
                rs.getString("title"),
                rs.getObject("phase_id", UUID.class),
                rs.getString("phase_code"),
                rs.getString("phase_name"),
                rs.getInt("phase_order"),
                rs.getBoolean("blocked_flag"),
                rs.getBoolean("waiting_review_flag"),
                rs.getInt("missing_evidence_count"),
                rs.getInt("traceability_issue_count"),
                rs.getInt("open_issue_count"),
                rs.getInt("risk_count"),
                rs.getInt("exception_count"),
                rs.getInt("ci_failed_count"),
                rs.getString("highest_risk_severity"),
                rs.getBigDecimal("evidence_quality_score"),
                rs.getString("score_band"),
                rs.getString("score_rule_version"),
                rs.getInt("age_days"),
                rs.getString("owner_display"),
                rs.getString("period_key"),
                rs.getObject("updated_at_source", OffsetDateTime.class),
                rs.getObject("refreshed_at", OffsetDateTime.class)
        );
    }

    private List<MissingEvidenceItem> findMissingEvidence(UUID ticketId) {
        return jdbc.query("""
                        SELECT
                            at.artifact_type_code,
                            at.artifact_name,
                            at.default_file_name,
                            s.source_path,
                            at.required_flag,
                            COALESCE(s.exists_flag, FALSE) AS exists_flag
                        FROM tbl_dim_artifact_type at
                        LEFT JOIN LATERAL (
                            SELECT a.source_path, a.exists_flag
                            FROM tbl_fact_artifact_snapshot a
                            WHERE a.ticket_id = :ticketId
                              AND a.artifact_type_id = at.artifact_type_id
                            ORDER BY a.collected_at DESC, a.artifact_snapshot_id DESC
                            LIMIT 1
                        ) s ON TRUE
                        WHERE at.required_flag = TRUE
                          AND at.artifact_type_code IN (:artifactCodes)
                        ORDER BY at.artifact_type_code
                        """,
                new MapSqlParameterSource()
                        .addValue("ticketId", ticketId)
                        .addValue("artifactCodes", TraceabilityModels.REQUIRED_ARTIFACT_CODES),
                (rs, rowNum) -> new MissingEvidenceItem(
                        rs.getString("artifact_type_code"),
                        rs.getString("artifact_name"),
                        rs.getString("default_file_name"),
                        rs.getString("source_path"),
                        rs.getBoolean("required_flag"),
                        rs.getBoolean("exists_flag")));
    }

    private List<RiskItem> findRisks(UUID ticketId) {
        return jdbc.query("""
                        SELECT risk_id, risk_key, risk_summary, severity::text AS severity, status,
                               mitigation_present, mitigation_summary
                        FROM tbl_fact_risk
                        WHERE ticket_id = :ticketId
                          AND status = 'OPEN'
                        ORDER BY severity DESC, updated_at DESC, risk_id DESC
                        """,
                new MapSqlParameterSource("ticketId", ticketId),
                (rs, rowNum) -> new RiskItem(
                        rs.getObject("risk_id", UUID.class),
                        rs.getString("risk_key"),
                        rs.getString("risk_summary"),
                        rs.getString("severity"),
                        rs.getString("status"),
                        rs.getBoolean("mitigation_present"),
                        rs.getString("mitigation_summary")));
    }

    private List<ExceptionItem> findExceptions(UUID ticketId) {
        return jdbc.query("""
                        SELECT exception_id, exception_type, reason, follow_up_status, approved, linked_report_path
                        FROM tbl_fact_exception
                        WHERE ticket_id = :ticketId
                          AND follow_up_status = 'OPEN'
                        ORDER BY updated_at DESC, exception_id DESC
                        """,
                new MapSqlParameterSource("ticketId", ticketId),
                (rs, rowNum) -> new ExceptionItem(
                        rs.getObject("exception_id", UUID.class),
                        rs.getString("exception_type"),
                        rs.getString("reason"),
                        rs.getString("follow_up_status"),
                        rs.getBoolean("approved"),
                        rs.getString("linked_report_path")));
    }

    private ScoreBreakdown findScoreBreakdown(UUID ticketId) {
        List<ScoreBreakdown> items = jdbc.query("""
                        SELECT spec_score, plan_score, review_score, self_review_score,
                               test_score, ci_score, blackbox_score, report_score
                        FROM tbl_fact_evidence_quality_score
                        WHERE ticket_id = :ticketId
                        ORDER BY calculated_at DESC, evidence_quality_score_id DESC
                        LIMIT 1
                        """,
                new MapSqlParameterSource("ticketId", ticketId),
                (rs, rowNum) -> new ScoreBreakdown(
                        rs.getBigDecimal("spec_score"),
                        rs.getBigDecimal("plan_score"),
                        rs.getBigDecimal("review_score"),
                        rs.getBigDecimal("self_review_score"),
                        rs.getBigDecimal("test_score"),
                        rs.getBigDecimal("ci_score"),
                        rs.getBigDecimal("blackbox_score"),
                        rs.getBigDecimal("report_score")));
        return items.isEmpty()
                ? new ScoreBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
                : items.get(0);
    }

    private int normalizePageSize(int size) {
        return Math.max(1, Math.min(size <= 0 ? DEFAULT_PAGE_SIZE : size, MAX_PAGE_SIZE));
    }

    private static String averageBand(BigDecimal averageScore) {
        if (averageScore == null) {
            return null;
        }
        double value = averageScore.doubleValue();
        if (value >= 90) return "EXCELLENT";
        if (value >= 75) return "GOOD";
        if (value >= 60) return "WARNING";
        if (value >= 40) return "RISKY";
        return "CRITICAL";
    }
}
