# Test Plan

**Ticket ID**: BUG-DASHBOARD
**Create date**: 2026-08-06 07:45:13
**Author**: Claude
**Update date**: 2026-08-07 09:30:00

## 1. Purpose

Decide, per AC (spec-pack.md §7), which test type already guarantees it, add the minimum test code
needed to close real gaps found in the existing suite, and record execution evidence in
`test-results.md`. This phase does not re-implement the feature; it only adds/repairs test code
against the already-shipped `TicketBugMetricsService`/`TicketBugMetricsController`/
`TicketBugMetricsPage` (per `self-review.md`).

## 2. AC Matrix ↔ Test Type

| AC ID | FE UT | BE UT | API IT (WebMvc) | Contract Test | DB/Migration | E2E | Black-box |
|---|---|---|---|---|---|---|---|
| AC-1 (list, non-deleted, filterable) | ✅ (`renders the list`) | ✅ (`ticketBugMetricsWhereClause` implicit via mapper, not unit-tested directly) | ✅ (`list_returnsPagedItems`) | n/a | Gap (index-backed exclusion not migration-tested) | Gap | — |
| AC-13 (Repository↔Ticket independence) | — | ✅ (`create_repositoryUnrelatedToTicket_stillAccepted_perBR12`) | — | n/a | — | Gap | — |
| AC-2 (empty state) | ✅ (list renders empty table via shared `CServerTable`, not separately asserted) | — | — | n/a | — | Gap | — |
| AC-3 (create happy path) | ✅ (`opens create drawer and submits normalized payload`) | ✅ (`mutateRoles_canCreateUpdateDelete`) | ✅ (`create_returns201WithoutVersionField`) | n/a | — | Gap | — |
| AC-4 (duplicate 409) | — | ✅ (`create_duplicateActiveTicket_throwsConflict`) | ✅ (`exceptions_mapToExpectedEnvelope`) | n/a | Gap (partial-unique-index race backstop untested) | Gap | — |
| AC-5 (bug count validation) | — | ✅ (`create_rejectsNegativeOrMissingBugCounts`, `create_acceptsZeroBugCounts`) | ✅ (`exceptions_mapToExpectedEnvelope`) | n/a | — | Gap | — |
| AC-6 (note length) | — | ✅ (`create_rejectsNoteOver500Characters`, `create_accepts500CharacterNote`) | — | n/a | — | Gap | — |
| AC-7 (update refresh) | ✅ (`opens create drawer...` covers create; no dedicated edit-flow FE test — accepted gap, §6) | ✅ (`mutateRoles_canCreateUpdateDelete`) | ✅ (`update_usesPut`) | n/a | — | Gap | — |
| AC-8 (soft delete) | ✅ (`soft deletes a row`) | ✅ (`mutateRoles_canCreateUpdateDelete`) | ✅ (`softDelete_usesPutDeleteEndpoint_neverHardDelete`) | n/a | — | Gap | — |
| AC-9 (404 on missing/deleted) | — | ✅ (`getOrUpdateOrDelete_notFoundOrDeletedRow_throwsNotFound`) | ✅ (`exceptions_mapToExpectedEnvelope`) | n/a | — | Gap | — |
| AC-10 (DEV view-only) | ✅ (`hides create/edit/delete affordances for DEV`) | ✅ (`devRole_canViewButNotMutate`) | Gap (controller fixes caller to ADMIN only) | n/a | — | Gap | — |
| AC-11 (blocked roles) | — | ✅ (`otherRoles_blockedFromEveryAction`, `unauthenticatedCaller_blockedFromEveryAction`) | Gap (same as AC-10) | n/a | — | Gap | — |
| AC-12 (error envelope) | ✅ (`shows error message when API fails`) | — | ✅ (`exceptions_mapToExpectedEnvelope`) | n/a | — | Gap | — |

Contract Test is marked n/a for every row: this repo has no separate contract-test tooling (e.g. Pact);
the `@WebMvcTest`-style controller tests are the closest equivalent and are already counted under API IT.

## 3. Priority

| test item | priority | reason |
|---|---|---|
| `/access`, `/ticket-options`, `/options` controller routes | High | Zero prior coverage; these routes gate the FE screen guard and cascading filter — a silent regression here breaks the whole page, not just one field |
| Repository not-found / cross-project mismatch in `create` | High | Directly backs BR-12/AC-13's "independently validated" claim; previously only the project-not-found path was asserted |
| Pagination clamping (`normalizePageSize`) | Medium | Low product risk, but silent misconfiguration (e.g. `size=0`) is easy to introduce and easy to verify cheaply |
| FE `/options` API-helper case | Medium | Same tier as the already-tested `ticket-options`/`access` helpers; was the one missing case |
| FE test-file repair (antd/Router/mock gaps) | High | Blocking — without it, 6 of 14 previously-claimed-passing FE tests silently fail/never exercised the real render path |

