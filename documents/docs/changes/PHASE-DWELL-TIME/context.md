# context.md

**Ticket ID**: PHASE-DWELL-TIME
**Vai trò**: Tech Lead — chuẩn bị context/rule riêng để AI implement không hiểu
sai codebase hiện có.
**Create date**: 2026-08-19
**Update date**: 2026-08-20 (bản dọn dẹp cuối — chỉ giữ quyết định đã chốt,
xóa toàn bộ lịch sử tranh luận/phương án bị thay thế)

---

## File đã đọc

**Database / migration:**
- `EDCAP_BE/src/main/resources/db/migration/V4__init_shema_v2.sql` —
  `tbl_fact_artifact_snapshot` (dòng 383-412, toàn bộ cột + constraint),
  `tbl_dim_phase` (195-205), `tbl_dim_artifact_type` (207-219).
- `EDCAP_BE/src/main/resources/db/migration/V160__artifact_scanner.sql`,
  `V161__artifact_scanner_ticket_status.sql`,
  `V503__artifact_snapshot_schema_version_numeric.sql` — 3 bản định nghĩa
  khác nhau của `vw_artifact_inventory_current` (view bị `CREATE OR
  REPLACE`/`CREATE VIEW` lại nhiều lần).

**Backend (đọc toàn văn hoặc đoạn quan trọng):**
- `application/usecase/phase/TicketPhaseEvaluatorService.java` (toàn bộ) —
  service xác định "current phase" hiện tại, KHÔNG phải nguồn cho Dwell Time.
- `application/usecase/scanner/ArtifactScannerService.java` —
  `scanTicketDirectory` (280-370), `buildSnapshot` (1495-1573),
  `CHANGE_TARGET_FILES`/`PHASE0_TARGET_FILES` (60-77).
- `infrastructure/persistence/adapter/scanner/ArtifactScannerJdbcAdapter.java`
  — `findLatestSnapshot`, `insertSnapshot`, `updateSnapshot`,
  `updateSnapshotParsedSummary`, `upsertTicketPhaseStatus`.
- `infrastructure/persistence/adapter/PmDashboardJdbcAdapter.java` —
  `findDetail` (215-270, toàn bộ query + construction record).
- `application/usecase/pmdashboard/PmDashboardService.java` (method
  `detail(...)`, `refresh(...)`).
- `application/usecase/pmdashboard/PmDashboardModels.java` (record
  `DashboardTicketDetail`, `ScoreBreakdown`).
- `web/rest/PmDashboardController.java` (toàn bộ).
- `web/dto/PmDashboardDtos.java` (toàn bộ).
- `application/port/out/persistence/PmDashboardRepositoryPort.java` (xác
  nhận method `findDetail` qua grep).
- `domain/service/markdown/core/MarkdownParserCore.java` (toàn bộ cơ chế
  `extractHeaderMetadata`, `TOP_META_PATTERN`, `normalizeMetadataKey`).
- `domain/service/markdown/{specpack,selfreview,reviewchecklist,report}/*Parser.java`
  — xác nhận cả 4 dùng `MarkdownParserCore`, có sẵn `headerMetadata`.
- `application/usecase/docparse/{ImplPlanParseService,TestPlanParseService,TestResultsParseService}.java`
  — xác nhận KHÔNG dùng `MarkdownParserCore` (kiến trúc khác).
- `test/UnitTest/.../phase/TicketPhaseEvaluatorServiceTest.java` (toàn văn,
  154 dòng).

**Frontend (đọc toàn văn):**
- `pages/pm-dashboard/components/TicketDetailDrawer.tsx` (toàn bộ, gồm
  `PhaseCard` dòng 115-157).
- `__ tests __/pm-dashboard/TicketDetailDrawer.test.tsx` (toàn bộ, 267 dòng).

**Frontend (đọc một phần / grep):**
- `lib/api.ts` (`PmDashboardTicketRow`, `PmDashboardTicketDetail`, block
  `pmDashboard` trong `endpoints`).
- `lib/utils.ts` (`formatDateTime`).
- `public/locales/{en,ja}/locale.json` (namespace `Pages.PmDashboard`, xác
  nhận ký tự tiếng Nhật literal UTF-8, không escape `\uXXXX`).

**Template ticket (đọc để xác nhận header tồn tại ở mọi loại file):**
- `docs/standards/templates/_ticket-template/{spec-pack,impl-plan,review-checklist,self-review,test-plan,test-results,report,blackbox-testcases}.md`
  — tất cả đều có header `**Create date**`/`**Update date**`.

