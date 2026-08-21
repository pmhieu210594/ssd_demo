# Self Review

**Ticket ID**: PHASE-DWELL-TIME
**Create date**: 2026-08-19
**Author**: Claude (Senior Engineer implementation pass)
**Update date**: 2026-08-21 (vòng 3 — BUG-PHASE-DWELL-TIME-1/2 đã được người dùng duyệt phương án
và fix xong, xem mục 7)

## 1. Implementation Summary

Đã implement đúng Phương án A (đọc-only, suy lịch sử phase từ header `create_date`/`update_date`
của mỗi file `.md`), theo `impl-plan.md` — **không phải** cách diễn giải khác ở `spec-pack.md §2`
(2 tài liệu mô tả cùng 1 phương án, không có mâu thuẫn thực tế: bảng mới `tbl_fact_artifact_document_date`
lưu `document_create_at`/`document_update_at` parse qua `MarkdownParserCore.headerMetadata()`).

Các phần đã code:

- **Migration mới** `V512__add_artifact_document_date.sql` — bảng `tbl_fact_artifact_document_date`,
  additive-only, `UNIQUE (artifact_snapshot_id)`, FK `ON DELETE CASCADE` tới
  `tbl_fact_artifact_snapshot`. Chưa chạy `flyway migrate` trên môi trường chia sẻ nào (chỉ tạo file SQL).
