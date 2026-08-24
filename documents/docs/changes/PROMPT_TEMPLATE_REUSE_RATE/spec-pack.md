# Spec Pack

**Ticket ID**: PROMPT_TEMPLATE_REUSE_RATE
**Create date**: 2026-08-10
**Author**: SDD Analyst
**Update date**: 2026-08-17 (Phase 5 round 3 — Codex automated review response: OI-15/16/17 added)

## 1. Context / Purpose

The platform currently has no measurement of how closely ticket documents (spec-pack, context, impact-analysis, review-checklist, self-review, codex-review, test-plan, test-results, blackbox-testcases, test-data, report) follow the standard templates under `documents/docs/standards/templates/`. Reviewers and PM stakeholders cannot see, per SDD phase, whether teams are actually filling in documents with the expected structure.

This feature adds automatic, structure-only validation of ticket documents whenever a GitHub Pull Request is processed, aggregates PASS/FAIL results into per-phase counters scoped by project and repository, exposes them via a read API, and displays them on the PM Dashboard page.

## 2. Scope

### 2.1. Within range

- Detecting ticket document files changed in a PR under a ticket's working directory, by reusing the existing `ticketKeyFromPath()` substring-based `/changes/` detection (already tolerant of the real repo layout `documents/docs/changes/{ticket-id}/...`).
- Resolving the matching standard template using the priority order: `documents/docs/standards/templates/{file-name}` → `.../_ticket-template/{file-name}` → `.../_light-ticket-template/{file-name}`.
- Comparing only header structure (name, level, order) between the changed file and its template, ignoring body content.
- Recording PASS/FAIL outcomes as incrementing counters (`total_check_count`, `template_match_count`) grouped by project, repository, and phase.
- Adding the 3 missing `tbl_dim_artifact_type` rows (`OPEN_ISSUES`, `CONTEXT`, `CODEX_REVIEW`) so all 8 phases are fully covered — in scope for this ticket.
- A new read API returning per-phase usage statistics, scoped by `projectId`/`repositoryId`.
- A new "Template Usage by Phase" section on `PMDashboardPage.tsx`, rendered immediately after `AllTicketsTable` and before `TicketDetailDrawer`, showing phase name, total/match counts, and usage rate.
- Extensible phase/file configuration with no core-logic change required to add/remove a phase or a file within a phase.

### 2.2. Out of range

- Any validation of document *content* (business meaning, completeness of fields, correctness of values) — out of scope; only header structure is checked.
- Retroactive backfill of statistics for PRs merged before this feature ships.
- Any display of template-usage statistics inside `TicketDetailDrawer.tsx` — superseded by the `PMDashboardPage.tsx` placement decision.
- Changes to the webhook's ticket-key derivation or HMAC verification logic (reused as-is).

## 3. Terminology

| terms | meaning | notes |
|---|---|---|
| Phase | An SDD workflow stage (Spec Pack, Working Files Initialization, Implementation Plan, Review Checklist, Implementation and Review, Test Plan and Results, Blackbox Test, Report) | Backed by existing `tbl_dim_phase` rows `'1'..'8'` |
| Template | The canonical markdown file for a given ticket document, resolved by the 3-tier priority path lookup | See Section 6.1 rule 2 |
| Header structure | The ordered list of `(header name, header level)` tuples in a markdown document, ignoring body text | Produced by `MarkdownParserCore.parse().sections()` |
| PASS / FAIL | Result of comparing a changed file's header structure against its resolved template's header structure | Exact match required |
| total_check_count | Number of times a file belonging to a phase was checked against its template | Incremented **exactly once per PR**, at the moment the PR's `pull_request` webhook fires with `action=closed` and `merged=true` — never on `opened`/`synchronize`/`reopened`, and never more than once per PR even though the PR's cumulative diff is re-read at close time. Resolved per `OI-PROMPT_TEMPLATE_REUSE_RATE-14`. This guarantee is scoped to **distinct lifecycle events** only; it does not cover GitHub redelivering the identical `closed`+`merged=true` event (webhook transport is at-least-once, not exactly-once), which would still double-count. Accepted as a known, unmitigated risk — see `OI-PROMPT_TEMPLATE_REUSE_RATE-15` |
| template_match_count | Number of those checks that resulted in PASS | |
| usageRate | `templateMatchCount / totalCheckCount * 100`, rounded to exactly 1 decimal place (e.g. `66.7`, `100.0`, `0.0`); `null` (API) / `-` (FE) if `totalCheckCount == 0` | Resolved per OI-10 |

## 4. As-Is

- `GithubWebhookController` / `GithubWebhookService.handlePullRequest()` derives the ticket key from changed file paths (via `ticketKeyFromPath()`) and triggers `ArtifactScannerService.scan(...)` for artifact presence tracking, but performs no structural comparison against templates.
- `ArtifactNormalizer` (a simpler H2/H3-only markdown parser used elsewhere) and `MarkdownParserCore` (a fuller H1–H6 parser with section-level/order capture) both exist, but neither is wired into any template-compliance check today.
- `tbl_dim_phase` and `tbl_dim_artifact_type` exist and already model 8 of the required phases and 13 of the ~16 required files, but are not used for any usage-rate metric — only for artifact-presence scanning.
- No API or UI exists to display template-compliance statistics.

## 5. To-Be

