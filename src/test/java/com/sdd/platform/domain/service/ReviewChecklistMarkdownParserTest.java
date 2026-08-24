package com.sdd.platform.domain.service;

import com.sdd.platform.domain.service.markdown.reviewchecklist.ReviewChecklistMarkdownParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewChecklistMarkdownParserTest {

    private final ReviewChecklistMarkdownParser parser = new ReviewChecklistMarkdownParser();

    @Test
    void parse_validChecklist_coversCoreEnvelope() {
        String markdown = """
                # Review Checklist
                **Ticket ID**: PARSER-REVIEW-CHECKLIST

                ## SPEC_AC
                core alignment verified

                ## THIET_KE_PHU_THUOC
                dependency design reviewed

                ## BAO_MAT
                security review items

                ## HIEU_NANG
                performance review items

                ## TUONG_THICH
                compatibility review items

                ## LOGGING_AUDIT
                logging and audit review items

                ## XU_LY_LOI
                error handling review items

                ## KIEM_THU
                test review items

                ## VAN_HANH
                operation review items

                ## BANG_ANH_XA_AC_CHECKLIST_ITEMS
                mapping reviewed
                """;

        var parsed = parser.parse(markdown, "docs/changes/PARSER-REVIEW-CHECKLIST/review-checklist.md", "official");

        assertThat(parsed.ticketId()).isEqualTo("PARSER-REVIEW-CHECKLIST");
        assertThat(parsed.parseMode()).isEqualTo("official");
        assertThat(parsed.artifactExists()).isTrue();
        assertThat(parsed.artifactStatus()).isEqualTo("present");
        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.parsedSummary()).containsEntry("ticket_id", "PARSER-REVIEW-CHECKLIST");
    }

    @Test
    void parse_reviewChecklist_marksSectionsUnchecked_whenAnyCheckboxRemainsOpen() {
        String markdown = """
                # Review Checklist
                **Ticket ID**: PARSER-REVIEW-CHECKLIST

                ## SECURITY_PRIVACY_REVIEW
                - [X] No secrets are logged
                - [ ] No PII is exposed

                ## TEST_REVIEW
                - [X] Boundary tests exist
                - [V] Negative-path tests exist
                """;

        var parsed = parser.parse(markdown, "docs/changes/PARSER-REVIEW-CHECKLIST/review-checklist.md", "official");
        assertThat(parsed.parseStatus()).isEqualTo("PARTIAL");
    }

    @Test
    void parse_missingFields_generatesWarnings() {
        String markdown = """
                # Review Checklist
                **Ticket ID**: PARSER-REVIEW-CHECKLIST

                ## SPEC_AC
                OK
                """;

        var parsed = parser.parse(markdown, "docs/changes/PARSER-REVIEW-CHECKLIST/review-checklist.md");

        assertThat(parsed.requiredFieldsMissing()).isNotEmpty();
        assertThat(parsed.warnings()).extracting(ReviewChecklistMarkdownParser.ParsingIssue::code).contains("required_fields_missing");
        assertThat(parsed.parseStatus()).isEqualTo("PARTIAL");
    }

    @Test
    void parse_detectsPlaceholder() {
        String markdown = """
                # Review Checklist
                **Ticket ID**: PARSER-REVIEW-CHECKLIST

                ## SPEC_AC
                <TODO>
                """;

        var parsed = parser.parse(markdown, "docs/changes/PARSER-REVIEW-CHECKLIST/review-checklist.md");
        assertThat(parsed.placeholders()).isNotEmpty();
        assertThat(parsed.warnings()).extracting(ReviewChecklistMarkdownParser.ParsingIssue::code).contains("placeholder_detected");
    }

    @Test
    void parse_ticketId_inferredFromPath_whenMissingInContent() {
        var parsed = parser.parse("# Review Checklist\n## SPEC_AC\nOK", "docs/changes/PARSER-REVIEW-CHECKLIST/review-checklist.md");
        assertThat(parsed.ticketId()).isEqualTo("PARSER-REVIEW-CHECKLIST");
    }

    @Test
    void parse_emptyContent_resultsInMissingArtifact() {
        var parsed = parser.parse("", "docs/changes/PARSER-REVIEW-CHECKLIST/review-checklist.md");
        assertThat(parsed.artifactExists()).isFalse();
        assertThat(parsed.artifactStatus()).isEqualTo("missing");
    }
}

