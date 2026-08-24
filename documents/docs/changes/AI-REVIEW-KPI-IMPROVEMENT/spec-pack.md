# spec-pack

**Ticket ID**: AI-REVIEW-KPI-IMPROVEMENT
**Title**: Ai review api improvement
**Change type**: feature | **Mode**: Standard | **System shape**: FE + BE + DB
**Create date**: 2026-08-18 | **Author**: SDD Analyst (Claude)
**Update**: 2026-08-18 — toàn bộ 8 Open Issues (OI-AIRKI-1..8) và 4 Human Decisions (H-AIRKI-1..4) đã được người dùng xác nhận; xem Decision Log ở §18. Các mục tương ứng đã được chuyển từ Assumptions sang thiết kế chính thức trong §7-§12.

ID prefix dùng trong tài liệu này: `AIRKI` = AI-REVIEW-KPI-IMPROVEMENT.

## 1. Tổng quan

Bổ sung khả năng tự động đọc 5 chỉ số KPI review AI (đang nằm rải rác dạng text trong bảng `## 8. Số liệu thống kê` của mỗi `ai-review.md`) mỗi khi một Pull Request được merge, lưu lại theo từng ticket, và hiển thị tổng hợp theo repository trên PM Dashboard. Việc trích xuất bảng phải **độc lập ngôn ngữ** (tài liệu hiện là tiếng Việt, có thể chuyển sang tiếng Anh/Nhật sau này) và việc ghi dữ liệu phải **không trùng lặp** khi cùng một ticket được merge PR nhiều lần.

## 2. Bối cảnh / mục tiêu

- Hiện tại không có gì đọc bảng §8 một cách tự động — số liệu chỉ tồn tại dạng text trong Markdown, PM phải đọc tay từng ticket để tổng hợp.
- Mục tiêu nghiệp vụ: có được 5 rate tổng hợp theo repository (`Blocker/Major Resolution Rate`, `AI Review Adoption Rate`, `AI Review Valid Finding Rate`, `AI False Positive Rate`, `AI Finding Resolution Rate`) hiển thị trực tiếp trên PM Dashboard, cập nhật tự động theo webhook GitHub, không cần thao tác thủ công.
- Ràng buộc kỹ thuật cốt lõi: label cột/dòng trong bảng là text đã dịch, không ổn định giữa các ngôn ngữ — **đã có bằng chứng thực tế** ngay cả giữa 2 tài liệu tiếng Việt (template gốc ghi "Tỷ lệ xử lý finding nghiêm trọng (Blocker)", tài liệu mẫu thực tế ghi "Tỷ lệ Blocker được xử lý") — nên không được dùng text label làm khóa tra cứu.
- Ràng buộc nghiệp vụ cốt lõi: bảng §8 là **snapshot lũy kế** của ticket tại thời điểm merge (đã bao gồm mọi vòng review trước), không phải sự kiện rời rạc — ghi dữ liệu phải là ghi đè (upsert), không được cộng dồn.

## 3. Phạm vi

- Parse bảng `## 8. Số liệu thống kê` trong `docs/changes/{ticket-id}/ai-review.md` khi PR chứa file này được merge, qua `GithubWebhookService.handlePullRequest`.
- Lưu 5 cặp numerator/denominator vào bảng fact mới `tbl_fact_ai_finding_stat`, 1 row/ticket, upsert theo `ticket_id`.
- API mới trên PM Dashboard trả về 5 rate tổng hợp theo repository.
- Hiển thị 5 rate đó trên `PMDashboardPage.tsx` bằng component mới, theo đúng mẫu `TemplateUsageByPhase`.
- Đảm bảo không double-count khi: (a) ticket merge PR thứ 2 cập nhật `ai-review.md`, (b) GitHub redeliver webhook trùng.

## 4. Ngoài phạm vi

- Không sửa `MarkdownParserCore` hay cấu trúc chung của `_ticket-template/ai-review.md`.
- Không thêm cơ chế machine-readable key (ví dụ HTML comment `<!-- metric: ... -->`) cho từng dòng bảng — đây là giải pháp dài hạn hơn, positional-mapping là đủ cho ticket này (xem Assumption A-AIRKI-1).
- Không backfill dữ liệu cho các ticket đã merge **trước khi** tính năng này được triển khai (xem Open Issue OI-AIRKI-8).
- Không thêm filter theo `periodKey`/thời gian cho API mới ở phiên bản này (xem Assumption A-AIRKI-4 / Open Issue OI-AIRKI-3).
- Không xây dựng lại toàn bộ CRUD UI (tạo/sửa/xoá thủ công) cho bảng mới — đây là dữ liệu chỉ do hệ thống ghi tự động từ webhook.
- Không đổi cơ chế xác thực webhook hiện có (HMAC-SHA256) — tính năng mới chạy bên trong flow đã được xác thực.

