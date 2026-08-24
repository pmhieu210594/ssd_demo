# Black-box Test Cases

**Ticket ID**: BUG-DASHBOARD
**Create date**: 2026-08-06 07:45:13
**Author**: Claude
**Update date**: 2026-08-07 09:45:00


## Test Case Summary

| case ID | AC ID | priority | category | title |
|---|---|---|---|---|
| BB-001 | AC-BUG-DASHBOARD-1 | P0 | Normal | List returns non-deleted rows |
| BB-002 | AC-BUG-DASHBOARD-1 | P1 | Normal | List filtered by Project |
| BB-003 | AC-BUG-DASHBOARD-1 | P1 | Normal | List filtered by Repository |
| BB-004 | AC-BUG-DASHBOARD-13 | P1 | Normal | Create with unrelated Repository/Ticket combo accepted |
| BB-005 | AC-BUG-DASHBOARD-2 | P1 | State | Empty state when no active rows |
| BB-006 | AC-BUG-DASHBOARD-3 | P0 | Normal | Create happy path (PM/QA) |
| BB-007 | AC-BUG-DASHBOARD-4 | P0 | Error / Duplicate | Create for Ticket with existing active row rejected (409) |
| BB-008 | AC-BUG-DASHBOARD-5 | P0 | Error | Negative bug count rejected |
| BB-009 | AC-BUG-DASHBOARD-5 | P0 | Error | Non-integer bug count rejected |
| BB-010 | AC-BUG-DASHBOARD-5 | P0 | Error | Missing bug count field rejected |
| BB-011 | AC-BUG-DASHBOARD-5 | P0 | Boundary | Bug count = 0 accepted |
| BB-012 | AC-BUG-DASHBOARD-6 | P1 | Boundary | Note exactly 500 characters accepted |
| BB-013 | AC-BUG-DASHBOARD-6 | P1 | Error / Boundary | Note 501 characters rejected |
| BB-014 | AC-BUG-DASHBOARD-6 | P2 | Boundary | Whitespace-only note trimmed to empty |
| BB-015 | AC-BUG-DASHBOARD-7 | P0 | Normal / State | Update refreshes counts, note, and audit fields |
| BB-016 | AC-BUG-DASHBOARD-8 | P0 | Normal / State | Soft delete removes row from default list |
| BB-017 | AC-BUG-DASHBOARD-9 | P0 | Error / Non-existing ID | Detail/update/delete on nonexistent ID rejected (404) |
| BB-018 | AC-BUG-DASHBOARD-9 | P0 | Error / Deleted data | Detail/update/delete on already soft-deleted ID rejected (404) |
| BB-019 | AC-BUG-DASHBOARD-10 | P0 | Permission | DEV can view list/detail |
| BB-020 | AC-BUG-DASHBOARD-10 | P0 | Permission | DEV blocked from create/update/delete (403) |
| BB-021 | AC-BUG-DASHBOARD-11 | P0 | Permission | Non-PM/QA/DEV/ADMIN role blocked from list/detail |
| BB-022 | AC-BUG-DASHBOARD-11 | P0 | Permission / Session expired | Unauthenticated caller blocked from every action |
| BB-023 | AC-BUG-DASHBOARD-12 | P1 | Error | Rejected request returns standard ErrorResponse envelope |
| BB-024 | AC-BUG-DASHBOARD-3 | P1 | Error / Non-existing ID | Create with nonexistent/inactive Project, Repository, or Ticket rejected (404) |
| BB-025 | AC-BUG-DASHBOARD-3 | P1 | Error / Empty/null | Create with missing required identifier field rejected (400) |

## Test Cases

### BB-001: List returns non-deleted rows

