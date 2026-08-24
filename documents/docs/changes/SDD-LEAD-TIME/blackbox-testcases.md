# Spec-pack created time / Report updated time / Duration — Black-box test cases

- **Ticket:** SDD-LEAD-TIME
- **Trạng thái:** Draft
- **Tạo ngày:** 2026-08-21 15:30:00
- **Phạm vi:** Hiển thị spec-pack created time, report.md updated time, và duration tính toán trong Ticket Detail drawer của PM Dashboard.

> Nguồn tham chiếu chính: `docs/changes/SDD-LEAD-TIME/spec-pack.md`.

---

## 1. Nguyên tắc black-box

Các test case dưới đây chỉ kiểm thử hành vi quan sát được từ bên ngoài hệ thống: nội dung hiển thị trên Ticket Detail drawer, giá trị trả về từ API ticket-detail hiện có, và kết quả sau khi Artifact Scanner scan một ticket. Không giả định implementation nội bộ (tên hàm, cấu trúc parser, schema DB).

## 2. Phân tầng ưu tiên

| Priority | Ý nghĩa | Kỳ vọng |
| -------- | --------------------------------------------------------------------- | ------------------------- |
| P0       | Hành vi bắt buộc để tránh hiển thị sai timestamp hoặc phá vỡ luồng chính | Phải pass trước khi release |
| P1       | Boundary/abnormal quan trọng (file thiếu, date malformed, order sai)     | Nên pass trong cùng vòng kiểm thử |
| P2       | Regression/compatibility mở rộng (ticket cũ, i18n locale khác)          | Chạy khi có thời gian |

## 3. Backlink theo màn hình / điểm quan sát

### PM Dashboard — Ticket Detail drawer

- Bao phủ trực tiếp: `TC-SDD-LEAD-TIME-1`, `TC-SDD-LEAD-TIME-2`.
- Bao phủ liên quan: `TC-SDD-LEAD-TIME-3`, `TC-SDD-LEAD-TIME-4`.

### Artifact Scanner scan kết quả (quan sát qua DB/API, không qua UI trực tiếp)

- Bao phủ trực tiếp: `TC-SDD-LEAD-TIME-5`, `TC-SDD-LEAD-TIME-6`, `TC-SDD-LEAD-TIME-8`.
- Bao phủ liên quan: `TC-SDD-LEAD-TIME-1`, `TC-SDD-LEAD-TIME-2`.

### PM Dashboard — Ticket list (ordering, regression)

- Bao phủ trực tiếp: `TC-SDD-LEAD-TIME-7`.
- Bao phủ liên quan: `TC-SDD-LEAD-TIME-1`.

## 4. Bộ dữ liệu tham chiếu

Chi tiết dữ liệu precondition, input và expected result nằm trong `docs/changes/SDD-LEAD-TIME/test-data.md`.

Các ký hiệu chính:

- `ND-1`/`ND-2`/`ND-3`: dữ liệu hợp lệ (đầy đủ datetime, bare date).
- `ED-1`/`ED-2`/`ED-3`: dữ liệu lỗi (malformed, file thiếu, whitespace).
- `BD-1`..`BD-4`: dữ liệu biên (độ dài date, thứ tự timestamp, số file có mặt).

---

## 5. Các black-box test case

### TC-SDD-LEAD-TIME-1 — Cả 2 file hợp lệ, hiển thị đầy đủ 3 field

**Priority:** P0
**Loại:** Positive

**Mô tả:** Xác nhận khi cả `spec-pack.md` và `report.md` có header date hợp lệ, Ticket Detail drawer hiển thị đúng created time, updated time, và duration.

**Tiêu chí chấp nhận**

- **AC-SDD-LEAD-TIME-1/v1 — `started_at` populated từ `spec-pack.md`.**
- **AC-SDD-LEAD-TIME-2/v1 — `completed_at` populated từ `report.md`.**
- **AC-SDD-LEAD-TIME-7/v1 — Hiển thị i18n-labeled, không hardcode text.**
- **AC-SDD-LEAD-TIME-9/v1 — Duration hiển thị đúng cạnh 2 timestamp.**

**Precondition:**

- Ticket đã có `spec-pack.md` (dữ liệu `ND-1`) và `report.md` (dữ liệu `ND-2`) trong evidence folder.
- Artifact Scanner đã chạy scan cho ticket này ít nhất 1 lần sau khi 2 file được thêm.

**Các bước:**

