# Phase Status

**Ticket ID**: PHASE-DWELL-TIME
**Create date**: 2026-08-19
**Author**: TBD
**Update date**: 2026-08-20

> Tài liệu này là bản tóm tắt bàn giao (handoff) — dùng để tiếp tục phiên làm việc sau khi nén/ngắt hội thoại. Đọc file này trước, chỉ mở lại `spec-pack.md`/`open-issues.md`/`impl-plan.md` khi cần chi tiết đầy đủ.

## 1. Mục tiêu ticket

Bổ sung Card mới **"Phase Dwell Time"** vào `TicketDetailDrawer.tsx`, đặt ngay dưới Card "Phase" hiện tại, hiển thị danh sách 7 Phase theo thứ tự Workflow kèm thời gian ticket đã "lưu lại" ở từng Phase. Tính năng thuần đọc (read-only), **không** thay đổi logic ghi/xác định "current phase" hiện tại (`TicketPhaseEvaluatorService`). Đối tượng dùng: Manager, Support Lead, Operation Admin, Support Agent. Chi tiết đầy đủ: `spec-pack.md`.

## 2. Trạng thái các artifact

| Artifact | Trạng thái |
|---|---|
| `01_raw-input.md` | DONE |
| `source-map.md`, `sources.md`, `context.md` | DONE (nguồn dữ liệu chính thức đã xác định: header `create_date`/`update_date`, không phải `tbl_fact_artifact_snapshot.created_at`) |
| `spec-pack.md` | CHỐT — không còn Open Issue cấp spec/business |
| `impact-analysis.md` | CHỐT |
| `wireframe.md` | CHỐT — Phương án A đã phê duyệt |
| `impl-plan.md` | CHỐT hoàn toàn — 7 Gate kỹ thuật đã RESOLVED 2026-08-20 (xem mục 4) |
| `ticket-rules.md` | Đã đồng bộ theo 7 Gate RESOLVED (sửa số file mục tiêu 7→8, bổ sung quyết định vào Must Follow/Stop-Ask) |
| `open-issues.md` | Toàn bộ Open Issue (`OI-10` đến `OI-13`) + 7 Gate kỹ thuật đã RESOLVED |
| `review-checklist.md` | Soạn xong, chờ implementation để điền kết quả |
| `self-review.md`, `test-plan.md`, `test-results.md`, `blackbox-testcases.md` | Chưa bắt đầu — chờ code |
| Code (`EDCAP_BE/src`, `EDCAP_FE/src`) | **Chưa có thay đổi nào** — nhưng **đã đủ điều kiện bắt đầu viết code** (không còn Gate kỹ thuật nào OPEN) |

## 3. Quyết định quan trọng đã chốt (không suy đoán lại)

| # | Quyết định |
|---|---|
| Danh sách phase | 7 phase hiển thị: `1, 3, 4, 5, 6, 7, 8`. Loại `0-A, 0-B, 2, 9` |
| Nguồn dữ liệu | `create_date`/`update_date` tự khai báo trong header mỗi file `.md`, parse qua `MarkdownParserCore`, lưu ở bảng mới `tbl_fact_artifact_document_date` — **không dùng** `tbl_fact_artifact_snapshot.created_at` |
| Công thức | Dwell Time 1 phase = Σ per-file `(document_update_at − document_create_at)` của các file thuộc phase đó có đủ cả 2 mốc |
| Case biên | File có `create_date` nhưng thiếu `update_date` không đóng góp vào tổng; nếu phase không còn file nào đủ cặp → hiển thị `"-"` |
| Endpoint/DTO | Mở rộng `PmDashboardTicketDetail` hiện có bằng field `phaseDwellTime: { phaseCode, phaseOrder, phaseName, dwellTime }[]` — không tạo endpoint riêng |
| Nguồn `phaseName` | `tbl_dim_phase.phase_name` |
| Layout | Card "Phase" hiện tại giữ nguyên; thêm Card mới "Phase Dwell Time" ngay dưới (Phương án A, `wireframe.md`) |
| Lỗi API | Lỗi/timeout lấy dữ liệu phase → hiển thị `"-"` (không ẩn khối, không thông báo lỗi riêng, không retry tự động) |
| Nhãn trạng thái | Không hiển thị nhãn Pending/InProgress/Completed — chỉ 1 giá trị Dwell Time hoặc `"-"` |
| `Timestamp_Now` | Không sử dụng trong phạm vi ticket này |
| Phạm vi loại trừ | Không sửa `TicketPhaseEvaluatorService`, `upsertTicketPhaseStatus`, `tbl_fact_ticket_phase_status`; không ALTER/DROP schema hiện có; không mở rộng `CHANGE_TARGET_FILES` |

