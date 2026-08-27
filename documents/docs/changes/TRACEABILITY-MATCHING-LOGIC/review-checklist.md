# Review Checklist - TRACEABILITY-MATCHING-LOGIC

**Ticket ID**: TRACEABILITY-MATCHING-LOGIC
**Create date**: 2026-06-23
**Author**: OpenAI
**Update date**: 2026-06-23

## 1. Specification / AC Matching

| AC ID                             | Review Point                                              | Severity | Result |
| --------------------------------- | --------------------------------------------------------- | -------- | ------ |
| AC-TRACEABILITY-MATCHING-LOGIC-1  | Ticket is correctly linked to artifacts                   | Blocker  | [ ]    |
| AC-TRACEABILITY-MATCHING-LOGIC-2  | Ticket is correctly linked to PR                          | Blocker  | [ ]    |
| AC-TRACEABILITY-MATCHING-LOGIC-3  | Commit links are displayed but excluded from completeness | Major    | [ ]    |
| AC-TRACEABILITY-MATCHING-LOGIC-4  | PR is correctly linked to CI runs                         | Blocker  | [ ]    |
| AC-TRACEABILITY-MATCHING-LOGIC-5  | Completeness calculation follows approved formula         | Blocker  | [ ]    |
| AC-TRACEABILITY-MATCHING-LOGIC-6  | Missing links remain visible                              | Major    | [ ]    |
| AC-TRACEABILITY-MATCHING-LOGIC-7  | Timeline ordering is deterministic                        | Major    | [ ]    |
| AC-TRACEABILITY-MATCHING-LOGIC-8  | Existing tbl_ tables are reused only                      | Blocker  | [ ]    |
| AC-TRACEABILITY-MATCHING-LOGIC-9  | Confidence level mapping is correct                       | Major    | [ ]    |
| AC-TRACEABILITY-MATCHING-LOGIC-10 | View remains read-only                                    | Blocker  | [ ]    |

### 1.1 AC Traceability Table

| AC ID                             | Specification Summary | Implementation Area     | Required Evidence | Status |
| --------------------------------- | --------------------- | ----------------------- | ----------------- | ------ |
| AC-TRACEABILITY-MATCHING-LOGIC-1  | Ticket ↔ Artifact     | Query Adapter           | API + DB Test     | [ ]    |
| AC-TRACEABILITY-MATCHING-LOGIC-2  | Ticket ↔ PR           | Query Adapter           | API + DB Test     | [ ]    |
| AC-TRACEABILITY-MATCHING-LOGIC-3  | Commit exclusion rule | Completeness Calculator | Unit Test         | [ ]    |
| AC-TRACEABILITY-MATCHING-LOGIC-4  | PR ↔ CI               | Query Adapter           | API + DB Test     | [ ]    |
| AC-TRACEABILITY-MATCHING-LOGIC-5  | Completeness          | Service Layer           | Unit Test         | [ ]    |
| AC-TRACEABILITY-MATCHING-LOGIC-6  | Broken Links          | Service Layer           | Black-box Test    | [ ]    |
| AC-TRACEABILITY-MATCHING-LOGIC-7  | Timeline              | Service Layer           | Unit/API Test     | [ ]    |
| AC-TRACEABILITY-MATCHING-LOGIC-8  | Reuse Existing Tables | Persistence Review      | Source Review     | [ ]    |
| AC-TRACEABILITY-MATCHING-LOGIC-9  | Confidence            | Service Layer           | Unit Test         | [ ]    |
| AC-TRACEABILITY-MATCHING-LOGIC-10 | Read-only             | FE/BE Review            | Black-box Test    | [ ]    |

---

## 2. General System Review

### 2.1 Number / Input Check

* [ ] Completeness percentage calculation is correct
* [ ] Null ticketId handling exists
* [ ] Empty ticketId handling exists
* [ ] Invalid UUID handling exists
* [ ] Percentage rounding rule is explicit

### 2.2 Character Type / Encoding / Locale