1. Mở PM Dashboard.
2. Chọn ticket tương ứng, mở Ticket Detail drawer.
3. Quan sát khu vực Ticket Information.
4. Đối chiếu giá trị created time/updated time với nội dung file gốc.
5. Quan sát dòng duration.

**Kết quả mong đợi:**

- Created time hiển thị đúng giá trị từ `spec-pack.md` (`2026-08-21 09:30:00`), định dạng `YYYY-MM-DD HH:mm:ss`.
- Updated time hiển thị đúng giá trị từ `report.md` (`2026-08-22 17:45:10`).
- Duration hiển thị dạng `"1d 8h"`-style, đúng ngôn ngữ hiện tại của UI.
- Không có thay đổi ngoài phạm vi ticket (các field khác trong card giữ nguyên).

---

### TC-SDD-LEAD-TIME-2 — Thiếu `report.md`, fallback `-`

**Priority:** P0
**Loại:** Negative

**Mô tả:** Xác nhận khi `report.md` không tồn tại, updated time và duration hiển thị `-`, không có lỗi UI.

**Tiêu chí chấp nhận**

- **AC-SDD-LEAD-TIME-4/v1 — `completed_at` NULL, UI hiển thị `-`.**
- **AC-SDD-LEAD-TIME-11/v1 — Duration hiển thị `-` khi 1 trong 2 timestamp null.**

**Precondition:**

- Ticket chỉ có `spec-pack.md` hợp lệ (`ND-1`), không có `report.md` (`ED-2`).
- Đã chạy scan sau khi thiết lập precondition.

**Các bước:**

1. Mở Ticket Detail drawer của ticket này.
2. Quan sát dòng created time, updated time, duration.

**Kết quả mong đợi:**

- Created time hiển thị giá trị thật.
- Updated time hiển thị `-`.
- Duration hiển thị `-`.
- Không có lỗi/crash UI, scan run vẫn hoàn tất bình thường cho các artifact khác của ticket.

---

### TC-SDD-LEAD-TIME-3 — Date field malformed

**Priority:** P1
**Loại:** Boundary / Exception

**Mô tả:** Xác nhận khi header date field tồn tại nhưng không parse được (vd. `TBD`), giá trị bị null hóa thay vì giữ giá trị cũ.

**Tiêu chí chấp nhận**

- **AC-SDD-LEAD-TIME-6/v1 — Malformed date → cột NULL, UI `-`, scan không fail.**

**Precondition:**

- Ticket trước đó đã có `started_at` hợp lệ từ 1 lần scan trước.
- `spec-pack.md` được sửa thành dữ liệu `ED-1` (`**Create date**: TBD`).

**Các bước:**

1. Chạy lại Artifact Scanner scan cho ticket.
2. Mở Ticket Detail drawer.

**Kết quả mong đợi:**

- Created time hiển thị `-` (không giữ giá trị cũ đã hiển thị trước đó).
- Scan run vẫn hoàn tất, không báo lỗi toàn cục.
- Duration hiển thị `-`.

---

### TC-SDD-LEAD-TIME-4 — `completed_at` sớm hơn `started_at`

**Priority:** P1
**Loại:** Boundary

**Mô tả:** Xác nhận khi report.md's Update date sớm hơn spec-pack.md's Create date, duration hiển thị `-` nhưng 2 timestamp vẫn hiển thị bình thường.

**Tiêu chí chấp nhận**

- **AC-SDD-LEAD-TIME-11/v1 — Duration `-` khi `completed_at < started_at`.**

**Precondition:**

- `spec-pack.md` header: `**Create date**: 2026-08-25 08:00:00`.
- `report.md` header: `**Update date**: 2026-08-20 10:00:00` (dữ liệu `BD-3`).

**Các bước:**

1. Chạy scan cho ticket.
2. Mở Ticket Detail drawer.

**Kết quả mong đợi:**

- Created time và updated time hiển thị đúng giá trị thật (không phải `-`).
- Duration hiển thị `-`.

---

### TC-SDD-LEAD-TIME-5 — Re-scan cập nhật giá trị mới

**Priority:** P1
**Loại:** Regression

**Mô tả:** Xác nhận re-scan sau khi `report.md` nội dung date đổi sẽ cập nhật `completed_at` sang giá trị mới, không giữ giá trị cũ.

**Tiêu chí chấp nhận**

- **AC-SDD-LEAD-TIME-8/v1 — Re-scan cập nhật `completed_at` mới.**

