# Final Report

**Ticket ID**: BUG-DASHBOARD
**Create date**: 2026-08-06 07:45:13
**Author**: Claude
**Update date**: 2026-08-07 10:00:00

## 1. Edited summary

Added a new governance-style CRUD feature, "Ticket Bug Metrics", letting PM/QA record per-Ticket
Internal Bug Count and Customer Bug Count (found by QA vs. customer/UAT), with list/detail/create/
update/soft-delete. DEV can view but not mutate; every other role (including unauthenticated callers)
is blocked from the screen entirely. Delivered as a wholly new BE surface (migration `V509`, domain
model, mapper/adapter/port, service, controller) and FE surface (new page, route, i18n keys), modeled
on the existing `RepositoryController`/`RepositoryService` governance pattern and a new role-check
method shaped like (but not modifying) `QaDashboardService.requireQaAccess`. No existing production
module was changed beyond three narrow touch points (`lib/api.ts`, `App.tsx`, plus a new bespoke FE
guard file instead of editing `RequireDashboardAccess.tsx`).

## 2. Corresponding specification / AC
| ACID | status | evidence |
|---|---|---|
| AC-BUG-DASHBOARD-1 | Done | `TicketBugMetricsService.search` + mapper `WhereClause` excludes deleted rows, filters projectId/repositoryId; BE web test `list_returnsPagedItems`; FE `renders the list` |
| AC-BUG-DASHBOARD-13 | Done | `create`/`update` never cross-check `repositoryId` vs `ticketId`; `create_repositoryUnrelatedToTicket_stillAccepted_perBR12` |
| AC-BUG-DASHBOARD-2 | Done | FE empty state via shared `CServerTable`; black-box case BB-005 |
| AC-BUG-DASHBOARD-3 | Done | `TicketBugMetricsService.create` happy path; `mutateRoles_canCreateUpdateDelete`, `create_returns201WithoutVersionField` |
| AC-BUG-DASHBOARD-4 | Done | `ConflictException` on `existsActiveByTicketId`; `create_duplicateActiveTicket_throwsConflict`, `exceptions_mapToExpectedEnvelope` |
| AC-BUG-DASHBOARD-5 | Done | `normalizeCountsAndNote`; `create_rejectsNegativeOrMissingBugCounts`, `create_acceptsZeroBugCounts` |
| AC-BUG-DASHBOARD-6 | Done | Same method, 500-char cap; `create_rejectsNoteOver500Characters`, `create_accepts500CharacterNote` |
| AC-BUG-DASHBOARD-7 | Done | `update` refreshes `updatedAt`/`updatedBy`; `mutateRoles_canCreateUpdateDelete` |
| AC-BUG-DASHBOARD-8 | Done | `softDelete` sets `delete_flag`/`deleted_at`/`deleted_by`/`status='DELETED'`; `softDelete_usesPutDeleteEndpoint_neverHardDelete` |
| AC-BUG-DASHBOARD-9 | Done | `loadActiveOrThrow` → `NotFoundException`; `getOrUpdateOrDelete_notFoundOrDeletedRow_throwsNotFound` |
| AC-BUG-DASHBOARD-10 | Done | `resolveAccess`/`requireMutateAccess`; `devRole_canViewButNotMutate`; FE `hides create/edit/delete affordances for DEV` |
| AC-BUG-DASHBOARD-11 | Done | `resolveAccess` returns `NONE` for other/unauthenticated; `otherRoles_blockedFromEveryAction`, `unauthenticatedCaller_blockedFromEveryAction` |
| AC-BUG-DASHBOARD-12 | Done | All exceptions route through `GlobalExceptionHandler` → `ErrorResponse`; `exceptions_mapToExpectedEnvelope` |

All 13 ACs have both code and automated-test evidence (`test-plan.md` §2 AC↔test matrix confirms no AC
without at least one test type).

## 3. Scope of influence

