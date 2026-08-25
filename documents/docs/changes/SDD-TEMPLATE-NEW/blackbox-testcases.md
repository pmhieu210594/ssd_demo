# {Feature / Change Name} — Black-box test cases

- **Ticket:** {TICKET_ID}
- **Trạng thái:** Draft
- **Tạo ngày:** {DATE_YYYY_MM_DD} {TIME_HH_MM}
- **Cập nhật ngày:** {DATE_YYYY_MM_DD} {TIME_HH_MM}
- **Phạm vi:** {SCOPE_SUMMARY}

> Nguồn tham chiếu chính: `{SPEC_FILE}`.

---

## 1. Nguyên tắc black-box

Các test case dưới đây chỉ kiểm thử hành vi quan sát được từ bên ngoài hệ thống:

- Trạng thái hiển thị trên màn hình.
- Dữ liệu/điều kiện được chuyển sang bước nghiệp vụ tiếp theo qua contract quan sát được của màn hình gọi, request, response, audit/log hiện có hoặc công cụ kiểm thử hiện có.
- Kết quả điều hướng, kết quả xử lý, thông báo hiện có và outcome hiện có.

Không giả định các chi tiết implementation như state nội bộ, component store, cache, database schema, event bus, tên hàm, tên class hoặc thuật toán xử lý nội bộ.

## 2. Phân tầng ưu tiên

| Priority | Ý nghĩa                                                               | Kỳ vọng                                                   |
| -------- | --------------------------------------------------------------------- | --------------------------------------------------------- |
| P0       | Hành vi bắt buộc để tránh chuyển sai dữ liệu hoặc phá vỡ luồng chính  | Phải pass trước khi release                               |
| P1       | Boundary/abnormal quan trọng, dễ tạo hồi quy hoặc hiểu nhầm nghiệp vụ | Nên pass trong cùng vòng kiểm thử                         |
| P2       | Regression/compatibility mở rộng, không tăng coverage vô hạn          | Chạy khi có thời gian hoặc khi vùng liên quan bị thay đổi |

## 3. Backlink theo màn hình / điểm quan sát

### {SCREEN_OR_FLOW_1}

- Bao phủ trực tiếp: `{CASE_ID}`, `{CASE_ID}`.
- Bao phủ liên quan: `{CASE_ID}`, `{CASE_ID}`.

### {SCREEN_OR_FLOW_2}

- Bao phủ trực tiếp: `{CASE_ID}`, `{CASE_ID}`.
- Bao phủ liên quan: `{CASE_ID}`, `{CASE_ID}`.

### Bước nghiệp vụ tiếp theo / kết quả xử lý

- Bao phủ trực tiếp: `{CASE_ID}`, `{CASE_ID}`.
- Bao phủ liên quan: `{CASE_ID}` để đối chiếu dữ liệu hiển thị với dữ liệu được chuyển bước.

## 4. Bộ dữ liệu tham chiếu

Chi tiết dữ liệu precondition, input và expected result nằm trong `{TEST_DATA_FILE}`.

Các ký hiệu chính:

- `{DATASET_A}`: {Mô tả tập dữ liệu A}.
- `{DATASET_B}`: {Mô tả tập dữ liệu B}.
- `{DATASET_EMPTY}`: tập kết quả rỗng.
- `{ROW_VALID}`: dòng hợp lệ.
- `{ROW_INVALID}`: dòng thiếu dữ liệu/khóa/điều kiện hợp lệ.
- `{ROW_CONFLICT}`: dòng hợp lệ nhưng gặp lỗi nghiệp vụ/xung đột ở bước tiếp theo.

---

## 5. Các black-box test case

### {CASE_ID} — {Tên test case ngắn gọn}

**Priority:** {P0/P1/P2}  
**Loại:** {Positive / Negative / Boundary / Regression / Compatibility / Exception}

**Mô tả:** {Mô tả mục tiêu kiểm thử bằng hành vi quan sát được từ bên ngoài hệ thống.}

**Tiêu chí chấp nhận**

