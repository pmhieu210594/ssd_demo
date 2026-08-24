package com.sdd.platform.application.usecase.governance;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.RoleRepositoryPort;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class RoleService {

    private static final int ROLE_NAME_MAX_LENGTH = 100;
    private static final String MODULE = "ROLE";
    private static final String ENTITY_TYPE = "ROLE";

    private final RoleRepositoryPort repository;
    private final AdminAuditLogService adminAuditLogService;

    public RoleService(RoleRepositoryPort repository, AdminAuditLogService adminAuditLogService) {
        this.repository = repository;
        this.adminAuditLogService = adminAuditLogService;
    }

    @Transactional(readOnly = true)
    public List<Role> list(String keyword, String status, AppUser caller) {
        requireCanView(caller);
        return repository.findActive(trimToNull(keyword), normalizeStatusFilter(status));
    }

    @Transactional(readOnly = true)
    public Role get(UUID roleId, AppUser caller) {
        requireCanView(caller);
        Role role = repository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("Pages.RoleManagement.NotFound"));
        return role;
    }

    @Transactional
    public Role create(String roleName, String description, AppUser caller) {
        requireCanMutate(caller);
        try {
            String normalizedName = normalizeRequired(roleName);
            String normalizedDescription = normalizeDescription(description);
            ensureUniqueName(normalizedName, null);
            String actor = resolveActor(caller);
            OffsetDateTime now = OffsetDateTime.now();

            Role role = Role.builder()
                    .roleId(UUID.randomUUID())
                    .roleName(normalizedName)
                    .description(normalizedDescription)
                    .createdAt(now)
                    .createdBy(actor)
                    .updatedAt(now)
                    .updatedBy(actor)
                    .deleteFlag(0)
                    .status(Role.RoleStatus.ACTIVE)
                    .build();
            Role created = repository.insert(role);
            adminAuditLogService.logCreate(caller, MODULE, ENTITY_TYPE, created.getRoleId().toString(), created);
            return created;
        } catch (RuntimeException ex) {
            adminAuditLogService.logCrudFailure(caller, MODULE, ENTITY_TYPE, null, "CREATE", ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    public Role update(UUID roleId, String roleName, String description, AppUser caller) {
        requireCanMutate(caller);
        try {
            Role existing = repository.findActiveById(roleId)
                    .orElseThrow(() -> new NotFoundException("Pages.RoleManagement.NotFound"));
            Map<String, Object> beforeSnapshot = adminAuditLogService.snapshot(existing);
            String normalizedName = normalizeRequired(roleName);
            String normalizedDescription = normalizeDescription(description);
            ensureUniqueName(normalizedName, roleId);

            existing.setRoleName(normalizedName);
            existing.setDescription(normalizedDescription);
            existing.setUpdatedAt(OffsetDateTime.now());
            existing.setUpdatedBy(resolveActor(caller));

            int affected = repository.update(existing);
            if (affected == 0) {
                throw new NotFoundException("Pages.RoleManagement.NotFound");
            }
            Role updated = repository.findActiveById(roleId)
                    .orElseThrow(() -> new NotFoundException("Pages.RoleManagement.NotFound"));
            adminAuditLogService.logUpdate(caller, MODULE, ENTITY_TYPE, roleId.toString(), beforeSnapshot, updated);
            return updated;
        } catch (RuntimeException ex) {
            adminAuditLogService.logCrudFailure(caller, MODULE, ENTITY_TYPE, roleId.toString(), "UPDATE", ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    public Role logicalDelete(UUID roleId, AppUser caller) {
        requireCanDelete(caller);
        try {
            Role before = repository.findActiveById(roleId)
                    .orElseThrow(() -> new NotFoundException("Pages.RoleManagement.NotFound"));
            int affected = repository.logicalDelete(roleId, resolveActor(caller), OffsetDateTime.now());
            if (affected == 0) {
                throw new NotFoundException("Pages.RoleManagement.NotFound");
            }
            Role deleted = repository.findById(roleId)
                    .orElseThrow(() -> new NotFoundException("Pages.RoleManagement.NotFound"));
            adminAuditLogService.logDelete(caller, MODULE, ENTITY_TYPE, roleId.toString(), before);
            return deleted;
        } catch (RuntimeException ex) {
            adminAuditLogService.logCrudFailure(caller, MODULE, ENTITY_TYPE, roleId.toString(), "DELETE", ex.getMessage());
            throw ex;
        }
    }

    private void requireCanView(AppUser caller) {
        if (caller == null || caller.getRole() == null) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }

    private void requireCanMutate(AppUser caller) {
        requireCanView(caller);
        if (caller.getRole() == AppUser.Role.VIEWER) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }

    private void requireCanDelete(AppUser caller) {
        requireCanView(caller);
        if (caller.getRole() != AppUser.Role.ADMIN) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }

    private String normalizeRequired(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessRuleException("Pages.RoleManagement.RoleName.Required");
        }
        if (normalized.length() > ROLE_NAME_MAX_LENGTH) {
            throw new BusinessRuleException("Pages.RoleManagement.RoleName.MaxLength");
        }
        return normalized;
    }

    private String normalizeDescription(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private String normalizeStatusFilter(String status) {
        String normalized = status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase(Locale.ROOT);
        if (!"ALL".equals(normalized) && !"ACTIVE".equals(normalized) && !"DELETED".equals(normalized)) {
            throw new BusinessRuleException("Pages.RoleManagement.Status.Invalid");
        }
        return normalized;
    }

    private void ensureUniqueName(String roleName, UUID excludeRoleId) {
        if (repository.existsActiveName(roleName, excludeRoleId)) {
            throw new BusinessRuleException("Pages.RoleManagement.RoleName.Duplicate");
        }
    }

    private String resolveActor(AppUser caller) {
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