- On every `pull_request` webhook event, after HMAC verification, for each changed file path under a ticket's working directory (detected via the existing `ticketKeyFromPath()` `/changes/` substring logic): resolve its phase/artifact type from `tbl_dim_artifact_type`, resolve its matching template via the 3-tier priority lookup, fetch both the changed file's and the template's content from the **same PR head revision** via `ArtifactScannerSourcePort.readBlob(...)`, parse both with `MarkdownParserCore.parse(content, sourcePath)`, and compare the ordered `(header name, level)` tuples from `sections()`.
- If no template is found at any of the 3 priority paths, skip that file silently (no error, no counter increment).
- If a single file's blob/tree fetch fails with `401/403/404`, reuse the existing `isSkippableBlobError` pattern: skip that file (log `warn`, no counter change) and continue processing the rest of the PR; any other error propagates/fails as today.
- If a template is found and both contents were fetched successfully, increment `total_check_count` for that file's `(project, repository, phase)`; if the header structures match exactly, additionally increment `template_match_count`.
- Persist counters in the new dedicated table `tbl_fact_template_usage_stat`, keyed by `(project_id, repository_id, phase_id)`.
- A new Flyway migration `V510` adds this table and the 3 missing `tbl_dim_artifact_type` rows (`OPEN_ISSUES`, `CONTEXT`, `CODEX_REVIEW`).
- Expose `GET /api/v1/pm/dashboard/template-usage?projectId=...&repositoryId=...` returning one row per phase with `phaseCode, phaseName, totalCheckCount, templateMatchCount, usageRate`.
- Render a new "Template Usage by Phase" section on `PMDashboardPage.tsx`, placed immediately after `<AllTicketsTable />` and before `<TicketDetailDrawer />`, scoped to the page's currently selected `filters.projectId`/`filters.repositoryId`, showing `-` for phases with `totalCheckCount == 0`.

## 6. Detailed specification

### 6.1. Business Rules

1. Only files whose path matches the existing `ticketKeyFromPath()` `/changes/` substring detection are eligible for validation — this transparently covers the real repository convention (`documents/docs/changes/{ticket-id}/...`) without a new, separately-maintained path rule.
2. Template resolution priority: (1) `documents/docs/standards/templates/{file-name}`, (2) `documents/docs/standards/templates/_ticket-template/{file-name}`, (3) `documents/docs/standards/templates/_light-ticket-template/{file-name}`. First match wins.
3. If no template is found in any of the 3 locations, the file is skipped: no PASS/FAIL is recorded, no counter is incremented, and no error is raised or logged as a failure.
4. Comparison considers only header name, header level (`#`–`######`), and header order. Body text, bullet lists, tables, and user-entered values are never compared.
5. A file is PASS only if its ordered header (name, level) sequence is identical to the template's; any difference in name, level, or order is FAIL.
6. Each matched file increments its `(project_id, repository_id, phase_id)` row's `total_check_count` by 1; a PASS additionally increments `template_match_count` by 1. Both counters are monotonically increasing (no decrement, no historical overwrite). This increment runs **exactly once per PR** — see Business Rule #11 for the triggering condition — never once per webhook delivery. "Exactly once" here means once per distinct lifecycle event (opened/synchronize/reopened vs. closed); it does **not** guard against GitHub redelivering the identical `closed`+`merged=true` event, which is an accepted, unmitigated risk (`OI-PROMPT_TEMPLATE_REUSE_RATE-15`).
7. Phase-to-file membership must be sourced from a data-driven mapping (`tbl_dim_phase` + `tbl_dim_artifact_type`), not a hard-coded Java `switch`/`enum` map, so that adding/removing a phase or a file requires only a data change. This rule governs **file → phase** mapping only. It does not extend to which `tbl_dim_phase` rows are in scope for this feature's statistics response: `tbl_dim_artifact_type` is shared with unrelated features (e.g. phase `0-A` has artifact-type rows for AI-safety file scanning, unrelated to template usage), so a phase's mere presence/absence of artifact-type rows is not a reliable proxy for "in scope for this feature." The in-scope phase set is the explicit, spec-fixed boundary in the Terminology section (`tbl_dim_phase` codes `'1'..'8'`) and may be expressed as a literal list at the query level (see `OI-PROMPT_TEMPLATE_REUSE_RATE-13`).
8. `usageRate` is computed at read time as `templateMatchCount / totalCheckCount * 100`; when `totalCheckCount == 0` the API/UI must represent it as `-`, never `0` or `NaN`.
9. Any new user-facing text introduced by this ticket's UI (section titles, descriptions, table headers, etc.) must have a corresponding key added to all 3 locale files (`en`, `vi`, `ja`) under `EDCAP_FE/public/locales/` — a hard-coded `t(key, {defaultValue: "..."})` fallback string is not a substitute for a real locale key, since it silently masks the missing-translation case for every language, not just the ones it was meant to cover (resolved per `OI-PROMPT_TEMPLATE_REUSE_RATE-12`, raised in Phase 5 review).
9. The template file's content is fetched from the same PR head revision as the changed file (not the repository's default branch).
10. If fetching a single file's (changed file or template) content from GitHub fails with HTTP `401/403/404`, reuse the existing `isSkippableBlobError` pattern: skip that file (log `warn`), do not change its counters, and continue processing the remaining files in the PR. Any other HTTP error propagates and fails the request, consistent with existing blob-read call sites. This same skippable/non-skippable classification also applies to the PR-level `resolveRevision`/`listTree` calls that run once per PR ahead of the per-file loop: `401/403/404` skips template-usage validation entirely for that PR (log `warn`, return early, no counter change); any other error propagates and fails the request — resolved per `OI-PROMPT_TEMPLATE_REUSE_RATE-16` (previously these calls swallowed every error, including transient/server errors, as a false success).
11. Template-usage validation and counter recording run only when the `pull_request` webhook fires with `action=closed` AND the payload's `merged` field is `true` — never on `opened`/`synchronize`/`reopened`, and never on a `closed`-but-not-merged (i.e. abandoned) PR. This is required because `listChangedFilePaths()` returns the PR's full cumulative diff on every delivery (not a delta since the last event), so triggering on every supported action would re-scan the same files and inflate the counters once per lifecycle event instead of once per PR (resolved per `OI-PROMPT_TEMPLATE_REUSE_RATE-14`, raised in Phase 5 second review).

