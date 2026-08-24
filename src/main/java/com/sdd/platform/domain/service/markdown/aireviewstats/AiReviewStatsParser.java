package com.sdd.platform.domain.service.markdown.aireviewstats;

import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownDocument;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownSection;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownTable;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the 5 KPI numerator/denominator pairs from the "## 8. ..." stats
 * table of an ai-review.md, independent of the section's translated heading
 * text (VI/EN/JA) and row label wording — locates the table purely by heading
 * number "8." and fixed row position (index 2-6), per spec-pack.md §7 (A-AIRKI-1)
 * and AC-AIRKI-2/3. {@link MarkdownParserCore}'s canonicalSectionKey lookup is a
 * hardcoded label-text switch with no entry for this section, so it cannot be
 * used to locate section 8 — this class re-locates the heading independently
 * and reuses only the core's already-extracted table for that section.
 */
public class AiReviewStatsParser {

    private static final Pattern HEADING_8_PATTERN = Pattern.compile("^#{1,6}\\s+8\\.");
    private static final Pattern NUMERATOR_DENOMINATOR_PATTERN = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");
    private static final int BLOCKER_MAJOR_ROW = 2;
    private static final int ADOPTION_ROW = 3;
    private static final int VALID_ROW = 4;
    private static final int FALSE_POSITIVE_ROW = 5;
    private static final int RESOLVED_ROW = 6;
    private static final int MIN_ROW_COUNT = 7;
    private static final int VALUE_COLUMN_INDEX = 1;

    private final MarkdownParserCore core;

    public AiReviewStatsParser() {
        this(new MarkdownParserCore());
    }

    protected AiReviewStatsParser(MarkdownParserCore core) {
        this.core = core;
    }

    public Optional<AiReviewStats> parse(String content) {
        MarkdownDocument document = core.parse(content != null ? content : "");
        MarkdownTable table = locateStatsTable(document);
        if (table == null || table.rows().size() < MIN_ROW_COUNT) {
            return Optional.empty();
        }

        List<List<String>> rows = table.rows();
        NumeratorDenominator blockerMajor = extractCell(rows.get(BLOCKER_MAJOR_ROW));
        NumeratorDenominator adoption = extractCell(rows.get(ADOPTION_ROW));
        NumeratorDenominator valid = extractCell(rows.get(VALID_ROW));
        NumeratorDenominator falsePositive = extractCell(rows.get(FALSE_POSITIVE_ROW));
        NumeratorDenominator resolved = extractCell(rows.get(RESOLVED_ROW));

        Integer sharedFindingTotal = resolveSharedFindingTotal(
                adoption.denominator(), valid.denominator(), falsePositive.denominator(), resolved.denominator());
        boolean sharedGroupValid = sharedFindingTotal != null;

        return Optional.of(new AiReviewStats(
                blockerMajor.numerator(),
                blockerMajor.denominator(),
                sharedGroupValid ? adoption.numerator() : null,
                sharedFindingTotal,
                sharedGroupValid ? valid.numerator() : null,
                sharedGroupValid ? falsePositive.numerator() : null,
                sharedGroupValid ? resolved.numerator() : null));
    }

    private MarkdownTable locateStatsTable(MarkdownDocument document) {
        int headingLineNumber = findHeading8LineNumber(document.normalizedContent());
        if (headingLineNumber == -1) {
            return null;
        }

        MarkdownSection section = document.sections().stream()
                .filter(s -> s.startLine() == headingLineNumber)
                .findFirst()
                .orElse(null);
        if (section == null) {
            return null;
        }

        return document.tables().stream()
                .filter(t -> t.sectionKey().equals(section.canonicalKey()) && t.sectionTitle().equals(section.title()))
                .findFirst()
                .orElse(null);
    }

    private int findHeading8LineNumber(String normalizedContent) {
        String[] lines = normalizedContent.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            if (HEADING_8_PATTERN.matcher(lines[i]).find()) {
                return i + 1;
            }
        }
        return -1;
    }

    private NumeratorDenominator extractCell(List<String> row) {
        if (row == null || row.size() <= VALUE_COLUMN_INDEX) {
            return new NumeratorDenominator(null, null);
        }
        Matcher matcher = NUMERATOR_DENOMINATOR_PATTERN.matcher(row.get(VALUE_COLUMN_INDEX));
        if (!matcher.find()) {
            return new NumeratorDenominator(null, null);
        }
        return new NumeratorDenominator(Integer.valueOf(matcher.group(1)), Integer.valueOf(matcher.group(2)));
    }

    /**
     * The 4 KPIs (adoption/valid/false-positive/resolution) share one denominator
     * column (A-AIRKI-3), so a row that individually parses as "không áp dụng" cannot
     * be told apart from a malformed one once only its denominator is inspected. Per
     * AC-AIRKI-4, if the 4 rows disagree on whether/what the shared total is, none of
     * them can be trusted — return null so the whole group persists as not-applicable
     * rather than any one KPI silently reporting 0/0%.
     */
    private Integer resolveSharedFindingTotal(Integer... denominators) {
        Integer total = null;
        for (Integer denominator : denominators) {
            if (denominator == null) {
                return null;
            }
            if (total == null) {
                total = denominator;
            } else if (!total.equals(denominator)) {
                return null;
            }
        }
        return total;
    }

    private record NumeratorDenominator(Integer numerator, Integer denominator) {
    }

    public record AiReviewStats(
            Integer blockerMajorResolvedCount,
            Integer blockerMajorTotalCount,
            Integer aiReviewAdoptedCount,
            Integer aiReviewFindingTotalCount,
            Integer aiReviewValidCount,
            Integer aiReviewFalsePositiveCount,
            Integer aiReviewResolvedCount) {
    }
}