| item | content |
|---|---|
| Related AC | AC-BUG-DASHBOARD-1 |
| Priority | P0 |
| Category | Normal |
| Preconditions | Caller is authenticated as PM (or QA/DEV/ADMIN); at least 2 active Ticket Bug Metrics rows and 1 soft-deleted row exist in the same Project |
| Input | `GET /api/v1/ticket-bug-metrics` with no filters, `page=0`, `size=20` |
| Steps | 1. Log in as PM. 2. Open the Ticket Bug Metrics list screen (or call the list API directly). |
| Expected Result | Response is a page DTO (`items, page, size, totalElements, totalPages`); `items` contains only the 2 active rows, each with ticket identifier, internal/customer bug count, note, and status = ACTIVE; the soft-deleted row is absent |
| Note | Covers BR-4 |

### BB-002: List filtered by Project

| item | content |
|---|---|
| Related AC | AC-BUG-DASHBOARD-1 |
| Priority | P1 |
| Category | Normal |
| Preconditions | Two active Projects (P-A, P-B) each with at least one active Ticket Bug Metrics row |
| Input | `GET /api/v1/ticket-bug-metrics?projectId=<P-A id>` |
| Steps | 1. Call list API with `projectId` = P-A's ID. |
| Expected Result | Only rows belonging to P-A are returned; P-B's rows are excluded |
| Note | |

### BB-003: List filtered by Repository

| item | content |
|---|---|
| Related AC | AC-BUG-DASHBOARD-1 |
| Priority | P1 |
| Category | Normal |
| Preconditions | Two active Repositories (R-A, R-B) under the same Project, each referenced by at least one active row |
| Input | `GET /api/v1/ticket-bug-metrics?repositoryId=<R-A id>` |
| Steps | 1. Call list API with `repositoryId` = R-A's ID. |
| Expected Result | Only rows whose stored `repositoryId` equals R-A are returned; the filter narrows by the row's stored value only, with no cross-validation against the row's `ticketId` |
| Note | Confirms the repository filter is informational/filter-only per BR-12 |

### BB-004: Create with unrelated Repository/Ticket combo accepted

| item | content |
|---|---|
| Related AC | AC-BUG-DASHBOARD-13 |
| Priority | P1 |
| Category | Normal |
| Preconditions | PM/QA caller; active Project P; active Repository R1 under P; active Ticket T1 under P that has no relation to R1 (Ticket has no repository FK at all); T1 has no existing active row |
| Input | `POST /api/v1/ticket-bug-metrics` body `{projectId: P, repositoryId: R1, ticketId: T1, internalBugCount: 3, customerBugCount: 1}` |
| Steps | 1. Submit create with R1 and T1 that have no real-world relationship. |
| Expected Result | Request succeeds (201); row persisted with `repositoryId = R1`, `ticketId = T1` as submitted, no rejection for "mismatch" |
| Note | Confirms BR-12/H-BUG-DASHBOARD-1 — Repository is informational/filter-only, never cross-validated against Ticket |

### BB-005: Empty state when no active rows

| item | content |
|---|---|
| Related AC | AC-BUG-DASHBOARD-2 |
| Priority | P1 |
| Category | State |
| Preconditions | Authenticated PM/QA/DEV/ADMIN caller; a Project with zero active Ticket Bug Metrics rows (either none created, or all soft-deleted) |
| Input | Open the Ticket Bug Metrics list screen filtered to that Project |
| Steps | 1. Navigate to the list screen. 2. Filter by the empty Project. |
| Expected Result | UI shows the defined empty state (e.g. "no data" message), not a broken/blank table or an error |
| Note | FE behavior |

### BB-006: Create happy path (PM/QA)

| item | content |
|---|---|
| Related AC | AC-BUG-DASHBOARD-3 |
| Priority | P0 |
| Category | Normal |
| Preconditions | PM or QA caller; active Project, Repository (under Project), and Ticket (under Project) with no existing active row for that Ticket |
| Input | `{projectId, repositoryId, ticketId, internalBugCount: 12, customerBugCount: 2, note: "regression from sprint 4"}` |
| Steps | 1. Submit create. 2. Immediately fetch list. 3. Immediately fetch detail by returned ID. |
| Expected Result | Create returns 201 with the persisted DTO; the row appears in the default list; detail read returns identical data; `createdBy`/`createdAt` are populated |
| Note | |

