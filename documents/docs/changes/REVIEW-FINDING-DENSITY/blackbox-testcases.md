# Review Finding Density — Black-box test cases

- **Ticket:** REVIEW-FINDING-DENSITY
- **Trạng thái:** Draft
- **Tạo ngày:** 2026-09-15 16:48:01
- **Cập nhật ngày:** 2026-09-15 16:48:01
- **Phạm vi:** Finding eligibility/classification, changed-lines & density calculation (bao gồm N/A rule, aggregation nhiều PR, rounding), và regression của luồng đồng bộ PR hiện có sau khi thêm bước finding classification. Không bao gồm chi tiết GraphQL client, migration SQL, hay ma trận quyền đầy đủ.

> Nguồn tham chiếu chính: `docs/changes/REVIEW-FINDING-DENSITY/spec-pack.md`.

---

## 1. Nguyên tắc black-box

Các test case dưới đây chỉ kiểm thử hành vi quan sát được từ bên ngoài hệ thống:

- Giá trị hiển thị trên Ticket Detail (`X findings/KLOC` hoặc `N/A`) và breakdown `OPEN`/`RESOLVED`.
- Response JSON của `GET /api/v1/pm/dashboard/tickets/{ticketId}/detail` (field `reviewFindingDensity.{density, unit, totalFindings, totalChangedLines, breakdown.{open, resolved}}`).
- Kết quả sau khi hệ thống đồng bộ PR (webhook/poll) — quan sát qua giá trị density thay đổi hay không, không quan sát qua bảng DB nội bộ.

Không giả định các chi tiết implementation như: tên GraphQL query, cấu trúc bảng `tbl_fact_finding`/`tbl_fact_pull_request_changed_file`, thuật toán dedupe nội bộ, hay tên class Java xử lý.

## 2. Phân tầng ưu tiên

| Priority | Ý nghĩa                                                               | Kỳ vọng                                                   |
| -------- | --------------------------------------------------------------------- | --------------------------------------------------------- |
| P0       | Hành vi bắt buộc để tránh chuyển sai dữ liệu hoặc phá vỡ luồng chính  | Phải pass trước khi release                               |
| P1       | Boundary/abnormal quan trọng, dễ tạo hồi quy hoặc hiểu nhầm nghiệp vụ | Nên pass trong cùng vòng kiểm thử                         |
| P2       | Regression/compatibility mở rộng, không tăng coverage vô hạn          | Chạy khi có thời gian hoặc khi vùng liên quan bị thay đổi |

## 3. Backlink theo màn hình / điểm quan sát

### Ticket Detail UI / API (`GET /tickets/{ticketId}/detail`)

- Bao phủ trực tiếp: `BB-RFD-DENSITY-01`, `BB-RFD-DENSITY-02`, `BB-RFD-DENSITY-03`, `BB-RFD-DENSITY-04`, `BB-RFD-DENSITY-05`.
- Bao phủ liên quan: `BB-RFD-CLASSIFY-01`, `BB-RFD-CLASSIFY-02`, `BB-RFD-CLASSIFY-03`, `BB-RFD-CLASSIFY-04` (quan sát kết quả density sau phân loại).

### Ingestion sync outcome (đồng bộ PR nền)

- Bao phủ trực tiếp: `BB-RFD-CLASSIFY-01`, `BB-RFD-CLASSIFY-02`, `BB-RFD-CLASSIFY-03`, `BB-RFD-CLASSIFY-04`, `BB-RFD-REG-01`.
- Bao phủ liên quan: `BB-RFD-DENSITY-01`, `BB-RFD-DENSITY-02` (density chỉ đúng nếu classification chạy đúng trước đó).

### Bước nghiệp vụ tiếp theo / kết quả xử lý

- Bao phủ trực tiếp: `BB-RFD-REG-01`.
- Bao phủ liên quan: `BB-RFD-DENSITY-01`..`05` để đối chiếu dữ liệu hiển thị với dữ liệu response API.