**Precondition:**

- Scan 1 đã chạy với `report.md` field = `2026-08-20 10:00:00`.

**Các bước:**

1. Sửa `report.md` field thành `2026-08-25 08:00:00`.
2. Chạy scan lần 2.
3. Mở Ticket Detail drawer.

**Kết quả mong đợi:**

- Updated time hiển thị `2026-08-25 08:00:00` (giá trị mới, không phải giá trị cũ).
- Duration tính lại theo giá trị mới.

---

### TC-SDD-LEAD-TIME-6 — Bare date được normalize thành `00:00:00`

**Priority:** P1
**Loại:** Boundary / Positive

**Mô tả:** Xác nhận khi header date chỉ chứa `YYYY-MM-DD` (không có time-of-day), giá trị được normalize và hiển thị dạng `YYYY-MM-DD 00:00:00`.

**Tiêu chí chấp nhận**

- **AC-SDD-LEAD-TIME-5/v1 — Bare `YYYY-MM-DD` hiển thị là `YYYY-MM-DD 00:00:00`.**

**Precondition:**

- `spec-pack.md` header: `**Create date**: 2026-08-21` (dữ liệu `ND-3`, không có time-of-day).
- Đã chạy scan cho ticket sau khi thiết lập precondition.

**Các bước:**

1. Mở Ticket Detail drawer của ticket này.
2. Quan sát dòng created time.

**Kết quả mong đợi:**

- Created time hiển thị `2026-08-21 00:00:00`, không hiển thị `-` và không hiển thị nguyên văn `2026-08-21` (thiếu phần giờ).
- Updated time/duration không bị ảnh hưởng bởi thay đổi này (giữ hành vi độc lập theo dữ liệu riêng của `report.md`).

---

### TC-SDD-LEAD-TIME-7 — Regression: thứ tự ticket theo `started_at` không đổi

**Priority:** P2
**Loại:** Regression

**Mô tả:** Xác nhận khi `started_at` bắt đầu được populate rộng rãi từ `spec-pack.md` (thay vì phần lớn NULL như trước), hành vi sắp xếp/lọc hiện có của PM Dashboard theo `started_at` vẫn hoạt động đúng, không có ticket bị sắp xếp sai vị trí hoặc biến mất khỏi danh sách.

**Tiêu chí chấp nhận**

- **AC-SDD-LEAD-TIME-10/v1 — Hành vi hiện có của `PmDashboardJdbcAdapter` liên quan `started_at` không hồi quy.**

**Precondition:**

- Có ít nhất 3 ticket với `started_at` khác nhau (một số từ scan mới, một số vẫn `NULL` do chưa có `spec-pack.md` hợp lệ).
- PM Dashboard có sẵn tính năng sắp xếp/lọc theo `started_at` (nếu có) đang dùng trước ticket này.

**Các bước:**

1. Mở PM Dashboard, áp dụng sắp xếp/lọc theo `started_at` (thứ tự hiện có của màn hình, không phải tính năng mới).
2. So sánh thứ tự danh sách ticket trước và sau khi tính năng này được scan (dùng dữ liệu ghi nhận baseline nếu có, hoặc đối chiếu logic thứ tự tăng/giảm dần theo giá trị hiển thị).

**Kết quả mong đợi:**

- Danh sách ticket vẫn sắp xếp đúng thứ tự tăng/giảm dần theo `started_at` như hành vi đã có trước ticket này.
- Ticket có `started_at = NULL` vẫn được xử lý theo quy tắc hiện có của màn hình (không bị loại bỏ ngoài ý muốn, không gây lỗi).
- Không có thay đổi ngoài phạm vi: các cột/field khác trong danh sách giữ nguyên.

---

### TC-SDD-LEAD-TIME-8 — Ticket cũ tự động populate khi được scan lại theo cadence hiện có

**Priority:** P2
**Loại:** Regression / Compatibility

**Mô tả:** Xác nhận ticket đã tồn tại từ trước khi tính năng này triển khai (evidence file có sẵn `Create date`/`Update date` nhưng chưa từng được đọc vào `started_at`/`completed_at`) sẽ tự động được populate ở lần scan kế tiếp theo cadence hiện có, không cần trigger/API/backfill riêng.

**Tiêu chí chấp nhận**

- **AC-SDD-LEAD-TIME-12/v1 — Ticket cũ populate `started_at`/`completed_at` ở lần scan kế tiếp, không cần backfill riêng.**

