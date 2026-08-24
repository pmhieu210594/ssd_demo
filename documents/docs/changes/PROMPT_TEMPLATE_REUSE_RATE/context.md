# Context

**Ticket ID**: PROMPT_TEMPLATE_REUSE_RATE
**Create date**: 2026-08-10
**Author**: Tech Lead (SDD Assistant)
**Update date**: 2026-08-10

## Files Read

BE (full read unless noted):
- `EDCAP_BE/src/main/java/com/sdd/platform/web/webhook/GithubWebhookController.java` (64 lines)
- `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/ingestion/GithubWebhookService.java` (480 lines, incl. `handlePullRequest` lines 158-309, `ticketKeyFromPath`/`resolveChangesFilePath`/`discoverTicketKeysFromTree` lines 420-478)
- `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/integration/ArtifactScannerSourcePort.java` (18 lines)
- `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/integration/GithubPullRequestFilesPort.java` (12 lines)
- `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/github/GithubArtifactScannerSourceAdapter.java` (167 lines)
- `EDCAP_BE/src/main/java/com/sdd/platform/domain/service/markdown/core/MarkdownParserCore.java` (505 lines)
- `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/ingestion/GithubSecurityEvidenceSnapshotService.java` (targeted read, lines ~569-616: `readBlobAsBytes`, `readBlobAsText`, `isSkippableBlobError`)
- `EDCAP_BE/src/main/java/com/sdd/platform/web/rest/PmDashboardController.java` (131 lines)
- `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/pmdashboard/PmDashboardService.java` (239 lines, incl. `requirePm` lines 197-212)
- `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/DataOpsDashboardJdbcAdapter.java` (first 120 lines)
- `EDCAP_BE/src/main/resources/db/migration/V508__add_submitted_by_to_review.sql` (1 line — confirms latest migration)
- `EDCAP_BE/src/main/resources/db/migration/V4__init_shema_v2.sql` (targeted: lines 195-224 `tbl_dim_phase`/`tbl_dim_artifact_type`, 676-705 `tbl_fact_test_run`, seed rows to ~1454)
- `EDCAP_BE/src/test/java/com/sdd/platform/application/usecase/ingestion/GithubWebhookServiceTest.java` (first 80 lines — mock/setup pattern)
- `EDCAP_BE/src/test/UnitTest/java/com/sdd/platform/application/usecase/pmdashboard/PmDashboardServiceTest.java` (first 80 lines — mock/setup pattern, permission test pattern)

FE (full read unless noted):
- `EDCAP_FE/src/pages/pm-dashboard/PMDashboardPage.tsx` (238 lines)
- `EDCAP_FE/src/pages/pm-dashboard/components/TicketDetailDrawer.tsx` (targeted: `EqsSummaryCard` lines 347-399, render call line 747)
- `EDCAP_FE/src/pages/pm-dashboard/utils.ts` (112 lines)
- `EDCAP_FE/src/utils/number-format.ts` (68 lines)
- `EDCAP_FE/src/pages/pm-dashboard/hooks/usePmDashboardFilters.ts` (47 lines)
- `EDCAP_FE/src/lib/api.ts` (targeted: `pmDashboard` namespace from line 1608, DTO interfaces lines 505-565)
- `EDCAP_FE/src/pages/pm-dashboard/types.ts` (24 lines, full)

Spec artifacts (full read): `spec-pack.md`, `open-issues.md`, `03_source-availability.md`, `raw/rules.md`, `raw/template-usage-requirement.md`.

**Not read**: any `.env`/secrets/credentials/key files (none needed); `EDCAP_BE/target/**`, `node_modules`, `dist` (build output, not source of truth). No dedicated unit test file exists yet for `MarkdownParserCore` itself (searched `src/test/**` — only purpose-specific parser tests like `SelfReviewMarkdownParserTest` were found, none for the generic core parser) — flagged below as a test-coverage gap, not an implementation blocker.

## Similar Implementations

