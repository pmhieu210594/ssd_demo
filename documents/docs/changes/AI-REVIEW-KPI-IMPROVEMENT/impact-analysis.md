# impact-analysis

**Ticket ID**: AI-REVIEW-KPI-IMPROVEMENT
**Create date**: 2026-08-18
**Author**: Principal Engineer (Claude)

Tài liệu này đọc dựa trên `spec-pack.md`, `open-issues.md`, `context.md`, `ticket-rules.md`,
`source-map.md`, toàn bộ `docs/architecture/*`, `docs/standards/*`, và source code thực tế đã xác
minh (không suy đoán). Mọi khẳng định "không ảnh hưởng" đều có căn cứ, không chỉ là giả định.

---

## Tổng quan thay đổi

Bổ sung 1 luồng ghi mới (webhook → parse `## 8. Số liệu thống kê` trong `ai-review.md` → upsert vào
bảng mới `tbl_fact_ai_finding_stat`, khóa theo `ticket_id`) và 1 luồng đọc mới (endpoint PM Dashboard
`GET /api/v1/pm/dashboard/ai-finding-stats?projectId=&repositoryId=` → trả về 1 object chứa 5 KPI rate
đã tổng hợp theo repository, hiển thị trên FE PM Dashboard). Không có API/DB/logic nào đã tồn tại bị
thay đổi hành vi — đây là bổ sung thuần túy (additive), không sửa `handlePullRequest` các nhánh khác,
không sửa `findTemplateUsage` hay bất kỳ endpoint PM Dashboard hiện có nào.

