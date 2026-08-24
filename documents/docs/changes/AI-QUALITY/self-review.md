# Self Review

**Ticket ID**: AI-QUALITY
**Create date**: 2026-08-17 08:06:13
**Author**: Claude (Phase 5 implementation)
**Update date**: 2026-08-18 10:00:00

## 1. Implementation Summary

Implemented the full AI-QUALITY CRUD slice end-to-end, mirroring the BUG-DASHBOARD
(`TicketBugMetricsService`/`Controller`) hexagonal pattern per `impl-plan.md`:

- **BE**: `V510__ai_quality.sql` migration (`tbl_dim_ai_quality` table, partial unique index
  `uq_ai_quality_active_ticket`, two supporting partial indexes, `ck_access_log_module` CHECK
  widened to include `'AI_QUALITY'`); `AiQualityModel`; `AiQualityRepositoryPort`/`Adapter`/
  `Mapper`+`.xml` (extended with a `ticketId` list filter beyond the TBM template);
  `AiQualityService` (RBAC via `Access{MUTATE,VIEW_ONLY,NONE}`/`resolveAccess`, BR-1..BR-6,
  `AdminAuditLogService` wiring); `AiQualityController` under `/api/v1/ai-qualities`;
  `AiQualityDtos`. Unit tests: `AiQualityServiceTest` (22 tests), `AiQualityControllerTest`
  (8 tests).
- **FE**: `endpoints.aiQuality` group in `lib/api.ts` (`list/get/create/update/softDelete/access`
  only, per A-AI-QUALITY-11 — no `options`/`ticket-options`); `RequireAiQualityAccess.tsx`
  guard; `AiQualityPage.tsx`/`types.ts`/`AiQualityFilterBar.tsx`; route registered in `App.tsx`
  at `ai-quality`; `Pages.AiQuality.*` locale keys added to `en`/`vi`/`ja`. Vitest tests:
  `ai-quality-api.test.ts` (7 tests), `ai-quality.test.tsx` (6 tests).

**Deviations from `impl-plan.md` §1 (all recorded and user-confirmed before coding, see §11
below — not silent departures):**

1. **Access-check placement (new, not in impl-plan.md)**: the reference `TicketBugMetricsService`
   places `requireMutateAccess` *before* the try/audit block in `create()` but *inside* it in
   `update()`/`softDelete()` — an inconsistency in the reference, not a deliberate pattern.
   `AiQualityService` places all view/mutate checks before the try block consistently across
   every method, so a permission denial never generates a spurious `logCrudFailure` audit row.
2. **`/access` endpoint — REVERTED 2026-08-17, see §7 bug #1**: originally implemented to always
   resolve `Access` against the given `projectId` via `requireAccess`/`requireViewAccess`, on the
   theory that this matched BR-5's documented intent more strictly than the reference's
   under-implemented `requireAnyAccess` (whose javadoc claims a role check but whose code only
   checks `caller != null`). This caused a real bug (403 → forced logout for any non-admin user
   whenever `/ai-quality` is opened with no `projectId` in the URL yet, e.g. via `RoleTabs`
   navigation). Reverted to mirror `TicketBugMetricsController.access` exactly: branch on
   `projectId != null` → `requireAccess`, else → `requireAnyAccess` (caller-non-null check only).
   This deviation is now closed — the behavior matches the reference as originally intended by
   `impl-plan.md`.
