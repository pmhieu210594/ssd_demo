package com.sdd.platform.domain.service;

import com.sdd.platform.domain.service.markdown.reviewchecklist.ReviewChecklistMarkdownParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewChecklistMarkdownParserTest {

    private final ReviewChecklistMarkdownParser parser = new ReviewChecklistMarkdownParser();

    @Test
    void parse_validChecklist_coversCoreEnvelope() {
        String markdown = """
                # Danh sách kiểm tra review — PARSER-REVIEW-CHECKLIST (Template)
                - **Ticket:** PARSER-REVIEW-CHECKLIST
                - **Trạng thái:** Draft
                - **Tạo ngày:** 2026-08-25 10:00
                - **Cập nhật ngày:** 2026-08-25 10:15

                ## 1. Spec / AC
                core alignment verified

                ## 2. Thiết kế / Phụ thuộc
                dependency design reviewed

                ## 3. Bảo mật
                | # | Hạng mục | Mức độ | Trạng thái |
                | --- | --- | --- | --- |
                | RC-11 | Input được kiểm tra/làm sạch | Blocker | [x] |
                | RC-12 | Phân quyền được enforce | Blocker | [x] |

                ## 4. Hiệu năng
                performance review items

                ## 5. Tương thích
                compatibility review items

                ## 6. Logging / Audit
                logging and audit review items

                ## 7. Xử lý lỗi
                error handling review items

                ## 8. Kiểm thử
                | # | Hạng mục | Mức độ | Trạng thái |
                | --- | --- | --- | --- |
                | RC-33 | Unit test bao phủ guard/limit | Major | [x] |
                | RC-34 | Unit test bao phủ business rule | Major | [x] |

                ## 9. Vận hành
                operation review items

                ## Bảng ánh xạ AC → Checklist items
                | # | AC | Các hạng mục checklist xác nhận |
                | --- | --- | --- |
                | 1 | AC-1 | RC-11, RC-33 |
                """;

        var parsed = parser.parse(markdown, "docs/changes/PARSER-REVIEW-CHECKLIST/review-checklist.md", "official");
        var summary = parsed.parsedSummary();

        assertThat(parsed.ticketId()).isEqualTo("PARSER-REVIEW-CHECKLIST");
        assertThat(parsed.parseMode()).isEqualTo("official");
        assertThat(parsed.artifactExists()).isTrue();
        assertThat(parsed.artifactStatus()).isEqualTo("present");
        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.headerMetadata())
                .containsEntry("ticket_id", "PARSER-REVIEW-CHECKLIST")
                .containsEntry("create_date", "2026-08-25 10:00")
                .containsEntry("update_date", "2026-08-25 10:15");
        assertThat(summary).containsEntry("ticket_id", "PARSER-REVIEW-CHECKLIST");
        assertThat(summary).containsEntry("security_review_present", true);
        assertThat(summary).containsEntry("security_review_checked", true);
        assertThat(summary).containsEntry("test_review_present", true);
        assertThat(summary).containsEntry("test_review_checked", true);
        assertThat(summary).containsEntry("all_checklist_sections_checked", false);
        assertThat(summary).containsEntry("checklist_total_item_count", 4);
        assertThat(summary).containsEntry("checklist_checked_item_count", 4);
        assertThat(summary).containsEntry("checklist_unchecked_item_count", 0);
        assertThat(summary).containsEntry("ac_checklist_mapping_present", true);
        assertThat(summary).containsEntry("ac_checklist_mapping_row_count", 1);
        assertThat(summary).containsEntry("ac_checklist_mapping_complete", true);
    }

    @Test
    void parse_reviewChecklist_marksSectionsUnchecked_whenAnyStatusRemainsOpen() {
        String markdown = """
                # Danh sách kiểm tra review — PARSER-REVIEW-CHECKLIST (Template)
                - **Ticket:** PARSER-REVIEW-CHECKLIST
                - **Trạng thái:** Draft
                - **Tạo ngày:** 2026-08-25 10:00
                - **Cập nhật ngày:** 2026-08-25 10:15

                ## 1. Spec / AC
                core alignment verified

                ## 2. Thiết kế / Phụ thuộc
                dependency design reviewed

                ## 3. Bảo mật
                | # | Hạng mục | Mức độ | Trạng thái |
                | --- | --- | --- | --- |
                | RC-11 | Input được kiểm tra/làm sạch | Blocker | [x] |
                | RC-12 | Phân quyền được enforce | Blocker | [ ] |

                ## 4. Hiệu năng
                performance review items

                ## 5. Tương thích
                compatibility review items

                ## 6. Logging / Audit
                logging and audit review items

                ## 7. Xử lý lỗi
                error handling review items

                ## 8. Kiểm thử
                | # | Hạng mục | Mức độ | Trạng thái |
                | --- | --- | --- | --- |
                | RC-33 | Unit test bao phủ guard/limit | Major | [x] |
                | RC-34 | Unit test bao phủ business rule | Major | [x] |

                ## 9. Vận hành
                operation review items

                ## Bảng ánh xạ AC → Checklist items
                | # | AC | Các hạng mục checklist xác nhận |
                | --- | --- | --- |
                | 1 | AC-1 | RC-11, RC-33 |
                """;

        var parsed = parser.parse(markdown, "docs/changes/PARSER-REVIEW-CHECKLIST/review-checklist.md", "official");
        var summary = parsed.parsedSummary();
        assertThat(parsed.parseStatus()).isEqualTo("OFFICIAL");
        assertThat(summary).containsEntry("security_review_checked", false);
        assertThat(summary).containsEntry("test_review_checked", true);
        assertThat(summary).containsEntry("checklist_total_item_count", 4);
        assertThat(summary).containsEntry("checklist_checked_item_count", 3);
        assertThat(summary).containsEntry("checklist_unchecked_item_count", 1);
        assertThat(summary).containsEntry("ac_checklist_mapping_complete", true);
        assertThat(summary.get("checklist_unchecked_severity_count"))
                .isEqualTo(java.util.Map.of("BLOCKER", 1));
    }

    @Test
    void parse_missingFields_generatesWarnings() {
        String markdown = """
                # Danh sách kiểm tra review — PARSER-REVIEW-CHECKLIST (Template)
                - **Ticket:** PARSER-REVIEW-CHECKLIST

                ## 1. Spec / AC
                OK
                """;

        var parsed = parser.parse(markdown, "docs/changes/PARSER-REVIEW-CHECKLIST/review-checklist.md");

        assertThat(parsed.requiredFieldsMissing()).isNotEmpty();
        assertThat(parsed.warnings()).extracting(ReviewChecklistMarkdownParser.ParsingIssue::code).contains("required_fields_missing");
        assertThat(parsed.parseStatus()).isEqualTo("PARTIAL");
    }

    @Test
    void parse_detectsPlaceholder() {
        String markdown = """
                # Danh sách kiểm tra review — PARSER-REVIEW-CHECKLIST (Template)
                - **Ticket:** PARSER-REVIEW-CHECKLIST

                ## 1. Spec / AC
                <TODO>
                """;

        var parsed = parser.parse(markdown, "docs/changes/PARSER-REVIEW-CHECKLIST/review-checklist.md");
        assertThat(parsed.placeholders()).isNotEmpty();
        assertThat(parsed.warnings()).extracting(ReviewChecklistMarkdownParser.ParsingIssue::code).contains("placeholder_detected");
    }

    @Test
    void parse_ticketId_inferredFromPath_whenMissingInContent() {
        var parsed = parser.parse("# Danh sách kiểm tra review\n## 1. Spec / AC\nOK", "docs/changes/PARSER-REVIEW-CHECKLIST/review-checklist.md");
        assertThat(parsed.ticketId()).isEqualTo("PARSER-REVIEW-CHECKLIST");
    }

    @Test
    void parse_emptyContent_resultsInMissingArtifact() {
        var parsed = parser.parse("", "docs/changes/PARSER-REVIEW-CHECKLIST/review-checklist.md");
        assertThat(parsed.artifactExists()).isFalse();
        assertThat(parsed.artifactStatus()).isEqualTo("missing");
    }
}

