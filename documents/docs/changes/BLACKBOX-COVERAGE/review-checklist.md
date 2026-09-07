# Review Checklist — BLACKBOX-COVERAGE

**Ticket ID**: BLACKBOX-COVERAGE
**Create date**: 2026-09-04
**Author**: AI-assisted
**Update date**: 2026-09-07

> Checklist dùng để review implementation của ticket BLACKBOX-COVERAGE (xem `spec-pack.md`, `impl-plan.md`).
> Cột `Result` được điền ngày 2026-09-07 dựa trên chạy test thật (BE UT, API IT, và
> `BlackboxCoverageBlackboxCaseIntegrationTest` mới chạy trên Postgres dev thật) — xem
> `test-results.md` mục 6 và `self-review.md` để có bằng chứng chi tiết.

---

## 1. Specification / AC Matching

| AC ID | Review Point | Severity | Result |
|---|---|---|---|
| AC-BLACKBOX-COVERAGE-1 | Scanner parse `blackbox-testcases.md` và persist case, AC-mapping, observation point | Blocker | PASS — `ArtifactScannerServiceTest`, `BlackboxTestcasesMarkdownParserTest`, `BlackboxCoverageJdbcAdapterTest` (unit-level) |
| AC-BLACKBOX-COVERAGE-2 | `TestResultsParseService` map đúng `PASS/FAIL/SKIP/NOT_RUN` vào blackbox case | Blocker | PASS — `TestResultsParseServiceTest` + xác nhận lại bằng real DB (`bbc05`/`bbc06`) |
| AC-BLACKBOX-COVERAGE-3 | `findBlackboxCoverageCounts` trả đủ counts cho AC/observation/priority | Blocker | PASS (sau fix) — xem bug ở mục 8/`self-review.md` mục 7; xác nhận bằng real DB (`bbc01`, `bbc04a/b/c`, `bbc15And16`) |
| AC-BLACKBOX-COVERAGE-4 | Query áp đúng priority weight (`P0=5, P1=3, P2=1`) và chỉ tính `DIRECT` observation | Major | PASS — real DB (`bbc07`, `bbc08`) |
| AC-BLACKBOX-COVERAGE-5 | `computeBlackboxOverallCoverage` khớp ví dụ spec (`89.4`) | Blocker | PASS (unit, số liệu tổng hợp) — **Ghi chú:** tổ hợp ví dụ spec (`8 mapped/9 all-passed`) không tái tạo được bằng dữ liệu quan hệ thật; real DB dùng bộ số khác (`81.6`), xem `test-data.md` mục 1.1 |
| AC-BLACKBOX-COVERAGE-6 | Mẫu số = 0 trả `0.0`, không redistribute weight, làm tròn 1 chữ số | Blocker | PASS — real DB (`bbc02`, `bbc04a/b/c`, `bbc14`) |
| AC-BLACKBOX-COVERAGE-7 | Blackbox Coverage tách biệt hoàn toàn khỏi AC-Test Coverage (bảng, formula) | Blocker | PASS — `findBlackboxCoverageCounts_neverReadsAcTestCoverageTables`; BBC-13 (so sánh qua HTTP) chưa chạy, xem gap |
| AC-BLACKBOX-COVERAGE-8 | DTO `blackboxCoveragePercent` giữ nguyên tên field, kiểu dữ liệu, vị trí | Major | PASS (1 phần) — `QaDashboardControllerTest`, `QaDashboardApiIntegrationTest` xác nhận contract + HTTP status; **chưa có test kết hợp HTTP thật + role thật từ DB** (BBC-09..13), xem gap |

---

## 2. Design / Dependencies

| # | Check | Severity | AC ref | Result |
|---|---|---|---|---|
| DD-1 | Migration `V515` chỉ thêm bảng mới, không sửa bảng cũ | Blocker | AC-7 | PASS — xác nhận qua `\d tbl_fact_blackbox_*` trên DB thật, không có ALTER lên bảng cũ |
| DD-2 | Port/adapter tuân hexagonal layering (`application` không phụ thuộc JDBC chi tiết) | Major | AC-1,7 | PASS — review code, `BlackboxCoveragePersistencePort`/`QaDashboardRepositoryPort` là interface thuần |
| DD-3 | Delete-then-reinsert cho design-side, update-only cho execution-side | Major | AC-1,2,6 | PASS — review code `BlackboxCoverageJdbcAdapter` (`replaceBlackbox*` vs `upsertBlackboxCaseResults`) |
| DD-4 | Không đọc `tbl_fact_test_case` / `tbl_fact_ac_test_coverage` khi tính Blackbox Coverage | Blocker | AC-7 | PASS — khóa bằng test `findBlackboxCoverageCounts_neverReadsAcTestCoverageTables` |
| DD-5 | `QaFilter` được dùng thống nhất cho truy vấn dashboard | Minor | AC-3 | PASS — `getSummary` build 1 `QaFilter` dùng chung cho cả 4 repository call |

