# Impact Analysis

**Ticket ID**: REVIEW-FINDING-DENSITY
**Create date**: 2026-09-10 22:30:00
**Author**: nvt_dung
**Update date**: 2026-09-10 23:15:00

## 1. Change Content

Bổ sung chỉ số **Review Finding Density** (`tổng review finding hợp lệ / tổng changed lines × 1000`, đơn vị `findings/KLOC`) hiển thị trên PM Dashboard Ticket Detail. Thay đổi gồm 4 phần:

1. **Migration + changed-lines versioning**: thêm cột `head_sha` vào `tbl_fact_pull_request_changed_file`, đổi unique key từ `(pr_id, file_path_hash)` sang `(pr_id, head_sha, file_path)` (OI-002, Closed).
2. **GitHub GraphQL client mới** (cô lập trong `infrastructure/github/`) để lấy `reviewThreads`/`isResolved` — nguồn thread identity/resolved-status thật (OI-001, Closed).
3. **Finding classification + writer mới**: phân loại review thread thuộc state hợp lệ thành finding theo metadata-only (OI-003, Closed), ghi vào `tbl_fact_finding` (bảng đã tồn tại, chưa có writer).
4. **Density service mới, nhúng vào API hiện có**: tính density theo công thức tổng/tổng (không average per-PR), rule N/A khi denominator=0, rounding 1 chữ số `HALF_UP` (OI-004, Closed); kết quả nhúng vào response `GET /tickets/{ticketId}/detail` hiện có (không tạo endpoint riêng — xác nhận IMPL-OI-3), hiển thị trên `TicketDetailDrawer.tsx`.

## 2. Directly Affected Files

| file | reason | change type |
|---|---|---|
| `EDCAP_BE/.../ingestion/GitPrMetadataCollectorService.java` | Gắn thêm bước gọi Finding writer sau khi review/comment được đồng bộ; truyền `headSha` vào `PullRequestChangedFileUpsert` | Sửa (thêm bước, không đổi logic cũ) |
| `EDCAP_BE/.../adapter/GitPrMetadataCollectorJdbcAdapter.java` | Đổi `ON CONFLICT` target của `upsertPullRequestChangedFile` sang `(pr_id, head_sha, file_path)`; thêm SQL method mới cho Finding writer (insert/delete-by-pr/update-status) | Sửa + Thêm method mới |
| `EDCAP_BE/src/main/resources/db/migration/V519__add_head_sha_to_pull_request_changed_file.sql` (tên do impl-plan đặt) | Thêm cột `head_sha`, đổi unique constraint, backfill dữ liệu cũ | Thêm mới |
| `EDCAP_BE/.../infrastructure/github/` — GraphQL client mới (ví dụ `GithubReviewThreadAdapter`) + port mới (ví dụ `GithubReviewThreadPort`) | Fetch `reviewThreads`/`isResolved` qua GraphQL, cô lập chi tiết GraphQL khỏi application/web (NFR §6.7) | Thêm mới |
| `EDCAP_BE/.../config/WebClientConfig.java` | Có thể cần thêm bean `WebClient` mới nếu GraphQL endpoint (`/graphql`) khác base URL REST hiện tại của `githubWebClient` | Sửa (thêm bean, nếu cần) hoặc Không đổi (nếu tái dùng được) |
| Finding classifier + writer mới (package `application/usecase/ingestion`, tên lớp do impl-plan đặt) | Phân loại metadata-only + ghi `tbl_fact_finding`, theo pattern `AiFindingStatWriter` (`REQUIRES_NEW`) | Thêm mới |
| Density service mới (package `application/usecase/...`, tên lớp do impl-plan đặt) | Tính density theo công thức tổng/tổng, N/A rule, rounding HALF_UP scale=1 | Thêm mới |
| `PmDashboardService.java`, `PmDashboardDtos.java` (hiện có) | **Cập nhật (IMPL-OI-3, Resolved)**: không tạo controller/DTO riêng — `detail(...)` gọi thêm Density service, `PmDashboardTicketDetailDto` thêm field `reviewFindingDensity` | Sửa (thêm field vào DTO hiện có, không đổi field cũ) |
| `EDCAP_FE/src/pages/pm-dashboard/components/TicketDetailDrawer.tsx` | Render card/badge mới đọc từ `detail.reviewFindingDensity` (không thêm `useQuery` mới) | Sửa (thêm section, không đổi phần khác) |
| TypeScript type `PmDashboardTicketDetail` (interfaces/) | Thêm field `reviewFindingDensity` khớp DTO BE mới | Sửa (thêm field) |

