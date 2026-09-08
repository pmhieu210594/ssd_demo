# Self Review — BLACKBOX-COVERAGE

**Ticket ID**: BLACKBOX-COVERAGE
**Create date**: 2026-09-04
**Author**: AI-assisted
**Update date**: 2026-09-07

> AI tự điền checklist này SAU KHI implementation xong. Mỗi mục là checkbox, tick khi đã tự xác nhận.

---

## 1. Implementation Summary

- [x] Đã tóm tắt ngắn gọn thay đổi chính (schema, port/adapter, scanner, dashboard formula)

_Tóm tắt:_ Thêm schema additive `V516` (4 bảng blackbox), port/adapter persistence riêng
(`BlackboxCoveragePersistencePort`/`BlackboxCoverageJdbcAdapter`), scanner parse
`blackbox-testcases.md`, `TestResultsParseService` map trạng thái execution, và
`QaDashboardService.computeBlackboxOverallCoverage` tính field `blackboxCoveragePercent` theo 3
công thức Design/Execution/Overall của `spec-pack.md`. Ngày 2026-09-07 đã chạy thật 11/16
black-box case (`BlackboxCoverageBlackboxCaseIntegrationTest`, real DB) và phát hiện + sửa 1 bug
thật: cột `priority` ambiguous trong `QaDashboardJdbcAdapter.findBlackboxCoverageCounts` (xem mục 7).

---

## 2. Specification / AC Matching

| AC ID | Đạt? | Evidence |
|---|---|---|
| AC-BLACKBOX-COVERAGE-1 | [x] | `ArtifactScannerServiceTest` (27 test), `BlackboxTestcasesMarkdownParserTest` (4 test), `BlackboxCoverageJdbcAdapterTest` (5 test) — tất cả PASS. Chưa có bằng chứng scan thật từ file `.md` thật trên đĩa qua toàn bộ pipeline (chỉ unit-level). |
| AC-BLACKBOX-COVERAGE-2 | [x] | `TestResultsParseServiceTest` (8 test) PASS; `bbc05`/`bbc06` (real DB) xác nhận `SKIP`/`NOT_RUN` không tính PASS ở tầng formula. |
| AC-BLACKBOX-COVERAGE-3 | [x] | `QaDashboardJdbcAdapterTest` (2 test, SQL shape) PASS; **real DB**: `bbc01`, `bbc04a/b/c`, `bbc15And16` (`BlackboxCoverageBlackboxCaseIntegrationTest`) xác nhận counts đúng trên Postgres thật. `QaDashboardJdbcAdapterIntegrationTest` (Testcontainers) vẫn skip — môi trường không detect được Docker qua named pipe. |
| AC-BLACKBOX-COVERAGE-4 | [x] | `bbc07` (priority weight P0 vs P2) + `bbc08` (chỉ DIRECT được tính) — cả hai chạy trên real DB, PASS. |
| AC-BLACKBOX-COVERAGE-5 | [x] | `QaDashboardServiceTest.getSummary_blackboxCoverage_matchesSpecPackWorkedExample` (unit, số liệu tổng hợp theo spec) PASS. **Lưu ý:** tổ hợp `8 mapped/9 all-passed` trong ví dụ spec không tái tạo được bằng dữ liệu quan hệ thật (đã xác nhận khi build `bbc01`) — hợp lệ cho unit test công thức thuần, không phải bằng chứng end-to-end. `bbc01` dùng bộ số liệu quan hệ hợp lệ khác, PASS trên real DB. |
| AC-BLACKBOX-COVERAGE-6 | [x] | `QaDashboardServiceTest` (test zero-denominator từng thành phần) PASS; `bbc02`, `bbc04a/b/c` (real DB) xác nhận không `NaN`/`null`; `bbc14` (real DB) xác nhận rounding 1 chữ số qua phân số tuần hoàn. |
| AC-BLACKBOX-COVERAGE-7 | [x] | `QaDashboardJdbcAdapterTest.findBlackboxCoverageCounts_neverReadsAcTestCoverageTables` PASS (khóa SQL không đọc bảng AC-Test Coverage). Chưa chạy black-box case BBC-13 (so sánh giá trị field AC-Test Coverage trước/sau qua HTTP thật) — xem mục 8. |
| AC-BLACKBOX-COVERAGE-8 | [~] | `QaDashboardControllerTest` (4 test) + `QaDashboardApiIntegrationTest` (5 test, real security chain, `QaDashboardService` mock) đều PASS — xác nhận đúng contract + đúng HTTP status (401/403/200). **Chưa có bằng chứng kết hợp cả HTTP thật + role thật từ DB** (`requireQaAccess` chỉ được test bằng mock repository ở `QaDashboardServiceTest`, hoặc bằng service mock ở API IT) — xem mục 8. |