### BB-007: Create for Ticket with existing active row rejected (409)

| item | content |
|---|---|
| Related AC | AC-BUG-DASHBOARD-4 |
| Priority | P0 |
| Category | Error / Duplicate |
| Preconditions | PM/QA caller; Ticket T already has one active Ticket Bug Metrics row |
| Input | Second create payload for the same `ticketId = T` |
| Steps | 1. Submit create for T again. |
| Expected Result | Request rejected with 409 (conflict); no second row is created; the original row is unchanged; error message directs caller to use update instead |
| Note | BR-6 |

### BB-008: Negative bug count rejected

| item | content |
|---|---|
| Related AC | AC-BUG-DASHBOARD-5 |
| Priority | P0 |
| Category | Error |
| Preconditions | PM/QA caller; valid Project/Repository/Ticket |
| Input | `internalBugCount: -1, customerBugCount: 2` (create); repeat with `customerBugCount: -1` on update of an existing row |
| Steps | 1. Submit create/update with a negative count. |
| Expected Result | Request rejected (400); no row created/changed |
| Note | BR-2 |

### BB-009: Non-integer bug count rejected

| item | content |
|---|---|
| Related AC | AC-BUG-DASHBOARD-5 |
| Priority | P0 |
| Category | Error |
| Preconditions | PM/QA caller; valid Project/Repository/Ticket |
| Input | `internalBugCount: 1.5` and, separately, `internalBugCount: "abc"` |
| Steps | 1. Submit create with each non-integer value. |
| Expected Result | Both rejected (400); no data changed |
| Note | BR-2 |

### BB-010: Missing bug count field rejected

| item | content |
|---|---|
| Related AC | AC-BUG-DASHBOARD-5 |
| Priority | P0 |
| Category | Error / Empty/null |
| Preconditions | PM/QA caller; valid Project/Repository/Ticket |
| Input | Payload omitting `internalBugCount` entirely; separately, omitting `customerBugCount` |
| Steps | 1. Submit create with each field omitted. |
| Expected Result | Rejected (400); no data changed |
| Note | BR-2 |

### BB-011: Bug count = 0 accepted

| item | content |
|---|---|
| Related AC | AC-BUG-DASHBOARD-5 |
| Priority | P0 |
| Category | Boundary |
| Preconditions | PM/QA caller; valid Project/Repository/Ticket, no existing active row |
| Input | `internalBugCount: 0, customerBugCount: 0` |
| Steps | 1. Submit create with both counts at 0. |
| Expected Result | Accepted (201); persisted with both counts = 0 |
| Note | Zero is a valid, meaningful state per spec §8.3 |

### BB-012: Note exactly 500 characters accepted

| item | content |
|---|---|
| Related AC | AC-BUG-DASHBOARD-6 |
| Priority | P1 |
| Category | Boundary |
| Preconditions | PM/QA caller; valid Project/Repository/Ticket |
| Input | `note` = a string of exactly 500 characters |
| Steps | 1. Submit create with a 500-character note. |
| Expected Result | Accepted; note persisted in full, untruncated |
| Note | BR-3 |

### BB-013: Note 501 characters rejected

| item | content |
|---|---|
| Related AC | AC-BUG-DASHBOARD-6 |
| Priority | P1 |
| Category | Error / Boundary |
| Preconditions | PM/QA caller; valid Project/Repository/Ticket |
| Input | `note` = a string of 501 characters |
| Steps | 1. Submit create with a 501-character note. |
| Expected Result | Rejected (400); no row created |
| Note | BR-3 |

### BB-014: Whitespace-only note trimmed to empty

