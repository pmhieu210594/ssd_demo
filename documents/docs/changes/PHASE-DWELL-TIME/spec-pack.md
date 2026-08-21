# Spec Pack

**Ticket ID**: PHASE-DWELL-TIME
**Create date**: 2026-08-19
**Author**: TBD
**Update date**: 2026-08-20

---

## 1. Tổng quan

Bổ sung **Card mới "Phase Dwell Time"** vào UI chi tiết ticket (`TicketDetailDrawer.tsx`), đặt ngay dưới Card "Phase" hiện tại, hiển thị danh sách 7 Phase theo thứ tự Workflow, mỗi Phase kèm thời gian ticket đã "lưu lại" (Dwell Time). Card "Phase" hiện tại (hiển thị phase hiện tại của ticket) giữ nguyên không đổi. Tính năng thuần đọc (read-only), không thay đổi logic ghi/xác định phase hiện tại đang tồn tại (`TicketPhaseEvaluatorService`).

Nguồn: `01_raw-input.md` (SRS do người dùng cung cấp).

## 2. Bối cảnh / mục tiêu

- **Đối tượng**: Manager, Support Lead, Operation Admin, Support Agent.
- **User Story**: Là Manager/Support Lead, muốn xem tổng thời gian ticket đã lưu lại ở từng Phase ngay trong giao diện chi tiết ticket, để xác định nguyên nhân chậm trễ, theo dõi SLA từng bước, và đánh giá hiệu suất giải quyết công việc.
- **Vấn đề hiện tại (As-Is)**: hệ thống chỉ lưu **1 dòng "phase hiện tại"/ticket** (`tbl_fact_ticket_phase_status`, `UNIQUE(ticket_id)` từ `V396`); mỗi lần đổi phase, dòng cũ bị ghi đè hoàn toàn. Không có bảng nào lưu lịch sử entry/exit của các phase đã đi qua; cột `dwell_time_minutes` tồn tại nhưng luôn `NULL` (chưa từng được tính).
- **Giải pháp (To-Be, Phương án A)**: suy ra lịch sử phase **gián tiếp, đọc-only**, không sửa `TicketPhaseEvaluatorService`/`tbl_fact_ticket_phase_status`. Nguồn dữ liệu chính thức là `create_date`/`update_date` **tự khai báo trong header mỗi file `.md`** của ticket (7 file mục tiêu), parse qua `MarkdownParserCore.parse(...).headerMetadata()`, lưu vào 1 bảng mới riêng (`tbl_fact_artifact_document_date`, additive). Không dùng `tbl_fact_artifact_snapshot.created_at` — cột này không phải proxy đáng tin cậy (scanner tạo dòng snapshot cho cả file chưa tồn tại ngay từ lần scan đầu, `created_at` không đổi dù file xuất hiện muộn hơn trên Git). Lý do chọn Phương án A: (a) không đụng logic ghi hiện tại; (b) backfill được ngay cho ticket cũ có sẵn header `create_date`/`update_date`.

## 3. Phạm vi (trong phạm vi)

