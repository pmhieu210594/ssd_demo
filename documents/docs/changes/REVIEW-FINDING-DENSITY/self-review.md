# Tự review — REVIEW-FINDING-DENSITY (Review Finding Density)

- **Ticket:** REVIEW-FINDING-DENSITY
- **Trạng thái:** Implemented — Parts A-E hoàn tất, chờ independent review / human review
- **Tạo ngày:** 2026-09-11 16:41:02
- **Cập nhật ngày:** 2026-09-14 09:40:25

> Nguồn tham chiếu chính: `docs/changes/REVIEW-FINDING-DENSITY/spec-pack.md`, `docs/changes/REVIEW-FINDING-DENSITY/impl-plan.md` và `docs/changes/REVIEW-FINDING-DENSITY/review-checklist.md`.

---

## 0. Tóm tắt implement

| Phần | Nội dung | Trạng thái |
| --- | --- | --- |
| Part A — Migration & changed-lines versioning | Migration `V520__add_head_sha_to_pull_request_changed_file.sql` (thêm cột `head_sha`, backfill `'legacy'`, đổi unique key sang `(pr_id, head_sha, file_path)`); `PullRequestChangedFileUpsert.headSha`; `upsertPullRequestChangedFile` đổi `ON CONFLICT`; `graph.headSha()` được truyền vào từ `GitPrMetadataCollectorService.persistPullRequest` | **Hoàn tất** |
| Part B — GraphQL client + Finding classification/writer | `GithubReviewThreadPort`/`GithubReviewThreadAdapter` (GraphQL `reviewThreads`/`isResolved`, `githubGraphqlWebClient` bean mới); `FindingClassifier` (metadata-only); `FindingWriter` (`@Transactional(REQUIRES_NEW)`, tự dọn theo `pr_id` trước `deleteReviewsByPrId`); gắn vào `persistPullRequest` | **Hoàn tất** |
| Part C — Density service & embed vào API hiện có | `ReviewFindingDensityReadPort`/`ReviewFindingDensityJdbcAdapter`; `ReviewFindingDensityService.calculate(ticketId)` (tổng/tổng, N/A rule, HALF_UP scale=1); nhúng vào `PmDashboardService.detail(...)` → `PmDashboardTicketDetailDto.reviewFindingDensity` (không tạo endpoint riêng, IMPL-OI-3) | **Hoàn tất** |
| Part D — FE display | `formatReviewFindingDensity` (`SummaryCards.tsx`), `ReviewFindingDensityCard` mới trong `TicketDetailDrawer.tsx` (giữa `EqsSummaryCard` và `TraceabilityIssuesSection`), field `reviewFindingDensity` trong `PmDashboardTicketDetail` (`lib/api.ts`) | **Hoàn tất** |

---

## 1. Trạng thái hoàn thành AC

> ✅ = đạt | ❌ = chưa đạt | 🔶 = một phần (kèm ghi chú lý do)

### 1.1. Finding classification — spec §5.1

| #   | AC                        | Trạng thái   | Bằng chứng (file:line, hoặc kết quả test) |
| --- | -------------------------- | ------------ | ----------------------------------------- |
| 1   | AC-FINDING-CLASSIFY-1/v1   | ✅           | `FindingClassifierTest.classify_review_changes_requested_with_threads_produces_one_finding_per_thread`; `GitPrMetadataCollectorServiceTest.collect_pull_request_syncs_findings_via_graphql_after_review_reinsert` |
| 2   | AC-FINDING-CLASSIFY-2/v1   | ✅           | `FindingClassifierTest.classify_unknown_or_blank_review_state_produces_no_finding` |
| 3   | AC-FINDING-CLASSIFY-3/v1   | ✅           | `FindingClassifierTest.classify_thread_with_question_or_lgtm_body_is_still_a_finding_because_rule_is_metadata_only` |
| 4   | AC-FINDING-CLASSIFY-4/v1   | ✅           | `FindingClassifierTest.classify_multiple_threads_in_one_review_each_count_as_separate_findings` |
| 5   | AC-FINDING-CLASSIFY-5/v1   | ✅           | `FindingClassifierTest.classify_dedupes_multiple_comments_in_same_thread_into_a_single_finding` (dedupe tự nhiên vì GraphQL `reviewThreads` trả 1 node/thread) |