* [ ] UTF-8 preserved
* [ ] Ticket IDs are not translated
* [ ] Commit hashes remain unchanged
* [ ] Branch names remain unchanged
* [ ] No mojibake

### 2.3 Literal / Magic Number

* [ ] Completeness denominator is not hardcoded without explanation
* [ ] Confidence values use approved constants
* [ ] Severity values use approved constants

### 2.4 Operation / Maintainability

* [ ] traceId logged
* [ ] Missing evidence visible
* [ ] Read model isolated from write model
* [ ] No duplicated matching logic

---

## 3. FE Review

| item                      | result | note |
| ------------------------- | ------ | ---- |
| Traceability page renders | [ ]    |      |
| Timeline renders          | [ ]    |      |
| Broken links visible      | [ ]    |      |
| Completeness visible      | [ ]    |      |
| Read-only UI enforced     | [ ]    |      |
| No edit button            | [ ]    |      |
| No repair button          | [ ]    |      |

---

## 4. BE/API Review

| item                            | result | note |
| ------------------------------- | ------ | ---- |
| No invented methods used        | [ ]    |      |
| No invented APIs used           | [ ]    |      |
| Existing persistence reused     | [ ]    |      |
| Completeness calculator correct | [ ]    |      |
| Broken-link classifier correct  | [ ]    |      |
| Timeline ordering deterministic | [ ]    |      |

---

## 5. DB / Migration Review

| item                             | result | note |
| -------------------------------- | ------ | ---- |
| Reuse tbl_fact_traceability_link | [ ]    |      |
| Reuse tbl_fact_artifact_snapshot | [ ]    |      |
| Reuse tbl_fact_pull_request      | [ ]    |      |
| Reuse tbl_fact_ci_run            | [ ]    |      |
| No new table introduced          | [ ]    |      |
| No destructive migration         | [ ]    |      |

---

## 6. Security / Privacy Review

| item                         | result | note |
| ---------------------------- | ------ | ---- |
| No raw markdown persisted    | [ ]    |      |
| No raw source code persisted | [ ]    |      |
| No secret stored             | [ ]    |      |
| No token stored              | [ ]    |      |
| Read-only endpoint           | [ ]    |      |
| Access control enforced      | [ ]    |      |

---

## 7. Operation / Maintenance Review

| item                             | result | note |
| -------------------------------- | ------ | ---- |
| traceId available                | [ ]    |      |
| Broken-link visibility preserved | [ ]    |      |
| Timeline observable              | [ ]    |      |
| Logging sufficient               | [ ]    |      |
| Future rule changes isolated     | [ ]    |      |

---

## 8. Test Review

| item                    | result | note |
| ----------------------- | ------ | ---- |
| Unit tests exist        | [ ]    |      |
| Integration tests exist | [ ]    |      |
| Black-box tests exist   | [ ]    |      |
| Completeness tested     | [ ]    |      |
| Broken links tested     | [ ]    |      |
| Permission tested       | [ ]    |      |
| Timeline tested         | [ ]    |      |

---

## 9. Documentation / Traceability Review

| item                       | result | note |
| -------------------------- | ------ | ---- |
| Spec aligned               | [ ]    |      |
| Context aligned            | [ ]    |      |
| Impact analysis aligned    | [ ]    |      |
| Open Issues documented     | [ ]    |      |
| Human decisions documented | [ ]    |      |

---

## 10. Release / Rollback Review

| item                           | result | note |
| ------------------------------ | ------ | ---- |
| Rollback is code-only          | [ ]    |      |
| No DB rollback required        | [ ]    |      |
| Existing collectors unaffected | [ ]    |      |
| Existing ingestion unaffected  | [ ]    |      |

---

## Severity Definition

| severity       | meaning                 | required action         |
| -------------- | ----------------------- | ----------------------- |
| Blocker        | Cannot be released      | Must fix                |
| Major          | High probability bug    | Fix or accepted risk    |
| Minor          | Improvement             | Optional                |
| Question       | Human decision required | Open Issue              |
| False Positive | Invalid finding         | Record rejection reason |
| Accepted Risk  | Accepted risk           | Record owner and impact |