## 4. Bộ dữ liệu tham chiếu

Chi tiết dữ liệu precondition, input và expected result nằm trong `test-data.md`.

Các ký hiệu chính:

- `TCK-1`/`TCK-2`/`TCK-3`: ticket mẫu (1 PR, nhiều PR, không PR).
- `PR-A`..`PR-E`: PR mẫu theo từng review state/changed-lines/finding-count.
- `THREAD-LGTM-1`: thread hợp lệ nhưng nội dung chỉ là câu hỏi/LGTM.
- `ND-*`: normal data; `ED-*`: error data; `BD-*`: boundary data (xem `test-data.md`).

---

## 5. Các black-box test case

### BB-RFD-CLASSIFY-01 — Nhiều thread hợp lệ, mỗi thread tính đúng 1 finding

**Priority:** P0
**Loại:** Positive

**Mô tả:** Khi một review có state hợp lệ với nhiều review thread riêng biệt, mỗi thread phải được tính là một finding và phản ánh đúng vào density hiển thị sau khi đồng bộ.

**Tiêu chí chấp nhận**

- **[AC-FINDING-CLASSIFY-1]/v1 — Review state hợp lệ với ≥1 thread → mỗi thread thành 1 finding, liên kết đúng review/comment gốc.**
- **[AC-FINDING-CLASSIFY-4]/v1 — Review state hợp lệ có nhiều thread → mỗi thread được tính đúng một finding.**

**Precondition:**

- Ticket `TCK-1` liên kết PR `PR-A`.
- `PR-A` có review `APPROVED` với 3 thread hợp lệ (dữ liệu `ND-3`), 500 changed lines.

**Các bước:**

1. Trigger đồng bộ PR `PR-A` (webhook/poll).
2. Gọi `GET /tickets/TCK-1/detail`.
3. Quan sát field `reviewFindingDensity.totalFindings` và `density`.

**Kết quả mong đợi:**

- `totalFindings = 3`.
- `density = 3/500×1000 = 6.0` findings/KLOC.
- Không có finding trùng lặp hoặc thiếu so với số thread thực tế.
- Không có thay đổi ngoài phạm vi ticket `TCK-1`.

---

### BB-RFD-CLASSIFY-02 — Review state không hợp lệ không tạo finding

**Priority:** P0
**Loại:** Negative

**Mô tả:** Review có state `UNKNOWN`, trống hoặc không nhận diện được không được tính vào finding/density.

**Tiêu chí chấp nhận**

- **[AC-FINDING-CLASSIFY-2]/v1 — Review state `UNKNOWN`/trống/không nhận diện → không finding nào được tạo từ review đó.**

**Precondition:**

- Ticket liên kết PR `PR-D` (dữ liệu `ED-1`), review state `UNKNOWN`, có ≥1 thread nội dung bất kỳ.
- `PR-D` có 200 changed lines.

**Các bước:**

1. Trigger đồng bộ PR `PR-D`.
2. Gọi `GET /tickets/{ticketId}/detail` cho ticket chứa `PR-D`.
3. Quan sát `reviewFindingDensity.totalFindings` và `density`.

**Kết quả mong đợi:**

- `totalFindings` không tăng do review của `PR-D` (= 0 nếu đây là PR duy nhất của ticket).
- `density = 0` findings/KLOC (khác `N/A` vì changed lines > 0).
- Không có lỗi hệ thống hay Ticket Detail bị lỗi toàn bộ.

---

### BB-RFD-CLASSIFY-03 — Thread chỉ là câu hỏi/LGTM vẫn tính là finding (metadata-only)

**Priority:** P1
**Loại:** Positive / Boundary nghiệp vụ

**Mô tả:** Rule phân loại chỉ dựa trên metadata (review state + thread tồn tại), không phân tích nội dung câu chữ — thread nội dung LGTM/câu hỏi dưới review state hợp lệ vẫn được tính là finding.

**Tiêu chí chấp nhận**

