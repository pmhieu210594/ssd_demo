package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.quality.ScoreThresholdConfigModels.ScoreThreshold;
import com.sdd.platform.application.usecase.quality.ScoreThresholdConfigModels.UpsertScoreThreshold;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ScoreThresholdConfigDtos {

    private ScoreThresholdConfigDtos() {
    }

    public record ScoreThresholdDto(
            UUID id,
            String code,
            String label,
            int minScore,
            int maxScore,
            String color,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy
    ) {
        public static ScoreThresholdDto from(ScoreThreshold model) {
            return new ScoreThresholdDto(
                    model.id(),
                    model.code(),
                    model.label(),
                    model.minScore(),
                    model.maxScore(),
                    model.color(),
                    model.createdAt(),
                    model.createdBy(),
                    model.updatedAt(),
                    model.updatedBy()
                );
        }
    }

    public record UpsertScoreThresholdDto(
            UUID id,
            String code,
            String label,
            Integer minScore,
            Integer maxScore,
            String color,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy
    ) {
        public UpsertScoreThreshold toModel() {
            return new UpsertScoreThreshold(
                    id,
                    code,
                    label,
                    minScore == null ? 0 : minScore,
                    maxScore == null ? 0 : maxScore,
                    color,
                    createdAt,
                    createdBy,
                    updatedAt,
                    updatedBy);
        }
    }

    public record SaveScoreThresholdsRequest(List<UpsertScoreThresholdDto> thresholds) {
    }
}
