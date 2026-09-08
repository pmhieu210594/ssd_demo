# Gói đặc tả — BLACKBOX-COVERAGE

- **Ticket:** BLACKBOX-COVERAGE
- **Trạng thái:** Draft
- **Tạo ngày:** 2026-09-04 00:00
- **Cập nhật ngày:** 2026-09-07 00:00

> Mục tiêu của tài liệu này là chuẩn hóa cách tính `Blackbox Coverage` để đọc dễ, áp dụng thống nhất, và không lẫn với các KPI khác.

---

## 1. Mục tiêu

Chuẩn hóa 3 chỉ số:

- `Blackbox Design Coverage`: đo lúc viết tài liệu test
- `Blackbox Execution Coverage`: đo sau khi chạy test
- `Blackbox Overall Coverage`: chỉ số tổng hợp để theo dõi nội bộ / dashboard

Nguyên tắc chính:

- Tách rõ coverage của tài liệu với coverage của execution.
- Chỉ dùng dữ liệu quan sát được từ `spec-pack.md`, `blackbox-testcases.md`, `test-results.md`.
- `Overall Coverage` chỉ để theo dõi, không thay thế release gate.
- Làm nguồn rule thống nhất cho implementation backend của scanner, persistence, và QA Dashboard field `blackboxCoveragePercent`.

---

## 2. Phạm vi

### Trong phạm vi

- Định nghĩa 3 chỉ số `Design`, `Execution`, `Overall`
- Định nghĩa các chỉ số thành phần: `AC Coverage`, `Observation Coverage`, `Priority Coverage`
- Định nghĩa quy tắc cho `Priority`, `mẫu số = 0`, `SKIP`, `NOT_RUN`, và làm tròn số
- Áp dụng công thức này cho backend của QA Dashboard để tính field `blackboxCoveragePercent`
- Lưu dữ liệu Blackbox Coverage vào storage/persistence riêng nếu cần để phục vụ parse và query ổn định

### Ngoài phạm vi

- Không đổi UI/UX hoặc vị trí hiển thị của field `blackboxCoveragePercent` trên QA Dashboard
- Không đổi tên field, không đổi kiểu dữ liệu, không tạo field API mới chỉ để show riêng `Design` / `Execution` / `Priority`
- Không thay đổi schema / logic của AC-Test Coverage hiện hữu; nếu cần lưu dữ liệu structured cho Blackbox Coverage thì dùng storage additive riêng
- Không thay đổi cách tính `AC-Test Coverage`
- Không gộp `observation point` với `test viewpoint`

---

## 3. Thuật ngữ

| Thuật ngữ | Nghĩa |
| --- | --- |
| AC | Acceptance Criteria trong `spec-pack.md` |
| Observation point | Một mục trong `blackbox-testcases.md` mục `3` |
| Black-box case | Một case trong `blackbox-testcases.md` mục `5` |
| Test viewpoint | Góc nhìn kiểm thử như `Normal`, `Error`, `Boundary`, `Permission`... |
| Design Coverage | Coverage của tài liệu test, chưa cần chạy |
| Execution Coverage | Coverage dựa trên kết quả chạy test thực tế |
| Overall Coverage | Chỉ số tổng hợp giữa Design và Execution |

Ghi chú:

- `Observation point` trả lời câu hỏi: “đang quan sát ở đâu / ở bước nào?”
- `Test viewpoint` trả lời câu hỏi: “đang test theo góc nhìn nào?”

---

## 4. Nguồn dữ liệu chuẩn

| Dữ liệu | Nguồn |
| --- | --- |
| Tổng AC trong scope | `spec-pack.md` |
| Observation point | `blackbox-testcases.md` mục `3` |
| Black-box case + Priority | `blackbox-testcases.md` mục `5` |
| Mapping `AC ↔ case` | `blackbox-testcases.md` mục `7` |
| Kết quả execution | `test-results.md` |

Ghi chú áp dụng runtime:

