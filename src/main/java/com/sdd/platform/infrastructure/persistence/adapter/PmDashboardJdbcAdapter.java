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
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.DashboardIssueItem;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.DashboardEvidenceBottleneckBucket;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.DashboardTicketDetail;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.DashboardTicketRow;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.ExceptionItem;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.MissingEvidenceItem;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.PhaseDwellTimeItem;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.RiskItem;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.ScoreBreakdown;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.TemplateUsageRow;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.AiFindingStatsRow;
import com.sdd.platform.application.usecase.traceability.TraceabilityModels;
import com.sdd.platform.domain.model.AuthUserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOG = LoggerFactory.getLogger(PmDashboardJdbcAdapter.class);
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private record PhaseBottleneck(String phaseCode, String phaseName, long blockedCount) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final DashboardProjectAccessJdbcAdapter projectAccess;

    public PmDashboardJdbcAdapter(NamedParameterJdbcTemplate jdbc, DashboardProjectAccessJdbcAdapter projectAccess) {
        this.jdbc = jdbc;
        this.projectAccess = projectAccess;
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
    public boolean hasDashboardAccess(AuthUserContext caller) {
        return projectAccess.hasDashboardRole(caller, "PM");
    }

    @Override
    public DashboardOptions findOptions(UUID projectId, AuthUserContext caller) {
        List<DashboardOption> projects = projectAccess.findProjectOptions(caller).stream()
                .map(projectAccess::toPmOption)
                .toList();

        List<DashboardOption> periods = jdbc.query("""
                SELECT DISTINCT period_key
                FROM tbl_fact_ticket_dashboard_snapshot
                WHERE period_key IS NOT NULL
                ORDER BY period_key DESC
                """,
                new MapSqlParameterSource(),
                (rs, rowNum) -> new DashboardOption(
                        rs.getString("period_key"),
                        rs.getString("period_key"),
                        null));

        MapSqlParameterSource repositoryParams = new MapSqlParameterSource();
        StringBuilder repositorySql = new StringBuilder("""
                SELECT
                    r.repository_id,
                    r.repo_name_masked AS repository_name,
                    r.created_at AS repository_created_at
                FROM tbl_dim_repository r
                WHERE r.status = 'ACTIVE'
                """);
        projectAccess.applyProjectScope(repositorySql, repositoryParams, caller, "r.project_id");
        if (projectId != null) {
            repositorySql.append(" AND r.project_id = :projectId");
            repositoryParams.addValue("projectId", projectId);
        }
        repositorySql.append("""

                ORDER BY r.created_at DESC, r.repo_name_masked ASC, r.repository_id ASC
                """);
        List<DashboardOption> repositories = jdbc.query(repositorySql.toString(), repositoryParams,
                (rs, rowNum) -> new DashboardOption(
                        rs.getObject("repository_id", UUID.class).toString(),
                        rs.getString("repository_name"),
                        null));

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
    public String findProjectRole(AuthUserContext caller, UUID projectId) {
        return projectAccess.findProjectRole(caller, projectId);
    }

    @Override
    public DashboardSummary findSummary(DashboardFilter filter) {
        long missingTraceabilitySectionTicketCount = countMissingTraceabilitySections(filter);
        long openIssueTicketCount = countOpenIssueTickets(filter);
        FirstCiPassStats firstCiPassStats = countFirstCiPassStats(filter);

        StringBuilder sql = new StringBuilder("""
                SELECT
                    COUNT(*) FILTER (WHERE s.blocked_flag) AS blocked_ticket_count,
                    COUNT(*) FILTER (WHERE s.missing_evidence_count > 0) AS missing_evidence_ticket_count,
                    COUNT(*) FILTER (WHERE s.waiting_review_flag) AS waiting_review_ticket_count,
                    COUNT(*) FILTER (WHERE s.ci_failed_count > 0) AS ci_failed_ticket_count,
                    COUNT(*) FILTER (WHERE s.risk_count > 0) AS risk_ticket_count,
                    COUNT(*) FILTER (WHERE s.exception_count > 0) AS exception_ticket_count,
                    AVG(s.evidence_quality_score) AS average_evidence_quality_score,
                    MAX(s.refreshed_at) AS updated_at
                FROM tbl_fact_ticket_dashboard_snapshot s
                WHERE 1 = 1
                """);

        MapSqlParameterSource params = filterParams(filter, sql);
        DashboardSummary result = jdbc.queryForObject(sql.toString(), params, (rs, rowNum) -> new DashboardSummary(
                rs.getLong("blocked_ticket_count"),
                rs.getLong("missing_evidence_ticket_count"),
                missingTraceabilitySectionTicketCount,
                openIssueTicketCount,
                rs.getLong("waiting_review_ticket_count"),
                rs.getLong("ci_failed_ticket_count"),
                firstCiPassStats.firstCiPassTicketCount(),
                firstCiPassStats.ticketWithCiCount(),
                rs.getLong("risk_ticket_count"),
                rs.getLong("exception_ticket_count"),
                rs.getBigDecimal("average_evidence_quality_score"),
                null,
                null,
                0L,
                rs.getObject("updated_at", OffsetDateTime.class)));

        if (result == null) {
            return new DashboardSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, BigDecimal.ZERO, null, null, 0L, null);
        }

        PhaseBottleneck bottleneck = findPhaseBottleneck(filter);
        return new DashboardSummary(
                result.blockedTicketCount(),
                result.missingEvidenceTicketCount(),
                result.missingTraceabilitySectionTicketCount(),
                result.openIssueCount(),
                result.waitingReviewTicketCount(),
                result.ciFailedTicketCount(),
                result.firstCiPassTicketCount(),
                result.ticketWithCiCount(),
                result.riskTicketCount(),
                result.exceptionTicketCount(),
                result.averageEvidenceQualityScore(),
                bottleneck == null ? null : bottleneck.phaseCode(),
                bottleneck == null ? null : bottleneck.phaseName(),
                bottleneck == null ? 0L : bottleneck.blockedCount(),
                result.updatedAt());
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
        List<DashboardTicketRow> rows = jdbc.query(
                """
                        SELECT
                            s.ticket_id, s.project_id, s.project_alias, s.repository_id, s.repository_name,
                            s.external_ticket_key, s.title, s.status AS ticket_status, t.created_at AS ticket_created_at,
                            s.phase_id, s.phase_code, ph.phase_name, ph.description AS phase_description, tps.created_at AS phase_created_at, ph.phase_order,
                            s.blocked_flag, s.waiting_review_flag, s.missing_evidence_count,
                            COALESCE((
                                SELECT COUNT(*)
                                FROM tbl_fact_artifact_parsed_section p
                                WHERE p.ticket_id = s.ticket_id
                                  AND p.required_flag = TRUE
                                  AND (
                                      p.present_flag = FALSE
                                      OR p.valid_flag = FALSE
                                      OR (p.parse_warning IS NOT NULL AND BTRIM(p.parse_warning) <> '')
                                  )
                            ), 0) AS traceability_issue_count,
                            s.open_issue_count,
                            s.risk_count, s.exception_count, s.ci_failed_count, s.highest_risk_severity,
                            s.evidence_quality_score, s.score_rule_version,
                            s.age_days, s.owner_display, s.period_key, t.updated_at AS updated_at_source, s.refreshed_at,
                            s.search_text,
                            (SELECT MAX(a.schema_version) FROM tbl_fact_artifact_snapshot a WHERE a.ticket_id = s.ticket_id) AS artifact_version,
                            (
                                SELECT pr.merged_at
                                FROM tbl_fact_pull_request pr
                                WHERE pr.ticket_id = s.ticket_id
                                ORDER BY pr.opened_at DESC NULLS LAST, pr.collected_at DESC, pr.updated_at DESC, pr.pr_id DESC
                                LIMIT 1
                            ) AS merged_at,
                            t.started_at, t.completed_at
                        FROM tbl_fact_ticket_dashboard_snapshot s
                        JOIN tbl_dim_ticket t ON t.ticket_id = s.ticket_id
                        LEFT JOIN tbl_fact_ticket_phase_status tps ON tps.ticket_id = s.ticket_id
                        LEFT JOIN tbl_dim_phase ph ON ph.phase_id = s.phase_id
                        WHERE s.ticket_id = :ticketId
                        """,
                new MapSqlParameterSource("ticketId", ticketId),
                this::mapTicketRow);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        DashboardTicketRow row = rows.get(0);
        return Optional.of(new DashboardTicketDetail(
                row,
                row.createdAt(),
                row.ownerDisplay(),
                countReviews(ticketId),
                findMissingEvidence(ticketId),
                findRisks(ticketId),
                findExceptions(ticketId),
                findIssueItems(ticketId),
                findScoreBreakdown(ticketId),
                "/traceability?ticketId=" + row.ticketId(),
                findPhaseDwellTime(ticketId)));
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
        StringBuilder sql = new StringBuilder(
                """
                        SELECT
                            COALESCE(s.repository_id::text, s.project_id::text) AS bucket_key,
                            COALESCE(s.repository_name, s.project_alias, COALESCE(s.repository_id::text, s.project_id::text)) AS bucket_name,
                            SUM(s.missing_evidence_count) AS missing_evidence_count,
                            MAX(s.age_days) AS max_age_days
                        FROM tbl_fact_ticket_dashboard_snapshot s
                        WHERE s.missing_evidence_count > 0
                        """);
        MapSqlParameterSource params = filterParams(filter, sql);
        sql.append(
                """

                        GROUP BY COALESCE(s.repository_id::text, s.project_id::text),
                                 COALESCE(s.repository_name, s.project_alias, COALESCE(s.repository_id::text, s.project_id::text))
                        ORDER BY SUM(s.missing_evidence_count) DESC, MAX(s.age_days) DESC, bucket_name ASC
                        LIMIT :limit
                        """);
        params.addValue("limit", Math.max(1, limit));
        return jdbc.query(sql.toString(), params, (rs, rowNum) -> new DashboardEvidenceBottleneckBucket(
                rs.getString("bucket_key"),
                rs.getString("bucket_name"),
                rs.getLong("missing_evidence_count")));
    }

    private long countTickets(DashboardFilter filter) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM tbl_fact_ticket_dashboard_snapshot s
                WHERE 1 = 1
                """);
        MapSqlParameterSource params = filterParams(filter, sql);
        Long count = jdbc.queryForObject(sql.toString(), params, Long.class);
        return count == null ? 0L : count;
    }

    private PhaseBottleneck findPhaseBottleneck(DashboardFilter filter) {
        StringBuilder sql = new StringBuilder("""
                SELECT s.phase_code, s.phase_name, COUNT(*) AS blocked_count
                FROM tbl_fact_ticket_dashboard_snapshot s
                WHERE s.blocked_flag = TRUE
                """);
        MapSqlParameterSource params = filterParams(filter, sql);
        sql.append("""

                GROUP BY s.phase_code, s.phase_name
                ORDER BY COUNT(*) DESC, s.phase_code ASC
                LIMIT 1
                """);
        List<PhaseBottleneck> rows = jdbc.query(sql.toString(), params, (rs, rowNum) -> new PhaseBottleneck(
                rs.getString("phase_code"),
                rs.getString("phase_name"),
                rs.getLong("blocked_count")));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long countMissingTraceabilitySections(DashboardFilter filter) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(DISTINCT s.ticket_id)
                FROM tbl_fact_ticket_dashboard_snapshot s
                WHERE 1 = 1
                  AND EXISTS (
                    SELECT 1
                    FROM tbl_fact_artifact_parsed_section p
                    WHERE p.ticket_id = s.ticket_id
                      AND p.required_flag = TRUE
                      AND (
                          p.present_flag = FALSE
                          OR p.valid_flag = FALSE
                          
                          OR (p.parse_warning IS NOT NULL AND BTRIM(p.parse_warning) <> '')
                      )
                  )
                """);
        MapSqlParameterSource params = filterParams(filter, sql);
        Long count = jdbc.queryForObject(sql.toString(), params, Long.class);
        return count == null ? 0L : count;
    }

    private long countOpenIssueTickets(DashboardFilter filter) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(DISTINCT s.ticket_id)
                FROM tbl_fact_ticket_dashboard_snapshot s
                WHERE 1 = 1
                  AND EXISTS (
                    SELECT 1
                    FROM tbl_fact_ticket_issue ti
                    WHERE ti.ticket_id = s.ticket_id
                      AND ti.source_type IN ('SPEC_PACK', 'REPORT')
                  )
                """);
        MapSqlParameterSource params = filterParams(filter, sql);
        Long count = jdbc.queryForObject(sql.toString(), params, Long.class);
        return count == null ? 0L : count;
    }

    private StringBuilder baseTicketQuery() {
        return new StringBuilder(
                """
                        SELECT
                            s.ticket_id, s.project_id, s.project_alias, s.repository_id, s.repository_name,
                            s.external_ticket_key, s.title, s.status AS ticket_status, t.created_at AS ticket_created_at,
                            s.phase_id, s.phase_code, ph.phase_name, ph.description AS phase_description, tps.created_at AS phase_created_at, ph.phase_order,
                            s.blocked_flag, s.waiting_review_flag, s.missing_evidence_count,
                            COALESCE((
                                SELECT COUNT(*)
                                FROM tbl_fact_artifact_parsed_section p
                                WHERE p.ticket_id = s.ticket_id
                                  AND p.required_flag = TRUE
                                  AND (
                                      p.present_flag = FALSE
                                      OR p.valid_flag = FALSE
                                      OR (p.parse_warning IS NOT NULL AND BTRIM(p.parse_warning) <> '')
                                  )
                            ), 0) AS traceability_issue_count,
                            s.open_issue_count,
                            s.risk_count, s.exception_count, s.ci_failed_count, s.highest_risk_severity,
                            s.evidence_quality_score, s.score_rule_version,
                            s.age_days, s.owner_display, s.period_key, t.updated_at AS updated_at_source, s.refreshed_at,
                            s.search_text,
                            (SELECT MAX(a.schema_version) FROM tbl_fact_artifact_snapshot a WHERE a.ticket_id = s.ticket_id) AS artifact_version,
                            (
                                SELECT pr.merged_at
                                FROM tbl_fact_pull_request pr
                                WHERE pr.ticket_id = s.ticket_id
                                ORDER BY pr.opened_at DESC NULLS LAST, pr.collected_at DESC, pr.updated_at DESC, pr.pr_id DESC
                                LIMIT 1
                            ) AS merged_at,
                            t.started_at, t.completed_at
                        FROM tbl_fact_ticket_dashboard_snapshot s
                        JOIN tbl_dim_ticket t ON t.ticket_id = s.ticket_id
                        LEFT JOIN tbl_fact_ticket_phase_status tps ON tps.ticket_id = s.ticket_id
                        LEFT JOIN tbl_dim_phase ph ON ph.phase_id = s.phase_id
                        WHERE 1 = 1
                        """);
    }

    private MapSqlParameterSource filterParams(DashboardFilter filter, StringBuilder sql) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (filter.projectId() != null) {
            sql.append(" AND s.project_id = :projectId");
            params.addValue("projectId", filter.projectId());
        }
        if (filter.periodKey() != null && !filter.periodKey().isBlank()) {
            sql.append(" AND s.period_key = :periodKey");
            params.addValue("periodKey", filter.periodKey().trim());
        }
        if (filter.repositoryId() != null) {
            sql.append(" AND s.repository_id = :repositoryId");
            params.addValue("repositoryId", filter.repositoryId());
        }
        if (filter.phaseCode() != null && !filter.phaseCode().isBlank()) {
            sql.append(" AND s.phase_id IN (SELECT phase_id FROM tbl_dim_phase WHERE phase_code = :phaseCode)");
            params.addValue("phaseCode", filter.phaseCode().trim());
        }
        if (filter.riskLevel() != null && !filter.riskLevel().isBlank()) {
            sql.append(" AND s.highest_risk_severity = :riskLevel");
            params.addValue("riskLevel", filter.riskLevel().trim().toUpperCase());
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            sql.append("""
                     AND s.search_text ILIKE :searchPattern
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
                rs.getString("ticket_status"),
                rs.getObject("phase_id", UUID.class),
                rs.getString("phase_code"),
                rs.getString("phase_name"),
                rs.getString("phase_description"),
                rs.getObject("phase_created_at", OffsetDateTime.class),
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
                rs.getString("score_rule_version"),
                rs.getInt("age_days"),
                rs.getString("owner_display"),
                rs.getString("period_key"),
                rs.getObject("ticket_created_at", OffsetDateTime.class),
                rs.getObject("updated_at_source", OffsetDateTime.class),
                rs.getObject("refreshed_at", OffsetDateTime.class),
                rs.getObject("artifact_version", Integer.class),
                rs.getObject("merged_at", OffsetDateTime.class),
                rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("completed_at", OffsetDateTime.class));
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

    private List<PhaseDwellTimeItem> findPhaseDwellTime(UUID ticketId) {
        try {
            return jdbc.query(
                    """
                            SELECT ph.phase_code, ph.phase_order, ph.phase_name,
                                   SUM(EXTRACT(EPOCH FROM (d.document_update_at - d.document_create_at)))::float AS dwell_seconds
                            FROM tbl_dim_phase ph
                            LEFT JOIN tbl_dim_artifact_type at ON at.phase_id = ph.phase_id
                            LEFT JOIN tbl_fact_artifact_snapshot s ON s.artifact_type_id = at.artifact_type_id
                                AND s.ticket_id = :ticketId
                            LEFT JOIN tbl_fact_artifact_document_date d ON d.artifact_snapshot_id = s.artifact_snapshot_id
                                AND d.document_create_at IS NOT NULL
                                AND d.document_update_at IS NOT NULL
                                AND d.document_update_at >= d.document_create_at
                            WHERE ph.phase_code IN ('1', '3', '4', '5', '6', '7', '8')
                            GROUP BY ph.phase_code, ph.phase_order, ph.phase_name
                            ORDER BY ph.phase_order ASC
                            """,
                    new MapSqlParameterSource("ticketId", ticketId),
                    (rs, rowNum) -> {
                        String phaseCode = rs.getString("phase_code");
                        return new PhaseDwellTimeItem(
                                phaseCode,
                                rs.getInt("phase_order"),
                                rs.getString("phase_name"),
                                formatDwellTimeSeconds(ticketId, phaseCode, rs.getObject("dwell_seconds", Double.class)));
                    });
        } catch (RuntimeException ex) {
            LOG.warn("Failed to compute phase dwell time for ticket {}: {}", ticketId, ex.getMessage());
            return List.of();
        }
    }

    private String formatDwellTimeSeconds(UUID ticketId, String phaseCode, Double dwellSeconds) {
        if (dwellSeconds == null) {
            return null;
        }
        if (dwellSeconds < 0) {
            LOG.warn(
                    "Negative phase dwell time computed for ticketId={}, phaseCode={} (dwellSeconds={}); "
                            + "likely a reversed document_create_at/document_update_at header on one file — rendering as \"-\"",
                    ticketId, phaseCode, dwellSeconds);
            return null;
        }
        long totalSeconds = Math.round(dwellSeconds);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private int countReviews(UUID ticketId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM tbl_fact_review
                WHERE ticket_id = :ticketId
                """,
                new MapSqlParameterSource("ticketId", ticketId),
                Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<DashboardIssueItem> findIssueItems(UUID ticketId) {
        return jdbc.query("""
                SELECT
                    ticket_issue_id,
                    ticket_id,
                    repository_id,
                    source_type,
                    issue_order,
                    issue_key,
                    issue_title,
                    issue_impact,
                    issue_owner,
                    issue_status,
                    issue_summary,
                    source_path,
                    collected_at
                FROM tbl_fact_ticket_issue
                WHERE ticket_id = :ticketId
                ORDER BY
                    CASE source_type
                        WHEN 'SPEC_PACK' THEN 0
                        WHEN 'REPORT' THEN 1
                        ELSE 2
                    END,
                    issue_order ASC,
                    ticket_issue_id ASC
                """,
                new MapSqlParameterSource("ticketId", ticketId),
                (rs, rowNum) -> new DashboardIssueItem(
                        rs.getObject("ticket_issue_id", UUID.class),
                        rs.getObject("ticket_id", UUID.class),
                        rs.getObject("repository_id", UUID.class),
                        rs.getString("source_type"),
                        rs.getInt("issue_order"),
                        rs.getString("issue_key"),
                        rs.getString("issue_title"),
                        rs.getString("issue_impact"),
                        rs.getString("issue_owner"),
                        rs.getString("issue_status"),
                        rs.getString("issue_summary"),
                        rs.getString("source_path"),
                        rs.getObject("collected_at", OffsetDateTime.class)));
    }

    @Override
    public List<TemplateUsageRow> findTemplateUsage(UUID projectId, UUID repositoryId) {
        return jdbc.query("""
                SELECT
                    ph.phase_code,
                    ph.phase_name,
                    COALESCE(st.total_check_count, 0) AS total_check_count,
                    COALESCE(st.template_match_count, 0) AS template_match_count
                FROM tbl_dim_phase ph
                LEFT JOIN tbl_fact_template_usage_stat st
                    ON st.phase_id = ph.phase_id
                    AND st.project_id = :projectId
                    AND st.repository_id = :repositoryId
                WHERE ph.phase_code IN ('1','2','3','4','5','6','7','8')
                ORDER BY ph.phase_order ASC, ph.phase_code ASC
                """,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("repositoryId", repositoryId),
                (rs, rowNum) -> new TemplateUsageRow(
                        rs.getString("phase_code"),
                        rs.getString("phase_name"),
                        rs.getLong("total_check_count"),
                        rs.getLong("template_match_count")));
    }

    @Override
    public Optional<AiFindingStatsRow> findAiFindingStats(UUID projectId, UUID repositoryId) {
        return jdbc.query("""
                SELECT
                    r.repository_id,
                    r.repo_name_masked AS repository_name,
                    COALESCE(SUM(s.blocker_major_resolved_count), 0) AS blocker_major_resolved_sum,
                    COALESCE(SUM(s.blocker_major_total_count), 0) AS blocker_major_total_sum,
                    COALESCE(SUM(s.ai_review_adopted_count), 0) AS ai_review_adopted_sum,
                    COALESCE(SUM(s.ai_review_finding_total_count), 0) AS ai_review_finding_total_sum,
                    COALESCE(SUM(s.ai_review_valid_count), 0) AS ai_review_valid_sum,
                    COALESCE(SUM(s.ai_review_false_positive_count), 0) AS ai_review_false_positive_sum,
                    COALESCE(SUM(s.ai_review_resolved_count), 0) AS ai_review_resolved_sum
                FROM tbl_dim_repository r
                LEFT JOIN tbl_fact_ai_finding_stat s
                    ON s.repository_id = r.repository_id
                    AND s.project_id = :projectId
                WHERE r.repository_id = :repositoryId
                    AND r.project_id = :projectId
                GROUP BY r.repository_id, r.repo_name_masked
                """,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("repositoryId", repositoryId),
                (rs, rowNum) -> new AiFindingStatsRow(
                        rs.getObject("repository_id", UUID.class),
                        rs.getString("repository_name"),
                        rs.getLong("blocker_major_resolved_sum"),
                        rs.getLong("blocker_major_total_sum"),
                        rs.getLong("ai_review_adopted_sum"),
                        rs.getLong("ai_review_finding_total_sum"),
                        rs.getLong("ai_review_valid_sum"),
                        rs.getLong("ai_review_false_positive_sum"),
                        rs.getLong("ai_review_resolved_sum")))
                .stream().findFirst();
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

    private record FirstCiPassStats(long firstCiPassTicketCount, long ticketWithCiCount) {
    }

    private FirstCiPassStats countFirstCiPassStats(DashboardFilter filter) {
        StringBuilder scopeSql = new StringBuilder("""
                SELECT ticket_id
                FROM tbl_fact_ticket_dashboard_snapshot s
                WHERE 1 = 1
                """);
        MapSqlParameterSource params = filterParams(filter, scopeSql);

        String sql = """
                WITH scoped_tickets AS (
                """
                + scopeSql
                + """
                        ),
                        first_runs AS (
                            SELECT DISTINCT ON (c.ticket_id)
                                c.ticket_id,
                                c.status,
                                c.started_at,
                                c.finished_at,
                                c.collected_at,
                                c.ci_run_id
                            FROM tbl_fact_ci_run c
                            JOIN scoped_tickets s ON s.ticket_id = c.ticket_id
                            ORDER BY
                                c.ticket_id,
                                CASE WHEN c.first_run_flag THEN 0 ELSE 1 END,
                                c.started_at ASC NULLS LAST,
                                c.finished_at ASC NULLS LAST,
                                c.collected_at ASC,
                                c.ci_run_id ASC
                        )
                        SELECT
                            COUNT(*) FILTER (WHERE fr.status::text IN ('PASSED', 'SUCCESS')) AS first_ci_pass_ticket_count,
                            COUNT(*) AS ticket_with_ci_count
                        FROM first_runs fr
                        """;

        return jdbc.queryForObject(sql, params, (rs, rowNum) -> new FirstCiPassStats(
                rs.getLong("first_ci_pass_ticket_count"),
                rs.getLong("ticket_with_ci_count")));
    }

    private int normalizePageSize(int size) {
        return Math.max(1, Math.min(size <= 0 ? DEFAULT_PAGE_SIZE : size, MAX_PAGE_SIZE));
    }

}