---

## Implementation tương tự

| Flow | Vai trò tham khảo | Đã đọc mức nào |
|---|---|---|
| **PM Dashboard ticket detail** (`PmDashboardService.detail` → `PmDashboardJdbcAdapter.findDetail`) | Domain chính sẽ mở rộng — pattern hexagonal đầy đủ (Service mỏng → Port → JDBC Adapter `NamedParameterJdbcTemplate` + SQL text block → Controller → DTO record `from(...)`) | Toàn văn các đoạn liên quan |
| **Artifact Scanner** (`application/usecase/scanner/`) | Nguồn dữ liệu gốc (`tbl_fact_artifact_snapshot`) và luồng đọc blob nội dung file (`source.readBlob`) | Đọc kỹ `scanTicketDirectory`, `buildSnapshot` toàn văn |
| **`MarkdownParserCore`** | Cơ chế generic đọc header `**Key**: Value` của mọi file `.md` — nền tảng kỹ thuật cho service mới của ticket này | Toàn văn |
| **4 parser dùng `MarkdownParserCore`** (`SpecPackMarkdownParser`, `ReviewChecklistMarkdownParser`, `SelfReviewMarkdownParser`, `ReportMarkdownParser`) | Ví dụ cách 1 parser lấy `headerMetadata` từ `MarkdownDocument` | Đọc đoạn liên quan |
| **TicketPhaseEvaluatorService** | Cách hệ thống xác định "current phase" — khác hoàn toàn khái niệm phase-timeline mới | Toàn văn |
| **Security Dashboard** (`docs/changes/SECURITY-FINDING-RESOLUTION-TIME/`) | Ticket khác đã làm xong "Tech Lead prep" tương tự — dùng làm template cấu trúc cho 3 file này | Đọc toàn văn 3 file làm mẫu |

---

## Pattern nên dùng

1. **Đọc dữ liệu dashboard**: `NamedParameterJdbcTemplate` + SQL text block
   (`"""..."""`), named parameter (`:paramName`) — đúng 100% style
   `PmDashboardJdbcAdapter.findDetail` (dòng 215-270). Không dùng MyBatis
   Mapper cho phần đọc mới.
2. **Service mỏng**: `PmDashboardService.detail(...)` chỉ gọi
   `repository.findDetail(...)` rồi `requirePm(...)`.
3. **Trích `create_date`/`update_date` bằng 1 service mới dùng chung**: gọi
   trực tiếp `new MarkdownParserCore().parse(blobContent,
   sourcePath).headerMetadata()` trên nội dung blob (`source.readBlob(...)`,
   đã có sẵn trong luồng scan) cho **cả 7 file mục tiêu** — không phụ thuộc
   4 parser chuyên biệt hiện có, không phụ thuộc
   `ImplPlanParseService`/`TestPlanParseService`/`TestResultsParseService`,
   không cần viết parser "full" cho `blackbox-testcases.md`. Tên service cụ
   thể do `impl-plan.md` đặt.
4. **Lưu trữ**: `create_date`/`update_date` đã parse lưu vào **1 bảng mới
   riêng** (ví dụ tên `tbl_fact_artifact_document_date` — `impl-plan.md`
   chốt tên/schema chính xác), KHÔNG dùng chung `parsed_summary` (đã có 4
   parser khác ghi vào, dễ xung đột). Cần 1 port/adapter method mới (ví dụ
   `upsertArtifactDocumentDates(...)`) — không tái sử dụng
   `updateSnapshotParsedSummary`.
5. **Công thức**: Dwell Time 1 file = `update_date − create_date`. Dwell
   Time 1 phase = **cộng dồn** `Σ(update_date − create_date)` của tất cả
   file thuộc phase đó (mapping qua `tbl_dim_artifact_type.phase_id`).
6. **DTO mapping**: mỗi model có DTO record riêng + static factory
   `from(Model m)` — đúng pattern `PmDashboardDtos.java`.
7. **Record bất biến, 1 nơi khởi tạo duy nhất**: `DashboardTicketDetail`
   chỉ được tạo ở `PmDashboardJdbcAdapter.findDetail(...)` (dòng 259-269).
8. **Exception**: `NotFoundException("Pages.PmDashboard.NotFound")` dùng lại
   khi ticket không tồn tại.