- **[AC-FINDING-CLASSIFY-3]/v1 — Thread thuộc review state hợp lệ mà nội dung chỉ là câu hỏi/LGTM/ACK vẫn được tính là finding.**

**Precondition:**

- PR có review `CHANGES_REQUESTED`, thread `THREAD-LGTM-1` với nội dung 2 comment chỉ là hỏi đáp/LGTM (dữ liệu `ND-4`).

**Các bước:**

1. Trigger đồng bộ PR chứa `THREAD-LGTM-1`.
2. Gọi `GET /tickets/{ticketId}/detail`.
3. Quan sát `totalFindings`.

**Kết quả mong đợi:**

- `THREAD-LGTM-1` được tính là 1 finding hợp lệ, `totalFindings` tăng tương ứng.
- Không có hành vi lọc bỏ thread này dựa trên nội dung câu chữ.

---

### BB-RFD-CLASSIFY-04 — Nhiều comment cùng thread gộp thành một finding

**Priority:** P1
**Loại:** Positive / Boundary nghiệp vụ

**Mô tả:** Nhiều comment thuộc cùng một thread phải dedupe thành đúng một finding, không nhân đôi density.

**Tiêu chí chấp nhận**

- **[AC-FINDING-CLASSIFY-5]/v1 — Nhiều comment cùng một thread được gộp thành một finding duy nhất (dedupe theo thread identity).**

**Precondition:**

- PR `PR-B` có review `CHANGES_REQUESTED`, 1 thread với 4 comment nối tiếp (dữ liệu `ND-5`), 500 changed lines.

**Các bước:**

1. Trigger đồng bộ PR `PR-B`.
2. Gọi `GET /tickets/{ticketId}/detail` cho ticket chứa `PR-B`.
3. Quan sát `totalFindings`.

**Kết quả mong đợi:**

- `totalFindings = 1` cho thread này (không phải 4).
- `density` phản ánh đúng 1 finding / 500 changed lines, không bị nhân đôi do số lượng comment.

---

### BB-RFD-DENSITY-01 — Density 1 PR với N finding, M changed lines (M>0)

**Priority:** P0
**Loại:** Positive

**Mô tả:** Với một PR có N finding hợp lệ và M changed lines (M>0), density hiển thị đúng công thức N/M×1000.

**Tiêu chí chấp nhận**

- **[AC-DENSITY-CALC-1]/v1 — N finding hợp lệ, M changed lines (M>0) → density = N/M×1000.**

**Precondition:**

- Ticket `TCK-1` liên kết duy nhất PR `PR-A`: 3 finding hợp lệ, 500 changed lines (dữ liệu `ND-1`).

**Các bước:**

1. Đảm bảo dữ liệu finding/changed-lines của `PR-A` đã được đồng bộ.
2. Gọi `GET /tickets/TCK-1/detail`.
3. Quan sát `reviewFindingDensity.density`, `totalFindings`, `totalChangedLines`.

**Kết quả mong đợi:**

- `totalFindings = 3`, `totalChangedLines = 500`.
- `density = 6.0` findings/KLOC.
- `unit = "findings/KLOC"`.

---

### BB-RFD-DENSITY-02 — Aggregation nhiều PR: tổng/tổng, không trung bình per-PR

**Priority:** P0
**Loại:** Positive / Boundary nghiệp vụ

**Mô tả:** Ticket có nhiều PR liên kết phải tính density theo tổng finding/tổng changed lines của tất cả PR, không lấy trung bình cộng của density từng PR.

**Tiêu chí chấp nhận**

- **[AC-DENSITY-CALC-2]/v1 — Ticket nhiều PR liên kết → density = tổng finding / tổng changed lines × 1000, không lấy trung bình per-PR.**

**Precondition:**

- Ticket `TCK-2` liên kết `PR-B` (1 finding/100 changed lines) và `PR-C` (1 finding/900 changed lines) — dữ liệu `ND-2b`.

**Các bước:**

