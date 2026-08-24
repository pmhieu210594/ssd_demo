# Implementation Plan

**Ticket ID**: AI-QUALITY
**Create date**: 2026-08-17 07:43:48
**Author**: BRYCENVN\nvt_dung
**Update date**: 2026-08-17 08:00:00

## 1. Implementation Principle

Mirror `TicketBugMetricsService`/`TicketBugMetricsController`/`TicketBugMetricsDtos`/
`TicketBugMetricsModel`/`TicketBugMetricsMapper` 1:1 as the structural template for
`AiQualityService`/`AiQualityController`/`AiQualityDtos`/`AiQualityModel`/`AiQualityMapper`,
including the exact `Access` enum shape (`MUTATE`/`VIEW_ONLY`/`NONE`), `resolveAccess`/
`requireViewAccess`/`requireMutateAccess` logic, and `AdminAuditLogService` call sequencing
(`snapshot`-before-mutate on update, `logCreate`/`logUpdate`/`logDelete` on success,
`logCrudFailure` in the catch block, `MODULE`/`ENTITY_TYPE = "AI_QUALITY"`). Diverge from the
template only where spec-pack explicitly requires it: (a) BR-3 repository↔project /
ticket↔repository parentage validation, which has no BUG-DASHBOARD precedent and must be
designed fresh; (b) an added `ticketId` filter parameter on the repository port's `findPage`/
`count`; (c) a smaller FE endpoint surface (`list/get/create/update/softDelete/access` only —
no `options`/`ticket-options`, since the ticket dropdown reuses the existing unfiltered
ticket-by-repository lookup per spec-pack §17 A-AI-QUALITY-11). All RBAC/soft-delete/audit
conventions were user-directed and source-verified against `TicketBugMetricsService.java` and
`V509__ticket_bug_metrics.sql` — see `context.md`/`source-inventory.md` for the full grounding.

## 2. Alternative Plan
| option | summary | pros | cons | decision |
|---|---|---|---|---|
| A. New standalone `AiQualityService` reusing `qaDashboardRepository.findProjectRole` as-is for per-project role lookup | Zero duplication of the role-lookup mechanism; consistent with how every other dashboard-style module (`TicketBugMetricsService` included) sources per-project role | If the lookup's contract turns out not to fit AI-QUALITY's needs once wired up, requires a Stop/Ask pause (ticket-rules.md) before proceeding | **Chosen** |
| B. Fork a parallel per-project-role lookup specific to AI-QUALITY | Full independence from the QA-dashboard-owned query | Duplicates a proven query for no stated reason; explicitly discouraged by `ticket-rules.md` Stop/Ask condition #1 | Rejected — no justification to duplicate |
| C. Inline BR-3 FK-parentage checks directly in `AiQualityService` (repository↔project, ticket↔repository) | Smallest surface for a Standard-complexity ticket; matches how `TicketBugMetricsService` inlines its own (looser) existence/active checks | Not reusable if a second consumer needs the same parentage check later | **Chosen for now** — revisit per ticket-rules.md Stop/Ask condition #2 if a second consumer appears |
| D. New shared FK-parentage validator (e.g. `HierarchyValidationHelper`) used by both BR-3 and any future consumer | Reusable, single source of truth for parentage rules | No existing precedent or second consumer to justify the abstraction yet; premature per this ticket's scope | Rejected for now — an implementation-phase Stop/Ask item, not a Phase 3 decision |

## 3. Reason for Choosing the Alternative Plan

Option A keeps this ticket aligned with the established dashboard-module convention (every
per-project role check in this codebase sources from the same shared query) and avoids
introducing a second, divergent lookup without cause — if reuse proves infeasible during
implementation, that is exactly the scenario ticket-rules.md's Stop/Ask condition #1 exists for,
so the plan explicitly defers rather than guesses. Option C is chosen over Option D because BR-3
has a single consumer today (`AiQualityService`) and no other ticket currently needs the same
repository↔project/ticket↔repository parentage check; introducing a shared validator now would be
speculative generalization ahead of a second real use case, which `.claude/rules` and general
project convention (see CLAUDE.md "Don't add features... beyond what the task requires") both
discourage. This is explicitly flagged as revisitable, not closed.

