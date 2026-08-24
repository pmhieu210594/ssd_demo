package com.sdd.platform.domain.service;

import com.sdd.platform.domain.service.markdown.aireviewstats.AiReviewStatsParser;
import com.sdd.platform.domain.service.markdown.aireviewstats.AiReviewStatsParser.AiReviewStats;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AiReviewStatsParserTest {

        private final AiReviewStatsParser parser = new AiReviewStatsParser();

        // ── AC-AIRKI-1/2: template-exact labels, VI heading "## 8." ───────────────

        @Test
        void parse_templateExactLabels_extractsPositionalPairs() {
                String content = """
                                ## 8. Số liệu thống kê

                                | Chỉ số | Giá trị | Ghi chú |
                                | --- | --- | --- |
                                | Tổng số finding | 5 | ⬜ |
                                | Tổng số finding đã fix | 5 / 5 | ⬜ |
                                | Tỷ lệ xử lý finding nghiêm trọng (Blocker) | 3 / 4 (75%) | ⬜ |
                                | Tỷ lệ AI finding được con người chấp nhận | 4 / 5 (80%) | ⬜ |
                                | Tỷ lệ AI review finding hữu ích | 4 / 5 (80%) | ⬜ |
                                | Tỷ lệ AI finding bị đánh giá false positive | 1 / 5 (20%) | ⬜ |
                                | Tỷ lệ AI finding đã được xử lý | 5 / 5 (100%) | ⬜ |
                                """;

                Optional<AiReviewStats> parsed = parser.parse(content);

                assertThat(parsed).isPresent();
                AiReviewStats stats = parsed.get();
                assertThat(stats.blockerMajorResolvedCount()).isEqualTo(3);
                assertThat(stats.blockerMajorTotalCount()).isEqualTo(4);
                assertThat(stats.aiReviewAdoptedCount()).isEqualTo(4);
                assertThat(stats.aiReviewFindingTotalCount()).isEqualTo(5);
                assertThat(stats.aiReviewValidCount()).isEqualTo(4);
                assertThat(stats.aiReviewFalsePositiveCount()).isEqualTo(1);
                assertThat(stats.aiReviewResolvedCount()).isEqualTo(5);
        }

        // ── AC-AIRKI-3: real-world reworded labels — fixture is a byte-for-byte copy
        //    of an actual `ai-review.md` produced by this repo's own AI-review flow
        //    (`raw/ai-review.md`, ticket PROMPT_TEMPLATE_REUSE_RATE), not a hand-typed
        //    approximation. A hand-typed literal cannot drift from what production
        //    files actually look like (markdown links, backticks, anchor tags, long
        //    prose "Ghi chú" cells, other tables earlier in the same document under
        //    headings "## 3."/"## 5."/"## 7." that must NOT be mistaken for "## 8.");
        //    this test fails if positional/heading-number lookup ever regresses to
        //    something that only worked on clean synthetic input.
        //    Also covers AC-AIRKI-4: not-applicable value ("không áp dụng") → NULL pair.

        @Test
        void parse_realAiReviewMdFixture_mapsByPositionNotLabelTextAndIgnoresOtherTables() throws Exception {
                Path fixture = Path.of(AiReviewStatsParserTest.class.getClassLoader()
                                .getResource("test-fixtures/PARSER-AI-REVIEW-STATS/ai-review-real-world.md")
                                .toURI());
                String content = Files.readString(fixture);

                Optional<AiReviewStats> parsed = parser.parse(content);

                assertThat(parsed).isPresent();
                AiReviewStats stats = parsed.get();
                assertThat(stats.blockerMajorResolvedCount()).isNull();
                assertThat(stats.blockerMajorTotalCount()).isNull();
                assertThat(stats.aiReviewAdoptedCount()).isEqualTo(2);
                assertThat(stats.aiReviewFindingTotalCount()).isEqualTo(2);
                assertThat(stats.aiReviewValidCount()).isEqualTo(2);
                assertThat(stats.aiReviewFalsePositiveCount()).isEqualTo(0);
                assertThat(stats.aiReviewResolvedCount()).isEqualTo(2);
        }

        // ── AC-AIRKI-2: language-independent — EN heading/labels, numbering kept ──

        @Test
        void parse_englishHeadingAndLabels_locatesByHeadingNumberNotTranslatedText() {
                String content = """
                                ## 8. Statistics

                                | Metric | Value | Note |
                                | --- | --- | --- |
                                | Total findings | 6 | |
                                | Total findings fixed | 6 / 6 | |
                                | Blocker/Major resolution rate | 5 / 6 (83%) | |
                                | AI finding adoption rate | 6 / 6 (100%) | |
                                | AI review valid finding rate | 5 / 6 (83%) | |
                                | AI false positive rate | 1 / 6 (17%) | |
                                | AI finding resolution rate | 6 / 6 (100%) | |
                                """;

                Optional<AiReviewStats> parsed = parser.parse(content);

                assertThat(parsed).isPresent();
                AiReviewStats stats = parsed.get();
                assertThat(stats.blockerMajorResolvedCount()).isEqualTo(5);
                assertThat(stats.blockerMajorTotalCount()).isEqualTo(6);
                assertThat(stats.aiReviewAdoptedCount()).isEqualTo(6);
                assertThat(stats.aiReviewFindingTotalCount()).isEqualTo(6);
        }

        // ── AC-AIRKI-4: shared-denominator KPIs (adoption/valid/false-positive/
        //    resolution) must agree on `ai_review_finding_total_count`. If any one
        //    of the 4 is "không áp dụng" while siblings are numeric, the shared total
        //    cannot be safely attributed to any of them — the whole group must be
        //    NULL, never a partially-derived 0/0% for the sibling rows. ────────────

        @Test
        void parse_adoptionRowNotApplicableWhileSiblingsNumeric_nullsWholeSharedGroup() {
                String content = """
                                ## 8. Số liệu thống kê

                                | Chỉ số | Giá trị | Ghi chú |
                                | --- | --- | --- |
                                | Tổng số finding | 2 | ⬜ |
                                | Tổng số finding đã fix | 2 / 2 | ⬜ |
                                | Tỷ lệ xử lý finding nghiêm trọng (Blocker) | 2 / 2 (100%) | ⬜ |
                                | Tỷ lệ AI finding được con người chấp nhận | không áp dụng | Không có finding trong pass này |
                                | Tỷ lệ AI review finding hữu ích | 2 / 2 (100%) | ⬜ |
                                | Tỷ lệ AI finding bị đánh giá false positive | 0 / 2 (0%) | ⬜ |
                                | Tỷ lệ AI finding đã được xử lý | 2 / 2 (100%) | ⬜ |
                                """;

                Optional<AiReviewStats> parsed = parser.parse(content);

                assertThat(parsed).isPresent();
                AiReviewStats stats = parsed.get();
                assertThat(stats.blockerMajorResolvedCount()).isEqualTo(2);
                assertThat(stats.blockerMajorTotalCount()).isEqualTo(2);
                assertThat(stats.aiReviewAdoptedCount()).isNull();
                assertThat(stats.aiReviewFindingTotalCount()).isNull();
                assertThat(stats.aiReviewValidCount()).isNull();
                assertThat(stats.aiReviewFalsePositiveCount()).isNull();
                assertThat(stats.aiReviewResolvedCount()).isNull();
        }

        @Test
        void parse_validRowNotApplicableWhileSiblingsNumeric_nullsWholeSharedGroup() {
                String content = """
                                ## 8. Số liệu thống kê

                                | Chỉ số | Giá trị | Ghi chú |
                                | --- | --- | --- |
                                | Tổng số finding | 2 | ⬜ |
                                | Tổng số finding đã fix | 2 / 2 | ⬜ |
                                | Tỷ lệ xử lý finding nghiêm trọng (Blocker) | 2 / 2 (100%) | ⬜ |
                                | Tỷ lệ AI finding được con người chấp nhận | 2 / 2 (100%) | ⬜ |
                                | Tỷ lệ AI review finding hữu ích | không áp dụng | Không có finding trong pass này |
                                | Tỷ lệ AI finding bị đánh giá false positive | 0 / 2 (0%) | ⬜ |
                                | Tỷ lệ AI finding đã được xử lý | 2 / 2 (100%) | ⬜ |
                                """;

                Optional<AiReviewStats> parsed = parser.parse(content);

                assertThat(parsed).isPresent();
                AiReviewStats stats = parsed.get();
                assertThat(stats.aiReviewAdoptedCount()).isNull();
                assertThat(stats.aiReviewFindingTotalCount()).isNull();
                assertThat(stats.aiReviewValidCount()).isNull();
                assertThat(stats.aiReviewFalsePositiveCount()).isNull();
                assertThat(stats.aiReviewResolvedCount()).isNull();
        }

        @Test
        void parse_falsePositiveRowNotApplicableWhileSiblingsNumeric_nullsWholeSharedGroup() {
                String content = """
                                ## 8. Số liệu thống kê

                                | Chỉ số | Giá trị | Ghi chú |
                                | --- | --- | --- |
                                | Tổng số finding | 2 | ⬜ |
                                | Tổng số finding đã fix | 2 / 2 | ⬜ |
                                | Tỷ lệ xử lý finding nghiêm trọng (Blocker) | 2 / 2 (100%) | ⬜ |
                                | Tỷ lệ AI finding được con người chấp nhận | 2 / 2 (100%) | ⬜ |
                                | Tỷ lệ AI review finding hữu ích | 2 / 2 (100%) | ⬜ |
                                | Tỷ lệ AI finding bị đánh giá false positive | không áp dụng | Không có finding trong pass này |
                                | Tỷ lệ AI finding đã được xử lý | 2 / 2 (100%) | ⬜ |
                                """;

                Optional<AiReviewStats> parsed = parser.parse(content);

                assertThat(parsed).isPresent();
                AiReviewStats stats = parsed.get();
                assertThat(stats.aiReviewAdoptedCount()).isNull();
                assertThat(stats.aiReviewFindingTotalCount()).isNull();
                assertThat(stats.aiReviewValidCount()).isNull();
                assertThat(stats.aiReviewFalsePositiveCount()).isNull();
                assertThat(stats.aiReviewResolvedCount()).isNull();
        }

        @Test
        void parse_resolvedRowNotApplicableWhileSiblingsNumeric_nullsWholeSharedGroup() {
                String content = """
                                ## 8. Số liệu thống kê

                                | Chỉ số | Giá trị | Ghi chú |
                                | --- | --- | --- |
                                | Tổng số finding | 2 | ⬜ |
                                | Tổng số finding đã fix | 2 / 2 | ⬜ |
                                | Tỷ lệ xử lý finding nghiêm trọng (Blocker) | 2 / 2 (100%) | ⬜ |
                                | Tỷ lệ AI finding được con người chấp nhận | 2 / 2 (100%) | ⬜ |
                                | Tỷ lệ AI review finding hữu ích | 2 / 2 (100%) | ⬜ |
                                | Tỷ lệ AI finding bị đánh giá false positive | 0 / 2 (0%) | ⬜ |
                                | Tỷ lệ AI finding đã được xử lý | không áp dụng | Không có finding trong pass này |
                                """;

                Optional<AiReviewStats> parsed = parser.parse(content);

                assertThat(parsed).isPresent();
                AiReviewStats stats = parsed.get();
                assertThat(stats.aiReviewAdoptedCount()).isNull();
                assertThat(stats.aiReviewFindingTotalCount()).isNull();
                assertThat(stats.aiReviewValidCount()).isNull();
                assertThat(stats.aiReviewFalsePositiveCount()).isNull();
                assertThat(stats.aiReviewResolvedCount()).isNull();
        }

        // ── AC-AIRKI-4 (extension): the "invalid number pattern" fallback must be a
        //    generic regex-mismatch, not a special case keyed on the literal string
        //    "không áp dụng" — any value that fails numerator/denominator extraction
        //    (e.g. full-width Unicode digits from a pasted CJK/JP locale table, or a
        //    value that mixes half- and full-width digits) must fall back to NULL,
        //    never silently truncate/misparse into a wrong-but-plausible number. ────

        @Test
        void parse_fullWidthDigitsInBlockerRow_treatedAsNotApplicableNotThrowOrMisparse() {
                String content = """
                                ## 8. Số liệu thống kê

                                | Chỉ số | Giá trị | Ghi chú |
                                | --- | --- | --- |
                                | Tổng số finding | 2 | ⬜ |
                                | Tổng số finding đã fix | 2 / 2 | ⬜ |
                                | Tỷ lệ xử lý finding nghiêm trọng (Blocker) | ５／１０ | ⬜ |
                                | Tỷ lệ AI finding được con người chấp nhận | 2 / 2 (100%) | ⬜ |
                                | Tỷ lệ AI review finding hữu ích | 2 / 2 (100%) | ⬜ |
                                | Tỷ lệ AI finding bị đánh giá false positive | 0 / 2 (0%) | ⬜ |
                                | Tỷ lệ AI finding đã được xử lý | 2 / 2 (100%) | ⬜ |
                                """;

                Optional<AiReviewStats> parsed = parser.parse(content);

                assertThat(parsed).isPresent();
                AiReviewStats stats = parsed.get();
                assertThat(stats.blockerMajorResolvedCount())
                                .as("full-width digits/slash never match the ASCII-only numerator/denominator "
                                                + "pattern; extraction must fall back to null, not silently read "
                                                + "the row as 0 or throw")
                                .isNull();
                assertThat(stats.blockerMajorTotalCount()).isNull();
                assertThat(stats.aiReviewAdoptedCount())
                                .as("an unrelated row (Blocker) failing to parse must not affect the other, "
                                                + "independently-parsed shared-denominator group")
                                .isEqualTo(2);
                assertThat(stats.aiReviewFindingTotalCount()).isEqualTo(2);
        }

        @Test
        void parse_mixedFullAndHalfWidthDigitsInAdoptionRow_nullsWholeSharedGroupNotPartialParse() {
                String content = """
                                ## 8. Số liệu thống kê

                                | Chỉ số | Giá trị | Ghi chú |
                                | --- | --- | --- |
                                | Tổng số finding | 2 | ⬜ |
                                | Tổng số finding đã fix | 2 / 2 | ⬜ |
                                | Tỷ lệ xử lý finding nghiêm trọng (Blocker) | 2 / 2 (100%) | ⬜ |
                                | Tỷ lệ AI finding được con người chấp nhận | 1２ / 15 | ⬜ |
                                | Tỷ lệ AI review finding hữu ích | 2 / 2 (100%) | ⬜ |
                                | Tỷ lệ AI finding bị đánh giá false positive | 0 / 2 (0%) | ⬜ |
                                | Tỷ lệ AI finding đã được xử lý | 2 / 2 (100%) | ⬜ |
                                """;

                Optional<AiReviewStats> parsed = parser.parse(content);

                assertThat(parsed).isPresent();
                AiReviewStats stats = parsed.get();
                assertThat(stats.aiReviewAdoptedCount())
                                .as("\"1２/15\" mixes an ASCII '1' with a full-width '２' in the same numerator; "
                                                + "the ASCII-only pattern must fail this value entirely rather than "
                                                + "matching a truncated numerator (e.g. misreading it as 1/15) — "
                                                + "the whole shared-denominator group must go NULL per AC-AIRKI-4, "
                                                + "not a silently wrong single-digit adoption count")
                                .isNull();
                assertThat(stats.aiReviewFindingTotalCount()).isNull();
                assertThat(stats.aiReviewValidCount()).isNull();
                assertThat(stats.aiReviewFalsePositiveCount()).isNull();
                assertThat(stats.aiReviewResolvedCount()).isNull();
                assertThat(stats.blockerMajorResolvedCount())
                                .as("Blocker/Major has its own independent denominator (A-AIRKI-3) and must not "
                                                + "be nulled by an unrelated shared-group row failing to parse")
                                .isEqualTo(2);
                assertThat(stats.blockerMajorTotalCount()).isEqualTo(2);
        }

        // ── Missing/malformed §8 → treated as "no data", not an error ─────────────

        @Test
        void parse_noSection8_returnsEmpty() {
                String content = """
                                ## 1. Tổng quan
                                Nothing else here.
                                """;

                Optional<AiReviewStats> parsed = parser.parse(content);

                assertThat(parsed).isEmpty();
        }

        @Test
        void parse_section8WithoutTable_returnsEmpty() {
                String content = """
                                ## 8. Số liệu thống kê
                                Chưa có bảng ở đây.
                                """;

                Optional<AiReviewStats> parsed = parser.parse(content);

                assertThat(parsed).isEmpty();
        }
}
