package com.sdd.platform.application.usecase.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.port.out.persistence.AdminAuditLogPersistencePort;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.application.usecase.governance.AdminAuditLogModels.AdminAuditLogDetail;
import com.sdd.platform.application.usecase.governance.AdminAuditLogModels.AdminAuditLogEntry;
import com.sdd.platform.application.usecase.governance.AdminAuditLogModels.AdminAuditLogFilter;
import com.sdd.platform.application.usecase.governance.AdminAuditLogModels.AdminAuditLogListItem;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.AuthUserContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Central write/read orchestration for the admin audit trail (AC-ADMIN-AUDIT-LOG-1..10).
 * CRUD hooks run best-effort inside the caller's existing transaction (a failed
 * audit insert must never fail the business operation); login/logout hooks run
 * in their own independent transaction since auth flows have no surrounding
 * business transaction to join.
 */
@Service
public class AdminAuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditLogService.class);

    private final AdminAuditLogWriter writer;
    private final AdminAuditLogPersistencePort readPort;
    private final ObjectMapper objectMapper;

    public AdminAuditLogService(AdminAuditLogWriter writer, AdminAuditLogPersistencePort readPort, ObjectMapper objectMapper) {
        this.writer = writer;
        this.readPort = readPort;
        this.objectMapper = objectMapper;
    }

    public void logCreate(AppUser caller, String module, String entityType, String entityId, Object afterEntity) {
        writeCrud(caller, module, entityType, entityId, "CREATE", null, null,
                mask(toSnapshot(afterEntity)), null);
    }

    /**
     * {@code beforeSnapshot} must be captured via {@link #snapshot(Object)} by the
     * caller BEFORE mutating the entity in place (Role/Organization/... setters
     * mutate the same object update() receives, so a plain object reference taken
     * after mutation would alias the "after" state).
     */
    public void logUpdate(AppUser caller, String module, String entityType, String entityId,
            Map<String, Object> beforeSnapshot, Object afterEntity) {
        Map<String, Object> rawAfter = toSnapshot(afterEntity);
        writeCrud(caller, module, entityType, entityId, "UPDATE",
                diffChangedFields(beforeSnapshot, rawAfter), mask(beforeSnapshot), mask(rawAfter), null);
    }

    /** Capture an immutable field snapshot before mutating an entity in place, for use as logUpdate's beforeSnapshot. */
    public Map<String, Object> snapshot(Object entity) {
        return toSnapshot(entity);
    }

    public void logDelete(AppUser caller, String module, String entityType, String entityId, Object beforeEntity) {
        writeCrud(caller, module, entityType, entityId, "DELETE", null,
                mask(toSnapshot(beforeEntity)), null, null);
    }

    /** Detail-view reads only (per human decision 2026-07-09); list views are not logged. */
    public void logRead(AppUser caller, String module, String entityType, String entityId) {
        writeCrud(caller, module, entityType, entityId, "READ", null, null, null, null);
    }

    public void logCrudFailure(AppUser caller, String module, String entityType, String entityId,
            String operationType, String errorMessage) {
        writeCrud(caller, module, entityType, entityId, operationType, null, null, null,
                sanitizeErrorMessage(errorMessage));
    }

    public void logLoginSuccess(AuthUserContext context) {
        AdminAuditLogEntry entry = new AdminAuditLogEntry(
                context.getUserAccountId(),
                context.getUsername(),
                context.getRole(),
                "LOGIN",
                "LOGIN_SESSION",
                null,
                "LOGIN_SUCCESS",
                null, null, null,
                currentUserAgent(),
                null,
                currentTraceId(),
                OffsetDateTime.now());
        insertIndependently(entry);
    }

    public void logLoginFailure(String attemptedUsername, String errorMessage) {
        AdminAuditLogEntry entry = new AdminAuditLogEntry(
                null,
                attemptedUsername,
                null,
                "LOGIN",
                "LOGIN_SESSION",
                null,
                "LOGIN_FAILED",
                null, null, null,
                currentUserAgent(),
                sanitizeErrorMessage(errorMessage),
                currentTraceId(),
                OffsetDateTime.now());
        insertIndependently(entry);
    }

    public void logLogout(AuthUserContext context) {
        if (context == null) {
            return;
        }
        AdminAuditLogEntry entry = new AdminAuditLogEntry(
                context.getUserAccountId(),
                context.getUsername(),
                context.getRole(),
                "LOGIN",
                "LOGIN_SESSION",
                null,
                "LOGOUT",
                null, null, null,
                currentUserAgent(),
                null,
                currentTraceId(),
                OffsetDateTime.now());
        insertIndependently(entry);
    }

    public PageResult<AdminAuditLogListItem> list(AdminAuditLogFilter filter, int page, int size) {
        return readPort.search(filter, page, size);
    }

    public Optional<AdminAuditLogDetail> detail(UUID id) {
        return readPort.findById(id);
    }

    private void writeCrud(AppUser caller, String module, String entityType, String entityId,
            String operationType, String changedFields,
            Map<String, Object> maskedBefore, Map<String, Object> maskedAfter, String errorMessage) {
        AdminAuditLogEntry entry = new AdminAuditLogEntry(
                actorUserId(caller),
                actorUsername(caller),
                actorRoleName(caller),
                module,
                entityType,
                entityId,
                operationType,
                changedFields,
                toJson(maskedBefore),
                toJson(maskedAfter),
                currentUserAgent(),
                errorMessage,
                currentTraceId(),
                OffsetDateTime.now());
        try {
            writer.insertWithinCallerTransaction(entry);
        } catch (RuntimeException ex) {
            log.warn("Audit log write failed for module={} entityType={} entityId={} operationType={}: {}",
                    module, entityType, entityId, operationType, ex.getMessage(), ex);
        }
    }

    private void insertIndependently(AdminAuditLogEntry entry) {
        try {
            writer.insertIndependently(entry);
        } catch (RuntimeException ex) {
            log.warn("Audit log write failed for module={} operationType={}: {}",
                    entry.module(), entry.operationType(), ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toSnapshot(Object entity) {
        if (entity == null) {
            return null;
        }
        return objectMapper.convertValue(entity, Map.class);
    }

    private Map<String, Object> mask(Map<String, Object> snapshot) {
        return AuditMaskingHelper.mask(snapshot);
    }

    private String toJson(Map<String, Object> snapshot) {
        if (snapshot == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception ex) {
            log.warn("Failed to serialize audit snapshot: {}", ex.getMessage());
            return null;
        }
    }

    private String diffChangedFields(Map<String, Object> rawBefore, Map<String, Object> rawAfter) {
        Map<String, Object> before = rawBefore == null ? Map.of() : rawBefore;
        Map<String, Object> after = rawAfter == null ? Map.of() : rawAfter;
        TreeSet<String> changed = new TreeSet<>();
        TreeSet<String> allKeys = new TreeSet<>();
        allKeys.addAll(before.keySet());
        allKeys.addAll(after.keySet());
        for (String key : allKeys) {
            Object beforeValue = before.get(key);
            Object afterValue = after.get(key);
            if (!java.util.Objects.equals(beforeValue, afterValue)) {
                changed.add(key);
            }
        }
        return changed.isEmpty() ? null : String.join(",", changed);
    }

    private String sanitizeErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return null;
        }
        String sanitized = errorMessage.replaceAll("(?i)(password|token|secret|api[_-]?key)[\\w-]*",
                "[redacted]");
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }

    private UUID actorUserId(AppUser caller) {
        if (caller == null || caller.getProviderUid() == null) {
            return null;
        }
        try {
            return UUID.fromString(caller.getProviderUid());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String actorUsername(AppUser caller) {
        if (caller == null) {
            return "SYSTEM";
        }
        if (caller.getEmail() != null && !caller.getEmail().isBlank()) {
            return caller.getEmail().trim();
        }
        if (caller.getDisplayName() != null && !caller.getDisplayName().isBlank()) {
            return caller.getDisplayName().trim();
        }
        return "SYSTEM";
    }

    private String actorRoleName(AppUser caller) {
        return caller == null || caller.getRole() == null ? null : caller.getRole().name();
    }

    private String currentTraceId() {
        return MDC.get("traceId");
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String currentIpAddress() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String currentUserAgent() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > 256 ? userAgent.substring(0, 256) : userAgent;
    }
}