Phạm vi thay đổi vật lý: 1 bảng DB mới, 2 file BE mới (writer + adapter ghi), method mới thêm vào 4
file BE hiện có (port đọc, adapter đọc, model, DTO, service, controller — xem "Danh sách file thay
đổi" trong `impl-plan.md`), 1 migration mới, method mới trong `api.ts` (FE), 1 component mới (FE), và
compose component đó vào trang PM Dashboard chính.

---

## Ảnh hưởng trực tiếp

- `GithubWebhookService.handlePullRequest` — thêm 1 lời gọi writer mới, chèn giữa dòng ~296 (kết thúc
  vòng lặp build `ticketScopes`) và dòng ~298 (`if (!shouldTriggerScan(action))`). Bắt buộc đúng vị
  trí này vì writer cần `ticketScopes.get(ticketKey).ticketId()` (UUID) làm khóa upsert — giá trị này
  chỉ tồn tại sau khi vòng lặp hoàn tất, và phải nằm trước early-return vì `action == "closed"` luôn
  làm `shouldTriggerScan` trả `false`.
- `PmDashboardRepositoryPort` + `PmDashboardJdbcAdapter` — thêm 1 method đọc mới (`findAiFindingStats`
  hoặc tên tương đương), theo đúng mẫu `findTemplateUsage` (dùng `NamedParameterJdbcTemplate` + text
  block SQL).
- `PmDashboardModels`, `PmDashboardDtos` — thêm record/DTO mới + `from(...)`.
- `PmDashboardService` — thêm service method mới, gọi `requirePm(caller, projectId)` trước khi đọc.
- `PmDashboardController` — thêm endpoint mới.
- DB: bảng mới `tbl_fact_ai_finding_stat` (migration `V512__add_ai_finding_stat_tracking.sql`, đổi từ `V511` ngày 2026-08-20 do va version sau merge `main`), không sửa bảng nào hiện có.
- FE: `api.ts` (method mới trong object `pmDashboard`), component mới, compose vào trang PM Dashboard
  chính.

## Ảnh hưởng gián tiếp

- **`MarkdownParserCore`**: không sửa file, nhưng logic đọc mới sẽ **dùng** `parse()`/`.tables()`/
  `.sections()`/`.sectionMap()` của module này để định vị §8. Rủi ro gián tiếp: `canonicalSectionKey`
  là switch cứng theo ngôn ngữ (hiện chỉ tiếng Anh) và số thứ tự heading ("8.") bị `stripNumericPrefix`
  xóa khỏi `MarkdownSection.title()` trước khi lưu — nên **không thể** dựa vào so khớp text đã dịch để
  đạt yêu cầu "ngôn ngữ độc lập" của business. Đây là ảnh hưởng gián tiếp vì bug tồn tại sẵn trong
  `MarkdownParserCore` không do ticket này gây ra, nhưng thiết kế logic đọc §8 phải né tránh nó (dùng
  định vị theo cấu trúc/thứ tự bảng, không theo tên tiêu đề) — xem "Điểm chưa rõ" bên dưới.
- **`ArtifactScannerSourcePort`**: không sửa file, nhưng logic ghi mới phụ thuộc trực tiếp vào chuỗi
  `resolveRevision → listTree → readBlob` (đã xác nhận đủ 3 method, `readBlob` trả `byte[]`) để lấy
  nội dung `ai-review.md` từ Git blob tại thời điểm PR merge.
- **Toàn bộ 33 test case hiện có của `GithubWebhookServiceTest.java`**: không sửa logic các test này,
  nhưng thêm code mới vào `handlePullRequest` có rủi ro gián tiếp phá vỡ verify/mock hiện có nếu writer
  mới không được mock đúng cách trong các test không liên quan tới §8 — bắt buộc rà lại toàn bộ 33 test
  trước khi thêm code (đã là MUST rule #15 trong `ticket-rules.md`).
- **`GlobalExceptionHandler`**: không sửa file, nhưng endpoint mới kế thừa hành vi lỗi hiện tại của nó
  — bao gồm cả một khiếm khuyết có sẵn (xem "Ảnh hưởng BE/API").
- **PM Dashboard page chính (FE)**: ảnh hưởng gián tiếp về bố cục khi thêm 1 component mới. **[Đã xác
  nhận 2026-08-19]** đường dẫn thật: `EDCAP_FE/src/pages/pm-dashboard/PMDashboardPage.tsx` (đọc trực
  tiếp, khớp suy đoán ban đầu) — xem "Ảnh hưởng FE".

## Ảnh hưởng FE

- Thêm method mới vào object `pmDashboard` (truy cập qua `endpoints.pmDashboard` trong FE, export từ
  `EDCAP_FE/src/lib/api.ts`, cạnh `templateUsage`, gần dòng 1857) — tuân thủ rule `20-architecture.md`
  "All API calls must go through `lib/api.ts`".
- Thêm component mới hiển thị 5 KPI (đề xuất tên `AiFindingStatsCard.tsx`). **[Sửa lại đánh giá trước
  đó, đã đọc cả 2 file trực tiếp 2026-08-19]**: `TemplateUsageByPhase.tsx` **không phải** mẫu phù
  hợp — nó nhận props `rows: TemplateUsageByPhaseRow[]` và render `CDataTable` (bảng danh sách hàng),
  trong khi endpoint mới trả **1 object duy nhất** (theo OI-AIRKI-2). Mẫu phù hợp hơn là
  `SummaryCards.tsx` (nhận 1 object `PmDashboardSummary | undefined`, render lưới thẻ KPI đơn giá trị
  dạng `{title, description, value}`, mỗi thẻ có accent-bar màu theo mức độ) — component mới nên theo
  đúng cấu trúc thẻ-KPI-đơn-giá-trị này, không theo bảng dữ liệu của `TemplateUsageByPhase`.
  Vị trí compose: `PMDashboardPage.tsx`, thêm 1 `useQuery` mới theo đúng pattern của
  `templateUsageQuery` hiện có (`queryKey: ["pm-dashboard", "ai-finding-stats", filters.projectId,
  filters.repositoryId]`, `enabled: Boolean(filters.projectId) && Boolean(filters.repositoryId)`),
  render component mới ngay cạnh `<SummaryCards summary={summary} />` hoặc dưới
  `<TemplateUsageByPhase>` (thứ tự chính xác là quyết định UI, không ảnh hưởng logic).
- FE format hiển thị: khi rate `null` → hiển thị `-` (đã xác nhận qua OI-AIRKI-7 + test
  `templateUsage_returnsDtoListWithComputedUsageRate` cho hành vi field vắng mặt trong JSON khi
  `Optional`/rate null — cần xác nhận `formatUsageRate` trong `utils.ts` có tái dùng được nguyên trạng
  hay cần hàm mới, tùy hình dạng field JSON có giống `template-usage` không).
- Rule "AC Closure" (`40-testing.md`) áp dụng: component mới **phải** được mount trên trang thật, một
  unit test cô lập không đủ để đóng AC.
- Không ảnh hưởng tới bất kỳ trang FE nào khác ngoài PM Dashboard — không có route mới, không sửa
  routing (`useLanguage`/`:lang` segment không bị động tới).

## Ảnh hưởng BE/API

- **Endpoint mới**: `GET /api/v1/pm/dashboard/ai-finding-stats?projectId={UUID}&repositoryId={UUID}`,
  cả hai param **bắt buộc** (không `required = false`) theo quyết định OI-AIRKI-2. Trả về **1 object
  duy nhất** (không phải `List`), khác với các endpoint dạng list khác trong `PmDashboardController`.
- **Permission gate**: dùng lại `PmDashboardService.requirePm(caller, projectId)` y hệt
  `getTemplateUsage` — không cần logic phân quyền mới, không ảnh hưởng tới các gate khác.
- **Khiếm khuyết có sẵn, đã quyết định giữ nguyên (OI-AIRKI-9, 2026-08-19)**:
  `GlobalExceptionHandler.java` (đọc toàn bộ 111 dòng) **không có** `@ExceptionHandler` cho
  `MissingServletRequestParameterException`. Khi thiếu `repositoryId`/`projectId` bắt buộc, Spring
  ném exception này, rơi xuống catch-all `Exception.class` → trả **HTTP 500 `INTERNAL_ERROR`**, không
  phải HTTP 400 như `spec-pack.md` §10 kỳ vọng ngầm định. **Đã grep toàn bộ `web/`** — xác nhận đây
  không phải riêng lỗi của `template-usage` mà là convention nhất quán 100% của toàn bộ codebase:
  không một endpoint nào (kể cả các endpoint khác của `PmDashboardController`) có handler riêng cho
  trường hợp này. Theo chỉ đạo của người dùng ("Các api khác đang check như nào thì hãy check giống
  như vậy"), endpoint mới **giữ nguyên hành vi 500 hiện tại**, không thêm `@ExceptionHandler` mới,
  không mở rộng `GlobalExceptionHandler` — nhất quán tuyệt đối với mọi API khác trong hệ thống.
- **`ErrorResponse` — đã xác minh bằng cách đọc trực tiếp source**
  (`web/exception/ErrorResponse.java`): shape thật là
  **`(timestamp, status, error, message, traceId)` — 5 field**, trong đó `error` là mã lỗi ngắn (ví
  dụ `"FORBIDDEN"`, `"INTERNAL_ERROR"`), không phải cụm từ HTTP phrase riêng biệt. Rule
  `.claude/rules/30-security.md` mô tả field thứ 3 là `errorCode` (khác tên field thật `error`, nhưng
  cùng là 5 field) — chỉ là khác biệt tên gọi trong tài liệu, không phải khác biệt số lượng field. Một
  số standards khác (`api-contract.md`, `error-handling.md`) mô tả shape 6 field
  (`timestamp, status, error, errorCode, message, traceId`) — đây là tài liệu **không khớp với source
  thật**, không áp dụng cho ticket này. Endpoint mới sẽ trả lỗi theo đúng shape 5-field thật của
  `GlobalExceptionHandler`, không theo tài liệu 6-field.
- Không ảnh hưởng tới bất kỳ endpoint PM Dashboard nào khác (`summary`, `insights`, `tickets`,
  `template-usage`, `access`, ...) — endpoint mới độc lập, không sửa logic chung.
- Không ảnh hưởng tới xác thực webhook (HMAC-SHA256) — điểm chèn logic mới nằm sau bước xác thực chữ
  ký đã có, không thay đổi guard này.

## Ảnh hưởng DTO/Schema/Validation

- DTO mới trong `PmDashboardDtos` (record + `from(...)` tĩnh) chứa: 5 rate (`Double`, nullable) +
  `repositoryId` + **`repositoryName`** (trường mới cần xác nhận: xác nhận qua `spec-pack.md` §9 JSON
  mẫu — cần JOIN `tbl_dim_repository` để lấy `repo_name_masked AS repository_name`, theo đúng alias
  pattern đã dùng tại `PmDashboardJdbcAdapter.java` dòng 88 cho danh sách filter options).
- Công thức tính rate: `denominator == 0 ? null : Math.round(numerator * 1000.0 / denominator) / 10.0`
  — SUM-trước-rồi-chia trên toàn bộ ticket trong scope, không phải trung bình cộng rate từng ticket
  (MUST rule #8). Rate null → JSON field vắng mặt (theo hành vi Jackson đã quan sát qua test
  `templateUsage_returnsDtoListWithComputedUsageRate`, cơ chế gốc — global `ObjectMapper` config hay
  annotation trên DTO — chưa xác minh độc lập, chỉ tái dùng hành vi quan sát được, không tự suy diễn
  cấu hình).
- Validation: không cần thêm validation logic mới ngoài Spring's built-in required-param check (vốn
  đã tồn tại pattern này ở `template-usage`) — không dùng Bean Validation annotation mới vì codebase
  hiện tại không dùng pattern đó cho query param ở tầng PM Dashboard.
- Không ảnh hưởng schema của bất kỳ DTO nào khác.

## Ảnh hưởng DB/Migration

- Bảng mới `tbl_fact_ai_finding_stat`, DDL đã chốt đầy đủ tại `spec-pack.md` §8 (tên cột, kiểu dữ
  liệu, PK/FK, index, tên migration `V512__add_ai_finding_stat_tracking.sql`, đổi từ `V511` ngày
  2026-08-20) — **không được tái phát
  minh DDL trong impl-plan**, chỉ trích dẫn.
- Khóa theo `ticket_id` (PK/unique), FK về `tbl_dim_ticket` — khác cấu trúc khóa
  `(project_id, repository_id, phase_id)` của `tbl_fact_template_usage_stat` (per-phase-aggregate) vì
  đây là bảng per-ticket.
- Cột `project_id`, `repository_id` denormalize trực tiếp vào bảng fact (không chỉ suy ra qua JOIN
  `tbl_dim_ticket`) — cần composite index `(project_id, repository_id)` để truy vấn đọc aggregate hiệu
  quả (đã có trong DDL §8).
- Không ALTER bảng nào hiện có — bổ sung thuần túy 1 bảng mới.
- **[Đã đọc trực tiếp, OI-AIRKI-12, 2026-08-19]**: DDL gốc của `tbl_dim_project`, `tbl_dim_repository`,
  `tbl_dim_ticket` đã đọc trực tiếp từ `V4__init_shema_v2.sql` (dòng 107-193), xác nhận:
  `tbl_dim_project.project_id UUID PRIMARY KEY`, `tbl_dim_repository.repository_id UUID PRIMARY KEY`
  + FK `project_id UUID NOT NULL REFERENCES tbl_dim_project(project_id)`,
  `tbl_dim_ticket.ticket_id UUID PRIMARY KEY` + FK `project_id UUID NOT NULL REFERENCES
  tbl_dim_project(project_id)`. Tên cột và kiểu `UUID` khớp 100% với giả định trong DDL migration mới
  — không còn suy luận gián tiếp. **Phát hiện quan trọng**: `tbl_dim_ticket` **không có cột
  `repository_id`** (chỉ có `project_id`) — nếu viết mới phải suy ra `repository_id` bằng JOIN thì sẽ
  không có đường nối trực tiếp. Tuy nhiên đã grep `GithubWebhookService.java` xác nhận
  `repository.get().repositoryId()` và `repository.get().projectId()` đã được resolve sẵn và nằm
  trong scope xuyên suốt `handlePullRequest` (dùng tại các dòng 183, 210, 255, 309, 359, 397, và tham
  số method tại dòng 495) — writer mới lấy `project_id`/`repository_id` trực tiếp từ context webhook
  đã resolve sẵn này, KHÔNG cần join qua `tbl_dim_ticket`. Do đó việc thiếu `repository_id` trên
  `tbl_dim_ticket` không phải là gap chặn — không còn điểm chưa rõ ở mục này.
- **`A-AIRKI-3` — đã xác nhận (OI-AIRKI-10, 2026-08-19)**: giả định thiết kế cột
  `ai_review_finding_total_count` là mẫu số DÙNG CHUNG cho 4/5 KPI (adoption/valid/false-positive/
  resolution rate), trong khi `blocker_major_total_count` có mẫu số riêng, đã được người dùng xác
  nhận giữ nguyên như đã quyết ("Cứ quyết như vậy đi"). Đây nay là quyết định đã chốt, ngang hàng với
  H-AIRKI-1..8, không còn là suy đoán AI chưa xác nhận. DDL migration `V512` (đổi từ `V511`) dùng số lượng cột
  denominator theo đúng giả định này.

## Ảnh hưởng Batch/Event/External IF

- Không có batch job nào bị ảnh hưởng — logic ghi mới chạy đồng bộ trong webhook handler
  (`handlePullRequest`), không tạo job định kỳ mới.
- Không ảnh hưởng tới GitHub App webhook event subscription hiện có — dùng lại đúng event
  `pull_request`/action `closed` đã được xử lý, không đăng ký event type mới.
- Không ảnh hưởng tới tích hợp Jira/CircleCI — ticket này chỉ đọc nội dung file `ai-review.md` trong
  chính repository Git đã được PR trỏ tới, qua `ArtifactScannerSourcePort` đã có sẵn.
- OI-AIRKI-8 đã chốt: không backfill dữ liệu ticket cũ — nghĩa là không có batch job một lần
  (one-off backfill script) nào cần viết cho ticket này.

## Ảnh hưởng Test

- **BE**: test mới cho writer + adapter ghi (`src/test/UnitTest/java/...`, theo class đang mở rộng,
  không tự chọn source root), mở rộng `GithubWebhookServiceTest.java`
  (`src/test/java/...` — source root khác, theo đúng vị trí class `GithubWebhookService`), test mới
  cho service method + controller endpoint tại `PmDashboardServiceTest.java`/
  `PmDashboardControllerTest.java` (đã đọc toàn bộ, đã xác nhận style test: `@Mock`/
  `MockitoExtension` cho Service, `Mockito.mock(...)` thủ công + `MockMvcBuilders.standaloneSetup(...)`
  cho Controller — KHÔNG dùng `@WebMvcTest`/`@MockBean` dù đó là ví dụ generic trong
  `docs/standards/testing.md`).
- Bắt buộc đọc toàn bộ 33 test case hiện có trong `GithubWebhookServiceTest.java` trước khi thêm test
  mới (MUST rule #15) — rủi ro nếu bỏ qua: phá vỡ mock verify hiện có do thêm dependency mới vào
  constructor `GithubWebhookService` mà không cập nhật test setup tương ứng.
- **[Rủi ro được chấp nhận có chủ đích, OI-AIRKI-11, 2026-08-19 — không tiếp tục xác minh]**:
  `docs/standards/testing.md` khẳng định ArchUnit `ArchitectureTest` "Confirmed" tồn tại và enforce
  hexagonal layer rules, nhưng glob `**/ArchitectureTest.java` và `**/*ArchitectureTest*` trong 2
  phiên nghiên cứu đều **không tìm thấy file nào**. Theo chỉ đạo của người dùng, việc xác minh thêm
  bị dừng lại ("skip") — đây không phải là đã giải quyết, mà là một rủi ro được chấp nhận có chủ đích:
  code mới có thể không có safety net ArchUnit tự động để bắt lỗi vi phạm layer boundary; việc tuân
  thủ hexagonal layer cho code mới sẽ dựa vào review thủ công thay vì test tự động.
- FE: cần test mới cho component (`AiFindingStatsCard` hoặc tương đương) + xác nhận mount trên trang
  thật theo rule "AC Closure" — không chỉ unit test cô lập.

## Ảnh hưởng Operation/Monitoring

- Log: lỗi ghi vào `tbl_fact_ai_finding_stat` (nếu có `DataAccessException`) log ở mức WARN, không
  rethrow — theo đúng mẫu xử lý lỗi của `validateTemplateUsage`/`templateUsageStatWriter` hiện có,
  nghĩa là **PR merge không bao giờ fail vì lỗi ghi §8** — hành vi "best-effort, không chặn luồng
  chính" được kế thừa nguyên trạng, không phải quyết định mới của ticket này.
  Cần tuân thủ `docs/standards/logging.md`/rule `30-security.md` "Log/Audit Sanitization" nếu message
  exception có khả năng chứa nội dung nhạy cảm (không có trong ticket này, vì nội dung chỉ là dữ liệu
  parse từ `ai-review.md`, không phải credential).
- Không có thay đổi nào tới cấu hình `AppProperties.java` (đã đọc toàn bộ 45 dòng, xác nhận không có
  group config liên quan) — không cần thêm property mới trừ khi impl-plan quyết định khác.
- Không ảnh hưởng dashboard/alerting hiện có bên ngoài phạm vi PM Dashboard — không có metric/APM mới
  nào được yêu cầu bởi spec.
- Rủi ro vận hành duy nhất đã biết: endpoint mới trả 500 (thay vì 400 kỳ vọng) khi thiếu param bắt
  buộc — xem "Ảnh hưởng BE/API" — có thể gây nhiễu log lỗi 500 nếu FE gọi sai param, dù không phải lỗi
  hệ thống thật.

## Ảnh hưởng Rollout/Rollback

- Migration mới (`V512`, đổi từ `V511` ngày 2026-08-20) là **additive-only** (tạo bảng mới, không ALTER bảng cũ) — rollback đơn giản
  bằng cách `DROP TABLE tbl_fact_ai_finding_stat` nếu cần lùi, không ảnh hưởng dữ liệu hiện có của bảng
  khác.
- Endpoint mới hoàn toàn mới (path mới), không thay đổi contract endpoint cũ — rollback BE = revert
  code deploy, không cần dữ liệu migration ngược đặc biệt nào ngoài việc bảng mới có thể để trống
  (không xóa) an toàn.
- Ghi §8 là best-effort (catch, log, không rethrow) — nếu logic ghi có bug, PR merge vẫn thành công
  bình thường, chỉ thiếu dữ liệu KPI cho ticket đó — rủi ro rollback thấp cho luồng ghi.
- Không backfill (OI-AIRKI-8) — nghĩa là sau khi rollout, dữ liệu KPI chỉ có từ ticket merge **sau**
  thời điểm deploy; đây là gap đã được PM/Ops chấp nhận, không phải rủi ro cần xử lý thêm trong
  rollout plan.
- Xem `impl-plan.md` mục "Phương châm Rollout/Rollback" cho đề xuất Heavy Option cụ thể (do có DB
  change + contract change mới).

## Vùng được phán định là không ảnh hưởng

Mỗi mục dưới đây đã được kiểm tra trực tiếp (đọc source hoặc grep xác nhận), không chỉ giả định:

- **Webhook HMAC signature validation**: không sửa — điểm chèn logic mới nằm sau bước xác thực chữ ký
  trong `handlePullRequest`, không đụng tới guard này. Căn cứ: đọc trực tiếp `handlePullRequest`, xác
  nhận vị trí chèn (~dòng 296-298) nằm sau các bước xác thực đầu hàm.
- **Owner/Author pseudonym rule** (nếu có trong codebase cho các bảng fact khác): không áp dụng — bảng
  mới không có cột liên quan tới tên người dùng cần pseudonymize; chỉ có `ticket_id`, `project_id`,
  `repository_id`, và các cột đếm số nguyên.
- **Append-Only trigger pattern** (dùng cho audit trail ở một số bảng khác như `V509`): không áp dụng —
  OI-AIRKI-4 đã chốt dùng schema tối giản kiểu `V510` (chỉ `created_at`/`updated_at`), không cần
  audit/soft-delete/append-only trigger.
- **MyBatis XML mapper convention**: không áp dụng — mọi adapter PM Dashboard/Template Usage hiện có
  đều dùng JDBC thuần qua `NamedParameterJdbcTemplate`, không dùng MyBatis XML; bảng mới đi theo đúng
  convention đó (MUST NOT rule #6 trong `ticket-rules.md`), dù `docs/architecture/overview.md` liệt kê
  MyBatis là công nghệ chung của BE.
- **JSONB/soft-delete/partitioning candidate rules**: không áp dụng — bảng mới không có cột JSON, có
  schema tối giản không soft-delete, và khối lượng dữ liệu per-ticket không đạt ngưỡng cần partition
  (căn cứ: `spec-pack.md` §8 DDL không có cột nào thuộc các loại này).
- **`formItemNm`/`SEQNO`/full-width-half-width/bảo toàn tiếng Nhật không Unicode-hóa**: đã grep toàn bộ
  `EDCAP_BE/src`/`EDCAP_FE/src` trong phiên Tech Lead trước, xác nhận các khái niệm này không tồn tại
  trong codebase — là boilerplate template dùng chung nhiều dự án, không đặc thù EDCAP, không áp dụng.
- **Các endpoint PM Dashboard khác** (`summary`, `insights`, `tickets`, `template-usage`, `access`):
  không sửa — endpoint mới độc lập hoàn toàn, không share logic ngoài `requirePm` (dùng lại nguyên
  trạng, không sửa method này).
- **Routing/i18n FE** (`:lang` segment, `useLanguage`): không ảnh hưởng — không có route mới, chỉ thêm
  component con trong trang PM Dashboard hiện có.
- **CircleCI/Jira integration**: không ảnh hưởng — logic mới chỉ đọc blob Git của repository đã được
  PR trỏ tới, không gọi thêm external system nào khác ngoài GitHub (đã có sẵn qua
  `ArtifactScannerSourcePort`).

## Điểm chưa rõ

Cập nhật 2026-08-19: OI-AIRKI-9, 10, 12, 13 và Gate #4 (`MarkdownParserCore`) đã resolved,
OI-AIRKI-11 đã skip theo yêu cầu người dùng — chi tiết resolution log tại `open-issues.md`. Không còn
điểm nào chặn implementation.

1. **[Đã resolved — OI-AIRKI-9, 2026-08-19]** `GlobalExceptionHandler` không có handler cho
   `MissingServletRequestParameterException` → thiếu `repositoryId`/`projectId` trả HTTP 500 thay vì
   400. Đã grep toàn bộ `web/` xác nhận đây là convention nhất quán 100% của mọi API, không riêng
   `template-usage`. Quyết định: chấp nhận hành vi 500 hiện tại cho endpoint mới, không thêm handler
   mới, nhất quán với "các API khác đang check như nào" theo chỉ đạo người dùng.
2. **[Đã resolved — OI-AIRKI-10, 2026-08-19]** `A-AIRKI-3` (cột `ai_review_finding_total_count` là
   mẫu số dùng chung cho 4/5 KPI) — người dùng xác nhận giữ nguyên quyết định như đã đề xuất ("Cứ
   quyết như vậy đi"). Nay là quyết định đã chốt, ngang hàng H-AIRKI-1..8.
3. **[Skipped theo yêu cầu người dùng — OI-AIRKI-11, 2026-08-19 — rủi ro được chấp nhận có chủ đích]**
   Chưa xác nhận được `ArchitectureTest.java` có thực sự tồn tại trong codebase hay không (glob 2 lần
   đều không ra kết quả). Người dùng chỉ đạo dừng xác minh thêm ("skip"). Không coi là đã giải quyết —
   là rủi ro chấp nhận có chủ đích: code mới có thể thiếu safety net ArchUnit tự động, cần review thủ
   công layer boundary thay thế.
4. **[Đã resolved — OI-AIRKI-12, 2026-08-19]** DDL gốc của `tbl_dim_ticket`/`tbl_dim_project`/
   `tbl_dim_repository` đã đọc trực tiếp từ `V4__init_shema_v2.sql` — tên cột/kiểu `UUID` khớp 100%.
   Phát hiện `tbl_dim_ticket` không có cột `repository_id`, nhưng đã xác nhận
   `GithubWebhookService.handlePullRequest` đã có sẵn `repository.get().repositoryId()`/`.projectId()`
   trong scope tại điểm chèn hook — writer mới dùng trực tiếp, không cần join qua `tbl_dim_ticket`.
5. **[Đã resolved — 2026-08-19, xác nhận trực tiếp bởi người dùng]** `MarkdownParserCore.canonicalSectionKey`
   là switch cứng theo ngôn ngữ (chỉ hỗ trợ tiếng Anh hiện tại). Người dùng xác nhận trực tiếp thiết kế:
   định vị heading theo số thứ tự `8.` (không theo tên tiêu đề dịch), trích từng dòng KPI theo đúng
   **vị trí/thứ tự dòng và cột thật trong bảng** của `ai-review.md` (positional/structural mapping),
   không so khớp theo nhãn/label text — khớp hoàn toàn với `A-AIRKI-1` (`spec-pack.md` §16). Gate #4
   trong `impl-plan.md` đã đóng.
6. **[Đã resolved — OI-AIRKI-13, 2026-08-19]** Đường dẫn file thật của trang PM Dashboard chính (FE)
   xác nhận là `EDCAP_FE/src/pages/pm-dashboard/PMDashboardPage.tsx` (đọc toàn bộ 259 dòng).
7. **[Đã resolved — OI-AIRKI-13, 2026-08-19]** Đã đọc `TemplateUsageByPhase.tsx` (95 dòng) và
   `SummaryCards.tsx` (152 dòng) trực tiếp. Phát hiện quan trọng: `TemplateUsageByPhase.tsx` render
   list qua `CDataTable` — SAI mẫu cho response 1 object. `SummaryCards.tsx` (grid KPI card từ 1
   object) mới là mẫu tham chiếu đúng — đã sửa lại trong "Ảnh hưởng FE" ở trên.

Tất cả 7 mục đã resolved hoặc skip có chủ đích (mục 3). Không còn điểm nào chặn implementation.
