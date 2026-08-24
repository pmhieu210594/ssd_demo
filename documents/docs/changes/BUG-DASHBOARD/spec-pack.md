# Spec Pack

**Ticket ID**: BUG-DASHBOARD
**Create date**: 2026-08-06 00:00:00
**Author**: Claude
**Update date**: 2026-08-06 01:00:00

## 1. Context / Purpose

This spec pack consolidates the "Ticket Bug Metrics" request (`docs/changes/BUG-DASHBOARD/01_raw-input.md`) into a single planning artifact for later implementation. The screen lets QA/PM record, per Ticket, both the Internal Bug Count (found by QA) and Customer Bug Count (found by customer/UAT), with create/update/detail/soft-delete and role-gated access.

Phase 1's goal is not to finalize every product decision by guesswork, but to:

- anchor the feature to observed source-code conventions and live schema (`tbl_dim_project`, `tbl_dim_repository`, `tbl_dim_ticket`, the `RepositoryController`/`RepositoryService` governance pattern, and the `QaDashboardService` role-gating pattern),
- convert the raw input's acceptance criteria into testable statements,
- surface every blocking ambiguity before Phase 3.

The primary business input is `docs/changes/BUG-DASHBOARD/01_raw-input.md`. No prior implementation of this feature exists in BE or FE. The three blocking contract questions originally raised in this spec pack (H-BUG-DASHBOARD-1, -2, -3) were resolved by human confirmation on 2026-08-06 and are incorporated below.

## 2. Scope

### 2.1. Within range

- Define the intended Ticket Bug Metrics behavior for list, detail, create, update, and soft delete.
- Lock what can already be confirmed from current source:
  - Project/Repository/Ticket dimension schema and FK shape,
  - governance REST/DTO/error patterns (`RepositoryController`/`RepositoryDtos`/`ErrorResponse`),
  - per-project role-gating pattern (`QaDashboardService`),
  - FE cascading-filter/form/delete/role-hiding conventions.
- Convert requested behavior into testable ACs.
- Separate unresolved contract/schema/product decisions into explicit decision logs (Human Decision Required / Assumptions / Open Issues).

### 2.2. Out of range

- Implementing backend or frontend code, or writing the actual migration file.
- Approving schema changes beyond what this ticket's raw input, observed conventions, and the resolved decisions in §16 justify.
- Finalizing i18n resource file locations, FE route/menu placement, or exact seeded role codes — logged as Open Issues.

## 3. Terminology

| terms | meaning | notes |
|---|---|---|
| Ticket Bug Metrics | New entity: one row per Ticket storing Internal Bug Count and Customer Bug Count | Table name proposed as `tbl_ticket_bug_metrics` |
| Internal Bug Count | Number of bugs found internally (QA) for a Ticket | `internal_bug_count`, non-negative integer |
| Customer Bug Count | Number of bugs reported by customer/UAT for a Ticket | `customer_bug_count`, non-negative integer |
| Project | Business entity stored in `tbl_dim_project` | PK `project_id` |
| Repository | Business entity stored in `tbl_dim_repository`, child of Project | PK `repository_id`, FK `project_id` |
| Ticket | Business entity stored in `tbl_dim_ticket`, child of Project | PK `ticket_id`, FK `project_id` — **has no FK to Repository**; per resolved H-1, Repository is treated as an informational/filter field only and is not cross-validated against the selected Ticket |
| Soft delete | Marking row inactive/deleted without physical removal | Backed by `delete_flag`, `deleted_at`, `deleted_by`, `status` per governance convention |
| Active row | Ticket Bug Metrics row not soft deleted | `delete_flag = false`, `deleted_at IS NULL`, `status = 'ACTIVE'` |
| Governance pattern | Existing CRUD pattern used by Repository (and Organization/Customer/Team) | List/detail/create/update/`PUT {id}/delete`, no `version` field for Repository |
| Per-project role gating pattern | Existing access-control pattern used by `QaDashboardService` | `findProjectRole(caller, projectId)` + ADMIN bypass |
| Spec source of truth | Canonical planning artifact for later phases | This document after Phase 1 |

## 4. As-Is

