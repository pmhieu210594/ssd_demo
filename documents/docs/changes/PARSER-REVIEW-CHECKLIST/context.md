# PARSER-REVIEW-CHECKLIST context.md

**Ticket ID**: PARSER-REVIEW-CHECKLIST
**Create date**: 2026-06-24
**Author**: ChatGPT
**Update date**: 2026-06-25

---

# 1. Mục tiêu hệ thống

Hệ thống cần trích xuất thông tin từ `review-checklist.md` để:

* Đánh giá độ đầy đủ của review
* Xác định các góc nhìn (review perspectives)
* Phục vụ Evidence Quality Score
* Hỗ trợ Data Quality và kiểm soát chất lượng tài liệu

---

# 2. Vai trò của parser trong hệ thống

Parser là thành phần chịu trách nhiệm:

* Nhận markdown content (string)
* Phân tích cấu trúc nội dung
* Trích xuất dữ liệu cần thiết
* Chuẩn hóa dữ liệu thành object `ParsedArtifact`

Parser **không chịu trách nhiệm**:

* Đọc file từ nguồn dữ liệu
* Thực hiện lưu database

---

# 3. Luồng xử lý (context)

```text id="k8l2ux"
Input content (string)
        ↓
ReviewChecklistMarkdownParser
        ↓
ParsedArtifact
        ↓
ArtifactStorageService
        ↓
tbl_fact_artifact_snapshot
```

---

# 4. Input dữ liệu

## 4.1 Định dạng

* Markdown
* Có thể có YAML front matter

---

## 4.2 Nội dung điển hình

* Section (heading)
* Checklist item:

  * `-`
  * `*`
  * `[ ]`
  * `[x]`

---

## 4.3 Metadata

Có thể chứa:

```yaml id="lczj3g"
ticket_id: ...
artifact_type: review_checklist
```

---

# 5. Output dữ liệu

## 5.1 Output trung gian

Parser trả về:

```text id="b2sxu7"
ParsedArtifact
```

Chứa:

* ticket_id
* artifact_type
* parsedSummary:

  * checklist_item_count
  * perspective_count
  * has_*_perspective
* parse_status
* parse_error_reason

---

## 5.2 Output lưu trữ

Dữ liệu được lưu vào:

```text id="m5df2h"
tbl_fact_artifact_snapshot
```

---

# 6. Các khái niệm chính

## 6.1 Checklist item

Là các dòng:

* `- item`
* `* item`
* `- [ ] item`
* `- [x] item`

---

## 6.2 Review perspective

Các góc nhìn:

| Perspective | Ý nghĩa   |
| ----------- | --------- |
| security    | bảo mật   |
| test        | kiểm thử  |
| performance | hiệu năng |

---

## 6.3 Parse status

| Status  | Ý nghĩa          |
| ------- | ---------------- |
| SUCCESS | parse thành công |
| WARNING | thiếu dữ liệu    |
| FAILED  | lỗi parse        |

---

# 7. Quy tắc xử lý

## 7.1 Checklist extraction

* Đếm số dòng checklist
* Không phụ thuộc format chi tiết

---

## 7.2 Perspective detection

Dựa trên:

* Keyword trong nội dung
* Keyword trong section

---

## 7.3 Validation

* Empty content → FAILED
* Missing ticket_id → WARNING
* Không có checklist → WARNING
* Không detect perspective → WARNING

---

# 8. Storage context

## 8.1 Vai trò

Storage layer chịu trách nhiệm:

* Nhận `ParsedArtifact`
* Map sang schema database
* Persist dữ liệu

---

## 8.2 Nguyên tắc

* Parser không truy cập database
* Storage là layer duy nhất ghi DB
* Không thay đổi schema database

---

# 9. Data consistency

* Dữ liệu trong database là dữ liệu phái sinh
* Không lưu raw markdown
* Dữ liệu phải có khả năng tái tạo từ input

---

# 10. Ràng buộc

* Không phụ thuộc nguồn dữ liệu (Git, file system)
* Không thêm field ngoài schema
* Không thay đổi contract parser

---

# 11. Hạn chế

* Không phân tích semantic sâu
* Không đánh giá chất lượng checklist
* Chỉ phát hiện sự tồn tại

---

# 12. Kết luận

Parser `review-checklist.md`:

* nhận input là markdown content
* trích xuất dữ liệu checklist và perspective
* trả về `ParsedArtifact`
* dữ liệu được lưu vào `tbl_fact_artifact_snapshot` thông qua storage layer

Thiết kế đảm bảo:

* Tách biệt trách nhiệm rõ ràng
* Dễ mở rộng
* Phù hợp với kiến trúc hệ thống
