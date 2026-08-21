# Open Issues

**Ticket ID**: PHASE-DWELL-TIME
**Create date**: 2026-08-19
**Author**: TBD
**Update date**: 2026-08-20

*(Không có Open Issue nào đang mở ở cấp spec/business — toàn bộ đã RESOLVED, xem Resolution Log bên dưới. Các quyết định kỹ thuật còn lại nằm ở `impl-plan.md` § Gate trước implementation, không phải Open Issue của tài liệu này.)*

## Blockers

*(Không còn Blocker nào — xem Resolution Log. Toàn bộ 7 Gate kỹ thuật của
`impl-plan.md` § Gate trước implementation đã RESOLVED 2026-08-20. Được
phép bắt đầu viết code.)*

- ~~Đọc migration `V394, V501, V502, V503, V505` và `EDCAP_FE/.../TicketDetailDrawer.test.tsx`, `EDCAP_BE/.../TicketPhaseEvaluatorServiceTest.java`~~ — **RESOLVED 2026-08-20 (vòng verify 2)**: đã đọc toàn văn. Không có schema drift (V503 chỉ đổi `schema_version` sang INTEGER, các bản còn lại chỉ CREATE OR REPLACE VIEW). `ArtifactScannerServiceTest.java` xác nhận an toàn khi thêm constructor param (dùng overload backward-compatible). `ja/vi locale.json` đã có sẵn section `Pages.PmDashboard`, không thiếu. Chi tiết đầy đủ: `impact-analysis.md` § "Xác minh kỹ thuật 2026-08-20 (vòng 2)".
- ~~7 Gate kỹ thuật ở `impl-plan.md` § Gate trước implementation~~ — **RESOLVED 2026-08-20**: người dùng đã duyệt "Áp dụng tất cả đề xuất". Chi tiết từng quyết định: `impl-plan.md` § "Quyết định Gate (RESOLVED 2026-08-20)".

## Questions

*(Không còn câu hỏi mở nào — `OI-PHASE-DWELL-TIME-14`/`15` đã RESOLVED 2026-08-21, xem Resolution Log.)*

~~**OI-PHASE-DWELL-TIME-14**~~ (phát hiện 2026-08-21, RESOLVED 2026-08-21): hành vi khi
`document_update_at < document_create_at` cho 1 file (dữ liệu header bị đảo/sai) nay đã chốt —
xem Resolution Log. Reproduction test đã được flip thành regression test xác nhận fix:
`PmDashboardJdbcAdapterPhaseDwellTimeTest#findPhaseDwellTime_rowMapper_negativeSumRendersAsDash_notAMalformedNegativeString`.

~~**OI-PHASE-DWELL-TIME-15**~~ (phát hiện 2026-08-21, RESOLVED 2026-08-21): hành vi FE khi
`PmDashboardTicketDetail.phaseDwellTime` bị thiếu hoàn toàn khỏi response nay đã chốt — xem
Resolution Log. Reproduction test (`it.fails`) đã được flip thành regression test xác nhận fix:
`TicketDetailDrawer.test.tsx` → `"degrades gracefully instead of crashing the whole drawer when
phaseDwellTime is missing from the API response"`.

## Pending Human Decisions

*(Không còn — xem Resolution Log.)*

## Accepted but Unresolved Items

- Data quality (xem `spec-pack.md §16` A-4): `create_date`/`update_date` là giá trị **tự khai báo trong header** file `.md`, phụ thuộc kỷ luật cập nhật của người/AI khi sửa file — ticket cũ có thể chưa từng cập nhật `update_date` theo quy ước mới, khiến phase đó hiển thị `"-"` dù thực tế đã hoàn thành (đã chấp nhận theo `spec-pack.md` AC-4: không đủ cặp `create_date`/`update_date` ⇒ `"-"`).

## Resolution Log