---

## 3. Security

| # | Check | Severity | AC ref | Result |
|---|---|---|---|---|
| SEC-1 | Tất cả SQL đều parameterized, không nối chuỗi trực tiếp | Blocker | AC-3,4 | PASS — đã soát lại toàn bộ `QaDashboardJdbcAdapter`/`BlackboxCoverageJdbcAdapter` khi sửa bug ambiguous-column; không có string concatenation từ input người dùng |
| SEC-2 | Không log dữ liệu nhạy cảm khi parse/persist blackbox data | Major | — | PASS — log warning chỉ in `ticketId`/`caseKey`/`acKey`/`observationKey`, không có dữ liệu người dùng nhạy cảm |
| SEC-3 | Endpoint dashboard hiện có giữ nguyên auth/session hiện hữu | Major | AC-8 | PASS (1 phần) — `QaDashboardApiIntegrationTest` xác nhận qua real security filter chain; chưa có bằng chứng role thật từ DB, xem gap ở mục 1 (AC-8) |

---

## 4. Performance

| # | Check | Severity | AC ref | Result |
|---|---|---|---|---|
| PERF-1 | Index đủ cho truy vấn theo ticket trên 4 bảng blackbox mới | Major | AC-3,4 | PASS (review tĩnh) — `V515` có index trên `ticket_id` cho cả 4 bảng; chưa đo runtime trên dữ liệu lớn |
| PERF-2 | Delete-then-reinsert không gây khóa dài trên bảng lớn | Minor | AC-1 | PASS (review tĩnh) — scope theo `ticket_id`, không phải toàn bảng |
| PERF-3 | Aggregation dashboard không N+1 query theo từng AC/case | Major | AC-3,4,5 | PASS — `findBlackboxCoverageCounts` dùng 3 câu aggregate (AC/observation/priority), không loop theo case; xác nhận qua đọc code + real DB chạy nhanh (13 test/22.68s bao gồm cả seed+cleanup) |

---

## 5. Compatibility

| # | Check | Severity | AC ref | Result |
|---|---|---|---|---|
| COMPAT-1 | Field `blackboxCoveragePercent`: tên, kiểu dữ liệu, vị trí không đổi | Blocker | AC-8 | PASS — `QaDashboardControllerTest` + `QaDashboardApiIntegrationTest` |
| COMPAT-2 | Không thêm field API mới để show riêng Design/Execution/Priority | Major | AC-8 | PASS — review `QaDashboardDtos.QaDashboardSummaryDto`, chỉ có `blackboxCoveragePercent` |
| COMPAT-3 | Không đổi hành vi/API của AC-Test Coverage hiện hữu | Blocker | AC-7 | PASS (1 phần) — khóa ở tầng SQL (DD-4); BBC-13 (so sánh giá trị qua HTTP trước/sau) chưa chạy |
| COMPAT-4 | Zero-denominator vẫn trả `0.0`, không trả `null`/`NaN` (giữ hành vi cũ) | Blocker | AC-6 | PASS — real DB (`bbc02`, `bbc04a/b/c`) |

---

## 6. Logs / Audit

| # | Check | Severity | AC ref | Result |
|---|---|---|---|---|
| LOG-1 | Parser warning ghi log, không tạo channel log riêng cho metric này | Minor | — | PASS — dùng `LoggerFactory.getLogger` chuẩn của class, không có channel riêng |
| LOG-2 | Parse failure có evidence event để trace lại | Major | AC-1,2 | PASS (review code) — chưa quan sát trực tiếp evidence event trong lần chạy này |
| LOG-3 | Case key/AC key/observation key rỗng hoặc null bị bỏ qua và có log cảnh báo | Major | AC-1 | PASS — quan sát trực tiếp log WARN thật khi chạy `ArtifactScannerServiceTest` (`Skipping blackbox case-observation link ... caseKey=''`) |

---

## 7. Error Handling

| # | Check | Severity | AC ref | Result |
|---|---|---|---|---|
| ERR-1 | `test-results.md` có key không khớp blackbox case → giữ nguyên, không ghi đè | Blocker | AC-2 | PASS — review code `upsertBlackboxCaseResults` (`UPDATE ... WHERE ticket_id AND case_key`, không match thì không ảnh hưởng row khác) |
| ERR-2 | Xóa `blackbox-testcases.md` → xóa evidence tương ứng theo ticket | Major | AC-1 | PASS (review code) — `deleteBlackboxEvidenceByTicketId`, đã dùng chính hàm này để cleanup real DB test và xác nhận xóa sạch |
| ERR-3 | Priority/status không hợp lệ được normalize uppercase trước khi ghi | Minor | AC-2,4 | PASS — review code (`toUpperCase(Locale.ROOT)` ở cả hai adapter) |
| ERR-4 | Mẫu số = 0 ghi rõ lý do (no AC / no observation / no case) | Minor | AC-6 | PASS — quan sát trực tiếp log thật khi chạy real DB test (`"Blackbox Priority coverage forced to 0% (no case planned)"`, v.v.) |

