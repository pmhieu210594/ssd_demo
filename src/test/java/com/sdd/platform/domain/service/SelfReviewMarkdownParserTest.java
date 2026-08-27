package com.sdd.platform.domain.service;

import com.sdd.platform.domain.service.markdown.selfreview.SelfReviewMarkdownParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SelfReviewMarkdownParserTest {

        private final SelfReviewMarkdownParser parser = new SelfReviewMarkdownParser();

        @Test
        void parse_actualRepositorySelfReview_coversCoreEnvelopeAndCounts() throws Exception {
                var resource = SelfReviewMarkdownParserTest.class.getClassLoader()
                                .getResource("test-fixtures/PARSER-SELF-REVIEW/self-review.md");
                String markdown = Files.readString(Path.of(resource.toURI()));

                SelfReviewMarkdownParser.ParsedArtifact parsed = parser.parse(
                                markdown,
                                "docs/changes/PARSER-SELF-REVIEW/self-review.md",
                                "official");

                assertThat(parsed.ticketId()).isEqualTo("PARSER-SELF-REVIEW");
                assertThat(parsed.parseMode()).isEqualTo("official");
                assertThat(parsed.parseStatus()).isEqualTo("PARTIAL");
                assertThat(parsed.artifactStatus()).isEqualTo("present");
                assertThat(parsed.artifactExists()).isTrue();
                assertThat(parsed.finalVerdict()).isEqualTo("PASS");
                assertThat(parsed.sections()).extracting(SelfReviewMarkdownParserTest::key)
                                .contains(
                                                "SELF_REVIEW",
                                                "IMPLEMENTATION_SUMMARY",
                                                "SPECIFICATION_AC_MATCHING",
                                                "LIST_OF_CHANGED_FILES",
                                                "RUN_COMMAND_AND_RESULTS",
                                                "SELF_CHECK_USING_REVIEW_CHECKLIST",
                                                "TEST_PLAN_CORRESPONDING_STATUS",
                                                "BUGS_FOUND_AND_RESOLVED",
                                                "UNPROCESSED_PENDING_ACCEPTED_RISK",
                                                "AI_GENERATED_PREDICTIONS",
                                                "ITEMS_REVIEWED_BY_HUMANS",
                                                "FINAL_SELF_VERDICT");
                assertThat(parsed.tables()).hasSize(6);
                assertThat(parsed.tableSections()).hasSize(6);
                assertThat(parsed.freeTextSections()).extracting(SelfReviewMarkdownParserTest::key)
                                .contains("IMPLEMENTATION_SUMMARY", "TEST_PLAN_CORRESPONDING_STATUS",
                                                "FINAL_SELF_VERDICT");
                assertThat(parsed.warnings()).extracting(SelfReviewMarkdownParser.ParsingIssue::code)
                                .contains("placeholder_detected");
                assertThat(parsed.errors()).isEmpty();
                assertThat(parsed.requiredSectionsMissing()).isEmpty();
                assertThat(parsed.parsedSummary())
                                .containsEntry("ticket_id", "PARSER-SELF-REVIEW")
                                .containsEntry("parse_status", "PARTIAL")
                                .containsEntry("section_count", 11)
                                .containsEntry("table_count", 6)
                                .containsEntry("warning_count", 1)
                                .containsEntry("error_count", 0)
                                .containsEntry("placeholder_count", 1)
                                .containsEntry("required_sections_missing_count", 0)
                                .containsEntry("final_verdict", "PASS")
                                .containsEntry("final_verdict_valid", true)
                                .containsEntry("has_review_checklist", true)
                                .containsEntry("has_human_review", true);
        }

        @Test
        void parse_detectsMissingSectionAndInvalidVerdict() {
                String markdown = """
                                # Self Review

                                **Ticket ID**: PARSER-SELF-REVIEW

                                ## 1. Implementation Summary
                                Summary.

                                ## 2. Specification/AC Matching
                                | AC ID | status | evidence |
                                |---|---|---|
                                | AC-PARSER-SELF-REVIEW-1 | PASS | Evidence |

                                ## 11. Final Self-Verdict
                                MAYBE
                                """;

                SelfReviewMarkdownParser.ParsedArtifact parsed = parser.parse(markdown,
                                "docs/changes/PARSER-SELF-REVIEW/self-review.md");

                assertThat(parsed.parseStatus()).isEqualTo("FAILED");
                assertThat(parsed.requiredSectionsMissing()).contains(
                                "section:LIST_OF_CHANGED_FILES",
                                "section:RUN_COMMAND_AND_RESULTS",
                                "section:SELF_CHECK_USING_REVIEW_CHECKLIST",
                                "section:TEST_PLAN_CORRESPONDING_STATUS",
                                "section:BUGS_FOUND_AND_RESOLVED",
                                "section:UNPROCESSED_PENDING_ACCEPTED_RISK",
                                "section:ITEMS_REVIEWED_BY_HUMANS");
                assertThat(parsed.warnings()).extracting(SelfReviewMarkdownParser.ParsingIssue::code)
                                .contains("optional_section_missing");
                assertThat(parsed.errors()).extracting(SelfReviewMarkdownParser.ParsingIssue::code)
                                .contains("verdict_invalid");
                assertThat(parsed.finalVerdict()).isNull();
        }

        @Test
        void parse_flagsNestedSubsectionDepthBeyondOneLevel() {
                String markdown = """
                                # Self Review
                                **Ticket ID**: PARSER-SELF-REVIEW

                                ## 1. Implementation Summary
                                Main summary.

                                ### 1.1 Too Deep
                                This nested level should trigger a warning.

                                ## 11. Final Self-Verdict
                                PASS
                                """;

                SelfReviewMarkdownParser.ParsedArtifact parsed = parser.parse(markdown,
                                "docs/changes/PARSER-SELF-REVIEW/self-review.md");

                assertThat(parsed.warnings()).extracting(SelfReviewMarkdownParser.ParsingIssue::code)
                                .contains("nested_subsection_depth_exceeded");
                assertThat(parsed.parseStatus()).isEqualTo("PARTIAL");
        }

        @Test
        void parse_flagsMalformedTableInRequiredSection() {
                String markdown = """
                                # Self Review
                                **Ticket ID**: PARSER-SELF-REVIEW

                                ## 1. Implementation Summary
                                Summary.

                                ## 2. Specification/AC Matching
                                - AC-PARSER-SELF-REVIEW-1 PASS Evidence

                                ## 11. Final Self-Verdict
                                PASS
                                """;

                SelfReviewMarkdownParser.ParsedArtifact parsed = parser.parse(markdown,
                                "docs/changes/PARSER-SELF-REVIEW/self-review.md");

                assertThat(parsed.errors()).isEmpty();
                assertThat(parsed.warnings()).extracting(SelfReviewMarkdownParser.ParsingIssue::code)
                                .contains("table_invalid");
                assertThat(parsed.requiredSectionsMissing()).contains("section:SPECIFICATION_AC_MATCHING table");
                assertThat(parsed.parseStatus()).isEqualTo("PARTIAL");
        }

        @Test
        void parse_marksRequiredTableSectionsMissing_whenTheyHaveOnlyHeaderAndNoDataRows() {
                String markdown = """
                                # Self Review
                                **Ticket ID**: PARSER-SELF-REVIEW

                                ## 4. Run Command and Results
                                | command | result |
                                |---|---|

                                ## 8. Unprocessed / Pending / Accepted Risk
                                | item | reason | impact | owner | deadline |
                                |---|---|---|---|---|

                                ## 11. Final Self-Verdict
                                PASS
                                """;

                SelfReviewMarkdownParser.ParsedArtifact parsed = parser.parse(markdown,
                                "docs/changes/PARSER-SELF-REVIEW/self-review.md");

                assertThat(parsed.requiredSectionsMissing()).contains(
                                "section:RUN_COMMAND_AND_RESULTS",
                                "section:UNPROCESSED_PENDING_ACCEPTED_RISK");
                assertThat(parsed.warnings()).extracting(SelfReviewMarkdownParser.ParsingIssue::code)
                                .contains("table_empty");
        }

        @Test
        void parse_normalizesLineEndings_beforeHashing() {
                String lfMarkdown = """
                                # Self Review
                                **Ticket ID**: PARSER-SELF-REVIEW

                                ## 1. Implementation Summary
                                Summary.

                                ## 11. Final Self-Verdict
                                PASS
                                """;
                String crlfMarkdown = lfMarkdown.replace("\n", "\r\n");

                SelfReviewMarkdownParser.ParsedArtifact lf = parser.parse(lfMarkdown,
                                "docs/changes/PARSER-SELF-REVIEW/self-review.md");
                SelfReviewMarkdownParser.ParsedArtifact crlf = parser.parse(crlfMarkdown,
                                "docs/changes/PARSER-SELF-REVIEW/self-review.md");

                assertThat(lf.contentHash()).isEqualTo(crlf.contentHash());
                assertThat(lf.normalizedContent()).isEqualTo(crlf.normalizedContent());
        }

        @Test
        void parse_normalizesVerdictTokens_andSupportsAliasLookups() throws Exception {
                var resource = SelfReviewMarkdownParserTest.class.getClassLoader()
                                .getResource("test-fixtures/PARSER-SELF-REVIEW/self-review.md");
                String markdown = Files.readString(Path.of(resource.toURI()))
                                .replace("## 11. Final Self-Verdict\nPASS", "## 11. Final Self-Verdict\nneeds update");

                SelfReviewMarkdownParser.ParsedArtifact parsed = parser.parse(
                                markdown,
                                "docs/changes/PARSER-SELF-REVIEW/self-review.md",
                                "official");

                assertThat(parsed.parsedSummary()).containsEntry("final_verdict_valid", true);
                assertThat(parser.hasSection(parsed, "final self verdict", "final-self-verdict", "FINAL_SELF_VERDICT"))
                                .isTrue();
        }

        private static String key(
                        com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownSection section) {
                return section.canonicalKey();
        }
}
