# Test Results

**Ticket ID**: SDD-LEAD-TIME
**Create date**: 2026-08-21
**Author**: nvt_dung
**Update date**: 2026-08-21

> Nguồn tham chiếu: `docs/changes/SDD-LEAD-TIME/test-plan.md`, `self-review.md` §3/§5, `review-checklist.md`.

## 1. Execution Environment

| item | value |
|---|---|
| BE build tool | Maven (offline mode, `mvn -o test`), local machine, no CI run this session |
| BE JDK / framework | Java 21, Spring Boot 3.4.1, JUnit 5 / Mockito |
| DB | None — no live Postgres instance used; all BE tests are unit-level (mocked ports / fake in-memory adapters) |
| FE tooling | Node/npm local run — Vitest (`npm run test:unit`), TypeScript compiler (`npx tsc --noEmit`) |
| FE build | Vite bundle (`npm run build`) not run standalone this session |
| E2E / browser | Not run — no live dev server (BE+FE+DB) available this session |
| Branch / scope | Implementation from Phase 4 (`impl-plan.md`), reviewed in Phase 5 (`self-review.md`); this file consolidates those results for Phase 6 |

## 2. Executed Command

| command | result | log/evidence | note |
|---|---|---|---|
| `mvn -o test` (EDCAP_BE/) | PASS | `Tests run: 637, Failures: 0, Errors: 0, Skipped: 0` — `self-review.md` §3.1 | Offline mode; equivalent to the test portion of `mvn clean verify` |
| `npx tsc --noEmit` (EDCAP_FE/) | PASS | No output — 0 type errors — `self-review.md` §3.2 | Project has no dedicated `typecheck` script; this is the compile-check portion of `npm run build` |
| `npm run test:unit` (EDCAP_FE/) | PASS | `Test Files 53 passed (53)`, `Tests 348 passed (348)` — `self-review.md` §3.4 | Includes new `leadTime.test.ts` (5 tests) and updated `TicketDetailDrawer.test.tsx` (+1 test) |
| `npx vitest run "src/__ tests __/pm-dashboard/leadTime.test.ts"` | PASS | `5 tests`, `1 file passed` — `self-review.md` §3.4 | Targeted re-run during implementation, superseded by the full suite run above |
| `npx vitest run "src/__ tests __/pm-dashboard/TicketDetailDrawer.test.tsx"` | PASS | `2 tests`, `1 file passed` — `self-review.md` §3.4 | Targeted re-run during implementation, superseded by the full suite run above |
| `npm run build` (EDCAP_FE/) | NOT RUN | — | Compile-check already covered by `tsc --noEmit`; see §8 |
| `mvn flyway:migrate` (V513) | NOT RUN | — | DB migration requires explicit user/DBA confirmation per `.claude/rules/00-safety.md §3`; not run in this session |
| `npx playwright test` (EDCAP_FE/) | NOT RUN | — | No live dev server this session; see §8 |

## 3. Summary of Results

All automated BE and FE unit/component tests pass (637 BE + 348 FE = 985 tests, 0 failures). Every
AC-SDD-LEAD-TIME-1..12 has at least one passing automated test per the coverage matrix in
`test-plan.md` §1/§3. No DB-level integration test (real Postgres) and no E2E/manual browser
verification were executed in this session — both are explicitly tracked as intentional gaps (see
§8), not silent omissions.

## 4. List of Passes

| TC ID | test | result | note |
|---|---|---|---|
| TC-SDD-LEAD-TIME-1 | `HeaderDateNormalizerTest.java` (7 cases: full datetime, bare date, null, empty, whitespace, non-date text, full-width digit) | PASS | AC-5, AC-6 |
| TC-SDD-LEAD-TIME-2 | `ArtifactScannerServiceTest.java` (24 cases, fake in-memory `ArtifactScannerPersistencePort`) | PASS | AC-1, AC-2, AC-3, AC-4, AC-6, AC-8, AC-12 |
| TC-SDD-LEAD-TIME-3 | `ArtifactScannerJdbcAdapterTest.java` | PASS | AC-1, AC-2 (adapter-level, mocked JDBC template — not a real DB round-trip) |
| TC-SDD-LEAD-TIME-4 | `PmDashboardServiceTest.java` | PASS | AC-9, AC-10 |
| TC-SDD-LEAD-TIME-5 | `PmDashboardControllerTest.java` | PASS | AC-9, AC-10 |
| TC-SDD-LEAD-TIME-6 | `PmDashboardDtosTest.java` | PASS | AC-9, AC-10 |
| TC-SDD-LEAD-TIME-7 | `leadTime.test.ts` (5 cases: valid pair, `startedAt` null, `completedAt` null, `completedAt < startedAt`, unparseable input) | PASS | AC-9, AC-11 |
| TC-SDD-LEAD-TIME-8 | `TicketDetailDrawer.test.tsx` (new case: renders created time / updated time / duration with correct labels, format, and i18n keys) | PASS | AC-7, AC-9 |
| TC-SDD-LEAD-TIME-9 | `PMDashboardPage.test.tsx` (fixture updated with `startedAt`/`completedAt`, existing assertions unaffected) | PASS | Regression — AC-10 |
| TC-SDD-LEAD-TIME-10 | `utils.test.ts` (`makeRow()` fixture updated with `startedAt: null, completedAt: null`) | PASS | Regression — AC-10 |
| TC-SDD-LEAD-TIME-11 | Full BE regression suite (637 tests, no assertion changed for existing `started_at`-based ordering/filtering behavior) | PASS | AC-10 |

