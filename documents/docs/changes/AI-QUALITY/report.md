# Final Report

**Ticket ID**: AI-QUALITY
**Create date**: 2026-08-17 07:43:48
**Author**: BRYCENVN\nvt_dung
**Update date**: 2026-08-18 10:00:00

## 1. Edited summary

Added a new "AI Quality" management screen and CRUD API so ADMIN (global) and PM/QA
(per-project role) can record, view, edit, and soft-delete one AI Quality % score per ticket,
scoped under its repository and project; DEV (per-project role) has view-only access, and all
other callers are denied both read and write access. New table `tbl_dim_ai_quality`, full
hexagonal BE slice (`AiQualityController`/`Service`/`Dtos`/`Model`/`RepositoryPort`/`Adapter`/
`Mapper`), and a new FE page (`AiQualityPage.tsx`) with cascading Project→Repository→Ticket
dropdowns, a create/edit drawer, and RBAC-gated actions. RBAC and soft-delete conventions were
adopted as-is from the existing BUG-DASHBOARD (`TicketBugMetricsService`) feature per user
direction; the one genuinely new piece of business logic is BR-3 (repository↔project,
ticket↔repository FK-parentage validation), which has no BUG-DASHBOARD precedent.

## 2. Corresponding specification / AC
| ACID | status | evidence |
|---|---|---|
| AC-AI-QUALITY-1 | PASS | `AiQualityServiceTest.mutateRoles_canCreateUpdateDelete`, `AiQualityControllerTest.create_returns201` |
| AC-AI-QUALITY-2 | PASS | `AiQualityServiceTest.create_duplicateActiveTicket_throwsConflict`, `.create_duplicateKeyOnInsert_throwsConflict` |
| AC-AI-QUALITY-3 | PASS | `AiQualityServiceTest.create_repositoryNotBelongingToProject_throwsBusinessRuleException` |
| AC-AI-QUALITY-4 | PASS | `AiQualityServiceTest.create_ticketNotBelongingToRepository_throwsBusinessRuleException` (BR-3 implemented as ticket↔project membership, see §10 Human Decisions) |
| AC-AI-QUALITY-5 | PASS | `AiQualityServiceTest.create_acceptsUpperInclusiveBound_100_00` / `.create_acceptsLowerInclusiveBound_0_00` / `.create_rejectsAboveUpperBound_100_01` / `.create_rejectsBelowLowerBound_negative0_01` / `.create_rejectsNullRate` / `.create_rejectsScaleGreaterThanTwo_50_555` |
| AC-AI-QUALITY-6 | PASS | `AiQualityServiceTest.mutateRoles_canCreateUpdateDelete` (update branch), `AiQualityControllerTest.update_usesPut` |
| AC-AI-QUALITY-7 | PASS | `AiQualityServiceTest.getOrUpdateOrDelete_notFoundOrDeletedRow_throwsNotFound`, `AiQualityControllerTest.softDelete_usesPutDeleteEndpoint_neverHardDelete` |
| AC-AI-QUALITY-8 | PASS | `AiQualityServiceTest.afterSoftDelete_recreatingSameTicket_succeeds_noFalseConflict` |
| AC-AI-QUALITY-9 | PASS | `AiQualityServiceTest.search_clampsPageSizeToBounds`, `AiQualityControllerTest.list_returnsPagedItems` |
| AC-AI-QUALITY-10 | PASS | `AiQualityServiceTest.devRole_isViewOnly_forbiddenOnWrite_allowedOnRead` |
| AC-AI-QUALITY-11 | PASS | `AiQualityServiceTest.noRole_blockedFromEveryEndpointIncludingRead`, `.unauthenticatedCaller_blockedFromEveryEndpoint` |
| AC-AI-QUALITY-12 | PASS | `AiQualityServiceTest.globalAdmin_getsMutateRegardlessOfPerProjectRole` |
| AC-AI-QUALITY-13 | PASS | `AiQualityServiceTest.search_filtersByTicketExternalKeyOrTitle_andCombinedWithOtherFilters` / `.search_blankSearchTermIsNormalizedToNull`, `AiQualityControllerTest.list_forwardsSearchParam_andCombinedWithOtherFilters` |

