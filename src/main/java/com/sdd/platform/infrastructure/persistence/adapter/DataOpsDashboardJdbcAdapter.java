package com.sdd.platform.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.port.out.persistence.DataOpsDashboardRepositoryPort;
import com.sdd.platform.application.usecase.dataopsdashboard.DataOpsDashboardModels;
import com.sdd.platform.domain.model.AuthUserContext;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DataOpsDashboardJdbcAdapter implements DataOpsDashboardRepositoryPort {

    // Freshness threshold is a pending PM decision (H-DATAOPS-1, spec-pack open
    // issue OI-DATAOPS-1).
    // Used as a provisional default until PM confirms the business threshold.
    private static final int DEFAULT_FRESHNESS_STALE_MINUTES = 1440;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final DashboardProjectAccessJdbcAdapter projectAccess;

    public DataOpsDashboardJdbcAdapter(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper,
            DashboardProjectAccessJdbcAdapter projectAccess) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.projectAccess = projectAccess;
    }

    // ── Summary ───────────────────────────────────────────────────────────────

    @Override
    public DataOpsDashboardModels.DataOpsDashboardSummary findSummary(
            DataOpsDashboardModels.DataOpsDashboardFilter filter) {
        return new DataOpsDashboardModels.DataOpsDashboardSummary(
                countConnectorFailures(filter),
                countParseErrors(filter),
                countMissingEvidence(filter),
                countStaleFreshness(filter),
                countBrokenLinks(filter),
                OffsetDateTime.now());
    }

    // Connector Health calculation (H-DATAOPS-2 pending): a connector counts as
    // failing when its
    // most recent run finished with a failure-like status.
    private long countConnectorFailures(DataOpsDashboardModels.DataOpsDashboardFilter filter) {
        var params = new MapSqlParameterSource();
        var sql = buildSummaryRowsSql(filter, params);
        sql.append("""
                SELECT COUNT(*)
                FROM unified_rows
                WHERE 1=1
                """);
        applyConnectorFilters(sql, params, filter, "unified_rows", "unified_rows.repository_id", true,
                "unified_rows.project_id");
        applyParserStatusFilter(sql, params, filter, "unified_rows");
        sql.append("""
                  AND unified_rows.connector_id IS NOT NULL
                  AND unified_rows.latest_run_status IN ('FAILED', 'FAILURE')
                """);
        Long count = jdbc.queryForObject(sql.toString(), params, Long.class);
        return count == null ? 0L : count;
    }

    private long countParseErrors(DataOpsDashboardModels.DataOpsDashboardFilter filter) {
        var params = new MapSqlParameterSource();
        var sql = buildSummaryRowsSql(filter, params);
        sql.append("""
                SELECT COUNT(*)
                FROM unified_rows
                WHERE 1=1
                """);
        applyConnectorFilters(sql, params, filter, "unified_rows", "unified_rows.repository_id", true,
                "unified_rows.project_id");
        applyParserStatusFilter(sql, params, filter, "unified_rows");
        sql.append(" AND unified_rows.parse_error_count > 0");
        Long count = jdbc.queryForObject(sql.toString(), params, Long.class);
        return count == null ? 0L : count;
    }

    private long countMissingEvidence(DataOpsDashboardModels.DataOpsDashboardFilter filter) {
        var params = new MapSqlParameterSource();
        var sql = buildSummaryRowsSql(filter, params);
        sql.append("""
                SELECT COUNT(*)
                FROM unified_rows
                WHERE 1=1
                """);
        applyConnectorFilters(sql, params, filter, "unified_rows", "unified_rows.repository_id", true,
                "unified_rows.project_id");
        applyParserStatusFilter(sql, params, filter, "unified_rows");
        sql.append(" AND unified_rows.missing_evidence_count > 0");
        Long count = jdbc.queryForObject(sql.toString(), params, Long.class);
        return count == null ? 0L : count;
    }

    private long countStaleFreshness(DataOpsDashboardModels.DataOpsDashboardFilter filter) {
        var params = new MapSqlParameterSource();
        params.addValue("staleThreshold", DEFAULT_FRESHNESS_STALE_MINUTES);
        var sql = buildSummaryRowsSql(filter, params);
        sql.append("""
                SELECT COUNT(*)
                FROM unified_rows
                WHERE 1=1
                """);
        applyConnectorFilters(sql, params, filter, "unified_rows", "unified_rows.repository_id", true,
                "unified_rows.project_id");
        applyParserStatusFilter(sql, params, filter, "unified_rows");
        sql.append(" AND unified_rows.freshness_delay_minutes > :staleThreshold");
        Long count = jdbc.queryForObject(sql.toString(), params, Long.class);
        return count == null ? 0L : count;
    }

    // "Broken" traceability has no dedicated flag in tbl_fact_traceability_link
    // (see
    // link_confidence_level enum: HIGH/MEDIUM/LOW). LOW confidence is used as the
    // provisional
    // proxy for a broken link, pending requirement confirmation (self-review open
    // item).
    private long countBrokenLinks(DataOpsDashboardModels.DataOpsDashboardFilter filter) {
        var params = new MapSqlParameterSource();
        var sql = buildSummaryRowsSql(filter, params);
        sql.append("""
                , filtered_projects AS (
                    SELECT DISTINCT project_id
                    FROM unified_rows
                    WHERE 1=1
                """);
        applyConnectorFilters(sql, params, filter, "unified_rows", "unified_rows.repository_id", true,
                "unified_rows.project_id");
        applyParserStatusFilter(sql, params, filter, "unified_rows");
        sql.append("""
                )
                SELECT COUNT(*)
                FROM tbl_fact_traceability_link tl
                JOIN tbl_dim_ticket t ON t.ticket_id = tl.ticket_id
                WHERE tl.confidence_level = 'LOW'
                  AND EXISTS (
                      SELECT 1
                      FROM filtered_projects fp
                      WHERE fp.project_id = t.project_id
                  )
                """);
        Long count = jdbc.queryForObject(sql.toString(), params, Long.class);
        return count == null ? 0L : count;
    }

    // ── Connector Table ───────────────────────────────────────────────────────

    @Override
    public DataOpsDashboardModels.DataOpsDashboardPage findConnectors(
            DataOpsDashboardModels.DataOpsDashboardFilter filter) {
        var params = new MapSqlParameterSource();
        int pageSize = Math.min(filter.size(), 100);
        int offset = (Math.max(filter.page(), 1) - 1) * pageSize;

        var countSql = buildConnectorCountSql(filter, params);
        Long total = jdbc.queryForObject(countSql.toString(), params, Long.class);
        long totalElements = total == null ? 0L : total;
        int totalPages = pageSize == 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);

        if (totalElements == 0) {
            return new DataOpsDashboardModels.DataOpsDashboardPage(List.of(), filter.page(), pageSize, 0L, 0);
        }

        var dataSql = buildConnectorDataSql(filter, params);
        dataSql.append(" LIMIT :pageSize OFFSET :offset");
        params.addValue("pageSize", pageSize).addValue("offset", offset);

        List<DataOpsDashboardModels.DataOpsConnectorRow> items = jdbc.query(dataSql.toString(), params,
                this::mapConnectorRow);

        return new DataOpsDashboardModels.DataOpsDashboardPage(items, filter.page(), pageSize, totalElements,
                totalPages);
    }

    private StringBuilder buildConnectorCountSql(DataOpsDashboardModels.DataOpsDashboardFilter filter,
            MapSqlParameterSource params) {
        var sql = new StringBuilder("""
                WITH latest_run AS (
                    SELECT DISTINCT ON (connector_id)
                        connector_id,
                        repository_id,
                        status,
                        started_at
                    FROM tbl_connector_run
                """);
        if (filter.repositoryId() != null) {
            sql.append(" WHERE repository_id = :repositoryId\n");
            params.addValue("repositoryId", filter.repositoryId());
        }
        sql.append("""
                    ORDER BY connector_id, started_at DESC NULLS LAST, connector_run_id DESC
                ),
                connector_rows AS (
                    SELECT
                        sc.connector_id,
                        COALESCE(sc.project_id, r.project_id) AS project_id,
                        p.project_alias,
                        COALESCE(sc.repository_id, lr.repository_id) AS repository_id,
                        r.repo_name_masked AS repository_name,
                        sc.connector_name,
                        sc.connector_type,
                        (
                            SELECT CAST(cr.status AS VARCHAR)
                            FROM tbl_connector_run cr
                            WHERE cr.connector_id = sc.connector_id
                            ORDER BY cr.started_at DESC NULLS LAST, cr.connector_run_id DESC
                            LIMIT 1
                        ) AS latest_run_status,
                        (
                            SELECT cr.started_at
                            FROM tbl_connector_run cr
                            WHERE cr.connector_id = sc.connector_id
                            ORDER BY cr.started_at DESC NULLS LAST, cr.connector_run_id DESC
                            LIMIT 1
                        ) AS latest_run_at,
                        (
                            SELECT COUNT(*)
                            FROM tbl_connector_run cr
                            WHERE cr.connector_id = sc.connector_id AND cr.status IN ('FAILED', 'FAILURE')
                        ) AS failed_run_count,
                        (
                            SELECT COALESCE(SUM(dq.parse_error_count), 0)
                            FROM tbl_fact_data_quality dq
                            WHERE dq.repository_id = COALESCE(sc.repository_id, lr.repository_id)
                        ) AS parse_error_count,
                        (
                            SELECT COUNT(*)
                            FROM tbl_fact_artifact_snapshot a
                            WHERE a.repository_id = COALESCE(sc.repository_id, lr.repository_id)
                              AND (a.exists_flag = FALSE OR COALESCE(jsonb_array_length(a.required_fields_missing), 0) > 0)
                        ) AS missing_evidence_count,
                        (
                            SELECT dq.freshness_delay_minutes
                            FROM tbl_fact_data_quality dq
                            WHERE dq.repository_id = COALESCE(sc.repository_id, lr.repository_id)
                            ORDER BY dq.checked_at DESC NULLS LAST
                            LIMIT 1
                        ) AS freshness_delay_minutes
		                    FROM tbl_source_connector sc
		                    LEFT JOIN latest_run lr ON lr.connector_id = sc.connector_id
		                    LEFT JOIN tbl_dim_repository r
		                        ON r.repository_id = COALESCE(sc.repository_id, lr.repository_id)
		                    LEFT JOIN tbl_dim_project p
		                        ON p.project_id = COALESCE(sc.project_id, r.project_id)
		                    WHERE 1=1
		                      AND COALESCE(sc.repository_id, lr.repository_id) IS NOT NULL
	                ),
                repo_only_rows AS (
                    SELECT
                        NULL::uuid AS connector_id,
                        r.project_id,
                        p.project_alias,
                        r.repository_id,
                        r.repo_name_masked AS repository_name,
                        NULL::text AS connector_name,
                        NULL::text AS connector_type,
                        NULL::text AS latest_run_status,
                        NULL::timestamptz AS latest_run_at,
                        0::bigint AS failed_run_count,
                        0::bigint AS parse_error_count,
                        COUNT(*) AS missing_evidence_count,
                        NULL::int AS freshness_delay_minutes
                    FROM tbl_fact_artifact_snapshot a
                    JOIN tbl_dim_repository r ON r.repository_id = a.repository_id
                    JOIN tbl_dim_project p ON p.project_id = r.project_id
                    WHERE (a.exists_flag = FALSE OR COALESCE(jsonb_array_length(a.required_fields_missing), 0) > 0)
                      AND NOT EXISTS (
                          SELECT 1
                          FROM connector_rows cr
                          WHERE cr.repository_id = r.repository_id
                      )
                    GROUP BY r.project_id, p.project_alias, r.repository_id, r.repo_name_masked
                ),
                unified_rows AS (
                    SELECT * FROM connector_rows
                    UNION ALL
                    SELECT * FROM repo_only_rows
                )
                SELECT COUNT(*)
                FROM unified_rows
                WHERE 1=1
                """);
        applyConnectorFilters(
                sql,
                params,
                filter,
                "unified_rows",
                "unified_rows.repository_id",
                true,
                "unified_rows.project_id");
        applyParserStatusFilter(sql, params, filter, "unified_rows");
        return sql;
    }

    private StringBuilder buildConnectorDataSql(DataOpsDashboardModels.DataOpsDashboardFilter filter,
            MapSqlParameterSource params) {
        var sql = new StringBuilder(
                """
                        WITH latest_run AS (
                            SELECT DISTINCT ON (connector_id)
                                connector_id,
                                repository_id,
                                status,
                                started_at
                            FROM tbl_connector_run
                """);
        if (filter.repositoryId() != null) {
            sql.append("                            WHERE repository_id = :repositoryId\n");
        }
        sql.append("""
                            ORDER BY connector_id, started_at DESC NULLS LAST, connector_run_id DESC
                        ),
                        connector_rows AS (
                            SELECT
                                sc.connector_id,
                                COALESCE(sc.project_id, r.project_id) AS project_id,
                                p.project_alias,
                                COALESCE(sc.repository_id, lr.repository_id) AS repository_id,
                                r.repo_name_masked AS repository_name,
                                sc.connector_name,
                                sc.connector_type,
                                (
                                    SELECT CAST(cr.status AS VARCHAR)
                                    FROM tbl_connector_run cr
                                    WHERE cr.connector_id = sc.connector_id
                                    ORDER BY cr.started_at DESC NULLS LAST, cr.connector_run_id DESC
                                    LIMIT 1
                                ) AS latest_run_status,
                                (
                                    SELECT cr.started_at
                                    FROM tbl_connector_run cr
                                    WHERE cr.connector_id = sc.connector_id
                                    ORDER BY cr.started_at DESC NULLS LAST, cr.connector_run_id DESC
                                    LIMIT 1
                                ) AS latest_run_at,
                                (
                                    SELECT COUNT(*)
                                    FROM tbl_connector_run cr
                                    WHERE cr.connector_id = sc.connector_id AND cr.status IN ('FAILED', 'FAILURE')
                                ) AS failed_run_count,
                                (
                                    SELECT COALESCE(SUM(dq.parse_error_count), 0)
                                    FROM tbl_fact_data_quality dq
                                    WHERE dq.repository_id = COALESCE(sc.repository_id, lr.repository_id)
                                ) AS parse_error_count,
                                (
                                    SELECT COUNT(*)
                                    FROM tbl_fact_artifact_snapshot a
                                    WHERE a.repository_id = COALESCE(sc.repository_id, lr.repository_id)
                                      AND (a.exists_flag = FALSE OR COALESCE(jsonb_array_length(a.required_fields_missing), 0) > 0)
                                ) AS missing_evidence_count,
                                (
                                    SELECT dq.freshness_delay_minutes
                                    FROM tbl_fact_data_quality dq
                                    WHERE dq.repository_id = COALESCE(sc.repository_id, lr.repository_id)
                                    ORDER BY dq.checked_at DESC NULLS LAST
                                    LIMIT 1
                                ) AS freshness_delay_minutes
		                            FROM tbl_source_connector sc
		                            LEFT JOIN latest_run lr ON lr.connector_id = sc.connector_id
		                            LEFT JOIN tbl_dim_repository r
		                                ON r.repository_id = COALESCE(sc.repository_id, lr.repository_id)
		                            LEFT JOIN tbl_dim_project p
		                                ON p.project_id = COALESCE(sc.project_id, r.project_id)
		                            WHERE 1=1
		                              AND COALESCE(sc.repository_id, lr.repository_id) IS NOT NULL
	                        ),
                        repo_only_rows AS (
                            SELECT
                                NULL::uuid AS connector_id,
                                r.project_id,
                                p.project_alias,
                                r.repository_id,
                                r.repo_name_masked AS repository_name,
                                NULL::text AS connector_name,
                                NULL::text AS connector_type,
                                NULL::text AS latest_run_status,
                                NULL::timestamptz AS latest_run_at,
                                0::bigint AS failed_run_count,
                                0::bigint AS parse_error_count,
                                COUNT(*) AS missing_evidence_count,
                                NULL::int AS freshness_delay_minutes
                            FROM tbl_fact_artifact_snapshot a
                            JOIN tbl_dim_repository r ON r.repository_id = a.repository_id
                            JOIN tbl_dim_project p ON p.project_id = r.project_id
                            WHERE (a.exists_flag = FALSE OR COALESCE(jsonb_array_length(a.required_fields_missing), 0) > 0)
                              AND NOT EXISTS (
                                  SELECT 1
                                  FROM connector_rows cr
                                  WHERE cr.repository_id = r.repository_id
                              )
                            GROUP BY r.project_id, p.project_alias, r.repository_id, r.repo_name_masked
                        ),
                        unified_rows AS (
                            SELECT * FROM connector_rows
                            UNION ALL
                            SELECT * FROM repo_only_rows
                        )
                        SELECT *
                        FROM unified_rows
                        WHERE 1=1
                        """);
        applyConnectorFilters(
                sql,
                params,
                filter,
                "unified_rows",
                "unified_rows.repository_id",
                true,
                "unified_rows.project_id");
        applyParserStatusFilter(sql, params, filter, "unified_rows");
        sql.append(" ORDER BY unified_rows.missing_evidence_count DESC, unified_rows.repository_name ASC, unified_rows.connector_name ASC NULLS LAST");
        return sql;
    }

    // Parser status has no stored enum column on tbl_fact_data_quality; it is
    // derived from the
    // raw error/violation counters (mirrors the boolean derivation already used by
    // DevDashboardJdbcAdapter for its parser_error_flag).
    private void applyParserStatusFilter(StringBuilder sql, MapSqlParameterSource params,
            DataOpsDashboardModels.DataOpsDashboardFilter filter, String alias) {
        if (filter.parserStatus() == null) {
            return;
        }
        switch (filter.parserStatus()) {
            case "ERROR" -> sql.append(" AND EXISTS (")
                    .append(" SELECT 1 FROM tbl_fact_data_quality dq")
                    .append(" WHERE dq.repository_id = ")
                    .append(alias)
                    .append(".repository_id AND dq.parse_error_count > 0")
                    .append(" )");
            case "WARNING" -> sql.append(" AND EXISTS (")
                    .append(" SELECT 1 FROM tbl_fact_data_quality dq")
                    .append(" WHERE dq.repository_id = ")
                    .append(alias)
                    .append(".repository_id")
                    .append(" AND dq.parse_error_count = 0")
                    .append(" AND (dq.schema_violation_count > 0 OR dq.missing_count > 0)")
                    .append(" )");
            case "SUCCESS" -> sql.append(" AND NOT EXISTS (")
                    .append(" SELECT 1 FROM tbl_fact_data_quality dq")
                    .append(" WHERE dq.repository_id = ")
                    .append(alias)
                    .append(".repository_id")
                    .append(" AND (dq.parse_error_count > 0 OR dq.schema_violation_count > 0 OR dq.missing_count > 0)")
                    .append(" )");
            default -> {
            }
        }
    }

    private void applyConnectorFilters(
            StringBuilder sql,
            MapSqlParameterSource params,
            DataOpsDashboardModels.DataOpsDashboardFilter filter,
            String alias,
            String repositoryExpression,
            boolean includeRepositoryNameSearch,
            String projectExpression) {
        if (filter.projectId() != null) {
            params.addValue("projectId", filter.projectId());
            sql.append(" AND ").append(projectExpression).append(" = :projectId");
        }
        if (filter.repositoryId() != null) {
            params.addValue("repositoryId", filter.repositoryId());
            sql.append(" AND ").append(repositoryExpression).append(" = :repositoryId");
        }
        if (filter.connectorName() != null && !filter.connectorName().isBlank()) {
            params.addValue("connectorName", "%" + filter.connectorName().trim() + "%");
            sql.append(" AND (")
                    .append(alias).append(".connector_name ILIKE :connectorName");
            sql.append(" OR ").append(alias).append(".connector_id::text ILIKE :connectorName");
            if (includeRepositoryNameSearch) {
                sql.append(" OR ").append(alias).append(".repository_name ILIKE :connectorName");
            }
            sql.append(" )");
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            params.addValue("search", "%" + filter.search().trim() + "%");
            sql.append(" AND (")
                    .append(alias).append(".connector_name ILIKE :search OR ")
                    .append(alias).append(".connector_type ILIKE :search OR ")
                    .append(includeRepositoryNameSearch
                            ? alias + ".repository_name ILIKE :search"
                            : "FALSE")
                    .append(")");
        }
    }

    private DataOpsDashboardModels.DataOpsConnectorRow mapConnectorRow(java.sql.ResultSet rs, int rowNum)
            throws java.sql.SQLException {
        Integer freshnessDelay = (Integer) rs.getObject("freshness_delay_minutes");
        String connectorIdValue = rs.getString("connector_id");
        return new DataOpsDashboardModels.DataOpsConnectorRow(
                connectorIdValue == null ? null : UUID.fromString(connectorIdValue),
                UUID.fromString(rs.getString("project_id")),
                rs.getString("project_alias"),
                UUID.fromString(rs.getString("repository_id")),
                rs.getString("repository_name"),
                rs.getString("connector_name"),
                rs.getString("connector_type"),
                rs.getString("latest_run_status"),
                rs.getObject("latest_run_at", OffsetDateTime.class),
                rs.getLong("failed_run_count"),
                rs.getLong("parse_error_count"),
                rs.getLong("missing_evidence_count"),
                freshnessDelay);
    }

    // ── Connector Detail ──────────────────────────────────────────────────────

    @Override
    public Optional<DataOpsDashboardModels.DataOpsConnectorDetail> findConnectorDetail(UUID connectorId) {
        var params = new MapSqlParameterSource().addValue("connectorId", connectorId);

        List<DataOpsDashboardModels.DataOpsConnectorRow> rows = jdbc.query(
                """
                        WITH latest_run AS (
                            SELECT DISTINCT ON (connector_id)
                                connector_id,
                                repository_id
                            FROM tbl_connector_run
                            ORDER BY connector_id, started_at DESC NULLS LAST, connector_run_id DESC
                        )
                        SELECT
                            sc.connector_id::text,
                            COALESCE(sc.project_id, r.project_id)::text AS project_id,
                            p.project_alias,
                            COALESCE(sc.repository_id, lr.repository_id)::text AS repository_id,
                            r.repo_name_masked AS repository_name,
                            sc.connector_name,
                            sc.connector_type,
                            (SELECT CAST(cr.status AS VARCHAR)
                             FROM tbl_connector_run cr
                             WHERE cr.connector_id = sc.connector_id
                             ORDER BY cr.started_at DESC NULLS LAST, cr.connector_run_id DESC
                             LIMIT 1) AS latest_run_status,
                            (SELECT cr.started_at
                             FROM tbl_connector_run cr
                             WHERE cr.connector_id = sc.connector_id
                             ORDER BY cr.started_at DESC NULLS LAST, cr.connector_run_id DESC
                             LIMIT 1) AS latest_run_at,
                            (SELECT COUNT(*) FROM tbl_connector_run cr
                             WHERE cr.connector_id = sc.connector_id AND cr.status IN ('FAILED', 'FAILURE')) AS failed_run_count,
                            (SELECT COALESCE(SUM(dq.parse_error_count), 0) FROM tbl_fact_data_quality dq
                             WHERE dq.repository_id = COALESCE(sc.repository_id, lr.repository_id)) AS parse_error_count,
                            (SELECT COUNT(*)
                             FROM tbl_fact_artifact_snapshot a
                             WHERE a.repository_id = COALESCE(sc.repository_id, lr.repository_id)
                               AND (a.exists_flag = FALSE OR COALESCE(jsonb_array_length(a.required_fields_missing), 0) > 0)) AS missing_evidence_count,
                            (SELECT dq.freshness_delay_minutes FROM tbl_fact_data_quality dq
                             WHERE dq.repository_id = COALESCE(sc.repository_id, lr.repository_id)
                             ORDER BY dq.checked_at DESC NULLS LAST LIMIT 1) AS freshness_delay_minutes
                        FROM tbl_source_connector sc
                        LEFT JOIN latest_run lr ON lr.connector_id = sc.connector_id
                        LEFT JOIN tbl_dim_repository r
                            ON r.repository_id = COALESCE(sc.repository_id, lr.repository_id)
                        LEFT JOIN tbl_dim_project p
                            ON p.project_id = COALESCE(sc.project_id, r.project_id)
                        WHERE sc.connector_id = :connectorId
                          AND COALESCE(sc.repository_id, lr.repository_id) IS NOT NULL
                        """,
                params, this::mapConnectorRow);

        if (rows.isEmpty()) {
            return Optional.empty();
        }
        DataOpsDashboardModels.DataOpsConnectorRow row = rows.get(0);

        List<DataOpsDashboardModels.DataOpsConnectorRunItem> recentRuns = jdbc.query("""
                SELECT connector_run_id::text, CAST(status AS VARCHAR) AS status, started_at, finished_at,
                       records_read, records_written, error_message
                FROM tbl_connector_run
                WHERE connector_id = :connectorId
                ORDER BY started_at DESC NULLS LAST, connector_run_id DESC
                LIMIT 20
                """, params,
                (rs, rowNum) -> new DataOpsDashboardModels.DataOpsConnectorRunItem(
                        UUID.fromString(rs.getString("connector_run_id")),
                        rs.getString("status"),
                        rs.getObject("started_at", OffsetDateTime.class),
                        rs.getObject("finished_at", OffsetDateTime.class),
                        rs.getInt("records_read"),
                        rs.getInt("records_written"),
                        rs.getString("error_message")));

        var dqParams = new MapSqlParameterSource().addValue("repositoryId", row.repositoryId());
        List<DataOpsDashboardModels.DataOpsDataQualityItem> dataQualityChecks = jdbc.query("""
                SELECT data_quality_id::text, source_type, missing_count, parse_error_count,
                       schema_violation_count, error_summary, freshness_delay_minutes, checked_at
                FROM tbl_fact_data_quality
                WHERE repository_id = :repositoryId
                ORDER BY checked_at DESC NULLS LAST
                LIMIT 20
                """, dqParams,
                (rs, rowNum) -> new DataOpsDashboardModels.DataOpsDataQualityItem(
                        UUID.fromString(rs.getString("data_quality_id")),
                        rs.getString("source_type"),
                        rs.getInt("missing_count"),
                        rs.getInt("parse_error_count"),
                        rs.getInt("schema_violation_count"),
                        rs.getString("error_summary"),
                        (Integer) rs.getObject("freshness_delay_minutes"),
                        rs.getObject("checked_at", OffsetDateTime.class)));

        List<DataOpsDashboardModels.DataOpsMissingEvidenceItem> missingEvidenceItems =
                findMissingEvidenceItems(row.repositoryId());

        return Optional.of(new DataOpsDashboardModels.DataOpsConnectorDetail(row, recentRuns, dataQualityChecks,
                missingEvidenceItems));
    }

    @Override
    public List<DataOpsDashboardModels.DataOpsMissingEvidenceItem> findRepositoryMissingEvidence(UUID repositoryId) {
        return findMissingEvidenceItems(repositoryId);
    }

    private List<DataOpsDashboardModels.DataOpsMissingEvidenceItem> findMissingEvidenceItems(UUID repositoryId) {
        var params = new MapSqlParameterSource().addValue("repositoryId", repositoryId);
        return jdbc.query("""
                SELECT
                    s.artifact_snapshot_id::text,
                    s.ticket_id::text,
                    tkt.external_ticket_key,
                    tkt.title,
                    t.artifact_type_code,
                    t.artifact_name,
                    COALESCE(NULLIF(s.source_path, ''), t.default_file_name) AS file_name,
                    s.source_path,
                    s.exists_flag,
                    COALESCE(to_jsonb(s.required_fields_missing), '[]'::jsonb)::text AS required_fields_missing,
                    COALESCE(to_jsonb(ARRAY(
                        SELECT p.section_key
                        FROM tbl_fact_artifact_parsed_section p
                        WHERE p.artifact_snapshot_id = s.artifact_snapshot_id
                          AND p.required_flag = TRUE
                          AND COALESCE(p.present_flag, FALSE) = FALSE
                        ORDER BY p.section_key ASC
                    )), '[]'::jsonb)::text AS missing_sections,
                    s.collected_at
                FROM tbl_fact_artifact_snapshot s
                JOIN tbl_dim_artifact_type t ON t.artifact_type_id = s.artifact_type_id
                LEFT JOIN tbl_dim_ticket tkt ON tkt.ticket_id = s.ticket_id
                WHERE s.repository_id = :repositoryId
                  AND (s.exists_flag = FALSE OR COALESCE(jsonb_array_length(s.required_fields_missing), 0) > 0)
                ORDER BY s.collected_at DESC NULLS LAST, s.artifact_snapshot_id DESC
                LIMIT 50
                """, params,
                (rs, rowNum) -> new DataOpsDashboardModels.DataOpsMissingEvidenceItem(
                        UUID.fromString(rs.getString("artifact_snapshot_id")),
                        parseUuidOrNull(rs.getString("ticket_id")),
                        rs.getString("external_ticket_key"),
                        rs.getString("title"),
                        rs.getString("artifact_type_code"),
                        rs.getString("artifact_name"),
                        rs.getString("file_name"),
                        rs.getString("source_path"),
                        rs.getBoolean("exists_flag"),
                        readStringList(rs.getString("required_fields_missing")),
                        readStringList(rs.getString("missing_sections")),
                        rs.getObject("collected_at", OffsetDateTime.class)));
    }

    // ── Options ───────────────────────────────────────────────────────────────

    @Override
    public boolean hasDashboardAccess(AuthUserContext caller) {
        return projectAccess.hasDashboardRole(caller, "DATA_OPS");
    }

    @Override
    public String findProjectRole(AuthUserContext caller, UUID projectId) {
        return projectAccess.findProjectRole(caller, projectId);
    }

    @Override
    public DataOpsDashboardModels.DataOpsDashboardOptions findOptions(UUID projectId, UUID repositoryId,
            AuthUserContext caller) {
        List<DataOpsDashboardModels.DataOpsDashboardOption> projects = projectAccess.findProjectOptions(caller).stream()
                .map(projectAccess::toDataOpsOption)
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
        List<DataOpsDashboardModels.DataOpsDashboardOption> repositories = jdbc.query(repoSql.toString(), repoParams,
                this::mapOption);

        var connectorParams = new MapSqlParameterSource();
        var connectorSql = new StringBuilder("""
                SELECT connector_id::text AS value, connector_name AS label
                FROM tbl_source_connector
                WHERE enabled = TRUE
                """);
        projectAccess.applyProjectScope(connectorSql, connectorParams, caller, "project_id");
        if (projectId != null) {
            connectorParams.addValue("projectId", projectId);
            connectorSql.append(" AND (project_id = :projectId OR project_id IS NULL)");
        }
        if (repositoryId != null) {
            connectorParams.addValue("repositoryId", repositoryId);
            connectorSql.append(" AND (repository_id = :repositoryId OR repository_id IS NULL)");
        }
        connectorSql.append(" ORDER BY connector_name ASC");
        List<DataOpsDashboardModels.DataOpsDashboardOption> connectors = jdbc.query(connectorSql.toString(),
                connectorParams, this::mapOption);

        return new DataOpsDashboardModels.DataOpsDashboardOptions(projects, repositories, connectors);
    }

    private DataOpsDashboardModels.DataOpsDashboardOption mapOption(java.sql.ResultSet rs, int rowNum)
            throws java.sql.SQLException {
        return new DataOpsDashboardModels.DataOpsDashboardOption(rs.getString("value"), rs.getString("label"), null);
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private UUID parseUuidOrNull(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    private StringBuilder buildSummaryRowsSql(DataOpsDashboardModels.DataOpsDashboardFilter filter,
            MapSqlParameterSource params) {
        var sql = new StringBuilder("""
                WITH latest_run AS (
                    SELECT DISTINCT ON (connector_id)
                        connector_id,
                        repository_id,
                        status,
                        started_at
                    FROM tbl_connector_run
                """);
        if (filter.repositoryId() != null) {
            sql.append(" WHERE repository_id = :repositoryId\n");
            params.addValue("repositoryId", filter.repositoryId());
        }
        sql.append("""
                    ORDER BY connector_id, started_at DESC NULLS LAST, connector_run_id DESC
                ),
                connector_rows AS (
                    SELECT
                        sc.connector_id,
                        COALESCE(sc.project_id, r.project_id) AS project_id,
                        p.project_alias,
                        COALESCE(sc.repository_id, lr.repository_id) AS repository_id,
                        r.repo_name_masked AS repository_name,
                        sc.connector_name,
                        sc.connector_type,
                        (
                            SELECT CAST(cr.status AS VARCHAR)
                            FROM tbl_connector_run cr
                            WHERE cr.connector_id = sc.connector_id
                            ORDER BY cr.started_at DESC NULLS LAST, cr.connector_run_id DESC
                            LIMIT 1
                        ) AS latest_run_status,
                        (
                            SELECT cr.started_at
                            FROM tbl_connector_run cr
                            WHERE cr.connector_id = sc.connector_id
                            ORDER BY cr.started_at DESC NULLS LAST, cr.connector_run_id DESC
                            LIMIT 1
                        ) AS latest_run_at,
                        (
                            SELECT COUNT(*)
                            FROM tbl_connector_run cr
                            WHERE cr.connector_id = sc.connector_id AND cr.status IN ('FAILED', 'FAILURE')
                        ) AS failed_run_count,
                        (
                            SELECT COALESCE(SUM(dq.parse_error_count), 0)
                            FROM tbl_fact_data_quality dq
                            WHERE dq.repository_id = COALESCE(sc.repository_id, lr.repository_id)
                        ) AS parse_error_count,
                        (
                            SELECT COUNT(*)
                            FROM tbl_fact_artifact_snapshot a
                            WHERE a.repository_id = COALESCE(sc.repository_id, lr.repository_id)
                              AND (a.exists_flag = FALSE OR COALESCE(jsonb_array_length(a.required_fields_missing), 0) > 0)
                        ) AS missing_evidence_count,
                        (
                            SELECT dq.freshness_delay_minutes
                            FROM tbl_fact_data_quality dq
                            WHERE dq.repository_id = COALESCE(sc.repository_id, lr.repository_id)
                            ORDER BY dq.checked_at DESC NULLS LAST
                            LIMIT 1
                        ) AS freshness_delay_minutes
                    FROM tbl_source_connector sc
                    LEFT JOIN latest_run lr ON lr.connector_id = sc.connector_id
                    LEFT JOIN tbl_dim_repository r
                        ON r.repository_id = COALESCE(sc.repository_id, lr.repository_id)
                    LEFT JOIN tbl_dim_project p
                        ON p.project_id = COALESCE(sc.project_id, r.project_id)
                    WHERE 1=1
                      AND COALESCE(sc.repository_id, lr.repository_id) IS NOT NULL
                ),
                repo_only_rows AS (
                    SELECT
                        NULL::uuid AS connector_id,
                        r.project_id,
                        p.project_alias,
                        r.repository_id,
                        r.repo_name_masked AS repository_name,
                        NULL::text AS connector_name,
                        NULL::text AS connector_type,
                        NULL::text AS latest_run_status,
                        NULL::timestamptz AS latest_run_at,
                        0::bigint AS failed_run_count,
                        0::bigint AS parse_error_count,
                        COUNT(*) AS missing_evidence_count,
                        NULL::int AS freshness_delay_minutes
                    FROM tbl_fact_artifact_snapshot a
                    JOIN tbl_dim_repository r ON r.repository_id = a.repository_id
                    JOIN tbl_dim_project p ON p.project_id = r.project_id
                    WHERE (a.exists_flag = FALSE OR COALESCE(jsonb_array_length(a.required_fields_missing), 0) > 0)
                      AND NOT EXISTS (
                          SELECT 1
                          FROM connector_rows cr
                          WHERE cr.repository_id = r.repository_id
                      )
                    GROUP BY r.project_id, p.project_alias, r.repository_id, r.repo_name_masked
                ),
                unified_rows AS (
                    SELECT * FROM connector_rows
                    UNION ALL
                    SELECT * FROM repo_only_rows
                )
                """);
        return sql;
    }
}