### 6.2. Input

| item | type | required | validation | notes |
|---|---|---|---|---|
| GitHub `pull_request` webhook payload | JSON (HTTP POST body) | Yes | HMAC-SHA256 signature verified before processing | Existing `GithubWebhookController` entry point, reused as-is |
| Changed file paths | `List<String>` | Yes | Must start with the ticket working-directory prefix to be eligible | Via `GithubPullRequestFilesPort.listChangedFilePaths(repoKey, prNumber)` |
| PR head revision SHA | `String` | Yes | Resolved via `ArtifactScannerSourcePort.resolveRevision(...)` | Used for `listTree`/`readBlob` calls |
| Changed file content | raw markdown (`byte[]` → `String`) | Yes (when a template is found) | UTF-8 decodable | Via `readBlob(repositoryFullName, blobSha)` |
| Template file content | raw markdown (`byte[]` → `String`) | Yes (when template found) | Same as above | Template content is read from the same PR head revision as the changed file |
| `GET /api/v1/pm/dashboard/template-usage` request | HTTP GET, no body | Yes | `projectId` and `repositoryId` are required query parameters | The frontend query is enabled only when both `filters.projectId` and `filters.repositoryId` are selected |

### 6.3. Output

| item | type | format | notes |
|---|---|---|---|
| Validation result | internal (PASS/FAIL) | boolean-equivalent enum | Not directly exposed via API; only aggregated |
| Persisted counters | DB rows | `total_check_count BIGINT`, `template_match_count BIGINT` per phase (+ dimensions per storage decision) | See Section 12 |
| `GET /api/v1/pm/dashboard/template-usage` response | JSON array | `[{ phaseCode: string, phaseName: string, totalCheckCount: number, templateMatchCount: number, usageRate: number \| null }]` | `usageRate` is a plain numeric percentage rounded to 1 decimal place (e.g. `66.7`), with no `%` sign in the value; `null` (rendered as `-` by the FE, which appends `%` itself) when `totalCheckCount == 0`. Resolved per OI-10. |
| FE display | UI section in `PMDashboardPage.tsx` | Phase name, `(match/total)`, usage rate % or `-` | Rendered immediately after `<AllTicketsTable />` and before `<TicketDetailDrawer />` |

### 6.4. Error / Exception

| case | expected behavior | message/code | notes |
|---|---|---|---|
| No template found for a changed file | Skip silently; no counter increment | None (no error) | Explicit rule from raw requirement |
| Invalid webhook HMAC signature | Reject before any processing | Existing `ErrorResponse` (401/403 per current webhook handling) | Reused as-is, unchanged by this ticket |
| GitHub blob/tree fetch fails for one file with `401/403/404` | Skip that file (log `warn`, no counter change), continue processing the rest of the PR | None (no error surfaced to caller) | Reuses `isSkippableBlobError` pattern from `GithubSecurityEvidenceSnapshotService`; resolved per OI-8 |
| GitHub `resolveRevision`/`listTree` (PR-level, shared across all files) fails with `401/403/404` | Skip template-usage validation entirely for that PR (log `warn`, return early, no counter change) | None (no error surfaced to caller) | Same classification as the per-file case above; any other status/non-HTTP error propagates and fails the request — resolved per `OI-PROMPT_TEMPLATE_REUSE_RATE-16` |
| `TemplateUsageStatWriter.recordIndependently(...)` throws `DataAccessException` while writing counters for one file | Log `warn` with delivery/project/repository/phase/path context, skip that file's counter update, continue processing remaining files and complete the webhook normally | None (no error surfaced to caller) | Best-Effort Audit/Stat Write Pattern (§2/§8) requires counter-write failures to never abort the primary webhook flow — resolved per `OI-PROMPT_TEMPLATE_REUSE_RATE-17` |
| GitHub blob/tree fetch fails for one file with any other status | Propagate/fail, consistent with existing non-skippable blob-read behavior | Existing `WebClientResponseException` handling | Resolved per OI-8 |
| `totalCheckCount == 0` on read | Return `usageRate: null`; FE renders `-` | N/A (not an error) | Explicit rule from raw requirement |
| Phase or artifact type not found in dimension tables for a changed file | File is excluded from statistics (cannot be attributed to a phase) | None (skip) | Follows same "skip silently" philosophy as rule 3 |

### 6.5. Boundary Value

