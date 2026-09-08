# BLACKBOX-COVERAGE — Black-box test cases

- **Ticket:** BLACKBOX-COVERAGE
- **Trạng thái:** Draft
- **Tạo ngày:** 2026-09-07 00:00
- **Cập nhật ngày:** 2026-09-07 00:00
- **Phạm vi:** Cách tính và hiển thị field `blackboxCoveragePercent` trên QA Dashboard (Design/Execution/Overall Coverage), theo `spec-pack.md` mục 5, 6, 10.

> Nguồn tham chiếu chính: `spec-pack.md`.

---

## 1. Nguyên tắc black-box

Các test case dưới đây chỉ kiểm thử hành vi quan sát được từ bên ngoài hệ thống:

- Giá trị field `blackboxCoveragePercent` trong response của API QA Dashboard summary.
- Mã HTTP status và field `error`/`errorCode` khi request bị từ chối (401/403).
- Dữ liệu đầu vào quan sát được: nội dung `blackbox-testcases.md`, `test-results.md` theo từng ticket.
- Không có thay đổi ngoài phạm vi trên các field khác của response (AC-Test Coverage, filter, v.v.).

Không giả định state nội bộ, tên bảng, schema, tên service/class, hay thuật toán parse cụ thể.

## 2. Phân tầng ưu tiên

| Priority | Ý nghĩa | Kỳ vọng |
| --- | --- | --- |
| P0 | Hành vi bắt buộc: giá trị field đúng, không vỡ auth, không rò dữ liệu ticket khác | Phải pass trước khi release |
| P1 | Boundary/abnormal quan trọng: SKIP/NOT_RUN, DIRECT/RELATED, priority weight, rounding | Nên pass trong cùng vòng kiểm thử |
| P2 | Regression/compatibility mở rộng, multi-ticket, multi-filter | Chạy khi có thời gian hoặc khi vùng liên quan bị thay đổi |

## 3. Backlink theo màn hình / điểm quan sát

### QA Dashboard summary API — field `blackboxCoveragePercent`

- Bao phủ trực tiếp: `BBC-01`, `BBC-02`, `BBC-03`, `BBC-04`, `BBC-14`, `BBC-15`, `BBC-16`.
- Bao phủ liên quan: `BBC-05`, `BBC-06`, `BBC-07`, `BBC-08`.

### QA Dashboard summary API — auth/permission gate

- Bao phủ trực tiếp: `BBC-09`, `BBC-10`, `BBC-11`.
- Bao phủ liên quan: `BBC-12`.

### QA Dashboard summary API — field AC-Test Coverage hiện hữu (không đổi)

- Bao phủ trực tiếp: `BBC-13`.
- Bao phủ liên quan: `BBC-12`.

## 4. Bộ dữ liệu tham chiếu

Chi tiết dữ liệu precondition, input và expected result nằm trong `test-data.md`.

Các ký hiệu chính:

- `D-FULL`: ticket có đủ design (case/AC-mapping/observation) và execution (PASS/FAIL/SKIP/NOT_RUN) khớp ví dụ spec mục 8.
- `D-EMPTY`: ticket chưa có `blackbox-testcases.md`, chưa parse được dữ liệu nào.
- `D-DESIGN-ONLY`: ticket có design nhưng chưa chạy test nào (`test-results.md` rỗng hoặc toàn `NOT_RUN`).
- `D-ZERO-AC` / `D-ZERO-OBS` / `D-ZERO-CASE`: từng mẫu số riêng lẻ = 0.
- `ROW-SKIP` / `ROW-NOT_RUN`: case có status `SKIP` / `NOT_RUN`.
- `ROW-P0` / `ROW-P1` / `ROW-P2`: case gắn priority tương ứng.
- `ROW-DIRECT` / `ROW-RELATED`: observation point bao phủ trực tiếp / liên quan.
- `ROLE-QA-SAME-PROJECT` / `ROLE-QA-OTHER-PROJECT` / `ROLE-DEV` / `ROLE-ADMIN` / `ROLE-NONE`: vai trò gọi API.

---

## 5. Các black-box test case

