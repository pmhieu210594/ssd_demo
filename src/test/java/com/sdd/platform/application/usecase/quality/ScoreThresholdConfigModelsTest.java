package com.sdd.platform.application.usecase.quality;

import com.sdd.platform.application.usecase.quality.ScoreThresholdConfigModels.UpsertScoreThreshold;
import com.sdd.platform.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static com.sdd.platform.application.usecase.quality.ScoreThresholdConfigModels.UpsertScoreThreshold;
import static com.sdd.platform.application.usecase.quality.ScoreThresholdConfigModels.lookupBand;
import static com.sdd.platform.application.usecase.quality.ScoreThresholdConfigModels.validateActiveSet;
import static com.sdd.platform.application.usecase.quality.ScoreThresholdConfigModels.validateRow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScoreThresholdConfigModelsTest {

    private static UpsertScoreThreshold row(String code, int min, int max) {
        OffsetDateTime now = OffsetDateTime.now();
        return new UpsertScoreThreshold(null, code, code + " label", min, max, "#10B981", now, "SYSTEM", now, "SYSTEM");
    }

    @Test
    void validateRow_acceptsWellFormedRow() {
        validateRow(row("EXCELLENT", 90, 100));
    }

    @Test
    void validateRow_rejectsBlankLabel() {
        OffsetDateTime now = OffsetDateTime.now();
        UpsertScoreThreshold bad = new UpsertScoreThreshold(null, "EXCELLENT", "  ", 90, 100, "#10B981", now, "SYSTEM", now, "SYSTEM");
        assertThrows(BusinessRuleException.class, () -> validateRow(bad));
    }

    @Test
    void validateRow_rejectsLowercaseCode() {
        OffsetDateTime now = OffsetDateTime.now();
        UpsertScoreThreshold bad = new UpsertScoreThreshold(null, "excellent", "Excellent", 90, 100, "#10B981", now, "SYSTEM", now, "SYSTEM");
        assertThrows(BusinessRuleException.class, () -> validateRow(bad));
    }

    @Test
    void validateRow_rejectsCodeWithSpacesOrSpecialChars() {
        OffsetDateTime now = OffsetDateTime.now();
        UpsertScoreThreshold bad = new UpsertScoreThreshold(null, "EXCELLENT SCORE!", "Excellent", 90, 100, "#10B981", now, "SYSTEM", now, "SYSTEM");
        assertThrows(BusinessRuleException.class, () -> validateRow(bad));
    }

    @Test
    void validateRow_rejectsInvalidColor() {
        OffsetDateTime now = OffsetDateTime.now();
        UpsertScoreThreshold bad = new UpsertScoreThreshold(null, "EXCELLENT", "Excellent", 90, 100, "not-a-color", now, "SYSTEM", now, "SYSTEM");
        assertThrows(BusinessRuleException.class, () -> validateRow(bad));
    }

    @Test
    void validateRow_rejectsMinGreaterThanMax() {
        UpsertScoreThreshold bad = row("EXCELLENT", 80, 60);
        assertThrows(BusinessRuleException.class, () -> validateRow(bad));
    }

    @Test
    void validateRow_rejectsOutOfBoundsRange() {
        assertThrows(BusinessRuleException.class, () -> validateRow(row("EXCELLENT", -1, 100)));
        assertThrows(BusinessRuleException.class, () -> validateRow(row("EXCELLENT", 0, 101)));
    }

    @Test
    void validateActiveSet_acceptsFullCoverageNoGapsNoOverlaps() {
        List<UpsertScoreThreshold> rows = List.of(
                row("CRITICAL", 0, 39),
                row("RISKY", 40, 59),
                row("WARNING", 60, 74),
                row("GOOD", 75, 89),
                row("EXCELLENT", 90, 100));
        validateActiveSet(rows);
    }

    @Test
    void validateActiveSet_acceptsSingleBandCoveringFullRange() {
        validateActiveSet(List.of(row("ALL", 0, 100)));
    }

    @Test
    void validateActiveSet_rejectsGap() {
        List<UpsertScoreThreshold> rows = List.of(row("A", 0, 39), row("B", 45, 100));
        assertThrows(BusinessRuleException.class, () -> validateActiveSet(rows));
    }

    @Test
    void validateActiveSet_rejectsOverlap() {
        List<UpsertScoreThreshold> rows = List.of(row("A", 0, 40), row("B", 40, 100));
        assertThrows(BusinessRuleException.class, () -> validateActiveSet(rows));
    }

    @Test
    void validateActiveSet_acceptsAdjacentBandsTouchingAtBoundary() {
        List<UpsertScoreThreshold> rows = List.of(row("A", 0, 39), row("B", 40, 59), row("C", 60, 100));
        validateActiveSet(rows);
    }

    @Test
    void validateActiveSet_rejectsDuplicateCodeWithinPayload() {
        List<UpsertScoreThreshold> rows = List.of(row("WARNING", 0, 50), row("WARNING", 51, 100));
        assertThrows(BusinessRuleException.class, () -> validateActiveSet(rows));
    }

    @Test
    void validateActiveSet_rejectsEmptyActiveSet() {
        assertThrows(BusinessRuleException.class, () -> validateActiveSet(List.of()));
    }

    @Test
    void validateActiveSet_rejectsMissingCoverageAtLowOrHighEnd() {
        assertThrows(BusinessRuleException.class,
                () -> validateActiveSet(List.of(row("A", 1, 100))));
        assertThrows(BusinessRuleException.class,
                () -> validateActiveSet(List.of(row("A", 0, 99))));
    }

    @Test
    void lookupBand_returnsExactlyOneMatchingBand() {
        OffsetDateTime now = OffsetDateTime.now();
        List<ScoreThresholdConfigModels.ScoreThreshold> active = List.of(
                new ScoreThresholdConfigModels.ScoreThreshold(null, "CRITICAL", "Critical", 0, 39, "#F43F5E", now, "SYSTEM", now, "SYSTEM"),
                new ScoreThresholdConfigModels.ScoreThreshold(null, "RISKY", "Risky", 40, 59, "#F97316", now, "SYSTEM", now, "SYSTEM"),
                new ScoreThresholdConfigModels.ScoreThreshold(null, "WARNING", "Warning", 60, 74, "#F59E0B", now, "SYSTEM", now, "SYSTEM"),
                new ScoreThresholdConfigModels.ScoreThreshold(null, "GOOD", "Good", 75, 89, "#0EA5E9", now, "SYSTEM", now, "SYSTEM"),
                new ScoreThresholdConfigModels.ScoreThreshold(null, "EXCELLENT", "Excellent", 90, 100, "#10B981", now, "SYSTEM", now, "SYSTEM"));

        assertEquals("CRITICAL", lookupBand(active, 0).code());
        assertEquals("CRITICAL", lookupBand(active, 39).code());
        assertEquals("RISKY", lookupBand(active, 40).code());
        assertEquals("RISKY", lookupBand(active, 59).code());
        assertEquals("WARNING", lookupBand(active, 60).code());
        assertEquals("WARNING", lookupBand(active, 74).code());
        assertEquals("GOOD", lookupBand(active, 75).code());
        assertEquals("GOOD", lookupBand(active, 89).code());
        assertEquals("EXCELLENT", lookupBand(active, 90).code());
        assertEquals("EXCELLENT", lookupBand(active, 100).code());
    }
}
