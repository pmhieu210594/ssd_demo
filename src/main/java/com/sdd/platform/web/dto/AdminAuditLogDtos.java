package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.application.usecase.governance.AdminAuditLogModels.AdminAuditLogDetail;
import com.sdd.platform.application.usecase.governance.AdminAuditLogModels.AdminAuditLogListItem;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AdminAuditLogDtos {

    private AdminAuditLogDtos() {
    }

    public record AdminAuditLogPageDto(
            List<AdminAuditLogListItemDto> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static AdminAuditLogPageDto from(PageResult<AdminAuditLogListItem> result) {
            return new AdminAuditLogPageDto(
                    result.items().stream().map(AdminAuditLogListItemDto::from).toList(),
                    result.page(),
                    result.size(),
                    result.totalElements(),
                    result.totalPages()
            );
        }
    }

    public record AdminAuditLogListItemDto(
            UUID id,
            OffsetDateTime occurredAt,
            String actorUsername,
            String module,
            String entityType,
            String entityId,
            String operationType
    ) {
        public static AdminAuditLogListItemDto from(AdminAuditLogListItem item) {
            return new AdminAuditLogListItemDto(
                    item.id(),
                    item.occurredAt(),
                    item.actorUsername(),
                    item.module(),
                    item.entityType(),
                    item.entityId(),
                    item.operationType()
            );
        }
    }

    public record AdminAuditLogDetailDto(
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
            String beforeValue,
            String afterValue,
            String userAgent,
            String errorMessage,
            String traceId
    ) {
        public static AdminAuditLogDetailDto from(AdminAuditLogDetail detail) {
            return new AdminAuditLogDetailDto(
                    detail.id(),
                    detail.occurredAt(),
                    detail.actorUserId(),
                    detail.actorUsername(),
                    detail.actorRoleName(),
                    detail.module(),
                    detail.entityType(),
                    detail.entityId(),
                    detail.operationType(),
                    detail.changedFields(),
                    detail.beforeValueJson(),
                    detail.afterValueJson(),
                    detail.userAgent(),
                    detail.errorMessage(),
                    detail.traceId());
        }
    }
}
