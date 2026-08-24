# impl-plan

**Ticket ID**: PHASE-DWELL-TIME
**Vai trò**: Principal Engineer — implementation plan.
**Create date**: 2026-08-21
**Update date**: 2026-08-21

---

## Phương châm implement

1. **Additive-only, không đụng luồng ghi hiện có**: không sửa
   `TicketPhaseEvaluatorService`, `upsertTicketPhaseStatus`,
   `tbl_fact_ticket_phase_status`, không ALTER/DROP bảng hiện có.
2. **Nguồn dữ liệu = header tự khai báo, không phải cột DB `created_at`**:
   `create_date`/`update_date` parse qua `MarkdownParserCore.headerMetadata()`,
   lưu ở 1 bảng mới riêng.
3. **1 service dùng chung cho cả 7 file**, không vá 3 parser cũ khác kiến
   trúc, không viết parser "full" riêng cho `blackbox-testcases.md`.
4. **Không tái sử dụng `updateSnapshotParsedSummary`** — tránh ghi đè dữ
   liệu của 4 parser chuyên biệt hiện có.
5. **Fail-soft ở tầng đọc**: lỗi tính Dwell Time cho 1 phase không được
   làm hỏng cả response `/detail` — bọc try/catch nội bộ, trả `null` cho
   field đó, không throw ra Controller.
6. **Đi đúng pattern hexagonal + test pattern đã có** của domain PM
   Dashboard (Service mỏng → Port → JDBC Adapter → Controller → DTO
   record), không tự sáng tạo pattern mới.
7. **Layout**: Card "Phase" hiện tại (`PhaseCard`, dòng 115-157) **giữ
   nguyên không đổi**; thêm 1 Card **mới** (tên tạm `PhaseDwellTimeCard`)
   ngay dưới Card "Phase" (cạnh `<PhaseCard detail={detail} t={t} />`,
   dòng 745), hiển thị danh sách 7 phase dạng list, mỗi dòng có
   `phaseName` (nguồn: `tbl_dim_phase.phase_name`) + giá trị Dwell Time.
8. **Công thức**: Σ per-file `(document_update_at − document_create_at)`
   trong cùng 1 phase (đọc từ bảng mới `tbl_fact_artifact_document_date`).
   File có `document_create_at` nhưng chưa có `document_update_at` không
   đóng góp vào tổng; nếu phase N không còn file nào đủ cặp, hiển thị
   `"-"`. Về mặt SQL: `SUM(...)` chỉ trên các dòng có cả 2 cột NOT NULL —
   `SUM` trả `NULL` khi không có dòng nào khớp, ánh xạ tự nhiên sang
   `"-"` ở FE.
9. **Root package xác nhận**: `com.sdd.platform` (ví dụ
   `com.sdd.platform.application.usecase.scanner.ArtifactScannerService`) —
   mọi class/service mới đặt dưới package này, không tự đoán package khác.
10. **`CHANGE_TARGET_FILES` có 8 phần tử, không phải 7**
    (`ArtifactScannerService.java:60-68`): spec-pack, impl-plan,
    review-checklist, self-review, test-plan, test-results, report, và
    `blackbox-testcases.md`. Service mới trích header phải tự đọc cả 8
    file này (không dựa vào các branch parse chuyên biệt sẵn có — file
    `blackbox-testcases.md` hiện không có branch đọc nội dung nào trong
    vòng lặp hiện tại).
