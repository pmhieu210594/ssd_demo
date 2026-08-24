package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.governance.FirstCiPassKpiService;
import com.sdd.platform.application.usecase.governance.FirstCiPassKpiService.FirstCiPassResult;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.web.dto.FirstCiPassKpiDtos;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FirstCiPassKpiControllerTest {

    private static final UUID TICKET_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CI_RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    // AC-FCI-2/3: first run found → 200 with DTO
    @Test
    void forTicket_found_returns200WithDto() {
        FirstCiPassKpiService service = mock(FirstCiPassKpiService.class);
        FirstCiPassKpiController controller = new FirstCiPassKpiController(service);
        AppUser admin = adminUser();

        FirstCiPassResult result = new FirstCiPassResult(
                CI_RUN_ID, null, "SUCCESS", true,
                OffsetDateTime.parse("2026-06-01T10:00:00Z")
        );
        when(service.computeForTicket(eq(TICKET_ID), eq(admin))).thenReturn(Optional.of(result));

        ResponseEntity<FirstCiPassKpiDtos.FirstCiPassDto> response = controller.forTicket(TICKET_ID, admin);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(CI_RUN_ID, response.getBody().ciRunId());
        assertTrue(response.getBody().firstPassSuccess());
        assertEquals("SUCCESS", response.getBody().firstRunStatus());
        verify(service).computeForTicket(TICKET_ID, admin);
    }

    // AC-FCI-2/3 edge: no CI run found → 404
    @Test
    void forTicket_notFound_returns404() {
        FirstCiPassKpiService service = mock(FirstCiPassKpiService.class);
        FirstCiPassKpiController controller = new FirstCiPassKpiController(service);
        AppUser admin = adminUser();

        when(service.computeForTicket(eq(TICKET_ID), eq(admin))).thenReturn(Optional.empty());

        ResponseEntity<FirstCiPassKpiDtos.FirstCiPassDto> response = controller.forTicket(TICKET_ID, admin);

        assertEquals(404, response.getStatusCode().value());
        assertNull(response.getBody());
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
