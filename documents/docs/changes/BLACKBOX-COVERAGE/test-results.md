# Test Results — BLACKBOX-COVERAGE

- **Ngày chạy:** 2026-09-07
- **Môi trường:** local, Windows, Java 21.0.12, Maven 4.0.0-rc-5, Node/npm (sau `npm install`), Postgres local đang chạy trên `localhost:5432` (dùng cho API IT/security chain — không phải cho DB IT testcontainer)
- **Phạm vi:** test BE UT/API IT liên quan BLACKBOX-COVERAGE, FE UT toàn bộ (regression), E2E cho `qa-dashboard.spec.ts`

## 1. BE Unit Tests

**Command:**
```bash
cd EDCAP_BE
mvn -q -Dtest=QaDashboardServiceTest,BlackboxTestcasesMarkdownParserTest,BlackboxCoverageJdbcAdapterTest,QaDashboardJdbcAdapterTest,QaDashboardControllerTest,TestResultsParseServiceTest,ArtifactScannerServiceTest test
```

**Kết quả:**

| Test class | Tests run | Failures | Errors |
| --- | --- | --- | --- |
| `QaDashboardServiceTest` | 29 | 0 | 0 |
| `BlackboxTestcasesMarkdownParserTest` | 4 | 0 | 0 |
| `BlackboxCoverageJdbcAdapterTest` | 5 | 0 | 0 |
| `QaDashboardJdbcAdapterTest` | 2 | 0 | 0 |
| `QaDashboardControllerTest` | 4 | 0 | 0 |
| `TestResultsParseServiceTest` | 8 | 0 | 0 |
| `ArtifactScannerServiceTest` | 27 | 0 | 0 |

**Tổng:** 79 tests, 0 fail. **PASS.**

`QaDashboardServiceTest` tăng từ 21 → 29 (8 test permission mới); `QaDashboardJdbcAdapterTest` tăng từ 1 → 2 (1 test isolation AC-7 mới).

## 2. BE Integration Tests (API IT + DB IT)

**Command:**
```bash
cd EDCAP_BE
mvn -q -Dit.test=QaDashboardApiIntegrationTest,QaDashboardJdbcAdapterIntegrationTest -DfailIfNoTests=false integration-test
```

**Kết quả:**

| Test class | Tests run | Failures | Skipped | Ghi chú |
| --- | --- | --- | --- | --- |
| `QaDashboardApiIntegrationTest` | 5 | 0 | 0 | Chạy qua Postgres local thật (`localhost:5432`), Flyway migrate lên V516 thành công |
| `QaDashboardJdbcAdapterIntegrationTest` | 1 | 0 | 1 (skip) | Skip vì **không có Docker** trong môi trường này (`@Testcontainers(disabledWithoutDocker = true)`) — hành vi đúng theo thiết kế, không phải lỗi |

**Tổng:** 6 tests, 0 fail, 1 skip có lý do rõ. **PASS** (với ghi chú DB IT chưa chạy thật lần này).

### Failure ban đầu và cách xử lý

Lần chạy đầu tiên, `QaDashboardApiIntegrationTest.summary_authenticatedWithoutQaRoleOnProject_returns403` fail:

```
java.lang.AssertionError: No value at JSON path "$.errorCode"
Caused by: com.jayway.jsonpath.PathNotFoundException: No results for path: $['errorCode']
```

- **Nguyên nhân:** test viết sai tên field JSON kỳ vọng. `docs/standards/security.md` mô tả `ErrorResponse(timestamp, status, errorCode, message, traceId)`, nhưng `ErrorResponse.java` thật sự có field tên `error`, không phải `errorCode`. Response thật: `{"status":403,"error":"FORBIDDEN","message":"Component.Permission.Denied",...}`.
- **Cách xử lý:** sửa assertion trong test từ `jsonPath("$.errorCode", ...)` sang `jsonPath("$.error", ...)` để khớp hành vi thật của `GlobalExceptionHandler`. Không sửa `ErrorResponse.java` vì đó là hành vi toàn hệ thống có sẵn, ngoài phạm vi ticket BLACKBOX-COVERAGE.
- **Việc còn lại (ngoài phạm vi ticket):** `docs/standards/security.md` nên được cập nhật lại tên field cho khớp code thật — đề xuất một task tài liệu riêng.

