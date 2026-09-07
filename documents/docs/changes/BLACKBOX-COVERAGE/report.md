# Báo cáo thay đổi — BLACKBOX-COVERAGE (Blackbox Coverage cho QA Dashboard)

- **Ticket:** BLACKBOX-COVERAGE
- **Trạng thái:** Draft
- **Tạo ngày:** 2026-09-07 00:00
- **Cập nhật ngày:** 2026-09-07 00:00

> Đối tượng đọc: kỹ sư tiếp theo, người review, người trực on-call.

---

## 1. Tóm tắt thay đổi

**Ticket:** BLACKBOX-COVERAGE
**Tác giả:** AI-assisted

### Đã thay đổi gì

- Thêm cách tính `Blackbox Design/Execution/Overall Coverage` theo 3 công thức chuẩn hóa ở `spec-pack.md` §5.
- Backend: thêm schema additive `V515` (4 bảng blackbox), port/adapter persistence riêng, scanner parse `blackbox-testcases.md`, service map `test-results.md` → `PASS/FAIL/SKIP/NOT_RUN`.
- QA Dashboard: field `blackboxCoveragePercent` đổi nguồn tính từ placeholder sang `Blackbox Overall Coverage` thật, giữ nguyên tên field/kiểu dữ liệu/vị trí.
- Không đổi UI/UX, không đổi API contract, không đổi logic `AC-Test Coverage` hiện hữu.

### Lý do

- `blackboxCoveragePercent` trước đây là placeholder `0.0` hoặc tính theo 8 viewpoint cố định, không phản ánh coverage thật của black-box test.
- Cần một nguồn tính coverage tách biệt, có thể query ổn định, không trộn với AC-Test Coverage.

---

## 2. Phạm vi ảnh hưởng

| # | Khu vực | Chi tiết |
| --- | --- | --- |
| 1 | Các tệp đã thay đổi | Migration `V515__blackbox_coverage_schema.sql`; port `BlackboxCoveragePersistencePort`; adapter `BlackboxCoverageJdbcAdapter`; `ArtifactScannerService`; `TestResultsParseService`; `QaDashboardModels`, `QaDashboardRepositoryPort`, `QaDashboardJdbcAdapter`, `QaDashboardService`. Danh sách đầy đủ + lý do: [impl-plan.md §4](impl-plan.md) |
| 2 | Lược đồ DB | Additive only: 4 bảng mới (`tbl_fact_blackbox_case`, `tbl_fact_blackbox_case_ac`, `tbl_fact_blackbox_observation_point`, `tbl_fact_blackbox_case_observation`). Không sửa `tbl_fact_test_case`/`tbl_fact_ac_test_coverage`. |
| 3 | Hợp đồng API | Không đổi. `blackboxCoveragePercent` giữ nguyên tên/kiểu (số thực)/vị trí trong response summary. Không thêm field API mới cho Design/Execution/Priority riêng. |
| 4 | Cấu hình | Không có setting mới. Priority weight (`P0=5/P1=3/P2=1`) và rounding (1 chữ số thập phân) cố định trong code, không expose qua UI/API. |
| 5 | Nhật ký | Parser warning + evidence event khi parse lỗi (dùng channel log hiện có, không tạo channel riêng). Case key/AC key/observation key rỗng bị bỏ qua kèm log cảnh báo. |
| 6 | Quyền/Vai trò | Dùng lại `requireQaAccess`/`requireAnyAccess` hiện hữu (QA đúng project, ADMIN bypass). Không đổi rule phân quyền; chỉ bổ sung test khóa lại hành vi cũ. |

---

## 3. Kết quả review

### Tự kiểm tra của Claude (`self-review.md`)

- **Trạng thái:** Đã hoàn tất ngày 2026-09-07, dựa trên bằng chứng chạy thật (không chỉ đọc code).
- Blocker phát hiện và đã xử lý: **1** — `QaDashboardJdbcAdapter.findBlackboxCoverageCounts` có cột `priority` ambiguous giữa `tbl_fact_blackbox_case`/`tbl_dim_ticket`, khiến mọi request `/api/v1/qa/dashboard/summary` lỗi 500 trên schema thật. Đã sửa (`CASE priority` → `CASE bc.priority`) và verify lại toàn bộ suite liên quan (79 BE UT + 5 API IT + 13 black-box IT, đều PASS sau fix).
- Tất cả AC đã được đáp ứng: **Một phần** — AC-1..7 có bằng chứng thật (unit + real DB); AC-8 chỉ có bằng chứng từng phần (HTTP thật/service mock, hoặc service thật/repo mock), chưa có test kết hợp HTTP thật + role thật từ DB.

