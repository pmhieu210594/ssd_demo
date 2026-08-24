# Implementation Plan

**Ticket ID**: BUG-DASHBOARD
**Create date**: 2026-08-06 07:45:13
**Author**: Claude
**Update date**: 2026-08-06 08:45:00

## 1. Implementation Principle

Follow the `RepositoryController`/`RepositoryService`/`RepositoryDtos`/`RepositoryModel`/`RepositoryMapper` governance CRUD shape as the structural template, but replace its ADMIN-only gating with a new PM/QA-mutate + DEV-view + others-blocked role check modeled on `QaDashboardService.requireQaAccess`'s shape (ADMIN bypass + `findProjectRole` + role-string compare), extended with PM and DEV branches that do not exist yet anywhere in the codebase. No cross-validation between `repositoryId` and `ticketId` is implemented at any layer (resolved H-BUG-DASHBOARD-1). See `context.md` for the full source inventory this plan is grounded in.

## 2. Alternative Plan

| option | summary | pros | cons | decision |
|---|---|---|---|---|
| A. New standalone `TicketBugMetricsService` with its own role-check method | Self-contained service, own role-gating method mirroring `QaDashboardService`'s shape | No risk of destabilizing existing `QaDashboardService` callers; clear ownership | Some duplication of the role-check pattern | Pending — recommended, to be confirmed at Phase 3 kickoff |
| B. Extend `QaDashboardService` with PM/DEV branches and reuse it for this feature too | Single source of role-check logic | Couples an unrelated feature's access rules into `QaDashboardService`; risks regressing existing QA-dashboard behavior | Higher blast radius, against `.claude/rules/20-architecture.md` module boundaries | Rejected — do not touch `QaDashboardService` for this ticket |
| C. Reuse `RequireDashboardAccess` (FE) by adding a new `DashboardAccessKey` | Consistent with other dashboards' FE guard pattern | Requires editing a shared union + map in `RequireDashboardAccess.tsx`; screen isn't really a "dashboard" | Pending — flagged as open decision in `context.md`, not resolved here |
| D. Bespoke lightweight FE route guard specific to this page | Avoids touching shared `RequireDashboardAccess` file | New one-off guard component to maintain | Pending — alternative to C, decide together at Phase 3 kickoff |

## 3. Reason for Choosing the Alternative Plan

Option A is preferred for the backend: it keeps `QaDashboardService` untouched (avoiding regression risk on the existing QA dashboard) while still following its proven role-gating shape. The FE guard choice (C vs. D) is intentionally left open — both are viable and low-risk; the decision should be made once the exact screen/route placement is confirmed (Open Issue OI-BUG-DASHBOARD-2), not guessed here.

## 4. Expected Change File

| file | change summary | reason | related AC |
|---|---|---|---|
| `EDCAP_BE/src/main/resources/db/migration/V509__ticket_bug_metrics.sql` (new) | New table, soft-delete-from-creation columns, partial unique index on `ticket_id` | BR-1, BR-5, BR-6 | AC-3, AC-4, AC-8 |
| `EDCAP_BE/src/main/java/.../domain/model/TicketBugMetricsModel.java` (new) | Domain model, mirrors `RepositoryModel` shape + `isDeleted()` | BR-4, BR-5 | AC-1, AC-8 |
| `EDCAP_BE/src/main/java/.../web/dto/TicketBugMetricsDtos.java` (new) | DTO records mirroring `RepositoryDtos` shape | Contract compatibility | AC-1, AC-3, AC-7, AC-12 |
| `EDCAP_BE/src/main/java/.../application/usecase/.../TicketBugMetricsService.java` (new) | Service layer: search/get/create/update/softDelete + new role-check method | BR-1..BR-13 | AC-1..AC-13 |
| `EDCAP_BE/src/main/java/.../infrastructure/persistence/mapper/TicketBugMetricsMapper.java` + `.xml` (new) | MyBatis mapper mirroring `RepositoryMapper` conventions | Persistence | AC-1, AC-3, AC-7, AC-8, AC-9 |
| `EDCAP_BE/src/main/java/.../web/rest/TicketBugMetricsController.java` (new) | REST routes: list/detail/create/update/`PUT {id}/delete` | Contract | AC-1..AC-12 |
| `EDCAP_FE/src/lib/api.ts` | Add `endpoints.ticketBugMetrics` + entity types | FE-BE contract | AC-1, AC-3, AC-7, AC-12 |
| `EDCAP_FE/src/pages/<TBD route>/TicketBugMetricsPage.tsx` (new) | List/create/edit/delete page, cascading Project→Repository→Ticket filter | AC-1, AC-2 | AC-1, AC-2, AC-3, AC-7, AC-8, AC-10, AC-11 |
| `EDCAP_FE/src/App.tsx` | Register new route (path/menu placement TBD, OI-BUG-DASHBOARD-2) | Routing | — |

