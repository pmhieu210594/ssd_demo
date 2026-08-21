# impact-analysis

**Ticket ID**: PHASE-DWELL-TIME
**Vai trò**: Principal Engineer — impact analysis trước khi implement.
**Create date**: 2026-08-19
**Update date**: 2026-08-20
**Nguồn đọc**: `spec-pack.md`, `open-issues.md`, `context.md`, `ticket-rules.md`,
`source-map.md`, `docs/architecture/*`, `docs/standards/*`, source đích đọc
lại toàn văn ở vòng verify này (`PmDashboardJdbcAdapter.java`,
`PmDashboardModels.java`, `PmDashboardDtos.java`,
`PmDashboardRepositoryPort.java`, `PmDashboardController.java`,
`PmDashboardService.java`, `ArtifactScannerService.java`,
`ArtifactScannerJdbcAdapter.java`, `MarkdownParserCore.java`,
`TicketDetailDrawer.tsx`, `lib/api.ts`, `lib/utils.ts`, migration
`V4, V391, V394, V397, V501, V502, V503, V505, V511`, `docs/standards/database.md`),
test hiện có (`TicketDetailDrawer.test.tsx`, `ArtifactScannerServiceTest.java`).

---

## Xác minh kỹ thuật 2026-08-20 (vòng 2) — đọc lại source thật, sửa các điểm sai/thiếu ở vòng 1

Vòng phân tích đầu (2026-08-19/20, thân tài liệu bên dưới) được viết dựa
nhiều vào `context.md`/suy đoán, một số điểm **chưa đọc trực tiếp source
mới nhất**. Sau khi đọc lại toàn văn, các điểm sau cần sửa:

