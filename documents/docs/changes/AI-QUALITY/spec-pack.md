# Spec Pack

**Ticket ID**: AI-QUALITY
**Create date**: 2026-08-17 07:10:27
**Author**: BRYCENVN\nvt_dung
**Update date**: 2026-08-17 07:34:25

## 1. Context / Purpose

EDCAP tracks engineering delivery metrics (GitHub/Jira/CircleCI) per Project/Repository/Ticket.
There is currently no way to record or manage an "AI Quality %" score per ticket. This feature adds
a management screen and backing CRUD API so ADMIN/QA/PM users can record, view, edit, and soft-delete
one AI Quality rate per ticket, scoped under its repository and project. Source: `01_raw-input.md`.
RBAC and soft-delete conventions follow the existing **BUG-DASHBOARD** (Ticket Bug Metrics) feature,
which has an almost identical Project/Repository/Ticket shape and the same PM/QA/DEV access
narrative (see §16/§17).

## 2. Scope

### 2.1. Within range

- New table `tbl_dim_ai_quality` (one active row per ticket) + Flyway migration.
- Backend CRUD API: create, update, soft-delete, get-by-id, list (filter by project/repository/ticket).
- Frontend management screen: Project/Repository filter dropdowns, ticket table, create/edit drawer
  with cascading Project → Repository → Ticket dropdowns and an AI Quality % input.
- Validation of `project_id`/`repository_id`/`ticket_id`/`ai_quality_rate` per raw input's rules.
- RBAC gating: ADMIN (global) and PM/QA (per-project role) can create/update/delete; DEV (per-project
  role) is view-only; all other callers are denied access to the screen and API entirely — mirrors
  BUG-DASHBOARD's `TicketBugMetricsService` access model (see §17 A-AI-QUALITY-6).
- Soft delete only (no physical delete), using the boolean `delete_flag` + `deleted_at`/`deleted_by`
  + `status` convention, mirroring BUG-DASHBOARD's `tbl_ticket_bug_metrics` (see §17 A-AI-QUALITY-7).

### 2.2. Out of range

- Bulk import/export of AI Quality rates.
- Automatic computation of AI Quality (e.g. from CI/AI-review tooling) — this ticket is manual
  CRUD only; automatic population is a future ticket.
- Historical audit trail / version history of rate changes beyond the standard `updated_at/updated_by`.
- Any change to `tbl_dim_project`, `tbl_dim_repository`, or `tbl_dim_ticket` schemas.
- Introducing a generic `ApiResponse<T>` envelope or Spring `MessageSource`-based i18n — neither
  exists in this codebase (see §17 Assumptions) and neither will be introduced by this ticket.

## 3. Terminology

| terms | meaning | notes |
|---|---|---|
| Project | Top-level grouping entity, `tbl_dim_project` | Existing table |
| Repository | Git repository under a Project, `tbl_dim_repository` | Existing table |
| Ticket | Work item under a Project, `tbl_dim_ticket` | Existing table; FK target only in this ticket |
| AI Quality Rate | Decimal percentage (0–100, 2dp) recorded per ticket | New concept, `ai_quality_rate` column |
| `record_status` | Postgres enum `ACTIVE, INACTIVE, ARCHIVED, DELETED` | Existing enum, reused for `status` column |
| Soft delete | Row remains in DB, marked via `delete_flag BOOLEAN` + `deleted_at`/`deleted_by` + `status='DELETED'` instead of physical `DELETE` | Convention taken from `tbl_ticket_bug_metrics` (BUG-DASHBOARD) |
| `PageResult<T>` | Existing generic pagination result type | `application/usecase/common/PageResult.java` |
| Access tier | `MUTATE` (ADMIN global, or PM/QA per-project role) / `VIEW_ONLY` (DEV per-project role) / `NONE` (all other callers) | Local enum pattern, mirrors `TicketBugMetricsService.Access` |

## 4. As-Is

No mechanism exists today to record an AI Quality metric against a ticket. `tbl_dim_ticket` has no
AI-quality-related column, and no controller/service/adapter exists for this data.