## 5. Thuật ngữ nghiệp vụ / tiền đề

| Thuật ngữ | Định nghĩa |
|---|---|
| Ticket | Một đơn vị công việc, định danh bởi `ticket-id` (ví dụ `AI-REVIEW-KPI-IMPROVEMENT`), có 1 file `ai-review.md` trong `docs/changes/{ticket-id}/` |
| `ai-review.md` §8 | Bảng thống kê 7 dòng theo `_ticket-template/ai-review.md`, dòng 0-1 không dùng cho tính năng này, dòng 2-6 tương ứng 5 KPI mục tiêu |
| Numerator/Denominator pair | Với mỗi KPI, lưu cặp (số đạt được, tổng số) thay vì % đã tính sẵn, để có thể `SUM(numerator)/SUM(denominator)` đúng khi gộp nhiều ticket |
| Snapshot ghi đè (upsert) | Mỗi ticket chỉ có đúng 1 row trong bảng fact mới; mỗi lần parse thành công sẽ ghi đè toàn bộ 5 cặp giá trị của row đó |
| Not-applicable / no-data | Khi ô giá trị trong bảng không chứa được pattern số (`n/m (x%)`, `x%`) — ví dụ "không áp dụng", "chưa có dữ liệu" — numerator/denominator của KPI đó lưu là `NULL`, loại khỏi mẫu số khi tổng hợp |
| Positional mapping | Xác định metric theo **vị trí dòng cố định** trong bảng (index 2-6), không theo text label của dòng |

**Tiền đề** (điều kiện phải đúng để thiết kế này hoạt động, không phải điều tự suy ra được):
- Thứ tự 7 dòng trong bảng §8 giữ nguyên theo `_ticket-template/ai-review.md` hiện tại, kể cả khi dịch sang ngôn ngữ khác.
- Heading `## 8.` giữ nguyên số thứ tự "8." dù tiêu đề chữ có đổi ngôn ngữ.
- `ticketScopes` (built tại `GithubWebhookService.handlePullRequest`, dòng ~293-296) đã resolve được `ticket_id` (UUID) cho ticket key trích từ path trước khi feature mới chạy.

## 6. Acceptance Criteria

> Toàn bộ AC dưới đây viết theo dạng Given/When/Then để có thể test trực tiếp. AC nào phụ thuộc quyết định con người chưa chốt được đánh dấu rõ.