| feature | file/path | relevance |
|---|---|---|
| GitHub PR webhook ingestion (extend, do not replace) | `GithubWebhookService.handlePullRequest()` (lines 158-309) | The exact function the new template-validation logic must plug into. `changedFilePaths` (resolved at line 208 via `pullRequestFilesPort.listChangedFilePaths(repoKey, prNumber)`) is already the full list of PR-changed file paths — reuse this list directly instead of re-fetching it. `revision` (line 225, via `revisionFromPullRequest`) is the exact PR-head revision already used to fetch security evidence (line 231) — reuse the same revision for reading both the target file and its template content, per OI-7. |
| Ticket-scoped file path parsing | `ticketKeyFromPath` (lines 434-453), `resolveChangesFilePath` (lines 455-464) | Shows the exact `/changes/<ticketKey>/<fileName>` path convention already used by production code (substring search on `/changes/`, not a fixed literal prefix). `resolveChangesFilePath` is the existing pattern for resolving a ticket+filename to a full repo tree path — model the new "find template counterpart of a changed file" lookup on this, not a fresh implementation. |
| Security evidence best-effort per-file fetch | `GithubSecurityEvidenceSnapshotService.readBlobAsBytes`/`readBlobAsText` (lines 572-604) | Exact reference for "read one blob by sha, catch `WebClientResponseException`, classify via `isSkippableBlobError`, return null/empty + log warn on skippable status, rethrow otherwise." New per-file template validation should follow this same try/catch shape file-by-file, not fail the whole PR on one bad file. |
| PM Dashboard controller/service/permission gate | `PmDashboardController.summary()` (lines 29-43), `PmDashboardService.requirePm()` (lines 197-212) | Exact reference for adding the new `template-usage` endpoint: `@RequestParam(required=false)` args + `@CurrentUser AuthUserContext caller` in the controller, `service.requirePm(caller, projectId)` as the very first line inside the new service method (throws `ForbiddenException("Component.Permission.Denied")` on failure — this exact message string must be reused, not a new one). |
| Unit test setup for the two target classes | `GithubWebhookServiceTest.java` (constructor-mock style, `Mockito.mock(X.class)` + manual field assignment, no `@ExtendWith(MockitoExtension.class)`); `PmDashboardServiceTest.java` (`@ExtendWith(MockitoExtension.class)` + `@Mock` fields + AssertJ `assertThat`/`assertThatThrownBy`) | These are two **different** mocking styles already coexisting in the codebase for these two exact classes. New tests for webhook-side logic should follow `GithubWebhookServiceTest`'s manual-mock style (consistent with its neighbors in `ingestion` package); new tests for `PmDashboardService` additions should follow `PmDashboardServiceTest`'s `@Mock`/AssertJ style (consistent with its neighbors in `pmdashboard` package). Do not mix styles within one test class. |

## Patterns To Follow

- Reuse `changedFilePaths` and `revision` already computed inside `handlePullRequest` — do not add a second GitHub API round trip to re-list PR files or re-resolve the revision.
- Use `ArtifactScannerSourcePort.readBlob(repositoryFullName, sha)` (via `listTree` to get the sha for a given path) for both the changed file and its resolved template file, at the same revision.
- Use `MarkdownParserCore.parse(content, sourcePath)` → `.sections()` → compare `title`/`canonicalKey`/`level` and order only, for both PASS/FAIL determination.
- Classify per-file GitHub fetch failures with the same 401/403/404-skippable rule as `isSkippableBlobError` (skip + `log.warn` + continue; anything else → propagate).
- New DB writes go through a dedicated adapter using `NamedParameterJdbcTemplate` (see `DataOpsDashboardJdbcAdapter.java`), inside its own `TransactionTemplate`, run **after** all GitHub HTTP calls complete (never interleave external HTTP with an open DB transaction — per `docs/.claude/rules/20-architecture.md`).
- New PM Dashboard endpoint: same controller (`PmDashboardController`), same service (`PmDashboardService`), `@CurrentUser AuthUserContext caller` param, `service.requirePm(caller, projectId)` as the first line of the new service method, `ForbiddenException("Component.Permission.Denied")` message reused verbatim.
- FE: add the new method inside the existing `endpoints.pmDashboard` object in `api.ts` (same file as `PmDashboardSummary`/`PmDashboardOptions` interfaces, lines 505-565) — build the query string with `URLSearchParams`, call `api.get<T>(...)`, matching every sibling method (`summary`, `insights`, `tickets`, ...).
- FE: new TanStack Query hook's `enabled` must reproduce `PMDashboardPage.tsx`'s actual full condition: `optionsQuery.isSuccess && Boolean(filters.projectId) && Boolean(filters.repositoryId) && isMatchingDashboard` — not a simplified two-flag version.
- FE: new percentage display needs a **new** small helper (`totalCheckCount === 0 → "-"`) — do not repurpose `formatScore` (null-guard only) or the generic `number-format.ts` helpers (no zero-guard at all).
- FE: model the new per-phase card visually on `EqsSummaryCard` (`TicketDetailDrawer.tsx:347-399`) for consistency with the drawer's existing per-metric card look, even though it now lives on `PMDashboardPage.tsx` (per OI-1).

