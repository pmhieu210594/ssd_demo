# PARSER-REVIEW-CHECKLIST review-checklist.md

**Ticket ID**: PARSER-REVIEW-CHECKLIST
**Create date**: 2026-06-24
**Author**: ChatGPT
**Update date**: 2026-06-24

---

## 0. Assumption (IMPORTANT) — UPDATED

* Parser chỉ thực hiện **processing (pure function)**:

  * Nhận input (markdown content + metadata)
  * Trả về `ParsedArtifact`
  * **KHÔNG thực hiện persist DB**

* Persistence được xử lý bởi **storage layer downstream**:

  * `ArtifactStorageService` chịu trách nhiệm ghi dữ liệu vào database
  * Parser **không gọi trực tiếp DB**

* Parser là một bước trong pipeline:

```text
Input → Parser → ParsedArtifact → Storage → DB
```

* Không log raw markdown content (chỉ log metadata + error)

* Các assumption này đảm bảo:

  * Tách biệt rõ responsibility (parser vs storage)
  * Phù hợp với thiết kế tổng thể của hệ thống (theo spec & raw input)

---

## 1. Khớp Specification / AC

### AC Mapping

| AC    | Description             | Check | Severity |
| ----- | ----------------------- | ----- | -------- |
| AC-01 | Extract checklist count | [ ]   | Blocker  |
| AC-02 | Detect perspective      | [ ]   | Blocker  |
| AC-03 | Extract ticket_id       | [ ]   | Blocker  |
| AC-04 | Handle empty content    | [ ]   | Blocker  |
| AC-05 | Handle invalid format   | [ ]   | Major    |
| AC-06 | Output đúng DTO         | [ ]   | Blocker  |

---

## 2. General System Review

* [ ] Parser không có side-effect
* [ ] Parser không gọi DB / API
* [ ] Adapter không chứa logic parsing
* [ ] Layering đúng (Adapter → Parser → DTO)

Severity:

* Sai architecture → Blocker

---

## 3. FE Review

```text
NOT APPLICABLE
```

---

## 4. BE / API Review

* [ ] Parser không expose ra API
* [ ] Không ảnh hưởng controller/service hiện tại
* [ ] Adapter không phá contract hiện có

Severity:

* Break API → Blocker

---

## 5. DB / Migration Review

* [ ] Không thay đổi schema
* [ ] Không insert sai format data
* [ ] Metadata structure consistent

Severity:

* Sai data → Major

---

## 6. Security / Privacy Review

* [ ] Không log raw markdown content
* [ ] Không expose sensitive data
* [ ] Regex không gây ReDoS

Severity:

* Leak data → Blocker
* Regex risk → Major

---

## 7. Operation / Maintenance Review

* [ ] Có log parse start / success / error
* [ ] Error có đủ context (file_path)
* [ ] Không crash toàn job

Severity:

* Crash batch → Blocker

---

## 8. Test Review

* [ ] Unit test checklist extraction
* [ ] Unit test perspective detection
* [ ] Test empty content
* [ ] Test duplicate keyword
* [ ] Test keyword boundary
* [ ] Test invalid markdown

Severity:

* Thiếu test critical → Major

---

## 9. Documentation / Traceability Review

* [ ] Code trace được về AC
* [ ] Mapping AC → test rõ ràng
* [ ] Impl-plan được follow

Severity:

* Không trace được → Major

---

## 10. Release / Rollback Review

* [ ] Deploy không break flow hiện tại
* [ ] Có thể rollback bằng cách remove parser
* [ ] Không ảnh hưởng data cũ

Severity:

* Không rollback được → Blocker

---

## 11. Parser Logic Review (CORE)

### Checklist

* [ ] Detect đúng pattern `- [ ]` và `- [x]`
* [ ] Không detect sai format

---

### Perspective

* [ ] Match keyword đúng
* [ ] Case-insensitive
* [ ] Match word boundary
* [ ] Không detect trong code block
* [ ] Count unique perspective

---

### Ticket ID

* [ ] Extract từ content đúng
* [ ] Fallback từ path đúng

---

### Empty Handling

* [ ] Empty content → failed
* [ ] Không crash

---

### Error Handling

* [ ] Exception → parse_status = failed
* [ ] parse_error_reason có giá trị

Severity:

* Sai parsing → Blocker

---

## 12. Severity Definition

| Level   | Meaning                       |
| ------- | ----------------------------- |
| Blocker | Không thể release             |
| Major   | Có thể release nhưng risk cao |
| Minor   | Nên fix nhưng không critical  |

---

## 13. Final Review Decision

* [ ] PASS
* [ ] FAIL
* [ ] NEED FIX

---

## Kết luận

Checklist này đảm bảo:

* Không lệch spec
* Không sai architecture
* Không thiếu edge case critical
* Có thể dùng trực tiếp cho production review
