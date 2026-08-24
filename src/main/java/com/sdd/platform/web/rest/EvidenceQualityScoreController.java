package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreService;
import com.sdd.platform.web.dto.EvidenceQualityScoreDtos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/evidence-quality-scores")
public class EvidenceQualityScoreController {

    private final EvidenceQualityScoreService service;

    public EvidenceQualityScoreController(EvidenceQualityScoreService service) {
        this.service = service;
    }

    @GetMapping("/tickets/{ticketId}")
    public EvidenceQualityScoreDtos.EvidenceQualityScoreResponseDto getLatest(@PathVariable @NotBlank String ticketId) {
        return EvidenceQualityScoreDtos.EvidenceQualityScoreResponseDto.from(service.latest(UUID.fromString(ticketId)));
    }

    @PostMapping
    public EvidenceQualityScoreDtos.EvidenceQualityScoreResponseDto recalculate(@Valid @RequestBody EvidenceQualityScoreDtos.EvidenceQualityScoreCalculationRequestDto request) {
        return recalculateFinal(request);
    }

    @PostMapping("/tickets/{ticketId}/partial")
    public EvidenceQualityScoreDtos.EvidenceQualityScoreResponseDto recalculatePartial(
            @PathVariable @NotBlank String ticketId,
            @Valid @RequestBody EvidenceQualityScoreDtos.EvidenceQualityScoreCalculationRequestDto request) {
        return EvidenceQualityScoreDtos.EvidenceQualityScoreResponseDto.from(
                service.recalculateFromParser(UUID.fromString(ticketId), normalizeRuleVersion(request.scoreRuleVersion()), request.requestedBy()));
    }

    @PostMapping("/tickets/{ticketId}/finalize")
    public EvidenceQualityScoreDtos.EvidenceQualityScoreResponseDto recalculateFinal(
            @PathVariable @NotBlank String ticketId,
            @Valid @RequestBody EvidenceQualityScoreDtos.EvidenceQualityScoreCalculationRequestDto request) {
        return EvidenceQualityScoreDtos.EvidenceQualityScoreResponseDto.from(
                service.recalculateFromCi(UUID.fromString(ticketId), normalizeRuleVersion(request.scoreRuleVersion()), request.requestedBy()));
    }

    private EvidenceQualityScoreDtos.EvidenceQualityScoreResponseDto recalculateFinal(
            EvidenceQualityScoreDtos.EvidenceQualityScoreCalculationRequestDto request) {
        String traceId = traceId();
        var scoreRequest = request.toRequest(traceId);
        UUID ticketId = scoreRequest.ticketId();
        if (ticketId == null) {
            if (scoreRequest.ticketIds() == null || scoreRequest.ticketIds().isEmpty()) {
                throw new IllegalArgumentException("invalid_ticket_id");
            }
            ticketId = scoreRequest.ticketIds().getFirst();
        }
        return EvidenceQualityScoreDtos.EvidenceQualityScoreResponseDto.from(
                service.recalculateFromCi(ticketId, normalizeRuleVersion(scoreRequest.scoreRuleVersion()), scoreRequest.requestedBy()));
    }

    private String traceId() {
        String traceId = MDC.get("traceId");
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }

    private String normalizeRuleVersion(String scoreRuleVersion) {
        return scoreRuleVersion == null || scoreRuleVersion.isBlank() ? "v0" : scoreRuleVersion;
    }
}