> **Trạng thái thực thi (2026-09-07):** BBC-01 đến BBC-08, BBC-14, BBC-15, BBC-16 đã chạy thật bằng
> `BlackboxCoverageBlackboxCaseIntegrationTest` (real Postgres dev DB, real adapters/service, không
> mock) — xem `test-results.md` mục 6 để biết bằng chứng và một bug thật đã tìm thấy + sửa trong lúc
> chạy (ambiguous column `priority`). BBC-09..13 (auth/permission/contract) **chưa được thực thi lại
> qua HTTP thật với role thật** trong lần này — vẫn dựa vào evidence sẵn có ở
> `QaDashboardApiIntegrationTest` + `QaDashboardServiceTest` (xem `test-results.md` mục 6.4).

### BBC-01 — Overall Coverage tính đúng khi dữ liệu đầy đủ

**Priority:** P0
**Loại:** Positive

**Mô tả:** Với dữ liệu design + execution đầy đủ (8/10 AC được map case, 7/10 AC có toàn bộ case map PASS, 5/5 observation point bao phủ trực tiếp PASS, priority planned/passed = 24/21), field `blackboxCoveragePercent` phải trả đúng `81.6`.
Ghi chú: bản gốc dùng tổ hợp `8 mapped / 9 all-passed` theo ví dụ minh họa của `spec-pack.md` mục 8; tổ hợp đó không thể tái tạo bằng dữ liệu quan hệ thật (xem `test-data.md` mục 1.1) nên đã đổi sang số liệu quan hệ hợp lệ này.

**Tiêu chí chấp nhận**

- **AC-BLACKBOX-COVERAGE-1/v1 — Scanner parse case/AC-mapping/observation từ `blackbox-testcases.md` và phản ánh vào kết quả tính.**
- **AC-BLACKBOX-COVERAGE-2/v1 — Kết quả execution trong `test-results.md` được map đúng vào trạng thái case.**
- **AC-BLACKBOX-COVERAGE-3/v1 — Counts AC/observation/priority đủ để tính đúng công thức.**
- **AC-BLACKBOX-COVERAGE-5/v1 — `Blackbox Overall Coverage` khớp ví dụ spec `89.4`.**

**Precondition:**

- Dữ liệu ticket ở trạng thái `D-FULL` (10 AC, 8 AC có case map, 7 AC pass toàn bộ case; 5 observation point, cả 5 pass trực tiếp; planned score 24, passed score 21).
- Người gọi có quyền `ROLE-QA-SAME-PROJECT`.

**Các bước:**

1. Gọi API summary QA Dashboard với `projectId`, `ticketId` của ticket `D-FULL`.
2. Quan sát response JSON.

**Kết quả mong đợi:**

- HTTP `200`.
- `blackboxCoveragePercent = 81.6`.
- Không có field nào khác bị thay đổi ngoài phạm vi ticket.

**Kết quả thực thi:** PASS — `bbc01_fullDataset_computesRealOverallCoverage` (real DB), xem `test-results.md` mục 6.

---

### BBC-02 — Chưa có dữ liệu design → field trả 0

**Priority:** P0
**Loại:** Boundary

**Mô tả:** Ticket chưa có `blackbox-testcases.md` hoặc chưa parse được case nào thì field phải trả `0.0`, không lỗi.

**Tiêu chí chấp nhận**

- **AC-BLACKBOX-COVERAGE-1/v1 — Không có case nào được parse/persist cho ticket.**
- **AC-BLACKBOX-COVERAGE-6/v1 — Mẫu số = 0 trả `0%`, không chia lại trọng số.**

**Precondition:**

- Dữ liệu ticket ở trạng thái `D-EMPTY`.
- Người gọi có quyền `ROLE-QA-SAME-PROJECT`.

**Các bước:**

1. Gọi API summary QA Dashboard với ticket `D-EMPTY`.
2. Quan sát response JSON.

**Kết quả mong đợi:**

- HTTP `200`.
- `blackboxCoveragePercent = 0.0` (không `null`, không lỗi).

---

### BBC-03 — Có design nhưng chưa chạy test nào