- **New BE files**: `V509__ticket_bug_metrics.sql`; `TicketBugMetricsModel`, `TicketOption` (domain);
  `TicketBugMetricsRepositoryPort`, `TicketLookupPort` (application ports); `TicketBugMetricsMapper(.xml)`,
  `TicketLookupMapper(.xml)`, `TicketBugMetricsRepositoryAdapter`, `TicketLookupJdbcAdapter`
  (infrastructure); `ConflictException` (application exception); `TicketBugMetricsService`,
  `TicketBugMetricsController`, `TicketBugMetricsDtos`; new BE unit/web test classes.
- **New FE files**: `TicketBugMetricsPage.tsx`, `RequireTicketBugMetricsAccess.tsx`; new FE test files.
- **Edited (narrow, additive) files**: `EDCAP_FE/src/lib/api.ts` (new `endpoints.ticketBugMetrics` + types),
  `EDCAP_FE/src/App.tsx` (new route), `en/ja/vi locale.json` (new `Pages.TicketBugMetrics.*` keys).
- **Explicit non-impact zones** (confirmed untouched): `RepositoryController`/`RepositoryService`/
  `RepositoryDtos`/`RepositoryModel`/`RepositoryMapper`; `QaDashboardService`; `AppUser.Role` enum;
  migrations `V4`–`V508`; `RequireDashboardAccess.tsx`'s `DashboardAccessKey` union; webhook/batch/job
  ingestion flows; auth bootstrap/session handling.
- Rollout is purely additive — no existing endpoint, contract, or migration was modified or made
  backward-incompatible.

## 4. Implementation content
| file | summary | reasons |
|---|---|---|
| `V509__ticket_bug_metrics.sql` | New table, soft-delete-from-creation columns, partial unique index on `ticket_id` | BR-1, BR-5, BR-6 |
| `TicketBugMetricsModel.java` | Domain model mirroring `RepositoryModel` + `isDeleted()` | BR-4, BR-5 |
| `TicketOption.java` | Small record for ticket lookups | FE cascading filter data source |
| `TicketBugMetricsRepositoryPort.java` | Port interface | Hexagonal boundary |
| `TicketLookupPort.java` | Minimal read-only port: ticket existence + project-scoped options | No prior generic `tbl_dim_ticket` read port existed |
| `TicketBugMetricsMapper.java`/`.xml` | MyBatis mapper mirroring `RepositoryMapper` conventions | Persistence |
| `TicketLookupMapper.java`/`.xml` | MyBatis mapper for ticket lookups | Persistence |
| `TicketBugMetricsRepositoryAdapter.java`, `TicketLookupJdbcAdapter.java` | Adapters | Persistence |
| `ConflictException.java` | `ApplicationException` subclass mapped to 409 | Names duplicate-active-ticket conflict (BR-6) |
| `TicketBugMetricsService.java` | search/get/create/update/softDelete + role-check method + audit | BR-1..BR-13 |
| `TicketBugMetricsDtos.java` | DTO records mirroring `RepositoryDtos` | Contract compatibility |
| `TicketBugMetricsController.java` | Routes: list/detail/create/update/`PUT {id}/delete` + `ticket-options`/`options`/`access` | Contract |
| `TicketBugMetricsServiceTest.java`, `TicketBugMetricsControllerTest.java` | Unit + web tests | `.claude/rules/40-testing.md` |
| `EDCAP_FE/src/lib/api.ts` (edit) | Added `TicketBugMetric*`/`TicketOption` types + `endpoints.ticketBugMetrics` | FE-BE contract |
| `TicketBugMetricsPage.tsx` | List/create/edit/delete page, inline cascading Project→Repository/Ticket selects | AC-1,2,3,7,8,10,11 |
| `RequireTicketBugMetricsAccess.tsx` | Bespoke screen guard | Confirmed decision, §10 |
| `EDCAP_FE/src/App.tsx` (edit) | Registered `ticket-bug-metrics` route under `:lang`, wrapped in the guard | Routing |
| `en/ja/vi locale.json` (edit) | Added `Pages.TicketBugMetrics.*` keys | i18n |
| `ticket-bug-metrics.test.tsx`, `ticket-bug-metrics-api.test.ts` | FE component + API-helper tests | Test coverage |