- `tbl_dim_project`, `tbl_dim_repository`, `tbl_dim_ticket` exist since `V4__init_shema_v2.sql` (L107-193), each with a semantic UUID PK (`project_id`, `repository_id`, `ticket_id`).
- `tbl_dim_repository.project_id` FKs to `tbl_dim_project`; `tbl_dim_ticket.project_id` FKs to `tbl_dim_project` — **`tbl_dim_ticket` has no `repository_id` column at all**. Repository and Ticket are siblings under Project, not parent/child.
- No `tbl_ticket_bug_metrics` or equivalent table exists in any migration; the only "bug" hit in migration history is a comment on `ticket_type` values (`V1__init_schema.sql:57`).
- Latest migration is `V508__add_submitted_by_to_review.sql`.
- Governance/junction tables added after V4 (e.g. `V120__project_management.sql`, `V130__repository_management.sql`) establish a soft-delete-from-creation convention: `delete_flag`, `deleted_at`, `deleted_by` (often alongside `status`), rather than a bare `delete_flag INT`.
- `RepositoryController`/`RepositoryService`/`RepositoryDtos` are the closest implemented governance CRUD precedent:
  - Routes: `GET /api/v1/repositories`, `GET /{id}`, `POST`, `PUT /{id}`, `PUT /{id}/delete` (soft delete).
  - No `version`/optimistic-locking field on `RepositoryModel`.
  - Page DTO: `RepositoryPageDto(items, page, size, totalElements, totalPages)`.
  - Domain exceptions (`NotFoundException`, `BusinessRuleException`), audit logging via `AdminAuditLogService`.
- `QaDashboardService.requireAnyAccess`/`requireQaAccess` (L48-74) is the existing precedent for per-project, role-scoped access: ADMIN bypasses, otherwise looks up the caller's per-project role via `findProjectRole(caller, projectId)` and compares against a required role code. No `@PreAuthorize`/`hasRole` usage exists anywhere in this codebase.
- Real error envelope in code: `ErrorResponse(timestamp, status, error, message, traceId)` (`ErrorResponse.java`), mapped by `GlobalExceptionHandler` (`NotFoundException`→404, `ForbiddenException`→403, `ApplicationException`→409, `DomainException`/`MethodArgumentNotValidException`→400).
- FE has a directly comparable cascading Project→Repository filter (`DevelopmentDashboardPage.tsx`/`DevFilterBar.tsx`): plain `useState` + TanStack Query key-based refetch, dependent queries gated by `enabled`, self-healing selection reset via `useEffect`. Forms use `@tanstack/react-form`-based `CForm` (no react-hook-form/zod); delete confirmation uses antd `Popconfirm`; role-based UI hiding is inline `useAuth().user.role` checks.
- No implemented Ticket Bug Metrics source was observed in BE or FE during this phase.

## 5. To-Be

Ticket Bug Metrics should become a governance-style CRUD feature that allows authorized users to:

- list Ticket Bug Metrics rows, filterable by Project and Repository,
- view a Ticket Bug Metrics detail,
- create a Ticket Bug Metrics row for a Ticket (one row per Ticket),
- update the Internal/Customer bug counts and note for an existing row,
- soft delete a row.

Mutation (create/update/delete) is restricted to PM and QA roles; DEV can view but not mutate; all other roles cannot access the screen at all, enforced via the existing `QaDashboardService`-style per-project role check. Repository is a filter/informational field only (per resolved H-1) and is not cross-validated against the selected Ticket. The eventual implementation should follow the observed `RepositoryController`/`RepositoryService` governance conventions and the `QaDashboardService` role-gating conventions, and should use the corrected DDL/migration approach agreed in §16 (H-3).

## 6. Detailed specification

### 6.1. Business Rules

| ID | Rule | Basis |
|---|---|---|
| BR-1 | A Ticket Bug Metrics row is uniquely tied to one Ticket (one active row per Ticket). | Raw input §4/§6.1 |
| BR-2 | `internal_bug_count` and `customer_bug_count` must each be a non-negative integer (`>= 0`). | Raw input §3.3 |
| BR-3 | `note` is optional; if provided, maximum 500 characters. | Raw input §3.3, matches `VARCHAR(500)` |
| BR-4 | Soft-deleted rows are excluded from the default list and normal detail/update flow. | Raw input + governance convention |
| BR-5 | Soft delete must update deletion metadata (`delete_flag`, `deleted_at`, `deleted_by`, `status='DELETED'`) rather than physically remove the row. | Raw input §6.4 + governance convention |
| BR-6 | Creating a row for a Ticket that already has an active Ticket Bug Metrics row must be rejected as a conflict, directing the caller to use update instead. | Raw input §6.1 |
| BR-7 | Only PM and QA roles may create, update, or soft delete a row. | Raw input §6.5 |
| BR-8 | DEV role may access the screen and view data, but cannot create, update, or delete. | Raw input §6.5 |
| BR-9 | Any role other than PM, QA, DEV, or ADMIN must not be able to access the screen at all (not even read). | Raw input §6.5 |
| BR-10 | Backend validation is the final enforcement point for all rules above; FE affordance hiding alone is not sufficient. | Standards + `.claude/rules/30-security.md` |
| BR-11 | Audit fields (`created_at/by`, `updated_at/by`) are populated automatically on create/update. | Raw input §6.2 + governance convention |
| BR-12 | Repository is stored on a Ticket Bug Metrics row for filtering/display only; it is **not** cross-validated against the selected Ticket (no such FK exists on `tbl_dim_ticket`). Only `projectId` and `ticketId` are validated against each other (Ticket must belong to Project); `repositoryId` only needs to exist, be active, and belong to the same `projectId`. | Resolved H-BUG-DASHBOARD-1, option (a) — user confirmation 2026-08-06 |
| BR-13 | Role check for every action reuses the existing per-project role check pattern (`findProjectRole(caller, projectId)` with ADMIN bypass), the same mechanism `QaDashboardService` uses. | Resolved H-BUG-DASHBOARD-2 — user confirmation 2026-08-06 |

