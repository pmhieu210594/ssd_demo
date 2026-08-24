# Black-box Test Cases

**Ticket ID**: AI-QUALITY
**Create date**: 2026-08-18 08:10:54
**Author**: BRYCENVN\nvt_dung
**Update date**: 2026-08-18 08:10:54

Derived strictly from `spec-pack.md` §6 (Input/Output/Error/Boundary), §7 (Acceptance Criteria),
and §8 (Examples) — no implementation source was read to produce this artifact, per the black-box
mandate.

## Test Case Summary

| case ID | AC ID | priority | category | title | status |
|---|---|---|---|---|---|
| BB-001 | AC-AI-QUALITY-1 | P0 | Normal | Create with valid project/repository/ticket and rate in range succeeds | Not run |
| BB-002 | AC-AI-QUALITY-2 | P0 | Error / Duplicate | Create for a ticket that already has an active row returns 409 | Not run |
| BB-003 | AC-AI-QUALITY-3 | P1 | Error | Create where repository does not belong to project returns 400 | Not run |
| BB-004 | AC-AI-QUALITY-4 | P1 | Error | Create where ticket does not belong to repository returns 400 | Not run |
| BB-005 | AC-AI-QUALITY-5 | P1 | Boundary | rate = 100.00 accepted (upper inclusive bound) | Not run |
| BB-006 | AC-AI-QUALITY-5 | P1 | Boundary | rate = 0.00 accepted (lower inclusive bound) | Not run |
| BB-007 | AC-AI-QUALITY-5 | P1 | Boundary | rate = 100.01 rejected (above upper bound) | Not run |
| BB-008 | AC-AI-QUALITY-5 | P1 | Boundary | rate = -0.01 rejected (below lower bound) | Not run |
| BB-009 | AC-AI-QUALITY-5 | P1 | Boundary / Empty-null | rate = null rejected | Not run |
| BB-010 | AC-AI-QUALITY-5 | P1 | Boundary | rate = 50.555 (scale 3) rejected | Not run |
| BB-011 | AC-AI-QUALITY-6 | P0 | Normal | Updating rate persists new value and updates updated_at/updated_by | Not run |
| BB-012 | AC-AI-QUALITY-7 | P0 | State transition | Soft-deleting a row makes it absent from get-by-id and list, no physical delete | Not run |
| BB-013 | AC-AI-QUALITY-8 | P0 | State transition / Duplicate | Re-creating a row for the same ticket after soft-delete succeeds (no false 409) | Not run |
| BB-014 | AC-AI-QUALITY-9 | P1 | Normal | List filters by project_id, repository_id, and ticket_id, AND-combined | Not run |
| BB-015 | AC-AI-QUALITY-9 | P2 | Normal | List returns PageResult-shaped response with correct page/size/totalElements/totalPages | Not run |
| BB-016 | AC-AI-QUALITY-10 | P0 | Permission | VIEW_ONLY (DEV project role) gets 403 on create/update/delete | Not run |
| BB-017 | AC-AI-QUALITY-10 | P0 | Permission | VIEW_ONLY (DEV project role) gets 200 on get/list | Not run |
| BB-018 | AC-AI-QUALITY-11 | P0 | Permission | NONE (no project role, not ADMIN) gets 403 on every endpoint, including get/list | Not run |
| BB-019 | AC-AI-QUALITY-12 | P0 | Permission | Global ADMIN gets MUTATE regardless of per-project role | Not run |
| BB-020 | AC-AI-QUALITY-13 | P2 | Normal | List `search` filters by ticket external key/title, AND-combined with other filters | Not run |
| BB-021 | — (BR-3, §6.5) | P1 | Boundary / Non-existing ID | Create with malformed UUID or well-formed but non-existent project_id/repository_id/ticket_id returns 400, not 500 | Not run |
| BB-022 | — (§6.4) | P1 | Error / Non-existing ID / Deleted data | Get/Update/Delete on a non-existent or already-soft-deleted id returns 404 | Not run |
| BB-023 | — (§13, §6.4) | P2 | Log/audit/operation | Error responses use standard ErrorResponse shape with traceId, no stack trace leaked | Not run |

## Test Cases

### BB-001: Create with valid project/repository/ticket and rate in range succeeds

