# Kế hoạch kiểm thử — REVIEW-FINDING-DENSITY (Review Finding Density)

- **Ticket:** REVIEW-FINDING-DENSITY
- **Trạng thái:** Final
- **Tạo ngày:** 2026-09-10 15:30:00
- **Cập nhật ngày:** 2026-09-15 16:15:08

> Mỗi AC phải được bao phủ bởi ít nhất một loại kiểm thử.
> Nguồn tham chiếu: `spec-pack.md`, `impact-analysis.md`, `impl-plan.md`, `review-checklist.md`, `self-review.md`, `docs/standards/testing.md`.

---

## 0. Quy ước cho ticket này

Repo này **không có tầng integration test chạy DB thật** (không có Testcontainers/`@SpringBootTest` full-context với Postgres thật ở bất kỳ đâu trong `EDCAP_BE/src/test`). Đây là giới hạn có sẵn của dự án, không phải do ticket này gây ra. Vì vậy trong ma trận dưới đây, cột **IT** cho ticket này được hiểu là:

- MockMvc (`standaloneSetup`) cho tầng controller, và
- Test adapter JDBC với `NamedParameterJdbcTemplate` bị mock (xác nhận cấu trúc SQL/param, không chạy SQL thật) —

theo đúng tiền lệ duy nhất đã có trong codebase (`GitPrMetadataCollectorJdbcAdapterTest`). Không giới thiệu hạ tầng test mới (Testcontainers, DB thật) trong phase này.

## 1. Ma trận bao phủ

| #   | AC                | FE UT | BE UT | IT (MockMvc/mocked-adapter) | E2E | Black-box |
| --- | -------------------- | ------- | ------- | -------- | ----- | ----------- |
| 1   | AC-FINDING-CLASSIFY-1/v1 | | ✓ | ✓ | | ✓ |
| 2   | AC-FINDING-CLASSIFY-2/v1 | | ✓ | ✓ | | ✓ |
| 3   | AC-FINDING-CLASSIFY-3/v1 | | ✓ | | | ✓ |
| 4   | AC-FINDING-CLASSIFY-4/v1 | | ✓ | ✓ | | ✓ |
| 5   | AC-FINDING-CLASSIFY-5/v1 | | ✓ | ✓ | | ✓ |
| 6   | AC-DENSITY-CALC-1/v1 | | ✓ | ✓ | | ✓ |
| 7   | AC-DENSITY-CALC-2/v1 | ✓ | ✓ | ✓ | ✓ | ✓ |
| 8   | AC-DENSITY-CALC-3/v1 | ✓ | ✓ | ✓ | | ✓ |
| 9   | AC-DENSITY-CALC-4/v1 | | ✓ | | | ✓ |
| 10  | AC-DENSITY-CALC-5/v1 | ✓ | ✓ | ✓ | | |
| 11  | AC-DENSITY-CALC-6/v1 | | ✓ | | | ✓ |
| 12  | AC-RECALC-1/v1 | | ✓ | ✓ | | |
| 13  | AC-RECALC-2/v1 | | ✓ | ✓ | | |
| 14  | AC-STATUS-1/v1 | | ✓ | ✓ | | |
| 15  | AC-STATUS-5/v1 | | ✓ | | | |
| 16  | AC-REG-1/v1 | | ✓ | ✓ | ✓ | ✓ |

## 2. Unit test FE

| #   | Tệp kiểm thử | Nội dung kiểm thử | AC  |
| --- | -------------- | -------------------- | ----- |
| 1   | `EDCAP_FE/src/__ tests __/pm-dashboard/SummaryCards.test.tsx` (đã có, describe `formatReviewFindingDensity`, 3 case) | `null`/`undefined` → `"N/A"`; số → `"X.X findings/KLOC"` (1 chữ số thập phân) | AC-DENSITY-CALC-3, -6 |
| 2   | `EDCAP_FE/src/__ tests __/pm-dashboard/TicketDetailDrawer.test.tsx` (**mới, thêm lần này**) — `shows N/A for review finding density when reviewFindingDensity is null (AC-DENSITY-CALC-3)` | Mount toàn bộ drawer với `reviewFindingDensity: null` → card "Review Finding Density" hiển thị "N/A" | AC-DENSITY-CALC-3 |
| 3   | `TicketDetailDrawer.test.tsx` (**mới**) — `renders the review finding density value and OPEN/RESOLVED breakdown when populated (AC-DENSITY-CALC-2, -5)` | Mount drawer với density = 5.3, breakdown open=2/resolved=1 → hiển thị đúng "5.3 findings/KLOC", "Open (2)", "Resolved (1)" | AC-DENSITY-CALC-2, -5 |

