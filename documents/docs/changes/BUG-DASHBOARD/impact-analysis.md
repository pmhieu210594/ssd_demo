# Impact Analysis

**Ticket ID**: BUG-DASHBOARD
**Create date**: 2026-08-06 08:30:00
**Author**: Claude
**Update date**: 2026-08-06 08:30:00

## 1. Content Change Summary

- Add a new governance-style CRUD module, "Ticket Bug Metrics", allowing PM/QA to record and manage
  Internal/Customer bug counts per Ticket, DEV to view-only, and all other roles to be blocked from
  the entire screen (list/detail included).
- Introduce a new DB table (`tbl_ticket_bug_metrics`) and full BE/FE CRUD surface, following the
  `RepositoryController`/`RepositoryService` governance pattern and a new role-check method modeled
  on (but not reusing) `QaDashboardService.requireQaAccess`.
- No existing production code is modified except three narrow, additive touch points: `lib/api.ts`
  (new endpoint group), `App.tsx` (new route), and optionally `RequireDashboardAccess.tsx` (new
  `DashboardAccessKey` union member — flagged as an open decision, not committed here).

## 2. Directly Affected Files

- `docs/changes/BUG-DASHBOARD/source-availability.md`
- `docs/changes/BUG-DASHBOARD/source-inventory.md`
- `docs/changes/BUG-DASHBOARD/impact-analysis.md`
- `docs/changes/BUG-DASHBOARD/impl-plan.md`

## 3. Indirectly Affected Files

- `EDCAP_BE/src/main/resources/db/migration/V509__ticket_bug_metrics.sql` (new)
- `EDCAP_BE/src/main/java/com/sdd/platform/domain/model/TicketBugMetricsModel.java` (new)
- `EDCAP_BE/src/main/java/com/sdd/platform/web/dto/TicketBugMetricsDtos.java` (new)
- `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/persistence/TicketBugMetricsRepositoryPort.java` (new)
- `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/TicketBugMetricsRepositoryAdapter.java` (new)
- `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/mapper/TicketBugMetricsMapper.java` + `.xml` (new)
- `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/.../TicketBugMetricsService.java` (new)
- `EDCAP_BE/src/main/java/com/sdd/platform/web/rest/TicketBugMetricsController.java` (new)
- `EDCAP_BE/src/test/UnitTest/java/.../TicketBugMetricsServiceTest.java`, `TicketBugMetricsControllerTest.java` (new)
- `EDCAP_FE/src/pages/TicketBugMetricsPage.tsx` (new, exact path TBD)
- `EDCAP_FE/src/lib/api.ts` (edit — add `endpoints.ticketBugMetrics`)
- `EDCAP_FE/src/App.tsx` (edit — add new `<Route>`)
- `EDCAP_FE/src/components/auth/RequireDashboardAccess.tsx` (edit, only if this guard is chosen)
- `EDCAP_FE/public/locales/en/locale.json`, `.../ja/locale.json`, `.../vi/locale.json` (edit — add `Pages.TicketBugMetrics.*` keys)
- `EDCAP_FE/src/__ tests __/ticket-bug-metrics/*.test.tsx` (new)
- `EDCAP_FE/e2e_tests/tests/ticket-bug-metrics/*.spec.ts` (new)

## 4. Caller / Callee Map

- FE page -> `endpoints.ticketBugMetrics.*` helper -> `api.*` -> `/api/v1/ticket-bug-metrics` (resolved in Phase 4; see spec-pack.md §20.1)
- FE route guard -> `RequireDashboardAccess` (if chosen) or bespoke guard -> `useAuth()` -> `/api/v1/me`
- BE controller -> `TicketBugMetricsService` -> new role-check method (ADMIN bypass / `findProjectRole`-style
  per-project compare, PM/QA mutate + DEV view + others blocked) -> `TicketBugMetricsRepositoryPort`
  -> adapter/mapper -> `tbl_ticket_bug_metrics`
- `TicketBugMetricsService` -> independent existence/active checks against `tbl_dim_project`,
  `tbl_dim_repository`, `tbl_dim_ticket` (no FK-level or code-level cross-validation between
  `repository_id` and `ticket_id` — BR-12/H-BUG-DASHBOARD-1)
