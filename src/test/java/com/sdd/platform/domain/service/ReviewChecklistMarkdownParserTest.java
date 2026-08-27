package com.sdd.platform.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sdd.platform.domain.service.markdown.reviewchecklist.ReviewChecklistMarkdownParser;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewChecklistMarkdownParserTest {
    private ReviewChecklistMarkdownParser parser;

    @BeforeEach
    void setUp() {
        parser = new ReviewChecklistMarkdownParser();
    }

    @Test
    void should_parse_success_with_full_data() {
        String content = """
            ---
            ticket_id: TICKET-123
            ---
            
            # Security Review
            
            - [ ] validate auth
            - [x] check secret
            
            # Test
            
            - [ ] add unit test
        """;

        var result = parser.parse(content, "test.md", Map.of());

        assertThat(result.getTicketId()).isEqualTo("TICKET-123");
        assertThat(result.getChecklistItemCount()).isEqualTo(3);

        assertThat(result.isHasSecurityPerspective()).isTrue();
        assertThat(result.isHasTestPerspective()).isTrue();
        assertThat(result.getPerspectiveCount()).isEqualTo(2);

        assertThat(result.getParseStatus())
                .isEqualTo(ReviewChecklistMarkdownParser.ParserResultStatus.SUCCESS);

        assertThat(result.getWarnings()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getArtifactType()).isEqualTo("review_checklist");
        assertThat(result.getSourceHash()).isNotNull();
    }

    @Test
    void should_fail_when_content_empty() {
        var result = parser.parse("   ", "test.md", Map.of());

        assertThat(result.getParseStatus())
                .isEqualTo(ReviewChecklistMarkdownParser.ParserResultStatus.FAIL);

        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors()).contains("Empty content");
    }

    @Test
    void should_warning_when_no_checklist() {
        String content = """
            # Title
            
            Just text
        """;

        var result = parser.parse(content, "test.md", Map.of());

        assertThat(result.getChecklistItemCount()).isZero();

        assertThat(result.getParseStatus())
                .isEqualTo(ReviewChecklistMarkdownParser.ParserResultStatus.WARNING);

        assertThat(result.getWarnings()).contains("No checklist items found");
    }

    @Test
    void should_extract_ticket_from_metadata_param() {
        String content = """
            # Review
            
            - [ ] something
        """;

        var result = parser.parse(content, "test.md",
                Map.of("ticket_id", "TICKET-999"));

        assertThat(result.getTicketId()).isEqualTo("TICKET-999");
    }

    @Test
    void should_extract_ticket_from_content() {
        String content = """
            # Review
            
            Ref: TICKET-456
            
            - [ ] item
        """;

        var result = parser.parse(content, "test.md", Map.of());

        assertThat(result.getTicketId()).isEqualTo("TICKET-456");
    }

    @Test
    void should_warning_when_missing_ticket() {
        String content = """
            # Review
            
            - [ ] item
        """;

        var result = parser.parse(content, "test.md", Map.of());

        assertThat(result.getTicketId()).isNull();

        assertThat(result.getParseStatus())
                .isEqualTo(ReviewChecklistMarkdownParser.ParserResultStatus.WARNING);

        assertThat(result.getWarnings()).contains("Missing ticket_id");
    }

    @Test
    void should_count_checklist_variants() {
        String content = """
            - item 1
            * item 2
            - [ ] item 3
            * [x] item 4
        """;

        var result = parser.parse(content, "test.md", Map.of());

        assertThat(result.getChecklistItemCount()).isEqualTo(4);
    }

    @Test
    void should_detect_all_perspectives() {
        String content = """
            # Security
            check auth
            
            # Performance
            reduce latency
            
            # Test
            add qa test
        """;

        var result = parser.parse(content, "test.md", Map.of());

        assertThat(result.isHasSecurityPerspective()).isTrue();
        assertThat(result.isHasPerformancePerspective()).isTrue();
        assertThat(result.isHasTestPerspective()).isTrue();
        assertThat(result.getPerspectiveCount()).isEqualTo(3);
    }

    @Test
    void should_detect_perspective_case_insensitive() {
        String content = """
            # SECURITY
            
            AUTH check
        """;

        var result = parser.parse(content, "test.md", Map.of());

        assertThat(result.isHasSecurityPerspective()).isTrue();
    }

    @Test
    void should_not_detect_any_perspective() {
        String content = """
            # General
            
            - [ ] do something
        """;

        var result = parser.parse(content, "test.md", Map.of());

        assertThat(result.isHasSecurityPerspective()).isFalse();
        assertThat(result.isHasTestPerspective()).isFalse();
        assertThat(result.isHasPerformancePerspective()).isFalse();

        assertThat(result.getPerspectiveCount()).isZero();

        assertThat(result.getParseStatus())
                .isEqualTo(ReviewChecklistMarkdownParser.ParserResultStatus.WARNING);

        assertThat(result.getWarnings()).contains("No perspective detected");
    }


}
