package com.sdd.platform.domain.service;

import com.sdd.platform.domain.service.markdown.specpack.SpecPackMarkdownParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpecPackMarkdownParserTest {

    private final SpecPackMarkdownParser parser = new SpecPackMarkdownParser();

    @Test
    void parse_actualRepositorySpecPack_coversCoreEnvelopeAndCounts() throws Exception {
        var resource = SpecPackMarkdownParserTest.class.getClassLoader().getResource("test-fixtures/PARSER-SPEC-PACK/spec-pack.md");
        String markdown = Files.readString(Path.of(resource.toURI()));

        var parsed = parser.parse(markdown, "changes/PARSER-SPEC-PACK/spec-pack.md", "official");

        assertThat(parsed.ticketId()).isEqualTo("PARSER-SPEC-PACK");
        assertThat(parsed.parseMode()).isEqualTo("official");
        assertThat(parsed.artifactStatus()).isEqualTo("present");
        assertThat(parsed.artifactExists()).isTrue();
        assertThat(parsed.acceptanceCriteria()).hasSize(3);
        assertThat(parsed.acceptanceCriteria()).allMatch(SpecPackMarkdownParser.AcceptanceCriterion::validFormat);
        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.requiredFieldsMissing()).isEmpty();
        assertThat(parsed.parsedSummary()).containsEntry("ticket_id", "PARSER-SPEC-PACK");
        assertThat(parsed.parsedSummary()).containsEntry("open_issue_total_count", 2);
        assertThat(parsed.parsedSummary().get("open_issue_open_count")).isEqualTo(Map.of("P0", 1, "P1", 1));
    }

    @Test
    void parse_prefersFrontMatterTicketId_overPathAndHeader() {
        String markdown = """
                ---
                ticket_id: FRONT-MATTER-TICKET
                ---

                # Spec Pack
                **Ticket ID**: HEADER-TICKET
                """;

        var parsed = parser.parse(markdown, "docs/changes/PATH-TICKET/spec-pack.md", "official");

        assertThat(parsed.ticketId()).isEqualTo("FRONT-MATTER-TICKET");
        assertThat(parsed.frontMatter()).containsEntry("ticket_id", "FRONT-MATTER-TICKET");
        assertThat(parsed.headerMetadata()).containsEntry("ticket_id", "HEADER-TICKET");
    }

    @Test
    void parse_detectsPlaceholdersAndWarnings() {
        String markdown = """
                # Spec Pack
                **Ticket ID**: PARSER-SPEC-PACK

                ## 7. Acceptance Criteria
                | ID | Description | UT | IT | E2E | BB |
                |---|---|---|---|---|---|
                | BROKEN-1 | <...> | TODO | | | |
                """;

        var parsed = parser.parse(markdown, "docs/changes/PARSER-SPEC-PACK/spec-pack.md");

        assertThat(parsed.placeholders()).isNotEmpty();
        assertThat(parsed.warnings().stream().map(SpecPackMarkdownParser.ParsingIssue::code).toList())
                .contains("placeholder_detected", "required_fields_missing");
        assertThat(parsed.parseStatus()).isEqualTo("PARTIAL");
    }

    @Test
    void parse_treatsScopeAsPresentWhenAnyChildHasContent() {
        String markdown = """
                # Spec Pack
                **Ticket ID**: PARSER-SPEC-PACK

                ## 2. Scope

                ### 2.1 In Scope
                In scope content.
                """;

        var parsed = parser.parse(markdown, "docs/changes/PARSER-SPEC-PACK/spec-pack.md");
        assertThat(parsed.parseStatus()).isEqualTo("PARTIAL");
    }

    @Test
    void parse_countsOpenIssuesRowsByPriority_fromFixture() throws Exception {
        var resource = SpecPackMarkdownParserTest.class.getClassLoader().getResource("test-fixtures/PARSER-SPEC-PACK/spec-pack.md");
        String markdown = Files.readString(Path.of(resource.toURI()));

        var parsed = parser.parse(markdown, "docs/changes/PARSER-SPEC-PACK/spec-pack.md");

        assertThat(parsed.parsedSummary()).containsEntry("open_issue_total_count", 2);
        assertThat(parsed.parsedSummary().get("open_issue_open_count")).isEqualTo(Map.of("P0", 1, "P1", 1));
    }

    @Test
    void parse_normalizesLineEndings_beforeHashing() {
        String lfMarkdown = """
                # Spec Pack
                **Ticket ID**: PARSER-SPEC-PACK

                ## 1. Context
                Context.
                """;
        String crlfMarkdown = lfMarkdown.replace("\n", "\r\n");

        var lf = parser.parse(lfMarkdown, "docs/changes/PARSER-SPEC-PACK/spec-pack.md");
        var crlf = parser.parse(crlfMarkdown, "docs/changes/PARSER-SPEC-PACK/spec-pack.md");

        assertThat(lf.contentHash()).isEqualTo(crlf.contentHash());
        assertThat(lf.normalizedContent()).isEqualTo(crlf.normalizedContent());
    }
}
