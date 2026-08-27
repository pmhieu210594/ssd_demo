package com.sdd.platform.domain.service;

import com.sdd.platform.domain.service.markdown.specpack.SpecPackMarkdownParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SpecPackMarkdownParserTest {

    private final SpecPackMarkdownParser parser = new SpecPackMarkdownParser();

    @Test
    void parse_actualRepositorySpecPack_coversCoreEnvelopeAndCounts() throws Exception {
        var resource = SpecPackMarkdownParserTest.class.getClassLoader()
                .getResource("test-fixtures/PARSER-SPEC-PACK/spec-pack.md");
        String markdown = Files.readString(Path.of(resource.toURI()));

        SpecPackMarkdownParser.ParsedArtifact parsed = parser.parse(
                markdown,
                "changes/PARSER-SPEC-PACK/spec-pack.md",
                "official");

        assertThat(parsed.ticketId()).isEqualTo("PARSER-SPEC-PACK");
        assertThat(parsed.parseMode()).isEqualTo("official");
        assertThat(parsed.parseStatus()).isEqualTo("PARTIAL");
        assertThat(parsed.artifactStatus()).isEqualTo("present");
        assertThat(parsed.artifactExists()).isTrue();
        assertThat(parsed.sections()).containsKeys(
                "CONTEXT_PURPOSE",
                "SCOPE",
                "DETAILED_SPECIFICATION",
                "ACCEPTANCE_CRITERIA",
                "OPEN_ISSUES");
        assertThat(parsed.tables()).hasSize(9);
        assertThat(parsed.acceptanceCriteria()).hasSize(10);
        assertThat(parsed.acceptanceCriteria()).allMatch(SpecPackMarkdownParser.AcceptanceCriterion::validFormat);
        assertThat(parsed.warnings()).extracting(SpecPackMarkdownParser.ParsingIssue::code)
                .contains("placeholder_detected");
        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.requiredFieldsMissing()).isEmpty();
        assertThat(parsed.parsedSummary())
                .containsEntry("ticket_id", "PARSER-SPEC-PACK")
                .containsEntry("parse_status", "PARTIAL")
                .containsEntry("section_count", 30)
                .containsEntry("table_count", 9)
                .containsEntry("ac_count", 10)
                .containsEntry("ac_valid_format_count", 10)
                .containsEntry("warning_count", 1)
                .containsEntry("error_count", 0)
                .containsEntry("placeholder_count", 6)
                .containsEntry("has_missing_required_sections", false)
                .containsEntry("has_open_issue_detected", true)
                .containsEntry("has_traceability_detected", true);
    }

    @Test
    void parse_prefersFrontMatterTicketId_overPathAndHeader() {
        String markdown = """
                ---
                ticket_id: FRONT-MATTER-TICKET
                schema_version: v1
                artifact_type: spec-pack
                ---

                # Spec Pack
                **Ticket ID**: HEADER-TICKET
                **Create date**: 2026-06-19

                ## 1. Context / Purpose
                Context body.

                ## 7. Acceptance Criteria
                | ACID | description | testable? | notes |
                |---|---|---|---|
                | AC-HEADER-TICKET-1 | First AC | Yes | Good |
                """;

        SpecPackMarkdownParser.ParsedArtifact parsed = parser.parse(
                markdown,
                "docs/changes/PATH-TICKET/spec-pack.md",
                "official");

        assertThat(parsed.ticketId()).isEqualTo("FRONT-MATTER-TICKET");
        assertThat(parsed.frontMatter()).containsEntry("ticket_id", "FRONT-MATTER-TICKET");
        assertThat(parsed.headerMetadata()).containsEntry("ticket_id", "HEADER-TICKET");
        assertThat(parsed.acceptanceCriteria()).hasSize(1);
        assertThat(parsed.acceptanceCriteria().getFirst().id()).isEqualTo("AC-HEADER-TICKET-1");
        assertThat(parsed.parseStatus()).isEqualTo("PARTIAL");
        assertThat(parsed.warnings()).allMatch(issue -> issue.code() != null && !issue.code().isBlank());
    }

    @Test
    void parse_detectsPlaceholdersAndInvalidAcceptanceCriteriaFormat() {
        String markdown = """
                # Spec Pack
                **Ticket ID**: PARSER-SPEC-PACK

                ## 1. Context / Purpose
                ---

                ## 7. Acceptance Criteria
                | ACID | description | testable? | notes |
                |---|---|---|---|
                | BROKEN-1 | <...> | TODO | N/A |
                """;

        SpecPackMarkdownParser.ParsedArtifact parsed = parser.parse(markdown, "docs/changes/PARSER-SPEC-PACK/spec-pack.md");

        assertThat(parsed.placeholders()).isNotEmpty();
        assertThat(parsed.requiredFieldsMissing()).contains("section:SCOPE");
        assertThat(parsed.acceptanceCriteria()).hasSize(1);
        assertThat(parsed.acceptanceCriteria().getFirst().validFormat()).isFalse();
        assertThat(parsed.warnings().stream().map(SpecPackMarkdownParser.ParsingIssue::code).toList())
                .contains("placeholder_detected", "ac_format_invalid", "required_fields_missing");
        assertThat(parsed.parseStatus()).isEqualTo("PARTIAL");
    }

    @Test
    void parse_treatsScopeAsPresentWhenAnyChildHasContent() {
        String markdown = """
                # Spec Pack
                **Ticket ID**: PARSER-SPEC-PACK

                ## 2. Scope

                ### 2.1. Within range
                The ticket is inside the target scope.

                ### 2.2. Out of range
                """;

        SpecPackMarkdownParser.ParsedArtifact parsed = parser.parse(markdown, "docs/changes/PARSER-SPEC-PACK/spec-pack.md");

        assertThat(parsed.requiredFieldsMissing()).doesNotContain("section:SCOPE");
        assertThat(parsed.parseStatus()).isEqualTo("PARTIAL");
    }

    @Test
    void parse_treatsScopeAsPresentWhenOnlyHeaderExists() {
        String markdown = """
                # Spec Pack
                **Ticket ID**: PARSER-SPEC-PACK

                ## 2. Scope
                """;

        SpecPackMarkdownParser.ParsedArtifact parsed = parser.parse(markdown, "docs/changes/PARSER-SPEC-PACK/spec-pack.md");

        assertThat(parsed.requiredFieldsMissing()).doesNotContain("section:SCOPE");
    }

    @Test
    void parse_countsOpenIssuesRowsByStatus() {
        String markdown = """
                # Spec Pack
                **Ticket ID**: PARSER-SPEC-PACK

                ## 18. Open Issues
                | ID | issue | impact | owner | status |
                |---|---|---|---|---|
                | OI-1 | Something remains open | Medium | QA | Open |
                | OI-2 | Another item | Low | Dev | Closed |
                """;

        SpecPackMarkdownParser.ParsedArtifact parsed = parser.parse(markdown, "docs/changes/PARSER-SPEC-PACK/spec-pack.md");

        assertThat(parsed.parsedSummary()).containsEntry("open_issue_total_count", 2);
        assertThat(parsed.parsedSummary()).containsEntry("open_issue_open_count", 1);
        assertThat(parsed.parsedSummary()).containsEntry("open_issue_closed_count", 1);
    }

    @Test
    void parse_normalizesLineEndings_beforeHashing() {
        String lfMarkdown = """
                # Spec Pack
                **Ticket ID**: PARSER-SPEC-PACK

                ## 1. Context / Purpose
                Context body.
                """;
        String crlfMarkdown = lfMarkdown.replace("\n", "\r\n");

        SpecPackMarkdownParser.ParsedArtifact lf = parser.parse(lfMarkdown, "docs/changes/PARSER-SPEC-PACK/spec-pack.md");
        SpecPackMarkdownParser.ParsedArtifact crlf = parser.parse(crlfMarkdown, "docs/changes/PARSER-SPEC-PACK/spec-pack.md");

        assertThat(lf.contentHash()).isEqualTo(crlf.contentHash());
        assertThat(lf.normalizedContent()).isEqualTo(crlf.normalizedContent());
    }
}