**Trọng tâm:** theo `docs/standards/testing.md` mục "UI-mount verification" — AC hiển thị UI chỉ được coi là Implemented khi có test xác nhận component **mount trên đúng trang thật** (`TicketDetailDrawer`), không chỉ test hàm format thuần túy. Mục #1 (đã có từ trước) chỉ test hàm `formatReviewFindingDensity`; mục #2/#3 (thêm lần này) lấp khoảng trống mount-level còn thiếu.

## 3. Unit test BE

| #   | Lớp kiểm thử | Nội dung kiểm thử | AC  |
| --- | -------------- | -------------------- | ----- |
| 1   | `FindingClassifierTest` (đã có, 6 test) | Review state hợp lệ (`APPROVED`/`CHANGES_REQUESTED`/`REVIEW_REQUIRED`, kể cả state hợp lệ bất kỳ) → 1 finding/thread; `UNKNOWN`/trống → không finding; thread chỉ là câu hỏi/LGTM vẫn tính finding (metadata-only); nhiều thread → nhiều finding; nhiều comment cùng thread → dedupe (GraphQL `reviewThreads` tự nhiên trả 1 node/thread) | AC-FINDING-CLASSIFY-1..5 |
| 2   | `ReviewFindingDensityServiceTest` (đã có, 7 test) | N/M×1000; N/A khi M=0; 0 finding với M>0 → 0 (không phải N/A); breakdown OPEN/RESOLVED không đổi tổng; bỏ qua status khác OPEN/RESOLVED; rounding HALF_UP (5.25→5.3, 5.24→5.2, biên dưới) | AC-DENSITY-CALC-1, -3, -4, -5, -6 |
| 3   | `ReviewFindingDensityJdbcAdapterTest` (**mới, thêm lần này**, 3 test) | Xác nhận SQL của `findTotalChangedLines` dùng `DISTINCT ON (pr_id) ... ORDER BY pr_id, collected_at DESC` (chỉ tính head_sha mới nhất, AC-RECALC-1) và **không** lọc bỏ `head_sha = 'legacy'` (RC-20 — dữ liệu backfill vẫn được cộng); trả về 0 khi không có row; `findFindingCountsByStatus` group theo `status`, chỉ `IN ('OPEN', 'RESOLVED')` | AC-DENSITY-CALC-1, -2; RC-20 |
| 4   | `FindingWriterTest` (đã có, 5 test) | Candidate resolved trên GitHub → tạo `RESOLVED` ngay; candidate chưa resolve → tạo `OPEN`; resync cùng thread giữ nguyên status đã `RESOLVED` trước đó (không reset); xóa toàn bộ finding cũ theo `pr_id` trước khi ghi lại; xóa an toàn khi PR chưa có finding | AC-RECALC-2, AC-STATUS-1, AC-STATUS-5 |
| 5   | `GitPrMetadataCollectorJdbcAdapterTest` (đã có, 8 test, gồm test mới cho conflict-target) | `upsertPullRequestChangedFile` dùng conflict target `(pr_id, head_sha, file_path)`; CRUD review/review comment; lookup member key | AC-RECALC-1 |
| 6   | `GitPrMetadataCollectorServiceTest` (đã có, 12 test, gồm 2 test mới) | `deleteFindingsByPrId` chạy **trước** `deleteReviewsByPrId` (tránh vi phạm FK); GraphQL fetch + classify + write chạy **sau** khi review/comment được reinsert; toàn bộ 10 test case gốc không sửa vẫn pass | AC-FINDING-CLASSIFY-1, AC-REG-1 |

**Trọng tâm:** giá trị biên (denominator=0, numerator=0), rounding HALF_UP, dedupe theo thread identity thật, và (mới) tính đúng của câu SQL aggregate mà `ReviewFindingDensityServiceTest` không thể chạm tới vì nó mock thẳng port.

## 4. Integration test API (MockMvc / mocked-adapter — xem mục 0)

