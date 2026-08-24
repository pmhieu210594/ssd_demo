# Test Results

**Ticket ID**: AI-QUALITY
**Create date**: 2026-08-17 07:43:48
**Author**: BRYCENVN\nvt_dung
**Update date**: 2026-08-18 10:00:00

## 1. Execution Environment

| item | value |
|---|---|
| OS | Windows 10 Pro (win32) |
| Maven | Apache Maven 3.9.16 |
| Java | 21.0.10 (Oracle) |
| Node | v22.22.2 |
| npm | 10.9.7 |
| BE working dir | `EDCAP_BE/` |
| FE working dir | `EDCAP_FE/` |

## 2. Executed Command

| command | result | log evidence | note |
|---|---|---|---|
| `mvn -DskipITs -Dtest=AiQualityServiceTest,AiQualityControllerTest test` | PASS | `Tests run: 26, Failures: 0, Errors: 0` (`AiQualityServiceTest`); `Tests run: 10, Failures: 0, Errors: 0` (`AiQualityControllerTest`); `Tests run: 36, Failures: 0, Errors: 0` total; `BUILD SUCCESS` | 26 = 24 pre-existing + 2 new (TC-1, TC-2); 10 = 9 pre-existing + 1 new (TC-3) |
| `mvn -DskipITs test` (full BE suite) | PASS (as of 2026-08-18 follow-up fix) | `Tests run: 559, Failures: 0, Errors: 0`; `BUILD SUCCESS` | Previously 2 pre-existing `InvalidUseOfMatchers` errors in `TicketBugMetricsServiceTest.search_clampsPageSizeToBounds` and `TicketBugMetricsControllerTest.list_returnsPagedItems` (unrelated to AI-QUALITY, confirmed reproducible in isolation with zero AI-QUALITY code present). Root cause: both tests mixed Mockito matchers (`eq`/`any`) with a raw `null` literal for the `String search` parameter in the same stubbed call — Mockito requires all-or-none matchers per call. Fixed by replacing the raw `null` with `isNull()` in both test files (BUG-DASHBOARD test-code-only fix, no production code changed). See §7. |
| `npx vitest run "src/__ tests __/ai-quality"` | PASS | `Test Files 2 passed (2)`, `Tests 13 passed (13)` | No new FE tests added this phase (§6 of test-plan.md); re-run to confirm no regression |
| `npx vitest run` (full FE suite) | PASS | `Test Files 50 passed (50)`, `Tests 330 passed (330)` | No regressions in any other module |

## 3. Summary of Results

- Backend AI-QUALITY-scoped tests: **36/36 passing** (24 + 9 = 33 pre-existing from Phase 5,
  plus 3 new this phase for AC-AI-QUALITY-13: `search_filtersByTicketExternalKeyOrTitle_andCombinedWithOtherFilters`,
  `search_blankSearchTermIsNormalizedToNull` in `AiQualityServiceTest`; `list_forwardsSearchParam_andCombinedWithOtherFilters`
  in `AiQualityControllerTest`).
- Full backend suite: 559/559 passing (as of 2026-08-18 follow-up). The 2 pre-existing failures
  in the unrelated `TicketBugMetricsServiceTest`/`ControllerTest` module (not caused by this
  ticket) have since been fixed at the user's request — see §7 for the fix detail.
- Frontend AI-QUALITY-scoped tests: **13/13 passing**, unchanged from Phase 5 (no new FE
  test needed this phase — see test-plan.md §5/§6).
- Full frontend suite: 330/330 passing, no regressions.
- All 13 ACs (AC-AI-QUALITY-1 through -13) now have at least one passing automated test.

## 4. List of Passes

