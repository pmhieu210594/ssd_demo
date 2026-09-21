# Context

**Ticket ID**: REVIEW-FINDING-DENSITY
**Create date**: 2026-09-10 15:30:00
**Author**: nvt_dung
**Update date**: 2026-09-10 15:30:00

## Screen / API / Batch / Related Job

### Batch / ingestion job liên quan (đã tồn tại)

| job/service | path | vai trò hiện tại | thay đổi dự kiến |
|---|---|---|---|
| `GitPrMetadataCollectorService.persistPullRequest` | `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/ingestion/GitPrMetadataCollectorService.java:228` | Upsert PR/commit/changed-file, xóa-chèn lại review + review comment mỗi lần đồng bộ PR | Cần gắn thêm bước phân loại finding (metadata-only) sau bước insert review/review comment |
| `GitPrMetadataCollectorJdbcAdapter.upsertPullRequestChangedFile` | `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/GitPrMetadataCollectorJdbcAdapter.java` | INSERT `tbl_fact_pull_request_changed_file` ON CONFLICT `(pr_id, file_path_hash)` | Đổi khóa upsert sang `(pr_id, head_sha, file_path)` theo OI-002 (Closed) |

### Màn hình liên quan (đã tồn tại)

| màn hình | path | vai trò hiện tại | thay đổi dự kiến |
|---|---|---|---|
| PM Dashboard — Ticket Detail | `EDCAP_FE/src/pages/pm-dashboard/components/TicketDetailDrawer.tsx` | Chưa có chỉ số Review Finding Density | Thêm hiển thị `findings / KLOC` hoặc `N/A` + breakdown theo trạng thái finding |

### API / entry point

- **Chưa có API endpoint riêng cho Review Finding Density.** Cần tạo mới ở Phase 3 (impl-plan) — route cụ thể, method (GET), path param (ticket id) do impl-plan quyết định; không được tự suy diễn route ở tài liệu này.
- Không có GraphQL client nào tồn tại trong `GithubPullRequestMetadataAdapter`/`GithubPullRequestMetadataPort` hiện tại — client GraphQL mới (OI-001, Closed) là component **hoàn toàn mới**, chưa có entry point.

## Example of a correctly implemented code

| purpose | file/path | pattern to follow |
|---|---|---|
| Ratio/BigDecimal rounding pattern (BE) | `EvidenceQualityScoreService.scoreByRatio(int numerator, int denominator, BigDecimal maxScore)` — `EDCAP_BE/.../application/usecase/quality/EvidenceQualityScoreService.java:480` | `if (denominator <= 0 \|\| numerator <= 0) return ZERO;` rồi `BigDecimal.valueOf(...).setScale(n, RoundingMode.HALF_UP)`. Density mới cần scale = **1** (không phải 2 như evidence quality) theo OI-004 (Closed), và rule N/A áp dụng khi denominator = 0 (khác với rule trả `ZERO` của evidence quality — phải phân biệt rõ, không copy y nguyên) |
| Isolated writer bean, tránh self-invocation proxy bypass (BE) | `AiFindingStatWriter` — `EDCAP_BE/.../application/usecase/ingestion/AiFindingStatWriter.java` | `@Component` riêng, method `recordStat(...)` có `@Transactional(propagation = Propagation.REQUIRES_NEW)`; áp dụng khi viết Finding writer mới để tránh gọi nội bộ trong `GitPrMetadataCollectorService` bỏ qua Spring proxy |
| Delete-then-reinsert pattern theo PR (BE) | `GitPrMetadataCollectorService.persistPullRequest` dòng ~342 (`deleteReviewsByPrId`) + insert loop (~351-380) | Finding writer mới nên theo cùng nguyên tắc tái tạo theo `(pr_id, head_sha)` khi có phiên bản mới, không cộng dồn sai (spec §5.4) |
| Upsert JDBC pattern (BE) | `GitPrMetadataCollectorJdbcAdapter.upsertPullRequestChangedFile` | Dùng `NamedParameterJdbcTemplate` + `MapSqlParameterSource`, `ON CONFLICT (...) DO UPDATE`; đổi conflict target sang `(pr_id, head_sha, file_path)` khi thêm cột `head_sha` |
| Ratio + N/A display pattern (FE) | `formatFirstCiPassRate` — `EDCAP_FE/src/pages/pm-dashboard/components/SummaryCards.tsx:121-126` | `if (totalWithCi <= 0) return "N/A";` rồi `Math.round(rate * 10) / 10`. Đây là precedent FE trực tiếp cho cách hiển thị `findings/KLOC` hoặc `N/A` — nên viết helper cùng phong cách (`formatReviewFindingDensity`) thay vì tạo cơ chế hiển thị mới |

