# Review Checklist

**Ticket ID**: ARTIFACT-SCANNER   
**Create date**: 2026-06-16   
**Author**: nk_trung      
**Update date**: 2026-06-16    

## 1. Specification/AC Matching
| AC ID | Review Point | Severity | Result |
|---|---|---|---|
| AC-ARTIFACT-SCANNER-01 | The scanner correctly identifies the 8 required files for each ticket, including `blackbox-testcases.md`. | Blocker | TODO |
| AC-ARTIFACT-SCANNER-02 | The scanner correctly infers `ticket_id` from a valid path and does not incorrectly infer it from an invalid path. | Blocker | TODO |
| AC-ARTIFACT-SCANNER-03 | The scanner correctly maps `artifact_type` based on the artifact type configuration/seed. | Blocker | TODO |
| AC-ARTIFACT-SCANNER-04 | The scanner correctly reflects whether required artifacts exist or are missing for each ticket. | Blocker | TODO |
| AC-ARTIFACT-SCANNER-05 | The snapshot stores all minimum required metadata, including path, hash, size, updated time, scan status, and scan message. | Major | TODO |
| AC-ARTIFACT-SCANNER-06 | The scanner correctly detects new artifacts or hash changes. | Major | TODO |
| AC-ARTIFACT-SCANNER-07 | `need_parse` is set correctly when a file is new/changed and is not set incorrectly when there is no change. | Major | TODO |
| AC-ARTIFACT-SCANNER-08 | Phase0 only scans basic metadata and does not parse content. | Major | TODO |
| AC-ARTIFACT-SCANNER-09 | Each scan correctly writes a run log to the V4 connector run mechanism. | Major | TODO |
| AC-ARTIFACT-SCANNER-10 | The scanner does not store full Markdown content in the DB; the parser rereads the source file from the repository using scanner metadata. | Blocker | TODO |
| AC-ARTIFACT-SCANNER-11 | The API/manual test view can display the scan summary and artifact result for review/operation purposes. | Major | TODO |

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

### 2.3. Literal / Magic Number

- [ ] No hard-coded business code value
- [ ] Enum/constant/master used correctly
- [ ] Clear mapping display/internal value

### 2.4. Operation / Maintainability

- [ ] Sufficient logs for incident investigation
- [ ] Correlation ID/request ID if needed
- [ ] Retry/double execution considered
- [ ] Clear rollback/manual recovery
- [ ] Configuration not hard-coded

## 3. API / Manual Review

- [ ] API/manual view is placed in the appropriate test/operation flow.
- [ ] Only the `endpoints` helper is used; no direct `fetch` calls.
- [ ] Minimum loading/error/empty states are available.
- [ ] Run summary and artifact result are displayed correctly.
- [ ] i18n keys are used for labels/messages if a UI exists.

## 4. BE/API Review

- [ ] Scanner boundary is not mixed with parser behavior.
- [ ] API does not depend on legacy tables.
- [ ] DTO/API response is sufficient for the manual test view.
- [ ] `FULL` and `TICKET_SCOPED` are supported correctly for MVP v1.
- [ ] `CHANGED_FILES_SCOPED` is not pulled into the main implementation scope.

## 5. DB/Migration Review

- [ ] Only V4 tables are used.
- [ ] Phase0 artifact types are seeded.
- [ ] `size_bytes`, `scan_status`, `scan_message`, and `need_parse` are added.
- [ ] `vw_artifact_inventory_current` is created.
- [ ] No new inventory table is created.

## 6. Security/Privacy Review

- [ ] Full Markdown content is not stored.
- [ ] Secrets/tokens/raw content are not logged.
- [ ] Manual scanner run/view permissions are appropriate for internal use.
- [ ] Unnecessary data is not exposed through the API/manual test.

## 7. Operation/Maintenance Review

- [ ] `unknown ticket` is only a warning and does not fail the entire run.
- [ ] Run history is sufficient for debugging.
- [ ] `scan_message` is clear enough for Data Ops/dev to understand.
- [ ] Current inventory can be queried easily through the view.

## 8. Test Review

- [ ] There are tests for all 8 required files.
- [ ] There is a test for missing artifacts.
- [ ] There is a test for unknown tickets.
- [ ] There is a test for hash change -> `need_parse`.
- [ ] There is a test for Phase0 metadata-only behavior.
- [ ] There is a test for run/result rendering.

## 9. Documentation/Traceability Review

- [ ] Context/rules/impl-plan match the spec-pack.
- [ ] Self-review/test-plan/test-results/report have appropriate skeletons.
- [ ] No open blocker decision remains for Phase 2.

## 10. Release/Rollback Review

- [ ] Migration has a clear forward-fix/rollback plan.
- [ ] If the scanner has a local error, the run can still be partial.
- [ ] Existing collectors/parsers outside the ticket scope are not affected.

## Severity Definition
| severity | meaning | required action |
|---|---|---|
| Blocker | Cannot be released | Must fix |
| Major | High probability of becoming a bug | Fix or accepted risk |
| Minor | Minor improvement | Optional |
| Question | Spec confirmation required | Open Issue |
| False Positive | Incorrect Report | Record Reason for Rejection |
| Accepted Risk | Accepted Risk | Record Impact/Owner/Deadline |