# Impact Analysis

**Ticket ID**: PROMPT_TEMPLATE_REUSE_RATE
**Create date**: 2026-08-11
**Author**: Tech Lead (SDD Assistant)
**Update date**: 2026-08-11

## 1. Change Content

Add automatic, structure-only validation of ticket documents on every `pull_request` webhook event, persist PASS/FAIL outcomes as per-`(project, repository, phase)` counters in a new table, expose them via a new read endpoint on the existing PM Dashboard API, and render them as a new "Template Usage by Phase" section on `PMDashboardPage.tsx`. Three independent surfaces, all extending existing components — no new controller/service class, no new GitHub client, no new markdown parser (per `spec-pack.md` §11, §16 H-5; `context.md` → Patterns To Follow / Forbidden Patterns):

1. **BE — webhook ingestion (write path)**: extend `GithubWebhookService.handlePullRequest()` to validate each eligible changed file's header structure against its resolved template, then record the result.
2. **BE — persistence + API (read path)**: new table `tbl_fact_template_usage_stat` (migration `V510`), a new write port/adapter for the ingestion side, a new read method on the existing `PmDashboardRepositoryPort`/`PmDashboardJdbcAdapter` for the dashboard side, a new method on `PmDashboardService`, a new endpoint on `PmDashboardController`.
3. **FE — PM Dashboard**: new query/endpoint method in `EDCAP_FE/src/lib/api.ts`, new `TemplateUsageByPhase` section on `PMDashboardPage.tsx`.

This document is derived entirely from `spec-pack.md` (single source of truth) plus source-verified findings already recorded in `context.md`/`source-map.md`/`ticket-rules.md`. One naming detail below (§7) follows `spec-pack.md` literally where it differs from an earlier internal suggestion in `ticket-rules.md` — see the note there.

## 2. Directly Affected Files