## Forbidden Patterns

- Adding a second GitHub HTTP client/adapter — `ArtifactScannerSourcePort` + `GithubArtifactScannerSourceAdapter` is the single sanctioned abstraction; do not call GitHub REST/GraphQL directly from new code.
- Writing a second markdown/header-structure parser — `MarkdownParserCore` already returns exactly `title`/`canonicalKey`/`level`/`startLine`/`endLine`; do not hand-roll regex-based header parsing.
- Hardcoding the 8 phases or their files in a Java `switch`/enum — the raw requirement (§5) and `spec-pack.md` (§6.1) both require this to stay data-driven via `tbl_dim_phase`/`tbl_dim_artifact_type`; a hardcoded mapping would fail this requirement outright.
- Any ad-hoc `ResponseEntity` status code in `PmDashboardController` for the new endpoint — `GlobalExceptionHandler` is the single exception→HTTP mapping point (per `docs/.claude/rules/30-security.md`).
- Raw `fetch()` anywhere in FE outside `lib/api.ts`.
- New Redux slice or Zustand store for this feature's data — it is server data, must go through TanStack Query per `docs/.claude/rules/20-architecture.md`.
- `@Data` on any new domain model, or field injection (`@Autowired` on a field) on any new service/adapter — project standard is constructor injection + `@Builder @Getter @Setter` only.
- Sharing one `TransactionTemplate`/transaction across the GitHub HTTP calls and the new DB counter writes — must be separate, with HTTP calls outside any open transaction.

## Existing Methods / Non-existent Methods

**Existing (verified in source, safe to call from the correct layer):**

| method | signature | path | note |
|---|---|---|---|
| `ArtifactScannerSourcePort.resolveRevision` | `ResolvedRevision resolveRevision(String repo, String ref)` | `ArtifactScannerSourcePort.java` | |
| `ArtifactScannerSourcePort.listTree` | `Map<String, GitHubTreeEntry> listTree(String repo, String revisionSha)` | same | |
| `ArtifactScannerSourcePort.readBlob` | `byte[] readBlob(String repo, String sha)` | same | |
| `GithubPullRequestFilesPort.listChangedFilePaths` | `List<String> listChangedFilePaths(String repositoryFullName, int pullRequestNumber)` | `GithubPullRequestFilesPort.java` | Already called at `GithubWebhookService.java:208`; reuse the resulting list, don't re-fetch. |
| `MarkdownParserCore.parse` | `MarkdownDocument parse(String content, String sourcePath)` → `.sections()` → `List<MarkdownSection>` | `MarkdownParserCore.java` | `MarkdownSection(title, canonicalKey, level, body, startLine, endLine)`. |
| `PmDashboardService.requirePm` | `void requirePm(AuthUserContext caller, UUID projectId)` | `PmDashboardService.java:197` | Public method, safe to call from a new method on the same service. |
| `endpoints.pmDashboard.summary/insights/tickets/options/access` | see `api.ts:1608+` | `api.ts` | Pattern to clone for `templateUsage`. |

**Exists but NOT callable across class boundaries as-is (real reuse constraint — decide in impl-plan.md, do not assume it can be called):**

| method | actual visibility | path | constraint |
|---|---|---|---|
| `ticketKeyFromPath(String path)` | `private static` on `GithubWebhookService` | `GithubWebhookService.java:434` | Cannot be called from a new class. New validation logic must either live inside `GithubWebhookService`, or the method must be promoted (e.g. `static` on a shared utility) as an explicit Phase 3 decision. |
| `isSkippableBlobError(WebClientResponseException ex)` | `private` **instance** method on `GithubSecurityEvidenceSnapshotService` (not static) | `GithubSecurityEvidenceSnapshotService.java:606` | Cannot be called from a new class either. New code must replicate the identical 401/403/404 classification (two lines), not reference this method. |
| `resolveChangesFilePath(...)` | `private static` on `GithubWebhookService` | `GithubWebhookService.java:455` | Same constraint as `ticketKeyFromPath` — useful as a *pattern* to copy, not as a directly callable method from outside. |

**Non-existent methods an AI implementer is likely to invent — do not use:**