## Allowed common components

| component | path | usage note |
|---|---|---|
| `NamedParameterJdbcTemplate` + `MapSqlParameterSource` | Toàn bộ `GitPrMetadataCollectorJdbcAdapter` | Pattern JDBC chuẩn của adapter layer trong module này, dùng lại cho Finding/changed-line adapter mới |
| `formatXxxRate`-style pure function pattern | `SummaryCards.tsx` | Dùng lại pattern hàm format thuần (không side-effect) cho density display, đặt cùng khu vực với các hàm `formatXxxRate` khác nếu có |
| `@Transactional(propagation = Propagation.REQUIRES_NEW)` isolated writer bean | `AiFindingStatWriter` | Dùng lại cho Finding writer để đảm bảo transaction riêng biệt khi ghi bất đồng bộ với transaction chính của `persistPullRequest` (theo NFR §6.1 — external HTTP call ngoài DB transaction) |
| `tbl_dim_member_pseudonym` join qua `findMemberKeyByPseudonym`/`findMemberKeyByExternalUserHash` | `GitPrMetadataCollectorJdbcAdapter` | Bắt buộc dùng để xác định reviewer identity, không dùng raw user id (`docs/standards/security.md`, `.claude/rules/30-security.md`) |

## Forbidden common components

| component | reason |
|---|---|
| `tbl_fact_ai_finding_stat` / `AiFindingStatJdbcAdapter` (đọc dữ liệu) | Bảng này là KPI riêng cho AI review; spec-pack §2.2 cấm dùng làm nguồn cho Review Finding Density (human-only) |
| NLP/phân tích ngữ nghĩa nội dung comment (bất kỳ thư viện hoặc heuristic đọc hiểu văn bản nào) | OI-REVIEW-FINDING-DENSITY-003 (Closed) chốt finding eligibility chỉ dựa metadata; cấm dùng NLP để đoán LGTM/ACK/câu hỏi |
| Raw user id (không join `tbl_dim_member_pseudonym`) để xác định reviewer | Vi phạm `docs/standards/security.md` — reviewer identity phải qua pseudonym |

## List of methods that actually exist

| method/class | path | usage |
|---|---|---|
| `GitPrMetadataCollectorService.persistPullRequest(RepositoryScope, PullRequestGraph, String traceId)` | `GitPrMetadataCollectorService.java:228` | Entry point đồng bộ PR — nơi gắn bước phân loại finding mới |
| `GitPrMetadataCollectorService.normalizeReviewState(...)` | `GitPrMetadataCollectorService.java:683` | Chuẩn hóa review state → `APPROVED\|CHANGES_REQUESTED\|REVIEW_REQUIRED\|UNKNOWN`; classifier nhận ba state hợp lệ đầu tiên và loại `UNKNOWN` |
| `GitPrMetadataCollectorJdbcAdapter.deleteReviewsByPrId(prId)` | `GitPrMetadataCollectorJdbcAdapter.java` | Xóa toàn bộ review/comment cũ trước khi chèn lại |
| `GitPrMetadataCollectorJdbcAdapter.insertReview(...)` / `insertReviewComment(...)` | `GitPrMetadataCollectorJdbcAdapter.java` | Ghi `tbl_fact_review` / `tbl_fact_review_comment` |
| `GitPrMetadataCollectorJdbcAdapter.upsertPullRequestChangedFile(...)` | `GitPrMetadataCollectorJdbcAdapter.java` | Ghi `tbl_fact_pull_request_changed_file`; cần sửa để nhận thêm `head_sha` |
| `GitPrMetadataCollectorJdbcAdapter.findMemberKeyByPseudonym(...)` / `findMemberKeyByExternalUserHash(...)` | `GitPrMetadataCollectorJdbcAdapter.java` | Join `tbl_dim_member_pseudonym` để lấy `member_key` |
| `EvidenceQualityScoreService.scoreByRatio(int, int, BigDecimal)` | `EvidenceQualityScoreService.java:480` | Pattern tham chiếu duy nhất hiện có cho ratio/BigDecimal rounding trong codebase |
| `AiFindingStatWriter.recordStat(AiFindingStatRecord)` | `AiFindingStatWriter.java` | Pattern tham chiếu duy nhất cho isolated writer bean `REQUIRES_NEW` |
| `GithubPullRequestMetadataPort.fetchPullRequest(String repositoryFullName, int pullRequestNumber)` | `EDCAP_BE/.../application/port/out/integration/GithubPullRequestMetadataPort.java` | REST fetch — không có method GraphQL nào trên port này |
| `GithubPullRequestMetadataPort.listPullRequests(String repositoryFullName, boolean includeClosed)` | cùng file | REST list |
| `formatFirstCiPassRate(firstPass, totalWithCi)` | `SummaryCards.tsx:121-126` | Pattern FE N/A + rounding tham chiếu |