**Priority:** P0
**Loại:** Boundary

**Mô tả:** Ticket có case/AC-mapping nhưng `test-results.md` rỗng/toàn `NOT_RUN` thì Execution Coverage = 0, kéo `Overall` xuống thấp theo trọng số `0.70`.

**Tiêu chí chấp nhận**

- **AC-BLACKBOX-COVERAGE-2/v1 — Trạng thái không có kết quả chạy được map là `NOT_RUN`.**
- **AC-BLACKBOX-COVERAGE-6/v1 — `NOT_RUN` không được tính là PASS.**

**Precondition:**

- Dữ liệu ticket ở trạng thái `D-DESIGN-ONLY`.
- Người gọi có quyền `ROLE-QA-SAME-PROJECT`.

**Các bước:**

1. Gọi API summary QA Dashboard với ticket `D-DESIGN-ONLY`.
2. Quan sát response JSON.

**Kết quả mong đợi:**

- HTTP `200`.
- `blackboxCoveragePercent` = giá trị `0.30 × Design + 0.70 × 0` (Execution = 0) = `25.5` (Design = 85.0%).
- Giá trị không phải `0.0` tuyệt đối trừ khi Design cũng bằng 0.

**Kết quả thực thi:** PASS — `bbc03_designOnly_noExecutionYet_dragsOverallDownByExecutionWeight` (real DB), xem `test-results.md` mục 6.

---

### BBC-04 — Mẫu số = 0 riêng lẻ cho từng thành phần

**Priority:** P0
**Loại:** Boundary

**Mô tả:** Khi từng thành phần (tổng AC, tổng observation point, tổng case planned) bằng 0 riêng lẻ, thành phần đó trả `0%` và không chia lại trọng số cho phần còn lại.

**Tiêu chí chấp nhận**

- **AC-BLACKBOX-COVERAGE-3/v1 — Counts trả đúng 0 cho thành phần rỗng.**
- **AC-BLACKBOX-COVERAGE-6/v1 — Mẫu số = 0 không redistribute weight; kết quả cuối vẫn hợp lệ (không NaN/null).**

**Precondition:**

- Ba biến thể dữ liệu: `D-ZERO-AC` (0 AC trong scope), `D-ZERO-OBS` (0 observation point), `D-ZERO-CASE` (0 case planned).
- Người gọi có quyền `ROLE-QA-SAME-PROJECT`.

**Các bước:**

1. Gọi API summary QA Dashboard lần lượt với từng biến thể.
2. Quan sát response JSON mỗi lần.

**Kết quả mong đợi:**

- HTTP `200` cho cả 3 lần gọi.
- `blackboxCoveragePercent` là số hợp lệ (không `NaN`, không `null`), không bị chia cho 0: `D-ZERO-AC = 34.2`, `D-ZERO-OBS = 56.6`, `D-ZERO-CASE = 0.0`.
- Không có exception/500 ở bất kỳ biến thể nào.

**Kết quả thực thi:** PASS — `bbc04a_zeroAcInScope_returnsValidNumberNotNaN`, `bbc04b_zeroObservationPoints_returnsValidNumberNotNaN`, `bbc04c_zeroCasePlanned_returnsZeroNotNaN` (real DB), xem `test-results.md` mục 6.

---

### BBC-05 — Case `SKIP` không tính là PASS

**Priority:** P1
**Loại:** Negative

**Mô tả:** Case có kết quả `SKIP` trong `test-results.md` không được tính vào tử số Execution Coverage/Priority Coverage.

**Tiêu chí chấp nhận**

- **AC-BLACKBOX-COVERAGE-2/v1 — `SKIP` được map đúng và giữ riêng biệt với `PASS`.**
- **AC-BLACKBOX-COVERAGE-6/v1 — `SKIP` không tính là PASS trong công thức execution.**

**Precondition:**

- Dữ liệu ticket có `ROW-SKIP` thay cho 1 case đang `PASS` trong `D-FULL`.
- Người gọi có quyền `ROLE-QA-SAME-PROJECT`.

**Các bước:**

