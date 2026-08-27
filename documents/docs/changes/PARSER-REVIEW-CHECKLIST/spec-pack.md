# PARSER-REVIEW-CHECKLIST spec-pack.md

**Ticket ID**: PARSER-REVIEW-CHECKLIST
**Create date**: 2026-06-24
**Author**: ChatGPT
**Update date**: 2026-06-25

---

# Spec Pack — Parser review-checklist.md (Aligned with Final Design)

---

## 1. Bối cảnh / Mục đích

Parser `review-checklist.md` là một thành phần trong hệ thống chuẩn hóa dữ liệu SDD nhằm:

* Cung cấp dữ liệu cho **Evidence Quality Score**
* Phát hiện thiếu hụt góc nhìn review
* Hỗ trợ cải tiến rule và template

Nguyên tắc:

* Input được cung cấp từ upstream
* Database chỉ lưu **metadata phái sinh**
* Parser không đọc dữ liệu từ database

---

## 2. Scope

### 2.1 In Scope

* Parse markdown content của `review-checklist.md`
* Trích xuất:

  * review perspectives
  * checklist item count
* Parse YAML front matter (nếu có)
* Xác định parse status
* Lưu metadata vào `tbl_fact_artifact_snapshot`

---

### 2.2 Out of Scope

* Đọc file từ Git / file system
* Data ingestion pipeline
* NLP semantic parsing
* Validate nội dung checklist
* Suggest cải thiện

---

## 3. Thuật ngữ

| Term        | Định nghĩa                    |
| ----------- | ----------------------------- |
| Artifact    | Nội dung markdown đầu vào     |
| Perspective | security / test / performance |
| Metadata    | dữ liệu phái sinh             |
| Parser      | module xử lý markdown         |

---

## 4. As-Is / To-Be

### As-Is

* Markdown không có metadata machine-readable
* Không tracking perspective coverage

---

### To-Be

* Metadata chuẩn hóa
* Dữ liệu có thể dùng cho analytics & scoring
* Không thay đổi kiến trúc hệ thống hiện tại

---

## 5. Data Flow (BẮT BUỘC)

```text
Input content (string)
        ↓
Markdown Parser (review-checklist)
        ↓
ParsedArtifact
        ↓
tbl_fact_artifact_snapshot
```

### Quy tắc

* Parser nhận input:

  * content (string)
  * metadata (source_path, source_hash, ...)
* Parser không:

  * đọc file
  * truy cập database

---

## 6. Specification chi tiết

### 6.1 Input

* content (string)
* source_path
* source_hash
* updated_at (metadata)

---

### 6.2 Output (Metadata)

```json
{
  "ticket_id": "string",
  "artifact_type": "review_checklist",

  "has_security_perspective": "boolean",
  "has_test_perspective": "boolean",
  "has_performance_perspective": "boolean",

  "perspective_count": "number",
  "checklist_item_count": "number",

  "parse_status": "SUCCESS | WARNING | FAILED",
  "parse_error_reason": "string | null"
}
```

---

## 7. Processing Logic

### Step 1 — Input validation

* Nếu:

  * content = null
  * hoặc content = empty

→ parse_status = failed

---

### Step 2 — Preprocessing

* Normalize text
* Remove code block
* Chuẩn hóa newline

---

### Step 3 — YAML Front Matter

* Nếu tồn tại:

  * extract:

    * ticket_id
    * artifact_type
* Ưu tiên giá trị từ YAML

---

### Step 4 — Structure Detection

Detect:

* heading (`##`, `###`)
* checklist item

Checklist regex:

```
^\s*[-*]\s*(\[[ xX]\])?
```

---

### Step 5 — Perspective Detection

Keyword mapping:

| Keyword                | Perspective |
| ---------------------- | ----------- |
| security, auth, secret | security    |
| test, qa               | test        |
| performance, latency   | performance |

Rule:

* Case-insensitive
* Word-boundary match

---

### Step 6 — Aggregation

* Set:

  * has_security_perspective
  * has_test_perspective
  * has_performance_perspective

* Count:

  * perspective_count (unique)
  * checklist_item_count

---

### Step 7 — Parse Status

| Status  | Điều kiện      |
| ------- | -------------- |
| success | parse OK       |
| warning | thiếu nội dung |
| failed  | lỗi parse      |

---

## 8. Non-functional

### 8.1 Idempotency

* Nếu `source_hash` không đổi → skip xử lý

---

### 8.2 Performance

* O(n) theo file size
* Phù hợp batch processing

---

### 8.3 Data Quality

* Parse lỗi vẫn ghi nhận metadata

---

### 8.4 Metadata First

* Không lưu raw markdown

---

## 9. Data Lineage (BẮT BUỘC)

Parser phải cung cấp:

| Field          | Ý nghĩa   |
| -------------- | --------- |
| source_path    | path      |
| source_hash    | hash      |
| parser_version | version   |
| job_run_id     | batch id  |
| parsed_at      | timestamp |

---

## 10. Storage Design (CRITICAL)

### 10.1 Table

```
tbl_fact_artifact_snapshot
```

---

### 10.2 Base Fields

* ticket_id
* artifact_type
* source_path
* source_hash
* parse_status
* parse_error_reason
* parser_version
* parsed_at

---

### 10.3 Extended Fields (review-checklist)

* has_security_perspective
* has_test_perspective
* has_performance_perspective
* perspective_count
* checklist_item_count

---

### 10.4 Rule

* Chỉ áp dụng khi:

```
artifact_type = 'review_checklist'
```

---

## 11. Security Impact

* Không xử lý dữ liệu nhạy cảm
* Không expose secret
* Chỉ xử lý metadata

---

## 12. Operation / Maintenance

* Logging parse error
* Track parser_version
* Hỗ trợ reprocess theo source_hash

---

## 13. Test Strategy Summary

* Unit test:

  * checklist parsing
  * keyword detection
* Edge cases:

  * empty content
  * invalid format
* Không phụ thuộc database

---

## 14. Acceptance Criteria

* AC-1: Content hợp lệ → parse success
* AC-2: Content rỗng → failed
* AC-3: Detect đúng perspective
* AC-4: Đếm đúng checklist item
* AC-5: YAML được ưu tiên
* AC-6: Parse lỗi vẫn ghi nhận metadata
* AC-7: Idempotent theo source_hash

---

## 15. Human Decision Required

* Keyword list có mở rộng không?
* Có enforce template không?

---

## 16. Assumptions

* Markdown UTF-8
* Checklist không strict schema
* Keyword đủ cho MVP

---

## 17. Open Issues

### Blocking

* Không có schema strict cho checklist

### Non-blocking

* Keyword chưa exhaustive
* Markdown edge case

---

## 18. Complexity Classification

* Level: Medium

Lý do:

* parsing đơn giản
* logic rõ ràng
* không phụ thuộc external system

---

# Kết luận

Spec Pack này:

* Đúng scope parser
* Không phụ thuộc ingestion
* Không over-design
* Đủ để implement production parser