## 4. Expected Change File
| file | change summary | reason | related AC |
|---|---|---|---|
| `EDCAP_BE/src/main/resources/db/migration/V510__ai_quality.sql` (new) | New table `tbl_dim_ai_quality`, soft-delete-from-creation columns, partial unique index on `ticket_id`, `ck_access_log_module` ALTER adding `'AI_QUALITY'` | BR-1, BR-2, BR-4, BR-6 | AC-AI-QUALITY-1, -2, -5, -7, -8 |
| `EDCAP_BE/src/main/java/.../domain/model/AiQualityModel.java` (new) | Domain model, mirrors `TicketBugMetricsModel` + `isDeleted()` | BR-4 | AC-AI-QUALITY-7 |
| `EDCAP_BE/src/main/java/.../web/dto/AiQualityDtos.java` (new) | `AiQualityDto`, `AiQualityPageDto`, `CreateAiQualityRequest`/`UpdateAiQualityRequest` (`@NoXssFields`) | Contract compatibility | AC-AI-QUALITY-1, -6, -9 |
| `EDCAP_BE/src/main/java/.../application/port/out/persistence/AiQualityRepositoryPort.java` (new) | Persistence port, `findPage`/`count` extended with `ticketId` filter | §6.2 list filters | AC-AI-QUALITY-9 |
| `EDCAP_BE/src/main/java/.../infrastructure/persistence/adapter/AiQualityRepositoryAdapter.java` (new) | Adapter implementation | Persistence | AC-AI-QUALITY-1, -6, -7, -9 |
| `EDCAP_BE/src/main/java/.../infrastructure/persistence/mapper/AiQualityMapper.java` + `.xml` (new) | MyBatis mapper mirroring `TicketBugMetricsMapper` conventions | Persistence | AC-AI-QUALITY-1, -6, -7, -9 |
| `EDCAP_BE/src/main/java/.../application/usecase/aiquality/AiQualityService.java` (new) | Service: resolveAccess/requireViewAccess/requireMutateAccess, create/update/softDelete/get/list, **new** BR-3 parentage validation, `AdminAuditLogService` wiring | BR-1..BR-6 | AC-AI-QUALITY-1..12 |
| `EDCAP_BE/src/main/java/.../web/rest/AiQualityController.java` (new) | REST routes: `GET /`, `GET /{id}`, `POST /`, `PUT /{id}`, `PUT /{id}/delete`, `GET /access` | Contract | AC-AI-QUALITY-1..12 |
| `EDCAP_BE/src/test/UnitTest/java/.../AiQualityServiceTest.java`, `AiQualityControllerTest.java` (new) | Unit + controller tests covering every AC | Test coverage | AC-AI-QUALITY-1..12 |
| `EDCAP_FE/src/lib/api.ts` | Add `endpoints.aiQuality` (`list/get/create/update/softDelete/access`) + types | FE-BE contract | AC-AI-QUALITY-1, -6, -7, -9 |
| `EDCAP_FE/src/pages/ai-quality/AiQualityPage.tsx`, `types.ts`, `components/AiQualityFilterBar.tsx` (new) | List/create/edit/delete page, cascading Project→Repository→Ticket filter/drawer | AC-AI-QUALITY-1, -6, -7, -9, -10, -11 | AC-AI-QUALITY-1, -6, -7, -9, -10, -11 |
| `EDCAP_FE/src/components/auth/RequireAiQualityAccess.tsx` (new) | Route guard mirroring `RequireTicketBugMetricsAccess` | AC-AI-QUALITY-10, -11 | AC-AI-QUALITY-10, -11 |
| `EDCAP_FE/src/App.tsx` | Register new `<Route path="ai-quality">` | Routing | — |
| `EDCAP_FE/public/locales/{en,vi,ja}/locale.json` | Add `Pages.AiQuality.*` keys | i18n | — |
| `EDCAP_FE/src/__ tests __/ai-quality/*.test.ts(x)` (new) | FE unit tests | Test coverage | AC-AI-QUALITY-1, -6, -7, -9, -10, -11 |