- **AC-AIRKI-1** (Backend — parse thành công): Given một PR được merge (`action=closed`, `merged=true`) có diff chứa `docs/changes/{ticket-id}/ai-review.md` với bảng §8 hợp lệ theo `_ticket-template`, When `handlePullRequest` xử lý PR đó, Then hệ thống upsert đúng 1 row vào `tbl_fact_ai_finding_stat` với `ticket_id` tương ứng và 5 cặp numerator/denominator khớp với nội dung file.
- **AC-AIRKI-2** (Backend — độc lập ngôn ngữ, heading): Given `ai-review.md` có heading section viết bằng ngôn ngữ khác tiếng Việt (ví dụ `## 8. Statistics` hoặc `## 8. 統計データ`) nhưng vẫn giữ số thứ tự `8.`, When parser tìm section, Then vẫn định vị đúng section và extract đúng bảng (test bằng cách giả lập input EN/JA).
- **AC-AIRKI-3** (Backend — độc lập ngôn ngữ, label dòng): Given bảng §8 có label dòng bị diễn đạt lại khác với `_ticket-template` (ví dụ thực tế: "Tỷ lệ Blocker được xử lý" thay vì "Tỷ lệ xử lý finding nghiêm trọng (Blocker)"), When parser map giá trị theo vị trí dòng (index 2-6), Then vẫn gán đúng giá trị cho đúng KPI, không phụ thuộc text label — dùng chính file mẫu thực tế `raw/ai-review.md` làm test fixture.
- **AC-AIRKI-4** (Backend — giá trị not-applicable): Given một dòng trong bảng có giá trị không chứa pattern số hợp lệ (ví dụ "không áp dụng", "⬜ chưa có dữ liệu"), When parser xử lý dòng đó, Then numerator/denominator của KPI đó lưu là `NULL` (không phải `0`), và toàn bộ quá trình xử lý PR không bị lỗi/không bị chặn lại.
- **AC-AIRKI-5** (Backend — chống double-count, merge lại): Given ticket X đã có sẵn 1 row trong `tbl_fact_ai_finding_stat`, When một PR khác của cùng ticket X được merge với `ai-review.md` đã cập nhật giá trị mới, Then bảng vẫn chỉ có đúng 1 row cho ticket X, với giá trị là bản mới nhất (không cộng dồn, không có row thứ 2).
- **AC-AIRKI-6** (Backend — chống double-count, webhook redelivery): Given GitHub gửi lại (redeliver) cùng một webhook payload đã xử lý thành công trước đó, When `handlePullRequest` chạy lại, Then kết quả trong `tbl_fact_ai_finding_stat` giống hệt như chỉ xử lý 1 lần (idempotent).
- **AC-AIRKI-7** (Backend — cô lập lỗi): Given việc ghi vào `tbl_fact_ai_finding_stat` thất bại (ví dụ `DataAccessException`), When lỗi xảy ra, Then toàn bộ transaction chính của `handlePullRequest` (các bước khác: collector, security evidence, template-usage, …) vẫn hoàn tất bình thường, lỗi chỉ được log WARN/ERROR.
- **AC-AIRKI-8** (Backend — fetch lỗi có thể bỏ qua): Given việc fetch blob `ai-review.md` trả về HTTP 401/403/404, When xử lý ticket đó, Then hệ thống skip ticket này (không throw exception làm fail toàn bộ webhook), log WARN.
- **AC-AIRKI-9** (Backend — API tổng hợp theo repository): Given ticket thuộc repository `repositoryId` đã có dữ liệu trong `tbl_fact_ai_finding_stat`, When gọi `GET /api/v1/pm/dashboard/ai-finding-stats?projectId=...&repositoryId=...` (cả hai tham số bắt buộc), Then response trả về **1 object duy nhất** với rate mỗi KPI tính bằng `ROUND(SUM(numerator)*100.0/SUM(denominator), 1)` gộp trên toàn bộ ticket thuộc repository đó, trả `null` cho một rate nếu tổng denominator của rate đó (trên tất cả ticket trong scope) = 0 — tức là không có ticket nào trong repository có dữ liệu cho KPI đó (xác nhận H-AIRKI-7).
- **AC-AIRKI-10** (Backend — permission): Given user không có role `PM` (scoped đúng `projectId`) và không có role `ADMIN`, When gọi API `ai-finding-stats`, Then trả về lỗi 403 theo `ErrorResponse` chuẩn (`Component.Permission.Denied`).
- **AC-AIRKI-11** (Frontend — hiển thị): Given API trả về object rate cho repository đã chọn, When user mở `PMDashboardPage.tsx` với `projectId`+`repositoryId` đã chọn, Then màn hình hiển thị section mới liệt kê 5 rate của repository đó, dùng pattern card-grid kiểu `SummaryCards` (đã cập nhật theo `OI-AIRKI-14`, 2026-08-20 — `CDataTable` không phù hợp cho response 1 object duy nhất; implementation thật là `AiFindingStatsCard.tsx`), hiển thị `-` cho rate `null`.
- **AC-AIRKI-12** (Frontend — AC Closure): Section mới phải được mount thực tế trong `PMDashboardPage.tsx` (không chỉ tồn tại như component cô lập) — verify bằng test tích hợp `PMDashboardPage.test.tsx` theo đúng convention hiện có (test DOM ordering + tham số gọi API).

> **Quyết định đã chốt (H-AIRKI-2, 2026-08-18)**: `repositoryId` là tham số **bắt buộc**, giống hệt shape của `template-usage` — mỗi lần gọi trả về đúng 1 object cho 1 repository, không trả list nhiều repository. AC-AIRKI-9/11 ở trên đã phản ánh quyết định này.

## 7. Input / Output

