# Test Plan — BLACKBOX-COVERAGE

- **Ticket:** BLACKBOX-COVERAGE
- **Trạng thái:** Draft
- **Tạo ngày:** 2026-09-07 00:00
- **Cập nhật ngày:** 2026-09-07 00:00
- **Nguồn đối chiếu:** [spec-pack.md](spec-pack.md), [impl-plan.md](impl-plan.md), [review-checklist.md](review-checklist.md)

## 1. Mục tiêu

Đảm bảo mỗi AC của BLACKBOX-COVERAGE có ít nhất một test bảo vệ AC/boundary thật, không chỉ chép lại code. Ticket này chỉ đổi backend (spec §2: không đổi UI/UX), nên FE UT/E2E đóng vai trò regression guard cho màn hình tiêu thụ field `blackboxCoveragePercent`, không phải nguồn xác nhận AC chính.

## 2. Quy ước tuân theo

- BE UT: mock port interface (`docs/standards/testing.md`), tên lớp `{Subject}Test`, đặt cạnh package tương ứng trong `src/test/java`.
- BE API IT: đặt trong `src/test/IntegrationTest/java`, tên lớp `*ApiIntegrationTest`, chạy qua `maven-failsafe-plugin` (`mvn verify`), theo đúng pattern `DataOpsDashboardApiIntegrationTest` (real Spring Security filter chain, service mock ở boundary).
- BE DB IT: đặt trong `src/test/IntegrationTest/java`, tên lớp `*JdbcAdapterIntegrationTest`, dùng Testcontainers Postgres (`@Testcontainers(disabledWithoutDocker = true)`), tự skip khi không có Docker.
- FE UT: `src/<module>/__tests__/<Subject>.test.tsx`, Vitest + Testing Library, mock `react-i18next`.
- E2E: `e2e_tests/tests/<feature>/<feature>.spec.ts`, Playwright, mock API qua `page.route()`.

## 3. Ma trận AC × test type

| AC | Nội dung | BE UT | API IT | FE UT | E2E |
| --- | --- | --- | --- | --- | --- |
| AC-1 | Scanner parse `blackbox-testcases.md`, persist case/AC-mapping/observation | `BlackboxTestcasesMarkdownParserTest`, `ArtifactScannerServiceTest`, `BlackboxCoverageJdbcAdapterTest` | — (ingestion nội bộ, không qua HTTP) | — | — |
| AC-2 | `TestResultsParseService` map `SUCCESS/FAILED/SKIPPED/khác` → `PASS/FAIL/SKIP/NOT_RUN` | `TestResultsParseServiceTest`, `BlackboxCoverageJdbcAdapterTest` | — | — | — |
| AC-3 | `findBlackboxCoverageCounts` trả đủ counts AC/observation/priority | `QaDashboardJdbcAdapterTest` (SQL shape) | `QaDashboardJdbcAdapterIntegrationTest` (DB thật, Postgres testcontainer) | — | — |
| AC-4 | Priority weight `P0=5/P1=3/P2=1`, chỉ tính `DIRECT` | `QaDashboardServiceTest` (worked example), `QaDashboardJdbcAdapterTest` | `QaDashboardJdbcAdapterIntegrationTest` (DIRECT vs RELATED, tổng điểm 9→6 với dữ liệu seed thật) | — | — |
| AC-5 | `computeBlackboxOverallCoverage` khớp ví dụ spec `89.4` | `QaDashboardServiceTest.getSummary_blackboxCoverage_matchesSpecPackWorkedExample` | `QaDashboardApiIntegrationTest.summary_authenticatedQaUser_returnsOkWithBlackboxCoveragePercent` (89.4 qua JSON thật) | — | — |
| AC-6 | Zero-denominator/`SKIP`/`NOT_RUN`/rounding | `QaDashboardServiceTest` (3 test zero-denominator riêng biệt + log-capture), `TestResultsParseServiceTest` | `QaDashboardApiIntegrationTest.summary_zeroBlackboxCoverage_returnsZeroNotNullOrError` | — | — |
| AC-7 | Blackbox Coverage tách biệt AC-Test Coverage | `QaDashboardJdbcAdapterTest.findBlackboxCoverageCounts_neverReadsAcTestCoverageTables` (mới) | `QaDashboardJdbcAdapterIntegrationTest` (schema riêng, không đụng bảng cũ) | — | — |
| AC-8 | DTO `blackboxCoveragePercent` giữ nguyên tên/kiểu/vị trí | `QaDashboardControllerTest` | `QaDashboardApiIntegrationTest` (qua real HTTP + auth) | `QaSummaryCards.test.tsx` (boundary values không vỡ FE type contract) | S-E2E-1 (dashboard load chung, không assert riêng field vì FE chưa render — xem §5) |
| Permission gate (`requireQaAccess`/`requireAnyAccess`, dùng chung bởi mọi AC ở trên) | ADMIN bypass, đúng project role, sai role, role khác project, caller null | `QaDashboardServiceTest` (8 test mới) | `QaDashboardApiIntegrationTest` (401 không token, 401 token sai, 403 sai role, 200 đúng role) | — | — |

