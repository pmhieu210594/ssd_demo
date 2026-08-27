# Review Checklist

**Ticket ID**: CUSTOMER  
**Create date**: 2026-06-09  
**Author**: nk_trung                    
**Update date**: 2026-06-10  

## 1. Specification/AC Matching

| ID | severity | check item | AC reference | status | note |
|---|---|---|---|---|---|
| RC-CUS-SPEC-001 | Blocker | Customer Management remains CRUD-only. | AC-CUSTOMER-1..18 | [ ] | No dashboard/KPI/export/restore. |
| RC-CUS-SPEC-002 | Blocker | ADMIN can access Customer function; non-ADMIN cannot. | AC-CUSTOMER-1,14 | [ ] | FE logout redirect + BE 403. |
| RC-CUS-SPEC-003 | Blocker | Customer list excludes Customers under inactive/deleted Organizations. | AC-CUSTOMER-3 | [ ] | BE query must enforce. |
| RC-CUS-SPEC-004 | Blocker | Default list excludes soft-deleted Customers. | AC-CUSTOMER-4 | [ ] | Active default. |
| RC-CUS-SPEC-005 | Major | Search/filter by Organization, alias/name, classification, status. | AC-CUSTOMER-5 | [ ] |  |
| RC-CUS-SPEC-006 | Blocker | Create/edit Organization dropdown only shows active/non-deleted Organizations. | AC-CUSTOMER-7,12 | [ ] |  |
| RC-CUS-SPEC-007 | Blocker | Create/update rejects inactive/deleted Organization. | AC-CUSTOMER-9 | [ ] | BE validation. |
| RC-CUS-SPEC-008 | Blocker | Duplicate alias in same Organization is rejected case-insensitively for non-deleted Customers. | AC-CUSTOMER-15 | [ ] | 409 expected later. |
| RC-CUS-SPEC-009 | Blocker | Stale version is rejected with conflict. | AC-CUSTOMER-16 | [ ] |  |
| RC-CUS-SPEC-010 | Blocker | Customer soft delete cascades to its full child tree. | AC-CUSTOMER-17 | [ ] |  |
| RC-CUS-SPEC-011 | Major | BE returns message key and FE translates `ja/en/vi`. | AC-CUSTOMER-18 | [ ] |  |

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

| ID | severity | check item | status | note |
|---|---|---|---|---|
| RC-CUS-GEN-001 | Blocker | No Organization Management implementation is added in this ticket. | [ ] | Customer may depend on active Organization lookup only. |
| RC-CUS-GEN-002 | Blocker | No Project/Repository child list is added to Customer detail. | [ ] | Out of scope. |
| RC-CUS-GEN-003 | Major | Existing connector/webhook/metrics flows are not changed. | [ ] |  |
| RC-CUS-GEN-004 | Major | Existing architecture layering is followed. | [ ] | Controller -> use case -> port/adapter. |

## 3. FE Review

| ID | severity | check item | status | note |
|---|---|---|---|---|
| RC-CUS-FE-001 | Blocker | Customer route decision is documented before implementation. | [ ] | Route is documented as `/:lang/customers` with `Menu.Customer`. |
| RC-CUS-FE-002 | Blocker | Non-ADMIN Customer access logs out and redirects to `/:lang/login`. | [ ] |  |
| RC-CUS-FE-003 | Major | Customer API uses `EDCAP_FE/src/lib/api.ts`; no direct fetch/axios. | [ ] |  |
| RC-CUS-FE-004 | Major | Organization dropdown excludes inactive/deleted Organizations. | [ ] |  |
| RC-CUS-FE-005 | Major | Customer list/search/filter state matches spec. | [ ] |  |
| RC-CUS-FE-006 | Major | Validation/i18n messages display in current locale. | [ ] | en/ja/vi. |
| RC-CUS-FE-007 | Major | Delete requires confirmation. | [ ] |  |
| RC-CUS-FE-008 | Minor | Empty state is translated and usable. | [ ] |  |

## 4. BE/API Review

