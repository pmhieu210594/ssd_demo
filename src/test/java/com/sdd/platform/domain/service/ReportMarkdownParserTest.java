package com.sdd.platform.domain.service;

import com.sdd.platform.domain.service.markdown.report.ReportMarkdownParser;
import com.sdd.platform.domain.service.markdown.report.ReportMarkdownParser.ParsedArtifact;
import com.sdd.platform.domain.service.markdown.report.ReportMarkdownParser.ParsingIssue;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReportMarkdownParserTest {

    private final ReportMarkdownParser parser = new ReportMarkdownParser();

    @Test
    void parse_emptyContent_returnsStatusFailed() {
        ParsedArtifact parsed = parser.parse("");
        assertThat(parsed.parseStatus()).isEqualTo("FAILED");
        assertThat(parsed.errors()).extracting(ParsingIssue::code).contains("markdown_empty");
    }

    @Test
    void parse_whitespaceOnlyContent_returnsStatusFailed() {
        ParsedArtifact parsed = parser.parse("   \n  \t  ");
        assertThat(parsed.parseStatus()).isEqualTo("FAILED");
        assertThat(parsed.errors()).extracting(ParsingIssue::code).contains("markdown_empty");
    }

    @Test
    void parse_missingSections_returnsStatusPartial() {
        ParsedArtifact parsed = parser.parse("## Summary\nPresent content only in one section.", "docs/changes/PARSER-REPORT/report.md");
        assertThat(parsed.parseStatus()).isEqualTo("PARTIAL");
        assertThat(parsed.warnings()).extracting(ParsingIssue::code).contains("required_fields_missing");
    }

    @Test
    void parse_placeholderContent_returnsStatusPartial() throws Exception {
        var resource = ReportMarkdownParserTest.class.getClassLoader().getResource("test-fixtures/PARSER-REPORT/placeholder-content.md");
        String content = Files.readString(Path.of(resource.toURI()));
        ParsedArtifact parsed = parser.parse(content, "docs/changes/PARSER-REPORT/report.md");
        assertThat(parsed.parseStatus()).isEqualTo("PARTIAL");
        assertThat(parsed.warnings()).extracting(ParsingIssue::code).contains("placeholder_detected");
    }

    @Test
    void parse_fullValidReport_returnsStatusPartial_withCurrentRequiredFields() throws Exception {
        var resource = ReportMarkdownParserTest.class.getClassLoader().getResource("test-fixtures/PARSER-REPORT/valid-full-report.md");
        String content = Files.readString(Path.of(resource.toURI()));
        ParsedArtifact parsed = parser.parse(content, "docs/changes/PARSER-REPORT/report.md");
        assertThat(parsed.parseStatus()).isEqualTo("PARTIAL");
        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.warnings()).extracting(ParsingIssue::code).contains("required_fields_missing");
    }

    @Test
    void parse_officialMode_returnsStatusPartial_whenWarningsExist() throws Exception {
        var resource = ReportMarkdownParserTest.class.getClassLoader().getResource("test-fixtures/PARSER-REPORT/valid-full-report.md");
        String content = Files.readString(Path.of(resource.toURI()));
        ParsedArtifact parsed = parser.parse(content, "docs/changes/PARSER-REPORT/report.md", "official");
        assertThat(parsed.parseStatus()).isEqualTo("PARTIAL");
    }

    @Test
    void parse_sectionValues_areStrings() throws Exception {
        var resource = ReportMarkdownParserTest.class.getClassLoader().getResource("test-fixtures/PARSER-REPORT/list-content.md");
        String content = Files.readString(Path.of(resource.toURI()));
        ParsedArtifact parsed = parser.parse(content, "docs/changes/PARSER-REPORT/report.md");
        assertThat(parsed.sections()).isNotEmpty();
        assertThat(parsed.sections().values().stream().allMatch(v -> v == null || v instanceof String)).isTrue();
    }

    @Test
    void allFields_hasCurrentShape() {
        assertThat(ReportMarkdownParser.ALL_FIELDS).hasSize(7);
        assertThat(ReportMarkdownParser.ALL_FIELDS.stream().anyMatch(v -> v.contains("REVIEW"))).isTrue();
    }

    @Test
    void parse_ticketIdInferredFromSourcePath() {
        ParsedArtifact parsed = parser.parse("## Summary\nContent.", "docs/changes/PARSER-REPORT/report.md");
        assertThat(parsed.ticketId()).isEqualTo("PARSER-REPORT");
    }

    @Test
    void parse_noTicketIdSource_generatesTicketIdWarning() {
        ParsedArtifact parsed = parser.parse("## Summary\nContent.", null);
        assertThat(parsed.warnings()).extracting(ParsingIssue::code).contains("ticket_id_missing");
    }

    @Test
    void parse_reportAliasSections_exportToParsedSummary() {
        String content = """
                ## 1. Edited summary
                Content for edited summary.

                ## 3. Scope of influence
                Scope content.

                ## 5. Review results
                Review passed.

                ## 6. Test results
                All tests green.
                """;
        ParsedArtifact parsed = parser.parse(content, "docs/changes/PARSER-REPORT/report.md");
        assertThat(parsed.parsedSummary()).containsKeys("EDITED_SUMMARY", "SCOPE_OF_INFLUENCE", "REVIEW_RESULTS", "TEST_RESULTS");
    }
}