| file | reason | change type |
|---|---|---|
| `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/ingestion/GithubWebhookService.java` | Add the per-file template-validation loop inside `handlePullRequest()`, reusing `changedFilePaths` (line 208) and `revision` (line 225); call the new write port after all GitHub HTTP calls for the PR complete | Modify |
| `EDCAP_BE/src/main/resources/db/migration/V510__add_template_usage_tracking.sql` | New table `tbl_fact_template_usage_stat` + 3 missing `tbl_dim_artifact_type` rows (`OPEN_ISSUES`, `CONTEXT`, `CODEX_REVIEW`) | Add |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/persistence/TemplateUsageStatPort.java` (new) | Write-side port used by `GithubWebhookService` to upsert counters | Add |
| `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/TemplateUsageStatJdbcAdapter.java` (new) | JDBC implementation of `TemplateUsageStatPort`, `INSERT ... ON CONFLICT (project_id, repository_id, phase_id) DO UPDATE` per `spec-pack.md` §12 | Add |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/persistence/PmDashboardRepositoryPort.java` | Add a new read method (e.g. `findTemplateUsage(UUID projectId, UUID repositoryId)`) alongside `findSummary`/`findTickets`/etc. | Modify |
| `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/PmDashboardJdbcAdapter.java` | Implement the new read method, `NamedParameterJdbcTemplate` pattern (same style as existing methods) | Modify |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/pmdashboard/PmDashboardService.java` | Add a new method (e.g. `getTemplateUsage(caller, projectId, repositoryId)`): `requirePm(caller, projectId)` first, then delegate to the port, compute `usageRate` per row | Modify |
| `EDCAP_BE/src/main/java/com/sdd/platform/web/rest/PmDashboardController.java` | Add `@GetMapping("/template-usage")` handler, mirrors `summary`/`insights`/`tickets` signatures | Modify |
| `EDCAP_FE/src/lib/api.ts` | Add new `templateUsage.statistics({ projectId, repositoryId })` namespace + response DTO interface(s) | Modify |
| `EDCAP_FE/src/pages/pm-dashboard/PMDashboardPage.tsx` | New `useQuery` (`templateUsageQuery`), render `<TemplateUsageByPhase>` between `<AllTicketsTable>` and `<TicketDetailDrawer>` | Modify |
| `EDCAP_FE/src/pages/pm-dashboard/components/TemplateUsageByPhase.tsx` (new) | New presentational section, modeled visually on `EqsSummaryCard` (`TicketDetailDrawer.tsx:347-399`) | Add |
| `EDCAP_FE/src/pages/pm-dashboard/utils.ts` | Add a new zero-guarded percentage formatter (`totalCheckCount === 0 → "-"`); do not modify `formatScore` | Modify |

## 3. Indirectly Affected Files

| file | reason | risk |
|---|---|---|
| `EDCAP_BE/.../application/port/out/integration/ArtifactScannerSourcePort.java` | Its existing `resolveRevision`/`listTree`/`readBlob` methods gain new call sites (inside the new validation loop); interface itself unchanged | Low — read-only reuse, same pattern as existing fallback-scan call sites |
| `EDCAP_BE/.../domain/service/markdown/core/MarkdownParserCore.java` | Gains new call sites (`parse(...).sections()` on both changed file and template); class itself unchanged | Low — no existing unit test for this class (see `source-map.md` → Test Map); new usage needs its own coverage |
| `EDCAP_BE/.../application/usecase/ingestion/GithubSecurityEvidenceSnapshotService.java` | Not modified and not called; only its `isSkippableBlobError` 401/403/404 classification logic is **replicated** (it is a private instance method, not reusable across classes) | Low — duplication risk noted in `ticket-rules.md` Must Not Do, accepted per Alternative Plan §2 below |
| `tbl_dim_phase` / `tbl_dim_artifact_type` (existing tables) | Gain 3 new rows via `V510` (data-only change); existing 13 rows/columns untouched | Low — additive, `ON CONFLICT DO NOTHING` pattern already used at `V234` |
| `EDCAP_FE/src/pages/pm-dashboard/hooks/usePmDashboardFilters.ts` | Not modified; the new query reads `filters.projectId`/`filters.repositoryId` it already produces | None |
| `EDCAP_FE/src/pages/pm-dashboard/components/TicketDetailDrawer.tsx` | Not modified; `EqsSummaryCard` is used only as a visual reference pattern, per `context.md` and OI-1 (display location moved to `PMDashboardPage.tsx`) | None |

## 4. Caller / Callee

| caller | callee | impact |
|---|---|---|
| `GithubWebhookController.receive` | `GithubWebhookService.handle` | Unchanged |
| `GithubWebhookService.handle` | `GithubWebhookService.handlePullRequest` | Unchanged |
| `GithubWebhookService.handlePullRequest` | `GithubPullRequestFilesPort.listChangedFilePaths` (line 208), `revisionFromPullRequest` (line 225) | Unchanged — results **reused**, not recomputed |
| `GithubWebhookService.handlePullRequest` (new step) | `ArtifactScannerSourcePort.resolveRevision` / `.listTree` / `.readBlob` (×2 per eligible file: changed file + resolved template) | New calls, same port, pattern copied from the existing fallback-scan call site (lines 247-248) |
| `GithubWebhookService.handlePullRequest` (new step) | `MarkdownParserCore.parse(content, sourcePath).sections()` (×2 per eligible file) | New calls |
| `GithubWebhookService.handlePullRequest` (new step) | `TemplateUsageStatPort.recordCheck(projectId, repositoryId, phaseId, matched)` (new port) | New call, executed after all GitHub HTTP calls for the PR finish, own `TransactionTemplate` |
| `PmDashboardController` (new endpoint) | `PmDashboardService.getTemplateUsage(caller, projectId, repositoryId)` | New call, mirrors `summary`/`insights`/`tickets` |
| `PmDashboardService.getTemplateUsage` | `PmDashboardService.requirePm(caller, projectId)` (existing, line 197) → `PmDashboardRepositoryPort.findTemplateUsage(...)` (new) | `requirePm` reused unchanged; new port method added |
| `PMDashboardPage.tsx` (new query) | `endpoints.templateUsage.statistics({ projectId, repositoryId })` via new `useQuery` | New call, `enabled` mirrors the page's real 4-flag gating condition |

## 5. FE Impact

- New presentational component `TemplateUsageByPhase.tsx` under `pages/pm-dashboard/components/`, rendered in `PMDashboardPage.tsx` between `<AllTicketsTable>` (~line 212) and `<TicketDetailDrawer>` (~line 227), per OI-1.
- New `useQuery` (`templateUsageQuery`) owned by `PMDashboardPage.tsx`, query key `["pm-dashboard", "template-usage", filters.projectId, filters.repositoryId]`, `enabled` reproducing the page's actual condition (`optionsQuery.isSuccess && Boolean(filters.projectId) && Boolean(filters.repositoryId) && isMatchingDashboard`) — not a simplified two-flag version (AC-10).
- New zero-guarded percentage formatter added to `pm-dashboard/utils.ts` (or a new small module) — `totalCheckCount === 0 → "-"`; `formatScore` is a null-guard only and must not be repurposed (AC-8).
- No change to any existing FE contract: `TicketDetailDrawer.tsx`, `usePmDashboardFilters.ts`, other existing queries/components are untouched.
- Routing/i18n: no new route; page already exists. No new copy strings identified beyond phase names (already sourced from `tbl_dim_phase.phase_name`, no client-side i18n table needed for this ticket per scope).

## 6. BE Impact

- **Ingestion (write) path** — `GithubWebhookService.handlePullRequest()` gains a new step after `changedFilePaths`/`revision` are resolved: for each path matching the existing `/changes/` substring detection (same logic pattern as `ticketKeyFromPath`, not the method itself — see §16), resolve phase/artifact type, resolve template via 3-tier lookup, fetch both blobs at the same revision, parse and compare, then write counters via the new `TemplateUsageStatPort`.
- **Dashboard (read) path** — `PmDashboardService` gains one new method following the exact `requirePm`-then-query shape already used by `summary`/`insights`/`tickets`; `PmDashboardController` gains one new `@GetMapping`.
- **New persistence surface** — one new write port+adapter (ingestion-owned) and one new read method on the existing PM-dashboard port+adapter (dashboard-owned). These are two separate call paths into the same table, matching hexagonal separation: the ingestion use case must not depend on the `pmdashboard` package's port, and vice versa.
- **Transaction boundary** — the new counter upsert runs in its own `TransactionTemplate`, strictly after all GitHub HTTP calls for that PR complete, per `docs/.claude/rules/20-architecture.md` ("External HTTP calls run outside DB transactions; each external upsert uses its own `TransactionTemplate`").
- **Error handling** — per-file blob/tree fetch failures classified 401/403/404 → skip (log `warn`, no counter change), any other status → propagate, replicating (not calling) the `isSkippableBlobError` classification.

## 7. API Contract Impact

| endpoint | request impact | response impact | backward compatible? |
|---|---|---|---|
| `GET /api/v1/pm/dashboard/template-usage` (new) | New endpoint; required query params `projectId` (UUID), `repositoryId` (UUID) | New: `[{ phaseCode, phaseName, totalCheckCount, templateMatchCount, usageRate }]`; `usageRate` is a plain numeric percentage rounded to exactly 1 decimal place (e.g. `66.7`, no `%` sign in the value), or `null` when `totalCheckCount == 0` (AC-7, AC-8, resolved per OI-10) | Yes — purely additive, no existing endpoint changes |
| `POST /api/v1/webhooks/github` | No request/response contract change; internal processing gains a new side-effect (counter writes) | No response shape change (`{ handled, recordsAffected }` unchanged) | Yes |

**FE namespace naming note**: `spec-pack.md` §11 and §15 (Test Strategy) both specify the new FE API surface as a **new sibling namespace** `endpoints.templateUsage.statistics({ projectId, repositoryId })` in `lib/api.ts` — not nested inside the existing `endpoints.pmDashboard` object. This differs from a suggestion recorded earlier in `ticket-rules.md` §Must Follow ("must live inside the existing `endpoints.pmDashboard` object"), which was this session's own inference, not text from `spec-pack.md`. Per the binding rule that `spec-pack.md` is the single source of truth, `impl-plan.md` follows the spec-pack naming (`endpoints.templateUsage.statistics`) exactly. The DTO interface still lives in `api.ts` (not `pages/pm-dashboard/types.ts`), consistent with every other PM Dashboard DTO.

## 8. DTO / Schema / Validation Impact

- New response DTO (BE): a small record/class, e.g. `PmDashboardDtos.TemplateUsageDto(phaseCode, phaseName, totalCheckCount, templateMatchCount, usageRate)`, `usageRate` as `Double`/`null`, rounded to exactly 1 decimal place when non-null (OI-10) — no `%` sign in the value, FE appends it at render time.
- New internal read model (BE): e.g. `PmDashboardModels.TemplateUsageRow(phaseCode, phaseName, totalCheckCount, templateMatchCount)` returned by the new port method; `usageRate` is computed in the service layer, not stored.
- New request validation (BE): `projectId`/`repositoryId` are required `UUID` query params on the controller method — missing/invalid → standard Spring binding error, mapped by `GlobalExceptionHandler` (existing, no change needed: `MethodArgumentTypeMismatchException`/binding errors already fall through to its generic handler).
- New DTO (FE): TypeScript interface mirroring the JSON array shape, declared in `api.ts` next to `PmDashboardSummary`/`PmDashboardTicketRow`.
- No change to any existing DTO, schema, or validation rule.

## 9. DB / Migration Impact

- New Flyway migration `V510__add_template_usage_tracking.sql` (next after `V508__add_submitted_by_to_review.sql`). Exact DDL, copied verbatim from `spec-pack.md` §12:

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

- Same migration seeds 3 missing `tbl_dim_artifact_type` rows (`OPEN_ISSUES`→`open-issues.md`, `CONTEXT`→`context.md`, `CODEX_REVIEW`→`codex-review.md`), each linked to its correct existing `phase_id` (phases `'1'`, `'2'`, `'5'`), using the `INSERT ... SELECT ... FROM tbl_dim_phase WHERE phase_code = '...' ON CONFLICT (artifact_type_code) DO NOTHING` pattern from `V234`.
- Purely additive (one new table + 3 new dimension rows); no destructive change, no column drop/rename on any existing table.
- `uq_template_usage_scope` makes the row upsert-able via `INSERT ... ON CONFLICT (project_id, repository_id, phase_id) DO UPDATE SET total_check_count = tbl_fact_template_usage_stat.total_check_count + 1, template_match_count = tbl_fact_template_usage_stat.template_match_count + CASE WHEN :matched THEN 1 ELSE 0 END, updated_at = now()`.
- Rollback: purely additive migration; rollback (if ever needed) would be a manual follow-up migration dropping the table/rows — no automatic Flyway "undo" in this project's convention (consistent with `V160`/`V234`).

## 10. Batch / Job / Event Impact

- No new scheduled job, cron, or batch process. The counter write is synchronous within the existing `pull_request` webhook request lifecycle (best-effort, own transaction, does not block the primary webhook response per §14/§17 of `spec-pack.md`).
- No new outbound event is published; no existing event consumer is affected.
- No retroactive backfill for PRs processed before this ticket ships — explicitly out of scope per `spec-pack.md` §2.2/§14 ("no automatic backfill/replay mechanism for missed events").

## 11. Test Impact

| production code | existing test | new test needed |
|---|---|---|
| `GithubWebhookService.handlePullRequest` | `GithubWebhookServiceTest.java` (manual-mock style) | Cases: template found+PASS, template found+FAIL, template not found (skip), file maps to unknown dimension (excluded), blob fetch 404 (skip+continue), blob fetch 500 (fail) |
| `MarkdownParserCore` (reused as-is) | None found in `src/test/**` | New structure-comparison logic on top of it needs full coverage from scratch (exact match, level mismatch, order mismatch, missing/extra headers, zero-header doc) |
| `TemplateUsageStatPort`/`TemplateUsageStatJdbcAdapter` (new) | None (new files) | New test class; mock `NamedParameterJdbcTemplate` — no production DB in unit tests per `docs/.claude/rules/40-testing.md` |
| `PmDashboardRepositoryPort.findTemplateUsage` / `PmDashboardJdbcAdapter` (new method) | None for this method | New test case, same file/class as existing adapter tests if any exist, else new |
| `PmDashboardService.getTemplateUsage` (new method) | `PmDashboardServiceTest.java` (`@Mock`/AssertJ style) | Add a `templateUsage_requiresPmRole()` test mirroring `summary_requiresPmRole()`; happy-path test mirroring the existing project-role-allow case |
| `PmDashboardController` (new endpoint) | `PmDashboardControllerTest.java` | Add a `@WebMvcTest`-slice case for the new route |
| FE `endpoints.templateUsage.statistics` + new formatter + `TemplateUsageByPhase.tsx` | Not located in this pass (FE test conventions for `pm-dashboard` not confirmed — see `source-map.md` → Unknown Source Areas) | Vitest + Testing Library; must cover `totalCheckCount === 0 → "-"` explicitly (AC-8) |
| `V510` migration | N/A (SQL) | Flyway applies cleanly against a clean dev DB; integration test asserting counters after a fixture webhook, per `spec-pack.md` §15 |

## 12. Operation / Monitoring Impact

- Skipped validations (no template found) and per-file fetch failures logged at `info`/`warn`, matching `spec-pack.md` §14 — no error surfaced to the webhook caller, but visible in logs for operability.
- No new alerting or monitoring dashboard required beyond the new PM Dashboard UI section.
- Counter writes follow the Best-Effort Audit/Stat Write Pattern (own transaction, `NESTED`/`REQUIRES_NEW` semantics per `spec-pack.md` §12/§14) so a statistics-write failure never blocks or rolls back the primary webhook/PR-ingestion flow.
- No new secrets, credentials, or external integration — reuses the existing GitHub App token already used for artifact scanning (`spec-pack.md` §13).

## 13. Rollout / Rollback Impact

- Additive-only change: new table, new dimension rows, new endpoint, new FE section. No feature flag identified as necessary or requested in `spec-pack.md`.
- Rollback path: revert the BE change (webhook validation step + new endpoint) and the FE change independently; the `V510` migration can remain applied (additive, non-breaking) even if the feature code is rolled back — existing dashboards/endpoints are unaffected by the presence of an unused table/rows.
- No coordinated deploy ordering required beyond the general convention (migration applies before/with the BE deploy that reads/writes the new table); FE change is independent and additive (new UI section only, no removal of existing UI).

## 14. Areas Determined to be Unaffected and Based on

| area | judgment | evidence |
|---|---|---|
| `TicketDetailDrawer.tsx` and its existing sections (incl. `EqsSummaryCard`) | Unaffected — used only as a visual pattern reference, not modified | `context.md` → Similar Implementations; OI-1 resolution moves display to `PMDashboardPage.tsx` |
| Existing PM Dashboard endpoints (`options`, `summary`, `insights`, `tickets`, detail) | Unaffected — no shared response shape, no shared query touched | `PmDashboardController.java` read in full; new endpoint is additive |
| `GithubSecurityEvidenceSnapshotService` | Unaffected — not called, not modified; only its failure-classification logic is replicated in the new code | Confirmed `private` instance method, not accessible cross-class |
| ArchUnit hexagonal-layering enforcement | **Cannot be fully verified by an automated test today** — `CLAUDE.md` states layering is "enforced by ArchUnit," but no `ArchitectureTest.java` or any `@AnalyzeClasses`/`ArchRule` usage was found anywhere under `src/test` in this session's search; only the `pom.xml` dependency and 4 `package-info.java` annotations reference ArchUnit. This is a pre-existing doc-vs-source gap, not introduced by this ticket, and out of this ticket's scope to fix — flagged here rather than asserted as "verified green." | Glob for `**/ArchitectureTest.java` and grep for `ArchRule\|@AnalyzeClasses` in `src/test`: zero results |
| `EDCAP_FE` routing / i18n segment handling | Unaffected — no new route, phase names sourced server-side from `tbl_dim_phase.phase_name` | `PMDashboardPage.tsx` read in full |

## 15. Required Options

Per `spec-pack.md` §10 (Complexity Classification): **FE-BE Contract** review and **DB Migration** review are both required for this ticket (Standard review mode, not Light) — no other special option (no Security Review, no Data Migration/Backfill Review, since there is explicitly no backfill per §10 and §14).

## 16. Human Decision Required

All ambiguities already raised as Open Issues (OI-1..10) are Resolved in `open-issues.md` (OI-10, `usageRate` rounding, was raised and resolved during this Phase's review — round to 1 decimal place, `null` on zero-total, FE appends `%`); no new Open Issue is raised by this document. Two implementation-shape decisions remain open specifically for `impl-plan.md` to resolve (both are design choices within already-resolved constraints, not new specification gaps):

| ID | decision item | options | recommendation |
|---|---|---|---|
| ID-1 | Where does the new per-file validation logic live, given `ticketKeyFromPath`/`isSkippableBlobError` are private and not callable across class boundaries? | (a) implement entirely inside `GithubWebhookService.handlePullRequest()`, replicating the needed logic locally; (b) extract a new class and promote/duplicate the two private methods | (a) — see Alternative Plan in `impl-plan.md` §2/§3 |
| ID-2 | Where does the write-side counter upsert live relative to the existing PM-dashboard read port? | (a) new dedicated write port (`TemplateUsageStatPort`) owned by the ingestion package, separate from `PmDashboardRepositoryPort`, which only gains a new **read** method; (b) put both read and write on `PmDashboardRepositoryPort` | (a) — keeps `GithubWebhookService` (ingestion) from depending on the `pmdashboard` package's port, consistent with hexagonal layering; see `impl-plan.md` §2/§3 |

## 17. Risk Summary

- **DB risk (Medium)**: new migration correctness — mitigated by copying the exact DDL from `spec-pack.md` §12 verbatim and following the existing `V234`/`V160` additive-migration + `ON CONFLICT` seeding conventions.
- **Contract risk (Low-Medium)**: project+repository-scoped query wiring on both BE and FE must exactly reproduce the existing `PMDashboardPage.tsx` gating condition (4 flags) — a simplified version would cause premature or duplicate fetches (AC-10).
- **Reuse-boundary risk (Low)**: `ticketKeyFromPath`/`isSkippableBlobError` are private and must be replicated, not called — accepted per ID-1 above; a future refactor could de-duplicate this if the team chooses to promote the methods later (out of scope now).
- **Doc-accuracy risk (Low, pre-existing)**: ArchUnit is claimed as enforcing hexagonal layering in `CLAUDE.md` but no test class was found — this ticket cannot rely on an automated architecture check catching a layering mistake; manual review focus is required instead (see `ticket-rules.md` → Review Focus).
- **No security/PII risk identified** — no new external integration, no PII, existing PM-role gate reused unchanged (`spec-pack.md` §13).
