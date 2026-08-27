# Brainstorm — Parser review-checklist.md (Final Aligned)

---

## 1. Mục tiêu suy nghĩ

* Hiểu đúng bản chất parser trong **data pipeline**
* Không xem parser là function đơn lẻ
* Align với:

  * Git-based ingestion
  * Landing layer
  * DWH (tbl_fact_artifact_snapshot)

---

## 2. Reframing Problem (QUAN TRỌNG)

### ✅ Đúng (theo thiết kế)

* Parser = **một bước trong data pipeline**

```text
Git → Connector → Landing → Parser → DWH
```

👉 Parser là:
→ **Data transformation stage**

---

## 3. Core Responsibility

Parser phải:

1. Nhận input từ upstream (Git connector / landing)
2. Xử lý markdown → metadata
3. Ghi kết quả vào fact table
4. Đảm bảo:

   * idempotency
   * lineage
   * data quality

---

## 4. Input Thinking

### Input KHÔNG chỉ là file path

Parser thực nhận:

* source_path
* file_content
* source_hash
* updated_at

👉 Insight:

* Parser không tự đọc file từ disk
* Parser **consume data đã collect**

---

## 5. SSOT Thinking

### Nguyên tắc:

* Git repository = SSOT
* Database = derived

👉 Implication:

* Không được:

  * đọc file từ DB
  * dùng DB làm source truth

* Parser luôn:

  * trust Git data

---

## 6. Data Flow Thinking

### Pipeline đầy đủ:

```text
Git Repo
  ↓
Git Connector
  ↓
Landing Layer (metadata only)
  ↓
Parser
  ↓
tbl_fact_artifact_snapshot
```

---

### Key realization:

* Parser không control upstream
* Parser không control downstream
* Parser chỉ:
  → **transform + enrich**

---

## 7. Parsing Logic Thinking

### Core logic vẫn giữ đơn giản:

1. Preprocess markdown
2. Detect checklist
3. Detect perspective
4. Aggregate

---

### Không nên:

* parse full AST markdown
* over-engineer

---

## 8. Perspective Detection Thinking

### Requirement:

* Detect:

  * security
  * test
  * performance

---

### Strategy:

#### Level 1 — keyword

* simple
* deterministic

#### Không cần:

* NLP
* ML

---

### Trade-off:

| Option         | Decision                       |
| -------------- | ------------------------------ |
| keyword only   | ✅ chọn                         |
| template-aware | ❌ chưa cần (theo design final) |

👉 Vì:

* Thiết kế final không enforce template parsing

---

## 9. Checklist Counting Thinking

### Rule:

Count:

* `-`
* `*`
* `[ ]`
* `[x]`

---

### Decision:

* Accept noise nhẹ
* Không validate semantic

---

## 10. Parse Status Thinking

### Quan trọng vì ảnh hưởng Data Quality

| Case           | Status  |
| -------------- | ------- |
| File missing   | failed  |
| Cannot parse   | failed  |
| Nội dung thiếu | warning |
| Parse OK       | success |

---

### Insight:

* Không drop record khi lỗi
* Luôn ghi vào fact table

---

## 11. Data Modeling Thinking

### Output không phải JSON thuần

→ Output = **fact record**

---

### Mapping:

Parser output → `tbl_fact_artifact_snapshot`

---

### Implication:

* Field phải align DB schema
* Không free-form JSON

---

## 12. Data Lineage Thinking

### Bắt buộc:

* source_path
* source_hash
* parser_version
* job_run_id
* parsed_at

---

### Ý nghĩa:

* trace back dữ liệu
* debug pipeline
* audit

---

## 13. Idempotency Thinking

### Rule:

* Nếu `source_hash` không đổi:
  → không parse lại

---

### Insight:

* Parser không cần compare content
* Chỉ cần hash

---

## 14. Architecture Thinking

Parser thuộc:

* Application layer (processing logic)

Không thuộc:

* Controller
* Infrastructure

---

### Dependency:

* Không DB dependency
* Không external API

→ Pure processing unit

---

## 15. Storage Thinking

### Fixed:

```text
tbl_fact_artifact_snapshot
```

---

### Không được:

* Tạo bảng mới
* Lưu raw markdown

---

### Design pattern:

* Wide table
* Nullable field theo artifact_type

---

## 16. Data Quality Thinking

### Nguyên tắc:

* Parse lỗi vẫn ghi record
* Không silent failure

---

### Insight:

* Dữ liệu lỗi cũng là signal

---

## 17. Risk Exploration

### 🔴 High

1. Sai mapping perspective → sai scoring
2. Không ghi lineage → mất traceability

---

### 🟠 Medium

3. Markdown không chuẩn
4. Keyword thiếu

---

### 🟡 Low

5. Encoding

---

## 18. Design Decisions (Final)

| Area        | Decision               |
| ----------- | ---------------------- |
| Parser role | pipeline stage         |
| Input       | từ connector           |
| Output      | fact table             |
| Storage     | tbl_fact_artifact_snapshot |
| SSOT        | Git                    |
| Idempotency | source_hash            |
| Parsing     | regex + keyword        |
| NLP         | không dùng             |

---

## 19. Things NOT to Do

* Không đọc DB làm source
* Không lưu raw markdown
* Không tạo bảng mới
* Không parse quá phức tạp

---

## 20. Open Questions

* Keyword có cần config external không?
* Có cần versioning parser logic không?

---

## 21. Future Thinking

* Quality scoring
* Missing perspective detection
* Template enforcement

---

# Kết luận

Parser không phải là:
→ utility đọc markdown

Mà là:
→ **data transformation component trong pipeline ingestion**

Tư duy đúng:

* Pipeline-first
* Metadata-first
* SSOT-first
* Lineage-aware