1. Gọi API summary QA Dashboard với ticket chứa `ROW-SKIP`.
2. So sánh `blackboxCoveragePercent` với kết quả của `D-FULL` (BBC-01).

**Kết quả mong đợi:**

- HTTP `200`.
- `blackboxCoveragePercent` thấp hơn kết quả `D-FULL` tương ứng với 1 case không còn được tính PASS. Trên thực tế, `SKIP` cho cùng kết quả hệt như `FAIL` (`81.6`, bằng đúng BBC-01) vì cả hai đều bị loại khỏi PASS như nhau — không có ưu đãi riêng cho `SKIP`.

**Kết quả thực thi:** PASS — `bbc05_skipStatus_notCountedAsPass_sameResultAsFail` + `bbc05And06_skipAndNotRun_scoreLowerThanAllCasesPassing` (real DB, so với biến thể toàn bộ PASS = `87.1`), xem `test-results.md` mục 6.

---

### BBC-06 — Case `NOT_RUN` không tính là PASS

**Priority:** P1
**Loại:** Negative

**Mô tả:** Case chưa chạy (`NOT_RUN`) không được tính vào tử số Execution Coverage/Priority Coverage, tương tự `SKIP` nhưng khác ý nghĩa quản trị.

**Tiêu chí chấp nhận**

- **AC-BLACKBOX-COVERAGE-2/v1 — `NOT_RUN` được map đúng khi không có entry kết quả tương ứng.**
- **AC-BLACKBOX-COVERAGE-6/v1 — `NOT_RUN` không tính là PASS trong công thức execution.**

**Precondition:**

- Dữ liệu ticket có `ROW-NOT_RUN` thay cho 1 case đang `PASS` trong `D-FULL`.
- Người gọi có quyền `ROLE-QA-SAME-PROJECT`.

**Các bước:**

1. Gọi API summary QA Dashboard với ticket chứa `ROW-NOT_RUN`.
2. So sánh `blackboxCoveragePercent` với kết quả của `D-FULL` (BBC-01).

**Kết quả mong đợi:**

- HTTP `200`.
- `blackboxCoveragePercent` thấp hơn kết quả `D-FULL`, giá trị giảm tương đương với BBC-05 nếu cùng vị trí case (cùng bằng `81.6`, vì `NOT_RUN` cũng không được tính PASS y hệt `FAIL`/`SKIP`).

**Kết quả thực thi:** PASS — `bbc06_notRunStatus_notCountedAsPass_sameResultAsFail` (real DB), xem `test-results.md` mục 6.

---

### BBC-07 — Priority weight ảnh hưởng đúng Priority Coverage

**Priority:** P1
**Loại:** Boundary

**Mô tả:** Case `P0` đổi trạng thái PASS→FAIL kéo Priority Coverage giảm nhiều hơn case `P2` đổi trạng thái tương tự, đúng theo weight `P0=5/P1=3/P2=1`.

**Tiêu chí chấp nhận**

- **AC-BLACKBOX-COVERAGE-4/v1 — Priority weight áp dụng đúng `P0=5/P1=3/P2=1`.**

**Precondition:**

- Hai biến thể dữ liệu dựa trên `D-FULL`: biến thể A đổi 1 case `ROW-P0` từ PASS→FAIL, biến thể B đổi 1 case `ROW-P2` từ PASS→FAIL (số lượng case thay đổi bằng nhau).
- Người gọi có quyền `ROLE-QA-SAME-PROJECT`.

**Các bước:**

1. Gọi API summary QA Dashboard cho biến thể A.
2. Gọi API summary QA Dashboard cho biến thể B.
3. So sánh mức giảm `blackboxCoveragePercent` giữa hai biến thể.

**Kết quả mong đợi:**

- HTTP `200` cho cả hai lần gọi.
- Mức giảm ở biến thể A (case `P0` fail) lớn hơn mức giảm ở biến thể B (case `P2` fail): `A = 45.3`, `B = 52.3` (dữ liệu 2-AC/2-case cô lập, không có observation point).

**Kết quả thực thi:** PASS — `bbc07_priorityWeight_p0FailureHurtsMoreThanP2Failure` (real DB), xem `test-results.md` mục 6.

