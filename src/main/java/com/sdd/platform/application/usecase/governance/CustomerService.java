package com.sdd.platform.application.usecase.governance;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.exception.OptimisticLockingException;
import com.sdd.platform.application.port.out.persistence.CustomerRepositoryPort;
import com.sdd.platform.application.port.out.persistence.OrganizationRepositoryPort;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.Customer;
import com.sdd.platform.domain.model.Organization;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class CustomerService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String MODULE = "CUSTOMER";
    private static final String ENTITY_TYPE = "CUSTOMER";

    private final CustomerRepositoryPort customerRepository;
    private final OrganizationRepositoryPort organizationRepository;
    private final AdminAuditLogService adminAuditLogService;

    public CustomerService(CustomerRepositoryPort customerRepository, OrganizationRepositoryPort organizationRepository,
            AdminAuditLogService adminAuditLogService) {
        this.customerRepository = customerRepository;
        this.organizationRepository = organizationRepository;
        this.adminAuditLogService = adminAuditLogService;
    }

    @Transactional(readOnly = true)
    public PageResult<Customer> search(
            UUID organizationId,
            String keyword,
            String classification,
            String status,
            int page,
            int pageSize,
            AppUser caller
    ) {
        requireAdmin(caller);
        int normalizedPage = Math.max(page, 0);
        int normalizedPageSize = normalizePageSize(pageSize);
        String normalizedKeyword = trimToNull(keyword);
        String normalizedClassification = normalizeClassificationFilter(classification);
        String normalizedStatus = normalizeStatusFilter(status);
        int offset = normalizedPage * normalizedPageSize;
        List<Customer> items = customerRepository.findPage(
                organizationId,
                normalizedKeyword,
                normalizedClassification,
                normalizedStatus,
                offset,
                normalizedPageSize
        );
        long totalElements = customerRepository.count(
                organizationId,
                normalizedKeyword,
                normalizedClassification,
                normalizedStatus
        );
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / normalizedPageSize);
        return new PageResult<>(items, normalizedPage, normalizedPageSize, totalElements, totalPages);
    }

    @Transactional(readOnly = true)
    public Customer get(UUID customerId, AppUser caller) {
        requireAdmin(caller);
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException("Pages.Customer.NotFound"));
        return customer;
    }

    @Transactional
    public Customer create(UUID organizationId, String customerCode, String customerAlias, String classification, AppUser caller) {
        requireAdmin(caller);
        try {
            Organization organization = resolveActiveOrganization(organizationId);
            String normalizedCode = normalizeRequiredCode(customerCode);
            String normalizedAlias = normalizeRequiredAlias(customerAlias);
            Customer.CustomerClassification normalizedClassification = normalizeClassification(classification);
            ensureUniqueCode(normalizedCode, null);
            ensureUniqueAlias(organization.getOrganizationId(), normalizedAlias, null);

            String actor = resolveActor(caller);
            OffsetDateTime now = OffsetDateTime.now();
            Customer customer = Customer.builder()
                    .customerId(UUID.randomUUID())
                    .organizationId(organization.getOrganizationId())
                    .organizationName(organization.getOrganizationName())
                    .customerCode(normalizedCode)
                    .customerAlias(normalizedAlias)
                    .classification(normalizedClassification)
                    .status(Customer.CustomerStatus.ACTIVE)
                    .createdAt(now)
                    .createdBy(actor)
                    .updatedAt(now)
                    .updatedBy(actor)
                    .version(0L)
                    .build();
            Customer created = customerRepository.insert(customer);
            adminAuditLogService.logCreate(caller, MODULE, ENTITY_TYPE, created.getCustomerId().toString(), created);
            return created;
        } catch (RuntimeException ex) {
            adminAuditLogService.logCrudFailure(caller, MODULE, ENTITY_TYPE, null, "CREATE", ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    public Customer update(
            UUID customerId,
            UUID organizationId,
            String customerCode,
            String customerAlias,
            String classification,
            long version,
            AppUser caller
    ) {
        requireAdmin(caller);
        try {
            Customer existing = customerRepository.findById(customerId)
                    .orElseThrow(() -> new NotFoundException("Pages.Customer.NotFound"));
            ensureEditable(existing);
            Map<String, Object> beforeSnapshot = adminAuditLogService.snapshot(existing);

            Organization organization = resolveActiveOrganization(organizationId);
            String normalizedCode = normalizeRequiredCode(customerCode);
            String normalizedAlias = normalizeRequiredAlias(customerAlias);
            Customer.CustomerClassification normalizedClassification = normalizeClassification(classification);
            ensureUniqueCode(normalizedCode, customerId);
            ensureUniqueAlias(organization.getOrganizationId(), normalizedAlias, customerId);

            String actor = resolveActor(caller);
            existing.setOrganizationId(organization.getOrganizationId());
            existing.setOrganizationName(organization.getOrganizationName());
            existing.setCustomerCode(normalizedCode);
            existing.setCustomerAlias(normalizedAlias);
            existing.setClassification(normalizedClassification);
            existing.setVersion(version);
            existing.setUpdatedBy(actor);
            existing.setUpdatedAt(OffsetDateTime.now());

            int affected = customerRepository.update(existing);
            if (affected == 0) {
                throw new OptimisticLockingException("Pages.Customer.Conflict.Version");
            }
            Customer updated = customerRepository.findById(customerId)
                    .orElseThrow(() -> new NotFoundException("Pages.Customer.NotFound"));
            adminAuditLogService.logUpdate(caller, MODULE, ENTITY_TYPE, customerId.toString(), beforeSnapshot, updated);
            return updated;
        } catch (RuntimeException ex) {
            adminAuditLogService.logCrudFailure(caller, MODULE, ENTITY_TYPE, customerId.toString(), "UPDATE", ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    public Customer softDelete(UUID customerId, long version, AppUser caller) {
        requireAdmin(caller);
        try {
            Customer existing = customerRepository.findById(customerId)
                    .orElseThrow(() -> new NotFoundException("Pages.Customer.NotFound"));
            if (existing.isDeleted()) {
                throw new BusinessRuleException("Pages.Customer.AlreadyDeleted");
            }

            String actor = resolveActor(caller);
            OffsetDateTime now = OffsetDateTime.now();
            int affected = customerRepository.softDelete(customerId, version, actor, now, actor, now);
            if (affected == 0) {
                throw new OptimisticLockingException("Pages.Customer.Conflict.Version");
            }
            Customer deleted = customerRepository.findById(customerId)
                    .orElseThrow(() -> new NotFoundException("Pages.Customer.NotFound"));
            adminAuditLogService.logDelete(caller, MODULE, ENTITY_TYPE, customerId.toString(), existing);
            return deleted;
        } catch (RuntimeException ex) {
            adminAuditLogService.logCrudFailure(caller, MODULE, ENTITY_TYPE, customerId.toString(), "DELETE", ex.getMessage());
            throw ex;
        }
    }

    private void requireAdmin(AppUser caller) {
        if (caller == null || caller.getRole() != AppUser.Role.ADMIN) {
            throw new ForbiddenException("Pages.Customer.Error.Forbidden");
        }
    }

    private Organization resolveActiveOrganization(UUID organizationId) {
        if (organizationId == null) {
            throw new BusinessRuleException("Pages.Customer.Organization.Required");
        }
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new NotFoundException("Pages.Customer.Organization.NotFound"));
        if (organization.getStatus() != Organization.OrganizationStatus.ACTIVE || organization.getDeletedAt() != null) {
            throw new BusinessRuleException("Pages.Customer.Organization.Unavailable");
        }
        return organization;
    }

    private void ensureEditable(Customer customer) {
        if (customer.isDeleted()) {
            throw new BusinessRuleException("Pages.Customer.Deleted.CannotEdit");
        }
    }

    private void ensureUniqueAlias(UUID organizationId, String customerAlias, UUID excludeCustomerId) {
        if (customerRepository.existsActiveAlias(organizationId, customerAlias, excludeCustomerId)) {
            throw new BusinessRuleException("Pages.Customer.Alias.Duplicate");
        }
    }

    private void ensureUniqueCode(String customerCode, UUID excludeCustomerId) {
        // Only active-scope duplicates are blocked; codes from soft-deleted Customers can be reused.
        if (customerRepository.existsActiveCode(customerCode, excludeCustomerId)) {
            throw new BusinessRuleException("Pages.Customer.Code.Duplicate");
        }
    }

    private String normalizeRequiredCode(String customerCode) {
        String normalized = trimToNull(customerCode);
        if (normalized == null) {
            throw new BusinessRuleException("Pages.Customer.Code.Required");
        }
        if (normalized.length() > 50) {
            throw new BusinessRuleException("Pages.Customer.Code.MaxLength");
        }
        return normalized;
    }

    private String normalizeRequiredAlias(String customerAlias) {
        String normalized = trimToNull(customerAlias);
        if (normalized == null) {
            throw new BusinessRuleException("Pages.Customer.Alias.Required");
        }
        if (normalized.length() > 255) {
            throw new BusinessRuleException("Pages.Customer.Alias.MaxLength");
        }
        return normalized;
    }

    private Customer.CustomerClassification normalizeClassification(String classification) {
        if (classification == null || classification.isBlank()) {
            return Customer.CustomerClassification.INTERNAL;
        }
        try {
            return Customer.CustomerClassification.valueOf(classification.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Pages.Customer.Classification.Invalid");
        }
    }

    private String normalizeClassificationFilter(String classification) {
        String normalized = trimToNull(classification);
        if (normalized == null) {
            return null;
        }
        if ("ALL".equalsIgnoreCase(normalized)) {
            return "ALL";
        }
        return normalizeClassification(normalized).name();
    }

    private String normalizeStatusFilter(String status) {
        String normalized = trimToNull(status);
        if (normalized == null) {
            return "ACTIVE";
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!"ALL".equals(upper) && !"ACTIVE".equals(upper) && !"DELETED".equals(upper)) {
            throw new BusinessRuleException("Pages.Customer.Status.Invalid");
        }
        return upper;
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
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
