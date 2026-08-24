# review-checklist

**Ticket ID**: AI-REVIEW-KPI-IMPROVEMENT
**Create date**: 2026-08-19
**Author**: Principal Reviewer (Claude)
**Update date**: 2026-08-19

> Tài liệu này văn bản hóa các review viewpoint **trước khi implementation bắt đầu**, dựa trên
> `spec-pack.md`, `impact-analysis.md`, `impl-plan.md`. Dùng để tự-review (AI) và human-review sau khi
> code xong — điền cột `Result`/checkbox tại `self-review.md`, không sửa file này trừ khi spec đổi.
> Các điểm còn mâu thuẫn/chưa xác định giữa các tài liệu được giữ nguyên là **chưa xác định** — không
> tự ý chọn phương án và ghi như đã chốt.

## 1. Đối chiếu specification / AC

| AC ID | Review Point | Severity | Result |
|---|---|---|---|
| AC-AIRKI-1 | Parse thành công: upsert đúng 1 row `tbl_fact_ai_finding_stat` với `ticket_id` đúng, 5 cặp numerator/denominator khớp **chính xác từng ô** trong bảng §8 thật (không lệch dòng/cột, không off-by-one index) | Blocker | |
| AC-AIRKI-2 | Độc lập ngôn ngữ (heading): heading `## 8. ...` được định vị bằng số thứ tự "8." (regex), test với heading giả lập EN (`## 8. Statistics`) và JA (`## 8. 統計データ`) đều ra cùng kết quả | Blocker | |
| AC-AIRKI-3 | Độc lập ngôn ngữ (label dòng): dùng chính `raw/ai-review.md` (label đã diễn đạt lại khác `_ticket-template`) làm fixture thật — verify map đúng theo vị trí dòng (index 2-6), không theo text label | Blocker | |
| AC-AIRKI-4 | Giá trị not-applicable (`"không áp dụng"`, `"⬜ chưa có dữ liệu"`, hoặc pattern số không hợp lệ khác) → lưu `NULL`, không phải `0`; xử lý PR không bị lỗi/chặn lại | Blocker | |
| AC-AIRKI-5 | Merge lại (re-merge) ticket đã có row: giá trị mới **ghi đè** hoàn toàn, không có row thứ 2, không cộng dồn từng phần (ví dụ chỉ 3/5 cặp được cập nhật còn lại giữ cũ) | Blocker | |
| AC-AIRKI-6 | Webhook redelivery: chạy lại đúng payload đã xử lý trước đó cho kết quả **giống hệt** 1 lần xử lý (idempotent) — không tăng count, không đổi `updated_at` sai cách | Blocker | |
| AC-AIRKI-7 | Cô lập lỗi ghi: `DataAccessException` khi ghi `tbl_fact_ai_finding_stat` không làm rollback/fail transaction chính của `handlePullRequest` (collector, security evidence, template-usage vẫn hoàn tất) | Blocker | |
| AC-AIRKI-8 | Fetch blob lỗi (401/403/404): skip ticket đó, không throw exception làm fail toàn bộ webhook, log WARN kèm status code (không log token/body response) | Major | |
| AC-AIRKI-9 | API tổng hợp: `ROUND(SUM(numerator)*100.0/SUM(denominator), 1)` đúng công thức, trả `null` khi tổng denominator (toàn bộ ticket trong scope) = 0 — không phải lỗi chia 0/NaN | Blocker | |
| AC-AIRKI-10 | Permission: user không có `PM` scoped đúng `projectId` và không phải `ADMIN` → 403 theo `ErrorResponse` chuẩn, `errorCode`/`error` = `Component.Permission.Denied` | Blocker | |
| AC-AIRKI-11 | Hiển thị FE: **[Resolved OI-AIRKI-14, 2026-08-20]** dùng pattern card-grid kiểu `SummaryCards` (không phải `CDataTable`) — người dùng xác nhận trực tiếp, khớp phân tích `impl-plan.md`/`TemplateUsageByPhase.tsx` đã có ở OI-AIRKI-13. `spec-pack.md` §6/§9 (đề cập `CDataTable`) là tài liệu chưa đồng bộ, không sửa lại phạm vi ticket này — chỉ ghi nhận `impl-plan.md`/`SummaryCards` là nguồn đúng. Rate `null` hiển thị `-`. | Question (Resolved) | |
| AC-AIRKI-12 | AC Closure: section/component mới phải **mount thực tế** trong `PMDashboardPage.tsx` (không chỉ tồn tại như component cô lập) — verify bằng `PMDashboardPage.test.tsx` (DOM ordering + tham số gọi API), đúng rule `testing.md` "AC Closure" | Major | |

