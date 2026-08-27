package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.QaDashboardRepositoryPort;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.AcCoverageCounts;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.AcCoveragePage;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.AcCoverageRow;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaAcTicketPage;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaAcTicketRow;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaDashboardOption;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaDashboardOptions;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaFilter;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaTicketFilter;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaTicketPage;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaTicketRow;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.TestRunCounts;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.TrendPoint;
import com.sdd.platform.domain.model.AuthUserContext;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class QaDashboardJdbcAdapter implements QaDashboardRepositoryPort {

    private static final DateTimeFormatter DAY_LABEL_FORMAT = DateTimeFormatter.ofPattern("dd/MM");
    private static final String TICKET_REPO_CTE = """
            ticket_repo AS (
                SELECT DISTINCT ON (s.ticket_id)
                       s.ticket_id,
                       s.repository_id
                FROM tbl_fact_artifact_snapshot s --
                ORDER BY s.ticket_id, s.collected_at DESC NULLS LAST
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final DashboardProjectAccessJdbcAdapter projectAccess;

    public QaDashboardJdbcAdapter(NamedParameterJdbcTemplate jdbc, DashboardProjectAccessJdbcAdapter projectAccess) {
        this.jdbc = jdbc;
        this.projectAccess = projectAccess;
    }

    // ── AC Coverage ───────────────────────────────────────────────────────────

    @Override
    public AcCoverageCounts findAcCoverageCounts(QaFilter filter) {
        var params = baseParams(filter);
        var sql = new StringBuilder("""
                SELECT
                    COUNT(*) AS total_count,
                    SUM(CASE WHEN EXISTS (
                        SELECT 1
                        FROM tbl_fact_ac_test_coverage atc2
                        WHERE atc2.ac_id = ac.ac_id
                          AND atc2.ticket_id = ac.ticket_id
                          AND atc2.test_case_id IS NOT NULL
                    ) THEN 1 ELSE 0 END) AS covered_count,
                    SUM(CASE WHEN NOT EXISTS (
                        SELECT 1
                        FROM tbl_fact_ac_test_coverage atc3
                        WHERE atc3.ac_id = ac.ac_id
                          AND atc3.ticket_id = ac.ticket_id
                          AND atc3.test_case_id IS NOT NULL
                    ) THEN 1 ELSE 0 END) AS not_tested_count
                FROM tbl_fact_acceptance_criteria ac
                JOIN tbl_dim_ticket t ON t.ticket_id = ac.ticket_id
                WHERE ac.status = 'ACTIVE'
                """);
        applyTicketFilters(sql, params, filter);

        return jdbc.queryForObject(sql.toString(), params, (rs, rowNum) -> new AcCoverageCounts(
                rs.getInt("covered_count"),
                rs.getInt("not_tested_count"),
                rs.getInt("total_count")));
    }

    private double findAcTestResultPercent(UUID ticketId) {
        var params = new MapSqlParameterSource("ticketId", ticketId);
        var sql = """
                SELECT SUM(CASE WHEN status IN ('PASSED', 'SKIPPED') THEN 1 ELSE 0 END)::numeric
                    / NULLIF(COUNT(*), 0) * 100 AS test_result_pct
                FROM (
                    SELECT
                        ac.ac_id,
                        CASE
                            WHEN SUM(CASE WHEN tc.status = 'SUCCESS' THEN 1 ELSE 0 END) > 0 THEN 'PASSED'
                            WHEN COUNT(atc.test_case_id) = 0 THEN 'NOT_TESTED'
                            WHEN SUM(CASE WHEN tc.status IN ('RUNNING', 'IN_PROGRESS', 'QUEUED', 'PENDING') THEN 1 ELSE 0 END) > 0 THEN 'RUNNING'
                            WHEN SUM(CASE WHEN tc.status IN ('FAILED', 'FAILURE') THEN 1 ELSE 0 END) > 0 THEN 'FAILED'
                            WHEN SUM(CASE WHEN tc.status = 'SKIPPED' THEN 1 ELSE 0 END) = COUNT(atc.test_case_id) THEN 'SKIPPED'
                            WHEN SUM(CASE WHEN tc.status = 'UNKNOWN' THEN 1 ELSE 0 END) > 0 THEN 'UNKNOWN'
                            ELSE 'PARTIAL'
                        END AS status
                    FROM tbl_fact_acceptance_criteria ac
                    LEFT JOIN tbl_fact_ac_test_coverage atc ON atc.ac_id = ac.ac_id AND atc.ticket_id = ac.ticket_id
                    LEFT JOIN tbl_fact_test_case tc ON tc.test_case_id = atc.test_case_id
                    WHERE ac.ticket_id = :ticketId AND ac.status = 'ACTIVE'
                    GROUP BY ac.ac_id
                ) AS ac_statuses
                """;
        Double percent = jdbc.queryForObject(sql, params, Double.class);
        return percent == null ? 0.0 : round2(percent);
    }

    // ── Defect Leakage ────────────────────────────────────────────────────────

    @Override
    public int findDefectLeakageCount(QaFilter filter) {
        var params = baseParams(filter);
        var sql = new StringBuilder("""
                SELECT COUNT(*) FROM tbl_fact_finding f
                JOIN tbl_dim_ticket t ON t.ticket_id = f.ticket_id
                WHERE f.status != 'RESOLVED'
                """);
        applyTicketFilters(sql, params, filter);

        Long count = jdbc.queryForObject(sql.toString(), params, Long.class);
        return count == null ? 0 : count.intValue();
    }

    // ── Test Run Summary ──────────────────────────────────────────────────────

    @Override
    public TestRunCounts findTestRunCounts(QaFilter filter) {
        var params = baseParams(filter);
        var sql = new StringBuilder("""
                SELECT
                    COALESCE(SUM(tr.passed_count), 0)  AS passed_sum,
                    COALESCE(SUM(tr.failed_count), 0)  AS failed_sum,
                    COALESCE(SUM(tr.skipped_count), 0) AS skipped_sum,
                    COALESCE(SUM(tr.test_count), 0)    AS total_sum
                FROM tbl_fact_test_run tr
                JOIN tbl_dim_ticket t ON t.ticket_id = tr.ticket_id
                WHERE 1=1
                """);
        applyTicketFilters(sql, params, filter);

        return jdbc.queryForObject(sql.toString(), params, (rs, rowNum) -> new TestRunCounts(
                rs.getInt("passed_sum"),
                rs.getInt("failed_sum"),
                rs.getInt("skipped_sum"),
                rs.getInt("total_sum")));
    }

    // ── Acceptance Criteria Rows (paginated) ──────────────────────────────────

    @Override
    public AcCoveragePage findAcCoverageRows(QaFilter filter) {
        var params = baseParams(filter);
        int pageSize = Math.min(filter.size(), 100);
        int offset = filter.page() * pageSize;

        var countSql = new StringBuilder("""
                SELECT COUNT(*)
                FROM tbl_fact_acceptance_criteria ac
                JOIN tbl_dim_ticket t ON t.ticket_id = ac.ticket_id
                WHERE ac.status = 'ACTIVE'
                """);
        applyTicketFilters(countSql, params, filter);

        Long total = jdbc.queryForObject(countSql.toString(), params, Long.class);
        long totalElements = total == null ? 0L : total;
        int totalPages = pageSize == 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);

        if (totalElements == 0) {
            return new AcCoveragePage(List.of(), filter.page(), pageSize, 0L, 0);
        }

        var dataSql = new StringBuilder("""
                SELECT
                    ac.ac_key,
                    t.external_ticket_key,
                    CASE
                        WHEN SUM(CASE WHEN tc.status = 'SUCCESS' THEN 1 ELSE 0 END) > 0 THEN 'PASSED'
                        WHEN COUNT(atc.test_case_id) = 0 THEN 'NOT_TESTED'
                        WHEN SUM(CASE WHEN tc.status IN ('RUNNING', 'IN_PROGRESS', 'QUEUED', 'PENDING') THEN 1 ELSE 0 END) > 0 THEN 'RUNNING'
                        WHEN SUM(CASE WHEN tc.status IN ('FAILED', 'FAILURE') THEN 1 ELSE 0 END) > 0 THEN 'FAILED'
                        WHEN SUM(CASE WHEN tc.status = 'SKIPPED' THEN 1 ELSE 0 END) = COUNT(atc.test_case_id) THEN 'SKIPPED'
                        WHEN SUM(CASE WHEN tc.status = 'UNKNOWN' THEN 1 ELSE 0 END) > 0 THEN 'UNKNOWN'
                        ELSE 'PARTIAL'
                    END AS derived_status
                FROM tbl_fact_acceptance_criteria ac
                JOIN tbl_dim_ticket t ON t.ticket_id = ac.ticket_id
                LEFT JOIN tbl_fact_ac_test_coverage atc
                       ON atc.ac_id = ac.ac_id AND atc.ticket_id = ac.ticket_id
                LEFT JOIN tbl_fact_test_case tc ON tc.test_case_id = atc.test_case_id
                WHERE ac.status = 'ACTIVE'
                """);
        applyTicketFilters(dataSql, params, filter);
        dataSql.append(" GROUP BY ac.ac_id, ac.ac_key, t.external_ticket_key");
        dataSql.append(" ORDER BY t.external_ticket_key ASC, ac.ac_key ASC");
        dataSql.append(" LIMIT :pageSize OFFSET :offset");
        params.addValue("pageSize", pageSize).addValue("offset", offset);

        List<AcCoverageRow> items = jdbc.query(dataSql.toString(), params,
                (rs, rowNum) -> {
                    String status = rs.getString("derived_status");
                    return new AcCoverageRow(
                            rs.getString("ac_key"),
                            rs.getString("external_ticket_key"),
                            status,
                            "No",
                            buildGap(status));
                });

        return new AcCoveragePage(items, filter.page(), pageSize, totalElements, totalPages);
    }

    // ── Acceptance Criteria Rows (unbounded, for CSV export) ──────────────────

    @Override
    public List<AcCoverageRow> findAllForExport(QaFilter filter) {
        var params = baseParams(filter);
        var sql = new StringBuilder("""
                SELECT
                    ac.ac_key,
                    t.external_ticket_key,
                    CASE
                        WHEN SUM(CASE WHEN tc.status = 'SUCCESS' THEN 1 ELSE 0 END) > 0 THEN 'PASSED'
                        WHEN COUNT(atc.test_case_id) = 0 THEN 'NOT_TESTED'
                        WHEN SUM(CASE WHEN tc.status IN ('RUNNING', 'IN_PROGRESS', 'QUEUED', 'PENDING') THEN 1 ELSE 0 END) > 0 THEN 'RUNNING'
                        WHEN SUM(CASE WHEN tc.status IN ('FAILED', 'FAILURE') THEN 1 ELSE 0 END) > 0 THEN 'FAILED'
                        WHEN SUM(CASE WHEN tc.status = 'SKIPPED' THEN 1 ELSE 0 END) = COUNT(atc.test_case_id) THEN 'SKIPPED'
                        WHEN SUM(CASE WHEN tc.status = 'UNKNOWN' THEN 1 ELSE 0 END) > 0 THEN 'UNKNOWN'
                        ELSE 'PARTIAL'
                    END AS derived_status
                FROM tbl_fact_acceptance_criteria ac
                JOIN tbl_dim_ticket t ON t.ticket_id = ac.ticket_id
                LEFT JOIN tbl_fact_ac_test_coverage atc
                       ON atc.ac_id = ac.ac_id AND atc.ticket_id = ac.ticket_id
                LEFT JOIN tbl_fact_test_case tc ON tc.test_case_id = atc.test_case_id
                WHERE ac.status = 'ACTIVE'
                """);
        applyTicketFilters(sql, params, filter);
        sql.append(" GROUP BY ac.ac_id, ac.ac_key, t.external_ticket_key");
        sql.append(" ORDER BY t.external_ticket_key ASC, ac.ac_key ASC");

        return jdbc.query(sql.toString(), params,
                (rs, rowNum) -> {
                    String status = rs.getString("derived_status");
                    return new AcCoverageRow(
                            rs.getString("ac_key"),
                            rs.getString("external_ticket_key"),
                            status,
                            "No",
                            buildGap(status));
                });
    }

    // ── Coverage Trend ────────────────────────────────────────────────────────

    @Override
    public List<TrendPoint> findCoverageTrendPoints(QaFilter filter) {
        var params = baseParams(filter);
        var sql = new StringBuilder("""
                SELECT
                    t.external_ticket_key                                                     AS ticket_label,
                    COUNT(DISTINCT CASE WHEN tc.status = 'SUCCESS' THEN atc.ac_id END)::float
                        / NULLIF(COUNT(DISTINCT atc.ac_id), 0) * 100                         AS coverage_pct
                FROM tbl_fact_ac_test_coverage atc
                JOIN tbl_dim_ticket t ON t.ticket_id = atc.ticket_id
                LEFT JOIN tbl_fact_test_case tc ON tc.test_case_id = atc.test_case_id
                WHERE atc.calculated_at IS NOT NULL
                """);
        applyTicketFilters(sql, params, filter);
        sql.append("""
                 GROUP BY t.ticket_id, t.external_ticket_key
                 ORDER BY t.external_ticket_key ASC
                """);

        return jdbc.query(sql.toString(), params,
                (rs, rowNum) -> new TrendPoint(
                        rs.getString("ticket_label"),
                        rs.getDouble("coverage_pct")));
    }

    // ── Ticket List ───────────────────────────────────────────────────────────

    /**
     * Resolves the PR author / commit author / ticket updater to a member, joined
     * against
     * both the pseudonym table (masked) and the auth account table (real login
     * fullname).
     */
    private static final String OWNER_JOINS_SQL = """
            LEFT JOIN LATERAL (
                SELECT pr.author_member_key, pr.author_display_name
                FROM tbl_fact_pull_request pr
                WHERE pr.ticket_id = t.ticket_id
                ORDER BY pr.opened_at DESC NULLS LAST
                LIMIT 1
            ) lpr ON TRUE
            LEFT JOIN tbl_dim_member_pseudonym pr_mp ON pr_mp.member_key = lpr.author_member_key
            LEFT JOIN tbl_auth_user_account pr_a ON pr_a.member_key = lpr.author_member_key
            LEFT JOIN LATERAL (
                SELECT c.author_pseudonym
                FROM tbl_fact_commit c
                WHERE c.ticket_id = t.ticket_id
                ORDER BY c.committed_at DESC NULLS LAST
                LIMIT 1
            ) lc ON TRUE
            LEFT JOIN tbl_dim_member_pseudonym mp ON mp.pseudonym = COALESCE(t.updated_by, t.created_by)
            LEFT JOIN tbl_auth_user_account mp_a ON mp_a.member_key = mp.member_key
            """;

    /**
     * Prefers real names (PR display name, then auth account fullname) over masked
     * pseudonyms.
     */
    private static final String OWNER_DISPLAY_EXPR = """
            COALESCE(
                NULLIF(BTRIM(lpr.author_display_name), ''),
                NULLIF(BTRIM(pr_a.fullname), ''),
                NULLIF(BTRIM(t.created_by), ''),
                NULLIF(BTRIM(mp_a.fullname), ''),
                NULLIF(BTRIM(pr_mp.pseudonym), ''),
                NULLIF(BTRIM(lc.author_pseudonym), ''),
                'Unknown'
            )""";

    @Override
    public QaTicketPage findTickets(QaTicketFilter filter) {
        var params = new MapSqlParameterSource();
        int pageSize = Math.min(filter.size() <= 0 ? 20 : filter.size(), 100);
        int offset = Math.max(filter.page(), 0) * pageSize;

        var countSql = new StringBuilder("""
                SELECT COUNT(*)
                FROM tbl_dim_ticket t
                WHERE 1=1
                """);
        applyTicketRowFilters(countSql, params, filter);
        Long total = jdbc.queryForObject(countSql.toString(), params, Long.class);
        long totalElements = total == null ? 0L : total;
        int totalPages = pageSize == 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);

        if (totalElements == 0) {
            return new QaTicketPage(List.of(), filter.page(), pageSize, 0L, 0);
        }

        var dataSql = ticketRowSelectSql();
        dataSql.append(" WHERE 1=1");
        applyTicketRowFilters(dataSql, params, filter);
        dataSql.append(" ORDER BY ").append(ticketSortColumn(filter.sortBy()))
                .append(' ').append(ticketSortDirection(filter.sortDir()));
        dataSql.append(" LIMIT :pageSize OFFSET :offset");
        params.addValue("pageSize", pageSize).addValue("offset", offset);

        List<QaTicketRow> items = jdbc.query(dataSql.toString(), params, this::mapTicketRow);
        return new QaTicketPage(items, filter.page(), pageSize, totalElements, totalPages);
    }

    @Override
    public Optional<QaTicketRow> findTicketDetail(UUID ticketId) {
        var params = new MapSqlParameterSource("ticketId", ticketId);
        var sql = ticketRowSelectSql().append(" WHERE t.ticket_id = :ticketId");
        List<QaTicketRow> rows = jdbc.query(sql.toString(), params, this::mapTicketRow);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private StringBuilder ticketRowSelectSql() {
        return new StringBuilder("WITH " + TICKET_REPO_CTE + """
                SELECT
                    t.ticket_id::text,
                    t.project_id::text,
                    p.project_alias,
                    tr.repository_id AS repository_id,
                    t.external_ticket_key,
                    t.title,
                    CAST(t.status AS VARCHAR) AS ticket_status,
                    t.priority,
                """).append(OWNER_DISPLAY_EXPR).append("""
                 AS owner_display,
                    ROUND(COALESCE((
                        SELECT COUNT(*) FILTER (WHERE EXISTS (
                                    SELECT 1
                                    FROM tbl_fact_ac_test_coverage atc
                                    WHERE atc.ac_id = ac.ac_id
                                      AND atc.ticket_id = ac.ticket_id
                                      AND atc.test_case_id IS NOT NULL
                                ))::numeric
                            / NULLIF(COUNT(*), 0) * 100
                        FROM tbl_fact_acceptance_criteria ac
                        WHERE ac.ticket_id = t.ticket_id
                          AND ac.status = 'ACTIVE'
                    ), 0), 2) AS ac_coverage_percent,
                    ROUND(COALESCE((
                        SELECT SUM(CASE WHEN status IN ('PASSED', 'SKIPPED') THEN 1 ELSE 0 END)::numeric
                            / NULLIF(COUNT(*), 0) * 100
                        FROM (
                            SELECT
                                ac.ac_id,
                                CASE
                                    WHEN SUM(CASE WHEN tc.status = 'SUCCESS' THEN 1 ELSE 0 END) > 0 THEN 'PASSED'
                                    WHEN COUNT(atc.test_case_id) = 0 THEN 'NOT_TESTED'
                                    WHEN SUM(CASE WHEN tc.status IN ('RUNNING', 'IN_PROGRESS', 'QUEUED', 'PENDING') THEN 1 ELSE 0 END) > 0 THEN 'RUNNING'
                                    WHEN SUM(CASE WHEN tc.status IN ('FAILED', 'FAILURE') THEN 1 ELSE 0 END) > 0 THEN 'FAILED'
                                    WHEN SUM(CASE WHEN tc.status = 'SKIPPED' THEN 1 ELSE 0 END) = COUNT(atc.test_case_id) THEN 'SKIPPED'
                                    WHEN SUM(CASE WHEN tc.status = 'UNKNOWN' THEN 1 ELSE 0 END) > 0 THEN 'UNKNOWN'
                                    ELSE 'PARTIAL'
                                END AS status
                            FROM tbl_fact_acceptance_criteria ac
                            LEFT JOIN tbl_fact_ac_test_coverage atc ON atc.ac_id = ac.ac_id AND atc.ticket_id = ac.ticket_id
                            LEFT JOIN tbl_fact_test_case tc ON tc.test_case_id = atc.test_case_id
                            WHERE ac.ticket_id = t.ticket_id AND ac.status = 'ACTIVE'
                            GROUP BY ac.ac_id
                        ) AS ac_statuses
                    ), 0), 2) AS test_result_percent,
                    t.created_at,
                    t.updated_at,
                    (SELECT MAX(a.schema_version) FROM tbl_fact_artifact_snapshot a WHERE a.ticket_id = t.ticket_id) AS artifact_version
                FROM tbl_dim_ticket t
                JOIN tbl_dim_project p ON p.project_id = t.project_id
                LEFT JOIN ticket_repo tr ON tr.ticket_id = t.ticket_id
                """).append(OWNER_JOINS_SQL);
    }

    private void applyTicketRowFilters(StringBuilder sql, MapSqlParameterSource params, QaTicketFilter filter) {
        if (filter.projectId() != null) {
            params.addValue("projectId", filter.projectId());
            sql.append(" AND t.project_id = :projectId");
        }
        if (filter.repositoryId() != null) {
            params.addValue("repositoryId", filter.repositoryId());
            sql.append("""
                     AND t.ticket_id IN (
                         SELECT DISTINCT ticket_id FROM tbl_fact_artifact_snapshot
                         WHERE repository_id = :repositoryId
                     )
                    """);
        }
        if (filter.ticketId() != null) {
            params.addValue("ticketId", filter.ticketId());
            sql.append(" AND t.ticket_id = :ticketId");
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            params.addValue("search", "%" + filter.search().trim() + "%");
            sql.append(" AND (t.external_ticket_key ILIKE :search OR t.title ILIKE :search)");
        }
    }

    @Override
    public Optional<String> findLatestCiRunUrl(UUID ticketId) {
        List<String> urls = jdbc.query("""
                SELECT ci_url
                FROM tbl_fact_ci_run
                WHERE ticket_id = :ticketId
                  AND ci_url IS NOT NULL
                ORDER BY COALESCE(completed_at, finished_at) DESC NULLS LAST,
                         COALESCE(started_at, collected_at) DESC,
                         collected_at DESC,
                         ci_run_id DESC
                LIMIT 1
                """,
                new MapSqlParameterSource("ticketId", ticketId),
                (rs, rowNum) -> rs.getString("ci_url"));
        return urls.isEmpty() ? Optional.empty() : Optional.ofNullable(urls.get(0));
    }

    private String ticketSortColumn(String sortBy) {
        if (sortBy == null)
            return "t.updated_at";
        return switch (sortBy) {
            case "ticketId" -> "t.external_ticket_key";
            case "summary" -> "t.title";
            case "status" -> "t.status";
            case "assignee" -> "owner_display";
            case "acCoverage" -> "ac_coverage_percent";
            case "testResult" -> "test_result_percent";
            case "updatedDate" -> "t.updated_at";
            default -> "t.updated_at";
        };
    }

    private String ticketSortDirection(String sortDir) {
        return "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
    }

    private QaTicketRow mapTicketRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Object repositoryIdValue = rs.getObject("repository_id");
        UUID repositoryId = repositoryIdValue == null
                ? null
                : repositoryIdValue instanceof UUID uuid
                        ? uuid
                        : UUID.fromString(repositoryIdValue.toString());

        return new QaTicketRow(
                UUID.fromString(rs.getString("ticket_id")),
                UUID.fromString(rs.getString("project_id")),
                rs.getString("project_alias"),
                repositoryId,
                rs.getString("external_ticket_key"),
                rs.getString("title"),
                rs.getString("ticket_status"),
                rs.getString("priority"),
                rs.getString("owner_display"),
                rs.getDouble("ac_coverage_percent"),
                rs.getDouble("test_result_percent"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("artifact_version", Integer.class));
    }

    // ── Ticket Acceptance Criteria ───────────────────────────────────────────

    @Override
    public QaAcTicketPage findTicketAcceptanceCriteria(UUID ticketId, String search, int page, int size) {
        int pageSize = Math.min(size <= 0 ? 20 : size, 100);
        int offset = Math.max(page, 0) * pageSize;

        var countParams = new MapSqlParameterSource("ticketId", ticketId);
        var countSql = new StringBuilder("""
                SELECT COUNT(*)
                FROM tbl_fact_acceptance_criteria ac
                WHERE ac.ticket_id = :ticketId AND ac.status = 'ACTIVE'
                """);
        applyAcSearchFilter(countSql, countParams, search);
        Long total = jdbc.queryForObject(countSql.toString(), countParams, Long.class);
        long totalElements = total == null ? 0L : total;
        int totalPages = pageSize == 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);

        AcCoverageCounts acCounts = findAcCoverageCounts(new QaFilter(null, null, ticketId, null, 0, 1));
        int testedCount = acCounts.totalCount() - acCounts.notTestedCount();
        double coveragePercent = acCounts.totalCount() == 0 ? 0.0
                : round2((double) acCounts.coveredCount() / acCounts.totalCount() * 100);
        double testResultPercent = findAcTestResultPercent(ticketId);

        if (totalElements == 0) {
            return new QaAcTicketPage(List.of(), page, pageSize, 0L, 0, acCounts.totalCount(),
                    testedCount, acCounts.notTestedCount(), coveragePercent, testResultPercent);
        }

        String ownerDisplay = findOwnerDisplay(ticketId);

        var dataParams = new MapSqlParameterSource("ticketId", ticketId);
        var dataSql = new StringBuilder("""
                SELECT
                    ac.ac_key,
                    ac.ac_summary,
                    MAX(tc.test_case_key) AS linked_test_case,
                    CASE
                        WHEN BOOL_OR(tc.status = 'SUCCESS') THEN 'PASSED'
                        WHEN BOOL_OR(tc.status IN ('RUNNING', 'IN_PROGRESS', 'QUEUED', 'PENDING')) THEN 'RUNNING'
                        WHEN COUNT(atc.test_case_id) = 0 THEN 'NOT_TESTED'
                        WHEN SUM(CASE WHEN tc.status = 'SKIPPED' THEN 1 ELSE 0 END) = COUNT(atc.test_case_id) THEN 'SKIPPED'
                        WHEN SUM(CASE WHEN tc.status = 'FAILED' THEN 1 ELSE 0 END) > 0 THEN 'FAILED'
                        ELSE 'PARTIAL'
                    END AS derived_status
                FROM tbl_fact_acceptance_criteria ac
                LEFT JOIN tbl_fact_ac_test_coverage atc ON atc.ac_id = ac.ac_id AND atc.ticket_id = ac.ticket_id
                LEFT JOIN tbl_fact_test_case tc ON tc.test_case_id = atc.test_case_id
                WHERE ac.ticket_id = :ticketId AND ac.status = 'ACTIVE'
                """);
        applyAcSearchFilter(dataSql, dataParams, search);
        dataSql.append(" GROUP BY ac.ac_id, ac.ac_key, ac.ac_summary");
        dataSql.append(" ORDER BY regexp_replace(ac.ac_key, '[0-9]+$', '') ASC,");
        dataSql.append(" (substring(ac.ac_key from '[0-9]+$'))::bigint ASC NULLS LAST,");
        dataSql.append(" ac.ac_key ASC");
        dataSql.append(" LIMIT :pageSize OFFSET :offset");
        dataParams.addValue("pageSize", pageSize).addValue("offset", offset);

        List<QaAcTicketRow> items = jdbc.query(dataSql.toString(), dataParams,
                (rs, rowNum) -> {
                    String status = rs.getString("derived_status");
                    return new QaAcTicketRow(
                            rs.getString("ac_key"),
                            rs.getString("ac_summary"),
                            status,
                            rs.getString("linked_test_case") == null ? "-" : rs.getString("linked_test_case"),
                            acTicketResultLabel(status),
                            ownerDisplay);
                });

        return new QaAcTicketPage(items, page, pageSize, totalElements, totalPages, acCounts.totalCount(),
                testedCount, acCounts.notTestedCount(), coveragePercent, testResultPercent);
    }

    private void applyAcSearchFilter(StringBuilder sql, MapSqlParameterSource params, String search) {
        if (search != null && !search.isBlank()) {
            params.addValue("search", "%" + search.trim() + "%");
            sql.append(" AND (ac.ac_key ILIKE :search OR ac.ac_summary ILIKE :search)");
        }
    }

    private String acTicketResultLabel(String status) {
        return switch (status) {
            case "PASSED" -> "Passed";
            case "RUNNING" -> "Running";
            case "FAILED" -> "Failed";
            default -> "Not Run";
        };
    }

    private String findOwnerDisplay(UUID ticketId) {
        var params = new MapSqlParameterSource("ticketId", ticketId);
        String sql = "SELECT " + OWNER_DISPLAY_EXPR + " AS owner_display FROM tbl_dim_ticket t "
                + OWNER_JOINS_SQL + " WHERE t.ticket_id = :ticketId";
        return jdbc.queryForObject(sql, params, String.class);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // ── Ticket Coverage Trend (average AC coverage per weekday) ──────────────

    @Override
    public List<TrendPoint> findTicketCoverageTrend(UUID ticketId) {
        var params = new MapSqlParameterSource("ticketId", ticketId);
        var sql = new StringBuilder("""
                WITH week_days AS (
                    SELECT gs::date AS trend_day
                    FROM generate_series(
                        date_trunc('week', CURRENT_DATE)::date,
                        date_trunc('week', CURRENT_DATE)::date + 4,
                        interval '1 day'
                    ) AS gs
                ),
                ac_day_coverage AS (
                    SELECT
                        atc.calculated_at::date AS trend_day,
                        ac.ac_id,
                        CASE
                            WHEN SUM(CASE WHEN tc.status = 'SUCCESS' THEN 1 ELSE 0 END) > 0 THEN 100.0
                            WHEN COUNT(atc.test_case_id) > 0 THEN 50.0
                            ELSE 0.0
                        END AS coverage_pct
                    FROM tbl_fact_acceptance_criteria ac
                    JOIN tbl_fact_ac_test_coverage atc ON atc.ac_id = ac.ac_id AND atc.ticket_id = ac.ticket_id
                    LEFT JOIN tbl_fact_test_case tc ON tc.test_case_id = atc.test_case_id
                    WHERE ac.ticket_id = :ticketId AND ac.status = 'ACTIVE' AND atc.calculated_at IS NOT NULL
                    GROUP BY atc.calculated_at::date, ac.ac_id
                )
                SELECT
                    wd.trend_day,
                    COALESCE(AVG(adc.coverage_pct), 0.0) AS coverage_pct
                FROM week_days wd
                LEFT JOIN ac_day_coverage adc ON adc.trend_day = wd.trend_day
                GROUP BY wd.trend_day
                ORDER BY wd.trend_day ASC
                """);

        return jdbc.query(sql.toString(), params,
                (rs, rowNum) -> new TrendPoint(
                        DAY_LABEL_FORMAT.format(rs.getDate("trend_day").toLocalDate()),
                        rs.getDouble("coverage_pct")));
    }

    // ── Options ───────────────────────────────────────────────────────────────

    @Override
    public boolean hasDashboardAccess(AuthUserContext caller) {
        return projectAccess.hasDashboardRole(caller, "QA");
    }

    @Override
    public String findProjectRole(AuthUserContext caller, UUID projectId) {
        return projectAccess.findProjectRole(caller, projectId);
    }

    @Override
    public QaDashboardOptions findOptions(UUID projectId, UUID repositoryId, AuthUserContext caller) {
        List<QaDashboardOption> projects = projectAccess.findProjectOptions(caller).stream()
                .map(projectAccess::toQaOption)
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
        List<QaDashboardOption> repositories = jdbc.query(repoSql.toString(), repoParams,
                (rs, rowNum) -> new QaDashboardOption(rs.getString("value"), rs.getString("label"), null));

        List<QaDashboardOption> tickets;
        if (repositoryId != null) {
            var ticketParams = new MapSqlParameterSource("repositoryId", repositoryId);
            tickets = jdbc.query("""
                    SELECT DISTINCT t.ticket_id::text AS value, t.external_ticket_key AS label
                    FROM tbl_dim_ticket t
                    JOIN tbl_fact_artifact_snapshot s ON s.ticket_id = t.ticket_id
                    WHERE s.repository_id = :repositoryId
                    ORDER BY t.external_ticket_key ASC
                    """, ticketParams,
                    (rs, rowNum) -> new QaDashboardOption(rs.getString("value"), rs.getString("label"), null));
        } else {
            tickets = List.of();
        }

        return new QaDashboardOptions(projects, repositories, tickets);
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private MapSqlParameterSource baseParams(QaFilter filter) {
        var params = new MapSqlParameterSource();
        if (filter.projectId() != null)
            params.addValue("projectId", filter.projectId());
        if (filter.repositoryId() != null)
            params.addValue("repositoryId", filter.repositoryId());
        return params;
    }

    private void applyTicketFilters(StringBuilder sql, MapSqlParameterSource params, QaFilter filter) {
        if (filter.projectId() != null) {
            sql.append(" AND t.project_id = :projectId");
        }
        if (filter.repositoryId() != null) {
            sql.append("""
                     AND t.ticket_id IN (
                         SELECT DISTINCT ticket_id FROM tbl_fact_artifact_snapshot
                         WHERE repository_id = :repositoryId
                     )
                    """);
        }
        if (filter.ticketId() != null) {
            sql.append(" AND t.ticket_id = :ticketId");
            params.addValue("ticketId", filter.ticketId());
        }
        applySearchFilter(sql, params, filter);
    }

    private void applySearchFilter(StringBuilder sql, MapSqlParameterSource params, QaFilter filter) {
        if (filter.search() != null && !filter.search().isBlank()) {
            sql.append(" AND (t.external_ticket_key ILIKE :search OR t.title ILIKE :search)");
            params.addValue("search", "%" + filter.search().trim() + "%");
        }
    }

    private String buildGap(String coverageStatus) {
        return switch (coverageStatus) {
            case "PASSED" -> "";
            case "NOT_TESTED" -> "No test case linked";
            case "RUNNING" -> "Tests still running";
            case "FAILED" -> "Tests have failed";
            case "SKIPPED" -> "All tests skipped";
            case "UNKNOWN" -> "Test status unknown";
            case "PARTIAL" -> "Tests incomplete";
            default -> "";
        };
    }
}
