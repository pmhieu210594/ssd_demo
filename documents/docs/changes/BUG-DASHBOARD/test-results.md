# Test Results

**Ticket ID**: BUG-DASHBOARD
**Create date**: 2026-08-06 07:45:13
**Author**: Claude
**Update date**: 2026-08-07 09:30:00

## 1. Execution Environment

| item | value |
|---|---|
| BE | Java 21, Maven, JUnit 5 / Mockito, no live Postgres (unit + `@WebMvcTest`-style tests only) |
| FE | Node/Vite, Vitest 3.2.6, jsdom environment, no dev server |
| OS | Windows 10, Git Bash shell |
| DB | Not available this session — DB/migration-level and E2E verification not executed (see §8) |

## 2. Executed Command

| command | result | log/evidence | note |
|---|---|---|---|
| `mvn -Dtest=TicketBugMetricsServiceTest,TicketBugMetricsControllerTest test` (`EDCAP_BE/`) | SUCCESS — 28/28 tests passed (19 service + 9 controller) | `Tests run: 28, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS` | Includes 6 new tests added this round (3 service, 3 controller) |
| `npx vitest run "src/__ tests __/ticket-bug-metrics"` (`EDCAP_FE/`) | SUCCESS — 15/15 tests passed (9 API + 6 component) | `Test Files 2 passed (2)` / `Tests 15 passed (15)` | Includes 1 new test (`options` API helper) + a harness repair that fixed 3 previously-broken component tests |
| `npx tsc --noEmit` (`EDCAP_FE/`) | FAIL | `TS6133` unused-symbol errors in `src/App.tsx` and `src/pages/ticket-bug-metrics/TicketBugMetricsPage.tsx` | Pre-existing, untouched-by-this-round production files; see §5/§8 |
| `npm run build` (`EDCAP_FE/`) | FAIL (same root cause as `tsc --noEmit`, since `build` runs `tsc && vite build`) | Same `TS6133` errors | Not fixed — out of scope for this test-only phase (plan explicitly excludes production code changes) |

## 3. Summary of Results

All BE and FE test suites for Ticket Bug Metrics pass (28 BE + 15 FE = 43 tests). Six new BE tests close
real gaps (untested `/access`/`/ticket-options`/`/options` routes, untested repository not-found/
cross-project paths, untested pagination clamping). One new FE test closes the `/options` API-helper
gap. Additionally, three latent defects in the existing FE component test file were found and fixed —
these were previously silently non-functional (see §6). Separately, a pre-existing, out-of-scope
TypeScript build failure was discovered in untouched production files and is reported, not fixed (§5).

## 4. List of Passes

| test | result | note |
|---|---|---|
| `TicketBugMetricsServiceTest` (19 tests, incl. 3 new) | PASS | Role matrix, validation, conflict, BR-12, not-found paths, pagination clamping |
| `TicketBugMetricsControllerTest` (9 tests, incl. 3 new) | PASS | Routes incl. `/access`/`/ticket-options`/`/options`, envelope mapping |
| `ticket-bug-metrics-api.test.ts` (9 tests, incl. 1 new) | PASS | All API-helper query/method/body construction incl. `options` |
| `ticket-bug-metrics.test.tsx` (6 tests) | PASS | List render, role-based hiding, create-flow, delete, error message — all now genuinely exercising the real render path (see §6) |

## 5. List of Fails

| test | cause | action | status |
|---|---|---|---|
| `npx tsc --noEmit` / `npm run build` (`EDCAP_FE/`) | `TS6133`: `RequireTicketBugMetricsAccess` unused in `App.tsx`; `CButton`, `filterOpen`, `setFilterOpen` unused in `TicketBugMetricsPage.tsx` | Not fixed — both files are pre-existing, untouched by this test-only round; fixing them is a production-code change outside this plan's scope | Open — escalated to team, see §8 |

## 6. Bugs Fixed