**Input:**
- GitHub webhook `pull_request` event payload: `action`, `pull_request.merged`, danh sách file thay đổi trong PR (đã có sẵn cơ chế lấy trong `handlePullRequest`).
- Nội dung blob `docs/changes/{ticket-id}/ai-review.md` (UTF-8 Markdown), fetch qua `artifactScannerSourcePort.resolveRevision → listTree → readBlob` (pattern có sẵn).
- Tham số filter API: `projectId` (bắt buộc), `repositoryId` (**bắt buộc**, xác nhận H-AIRKI-2).

**Output:**
- 1 row upsert trong `tbl_fact_ai_finding_stat` (5 cặp numerator/denominator + FK `project_id`/`repository_id`/`ticket_id`).
- JSON response từ API `ai-finding-stats`: 1 object rate cho repository được chỉ định (5 field rate + tên/ID repository).
- UI: bảng mới trên PM Dashboard hiển thị 5 rate theo repository.

## 8. Ảnh hưởng màn hình / API / DB / Batch / Event

**Màn hình:**
- `EDCAP_FE/src/pages/pm-dashboard/PMDashboardPage.tsx` — thêm 1 section mới (component mới), không sửa section hiện có.

**API:**
- Mới: `GET /api/v1/pm/dashboard/ai-finding-stats?projectId={projectId}&repositoryId={repositoryId?}` — thêm method mới trong `PmDashboardController`, `PmDashboardService`, `PmDashboardRepositoryPort`, `PmDashboardJdbcAdapter` (mirror đúng theo `template-usage`).

**DB:**
- Migration mới `V512__add_ai_finding_stat_tracking.sql` (đổi từ `V511` sang `V512` ngày 2026-08-20 sau khi merge nhánh `main` — `V511` đã bị chiếm bởi `V511__ai_quality.sql` của 1 ticket khác; xem `open-issues.md` Resolution Log), tạo bảng `tbl_fact_ai_finding_stat`.
- Cột đã chốt (xác nhận H-AIRKI-4, 2026-08-18: dùng schema **tối giản**, không audit/soft-delete đầy đủ):
  ```
  id                                  UUID PK DEFAULT gen_random_uuid()
  project_id                          UUID NOT NULL REFERENCES tbl_dim_project(project_id)
  repository_id                       UUID NOT NULL REFERENCES tbl_dim_repository(repository_id)
  ticket_id                           UUID NOT NULL UNIQUE REFERENCES tbl_dim_ticket(ticket_id)
  blocker_major_resolved_count        INTEGER NULL
  blocker_major_total_count           INTEGER NULL
  ai_review_adopted_count             INTEGER NULL
  ai_review_finding_total_count       INTEGER NULL
  ai_review_valid_count               INTEGER NULL
  ai_review_false_positive_count      INTEGER NULL
  ai_review_resolved_count            INTEGER NULL
  created_at                          TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at                          TIMESTAMPTZ NOT NULL DEFAULT now()
  ```
  (Lưu ý: `ai_review_finding_total_count` dùng chung làm denominator cho 4 KPI — adoption/valid/false-positive/resolution — theo đúng mô tả nghiệp vụ trong `raw/requirement.md` §3, vì cả 4 KPI này đều chia trên cùng tổng số finding của ticket; `blocker_major_total_count` tách riêng vì denominator khác — chỉ đếm finding mức Blocker/Major. Xác nhận H-AIRKI-1 (2026-08-18): dòng này xử lý như một số liệu **tổng hợp chung cho Blocker + Major** ở phiên bản hiện tại của `ai-review.md`; việc tách riêng Blocker và Major (nếu cần) sẽ do một thay đổi khác đối với format output của `ai-review.md`, ngoài phạm vi ticket này.)
- Index: `uq_ai_finding_stat_ticket` (UNIQUE trên `ticket_id`, đã đủ vì mỗi ticket chỉ có đúng 1 row), index thường trên `(project_id, repository_id)` để tối ưu API tổng hợp.
- Trigger `trg_ai_finding_stat_updated_at` cập nhật `updated_at` (theo convention `database.md`).

**Batch:** Không có — xử lý theo sự kiện webhook, không có job định kỳ.