- Hiển thị **danh sách 7 Phase** theo `phase_order` của Workflow: `1, 3, 4, 5, 6, 7, 8` (loại `0-A, 0-B, 2, 9` — không có nguồn dữ liệu hoặc ngoài phạm vi, xem §5).
- Với mỗi Phase, chỉ hiển thị **1 field giá trị duy nhất**: tên phase (`phaseName`) + Dwell Time dạng `hh:mm:ss` khi tính được; hiển thị `"-"` khi không tính được hoặc lỗi/timeout khi lấy dữ liệu (điều kiện chi tiết xem §6 AC-3/AC-4). **Không hiển thị nhãn trạng thái** (không có khái niệm Pending/Completed/InProgress trên UI).
- Nguồn dữ liệu: **bảng mới** `tbl_fact_artifact_document_date` (lưu `document_create_at`/`document_update_at` parse từ header mỗi file `.md`) + 1 subquery/aggregate mới trong `PmDashboardJdbcAdapter.findDetail(...)` (không phải 1 SQL VIEW riêng), JOIN `tbl_fact_artifact_snapshot` + `tbl_dim_artifact_type` + `tbl_dim_phase` — thuần **additive**, không ALTER/DROP bảng hiện có.
- Vị trí UI: **Card mới** "Phase Dwell Time" (tên tạm `PhaseDwellTimeCard`) trong `TicketDetailDrawer.tsx`, đặt **ngay dưới Card "Phase" hiện tại** (không chèn vào bên trong `PhaseCard`). Danh sách 7 phase trong Card mới giữ nguyên thứ tự Workflow Sequence (`phaseOrder`).
- Backfill: dữ liệu bảng mới chỉ được tạo khi scanner chạy lại cho ticket đó; ticket cũ hiển thị `"-"` cho tới lần scan tiếp theo, trừ khi có quyết định trigger `FULL` scan sau deploy (chưa chốt — xem `impl-plan.md` § Gate trước implementation).
- Đa ngôn ngữ: tên tính năng theo 3 locale EN/JA/VI (xem §5); tên từng phase (`phaseName`) lấy nguyên văn từ `tbl_dim_phase.phase_name` (tiếng Anh) — không có bản dịch riêng theo locale trong phạm vi ticket này.

## 4. Ngoài phạm vi

- **Không** sửa `TicketPhaseEvaluatorService`, `upsertTicketPhaseStatus`, hoặc bất kỳ logic xác định "current phase" hiện tại của `PhaseCard` hôm nay — 2 khái niệm phase (mới vs. hiện tại) tồn tại song song, có thể lệch nhau.
- **Không** mở rộng `ArtifactScannerService.CHANGE_TARGET_FILES` để quét thêm `context.md` cho Phase `2`.
- **Phase `0-A`, `0-B` và `9`**: không nằm trong danh sách hiển thị — `0-B`/`9` không có nguồn dữ liệu (`tbl_dim_artifact_type`); `0-A` bị loại theo quyết định người dùng (không cần suy Entry theo `repository_id`, không cần đọc `tbl_dim_ticket`/`TicketScope`). Danh sách hiển thị cuối cùng: `1, 3, 4, 5, 6, 7, 8` (7 phase).
- **Không** hiển thị Dwell Time cho phase "đang xử lý" (Active Phase).
- **Không** ALTER/DROP schema hiện có; không đổi hành vi ghi (`insertSnapshot`, `upsertTicketPhaseStatus`).
- **`Timestamp_Now` không được sử dụng** trong phạm vi ticket này — không có timezone conversion nào cần xử lý.

## 5. Thuật ngữ nghiệp vụ / tiền đề

| Thuật ngữ | Ý nghĩa | Ghi chú |
|---|---|---|
| Phase | Một bước trong Workflow xử lý ticket, định nghĩa tại `tbl_dim_phase` (11 phase, `phase_order` 0–10) | Nguồn danh mục |
| Phase Dwell Time | Với 1 phase, = **tổng (Σ)** của (`document_update_at` − `document_create_at`) cho **tất cả file** thuộc phase đó (map qua `tbl_dim_artifact_type.phase_id`) | Nguồn: `context.md` Pattern #5, `impl-plan.md` "Phương châm data/DB/query" |
| `create_date`/`update_date` (per file) | Giá trị tự khai báo trong header mỗi file `.md` (`**create_date**: ...`, `**update_date**: ...`), parse qua `MarkdownParserCore.parse(...).headerMetadata()`, lưu vào bảng mới `tbl_fact_artifact_document_date` (cột `document_create_at`/`document_update_at`) | Đây là nguồn Entry/Exit duy nhất cho tính năng này |
| `phaseName` (trong `phaseDwellTime[]`) | `tbl_dim_phase.phase_name` (cột tiếng Anh, `V4__init_shema_v2.sql:1377`) | |
| Danh sách 7 Phase hiển thị | `1, 3, 4, 5, 6, 7, 8` | `0-A, 0-B, 2, 9` loại khỏi phạm vi |
| Nguồn phase-mapping | `tbl_dim_artifact_type.phase_id` (DB) — **không** dùng mapping hardcode của `TicketPhaseEvaluatorService.resolvePhaseCode` (Java) | 2 nguồn phase-mapping trong hệ thống không khớp nhau (tồn tại từ trước ticket này); tính năng mới dùng nguồn DB |