**Precondition:**

- Chọn một ticket đã tồn tại trong `docs/changes/<TICKET>/` từ trước, có `spec-pack.md`/`report.md` với header date hợp lệ, nhưng `started_at`/`completed_at` hiện đang `NULL` (chưa từng được scan bởi cơ chế mới).
- Không có API/trigger thủ công nào được gọi ngoài cơ chế scan hiện có.

**Các bước:**

1. Chờ/kích hoạt Artifact Scanner scan ticket này theo cadence hiện có của hệ thống (không dùng endpoint mới, không dùng script backfill).
2. Mở Ticket Detail drawer của ticket này sau khi scan hoàn tất.

**Kết quả mong đợi:**

- Created time và updated time hiển thị đúng giá trị từ nội dung file hiện có của ticket (không còn `-`).
- Duration được tính đúng nếu cả 2 giá trị hợp lệ.
- Không có bước thao tác thủ công nào khác ngoài scan cadence hiện có được yêu cầu để đạt kết quả này.

---

## 6. Chưa được bao phủ ở đây

- Cấu trúc/schema DB, tên cột, cách persist nội bộ (đã mô tả ở `context.md`/`impl-plan.md`, không phải black-box).
- Thuật toán chi tiết của date-normalization utility (được cover ở BE unit test, không phải black-box case).
- Toàn bộ ma trận quyền truy cập — không có thay đổi phân quyền trong ticket này.
- Performance benchmark hạ tầng — không có yêu cầu benchmark riêng trong spec-pack.

---

## 7. Traceability: AC ↔ black-box cases

| #   | AC                        | Black-box case(s)                         | Priority | Ghi chú |
| --- | --------------------------- | --------------------------------------------- | -------- | ------- |
| 1   | `AC-SDD-LEAD-TIME-1/v1`      | `TC-SDD-LEAD-TIME-1`                          | P0       |         |
| 2   | `AC-SDD-LEAD-TIME-2/v1`      | `TC-SDD-LEAD-TIME-1`                          | P0       |         |
| 3   | `AC-SDD-LEAD-TIME-3/v1`      | `TC-SDD-LEAD-TIME-2`                          | P0       | `TC-2` cover chiều `report.md` thiếu; hành vi `spec-pack.md` thiếu là đối xứng (cùng cơ chế fallback `-`), quan sát qua cùng case, không tách case riêng |
| 4   | `AC-SDD-LEAD-TIME-4/v1`      | `TC-SDD-LEAD-TIME-2`                          | P0       |         |
| 5   | `AC-SDD-LEAD-TIME-5/v1`      | `TC-SDD-LEAD-TIME-6`                          | P1       |         |
| 6   | `AC-SDD-LEAD-TIME-6/v1`      | `TC-SDD-LEAD-TIME-3`                          | P1       |         |
| 7   | `AC-SDD-LEAD-TIME-7/v1`      | `TC-SDD-LEAD-TIME-1`                          | P0       |         |
| 8   | `AC-SDD-LEAD-TIME-8/v1`      | `TC-SDD-LEAD-TIME-5`                          | P1       |         |
| 9   | `AC-SDD-LEAD-TIME-9/v1`      | `TC-SDD-LEAD-TIME-1`                          | P0       |         |
| 10  | `AC-SDD-LEAD-TIME-10/v1`     | `TC-SDD-LEAD-TIME-7`                          | P2       |         |
| 11  | `AC-SDD-LEAD-TIME-11/v1`     | `TC-SDD-LEAD-TIME-2`, `TC-SDD-LEAD-TIME-4`    | P1       |         |
| 12  | `AC-SDD-LEAD-TIME-12/v1`     | `TC-SDD-LEAD-TIME-8`                          | P2       |         |

---

## 8. Checklist trước khi review

- [ ] Mỗi test case chỉ mô tả hành vi quan sát được, không mô tả implementation.
- [ ] Mỗi expected result có thể kiểm chứng qua UI, contract, response, log hoặc test harness hiện có.
- [ ] P0 bao phủ đầy đủ main flow và rủi ro hiển thị sai timestamp.
- [ ] P1 bao phủ boundary/abnormal quan trọng.
- [ ] P2 chỉ dùng cho smoke/compatibility mở rộng.
- [ ] Có backlink theo màn hình/điểm quan sát.
- [ ] Có traceability AC ↔ test case.
- [ ] Có ghi rõ phần không thuộc coverage.
