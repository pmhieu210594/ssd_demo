package com.sdd.platform.application.usecase.securitydashboard;

import com.sdd.platform.application.port.out.persistence.SecurityDashboardRepositoryPort;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.ExceptionCounts;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SafetyPackCounts;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SastScaCounts;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecretScanCounts;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityFilter;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecuritySummaryModel;
import com.sdd.platform.domain.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class SecurityDashboardServiceTest {

    private SecurityDashboardRepositoryPort repository;
    private SecurityDashboardService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(SecurityDashboardRepositoryPort.class);
        service = new SecurityDashboardService(repository);
    }

    private void stubZeroState() {
        when(repository.findSafetyPackCounts(any())).thenReturn(new SafetyPackCounts(0, 0, 0));
        when(repository.findSecretScanCounts(any())).thenReturn(new SecretScanCounts(0, 0));
        when(repository.findSastScaCounts(any())).thenReturn(new SastScaCounts(0, 0, 0));
        when(repository.findExceptionCounts(any())).thenReturn(new ExceptionCounts(0, 0));
    }

    // ── BR: zero-value KPI when metadata does not exist (spec §6.4 / §8.3) ────

    @Test
    void getSummary_allZero_whenNoData() {
        stubZeroState();

        SecuritySummaryModel result = service.getSummary(null, null, null, null, null, null, null);

        assertEquals(0, result.safetyPack().readyCount());
        assertEquals(0, result.safetyPack().warningCount());
        assertEquals(0, result.safetyPack().missingCount());
        assertEquals(0, result.secretScan().passCount());
        assertEquals(0, result.secretScan().failCount());
        assertEquals(0, result.sastSca().passCount());
        assertEquals(0, result.exception().openCount());
    }

    @Test
    void getSummary_passesThroughCounts() {
        when(repository.findSafetyPackCounts(any())).thenReturn(new SafetyPackCounts(1, 2, 3));
        when(repository.findSecretScanCounts(any())).thenReturn(new SecretScanCounts(5, 1));
        when(repository.findSastScaCounts(any())).thenReturn(new SastScaCounts(4, 2, 1));
        when(repository.findExceptionCounts(any())).thenReturn(new ExceptionCounts(1, 4));

        SecuritySummaryModel result = service.getSummary(null, null, null, null, null, null, null);

        assertEquals(1, result.safetyPack().readyCount());
        assertEquals(5, result.secretScan().passCount());
        assertEquals(4, result.sastSca().passCount());
        assertEquals(1, result.exception().openCount());
    }

    // ── getTicketDetail: not found ──────────────────────────────────────────

    @Test
    void getTicketDetail_throwsNotFound_whenMissing() {
        UUID ticketId = UUID.randomUUID();
        when(repository.findTicketDetail(ticketId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getTicketDetail(ticketId));
    }

    // ── normalize: validation ───────────────────────────────────────────────

    @Test
    void normalize_throwsOnSearchTooLong() {
        String longSearch = "a".repeat(201);
        assertThrows(IllegalArgumentException.class,
                () -> service.normalize(null, null, longSearch, null, null, null, null, 0, 20));
    }

    @Test
    void normalize_blankSearchBecomesNull() {
        SecurityFilter filter = service.normalize(null, null, "   ", null, null, null, null, 0, 20);
        assertNull(filter.search());
    }

    @Test
    void normalize_throwsOnInvalidSafetyStatus() {
        assertThrows(IllegalArgumentException.class,
                () -> service.normalize(null, null, null, "BOGUS", null, null, null, 0, 20));
    }

    @Test
    void normalize_throwsOnInvalidSecretScanStatus() {
        assertThrows(IllegalArgumentException.class,
                () -> service.normalize(null, null, null, null, "BOGUS", null, null, 0, 20));
    }

    @Test
    void normalize_throwsOnInvalidSastStatus() {
        assertThrows(IllegalArgumentException.class,
                () -> service.normalize(null, null, null, null, null, "BOGUS", null, 0, 20));
    }

    @Test
    void normalize_throwsOnInvalidExceptionStatus() {
        assertThrows(IllegalArgumentException.class,
                () -> service.normalize(null, null, null, null, null, null, "BOGUS", 0, 20));
    }

    @Test
    void normalize_acceptsValidEnumsCaseInsensitive() {
        SecurityFilter filter = assertDoesNotThrow(() ->
                service.normalize(null, null, null, "ready", "pass", "warning", "open", 0, 20));

        assertEquals("READY", filter.safetyStatus());
        assertEquals("PASS", filter.secretScanStatus());
        assertEquals("WARNING", filter.sastStatus());
        assertEquals("OPEN", filter.exceptionStatus());
    }

    @Test
    void normalize_pageAndSizeClamped() {
        SecurityFilter negPage = service.normalize(null, null, null, null, null, null, null, -5, 20);
        assertEquals(0, negPage.page());

        SecurityFilter oversizeSize = service.normalize(null, null, null, null, null, null, null, 0, 999);
        assertEquals(100, oversizeSize.size());
    }

    @Test
    void normalize_passesProjectIdAndRepositoryId() {
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID repositoryId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        SecurityFilter filter = service.normalize(projectId, repositoryId, null, null, null, null, null, 0, 20);

        assertEquals(projectId, filter.projectId());
        assertEquals(repositoryId, filter.repositoryId());
    }
}