| invented method/API | why it looks plausible | reality |
|---|---|---|
| `GET /api/template-usage/statistics` | Literal text from the raw requirement (`raw/template-usage-requirement.md` §7) | Does not exist and will not be created — the real, spec-pack-approved path is `GET /api/v1/pm/dashboard/template-usage`, added to the **existing** `PmDashboardController`. |
| `formatScore(rate, { zeroGuard: true })` or similar overload | `formatScore` exists and looks like the natural place to add a flag | `formatScore` (`pm-dashboard/utils.ts`) has no zero-guard parameter/overload today; do not assume one — write a new small formatter instead. |
| `artifactScannerSourcePort.readTemplate(...)` / any "template-aware" port method | Feels like a natural addition given the port's existing `readBlob`/`listTree`/`resolveRevision` | Does not exist. Template content is read the same way as any other file: `listTree` → find sha by path → `readBlob`. |
| `MarkdownParserCore.compare(a, b)` / `.diffStructure(...)` | The task is literally "compare two documents' structure" | Does not exist. `MarkdownParserCore` only parses one document into sections; the comparison logic itself (structure-equality check) must be written new for this ticket. |
| `PmDashboardService.requireTemplateUsageAccess(...)` or a new dedicated permission method | Feature-specific naming looks tidy | Does not exist and is unnecessary — reuse `requirePm(caller, projectId)` unchanged, exactly like every other read method on this service. |

## Mapping

| display/item | internal value | source | note |
|---|---|---|---|
| Phase display name | `phase_name` | `tbl_dim_phase` | Canonical source; already used elsewhere on PM Dashboard (`PmDashboardTicketRow.phaseName`, `PmDashboardPhaseOption`). |
| Phase display order | `phase_order` | `tbl_dim_phase` | Drives stable, extensible phase ordering — do not hardcode a phase list/order in code. |
| File → phase/artifact-type mapping | `artifact_type_code`, `default_file_name` (FK → `tbl_dim_phase`) | `tbl_dim_artifact_type` | Data-driven mechanism satisfying "add/remove phase or file without code change" (raw requirement §5 / spec-pack §5). 13 existing rows confirmed in `V4__init_shema_v2.sql`; V510 (this ticket) adds 3 more (`OPEN_ISSUES`, `CONTEXT`, `CODEX_REVIEW`) per OI-3. |
| Counters | `total_check_count`, `template_match_count` | new `tbl_fact_template_usage_stat` (V510) | Scoped by `project_id`, `repository_id`, `phase_id` (all FKs). Per OI-2. |
| API response field | `usageRate` | computed, not stored | `templateMatchCount / totalCheckCount * 100`; `totalCheckCount === 0` → API/FE must render `-`, never `0`/`NaN`/divide-by-zero. |
| Unmapped file (no dimension row) | — | `tbl_dim_artifact_type` has no `IMPACT_ANALYSIS` row and V510 does not add one | Confirmed expected/accepted behavior per spec-pack §6.4 — such a file is excluded from statistics, not an error and not a FAIL. |

## Ticket-Specific Business Rules

Restated from `spec-pack.md` §6.1 (the single source of truth — do not reinterpret or extend beyond this list):