- **[AC-ID-01]/v1 — [Tiêu chí chấp nhận 1].**
- **[AC-ID-02]/v1 — [Tiêu chí chấp nhận 2].**
- **[AC-ID-03]/v1 — [Tiêu chí chấp nhận 3].**
- **[AC-ID-04]/v1 — [Tiêu chí chấp nhận 4].**

**Precondition:**

- {Điều kiện tiên quyết 1}.
- {Điều kiện tiên quyết 2}.
- {Dữ liệu cần có, quyền truy cập hoặc baseline cần đối chiếu}.

**Các bước:**

1. {Bước thao tác 1}.
2. {Bước thao tác 2}.
3. {Bước thao tác 3}.
4. {Bước thao tác 4}.
5. Quan sát {màn hình / contract / request / response / log / outcome hiện có}.

**Kết quả mong đợi:**

- {Expected result 1}.
- {Expected result 2}.
- {Expected result 3}.
- Không có thay đổi ngoài phạm vi {feature/change/ticket}.
- Nếu có lỗi nghiệp vụ khác, outcome giữ theo hành vi hiện có.

---

### {CASE_ID} — {Tên test case ngắn gọn}

**Priority:** {P0/P1/P2}  
**Loại:** {Positive / Negative / Boundary / Regression / Compatibility / Exception}

**Mô tả:** {Mô tả test case.}

**Tiêu chí chấp nhận**

- **[AC-ID-01]/v1 — [Tiêu chí chấp nhận 1].**
- **[AC-ID-02]/v1 — [Tiêu chí chấp nhận 2].**
- **[AC-ID-03]/v1 — [Tiêu chí chấp nhận 3].**
- **[AC-ID-04]/v1 — [Tiêu chí chấp nhận 4].**

**Precondition:**

- {Điều kiện tiên quyết}.

**Các bước:**

1. {Bước thao tác}.
2. {Bước thao tác}.
3. {Bước thao tác}.

**Kết quả mong đợi:**

- {Kết quả mong đợi}.
- {Kết quả mong đợi}.

---

## 6. Chưa được bao phủ ở đây

Các nội dung sau không thuộc coverage của tài liệu black-box này:

- Thuật toán, cấu trúc state, cache hoặc component nội bộ.
- Tên bảng, schema DB, migration hoặc cách persist dữ liệu tạm thời nếu có.
- Thay đổi UI/UX mới ngoài những hành vi đã được mô tả trong spec.
- Kiểm thử chi tiết từng màn hình nghiệp vụ nếu chỉ khác nhau về domain data nhưng cùng contract/hành vi.
- Kiểm thử toàn bộ ma trận quyền truy cập; chỉ có smoke nếu cần xác nhận không mở rộng phạm vi quyền.
- Performance benchmark cấp hạ tầng, trừ khi có hành vi black-box cụ thể cần xác nhận.

---

## 7. Traceability: AC ↔ black-box cases

| #   | AC           | Black-box case(s)        | Priority   | Ghi chú           |
| --- | ------------ | ------------------------ | ---------- | ----------------- |
| 1   | `{AC-ID/v1}` | `{CASE_ID}`              | {P0/P1/P2} | {Ghi chú mapping} |
| 2   | `{AC-ID/v1}` | `{CASE_ID}`, `{CASE_ID}` | {P0/P1/P2} | {Ghi chú mapping} |
| 3   | `{AC-ID/v1}` | `{CASE_ID}`              | {P0/P1/P2} | {Ghi chú mapping} |

---

## 8. Checklist trước khi review

- [ ] Mỗi test case chỉ mô tả hành vi quan sát được, không mô tả implementation.
- [ ] Mỗi expected result có thể kiểm chứng qua UI, contract, response, log hoặc test harness hiện có.
- [ ] P0 bao phủ đầy đủ main flow và rủi ro chuyển sai dữ liệu.
- [ ] P1 bao phủ boundary/abnormal quan trọng.
- [ ] P2 chỉ dùng cho smoke/compatibility mở rộng.
- [ ] Có backlink theo màn hình/điểm quan sát.
- [ ] Có traceability AC ↔ test case.
- [ ] Có ghi rõ phần không thuộc coverage.
