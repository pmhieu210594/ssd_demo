# PARSER-REVIEW-CHECKLIST impact-analysis.md

**Ticket ID**: PARSER-REVIEW-CHECKLIST
**Create date**: 2026-06-24
**Author**: ChatGPT
**Update date**: 2026-06-25

---

# 1. Mục tiêu

Phân tích ảnh hưởng của việc triển khai parser `review-checklist.md` tới:

* Dữ liệu hệ thống
* Các thành phần liên quan
* Downstream sử dụng dữ liệu

---

# 2. Phạm vi ảnh hưởng

## 2.1 Trong phạm vi

* Parser xử lý markdown content
* Object `ParsedArtifact`
* Storage layer
* Bảng `tbl_fact_artifact_snapshot`

---

## 2.2 Ngoài phạm vi

* Không ảnh hưởng đến ingestion
* Không ảnh hưởng đến Git / file system
* Không thay đổi các parser khác

---

# 3. Ảnh hưởng tới dữ liệu

## 3.1 Thêm dữ liệu mới

Parser bổ sung dữ liệu cho artifact type:

```text id="r1n2qa"
review_checklist
```

---

## 3.2 Các field được sử dụng

| Field                       | Ý nghĩa             |
| --------------------------- | ------------------- |
| checklist_item_count        | số checklist item   |
| perspective_count           | số loại perspective |
| has_security_perspective    | có security         |
| has_test_perspective        | có test             |
| has_performance_perspective | có performance      |

---

## 3.3 Tác động tới bảng

```text id="q3a6n9"
tbl_fact_artifact_snapshot
```

* Không thay đổi schema hiện có
* Chỉ populate dữ liệu cho artifact_type tương ứng

---

# 4. Ảnh hưởng tới parser

## 4.1 Parser mới

Thêm parser:

```text id="m8z7lw"
ReviewChecklistMarkdownParser
```

---

## 4.2 Không ảnh hưởng parser khác

* Không thay đổi:

  * MarkdownParserCore
  * Các parser hiện có
* Không thay đổi contract chung

---

# 5. Ảnh hưởng tới storage

## 5.1 Storage layer

* Không thay đổi interface
* Sử dụng lại logic lưu dữ liệu hiện có

---

## 5.2 Mapping

* Mapping dựa trên field tương ứng
* Không thay đổi schema

---

# 6. Ảnh hưởng tới downstream

## 6.1 Evidence Quality Score

Sử dụng:

* perspective_count
* checklist_item_count

---

## 6.2 Data Quality

Sử dụng:

* parse_status
* parse_error_reason

---

## 6.3 Reporting

Có thể sử dụng:

* coverage của perspective
* số lượng checklist item

---

# 7. Ảnh hưởng tới performance

## 7.1 Parser

* Chi phí xử lý thấp
* Regex đơn giản
* Không phụ thuộc external system

---

## 7.2 Storage

* Insert / Update đơn giản
* Không tăng đáng kể tải hệ thống

---

# 8. Ảnh hưởng tới data consistency

## 8.1 Idempotency

* Dựa trên `source_hash`
* Không ghi đè khi dữ liệu không đổi

---

## 8.2 Data correctness

* Dữ liệu có thể tái tạo từ input
* Không lưu raw content

---

# 9. Rủi ro

## 9.1 False negative

* Không detect được perspective nếu keyword không match

---

## 9.2 False positive

* Keyword match không chính xác

---

## 9.3 Format variation

* Markdown format khác nhau có thể ảnh hưởng parsing

---

# 10. Giảm thiểu rủi ro

* Sử dụng regex đơn giản, ổn định
* Mapping keyword rõ ràng
* Validation rule cơ bản

---

# 11. Ảnh hưởng tới test

* Cần bổ sung:

  * unit test parser
  * integration test storage

---

# 12. Ảnh hưởng tới vận hành

* Không yêu cầu thay đổi hệ thống vận hành
* Không yêu cầu cấu hình thêm

---

# 13. Kết luận

Việc triển khai parser `review-checklist.md`:

* Không ảnh hưởng tới hệ thống hiện tại
* Chỉ bổ sung dữ liệu mới
* Không thay đổi schema
* Không thay đổi flow hệ thống

Đảm bảo:

* Tính ổn định
* Tính mở rộng
* Tính tương thích với hệ thống hiện có
