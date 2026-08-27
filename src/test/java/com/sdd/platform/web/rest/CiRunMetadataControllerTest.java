package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.governance.CiRunMetadataService;
import com.sdd.platform.application.usecase.ingestion.CiRunModels;
import com.sdd.platform.domain.model.AppUser;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CiRunMetadataControllerTest {

    @Test
    void list_maps_service_rows_to_contract_dto() {
        CiRunMetadataService service = mock(CiRunMetadataService.class);
        CiRunMetadataController controller = new CiRunMetadataController(service);
        AppUser admin = user();
        List<CiRunModels.CiRunMetadataView> rows = List.of(row());
        when(service.recent(eq(25), eq(admin))).thenReturn(rows);

        List<com.sdd.platform.web.dto.CiRunMetadataDtos.CiRunMetadataDto> result = controller.list(25, admin);

        assertEquals(1, result.size());
        assertEquals(rows.get(0).ciRunId(), result.get(0).ciRunId());
        assertEquals(rows.get(0).workflowName(), result.get(0).workflowName());
        assertEquals(rows.get(0).jobName(), result.get(0).jobName());
        assertEquals(rows.get(0).ciUrl(), result.get(0).ciUrl());
        verify(service).recent(25, admin);
    }

    private static AppUser user() {
        return AppUser.builder()
                .id(10L)
                .provider("internal")
                .providerUid("10")
                .email("admin@example.test")
                .displayName("Admin")
                .role(AppUser.Role.ADMIN)
                .active(true)
                .build();
    }

    private static CiRunModels.CiRunMetadataView row() {
        return new CiRunModels.CiRunMetadataView(
                UUID.fromString("00000000-0000-0000-0000-000000000801"),
                UUID.fromString("00000000-0000-0000-0000-000000000802"),
                "acme/widget",
                UUID.fromString("00000000-0000-0000-0000-000000000803"),
                "GITHUB_ACTIONS",
                "CI",
                "build",
                "27660577827",
                "77123456789",
                "https://github.com/acme/widget/actions/runs/27660577827/job/77123456789",
                "SUCCESS",
                OffsetDateTime.parse("2026-06-17T02:00:00Z"),
                OffsetDateTime.parse("2026-06-17T02:05:00Z"),
                OffsetDateTime.parse("2026-06-17T02:10:00Z")
        );
    }
}