## 3. Indirectly Affected Files

| file | reason | risk |
|---|---|---|
| `GitPrMetadataCollectorServiceTest.java` | Cần thêm test case cho bước finding classification mới, đảm bảo AC-REG-1 (hành vi cũ không đổi) | Trung bình — nếu không thêm test, regression của flow ingestion hiện có khó phát hiện |
| `GitPrMetadataCollectorJdbcAdapterTest.java` | Hiện chỉ có 2 test, không cover `upsertPullRequestChangedFile`/review CRUD — bắt buộc bổ sung trước khi đổi unique key | Cao — đổi `ON CONFLICT` target không có test bảo vệ có thể vỡ silent |
| `GithubPullRequestMetadataAdapterTest` (nếu tồn tại) | Không sửa method REST hiện có, nhưng cần test riêng cho GraphQL client mới | Thấp |
| Mọi consumer khác đọc `tbl_fact_pull_request_changed_file` theo khóa cũ `(pr_id, file_path_hash)` (nếu có, ví dụ báo cáo/service khác) | Đổi unique key có thể ảnh hưởng nếu có code khác dựa vào khóa cũ để dedupe | Cần grep toàn repo tìm usage khác trước khi đổi (đưa vào implementation step, chưa xác nhận ở Phase 3 này) |
| `docs/architecture/service-layer-map.md`, `repository-db-map.md`, `route-api-map.md`, `entrypoint-map.md`, `fe-be-contract-map.md` | Các doc này vốn đã stale (không mô tả `GitPrMetadataCollectorService`/`tbl_fact_finding`/`tbl_fact_review`), ticket này làm tăng thêm độ lệch nếu không cập nhật | Thấp cho chức năng, nhưng tăng nợ tài liệu — ghi nhận là rủi ro vận hành, không phải blocker |

## 4. Caller / Callee

| caller | callee | impact |
|---|---|---|
| GitHub Webhook / Poll job (không đổi) | `GitPrMetadataCollectorService.persistPullRequest` | Không đổi entry point, chỉ mở rộng logic bên trong |
| `persistPullRequest` | `GitPrMetadataCollectorJdbcAdapter.upsertPullRequestChangedFile` | Thêm tham số `headSha`, đổi conflict target |
| `persistPullRequest` | Finding writer mới (`@Component`, `REQUIRES_NEW`) | Gọi **sau** khi review/comment được ghi lại, **trước hoặc cùng lúc** dọn finding cũ theo `pr_id` (xem quyết định §9 impl-plan) |
| Finding writer mới | GraphQL client mới (qua port) | External HTTP call — phải chạy ngoài DB transaction chính theo `.claude/rules/20-architecture.md` |
| Finding writer mới | `GitPrMetadataCollectorJdbcAdapter` (method mới cho `tbl_fact_finding`) | Ghi finding trong transaction riêng (`REQUIRES_NEW`) |
| `PmDashboardService.detail(ticketId, caller)` (hiện có) | `ReviewFindingDensityService.calculate(ticketId)` (mới) | **Cập nhật (xác nhận IMPL-OI-3)**: không tạo controller riêng — service hiện có gọi thêm density service, kết quả nhúng vào `PmDashboardTicketDetailDto` |
| `TicketDetailDrawer.tsx` | `GET /tickets/{ticketId}/detail` (hiện có, không đổi endpoint, chỉ đổi response shape) | **Cập nhật**: không thêm `useQuery` mới — đọc `detail.reviewFindingDensity` từ prop/query đã có sẵn |

## 5. FE Impact

