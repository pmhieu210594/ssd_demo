# Implementation Plan

**Ticket ID**: PROMPT_TEMPLATE_REUSE_RATE
**Create date**: 2026-08-11
**Author**: Tech Lead (SDD Assistant)
**Update date**: 2026-08-11

## 1. Implementation Principle

- Reuse existing infrastructure exclusively: `ArtifactScannerSourcePort` for GitHub content, `MarkdownParserCore` for header parsing, `PmDashboardService.requirePm` for authorization, `PmDashboardController`/`PmDashboardService` for the new endpoint (no new controller/service class), `endpoints`-style namespace + TanStack Query on FE. No new abstraction is introduced where an existing one already fits (`context.md` → Patterns To Follow).
- All phase/file-to-phase mapping is data-driven via `tbl_dim_phase`/`tbl_dim_artifact_type` — zero hardcoded phase lists or switch statements in Java or TypeScript (AC-6, raw requirement §5).
- Write path (webhook counter increments) and read path (dashboard query) are implemented as two separate ports, even though they target the same table, to keep the `ingestion` use case from depending on the `pmdashboard` use case's port — hexagonal layering (`docs/.claude/rules/20-architecture.md`).
- External HTTP calls (GitHub blob/tree reads) always complete before the DB counter write opens its own `TransactionTemplate` — never interleaved, never sharing a transaction.
- No specification beyond `spec-pack.md` is introduced. Where a naming/shape ambiguity existed between `spec-pack.md` and this session's own earlier working notes (`ticket-rules.md`), `spec-pack.md` governs (see `impact-analysis.md` §7 note on `endpoints.templateUsage.statistics`).

## 2. Alternative Plan

| option | summary | pros | cons | decision |
|---|---|---|---|---|
| ID-1(a) | Implement the per-file validation loop entirely inside `GithubWebhookService.handlePullRequest()`; replicate the small amount of logic needed from `ticketKeyFromPath`'s `/changes/`-substring pattern and `isSkippableBlobError`'s 401/403/404 classification, as new private helper methods on the same class | No refactor of existing, already-tested private methods; smallest blast radius; matches how `resolveChangesFilePath`/`discoverTicketKeysFromTree` already coexist as siblings on this class | `GithubWebhookService` grows larger; two near-identical classification helpers now exist in two classes | **Chosen** |
| ID-1(b) | Extract a new class (e.g. `TemplateUsageValidationService`) and promote `ticketKeyFromPath` and `isSkippableBlobError` from `private` to package-private/public on their current classes so the new class can call them | Less duplication; single source of truth for both helpers | Modifies two existing, working methods' visibility on production classes not otherwise touched by this ticket — a refactor risk outside the stated scope, and requires re-verifying `GithubWebhookServiceTest`/`GithubSecurityEvidenceSnapshotService` callers are unaffected | Rejected — the risk of touching working, unrelated methods outweighs the DRY benefit for two small classification rules |
| ID-2(a) | New dedicated write port `TemplateUsageStatPort` (+ `TemplateUsageStatJdbcAdapter`), owned by/adjacent to the ingestion side; `PmDashboardRepositoryPort` gains only a new **read** method (`findTemplateUsage`), implemented on the existing `PmDashboardJdbcAdapter` | Clean hexagonal separation: `GithubWebhookService` (package `usecase.ingestion`) never depends on a port declared for `usecase.pmdashboard`; each adapter has one clear responsibility (write vs. read) against the same table | Two small new files (port + adapter) instead of one | **Chosen** |
| ID-2(b) | Put both the write (`recordCheck`) and read (`findTemplateUsage`) methods on the same existing `PmDashboardRepositoryPort`/`PmDashboardJdbcAdapter` | Fewer new files | Forces `GithubWebhookService` to import/depend on a port named and owned by the `pmdashboard` feature, which is a layering smell even though both ends are technically in `application.port.out.persistence` | Rejected |
| ID-3 | (Confirmatory, not a real alternative) FE namespace naming: follow `spec-pack.md` §11/§15 literally — new sibling `endpoints.templateUsage.statistics(...)` in `api.ts`, not nested inside `endpoints.pmDashboard` | Matches the single source of truth exactly; matches the literal test-strategy text (`vi.spyOn(endpoints.templateUsage, "statistics")`) | Diverges from this session's own earlier `ticket-rules.md` suggestion (which was an inference, not spec text) | **Chosen** — spec-pack.md overrides own prior inference |