| item | content |
|---|---|
| Related AC | AC-AI-QUALITY-1 |
| Priority | P0 |
| Category | Normal |
| Preconditions | Caller has MUTATE access (e.g. PM/QA/ADMIN); project P-1 exists and is active; repository R-1 belongs to P-1; ticket T-1 belongs to R-1; T-1 has no active AI Quality row |
| Input | `{project_id: P-1, repository_id: R-1, ticket_id: T-1, ai_quality_rate: 92.50}` via `POST /api/v1/ai-qualities` |
| Steps | 1. Authenticate as a MUTATE-access caller. 2. Send create request with the input above. |
| Expected Result | 201/200 response echoing `project_id`, `repository_id`, `ticket_id`, `ai_quality_rate = 92.50`, new `ticket_ai_quality_id`, `status = ACTIVE`, populated `created_at/created_by/updated_at/updated_by` |
| Note | Mirrors spec-pack §8.1 |

### BB-002: Create for a ticket that already has an active row returns 409

| item | content |
|---|---|
| Related AC | AC-AI-QUALITY-2 |
| Priority | P0 |
| Category | Error / Duplicate |
| Preconditions | Ticket T-1 already has an active AI Quality row (e.g. from BB-001) |
| Input | Same create request repeated: `{project_id: P-1, repository_id: R-1, ticket_id: T-1, ai_quality_rate: 92.50}` |
| Steps | 1. Repeat the exact same create request against a ticket with an existing active row. |
| Expected Result | 409 Conflict, conflict error code instructing caller to use `PUT /api/v1/ai-qualities/{id}` instead; no second row is created |
| Note | Mirrors spec-pack §8.2; BR-1/BR-2 |

### BB-003: Create where repository does not belong to project returns 400

| item | content |
|---|---|
| Related AC | AC-AI-QUALITY-3 |
| Priority | P1 |
| Category | Error |
| Preconditions | Project P-1 exists; repository R-2 exists but belongs to a different project P-2, not P-1; ticket T-1 belongs to R-1 (not used consistently on purpose) |
| Input | `{project_id: P-1, repository_id: R-2, ticket_id: T-1, ai_quality_rate: 50.00}` |
| Steps | 1. Send create request with a repository_id that belongs to a different project than the one supplied. |
| Expected Result | 400 Bad Request, validation error code; no row created; no silent auto-correction of the mismatched IDs |
| Note | BR-3 |

### BB-004: Create where ticket does not belong to repository returns 400

| item | content |
|---|---|
| Related AC | AC-AI-QUALITY-4 |
| Priority | P1 |
| Category | Error |
| Preconditions | Project P-1 exists; repository R-1 belongs to P-1; ticket T-9 exists but belongs to a different repository R-9, not R-1 |
| Input | `{project_id: P-1, repository_id: R-1, ticket_id: T-9, ai_quality_rate: 50.00}` |
| Steps | 1. Send create request with a ticket_id that belongs to a different repository than the one supplied. |
| Expected Result | 400 Bad Request, validation error code; no row created |
| Note | BR-3 |

### BB-005: rate = 100.00 accepted (upper inclusive bound)

| item | content |
|---|---|
| Related AC | AC-AI-QUALITY-5 |
| Priority | P1 |
| Category | Boundary |
| Preconditions | Valid project/repository/ticket triple with no active row |
| Input | `ai_quality_rate = 100.00` |
| Steps | 1. Create with rate exactly 100.00. |
| Expected Result | 201/200, row created with `ai_quality_rate = 100.00` |
| Note | §6.5, §8.3 upper inclusive bound |

### BB-006: rate = 0.00 accepted (lower inclusive bound)

| item | content |
|---|---|
| Related AC | AC-AI-QUALITY-5 |
| Priority | P1 |
| Category | Boundary |
| Preconditions | Valid project/repository/ticket triple with no active row |
| Input | `ai_quality_rate = 0.00` |
| Steps | 1. Create with rate exactly 0.00. |
| Expected Result | 201/200, row created with `ai_quality_rate = 0.00` |
| Note | §6.5 lower inclusive bound |

### BB-007: rate = 100.01 rejected (above upper bound)