### 6.2. Input

| item | type | required | validation | notes |
|---|---|---|---|---|
| `projectId` | UUID | Yes | Must refer to an existing, active Project | FK to `tbl_dim_project.project_id` |
| `repositoryId` | UUID | Yes | Must refer to an existing, active Repository belonging to `projectId` | FK to `tbl_dim_repository.repository_id`; informational/filter-only per resolved H-1 — **not** cross-validated against `ticketId` |
| `ticketId` | UUID | Yes | Must refer to an existing, active Ticket belonging to `projectId` | FK to `tbl_dim_ticket.ticket_id`; no cross-validation against `repositoryId` (resolved H-1) |
| `internalBugCount` | integer | Yes | `>= 0` | Rejects negative/non-integer input |
| `customerBugCount` | integer | Yes | `>= 0` | Rejects negative/non-integer input |
| `note` | string | No | Max 500 characters | Optional free text |
| list `projectId` filter | UUID | No | Must be an existing Project if provided | For list screen |
| list `repositoryId` filter | UUID | No | Must be an existing Repository if provided | For list screen |
| list `page`/`size` | int | No | Governance default: `page=0`; size default/limits follow existing convention | Matches `RepositoryController` pagination |

### 6.3. Output

| item | type | format | notes |
|---|---|---|---|
| Ticket Bug Metrics list response | page DTO | `items, page, size, totalElements, totalPages` | Follows real `RepositoryPageDto` shape, **not** the raw input's `content/...` example |
| Ticket Bug Metrics list item | object | UUID + strings + ints + timestamps | Minimum fields: ticket code/title, internal/customer bug count, note, status, action affordances derived from role |
| Ticket Bug Metrics detail response | object | DTO | Includes project/repository/ticket identifiers and labels, both bug counts, note, audit fields |
| Mutation response | object | DTO | Governance modules return the updated resource DTO |
| Error response | object | `timestamp, status, error, message, traceId` | Real `ErrorResponse` shape — **not** the raw input's `code/status/message/data` example |

### 6.4. Error / Exception

| case | expected behavior | message/code | notes |
|---|---|---|---|
| Missing/blank required field (`projectId`, `repositoryId`, `ticketId`) | Reject request | HTTP 400 via validation | |
| `internalBugCount`/`customerBugCount` negative or non-integer | Reject request | HTTP 400 | As implemented, the i18n key is `Pages.TicketBugMetrics.InternalBugCount.Invalid` / `.CustomerBugCount.Invalid`, **not** the raw input's flat `validation.bug_count.min` — see §20.3 |
| `note` exceeds 500 characters | Reject request | HTTP 400 | As implemented, the i18n key is `Pages.TicketBugMetrics.Note.MaxLength`, **not** the raw input's flat `validation.note.max_length` — see §20.3 |
| `projectId`/`repositoryId`/`ticketId` not found or not active | Reject request | HTTP 404 (`NotFoundException`) | |
| Active row already exists for the Ticket on create | Reject request | HTTP 409 (`ApplicationException`-style conflict) | Direct caller to update flow instead |
| Row not found or soft-deleted on detail/update/delete | Reject request | HTTP 404 | Consistent with governance `NotFoundException` pattern |
| Caller role lacks permission for the attempted action | Reject request | HTTP 403 (`ForbiddenException`) | PM/QA required for mutation; PM/QA/DEV/ADMIN required for any access |
| Backend error / unexpected exception | Standard error envelope | 500 with `traceId` | `GlobalExceptionHandler` catch-all |

### 6.5. Boundary Value

| item | min | max | special cases | expected |
|---|---|---|---|---|
| `internalBugCount` | 0 | No documented upper bound (DB `INT`) | negative, non-integer, omitted | Reject negative/non-integer/omitted; `0` is valid |
| `customerBugCount` | 0 | No documented upper bound (DB `INT`) | negative, non-integer, omitted | Reject negative/non-integer/omitted; `0` is valid |
| `note` | 0 chars (optional) | 500 chars | exactly 500 chars, 501 chars, whitespace-only | Accept up to 500; reject 501+; whitespace-only handling not specified by raw input — logged as Open Issue |
| `page` | 0 | TBD | omitted value | Governance modules default to `0` |
| `size` | 1 | TBD | omitted value | Follow existing `RepositoryController` default/limit behavior |