| TC ID | test | result | note |
|---|---|---|---|
| AC-1, AC-6, AC-12 | `AiQualityServiceTest.mutateRoles_canCreateUpdateDelete` | PASS | |
| AC-10 | `AiQualityServiceTest.devRole_isViewOnly_forbiddenOnWrite_allowedOnRead` | PASS | |
| AC-11 | `AiQualityServiceTest.noRole_blockedFromEveryEndpointIncludingRead` | PASS | |
| AC-11 | `AiQualityServiceTest.unauthenticatedCaller_blockedFromEveryEndpoint` | PASS | |
| §11 FE-guard fallback | `AiQualityServiceTest.requireAnyAccess_passesForAnyAuthenticatedCaller_regardlessOfProjectRole` | PASS | |
| §11 FE-guard fallback | `AiQualityServiceTest.requireAnyAccess_throwsForUnauthenticatedCaller` | PASS | |
| AC-12 | `AiQualityServiceTest.globalAdmin_getsMutateRegardlessOfPerProjectRole` | PASS | |
| AC-5 | `AiQualityServiceTest.create_acceptsUpperInclusiveBound_100_00` | PASS | |
| AC-5 | `AiQualityServiceTest.create_acceptsLowerInclusiveBound_0_00` | PASS | |
| AC-5 | `AiQualityServiceTest.create_rejectsAboveUpperBound_100_01` | PASS | |
| AC-5 | `AiQualityServiceTest.create_rejectsBelowLowerBound_negative0_01` | PASS | |
| AC-5 | `AiQualityServiceTest.create_rejectsNullRate` | PASS | |
| AC-5 | `AiQualityServiceTest.create_rejectsScaleGreaterThanTwo_50_555` | PASS | |
| AC-2 | `AiQualityServiceTest.create_duplicateActiveTicket_throwsConflict` | PASS | |
| AC-2 | `AiQualityServiceTest.create_duplicateKeyOnInsert_throwsConflict` | PASS | |
| AC-3 | `AiQualityServiceTest.create_repositoryNotBelongingToProject_throwsBusinessRuleException` | PASS | |
| AC-4 | `AiQualityServiceTest.create_ticketNotBelongingToRepository_throwsBusinessRuleException` | PASS | |
| AC-7 | `AiQualityServiceTest.getOrUpdateOrDelete_notFoundOrDeletedRow_throwsNotFound` | PASS | |
| AC-8 | `AiQualityServiceTest.afterSoftDelete_recreatingSameTicket_succeeds_noFalseConflict` | PASS | |
| AC-9 | `AiQualityServiceTest.search_clampsPageSizeToBounds` | PASS | |
| AC-13 | `AiQualityServiceTest.search_filtersByTicketExternalKeyOrTitle_andCombinedWithOtherFilters` (new) | PASS | |
| AC-13 | `AiQualityServiceTest.search_blankSearchTermIsNormalizedToNull` (new) | PASS | |
| AC-9 | `AiQualityControllerTest.list_returnsPagedItems` | PASS | |
| AC-13 | `AiQualityControllerTest.list_forwardsSearchParam_andCombinedWithOtherFilters` (new) | PASS | |
| AC-7 | `AiQualityControllerTest.get_returnsDetail` | PASS | |
| AC-1 | `AiQualityControllerTest.create_returns201` | PASS | |
| AC-6 | `AiQualityControllerTest.update_usesPut` | PASS | |
| AC-7 | `AiQualityControllerTest.softDelete_usesPutDeleteEndpoint_neverHardDelete` | PASS | |
| §6.4 error mapping | `AiQualityControllerTest.exceptions_mapToExpectedEnvelope` | PASS | |
| AC-10/-11/-12 | `AiQualityControllerTest.getAccess_returnsNoContent_whenCallerHasAccess` | PASS | |
| §11 FE-guard fallback | `AiQualityControllerTest.getAccess_withNoProjectId_returnsNoContent_forAnyAuthenticatedCaller` | PASS | |
| AC-10/-11 | `AiQualityControllerTest.getAccess_returnsForbidden_whenCallerHasNoAccess` | PASS | |
| AC-1/-6/-7/-9 | `ai-quality-api.test.ts` (7 cases: list w/ params, list w/o params, get, create, update, soft-delete, access) | PASS | |
| AC-9/-10 | `ai-quality.test.tsx` (6 cases: renders list, QA sees actions, DEV hides actions, create-drawer submit, soft-delete, API-failure message) | PASS | |

## 5. List of Fails

| TC ID | test | cause | action | status |
|---|---|---|---|---|
| — | — | — | — | — |

No AI-QUALITY test failed. (The 2 failing tests in the full-suite run belong to the
unrelated `TicketBugMetricsServiceTest`/`ControllerTest` classes — see §7, not listed here
as an AI-QUALITY test failure.)

