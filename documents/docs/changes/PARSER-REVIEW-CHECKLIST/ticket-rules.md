# PARSER-REVIEW-CHECKLIST ticket-rules.md

**Ticket ID**: PARSER-REVIEW-CHECKLIST
**Create date**: 2026-06-24
**Author**: ChatGPT
**Update date**: 2026-06-25

---

# 1. Mục tiêu

Định nghĩa các rule kiểm tra dữ liệu của `review-checklist.md` sau khi được parse, nhằm:

* Đảm bảo chất lượng dữ liệu
* Phát hiện thiếu sót trong review checklist
* Hỗ trợ downstream (Evidence Quality Score, Data Quality)

---

# 2. Phạm vi

## 2.1 Đầu vào

Dữ liệu đầu vào cho rule là:

```text id="b0tq0q"
ParsedArtifact
```

Không làm việc trực tiếp với markdown content

---

## 2.2 Đầu ra

Kết quả:

* parse_status
* parse_error_reason
* Các warning / error tương ứng

---

# 3. Nguyên tắc

* Rule chỉ áp dụng trên dữ liệu đã parse
* Không truy cập markdown raw
* Không truy cập database
* Không thay đổi dữ liệu
* Chỉ kiểm tra và đánh giá

---

# 4. Nhóm rule

## 4.1 Rule về nội dung

### Rule 1: Empty content

**Điều kiện**

```text id="7q2hsm"
content rỗng hoặc null
```

**Kết quả**

```text id="5aqz3w"
parse_status = FAILED
```

---

### Rule 2: Missing ticket_id

**Điều kiện**

```text id="1r5qqn"
ParsedArtifact.ticket_id = null hoặc empty
```

**Kết quả**

```text id="5p8n2p"
parse_status = WARNING
```

---

## 4.2 Rule về checklist

### Rule 3: No checklist item

**Điều kiện**

```text id="e5hpt7"
checklist_item_count = 0
```

**Kết quả**

```text id="2y7xkq"
parse_status = WARNING
```

---

### Rule 4: Checklist tồn tại

**Điều kiện**

```text id="p6b6lg"
checklist_item_count > 0
```

**Kết quả**

```text id="4hyv6j"
Không phát sinh lỗi
```

---

## 4.3 Rule về perspective

### Rule 5: No perspective detected

**Điều kiện**

```text id="6r7tci"
perspective_count = 0
```

**Kết quả**

```text id="w5r38r"
parse_status = WARNING
```

---

### Rule 6: Có perspective

**Điều kiện**

```text id="w7x7x3"
perspective_count > 0
```

**Kết quả**

```text id="d0f4gd"
Không phát sinh lỗi
```

---

## 4.4 Rule về consistency

### Rule 7: Perspective flag consistency

**Điều kiện**

```text id="7l3c9w"
perspective_count = tổng số field has_*_perspective = true
```

**Kết quả**

```text id="m3j3xb"
Nếu không khớp → WARNING
```

---

# 5. Xác định parse_status

## 5.1 Nguyên tắc

```text id="t6j4mj"
Nếu có bất kỳ FAILED → parse_status = FAILED
Nếu không có FAILED nhưng có WARNING → parse_status = WARNING
Nếu không có lỗi → parse_status = SUCCESS
```

---

## 5.2 Thứ tự ưu tiên

1. FAILED (cao nhất)
2. WARNING
3. SUCCESS

---

# 6. parse_error_reason

## 6.1 Nguyên tắc

* Ghi nhận lỗi chính
* Không cần ghi toàn bộ chi tiết

---

## 6.2 Ví dụ

| Condition         | parse_error_reason        |
| ----------------- | ------------------------- |
| Empty content     | "empty content"           |
| Missing ticket_id | "missing ticket_id"       |
| No checklist      | "no checklist item"       |
| No perspective    | "no perspective detected" |

---

# 7. Thứ tự áp dụng rule

```text id="3y6n8r"
1. Validate content
2. Validate metadata
3. Validate checklist
4. Validate perspective
5. Aggregate result
```

---

# 8. Ví dụ

## 8.1 Case hợp lệ

```json id="q1p1sv"
{
  "checklist_item_count": 5,
  "perspective_count": 2,
  "ticket_id": "ABC-123"
}
```

**Kết quả**

```text id="4c7o9g"
parse_status = SUCCESS
```

---

## 8.2 Case thiếu checklist

```json id="w6z2h6"
{
  "checklist_item_count": 0,
  "perspective_count": 1
}
```

**Kết quả**

```text id="y7t2qq"
parse_status = WARNING
```

---

## 8.3 Case empty content

```json id="h2z3yf"
{}
```

**Kết quả**

```text id="9o7qme"
parse_status = FAILED
```

---

# 9. Ràng buộc

* Không thêm rule ngoài spec
* Không thay đổi dữ liệu ParsedArtifact
* Không phụ thuộc vào external system

---

# 10. Kết luận

Các rule:

* Áp dụng trên `ParsedArtifact`
* Xác định chất lượng dữ liệu
* Thiết lập `parse_status` và `parse_error_reason`

Đảm bảo:

* Logic đơn giản
* Dễ kiểm tra
* Phù hợp với phạm vi parser