**Event:**
- Không thêm entrypoint webhook mới. Thêm logic xử lý bên trong `GithubWebhookService.handlePullRequest`, **đặt sau đoạn build `ticketScopes`** (hiện ở dòng ~293-296), khác với `validateTemplateUsage` (đặt trước, dòng ~253-256, chạy trước khi `ticketScopes` tồn tại) — bắt buộc đặt sau vì cần `ticketScopes.get(ticketKey).ticketId()` (UUID) làm khóa upsert.
- **Quyết định chốt (H-AIRKI-5, người dùng ủy quyền SDD analyst)**: giữ nguyên vị trí hook như phân tích kỹ thuật ở trên — ngay sau vòng lặp build `ticketScopes` (dòng ~296), trước các bước xử lý còn lại của `handlePullRequest` có thể phụ thuộc `ticketScopes`. Đây là vị trí duy nhất thỏa cả 2 điều kiện: (a) có `ticketId` đã resolve, (b) không làm thay đổi thứ tự của `validateTemplateUsage` hay các side-effect khác đã tồn tại trước đó trong hàm. Không cần thay đổi thứ tự các bước hiện có, chỉ chèn thêm logic mới.
- Tái sử dụng writer bean riêng biệt kiểu `TemplateUsageStatWriter`, với `@Transactional(propagation = Propagation.REQUIRES_NEW)`, để lỗi ghi không rollback transaction chính.

## 9. FE/BE contract

**Request:**
```
GET /api/v1/pm/dashboard/ai-finding-stats?projectId={UUID}&repositoryId={UUID}
```
- `projectId`: bắt buộc (giống mọi endpoint PM Dashboard khác).
- `repositoryId`: **bắt buộc** (xác nhận H-AIRKI-2, 2026-08-18) — cùng shape cardinality với `template-usage`.

**Response** (`200 OK`, không bọc envelope, theo `api-contract.md`) — **1 object duy nhất** (không phải mảng, vì `repositoryId` đã xác định đúng 1 repository):
```json
{
  "repositoryId": "uuid",
  "repositoryName": "string",
  "blockerMajorResolutionRate": 87.5,
  "aiReviewAdoptionRate": 100.0,
  "aiReviewValidFindingRate": 100.0,
  "aiFalsePositiveRate": 0.0,
  "aiFindingResolutionRate": 100.0
}
```
- Mỗi field rate: `Double`, làm tròn 1 chữ số thập phân, `null` khi tổng denominator (gộp trên toàn bộ ticket của repository đó) = 0 — tức là không ticket nào trong repository có dữ liệu cho KPI đó (đúng convention `TemplateUsageDto.usageRate`, xác nhận H-AIRKI-7 → FE hiển thị `-`).
- Lỗi: theo `ErrorResponse` chuẩn (`timestamp, status, error, message, traceId` — 5 field, xác nhận trực tiếp qua `web/exception/ErrorResponse.java`, không có field `errorCode` riêng; `docs/standards/{security,api-contract,error-handling}.md` và `.claude/rules/30-security.md` mô tả sai shape này, xem `open-issues.md` cuối file), không có shape riêng cho endpoint này.

**FE:**
- `lib/api.ts`: thêm `pmDashboard.aiFindingStats(params: {projectId: string; repositoryId: string}) => api.get<AiFindingStats>(...)`, nested trong namespace `pmDashboard` hiện có (đúng convention đã thiết lập cho `templateUsage`).
- Interface mới `AiFindingStats` (đơn — không phải mảng) khai báo cạnh `TemplateUsageByPhase` trong `lib/api.ts`.
- Component mới `AiFindingStatsCard.tsx` (đổi tên/pattern theo `OI-AIRKI-14`, 2026-08-20 — không dùng `CDataTable` vì response là 1 object duy nhất, không phải danh sách hàng; dùng card-grid kiểu `SummaryCards.tsx` thay vì `TemplateUsageByPhase.tsx`), `useQuery` với query key `["pm-dashboard", "ai-finding-stats", projectId, repositoryId]`, `enabled` gate giống `templateUsage` query (chỉ fetch khi có cả `projectId` **và** `repositoryId`).
- i18n key namespace: `Pages.PmDashboard.aiFindingStats.*`.

## 10. Validation / Error / Message

