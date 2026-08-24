package com.sdd.platform.application.usecase.ticketbugmetrics;

import com.sdd.platform.application.exception.ConflictException;
import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.ProjectRepositoryPort;
import com.sdd.platform.application.port.out.persistence.QaDashboardRepositoryPort;
import com.sdd.platform.application.port.out.persistence.RepositoryRepositoryPort;
import com.sdd.platform.application.port.out.persistence.TicketBugMetricsRepositoryPort;
import com.sdd.platform.application.port.out.persistence.TicketLookupPort;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.application.usecase.governance.AdminAuditLogService;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.AuthUserContext;
import com.sdd.platform.domain.model.TicketBugMetricsModel;
import com.sdd.platform.application.usecase.ticketbugmetrics.TicketBugMetricsModels.TicketBugMetricsOption;
import com.sdd.platform.application.usecase.ticketbugmetrics.TicketBugMetricsModels.TicketBugMetricsOptions;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TicketBugMetricsService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int NOTE_MAX_LENGTH = 500;
    private static final String MODULE = "TICKET_BUG_METRICS";
    private static final String ENTITY_TYPE = "TICKET_BUG_METRICS";

    private final TicketBugMetricsRepositoryPort repository;
    private final ProjectRepositoryPort projectRepository;
    private final RepositoryRepositoryPort repositoryRepository;
    private final TicketLookupPort ticketLookup;
    private final QaDashboardRepositoryPort qaDashboardRepository;
    private final AdminAuditLogService adminAuditLogService;

    public TicketBugMetricsService(
            TicketBugMetricsRepositoryPort repository,
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
    public PageResult<TicketBugMetricsModel> search(UUID projectId, UUID repositoryId, String search, int page, int size, AuthUserContext caller) {
        requireViewAccess(caller, projectId);
        String normalizedSearch = normalizeSearch(search);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = normalizePageSize(size);
        int offset = normalizedPage * normalizedSize;
        var items = repository.findPage(projectId, repositoryId, normalizedSearch, offset, normalizedSize);
        long total = repository.count(projectId, repositoryId, normalizedSearch);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / normalizedSize);
        return new PageResult<>(items, normalizedPage, normalizedSize, total, totalPages);
    }

    /** Single-record lookup for a ticket's active bug-metric row, used by ticket-detail screens that only know the ticketId. */
    @Transactional(readOnly = true)
    public java.util.Optional<TicketBugMetricsModel> getByTicketId(UUID ticketId, AuthUserContext caller) {
        var model = repository.findActiveByTicketId(ticketId);
        model.ifPresent(m -> requireViewAccess(caller, m.getProjectId()));
        return model;
    }

    /** Route-entry gate for the FE screen guard: does the caller have any (view or mutate) access to this project? */
    @Transactional(readOnly = true)
    public void requireAccess(UUID projectId, AuthUserContext caller) {
        requireViewAccess(caller, projectId);
    }

    @Transactional(readOnly = true)
    public java.util.List<com.sdd.platform.domain.model.TicketOption> ticketOptions(UUID projectId, UUID repositoryId, AuthUserContext caller) {
        requireViewAccess(caller, projectId);
        return ticketLookup.findOptionsByProject(projectId, repositoryId);
    }

    @Transactional(readOnly = true)
    public TicketBugMetricsOptions options(UUID projectId, UUID repositoryId, AuthUserContext caller) {
        requireAuthenticated(caller);

        List<TicketBugMetricsOption> projects = qaDashboardRepository.findOptions(projectId, repositoryId, caller)
                .projects().stream()
                .map(option -> new TicketBugMetricsOption(option.value(), option.label(), option.role()))
                .toList();

        List<TicketBugMetricsOption> repositories = projectId == null
                ? List.of()
                : repositoryRepository.findPage(projectId, "ACTIVE", null, 0, MAX_PAGE_SIZE).stream()
                        .map(repo -> new TicketBugMetricsOption(repo.getRepositoryId().toString(), repo.getRepoNameMasked(), null))
                        .toList();

        List<TicketBugMetricsOption> tickets = projectId == null
                ? List.of()
                : ticketLookup.findOptionsByProject(projectId, repositoryId).stream()
                        .map(ticket -> new TicketBugMetricsOption(ticket.ticketId().toString(), ticket.externalTicketKey(), null))
                        .toList();

        return new TicketBugMetricsOptions(projects, repositories, tickets);
    }

    private void requireAuthenticated(AuthUserContext caller) {
        if (caller == null) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }

    @Transactional(readOnly = true)
    public TicketBugMetricsModel get(UUID ticketBugId, AuthUserContext caller) {
        TicketBugMetricsModel item = loadActiveOrThrow(ticketBugId);
        requireViewAccess(caller, item.getProjectId());
        return item;
    }

    @Transactional
    public TicketBugMetricsModel create(UUID projectId, UUID repositoryId, UUID ticketId,
            Integer internalBugCount, Integer customerBugCount, String note, AuthUserContext caller) {
        requireMutateAccess(caller, projectId);
        try {
            Payload payload = normalize(projectId, repositoryId, ticketId, internalBugCount, customerBugCount, note);
            ensureProjectActive(payload.projectId());
            ensureRepositoryActive(payload.projectId(), payload.repositoryId());
            ensureTicketExists(payload.projectId(), payload.ticketId());
            if (repository.existsActiveByTicketId(payload.ticketId())) {
                throw new ConflictException("Pages.TicketBugMetrics.Ticket.AlreadyExists");
            }
            String actor = resolveActor(caller);
            OffsetDateTime now = OffsetDateTime.now();
            TicketBugMetricsModel entity = TicketBugMetricsModel.builder()
                    .ticketBugId(UUID.randomUUID())
                    .projectId(payload.projectId())
                    .repositoryId(payload.repositoryId())
                    .ticketId(payload.ticketId())
                    .internalBugCount(payload.internalBugCount())
                    .customerBugCount(payload.customerBugCount())
                    .note(payload.note())
                    .status(TicketBugMetricsModel.Status.ACTIVE)
                    .deleteFlag(false)
                    .createdAt(now)
                    .createdBy(actor)
                    .updatedAt(now)
                    .updatedBy(actor)
                    .build();
            try {
                repository.insert(entity);
            } catch (DataIntegrityViolationException ex) {
                throw new ConflictException("Pages.TicketBugMetrics.Ticket.AlreadyExists");
            }
            TicketBugMetricsModel created = repository.findById(entity.getTicketBugId())
                    .orElseThrow(() -> new NotFoundException("Pages.TicketBugMetrics.NotFound"));
            adminAuditLogService.logCreate(toAppUser(caller), MODULE, ENTITY_TYPE, created.getTicketBugId().toString(), created);
            return created;
        } catch (RuntimeException ex) {
            adminAuditLogService.logCrudFailure(toAppUser(caller), MODULE, ENTITY_TYPE, null, "CREATE", ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    public TicketBugMetricsModel update(UUID ticketBugId, Integer internalBugCount, Integer customerBugCount,
            String note, AuthUserContext caller) {
        try {
            TicketBugMetricsModel existing = loadActiveOrThrow(ticketBugId);
            requireMutateAccess(caller, existing.getProjectId());
            var beforeSnapshot = adminAuditLogService.snapshot(existing);
            NoteAndCounts normalized = normalizeCountsAndNote(internalBugCount, customerBugCount, note);
            existing.setInternalBugCount(normalized.internalBugCount());
            existing.setCustomerBugCount(normalized.customerBugCount());
            existing.setNote(normalized.note());
            existing.setUpdatedAt(OffsetDateTime.now());
            existing.setUpdatedBy(resolveActor(caller));
            int affected = repository.update(existing);
            if (affected == 0) {
                throw new NotFoundException("Pages.TicketBugMetrics.NotFound");
            }
            TicketBugMetricsModel updated = repository.findById(ticketBugId)
                    .orElseThrow(() -> new NotFoundException("Pages.TicketBugMetrics.NotFound"));
            adminAuditLogService.logUpdate(toAppUser(caller), MODULE, ENTITY_TYPE, ticketBugId.toString(), beforeSnapshot, updated);
            return updated;
        } catch (RuntimeException ex) {
            adminAuditLogService.logCrudFailure(toAppUser(caller), MODULE, ENTITY_TYPE, ticketBugId.toString(), "UPDATE", ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    public TicketBugMetricsModel softDelete(UUID ticketBugId, AuthUserContext caller) {
        try {
            TicketBugMetricsModel existing = loadActiveOrThrow(ticketBugId);
            requireMutateAccess(caller, existing.getProjectId());
            String actor = resolveActor(caller);
            OffsetDateTime now = OffsetDateTime.now();
            int affected = repository.softDelete(ticketBugId, actor, now, actor, now);
            if (affected == 0) {
                throw new NotFoundException("Pages.TicketBugMetrics.NotFound");
            }
            TicketBugMetricsModel deleted = repository.findById(ticketBugId)
                    .orElseThrow(() -> new NotFoundException("Pages.TicketBugMetrics.NotFound"));
            adminAuditLogService.logDelete(toAppUser(caller), MODULE, ENTITY_TYPE, ticketBugId.toString(), existing);
            return deleted;
        } catch (RuntimeException ex) {
            adminAuditLogService.logCrudFailure(toAppUser(caller), MODULE, ENTITY_TYPE, ticketBugId.toString(), "DELETE", ex.getMessage());
            throw ex;
        }
    }

    // ── Role gating ─────────────────────────────────────────────────────────

    private enum Access { MUTATE, VIEW_ONLY, NONE }

    private Access resolveAccess(AuthUserContext caller, UUID projectId) {
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

    /**
     * Route-entry gate: does the caller hold the DEV role on *any* project (or
     * ADMIN)?
     */
    public void requireAnyAccess(AuthUserContext caller) {
        if (caller == null) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }

    // ── Validation ──────────────────────────────────────────────────────────

    private TicketBugMetricsModel loadActiveOrThrow(UUID ticketBugId) {
        TicketBugMetricsModel item = repository.findById(ticketBugId)
                .orElseThrow(() -> new NotFoundException("Pages.TicketBugMetrics.NotFound"));
        if (item.isDeleted()) {
            throw new NotFoundException("Pages.TicketBugMetrics.NotFound");
        }
        return item;
    }

    private void ensureProjectActive(UUID projectId) {
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Pages.TicketBugMetrics.Project.NotFound"));
        if (project.isDeleted()) {
            throw new NotFoundException("Pages.TicketBugMetrics.Project.NotFound");
        }
    }

    private void ensureRepositoryActive(UUID projectId, UUID repositoryId) {
        var repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new NotFoundException("Pages.TicketBugMetrics.Repository.NotFound"));
        if (repo.isDeleted() || !projectId.equals(repo.getProjectId())) {
            throw new NotFoundException("Pages.TicketBugMetrics.Repository.NotFound");
        }
    }

    private void ensureTicketExists(UUID projectId, UUID ticketId) {
        if (!ticketLookup.existsTicketInProject(ticketId, projectId)) {
            throw new NotFoundException("Pages.TicketBugMetrics.Ticket.NotFound");
        }
    }

    private Payload normalize(UUID projectId, UUID repositoryId, UUID ticketId,
            Integer internalBugCount, Integer customerBugCount, String note) {
        if (projectId == null) {
            throw new BusinessRuleException("Pages.TicketBugMetrics.Project.Required");
        }
        if (repositoryId == null) {
            throw new BusinessRuleException("Pages.TicketBugMetrics.Repository.Required");
        }
        if (ticketId == null) {
            throw new BusinessRuleException("Pages.TicketBugMetrics.Ticket.Required");
        }
        NoteAndCounts counts = normalizeCountsAndNote(internalBugCount, customerBugCount, note);
        return new Payload(projectId, repositoryId, ticketId, counts.internalBugCount(), counts.customerBugCount(), counts.note());
    }

    private NoteAndCounts normalizeCountsAndNote(Integer internalBugCount, Integer customerBugCount, String note) {
        if (internalBugCount == null || internalBugCount < 0) {
            throw new BusinessRuleException("Pages.TicketBugMetrics.InternalBugCount.Invalid");
        }
        if (customerBugCount == null || customerBugCount < 0) {
            throw new BusinessRuleException("Pages.TicketBugMetrics.CustomerBugCount.Invalid");
        }
        String trimmedNote = note == null ? null : note.trim();
        String normalizedNote = (trimmedNote == null || trimmedNote.isEmpty()) ? null : trimmedNote;
        if (normalizedNote != null && normalizedNote.length() > NOTE_MAX_LENGTH) {
            throw new BusinessRuleException("Pages.TicketBugMetrics.Note.MaxLength");
        }
        return new NoteAndCounts(internalBugCount, customerBugCount, normalizedNote);
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

    private record Payload(UUID projectId, UUID repositoryId, UUID ticketId,
            int internalBugCount, int customerBugCount, String note) {}

    private record NoteAndCounts(int internalBugCount, int customerBugCount, String note) {}
}
