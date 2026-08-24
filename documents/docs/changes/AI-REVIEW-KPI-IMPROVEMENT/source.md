# Source Availability

**Ticket ID**: AI-REVIEW-KPI-IMPROVEMENT
**Create date**: 2026-08-18
**Author**: SDD Analyst (Claude)
**Update date**: 2026-08-18

| source | path | read_status | trust_level | owner | purpose | risk | action |
|---|---|---|---|---|---|---|---|
| Ticket rules | `docs/changes/AI-REVIEW-KPI-IMPROVEMENT/raw/rules.md` | Read (full) | Confirmed | Ticket author | Xác định path chuẩn cho `spec-pack.md`/`source.md`/`open-issues.md` và quy tắc dùng `_ticket-template` | Thấp | Đã áp dụng đúng path (không đặt trong `raw/`) |
| Raw requirement (đã chốt phương án) | `docs/changes/AI-REVIEW-KPI-IMPROVEMENT/raw/requirement.md` | Read (full) | Confirmed — user đã "chốt phương án" | Ticket author | Input chính cho spec-pack | File viết tên bảng cũ `tbl_fact_ai_review_stat`, không khớp tên chính thức `tbl_fact_ai_finding_stat` do user chỉ định sau đó | Spec-pack dùng tên bảng mới; ghi rõ mismatch này để tránh nhầm khi đọc lại `raw/requirement.md` |
| Sample input document | `docs/changes/AI-REVIEW-KPI-IMPROVEMENT/raw/ai-review.md` | Read (full, đọc lại §8 chi tiết) | Cao (bằng chứng thật) | — | Verify giả thuyết parse độc lập ngôn ngữ bằng dữ liệu thật | Đây là `ai-review.md` của ticket **PROMPT_TEMPLATE_REUSE_RATE**, đặt vào đây làm ví dụ mẫu — không phải tài liệu review của chính ticket AI-REVIEW-KPI-IMPROVEMENT | Chỉ dùng để kiểm chứng cấu trúc bảng/label, không dùng số liệu trong đó làm nghiệp vụ thật |
| Canonical ticket template | `docs/standards/templates/_ticket-template/ai-review.md` | Read (full) | Confirmed (đây là "contract" mọi `ai-review.md` phải theo) | — | Xác định đúng thứ tự 7 dòng bảng §8 và vị trí 5 metric mục tiêu (index 2-6) | Nếu template bị sửa đổi số dòng/thứ tự trong tương lai, vị trí hard-code trong spec sẽ sai | Ghi rõ dependency này trong Assumptions; cần review lại nếu template đổi |
| `GithubWebhookService.java` | `EDCAP_BE/src/main/java/.../application/usecase/ingestion/GithubWebhookService.java` | Read (full 608 dòng, đọc lại 2 đoạn trọng tâm) | Confirmed (source code) | — | Xác định chính xác điểm hook, các helper tái dùng được (`ticketKeyFromPath`, `isSkippableFetchError`, `fileNameOf`, `artifactScannerSourcePort.resolveRevision/listTree/readBlob`) | Thấp | Điểm hook đề xuất: **sau** khi `ticketScopes` được build (dòng ~293-296), khác vị trí `validateTemplateUsage` (dòng ~253-256, chạy **trước** khi có `ticketScopes`) |
| `MarkdownParserCore.java` | `.../domain/service/markdown/core/MarkdownParserCore.java` | Read (đọc phần `parse()`, `extractTables()`, `extractSections()`, `canonicalSectionKey()`) | Confirmed | — | Xác nhận `parse(content, path).tables()` trả về `MarkdownTable` có `sectionTitle` giữ nguyên text heading gốc (bao gồm số thứ tự) → filter theo `"8."` được | Thấp | Không cần sửa `MarkdownParserCore`; feature mới tự lọc/duyệt kết quả `tables()` |
| `TemplateUsageStatWriter.java`, `TemplateUsageStatJdbcAdapter.java`, `V510__add_template_usage_tracking.sql` | `EDCAP_BE/src/main/java/...ingestion/`, `db/migration/V510...` | Read (full) | Confirmed | — | Mẫu tham chiếu cho writer `@Transactional(REQUIRES_NEW)` best-effort | Pattern **increment counter** (`total_check_count += 1`) không phù hợp cho feature mới (feature mới cần snapshot ghi đè, không cộng dồn) | Chỉ tái dùng phần transaction-isolation pattern, không tái dùng phần SQL increment |
| `tbl_ticket_bug_metrics` stack (`V509__ticket_bug_metrics.sql`, `TicketBugMetricsService/Controller/Mapper/Model`) | `EDCAP_BE/src/main/...` | Read (full) | Confirmed | — | Mẫu DB gần đúng nhất: per-ticket, FK `project_id`+`repository_id`+`ticket_id`, soft-delete + audit columns | Đây là luồng CRUD người dùng nhập tay qua UI/API, không phải luồng ghi tự động từ webhook | Dùng làm baseline cột FK + naming, nhưng **không** copy y nguyên toàn bộ audit/soft-delete/API tạo-sửa-xoá thủ công (xem Assumption A-AIRKI-2) |
| PM Dashboard stack (`PmDashboardModels/Service/Controller/Dtos`, `PmDashboardRepositoryPort`, `PmDashboardJdbcAdapter.findTemplateUsage`) | `EDCAP_BE/src/main/java/...pmdashboard/`, `.../web/rest/PmDashboardController.java`, `.../web/dto/PmDashboardDtos.java` | Read (full) | Confirmed | — | Mẫu chính xác cho API mới: auth gate `requirePm`, công thức làm tròn rate (`TemplateUsageDto.from`), response shape | Thấp | Copy đúng pattern cho endpoint `ai-finding-stats` |
| FE: `PMDashboardPage.tsx`, `TemplateUsageByPhase.tsx`, `lib/api.ts` (namespace `pmDashboard`, interface `TemplateUsageByPhase`) | `EDCAP_FE/src/pages/pm-dashboard/...`, `EDCAP_FE/src/lib/api.ts` | Read (đủ đoạn liên quan) | Confirmed | — | Mẫu chính xác cho component + endpoint + test đi kèm (`TemplateUsageByPhase.test.tsx`, `PMDashboardPage.test.tsx`) | Thấp | Copy đúng pattern cho `AiFindingStatsByRepository` |
| `docs/standards/{database,api-contract,error-handling,logging,testing,security}.md` | `documents/docs/standards/*.md` | Read (full) | Confirmed (đánh dấu "Confirmed" trong chính các file) | — | Ràng buộc bắt buộc cho §9–§13 của spec-pack | Thấp | Áp dụng trực tiếp |
| `docs/architecture/*` (`repository-db-map.md`, `route-api-map.md`, `fe-be-contract-map.md`, `service-layer-map.md`, …) | `documents/docs/architecture/*.md` | Chưa đọc nội dung (chỉ liệt kê tên file) | Partial | — | Có thể đã ghi sẵn route/table map liên quan | Có thể spec-pack thiếu đối chiếu với map hiện có | Liệt kê ở "Unavailable / Partial Sources"; cần đối chiếu và cập nhật các map này **sau khi** implement, không phải trước |
| `GithubWebhookServiceTest.java` (33 test hiện có) | `EDCAP_BE/src/test/java/.../GithubWebhookServiceTest.java` | Tìm thấy, chỉ grep tên dòng `@Test`, **chưa đọc nội dung từng test** | Partial | — | Tránh trùng lặp/phá vỡ test hiện có khi thêm logic mới vào `handlePullRequest` | Có thể đã có sẵn mock/fixture pattern cần tái dùng chưa được nắm rõ | Bắt buộc đọc kỹ trước khi implement (impl-plan phase) |
| `PmDashboardServiceTest.java`, `PmDashboardJdbcAdapterFindTemplateUsageTest.java`, `PmDashboardControllerTest.java` | `EDCAP_BE/src/test/UnitTest/java/...` | Tìm thấy qua glob, **chưa đọc nội dung** | Partial | — | Tương tự trên, cho phần PM Dashboard API | Cùng rủi ro như trên | Đọc trước khi implement |
| DDL đầy đủ của `tbl_dim_ticket` / `tbl_dim_project` / `tbl_dim_repository` | `EDCAP_BE/src/main/resources/db/migration/V4__init_shema_v2.sql` (và các migration sau) | Chưa đọc trực tiếp — tên cột PK suy ra gián tiếp qua FK reference trong V509/V510 (`REFERENCES tbl_dim_project(project_id)`, …) | Partial (suy luận gián tiếp, nhưng dựa trên convention đã "Confirmed") | — | Xác định đúng tên cột FK cho bảng mới | Rủi ro thấp vì convention đã confirmed trong `database.md`, nhưng chưa verify 100% bằng cách đọc DDL gốc | Verify lại khi viết migration thật (impl-plan phase) |
| Bản trích xuất Excel/Word/PowerPoint/PDF của specification | — | Không tìm thấy file nào thuộc định dạng này trong `docs/changes/AI-REVIEW-KPI-IMPROVEMENT/` (đã glob toàn bộ thư mục) | N/A | — | — | Không có gì để trích xuất | Không dùng — không có Office/PDF source cho ticket này |
| Tên bảng `tbl_fact_ai_finding_stat` | Chỉ định trực tiếp bởi user trong yêu cầu tạo spec-pack | — | Confirmed (Human decision, ưu tiên cao nhất) | User | Ghi đè tên `tbl_fact_ai_review_stat` đề xuất trong `raw/requirement.md` | Không | Áp dụng xuyên suốt spec-pack |