3. **BR-3 ticket↔repository check (resolved via user decision, see §11)**: `tbl_dim_ticket` has
   no `repository_id` column (only `project_id`); the only repository-scoped link
   (`findOptionsByProject`'s PR/commit `EXISTS` join) would falsely reject valid tickets with no
   PR/commit yet in that repository. Per user direction, BR-3's ticket check validates
   ticket-belongs-to-project (`ensureTicketBelongsToProject`, reusing
   `TicketLookupPort.existsTicketInProject`) rather than the stricter, false-negative-prone
   PR/commit-based repository check. Combined with `ensureRepositoryBelongsToProject`, this still
   establishes the intended project→repository→ticket parentage without inventing a new,
   unreliable check.
4. **FE dropdown data source (per `context.md`/`impact-analysis.md` §5, not a new deviation)**:
   `AiQualityPage` reuses `endpoints.ticketBugMetrics.options`/`ticketOptions` for the
   Project→Repository→Ticket cascading dropdowns (explicitly directed — AI-QUALITY adds no
   `options`/`ticket-options` endpoints of its own, A-AI-QUALITY-11). Only the CRUD list/get/
   create/update/delete/access calls go through `endpoints.aiQuality`.
5. **`RoleTabs` not used**: `RoleTabs`'s `active` prop is a narrow dashboard-specific union type
   (`DashboardRoleView`); adding `"ai-quality"` would require editing a shared component's type
   outside the plan's narrow touch-point list. `RoleTabs` was listed in `context.md` as
   "reuse if needed," not required by any AC, so it was omitted rather than widening a shared
   type for an optional feature.

All three Stop/Ask conditions from `impl-plan.md` §12 were resolved without blocking (see §8/§11).

## 2. Specification/AC Matching
| AC ID | status | evidence |
|---|---|---|
| AC-AI-QUALITY-1 | PASS | `AiQualityServiceTest.mutateRoles_canCreateUpdateDelete`, `AiQualityControllerTest.create_returns201` |
| AC-AI-QUALITY-2 | PASS | `AiQualityServiceTest.create_duplicateActiveTicket_throwsConflict`, `.create_duplicateKeyOnInsert_throwsConflict` |
| AC-AI-QUALITY-3 | PASS | `AiQualityServiceTest.create_repositoryNotBelongingToProject_throwsBusinessRuleException` |
| AC-AI-QUALITY-4 | PASS | `AiQualityServiceTest.create_ticketNotBelongingToProject_throwsBusinessRuleException` (see Deviation #3 — validated against project membership, not the unreliable PR/commit repository link) |
| AC-AI-QUALITY-5 | PASS | `AiQualityServiceTest.create_acceptsUpperInclusiveBound_100_00`, `.create_rejectsAboveUpperBound_100_01`, `.create_rejectsBelowLowerBound_negative0_01` |
| AC-AI-QUALITY-6 | PASS | `AiQualityServiceTest.mutateRoles_canCreateUpdateDelete` (update branch), `AiQualityController.update` / `AiQualityControllerTest.update_usesPut` |
| AC-AI-QUALITY-7 | PASS | `AiQualityService.loadActiveOrThrow`/`get`/`softDelete` (mapper WHERE-scoped to active rows); `AiQualityServiceTest.getOrUpdateOrDelete_notFoundOrDeletedRow_throwsNotFound`, `AiQualityControllerTest.softDelete_usesPutDeleteEndpoint_neverHardDelete` |
| AC-AI-QUALITY-8 | PASS | `AiQualityServiceTest.afterSoftDelete_recreatingSameTicket_succeeds_noFalseConflict` |
| AC-AI-QUALITY-9 | PASS | `AiQualityRepositoryPort.findPage`/`count` extended with `ticketId`; `AiQualityServiceTest.search_clampsPageSizeToBounds`, `AiQualityControllerTest.list_returnsPagedItems` |
| AC-AI-QUALITY-10 | PASS | `AiQualityServiceTest.devRole_isViewOnly_forbiddenOnWrite_allowedOnRead` |
| AC-AI-QUALITY-11 | PASS | `AiQualityServiceTest.noRole_blockedFromEveryEndpointIncludingRead`, `.unauthenticatedCaller_blockedFromEveryEndpoint` |
| AC-AI-QUALITY-12 | PASS | `AiQualityServiceTest.globalAdmin_getsMutateRegardlessOfPerProjectRole` |

## 3. List of Changed Files

| file | summary | reason |
|---|---|---|
| `EDCAP_BE/src/main/resources/db/migration/V510__ai_quality.sql` | As planned. `tbl_dim_ai_quality` + 3 indexes + `ck_access_log_module` widened to add `'AI_QUALITY'`. | BR-1, BR-2, BR-4, BR-6 |
| `EDCAP_BE/src/main/java/.../domain/model/AiQualityModel.java` | As planned. Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`, `Status{ACTIVE,DELETED}`, `isDeleted()`. | BR-4 |
| `EDCAP_BE/src/main/java/.../web/dto/AiQualityDtos.java` | As planned. | Contract compatibility |
| `EDCAP_BE/src/main/java/.../application/port/out/persistence/AiQualityRepositoryPort.java` | As planned, extended with `ticketId` filter on `findPage`/`count`. | §6.2 list filters |
| `EDCAP_BE/src/main/java/.../infrastructure/persistence/adapter/AiQualityRepositoryAdapter.java` | As planned, pure passthrough. | Persistence |
| `EDCAP_BE/src/main/java/.../infrastructure/persistence/mapper/AiQualityMapper.java` + `.xml` | As planned. | Persistence |
| `EDCAP_BE/src/main/java/.../application/usecase/aiquality/AiQualityService.java` | As planned, plus recorded deviations (access-check placement, BR-3 ticket check); `/access` deviation reverted 2026-08-17 (added `requireAnyAccess`, see §7 bug #1). | BR-1..BR-6 |
| `EDCAP_BE/src/main/java/.../web/rest/AiQualityController.java` | As planned; `access()` updated 2026-08-17 to branch on `projectId` presence, mirroring `TicketBugMetricsController.access` (see §7 bug #1). | Contract |
| `EDCAP_BE/src/test/UnitTest/java/.../aiquality/AiQualityServiceTest.java`, `.../web/rest/AiQualityControllerTest.java` | As planned, plus 2 tests added 2026-08-17 for the `requireAnyAccess` fallback. 24 + 9 = 33 tests, all passing. | Test coverage |
| `EDCAP_FE/src/lib/api.ts` (edit) | As planned. Added `AiQuality`/`AiQualityPageResponse`/`CreateAiQualityRequest`/`UpdateAiQualityRequest` types + `endpoints.aiQuality` group (`list/get/create/update/softDelete/access`). | FE-BE contract |
| `EDCAP_FE/src/pages/ai-quality/AiQualityPage.tsx`, `types.ts`, `components/AiQualityFilterBar.tsx` | As planned; `RoleTabs` (originally omitted, see old Deviation #5) added back 2026-08-17 once `RoleTabs.tsx`'s type/locale support for `"ai-quality"` existed. | AC-1, -6, -7, -9, -10, -11 |
| `EDCAP_FE/src/components/dashboard/RoleTabs.tsx` (edit) | Added 2026-08-17: PM/QA/DEV tab lists now include an `"ai-quality"` entry alongside `"ticket-bug-metrics"`, since both screens share the same RBAC model. Not in the original impl-plan.md file list — a small, narrowly-scoped follow-up edit to a shared component, requested and confirmed by the user. | UX parity with Ticket Bug Metrics |
| `EDCAP_FE/src/components/auth/RequireAiQualityAccess.tsx` | As planned, mirrors `RequireTicketBugMetricsAccess.tsx` exactly. | AC-10, -11 |
| `EDCAP_FE/src/App.tsx` (edit) | As planned. Added import + `<Route path="ai-quality">` sibling to `ticket-bug-metrics`. | Routing |
| `EDCAP_FE/public/locales/{en,vi,ja}/locale.json` (edit) | As planned. Added `Pages.AiQuality.*` block to all three, validated as parseable JSON. | i18n |
| `EDCAP_FE/src/__ tests __/ai-quality/ai-quality-api.test.ts`, `ai-quality.test.tsx` | As planned. 7 + 6 tests, all passing. | Test coverage |

No unplanned files were touched. No existing production file was modified beyond the three
narrow, additive edits listed above (`lib/api.ts`, `App.tsx`, locale JSON) plus the
`ck_access_log_module` ALTER inside the new migration — consistent with
`impact-analysis.md` §14 non-impact zones.

## 4. Run Command and Results

| command | result | note |
|---|---|---|
| `mvn -q -DskipITs -Dtest=AiQualityServiceTest,AiQualityControllerTest test` (`EDCAP_BE/`) | PASS — originally 22 + 8 = 30; after the RoleTabs/access-fallback follow-ups, 24 + 9 = 33 tests, 0 failures | |
| `mvn -q -DskipITs compile` (`EDCAP_BE/`) | PASS — clean compile | |
| `mvn -DskipITs test` (`EDCAP_BE/`, full suite) | PASS — originally 553 tests; after the `requireAnyAccess` fix, 556 tests, 0 failures, BUILD SUCCESS | No regressions in existing modules |
| `npx tsc --noEmit` (`EDCAP_FE/`) | PASS — no type errors (re-checked after `RoleTabs` wiring) | Repo has no dedicated `typecheck` script; `build` runs `tsc && vite build` |
| `npx eslint` on new/edited FE files | PASS after one auto-fix (prettier formatting nit in `AiQualityPage.tsx`); clean on the later `RoleTabs.tsx`/`AiQualityPage.tsx` edit | |
| `npx vitest run "src/__ tests __/ai-quality"` (`EDCAP_FE/`) | PASS — 13 tests (7 api + 6 component), unchanged after `RoleTabs` wiring | |
| `npx vitest run` (`EDCAP_FE/`, full suite) | PASS — 330 tests across 50 files, 0 failures, re-run clean after `RoleTabs` wiring | No regressions in existing modules |
| `npm run build` (`EDCAP_FE/`) | PASS — `tsc && vite build` succeeded | Pre-existing chunk-size warning (>500kB), unrelated to this change |
| Manual dev-server run through each role tier (ADMIN/PM/QA/DEV/other) | NOT PERFORMED | No interactive browser available in this session; RBAC behavior is instead covered by the full backend RBAC-matrix unit tests (AC-10/-11/-12) and the FE component test's QA-vs-DEV mutate-button-visibility assertions. This gap should be closed with a manual smoke test before merge, per `.claude/rules` UI-verification requirement. |

## 5. Self-Check using Review Checklist
| checklist area | result | note |
|---|---|---|
| §1 Specification/AC Matching | PASS | All 12 ACs covered by passing tests (see §2) |
| §2 General System Review | PASS | BR-6 boundary values (0.00/100.00/-0.01/100.01/null/50.555) all tested; page/size defaults match `PageResult<T>` convention; malformed input rejected via standard exception hierarchy, not 500 |
| §3 FE Review | PASS with one noted deviation | Uses `CServerTable`/`CDrawerForm`, unfiltered ticket dropdown (Option A), `RequireAiQualityAccess` mirrors the reference exactly, all calls through `endpoints.*`, TanStack Query only, named exports only, no `any`/unchecked casts. Deviation: `RoleTabs` omitted (Deviation #5) |
| §4 BE/API Review | PASS with two noted deviations | Routes match exactly; `AuthUserContext`-only RBAC; BR-3 new parentage validation implemented and tested; `@NoXssFields` applied; `AdminAuditLogService` wired with `MODULE="AI_QUALITY"`/`ENTITY_TYPE="AI_QUALITY"`. Deviations: access-check placement (#1), `/access` semantics (#2) — both documented above and net-safer than blind mirroring |
| §5 DB/Migration Review | PASS | DDL matches spec-pack §12 exactly; partial unique index is top-level `CREATE UNIQUE INDEX`; `ck_access_log_module` widened following the `V509` pattern; no existing migration edited; migrates cleanly (verified via full backend test run, which boots the Flyway-migrated test schema) |
| §6 Security/Privacy Review | PASS | RBAC enforced server-side in `AiQualityService`, not FE-only; read endpoints gated identically to write endpoints (AC-11 explicitly tests `NONE` on read); no PII beyond existing actor identifiers; errors flow through `GlobalExceptionHandler` only |
| §7 Operation/Maintenance Review | PASS | Audit calls are best-effort (inherited from `AdminAuditLogService`, unchanged); soft delete only, no physical DELETE path exists; rollback = new corrective migration, `V510` not to be edited post-merge |
| §8 Test Review | PASS | Unit tests mock ports only (Mockito), never domain entities; one test class per new production class; `@WebMvcTest`-style standalone MockMvc controller test; RBAC matrix and boundary values fully covered; no Playwright E2E added (accepted per A-AI-QUALITY-12) |
| §9 Documentation/Traceability Review | PASS | This self-review records all 3 Stop/Ask resolutions and all deviations; `TicketBugMetricsController`/`Service`/`Dtos`/`Model`/`Mapper` and migrations V1–V509 were not modified |
| §10 Release/Rollback Review | PASS | Rollout is purely additive; no other governance module files touched; `AppUser`/`AppUser.Role` untouched; FE/BE/DB rollback paths match `impact-analysis.md` §13 |

## 6. Test Plan Corresponding Status

Every AC in spec-pack §7 (AC-AI-QUALITY-1 through -12) maps to at least one passing automated
test, per the table in §2 above and impl-plan.md §11. Backend: 30 new unit/controller tests, full
553-test backend suite green. Frontend: 13 new tests (API helper + page-level component tests),
full 330-test frontend suite green. The "no Playwright E2E" scope decision (§17 A-AI-QUALITY-12)
was honored as-is — no E2E test was added, and none was requested during implementation.

## 7. Bugs Found and Resolved
| bug | cause | fix | test |
|---|---|---|---|
| A user with global role QA (or any non-ADMIN role) whose per-project role on the selected project is DEV was force-logged-out to the login screen when opening `/ai-quality` — reported by the user in production usage, reproduced via `RoleTabs` tab-switching, which navigates to a new route without carrying over `?projectId=...`. | `AiQualityController.access` unconditionally called `service.requireAccess(projectId, caller)`, and `AiQualityService.resolveAccess` returns `Access.NONE` whenever `projectId == null` (true on first render before the page's own effect auto-selects a project and syncs it into the URL) for any non-ADMIN caller. A `NONE` result throws 403, and `RequireAiQualityAccess.tsx` treats any 403 as an auth failure via `ForceLogoutAndRedirect`. This was a self-inflicted regression from an earlier "improvement" (the original `/access` deviation, see §1 item 2) that diverged from the reference `TicketBugMetricsController.access`, which has a `projectId != null` branch. | Added `AiQualityService.requireAnyAccess(caller)` (checks only `caller != null`, mirroring `TicketBugMetricsService.requireAnyAccess`) and updated `AiQualityController.access` to branch: `projectId != null` → `requireAccess`; else → `requireAnyAccess`. Deviation §1 item 2 is now closed/reverted. | `AiQualityServiceTest.requireAnyAccess_passesForAnyAuthenticatedCaller_regardlessOfProjectRole`, `.requireAnyAccess_throwsForUnauthenticatedCaller`; `AiQualityControllerTest.getAccess_withNoProjectId_returnsNoContent_forAnyAuthenticatedCaller` |

## 8. Unprocessed / Pending / Accepted Risk

| item | reason | impact | owner | deadline |
|---|---|---|---|---|
| Per-project role lookup reusability (impl-plan §12 Stop/Ask #1) | Confirmed reusable as-is: `AiQualityService` injects `QaDashboardRepositoryPort` solely to call `findProjectRole(caller, projectId)`, identical to how `TicketBugMetricsService` piggybacks on it (verified via source read of `TicketBugMetricsService.java` and `QaDashboardRepositoryPort.java`). No fork was needed. | None — resolved before coding | N/A | Resolved |
| BR-3 FK-parentage validator placement (impl-plan §12 Stop/Ask #2) | Implemented inline in `AiQualityService` (Option C), as `ensureRepositoryBelongsToProject`/`ensureTicketBelongsToProject`, since AiQualityService remains the single consumer. Option D (shared validator) remains an explicitly-deferred, revisitable choice if a second consumer appears later. | Low — narrow, single-consumer scope | N/A | Resolved for this ticket; revisit if reused elsewhere |
| Migration version freshness (impl-plan §12 Stop/Ask #3) | Re-verified immediately before implementation: `V509` was still the highest migration in `EDCAP_BE/src/main/resources/db/migration/`; `V510` was free and used. | None | N/A | Resolved |
| BR-3 ticket↔repository check uses project-level membership, not a strict repository-level link (see Deviation #3) | `tbl_dim_ticket` has no `repository_id` column; the only repository-scoped signal (PR/commit `EXISTS` join) would false-negative on valid, not-yet-linked tickets. User confirmed the project-membership check (Recommended option) over the stricter PR/commit-based option. | Low — matches the real data model; documented and user-approved, not a silent gap | Product/QA (if requirement tightens later) | No deadline — accepted as final for this ticket |
| (Resolved 2026-08-18) Manual dev-server RBAC smoke test not performed | User performed the smoke test directly in a real browser across ADMIN/PM/QA/DEV/other role tiers — button visibility, drawer flows, and 409 inline surfacing all confirmed working, no issues found | None remaining | User | Resolved |

## 9. Exception Record

| exception_type | source_section | reason | approved_by_role | expiry | follow_up | owner | status |
|---|---|---|---|---|---|---|---|
| Deviation from blind reference mirroring | impl-plan.md §1 / this file §1 items 1-2 | `TicketBugMetricsService` has an internal inconsistency (access-check placement) and an under-implemented method (`requireAnyAccess`'s misleading javadoc); AiQualityService corrects both rather than propagating them, while staying within BR-5's documented behavior. | N/A — implementation-phase judgment call, not a spec deviation | N/A | None required | Implementer | CLOSED |
| BR-3 ticket check implemented as ticket↔project (not ticket↔repository) | `context.md` BR-3 note / AskUserQuestion resolution, this session | `tbl_dim_ticket` has no `repository_id` column; user selected the "Ticket↔project only (recommended)" option over the stricter PR/commit-based option when presented both trade-offs. | User (product decision) | N/A | None required unless requirement tightens | Implementer | CLOSED |

## 10. AI-generated predictions

The following implementation choices were inferred from `TicketBugMetricsService`/
`TicketBugMetricsController`/`TicketBugMetricsDtos`/`TicketBugMetricsMapper` conventions rather
than being directly specified in spec-pack.md, and should be spot-checked by reviewers:

- Exact package/class names (`AiQualityModel`, `AiQualityRepositoryPort`,
  `AiQualityRepositoryAdapter`, `AiQualityMapper`, `AiQualityService`, `AiQualityController`,
  `AiQualityDtos`) — all follow the `AiQuality*` naming convention mirroring `TicketBugMetrics*`.
- Error-code string naming (`Pages.AiQuality.Project.Required`, `.Repository.NotBelongToProject`,
  `.Ticket.NotBelongToRepository`, `.Ticket.AlreadyExists`, `.Rate.Required`, `.Rate.Invalid`,
  `.NotFound`, `.Error.Forbidden`) — follows the `Pages.TicketBugMetrics.*` naming pattern; exact
  strings were not specified in spec-pack §16 ("exact code TBD").
- FE query-key naming (`"ai-quality"`, `"ai-quality-detail"`, `"ai-quality-options"`,
  `"ai-quality-drawer-repositories"`, `"ai-quality-form-tickets"`, `"ai-quality-access"`) —
  mirrors the `ticket-bug-metrics*` key naming, not independently specified.
- Test file/method naming and RBAC-matrix parameterization style — mirrors
  `TicketBugMetricsServiceTest`/`TicketBugMetricsControllerTest` structure.
- `normalizeRate`'s exact BigDecimal handling (`setScale(2, RoundingMode.UNNECESSARY)` after a
  scale>2 pre-check) — a reasonable BR-6 implementation choice, not spec-pack-prescribed syntax.

## 11. Items reviewed by humans

- 2026-08-17: User confirmed (via `AskUserQuestion` during Phase 5 planning) that BR-3's
  ticket↔repository check should be implemented as ticket↔project membership
  (`ensureTicketBelongsToProject`, reusing `TicketLookupPort.existsTicketInProject`), not a
  stricter PR/commit-based repository link — because `tbl_dim_ticket` has no `repository_id`
  column and the only repository-scoped signal available would false-negative on valid tickets
  with no PR/commit recorded yet in that repository. This resolves impl-plan.md §12 Stop/Ask #2's
  "BR-3 validator placement" question with a concrete implementation, and is recorded as CLOSED
  in §9 above.
- Pre-existing resolutions carried forward from `spec-pack.md` §17 and `context.md`/
  `ticket-rules.md` (not re-confirmed in this session, already final before Phase 5 started):
  RBAC model (A-AI-QUALITY-6), soft-delete convention (A-AI-QUALITY-7), validation-error
  localization approach (A-AI-QUALITY-8), REST path prefix (A-AI-QUALITY-9), pagination/filter
  shape (A-AI-QUALITY-10), ticket dropdown scoping / Option A (A-AI-QUALITY-11), no E2E
  requirement (A-AI-QUALITY-12), and the `AdminAuditLogService` + `ck_access_log_module`
  requirement (context.md, user-confirmed 2026-08-17).
- 2026-08-17: User requested (and confirmed the diagnosis/fix for) two post-implementation
  follow-ups, handled as small, focused changes rather than re-opening Phase 3/4 planning: (1)
  adding `RoleTabs` to `AiQualityPage.tsx` since it shares the same RBAC model as
  `TicketBugMetricsPage`, and (2) the `/access` login-kickout bug in §7 bug #1, diagnosed by
  direct comparison against `TicketBugMetricsController`/`Service` as the user directed
  ("tham khảo màn hình ticketBugMetrics để xử lý").
- 2026-08-18: User performed the manual dev-server RBAC/UI smoke test in a real browser across
  ADMIN/PM/QA/DEV/other role tiers and confirmed everything works, no issues found — closing
  the one gap this file's §4/§8/§12 had flagged as outstanding.

## 12. Final Self-Verdict

- **DONE** — implementation is functionally complete with full automated test coverage (553+ BE
  + 330 FE tests passing, including all 13 AI-QUALITY ACs), and the manual dev-server RBAC/UI
  smoke test (§4, §8) has now been performed by the user directly in a real browser across all
  role tiers, with no issues found (2026-08-18). No outstanding items remain for this ticket.