## Forbidden methods / methods that do not exist

| method/API | reason | alternative |
|---|---|---|
| Bất kỳ method GraphQL nào trên `GithubPullRequestMetadataAdapter`/`GithubPullRequestMetadataPort` (ví dụ giả định `fetchReviewThreads`, `isResolved`) | **Xác nhận không tồn tại** — adapter hiện tại REST-only (`/pulls/{number}`, `/commits`, `/files`, `/reviews`, `/comments`). Không được giả định đã có sẵn client GraphQL | Phải tạo mới một GraphQL client, cô lập trong infrastructure layer (NFR §6.7); đặt tên method mới ở Phase 3 (impl-plan), không đặt tên tùy tiện ở đây |
| Bất kỳ method nào ghi `tbl_fact_finding` (ví dụ giả định `insertFinding`, `upsertFinding`) | **Xác nhận không tồn tại** — không có writer nào cho bảng này trong toàn bộ codebase hiện tại | Phải tạo mới, theo pattern `AiFindingStatWriter` (isolated bean, `REQUIRES_NEW`) |
| Bất kỳ method tính density nào (ví dụ giả định `calculateDensity`, `DensityService`) | **Xác nhận không tồn tại** — chưa có bất kỳ logic density/KLOC nào trong hệ thống (spec-pack §4 dòng 5) | Phải tạo mới ở Phase 3 |
| `AiFindingStatJdbcAdapter` dùng làm nguồn đọc cho density | Bị cấm theo scope (xem "Forbidden common components") | Đọc trực tiếp `tbl_fact_finding` (mới) + `tbl_fact_pull_request_changed_file` |

## DTO / Entity / Table / Migration mapping

| layer | name | path | Note |
|---|---|---|---|
| Table | `tbl_fact_review` | `EDCAP_BE/src/main/resources/db/migration/V4__init_shema_v2.sql:570-588` | Cột chính: `review_id` PK, `pr_id` FK CASCADE, `ticket_id`, `reviewer_member_key` FK→`tbl_dim_member_pseudonym`, `reviewer_role_id`, `state review_state NOT NULL`, `source_actor_type_id`, `started_at`, `submitted_at`, `comment_count`, `collected_at` |
| Table | `tbl_fact_review_comment` | `V4__init_shema_v2.sql:590-608` | Cột chính: `review_comment_id` PK, `review_id` FK CASCADE, `pr_id` FK CASCADE, `ticket_id`, `commenter_member_key` FK, `actor_type_id`, `comment_hash`, `comment_summary`, `file_path_hash`, `line_number`, `category_id` FK→`tbl_dim_finding_category`, `severity severity_level`, `resolved_flag BOOLEAN` |
| Table | `tbl_fact_finding` | `V4__init_shema_v2.sql:610-631` | Cột chính: `finding_id` PK, `ticket_id` FK, `pr_id` FK, `review_id` FK, `review_comment_id` FK, `source_actor_type_id` FK, `category_id` FK, `severity severity_level NOT NULL DEFAULT 'MEDIUM'`, `status finding_status NOT NULL DEFAULT 'OPEN'`, `accepted_flag`, `false_positive_reason`, `finding_summary`, `source_location_hash`, `detected_at`, `resolved_at`. **Bảng đã tồn tại nhưng chưa có writer nào — mọi hàng hiện tại (nếu có) đều rỗng về mặt flow ghi dữ liệu** |
| Table | `tbl_fact_pull_request_changed_file` | `EDCAP_BE/src/main/resources/db/migration/V181__git_pr_metadata_collector_schema.sql` | Cột hiện có: `pull_request_changed_file_id` PK, `pr_id` FK CASCADE, `repository_id` FK, `file_path`, `file_path_hash`, `file_extension`, `change_type`, `additions`, `deletions`, `collected_at`; unique constraint hiện tại `(pr_id, file_path_hash)`. **Chưa có cột `head_sha`** — cần migration mới (số hiệu do Phase 3 xác định) thêm cột `head_sha` và đổi unique key thành `(pr_id, head_sha, file_path)` theo OI-002 (Closed) |
| Table | `tbl_dim_member_pseudonym` | `V4__init_shema_v2.sql:158-172` | Cột chính: `member_key` PK, `role_id` FK, `team_id` FK, `pseudonym UNIQUE`, `external_user_hash`, `active_from/active_to`. Bắt buộc dùng để join reviewer identity |
| Table (audit, không phải PR-ticket link riêng) | `tbl_fact_traceability_link` | `V4__init_shema_v2.sql:472` | Không có bảng join PR↔ticket riêng; liên kết PR-ticket là FK trực tiếp `tbl_fact_pull_request.ticket_id` (nullable) + `linked_issue_key` text; `tbl_fact_traceability_link` là bảng audit evidence chung, ghi qua `persistence.upsertTraceabilityLink` |
| Enum/DB type | `review_state` | dùng trong `tbl_fact_review.state` | Giá trị chuẩn hóa bởi `normalizeReviewState`: `APPROVED\|CHANGES_REQUESTED\|REVIEW_REQUIRED\|UNKNOWN` |
| Enum/DB type | `finding_status` | dùng trong `tbl_fact_finding.status` | Feature này sử dụng `OPEN\|RESOLVED`; enum DB dùng chung giữ nguyên các giá trị lịch sử khác |

