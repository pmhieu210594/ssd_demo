package com.sdd.platform.web.rest;

import com.sdd.platform.domain.service.markdown.specpack.SpecPackMarkdownParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpecPackMarkdownParserControllerTest {

    private final SpecPackMarkdownParserController controller = new SpecPackMarkdownParserController(new SpecPackMarkdownParser());

    @Test
    void parseFile_rejectsPathsOutsideSpecPackScope() {
        SpecPackMarkdownParserController.ParseFileRequest request = new SpecPackMarkdownParserController.ParseFileRequest("D:\\temp\\notes.md", "draft");

        assertThatThrownBy(() -> controller.parseFile(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("changes/<TICKET>/spec-pack.md");
    }

    @Test
    void parseFile_readsRepositoryFixture_andReturnsParsedEnvelope() throws Exception {
        Path fixturePath = Path.of("target", "test-fixtures", "changes", "PARSER-SPEC-PACK", "spec-pack.md");
        Files.createDirectories(fixturePath.getParent());
        var resource = SpecPackMarkdownParserControllerTest.class.getClassLoader()
                .getResource("test-fixtures/PARSER-SPEC-PACK/spec-pack.md");
        Files.writeString(fixturePath, Files.readString(Path.of(resource.toURI())));

        Map<String, Object> response = controller.parseFile(new SpecPackMarkdownParserController.ParseFileRequest(
                fixturePath.toString(),
                "official"));

        assertThat(response.get("filePath")).isEqualTo(fixturePath.toAbsolutePath().toString());
        Map<String, Object> result = castMap(response.get("result"));
        assertThat(result.get("ticketId")).isEqualTo("PARSER-SPEC-PACK");
        assertThat(result.get("parseStatus")).isEqualTo("PARTIAL");
        assertThat(result.get("artifactStatus")).isEqualTo("present");
        assertThat(result.get("artifactExists")).isEqualTo(Boolean.TRUE);
        assertThat(castList(result.get("acceptanceCriteria"))).hasSize(10);
        assertThat(castList(result.get("warnings")))
                .extracting(issue -> ((SpecPackMarkdownParser.ParsingIssue) issue).code())
                .contains("placeholder_detected");
        assertThat(castMap(result.get("parsedSummary"))).containsEntry("ac_count", 10);
    }

    @Test
    void parseInline_returnsTicketEnvelope() {
        SpecPackMarkdownParserController.ParseRequest request = new SpecPackMarkdownParserController.ParseRequest("""
                # Spec Pack
                **Ticket ID**: PARSER-SPEC-PACK

                ## 1. Context / Purpose
                Context body.
                """, "docs/changes/PARSER-SPEC-PACK/spec-pack.md", "draft");

        Map<String, Object> response = controller.parseInline(request);

        assertThat(response.get("ticketId")).isEqualTo("PARSER-SPEC-PACK");
        assertThat(response.get("parseMode")).isEqualTo("draft");
        assertThat(response.get("parsedSummary")).isInstanceOf(Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> castList(Object value) {
        assertThat(value).isInstanceOf(List.class);
        return (List<Object>) value;
    }
}