- `projectId`/`repositoryId` thiếu trong query param → hệ thống trả `500 INTERNAL_ERROR` (không phải `400` như bản nháp trước đây giả định) — `GlobalExceptionHandler` không có `@ExceptionHandler` cho `MissingServletRequestParameterException` ở **bất kỳ** endpoint nào trong toàn hệ thống hiện tại, không riêng endpoint này. Xác nhận giữ nguyên hành vi 500 hiện có, không mở rộng `GlobalExceptionHandler` trong phạm vi ticket này (`OI-AIRKI-9`, 2026-08-19).
- `projectId` không tồn tại / user không có quyền PM/ADMIN trên project → `403 Forbidden`, `errorCode = Component.Permission.Denied` (dùng lại `requirePm(caller, projectId)` có sẵn).
- Không có case validate nghiệp vụ phức tạp khác — đây là API read-only tổng hợp, không có input ghi.
- Phía ghi (webhook): không trả lỗi ra ngoài cho end-user (webhook không có người dùng tương tác trực tiếp) — mọi lỗi parse/ghi chỉ log, không raise exception làm fail response webhook (webhook vẫn phải trả `200` cho GitHub theo hành vi hiện có).
- Message key hiển thị lỗi/label FE theo namespace `Pages.PmDashboard.aiFindingStats.*`, nhất quán với `Pages.PmDashboard.templateUsage.*`.

## 11. Security / Privacy / Permission / Audit

- **Permission**: endpoint mới bắt buộc qua `requirePm(caller, projectId)` — ADMIN luôn pass; role khác phải có `PM` scoped đúng `projectId`. Không có permission mới nào cần tạo.
- **Webhook auth**: không thay đổi — vẫn xác thực HMAC-SHA256 tại tầng `GithubWebhookController` trước khi vào `handlePullRequest`; feature mới không mở thêm entrypoint.
- **Privacy/PII**: dữ liệu lưu chỉ là số đếm (numerator/denominator), không lưu text tự do từ cột "Ghi chú" của bảng §8 (cột này có thể chứa tên người/ghi chú nghiệp vụ) → không có rủi ro PII từ việc lưu trữ.
- **Owner/Author display rule** (`security.md`): API mới **không** trả về bất kỳ trường owner/author/assignee nào → rule "Pseudonym Only" không áp dụng cho tính năng này (ghi rõ để review không hiểu nhầm là thiếu sót).
- **Audit**: theo schema tối giản đã chốt (H-AIRKI-4, chỉ có `created_at`/`updated_at`) sẽ **không** có `created_by`/`updated_by`/lịch sử thay đổi cho bảng mới — đây là trade-off đã được người dùng xác nhận chấp nhận, vì dữ liệu luôn tái tạo được từ nguồn (`ai-review.md` trong Git) chứ không phải nguồn ghi duy nhất.

## 12. Operation / Logging / Monitoring / Recovery

- **Logging**: SLF4J + `{}` placeholder, 1 logger/class (theo `logging.md`). Log các sự kiện: bắt đầu parse (INFO, kèm `ticketId`, path), parse thành công (INFO, có ghi số field parse được), skip do lỗi fetch 401/403/404 (WARN, kèm status code — không log token/nội dung response), skip do không tìm thấy bảng/section hợp lệ (WARN), lỗi ghi DB (WARN/ERROR, không log toàn bộ nội dung `ai-review.md`).
- **Cấm log**: toàn bộ nội dung file `ai-review.md` (có thể dài, có thể chứa tên người ở cột Ghi chú) — chỉ log field đã parse (số), không log text gốc.
- **Monitoring**: có thể theo dõi tỷ lệ WARN "parse failed/skip" theo thời gian để phát hiện sớm khi cấu trúc bảng mẫu bị thay đổi phá vỡ parser (không có dashboard riêng trong scope ticket này, chỉ dựa vào log hiện có).
- **Recovery**: do cơ chế là upsert-ghi-đè theo trạng thái file hiện tại, hệ thống tự phục hồi khi có PR merge tiếp theo chạm lại `ai-review.md` — không cần replay/backfill job cho ticket bị lỗi tạm thời. **Xác nhận H-AIRKI-8 (2026-08-18): không cần backfill** cho ticket đã merge trước khi tính năng deploy — tính năng áp dụng ngay từ đầu dự án theo kế hoạch triển khai chung, chấp nhận gap dữ liệu cho các ticket lịch sử trước thời điểm go-live (nếu có).

## 13. Test Strategy Summary

