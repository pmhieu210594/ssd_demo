package com.sdd.platform.application.usecase.quality;

import com.sdd.platform.domain.exception.BusinessRuleException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class ScoreThresholdConfigModels {

    private ScoreThresholdConfigModels() {
    }

    public static final int MIN_SCORE_BOUND = 0;
    public static final int MAX_SCORE_BOUND = 100;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9_]+$");
    private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    public record ScoreThreshold(
            UUID id,
            String code,
            String label,
            int minScore,
            int maxScore,
            String color,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy) {
    }

    public record UpsertScoreThreshold(
            UUID id,
            String code,
            String label,
            int minScore,
            int maxScore,
            String color,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy) {
    }

    /**
     * Validates one row's own fields (BR-THRESHOLD-CONFIG-006/007/008).
     * Coverage/overlap/duplicate checks are cross-row and handled by {@link #validateActiveSet}.
     */
    public static void validateRow(UpsertScoreThreshold row) {
        if (row.label() == null || row.label().trim().isEmpty()) {
            throw new BusinessRuleException("Pages.ThresholdConfig.Label.Required");
        }
        String code = row.code() == null ? null : row.code().trim();
        if (code == null || code.isEmpty()) {
            throw new BusinessRuleException("Pages.ThresholdConfig.Code.Required");
        }
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new BusinessRuleException("Pages.ThresholdConfig.Code.InvalidFormat");
        }
        if (row.color() == null || !COLOR_PATTERN.matcher(row.color().trim()).matches()) {
            throw new BusinessRuleException("Pages.ThresholdConfig.Color.InvalidFormat");
        }
        if (row.minScore() < MIN_SCORE_BOUND || row.maxScore() > MAX_SCORE_BOUND) {
            throw new BusinessRuleException("Pages.ThresholdConfig.Range.OutOfBounds");
        }
        if (row.minScore() > row.maxScore()) {
            throw new BusinessRuleException("Pages.ThresholdConfig.Range.MinGreaterThanMax");
        }
    }

    /**
     * Validates the resulting active set as a whole: duplicate codes, full 0-100
     * coverage with no gaps/overlaps (BR-THRESHOLD-CONFIG-005/008/009). At least one
     * active band is required after save (this ticket's OI-2/3 resolution).
     */
    public static void validateActiveSet(List<UpsertScoreThreshold> resultingActiveRows) {
        if (resultingActiveRows.isEmpty()) {
            throw new BusinessRuleException("Pages.ThresholdConfig.ActiveSet.Empty");
        }

        Set<String> seenCodes = new HashSet<>();
        for (UpsertScoreThreshold row : resultingActiveRows) {
            String code = row.code().trim().toUpperCase(java.util.Locale.ROOT);
            if (!seenCodes.add(code)) {
                throw new BusinessRuleException("Pages.ThresholdConfig.Code.Duplicate");
            }
        }

        List<UpsertScoreThreshold> ordered = new ArrayList<>(resultingActiveRows);
        ordered.sort(Comparator.comparingInt(UpsertScoreThreshold::minScore));

        UpsertScoreThreshold first = ordered.get(0);
        if (first.minScore() != MIN_SCORE_BOUND) {
            throw new BusinessRuleException("Pages.ThresholdConfig.Coverage.Gap");
        }
        UpsertScoreThreshold last = ordered.get(ordered.size() - 1);
        if (last.maxScore() != MAX_SCORE_BOUND) {
            throw new BusinessRuleException("Pages.ThresholdConfig.Coverage.Gap");
        }

        for (int i = 1; i < ordered.size(); i++) {
            int previousMax = ordered.get(i - 1).maxScore();
            int currentMin = ordered.get(i).minScore();
            if (currentMin <= previousMax) {
                throw new BusinessRuleException("Pages.ThresholdConfig.Coverage.Overlap");
            }
            if (currentMin > previousMax + 1) {
                throw new BusinessRuleException("Pages.ThresholdConfig.Coverage.Gap");
            }
        }
    }

    public static ScoreThreshold lookupBand(List<ScoreThreshold> activeOrderedByMinScore, int score) {
        for (ScoreThreshold band : activeOrderedByMinScore) {
            if (score >= band.minScore() && score <= band.maxScore()) {
                return band;
            }
        }
        // Defensive fallback per BR-THRESHOLD-CONFIG-014: coverage gaps are rejected at
        // save time, but if one is ever found at lookup time, clamp to the nearest band
        // instead of throwing or silently misclassifying.
        if (activeOrderedByMinScore.isEmpty()) {
            throw new BusinessRuleException("Pages.ThresholdConfig.ActiveSet.Empty");
        }
        ScoreThreshold nearest = activeOrderedByMinScore.get(0);
        for (ScoreThreshold band : activeOrderedByMinScore) {
            if (score > band.maxScore()) {
                nearest = band;
            }
        }
        return nearest;
    }
}