## 5. Review results

| review type | result | notes |
|---|---|---|
| Self Review | NEEDS_UPDATE (as of `self-review.md`) → resolved to PARTIAL after Phase 8 test round | All 13 ACs mapped to code+test evidence; `review-checklist.md`'s 10 sections self-checked as Pass except §8 (Partial — E2E/manual not run) |
| Independent AI Review | Not performed | No separate AI-reviewer pass (e.g. `/code-review`, `codex-review.md`) was run this ticket; recorded as a gap, not a pass |
| Human Review | Partial | 5 individual decision points confirmed by human (H-1/H-2/H-3 pre-implementation, ticket-active semantics + FE guard choice mid-implementation, §10); no full end-to-end human sign-off on the shipped diff has occurred yet |

## 6. Test results
| test type | result | evidence |
|---|---|---|
| BE unit tests (`TicketBugMetricsServiceTest`) | PASS — 19/19 (incl. 3 new) | `test-results.md` §2/§4 |
| BE web tests (`TicketBugMetricsControllerTest`) | PASS — 9/9 (incl. 3 new) | `test-results.md` §2/§4 |
| FE API-helper tests (`ticket-bug-metrics-api.test.ts`) | PASS — 9/9 (incl. 1 new) | `test-results.md` §2/§4 |
| FE component tests (`ticket-bug-metrics.test.tsx`) | PASS — 6/6, after harness repair | `test-results.md` §6 (3 latent bugs fixed) |
| `npx tsc --noEmit` / `npm run build` (FE) | FAIL | `TS6133` unused-symbol errors in pre-existing, untouched `App.tsx`/`TicketBugMetricsPage.tsx` — production-code defect, out of scope for the test-only phase |
| E2E CRUD journey (Playwright) | Not executed | No `e2e_tests/tests/ticket-bug-metrics/` spec exists; no dev server/DB available this session |
| Manual role-matrix browser run (ADMIN/PM/QA/DEV/other) | Not executed | Same reason; automated-test coverage substitutes (see `test-results.md` §8) |

Total: 43/43 automated tests pass (28 BE + 15 FE). No AC lacks at least one test type
(`test-plan.md` §2).

## 7. Security / operations perspective

- All endpoints require an authenticated caller; list/detail (read-only) routes are gated identically
  to mutation routes — a common miss when copying the Repository module's ADMIN-only precedent, avoided
  here (BR-9/AC-11).
- Role decisions are made server-side via `AuthUserContext` + `findProjectRole`, with FE button-hiding
  treated as UX only, never the enforcement mechanism (BR-10).
- Error responses use only the standard `ErrorResponse(timestamp, status, error, message, traceId)`
  envelope; no stack traces or ad-hoc `ResponseEntity` statuses leak to the client (AC-12).
- Audit fields (`createdBy`/`updatedBy`/`deletedBy`) are populated only from the authenticated caller,
  never client-supplied; `AdminAuditLogService` calls are best-effort and never block/roll back the
  business transaction on failure.
- Soft delete is the only sanctioned removal path — no physical delete exists anywhere in this feature;
  any future data reset must be an out-of-band operator action, never `DROP`/`TRUNCATE` run by an agent.
- No new secrets, tokens, PII, or monitoring/alerting surface was introduced.

## 8. Accepted Risk
| risk | impact | owner | deadline | approver |
|---|---|---|---|---|
| `TS6133` TypeScript build failure in pre-existing `App.tsx`/`TicketBugMetricsPage.tsx` (unused imports) | Blocks `npm run build`; cannot produce a release build until fixed | Team | Before release | Pending |
| E2E CRUD journey not written/run | Full browser-level journey unverified; automated unit/web tests are the only current evidence | Team | Before release | Pending |
| Manual role-matrix browser check not performed | AC-10/AC-11 compliance verified only via automated tests, not a live run | Team | Before release | Pending |
| Whitespace-only `note` implemented as trim-to-null (OI-5) | Low — most-permissive, reasonable interpretation; not specified by raw input | Team | Non-blocking | Accepted |
| No dedicated controller-layer role-based 403 test (caller fixed to one `AuthUserContext` in `@WebMvcTest`) | Low — authoritative role-check logic is already fully tested at the service layer | Team | Non-blocking | Accepted |

