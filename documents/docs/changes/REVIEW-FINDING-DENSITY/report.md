# Báo cáo thay đổi — REVIEW-FINDING-DENSITY (Review Finding Density)

- **Ticket:** REVIEW-FINDING-DENSITY
- **Trạng thái:** Final — Phase 8 hoàn tất
- **Tạo ngày:** 2026-09-10 15:30:00
- **Cập nhật ngày:** 2026-09-15 17:20:00

> Đối tượng đọc: kỹ sư tiếp theo, người review, người trực on-call.
> Phải có thể hiểu được chỉ từ nội dung chính. Bao gồm đường dẫn tới bằng chứng.

---

## 1. Tóm tắt thay đổi

**Ticket:** REVIEW-FINDING-DENSITY
**Nhánh:** Không có nhánh PR riêng ghi nhận trong repo tại thời điểm viết báo cáo này — làm việc trực tiếp trên trạng thái hiện tại của repo (`git status`: HEAD, working tree clean).
**Ngày:** 2026-09-15
**Tác giả:** nvt_dung (có hỗ trợ từ Claude)

### Đã thay đổi gì

Bổ sung chỉ số **Review Finding Density** (`tổng review finding hợp lệ / tổng changed lines × 1000`, đơn vị `findings/KLOC`) hiển thị trên PM Dashboard Ticket Detail, gồm 4 phần (`impact-analysis.md` §1):

1. **Migration + changed-lines versioning**: migration `V520__add_head_sha_to_pull_request_changed_file.sql` thêm cột `head_sha` vào `tbl_fact_pull_request_changed_file`, đổi unique key từ `(pr_id, file_path_hash)` sang `(pr_id, head_sha, file_path)`, backfill dữ liệu cũ bằng sentinel `'legacy'`.
2. **GitHub GraphQL client mới** (`GithubReviewThreadPort`/`GithubReviewThreadAdapter`, cô lập trong `infrastructure/github/`) để lấy `reviewThreads`/`isResolved` — nguồn thread identity/resolved-status thật.
3. **Finding classification + writer mới** (`FindingClassifier`, `FindingWriter`): phân loại review thread thuộc state hợp lệ thành finding theo metadata-only, ghi vào `tbl_fact_finding` (bảng đã tồn tại từ trước, trước ticket này chưa có writer).
4. **Density service mới** (`ReviewFindingDensityService`), nhúng vào response hiện có của `PmDashboardService.detail(...)`: tính density theo công thức tổng/tổng (không average per-PR), rule N/A khi denominator=0, rounding 1 chữ số `HALF_UP`; hiển thị trên `TicketDetailDrawer.tsx` (FE) qua `ReviewFindingDensityCard` + `formatReviewFindingDensity`.

### Lý do

- Đo mật độ finding do human reviewer phát hiện tương ứng với quy mô thay đổi của PR, giúp PM/QA/engineering đánh giá chất lượng review độc lập với kích thước PR (spec-pack §1).

## 2. Phạm vi ảnh hưởng

