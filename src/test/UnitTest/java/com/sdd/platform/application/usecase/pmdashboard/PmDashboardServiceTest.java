package com.sdd.platform.application.usecase.pmdashboard;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.PmDashboardRepositoryPort;
import com.sdd.platform.application.usecase.common.PageResult;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PmDashboardServiceTest {

    private static final UUID PROJECT_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID TICKET_ID = UUID.fromString("90000000-0000-0000-0000-000000000002");

    @Mock
    private PmDashboardRepositoryPort repository;

    private PmDashboardService service;

    @BeforeEach
    void setUp() {
        service = new PmDashboardService(repository);
    }

    @Test
    void summary_requiresPmRole() {
        AuthUserContext viewer = AuthUserContext.builder().role("VIEWER").build();

        assertThatThrownBy(() -> service.summary(null, null, null, null, null, null, null, viewer))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Component.Permission.Denied");
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
        when(repository.findSummary(any())).thenReturn(new PmDashboardModels.DashboardSummary(
                1, 2, 3, 4, 5, 6, 7, 8,
                new BigDecimal("78.25"), "GOOD", "PLAN", "Plan", 2L,
                OffsetDateTime.parse("2026-06-25T00:00:00Z")
        ));

        PmDashboardModels.DashboardSummary result = service.summary(
                PROJECT_ID, " 2026-06 ", UUID.randomUUID(), "  plan  ", " good ", " high ", "  ticket  ", pm
        );

        ArgumentCaptor<PmDashboardModels.DashboardFilter> captor =
                ArgumentCaptor.forClass(PmDashboardModels.DashboardFilter.class);
        verify(repository).findSummary(captor.capture());
        assertThat(captor.getValue().scoreBand()).isEqualTo("GOOD");
        assertThat(captor.getValue().riskLevel()).isEqualTo("HIGH");
        assertThat(captor.getValue().search()).isEqualTo("ticket");
        assertThat(result.missingTraceabilitySectionTicketCount()).isEqualTo(3);
        assertThat(result.averageScoreBand()).isEqualTo("GOOD");
    }

    @Test
    void insights_normalizesFiltersAndDelegatesToRepository() {
        AuthUserContext pm = AuthUserContext.builder().role("PM").build();
        when(repository.findInsights(any())).thenReturn(new PmDashboardModels.DashboardInsights(List.of(), List.of()));

        service.insights(
                PROJECT_ID, " 2026-06 ", UUID.randomUUID(), "  plan  ", " good ", " high ", "  ticket  ", pm
        );

        ArgumentCaptor<PmDashboardModels.DashboardFilter> captor =
                ArgumentCaptor.forClass(PmDashboardModels.DashboardFilter.class);
        verify(repository).findInsights(captor.capture());
        assertThat(captor.getValue().scoreBand()).isEqualTo("GOOD");
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
    void exportDelegatesWithMaxPageSizeForCsv() {
        AuthUserContext pm = AuthUserContext.builder().role("PM").build();
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
    void tickets_normalizesFiltersAndDelegatesToRepository() {
        AuthUserContext pm = AuthUserContext.builder().role("PM").build();
        when(repository.findTickets(any())).thenReturn(new PageResult<>(List.of(), 1, 20, 0, 0));

        service.tickets(PROJECT_ID, " 2026-06 ", null, "  plan  ", " warning ", " medium ", "  abc  ", 1, 20, pm);

        ArgumentCaptor<PmDashboardModels.DashboardFilter> captor =
                ArgumentCaptor.forClass(PmDashboardModels.DashboardFilter.class);
        verify(repository).findTickets(captor.capture());
        assertThat(captor.getValue().scoreBand()).isEqualTo("WARNING");
        assertThat(captor.getValue().riskLevel()).isEqualTo("MEDIUM");
        assertThat(captor.getValue().search()).isEqualTo("abc");
        assertThat(captor.getValue().phaseCode()).isEqualTo("plan");
    }
}
