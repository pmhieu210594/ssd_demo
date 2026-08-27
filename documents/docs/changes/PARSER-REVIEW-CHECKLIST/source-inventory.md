# PARSER-REVIEW-CHECKLIST source-inventory.md

**Ticket ID**: PARSER-REVIEW-CHECKLIST
**Create date**: 2026-06-24
**Author**: ChatGPT
**Update date**: 2026-06-24

---

## 1. Mục tiêu

Liệt kê toàn bộ:

* Source liên quan trực tiếp
* Source liên quan gián tiếp
* Mapping giữa source ↔ chức năng

---

## 2. Source Structure (Hiện tại)

### 2.1 Data Source (Markdown)

```id="src_data"
docs/changes/<TICKET>/
 ├── spec-pack.md
 ├── context.md
 ├── review-checklist.md
 ├── blackbox-review-checklist.md
```

---

### 2.2 Documentation

```id="src_docs"
docs/architecture/*
docs/standards/*
.claude/rules/*
```

---

## 3. Target Source (Sẽ tạo mới)

### 3.1 Parser Module

```id="src_parser"
src/parser/
 ├── reviewChecklistParser.ts
 ├── yamlExtractor.ts
 ├── checklistExtractor.ts
 ├── perspectiveDetector.ts
 ├── aggregator.ts
 └── types.ts
```

---

### 3.2 Test Module

```id="src_test"
tests/parser/
 ├── yamlExtractor.test.ts
 ├── checklistExtractor.test.ts
 ├── perspectiveDetector.test.ts
 ├── parser.integration.test.ts
```

---

## 4. Mapping Source → Responsibility

| File                     | Responsibility             |
| ------------------------ | -------------------------- |
| reviewChecklistParser.ts | Entry point parser         |
| yamlExtractor.ts         | Parse YAML front matter    |
| checklistExtractor.ts    | Extract checklist items    |
| perspectiveDetector.ts   | Detect keyword perspective |
| aggregator.ts            | Combine result → DTO       |
| types.ts                 | Define DTO & types         |

---

## 5. Mapping Source → Acceptance Criteria

| AC                      | File                   |
| ----------------------- | ---------------------- |
| Extract checklist count | checklistExtractor.ts  |
| Detect perspective      | perspectiveDetector.ts |
| Handle YAML             | yamlExtractor.ts       |
| Aggregate output        | aggregator.ts          |

---

## 6. Caller / Callee Mapping

### 6.1 Caller (Upstream)

* Data ingestion pipeline
* Batch job đọc markdown

---

### 6.2 Callee (Downstream)

* Parser functions:

```id="callee"
parseReviewChecklist()
 ├── extractYamlFrontMatter()
 ├── extractChecklistItems()
 ├── detectPerspectives()
 └── aggregateResult()
```

---

## 7. External Dependency

### 7.1 Allowed

* Logging util
* String util
* Regex

---

### 7.2 Not Allowed

* Database
* API
* External service

---

## 8. Test Source Mapping

| Test Type        | File                                        |
| ---------------- | ------------------------------------------- |
| Unit Test        | yamlExtractor.test.ts                       |
| Unit Test        | checklistExtractor.test.ts                  |
| Unit Test        | perspectiveDetector.test.ts                 |
| Integration Test | parser.integration.test.ts                  |
| Blackbox Test    | docs/changes/*/blackbox-review-checklist.md |

---

## 9. Configuration / Setting

* Không yêu cầu config đặc biệt
* Parser hoạt động độc lập

---

## 10. Dependency Graph (Logical)

```id="dep_graph"
reviewChecklistParser
        ↓
 ├── yamlExtractor
 ├── checklistExtractor
 ├── perspectiveDetector
        ↓
     aggregator
```

---

## 11. Non-impact Source

Các source KHÔNG bị ảnh hưởng:

* FE code
* API controller
* Existing business service
* DB schema
* Migration script

---

## 12. Kết luận

* Parser module là **isolated component**
* Source ảnh hưởng:

  * chủ yếu là module mới
* Không modify source hiện có