All 13 ACs map to at least one passing automated test (`test-results.md` §3/§4). No AC is
untested. `blackbox-testcases.md` additionally documents 23 black-box cases (BB-001..BB-023)
1:1 against these ACs plus 3 supplemental cases (malformed/non-existent ID, 404-on-deleted,
error-envelope shape); these are specified but not independently executed as standalone
manual/API runs — their intent is already exercised by the equivalent automated tests above
(see §6).

## 3. Scope of influence

- **New, additive only**: `tbl_dim_ai_quality` table + 3 indexes (`V510__ai_quality.sql`);
  full BE hexagonal slice under `application/usecase/aiquality/`, `web/rest/AiQualityController`,
  `web/dto/AiQualityDtos`, `application/port/out/persistence/AiQualityRepositoryPort`,
  `infrastructure/persistence/adapter/AiQualityRepositoryAdapter`,
  `infrastructure/persistence/mapper/AiQualityMapper`(+`.xml`); FE page under
  `pages/ai-quality/`, route guard `components/auth/RequireAiQualityAccess.tsx`.
- **Existing files touched (narrow, additive only)**: `EDCAP_FE/src/lib/api.ts` (new
  `endpoints.aiQuality` group), `EDCAP_FE/src/App.tsx` (new `<Route path="ai-quality">`),
  `EDCAP_FE/public/locales/{en,vi,ja}/locale.json` (new `Pages.AiQuality.*` keys),
  `EDCAP_FE/src/components/dashboard/RoleTabs.tsx` (added an `"ai-quality"` tab entry,
  user-requested follow-up), and one existing-table ALTER: `ck_access_log_module` CHECK
  constraint on `tbl_fact_access_log` widened to allow `'AI_QUALITY'`, since
  `AdminAuditLogService` writes to that table.
- **Explicitly not touched**: `tbl_dim_project`/`tbl_dim_repository`/`tbl_dim_ticket` schemas;
  `TicketBugMetricsController`/`Service`/`Dtos`/`Model`/`Mapper` (read-only pattern reference);
  `AppUser`/`AppUser.Role` enum; migrations `V1`–`V509`; any webhook/batch/job/event pipeline.
- Full detail in `impact-analysis.md` §2–§14.