Toàn bộ Human Decisions H-1 → H-11: xem `spec-pack.md §18`. Toàn bộ Open Issues đã resolved: xem `open-issues.md § Resolution Log`.

## 4. Vấn đề chưa giải quyết

**Không còn** — toàn bộ 7 Gate kỹ thuật đã RESOLVED 2026-08-20 (người dùng
đã duyệt "Áp dụng tất cả đề xuất"). Quyết định cuối cho từng Gate:

1. **RESOLVED**: Bảng mới `tbl_fact_artifact_document_date`, `UNIQUE (artifact_snapshot_id)`, ghi bằng `INSERT ... ON CONFLICT (artifact_snapshot_id) DO NOTHING`.
2. **RESOLVED**: Service `ArtifactDocumentDateService` trong package `com.sdd.platform.application.usecase.scanner`, gọi qua constructor injection từ `ArtifactScannerService`.
3. **RESOLVED**: Lỗi tính Dwell Time cho 1 phase — try/catch trong `PmDashboardJdbcAdapter`, log `WARN`, trả rỗng/`null`, không throw lên Controller.
4. **RESOLVED**: Không trigger `FULL` scan backfill tự động sau deploy — backfill dần theo scan tự nhiên.
5. **RESOLVED**: Log lỗi parse header ở mức `WARN`, chỉ kèm `ticketId`/`sourcePath`/tên field thiếu, không log nội dung file.
6. **RESOLVED** (từ vòng verify 2): `ArtifactScannerServiceTest.java` dùng overload backward-compatible, an toàn khi thêm constructor param.
7. **RESOLVED**: Service tự `readBlob` lại theo `source_path` (không refactor vòng lặp scan hiện có), chấp nhận +tối đa 8 API call/scan/ticket.
8. **RESOLVED**: Service đọc header cho cả 8 file trong `CHANGE_TARGET_FILES` (gồm `blackbox-testcases.md`), để mapping DB (`tbl_dim_artifact_type.phase_id`) tự quyết định đóng góp phase.

Chi tiết đầy đủ + căn cứ từng quyết định: `impl-plan.md` § "Quyết định
Gate (RESOLVED 2026-08-20)"; log quyết định: `open-issues.md` §
Resolution Log và `spec-pack.md §18` (H-PHASE-DWELL-TIME-12 → 18).

**Được phép bắt đầu viết code.**

## 5. File cần đọc trước khi code

Tất cả các file trước đây liệt kê "chưa đọc" đã được đọc và verify trong
vòng 2 (2026-08-20) — không còn file nào chặn tiến độ đọc:

1. ~~`EDCAP_FE/.../TicketDetailDrawer.test.tsx`~~ — ĐÃ ĐỌC (đường dẫn chính xác: `EDCAP_FE/src/__ tests __/pm-dashboard/TicketDetailDrawer.test.tsx`, lưu ý khoảng trắng literal trong tên thư mục).
2. ~~`EDCAP_BE/.../TicketPhaseEvaluatorServiceTest.java`~~ — đã đọc từ vòng 1 (`context.md`).
3. ~~Migration `V394, V501, V502, V503, V505`~~ — ĐÃ ĐỌC: không có schema drift ảnh hưởng ticket này; `V503` đổi `tbl_fact_artifact_snapshot.schema_version` sang INTEGER (không liên quan trực tiếp); các bản còn lại chỉ CREATE OR REPLACE VIEW `tbl_fact_ticket_dashboard_snapshot`.
4. ~~`ja/locale.json`, `vi/locale.json`~~ — ĐÃ ĐỌC: cả 2 đã có sẵn section `Pages.PmDashboard` (dòng 136/137).
5. **[MỚI cần đọc trước khi code]** `docs/standards/database.md` mục quy ước bảng `tbl_fact_*` — đã đọc một phần qua agent verify, nên đọc lại toàn văn khi viết migration thật.