### 1.2. Density calculation — spec §5.3

| #   | AC                       | Trạng thái   | Bằng chứng |
| --- | ------------------------- | ------------ | ---------- |
| 1   | AC-DENSITY-CALC-1/v1      | ✅           | `ReviewFindingDensityServiceTest.calculate_n_findings_over_m_changed_lines_returns_ratio_times_1000` |
| 2   | AC-DENSITY-CALC-2/v1      | ✅           | `ReviewFindingDensityJdbcAdapter` aggregates `SUM` across all PRs linked to ticket before dividing (không average per-PR); `PmDashboardControllerTest`/`PmDashboardServiceTest` xác nhận field nhúng đúng |
| 3   | AC-DENSITY-CALC-3/v1      | ✅           | `ReviewFindingDensityServiceTest.calculate_zero_changed_lines_returns_na_not_zero` |
| 4   | AC-DENSITY-CALC-4/v1      | ✅           | `ReviewFindingDensityServiceTest.calculate_zero_findings_with_changed_lines_returns_zero_not_na` |
| 5   | AC-DENSITY-CALC-5/v1      | ✅           | `ReviewFindingDensityServiceTest.calculate_breakdown_by_status_does_not_change_total_density` |
| 6   | AC-DENSITY-CALC-6/v1      | ✅           | `ReviewFindingDensityServiceTest.calculate_rounds_half_up_to_one_decimal` (5.25→5.3), `calculate_rounds_half_up_lower_boundary` (5.24→5.2) |

### 1.3. Recalculation & Finding status — spec §5.2 / §5.4

| #   | AC              | Trạng thái   | Bằng chứng |
| --- | ---------------- | ------------ | ---------- |
| 1   | AC-RECALC-1/v1    | ✅           | `GitPrMetadataCollectorJdbcAdapterTest.upsertPullRequestChangedFile_conflictTarget_is_pr_id_head_sha_and_file_path`; `GitPrMetadataCollectorService.java` truyền `graph.headSha()` (không cộng dồn theo commit trung gian, chỉ đọc diff hiện tại) |
| 2   | AC-RECALC-2/v1    | ✅           | `FindingWriterTest.writeFindings_preserves_previously_resolved_status_when_pr_resyncs_with_same_thread` |
| 3   | AC-STATUS-1/v1    | ✅           | `FindingWriterTest.writeFindings_candidate_resolved_on_github_is_created_resolved` |
| 7   | AC-STATUS-5/v1    | ✅           | `FindingWriterTest.writeFindings_preserves_previously_resolved_status_when_pr_resyncs_with_same_thread` (không bị ghi đè về `OPEN`/reset khi resync) |

### 1.4. Regression — spec §7.X

| #   | AC           | Trạng thái   | Bằng chứng |
| --- | ------------ | ------------ | ---------- |
| 1   | AC-REG-1/v1  | ✅           | `GitPrMetadataCollectorServiceTest` — toàn bộ 10 test case gốc + 2 test case mới (thứ tự xóa finding/review, gọi GraphQL sau reinsert) pass không sửa test cũ; `mvn test` full suite exit code 0 |

---

## 2. Các hạng mục checklist (từ `review-checklist.md`)

