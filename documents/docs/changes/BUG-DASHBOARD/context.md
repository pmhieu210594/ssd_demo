# Context

**Ticket ID**: BUG-DASHBOARD
**Create date**: 2026-08-06 07:45:13
**Author**: Claude
**Update date**: 2026-08-06 07:45:13

## Screen / API / Batch / Related Job

- New feature, no existing implementation in BE or FE (confirmed in `sources.md`/`spec-pack.md`).
- New BE surface to build: `TicketBugMetricsController` / `TicketBugMetricsService` / `TicketBugMetricsMapper` (+ `.xml`), new migration `V509__ticket_bug_metrics.sql` (verify `V509` is still free at implementation start; `V508__add_submitted_by_to_review.sql` was the highest existing migration as of this phase).
- New FE surface to build: a "Ticket Bug Metrics" page, registered as a new `<Route>` inside `App.tsx`'s `:lang` parent (no file-based routing exists — must hand-edit `App.tsx`).
- No batch/job/webhook involved.
- Closest live precedents to follow, not to copy verbatim:
  - `RepositoryController` / `RepositoryService` / `RepositoryDtos` / `RepositoryModel` — governance CRUD shape (list/detail/create/update/soft-delete).
  - `QaDashboardService.requireAnyAccess` / `requireQaAccess` — per-project role-gating shape (this ticket needs PM/QA mutate + DEV view + others-blocked, which is a **superset** of what `QaDashboardService` currently implements — see "Ticket-Specific Constraints" below).

## Example of a correctly implemented code

| purpose | file/path | pattern to follow |
|---|---|---|
| Governance REST routes (list/detail/create/update/soft-delete) | `EDCAP_BE/src/main/java/com/sdd/platform/web/rest/RepositoryController.java` (L31-83) | `GET /api/v1/repositories`, `GET /{id}`, `POST`, `PUT /{id}`, `PUT /{id}/delete` (soft delete is `PUT .../delete`, never `DELETE`); caller resolved via `@CurrentUser` param annotation |
| Governance service layer (search/get/create/update/softDelete) | `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/governance/RepositoryService.java` (L58-238) | One `@Transactional` (`readOnly=true` for reads) method per action; audit call before returning; before-snapshot captured for update via `AdminAuditLogService.snapshot()` **before** mutation |
| Governance DTO records | `EDCAP_BE/src/main/java/com/sdd/platform/web/dto/RepositoryDtos.java` | `RepositoryDto` (all fields incl. audit fields), `RepositoryPageDto(items, page, size, totalElements, totalPages)` with static `from(PageResult<T>)`, `Create...Request`/`Update...Request` records annotated `@NoXssFields` |
| Domain model with soft-delete helper | `EDCAP_BE/src/main/java/com/sdd/platform/domain/model/RepositoryModel.java` (L27) | `boolean isDeleted() { return deleteFlag || deletedAt != null || status == ...Status.DELETED; }` |
| Per-project role-gating (ADMIN bypass + role-string compare) | `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/qadashboard/QaDashboardService.java` (L47-74) | `requireAnyAccess(AuthUserContext)` for whole-screen gate; `requireQaAccess(AuthUserContext, UUID projectId)` for per-project role check via `findProjectRole(caller, projectId)`, `"ADMIN".equals(role)` bypass, `"QA".equalsIgnoreCase(projectRole)` compare |
| MyBatis mapper conventions | `EDCAP_BE/src/main/java/.../mapper/RepositoryMapper.java` + `EDCAP_BE/src/main/resources/mapper/RepositoryMapper.xml` | `@Param` on multi-arg methods, no `@Param` on single-entity insert/update; reusable `<sql id="...Columns">`/`<sql id="...WhereClause">` fragments; explicit `#{status}::record_status` enum cast; `update`/`softDelete` WHERE-scope re-checks `delete_flag = FALSE AND deleted_at IS NULL AND status = 'ACTIVE'` to catch races, 0-row result → `NotFoundException` in the service |
| Soft-delete-from-creation migration convention + partial unique index | `EDCAP_BE/src/main/resources/db/migration/V120__project_management.sql`, `V130__repository_management.sql` | `delete_flag BOOLEAN NOT NULL DEFAULT FALSE`, `deleted_at TIMESTAMPTZ`, `deleted_by VARCHAR(100)` at creation (not bare `INT`); `CREATE UNIQUE INDEX ... WHERE delete_flag = FALSE AND deleted_at IS NULL AND status = 'ACTIVE'` for one-active-row-per-key rules |
| Dim table shape (no soft delete at creation) | `EDCAP_BE/src/main/resources/db/migration/V4__init_shema_v2.sql` (L107-193) | `tbl_dim_project`/`tbl_dim_repository`/`tbl_dim_ticket`, UUID PK with `gen_random_uuid()` default, `status <enum> NOT NULL DEFAULT ...` |
| FE governance list/detail/create/edit/delete page | `EDCAP_FE/src/pages/RepositoryPage.tsx` (L63-748) | `CDrawerForm` for create/edit (+ a second `CDrawerForm` instance used purely as a filter drawer); antd `Popconfirm` (imported directly from `antd`) wraps the delete trigger; `CServerTable`/`IServerTableColumn` for the list; mutations invalidate `["repositories"]` and set/remove `["repository", id]` |
| FE cascading Project→Repository filter + dependent-query gating | `EDCAP_FE/src/pages/development-dashboard/DevelopmentDashboardPage.tsx` (L46-163), `.../components/DevFilterBar.tsx` | `options` query keyed on `projectId` always enabled; `summary`/`tickets` queries `enabled: Boolean(projectId) && Boolean(repositoryId)`; self-healing `useEffect`s auto-select/reset an invalid `projectId`/`repositoryId` when the option list changes |
| FE API endpoint namespace (governance-CRUD style) | `EDCAP_FE/src/lib/api.ts`, `endpoints.repositories` (L1057-1086) | `list(params) → GET`, `get(id) → GET /{id}`, `create(body) → POST`, `update(id, body) → PUT /{id}`, `softDelete(id) → PUT /{id}/delete` |
| FE API endpoint namespace (dashboard style, page-local types) | `EDCAP_FE/src/lib/api.ts`, `endpoints.devDashboard` (L1409-1506) | Return types imported via `import("@/pages/<feature>/types").Foo` rather than declared inline in `api.ts` |