| # | Điểm sai/thiếu ở vòng 1 | Thực tế xác minh | Tác động |
|---|---|---|---|
| 1 | Ghi package là `EDCAP_BE/.../application/usecase/...` không rõ root package | Root package thực tế là `com.sdd.platform` (ví dụ `com.sdd.platform.application.usecase.scanner.ArtifactScannerService`) | Phải dùng đúng package này khi đặt class/service mới, tránh AI tự đoán `com.edcap` |
| 2 | Giả định `CHANGE_TARGET_FILES` có 7 file mục tiêu | **Thực tế có 8 file** (`ArtifactScannerService.java:60-68`): spec-pack, impl-plan, review-checklist, self-review, test-plan, test-results, report, **và `blackbox-testcases.md`**) — nhưng vòng lặp parse (dòng 366-441) **không có branch đọc nội dung riêng cho `blackbox-testcases.md`** | Cần chốt lại: dwell-time có tính luôn `blackbox-testcases.md` không (7 phase hiển thị vẫn đúng vì đây là phase-mapping qua `tbl_dim_artifact_type`, không phải đếm số file — nhưng nếu bỏ sót đọc header của file này, phase tương ứng có thể thiếu dữ liệu). Thêm vào "Điểm chưa rõ" #8 |
| 3 | Giả định "tái dùng blob content đã đọc sẵn, không cần đọc lại/không thêm GitHub API call mới" | **Sai đã xác minh bằng code**: `scanTicketDirectory` **không có map blob dùng chung** — mỗi branch trong vòng lặp (dòng 366-441) tự gọi `source.readBlob(...)` riêng, độc lập, không lưu vào biến dùng lại được. Không có sẵn "content đã đọc" nào để service mới tái sử dụng nguyên trạng | Rủi ro hiệu năng/API call bị đánh giá thấp ở vòng 1 — cần chọn 1 trong 2 hướng: (a) refactor vòng lặp hiện có để gom `String content` mỗi file vào 1 `Map<String,String>` dùng chung rồi mới gọi service mới (an toàn về API call nhưng đụng vào code hiện có nhiều hơn dự kiến), hoặc (b) service mới tự gọi `source.readBlob(...)` lại theo `source_path` từ `ArtifactSnapshot` đã build — chấp nhận thêm tối đa 8 GitHub API call/lần scan/ticket. Chuyển từ "cần xác nhận" (vòng 1) sang quyết định bắt buộc ở Gate — xem "Điểm chưa rõ" #9 |
| 4 | Giả định theo `20-architecture.md`: "Each external upsert uses its own `TransactionTemplate`" nên adapter mới phải theo đúng pattern này | **Thực tế: `ArtifactScannerService`/`ArtifactScannerJdbcAdapter` hiện tại KHÔNG dùng `TransactionTemplate`/`@Transactional` ở đâu cả** (grep toàn file, 0 kết quả) — mọi method ghi (`insertSnapshot`, `updateSnapshot`, `updateSnapshotParsedSummary`, `upsertTicketPhaseStatus`) chỉ là 1 lệnh `jdbc.update`/`queryForObject` đơn lẻ, dựa vào autocommit mặc định | Đây là **sai lệch có sẵn từ trước** giữa code và `20-architecture.md`, không phải lỗi phát sinh từ ticket này — **không có trách nhiệm sửa** trong scope này. Method ghi bảng mới nên theo đúng pattern thực tế đang dùng (1 `jdbc.update` đơn lẻ), không tự bịa ra `TransactionTemplate` không có tiền lệ trong class này |
| 5 | `PmDashboardJdbcAdapter.findDetail`/`DashboardTicketDetail`/`PmDashboardTicketDetailDto` — số dòng | **Xác nhận khớp 100%** với vòng 1: `findDetail` dòng 214-270, khởi tạo record dòng 259-269, `DashboardTicketDetail` record `PmDashboardModels.java:266-277`, `PmDashboardTicketDetailDto` `PmDashboardDtos.java:334-360`. Không có bất kỳ query/field nào liên quan phase-dwell-time tồn tại sẵn — phải viết mới hoàn toàn | Không đổi kế hoạch, chỉ xác nhận độ tin cậy cao hơn |
| 6 | `PhaseCard` dòng 115-157, điểm chèn Card mới dòng 745 | **Xác nhận khớp 100%** | Không đổi |
| 7 | `TicketDetailDrawer.test.tsx` — đường dẫn file | Đường dẫn thực tế là `EDCAP_FE/src/__ tests __/pm-dashboard/TicketDetailDrawer.test.tsx` — **tên thư mục có khoảng trắng literal** (`__ tests __`, không phải `__tests__` chuẩn) | Ghi đúng path này khi tạo/sửa file, tránh lỗi "file not found" do gõ nhầm thành `__tests__` |
| 8 | `open-issues.md`/`source-availability.md` liệt kê `ja/vi locale.json` là "chưa đọc" | **Đã xác minh: cả `ja/locale.json` và `vi/locale.json` đều tồn tại và đã có sẵn section `Pages.PmDashboard`** (dòng 136/137 tương ứng) — không phải thiếu, chỉ là chưa được đọc kỹ ở vòng trước | Bỏ khỏi danh sách "phải đọc trước implementation" trong `open-issues.md`/`phase-status.md` — chỉ cần thêm 1 key mới vào cả 3 file, không có rủi ro cấu trúc |
| 9 | Chưa xác định migration version kế tiếp | Migration cao nhất hiện có là **`V511__ai_quality.sql`** → migration mới phải là **`V512__...sql`** | Dùng đúng số này khi đặt tên file migration, tránh trùng version |
| 10 | Chưa có tiền lệ rõ cho UNIQUE constraint trên `artifact_snapshot_id` | Tiền lệ gần nhất là `tbl_fact_ticket_issue` (V397) — FK tới `artifact_snapshot_id`, **KHÔNG UNIQUE** (1:nhiều, phân biệt bằng `issue_order`); không có bảng `tbl_fact_*` nào hiện có dùng UNIQUE 1:1 trực tiếp trên `artifact_snapshot_id`. Bảng gần nhất về hình dạng 1:nhiều khác là `tbl_fact_artifact_parsed_section` (cũng FK `artifact_snapshot_id`, không UNIQUE) | Không có tiền lệ schema ép buộc — quyết định UNIQUE hay không thực sự là quyết định thiết kế mới của ticket này, không suy ra được từ pattern có sẵn. Giữ nguyên là quyết định cần chốt ở Gate (không tự chọn) |
| 11 | Constructor `ArtifactScannerService` có 12 dependency, 2 overload "backward-compatible" (2-arg, 11-arg) dùng trong test | Xác nhận `ArtifactScannerServiceTest.java` chỉ dựng service qua 2 overload này (không gọi constructor 12-arg trực tiếp) → **thêm 1 dependency mới vào constructor 12-arg chính, giữ nguyên 2 overload cũ (fill `null`/default cho tham số mới) sẽ KHÔNG làm vỡ compile test hiện có** | Gỡ bỏ rủi ro "constructor đổi có thể vỡ test" nêu ở vòng 1 — miễn tuân thủ đúng cách thêm này |
| 12 | Chuẩn hoá key header (`normalizeMetadataKey`) | Xác nhận: chỉ replace khoảng trắng (`\s+`) thành `_`, **không xử lý dấu gạch ngang** — `**Create-Date**` sẽ KHÔNG normalize thành `create_date`. Mọi template ticket hiện có đều dùng `**Create date**`/`**Update date**` (khoảng trắng), nên an toàn với dữ liệu hiện có | Ghi chú rủi ro: nếu sau này ai đó gõ header dùng gạch ngang, parse sẽ thất bại âm thầm (trả `null`, không throw) — chấp nhận được vì đã có sẵn cơ chế "parse fail → hiển thị `-`" |