## 5. To-Be

A new `tbl_dim_ai_quality` table stores one active row per ticket (enforced by a partial unique
index scoped to non-deleted, ACTIVE rows). A new hexagonal CRUD slice (controller → service → port
→ adapter → mapper) exposes create/update/soft-delete/get/list endpoints, mirroring
`UserAccountAdminController`'s layering and `TicketBugMetricsService`'s access-resolution/soft-delete
pattern. A new FE page under `pages/` renders the filterable table and create/edit drawer, with a
route guard mirroring `RequireTicketBugMetricsAccess` and mutate-button gating mirroring
`TicketBugMetricsPage`'s `canMutate` logic, using TanStack Query hooks through `lib/api.ts` per
[[20-architecture.md]].

## 6. Detailed specification

### 6.1. Business Rules

- BR-1: One ticket has at most one **active** `tbl_dim_ai_quality` row, enforced by a partial unique
  index on `ticket_id` (or `(repository_id, ticket_id)`) `WHERE delete_flag = FALSE AND deleted_at
  IS NULL AND status = 'ACTIVE'`, mirroring `uq_ticket_bug_metrics_active_ticket`.
- BR-2: `POST` (create) with a `ticket_id` that already has an active row returns `409 Conflict`;
  caller must use `PUT` to update the existing row instead.
- BR-3: `repository_id` must belong to the given `project_id`; `ticket_id` must belong to the given
  `repository_id`. Mismatches are validation errors, not silent corrections.
- BR-4: Soft delete never physically removes the row; it sets `delete_flag = TRUE`, `deleted_at`/
  `deleted_by`, and `status = 'DELETED'` (mirrors `TicketBugMetricsService.softDelete()` /
  `tbl_ticket_bug_metrics`). Soft-deleted rows are excluded from `GET`/list results and from the
  uniqueness constraint, so a ticket may be re-created after its prior row was soft-deleted.
- BR-5: Access is resolved per caller as `MUTATE` / `VIEW_ONLY` / `NONE`, mirroring
  `TicketBugMetricsService.resolveAccess()`: global role `ADMIN` → `MUTATE`; otherwise the caller's
  **per-project role** (not the global `AppUser.Role`) is checked — `PM` or `QA` → `MUTATE`, `DEV` →
  `VIEW_ONLY`, anything else (including no project role) → `NONE`. Create/update/delete require
  `MUTATE`; get/list require at least `VIEW_ONLY` or `MUTATE` (i.e. any resolved project role or
  ADMIN); `NONE` is denied both read and write access to the screen and API.
- BR-6: `ai_quality_rate` is stored as `DECIMAL(5,2)`; valid range is 0.00–100.00 inclusive.

### 6.2. Input

| item | type | required | validation | notes |
|---|---|---|---|---|
| project_id | UUID | yes | must exist in `tbl_dim_project` and be active | path/query on list; body on create/update |
| repository_id | UUID | yes | must exist in `tbl_dim_repository`; must belong to `project_id` | body on create/update; query filter on list |
| ticket_id | UUID | yes | must exist in `tbl_dim_ticket`; must belong to `repository_id` | body on create/update; query filter on list |
| ai_quality_rate | BigDecimal | yes | 0.00 ≤ value ≤ 100.00, scale ≤ 2 | body on create/update |
| (list filter) page | int | no | ≥ 0, default 0 | query param, `PageResult<T>` convention |
| (list filter) size | int | no | > 0, default per platform norm | query param, `PageResult<T>` convention |
| (list filter) projectId / repositoryId / ticketId | UUID | no | must be valid UUID if present | optional query filters, AND-combined |
| (list filter) search | string | no | free text, matched against ticket external key / title | optional query filter, AND-combined with the above; unlike `TicketBugMetricsPage`'s decorative-only `CSearch` state, this is wired end-to-end (controller → service → mapper) as a real filter — a deliberate improvement over the BUG-DASHBOARD reference, confirmed post-implementation |