---

## 3. List of Changed Files

| file | summary | reason |
|---|---|---|
| `EDCAP_BE/src/main/resources/db/migration/V516__blackbox_coverage_schema.sql` | Thêm 4 bảng blackbox additive | Lưu design/execution evidence tách biệt AC-Test Coverage |
| `EDCAP_BE/.../port/out/persistence/BlackboxCoveragePersistencePort.java` | Port ghi dữ liệu blackbox | Tách application khỏi chi tiết JDBC |
| `EDCAP_BE/.../infrastructure/persistence/adapter/BlackboxCoverageJdbcAdapter.java` | Adapter ghi dữ liệu blackbox | Delete-then-reinsert (design) / update-only (execution) |
| `EDCAP_BE/.../infrastructure/persistence/adapter/QaDashboardJdbcAdapter.java` | Đọc counts AC/observation/priority; **sửa bug 2026-09-07**: `CASE priority` → `CASE bc.priority` (ambiguous column) | Cung cấp input thô cho formula; bug khiến mọi request tới `/summary` lỗi 500 |
| `EDCAP_BE/.../usecase/qadashboard/QaDashboardService.java` | Tính `blackboxCoveragePercent` theo formula | Áp dụng đúng trọng số/rounding/zero-denominator theo spec |
| `EDCAP_BE/.../usecase/scanner/ArtifactScannerService.java` | Parse + persist `blackbox-testcases.md` | Nguồn design-side |
| `EDCAP_BE/.../usecase/docparse/TestResultsParseService.java` | Map trạng thái execution | Nguồn execution-side |
| `EDCAP_BE/src/test/IntegrationTest/.../BlackboxCoverageBlackboxCaseIntegrationTest.java` (mới, 2026-09-07) | 13 test chạy thật trên Postgres dev, real adapters/service | Đóng gap "black-box case chưa từng chạy" từ báo cáo trước |
| `docs/changes/BLACKBOX-COVERAGE/{blackbox-testcases.md, test-data.md, test-results.md, report.md}` | Cập nhật số liệu D-FULL theo dữ liệu quan hệ thật + ghi nhận kết quả thực thi | Số liệu gốc (8 mapped/9 all-passed) không tái tạo được bằng dữ liệu quan hệ thật |

---

## 4. Command Run and Results

| command | result | note |
|---|---|---|
| `mvn -Dtest=QaDashboardServiceTest,BlackboxTestcasesMarkdownParserTest,BlackboxCoverageJdbcAdapterTest,QaDashboardJdbcAdapterTest,QaDashboardControllerTest,TestResultsParseServiceTest,ArtifactScannerServiceTest test` | 79/79 PASS | Chạy 2 lần (trước và sau bug fix), cùng kết quả |
| `mvn -Dit.test=QaDashboardApiIntegrationTest,QaDashboardJdbcAdapterIntegrationTest integration-test` | API IT 5/5 PASS; DB IT skip (1) | Docker daemon có sẵn nhưng Testcontainers không detect được qua named pipe trong shell này |
| `mvn -Dit.test=BlackboxCoverageBlackboxCaseIntegrationTest integration-test` | 13/13 PASS (real DB) | Lần đầu fail 13/13 do bug ambiguous column; sau khi sửa, PASS toàn bộ |
| `npx vitest run` (FE) | 362/362 PASS | Không chạy lại trong lần 2026-09-07 (không đổi code FE); giữ nguyên bằng chứng từ lần trước |
| `npx playwright test qa-dashboard.spec.ts` (E2E) | 5/5 PASS | Không chạy lại trong lần 2026-09-07 (không đổi code FE); giữ nguyên bằng chứng từ lần trước |