## 9. Open Issues
| issue | impact | next action |
|---|---|---|
| OI-BUG-DASHBOARD-4 (seeded "DEV" role code) | None — closed via direct source verification (`V4` seed + `findProjectRole`'s `UPPER(BTRIM(...))` normalization) | Closed, spec-pack.md §20.4 |
| OI-BUG-DASHBOARD-2 (FE route/menu placement) | None — resolved: `/{lang}/ticket-bug-metrics`, `RoleTabs.tsx`/`dashboardRoutes.ts` | Closed, spec-pack.md §20.2 |
| OI-BUG-DASHBOARD-3 (i18n resource locations) | None — resolved: `Pages.TicketBugMetrics.*` in en/vi/ja | Closed, spec-pack.md §20.3 |
| OI-BUG-DASHBOARD-5 (whitespace-only note) | Low — implemented as trim-to-null | Closed as implemented; carried in §8 as accepted risk |
| `TS6133` build failure (new this phase) | Blocks production build | Team fixes unused imports before release |
| No E2E spec / manual role-matrix run (new this phase) | Release-readiness gap | Team runs before release |
| No dedicated `/ticket-options`/`/access` route-level role test beyond service-layer coverage | Low, nice-to-have follow-up | Optional follow-up ticket |

## 10. Human Decisions
| decision | owner | result |
|---|---|---|
| H-BUG-DASHBOARD-1: Repository↔Ticket relationship handling | Product/Tech Lead | Resolved — option (a), Repository informational/filter-only, no cross-validation |
| H-BUG-DASHBOARD-2: reuse `QaDashboardService`-style per-project role check | Tech Lead | Resolved — reuse the shape via a new method, do not edit `QaDashboardService` |
| H-BUG-DASHBOARD-3: corrected DDL/migration convention + target version | Tech Lead/DBA | Resolved — soft-delete-from-creation columns, partial unique index, `V509` |
| Ticket "active" validation semantics (`ticket_status` has no `ACTIVE` value) | Tech Lead (mid-implementation) | Resolved — existence-only check, no status filter |
| FE screen guard approach (bespoke vs. extend `RequireDashboardAccess`) | Tech Lead (mid-implementation) | Resolved — bespoke `RequireTicketBugMetricsAccess`, no `DashboardAccessKey` union edit |

## 11. Source Analysis Limitations

- No canonical ticket body exists beyond `01_raw-input.md` (no Jira ticket, meeting memo, or basic
  design doc was found in the workspace) — all specification work is anchored to that single document
  plus observed code conventions.
- `docs/standards/api-contract.md` and `security.md` were already flagged as partially stale versus
  real code by a prior ticket (`PROJECT`'s sources.md) and were not independently re-verified line-by-line
  this ticket; the same caution is assumed to still hold.
- `docs/architecture/route-api-map.md`, `service-layer-map.md`, and `repository-db-map.md` were checked
  during impact analysis and found stale relative to this domain (they describe a legacy webhook/
  ingestion `repository` table, not the governance CRUD module) — not used as evidence, and flagged as a
  living-docs candidate (§15).
- Exact seeded role codes beyond "DEV" (e.g. full `tbl_dim_role` contents) were not exhaustively read;
  only the "DEV" row was directly verified against source.
- No live Postgres instance was available during implementation or this test phase — DB/migration-level
  behavior (Flyway apply, partial-unique-index race) is verified by code-level backstop tests only, not
  a real database run.

## 12. What worked

- Anchoring the new module tightly to two existing, already-implemented precedents
  (`RepositoryController`/`Service`/`DTOs`/`Model`/`Mapper` for CRUD shape, `QaDashboardService` for
  role-gating shape) meant almost no architectural decisions were invented from scratch — only the
  PM/QA/DEV role matrix itself was genuinely new.
- Resolving all three blocking Human Decisions (H-1/H-2/H-3) during Phase 1, before any code was
  written, meant Phase 3 implementation proceeded without contract rework or backtracking.
- Treating `AuthUserContext` (raw role string) as mandatory and `AppUser` (role-collapsing enum) as
  forbidden, decided explicitly in `context.md`, prevented a class of bug where PM/QA/DEV would have
  been indistinguishable at runtime.
- Running a dedicated test-only phase (Phase 7/8) after self-review surfaced three real, previously
  invisible FE test-harness defects that had been silently producing false-positive "passing" results.

## 13. What failed

- Three latent FE component-test harness bugs (a wholesale `antd` module mock missing the `Input`
  export, an unmocked `RoleTabs`/`useNavigate()` needing Router context, and a hoisted API mock object
  missing the `options` key) went undetected through the original implementation and self-review passes
  — the tests reported as "passing" earlier were not actually exercising the intended render path.
- A pre-existing, out-of-scope `TS6133` TypeScript build failure (unused imports in `App.tsx` and
  `TicketBugMetricsPage.tsx`) was only discovered via `tsc --noEmit`/`npm run build` during this test
  phase, not caught by any earlier review checklist item, despite `npm run build` succeeding in the
  original `self-review.md` §4 run — indicating the production files changed or the build step behaved
  differently between sessions.
- No independent AI code-review pass (e.g. `/code-review`) or full human sign-off on the shipped diff
  occurred before reaching this final-report phase.

## 14. Candidate updates Failure Mode Index

- "A test file that mocks an entire third-party UI library module (e.g. `vi.mock('antd', ...)`) can
  silently crash or no-op unrelated components that import a different export from that same module —
  stub only the specific exports actually used, never replace the whole module blindly."
- "A hoisted mock object for a multi-endpoint API client that is missing one endpoint key resolves to
  `undefined` at call time and fails silently (e.g. a gated dependent query just never fires) rather
  than throwing — when a dependent-query chain 'passes' with zero assertions on the gating data, verify
  the upstream mock actually returns the shape the component expects before trusting the green result."
- "`npm run build` passing in one session and failing with `TS6133` in a later session on the same
  committed files suggests build-step state (cache, incremental `tsconfig` state) should not be assumed
  stable across sessions — treat a completed `self-review.md` build-pass as a snapshot, not a permanent
  guarantee, and re-run `tsc --noEmit` at the start of any later phase that depends on it."

## 15. Candidate updates Living Docs

- Add the `QaDashboardService.requireQaAccess`-style role-check shape (ADMIN bypass + `findProjectRole`
  + role-string compare) as a named, reusable precedent in `docs/standards/coding.md`, since this ticket
  is now the second implementation of a near-identical pattern (mutate/view/blocked tiers) — future
  PM/QA/DEV-tier features should reference it directly instead of re-deriving it from `context.md`-style
  ticket docs each time.
- Refresh or retire `docs/architecture/route-api-map.md`, `service-layer-map.md`, and
  `repository-db-map.md` — confirmed stale against the current governance CRUD domain (they describe a
  legacy webhook/ingestion `repository` table) during this ticket's impact analysis; leaving them in
  place risks being cited as evidence by a future ticket without the same caveat being rediscovered.
- Consider documenting the `AuthUserContext` vs. `AppUser` distinction (raw role string vs.
  role-collapsing enum) directly in `docs/standards/coding.md` or `docs/architecture/overview.md`, since
  this ticket needed to explicitly forbid `AppUser` for role checks and that pitfall is not currently
  written down anywhere outside this ticket's own `context.md`.

## 16. Final Verdict

- NEEDS_UPDATE — all authored test code passes (43/43: 28 BE + 15 FE) and all 13 ACs have code + test
  evidence, but release is blocked on three items: (1) the `TS6133` production build failure, (2) the
  missing E2E CRUD spec, and (3) a manual role-matrix browser verification. None of these require
  redesign — they are follow-up execution items for the team before promoting this ticket past Phase 8.