## Summary

Phần lớn nguồn quan trọng nhất — source code hiện có (`GithubWebhookService`, `MarkdownParserCore`, PM Dashboard stack, `tbl_ticket_bug_metrics` stack) và các file convention "Confirmed" trong `docs/standards/` — đều đã đọc đầy đủ và có độ tin cậy cao. Đây là nền tảng chính cho spec-pack, đúng theo nguyên tắc "ưu tiên source khi source và tài liệu mâu thuẫn". `raw/requirement.md` (tài liệu business đã được user chốt) được dùng làm khung nội dung nhưng đã cập nhật lại tên bảng theo quyết định mới nhất của user.

## Unavailable / Partial Sources

- `docs/architecture/*` — chưa đối chiếu chi tiết (chỉ biết tên file tồn tại).
- 4 file test hiện có (BE) liên quan trực tiếp đến vùng code sẽ sửa — chưa đọc nội dung, chỉ biết số lượng test case.
- DDL gốc của `tbl_dim_ticket`/`tbl_dim_project`/`tbl_dim_repository` — tên cột suy luận gián tiếp qua FK ở migration sau, chưa verify trực tiếp.
- Không có Office/PDF/Excel/PowerPoint spec extract nào tồn tại cho ticket này (đã kiểm tra, không phải bỏ sót).
- Không có OpenAPI spec chính thức trong repo (theo `api-contract.md`, đây vẫn là "Candidate", chưa "Confirmed") — dùng convention doc thay thế.

