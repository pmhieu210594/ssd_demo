package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ScoreCriterion;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ScoreResult;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreService;
import com.sdd.platform.web.dto.EvidenceQualityScoreDtos;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceQualityScoreControllerTest {

    @Test
    void get_latest_maps_service_result_to_contract_dto() {
        EvidenceQualityScoreService service = mock(EvidenceQualityScoreService.class);
        EvidenceQualityScoreController controller = new EvidenceQualityScoreController(service);
        UUID ticketId = UUID.fromString("00000000-0000-0000-0000-00000000c001");
        ScoreResult result = result(ticketId);
        when(service.latest(ticketId)).thenReturn(result);

        EvidenceQualityScoreDtos.EvidenceQualityScoreResponseDto dto = controller.getLatest(ticketId.toString());

        assertEquals(ticketId, dto.ticketId());
        assertEquals("Excellent", dto.band());
        assertEquals(1, dto.breakdown().size());
        verify(service).latest(ticketId);
    }

    @Test
    void post_recalculate_uses_service_and_maps_response() {
        EvidenceQualityScoreService service = mock(EvidenceQualityScoreService.class);
        EvidenceQualityScoreController controller = new EvidenceQualityScoreController(service);
        UUID ticketId = UUID.fromString("00000000-0000-0000-0000-00000000c002");
        ScoreResult result = result(ticketId);
        when(service.recalculateFromCi(any(UUID.class), any(), any())).thenReturn(result);

        EvidenceQualityScoreDtos.EvidenceQualityScoreResponseDto dto = controller.recalculate(
                new EvidenceQualityScoreDtos.EvidenceQualityScoreCalculationRequestDto(
                        ticketId.toString(),
                        null,
                        true,
                        null,
                        "tester@example.com"));

        assertEquals(ticketId, dto.ticketId());
        assertEquals("Excellent", dto.band());
        verify(service).recalculateFromCi(ticketId, "v0", "tester@example.com");
    }

    @Test
    void post_partial_uses_parser_trigger() {
        EvidenceQualityScoreService service = mock(EvidenceQualityScoreService.class);
        EvidenceQualityScoreController controller = new EvidenceQualityScoreController(service);
        UUID ticketId = UUID.fromString("00000000-0000-0000-0000-00000000c003");
        ScoreResult result = result(ticketId);
        when(service.recalculateFromParser(any(UUID.class), any(), any())).thenReturn(result);

        EvidenceQualityScoreDtos.EvidenceQualityScoreResponseDto dto = controller.recalculatePartial(
                ticketId.toString(),
                new EvidenceQualityScoreDtos.EvidenceQualityScoreCalculationRequestDto(
                        null,
                        null,
                        true,
                        null,
                        "parser@example.com"));

        assertEquals(ticketId, dto.ticketId());
        assertEquals("Excellent", dto.band());
        verify(service).recalculateFromParser(ticketId, "v0", "parser@example.com");
    }

    private static ScoreResult result(UUID ticketId) {
        return new ScoreResult(
                UUID.fromString("00000000-0000-0000-0000-00000000ca01"),
                UUID.fromString("00000000-0000-0000-0000-00000000ca02"),
                ticketId,
                new BigDecimal("100.00"),
                "Excellent",
                List.of(new ScoreCriterion("c1", "criterion", new BigDecimal("10.00"), new BigDecimal("10.00"), "complete", List.of("ref"))),
                List.of(),
                List.of(),
                List.of(),
                "v0",
                "final",
                OffsetDateTime.parse("2026-06-24T00:00:00Z"),
                new BigDecimal("10.00"),
                new BigDecimal("10.00"),
                new BigDecimal("10.00"),
                new BigDecimal("10.00"),
                new BigDecimal("10.00"),
                new BigDecimal("10.00"),
                new BigDecimal("10.00"),
                new BigDecimal("10.00"),
                List.of()
        );
    }
}
