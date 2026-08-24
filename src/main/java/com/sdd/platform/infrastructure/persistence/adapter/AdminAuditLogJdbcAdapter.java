package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.AdminAuditLogPersistencePort;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.application.usecase.governance.AdminAuditLogModels.AdminAuditLogDetail;
import com.sdd.platform.application.usecase.governance.AdminAuditLogModels.AdminAuditLogEntry;
import com.sdd.platform.application.usecase.governance.AdminAuditLogModels.AdminAuditLogFilter;
import com.sdd.platform.application.usecase.governance.AdminAuditLogModels.AdminAuditLogListItem;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AdminAuditLogJdbcAdapter implements AdminAuditLogPersistencePort {

    private final NamedParameterJdbcTemplate jdbc;

    public AdminAuditLogJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(AdminAuditLogEntry entry) {
        // result (access_result) is NOT NULL with no DB default; the Admin Audit Log feature no
        // longer tracks operation status, so a fixed SUCCESS literal is written to satisfy the
        // shared column's constraint without exposing/filtering on it in this feature.
        jdbc.update("""
                INSERT INTO tbl_fact_access_log (
                    access_log_id, actor_user_id, actor_username, actor_role_name,
                    module, entity_type, entity_id, operation_type,
                    changed_fields, before_value, after_value,
                    user_agent, result, error_message, trace_id, occurred_at
                ) VALUES (
                    gen_random_uuid(), :actorUserId, :actorUsername, :actorRoleName,
                    :module, :entityType, :entityId, :operationType,
                    :changedFields, CAST(:beforeValueJson AS jsonb), CAST(:afterValueJson AS jsonb),
                    :userAgent, 'SUCCESS'::access_result, :errorMessage, :traceId, :occurredAt
                )
                """,
                new MapSqlParameterSource()
                        .addValue("actorUserId", entry.actorUserId())
                        .addValue("actorUsername", entry.actorUsername())
                        .addValue("actorRoleName", entry.actorRoleName())
                        .addValue("module", entry.module())
                        .addValue("entityType", entry.entityType())
                        .addValue("entityId", entry.entityId())
                        .addValue("operationType", entry.operationType())
                        .addValue("changedFields", entry.changedFields())
                        .addValue("beforeValueJson", entry.beforeValueJson())
                        .addValue("afterValueJson", entry.afterValueJson())
                        .addValue("userAgent", entry.userAgent())
                        .addValue("errorMessage", entry.errorMessage())
                        .addValue("traceId", entry.traceId())
                        .addValue("occurredAt", entry.occurredAt()));
    }

    @Override
    public PageResult<AdminAuditLogListItem> search(AdminAuditLogFilter filter, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        applyFilters(filter, params, where);

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tbl_fact_access_log" + where,
                params, Long.class);

        params.addValue("limit", size).addValue("offset", (long) page * size);
        List<AdminAuditLogListItem> items = jdbc.query("""
                SELECT access_log_id, occurred_at, actor_username, module, entity_type, entity_id,
                       operation_type
                FROM tbl_fact_access_log
                """ + where + """
                ORDER BY occurred_at DESC
                LIMIT :limit OFFSET :offset
                """,
                params, AdminAuditLogJdbcAdapter::mapListItem);

        long totalElements = total == null ? 0 : total;
        int totalPages = size <= 0 ? 0 : (int) Math.ceil(totalElements / (double) size);
        return new PageResult<>(items, page, size, totalElements, totalPages);
    }

    @Override
    public Optional<AdminAuditLogDetail> findById(UUID id) {
        return jdbc.query("""
                SELECT access_log_id, occurred_at, actor_user_id, actor_username, actor_role_name,
                       module, entity_type, entity_id, operation_type,
                       changed_fields, before_value::text AS before_value, after_value::text AS after_value,
                        user_agent, error_message, trace_id
                FROM tbl_fact_access_log
                WHERE access_log_id = :id
                """,
                new MapSqlParameterSource("id", id),
                rs -> rs.next() ? Optional.of(mapDetail(rs)) : Optional.empty());
    }

    private void applyFilters(AdminAuditLogFilter filter, MapSqlParameterSource params, StringBuilder where) {
        if (filter == null) {
            return;
        }
        if (filter.module() != null && !filter.module().isBlank()) {
            where.append(" AND module = :module ");
            params.addValue("module", filter.module());
        }
        if (filter.operationType() != null && !filter.operationType().isBlank()) {
            where.append(" AND operation_type = :operationType ");
            params.addValue("operationType", filter.operationType());
        }
        if (filter.actor() != null && !filter.actor().isBlank()) {
            where.append(" AND actor_username ILIKE :actor ");
            params.addValue("actor", "%" + filter.actor() + "%");
        }
        if (filter.dateFrom() != null) {
            where.append(" AND occurred_at >= :dateFrom ");
            params.addValue("dateFrom", filter.dateFrom());
        }
        if (filter.dateTo() != null) {
            where.append(" AND occurred_at <= :dateTo ");
            params.addValue("dateTo", filter.dateTo());
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            where.append(" AND (entity_id ILIKE :search OR actor_username ILIKE :search) ");
            params.addValue("search", "%" + filter.search() + "%");
        }
    }

    private static AdminAuditLogListItem mapListItem(ResultSet rs, int rowNum) throws SQLException {
        return new AdminAuditLogListItem(
                (UUID) rs.getObject("access_log_id"),
                rs.getObject("occurred_at", OffsetDateTime.class),
                rs.getString("actor_username"),
                rs.getString("module"),
                rs.getString("entity_type"),
                rs.getString("entity_id"),
                rs.getString("operation_type"));
    }

    private static AdminAuditLogDetail mapDetail(ResultSet rs) throws SQLException {
        return new AdminAuditLogDetail(
                (UUID) rs.getObject("access_log_id"),
                rs.getObject("occurred_at", OffsetDateTime.class),
                (UUID) rs.getObject("actor_user_id"),
                rs.getString("actor_username"),
                rs.getString("actor_role_name"),
                rs.getString("module"),
                rs.getString("entity_type"),
                rs.getString("entity_id"),
                rs.getString("operation_type"),
                rs.getString("changed_fields"),
                rs.getString("before_value"),
                rs.getString("after_value"),
                rs.getString("user_agent"),
                rs.getString("error_message"),
                rs.getString("trace_id"));
    }
}