Ticket dropdown scoping (create/edit drawer): shows **all** tickets belonging to the selected
`repository_id`, regardless of whether they already have an active AI Quality row (Option A —
confirmed by user, see §17 A-AI-QUALITY-10). Selecting an already-tracked ticket and submitting
`Create` will surface the standard 409 Conflict from BR-2; the FE is expected to show that error
through the existing localized toast/global error notification rather than pre-filtering the dropdown.

### 6.3. Output
| item | type | format | notes |
|---|---|---|---|
| ticket_ai_quality_id | UUID | string | PK of the row |
| project_id / repository_id / ticket_id | UUID | string | FK echoes |
| ai_quality_rate | BigDecimal | decimal, 2dp | e.g. `85.50` |
| status | string | `record_status` value | e.g. `ACTIVE` |
| created_at / updated_at | string | ISO-8601 timestamptz | standard audit fields |
| created_by / updated_by | string | actor identifier | standard audit fields |
| list response | `PageResult<AiQualityDto>`-shaped DTO | JSON | mirrors `UserAccountPageDto` pattern (`items, page, size, totalElements, totalPages`) |

### 6.4. Error / Exception
| case | expected behavior | message/code | notes |
|---|---|---|---|
| Missing/invalid `project_id`, or well-formed `project_id` that doesn't exist / is inactive | 400 Bad Request (`Pages.AiQuality.Project.NotFound`, via `BusinessRuleException`, never `NotFoundException`/404) | validation error code (exact code TBD per §16 i18n decision) | BR-3 |
| `repository_id` doesn't belong to `project_id` | 400 Bad Request | validation error code | BR-3 |
| `ticket_id` doesn't belong to `repository_id` | 400 Bad Request | validation error code | BR-3 |
| `ai_quality_rate` out of 0–100 range or wrong scale | 400 Bad Request | validation error code | BR-6 |
| Create with `ticket_id` already having an active row | 409 Conflict | conflict error code | BR-2 |
| Get/Update/Delete on non-existent or already-soft-deleted id | 404 Not Found | not-found error code | BR-4 |
| Caller's resolved access is `NONE` (no project role, not ADMIN) | 403 Forbidden on any endpoint (read or write) | forbidden error code | BR-5 |
| Caller's resolved access is `VIEW_ONLY` (DEV project role) calling create/update/delete | 403 Forbidden | forbidden error code | BR-5 |
| Any unhandled exception | mapped by `GlobalExceptionHandler` | `ErrorResponse(timestamp, status, errorCode, message, traceId)` | per [[30-security.md]] — no ad-hoc `ResponseEntity` status codes in controllers |

### 6.5. Boundary Value
| item | min | max | special cases | expected |
|---|---|---|---|---|
| ai_quality_rate | 0.00 | 100.00 | -0.01, 100.01, null, scale > 2 (e.g. 50.555) | reject outside [0,100]; reject null; reject scale > 2 |
| project_id/repository_id/ticket_id | n/a | n/a | empty string, malformed UUID, well-formed UUID that doesn't exist | reject with 400 (malformed/missing) or 400 validation (doesn't exist), not 500 |

### 6.6. Non-functional
| item | requirement | target / threshold | verification | notes |
|---|---|---|---|---|
| Performance | List endpoint responds within existing platform norms | consistent with other `PageResult<T>` list endpoints | integration/manual timing | No new performance requirement beyond existing conventions |
| Security | RBAC enforced server-side on every write endpoint; no client-only gating | 100% of write endpoints checked | unit test per endpoint | Per [[30-security.md]] and BR-5 |
| Availability / Reliability | Uniqueness enforced at DB level (not just app level) | partial unique index present in migration | migration review | Prevents race-condition duplicates |
| Maintainability | Follows existing hexagonal CRUD pattern (controller/service/port/adapter/mapper) | matches `UserAccountAdminController` structure | code review | See §11 |
| Observability / Logging | Errors logged server-side with `traceId`; no PII/secrets in logs | 100% of error paths | code review | Per [[30-security.md]] and [[ticket-rules]] "Do not export secrets/PII to logs" |
| Compatibility | No schema change to `tbl_dim_project`/`tbl_dim_repository`/`tbl_dim_ticket` | zero ALTER statements on those tables | migration review | Per §2.2 out-of-scope |