| item | min | max | special cases | expected |
|---|---|---|---|---|
| `totalCheckCount` | 0 | unbounded (monotonic counter) | 0 | `usageRate` displayed as `-` |
| `templateMatchCount` | 0 | `totalCheckCount` | equals `totalCheckCount` | `usageRate = 100` |
| Header count in a document | 0 headers | unbounded | Document with 0 headers vs. a template with ≥1 header | FAIL (structures differ) |
| Files changed per PR | 0 eligible files | unbounded | PR touches files outside any ticket working directory | No validation triggered, no counters touched |
| Phases per statistics response | 0 | number of `tbl_dim_phase` rows relevant to this feature (8: codes `'1'..'8'`) | A phase whose `tbl_dim_artifact_type` rows belong to an unrelated feature (e.g. `0-A`) | Excluded via literal `phase_code IN ('1'..'8')` filter — resolved per `OI-PROMPT_TEMPLATE_REUSE_RATE-13` (supersedes the insufficient `OI-PROMPT_TEMPLATE_REUSE_RATE-11` EXISTS-join fix) |

### 6.6. Non-functional

| item | requirement | target / threshold | verification | notes |
|---|---|---|---|---|
| Performance | Validation must not materially slow webhook response | Webhook handler already does blocking I/O (GitHub API calls) for artifact scanning; new calls should reuse the same request-scoped revision resolution to avoid duplicate `resolveRevision`/`listTree` calls | Integration test timing PR processing before/after | |
| Security | No new external surface; reuses existing GitHub App token and HMAC-verified webhook | Same as existing webhook security posture | Code review | No PII involved |
| Availability / Reliability | A single file's validation failure must not abort the rest of PR processing (artifact scanning, ticket status updates) | Best-Effort Audit/Stat Write Pattern (`NESTED`/`REQUIRES_NEW`) for counter writes | Integration test simulating a GitHub API error mid-validation | See PJ5 |
| Maintainability | Adding/removing a phase or file must require zero core-logic changes | Verified by adding a test-only phase/file row and confirming validation picks it up without code changes | Unit/integration test | Directly maps to raw requirement Section 5 |
| Observability / Logging | Skipped validations (no template found) and validation failures should be logged at appropriate levels (info/warn), not silently swallowed from an operability standpoint | Structured log line per skipped/failed file | Log review in staging | Does not contradict "no error raised to the caller" |
| Compatibility | New API and FE section must not change existing `TicketDetailDrawer` contract for other sections | No breaking change to `PmDashboardService` detail response | Existing FE tests for other drawer sections still pass | |

## 7. Acceptance Criteria

| ACID | description | testable? | notes |
|---|---|---|---|
| AC-PROMPT_TEMPLATE_REUSE_RATE-1 | When a `pull_request` webhook event is processed, only files under the ticket's working directory are considered for template validation | Yes | Depends on HD4 resolving the correct path prefix |
| AC-PROMPT_TEMPLATE_REUSE_RATE-2 | The system resolves a file's template using the 3-tier priority order and uses the first match found | Yes | |
| AC-PROMPT_TEMPLATE_REUSE_RATE-3 | If no template is found for a file, validation is skipped for that file with no error raised and no counters changed | Yes | |
| AC-PROMPT_TEMPLATE_REUSE_RATE-4 | Comparison considers only header name, level, and order; differing body content does not cause a FAIL | Yes | |
| AC-PROMPT_TEMPLATE_REUSE_RATE-5 | Each validated file increments `total_check_count` for its phase by 1; a PASS additionally increments `template_match_count` by 1 | Yes | Validation (and thus this increment) runs exactly once per PR, on `action=closed && merged=true` — resolved per `OI-PROMPT_TEMPLATE_REUSE_RATE-14`. Does not guard against GitHub redelivering the identical event (accepted risk, `OI-PROMPT_TEMPLATE_REUSE_RATE-15`); a per-file counter-write failure is caught and logged rather than aborting the remaining files/webhook (`OI-PROMPT_TEMPLATE_REUSE_RATE-17`) |
| AC-PROMPT_TEMPLATE_REUSE_RATE-6 | Adding a new file-to-phase mapping (a new `tbl_dim_artifact_type` row for an existing in-scope phase) via data change alone (no code deployment) causes it to be picked up by validation and statistics | Yes | File→phase mapping remains fully data-driven per Business Rule #7. Note: adding a brand-new **phase** (a new `tbl_dim_phase` row not already in the `'1'..'8'` literal list) to this feature's scope requires a code change to the `phase_code IN (...)` filter — a deliberate, accepted scope-narrowing decided in the Phase 5 second review; see `OI-PROMPT_TEMPLATE_REUSE_RATE-13` |
| AC-PROMPT_TEMPLATE_REUSE_RATE-7 | `GET /api/v1/pm/dashboard/template-usage?projectId=...&repositoryId=...` returns one entry for each in-scope phase (`tbl_dim_phase` codes `'1'..'8'`, per the Terminology section); other phases (e.g. `0-A`, `0-B`, `9`, `10`) are excluded regardless of any `tbl_dim_artifact_type` rows they may carry for unrelated features — each entry has `phaseCode, phaseName, totalCheckCount, templateMatchCount, usageRate`, scoped to the given project and repository | Yes | Resolved per `OI-PROMPT_TEMPLATE_REUSE_RATE-13` (supersedes `OI-PROMPT_TEMPLATE_REUSE_RATE-11`, which was insufficient) |
| AC-PROMPT_TEMPLATE_REUSE_RATE-8 | `usageRate` is computed as `templateMatchCount/totalCheckCount*100`, rounded to exactly 1 decimal place, and is represented as `-` (not `0`/`NaN`) when `totalCheckCount == 0` | Yes | Rounding rule resolved per OI-10 |
| AC-PROMPT_TEMPLATE_REUSE_RATE-9 | `PMDashboardPage.tsx` displays a "Template Usage by Phase" section, immediately after `AllTicketsTable`, showing per phase: phase name, total check count, template match count, and usage rate (or `-`) | Yes | |
| AC-PROMPT_TEMPLATE_REUSE_RATE-10 | FE statistics section retrieves data from the backend API via a `useQuery` call following the existing `["pm-dashboard", ...]` query-key convention, enabled only when both `filters.projectId` and `filters.repositoryId` are selected (mirroring `summaryQuery`/`ticketsQuery`) | Yes | |
| AC-PROMPT_TEMPLATE_REUSE_RATE-11 | `GET /api/v1/pm/dashboard/template-usage` is restricted to PM-role users for the given `projectId` (ADMIN system role also permitted); a caller without the PM project role or ADMIN role receives the same `403 Component.Permission.Denied` as other PM Dashboard endpoints | Yes | Reuses `PmDashboardService.requirePm(...)`, resolved per OI-9 |

