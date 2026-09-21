# Test Results

**Ticket ID**: REVIEW-FINDING-DENSITY
**Create date**: 2026-09-10 15:30:00
**Author**: nvt_dung
**Update date**: 2026-09-15 17:20:00

## 1. Execution Environment

| item | value |
|---|---|
| OS | Windows 10 Pro 10.0.19045 |
| Backend | Java 21, Spring Boot 3.4.1, Maven (surefire 3.5.4) |
| Frontend | Node.js, Vite 6, Vitest, Playwright |
| DB | Không có DB thật trong scope các lệnh dưới đây — mọi test BE mock `NamedParameterJdbcTemplate`/port interfaces; mọi test E2E mock API qua `page.route()` (xem `test-plan.md` mục 0, 8) |
| GitHub API | Không gọi GitHub thật — `GithubReviewThreadPort` được mock trong mọi test |

## 2. Executed Command

| command | result | log/evidence | note |
|---|---|---|---|
| `cd EDCAP_BE && mvn test -Dtest=PmDashboardServiceTest,PmDashboardControllerTest,ReviewFindingDensityJdbcAdapterTest,ReviewFindingDensityServiceTest` | PASS (exit 0) | Chạy trước để xác nhận nhanh 4 test class liên quan trực tiếp tới thay đổi lần này | Bao gồm 4 test mới (2 trong `PmDashboardServiceTest`, 2 trong `PmDashboardControllerTest`) + 1 file mới `ReviewFindingDensityJdbcAdapterTest` (3 test) |
| `cd EDCAP_BE && mvn test` | PASS (exit 0) | `target/surefire-reports/*.txt` — tổng hợp: **772 tests run, 0 failures, 0 errors, 0 skipped** | Toàn bộ backend suite, xác nhận không regress (AC-REG-1) |
| `cd EDCAP_FE && npx vitest run "src/__ tests __/pm-dashboard"` | PASS | 8 test files, **60 tests passed** (0 failed) — bao gồm `TicketDetailDrawer.test.tsx` nay có 6 test (2 test mới cho density) | `stderr` có warning "Not implemented: window.getComputedStyle" từ jsdom (thư viện Ant Design nội bộ dùng `useScrollLocker`) — pre-existing, không liên quan thay đổi lần này, không làm fail test |
| `cd EDCAP_FE && npx tsc --noEmit` | PASS (exit 0) | Không có lỗi type | Xác nhận field `reviewFindingDensity` khớp type giữa fixture test và `PmDashboardTicketDetail` |
| `cd EDCAP_FE && npx playwright test pm-dashboard` | PASS | **4 passed, 1 skipped** (test 2 "Search updates the ticket table" đã `test.skip()` từ trước, không liên quan ticket này) | Test 3 có assertion mới xác nhận card "Review Finding Density" hiển thị "4.2 findings/KLOC" trong drawer thật |

## 3. Summary of Results

Tất cả lệnh kiểm thử ở mục 2 đều PASS. Không có regression nào ở test suite hiện có (772 test BE, 60 test FE unit, 4/5 test E2E đều pass — 1 skip từ trước không liên quan). 5 gap kiểm thử xác định ở `test-plan.md` mục 7 đã được lấp:

1. `ReviewFindingDensityJdbcAdapterTest` (mới, 3 test) — xác nhận SQL aggregate đúng (latest `head_sha`, không loại `head_sha='legacy'`).
2. `PmDashboardServiceTest` (2 test mới) — xác nhận `reviewFindingDensity` được embed đúng từ `ReviewFindingDensityService`, kể cả case N/A.
3. `PmDashboardControllerTest` (2 test mới) — xác nhận JSON shape `reviewFindingDensity` đúng ở tầng contract (case có giá trị và case N/A).
4. `TicketDetailDrawer.test.tsx` (2 test mới) — xác nhận UI mount thật hiển thị đúng "N/A" hoặc "X.X findings/KLOC" + breakdown OPEN/RESOLVED (UI-mount verification theo `docs/standards/testing.md`).
5. `pm-dashboard.spec.ts` (1 assertion mới) — xác nhận e2e mock-based hiển thị đúng giá trị density trong drawer thật.

Các mục cố ý bỏ qua (integration test DB thật, resilience test cho lỗi GraphQL trong sync, manual verification với GitHub/DB thật) được liệt kê ở `test-plan.md` mục 8 kèm lý do — không phải lỗi, mà là quyết định phạm vi có ghi chú.

## 4. List of Passes