- All mutation/error flows -> `GlobalExceptionHandler` for error mapping -> `ErrorResponse`
- `TicketBugMetricsService` -> `AdminAuditLogService` (`snapshot`/`logCreate`/`logUpdate`/`logDelete`/`logCrudFailure`, best-effort, never blocks the business transaction)

## 5. FE Impact

- Add a new Ticket Bug Metrics page with list, drawer-based create/edit/detail, and delete
  confirmation flow, mirroring `RepositoryPage.tsx`.
- Add a Project→Repository→Ticket cascading filter, mirroring `DevFilterBar.tsx`'s `enabled`-gated
  query pattern and self-healing `useEffect` resets (Repository is filter/informational only, per
  BR-12 — no cross-validation against the selected Ticket).
- Add `endpoints.ticketBugMetrics` to `src/lib/api.ts`; resolved (Phase 4) as inline types
  (Repository-style), not a page-local `types.ts`.
- Add route registration under the existing `:lang`-first router in `App.tsx`; resolved (Phase 4)
  as a bespoke lighter guard (`RequireTicketBugMetricsAccess.tsx`), not an extension of
  `RequireDashboardAccess`'s `DashboardAccessKey` union.
- Add inline `useAuth().user.role` checks (per `RolePage.tsx` convention) to hide mutate actions for
  DEV while still allowing DEV to view list/detail.
- Add locale keys under `Pages.TicketBugMetrics.*` (resolved: flat per-field keys like
  `Project.Required`, `InternalBugCount.Invalid`, `Note.MaxLength` — not a nested `validation.*`
  sub-object — descriptive-key convention, no generic `field.ruleType` keys exist in this codebase)
  across `en`/`ja`/`vi`; see spec-pack.md §20.3.
- Add FE unit tests (Vitest + Testing Library, mirroring `repository.test.tsx`/`repository-api.test.ts`)
  and E2E tests (mirroring `repository.spec.ts`).

## 6. BE Impact

- Add `TicketBugMetricsController` under route prefix `/api/v1/ticket-bug-metrics` (resolved in
  Phase 4), following `RepositoryController`'s route shape: `GET`, `GET /{id}`, `POST`,
  `PUT /{id}`, `PUT /{id}/delete` (soft delete only — never a hard `DELETE`), plus
  `GET /ticket-options`, `GET /options`, `GET /access` added during implementation (see
  spec-pack.md §20.1).
- Add `TicketBugMetricsService` enforcing: non-negative integer bug counts (BR-2), 500-char note max
  (BR-3), one-active-row-per-Ticket conflict on create (BR-6, 409), independent existence/active
  checks for `projectId`/`repositoryId`/`ticketId` with no cross-validation between repository and
  ticket (BR-12), and a **new** role-check method (PM/QA mutate, DEV view-only, others blocked) —
  this is new code modeled on `QaDashboardService.requireQaAccess`'s shape, not a modification of
  `QaDashboardService` itself, since that service only has ADMIN/QA branches today.
- Use `AuthUserContext caller` exclusively for role checks (never `AppUser caller`, which collapses
  all non-ADMIN roles to `EDITOR` and cannot distinguish PM/QA/DEV).
- Add `TicketBugMetricsRepositoryPort` + adapter + `TicketBugMetricsMapper`/`.xml`, following
  `RepositoryMapper`'s `@Param` usage, resultMap, `#{status}::record_status` cast, and WHERE-scoped
  guarded update/softDelete queries (0-row result → `NotFoundException` in the service).
- Add `TicketBugMetricsDtos` (`TicketBugMetricsDto`, `TicketBugMetricsPageDto`,
  `CreateTicketBugMetricsRequest`/`UpdateTicketBugMetricsRequest` annotated `@NoXssFields`).
- Wire `AdminAuditLogService` exactly as `RepositoryService` does: snapshot before update-mutation,
  `logCreate`/`logUpdate`/`logDelete` after success, `logCrudFailure` in catch-and-rethrow.
- Add BE unit tests (service validation, role matrix, duplicate conflict, Repository↔Ticket
  independence) and web tests (route/status/error-envelope mapping) — required per
  `.claude/rules/40-testing.md` even though the Repository module itself (the pattern being
  mirrored) currently has none.

## 7. API Contract Impact

- Introduce new endpoints for list, detail, create, update, and soft delete under a new route prefix.
- List response wrapper must be `items/page/size/totalElements/totalPages` (real `RepositoryPageDto`
  shape), not the raw input's illustrative `content/...` example.