| ID | severity | check item | status | note |
|---|---|---|---|---|
| RC-CUS-BE-001 | Blocker | BE enforces ADMIN-only access with standard 403. | [ ] | Do not copy ad-hoc `Map.of("error")` pattern. |
| RC-CUS-BE-002 | Blocker | BE validates Organization active/non-deleted for create/update. | [ ] |  |
| RC-CUS-BE-003 | Blocker | BE list query excludes invalid Organizations and deleted Customers by default. | [ ] |  |
| RC-CUS-BE-004 | Blocker | BE prevents editing deleted Customer. | [ ] |  |
| RC-CUS-BE-005 | Blocker | BE performs cascade soft delete for Customer child tree. | [ ] |  |
| RC-CUS-BE-006 | Major | API status codes are documented and tested. | [ ] | 400/403/404/409 candidates. |
| RC-CUS-BE-007 | Major | Error response contract supports message-key i18n. | [ ] | Ticket keeps `ErrorResponse.message` as the translated key. |

## 5. DB/Migration Review

| ID | severity | check item | status | note |
|---|---|---|---|---|
| RC-CUS-DB-001 | Blocker | `tbl_dim_customer` is not renamed/recreated. | [ ] |  |
| RC-CUS-DB-002 | Blocker | Old V4 migration is not edited in implementation. | [ ] | Use new migration. |
| RC-CUS-DB-003 | Blocker | `deleted_at`, `deleted_by`, `version` are added safely if implemented. | [ ] |  |
| RC-CUS-DB-004 | Blocker | Existing unique constraint is migrated safely to active-scope/case-insensitive uniqueness. | [ ] | Requires pre-check. |
| RC-CUS-DB-005 | Major | Existing data is preserved. | [ ] |  |
| RC-CUS-DB-006 | Major | Cascade delete path is supported by schema/FK relationships. | [ ] |  |

## 6. Security/Privacy Review

| ID | severity | check item | status | note |
|---|---|---|---|---|
| RC-CUS-SEC-001 | Blocker | FE guard is not the only security control; BE enforces role. | [ ] |  |
| RC-CUS-SEC-002 | Major | Logs do not expose secrets or unnecessary customer-sensitive data. | [ ] |  |
| RC-CUS-SEC-003 | Major | TraceId/error handling remains available. | [ ] |  |
| RC-CUS-SEC-004 | Major | Session/auth behavior is not weakened. | [ ] |  |

## 7. Operation/Maintenance Review

| ID | severity | check item | status | note |
|---|---|---|---|---|
| RC-CUS-OPS-001 | Major | Create/update/delete actions are auditable. | [ ] | created/updated/deleted metadata. |
| RC-CUS-OPS-002 | Major | Migration has pre-check/post-check notes. | [ ] | duplicate alias check. |
| RC-CUS-OPS-003 | Minor | Operational errors have enough context via traceId. | [ ] |  |

## 8. Test Review

| ID | severity | check item | status | note |
|---|---|---|---|---|
| RC-CUS-TEST-001 | Blocker | Test plan covers all ACs. | [ ] |  |
| RC-CUS-TEST-002 | Major | BE validation/security/conflict tests are planned. | [ ] |  |
| RC-CUS-TEST-003 | Major | DB migration verification is planned. | [ ] |  |
| RC-CUS-TEST-004 | Major | FE automated tests are explicitly deferred if not implemented. | [ ] | Not accidental omission. |
| RC-CUS-TEST-005 | Major | Blackbox testcases cover permission, duplicate, deleted state, cascade soft delete. | [ ] |  |

## 9. Documentation/Traceability Review

| ID | severity | check item | status | note |
|---|---|---|---|---|
| RC-CUS-DOC-001 | Major | AC mapping remains traceable across spec/context/plan/test. | [ ] |  |
| RC-CUS-DOC-002 | Major | Source assumptions are documented. | [ ] |  |
| RC-CUS-DOC-003 | Minor | Deferred items are clearly listed. | [ ] |  |

## 10. Release/Rollback Review

| ID | severity | check item | status | note |
|---|---|---|---|---|
| RC-CUS-REL-001 | Blocker | Migration rollback/limitation is documented before release. | [ ] |  |
| RC-CUS-REL-002 | Major | Unique index migration failure path is documented. | [ ] |  |
| RC-CUS-REL-003 | Major | App rollback does not require destructive DB rollback. | [ ] |  |

## Severity Definition
| severity | meaning | required action |
|---|---|---|
| Blocker | Cannot be released | Must fix before merge/release |
| Major | High probability of becoming a bug | Fix or record accepted risk |
| Minor | Minor improvement | Optional/follow-up acceptable |
| Question | Spec confirmation required | Open Issue |
| False Positive | Incorrect report | Record reason for rejection |
| Accepted Risk | Accepted risk | Record impact/owner/deadline |
