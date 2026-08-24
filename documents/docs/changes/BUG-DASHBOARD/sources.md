# BUG-DASHBOARD Sources

**Ticket ID**: BUG-DASHBOARD
**Create date**: 2026-08-06 00:00:00
**Author**: Claude
**Update date**: 2026-08-06 01:00:00

## 1. Source policy

- `docs/changes/BUG-DASHBOARD/spec-pack.md` is the intended single source of truth after Phase 1.
- This file records provenance only: what was read, what was inferred, and what is still missing.
- When raw input conflicts with observed source, the spec pack must record both and surface the gap explicitly, rather than silently favoring one side.

## 2. Source inventory

| Area | Source | Status | Trust | Type | Notes |
|---|---|---|---|---|---|
| Ticket input | `docs/changes/BUG-DASHBOARD/01_raw-input.md` | Read | Medium | Requested behavior | Primary business input. Contains malformed DDL and an illustrative API contract that does not match current code conventions. |
| Spec-pack precedent | `docs/changes/PROJECT/spec-pack.md`, `sources.md`, `00_brainstorm.md` | Read | High | Process precedent | Used as the structural template for this ticket's artifacts (closest prior ticket with the same Phase 1 process and a similar FE+BE+DB governance shape). |
| Standards templates | `docs/standards/templates/be-controller.md`, `be-use-case.md`, `be-adapter.md`, `fe-component.md`, `fe-hook.md` | Read (listing only, not fully read) | Medium | Process standard | No dedicated `spec-pack` template file exists under `docs/standards/templates/`; PROJECT's spec-pack is the closest available structural reference. |
| Standards docs | `docs/standards/api-contract.md`, `security.md`, `testing.md`, `database.md`, `coding.md` | Not fully read this phase | Medium | Repo standard | Not re-read in depth; PROJECT's sources.md already flagged known staleness in `api-contract.md` and `security.md` (raw `List` vs page DTO, `errorCode` vs `error`). Same caveat assumed to still apply. |
| Automation rules | `.claude/rules/00-safety.md`, `10-style.md`, `20-architecture.md`, `30-security.md`, `40-testing.md` | Read (via CLAUDE.md context) | High | Automation rule | Confirms layer boundaries, PUT/commit-push confirmation gates, and Phase 0 scope restriction (not applicable here — Phase 0 is complete per project CLAUDE.md). |
| V4 base schema | `EDCAP_BE/src/main/resources/db/migration/V4__init_shema_v2.sql` | Read (via Explore agent) | High | Observed source | Confirms `record_status`, `severity_level`, `tbl_dim_project` (L107-119), `tbl_dim_repository` (L121-134), `tbl_dim_ticket` (L174-193). |
| Latest migration | `EDCAP_BE/src/main/resources/db/migration/V508__add_submitted_by_to_review.sql` | Confirmed via Explore agent (filename only) | High | Observed source | Highest existing migration version at time of writing; next new migration should use an unused version number above 508 (numbers are non-contiguous). |
| Existing bug-metrics table search | Full-text search of `EDCAP_BE/src/main/resources/db/migration/*.sql` for "bug" | Read (via Explore agent) | High | Observed source | Only hit is `V1__init_schema.sql:57`, a comment on `ticket_type` values (`FEATURE | BUG | TASK`), not a metrics table. No existing `tbl_ticket_bug_metrics` or equivalent. |
| Governance soft-delete precedent | `V120__project_management.sql`, `V130__repository_management.sql`, `V140__team_management.sql`, `V110__...customer_for_soft_delete...sql` | Read (via Explore agent, partial) | High | Observed source | Confirms governance/junction tables add `delete_flag`, `deleted_at`, `deleted_by` (and often `status`) at creation time, distinct from the plain V4 dim-table shape. |
| Repository REST pattern | `EDCAP_BE/src/main/java/com/sdd/platform/web/rest/RepositoryController.java` | Read (via Explore agent) | High | Observed source | Confirms list/detail/create/update route shape and soft delete via `PUT /api/v1/repositories/{id}/delete` (not PATCH). |
| Repository service | `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/governance/RepositoryService.java` | Read (via Explore agent) | High | Observed source | `requireAdmin` gate per method; soft delete sets `delete_flag`/`deleted_at`/`deleted_by`; domain exceptions (`BusinessRuleException`, `NotFoundException`); audit logging via `AdminAuditLogService`; no `version` field anywhere in `RepositoryModel`. |
| Repository DTOs | `EDCAP_BE/src/main/java/com/sdd/platform/web/dto/RepositoryDtos.java` | Read (via Explore agent) | High | Observed source | `RepositoryPageDto(items, page, size, totalElements, totalPages)` built from generic `PageResult<T>`. |
| QA dashboard role gating | `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/qadashboard/QaDashboardService.java` (`requireAnyAccess` L48-52, `requireQaAccess` L59-74) | Read (via Explore agent) | High | Observed source | Direct precedent for PM/QA/DEV role gating: per-project role lookup via `findProjectRole(caller, projectId)`, ADMIN bypass, string role compare; `requireAnyAccess` gates whole-dashboard access. |
| Auth principal resolution | `EDCAP_BE/src/main/java/com/sdd/platform/web/security/CurrentAppUserResolver.java` (L27-53) | Read (via Explore agent) | High | Observed source | Caller role resolved as a String (e.g. "PM"/"QA"/"DEV"/"ADMIN") from JWT/bearer principal via `SecurityContextHolder`; NOT the same as the stale `AppUser.Role` enum (`VIEWER, EDITOR, ADMIN, PM` only). |
| Role data table | Migrations `V90__alter_tbl_role_for_management.sql`, `V91__seed_data_ops_role.sql`, `V93__alter_tbl_dim_role_add_status.sql`; `RoleMapper.xml` | Confirmed via Explore agent (filenames/existence only) | Medium | Observed source | Confirms `tbl_dim_role` and per-project role assignment infrastructure exists; not read in full detail this phase. |
| Error response record | `EDCAP_BE/src/main/java/com/sdd/platform/web/exception/ErrorResponse.java` | Read (via Explore agent) | High | Observed source | Actual fields: `timestamp (OffsetDateTime), status (int), error (String), message (String), traceId (String)`. Raw input's example (`code/status/message/data`) does not match. |
| Global exception handler | `EDCAP_BE/src/main/java/com/sdd/platform/web/exception/GlobalExceptionHandler.java` | Read (via Explore agent) | High | Observed source | Confirms mapping: `NotFoundException`→404, `ForbiddenException`→403, `OptimisticLockingException`→409, `DomainException`→400, `ApplicationException`→409 (candidate for the "duplicate ticket bug row" 409 case), `MethodArgumentNotValidException`→400. |
| Parser flow relevance check | `SpecPackMarkdownParser`, `ReportMarkdownParser`, etc. under `domain/service/markdown/**` | Read (via Explore agent) | High | Observed source | These are pure in-memory markdown-parsing utilities with no DB transactions; confirmed **not applicable** as a pattern for this CRUD feature. The `TransactionTemplate`-per-item rule in `.claude/rules/20-architecture.md` is scoped to external ingestion (GitHub/Jira/CircleCI/webhooks) only, per `docs/architecture/key-flows.md`; standard `@Transactional` per service method (as in `RepositoryService`) is the correct pattern here. |
| FE cascading dropdown precedent | `EDCAP_FE/src/pages/development-dashboard/DevelopmentDashboardPage.tsx`, `DevFilterBar.tsx` | Read (via Explore agent) | High | Observed source | Project→Repository cascading filter via plain `useState` + TanStack Query key-based refetch (`["devDashboard","options", filters.projectId]`); dependent queries gated with `enabled: Boolean(projectId) && Boolean(repositoryId)`; self-healing `useEffect` resets invalid selections. |
| FE form library | `EDCAP_FE/src/components/ui/form/*` (`CForm`, `generate-form.tsx`, `field-info.tsx`) | Read (via Explore agent) | High | Observed source | Built on `@tanstack/react-form`, column-config driven. No react-hook-form/zod in this codebase. No existing shared "non-negative integer" numeric validator; closest analog is a raw controlled `<input type="number" min={0}>` in `threshold-config/components/ThresholdTable.tsx:81-95`. |
| FE API wrapper | `EDCAP_FE/src/lib/api.ts` (L93-143, 192-205, endpoints namespace ~L1409-1506) | Read (via Explore agent) | High | Observed source | `request<T>()` reads `body.error`→`errorCode`, `body.message`→`messageKey`, matching the real `ErrorResponse` shape; throws typed `ApiError`; `endpoints.<feature>` namespace convention (e.g. `endpoints.devDashboard`) should be mirrored as `endpoints.bugMetrics`. |
| FE delete confirmation | `EDCAP_FE/src/pages/repository/RepositoryPage.tsx:709-735` (and Team/Organization/Customer/Role pages) | Read (via Explore agent) | High | Observed source | antd `Popconfirm` wraps the delete trigger before calling the soft-delete mutation; reusable pattern. |
| FE role-based UI hiding | `EDCAP_FE/src/pages/role/RolePage.tsx:242-243,592`; `EDCAP_FE/src/hooks/useAuth.ts:16-41`; `EDCAP_FE/src/components/auth/RequireDashboardAccess.tsx` | Read (via Explore agent) | High | Observed source | No dedicated `usePermission` hook; inline `user?.role === "..."` checks off `useAuth()`, plus a route-level `RequireDashboardAccess` guard for whole-page gating. |

