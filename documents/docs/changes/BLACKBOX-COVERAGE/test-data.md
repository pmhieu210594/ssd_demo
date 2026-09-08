# Test Data — BLACKBOX-COVERAGE

- **Ticket:** BLACKBOX-COVERAGE
- **Trạng thái:** Draft
- **Tạo ngày:** 2026-09-07 00:00
- **Cập nhật ngày:** 2026-09-07 00:00
- **Dùng cho:** `blackbox-testcases.md` (BBC-01..16)

> Dữ liệu dưới đây mô tả bằng thuật ngữ nghiệp vụ (AC, observation point, case, priority, status), không mô tả bảng/schema lưu trữ nội bộ.

---

## 1. Precondition data

### 1.1 Master data — Ticket dataset

| Dataset | Mô tả | Tổng AC | AC map case | AC map case toàn bộ PASS | Tổng observation point | Observation point cover PASS (DIRECT) | Planned score | Passed score |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `D-FULL` | Đầy đủ design + execution | 10 | 8 | 7 | 5 | 5 | 24 | 21 |
| `D-EMPTY` | Chưa có `blackbox-testcases.md` | 10 (khai báo ở spec) | 0 | 0 | 0 | 0 | 0 | 0 |
| `D-DESIGN-ONLY` | Có design, chưa chạy test nào | 10 | 8 | 0 | 5 | 0 | 24 | 0 |
| `D-ZERO-AC` | Không có AC trong scope | 0 | 0 | 0 | 5 | 5 | 24 | 21 |
| `D-ZERO-OBS` | Không có observation point nào định nghĩa | 10 | 8 | 7 | 0 | 0 | 24 | 21 |
| `D-ZERO-CASE` | Không có case nào được plan | 10 | 0 | 0 | 5 | 0 | 0 | 0 |

Ghi chú:

- **Đã sửa lại số liệu `D-FULL` sau khi chạy thật (2026-09-07).** Bản gốc tái dùng số liệu ví dụ ở `spec-pack.md` mục 8 (`8 AC mapped / 9 AC all-passed`), nhưng tổ hợp đó **không thể tái tạo bằng dữ liệu quan hệ thật**: theo đúng SQL của `findBlackboxCoverageCounts`, một AC chỉ được tính "all mapped cases passed" nếu nó đã được tính "mapped" trước đó — nên tử số execution luôn ⊆ tử số design, không thể lớn hơn (8 mapped → tối đa 8 all-passed, không thể là 9). Ví dụ ở spec chỉ hợp lệ như input tổng hợp cho unit test thuần công thức (`QaDashboardServiceTest`, vẫn PASS), không phải dữ liệu seed thật. Số liệu `D-FULL` ở đây (8 mapped / 7 all-passed, planned 24 / passed 21) là dữ liệu quan hệ hợp lệ, đã chạy thật qua `BlackboxCoverageBlackboxCaseIntegrationTest` — xem `test-results.md` mục 6.
- `D-EMPTY`: mọi thành phần mẫu số = 0 do chưa parse được gì → mọi tỷ lệ = `0%`.

### 1.2 User permissions

| Role token | Mô tả | Project của ticket test | Quyền QA Dashboard |
| --- | --- | --- | --- |
| `ROLE-NONE` | Không có token / token không hợp lệ | — | Không xác thực được |
| `ROLE-QA-SAME-PROJECT` | User có role `QA` trên đúng project chứa ticket | Trùng | Xem được summary |
| `ROLE-QA-OTHER-PROJECT` | User có role `QA` nhưng ở project khác | Khác | Không xem được summary của project test |
| `ROLE-DEV` | User có role `DEV` trên đúng project | Trùng | Không xem được summary (thiếu quyền QA) |
| `ROLE-ADMIN` | User có role `ADMIN` toàn hệ thống | Bất kỳ | Bypass, xem được mọi project |

### 1.3 Settings / cấu hình

- Priority weight cố định theo spec mục 6.1: `P0=5`, `P1=3`, `P2=1`. Không có setting nào cho phép đổi giá trị này qua UI/API.
- Làm tròn cố định `1` chữ số thập phân cho mọi tỷ lệ phần trăm (spec mục 6.4).

---

## 2. Input data

### 2.1 Case rows (dùng cho biến thể sửa đổi từ `D-FULL`)

| Row ID | Trạng thái execution | Priority | Loại bao phủ observation | Ghi chú |
| --- | --- | --- | --- | --- |
| `ROW-PASS` | `PASS` | — | — | Baseline, dùng trong `D-FULL` |
| `ROW-SKIP` | `SKIP` | — | — | Thay 1 case `PASS` trong `D-FULL` (BBC-05) |
| `ROW-NOT_RUN` | `NOT_RUN` (không có entry trong `test-results.md`) | — | — | Thay 1 case `PASS` trong `D-FULL` (BBC-06) |
| `ROW-P0` | `PASS` → `FAIL` | `P0` | — | Dùng cho BBC-07 biến thể A |
| `ROW-P2` | `PASS` → `FAIL` | `P2` | — | Dùng cho BBC-07 biến thể B |
| `ROW-DIRECT` | `PASS` | — | `DIRECT` | Case bao phủ observation point trực tiếp |
| `ROW-RELATED` | `PASS` | — | `RELATED` | Case chỉ bao phủ observation point liên quan, không tính Observation Coverage |

