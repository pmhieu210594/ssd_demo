package com.sdd.platform.application.usecase.governance;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.ExceptionKpiRepositoryPort;
import com.sdd.platform.application.usecase.governance.ExceptionKpiService.ExceptionKpiResult;
import com.sdd.platform.application.usecase.governance.ExceptionKpiService.ExceptionSummary;
import com.sdd.platform.domain.model.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExceptionKpiServiceTest {

    private static final UUID TICKET_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private ExceptionKpiRepositoryPort repositoryPort;
    private ExceptionKpiService service;

    @BeforeEach
    void setUp() {
        repositoryPort = mock(ExceptionKpiRepositoryPort.class);
        service = new ExceptionKpiService(repositoryPort);
    }

    // AC-FCI-7: Exception KPI delegates to repository and returns explicit record counts
    @Test
    void computeForTicket_delegatesToRepositoryAndReturnsResult() {
        AppUser admin = adminUser();
        ExceptionKpiResult stubResult = new ExceptionKpiResult(
                TICKET_ID, 2, 1, 1, true,
                List.of(
                        exception("DEADLINE_EXTENSION", false, "OPEN"),
                        exception("SCOPE_REDUCTION", true, "CLOSED")
                )
        );
        when(repositoryPort.findExceptionKpi(TICKET_ID)).thenReturn(stubResult);

        ExceptionKpiResult result = service.computeForTicket(TICKET_ID, admin);

        assertEquals(2, result.totalExceptions());
        assertEquals(1, result.openExceptions());
        assertEquals(1, result.approvedExceptions());
        assertTrue(result.hasExplicitExceptions());
        verify(repositoryPort).findExceptionKpi(TICKET_ID);
    }

    // AC-FCI-7: no exceptions → zero counts, hasExplicitExceptions = false
    @Test
    void computeForTicket_noExceptions_returnsZeroCounts() {
        AppUser admin = adminUser();
        ExceptionKpiResult stubResult = new ExceptionKpiResult(TICKET_ID, 0, 0, 0, false, List.of());
        when(repositoryPort.findExceptionKpi(TICKET_ID)).thenReturn(stubResult);

        ExceptionKpiResult result = service.computeForTicket(TICKET_ID, admin);

        assertEquals(0, result.totalExceptions());
        assertFalse(result.hasExplicitExceptions());
    }

    // AC-FCI-7: mixed open/approved statuses reflected correctly
    @Test
    void computeForTicket_mixedStatuses_preservesRepositoryCounts() {
        AppUser admin = adminUser();
        ExceptionKpiResult stubResult = new ExceptionKpiResult(
                TICKET_ID, 3, 2, 1, true,
                List.of(
                        exception("TYPE_A", false, "OPEN"),
                        exception("TYPE_B", false, "OPEN"),
                        exception("TYPE_C", true, "CLOSED")
                )
        );
        when(repositoryPort.findExceptionKpi(TICKET_ID)).thenReturn(stubResult);

        ExceptionKpiResult result = service.computeForTicket(TICKET_ID, admin);

        assertEquals(3, result.totalExceptions());
        assertEquals(2, result.openExceptions());
        assertEquals(1, result.approvedExceptions());
    }

    // AC-FCI-8: non-ADMIN caller → ForbiddenException
    @Test
    void computeForTicket_nonAdminCaller_throwsForbidden() {
        AppUser viewer = viewerUser();

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> service.computeForTicket(TICKET_ID, viewer));

        assertEquals("Component.Permission.Denied", ex.getMessage());
        verifyNoInteractions(repositoryPort);
    }

    private static ExceptionSummary exception(String type, boolean approved, String followUpStatus) {
        return new ExceptionSummary(
                UUID.fromString("00000000-0000-0000-0000-000000000099"),
                type,
                true,
                approved,
                followUpStatus,
                "SELF_REVIEW",
                null
        );
    }

    private static AppUser adminUser() {
        return AppUser.builder()
                .id(1L)
                .email("admin@test.com")
                .role(AppUser.Role.ADMIN)
                .active(true)
                .build();
    }

    private static AppUser viewerUser() {
        return AppUser.builder()
                .id(2L)
                .email("viewer@test.com")
                .role(AppUser.Role.VIEWER)
                .active(true)
                .build();
    }
}