## Allowed common components

| component | path | usage note |
|---|---|---|
| `CDrawerForm` | `@/components/ui/drawer` | Create/edit drawer; wraps `CForm` internally. In "view" mode pass `columns={[]}` and render read-only detail via children (RepositoryPage pattern). |
| `Popconfirm` (antd) | `antd` (direct import) | Delete confirmation wrapper; there is no in-house `CPopconfirm` |
| `CServerTable` / `IServerTableColumn` | `@/components/ui/server-table` | List table; supports `leftHeader` (search + filter button), `action.render` for per-row buttons, `pagination` (table `page` is 1-based, internal state is commonly 0-based — see RepositoryPage's `page: page + 1`) |
| `CButton`, `Badge`, `CSearch`, `CSvgIcon`, `CTooltip` | `@/components/ui/*` | Standard list/page chrome, used as in RepositoryPage |
| `DashboardFilterPill` | `@/components/dashboard/DashboardFilterPill` | Only if a `DevFilterBar`-style cascading filter bar (rather than a filter Drawer) is chosen for Project→Repository/Ticket filtering |
| `IForm` / `EFormType` / `EFormRuleType` field config shape | `@/interfaces` (L161-293), consumed by `@/components/ui/form` | Field config for any `CForm`/`CDrawerForm` fields |

## Forbidden common components

| component | reason |
|---|---|
| `CPopconfirm` | Does not exist — RepositoryPage imports `Popconfirm` directly from `antd`; do not invent an in-house wrapper |
| react-hook-form / zod (any form) | Not used anywhere in this codebase; the only form library is `@tanstack/react-form`-based `CForm` |
| File-based / auto-discovered routing | Does not exist; there is no `router.tsx`/`routes.ts` — routes are hand-registered as `<Route>` children inside `App.tsx`'s `:lang` block |
| Extending `RequireDashboardAccess`'s `DashboardAccessKey` union without flagging it | `DashboardAccessKey` is a **fixed** union `"pm" \| "qa" \| "dev" \| "dataOps" \| "security"` plus a matching `ACCESS_QUERY_BY_KEY` map in `EDCAP_FE/src/components/auth/RequireDashboardAccess.tsx`. Adding a new dashboard key is a real edit to this shared file, not a drop-in prop. Whether to reuse this component (by adding a new key) or build a bespoke lighter guard is an **open implementation decision for Phase 3**, not decided here — do not silently extend the union. |

## List of methods that actually exist

| method/class | path | usage |
|---|---|---|
| `RepositoryService.search(UUID projectId, String status, String keyword, int page, int size, AppUser caller)` | `RepositoryService.java` L58 | List query pattern (readOnly transactional) |
| `RepositoryService.get(UUID repositoryId, AppUser caller)` | `RepositoryService.java` L80 | Detail query pattern |
| `RepositoryService.create(...)` / `.update(...)` / `.softDelete(UUID repositoryId, AppUser caller)` | `RepositoryService.java` L91/127/164 | Mutation pattern incl. audit logging |
| `QaDashboardService.requireAnyAccess(AuthUserContext caller)` | `QaDashboardService.java` L47-52 | Whole-screen access gate (ADMIN bypass, else must have dashboard access on *any* project) |
| `QaDashboardService.requireQaAccess(AuthUserContext caller, UUID projectId)` | `QaDashboardService.java` L54-74 | Per-project role gate; **only ADMIN/QA branches exist today** — see Ticket-Specific Constraints |
| `AdminAuditLogService.logCreate/logUpdate/snapshot/logDelete/logCrudFailure(AppUser caller, String module, String entityType, String entityId, ...)` | `AdminAuditLogService.java` L48-81 | Audit logging; best-effort, never fails the business transaction |
| `CurrentAppUserResolver` → `AuthUserContext` (raw role string) | `web/security/CurrentAppUserResolver.java` L27-53 | Use this resolution path (parameter type `AuthUserContext caller`, matching `QaDashboardService`'s usage), not `AppUser`, whenever PM/QA/DEV distinction is needed |
| `ErrorResponse(OffsetDateTime timestamp, int status, String error, String message, String traceId)` | `web/exception/ErrorResponse.java` | Real error envelope — use for all error responses |
| `GlobalExceptionHandler` mappings | `web/exception/GlobalExceptionHandler.java` | `NotFoundException`→404, `ForbiddenException`→403, `OptimisticLockingException`→409, `DomainException`→400, `ApplicationException`→409, `MethodArgumentNotValidException`→400 |
| `RepositoryMapper` interface methods: `findById`, `findPage`, `count`, `existsActiveName`, `existsActiveProject`, `insert`, `update`, `softDelete` | `infrastructure/persistence/mapper/RepositoryMapper.java` | MyBatis method-shape precedent for a new `TicketBugMetricsMapper` |
| FE: `endpoints.repositories.{list,get,create,update,softDelete}` | `EDCAP_FE/src/lib/api.ts` L1057-1086 | Direct copyable pattern for `endpoints.ticketBugMetrics` |
| FE: `useAuth()` → `{ user: AuthUser | null, isAuthenticated, isLoading, isError, error, refetch }`, `AuthUser = { username, displayName, email, role: string, accessScopes: string[] }` | `EDCAP_FE/src/hooks/useAuth.ts` | `role` is a flat string; inline `user?.role === "..."` checks are the existing FE role-hiding convention (e.g. `RolePage.tsx` L242-243,592) |

## Forbidden methods / methods that do not exist

| method/API | reason | alternative |
|---|---|---|
| `RepositoryService.requireAdmin(AppUser caller)` | ADMIN-only gate; wrong model for a PM/QA-mutate + DEV-view + others-blocked requirement | Write a new role-check method (e.g. on the new `TicketBugMetricsService`) modeled on `QaDashboardService.requireQaAccess`'s shape, extended with PM/DEV branches |
| `AppUser.Role` enum for distinguishing QA/DEV/PM | Only contains `ADMIN, EDITOR, VIEWER, PM`; `CurrentAppUserResolver`'s `AppUser` resolution path collapses **any** non-"ADMIN" role string to `EDITOR` — it cannot represent QA or DEV at all | Use `AuthUserContext caller` (raw role string from JWT/session principal), the same parameter type `QaDashboardService` uses |
| `com.sdd.platform.domain.exception.ForbiddenException` / `com.sdd.platform.domain.exception.OptimisticLockingException` | Do not exist under `domain.exception` | Use `com.sdd.platform.application.exception.ForbiddenException` / `.OptimisticLockingException` (they live under `application.exception`, not `domain.exception`) |
| `tbl_dim_ticket.repository_id` (any FK/column) | Confirmed absent from schema (`V4__init_shema_v2.sql` L174-193, no later migration adds it) | Repository is stored on the new row for filter/display only; do not attempt an FK or code-level cross-validation against `ticketId` (resolved H-BUG-DASHBOARD-1, spec-pack BR-12) |
| `tbl_dim_ticket.delete_flag` / `.deleted_at` / `.deleted_by` | Confirmed absent — `tbl_dim_ticket` has no soft-delete columns in the current schema at all | "Active Ticket" existence check must use `tbl_dim_ticket.status`, not a delete-flag column that doesn't exist on that table |
| `CPopconfirm`, any generic route-registration helper, react-hook-form/zod APIs | Do not exist in this codebase | See "Forbidden common components" above |

## DTO / Entity / Table / Migration mapping

| layer | name | path | Note |
|---|---|---|---|
| DB (existing) | `tbl_dim_project`, `tbl_dim_repository`, `tbl_dim_ticket` | `V4__init_shema_v2.sql` L107-193 | Semantic UUID PKs; `tbl_dim_ticket` has no FK to Repository (siblings under Project) |
| DB (existing, precedent) | soft-delete-from-creation column set + partial unique index | `V120__project_management.sql`, `V130__repository_management.sql` | Convention to replicate exactly for the new table |
| DB (new) | `tbl_ticket_bug_metrics` | `V509__ticket_bug_metrics.sql` (target — **re-verify V509 is still free at Phase 3 start**) | PK `ticket_bug_id UUID DEFAULT gen_random_uuid()`; FKs `project_id`, `repository_id`, `ticket_id` (each validated independently, no cross-validation between `repository_id`/`ticket_id`); `internal_bug_count INT NOT NULL DEFAULT 0`, `customer_bug_count INT NOT NULL DEFAULT 0`, `note VARCHAR(500)`; soft-delete-from-creation columns (`delete_flag`, `deleted_at`, `deleted_by`, `status record_status NOT NULL DEFAULT 'ACTIVE'`); standard audit columns; partial unique index on `ticket_id` filtered `WHERE delete_flag = FALSE AND deleted_at IS NULL AND status = 'ACTIVE'` for BR-1/BR-6 |
| BE domain model (new, naming precedent `RepositoryModel`) | `TicketBugMetricsModel` | `domain/model/` (new file) | Mirror `RepositoryModel`'s Lombok annotations (`@Builder @Getter @Setter @NoArgsConstructor @AllArgsConstructor`, never `@Data`, per `10-style.md`) and its `isDeleted()` helper |
| BE DTOs (new, naming precedent `RepositoryDtos`) | `TicketBugMetricsDto`, `TicketBugMetricsPageDto`, `CreateTicketBugMetricsRequest`, `UpdateTicketBugMetricsRequest` | `web/dto/` (new file) | Mirror `RepositoryDtos`' record shape and `@NoXssFields` usage on request records |
| BE mapper (new, naming precedent `RepositoryMapper`) | `TicketBugMetricsMapper` (+ `.xml`) | `infrastructure/persistence/mapper/`, `resources/mapper/` (new files) | Mirror `@Param` usage, resultMap, `#{status}::record_status` cast, defensive WHERE-scoped mutations |
| FE types (new — two viable placements, decide in Phase 3) | `TicketBugMetric`, `TicketBugMetricsPageResponse`, `CreateTicketBugMetricsRequest`, `UpdateTicketBugMetricsRequest` | Option A: inline in `EDCAP_FE/src/lib/api.ts` (matches `Repository`/`RepositoryPageResponse` naming convention, recommended since this is governance-CRUD like Repository, not a dashboard); Option B: page-local `types.ts` (matches `devDashboard` convention) | Flag the choice explicitly in `impl-plan.md`; do not place in `src/interfaces/` — that directory holds generic UI-plumbing (`IForm`, etc.), not entity DTOs |

## formItemNm / SEQNO / Master Data / Code Value Mapping

| display/item | internal value | source | note |
|---|---|---|---|
| Role codes PM / QA / DEV / ADMIN | String, compared via `equalsIgnoreCase`/`.equals` (e.g. `"QA".equalsIgnoreCase(projectRole)`) | `tbl_dim_role` + `findProjectRole(caller, projectId)` (port method, impl not read this phase) | Exact seeded code for "DEV" not verified line-by-line this phase (carried Open Issue OI-BUG-DASHBOARD-4) — confirm before writing the DEV branch of the role check |
| `status` (row lifecycle) | Postgres enum `record_status` (`ACTIVE`/`DELETED`) | `V4__init_shema_v2.sql` (type definition), cast explicitly `#{status}::record_status` in MyBatis XML | Follow `RepositoryMapper.xml`'s exact cast pattern for the new mapper |
| `ticket_status` (existing, on `tbl_dim_ticket`) | Separate custom enum type from `record_status` | `V4__init_shema_v2.sql` | Do not confuse with the new table's `status` column type; "active Ticket" check reads `tbl_dim_ticket.status`, a `ticket_status` value, not `record_status` |

## Multilingual Note

- Existing Repository request bodies mix snake_case (`repo_name_masked`, `host_type`, `default_branch`, `repo_url_hash`) with a camelCase response DTO (`repoNameMasked`, `hostType`, ...) — a known existing inconsistency. Do not copy this mismatch unless deliberately matching precedent; prefer camelCase consistently for the new feature's request/response bodies unless Phase 3 explicitly decides to mirror Repository's convention for consistency.
- Client-side error messages route through `t(error.message, { defaultValue: error.message })` (see `RepositoryPage.tsx` `handleError`); spec-pack proposes i18n keys `validation.bug_count.min` and `validation.note.max_length` — exact resource file/namespace location is unconfirmed (Open Issue OI-BUG-DASHBOARD-3).

## Encoding / Mojibake Note

- No feature-specific encoding concerns identified; `note` is a free-text `VARCHAR(500)` field — apply the same `@NoXssFields` annotation convention used on `CreateRepositoryRequest`/`UpdateRepositoryRequest` to the new create/update request records.

## Log / Audit / Operation Note

- Use `AdminAuditLogService` exactly as `RepositoryService` does: `snapshot()` the row **before** mutation for update, then `logCreate`/`logUpdate`/`logDelete` after a successful mutation, and `logCrudFailure` in a catch-and-rethrow block on failure.
- Audit writes are best-effort (wrapped internally so a broken audit write only logs a warning and never fails the business transaction) — do not add code that makes an audit failure block or roll back a CRUD operation.
- `AdminAuditLogService.logRead(...)` exists for detail-view auditing (per a 2026-07-09 human decision precedent) — decide in Phase 3 whether Ticket Bug Metrics detail views need this; not decided here.

## Ticket-Specific Constraints

- Role gating for this feature must use `AuthUserContext caller` (the raw role-string principal), never `AppUser caller` — `AppUser`'s role resolution collapses every non-ADMIN role to `EDITOR` and cannot distinguish PM/QA/DEV.
- `QaDashboardService.requireQaAccess` today only has ADMIN-bypass and QA branches; it has **no PM or DEV branch**. This ticket's requirement (PM/QA mutate, DEV view-only, all others blocked from the whole screen) is a **new** role-check shape that does not exist yet anywhere in the codebase. Phase 3 must write a new method (e.g. on a new `TicketBugMetricsService`) modeled on `QaDashboardService`'s shape (ADMIN bypass + `findProjectRole` + role-string compare), not assume it can call `QaDashboardService` as-is.
- Whether to reuse `RequireDashboardAccess` (by adding a new `DashboardAccessKey`) for the FE screen-level gate, or build a lighter bespoke guard, is an open implementation decision — see "Forbidden common components" above. Backend-side enforcement (`BR-10`: backend validation is the final enforcement point) is mandatory regardless of the FE guard choice.
- Repository is filter/informational only on the new row — no FK, no cross-validation against `ticketId` anywhere in application code or the DB schema (resolved H-BUG-DASHBOARD-1 / BR-12).