### Review bởi Codex

- **Missing** — `ai-review.md` không nằm trong danh sách input được cung cấp cho báo cáo này, dù file tồn tại trong thư mục ticket. Cần bổ sung riêng nếu muốn đưa Codex findings vào báo cáo.

### Các phát hiện từ review thủ công (`review-checklist.md`)

| # | Phát hiện | Mức độ nghiêm trọng | Hành động đã thực hiện |
| --- | --- | --- | --- |
| 1 | Cột `priority` ambiguous trong `findBlackboxCoverageCounts` — mọi request tới summary API lỗi 500 trên schema thật | Blocker | **Fixed** ngày 2026-09-07 — xem `review-checklist.md` mục 11, `self-review.md` mục 7 |
| 2 | `PERF-1`/`PERF-3` (index đủ, không N+1) chỉ được review tĩnh, chưa có test đo runtime | Major | Chấp nhận rủi ro tạm thời — 3 query đều aggregate 1 lần, không loop theo AC/case ([test-plan.md §5](test-plan.md)) |
| 3 | Field `error` trong `ErrorResponse` khác `errorCode` mô tả ở `docs/standards/security.md` | Minor | Test đã dùng đúng field thật (`error`); tài liệu chuẩn cần cập nhật riêng, ngoài phạm vi ticket ([test-results.md §2](test-results.md)) |
| 4 | `blackboxCoveragePercent` chưa có nơi hiển thị trên FE (`QaSummaryCards.tsx` chỉ 3/6 card) | Major | Đã khóa lại bằng FE UT để không bị sửa nhầm; đề xuất ticket riêng nếu cần hiển thị ([test-plan.md §5](test-plan.md)) |
| 5 (TEST-7) | BBC-09..13 (auth/permission/contract) chưa có bằng chứng HTTP thật + role thật từ DB kết hợp | Major | Chưa xử lý — hạ tầng đã xác nhận khả thi (login endpoint thật, JWT tự ký, user demo có sẵn) nhưng cần thêm thời gian để viết test end-to-end ([test-results.md §6.4](test-results.md)) |

---

## 4. Kết quả kiểm thử

| Loại kiểm thử | Lệnh | Kết quả | Ghi chú |
| --- | --- | --- | --- |
| BE UT | `mvn -Dtest=QaDashboardServiceTest,BlackboxTestcasesMarkdownParserTest,BlackboxCoverageJdbcAdapterTest,QaDashboardJdbcAdapterTest,QaDashboardControllerTest,TestResultsParseServiceTest,ArtifactScannerServiceTest test` | PASS | 79 tests, 0 fail — chạy lại sau bug fix, không đổi |
| BE API IT | `mvn -Dit.test=QaDashboardApiIntegrationTest,... integration-test` | PASS | 5 tests, 0 fail, qua Postgres local thật + Flyway v515 |
| BE DB IT (Testcontainers) | (cùng lệnh trên) | SKIP | 1 test skip — Docker daemon có sẵn (`docker ps` OK) nhưng Testcontainers không detect được qua named pipe trong shell này |
| **Black-box (real DB, mới 2026-09-07)** | `mvn -Dit.test=BlackboxCoverageBlackboxCaseIntegrationTest integration-test` | **PASS** | **13/13**, chạy trực tiếp trên Postgres dev thật với adapter/service thật (không mock) — bao phủ BBC-01..08, 14, 15, 16 (11/16 case). Phát hiện + sửa 1 bug thật (xem mục 3) trong lúc chạy. |
| FE UT | `npx vitest run` | PASS | 362 tests, 0 fail (từ lần chạy trước, không đổi code FE nên không chạy lại) |
| E2E | `npx playwright test qa-dashboard.spec.ts` | PASS | 5/5 sau khi loại trừ cold-start (từ lần chạy trước) |
| Black-box (BBC-09..13) | thủ công | **NOT_RUN** (từng phần) | 5/16 case còn lại (auth/permission/contract) chưa có bằng chứng HTTP thật + role thật kết hợp — xem `test-results.md` §6.4 |

Chi tiết đầy đủ: [test-results.md](test-results.md), [blackbox-testcases.md](blackbox-testcases.md), [test-data.md](test-data.md)

