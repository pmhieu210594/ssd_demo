package com.sdd.platform.application.usecase.aiquality;

import com.sdd.platform.application.exception.ConflictException;
import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.ProjectRepositoryPort;
import com.sdd.platform.application.port.out.persistence.QaDashboardRepositoryPort;
import com.sdd.platform.application.port.out.persistence.RepositoryRepositoryPort;
import com.sdd.platform.application.port.out.persistence.AiQualityRepositoryPort;
import com.sdd.platform.application.port.out.persistence.TicketLookupPort;
import com.sdd.platform.application.usecase.governance.AdminAuditLogService;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AiQualityModel;
import com.sdd.platform.domain.model.AuthUserContext;
import com.sdd.platform.domain.model.Project;
import com.sdd.platform.domain.model.RepositoryModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiQualityServiceTest {

    private static final UUID PROJECT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID REPOSITORY_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID TICKET_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID AI_QUALITY_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");

    @Mock private AiQualityRepositoryPort repository;
    @Mock private ProjectRepositoryPort projectRepository;
    @Mock private RepositoryRepositoryPort repositoryRepository;
    @Mock private TicketLookupPort ticketLookup;
    @Mock private QaDashboardRepositoryPort qaDashboardRepository;
    @Mock private AdminAuditLogService adminAuditLogService;

    private AiQualityService service;

    @BeforeEach
    void setUp() {
        service = new AiQualityService(repository, projectRepository, repositoryRepository,
                ticketLookup, qaDashboardRepository, adminAuditLogService);
        lenient().when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(activeProject()));
        lenient().when(repositoryRepository.findById(REPOSITORY_ID)).thenReturn(Optional.of(activeRepository()));
        lenient().when(ticketLookup.existsTicketInRepository(TICKET_ID, REPOSITORY_ID)).thenReturn(true);
    }

    // ── RBAC matrix (AC-AI-QUALITY-10, -11, -12) ─────────────────────────────

    @ParameterizedTest
    @CsvSource({"PM", "QA", "ADMIN"})
    void mutateRoles_canCreateUpdateDelete(String role) {
        AuthUserContext caller = callerWithRole(role, PROJECT_ID);
        when(repository.existsActiveByTicketId(TICKET_ID)).thenReturn(false);
        when(repository.insert(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findById(any())).thenReturn(Optional.of(activeRow()));

        assertThat(service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, new BigDecimal("85.50"), caller)).isNotNull();

        when(repository.update(any())).thenReturn(1);
        assertThat(service.update(AI_QUALITY_ID, new BigDecimal("90.00"), caller)).isNotNull();

        when(repository.softDelete(any(), any(), any(), any(), any())).thenReturn(1);
        assertThat(service.softDelete(AI_QUALITY_ID, caller)).isNotNull();
    }

    @Test
    void devRole_isViewOnly_forbiddenOnWrite_allowedOnRead() {
        AuthUserContext dev = callerWithRole("DEV", PROJECT_ID);
        when(repository.findPage(PROJECT_ID, null, null, null, 0, 20)).thenReturn(java.util.List.of());
        when(repository.count(PROJECT_ID, null, null, null)).thenReturn(0L);

        assertThat(service.search(PROJECT_ID, null, null, null, 0, 20, dev).items()).isEmpty();

        when(repository.findById(AI_QUALITY_ID)).thenReturn(Optional.of(activeRow()));
        assertThat(service.get(AI_QUALITY_ID, dev)).isNotNull();

        assertThatThrownBy(() -> service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, new BigDecimal("50.00"), dev))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.update(AI_QUALITY_ID, new BigDecimal("50.00"), dev))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.softDelete(AI_QUALITY_ID, dev))
                .isInstanceOf(ForbiddenException.class);
    }

    @ParameterizedTest
    @CsvSource({"VIEWER", "SECURITY", "DATA_OPS"})
    void noRole_blockedFromEveryEndpointIncludingRead(String role) {
        AuthUserContext caller = callerWithRole(role, PROJECT_ID);

        assertThatThrownBy(() -> service.search(PROJECT_ID, null, null, role, 0, 20, caller))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, new BigDecimal("50.00"), caller))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void unauthenticatedCaller_blockedFromEveryEndpoint() {
        assertThatThrownBy(() -> service.search(PROJECT_ID, null, null, null, 0, 20, null))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, new BigDecimal("50.00"), null))
                .isInstanceOf(ForbiddenException.class);
    }

    // ── requireAnyAccess: FE guard fallback when no projectId is known yet ──

    @Test
    void requireAnyAccess_passesForAnyAuthenticatedCaller_regardlessOfProjectRole() {
        AuthUserContext dev = callerWithRole("DEV", PROJECT_ID);
        AuthUserContext noRole = AuthUserContext.builder().userAccountId(UUID.randomUUID()).role("VIEWER").build();

        service.requireAnyAccess(dev);
        service.requireAnyAccess(noRole);
        // no exception thrown for either — this is the guard used when the FE
        // navigates to the screen before a project has been selected/synced
        // into the URL (e.g. via RoleTabs), so project-role resolution isn't
        // possible yet.
    }

    @Test
    void requireAnyAccess_throwsForUnauthenticatedCaller() {
        assertThatThrownBy(() -> service.requireAnyAccess(null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void globalAdmin_getsMutateRegardlessOfPerProjectRole() {
        AuthUserContext admin = AuthUserContext.builder().userAccountId(UUID.randomUUID()).role("ADMIN").build();
        when(repository.existsActiveByTicketId(TICKET_ID)).thenReturn(false);
        when(repository.insert(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findById(any())).thenReturn(Optional.of(activeRow()));

        assertThat(service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, new BigDecimal("77.00"), admin)).isNotNull();
    }

    // ── BR-6 boundary values ─────────────────────────────────────────────────

    @Test
    void create_acceptsUpperInclusiveBound_100_00() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        when(repository.existsActiveByTicketId(TICKET_ID)).thenReturn(false);
        when(repository.insert(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findById(any())).thenReturn(Optional.of(activeRow()));

        assertThat(service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, new BigDecimal("100.00"), admin)).isNotNull();
    }

    @Test
    void create_acceptsLowerInclusiveBound_0_00() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        when(repository.existsActiveByTicketId(TICKET_ID)).thenReturn(false);
        when(repository.insert(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findById(any())).thenReturn(Optional.of(activeRow()));

        assertThat(service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, new BigDecimal("0.00"), admin)).isNotNull();
    }

    @Test
    void create_rejectsAboveUpperBound_100_01() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        assertThatThrownBy(() -> service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, new BigDecimal("100.01"), admin))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void create_rejectsBelowLowerBound_negative0_01() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        assertThatThrownBy(() -> service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, new BigDecimal("-0.01"), admin))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void create_rejectsNullRate() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        assertThatThrownBy(() -> service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, null, admin))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void create_rejectsScaleGreaterThanTwo_50_555() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        assertThatThrownBy(() -> service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, new BigDecimal("50.555"), admin))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ── BR-2 duplicate-active conflict ───────────────────────────────────────

    @Test
    void create_duplicateActiveTicket_throwsConflict() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        when(repository.existsActiveByTicketId(TICKET_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, new BigDecimal("50.00"), admin))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_duplicateKeyOnInsert_throwsConflict() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        when(repository.existsActiveByTicketId(TICKET_ID)).thenReturn(false);
        when(repository.insert(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, new BigDecimal("50.00"), admin))
                .isInstanceOf(ConflictException.class);
    }

    // ── BR-3 parentage validation ────────────────────────────────────────────

    @Test
    void create_repositoryNotBelongingToProject_throwsBusinessRuleException() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        RepositoryModel otherProjectRepo = RepositoryModel.builder()
                .repositoryId(REPOSITORY_ID)
                .projectId(UUID.fromString("20000000-0000-0000-0000-000000000099"))
                .status(RepositoryModel.RepositoryStatus.ACTIVE)
                .deleteFlag(false)
                .build();
        when(repositoryRepository.findById(REPOSITORY_ID)).thenReturn(Optional.of(otherProjectRepo));

        assertThatThrownBy(() -> service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, new BigDecimal("50.00"), admin))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void create_ticketNotBelongingToRepository_throwsBusinessRuleException() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        when(ticketLookup.existsTicketInRepository(TICKET_ID, REPOSITORY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, new BigDecimal("50.00"), admin))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ── Soft-delete lifecycle (BR-4, AC-AI-QUALITY-7/8) ──────────────────────

    @Test
    void getOrUpdateOrDelete_notFoundOrDeletedRow_throwsNotFound() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        when(repository.findById(AI_QUALITY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(AI_QUALITY_ID, admin)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.update(AI_QUALITY_ID, new BigDecimal("50.00"), admin)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.softDelete(AI_QUALITY_ID, admin)).isInstanceOf(NotFoundException.class);

        AiQualityModel deleted = activeRow();
        deleted.setDeleteFlag(true);
        deleted.setStatus(AiQualityModel.Status.DELETED);
        when(repository.findById(AI_QUALITY_ID)).thenReturn(Optional.of(deleted));
        assertThatThrownBy(() -> service.get(AI_QUALITY_ID, admin)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void afterSoftDelete_recreatingSameTicket_succeeds_noFalseConflict() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        // simulates the state after a prior row was soft-deleted: no active row remains.
        when(repository.existsActiveByTicketId(TICKET_ID)).thenReturn(false);
        when(repository.insert(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findById(any())).thenReturn(Optional.of(activeRow()));

        assertThat(service.create(PROJECT_ID, REPOSITORY_ID, TICKET_ID, new BigDecimal("60.00"), admin)).isNotNull();
    }

    // ── Pagination ────────────────────────────────────────────────────────

    @Test
    void search_clampsPageSizeToBounds() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        when(repository.findPage(eq(PROJECT_ID), any(), any(), eq((String) null), any(Integer.class), any(Integer.class))).thenReturn(java.util.List.of());
        when(repository.count(eq(PROJECT_ID), any(), any(), eq((String) null))).thenReturn(0L);

        assertThat(service.search(PROJECT_ID, null, null, null, 0, 0, admin).size()).isEqualTo(20);
        assertThat(service.search(PROJECT_ID, null, null, null, 0, 500, admin).size()).isEqualTo(100);
        assertThat(service.search(PROJECT_ID, null, null, null, -5, 20, admin).page()).isEqualTo(0);
    }

    // ── search filter (AC-AI-QUALITY-13) ─────────────────────────────────────

    @Test
    void search_filtersByTicketExternalKeyOrTitle_andCombinedWithOtherFilters() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        when(repository.findPage(PROJECT_ID, REPOSITORY_ID, TICKET_ID, "AI-123", 0, 20))
                .thenReturn(java.util.List.of(activeRow()));
        when(repository.count(PROJECT_ID, REPOSITORY_ID, TICKET_ID, "AI-123")).thenReturn(1L);

        var result = service.search(PROJECT_ID, REPOSITORY_ID, TICKET_ID, "AI-123", 0, 20, admin);

        assertThat(result.items()).hasSize(1);
        org.mockito.Mockito.verify(repository).findPage(PROJECT_ID, REPOSITORY_ID, TICKET_ID, "AI-123", 0, 20);
        org.mockito.Mockito.verify(repository).count(PROJECT_ID, REPOSITORY_ID, TICKET_ID, "AI-123");
    }

    @Test
    void search_blankSearchTermIsNormalizedToNull() {
        AuthUserContext admin = callerWithRole("ADMIN", PROJECT_ID);
        when(repository.findPage(PROJECT_ID, null, null, null, 0, 20)).thenReturn(java.util.List.of());
        when(repository.count(PROJECT_ID, null, null, null)).thenReturn(0L);

        service.search(PROJECT_ID, null, null, "   ", 0, 20, admin);

        org.mockito.Mockito.verify(repository).findPage(PROJECT_ID, null, null, null, 0, 20);
        org.mockito.Mockito.verify(repository).count(PROJECT_ID, null, null, null);
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

    private AiQualityModel activeRow() {
        return AiQualityModel.builder()
                .ticketAiQualityId(AI_QUALITY_ID)
                .projectId(PROJECT_ID)
                .repositoryId(REPOSITORY_ID)
                .ticketId(TICKET_ID)
                .aiQualityRate(new BigDecimal("85.50"))
                .status(AiQualityModel.Status.ACTIVE)
                .deleteFlag(false)
                .build();
    }
}