## 7. Acceptance Criteria
| ACID | description | testable? | notes |
|---|---|---|---|
| AC-AI-QUALITY-1 | Creating an AI Quality row with valid project/repository/ticket IDs and a rate in [0,100] succeeds and returns the created row | Yes | BR-1, BR-6 |
| AC-AI-QUALITY-2 | Creating a second row for a ticket that already has an active row returns 409 Conflict | Yes | BR-2 |
| AC-AI-QUALITY-3 | Creating a row where `repository_id` does not belong to `project_id` returns a 400 validation error | Yes | BR-3 |
| AC-AI-QUALITY-4 | Creating a row where `ticket_id` does not belong to `repository_id` returns a 400 validation error | Yes | BR-3 |
| AC-AI-QUALITY-5 | Creating a row with `ai_quality_rate` = 100.00 succeeds; with 100.01 or -0.01 fails validation | Yes | BR-6, §6.5 |
| AC-AI-QUALITY-6 | Updating an existing row's `ai_quality_rate` persists the new value and updates `updated_at/updated_by` | Yes | BR-1 |
| AC-AI-QUALITY-7 | Soft-deleting a row makes it absent from get-by-id and list results, without a physical DELETE | Yes | BR-4 |
| AC-AI-QUALITY-8 | After a row is soft-deleted, creating a new row for the same ticket succeeds (no false 409) | Yes | BR-4 |
| AC-AI-QUALITY-9 | List endpoint filters correctly by project_id and/or repository_id and/or ticket_id and returns a `PageResult`-shaped response | Yes | §6.3 |
| AC-AI-QUALITY-10 | A caller resolved to `VIEW_ONLY` (DEV project role) receives 403 Forbidden on create/update/delete but 200 on get/list | Yes | BR-5 |
| AC-AI-QUALITY-11 | A caller resolved to `NONE` (no project role and not global ADMIN) receives 403 Forbidden on every endpoint, including get/list | Yes | BR-5 |
| AC-AI-QUALITY-12 | A caller with global role `ADMIN` receives `MUTATE` access regardless of per-project role | Yes | BR-5 |
| AC-AI-QUALITY-13 | List endpoint's `search` query param filters results by ticket external key / title, AND-combined with `projectId`/`repositoryId`/`ticketId` | Yes | §6.2 (post-implementation addition) |

## 8. Examples

### 8.1. Normal Case

Create request with `project_id`, `repository_id` (belongs to project), `ticket_id` (belongs to
repository), `ai_quality_rate = 92.50` by an authorized role → 201/200 with the created row echoing
all fields plus `ticket_ai_quality_id`, `status = ACTIVE`, audit fields populated.

### 8.2. Error Case

Same request repeated (same `ticket_id`, still active) → 409 Conflict, error code indicating the
ticket already has an AI Quality row, instructing the caller to use `PUT /api/v1/ai-qualities/{id}`.

### 8.3. Boundary Case

`ai_quality_rate = 100.00` → accepted (upper inclusive bound). `ai_quality_rate = 100.01` → rejected,
400 validation error. `ai_quality_rate = 50.555` (scale 3) → rejected, 400 validation error (BR-6).

## 9. Source Availability Summary

Full inventory in `sources.md`. Summary: one raw-input design doc (reference-only for API/DB
shapes); relevant conventions (dim tables, `record_status`, pagination wrapper, one reference CRUD
feature) were verified directly against `EDCAP_BE` source. RBAC and soft-delete conventions were
additionally verified against **BUG-DASHBOARD** (`TicketBugMetricsService.java`,
`V509__ticket_bug_metrics.sql`), per user direction, and resolve what were previously open Human
Decisions (§17 A-AI-QUALITY-6, A-AI-QUALITY-7). No existing tests exist for this not-yet-built
feature. Several conventions named in the raw input (`ApiResponse<T>`, Spring
`MessageSource`/`.properties` i18n, `@PreAuthorize`) were checked and **do not exist** in the
codebase — spec follows verified real conventions instead (§17).