| #   | Lớp/Endpoint | Kịch bản                                       | AC  |
| --- | ---------- | --------------------------------------------------- | ----- |
| 1   | `PmDashboardServiceTest.detail_embedsReviewFindingDensityFromServiceRegardlessOfRepositoryValue` (**mới**) | `reviewFindingDensityService.calculate(ticketId)` được stub trả 1 giá trị cụ thể; `service.detail(...)` phải trả đúng giá trị đó, bất kể repository snapshot mang gì | AC-DENSITY-CALC-2, RC-38 |
| 2   | `PmDashboardServiceTest.detail_embedsNullDensityWhenTotalChangedLinesIsZero` (**mới**) | Density = N/A (`density=null`) phải đi xuyên suốt tới kết quả `detail(...)`, không bị đổi thành 0 | AC-DENSITY-CALC-3 |
| 3   | `PmDashboardControllerTest.detailReturns200WithReviewFindingDensityPopulated` (**mới**) | `GET /tickets/{ticketId}/detail` trả đúng JSON shape `reviewFindingDensity.{density,unit,totalFindings,totalChangedLines,breakdown.{open,resolved}}` | AC-DENSITY-CALC-2, -5, RC-38 |
| 4   | `PmDashboardControllerTest.detailReturns200WithReviewFindingDensityNullWhenNoChangedLines` (**mới**) | `reviewFindingDensity.density` serialize thành JSON `null` (không phải `0` hay bị lược bỏ khỏi payload) | AC-DENSITY-CALC-3 |
| 5   | `GitPrMetadataCollectorServiceTest` (đã có) | Đồng bộ PR đầy đủ (review/comment/changed-file/finding) không lỗi FK qua 2 lần sync liên tiếp | AC-RECALC-1, AC-STATUS-1, AC-REG-1 |

## 5. Kiểm thử E2E (Playwright)

| #   | Kịch bản                  | Các bước | Kết quả mong đợi | AC  |
| --- | ---------------------------- | ---------- | ------------------- | ----- |
| 1   | `pm-dashboard.spec.ts` test 3 "Clicking a ticket row opens the read-only detail drawer" — step 6 (**mới, thêm lần này**) | Mở Ticket Detail (mock `DETAIL_RESPONSE.reviewFindingDensity` = density 4.2, breakdown open=2/resolved=1) | Card "Review Finding Density" hiển thị "4.2 findings/KLOC" trong drawer thật | AC-DENSITY-CALC-2 |
| 2   | `pm-dashboard.spec.ts` test 1 "Dashboard loads..." (đã có, không đổi) | Đồng bộ PR đầy đủ không bị regress — dashboard load, KPI cards, ticket row vẫn hiển thị đúng sau khi thêm field `reviewFindingDensity` vào response | AC-REG-1 |

## 6. Đã được test hiện có bảo đảm (không cần code mới)

Nhóm test sau đã tồn tại từ khi implement Part A-E (`self-review.md` mục 0) và đã được xác minh lại (đọc trực tiếp file, không chỉ tin theo self-review) là bao phủ đúng các AC tương ứng:

- `FindingClassifierTest` — 6 method, bao phủ AC-FINDING-CLASSIFY-1..5.
- `ReviewFindingDensityServiceTest` — 7 method, bao phủ AC-DENSITY-CALC-1, -3, -4, -5, -6.
- `FindingWriterTest` — 5 method, bao phủ AC-RECALC-2, AC-STATUS-1, AC-STATUS-5.
- `GitPrMetadataCollectorJdbcAdapterTest` — 8 method (bao gồm test conflict-target `(pr_id, head_sha, file_path)`), bao phủ AC-RECALC-1.
- `GitPrMetadataCollectorServiceTest` — 12 method (bao gồm thứ tự xóa finding/review và gọi GraphQL sau reinsert), bao phủ AC-FINDING-CLASSIFY-1, AC-REG-1.
- `SummaryCards.test.tsx` — 3 case cho `formatReviewFindingDensity`, bao phủ format/N/A rule ở mức hàm thuần túy.

## 7. Test thêm lần này (Phase 6)

| #   | File | Nội dung | AC / RC đóng lại |
| --- | ---- | -------- | ---------------- |
| 1   | `EDCAP_BE/.../infrastructure/persistence/adapter/ReviewFindingDensityJdbcAdapterTest.java` (mới) | SQL-shape test cho `findTotalChangedLines`/`findFindingCountsByStatus` | Điểm "AI đã suy đoán" #4 trong self-review (DISTINCT ON latest head_sha), RC-20 |
| 2   | `PmDashboardServiceTest.java` (sửa) | 2 test mới xác nhận `reviewFindingDensity` được embed đúng từ service, kể cả case N/A; refactor fixture trùng lặp thành `detailFixture(...)` | RC-38 |
| 3   | `PmDashboardControllerTest.java` (sửa) | 2 test mới xác nhận JSON shape `reviewFindingDensity` (case có giá trị và case N/A) | RC-38, AC-DENSITY-CALC-2, -5 |
| 4   | `TicketDetailDrawer.test.tsx` (sửa) | 2 test mount-level mới (N/A case, populated case với breakdown) | UI-mount verification cho AC-DENSITY-CALC-2, -3, -5 |
| 5   | `pm-dashboard.spec.ts` (sửa) | Thêm `reviewFindingDensity` vào `DETAIL_RESPONSE` fixture + 1 assertion trong test đã có | AC-DENSITY-CALC-2 (E2E) |