## 3. FE Unit Tests

**Command:**
```bash
cd EDCAP_FE
npm install   # node_modules chưa có sẵn trong môi trường này
npx vitest run
```

**Kết quả:** 55 test file, 362 tests, **0 fail**. Bao gồm 2 file mới/mở rộng cho ticket này:

- `src/pages/qa-dashboard/__tests__/QaFilterBar.test.tsx` — 4/4 pass.
- `src/__ tests __/qa-dashboard/QaSummaryCards.test.tsx` — 11/11 pass (6 test cũ + 5 test mới).

**PASS.** Không có regression ở các module khác.

## 4. E2E (Playwright)

**Command:**
```bash
cd EDCAP_FE
npx playwright install chromium   # browser chưa cài sẵn trong môi trường này
npx playwright test e2e_tests/tests/qa-dashboard/qa-dashboard.spec.ts --reporter=list
```

**Kết quả lần chạy đầu:** 3/5 pass, 2 fail (`S-E2E-1`, `S-E2E-2`) với lỗi timeout chờ tiêu đề/table xuất hiện.

- **Nguyên nhân:** cold-start — lần chạy đầu tiên sau khi vừa cài Playwright browser, Vite dev server phải compile toàn bộ app từ đầu (cache rỗng), vượt quá timeout cố định 10s của 2 test đầu tiên. Đây là vấn đề môi trường/cache, không phải regression từ thay đổi của ticket này (không đụng tới `QADashboardPage.tsx` hay `qa-dashboard.spec.ts`).
- **Cách xử lý:** chạy lại (dev server đã "ấm" từ lần trước). Kết quả: **5/5 pass**, 12.7s.
- **Việc còn lại:** không cần sửa gì trong source; nếu CI runner luôn cold-start, cân nhắc tăng timeout bước đầu hoặc thêm bước "warm up" dev server trước khi chạy suite thật — đề xuất, không bắt buộc cho ticket này.

**PASS** (5/5 sau khi loại trừ cold-start).

## 5. Tổng kết

| Loại test | Số lượng | Pass | Fail | Skip |
| --- | --- | --- | --- | --- |
| BE UT | 79 | 79 | 0 | 0 |
| BE API IT | 5 | 5 | 0 | 0 |
| BE DB IT | 1 | 0 | 0 | 1 (no Docker) |
| FE UT | 362 | 362 | 0 | 0 |
| E2E | 5 | 5 | 0 | 0 |

**Verdict: PASS**, với 1 gap được ghi nhận: DB IT (`QaDashboardJdbcAdapterIntegrationTest`) chưa chạy thật trong môi trường này vì thiếu Docker — cần chạy lại trên máy/CI có Docker trước khi merge để có bằng chứng thật cho AC-3/AC-4/AC-7 ở tầng SQL thật (khác với BE UT vốn chỉ assert cấu trúc câu SQL).

## 6. Black-box case execution (2026-09-07, lần chạy thứ 2)

**Môi trường:** Docker Desktop có sẵn (`docker ps` thấy container `sdd-postgres`, Postgres 16, đã Flyway migrate lên `V516`). Testcontainers vẫn **không** detect được Docker daemon qua named pipe trong shell này (`NpipeSocketClientProviderStrategy` lỗi `Status 400`) — `QaDashboardJdbcAdapterIntegrationTest` vẫn skip (1/1), không đổi so với lần chạy trước. Đây là giới hạn môi trường, không phải regression.

Vì Testcontainers không dùng được, 11/16 black-box case (BBC-01..08, 14, 15, 16) được chạy trực tiếp trên Postgres dev thật (`localhost:5432`, cùng DB mà API IT dùng) bằng một test class mới, tái sử dụng nguyên vẹn các adapter/service thật của production — không mock, không schema giả lập.

### 6.1 Bug thật tìm thấy và đã sửa

Lần chạy đầu tiên của `BlackboxCoverageBlackboxCaseIntegrationTest` (mọi test case) fail với:

```
org.postgresql.util.PSQLException: ERROR: column reference "priority" is ambiguous
```