## 2. General System Review

### Số, số full-width, số chữ số, độ chính xác

- [ ] Rate mỗi KPI làm tròn đúng **1 chữ số thập phân**, dùng lại đúng convention rounding của `TemplateUsageDto.usageRate` (không viết logic `ROUND`/`BigDecimal` mới riêng gây lệch chế độ làm tròn — HALF_UP vs HALF_EVEN)
- [ ] Bảng §8 trong `ai-review.md` có thể chứa số full-width (ví dụ do người soạn gõ nhầm từ bộ gõ tiếng Nhật/Trung) — xác nhận rõ ràng regex trích số **chỉ chấp nhận half-width ASCII digit** hay có auto-normalize; nếu không hỗ trợ full-width, phải coi là "not-applicable" (NULL) chứ không throw exception hay parse sai
- [ ] Không trộn lẫn nửa/đầy đủ (half-width/full-width) trong cùng 1 giá trị số (ví dụ `1２/15`) — có xử lý rõ ràng, không parse ra giá trị sai lệch âm thầm
- [ ] Numerator/denominator lưu kiểu `INTEGER`, không có bước ép qua `Double`/`float` trung gian gây mất chính xác trước khi lưu DB
- [ ] Phân biệt rõ `NULL` (not-applicable/no-data) và `0` (có dữ liệu, giá trị thật là 0) — không được conflate hai trường hợp này ở bất kỳ tầng nào (parser, DB, DTO, FE)
- [ ] Không có overflow/underflow thực tế cho `INTEGER` count (số finding của 1 ticket không thể vượt phạm vi `INTEGER`, nhưng vẫn xác nhận không có phép cộng dồn sai logic nào có thể khiến giá trị âm hoặc vượt ngưỡng)
- [ ] Response API: `Double` rate hiển thị đúng 1 chữ số thập phân ở tầng JSON serialize (không bị JS/Jackson cắt bớt số 0 ở cuối theo cách gây hiểu lầm, ví dụ `100.0` không rút gọn thành `100`)

### Loại ký tự, encoding, locale

- [ ] `ai-review.md` đọc qua `readBlob` giữ nguyên UTF-8, không mojibake với ký tự tiếng Việt có dấu (labels, giá trị not-applicable dạng text)
- [ ] Regex/heading detection cho `## 8.` chịu được khoảng trắng bất thường quanh số thứ tự (tab, non-breaking space, nhiều khoảng trắng) — có rule trim rõ ràng
- [ ] Chuỗi đánh dấu not-applicable (`"không áp dụng"`, `"chưa có dữ liệu"`, biểu tượng `⬜`) — nếu logic có so khớp text ở bất kỳ đâu (dù thiết kế chính là dựa vào "không match được pattern số" chứ không match text) thì phải diacritic-safe, không phụ thuộc chuẩn hóa Unicode cụ thể
- [ ] i18n key mới `Pages.PmDashboard.aiFindingStats.*` được thêm **đủ ở mọi file ngôn ngữ** hiện có (không chỉ 1 locale, gây fallback key thô ngoài UI)
- [ ] Text tiếng Việt/Anh (labels, message lỗi) trong i18n mới không lỗi chính tả, không sao chép nhầm từ `templateUsage.*` mà quên đổi nghĩa
- [ ] `repositoryName` lấy từ `tbl_dim_repository.repo_name_masked` không bị mất/đổi encoding khi qua các tầng JOIN → DTO → JSON → FE render