## 8. Examples

### 8.1. Normal Case

A PR modifies `documents/docs/changes/ABC-123/spec-pack.md`. A template exists at `documents/docs/standards/templates/_ticket-template/spec-pack.md`. Header structures match exactly → PASS. Phase "Spec Pack": `total_check_count += 1`, `template_match_count += 1`.

### 8.2. Error Case

A PR modifies `documents/docs/changes/ABC-123/self-review.md`. No file named `self-review.md` exists at any of the 3 template priority paths (hypothetical — in the current repo it does exist, this illustrates the skip path). Validation is skipped for this file; no counters change; no error surfaces to the PR author or webhook caller.

### 8.3. Boundary Case

For project P / repository R, the "Blackbox Test" phase has received zero validated files so far this period. `GET /api/v1/pm/dashboard/template-usage?projectId=P&repositoryId=R` returns `{ phaseCode: "7", phaseName: "Blackbox Test", totalCheckCount: 0, templateMatchCount: 0, usageRate: null }`, and the FE renders `-` instead of `0%`.

## 9. Source Availability Summary

Full detail in `03_source-availability.md`. Summary: the webhook entry point, changed-file listing, raw-content fetch (`ArtifactScannerSourcePort`), structural markdown parsing (`MarkdownParserCore`), the ticket-path substring detection (`ticketKeyFromPath`), the skippable-blob-error pattern (`isSkippableBlobError`), and the phase/artifact-type dimension tables all already exist and can be reused directly — this ticket is primarily an integration/aggregation feature, not new infrastructure. All Human Decisions (storage shape, API scope, missing dimension rows, path/naming, revision choice, failure handling) were resolved at kickoff review — see Resolved Decisions in `03_source-availability.md`.

## 10. Complexity Classification

```text
- Complexity: Standard
- System shape: FE+BE+DB
- Primary risk: DB (new migration correctness) / Contract (project+repo-scoped query wiring)
- Review mode: Standard
- Required options: FE-BE Contract, DB Migration
```

Rationale: no new external integrations are required (GitHub content-fetch and markdown parsing are fully reused), and the DB already has a matching extensible dimension model — this keeps the ticket out of "Complex/Critical" territory. It still touches webhook processing (BE), a new migration (DB, new table + new dimension rows), a new read API (FE/BE contract), and a new dashboard UI section (FE) — enough surface area to warrant "Standard" (not "Light") review mode with explicit FE-BE Contract and DB Migration review passes, even though all design ambiguities are now resolved.

## 11. FE/BE Contract Impact

- New endpoint: `GET /api/v1/pm/dashboard/template-usage?projectId={projectId}&repositoryId={repositoryId}`, added as a new `@GetMapping("/template-usage")` handler on the existing `PmDashboardController` (`@RequestMapping("/api/v1/pm/dashboard")`) — no new controller class. Resolved per HD5.
- Backend logic (query counters, compute `usageRate`, apply permission gating) is implemented as a new method on the existing `PmDashboardService` — no new service class — alongside `getSummary`/`getTickets`/`getDetail`/etc.
- Access control: gated by the existing `PmDashboardService.requirePm(caller, projectId)` (`PmDashboardService.java:197-212`), the same check already applied to every other PM Dashboard read method. Restricted to PM-role users for the given `projectId` (ADMIN system role always passes) — resolved per OI-PROMPT_TEMPLATE_REUSE_RATE-9.
- Both `projectId` and `repositoryId` are required query parameters — matches the project+repository scope decision (HD1) and mirrors `PMDashboardPage`'s existing `summaryQuery`/`ticketsQuery` gating (`enabled: Boolean(filters.projectId) && Boolean(filters.repositoryId)`).
- Response: raw JSON array (no envelope, consistent with existing endpoints), `[{ phaseCode, phaseName, totalCheckCount, templateMatchCount, usageRate }]`; `usageRate` is `null` when `totalCheckCount == 0`.
- New FE API method in `lib/api.ts`, nested inside the existing `pmDashboard` namespace (consistent with the sibling `summary`/`insights`/`tickets`/`options`/`export` methods already grouped under `pmDashboard`, rather than a new top-level sibling namespace): `pmDashboard.templateUsage({ projectId, repositoryId })`.
- New `useQuery` owned by `PMDashboardPage.tsx` (same level as `summaryQuery`/`ticketsQuery`/`detailQuery`), query key `["pm-dashboard", "template-usage", filters.projectId, filters.repositoryId]`, `enabled` mirroring the existing `ticketsQuery` condition.
- New presentational component (e.g. `TemplateUsageByPhase.tsx` under `pages/pm-dashboard/components/`), rendered in `PMDashboardPage.tsx` between `<AllTicketsTable ... />` (line 212–225) and `<TicketDetailDrawer ... />` (line 227) — following the `EqsSummaryCard` per-phase row layout pattern for consistency with the drawer's own per-phase visuals.
- New FE formatter needed: a zero-guarded percentage formatter (no existing helper in `utils.ts` or `number-format.ts` covers `total === 0 → "-"`).
- No change to `TicketDetailDrawer.tsx` or `PmDashboardService`'s ticket-detail response — this feature does not touch the per-ticket contract at all.