| ngày | quyết định | người quyết định |
|---|---|---|
| 2026-08-19 | Chọn Phương án A: suy lịch sử phase gián tiếp, đọc-only — không sửa `TicketPhaseEvaluatorService`/`tbl_fact_ticket_phase_status` | Người dùng |
| 2026-08-19 | Dùng `tbl_dim_artifact_type.phase_id` (DB) làm nguồn phase-mapping cho tính năng mới, chấp nhận song song với mapping hardcode cũ của `TicketPhaseEvaluatorService` | Người dùng |
| 2026-08-19 | Bỏ yêu cầu "Active Phase" | Người dùng |
| 2026-08-19 | Loại `0-B, 2` khỏi danh sách phase hiển thị (không có nguồn dữ liệu quét theo ticket); không mở rộng `CHANGE_TARGET_FILES` cho `context.md`; phase `9` không tồn tại trong hệ thống | Người dùng |
| 2026-08-19 | Chốt bản dịch 3 locale cho tên tính năng "Phase Dwell Time" (EN/JA/VI) | Người dùng |
| 2026-08-20 | Loại phase `0-A` khỏi danh sách hiển thị. Danh sách hiển thị cuối cùng: **7 phase** `1, 3, 4, 5, 6, 7, 8` | Người dùng |
| 2026-08-20 | Khi API lỗi/timeout lấy dữ liệu phase/dwell time, hiển thị `"-"` | Người dùng |
| 2026-08-20 | Bỏ toàn bộ nhãn trạng thái (Pending/InProgress/Completed) trên UI — chỉ hiển thị 1 field giá trị Dwell Time hoặc `"-"` | Người dùng |
| 2026-08-20 | Mở rộng `PmDashboardTicketDetail` hiện có với field mới `phaseDwellTime`, không tạo endpoint riêng | Người dùng |
| 2026-08-20 | Nguồn dữ liệu Dwell Time chính thức là `create_date`/`update_date` tự khai báo trong header file `.md` (bảng mới `tbl_fact_artifact_document_date`), không phải `tbl_fact_artifact_snapshot.created_at` — công thức là Σ per-file `(update_date − create_date)` trong cùng 1 phase | Người dùng (đồng bộ theo `context.md`/`impl-plan.md`) |
| 2026-08-20 | Phê duyệt Phương án A trong `wireframe.md`: giữ nguyên Card "Phase" hiện tại, thêm mới Card "Phase Dwell Time" dạng danh sách 7 dòng có tên phase (`phaseName`) + giá trị Dwell Time | Người dùng |
| 2026-08-20 | `phaseName` lấy từ cột `tbl_dim_phase.phase_name` | Người dùng |
| 2026-08-20 | Card mới "Phase Dwell Time" đặt ngay dưới Card "Phase" hiện tại trong `TicketDetailDrawer.tsx` | Người dùng |
| 2026-08-20 | `Timestamp_Now` không được sử dụng trong phạm vi ticket này | Người dùng |
| 2026-08-20 | Nếu phase N không còn file nào đủ cặp `create_date`/`update_date`, luôn hiển thị `"-"` (file thiếu `update_date` không đóng góp vào tổng) | Người dùng |
| 2026-08-20 | (Gate #1) Bảng mới `tbl_fact_artifact_document_date` có `UNIQUE (artifact_snapshot_id)`, ghi bằng `INSERT ... ON CONFLICT (artifact_snapshot_id) DO NOTHING` | Người dùng |
| 2026-08-20 | (Gate #2) Service trích header (`ArtifactDocumentDateService`) đặt trong package `com.sdd.platform.application.usecase.scanner`, được `ArtifactScannerService` gọi qua constructor injection | Người dùng |
| 2026-08-20 | (Gate #3) Lỗi tính Dwell Time cho 1 phase: bọc try/catch trong `PmDashboardJdbcAdapter`, log `WARN`, trả rỗng/`null`, không throw lên Controller | Người dùng |
| 2026-08-20 | (Gate #4) Không trigger `FULL` scan backfill tự động sau deploy — backfill dần theo scan tự nhiên | Người dùng |
| 2026-08-20 | (Gate #5) Log lỗi parse header ở mức `WARN`, chỉ kèm `ticketId`/`sourcePath`/tên field thiếu, không log nội dung file | Người dùng |
| 2026-08-20 | (Gate #7) Service trích header tự `readBlob` lại theo `source_path` (không refactor vòng lặp scan hiện có để chia sẻ blob content), chấp nhận thêm tối đa +8 GitHub API call/lần scan/ticket | Người dùng |
| 2026-08-20 | (Gate #8) Service đọc header cho cả 8 file trong `CHANGE_TARGET_FILES` (gồm `blackbox-testcases.md`), để mapping `tbl_dim_artifact_type.phase_id` (DB) tự quyết định đóng góp phase, không tự loại trừ theo tên file | Người dùng |
| 2026-08-21 | (OI-14, BUG-PHASE-DWELL-TIME-1) Khi tổng Dwell Time của 1 phase ra số âm (dữ liệu header bị đảo `update_date < create_date` trên 1 file), hiển thị `"-"` (coi như dữ liệu không hợp lệ, cùng nhánh với AC-3/AC-4), kèm log `WARN` (ticketId/phaseCode/dwellSeconds) để phát hiện dữ liệu header sai — không hiển thị giá trị tuyệt đối, không chấp nhận rủi ro không sửa | Người dùng |
| 2026-08-21 | (OI-15, BUG-PHASE-DWELL-TIME-2) Thêm guard mặc định `detail.phaseDwellTime ?? []` trong `PhaseDwellTimeCard` để không crash toàn bộ `TicketDetailDrawer` khi field bị thiếu khỏi response (cache cũ/lệch rollout BE-FE) — không chấp nhận rủi ro giữ nguyên hành vi crash | Người dùng |
| 2026-08-21 | (`ai-review.md` F1) Xác nhận đúng fix netting/ăn bớt số âm: thêm điều kiện `AND d.document_update_at >= d.document_create_at` vào `ON` clause của `LEFT JOIN tbl_fact_artifact_document_date` — file có ngày bị đảo bị loại hoàn toàn khỏi phép cộng, kể cả khi có file hợp lệ khác cùng phase. Đã bổ sung case black-box mới `BB-PHASE-DWELL-TIME-25` (bộ dữ liệu `AD-7`/`ND-10`, ticket `DEMO-DWELL-12`) | Người dùng |
| 2026-08-21 | (`ai-review.md` F2) Xác nhận PASS: chấp nhận `self-review.md` làm nguồn duy nhất cho việc đối chiếu review của ticket này, không yêu cầu điền lại `review-checklist.md` (đã ghi chú WAIVED trực tiếp trong `review-checklist.md`) | Người dùng |
| 2026-08-21 | (`ai-review.md` F3) Xác nhận bỏ qua: không fix log `WARN` bị lặp lại mỗi lần mở Ticket Detail khi tổng âm | Người dùng |
| 2026-08-21 | (`ai-review.md` F4) Xác nhận đề xuất sửa: chuyển `upsertArtifactDocumentDates` sang dùng `batchUpdate` thay vì vòng lặp `jdbc.update` tuần tự — **đã duyệt, chưa implement**, cần 1 lượt dev riêng | Người dùng |
| 2026-08-21 | (`ai-review.md` F5) Xác nhận bỏ qua: không fix việc log lỗi đọc/parse file thiếu `ex.getMessage()` | Người dùng |