### Literal / Magic Number / Master Data

- [ ] Vị trí dòng bảng §8 dùng cho positional mapping (index 2-6) được định nghĩa tại **một nơi duy nhất** (constant/enum có tên rõ ràng, ví dụ `AiFindingRowIndex`), không rải rác số nguyên "2", "3"... trực tiếp trong code parser
- [ ] Tên cột DB (`blocker_major_resolved_count`, `ai_review_finding_total_count`, ...) dùng thống nhất giữa migration DDL và adapter SQL — không có literal string bị gõ sai lệch giữa 2 nơi
- [ ] Hằng số làm tròn (`1` chữ số thập phân), tên migration (`V511...`), error code (`Component.Permission.Denied`) đều tái sử dụng constant/pattern có sẵn, không phải literal chép tay mới
- [ ] Không hard-code business value nào khác ngoài các literal đã được spec hóa rõ ràng (ví dụ không tự thêm ngưỡng/threshold nào không có trong spec)
- [ ] Mapping giữa tên hiển thị (label FE) và field nội bộ (`blockerMajorResolutionRate`, ...) rõ ràng, không suy luận ngầm qua thứ tự field trong JSON

### Chuyển trạng thái, boundary value, exception

- [ ] Boundary: 1 KPI có denominator = 0 ở **mức 1 ticket** (not-applicable) nhưng SUM tổng ở mức repository vẫn > 0 (do ticket khác có dữ liệu) → rate vẫn tính đúng, ticket NULL không kéo cả tổng về NULL
- [ ] Boundary: **toàn bộ ticket trong repository** đều NULL cho 1 KPI cụ thể → tổng denominator = 0 → API trả `null` cho đúng KPI đó, không lỗi chia 0/NaN, các KPI khác vẫn tính bình thường
- [ ] Boundary: repository chưa có ticket nào có dữ liệu trong `tbl_fact_ai_finding_stat` (0 row) → API vẫn trả 200 với cả 5 rate = `null`, không phải 404/500
- [ ] Chuyển trạng thái: ticket đã có 1 row → PR thứ 2 merge → row chuyển từ "giá trị cũ" sang "giá trị mới" đúng 1 lần UPDATE, không phát sinh row thứ 2, không vi phạm `UNIQUE(ticket_id)`
- [ ] Exception: bảng §8 tồn tại nhưng sai cấu trúc (thiếu dòng, sai số cột, heading tồn tại nhưng không có table theo sau) → skip có kiểm soát (log, không ghi row), không throw exception làm gián đoạn `handlePullRequest`
- [ ] Exception: PR có `action=closed` nhưng `merged=false` → không kích hoạt logic ghi mới (đúng guard điều kiện tái sử dụng), không tạo row rác
- [x] Boundary: giá trị số parse được là âm hoặc vượt quá denominator tương ứng (numerator > denominator) — **[Resolved OI-AIRKI-15, 2026-08-20]** người dùng xác nhận giả định input: dữ liệu đầu vào (bảng §8 của `ai-review.md`) không phát sinh trường hợp numerator > denominator trong thực tế. Không thêm validate/log-cảnh-báo cho trường hợp này, không thêm test boundary giả định input bất thường không xảy ra trong thực tế (parser xử lý nguyên theo giá trị parse được, đúng hành vi hiện tại)

## 3. FE Review