## 12. DB/Migration Impact

- New Flyway migration: `V510__add_template_usage_tracking.sql` (next available version after `V508__add_submitted_by_to_review.sql`).
- New dedicated table `tbl_fact_template_usage_stat`, following the `tbl_fact_test_run` counter-table precedent:

  ```sql
  CREATE TABLE IF NOT EXISTS tbl_fact_template_usage_stat (
      template_usage_stat_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      project_id UUID NOT NULL REFERENCES tbl_dim_project(project_id),
      repository_id UUID NOT NULL REFERENCES tbl_dim_repository(repository_id),
      phase_id UUID NOT NULL REFERENCES tbl_dim_phase(phase_id),
      total_check_count BIGINT NOT NULL DEFAULT 0,
      template_match_count BIGINT NOT NULL DEFAULT 0,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      CONSTRAINT ck_template_usage_counts_non_negative
          CHECK (total_check_count >= 0 AND template_match_count >= 0),
      CONSTRAINT ck_template_usage_match_le_total
          CHECK (template_match_count <= total_check_count),
      CONSTRAINT uq_template_usage_scope
          UNIQUE (project_id, repository_id, phase_id)
  );
  ```

  The unique constraint on `(project_id, repository_id, phase_id)` makes the row upsert-able: each PR processing does an `INSERT ... ON CONFLICT (project_id, repository_id, phase_id) DO UPDATE SET total_check_count = tbl_fact_template_usage_stat.total_check_count + 1, ...`.
- Same migration also seeds the 3 missing `tbl_dim_artifact_type` rows (`OPEN_ISSUES` → `open-issues.md`, `CONTEXT` → `context.md`, `CODEX_REVIEW` → `codex-review.md`), each linked to the correct existing `phase_id` (phases `'1'`, `'2'`, `'5'` respectively), following the same `INSERT ... SELECT phase_id ... FROM tbl_dim_phase WHERE phase_code = '...' ON CONFLICT (artifact_type_code) DO NOTHING` pattern used in `V234`.
- No destructive schema changes (purely additive: one new table + 3 new dimension rows), consistent with the platform's additive-migration convention seen in `V160`/`V234`.
- Counters are append/increment-only (never decremented or overwritten), consistent with the append-only/audit conventions in `docs/standards/database.md`.
- Counter writes follow the Best-Effort Write Pattern (`NESTED`/`REQUIRES_NEW`) so a failure to persist statistics never blocks or rolls back the primary webhook/PR-ingestion flow.

## 13. Security/Privacy Impact

- No new external integration or credential — reuses the existing GitHub App token (`AppProperties.Connectors.GitHub`) already used for artifact scanning.
- No PII is read, stored, or exposed; only document structural metadata (header names/levels) and aggregate counts.
- New read endpoint reuses the existing `PmDashboardService.requirePm(caller, projectId)` gate — the same permission check already applied to every other PM Dashboard read method (options/summary/tickets/detail). Access is restricted to PM-role users for the given `projectId` (ADMIN system role always passes); it is not open to all authenticated users. Resolved per OI-PROMPT_TEMPLATE_REUSE_RATE-9 (see Section 18).

## 14. Operation/Maintenance Impact

- Skipped validations (no template found) and any file-fetch failures should be logged (info/warn level) for operability, even though no error is surfaced to the webhook caller.
- Counter writes should follow the existing Best-Effort Audit/Stat Write Pattern (`NESTED`/`REQUIRES_NEW`) so a failure to persist statistics never blocks or rolls back the primary webhook/PR-ingestion flow.
- No new monitoring dashboards required beyond the new PM Dashboard UI section; no new alerting identified as necessary for this feature.
- Recovery: since counters are monotonic increments derived from PR events, there is no automatic backfill/replay mechanism for missed events — out of scope per Section 2.2 (no retroactive backfill).

## 15. Test Strategy Summary

