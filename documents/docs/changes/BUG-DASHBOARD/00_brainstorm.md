# BUG-DASHBOARD Brainstorm

**Ticket ID**: BUG-DASHBOARD
**Create date**: 2026-08-06 00:00:00
**Author**: Claude
**Update date**: 2026-08-06 01:00:00

## 1. Quick context

- Raw input asks for a "Ticket Bug Metrics" screen: one row per Ticket, storing both Internal Bug Count and Customer Bug Count, scoped by Project and Repository, with create/update/detail/soft-delete and PM/QA-mutate + DEV-view-only + others-blocked access.
- The repo already has strong DB and governance-module precedent (`tbl_dim_project`, `tbl_dim_repository`, `tbl_dim_ticket`, `RepositoryController`/`RepositoryService`/`RepositoryDtos`) that this feature should reuse rather than reinvent.
- The repo does not yet have any bug-metrics table or implementation for this feature.
- The raw input's own DDL and API examples do not fully match either the real DB shape or the real code conventions — several corrections are required, not just gap-filling.

## 2. Confirmed from source

- `tbl_dim_project` (PK `project_id`), `tbl_dim_repository` (PK `repository_id`, FK `project_id`), `tbl_dim_ticket` (PK `ticket_id`, FK `project_id` only — **no FK to repository**) all exist in `V4__init_shema_v2.sql`.
- No `tbl_ticket_bug_metrics` or equivalent exists anywhere in the migration history.
- Latest migration is `V508__add_submitted_by_to_review.sql`; a new migration needs an unused version number above 508.
- Governance/junction tables created after V4 (e.g. `V120__project_management.sql`, `V130__repository_management.sql`) add soft-delete columns (`delete_flag`, `deleted_at`, `deleted_by`, often `status`) at creation time, not as a bare `INT` flag.
- `RepositoryController`/`RepositoryService`/`RepositoryDtos` establish the closest governance CRUD precedent:
  - `GET /api/v1/repositories`, `GET /{id}`, `POST`, `PUT /{id}`, `PUT /{id}/delete` (soft delete, not PATCH/DELETE).
  - No `version`/optimistic-locking field.
  - Page DTO shape: `items, page, size, totalElements, totalPages`.
  - Domain exceptions (`NotFoundException`, `BusinessRuleException`) rather than bean-validation groups; audit logging via `AdminAuditLogService`.
- `QaDashboardService.requireAnyAccess` / `requireQaAccess` is a direct precedent for role-scoped dashboards: per-project role lookup (`findProjectRole(caller, projectId)`), ADMIN bypass, string role compare — this is the intended shape for "PM/QA can mutate, DEV can view, others blocked."
- Caller role is a String resolved from the JWT/bearer principal (`CurrentAppUserResolver`), not the stale `AppUser.Role` enum. No `@PreAuthorize`/`hasRole` usage exists anywhere in the codebase — role checks are always explicit in the service layer.
- Real error envelope: `ErrorResponse(timestamp, status, error, message, traceId)`.
- FE has a directly comparable cascading Project→Repository filter pattern (`DevelopmentDashboardPage.tsx` / `DevFilterBar.tsx`) using plain `useState` + TanStack Query key-based refetch and `enabled`-gated dependent queries — no Redux/Zustand needed for this kind of filter state.
- FE forms use `@tanstack/react-form`-based `CForm`, not react-hook-form/zod; delete confirmation uses antd `Popconfirm`; role-based UI hiding is inline `useAuth().user.role` checks (no `usePermission` hook).
- The `TransactionTemplate`-per-item rule in `.claude/rules/20-architecture.md` is scoped to external ingestion (GitHub/Jira/CircleCI), confirmed via markdown-parser flows that have no DB transactions at all — **not applicable** to this feature. Standard `@Transactional` per service method (as in `RepositoryService`) is the right pattern.

## 3. Likely by convention

- New table should be `tbl_ticket_bug_metrics` with UUID PK, FKs to `project_id`, `repository_id`, `ticket_id`, `internal_bug_count INT NOT NULL DEFAULT 0`, `customer_bug_count INT NOT NULL DEFAULT 0`, `note VARCHAR(500)`, `status record_status`, `delete_flag`, `deleted_at`, `deleted_by`, and the standard `created_at/by`, `updated_at/by` audit columns — mirroring the `V120`/`V130` soft-delete-from-creation convention rather than the raw input's plain `delete_flag INT`.
- BE module should live under the same governance package shape as Repository (`web/rest`, `application/usecase/governance` or a new `bugmetrics` usecase package, `web/dto`), reusing `PageResult<T>` and the same list/detail/create/update/soft-delete route shape (`PUT /{id}/delete`).
- Role gating should reuse `findProjectRole`/`tbl_dim_role` infrastructure the same way `QaDashboardService` does, rather than introducing a new permission model.
- FE page should reuse the `DevelopmentDashboardPage`-style cascading filter + table + Drawer pattern, `CForm` for the create/edit form, `Popconfirm` for delete, and inline `useAuth().user.role` checks for hiding mutate actions from DEV/other roles.
- Duplicate-ticket-on-create should map to a 409-style conflict, consistent with `ApplicationException`→409 in `GlobalExceptionHandler`.