### 6.6. Non-functional

| item | requirement | target / threshold | verification | notes |
|---|---|---|---|---|
| Performance | List/detail should use indexed active-row access paths | Follow soft-delete partial-index convention from `V120`/`V130` | SQL/code review + tests later | |
| Security | Authenticated, role-gated access only | BE role check on every action (per-project role via `findProjectRole`) | Web/API tests later | Reuse `QaDashboardService` pattern |
| Availability / Reliability | Create/update should be atomic for the row | Standard `@Transactional` per service method (not `TransactionTemplate`-per-item; that rule is scoped to external ingestion only) | Service/integration tests later | |
| Maintainability | Must respect existing layered architecture | No web-to-infrastructure shortcut | Arch review + tests later | Required |
| Observability / Logging | Failures surface `traceId` via standard error handling | Standard `ErrorResponse` on exceptions | Web tests later | Required |
| Compatibility | Contracts should follow real governance/`ErrorResponse`/page-DTO conventions, not the raw input's illustrative JSON examples | Minimal surprise to FE/BE | Spec review now, contract tests later | |

## 7. Acceptance Criteria

| ACID | description | testable? | notes |
|---|---|---|---|
| AC-BUG-DASHBOARD-1 | Given an authorized caller (PM/QA/DEV/ADMIN) and at least one active Ticket Bug Metrics row, when the caller opens the list (optionally filtered by Project/Repository), then the API/UI returns only non-deleted rows with ticket identifier, internal/customer bug count, note, and status. | Yes | Repository filter narrows the list by the row's stored `repositoryId` value but performs no cross-validation against `ticketId` |
| AC-BUG-DASHBOARD-13 | Given a PM or QA caller submits a create/update payload where `ticketId` belongs to a different Repository than the submitted `repositoryId`, when the request is processed, then the request is still accepted as long as `projectId`/`repositoryId`/`ticketId` each independently exist and are active (Repository is informational/filter-only, per resolved H-1). | Yes | Confirms BR-12/H-BUG-DASHBOARD-1 behavior explicitly |
| AC-BUG-DASHBOARD-2 | Given an authorized caller and no matching active rows, when the caller opens the list, then the UI shows the defined empty state instead of a broken table. | Yes | FE behavior |
| AC-BUG-DASHBOARD-3 | Given a PM or QA caller and a valid create payload for a Ticket with no existing active Ticket Bug Metrics row, when the caller submits create, then one active row is persisted and reflected in subsequent list/detail reads. | Yes | |
| AC-BUG-DASHBOARD-4 | Given a PM or QA caller and a Ticket that already has an active Ticket Bug Metrics row, when the caller submits create for that same Ticket, then the request is rejected as a conflict (409) and no duplicate row is created. | Yes | BR-6 |
| AC-BUG-DASHBOARD-5 | Given a create or update payload with `internalBugCount` or `customerBugCount` negative, non-integer, or missing, when the request is submitted, then the request is rejected and no data is changed. | Yes | BR-2 |
| AC-BUG-DASHBOARD-6 | Given a create or update payload with `note` longer than 500 characters, when the request is submitted, then the request is rejected and no data is changed. | Yes | BR-3 |
| AC-BUG-DASHBOARD-7 | Given a PM or QA caller and an existing active row, when the caller updates internal/customer bug count or note, then the latest persisted data is returned and reflected in subsequent reads, with `updated_at`/`updated_by` refreshed. | Yes | BR-11 |
| AC-BUG-DASHBOARD-8 | Given a PM or QA caller and an existing active row, when the caller performs soft delete, then the row is marked deleted (`delete_flag`, `deleted_at`, `deleted_by`, `status='DELETED'`) and no longer appears in the default active list. | Yes | BR-5 |
| AC-BUG-DASHBOARD-9 | Given a nonexistent or already soft-deleted row ID, when the caller requests detail, update, or delete through the normal flow, then the API rejects the request as not found (404) and no mutation occurs. | Yes | |
| AC-BUG-DASHBOARD-10 | Given a DEV caller, when that caller attempts create, update, or delete, then the backend rejects the action with a 403, while list/detail requests from the same caller succeed. | Yes | BR-7/BR-8 |
| AC-BUG-DASHBOARD-11 | Given a caller whose role is not PM, QA, DEV, or ADMIN, when that caller invokes any Ticket Bug Metrics endpoint (including list/detail), then the backend rejects the action and the caller cannot access the screen at all. | Yes | BR-9 |
| AC-BUG-DASHBOARD-12 | Given a backend error occurs during any Ticket Bug Metrics API call, when the error response is returned, then the response uses the standard `ErrorResponse` envelope (`timestamp, status, error, message, traceId`), not the raw input's illustrative example shape. | Yes | Based on observed code |