## 3. Solid evidence

| Topic | Evidence | Source |
|---|---|---|
| `tbl_dim_project`, `tbl_dim_repository`, `tbl_dim_ticket` exist with semantic UUID PKs | `project_id`, `repository_id`, `ticket_id`, all UUID with `gen_random_uuid()` default | `V4__init_shema_v2.sql:107-193` |
| **`tbl_dim_ticket` has no FK to `tbl_dim_repository`** | Ticket FKs only to `project_id`; Repository is a sibling of Ticket under Project, not its parent | `V4__init_shema_v2.sql:174-193` (Explore agent confirmation) |
| No existing bug-metrics table | Only "bug" hit in all migrations is a comment on `ticket_type` values | `V1__init_schema.sql:57` |
| Latest migration number | Highest existing file is `V508__add_submitted_by_to_review.sql` | migration directory listing |
| Governance soft-delete-from-creation convention | New governance/junction tables add `delete_flag`, `deleted_at`, `deleted_by` (and often `status`) at creation, not as a bare `INT` flag | `V120__project_management.sql`, `V130__repository_management.sql` |
| Repository governance module has no `version`/optimistic locking | Confirmed via grep of `RepositoryModel.java` | `RepositoryService.java`, `RepositoryModel.java` |
| Soft delete route convention | `PUT /api/v1/repositories/{id}/delete`, not PATCH, not DELETE | `RepositoryController.java:80-83` |
| Real error envelope | `ErrorResponse(timestamp, status, error, message, traceId)` | `ErrorResponse.java` |
| Real page DTO shape | `RepositoryPageDto(items, page, size, totalElements, totalPages)` | `RepositoryDtos.java:52-68` |
| PM/QA/DEV per-project role gating precedent exists | `QaDashboardService.requireAnyAccess` / `requireQaAccess` pattern with ADMIN bypass | `QaDashboardService.java:48-74` |
| Caller role is resolved as a String from JWT principal, not the stale `AppUser.Role` enum | `CurrentAppUserResolver.java:27-53`; `AppUser.java:21` | Explore agent |
| No Spring Security `@PreAuthorize`/`hasRole` usage anywhere in the codebase | Explore agent grep returned zero matches | Explore agent |
| FE cascading dropdown + dependent-query precedent exists | `DevFilterBar.tsx` clears repository on project change; dependent queries gated by `enabled` | `DevelopmentDashboardPage.tsx:67-86,132-163` |
| FE form library is `@tanstack/react-form`-based `CForm`, not react-hook-form/zod | `components/ui/form/index.tsx` | Explore agent |
| FE delete confirmation pattern is antd `Popconfirm` | `RepositoryPage.tsx:709-735` | Explore agent |
| FE role-based UI hiding is inline `useAuth().user.role` checks | `RolePage.tsx:242-243,592` | Explore agent |