## 6. Acceptance Criteria

| ACID | Description | Testable? | Notes |
|---|---|---|---|
| AC-PHASE-DWELL-TIME-1 | Trong `TicketDetailDrawer`, Card "Phase Dwell Time" hiển thị đúng 7 Phase theo thứ tự `phase_order`: `1, 3, 4, 5, 6, 7, 8`. Không hiển thị `0-A, 0-B, 2, 9`. | Yes | So khớp danh sách + thứ tự với `tbl_dim_phase.phase_order` |
| AC-PHASE-DWELL-TIME-2 | Với 1 ticket có ≥1 file thuộc phase N mà header có đủ cả `create_date` và `update_date` parse được, Phase N hiển thị Dwell Time = tổng (Σ) `(update_date − create_date)` của các file đó, format `hh:mm:ss` (hh không giới hạn 24, ví dụ `27:20:05`) | Yes | So khớp giá trị tính bằng tay từ `document_create_at`/`document_update_at` của các file thuộc phase N trong `tbl_fact_artifact_document_date` |
| AC-PHASE-DWELL-TIME-3 | Với 1 ticket mà phase N không có file nào (theo mapping `tbl_dim_artifact_type.phase_id`) từng được ghi nhận `create_date`, Phase N hiển thị `"-"`, không hiển thị Dwell Time | Yes | Ticket test chưa có file nào thuộc phase N |
| AC-PHASE-DWELL-TIME-4 | Với 1 phase N, file có `create_date` nhưng **chưa có** `update_date` **không đóng góp vào tổng** Dwell Time của phase đó. Nếu sau khi loại các file thiếu cặp, phase N **không còn file nào đủ cả `create_date` và `update_date`**, Phase N hiển thị `"-"` | Yes | Test: 1 file duy nhất của phase N có `create_date` nhưng không có `update_date` → phase N hiển thị `"-"`; 1 phase có 2 file, 1 file đủ cặp + 1 file thiếu `update_date` → Dwell Time chỉ tính từ file đủ cặp |
| AC-PHASE-DWELL-TIME-6 | Rescan lại cùng nội dung file (`content_hash` không đổi) không làm thay đổi `document_create_at`/`document_update_at` đã tính trước đó cho bảng `tbl_fact_artifact_document_date`. Hành vi `ON CONFLICT` chính xác (upsert vs insert-only, có UNIQUE trên `artifact_snapshot_id` hay không) **chưa chốt** — xem `impl-plan.md` § Gate trước implementation | TBD | Test: chạy scanner 2 lần liên tiếp trên cùng nội dung, Dwell Time không đổi — chỉ viết được sau khi Gate chốt |
| AC-PHASE-DWELL-TIME-7 | Tính năng không làm thay đổi giá trị "current phase" hiện tại hiển thị ở Card "Phase"/`TicketPhaseEvaluatorService` | Yes | Regression test: field `phaseCode` hiện tại không đổi trước/sau khi thêm tính năng |
| AC-PHASE-DWELL-TIME-8 | Tên tính năng hiển thị đúng theo locale: EN "Phase Dwell Time", JA "各フェーズの滞留時間", VI "Thời gian kẹt ở từng phase" | Yes | So khớp UI theo từng locale |
| AC-PHASE-DWELL-TIME-9 | Không có ALTER/DROP nào lên bảng hiện có (`tbl_fact_ticket_phase_status`, `tbl_fact_artifact_snapshot`, ...); implementation chỉ thêm mới (bảng/migration mới) | Yes | Review migration diff |
| AC-PHASE-DWELL-TIME-10 | Khi API lấy dữ liệu phase/dwell time bị lỗi hoặc timeout, Phase hiển thị `"-"` (không throw lỗi UI, không retry tự động) | Yes | |

*(Không có `AC-PHASE-DWELL-TIME-5`.)*

## 7. Input / Output

### 7.1. Input