- **Nguyên nhân:** `QaDashboardJdbcAdapter.PRIORITY_WEIGHT_SQL` viết `CASE priority WHEN 'P0' ...` không qualify tên bảng. Câu SQL `JOIN`s `tbl_fact_blackbox_case bc` với `tbl_dim_ticket t`, và **cả hai bảng đều có cột `priority`** — nên `priority` không xác định được là của bảng nào. Đây là bug thật trong code sản phẩm, không phải lỗi test.
- **Vì sao chưa ai phát hiện trước đây:** `QaDashboardJdbcAdapterIntegrationTest` (DB IT có sẵn) tự tạo schema `tbl_dim_ticket` rút gọn không có cột `priority`, nên ambiguity không bao giờ xảy ra ở đó — và bản thân test đó cũng chưa từng chạy thật (luôn skip vì thiếu Docker). `QaDashboardJdbcAdapterTest` (unit test) mock `NamedParameterJdbcTemplate`, không thực thi SQL thật. `QaDashboardApiIntegrationTest` mock hẳn `QaDashboardService`. Do đó câu SQL này **chưa từng được thực thi với schema thật** cho tới lần chạy này.
- **Tác động nếu chưa sửa:** `findBlackboxCoverageCounts` — và do đó toàn bộ endpoint `/api/v1/qa/dashboard/summary` — sẽ trả lỗi 500 cho **mọi** request, vì câu SQL này luôn chạy bất kể ticket có dữ liệu blackbox hay không.
- **Cách xử lý:** sửa `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/QaDashboardJdbcAdapter.java` — đổi `CASE priority ...` thành `CASE bc.priority ...`. Không có test nào (đã kiểm tra bằng grep) hardcode chuỗi SQL cũ nên không có test nào bị vỡ theo.
- **Verify không hồi quy:** chạy lại `QaDashboardServiceTest` (29/29), `QaDashboardJdbcAdapterTest` (2/2), `QaDashboardControllerTest` (4/4) — tất cả vẫn PASS sau khi sửa.

### 6.2 Test mới: `BlackboxCoverageBlackboxCaseIntegrationTest`

`EDCAP_BE/src/test/IntegrationTest/java/com/sdd/platform/application/usecase/qadashboard/BlackboxCoverageBlackboxCaseIntegrationTest.java` — 13 test method, chạy qua:

```bash
cd EDCAP_BE
mvn -Dit.test=BlackboxCoverageBlackboxCaseIntegrationTest -DfailIfNoTests=false integration-test
```

**Kết quả:** `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0`. **PASS.**

Cách hoạt động: nối trực tiếp tới Postgres dev thật, dựng `QaDashboardJdbcAdapter` + `BlackboxCoverageJdbcAdapter` + `QaDashboardService` thật (không mock), tạo 1 project test riêng (`project_alias = 'BBC-COVERAGE-BLACKBOX-CASE-TEST-DO-NOT-USE'`) và các ticket test riêng bên trong, gọi `service.getSummary(...)` thật, rồi dọn sạch toàn bộ dữ liệu vừa tạo ở `@AfterEach`/`@AfterAll`. Đã xác nhận sau khi chạy: `tbl_dim_project`/`tbl_dim_ticket`/`tbl_fact_blackbox_case` không còn row nào của test này — không để lại rác trên DB dev dùng chung.

**Phát hiện quan trọng khi build dataset cho BBC-01:** tổ hợp `8 AC mapped / 9 AC all-passed` trong ví dụ minh họa ở `spec-pack.md` mục 8 **không thể tái tạo bằng dữ liệu quan hệ thật** — theo đúng SQL của `findBlackboxCoverageCounts`, tử số "all mapped cases passed" luôn là tập con của tử số "mapped" (một AC chỉ được tính all-passed nếu nó đã được tính mapped). Ví dụ minh họa đó chỉ hợp lệ như input tổng hợp cho unit test thuần công thức (`QaDashboardServiceTest`, vẫn PASS). Test mới dùng bộ dữ liệu quan hệ hợp lệ khác (`8 mapped / 7 all-passed`, planned=24/passed=21) và đã cập nhật lại `blackbox-testcases.md`/`test-data.md` cho khớp — xem ghi chú ở hai file đó.

Ánh xạ case → test method → kết quả (giá trị `blackboxCoveragePercent` thật):

