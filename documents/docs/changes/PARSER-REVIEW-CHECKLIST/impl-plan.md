# PARSER-REVIEW-CHECKLIST impl-plan.md

**Ticket ID**: PARSER-REVIEW-CHECKLIST
**Create date**: 2026-06-24
**Author**: ChatGPT
**Update date**: 2026-06-25

---

# 1. Mục tiêu

Triển khai chức năng parser cho `review-checklist.md` nhằm:

* Trích xuất checklist item
* Xác định các góc nhìn review (perspective)
* Chuẩn hóa dữ liệu
* Lưu metadata vào `tbl_fact_artifact_snapshot`

---

# 2. Phạm vi

## 2.1 Trong phạm vi

* Parse markdown content (string)
* Trả về `ParsedArtifact`
* Lưu dữ liệu vào database thông qua storage layer

---

## 2.2 Ngoài phạm vi

* Không đọc file từ Git
* Không xử lý ingestion pipeline
* Không xử lý downstream analytics

---

# 3. Thành phần cần triển khai

## 3.1 ReviewChecklistMarkdownParser

### Method chính

```java
ParsedArtifact parse(String content, String sourcePath, Map<String, Object> metadata)
```

---

### Trách nhiệm

* Parse markdown
* Trích xuất dữ liệu
* Validate
* Trả về `ParsedArtifact`

---

## 3.2 ArtifactStorageService

### Interface

```java
interface ArtifactStorageService {
    void save(ParsedArtifact artifact);
}
```

---

### Trách nhiệm

* Nhận `ParsedArtifact`
* Map sang schema database
* Insert / Update dữ liệu

---

# 4. Chi tiết implementation

## 4.1 Parser flow

### Bước 1: Tiền xử lý

* Normalize markdown
* Loại bỏ code block

---

### Bước 2: Parse cấu trúc

* Parse heading
* Parse section
* Parse YAML front matter

---

### Bước 3: Extract metadata

Ưu tiên:

1. YAML front matter
2. Infer từ sourcePath

---

### Bước 4: Extract checklist item

Regex:

```text
^\s*[-*]\s*(\[[ xX]\])?
```

Output:

* checklist_item_count

---

### Bước 5: Detect perspective

Mapping:

| Keyword                | Perspective |
| ---------------------- | ----------- |
| security, auth, secret | security    |
| test, qa               | test        |
| performance, latency   | performance |

Nguồn:

* section title
* checklist content

---

### Bước 6: Aggregate

* has_security_perspective
* has_test_perspective
* has_performance_perspective
* perspective_count
* checklist_item_count

---

### Bước 7: Validation

| Rule                    | Severity |
| ----------------------- | -------- |
| Empty content           | FAILED   |
| Missing ticket_id       | WARNING  |
| No checklist item       | WARNING  |
| No perspective detected | WARNING  |

---

### Bước 8: Build ParsedArtifact

```json
{
  "ticket_id": "...",
  "artifact_type": "review_checklist",
  "parsedSummary": {...},
  "parse_status": "...",
  "warnings": [],
  "errors": []
}
```

---

## 4.2 Storage flow

### Input

```text
ParsedArtifact
```

---

### Output

```text
tbl_fact_artifact_snapshot
```

---

### Mapping

* Mapping theo tên field tương ứng giữa:

  * ParsedArtifact
  * Database schema

---

### Upsert rule

Key:

```text
(ticket_id, artifact_type)
```

Logic:

```text
IF source_hash không đổi → không update
ELSE → update record
```

---

# 5. Error handling

## 5.1 Parser

* Không throw exception với lỗi dữ liệu
* Trả về:

  * warnings
  * errors

---

## 5.2 Storage

* Vẫn lưu record nếu parse lỗi
* Ghi nhận:

  * parse_status
  * parse_error_reason

---

# 6. Test strategy

## 6.1 Unit test (Parser)

* Checklist extraction
* Perspective detection
* Metadata extraction
* Validation rules

---

## 6.2 Integration test

Flow:

```text
Input content
    ↓
Parser
    ↓
Storage
    ↓
Database
```

---

## 6.3 Idempotency test

* Không tạo duplicate record khi source_hash không đổi

---

# 7. Deliverables

* ReviewChecklistMarkdownParser
* ArtifactStorageService
* Unit test
* Integration test

---

# 8. Ràng buộc

* Parser không phụ thuộc DB
* Không thêm field ngoài schema
* Không thay đổi contract của `tbl_fact_artifact_snapshot`

---

# 9. Kết luận

Implementation gồm 2 phần:

1. Parser:

   * xử lý markdown
   * trả về `ParsedArtifact`

2. Storage:

   * persist dữ liệu vào database

Đảm bảo:

* Tách biệt trách nhiệm
* Dễ test
* Dễ mở rộng