| item | type | required | validation | notes |
|---|---|---|---|---|
| `ticketId` | UUID/PK ticket (kiểu hiện có trong `PmDashboardTicketDetail`) | Yes | Phải tồn tại trong `tbl_dim_ticket` | Dùng để lọc theo `ticket_id` cho toàn bộ 7 phase |

### 7.2. Output

| item | type | format | notes |
|---|---|---|---|
| `phaseDwellTime` (DTO mới, danh sách 7 phần tử: `1,3,4,5,6,7,8`) | array | `{ phaseCode, phaseOrder, phaseName, dwellTime: string \| null }` | Mỗi phần tử có 1 field giá trị Dwell Time duy nhất — không có `status`/nhãn trạng thái |
| `phaseName` | string | Tên phase (vd "Spec Pack") | Nguồn: `tbl_dim_phase.phase_name` |
| `dwellTime` | string \| null | `hh:mm:ss` (hh không giới hạn 24, ví dụ `03:20:00`, `27:20:05`) khi tính được; `null`/thiếu khi chưa có Dwell Time hoặc lỗi API (FE render `"-"`) | Theo `01_raw-input.md` §3.5 |

## 8. Ảnh hưởng màn hình / API / DB / Batch / Event

- **Màn hình**: `TicketDetailDrawer.tsx` — `PhaseCard` (dòng 115-157) **giữ nguyên, không đổi**; thêm **Card mới** (tên tạm `PhaseDwellTimeCard`) ngay dưới Card "Phase" hiện tại (cạnh lời gọi `<PhaseCard detail={detail} t={t} />`, dòng 745), hiển thị danh sách 7 phần tử từ `detail.phaseDwellTime`.
- **API**: mở rộng `PmDashboardTicketDetail` hiện có (`api.ts:585-641`, hiện chỉ có 1 phase hiện tại `row.phaseCode/phaseName/phaseDescription/phaseCreatedAt/phaseOrder`, `api.ts:558-562`) bằng field mới `phaseDwellTime: PhaseDwellTimeItem[]` (`{ phaseCode, phaseOrder, phaseName, dwellTime }` — xem §7.2), không tạo endpoint riêng. Controller cụ thể ghép DTO này cần đọc thêm khi vào `impl-plan.md`.
- **DB**: thêm **1 bảng mới** (đề xuất tên `tbl_fact_artifact_document_date` — lưu `document_create_at`/`document_update_at` parse từ header mỗi file `.md`, FK tới `tbl_fact_artifact_snapshot(artifact_snapshot_id)`), **không phải 1 SQL VIEW riêng**. Đọc dữ liệu qua 1 subquery/aggregate mới trong `PmDashboardJdbcAdapter.findDetail(...)`, JOIN bảng mới + `tbl_fact_artifact_snapshot` + `tbl_dim_artifact_type` + `tbl_dim_phase`; **chỉ join theo `ticket_id`**; không ALTER/DROP bảng hiện có. Cần Flyway migration mới (theo `00-safety.md §3`, phải hỏi người dùng trước khi `flyway migrate`).
- **Batch/Event**: không có batch/job mới; service mới trích `create_date`/`update_date` chạy **trong luồng scan hiện có** (`ArtifactScannerService.scanTicketDirectory`), tái dùng blob content đã đọc sẵn — không thêm GitHub API call mới (cần xác nhận khi implement).

## 9. FE/BE contract

