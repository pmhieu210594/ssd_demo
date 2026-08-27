package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.SecurityDashboardRepositoryPort;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.ChecklistSectionResult;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.ExceptionCounts;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.ExceptionResult;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SafetyPackCounts;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SastScaCounts;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecretScanCounts;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityFilter;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityDashboardOption;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityDashboardOptions;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityScanResult;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityScanSnapshot;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityTicketDetail;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityTicketPage;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityTicketRow;
import com.sdd.platform.application.usecase.securitydashboard.SecurityFindingResolutionTimeCalculator;
import com.sdd.platform.domain.model.AuthUserContext;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only aggregation over existing V4 Security tables.
 * ticket_repo derives a ticket's owning repository since tbl_dim_ticket has no
 * repository_id column.
 */
@Repository
public class SecurityDashboardJdbcAdapter implements SecurityDashboardRepositoryPort {

    private static final String TICKET_REPO_CTE = """
            ticket_repo AS (
                SELECT t.ticket_id,
                       COALESCE(
                           (SELECT s.repository_id FROM tbl_fact_security_scan s
                             WHERE s.ticket_id = t.ticket_id ORDER BY s.collected_at DESC LIMIT 1),
                           (SELECT a.repository_id FROM tbl_fact_artifact_snapshot a
                             WHERE a.ticket_id = t.ticket_id ORDER BY a.collected_at DESC LIMIT 1),
                           (SELECT e.repository_id FROM tbl_fact_exception e
                             WHERE e.ticket_id = t.ticket_id ORDER BY e.created_at DESC LIMIT 1)
                       ) AS repository_id
                FROM tbl_dim_ticket t
            )
            """;

    private static final String SECRET_SCAN_LATERAL_JOIN = """
            LEFT JOIN LATERAL (
                SELECT scan_status FROM tbl_fact_security_scan s2
                WHERE s2.ticket_id = t.ticket_id AND '1' = '1'
                ORDER BY s2.collected_at DESC LIMIT 1
            ) secret ON true
            """;

    private static final String SAST_LATERAL_JOIN = """
            LEFT JOIN LATERAL (
                SELECT scan_status FROM tbl_fact_security_scan s3
                WHERE s3.ticket_id = t.ticket_id AND s3.scanner_type = 'SAST'
                ORDER BY s3.collected_at DESC LIMIT 1
            ) sast ON true
            """;

    private static final String EXCEPTION_LATERAL_JOIN = """
            LEFT JOIN LATERAL (
                SELECT
                    CASE
                        WHEN COUNT(*) FILTER (WHERE e2.follow_up_status ILIKE 'OPEN') > 0 THEN 'OPEN'
                        WHEN COUNT(*) FILTER (WHERE e2.expiry_date IS NOT NULL AND e2.expiry_date < CURRENT_DATE) > 0 THEN 'EXPIRED'
                        WHEN COUNT(*) > 0 THEN 'CLOSED'
                        ELSE 'NONE'
                    END AS exception_status
                FROM tbl_fact_exception e2
                WHERE e2.ticket_id = t.ticket_id
            ) exc ON true
            """;