| TC ID | test | result | note |
|---|---|---|---|
| TC-REVIEW-FINDING-DENSITY-1 | `EDCAP_BE mvn test` (full suite, 772 tests) | PASS | Bao gồm toàn bộ test AC-FINDING-CLASSIFY, AC-DENSITY-CALC, AC-RECALC, AC-STATUS, AC-REG-1 |
| TC-REVIEW-FINDING-DENSITY-2 | `ReviewFindingDensityJdbcAdapterTest` (3 test, mới) | PASS | SQL-shape cho `findTotalChangedLines`/`findFindingCountsByStatus` |
| TC-REVIEW-FINDING-DENSITY-3 | `PmDashboardServiceTest.detail_embedsReviewFindingDensityFromServiceRegardlessOfRepositoryValue` (mới) | PASS | RC-38 — service layer |
| TC-REVIEW-FINDING-DENSITY-4 | `PmDashboardServiceTest.detail_embedsNullDensityWhenTotalChangedLinesIsZero` (mới) | PASS | AC-DENSITY-CALC-3 — service layer |
| TC-REVIEW-FINDING-DENSITY-5 | `PmDashboardControllerTest.detailReturns200WithReviewFindingDensityPopulated` (mới) | PASS | RC-38, AC-DENSITY-CALC-2/-5 — contract layer |
| TC-REVIEW-FINDING-DENSITY-6 | `PmDashboardControllerTest.detailReturns200WithReviewFindingDensityNullWhenNoChangedLines` (mới) | PASS | AC-DENSITY-CALC-3 — contract layer |
| TC-REVIEW-FINDING-DENSITY-7 | `TicketDetailDrawer.test.tsx` — "shows N/A for review finding density..." (mới) | PASS | AC-DENSITY-CALC-3 — UI-mount |
| TC-REVIEW-FINDING-DENSITY-8 | `TicketDetailDrawer.test.tsx` — "renders the review finding density value and OPEN/RESOLVED breakdown..." (mới) | PASS | AC-DENSITY-CALC-2/-5 — UI-mount |
| TC-REVIEW-FINDING-DENSITY-9 | `pm-dashboard.spec.ts` test 3, step 6 (mới assertion) | PASS | AC-DENSITY-CALC-2 — E2E mock-based |
| TC-REVIEW-FINDING-DENSITY-10 | `EDCAP_FE npx tsc --noEmit` | PASS | Type-check toàn bộ FE |

## 5. List of Fails

| TC ID | test | cause | action | status |
|---|---|---|---|---|
| — | (không có) | — | — | — |

## 6. Bugs Fixed

| bug | fix | evidence |
|---|---|---|
| — (không phát hiện bug mới trong lúc viết/chạy test phase này) | — | — |

## 7. Not yet fixed / Pending

- Xem `self-review.md` mục 5.3 #2 (RC-31): `syncFindings` chưa có try/catch quanh lỗi GraphQL — quyết định sản phẩm còn treo (`self-review.md` mục 8 #4), không thuộc phạm vi thêm test ở phase này (xem `test-plan.md` mục 8 #2 về lý do không viết test hợp thức hóa hành vi hiện tại).
- Chạy thử end-to-end với GitHub GraphQL thật + Postgres dev thật (`self-review.md` §3.5 scenario 1-3) vẫn cần môi trường staging, chưa thực hiện được trong phiên làm việc này.

## 8. Test cannot be executed and reason

| TC ID | reason | risk | alternative evidence |
|---|---|---|---|
| TC-REVIEW-FINDING-DENSITY-DB-IT | Integration test với Postgres thật cho ingestion flow / `GET /tickets/{ticketId}/detail` — không có hạ tầng Testcontainers/DB thật ở bất kỳ đâu trong repo (giới hạn có sẵn của dự án) | Trung bình — logic SQL (đặc biệt `DISTINCT ON` latest head_sha) chỉ được xác nhận qua so khớp chuỗi SQL, chưa chạy trên Postgres thật | `ReviewFindingDensityJdbcAdapterTest` (SQL-shape, mock JDBC template) + review thủ công câu SQL bởi reviewer con người (đã ghi ở self-review.md mục 7 "Phần AI đã suy đoán" #4) |
| TC-REVIEW-FINDING-DENSITY-GITHUB-LIVE | Chạy thử với GitHub GraphQL API thật (rate limit, response shape thật) | Trung bình — chưa đo rate limit GraphQL thực tế | Toàn bộ `FindingWriterTest`/`GitPrMetadataCollectorServiceTest` dùng mock GraphQL response theo đúng shape đã biết |

## 9. Remaining risk

- Metadata-only false positive (thread chỉ là câu hỏi/LGTM vẫn tính là finding) — rủi ro đã được chấp nhận ở spec-pack §9 rủi ro #3, không phải gap kiểm thử.
- GraphQL rate limit khác REST — chưa đo được trong môi trường không có GitHub PAT thật; theo dõi ở giai đoạn staging/production theo `self-review.md` mục 5.1 #2.
- `syncFindings` không có try/catch quanh lỗi GraphQL (RC-31) — nếu GraphQL lỗi/timeout khi ingest, toàn bộ `persistPullRequest` sẽ fail thay vì partial-success; đây là quyết định sản phẩm còn treo, không phải điểm kiểm thử bị bỏ sót.

## 10. Final Test Verdict

- **PASS** — phạm vi: automated suite (BE unit + BE MockMvc/mocked-adapter "IT" + FE unit/UI-mount + FE type-check + E2E mock-based Playwright), theo đúng quy ước IT ở `test-plan.md` mục 0. Không bao gồm: integration test DB/GitHub thật (xem mục 8) và manual verification trên môi trường staging thật (xem mục 7) — các phần này vẫn cần review/QA xác nhận riêng trước khi release, theo `self-review.md` mục 8.