## 5. Class / Function / Method to Add or Modify

| target | action | input | output | note |
|---|---|---|---|---|
| `TicketBugMetricsService.search` | Add | `projectId?, repositoryId?, page, size, AuthUserContext caller` | `PageResult<TicketBugMetricsModel>` | Mirrors `RepositoryService.search` |
| `TicketBugMetricsService.get` | Add | `UUID id, AuthUserContext caller` | `TicketBugMetricsModel` | 404 if not found/deleted |
| `TicketBugMetricsService.create` | Add | `projectId, repositoryId, ticketId, internalBugCount, customerBugCount, note?, AuthUserContext caller` | `TicketBugMetricsModel` | 409 if active row exists for `ticketId` (BR-6) |
| `TicketBugMetricsService.update` | Add | `UUID id, internalBugCount, customerBugCount, note?, AuthUserContext caller` | `TicketBugMetricsModel` | Refresh `updated_at/by` |
| `TicketBugMetricsService.softDelete` | Add | `UUID id, AuthUserContext caller` | `TicketBugMetricsModel` | Sets `delete_flag/deleted_at/deleted_by/status='DELETED'` |
| `TicketBugMetricsService.requireMutateAccess` / `.requireViewAccess` (naming TBD) | Add (new — no existing equivalent) | `AuthUserContext caller, UUID projectId` | `void` (throws `ForbiddenException`) | ADMIN bypass; PM/QA → mutate+view; DEV → view only; others → blocked entirely |

## 6. SQL / Query / Repository Policy

- MyBatis mapper mirrors `RepositoryMapper`'s conventions: `@Param` on multi-arg methods, resultMap with `UUIDTypeHandler` for UUID columns, reusable `<sql>` fragments, explicit `#{status}::record_status` cast.
- All mutation queries (`update`, `softDelete`) scope their `WHERE` clause defensively to `delete_flag = FALSE AND deleted_at IS NULL AND status = 'ACTIVE'`; a 0-row result maps to `NotFoundException` in the service.
- `projectId`/`repositoryId`/`ticketId` existence/active checks query `tbl_dim_project`/`tbl_dim_repository`/`tbl_dim_ticket` independently — no join/cross-check between `repository_id` and `ticket_id`.

## 7. Validation / Error / Logging Policy

- Bean validation (or manual service-level checks) reject negative/non-integer/missing `internalBugCount`/`customerBugCount` → 400.
- `note` max length 500 → 400 if exceeded; `@NoXssFields` on request records.
- `projectId`/`repositoryId`/`ticketId` not found or not active → 404 (`NotFoundException`).
- Duplicate active row for `ticketId` on create → 409 (`ApplicationException`-style conflict).
- Role check failure → 403 (`ForbiddenException`).
- All error responses flow through `GlobalExceptionHandler` into the standard `ErrorResponse` envelope — no ad-hoc `ResponseEntity` status codes in the controller.
- Audit: `AdminAuditLogService.logCreate/logUpdate/logDelete/logCrudFailure`, snapshot-before-mutation for update, best-effort (never blocks the transaction).

