package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.application.usecase.quality.ScoreThresholdConfigModels.ScoreThreshold;
import com.sdd.platform.application.usecase.quality.ScoreThresholdConfigModels.UpsertScoreThreshold;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ScoreThresholdConfigRepositoryPort {

    List<ScoreThreshold> findActiveOrderedByMinScore();

    ScoreThreshold insert(UpsertScoreThreshold row, String actor, OffsetDateTime updatedAt);

    int update(UpsertScoreThreshold row, String actor, OffsetDateTime updatedAt);

    int[] updateBatch(List<UpsertScoreThreshold> row, String actor, OffsetDateTime updatedAt);

    int softDelete(UUID id, String actor, OffsetDateTime updatedAt);
}