## 4. Reuse Existing Test

| existing test | path | covers | gap |
|---|---|---|---|
| `TicketBugMetricsServiceTest` | `EDCAP_BE/src/test/UnitTest/java/.../ticketbugmetrics/TicketBugMetricsServiceTest.java` | Full role matrix (PM/QA/ADMIN mutate, DEV view-only, other/unauthenticated blocked), bug-count/note validation incl. boundaries, duplicate-ticket conflict, BR-12 independence, project-not-found, get/update/delete-not-found | Repository not-found/cross-project, pagination clamping (closed this round) |
| `TicketBugMetricsControllerTest` | `EDCAP_BE/src/test/UnitTest/java/.../web/rest/TicketBugMetricsControllerTest.java` | list/detail/create/update/soft-delete routes, no-`version`-field assertion, soft-delete-only assertion, exception→envelope mapping (404/403/409/400) | `/access`, `/ticket-options`, `/options` routes (closed this round); role-based 403 at controller layer still not covered (caller fixed to ADMIN) — logged as accepted gap, §6 |
| `ticket-bug-metrics.test.tsx` | `EDCAP_FE/src/__ tests __/ticket-bug-metrics/ticket-bug-metrics.test.tsx` | List render, role-based create-button hiding (QA vs DEV), create-flow submission with note-trim, delete-mock invocation, list-fetch-error message | Test file had 3 latent, previously-undetected failures (missing `options` API mock causing the filter auto-select to never fire, un-mocked `RoleTabs` needing Router context, an antd module mock missing `Input`) — repaired this round, not a coverage gap but a correctness gap in the harness itself |
| `ticket-bug-metrics-api.test.ts` | `EDCAP_FE/src/__ tests __/ticket-bug-metrics/ticket-bug-metrics-api.test.ts` | list/get/create/update/soft-delete query-string & method construction, `ticket-options`/`access` helpers | `options` helper (closed this round) |

## 5. Additional Test This Time

| test | type | target | related AC |
|---|---|---|---|
| `getAccess_returnsNoContent_whenCallerHasAccess` | BE API IT | `TicketBugMetricsController.access` | AC-10/AC-11 (route existence for the FE guard) |
| `getTicketOptions_returnsList` | BE API IT | `TicketBugMetricsController.ticketOptions` | AC-1 (cascading filter data source) |
| `getOptions_returnsProjectsRepositoriesTickets` | BE API IT | `TicketBugMetricsController.options` | AC-1 (cascading filter data source) |
| `create_repositoryNotFoundOrInactive_throwsNotFound` | BE UT | `TicketBugMetricsService.ensureRepositoryActive` | AC-13/BR-12 (repository independently validated) |
| `create_repositoryBelongsToDifferentProject_throwsNotFound` | BE UT | `TicketBugMetricsService.ensureRepositoryActive` | AC-13/BR-12 |
| `search_clampsPageSizeToBounds` | BE UT | `TicketBugMetricsService.normalizePageSize`/page floor | AC-1 (pagination §6.5) |
| `fetches options scoped by projectId and repositoryId` | FE API UT | `endpoints.ticketBugMetrics.options` | AC-1 (cascading filter data source) |
| Test-harness repair: `options` API mock, `RoleTabs` mock, `Input` antd stub, `access` mock resolution | FE UT (harness fix, no new assertions) | `ticket-bug-metrics.test.tsx` | AC-1, AC-2, AC-8, AC-10 (these were silently unverified before the repair) |

### E2E Step-by-step Scenarios

| scenario | precondition | steps | expected | related AC |
|---|---|---|---|---|
| — | — | — | — | Not written this round — see §6 for reason |

## 6. Areas intentionally left untested this time