1. Đảm bảo cả `PR-B` và `PR-C` đã đồng bộ đầy đủ finding/changed-lines.
2. Gọi `GET /tickets/TCK-2/detail`.
3. Quan sát `reviewFindingDensity.density`.

**Kết quả mong đợi:**

- `totalFindings = 2`, `totalChangedLines = 1000`.
- `density = 2.0` findings/KLOC (đúng công thức tổng/tổng).
- **Không** ra kết quả trung bình per-PR sai `(10 + 1.1) / 2 ≈ 5.6` findings/KLOC.

---

### BB-RFD-DENSITY-03 — Tổng changed lines = 0 → hiển thị N/A

**Priority:** P0
**Loại:** Negative / Boundary

**Mô tả:** Khi tổng changed lines của các PR liên kết bằng 0 (kể cả khi ticket chưa có PR liên kết), hệ thống phải hiển thị `N/A`, không phải `0`.

**Tiêu chí chấp nhận**

- **[AC-DENSITY-CALC-3]/v1 — Tổng changed lines = 0 → hệ thống trả `N/A`, không trả `0`.**

**Precondition:**

- Biến thể A: Ticket liên kết `PR-E` (chỉ đổi file binary, changed lines = 0) — dữ liệu `BD-1`.
- Biến thể B: Ticket `TCK-3` chưa có PR liên kết nào — dữ liệu `BD-2`.

**Các bước:**

1. Gọi `GET /tickets/{ticketId}/detail` cho từng biến thể (ticket có `PR-E`, và `TCK-3`).
2. Quan sát field `reviewFindingDensity.density` trong response JSON và giá trị hiển thị trên UI.

**Kết quả mong đợi:**

- Response JSON: `density = null`.
- UI hiển thị `"N/A"`, không hiển thị `"0"` hay `"0 findings/KLOC"`.
- Đúng cho cả 2 biến thể (có PR nhưng 0 changed lines, và không có PR liên kết).

---

### BB-RFD-DENSITY-04 — 0 finding với changed lines > 0 → hiển thị 0, không phải N/A

**Priority:** P1
**Loại:** Boundary

**Mô tả:** Phân biệt rõ "0 findings/KLOC hợp lệ" (khi changed lines > 0 nhưng không có finding) với trường hợp N/A.

**Tiêu chí chấp nhận**

- **[AC-DENSITY-CALC-4]/v1 — PR có 0 finding và changed lines > 0 → density = 0 findings/KLOC (phân biệt với N/A).**

**Precondition:**

- Ticket liên kết duy nhất `PR-C`: 0 finding, 500 changed lines (dữ liệu `BD-3`).

**Các bước:**

1. Gọi `GET /tickets/{ticketId}/detail` cho ticket chứa `PR-C`.
2. Quan sát `reviewFindingDensity.density`.

**Kết quả mong đợi:**

- `density = 0.0` findings/KLOC (giá trị số, không phải `null`).
- UI hiển thị `"0.0 findings/KLOC"`, không hiển thị `"N/A"`.

---

### BB-RFD-DENSITY-05 — Rounding HALF_UP đúng 1 chữ số thập phân

**Priority:** P1
**Loại:** Boundary

**Mô tả:** Giá trị density có nhiều hơn 1 chữ số thập phân phải được làm tròn đúng 1 chữ số theo `RoundingMode.HALF_UP`.

**Tiêu chí chấp nhận**

- **[AC-DENSITY-CALC-6]/v1 — Density có nhiều hơn 1 chữ số thập phân → làm tròn còn 1 chữ số theo HALF_UP (5.25→5.3; 5.24→5.2).**

**Precondition:**

- Dữ liệu tạo ra density thô = 5.25 (dữ liệu `BD-4`).
- Dữ liệu tạo ra density thô = 5.24 (dữ liệu `BD-5`).

**Các bước:**

1. Gọi `GET /tickets/{ticketId}/detail` cho ticket ứng với mỗi bộ dữ liệu.
2. Quan sát `reviewFindingDensity.density`.

**Kết quả mong đợi:**