## 4. Partial or stale evidence

| Topic | Issue | Impact |
|---|---|---|
| Raw input error envelope example | Uses `{code, status, message, data}`; real `ErrorResponse` is `{timestamp, status, error, message, traceId}` | Spec must follow code, treat raw input's JSON examples as illustrative business intent only, not a literal contract |
| Raw input list response example | Uses `content/page/size/totalElements/totalPages`; real governance convention is `items/page/size/totalElements/totalPages` | Spec should follow the real convention unless a deviation is explicitly approved |
| Raw input DDL | `gen_random_uuid()` used without `DEFAULT` keyword; `CREATE UNIQUE INDEX` statement nested illegally inside `CREATE TABLE(...)`; plain `delete_flag INT` instead of the soft-delete-from-creation convention seen in `V120`/`V130` | DDL as written cannot run; corrected DDL must be produced before Phase 3, following the governance junction-table pattern |
| Raw input Ticket dropdown scoping ("Ticket phải thuộc về repository_id đã chọn") | Contradicts `tbl_dim_ticket` having no `repository_id` FK | Resolved 2026-08-06: rule dropped, Repository treated as informational/filter-only (spec pack §16, H-BUG-DASHBOARD-1) |
| `docs/standards/api-contract.md`, `security.md` | PROJECT's sources.md already flagged these as partially stale versus real code (raw list DTOs, `errorCode` field); not re-verified line-by-line this phase, but the same caution is assumed to still hold | Do not copy these docs' examples blindly into the BUG-DASHBOARD spec |