- **BE → FE**: mở rộng `PmDashboardTicketDetail` với field mới `phaseDwellTime: { phaseCode, phaseOrder, phaseName, dwellTime: string | null }[]` (xem §7.2) — 1 field giá trị Dwell Time duy nhất mỗi phần tử, không có `status`/nhãn trạng thái. Không đổi field `phaseCode/phaseName/phaseDescription/phaseCreatedAt/phaseOrder` hiện có trên `row` (giữ nguyên "current phase" như hôm nay — `phaseName` trong `phaseDwellTime[]` lấy từ `tbl_dim_phase.phase_name`, là field mới riêng, không phải field `row.phaseName` cũ). Khi lỗi/timeout lấy dữ liệu, BE trả `dwellTime: null` cho phase liên quan, FE render `"-"`.
- **FE — vị trí render**: Card mới "Phase Dwell Time" đặt ngay dưới Card "Phase" hiện tại trong `TicketDetailDrawer.tsx` (không chèn vào bên trong `PhaseCard`).
- **FE**: thêm helper format duration mới nếu cần (chưa có helper tương tự trong `utils.ts` — hiện chỉ có `formatDateTime`), đặt cùng `EDCAP_FE/src/lib/utils.ts` theo pattern hiện có. FE render `"-"` khi `dwellTime` là `null`/thiếu — không cần i18n key riêng cho trạng thái.
- Không cần thêm i18n key mới cho nhãn trạng thái vì scope chỉ hiển thị giá trị Dwell Time hoặc `"-"` — tên tính năng (đã dịch 3 locale, xem §5) là label i18n duy nhất cần thiết cho phần này.
- Controller/BE cụ thể ghép `PmDashboardTicketDetail` cần xác định ở `impl-plan.md` trước khi viết code; endpoint hiện có được mở rộng, không tạo endpoint mới.

## 10. Validation / Error / Message

| case | expected behavior | message/code | notes |
|---|---|---|---|
| Ticket chưa có file nào thuộc phase N (không có bản ghi `create_date` cho phase đó) | Hiển thị `"-"` | Không cần i18n key riêng | Không phải lỗi, là trạng thái hợp lệ |
| Phase N có file với `create_date` nhưng chưa có `update_date`, và không còn file nào khác đủ cặp trong phase đó | Hiển thị `"-"` (file thiếu `update_date` không đóng góp vào tổng) | Không cần message riêng | Không tính theo `Timestamp_Now` |
| API lấy dữ liệu phase/dwell time bị lỗi hoặc timeout | Hiển thị `"-"` cho phase liên quan (BE trả `dwellTime: null`, FE không throw lỗi UI, không retry tự động) | Không cần error code riêng ở FE; lỗi BE (nếu có) log qua `GlobalExceptionHandler` chuẩn `30-security.md` | |

## 11. Security / Privacy / Permission / Audit

- Tính năng thuần đọc, không có input mới từ người dùng (không có nguy cơ injection mới từ input) — chỉ đọc thêm dữ liệu đã có sẵn trong DB.
- Không có PII mới bị lộ: dữ liệu Dwell Time chỉ là khoảng thời gian tính từ timestamp nội bộ, không chứa nội dung nhạy cảm.
- Permission: dùng theo permission hiện có của `TicketDetailDrawer`/`PmDashboardTicketDetail` (session-based, theo `30-security.md`); không có role mới nào cần định nghĩa riêng cho Dwell Time (đối tượng sử dụng là các role đã có quyền xem ticket detail: Manager, Support Lead, Operation Admin, Support Agent).
- Audit: raw-input không đề cập yêu cầu audit/log riêng cho việc tính hoặc xem Dwell Time — không suy đoán thêm (theo `ticket-rules.md`).
- Không log secret/token nào liên quan (tuân `30-security.md` § Log/Audit Sanitization — không áp dụng trực tiếp vì tính năng không xử lý secret).

## 12. Operation / Logging / Monitoring / Recovery

- Không có batch/job mới cần vận hành; service trích header chạy trong luồng scan hiện có, bảng mới là read-only khi phục vụ `/detail`.
- Logging: nếu cần log lỗi khi tính Dwell Time thất bại hoặc parse header lỗi, dùng `GlobalExceptionHandler`/log `WARN` nội bộ theo `30-security.md` (không thêm `ResponseEntity` ad-hoc trong controller); chính sách log cụ thể khi parse header lỗi vẫn **chưa chốt** — xem `impl-plan.md` § Gate trước implementation.
- Recovery: bảng mới (`tbl_fact_artifact_document_date`) chỉ được ghi lại khi scanner chạy lại — không có state nào khác cần backup/restore riêng.
- Data quality cần theo dõi: `create_date`/`update_date` là giá trị **tự khai báo trong header** file `.md` — phụ thuộc việc người/AI cập nhật header đúng quy ước khi sửa file; file ticket cũ có thể chưa từng cập nhật `update_date` theo quy ước mới, khiến phase đó hiển thị `"-"` dù thực tế đã hoàn thành (đã chấp nhận theo AC-4).

