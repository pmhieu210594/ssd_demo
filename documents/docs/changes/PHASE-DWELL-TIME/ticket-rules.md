# Ticket Rules

**Ticket ID**: PHASE-DWELL-TIME
**Create date**: 2026-08-19
**Author**: Claude (Tech Lead prep)
**Update date**: 2026-08-20 (vòng 2 — đồng bộ với 7 Gate kỹ thuật đã RESOLVED trong `impl-plan.md` § "Quyết định Gate"; sửa số file mục tiêu 7→8 sau khi verify source thật)

## Must Follow

- Không thêm specification nào ngoài `spec-pack.md`. Điểm mơ hồ mới phát
  sinh khi implement → ghi vào `open-issues.md`, KHÔNG tự quyết định.
- Đọc `context.md` và `source-map.md` (cùng thư mục) trước khi sửa bất kỳ
  file source nào.
- Trước khi implementation, đọc source đích + test hiện có:
  `TicketDetailDrawer.tsx`, `TicketDetailDrawer.test.tsx`,
  `PmDashboardJdbcAdapter.java` (`findDetail`), `PmDashboardModels.java`,
  `MarkdownParserCore.java`, `ArtifactScannerService.java`.
- Bám sát pattern hexagonal hiện có của domain PM Dashboard (Service mỏng →
  Port interface → JDBC Adapter dùng `NamedParameterJdbcTemplate` + SQL text
  block → Controller → DTO record với `from(...)` factory).
- Danh sách 7 Phase hiển thị theo `phase_order`: `1, 3, 4, 5, 6, 7, 8`.
  Không hiển thị `0-A, 0-B, 2, 9`.