- Các tài liệu trên là **nguồn nghiệp vụ chuẩn** để parse/tính coverage.
- Backend có thể persist dữ liệu đã parse sang bảng/record riêng để phục vụ scanner và QA Dashboard, miễn là không làm thay đổi rule nghiệp vụ trong tài liệu này.
- Dữ liệu runtime cho Blackbox Coverage phải tách khỏi storage của `AC-Test Coverage`.

---

## 5. Quy tắc tính

### 5.1. Design Coverage

`AC Coverage (Design) = Số AC có ít nhất 1 case map / Tổng AC × 100`

`Observation Coverage (Design) = Số observation point có case bao phủ trực tiếp / Tổng observation point × 100`

`Blackbox Design Coverage = 0.75 × AC Coverage (Design) + 0.25 × Observation Coverage (Design)`

### 5.2. Execution Coverage

`AC Coverage (Execution) = Số AC có ít nhất 1 case map và toàn bộ case map cho AC đó đều PASS / Tổng AC × 100`

`Observation Coverage (Execution) = Số observation point có ít nhất 1 case bao phủ trực tiếp PASS / Tổng observation point × 100`

`Priority Coverage = Tổng điểm case PASS / Tổng điểm case planned × 100`

`Blackbox Execution Coverage = 0.60 × AC Coverage (Execution) + 0.25 × Observation Coverage (Execution) + 0.15 × Priority Coverage`

### 5.3. Overall Coverage

`Blackbox Overall Coverage = 0.30 × Design Coverage + 0.70 × Execution Coverage`

---

## 6. Quy tắc chi tiết

### 6.1. Priority weight

| Priority | Weight |
| --- | --- |
| P0 | 5 |
| P1 | 3 |
| P2 | 1 |

Ghi chú:

- `Priority` dùng để tính `Priority Coverage` và để theo dõi release gate như `P0 PASS rate`.
- `Priority` không dùng để suy ra khái niệm riêng như "case bắt buộc" trong `AC Coverage (Execution)`.

### 6.2. Mẫu số bằng 0

Nếu mẫu số của một chỉ số thành phần bằng `0`:

- Chỉ số đó = `0%`
- Không chia lại trọng số cho các thành phần còn lại
- Nên ghi chú rõ lý do, ví dụ:
  - `0% (no AC in scope)`
  - `0% (no observation points defined)`
  - `0% (no case planned)`

### 6.3. `SKIP` và `NOT_RUN`

| Trạng thái | Ý nghĩa | Có tính là PASS không? |
| --- | --- | --- |
| `PASS` | Đã chạy và đạt | Có |
| `FAIL` | Đã chạy nhưng không đạt | Không |
| `SKIP` | Chủ động bỏ qua, có lý do | Không |
| `NOT_RUN` | Chưa chạy / không có điều kiện chạy | Không |

Ghi chú:

- `SKIP` khác `NOT_RUN` về ý nghĩa quản trị.
- Trong công thức coverage execution, cả hai đều không được tính là `PASS`.

### 6.4. Làm tròn

- Làm tròn đến `1` chữ số thập phân cho mọi tỷ lệ phần trăm

---

## 7. Rule sử dụng chỉ số

| Chỉ số | Mục đích |
| --- | --- |
| `Design Coverage` | Đánh giá chất lượng chuẩn bị test |
| `Execution Coverage` | Đánh giá bằng chứng chạy test thực tế |
| `Overall Coverage` | Theo dõi nội bộ / dashboard |

Rule bắt buộc:

- `Overall Coverage` không được dùng một mình để quyết định release
- Release gate vẫn phải kiểm riêng:
  - `P0 PASS rate = 100%`
  - `AC Coverage (Execution)` đạt mức yêu cầu của release

---

## 8. Ví dụ ngắn

### Design

- `10 AC`, có `8 AC` được map case → `AC Coverage (Design) = 80%`
- `5 observation point`, có `5` điểm được cover trực tiếp → `Observation Coverage (Design) = 100%`
- `Blackbox Design Coverage = 0.75 × 80 + 0.25 × 100 = 85%`

### Execution

- `10 AC`, có `9 AC` passed → `AC Coverage (Execution) = 90%`
- `5 observation point`, có `5` điểm passed → `Observation Coverage (Execution) = 100%`
- Planned score = `22`, passed score = `18` → `Priority Coverage = 81.8%`
- `Blackbox Execution Coverage = 0.60 × 90 + 0.25 × 100 + 0.15 × 81.8 = 91.3%`