- [ ] Component mới hiển thị đúng 5 KPI rate của repository đã chọn, `null` → hiển thị `-` (không phải `NaN%`, `undefined`, chuỗi rỗng)
- [ ] **[Xem AC-AIRKI-11 ở mục 1]** Cấu trúc UI thật (CDataTable hay card-grid) khớp với quyết định cuối cùng đã chốt tại thời điểm implement — không tự ý chọn khác spec mà không cập nhật tài liệu
- [ ] `useQuery` mới dùng đúng `queryKey` phân biệt theo `projectId`/`repositoryId` (tránh cache lẫn giữa các repository khác nhau), `enabled` gate giống hệt pattern `templateUsage` (`Boolean(filters.projectId) && Boolean(filters.repositoryId)`)
- [ ] Gọi API duy nhất qua `lib/api.ts` (`endpoints.pmDashboard.*`), không gọi `fetch` trực tiếp ở component
- [ ] Component mới mount đúng vị trí trong `PMDashboardPage.tsx` (ngay sau các component summary hiện có), không phá vỡ thứ tự render các component khác
- [ ] Interface TypeScript `AiFindingStats` không dùng `any`, không có `as` cast không giải thích, field khớp 100% với JSON response thật (không suy đoán tên field)
- [ ] Trạng thái loading/error được xử lý rõ ràng (không render trắng/crash khi API đang fetch hoặc lỗi)
- [ ] Named export, không dùng default export (theo `coding.md`); nếu là reusable UI component thì có `forwardRef` + `displayName`
- [ ] Không có regression ở các section hiện có của `PMDashboardPage.tsx` (SummaryCards, AllTicketsTable, TemplateUsageByPhase, TicketDetailDrawer) do việc chèn thêm component mới

## 4. BE/API Review

- [ ] `PmDashboardController` — endpoint mới thin, không chứa logic nghiệp vụ, `@RequestParam UUID projectId, @RequestParam UUID repositoryId` đều bắt buộc (không `required = false`)
- [ ] `PmDashboardService.getAiFindingStats` gọi `requirePm(caller, projectId)` **trước** khi truy vấn dữ liệu (không leak thông tin tồn tại/không tồn tại của repository qua timing hoặc lỗi khác nhau nếu thứ tự bị đảo)
- [ ] Query đọc là **1 câu SUM-aggregate** theo `WHERE project_id=... AND repository_id=...`, không dùng `LEFT JOIN` kiểu `findTemplateUsage` (tránh fan-out nhân bản dòng không cần thiết)
- [ ] Toàn bộ tham số SQL dùng `NamedParameterJdbcTemplate` binding, không có chuỗi SQL nối tay từ `projectId`/`repositoryId` (rủi ro SQL injection dù input là UUID)
- [ ] `AiFindingStatWriter` là `@Component` **riêng biệt** (không phải method nội bộ của `GithubWebhookService`) — xác nhận `@Transactional(REQUIRES_NEW)` thật sự áp dụng qua proxy Spring, không bị vô hiệu do self-invocation
- [ ] SQL upsert dùng đúng `ON CONFLICT (ticket_id) DO UPDATE SET col = EXCLUDED.col` (ghi đè) — **không phải** `col = table.col + EXCLUDED.col` (cộng dồn); đây là điểm đúng-sai nghiệp vụ quan trọng nhất, cần đọc trực tiếp SQL thật để xác nhận, không suy đoán từ tên method
- [ ] Vị trí chèn hook trong `handlePullRequest` không làm thay đổi thứ tự/side-effect của các bước đã có (`validateTemplateUsage`, collector, security evidence...) — verify bằng cách chạy lại toàn bộ test hiện có, không chỉ đọc code bằng mắt
- [ ] `DataAccessException` từ writer bị bắt, không propagate ra làm fail response webhook (webhook luôn trả `200` cho GitHub)
- [ ] Lỗi fetch blob 401/403/404 → skip ticket, log WARN kèm status code, không log token/body
- [ ] DTO field name khớp 100% JSON contract đã định (`repositoryId`, `repositoryName`, `blockerMajorResolutionRate`, `aiReviewAdoptionRate`, `aiReviewValidFindingRate`, `aiFalsePositiveRate`, `aiFindingResolutionRate`)
- [ ] **[Ghi chú tài liệu, không phải bug]** `spec-pack.md` §9/§10 mô tả `ErrorResponse` có 6 field (`timestamp, status, error, errorCode, message, traceId`); source thật `ErrorResponse.java` chỉ có 5 field (`timestamp, status, error, message, traceId`, không có `errorCode` riêng). Review point: xác nhận response lỗi thật của endpoint mới đúng 5-field theo source, không theo mô tả 6-field trong spec-pack — đây là spec-pack mô tả sai, không phải regression
- [ ] Thiếu `projectId`/`repositoryId` → hành vi 500 hiện tại của hệ thống (không phải 400) — xác nhận endpoint mới **nhất quán** với hành vi này, không vô tình thêm validation riêng làm nó trả 400 khác các endpoint PM Dashboard khác