- [x] Lint/test command đã chạy thật, không phải giả định
- [x] Đã đính kèm output thật (pass/fail count) ở cột result

---

## 5. Self-Check using Review Checklist

Tick từng section trong `review-checklist.md` sau khi tự soát.

| checklist area | result | note |
|---|---|---|
| 1. Specification / AC Matching | PASS (1 phần) | AC-7/AC-8 có bằng chứng gián tiếp, chưa có bằng chứng HTTP+role thật kết hợp — xem mục 2 |
| 2. Design / Dependencies | PASS | Delete-then-reinsert/update-only đúng theo impl-plan; hexagonal layering giữ nguyên |
| 3. Security | PASS | SQL parameterized (đã soát lại khi sửa bug); không log dữ liệu nhạy cảm |
| 4. Performance | PASS (review tĩnh) | 3 query đều aggregate 1 lần, không N+1; chưa đo runtime trên dữ liệu lớn |
| 5. Compatibility | PASS | Contract field không đổi; AC-Test Coverage tách biệt (khóa bằng test) |
| 6. Logs / Audit | PASS | Parser warning + log "forced to 0%" quan sát được trong output test thật |
| 7. Error Handling | PASS | Case key rỗng bị bỏ qua (thấy log WARN thật khi chạy suite); mẫu số=0 không NaN (real DB) |
| 8. Testing | PASS (1 phần) | 11/16 black-box case chạy thật; 5/16 (BBC-09..13) còn ở mức "bằng chứng từng phần" |
| 9. Operations | PASS | Migration additive, không cần thao tác thủ công |

---

## 6. Test Plan Corresponding Status

- [x] Ví dụ spec (`10 AC, 8 mapped, 9 pass, 5 obs, 5 direct-pass, priority 22/18`) cho ra `89.4` — **đúng ở mức unit test công thức thuần** (`QaDashboardServiceTest`); đã xác nhận tổ hợp này không tái tạo được bằng dữ liệu quan hệ thật, nên bằng chứng real-DB (`bbc01`) dùng bộ số khác (`8 mapped/7 all-passed` → `81.6`), xem mục 2/AC-5.
- [x] Zero-denominator: không AC trong scope → `0.0`-class (thực ra `34.2` khi các thành phần khác >0; riêng thành phần AC = `0%`) — xác nhận real DB (`bbc04a`)
- [x] Zero-denominator: không observation point → tương tự, xác nhận real DB (`bbc04b`)
- [x] Zero-denominator: không case planned → `0.0`, xác nhận real DB (`bbc04c`)
- [x] `SKIP`/`NOT_RUN` không được tính là PASS — xác nhận real DB (`bbc05`, `bbc06`), bằng đúng kết quả với `FAIL`
- [x] Rounding 1 chữ số thập phân đúng ở từng bước trước khi cộng trọng số — xác nhận real DB qua phân số tuần hoàn 5/7 (`bbc14`)

---

## 7. Bugs Found and Resolved

| bug | cause | fix | test |
|---|---|---|---|
| `findBlackboxCoverageCounts` (priority sub-query) luôn ném `PSQLException: column reference "priority" is ambiguous` khi chạy trên schema thật | `PRIORITY_WEIGHT_SQL` viết `CASE priority ...` không qualify bảng; `tbl_fact_blackbox_case` và `tbl_dim_ticket` (JOIN cùng câu SQL) đều có cột `priority` | Đổi thành `CASE bc.priority ...` trong `QaDashboardJdbcAdapter.java` | `BlackboxCoverageBlackboxCaseIntegrationTest` (13/13 PASS sau fix); rerun `QaDashboardServiceTest`/`QaDashboardJdbcAdapterTest`/`QaDashboardControllerTest` xác nhận không hồi quy |

---

## 8. Known Risks / Not Handled Yet / Remaining Issues

