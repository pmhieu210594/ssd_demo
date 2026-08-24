package com.sdd.platform.application.usecase.governance;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.exception.OptimisticLockingException;
import com.sdd.platform.application.port.out.persistence.OrganizationRepositoryPort;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.Organization;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class OrganizationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String MODULE = "ORGANIZATION";
    private static final String ENTITY_TYPE = "ORGANIZATION";

    private final OrganizationRepositoryPort repository;
    private final AdminAuditLogService adminAuditLogService;

    public OrganizationService(OrganizationRepositoryPort repository, AdminAuditLogService adminAuditLogService) {
        this.repository = repository;
        this.adminAuditLogService = adminAuditLogService;
    }

    @Transactional(readOnly = true)
    public PageResult<Organization> search(String keyword, String status, int page, int size, AppUser caller) {
        requireAdmin(caller);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = normalizePageSize(size);
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedStatus = normalizeStatusFilter(status);

        int offset = normalizedPage * normalizedSize;
        List<Organization> items = repository.findPage(normalizedKeyword, normalizedStatus, offset, normalizedSize);
        long totalElements = repository.count(normalizedKeyword, normalizedStatus);
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / normalizedSize);
        return new PageResult<>(items, normalizedPage, normalizedSize, totalElements, totalPages);
    }

    @Transactional(readOnly = true)
    public Organization get(UUID organizationId, AppUser caller) {
        requireAdmin(caller);
        Organization organization = repository.findById(organizationId)
                .orElseThrow(() -> new NotFoundException("Pages.Organization.NotFound"));
        return organization;
    }

    @Transactional
    public Organization create(String organizationCode, String organizationName, String description, AppUser caller) {
        requireAdmin(caller);
        try {
            String normalizedCode = normalizeRequired(organizationCode, 50, "Pages.Organization.Code.Required");
            String normalizedName = normalizeRequired(organizationName, 255, "Pages.Organization.Name.Required");
            String normalizedDescription = normalizeOptional(description, 500, "Pages.Organization.Description.MaxLength");
            String actor = resolveActor(caller);

            ensureUniqueCode(normalizedCode, null, false);
            ensureUniqueName(normalizedName, null);

            OffsetDateTime now = OffsetDateTime.now();
            Organization organization = Organization.builder()
                    .organizationId(UUID.randomUUID())
                    .organizationCode(normalizedCode)
                    .organizationName(normalizedName)
                    .description(normalizedDescription)
                    .status(Organization.OrganizationStatus.ACTIVE)
                    .createdAt(now)
                    .createdBy(actor)
                    .updatedAt(now)
                    .updatedBy(actor)
                    .version(0L)
                    .build();
            Organization created = repository.insert(organization);
            adminAuditLogService.logCreate(caller, MODULE, ENTITY_TYPE, created.getOrganizationId().toString(), created);
            return created;
        } catch (RuntimeException ex) {
            adminAuditLogService.logCrudFailure(caller, MODULE, ENTITY_TYPE, null, "CREATE", ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    public Organization update(
            UUID organizationId,
            String organizationCode,
            String organizationName,
            String description,
            String status,
            long version,
            AppUser caller
    ) {
        requireAdmin(caller);
        try {
            Organization existing = repository.findById(organizationId)
                    .orElseThrow(() -> new NotFoundException("Pages.Organization.NotFound"));
            ensureEditable(existing);
            Map<String, Object> beforeSnapshot = adminAuditLogService.snapshot(existing);

            String normalizedCode = normalizeRequired(organizationCode, 50, "Pages.Organization.Code.Required");
            String normalizedName = normalizeRequired(organizationName, 255, "Pages.Organization.Name.Required");
            String normalizedDescription = normalizeOptional(description, 500, "Pages.Organization.Description.MaxLength");
            Organization.OrganizationStatus normalizedStatus = normalizeStatus(status);
            String actor = resolveActor(caller);

            ensureUniqueCode(normalizedCode, organizationId, true);
            ensureUniqueName(normalizedName, organizationId);

            existing.setOrganizationCode(normalizedCode);
            existing.setOrganizationName(normalizedName);
            existing.setDescription(normalizedDescription);
            existing.setStatus(normalizedStatus);
            if (normalizedStatus == Organization.OrganizationStatus.DELETED) {
                existing.setDeletedAt(OffsetDateTime.now());
                existing.setDeletedBy(actor);
            } else {
                existing.setDeletedAt(null);
                existing.setDeletedBy(null);
            }
            existing.setVersion(version);
            existing.setUpdatedBy(actor);
            existing.setUpdatedAt(OffsetDateTime.now());

            int affected = repository.update(existing);
            if (affected == 0) {
                throw new OptimisticLockingException("Pages.Organization.Conflict.Version");
            }
            Organization updated = repository.findById(organizationId)
                    .orElseThrow(() -> new NotFoundException("Pages.Organization.NotFound"));
            adminAuditLogService.logUpdate(caller, MODULE, ENTITY_TYPE, organizationId.toString(), beforeSnapshot, updated);
            return updated;
        } catch (RuntimeException ex) {
            adminAuditLogService.logCrudFailure(caller, MODULE, ENTITY_TYPE, organizationId.toString(), "UPDATE", ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    public Organization softDelete(UUID organizationId, long version, AppUser caller) {
        requireAdmin(caller);
        try {
            Organization existing = repository.findById(organizationId)
                    .orElseThrow(() -> new NotFoundException("Pages.Organization.NotFound"));
            if (existing.isDeleted()) {
                throw new BusinessRuleException("Pages.Organization.Deleted.AlreadyDeleted");
            }

            String actor = resolveActor(caller);
            OffsetDateTime now = OffsetDateTime.now();
            int affected = repository.softDelete(
                    organizationId,
                    version,
                    actor,
                    now,
                    actor,
                    now
            );
            if (affected == 0) {
                throw new OptimisticLockingException("Pages.Organization.Conflict.Version");
            }
            Organization deleted = repository.findById(organizationId)
                    .orElseThrow(() -> new NotFoundException("Pages.Organization.NotFound"));
            adminAuditLogService.logDelete(caller, MODULE, ENTITY_TYPE, organizationId.toString(), existing);
            return deleted;
        } catch (RuntimeException ex) {
            adminAuditLogService.logCrudFailure(caller, MODULE, ENTITY_TYPE, organizationId.toString(), "DELETE", ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    public int purgeTestDataByCodePrefix(String organizationCodePrefix, AppUser caller) {
        requireAdmin(caller);
        String normalizedPrefix = trimToNull(organizationCodePrefix);
        if (normalizedPrefix == null || !normalizedPrefix.startsWith("E2E_ORG_")) {
            throw new BusinessRuleException("Pages.Organization.TestSupport.InvalidPrefix");
        }
        return repository.deleteByCodePrefix(normalizedPrefix);
    }

    private void ensureEditable(Organization organization) {
        if (organization.isDeleted()) {
            throw new BusinessRuleException("Pages.Organization.Deleted.EditNotAllowed");
        }
    }

    private void ensureUniqueCode(String organizationCode, UUID excludeOrganizationId, boolean onUpdate) {
        if (repository.existsActiveCode(organizationCode, excludeOrganizationId)) {
            throw new BusinessRuleException(onUpdate
                    ? "Pages.Organization.Code.DuplicateOnUpdate"
                    : "Pages.Organization.Code.Duplicate");
        }
    }

    private void ensureUniqueName(String organizationName, UUID excludeOrganizationId) {
        if (repository.existsActiveName(organizationName, excludeOrganizationId)) {
            throw new BusinessRuleException("Pages.Organization.Name.Duplicate");
        }
    }

    private void requireAdmin(AppUser caller) {
        if (caller == null || caller.getRole() != AppUser.Role.ADMIN) {
            throw new ForbiddenException("Component.Permission.Denied");
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

    private String normalizeKeyword(String keyword) {
        return keyword == null ? null : trimToNull(keyword);
    }

    private String normalizeStatusFilter(String status) {
        String normalized = status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase(Locale.ROOT);
        if (!"ALL".equals(normalized) && !"ACTIVE".equals(normalized) && !"DELETED".equals(normalized)) {
            throw new BusinessRuleException("Pages.Organization.Status.Invalid");
        }
        return normalized;
    }

    private String normalizeRequired(String value, int maxLength, String emptyMessageKey) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessRuleException(emptyMessageKey);
        }
        if (normalized.length() > maxLength) {
            throw new BusinessRuleException(maxLength == 50
                    ? "Pages.Organization.Code.MaxLength"
                    : "Pages.Organization.Name.MaxLength");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength, String messageKey) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new BusinessRuleException(messageKey);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private Organization.OrganizationStatus normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return Organization.OrganizationStatus.ACTIVE;
        }
        try {
            return Organization.OrganizationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Pages.Organization.Status.Invalid");
        }
    }
}