## 4. Need human confirmation

All three items below were resolved by user confirmation on 2026-08-06:

- **Repository↔Ticket relationship — resolved: option (a).** Repository selection is informational/filter-only; there is no cross-validation against the selected Ticket. The raw input's "ticket must belong to repository" rule is dropped.
- **Role enforcement — resolved.** Reuse the `QaDashboardService`-style per-project role check (PM/QA mutate, DEV view, others blocked) rather than any new permission model.
- **Migration/DDL — resolved.** Correct the raw input's malformed DDL and follow the soft-delete-from-creation convention, targeting `V509` (or next free number).

## 5. Decision points that affect Phase 3 directly

| ID | Decision point | Why it matters |
|---|---|---|
| D-1 | Repository↔Ticket relationship model | Resolved as option (a): informational/filter-only, no cross-validation |
| D-2 | Role enforcement source/shape | Resolved: reuse `QaDashboardService` per-project role check pattern |
| D-3 | Corrected DDL + migration version | Resolved: implement as proposed, target `V509` |
| D-4 | Exact list/detail DTO field names | Implementation detail; governance convention (`items/page/size/totalElements/totalPages`) is a safe default |
| D-5 | FE route/menu placement | Not specified in raw input; needs normal design decision, non-blocking |

## 6. Raw input vs source gaps

| Topic | Raw input | Observed source / convention | Initial handling |
|---|---|---|---|
| Ticket scoping | Ticket must belong to selected Repository | `tbl_dim_ticket` has no `repository_id` FK; Repository and Ticket are both children of Project only | Resolved (H-1, option a): Repository is informational/filter-only |
| Soft delete route | `PUT /{id}/delete` | Matches `RepositoryController` exactly | No deviation — confirmed |
| Optimistic locking | No `version` field mentioned | `RepositoryModel` also has no `version` field | No deviation — confirmed |
| Error envelope | `{code, status, message, data}` | Real code: `{timestamp, status, error, message, traceId}` | Spec follows code, not raw input example |
| List response shape | `{content, page, size, totalElements, totalPages}` | Real convention: `{items, page, size, totalElements, totalPages}` | Spec follows code, not raw input example |
| DDL | `gen_random_uuid()` without `DEFAULT`; nested `CREATE UNIQUE INDEX` inside `CREATE TABLE`; plain `delete_flag INT` | V120/V130 soft-delete-from-creation convention | Spec proposes corrected DDL, flagged as Human Decision (H-3) |
| Role gating | PM/QA mutate, DEV view-only, others blocked | `QaDashboardService.requireAnyAccess`/`requireQaAccess` precedent matches closely | Resolved (H-2): adopt precedent |

## 7. Risks if we skip clarification

- If the Repository↔Ticket relationship is not resolved first, Phase 3 could implement a cascading-dropdown UI and validation rule that either silently allows tickets unrelated to the selected repository, or blocks valid tickets because of a nonexistent FK — either way, rework risk is high.
- If role gating is implemented ad hoc instead of reusing `QaDashboardService`'s per-project role pattern, it risks diverging from the one existing precedent for exactly this kind of PM/QA/DEV access model, creating maintenance inconsistency.
- If the raw input's malformed DDL is copied forward without correction, the migration will fail to apply.

## 8. Suggested posture for the spec pack

- Treat `01_raw-input.md` as requested behavior and business intent, not a literal technical contract.
- Treat current governance modules (`RepositoryController`/`Service`/`Dtos`), `QaDashboardService`, and the V4/V120/V130 migrations as the observed implementation constraints that the spec should align to by default.
- Encode the Repository↔Ticket relationship mismatch as a blocking Human Decision (it changes contract/schema), the error/page DTO shape corrections as Assumptions (low-risk, code-following defaults), and FE route placement / i18n file locations as Open Issues (non-blocking, implementation detail).

## 9. Preliminary answer to "can Phase 3 start now?"

- **Yes.** All three blocking questions (H-1, H-2, H-3) were resolved by user confirmation on 2026-08-06. Remaining items are non-blocking implementation details (FE route placement, i18n file locations, exact seeded role codes, whitespace-only `note` handling) and can proceed into Phase 3.
