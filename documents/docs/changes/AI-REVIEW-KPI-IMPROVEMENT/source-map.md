# source-map.md

**Ticket ID**: AI-REVIEW-KPI-IMPROVEMENT
**Create date**: 2026-08-18
**Author**: Tech Lead (Claude)

Bản đồ file/class/method liên quan tới ticket này, phân loại rõ **sẽ sửa** vs **chỉ tham chiếu**.
Không có file nào trong danh sách "sẽ sửa" đã bị thay đổi trong giai đoạn tài liệu này (Phase 0 —
chỉ tạo/sửa file dưới `documents/docs/`, xem `.claude/rules/00-safety.md` §4).

---

## 1. File sẽ sửa (impl-plan sẽ động tới)

### Backend

| File | Thay đổi dự kiến |
|---|---|
| `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/ingestion/GithubWebhookService.java` | Thêm field constructor-injected cho writer mới; chèn logic gọi writer giữa dòng ~296 và ~298 trong `handlePullRequest` |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/ingestion/` (thư mục) | Thêm file mới `AiFindingStatWriter.java` (tên đề xuất) — `@Component`, method `@Transactional(REQUIRES_NEW)` |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/persistence/` (thư mục) | Thêm file mới `AiFindingStatPort.java` (tên đề xuất) — port ghi, 1 method |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/persistence/PmDashboardRepositoryPort.java` | Thêm method đọc mới, ví dụ `findAiFindingStats(UUID repositoryId)` |
| `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/PmDashboardJdbcAdapter.java` | Implement method đọc mới ở trên |
| `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/` (thư mục) | Thêm file mới `AiFindingStatJdbcAdapter.java` (tên đề xuất) — implement port ghi |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/pmdashboard/PmDashboardModels.java` | Thêm record mới (ví dụ `AiFindingStatsRow`/`AiFindingStatsSummary`) |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/pmdashboard/PmDashboardService.java` | Thêm service method mới (gate `requirePm` + delegate xuống port đọc) |
| `EDCAP_BE/src/main/java/com/sdd/platform/web/dto/PmDashboardDtos.java` | Thêm DTO record mới + `from(...)` |
| `EDCAP_BE/src/main/java/com/sdd/platform/web/rest/PmDashboardController.java` | Thêm endpoint mới, ví dụ `GET /api/v1/pm/dashboard/ai-finding-stats` |
| `EDCAP_BE/src/main/resources/db/migration/V511__<mo_ta>.sql` (file mới) | Tạo bảng `tbl_fact_ai_finding_stat` |
| `EDCAP_BE/src/test/java/com/sdd/platform/application/usecase/ingestion/GithubWebhookServiceTest.java` | Bổ sung test case mới / mở rộng test hiện có (xem mục 3) |
| `EDCAP_BE/src/test/UnitTest/java/com/sdd/platform/...` (đường dẫn tương ứng writer/adapter mới) | Test mới cho writer + JDBC adapter mới |
| `EDCAP_BE/src/test/UnitTest/java/com/sdd/platform/application/usecase/pmdashboard/PmDashboardServiceTest.java` | Test service method mới |
| `EDCAP_BE/src/test/UnitTest/java/.../PmDashboardControllerTest.java` | Test endpoint mới |

### Frontend

| File | Thay đổi dự kiến |
|---|---|
| `EDCAP_FE/src/lib/api.ts` | Thêm method mới vào object `pmDashboard` (cạnh `templateUsage`, ~dòng 1857) |
| `EDCAP_FE/src/pages/pm-dashboard/utils.ts` | Có thể tái dùng `formatUsageRate` nguyên trạng, hoặc thêm hàm format mới nếu hình dạng dữ liệu khác |
| `EDCAP_FE/src/pages/pm-dashboard/components/` (thư mục) | Thêm component mới hiển thị 5 KPI (tên đề xuất `AiFindingStatsCard.tsx`, theo mẫu `TemplateUsageByPhase.tsx`) |
| PM Dashboard page chính (chưa đọc, cần xác nhận đường dẫn thật trước khi impl — có khả năng `EDCAP_FE/src/pages/pm-dashboard/PMDashboardPage.tsx` hoặc tương tự) | Compose component mới vào trang, theo rule `40-testing.md` "AC Closure" — component phải được mount trên trang thật, không chỉ có unit test cô lập |

**Lưu ý migration**: `V511` là version tiếp theo dự kiến, dựa trên xác nhận `V510` là file mới nhất
tại thời điểm viết tài liệu này (qua glob `**/V51*.sql`). Impl-plan PHẢI re-check thư mục
`db/migration/` ngay trước khi tạo file thật, vì có thể có migration khác đã được thêm vào giữa lúc
viết tài liệu và lúc bắt đầu code.

---

## 2. File chỉ tham chiếu (đọc để hiểu pattern, KHÔNG sửa)

| File | Vai trò tham chiếu |
|---|---|
| `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/ingestion/TemplateUsageStatWriter.java` | Mẫu writer bean riêng + `@Transactional(REQUIRES_NEW)` |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/persistence/TemplateUsageStatPort.java` | Mẫu port ghi tối giản |
| `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/TemplateUsageStatJdbcAdapter.java` | Mẫu adapter JDBC — **chỉ tham khảo cấu trúc**, KHÔNG copy SQL increment |
| `EDCAP_BE/src/main/resources/db/migration/V510__add_template_usage_tracking.sql` | Mẫu schema tối giản (OI-AIRKI-4) |
| `EDCAP_BE/src/main/resources/db/migration/V509__ticket_bug_metrics.sql` | Đối chứng schema đầy đủ audit — KHÔNG dùng pattern này cho ticket này |
| `EDCAP_BE/src/main/java/com/sdd/platform/domain/service/markdown/core/MarkdownParserCore.java` | Công cụ parse Markdown — dùng `parse()`, `.tables()`, `.sections()`, `.sectionMap()`; KHÔNG sửa file này trừ khi impl-plan quyết định cần bổ sung case mới vào `canonicalSectionKey` (cần cân nhắc kỹ, xem rủi ro trong `context.md`) |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/scanner/ArtifactScannerModels.java` | Định nghĩa `TicketScope` và các record scanner khác |
| `EDCAP_BE/src/test/UnitTest/java/.../PmDashboardJdbcAdapterFindTemplateUsageTest.java` | Mẫu test JDBC adapter, đặc biệt cách assert SQL text qua `ArgumentCaptor` |

---

## 3. Hai source root test khác nhau (đặc thù codebase, cần nắm rõ)

Codebase hiện tại có **2 thư mục gốc test Java khác nhau** trong cùng module `EDCAP_BE` — đây là
điểm không nhất quán có sẵn của project, không phải lỗi cần sửa trong ticket này:

| Source root | Dùng cho | Ví dụ xác nhận |
|---|---|---|
| `EDCAP_BE/src/test/java/...` | Test webhook/ingestion (quy ước Maven chuẩn) | `GithubWebhookServiceTest.java` |
| `EDCAP_BE/src/test/UnitTest/java/...` | Test PM Dashboard | `PmDashboardJdbcAdapterFindTemplateUsageTest.java`, `PmDashboardServiceTest.java`, `PmDashboardControllerTest.java` |

**Quy tắc áp dụng cho ticket này**: test cho `GithubWebhookService`/writer mới đặt tại
`src/test/java/...` (theo class đang sửa); test cho `PmDashboardJdbcAdapter`/`PmDashboardService`/
`PmDashboardController` đặt tại `src/test/UnitTest/java/...` (theo class đang sửa) — tức là **vị
trí test đi theo vị trí class hiện có đang được mở rộng**, không tự chọn source root theo sở thích.

---

## 4. Bảng DB liên quan

Xem chi tiết đầy đủ (cột, FK, ý nghĩa) tại `context.md` mục "Mapping". Tóm tắt:

- `tbl_dim_ticket` — dimension ticket, `tbl_fact_ai_finding_stat` FK về đây qua `ticket_id`
- `tbl_dim_repository` — dimension repository, dùng để lọc theo `repositoryId` khi đọc aggregate
- `tbl_dim_project` — dimension project, dùng bởi `requirePm`
- `tbl_fact_template_usage_stat` — bảng tham chiếu mẫu, KHÔNG phải bảng của ticket này
- `tbl_fact_ai_finding_stat` (MỚI) — bảng đích, khóa theo `ticket_id`, schema tối giản kiểu V510

---

## 5. Xác nhận: rule đặc thù không áp dụng

Đã kiểm tra toàn bộ `EDCAP_BE/src` và `EDCAP_FE/src` (grep) và xác nhận các khái niệm sau **không
tồn tại và không áp dụng** cho ticket này — không cần map hay xử lý gì thêm:

- `formItemNm`, `SEQNO` (form động / số thứ tự trường form)
- Full-width / half-width character handling (全角/半角)
- Yêu cầu giữ nguyên tiếng Nhật không Unicode-hóa

Các khái niệm này là boilerplate template dùng chung cho nhiều loại dự án (kể cả dự án thị trường
Nhật Bản khác), không đặc thù cho EDCAP.

---

## 6. Khoảng trống cần lấp trước impl-plan (chưa map được trong phiên này)

- `ArtifactScannerSourcePort` — chưa đọc đầy đủ; cần xác nhận chuỗi method đọc nội dung
  `ai-review.md` từ Git blob (`resolveRevision`/`listTree`/`readBlob` hay tương đương).
- `AppProperties.java` — chưa đọc; cần xác nhận convention đặt tên nếu cần thêm property cấu hình mới.
- `GithubPullRequestFilesPort` — chưa đọc; cần xác nhận có liên quan tới việc lấy nội dung file thay
  đổi trong PR hay không (có thể trùng vai trò với `ArtifactScannerSourcePort`).
- Đường dẫn thật của trang PM Dashboard chính (FE) nơi cần compose component mới vào — chưa xác
  nhận tên file chính xác trong phiên Tech Lead này.
- `PmDashboardServiceTest.java` và `PmDashboardControllerTest.java` — chưa đọc toàn bộ nội dung
  (chỉ xác nhận đường dẫn tồn tại), bắt buộc đọc đầy đủ trước khi viết test mới theo OI-AIRKI-6.