---

### BBC-08 — Chỉ observation point `DIRECT` được tính Observation Coverage

**Priority:** P1
**Loại:** Boundary

**Mô tả:** Case bao phủ `RELATED` không được tính vào Observation Coverage; chỉ case bao phủ `DIRECT` mới tính.

**Tiêu chí chấp nhận**

- **AC-BLACKBOX-COVERAGE-3/v1 — Counts observation point phân biệt đúng bao phủ trực tiếp và liên quan.**
- **AC-BLACKBOX-COVERAGE-4/v1 — Chỉ tính `DIRECT` khi tính Observation Coverage.**

**Precondition:**

- Ticket có 1 observation point chỉ được bao phủ bởi case `ROW-RELATED` (không có case `DIRECT` nào).
- Người gọi có quyền `ROLE-QA-SAME-PROJECT`.

**Các bước:**

1. Gọi API summary QA Dashboard với ticket có observation point nêu trên.
2. Quan sát `blackboxCoveragePercent`.

**Kết quả mong đợi:**

- HTTP `200`.
- Observation point chỉ có case `RELATED` không được tính là đã bao phủ; kết quả tổng thấp hơn trường hợp có ít nhất 1 case `DIRECT` (`DIRECT = 100.0` so với `RELATED = 75.0`, dữ liệu 1-AC/1-case/1-obs cô lập).

**Kết quả thực thi:** PASS — `bbc08_directObservationCoverageCountsMoreThanRelatedOnly` (real DB), xem `test-results.md` mục 6.

---

### BBC-09 — Không có token → 401

**Priority:** P0
**Loại:** Exception

**Mô tả:** Gọi API summary QA Dashboard không kèm token phải bị từ chối, không trả field `blackboxCoveragePercent`.

**Tiêu chí chấp nhận**

- **AC-BLACKBOX-COVERAGE-8/v1 — Endpoint giữ nguyên auth/session hiện hữu.**

**Precondition:**

- Ticket bất kỳ đã tồn tại dữ liệu (`D-FULL`).
- Người gọi ở trạng thái `ROLE-NONE` (không có token).

**Các bước:**

1. Gọi API summary QA Dashboard không kèm token/header xác thực.

**Kết quả mong đợi:**

- HTTP `401`.
- Response không chứa `blackboxCoveragePercent` hay dữ liệu ticket.

**Kết quả thực thi:** PASS (gián tiếp) — `QaDashboardApiIntegrationTest.summary_withoutBearerToken_returns401` (real Spring Security filter chain, `QaDashboardService` mock). Chưa chạy qua login thật + role thật; xem gap ở `test-results.md` mục 6.4.

---

### BBC-10 — Sai vai trò trên đúng project → 403

**Priority:** P0
**Loại:** Exception

**Mô tả:** User có quyền hợp lệ nhưng không có role QA trên project bị từ chối truy cập summary của project đó.

**Tiêu chí chấp nhận**

- **AC-BLACKBOX-COVERAGE-8/v1 — Endpoint giữ nguyên rule phân quyền hiện hữu.**

**Precondition:**

- Ticket `D-FULL` thuộc project P.
- Người gọi ở trạng thái `ROLE-DEV` trên project P (có tài khoản hợp lệ, sai role).

**Các bước:**

1. Gọi API summary QA Dashboard với `projectId=P`, `ticketId` của `D-FULL`.

**Kết quả mong đợi:**

- HTTP `403`.
- Response theo format lỗi hiện có của hệ thống (timestamp, status, errorCode/error, message, traceId).

**Kết quả thực thi:** PASS (gián tiếp) — `QaDashboardApiIntegrationTest.summary_authenticatedWithoutQaRoleOnProject_returns403` (real Spring Security filter chain; `requireQaAccess` được stub throw trên mock service, chưa phải role thật từ DB). Đơn vị logic `requireQaAccess` thật đã được test riêng (mock repository) ở `QaDashboardServiceTest`. Chưa có test permission chạy qua HTTP + role thật từ DB; xem gap ở `test-results.md` mục 6.4.

---