## 4. Chi tiết theo test type

### 4.1 BE UT

| File | Test mới trong lần này | Bảo vệ gì |
| --- | --- | --- |
| `QaDashboardServiceTest.java` | 8 test `requireAnyAccess`/`requireQaAccess` | Boundary permission: null caller, null role, ADMIN bypass, thiếu projectId, role đúng/sai, role đúng nhưng sai project |
| `QaDashboardJdbcAdapterTest.java` | `findBlackboxCoverageCounts_neverReadsAcTestCoverageTables` | AC-7 — khoá SQL không được tham chiếu `tbl_fact_test_case`/`tbl_fact_ac_test_coverage` |

Các test formula/zero-denominator/log-warning khác (F1–F5) đã có sẵn từ vòng review trước — xem [ai-review.md](ai-review.md).

### 4.2 BE API IT (mới)

`EDCAP_BE/src/test/IntegrationTest/java/com/sdd/platform/web/rest/QaDashboardApiIntegrationTest.java`:

- `summary_withoutBearerToken_returns401` — không token → 401 qua security filter thật.
- `summary_withInvalidBearerToken_returns401` — token sai → 401.
- `summary_authenticatedWithoutQaRoleOnProject_returns403` — user role `DEV` gọi `/summary?projectId=...` → 403 `Component.Permission.Denied` qua `GlobalExceptionHandler` thật.
- `summary_authenticatedQaUser_returnsOkWithBlackboxCoveragePercent` — user `QA` → 200, JSON có `blackboxCoveragePercent=89.4`.
- `summary_zeroBlackboxCoverage_returnsZeroNotNullOrError` — mọi thành phần = 0 → JSON trả `0.0`, không `null`/lỗi.

Chỉ mock `QaDashboardService` (boundary port-level), toàn bộ security chain (`SecurityConfig`, `BearerTokenAuthenticationFilter`, `CurrentAppUserResolver`) chạy thật — theo đúng pattern `DataOpsDashboardApiIntegrationTest`.

### 4.3 BE DB IT (đã có từ vòng trước)

`QaDashboardJdbcAdapterIntegrationTest.java` — seed dữ liệu thật vào 4 bảng blackbox qua Postgres testcontainer, gọi `findBlackboxCoverageCounts` thật (không mock), xác nhận counts đúng: `totalAc=3, acWithCaseMapped=2, acWithAllMappedCasesPassed=1, ...`.

### 4.4 FE UT (mới)

| File | Test | Bảo vệ gì |
| --- | --- | --- |
| `src/pages/qa-dashboard/__tests__/QaFilterBar.test.tsx` | state-transition boundary | Đổi `project` phải reset cả `repositoryId` và `ticketId`; đổi `repository` chỉ reset `ticketId`, không đụng `projectId`; `isDisabled` khoá cả 2 select; options rỗng không crash |
| `src/__ tests __/qa-dashboard/QaSummaryCards.test.tsx` (mở rộng) | boundary `blackboxCoveragePercent` = 0 / 0.1 / 89.4 / 100 | Component không crash khi field nhận giá trị thật (thay vì placeholder `0.0` cũ); đồng thời khoá lại hiện trạng: field này **chưa được hiển thị** ở bất kỳ card nào (xem §5) |