---

## 8. Testing

| # | Check | Severity | AC ref | Result |
|---|---|---|---|---|
| TEST-1 | `QaDashboardServiceTest` cover ví dụ spec (`10,8,9,5,5,5,22,18 → 89.4`) | Blocker | AC-5 | PASS (unit); xem ghi chú AC-5 về việc ví dụ này không tái tạo được bằng dữ liệu quan hệ thật |
| TEST-2 | Test cover zero-denominator cho từng thành phần (AC, observation, priority) | Blocker | AC-6 | PASS — cả unit lẫn real DB (`bbc04a/b/c`) |
| TEST-3 | `QaDashboardControllerTest` xác nhận DTO vẫn có `blackboxCoveragePercent` | Major | AC-8 | PASS |
| TEST-4 | Có test cho case `SKIP` và `NOT_RUN` không được tính là PASS | Major | AC-2,6 | PASS — cả unit lẫn real DB (`bbc05`, `bbc06`) |
| TEST-5 | Có test cho mapping `SUCCESS/FAILED/SKIPPED/khác` → `PASS/FAIL/SKIP/NOT_RUN` | Major | AC-2 | PASS — `TestResultsParseServiceTest` |
| TEST-6 (mới) | Có bằng chứng real DB (không mock) cho counts/formula, không chỉ mock-based | Blocker | AC-3,4,5,6 | PASS — `BlackboxCoverageBlackboxCaseIntegrationTest`, 13/13, phát hiện + sửa 1 bug thật trong lúc chạy |
| TEST-7 (mới) | Có bằng chứng HTTP thật + role thật từ DB cho permission gate | Major | AC-8 | **CHƯA ĐẠT** — chỉ có bằng chứng từng phần (HTTP thật/service mock hoặc service thật/repo mock), xem `test-results.md` mục 6.4 |

---

## 9. Operations

| # | Check | Severity | AC ref | Result |
|---|---|---|---|---|
| OPS-1 | Migration chạy được trên local/dev, không cần rollback thủ công phức tạp | Major | AC-1 | PASS — xác nhận `flyway_schema_history` có version `515` thành công trên DB dev thật |
| OPS-2 | Rollback plan: revert code, không đụng bảng AC-Test Coverage cũ | Major | AC-7 | PASS (review kế hoạch) — xem `impl-plan.md` §8 |
| OPS-3 | Không cần thao tác vận hành thủ công (manual UI/FE recompute) | Minor | — | PASS |

---

## 10. AC Mapping Table (tổng hợp)

| AC ID | Checks xác nhận |
|---|---|
| AC-BLACKBOX-COVERAGE-1 | DD-1, DD-3, DD-4, LOG-2, LOG-3, ERR-2, OPS-1 |
| AC-BLACKBOX-COVERAGE-2 | DD-3, LOG-2, ERR-1, ERR-3, TEST-4, TEST-5 |
| AC-BLACKBOX-COVERAGE-3 | DD-5, SEC-1, PERF-1, PERF-3, TEST-6 |
| AC-BLACKBOX-COVERAGE-4 | SEC-1, PERF-1, PERF-3, ERR-3, TEST-6 |
| AC-BLACKBOX-COVERAGE-5 | PERF-3, TEST-1, TEST-6 |
| AC-BLACKBOX-COVERAGE-6 | COMPAT-4, ERR-4, TEST-2, TEST-4, TEST-6 |
| AC-BLACKBOX-COVERAGE-7 | DD-1, DD-4, COMPAT-3, OPS-2 |
| AC-BLACKBOX-COVERAGE-8 | SEC-3, COMPAT-1, COMPAT-2, TEST-3, TEST-7 |

---

## 11. Bug tìm thấy trong lần review này (2026-09-07)

| # | Mô tả | Severity | Trạng thái |
|---|---|---|---|
| 1 | `QaDashboardJdbcAdapter.findBlackboxCoverageCounts` — cột `priority` ambiguous giữa `tbl_fact_blackbox_case` và `tbl_dim_ticket` khi chạy trên schema thật → mọi request tới `/api/v1/qa/dashboard/summary` sẽ lỗi 500 | Blocker | **Fixed** — đổi `CASE priority` thành `CASE bc.priority`; verify lại 79 BE UT + 5 API IT + 13 black-box IT đều PASS sau fix |

---

## Severity Definition

| severity | meaning | required action |
|---|---|---|
| Blocker | Cannot be released | Must fix |
| Major | High probability of becoming a bug | Fix or accepted risk |
| Minor | Minor improvement | Optional |
| Question | Spec confirmation required | Open Issue |
| False Positive | Incorrect Report | Record Reason for Rejection |
| Accepted Risk | Accepted Risk | Record Impact/Owner/Deadline