## 5. List of Fails

| TC ID | test | cause | action | status |
|---|---|---|---|---|
| — | (none) | — | — | — |

## 6. Bugs Fixed

| bug | fix | evidence |
|---|---|---|
| (none found during Phase 6 consolidation) | — | — |

## 7. Not yet fixed / Pending

- DB-level integration test (real Postgres) for `updateTicketStartedAt`/`updateTicketCompletedAt`
  and for the new `started_at`/`completed_at` columns in `PmDashboardJdbcAdapter`'s SQL — see §8.
- 2 manual/E2E scenarios from `test-plan.md` §5 and `self-review.md` §3.5 — see §8.
- Migration `V513__add_completed_at_to_tbl_dim_ticket.sql` not yet run on any DB — requires
  explicit user/DBA confirmation per `.claude/rules/00-safety.md §3` before deploy.

## 8. Test cannot be executed and reason

| TC ID | reason | risk | alternative evidence |
|---|---|---|---|
| TC-SDD-LEAD-TIME-DB-IT | No Testcontainers/DB test harness exists anywhere in this repo — the `testcontainers`/`postgresql` Maven dependencies are declared in `pom.xml` but unused (no `@Container`/`@Testcontainers` usage found repo-wide); `ArtifactScannerPersistenceIntegrationTest.java` only asserts on file/migration text content via `Files.readString`, it does not connect to a database. Building this harness from scratch is a first-of-its-kind, out-of-scope change for this additive ticket — confirmed with user during Phase 6 planning | Medium — SQL correctness for the 2 new UPDATE statements and the 2 new SELECT columns (in both `findDetail` and `baseTicketQuery()`) is verified only via fake-port/mocked unit tests and manual diff review, not a real Postgres round-trip | `ArtifactScannerServiceTest` (fake port captures exact call args), `ArtifactScannerJdbcAdapterTest`, `PmDashboardServiceTest`/`PmDashboardControllerTest`/`PmDashboardDtosTest`, manual SQL review recorded in `review-checklist.md` RC-22/RC-23 |
| TC-SDD-LEAD-TIME-E2E-1 | No live dev server (BE+FE+DB) available this session to run Playwright against the "both files present" scenario | Low — FE render logic already covered by `TicketDetailDrawer.test.tsx` | `TicketDetailDrawer.test.tsx`, `self-review.md` §3.5 row 1 |
| TC-SDD-LEAD-TIME-E2E-2 | Same as above, for the "missing file(s) → `-` fallback" scenario | Low — same reasoning | `TicketDetailDrawer.test.tsx`, `self-review.md` §3.5 rows 2-3 |
| TC-SDD-LEAD-TIME-BUILD | `npm run build` (full Vite bundle) not run standalone | Low — `tsc --noEmit` already confirms 0 type errors; bundling failures independent of type-safety are unlikely | `npx tsc --noEmit` result (§2) |

## 9. Remaining risk

- **DB-level SQL correctness** (see TC-SDD-LEAD-TIME-DB-IT above) is the primary remaining risk —
  accepted as a pre-existing infrastructure gap in this repo, not a defect introduced by this
  ticket. Recommended follow-up (outside this ticket's scope): introduce a shared
  Testcontainers-based integration test base class, then add DB-level tests for this and future
  JDBC adapter changes.
- **Manual/E2E visual confirmation** not yet performed — recommended before merge if a dev
  environment becomes available, per `impl-plan.md` §8.2 and `self-review.md` §3.5.
- **Migration `V513` not yet executed on any environment** — must run (with explicit user/DBA
  approval) before or alongside BE/FE deploy, per `impl-plan.md` §13/`review-checklist.md` RC-56.
- All other risks identified in `impl-plan.md` §6 (SQL duplication between `findDetail`/
  `baseTicketQuery()`, compact-constructor defaults) are mitigated and covered by passing automated
  tests (TC-SDD-LEAD-TIME-4/5/6/9).

## 10. Final Test Verdict

- **PARTIAL** — Automated BE unit test suite (637/637) and FE unit/component test suite (348/348)
  are a full **PASS**, covering all 12 acceptance criteria (AC-SDD-LEAD-TIME-1..12) per the matrix
  in `test-plan.md` §1. Verdict is **PARTIAL** rather than PASS only because DB-level integration
  verification and manual/E2E browser verification remain not-executed, by explicit, documented
  decision (§8) — not because of any failing test.