    private static final String SAFETY_LATERAL_JOIN = """
            LEFT JOIN LATERAL (
                SELECT scan_status FROM tbl_fact_safety_pack_status sp2
                WHERE sp2.repository_id = tr.repository_id
                ORDER BY sp2.collected_at DESC LIMIT 1
            ) sp ON true
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final DashboardProjectAccessJdbcAdapter projectAccess;

    public SecurityDashboardJdbcAdapter(NamedParameterJdbcTemplate jdbc,
            DashboardProjectAccessJdbcAdapter projectAccess) {
        this.jdbc = jdbc;
        this.projectAccess = projectAccess;
    }

    // ── Safety Pack (repository-level) ────────────────────────────────────────

    @Override
    public SafetyPackCounts findSafetyPackCounts(SecurityFilter filter) {
        var params = new MapSqlParameterSource();
        var sql = new StringBuilder("""
                WITH latest_safety AS (
                    SELECT DISTINCT ON (sp.repository_id) sp.repository_id, sp.scan_status
                    FROM tbl_fact_safety_pack_status sp
                    JOIN tbl_dim_repository r ON r.repository_id = sp.repository_id
                    WHERE 1=1
                """);
        applyRepositoryLevelFilters(sql, params, filter);
        applyRepositoryCrossTicketFilters(sql, params, filter);
        sql.append("""
                     ORDER BY sp.repository_id, sp.collected_at DESC
                )
                SELECT
                    COUNT(*) FILTER (WHERE scan_status = 'READY')   AS ready_count,
                    COUNT(*) FILTER (WHERE scan_status = 'WARNING') AS warning_count,
                    COUNT(*) FILTER (WHERE scan_status = 'MISSING') AS missing_count
                FROM latest_safety
                """);

        return jdbc.queryForObject(sql.toString(), params, (rs, rowNum) -> new SafetyPackCounts(
                rs.getInt("ready_count"),
                rs.getInt("warning_count"),
                rs.getInt("missing_count")));
    }

    /**
     * Restricts repo-level safety pack counts to repositories that have at least
     * one ticket
     * matching the active ticket-level filters (search, secretScanStatus,
     * sastStatus, exceptionStatus),
     * so the Safety Pack KPI stays consistent with the ticket table below it.
     */
    private void applyRepositoryCrossTicketFilters(StringBuilder sql, MapSqlParameterSource params,
            SecurityFilter filter) {
        boolean needsTicketJoin = (filter.search() != null && !filter.search().isBlank())
                || filter.secretScanStatus() != null
                || filter.sastStatus() != null
                || filter.exceptionStatus() != null;
        if (!needsTicketJoin) {
            return;
        }

        var exists = new StringBuilder(" AND EXISTS (\n                WITH " + TICKET_REPO_CTE + """
                    SELECT 1
                    FROM tbl_dim_ticket t
                    JOIN ticket_repo tr ON tr.ticket_id = t.ticket_id
                """);
        exists.append(SECRET_SCAN_LATERAL_JOIN);
        exists.append(SAST_LATERAL_JOIN);
        exists.append(EXCEPTION_LATERAL_JOIN);
        exists.append(" WHERE tr.repository_id = sp.repository_id");
        if (filter.search() != null && !filter.search().isBlank()) {
            exists.append(" AND t.external_ticket_key ILIKE :crossSearch");
            params.addValue("crossSearch", "%" + filter.search().trim() + "%");
        }
        if (filter.secretScanStatus() != null) {
            exists.append(" AND secret.scan_status = :crossSecretScanStatus");
            params.addValue("crossSecretScanStatus", filter.secretScanStatus());
        }
        if (filter.sastStatus() != null) {
            exists.append(" AND sast.scan_status = :crossSastStatus");
            params.addValue("crossSastStatus", filter.sastStatus());
        }
        if (filter.exceptionStatus() != null) {
            exists.append(" AND exc.exception_status = :crossExceptionStatus");
            params.addValue("crossExceptionStatus", filter.exceptionStatus());
        }
        exists.append(")");

        sql.append(exists);
    }

    // ── Secret Scan (ticket-level) ─────────────────────────────────────────────

    @Override
    public SecretScanCounts findSecretScanCounts(SecurityFilter filter) {
        var params = new MapSqlParameterSource();
        var sql = new StringBuilder("WITH " + TICKET_REPO_CTE + """
                , latest_scan AS (
                    SELECT DISTINCT ON (s.ticket_id) s.ticket_id, s.scan_status
                    FROM tbl_fact_security_scan s
                    WHERE s.scanner_type = 'SECRET'
                    ORDER BY s.ticket_id, s.collected_at DESC
                )
                SELECT
                    COUNT(*) FILTER (WHERE ls.scan_status = 'PASS') AS pass_count,
                    COUNT(*) FILTER (WHERE ls.scan_status = 'FAIL') AS fail_count
                FROM latest_scan ls
                JOIN tbl_dim_ticket t ON t.ticket_id = ls.ticket_id
                JOIN tbl_dim_project p ON p.project_id = t.project_id
                JOIN ticket_repo tr ON tr.ticket_id = t.ticket_id
                LEFT JOIN tbl_dim_repository r ON r.repository_id = tr.repository_id
                """);
        sql.append(SAFETY_LATERAL_JOIN);
        sql.append(SAST_LATERAL_JOIN);
        sql.append(EXCEPTION_LATERAL_JOIN);
        sql.append(" WHERE 1=1\n");
        applyTicketLevelFilters(sql, params, filter);
        if (filter.secretScanStatus() != null) {
            sql.append(" AND ls.scan_status = :secretScanStatus");
            params.addValue("secretScanStatus", filter.secretScanStatus());
        }
        if (filter.safetyStatus() != null) {
            sql.append(" AND sp.scan_status = :safetyStatus");
            params.addValue("safetyStatus", filter.safetyStatus());
        }
        if (filter.sastStatus() != null) {
            sql.append(" AND sast.scan_status = :sastStatus");
            params.addValue("sastStatus", filter.sastStatus());
        }
        if (filter.exceptionStatus() != null) {
            sql.append(" AND exc.exception_status = :exceptionStatus");
            params.addValue("exceptionStatus", filter.exceptionStatus());
        }

        return jdbc.queryForObject(sql.toString(), params,
                (rs, rowNum) -> new SecretScanCounts(rs.getInt("pass_count"), rs.getInt("fail_count")));
    }

    // ── SAST / SCA (ticket-level) ──────────────────────────────────────────────

    @Override
    public SastScaCounts findSastScaCounts(SecurityFilter filter) {
        var params = new MapSqlParameterSource();
        var sql = new StringBuilder("WITH " + TICKET_REPO_CTE + """
                , latest_scan AS (
                    SELECT DISTINCT ON (s.ticket_id, s.scanner_type) s.ticket_id, s.scanner_type, s.scan_status
                    FROM tbl_fact_security_scan s
                    WHERE s.scanner_type IN ('SAST', 'SCA')
                    ORDER BY s.ticket_id, s.scanner_type, s.collected_at DESC
                )
                SELECT
                    COUNT(*) FILTER (WHERE ls.scan_status = 'PASS')    AS pass_count,
                    COUNT(*) FILTER (WHERE ls.scan_status = 'WARNING') AS warning_count,
                    COUNT(*) FILTER (WHERE ls.scan_status = 'FAIL')    AS fail_count
                FROM latest_scan ls
                JOIN tbl_dim_ticket t ON t.ticket_id = ls.ticket_id
                JOIN tbl_dim_project p ON p.project_id = t.project_id
                JOIN ticket_repo tr ON tr.ticket_id = t.ticket_id
                LEFT JOIN tbl_dim_repository r ON r.repository_id = tr.repository_id
                """);
        sql.append(SAFETY_LATERAL_JOIN);
        sql.append(SECRET_SCAN_LATERAL_JOIN);
        sql.append(SAST_LATERAL_JOIN);
        sql.append(EXCEPTION_LATERAL_JOIN);
        sql.append(" WHERE 1=1\n");
        applyTicketLevelFilters(sql, params, filter);
        if (filter.sastStatus() != null) {
            sql.append(" AND sast.scan_status = :sastStatus");
            params.addValue("sastStatus", filter.sastStatus());
        }
        if (filter.safetyStatus() != null) {
            sql.append(" AND sp.scan_status = :safetyStatus");
            params.addValue("safetyStatus", filter.safetyStatus());
        }
        if (filter.secretScanStatus() != null) {
            sql.append(" AND secret.scan_status = :secretScanStatus");
            params.addValue("secretScanStatus", filter.secretScanStatus());
        }
        if (filter.exceptionStatus() != null) {
            sql.append(" AND exc.exception_status = :exceptionStatus");
            params.addValue("exceptionStatus", filter.exceptionStatus());
        }

        return jdbc.queryForObject(sql.toString(), params, (rs, rowNum) -> new SastScaCounts(
                rs.getInt("pass_count"),
                rs.getInt("warning_count"),
                rs.getInt("fail_count")));
    }

    // ── Security Exception (ticket-level) ─────────────────────────────────────

    @Override
    public ExceptionCounts findExceptionCounts(SecurityFilter filter) {
        var params = new MapSqlParameterSource();
        var sql = new StringBuilder("WITH " + TICKET_REPO_CTE + """
                SELECT
                    COUNT(*) FILTER (WHERE e.follow_up_status ILIKE 'OPEN') AS open_count,
                    COUNT(*)                                                 AS total_count
                FROM tbl_fact_exception e
                JOIN tbl_dim_ticket t ON t.ticket_id = e.ticket_id
                JOIN tbl_dim_project p ON p.project_id = t.project_id
                JOIN ticket_repo tr ON tr.ticket_id = t.ticket_id
                LEFT JOIN tbl_dim_repository r ON r.repository_id = tr.repository_id
                """);
        sql.append(SAFETY_LATERAL_JOIN);
        sql.append(SECRET_SCAN_LATERAL_JOIN);
        sql.append(SAST_LATERAL_JOIN);
        sql.append(" WHERE 1=1\n");
        applyTicketLevelFilters(sql, params, filter);
        if (filter.exceptionStatus() != null) {
            sql.append("""
                     AND (
                        CASE
                            WHEN e.follow_up_status ILIKE 'OPEN' THEN 'OPEN'
                            WHEN e.expiry_date IS NOT NULL AND e.expiry_date < CURRENT_DATE THEN 'EXPIRED'
                            ELSE 'CLOSED'
                        END
                    ) = :exceptionStatus
                    """);
            params.addValue("exceptionStatus", filter.exceptionStatus());
        }
        if (filter.safetyStatus() != null) {
            sql.append(" AND sp.scan_status = :safetyStatus");
            params.addValue("safetyStatus", filter.safetyStatus());
        }
        if (filter.secretScanStatus() != null) {
            sql.append(" AND secret.scan_status = :secretScanStatus");
            params.addValue("secretScanStatus", filter.secretScanStatus());
        }
        if (filter.sastStatus() != null) {
            sql.append(" AND sast.scan_status = :sastStatus");
            params.addValue("sastStatus", filter.sastStatus());
        }

        return jdbc.queryForObject(sql.toString(), params,
                (rs, rowNum) -> new ExceptionCounts(rs.getInt("open_count"), rs.getInt("total_count")));
    }

    // ── Ticket List (paginated) ────────────────────────────────────────────────

    @Override
    public SecurityTicketPage findTicketRows(SecurityFilter filter) {
        var params = new MapSqlParameterSource();
        int pageSize = Math.min(filter.size(), 100);
        int offset = filter.page() * pageSize;

        var countSql = new StringBuilder("WITH " + TICKET_REPO_CTE
                + """
                        SELECT COUNT(*)
                        FROM tbl_dim_ticket t
                        JOIN tbl_dim_project p ON p.project_id = t.project_id
                        JOIN ticket_repo tr ON tr.ticket_id = t.ticket_id
                        LEFT JOIN tbl_dim_repository r ON r.repository_id = tr.repository_id
                        LEFT JOIN LATERAL (
                            SELECT scan_status FROM tbl_fact_safety_pack_status sp2
                            WHERE sp2.repository_id = tr.repository_id
                            ORDER BY sp2.collected_at DESC LIMIT 1
                        ) sp ON true
                        LEFT JOIN LATERAL (
                            SELECT scan_status FROM tbl_fact_security_scan s2
                            WHERE s2.ticket_id = t.ticket_id AND s2.scanner_type = 'SECRET'
                            ORDER BY s2.collected_at DESC LIMIT 1
                        ) secret ON true
                        LEFT JOIN LATERAL (
                            SELECT scan_status FROM tbl_fact_security_scan s3
                            WHERE s3.ticket_id = t.ticket_id AND s3.scanner_type = 'SAST'
                            ORDER BY s3.collected_at DESC LIMIT 1
                        ) sast ON true
                        LEFT JOIN LATERAL (
                            SELECT
                                CASE
                                    WHEN COUNT(*) FILTER (WHERE e2.follow_up_status ILIKE 'OPEN') > 0 THEN 'OPEN'
                                    WHEN COUNT(*) FILTER (WHERE e2.expiry_date IS NOT NULL AND e2.expiry_date < CURRENT_DATE) > 0 THEN 'EXPIRED'
                                    WHEN COUNT(*) > 0 THEN 'CLOSED'
                                    ELSE 'NONE'
                                END AS exception_status
                            FROM tbl_fact_exception e2
                            WHERE e2.ticket_id = t.ticket_id
                        ) exc ON true
                        WHERE 1=1
                        """);
        applyTicketRowFilters(countSql, params, filter);

        Long total = jdbc.queryForObject(countSql.toString(), params, Long.class);
        long totalElements = total == null ? 0L : total;
        int totalPages = pageSize == 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);

        if (totalElements == 0) {
            return new SecurityTicketPage(List.of(), filter.page(), pageSize, 0L, 0);
        }

        var dataSql = new StringBuilder("WITH " + TICKET_REPO_CTE
                + """
                        SELECT
                            t.ticket_id,
                            t.external_ticket_key,
                            p.project_alias,
                            r.repo_name_masked,
                            sp.scan_status     AS safety_status,
                            secret.scan_status AS secret_status,
                            sast.scan_status   AS sast_status,
                            sca.scan_status    AS sca_status,
                            exc.exception_status AS exception_status,
                            (SELECT MAX(a.schema_version) FROM tbl_fact_artifact_snapshot a WHERE a.ticket_id = t.ticket_id) AS artifact_version
                        FROM tbl_dim_ticket t
                        JOIN tbl_dim_project p ON p.project_id = t.project_id
                        JOIN ticket_repo tr ON tr.ticket_id = t.ticket_id
                        LEFT JOIN tbl_dim_repository r ON r.repository_id = tr.repository_id
                        LEFT JOIN LATERAL (
                            SELECT scan_status FROM tbl_fact_safety_pack_status sp2
                            WHERE sp2.repository_id = tr.repository_id
                            ORDER BY sp2.collected_at DESC LIMIT 1
                        ) sp ON true
                        LEFT JOIN LATERAL (
                            SELECT scan_status FROM tbl_fact_security_scan s2
                            WHERE s2.ticket_id = t.ticket_id AND s2.scanner_type = 'SECRET'
                            ORDER BY s2.collected_at DESC LIMIT 1
                        ) secret ON true
                        LEFT JOIN LATERAL (
                            SELECT scan_status FROM tbl_fact_security_scan s3
                            WHERE s3.ticket_id = t.ticket_id AND s3.scanner_type = 'SAST'
                            ORDER BY s3.collected_at DESC LIMIT 1
                        ) sast ON true
                        LEFT JOIN LATERAL (
                            SELECT scan_status FROM tbl_fact_security_scan s4
                            WHERE s4.ticket_id = t.ticket_id AND s4.scanner_type = 'SCA'
                            ORDER BY s4.collected_at DESC LIMIT 1
                        ) sca ON true
                        LEFT JOIN LATERAL (
                            SELECT
                                CASE
                                    WHEN COUNT(*) FILTER (WHERE e2.follow_up_status ILIKE 'OPEN') > 0 THEN 'OPEN'
                                    WHEN COUNT(*) FILTER (WHERE e2.expiry_date IS NOT NULL AND e2.expiry_date < CURRENT_DATE) > 0 THEN 'EXPIRED'
                                    WHEN COUNT(*) > 0 THEN 'CLOSED'
                                    ELSE 'NONE'
                                END AS exception_status
                            FROM tbl_fact_exception e2
                            WHERE e2.ticket_id = t.ticket_id
                        ) exc ON true
                        WHERE 1=1
                        """);
        applyTicketRowFilters(dataSql, params, filter);
        dataSql.append(" ORDER BY t.external_ticket_key ASC");
        dataSql.append(" LIMIT :pageSize OFFSET :offset");
        params.addValue("pageSize", pageSize).addValue("offset", offset);

        List<SecurityTicketRow> items = jdbc.query(dataSql.toString(), params, (rs, rowNum) -> new SecurityTicketRow(
                rs.getObject("ticket_id", UUID.class),
                rs.getString("external_ticket_key"),
                rs.getString("project_alias"),
                rs.getString("repo_name_masked"),
                rs.getString("safety_status"),
                rs.getString("secret_status"),
                rs.getString("sast_status"),
                rs.getString("sca_status"),
                rs.getString("exception_status"),
                "NOT_CONFIGURED",
                rs.getObject("artifact_version", Integer.class)));

        return new SecurityTicketPage(items, filter.page(), pageSize, totalElements, totalPages);
    }

    // ── Ticket Detail ──────────────────────────────────────────────────────────

    @Override
    public Optional<SecurityTicketDetail> findTicketDetail(UUID ticketId) {
        var params = new MapSqlParameterSource("ticketId", ticketId);

        var headerSql = "WITH " + TICKET_REPO_CTE
                + """
                        SELECT
                            t.ticket_id,
                            t.external_ticket_key,
                            p.project_alias,
                            r.repo_name_masked,
                            sp.scan_status AS safety_status,
                            (SELECT MAX(a.schema_version) FROM tbl_fact_artifact_snapshot a WHERE a.ticket_id = t.ticket_id) AS artifact_version
                        FROM tbl_dim_ticket t
                        JOIN tbl_dim_project p ON p.project_id = t.project_id
                        JOIN ticket_repo tr ON tr.ticket_id = t.ticket_id
                        LEFT JOIN tbl_dim_repository r ON r.repository_id = tr.repository_id
                        LEFT JOIN LATERAL (
                            SELECT scan_status FROM tbl_fact_safety_pack_status sp2
                            WHERE sp2.repository_id = tr.repository_id
                            ORDER BY sp2.collected_at DESC LIMIT 1
                        ) sp ON true
                        WHERE t.ticket_id = :ticketId
                        """;

        List<SecurityTicketDetail> headers = jdbc.query(headerSql, params, (rs, rowNum) -> {
            Integer artifactVersion = rs.getObject("artifact_version", Integer.class);
            return new SecurityTicketDetail(
                    rs.getObject("ticket_id", UUID.class),
                    rs.getString("external_ticket_key"),
                    rs.getString("project_alias"),
                    rs.getString("repo_name_masked"),
                    rs.getString("safety_status"),
                    List.of(),
                    List.of(),
                    List.of(),
                    "NOT_CONFIGURED",
                    artifactVersion,
                    "-");
        });

        if (headers.isEmpty()) {
            return Optional.empty();
        }
        SecurityTicketDetail header = headers.get(0);

        List<SecurityScanResult> scans = jdbc.query("""
                SELECT DISTINCT ON (scanner_type)
                    scanner_type, scan_status, severity, finding_count, unresolved_count
                FROM tbl_fact_security_scan
                WHERE ticket_id = :ticketId
                ORDER BY scanner_type, collected_at DESC
                """, params, (rs, rowNum) -> new SecurityScanResult(
                rs.getString("scanner_type"),
                rs.getString("scan_status"),
                rs.getString("severity"),
                rs.getInt("finding_count"),
                rs.getInt("unresolved_count")));

        List<ChecklistSectionResult> checklistSections = jdbc.query("""
                SELECT pas.section_type, pas.present_flag, pas.valid_flag, pas.parse_warning
                FROM tbl_fact_artifact_parsed_section pas
                JOIN tbl_fact_artifact_snapshot snap ON snap.artifact_snapshot_id = pas.artifact_snapshot_id
                JOIN tbl_dim_artifact_type atype ON atype.artifact_type_id = snap.artifact_type_id
                WHERE pas.ticket_id = :ticketId AND atype.artifact_type_code = 'REVIEW_CHECKLIST'
                ORDER BY pas.section_type ASC
                """, params, (rs, rowNum) -> new ChecklistSectionResult(
                rs.getString("section_type"),
                rs.getBoolean("present_flag"),
                (Boolean) rs.getObject("valid_flag"),
                rs.getString("parse_warning")));

        List<ExceptionResult> exceptions = jdbc.query("""
                SELECT exception_type, approved, follow_up_status, expiry_date
                FROM tbl_fact_exception
                WHERE ticket_id = :ticketId
                ORDER BY created_at DESC
                """, params, (rs, rowNum) -> new ExceptionResult(
                rs.getString("exception_type"),
                rs.getBoolean("approved"),
                rs.getString("follow_up_status"),
                rs.getObject("expiry_date", java.time.LocalDate.class)));

        // SECURITY-FINDING-RESOLUTION-TIME: BR-1 — SAST scan history only, ordered
        // ascending so SecurityFindingResolutionTimeCalculator can walk cycles in
        // chronological order.
        List<SecurityScanSnapshot> sastHistory = jdbc.query("""
                SELECT unresolved_count, collected_at
                FROM tbl_fact_security_scan
                WHERE ticket_id = :ticketId AND scanner_type = 'SAST'
                ORDER BY collected_at ASC
                """, params, (rs, rowNum) -> new SecurityScanSnapshot(
                rs.getInt("unresolved_count"),
                rs.getObject("collected_at", OffsetDateTime.class)));
        String resolutionTime = SecurityFindingResolutionTimeCalculator.compute(sastHistory);

        return Optional.of(new SecurityTicketDetail(
                header.ticketId(),
                header.ticketKey(),
                header.projectAlias(),
                header.repositoryName(),
                header.safetyStatus(),
                scans,
                checklistSections,
                exceptions,
                "NOT_CONFIGURED", header.artifactVersion(),
                resolutionTime));
    }

    @Override
    public Optional<UUID> findProjectIdByTicketId(UUID ticketId) {
        var params = new MapSqlParameterSource("ticketId", ticketId);
        try {
            UUID projectId = jdbc.queryForObject("""
                    SELECT t.project_id FROM tbl_dim_ticket t WHERE t.ticket_id = :ticketId
                    """, params, UUID.class);
            return Optional.ofNullable(projectId);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean hasDashboardAccess(AuthUserContext caller) {
        return projectAccess.hasDashboardRole(caller, "SECURITY");
    }

    @Override
    public String findProjectRole(AuthUserContext caller, UUID projectId) {
        return projectAccess.findProjectRole(caller, projectId);
    }

    @Override
    public SecurityDashboardOptions findOptions(UUID projectId, UUID repositoryId, AuthUserContext caller) {
        List<SecurityDashboardOption> projects = projectAccess.findProjectOptions(caller).stream()
                .map(projectAccess::toSecurityOption)
                .toList();

        var repoParams = new MapSqlParameterSource();
        var repoSql = new StringBuilder("""
                SELECT repository_id::text AS value, repo_name_masked AS label
                FROM tbl_dim_repository
                WHERE status = 'ACTIVE'
                """);
        projectAccess.applyProjectScope(repoSql, repoParams, caller, "tbl_dim_repository.project_id");
        if (projectId != null) {
            repoParams.addValue("projectId", projectId);
            repoSql.append(" AND project_id = :projectId");
        }
        repoSql.append(" ORDER BY created_at DESC, repo_name_masked ASC, repository_id ASC");
        String projectRole = projectId == null ? null : projectAccess.findProjectRole(caller, projectId);
        List<SecurityDashboardOption> repositories = jdbc.query(repoSql.toString(), repoParams,
                (rs, rowNum) -> new SecurityDashboardOption(
                        rs.getString("value"),
                        rs.getString("label"),
                        projectRole));

        return new SecurityDashboardOptions(projects, repositories);
    }

    // ── Shared filter helpers ──────────────────────────────────────────────────

    private void applyRepositoryLevelFilters(StringBuilder sql, MapSqlParameterSource params, SecurityFilter filter) {
        if (filter.projectId() != null) {
            sql.append(" AND r.project_id = :projectId");
            params.addValue("projectId", filter.projectId());
        }
        if (filter.repositoryId() != null) {
            sql.append(" AND r.repository_id = :repositoryId");
            params.addValue("repositoryId", filter.repositoryId());
        }
    }

    private void applyTicketLevelFilters(StringBuilder sql, MapSqlParameterSource params, SecurityFilter filter) {
        if (filter.projectId() != null) {
            sql.append(" AND p.project_id = :projectId");
            params.addValue("projectId", filter.projectId());
        }
        if (filter.repositoryId() != null) {
            sql.append(" AND tr.repository_id = :repositoryId");
            params.addValue("repositoryId", filter.repositoryId());
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            sql.append(" AND t.external_ticket_key ILIKE :search");
            params.addValue("search", "%" + filter.search().trim() + "%");
        }
    }

    private void applyTicketRowFilters(StringBuilder sql, MapSqlParameterSource params, SecurityFilter filter) {
        applyTicketLevelFilters(sql, params, filter);
        if (filter.safetyStatus() != null) {
            sql.append(" AND sp.scan_status = :safetyStatus");
            params.addValue("safetyStatus", filter.safetyStatus());
        }
        if (filter.secretScanStatus() != null) {
            sql.append(" AND secret.scan_status = :secretScanStatus");
            params.addValue("secretScanStatus", filter.secretScanStatus());
        }
        if (filter.sastStatus() != null) {
            sql.append(" AND sast.scan_status = :sastStatus");
            params.addValue("sastStatus", filter.sastStatus());
        }
        if (filter.exceptionStatus() != null) {
            sql.append(" AND exc.exception_status = :exceptionStatus");
            params.addValue("exceptionStatus", filter.exceptionStatus());
        }
    }
}