- Success responses are raw DTOs (or DTO lists via the page wrapper); failure responses are the real
  `ErrorResponse(timestamp, status, error, message, traceId)` envelope — never an ad-hoc
  `ResponseEntity` status in the controller.
- No `version`/optimistic-locking field, consistent with `RepositoryModel` having none.
- This is a wholly new endpoint surface — no existing contract is changed or made backward-incompatible.

## 8. DTO / Schema / Validation Impact

- New request DTOs accept `projectId`, `repositoryId`, `ticketId`, `internalBugCount`,
  `customerBugCount`, optional `note` (max 500 chars).
- `internalBugCount`/`customerBugCount`: reject negative, non-integer, or missing values; `0` is a
  valid boundary value.
- `note`: optional; reject 501+ chars; whitespace-only handling is unspecified by raw input
  (OI-BUG-DASHBOARD-5) — not resolved in this phase.
- `repositoryId` is validated only for its own existence/active status and membership in
  `projectId` — never cross-validated against `ticketId` (BR-12).
- Response DTOs model soft-delete fields/status values consistent with the `record_status` enum
  convention (`ACTIVE`/`DELETED`).

## 9. DB / Migration Impact

- New migration `V509__ticket_bug_metrics.sql` (confirmed free — current highest is `V508`).
- New table `tbl_ticket_bug_metrics`: UUID PK (`ticket_bug_id`, `gen_random_uuid()` default), FKs to
  `project_id`, `repository_id`, `ticket_id` (each independently validated, no cross-validation
  between `repository_id`/`ticket_id`), `internal_bug_count INT NOT NULL DEFAULT 0`,
  `customer_bug_count INT NOT NULL DEFAULT 0`, `note VARCHAR(500)`, soft-delete-from-creation columns
  (`delete_flag`, `deleted_at`, `deleted_by`, `status record_status NOT NULL DEFAULT 'ACTIVE'`),
  standard audit columns — following the `V120`/`V130` convention exactly.
- Partial unique index on `ticket_id` filtered `WHERE delete_flag = FALSE AND deleted_at IS NULL AND
  status = 'ACTIVE'` enforces BR-1/BR-6 (one active row per Ticket).
- No existing migration is edited; this is a purely additive forward migration.
- "Active Ticket" existence check reads `tbl_dim_ticket.status` (a `ticket_status` enum value), not
  a delete-flag column — `tbl_dim_ticket` has no soft-delete columns at all.

## 10. Batch / Job / Event Impact

- No batch, job, queue, webhook, or event processing is in scope. The feature is purely synchronous
  request/response CRUD, consistent with the Repository module precedent.
- No webhook or connector paths (GitHub/Jira/CircleCI ingestion) are touched.

## 11. Test Impact

- BE unit tests: bug-count non-negative validation, note length, duplicate-ticket conflict (409),
  role-based authorization matrix (PM/QA mutate, DEV view-only, others blocked from all actions
  including list/detail), Repository↔Ticket independence.
- BE web/integration tests: list/detail/create/update/delete routes, error envelope/status mapping,
  migration/index behavior for soft delete and the new uniqueness constraint.
- FE unit tests: empty/loading/error states, cascading dropdown behavior, create/edit/delete flows,
  role-based UI hiding (DEV vs PM/QA), API helper query-string construction and error handling.
- FE E2E tests: full CRUD journey and deleted-row behavior, mirroring `repository.spec.ts`.
- **Precedent gap**: the Repository module being mirrored has zero existing unit/web tests, and this
  repo has no ArchUnit/`ArchitectureTest` at all — the new module should not rely on an architecture
  test catching layer violations; manual review is the only enforcement mechanism currently.

## 12. Operation / Monitoring Impact

- Preserve `traceId` propagation through `ErrorResponse` for debugging failures.
- Use the existing `AdminAuditLogService` best-effort pattern — an audit-write failure must never
  block or roll back the business transaction (per `ticket-rules.md` "Must Not Do").
- No new monitoring/alerting infrastructure is required for this phase.
- Soft delete must be treated as logical retention, not physical removal, in any future operational
  runbook.

## 13. Rollout / Rollback Impact

- Rollout is purely additive: new API routes, new page, new migration — no existing feature is
  modified beyond the three narrow touch points listed in §3.
