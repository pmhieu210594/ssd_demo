# PARSER-REVIEW-CHECKLIST source-availability.md

**Ticket ID**: PARSER-REVIEW-CHECKLIST
**Create date**: 2026-06-24
**Author**: ChatGPT
**Update date**: 2026-06-25

---

# 1. Mục tiêu

Xác định tính sẵn sàng (availability) và đặc điểm của dữ liệu đầu vào cho parser `review-checklist.md`, nhằm:

* Đảm bảo parser có đủ dữ liệu để hoạt động
* Xác định các trường hợp dữ liệu thiếu hoặc không hợp lệ
* Hỗ trợ thiết kế validation và error handling

---

# 2. Phạm vi

## 2.1 Trong phạm vi

* Input content (markdown dạng string)
* Metadata đi kèm (source_path, source_hash, ticket_id)

---

## 2.2 Ngoài phạm vi

* Không xét nguồn dữ liệu (Git, API, file system)
* Không xét cơ chế thu thập dữ liệu
* Không xét pipeline upstream

---

# 3. Input availability

## 3.1 Content

### Trạng thái có thể xảy ra

| Trạng thái | Mô tả                |
| ---------- | -------------------- |
| Available  | Có nội dung markdown |
| Empty      | Content rỗng         |
| Null       | Không có content     |

---

### Xử lý

| Trạng thái | Hành vi               |
| ---------- | --------------------- |
| Available  | Parse bình thường     |
| Empty      | parse_status = FAILED |
| Null       | parse_status = FAILED |

---

## 3.2 Format

### Trạng thái

| Trạng thái       | Mô tả                |
| ---------------- | -------------------- |
| Valid markdown   | Có thể parse         |
| Invalid markdown | Format không chuẩn   |
| Mixed format     | Kết hợp nhiều format |

---

### Xử lý

* Parser vẫn cố gắng parse tối đa
* Không fail ngay nếu format không chuẩn
* Áp dụng validation sau parsing

---

## 3.3 Checklist item

### Trạng thái

| Trạng thái         | Mô tả                          |
| ------------------ | ------------------------------ |
| Có checklist       | Có dòng `-`, `*`, `[ ]`, `[x]` |
| Không có checklist | Không có list                  |

---

### Xử lý

| Trạng thái         | Hành vi                  |
| ------------------ | ------------------------ |
| Có checklist       | checklist_item_count > 0 |
| Không có checklist | parse_status = WARNING   |

---

## 3.4 Perspective

### Trạng thái

| Trạng thái           | Mô tả               |
| -------------------- | ------------------- |
| Có perspective       | Detect được keyword |
| Không có perspective | Không detect được   |

---

### Xử lý

| Trạng thái           | Hành vi                |
| -------------------- | ---------------------- |
| Có perspective       | perspective_count > 0  |
| Không có perspective | parse_status = WARNING |

---

## 3.5 Metadata

### Ticket ID

| Trạng thái | Mô tả              |
| ---------- | ------------------ |
| Available  | Có ticket_id       |
| Missing    | Không có ticket_id |

---

### Xử lý

| Trạng thái | Hành vi                |
| ---------- | ---------------------- |
| Available  | Sử dụng bình thường    |
| Missing    | parse_status = WARNING |

---

# 4. Data completeness

## 4.1 Mức độ đầy đủ

| Thành phần  | Bắt buộc |
| ----------- | -------- |
| content     | Có       |
| ticket_id   | Không    |
| checklist   | Không    |
| perspective | Không    |

---

## 4.2 Quy tắc

* Thiếu content → FAILED
* Thiếu các thành phần khác → WARNING

---

# 5. Data reliability

## 5.1 Đặc điểm

* Dữ liệu không đảm bảo format cố định
* Có thể thiếu hoặc không đầy đủ
* Phụ thuộc vào người viết markdown

---

## 5.2 Hệ quả

* Parser phải tolerant với format
* Không assume cấu trúc cố định
* Dựa trên pattern đơn giản (regex, keyword)

---

# 6. Data variability

## 6.1 Variation có thể xảy ra

* Checklist viết bằng `-` hoặc `*`
* Có hoặc không có `[ ]`
* Keyword viết khác nhau

---

## 6.2 Xử lý

* Dùng regex linh hoạt
* Dùng keyword mapping đơn giản

---

# 7. Error scenario

## 7.1 Empty content

```text id="6y6f3h"
content = ""
```

→ FAILED

---

## 7.2 No checklist

```text id="0v3g3q"
checklist_item_count = 0
```

→ WARNING

---

## 7.3 No perspective

```text id="p7f3r9"
perspective_count = 0
```

→ WARNING

---

## 7.4 Missing ticket_id

```text id="8f5l1h"
ticket_id = null
```

→ WARNING

---

# 8. Liên hệ với parser

## 8.1 Input

Parser nhận:

```text id="5czq0m"
content (string)
source_path
metadata
```

---

## 8.2 Output

Parser trả:

```text id="v3c6nj"
ParsedArtifact
```

---

# 9. Ràng buộc

* Không giả định input luôn hợp lệ
* Không phụ thuộc nguồn dữ liệu
* Không thay đổi dữ liệu input
* Không bổ sung dữ liệu ngoài parsing

---

# 10. Kết luận

Input của parser:

* Có thể thiếu
* Có thể không chuẩn
* Có thể không đầy đủ

Parser cần:

* Xử lý tolerant
* Áp dụng validation rõ ràng
* Trả về kết quả nhất quán qua `ParsedArtifact`
