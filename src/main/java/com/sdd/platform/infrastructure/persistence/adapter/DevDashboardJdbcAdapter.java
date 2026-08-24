package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.DevDashboardRepositoryPort;
import com.sdd.platform.application.usecase.devdashboard.DevDashboardModels;
import com.sdd.platform.domain.model.AuthUserContext;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DevDashboardJdbcAdapter implements DevDashboardRepositoryPort {

    private final NamedParameterJdbcTemplate jdbc;
    private final DashboardProjectAccessJdbcAdapter projectAccess;

    public DevDashboardJdbcAdapter(NamedParameterJdbcTemplate jdbc, DashboardProjectAccessJdbcAdapter projectAccess) {
        this.jdbc = jdbc;
        this.projectAccess = projectAccess;
    }

    // ── Summary ───────────────────────────────────────────────────────────────

    @Override
    public DevDashboardModels.DevDashboardSummary findSummary(DevDashboardModels.DevDashboardFilter filter) {
        long ciFailureCount = countCiFailures(filter);
        long reviewCommentCount = countReviewComments(filter);
        long parserErrorCount = sumParseErrors(filter);
        return new DevDashboardModels.DevDashboardSummary(
                ciFailureCount,
                reviewCommentCount,
                parserErrorCount,
                OffsetDateTime.now()
        );
    }

    private long countCiFailures(DevDashboardModels.DevDashboardFilter filter) {
        var params = new MapSqlParameterSource();
        var sql = new StringBuilder("""
                SELECT COUNT(*) FROM (
                    SELECT DISTINCT ON (ci.ticket_id, COALESCE(ci.workflow_name, ci.ci_run_id::text))
                           ci.ticket_id, ci.workflow_name, ci.ci_run_id, ci.status
                    FROM tbl_fact_ci_run ci
                    ORDER BY ci.ticket_id, COALESCE(ci.workflow_name, ci.ci_run_id::text), ci.started_at DESC NULLS LAST, ci.ci_run_id DESC
                ) ci
                JOIN tbl_dim_ticket t ON t.ticket_id = ci.ticket_id
                WHERE ci.status = 'FAILURE'
                """);
        applyProjectFilter(sql, params, filter);
        applyRepositoryFilterViaCiRun(sql, params, filter);
        Long count = jdbc.queryForObject(sql.toString(), params, Long.class);
        return count == null ? 0L : count;
    }

    private long countReviewComments(DevDashboardModels.DevDashboardFilter filter) {
        var params = new MapSqlParameterSource();
        var sql = new StringBuilder("""
                SELECT COALESCE(COUNT(DISTINCT rc.ticket_id), 0) AS total_commented_tickets
                FROM tbl_fact_review_comment rc
                JOIN tbl_dim_ticket t ON t.ticket_id = rc.ticket_id
                WHERE rc.ticket_id IS NOT NULL
                """); 
        
        applyTicketFilters(sql, params, filter);
        Long count = jdbc.queryForObject(sql.toString(), params, Long.class);
        return count == null ? 0L : count;
    }

    private long sumParseErrors(DevDashboardModels.DevDashboardFilter filter) {
        var params = new MapSqlParameterSource();
        var sql = new StringBuilder("""
                SELECT COUNT(DISTINCT t.ticket_id)
                FROM tbl_dim_ticket t
                JOIN tbl_dim_project p ON p.project_id = t.project_id
                WHERE (
                    EXISTS (
                        SELECT 1
                        FROM tbl_fact_data_quality dq
                        JOIN tbl_fact_artifact_snapshot snap
                            ON snap.repository_id = dq.repository_id
                           AND snap.source_path = dq.source_ref
                        WHERE snap.ticket_id = t.ticket_id
                          AND (dq.parse_error_count > 0
                          OR dq.missing_count > 0
                          OR dq.schema_violation_count > 0)
                    )
                    OR EXISTS (
                        SELECT 1 FROM (VALUES
                            ('SPEC_PACK'), ('IMPL_PLAN'), ('REVIEW_CHECKLIST'), ('SELF_REVIEW'),
                            ('TEST_PLAN'), ('TEST_RESULTS'), ('REPORT')
                        ) AS req(code)
                        WHERE NOT EXISTS (
                            SELECT 1 FROM tbl_fact_artifact_snapshot asnap
                            JOIN tbl_dim_artifact_type atype ON atype.artifact_type_id = asnap.artifact_type_id
                            WHERE asnap.ticket_id = t.ticket_id
                              AND atype.artifact_type_code = req.code
                              AND asnap.exists_flag = true
                        )
                    )
                )
                """);
        applyTicketFilters(sql, params, filter);
        Long sum = jdbc.queryForObject(sql.toString(), params, Long.class);
        return sum == null ? 0L : sum;
    }

    // ── Ticket List ───────────────────────────────────────────────────────────

    @Override
    public DevDashboardModels.DevDashboardPage findTickets(DevDashboardModels.DevDashboardFilter filter) {
        var params = new MapSqlParameterSource();
        int pageSize = Math.min(filter.size(), 100);
        int offset = (Math.max(filter.page(), 1) - 1) * pageSize;

        var countSql = buildTicketCountSql(filter, params);
        Long total = jdbc.queryForObject(countSql.toString(), params, Long.class);
        long totalElements = total == null ? 0L : total;
        int totalPages = pageSize == 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);

        if (totalElements == 0) {
            return new DevDashboardModels.DevDashboardPage(List.of(), filter.page(), pageSize, 0L, 0);
        }

        var dataSql = buildTicketDataSql(filter, params);
        dataSql.append(" ORDER BY t.external_ticket_key ASC");
        dataSql.append(" LIMIT :pageSize OFFSET :offset");
        params.addValue("pageSize", pageSize).addValue("offset", offset);

        List<DevDashboardModels.DevTicketRow> items = jdbc.query(dataSql.toString(), params,
                (rs, rowNum) -> new DevDashboardModels.DevTicketRow(
                        UUID.fromString(rs.getString("ticket_id")),
                        UUID.fromString(rs.getString("project_id")),
                        rs.getString("project_alias"),
                        rs.getString("external_ticket_key"),
                        rs.getString("title"),
                        rs.getString("ticket_status"),
                        rs.getLong("ci_fail_count"),
                        rs.getString("latest_ci_status"),
                        rs.getLong("open_finding_count"),
                        rs.getInt("review_round_count"),
                        rs.getLong("review_comment_count"),
                        rs.getBoolean("parser_error_flag"),
                        rs.getInt("age_days"),
                        rs.getObject("artifact_version", Integer.class)
                ));

        return new DevDashboardModels.DevDashboardPage(items, filter.page(), pageSize, totalElements, totalPages);
    }

    @Override
    public List<DevDashboardModels.DevTicketRow> findAllTickets(DevDashboardModels.DevDashboardFilter filter) {
        var params = new MapSqlParameterSource();
        var sql = buildTicketDataSql(filter, params);
        sql.append(" ORDER BY t.external_ticket_key ASC");
        return jdbc.query(sql.toString(), params,
                (rs, rowNum) -> new DevDashboardModels.DevTicketRow(
                        UUID.fromString(rs.getString("ticket_id")),
                        UUID.fromString(rs.getString("project_id")),
                        rs.getString("project_alias"),
                        rs.getString("external_ticket_key"),
                        rs.getString("title"),
                        rs.getString("ticket_status"),
                        rs.getLong("ci_fail_count"),
                        rs.getString("latest_ci_status"),
                        rs.getLong("open_finding_count"),
                        rs.getInt("review_round_count"),
                        rs.getLong("review_comment_count"),
                        rs.getBoolean("parser_error_flag"),
                        rs.getInt("age_days"),
                        rs.getObject("artifact_version", Integer.class)
                ));
    }

    private StringBuilder buildTicketCountSql(DevDashboardModels.DevDashboardFilter filter, MapSqlParameterSource params) {
        var sql = new StringBuilder("""
                SELECT COUNT(DISTINCT t.ticket_id)
                FROM tbl_dim_ticket t
                JOIN tbl_dim_project p ON p.project_id = t.project_id
                WHERE 1=1
                """);
        applyTicketFilters(sql, params, filter);
        return sql;
    }

    private StringBuilder buildTicketDataSql(DevDashboardModels.DevDashboardFilter filter, MapSqlParameterSource params) {
        var sql = new StringBuilder("""
                SELECT
                    t.ticket_id::text,
                    t.project_id::text,
                    p.project_alias,
                    t.external_ticket_key,
                    t.title,
                    CAST(t.status AS VARCHAR) AS ticket_status,
                    (SELECT COUNT(DISTINCT (ci.ticket_id, COALESCE(ci.workflow_name, ci.ci_run_id::text)))
                     FROM tbl_fact_ci_run ci
                     WHERE ci.ticket_id = t.ticket_id AND ci.status = 'FAILURE') AS ci_fail_count,
                    (SELECT x.status FROM (
                        SELECT DISTINCT ON (COALESCE(workflow_name, ci_run_id::text))
                               status, started_at
                        FROM tbl_fact_ci_run
                        WHERE ticket_id = t.ticket_id
                        ORDER BY COALESCE(workflow_name, ci_run_id::text), started_at DESC NULLS LAST, ci_run_id DESC
                    ) x
                    ORDER BY x.started_at DESC NULLS LAST
                    LIMIT 1) AS latest_ci_status,
                    (SELECT COUNT(*) FROM tbl_fact_finding f
                     WHERE f.ticket_id = t.ticket_id AND f.status = 'OPEN') AS open_finding_count,
                    (SELECT COUNT(*) FROM tbl_fact_review r
                     WHERE r.ticket_id = t.ticket_id) AS review_round_count,
                    (SELECT COUNT(*) FROM tbl_fact_review_comment rc
                     WHERE rc.ticket_id = t.ticket_id) AS review_comment_count,
                    (
                        EXISTS (
                            SELECT 1 FROM tbl_fact_data_quality dq
                            JOIN tbl_fact_artifact_snapshot snap
                                ON snap.repository_id = dq.repository_id
                               AND snap.source_path = dq.source_ref
                            WHERE snap.ticket_id = t.ticket_id
                            AND (dq.parse_error_count > 0 OR dq.missing_count > 0 OR dq.schema_violation_count > 0)
                        )
                        OR EXISTS (
                            SELECT 1 FROM (VALUES
                                ('SPEC_PACK'), ('IMPL_PLAN'), ('REVIEW_CHECKLIST'), ('SELF_REVIEW'),
                                ('TEST_PLAN'), ('TEST_RESULTS'), ('REPORT')
                            ) AS req(code)
                            WHERE NOT EXISTS (
                                SELECT 1 FROM tbl_fact_artifact_snapshot asnap
                                JOIN tbl_dim_artifact_type atype ON atype.artifact_type_id = asnap.artifact_type_id
                                WHERE asnap.ticket_id = t.ticket_id
                                  AND atype.artifact_type_code = req.code
                                  AND asnap.exists_flag = true
                            )
                        )
                    ) AS parser_error_flag,
                    COALESCE(EXTRACT(DAY FROM now() - t.created_at)::INT, 0) AS age_days,
                    (SELECT MAX(a.schema_version) FROM tbl_fact_artifact_snapshot a WHERE a.ticket_id = t.ticket_id) AS artifact_version
                FROM tbl_dim_ticket t
                JOIN tbl_dim_project p ON p.project_id = t.project_id
                WHERE 1=1
                """);
        applyTicketFilters(sql, params, filter);
        return sql;
    }

    private void applyTicketFilters(StringBuilder sql, MapSqlParameterSource params, DevDashboardModels.DevDashboardFilter filter) {
        applyProjectFilter(sql, params, filter);
        if (filter.repositoryId() != null) {
            params.addValue("repositoryId", filter.repositoryId());
            sql.append("""
                     AND t.ticket_id IN (
                         SELECT DISTINCT ticket_id FROM tbl_fact_artifact_snapshot
                         WHERE repository_id = :repositoryId AND ticket_id IS NOT NULL
                     )
                    """);
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            params.addValue("search", "%" + filter.search().trim() + "%");
            sql.append(" AND (t.external_ticket_key ILIKE :search OR t.title ILIKE :search)");
        }
        if ("FAIL".equalsIgnoreCase(filter.ciStatus())) {
            sql.append("""
                     AND t.ticket_id IN (
                         SELECT DISTINCT ci.ticket_id FROM (
                             SELECT DISTINCT ON (ticket_id, COALESCE(workflow_name, ci_run_id::text))
                                    ticket_id, status
                             FROM tbl_fact_ci_run
                             ORDER BY ticket_id, COALESCE(workflow_name, ci_run_id::text), started_at DESC NULLS LAST, ci_run_id DESC
                         ) ci
                         WHERE ci.status = 'FAILURE'
                     )
                    """);
        } else if ("PASS".equalsIgnoreCase(filter.ciStatus())) {
            sql.append("""
                     AND t.ticket_id NOT IN (
                         SELECT DISTINCT ci.ticket_id FROM (
                             SELECT DISTINCT ON (ticket_id, COALESCE(workflow_name, ci_run_id::text))
                                    ticket_id, status
                             FROM tbl_fact_ci_run
                             ORDER BY ticket_id, COALESCE(workflow_name, ci_run_id::text), started_at DESC NULLS LAST, ci_run_id DESC
                         ) ci
                         WHERE ci.status = 'FAILURE'
                     )
                    """);
        }
        if ("OPEN".equalsIgnoreCase(filter.reviewStatus())) {
            sql.append("""
                     AND EXISTS (
                         SELECT 1 FROM tbl_fact_finding f
                         WHERE f.ticket_id = t.ticket_id AND f.status = 'OPEN'
                     )
                    """);
        } else if ("RESOLVED".equalsIgnoreCase(filter.reviewStatus())) {
            sql.append("""
                     AND NOT EXISTS (
                         SELECT 1 FROM tbl_fact_finding f
                         WHERE f.ticket_id = t.ticket_id AND f.status = 'OPEN'
                     )
                    """);
        }
    }

    // ── Ticket Detail ─────────────────────────────────────────────────────────

    @Override
    public Optional<DevDashboardModels.DevTicketDetail> findDetail(UUID ticketId) {
        var params = new MapSqlParameterSource().addValue("ticketId", ticketId);

        List<DevDashboardModels.DevTicketRow> rows = jdbc.query("""
                SELECT
                    t.ticket_id::text,
                    t.project_id::text,
                    p.project_alias,
                    t.external_ticket_key,
                    t.title,
                    CAST(t.status AS VARCHAR) AS ticket_status,
                    (SELECT COUNT(DISTINCT (ci.ticket_id, COALESCE(ci.workflow_name, ci.ci_run_id::text)))
                     FROM tbl_fact_ci_run ci
                     WHERE ci.ticket_id = t.ticket_id AND ci.status = 'FAILURE') AS ci_fail_count,
                    (SELECT x.status FROM (
                        SELECT DISTINCT ON (COALESCE(workflow_name, ci_run_id::text))
                               status, started_at
                        FROM tbl_fact_ci_run
                        WHERE ticket_id = t.ticket_id
                        ORDER BY COALESCE(workflow_name, ci_run_id::text), started_at DESC NULLS LAST, ci_run_id DESC
                    ) x
                    ORDER BY x.started_at DESC NULLS LAST
                    LIMIT 1) AS latest_ci_status,
                    (SELECT COUNT(*) FROM tbl_fact_finding f
                     WHERE f.ticket_id = t.ticket_id AND f.status = 'OPEN') AS open_finding_count,
                    (SELECT COUNT(*) FROM tbl_fact_review r
                     WHERE r.ticket_id = t.ticket_id) AS review_round_count,
                    (SELECT COUNT(*) FROM tbl_fact_review_comment rc
                     WHERE rc.ticket_id = t.ticket_id) AS review_comment_count,
                    (
                        EXISTS (
                            SELECT 1 FROM tbl_fact_data_quality dq
                            JOIN tbl_fact_artifact_snapshot snap
                                ON snap.repository_id = dq.repository_id
                               AND snap.source_path = dq.source_ref
                            WHERE snap.ticket_id = t.ticket_id
                            AND (dq.parse_error_count > 0 OR dq.missing_count > 0 OR dq.schema_violation_count > 0)
                        )
                        OR EXISTS (
                            SELECT 1 FROM (VALUES
                                ('SPEC_PACK'), ('IMPL_PLAN'), ('REVIEW_CHECKLIST'), ('SELF_REVIEW'),
                                ('TEST_PLAN'), ('TEST_RESULTS'), ('REPORT')
                            ) AS req(code)
                            WHERE NOT EXISTS (
                                SELECT 1 FROM tbl_fact_artifact_snapshot asnap
                                JOIN tbl_dim_artifact_type atype ON atype.artifact_type_id = asnap.artifact_type_id
                                WHERE asnap.ticket_id = t.ticket_id
                                  AND atype.artifact_type_code = req.code
                                  AND asnap.exists_flag = true
                            )
                        )
                    ) AS parser_error_flag,
                    COALESCE(EXTRACT(DAY FROM now() - t.created_at)::INT, 0) AS age_days,
                    (SELECT MAX(a.schema_version) FROM tbl_fact_artifact_snapshot a WHERE a.ticket_id = t.ticket_id) AS artifact_version
                FROM tbl_dim_ticket t
                JOIN tbl_dim_project p ON p.project_id = t.project_id
                WHERE t.ticket_id = :ticketId
                """, params,
                (rs, rowNum) -> new DevDashboardModels.DevTicketRow(
                        UUID.fromString(rs.getString("ticket_id")),
                        UUID.fromString(rs.getString("project_id")),
                        rs.getString("project_alias"),
                        rs.getString("external_ticket_key"),
                        rs.getString("title"),
                        rs.getString("ticket_status"),
                        rs.getLong("ci_fail_count"),
                        rs.getString("latest_ci_status"),
                        rs.getLong("open_finding_count"),
                        rs.getInt("review_round_count"),
                        rs.getLong("review_comment_count"),
                        rs.getBoolean("parser_error_flag"),
                        rs.getInt("age_days"),
                        rs.getObject("artifact_version", Integer.class)
                ));

        if (rows.isEmpty()) {
            return Optional.empty();
        }
        DevDashboardModels.DevTicketRow ticketRow = rows.get(0);

        List<DevDashboardModels.DevCiRunItem> ciRuns = jdbc.query("""
                SELECT ci_run_id::text, workflow_name, CAST(status AS VARCHAR) AS status,
                       failure_category, ci_url, started_at
                FROM (
                    SELECT DISTINCT ON (COALESCE(workflow_name, ci_run_id::text))
                           ci_run_id, workflow_name, status, failure_category, ci_url, started_at
                    FROM tbl_fact_ci_run
                    WHERE ticket_id = :ticketId
                    ORDER BY COALESCE(workflow_name, ci_run_id::text), started_at DESC NULLS LAST, ci_run_id DESC
                ) ci
                ORDER BY ci.started_at DESC NULLS LAST
                LIMIT 20
                """, params,
                (rs, rowNum) -> new DevDashboardModels.DevCiRunItem(
                        UUID.fromString(rs.getString("ci_run_id")),
                        rs.getString("workflow_name"),
                        rs.getString("status"),
                        rs.getString("failure_category"),
                        rs.getString("ci_url"),
                        rs.getObject("started_at", OffsetDateTime.class)
                ));

        List<DevDashboardModels.DevFindingItem> findings = jdbc.query("""
                SELECT finding_id::text, CAST(severity AS VARCHAR) AS severity,
                       CAST(status AS VARCHAR) AS status, finding_summary
                FROM tbl_fact_finding
                WHERE ticket_id = :ticketId AND status = 'OPEN'
                ORDER BY detected_at DESC NULLS LAST
                LIMIT 50
                """, params,
                (rs, rowNum) -> new DevDashboardModels.DevFindingItem(
                        UUID.fromString(rs.getString("finding_id")),
                        rs.getString("severity"),
                        rs.getString("status"),
                        rs.getString("finding_summary")
                ));

        List<DevDashboardModels.DevReviewCommentItem> reviewComments = jdbc.query("""
                SELECT rc.review_comment_id::text, rc.file_path_hash, rc.line_number,
                       CAST(rc.severity AS VARCHAR) AS severity, rc.comment_summary, rc.resolved_flag,
                       (SELECT c.commit_url FROM tbl_fact_pull_request_commit prc
                        JOIN tbl_fact_commit c ON c.commit_id = prc.commit_id
                        WHERE prc.pr_id = rc.pr_id
                        ORDER BY c.committed_at DESC NULLS LAST LIMIT 1) AS commit_url,
                        r.state,
                        r.submitted_at,
                        r.submitted_by
                FROM tbl_fact_review_comment rc
                LEFT JOIN tbl_fact_review r
                    ON r.review_id = rc.review_id
                    AND r.pr_id = rc.pr_id
                    AND r.ticket_id = rc.ticket_id
                WHERE rc.ticket_id = :ticketId
                ORDER BY r.submitted_at DESC NULLS LAST
                LIMIT 50
                """, params,
                (rs, rowNum) -> new DevDashboardModels.DevReviewCommentItem(
                        UUID.fromString(rs.getString("review_comment_id")),
                        rs.getString("file_path_hash"),
                        (Integer) rs.getObject("line_number"),
                        rs.getString("severity"),
                        rs.getString("comment_summary"),
                        (Boolean) rs.getObject("resolved_flag"),
                        rs.getString("commit_url"),
                        rs.getString("state"),
                        rs.getString("submitted_at"),
                        rs.getString("submitted_by")
                ));

        var parserParams = new MapSqlParameterSource().addValue("ticketId", ticketId);
        DevDashboardModels.DevParserSummary parserSummary = jdbc.queryForObject("""
                SELECT
                    COALESCE(SUM(dq.parse_error_count), 0)       AS parse_error_count,
                    COALESCE(SUM(dq.schema_violation_count), 0)  AS schema_violation_count,
                    COALESCE(SUM(dq.missing_count), 0)           AS missing_count
                FROM tbl_fact_data_quality dq
                JOIN tbl_fact_artifact_snapshot snap
                    ON snap.repository_id = dq.repository_id
                   AND snap.source_path = dq.source_ref
                WHERE snap.ticket_id = :ticketId
                """, parserParams,
                (rs, rowNum) -> new DevDashboardModels.DevParserSummary(
                        rs.getLong("parse_error_count"),
                        rs.getLong("schema_violation_count"),
                        rs.getLong("missing_count")
                ));

        return Optional.of(new DevDashboardModels.DevTicketDetail(
                ticketRow,
                ciRuns,
                findings,
                reviewComments,
                parserSummary == null
                        ? new DevDashboardModels.DevParserSummary(0L, 0L, 0L)
                        : parserSummary
        ));
    }

    // ── Options ───────────────────────────────────────────────────────────────

    @Override
    public boolean hasDashboardAccess(AuthUserContext caller) {
        return projectAccess.hasDashboardRole(caller, "DEV");
    }

    @Override
    public String findProjectRole(AuthUserContext caller, UUID projectId) {
        return projectAccess.findProjectRole(caller, projectId);
    }

    @Override
    public DevDashboardModels.DevDashboardOptions findOptions(UUID projectId, AuthUserContext caller) {
        List<DevDashboardModels.DevDashboardOption> projects = projectAccess.findProjectOptions(caller).stream()
                .map(projectAccess::toDevOption)
                .toList();

        var repoParams = new MapSqlParameterSource();
        var repoSql = new StringBuilder("""
                SELECT repository_id::text AS value, repo_name_masked AS label
                FROM tbl_dim_repository
                WHERE status = 'ACTIVE'
                """);
        projectAccess.applyProjectScope(repoSql, repoParams, caller, "project_id");
        if (projectId != null) {
            repoParams.addValue("projectId", projectId);
            repoSql.append(" AND project_id = :projectId");
        }
        repoSql.append(" ORDER BY created_at DESC, repo_name_masked ASC, repository_id ASC");
        List<DevDashboardModels.DevDashboardOption> repositories = jdbc.query(
                repoSql.toString(), repoParams,
                (rs, rowNum) -> new DevDashboardModels.DevDashboardOption(
                        rs.getString("value"),
                        rs.getString("label"),
                        null
                ));

        return new DevDashboardModels.DevDashboardOptions(projects, repositories);
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private void applyProjectFilter(StringBuilder sql, MapSqlParameterSource params, DevDashboardModels.DevDashboardFilter filter) {
        if (filter.projectId() != null) {
            params.addValue("projectId", filter.projectId());
            sql.append(" AND t.project_id = :projectId");
        }
    }

    private void applyRepositoryFilterViaCiRun(StringBuilder sql, MapSqlParameterSource params, DevDashboardModels.DevDashboardFilter filter) {
        if (filter.repositoryId() != null) {
            params.addValue("repositoryId", filter.repositoryId());
            sql.append("""
                     AND ci.ticket_id IN (
                         SELECT DISTINCT ticket_id FROM tbl_fact_artifact_snapshot
                         WHERE repository_id = :repositoryId AND ticket_id IS NOT NULL
                     )
                    """);
        }
    }
}