9. **Permission**: `service.requirePm(caller, detail.row().projectId())`.
10. **FE type**: thêm field vào interface `PmDashboardTicketDetail` trong
    `lib/api.ts` (named export).
11. **FE hiển thị**: sửa hàm `PhaseCard` (dòng 115-157 của
    `TicketDetailDrawer.tsx`) — thêm field mới ngay dưới `phaseCreatedAt`
    (146-152), theo pattern `{t("Pages.PmDashboard.xxx", { defaultValue })}: {value}`.
12. **i18n**: chỉ cần 1 key tên field mới (không nhãn trạng thái) ở cả 3
    file `public/locales/{en,ja,vi}/locale.json` dưới `Pages.PmDashboard`.
13. **`lib/api.ts` không cần sửa phần fetch** —
    `endpoints.pmDashboard.detail(ticketId)` dùng generic
    `api.get<PmDashboardTicketDetail>(...)`.
14. **FE test**: theo đúng pattern `TicketDetailDrawer.test.tsx` — mock
    `react-i18next`, dựng `detail` bằng `satisfies PmDashboardTicketDetail`,
    render qua `MemoryRouter` + `QueryClientProvider`.

---

## Pattern cấm dùng

- **Không tạo endpoint mới** — field bổ sung vào response
  `GET /api/v1/pm/dashboard/tickets/{ticketId}/detail` đã có.
- **Không sửa `TicketPhaseEvaluatorService`**, `upsertTicketPhaseStatus`,
  `tbl_fact_ticket_phase_status` — 2 khái niệm phase (current phase cũ vs.
  phase timeline/dwell time mới) tồn tại song song.
- **Không mở rộng `ArtifactScannerService.CHANGE_TARGET_FILES`/
  `PHASE0_TARGET_FILES`**.
- **Không ALTER/DROP** bảng hiện có (`tbl_fact_artifact_snapshot`,
  `tbl_fact_ticket_phase_status`, ...) — chỉ được thêm bảng mới/VIEW mới
  (Flyway migration mới, cần hỏi người dùng trước khi `flyway migrate` —
  `00-safety.md §3`).
- **Không dùng cột `created_at`/`collected_at`/`source_updated_at` của
  `tbl_fact_artifact_snapshot` làm mốc Entry** — nguồn chính thức là
  `create_date`/`update_date` tự khai báo trong header file `.md`.