## 3. Reason for Choosing the Alternative Plan

ID-1(a) is chosen because this ticket's stated scope is additive (new validation + new statistics), and `ticket-rules.md` → Stop/Ask Conditions already flags any promotion of `ticketKeyFromPath`/`isSkippableBlobError` visibility as requiring explicit confirmation before touching those files' existing signatures; defaulting to (a) avoids that confirmation gate entirely by not touching them. The duplication is small (a substring check and a 3-value status-code classification) and low-risk.

ID-2(a) is chosen because it is the more architecturally correct hexagonal split — `GithubWebhookService` already lives in `application.usecase.ingestion` and depends only on ingestion-relevant ports (`ArtifactScannerSourcePort`, `GithubPullRequestFilesPort`, etc.); adding a dependency on a `pmdashboard`-owned port purely for a write would cross a use-case boundary for no benefit, since the read side (`PmDashboardService`) is a completely separate consumer.

ID-3 is chosen because `spec-pack.md` is the explicit single source of truth per this session's binding rules, and it names the FE surface unambiguously in two places (§11 FE/BE Contract Impact, §15 Test Strategy Summary).

## 4. Expected Change File

| file | change summary | reason | related AC |
|---|---|---|---|
| `GithubWebhookService.java` | Add validation loop after line 208/225; add 2 small private helpers (path-scope check, fetch-failure classification) | Core validation logic | AC-1, AC-2, AC-3, AC-4, AC-5 |
| `V510__add_template_usage_tracking.sql` | New table + 3 dimension rows | Storage for counters, dimension completeness | AC-5, AC-6 |
| `TemplateUsageStatPort.java` (new) | New write port interface | Write-side abstraction | AC-5 |
| `TemplateUsageStatJdbcAdapter.java` (new) | New write adapter, upsert SQL | Write-side implementation | AC-5 |
| `PmDashboardRepositoryPort.java` | Add `findTemplateUsage(UUID projectId, UUID repositoryId)` | Read-side abstraction | AC-7 |
| `PmDashboardJdbcAdapter.java` | Implement `findTemplateUsage` | Read-side implementation | AC-7 |
| `PmDashboardService.java` | Add `getTemplateUsage(caller, projectId, repositoryId)`; `requirePm` first, compute `usageRate` | Business/permission logic | AC-7, AC-8, AC-11 |
| `PmDashboardController.java` | Add `@GetMapping("/template-usage")` | New endpoint | AC-7, AC-11 |
| `PmDashboardDtos.java` (or nested type in `PmDashboardController`) | Add `TemplateUsageDto` | Response shape | AC-7, AC-8 |
| `PmDashboardModels.java` (or nested type) | Add `TemplateUsageRow` | Internal read model | AC-7 |
| `EDCAP_FE/src/lib/api.ts` | Add `templateUsage.statistics(...)` namespace + response TS interface | FE contract | AC-10 |
| `PMDashboardPage.tsx` | Add `templateUsageQuery`, render `<TemplateUsageByPhase>` | FE wiring | AC-9, AC-10 |
| `TemplateUsageByPhase.tsx` (new) | New presentational component | FE display | AC-9, AC-8 |
| `pm-dashboard/utils.ts` | Add zero-guarded percentage formatter | Correct `-` rendering | AC-8 |

## 5. Class / Function / Method to Add or Modify

