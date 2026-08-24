package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ScoreResult;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.SourceSnapshot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvidenceQualityScoreRepositoryPort {

    SourceSnapshot loadSourceSnapshot(UUID ticketId);

    Optional<ScoreResult> findLatest(UUID ticketId);

    List<ScoreResult> findHistory(UUID ticketId, int limit);

    ScoreResult save(ScoreResult result, String requestedBy);
}
