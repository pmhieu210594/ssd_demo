package com.sdd.platform.application.usecase.pmdashboard;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.PmDashboardRepositoryPort;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.application.usecase.quality.ScoreThresholdConfigService;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AuthUserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PmDashboardServiceTest {

    private static final UUID PROJECT_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID TICKET_ID = UUID.fromString("90000000-0000-0000-0000-000000000002");

    @Mock
    private PmDashboardRepositoryPort repository;

    @Mock
    private ScoreThresholdConfigService scoreThresholdConfigService;

    private PmDashboardService service;

    @BeforeEach
    void setUp() {
        lenient().when(scoreThresholdConfigService.getActiveCodes())
                .thenReturn(Set.of("EXCELLENT", "GOOD", "WARNING", "RISKY", "CRITICAL"));
        service = new PmDashboardService(repository, scoreThresholdConfigService);
    }

    @Test
    void summary_requiresPmRole() {
        AuthUserContext viewer = AuthUserContext.builder().role("VIEWER").build();

        assertThatThrownBy(() -> service.summary(null, null, null, null, null, null, null, viewer))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Component.Permission.Denied");
    }

    @Test
    void summary_allowsProjectRolePmEvenWhenSystemRoleIsDifferent() {
        AuthUserContext viewer = AuthUserContext.builder().role("VIEWER").build();
        when(repository.findProjectRole(viewer, PROJECT_ID)).thenReturn("PM");
        when(repository.findSummary(any())).thenReturn(new PmDashboardModels.DashboardSummary(
                1, 2, 3, 4, 5, 6, 7, 8,
                new BigDecimal("78.25"), "PLAN", "Plan", 2L,
                OffsetDateTime.parse("2026-06-25T00:00:00Z")
        ));

        PmDashboardModels.DashboardSummary result = service.summary(
                PROJECT_ID, null, null, null, null, null, null, viewer
        );

        verify(repository).findProjectRole(viewer, PROJECT_ID);
    }

    @Test
    void insights_requiresPmRole() {
        AuthUserContext viewer = AuthUserContext.builder().role("VIEWER").build();

        assertThatThrownBy(() -> service.insights(null, null, null, null, null, null, null, viewer))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Component.Permission.Denied");
    }

    @Test
    void summary_normalizesFiltersAndDelegatesToRepository() {
        AuthUserContext pm = AuthUserContext.builder().role("PM").build();
        when(repository.findProjectRole(pm, PROJECT_ID)).thenReturn("PM");
        when(repository.findSummary(any())).thenReturn(new PmDashboardModels.DashboardSummary(
                1, 2, 3, 4, 5, 6, 7, 8,
                new BigDecimal("78.25"), "PLAN", "Plan", 2L,
                OffsetDateTime.parse("2026-06-25T00:00:00Z")
        ));

        PmDashboardModels.DashboardSummary result = service.summary(
                PROJECT_ID, " 2026-06 ", UUID.randomUUID(), "  plan  ", " good ", " high ", "  ticket  ", pm
        );

        ArgumentCaptor<PmDashboardModels.DashboardFilter> captor =
                ArgumentCaptor.forClass(PmDashboardModels.DashboardFilter.class);
        verify(repository).findSummary(captor.capture());
        assertThat(captor.getValue().riskLevel()).isEqualTo("HIGH");
        assertThat(captor.getValue().search()).isEqualTo("ticket");
        assertThat(result.missingTraceabilitySectionTicketCount()).isEqualTo(3);
    }

    @Test
    void insights_normalizesFiltersAndDelegatesToRepository() {
        AuthUserContext pm = AuthUserContext.builder().role("PM").build();
        when(repository.findProjectRole(pm, PROJECT_ID)).thenReturn("PM");
        when(repository.findInsights(any())).thenReturn(new PmDashboardModels.DashboardInsights(List.of(), List.of()));

        service.insights(
                PROJECT_ID, " 2026-06 ", UUID.randomUUID(), "  plan  ", " good ", " high ", "  ticket  ", pm
        );

        ArgumentCaptor<PmDashboardModels.DashboardFilter> captor =
                ArgumentCaptor.forClass(PmDashboardModels.DashboardFilter.class);
        verify(repository).findInsights(captor.capture());
        assertThat(captor.getValue().riskLevel()).isEqualTo("HIGH");
        assertThat(captor.getValue().search()).isEqualTo("ticket");
    }

    @Test
    void detail_returnsNotFoundWhenSnapshotMissing() {
        AuthUserContext pm = AuthUserContext.builder().role("PM").build();
        when(repository.findDetail(TICKET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(TICKET_ID, pm))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Pages.PmDashboard.NotFound");
    }

    @Test
    void detail_allowsProjectRolePmEvenWhenSystemRoleIsDifferent() {
        AuthUserContext viewer = AuthUserContext.builder().role("VIEWER").build();
        when(repository.findDetail(TICKET_ID)).thenReturn(Optional.of(
                new PmDashboardModels.DashboardTicketDetail(
                        new PmDashboardModels.DashboardTicketRow(
                                TICKET_ID,
                                PROJECT_ID,
                                "Project Alpha",
                                UUID.randomUUID(),
                                "Repo Alpha",
                                "PM-1",
                                "Title",
                                "OPEN",
                                UUID.randomUUID(),
                                "PLAN",
                                "Plan",
                                "Plan",
                                OffsetDateTime.parse("2026-06-20T00:00:00Z"),
                                1,
                                false,
                                false,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                null,
                                new BigDecimal("88"),
                                "v1",
                                1,
                                "Owner",
                                "2026-06",
                                OffsetDateTime.parse("2026-06-20T00:00:00Z"),
                                OffsetDateTime.parse("2026-06-20T00:00:00Z"),
                                OffsetDateTime.parse("2026-06-20T00:00:00Z"),
                                null,
                                null,
                                null,
                                null
                        ),
                        OffsetDateTime.parse("2026-06-20T00:00:00Z"),
                        "Owner",
                        0,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        new PmDashboardModels.ScoreBreakdown(
                                new BigDecimal("10"),
                                new BigDecimal("10"),
                                new BigDecimal("10"),
                                new BigDecimal("10"),
                                new BigDecimal("10"),
                                new BigDecimal("10"),
                                new BigDecimal("10"),
                                new BigDecimal("10")
                        ),
                        "/traceability/ticket-1",
                        List.of()
                )));
        when(repository.findProjectRole(viewer, PROJECT_ID)).thenReturn("PM");

        PmDashboardModels.DashboardTicketDetail result = service.detail(TICKET_ID, viewer);

        verify(repository).findProjectRole(viewer, PROJECT_ID);
        assertThat(result.row().projectId()).isEqualTo(PROJECT_ID);
    }

    @Test
    void exportDelegatesWithMaxPageSizeForCsv() {
        AuthUserContext pm = AuthUserContext.builder().role("PM").build();
        when(repository.findProjectRole(pm, PROJECT_ID)).thenReturn("PM");
        when(repository.findAllTickets(any())).thenReturn(List.of());

        byte[] csv = service.exportCsv(
                PROJECT_ID, " 2026-06 ", null, " plan ", " good ", " high ", " ticket ", pm
        );

        ArgumentCaptor<PmDashboardModels.DashboardFilter> captor =
                ArgumentCaptor.forClass(PmDashboardModels.DashboardFilter.class);
        verify(repository).findAllTickets(captor.capture());
        assertThat(captor.getValue().page()).isEqualTo(1);
        assertThat(captor.getValue().size()).isEqualTo(100);
        assertThat(new String(csv)).contains("ticket_id,external_ticket_key");
    }

    @Test
    void tickets_requiresPmRole() {
        AuthUserContext viewer = AuthUserContext.builder().role("VIEWER").build();

        assertThatThrownBy(() -> service.tickets(null, null, null, null, null, null, null, 1, 20, viewer))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Component.Permission.Denied");
    }

    @Test
    void detail_requiresPmRole() {
        AuthUserContext viewer = AuthUserContext.builder().role("VIEWER").build();
        when(repository.findDetail(TICKET_ID)).thenReturn(Optional.of(
                new PmDashboardModels.DashboardTicketDetail(
                        new PmDashboardModels.DashboardTicketRow(
                                TICKET_ID,
                                PROJECT_ID,
                                "Project Alpha",
                                UUID.randomUUID(),
                                "Repo Alpha",
                                "PM-1",
                                "Title",
                                "OPEN",
                                UUID.randomUUID(),
                                "PLAN",
                                "Plan",
                                "Plan",
                                OffsetDateTime.parse("2026-06-20T00:00:00Z"),
                                1,
                                false,
                                false,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                null,
                                new BigDecimal("88"),
                                "v1",
                                1,
                                "Owner",
                                "2026-06",
                                OffsetDateTime.parse("2026-06-20T00:00:00Z"),
                                OffsetDateTime.parse("2026-06-20T00:00:00Z"),
                                OffsetDateTime.parse("2026-06-20T00:00:00Z"),
                                null,
                                null,
                                null,
                                null
                        ),
                        OffsetDateTime.parse("2026-06-20T00:00:00Z"),
                        "Owner",
                        0,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        new PmDashboardModels.ScoreBreakdown(
                                new BigDecimal("10"),
                                new BigDecimal("10"),
                                new BigDecimal("10"),
                                new BigDecimal("10"),
                                new BigDecimal("10"),
                                new BigDecimal("10"),
                                new BigDecimal("10"),
                                new BigDecimal("10")
                        ),
                        "/traceability/ticket-1",
                        List.of()
                )));
        when(repository.findProjectRole(viewer, PROJECT_ID)).thenReturn("DEV");

        assertThatThrownBy(() -> service.detail(TICKET_ID, viewer))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Component.Permission.Denied");
    }

    @Test
    void refresh_requiresPmRole() {
        AuthUserContext viewer = AuthUserContext.builder().role("VIEWER").build();

        assertThatThrownBy(() -> service.refresh(viewer))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Component.Permission.Denied");
    }

    @Test
    void templateUsage_requiresPmRole() {
        AuthUserContext viewer = AuthUserContext.builder().role("VIEWER").build();

        assertThatThrownBy(() -> service.getTemplateUsage(viewer, null, null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Component.Permission.Denied");
    }

    @Test
    void templateUsage_allowsAdminRoleWithoutProjectRoleLookup() {
        AuthUserContext admin = AuthUserContext.builder().role("ADMIN").build();
        UUID repositoryId = UUID.randomUUID();
        when(repository.findTemplateUsage(PROJECT_ID, repositoryId)).thenReturn(List.of(
                new PmDashboardModels.TemplateUsageRow("PLAN", "Plan", 2, 1)));

        List<PmDashboardModels.TemplateUsageRow> result =
                service.getTemplateUsage(admin, PROJECT_ID, repositoryId);

        assertThat(result).hasSize(1);
        verify(repository).findTemplateUsage(PROJECT_ID, repositoryId);
    }

    @Test
    void templateUsage_allowsProjectRolePmEvenWhenSystemRoleIsDifferent() {
        AuthUserContext viewer = AuthUserContext.builder().role("VIEWER").build();
        UUID repositoryId = UUID.randomUUID();
        when(repository.findProjectRole(viewer, PROJECT_ID)).thenReturn("PM");
        when(repository.findTemplateUsage(PROJECT_ID, repositoryId)).thenReturn(List.of());

        service.getTemplateUsage(viewer, PROJECT_ID, repositoryId);

        verify(repository).findProjectRole(viewer, PROJECT_ID);
        verify(repository).findTemplateUsage(PROJECT_ID, repositoryId);
    }

    @Test
    void aiFindingStats_requiresPmRole() {
        AuthUserContext viewer = AuthUserContext.builder().role("VIEWER").build();

        assertThatThrownBy(() -> service.getAiFindingStats(viewer, null, null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Component.Permission.Denied");
    }

    @Test
    void aiFindingStats_allowsAdminRoleAndReturnsRowFromRepository() {
        AuthUserContext admin = AuthUserContext.builder().role("ADMIN").build();
        UUID repositoryId = UUID.randomUUID();
        PmDashboardModels.AiFindingStatsRow row = new PmDashboardModels.AiFindingStatsRow(
                repositoryId, "widget", 1, 1, 2, 2, 2, 0, 2);
        when(repository.findAiFindingStats(PROJECT_ID, repositoryId)).thenReturn(Optional.of(row));

        PmDashboardModels.AiFindingStatsRow result = service.getAiFindingStats(admin, PROJECT_ID, repositoryId);

        assertThat(result).isEqualTo(row);
        verify(repository).findAiFindingStats(PROJECT_ID, repositoryId);
    }

    @Test
    void aiFindingStats_returnsZeroedRowWhenRepositoryNotFound() {
        AuthUserContext admin = AuthUserContext.builder().role("ADMIN").build();
        UUID repositoryId = UUID.randomUUID();
        when(repository.findAiFindingStats(PROJECT_ID, repositoryId)).thenReturn(Optional.empty());

        PmDashboardModels.AiFindingStatsRow result = service.getAiFindingStats(admin, PROJECT_ID, repositoryId);

        assertThat(result).isEqualTo(new PmDashboardModels.AiFindingStatsRow(
                repositoryId, null, 0, 0, 0, 0, 0, 0, 0));
    }

    @Test
    void aiFindingStats_allowsProjectRolePmEvenWhenSystemRoleIsDifferent() {
        AuthUserContext viewer = AuthUserContext.builder().role("VIEWER").build();
        UUID repositoryId = UUID.randomUUID();
        when(repository.findProjectRole(viewer, PROJECT_ID)).thenReturn("PM");
        when(repository.findAiFindingStats(PROJECT_ID, repositoryId)).thenReturn(Optional.empty());

        service.getAiFindingStats(viewer, PROJECT_ID, repositoryId);

        verify(repository).findProjectRole(viewer, PROJECT_ID);
        verify(repository).findAiFindingStats(PROJECT_ID, repositoryId);
    }

    @Test
    void tickets_normalizesFiltersAndDelegatesToRepository() {
        AuthUserContext pm = AuthUserContext.builder().role("PM").build();
        when(repository.findProjectRole(pm, PROJECT_ID)).thenReturn("PM");
        when(repository.findTickets(any())).thenReturn(new PageResult<>(List.of(), 1, 20, 0, 0));

        service.tickets(PROJECT_ID, " 2026-06 ", null, "  plan  ", " warning ", " medium ", "  abc  ", 1, 20, pm);

        ArgumentCaptor<PmDashboardModels.DashboardFilter> captor =
                ArgumentCaptor.forClass(PmDashboardModels.DashboardFilter.class);
        verify(repository).findTickets(captor.capture());
        assertThat(captor.getValue().riskLevel()).isEqualTo("MEDIUM");
        assertThat(captor.getValue().search()).isEqualTo("abc");
        assertThat(captor.getValue().phaseCode()).isEqualTo("plan");
    }
}
