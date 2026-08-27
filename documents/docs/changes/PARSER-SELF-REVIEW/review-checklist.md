# Review Checklist

**Ticket ID**: PARSER-SELF-REVIEW  
**Create date**: 2026-06-22  
**Author**: nk_trung     
**Update date**: 2026-06-22

## 1. Specification/AC Matching
| AC ID | Review Point | Severity | Result |
|---|---|---|---|
| AC-PARSER-SELF-REVIEW-1 | Canonical header/metadata parsing is stable and deterministic | Blocker |  |
| AC-PARSER-SELF-REVIEW-2 | Ticket identity and source path inference are correct | Major |  |
| AC-PARSER-SELF-REVIEW-3 | Canonical 11-section detection/order is respected | Blocker |  |
| AC-PARSER-SELF-REVIEW-4 | Required/recommended/optional handling matches spec-pack | Blocker |  |
| AC-PARSER-SELF-REVIEW-5 | Table parsing and AC extraction are correct | Major |  |
| AC-PARSER-SELF-REVIEW-6 | Free-text / evidence blocks are preserved as required | Major |  |
| AC-PARSER-SELF-REVIEW-7 | Verdict normalization is limited to approved values | Blocker |  |
| AC-PARSER-SELF-REVIEW-8 | Placeholder / incomplete markers are flagged correctly | Major |  |
| AC-PARSER-SELF-REVIEW-9 | Missing section / malformed table behavior matches spec | Blocker |  |
| AC-PARSER-SELF-REVIEW-10 | Idempotency via content hash is preserved | Major |  |

## 2. General System Review

### 2.1. Number/Input Check
- [ ] Clear Numeric Validation
- [ ] Full-width Numbers are Processed or Clearly Not Supported
- [ ] Half-width/Full-width Mixed Numbers are Considered
- [ ] Empty String/Null are Processed
- [ ] Clear Digit/Precision/Scale/Rounding
- [ ] No Overflow/Underflow

### 2.2. Character Type / Encoding / Locale

- [ ] Full-width/half-width/emoji/surrogate pair considered
- [ ] Clear trim rule
- [ ] Unicode normalization if needed
- [ ] No mojibake Shift-JIS/UTF-8
- [ ] Japanese/Vietnamese/English messages are not misspelled
- [ ] UTF-8 Vietnamese spec text is preserved in outputs and examples

### 2.3. Literal / Magic Number

- [ ] No hard-coded business code value
- [ ] Enum/constant/master used correctly
- [ ] Clear mapping display/internal value
- [ ] Canonical section map and alias map are fixed and reviewable

### 2.4. Operation / Maintainability

- [ ] Sufficient logs for incident investigation
- [ ] Correlation ID/request ID if needed
- [ ] Retry/double execution considered
- [ ] Clear rollback/manual recovery
- [ ] Configuration not hard-coded
- [ ] Warning/error summary is understandable without reading raw source

## 3. FE Review
- [ ] No FE change is required for this backend parser phase.
- [ ] No hidden UI dependency is introduced.
- [ ] No new client-side contract is created without explicit need.

## 4. BE/API Review
- [ ] API path is guarded and internal by default.
- [ ] Endpoint is not a generic Markdown/file reader.
- [ ] Alias heading mapping is fixed and canonical.
- [ ] Nested subsections are limited to one level under heading.
- [ ] Missing required sections result in partial parse + warning/incomplete.
- [ ] Section 9 remains optional in the current phase.

## 5. DB/Migration Review
- [ ] No schema migration is introduced in Phase 4 by accident.
- [ ] Existing snapshot/reuse-first pattern is respected.
- [ ] No extra audit table is introduced unless a later phase explicitly requires it.
- [ ] Any persistence impact is traceable to the approved design.
- [ ] Raw text is not duplicated into a new storage shape without approval.

## 6. Security/Privacy Review
- [ ] Raw content is not logged.
- [ ] No sensitive data is copied into debug output.
- [ ] Internal-only exposure remains internal by default.
- [ ] Path/input validation prevents file scope escape.
- [ ] Alias matching does not broaden the accepted attack surface.

## 7. Operation/Maintenance Review
- [ ] Parse status, warning count, and error count are observable.
- [ ] Failure paths are actionable for support or QA.
- [ ] Rollback can be done without breaking other parser flows.
- [ ] No uncontrolled proliferation of records or side effects.
- [ ] The implementation can be maintained without widening the canonical contract.

## 8. Test Review
- [ ] Canonical happy-path fixture is covered.
- [ ] Missing-section fixture is covered.
- [ ] Malformed-table fixture is covered.
- [ ] Nested-subsection boundary fixture is covered.
- [ ] Unicode / CRLF / placeholder boundary cases are covered.
- [ ] Alias-heading fixture is covered.

## 9. Documentation/Traceability Review
- [ ] Spec-pack, context, and impl-plan are aligned.
- [ ] Review checklist matches the implemented AC mapping.
- [ ] Self-review skeleton can be completed without changing the review logic.
- [ ] Human decision items are clearly separated from implementation facts.
- [ ] Source, rules, and template references are traceable.

## 10. Release/Rollback Review
- [ ] Parser can be disabled or bypassed without affecting unrelated flows.
- [ ] No rollback-blocking migration is introduced.
- [ ] Internal-default exposure is preserved.
- [ ] Release notes can describe the scope without ambiguity.
- [ ] A rollback plan exists if alias/nesting handling causes unexpected parse behavior.

## Severity Definition
| severity | meaning | required action |
|---|---|---|
| Blocker | Cannot be released | Must fix |
| Major | High probability of becoming a bug | Fix or accepted risk |
| Minor | Minor improvement | Optional |
| Question | Spec confirmation required | Open Issue |
| False Positive | Incorrect Report | Record Reason for Rejection |
| Accepted Risk | Accepted Risk | Record Impact/Owner/Deadline |
