# Thiết kế chức năng: Parser review-checklist.md

---

# 1. Mục tiêu

Xây dựng chức năng parser `review-checklist.md` nhằm:

* Parse markdown → structured data

* Trích xuất góc nhìn review (review perspectives)

* Chuẩn hóa metadata phục vụ:

  * Evidence Quality Score
  * Review coverage analysis
  * Data Quality

* Lưu kết quả vào hệ thống dữ liệu (`tbl_fact_artifact_snapshot`)

---

# 2. Nguyên tắc thiết kế

## 2.1 Kiến trúc tổng thể

Parser tuân theo pattern chung của hệ thống:

* Stateless
* Input: raw markdown content (string)
* Không phụ thuộc:

  * Git repository
  * Database

Tách biệt rõ:

* Parsing
* Validation
* Aggregation
* Persistence

---

## 2.2 Flow tổng thể

```text
Git Repository (SSOT)
        ↓
Git Connector / File Loader
        ↓
Raw Markdown Content (string)
        ↓
Markdown Parser Core
        ↓
ReviewChecklistMarkdownParser
        ↓
ParsedArtifact (in-memory)
        ↓
ArtifactStorageService
        ↓
tbl_fact_artifact_snapshot (DB)
```

---

# 3. Input / Output

## 3.1 Input

```java
parse(String content, String sourcePath, Map<String, Object> metadata)
```

### Nguồn dữ liệu

* Git Connector
* File loader

Parser không thực hiện việc đọc file

---

## 3.2 Output trung gian (Parser)

```java
ParsedArtifact
```

### Cấu trúc

```json
{
  "ticket_id": "ABC-123",
  "artifact_type": "review_checklist",

  "parsedSummary": {
    "checklist_item_count": 7,
    "perspective_count": 2,
    "has_security_perspective": true,
    "has_test_perspective": true,
    "has_performance_perspective": false
  },

  "parseStatus": "SUCCESS | WARNING | FAILED",
  "warnings": [],
  "errors": []
}
```

---

# 4. Parsing Flow chi tiết

## 4.1 Bước 1: Core parsing

* Normalize markdown
* Parse:

  * heading
  * section
  * YAML front matter

Output:

```text
MarkdownDocument
```

---

## 4.2 Bước 2: Extract metadata

Ưu tiên:

1. YAML front matter
2. Header
3. Infer từ sourcePath

Ví dụ:

```yaml
ticket_id: ABC-123
artifact_type: review_checklist
```

---

## 4.3 Bước 3: Checklist extraction

Detect các dòng:

* `- [ ]`
* `- [x]`
* `- item`
* `* item`

Regex:

```text
^\s*[-*]\s*(\[[ xX]\])?
```

Output:

* checklist_item_count

---

## 4.4 Bước 4: Perspective detection

### Keyword mapping

| Keyword              | Perspective |
| -------------------- | ----------- |
| security, auth       | security    |
| test, qa             | test        |
| performance, latency | performance |

### Nguồn detect:

* Section title
* Nội dung checklist

---

## 4.5 Bước 5: Aggregation

Tổng hợp:

* has_security_perspective
* has_test_perspective
* has_performance_perspective
* perspective_count
* checklist_item_count

---

## 4.6 Bước 6: Validation

| Rule                     | Severity |
| ------------------------ | -------- |
| Empty content            | FAILED   |
| Missing ticket_id        | WARNING  |
| Không có checklist item  | WARNING  |
| Không detect perspective | WARNING  |

---

## 4.7 Bước 7: Parse Status

```text
SUCCESS  → không error  
WARNING  → có warning  
FAILED   → có error  
```

---

## 4.8 Bước 8: Build ParsedArtifact

Parser trả về:

```java
ParsedArtifact
```

---

# 5. Storage Layer

## 5.1 Trách nhiệm

`ArtifactStorageService`:

* Nhận `ParsedArtifact`
* Map sang schema database
* Insert / Update dữ liệu

---

## 5.2 Interface

```java
interface ArtifactStorageService {
    void save(ParsedArtifact artifact);
}
```

---

## 5.3 Mapping sang database

### Bảng:

```text
tbl_fact_artifact_snapshot
```

---

### Mapping chi tiết

| ParsedArtifact field                      | DB column                   |
| ----------------------------------------- | --------------------------- |
| ticket_id                                 | ticket_id                   |
| artifact_type                             | artifact_type               |
| parseStatus                               | parse_status                |
| errors                                    | parse_error_reason          |
| sourcePath                                | source_path                 |
| metadata.source_hash                      | source_hash                 |
| parsedSummary.checklist_item_count        | checklist_item_count        |
| parsedSummary.perspective_count           | perspective_count           |
| parsedSummary.has_security_perspective    | has_security_perspective    |
| parsedSummary.has_test_perspective        | has_test_perspective        |
| parsedSummary.has_performance_perspective | has_performance_perspective |

---

## 5.4 Upsert rule

Key:

```text
(ticket_id, artifact_type)
```

Logic:

```text
IF source_hash không đổi → skip  
ELSE → update record  
```

---

# 6. Data Lineage

| Field          | Ý nghĩa         |
| -------------- | --------------- |
| source_path    | đường dẫn file  |
| source_hash    | hash file       |
| parser_version | version parser  |
| parsed_at      | thời điểm parse |

---

# 7. Ràng buộc

## 7.1 Metadata First

* Không lưu raw markdown

---

## 7.2 Repo as SSOT

* Repository là nguồn chính
* Database là dữ liệu phái sinh

---

## 7.3 Idempotency

* source_hash không đổi → không parse lại

---

## 7.4 Data Quality

* Parse lỗi vẫn lưu record

---

# 8. Hạn chế (MVP)

* Keyword-based detection
* Không NLP
* Không đánh giá chất lượng checklist

---

# 9. Mở rộng

* NLP-based perspective detection
* Checklist quality scoring
* Mapping checklist → issue / defect

---

# Kết luận

Hệ thống gồm 2 bước rõ ràng:

1. Parser:

   * xử lý markdown
   * tạo `ParsedArtifact`

2. Storage:

   * persist vào `tbl_fact_artifact_snapshot`

Thiết kế đảm bảo:

* Tách biệt trách nhiệm rõ ràng
* Dễ test
* Dễ mở rộng
* Phù hợp với kiến trúc parser chung của hệ thống