11. **Không có blob content dùng chung sẵn** trong `scanTicketDirectory` —
    mỗi branch parse (dòng 366-441) tự gọi `source.readBlob(...)` độc
    lập. Service mới trích header sẽ tự gọi lại `source.readBlob(...)`
    theo `source_path` (đã có trong `ArtifactSnapshot`/
    `snapshotsByFileName`) — chấp nhận thêm tối đa +8 GitHub API
    call/lần scan/ticket (đã đổi từ giả định ban đầu "tái dùng blob, 0
    API call mới" sang phương án đã xác nhận khả thi này — xem Gate #7).

---

## Danh sách file thay đổi

### File mới

| File | Mục đích |
|---|---|
| `EDCAP_BE/src/main/resources/db/migration/V512__add_artifact_document_date.sql` (số version đã xác nhận — cao nhất hiện có là `V511__ai_quality.sql`) | Bảng mới lưu `create_date`/`update_date` đã parse (tên đề xuất `tbl_fact_artifact_document_date`) |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/.../ArtifactDocumentDateService.java` (root package xác nhận `com.sdd.platform`; gói con cụ thể — chốt ở Gate) | Service dùng chung: đọc `MarkdownParserCore.parse(...).headerMetadata()` cho **8 file** (`CHANGE_TARGET_FILES` xác nhận có 8, không phải 7 — gồm cả `blackbox-testcases.md`), trả `create_date`/`update_date` đã parse |
| Port method mới trên `ArtifactScannerPersistencePort` (hoặc port mới riêng, chốt ở Gate) | Ghi kết quả vào bảng mới |
| Method mới trên adapter tương ứng (`ArtifactScannerJdbcAdapter` hoặc adapter mới) | Implement port trên — theo đúng pattern thực tế đang dùng trong class này: 1 lệnh `jdbc.update`/`queryForObject` đơn lẻ, **không có** `TransactionTemplate`/`@Transactional` nào trong `ArtifactScannerJdbcAdapter`/`ArtifactScannerService` hiện tại (xác nhận bằng grep — 0 kết quả); không tự thêm cơ chế transaction mới không có tiền lệ trong class này |
| `EDCAP_BE/src/test/UnitTest/java/com/sdd/platform/.../ArtifactDocumentDateServiceTest.java` | Unit test service mới |
| `EDCAP_FE/src/__ tests __/pm-dashboard/TicketDetailDrawer.test.tsx` (chú ý tên thư mục có khoảng trắng literal `__ tests __`) — bổ sung case Dwell Time (mở rộng file có sẵn, không phải file mới) | — |

### File sửa

| File | Vị trí | Thay đổi |
|---|---|---|
| `PmDashboardModels.java` | record `DashboardTicketDetail` (266-277) | + field `phaseDwellTime` |
| `PmDashboardJdbcAdapter.java` | `findDetail(...)` (215-270), 1 nơi khởi tạo (259-269) | + subquery/join đọc bảng mới, group theo phase; + tham số vào constructor record |
| `PmDashboardDtos.java` | record `PmDashboardTicketDetailDto` (334-360) | + field `phaseDwellTime` + cập nhật `from(...)` |
| `PmDashboardRepositoryPort.java` | interface | + method mới nếu chọn hướng port riêng (xem Gate) |
| `ArtifactScannerService.java` | `scanTicketDirectory` (280-447, vòng lặp parse chính 366-441, method kết thúc dòng 446 `return new ScanCounts(...)`) | + gọi service mới **ngay sau khi vòng lặp `CHANGE_TARGET_FILES` (dòng 366-441) kết thúc, trước lời gọi `ticketPhaseEvaluatorService.evaluateAndPersist(...)` (dòng 443-445)** — tức chèn tại dòng 442; service mới tự `readBlob` lại theo `source_path`, **không sửa** logic hiện có trong vòng lặp. Constructor `ArtifactScannerService` có 12 dependency (constructor chính, dòng 100-124) + 2 overload "backward-compatible" (2-arg dòng ~126-140, 11-arg dòng ~147-152) dùng trong test — thêm dependency mới **chỉ vào constructor 12-arg chính**, cập nhật 2 overload để fill `null`/default cho tham số mới → xác nhận không vỡ compile `ArtifactScannerServiceTest.java` (test chỉ dùng 2 overload, không gọi constructor chính trực tiếp) |
| `lib/api.ts` | interface `PmDashboardTicketDetail` | + field `phaseDwellTime: { phaseCode: string; phaseOrder: number; phaseName: string; dwellTime: string \| null }[]` |
| `lib/utils.ts` | cuối file, cạnh `formatDateTime` | + hàm format duration mới, nếu còn giữ ở FE (xem mục Gate) |
| `TicketDetailDrawer.tsx` | hàm `PhaseCard` (115-157) | **KHÔNG đổi** — giữ nguyên nguyên trạng |
| `TicketDetailDrawer.tsx` | Card mới (tên tạm `PhaseDwellTimeCard`), gọi cạnh `<PhaseCard detail={detail} t={t} />` (dòng 745) | Component mới: render danh sách 7 dòng từ `detail.phaseDwellTime`, mỗi dòng `phaseName` + `dwellTime ?? "-"` |
| `TicketDetailDrawer.test.tsx` | object `detail` giả lập (26-114) | + field `phaseDwellTime` để khớp type |
| `public/locales/{en,ja,vi}/locale.json` | `Pages.PmDashboard` | + 1 key tên field mới |
| `ArtifactScannerServiceTest.java` | constructor test setup | + mock dependency mới nếu constructor `ArtifactScannerService` đổi |

---

## Các bước thay đổi

1. **Gate quyết định kỹ thuật** (xem mục "Gate trước implementation") —
   phải xong trước khi viết code.
2. Viết migration mới (bảng lưu `create_date`/`update_date`) — chạy local,
   **chưa** `flyway migrate` trên môi trường chia sẻ (hỏi người dùng
   trước).
3. Viết service mới trích header (đọc blob, gọi `MarkdownParserCore`,
   parse `create_date`/`update_date` — xử lý cả format chỉ có ngày lẫn có
   giờ).
4. Viết port method mới + adapter implement (ghi bảng mới).
5. Gắn service mới vào `ArtifactScannerService.scanTicketDirectory` (đọc
   thêm, không đổi nhánh hiện có).
6. Viết subquery/join tính Dwell Time theo phase trong
   `PmDashboardJdbcAdapter.findDetail(...)` (cộng dồn theo
   `tbl_dim_artifact_type.phase_id`).
7. Cập nhật `PmDashboardModels`/`PmDashboardDtos` (record + factory).
8. Cập nhật FE: type, Card mới `PhaseDwellTimeCard`, test hiện có.
9. Cập nhật i18n 3 locale.
10. Viết test mới (BE unit, FE unit, AC-closure mount test).
11. Chạy lại toàn bộ test hiện có bị ảnh hưởng
    (`ArtifactScannerServiceTest`, `TicketDetailDrawer.test.tsx`,
    ArchUnit) — xác nhận regression = 0.
12. Xin xác nhận người dùng trước khi chạy `flyway migrate` trên môi
    trường chia sẻ.

---

## Ý định thay đổi theo class/function/method

> Ghi ý định, không viết full code.

**`ArtifactDocumentDateService`** (tên/gói tạm, chốt ở Gate)
- Input: nội dung blob (String) + `sourcePath` của 1 file, HOẶC danh sách
  snapshot cần xử lý cho 1 ticket (chốt hình dạng input ở Gate).
- Xử lý: gọi `new MarkdownParserCore().parse(content, sourcePath)`, lấy
  `headerMetadata().get("create_date")`/`get("update_date")`, parse string
  → `OffsetDateTime` (xử lý cả format `YYYY-MM-DD` và `YYYY-MM-DD HH:mm:ss`;
  case parse fail → trả `null`, không throw).
- Output: record/DTO nội bộ `{ artifactSnapshotId, createAt: OffsetDateTime|null, updateAt: OffsetDateTime|null }`.
- Không phụ thuộc `ImplPlanParseService`/`TestPlanParseService`/
  `TestResultsParseService`/4 parser chuyên biệt.

**Port method mới (tên tạm `upsertArtifactDocumentDates` hoặc tương đương)**
- Input: danh sách record ở trên.
- Hành vi: upsert vào bảng mới theo `artifact_snapshot_id` (chốt upsert
  vs insert-only ở Gate, phụ thuộc quyết định UNIQUE constraint).
- Không gọi `updateSnapshotParsedSummary`.

**`PmDashboardJdbcAdapter.findDetail(...)` — sửa**
- Thêm 1 subquery/CTE mới: JOIN bảng mới + `tbl_fact_artifact_snapshot`
  (lấy `ticket_id`, `artifact_type_id`) + `tbl_dim_artifact_type`
  (`phase_id`) + `tbl_dim_phase` (`phase_code`, `phase_order`, `phase_name`).
- Điều kiện WHERE: `ticket_id = :ticketId`, lọc theo 7
  `artifact_type_code` trong scope (hoặc lọc theo `phase_code IN
  ('1','3','4','5','6','7','8')` sau khi join `tbl_dim_phase`).
- Group theo `phase_id`/`phase_code`, tính `SUM(document_update_at −
  document_create_at)` chỉ trên dòng có cả 2 cột NOT NULL (cộng dồn theo
  file trong cùng phase).
- Bọc phần tính Dwell Time trong try/catch nội bộ method — lỗi không làm
  hỏng phần còn lại của `findDetail`.
- Sửa **1 nơi khởi tạo** `DashboardTicketDetail` (dòng 259-269) — thêm
  tham số `phaseDwellTime`.
- Rủi ro hiệu năng: subquery mới thêm 1 lần JOIN cho 1 ticket — chấp nhận
  được (tương tự các subquery con khác đã có trong file:
  `countReviews`, `findMissingEvidence`, ...).

**`ArtifactScannerService.scanTicketDirectory` — sửa**
- Vòng lặp parse chính nằm ở dòng 366-441 (8 file `CHANGE_TARGET_FILES`,
  mỗi branch tự gọi `source.readBlob(...)` riêng — **không có** map blob
  dùng chung). Method kết thúc dòng 446 (`return new ScanCounts(read,
  written)`), sau lời gọi `ticketPhaseEvaluatorService.evaluateAndPersist(...)`
  (dòng 443-445).
- Thêm 1 lời gọi tới service mới **tại dòng 442** (ngay sau vòng lặp,
  trước `ticketPhaseEvaluatorService`) — service mới tự `readBlob` lại
  theo `source_path` của từng `ArtifactSnapshot` trong
  `snapshotsByFileName` (đã build ở dòng 304+), **không** cố tái sử dụng
  content từ các branch parse hiện có (không tồn tại biến dùng chung phù
  hợp) — chấp nhận thêm tối đa +8 GitHub API call/lần scan/ticket.
- Không sửa bất kỳ nhánh `if (...).equalsIgnoreCase(...))` hiện có (spec-pack,
  self-review, report, ...).

**FE format duration (tên tạm, `lib/utils.ts`)**
- Khuyến nghị: nhận thẳng `dwellTime: string | null` đã format sẵn từ BE,
  KHÔNG tính lại ở FE — giảm rủi ro lệch công thức giữa BE/FE.
- Nếu BE trả sẵn chuỗi `hh:mm:ss`, FE **không cần** viết helper riêng —
  chỉ cần render trực tiếp `dwellTime ?? "-"`. Quyết định cuối cùng chốt
  ở Gate.

**`PhaseCard` (`TicketDetailDrawer.tsx`) — KHÔNG sửa**
- Code thực tế (115-157) chỉ render **1 phase hiện tại** (không phải
  danh sách nhiều phase). Giữ nguyên 100%, không nhận thêm prop/field nào.

**`PhaseDwellTimeCard` (tên tạm, component mới trong `TicketDetailDrawer.tsx`)**
- Nhận `detail.phaseDwellTime` (mảng 7 phần tử `{ phaseCode, phaseOrder,
  phaseName, dwellTime }`, xem `spec-pack.md §7.2`).
- Render danh sách dọc, sort theo `phaseOrder`, mỗi dòng: `phaseName` +
  giá trị `dwellTime ?? "-"`.
- Gọi cạnh `<PhaseCard detail={detail} t={t} />` (dòng 745), đặt ngay
  dưới Card "Phase" trong Drawer.

---

## Phương châm data/DB/query

- **Bảng mới** (tên đề xuất `tbl_fact_artifact_document_date`):
  - `artifact_snapshot_id UUID NOT NULL REFERENCES tbl_fact_artifact_snapshot(artifact_snapshot_id)`
  - `document_create_at TIMESTAMPTZ` (nullable — parse có thể fail/thiếu)
  - `document_update_at TIMESTAMPTZ` (nullable)
  - `created_at`, `created_by`, `updated_at`, `updated_by` theo chuẩn
    `database.md`
  - Cân nhắc UNIQUE trên `artifact_snapshot_id` (1 dòng/snapshot — khuyến
    nghị, vì mỗi snapshot chỉ có 1 header tại 1 thời điểm) — chốt ở Gate.
    **Đã kiểm tra tiền lệ**: `tbl_fact_ticket_issue` (V397) và
    `tbl_fact_artifact_parsed_section` (V4) là 2 bảng FK tới
    `artifact_snapshot_id` gần nhất trong migration hiện có — **cả 2 đều
    không UNIQUE** (1:nhiều). Không có bảng nào hiện tại dùng UNIQUE 1:1
    trực tiếp trên `artifact_snapshot_id` — quyết định UNIQUE cho bảng
    mới này là quyết định thiết kế mới, không có pattern ép buộc từ
    schema hiện có.
  - **Migration version xác nhận**: cao nhất hiện có là
    `V511__ai_quality.sql` → dùng `V512__add_artifact_document_date.sql`.
- **Query chính (subquery trong `findDetail`)**:
  - Mục tiêu: `tbl_fact_artifact_document_date` JOIN
    `tbl_fact_artifact_snapshot` (lấy `ticket_id`, `artifact_type_id`) JOIN
    `tbl_dim_artifact_type` (lấy `phase_id`) JOIN `tbl_dim_phase` (lấy
    `phase_code`, `phase_order`, `phase_name`).
  - WHERE: `tbl_fact_artifact_snapshot.ticket_id = :ticketId` AND
    `tbl_dim_phase.phase_code IN ('1','3','4','5','6','7','8')`.
  - Aggregate: `SUM(document_update_at − document_create_at)` GROUP BY
    `phase_code`, `phase_order`, `phase_name` — chỉ trên dòng có cả 2 cột
    NOT NULL.
  - **Rủi ro số lượng**: tối đa 7 dòng kết quả/ticket (1 dòng/phase) — quy
    mô nhỏ, không cần phân trang.
  - **Rủi ro performance**: cần JOIN qua 3 bảng cho mỗi lần xem chi tiết 1
    ticket — chấp nhận được ở quy mô hiện tại (tương tự độ phức tạp các
    subquery con khác đã có trong `findDetail`); cân nhắc index trên
    `artifact_snapshot_id` (bảng mới) nếu chưa có từ UNIQUE constraint.
  - Không dùng `vw_artifact_inventory_current` (đã bị định nghĩa lại
    nhiều lần, không phải nguồn cho tính năng này).
- **Ghi (upsert bảng mới)**: chạy trong luồng scan hiện có, ngoài
  transaction bọc network I/O (theo `database.md` — HTTP call GitHub phải
  ở ngoài `@Transactional`; ghi DB bảng mới nên nằm cùng transaction ghi
  snapshot nếu khả thi, hoặc theo đúng pattern transaction hiện có của
  `ArtifactScannerService`).

---

## Phương châm FE/BE contract

- BE → FE: `PmDashboardTicketDetail.phaseDwellTime:
  { phaseCode: string; phaseOrder: number; phaseName: string; dwellTime: string | null }[]`
  — danh sách đúng 7 phần tử (`1,3,4,5,6,7,8`), thứ tự theo `phaseOrder`.
- Không đổi field `phaseCode/phaseName/phaseDescription/phaseCreatedAt/phaseOrder`
  hiện có trên `row`.
- Không tạo endpoint mới, không đổi request shape (`ticketId` path
  variable duy nhất).
- Lỗi khi tính Dwell Time (bất kỳ nguyên nhân nào) → BE trả `dwellTime: null`
  cho phần tử phase liên quan, KHÔNG để lỗi lan ra toàn bộ response
  `/detail` (xem "Phương châm Error/Validation/Logging").
- FE không tự tính lại công thức Dwell Time — chỉ render giá trị BE trả
  (`dwellTime ?? "-"`), tránh lệch công thức 2 phía.

---

## Phương châm Error/Validation/Logging

- Không thêm `ResponseEntity`/`try-catch` ad-hoc trong
  `PmDashboardController` (giữ nguyên `error-handling.md`).
- Lỗi tính Dwell Time cho 1 phase: bọc try/catch **trong
  `PmDashboardJdbcAdapter`/service nội bộ**, log ở mức `WARN` (không phải
  `ERROR` — đây là suy giảm dữ liệu cục bộ, không phải lỗi hệ thống), trả
  `null` cho field đó, không throw tiếp.
- Lỗi parse header (`create_date`/`update_date` thiếu hoặc sai định dạng)
  trong service mới: log `WARN` kèm `sourcePath`, không throw — để luồng
  scan tiếp tục cho các file khác.
- Không log toàn bộ nội dung file `.md` (tránh log payload lớn/nhạy cảm
  không cần thiết) — chỉ log `sourcePath` + tên field bị thiếu/sai.
- Validation: không có input mới từ người dùng cần validate thêm.

---

## Phương châm Test

- **BE unit**: service mới — mock port (không mock domain record), test
  case: header đầy đủ, header chỉ có ngày, header thiếu field, parse fail.
- **BE unit**: subquery/tính Dwell Time — test qua adapter test hoặc
  service test tuỳ nơi đặt logic cộng dồn (chốt ở Gate); case: 1 phase 1
  file, 1 phase ≥2 file (cộng dồn), phase chưa có dữ liệu (`null`).
- **BE regression**: `ArtifactScannerServiceTest.java` — đọc trước khi
  sửa, cập nhật mock nếu constructor đổi.
- **ArchUnit**: chạy lại `ArchitectureTest` sau khi thêm class/package mới.
- **FE unit**: test format duration (nếu còn giữ ở FE) hoặc render trực
  tiếp `dwellTime`; test Card mới với giá trị thật và `"-"`.
- **FE regression**: cập nhật `TicketDetailDrawer.test.tsx` object `detail`
  giả lập.
- **AC Closure**: mount `TicketDetailDrawer` thật (không chỉ Card cô lập)
  để verify field thực sự hiển thị trên trang, theo `testing.md`.
- **Integration/backfill**: test trên ticket có sẵn snapshot lịch sử (sau
  khi chạy service mới) — xác nhận Dwell Time tính đúng.

---

## Phương châm Rollout/Rollback

- Rollout thẳng (additive, không breaking) sau khi migration được duyệt
  và chạy.
- Backfill: chờ xác nhận có cần trigger `FULL` scan ngay sau deploy hay
  không (xem Gate #4 bên dưới).
- Rollback code: git revert (an toàn, additive).
- Rollback DB: ưu tiên giữ bảng mới + ẩn field ở BE/FE; chỉ `DROP TABLE`
  bằng migration mới nếu thực sự cần dọn dẹp và xác nhận không còn
  consumer nào phụ thuộc.

---

## Gate trước implementation

**TOÀN BỘ 7 Gate đã RESOLVED 2026-08-20 — người dùng đã duyệt "Áp dụng
tất cả đề xuất" ở mục dưới ("Quyết định Gate", trước đây là "Đề xuất").
Được phép bắt đầu viết code.** Lịch sử đầy đủ + căn cứ từng quyết định:
xem `open-issues.md` § Resolution Log (mục 2026-08-20 nhóm Gate kỹ
thuật) và `spec-pack.md §18` (H-PHASE-DWELL-TIME-12 → 18).

1. ~~Tên/schema chính xác của bảng mới + có UNIQUE trên `artifact_snapshot_id`~~
   — **RESOLVED**: xem Gate #1 dưới.
2. ~~Package/vị trí chính xác của service mới trích header, và ai gọi nó~~
   — **RESOLVED**: xem Gate #2 dưới.
3. ~~Cách xử lý lỗi per-phase vs toàn request~~ — **RESOLVED**: xem Gate #3 dưới.
4. ~~Có cần trigger `FULL` scan backfill ngay sau deploy hay không~~ —
   **RESOLVED**: xem Gate #4 dưới.
5. ~~Chính sách log khi parse header lỗi~~ — **RESOLVED**: xem Gate #5 dưới.
6. ~~Đọc toàn văn `ArtifactScannerServiceTest.java`~~ — **ĐÃ ĐỌC, RESOLVED**:
   test chỉ dựng `ArtifactScannerService` qua 2 overload backward-compatible
   (2-arg, 11-arg), không gọi constructor 12-arg chính trực tiếp → thêm
   dependency mới vào constructor chính (giữ nguyên 2 overload, fill
   `null`/default cho tham số mới) không làm vỡ compile. Không còn là Gate mở.
7. ~~Chiến lược đọc blob cho service mới trích header~~ — **RESOLVED**:
   xem Gate #7 dưới.
8. ~~Dwell-time có bắt buộc đọc header của `blackbox-testcases.md`~~ —
   **RESOLVED**: xem Gate #8 dưới.

*(Không còn Gate nào OPEN. Bất kỳ điểm mơ hồ MỚI phát sinh trong lúc code
— khác với 8 mục đã liệt kê ở đây — vẫn phải ghi vào `open-issues.md` và
dừng lại hỏi theo `ticket-rules.md`, không tự suy đoán thêm.)*

---

## Quyết định Gate (RESOLVED 2026-08-20 — người dùng đã duyệt toàn bộ đề xuất)

> Theo `ticket-rules.md` § Stop/Ask Conditions, các điểm này trước đây bắt
> buộc phải dừng lại hỏi trước khi code — người dùng đã duyệt "Áp dụng
> tất cả đề xuất" nên **các quyết định dưới đây là chính thức, dùng làm
> căn cứ implement**, không còn là "đề xuất chờ xác nhận".

### Gate #1 — Schema bảng mới + UNIQUE trên `artifact_snapshot_id`

- **Đề xuất tên/schema**: `tbl_fact_artifact_document_date` —
  `artifact_document_date_id UUID PRIMARY KEY DEFAULT gen_random_uuid()`,
  `artifact_snapshot_id UUID NOT NULL REFERENCES tbl_fact_artifact_snapshot(artifact_snapshot_id) ON DELETE CASCADE`,
  `document_create_at TIMESTAMPTZ` (nullable), `document_update_at TIMESTAMPTZ`
  (nullable), + `created_at/created_by/updated_at/updated_by` theo
  `database.md`.
- **Quyết định UNIQUE**: có, `CONSTRAINT uq_artifact_document_date_snapshot
  UNIQUE (artifact_snapshot_id)`. Ghi bằng
  `INSERT ... ON CONFLICT (artifact_snapshot_id) DO NOTHING`.
- **Căn cứ**: `insertSnapshot` hiện tại dùng `ON CONFLICT` trên
  `uq_artifact_snapshot (repository_id, source_path, content_hash)` — nếu
  `content_hash` không đổi (rescan giống nội dung cũ), **cùng 1
  `artifact_snapshot_id` được giữ nguyên** (không tạo row mới). Vì vậy
  UNIQUE trên `artifact_snapshot_id` ở bảng mới + ghi bằng
  `INSERT ... ON CONFLICT (artifact_snapshot_id) DO NOTHING` sẽ tự động
  thỏa AC-6 (rescan cùng nội dung không đổi `document_create_at`/
  `document_update_at` đã có) mà không cần logic so sánh thủ công ở tầng
  service. Nếu content đổi (`content_hash` đổi) → snapshot cũ có
  `artifact_snapshot_id` mới → tự nhiên có 1 dòng mới trong bảng, không
  đụng dòng cũ.
- **Trạng thái**: RESOLVED 2026-08-20 — người dùng đã duyệt. Không có
  tiền lệ schema nào ép buộc hướng này (`tbl_fact_ticket_issue`/
  `tbl_fact_artifact_parsed_section` đều không UNIQUE) nên quyết định
  này là quyết định logic nghiệp vụ mới (rescan behavior), không suy ra
  từ pattern DB có sẵn — đã được duyệt rõ ràng theo đúng Stop/Ask
  Condition của `ticket-rules.md`, không còn là suy đoán.

### Gate #2 — Package/vị trí service mới trích header

- **Quyết định**: đặt class mới `ArtifactDocumentDateService` trong cùng
  package với `ArtifactScannerService`
  (`com.sdd.platform.application.usecase.scanner`), vì nó chỉ được gọi từ
  trong `scanTicketDirectory` và dùng chung `ArtifactScannerSourcePort`
  để đọc blob — không có lý do tách sang package riêng theo domain khác.
- **Quyết định ai gọi**: `ArtifactScannerService` gọi trực tiếp qua constructor
  injection (thêm `ArtifactDocumentDateService` vào constructor 12-arg
  chính, giữ nguyên 2 overload backward-compatible cho test — xem "Danh
  sách file thay đổi").
- **Trạng thái**: RESOLVED 2026-08-20. Căn cứ: đúng pattern hexagonal
  "Service mỏng" đã có, không tạo thêm tầng gián tiếp không cần thiết.

### Gate #3 — Xử lý lỗi per-phase vs toàn request

- **Quyết định**: bọc try/catch **trong `PmDashboardJdbcAdapter`** ở method
  helper mới (ví dụ `findPhaseDwellTime(ticketId)`, gọi tương tự
  `countReviews`/`findMissingEvidence` hiện có) — nếu query lỗi/timeout,
  catch tại đây, log `WARN` (không phải `ERROR`), trả `List.of()` (rỗng)
  hoặc list với `dwellTime: null` cho từng phase, **không** throw tiếp lên
  `PmDashboardService`/`Controller`.
- **Căn cứ**: `error-handling.md`/`30-security.md` cấm `ResponseEntity`
  ad-hoc trong Controller, nhưng không cấm try/catch nội bộ ở Adapter cho
  1 subquery phụ — đây là pattern "suy giảm cục bộ" (partial degradation),
  không phải lỗi hệ thống cần map qua `GlobalExceptionHandler`. Cách này
  giữ nguyên `findDetail(...)` tổng thể không bị fail chỉ vì 1 subquery
  phụ lỗi.
- **Trạng thái**: RESOLVED 2026-08-20 — người dùng/Tech Lead đã xác nhận
  cách bọc try/catch nội bộ này được chấp nhận.

### Gate #4 — Trigger `FULL` scan backfill sau deploy

- **Quyết định**: KHÔNG trigger `FULL` scan tự động ngay sau deploy. Để dữ
  liệu backfill dần theo lần scan tự nhiên tiếp theo của từng ticket
  (đúng hành vi additive/read-only đã cam kết, tránh gây tải đột biến lên
  GitHub API cho toàn bộ ticket cùng lúc).
- **Căn cứ**: tính năng đã được thiết kế để chấp nhận `"-"` cho ticket cũ
  chưa scan lại (AC-3/AC-4, `spec-pack.md §12` đã ghi nhận rủi ro này là
  "đã chấp nhận, không phải lỗi"). Trigger FULL scan hàng loạt là hành
  động có rủi ro vận hành (rate limit, tải hệ thống) không tương xứng với
  lợi ích (chỉ cải thiện hiển thị, không sửa dữ liệu sai).
- **Trạng thái**: RESOLVED 2026-08-20 — quyết định PM/vận hành đã được
  người dùng xác nhận rõ ràng.

### Gate #5 — Chính sách log khi parse header lỗi

- **Quyết định**: log mức `WARN`, message dạng cố định (không nội suy dữ
  liệu nhạy cảm): `"ArtifactDocumentDateService: failed to parse
  create_date/update_date header, ticketId={}, sourcePath={}, missingField={}"`
  — chỉ log `ticketId`, `sourcePath`, tên field thiếu/sai; **không** log
  nội dung file `.md`, không log toàn bộ `headerMetadata()` map.
