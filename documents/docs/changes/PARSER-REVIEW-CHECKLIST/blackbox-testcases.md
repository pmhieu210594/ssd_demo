# PARSER-REVIEW-CHECKLIST blackbox-testcases.md

**Ticket ID**: PARSER-REVIEW-CHECKLIST
**Create date**: 2026-06-24
**Author**: ChatGPT
**Update date**: 2026-06-25

---

# 1. Mục tiêu

Xác minh chức năng parser `review-checklist.md` theo hướng black-box:

* Không phụ thuộc implementation
* Dựa trên input/output theo specification
* Đảm bảo đáp ứng Acceptance Criteria (AC)

---

# 2. Phạm vi

* Parsing markdown input
* Extract:

  * checklist
  * perspective
  * ticket_id
* Validate:

  * parse_status
  * output DTO
* Quan sát side-effect:

  * log
  * storage behavior (nếu có)

---

# 3. Danh sách test case

---

## 3.1 AC-01 – Checklist extraction

### TC-01 (P0) – Normal checklist

**Precondition**

* Không

**Input**

```text
- [ ] item 1
- [x] item 2
- [ ] item 3
```

**Expected**

* checklist_item_count = 3
* parse_status = SUCCESS

---

### TC-02 (P1) – Mixed format checklist

**Input**

```text
- item 1
* item 2
- [x] item 3
```

**Expected**

* checklist_item_count = 3

---

### TC-03 (P1) – No checklist

**Input**

```text
This is a document without checklist
```

**Expected**

* checklist_item_count = 0
* parse_status = WARNING

---

### TC-04 (P2) – Checklist in code block (boundary)

**Input**

```text
\`\`\`
- [ ] item 1
\`\`\`
```

**Expected**

* checklist_item_count = 0

---

## 3.2 AC-02 – Perspective detection

### TC-05 (P0) – Detect security perspective

**Input**

```text
- [ ] security check required
```

**Expected**

* has_security_perspective = true

---

### TC-06 (P0) – Detect multiple perspectives

**Input**

```text
- [ ] security check
- [ ] performance optimization
- [ ] test coverage
```

**Expected**

* perspective_count = 3

---

### TC-07 (P1) – No perspective

**Input**

```text
- [ ] random task
```

**Expected**

* perspective_count = 0
* parse_status = WARNING

---

### TC-08 (P2) – False positive keyword

**Input**

```text
performance review meeting
```

**Expected**

* perspective_count = 0

---

## 3.3 AC-03 – ticket_id extraction

### TC-09 (P0) – ticket_id from YAML

**Input**

```yaml
ticket_id: ABC-123
```

**Expected**

* ticket_id = ABC-123

---

### TC-10 (P1) – Missing ticket_id

**Input**

```text
- [ ] item 1
```

**Expected**

* parse_status = WARNING

---

### TC-11 (P2) – Invalid ticket_id format

**Input**

```yaml
ticket_id: ???
```

**Expected**

* parse_status = WARNING

---

## 3.4 AC-04 – Empty content

### TC-12 (P0) – Empty input

**Input**

```text
""
```

**Expected**

* parse_status = FAILED

---

## 3.5 AC-05 – Invalid format

### TC-13 (P0) – Malformed markdown

**Input**

```text
::: invalid structure
```

**Expected**

* parse_status = FAILED
* Không crash

---

### TC-14 (P1) – Malformed YAML

**Input**

```yaml
ticket_id: ABC-123
: invalid
```

**Expected**

* parse_status = WARNING hoặc FAILED

---

## 3.6 AC-06 – Output DTO validation

### TC-15 (P0) – Validate full DTO

**Input**

```text
- [ ] security check
```

**Expected**

* checklist_item_count đúng
* perspective flags đúng
* parse_status hợp lệ
* DTO không thiếu field

---

## 3.7 Operation viewpoint

### TC-16 (P1) – Batch processing

**Input**

* Multiple markdown files

**Expected**

* Không crash toàn bộ batch
* Mỗi file xử lý độc lập

---

### TC-17 (P1) – Webhook trigger

**Input**

* Trigger từ event

**Expected**

* Parser chạy thành công
* Không ảnh hưởng flow chính

---

## 3.8 Logging / Audit

### TC-18 (P2) – Log SUCCESS

**Expected**

* Có log parse SUCCESS

---

### TC-19 (P2) – Log ERROR

**Expected**

* Có log khi parse FAILED

---

# 4. Mapping AC ↔ Test Case

| AC    | Test Cases    |
| ----- | ------------- |
| AC-01 | TC-01 → TC-04 |
| AC-02 | TC-05 → TC-08 |
| AC-03 | TC-09 → TC-11 |
| AC-04 | TC-12         |
| AC-05 | TC-13 → TC-14 |
| AC-06 | TC-15         |

---

# 5. Priority Summary

| Priority | Test Cases              |
| -------- | ----------------------- |
| P0       | TC-01,05,06,09,12,13,15 |
| P1       | TC-02,03,07,10,14,16,17 |
| P2       | TC-04,08,11,18,19       |

---

# 6. Kết luận

Black-box testcases đảm bảo:

* Cover đầy đủ AC
* Có đủ:

  * normal case
  * error case
  * boundary case
* Không phụ thuộc implementation
* Có thể dùng trực tiếp cho QA / UAT

