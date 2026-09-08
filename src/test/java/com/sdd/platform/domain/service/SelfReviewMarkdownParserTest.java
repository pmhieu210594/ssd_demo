package com.sdd.platform.domain.service;

import com.sdd.platform.domain.service.markdown.selfreview.SelfReviewMarkdownParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SelfReviewMarkdownParserTest {
    private final SelfReviewMarkdownParser parser = new SelfReviewMarkdownParser();

    @Test
    void currentTemplate_doesNotReportAcHierarchyAsMissing() {
        var parsed = parser.parse(template(), "docs/changes/PARSER-SELF-REVIEW/self-review.md");
        assertThat(parsed.ticketId()).isEqualTo("PARSER-SELF-REVIEW");
        assertThat(parsed.requiredSectionsMissing()).isEmpty();
        assertThat(parsed.parsedSummary()).doesNotContainKey("final_verdict_valid");
    }

    @Test
    void acSection_withoutAnyAcGroupChild_isReportedMissing() {
        var parsed = parser.parse(template().replace("""
                ### 1.1. AC_GROUP_1 — spec SPEC
                | AC | Trạng thái | Evidence |
                |---|---|---|
                | AC-1 | PASS | evidence |
                """, ""), "docs/changes/PARSER-SELF-REVIEW/self-review.md");
        assertThat(parsed.requiredSectionsMissing()).contains("section:TRẠNG_THÁI_HOÀN_THÀNH_AC");
    }

    @Test
    void missingLeafSection_isReportedWithSectionPrefix() {
        var parsed = parser.parse(template().replace("### Build\npass", ""),
                "docs/changes/PARSER-SELF-REVIEW/self-review.md");
        assertThat(parsed.requiredSectionsMissing()).contains("section:BUILD");
    }

    @Test
    void removedLegacySections_areIgnored() {
        var parsed = parser.parse(template() + "\n## AI-generated predictions\nIgnored\n"
                + "## Items reviewed by humans\nIgnored\n## Final Self-Verdict\nPASS\n",
                "docs/changes/PARSER-SELF-REVIEW/self-review.md");
        assertThat(parsed.warnings()).extracting(SelfReviewMarkdownParser.ParsingIssue::code)
                .doesNotContain("verdict_missing");
        assertThat(parsed.parsedSummary()).doesNotContainKey("final_verdict");
    }

    @Test
    void acGroupTableEmptiness_isNotSeparatelyDetected() {
        var fullParsed = parser.parse(template(), "docs/changes/PARSER-SELF-REVIEW/self-review.md");
        var emptyAcParsed = parser.parse(template().replace("| AC-1 | PASS | evidence |\n", ""),
                "docs/changes/PARSER-SELF-REVIEW/self-review.md");
        assertThat(emptyAcParsed.requiredSectionsMissing()).isEqualTo(fullParsed.requiredSectionsMissing());
    }

    @Test
    void manualE2E_isOptional() {
        var parsed = parser.parse(template(), "docs/changes/PARSER-SELF-REVIEW/self-review.md");
        assertThat(parsed.requiredSectionsMissing()).noneMatch(value -> value.contains("MANUAL_E2E"));
    }

    @Test
    void ticketId_inferredFromFrontMatter() {
        String content = """
                ---
                ticket_id: FM-TICKET-1
                ---
                # Self Review
                ## Lint
                pass
                """;
        var parsed = parser.parse(content);
        assertThat(parsed.ticketId()).isEqualTo("FM-TICKET-1");
    }

    @Test
    void ticketId_inferredFromSelfReviewSectionBody() {
        String content = """
                # Self Review
                Some notes here.
                Ticket: TXT-TICKET-1
                """;
        var parsed = parser.parse(content);
        assertThat(parsed.ticketId()).isEqualTo("TXT-TICKET-1");
    }

    @Test
    void ticketId_missing_emitsWarning() {
        String content = """
                # Self Review
                No ticket information anywhere in this document.
                """;
        var parsed = parser.parse(content);
        assertThat(parsed.ticketId()).isNull();
        assertThat(parsed.warnings()).extracting(SelfReviewMarkdownParser.ParsingIssue::code)
                .contains("ticket_id_missing");
    }

    @Test
    void placeholderToken_isDetectedAndReported() {
        String content = """
                ## Lint
                TBD
                """;
        var parsed = parser.parse(content, "docs/changes/PARSER-SELF-REVIEW/self-review.md");
        assertThat(parsed.warnings()).extracting(SelfReviewMarkdownParser.ParsingIssue::code)
                .contains("placeholder_detected");
    }

    @Test
    void sectionOrderDeviation_isReported() {
        String content = """
                ## Build
                pass
                ## Lint
                pass
                """;
        var parsed = parser.parse(content, "docs/changes/PARSER-SELF-REVIEW/self-review.md");
        assertThat(parsed.warnings()).extracting(SelfReviewMarkdownParser.ParsingIssue::code)
                .contains("section_order_deviation");
    }

    @Test
    void nestedSubsectionDepth_exceeded_isReported() {
        String content = """
                ## Lint
                #### Too deep
                text
                """;
        var parsed = parser.parse(content, "docs/changes/PARSER-SELF-REVIEW/self-review.md");
        assertThat(parsed.warnings()).extracting(SelfReviewMarkdownParser.ParsingIssue::code)
                .contains("nested_subsection_depth_exceeded");
    }

    @Test
    void blankContent_producesMarkdownEmptyError_andMissingStatus() {
        var parsed = parser.parse("   \n\n  ", "docs/changes/PARSER-SELF-REVIEW/self-review.md");
        assertThat(parsed.errors()).extracting(SelfReviewMarkdownParser.ParsingIssue::code)
                .contains("markdown_empty");
        assertThat(parsed.artifactStatus()).isEqualTo("missing");
        assertThat(parsed.artifactExists()).isFalse();
        assertThat(parsed.parseStatus()).isEqualTo("FAILED");
    }

    @Test
    void officialParseMode_withNoIssues_yieldsOfficialStatus() {
        String content = """
                # Random Doc
                Just some unstructured notes.
                """;
        var parsed = parser.parse(content, "docs/changes/PARSER-SELF-REVIEW/self-review.md", "official");
        assertThat(parsed.warnings()).isEmpty();
        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.parseStatus()).isEqualTo("OFFICIAL");
    }

    @Test
    void tableSection_missingTableEntirely_reportsTableInvalid() {
        String content = """
                ## Các hạng mục checklist (từ `review-checklist.md`)
                Some plain text, no table here.
                """;
        var parsed = parser.parse(content, "docs/changes/PARSER-SELF-REVIEW/self-review.md");
        assertThat(parsed.requiredSectionsMissing())
                .contains("section:CÁC_HẠNG_MỤC_CHECKLIST_TỪ_REVIEW_CHECKLIST_MD table");
        assertThat(parsed.warnings()).extracting(SelfReviewMarkdownParser.ParsingIssue::code)
                .contains("table_invalid");
    }

    @Test
    void tableSection_emptyRows_reportsTableEmpty() {
        String content = """
                ## Known risks
                | # | Risk | Impact | Mitigation |
                |---|---|---|---|
                """;
        var parsed = parser.parse(content, "docs/changes/PARSER-SELF-REVIEW/self-review.md");
        assertThat(parsed.requiredSectionsMissing()).contains("section:KNOWN_RISKS");
        assertThat(parsed.warnings()).extracting(SelfReviewMarkdownParser.ParsingIssue::code)
                .contains("table_empty");
    }

    @Test
    void parentHierarchy_withoutAnyChildOrBody_reportsParentMissing() {
        String content = """
                ## Các lệnh đã chạy
                ## Lint
                pass
                """;
        var parsed = parser.parse(content, "docs/changes/PARSER-SELF-REVIEW/self-review.md");
        assertThat(parsed.requiredSectionsMissing()).contains("section:CÁC_LỆNH_ĐÃ_CHẠY");
    }

    // @Test
    // void exceptionRecord_validRows_areExtracted() {
    //     String content = """
    //             ## Exception Record
    //             | Exception Type | Reason | Alternative Check | Approved | Approved By Role | Expiry Date | Follow Up Status | Status |
    //             |---|---|---|---|---|---|---|---|
    //             | Manual Skip | reason text | check X | Yes | QA Lead | 2026-12-31 | | Approved |
    //             """;
    //     var parsed = parser.parse(content, "docs/changes/PARSER-SELF-REVIEW/self-review.md");
    //     assertThat(parsed.exceptionRecords()).hasSize(1);
    //     var exceptionRecord = parsed.exceptionRecords().get(0);
    //     assertThat(exceptionRecord.exceptionType()).isEqualTo("Manual Skip");
    //     assertThat(exceptionRecord.reasonPresent()).isTrue();
    //     assertThat(exceptionRecord.reason()).isEqualTo("reason text");
    //     assertThat(exceptionRecord.alternativeCheck()).isEqualTo("check X");
    //     assertThat(exceptionRecord.approved()).isTrue();
    //     assertThat(exceptionRecord.approvedByRoleName()).isEqualTo("QA Lead");
    //     assertThat(exceptionRecord.expiryDate()).isEqualTo("2026-12-31");
    //     assertThat(exceptionRecord.followUpStatus()).isEqualTo("OPEN");
    //     assertThat(exceptionRecord.status()).isEqualTo("Approved");
    // }

    // @Test
    // void exceptionRecord_presentButEmpty_emitsWarning() {
    //     String content = """
    //             ## Exception Record
    //             | Exception Type | Reason | Alternative Check | Approved | Approved By Role | Expiry Date | Follow Up Status | Status |
    //             |---|---|---|---|---|---|---|---|
    //             """;
    //     var parsed = parser.parse(content, "docs/changes/PARSER-SELF-REVIEW/self-review.md");
    //     assertThat(parsed.exceptionRecords()).isEmpty();
    //     assertThat(parsed.warnings()).extracting(SelfReviewMarkdownParser.ParsingIssue::code)
    //             .contains("exception_table_empty");
    // }

    // @Test
    // void exceptionRecord_absent_returnsEmptyList() {
    //     var parsed = parser.parse(template(), "docs/changes/PARSER-SELF-REVIEW/self-review.md");
    //     assertThat(parsed.exceptionRecords()).isEmpty();
    //     assertThat(parsed.warnings()).extracting(SelfReviewMarkdownParser.ParsingIssue::code)
    //             .doesNotContain("exception_table_empty");
    // }

    @Test
    void hasSection_matchesByAliasNormalization() {
        String content = """
                ## Known risks
                | # | Risk | Impact | Mitigation |
                |---|---|---|---|
                | 1 | risk | Low | monitor |
                """;
        var parsed = parser.parse(content, "docs/changes/PARSER-SELF-REVIEW/self-review.md");
        assertThat(parser.hasSection(parsed, "known-risks")).isTrue();
        assertThat(parser.hasSection(parsed, "known/risks")).isTrue();
        assertThat(parser.hasSection(parsed, "not-a-real-section")).isFalse();
    }

    @Test
    void isRequiredSectionKey_and_isTableSectionKey_staticHelpers() {
        assertThat(SelfReviewMarkdownParser.isRequiredSectionKey("lint")).isTrue();
        assertThat(SelfReviewMarkdownParser.isRequiredSectionKey("not-a-section")).isFalse();
        assertThat(SelfReviewMarkdownParser.isRequiredSectionKey(null)).isFalse();
        assertThat(SelfReviewMarkdownParser.isTableSectionKey("known risks")).isTrue();
        assertThat(SelfReviewMarkdownParser.isTableSectionKey(null)).isFalse();
        assertThat(SelfReviewMarkdownParser.isTableSectionKey("lint")).isFalse();

        assertThat(SelfReviewMarkdownParser.requiredSectionKeys()).hasSize(14);
        assertThat(SelfReviewMarkdownParser.requiredPersistenceSectionKeys())
                .isEqualTo(SelfReviewMarkdownParser.requiredSectionKeys());
        assertThat(SelfReviewMarkdownParser.tableSectionKeys()).contains(
                "CÁC_HẠNG_MỤC_CHECKLIST_TỪ_REVIEW_CHECKLIST_MD",
                "TỔNG_QUAN_DIFF",
                "CONFIRMATIONS_CUỐI_CÙNG",
                "MANUAL_E2E",
                "KNOWN_RISKS",
                "NOT_HANDLED_YET",
                "REMAINING_ISSUES_NỢ_KỸ_THUẬT");
    }

    @Test
    void parsedSummary_reflectsActualCounts() {
        String content = """
                ## Lint
                pass
                ## Type-check
                pass
                """;
        var parsed = parser.parse(content, "docs/changes/PARSER-SELF-REVIEW/self-review.md");
        assertThat(parsed.parsedSummary())
                .containsEntry("section_count", 2)
                .containsEntry("table_count", 0)
                .containsEntry("warning_count", 0)
                .containsEntry("error_count", 0)
                .containsEntry("placeholder_count", 0)
                .containsEntry("required_sections_missing_count", 12)
                .containsEntry("has_missing_required_sections", true)
                .containsEntry("has_review_checklist", false);
    }

    @Test
    void openIssuesTuImplPlan_emptyTable_isReportedAsTableEmpty() {
        // REQUIRED_SECTION_KEYS and TABLE_SECTION_KEYS both use
        // "OPEN_ISSUES_TỪ_IMPL_PLAN_VẪN_CÒN" (underscore), so a table with no data
        // rows in this section is correctly detected as empty.
        String content = """
                ## Open Issues từ impl-plan vẫn còn
                | # | OI ID | Description | Status |
                |---|---|---|---|
                """;
        var parsed = parser.parse(content, "docs/changes/PARSER-SELF-REVIEW/self-review.md");
        assertThat(parsed.requiredSectionsMissing())
                .contains("section:OPEN_ISSUES_TỪ_IMPL_PLAN_VẪN_CÒN");
        assertThat(parsed.warnings()).extracting(SelfReviewMarkdownParser.ParsingIssue::code)
                .contains("table_empty");
    }

    private String template() {
        return """
                # Tự review — [CHANGE_ID]
                **Ticket:** PARSER-SELF-REVIEW
                ## 1. Trạng thái hoàn thành AC
                ### 1.1. AC_GROUP_1 — spec SPEC
                | AC | Trạng thái | Evidence |
                |---|---|---|
                | AC-1 | PASS | evidence |
                ## 2. Các hạng mục checklist (từ `review-checklist.md`)
                | RC# | Trạng thái | Evidence |
                |---|---|---|
                | RC-01 | [x] | checked |
                ## 3. Các lệnh đã chạy
                ### Lint
                pass
                ### Type-check
                pass
                ### Unit test
                pass
                ### Build
                pass
                ## 4. Tổng quan diff
                | Loại | File | Vai trò |
                |---|---|---|
                | Sửa | src/App.java | implementation |
                ## 5. Rủi ro đã biết / Chưa bao phủ / Công việc còn lại
                ### Known risks
                | # | Risk | Impact | Mitigation |
                |---|---|---|---|
                | 1 | risk | Low | monitor |
                ### Not handled yet
                | # | Item | Reason | Action |
                |---|---|---|---|
                | 1 | item | reason | action |
                ### Remaining issues / nợ kỹ thuật
                | # | Issue | AC/RC | Action |
                |---|---|---|---|
                | 1 | issue | AC-1 | action |
                ### Open Issues từ impl-plan vẫn còn
                | # | OI ID | Description | Status |
                |---|---|---|---|
                | 1 | IMPL-OI-01 | issue | Open |
                ## 6. Confirmations cuối cùng
                | # | Confirmation | Trạng thái |
                |---|---|---|
                | 1 | confirmed | [x] |
                """;
    }
}
