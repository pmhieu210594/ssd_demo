package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ScoreCriterion;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ScoreRequest;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ScoreResult;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class EvidenceQualityScoreDtos {

    private EvidenceQualityScoreDtos() {
    }

    public record EvidenceQualityScoreCalculationRequestDto(
            String ticketId,
            List<String> ticketIds,
            boolean forceRecalculate,
            String scoreRuleVersion,
            String requestedBy
    ) {
        public ScoreRequest toRequest(String traceId) {
            List<UUID> parsedTicketIds = ticketIds == null ? List.of() : ticketIds.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> UUID.fromString(value.trim()))
                    .toList();
            UUID parsedTicketId = ticketId == null || ticketId.isBlank() ? null : UUID.fromString(ticketId.trim());
            return new ScoreRequest(parsedTicketId, parsedTicketIds, forceRecalculate, scoreRuleVersion, requestedBy, traceId);
        }
    }

    public record EvidenceQualityScoreBreakdownItemDto(
            String criterionId,
            String label,
            BigDecimal score,
            BigDecimal maxScore,
            String status,
            List<String> sourceRefs
    ) {
        public static EvidenceQualityScoreBreakdownItemDto from(ScoreCriterion criterion) {
            return new EvidenceQualityScoreBreakdownItemDto(
                    criterion.criterionId(),
                    criterion.label(),
                    criterion.score(),
                    criterion.maxScore(),
                    criterion.status(),
                    criterion.sourceRefs()
            );
        }
    }

    public record EvidenceQualityScoreResponseDto(
            UUID ticketId,
            BigDecimal score,
            String band,
            List<EvidenceQualityScoreBreakdownItemDto> breakdown,
            List<String> missing,
            List<String> parseErrors,
            List<String> traceIds,
            String scoreRuleVersion,
            String snapshotState,
            OffsetDateTime calculatedAt
    ) {
        public static EvidenceQualityScoreResponseDto from(ScoreResult result) {
            return new EvidenceQualityScoreResponseDto(
                    result.ticketId(),
                    result.score(),
                    result.band(),
                    result.breakdown().stream().map(EvidenceQualityScoreBreakdownItemDto::from).toList(),
                    result.missing(),
                    result.parseErrors(),
                    result.traceIds(),
                    result.scoreRuleVersion(),
                    result.snapshotState(),
                    result.calculatedAt()
            );
        }
    }

    public record EvidenceQualityScoreResultEnvelopeDto(
            EvidenceQualityScoreResponseDto result
    ) {
    }
}