## 8. Examples

### 8.1. Normal Case

- Create Ticket Bug Metrics:
  - Input: PM/QA caller, active `projectId`, `repositoryId`, `ticketId` (no existing active row for that Ticket), `internalBugCount = 12`, `customerBugCount = 2`, optional `note`.
  - Expected: row persists as active; appears in default list; detail readable immediately.
- Update Ticket Bug Metrics:
  - Input: PM/QA caller, existing active row ID, changed `internalBugCount`/`customerBugCount`/`note`.
  - Expected: updated data returned and reflected in subsequent list/detail reads.
- Soft delete Ticket Bug Metrics:
  - Input: PM/QA caller, existing active row ID, soft-delete action.
  - Expected: row leaves active list and normal detail flow.

### 8.2. Error Case

- Negative bug count:
  - Input: `internalBugCount = -1`.
  - Expected: request rejected; no write committed.
- Duplicate active row for Ticket:
  - Input: create for a Ticket that already has an active row.
  - Expected: request rejected as 409 conflict.
- Unauthorized mutation:
  - Input: valid payload, DEV caller attempting create/update/delete.
  - Expected: request rejected with 403.
- No-access role:
  - Input: caller with role outside PM/QA/DEV/ADMIN attempting list.
  - Expected: request rejected; screen inaccessible.
- Detail for deleted row:
  - Input: soft-deleted row ID through normal detail flow.
  - Expected: 404-style unavailable response.

### 8.3. Boundary Case

- `internalBugCount = 0` and `customerBugCount = 0`:
  - Expected: accepted (zero bugs is a valid, meaningful state).
- `note` exactly 500 characters:
  - Expected: accepted.
- `note` 501 characters:
  - Expected: rejected.
- Non-integer bug count (e.g. `1.5` or `"abc"`):
  - Expected: rejected.

## 9. Source Availability Summary

- Strong evidence exists for Project/Repository/Ticket DB schema and FK shape, the `RepositoryController`/`RepositoryService`/`RepositoryDtos` governance pattern, the `QaDashboardService` role-gating pattern, the real error/page DTO shapes, and FE cascading-filter/form/delete/role-hiding conventions.
- The original structural gap (raw input assumed a Repository→Ticket FK that does not exist in `tbl_dim_ticket`) has been resolved by user confirmation on 2026-08-06: Repository is treated as informational/filter-only (option a), so no further schema investigation is needed on this point.
- See `docs/changes/BUG-DASHBOARD/sources.md` for detailed provenance.

## 10. Complexity Classification

```text
- Complexity: Complex
- System shape: FE+BE+DB
- Primary risk: Contract / DB / Source
- Review mode: Heavy
- Required options: Source Analysis / FE-BE Contract / DB Migration / Full Security
```

## 11. FE/BE Contract Impact

- Ticket Bug Metrics should align to the `RepositoryController`/`RepositoryDtos` governance contract shape (page DTO `items/page/size/totalElements/totalPages`, `PUT {id}/delete` soft delete, no `version` field), not the raw input's illustrative JSON examples.
- Contract items that must be locked before implementation:
  - list response wrapper shape and filter parameters (Project/Repository),
  - detail DTO shape,
  - create/update request bodies,
  - how Project/Repository/Ticket option data is loaded for the cascading dropdowns (Repository is filter/informational only — resolved H-1),
  - role-derived action affordances (mutate vs view-only vs no-access).
- FE should reuse the `DevelopmentDashboardPage`/`DevFilterBar` cascading-filter pattern, `CForm` for the create/edit Drawer, antd `Popconfirm` for delete, and inline `useAuth().user.role` checks for hiding mutate actions — all via `lib/api.ts` and TanStack Query, per `.claude/rules/20-architecture.md`.

## 12. DB/Migration Impact