## 13. Test Strategy Summary

- **Unit (BE)**: test service trích header (`create_date`/`update_date` từ `MarkdownParserCore.headerMetadata()`) và test subquery/aggregate Dwell Time mới (chỉ join theo `ticket_id`, 7 phase `1,3,4,5,6,7,8`) với dữ liệu giả lập `tbl_fact_artifact_document_date` — các case: 1 phase có 1 file đủ cặp `create_date`/`update_date` (có Dwell Time), 1 phase có ≥2 file (cộng dồn), phase chưa có file nào (`"-"`), rescan trùng `content_hash` (giá trị không đổi — phụ thuộc Gate schema, xem AC-6 ở §6).
- **Unit (FE)**: test helper format duration nếu có (biên: `00:00:00`, `27:20:05`, không giới hạn 24h) và test render Card mới cho 2 case giá trị: có Dwell Time (`hh:mm:ss`) và không có (`"-"`) — không còn nhãn trạng thái riêng để test.
- **Regression**: đảm bảo `TicketDetailDrawer.test.tsx` hiện có không vỡ khi mở rộng DTO (chưa đọc nội dung file test này — phải đọc trước khi implement); đảm bảo `TicketPhaseEvaluatorServiceTest.java` không cần đổi (service này không bị sửa).
- **Integration/Backfill**: chạy trên ticket cũ đã có sẵn snapshot lịch sử, xác nhận Dwell Time tính đúng không cần dữ liệu mới.
- Không mock domain/value object (theo `40-testing.md`); mock port interface nếu cần trong unit test BE.
- Chi tiết test case cụ thể sẽ ở `test-plan.md`/`blackbox-testcases.md` (chưa soạn).

## 14. Source Availability Summary

*(Chi tiết đầy đủ tại `source-availability.md`.)*

- **Đã đọc, độ tin cậy cao**: raw input (`01_raw-input.md`); FE `TicketDetailDrawer.tsx`, `api.ts`, `utils.ts`, `TemplateUsageByPhase.tsx`; BE `TicketPhaseEvaluatorService.java`, `ArtifactScannerJdbcAdapter.java` (đoạn insert/upsert), `ArtifactScannerService.java` (`CHANGE_TARGET_FILES`, `PHASE0_TARGET_FILES`, `scanPhase0`); DB migration `V4, V160, V330, V391, V395, V396`.
- **Đọc một phần**: FE `en/locale.json` (chỉ đoạn liên quan); DB migration `V510` (chỉ đoạn seed artifact_type liên quan).
- **Chưa đọc / không đọc được** (phải đọc trước implementation): `ja/vi locale.json` (nội dung); migration `V394, V501, V502, V503, V505`; DB Postgres thực tế (không có quyền truy cập — rủi ro schema drift); FE test `TicketDetailDrawer.test.tsx`; BE test `TicketPhaseEvaluatorServiceTest.java`.
- **Rủi ro lớn nhất còn tồn đọng**: (1) chưa xác minh schema thực tế khớp migration tĩnh; (2) chưa đọc test hiện có nên chưa đo được regression risk cụ thể.

## 15. Complexity Classification

```text
- Complexity: Standard
- System shape: FE+BE+DB
- Primary risk: Source (dữ liệu lịch sử suy diễn, không chính xác tuyệt đối)
- Review mode: Standard
- Required options: Source Analysis, DB Migration, FE-BE Contract
```

**Recommended Mode: Standard**

Lý do: phạm vi nghiệp vụ đã chốt rõ (7 phase, công thức Dwell Time, format thời gian), giải pháp kỹ thuật (Phương án A) đã được người dùng phê duyệt, không có thay đổi phá vỡ (breaking change) lên logic ghi hiện tại. Chỉ còn cần: (a) 1 migration DB mới (bảng mới, chỉ join theo `ticket_id`), (b) mở rộng FE/BE contract (`phaseDwellTime`). Không đủ đơn giản để dùng Light (có DB migration + contract change), nhưng cũng không đến mức Heavy/Critical (không đổi logic ghi, không ảnh hưởng nhiều service, additive-only).