- Thêm 1 metric mới trên `TicketDetailDrawer.tsx`: giá trị `X findings/KLOC` hoặc `N/A`, kèm breakdown 2 trạng thái (`OPEN`/`RESOLVED`).
- Theo pattern `formatFirstCiPassRate` (`SummaryCards.tsx:121-126`): viết `formatReviewFindingDensity` — guard `totalChangedLines <= 0 → "N/A"`, else rounding 1 chữ số.
- Không đổi routing, không đổi layout tổng thể của Ticket Detail (spec §2.2 — chỉ thêm chỉ số vào màn hình hiện có).
- Cần xác nhận i18n key convention khi code thật (context.md ghi nhận `formatFirstCiPassRate` hiện dùng literal string, không qua i18n key) — không phải blocker, theo đúng pattern hiện có là đủ.
- Không có test FE hiện tại cho `SummaryCards.tsx`/`TicketDetailDrawer.tsx` — theo `.claude/rules/40-testing.md`, code mới thêm vào `src/` cần có test file mới (Vitest + Testing Library) cho hàm format mới, kể cả khi component chưa có test.

## 6. BE Impact

- **Ingestion flow**: mở rộng `persistPullRequest`, không đổi hành vi cũ ngoài phạm vi OI-002 (AC-REG-1).
- **Infrastructure**: GraphQL client mới hoàn toàn, cô lập trong `infrastructure/github/`, không lộ DTO/response GraphQL ra `application`/`web` (NFR §6.7).
- **Application**: Finding classifier (pure logic, metadata-only) + Finding writer (isolated bean, `REQUIRES_NEW`, theo `AiFindingStatWriter`); Density service (theo pattern `scoreByRatio` nhưng scale=1, N/A rule khác `ZERO` fallback).
- **Transaction boundary**: external HTTP call (GraphQL) phải chạy ngoài DB transaction chính; mỗi finding upsert dùng `TransactionTemplate`/`REQUIRES_NEW` riêng, không share transaction giữa nhiều item (`.claude/rules/20-architecture.md`).
- **Web**: **cập nhật (IMPL-OI-3, Resolved)** — không thêm controller/DTO mới; mở rộng `PmDashboardTicketDetailDto` hiện có, tái dùng `@CurrentUser AuthUserContext caller` và permission gate đã có của `GET /tickets/{ticketId}/detail`.

## 7. API Contract Impact

| endpoint | request impact | response impact | backward compatible? |
|---|---|---|---|
| `GET /api/v1/pm/dashboard/tickets/{ticketId}/detail` (hiện có) | Không đổi request | **Cập nhật (IMPL-OI-3, Resolved)**: response thêm field mới `reviewFindingDensity: { density: number \| null, unit: "findings/KLOC", totalFindings, totalChangedLines, breakdown: { open, resolved } }`; không có endpoint riêng nào được tạo | Có — chỉ **thêm field mới** vào object JSON hiện có, không đổi/xóa field cũ, client cũ bỏ qua field lạ an toàn |

## 8. DTO / Schema / Validation Impact

- `PullRequestChangedFileUpsert` (record trong `GitPrMetadataCollectorModels`): thêm field `headSha` (String, not null sau migration).
- `PmDashboardDtos.PmDashboardTicketDetailDto` (hiện có): thêm field `reviewFindingDensity` — object hoặc `null`-safe, `density` bên trong là số hoặc `null` (không phải chuỗi `"N/A"` — FE tự format, theo spec §5.3 UI states).
- Validation: `ticketId` path param của `/detail` đã được validate bởi flow hiện có (`NotFoundException` nếu không tìm thấy ticket) — không cần thêm validation mới vì tái dùng endpoint cũ.
- Không có input body nào cần validate (endpoint `/detail` là GET, read-only).

## 9. DB / Migration Impact

- **Migration mới `V519`**: `ALTER TABLE tbl_fact_pull_request_changed_file ADD COLUMN head_sha VARCHAR(64)`, drop `uq_pr_changed_file`, thêm `CONSTRAINT uq_pr_changed_file UNIQUE (pr_id, head_sha, file_path)`.
- **Backfill (Resolved)**: đã đọc `tbl_fact_pull_request` (V4 dòng 535-558) và xác nhận **không có cột `head_sha`/head-commit nào** để backfill chính xác từ dữ liệu hiện có → backfill row cũ bằng placeholder sentinel `'legacy'` (user đã xác nhận). Row mang `head_sha='legacy'` được hiểu là snapshot lịch sử trước khi versioning tồn tại, không đối chiếu được với PR version cụ thể nhưng vẫn cộng dồn đúng vào density tổng.
- **`tbl_fact_finding`**: không đổi schema. Enum dùng chung được giữ nguyên; Review Finding Density chỉ tạo/hiển thị `OPEN` và `RESOLVED`.
- **FK `tbl_fact_finding.review_id`/`review_comment_id`**: **giữ nguyên, không sửa** (`NO ACTION`, không `CASCADE`) — theo quyết định đã chốt, Finding writer tự quản lý vòng đời finding của nó (xóa/ghi lại theo `pr_id` trước khi review cha bị xóa-chèn-lại), không dựa vào cascade DB.
- **Rollback**: `V519` down = drop cột `head_sha`, khôi phục unique constraint cũ `(pr_id, file_path_hash)` — chấp nhận mất `head_sha` đã backfill nếu rollback sau khi có dữ liệu mới ghi theo khóa mới (ghi rõ trong impl-plan §7.2).