| item | content |
|---|---|
| Related AC | AC-BUG-DASHBOARD-6 |
| Priority | P2 |
| Category | Boundary |
| Preconditions | PM/QA caller; valid Project/Repository/Ticket |
| Input | `note` = `"   "` (spaces only) |
| Steps | 1. Submit create with a whitespace-only note. |
| Expected Result | Accepted; persisted note is empty/null (trimmed), not stored as literal whitespace |
| Note | Resolves OI-BUG-DASHBOARD-5 — implemented as trim-to-null |

### BB-015: Update refreshes counts, note, and audit fields

| item | content |
|---|---|
| Related AC | AC-BUG-DASHBOARD-7 |
| Priority | P0 |
| Category | Normal / State |
| Preconditions | PM/QA caller; existing active row with known `internalBugCount`, `customerBugCount`, `note`, `updatedAt` |
| Input | `PUT /{id}` with changed `internalBugCount`, `customerBugCount`, and `note` |
| Steps | 1. Submit update. 2. Fetch detail immediately after. |
| Expected Result | Update response and subsequent detail read both reflect the new values; `updatedAt`/`updatedBy` are refreshed (newer than before, and match the caller) |
| Note | BR-11 |

### BB-016: Soft delete removes row from default list

| item | content |
|---|---|
| Related AC | AC-BUG-DASHBOARD-8 |
| Priority | P0 |
| Category | Normal / State |
| Preconditions | PM/QA caller; existing active row |
| Input | `PUT /{id}/delete` |
| Steps | 1. Submit soft delete. 2. Fetch default list. 3. Attempt normal detail read of the same ID. |
| Expected Result | Delete response shows the row as deleted (`status = DELETED`, `deleteFlag`/`deletedAt`/`deletedBy` populated); the row is absent from the default active list; normal detail read returns 404 |
| Note | BR-5 |

### BB-017: Detail/update/delete on nonexistent ID rejected (404)

| item | content |
|---|---|
| Related AC | AC-BUG-DASHBOARD-9 |
| Priority | P0 |
| Category | Error / Non-existing ID |
| Preconditions | PM/QA/DEV/ADMIN caller; a random UUID that does not correspond to any row |
| Input | `GET /{randomId}`, `PUT /{randomId}`, `PUT /{randomId}/delete` |
| Steps | 1. Call detail, then update, then delete with a nonexistent ID. |
| Expected Result | Each call rejected with 404; no mutation occurs |
| Note | |

### BB-018: Detail/update/delete on already soft-deleted ID rejected (404)

| item | content |
|---|---|
| Related AC | AC-BUG-DASHBOARD-9 |
| Priority | P0 |
| Category | Error / Deleted data |
| Preconditions | PM/QA/DEV/ADMIN caller; a row that has already been soft-deleted |
| Input | `GET /{deletedId}`, `PUT /{deletedId}`, `PUT /{deletedId}/delete` |
| Steps | 1. Call detail, then update, then delete-again with the soft-deleted row's ID. |
| Expected Result | Each call rejected with 404 through the normal flow; no mutation occurs; the row's deletion metadata is unchanged |
| Note | |

### BB-019: DEV can view list/detail

| item | content |
|---|---|
| Related AC | AC-BUG-DASHBOARD-10 |
| Priority | P0 |
| Category | Permission |
| Preconditions | DEV caller with project-scoped DEV role on Project P; at least one active row under P |
| Input | `GET /api/v1/ticket-bug-metrics?projectId=P`, `GET /{id}` |
| Steps | 1. Log in as DEV. 2. Call list and detail for Project P. |
| Expected Result | Both requests succeed (200) and return the expected data |
| Note | BR-8 |

### BB-020: DEV blocked from create/update/delete (403)