### 2.2 Raw status values từ nguồn thô (trước khi map)

| Giá trị thô (`test-results.md`) | Map sang status chuẩn |
| --- | --- |
| `SUCCESS` | `PASS` |
| `FAILED` | `FAIL` |
| `SKIPPED` | `SKIP` |
| (không có entry / giá trị lạ khác) | `NOT_RUN` |

### 2.3 Request input (API summary)

| Field | Giá trị mẫu |
| --- | --- |
| `projectId` | ID project chứa ticket đang test |
| `ticketId` | ID của một trong các dataset ở mục 1.1 |
| Header xác thực | Theo role token ở mục 1.2 |

---

## 3. Expected results

### 3.1 Response field theo dataset

| Dataset | HTTP status | `blackboxCoveragePercent` | Ghi chú |
| --- | --- | --- | --- |
| `D-FULL` | 200 | `81.6` | Design=85.0%, Execution=80.1% — xác nhận bằng real DB, xem `test-results.md` mục 6 |
| `D-EMPTY` | 200 | `0.0` | Mọi thành phần mẫu số = 0 — xác nhận bằng real DB |
| `D-DESIGN-ONLY` | 200 | `25.5` | Design = 85% (giống `D-FULL`), Execution = 0% — xác nhận bằng real DB |
| `D-ZERO-AC` | 200 | `34.2` | `AC Coverage` (Design & Execution) = 0% do 0 AC trong scope — xác nhận bằng real DB |
| `D-ZERO-OBS` | 200 | `56.6` | `Observation Coverage` (Design & Execution) = 0% — xác nhận bằng real DB |
| `D-ZERO-CASE` | 200 | `0.0` | `Priority Coverage` = 0% do 0 case planned — xác nhận bằng real DB |

### 3.2 Response theo role (BBC-09..11)

| Role | HTTP status | Body |
| --- | --- | --- |
| `ROLE-NONE` | 401 | Không có `blackboxCoveragePercent` |
| `ROLE-DEV` (đúng project, sai role) | 403 | `ErrorResponse(timestamp, status, errorCode, message, traceId)`, không có `blackboxCoveragePercent` |
| `ROLE-QA-OTHER_PROJECT` | 403 | Tương tự trên |
| `ROLE-QA-SAME-PROJECT` | 200 | Có `blackboxCoveragePercent` đúng dataset |
| `ROLE-ADMIN` | 200 | Có `blackboxCoveragePercent` đúng dataset, bất kể project |

### 3.3 Log/audit (quan sát được qua công cụ hiện có, không mô tả channel nội bộ)

| Tình huống | Kỳ vọng log |
| --- | --- |
| Request bị từ chối do thiếu/sai quyền | Có bản ghi cảnh báo (WARN) tương ứng, theo cơ chế log hiện hữu của hệ thống |
| Mẫu số = 0 cho một thành phần | Có ghi chú lý do (`no AC in scope` / `no observation points defined` / `no case planned`) theo spec mục 6.2, nếu hệ thống có xuất log/diagnostic cho việc này |

### 3.4 Giá trị dẫn xuất dùng để đối chiếu (theo công thức spec, không phải implementation)

| Đại lượng | Công thức | Giá trị với `D-FULL` |
| --- | --- | --- |
| `AC Coverage (Design)` | AC có case map / tổng AC | `8/10 = 80.0%` |
| `Observation Coverage (Design)` | Observation point cover trực tiếp / tổng | `5/5 = 100.0%` |
| `Blackbox Design Coverage` | `0.75 × 80.0 + 0.25 × 100.0` | `85.0%` |
| `AC Coverage (Execution)` | AC có case map và toàn bộ PASS / tổng AC | `7/10 = 70.0%` |
| `Observation Coverage (Execution)` | Observation point cover trực tiếp PASS / tổng | `5/5 = 100.0%` |
| `Priority Coverage` | Passed score / planned score | `21/24 = 87.5%` |
| `Blackbox Execution Coverage` | `0.60×70.0 + 0.25×100.0 + 0.15×87.5` | `80.1%` |
| `Blackbox Overall Coverage` | `0.30×85.0 + 0.70×80.1` | `81.6%` |

Ghi chú: bảng này đã được cập nhật ngày 2026-09-07 sau khi phát hiện tổ hợp `8 mapped / 9 all-passed` ở bản gốc (theo ví dụ minh họa của `spec-pack.md` mục 8) không thể tái tạo bằng dữ liệu quan hệ thật — xem ghi chú ở mục 1.1. Số liệu ở đây đã chạy thật qua `BlackboxCoverageBlackboxCaseIntegrationTest`.