| item | content |
|---|---|
| Related AC | AC-AI-QUALITY-5 |
| Priority | P1 |
| Category | Boundary |
| Preconditions | Valid project/repository/ticket triple with no active row |
| Input | `ai_quality_rate = 100.01` |
| Steps | 1. Create with rate 100.01. |
| Expected Result | 400 Bad Request, validation error code; no row created |
| Note | §6.5, §8.3 |

### BB-008: rate = -0.01 rejected (below lower bound)

| item | content |
|---|---|
| Related AC | AC-AI-QUALITY-5 |
| Priority | P1 |
| Category | Boundary |
| Preconditions | Valid project/repository/ticket triple with no active row |
| Input | `ai_quality_rate = -0.01` |
| Steps | 1. Create with rate -0.01. |
| Expected Result | 400 Bad Request, validation error code; no row created |
| Note | §6.5 |

### BB-009: rate = null rejected

| item | content |
|---|---|
| Related AC | AC-AI-QUALITY-5 |
| Priority | P1 |
| Category | Boundary / Empty-null |
| Preconditions | Valid project/repository/ticket triple with no active row |
| Input | `ai_quality_rate = null` (field omitted or explicit null) |
| Steps | 1. Create request omitting `ai_quality_rate` / sending null. |
| Expected Result | 400 Bad Request, validation error code; no row created |
| Note | §6.5 "reject null" |

### BB-010: rate = 50.555 (scale 3) rejected

| item | content |
|---|---|
| Related AC | AC-AI-QUALITY-5 |
| Priority | P1 |
| Category | Boundary |
| Preconditions | Valid project/repository/ticket triple with no active row |
| Input | `ai_quality_rate = 50.555` |
| Steps | 1. Create with a rate having 3 decimal places. |
| Expected Result | 400 Bad Request, validation error code; no row created |
| Note | BR-6, §8.3 |

### BB-011: Updating rate persists new value and updates audit fields

| item | content |
|---|---|
| Related AC | AC-AI-QUALITY-6 |
| Priority | P0 |
| Category | Normal |
| Preconditions | An active row exists for ticket T-1 (e.g. from BB-001) with `ai_quality_rate = 92.50` |
| Input | `PUT /api/v1/ai-qualities/{id}` with `{ai_quality_rate: 75.25}` |
| Steps | 1. Send update request changing only the rate. 2. Get the row again. |
| Expected Result | 200 response with `ai_quality_rate = 75.25`; `updated_at` is newer than before; `updated_by` reflects the acting caller; `project_id/repository_id/ticket_id` unchanged |
| Note | BR-1 |

### BB-012: Soft-deleting a row makes it absent from get-by-id and list

| item | content |
|---|---|
| Related AC | AC-AI-QUALITY-7 |
| Priority | P0 |
| Category | State transition |
| Preconditions | An active row exists for ticket T-1 |
| Input | `PUT /api/v1/ai-qualities/{id}/delete` |
| Steps | 1. Soft-delete the row. 2. Attempt `GET /api/v1/ai-qualities/{id}`. 3. Call the list endpoint with a filter that would have included this row. |
| Expected Result | Delete call succeeds; subsequent get returns 404; the row does not appear in list results; row is not physically removed (verified indirectly: re-creating for the same ticket succeeds per BB-013, and no duplicate-key error occurs) |
| Note | BR-4 |

### BB-013: Re-creating a row for the same ticket after soft-delete succeeds

| item | content |
|---|---|
| Related AC | AC-AI-QUALITY-8 |
| Priority | P0 |
| Category | State transition / Duplicate |
| Preconditions | Ticket T-1's prior active row was soft-deleted (BB-012) |
| Input | `POST /api/v1/ai-qualities` with `{project_id: P-1, repository_id: R-1, ticket_id: T-1, ai_quality_rate: 60.00}` |
| Steps | 1. After soft-delete, send a new create request for the same `ticket_id`. |
| Expected Result | 201/200, new row created with a new `ticket_ai_quality_id`, `status = ACTIVE`; no false 409 |
| Note | BR-4 |

### BB-014: List filters by project_id, repository_id, and ticket_id, AND-combined

