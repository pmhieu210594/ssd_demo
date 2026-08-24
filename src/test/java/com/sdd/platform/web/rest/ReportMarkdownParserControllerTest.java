package com.sdd.platform.web.rest;

import com.sdd.platform.domain.service.markdown.report.ReportMarkdownParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportMarkdownParserControllerTest {

    private final ReportMarkdownParserController controller =
            new ReportMarkdownParserController(new ReportMarkdownParser());

    @Test
    void parseFile_rejectsPathsOutsideReportScope() {
        var request = new ReportMarkdownParserController.ParseFileRequest(
                "D:\\temp\\notes.md", "draft");

        assertThatThrownBy(() -> controller.parseFile(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("changes/<TICKET>/report.md");
    }

    @Test
    void parseFile_rejectsNonMarkdownExtension() {
        var request = new ReportMarkdownParserController.ParseFileRequest(
                "D:\\temp\\report.txt", "draft");

        assertThatThrownBy(() -> controller.parseFile(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".md");
    }

    @Test
    void parseFile_readsReportFixture_andReturnsParsedEnvelope() throws Exception {
        Path fixturePath = Path.of("target", "test-fixtures", "changes", "PARSER-REPORT", "report.md");
        Files.createDirectories(fixturePath.getParent());
        var resource = ReportMarkdownParserControllerTest.class.getClassLoader()
                .getResource("test-fixtures/PARSER-REPORT/valid-full-report.md");
        Files.writeString(fixturePath, Files.readString(Path.of(resource.toURI())));

        Map<String, Object> response = controller.parseFile(
                new ReportMarkdownParserController.ParseFileRequest(
                        fixturePath.toString(), "report"));

        assertThat(response.get("filePath")).isEqualTo(fixturePath.toAbsolutePath().toString());
        Map<String, Object> result = castMap(response.get("result"));
        assertThat(result.get("ticketId")).isEqualTo("PARSER-REPORT");
        assertThat(result.get("artifactStatus")).isEqualTo("present");
        assertThat(result.get("artifactExists")).isEqualTo(Boolean.TRUE);
        assertThat(result.get("parsedSummary")).isInstanceOf(Map.class);
    }

    @Test
    void parseInline_returnsEnvelope() {
        var request = new ReportMarkdownParserController.ParseRequest(
                """
                ## Summary
                Test content.
                """,
                "docs/changes/PARSER-REPORT/report.md",
                "draft");

        Map<String, Object> response = controller.parseInline(request);

        assertThat(response.get("ticketId")).isEqualTo("PARSER-REPORT");
        assertThat(response.get("parseMode")).isEqualTo("draft");
        assertThat(response.get("parsedSummary")).isInstanceOf(Map.class);
        assertThat(response.get("parseStatus")).isNotNull();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }
}
