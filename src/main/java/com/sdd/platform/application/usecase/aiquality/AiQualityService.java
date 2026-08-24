package com.sdd.platform.application.usecase.aiquality;

import com.sdd.platform.application.exception.ConflictException;
import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.AiQualityRepositoryPort;
import com.sdd.platform.application.port.out.persistence.ProjectRepositoryPort;
import com.sdd.platform.application.port.out.persistence.RepositoryRepositoryPort;
import com.sdd.platform.application.port.out.persistence.QaDashboardRepositoryPort;
import com.sdd.platform.application.port.out.persistence.TicketLookupPort;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.application.usecase.governance.AdminAuditLogService;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AiQualityModel;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.AuthUserContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
public class AiQualityService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final BigDecimal RATE_MIN = BigDecimal.ZERO;
    private static final BigDecimal RATE_MAX = new BigDecimal("100.00");
    private static final String MODULE = "AI_QUALITY";
    private static final String ENTITY_TYPE = "AI_QUALITY";

    private final AiQualityRepositoryPort repository;
    private final ProjectRepositoryPort projectRepository;
    private final RepositoryRepositoryPort repositoryRepository;
    private final TicketLookupPort ticketLookup;
    private final QaDashboardRepositoryPort qaDashboardRepository;
    private final AdminAuditLogService adminAuditLogService;

    public AiQualityService(
            AiQualityRepositoryPort repository,
            ProjectRepositoryPort projectRepository,
            RepositoryRepositoryPort repositoryRepository,
            TicketLookupPort ticketLookup,
            QaDashboardRepositoryPort qaDashboardRepository,
            AdminAuditLogService adminAuditLogService
    ) {
        this.repository = repository;
        this.projectRepository = projectRepository;
        this.repositoryRepository = repositoryRepository;
        this.ticketLookup = ticketLookup;
        this.qaDashboardRepository = qaDashboardRepository;
        this.adminAuditLogService = adminAuditLogService;
    }

    @Transactional(readOnly = true)
    public PageResult<AiQualityModel> search(
            UUID projectId, UUID repositoryId, UUID ticketId, String search, int page, int size, AuthUserContext caller
    ) {
        requireViewAccess(caller, projectId);
        String normalizedSearch = normalizeSearch(search);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = normalizePageSize(size);
        int offset = normalizedPage * normalizedSize;
        var items = repository.findPage(projectId, repositoryId, ticketId, normalizedSearch, offset, normalizedSize);
        long total = repository.count(projectId, repositoryId, ticketId, normalizedSearch);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / normalizedSize);
        return new PageResult<>(items, normalizedPage, normalizedSize, total, totalPages);
    }

    /** Route-entry gate for the FE screen guard: does the caller have any (view or mutate) access to this project? */
    @Transactional(readOnly = true)
    public void requireAccess(UUID projectId, AuthUserContext caller) {
        requireViewAccess(caller, projectId);
    }

    /**
     * Route-entry gate for the FE screen guard when no project is selected yet
     * (e.g. first render before the page's own effect auto-selects a project
     * and syncs it into the URL, or after RoleTabs navigates here without
     * carrying over ?projectId=...). Mirrors
     * TicketBugMetricsService.requireAnyAccess: any authenticated caller
     * passes, regardless of role — resolveAccess cannot be evaluated without
     * a projectId, so this intentionally does not attempt project-role
     * resolution.
     */
    public void requireAnyAccess(AuthUserContext caller) {
        if (caller == null) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }

    @Transactional(readOnly = true)
    public AiQualityModel get(UUID ticketAiQualityId, AuthUserContext caller) {
        AiQualityModel item = loadActiveOrThrow(ticketAiQualityId);
        requireViewAccess(caller, item.getProjectId());
        return item;
    }

    /** Single-record lookup for a ticket's active AI-quality row, used by ticket-detail screens that only know the ticketId. */
    @Transactional(readOnly = true)
    public java.util.Optional<AiQualityModel> getByTicketId(UUID ticketId, AuthUserContext caller) {
        var model = repository.findActiveByTicketId(ticketId);
        model.ifPresent(m -> requireViewAccess(caller, m.getProjectId()));
        return model;
    }

    @Transactional
    public AiQualityModel create(UUID projectId, UUID repositoryId, UUID ticketId, BigDecimal aiQualityRate, AuthUserContext caller) {
        requireMutateAccess(caller, projectId);
        try {
            Payload payload = normalize(projectId, repositoryId, ticketId, aiQualityRate);
            ensureProjectActive(payload.projectId());
            ensureRepositoryBelongsToProject(payload.projectId(), payload.repositoryId());
            ensureTicketBelongsToRepository(payload.repositoryId(), payload.ticketId());
            if (repository.existsActiveByTicketId(payload.ticketId())) {
                throw new ConflictException("Pages.AiQuality.Ticket.AlreadyExists");
            }
            String actor = resolveActor(caller);
            OffsetDateTime now = OffsetDateTime.now();
            AiQualityModel entity = AiQualityModel.builder()
                    .ticketAiQualityId(UUID.randomUUID())
                    .projectId(payload.projectId())
                    .repositoryId(payload.repositoryId())
                    .ticketId(payload.ticketId())
                    .aiQualityRate(payload.aiQualityRate())
                    .status(AiQualityModel.Status.ACTIVE)
                    .deleteFlag(false)
                    .createdAt(now)
                    .createdBy(actor)
                    .updatedAt(now)
                    .updatedBy(actor)
                    .build();
            try {
                repository.insert(entity);
            } catch (DataIntegrityViolationException ex) {
                throw new ConflictException("Pages.AiQuality.Ticket.AlreadyExists");
            }
            AiQualityModel created = repository.findById(entity.getTicketAiQualityId())
                    .orElseThrow(() -> new NotFoundException("Pages.AiQuality.NotFound"));
            adminAuditLogService.logCreate(toAppUser(caller), MODULE, ENTITY_TYPE, created.getTicketAiQualityId().toString(), created);
            return created;
        } catch (RuntimeException ex) {
            adminAuditLogService.logCrudFailure(toAppUser(caller), MODULE, ENTITY_TYPE, null, "CREATE", ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    public AiQualityModel update(UUID ticketAiQualityId, BigDecimal aiQualityRate, AuthUserContext caller) {
        AiQualityModel existing = loadActiveOrThrow(ticketAiQualityId);
        requireMutateAccess(caller, existing.getProjectId());
        try {
            var beforeSnapshot = adminAuditLogService.snapshot(existing);
            BigDecimal normalizedRate = normalizeRate(aiQualityRate);
            existing.setAiQualityRate(normalizedRate);
            existing.setUpdatedAt(OffsetDateTime.now());
            existing.setUpdatedBy(resolveActor(caller));
            int affected = repository.update(existing);
            if (affected == 0) {
                throw new NotFoundException("Pages.AiQuality.NotFound");
            }
            AiQualityModel updated = repository.findById(ticketAiQualityId)
                    .orElseThrow(() -> new NotFoundException("Pages.AiQuality.NotFound"));
            adminAuditLogService.logUpdate(toAppUser(caller), MODULE, ENTITY_TYPE, ticketAiQualityId.toString(), beforeSnapshot, updated);
            return updated;
        } catch (RuntimeException ex) {
            adminAuditLogService.logCrudFailure(toAppUser(caller), MODULE, ENTITY_TYPE, ticketAiQualityId.toString(), "UPDATE", ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    public AiQualityModel softDelete(UUID ticketAiQualityId, AuthUserContext caller) {
        AiQualityModel existing = loadActiveOrThrow(ticketAiQualityId);
        requireMutateAccess(caller, existing.getProjectId());
        try {
            String actor = resolveActor(caller);
            OffsetDateTime now = OffsetDateTime.now();
            int affected = repository.softDelete(ticketAiQualityId, actor, now, actor, now);
            if (affected == 0) {
                throw new NotFoundException("Pages.AiQuality.NotFound");
            }
            AiQualityModel deleted = repository.findById(ticketAiQualityId)
                    .orElseThrow(() -> new NotFoundException("Pages.AiQuality.NotFound"));
            adminAuditLogService.logDelete(toAppUser(caller), MODULE, ENTITY_TYPE, ticketAiQualityId.toString(), existing);
            return deleted;
        } catch (RuntimeException ex) {
            adminAuditLogService.logCrudFailure(toAppUser(caller), MODULE, ENTITY_TYPE, ticketAiQualityId.toString(), "DELETE", ex.getMessage());
            throw ex;
        }
    }

    // ── Role gating ─────────────────────────────────────────────────────────

    enum Access { MUTATE, VIEW_ONLY, NONE }

    Access resolveAccess(AuthUserContext caller, UUID projectId) {
        if (caller == null || caller.getRole() == null || caller.getRole().isBlank()) {
            return Access.NONE;
        }
        String role = caller.getRole().trim().toUpperCase(Locale.ROOT);
        if ("ADMIN".equals(role)) {
            return Access.MUTATE;
        }
        if (projectId == null) {
            return Access.NONE;
        }
        String projectRole = qaDashboardRepository.findProjectRole(caller, projectId);
        if (projectRole == null) {
            return Access.NONE;
        }
        String normalizedProjectRole = projectRole.trim().toUpperCase(Locale.ROOT);
        if ("PM".equals(normalizedProjectRole) || "QA".equals(normalizedProjectRole)) {
            return Access.MUTATE;
        }
        if ("DEV".equals(normalizedProjectRole)) {
            return Access.VIEW_ONLY;
        }
        return Access.NONE;
    }

    /**
     * Unlike TicketBugMetricsService (where requireMutateAccess for create runs
     * before the try block, but for update/softDelete runs inside it), all
     * mutate/view checks here run before the audit try block consistently: a
     * permission denial is not a business-operation failure and should not
     * generate a logCrudFailure audit row.
     */
    private void requireViewAccess(AuthUserContext caller, UUID projectId) {
        if (resolveAccess(caller, projectId) == Access.NONE) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }

    private void requireMutateAccess(AuthUserContext caller, UUID projectId) {
        if (resolveAccess(caller, projectId) != Access.MUTATE) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }

    // ── Validation ──────────────────────────────────────────────────────────

    private AiQualityModel loadActiveOrThrow(UUID ticketAiQualityId) {
        AiQualityModel item = repository.findById(ticketAiQualityId)
                .orElseThrow(() -> new NotFoundException("Pages.AiQuality.NotFound"));
        if (item.isDeleted()) {
            throw new NotFoundException("Pages.AiQuality.NotFound");
        }
        return item;
    }

    /** projectId well-formed but non-existent/inactive is a validation error (400), not 404 — 404 is reserved for the AI Quality row itself. */
    private void ensureProjectActive(UUID projectId) {
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessRuleException("Pages.AiQuality.Project.NotFound"));
        if (project.isDeleted()) {
            throw new BusinessRuleException("Pages.AiQuality.Project.NotFound");
        }
    }

    /** BR-3: repository_id must belong to project_id. */
    private void ensureRepositoryBelongsToProject(UUID projectId, UUID repositoryId) {
        var repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new BusinessRuleException("Pages.AiQuality.Repository.NotBelongToProject"));
        if (repo.isDeleted() || !projectId.equals(repo.getProjectId())) {
            throw new BusinessRuleException("Pages.AiQuality.Repository.NotBelongToProject");
        }
    }

    /** BR-3: ticket_id must belong to repository_id. */
    private void ensureTicketBelongsToRepository(UUID repositoryId, UUID ticketId) {
        if (!ticketLookup.existsTicketInRepository(ticketId, repositoryId)) {
            throw new BusinessRuleException("Pages.AiQuality.Ticket.NotBelongToRepository");
        }
    }

    private Payload normalize(UUID projectId, UUID repositoryId, UUID ticketId, BigDecimal aiQualityRate) {
        if (projectId == null) {
            throw new BusinessRuleException("Pages.AiQuality.Project.Required");
        }
        if (repositoryId == null) {
            throw new BusinessRuleException("Pages.AiQuality.Repository.Required");
        }
        if (ticketId == null) {
            throw new BusinessRuleException("Pages.AiQuality.Ticket.Required");
        }
        BigDecimal normalizedRate = normalizeRate(aiQualityRate);
        return new Payload(projectId, repositoryId, ticketId, normalizedRate);
    }

    /** BR-6: reject null, reject outside [0.00, 100.00], reject scale > 2. */
    private BigDecimal normalizeRate(BigDecimal aiQualityRate) {
        if (aiQualityRate == null) {
            throw new BusinessRuleException("Pages.AiQuality.Rate.Required");
        }
        if (aiQualityRate.scale() > 2) {
            throw new BusinessRuleException("Pages.AiQuality.Rate.Invalid");
        }
        BigDecimal normalized = aiQualityRate.setScale(2, RoundingMode.UNNECESSARY);
        if (normalized.compareTo(RATE_MIN) < 0 || normalized.compareTo(RATE_MAX) > 0) {
            throw new BusinessRuleException("Pages.AiQuality.Rate.Invalid");
        }
        return normalized;
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim();
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String resolveActor(AuthUserContext caller) {
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

    /**
     * AdminAuditLogService's CRUD hooks only accept AppUser (a separate identity
     * model from AuthUserContext, predating this feature). Synthesize a
     * throwaway AppUser purely to satisfy that existing best-effort logging
     * signature — never used for access decisions, which stay on AuthUserContext.
     */
    private AppUser toAppUser(AuthUserContext caller) {
        if (caller == null) {
            return null;
        }
        AppUser.Role mappedRole = mapRole(caller.getRole());
        return AppUser.builder()
                .provider("internal")
                .providerUid(caller.getUserAccountId() == null ? null : caller.getUserAccountId().toString())
                .email(caller.getEmail())
                .displayName(caller.getDisplayName())
                .role(mappedRole)
                .active(true)
                .build();
    }

    private AppUser.Role mapRole(String role) {
        if (role == null) {
            return null;
        }
        try {
            return AppUser.Role.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private record Payload(UUID projectId, UUID repositoryId, UUID ticketId, BigDecimal aiQualityRate) {}
}