- Confirmed existing DB support: `tbl_dim_project`, `tbl_dim_repository`, `tbl_dim_ticket` all exist in V4; no bug-metrics table exists yet.
- New migration required: `tbl_ticket_bug_metrics` with UUID PK (`ticket_bug_id`, `gen_random_uuid()` default), FKs to `project_id`, `repository_id`, `ticket_id` (each validated independently for existence/active status; no FK-level cross-validation between `repository_id` and `ticket_id`), `internal_bug_count INT NOT NULL DEFAULT 0`, `customer_bug_count INT NOT NULL DEFAULT 0`, `note VARCHAR(500)`, soft-delete-from-creation columns (`delete_flag BOOLEAN NOT NULL DEFAULT FALSE`, `deleted_at TIMESTAMPTZ`, `deleted_by VARCHAR(100)`, `status record_status NOT NULL DEFAULT 'ACTIVE'`), and standard audit columns — following the `V120`/`V130` convention, correcting the raw input's malformed DDL (resolved H-BUG-DASHBOARD-3, user confirmation 2026-08-06). Active-row uniqueness index on `ticket_id` (partial index filtered on `delete_flag = FALSE`) enforces BR-1/BR-6 (one active row per Ticket).
- Target migration version: next unused version number above the current highest (`V508`), i.e. `V509__ticket_bug_metrics.sql` (or the next free number if `V509` is taken by the time implementation starts) — resolved H-BUG-DASHBOARD-3.
- Repository↔Ticket relationship (H-BUG-DASHBOARD-1) is resolved: option (a) — `repository_id` is informational/filter-only, no cross-validation against `ticket_id`.

## 13. Security/Privacy Impact

- All Ticket Bug Metrics endpoints must require authenticated session-based/JWT-based access.
- Access must be role-gated in backend code using the `QaDashboardService`-style per-project role check: PM/QA can mutate, DEV can view only, all other roles (including unauthenticated or role-less callers) must be blocked from the entire screen, not just from mutation actions.
- Error handling must use the standard `ErrorResponse` envelope and avoid leaking internal details.
- No special PII was observed in the raw input; audit fields (`createdBy`, `updatedBy`, `deletedBy`) are operationally sensitive and should follow existing conventions.

## 14. Operation/Maintenance Impact

- This feature adds a new governance-style module surface in BE and FE, following the Repository module's shape closely enough that it should not introduce new architectural patterns.
- Supportability depends on keeping error handling, role-gating, and transactional behavior consistent with `RepositoryService`/`QaDashboardService`.
- Since Repository is filter/informational only (resolved H-1), operational debugging is simplified — no cross-table validation path to trace for Repository↔Ticket mismatches.

## 15. Test Strategy Summary

- Backend unit tests:
  - service validation rules (bug count non-negative, note length, duplicate-ticket conflict),
  - role-based authorization enforcement (PM/QA mutate, DEV view-only, others blocked),
  - Repository↔Ticket independence (create/update accepted even when `ticketId` and `repositoryId` are unrelated, per resolved H-1).
- Backend web/integration tests:
  - list/detail/create/update/delete routes,
  - error envelope and status mapping,
  - migration/index behavior for soft delete and any new uniqueness constraint.
- Frontend tests:
  - page empty/loading/error states,
  - cascading Project→Repository→Ticket dropdown behavior,
  - create/edit/delete flows,
  - role-based UI hiding for DEV vs PM/QA,
  - API helper and error-handling behavior.
- Contract validation:
  - confirm page DTO shape and detail DTO shape before FE implementation.

## 16. Human Decision Required

| ID | decision item | reasons | owner | status |
|---|---|---|---|---|
| H-BUG-DASHBOARD-1 | How should the Repository↔Ticket relationship be handled in Ticket Bug Metrics, given `tbl_dim_ticket` has no `repository_id` FK? **Resolved: option (a)** — Repository selection is informational/filter-only, with no cross-validation against the selected Ticket. | Raw input's cascading dropdown and validation rule ("ticket must belong to repository") cannot be enforced as literally described at the DB layer | Product/Tech Lead | Resolved 2026-08-06 |
| H-BUG-DASHBOARD-2 | Confirm role enforcement should reuse the `QaDashboardService`-style per-project role check (`findProjectRole` + ADMIN bypass) for PM/QA mutate, DEV view-only, others blocked, rather than introducing a new permission model. **Resolved: reuse per-project role check.** | Matches raw input intent closely and avoids introducing a new permission model | Tech Lead | Resolved 2026-08-06 |
| H-BUG-DASHBOARD-3 | Confirm the new migration should correct the raw input's malformed DDL (missing `DEFAULT` on `gen_random_uuid()`, illegally nested `CREATE UNIQUE INDEX`, plain `delete_flag INT`) and instead follow the `V120`/`V130` soft-delete-from-creation convention, and confirm the target migration version number. **Resolved: implement as proposed** — soft-delete-from-creation columns, active-row unique index on `ticket_id`, target version `V509` (or next free number). | Raw input DDL cannot execute as written | Tech Lead/DBA | Resolved 2026-08-06 |

## 17. Assumptions and Inference Log