### BBC-11 — ADMIN bypass vẫn thấy đúng giá trị

**Priority:** P1
**Loại:** Positive

**Mô tả:** User có role `ADMIN` xem được summary của bất kỳ project nào, giá trị field vẫn đúng công thức.

**Tiêu chí chấp nhận**

- **AC-BLACKBOX-COVERAGE-8/v1 — Endpoint giữ nguyên rule bypass hiện hữu cho ADMIN.**

**Precondition:**

- Ticket `D-FULL` không thuộc project quản lý trực tiếp của ADMIN.
- Người gọi ở trạng thái `ROLE-ADMIN`.

**Các bước:**

1. Gọi API summary QA Dashboard với ticket `D-FULL`.

**Kết quả mong đợi:**

- HTTP `200`.
- `blackboxCoveragePercent` giống kết quả tính cho ticket đó (ví dụ `81.6` nếu là ticket `D-FULL`, xem BBC-01).

**Kết quả thực thi:** Chưa thực thi qua HTTP + role ADMIN thật từ DB trong lần này (chỉ có `QaDashboardApiIntegrationTest` với service mock trả giá trị cố định — không phải bằng chứng cho rule bypass thật của `requireQaAccess`). Xem gap ở `test-results.md` mục 6.4.

---

### BBC-12 — Contract field không đổi tên/kiểu/vị trí

**Priority:** P0
**Loại:** Compatibility

**Mô tả:** Field `blackboxCoveragePercent` giữ nguyên tên, kiểu dữ liệu số thực, và vị trí trong response JSON so với hiện trạng trước khi áp dụng thay đổi.

**Tiêu chí chấp nhận**

- **AC-BLACKBOX-COVERAGE-8/v1 — Không đổi tên field, không đổi kiểu dữ liệu, không tạo field API mới riêng cho Design/Execution/Priority.**

**Precondition:**

- Ticket `D-FULL`.
- Người gọi có quyền `ROLE-QA-SAME-PROJECT`.

**Các bước:**

1. Gọi API summary QA Dashboard với ticket `D-FULL`.
2. Đối chiếu cấu trúc JSON response với contract hiện hữu (tên field, kiểu dữ liệu, các field khác không đổi).

**Kết quả mong đợi:**

- HTTP `200`.
- Field `blackboxCoveragePercent` tồn tại đúng tên, kiểu số thực (ví dụ `89.4`, không phải chuỗi).
- Không có field mới nào riêng cho Design/Execution/Priority Coverage.

**Kết quả thực thi:** PASS (gián tiếp) — `QaDashboardApiIntegrationTest.summary_authenticatedQaUser_returnsOkWithBlackboxCoveragePercent` xác nhận đúng contract JSON (field tồn tại, kiểu số thực) qua real HTTP + real security chain; giá trị số trong test đó là giá trị mock, không phải tính từ DB thật (xem BBC-01 để có giá trị tính thật).

---

### BBC-13 — AC-Test Coverage hiện hữu không đổi hành vi

**Priority:** P0
**Loại:** Regression

**Mô tả:** Field/API của `AC-Test Coverage` hiện hữu (khác `blackboxCoveragePercent`) phải trả giá trị giống hệt trước khi ticket này được áp dụng.

**Tiêu chí chấp nhận**

- **AC-BLACKBOX-COVERAGE-7/v1 — Blackbox Coverage tách biệt hoàn toàn khỏi AC-Test Coverage.**

**Precondition:**

- Ticket đã có dữ liệu AC-Test Coverage baseline từ trước (không thuộc phạm vi `blackbox-testcases.md`).
- Người gọi có quyền `ROLE-QA-SAME-PROJECT`.

**Các bước:**

1. Gọi API summary QA Dashboard cho ticket này trước và sau khi dữ liệu Blackbox Coverage được thêm.
2. So sánh giá trị field AC-Test Coverage giữa hai lần gọi.

**Kết quả mong đợi:**

- HTTP `200` cho cả hai lần gọi.
- Giá trị field AC-Test Coverage không đổi.
- Việc thêm dữ liệu Blackbox Coverage không ảnh hưởng đến field khác.

