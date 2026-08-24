package com.sdd.platform.domain.service;

import com.sdd.platform.domain.service.markdown.selfreview.SelfReviewMarkdownParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SelfReviewMarkdownParserTest {

    private final SelfReviewMarkdownParser parser = new SelfReviewMarkdownParser();

    @Test
    void parse_partialEnvelope_withCurrentCanonicalContract() {
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
                PASS
                """;

        var parsed = parser.parse(markdown, "docs/changes/PARSER-SELF-REVIEW/self-review.md", "official");

        assertThat(parsed.ticketId()).isEqualTo("PARSER-SELF-REVIEW");
        assertThat(parsed.parseMode()).isEqualTo("official");
        assertThat(parsed.parseStatus()).isEqualTo("PARTIAL");
        assertThat(parsed.artifactStatus()).isEqualTo("present");
        assertThat(parsed.artifactExists()).isTrue();
        assertThat(parsed.finalVerdict()).isNull();
        assertThat(parsed.requiredSectionsMissing()).isNotEmpty();
        assertThat(parsed.warnings()).extracting(SelfReviewMarkdownParser.ParsingIssue::code).contains("verdict_missing");
        assertThat(parsed.parsedSummary()).containsEntry("final_verdict_valid", false);
    }

    @Test
    void parse_detectsMissingSections() {
        String markdown = """
                # Self Review
                **Ticket ID**: PARSER-SELF-REVIEW

                ## 2. Specification/AC Matching
                | AC ID | status | evidence |
                |---|---|---|
                | AC-PARSER-SELF-REVIEW-1 | PASS | Evidence |
                """;

        var parsed = parser.parse(markdown, "docs/changes/PARSER-SELF-REVIEW/self-review.md");

        assertThat(parsed.parseStatus()).isEqualTo("PARTIAL");
        assertThat(parsed.requiredSectionsMissing()).isNotEmpty();
        assertThat(parsed.warnings()).extracting(SelfReviewMarkdownParser.ParsingIssue::code).contains("verdict_missing");
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
                """;

        var parsed = parser.parse(markdown, "docs/changes/PARSER-SELF-REVIEW/self-review.md");
        assertThat(parsed.warnings()).extracting(SelfReviewMarkdownParser.ParsingIssue::code)
                .contains("nested_subsection_depth_exceeded");
        assertThat(parsed.parseStatus()).isEqualTo("PARTIAL");
    }

    @Test
    void parse_malformedTableSection_staysPartial() {
        String markdown = """
                # Self Review
                **Ticket ID**: PARSER-SELF-REVIEW

                ## 4. Run Command and Results
                - mvn test PASS
                """;

        var parsed = parser.parse(markdown, "docs/changes/PARSER-SELF-REVIEW/self-review.md");
        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.parseStatus()).isEqualTo("PARTIAL");
        assertThat(parsed.requiredSectionsMissing()).isNotEmpty();
    }

    @Test
    void parse_tableSectionsWithNoRows_staysPartial() {
        String markdown = """
                # Self Review
                **Ticket ID**: PARSER-SELF-REVIEW

                ## 4. Run Command and Results
                | command | result |
                |---|---|
                """;

        var parsed = parser.parse(markdown, "docs/changes/PARSER-SELF-REVIEW/self-review.md");
        assertThat(parsed.parseStatus()).isEqualTo("PARTIAL");
        assertThat(parsed.requiredSectionsMissing()).isNotEmpty();
    }

    @Test
    void parse_normalizesLineEndings_beforeHashing() {
        String lfMarkdown = """
                # Self Review
                **Ticket ID**: PARSER-SELF-REVIEW

                ## 1. Implementation Summary
                Summary.
                """;
        String crlfMarkdown = lfMarkdown.replace("\n", "\r\n");

        var lf = parser.parse(lfMarkdown, "docs/changes/PARSER-SELF-REVIEW/self-review.md");
        var crlf = parser.parse(crlfMarkdown, "docs/changes/PARSER-SELF-REVIEW/self-review.md");

        assertThat(lf.contentHash()).isEqualTo(crlf.contentHash());
        assertThat(lf.normalizedContent()).isEqualTo(crlf.normalizedContent());
    }

    @Test
    void parse_supportsAliasLookups() {
        String markdown = """
                # Self Review
                **Ticket ID**: PARSER-SELF-REVIEW

                ## 1. Implementation Summary
                Summary.
                """;

        var parsed = parser.parse(markdown, "docs/changes/PARSER-SELF-REVIEW/self-review.md", "official");

        assertThat(parsed.parsedSummary()).containsEntry("final_verdict_valid", false);
        assertThat(parser.hasSection(parsed, "implementation summary", "IMPLEMENTATION_SUMMARY")).isTrue();
    }
}