### 4.5 E2E (giữ nguyên, không thêm)

`e2e_tests/tests/qa-dashboard/qa-dashboard.spec.ts` đã có 5 scenario (S-E2E-1..5) cho luồng chính: load dashboard, đổi filter, zero-state, read-only, unauthenticated. Không thêm scenario mới cho riêng `blackboxCoveragePercent` vì FE hiện không render field này ở đâu để có thể assert bằng UI — xem gap ở §5. Thêm E2E cho một giá trị không hiển thị sẽ không kiểm chứng được gì, đi ngược nguyên tắc "giá trị cao nhất, số lượng tối thiểu".

## 5. Self-check — thiếu test perspective (đối chiếu `review-checklist.md`)

| Checklist item | Trạng thái trước | Đã xử lý trong lần này |
| --- | --- | --- |
| SEC-3: Endpoint dashboard giữ nguyên auth/session hiện hữu | Chỉ được test qua `QaDashboardControllerTest` (không có security filter thật) | Đã thêm `QaDashboardApiIntegrationTest` — auth chain thật, cả 401/403/200 |
| Permission boundary cho `requireQaAccess`/`requireAnyAccess` | Không có test nào (0% coverage) | Đã thêm 8 BE UT + 4 API IT case |
| DD-4 / AC-7: không đọc bảng AC-Test Coverage | Chỉ xác nhận bằng đọc code, không có test khoá lại | Đã thêm `findBlackboxCoverageCounts_neverReadsAcTestCoverageTables` |
| TEST-2 (per-component zero-denominator) | Đã có (log fix trước) | Không đổi — đã đạt |
| PERF-1/PERF-3 (index đủ, không N+1) | Chỉ review tĩnh, không có test đo | **Còn thiếu** — cần test đo số lần query hoặc `EXPLAIN` trên dữ liệu lớn nếu team cần bằng chứng runtime; hiện tại chấp nhận review tĩnh vì 3 query đều là aggregate 1 lần, không loop theo AC/case |
| Field `blackboxCoveragePercent` không có nơi hiển thị trên FE (`QaSummaryCards.tsx` chỉ có 3/6 card như comment "2x3 grid" mô tả) | Không phát hiện trước đó | **Gap có thật, ngoài phạm vi ticket này** (spec §2: không đổi UI/UX) — đã khoá lại bằng test tại §4.4 để không vô tình "sửa nhầm" trong PR khác mà không ai biết. Đề xuất mở ticket riêng nếu QA Dashboard cần hiển thị `Blackbox Coverage` card |
| Field `error` trong `ErrorResponse` khác với `errorCode` mô tả ở `docs/standards/security.md` | Không phát hiện trước đó | Test API IT đã dùng đúng field thật (`error`); tài liệu chuẩn cần cập nhật riêng, ngoài phạm vi ticket này |
| E2E cho `blackboxCoveragePercent` | Không có | Không bổ sung — không có UI để assert (xem §4.5); coverage AC-8 dồn về BE UT + API IT |

## 6. Cách chạy

```bash
# BE unit tests
cd EDCAP_BE
mvn -Dtest=QaDashboardServiceTest,QaDashboardControllerTest,QaDashboardJdbcAdapterTest,BlackboxCoverageJdbcAdapterTest,BlackboxTestcasesMarkdownParserTest,TestResultsParseServiceTest,ArtifactScannerServiceTest test

# BE integration tests (API IT + DB IT — DB IT tự skip nếu không có Docker)
mvn -Dit.test=QaDashboardApiIntegrationTest,QaDashboardJdbcAdapterIntegrationTest -DfailIfNoTests=false integration-test

# FE unit tests
cd EDCAP_FE
npx vitest run src/pages/qa-dashboard "src/__ tests __/qa-dashboard"

# E2E (auto-start Vite dev server)
npx playwright test e2e_tests/tests/qa-dashboard/qa-dashboard.spec.ts
```

Kết quả thực tế của các lệnh trên: xem [test-results.md](test-results.md).