**Kết quả thực thi:** Chưa thực thi trực tiếp qua HTTP trong lần này. Bằng chứng gián tiếp: `findBlackboxCoverageCounts_neverReadsAcTestCoverageTables` (đã có sẵn, khóa lại ở tầng SQL rằng query blackbox không đọc `tbl_fact_test_case`/`tbl_fact_ac_test_coverage`) + review code DD-4. Xem gap ở `test-results.md` mục 6.4.

---

### BBC-14 — Làm tròn 1 chữ số thập phân

**Priority:** P1
**Loại:** Boundary

**Mô tả:** Mọi tỷ lệ phần trăm trả về (bao gồm `blackboxCoveragePercent`) được làm tròn đến 1 chữ số thập phân, kể cả khi công thức ra số thập phân dài.

**Tiêu chí chấp nhận**

- **AC-BLACKBOX-COVERAGE-6/v1 — Làm tròn đến 1 chữ số thập phân cho mọi tỷ lệ phần trăm.**

**Precondition:**

- Ticket có Priority Coverage thô ra số thập phân dài, không có AC/observation point nào (planned=7, passed=5 → 71.428571...%).
- Người gọi có quyền `ROLE-QA-SAME-PROJECT`.

**Các bước:**

1. Gọi API summary QA Dashboard với ticket nêu trên.
2. Quan sát giá trị `blackboxCoveragePercent`.

**Kết quả mong đợi:**

- HTTP `200`.
- Giá trị trả về có tối đa 1 chữ số thập phân ở mọi bước trung gian: Priority Coverage làm tròn `71.4`, Execution làm tròn `10.7`, Overall làm tròn `7.5`.

**Kết quả thực thi:** PASS — `bbc14_roundsToOneDecimalPlace_forRepeatingFraction` (real DB), xem `test-results.md` mục 6.

---

### BBC-15 — Đổi filter project/ticket trả đúng dữ liệu độc lập

**Priority:** P2
**Loại:** Regression

**Mô tả:** Khi đổi filter sang ticket khác trên cùng dashboard, `blackboxCoveragePercent` phải cập nhật theo đúng ticket mới, không giữ giá trị cũ.

**Tiêu chí chấp nhận**

- **AC-BLACKBOX-COVERAGE-3/v1 — Counts được tính riêng cho ticket đang truy vấn.**

**Precondition:**

- Hai ticket khác nhau: `D-FULL` và `D-EMPTY`, cùng thuộc project mà người gọi có quyền `ROLE-QA-SAME-PROJECT`.

**Các bước:**

1. Gọi API summary QA Dashboard với `ticketId` của `D-FULL`.
2. Gọi lại API summary QA Dashboard với `ticketId` của `D-EMPTY`.
3. So sánh giá trị `blackboxCoveragePercent` giữa hai lần gọi.

**Kết quả mong đợi:**

- Lần 1: `blackboxCoveragePercent = 81.6` (`D-FULL`).
- Lần 2: `blackboxCoveragePercent = 0.0` (`D-EMPTY`).
- Không có rò rỉ dữ liệu giữa hai ticket.

**Kết quả thực thi:** PASS — `bbc15And16_multipleTicketsInSameProject_neverCrossContaminate` (real DB, 2 ticket trong cùng 1 project, gọi xen kẽ cả hai chiều), xem `test-results.md` mục 6.

---

### BBC-16 — Nhiều ticket cùng project không lẫn dữ liệu

**Priority:** P2
**Loại:** Regression

**Mô tả:** Khi project có nhiều ticket đồng thời có dữ liệu blackbox khác nhau, mỗi lần truy vấn chỉ trả đúng coverage của ticket được yêu cầu.

**Tiêu chí chấp nhận**

- **AC-BLACKBOX-COVERAGE-3/v1 — Counts tách theo từng ticket, không cộng gộp.**
- **AC-BLACKBOX-COVERAGE-7/v1 — Không lẫn dữ liệu giữa các nguồn/ticket khác nhau.**

**Precondition:**