## 5. DB/Migration Review

- [x] Tên file migration lấy đúng version kế tiếp thật tại thời điểm code (re-check `db/migration/`, không giả định `V511` vẫn còn trống) — **[2026-08-20]** đúng như lo ngại đã ghi ở đây, sau khi merge `main` phát hiện `V511` đã bị chiếm bởi `V511__ai_quality.sql` của 1 ticket khác; đổi file của ticket này thành `V512__add_ai_finding_stat_tracking.sql` (re-check lại `db/migration/` xác nhận `V512` còn trống tại thời điểm đổi)
- [ ] FK đúng bảng/cột: `project_id → tbl_dim_project(project_id)`, `repository_id → tbl_dim_repository(repository_id)`, `ticket_id → tbl_dim_ticket(ticket_id)`, đều kiểu `UUID`
- [ ] `UNIQUE(ticket_id)` (`uq_ai_finding_stat_ticket`) tồn tại và được dùng làm target của `ON CONFLICT` trong câu upsert (khớp nhau, không lệch tên constraint)
- [ ] Index thường trên `(project_id, repository_id)` tồn tại, hỗ trợ đúng câu query đọc aggregate
- [ ] Trigger `trg_ai_finding_stat_updated_at` cập nhật `updated_at` tự động, đúng pattern trigger dùng chung của `database.md` (không viết logic update thủ công trong code Java)
- [ ] 7 cột đếm (`blocker_major_resolved_count`, ..., `ai_review_resolved_count`) đều `INTEGER NULL` (không `NOT NULL`) — bắt buộc để lưu được `NULL` cho not-applicable
- [ ] Migration thuần túy `CREATE TABLE` mới — không có `ALTER TABLE` nào lên bảng hiện có
- [ ] Không phát sinh thêm cột audit/soft-delete ngoài phạm vi đã chốt (`created_at`/`updated_at` only) — tránh scope creep so với schema tối giản đã quyết định
- [ ] Migration file không bị sửa lại sau khi đã apply (Flyway checksum) trong quá trình dev/test lặp lại

## 6. Security/Privacy Review

- [ ] `requirePm(caller, projectId)` là gate thật sự được thực thi (không bị bug copy-paste dùng nhầm permission check khác)
- [ ] Xác thực webhook HMAC-SHA256 không bị đụng chạm — logic mới nằm hoàn toàn sau bước xác thực chữ ký đã có trong `GithubWebhookController`, không mở thêm entrypoint nào
- [ ] Không có bất kỳ nội dung text tự do nào từ cột "Ghi chú" của bảng §8 được lưu vào DB hoặc ghi vào log — chỉ lưu/log số đếm (numerator/denominator)
- [ ] Log không bao giờ chứa toàn bộ nội dung `ai-review.md`, token GitHub, hay secret webhook — chỉ log `ticketId` + số liệu đã parse
- [ ] Tham số SQL được bind qua `NamedParameterJdbcTemplate`, không nối chuỗi SQL thủ công từ input (dù input là UUID đã được Spring type-convert)
- [ ] `GlobalExceptionHandler` catch-all không bị thay đổi/mở rộng ngoài phạm vi ticket — response lỗi không leak stack trace/class name nội bộ
- [ ] Endpoint mới **không** thêm `permitAll`/CORS rule riêng — vẫn yêu cầu session đã đăng nhập giống mọi endpoint PM Dashboard khác
- [ ] Response DTO không trả bất kỳ field owner/author/assignee nào (rule "Pseudonym Only" của `security.md`) — xác nhận không có leak field nội bộ nào qua serialize (thiếu `@JsonIgnore` ở field ẩn)
- [ ] Dữ liệu ghi vào bảng mới không chứa PII — đúng theo đánh giá `spec-pack.md` §11 (chỉ số đếm, không lưu text tự do)