## formItemNm / SEQNO / Master Data / Code Value Mapping

| display/item | internal value | source | note |
|---|---|---|---|
| Review state dùng để lọc finding eligibility | `APPROVED`/`CHANGES_REQUESTED`/`REVIEW_REQUIRED` | `tbl_fact_review.state` (đã chuẩn hóa bởi `normalizeReviewState`) | Ba state hợp lệ được xét; `UNKNOWN`, trống và state không nhận diện bị loại (spec §5.1) |
| Finding status hiển thị breakdown | `OPEN`/`RESOLVED` | `tbl_fact_finding.status` | Không ảnh hưởng tổng density, chỉ phục vụ breakdown UI |
| Đơn vị hiển thị density | `findings / KLOC` hoặc `N/A` | tính toán, không phải master data | Rounding 1 chữ số thập phân, `RoundingMode.HALF_UP` (OI-004, Closed) |
| Reviewer identity | `member_key` (không phải raw user id) | `tbl_dim_member_pseudonym.pseudonym`/`external_user_hash` | Bắt buộc theo `docs/standards/security.md` |

## Multilingual Note

- Logic BE (finding classification, density calculation) là logic thuần túy, không có nội dung hiển thị đa ngôn ngữ.
- Phần FE hiển thị `"N/A"` hoặc `"X findings/KLOC"` trên `TicketDetailDrawer.tsx`: cần xác nhận ở Phase 3 xem màn hình này có dùng cơ chế i18n key hiện có hay chuỗi tĩnh (đối chiếu với cách `formatFirstCiPassRate` hiện đang hiển thị `"N/A"`/`"X%"` — hiện tại các chuỗi này là literal tiếng Anh, không qua i18n key theo code đã đọc được).

## Encoding / Mojibake Note

- Không có xử lý encoding đặc biệt nào được xác định trong scope ticket này (không đọc/ghi nội dung diff/source code thô — spec §6.2 cấm lưu nội dung diff thô).
- Nếu Finding writer mới cần lưu `comment_summary`/`finding_summary` dạng text tự do (UTF-8, có thể chứa emoji/markdown từ GitHub), cần xác nhận charset của cột hiện có trong `tbl_fact_review_comment.comment_summary` ở Phase 3 trước khi ghi.

## Log / Audit / Operation Note