---

## ⚠️ Mâu thuẫn tài liệu cần xử lý trước khi code

`spec-pack.md` (v2026-08-20) **vẫn mô tả thiết kế cũ đã bị thay thế**:
§5 định nghĩa `Entry(ticket, phase) = MIN(created_at)` trên
`tbl_fact_artifact_snapshot`, §8 chỉ nói "thêm 1 SQL VIEW", §16
Assumption A-PHASE-DWELL-TIME-1 vẫn chấp nhận `created_at` làm proxy. Đây
là thiết kế đã bị `context.md`/`ticket-rules.md`/`source-map.md` (cập nhật
2026-08-20 sau) **chứng minh sai bằng code** (xem `context.md` mục "Rule
nghiệp vụ đặc thù" #1) và **thay thế hoàn toàn** bằng nguồn
`create_date`/`update_date` tự khai báo trong header file `.md`, lưu ở 1
bảng mới, tính qua 1 service mới dùng chung.

**Quyết định cho impl-plan này**: dùng đúng thiết kế mới nhất trong
`context.md`/`ticket-rules.md`/`source-map.md` (nguồn đáng tin cậy hơn vì
mới hơn và có bằng chứng code cụ thể). `spec-pack.md` §5/§8/§16 cần được
cập nhật lại sau khi impl-plan này được duyệt — ghi nhận là việc còn nợ,
không tự sửa `spec-pack.md` trong phạm vi file này (theo `ticket-rules.md`:
mọi mơ hồ phát sinh → `open-issues.md`, không tự quyết định ở tài liệu spec).

---

## Tổng quan thay đổi

Thêm field `phaseDwellTime` vào `GET
/api/v1/pm/dashboard/tickets/{ticketId}/detail` — hiển thị Dwell Time
(`hh:mm:ss` hoặc `"-"`) cho 7 phase (`1,3,4,5,6,7,8`) trong `PhaseCard` của
`TicketDetailDrawer.tsx`. Nguồn dữ liệu: `create_date`/`update_date` tự
khai báo trong header mỗi file `.md` ticket, parse qua 1 service mới dùng
chung (`MarkdownParserCore`), lưu vào 1 bảng DB mới (additive), cộng dồn
theo phase qua mapping `tbl_dim_artifact_type.phase_id`. Không sửa
`TicketPhaseEvaluatorService`/`tbl_fact_ticket_phase_status`, không tạo
endpoint mới.

**Phân loại thay đổi**: có **DB change** (1 bảng mới) và **contract
change** (field mới trên DTO dùng chung `PmDashboardTicketDetail`) — xem
"Điểm chưa rõ" #7 về việc có nên nâng review mode.

---

## Ảnh hưởng trực tiếp

| Vùng | Thay đổi | File |
|---|---|---|
| DB | Thêm 1 bảng mới (đề xuất `tbl_fact_artifact_document_date`) | Migration mới `V{n}__add_artifact_document_date.sql` |
| BE — service mới | 1 service dùng chung trích `create_date`/`update_date` qua `MarkdownParserCore` cho 7 file mục tiêu | Class mới (vị trí — xem gate impl-plan) |
| BE — port/adapter | Method mới ghi/đọc bảng mới; method mới tính Dwell Time theo phase | Port mới hoặc mở rộng `ArtifactScannerPersistencePort` + adapter tương ứng |
| BE — domain model | Thêm field `phaseDwellTime` | `PmDashboardModels.DashboardTicketDetail` (record, 1 nơi khởi tạo) |
| BE — adapter đọc | Thêm subquery/join lấy Dwell Time theo phase | `PmDashboardJdbcAdapter.findDetail(...)` |
| BE — DTO | Thêm field tương ứng | `PmDashboardDtos.PmDashboardTicketDetailDto` |
| BE — scan flow | Gọi service mới trong luồng scan hiện có (đọc thêm, không đổi luồng ghi cũ) | `ArtifactScannerService.scanTicketDirectory` (điểm gọi thêm, không sửa logic hiện có) |
| FE — type | Thêm field vào interface | `lib/api.ts` (`PmDashboardTicketDetail`) |
| FE — UI | Thêm 1 dòng hiển thị trong `PhaseCard` | `TicketDetailDrawer.tsx:115-157` |
| FE — helper | `formatDuration` mới (chưa có helper tương tự) | `lib/utils.ts` |
| i18n | 1 key tên field mới, 3 locale | `public/locales/{en,ja,vi}/locale.json` |

---

## Ảnh hưởng gián tiếp

| Vùng | Vì sao gián tiếp | Rủi ro |
|---|---|---|
| Mọi consumer khác của `PmDashboardTicketDetail`/`DashboardTicketDetail` | Thêm field vào DTO/record dùng chung — về nguyên tắc additive nhưng chưa rà hết nơi consume (Assumption A-PHASE-DWELL-TIME-3, spec-pack §16) | Thấp/Trung bình — cần xác nhận không có code nào so sánh DTO theo cách "đóng" (snapshot đầy đủ trong test khác) |
| `ArtifactScannerService.scanTicketDirectory` (luồng scan hiện có) | Service mới được gọi trong cùng luồng scan (đọc blob content đã có sẵn) | Trung bình — cần đặt code mới ở vị trí không đổi hành vi ghi/side-effect hiện có (xóa parsed sections, deactivate AC, v.v.) |
| `tbl_fact_artifact_snapshot` (đọc, không ghi) | Bảng mới có FK `artifact_snapshot_id` tham chiếu bảng này | Thấp — chưa phát hiện luồng DELETE nào trên bảng này trong source đã đọc |
| Hiệu năng scan (network/CPU) | Service mới cần nội dung blob 7 file — nếu không tái dùng blob đã đọc sẵn mà gọi lại `source.readBlob` riêng, tăng số GitHub API call mỗi lần scan | Trung bình — cần xác nhận dùng lại blob đã đọc trong `scanTicketDirectory`, không gọi thêm |
| ArchUnit / hexagonal layering | Service/port mới phải nằm đúng layer | Thấp nếu tuân thủ pattern hiện có; ArchUnit chặn build nếu sai |
| Ticket cũ (backfill) | File `.md` cũ có thể có `Update date` header chưa từng cập nhật lại (quy ước cũ chỉ có ngày) — Dwell Time backfill kém chính xác hơn | Thấp (giới hạn đã biết, đã chấp nhận — xem `context.md`/`open-issues.md`) |

---

## Ảnh hưởng FE

- `lib/api.ts`: interface `PmDashboardTicketDetail` thêm field
  `phaseDwellTime: { phaseCode: string; phaseOrder: number; dwellTime: string | null }[]`.
  Không cần sửa `endpoints.pmDashboard.detail(...)` (generic `api.get<T>`).
- `lib/utils.ts`: thêm hàm mới `formatDuration` (tên đề xuất) — input 2
  timestamp hoặc số giây, output `hh:mm:ss` không giới hạn 24h. Không sửa
  `formatDateTime` hiện có.
- `TicketDetailDrawer.tsx`: sửa hàm `PhaseCard` (dòng 115-157) — lặp qua
  `detail.phaseDwellTime`, hiển thị dưới `phaseCreatedAt` hiện có (dòng
  146-152). Không đổi field/props khác của component.
- `TicketDetailDrawer.test.tsx`: object `detail` giả lập (dòng 26-114)
  phải bổ sung `phaseDwellTime` để khớp `satisfies PmDashboardTicketDetail`
  — nếu field bắt buộc (không optional), test hiện có **sẽ vỡ compile**
  cho đến khi cập nhật. Phải sửa cùng lúc với thay đổi type.
- Không có route/màn hình mới; không đổi hành vi loading/error hiện có của
  `TicketDetailDrawer` (chỉ thêm dữ liệu hiển thị).

---

## Ảnh hưởng BE/API

- **Endpoint không đổi**: `GET /api/v1/pm/dashboard/tickets/{ticketId}/detail`
  — chỉ mở rộng response.
- **Controller không đổi**: `PmDashboardController.detail(...)` (dòng
  80-86) chỉ gọi `.from(service.detail(...))`.
- **Service không đổi chữ ký**: `PmDashboardService.detail(ticketId, caller)`
  giữ nguyên logic `requirePm` + `NotFoundException` — field mới tự động
  có mặt qua `DashboardTicketDetail` mở rộng.
- **Port mới hoặc mở rộng**: cần quyết định (xem gate impl-plan) — thêm
  method vào `PmDashboardRepositoryPort`/adapter cho subquery Dwell Time,
  và 1 port/method mới riêng để ghi bảng `create_date`/`update_date`
  (không dùng `ArtifactScannerPersistencePort.updateSnapshotParsedSummary`).
- **Service mới độc lập với luồng ghi hiện có**: gọi trong
  `ArtifactScannerService.scanTicketDirectory`, chỉ đọc thêm + ghi vào
  bảng mới — không sửa nhánh nào ghi `tbl_fact_artifact_snapshot`/
  `tbl_fact_ticket_phase_status` hiện có.

---

## Ảnh hưởng DTO/Schema/Validation

| DTO/Model | Thay đổi | Ghi chú |
|---|---|---|
| `PmDashboardModels.DashboardTicketDetail` (record, application) | + field `phaseDwellTime` | Record bất biến — sửa cả định nghĩa và 1 nơi khởi tạo (`PmDashboardJdbcAdapter.java:259-269`) |
| `PmDashboardDtos.PmDashboardTicketDetailDto` (record, web) | + field `phaseDwellTime` + cập nhật `from(...)` | Cùng pattern factory hiện có |
| FE `PmDashboardTicketDetail` (`lib/api.ts`) | + field `phaseDwellTime` | Dùng kiểu `dwellTime: string \| null` (không optional field) để buộc mọi nơi dựng object test xử lý rõ ràng |
| Validation | Không có input mới từ người dùng — `ticketId` path variable đã validate sẵn (tồn tại trong `tbl_dim_ticket`, ném `NotFoundException` nếu không) | Không cần thêm `@Valid`/DTO request mới |
| Bảng mới (`tbl_fact_artifact_document_date`, đề xuất) | `artifact_snapshot_id UUID` (FK), `document_create_at TIMESTAMPTZ` (nullable), `document_update_at TIMESTAMPTZ` (nullable), `created_at`/`updated_at`/`created_by`/`updated_by` theo `database.md` | UNIQUE trên `artifact_snapshot_id` hay cho phép nhiều dòng lịch sử — chưa chốt, xem "Điểm chưa rõ" #2 |

---

## Ảnh hưởng DB/Migration

- **Bảng mới, additive-only** — không ALTER/DROP `tbl_fact_artifact_snapshot`,
  `tbl_fact_ticket_phase_status`, hay bảng hiện có nào khác
  (AC-PHASE-DWELL-TIME-9).
- Theo `database.md`: bảng mới dùng prefix `tbl_fact_` (dữ liệu event/fact),
  PK `UUID DEFAULT gen_random_uuid()`, bắt buộc `created_at`/`updated_at
  TIMESTAMPTZ NOT NULL DEFAULT NOW()` + `created_by`/`updated_by`, migration
  đặt tên `V{n}__snake_case.sql`, FK tới
  `tbl_fact_artifact_snapshot(artifact_snapshot_id)`.
- **Không có down-migration** (Flyway Community — `repository-db-map.md §7`)
  — mọi migration là vĩnh viễn một khi đã chạy trên môi trường chia sẻ.
  Rollback thực chất = migration mới (forward-fix).
- Theo `00-safety.md §3`: migration mới phải hỏi người dùng trước khi chạy
  `flyway migrate` trên môi trường chia sẻ.
- **Rủi ro số lượng dữ liệu**: bảng mới tối đa 1 dòng/`artifact_snapshot_id`
  × 8 file/ticket (đã xác minh `CHANGE_TARGET_FILES` có 8 phần tử, không
  phải 7 — xem "Xác minh kỹ thuật" #2) — tăng thêm ~8N dòng cho N ticket.
  Không cùng cấp độ với `tbl_fact_artifact_snapshot`/`tbl_fact_ci_run`,
  rủi ro hiệu năng thấp ở quy mô hiện tại.
- **Index cần cân nhắc**: `PmDashboardJdbcAdapter.findDetail` sẽ JOIN bảng
  mới theo `artifact_snapshot_id` — cần ít nhất 1 index (hoặc dùng UNIQUE
  constraint làm index luôn nếu chọn 1-dòng/snapshot).
- **Migration version kế tiếp xác nhận**: migration cao nhất hiện có là
  `V511__ai_quality.sql` → migration mới phải đặt tên `V512__...sql`.
- **Tiền lệ schema đã kiểm tra**: `tbl_fact_ticket_issue` (V397) là bảng
  FK tới `artifact_snapshot_id` gần nhất — **không UNIQUE** (1:nhiều, phân
  biệt bằng `issue_order`); `tbl_fact_artifact_parsed_section` (V4) cùng
  hình dạng, cũng không UNIQUE. Không có tiền lệ 1:1 UNIQUE trực tiếp trên
  `artifact_snapshot_id` trong toàn bộ migration hiện có — quyết định
  UNIQUE hay không cho bảng mới là quyết định thiết kế mới, không suy ra
  được từ pattern có sẵn (giữ nguyên ở Gate, không tự chọn thay).

---

## Ảnh hưởng Batch/Event/External IF

- **Không có batch/job mới** — service mới chạy trong luồng scan hiện có
  (`ArtifactScannerService.scanTicketDirectory`), không phải job riêng.
- **External interface không đổi**: không thêm GitHub API call mới nếu
  tái dùng blob content đã đọc sẵn trong luồng scan — phải xác nhận khi
  implement (xem "Ảnh hưởng gián tiếp").
- Không có webhook/event mới, không đổi `ArtifactScannerSourcePort`.

---

## Ảnh hưởng Test

| Loại | File | Ảnh hưởng |
|---|---|---|
| BE unit — có sẵn | `TicketPhaseEvaluatorServiceTest.java` | Không cần sửa — service không bị đụng; chạy lại để xác nhận regression |
| BE unit — có sẵn | `ArtifactScannerServiceTest.java` (đã đọc toàn văn ở vòng verify này) | **Xác nhận an toàn**: test chỉ dựng `ArtifactScannerService` qua 2 overload "backward-compatible" (2-arg, 11-arg), không gọi constructor 12-arg chính trực tiếp. Thêm 1 dependency mới vào constructor 12-arg (giữ nguyên 2 overload cũ, fill `null`/default cho tham số mới) sẽ **không** làm vỡ compile file test này. Chỉ cần chạy lại để xác nhận hành vi runtime không đổi |
| BE unit — mới | Service trích header, adapter ghi/đọc bảng mới, subquery Dwell Time | Cần tạo — mock port interface theo `testing.md`, không mock domain record |
| FE unit — có sẵn | `EDCAP_FE/src/__ tests __/pm-dashboard/TicketDetailDrawer.test.tsx` (chú ý tên thư mục có khoảng trắng literal `__ tests __`, không phải `__tests__`) | Sẽ vỡ compile nếu `phaseDwellTime` là field bắt buộc và object `detail satisfies PmDashboardTicketDetail` giả lập (dòng 26-114) không được cập nhật — sửa cùng lúc |
| FE unit — mới | `formatDuration` helper, render `PhaseCard` với Dwell Time | Cần tạo |
| ArchUnit | `ArchitectureTest.java` | Chạy lại sau khi thêm class/package mới — xác nhận layer mới tuân thủ hexagonal rules |
| AC Closure | Theo `testing.md`: field mới phải verify **mounted thật** trong `TicketDetailDrawer`, không chỉ test cô lập `PhaseCard` | Bắt buộc cho AC-PHASE-DWELL-TIME-1/2/3/4 |

---

## Ảnh hưởng Operation/Monitoring

- Không có job vận hành mới; bảng mới chỉ được ghi trong luồng scan hiện có.
- Nếu service mới trích header lỗi (parse fail, header thiếu field), cần
  quyết định log warning hay ghi `NULL` im lặng — xem "Điểm chưa rõ" #6.
- Theo `error-handling.md`: không thêm `ResponseEntity`/`try-catch` ad-hoc
  trong controller. Nếu lỗi khi tính Dwell Time cần chỉ ảnh hưởng riêng
  field đó (không phải toàn API `/detail`), phải bọc try/catch **ở tầng
  Service/Adapter** (không phải Controller) — xem "Điểm chưa rõ" #4.
- Không cần alert/dashboard vận hành mới.

---

## Ảnh hưởng Rollout/Rollback

- **Rollout**: additive-only, không breaking change cho consumer hiện có
  của endpoint — có thể rollout thẳng, không cần feature flag (chưa thấy
  cơ chế feature flag nào trong codebase đã đọc).
- **Backfill**: dữ liệu bảng mới chỉ được tạo khi scan chạy lại cho ticket
  đó — ticket cũ hiển thị `"-"` cho tới lần scan tiếp theo, trừ khi trigger
  `FULL` scan sau deploy (xem "Điểm chưa rõ" #5).
- **Rollback DB**: không có down-migration — rollback thực tế là (a)
  migration mới `DROP TABLE` bảng vừa thêm (an toàn vì bảng chỉ phục vụ
  tính năng này, chưa có consumer khác), hoặc (b) giữ bảng, chỉ revert
  code BE/FE (ẩn field khỏi response/UI). Khuyến nghị (b) trước.
- **Rollback code**: additive-only + field mới có thể là `null`/mảng rỗng
  an toàn → revert code là git revert đơn giản, không cần thao tác DB nếu
  chọn (b).

---

## Vùng được phán định là không ảnh hưởng

| Vùng | Căn cứ |
|---|---|
| `TicketPhaseEvaluatorService`/`tbl_fact_ticket_phase_status` | Đọc toàn văn service (102 dòng) — không có method/logic liên quan lịch sử phase hay Dwell Time; ticket-rules cấm sửa; feature mới không gọi tới class này |
| `PmDashboardController.detail(...)` | Đọc toàn văn (`web/rest/PmDashboardController.java:80-86`) — chỉ gọi service + map DTO, không có logic riêng cần sửa |
| Permission/`requirePm` | Đọc `PmDashboardService.detail` (91-97) — permission check bọc toàn bộ response, field mới nằm trong response đã bảo vệ |
| `ArtifactScannerSourcePort` (đọc GitHub tree/blob) | Không cần thêm method — service mới dự kiến tái dùng blob đã đọc trong `scanTicketDirectory`/`buildSnapshot` |
| `DevDashboard` (domain "chị em") | Grep xác nhận `DevDashboardJdbcAdapter`/`DevDashboardModels` là code riêng biệt, không dùng chung `PmDashboardModels`/`PmDashboardTicketDetail` |
| `vw_artifact_inventory_current` | Thiết kế mới không dùng view này (dùng bảng mới riêng) |
| 4 parser chuyên biệt (spec-pack/review-checklist/self-review/report) | Service mới gọi `MarkdownParserCore` độc lập, không sửa/gọi lại 4 parser này |
| `ImplPlanParseService`/`TestPlanParseService`/`TestResultsParseService` | Service mới không phụ thuộc 3 service này (đọc thẳng `MarkdownParserCore`) |

---

## Điểm chưa rõ

| # | Vấn đề | Ảnh hưởng nếu không chốt | Đề xuất |
|---|---|---|---|
| 1 | `spec-pack.md` §5/§8/§16 vẫn mô tả thiết kế cũ (`created_at`) — mâu thuẫn với `context.md` | Người review sau có thể hiểu sai nguồn dữ liệu chính thức | Cập nhật `spec-pack.md` song song hoặc ngay sau khi impl-plan được duyệt, trước khi merge code |
| 2 | Tên/schema chính xác bảng mới và có UNIQUE trên `artifact_snapshot_id` hay không (1 dòng vs nhiều dòng lịch sử) | Ảnh hưởng thiết kế migration + cách adapter ghi (upsert vs insert-only) | Chốt ở gate trước implementation |
| 3 | Vị trí đặt service mới trích header (package nào, ai gọi nó) | Ảnh hưởng ArchUnit layer + khả năng unit test độc lập | Chốt ở gate trước implementation |
| 4 | `error-handling.md` cấm try/catch ad-hoc trong controller, nhưng spec-pack yêu cầu lỗi khi lấy Dwell Time → `"-"` cho riêng phase đó (không phải lỗi toàn API) | Không rõ mức nào (per-phase hay toàn request) trả `"-"` | Bọc riêng phần tính Dwell Time trong Service/Adapter bằng try/catch nội bộ (log + trả `null` cho field đó), không để exception thoát ra Controller |
| 5 | Có cần trigger 1 lần `FULL` scan lại sau deploy để backfill ngay Dwell Time cho ticket cũ? | Ảnh hưởng trải nghiệm ngay sau rollout | Hỏi người dùng/PM trước khi rollout |
| 6 | Header lỗi/thiếu `create_date`/`update_date` khi parse — log warning hay im lặng? | Ảnh hưởng khả năng debug khi Dwell Time sai | Khuyến nghị log warning (không phải error), không throw exception làm hỏng cả luồng scan |
| 7 | Có nên nâng review mode lên Heavy vì có DB change + contract change trên DTO dùng chung? | Ảnh hưởng số vòng review cần thiết | Đề xuất giữ Standard nhưng bắt buộc 1 vòng review riêng cho migration DB mới trước khi duyệt merge (Heavy-lite) — xác nhận với Tech Lead |
| 8 | `CHANGE_TARGET_FILES` thực tế có 8 phần tử (bao gồm `blackbox-testcases.md`), nhưng vòng lặp parse hiện tại không có branch đọc riêng cho file này — dwell-time có cần tính cho artifact-type ứng với `blackbox-testcases.md` không? | Nếu bỏ sót, phase ứng với artifact-type này (nếu có mapping `phase_id`) sẽ luôn hiển thị `"-"` dù file tồn tại | Xác nhận: service mới phải chủ động đọc cả 8 file (không dựa vào nhánh parse có sẵn), không giới hạn theo các branch đã có |
| 9 | Chiến lược đọc blob cho service mới: refactor vòng lặp hiện có để chia sẻ `content` đã đọc (an toàn API call nhưng đụng code hiện có nhiều hơn), hay để service mới tự `readBlob` lại theo `source_path` (đơn giản hơn nhưng cộng thêm tối đa 8 GitHub API call/lần scan/ticket)? | Ảnh hưởng hiệu năng scan (rate limit GitHub API) và mức độ đụng chạm vào `scanTicketDirectory` hiện có | Khuyến nghị hướng (b) — service mới tự đọc lại `source_path` đã có trong `ArtifactSnapshot`/`snapshotsByFileName` — đơn giản, không đụng nhánh parse hiện có, đổi lại chấp nhận tối đa +8 API call/scan (quy mô 1 ticket, tần suất scan không cao — rủi ro thấp); chốt ở Gate |