## 16. Assumptions

| ID | assumption | basis | risk | need confirmation? |
|---|---|---|---|---|
| A-PHASE-DWELL-TIME-3 | Việc thêm field mới vào `PmDashboardTicketDetail` sẽ không phá vỡ consumer khác của DTO này (ngoài `PhaseCard`) | Suy đoán dựa trên nguyên tắc mở rộng thuần additive (thêm field mới, không đổi field cũ) — **chưa rà toàn bộ nơi consume DTO này** | Thấp/Trung bình | Nên rà soát ở `impl-plan.md`/khi implement |
| A-PHASE-DWELL-TIME-4 | `create_date`/`update_date` tự khai báo trong header file `.md` là proxy đủ tốt cho "thời điểm bắt đầu/kết thúc phase" | `context.md`/`impl-plan.md` — dữ liệu do người/AI tự ghi khi soạn/cập nhật file, không phải mốc hệ thống ghi tự động | Trung bình — phụ thuộc kỷ luật cập nhật header đúng quy ước; file cũ có thể thiếu `update_date` (xem §12) | Không cần xác nhận thêm |

## 17. Open Issues

Không còn Open Issue nào ở cấp spec/business — toàn bộ đã RESOLVED. Lịch sử đầy đủ (bao gồm `OI-PHASE-DWELL-TIME-10` đến `13`): xem `open-issues.md` § Resolution Log.

Toàn bộ 7 Gate kỹ thuật trước khi viết code (schema bảng mới, vị trí service, xử lý lỗi, backfill, log policy, chiến lược đọc blob, phạm vi `blackbox-testcases.md`) đã RESOLVED 2026-08-20 — xem `impl-plan.md` § "Quyết định Gate (RESOLVED 2026-08-20)" và Human Decisions H-PHASE-DWELL-TIME-12 → 18 dưới đây.

## 18. Human Decisions Required