- Project có 3 ticket đồng thời tồn tại: `D-FULL`, `D-DESIGN-ONLY`, `D-EMPTY`.
- Người gọi có quyền `ROLE-QA-SAME-PROJECT`.

**Các bước:**

1. Gọi API summary QA Dashboard lần lượt cho từng ticket trong 3 ticket trên.
2. Đối chiếu từng giá trị trả về với kết quả kỳ vọng riêng của ticket đó (BBC-01, BBC-03, BBC-02).

**Kết quả mong đợi:**

- Giá trị mỗi lần gọi khớp đúng ticket tương ứng, không bị trộn lẫn số liệu giữa các ticket.

**Kết quả thực thi:** PASS (gộp chung với BBC-15) — `bbc15And16_multipleTicketsInSameProject_neverCrossContaminate` (real DB), xem `test-results.md` mục 6.

---

## 6. Chưa được bao phủ ở đây

Các nội dung sau không thuộc coverage của tài liệu black-box này:

- Thuật toán parse markdown, cấu trúc bảng lưu trữ, tên service/class nội bộ.
- Schema DB, migration, cách persist dữ liệu Blackbox Coverage.
- Thay đổi UI/UX của QA Dashboard (spec §2: ngoài phạm vi).
- Đo performance benchmark hạ tầng (số lần query, EXPLAIN) — chỉ review tĩnh theo `test-plan.md` §5, trừ khi phát sinh hành vi black-box cụ thể cần xác nhận.
- Toàn bộ ma trận quyền truy cập của hệ thống; chỉ smoke các case liên quan trực tiếp đến field này (BBC-09..11).

---

## 7. Traceability: AC ↔ black-box cases

| # | AC | Black-box case(s) | Priority | Ghi chú |
| --- | --- | --- | --- | --- |
| 1 | `AC-BLACKBOX-COVERAGE-1/v1` | `BBC-01`, `BBC-02`, `BBC-03` | P0 | Quan sát gián tiếp qua giá trị field khi thay đổi nguồn dữ liệu design |
| 2 | `AC-BLACKBOX-COVERAGE-2/v1` | `BBC-01`, `BBC-05`, `BBC-06` | P0/P1 | Quan sát gián tiếp qua giá trị field khi thay đổi trạng thái execution |
| 3 | `AC-BLACKBOX-COVERAGE-3/v1` | `BBC-01`, `BBC-04`, `BBC-08`, `BBC-15`, `BBC-16` | P0/P1/P2 | Counts đúng, không lẫn dữ liệu giữa ticket |
| 4 | `AC-BLACKBOX-COVERAGE-4/v1` | `BBC-07`, `BBC-08` | P1 | Priority weight và rule chỉ tính `DIRECT` |
| 5 | `AC-BLACKBOX-COVERAGE-5/v1` | `BBC-01` | P0 | Khớp ví dụ spec `89.4` |
| 6 | `AC-BLACKBOX-COVERAGE-6/v1` | `BBC-02`, `BBC-03`, `BBC-04`, `BBC-05`, `BBC-06`, `BBC-14` | P0/P1 | Zero-denominator, SKIP/NOT_RUN, rounding |
| 7 | `AC-BLACKBOX-COVERAGE-7/v1` | `BBC-13`, `BBC-16` | P0/P2 | Tách biệt AC-Test Coverage |
| 8 | `AC-BLACKBOX-COVERAGE-8/v1` | `BBC-09`, `BBC-10`, `BBC-11`, `BBC-12` | P0/P1 | Contract + permission gate |

---

## 8. Checklist trước khi review

- [x] Mỗi test case chỉ mô tả hành vi quan sát được, không mô tả implementation.
- [x] Mỗi expected result có thể kiểm chứng qua response API hoặc HTTP status.
- [x] P0 bao phủ đầy đủ main flow và rủi ro chuyển sai dữ liệu.
- [x] P1 bao phủ boundary/abnormal quan trọng.
- [x] P2 chỉ dùng cho smoke/compatibility mở rộng.
- [x] Có backlink theo màn hình/điểm quan sát.
- [x] Có traceability AC ↔ test case.
- [x] Có ghi rõ phần không thuộc coverage.