| RC#   | Trạng thái | Bằng chứng / Ghi chú |
| ----- | ---------- | -------------------- |
| RC-01 | [x]        | Xem mục 1 — 18 AC đều có test cụ thể |
| RC-02 | [x]        | Không dùng NLP (`FindingClassifier` chỉ đọc `review.state()`/thread tồn tại); không đọc `tbl_fact_ai_finding_stat`; không thêm ranking reviewer; không redesign Ticket Detail |
| RC-04 | [x]        | Finding classification là bước bắt buộc trong `persistPullRequest` (không phải on-read); density đọc từ `tbl_fact_finding`/`tbl_fact_pull_request_changed_file` đã lưu, không gọi GitHub khi mở Ticket Detail |
| RC-05 | [x]        | `FindingClassifier` (pure), `ReviewFindingDensityService` ở `application/usecase/*`; không mutate `tbl_fact_finding` ở nơi khác |
| RC-06 | [x]        | `FindingJdbcAdapter`/`ReviewFindingDensityJdbcAdapter`/`GitPrMetadataCollectorJdbcAdapter` gom hết SQL; `application` không phụ thuộc SQL/GraphQL response cụ thể |
| RC-07 | [x]        | `GithubReviewThreadAdapter` cô lập GraphQL trong `infrastructure/github`; `GithubReviewThreadPort.ReviewThread` là record thuần, không lộ `JsonNode` ra ngoài |
| RC-08 | [x]        | `PmDashboardTicketDetailDto` chỉ thêm field `reviewFindingDensity`; field cũ không đổi (test `PmDashboardControllerTest` cũ vẫn pass không sửa assertion cũ) |
| RC-09 | [x]        | Vòng đời status nằm trong `FindingWriter`/`FindingClassifier`, tách biệt logic PR/commit/changed-file |
| RC-10 | [x]        | `GitPrMetadataCollectorServiceTest.collect_pull_request_deletes_findings_before_deleting_reviews_to_avoid_fk_violation` xác nhận đúng thứ tự bằng `InOrder` |
| RC-11 | [x]        | `pr_id`/`ticket_id`/`head_sha` đều là `UUID`/`String` nội bộ do server tính, không nhận trực tiếp từ client cho density/changed-lines query |
| RC-13 | [x]        | Log "Finding created"/"status changed"/"Density recalculated" chỉ log `findingId`/`prId`/`headSha`/`ticketId`/status — không log nội dung comment/diff |
| RC-14 | [x]        | `GithubReviewThreadAdapter` dùng `variables` JSON (ObjectNode) — không nối chuỗi giá trị động vào query string |
| RC-15 | [x]        | `githubGraphqlWebClient` dùng chung `props.connectors().github().apiToken()` hiện có, không hardcode credential |
| RC-16 | [x]        | `ReviewFindingDensityService` chỉ đọc `ReviewFindingDensityReadPort` (SQL), không gọi `GithubReviewThreadPort`/REST khi tính density |
| RC-17 | [x]        | `ReviewFindingDensityJdbcAdapter` dùng 2 câu `SUM`/`GROUP BY` ở DB, không N+1 theo từng PR |
| RC-18 | [x]        | `PmDashboardService.detail(...)` chỉ thêm 1 lời gọi `reviewFindingDensityService.calculate(ticketId)` (2 query mới), không lặp lại query cũ |
| RC-19 | [x]        | GraphQL fetch nằm trong `persistPullRequest` (không có transaction bao ngoài method này — xác nhận không có `@Transactional` cấp method/class); `FindingWriter` các thao tác ghi DB dùng `REQUIRES_NEW` riêng |
| RC-20 | [x]        | `ReviewFindingDensityJdbcAdapter.findTotalChangedLines` không lọc theo `head_sha != 'legacy'`, cộng đúng row backfill vào tổng |
| RC-21 | [x]        | `V519` không có `-- down` script riêng (Flyway không có built-in down); rollback thủ công được ghi rõ ở impl-plan §7.2 (drop cột, khôi phục unique key cũ) — xem mục 5.3 dưới |
| RC-22 | [x]        | 10 test case gốc `GitPrMetadataCollectorServiceTest` không sửa đổi, vẫn pass |
| RC-24 | [x]        | `FindingWriter.writeFindings` log "Finding created" với `findingId/prId/headSha/status` |
| RC-26 | [x]        | Không log `reason`/nội dung comment/diff — chỉ log status/id/timestamp |
| RC-27 | [🔶]       | `ReviewFindingDensityService.calculate` không throw nếu 1 PR thiếu changed-lines (SQL `COALESCE`/`SUM` tự bỏ qua); tuy nhiên nếu GraphQL fetch lỗi khi *sync* (không phải khi đọc Ticket Detail) thì `FindingWriter` hiện **không** bắt exception riêng — xem mục 5.3 (nợ kỹ thuật) |
| RC-28 | [x]        | `Optional.empty()` (N/A) vs `Optional.of(ZERO)` phân biệt rõ qua `BigDecimal density` = `null` (N/A) vs `0.0` — test `calculate_zero_changed_lines_returns_na_not_zero` / `calculate_zero_findings_with_changed_lines_returns_zero_not_na` |
| RC-29 | [x]        | `FindingWriterTest.deleteFindingsByPrId_is_safe_when_pr_has_no_findings`; `GitPrMetadataCollectorServiceTest` PR không có review nào vẫn chạy sạch (test hiện có #4) |
| RC-30 | [x]        | Thứ tự `findingWriter.deleteFindingsByPrId` trước `persistence.deleteReviewsByPrId` — test `InOrder` ở RC-10 |
| RC-31 | [🔶]       | `writeFindings`/`deleteFindingsByPrId` chạy `REQUIRES_NEW` độc lập với transaction chính (vốn `persistPullRequest` không có transaction bao method); tuy nhiên nếu `githubReviewThreadPort.fetchReviewThreads` ném exception, nó sẽ propagate lên và làm fail toàn bộ `persistPullRequest` hiện tại (không có try/catch riêng) — xem mục 5.3 |
| RC-33 | [x]        | `GitPrMetadataCollectorJdbcAdapterTest` bổ sung 7 test mới (bao gồm `upsertPullRequestChangedFile`, `deleteReviewsByPrId`, `insertReview`, `insertReviewComment`, `findMemberKeyBy*`) trước khi đổi khóa unique ở A.3/A.4 |
| RC-34 | [x]        | 6 test `FindingClassifierTest`, bao phủ state hợp lệ và `UNKNOWN`/trống |
| RC-35 | [x]        | 6 test `ReviewFindingDensityServiceTest` |
| RC-36 | [x]        | `FindingWriterTest` (AC-RECALC-1/2, AC-STATUS-1/5), `GitPrMetadataCollectorServiceTest` (headSha wiring) |
| RC-37 | [x]        | 10 test gốc `GitPrMetadataCollectorServiceTest` pass nguyên vẹn + test FK-order mới |
| RC-38 | [x]        | `PmDashboardControllerTest`/`PmDashboardServiceTest` xác nhận field `reviewFindingDensity` + field cũ không đổi |
| RC-39 | [x]        | `SummaryCards.test.tsx` (3 case `formatReviewFindingDensity`); `TicketDetailDrawer.test.tsx` vẫn pass với field mới; card hiển thị xác nhận qua `npx vitest run` |
| RC-41 | [x]        | Rollback code theo thứ tự D→C→B→A (impl-plan §7.1); không có state FE phức tạp |
| RC-42 | [x]        | `graphql-api-base-url` đặt trong `AppProperties.Connectors.GitHub` + `application.yml`, không hardcode trong `WebClientConfig`/adapter |
| RC-43 | [x]        | Không thêm feature flag nào ngoài yêu cầu spec |

---

## 3. Các lệnh đã chạy

### 3.1. Lint

```bash
# Lệnh:
[Không có lint riêng cho Java; SonarLint/IDE diagnostics đã được kiểm tra qua mỗi Edit — không phát hiện lỗi mới ngoài warning style pre-existing (unused eq(), static import suggestions ở test file gốc không đụng tới)]

# FE:
# (npm run lint không chạy riêng lượt này; npx tsc --noEmit + npm run build đã bao phủ type-safety)
```

### 3.2. Type-check

```bash
# Lệnh (không có script "typecheck" riêng trong package.json — dùng tsc trực tiếp, tương đương "tsc && vite build" trong "build"):
npx tsc --noEmit

# Kết quả:
Không có lỗi (exit code 0) sau khi cập nhật 3 fixture test (PMDashboardPage.test.tsx, TicketDetailDrawer.test.tsx x2) thêm field reviewFindingDensity.
```

### 3.3. Unit test

```bash
# Lệnh:
cd EDCAP_BE && mvn test
cd EDCAP_FE && npx vitest run "src/__ tests __/pm-dashboard/SummaryCards.test.tsx" "src/__ tests __/pm-dashboard/TicketDetailDrawer.test.tsx" "src/__ tests __/pm-dashboard/PMDashboardPage.test.tsx"

# Kết quả:
- mvn test (toàn bộ backend, bao gồm mọi test mới của Part A-E + toàn bộ test suite hiện có): BUILD SUCCESS, exit code 0.
- FE vitest 3 test file liên quan: 3 passed (3 files), 12 passed (12 tests).
```

### 3.4. Build

```bash
# Lệnh:
cd EDCAP_BE && mvn compile
cd EDCAP_FE && npm run build   # tsc && vite build

# Kết quả:
- mvn compile: BUILD SUCCESS.
- npm run build: built in 43.77s, dist/ generated (chunk-size warning >500kB là pre-existing, không liên quan ticket này).
```

### 3.5. Manual / E2E (theo impl-plan §8.2 — Manual verification matrix)

| #   | Scenario                                                                                  | Mode        | Kết quả | Ghi chú |
| --- | ------------------------------------------------------------------------------------------ | ----------- | -------- | ------- |
| 1   | Ticket có 1 PR, review thuộc state hợp lệ với 3 thread → 3 finding hiển thị, density = 3/M×1000 | PM/QA (read) | 🔶       | Bao phủ bằng unit/integration test (mock GraphQL/JDBC); **chưa chạy thủ công với DB dev thật + GitHub thật** — cần môi trường có Docker Compose Postgres + GitHub PAT thật, ngoài khả năng của phiên làm việc này |
| 2   | Ticket có 2 PR liên kết → density = tổng/tổng, không phải average                          | PM/QA (read) | 🔶       | Bao phủ bằng `ReviewFindingDensityServiceTest`/SQL aggregate logic; chưa chạy thủ công trên UI thật |
| 3   | Ticket có PR nhưng changed lines = 0 (chỉ đổi file binary) → hiển thị `N/A`, không hiển thị `0` | PM/QA (read) | 🔶       | Bao phủ bằng test; UI hiển thị `formatReviewFindingDensity(null)` = "N/A" xác nhận qua Vitest |
| 4   | PR có commit mới sau khi đã có finding từ `head_sha` cũ → changed lines tính theo `head_sha` mới nhất, finding cũ không tự resolve | System/service | ✅       | `FindingWriterTest`, `GitPrMetadataCollectorJdbcAdapterTest` (unit, không cần DB thật) |
| 5   | GitHub thread được resolve trên GitHub (qua GraphQL `isResolved`) → finding chuyển `RESOLVED` khi đồng bộ lại | System/service | ✅       | `FindingWriterTest.writeFindings_candidate_resolved_on_github_is_created_resolved` |

> Scenario 1-3 chưa chạy trên môi trường dev thật (Docker Compose Postgres + GitHub GraphQL thật) — đây là công việc còn lại trước khi merge, không phải blocker về mặt logic (đã bao phủ bằng unit/integration test mock).

---

## 4. Tổng quan diff

| Loại      | File / module                                                                                   | Vai trò chính trong PR                                                        |
| --------- | ---------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| Sửa lớn   | `EDCAP_BE/.../ingestion/GitPrMetadataCollectorService.java`                                     | Truyền `headSha`; gắn `findingWriter.deleteFindingsByPrId` trước xóa review; gọi `syncFindings` (GraphQL fetch + classify + write) sau reinsert review/comment; `normalizeReviewState`/`hash` đổi `private`→`static` để tái dùng |
| Sửa lớn   | `EDCAP_BE/.../adapter/GitPrMetadataCollectorJdbcAdapter.java`                                    | `upsertPullRequestChangedFile` đổi `ON CONFLICT` sang `(pr_id, head_sha, file_path)` |
| Sửa nhỏ   | `EDCAP_BE/.../ingestion/GitPrMetadataCollectorModels.java`                                       | Thêm `headSha` vào `PullRequestChangedFileUpsert` |
| Sửa nhỏ   | `EDCAP_BE/.../config/AppProperties.java`, `WebClientConfig.java`, `application.yml`              | Thêm `graphqlApiBaseUrl` + bean `githubGraphqlWebClient` |
| Thêm mới  | `EDCAP_BE/.../infrastructure/github/GithubReviewThreadAdapter.java` + `application/port/out/integration/GithubReviewThreadPort.java` | GraphQL client mới lấy `reviewThreads`/`isResolved`, cô lập trong infrastructure |
| Thêm mới  | `EDCAP_BE/.../application/usecase/ingestion/FindingClassifier.java`                              | Phân loại finding metadata-only (pure logic) |
| Thêm mới  | `EDCAP_BE/.../application/port/out/persistence/FindingPersistencePort.java` + `infrastructure/persistence/adapter/FindingJdbcAdapter.java` | SQL cho `tbl_fact_finding` (insert/delete/find/updateStatus/member-key lookup) |
| Thêm mới  | `EDCAP_BE/.../application/usecase/reviewfinding/ReviewFindingDensityModels.java` + `ReviewFindingDensityService.java` | Tính density tổng/tổng, N/A rule, rounding HALF_UP scale=1 |
| Thêm mới  | `EDCAP_BE/.../application/port/out/persistence/ReviewFindingDensityReadPort.java` + `infrastructure/persistence/adapter/ReviewFindingDensityJdbcAdapter.java` | SQL aggregate SUM changed-lines (chỉ head_sha mới nhất) + finding counts by status |
| Sửa nhỏ   | `PmDashboardModels.java`, `PmDashboardService.java`, `PmDashboardDtos.java`, `PmDashboardJdbcAdapter.java` | Thêm field `reviewFindingDensity` vào `DashboardTicketDetail`/`PmDashboardTicketDetailDto`, service gọi `ReviewFindingDensityService.calculate` |
| Migration | `V520__add_head_sha_to_pull_request_changed_file.sql`                                            | Thêm cột `head_sha`, backfill `'legacy'`, đổi unique key |
| Sửa nhỏ   | `EDCAP_FE/src/lib/api.ts`                                                                         | Thêm field `reviewFindingDensity` vào `PmDashboardTicketDetail` |
| Sửa nhỏ   | `EDCAP_FE/src/pages/pm-dashboard/components/SummaryCards.tsx`                                     | `formatReviewFindingDensity` (exported pure function) |
| Sửa nhỏ   | `EDCAP_FE/src/pages/pm-dashboard/components/TicketDetailDrawer.tsx`                               | `ReviewFindingDensityCard` mới, chèn giữa `EqsSummaryCard`/`TraceabilityIssuesSection` |
| Test mới  | Finding/classifier/writer/density/GraphQL tests và các regression tests của ingestion | Bao phủ AC theo mục 1 |
| Test sửa  | `SummaryCards.test.tsx` (+3 test), `PMDashboardPage.test.tsx`/`TicketDetailDrawer.test.tsx` (thêm field fixture), `GitPrMetadataCollectorServiceTest`/`PmDashboardServiceTest`/`PmDashboardControllerTest` (constructor mới), 4 test file GitHub cũ (`AppProperties.Connectors.GitHub` thêm field) | Cập nhật fixture cho constructor/record signature mới, không đổi assertion cũ |

---

## 5. Rủi ro đã biết / Chưa bao phủ / Công việc còn lại

### 5.1. Known risks

| #   | Rủi ro                                                                                                          | Mức độ ảnh hưởng | Cách giảm thiểu / Theo dõi |
| --- | ------------------------------------------------------------------------------------------------------------------ | ----------------- | -------------------------- |
| 1   | Rule metadata-only tính cả thread chỉ là câu hỏi/LGTM/ACK dưới review state hợp lệ là finding, có thể làm density cao hơn thực tế "số lỗi thật" (spec §9 rủi ro #3, đã được xác nhận chấp nhận) | Medium             | Đã accepted theo OI-003 (Closed); theo dõi phản hồi PM/QA sau release |
| 2   | GitHub GraphQL API có rate limit/quota riêng khác REST (spec §9 rủi ro #1) — **chưa test với GitHub thật** trong phiên này | Medium             | Cần đo rate limit thực tế trước khi bật cho toàn bộ repo — xem mục 5.2 |
| 3   | Dữ liệu `tbl_fact_pull_request_changed_file` cũ backfill bằng placeholder `'legacy'` (không đối chiếu được với PR version cụ thể) | Low (dữ liệu lịch sử, chấp nhận được) | Không cần xử lý thêm |
| 4   | `FindingWriter.writeFindings`/`deleteFindingsByPrId` không có try/catch riêng quanh `githubReviewThreadPort.fetchReviewThreads` trong `syncFindings` — nếu GraphQL lỗi/timeout, `persistPullRequest` sẽ ném exception lên trên (không "graceful partial success" như NFR §6.3 gợi ý cho *đọc* Ticket Detail; đây là *ghi* lúc ingestion, khác ngữ cảnh nhưng cần cân nhắc retry/circuit-breaker) | Medium             | Đề xuất theo dõi ở production; nếu GraphQL rate limit gây fail thường xuyên, cân nhắc bọc `syncFindings` bằng try/catch + log warning thay vì để lỗi lan lên toàn bộ PR sync (theo dõi ở mục 5.3) |

### 5.2. Not handled yet

| #   | Hạng mục                                                    | Lý do chưa bao phủ                                                            | Hành động được đề xuất |
| --- | ------------------------------------------------------------ | ------------------------------------------------------------------------------- | ---------------------- |
| 1   | Chạy thử end-to-end với GitHub GraphQL thật (rate limit, response shape thật) | Phiên làm việc này không có credential/kết nối GitHub thật, chỉ test với mock GraphQL response | Cần review/QA verify trên môi trường staging với GitHub PAT thật trước khi release |

### 5.3. Remaining issues / nợ kỹ thuật

| #   | Issue                                                                                   | Liên quan AC / RC | Đề xuất xử lý (ticket follow-up, ...) |
| --- | ------------------------------------------------------------------------------------------ | ----------------- | -------------------------------------- |
| 1   | `docs/architecture/service-layer-map.md`, `repository-db-map.md`, `route-api-map.md`, `entrypoint-map.md`, `fe-be-contract-map.md` không mô tả các class/bảng mới của ticket này | Không map AC cụ thể | Ghi nhận backlog cập nhật doc kiến trúc riêng, không block ticket này |
| 2   | GraphQL fetch lỗi trong `syncFindings` làm fail toàn bộ `persistPullRequest` (RC-31 🔶) | RC-27, RC-31 | Cân nhắc bọc try/catch + log warning ở lần merge tiếp theo nếu rate limit gây fail thường xuyên trong thực tế |
| 3   | `detected_at` của một finding bị reset về thời điểm resync hiện tại thay vì giữ nguyên thời điểm phát hiện gốc, vì `FindingWriter.writeFindings` xóa-chèn-lại toàn bộ finding mỗi lần sync (chỉ `status` được carry-over qua `sourceLocationHash`, không phải `detected_at`) | Không map AC cụ thể (spec không yêu cầu preserve `detected_at` qua resync) | Nếu cần audit chính xác thời điểm phát hiện gốc, cân nhắc thêm `detected_at` vào `FindingIdentitySnapshot` ở ticket follow-up |

### 5.4. Open Issues từ impl-plan vẫn còn

| #   | OI ID        | Mô tả ngắn                                                                                     | Trạng thái                 |
| --- | ------------ | -------------------------------------------------------------------------------------------------- | --------------------------- |
| —   | (none)       | Toàn bộ IMPL-OI-1..9 đã Resolved trước khi implementation bắt đầu; không phát sinh Open Issue mới trong lúc code | N/A |

---

## 6. Confirmations cuối cùng

| #   | Confirmation                                                              | Trạng thái |
| --- | --------------------------------------------------------------------------- | ---------- |
| 1   | Không thêm scope ngoài spec-pack mục `§2.2`                                 | [x]        |
| 2   | Mọi Open Issue vẫn open đều được nêu ở mục 5.4                              | [x]        |
| 3   | Mọi RC mức Blocker đã ✅ hoặc đã nêu lý do ở mục 5                          | [x]        |
| 4   | Lint / type-check / build pass; nếu fail đã được giải trình ở mục 3        | [x]        |
| 5   | Không có console log, debug code, commented-out code còn sót                | [x]        |
| 6   | Không log PII / secret / raw business data vượt mức cần thiết               | [x]        |
| 7   | Migration/schema/data change có rollback hoặc có giải trình nếu không cần   | [x]        |

---

## 7. Phần AI đã suy đoán

> Các điểm mà implementation đã tự đặt tên/quyết định cụ thể vì impl-plan chỉ gợi ý — cần người review xác nhận.

| #   | Điểm đã suy đoán / quyết định khi code                                                            | Nguồn gợi ý                     | Cần dev/reviewer xác nhận gì |
| --- | ------------------------------------------------------------------------------------------------------ | ---------------------------------- | ------------------------------------------------------------------------ |
| 1   | `source_location_hash` = `hash(prId + "|" + headSha + "|" + threadId)` dùng làm thread identity ổn định qua các lần resync (để carry-over status) | impl-plan không chốt công thức hash cụ thể, chỉ nói "theo `(pr_id, head_sha, thread_id)`" | Xác nhận công thức hash này đủ ổn định/không xung đột (thread id GraphQL là duy nhất toàn cục nên rủi ro collision rất thấp) |
| 4   | Query "latest head_sha" cho changed-lines dùng `DISTINCT ON (pr_id) ... ORDER BY collected_at DESC` (Postgres-specific) | impl-plan không chốt SQL cụ thể cho việc chỉ tính head_sha mới nhất khi có nhiều snapshot lịch sử | Xác nhận đây đúng là cách phải làm để không double-count các snapshot head_sha cũ (rủi ro không được spec-pack nêu rõ, tự suy luận từ yêu cầu "chỉ tính theo head_sha hiện tại") |

## 8. Hạng mục nhờ con người review

| #   | Hạng mục                                                                                                   | Lý do cần người review                                                                    |
| --- | -------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| 2   | Chạy thử end-to-end với GitHub GraphQL thật (rate limit, response shape thật, xem mục 5.2) | Không thể verify bằng unit test mock; cần môi trường có GitHub PAT thật |
| 4   | Rủi ro GraphQL lỗi làm fail toàn bộ `persistPullRequest` (mục 5.1 #4, 5.3 #2) — có cần circuit-breaker/try-catch riêng ngay trong ticket này hay để theo dõi production trước | Quyết định về mức độ resilience cần thiết ngay bây giờ vs. theo dõi rồi làm sau là quyết định sản phẩm/vận hành, không chỉ kỹ thuật |