## 5. Class / Function / Method to Add or Modify
| target | action | input | output | note |
|---|---|---|---|---|
| `AiQualityService.search` | Add | `projectId?, repositoryId?, ticketId?, search?, page, size, AuthUserContext caller` | `PageResult<AiQualityModel>` | Mirrors `TicketBugMetricsService.search`, extended with `ticketId` filter and a **working** `search` filter (ticket external key/title) — unlike `TicketBugMetricsPage`'s decorative-only `CSearch`, this one is wired end-to-end into `findPage`/`count` |
| `AiQualityService.get` | Add | `UUID id, AuthUserContext caller` | `AiQualityModel` | 404 if not found/deleted |
| `AiQualityService.create` | Add | `projectId, repositoryId, ticketId, aiQualityRate, AuthUserContext caller` | `AiQualityModel` | 409 if active row exists for `ticketId` (BR-2); 400 if BR-3 parentage fails |
| `AiQualityService.update` | Add | `UUID id, aiQualityRate, AuthUserContext caller` | `AiQualityModel` | Refreshes `updated_at/by`; project/repository/ticket immutable after create |
| `AiQualityService.softDelete` | Add | `UUID id, AuthUserContext caller` | `AiQualityModel` | Sets `delete_flag/deleted_at/deleted_by/status='DELETED'` + `updated_at/by` in one call |
| `AiQualityService.resolveAccess` / `requireViewAccess` / `requireMutateAccess` | Add (mirrors `TicketBugMetricsService` verbatim) | `AuthUserContext caller, UUID projectId` | `Access` enum / `void` (throws `ForbiddenException`) | ADMIN bypass; PM/QA→MUTATE; DEV→VIEW_ONLY; else→NONE (denied read+write) |
| `AiQualityService.ensureRepositoryBelongsToProject` | Add (**new** — no BUG-DASHBOARD precedent) | `UUID projectId, UUID repositoryId` | `void` (throws validation exception) | BR-3 |
| `AiQualityService.ensureTicketBelongsToRepository` | Add (**new** — no BUG-DASHBOARD precedent) | `UUID repositoryId, UUID ticketId` | `void` (throws validation exception) | BR-3 |
| `AiQualityService.normalizeRate` | Add | `BigDecimal rate` | validated `BigDecimal` | BR-6: reject null, outside [0,100], scale > 2 |

## 6. SQL / Query / Repository Policy

- MyBatis mapper mirrors `TicketBugMetricsMapper`'s conventions: `@Param` on multi-arg methods,
  resultMap with `UUIDTypeHandler` for UUID columns, reusable `<sql>` fragments, explicit
  `#{status}::record_status` cast on insert/update.
- All mutation queries (`update`, `softDelete`) scope their `WHERE` clause defensively to
  `delete_flag = FALSE AND deleted_at IS NULL AND status = 'ACTIVE'`; a 0-row result maps to
  `NotFoundException` in the service — prevents racing soft-deletes from corrupting data.
- `findPage`/`count` extend the BUG-DASHBOARD pattern with an additional optional `ticketId`
  filter (AND-combined with `projectId`/`repositoryId` when present), per spec-pack §6.2.