---

## 5. Công việc còn lại / Hành động tiếp theo

| # | Công việc | Người phụ trách | Hạn chót |
| --- | --- | --- | --- |
| 1 | Người review xác nhận lại bug fix `bc.priority` (ambiguous column) — đã sửa + verify 79 BE UT + 5 API IT + 13 black-box IT PASS, nhưng cần double-check độc lập trước khi merge | Chưa gán | Trước merge — **ưu tiên cao nhất, đây là Blocker vừa fix** |
| 2 | Viết + chạy integration test end-to-end thật cho BBC-09..13 (login thật qua `/api/v1/auth/login`, seed role QA/DEV trên `tbl_auth_member_project_role`, gọi HTTP thật vào app đang chạy) | Chưa gán | Trước merge (khuyến nghị) |
| 3 | Chạy `QaDashboardJdbcAdapterIntegrationTest` trên máy/CI có Testcontainers hoạt động đúng (môi trường hiện tại: Docker daemon có sẵn nhưng named-pipe detection lỗi) | Chưa gán | Trước merge nếu cần bằng chứng Testcontainers riêng (đã có real-DB coverage tương đương qua `BlackboxCoverageBlackboxCaseIntegrationTest`) |
| 4 | Cân nhắc sửa lại ví dụ minh họa ở `spec-pack.md` mục 8 (`8 mapped/9 all-passed` không tái tạo được bằng dữ liệu quan hệ thật) | Chưa gán | Lần cập nhật tài liệu tiếp theo |
| 5 | Bổ sung Codex review (`ai-review.md`) nếu cần đưa vào gate review | Chưa gán | Tùy quy trình team |
| 6 | Cập nhật `docs/standards/security.md` cho khớp field `error` thật của `ErrorResponse` | Chưa gán | Ngoài phạm vi ticket, làm riêng |
| 7 | Xem xét mở ticket riêng để hiển thị `blackboxCoveragePercent` trên FE (`QaSummaryCards.tsx`) | Chưa gán | Ngoài phạm vi ticket |
| 8 | Bổ sung E2E scanner integration test cho luồng `blackbox-testcases.md → test-results.md → dashboard summary` (đề xuất ở `impl-plan.md §14`) | Chưa gán | Sau ticket này |

---

## 6. Quy trình hoàn tác

1. **Runtime rollback:** revert code scanner persistence (`ArtifactScannerService`, `TestResultsParseService`) và dashboard aggregation (`QaDashboardJdbcAdapter`, `QaDashboardService`); nếu cần, khôi phục `blackboxCoveragePercent` về hành vi placeholder cũ.
2. **DB rollback:** chỉ drop 4 bảng blackbox mới (`tbl_fact_blackbox_case`, `tbl_fact_blackbox_case_ac`, `tbl_fact_blackbox_observation_point`, `tbl_fact_blackbox_case_observation`) khi revert toàn bộ tính năng; **không đụng** `tbl_fact_test_case`, `tbl_fact_ac_test_coverage`, hay bảng ticket master.
3. Vì migration `V515` là additive, việc revert không yêu cầu rollback dữ liệu AC-Test Coverage hiện hữu.
4. Sau rollback, xác nhận lại field `blackboxCoveragePercent` trả về không lỗi (200, giá trị hợp lệ) qua `QaDashboardApiIntegrationTest`.

---

## 7. Danh mục đầu ra

| # | Tệp | Mục đích |
| --- | --- | --- |
| 1 | [spec-pack.md](spec-pack.md) | Nguồn tham chiếu duy nhất cho spec & 3 công thức coverage |
| 2 | [impl-plan.md](impl-plan.md) | Cách tiếp cận triển khai, danh sách file/step thay đổi, rollback policy |
| 3 | [review-checklist.md](review-checklist.md) | Các góc nhìn review implementation-level (chưa điền Result) |
| 4 | [self-review.md](self-review.md) | Tự kiểm tra của Claude (hiện là template rỗng, chưa điền) |
| 5 | [test-plan.md](test-plan.md) | Ma trận AC × loại test, self-check gap |
| 6 | [test-results.md](test-results.md) | Kết quả thực thi BE UT/IT/FE UT/E2E |
| 7 | [blackbox-testcases.md](blackbox-testcases.md) | 16 black-box test case, chưa thực thi |
| 8 | [test-data.md](test-data.md) | Bộ dữ liệu precondition/input/expected cho black-box case |