## 10. Batch / Job / Event Impact

- Không có batch/job mới — mở rộng job ingestion hiện có (`persistPullRequest`, được trigger bởi GitHub Webhook/Poll, không đổi).
- Không thêm event mới ở tầng message queue (không tồn tại trong kiến trúc hiện tại theo phạm vi đã đọc).
- 3 log event mới (Finding created, Finding status changed, Density recalculated) là **log**, không phải job/event nghiệp vụ riêng — xem mục 12.

## 11. Test Impact

- **Bắt buộc thêm trước khi sửa `upsertPullRequestChangedFile`**: test JDBC adapter cho conflict-target mới (insert 2 lần cùng `pr_id`+`head_sha`+`file_path` khác `additions/deletions` → update; khác `head_sha` → 2 row riêng).
- **Unit test mới**: Finding classifier (AC-FINDING-CLASSIFY-1..5), Density service (AC-DENSITY-CALC-1..6), Finding status transition (AC-STATUS-1,2,5), Recalculation (AC-RECALC-1,2).
- **Integration test**: Finding writer ghi thật vào `tbl_fact_finding` qua `REQUIRES_NEW`; `GET /tickets/{ticketId}/detail` trả đúng field `reviewFindingDensity` cho ticket có/không có PR liên kết (contract test cần cập nhật vì response thay đổi — xem §7).
- **Regression test (AC-REG-1)**: `GitPrMetadataCollectorServiceTest` hiện có phải tiếp tục pass sau khi thêm bước finding classification — không sửa test cũ, chỉ thêm test mới.
- **FE test**: thêm Vitest test cho `formatReviewFindingDensity` (theo pattern test tương tự nếu có cho `formatFirstCiPassRate`; hiện tại không có test FE nào cho `SummaryCards.tsx` — đây là gap chung, không phải gap riêng của ticket này, nhưng code mới vẫn cần có test theo `.claude/rules/40-testing.md`).

## 12. Operation / Monitoring Impact

Theo spec §5.X và NFR §6.4, 3 log event bắt buộc:

| Event | Khi ghi | Payload | Chú ý |
|---|---|---|---|
| Finding created | Review comment/thread được phân loại là finding | `pr_id`, `head_sha`, review/comment gốc, thời điểm phát hiện | Không log raw GitHub user id — dùng `member_key`/pseudonym |
| Density recalculated | PR có commit mới, density tính lại | `pr_id`, `head_sha` cũ/mới, density trước/sau | Không log nội dung diff/source code |

Log level/format cụ thể theo convention hiện có trong `docs/standards/logging.md` (đối chiếu ở implementation, không đóng ở đây).

## 13. Rollout / Rollback Impact

- **Rollout**: migration `V519` chạy trước khi deploy code mới (Flyway tự động ở BE); code Finding writer/Density service có thể deploy cùng migration vì không có consumer cũ phụ thuộc field mới.
- **Feature flag**: spec không yêu cầu feature flag; nếu cần giảm rủi ro rollout (ví dụ GraphQL rate limit ảnh hưởng ingestion), cân nhắc thêm flag bật/tắt Finding classification riêng — quyết định cụ thể để ở impl-plan §7.3 (không chốt sẵn ở đây vì ngoài phạm vi spec).
- **Rollback**: revert code theo thứ tự ngược Part D → C → B → A; DB rollback migration `V519` chấp nhận mất `head_sha` mới backfill (xem mục 9).

## 14. Areas Determined to be Unaffected and Based on