## 10. Complexity Classification

```text
- Complexity: Standard
- System shape: FE+BE / DB
- Primary risk: Contract
- Review mode: Standard
- Required options: FE-BE Contract, DB Migration
```

## 11. FE/BE Contract Impact

New endpoints under `/api/v1/ai-qualities` (`POST`, `PUT /{id}`, `PUT /{id}/delete`,
`GET /{id}`, `GET` list with `page`/`size`/`projectId`/`repositoryId`/`ticketId` query params),
plus `GET /api/v1/ai-qualities/access` mirroring `GET /api/v1/ticket-bug-metrics/access` for the FE
route guard. When called with `projectId`, `access` resolves per-project access as usual
(`requireAccess` → `requireViewAccess`); when called without `projectId` (e.g. first render before
the page's own effect selects a project and syncs it into the URL), it falls back to
`requireAnyAccess` — any authenticated caller passes, mirroring
`TicketBugMetricsService.requireAnyAccess`, since project-role resolution cannot run without a
`projectId`. Response DTOs are new (`AiQualityDto`, `AiQualityPageDto` mirroring
`UserAccountPageDto`/`PageResult<T>`). No existing FE/BE contract is modified. All FE calls go
through `lib/api.ts` per [[20-architecture.md]]; list/detail data fetched via TanStack Query hooks,
no direct `fetch`. Ticket dropdown in the create/edit drawer reuses the existing ticket-by-repository
lookup unfiltered (Option A, §17 A-AI-QUALITY-10) — no new "available tickets" endpoint needed.

## 12. DB/Migration Impact

New Flyway migration adds `tbl_dim_ai_quality`, mirroring `V509__ticket_bug_metrics.sql`'s
`tbl_ticket_bug_metrics` shape:
```sql
CREATE TABLE IF NOT EXISTS tbl_dim_ai_quality (
    ticket_ai_quality_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES tbl_dim_project(project_id),
    repository_id UUID NOT NULL REFERENCES tbl_dim_repository(repository_id),
    ticket_id UUID NOT NULL REFERENCES tbl_dim_ticket(ticket_id),
    ai_quality_rate DECIMAL(5,2) NOT NULL DEFAULT 0,
    delete_flag BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by VARCHAR(100),
    status record_status NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT ck_ai_quality_rate_range CHECK (ai_quality_rate >= 0 AND ai_quality_rate <= 100)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_quality_active_ticket
    ON tbl_dim_ai_quality (ticket_id)
    WHERE delete_flag = FALSE AND deleted_at IS NULL AND status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_ai_quality_project_status
    ON tbl_dim_ai_quality (project_id, status)
    WHERE delete_flag = FALSE AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_ai_quality_repository
    ON tbl_dim_ai_quality (repository_id)
    WHERE delete_flag = FALSE AND deleted_at IS NULL;
```
No changes to `tbl_dim_project`, `tbl_dim_repository`, or `tbl_dim_ticket`.

## 13. Security/Privacy Impact

Access is resolved server-side per BR-5 (`MUTATE`/`VIEW_ONLY`/`NONE`), mirroring
`TicketBugMetricsService.resolveAccess()` — ADMIN (global) or PM/QA (per-project role) get
`MUTATE`; DEV (per-project role) gets `VIEW_ONLY`; everyone else gets `NONE`, denied even read
access. Enforcement happens in the service layer (`requireViewAccess()`/`requireMutateAccess()`
equivalents), not in the controller, and not via `@PreAuthorize` (none exists in this codebase).
The FE route guard (mirroring `RequireTicketBugMetricsAccess`) and mutate-button gating (mirroring
`TicketBugMetricsPage.canMutate`) are UX-only; the backend is authoritative per [[30-security.md]].
No PII is stored in `tbl_dim_ai_quality` beyond existing `created_by`/`updated_by`/`deleted_by`
actor identifiers already used elsewhere. Error responses must use the standard
`ErrorResponse(timestamp, status, errorCode, message, traceId)` shape; no stack traces to the client.

## 14. Operation/Maintenance Impact

New table requires no special operational runbook beyond standard Flyway migration deployment.
Soft-deleted rows accumulate over time like other soft-deleted dim tables; no additional archival
process is in scope for this ticket.

## 15. Test Strategy Summary

Per [[40-testing.md]]: backend unit tests mock the new repository port (Mockito), not domain
entities; one test class per new production class (controller/service/adapter/mapper); `@WebMvcTest`
slice for the controller, not full `@SpringBootTest`; `ArchitectureTest` must remain green (new
classes must respect hexagonal layering). Frontend: Vitest + Testing Library for the new page/hooks/
components; no Playwright E2E required for this ticket (default — see §17 A-AI-QUALITY-11), may be
added later if QA requests it. Every AC in §7 maps to at least one test case.

## 16. Human Decision Required

None outstanding. All items formerly listed here are resolved — see §17 Assumptions and Inference
Log (A-AI-QUALITY-6 through A-AI-QUALITY-12) for the resolutions and their basis:
- RBAC model → A-AI-QUALITY-6 (BUG-DASHBOARD precedent)
- Soft-delete convention → A-AI-QUALITY-7 (BUG-DASHBOARD precedent)
- Validation-error localization approach → A-AI-QUALITY-8 (default: FE-only i18next, no real alternative exists in codebase)
- Final REST path prefix → A-AI-QUALITY-9 (default: `/api/v1/ai-qualities`, top-level)
- List/filter pagination shape → A-AI-QUALITY-10 (default: standard `PageResult<T>` `page`/`size` + optional ID filters)
- Ticket dropdown scoping → A-AI-QUALITY-11 (Option A, user-confirmed)
- E2E test requirement → A-AI-QUALITY-12 (default: none for this ticket)

## 17. Assumptions and Inference Log
| ID | assumption | basis | risk | need confirmation? |
|---|---|---|---|---|
| A-AI-QUALITY-1 | No `ApiResponse<T>` envelope will be introduced; responses use `ResponseEntity<T>`/`PageResult<T>` directly, matching `UserAccountAdminController`/`PageResult.java` | Verified: no `ApiResponse` class exists anywhere in `EDCAP_BE` | Low | No |
| A-AI-QUALITY-2 | i18n for validation messages will not use Spring `MessageSource`; BE returns stable error codes, FE localizes via existing i18next mechanism | Verified: no `LocaleResolver`/`MessageSource`/`.properties` files exist; i18n is FE-only JSON | Medium — changes raw input's described contract | No — resolved, see A-AI-QUALITY-8 |
| A-AI-QUALITY-3 | RBAC is enforced via manual role/access resolution in the service layer (no `@PreAuthorize`) | Verified: zero `@PreAuthorize` usages in `EDCAP_BE` | Low | No |
| A-AI-QUALITY-4 | Re-creating a row for a ticket after its previous row was soft-deleted is allowed (partial unique index only covers active rows) | Confirmed by BUG-DASHBOARD's identical pattern: `uq_ticket_bug_metrics_active_ticket` is a partial index `WHERE delete_flag = FALSE AND deleted_at IS NULL AND status = 'ACTIVE'` | Low | No |
| A-AI-QUALITY-5 | `tbl_dim_ticket` is used only as an FK target and its non-standard audit/soft-delete columns are not a pattern to replicate for the new table | Verified: `tbl_dim_ticket`'s audit columns are nullable and it has no `delete_flag`, unlike `tbl_dim_project`/`tbl_dim_repository` | Low | No |
| A-AI-QUALITY-6 | RBAC model follows BUG-DASHBOARD's `TicketBugMetricsService.resolveAccess()` exactly: global `ADMIN` → `MUTATE`; per-project role `PM`/`QA` → `MUTATE`; per-project role `DEV` → `VIEW_ONLY`; anything else (including no project role) → `NONE`, denied read and write. This uses the per-project role string (via the same lookup mechanism as `qaDashboardRepository.findProjectRole`), not the global `AppUser.Role` enum, so no new global roles need to be added | User-directed: reuse BUG-DASHBOARD's screen as the reference for this exact question; source-verified in `TicketBugMetricsService.java` L213-262 | Low — directly confirmed by user, matches raw input's PM/QA-mutate/DEV-view-only narrative and resolves its self-contradiction (DEV is view-only, not "no access") | No — resolved |
| A-AI-QUALITY-7 | Soft-delete convention follows BUG-DASHBOARD's `tbl_ticket_bug_metrics` exactly: `delete_flag BOOLEAN NOT NULL DEFAULT FALSE`, `deleted_at TIMESTAMPTZ` (nullable), `deleted_by VARCHAR(100)` (nullable), plus `status record_status DEFAULT 'ACTIVE'`; partial unique index scoped to `delete_flag = FALSE AND deleted_at IS NULL AND status = 'ACTIVE'` | User-directed: reuse BUG-DASHBOARD's screen as the reference for this exact question; source-verified in `V509__ticket_bug_metrics.sql` | Low — directly confirmed by user | No — resolved |
| A-AI-QUALITY-8 | Validation-error localization: BE returns a stable error code via `ErrorResponse`/`GlobalExceptionHandler`; FE maps the code to a localized string using the existing i18next mechanism. No Spring `MessageSource` is introduced | Only real i18n mechanism in this codebase is FE-only i18next (A-AI-QUALITY-2); no alternative exists to choose between | Low — this is a fact about the codebase, not a real design choice | No — resolved (default applied) |
| A-AI-QUALITY-9 | Final REST path prefix is `/api/v1/ai-qualities` (top-level, not `/admin/...`), since this is a project-scoped management screen for multiple roles (PM/QA/DEV), not an admin-only screen like `UserAccountAdminController` | Consistent with BUG-DASHBOARD's own top-level `/api/v1/ticket-bug-metrics` prefix, which has the identical PM/QA/DEV access shape | Low — naming only, cheap to rename later if needed | No — resolved (default applied) |
| A-AI-QUALITY-10 | List endpoint pagination/filter shape: standard `page`/`size` query params (per `PageResult<T>` convention) plus optional `projectId`/`repositoryId`/`ticketId` filters, AND-combined | Matches every other `PageResult<T>`-based list endpoint in the codebase; raw input only specified filtering by project/repo/ticket, not the transport shape | Low | No — resolved (default applied) |
| A-AI-QUALITY-11 | Ticket dropdown in the create/edit drawer (Option A): shows all tickets in the selected repository, not filtered to exclude tickets that already have an active AI Quality row. Selecting an already-tracked ticket and submitting Create surfaces the standard 409 from BR-2 | User-confirmed choice of Option A over Option B (pre-filtered dropdown) after being presented both trade-offs | Medium — UX allows a guaranteed-to-fail selection, but this is the user's explicit choice, not an inferred default | No — resolved (user-confirmed) |
| A-AI-QUALITY-12 | No Playwright E2E test is required for this ticket; Vitest + Testing Library component/unit tests are sufficient per [[40-testing.md]] | Raw input does not mention E2E requirements; existing per-feature test strategies in this codebase default to unit/component level unless explicitly requested | Low — can be added later without rework | No — resolved (default applied) |

## 18. Open Issues

None outstanding. All previously open issues are resolved — see §17:
| former ID | resolution |
|---|---|
| OI-AI-QUALITY-1 (pagination/filter shape) | Resolved via A-AI-QUALITY-10 |
| OI-AI-QUALITY-2 (ticket dropdown scoping) | Resolved via A-AI-QUALITY-11 (Option A, user-confirmed) |
| OI-AI-QUALITY-3 (E2E requirement) | Resolved via A-AI-QUALITY-12 |
| OI-AI-QUALITY-4 (REST path/naming) | Resolved via A-AI-QUALITY-9 |
