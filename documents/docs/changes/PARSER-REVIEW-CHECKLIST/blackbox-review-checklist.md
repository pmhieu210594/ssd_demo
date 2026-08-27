# PARSER-REVIEW-CHECKLIST blackbox-review-checklist.md

**Ticket ID**: PARSER-REVIEW-CHECKLIST
**Create date**: 2026-06-25
**Author**: ChatGPT

---

# 1. Mục tiêu

Checklist để review chất lượng black-box test:

* Đảm bảo test bám sát specification
* Không phụ thuộc implementation
* Cover đầy đủ acceptance criteria (AC)
* Có đủ test case quan trọng

---

# 2. Coverage checklist

## 2.1 Acceptance Criteria coverage

| Check                                    | Status |
| ---------------------------------------- | ------ |
| Mỗi AC có ít nhất 2 test case            | ☐      |
| AC-01 (checklist extraction) được cover  | ☐      |
| AC-02 (perspective detection) được cover | ☐      |
| AC-03 (ticket_id extraction) được cover  | ☐      |
| AC-04 (empty content) được cover         | ☐      |
| AC-05 (invalid format) được cover        | ☐      |
| AC-06 (DTO validation) được cover        | ☐      |

---

## 2.2 Test type coverage

| Type           | Check                  | Status |
| -------------- | ---------------------- | ------ |
| Normal case    | Có test happy path     | ☐      |
| Error case     | Có test lỗi            | ☐      |
| Boundary value | Có test edge case      | ☐      |
| Negative case  | Có test false positive | ☐      |

---

# 3. Test case quality

## 3.1 Input / Output clarity

| Check                               | Status |
| ----------------------------------- | ------ |
| Input rõ ràng, cụ thể               | ☐      |
| Expected result measurable          | ☐      |
| Không mơ hồ (e.g. "đúng", "hợp lệ") | ☐      |

---

## 3.2 Independence

| Check                         | Status |
| ----------------------------- | ------ |
| Test không phụ thuộc lẫn nhau | ☐      |
| Có thể chạy độc lập           | ☐      |

---

## 3.3 Black-box principle

| Check                                  | Status |
| -------------------------------------- | ------ |
| Không sử dụng chi tiết implementation  | ☐      |
| Không phụ thuộc regex / internal logic | ☐      |
| Chỉ dựa vào input/output               | ☐      |

---

# 4. Test data quality

| Check                       | Status |
| --------------------------- | ------ |
| Có file test-data.md riêng  | ☐      |
| Data reusable               | ☐      |
| Có mapping data ↔ test case | ☐      |
| Có data cho error case      | ☐      |
| Có data cho boundary case   | ☐      |

---

# 5. Priority & risk

| Check                       | Status |
| --------------------------- | ------ |
| Có phân loại P0 / P1 / P2   | ☐      |
| P0 cover core functionality | ☐      |
| P1 cover edge cases         | ☐      |
| P2 cover rare cases         | ☐      |

---

# 6. Operation viewpoint

| Check                     | Status |
| ------------------------- | ------ |
| Có test batch processing  | ☐      |
| Có test webhook / trigger | ☐      |
| Không crash toàn flow     | ☐      |

---

# 7. Logging / audit

| Check                                         | Status |
| --------------------------------------------- | ------ |
| Có test log SUCCESS                           | ☐      |
| Có test log ERROR                             | ☐      |
| Có test parse_status (SUCCESS/WARNING/FAILED) | ☐      |

---

# 8. Consistency

| Check                              | Status |
| ---------------------------------- | ------ |
| Test case ↔ test data mapping đúng | ☐      |
| Không có test dư / duplicate       | ☐      |
| Naming consistent                  | ☐      |

---

# 9. Review kết luận

## 9.1 Summary

* Coverage: ☐ Good / ☐ Partial / ☐ Missing
* Quality: ☐ Good / ☐ Needs improvement

---

## 9.2 Issues (nếu có)

* Issue 1:
* Issue 2:

---

## 9.3 Recommendation

* [ ] Bổ sung test case
* [ ] Cải thiện expected result
* [ ] Tách test data rõ hơn
* [ ] Khác:

---

# 10. Kết luận

Black-box test được coi là đạt khi:

* Cover đầy đủ AC
* Có đủ normal / error / boundary case
* Không phụ thuộc implementation
* Expected result rõ ràng, kiểm chứng được
* Có thể dùng trực tiếp cho QA / UAT / audit