| area | reason | risk |
|---|---|---|
| Full E2E CRUD journey (`e2e_tests/tests/ticket-bug-metrics/*.spec.ts`) | No dev server/DB available in this environment (carried from `self-review.md §8`); writing a spec that can never be executed here would give false confidence | Medium — no browser-level verification of the full journey before release; owner: Team, before release |
| Manual role-matrix browser run (ADMIN/PM/QA/DEV/other) | Same reason — no live dev server/DB this session | Medium — same as above |
| Controller-layer role-based 403 (AC-10/AC-11 at the `@WebMvcTest` level) | `TicketBugMetricsControllerTest` fixes the caller to a single `AuthUserContext` via a custom argument resolver; the full role matrix is already exercised at the service layer (`TicketBugMetricsServiceTest`), which is where the actual authorization decision is made. Re-parameterizing the controller test would duplicate, not add, coverage | Low — service-layer test is the authoritative check; controller only forwards the caller |
| FE edit-submission-flow test (as opposed to create-submission, which is tested) and Popconfirm click-through delete (as opposed to the direct-mock-call delete test) | Same underlying `CDrawerForm`/`endpoints.ticketBugMetrics.update`/`softDelete` contract is already exercised by the create-flow and API-helper tests; the create-vs-edit and mock-call-vs-click-through differences are UI wiring, not business logic | Low — incremental risk given the FE API + BE contract are both directly tested |
| DB/migration-level partial-unique-index race test | Requires a real Postgres instance; `.claude/rules` restrict destructive/live-DB actions in this session, and no dev DB is running here | Low — the conflict check (`existsActiveByTicketId` → 409) is unit-tested; the index is a backstop against a race the unit test can't simulate without a real DB |
| DB/migration Flyway apply verification | No dev DB running this session (same constraint as above) | Low — migration was already applied successfully during original implementation per `self-review.md §4` |

## 7. Data testing principles

- No hard-coded UUIDs reused across unrelated test cases beyond the existing fixture constants already
  established in each test file (mirrors `review-checklist.md §2.3`).
- Fixtures use realistic column defaults: `internalBugCount`/`customerBugCount` default to non-negative
  integers, `note` defaults to `null` unless a test is specifically exercising note content.
- Boundary values only, no fuzzing: bug counts at `{-1, null, 0}`, note length at `{500, 501}` chars,
  page size at `{<=0, within range, >100}`, page at `{negative, 0}` — matches the governance-CRUD scope
  of this feature; property-based testing is not warranted here.
- Mockito mocks are built per port interface (`TicketBugMetricsRepositoryPort`, `ProjectRepositoryPort`,
  `RepositoryRepositoryPort`, `TicketLookupPort`, `QaDashboardRepositoryPort`), never domain models —
  per `.claude/rules/40-testing.md` and `docs/standards/testing.md`.
- FE fixtures avoid asserting on real i18n strings; the mocked `t()` returns the raw key (or
  `defaultValue`), so assertions target keys, not locale-dependent text.

## 8. Execution command

| command | purpose |
|---|---|
| `mvn -Dtest=TicketBugMetricsServiceTest,TicketBugMetricsControllerTest test` (in `EDCAP_BE/`) | Run all BE unit + web-slice tests for this feature, incl. the new gap-closing tests |
| `npx vitest run "src/__ tests __/ticket-bug-metrics"` (in `EDCAP_FE/`) | Run all FE component + API-helper tests for this feature, incl. the new gap-closing test and the harness repair |
| `npx tsc --noEmit` (in `EDCAP_FE/`) | Confirm the FE still typechecks (independent of this ticket's test files) |

## 9. Stop Condition

Any new test failing after one root-cause-driven fix attempt escalates to human review rather than being
loosened, skipped, or its assertion weakened to force a pass. A pre-existing failure discovered while
adding new tests (as happened with the FE component test's harness bugs, §4/§5) is fixed at the harness
level only if the root cause is a test-file defect; it is never "fixed" by asserting on the incorrect
behavior. Discovering an FE build/typecheck defect that requires touching production code (see
`test-results.md §5`) is out of scope for this test-only phase and is escalated rather than silently
patched.

## 10. Required Human Decision

None new. Carried forward, unchanged from `self-review.md §8`/`spec-pack.md §18`:
- OI-BUG-DASHBOARD-5 (whitespace-only `note` handling) — non-blocking, already resolved as trim-to-null.
- E2E suite and manual role-matrix browser run — required before release, not before this phase closes.

One new, non-blocking finding surfaced during this phase (see `test-results.md §5`): `npx tsc --noEmit`
/ `npm run build` currently fail on pre-existing, untouched production files (`App.tsx`,
`TicketBugMetricsPage.tsx`) due to unused imports/state (`TS6133`). This is a production-code defect,
not a test defect, and is out of scope for this test-plan phase to fix — flagged for the team to address
before release.