## 8. Migration / Rollback Policy

- New migration `V509__ticket_bug_metrics.sql` (verify still free at Phase 3 start) — additive only (`CREATE TABLE`), no changes to existing tables.
- Rollback = a follow-up migration dropping the table if ever needed; no data migration/backfill required since this is a brand-new table with no prior data.

## 9. Step Implementation

| step | action | target file | verification | stop condition |
|---|---|---|---|---|
| 1 | Write and apply migration | `V509__ticket_bug_metrics.sql` | Flyway migrates cleanly against dev DB | V509 taken by another migration |
| 2 | Domain model + mapper + mapper XML | `TicketBugMetricsModel`, `TicketBugMetricsMapper(.xml)` | Unit test on mapper via integration test | — |
| 3 | Service layer incl. new role-check method | `TicketBugMetricsService` | Unit tests per BR-1..BR-13 | Seeded DEV role code mismatch (OI-4) |
| 4 | Controller + DTOs | `TicketBugMetricsController`, `TicketBugMetricsDtos` | `@WebMvcTest` slice tests | — |
| 5 | FE API client + types | `lib/api.ts` | Type-check passes | Type placement decision (api.ts vs page-local) |
| 6 | FE page + route registration | `TicketBugMetricsPage.tsx`, `App.tsx` | Manual run + Playwright E2E | Route/menu placement decision (OI-2) |

## 10. How to Verify Each Step

`mvn clean verify` (BE, incl. ArchUnit `ArchitectureTest` staying green) after steps 1-4; `npm run typecheck` and `npm run build` (FE) after steps 5-6; manual dev-server run per `.claude/rules` for UI verification before marking complete.

## 11. Corresponding AC Table

| AC ID | implementation point | verification |
|---|---|---|
| AC-BUG-DASHBOARD-1 | `TicketBugMetricsService.search` + list DTO | Web/integration test |
| AC-BUG-DASHBOARD-13 | No cross-validation between `repositoryId`/`ticketId` in create/update | Unit test |
| AC-BUG-DASHBOARD-2 | FE empty-state rendering | Component/E2E test |
| AC-BUG-DASHBOARD-3 | `TicketBugMetricsService.create` happy path | Integration test |
| AC-BUG-DASHBOARD-4 | Duplicate-active-row 409 check | Unit + integration test |
| AC-BUG-DASHBOARD-5 | Bug-count validation | Unit test |
| AC-BUG-DASHBOARD-6 | Note length validation | Unit test |
| AC-BUG-DASHBOARD-7 | `update` refreshes audit fields | Integration test |
| AC-BUG-DASHBOARD-8 | `softDelete` sets deletion metadata | Integration test |
| AC-BUG-DASHBOARD-9 | 404 on nonexistent/deleted row | Integration test |
| AC-BUG-DASHBOARD-10 | Role check DEV branch (view ok, mutate 403) | Unit test |
| AC-BUG-DASHBOARD-11 | Role check no-access branch | Unit test |
| AC-BUG-DASHBOARD-12 | Error envelope shape | Web test |

## 12. Stop / Ask Condition

See `ticket-rules.md` §"Stop / Ask Conditions".

## 13. Do Not Do This Ticket

- Do not touch `QaDashboardService` (Option B rejected above).
- Do not add a `repository_id`↔`ticket_id` FK or cross-validation.
- Do not finalize FE route/menu placement or `RequireDashboardAccess` vs. bespoke-guard choice without confirming with the team first (both flagged Open/Pending above).

## 14. Open Related Issues

Carried from `spec-pack.md` §18: OI-BUG-DASHBOARD-1 (impl not yet started), OI-2 (FE route/menu placement), OI-3 (i18n resource locations), OI-4 (seeded DEV role code), OI-5 (whitespace-only note handling). Plus new: FE guard reuse-vs-bespoke decision (this document, §2/§3), FE type placement decision (`context.md` DTO mapping table).
