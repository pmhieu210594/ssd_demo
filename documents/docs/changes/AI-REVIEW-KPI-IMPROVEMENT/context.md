# context.md

**Ticket ID**: AI-REVIEW-KPI-IMPROVEMENT
**Create date**: 2026-08-18
**Author**: Tech Lead (Claude) — chuẩn bị context cho AI implementer, đọc-only, không sửa `EDCAP_BE/src` hoặc `EDCAP_FE/src`.

Tài liệu này tổng hợp bằng chứng đã xác minh trực tiếp trong source code (không suy diễn) để AI
implement `impl-plan.md` không hiểu sai cách làm hiện có của project, không gọi API không tồn tại,
và không copy nhầm pattern bị cấm. Xem thêm quyết định nghiệp vụ đầy đủ tại `spec-pack.md`
(§7-§13, §18) và `open-issues.md` (Resolution Log).

---

## File đã đọc

Đọc **toàn bộ nội dung** (không phải trích đoạn), trừ khi ghi chú khác:

**Target file (sẽ sửa)**:
- `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/ingestion/GithubWebhookService.java` (608 dòng, đọc toàn bộ)

**Implementation tương tự (đọc toàn bộ, dùng làm khuôn mẫu)**:
- `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/ingestion/TemplateUsageStatWriter.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/persistence/TemplateUsageStatPort.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/TemplateUsageStatJdbcAdapter.java`
- `EDCAP_BE/src/main/resources/db/migration/V510__add_template_usage_tracking.sql`
- `EDCAP_BE/src/main/resources/db/migration/V509__ticket_bug_metrics.sql` (đối chứng, KHÔNG theo pattern này)
- `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/pmdashboard/PmDashboardModels.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/pmdashboard/PmDashboardService.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/persistence/PmDashboardRepositoryPort.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/PmDashboardJdbcAdapter.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/web/dto/PmDashboardDtos.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/web/rest/PmDashboardController.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/domain/service/markdown/core/MarkdownParserCore.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/scanner/ArtifactScannerModels.java`
- `EDCAP_FE/src/lib/api.ts` (đoạn `templateUsage`, dòng ~1857-1864)
- `EDCAP_FE/src/pages/pm-dashboard/components/TemplateUsageByPhase.tsx`
- `EDCAP_FE/src/pages/pm-dashboard/utils.ts` (hàm `formatUsageRate`)

**Test hiện có (đọc bằng grep cấu trúc test + đọc toàn bộ thân 1 test case trọng yếu)**:
- `EDCAP_BE/src/test/java/com/sdd/platform/application/usecase/ingestion/GithubWebhookServiceTest.java`
  — grep toàn bộ danh sách 33 `@Test` (tên method + các dòng `verify(...)`/`when(...)` chính) +
  đọc toàn bộ thân test `pull_request_closed_merged_event_updates_status_without_scanning` (dòng
  ~563-601) vì đây là test case đúng luồng "merged PR" mà tính năng mới phải chạy trong đó.
- `EDCAP_BE/src/test/UnitTest/java/com/sdd/platform/infrastructure/persistence/adapter/PmDashboardJdbcAdapterFindTemplateUsageTest.java` (đọc toàn bộ, 94 dòng)

**Tìm kiếm xác minh (không tìm thấy áp dụng cho ticket này — xem mục cuối "Rule nghiệp vụ đặc thù")**:
- Grep toàn repo `formItemNm|SEQNO|full-width|half-width|全角|半角` — 0 kết quả trong source code
  `EDCAP_BE/src` hoặc `EDCAP_FE/src`; các kết quả duy nhất nằm trong tài liệu SDD phương pháp luận
  chung (`SDD-Installation Pack_V04.2/*.md`), không liên quan tới codebase hay ticket này.
- Grep FE `templateUsage` trong file `*.json` (locale) — 0 kết quả: không có file locale JSON nào
  chứa key `Pages.PmDashboard.templateUsage.*`; các key này chỉ tồn tại inline qua `t(key, {defaultValue})`.

---

## Implementation tương tự

Tính năng gần nhất về hình dạng kỹ thuật là **PROMPT_TEMPLATE_REUSE_RATE / Template Usage
tracking** (đã xong, đang chạy production). Đây là mẫu tham chiếu chính cho toàn bộ luồng
BE (webhook hook → writer bean riêng → JDBC upsert → PM Dashboard port/service/controller/DTO →
FE table + `formatUsageRate`), vì nó cùng chung:
- Cùng file trigger (`GithubWebhookService.handlePullRequest`, `action == "closed"` + `merged == true`).
- Cùng đích hiển thị (PM Dashboard, theo `projectId` + `repositoryId`).
- Cùng nhu cầu tránh double-count khi GitHub redeliver webhook (idempotency).
- Cùng kiểu response rate `null` → FE hiển thị `-`.

