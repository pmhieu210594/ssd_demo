# Test Plan

**Ticket ID**: AI-QUALITY
**Create date**: 2026-08-17 07:43:48
**Author**: BRYCENVN\nvt_dung
**Update date**: 2026-08-18 10:00:00

## 1. Purpose

Confirm every acceptance criterion in `spec-pack.md` §7 (AC-AI-QUALITY-1 through -13) is
covered by an executable, currently-passing test, identify the one real coverage gap left
over from Phase 5 implementation (AC-13's `search` filter), close it with the minimum
necessary test code, and record real execution results in `test-results.md`. No production
code is changed in this phase — implementation was completed and self-reviewed in Phase 5
(`self-review.md`).

## 2. AC Matrix ↔ Test Type

| AC ID | FE UT | BE UT | API IT | Contract Test | DB/Migration | E2E | Black-box |
|---|---|---|---|---|---|---|---|
| AC-AI-QUALITY-1 | — | Yes | Yes | — | — | — | — |
| AC-AI-QUALITY-2 | — | Yes | — | — | — | — | — |
| AC-AI-QUALITY-3 | — | Yes | — | — | — | — | — |
| AC-AI-QUALITY-4 | — | Yes | — | — | — | — | — |
| AC-AI-QUALITY-5 | — | Yes | — | — | — | — | — |
| AC-AI-QUALITY-6 | Yes | Yes | Yes | — | — | — | — |
| AC-AI-QUALITY-7 | Yes | Yes | Yes | — | — | — | — |
| AC-AI-QUALITY-8 | — | Yes | — | — | — | — | — |
| AC-AI-QUALITY-9 | Yes | Yes | Yes | — | — | — | — |
| AC-AI-QUALITY-10 | Yes | Yes | — | — | — | — | — |
| AC-AI-QUALITY-11 | — | Yes | — | — | — | — | — |
| AC-AI-QUALITY-12 | — | Yes | — | — | — | — | — |
| AC-AI-QUALITY-13 | — | Yes | Yes | — | — | — | — |

`API IT` here means the `@WebMvcTest`-style standalone `MockMvc` controller test
(`AiQualityControllerTest`) verifying route/status/error-envelope mapping, per
`.claude/rules/40-testing.md` — not a full `@SpringBootTest` integration test against a
live DB (none exists in this codebase, per `impl-plan.md` §10). No Contract Test / DB
migration-specific test / E2E / Black-box row is populated: DB-level enforcement (partial
unique index) is reviewed, not executed, against a live DB (see §6); E2E is explicitly out
of scope (A-AI-QUALITY-12).

## 3. Priority

| test item | priority | reason |
|---|---|---|
| AC-1, -2, -6, -7, -8 (CRUD + soft-delete lifecycle) | High | Core data-integrity behavior (BR-1, BR-2, BR-4); Blocker severity in `review-checklist.md` |
| AC-3, -4 (BR-3 parentage validation) | High | New logic with no BUG-DASHBOARD precedent; highest risk of an unverified edge case |
| AC-5 (BR-6 boundary values) | High | Boundary/rounding bugs are easy to introduce and easy to silently miss |
| AC-10, -11, -12 (RBAC matrix) | High | Security-critical; Blocker severity; must be enforced server-side per BR-5 |
| AC-9 (list/filter pagination) | Medium | Functional but lower blast-radius than write-path bugs; Major severity in `review-checklist.md` |
| AC-13 (search filter, AND-combined) | Medium | Post-implementation addition; was the one identified coverage gap this phase closes |

## 4. Reuse Existing Test

All of the following already exist from Phase 5 and were re-run in this phase (see
`test-results.md` §2) with no changes to their code — reused as-is.

| existing test | path | covers | gap |
|---|---|---|---|
| `mutateRoles_canCreateUpdateDelete` (PM/QA/ADMIN) | `EDCAP_BE/src/test/UnitTest/java/com/sdd/platform/application/usecase/aiquality/AiQualityServiceTest.java` | AC-1, AC-6, AC-12 (create/update/delete happy path for MUTATE roles) | none |
| `devRole_isViewOnly_forbiddenOnWrite_allowedOnRead` | same file | AC-10 | none |
| `noRole_blockedFromEveryEndpointIncludingRead` | same file | AC-11 | none |
| `unauthenticatedCaller_blockedFromEveryEndpoint` | same file | AC-11 | none |
| `requireAnyAccess_passesForAnyAuthenticatedCaller_regardlessOfProjectRole` / `requireAnyAccess_throwsForUnauthenticatedCaller` | same file | §11 FE-guard fallback (`/access` without `projectId`), regression test for the bug in `self-review.md` §7 | none |
| `globalAdmin_getsMutateRegardlessOfPerProjectRole` | same file | AC-12 | none |
| `create_acceptsUpperInclusiveBound_100_00` / `create_acceptsLowerInclusiveBound_0_00` / `create_rejectsAboveUpperBound_100_01` / `create_rejectsBelowLowerBound_negative0_01` / `create_rejectsNullRate` / `create_rejectsScaleGreaterThanTwo_50_555` | same file | AC-5 (BR-6 boundary values) | none |
| `create_duplicateActiveTicket_throwsConflict` / `create_duplicateKeyOnInsert_throwsConflict` | same file | AC-2 (BR-2, app-level + DB-level conflict) | none |
| `create_repositoryNotBelongingToProject_throwsBusinessRuleException` | same file | AC-3 | none |
| `create_ticketNotBelongingToRepository_throwsBusinessRuleException` | same file | AC-4 | none |
| `getOrUpdateOrDelete_notFoundOrDeletedRow_throwsNotFound` | same file | AC-7 (soft-deleted row absent from get/update/delete) | none |
| `afterSoftDelete_recreatingSameTicket_succeeds_noFalseConflict` | same file | AC-8 | none |
| `search_clampsPageSizeToBounds` | same file | AC-9 (page/size clamping) | did not assert `projectId`/`repositoryId`/`ticketId` AND-combination or `search` — closed by new tests below |
| `list_returnsPagedItems`, `get_returnsDetail`, `create_returns201`, `update_usesPut`, `softDelete_usesPutDeleteEndpoint_neverHardDelete`, `exceptions_mapToExpectedEnvelope`, `getAccess_returnsNoContent_whenCallerHasAccess`, `getAccess_withNoProjectId_returnsNoContent_forAnyAuthenticatedCaller`, `getAccess_returnsForbidden_whenCallerHasNoAccess` | `EDCAP_BE/src/test/UnitTest/java/com/sdd/platform/web/rest/AiQualityControllerTest.java` | AC-1, AC-6, AC-7, AC-9 (route/status mapping), AC-10/-11/-12 (`/access` route) | `list_returnsPagedItems` did not exercise the `search` param — closed by new test below |
| `ai-quality-api.test.ts` (7 `it` cases: list query-string construction incl. project/repository/ticket filters, get, create, update, soft-delete via PUT, access) | `EDCAP_FE/src/__ tests __/ai-quality/ai-quality-api.test.ts` | AC-1, AC-6, AC-7, AC-9 (FE query-string construction) | none — `search` param construction not required at this layer per spec (drawer doesn't submit search) |
| `ai-quality.test.tsx` (6 `it` cases: renders list, QA sees create/edit/delete, DEV hides them, create-drawer submit flow, soft-delete, API-failure error message) | `EDCAP_FE/src/__ tests __/ai-quality/ai-quality.test.tsx` | AC-9 (rendering), AC-10 (role-based UI hiding) | none |

## 5. Additional Test This Time

| TC ID | test | type | target | related AC |
|---|---|---|---|---|
| TC-AI-QUALITY-1 | `search_filtersByTicketExternalKeyOrTitle_andCombinedWithOtherFilters` | BE UT | `AiQualityService.search` — asserts `search` term is forwarded to `findPage`/`count` together with non-null `projectId`/`repositoryId`/`ticketId` (AND-combined, not overriding the other filters) | AC-AI-QUALITY-13 |
| TC-AI-QUALITY-2 | `search_blankSearchTermIsNormalizedToNull` | BE UT | `AiQualityService.normalizeSearch` — asserts a blank/whitespace-only `search` param is normalized to `null` rather than passed through literally | AC-AI-QUALITY-13 |
| TC-AI-QUALITY-3 | `list_forwardsSearchParam_andCombinedWithOtherFilters` | API IT (`MockMvc`) | `AiQualityController` — asserts `GET /api/v1/ai-qualities?search=...` (with `projectId`/`repositoryId`/`ticketId` also present) is wired through to `AiQualityService.search` with the `search` value intact | AC-AI-QUALITY-13 |

No new FE test was added: the FE drawer/table for this ticket does not expose a `search`
input in its own component tests' scope (the search wiring is a list-query-string
enhancement covered by `ai-quality-api.test.ts`'s existing query-string-construction
pattern for the other filters); adding a redundant FE assertion for a param the FE doesn't
currently surface in the UI would test an untriggerable path. Tracked as an intentionally
untested area in §6 below, not a silent gap.

### E2E Step-by-step Scenarios

| scenario | precondition | steps | expected | related AC |
|---|---|---|---|---|
| — | — | — | — | — |

No E2E scenarios in scope for this ticket (A-AI-QUALITY-12).

## 6. Areas intentionally left untested this time

| area | reason | risk |
|---|---|---|
| (Resolved 2026-08-18) Manual browser RBAC/UI smoke test (button visibility per role, drawer cascading, 409 toast) | Was untestable in this execution environment (no interactive browser); covered in the interim by BE RBAC-matrix unit tests (AC-10/-11/-12) and FE component tests. User has since performed the manual test directly in a real browser across all role tiers and confirmed everything works, no issues found — see `test-results.md` §8/§10, `report.md` §16 | Closed — no residual risk |
| Playwright E2E | Explicitly out of scope for this ticket (A-AI-QUALITY-12); no raw input or AC requires it | Low — can be added later without rework if QA requests it |
| DB-level partial unique index (`uq_ai_quality_active_ticket`) enforcement under real concurrent inserts | No live DB harness/integration test exists in this codebase (per `impl-plan.md` §10); reviewed via migration-file inspection and via the app-level `DataIntegrityViolationException` → `ConflictException` mapping test (`create_duplicateKeyOnInsert_throwsConflict`), not executed against a live race condition | Low — the app-level check (`existsActiveByTicketId`) already prevents the common case; the DB index is a backstop for the true race window, reviewed not executed, consistent with `docs/standards/testing.md`'s "DB-dependent AC tracking" guidance |
| FE `search` query param wiring (list drawer does not surface a search input for this ticket's UI) | The FE component for this ticket doesn't currently expose a `search` text box; wiring is verified at the API-helper/BE layer only (§5 TC-3) | Low — if a `search` input is added to the FE later, its query-string construction should be tested at that time using the same pattern as the other filters in `ai-quality-api.test.ts` |

## 7. Data testing principles

- Backend unit/controller tests use Mockito-mocked port interfaces only
  (`AiQualityRepositoryPort`, `ProjectRepositoryPort`, `RepositoryRepositoryPort`,
  `TicketLookupPort`, `QaDashboardRepositoryPort`, `AdminAuditLogService`) — never mocked
  domain entities/value types, per `.claude/rules/40-testing.md`.
- All test UUIDs are fixed, clearly-fake constants in the `2000...`/`3000...`/`4000...`/
  `5000...` prefix ranges already established in `AiQualityServiceTest`/
  `AiQualityControllerTest` — no real/production-like IDs, no environment-specific values.
- No live database is used by any BE test in this ticket; there is no integration-test
  harness in this codebase to run one against (per `impl-plan.md` §10).
- Frontend tests use `vi.spyOn(global, "fetch")` mocks per `docs/standards/testing.md`
  Mocking Strategy table — no real network calls; a fresh `QueryClient` per test wrapper.
- No secrets, tokens, or credentials appear in any test fixture.

## 8. Execution command

| command | purpose |
|---|---|
| `mvn -q -DskipITs -Dtest=AiQualityServiceTest,AiQualityControllerTest test` (from `EDCAP_BE/`) | Targeted run of all AI-QUALITY BE unit + controller tests |
| `mvn -DskipITs test` (from `EDCAP_BE/`) | Full BE regression suite — confirm no cross-module regression |
| `npx vitest run "src/__ tests __/ai-quality"` (from `EDCAP_FE/`) | Targeted run of all AI-QUALITY FE tests |
| `npx vitest run` (from `EDCAP_FE/`) | Full FE regression suite — confirm no cross-module regression |

## 9. Stop Condition

None outstanding. All three Stop/Ask conditions from `impl-plan.md` §12 were already
resolved during Phase 5 (`self-review.md` §8) before this phase started. This phase does
not introduce any new Stop/Ask condition — the only new work is one BE test class addition
(no production code change) to close the AC-13 coverage gap.

## 10. Required Human Decision

None outstanding. The one carried-forward pending item — the manual browser RBAC/UI smoke
test (§6), per `docs/standards/testing.md`'s AC Closure / Release Gating "UI-mount
verification" guidance — was performed by the user on 2026-08-18 with no issues found; see
`test-results.md` §8/§10.
