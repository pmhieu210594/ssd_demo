package com.sdd.platform.application.usecase.ticketbugmetrics;

import com.sdd.platform.application.exception.ConflictException;
import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.ProjectRepositoryPort;
import com.sdd.platform.application.port.out.persistence.QaDashboardRepositoryPort;
import com.sdd.platform.application.port.out.persistence.RepositoryRepositoryPort;
import com.sdd.platform.application.port.out.persistence.TicketBugMetricsRepositoryPort;
import com.sdd.platform.application.port.out.persistence.TicketLookupPort;
import com.sdd.platform.application.usecase.governance.AdminAuditLogService;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AuthUserContext;
import com.sdd.platform.domain.model.Project;
import com.sdd.platform.domain.model.RepositoryModel;
import com.sdd.platform.domain.model.TicketBugMetricsModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketBugMetricsServiceTest {

    private static final UUID PROJECT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID REPOSITORY_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID TICKET_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID TICKET_BUG_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");

    @Mock private TicketBugMetricsRepositoryPort repository;
    @Mock private ProjectRepositoryPort projectRepository;
    @Mock private RepositoryRepositoryPort repositoryRepository;
    @Mock private TicketLookupPort ticketLookup;
    @Mock private QaDashboardRepositoryPort qaDashboardRepository;
    @Mock private AdminAuditLogService adminAuditLogService;

    private TicketBugMetricsService service;

    @BeforeEach
    void setUp() {
        service = new TicketBugMetricsService(repository, projectRepository, repositoryRepository,
                ticketLookup, qaDashboardRepository, adminAuditLogService);
        lenient().when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(activeProject()));
        lenient().when(repositoryRepository.findById(REPOSITORY_ID)).thenReturn(Optional.of(activeRepository()));
        lenient().when(ticketLookup.existsTicketInProject(TICKET_ID, PROJECT_ID)).thenReturn(true);
    }

    // ── Role matrix ────────────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({"PM", "QA", "ADMIN"})
    void mutateRoles_canCreateUpdateDelete(String role) {
        AuthUserContext caller = callerWithRole(role, PROJECT_ID);
        when(repository.existsActiveByTicketId(TICKET_ID)).thenReturn(false);
        when(repository.insert(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findById(any())).thenReturn(Optional.of(activeMetric()));

        assertThat(service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, 1, 0, "note", caller)).isNotNull();

        when(repository.update(any())).thenReturn(1);
        assertThat(service.update(TICKET_BUG_ID, 2, 1, "note2", caller)).isNotNull();

        when(repository.softDelete(any(), any(), any(), any(), any())).thenReturn(1);
        assertThat(service.softDelete(TICKET_BUG_ID, caller)).isNotNull();
    }

    @Test
    void devRole_canViewButNotMutate() {
        AuthUserContext dev = callerWithRole("DEV", PROJECT_ID);
        when(repository.findPage(PROJECT_ID, null, null, 0, 20)).thenReturn(java.util.List.of());
        when(repository.count(PROJECT_ID, null, null)).thenReturn(0L);

        assertThat(service.search(PROJECT_ID, null, null, 0, 20, dev).items()).isEmpty();

        when(repository.findById(TICKET_BUG_ID)).thenReturn(Optional.of(activeMetric()));
        assertThat(service.get(TICKET_BUG_ID, dev)).isNotNull();

        assertThatThrownBy(() -> service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, 1, 0, null, dev))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.update(TICKET_BUG_ID, 1, 0, null, dev))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.softDelete(TICKET_BUG_ID, dev))
                .isInstanceOf(ForbiddenException.class);
    }

    @ParameterizedTest
    @CsvSource({"VIEWER", "SECURITY", "DATA_OPS"})
    void otherRoles_blockedFromEveryAction(String role) {
        AuthUserContext caller = callerWithRole(role, PROJECT_ID);

        assertThatThrownBy(() -> service.search(PROJECT_ID, null, role, 0, 20, caller))
                .isInstanceOf(ForbiddenException.class);
        // options() only requires an authenticated caller (see
        // TicketBugMetricsService#options) — it is not role-gated like
        // search/create, so it is intentionally not asserted here.
        assertThatThrownBy(() -> service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, 1, 0, null, caller))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void unauthenticatedCaller_blockedFromEveryAction() {
        assertThatThrownBy(() -> service.search(PROJECT_ID, null, null, 0, 20, null))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.options(PROJECT_ID, null, null))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, 1, 0, null, null))
                .isInstanceOf(ForbiddenException.class);
    }

    // ── Validation ─────────────────────────────────────────────────────────

    @Test
    void create_rejectsNegativeOrMissingBugCounts() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);

        assertThatThrownBy(() -> service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, -1, 0, null, admin))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, null, 0, null, admin))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, 0, -5, null, admin))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void create_acceptsZeroBugCounts() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        when(repository.existsActiveByTicketId(TICKET_ID)).thenReturn(false);
        when(repository.insert(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findById(any())).thenReturn(Optional.of(activeMetric()));

        assertThat(service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, 0, 0, null, admin)).isNotNull();
    }

    @Test
    void create_rejectsNoteOver500Characters() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        String longNote = "a".repeat(501);

        assertThatThrownBy(() -> service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, 1, 0, longNote, admin))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void create_accepts500CharacterNote() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        when(repository.existsActiveByTicketId(TICKET_ID)).thenReturn(false);
        when(repository.insert(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findById(any())).thenReturn(Optional.of(activeMetric()));

        assertThat(service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, 1, 0, "a".repeat(500), admin)).isNotNull();
    }

    @Test
    void create_duplicateActiveTicket_throwsConflict() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        when(repository.existsActiveByTicketId(TICKET_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, 1, 0, null, admin))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_duplicateKeyOnInsert_throwsConflict() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        when(repository.existsActiveByTicketId(TICKET_ID)).thenReturn(false);
        when(repository.insert(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, 1, 0, null, admin))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Pages.TicketBugMetrics.Ticket.AlreadyExists");
    }

    @Test
    void create_repositoryUnrelatedToTicket_stillAccepted_perBR12() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        when(repository.existsActiveByTicketId(TICKET_ID)).thenReturn(false);
        when(repository.insert(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findById(any())).thenReturn(Optional.of(activeMetric()));
        // repositoryId belongs to the project but has no relation asserted to ticketId — no cross-check performed.
        assertThat(service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, 1, 0, null, admin)).isNotNull();
    }

    @Test
    void create_projectOrRepositoryOrTicketNotFound_throwsNotFound() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, 1, 0, null, admin))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getOrUpdateOrDelete_notFoundOrDeletedRow_throwsNotFound() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        when(repository.findById(TICKET_BUG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(TICKET_BUG_ID, admin)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.update(TICKET_BUG_ID, 1, 0, null, admin)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.softDelete(TICKET_BUG_ID, admin)).isInstanceOf(NotFoundException.class);

        TicketBugMetricsModel deleted = activeMetric();
        deleted.setDeleteFlag(true);
        deleted.setStatus(TicketBugMetricsModel.Status.DELETED);
        when(repository.findById(TICKET_BUG_ID)).thenReturn(Optional.of(deleted));
        assertThatThrownBy(() -> service.get(TICKET_BUG_ID, admin)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_repositoryNotFoundOrInactive_throwsNotFound() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        when(repositoryRepository.findById(REPOSITORY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, 1, 0, null, admin))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_repositoryBelongsToDifferentProject_throwsNotFound() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        RepositoryModel otherProjectRepo = RepositoryModel.builder()
                .repositoryId(REPOSITORY_ID)
                .projectId(UUID.fromString("20000000-0000-0000-0000-000000000099"))
                .status(RepositoryModel.RepositoryStatus.ACTIVE)
                .deleteFlag(false)
                .build();
        when(repositoryRepository.findById(REPOSITORY_ID)).thenReturn(Optional.of(otherProjectRepo));

        assertThatThrownBy(() -> service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, 1, 0, null, admin))
                .isInstanceOf(NotFoundException.class);
    }

    // ── Pagination ────────────────────────────────────────────────────────

    @Test
    void search_clampsPageSizeToBounds() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        when(repository.findPage(eq(PROJECT_ID), any(), isNull(), any(Integer.class), any(Integer.class))).thenReturn(java.util.List.of());
        when(repository.count(eq(PROJECT_ID), any(), isNull())).thenReturn(0L);

        // size <= 0 falls back to the default page size (20), not 0/negative.
        assertThat(service.search(PROJECT_ID, null, null, 0, 0, admin).size()).isEqualTo(20);
        // size above the max is clamped down to the max (100).
        assertThat(service.search(PROJECT_ID, null, null, 0, 500, admin).size()).isEqualTo(100);
        // negative page is floored at 0.
        assertThat(service.search(PROJECT_ID, null, null, -5, 20, admin).page()).isEqualTo(0);
    }

    // ── Fixtures ───────────────────────────────────────────────────────────

    private AuthUserContext callerWithRole(String role, UUID projectId) {
        AuthUserContext caller = AuthUserContext.builder()
                .userAccountId(UUID.randomUUID())
                .username("user")
                .email("user@example.com")
                .role(role)
                .build();
        if (!"ADMIN".equalsIgnoreCase(role)) {
            lenient().when(qaDashboardRepository.findProjectRole(eq(caller), eq(projectId))).thenReturn(role);
        }
        return caller;
    }

    private Project activeProject() {
        return Project.builder().projectId(PROJECT_ID).status(Project.ProjectStatus.ACTIVE).deleteFlag(false).build();
    }

    private RepositoryModel activeRepository() {
        return RepositoryModel.builder()
                .repositoryId(REPOSITORY_ID)
                .projectId(PROJECT_ID)
                .status(RepositoryModel.RepositoryStatus.ACTIVE)
                .deleteFlag(false)
                .build();
    }

    private TicketBugMetricsModel activeMetric() {
        return TicketBugMetricsModel.builder()
                .ticketBugId(TICKET_BUG_ID)
                .projectId(PROJECT_ID)
                .repositoryId(REPOSITORY_ID)
                .ticketId(TICKET_ID)
                .internalBugCount(1)
                .customerBugCount(0)
                .status(TicketBugMetricsModel.Status.ACTIVE)
                .deleteFlag(false)
                .build();
    }
}