## Risk Before Implementation

1. **Định nghĩa nghiệp vụ chưa khớp 100%**: tên KPI "Blocker/Major Resolution Rate" mà business yêu cầu bao gồm cả "Major", nhưng label dòng tương ứng trong template gốc chỉ ghi "Blocker" — chưa rõ đây là named khác nhau của cùng 1 số liệu hay 2 phạm vi khác nhau (xem Open Issue OI-AIRKI-1 / Human Decision H-AIRKI-1).
2. **Test hiện có chưa được đọc kỹ** — có nguy cơ trùng lặp hoặc phá vỡ test/mock pattern đã tồn tại trong `GithubWebhookServiceTest.java` và các test PM Dashboard.
3. **Chưa có quyết định cuối về shape API** (`repositoryId` bắt buộc như `template-usage` hay optional để trả list nhiều repository) — ảnh hưởng trực tiếp tới FE/BE contract (§9).
4. **Chưa có quyết định về schema pattern** (tối giản kiểu V510 hay đầy đủ audit/soft-delete kiểu V509) cho bảng mới.
5. **Ticket cũ đã merge trước khi deploy feature** sẽ không có dữ liệu KPI cho tới khi có PR merge tiếp theo chạm vào `ai-review.md` — cần quyết định có cần backfill 1 lần hay chấp nhận gap này.

## Required Human Decision

Xem chi tiết tại `open-issues.md` (mục "Pending Human Decisions") và spec-pack.md §18. Tóm tắt 4 điểm: định nghĩa Blocker/Major (H-AIRKI-1), shape API theo repository (H-AIRKI-2), có cần periodKey filter (H-AIRKI-3), chọn DB schema pattern (H-AIRKI-4).