- **Trạng thái**: RESOLVED 2026-08-20. Căn cứ: đúng `30-security.md` §
  Log/Audit Sanitization (không log payload lớn/nhạy cảm không cần
  thiết) và nhất quán với Gate #3 (log `WARN` cho suy giảm cục bộ, không
  phải `ERROR`).

### Gate #7 — Chiến lược đọc blob cho service mới

- **Quyết định**: phương án (b) — service mới tự gọi lại
  `source.readBlob(repoFullName, entry.sha())` theo `source_path`/`sha`
  đã có sẵn trong `ArtifactSnapshot`/`snapshotsByFileName` (đã build ở
  dòng 304+ của `scanTicketDirectory`), **không** refactor vòng lặp
  parse hiện có (366-441) để chia sẻ content. Chấp nhận thêm tối đa +8
  GitHub API call/lần scan/ticket.
- **Căn cứ**: refactor vòng lặp hiện có để gom content vào 1 map dùng
  chung sẽ đụng vào code đã ổn định của 4 parser chuyên biệt (rủi ro
  regression cao hơn lợi ích), trong khi chi phí +8 API call/lần
  scan/ticket là nhỏ (scan chạy theo ticket, không phải theo request
  người dùng tần suất cao).
- **Trạng thái**: RESOLVED 2026-08-20 — Tech Lead/người dùng đã duyệt
  trade-off hiệu năng vs. mức độ đụng code hiện có.