| item | content |
|---|---|
| Related AC | AC-BUG-DASHBOARD-10 |
| Priority | P0 |
| Category | Permission |
| Preconditions | DEV caller with project-scoped DEV role on Project P; existing active row under P |
| Input | Valid create payload; valid update payload on the existing row; delete on the existing row |
| Steps | 1. As DEV, attempt create. 2. Attempt update. 3. Attempt delete. |
| Expected Result | All three rejected with 403; no data is changed; the same DEV caller's list/detail calls (BB-019) still succeed |
| Note | BR-7/BR-8 |

### BB-021: Non-PM/QA/DEV/ADMIN role blocked from list/detail

| item | content |
|---|---|
| Related AC | AC-BUG-DASHBOARD-11 |
| Priority | P0 |
| Category | Permission |
| Preconditions | Authenticated caller whose project role on Project P is outside {PM, QA, DEV, ADMIN} (e.g. VIEWER, or a role with no project-role grant at all) |
| Input | `GET /api/v1/ticket-bug-metrics?projectId=P`, `GET /{id}` for an existing row under P |
| Steps | 1. Log in as the out-of-scope role. 2. Attempt list. 3. Attempt detail. |
| Expected Result | Both requests rejected (403); the screen is inaccessible, not merely read-only |
| Note | BR-9 |

### BB-022: Unauthenticated caller blocked from every action

| item | content |
|---|---|
| Related AC | AC-BUG-DASHBOARD-11 |
| Priority | P0 |
| Category | Permission / Session expired |
| Preconditions | No active session / no auth token (e.g. session expired) |
| Input | `GET /api/v1/ticket-bug-metrics`, `GET /{id}`, `POST /`, `PUT /{id}`, `PUT /{id}/delete` |
| Steps | 1. Call each endpoint without authentication. |
| Expected Result | Every call rejected (401/403 depending on session-auth mapping); no data returned or changed |
| Note | |

### BB-023: Rejected request returns standard ErrorResponse envelope

| item | content |
|---|---|
| Related AC | AC-BUG-DASHBOARD-12 |
| Priority | P1 |
| Category | Error |
| Preconditions | Any rejected scenario (e.g. BB-007 duplicate conflict, BB-008 negative count, BB-017 not-found) |
| Input | Trigger any one rejected request, e.g. the BB-008 negative-count create |
| Steps | 1. Submit the rejected request. 2. Inspect the response body. |
| Expected Result | Response body is exactly `{timestamp, status, error, message, traceId}` — not the raw input's illustrative `code/status/message/data` shape; no stack trace or internal detail is present |
| Note | Representative case for BR-10/§13; applies to every error case in this document |

### BB-024: Create with nonexistent/inactive Project, Repository, or Ticket rejected (404)

| item | content |
|---|---|
| Related AC | AC-BUG-DASHBOARD-3 |
| Priority | P1 |
| Category | Error / Non-existing ID |
| Preconditions | PM/QA caller; one random UUID not corresponding to any Project; one inactive Repository; one inactive Ticket |
| Input | Create payload with (a) nonexistent `projectId`, (b) inactive `repositoryId`, (c) inactive `ticketId`, tested independently |
| Steps | 1. Submit create with each single invalid identifier while keeping the other two valid. |
| Expected Result | Each case rejected with 404; no row created |
| Note | Confirms independent existence/active checks per BR-12 |

### BB-025: Create with missing required identifier field rejected (400)

| item | content |
|---|---|
| Related AC | AC-BUG-DASHBOARD-3 |
| Priority | P1 |
| Category | Error / Empty/null |
| Preconditions | PM/QA caller |
| Input | Create payload omitting `projectId`; separately omitting `repositoryId`; separately omitting `ticketId` |
| Steps | 1. Submit create with each field blank/omitted. |
| Expected Result | Each case rejected with 400; no row created |
| Note | |

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
- [x] Session expired
- [ ] Existing data compatibility (N/A — wholly new feature/table, no legacy data to migrate)
- [x] Log/audit/notification/report output
