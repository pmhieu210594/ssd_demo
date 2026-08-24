package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.governance.ExceptionKpiService;
import com.sdd.platform.application.usecase.governance.ExceptionKpiService.ExceptionKpiResult;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.web.dto.ExceptionKpiDtos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ExceptionKpiControllerTest {

    private static final UUID TICKET_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    // AC-FCI-7: endpoint always returns a DTO with correct counts
    @Test
    void forTicket_withExceptions_returnsCorrectCounts() {
        ExceptionKpiService service = mock(ExceptionKpiService.class);
        ExceptionKpiController controller = new ExceptionKpiController(service);
        AppUser admin = adminUser();

        ExceptionKpiResult result = new ExceptionKpiResult(
                TICKET_ID, 2, 1, 1, true, List.of()
        );
        when(service.computeForTicket(eq(TICKET_ID), eq(admin))).thenReturn(result);

        ExceptionKpiDtos.ExceptionKpiDto dto = controller.forTicket(TICKET_ID, admin);

        assertNotNull(dto);
        assertEquals(TICKET_ID, dto.ticketId());
        assertEquals(2, dto.totalExceptions());
        assertEquals(1, dto.openExceptions());
        assertEquals(1, dto.approvedExceptions());
        assertTrue(dto.hasExplicitExceptions());
        verify(service).computeForTicket(TICKET_ID, admin);
    }

    // AC-FCI-7: no exceptions → zero counts, never returns null
    @Test
    void forTicket_noExceptions_returnsZeroCountsDto() {
        ExceptionKpiService service = mock(ExceptionKpiService.class);
        ExceptionKpiController controller = new ExceptionKpiController(service);
        AppUser admin = adminUser();

        ExceptionKpiResult result = new ExceptionKpiResult(
                TICKET_ID, 0, 0, 0, false, List.of()
        );
        when(service.computeForTicket(eq(TICKET_ID), eq(admin))).thenReturn(result);

        ExceptionKpiDtos.ExceptionKpiDto dto = controller.forTicket(TICKET_ID, admin);

        assertNotNull(dto);
        assertEquals(0, dto.totalExceptions());
        assertFalse(dto.hasExplicitExceptions());
    }

    private static AppUser adminUser() {
        return AppUser.builder()
                .id(1L)
                .email("admin@test.com")
                .role(AppUser.Role.ADMIN)
                .active(true)
                .build();
    }
}
