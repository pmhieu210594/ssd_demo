package com.sdd.platform.application.usecase.qadashboard;

import com.sdd.platform.application.port.out.persistence.QaDashboardRepositoryPort;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.AcCoverageCounts;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaFilter;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaSummaryModel;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.TestRunCounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class QaDashboardServiceTest {

    private QaDashboardRepositoryPort repository;
    private QaDashboardService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(QaDashboardRepositoryPort.class);
        service = new QaDashboardService(repository);
    }

    private void stubZeroState() {
        when(repository.findAcCoverageCounts(any())).thenReturn(new AcCoverageCounts(0, 0, 0));
        when(repository.findDefectLeakageCount(any())).thenReturn(0);
        when(repository.findTestRunCounts(any())).thenReturn(new TestRunCounts(0, 0, 0, 0));
    }

    // ── BR-2: AC coverage % ──────────────────────────────────────────────────

    @Test
    void getSummary_acCoveragePercent_normalCase() {
        // 3 covered / 5 total = 60.0%
        when(repository.findAcCoverageCounts(any())).thenReturn(new AcCoverageCounts(3, 2, 5));
        when(repository.findDefectLeakageCount(any())).thenReturn(0);
        when(repository.findTestRunCounts(any())).thenReturn(new TestRunCounts(0, 0, 0, 0));

        QaSummaryModel result = service.getSummary(null, null, null, null);

        assertEquals(60.0, result.acTestCoveragePercent());
        assertEquals(2, result.acNotTestedCount());
    }

    @Test
    void getSummary_acCoveragePercent_zeroDenominator() {
        // 0 total AC → must return 0.0 without division-by-zero
        when(repository.findAcCoverageCounts(any())).thenReturn(new AcCoverageCounts(0, 0, 0));
        when(repository.findDefectLeakageCount(any())).thenReturn(0);
        when(repository.findTestRunCounts(any())).thenReturn(new TestRunCounts(0, 0, 0, 0));

        QaSummaryModel result = service.getSummary(null, null, null, null);

        assertEquals(0.0, result.acTestCoveragePercent());
    }

    @Test
    void getSummary_acCoveragePercent_roundsToOneDecimal() {
        // 2/3 = 66.6666... → 66.7
        when(repository.findAcCoverageCounts(any())).thenReturn(new AcCoverageCounts(2, 1, 3));
        when(repository.findDefectLeakageCount(any())).thenReturn(0);
        when(repository.findTestRunCounts(any())).thenReturn(new TestRunCounts(0, 0, 0, 0));

        QaSummaryModel result = service.getSummary(null, null, null, null);

        assertEquals(66.7, result.acTestCoveragePercent());
    }

    @Test
    void getSummary_acCoveragePercent_countsAllTestedAsFullyCovered() {
        // All 18 ACs have at least one linked test, so coverage should be 100%.
        // The covered-count snapshot can still be lower than totalCount in the
        // current model, so this guards the testedCount-based formula.
        when(repository.findAcCoverageCounts(any())).thenReturn(new AcCoverageCounts(17, 0, 18));
        when(repository.findDefectLeakageCount(any())).thenReturn(0);
        when(repository.findTestRunCounts(any())).thenReturn(new TestRunCounts(0, 0, 0, 0));

        QaSummaryModel result = service.getSummary(null, null, null, null);

        assertEquals(100.0, result.acTestCoveragePercent());
        assertEquals(0, result.acNotTestedCount());
    }

    // ── BR-3: Blackbox coverage (placeholder) ────────────────────────────────

    @Test
    void getSummary_blackboxCoverageIsAlwaysPlaceholder() {
        stubZeroState();

        QaSummaryModel result = service.getSummary(null, null, null, null);

        assertEquals(0.0, result.blackboxCoveragePercent());
    }

    // ── Test pass percent ────────────────────────────────────────────────────

    @Test
    void getSummary_testPassPercent_normalCase() {
        // 4 passed / 5 total = 80.0%
        when(repository.findAcCoverageCounts(any())).thenReturn(new AcCoverageCounts(0, 0, 0));
        when(repository.findDefectLeakageCount(any())).thenReturn(0);
        when(repository.findTestRunCounts(any())).thenReturn(new TestRunCounts(4, 1, 0, 5));

        QaSummaryModel result = service.getSummary(null, null, null, null);

        assertEquals(80.0, result.testResultsPassPercent());
    }

    @Test
    void getSummary_testPassPercent_zeroDenominator() {
        when(repository.findAcCoverageCounts(any())).thenReturn(new AcCoverageCounts(0, 0, 0));
        when(repository.findDefectLeakageCount(any())).thenReturn(0);
        when(repository.findTestRunCounts(any())).thenReturn(new TestRunCounts(0, 0, 0, 0));

        QaSummaryModel result = service.getSummary(null, null, null, null);

        assertEquals(0.0, result.testResultsPassPercent());
    }

    // ── Defect leakage count ─────────────────────────────────────────────────

    @Test
    void getSummary_defectLeakageCount() {
        when(repository.findAcCoverageCounts(any())).thenReturn(new AcCoverageCounts(0, 0, 0));
        when(repository.findDefectLeakageCount(any())).thenReturn(3);
        when(repository.findTestRunCounts(any())).thenReturn(new TestRunCounts(0, 0, 0, 0));

        QaSummaryModel result = service.getSummary(null, null, null, null);

        assertEquals(3, result.defectLeakageCount());
    }

    // ── Zero-state (parser has not run yet) ──────────────────────────────────

    @Test
    void getSummary_allZero_whenNoData() {
        stubZeroState();

        QaSummaryModel result = service.getSummary(null, null, null, null);

        assertEquals(0.0, result.acTestCoveragePercent());
        assertEquals(0, result.acNotTestedCount());
        assertEquals(0.0, result.blackboxCoveragePercent());
        assertEquals(0.0, result.testResultsPassPercent());
        assertEquals(0, result.defectLeakageCount());
        assertEquals(0, result.acceptanceReadyCount());
    }

    @Test
    void getSummary_acceptanceReadyCountIsZeroForPoC() {
        stubZeroState();

        QaSummaryModel result = service.getSummary(null, null, null, null);

        assertEquals(0, result.acceptanceReadyCount());
    }

    // ── normalize: validation ─────────────────────────────────────────────────

    @Test
    void normalize_throwsOnSearchTooLong() {
        String longSearch = "a".repeat(201);
        assertThrows(IllegalArgumentException.class,
                () -> service.normalize(null, null, null, longSearch, 0, 20));
    }

    @Test
    void normalize_blankSearchBecomesNull() {
        QaFilter filter = service.normalize(null, null, null, "   ", 0, 20);
        assertNull(filter.search());
    }

    @Test
    void normalize_passesTicketId() {
        UUID ticketId = UUID.fromString("00000000-0000-0000-0000-000000000003");

        QaFilter filter = service.normalize(null, null, ticketId, null, 0, 20);

        assertEquals(ticketId, filter.ticketId());
    }

    @Test
    void normalize_pageAndSizeClamped() {
        QaFilter negPage = service.normalize(null, null, null, null, -5, 20);
        assertEquals(0, negPage.page());

        QaFilter oversizeSize = service.normalize(null, null, null, null, 0, 999);
        assertEquals(100, oversizeSize.size());
    }

    @Test
    void normalize_passesProjectIdAndRepositoryId() {
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID repositoryId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        QaFilter filter = service.normalize(projectId, repositoryId, null, null, 0, 20);

        assertEquals(projectId, filter.projectId());
        assertEquals(repositoryId, filter.repositoryId());
    }
}
