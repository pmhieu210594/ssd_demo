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
                # Danh sách kiểm tra review — PARSER-REVIEW-CHECKLIST (Template)
                - **Ticket:** PARSER-REVIEW-CHECKLIST
                - **Trạng thái:** Draft
                - **Tạo ngày:** 2026-08-25 10:00
                - **Cập nhật ngày:** 2026-08-25 10:15

                ## 1. Spec / AC
                | # | Hạng mục | Mức độ | Trạng thái |
                | --- | --- | --- | --- |
                | RC-01 | Spec aligned | Blocker | [x] |

                ## 2. Thiết kế / Phụ thuộc
                | # | Hạng mục | Mức độ | Trạng thái |
                | --- | --- | --- | --- |
                | RC-05 | Design aligned | Major | [x] |

                ## 3. Bảo mật
                | # | Hạng mục | Mức độ | Trạng thái |
                | --- | --- | --- | --- |
                | RC-11 | Security validated | Blocker | [x] |

                ## 4. Hiệu năng
                | # | Hạng mục | Mức độ | Trạng thái |
                | --- | --- | --- | --- |
                | RC-16 | Performance validated | Major | [x] |

                ## 5. Tương thích
                | # | Hạng mục | Mức độ | Trạng thái |
                | --- | --- | --- | --- |
                | RC-20 | Compatibility validated | Blocker | [x] |

                ## 6. Logging / Audit
                | # | Hạng mục | Mức độ | Trạng thái |
                | --- | --- | --- | --- |
                | RC-24 | Audit validated | Major | [x] |

                ## 7. Xử lý lỗi
                | # | Hạng mục | Mức độ | Trạng thái |
                | --- | --- | --- | --- |
                | RC-27 | Error handling validated | Major | [x] |

                ## 8. Kiểm thử
                | # | Hạng mục | Mức độ | Trạng thái |
                | --- | --- | --- | --- |
                | RC-33 | Test coverage validated | Major | [x] |

                ## 9. Vận hành
                | # | Hạng mục | Mức độ | Trạng thái |
                | --- | --- | --- | --- |
                | RC-40 | Operations validated | Major | [x] |

                ## Bảng ánh xạ AC → Checklist items
                | # | AC | Các hạng mục checklist xác nhận |
                | --- | --- | --- |
                | 1 | AC-1 | RC-01 |
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
        assertThat(result.get("parseStatus")).isEqualTo("OFFICIAL");
        assertThat(result.get("artifactStatus")).isEqualTo("present");
        assertThat(result.get("artifactExists")).isEqualTo(Boolean.TRUE);

        assertThat(castMap(result.get("sections")))
                .containsKeys("BẢO_MẬT", "KIỂM_THỬ", "HIỆU_NĂNG");

        assertThat(castList(result.get("errors"))).isEmpty();

        List<Object> warnings = castList(result.get("warnings"));
        assertThat(warnings).isNotNull();

        Map<String, Object> summary = castMap(result.get("parsedSummary"));

        assertThat(summary)
                .containsEntry("security_review_present", true)
                .containsEntry("test_review_present", true)
                .containsEntry("all_checklist_sections_checked", true)
                .containsEntry("checklist_total_item_count", 9)
                .containsEntry("checklist_checked_item_count", 9)
                .containsEntry("checklist_unchecked_item_count", 0)
                .containsEntry("ac_checklist_mapping_present", true)
                .containsEntry("ac_checklist_mapping_row_count", 1)
                .containsEntry("ac_checklist_mapping_complete", true);
    }

    @Test
    void parseInline_returnsTicketEnvelope() {
        ReviewChecklistMarkdownParserController.ParseRequest request =
                new ReviewChecklistMarkdownParserController.ParseRequest("""
                        # Danh sách kiểm tra review — PARSER-REVIEW-CHECKLIST (Template)
                        - **Ticket:** PARSER-REVIEW-CHECKLIST

                        ## 3. Bảo mật
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
