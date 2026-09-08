package com.sdd.platform.web.rest;

import com.sdd.platform.domain.service.markdown.selfreview.SelfReviewMarkdownParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SelfReviewMarkdownParserControllerTest {

    private final SelfReviewMarkdownParserController controller = new SelfReviewMarkdownParserController(new SelfReviewMarkdownParser());

    @Test
    void parseFile_rejectsPathsOutsideSelfReviewScope() {
        SelfReviewMarkdownParserController.ParseFileRequest request = new SelfReviewMarkdownParserController.ParseFileRequest("D:\\temp\\notes.md", "draft");

        assertThatThrownBy(() -> controller.parseFile(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("changes/<TICKET>/self-review.md");
    }

    @Test
    void parseFile_readsRepositoryFixture_andReturnsParsedEnvelope() throws Exception {
        Path fixturePath = Path.of("target", "test-fixtures", "changes", "PARSER-SELF-REVIEW", "self-review.md");
        Files.createDirectories(fixturePath.getParent());
        var resource = SelfReviewMarkdownParserControllerTest.class.getClassLoader()
                .getResource("test-fixtures/PARSER-SELF-REVIEW/self-review.md");
        Files.writeString(fixturePath, Files.readString(Path.of(resource.toURI())));

        Map<String, Object> response = controller.parseFile(new SelfReviewMarkdownParserController.ParseFileRequest(
                fixturePath.toString(),
                "official"));

        assertThat(response.get("filePath")).isEqualTo(fixturePath.toAbsolutePath().toString());
        Map<String, Object> result = castMap(response.get("result"));
        assertThat(result.get("ticketId")).isEqualTo("PARSER-SELF-REVIEW");
        assertThat(result.get("parseStatus")).isEqualTo("PARTIAL");
        assertThat(result.get("artifactStatus")).isEqualTo("present");
        assertThat(result.get("artifactExists")).isEqualTo(Boolean.TRUE);
        assertThat(castList(result.get("tables"))).hasSize(12);
        assertThat(castList(result.get("freeTextSections"))).isEmpty();
        assertThat(result.get("finalVerdict")).isNull();
        assertThat(castMap(result.get("parsedSummary")))
                .doesNotContainKey("final_verdict")
                .containsEntry("parse_status", "PARTIAL")
                .containsKey("warning_count");
    }

    @Test
    void parseInline_returnsTicketEnvelope() {
        SelfReviewMarkdownParserController.ParseRequest request = new SelfReviewMarkdownParserController.ParseRequest("""
                # Self Review
                **Ticket ID**: PARSER-SELF-REVIEW

                ## 1. Implementation Summary
                Summary.

                ## 11. Final Self-Verdict
                PASS
                """, "docs/changes/PARSER-SELF-REVIEW/self-review.md", "draft");

        Map<String, Object> response = controller.parseInline(request);

        assertThat(response.get("ticketId")).isEqualTo("PARSER-SELF-REVIEW");
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