| target | action | input | output | note |
|---|---|---|---|---|
| `GithubWebhookService.validateTemplateUsage(...)` (new private method) | Add | `String repoKey`, `List<String> changedFilePaths`, `ResolvedRevision revision` (or equivalent already-resolved values) | `void` (writes via `TemplateUsageStatPort`) | Called from `handlePullRequest` right after line 225/235; iterates `changedFilePaths`, resolves phase/template/comparison per file |
| `GithubWebhookService.isEligibleTicketDocPath(String path)` (new private helper) | Add | file path | `boolean` | Mirrors `ticketKeyFromPath`'s `/changes/` substring check logically; does not call the existing method (private, different class of concern — see ID-1) |
| `GithubWebhookService.isSkippableFetchError(WebClientResponseException ex)` (new private helper) | Add | exception | `boolean` (401/403/404) | Mirrors `isSkippableBlobError`'s classification; local to this class per ID-1 |
| `TemplateUsageStatPort.recordCheck(UUID projectId, UUID repositoryId, UUID phaseId, boolean matched)` | Add (new interface) | scope IDs + match flag | `void` | Upsert increment; own `TransactionTemplate` in the adapter or the calling service |
| `TemplateUsageStatJdbcAdapter` | Add (new class, implements above) | — | — | `NamedParameterJdbcTemplate`, `INSERT ... ON CONFLICT (project_id, repository_id, phase_id) DO UPDATE ...`, constructor injection only |
| `PmDashboardRepositoryPort.findTemplateUsage(UUID projectId, UUID repositoryId)` | Add (interface method) | scope IDs | `List<PmDashboardModels.TemplateUsageRow>` | Alongside `findSummary`/`findTickets`/etc. |
| `PmDashboardJdbcAdapter.findTemplateUsage(...)` | Add (implementation) | scope IDs | rows | `NamedParameterJdbcTemplate` + `MapSqlParameterSource`, joins `tbl_fact_template_usage_stat` → `tbl_dim_phase` for `phase_code`/`phase_name`/ordering by `phase_order` |
| `PmDashboardService.getTemplateUsage(AuthUserContext caller, UUID projectId, UUID repositoryId)` | Add | caller, scope IDs | `List<PmDashboardDtos.TemplateUsageDto>` | First line: `requirePm(caller, projectId)`; then delegate to port; compute `usageRate` per row as `Math.round(templateMatchCount * 1000.0 / totalCheckCount) / 10.0` (1 decimal place), or `null` if `totalCheckCount == 0` (OI-10) |
| `PmDashboardController.templateUsage(...)` | Add | `@RequestParam UUID projectId`, `@RequestParam UUID repositoryId`, `@CurrentUser AuthUserContext caller` | `List<PmDashboardDtos.TemplateUsageDto>` | Thin controller, delegates only |
| `endpoints.templateUsage.statistics({ projectId, repositoryId })` (FE, `api.ts`) | Add | filter object | `Promise<TemplateUsageStat[]>` | Builds `URLSearchParams`, `api.get<T>(...)`, mirrors sibling `endpoints.pmDashboard.*` methods' internal shape |
| `formatUsageRate(usageRate)` (FE, `utils.ts`) | Add | `number \| null` (the already-rounded value from the API) | `string` | Returns `"-"` when `usageRate === null`; else `` `${usageRate}%` `` — the FE does not re-round, it only appends `%` (rounding is server-side per OI-10) |
| `TemplateUsageByPhase` (FE component) | Add | `data: TemplateUsageStat[]`, `isLoading` | JSX | Modeled visually on `EqsSummaryCard` |

## 6. SQL / Query / Repository Policy

- All new SQL is raw text-block SQL via `NamedParameterJdbcTemplate` + `MapSqlParameterSource`, matching `PmDashboardJdbcAdapter`'s existing style (no MyBatis XML mapper introduced for this ticket, consistent with the adapter it extends).
- Write: single upsert statement per validated file —
  ```sql
  INSERT INTO tbl_fact_template_usage_stat (project_id, repository_id, phase_id, total_check_count, template_match_count)
  VALUES (:projectId, :repositoryId, :phaseId, 1, :matchIncrement)
  ON CONFLICT (project_id, repository_id, phase_id) DO UPDATE SET
      total_check_count = tbl_fact_template_usage_stat.total_check_count + 1,
      template_match_count = tbl_fact_template_usage_stat.template_match_count + EXCLUDED.template_match_count,
      updated_at = now();
  ```
  (`:matchIncrement` is `1` on PASS, `0` on FAIL.)
