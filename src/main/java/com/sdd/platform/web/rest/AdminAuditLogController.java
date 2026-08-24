package com.sdd.platform.web.rest;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.usecase.governance.AdminAuditLogModels.AdminAuditLogFilter;
import com.sdd.platform.application.usecase.governance.AdminAuditLogService;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.web.dto.AdminAuditLogDtos;
import com.sdd.platform.web.security.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read-only admin audit-log screen (AC-ADMIN-AUDIT-LOG-8..10). GET-only by
 * design — see ticket-rules.md: no update/delete endpoints for this screen.
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditLogController {

    private final AdminAuditLogService service;

    public AdminAuditLogController(AdminAuditLogService service) {
        this.service = service;
    }

    @GetMapping
    public AdminAuditLogDtos.AdminAuditLogPageDto list(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dateTo,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser AppUser caller
    ) {
        requireAdmin(caller);
        AdminAuditLogFilter filter = new AdminAuditLogFilter(module, operationType, actor, dateFrom, dateTo, search);
        return AdminAuditLogDtos.AdminAuditLogPageDto.from(service.list(filter, Math.max(page, 0), normalizeSize(size)));
    }

    @GetMapping("/{id}")
    public AdminAuditLogDtos.AdminAuditLogDetailDto detail(@PathVariable UUID id, @CurrentUser AppUser caller) {
        requireAdmin(caller);
        return service.detail(id)
                .map(AdminAuditLogDtos.AdminAuditLogDetailDto::from)
                .orElseThrow(() -> new NotFoundException("Pages.AdminAuditLog.NotFound"));
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }

    private void requireAdmin(AppUser caller) {
        if (caller == null || caller.getRole() != AppUser.Role.ADMIN) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }
}