1. Validation triggers only inside the existing `pull_request` webhook flow (`handlePullRequest`) — no new event type.
2. Only PR-changed files matching the `/changes/<ticketKey>/` scope (reuse `changedFilePaths` + the same substring convention as `ticketKeyFromPath`) are validated.
3. Template lookup priority: `documents/standards/templates/{file-name}` → `_ticket-template/{file-name}` → `_light-ticket-template/{file-name}`; first match wins; no match → skip silently, no error.
4. Comparison is structure-only (header title/level/order via `MarkdownParserCore`); never compare body/content/bullets/user-entered values.
5. PASS/FAIL is binary per file.
6. Per PR processed: `total_check_count += 1` always, on the resolved (project, repository, phase) row; `template_match_count += 1` only on PASS.
7. A file with no matching `tbl_dim_phase`/`tbl_dim_artifact_type` row is excluded from statistics — not an error, not a FAIL (confirmed applies to `IMPACT_ANALYSIS` today).
8. Template content is read from the **same PR head revision** as the changed file (even if the PR itself edits the template).
9. Per-file GitHub fetch failure: 401/403/404 → skip that file, `log.warn`, continue; any other error → fail/propagate (same as `isSkippableBlobError`'s classification, replicated — not called, per the visibility constraint above).
10. `GET /api/v1/pm/dashboard/template-usage` is PM-role-gated via `requirePm(caller, projectId)`, scoped by `projectId` + `repositoryId`, identical to every sibling PM Dashboard endpoint.

## Implementation Notes

- Insert the new validation loop inside `handlePullRequest`, after `changedFilePaths` is resolved (line 208) and after `revision` is computed (line 225) — reuse both, do not recompute.
- `ticketKeyFromPath`/`isSkippableBlobError` cannot be called across classes as-is (see visibility table above). Decide explicitly in `impl-plan.md` whether the new logic lives inside `GithubWebhookService` (simplest, no refactor) or in a new collaborator class (requires promoting/duplicating the relevant logic) — do not silently assume either without recording the decision.
- DB counter writes must be their own adapter method with its own `TransactionTemplate`, executed after all GitHub HTTP calls for that PR complete — do not open a transaction spanning the blob-read loop.
- New migration is `V510__*.sql` (confirmed next after `V508__add_submitted_by_to_review.sql`); exact DDL is already specified in `spec-pack.md` §12 — copy it, do not redesign columns/constraints here.
- FE: declare the new response type (`PmDashboardTemplateUsageRow` or similar, name to finalize in impl-plan.md) next to `PmDashboardSummary`/`PmDashboardOptions` in `api.ts`, not in `pages/pm-dashboard/types.ts` (that file is page-local UI/filter types only).
- FE: place the new section on `PMDashboardPage.tsx` after `<AllTicketsTable>` (line 212), per OI-1 — not inside `TicketDetailDrawer.tsx` as the raw requirement originally said.

## Review Notes

- Confirm the new validation loop reuses `changedFilePaths`/`revision` from the existing flow and does not add a duplicate GitHub API call.
- Confirm every per-file GitHub fetch failure path is 401/403/404 → skip+warn+continue, and everything else → fail — matching the `isSkippableBlobError` classification exactly (two status-code checks), even though the method itself isn't reused directly.
- Confirm the new DB write happens in its own `TransactionTemplate`, outside/after the GitHub HTTP calls.
- Confirm `requirePm(caller, projectId)` is the first line of the new service method, and the `ForbiddenException` message string is exactly `"Component.Permission.Denied"` (must match, not a new custom message).
- Confirm no new hardcoded phase/file switch exists anywhere — all phase/file logic must route through `tbl_dim_phase`/`tbl_dim_artifact_type` lookups.
- Confirm FE `usageRate` rendering shows `-` (not `0`, not `NaN`) when `totalCheckCount === 0`, and that this uses a **new** formatter, not a modified `formatScore`.
- Confirm the new TanStack Query's `enabled` matches `PMDashboardPage.tsx`'s real gating condition (4 flags), not a simplified version.
- Confirm no file under `documents/standards/templates/**` is ever treated as a *target* to validate (only as a *source* of comparison) — this would be an inverted-role bug.

## Test Notes

- Backend: follow `GithubWebhookServiceTest.java`'s existing manual-mock style (`Mockito.mock(X.class)` fields set in `@BeforeEach`, no `MockitoExtension`) for any new test class touching `GithubWebhookService`/webhook-side logic, for consistency with sibling tests in the `ingestion` package.
- Backend: follow `PmDashboardServiceTest.java`'s style (`@ExtendWith(MockitoExtension.class)`, `@Mock` fields, AssertJ `assertThat`/`assertThatThrownBy`) for any new test touching `PmDashboardService`, including a `templateUsage_requiresPmRole()` test mirroring the existing `summary_requiresPmRole()` test shape (throws `ForbiddenException`, message `"Component.Permission.Denied"`).
- Note: `src/test` has two coexisting test source roots in this repo — `src/test/java/...` (e.g. `GithubWebhookServiceTest`) and `src/test/UnitTest/java/...` (e.g. `PmDashboardServiceTest`, `PmDashboardControllerTest`). Place new test classes in whichever root already holds their sibling class for the same production class, not by guessing.
- No existing dedicated unit test for `MarkdownParserCore` was found (only per-purpose parsers like `SelfReviewMarkdownParserTest` exist). The new structure-comparison logic built on top of `MarkdownParserCore` needs its own test coverage from scratch — do not assume equivalent coverage already exists.
- Required boundary cases per spec-pack: `totalCheckCount == 0` (API `-`, not 0/NaN), template not found (skip, no error, no FAIL), file matches no dimension row (excluded from stats, not FAIL), 401/403/404 on blob fetch (skip+continue), non-skippable fetch error (fail/propagate), PR that itself modifies the template file (must still read template at PR head revision, per rule 8).