| #   | Khu vực             | Chi tiết |
| --- | ------------------- | -------- |
| 1   | Các tệp đã thay đổi | BE: `GitPrMetadataCollectorService.java`, `GitPrMetadataCollectorJdbcAdapter.java`, `GitPrMetadataCollectorModels.java`, `AppProperties.java`, `WebClientConfig.java`, `application.yml`, `PmDashboardService.java`/`PmDashboardModels.java`/`PmDashboardDtos.java`/`PmDashboardJdbcAdapter.java`. Mới: `GithubReviewThreadAdapter`+`GithubReviewThreadPort`, `FindingClassifier`, `FindingWriter`, `FindingPersistencePort`+`FindingJdbcAdapter`, `ReviewFindingDensityService`+`ReviewFindingDensityModels`, `ReviewFindingDensityReadPort`+`ReviewFindingDensityJdbcAdapter`. FE: `lib/api.ts`, `SummaryCards.tsx`, `TicketDetailDrawer.tsx`. Chi tiết đầy đủ: `impact-analysis.md` §2, `self-review.md` §4. |
| 2   | Lược đồ DB          | Migration `V520__add_head_sha_to_pull_request_changed_file.sql`: thêm cột `head_sha VARCHAR(64)`, backfill `'legacy'` cho row cũ, đổi unique constraint sang `(pr_id, head_sha, file_path)`. `tbl_fact_finding` (đã tồn tại từ V4) nay có writer lần đầu, không đổi schema. |
| 3   | Hợp đồng API        | **Không tạo endpoint riêng** (IMPL-OI-3, Resolved). `GET /api/v1/pm/dashboard/tickets/{ticketId}/detail` (hiện có) chỉ **thêm field mới** `reviewFindingDensity: {density, unit, totalFindings, totalChangedLines, breakdown:{open,resolved}}` — backward compatible, không đổi/xóa field cũ (`impact-analysis.md` §7). |
| 4   | Cấu hình            | Thêm `graphqlApiBaseUrl` trong `AppProperties.Connectors.GitHub` + `application.yml`, bean `githubGraphqlWebClient` mới trong `WebClientConfig`; dùng chung token GitHub hiện có (`props.connectors().github().apiToken()`), không thêm credential mới. |
| 5   | Nhật ký             | 3 log event mới: "Finding created" (`pr_id`, `head_sha`, review/comment gốc, thời điểm), "Finding status changed" (trạng thái cũ/mới, nguồn `GITHUB_THREAD_RESOLVE`), "Density recalculated" (`pr_id`, `head_sha` cũ/mới, density trước/sau). Không log secret/PII/nội dung diff/raw user id (`impact-analysis.md` §12). |
| 6   | Quyền/Vai trò       | Không thêm cơ chế phân quyền mới — density thừa hưởng permission gate hiện có của `GET /tickets/{ticketId}/detail` (`AuthUserContext`, PM/QA read-only) vì được nhúng vào endpoint đó thay vì tạo endpoint riêng (`impact-analysis.md` §2.6). |

---

## 3. Kết quả review

### Tự kiểm tra của Claude (`docs/changes/REVIEW-FINDING-DENSITY/self-review.md`)