- Với density thô 5.25 → hiển thị `5.3`.
- Với density thô 5.24 → hiển thị `5.2`.
- Không có trường hợp hiển thị nhiều hơn 1 chữ số thập phân.

---

### BB-RFD-REG-01 — Đồng bộ PR 2 lần liên tiếp không hồi quy hành vi cũ

**Priority:** P0
**Loại:** Regression

**Mô tả:** Sau khi thêm bước phân loại finding vào luồng đồng bộ PR, hành vi đồng bộ PR/commit/changed-file/review hiện có không được thay đổi, và dashboard/ticket row vẫn hiển thị đúng sau khi response `/detail` có thêm field `reviewFindingDensity`.

**Tiêu chí chấp nhận**

- **[AC-REG-1]/v1 — Bổ sung bước phân loại finding không làm thay đổi hành vi đồng bộ PR/commit/changed-file/review hiện có.**

**Precondition:**

- Ticket `TCK-1` liên kết `PR-A` với `head_sha = SHA-1`, đã đồng bộ lần 1 thành công.
- `PR-A` nhận thêm 1 commit mới, `head_sha` đổi thành `SHA-2`.

**Các bước:**

1. Trigger đồng bộ PR lần 1 (`SHA-1`) — xác nhận PR/commit/changed-file/review/comment được lưu đúng như hành vi hiện có (trước ticket này).
2. Trigger đồng bộ PR lần 2 sau khi có commit mới (`SHA-2`).
3. Gọi `GET /tickets/TCK-1/detail` và mở PM Dashboard (danh sách ticket) sau cả 2 lần đồng bộ.
4. Quan sát response `/detail`, dashboard list, và ticket row.

**Kết quả mong đợi:**

- Cả 2 lần đồng bộ không lỗi (không có lỗi FK hay lỗi hệ thống).
- Dữ liệu PR/commit/changed-file/review/comment hiển thị đúng như hành vi hiện có (không bị mất/trùng lặp).
- Response `/detail` có thêm field `reviewFindingDensity` nhưng các field cũ khác giữ nguyên giá trị/shape.
- Dashboard list và ticket row hiển thị bình thường, không có lỗi render hay KPI card bị vỡ.

---

## 6. Chưa được bao phủ ở đây

Các nội dung sau không thuộc coverage của tài liệu black-box này:

- Chi tiết GraphQL query (`reviewThreads`, `isResolved`) hay cấu trúc response GraphQL nội bộ.
- Tên bảng, schema DB (`tbl_fact_finding`, `tbl_fact_pull_request_changed_file`), migration SQL (`V519`), hay cách backfill `head_sha='legacy'` ở mức kỹ thuật.
- Vòng đời trạng thái finding chi tiết (`AC-STATUS-1`, `AC-STATUS-5`) và recalculation theo `head_sha` (`AC-RECALC-1`, `AC-RECALC-2`) — các AC này không có yêu cầu BB theo `test-plan.md` mục 1, chỉ verify qua UT/IT.
- Breakdown OPEN/RESOLVED chi tiết (`AC-DENSITY-CALC-5`) — không có yêu cầu BB theo test-plan, chỉ verify qua UT/IT.
- Ma trận quyền truy cập đầy đủ; chỉ có smoke PM/QA đọc `/detail` (không kiểm thử toàn bộ role khác vì spec không yêu cầu thay đổi phân quyền).
- Performance/rate-limit của GitHub GraphQL API — đây là rủi ro vận hành đã ghi nhận ở spec §9, không phải hành vi black-box cần assert.
- Test resilience khi GraphQL lỗi giữa chừng luồng sync (đã ghi nhận là quyết định thiết kế còn treo ở `test-plan.md` mục 8 #2).

---

## 7. Traceability: AC ↔ black-box cases

| #   | AC                          | Black-box case(s)                              | Priority | Ghi chú                                                                 |
| --- | ---------------------------- | ------------------------------------------------ | -------- | ------------------------------------------------------------------------ |
| 1   | `AC-FINDING-CLASSIFY-1/v1`   | `BB-RFD-CLASSIFY-01`                              | P0       | Quan sát qua `totalFindings` sau sync                                    |
| 2   | `AC-FINDING-CLASSIFY-2/v1`   | `BB-RFD-CLASSIFY-02`                              | P0       | Review state không hợp lệ không tạo finding                              |
| 3   | `AC-FINDING-CLASSIFY-3/v1`   | `BB-RFD-CLASSIFY-03`                              | P1       | Metadata-only, không NLP                                                 |
| 4   | `AC-FINDING-CLASSIFY-4/v1`   | `BB-RFD-CLASSIFY-01`                              | P0       | Nhiều thread → nhiều finding                                             |
| 5   | `AC-FINDING-CLASSIFY-5/v1`   | `BB-RFD-CLASSIFY-04`                              | P1       | Dedupe theo thread                                                       |
| 6   | `AC-DENSITY-CALC-1/v1`       | `BB-RFD-DENSITY-01`                               | P0       | Công thức N/M×1000 cho 1 PR                                              |
| 7   | `AC-DENSITY-CALC-2/v1`       | `BB-RFD-DENSITY-02`                               | P0       | Tổng/tổng, không trung bình per-PR                                       |
| 8   | `AC-DENSITY-CALC-3/v1`       | `BB-RFD-DENSITY-03`                               | P0       | N/A khi changed lines = 0, kể cả không có PR liên kết                    |
| 9   | `AC-DENSITY-CALC-4/v1`       | `BB-RFD-DENSITY-04`                               | P1       | Phân biệt 0 với N/A                                                      |
| 10  | `AC-DENSITY-CALC-5/v1`       | Không có BB case                                  | —        | Lý do: `test-plan.md` mục 1 không đánh dấu BB cho AC này — chỉ verify qua UT/IT (breakdown OPEN/RESOLVED) |
| 11  | `AC-DENSITY-CALC-6/v1`       | `BB-RFD-DENSITY-05`                               | P1       | Rounding HALF_UP                                                         |
| 12  | `AC-RECALC-1/v1`             | Không có BB case                                  | —        | Lý do: `test-plan.md` mục 1 không đánh dấu BB — chỉ verify qua UT/IT (đọc lại diff theo head_sha mới) |
| 13  | `AC-RECALC-2/v1`             | Không có BB case                                  | —        | Lý do: `test-plan.md` mục 1 không đánh dấu BB — chỉ verify qua UT/IT (finding cũ không tự resolve) |
| 14  | `AC-STATUS-1/v1`             | Không có BB case                                  | —        | Lý do: `test-plan.md` mục 1 không đánh dấu BB — chỉ verify qua UT/IT (chuyển OPEN → RESOLVED) |
| 15  | `AC-STATUS-5/v1`             | Không có BB case                                  | —        | Lý do: `test-plan.md` mục 1 không đánh dấu BB — chỉ verify qua UT (giữ nguyên RESOLVED) |
| 16  | `AC-REG-1/v1`                | `BB-RFD-REG-01`                                   | P0       | Không hồi quy luồng đồng bộ PR hiện có                                   |

---

## 8. Checklist trước khi review

- [x] Mỗi test case chỉ mô tả hành vi quan sát được, không mô tả implementation.
- [x] Mỗi expected result có thể kiểm chứng qua UI, contract, response, log hoặc test harness hiện có.
- [x] P0 bao phủ đầy đủ main flow và rủi ro chuyển sai dữ liệu.
- [x] P1 bao phủ boundary/abnormal quan trọng.
- [x] P2 chỉ dùng cho smoke/compatibility mở rộng (ticket này chưa cần case P2 riêng — mọi rủi ro chuyển sai dữ liệu được xếp P0/P1).
- [x] Có backlink theo màn hình/điểm quan sát.
- [x] Có traceability AC ↔ test case, bao gồm cả AC không có BB case kèm lý do.
- [x] Có ghi rõ phần không thuộc coverage.