- **Finding created**: ghi khi một review comment/thread được phân loại là finding — payload tối thiểu: `pr_id`, `head_sha`, review/comment gốc, thời điểm phát hiện (spec §5.X).
- **Density recalculated**: ghi khi PR có commit mới và density được tính lại — payload: `pr_id`, `head_sha` cũ/mới, giá trị density trước/sau (spec §5.X, §5.4).
- Không log secret/PII; reviewer identity trong log phải dùng `member_key`/pseudonym, không dùng raw GitHub username/user id (`.claude/rules/30-security.md`, `docs/standards/security.md`).
- Theo NFR §6.4 (Khả năng quan sát): log phải đủ để audit truy vết theo `pr_id`/`head_sha` — không có yêu cầu log level cụ thể trong spec, cần xác định ở Phase 3 (khớp với convention hiện có trong `docs/standards/logging.md`).

## Ticket-Specific Constraints

- Không triển khai bất kỳ nội dung nào không có trong `spec-pack.md`; các điểm chưa rõ (ví dụ SLA hiệu năng cụ thể ở NFR §6.1, số migration version mới, tên GraphQL query cụ thể) phải đưa vào Open Issues ở `impl-plan.md`, không tự quyết ở Phase 2/3.
- Cả 4 Open Issues gốc (OI-001..004) đã **Closed** — không được mở lại hoặc diễn giải khác đi so với quyết định đã ghi ở spec-pack §1, §8, §12.
- Rủi ro đã biết và được chấp nhận (không phải điểm chặn): rule metadata-only có thể tính cả thread chỉ là câu hỏi/LGTM là finding (spec §9 rủi ro #3); GraphQL rate limit khác REST (rủi ro #1); backfill `head_sha` cho dữ liệu `tbl_fact_pull_request_changed_file` cũ (rủi ro #2) — các rủi ro này cần phương án cụ thể ở impl-plan, không phải lý do trì hoãn implementation.
- External HTTP call (GitHub REST + GraphQL mới) phải chạy **ngoài** DB transaction; mỗi external upsert dùng `TransactionTemplate` riêng — không dùng chung transaction cho nhiều item (`.claude/rules/20-architecture.md`).
- GraphQL client mới phải cô lập trong `infrastructure` layer của GitHub adapter, không rò rỉ chi tiết GraphQL (query shape, response DTO) ra `application`/`web` (NFR §6.7).

## AC Coverage Mapping

| Nhóm AC | Method/Table thực sự liên quan (đã xác nhận tồn tại hoặc xác nhận cần tạo mới) |
|---|---|
| `AC-FINDING-CLASSIFY-*` | Cần tạo mới: Finding classifier + writer (theo pattern `AiFindingStatWriter`); đọc `tbl_fact_review.state` (đã tồn tại, dùng `normalizeReviewState`); ghi `tbl_fact_finding` (bảng tồn tại, writer chưa tồn tại); cần GraphQL client mới cho thread identity |
| `AC-DENSITY-CALC-*` | Cần tạo mới: Density service, tham chiếu pattern `EvidenceQualityScoreService.scoreByRatio`; đọc `tbl_fact_finding` + `tbl_fact_pull_request_changed_file` (cần thêm cột `head_sha`) |
| `AC-RECALC-*` | Sửa `GitPrMetadataCollectorJdbcAdapter.upsertPullRequestChangedFile` (đổi khóa); sửa `GitPrMetadataCollectorService.persistPullRequest` (gắn thêm bước re-classify) |
| `AC-REG-1` | Không đổi hành vi hiện có của `persistPullRequest`/`upsertPullRequest`/`upsertCommit`/`insertReview`/`insertReviewComment` — chỉ được thêm bước mới, không sửa logic cũ ngoài phạm vi OI-002 |

## GraphQL & head_sha Migration Note

- **GraphQL client là thành phần hoàn toàn mới** — hiện tại `GithubPullRequestMetadataAdapter` không có `WebClient` hay cấu hình nào trỏ tới GitHub GraphQL endpoint (`https://api.github.com/graphql`). Phase 3 cần thiết kế: endpoint, auth (dùng lại token hiện có của REST client hay cần credential riêng — không đọc `.env`/secrets theo `.claude/rules/00-safety.md §1`, chỉ tham chiếu tên biến cấu hình), rate-limit handling.
- **Migration `head_sha`**: chưa có file migration nào thêm cột này; Phase 3 phải chỉ định số version migration mới (tiếp theo sau các version hiện có trong `db/migration/`), kèm chính sách backfill cho dữ liệu `tbl_fact_pull_request_changed_file` hiện có (rủi ro #2, spec §9) — ví dụ giá trị mặc định `head_sha` cho hàng cũ hoặc đánh dấu snapshot cũ là "unknown version".