- FE rollback: remove the new route/page wiring and its `endpoints.ticketBugMetrics` calls; revert
  the `RequireDashboardAccess` union edit if that path was taken.
- BE rollback: remove the new controller/service/DTOs/ports/adapter/mapper in a single revert set.
- DB rollback: never edit the committed `V509` migration once merged — add a new corrective migration
  if the schema needs to change again (per `.claude/rules/00-safety.md` destructive-command policy
  and `docs/changes/REPOSITORY` precedent).
- This phase is documentation-only; no production rollback action is required yet.

## 14. Explicit Non-Impact Zones

- `RepositoryController`/`RepositoryService`/`RepositoryDtos`/`RepositoryModel`/`RepositoryMapper`
  are not modified — they are read-only pattern references for the new module.
- `QaDashboardService` is not modified — its ADMIN/QA-only role-check shape does not support this
  ticket's PM/QA-mutate + DEV-view requirement, so a **new** role-check method is added elsewhere
  rather than editing this shared service (per `context.md`/`ticket-rules.md`).
- `AppUser.Role` enum is not touched — it cannot represent QA/DEV and is not used by this feature.
- Existing migrations (`V4` through `V508`) are not edited — the change is purely additive (`V509`).
- Webhook handlers, batch/job processing, and connector sync/ingestion flows are not impacted — this
  feature is not event-driven.
- Auth bootstrap and session handling are not impacted — the feature uses existing authenticated-
  session patterns only.
- `tbl_dim_project`/`tbl_dim_repository`/`tbl_dim_ticket` schemas themselves are not altered — only
  read from, via independent existence/active checks (no new FK added to `tbl_dim_ticket`).
- Project, Organization, Customer, Team, Role, and other unrelated governance/dashboard modules are
  not impacted except as read-only comparison baselines.

## 15. Evidence Used

- `docs/changes/BUG-DASHBOARD/spec-pack.md`
- `docs/changes/BUG-DASHBOARD/context.md`
- `docs/changes/BUG-DASHBOARD/ticket-rules.md`
- `docs/standards/templates/_ticket-template/impact-analysis.md`, `impl-plan.md`
- `docs/changes/REPOSITORY/impact-analysis.md`, `impl-plan.md` (style/format precedent)
- `.claude/rules/00-safety.md`, `10-style.md`, `20-architecture.md`, `30-security.md`, `40-testing.md`
- `EDCAP_BE/src/main/java/com/sdd/platform/web/rest/RepositoryController.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/governance/RepositoryService.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/web/dto/RepositoryDtos.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/domain/model/RepositoryModel.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/qadashboard/QaDashboardService.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/persistence/QaDashboardRepositoryPort.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/web/security/CurrentAppUserResolver.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/web/exception/ErrorResponse.java`, `GlobalExceptionHandler.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/application/service/AdminAuditLogService.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/mapper/RepositoryMapper.java` + `.xml`
- `EDCAP_BE/src/main/resources/db/migration/V4__init_shema_v2.sql`, `V120__project_management.sql`,
  `V130__repository_management.sql`, `V508__add_submitted_by_to_review.sql`
- `EDCAP_BE/src/test/UnitTest/java/.../ProjectServiceTest.java`, `ProjectControllerTest.java`
- `EDCAP_FE/src/pages/RepositoryPage.tsx`
- `EDCAP_FE/src/lib/api.ts`
- `EDCAP_FE/src/pages/development-dashboard/DevelopmentDashboardPage.tsx`, `DevFilterBar.tsx`
- `EDCAP_FE/src/hooks/useAuth.ts`
- `EDCAP_FE/src/components/auth/RequireDashboardAccess.tsx`
- `EDCAP_FE/src/App.tsx`
- `EDCAP_FE/src/pages/RolePage.tsx`
- `EDCAP_FE/src/interfaces/index.ts`
- `EDCAP_FE/public/locales/en/locale.json`, `ja/locale.json`, `vi/locale.json`
- `EDCAP_FE/src/__ tests __/repository/repository.test.tsx`, `repository-api.test.ts`
- `EDCAP_FE/e2e_tests/tests/repository/repository.spec.ts`
- Note: `docs/architecture/route-api-map.md`, `service-layer-map.md`, `repository-db-map.md` were
  checked but found stale relative to this domain (they document a legacy webhook/ingestion
  `repository` table, not the governance CRUD module) and are **not** used as evidence above.
