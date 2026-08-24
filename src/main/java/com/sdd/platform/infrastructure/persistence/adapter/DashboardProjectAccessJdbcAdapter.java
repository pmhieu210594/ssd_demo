package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.usecase.dataopsdashboard.DataOpsDashboardModels;
import com.sdd.platform.application.usecase.devdashboard.DevDashboardModels;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels;
import com.sdd.platform.domain.model.AuthUserContext;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Repository
public class DashboardProjectAccessJdbcAdapter {

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardProjectAccessJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isAdmin(AuthUserContext caller) {
        if (caller == null || caller.getUserAccountId() == null) {
            return false;
        }
        Boolean result = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM tbl_auth_user_account a
                    JOIN tbl_dim_member_pseudonym m ON m.member_key = a.member_key
                    JOIN tbl_dim_role r ON r.role_id = m.role_id
                    WHERE a.user_account_id = :userAccountId
                      AND UPPER(BTRIM(r.role_name)) = 'ADMIN'
                )
                """,
                new MapSqlParameterSource("userAccountId", caller.getUserAccountId()),
                Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    public void applyProjectScope(StringBuilder sql, MapSqlParameterSource params, AuthUserContext caller,
            String projectColumnExpression) {
        if (isAdmin(caller)) {
            return;
        }
        if (caller == null || caller.getUserAccountId() == null) {
            sql.append(" AND 1 = 0");
            return;
        }
        params.addValue("callerUserAccountId", caller.getUserAccountId());
        sql.append("""
                 AND (
                     EXISTS (
                         SELECT 1
                         FROM tbl_auth_member_project_role apr
                         JOIN tbl_auth_user_account a ON a.member_key = apr.member_key
                         WHERE a.user_account_id = :callerUserAccountId
                           AND apr.active_flag = TRUE
                           AND apr.project_id = """).append(projectColumnExpression).append("""
                     )
                     OR EXISTS (
                         SELECT 1
                         FROM tbl_auth_member_access_scope mas
                         JOIN tbl_auth_user_account a ON a.member_key = mas.member_key
                         WHERE a.user_account_id = :callerUserAccountId
                           AND mas.active_flag = TRUE
                           AND mas.project_id = """).append(projectColumnExpression).append("""
                     )
                     OR EXISTS (
                         SELECT 1
                         FROM tbl_project_team pt
                         JOIN tbl_team_member tm ON tm.team_id = pt.team_id
                         JOIN tbl_auth_user_account a ON a.member_key = tm.member_key
                         WHERE a.user_account_id = :callerUserAccountId
                           AND tm.deleted_at IS NULL
                           AND tm.status = 'ACTIVE'
                           AND pt.deleted_at IS NULL
                           AND pt.status = 'ACTIVE'
                           AND pt.project_id = """).append(projectColumnExpression).append("""
                     )
                 )
                """);
    }

    public boolean hasDashboardRole(AuthUserContext caller, String requiredRole) {
        if (isAdmin(caller)) {
            return true;
        }
        if (caller == null || caller.getUserAccountId() == null) {
            return false;
        }
        Boolean result = jdbc.queryForObject("""
                WITH direct_project_role AS (
                    SELECT UPPER(BTRIM(r.role_name)) AS role
                    FROM tbl_auth_member_project_role apr
                    JOIN tbl_auth_user_account a ON a.member_key = apr.member_key
                    JOIN tbl_dim_role r ON r.role_id = apr.role_id
                    WHERE a.user_account_id = :userAccountId
                      AND apr.active_flag = TRUE
                ),
                scope_project_role AS (
                    SELECT UPPER(BTRIM(r.role_name)) AS role
                    FROM tbl_auth_member_access_scope mas
                    JOIN tbl_auth_user_account a ON a.member_key = mas.member_key
                    JOIN tbl_dim_role r ON r.role_id = mas.role_id
                    WHERE a.user_account_id = :userAccountId
                      AND mas.active_flag = TRUE
                ),
                team_project_role AS (
                    SELECT UPPER(BTRIM(r.role_name)) AS role
                    FROM tbl_project_team pt
                    JOIN tbl_team_member tm ON tm.team_id = pt.team_id
                    JOIN tbl_auth_user_account a ON a.member_key = tm.member_key
                    JOIN tbl_dim_role r ON r.role_id = tm.role_id
                    WHERE a.user_account_id = :userAccountId
                      AND pt.deleted_at IS NULL
                      AND pt.status = 'ACTIVE'
                      AND tm.deleted_at IS NULL
                      AND tm.status = 'ACTIVE'
                )
                SELECT EXISTS (
                    SELECT 1 FROM (
                        SELECT * FROM direct_project_role
                        UNION ALL
                        SELECT * FROM scope_project_role
                        UNION ALL
                        SELECT * FROM team_project_role
                    ) project_roles
                    WHERE role = :requiredRole
                )
                """,
                new MapSqlParameterSource()
                        .addValue("userAccountId", caller.getUserAccountId())
                        .addValue("requiredRole", requiredRole == null ? null : requiredRole.trim().toUpperCase(Locale.ROOT)),
                Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    public List<ProjectAccessOption> findProjectOptions(AuthUserContext caller) {
        if (isAdmin(caller)) {
            return jdbc.query("""
                    SELECT project_id::text AS value,
                           project_alias AS label,
                           'ADMIN' AS role
                    FROM tbl_dim_project
                    WHERE status = 'ACTIVE'
                    ORDER BY project_alias ASC, project_id ASC
                    """,
                    new MapSqlParameterSource(),
                    (rs, rowNum) -> new ProjectAccessOption(
                            rs.getString("value"),
                            rs.getString("label"),
                            rs.getString("role")));
        }

        if (caller == null || caller.getUserAccountId() == null) {
            return List.of();
        }

        return jdbc.query("""
                WITH direct_project_role AS (
                    SELECT
                        p.project_id::text AS value,
                        p.project_alias AS label,
                        UPPER(BTRIM(r.role_name)) AS role,
                        300 AS priority
                    FROM tbl_dim_project p
                    JOIN tbl_auth_member_project_role apr ON apr.project_id = p.project_id
                    JOIN tbl_auth_user_account a ON a.member_key = apr.member_key
                    JOIN tbl_dim_role r ON r.role_id = apr.role_id
                    WHERE p.status = 'ACTIVE'
                      AND apr.active_flag = TRUE
                      AND a.user_account_id = :userAccountId
                ),
                scope_project_role AS (
                    SELECT
                        p.project_id::text AS value,
                        p.project_alias AS label,
                        UPPER(BTRIM(r.role_name)) AS role,
                        200 AS priority
                    FROM tbl_dim_project p
                    JOIN tbl_auth_member_access_scope mas ON mas.project_id = p.project_id
                    JOIN tbl_auth_user_account a ON a.member_key = mas.member_key
                    JOIN tbl_dim_role r ON r.role_id = mas.role_id
                    WHERE p.status = 'ACTIVE'
                      AND mas.active_flag = TRUE
                      AND a.user_account_id = :userAccountId
                ),
                team_project_role AS (
                    SELECT
                        p.project_id::text AS value,
                        p.project_alias AS label,
                        UPPER(BTRIM(r.role_name)) AS role,
                        100 AS priority
                    FROM tbl_dim_project p
                    JOIN tbl_project_team pt ON pt.project_id = p.project_id
                    JOIN tbl_team_member tm ON tm.team_id = pt.team_id
                    JOIN tbl_auth_user_account a ON a.member_key = tm.member_key
                    JOIN tbl_dim_role r ON r.role_id = tm.role_id
                    WHERE p.status = 'ACTIVE'
                      AND pt.deleted_at IS NULL
                      AND pt.status = 'ACTIVE'
                      AND tm.deleted_at IS NULL
                      AND tm.status = 'ACTIVE'
                      AND a.user_account_id = :userAccountId
                )
                SELECT DISTINCT ON (value)
                       value, label, role
                FROM (
                    SELECT * FROM direct_project_role
                    UNION ALL
                    SELECT * FROM scope_project_role
                    UNION ALL
                    SELECT * FROM team_project_role
                ) project_access
                ORDER BY value, priority DESC, label ASC, role ASC
                """,
                new MapSqlParameterSource("userAccountId", caller.getUserAccountId()),
                (rs, rowNum) -> new ProjectAccessOption(
                        rs.getString("value"),
                        rs.getString("label"),
                        rs.getString("role")));
    }

    public String findProjectRole(AuthUserContext caller, UUID projectId) {
        if (projectId == null) {
            return null;
        }
        if (isAdmin(caller)) {
            return "ADMIN";
        }
        if (caller == null || caller.getUserAccountId() == null) {
            return null;
        }
        return jdbc.query("""
                WITH direct_project_role AS (
                    SELECT UPPER(BTRIM(r.role_name)) AS role, 300 AS priority
                    FROM tbl_auth_member_project_role apr
                    JOIN tbl_auth_user_account a ON a.member_key = apr.member_key
                    JOIN tbl_dim_role r ON r.role_id = apr.role_id
                    WHERE a.user_account_id = :userAccountId
                      AND apr.project_id = :projectId
                      AND apr.active_flag = TRUE
                ),
                scope_project_role AS (
                    SELECT UPPER(BTRIM(r.role_name)) AS role, 200 AS priority
                    FROM tbl_auth_member_access_scope mas
                    JOIN tbl_auth_user_account a ON a.member_key = mas.member_key
                    JOIN tbl_dim_role r ON r.role_id = mas.role_id
                    WHERE a.user_account_id = :userAccountId
                      AND mas.project_id = :projectId
                      AND mas.active_flag = TRUE
                ),
                team_project_role AS (
                    SELECT UPPER(BTRIM(r.role_name)) AS role, 100 AS priority
                    FROM tbl_project_team pt
                    JOIN tbl_team_member tm ON tm.team_id = pt.team_id
                    JOIN tbl_auth_user_account a ON a.member_key = tm.member_key
                    JOIN tbl_dim_role r ON r.role_id = tm.role_id
                    WHERE a.user_account_id = :userAccountId
                      AND pt.project_id = :projectId
                      AND pt.deleted_at IS NULL
                      AND pt.status = 'ACTIVE'
                      AND tm.deleted_at IS NULL
                      AND tm.status = 'ACTIVE'
                )
                SELECT role
                FROM (
                    SELECT * FROM direct_project_role
                    UNION ALL
                    SELECT * FROM scope_project_role
                    UNION ALL
                    SELECT * FROM team_project_role
                ) project_roles
                ORDER BY priority DESC, role ASC
                LIMIT 1
                """,
                new MapSqlParameterSource()
                        .addValue("userAccountId", caller.getUserAccountId())
                        .addValue("projectId", projectId),
                rs -> rs.next() ? rs.getString(1) : null);
    }

    public PmDashboardModels.DashboardOption toPmOption(ProjectAccessOption option) {
        return new PmDashboardModels.DashboardOption(option.value(), option.label(), option.role());
    }

    public DevDashboardModels.DevDashboardOption toDevOption(ProjectAccessOption option) {
        return new DevDashboardModels.DevDashboardOption(option.value(), option.label(), option.role());
    }

    public QaDashboardModels.QaDashboardOption toQaOption(ProjectAccessOption option) {
        return new QaDashboardModels.QaDashboardOption(option.value(), option.label(), option.role());
    }

    public DataOpsDashboardModels.DataOpsDashboardOption toDataOpsOption(ProjectAccessOption option) {
        return new DataOpsDashboardModels.DataOpsDashboardOption(option.value(), option.label(), option.role());
    }

    public SecurityDashboardModels.SecurityDashboardOption toSecurityOption(ProjectAccessOption option) {
        return new SecurityDashboardModels.SecurityDashboardOption(option.value(), option.label(), option.role());
    }

    public record ProjectAccessOption(String value, String label, String role) {
        public ProjectAccessOption {
            role = role == null ? null : role.trim().toUpperCase(Locale.ROOT);
        }
    }
}