| BBC | Test method | Kết quả thật |
| --- | --- | --- |
| BBC-01 | `bbc01_fullDataset_computesRealOverallCoverage` | `81.6` |
| BBC-02 | `bbc02_noBlackboxData_returnsZeroNotNullOrError` | `0.0` |
| BBC-03 | `bbc03_designOnly_noExecutionYet_dragsOverallDownByExecutionWeight` | `25.5` |
| BBC-04 | `bbc04a_zeroAcInScope_returnsValidNumberNotNaN` / `bbc04b_zeroObservationPoints_returnsValidNumberNotNaN` / `bbc04c_zeroCasePlanned_returnsZeroNotNaN` | `34.2` / `56.6` / `0.0` |
| BBC-05 | `bbc05_skipStatus_notCountedAsPass_sameResultAsFail` + `bbc05And06_skipAndNotRun_scoreLowerThanAllCasesPassing` | `81.6` (bằng FAIL), `< 87.1` (all-pass) |
| BBC-06 | `bbc06_notRunStatus_notCountedAsPass_sameResultAsFail` | `81.6` (bằng FAIL/SKIP) |
| BBC-07 | `bbc07_priorityWeight_p0FailureHurtsMoreThanP2Failure` | A(P0 fail)=`45.3` < B(P2 fail)=`52.3` |
| BBC-08 | `bbc08_directObservationCoverageCountsMoreThanRelatedOnly` | DIRECT=`100.0` > RELATED=`75.0` |
| BBC-14 | `bbc14_roundsToOneDecimalPlace_forRepeatingFraction` | `7.5` (từ phân số tuần hoàn 5/7) |
| BBC-15/16 | `bbc15And16_multipleTicketsInSameProject_neverCrossContaminate` | Không lẫn dữ liệu giữa 2 ticket, gọi xen kẽ 2 chiều |

### 6.3 PASS toàn bộ suite sau khi sửa bug

Chạy lại `mvn -Dtest=QaDashboardServiceTest,BlackboxTestcasesMarkdownParserTest,BlackboxCoverageJdbcAdapterTest,QaDashboardJdbcAdapterTest,QaDashboardControllerTest,TestResultsParseServiceTest,ArtifactScannerServiceTest test`: **79/79 PASS**, không đổi so với mục 1. Chạy lại `mvn -Dit.test=QaDashboardApiIntegrationTest,QaDashboardJdbcAdapterIntegrationTest integration-test`: `QaDashboardApiIntegrationTest` **5/5 PASS**; `QaDashboardJdbcAdapterIntegrationTest` vẫn skip (Docker/Testcontainers, không phải regression).

### 6.4 Gap còn lại: BBC-09..13 (auth/permission/contract qua HTTP + role thật)

5 case còn lại **chưa được thực thi qua HTTP thật với role thật từ DB** trong lần này:

- Có bằng chứng gián tiếp: `QaDashboardApiIntegrationTest` (real Spring Security filter chain, real `GlobalExceptionHandler`, nhưng `QaDashboardService` bị mock) xác nhận đúng HTTP status (401/403/200) và đúng contract JSON; `QaDashboardServiceTest` (29 test, real `requireQaAccess`/`requireAnyAccess`, nhưng `QaDashboardRepositoryPort` bị mock) xác nhận đúng logic phân quyền.
- Chưa có bằng chứng nào kết hợp **cả ba** ở cùng lúc: HTTP thật + `QaDashboardService` thật + role thật đọc từ `tbl_auth_member_project_role`/`tbl_dim_role` qua `DashboardProjectAccessJdbcAdapter` thật.
- **Đã xác nhận khả thi về mặt hạ tầng nhưng chưa triển khai:** hệ thống có endpoint `POST /api/v1/auth/login` thật (bcrypt + JWT tự ký, xem `AuthTokenService`), có sẵn user demo (`tbl_auth_user_account`, migration `V81`), và không dùng OAuth2/IdP ngoài — nên có thể tạo user QA/DEV test + seed `tbl_auth_member_project_role`, login lấy token thật, rồi gọi `curl` thật vào app đang chạy. Việc này cần khởi động Spring Boot app thật (`mvn spring-boot:run`) và không nằm trong phạm vi lần chạy này.
- **Khuyến nghị:** mở một hạng mục riêng (hoặc làm tiếp trong lần review kế) để viết một integration test thật sự end-to-end (HTTP + role DB thật) cho BBC-09..13, thay vì chỉ dựa vào bằng chứng "từng phần thật" như hiện tại.