**Không cần đọc**: `tbl_dim_ticket`/`TicketScope` — phase `0-A` (phase duy nhất cần thông tin repository) đã bị loại khỏi danh sách hiển thị.

(Danh sách đầy đủ + độ tin cậy từng nguồn: `source-availability.md`.)

## 6. Next Action

Không còn Gate nào cần xin xác nhận — bắt đầu implementation theo thứ tự:

1. Đọc lại toàn văn `docs/standards/database.md` mục quy ước `tbl_fact_*`
   (mục 5 chưa đọc đầy đủ) ngay trước khi viết migration.
2. Xác nhận lại migration version cao nhất thực tế trong
   `db/migration/` (đề xuất `V512`, dựa trên `V511__ai_quality.sql` là
   bản cao nhất tại thời điểm viết tài liệu — có thể đã đổi).
3. Viết migration `V{n}__add_artifact_document_date.sql` (bảng
   `tbl_fact_artifact_document_date`, `UNIQUE (artifact_snapshot_id)` —
   chạy local, **chưa** `flyway migrate` môi trường chia sẻ, hỏi người
   dùng trước theo `00-safety.md §3`).
4. Viết `ArtifactDocumentDateService` (package
   `com.sdd.platform.application.usecase.scanner`, đọc header cả 8 file
   trong `CHANGE_TARGET_FILES` qua `MarkdownParserCore`, tự `readBlob`
   lại theo `source_path`).
5. Viết port method mới + adapter ghi bảng mới (pattern `jdbc.update`
   đơn lẻ, không `TransactionTemplate` — nhất quán với
   `ArtifactScannerJdbcAdapter` hiện tại).
6. Gắn service mới vào `ArtifactScannerService.scanTicketDirectory`
   (chèn tại dòng 442, thêm dependency vào constructor 12-arg chính,
   giữ nguyên 2 overload backward-compatible).
7. Viết subquery/helper method Dwell Time trong `PmDashboardJdbcAdapter`
   (try/catch nội bộ, log `WARN` khi lỗi) → cập nhật `PmDashboardModels`
   (record `DashboardTicketDetail`, dòng 266-277 + nơi khởi tạo dòng
   259-269) → `PmDashboardDtos` (`PmDashboardTicketDetailDto`, dòng
   334-360).
8. Cập nhật FE: type `phaseDwellTime` trong `lib/api.ts:585-641`, Card
   mới `PhaseDwellTimeCard` cạnh dòng 745 trong `TicketDetailDrawer.tsx`,
   i18n 3 locale (`en/ja/vi` dưới `Pages.PmDashboard`).
9. Viết test mới (BE unit cho service + adapter, FE unit cho Card mới)
   và cập nhật test hiện có (`EDCAP_FE/src/__ tests __/pm-dashboard/TicketDetailDrawer.test.tsx`
   object `detail`, `ArtifactScannerServiceTest.java` nếu cần) → chạy
   lại toàn bộ regression + ArchUnit.
10. Điền `self-review.md`/`review-checklist.md` với bằng chứng thực tế
    sau khi code xong — đặc biệt 4 mục "AI-generated predictions" đã
    đánh dấu bắt buộc điền (blob strategy, phạm vi `blackbox-testcases.md`,
    UNIQUE constraint thực tế, migration version thực tế đã dùng).
11. Nếu phát sinh điểm mơ hồ MỚI (khác 8 Gate đã liệt kê) trong lúc code
    → ghi vào `open-issues.md`, dừng lại hỏi, không tự suy đoán
    (`ticket-rules.md`).