## 8. Test cố ý bỏ qua và lý do

| #   | Hạng mục bỏ qua | Lý do |
| --- | ---------------- | ----- |
| 1   | Integration test với DB thật/Testcontainers cho ingestion flow hoặc `GET /tickets/{ticketId}/detail` | Không có hạ tầng này ở bất kỳ đâu trong repo hiện tại (đã xác minh: 0 usage Testcontainers toàn repo); việc thêm hạ tầng test mới nằm ngoài phạm vi "viết test cho ticket", tránh thay đổi ngoài scope. Theo dõi như giới hạn có sẵn của dự án. |
| 2   | Test resilience cho lỗi GraphQL trong `syncFindings` (RC-31) | Code hiện tại chưa có try/catch quanh `githubReviewThreadPort.fetchReviewThreads` — đây là quyết định thiết kế còn treo, tự `self-review.md` mục 8 đã yêu cầu con người quyết định có cần circuit-breaker/try-catch ngay bây giờ hay không. Viết test khẳng định hành vi "lỗi lan ra toàn bộ sync" hiện tại sẽ là hợp thức hóa nợ kỹ thuật thành đặc tả, không phải kiểm thử NFR — bỏ qua, giữ nguyên là follow-up cần quyết định sản phẩm. |
| 3   | DATA-E2 ("GraphQL response lỗi khi đồng bộ") áp cho *density read path* | `ReviewFindingDensityService.calculate()` không bao giờ gọi GraphQL — chỉ đọc `tbl_fact_finding`/`tbl_fact_pull_request_changed_file` đã lưu (đúng NFR §6.1). DATA-E2 chỉ áp dụng cho luồng ingestion/sync (đã có test dùng mock ở `FindingWriterTest`/`GitPrMetadataCollectorServiceTest`), không áp dụng cho luồng tính density khi mở Ticket Detail — ghi rõ để không bị đếm nhầm là AC chưa test. |
| 4   | Manual verification matrix scenario 1-3 (`self-review.md` §3.5) — chạy thử với GitHub GraphQL thật + Postgres dev thật | Cần môi trường staging có GitHub PAT thật và Docker Compose Postgres, ngoài khả năng của phiên làm việc hiện tại. Đã có unit/integration test (mock) bao phủ logic; cần QA verify thủ công trước khi release, ghi nhận ở `self-review.md` mục 8. |

## 9. Policy test data

- Tái dùng pattern có sẵn trong repo: Java record builder với UUID cố định (như đã dùng trong `GitPrMetadataCollectorServiceTest`/`PmDashboardServiceTest`) — không tạo factory/builder mới.
- Giá trị biên tái dùng từ `ReviewFindingDensityServiceTest` (đã có): changed lines = 0 (N/A), 0 finding với changed lines > 0 (= 0, không phải N/A), rounding 5.25/5.24 (HALF_UP). Không tạo lại các case này ở nơi khác.
- Fixture FE tuân theo shape cục bộ có sẵn của từng file test (`DETAIL_RESPONSE` cho Playwright, object fixture inline cho Vitest) — chỉ thêm field `reviewFindingDensity` vào fixture đã có, không tạo fixture mới.
- Không dùng dữ liệu production thật, không dùng GitHub token/username thật; định danh reviewer trong test data chỉ dùng giá trị giả lập (pseudonym/member_key), theo `.claude/rules/30-security.md`.

## 10. Các lệnh chạy kiểm thử

```bash
# BE — chạy nhóm class liên quan density trước (nhanh)
cd EDCAP_BE && mvn test -Dtest=PmDashboardServiceTest,PmDashboardControllerTest,ReviewFindingDensityJdbcAdapterTest,ReviewFindingDensityServiceTest

# BE — full suite, xác nhận không regress (AC-REG-1)
cd EDCAP_BE && mvn test

# FE — nhóm test pm-dashboard
cd EDCAP_FE && npx vitest run "src/__ tests __/pm-dashboard"

# FE — type-check
cd EDCAP_FE && npx tsc --noEmit

# E2E (Playwright)
cd EDCAP_FE && npx playwright test pm-dashboard
```

## 11. Ghi chú / Ràng buộc

- Theo `.claude/rules/40-testing.md`/`docs/standards/testing.md`: unit test BE mock **port interfaction** (Mockito), không mock domain entity; `ArchitectureTest` phải giữ green (đã xác nhận qua `mvn test` full suite pass).
- Không dùng production DB trong unit test; xem mục 0 và mục 8 #1 về giới hạn integration test DB thật của dự án.
- Kết quả chạy thật của các lệnh ở mục 10 được lưu ở `test-results.md`.