| ID | assumption | basis | risk | need confirmation? |
|---|---|---|---|---|
| A-BUG-DASHBOARD-1 | `01_raw-input.md` is the primary business input for this phase | No other ticket body/basic design/memo exists in workspace | Medium | Yes |
| A-BUG-DASHBOARD-2 | Ticket Bug Metrics list/detail/error contracts follow real code conventions (`items/page/size/totalElements/totalPages`, `ErrorResponse(timestamp,status,error,message,traceId)`), not the raw input's illustrative JSON examples | Observed source in `RepositoryDtos.java`, `ErrorResponse.java` | Low | No |
| A-BUG-DASHBOARD-3 | Default active list excludes soft-deleted rows | Raw input + governance pattern | Low | No |
| A-BUG-DASHBOARD-4 | No optimistic-locking `version` field is required, consistent with `RepositoryModel` having none | Observed source | Low | No |
| A-BUG-DASHBOARD-5 | Soft delete route is `PUT /api/v1/ticket-bug-metrics/{id}/delete`, matching `RepositoryController`'s `PUT {id}/delete` convention. As implemented, the resource segment is `ticket-bug-metrics`, not the raw input's proposed `ticket-bugs` — see §20 for the full implemented route list. | Observed source (`TicketBugMetricsController`) | Low | No |
| A-BUG-DASHBOARD-6 | New migration targets `V509` (or next free number if taken by implementation time) | Highest existing migration observed (`V508`); resolved H-BUG-DASHBOARD-3 | Low | No |
| A-BUG-DASHBOARD-7 | Repository is stored on the row purely for filter/display; no application-level or DB-level cross-validation against `ticketId` is implemented anywhere in this feature | Resolved H-BUG-DASHBOARD-1, option (a) | Low | No |

## 18. Open Issues

| ID | issue | impact | owner | status |
|---|---|---|---|---|
| OI-BUG-DASHBOARD-1 | Ticket Bug Metrics implementation code does not yet exist in BE/FE source | Normal implementation work remains for Phase 3 | Team | Open |
| OI-BUG-DASHBOARD-2 | Exact FE route/menu placement for the new screen is not specified in raw input | Needs a normal design decision during implementation | Tech Lead | Resolved — route `/{lang}/ticket-bug-metrics`, registered by hand in `App.tsx`, tab surfaced via `RoleTabs.tsx`/`dashboardRoutes.ts` (see §20.1) |
| OI-BUG-DASHBOARD-3 | i18n resource file locations/keys are only referenced generically in raw input, not confirmed against actual `messages_*.properties`/i18next namespace files | Needs verification during implementation | Team | Resolved — `Pages.TicketBugMetrics.*` nested keys added to `en/vi/ja locale.json`, following the `Pages.Repository.*` convention (see §20.3) |
| OI-BUG-DASHBOARD-4 | Exact seeded role codes returned by `findProjectRole`/`tbl_dim_role` for "DEV" were not verified line-by-line this phase | Needs confirmation before coding role checks | Tech Lead | Resolved — `V4__init_shema_v2.sql:1363` seeds `tbl_dim_role.role_name = 'DEV'`; `DashboardProjectAccessJdbcAdapter.findProjectRole()` returns `UPPER(BTRIM(r.role_name))`, so the value handed to `TicketBugMetricsService.resolveAccess` is always `"DEV"` regardless of case/whitespace in storage. Matches the literal comparison in `resolveAccess`. See §20.4. |
| OI-BUG-DASHBOARD-5 | Whitespace-only `note` handling (trim vs reject vs accept) is not specified by raw input | Minor UX/validation detail; should be decided during implementation, low risk | Team | Resolved — implemented as trim-to-null in `TicketBugMetricsService.normalizeCountsAndNote` |

## 19. Phase 1 Output

### 19.1. Can Phase 3 proceed using only this Spec Pack?

**Yes.**

All three original blocking contract questions (H-BUG-DASHBOARD-1, -2, -3) were resolved by user confirmation on 2026-08-06. This spec pack is now sufficient to hand off into Phase 3, with the remaining open issues being implementation-detail and review concerns rather than unresolved ticket intent.

### 19.2. If not, what information is still missing?

- No blocking information remains. Remaining Open Issues (§18) are non-blocking implementation details: exact FE route/menu placement, i18n resource file locations, exact seeded role codes for "DEV", and whitespace-only `note` handling.

### 19.3. What questions require human confirmation?

- No blocking confirmation questions remain for the original Phase 1 scope.
- Remaining Open Issues can be resolved during Phase 3 without reopening the ticket contract.

### 19.4. What specialist packs are needed?

- DB/Migration review (to finalize the exact migration version number and DDL against the latest schema state at implementation time).
- FE/BE Contract review (list/detail/create/update DTO field names).

## 20. Implementation Addendum (post Phase 3)

This section reconciles the spec above with the code actually shipped
(`TicketBugMetricsController.java`, `EDCAP_FE/src/lib/api.ts`, `EDCAP_FE/src/pages/ticket-bug-metrics/`,
`V509__ticket_bug_metrics.sql`, `self-review.md`). Everywhere this section conflicts with §§1-19
above, this section is authoritative.