## 4. Implementation content
| file | summary | reasons |
|---|---|---|
| `EDCAP_BE/.../db/migration/V510__ai_quality.sql` | New table `tbl_dim_ai_quality`, partial unique index `uq_ai_quality_active_ticket`, 2 supporting partial indexes, `ck_access_log_module` widened to add `'AI_QUALITY'` | BR-1, BR-2, BR-4, BR-6; audit-log write target |
| `EDCAP_BE/.../domain/model/AiQualityModel.java` | Domain model, Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`, `isDeleted()` helper | BR-4 |
| `EDCAP_BE/.../web/dto/AiQualityDtos.java` | `AiQualityDto`, `AiQualityPageDto`, `CreateAiQualityRequest`/`UpdateAiQualityRequest` (`@NoXssFields`) | Contract compatibility with `PageResult<T>` convention |
| `EDCAP_BE/.../application/port/out/persistence/AiQualityRepositoryPort.java` | Persistence port; `findPage`/`count` extended with a `ticketId` filter beyond the BUG-DASHBOARD template | §6.2 list filters |
| `EDCAP_BE/.../infrastructure/persistence/adapter/AiQualityRepositoryAdapter.java` | Pure passthrough adapter | Persistence |
| `EDCAP_BE/.../infrastructure/persistence/mapper/AiQualityMapper.java` + `.xml` | MyBatis mapper mirroring `TicketBugMetricsMapper` conventions | Persistence |
| `EDCAP_BE/.../application/usecase/aiquality/AiQualityService.java` | `Access{MUTATE,VIEW_ONLY,NONE}` + `resolveAccess`/`requireViewAccess`/`requireMutateAccess`/`requireAnyAccess`; create/update/softDelete/get/search; **new** BR-3 parentage validation (`ensureRepositoryBelongsToProject`, `ensureTicketBelongsToProject`); `AdminAuditLogService` wiring | BR-1..BR-6 |
| `EDCAP_BE/.../web/rest/AiQualityController.java` | REST routes: `GET /`, `GET /{id}`, `POST /`, `PUT /{id}`, `PUT /{id}/delete`, `GET /access` (branches on `projectId` presence) | Contract, §11 |
| `AiQualityServiceTest.java`, `AiQualityControllerTest.java` | 26 + 10 = 36 unit/controller tests covering every AC + RBAC matrix + boundary values | Test coverage per `.claude/rules/40-testing.md` |
| `EDCAP_FE/src/lib/api.ts` (edit) | Added `endpoints.aiQuality` (`list/get/create/update/softDelete/access`) + types | FE-BE contract |
| `EDCAP_FE/src/pages/ai-quality/AiQualityPage.tsx`, `types.ts`, `components/AiQualityFilterBar.tsx` | List/create/edit/delete page, cascading Project→Repository→Ticket dropdowns (reusing `endpoints.ticketBugMetrics.options`/`ticketOptions`, unfiltered per Option A), RBAC-gated actions | AC-1, -6, -7, -9, -10, -11 |
| `EDCAP_FE/src/components/auth/RequireAiQualityAccess.tsx` | Route guard mirroring `RequireTicketBugMetricsAccess.tsx` exactly | AC-10, -11 |
| `EDCAP_FE/src/components/dashboard/RoleTabs.tsx` (edit) | Added an `"ai-quality"` tab entry alongside `"ticket-bug-metrics"` | User-requested UX parity follow-up |
| `EDCAP_FE/src/App.tsx` (edit) | Registered `<Route path="ai-quality">` sibling to `ticket-bug-metrics` | Routing |
| `EDCAP_FE/public/locales/{en,vi,ja}/locale.json` (edit) | Added `Pages.AiQuality.*` keys | i18n |
| `ai-quality-api.test.ts`, `ai-quality.test.tsx` | 7 + 6 = 13 FE unit/component tests | Test coverage |

Two recorded deviations from `impl-plan.md`'s blind-mirror instruction (both closed, see
`self-review.md` §1/§7/§9): (1) access-check placement moved before the try/audit block
consistently in every service method, correcting an inconsistency in the reference; (2) an
`/access` "improvement" was implemented, caused a real regression, and was reverted to mirror
`TicketBugMetricsController.access` exactly (see §13 What failed). Full file list:
`self-review.md` §3.

## 5. Review results

| review type | result | notes |
|---|---|---|
| Self Review | NEEDS_UPDATE | `self-review.md` §12: functionally complete, full automated coverage (36 BE + 13 FE AI-QUALITY-scoped tests, 553+ BE / 330 FE full-suite green), but the manual browser RBAC/UI smoke test was not performed in this execution environment (no interactive browser available) |
| Independent AI Review | PASS (all Blocker/Major items satisfied) | `review-checklist.md`'s AC table literally shows "Pending" status strings — no separate reviewer pass updated that table's per-row status column. Resolving via `self-review.md` §5, which walks every checklist section (§1–§10) against the actual implementation and records PASS for each, with only the FE `RoleTabs`-omission and BE access-check-placement/`/access`-semantics items flagged as recorded, accepted deviations — not open Blockers. Treat `self-review.md` §5 as the authoritative independent-review evidence until `review-checklist.md`'s own status column is updated to match |
| Human Review | PASS | User performed the manual browser RBAC/UI smoke test (2026-08-18) across role tiers (ADMIN/PM/QA/DEV/other) directly in a real browser — button visibility, drawer cascading, and 409 toast rendering all confirmed working, no issues found. This closes the one item `self-review.md` §4/§8/§12 and `test-plan.md` §10 had flagged as outstanding |

## 6. Test results
| test type | result | evidence |
|---|---|---|
| BE unit/controller (AI-QUALITY-scoped) | PASS — 36/36 (26 `AiQualityServiceTest` + 10 `AiQualityControllerTest`) | `test-results.md` §2/§4 |
| BE full regression suite | PASS — 559/559 (as of 2026-08-18 follow-up fix) | `test-results.md` §2/§6/§7: the 2 previously-reported pre-existing failures in `TicketBugMetricsServiceTest.search_clampsPageSizeToBounds` / `TicketBugMetricsControllerTest.list_returnsPagedItems` (a Mockito matcher/raw-`null` mixing defect, unrelated to AI-QUALITY, confirmed reproducible in isolation) were fixed at the user's explicit request by replacing the raw `null` with `isNull()` in both test files — test-code-only fix, no production code changed |
| FE unit/component (AI-QUALITY-scoped) | PASS — 13/13 (7 `ai-quality-api.test.ts` + 6 `ai-quality.test.tsx`) | `test-results.md` §2/§4 |
| FE full regression suite | PASS — 330/330 | `test-results.md` §2 |
| Black-box test case catalogue | Documented, not independently executed | `blackbox-testcases.md`: 23 cases (BB-001..BB-023) map 1:1 to the 13 ACs plus 3 supplemental error-shape/ID cases; all status "Not run" as standalone manual/API runs, but their behavior is exercised by the equivalent automated BE/FE tests above (`test-plan.md` §4 traces each BB case to its covering automated test) |
| Manual browser RBAC/UI smoke test | PASS (executed 2026-08-18) | User performed the smoke test in a real browser across ADMIN/PM/QA/DEV/other role tiers and confirmed everything works, no issues found; `test-results.md` §8/§10 updated accordingly |
| DB-level partial unique index under real concurrency | Reviewed, not executed | `test-results.md` §8: no live-DB integration harness exists in this codebase; app-level `existsActiveByTicketId` + `DataIntegrityViolationException`→409 mapping test cover the common/backstop paths |

Final verdict (per `test-results.md` §10): **PASS** — automated-test scope and the manual
browser RBAC/UI smoke test are both green as of 2026-08-18.

## 7. Security / operations perspective

- RBAC (`MUTATE`/`VIEW_ONLY`/`NONE`) is enforced server-side in `AiQualityService`, never
  client-only; read endpoints (`GET /{id}`, `GET /`) are gated identically to write endpoints —
  `NONE` callers are blocked on read too, explicitly tested (AC-11). FE route guard/button
  gating is UX-only, per `spec-pack.md` §13.
- No new `ApiResponse<T>` envelope, Spring `MessageSource`/`.properties` i18n, or
  `@PreAuthorize` usage introduced — none exist in this codebase and none were added.
- No PII beyond existing `created_by`/`updated_by`/`deleted_by` actor identifiers, populated
  only from the authenticated caller.
- Error responses flow exclusively through `GlobalExceptionHandler` → standard
  `ErrorResponse(timestamp, status, errorCode, message, traceId)`; no stack traces reach the
  client.
- `AdminAuditLogService` wired for create/update/soft-delete (`MODULE`/`ENTITY_TYPE =
  "AI_QUALITY"`), best-effort — a broken audit write never blocks or rolls back the business
  transaction, mirroring `TicketBugMetricsService`.
- Operationally purely additive: no new batch/job/event processing; soft-deleted rows
  accumulate like other soft-deleted dim-adjacent tables, no new archival process in scope;
  rollback = new corrective migration only, `V510` never edited post-merge.

## 8. Accepted Risk
| risk | impact | owner | deadline | status | approver |
|---|---|---|---|---|---|
| (Resolved 2026-08-18) Manual browser RBAC/UI smoke test not performed | None remaining — user performed the smoke test in a real browser across ADMIN/PM/QA/DEV/other role tiers, confirmed everything works, no issues found | Independent/human reviewer | Before merge | CLOSED | User (2026-08-18) |
| BR-3 ticket↔repository check implemented as ticket↔project membership (`tbl_dim_ticket` has no `repository_id` column; strict PR/commit-based repository link would false-negative on valid, not-yet-linked tickets) | Low — matches the real data model, user-confirmed as the Recommended option over the stricter alternative | Product/QA (if requirement tightens later) | No deadline | CLOSED | User (2026-08-17, via AskUserQuestion) |
| Per-project role lookup reuse (`qaDashboardRepository.findProjectRole`) confirmed directly reusable, no fork needed | None — resolved before coding | N/A | Resolved | CLOSED | N/A |
| BR-3 FK-parentage validator placement: inline in `AiQualityService` (Option C) vs. a shared validator (Option D) | Low — single consumer today; Option D explicitly deferred, not rejected outright | N/A | Revisit if a second consumer appears | CLOSED (for this ticket) | Implementer |
| DB-level partial unique index enforcement under a real concurrent-insert race | Low — app-level `existsActiveByTicketId` check covers the common case; DB index is a backstop for the true race window only, reviewed via migration DDL + `DataIntegrityViolationException`→409 mapping test, not executed against a live race | N/A | No deadline | ACCEPTED | N/A |

## 9. Open Issues
| issue | impact | next action |
|---|---|---|
| — | — | — |

None outstanding. All previously open issues are resolved:
- (Resolved 2026-08-18) Manual dev-server RBAC/UI smoke test across ADMIN/PM/QA/DEV/other
  roles — user performed the test in a real browser and confirmed everything works, no issues
  found.
- (Formerly open, now resolved) Pagination/filter shape, ticket dropdown scoping, E2E
  requirement, REST path prefix — resolved via `spec-pack.md` §17 assumptions
  A-AI-QUALITY-9..12, confirmed by user or by codebase-convention default.
- (Resolved 2026-08-18) Two pre-existing, unrelated `TicketBugMetricsServiceTest`/
  `ControllerTest` failures surfaced during the full BE regression run — fixed at user's
  request by correcting the Mockito matcher/raw-`null` mixing in both test files; full BE
  suite now 559/559.

## 10. Human Decisions
| decision | owner | result |
|---|---|---|
| RBAC model: adopt BUG-DASHBOARD's `TicketBugMetricsService.resolveAccess()` exactly (ADMIN global→MUTATE; per-project PM/QA→MUTATE; per-project DEV→VIEW_ONLY; else→NONE, denied read+write) | User | Confirmed (`spec-pack.md` §17 A-AI-QUALITY-6) |
| Soft-delete convention: adopt BUG-DASHBOARD's `tbl_ticket_bug_metrics` shape exactly (`delete_flag`+`deleted_at`/`deleted_by`+`status`, partial unique index) | User | Confirmed (A-AI-QUALITY-7) |
| Ticket dropdown scoping in create/edit drawer: show all tickets in the repository, unfiltered (Option A) vs. pre-filter out already-tracked tickets (Option B) | User | Option A confirmed — duplicate selection surfaces the standard BR-2 409 on submit (A-AI-QUALITY-11) |
| `AdminAuditLogService` usage required for create/update/delete, requiring a `ck_access_log_module` migration widening | User (2026-08-17) | Confirmed — implemented as `MODULE`/`ENTITY_TYPE = "AI_QUALITY"` (`context.md`) |
| BR-3 ticket↔repository check: implement as ticket↔project membership (recommended) vs. a stricter PR/commit-based repository link | User (via `AskUserQuestion`, Phase 5) | Ticket↔project membership confirmed — avoids false-negatives on valid, not-yet-linked tickets (`self-review.md` §9/§11) |
| Post-implementation follow-ups: add `RoleTabs` entry for AI-QUALITY; diagnose/fix the `/access` login-kickout bug by direct comparison against `TicketBugMetricsController`/`Service` | User (2026-08-17) | Both implemented as small, focused changes rather than re-opening Phase 3/4 planning (`self-review.md` §11) |
| Validation-error localization, REST path prefix, pagination/filter shape, E2E test requirement | Default applied (only real convention/no genuine alternative in codebase) | No human confirmation needed — resolved as defaults (A-AI-QUALITY-8/-9/-10/-12) |

## 11. Source Analysis Limitations

Per `sources.md` "Source Limitations" and "Assumptions from Sources": the raw input
(`01_raw-input.md`) is a single Vietnamese technical design document with no accompanying
meeting memo, Jira ticket, or basic-design doc, and its own API/DB reference sections are
explicitly "reference only." Several conventions it names — a generic `ApiResponse<T>`
envelope, Spring `MessageSource`/`.properties`-based i18n (`LocaleResolver`,
`messages_{vi,en,ja}.properties`) — were checked against real source and found **not to exist**
in this codebase; the spec instead followed verified real conventions (`ResponseEntity<T>`/
`PageResult<T>`, FE-only i18next JSON). No design mockups/wireframes were supplied beyond the
raw input's textual UI description. RBAC and soft-delete conventions were not fully specified
by the raw input alone (it self-contradicted on DEV access) and were resolved by adopting
BUG-DASHBOARD's identical-shape feature as the reference, per explicit user direction rather
than inference.

## 12. What worked

- Adopting BUG-DASHBOARD (`TicketBugMetricsService`/`Controller`) as a structural 1:1 template
  eliminated design ambiguity for RBAC and soft-delete — no rework was needed once implementation
  began, and all three `impl-plan.md` §12 Stop/Ask conditions resolved without blocking.
- The per-project role lookup mechanism (`qaDashboardRepository.findProjectRole`) was confirmed
  directly reusable as-is with zero forking, avoiding a duplicated query the project conventions
  explicitly discourage.
- Escalating the one genuine UX trade-off (ticket dropdown scoping) to the user via
  `AskUserQuestion` with both options and consequences spelled out, instead of silently picking
  a default, produced a clean, traceable decision (Option A) with no later rework.
- Treating spec-pack/context/impact-analysis/impl-plan as living documents and updating them
  retroactively when the implementation improved on the spec (e.g. AC-AI-QUALITY-13's `search`
  filter) kept documentation and code in sync rather than letting drift accumulate silently.

## 13. What failed

An unrequested "improvement" to the `/access` endpoint — resolving `Access` strictly against
`projectId` via `requireAccess`, believing this matched BR-5's documented intent more precisely
than the reference's `requireAnyAccess` fallback — diverged from the mirrored reference
(`TicketBugMetricsController.access`) without first checking what edge case that fallback
exists to handle. The result was a real production-usage bug: any non-ADMIN user (QA global
role with DEV per-project role, or any first-render caller before the page's own effect
auto-selects and syncs a `projectId` into the URL) was force-logged-out with a 403 whenever the
page loaded with no `projectId` yet — reproduced via `RoleTabs` tab-switching, which navigates
without carrying over `?projectId=...`. Diagnosed and fixed by reverting to the reference's
exact branch (`projectId != null` → `requireAccess`, else → `requireAnyAccess`), closing the
self-inflicted regression (`self-review.md` §7 bug #1).

## 14. Candidate updates Failure Mode Index

- **Failure mode**: Diverging from a mirrored reference implementation's exact edge-case
  handling (e.g. a null/absent-parameter fallback branch) to apply a "stricter" or "more
  correct" reading of the written spec, without first identifying what real-world scenario the
  reference's existing branch was written to handle.
  **Trigger**: Implementing a "mirror pattern X" ticket and spotting what looks like an
  under-implementation or inconsistency in the reference (e.g. a javadoc claiming a check the
  code doesn't perform).
  **Prevention**: Before diverging from a mirrored reference's control flow, trace *why* that
  branch exists — check for a caller (e.g. a route-guard component, a first-render effect) that
  depends on the exact fallback behavior — rather than assuming the reference's simpler
  behavior was an oversight.
  **Detection**: A regression surfaces as a permission/auth failure (403/force-logout) for a
  caller state the new stricter code no longer tolerates, typically triggered by a navigation
  path that doesn't carry the parameter the new code newly requires.

## 15. Candidate updates Living Docs

- **BR-3-style FK-parentage validation** (repository must belong to project; ticket must
  belong to repository/project) has no existing named pattern anywhere in
  `docs/standards/backend.md`, despite being a likely-recurring need for any future feature
  built on the same `Project`→`Repository`→`Ticket` dim-hierarchy shape. Worth documenting the
  `ensureXBelongsToY`-style inline-validation pattern (with its 400-not-404 status convention)
  as a named, reusable pattern once a second consumer appears — tracked as deferred (Option D),
  not urgent yet.
- **`AuthUserContext` + per-project role resolution** (vs. the global-role-only `AppUser.Role`
  model) now has a second confirmed real-world usage (BUG-DASHBOARD + AI-QUALITY) with an
  identical `Access{MUTATE,VIEW_ONLY,NONE}`/`resolveAccess` shape. This strengthens the case
  for promoting it from `20-architecture.md`'s brief per-project role-tier footnote to a fully
  documented pattern (with the `Access` enum shape and `resolveAccess` code example) in
  `docs/standards/backend.md`, since it is proving to be a stable, repeatable convention rather
  than a one-off.

## 16. Final Verdict

- **DONE** — all 13 ACs covered by passing automated tests (36/36 AI-QUALITY BE tests, 13/13
  AI-QUALITY FE tests), both full regression suites fully green (BE 559/559, FE 330/330), and
  the manual browser RBAC/UI smoke test — the one remaining blocker — was performed by the user
  on 2026-08-18 across all role tiers (ADMIN/PM/QA/DEV/other) with no issues found. No open
  issues or unresolved accepted risks remain.
