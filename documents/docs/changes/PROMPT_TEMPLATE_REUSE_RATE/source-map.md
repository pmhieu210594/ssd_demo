# Source Map

**Ticket ID**: PROMPT_TEMPLATE_REUSE_RATE
**Create date**: 2026-08-10
**Author**: Tech Lead (SDD Assistant)
**Update date**: 2026-08-10

## Target Area

Three independent surfaces, all extending existing components (no new controller/service classes, per `spec-pack.md` §11 / OI-4):

1. **BE — webhook ingestion**: `com.sdd.platform.application.usecase.ingestion.GithubWebhookService` (`handlePullRequest`), reading via `com.sdd.platform.application.port.out.integration.ArtifactScannerSourcePort` and `GithubPullRequestFilesPort`, structure-parsing via `com.sdd.platform.domain.service.markdown.core.MarkdownParserCore`.
2. **BE — persistence + API**: new `tbl_fact_template_usage_stat` (migration `V510`), a new read adapter (JDBC, pattern from `DataOpsDashboardJdbcAdapter`), a new method on `com.sdd.platform.application.usecase.pmdashboard.PmDashboardService`, a new endpoint on `com.sdd.platform.web.rest.PmDashboardController`.
3. **FE — PM Dashboard**: new query/endpoint method in `EDCAP_FE/src/lib/api.ts` (`endpoints.pmDashboard`), new display section in `EDCAP_FE/src/pages/pm-dashboard/PMDashboardPage.tsx`.

## Entry Points

| entry | path | note |
|---|---|---|
| `POST /api/v1/webhooks/github` (`X-GitHub-Event: pull_request`) | `GithubWebhookController.receive()` (`web/webhook/GithubWebhookController.java:36`) | Raw `byte[]` body for exact-bytes HMAC verification; routes to `GithubWebhookService.handle()`. Not modified structurally — new logic is added *inside* the downstream `handlePullRequest`. |
| `GithubWebhookService.handle(byte[] body, String signature, String event, String delivery)` | `GithubWebhookService.java:~100-125` (event `switch`) | `case "pull_request" -> handlePullRequest(payload, deliveryId)` (line 114). |
| `GET /api/v1/pm/dashboard/template-usage` (new) | `PmDashboardController` (new method, alongside `summary`/`insights`/`tickets` at lines 29-70+) | Params: `projectId` (UUID, required per spec-pack §11), `repositoryId` (UUID, required); `@CurrentUser AuthUserContext caller`. |
| `endpoints.pmDashboard.templateUsage(...)` (new) | `EDCAP_FE/src/lib/api.ts` (new method inside object starting line 1608) | Mirrors `summary`/`insights`/`tickets` shape exactly. |

## Call Flow

| caller | callee | note |
|---|---|---|
| `GithubWebhookController.receive` | `GithubWebhookService.handle` | Existing, unchanged. |
| `GithubWebhookService.handle` | `GithubWebhookService.handlePullRequest` | Existing, unchanged (line 114). |
| `GithubWebhookService.handlePullRequest` | `GithubPullRequestFilesPort.listChangedFilePaths(repoKey, prNumber)` | Existing (line 208) — **reuse this result**, the new validation loop iterates the same `changedFilePaths` list, does not call it again. |
| `GithubWebhookService.handlePullRequest` | `revisionFromPullRequest(pullRequest, sourceBranch)` | Existing (line 225) — **reuse this `revision` value** for both target-file and template-file reads. |
| *(new)* per-file validation step, called from `handlePullRequest` after line 225/235 | `ArtifactScannerSourcePort.resolveRevision` → `.listTree` → `.readBlob` (×2: changed file, resolved template) | New call, follows the exact pattern already used at lines 247-248 for the fallback-tree-scan path. |
| *(new)* per-file validation step | `MarkdownParserCore.parse(content, sourcePath).sections()` (×2) | New call; compare the two `List<MarkdownSection>` results (title/canonicalKey/level/order) → PASS or FAIL. |
| *(new)* per-file validation step | new read/write adapter → `tbl_fact_template_usage_stat` upsert (`total_check_count += 1`, `template_match_count += 1` on PASS) | New call; own `TransactionTemplate`, executed after all GitHub HTTP calls for the PR finish, not interleaved with them. |
| `PmDashboardController` (new endpoint) | `PmDashboardService.requirePm(caller, projectId)` → new `PmDashboardService` method → new persistence-port method | Mirrors `PmDashboardController.summary` → `PmDashboardService.summary` (lines 29-43) exactly. |
| `PMDashboardPage.tsx` (new query, new component) | `endpoints.pmDashboard.templateUsage({ projectId, repositoryId })` via a new `useQuery` | Mirrors the existing `summaryQuery`/`ticketsQuery` pattern, including the real `enabled` condition (`optionsQuery.isSuccess && Boolean(filters.projectId) && Boolean(filters.repositoryId) && isMatchingDashboard`). |