- Blocker phát hiện: 0 — toàn bộ hạng mục Blocker trong `review-checklist.md` (RC-01, RC-02, RC-04, RC-07, RC-08, RC-10, RC-11, RC-12, RC-13, RC-14, RC-16, RC-20, RC-22, RC-26, RC-27, RC-28, RC-30, RC-33, RC-37) đều ở trạng thái `[x]` (self-review §2).
- Vấn đề mức nghiêm trọng cao phát hiện: 0 Blocker chưa xử lý; 2 mục Major được đánh dấu 🔶 (một phần) thay vì [x]: RC-27 (partial-success khi 1 PR thiếu dữ liệu — OK cho density read path, nhưng GraphQL lỗi lúc *sync* vẫn propagate) và RC-31 (GraphQL lỗi trong `syncFindings` làm fail toàn bộ `persistPullRequest`, chưa có try/catch riêng) — xem mục 9 "Accepted risk" và mục 5 "Công việc còn lại" bên dưới.
- Tất cả AC đã được đáp ứng: **Có** — toàn bộ 18 dòng AC (16 AC theo spec-pack §7, đánh số lại thành 18 dòng do `self-review.md` liệt kê `AC-STATUS-1`/`AC-STATUS-5` là dòng #1/#7 trong bảng con) đều ✅ (self-review §1.1-§1.4). Bảng chi tiết AC ↔ bằng chứng: xem mục "Tương ứng specification・AC" ở cuối báo cáo này.

### Review bởi Codex (nếu có chạy)

- Kết luận tổng thể: **Không áp dụng** — không có review Codex nào chạy trong phạm vi các artifact đã đọc cho ticket này.
- Các phát hiện chính: Không có.

### Các phát hiện từ review thủ công

| #   | Phát hiện | Mức độ nghiêm trọng | Hành động đã thực hiện |
| --- | --------- | -------------------- | ---------------------- |
| 1   | Phát hiện ngoài spec-pack: `tbl_fact_finding` có FK `review_id`/`review_comment_id` (`NO ACTION`, không `CASCADE`), trong khi `persistPullRequest` xóa-chèn-lại review mỗi lần sync → có thể vi phạm FK nếu thứ tự tác vụ sai (IMPL-OI-6, `impact-analysis.md` §17 #1) | High (nếu implement sai thứ tự) | Đã chốt với user: `FindingWriter.deleteFindingsByPrId(prId)` chạy **trước** `persistence.deleteReviewsByPrId(prId)`; có test `InOrder` xác nhận (`GitPrMetadataCollectorServiceTest.collect_pull_request_deletes_findings_before_deleting_reviews_to_avoid_fk_violation`, RC-10) |
| 2   | `source_location_hash` dùng làm thread identity ổn định qua resync — impl-plan không chốt công thức hash cụ thể (self-review §7 #1) | Medium (cần xác nhận review) | Đã tự quyết định `hash(prId + "\|" + headSha + "\|" + threadId)`; đánh dấu cần dev/reviewer con người xác nhận, chưa có xác nhận chính thức tại thời điểm viết báo cáo này |
| 3   | Query "latest head_sha" dùng `DISTINCT ON (pr_id) ... ORDER BY collected_at DESC` (Postgres-specific), impl-plan không chốt SQL cụ thể (self-review §7 #4) | Medium (cần xác nhận review) | Đã thêm `ReviewFindingDensityJdbcAdapterTest` (SQL-shape, mock JDBC) để xác nhận cấu trúc câu SQL; xác nhận trên Postgres thật vẫn còn treo (xem mục "Source Analysis Limitations") |
| 4   | `FindingWriter.writeFindings`/`deleteFindingsByPrId` không có try/catch quanh `githubReviewThreadPort.fetchReviewThreads` (RC-31) | Medium | Ghi nhận là nợ kỹ thuật còn treo, chưa xử lý trong ticket này — cần quyết định sản phẩm (self-review §5.3 #2, §8 #4) |

---

## 4. Kết quả kiểm thử

| Loại kiểm thử | Lệnh                  | Kết quả     | Ghi chú |
| ------------- | --------------------- | ----------- | ------- |
| BE UT/IT      | `mvn test` (full suite) | PASS | 772 tests run, 0 failures, 0 errors, 0 skipped (`test-results.md` §2) |
| BE UT (nhóm liên quan) | `mvn test -Dtest=PmDashboardServiceTest,PmDashboardControllerTest,ReviewFindingDensityJdbcAdapterTest,ReviewFindingDensityServiceTest` | PASS | Bao gồm 4 test mới + 1 file mới (3 test) |
| FE UT         | `npx vitest run "src/__ tests __/pm-dashboard"` | PASS | 8 test files, 60 tests passed (0 failed); warning jsdom pre-existing, không liên quan thay đổi này |
| FE type-check | `npx tsc --noEmit`    | PASS | Không có lỗi type |
| E2E           | `npx playwright test pm-dashboard` | PASS | 4 passed, 1 skipped (skip từ trước, không liên quan ticket này) |
| Black-box     | Theo `blackbox-testcases.md` (10 case, P0/P1) | PASS (qua UT/IT/E2E tự động) | Case P0/P1 được bao phủ gián tiếp qua automated suite; manual verification với GitHub/DB thật **chưa chạy** (xem mục "Công việc còn lại") |

Chi tiết đầy đủ: `docs/changes/REVIEW-FINDING-DENSITY/test-results.md`

---

## 5. Công việc còn lại / Hành động tiếp theo

| #   | Công việc | Người phụ trách | Hạn chót |
| --- | --------- | --------------- | -------- |
| 1   | Chạy thử end-to-end với GitHub GraphQL thật (rate limit, response shape thật) trên môi trường staging có GitHub PAT thật (`self-review.md` §5.2, §8 #2) | QA / Reviewer con người | Trước khi release lên production |
| 2   | Chạy thử manual verification matrix scenario 1-3 (`self-review.md` §3.5) với Postgres dev thật + Docker Compose (không có trong phiên làm việc hiện tại) | QA | Trước khi release |
| 3   | Quyết định sản phẩm: có cần bọc `syncFindings` bằng try/catch/circuit-breaker quanh lỗi GraphQL ngay trong ticket này hay theo dõi production trước rồi làm sau (RC-31, `self-review.md` §8 #4) | Product owner / Tech lead | Trước khi release, hoặc chấp nhận rủi ro có ghi chú |
| 4   | Xác nhận công thức `source_location_hash` đủ ổn định cho thread identity (self-review §7 #1) và SQL `DISTINCT ON (pr_id)` cho latest `head_sha` đúng theo yêu cầu (self-review §7 #4) | Reviewer con người | Trước khi merge/release |
| 5   | Cập nhật `docs/architecture/*-map.md` (service-layer-map, repository-db-map, route-api-map, entrypoint-map, fe-be-contract-map) để phản ánh các class/bảng mới của ticket này (backlog, không block ticket) | Tech lead (backlog riêng) | Không xác định — theo dõi ở `promotion-candidates.md` |

---

## 6. Quy trình hoàn tác

1. Revert code theo thứ tự ngược Part D → C → B → A (impl-plan §7.1): D (FE display) → C (Density service & API) → B (GraphQL client + Finding classifier/writer) → A (Migration & head_sha versioning).
2. Không có feature flag bắt buộc — nếu có flag tùy chọn được thêm sau này để giảm rủi ro GraphQL rate limit, tắt flag trước khi revert code Part B.
3. Không có state FE persisted phức tạp cần clear (chỉ hiển thị read-only qua TanStack Query cache).
4. DB rollback: down migration `V519` — drop cột `head_sha`, khôi phục `UNIQUE (pr_id, file_path_hash)`; **chấp nhận mất dữ liệu `head_sha` đã backfill** nếu rollback sau khi có dữ liệu mới ghi theo khóa mới (impl-plan §7.2).
5. Chi tiết đầy đủ: `docs/changes/REVIEW-FINDING-DENSITY/impl-plan.md` §7 (Rollback plan).

---

## 7. Danh mục đầu ra

| #   | Tệp                                             | Mục đích                                      |
| --- | ----------------------------------------------- | --------------------------------------------- |
| 1   | `docs/changes/REVIEW-FINDING-DENSITY/spec-pack.md`          | Nguồn tham chiếu duy nhất cho spec & AC       |
| 2   | `docs/changes/REVIEW-FINDING-DENSITY/context.md`            | Context implement thật (method/table tồn tại) |
| 3   | `docs/changes/REVIEW-FINDING-DENSITY/ticket-rules.md`       | Quy tắc bắt buộc/cấm cho ticket này |
| 4   | `docs/changes/REVIEW-FINDING-DENSITY/impl-plan.md`          | Cách tiếp cận triển khai & các bước thực hiện |
| 5   | `docs/changes/REVIEW-FINDING-DENSITY/review-checklist.md`   | Các góc nhìn review                           |
| 6   | `docs/changes/REVIEW-FINDING-DENSITY/self-review.md`        | Kết quả tự kiểm tra của Claude                |
| 7   | `docs/changes/REVIEW-FINDING-DENSITY/test-plan.md`          | Kế hoạch bao phủ kiểm thử                     |
| 8   | `docs/changes/REVIEW-FINDING-DENSITY/test-results.md`       | Kết quả thực thi kiểm thử                     |
| 9   | `docs/changes/REVIEW-FINDING-DENSITY/blackbox-testcases.md` | Các test case black-box                       |
| 10  | `docs/changes/REVIEW-FINDING-DENSITY/test-data.md`          | Dữ liệu test tham chiếu                       |
| 11  | `docs/changes/REVIEW-FINDING-DENSITY/promotion-candidates.md` | Ứng viên cập nhật Living Docs / Failure Mode Index (Phase 8) |

---

## 8. Tương ứng specification・AC

Nguồn: `spec-pack.md` §7 (định nghĩa AC), `self-review.md` §1 (trạng thái + bằng chứng), `test-results.md` §4 (test case tương ứng).

| Nhóm | AC | Trạng thái | Bằng chứng |
| --- | --- | --- | --- |
| Finding classification | AC-FINDING-CLASSIFY-1/v1 | ✅ | `FindingClassifierTest.classify_review_changes_requested_with_threads_produces_one_finding_per_thread`; `GitPrMetadataCollectorServiceTest.collect_pull_request_syncs_findings_via_graphql_after_review_reinsert` |
| Finding classification | AC-FINDING-CLASSIFY-2/v1 | ✅ | `FindingClassifierTest.classify_unknown_or_blank_review_state_produces_no_finding` |
| Finding classification | AC-FINDING-CLASSIFY-3/v1 | ✅ | `FindingClassifierTest.classify_thread_with_question_or_lgtm_body_is_still_a_finding_because_rule_is_metadata_only` |
| Finding classification | AC-FINDING-CLASSIFY-4/v1 | ✅ | `FindingClassifierTest.classify_multiple_threads_in_one_review_each_count_as_separate_findings` |
| Finding classification | AC-FINDING-CLASSIFY-5/v1 | ✅ | `FindingClassifierTest.classify_dedupes_multiple_comments_in_same_thread_into_a_single_finding` |
| Density calculation | AC-DENSITY-CALC-1/v1 | ✅ | `ReviewFindingDensityServiceTest.calculate_n_findings_over_m_changed_lines_returns_ratio_times_1000` |
| Density calculation | AC-DENSITY-CALC-2/v1 | ✅ | `ReviewFindingDensityJdbcAdapter` SUM aggregate across PRs; `PmDashboardControllerTest`/`PmDashboardServiceTest` |
| Density calculation | AC-DENSITY-CALC-3/v1 | ✅ | `ReviewFindingDensityServiceTest.calculate_zero_changed_lines_returns_na_not_zero` |
| Density calculation | AC-DENSITY-CALC-4/v1 | ✅ | `ReviewFindingDensityServiceTest.calculate_zero_findings_with_changed_lines_returns_zero_not_na` |
| Density calculation | AC-DENSITY-CALC-5/v1 | ✅ | `ReviewFindingDensityServiceTest.calculate_breakdown_by_status_does_not_change_total_density` |
| Density calculation | AC-DENSITY-CALC-6/v1 | ✅ | `calculate_rounds_half_up_to_one_decimal` (5.25→5.3), `calculate_rounds_half_up_lower_boundary` (5.24→5.2) |
| Recalculation | AC-RECALC-1/v1 | ✅ | `GitPrMetadataCollectorJdbcAdapterTest.upsertPullRequestChangedFile_conflictTarget_is_pr_id_head_sha_and_file_path` |
| Recalculation | AC-RECALC-2/v1 | ✅ | `FindingWriterTest.writeFindings_preserves_previously_resolved_status_when_pr_resyncs_with_same_thread` |
| Finding status | AC-STATUS-1/v1 | ✅ | `FindingWriterTest.writeFindings_candidate_resolved_on_github_is_created_resolved` |
| Finding status | AC-STATUS-5/v1 | ✅ | `FindingWriterTest.writeFindings_preserves_previously_resolved_status_when_pr_resyncs_with_same_thread` |
| Regression | AC-REG-1/v1 | ✅ | Toàn bộ 10 test case gốc + 2 test case mới của `GitPrMetadataCollectorServiceTest` pass; `mvn test` full suite exit code 0 |

---

## 9. Quan điểm security・operation

Nguồn: `review-checklist.md` §3 (Bảo mật), `self-review.md` §2 (RC-11..15, RC-24, RC-26), `spec-pack.md` §6 (NFR), `impact-analysis.md` §12.

- **Reviewer identity**: mọi bảng/log mới liên quan reviewer join qua `tbl_dim_member_pseudonym` (`member_key`), không dùng raw GitHub user id (RC-12, NFR §6.2, `docs/standards/security.md`).
- **GraphQL query injection**: dùng `variables` JSON (ObjectNode), không nối chuỗi giá trị động vào query string (RC-14).
- **GraphQL auth**: dùng chung token GitHub hiện có (`props.connectors().github().apiToken()`), không hardcode credential mới, không đọc `.env` trực tiếp (RC-15, IMPL-OI-4).
- **Log/audit**: 3 log event ("Finding created", "Finding status changed", "Density recalculated") chỉ chứa `pr_id`/`head_sha`/`findingId`/status/timestamp — không log nội dung comment/diff, không log PII/secret (RC-13, RC-24, RC-26, NFR §6.4).
- **Transaction boundary**: GraphQL call (external HTTP) chạy ngoài DB transaction chính; `FindingWriter` dùng `@Transactional(REQUIRES_NEW)` riêng, tách biệt với transaction chính của `persistPullRequest` (RC-19, `.claude/rules/20-architecture.md`).
- **Availability**: Density service đọc từ dữ liệu đã lưu (`tbl_fact_finding` + `tbl_fact_pull_request_changed_file`), không gọi GitHub API runtime khi mở Ticket Detail (NFR §6.1, RC-16) — tránh phụ thuộc uptime GitHub cho luồng đọc UI. Tuy nhiên luồng **ghi** (ingestion/sync) vẫn phụ thuộc GraphQL — xem "Accepted risk" bên dưới về rủi ro RC-31.
- **Error response**: lỗi liên quan density (nếu có) đi qua `GlobalExceptionHandler`/`ErrorResponse` hiện có, không thêm `ResponseEntity` status code tùy tiện (RC-32).

---

## 10. Accepted risk

Nguồn: `spec-pack.md` §9, `self-review.md` §5.1, `impact-analysis.md` §17.

| # | Rủi ro | Mức độ | Trạng thái chấp nhận |
| --- | --- | --- | --- |
| 1 | Rule metadata-only tính cả thread chỉ là câu hỏi/LGTM/ACK dưới review state hợp lệ là finding → density có thể cao hơn thực tế "số lỗi thật" | High/Medium | **Accepted** — quyết định đã chốt ở OI-003 (Closed); không dùng NLP theo yêu cầu spec §2.2 |
| 2 | GitHub GraphQL API có rate limit/quota riêng khác REST, ảnh hưởng tần suất đồng bộ PR | Medium | **Accepted, chưa đo thực tế** — chưa test với GitHub PAT thật trong phiên làm việc này; theo dõi ở staging/production |
| 3 | Dữ liệu `tbl_fact_pull_request_changed_file` cũ backfill bằng placeholder `'legacy'`, không đối chiếu được với PR version cụ thể | Low | **Accepted** — dữ liệu lịch sử trước khi ticket tồn tại, không phải blocker |
| 4 | `FindingWriter`/`syncFindings` không có try/catch riêng quanh lỗi GraphQL — nếu GraphQL lỗi/timeout, toàn bộ `persistPullRequest` sẽ fail thay vì partial-success | Medium | **Chưa Accepted chính thức** — cần quyết định sản phẩm (xem mục "Human Decisions", "Công việc còn lại" #3) |

---

## 11. Open Issues

Nguồn: `spec-pack.md` §8, §12; `impl-plan.md` §9.

Tất cả Open Issues của ticket này đã **Resolved/Closed**, không còn điểm chặn nào:

| ID | Chủ đề | Trạng thái |
| --- | --- | --- |
| OI-REVIEW-FINDING-DENSITY-001 (P0) | GitHub GraphQL client cho thread identity/resolved-status | **Closed** |
| OI-REVIEW-FINDING-DENSITY-002 (P0) | `head_sha` vào `tbl_fact_pull_request_changed_file`, đổi khóa | **Closed** |
| OI-REVIEW-FINDING-DENSITY-003 (P1) | Finding eligibility metadata-only, không NLP | **Closed** |
| OI-REVIEW-FINDING-DENSITY-004 (P2) | Rounding 1 chữ số thập phân, `HALF_UP` | **Closed** |
| IMPL-OI-1..8 (impl-plan) | Migration version, backfill policy, API route, GraphQL auth, enum, FK/delete-reinsert, test gap, head-sha column | **Resolved** (toàn bộ 8 mục) |

---

## 12. Human Decisions

Nguồn: `impact-analysis.md` §16, `self-review.md` §8.

**Đã quyết định (user xác nhận trước/trong khi implementation):**

1. Finding writer tự quản lý vòng đời finding theo `(pr_id, head_sha, thread_id)`, tự dọn dẹp trước bước review bị xóa-chèn-lại; không sửa FK `tbl_fact_finding` sang `ON DELETE CASCADE`.
2. Backfill `head_sha` cho dữ liệu cũ bằng placeholder sentinel `'legacy'` (vì `tbl_fact_pull_request` không có cột head-sha nào để backfill chính xác).
3. Không tạo API endpoint riêng cho density — nhúng vào response của `GET /tickets/{ticketId}/detail` hiện có.
4. GraphQL client dùng chung token GitHub hiện có, chỉ cần base URL `/graphql` riêng.
5. Bổ sung test JDBC adapter còn thiếu là bắt buộc trước khi đổi unique key (không bỏ qua).

**Còn cần quyết định (chưa có xác nhận cuối cùng tại thời điểm viết báo cáo này):**

1. Chạy thử end-to-end với GitHub GraphQL thật — cần môi trường có GitHub PAT thật, không thể tự quyết trong phiên làm việc này (`self-review.md` §8 #2).
2. Có cần circuit-breaker/try-catch ngay trong ticket này cho lỗi GraphQL trong `syncFindings` (RC-31), hay theo dõi production trước rồi làm sau — đây là quyết định sản phẩm/vận hành, không chỉ kỹ thuật (`self-review.md` §8 #4).

---

## 13. Source Analysis Limitations

Nguồn: `sources.md` "Source Limitations", `test-results.md` §8.

- Tại thời điểm viết spec-pack, không có runtime data nào tồn tại cho `tbl_fact_finding` (chưa có writer) nên rule phân loại không thể được validate thực nghiệm bằng dữ liệu thật — chỉ dựa trên đặc tả metadata-only đã chốt.
- GitHub GraphQL thread-resolved capability chưa được verify trực tiếp với GitHub thật trong bất kỳ giai đoạn nào của ticket này (kể cả sau khi code xong) — rate limit, response shape thật, và quyền truy cập GraphQL endpoint vẫn chưa được đo thực nghiệm.
- Không có tầng integration test chạy DB thật (không có Testcontainers/`@SpringBootTest` full-context với Postgres) ở bất kỳ đâu trong repo — đây là giới hạn có sẵn của dự án, không riêng của ticket này (`test-plan.md` §0). Do đó câu SQL `DISTINCT ON (pr_id) ... ORDER BY collected_at DESC` (latest `head_sha`) chỉ được xác nhận qua so khớp cấu trúc SQL (mock JDBC template), chưa chạy trên Postgres thật.
- Không tìm thấy FE ticket-detail component nào đọc metric ratio tương tự trước đây để xác nhận chính xác convention hiển thị "N/A" — đã dùng lại pattern gần nhất hiện có (`formatFirstCiPassRate`) làm cơ sở, chấp nhận được vì đúng theo `context.md`.

---

## 14. What worked

Nguồn: `self-review.md`, `test-plan.md` §2, §6.

- Tái dùng thành công pattern `AiFindingStatWriter` (isolated `@Component` bean, `@Transactional(REQUIRES_NEW)`) cho `FindingWriter` — tránh đúng lỗi self-invocation proxy bypass đã được cảnh báo sẵn trong javadoc gốc.
- Tái dùng pattern `EvidenceQualityScoreService.scoreByRatio` cho rounding logic của density, nhưng có điều chỉnh đúng chỗ khác biệt bắt buộc (scale=1 thay vì 2, N/A rule dùng `Optional.empty()` thay vì fallback `ZERO`) — không copy y nguyên khi ngữ nghĩa nghiệp vụ khác nhau.
- Tái dùng pattern `formatFirstCiPassRate` cho FE display (`formatReviewFindingDensity`) — giữ nhất quán với cách hệ thống đã hiển thị N/A/rate khác.
- UI-mount verification (`docs/standards/testing.md`) đã lấp đúng khoảng trống: test hàm `formatReviewFindingDensity` thuần túy là chưa đủ, cần thêm test mount `TicketDetailDrawer` thật để xác nhận AC hiển thị UI — việc bổ sung 2 test mount-level mới ở Phase 6 đã đóng đúng gap này.
- Phát hiện sớm xung đột FK/delete-reinsert (IMPL-OI-6) ngay ở giai đoạn đọc code thật (Phase 3), trước khi viết code — tránh được lỗi runtime khó phát hiện nếu chỉ dựa vào spec-pack (spec không hề đề cập chi tiết implementation này).

## 15. What failed

Nguồn: `self-review.md` §5.1, §5.3.

- `FindingWriter.writeFindings`/`deleteFindingsByPrId` không có try/catch quanh `githubReviewThreadPort.fetchReviewThreads` trong `syncFindings` — nếu GraphQL lỗi/timeout, toàn bộ `persistPullRequest` sẽ fail thay vì partial-success/graceful degradation, khác với kỳ vọng NFR §6.3 cho luồng đọc (RC-31, chưa xử lý).
- `detected_at` của một finding bị reset về thời điểm resync hiện tại thay vì giữ nguyên thời điểm phát hiện gốc, vì `FindingWriter.writeFindings` xóa-chèn-lại toàn bộ finding mỗi lần sync (chỉ `status` được carry-over qua `sourceLocationHash`, không phải `detected_at`) — không vi phạm AC nào (spec không yêu cầu preserve), nhưng làm giảm độ chính xác audit nếu cần biết thời điểm phát hiện gốc.
- Không thể chạy end-to-end với GitHub GraphQL thật hoặc Postgres dev thật trong phiên làm việc — mọi xác nhận dừng ở mức mock/unit/integration-mocked, để lại rủi ro chưa đo được (rate limit thật, SQL thật trên Postgres).
- 5 file `docs/architecture/*-map.md` tiếp tục bị stale thêm sau ticket này (đã stale từ trước, ticket này không làm gì để giảm độ lệch) — nợ tài liệu tăng dần qua từng ticket nếu không có backlog riêng xử lý.

## 16. Ứng viên cập nhật Failure Mode Index

Nguồn: `impl-plan.md` §1.5, §9 rủi ro #4; `self-review.md` §5.3 #2.

1. **Failure mode: FK không-cascade + flow xóa-chèn-lại (delete-then-reinsert) trên bảng cha** — khi một bảng con (`tbl_fact_finding`) có FK `NO ACTION` trỏ tới một bảng cha bị xóa-chèn-lại định kỳ (`tbl_fact_review`), phải đảm bảo bảng con được dọn dẹp **trước** bước xóa bảng cha trong cùng flow, nếu không sẽ vi phạm FK ở lần sync tiếp theo. Đây là pattern phát hiện ngoài spec-pack (không được spec/context đề cập trước), chỉ lộ ra khi đọc code thật ở Phase 3 — đáng đưa vào checklist chuẩn cho các ticket tương lai có flow đồng bộ dữ liệu tương tự.
2. **Failure mode: external API call (không transaction hóa được) nằm giữa một transaction ghi DB chính** — khi một bước ghi mới (Finding sync) cần gọi API bên ngoài (GraphQL) và ghi kết quả vào DB, nếu không cô lập rõ ràng (try/catch + fallback) thì lỗi/timeout của API ngoài sẽ lan ra làm fail toàn bộ flow ghi chính (ở đây là toàn bộ `persistPullRequest`), kể cả các bước không liên quan (upsert PR/commit/changed-file đã thành công trước đó). Ticket này chưa xử lý dứt điểm (RC-31) — nên trở thành mục checklist bắt buộc "đánh giá blast radius khi 1 external call mới lỗi" cho các ticket sau có tích hợp API ngoài vào flow ghi hiện có.

## 17. Ứng viên cập nhật Living Docs

Nguồn: `impact-analysis.md` §3, §14; `self-review.md` §5.3 #1.

5 tài liệu kiến trúc sau đã stale từ trước ticket này và tiếp tục không mô tả các thành phần mới được thêm bởi REVIEW-FINDING-DENSITY — đề xuất đưa vào backlog cập nhật riêng (không phải blocker của ticket này):

- `docs/architecture/service-layer-map.md` — thiếu `GitPrMetadataCollectorService`, `FindingClassifier`/`FindingWriter`, `ReviewFindingDensityService`.
- `docs/architecture/repository-db-map.md` — thiếu `tbl_fact_finding` (writer mới), `tbl_fact_pull_request_changed_file` (cột `head_sha` mới).
- `docs/architecture/route-api-map.md` — thiếu field mới `reviewFindingDensity` trên `GET /tickets/{ticketId}/detail`.
- `docs/architecture/entrypoint-map.md` — thiếu GraphQL client mới (`GithubReviewThreadAdapter`) như một entry point ra ngoài hệ thống.
- `docs/architecture/fe-be-contract-map.md` — thiếu contract mới của field `reviewFindingDensity`.

Chi tiết đề xuất cụ thể: xem `docs/changes/REVIEW-FINDING-DENSITY/promotion-candidates.md`.