## 7. Operation/Maintenance Review

- [ ] Log đủ để điều tra sự cố: bắt đầu parse (INFO, kèm `ticketId`+path), parse thành công (INFO, số field parse được), skip do lỗi fetch (WARN, kèm status code), skip do bảng/section không hợp lệ (WARN), lỗi ghi DB (WARN/ERROR) — không log nội dung file gốc
- [ ] Correlation: log của luồng parse mới có thể liên kết được với log webhook chính (cùng `ticketId`/request context) để trace xuyên suốt 1 lần xử lý PR
- [ ] Retry/redelivery: log không bị nhân đôi gây nhiễu khi GitHub redeliver webhook — có thể phân biệt được đây là redelivery hay lần xử lý mới khi đọc log (nếu cần)
- [ ] Rollback/manual recovery: có ghi chú rõ cách rollback migration (`DROP TABLE tbl_fact_ai_finding_stat`, bảng mới hoàn toàn nên an toàn) ở tài liệu vận hành, không chỉ tồn tại ngầm trong đầu người viết
- [ ] Cấu hình không hard-code sai chỗ: các hằng số vị trí dòng/cột (positional mapping) được coi là thiết kế cố định có chủ đích (không phải thứ cần đưa ra config), nhưng cần tập trung ở 1 nơi rõ ràng, không rải rác
- [ ] Monitoring: cơ chế theo dõi tỷ lệ log WARN "parse failed/skip" theo thời gian có thực sự khả thi bằng công cụ log hiện có (không có dashboard riêng theo spec) — xác nhận đủ cho vận hành giai đoạn đầu, không phải giả định suông

## 8. Test Review

- [ ] Tối thiểu 3 fixture cho unit test parser: (1) label đúng `_ticket-template`, (2) label diễn đạt lại như `raw/ai-review.md` thật, (3) heading giả lập ngôn ngữ khác (EN/JA) — đủ cả 3, không chỉ 1
- [ ] Fixture (2) dùng **chính file `raw/ai-review.md` thật**, không phải bản tự viết mô phỏng
- [ ] Test writer bean assert rõ ràng hành vi ghi đè: parse 2 lần với 2 giá trị khác nhau, verify kết quả cuối = lần 2 (không phải tổng 2 lần)
- [ ] Toàn bộ 33 test case hiện có trong `GithubWebhookServiceTest.java` được chạy lại và **pass không sửa đổi** (baseline regression) — không chỉ giả định không ảnh hưởng
- [ ] 3 file test PM Dashboard hiện có (`PmDashboardServiceTest`, `PmDashboardJdbcAdapterFindTemplateUsageTest`, `PmDashboardControllerTest`) được đọc trước, test mới theo đúng convention mock/fixture đã có, không tạo pattern mới không cần thiết
- [ ] Test FE cho rate `null` → hiển thị `-`, trạng thái loading, và test tích hợp `PMDashboardPage.test.tsx` xác nhận section mount đúng vị trí (không chỉ test component cô lập — theo rule "AC Closure" của `testing.md`)
- [x] Test migration verify `UNIQUE(ticket_id)` và FK constraint reject giá trị không hợp lệ — **[Resolved OI-AIRKI-16, 2026-08-20]** người dùng chấp nhận gap: không viết integration test DB thật cho migration `V512` (đổi tên từ `V511` cùng ngày do va version với migration khác sau merge `main`; nhất quán với rule cấm production DB trong unit test); constraint chỉ được xác nhận gián tiếp qua SQL string assertion ở `AiFindingStatJdbcAdapterTest`, rủi ro được chấp nhận có ý thức
- [ ] Có test case riêng cho webhook redelivery idempotency (AC-AIRKI-6), không chỉ suy luận từ logic upsert
- [ ] Test boundary: toàn bộ denominator = 0 ở mức aggregate → rate `null`, không lỗi/NaN
- [ ] `ArchitectureTest` (nếu tồn tại) vẫn pass với class mới đặt đúng layer domain/application/infrastructure — **[chưa xác định]** liệu file test này có tồn tại trong codebase hay không, cần xác nhận trực tiếp tại thời điểm implement bất kể quyết định trước đó có bỏ qua việc xác minh