- Unit tests for the header-structure comparator (exact match, level mismatch, order mismatch, missing headers, extra headers, zero-header documents).
- Unit tests for template-resolution priority order (match at tier 1, tier 2, tier 3, no match at any tier).
- Integration test: process a `pull_request` webhook fixture with known changed files and assert the resulting counters in DB.
- Integration test: `GET /api/v1/pm/dashboard/template-usage` returns correct `usageRate` including the `totalCheckCount == 0 → null` boundary case.
- Integration test: adding a phase/artifact-type row via test data (not code) and confirming it is picked up by both validation and the statistics endpoint, to prove the "no core-logic change" extensibility requirement (AC-6).
- FE component test for the new `TemplateUsageByPhase` section and for `PMDashboardPage.tsx`'s wiring of it, following the existing `TicketDetailDrawer.test.tsx` convention (`QueryClientProvider` + `MemoryRouter`, `vi.spyOn(endpoints.pmDashboard, "templateUsage")`), covering the `-` rendering for zero-count phases and the `enabled` gating on `projectId`/`repositoryId`.

## 16. Human Decision Required

| ID | decision item | reasons | owner | status |
|---|---|---|---|---|
| H-PROMPT_TEMPLATE_REUSE_RATE-1 | Statistics API scope | Resolved: project + repository scoped, matching `PMDashboardPage`'s existing filters and the new display location | Product/Tech Lead | Resolved |
| H-PROMPT_TEMPLATE_REUSE_RATE-2 | DB storage shape | Resolved: new dedicated `tbl_fact_template_usage_stat` table (see Section 12) | Backend Lead | Resolved |
| H-PROMPT_TEMPLATE_REUSE_RATE-3 | Whether to add the 3 missing `tbl_dim_artifact_type` rows in this ticket's migration | Resolved: in scope, added via `V510` | Backend Lead | Resolved |
| H-PROMPT_TEMPLATE_REUSE_RATE-4 | Confirm real path convention supersedes the raw requirement's literal path | Resolved: `ticketKeyFromPath()`'s `/changes/` substring detection already handles the real layout; reused as-is | Backend Lead | Resolved |
| H-PROMPT_TEMPLATE_REUSE_RATE-5 | Confirm API URL uses `/api/v1/` prefix per platform convention | Resolved: `GET /api/v1/pm/dashboard/template-usage`, added directly to the existing `PmDashboardController`/`PmDashboardService` (no new controller/service class) | Backend Lead | Resolved |

## 17. Assumptions and Inference Log

| ID | assumption | basis | risk | need confirmation? |
|---|---|---|---|---|
| A-PROMPT_TEMPLATE_REUSE_RATE-2 | Counters are tracked at the phase level only (not per individual file within a phase), matching the raw requirement's Section 4/7 examples | Raw requirement's API example and UI mockup are both phase-level, not file-level | Low | No |
| A-PROMPT_TEMPLATE_REUSE_RATE-3 | The webhook's existing ticket-key derivation logic (already used for artifact scanning) is reused unchanged to identify which ticket a changed file belongs to, purely to attribute the check to the correct project/repository/phase — no ticket-level breakdown is exposed in the API | No new ticket-key derivation logic is described in the raw requirement | Low | No |