| area | judgment | evidence |
|---|---|---|
| `tbl_fact_ai_finding_stat` / `AiFindingStatJdbcAdapter` | Không đổi, không đọc/ghi | Spec §2.2 cấm rõ dùng làm nguồn cho density; ticket này không chạm bảng này |
| Các dashboard khác (Security/QA/Dev Dashboard) | Không đổi | Route/controller riêng biệt (`SecurityDashboardController`, `QaDashboardController`, `DevDashboardController`), không share code với thay đổi này |
| Permission model hiện có (`AuthUserContext`, role gate PM) | Không đổi cơ chế, chỉ tái sử dụng | Density nhúng vào `GET /tickets/{ticketId}/detail` (IMPL-OI-3, Resolved) → tự động thừa hưởng `@CurrentUser AuthUserContext` + pattern `requirePm` hiện có, không tạo cơ chế phân quyền mới |
| FE routing (`/:lang/...`) | Không đổi | Density hiển thị trong component con của `TicketDetailDrawer.tsx`, không thêm route mới |
| Logic đồng bộ PR/commit/changed-file hiện có (ngoài phạm vi `head_sha`) | Không đổi | `persistPullRequest` chỉ được **thêm** bước mới, không sửa logic upsert PR/commit (AC-REG-1) |

## 15. Required Options

Tất cả các option trước đây (route API, backfill `head_sha`, cấu hình GraphQL client) đã được user chốt — xem mục 16. Không còn option nào cần chọn thêm trước khi implementation.

## 16. Human Decision Required

Toàn bộ 5 điểm sau đã được **user xác nhận** (không còn cần quyết định thêm trước Part A-D):

- **FK/delete-reinsert conflict**: Finding writer tự quản lý vòng đời finding theo `(pr_id, head_sha, thread_id)`, tự dọn dẹp trước bước review bị xóa-chèn-lại; không sửa FK `tbl_fact_finding` sang `ON DELETE CASCADE`.
- **Backfill `head_sha`** (IMPL-OI-2/8): `tbl_fact_pull_request` xác nhận không có cột head-sha nào → backfill row cũ bằng placeholder sentinel `'legacy'`.
- **API route** (IMPL-OI-3): không tạo endpoint riêng — nhúng `reviewFindingDensity` vào response của `GET /tickets/{ticketId}/detail` hiện có.
- **GraphQL auth** (IMPL-OI-4): dùng chung token hiện tại (`props.connectors().github().apiToken()`), chỉ cần base URL `/graphql` riêng.
- **Test gap** (IMPL-OI-7): bổ sung test JDBC adapter còn thiếu là bắt buộc, thực hiện ở Part A step A.1 trước khi đổi unique key.


## 17. Risk Summary

1. **FK/delete-reinsert conflict** (phát hiện mới, không có trong spec-pack): đã xử lý bằng quyết định thiết kế ở mục 16 — rủi ro còn lại là thứ tự tác vụ (finding cleanup vs review delete) phải đúng trong cùng transaction/step, cần review kỹ ở code review.
2. **GraphQL rate limit khác REST** (spec §9 rủi ro #1): Trung bình/Trung bình — cần đánh giá ở Part B trước khi bật classification cho toàn bộ PR.
3. **Backfill `head_sha` bằng placeholder `'legacy'`** (spec §9 rủi ro #2, đã Resolved): dữ liệu lịch sử không đối chiếu được theo phiên bản PR cụ thể — chấp nhận được vì là dữ liệu trước khi ticket tồn tại, không phải blocker.
4. **Metadata-only false positive** (spec §9 rủi ro #3): Cao/Trung bình — rủi ro đã được xác nhận chấp nhận, không phải blocker.
5. **Test gap hiện có** (`GitPrMetadataCollectorJdbcAdapterTest` không cover changed-file/review CRUD, IMPL-OI-7 Resolved): Trung bình/Cao — phải vá ở step A.1 trước khi đổi unique key để tránh regression âm thầm.
6. **Response contract của `GET /tickets/{ticketId}/detail` thay đổi** (do quyết định nhúng thay vì endpoint riêng, IMPL-OI-3 Resolved): Thấp (chỉ thêm field, không phá field cũ) — cần cập nhật test snapshot/contract test hiện có nếu có.
7. **Doc drift** (`docs/architecture/*.md` stale): Thấp/Trung bình về vận hành lâu dài — không block ticket này, nhưng nên ghi nhận backlog cập nhật doc riêng.