**Khác biệt quan trọng KHÔNG được copy y nguyên** (chi tiết ở "Pattern cấm dùng"):
1. Template Usage là **counter cộng dồn theo sự kiện** (mỗi lần kiểm tra +1); AI Finding Stat là
   **snapshot ghi đè theo ticket** (mỗi lần merge ghi lại số liệu mới nhất đọc được từ `ai-review.md`
   tại thời điểm đó — không cộng dồn qua nhiều lần merge).
2. Template Usage response là **1 danh sách nhiều dòng** (1 dòng/phase); AI Finding Stat response
   là **1 object duy nhất** theo quyết định OI-AIRKI-2 (không phải danh sách).
3. Template Usage aggregate theo `(project_id, repository_id, phase_id)`; AI Finding Stat lưu thô
   theo `ticket_id` (1 dòng/ticket) rồi **aggregate SUM ở tầng query** theo `repositoryId` khi trả
   API — khác cấu trúc lưu trữ, không phải khác chỉ ở cách đọc.

---

## Pattern nên dùng

1. **Writer là `@Component` riêng, không phải method trong `GithubWebhookService`.**
   `TemplateUsageStatWriter` (32 dòng) tồn tại như một bean tách biệt CHỈ để
   `@Transactional(propagation = Propagation.REQUIRES_NEW)` có hiệu lực thật — Javadoc của chính
   file này nêu rõ lý do: Spring AOP proxy chỉ chặn được method call đi **qua bean khác**; nếu gọi
   một method `@Transactional` ngay trên `this` từ bên trong `GithubWebhookService` thì proxy bị
   bỏ qua và annotation **không có tác dụng gì** (self-invocation limitation của Spring AOP).
   → Tính năng mới PHẢI tạo một `@Component` riêng (ví dụ `AiFindingStatWriter`) với method
   `@Transactional(propagation = Propagation.REQUIRES_NEW)`, được constructor-inject vào
   `GithubWebhookService` như một field mới, gọi từ `handlePullRequest`. Không thêm method
   `@Transactional` trực tiếp vào `GithubWebhookService`.

2. **Ghi log lỗi, không rethrow, khi write DB thất bại.** `validateTemplateUsage` bọc lời gọi
   `templateUsageStatWriter.recordIndependently(...)` trong try/catch bắt `DataAccessException`,
   log WARN, KHÔNG rethrow — để một lỗi ghi DB không làm hỏng phần còn lại của webhook response.
   Test `template_usage_continues_processing_remaining_files_when_counter_write_fails` xác nhận
   hành vi này (ghi tiếp file thứ 2 dù file thứ 1 lỗi). → Áp dụng y hệt pattern try/catch này khi
   gọi writer mới.

3. **Port interface tối giản, 1 method, đặt tên theo hành động nghiệp vụ.**
   `TemplateUsageStatPort` chỉ có `void recordCheck(UUID projectId, UUID repositoryId, UUID phaseId, boolean matched);`
   — không thêm getter/finder vào cùng port trừ khi PM Dashboard cần đọc lại (đọc lại nên đi qua
   `PmDashboardRepositoryPort`, xem điểm 6).

4. **JDBC raw SQL qua `NamedParameterJdbcTemplate` + `MapSqlParameterSource` + text block.**
   `TemplateUsageStatJdbcAdapter` dùng constructor injection của `NamedParameterJdbcTemplate`, SQL
   viết bằng Java text block (`"""`), tham số qua `:paramName`. Toàn bộ adapter trong project (kể cả
   `PmDashboardJdbcAdapter`) đi theo pattern này — không dùng MyBatis XML cho các bảng fact loại này
   (MyBatis XML chỉ thấy ở `TicketBugMetricsMapper.xml`, một module khác).

