package com.sdd.platform.domain.service;

import com.sdd.platform.domain.service.markdown.report.ReportMarkdownParser;
import com.sdd.platform.domain.service.markdown.report.ReportMarkdownParser.ParsedArtifact;
import com.sdd.platform.domain.service.markdown.report.ReportMarkdownParser.ParsingIssue;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReportMarkdownParserTest {

    private static final String REPORT_SOURCE_PATH = "docs/changes/PARSER-REPORT/report.md";

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
        ParsedArtifact parsed = parser.parse("## Summary\nPresent content only in one section.", REPORT_SOURCE_PATH);
        assertThat(parsed.parseStatus()).isEqualTo("PARTIAL");
        assertThat(parsed.warnings()).extracting(ParsingIssue::code).contains("required_fields_missing");
    }

    @Test
    void parse_placeholderContent_returnsStatusPartial() throws Exception {
        ParsedArtifact parsed = parser.parse(readFixture("placeholder-content.md"), REPORT_SOURCE_PATH);
        assertThat(parsed.parseStatus()).isEqualTo("PARTIAL");
        assertThat(parsed.warnings()).extracting(ParsingIssue::code).contains("placeholder_detected");
    }

    @Test
    void parse_fullValidReport_returnsDraft_whenTemplateSectionsAreComplete() throws Exception {
        ParsedArtifact parsed = parser.parse(readFixture("valid-full-report.md"), REPORT_SOURCE_PATH);
        assertThat(parsed.parseStatus()).isEqualTo("DRAFT");
        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.warnings()).isEmpty();
        assertThat(parsed.headerMetadata())
                .containsEntry("ticket_id", "PARSER-REPORT")
                .containsEntry("create_date", "2026-08-25 10:00")
                .containsEntry("update_date", "2026-08-25 10:15");
    }

    @Test
    void parse_officialMode_returnsStatusOfficial_whenTemplateSectionsAreComplete() throws Exception {
        ParsedArtifact parsed = parser.parse(readFixture("valid-full-report.md"), REPORT_SOURCE_PATH, "official");
        assertThat(parsed.parseStatus()).isEqualTo("OFFICIAL");
    }

    @Test
    void parse_sectionValues_areStrings() throws Exception {
        ParsedArtifact parsed = parser.parse(readFixture("list-content.md"), REPORT_SOURCE_PATH);
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
        ParsedArtifact parsed = parser.parse("## Summary\nContent.", REPORT_SOURCE_PATH);
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
                ## 1. Tóm tắt thay đổi
                Content for edited summary.

                ## 2. Phạm vi ảnh hưởng
                Scope content.

                ## 3. Kết quả review
                Review passed.

                ## 4. Kết quả kiểm thử
                All tests green.

                ## 5. Công việc còn lại / Hành động tiếp theo
                Follow-up item.

                ## 6. Quy trình hoàn tác
                Rollback steps.

                ## 7. Danh mục đầu ra
                Output list.
                """;
        ParsedArtifact parsed = parser.parse(content, REPORT_SOURCE_PATH);
        assertThat(parsed.parsedSummary()).containsKeys(
                "TÓM_TẮT_THAY_ĐỔI",
                "PHẠM_VI_ẢNH_HƯỞNG",
                "KẾT_QUẢ_REVIEW",
                "KẾT_QUẢ_KIỂM_THỬ",
                "EDITED_SUMMARY",
                "SCOPE_OF_INFLUENCE",
                "REVIEW_RESULTS",
                "TEST_RESULTS");
        assertThat(parsed.headerMetadata()).isEmpty();
    }

    private String readFixture(String fileName) throws Exception {
        var resource = ReportMarkdownParserTest.class.getClassLoader()
                .getResource("test-fixtures/PARSER-REPORT/" + fileName);
        return Files.readString(Path.of(resource.toURI()));
    }
}
