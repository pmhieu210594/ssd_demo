# impl-plan

**Ticket ID**: AI-REVIEW-KPI-IMPROVEMENT
**Create date**: 2026-08-18
**Author**: Principal Engineer (Claude)

Tài liệu này là **skeleton** — mô tả ý định thay đổi theo class/function/method, không viết full code.
Trước khi code thật, đọc `context.md` + `ticket-rules.md` (whitelist method tồn tại / cấm bịa method)
và giải quyết các mục trong "Gate trước implementation" bên dưới.

---

## Phương châm implement

- Additive-only: không sửa hành vi hiện có của bất kỳ endpoint/method/bảng nào — chỉ thêm mới.
- Copy cấu trúc (không copy logic sai) từ cặp mẫu `TemplateUsageStatWriter`/`TemplateUsageStatJdbcAdapter`
  cho phần ghi, và `findTemplateUsage`/`TemplateUsageDto` cho phần đọc — nhưng đảo ngược đúng 2 điểm
  khác biệt bản chất: (1) SQL ghi là **overwrite** không phải **increment**; (2) response đọc là
  **1 object** không phải **List**.
  Xem `ticket-rules.md` (18 rule MUST, 15 rule MUST NOT — hai rule quan trọng nhất là #4 và #10).
- Ghi §8 là best-effort: không được làm fail luồng merge PR nếu ghi lỗi (try/catch, log WARN, không
  rethrow) — nguyên tắc đã tồn tại ở `validateTemplateUsage`, áp dụng nguyên trạng cho writer mới.
- Không tái phát minh DDL — dùng nguyên DDL đã chốt tại `spec-pack.md` §8 (chỉ trích dẫn, không đổi
  tên cột/kiểu dữ liệu tùy tiện). Gate #3 (đã đóng, OI-AIRKI-12) xác nhận tên cột/kiểu FK khớp 100%,
  không phát hiện sai lệch.

## Danh sách file thay đổi

Xem đầy đủ tại `source-map.md` mục 1 — không lặp lại toàn bộ ở đây. Tóm tắt theo lớp kiến trúc:

| Lớp | File | Loại thay đổi |
|---|---|---|
| DB | `db/migration/V511__add_ai_finding_stat_tracking.sql` (mới) | Tạo bảng |
| Application (port out) | `application/port/out/persistence/AiFindingStatPort.java` (mới) | Port ghi, 1 method |
| Application (port out) | `application/port/out/persistence/PmDashboardRepositoryPort.java` | Thêm 1 method đọc |
| Application (usecase) | `application/usecase/ingestion/AiFindingStatWriter.java` (mới) | `@Component` writer |
| Application (usecase) | `application/usecase/ingestion/GithubWebhookService.java` | Constructor injection + 1 lời gọi |
| Application (usecase) | `application/usecase/pmdashboard/PmDashboardModels.java` | Thêm 1-2 record |
| Application (usecase) | `application/usecase/pmdashboard/PmDashboardService.java` | Thêm 1 service method |
| Infrastructure | `infrastructure/persistence/adapter/AiFindingStatJdbcAdapter.java` (mới) | Implement port ghi |
| Infrastructure | `infrastructure/persistence/adapter/PmDashboardJdbcAdapter.java` | Implement method đọc mới |
| Web | `web/dto/PmDashboardDtos.java` | Thêm 1 DTO record + `from(...)` |
| Web | `web/rest/PmDashboardController.java` | Thêm 1 endpoint |
| Test (BE) | tương ứng từng file trên (source root theo class đang mở rộng — xem `source-map.md` mục 3) | Thêm test case |
| FE | `lib/api.ts` | Thêm method trong object `pmDashboard` |
| FE | `pages/pm-dashboard/components/AiFindingStatsCard.tsx` (mới, tên đề xuất) | Component hiển thị 5 KPI |
| FE | `pages/pm-dashboard/PMDashboardPage.tsx` (đường dẫn thật, đã xác nhận — Gate #6 đóng) | Compose component mới |
| FE | Test tương ứng component mới | Thêm test + xác nhận mount trên trang thật (AC Closure) |

## Các bước thay đổi

Thứ tự đề xuất (mỗi bước phải xanh trước khi sang bước sau):

1. Đọc toàn bộ 33 test case `GithubWebhookServiceTest.java` + 3 file test PM Dashboard hiện có (MUST
   rule #15) — chưa đọc thì chưa viết dòng code nào.
2. Toàn bộ Gate đã đóng (Gate #1/2/3/4/6 CLOSED, Gate #5 skip theo yêu cầu người dùng — xem "Gate
   trước implementation") — không còn gate nào chặn bước 3 trở đi.
3. Tạo migration `V511` (re-check version mới nhất trong `db/migration/` ngay trước khi tạo file,
   không giả định `V510` vẫn là file cuối cùng).
4. Tạo port ghi (`AiFindingStatPort`) + adapter ghi (`AiFindingStatJdbcAdapter`) + writer
   (`AiFindingStatWriter`) — theo thứ tự domain-outward (port trước, adapter sau).
5. Viết logic parse §8 từ Markdown (dùng `MarkdownParserCore`) theo thiết kế positional/structural đã
   xác nhận ở Gate #4 (đóng 2026-08-19) — xử lý riêng, review kỹ trước khi nối vào writer.
6. Nối writer vào `GithubWebhookService.handlePullRequest` (constructor injection + 1 lời gọi tại
   đúng vị trí đã xác nhận) — chạy lại toàn bộ 33 test hiện có, xác nhận không có test nào đỏ.
7. Thêm method đọc vào `PmDashboardRepositoryPort`/`PmDashboardJdbcAdapter`, model, DTO.
8. Thêm service method (`PmDashboardService`) + endpoint (`PmDashboardController`).
9. Test BE cho toàn bộ các thành phần mới ở bước 4-8.
10. FE: `api.ts` method → component → compose vào trang thật → test FE + xác nhận mount thật (AC
    Closure) → verify thủ công trên trình duyệt dev (`npm run dev`, có projectId/repositoryId có dữ
    liệu thật).

## Ý định thay đổi theo class/function/method

- **`AiFindingStatPort`** (mới, interface): 1 method, ví dụ
  `void upsert(AiFindingStatRecord record)` — record chứa `ticketId`, `projectId`, `repositoryId`,
  5 cặp numerator/denominator (hoặc số cột thực tế theo DDL sau khi Gate #2 xác nhận). Không thêm
  method đọc vào port này — port ghi và port đọc tách biệt (đọc dùng `PmDashboardRepositoryPort`,
  theo đúng convention hiện có).
- **`AiFindingStatJdbcAdapter`** (mới, `@Repository`/`@Component`): implement `upsert(...)` bằng
  `NamedParameterJdbcTemplate` + `INSERT ... ON CONFLICT (ticket_id) DO UPDATE SET col = EXCLUDED.col`
  (overwrite, KHÔNG `col = table.col + EXCLUDED.col`). Không dùng MyBatis XML.
- **`AiFindingStatWriter`** (mới, `@Component` riêng biệt): 1 method
  `@Transactional(propagation = Propagation.REQUIRES_NEW) void recordStat(...)` — nhận input đã parse
  sẵn (ticketId, các số liệu §8), gọi `AiFindingStatPort.upsert(...)`, bắt `DataAccessException` bên
  trong hoặc để caller (`GithubWebhookService`) bắt — quyết định cụ thể theo đúng mẫu
  `TemplateUsageStatWriter`/`validateTemplateUsage` (đọc lại 2 file này để xác nhận catch nằm ở đâu
  trước khi code, không suy đoán).
- **`GithubWebhookService.handlePullRequest`**: thêm field constructor-injected
  `AiFindingStatWriter aiFindingStatWriter` (hoặc tên tương ứng); chèn 1 lời gọi
  `aiFindingStatWriter.recordStat(...)` giữa dòng ~296 và ~298, dùng lại guard điều kiện đã có
  (`"closed".equals(action) && pullRequest.path("merged").asBoolean(false)`, dòng ~253-256) — không
  phát minh điều kiện mới. Trước lời gọi writer: đọc nội dung `ai-review.md` qua
  `ArtifactScannerSourcePort` (`resolveRevision → listTree → readBlob`), parse §8 qua
  `MarkdownParserCore`. Logic parse nên tách thành 1 method private mới hoặc 1 class helper riêng
  (không nhồi hết vào `handlePullRequest` — nhưng đây là chi tiết code review, không phải interface
  công khai mới cần port). **Thiết kế parse đã xác nhận (Gate #4 đóng, 2026-08-19)**: định vị heading
  theo số thứ tự `8.` bằng regex (không match tên tiêu đề đã dịch) → sau khi tìm được section, lấy
  bảng Markdown ngay dưới heading đó → trích 5 cặp numerator/denominator theo **đúng vị trí/thứ tự
  dòng và cột cố định trong bảng** (dòng thứ N ứng với KPI thứ N theo đúng thứ tự 7 dòng của
  `_ticket-template/ai-review.md`, cột numerator/denominator theo đúng vị trí cột cố định) — hoàn
  toàn không match theo nhãn/label text, đảm bảo độc lập ngôn ngữ (AC-AIRKI-2/3) đúng như `A-AIRKI-1`.
  **Đã xác nhận (OI-AIRKI-12, 2026-08-19)**: `project_id`/`repository_id` truyền vào
  `aiFindingStatWriter.recordStat(...)` lấy trực tiếp từ `repository.get().projectId()`/
  `.repositoryId()` — đã resolve sẵn và nằm trong scope xuyên suốt `handlePullRequest` (cùng biến
  dùng tại các dòng 183, 210, 255, 309, 359, 397). KHÔNG cần join qua `tbl_dim_ticket` để suy ra
  `repository_id` (bảng này không có cột đó) — dùng thẳng biến webhook context có sẵn.
- **`PmDashboardRepositoryPort`**: thêm 1 method, ví dụ
  `AiFindingStatsSummary findAiFindingStats(UUID projectId, UUID repositoryId)` — trả 1 object, không
  phải `List`.
- **`PmDashboardJdbcAdapter`**: implement method trên bằng 1 câu SUM-aggregate (xem "Phương châm
  data/DB/query" — **không** cần `LEFT JOIN` như `findTemplateUsage`, vì đây là aggregate 1 hàng chứ
  không phải enumerate danh sách phase cố định).
- **`PmDashboardModels`**: thêm record kết quả JDBC (ví dụ `AiFindingStatsRow`) chứa các SUM thô
  (numerator/denominator từng KPI) — tính rate ở tầng DTO, không tính trong SQL, để nhất quán với
  cách `TemplateUsageDto.from()` đang làm (giữ nguyên convention hiện có, không đổi sang tính rate
  bằng SQL `ROUND()` dù `spec-pack.md` §9 viết công thức ở dạng SQL-style — đó chỉ là cách biểu diễn
  công thức, không phải chỉ định tầng tính toán).
- **`PmDashboardDtos`**: thêm DTO record (ví dụ `AiFindingStatsDto`) + `from(AiFindingStatsRow row,
  String repositoryName)` — áp dụng công thức rate y hệt `TemplateUsageDto.from()` cho từng KPI độc
  lập (4 KPI dùng chung denominator `ai_review_finding_total_count`, 1 KPI dùng denominator riêng —
  theo `A-AIRKI-3`, đã được người dùng xác nhận giữ nguyên, Gate #2 đã đóng).
- **`PmDashboardService`**: thêm method, ví dụ
  `AiFindingStatsDto getAiFindingStats(AuthUserContext caller, UUID projectId, UUID repositoryId)` —
  gọi `requirePm(caller, projectId)` đầu tiên, sau đó gọi port đọc + JOIN lấy `repositoryName`, map
  sang DTO.
- **`PmDashboardController`**: thêm 1 `@GetMapping("/ai-finding-stats")` với
  `@RequestParam UUID projectId, @RequestParam UUID repositoryId` (không `required = false`), gọi
  service, trả `ResponseEntity.ok(dto)`.
- **FE `api.ts`**: thêm method trong object `pmDashboard`, ví dụ
  `getAiFindingStats(projectId: string, repositoryId: string): Promise<AiFindingStatsDto>`.
- **FE component mới**: nhận props DTO đã fetch (1 object, không phải list) qua `useQuery` mới trong
  `PMDashboardPage.tsx`, key kiểu `["pm-dashboard", "ai-finding-stats", filters.projectId,
  filters.repositoryId]`, gọi `endpoints.pmDashboard.getAiFindingStats(...)`, `enabled:
  Boolean(filters.projectId) && Boolean(filters.repositoryId)` — cùng pattern các query khác trong
  trang. **Mẫu tham chiếu đã xác nhận (Gate #6 đóng, OI-AIRKI-13, 2026-08-19): `SummaryCards.tsx`**
  (grid KPI card từ 1 object, `SummaryCardDef[]` build inline từ `summary?.xxx ?? 0`, mỗi card có
  accent bar + `text-3xl font-black tabular-nums`), **KHÔNG PHẢI `TemplateUsageByPhase.tsx`** (component
  đó render list qua `CDataTable`, sai cấu trúc cho response 1 object). Render component mới ngay sau
  `<SummaryCards summary={summary} />` trong `PMDashboardPage.tsx`, hiển thị 5 KPI, dùng
  `formatUsageRate`/hàm tương đương cho `null → "-"`.

## Phương châm data/DB/query

- **Bảng đích**: `tbl_fact_ai_finding_stat` (DDL đã chốt tại `spec-pack.md` §8, không lặp lại SQL đầy
  đủ ở đây).
- **Ghi (upsert)**: ý định — 1 câu `INSERT ... ON CONFLICT (ticket_id) DO UPDATE SET <mỗi cột số> =
  EXCLUDED.<cột>, updated_at = now()`. Điều kiện: khóa xung đột là `ticket_id` (PK/unique). Rủi ro số
  lượng: 1 lần ghi/lần PR merge có `ai-review.md` — tần suất thấp (không phải batch), không có rủi ro
  performance đáng kể.
- **Đọc (aggregate)**: ý định — 1 câu `SELECT SUM(...), SUM(...), ... FROM tbl_fact_ai_finding_stat
  WHERE project_id = :projectId AND repository_id = :repositoryId`. Điều kiện WHERE: lọc trực tiếp
  bằng 2 cột denormalize sẵn trên bảng fact (không cần JOIN `tbl_dim_ticket` để lọc, vì
  `project_id`/`repository_id` đã có sẵn trên chính bảng fact theo DDL §8).
  **Lưu ý quan trọng (khác với `findTemplateUsage`)**: KHÔNG cần `LEFT JOIN` ở đây, vì:
  (a) không có yêu cầu enumerate 1 danh sách cố định (như danh sách phase) để hiển thị hàng có giá trị
  0 cho phase chưa có dữ liệu;
  (b) SQL `SUM()` trên tập rỗng tự nhiên trả `NULL`, khớp chính xác với yêu cầu OI-AIRKI-7
  ("rate = null chỉ khi tổng denominator = 0 trên toàn scope") — không cần logic COALESCE/LEFT JOIN
  thêm để đạt hành vi này.
  Rủi ro số lượng/performance: số hàng per (project, repository) tối đa bằng số ticket đã merge PR có
  `ai-review.md` trong repository đó — thấp (hàng trăm tới thấp nghìn), cần index
  `(project_id, repository_id)` (đã có trong DDL §8) để tránh seq scan khi dữ liệu tăng theo thời gian.
- **JOIN lấy `repositoryName`**: 1 câu JOIN riêng hoặc phần join thêm vào câu trên tới
  `tbl_dim_repository` lấy `repo_name_masked AS repository_name` — theo đúng alias pattern đã dùng ở
  `PmDashboardJdbcAdapter.java` (dòng 88, phần build danh sách filter option). Điều kiện: match theo
  `repository_id`. Rủi ro: không đáng kể, 1 hàng dimension.
- **Không dùng MyBatis XML** cho bảng mới — JDBC thuần qua `NamedParameterJdbcTemplate`, nhất quán với
  mọi adapter PM Dashboard/Template Usage khác.
- **Tenant/repository scoping**: vì không dùng LEFT JOIN ở đây, rule "scoping trong ON không trong
  WHERE" (MUST rule #7, dành cho trường hợp có LEFT JOIN) **không áp dụng trực tiếp** cho câu đọc
  aggregate chính — nhưng vẫn áp dụng nếu impl thực tế cần JOIN thêm bảng khác có tính chất dimension
  cố định trong tương lai.

## Phương châm FE/BE contract

- Contract JSON response: 1 object phẳng chứa `repositoryId`, `repositoryName`, và 5 field rate
  (`Double`, vắng mặt trong JSON khi null — theo đúng hành vi quan sát ở `template-usage`). Tên field
  chính xác của 5 rate: lấy theo `spec-pack.md` §9 (không đặt tên mới tùy tiện).
- Request: `GET` với 2 query param bắt buộc (`projectId`, `repositoryId`) — không có body, không có
  param optional nào khác (không `periodKey` theo OI-AIRKI-3).
- Lỗi: theo hành vi thật của `GlobalExceptionHandler` hiện tại — 403 `ForbiddenException` khi không
  đủ quyền PM (đã xác nhận message `"Component.Permission.Denied"`), 500 (không phải 400) khi thiếu
  param bắt buộc. **Đã quyết định (OI-AIRKI-9, 2026-08-19, Gate #1 đóng)**: giữ nguyên 500, không mở
  rộng phạm vi ticket để thêm handler 400 — nhất quán 100% với mọi API khác trong hệ thống (đã grep
  toàn bộ `web/` xác nhận không API nào có handler riêng cho trường hợp này). FE cần xử lý graceful
  cả 2 trường hợp lỗi mà không giả định luôn nhận được 400.
- Không versioning API riêng cho endpoint mới — dùng chung prefix `/api/v1/pm/dashboard/` hiện có.

## Phương châm Error/Validation/Logging

- Ghi §8: try/catch `DataAccessException` (hoặc superclass phù hợp xác nhận qua đọc lại
  `validateTemplateUsage`), log WARN kèm `ticketId`/`repositoryId` để dễ trace, không rethrow — merge
  PR không bao giờ fail vì lỗi ghi §8.
- Parse §8: nếu không tìm thấy section hoặc bảng không đúng cấu trúc kỳ vọng (ví dụ ticket cũ chưa có
  §8, hoặc AI review output sai định dạng) → coi là "không có dữ liệu", không ghi row mới, không throw
  exception làm gián đoạn `handlePullRequest` — log ở mức INFO/DEBUG (không phải lỗi nghiêm trọng, đây
  là trường hợp mong đợi cho ticket cũ theo OI-AIRKI-8).
- Đọc aggregate: không cần validation logic mới ngoài required-param check có sẵn của Spring; không
  thêm Bean Validation annotation mới (không có pattern này ở tầng PM Dashboard hiện tại). **Đã quyết
  định (OI-AIRKI-9, 2026-08-19)**: không thêm `@ExceptionHandler(MissingServletRequestParameterException.class)`
  — thiếu `projectId`/`repositoryId` tiếp tục trả 500 `INTERNAL_ERROR` như mọi endpoint khác, không mở
  rộng `GlobalExceptionHandler` ngoài phạm vi ticket.
- Log: không log nội dung thô của `ai-review.md` (có thể dài, không cần thiết cho debug) — chỉ log
  `ticketId` + kết quả parse tóm tắt (số liệu đã trích xuất) khi cần debug.
- Không thêm secret-pattern nào vào log — không áp dụng rule redaction của `30-security.md` vì dữ liệu
  không chứa credential.

## Phương châm Test

- **Trước khi viết test mới**: đọc toàn bộ 33 test case `GithubWebhookServiceTest.java` +
  `PmDashboardServiceTest.java` + `PmDashboardJdbcAdapterFindTemplateUsageTest.java` +
  `PmDashboardControllerTest.java` (MUST rule #15, đã ủy quyền nhưng vẫn bắt buộc thực thi).
- **Writer/adapter ghi mới**: unit test tại `src/test/UnitTest/java/...` (theo vị trí class được test,
  không tự chọn source root) — mock port, xác nhận đúng SQL upsert overwrite (không phải increment)
  qua `ArgumentCaptor`, theo mẫu `PmDashboardJdbcAdapterFindTemplateUsageTest` cho cách assert SQL text.
- **`GithubWebhookServiceTest`**: thêm test case mới tại `src/test/java/...` xác nhận writer được gọi
  đúng 1 lần khi `action=="closed" && merged==true` và **không** gọi khi ngược lại; đồng thời chạy lại
  toàn bộ 33 test hiện có, xác nhận xanh (không cần sửa test cũ nếu constructor injection dùng
  `@Mock`/tương đương đúng cách).
- **Service method mới**: test tại `PmDashboardServiceTest.java`, theo style `@Mock`/
  `@ExtendWith(MockitoExtension.class)` — test case tối thiểu: permission gate (denied khi không phải
  PM, allowed khi ADMIN hoặc PM đúng project — theo mẫu 3 test case `templateUsage_*` đã có).
- **Controller endpoint mới**: test tại `PmDashboardControllerTest.java`, theo style
  `Mockito.mock(...)` + `MockMvcBuilders.standaloneSetup(...).setControllerAdvice(new
  GlobalExceptionHandler())` (KHÔNG dùng `@WebMvcTest`/`@MockBean` dù đó là ví dụ generic trong
  `docs/standards/testing.md`) — test case tối thiểu: 200 với dữ liệu hợp lệ, 403 khi forbidden
  (theo mẫu `templateUsage_mapsForbidden`), rate null → field vắng mặt trong JSON (theo mẫu
  `templateUsage_returnsDtoListWithComputedUsageRate`).
- **FE**: unit test component mới (Vitest + Testing Library) + xác nhận mount trên trang PM Dashboard
  thật (AC Closure, `40-testing.md`) — không coi AC "Implemented" nếu chỉ có unit test cô lập.
- **Không** viết integration test với DB thật cho ticket này trừ khi codebase đã có tiền lệ integration
  test riêng cho PM Dashboard (chưa xác nhận có tồn tại nhóm test này hay không — nếu cần, xác nhận
  trước khi thêm).

## Phương châm Rollout/Rollback

- **Light option (mặc định, khuyến nghị)**: deploy migration `V511` + code mới trong 1 lần release
  bình thường. Vì additive-only (bảng mới, endpoint mới, không sửa contract cũ), rollback = revert
  code deploy + (tùy chọn) `DROP TABLE tbl_fact_ai_finding_stat` nếu cần dọn dẹp — không có rủi ro dữ
  liệu cũ bị mất vì không bảng nào bị ALTER.
- **Heavy Option (đề xuất theo yêu cầu — có DB change + contract change)**:
  1. **Feature flag ở tầng FE**: bọc việc hiển thị component `AiFindingStatsCard` mới sau 1 flag cấu
     hình (ví dụ biến môi trường FE hoặc config đơn giản, không cần hệ thống feature-flag phức tạp) —
     cho phép merge code BE+FE trước, nhưng chỉ bật hiển thị trên UI sau khi xác nhận dữ liệu ghi §8
     đã chạy ổn định qua vài lần merge PR thật trong môi trường staging. Giảm rủi ro: nếu endpoint mới
     trả lỗi/dữ liệu sai, FE không hiển thị UI hỏng cho PM ngay lập tức.
  2. **Tách rời 2 lần deploy**: deploy migration `V511` + writer (ghi) trước, theo dõi log WARN của
     `AiFindingStatWriter` qua vài ngày để tích lũy dữ liệu và xác nhận không có lỗi ghi hàng loạt; sau
     đó mới deploy endpoint đọc + FE ở lần release kế tiếp. Lý do: tách rủi ro ghi (chạy trong luồng
     webhook production) khỏi rủi ro đọc/hiển thị (chạy khi PM mở dashboard) — nếu ghi có bug, phát
     hiện sớm hơn qua log trước khi PM nhìn thấy số liệu sai trên UI.
  3. **Endpoint mới độc lập, không thay endpoint cũ**: vì đây vốn đã là additive (không sửa
     `template-usage`), không cần versioning API kiểu `/v2/`; nếu cần rollback riêng endpoint mới,
     chỉ cần revert riêng phần route + controller, không ảnh hưởng các endpoint khác.
  4. **Rollback migration**: `V511` chỉ tạo bảng mới → rollback bằng cách không chạy migration tiếp
     theo phụ thuộc vào bảng này, và (nếu bắt buộc dọn dẹp) 1 migration `V512` riêng `DROP TABLE` —
     không dùng `flyway undo` (không có trong Flyway Community edition, cần xác nhận edition đang dùng
     nếu muốn chọn hướng này).
- Khuyến nghị chọn **Light option** trừ khi PM/Ops đánh giá rủi ro dữ liệu KPI sai hiển thị trực tiếp
  cho lãnh đạo là nghiêm trọng — khi đó dùng Heavy Option bước 1+2 (feature flag + tách deploy ghi/đọc).

## Gate trước implementation

Cập nhật 2026-08-19: Gate #1, #2, #3, #6 đã đóng (chi tiết resolution trong `open-issues.md` và
`impact-analysis.md`). Gate #5 chuyển thành "skipped theo yêu cầu người dùng, rủi ro chấp nhận có chủ
đích" — không còn là gate chặn. Gate #4 (MarkdownParserCore) đã đóng ngày 2026-08-19 — người dùng
xác nhận trực tiếp thiết kế positional/structural. **Không còn gate nào chặn implementation.**

1. **[CLOSED — 2026-08-19, OI-AIRKI-9]** Quyết định hành vi lỗi thiếu param bắt buộc: đã grep toàn bộ
   `web/` xác nhận 500 là convention nhất quán 100% của mọi API (11 handler trong
   `GlobalExceptionHandler`, không handler nào riêng cho `MissingServletRequestParameterException`).
   Quyết định: giữ nguyên 500, KHÔNG mở rộng phạm vi thêm handler 400.
2. **[CLOSED — 2026-08-19, OI-AIRKI-10]** Re-confirm `A-AIRKI-3` (thiết kế denominator dùng chung
   `ai_review_finding_total_count` cho 4/5 KPI): người dùng xác nhận giữ nguyên như đã quyết
   ("Cứ quyết như vậy đi") — nay ngang hàng Human Decision.
3. **[CLOSED — 2026-08-19, OI-AIRKI-12]** Đã đọc trực tiếp DDL gốc `tbl_dim_ticket`/`tbl_dim_project`/
   `tbl_dim_repository` từ `V4__init_shema_v2.sql` (dòng 107-193) — tên cột/kiểu `UUID` khớp 100%.
   `tbl_dim_ticket` không có `repository_id`, nhưng đã xác nhận `GithubWebhookService.handlePullRequest`
   có sẵn `repository.get().repositoryId()`/`.projectId()` trong scope — writer dùng trực tiếp, không
   cần join.
4. **[CLOSED — 2026-08-19]** Quyết định kỹ thuật cụ thể cho việc định vị §8 "ngôn ngữ độc lập" trong
   `MarkdownParserCore`: người dùng xác nhận trực tiếp — định vị **theo vị trí/thứ tự dòng và cột
   thật trong bảng của `ai-review.md`** (positional/structural mapping), không theo tên tiêu đề/nhãn
   đã dịch. Cụ thể: (a) tìm section bằng số thứ tự heading `8.` (regex, không match text tiêu đề); (b)
   trong bảng, mỗi dòng KPI lấy theo đúng thứ tự cố định (dòng 1 = KPI1, dòng 2 = KPI2, ... theo đúng
   thứ tự 7 dòng trong `_ticket-template/ai-review.md` hiện tại — xem `A-AIRKI-1`, `spec-pack.md`
   §16); (c) mỗi cột numerator/denominator lấy theo đúng vị trí cột cố định trong bảng, không match
   theo header text. Khớp hoàn toàn với `A-AIRKI-1` — không phải thiết kế mới, chỉ là xác nhận rõ
   ràng lại nguyên tắc đã ghi trong spec-pack. Không còn gate nào chặn implementation.
5. **[Skipped theo yêu cầu người dùng — 2026-08-19, OI-AIRKI-11 — rủi ro chấp nhận có chủ đích, không
   còn là gate]** Xác nhận `ArchitectureTest.java` có tồn tại hay không — người dùng chỉ đạo dừng xác
   minh thêm. Ghi nhận: có thể không có ArchUnit safety net cho layer boundary, cần review thủ công.
6. **[CLOSED — 2026-08-19, OI-AIRKI-13]** Xác nhận đường dẫn file thật của trang PM Dashboard chính
   (`PMDashboardPage.tsx`) và đọc `TemplateUsageByPhase.tsx` + `SummaryCards.tsx` trực tiếp — phát hiện
   `TemplateUsageByPhase.tsx` sai mẫu (render list), `SummaryCards.tsx` là mẫu tham chiếu đúng (grid từ
   1 object).
7. **[Non-blocking]** Xác nhận `formatUsageRate` trong `utils.ts` có tái dùng được nguyên trạng cho
   hình dạng dữ liệu mới hay cần viết hàm mới.
8. Re-check `db/migration/` ngay trước khi tạo file `V511` thật — không giả định `V510` vẫn là bản mới
   nhất tại thời điểm code (đã ghi chú tại `source-map.md`).

Tất cả các gate trên (trừ #8, chỉ là thao tác kỹ thuật tại thời điểm code) sẽ được đưa thành entry mới
trong `open-issues.md`.