## 9. Documentation/Traceability Review

- [ ] `self-review.md` có bảng đối chiếu AC-AIRKI-1..12 với evidence cụ thể (tên test/file/dòng), không chỉ ghi "done"
- [x] Tên file migration thật được ghi lại chính xác trong tài liệu (không để nguyên `V511` nếu version thật tại thời điểm code khác đi) — **[2026-08-20]** cập nhật `V511` → `V512` ở `spec-pack.md`, `self-review.md`, `open-issues.md`, `impact-analysis.md`, `source-map.md` sau khi đổi tên file thật; các artifact review đã đóng dấu thời gian trước đó (`codex-review.md`, `test-results.md`, `blackbox-test-results.md`) giữ nguyên tham chiếu `V511` như bản ghi lịch sử tại thời điểm review, không viết lại
- [ ] Mọi sai lệch so với `spec-pack.md` (ví dụ cấu trúc FE — xem AC-AIRKI-11) được ghi nhận tường minh kèm lý do trong `self-review.md`, không âm thầm đổi mà không note
- [ ] i18n key namespace ghi trong tài liệu khớp đúng key thật đã thêm (`Pages.PmDashboard.aiFindingStats.*`)
- [ ] `impl-plan.md` mục "Gate trước implementation" được cập nhật phản ánh quyết định cuối cùng thật sự tại thời điểm code (nếu có thay đổi so với kế hoạch)
- [ ] Có thể truy vết từ AC → tên test method (comment/display name có nhắc AC ID) để phục vụ audit sau này

## 10. Release/Rollback Review

- [ ] Migration mới (version thật, không nhất thiết là `V511`) áp dụng thành công ở staging trước prod; rollback = `DROP TABLE tbl_fact_ai_finding_stat` (bảng mới hoàn toàn, không cần data migration khi rollback)
- [ ] Có thể rollback độc lập: gỡ hook trong `GithubWebhookService` không phá vỡ luồng webhook hiện có (guard/không side-effect nếu revert); gỡ component FE không phá `PMDashboardPage.tsx` nếu BE bị rollback trước (FE xử lý lỗi/absent gracefully nếu gọi API chưa deploy)
- [ ] Thứ tự rollout đúng: BE (migration + endpoint) deploy trước hoặc cùng lúc FE, không phải sau — tránh FE gọi API chưa tồn tại
- [ ] Nếu áp dụng Heavy Option (feature flag / tách deploy ghi-đọc) theo `impl-plan.md`, xác nhận đã triển khai đúng như tài liệu hoặc ghi rõ lý do không cần
- [ ] Không có thay đổi breaking nào tới các endpoint PM Dashboard hiện có (`summary`, `insights`, `tickets`, `template-usage`, `access`) — smoke test lại các endpoint này sau khi deploy
- [ ] Rollback riêng phần webhook hook (không rollback DB) không để lại trạng thái mồ côi/vi phạm invariant nào

## Severity Definition

| severity | meaning | required action |
|---|---|---|
| Blocker | Cannot be released | Must fix |
| Major | High probability of becoming a bug | Fix or accepted risk |
| Minor | Minor improvement | Optional |
| Question | Spec confirmation required | Open Issue |
| False Positive | Incorrect Report | Record Reason for Rejection |
| Accepted Risk | Accepted Risk | Record Impact/Owner/Deadline |