- **Unit test — parser** (mới, domain layer): test positional/structural extraction với ít nhất 3 fixture: (1) label đúng như `_ticket-template`, (2) label bị diễn đạt lại như trong `raw/ai-review.md` thực tế, (3) label giả lập ngôn ngữ khác (EN/JA) — verify AC-AIRKI-2, AC-AIRKI-3, AC-AIRKI-4.
- **Unit test — writer bean mới** (mirror `TemplateUsageStatWriter`): verify upsert-ghi-đè (không tăng dần), verify `DataAccessException` bị catch không throw ra ngoài — verify AC-AIRKI-5, AC-AIRKI-7.
- **Unit test — `GithubWebhookServiceTest`**: verify writer mới được gọi đúng thời điểm (sau khi `ticketScopes` built), verify skip khi file không đổi trong diff, verify skip khi fetch trả 401/403/404 — verify AC-AIRKI-1, AC-AIRKI-6, AC-AIRKI-8. **Quyết định chốt (H-AIRKI-6)**: trước khi viết test mới, bắt buộc đọc toàn bộ 33 test case hiện có trong `GithubWebhookServiceTest.java` để tái dùng đúng mock/fixture pattern và tránh trùng lặp — đây là bước thủ tục bắt buộc trong impl-plan, không phải điểm cần quyết định thiết kế thêm.
- **Unit test — `PmDashboardService`/`PmDashboardJdbcAdapter` mới**: verify công thức `SUM(numerator)/SUM(denominator)`, verify `null` khi denominator tổng = 0, verify `requirePm` gate — verify AC-AIRKI-9, AC-AIRKI-10. Cùng quyết định H-AIRKI-6: đọc trước `PmDashboardServiceTest.java`/`PmDashboardJdbcAdapterFindTemplateUsageTest.java`/`PmDashboardControllerTest.java` hiện có trước khi viết test mới.
- **Migration test**: verify `UNIQUE(ticket_id)`, verify FK constraints trỏ đúng bảng dim.
- **FE test**: component test mới theo mẫu `TemplateUsageByPhase.test.tsx` (loading/empty/render/`-` khi null), và test tích hợp `PMDashboardPage.test.tsx` đảm bảo section mount đúng vị trí — verify AC-AIRKI-11, AC-AIRKI-12 (theo rule "AC Closure" của `testing.md`).
- **ArchUnit**: đảm bảo không phá vỡ `ArchitectureTest` hiện có (writer/parser mới phải nằm đúng layer domain/application/infrastructure).

## 14. Source Availability Summary

Xem đầy đủ tại `source.md`. Tóm tắt: source code hiện có (BE, FE, DB migration) và toàn bộ `docs/standards/*` liên quan đã đọc đầy đủ, độ tin cậy Confirmed. Còn 4 nhóm thông tin ở trạng thái Partial/chưa xác minh: (1) `docs/architecture/*` chưa đối chiếu chi tiết, (2) nội dung test hiện có (`GithubWebhookServiceTest`, PM Dashboard test) chưa đọc, (3) DDL gốc `tbl_dim_ticket/project/repository` chưa đọc trực tiếp (suy luận qua FK), (4) không có Office/PDF/OpenAPI spec chính thức nào tồn tại cho ticket này.

## 15. Complexity Classification

```
Complexity: Standard
System shape: FE + BE + DB
Primary risk: Implementation (đọc kỹ 33 test hiện có trong GithubWebhookServiceTest.java trước khi
              chèn logic mới vào handlePullRequest; giữ đúng vị trí hook sau ticketScopes) — mọi
              ambiguity về Spec/Contract đã được người dùng xác nhận (2026-08-18), không còn là rủi ro
              chính.
Review mode: Standard
Required options: DB Migration, FE-BE Contract, Backward-compatible parsing (không sửa template hiện có)
```

## 16. Assumptions

> Phần AI suy đoán/đề xuất, chưa được user xác nhận trực tiếp — tách riêng khỏi phần đã chốt. Các mục đã được xác nhận qua Human Decisions (2026-08-18) đã được chuyển khỏi mục này sang thiết kế chính thức ở §7-§13 — xem Decision Log ở §18.

- **A-AIRKI-1**: Thứ tự 7 dòng trong bảng §8 giữ nguyên theo `_ticket-template/ai-review.md` hiện tại kể cả khi ngôn ngữ đổi — không cần cơ chế machine-readable key (HTML comment) ở ticket này; nếu template đổi thứ tự trong tương lai, cần một ticket riêng để làm parser bền vững hơn.
- ~~**A-AIRKI-3**~~ — đã được xác nhận trực tiếp và chuyển thành quyết định chính thức, xem `H-AIRKI-9` ở §18 (không còn là suy đoán AI chưa hỏi).
- **A-AIRKI-7**: Ghi log ở mức parse (INFO khi thành công, WARN khi skip) là đủ cho vận hành ở giai đoạn đầu — không cần dashboard/alert riêng cho tỷ lệ parse-fail trong ticket này.

