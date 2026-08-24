package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.AcCoveragePage;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.AcCoverageRow;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaSummaryModel;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.TrendPoint;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardService;
import com.sdd.platform.web.dto.QaDashboardDtos;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QaDashboardControllerTest {

    @Test
    void summary_delegatesToServiceAndMapsDto() {
        QaDashboardService service = mock(QaDashboardService.class);
        QaDashboardController controller = new QaDashboardController(service);
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        QaSummaryModel model = new QaSummaryModel(
                75.0, 3, 0.0, 80.0, 2, 0, OffsetDateTime.parse("2026-06-29T00:00:00Z"));
        when(service.getSummary(projectId, null, null, null)).thenReturn(model);

        QaDashboardDtos.QaDashboardSummaryDto dto = controller.summary(projectId, null, null, null, null);

        assertEquals(75.0, dto.acTestCoveragePercent());
        assertEquals(3, dto.acNotTestedCount());
        assertEquals(0.0, dto.blackboxCoveragePercent());
        assertEquals(80.0, dto.testResultsPassPercent());
        assertEquals(2, dto.defectLeakageCount());
        assertEquals(0, dto.acceptanceReadyCount());
        verify(service).getSummary(projectId, null, null, null);
    }

    @Test
    void acceptanceCriteria_delegatesToServiceAndMapsPage() {
        QaDashboardService service = mock(QaDashboardService.class);
        QaDashboardController controller = new QaDashboardController(service);
        AcCoverageRow row = new AcCoverageRow("AC-1", "PROJ-42", "NOT_TESTED", "No", "missing test");
        // page=0, totalPages=1 → hasNext=false
        AcCoveragePage page = new AcCoveragePage(List.of(row), 0, 20, 1L, 1);
        when(service.getAcceptanceCriteria(null, null, null, null, 0, 20)).thenReturn(page);

        QaDashboardDtos.AcceptanceCriteriaPageDto dto =
                controller.acceptanceCriteria(null, null, null, null, 0, 20, null);

        assertEquals(1, dto.items().size());
        assertEquals("AC-1", dto.items().get(0).acId());
        assertEquals("PROJ-42", dto.items().get(0).ticketKey());
        assertEquals("NOT_TESTED", dto.items().get(0).status());
        assertEquals(0, dto.page());
        assertEquals(1, dto.totalPages());
        assertFalse(dto.hasNext());
        verify(service).getAcceptanceCriteria(null, null, null, null, 0, 20);
    }

    @Test
    void acceptanceCriteria_hasNextTrueWhenMorePages() {
        QaDashboardService service = mock(QaDashboardService.class);
        QaDashboardController controller = new QaDashboardController(service);
        AcCoverageRow row = new AcCoverageRow("AC-2", "PROJ-43", "PASSED", "Yes", "");
        // page=0, totalPages=3 → hasNext=true
        AcCoveragePage page = new AcCoveragePage(List.of(row), 0, 20, 50L, 3);
        when(service.getAcceptanceCriteria(null, null, null, null, 0, 20)).thenReturn(page);

        QaDashboardDtos.AcceptanceCriteriaPageDto dto =
                controller.acceptanceCriteria(null, null, null, null, 0, 20, null);

        assertTrue(dto.hasNext());
        assertEquals(3, dto.totalPages());
        assertEquals(50L, dto.totalElements());
    }

    @Test
    void coverageTrend_delegatesToServiceAndMapsList() {
        QaDashboardService service = mock(QaDashboardService.class);
        QaDashboardController controller = new QaDashboardController(service);
        List<TrendPoint> trendPoints = List.of(
                new TrendPoint("W1", 50.0),
                new TrendPoint("W2", 75.0)
        );
        when(service.getCoverageTrend(null, null, null, null)).thenReturn(trendPoints);

        List<QaDashboardDtos.AcCoverageTrendPointDto> result =
                controller.coverageTrend(null, null, null, null, null);

        assertEquals(2, result.size());
        assertEquals("W1", result.get(0).label());
        assertEquals(50.0, result.get(0).coveragePercent());
        assertEquals("W2", result.get(1).label());
        assertEquals(75.0, result.get(1).coveragePercent());
        verify(service).getCoverageTrend(null, null, null, null);
    }
}
