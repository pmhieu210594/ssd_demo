# Gói đặc tả — PARSER-SPEC-PACK

- **Ticket:** PARSER-SPEC-PACK
- **Trạng thái:** Draft
- **Tạo ngày:** 2026-06-19

**Create date**: 2026-06-19

## 1. Bối cảnh / Mục đích

`spec-pack.md` là tài liệu đặc tả trung tâm cho từng ticket trong luồng SDD Evidence Collection &
Analysis. Parser này đọc file theo template chuẩn, chuẩn hoá thành dữ liệu có cấu trúc, và cung cấp
đầu vào cho Artifact Inventory, Evidence Quality Score, AC-Test Coverage, và Traceability Map.

## 2. Phạm vi

### 2.1 Trong phạm vi

- Đọc file `docs/changes/<TICKET>/spec-pack.md` hoặc nội dung đã đồng bộ vào hệ thống.
- Nhận diện `ticket_id` từ đường dẫn, front matter, hoặc header khi có.
- Trích xuất các mục chính và bảng theo template chuẩn.
- Chuẩn hoá Acceptance Criteria theo định dạng `AC-<NHÓM>-<n>/v<version>`.
- Phát hiện placeholder còn sót lại trong các trường bắt buộc.

### 2.2 Ngoài phạm vi

- Hiểu ngôn ngữ tự nhiên tổng quát ngoài phạm vi template `spec-pack.md`.
- Phân tích các file đặc tả khác như `impl-plan.md`, `test-plan.md`, `report.md`.
- Tính toán trực tiếp Evidence Quality Score, AC-Test Coverage, hoặc Traceability Map.

## 3. Thuật ngữ

| #   | Thuật ngữ | Định nghĩa                                                    |
| --- | --------- | -------------------------------------------------------------- |
| 1   | Spec Pack | Tài liệu đặc tả theo ticket, dùng làm nguồn sự thật duy nhất  |
| 2   | AC        | Acceptance Criteria, phải có mã chuẩn để ánh xạ test           |

## 4. Hiện trạng / Trạng thái mục tiêu

Hiện trạng: chưa có parser riêng cho `spec-pack.md` trong luồng ingest chính thức. Trạng thái mục
tiêu: parser đọc và chuẩn hoá dữ liệu tự động, phục vụ Artifact Scanner.

## 5. Chi tiết đặc tả

Parser đọc file theo đường dẫn chuẩn, trích xuất section/table theo template, và chuẩn hoá kết quả
thành JSON/record để lưu DB và phục vụ tính điểm chất lượng.

## 6. Yêu cầu phi chức năng

| #   | Danh mục          | Yêu cầu                                              |
| --- | ----------------- | ----------------------------------------------------- |
| 1   | Hiệu năng          | Không chặn pipeline; runtime ổn định với file thông thường |
| 2   | Bảo mật            | Không đọc/ghi secrets hoặc source code ngoài phạm vi |

## 7. Tiêu chí chấp nhận

### 7.1 Đọc và chuẩn hoá file (AC-CORE)

| ID           | Mô tả                                                                          | UT  | IT  | E2E | BB  |
| ------------ | ------------------------------------------------------------------------------ | --- | --- | --- | --- |
| AC-CORE-1/v1 | Khi cung cấp file theo đường dẫn chuẩn, parser đọc đúng `ticket_id`             | ✓   |     |     | ✓   |
| AC-CORE-2/v1 | Khi file chứa front matter hợp lệ, parser ưu tiên giá trị front matter         |     | ✓   | ✓   | ✓   |

### 7.X Không hồi quy (AC-REG)

| ID          | Mô tả                                                          | UT  | IT  | E2E | BB  |
| ----------- | ---------------------------------------------------------------- | --- | --- | --- | --- |
| AC-REG-1/v1 | Hành vi parse cũ vẫn hoạt động đúng sau khi đổi template          | ✓   | ✓   | ✓   | ✓   |

## 8. Các vấn đề mở

| #   | ID                       | Câu hỏi                                          | Ưu tiên | Người phụ trách | Hạn chót   |
| --- | ------------------------ | -------------------------------------------------- | ------- | ---------------- | ---------- |
| 1   | OI-PARSER-SPEC-PACK-001 | Có cần hỗ trợ heading biến thể không?             | P0      | BA                | 2026-07-01 |
| 2   | OI-PARSER-SPEC-PACK-002 | Có cần audit log riêng cho từng lần parse không?  | P1      | Dev               | 2026-07-05 |

## 9. Rủi ro

| #   | Rủi ro                       | Khả năng xảy ra | Mức độ ảnh hưởng | Biện pháp giảm thiểu             |
| --- | ------------------------------ | ----------------- | ------------------- | ----------------------------------- |
| 1   | Template tiếp tục thay đổi     | Trung bình         | Cao                  | Tách mapping heading khỏi logic core |

## 10. Bảng truy vết

| #   | AC           | Màn hình/API | DB  | Logs      | Quyền | Loại kiểm thử |
| --- | ------------ | -------------- | --- | --------- | ------ | --------------- |
| 1   | AC-CORE-1/v1 | Parser API      | —   | parse_log | N/A    | UT · BB          |

## 11. Phán định implementation readiness

**Có thể bắt đầu implementation với Spec Pack này — Yes.**

## 12. Thứ tự ưu tiên Open Issues cần con người quyết định

1. **P0 — OI-PARSER-SPEC-PACK-001:** Có cần hỗ trợ heading biến thể không?
2. **P1 — OI-PARSER-SPEC-PACK-002:** Có cần audit log riêng cho từng lần parse không?