| item | reason | impact | owner | deadline |
|---|---|---|---|---|
| BBC-09..13 (auth/permission/contract) chưa chạy qua HTTP thật + role thật từ DB | Cần khởi động Spring Boot app thật + seed `tbl_auth_*` + login thật; ngoài phạm vi thời gian của lần review này | Chưa có bằng chứng end-to-end cho AC-8 (chỉ có bằng chứng từng phần: HTTP thật/service mock, hoặc service thật/repo mock) | Chưa gán | Trước merge (khuyến nghị) |
| `QaDashboardJdbcAdapterIntegrationTest` (Testcontainers) vẫn skip | Testcontainers không detect Docker qua named pipe trong shell này dù `docker ps` hoạt động bình thường | Chưa có bằng chứng SQL thật qua Testcontainers cho AC-3/4/7 (đã bù đắp một phần bằng `BlackboxCoverageBlackboxCaseIntegrationTest` chạy trực tiếp trên Postgres dev thật) | Chưa gán | Chạy lại trên máy/CI có Docker hoạt động đúng |
| Số liệu ví dụ minh họa ở `spec-pack.md` mục 8 (`8 mapped/9 all-passed`) không tái tạo được bằng dữ liệu quan hệ thật | Do cấu trúc SQL: execution-numerator ⊆ design-numerator | Không ảnh hưởng formula (vẫn đúng), chỉ ảnh hưởng ví dụ minh họa trong tài liệu | Chưa gán | Cân nhắc sửa ví dụ ở `spec-pack.md` mục 8 trong lần cập nhật tài liệu tiếp theo |
| `docs/standards/security.md` mô tả field lỗi là `errorCode`, code thật dùng `error` | Phát hiện ở lần review trước, chưa xử lý | Tài liệu chuẩn không khớp code thật | Chưa gán | Task tài liệu riêng, ngoài phạm vi ticket |

- [x] Đã đối chiếu với mục "Do Not Do This Ticket" (impl-plan.md §13), không vi phạm điều nào
- [x] Đã đối chiếu với "Open Related Issues" (impl-plan.md §14), ghi rõ item nào còn tồn đọng (mục này)

---

## 9. Exception Record

| exception_type | source_section | reason | approved_by_role | expiry | follow_up | owner | status |
|---|---|---|---|---|---|---|---|
| Test gap | AC-8 (BBC-09..13) | Chưa có bằng chứng HTTP+role thật kết hợp trong 1 test | Chưa duyệt | — | Viết integration test end-to-end thật (login + role DB + HTTP) | Chưa gán | Open |
| Test gap | AC-3/4/7 (Testcontainers) | Testcontainers không detect Docker trong môi trường này | Chưa duyệt | — | Chạy lại trên máy/CI có Docker hoạt động đúng | Chưa gán | Open |

---

## 10. AI-generated Predictions

- [x] Đã nêu rõ phần nào là suy đoán/giả định của AI, chưa có bằng chứng chạy thật: **BBC-09..13** (giả định rằng bằng chứng từng phần hiện có — HTTP thật/service mock + service thật/repo mock — đủ tin cậy cho tới khi có test end-to-end thật) là suy đoán rủi ro thấp nhưng chưa được xác nhận trực tiếp.

---

## 11. Items Reviewed by Humans

- [ ] Danh sách mục cần người review xác nhận thêm:
  - Xác nhận bug fix `bc.priority` không có tác dụng phụ khác ngoài phạm vi đã kiểm (79 BE UT + 5 API IT + 13 black-box IT đều PASS sau fix).
  - Quyết định có cần chặn merge cho tới khi có bằng chứng end-to-end cho BBC-09..13 hay chấp nhận rủi ro với bằng chứng từng phần hiện có.
  - Xác nhận số liệu ví dụ mới ở `spec-pack.md` mục 8 (nếu team muốn sửa tài liệu spec cho khớp dữ liệu quan hệ thật).

---

## 12. Final Self-Verdict

- [ ] PASS
- [x] NEEDS_UPDATE
- [ ] BLOCKED

_Lý do NEEDS_UPDATE:_ Một bug thật (ambiguous column, gây lỗi 500 cho mọi request) đã được tìm thấy
và sửa trong lần review này — cần người review xác nhận lại fix trước khi coi là PASS hoàn toàn.
Ngoài ra 5/16 black-box case (BBC-09..13) chưa có bằng chứng end-to-end thật.
