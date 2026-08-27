package com.sdd.platform.application.usecase.quality;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ScoreBand.CRITICAL;
import static com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ScoreBand.EXCELLENT;
import static com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ScoreBand.GOOD;
import static com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ScoreBand.RISKY;
import static com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ScoreBand.WARNING;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EvidenceQualityScoreModelsTest {

    @Test
    void score_band_thresholds_match_spec() {
        assertEquals(CRITICAL, EvidenceQualityScoreModels.ScoreBand.fromScore(BigDecimal.ZERO));
        assertEquals(CRITICAL, EvidenceQualityScoreModels.ScoreBand.fromScore(BigDecimal.valueOf(39)));
        assertEquals(RISKY, EvidenceQualityScoreModels.ScoreBand.fromScore(BigDecimal.valueOf(40)));
        assertEquals(RISKY, EvidenceQualityScoreModels.ScoreBand.fromScore(BigDecimal.valueOf(59)));
        assertEquals(WARNING, EvidenceQualityScoreModels.ScoreBand.fromScore(BigDecimal.valueOf(60)));
        assertEquals(WARNING, EvidenceQualityScoreModels.ScoreBand.fromScore(BigDecimal.valueOf(74)));
        assertEquals(GOOD, EvidenceQualityScoreModels.ScoreBand.fromScore(BigDecimal.valueOf(75)));
        assertEquals(GOOD, EvidenceQualityScoreModels.ScoreBand.fromScore(BigDecimal.valueOf(89)));
        assertEquals(EXCELLENT, EvidenceQualityScoreModels.ScoreBand.fromScore(BigDecimal.valueOf(90)));
        assertEquals(EXCELLENT, EvidenceQualityScoreModels.ScoreBand.fromScore(BigDecimal.valueOf(100)));
    }
}