| bug | fix | evidence |
|---|---|---|
| `ticket-bug-metrics.test.tsx`'s `antd` mock replaced the whole module without an `Input` export, crashing at import time via `field-info.tsx`'s `Input.OTP` reference | Added a minimal `Input` stub (with `.OTP`) to the existing mock, without spreading real `antd` (which pulled in unrelated Router-dependent rendering) | File no longer throws `[vitest] There was an error when mocking a module` at collection time |
| `TicketBugMetricsPage.tsx` renders `<RoleTabs active="ticket-bug-metrics" />`, which calls `useNavigate()`; the test file had no mock for it and no Router wrapper | Added `vi.mock("@/components/dashboard/RoleTabs", () => ({ RoleTabs: () => null }))`, matching the file's existing convention of mocking every other UI dependency | `useNavigate() may be used only in the context of a <Router> component` error resolved |
| The hoisted `ticketBugMetricsApiMocks` object never defined an `options` mock (only `ticketOptions`); `TicketBugMetricsPage`'s `optionsQuery` calls `endpoints.ticketBugMetrics.options(...)`, which was `undefined`, silently failing and preventing the project/repository filter auto-select effect from ever firing — so `listQuery` (gated on both being set) never ran | Added `options: vi.fn()` to the hoisted mock and a `mockResolvedValue` in `beforeEach` returning `{ projects, repositories, tickets }` | `renders the list`, `hides create/edit/delete affordances for DEV`, and `soft deletes a row` now find `"PROJ-1"` as expected instead of timing out |

## 7. Not yet fixed / Pending

- `npx tsc --noEmit` / `npm run build` failure in `App.tsx`/`TicketBugMetricsPage.tsx` (§5) — production-code fix, not this phase's scope.
- E2E CRUD journey and manual role-matrix browser verification (carried from `self-review.md §8`) — no dev server/DB this session.
- OI-BUG-DASHBOARD-5 (whitespace-only `note`) — already resolved as trim-to-null; not re-opened.

## 8. Test cannot be executed and reason

| test/command | reason | risk | alternative evidence |
|---|---|---|---|
| Playwright E2E (`e2e_tests/tests/ticket-bug-metrics/*.spec.ts`) | Directory does not exist; no dev server/DB available in this session to write against | Full browser CRUD journey unverified | BE web-slice tests + FE component tests cover the same contract at each layer independently |
| Manual role-matrix browser run (ADMIN/PM/QA/DEV/other) | No live dev server/DB this session | AC-10/AC-11 compliance verified only via automated tests | `devRole_canViewButNotMutate`, `otherRoles_blockedFromEveryAction`, `unauthenticatedCaller_blockedFromEveryAction` (BE); `hides create/edit/delete affordances for DEV` (FE) |
| Flyway migration apply / partial-unique-index race (`V509__ticket_bug_metrics.sql`) | No dev DB running this session | Low — migration already applied successfully during original implementation (`self-review.md §4`) | `create_duplicateActiveTicket_throwsConflict` unit-tests the code-level backstop the index reinforces |
| `npm run build` full production build | Blocked by pre-existing `TS6133` errors (§5), unrelated to this phase's changes | Cannot produce a release build until fixed | `npx vitest run` and the BE `mvn test` both succeeded independently of the build step |

## 9. Remaining risk

- Release should not proceed until: (a) the `TS6133` build failure is fixed (production-code change, owned by the team, not this phase), (b) an E2E spec is written and run, and (c) a manual role-matrix browser check is performed — all three are pre-existing, already-logged risks from `self-review.md §8`, with one new build-blocking item added this round.
- No new BE or FE test-coverage risk was introduced by this round; all new tests pass and close previously real gaps.

## 10. Final Test Verdict

PARTIAL — all test code for this phase (28 BE + 15 FE = 43 tests) passes, and the AC↔test matrix in
`test-plan.md §2` shows no AC without at least one test type. Release readiness is still blocked by the
pre-existing, out-of-scope `TS6133` build failure and the still-missing E2E/manual verification —
neither of which this test-only phase was scoped to resolve.