## 6. Bugs Fixed

| bug | fix | evidence |
|---|---|---|
| (Carried forward from Phase 5, not re-fixed this phase) `/access` login-kickout for non-ADMIN callers opening `/ai-quality` with no `projectId` yet | `AiQualityController.access` branches on `projectId != null` → `requireAccess`, else → `requireAnyAccess`, mirroring `TicketBugMetricsController` | `AiQualityServiceTest.requireAnyAccess_*`, `AiQualityControllerTest.getAccess_withNoProjectId_returnsNoContent_forAnyAuthenticatedCaller` (all still passing, re-verified this phase) |
| (2026-08-18 follow-up, user-requested, out-of-scope-for-AI-QUALITY but fixed at user request) `TicketBugMetricsServiceTest.search_clampsPageSizeToBounds` / `TicketBugMetricsControllerTest.list_returnsPagedItems` — `InvalidUseOfMatchers` in the full BE suite | Both tests mixed Mockito matchers (`eq`/`any`) with a raw `null` literal for the `String search` parameter within the same stubbed call (`repository.findPage(...)`, `repository.count(...)`, `service.search(...)`) — Mockito requires all-or-none matcher usage per call. Fixed by importing `org.mockito.ArgumentMatchers.isNull` and replacing the raw `null` with `isNull()` in both test files. No production code changed — test-code-only fix in the unrelated BUG-DASHBOARD module. | `TicketBugMetricsServiceTest`/`TicketBugMetricsControllerTest` targeted run: `Tests run: 29, Failures: 0, Errors: 0`, `BUILD SUCCESS`; full BE suite: `Tests run: 559, Failures: 0, Errors: 0`, `BUILD SUCCESS` |

## 7. Not yet fixed / Pending

| item | reason | risk | owner |
|---|---|---|---|
| — | — | — | — |

None remaining. The previously-listed `TicketBugMetricsServiceTest`/`ControllerTest`
`InvalidUseOfMatchers` errors (pre-existing, unrelated to AI-QUALITY's own code) were fixed on
2026-08-18 at the user's explicit request — see §6 above.

## 8. Test cannot be executed and reason

| TC ID | reason | risk | alternative evidence |
|---|---|---|---|
| — | — | — | — |

None outstanding. The manual browser RBAC/UI smoke test (per role: ADMIN/PM/QA/DEV/other),
previously blocked on no interactive browser being available in this execution environment,
was performed directly by the user in a real browser on 2026-08-18 — all role tiers confirmed
working (button visibility, drawer cascading, 409 toast rendering), no issues found. The
DB-level partial unique index (`uq_ai_quality_active_ticket`) under a real concurrent-insert
race remains reviewed-not-executed (no live DB/integration-test harness exists in this
codebase, `impl-plan.md` §10); this is a low-risk, accepted gap covered by the app-level
`existsActiveByTicketId` check plus the `create_duplicateKeyOnInsert_throwsConflict` mapping
test, not a blocker.

## 9. Remaining risk

- (Resolved 2026-08-18) The manual RBAC/UI smoke test gap carried forward from Phase 5 is now
  closed — the user performed it in a real browser across all role tiers and confirmed
  everything works, no issues found.
- (Resolved 2026-08-18) The two pre-existing `TicketBugMetricsServiceTest`/`ControllerTest`
  failures were an existing quality issue in an unrelated module (BUG-DASHBOARD); they never
  blocked or reflected on AI-QUALITY's own release readiness, but did mean the full backend
  suite was not 100% green. Fixed at the user's request (§6) — the full backend suite is now
  559/559 green.
- No remaining risk items for this ticket.

## 10. Final Test Verdict

**PASS** — every AC-AI-QUALITY-1..13 has at least one passing automated test; the targeted
AI-QUALITY backend (36/36) and frontend (13/13) suites are fully green, and both full
regression suites are fully green (BE 559/559, FE 330/330). The manual browser RBAC/UI smoke
test was performed by the user on 2026-08-18 across all role tiers with no issues found,
closing the one item that previously scoped this verdict to "automated tests only." This
ticket's test verdict is now unconditionally PASS.