- Nguồn dữ liệu Dwell Time: `create_date`/`update_date` tự khai báo trong
  header mỗi file `.md` (dòng `**Create date**: ...` / `**Update date**: ...`),
  parse qua `MarkdownParserCore.parse(blobContent,
  sourcePath).headerMetadata()` bằng **1 service mới dùng chung**
  (`ArtifactDocumentDateService`, package `com.sdd.platform.application.usecase.scanner`
  — RESOLVED Gate #2) cho cả **8 file** mục tiêu trong `CHANGE_TARGET_FILES`
  (đã xác minh thực tế có 8 phần tử, không phải 7 — gồm cả
  `blackbox-testcases.md`; service đọc header đủ cả 8 file, để mapping
  `tbl_dim_artifact_type.phase_id` tự quyết định file nào đóng góp phase
  nào, không tự loại trừ theo tên file — RESOLVED Gate #8) — độc lập với
  4 parser chuyên biệt hiện có (`SpecPackMarkdownParser`,
  `ReviewChecklistMarkdownParser`, `SelfReviewMarkdownParser`,
  `ReportMarkdownParser`) và độc lập với
  `ImplPlanParseService`/`TestPlanParseService`/`TestResultsParseService`.
  Service tự gọi lại `source.readBlob(...)` theo `source_path`/`sha` đã có
  sẵn (không refactor vòng lặp parse hiện có để chia sẻ blob content —
  RESOLVED Gate #7, chấp nhận thêm tối đa +8 GitHub API call/lần scan/ticket).
- Lưu `create_date`/`update_date` đã parse vào **1 bảng mới riêng**
  (`tbl_fact_artifact_document_date`, có `CONSTRAINT
  uq_artifact_document_date_snapshot UNIQUE (artifact_snapshot_id)`, ghi
  bằng `INSERT ... ON CONFLICT (artifact_snapshot_id) DO NOTHING` —
  RESOLVED Gate #1), KHÔNG dùng chung cột `parsed_summary` của
  `tbl_fact_artifact_snapshot`, KHÔNG tái sử dụng
  `updateSnapshotParsedSummary(...)`.
- Công thức: Dwell Time 1 file = `update_date − create_date`. Dwell Time 1
  phase = **cộng dồn** `Σ(update_date − create_date)` của tất cả file
  thuộc phase đó (mapping qua `tbl_dim_artifact_type.phase_id`).
- Mỗi Phase chỉ hiển thị **1 field giá trị**: Dwell Time dạng `hh:mm:ss`
  (không giới hạn 24 giờ) khi tính được, hoặc `"-"` cho mọi trường hợp còn
  lại (chưa có dữ liệu, lỗi/timeout API). **Không hiển thị nhãn trạng
  thái** nào.
- `DashboardTicketDetail` là Java **record** (bất biến) — khi thêm field,
  cập nhật đồng thời cả 2 nơi: định nghĩa record
  (`PmDashboardModels.java:266-277`) và nơi khởi tạo duy nhất trong
  `PmDashboardJdbcAdapter.findDetail(...)` (dòng 259-269).
- Constructor injection cho mọi Service/Adapter/Controller mới hoặc bị sửa
  — không `@Autowired` field injection.
- `@Transactional(readOnly = true)` bắt buộc cho method Service đọc dữ liệu.
- DTO record + static `from(Model m)` factory — đúng pattern
  `PmDashboardDtos.java`, không dùng thư viện mapping khác.
- Exception dùng lại `NotFoundException("Pages.PmDashboard.NotFound")` —
  không tạo class exception mới, không thêm `ResponseEntity` status code
  tùy biến trong controller.
- FE: named export, không default export; type mới thêm vào
  `PmDashboardTicketDetail` trong `lib/api.ts`; mọi call API qua
  `lib/api.ts`.
- i18n: thêm 1 key tên field mới đồng thời ở cả 3 file
  `public/locales/{en,ja,vi}/locale.json`, dưới `Pages.PmDashboard`,
  namespace `locale` (không tạo namespace/file locale mới).
- Business code value dùng master data có sẵn (`tbl_dim_phase.phase_code`,
  `tbl_dim_artifact_type.phase_id`) thay vì magic string.
- Giữ nguyên chữ số ASCII (half-width) cho `hh:mm:ss`, không dùng ký tự
  full-width kiểu Nhật.
- Giữ nguyên literal UTF-8 cho ký tự tiếng Nhật trong `locale.json` — KHÔNG
  escape thành `\uXXXX`.
- Không export secret/PII vào log.
- Bảng mới cần Flyway migration mới — theo `00-safety.md §3`, phải hỏi
  người dùng trước khi chạy `flyway migrate`. Version đề xuất: `V512`
  (cao nhất hiện có là `V511__ai_quality.sql`) — xác nhận lại version cao
  nhất thực tế ngay trước khi merge, không tin số này một cách mù quáng.
- Lỗi tính Dwell Time cho 1 phase: bọc try/catch **trong
  `PmDashboardJdbcAdapter`** (method helper mới, ví dụ
  `findPhaseDwellTime(ticketId)`), log `WARN`, trả rỗng/`null` cho field
  liên quan — **không** throw lên `PmDashboardService`/`Controller`
  (RESOLVED Gate #3).
- Lỗi parse header (`create_date`/`update_date` thiếu/sai định dạng): log
  `WARN` kèm `ticketId`, `sourcePath`, tên field thiếu/sai — **không** log
  nội dung file `.md`, không log toàn bộ `headerMetadata()` map (RESOLVED
  Gate #5).
- KHÔNG trigger `FULL` scan backfill tự động ngay sau deploy — để dữ
  liệu backfill dần theo lần scan tự nhiên tiếp theo của từng ticket
  (RESOLVED Gate #4).

## Must Not Do

- Không tạo bảng/cột migration DB mới ngoài 1 bảng riêng cho
  `create_date`/`update_date` — additive-only, không ALTER/DROP bảng hiện
  có (`tbl_fact_artifact_snapshot`, `tbl_fact_ticket_phase_status`, ...).
- Không sửa `TicketPhaseEvaluatorService`, `upsertTicketPhaseStatus`,
  `tbl_fact_ticket_phase_status`, hoặc logic xác định "current phase" hiện
  tại của `PhaseCard`.
- Không mở rộng `ArtifactScannerService.CHANGE_TARGET_FILES`/
  `PHASE0_TARGET_FILES`.
- Không đọc/dùng phase `0-A` cho Dwell Time.
- Không dùng cột `created_at`/`collected_at`/`source_updated_at` của
  `tbl_fact_artifact_snapshot` làm mốc Entry.
- Không dùng chung cột `parsed_summary`/method `updateSnapshotParsedSummary`
  cho `create_date`/`update_date` mới.
- Không tạo endpoint mới — field bổ sung vào response
  `GET /api/v1/pm/dashboard/tickets/{ticketId}/detail` đã có sẵn.
- Không dùng MyBatis Mapper cho phần đọc.
- Không dùng `Duration.toString()`/`Duration.format()` mặc định của Java để
  tạo chuỗi `hh:mm:ss`.
- Không dùng `@Data` Lombok trên model mới nếu có — dùng
  `@Builder @Getter @Setter @NoArgsConstructor @AllArgsConstructor`.
- Không gọi `fetch` trực tiếp ở FE, không dùng `any` không có comment
  `// reason:`.
- Không hiển thị bất kỳ nhãn trạng thái nào (Pending/Completed/InProgress)
  trên UI — chỉ 1 giá trị Dwell Time hoặc `"-"`.
- Không bịa ra method/API/cột chưa tồn tại — xem `context.md` mục "Method
  tồn tại / method không tồn tại".
- Không sửa `PmDashboardController.detail(...)` logic gọi.
- Không amend/sửa các test có sẵn (`TicketDetailDrawer.test.tsx`,
  `TicketPhaseEvaluatorServiceTest.java`) để che giấu lỗi mới — chỉ thêm
  test case mới.
- Không dựa vào `vw_artifact_inventory_current` nếu chưa xác nhận migration
  mới nhất định nghĩa view đó.
- Không dùng công thức khoảng bao trùm MIN/MAX cho phase có ≥2 file — chỉ
  dùng cộng dồn.

## Stop / Ask Conditions

- ~~Nếu cách gộp Dwell Time vào `findDetail(...)` (subquery) hay tách port
  method riêng chưa có quyết định rõ~~ — **RESOLVED**: gộp bằng 1 method
  helper mới trong `PmDashboardJdbcAdapter` (không tách port riêng), xem
  `impl-plan.md` Gate #3.
- Nếu JOIN `tbl_dim_artifact_type.phase_id` tại thời điểm query cho ra kết
  quả khác với cột `phase_id` đã denormalize sẵn trên
  `tbl_fact_artifact_snapshot` → dừng lại, báo cáo, không tự chọn nguồn
  nào để "sửa cho khớp".
- Nếu truy vấn bảng mới (`create_date`/`update_date`) chậm với số lượng
  bản ghi lớn → hỏi trước khi quyết định thêm migration index mới (cần
  phê duyệt riêng theo `00-safety.md`).
- Nếu phát hiện `vw_artifact_inventory_current` có định nghĩa khác với bản
  đã đọc (`V503`) khi chạy thực tế trên DB → dừng lại, không tự suy diễn.
- Nếu ticket cũ chỉ có `Create date`/`Update date` dạng `YYYY-MM-DD` (không
  giờ) → Dwell Time có độ phân giải tối đa "ngày" — đây là giới hạn đã
  biết, không phải bug, ghi rõ trong `spec-pack.md` (Assumption), không
  được âm thầm bỏ qua.
- ~~Nếu tên/schema chính xác của bảng mới lưu `create_date`/`update_date`
  chưa được chốt~~ — **RESOLVED**: `tbl_fact_artifact_document_date`,
  UNIQUE trên `artifact_snapshot_id`, xem `impl-plan.md` Gate #1.
- Nếu migration version thực tế cao nhất tại thời điểm code KHÁC với
  `V511` (giả định khi viết tài liệu này) → dùng version kế tiếp thực tế,
  không dùng cứng `V512` nếu đã có migration khác chen vào.

## Review Focus

- Công thức Dwell Time dùng đúng bảng mới lưu `create_date`/`update_date`,
  KHÔNG dùng `created_at`/`collected_at`/`source_updated_at`/
  `parsed_summary` của `tbl_fact_artifact_snapshot`.
- Công thức cộng dồn đúng khi phase có ≥2 file (không phải khoảng bao trùm
  MIN/MAX).
- `DashboardTicketDetail` record cập nhật đủ ở 1 nơi khởi tạo duy nhất
  trong `PmDashboardJdbcAdapter.findDetail(...)`.
- Định dạng `hh:mm:ss` được test với giá trị > 24 giờ, không dùng
  `Duration.toString()`.
- Danh sách phase hiển thị đúng 7 phase `1,3,4,5,6,7,8`, không có `0-A`.
- Không có nhãn trạng thái nào xuất hiện trong UI hoặc response API.
- Cả 3 file `locale.json` (en/ja/vi) có key mới, nội dung tiếng Nhật giữ
  nguyên literal UTF-8, không tạo namespace mới.
- Không có migration ALTER/DROP nào lên bảng hiện có — chỉ thêm bảng/VIEW
  mới.
- Permission gate (`requirePm`) vẫn được gọi trước khi trả field mới.
- Service mới không ghi đè/đụng `parsed_summary` của 4 parser chuyên biệt
  hiện có.
- Không có code nào đọc `tbl_fact_ticket_phase_status`/gọi
  `TicketPhaseEvaluatorService` cho tính năng mới.

## Test Focus

- Test parse `create_date`/`update_date` từ header thật qua
  `headerMetadata`, bao gồm case chỉ có ngày (không giờ:phút:giây).
- Test service mới hoạt động đồng nhất trên cả 8 loại file mục tiêu
  trong `CHANGE_TARGET_FILES` (bao gồm `blackbox-testcases.md`), không
  chỉ 7 file có branch parse chuyên biệt sẵn có.
- Test cộng dồn Dwell Time cho phase có ≥2 file (ví dụ phase `6`:
  `test-plan.md` + `test-results.md`).
- Test giá trị `"-"` khi: chưa có dữ liệu, lỗi/timeout API.
- Test format `hh:mm:ss` > 24 giờ (`"27:20:05"`).
- Test regression: ghi bảng mới không làm thay đổi `parsed_summary` hiện
  có từ 4 parser chuyên biệt khác.
- FE test theo pattern `TicketDetailDrawer.test.tsx` (mock `react-i18next`,
  `MemoryRouter` + `QueryClientProvider`) — verify field mounted thật trên
  `PhaseCard`, không chỉ test cô lập (AC Closure, `40-testing.md`).
- Không mock domain record trực tiếp làm "dữ liệu giả" — record
  instantiation là dữ liệu thật; mock port interface nếu logic đặt ở
  Service.
- Test đa ngôn ngữ: label tên field hiển thị đúng theo `en`/`ja`/`vi`.