### Gate #8 — Phạm vi `blackbox-testcases.md` (file thứ 8)

- **Quyết định**: service mới đọc header của **cả 8 file** trong
  `CHANGE_TARGET_FILES` (không loại `blackbox-testcases.md`), và **không
  tự quyết định loại phase nào dựa trên tên file** — để mapping
  `tbl_dim_artifact_type.phase_id` (nguồn DB, đã chốt ở H-PHASE-DWELL-TIME-2)
  tự động quyết định file đó có đóng góp vào phase nào hay không.
- **Căn cứ**: `ticket-rules.md` đã chốt "Nguồn phase-mapping chính thức:
  `tbl_dim_artifact_type.phase_id` (DB)" — nếu service mới tự loại trừ
  `blackbox-testcases.md` theo danh sách hardcode "7 file", sẽ tạo ra 1
  nguồn mapping thứ 2 (theo tên file) mâu thuẫn với nguyên tắc "chỉ 1
  nguồn phase-mapping" đã chốt. Đọc đủ cả 8 file rồi để DB mapping quyết
  định là cách nhất quán nhất với quyết định đã RESOLVED trước đó.
- **Trạng thái**: RESOLVED 2026-08-20. `ticket-rules.md` (dòng 26, 156)
  đã được cập nhật đồng bộ từ "cả 7 file mục tiêu" thành "cả 8 file mục
  tiêu" (xem file đó) để không còn mâu thuẫn với quyết định này.