## 17. Open Issues

Toàn bộ 8 Open Issue (OI-AIRKI-1..8) đã được người dùng xác nhận ngày 2026-08-18 — xem `open-issues.md` mục "Resolution Log" để biết chi tiết từng câu trả lời. Không còn Open Issue nào đang chờ xử lý cho giai đoạn spec-pack; các quyết định đã được inline vào thiết kế chính thức tại §7-§13.

## 18. Human Decisions Required

Toàn bộ 4 quyết định dưới đây đã được xác nhận ngày 2026-08-18 — bảng này giữ lại làm Decision Log để impl-plan tham chiếu.

| ID | Quyết định | Kết quả xác nhận (2026-08-18) | Áp dụng tại |
|---|---|---|---|
| H-AIRKI-1 | Định nghĩa "Blocker/Major Resolution Rate" | Xử lý dòng này như 1 số liệu **tổng hợp chung Blocker + Major** ở phiên bản hiện tại; tách riêng Blocker/Major (nếu cần) sẽ do thay đổi khác đối với output `ai-review.md`, ngoài phạm vi ticket này | §8 (cột `blocker_major_*`) |
| H-AIRKI-2 | `repositoryId` bắt buộc hay optional | **Bắt buộc** — API trả về 1 object cho đúng 1 repository, giống shape `template-usage` | §6 (AC-AIRKI-9/11), §7, §9 |
| H-AIRKI-3 | Có cần `periodKey` filter ở v1 | **Không cần** — giữ nguyên thiết kế cumulative theo snapshot mới nhất | §3 Ngoài phạm vi |
| H-AIRKI-4 | Chọn DB schema pattern | **Tối giản** (kiểu `V510`, chỉ `created_at`/`updated_at`, không audit/soft-delete đầy đủ) | §8 (DDL), §11 (Audit) |
| H-AIRKI-5 (bổ sung) | Vị trí hook trong `handlePullRequest` | Ủy quyền SDD analyst quyết định theo đúng ràng buộc kỹ thuật — giữ vị trí ngay sau vòng lặp build `ticketScopes` | §8 (Event) |
| H-AIRKI-6 (bổ sung) | Có cần đọc test hiện có trước khi implement | Ủy quyền SDD analyst quyết định — xác nhận: bắt buộc đọc 33 test case `GithubWebhookServiceTest.java` + 3 file test PM Dashboard trước khi viết code/test mới | §13 |
| H-AIRKI-7 (bổ sung) | Rate = null xảy ra khi nào | Xác nhận: chỉ khi **tổng denominator = 0 trên toàn bộ ticket trong scope** (tức không ticket nào có dữ liệu cho KPI đó) — FE hiển thị `-` | §6 (AC-AIRKI-9), §9 |
| H-AIRKI-8 (bổ sung) | Có cần backfill ticket cũ | **Không cần** — tính năng áp dụng ngay từ đầu dự án theo kế hoạch triển khai chung | §12 (Recovery) |
| H-AIRKI-9 (bổ sung, xác nhận 2026-08-19 qua `OI-AIRKI-10`) | `A-AIRKI-3` — shared denominator `ai_review_finding_total_count` cho 4/5 KPI có đúng thiết kế không | **Giữ nguyên A-AIRKI-3**: `ai_review_finding_total_count` là denominator dùng chung cho adoption/valid/false-positive/resolution rate; `blocker_major_total_count` là denominator riêng cho Blocker/Major. DDL `V512` (đổi tên từ `V511` ngày 2026-08-20, xem Resolution Log `open-issues.md`) dùng nguyên cấu trúc cột này | §8 (DDL), §16 (A-AIRKI-3) |
| H-AIRKI-10 (bổ sung, xác nhận 2026-08-20 qua `OI-AIRKI-14`) | AC-AIRKI-11 — FE dùng `CDataTable` hay card-grid | **Card-grid kiểu `SummaryCards`** (không phải `CDataTable`) — implementation thật là `AiFindingStatsCard.tsx` | §6 (AC-AIRKI-11), §9 (FE) |
