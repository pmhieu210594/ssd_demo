package com.sdd.platform.application.usecase.governance;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.CiRunRepositoryPort;
import com.sdd.platform.application.usecase.ingestion.CiRunModels.CiRunMetadataView;
import com.sdd.platform.domain.model.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CiRunMetadataServiceTest {

    private CiRunRepositoryPort repositoryPort;
    private CiRunMetadataService service;

    @BeforeEach
    void setUp() {
        repositoryPort = mock(CiRunRepositoryPort.class);
        service = new CiRunMetadataService(repositoryPort);
    }

    @Test
    void recent_normalizes_limit_for_admin_caller() {
        AppUser admin = user(AppUser.Role.ADMIN);
        List<CiRunMetadataView> rows = List.of(row());
        when(repositoryPort.findRecentCiRuns(30)).thenReturn(rows);

        List<CiRunMetadataView> result = service.recent(0, admin);

        assertEquals(rows, result);
        verify(repositoryPort).findRecentCiRuns(30);
    }

    @Test
    void recent_caps_limit_at_hundred() {
        AppUser admin = user(AppUser.Role.ADMIN);
        when(repositoryPort.findRecentCiRuns(100)).thenReturn(List.of());

        service.recent(200, admin);

        verify(repositoryPort).findRecentCiRuns(100);
    }

    @Test
    void recent_rejects_non_admin_caller() {
        AppUser viewer = user(AppUser.Role.VIEWER);

        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> service.recent(10, viewer));

        assertEquals("Component.Permission.Denied", ex.getMessage());
    }

    private static AppUser user(AppUser.Role role) {
        return AppUser.builder()
                .id(1L)
                .provider("internal")
                .providerUid("1")
                .email("ci@example.test")
                .displayName("CI Tester")
                .role(role)
                .active(true)
                .build();
    }

    private static CiRunMetadataView row() {
        return new CiRunMetadataView(
                UUID.fromString("00000000-0000-0000-0000-000000000701"),
                UUID.fromString("00000000-0000-0000-0000-000000000702"),
                "acme/widget",
                UUID.fromString("00000000-0000-0000-0000-000000000703"),
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