| item | content |
|---|---|
| Related AC | AC-AI-QUALITY-9 |
| Priority | P1 |
| Category | Normal |
| Preconditions | Multiple active rows exist across different projects/repositories/tickets |
| Input | `GET /api/v1/ai-qualities?projectId=P-1&repositoryId=R-1&ticketId=T-1` |
| Steps | 1. Call list with all three ID filters set. 2. Call list with only `projectId`. 3. Call list with only `repositoryId`. |
| Expected Result | Each call returns only rows matching all supplied filters (AND-combined); no rows outside the filter scope are returned |
| Note | §6.2 |

### BB-015: List returns PageResult-shaped response

| item | content |
|---|---|
| Related AC | AC-AI-QUALITY-9 |
| Priority | P2 |
| Category | Normal |
| Preconditions | At least a few active rows exist |
| Input | `GET /api/v1/ai-qualities?page=0&size=10` |
| Steps | 1. Call list with default paging params. |
| Expected Result | Response body has `items`, `page`, `size`, `totalElements`, `totalPages` fields matching `PageResult<T>` shape; `items.length <= size` |
| Note | §6.3 |

### BB-016: VIEW_ONLY (DEV project role) gets 403 on create/update/delete

| item | content |
|---|---|
| Related AC | AC-AI-QUALITY-10 |
| Priority | P0 |
| Category | Permission |
| Preconditions | Caller has per-project role DEV on project P-1, no global ADMIN role |
| Input | Create/update/delete requests scoped to P-1 |
| Steps | 1. Attempt `POST /api/v1/ai-qualities` as DEV. 2. Attempt `PUT /{id}`. 3. Attempt `PUT /{id}/delete`. |
| Expected Result | All three calls return 403 Forbidden; no row is created/modified/deleted |
| Note | BR-5 |

### BB-017: VIEW_ONLY (DEV project role) gets 200 on get/list

| item | content |
|---|---|
| Related AC | AC-AI-QUALITY-10 |
| Priority | P0 |
| Category | Permission |
| Preconditions | Caller has per-project role DEV on project P-1; at least one active row exists under P-1 |
| Input | `GET /api/v1/ai-qualities?projectId=P-1`, `GET /api/v1/ai-qualities/{id}` |
| Steps | 1. Call list as DEV. 2. Call get-by-id as DEV. |
| Expected Result | Both calls return 200 with the expected data; read access is permitted |
| Note | BR-5 |

### BB-018: NONE (no project role, not ADMIN) gets 403 on every endpoint

| item | content |
|---|---|
| Related AC | AC-AI-QUALITY-11 |
| Priority | P0 |
| Category | Permission |
| Preconditions | Caller is authenticated but has no role on project P-1 and is not global ADMIN |
| Input | Create, update, delete, get, and list requests scoped to P-1 |
| Steps | 1. Attempt each of create/update/delete/get/list as this caller. |
| Expected Result | Every call returns 403 Forbidden, including get/list (read access is also denied) |
| Note | BR-5 |

### BB-019: Global ADMIN gets MUTATE regardless of per-project role

| item | content |
|---|---|
| Related AC | AC-AI-QUALITY-12 |
| Priority | P0 |
| Category | Permission |
| Preconditions | Caller has global role ADMIN; caller either has no per-project role on P-1, or has DEV per-project role on P-1 |
| Input | Create/update/delete requests scoped to P-1 |
| Steps | 1. As global ADMIN with no/low per-project role, attempt create, update, and delete. |
| Expected Result | All calls succeed (not 403); ADMIN's global role overrides the per-project role resolution |
| Note | BR-5 |

### BB-020: List `search` filters by ticket external key/title, AND-combined

| item | content |
|---|---|
| Related AC | AC-AI-QUALITY-13 |
| Priority | P2 |
| Category | Normal |
| Preconditions | Multiple active rows exist; at least one ticket's external key/title contains a known substring (e.g. "PROJ-123") and others do not |
| Input | `GET /api/v1/ai-qualities?projectId=P-1&search=PROJ-123` |
| Steps | 1. Call list with `search` alone. 2. Call list with `search` combined with `projectId`/`repositoryId`/`ticketId`. |
| Expected Result | Only rows whose ticket external key/title matches the search term are returned; when combined with other filters, results satisfy all filters simultaneously (AND, not OR) |
| Note | §6.2, post-implementation addition |