- `projectId`/`repositoryId`/`ticketId` existence/active checks query `tbl_dim_project`/
  `tbl_dim_repository`/`tbl_dim_ticket` — **unlike** `TicketBugMetricsService`, these are not
  independent: BR-3 requires `repositoryId` to belong to `projectId` and `ticketId` to belong to
  `repositoryId`, checked via a single join or two sequential lookups (implementation detail,
  covered by Stop/Ask condition #2 for whether this becomes a shared helper).

## 7. Validation / Error / Logging Policy

- `ai_quality_rate`: `DECIMAL(5,2)`, reject null, reject outside [0.00, 100.00], reject scale > 2
  (e.g. 50.555) → 400 (BR-6, spec-pack §6.5).
- `repository_id` not belonging to `project_id`, or `ticket_id` not belonging to `repository_id`
  → 400 validation error, never a silent auto-correction (BR-3).
- `projectId`/`repositoryId`/`ticketId` not found or not active → 400 (never 404) per existing
  `TicketBugMetricsService` convention (malformed/missing UUID → 400; well-formed but
  non-existent → 400 validation per spec-pack §6.5, not 404 — 404 is reserved for the AI Quality
  row itself). Implemented via `BusinessRuleException` in `ensureProjectActive`/
  `ensureRepositoryBelongsToProject`/`ensureTicketBelongsToRepository` — none of the three may use
  `NotFoundException`, since that maps to 404 in `GlobalExceptionHandler`.
- Duplicate active row for `ticketId` on create → 409 (`ConflictException`/`ApplicationException`).
- Role check failure → 403 (`ForbiddenException`) — applies to write endpoints for VIEW_ONLY
  callers and to every endpoint (including read) for NONE callers, per BR-5.
- All error responses flow through `GlobalExceptionHandler` into the standard `ErrorResponse`
  envelope — no ad-hoc `ResponseEntity` status codes in the controller.
- Audit: `AdminAuditLogService.logCreate/logUpdate/logDelete/logCrudFailure`,
  `MODULE = "AI_QUALITY"`, `ENTITY_TYPE = "AI_QUALITY"`, snapshot-before-mutation for update,
  best-effort (never blocks the transaction) — **required** for this ticket per
  `ticket-rules.md`/`context.md` (user-confirmed 2026-08-17), including `logCrudFailure` on the
  failure path.
- No PII/secrets in logs; no stack traces reach the client (per `.claude/rules/30-security.md`).

## 8. Migration / Rollback Policy

- New migration `V510__ai_quality.sql` (verified free at Phase 3 time — current highest is
  `V509`; re-check the migration directory immediately before implementation starts in case
  another ticket has landed a migration since, per ticket-rules.md Stop/Ask condition #3).
- Additive only: `CREATE TABLE tbl_dim_ai_quality`, its 3 indexes, plus one `ALTER TABLE
  tbl_fact_access_log DROP CONSTRAINT ck_access_log_module` / `ADD CONSTRAINT ... CHECK (module IN
  (..., 'AI_QUALITY'))` pair (mirrors `V509`'s own handling of the same constraint). No other
  existing table is touched.
- This codebase has no automated migration-rollback tooling (Flyway `undo` is not configured, per
  `docs/standards/database.md`'s "never edit a committed migration" convention observed in
  `V509`/BUG-DASHBOARD). Rollback = write a new corrective migration (e.g. dropping the table and
  reverting the CHECK constraint to its pre-`V510` list) — never edit or delete `V510` once merged.
  No data backfill/migration required since this is a brand-new table with no prior data.

## 9. Step Implementation
| step | action | target file | verification | stop condition |
|---|---|---|---|---|
| 1 | Write and apply migration | `V510__ai_quality.sql` | Flyway migrates cleanly against dev DB; `ck_access_log_module` accepts `'AI_QUALITY'` | `V510` taken by another migration since Phase 3 (ticket-rules Stop/Ask #3) |
| 2 | Domain model + port interface | `AiQualityModel.java`, `AiQualityRepositoryPort.java` | Compiles; mirrors `TicketBugMetricsModel`/`Port` shape | — |
| 3 | Mapper + XML + adapter | `AiQualityMapper.java`/`.xml`, `AiQualityRepositoryAdapter.java` | Adapter-level test against dev DB (insert/update/softDelete/find/count with `ticketId` filter) | — |
| 4 | Service layer incl. RBAC + BR-3 | `AiQualityService.java` | Unit tests per BR-1..BR-6, RBAC matrix, boundary values | `qaDashboardRepository.findProjectRole` not directly reusable as-is (Stop/Ask #1); BR-3 validator placement unclear once real usage is seen (Stop/Ask #2) |
| 5 | Controller + DTOs | `AiQualityController.java`, `AiQualityDtos.java` | `MockMvcBuilders.standaloneSetup` + `GlobalExceptionHandler` tests (mirrors `TicketBugMetricsControllerTest`) | — |
| 6 | FE API client + types | `lib/api.ts` — `endpoints.aiQuality` | `npm run typecheck` passes | — |
| 7 | FE route guard | `RequireAiQualityAccess.tsx` | Manual auth-flow check (401/403 → force logout) | — |
| 8 | FE page + drawer + table | `AiQualityPage.tsx`, `types.ts`, `AiQualityFilterBar.tsx` | Manual dev-server run: create/edit/view/delete flows, cascading dropdowns, RBAC-gated actions | — |
| 9 | FE route registration + locale keys | `App.tsx`, `en/vi/ja locale.json` | Route resolves at `/:lang/ai-quality`; no missing-key warnings in any locale | — |
| 10 | FE Vitest tests | `__ tests __/ai-quality/*.test.ts(x)` | `npm run test` passes | — |
| 11 | Full AC traceability pass | all of the above | Every AC-AI-QUALITY-1..12 has a passing test (see §11) | Any AC without a covering test |

## 10. How to Verify Each Step

`mvn clean verify` (BE) after steps 1-5 — no ArchUnit test exists in this repo to gate layering
(confirmed absent; see `source-availability.md` §2), so hexagonal-layer correctness is verified by
manual review against the directory-structure convention instead. `npm run typecheck` and `npm run
build` (FE) after steps 6-9. `npm run test` (FE) after step 10. Manual dev-server run per
`.claude/rules` for UI verification (RBAC-gated buttons, drawer cascading, error-toast rendering)
before marking any FE step complete.

## 11. Corresponding AC Table
| AC ID | implementation point | verification |
|---|---|---|
| AC-AI-QUALITY-1 | `AiQualityService.create` happy path | Unit + controller test |
| AC-AI-QUALITY-2 | Duplicate-active-row 409 check (BR-2) | Unit test |
| AC-AI-QUALITY-3 | BR-3 repository↔project mismatch → 400 | Unit test |
| AC-AI-QUALITY-4 | BR-3 ticket↔repository mismatch → 400 | Unit test |
| AC-AI-QUALITY-5 | BR-6 boundary: 100.00 accepted, 100.01/-0.01 rejected | Unit test |
| AC-AI-QUALITY-6 | `AiQualityService.update` refreshes `ai_quality_rate`/`updated_at`/`updated_by` | Unit + integration test |
| AC-AI-QUALITY-7 | `softDelete` excludes row from get/list, no physical DELETE | Unit + integration test |
| AC-AI-QUALITY-8 | Re-create after soft-delete succeeds (no false 409) | Unit test — soft-delete lifecycle |
| AC-AI-QUALITY-9 | `list` filters by project/repository/ticket, `PageResult`-shaped response | Unit + controller test |
| AC-AI-QUALITY-10 | RBAC: VIEW_ONLY (DEV) → 403 on write, 200 on read | Unit test — RBAC matrix |
| AC-AI-QUALITY-11 | RBAC: NONE → 403 on every endpoint incl. read | Unit test — RBAC matrix |
| AC-AI-QUALITY-12 | RBAC: global ADMIN → MUTATE regardless of project role | Unit test — RBAC matrix |
| AC-AI-QUALITY-13 | `search` query param filters list results by ticket external key/title, AND-combined with other filters | Unit + controller test (post-implementation addition) |

## 12. Stop / Ask Condition

Carried verbatim from `ticket-rules.md` §"Stop / Ask Conditions":
- Stop and ask if the per-project role lookup mechanism needed for `resolveAccess()`
  (`TicketBugMetricsService` uses `qaDashboardRepository.findProjectRole(caller, projectId)`) is
  not directly reusable as-is for AI-QUALITY once implementation begins — do not fork or
  reimplement a parallel lookup without confirming.
- Stop and ask if BR-3's FK-parentage validation (repository belongs to project; ticket belongs to
  repository) needs a new reusable validation helper versus inline checks — this logic does not
  exist in the BUG-DASHBOARD analog and its placement (service vs. shared validator) is an
  implementation-phase decision, not yet specified.
- Stop and ask if the exact Flyway migration version number (`V510`) conflicts with any
  concurrently in-progress migration — re-check `EDCAP_BE/src/main/resources/db/migration/` for
  the latest `V*` immediately before assigning it in implementation.

## 13. Do Not Do This Ticket

Carried from `ticket-rules.md` §"Must Not Do":
- Do not introduce a new `ApiResponse<T>` envelope.
- Do not introduce Spring `MessageSource` / `.properties`-based i18n.
- Do not use or add `@PreAuthorize` anywhere.
- Do not use `AppUser.Role`/`UserAccountAdminService.requireAdmin`'s single-global-role RBAC model.
- Do not add `QA`/`DEV` values to the `AppUser.Role` enum.
- Do not implement bulk import/export of AI Quality rates.
- Do not implement automatic AI Quality computation from CI/AI-review tooling.
- Do not add a historical audit trail / version history beyond standard `updated_at`/`updated_by`.
- Do not modify `tbl_dim_project`, `tbl_dim_repository`, or `tbl_dim_ticket` schemas.
- Do not implement a hard/physical `DELETE` on `tbl_dim_ai_quality` — soft delete only.
- Do not add a new "available tickets" filtered-lookup endpoint that excludes already-tracked
  tickets — Option A (unfiltered dropdown) was explicitly user-confirmed.
- Do not add Playwright E2E tests for this ticket by default.

## 14. Open Related Issues

None outstanding — spec-pack §18 confirms all former Open Issues are resolved. The only
carried-forward items are the three Stop/Ask conditions in §12 above (per-project role lookup
reusability, BR-3 validator placement, migration version freshness check), which are
implementation-phase confirmations, not open specification gaps.