- **Không dùng chung cột `parsed_summary`** cho dữ liệu
  `create_date`/`update_date` mới — dùng bảng mới riêng (xem "Pattern nên
  dùng" #4).
- **Không dùng MyBatis Mapper** cho phần đọc.
- **Không dùng `@Data`** trên model mới nếu có — dùng
  `@Builder @Getter @Setter @NoArgsConstructor @AllArgsConstructor`.
- **Không field-inject `@Autowired`** — constructor injection.
- **Không gọi `fetch` trực tiếp ở FE**, không dùng `any` không có comment
  `// reason:`.
- **Không dùng default export** ở file FE nào được sửa/tạo mới.
- **Không tự thêm `React.forwardRef`** cho các hàm Card trong
  `TicketDetailDrawer.tsx` — function component nội bộ của trang, không
  phải component tái sử dụng trong `components/ui/`.
- **Không hiển thị nhãn trạng thái nào** (Pending/Completed/InProgress) —
  chỉ 1 giá trị Dwell Time hoặc `"-"`.
- **Không dùng `vw_artifact_inventory_current`** làm nguồn cho Dwell Time
  nếu chưa xác nhận migration mới nhất định nghĩa nó (đã bị định nghĩa lại
  ≥3 lần: `V160`, `V161`, `V503`).

---

## Method tồn tại / method không tồn tại

### Method THỰC SỰ tồn tại (được phép gọi)

**`PmDashboardService`**: `summary(...)`, `insights(...)`, `tickets(...)`,
`detail(ticketId, caller)`, `refresh(caller)`, `options(projectId, caller)`,
`getTemplateUsage(...)`, `exportCsv(...)`, `requirePm(caller, projectId)`,
`requireAnyAccess(caller)`.

**`PmDashboardRepositoryPort`** (interface): `findDetail(UUID ticketId)` →
`Optional<DashboardTicketDetail>` (các method khác suy ra từ cách gọi
trong Service, chưa đọc toàn bộ chữ ký interface).

**`PmDashboardJdbcAdapter`**: `findDetail(ticketId)` (215-270),
`findTickets(...)`, `findAttentionTickets(...)`, cùng helper `private`
(`countReviews`, `findMissingEvidence`, `findRisks`, `findExceptions`,
`findIssueItems`, `findScoreBreakdown`, `mapTicketRow`, ...).

**`DashboardTicketDetail`** (record): `row()`, `createdAt()`,
`ownerDisplay()`, `reviewCount()`, `missingEvidenceItems()`, `riskItems()`,
`exceptionItems()`, `issueItems()`, `scoreBreakdown()`, `traceabilityUrl()`.
**Thêm field mới phải sửa cả record (`PmDashboardModels.java:266`) và nơi
khởi tạo duy nhất (`PmDashboardJdbcAdapter.java:259`).**

**`PmDashboardDtos.PmDashboardTicketDetailDto`** (record): tương tự — sửa
cả record (`PmDashboardDtos.java:334`) và `from(...)` (`:346`).

**`MarkdownParserCore`**: `parse(String content, String sourcePath)` →
`MarkdownDocument` (public); `MarkdownDocument.headerMetadata()` → `Map<String,
String>` với key normalize (`create_date`, `update_date`, `ticket_id`, ...).

### Method / cột KHÔNG tồn tại — AI dễ tự bịa, TUYỆT ĐỐI KHÔNG dùng

| Bị bịa ra (SAI) | Lý do sai | Thực tế |
|---|---|---|
| `PmDashboardRepositoryPort.findPhaseDwellTime(...)` | Chưa tồn tại | Phải tự tạo mới ở `impl-plan.md` nếu chọn hướng port method riêng |
| `ArtifactSnapshot.enteredAt()` / `getEntryTimestamp()` | Domain "Entry" chỉ tồn tại trong tài liệu ticket này | `ArtifactSnapshot` không có field "entry"/"detected" |
| `tbl_fact_artifact_snapshot.created_at` = "thời điểm evidence xuất hiện" | **SAI đã xác minh bằng code** — `ArtifactScannerService.scanTicketDirectory` tạo dòng cho cả 7-8 file ngay từ lần scan đầu tiên (kể cả file chưa tồn tại), `created_at` không đổi sau đó dù file xuất hiện muộn hơn | Nguồn Entry chính thức là `create_date`/`update_date` parse từ header file `.md` |
| `Duration.toString()` cho ra `"27:20:05"` | **Sai** — cho ra ISO-8601 (`"PT27H20M5S"`) | Phải tự viết formatter thủ công |
| `formatDuration`/`DurationUtils` có sẵn trong `EDCAP_BE`/`EDCAP_FE` | Chưa tìm thấy qua các file đã đọc | Coi là chưa có, phải viết mới |
| `PmDashboardModels.DashboardTicketDetail.withPhaseDwellTime(...)` | Record Java không tự sinh method `withXxx` | Tạo lại toàn bộ instance bằng constructor đầy đủ tham số |
| `ImplPlanParseService`/`TestPlanParseService`/`TestResultsParseService` có sẵn `headerMetadata` | **Không** — 3 service này không dùng `MarkdownParserCore` | Service mới của ticket này gọi `MarkdownParserCore` trực tiếp, độc lập với 3 service trên |
| Parser có sẵn cho `blackbox-testcases.md` | **Không tồn tại** — đã grep toàn bộ codebase, không có class nào | Dùng chung service mới (đọc header qua `MarkdownParserCore`), không cần viết parser "full" riêng cho file này |
| `persistence.updateSnapshotParsedSummary(...)` dùng được cho `create_date`/`update_date` | Method này **ghi đè toàn bộ** cột `parsed_summary`, dùng lại sẽ xoá dữ liệu của 4 parser khác | Dùng bảng mới riêng + method mới, không tái sử dụng method này |

---

## Mapping

| DB column (`tbl_fact_artifact_snapshot`) | Dùng cho field này? | Ghi chú |
|---|---|---|
| `ticket_id` | ✅ Có | Filter theo ticket cho toàn bộ 7 phase (phase `0-A` ngoài scope) |
| `artifact_type_id` → `tbl_dim_artifact_type.phase_id` | ✅ Có (join) | Nguồn phase-mapping chính thức, join tại thời điểm query (không tin cột `phase_id` denormalize sẵn trên snapshot nếu seed artifact_type từng đổi) |
| `exists_flag` | ✅ Có | Điều kiện lọc file thực sự tồn tại |
| `created_at`, `collected_at`, `source_updated_at` | ❌ Không dùng | Không phản ánh đúng thời điểm file xuất hiện/hoàn thành — xem "Method không tồn tại" |
| `parsed_summary` | ❌ Không dùng cho field này | Đã có 4 parser khác ghi/ghi đè — dùng bảng mới riêng |

**Nguồn dữ liệu mới cho Dwell Time (bảng mới, chưa tồn tại — cần tạo ở
`impl-plan.md`):**

| Bảng mới (tên đề xuất) | Cột đề xuất | Ghi chú |
|---|---|---|
| `tbl_fact_artifact_document_date` | `artifact_snapshot_id` (FK), `document_create_at` (TIMESTAMPTZ), `document_update_at` (TIMESTAMPTZ) | Lưu `create_date`/`update_date` đã parse từ header mỗi file; tên/schema chính xác do `impl-plan.md` chốt |

**Model / DTO / FE chain (cần đồng bộ khi thêm field):**

| Layer | Vị trí | Thay đổi cần thiết |
|---|---|---|
| DB | Bảng mới (đề xuất `tbl_fact_artifact_document_date`) | Migration mới, additive-only |
| Application model | `PmDashboardModels.DashboardTicketDetail` (record, 266-277) | Thêm `List<...> phaseDwellTime` |
| Adapter | `PmDashboardJdbcAdapter.findDetail(...)` (215-270) | Sửa 1 nơi khởi tạo (259-269) — thêm subquery đọc bảng mới, group theo phase |
| Web DTO | `PmDashboardDtos.PmDashboardTicketDetailDto` (334-360) | Thêm field + cập nhật `from(...)` |
| Controller | `PmDashboardController.detail(...)` (80-86) | Không cần sửa |
| FE type | `lib/api.ts` (`PmDashboardTicketDetail`) | Thêm `phaseDwellTime: { phaseCode: string; phaseOrder: number; dwellTime: string | null }[]` |
| FE hiển thị | `TicketDetailDrawer.tsx` → `PhaseCard` (115-157) | Thêm dòng hiển thị mới dưới `phaseCreatedAt` |
| i18n | `public/locales/{en,ja,vi}/locale.json` → `Pages.PmDashboard` | Thêm 1 key tên field |

**Master data / code value:**
- `tbl_dim_phase`: 11 phase, `phase_code` (`VARCHAR(20) UNIQUE`),
  `phase_order` (`INT`) — danh sách 7 phase hiển thị: `1, 3, 4, 5, 6, 7, 8`.
- `tbl_dim_artifact_type`: `artifact_type_code`, `default_file_name`,
  `phase_id` (FK) — nguồn phase-mapping chính thức.
- **Không có khái niệm SEQNO/formItemNm** trong vùng dữ liệu này.

---

## Rule nghiệp vụ đặc thù

**1. Nguồn Entry chính thức**: `create_date`/`update_date` tự khai báo
trong header mỗi file `.md` (dòng `**Create date**: ...` / `**Update
date**: ...`), parse qua `MarkdownParserCore.parse(...).headerMetadata()`
(key normalize thành `create_date`/`update_date`). Dwell Time 1 file =
`update_date − create_date`; Dwell Time 1 phase = cộng dồn tổng của tất cả
file thuộc phase đó.

**2. Rủi ro chất lượng dữ liệu (đã được người dùng chấp nhận, không phải
lỗi cần sửa)**: `Create date`/`Update date` là text tự gõ bởi người/AI
soạn ticket — có thể quên cập nhật. 100% ví dụ hiện có trong dự án chỉ ở
định dạng `YYYY-MM-DD` (không giờ:phút:giây) — nếu ticket cũ không được bổ
sung giờ, độ phân giải Dwell Time tối đa là "ngày" (có thể ra `0` dù thực
tế cách nhau vài giờ). Ghi rõ giới hạn này trong `spec-pack.md`
(Assumption), không được âm thầm bỏ qua.

**3. Đa ngôn ngữ**: 3 ngôn ngữ `en`, `ja`, `vi`, namespace `"locale"`. Chỉ
cần 1 label tên field mới (không có nhãn trạng thái).

**4. Master data**: `tbl_dim_phase`, `tbl_dim_artifact_type` là 2 bảng
master duy nhất liên quan.

**5. Quan hệ bảng đặc thù**: `tbl_fact_artifact_snapshot.phase_id` được
denormalize từ `tbl_dim_artifact_type.phase_id` tại thời điểm scan — nếu
seed `tbl_dim_artifact_type` bị sửa sau đó, dòng snapshot cũ không tự cập
nhật. JOIN lại `tbl_dim_artifact_type` tại thời điểm query để lấy mapping
mới nhất.

**6. `tbl_fact_ticket_phase_status` KHÔNG liên quan** tới field mới (chỉ 1
dòng/ticket, `dwell_time_minutes` luôn `NULL`) — không đụng vào.

**7. Permission**: dùng lại `service.requirePm(caller,
detail.row().projectId())`.

**8. Encoding**: giữ nguyên literal UTF-8 cho ký tự tiếng Nhật trong
`locale.json`, không escape `\uXXXX` (xem `ja/locale.json` hiện tại).

---

## Chú ý khi implement

1. Tạo migration mới cho bảng lưu `create_date`/`update_date` (đề xuất
   `tbl_fact_artifact_document_date`) — additive-only, không ALTER bảng
   hiện có. Hỏi người dùng trước khi chạy `flyway migrate`.
2. Viết 1 service mới dùng chung gọi `MarkdownParserCore.parse(blobContent,
   sourcePath).headerMetadata()` cho cả 7 file mục tiêu — không phụ thuộc
   `ImplPlanParseService`/`TestPlanParseService`/`TestResultsParseService`
   hay bất kỳ parser chuyên biệt nào.
3. `DashboardTicketDetail` là Java record — chỉ 1 nơi khởi tạo
   (`PmDashboardJdbcAdapter.java:259-269`) — sửa cả định nghĩa record
   (`PmDashboardModels.java:266-277`) và nơi khởi tạo.
4. Viết formatter `hh:mm:ss` thủ công (không dùng `Duration.toString()`),
   không giới hạn 24 giờ (ví dụ `27:20:05`).
5. Công thức phase có ≥2 file: **cộng dồn** `Σ(update−create)`, không phải
   khoảng bao trùm MIN/MAX.
6. Lỗi/timeout khi lấy dữ liệu → hiển thị `"-"` cho phase liên quan, không
   throw lỗi UI, không retry tự động.

## Chú ý khi review

1. Kiểm tra công thức Dwell Time dùng đúng bảng mới
   (`tbl_fact_artifact_document_date` hoặc tên tương đương), KHÔNG dùng
   `created_at`/`collected_at`/`source_updated_at`/`parsed_summary`.
2. Kiểm tra `DashboardTicketDetail` record cập nhật đủ ở 1 nơi khởi tạo duy
   nhất.
3. Kiểm tra không dùng `Duration.toString()`/`.format()` mặc định.
4. Kiểm tra công thức cộng dồn đúng khi phase có ≥2 file.
5. Kiểm tra JOIN `tbl_dim_artifact_type.phase_id` tại thời điểm query.
6. Kiểm tra không có ALTER/DROP nào lên bảng hiện có — chỉ thêm bảng/VIEW
   mới.
7. Kiểm tra permission `requirePm` vẫn được gọi trước khi trả field mới.
8. Cả 3 file `locale.json` (en/ja/vi) cập nhật đồng bộ, không nhãn trạng
   thái, giữ nguyên literal UTF-8 tiếng Nhật.
9. Kiểm tra service mới không đụng/ghi đè `parsed_summary` của 4 parser
   chuyên biệt hiện có.

## Chú ý khi test

1. Test parse `create_date`/`update_date` từ header thật (`headerMetadata`),
   bao gồm case chỉ có ngày (không giờ).
2. Test service mới hoạt động đồng nhất trên cả 7 loại file (không cần
   phân biệt file có/không có parser chuyên biệt).
3. Test cộng dồn Dwell Time cho phase có ≥2 file.
4. Test giá trị `"-"` khi: chưa có dữ liệu, lỗi/timeout API.
5. Test format `hh:mm:ss` > 24 giờ.
6. Test regression: ghi bảng mới không làm thay đổi `parsed_summary` hiện
   có của 4 parser khác.
7. FE test theo pattern `TicketDetailDrawer.test.tsx` (mock `react-i18next`,
   `MemoryRouter` + `QueryClientProvider`) — verify field mounted thật, AC
   Closure theo `40-testing.md`.
8. Không mock domain record trực tiếp làm "dữ liệu giả" — record
   instantiation là dữ liệu thật theo `40-testing.md`.