| ID | decision item | reasons | owner | status |
|---|---|---|---|---|
| H-PHASE-DWELL-TIME-1 | Chọn Phương án A (suy lịch sử phase, đọc-only) thay vì Phương án B (bảng log lịch sử mới) | Phương án A không đụng logic ghi hiện tại và backfill được ngay cho ticket cũ | Người dùng | RESOLVED |
| H-PHASE-DWELL-TIME-2 | Dùng `tbl_dim_artifact_type.phase_id` (DB) làm nguồn phase-mapping cho tính năng mới, chấp nhận có thể lệch với mapping hardcode hiện tại của `TicketPhaseEvaluatorService` | 2 nguồn phase-mapping trong hệ thống không khớp nhau từ trước | Người dùng | RESOLVED |
| H-PHASE-DWELL-TIME-3 | Không mở rộng `ArtifactScannerService.CHANGE_TARGET_FILES` để quét `context.md` cho phase `2` | Giữ additive tối thiểu, tránh side-effect lên scanner hiện có | Người dùng | RESOLVED |
| H-PHASE-DWELL-TIME-5 | Bỏ yêu cầu hiển thị Dwell Time cho "Active Phase" (phase đang xử lý, chưa có Exit) | Không có Exit thật từ scan .md nếu phase chưa hoàn thành | Người dùng | RESOLVED |
| H-PHASE-DWELL-TIME-6 | Dùng `Timestamp_Now` trực tiếp, không xử lý timezone riêng | Hiệu số giữa 2 timestamp tuyệt đối không phụ thuộc timezone hiển thị | Người dùng | RESOLVED (sau đó `Timestamp_Now` được xác nhận không dùng — xem §4) |
| H-PHASE-DWELL-TIME-7 | Loại phase `0-A` khỏi danh sách hiển thị (không suy Entry theo `repository_id`) | Không cần cơ chế map ticket→repository, danh sách hiển thị còn 7 phase | Người dùng | RESOLVED |
| H-PHASE-DWELL-TIME-8 | Khi API lỗi/timeout lúc lấy dữ liệu phase, hiển thị `"-"` | Đơn giản, nhất quán với triết lý fail-silent của tính năng read-only | Người dùng | RESOLVED |
| H-PHASE-DWELL-TIME-9 | Không hiển thị nhãn trạng thái (Pending/InProgress) trên UI — chỉ 1 field giá trị Dwell Time hoặc `"-"` | Raw-input chỉ có nhãn tiếng Việt cho Pending, chưa đủ 3 locale — người dùng chọn bỏ nhãn thay vì bổ sung bản dịch | Người dùng | RESOLVED |
| H-PHASE-DWELL-TIME-10 | Mở rộng `PmDashboardTicketDetail` hiện có với field `phaseDwellTime`, không tạo endpoint mới | Giữ additive-only, đúng tinh thần Phương án A | Người dùng | RESOLVED |
| H-PHASE-DWELL-TIME-11 | Layout: giữ nguyên Card "Phase" hiện tại, thêm Card mới "Phase Dwell Time" dạng danh sách ngay dưới (Phương án A trong `wireframe.md`) | Không phá vỡ layout/props hiện có; giữ rõ ràng 2 khái niệm "current phase" vs "lịch sử dwell time" | Người dùng | RESOLVED |
| H-PHASE-DWELL-TIME-12 | (Gate #1) Bảng mới `tbl_fact_artifact_document_date` có `UNIQUE (artifact_snapshot_id)`, ghi bằng `INSERT ... ON CONFLICT (artifact_snapshot_id) DO NOTHING` | `insertSnapshot` giữ nguyên `artifact_snapshot_id` khi `content_hash` không đổi — UNIQUE + `DO NOTHING` tự thỏa AC-6 (rescan) không cần logic so sánh thủ công | Người dùng | RESOLVED |
| H-PHASE-DWELL-TIME-13 | (Gate #2) Service `ArtifactDocumentDateService` đặt trong package `com.sdd.platform.application.usecase.scanner`, `ArtifactScannerService` gọi qua constructor injection | Đúng pattern "Service mỏng" hexagonal đã có, không tạo tầng gián tiếp thừa | Người dùng | RESOLVED |
| H-PHASE-DWELL-TIME-14 | (Gate #3) Lỗi tính Dwell Time cho 1 phase: try/catch trong `PmDashboardJdbcAdapter`, log `WARN`, trả rỗng/`null`, không throw lên Controller | Không vi phạm `error-handling.md` (cấm `ResponseEntity` ad-hoc ở Controller); giữ `findDetail` không fail toàn phần vì 1 subquery phụ | Người dùng | RESOLVED |
| H-PHASE-DWELL-TIME-15 | (Gate #4) Không trigger `FULL` scan backfill tự động sau deploy | Tránh tải đột biến GitHub API cho toàn bộ ticket cùng lúc; `"-"` cho ticket cũ đã được chấp nhận trước (AC-3/4) | Người dùng | RESOLVED |
| H-PHASE-DWELL-TIME-16 | (Gate #5) Log lỗi parse header ở mức `WARN`, chỉ kèm `ticketId`/`sourcePath`/tên field thiếu, không log nội dung file | Đúng `30-security.md` § Log/Audit Sanitization | Người dùng | RESOLVED |
| H-PHASE-DWELL-TIME-17 | (Gate #7) Service trích header tự `readBlob` lại theo `source_path` (không refactor vòng lặp scan hiện có), chấp nhận thêm tối đa +8 GitHub API call/lần scan/ticket | Tránh regression lên 4 parser chuyên biệt đã ổn định; chi phí API call nhỏ vì scan chạy theo ticket, không theo request tần suất cao | Người dùng | RESOLVED |
| H-PHASE-DWELL-TIME-18 | (Gate #8) Service đọc header cho cả 8 file trong `CHANGE_TARGET_FILES` (gồm `blackbox-testcases.md`), để mapping `tbl_dim_artifact_type.phase_id` (DB) tự quyết định đóng góp phase, không tự loại trừ theo tên file | Nhất quán với nguyên tắc "chỉ 1 nguồn phase-mapping = DB" đã chốt ở H-PHASE-DWELL-TIME-2 | Người dùng | RESOLVED |