## 5. Missing evidence

| Item | Looked in | Why missing |
|---|---|---|
| Canonical ticket body outside `01_raw-input.md` (Jira ticket, meeting memo, basic design doc) | `docs/changes/BUG-DASHBOARD/`, repo search | Not present in workspace |
| Any existing ticket-to-repository association mechanism (e.g. via PR/commit linkage tables) that could substitute for a direct FK | Not exhaustively searched this phase | Would materially change the answer to Human Decision H-1; needs a follow-up source check if option (b) is chosen |
| Exact FE route/menu placement for the new "Ticket Bug Metrics" page | Raw input, FE routing config | Not specified in raw input; not located in this phase |
| i18n resource file locations/keys (`messages_vi.properties` etc., or FE i18next namespace files) | Not located in this phase | Only referenced generically in raw input; exact files not confirmed |
| Whether `tbl_dim_role`/`findProjectRole` already returns "DEV" as a queryable role value for this per-project lookup | `RoleMapper.xml`, seed migrations — not read line-by-line this phase | Confirmed table/infrastructure exists, but exact seeded role codes not verified |

## 6. External or non-canonical references

None identified this phase — no external files or IDE tabs were referenced in the current task.

## 7. Pending judgments / human decisions seeded from source review

| ID | Question | Why it matters | Status |
|---|---|---|---|
| PJ-BUG-DASHBOARD-1 | How should the Repository↔Ticket relationship be handled given `tbl_dim_ticket` has no `repository_id` FK? | Raw input's cascading Project→Repository→Ticket UI and its "ticket must belong to repository" validation rule cannot be enforced as literally described at the DB layer | Resolved 2026-08-06 — option (a): Repository is informational/filter-only, no cross-validation against Ticket |
| PJ-BUG-DASHBOARD-2 | Should role enforcement follow the `QaDashboardService`-style per-project role check (PM/QA mutate, DEV view-only, others blocked), reusing `tbl_dim_role`/`findProjectRole`? | Matches raw input intent closely but needs explicit confirmation this is the intended reuse rather than a new permission model | Resolved 2026-08-06 — reuse per-project role check |
| PJ-BUG-DASHBOARD-3 | Should the new migration correct the raw input's malformed DDL and follow the soft-delete-from-creation convention (`V120`/`V130`-style) instead of the literal raw input DDL? | Raw input DDL cannot execute as written | Resolved 2026-08-06 — implement as proposed, target `V509` |

## 8. Initial readiness summary

- Source coverage is strong for DB schema facts, governance REST/service conventions, real error/page DTO shapes, and FE cascading-dropdown/form/delete/role-hiding patterns.
- All three original pending judgments (PJ-BUG-DASHBOARD-1/2/3) were resolved by user confirmation on 2026-08-06.
- Remaining gaps (FE route placement, i18n file locations, exact seeded role codes) are implementation-detail level and do not block spec-pack completion; they are logged as Open Issues in `spec-pack.md` §18.