### Overall

- `Design = 85%`
- `Execution = 91.3%`
- `Overall = 0.30 × 85 + 0.70 × 91.3 = 89.4%`

---

## 9. Kết luận áp dụng

- Dùng `Design Coverage` khi muốn biết tài liệu test đã bao phủ đủ chưa
- Dùng `Execution Coverage` khi muốn biết test đã chạy và pass đến đâu
- Dùng `Overall Coverage` để theo dõi nhanh trên báo cáo nội bộ
- Khi cần sign-off hoặc release, không dùng `Overall` thay cho `P0` và execution gate

---

## 10. Áp dụng vào QA Dashboard — field `blackboxCoveragePercent`

**Mục tiêu:** field `blackboxCoveragePercent` trên màn hình QA Dashboard lấy giá trị từ `Blackbox Overall Coverage` (mục 5.3), thay cho cách tính "% test viewpoint cố định" hoặc placeholder `0.0`.

**Công thức áp dụng cho field:**

`blackboxCoveragePercent = Blackbox Overall Coverage = 0.30 × Blackbox Design Coverage + 0.70 × Blackbox Execution Coverage`

Trong đó `Design Coverage` và `Execution Coverage` tính theo mục 5.1/5.2, dữ liệu lấy theo mục 4 (Nguồn dữ liệu chuẩn), tính riêng cho từng ticket đang hiển thị trên dashboard.

**Phạm vi backend áp dụng:**

- `blackbox-testcases.md` cung cấp dữ liệu design-side: observation point, case, priority, mapping `AC ↔ case`.
- `test-results.md` cung cấp dữ liệu execution-side: `PASS`, `FAIL`, `SKIP`, `NOT_RUN`.
- Scanner/parser backend có thể parse hai nguồn trên và persist sang storage Blackbox Coverage riêng.
- QA Dashboard backend query dữ liệu Blackbox Coverage đã parse để tính `blackboxCoveragePercent`.

**Phạm vi thay đổi trên màn hình:**

- Chỉ thay đổi cách tính giá trị đứng sau field `blackboxCoveragePercent`.
- Không đổi tên field, không đổi kiểu dữ liệu, không đổi vị trí/hình thức hiển thị trên màn hình.
- Không dùng `test viewpoint` (8 góc nhìn cố định) làm nguồn cho `Observation Coverage` — dùng `observation point` theo `blackbox-testcases.md` mục 3 (hai khái niệm khác nhau, xem mục 3).

**Quy tắc khi thiếu dữ liệu (áp dụng mục 6.2):**

| Trường hợp | Kết quả trên field `blackboxCoveragePercent` |
| --- | --- |
| Ticket chưa có `blackbox-testcases.md` hoặc chưa parse được dữ liệu | `Design Coverage = 0%`, `Execution Coverage = 0%` → `blackboxCoveragePercent = 0%` |
| Ticket đã có Design nhưng chưa chạy test nào | `Execution Coverage = 0%` → `blackboxCoveragePercent` bị kéo thấp theo trọng số `0.70` |
| Mọi trường hợp mẫu số = 0 | Trả `0.0`, không trả `null`/`NaN` (giữ nguyên hành vi zero-denominator hiện có của QA Dashboard) |

**Ví dụ:** dùng lại số liệu ở mục 8 → `Design = 85%`, `Execution = 91.3%` → `blackboxCoveragePercent = 89.4`.

**Rule không đổi:** giá trị hiển thị ở field `blackboxCoveragePercent` (Overall) không được dùng để quyết định release; release gate vẫn kiểm riêng theo mục 7.

**Ràng buộc implementation không đổi:**

- Không được fallback sang `test viewpoint` cố định để thay cho `observation point`.
- Không được trộn dữ liệu Blackbox Coverage với dữ liệu AC-Test Coverage để “tận dụng sẵn” bảng cũ.
- Nếu backend cần schema mới để lưu dữ liệu Blackbox Coverage đã parse, schema đó phải là additive và tách biệt.