- Read: single `SELECT` joining `tbl_fact_template_usage_stat` to `tbl_dim_phase` (for `phase_code`/`phase_name`/`phase_order`), filtered by `project_id`/`repository_id`, ordered by `phase_order`. Phases with no row yet are **not** synthesized as zero-rows by this query per se — see §7 for how the service layer fills gaps (needed for AC-9's "one entry per phase" and the boundary example in `spec-pack.md` §8.3).
- No new repository class hierarchy; both new methods live on the ports/adapters already named in §5.

## 7. Validation / Error / Logging Policy

- Path eligibility, template resolution, and dimension-mapping absence are **not errors** — they are silent skips per `spec-pack.md` §6.4/§6.1 rule 3/rule 7. No exception is thrown for these cases; the loop simply moves to the next file.
- Per-file GitHub fetch failure: catch `WebClientResponseException`; if status is 401/403/404 → `log.warn("Skipping template validation for {}: {}", path, status)`, continue to next file, no counter change; any other status → rethrow/propagate, which fails the whole webhook request exactly as today's non-skippable blob-read call sites do.
- `PmDashboardService.getTemplateUsage` throws `ForbiddenException("Component.Permission.Denied")` (exact string, reused) when `requirePm` fails — mapped to HTTP 403 by the existing `GlobalExceptionHandler`, no new exception type, no new `ResponseEntity` status code in the controller.
- `usageRate` computation: if `totalCheckCount == 0`, the service returns `usageRate = null` (never divides by zero, never returns `0`/`NaN`); otherwise it is rounded server-side to exactly 1 decimal place (`Math.round(templateMatchCount * 1000.0 / totalCheckCount) / 10.0`), e.g. `66.7`, `100.0`, `0.0` — resolved per OI-10, replacing the earlier unspecified `templateMatchCount/totalCheckCount*100` formula. FE `formatUsageRate` renders `"-"` when the value is `null`, and otherwise only appends the `%` sign — it must not re-round or reformat the number, to avoid a second, possibly divergent rounding step. Both layers independently guard the zero-total case (defense in depth), per AC-8 and `spec-pack.md` §8.3.
- Logging never includes secrets; only file path + HTTP status for skipped files, consistent with `docs/.claude/rules/30-security.md`.
- No response envelope change; errors for the new endpoint flow through `GlobalExceptionHandler` only.

## 8. Migration / Rollback Policy

- `V510__add_template_usage_tracking.sql` is purely additive: one `CREATE TABLE IF NOT EXISTS` + one/three `INSERT ... ON CONFLICT DO NOTHING` for dimension rows. Idempotent-safe by construction (matches `V234`'s own idempotency pattern).
- No `ALTER TABLE`, no data backfill for historical PRs (explicitly out of scope per `spec-pack.md` §14).
- Rollback: if ever needed, a follow-up migration would `DROP TABLE tbl_fact_template_usage_stat` and optionally remove the 3 seeded dimension rows — not created proactively in this ticket (no rollback script is authored unless requested; Flyway in this project has no automatic "down" migration convention, matching `V160`/`V234`).
- Migration application requires explicit human approval before running (`docs/.claude/rules/00-safety.md` §3) — this plan does not run `mvn flyway:migrate` or any DB command; it only authors the `.sql` file.

## 9. Step Implementation

| step | action | target file | verification | stop condition |
|---|---|---|---|---|
| 1 | Add `V510__add_template_usage_tracking.sql` with the exact DDL from `spec-pack.md` §12 | `V510__add_template_usage_tracking.sql` | SQL reviewed against spec-pack §12 line-by-line; no manual migration run without approval | Do not run `flyway:migrate` without explicit human approval |
| 2 | Add `TemplateUsageStatPort` interface + `TemplateUsageStatJdbcAdapter` | new files under `application/port/out/persistence/`, `infrastructure/persistence/adapter/` | Constructor injection only; no `@Data`; matches `PmDashboardJdbcAdapter` SQL style | — |
| 3 | Add `findTemplateUsage` to `PmDashboardRepositoryPort` + implement in `PmDashboardJdbcAdapter` | both files | Query verified against schema (`tbl_dim_phase` join) | — |
| 4 | Add `getTemplateUsage` to `PmDashboardService`; add endpoint to `PmDashboardController`; add DTOs | 3 files | `requirePm` is first line; exact exception message reused; controller stays thin | — |
| 5 | Add the two new private helpers + the validation loop to `GithubWebhookService.handlePullRequest` | `GithubWebhookService.java` | Reuses `changedFilePaths`/`revision` (no re-fetch); write happens after all HTTP calls, own `TransactionTemplate` | If implementing this step reveals that replicating the helpers is materially more than "small" (e.g. >30 lines each), stop and re-raise ID-1 for reconsideration |
| 6 | Add `templateUsage.statistics(...)` + DTO interface to `api.ts` | `api.ts` | Matches `spec-pack.md` §11/§15 naming exactly | — |
| 7 | Add `formatUsageRate` to `pm-dashboard/utils.ts` | `utils.ts` | `totalCheckCount === 0 → "-"`; does not modify `formatScore` | — |
| 8 | Add `TemplateUsageByPhase.tsx`; wire `templateUsageQuery` + render into `PMDashboardPage.tsx` | 2 files | `enabled` matches the page's real 4-flag condition; rendered between `<AllTicketsTable>` and `<TicketDetailDrawer>` | — |
| 9 | Write/extend unit + integration tests per `impact-analysis.md` §11 | test files listed there | All new and existing tests pass; `ArchitectureTest` note: cannot be run/verified — none exists (see `impact-analysis.md` §14) | — |

## 10. How to Verify Each Step

- Steps 1-4 (BE persistence/API): unit test the new port/adapter with a mocked `NamedParameterJdbcTemplate`; unit test `PmDashboardService.getTemplateUsage` for both the forbidden path and the happy path (mirroring `PmDashboardServiceTest.java`'s existing style); `@WebMvcTest` slice for the controller.
- Step 5 (webhook validation): unit test `handlePullRequest` with fixture PR payloads covering PASS, FAIL, skip-no-template, skip-unmapped-dimension, skip-401/403/404, and propagate-on-other-error, mirroring `GithubWebhookServiceTest.java`'s manual-mock style.
- Steps 6-8 (FE): Vitest + Testing Library test for `TemplateUsageByPhase` (renders `-` for zero-count phase, renders a percentage otherwise) and for `PMDashboardPage.tsx`'s query wiring/`enabled` condition.
- Step 1 (migration): do not execute against a live DB as part of this plan; verification is a manual line-by-line diff against `spec-pack.md` §12, deferred execution to an explicitly approved step outside this document.
- End-to-end (manual, deferred to Phase 6/7 per SDD phases): process a real/fixture PR webhook, confirm counters in `tbl_fact_template_usage_stat`, then call `GET /api/v1/pm/dashboard/template-usage` and confirm the FE section renders correctly, including the `totalCheckCount == 0 → "-"` boundary case from `spec-pack.md` §8.3.

## 11. Corresponding AC Table

| AC ID | implementation point | verification |
|---|---|---|
| AC-1 | New path-eligibility helper in `GithubWebhookService` (Step 5) | Unit test: file outside `/changes/` scope is not validated |
| AC-2 | 3-tier template-resolution lookup in the new validation loop (Step 5) | Unit test: match at tier 1/2/3, no match at any tier |
| AC-3 | No-template-found → skip, no error, no counter change (Step 5) | Unit test asserting counters unchanged and no exception |
| AC-4 | `MarkdownParserCore` comparison limited to title/level/order (Step 5) | Unit test: identical structure + different body → PASS |
| AC-5 | `TemplateUsageStatPort.recordCheck` upsert semantics (Steps 2, 5) | Unit test: `total_check_count` always +1, `template_match_count` +1 only on PASS |
| AC-6 | All phase/file mapping via `tbl_dim_phase`/`tbl_dim_artifact_type` (Steps 1, 5) | Test: add a test-only dimension row, confirm picked up without code change |
| AC-7 | `findTemplateUsage` + `getTemplateUsage` + controller endpoint (Steps 3, 4) | Integration test: endpoint returns one entry per phase, scoped correctly |
| AC-8 | `usageRate` rounded to 1 decimal place, null-on-zero in service (OI-10); `formatUsageRate` `-`-on-null in FE, appends `%` without re-rounding (Steps 4, 7) | Unit tests on both layers for the zero-total boundary and for a representative repeating-decimal case (e.g. 2/3 → `66.7`) |
| AC-9 | `TemplateUsageByPhase` placement in `PMDashboardPage.tsx` (Step 8) | FE test: section renders after `AllTicketsTable`, before `TicketDetailDrawer` |
| AC-10 | `templateUsageQuery` query-key + `enabled` condition (Step 8) | FE test: query only fires when both `projectId`/`repositoryId` set, following `["pm-dashboard", ...]` convention |
| AC-11 | `requirePm(caller, projectId)` as first line of `getTemplateUsage` (Step 4) | Unit test: non-PM caller → 403 `Component.Permission.Denied`; ADMIN → allowed |

## 12. Stop / Ask Condition

- Before running `mvn flyway:migrate` (or any DB migration command) — stop and get explicit human approval (`docs/.claude/rules/00-safety.md` §3).
- Before any `git commit`/`git push` — stop and get explicit human approval.
- If Step 5's helper replication (ID-1(a)) turns out to require more than the two small helpers described — stop and re-raise ID-1 rather than silently expanding scope or promoting the original private methods.
- If a changed file resolves to a phase/artifact-type combination with no `tbl_dim_artifact_type` row other than the already-accepted `IMPACT_ANALYSIS` gap — stop and ask whether this is a new gap requiring a `V510` addition, rather than silently excluding it (per `ticket-rules.md`).
- If FE test conventions for `pm-dashboard` (test file location/pattern) cannot be located when Step 9 begins — stop and ask rather than guessing a convention (per `ticket-rules.md`).

## 13. Do Not Do This Ticket

- Do not promote `ticketKeyFromPath` or `isSkippableBlobError` out of `private` on their existing classes (ID-1(a) chosen instead).
- Do not add both read and write template-usage methods onto `PmDashboardRepositoryPort` (ID-2(a) chosen instead — write goes on the new `TemplateUsageStatPort`).
- Do not create a second GitHub HTTP client/adapter, a second markdown parser, a new controller/service class, a new Redux slice/Zustand store, or a raw `fetch()` call outside `api.ts` (per `ticket-rules.md` → Must Not Do, restated, not re-derived).
- Do not implement retroactive backfill of counters for PRs processed before this ticket ships.
- Do not add a feature flag — not requested in `spec-pack.md`, and the change is purely additive.
- Do not attempt to add or modify an ArchUnit test class as part of this ticket — the gap noted in `impact-analysis.md` §14 is pre-existing and out of scope; only note it, do not fix it here.

## 14. Open Related Issues

No new Open Issues are raised by this document — all ambiguities needed to write this plan were already resolved in `open-issues.md` (OI-1..9). The two implementation-shape decisions (ID-1, ID-2) in `impact-analysis.md` §16 are design choices within those already-resolved constraints, resolved directly in this document (§2/§3) rather than escalated, since neither changes any user-facing behavior, API contract, or data model already fixed by `spec-pack.md`.