*(A-PROMPT_TEMPLATE_REUSE_RATE-1, the endpoint's permission scope, was raised as OI-PROMPT_TEMPLATE_REUSE_RATE-9 in Section 18 and has since been resolved: the endpoint is restricted to PM-role users via the existing `PmDashboardService.requirePm(...)` gate.)*

## 18. Open Issues

All resolved at kickoff review — see `open-issues.md` Resolution Log for full detail. Summary:

| ID | issue | impact | owner | status |
|---|---|---|---|---|
| OI-PROMPT_TEMPLATE_REUSE_RATE-1 | Statistics API scope | FE/BE Contract | Product/Tech Lead | Resolved |
| OI-PROMPT_TEMPLATE_REUSE_RATE-2 | DB storage shape choice for the new counters | DB/Migration | Backend Lead | Resolved |
| OI-PROMPT_TEMPLATE_REUSE_RATE-3 | 3 missing `tbl_dim_artifact_type` rows | DB/Migration, AC completeness | Backend Lead | Resolved |
| OI-PROMPT_TEMPLATE_REUSE_RATE-4 | Path/URL convention vs. real repository/platform conventions | Backend logic, API contract | Backend Lead | Resolved |
| OI-PROMPT_TEMPLATE_REUSE_RATE-5 | Phase 3 file name mismatch (`impl_plan_template.md` vs `impl-plan.md`) | Phase-file mapping accuracy | Backend Lead | Resolved |
| OI-PROMPT_TEMPLATE_REUSE_RATE-6 | `ConnectorOrchestrator` documented but not in source | Documentation accuracy only | Architecture doc owner | Accepted, no action |
| OI-PROMPT_TEMPLATE_REUSE_RATE-7 | Template-file revision choice | Comparison correctness | Backend Lead | Resolved |
| OI-PROMPT_TEMPLATE_REUSE_RATE-8 | Failure handling for a single file's content-fetch error mid-webhook | Webhook reliability | Backend Lead | Resolved |
| OI-PROMPT_TEMPLATE_REUSE_RATE-9 | Confirm whether the new template usage statistics endpoint should be accessible to all authenticated dashboard users or restricted to PM-role users | Security/Permission, API contract | Product/Tech Lead | Resolved |
| OI-PROMPT_TEMPLATE_REUSE_RATE-10 | `usageRate` rounding rule was unspecified (risk of inconsistent display precision) | FE display consistency | Backend Lead | Resolved — round to 1 decimal place server-side, `null` when `totalCheckCount == 0`, FE appends `%` without re-rounding |
| OI-PROMPT_TEMPLATE_REUSE_RATE-11 | Phase-scope filtering for the template-usage statistics response: whether a `tbl_dim_phase` row with no mapped `tbl_dim_artifact_type` (e.g. `0-A`, `0-B`, `9`, `10`) should be excluded from the response or shown with zero counts | API contract, dashboard UX (raised in Phase 5 review as an out-of-scope-phases display defect) | Product/Tech Lead | Superseded by `OI-PROMPT_TEMPLATE_REUSE_RATE-13` — the `EXISTS`/JOIN fix decided here was insufficient (see OI-13) |
| OI-PROMPT_TEMPLATE_REUSE_RATE-12 | New UI text (`Template Usage by Phase` section title/description, table column headers) shipped with a hard-coded English `defaultValue` fallback only, with no corresponding key in any of the 3 locale files — raised in Phase 5 review | i18n compliance, dashboard UX | Frontend Lead | Resolved — added `Pages.PmDashboard.templateUsageByPhase(Description)` and `Pages.PmDashboard.templateUsage.{phase,totalChecked,matched,usageRate}` keys to `en`/`vi`/`ja` locale files; see Business Rule #9 |
| OI-PROMPT_TEMPLATE_REUSE_RATE-13 | The `OI-11` fix (`WHERE EXISTS (SELECT 1 FROM tbl_dim_artifact_type at WHERE at.phase_id = ph.phase_id)`) still let phase `0-A` leak into the response, because `tbl_dim_artifact_type` is shared with an unrelated feature (AI-safety file scanning) that maps 3 artifact types to `0-A` — raised in Phase 5 second human review | API contract, dashboard UX | Product/Tech Lead | Resolved — `findTemplateUsage` now filters with the literal `WHERE ph.phase_code IN ('1','2','3','4','5','6','7','8')`, grounded directly in the Terminology section's already-fixed phase scope, per the clarified Business Rule #7 |
| OI-PROMPT_TEMPLATE_REUSE_RATE-14 | Shipped `validateTemplateUsage(...)` ran unconditionally on every supported `pull_request` webhook action (`opened`/`synchronize`/`reopened`/`closed`); since each delivery re-reads the PR's full cumulative diff and `recordCheck()` is a pure additive `+1` counter with no idempotency key, this inflated/duplicated `total_check_count`/`template_match_count` once per lifecycle event instead of once per PR — raised in Phase 5 second human review | Statistics correctness (core feature output) | Backend Lead | Resolved — template-usage recording now gated to `action=="closed" && merged==true` only, per Business Rule #11 |
| OI-PROMPT_TEMPLATE_REUSE_RATE-15 | GitHub webhook delivery is at-least-once transport; if GitHub redelivers the identical `pull_request` `action=closed`+`merged=true` event for the same PR, `validateTemplateUsage(...)` has no idempotency key (`deliveryId`/PR id) to detect the repeat, so `total_check_count`/`template_match_count` would be double-counted — raised by Codex automated review (Phase 5 third review, round 3) | Statistics correctness (core feature output), long-tail/rare-occurrence risk | Product/Tech Lead | **Accepted Risk — no code fix.** No webhook-redelivery dedup mechanism exists anywhere else in the codebase (verified: no `webhook_event`/`webhook_delivery` table, `deliveryId` is logged only, never persisted); the feature owner judged that adding dedup solely for this feature, when the platform has no existing redelivery-dedup precedent for any other webhook consumer, is disproportionate. Decision made by the ticket owner during Phase 5 round 3 review (2026-08-17). Tracked as the pre-existing `REDELIVERY_DEDUPE_GAP` exception in `self-review.md` §9, now human-ratified as Accepted Risk rather than OPEN |
| OI-PROMPT_TEMPLATE_REUSE_RATE-16 | Shipped `validateTemplateUsage(...)` wrapped the shared, PR-level `resolveRevision`/`listTree` calls in a blanket `catch (Exception ex)` that logged a `warn` and returned for **any** failure, including `5xx`/timeout/non-HTTP errors — unlike the per-file blob-fetch call sites, which already classified errors via `isSkippableBlobError` (401/403/404 skip vs. other errors fail); this let transient/server-side GitHub failures silently short-circuit validation and report a false success — raised by Codex automated review (Phase 5 third review, round 3) | Webhook reliability, silent data-quality gap | Backend Lead | Resolved — `resolveRevision`/`listTree` failures now reuse the existing `isSkippableFetchError` classification: `401/403/404` skip validation for that PR (log `warn`), any other error propagates and fails the request, per Business Rule #10 |
| OI-PROMPT_TEMPLATE_REUSE_RATE-17 | `TemplateUsageStatWriter.recordIndependently(...)` (`@Transactional(REQUIRES_NEW)`) had no try/catch at its `GithubWebhookService` call site; `REQUIRES_NEW` only isolates the transaction boundary, it does not swallow exceptions, so a `DataAccessException` (e.g. a transient DB outage) while writing one file's counters aborted the entire webhook, contradicting the Best-Effort Audit/Stat Write Pattern already documented for this exact call — raised by Codex automated review (Phase 5 third review, round 3) | Webhook reliability, best-effort-pattern violation | Backend Lead | Resolved — call site now wraps `recordIndependently(...)` in a `try/catch (DataAccessException)`, logs `warn` with delivery/project/repository/phase/path context, and continues processing the remaining files and the webhook response normally |