## Data Flow

1. GitHub PR webhook → `handlePullRequest` resolves `repoKey`, `prNumber`, `changedFilePaths`, `revision` (all pre-existing).
2. **(new)** For each path in `changedFilePaths` matching `/changes/<ticketKey>/<fileName>`: resolve template candidate via the 3-tier priority lookup (`documents/standards/templates/{fileName}` → `_ticket-template/{fileName}` → `_light-ticket-template/{fileName}`) against the same revision's tree; if none found, skip (no DB write for that file).
3. **(new)** If a template is found: read both blobs (target file + template) at `revision`; parse both with `MarkdownParserCore`; compare header title/level/order only → PASS or FAIL.
4. **(new)** Resolve the file's phase via its path → `tbl_dim_artifact_type` → `tbl_dim_phase`; if no matching row, exclude from statistics (no DB write, not a FAIL).
5. **(new)** Upsert `tbl_fact_template_usage_stat` row keyed by `(project_id, repository_id, phase_id)`: `total_check_count += 1` always; `template_match_count += 1` only on PASS.
6. **(new, separate request path)** FE loads `PMDashboardPage.tsx` with `projectId`+`repositoryId` filters set → `templateUsageQuery` fires → `GET /api/v1/pm/dashboard/template-usage` → `PmDashboardService` (after `requirePm` gate) → new persistence read → aggregated per-phase rows (`phaseCode`, `phaseName`, `totalCheckCount`, `templateMatchCount`, `usageRate`) → rendered in the new section.

## Test Map

| production code | existing test | new test needed |
|---|---|---|
| `GithubWebhookService.handlePullRequest` | `GithubWebhookServiceTest.java` (manual-mock style, no `MockitoExtension`) | Add cases for: template found+PASS, template found+FAIL, template not found (skip), file maps to unknown dimension (excluded), blob fetch 404 (skip+continue), blob fetch 500 (fail). |
| `MarkdownParserCore` (reused as-is) | **None found** (`src/test/**` only has purpose-specific parsers, e.g. `SelfReviewMarkdownParserTest`) | The new structure-comparison logic sitting on top of it needs full new coverage; do not assume existing coverage protects this ticket's usage. |
| `PmDashboardService` (new method) | `PmDashboardServiceTest.java` (`@ExtendWith(MockitoExtension.class)`, `@Mock`, AssertJ) | Add `templateUsage_requiresPmRole()` mirroring `summary_requiresPmRole()`; add a happy-path case mirroring `summary_allowsProjectRolePmEvenWhenSystemRoleIsDifferent()`. |
| `PmDashboardController` (new endpoint) | `PmDashboardControllerTest.java` (same `UnitTest` root as `PmDashboardServiceTest`) | Add `@WebMvcTest`-slice case for the new route, per `docs/.claude/rules/40-testing.md`. |
| New JDBC adapter for `tbl_fact_template_usage_stat` | None (new file) | New test class; no production DB in unit tests per `docs/.claude/rules/40-testing.md` — mock or use an integration-test track if one exists for `DataOpsDashboardJdbcAdapter`'s sibling. |
| FE `endpoints.pmDashboard.templateUsage` + new formatter + new section component | Not yet located (FE test conventions for `pm-dashboard` not inspected in this pass) | Vitest + Testing Library per `docs/.claude/rules/40-testing.md`; must cover the `totalCheckCount === 0 → "-"` boundary explicitly. |

## Unknown Source Areas

- Exact FE Vitest test file(s), if any, for `pm-dashboard` components/hooks were not located in this pass — needs a targeted search before Phase 3 test planning (`test-plan.md`) is finalized.
- Whether `DataOpsDashboardJdbcAdapter` (the JDBC pattern reference) has an existing integration-test track with a real DB, or is unit-tested with a mocked `NamedParameterJdbcTemplate` — only the first 120 lines of the adapter itself were read, not its test file.
- Whether promoting `ticketKeyFromPath`/`isSkippableBlobError` out of `private` (if the impl-plan decision goes that way) would trip any existing test that asserts on class internals via reflection — not checked, since the promotion decision itself is deferred to Phase 3.
