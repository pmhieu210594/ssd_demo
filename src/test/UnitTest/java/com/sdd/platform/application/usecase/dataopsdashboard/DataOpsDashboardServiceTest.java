package com.sdd.platform.application.usecase.dataopsdashboard;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.DataOpsDashboardRepositoryPort;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AuthUserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataOpsDashboardServiceTest {

    private static final UUID PROJECT_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID CONNECTOR_ID = UUID.fromString("90000000-0000-0000-0000-000000000002");

    @Mock
    private DataOpsDashboardRepositoryPort repository;

    private DataOpsDashboardService service;

    @BeforeEach
    void setUp() {
        service = new DataOpsDashboardService(repository);
    }

    @Test
    void summary_requiresAuthenticatedCaller() {
        assertThatThrownBy(() -> service.summary(null, null, null, null, null, null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Component.Permission.Denied");
    }

    @Test
    void connectors_requiresAuthenticatedCaller() {
        assertThatThrownBy(() -> service.connectors(null, null, null, null, null, 1, 20, null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Component.Permission.Denied");
    }

    @Test
    void connectorDetail_requiresAuthenticatedCaller() {
        assertThatThrownBy(() -> service.connectorDetail(CONNECTOR_ID, null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Component.Permission.Denied");
    }

    @Test
    void summary_normalizesFiltersAndDelegatesToRepository() {
        AuthUserContext caller = AuthUserContext.builder().role("DATA_OPS").build();
        when(repository.findProjectRole(caller, PROJECT_ID)).thenReturn("DATA_OPS");
        when(repository.findSummary(any())).thenReturn(new DataOpsDashboardModels.DataOpsDashboardSummary(
                1, 2, 3, 4, 5, OffsetDateTime.parse("2026-07-02T00:00:00Z")
        ));

        DataOpsDashboardModels.DataOpsDashboardSummary result = service.summary(
                PROJECT_ID, null, "  github-connector  ", "  error  ", "  ticket  ", caller
        );

        ArgumentCaptor<DataOpsDashboardModels.DataOpsDashboardFilter> captor =
                ArgumentCaptor.forClass(DataOpsDashboardModels.DataOpsDashboardFilter.class);
        verify(repository).findSummary(captor.capture());
        assertThat(captor.getValue().projectId()).isEqualTo(PROJECT_ID);
        assertThat(captor.getValue().connectorName()).isEqualTo("github-connector");
        assertThat(captor.getValue().parserStatus()).isEqualTo("ERROR");
        assertThat(captor.getValue().search()).isEqualTo("ticket");
        assertThat(result.connectorFailureCount()).isEqualTo(1);
        assertThat(result.brokenLinkCount()).isEqualTo(5);
    }

    @Test
    void summary_rejectsInvalidParserStatus() {
        AuthUserContext caller = AuthUserContext.builder().role("DATA_OPS").build();
        when(repository.findProjectRole(caller, PROJECT_ID)).thenReturn("DATA_OPS");

        assertThatThrownBy(() -> service.summary(PROJECT_ID, null, null, "BOGUS", null, caller))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Pages.DataOpsDashboard.ParserStatus.Invalid");
    }

    @Test
    void summary_rejectsSearchTermTooLong() {
        AuthUserContext caller = AuthUserContext.builder().role("DATA_OPS").build();
        when(repository.findProjectRole(caller, PROJECT_ID)).thenReturn("DATA_OPS");
        String tooLong = "a".repeat(256);

        assertThatThrownBy(() -> service.summary(PROJECT_ID, null, null, null, tooLong, caller))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Pages.DataOpsDashboard.Search.TooLong");
    }

    @Test
    void connectorDetail_throwsNotFoundWhenMissing() {
        AuthUserContext caller = AuthUserContext.builder().role("DATA_OPS").build();
        when(repository.findConnectorDetail(CONNECTOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.connectorDetail(CONNECTOR_ID, caller))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void connectors_appliesZeroValueKpisAndEmptyStateSemantics() {
        AuthUserContext caller = AuthUserContext.builder().role("DATA_OPS").build();
        when(repository.findProjectRole(caller, PROJECT_ID)).thenReturn("DATA_OPS");
        when(repository.findConnectors(any())).thenReturn(
                new DataOpsDashboardModels.DataOpsDashboardPage(List.of(), 1, 20, 0L, 0)
        );

        DataOpsDashboardModels.DataOpsDashboardPage page =
                service.connectors(PROJECT_ID, null, null, null, null, 1, 20, caller);

        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    @Test
    void summary_deniesProjectMemberWhoseRoleAtThatProjectIsNotDataOps() {
        // Caller holds DATA_OPS somewhere else, but only PM at PROJECT_ID — must be denied.
        AuthUserContext caller = AuthUserContext.builder().role("PM").build();
        when(repository.findProjectRole(caller, PROJECT_ID)).thenReturn("PM");

        assertThatThrownBy(() -> service.summary(PROJECT_ID, null, null, null, null, caller))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Component.Permission.Denied");
    }

    @Test
    void summary_allowsProjectMemberWhoseRoleAtThatProjectIsDataOps() {
        AuthUserContext caller = AuthUserContext.builder().role("PM").build();
        when(repository.findProjectRole(caller, PROJECT_ID)).thenReturn("DATA_OPS");
        when(repository.findSummary(any())).thenReturn(new DataOpsDashboardModels.DataOpsDashboardSummary(
                0, 0, 0, 0, 0, OffsetDateTime.parse("2026-07-02T00:00:00Z")
        ));

        DataOpsDashboardModels.DataOpsDashboardSummary result =
                service.summary(PROJECT_ID, null, null, null, null, caller);

        assertThat(result).isNotNull();
    }

    @Test
    void options_delegatesToRepository() {
        AuthUserContext caller = AuthUserContext.builder().role("DATA_OPS").build();
        when(repository.findOptions(PROJECT_ID, null, caller)).thenReturn(
                new DataOpsDashboardModels.DataOpsDashboardOptions(List.of(), List.of(), List.of())
        );

        DataOpsDashboardModels.DataOpsDashboardOptions options = service.options(PROJECT_ID, null, caller);

        assertThat(options.projects()).isEmpty();
        verify(repository).findOptions(PROJECT_ID, null, caller);
    }
}