### BB-021: Create with malformed or non-existent UUIDs returns 400, not 500

| item | content |
|---|---|
| Related AC | — (§6.5 Boundary Value table) |
| Priority | P1 |
| Category | Boundary / Non-existing ID |
| Preconditions | None beyond an authenticated MUTATE-access caller |
| Input | (a) `project_id = "not-a-uuid"`; (b) `project_id` well-formed UUID that does not exist in `tbl_dim_project`; (c) empty-string `repository_id` |
| Steps | 1. Send create requests with each malformed/non-existent input variant in turn. |
| Expected Result | Each variant returns 400 Bad Request (malformed → 400 missing/invalid; non-existent well-formed UUID → 400 validation), never a 500 |
| Note | §6.5 |

### BB-022: Get/Update/Delete on non-existent or already-soft-deleted id returns 404

| item | content |
|---|---|
| Related AC | — (§6.4 Error/Exception table) |
| Priority | P1 |
| Category | Error / Non-existing ID / Deleted data |
| Preconditions | (a) an id that has never existed; (b) an id whose row was already soft-deleted |
| Input | `GET /api/v1/ai-qualities/{id}`, `PUT /{id}`, `PUT /{id}/delete` for each precondition id |
| Steps | 1. Call get/update/delete against a never-existed id. 2. Repeat against an already-soft-deleted id. |
| Expected Result | All calls in both cases return 404 Not Found, not-found error code |
| Note | BR-4 |

### BB-023: Error responses use standard ErrorResponse shape with traceId, no stack trace leaked

| item | content |
|---|---|
| Related AC | — (§13 Security/Privacy Impact, §6.4) |
| Priority | P2 |
| Category | Log/audit/operation |
| Preconditions | Trigger any of the 400/403/404/409 error cases above |
| Input | Any invalid request from BB-002/003/004/007/008/009/010/016/018/021/022 |
| Steps | 1. Inspect the JSON error body of any failing request. |
| Expected Result | Body matches `ErrorResponse(timestamp, status, errorCode, message, traceId)` exactly; no stack trace, internal class name, or SQL text appears in the response body |
| Note | Per §13 and 30-security.md; observable black-box, log-side verification (e.g. correlating traceId server-side) is out of black-box scope |

## AC ↔ Black-box Case Mapping

| AC ID | Black-box case(s) |
|---|---|
| AC-AI-QUALITY-1 | BB-001 |
| AC-AI-QUALITY-2 | BB-002 |
| AC-AI-QUALITY-3 | BB-003 |
| AC-AI-QUALITY-4 | BB-004 |
| AC-AI-QUALITY-5 | BB-005, BB-006, BB-007, BB-008, BB-009, BB-010 |
| AC-AI-QUALITY-6 | BB-011 |
| AC-AI-QUALITY-7 | BB-012 |
| AC-AI-QUALITY-8 | BB-013 |
| AC-AI-QUALITY-9 | BB-014, BB-015 |
| AC-AI-QUALITY-10 | BB-016, BB-017 |
| AC-AI-QUALITY-11 | BB-018 |
| AC-AI-QUALITY-12 | BB-019 |
| AC-AI-QUALITY-13 | BB-020 |
| (supplemental, §6.4/§6.5/§13) | BB-021, BB-022, BB-023 |

## Viewpoints Covered

- [x] Normal case
- [x] Error case
- [x] Boundary value
- [x] Permission difference
- [x] State transition
- [ ] Character type input
- [x] Numeric input
- [ ] Full-width number
- [x] Empty/null
- [x] Duplicate
- [x] Non-existing ID
- [x] Deleted data
- [ ] External IF failure
- [ ] Timeout/retry
- [ ] Double submit
- [ ] Back/reload
- [ ] Session expired
- [x] Existing data compatibility
- [x] Log/audit/notification/report output

Unchecked items are genuinely out of scope for this feature: no character-type-specific field
(no free-text field other than `search`), no full-width-number input surface, no external
integration/webhook (impact-analysis §10), and no FE-only concerns (double submit, back/reload,
session expiry) are testable purely at the black-box API-contract level covered here — those are
tracked as FE component/E2E concerns in `test-plan.md` §6, not this artifact.
