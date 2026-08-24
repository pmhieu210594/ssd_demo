package com.sdd.platform.web.rest;

import com.sdd.platform.domain.service.markdown.reviewchecklist.ReviewChecklistMarkdownParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewChecklistMarkdownParserControllerTest {

    private final ReviewChecklistMarkdownParserController controller =
            new ReviewChecklistMarkdownParserController(new ReviewChecklistMarkdownParser());

    @Test
    void parseFile_rejectsPathsOutsideReviewChecklistScope() {
        ReviewChecklistMarkdownParserController.ParseFileRequest request =
                new ReviewChecklistMarkdownParserController.ParseFileRequest("D:\\temp\\notes.md", "draft");

        assertThatThrownBy(() -> controller.parseFile(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("review-checklist.md");
    }

    @Test
    void parseFile_readsRepositoryFixture_andReturnsParsedEnvelope() throws Exception {
        Path fixturePath = Path.of("target", "test-fixtures", "changes", "PARSER-REVIEW-CHECKLIST", "review-checklist.md");
        Files.createDirectories(fixturePath.getParent());

        String markdown = """
                # Review Checklist
                **Ticket ID**: PARSER-REVIEW-CHECKLIST

                ## Security
                Security content.

                ## Test
                Test content.

                ## Performance
                Performance content.
                """;

        Files.writeString(fixturePath, markdown);

        Map<String, Object> response = controller.parseFile(
                new ReviewChecklistMarkdownParserController.ParseFileRequest(
                        fixturePath.toString(),
                        "official"
                )
        );

        assertThat(response.get("filePath")).isEqualTo(fixturePath.toAbsolutePath().toString());

        Map<String, Object> result = castMap(response.get("result"));
        assertThat(result.get("ticketId")).isEqualTo("PARSER-REVIEW-CHECKLIST");
        assertThat(result.get("parseStatus")).isIn("OFFICIAL", "PARTIAL");
        assertThat(result.get("artifactStatus")).isEqualTo("present");
        assertThat(result.get("artifactExists")).isEqualTo(Boolean.TRUE);

        assertThat(castMap(result.get("sections")))
                .containsKeys("SECURITY", "TEST", "PERFORMANCE");

        assertThat(castList(result.get("errors"))).isEmpty();

        List<Object> warnings = castList(result.get("warnings"));
        assertThat(warnings).isNotNull();

        Map<String, Object> summary = castMap(result.get("parsedSummary"));

        assertThat(summary)
                .containsEntry("security_review_present", false)
                .containsEntry("test_review_present", false);
            }

    @Test
    void parseInline_returnsTicketEnvelope() {
        ReviewChecklistMarkdownParserController.ParseRequest request =
                new ReviewChecklistMarkdownParserController.ParseRequest("""
                        # Review Checklist
                        **Ticket ID**: PARSER-REVIEW-CHECKLIST

                        ## Security
                        OK
                        """,
                        "docs/changes/PARSER-REVIEW-CHECKLIST/review-checklist.md",
                        "draft");

        Map<String, Object> response = controller.parseInline(request);

        assertThat(response.get("ticketId")).isEqualTo("PARSER-REVIEW-CHECKLIST");
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