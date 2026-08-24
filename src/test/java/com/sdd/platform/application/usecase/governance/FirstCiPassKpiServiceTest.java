package com.sdd.platform.application.usecase.governance;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.CiRunRepositoryPort;
import com.sdd.platform.application.usecase.governance.FirstCiPassKpiService.FirstCiPassResult;
import com.sdd.platform.application.usecase.ingestion.CiRunModels.CiRunMetadataView;
import com.sdd.platform.domain.model.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FirstCiPassKpiServiceTest {

    private static final UUID TICKET_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CI_RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private CiRunRepositoryPort repositoryPort;
    private FirstCiPassKpiService service;

    @BeforeEach
    void setUp() {
        repositoryPort = mock(CiRunRepositoryPort.class);
        service = new FirstCiPassKpiService(repositoryPort);
    }

    // AC-FCI-1: earliest CI run selected from repository
    @Test
    void computeForTicket_delegatesToRepositoryAndReturnsRun() {
        AppUser admin = adminUser();
        CiRunMetadataView run = ciRunView("SUCCESS");
        when(repositoryPort.findFirstCiRunByTicketId(TICKET_ID)).thenReturn(Optional.of(run));

        Optional<FirstCiPassResult> result = service.computeForTicket(TICKET_ID, admin);

        assertTrue(result.isPresent());
        assertEquals(CI_RUN_ID, result.get().ciRunId());
        verify(repositoryPort).findFirstCiRunByTicketId(TICKET_ID);
    }

    // AC-FCI-1 edge: no runs found
    @Test
    void computeForTicket_noRunsFound_returnsEmpty() {
        AppUser admin = adminUser();
        when(repositoryPort.findFirstCiRunByTicketId(TICKET_ID)).thenReturn(Optional.empty());

        Optional<FirstCiPassResult> result = service.computeForTicket(TICKET_ID, admin);

        assertTrue(result.isEmpty());
    }

    // AC-FCI-2: SUCCESS status → firstPassSuccess = true
    @Test
    void computeForTicket_successStatus_firstPassSuccessTrue() {
        AppUser admin = adminUser();
        when(repositoryPort.findFirstCiRunByTicketId(TICKET_ID)).thenReturn(Optional.of(ciRunView("SUCCESS")));

        Optional<FirstCiPassResult> result = service.computeForTicket(TICKET_ID, admin);

        assertTrue(result.isPresent());
        assertTrue(result.get().firstPassSuccess());
        assertEquals("SUCCESS", result.get().firstRunStatus());
    }

    // AC-FCI-3: non-SUCCESS status → firstPassSuccess = false
    @Test
    void computeForTicket_failedStatus_firstPassSuccessFalse() {
        AppUser admin = adminUser();
        when(repositoryPort.findFirstCiRunByTicketId(TICKET_ID)).thenReturn(Optional.of(ciRunView("FAILED")));

        Optional<FirstCiPassResult> result = service.computeForTicket(TICKET_ID, admin);

        assertTrue(result.isPresent());
        assertFalse(result.get().firstPassSuccess());
        assertEquals("FAILED", result.get().firstRunStatus());
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

    private static CiRunMetadataView ciRunView(String status) {
        return new CiRunMetadataView(
                CI_RUN_ID,
                UUID.fromString("00000000-0000-0000-0000-000000000010"),
                "acme/widget",
                UUID.fromString("00000000-0000-0000-0000-000000000020"),
                "GITHUB_ACTIONS",
                "CI",
                "build-001",
                "https://ci.example.com/runs/1",
                status,
                OffsetDateTime.parse("2026-06-01T10:00:00Z"),
                OffsetDateTime.parse("2026-06-01T10:05:00Z"),
                OffsetDateTime.parse("2026-06-01T10:06:00Z")
        );
    }
}
