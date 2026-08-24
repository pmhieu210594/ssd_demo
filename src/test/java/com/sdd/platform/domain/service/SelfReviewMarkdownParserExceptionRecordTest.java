package com.sdd.platform.domain.service;

import com.sdd.platform.domain.service.markdown.selfreview.SelfReviewMarkdownParser;
import com.sdd.platform.domain.service.markdown.selfreview.SelfReviewMarkdownParser.ParsedArtifact;
import com.sdd.platform.domain.service.markdown.selfreview.SelfReviewMarkdownParser.ParsedExceptionRecord;
import com.sdd.platform.domain.service.markdown.selfreview.SelfReviewMarkdownParser.ParsingIssue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SelfReviewMarkdownParserExceptionRecordTest {

    private static final String SOURCE_PATH = "docs/changes/FIRST-CI-PASS-EXCEPTION-KPI/self-review.md";

    private final SelfReviewMarkdownParser parser = new SelfReviewMarkdownParser();

    // AC-FCI-4: EXCEPTION_RECORD section with data rows → explicit records extracted
    @Test
    void parse_withExceptionRecordSection_extractsRows() {
        String markdown = """
                # Self Review
                **Ticket ID**: FIRST-CI-PASS-EXCEPTION-KPI

                ## Exception Record

                | Exception Type | Reason | Alternative Check | Approved | Approved By Role | Expiry Date | Follow Up Status |
                |---|---|---|---|---|---|---|
                | DEADLINE_EXTENSION | Urgent business need | None | Yes | PM | 2026-12-31 | OPEN |
                """;

        ParsedArtifact parsed = parser.parse(markdown, SOURCE_PATH);

        List<ParsedExceptionRecord> records = parsed.exceptionRecords();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).exceptionType()).isEqualTo("DEADLINE_EXTENSION");
        assertThat(records.get(0).reasonPresent()).isTrue();
        assertThat(records.get(0).approved()).isTrue();
        assertThat(records.get(0).followUpStatus()).isEqualTo("OPEN");
    }

    // AC-FCI-4: multiple rows are all extracted
    @Test
    void parse_withMultipleExceptionRows_extractsAllRows() {
        String markdown = """
                # Self Review
                **Ticket ID**: FIRST-CI-PASS-EXCEPTION-KPI

                ## Exception Record

                | Exception Type | Reason | Approved | Follow Up Status |
                |---|---|---|---|
                | DEADLINE_EXTENSION | Business urgency | Yes | OPEN |
                | SCOPE_REDUCTION | Agreed with PM | No | CLOSED |
                """;

        ParsedArtifact parsed = parser.parse(markdown, SOURCE_PATH);

        assertThat(parsed.exceptionRecords()).hasSize(2);
        assertThat(parsed.exceptionRecords())
                .extracting(ParsedExceptionRecord::exceptionType)
                .containsExactly("DEADLINE_EXTENSION", "SCOPE_REDUCTION");
    }

    // AC-FCI-6: EXCEPTION_RECORD section absent → empty records, no synthetic rows
    @Test
    void parse_missingExceptionSection_returnsEmptyExceptionRecords() {
        String markdown = """
                # Self Review
                **Ticket ID**: FIRST-CI-PASS-EXCEPTION-KPI

                ## Implementation Summary
                Some implementation details here.
                """;

        ParsedArtifact parsed = parser.parse(markdown, SOURCE_PATH);

        assertThat(parsed.exceptionRecords()).isEmpty();
    }

    // AC-FCI-6: no warning specific to exception when section is completely absent
    @Test
    void parse_missingExceptionSection_doesNotEmitExceptionTableWarning() {
        String markdown = """
                # Self Review
                **Ticket ID**: FIRST-CI-PASS-EXCEPTION-KPI

                ## Implementation Summary
                Content here.
                """;

        ParsedArtifact parsed = parser.parse(markdown, SOURCE_PATH);

        assertThat(parsed.exceptionRecords()).isEmpty();
        assertThat(parsed.warnings())
                .extracting(ParsingIssue::code)
                .doesNotContain("exception_table_empty");
    }

    // AC-FCI-10: EXCEPTION_RECORD present with empty table → warning emitted
    @Test
    void parse_emptyExceptionTable_emitsWarning() {
        String markdown = """
                # Self Review
                **Ticket ID**: FIRST-CI-PASS-EXCEPTION-KPI

                ## Exception Record

                | Exception Type | Reason | Approved | Follow Up Status |
                |---|---|---|---|
                """;

        ParsedArtifact parsed = parser.parse(markdown, SOURCE_PATH);

        assertThat(parsed.exceptionRecords()).isEmpty();
        assertThat(parsed.warnings())
                .extracting(ParsingIssue::code)
                .contains("exception_table_empty");
    }

    // AC-FCI-10: EXCEPTION_RECORD table with placeholder-only type values → warning emitted, no records
    @Test
    void parse_exceptionTableWithPlaceholderRows_emitsWarning() {
        String markdown = """
                # Self Review
                **Ticket ID**: FIRST-CI-PASS-EXCEPTION-KPI

                ## Exception Record

                | Exception Type | Reason | Approved | Follow Up Status |
                |---|---|---|---|
                | TBD | --- | N/A | N/A |
                """;

        ParsedArtifact parsed = parser.parse(markdown, SOURCE_PATH);

        assertThat(parsed.exceptionRecords()).isEmpty();
        assertThat(parsed.warnings())
                .extracting(ParsingIssue::code)
                .contains("exception_table_empty");
    }
}