5. **Upsert bằng `INSERT ... ON CONFLICT (...) DO UPDATE SET col = EXCLUDED.col`** — đây là cú
   pháp Postgres đúng cho snapshot-overwrite. Ví dụ cần viết mới hoàn toàn (không có sẵn để copy
   1:1 vì `TemplateUsageStatJdbcAdapter` dùng biến thể INCREMENT — xem "Pattern cấm dùng" #1).

6. **PM Dashboard đọc dữ liệu qua `PmDashboardRepositoryPort` + `PmDashboardJdbcAdapter`, KHÔNG
   qua port ghi.** `findTemplateUsage(UUID projectId, UUID repositoryId)` là ví dụ chuẩn: thêm 1
   method mới vào `PmDashboardRepositoryPort` (ví dụ `findAiFindingStats(UUID repositoryId)`),
   implement trong `PmDashboardJdbcAdapter`, expose qua `PmDashboardService`
   (bọc bằng `@Transactional(readOnly = true)` + `requirePm(caller, projectId)` như mọi method đọc
   khác trong service này), rồi map sang DTO trong `PmDashboardDtos` với factory method tĩnh
   `from(...)` (xem điểm 8), cuối cùng expose qua `PmDashboardController`.

7. **`@RequestParam UUID xxx` không có `required = false` khi tham số bắt buộc.**
   `PmDashboardController.templateUsage(@RequestParam UUID projectId, @RequestParam UUID repositoryId, ...)`
   (dòng 115-117) là ví dụ **chính xác** của cách khai báo `repositoryId` bắt buộc mà OI-AIRKI-2 đã
   chốt — Spring tự trả 400 nếu thiếu tham số. Copy y hệt cách khai báo này cho endpoint mới, không
   dùng `@RequestParam(required = false)` rồi tự ném `IllegalArgumentException` (cách đó tồn tại ở
   các tham số optional khác trong cùng controller nhưng KHÔNG phải cách xử lý tham số bắt buộc).

8. **DTO là `record` tĩnh lồng trong 1 class `final` không thể khởi tạo** (`PmDashboardDtos`,
   `PmDashboardModels` đều là `public final class` với constructor `private`, chứa các `public record`
   lồng bên trong, mỗi DTO record có factory method `static XxxDto from(ModelType model)`). Tính
   năng mới phải theo đúng khuôn: model mới thêm vào `PmDashboardModels` (ví dụ record
   `AiFindingStatsRow`/`AiFindingStatsSummary`), DTO tương ứng thêm vào `PmDashboardDtos` với
   `from(...)`.

9. **FE: `-` khi rate null, không phải `0%` hay ẩn dòng.** `formatUsageRate` trong
   `EDCAP_FE/src/pages/pm-dashboard/utils.ts` (dòng 64-67):
   ```ts
   export function formatUsageRate(usageRate: number | null): string {
     if (usageRate === null) return "-";
     return `${usageRate}%`;
   }
   ```
   Đây chính xác là hành vi OI-AIRKI-7 đã chốt. Có thể tái dùng thẳng hàm này (rate đã là % dạng số)
   thay vì viết hàm format mới, nếu 5 KPI rate của tính năng mới cũng biểu diễn dưới dạng % 1 chữ số
   thập phân giống `usageRate`.

10. **FE gọi API qua `EDCAP_FE/src/lib/api.ts`, không gọi `fetch` trực tiếp** (theo
    `docs/.claude/rules/20-architecture.md`). Thêm method mới vào cùng object `pmDashboard` (nơi
    `templateUsage` đang nằm, dòng ~1857) theo đúng khuôn dựng `URLSearchParams` + `api.get<T>(...)`.

11. **i18n: key dùng `t("Pages.PmDashboard.xxx", { defaultValue: "..." })` inline, không bắt buộc
    phải có sẵn trong file locale JSON.** Đã grep toàn bộ `EDCAP_FE/src/**/*.json` cho
    `templateUsage` — không có kết quả nào, nghĩa là các key hiện tại của Template Usage cũng chỉ
    tồn tại dưới dạng `defaultValue` inline trong `.tsx`, chưa được đăng ký vào locale JSON thật.
    → Tính năng mới có thể theo đúng pattern này (dùng `defaultValue` tiếng Anh inline), không cần
    tạo/sửa file locale JSON trừ khi có locale JSON thật đang tồn tại và ban tổ chức yêu cầu đồng bộ
    (chưa thấy yêu cầu này trong `spec-pack.md`).

---

## Pattern cấm dùng

1. **CẤM copy nguyên xi SQL increment của `TemplateUsageStatJdbcAdapter`:**
   ```sql
   INSERT INTO tbl_fact_template_usage_stat (...)
   VALUES (...)
   ON CONFLICT (project_id, repository_id, phase_id)
   DO UPDATE SET
       total_check_count = tbl_fact_template_usage_stat.total_check_count + 1,
       template_match_count = tbl_fact_template_usage_stat.template_match_count + EXCLUDED.template_match_count,
       updated_at = now()
   ```
   Đây là **increment theo sự kiện** (mỗi request +1). `tbl_fact_ai_finding_stat` cần
   **overwrite theo snapshot** (`col = EXCLUDED.col`, không `col = table.col + ...`) vì §8 của
   `ai-review.md` là số liệu tổng hợp tại thời điểm đọc, không phải số lần kiểm tra. Việc dùng nhầm
   increment ở đây sẽ làm KPI tăng vô hạn qua mỗi lần merge lại cùng 1 PR/redeliver webhook.

2. **CẤM đặt logic ghi DB mới nằm SAU điểm `return` sớm ở dòng ~298-303 của `handlePullRequest`.**
   Xem phân tích chi tiết ở "Chú ý khi implement" — đây là lỗi vị trí hook nghiêm trọng nhất có thể
   xảy ra, làm tính năng KHÔNG BAO GIỜ chạy với merged PR.

3. **CẤM thêm `@Transactional(propagation = Propagation.REQUIRES_NEW)` trực tiếp lên một method
   (kể cả private) của `GithubWebhookService`.** Do self-invocation, Spring sẽ bỏ qua annotation
   một cách im lặng — không có exception, không có warning, chỉ đơn giản là transaction không được
   tạo mới. Đây là loại bug khó phát hiện qua test đơn vị thông thường (mock port thì test không
   biết transaction có REQUIRES_NEW hay không) nên càng phải tuân thủ nguyên tắc "writer riêng bean".

4. **CẤM để endpoint API mới trả về `List<...>` khi response 1 object.** `templateUsage` trả
   `List<PmDashboardDtos.TemplateUsageDto>` vì bản chất là "N dòng theo phase". Endpoint mới
   (`ai-finding-stats`) đã chốt theo OI-AIRKI-2 là **1 object/lần gọi** — không bắt chước hình dạng
   List của `templateUsage`.

5. **CẤM dùng MyBatis XML mapper cho bảng fact mới.** Dù project có dùng MyBatis XML ở module khác
   (`TicketBugMetricsMapper.xml`), toàn bộ nhóm PM Dashboard + Template Usage (module gần nhất về
   nghiệp vụ) dùng JDBC thuần qua `NamedParameterJdbcTemplate`. Trộn 2 kiểu truy cập DB trong cùng
   1 tính năng liên quan tới PM Dashboard sẽ không nhất quán với các adapter lân cận.

6. **CẤM query tenant/repository scoping trong mệnh đề `WHERE` khi cần `LEFT JOIN` để giữ lại hàng
   có giá trị 0/null.** `PmDashboardJdbcAdapterFindTemplateUsageTest.findTemplateUsage_scopesTenantInsideLeftJoinOnClause_notInWhereClause`
   (đọc toàn bộ, dòng 54-92) là một regression test có chủ đích ngăn đúng lỗi này: nếu điều kiện
   `st.project_id = :projectId` / `st.repository_id = :repositoryId` bị đưa vào `WHERE` thay vì
   `ON` của `LEFT JOIN`, `LEFT JOIN` sẽ hoạt động như `INNER JOIN` trên thực tế và làm biến mất các
   hàng lẽ ra phải hiển thị "chưa có số liệu" (0/`-`). Khi viết truy vấn tổng hợp cho
   `ai-finding-stats` (SUM theo `repositoryId` từ nhiều ticket), cần rà soát kỹ tương tự nếu có
   `JOIN` giữa ticket scope và `tbl_fact_ai_finding_stat` để không vô tình loại bỏ ticket chưa có
   dòng fact nào.

7. **CẤM instantiate dependency mới bằng field default value (`= new Xxx()`) trong
   `GithubWebhookService`.** Class này có 1 field không chuẩn:
   `private final MarkdownParserCore markdownParserCore = new MarkdownParserCore();` (không qua
   constructor injection) — đây là ngoại lệ lịch sử, KHÔNG phải pattern nên bắt chước cho dependency
   mới. Bất kỳ collaborator mới nào (writer, port đọc §8, v.v.) đều phải constructor-inject như 11
   field còn lại của class.

---

## Method tồn tại / method không tồn tại

**Method/API đã xác nhận TỒN TẠI, có thể gọi trực tiếp:**

| Method | Vị trí | Chữ ký |
|---|---|---|
| `artifactScannerPersistence.findRepositoryByMaskedName(String)` | field trong `GithubWebhookService` | `Optional<RepositoryScope>` |
| `artifactScannerPersistence.upsertMinimalTicket(UUID, String, String, String, OffsetDateTime)` | field trong `GithubWebhookService` | trả `TicketScope` |
| `shouldTriggerScan(String action)` | static private, `GithubWebhookService` | `boolean`; true chỉ cho `"opened"`, `"synchronize"`, `"reopened"` |
| `isSupportedPullRequestAction(String)` | static private, `GithubWebhookService` | gate action hợp lệ (bao gồm `"closed"`) |
| `templateUsageStatWriter.recordIndependently(UUID projectId, UUID repositoryId, UUID phaseId, boolean matched)` | `TemplateUsageStatWriter` | `void`, `@Transactional(REQUIRES_NEW)` |
| `markdownParserCore.parse(String content, String path)` | `MarkdownParserCore` | trả `MarkdownDocument` (có `.sections()`, `.tables()`, `.sectionMap()`, `.frontMatter()`, `.headerMetadata()`, `.placeholders()`, `.warnings()`, `.errors()`) |
| `MarkdownDocument.tables()` | record `MarkdownParserCore.MarkdownDocument` | `List<MarkdownTable>`; mỗi `MarkdownTable` có `sectionKey()`, `sectionTitle()`, `headers()`, `rows()` (`List<List<String>>`), `startLine()` |
| `MarkdownDocument.sectionMap()` | record `MarkdownParserCore.MarkdownDocument` | `Map<String canonicalKey, String body>` — hữu ích để định vị section "§8 Số liệu thống kê" độc lập ngôn ngữ theo `canonicalKey`, xem `canonicalSectionKey(String)` (private, mapping cứng theo danh sách tên section chuẩn — heading không khớp danh sách sẽ rơi vào nhánh `UNKNOWN_SECTION`/uppercase-hóa tự do, KHÔNG có entry sẵn cho "Số liệu thống kê" — cần bổ sung case mới vào `canonicalSectionKey` nếu muốn định danh chính xác section này bằng canonical key, hoặc định vị bằng heading level + thứ tự bảng thay vì canonical key) |
| `PmDashboardRepositoryPort.findTemplateUsage(UUID, UUID)` | interface | mẫu chữ ký cho method đọc mới (không phải method gọi trực tiếp cho tính năng mới, chỉ là mẫu) |
| `PmDashboardService.requirePm(AuthUserContext, UUID)` | public method | gate quyền PM/ADMIN theo project — dùng lại y hệt cho service method mới |
| `service.getTemplateUsage(caller, projectId, repositoryId)` | `PmDashboardService` | mẫu cho service method mới (đọc + gate quyền + delegate xuống port) |

**Method KHÔNG TỒN TẠI — CẤM giả định hoặc gọi (AI dễ "bịa" các tên sau vì chúng nghe hợp lý theo ngữ cảnh):**

| Tên method có thể bị AI bịa ra | Vì sao không tồn tại / lý do dễ nhầm |
|---|---|
| `MarkdownParserCore.extractTable(String sectionKey)` | Không có convenience method lọc bảng theo section; phải tự `parse(...).tables().stream().filter(t -> t.sectionKey().equals(...))` |
| `MarkdownParserCore.findSection(String title)` hoặc `.getSection(int level)` | Không tồn tại; chỉ có `.sections()` (List) và `.sectionMap()` (Map theo canonicalKey) |
| `GithubWebhookService.recordAiFindingStat(...)` (thêm thẳng vào class này) | Vi phạm pattern "writer riêng bean" — xem Pattern cấm dùng #3 |
| `ArtifactScannerModels.TicketScope.projectId()` | `TicketScope` chỉ có 2 field: `ticketId`, `externalTicketKey` — KHÔNG có `projectId`; muốn lấy `projectId` phải dùng biến `repository.get().projectId()` đã có sẵn trong scope của `handlePullRequest`, không lấy từ `TicketScope` |
| `PmDashboardRepositoryPort.findAiFindingStat(UUID ticketId)` (số ít, theo ticket) | Theo quyết định OI-AIRKI-2, endpoint đọc theo `repositoryId` (aggregate nhiều ticket), không có method đọc theo từng ticket riêng lẻ trong scope v1 |
| `TemplateUsageStatWriter.recordSnapshot(...)` hoặc bất kỳ method thứ 2 nào trên writer này | `TemplateUsageStatWriter` chỉ có đúng 1 method `recordIndependently(...)` — không có overload khác |
| `AppProperties.getAiReview()...` hoặc cấu hình tương tự chưa xác minh | Chưa đọc `AppProperties.java` trong phiên làm việc này — KHÔNG được giả định có sẵn field cấu hình cho tính năng mới; nếu impl-plan cần 1 property mới, phải đọc `AppProperties.java` trước để biết đúng convention đặt tên |
| `pullRequestFilesPort.readFileContent(...)` / `.getFileContent(...)` | Việc đọc nội dung `ai-review.md` để parse §8 cần xác minh lại qua đúng port nào cung cấp nội dung file thay đổi (không có trong phạm vi các file đã đọc phiên này — impl-plan PHẢI đọc thêm `GithubPullRequestFilesPort` và/hoặc `ArtifactScannerSourcePort` trước khi viết code để xác nhận method đọc nội dung file đúng tồn tại, không suy đoán tên) |

**⚠ Khoảng trống cần lấp trước khi code (không giả định):** Trong `validateTemplateUsage`, nội dung
file đã thay đổi được đọc qua `artifactScannerSourcePort.readBlob(repoKey, entry.sha())` (sau khi
có `sha` từ `artifactScannerSourcePort.listTree(...)`), KHÔNG qua `pullRequestFilesPort`. Nhiều khả
năng tính năng mới cũng cần đọc nội dung `ai-review.md` theo đúng chuỗi
`resolveRevision → listTree → tìm entry theo path ticket → readBlob` này (không phải một method
đọc file trực tiếp theo path). Impl-plan phải xác nhận lại chuỗi gọi này bằng cách đọc
`ArtifactScannerSourcePort` đầy đủ trước khi viết code — port này KHÔNG được đọc toàn bộ trong
phiên Tech Lead hiện tại.

---

## Mapping

**Bảng DB liên quan (đã xác nhận từ migration thật, không suy đoán):**

| Bảng | Vai trò | Cột khóa liên kết |
|---|---|---|
| `tbl_dim_project` | Dimension dự án | `project_id` (PK), tham chiếu bởi mọi bảng fact |
| `tbl_dim_repository` | Dimension repository | `repository_id` (PK), `project_id` (FK), `repo_name_masked`, `status` |
| `tbl_dim_ticket` | Dimension ticket | `ticket_id` (PK) — bảng mới `tbl_fact_ai_finding_stat` khóa theo `ticket_id`, FK về đây |
| `tbl_dim_phase` | Dimension phase SDD (`phase_code '1'..'8'`, `phase_order`) | dùng bởi Template Usage; **không** cần cho `tbl_fact_ai_finding_stat` vì §8 không phân theo phase |
| `tbl_fact_template_usage_stat` | Bảng tham chiếu mẫu (KHÔNG phải bảng của ticket này) | PK tổng hợp `(project_id, repository_id, phase_id)`, increment counters |
| `tbl_fact_ai_finding_stat` (MỚI, chưa tạo) | Bảng đích của ticket này | Theo `spec-pack.md` §8: khóa theo `ticket_id` (unique), các cột đếm dạng snapshot, `created_at`/`updated_at` (schema tối giản kiểu V510, quyết định OI-AIRKI-4) |
| `tbl_fact_ticket_dashboard_snapshot` | Snapshot tổng hợp PM Dashboard hiện có | KHÔNG lưu KPI mới ở đây — đây là bảng riêng cho snapshot ticket-status, không mở rộng cho AI Finding KPI theo thiết kế đã chốt |

**Không có master data / code value / SEQNO liên quan.** Đã kiểm tra: không có bảng
`tbl_mst_*`/mã danh mục dạng SEQNO nào cần map cho 5 chỉ số KPI này — dữ liệu đến trực tiếp từ
parse Markdown, không qua bảng master data trung gian. Nếu impl-plan phát sinh nhu cầu thêm
`artifact_type_code` mới (giống cách `V510` đăng ký `OPEN_ISSUES`/`CONTEXT` vào
`tbl_dim_artifact_type`), cần đối chiếu lại với `spec-pack.md` §8 xem có yêu cầu này không — phiên
Tech Lead hiện tại KHÔNG thấy yêu cầu đăng ký `artifact_type` mới trong spec đã chốt.

**Quyền (permission) — đã xác nhận từ `PmDashboardService`:**

| Role | Quyền |
|---|---|
| `ADMIN` | Full access, bỏ qua check theo project |
| `PM` | Chỉ truy cập được nếu có role `PM` trên đúng `projectId` được truyền vào (kiểm tra qua `repository.findProjectRole(caller, projectId)`) |

→ Endpoint mới (`ai-finding-stats`) PHẢI gọi `requirePm(caller, projectId)` giống hệt
`getTemplateUsage`, không tạo cơ chế quyền mới.

---

## Rule nghiệp vụ đặc thù

- **Đa ngôn ngữ (FE i18n)**: dùng `react-i18next`, key dạng `Pages.PmDashboard.xxx`, giá trị mặc
  định tiếng Anh khai báo inline qua `t(key, { defaultValue: "..." })`. KHÔNG có locale JSON files
  cần cập nhật bắt buộc (đã kiểm tra, xem "File đã đọc"). Không liên quan tới tiếng Nhật.
- **Đa ngôn ngữ (BE parsing §8)**: đây là yêu cầu đa ngôn ngữ THẬT SỰ của ticket này — khác hẳn
  i18n UI. Việc parse §8 "Số liệu thống kê" trong `ai-review.md` PHẢI độc lập ngôn ngữ (structural,
  theo vị trí bảng/heading level, KHÔNG match theo text label dịch được) vì tài liệu nguồn có thể ở
  VI/EN/JA. Đây là lý do chính khiến `MarkdownParserCore.tables()`/`.sections()` (dựa vào cấu trúc
  Markdown, không phải regex theo từ khóa ngôn ngữ) là công cụ đúng, không phải string-matching
  heading tiếng Việt.
- **`formItemNm` / `SEQNO` / full-width–half-width / "giữ nguyên tiếng Nhật" / cấm Unicode hóa**:
  đã tìm kiếm toàn repo (`EDCAP_BE/src`, `EDCAP_FE/src`) — **không tìm thấy** khái niệm nào trong số
  này tồn tại trong codebase hoặc liên quan tới ticket `AI-REVIEW-KPI-IMPROVEMENT`. Đây là các mối
  quan tâm đặc trưng của một số dự án thị trường Nhật Bản (ví dụ hệ thống có form động
  `formItemNm`/`SEQNO`, hoặc yêu cầu bảo toàn ký tự toàn/bán góc trong dữ liệu tiếng Nhật) — không
  áp dụng cho EDCAP. Không tạo rule giả cho các khái niệm này.
- **Không backfill** (OI-AIRKI-8 đã chốt): ticket merge trước khi tính năng deploy sẽ không có dữ
  liệu cho tới lần merge tiếp theo — đây là hành vi được chấp nhận, không phải bug cần né bằng
  migration data.
- **`repositoryId` bắt buộc, response 1 object** (OI-AIRKI-2) — xem "Pattern nên dùng" #7, #4.
- **Rate = `null` chỉ khi tổng denominator = 0 trên toàn bộ ticket trong scope** (OI-AIRKI-7) — khi
  viết SQL aggregate (SUM tử số / SUM mẫu số qua nhiều ticket), phải SUM trước rồi mới chia, không
  chia từng ticket rồi AVG (2 công thức cho kết quả khác nhau và chỉ SUM-rồi-chia đúng theo spec).
- **Blocker/Major là 1 chỉ số gộp** (OI-AIRKI-1) — không tách cột riêng Blocker và Major ở v1.
- **Idempotency**: giống hệt nguyên tắc đã ghi trong Javadoc lớp `GithubWebhookService` (GitHub có
  thể redeliver cùng 1 webhook) — ghi `tbl_fact_ai_finding_stat` PHẢI là upsert theo
  `ticket_id` (snapshot ghi đè), không phải insert-only hay increment, để redeliver không làm sai
  lệch số liệu.

---

## Chú ý khi implement

1. **Vị trí hook chính xác (điểm quan trọng nhất, đã tinh chỉnh so với phân tích trước đó trong
   `spec-pack.md` §8):** Trong `handlePullRequest`, code build `ticketScopes` kết thúc ở dòng ~296:
   ```java
   Map<String, ...TicketScope> ticketScopes = new LinkedHashMap<>();
   for (String ticketKey : ticketKeys) {
       ticketScopes.put(ticketKey, artifactScannerPersistence.upsertMinimalTicket(...));
   }
   ```
   Ngay sau đó, dòng ~298-303 có một **early return**:
   ```java
   if (!shouldTriggerScan(action)) {
       log.info(...);
       int affected = ...;
       return new Result("pull_request:" + action, affected);
   }
   ```
   `shouldTriggerScan(action)` trả `true` CHỈ cho `"opened"`, `"synchronize"`, `"reopened"` — KHÔNG
   bao gồm `"closed"`. Vì PR merged luôn có `action == "closed"`, nhánh `if` này LUÔN đúng với PR
   merged, và hàm return NGAY tại đây — code phía dưới (phần trigger scan) không bao giờ chạy với
   PR merged. **Logic ghi `tbl_fact_ai_finding_stat` mới PHẢI được chèn giữa dòng ~296 (sau vòng
   lặp `ticketScopes`) và dòng ~298 (trước `if (!shouldTriggerScan(action))`)** — không phải "sau
   `ticketScopes`" một cách chung chung, vì nếu đặt sau điểm return thì tính năng sẽ KHÔNG BAO GIỜ
   chạy với đúng trigger mà nó cần (PR merged).
   Điều kiện kích hoạt parse §8 nên đối chiếu với điều kiện đã có sẵn ở dòng 253-256
   (`"closed".equals(action) && pullRequest.path("merged").asBoolean(false)`) — đây chính là guard
   `validateTemplateUsage` đang dùng, và nghiệp vụ mới (đọc §8 khi PR merge) có cùng điều kiện kích
   hoạt, nên đặt logic mới trong cùng guard `if` này hoặc một guard tương đương ngay trước điểm
   early-return, KHÔNG tạo thêm biến `action`/`merged` mới khi 2 biến này đã có sẵn trong scope.
2. **Không đổi thứ tự các bước hiện có khác** trong `handlePullRequest` (yêu cầu H-AIRKI-5/OI-AIRKI-5
   đã chốt) — chỉ chèn thêm, không di chuyển code có sẵn.
3. **Trước khi viết `impl-plan.md`, đọc thêm** (chưa đọc đầy đủ trong phiên Tech Lead này, xem
   "Method không tồn tại" phần khoảng trống): `ArtifactScannerSourcePort` (đầy đủ), `AppProperties`
   (nếu cần property cấu hình mới), `GithubPullRequestFilesPort` (để xác nhận có cần dùng port này
   hay chỉ cần `ArtifactScannerSourcePort.readBlob`).
4. **Migration mới**: version tiếp theo là `V511` (đã xác nhận `V510` là file mới nhất qua
   `**/V51*.sql`, không dùng `V50*.sql` để tìm vì bỏ sót `V510`). Đặt tên file theo đúng convention
   `V511__<snake_case_description>.sql` giống các file trước.
5. **Test bắt buộc phải cập nhật**: `pull_request_closed_merged_event_updates_status_without_scanning`
   (dòng ~563-601 của `GithubWebhookServiceTest.java`) là test hiện có bao phủ chính xác luồng
   "merged PR → cập nhật status, không scan" — đây là nơi hợp lý nhất để bổ sung `verify(...)` cho
   writer mới, hoặc tạo test case mới liền kề cùng fixture. Test
   `pull_request_closed_event_updates_status_without_scanning` (merged=false) phải KHÔNG gọi writer
   mới — cần một `verify(..., never())` tương ứng.

---

## Chú ý khi review

- Kiểm tra writer mới có thật sự là `@Component` riêng, không phải method trong `GithubWebhookService`.
- Kiểm tra SQL upsert dùng `col = EXCLUDED.col` (overwrite), KHÔNG dùng `col = table.col + ...` (increment).
- Kiểm tra vị trí chèn code nằm TRƯỚC dòng `if (!shouldTriggerScan(action))`, verify bằng cách đọc
  lại số dòng thực tế sau khi chỉnh sửa (số dòng sẽ dịch chuyển so với file gốc 608 dòng).
- Kiểm tra endpoint mới trả về 1 object (không phải `List<...>`), và `repositoryId` là
  `@RequestParam UUID` bắt buộc (không có `required = false`).
- Kiểm tra `requirePm(caller, projectId)` được gọi trong service method mới, không bỏ sót permission gate.
- Kiểm tra rate `null` chỉ khi SUM(denominator) toàn scope = 0, không phải per-ticket rồi loại bỏ ticket có mẫu số 0 khỏi tính trung bình.
- Kiểm tra không có migration nào khác đã lấy `V511` trước đó (race điều kiện nếu có nhánh song song).
- Kiểm tra FE dùng lại `formatUsageRate` hoặc hàm cùng logic (`null → "-"`), không viết lại logic hiển thị mới không nhất quán.

---

## Chú ý khi test

- Bắt buộc đọc toàn bộ 33 test case hiện có trong `GithubWebhookServiceTest.java` trước khi thêm
  test mới (đã ủy quyền, quyết định H-AIRKI-6/OI-AIRKI-6) — danh sách tên đầy đủ đã liệt kê qua grep
  trong phiên Tech Lead này (33 `@Test` method, chủ đề chính: signature verification, pull_request
  theo từng action, push event, review/review_comment event, và 1 nhóm ~14 test riêng cho
  `validateTemplateUsage`/`template_usage_*` — nhóm này là mẫu gần nhất về style test cho tính năng
  mới, đặc biệt: `template_usage_skips_when_action_is_opened_synchronize_or_reopened_even_if_merged_true`
  và `template_usage_skips_when_pull_request_is_closed_but_not_merged` xác nhận đúng 2 boundary case
  (`action != closed` và `merged == false`) mà logic mới cũng phải test tương tự.
- Test PM Dashboard mới nên đặt cùng source root với `PmDashboardJdbcAdapterFindTemplateUsageTest`,
  tức **`EDCAP_BE/src/test/UnitTest/java/...`** (KHÔNG phải `src/test/java/...`) — xem "Chú ý khi
  implement/source-map.md" để biết rõ vì sao 2 khu vực test dùng 2 source root khác nhau trong cùng
  project.
- Mock port bằng Mockito thuần (`Mockito.mock(...)`, `when(...)`, `verify(...)`) — không mock domain
  model/value object (theo `docs/.claude/rules/40-testing.md`: "Do not mock what you own").
- Test JDBC adapter mới nên có ít nhất 1 test theo mẫu
  `findTemplateUsage_scopesTenantInsideLeftJoinOnClause_notInWhereClause` — assert trực tiếp trên
  chuỗi SQL captured (`ArgumentCaptor<String>`) để khóa chặt vị trí điều kiện tenant-scoping trong
  câu SQL, phòng regression khi có người "đơn giản hóa" query sau này.
