package com.sdd.platform.application.usecase.governance;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class AdminAuditLogModels {

    private AdminAuditLogModels() {
    }

    public record AdminAuditLogEntry(
            UUID actorUserId,
            String actorUsername,
            String actorRoleName,
            String module,
            String entityType,
            String entityId,
            String operationType,
            String changedFields,
            String beforeValueJson,
            String afterValueJson,
            String userAgent,
            String errorMessage,
            String traceId,
            OffsetDateTime occurredAt
    ) {
    }

    public record AdminAuditLogFilter(
            String module,
            String operationType,
            String actor,
            OffsetDateTime dateFrom,
            OffsetDateTime dateTo,
            String search
    ) {
    }

    public record AdminAuditLogListItem(
            UUID id,
            OffsetDateTime occurredAt,
            String actorUsername,
            String module,
            String entityType,
            String entityId,
            String operationType
    ) {
    }

    public record AdminAuditLogDetail(
            UUID id,
            OffsetDateTime occurredAt,
            UUID actorUserId,
            String actorUsername,
            String actorRoleName,
            String module,
            String entityType,
            String entityId,
            String operationType,
            String changedFields,
            String beforeValueJson,
            String afterValueJson,
            String userAgent,
            String errorMessage,
            String traceId
    ) {
    }
}