### 20.1. Implemented routes (base path `/api/v1/ticket-bug-metrics`)

| Method | Path | Params / Body | Response |
|---|---|---|---|
| `GET` | `/` | query: `projectId?`, `repositoryId?`, `page=0`, `size=20` | `TicketBugMetricsPageDto { items, page, size, totalElements, totalPages }` |
| `GET` | `/{id}` | path: `id` | `TicketBugMetricsDto` |
| `POST` | `/` | body: `{ projectId, repositoryId, ticketId, internalBugCount, customerBugCount, note? }` | `201` + `TicketBugMetricsDto` |
| `PUT` | `/{id}` | body: `{ internalBugCount, customerBugCount, note? }` | `TicketBugMetricsDto` |
| `PUT` | `/{id}/delete` | path: `id` | `TicketBugMetricsDto` (soft-deleted) |
| `GET` | `/ticket-options` | query: `projectId` (required), `repositoryId?` | `TicketOptionDto[]` |
| `GET` | `/options` | query: `projectId?`, `repositoryId?` | `TicketBugMetricsOptionsDto { projects, repositories, tickets }` |
| `GET` | `/access` | query: `projectId?` | `204`/empty; throws `ForbiddenException` if the caller lacks view access |

`/ticket-options`, `/options`, and `/access` were not in the original raw input or in §6/§13 above —
they exist to support the FE's cascading Project→Repository→Ticket filter/select and the route
guard (`RequireTicketBugMetricsAccess.tsx`). `self-review.md` documents `/ticket-options` and
`/access`; `/options` should be treated as part of the same addition (add it to `self-review.md`'s
route/file table as well).

### 20.2. FE route and role surfacing

- Route: `/{lang}/ticket-bug-metrics`, registered by hand under the `:lang` route block in
  `EDCAP_FE/src/App.tsx`, wrapped in `RequireTicketBugMetricsAccess.tsx`.
- Tab entry: `dashboardRoutes.ts` (`TICKET_BUG_METRICS -> "ticket-bug-metrics"`) and `RoleTabs.tsx`
  (`DashboardRoleView` includes `"ticket-bug-metrics"`; visible to `ADMIN`, `PM`, `QA`, `DEV`).
  Label key: `Pages.Dashboard.roleTabs.ticketBugMetrics`.

### 20.3. Implemented i18n keys

All strings live under `Pages.TicketBugMetrics.*` in `en/vi/ja locale.json` (not the raw input's
flat `validation.*` keys from §3.3 of the raw input, and not reused from `Pages.Repository.*`):
`title, description, create, createTitle, editTitle, detailTitle, loading, detailEmpty, save,
cancel, close, edit, delete, project, repository, ticket, internalBugCount, customerBugCount,
note, status, all, filters, apply, statusValues.ACTIVE, statusValues.DELETED, emptyValue,
updatedAt, deleteConfirm, createSuccess, updateSuccess, deleteSuccess, unknownError,
Project.Required, Project.NotFound, Repository.Required, Repository.NotFound, Ticket.Required,
Ticket.NotFound, Ticket.AlreadyExists, InternalBugCount.Invalid, CustomerBugCount.Invalid,
Note.MaxLength, NotFound`. Role-tab label lives separately at `Pages.Dashboard.roleTabs.ticketBugMetrics`.

### 20.4. OI-BUG-DASHBOARD-4 resolution — seeded "DEV" role code

Verified by reading the seed migration and the role-resolution query directly (no live DB needed):

- `EDCAP_BE/src/main/resources/db/migration/V4__init_shema_v2.sql:1361-1366` seeds
  `tbl_dim_role (role_name, description)` including `('DEV', 'Developer')`.
- `DashboardProjectAccessJdbcAdapter.findProjectRole(caller, projectId)`
  (`EDCAP_BE/.../infrastructure/persistence/adapter/DashboardProjectAccessJdbcAdapter.java:229-254`)
  resolves the caller's per-project role via `SELECT UPPER(BTRIM(r.role_name)) AS role ...` across
  `tbl_auth_member_project_role` / `tbl_auth_member_access_scope` / team-based grants, always
  returning an upper-cased, trimmed string.
- Net effect: for a user seeded/assigned the `DEV` role, `findProjectRole` always returns the
  literal `"DEV"`, which is exactly what `TicketBugMetricsService.resolveAccess` compares against
  for `VIEW_ONLY` access. No mismatch is possible from casing or whitespace in the stored
  `role_name`, since the query normalizes it before comparison.

OI-BUG-DASHBOARD-4 is closed.
- Security/Authorization review (role-gating implementation against `QaDashboardService`).
- Test Strategy review.