- **`ArtifactDocumentDateService`** (package `com.sdd.platform.application.usecase.scanner`, đúng Gate #2) —
  đọc lại blob qua `source.readBlob(...)` theo `source_path`/`sha` có sẵn trong tree (đúng Gate #7, không
  refactor vòng lặp parse hiện có), parse header bằng `MarkdownParserCore.parse(...).headerMetadata()`,
  hỗ trợ 2 format: `yyyy-MM-dd HH:mm:ss` / `yyyy-MM-dd'T'HH:mm:ss` và `yyyy-MM-dd` (chỉ ngày). Parse fail
  hoặc field thiếu → trả `null`, log `WARN` theo message cố định đúng Gate #5, không throw.
- **Port + Adapter**: `ArtifactScannerPersistencePort.upsertArtifactDocumentDates(...)` +
  `ArtifactScannerJdbcAdapter` implement bằng `INSERT ... ON CONFLICT (artifact_snapshot_id) DO NOTHING`
  (đúng Gate #1 — rescan cùng `content_hash` giữ nguyên `artifact_snapshot_id` nên `DO NOTHING` tự thỏa AC-6).
- **`ArtifactScannerService.scanTicketDirectory`**: thêm dependency `ArtifactDocumentDateService` vào
  constructor 12-arg chính (giữ nguyên 2 overload backward-compatible, fill `null`), gọi service mới
  ngay sau vòng lặp `CHANGE_TARGET_FILES` (366-441 gốc), trước `ticketPhaseEvaluatorService.evaluateAndPersist(...)` —
  đúng vị trí `impl-plan.md` đã chỉ định. Bọc try/catch quanh lời gọi để lỗi cục bộ không làm hỏng scan.
  Service mới đọc **cả 8 file** trong `CHANGE_TARGET_FILES` (đúng Gate #8) — không tự loại `blackbox-testcases.md`,
  để mapping `tbl_dim_artifact_type.phase_id` (DB) tự quyết định phase.
- **BE aggregate**: `PmDashboardJdbcAdapter.findPhaseDwellTime(ticketId)` — JOIN
  `tbl_dim_phase → tbl_dim_artifact_type → tbl_fact_artifact_snapshot → tbl_fact_artifact_document_date`,
  lọc `phase_code IN ('1','3','4','5','6','7','8')`, `SUM(EXTRACT(EPOCH FROM (update_at - create_at)))`
  chỉ trên dòng có cả 2 cột NOT NULL (JOIN điều kiện lọc ngay trong ON), format `hh:mm:ss` (hh không giới
  hạn 24). Bọc try/catch nội bộ (Gate #3) — lỗi trả `List.of()`, log `WARN`, không throw lên `findDetail`.
  Field mới `phaseDwellTime` thêm vào `DashboardTicketDetail`/`PmDashboardTicketDetailDto` (additive).
- **FE**: field mới `phaseDwellTime` trong `PmDashboardTicketDetail` (`api.ts`); Card mới `PhaseDwellTimeCard`
  trong `TicketDetailDrawer.tsx`, đặt ngay dưới `<PhaseCard .../>`, không sửa `PhaseCard`. FE render trực
  tiếp `dwellTime ?? "-"` — không tự tính lại công thức (đúng `impl-plan.md`). i18n 3 locale (EN/JA/VI)
  cho tên Card (không thêm key trạng thái vì scope không có nhãn Pending/InProgress — đúng H-PHASE-DWELL-TIME-9).

Không sửa `TicketPhaseEvaluatorService`, `upsertTicketPhaseStatus`, không ALTER/DROP bảng hiện có.

## 2. Specification/AC Matching

| AC ID | status | evidence |
|---|---|---|
| AC-PHASE-DWELL-TIME-1 | PASS | `PmDashboardJdbcAdapter.findPhaseDwellTime` WHERE lọc đúng `phase_code IN ('1','3','4','5','6','7','8')`, `ORDER BY phase_order ASC`; FE `PhaseDwellTimeCard` sort lại theo `phaseOrder` trước khi render. |
| AC-PHASE-DWELL-TIME-2 | PASS (đơn vị) | `ArtifactDocumentDateServiceTest.extractsFullHeaderWithDateAndTime`/`extractsDateOnlyHeader` xác nhận parse đúng cả 2 format; aggregate SQM dùng `SUM(EXTRACT(EPOCH ...))` cộng dồn nhiều file cùng phase — **chưa có test DB thật cho phần cộng dồn nhiều file/1 phase** (xem mục 8). |
| AC-PHASE-DWELL-TIME-3 | PASS (logic) | LEFT JOIN từ `tbl_dim_phase` → không có snapshot/document_date nào khớp → `SUM` trên tập rỗng trả `NULL` → `formatDwellTimeSeconds(null)` trả `null` → FE render `"-"`. |
| AC-PHASE-DWELL-TIME-4 | PASS (logic) | JOIN điều kiện `d.document_create_at IS NOT NULL AND d.document_update_at IS NOT NULL` ngay trong `ON` — file thiếu `update_date` không match dòng nào trong `d`, không đóng góp vào `SUM`; `ArtifactDocumentDateServiceTest.missingUpdateDateFieldYieldsNullWithoutThrowing` xác nhận service ghi `updateAt=null` cho file này (không tự suy diễn). |
| AC-PHASE-DWELL-TIME-6 | PASS (theo Gate #1) | `UNIQUE (artifact_snapshot_id)` + `ON CONFLICT DO NOTHING` — rescan cùng `content_hash` giữ `artifact_snapshot_id` cũ (theo `insertSnapshot` hiện có), nên `document_create_at`/`document_update_at` không bị ghi đè. **Chưa có integration test thật chạy scan 2 lần trên DB** (cần Postgres — xem mục 8). |
| AC-PHASE-DWELL-TIME-7 | PASS | Không sửa dòng code nào trong `TicketPhaseEvaluatorService`; full BE suite (596 tests, gồm `TicketPhaseEvaluatorServiceTest`) chạy PASS không đổi. `findDetail` vẫn build `row.phaseCode/phaseName/...` từ `tbl_fact_ticket_dashboard_snapshot`/`tbl_fact_ticket_phase_status` như cũ, không đụng field này. |
| AC-PHASE-DWELL-TIME-8 | PASS | 3 file locale (`en`/`ja`/`vi`) thêm đúng key `Pages.PmDashboard.phaseDwellTime` = "Phase Dwell Time" / "各フェーズの滞留時間" / "Thời gian kẹt ở từng phase". |
| AC-PHASE-DWELL-TIME-9 | PASS | Migration `V512` chỉ có `CREATE TABLE IF NOT EXISTS` — không ALTER/DROP bảng nào hiện có; grep xác nhận không có `ALTER TABLE`/`DROP` trong diff. |
| AC-PHASE-DWELL-TIME-10 | PASS | `findPhaseDwellTime` bọc try/catch, lỗi trả `List.of()` → FE nhận `phaseDwellTime: []` → không có phase nào render giá trị (mặc định `"-"` vì không tìm thấy item cho `phaseCode`) — không throw lên Controller, không retry. |

## 3. List of Changed Files

| file | summary | reason |
|---|---|---|
| `EDCAP_BE/.../db/migration/V512__add_artifact_document_date.sql` | Bảng mới `tbl_fact_artifact_document_date` | Nguồn dữ liệu Dwell Time, additive-only |
| `EDCAP_BE/.../usecase/scanner/ArtifactDocumentDateService.java` | Service mới trích `create_date`/`update_date` từ header | Gate #2/#7 |
| `EDCAP_BE/.../port/out/persistence/ArtifactScannerPersistencePort.java` | + method `upsertArtifactDocumentDates` | Ghi bảng mới |
| `EDCAP_BE/.../adapter/scanner/ArtifactScannerJdbcAdapter.java` | + implement `upsertArtifactDocumentDates` (UPSERT `ON CONFLICT DO NOTHING`) | Gate #1 |
| `EDCAP_BE/.../usecase/scanner/ArtifactScannerService.java` | + dependency `ArtifactDocumentDateService` (constructor 12-arg), gọi sau vòng lặp scan | Wiring vào luồng scan hiện có |
| `EDCAP_BE/.../usecase/pmdashboard/PmDashboardModels.java` | + record `PhaseDwellTimeItem`, + field `phaseDwellTime` trong `DashboardTicketDetail` | Model BE |
| `EDCAP_BE/.../adapter/PmDashboardJdbcAdapter.java` | + `findPhaseDwellTime(ticketId)` + logger + wiring vào `findDetail` | Aggregate query mới |
| `EDCAP_BE/.../web/dto/PmDashboardDtos.java` | + `PhaseDwellTimeItemDto`, + field trong `PmDashboardTicketDetailDto.from(...)` | DTO mở rộng |
| `EDCAP_BE/.../test/.../ArtifactDocumentDateServiceTest.java` (mới) | Unit test service mới (5 case) | Test coverage |
| `EDCAP_BE/.../test/.../ArtifactScannerServiceTest.java` | + `upsertArtifactDocumentDates` no-op trong fake persistence port | Compile fix cho interface mới |
| `EDCAP_BE/.../test/.../PmDashboardServiceTest.java` | + arg `List.of()` cho `DashboardTicketDetail` (2 chỗ) | Compile fix |
| `EDCAP_BE/.../test/.../PmDashboardControllerTest.java` | + arg `List.of()` cho `DashboardTicketDetail` | Compile fix |
| `EDCAP_FE/src/lib/api.ts` | + field `phaseDwellTime` trong `PmDashboardTicketDetail` | FE type |
| `EDCAP_FE/src/pages/pm-dashboard/components/TicketDetailDrawer.tsx` | + component `PhaseDwellTimeCard`, gọi cạnh `<PhaseCard .../>` | UI Card mới |
| `EDCAP_FE/public/locales/{en,ja,vi}/locale.json` | + key `phaseDwellTime` | i18n |
| `EDCAP_FE/src/__ tests __/pm-dashboard/TicketDetailDrawer.test.tsx` | + `phaseDwellTime` fixture (mix có/không giá trị) + assertion Card mới | Regression + AC closure |
| `EDCAP_FE/src/__ tests __/pm-dashboard/PMDashboardPage.test.tsx` | + `phaseDwellTime: []` vào fixture | Compile fix (type mở rộng) |
| `EDCAP_BE/.../adapter/PmDashboardJdbcAdapterPhaseDwellTimeTest.java` (mới, 2026-08-21) | 4 test: AC-1/AC-3/AC-4 SQL-shape, AC-10 fail-soft, + 1 bug-reproduction test cho tổng âm | Bug-hunting pass — bảo vệ regression + pin 1 defect thật (xem mục 7) |
| `EDCAP_BE/.../adapter/scanner/ArtifactScannerJdbcAdapterTest.java` (2026-08-21) | + 2 test: `ON CONFLICT DO NOTHING` đúng (không phải `DO UPDATE`), danh sách rỗng không gọi SQL nào | AC-6 |
| `EDCAP_BE/.../migration/ArtifactDocumentDateMigrationTest.java` (mới, 2026-08-21) | Đọc nội dung `V512` thật, assert không `ALTER/DROP`, có `UNIQUE(artifact_snapshot_id)` | AC-9, cùng pattern `CiRunMetadataMigrationTest` đã có |
| `EDCAP_FE/src/__ tests __/pm-dashboard/TicketDetailDrawer.test.tsx` (2026-08-21) | + test sort theo `phaseOrder` với input xáo trộn; + `afterEach(cleanup)` (thiếu — DOM leak giữa nhiều `render()` trong cùng file vì `vite.config.ts` không bật `test.globals`); + 1 `it.fails(...)` pin bug thật (xem mục 7) | AC-1 (FE) + bug-hunting |
| `EDCAP_FE/src/__ tests __/i18n/phaseDwellTimeLocale.test.ts` (mới, 2026-08-21) | Đọc trực tiếp 3 file `locale.json` thật (không mock `t()`), so khớp byte-for-byte | AC-8 — trước đây KHÔNG có test nào từng đọc giá trị JA/VI thật |
| `EDCAP_BE/.../adapter/PmDashboardJdbcAdapter.java` (fix, 2026-08-21) | `formatDwellTimeSeconds`: nhánh mới `if (dwellSeconds < 0) { LOG.warn(...); return null; }` | Fix BUG-PHASE-DWELL-TIME-1 (`OI-14`) |
| `EDCAP_FE/src/pages/pm-dashboard/components/TicketDetailDrawer.tsx` (fix, 2026-08-21) | `PhaseDwellTimeCard`: `[...detail.phaseDwellTime]` → `[...(detail.phaseDwellTime ?? [])]` | Fix BUG-PHASE-DWELL-TIME-2 (`OI-15`) |
| `docs/changes/PHASE-DWELL-TIME/open-issues.md` (2026-08-21) | + `OI-PHASE-DWELL-TIME-14`/`15` cho 2 defect tìm thấy | Điểm mơ hồ đưa trở lại Open Issues theo đúng chỉ thị |

## 4. Run Command and Results

| command | result | note |
|---|---|---|
| `mvn -o clean compile` (EDCAP_BE) | BUILD SUCCESS | Full clean compile, không lỗi |
| `mvn -o test` (EDCAP_BE, full suite) | BUILD SUCCESS — Tests run: 603, Failures: 0, Errors: 0 | 596 trước đó + 7 test mới (2026-08-21 bug-hunting pass) = 603, zero regression |
| `npx tsc --noEmit` (EDCAP_FE) | Exit 0, không lỗi | Sau khi sửa 2 fixture test bị thiếu field + thêm test mới |
| `npm run build` (EDCAP_FE) | BUILD SUCCESS (vite build) | Cảnh báo chunk-size >500kB là pre-existing, không liên quan |
| `npx vitest run` (EDCAP_FE, full suite) | 52 test files passed, 344 tests passed | 339 trước đó + 5 test mới (2026-08-21) = 344, zero regression. Chi tiết: `test-results.md` |
| ArchUnit `ArchitectureTest` | KHÔNG CHẠY ĐƯỢC | Không tìm thấy file `ArchitectureTest.java` trong repo (`find`/`Glob` không có kết quả) — impl-plan giả định file này tồn tại nhưng thực tế không có trong codebase hiện tại; không thể verify hexagonal layering bằng ArchUnit tự động. Đã tự rà bằng tay: class mới đều nằm đúng layer `application`/`infrastructure`, không có import ngược. |
| `flyway migrate` trên môi trường chia sẻ | KHÔNG CHẠY | Theo `00-safety.md §3`, phải hỏi người dùng trước — chưa được yêu cầu chạy trong task này. |

## 5. Self-Check using Review Checklist

| checklist area | result | note |
|---|---|---|
| 1. Đối chiếu specification / AC | PASS | Xem mục 2 — 9/9 AC khớp (trừ 2 AC cần integration/DB thật, ghi rõ ở mục 8) |
| 2.1 Số, số full-width, số chữ số, độ chính xác | PASS | `hh:mm:ss` dùng `Math.round` trên giây, không giới hạn `hh` 24 (dùng `hours = totalSeconds/3600`, không modulo 24) |
| 2.2 Loại ký tự, encoding, locale | PASS | Đọc blob bằng `StandardCharsets.UTF_8` giống pattern hiện có; locale 3 file đúng theo spec §5 |
| 2.3 Literal / Magic Number / Master Data | PASS | Danh sách 7 phase dùng literal SQL `IN ('1','3','4','5','6','7','8')` — nhất quán với cách các subquery khác trong cùng file dùng literal (không có bảng cấu hình riêng cho scope này theo spec) |
| 2.4 Chuyển trạng thái, boundary value, exception | PASS | Test cover: header đủ cặp, chỉ ngày, thiếu `update_date`, giá trị không parse được, file không tồn tại/thiếu trong tree |
| 3. FE Review | PASS | `PhaseDwellTimeCard` dùng `forwardRef`? — **không cần** vì đây không phải reusable UI component theo `10-style.md` (nó là page-level composed component giống `PhaseCard`/`OpenIssuesCard` hiện có, không dùng `forwardRef` — nhất quán pattern sẵn có trong cùng file) |
| 4. BE/API Review | PASS | Controller không đổi (`PmDashboardController`), không thêm `ResponseEntity` ad-hoc; port method mới theo interface pattern hiện có |
| 5. DB/Migration Review | PASS | `V512` chỉ `CREATE TABLE IF NOT EXISTS`, có `created_at/created_by/updated_at/updated_by` theo chuẩn; không ALTER/DROP |
| 6. Security/Privacy Review | PASS | Không log nội dung file, chỉ log `ticketId`/`sourcePath`/`missingField`; không có input mới từ user |
| 7. Operation/Maintenance Review | PASS | Không có batch mới; backfill tự nhiên qua lần scan tiếp theo (Gate #4, không trigger FULL scan tự động) |
| 8. Test Review | PARTIAL | Unit test BE/FE đầy đủ; **thiếu** integration test thật trên Postgres cho AC-2 (cộng dồn nhiều file/1 phase) và AC-6 (rescan) — không có môi trường DB truy cập được trong phiên làm việc này (xem mục 8) |
| 9. Documentation/Traceability Review | PASS | self-review.md này được cập nhật đầy đủ theo yêu cầu |
| 10. Release/Rollback Review | PASS | Rollback code = git revert (additive); rollback DB = giữ bảng mới, không cần DROP ngay; chưa chạy migrate trên môi trường chia sẻ |

## 6. Test Plan Corresponding Status

Chưa có `test-plan.md`/`blackbox-testcases.md` soạn sẵn cho ticket này (theo `spec-pack.md §13`: "Chi tiết test case cụ thể sẽ ở `test-plan.md`/`blackbox-testcases.md` (chưa soạn)"). Đối chiếu theo `spec-pack.md §13 Test Strategy Summary`:

| case (từ §13) | status | evidence |
|---|---|---|
| Unit BE: header đủ cặp create/update_date | PASS | `ArtifactDocumentDateServiceTest.extractsFullHeaderWithDateAndTime` |
| Unit BE: header chỉ có ngày | PASS | `ArtifactDocumentDateServiceTest.extractsDateOnlyHeader` |
| Unit BE: header thiếu field | PASS | `ArtifactDocumentDateServiceTest.missingUpdateDateFieldYieldsNullWithoutThrowing` |
| Unit BE: parse fail | PASS | `ArtifactDocumentDateServiceTest.unparsableHeaderValueYieldsNullWithoutThrowing` |
| Unit BE: file không tồn tại trong tree | PASS | `ArtifactDocumentDateServiceTest.skipsFilesNotExistingOrMissingFromTree` |
| Unit BE: subquery Dwell Time (1 phase 1 file / ≥2 file / rỗng) | PARTIAL | Cấu trúc SQL bảo vệ bằng mock-test mới (`PmDashboardJdbcAdapterPhaseDwellTimeTest`: anchor `tbl_dim_phase`, đúng 7 phase_code, điều kiện NOT NULL trong `ON`) — nhưng ngữ nghĩa runtime thật của `SUM`/`EXTRACT(EPOCH ...)` trên Postgres vẫn NOT_RUN (không mock được, xem mục 8) |
| Unit FE: render Card giá trị thật + `"-"` | PASS | `TicketDetailDrawer.test.tsx` assertion mới (`"Phase Dwell Time"`, `"03:20:00"`, `"-"`) |
| Unit FE: render đúng thứ tự `phaseOrder` khi input không theo thứ tự | PASS (mới, 2026-08-21) | `TicketDetailDrawer.test.tsx` — test riêng với input xáo trộn, assert qua `compareDocumentPosition` |
| Unit FE: locale JA/VI thật khớp `spec-pack.md §5` | PASS (mới, 2026-08-21) | `phaseDwellTimeLocale.test.ts` — trước đây KHÔNG có test nào đọc giá trị JSON thật (mock `t()` luôn trả `defaultValue`) |
| Regression: `TicketDetailDrawer.test.tsx` hiện có không vỡ | PASS | Test cũ (missing sections, open issues, reviews...) vẫn PASS sau khi thêm field |
| Regression: `TicketPhaseEvaluatorServiceTest.java` không cần đổi | PASS | Không sửa file này, 4 test vẫn PASS |
| Integration/Backfill trên ticket cũ có sẵn snapshot | NOT_RUN | Cần Postgres + dữ liệu thật — không có trong phiên làm việc này |
| Fail-soft AC-10 (lỗi subquery không hỏng `/detail`) | PASS (mới, 2026-08-21) | `PmDashboardJdbcAdapterPhaseDwellTimeTest#findDetail_whenPhaseDwellTimeQueryThrows_stillReturnsDetailWithEmptyPhaseDwellTime` |
| Idempotency AC-6 (SQL dùng đúng `ON CONFLICT DO NOTHING`) | PASS (mới, 2026-08-21) | `ArtifactScannerJdbcAdapterTest#upsertArtifactDocumentDates_usesOnConflictDoNothing_notDoUpdate` |
| Additive-only AC-9 (migration không ALTER/DROP) | PASS (mới, 2026-08-21) | `ArtifactDocumentDateMigrationTest` — đọc nội dung file thật |

## 7. Bugs Found and Resolved

**Cập nhật 2026-08-21 (vòng 3 — fix)**: người dùng đã duyệt phương án khuyến nghị cho cả 2 defect
tìm thấy ở vòng bug-hunting (xem `open-issues.md` Resolution Log, 2026-08-21). Cả 2 **ĐÃ ĐƯỢC SỬA**
trong cùng phiên; 2 reproduction test tương ứng đã được flip thành regression test bình thường.

| bug | cause | fix | test |
|---|---|---|---|
| BUG-PHASE-DWELL-TIME-1: `formatDwellTimeSeconds` sinh chuỗi âm/không zero-pad (`"-1:00:00"`) khi tổng giây < 0 (file có `document_update_at < document_create_at`) | Không có validation/clamp giữa giá trị SQL trả về và `String.format("%02d:%02d:%02d", ...)` — âm chỉ ảnh hưởng phần `hours`, `minutes`/`seconds` vẫn dương do phép `%` | **ĐÃ SỬA**: `formatDwellTimeSeconds` thêm nhánh `if (dwellSeconds < 0)` → trả `null` (FE render `"-"`, cùng nhánh AC-3/AC-4) + `LOG.warn(...)` kèm `ticketId`/`phaseCode`/`dwellSeconds` để phát hiện dữ liệu header bị đảo | `PmDashboardJdbcAdapterPhaseDwellTimeTest#findPhaseDwellTime_rowMapper_negativeSumRendersAsDash_notAMalformedNegativeString` (đã flip từ reproduction test, PASS) |
| BUG-PHASE-DWELL-TIME-2: `PhaseDwellTimeCard` crash toàn bộ `TicketDetailDrawer` (`TypeError: detail.phaseDwellTime is not iterable`) khi field `phaseDwellTime` bị thiếu khỏi response (cache cũ/lệch phiên bản rollout) | `[...detail.phaseDwellTime].sort(...)` không có guard cho trường hợp field `undefined` | **ĐÃ SỬA**: `[...detail.phaseDwellTime]` → `[...(detail.phaseDwellTime ?? [])]` trong `PhaseDwellTimeCard` | `TicketDetailDrawer.test.tsx` → `"degrades gracefully instead of crashing the whole drawer when phaseDwellTime is missing from the API response"` (đã flip từ `it.fails`, PASS) |

## 8. Unprocessed / Pending / Accepted Risk

| item | reason | impact | owner | deadline |
|---|---|---|---|---|
| Chưa chạy integration test thật (Postgres) cho AC-2 (cộng dồn nhiều file/1 phase) và AC-6 (rescan giữ nguyên giá trị) | Môi trường làm việc hiện tại không có Postgres khả dụng để chạy `SUM(EXTRACT(EPOCH FROM ...))`/`ON CONFLICT` thật; đã verify logic bằng unit test service + mock-SQL-shape test (2026-08-21) + đọc kỹ SQL, nhưng chưa test end-to-end trên DB thật | Trung bình — rủi ro lệch cú pháp SQL Postgres cụ thể (vd. kiểu trả về của `EXTRACT`) chỉ phát hiện khi chạy trên DB thật | Người review / QA trước khi merge | Trước khi deploy lên môi trường có DB |
| ArchUnit `ArchitectureTest` không tồn tại trong repo để chạy | File được nhắc trong `CLAUDE.md`/`impl-plan.md` nhưng không có trong `src/test` hiện tại (`Glob`/`find` không tìm thấy) | Thấp — đã tự rà layer boundary bằng tay, class mới đặt đúng `application`/`infrastructure` | Team (có thể là gap từ trước, không phải do ticket này) | Không áp dụng cho ticket này |
| `flyway migrate` chưa chạy trên môi trường chia sẻ | Theo `00-safety.md §3`, cần hỏi người dùng trước khi chạy DB migration | Không có — chỉ là bước tiếp theo cần xác nhận | Người dùng | Trước khi merge/deploy |

~~BUG-PHASE-DWELL-TIME-1~~ và ~~BUG-PHASE-DWELL-TIME-2~~ đã được fix ở vòng 3 (2026-08-21) — xem
mục 7. Không còn nằm trong danh sách rủi ro mở.

## 9. Exception Record

| exception_type | source_section | reason | approved_by_role | expiry | follow_up | owner | status |
|---|---|---|---|---|---|---|---|
| | | | | | | | |

## 10. AI-generated predictions

- Đã giả định format ngày trong header có thể là `yyyy-MM-dd`, `yyyy-MM-dd HH:mm:ss`, hoặc
  `yyyy-MM-dd'T'HH:mm:ss` — không có tài liệu nào chốt chính xác 100% format tự khai báo trong
  header thực tế của các ticket cũ. **Chưa được người dùng xác nhận trực tiếp** — nếu format thực
  tế khác (vd. có timezone offset riêng), cần bổ sung formatter mới, không sửa logic hiện có.
- Đã chọn JOIN qua `tbl_dim_artifact_type` (theo đúng chữ trong `impl-plan.md §8`) thay vì dùng trực
  tiếp cột `phase_id` đã denormalize sẵn trên `tbl_fact_artifact_snapshot` (2 cách tương đương về kết
  quả vì `snapshot.phase_id` được copy từ `artifactType.phaseId()` lúc insert) — chọn theo đúng văn bản
  spec để tránh mơ hồ khi review, dù về mặt kỹ thuật join qua `phase_id` trực tiếp sẽ đơn giản hơn 1 bậc.

**Đối chiếu 4 mục Gate đã RESOLVED với code thực tế:**

| # | Quyết định đã RESOLVED cần đối chiếu với code thực tế | Code có khớp đúng quyết định? |
|---|---|---|
| 1 | Gate #7: service tự `readBlob` lại theo `source_path` (không refactor chia sẻ content); số API call GitHub tăng thêm thực tế ≤ 8/lần scan | **CÓ** — `ArtifactDocumentDateService.extract(...)` tự gọi `source.readBlob(repoFullName, treeEntry.sha())` cho từng file tồn tại trong `snapshotsByFileName` (tối đa 8 file `CHANGE_TARGET_FILES`), không đụng vòng lặp parse hiện có (366-441) |
| 2 | Gate #8: service đọc header cho cả 8 file trong `CHANGE_TARGET_FILES` (gồm `blackbox-testcases.md`), không tự loại trừ theo tên file | **CÓ** — service lặp qua toàn bộ `snapshotsByFileName` (được build từ đủ 8 phần tử `CHANGE_TARGET_FILES` trong `scanTicketDirectory`), không có điều kiện loại trừ theo tên file nào |
| 3 | Gate #1: bảng `tbl_fact_artifact_document_date` có `UNIQUE (artifact_snapshot_id)`, ghi bằng `INSERT ... ON CONFLICT (artifact_snapshot_id) DO NOTHING` | **CÓ** — `V512__add_artifact_document_date.sql` có `CONSTRAINT uq_artifact_document_date_snapshot UNIQUE (artifact_snapshot_id)`; `ArtifactScannerJdbcAdapter.upsertArtifactDocumentDates` dùng đúng `ON CONFLICT (artifact_snapshot_id) DO NOTHING` |
| 4 | Migration version thực tế dùng — đề xuất `V512` dựa trên `V511` là bản cao nhất tại thời điểm viết tài liệu; xác nhận lại ngay trước khi merge vì có thể đã có migration khác chen vào | **CÓ, đã xác nhận lại** — `Glob` xác nhận `V511__ai_quality.sql` vẫn là bản cao nhất hiện có tại thời điểm implement (2026-08-21); dùng `V512__add_artifact_document_date.sql` |

## 11. Items reviewed by humans

- 2026-08-21: người dùng đã xem và duyệt phương án fix cho cả 2 bug tìm thấy ở lượt bug-hunting
  (BUG-PHASE-DWELL-TIME-1: hiển thị `"-"` khi tổng âm; BUG-PHASE-DWELL-TIME-2: thêm guard mặc định
  `?? []`) — xem `open-issues.md` Resolution Log. Đây là quyết định phạm vi/hành vi do người dùng
  đưa ra trực tiếp, không phải AI tự suy đoán.
- Phần còn lại (migration DB, câu SQL aggregate `EXTRACT(EPOCH FROM ...)` trên Postgres thật) vẫn
  **chưa** được người dùng/reviewer con người xem lại trực tiếp — cần review trước khi merge.

## 12. Final Self-Verdict

- **PASS** (nâng từ NEEDS_UPDATE lên PASS sau khi fix ở vòng 3, 2026-08-21) — Code compile sạch,
  toàn bộ 603 test BE + 344 test FE đều PASS (không regression). Cả 2 defect thật tìm được ở lượt
  bug-hunting (BUG-PHASE-DWELL-TIME-1: dwell time âm hiển thị sai format; BUG-PHASE-DWELL-TIME-2:
  FE crash toàn Drawer khi thiếu field) đã được người dùng duyệt phương án và fix xong; 2
  reproduction test tương ứng đã flip thành regression test bình thường xác nhận đúng hành vi mới
  (`open-issues.md` `OI-14`/`OI-15` đã RESOLVED). Vẫn còn 1 khoảng trống cũ, không chặn merge
  nhưng cần theo dõi: chưa chạy được integration test trên Postgres thật cho AC-2 (cộng dồn)/AC-6
  (rescan runtime semantics), và ArchUnit không tồn tại để verify tự động layer boundary (xem mục
  8). Trước khi coi tính năng "hoàn thiện toàn phần" ở mức vận hành thật: (1) review SQL aggregate
  mới (`EXTRACT(EPOCH FROM ...)`) trên Postgres thật, (2) xác nhận người dùng trước khi chạy
  `flyway migrate` trên môi trường chia sẻ theo `00-safety.md §3`.
