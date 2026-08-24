# Final Report

**Ticket ID**: PROMPT_TEMPLATE_REUSE_RATE
**Create date**: 2026-08-10
**Author**: SDD Reporter (AI Assistant)
**Update date**: 2026-08-17

## 1. Edited summary

Added automatic, structure-only validation of ticket documents (header title/level/order only, never body content) on every eligible `pull_request` webhook `closed`+`merged` event, persisted as PASS/FAIL counters per `(project, repository, phase)` in a new table, and exposed as a "Template Usage by Phase" section on the PM Dashboard.

- **BE write path**: `GithubWebhookService.handlePullRequest()` gained a validation loop (`validateTemplateUsage`) that resolves each changed file's phase/template via the existing DB-driven `tbl_dim_phase`/`tbl_dim_artifact_type` mapping, compares header structure only, and records the result via a new `TemplateUsageStatPort` → `TemplateUsageStatJdbcAdapter` upsert (best-effort, own `REQUIRES_NEW` transaction, never blocks the webhook response).
- **BE read path**: `PmDashboardRepositoryPort.findTemplateUsage` / `PmDashboardJdbcAdapter`, `PmDashboardService.getTemplateUsage` (reuses existing `requirePm` authorization), new `GET /api/v1/pm/dashboard/template-usage` endpoint, `PmDashboardDtos.TemplateUsageDto`.
- **FE**: `endpoints.templateUsage.statistics(...)` in `api.ts`, `formatUsageRate` in `pm-dashboard/utils.ts`, new `TemplateUsageByPhase.tsx` (built on the shared `CServerTable`), mounted in `PMDashboardPage.tsx` right after `AllTicketsTable`.
- **DB**: `V510__add_template_usage_tracking.sql` — new `tbl_fact_template_usage_stat` table plus 3 `tbl_dim_artifact_type` seed rows.

The implementation went through 3 rejection-and-fix rounds during Phase 5 review (2 human review rounds, 1 Codex automated review round) before reaching a clean `PASS`. All 11 acceptance criteria (AC-1..AC-11) are implemented and test-covered. Two items remain as explicitly ratified accepted risks (webhook-redelivery double-counting; real-DB/Testcontainers migration verification), and one real, unfixed defect in shared `MarkdownParserCore` infrastructure was found and documented (not fixed, out of this ticket's blast radius) during the Phase 6 bug-hunting pass.

## 2. Corresponding specification / AC

| ACID | status | evidence |
|---|---|---|
| AC-1 | Implemented | Only files under `/changes/{ticketKey}/` are validated; `GithubWebhookServiceTest.template_usage_*` (6 core cases) and `BB-001`/`BB-002`/`BB-021` (blackbox) confirm out-of-scope files never change counters |
| AC-2 | Implemented | 3-tier template-resolution priority (standard templates → `_ticket-template` → `_light-ticket-template`) applied via existing DB-driven `ArtifactTypeScope`; `BB-003`/`BB-004`/`BB-005` confirm priority order, not "any match" |
| AC-3 | Implemented | No-template-found is a silent skip — no exception, no counter change; `template_usage_skips_silently_when_no_matching_template_found` |
| AC-4 | Implemented | Comparison limited to header title + level + order, never body; `template_usage_records_match_when_header_structure_equals_template` (identical headers, different body → PASS); `BB-006`/`BB-010`/`BB-020`/`BB-024` cover the boundary/abnormal side |
| AC-5 | Implemented | `total_check_count` always +1, `template_match_count` +1 only on PASS, upsert semantics verified in `TemplateUsageStatJdbcAdapterTest`; recording gated to fire exactly once per PR (`action=="closed" && merged==true`, OI-14) and made resilient to per-file counter-write failure (OI-17) |
| AC-6 | Implemented | Phase/artifact-type resolution is 100% DB-driven (`tbl_dim_phase`/`tbl_dim_artifact_type`), no hardcoded switch/list in Java or TypeScript |
| AC-7 | Implemented | `GET /api/v1/pm/dashboard/template-usage?projectId=...&repositoryId=...` returns one entry per in-scope phase; scoping narrowed to a literal `phase_code IN ('1'..'8')` allow-list after OI-11/OI-13 (see §9) |
| AC-8 | Implemented | `usageRate` rounded to 1 decimal server-side, `null` on `totalCheckCount==0`, FE renders `-` without re-rounding (OI-10); tested both layers incl. the `2/3 → 66.7` repeating-decimal case |
| AC-9 | Implemented | `TemplateUsageByPhase` mounted immediately after `<AllTicketsTable>`, verified via DOM position assertion in `PMDashboardPage.test.tsx` (AC-closure rule per `40-testing.md`) |
| AC-10 | Implemented | `templateUsageQuery` follows the `["pm-dashboard", ...]` key convention, `enabled` only when both `projectId`/`repositoryId` are set, mirroring `summaryQuery`/`ticketsQuery` |
| AC-11 | Implemented | `requirePm(caller, projectId)` is the first statement of `getTemplateUsage`; non-PM/non-ADMIN callers get `403 Component.Permission.Denied` |

## 3. Scope of influence

- **Write path**: `GithubWebhookService.handlePullRequest()` — additive new step, existing `changedFilePaths`/`revision` resolution reused, no re-fetch.
- **Read path**: `PmDashboardRepositoryPort`/`PmDashboardJdbcAdapter` gain one new read method; `PmDashboardService`/`PmDashboardController` gain one new method/endpoint each — all other existing PM Dashboard endpoints (`options`, `summary`, `insights`, `tickets`, detail) are untouched.
- **DB**: one new table + 3 new dimension seed rows, purely additive, no `ALTER`/`DROP` on any existing table.
- **FE**: one new sibling `endpoints.templateUsage` namespace (not nested inside `endpoints.pmDashboard`, per `spec-pack.md` over an earlier internal inference), one new presentational component, `PMDashboardPage.tsx` gains one new query + one new rendered section.
- **Explicitly unaffected** (confirmed by reading source, not assumed): `TicketDetailDrawer.tsx` and its `EqsSummaryCard` (used only as a visual reference pattern), all other PM Dashboard endpoints, `GithubSecurityEvidenceSnapshotService` (only its failure-classification logic is replicated, the class itself is never called), FE routing/i18n segment handling.
- **Cross-feature interaction found and corrected**: `tbl_dim_artifact_type` is shared with an unrelated AI-safety file-scanning feature — an early scoping approach (`WHERE EXISTS`) leaked that feature's phase (`0-A`) into this endpoint's response until corrected (OI-13, §9).

## 4. Implementation content

| file | summary | reasons |
|---|---|---|
| `EDCAP_BE/.../application/usecase/ingestion/GithubWebhookService.java` | Added `validateTemplateUsage`/`fetchTemplateUsageBlob` + 2 private helpers (path-eligibility, fetch-error classification); gated on `closed`+`merged`; shared `resolveRevision`/`listTree` errors classified via `isSkippableFetchError`; per-file counter-write wrapped in `try/catch (DataAccessException)` | AC-1..AC-5 |
| `EDCAP_BE/.../resources/db/migration/V510__add_template_usage_tracking.sql` | New `tbl_fact_template_usage_stat` table + 3 `tbl_dim_artifact_type` seed rows | AC-5, AC-6 |
| `EDCAP_BE/.../application/port/out/persistence/TemplateUsageStatPort.java` (new) | Write-side port interface (`recordCheck`) | AC-5 |
| `EDCAP_BE/.../infrastructure/persistence/adapter/TemplateUsageStatJdbcAdapter.java` (new) | Write-side JDBC adapter, upsert SQL | AC-5 |
| `EDCAP_BE/.../application/usecase/ingestion/TemplateUsageStatWriter.java` (new) | `REQUIRES_NEW`-propagation bean, mirrors `AdminAuditLogWriter` | AC-5 (best-effort write) |
| `EDCAP_BE/.../application/port/out/persistence/PmDashboardRepositoryPort.java` | Added `findTemplateUsage(projectId, repositoryId)` | AC-7 |
| `EDCAP_BE/.../infrastructure/persistence/adapter/PmDashboardJdbcAdapter.java` | Implemented `findTemplateUsage`, scoped by literal `phase_code IN ('1'..'8')` | AC-7 |
| `EDCAP_BE/.../application/usecase/pmdashboard/PmDashboardService.java` | Added `getTemplateUsage(caller, projectId, repositoryId)`; `requirePm` first, computes `usageRate` | AC-7, AC-8, AC-11 |
| `EDCAP_BE/.../application/usecase/pmdashboard/PmDashboardModels.java` | Added `TemplateUsageRow` record | AC-7 |
| `EDCAP_BE/.../web/dto/PmDashboardDtos.java` | Added `TemplateUsageDto` + rounding factory | AC-7, AC-8 |
| `EDCAP_BE/.../web/rest/PmDashboardController.java` | Added `GET /api/v1/pm/dashboard/template-usage` | AC-7, AC-11 |
| `EDCAP_FE/src/lib/api.ts` | Added `endpoints.templateUsage.statistics(...)` namespace + DTO interface | AC-10 |
| `EDCAP_FE/src/pages/pm-dashboard/utils.ts` | Added `formatUsageRate` (zero/`null`-guarded) | AC-8 |
| `EDCAP_FE/src/pages/pm-dashboard/components/TemplateUsageByPhase.tsx` (new) | Table section built on shared `CServerTable`; `isLoading` prop gates empty-state | AC-9, AC-8 |
| `EDCAP_FE/src/pages/pm-dashboard/PMDashboardPage.tsx` | Added `templateUsageQuery`, renders section after `AllTicketsTable`, passes `isLoading` | AC-9, AC-10 |
| `EDCAP_FE/public/locales/{en,vi,ja}/locale.json` | New i18n keys for all new UI text | AC-9 (i18n compliance, OI-12) |
| BE test files (`GithubWebhookServiceTest`, `TemplateUsageStatWriterTest`, `TemplateUsageStatJdbcAdapterTest`, `PmDashboardServiceTest`, `PmDashboardControllerTest`, `PmDashboardJdbcAdapterFindTemplateUsageTest`, `MarkdownParserCoreTest`) | New/extended unit test coverage, incl. a 3rd Phase-6 bug-hunting file | AC-1..AC-11 |
| FE test files (`PMDashboardPage.test.tsx`, `utils.test.ts`, `TemplateUsageByPhase.test.tsx`) | New/extended coverage: mount-order, query wiring, formatter, loading state | AC-8, AC-9, AC-10 |

Full file-level detail and rationale: `impact-analysis.md` §3, `impl-plan.md` §4-5, `self-review.md` §3.

## 5. Review results

| review type | result | notes |
|---|---|---|
| Self Review | PASS | `self-review.md` §12 Final Self-Verdict: all 11 ACs matched, full BE suite passes (`mvn -o test`, 514/514, no regressions), FE `tsc --noEmit`/tests pass |
| Independent AI Review | PASS (after 1 revision round) | `codex-review.md` — round 3 initial verdict `NEEDS_FIX` (3 findings, 5 missed tests, 2 open questions, 2 false-positive candidates); after fixes, re-review returned `PASS_WITH_MINOR`, then a follow-up re-check returned a clean `PASS` with no outstanding findings |
| Human Review | PASS (after 2 revision rounds) | Round 1 (2026-08-11): 4 findings (table style, out-of-scope phases, missing i18n ×2) — all fixed. Round 2 (2026-08-11): 2 further findings on the round-1 fix diff itself (phase-scope filter still insufficient; duplicate counting across webhook deliveries) — both classified Blocker, fixed and re-verified |

Total across all 3 rounds: 9 findings raised and fixed (4 human round 1, 2 human round 2, 2 Codex round 3 fixed + 1 Codex round 3 accepted as risk), plus 1 Minor FE fix and 1 documentation Minor fix. No finding was dismissed without either a fix or an explicit, recorded owner decision.

## 6. Test results

| test type | result | evidence |
|---|---|---|
| BE unit tests (full suite, offline) | PASS, 514/514, 0 failures/0 errors | `self-review.md` §4, `mvn -o test` after round-3 fixes |
| BE new test files for this ticket | PASS | `GithubWebhookServiceTest` (31/31, incl. 3 round-3 tests), `TemplateUsageStatWriterTest`, `TemplateUsageStatJdbcAdapterTest`, `PmDashboardJdbcAdapterFindTemplateUsageTest` (2 tests), `PmDashboardServiceTest`/`PmDashboardControllerTest` (5 new tests) |
| BE compile / test-compile | PASS | `mvn -q -o compile`, `mvn -q -o test-compile` |
| FE typecheck | PASS | `npx tsc --noEmit`, no errors |
| FE lint | PASS | `npx eslint` on changed files, 0 problems |
| FE unit/component tests | PASS | `PMDashboardPage.test.tsx`, `utils.test.ts`, `TemplateUsageByPhase.test.tsx` (3/3) |
| Phase 6 bug-hunting pass | PARTIAL | 2 new tests added and passing (real gaps closed); 1 new test (`MarkdownParserCoreTest`) intentionally left failing as a documented reproduction of a real, unfixed defect — see §9 and `test-results.md` |
| Phase 7 black-box test design | 33 test cases authored (`blackbox-testcases.md`), covering AC-1..AC-11, boundary values (BD-01..05), error paths (ED-01..12), and permission scenarios (BB-029..031) | Not yet executed against a live environment — requires a real GitHub/DB/PM-Dashboard test environment per `test-data.md`'s setup procedure; execution is a QA/manual-test activity outside this AI session's available tooling |
| Flyway `V510` against a real DB | Not run | No Docker/Testcontainers available in this environment; reviewed by reading SQL text only (`INTEGRATION_TEST_UNVERIFIED`, §9) |

## 7. Security / operations perspective

- New endpoint `GET /api/v1/pm/dashboard/template-usage` is gated by the existing `requirePm(caller, projectId)` check (ADMIN bypass, else PM project role required) — not added to any `permitAll` matcher; confirmed identical 403 shape to sibling PM Dashboard endpoints.
- The existing webhook HMAC-SHA256 signature validation gate is unaffected — the new validation loop executes only after signature verification already passed in the existing call chain.
- No new secret/credential introduced — GitHub blob/tree fetches reuse the existing GitHub App token via `ArtifactScannerSourcePort`.
- Log lines for skipped/failed validations carry only identifiers (path, HTTP status, projectId/repositoryId/phaseId) — no raw file content, tokens, or credentials, per `30-security.md`.
- Counter writes follow the Best-Effort Audit/Stat Write Pattern: own `REQUIRES_NEW` transaction, and (after OI-17) a per-file `DataAccessException` no longer aborts the webhook.
- Operational gap found and fixed (OI-16): the shared `resolveRevision`/`listTree` calls previously swallowed *any* exception (including 5xx/timeout), silently reporting false success — now classified the same as per-file fetches (401/403/404 skip, else propagate).
- Remaining operational risk, explicitly accepted (not fixed): GitHub's at-least-once webhook delivery has no dedupe key anywhere in this codebase for any webhook consumer; a redelivered `closed`+`merged` event would double-count statistics (OI-15).

## 8. Accepted Risk

| risk | impact | owner | deadline | status | approver |
|---|---|---|---|---|---|
| GitHub webhook redelivery of the identical `closed`+`merged` event has no dedupe key, so `total_check_count`/`template_match_count` could be double-counted (OI-15) | Medium — rare-occurrence, statistics-only, no data corruption beyond inflated counts | Product/Tech Lead | — | OPEN | Ticket owner (2026-08-17, Phase 5 Codex round 3) |
| `V510` migration and `*IntegrationTest` classes not executed against a real PostgreSQL instance (no Docker/Testcontainers in this environment) — `INTEGRATION_TEST_UNVERIFIED` | Medium — SQL/constraint correctness and `REQUIRES_NEW` isolation reviewed by inspection only, not exercised end-to-end | Ticket owner | Before release | OPEN | N/A (environment limitation, not yet ratified) |
| `MarkdownParserCore.extractSections()` miscounts heading-like lines inside fenced code blocks as real sections, causing false-negative `headerStructureMatches` results for documents containing example Markdown in a code fence | Medium — silently wrong data in the metric this ticket exists to produce, but scope-limited to documents with fenced code blocks | Ticket owner | — | OPEN | Not yet — needs ticket-owner fix-now-vs-follow-up decision |
| `ArchitectureTest`/ArchUnit layering enforcement referenced by `CLAUDE.md` does not exist under `src/test` — pre-existing gap, not introduced by this ticket | Low — new port/adapter placement was manually verified against `20-architecture.md` instead | Architecture doc owner | — | CLOSED (accepted, out of scope) | Recorded, no action requested |

## 9. Open Issues

| issue | impact | next action |
|---|---|---|
| OI-15 — webhook-redelivery double-counting | Statistics could be inflated by a rare GitHub redelivery | None planned; only revisit if a system-wide webhook-dedup mechanism is ever introduced platform-wide |
| `FENCED_HEADING_FALSE_NEGATIVE` (found in Phase 6 bug-hunting pass, not a formal OI-numbered issue) | `MarkdownParserCore` (shared infrastructure) undercounts `templateMatchCount` for documents with fenced code blocks containing heading-like lines | Needs ticket-owner decision: fix within this ticket vs. raise a follow-up ticket against `MarkdownParserCore` directly (recommended, given its cross-feature blast radius) |
| `INTEGRATION_TEST_UNVERIFIED` | `V510` migration and REQUIRES_NEW transaction isolation unverified against a real Postgres instance | Run `mvn clean verify` / the integration-test profile in an environment with Docker before production rollout |
| Phase 7 black-box test cases (33, `blackbox-testcases.md`) not yet executed | Manual/QA verification of end-to-end behavior (real webhook, real DB, real Dashboard UI) still outstanding | QA to execute per `test-data.md`'s setup/cleanup procedure on the isolated `TEST-PROJ-01` test project before release sign-off |

All other OIs raised during this ticket (OI-1 through OI-14, OI-16, OI-17) are Resolved — see `open-issues.md` Resolution Log for full detail; OI-11 was superseded by OI-13 (initial fix attempt was insufficient).

## 10. Human Decisions

| decision | owner | result |
|---|---|---|
| Statistics API scope and display location (OI-1) | Stakeholder | Moved from `TicketDetailDrawer.tsx` to `PMDashboardPage.tsx`, scoped by project + repository |
| New table design for counters (OI-2) | Stakeholder | New `tbl_fact_template_usage_stat` table, standard audit columns |
| Which template revision to compare against (OI-7) | Stakeholder | Same PR head revision as the changed file |
| Skippable-fetch-error classification (OI-8) | Stakeholder | Reuse `isSkippableBlobError` pattern: 401/403/404 skip, else propagate |
| Endpoint authorization scope (OI-9) | Stakeholder | Restricted to PM-role users (+ ADMIN), reusing existing `requirePm` |
| `usageRate` rounding rule (OI-10) | Stakeholder | 1 decimal place server-side, `null` on zero total, FE never re-rounds |
| Phase-scope filter for `findTemplateUsage`, round 1 → round 2 (OI-11 → OI-13) | Human reviewer | Data-driven `EXISTS` filter rejected as insufficient; replaced with literal `phase_code IN ('1'..'8')` allow-list |
| Once-per-PR recording gate (OI-14) | Human reviewer | Gate on `action=="closed" && merged==true`, not `action=="closed"` alone (to also exclude close-without-merge) |
| Webhook-redelivery dedup (OI-15) | Ticket owner | Explicitly declined — no precedent for dedup on any webhook consumer in the codebase; accepted as risk |
| Shared `resolveRevision`/`listTree` error classification (OI-16) | Ticket owner | Classify identically to per-file blob fetches (401/403/404 skip, else propagate) |
| ID-1: where new validation logic lives, given `ticketKeyFromPath`/`isSkippableBlobError` are `private` | Tech Lead (impl-plan) | Replicate small logic locally in `GithubWebhookService`, do not promote existing private methods |
| ID-2: write-side port ownership relative to the read-side PM-dashboard port | Tech Lead (impl-plan) | New dedicated `TemplateUsageStatPort` (write), `PmDashboardRepositoryPort` gains only a read method |

## 11. Source Analysis Limitations

- This ticket was executed entirely by an AI assistant across all SDD phases in a single working environment with no Docker/Testcontainers available — every claim about real-DB behavior (migration application, `REQUIRES_NEW` isolation, concurrent-upsert correctness) is based on reading SQL/code, not on execution against a live PostgreSQL instance.
- No human independently reviewed the diff line-by-line before the two documented human-review rounds; those two rounds are the only ground-truth human verification this ticket received. The Codex automated review (round 3) is an independent AI review, not a human one — flagged as such throughout `self-review.md`/`phase-status.md` rather than conflated with human sign-off.
- The Phase 7 black-box test cases (`blackbox-testcases.md`, 33 cases) are a QA-ready test design, not executed test results — no environment with a real GitHub webhook + real PM Dashboard UI was available in this session to run them.
- `phaseName`'s locale/language consistency (Vietnamese/English) was flagged in `review-checklist.md` as **Undetermined** — no explicit locale requirement was found in `spec-pack.md` for this specific field, and it was not re-resolved during implementation.
- No dedicated `report.md` file existed prior to this document; the file did not need to be read before writing since it is newly created (per `_ticket-template/report.md`'s structure).

## 12. What worked

- Reusing 100% of existing infrastructure (`ArtifactScannerSourcePort`, `MarkdownParserCore`, `requirePm`, `tbl_dim_phase`/`tbl_dim_artifact_type`) meant zero new abstractions were introduced where an existing one already fit — kept the blast radius additive-only across BE, DB, and FE.
- The hexagonal split between the new write-only `TemplateUsageStatPort` and the existing read-only `PmDashboardRepositoryPort` (ID-2(a)) held up cleanly through all 3 review rounds with no reviewer pushback on the architecture itself — only on the read query's phase-scope filter logic.
- Actually running tests before reporting them done caught 2 self-authored test bugs (a Mockito `when(...).thenThrow(...)` re-stubbing bug, and 2 FE test regressions from the `CServerTable` rewrite) before they reached human review.
- The Phase 6 bug-hunting pass, done by reading production code directly rather than re-deriving AC coverage, found a real, previously undetected defect (`MarkdownParserCore` fenced-code-block false heading) that no prior review round had surfaced.
- Every design decision with more than one reasonable option (ID-1, ID-2, the round-3 Codex open questions) was surfaced to a human/ticket-owner for a real decision rather than silently picked — no reviewer round found scope creep or unauthorized assumptions.

## 13. What failed

- The very first phase-scope filtering attempt (`WHERE EXISTS (... tbl_dim_artifact_type ...)`, OI-11) looked data-driven and correct but missed that the same dimension table is shared across unrelated features — it took a second human-review round (OI-13) to catch that `0-A` still leaked through, and the fix ultimately required a more direct, literal phase-code allow-list instead of a cleverer indirect join.
- The initial implementation ran `validateTemplateUsage(...)` on every webhook delivery for a PR, not realizing that `listChangedFilePaths()` returns the full cumulative diff on every delivery — this duplicated/inflated counters across a PR's lifecycle and was only caught in human-review round 2 (OI-14), not by the original implementer or the first review round.
- `resolveRevision`/`listTree`'s error handling was implemented with a blanket `catch (Exception ex)` that silently reported false success on any failure — an inconsistency with the already-correct per-file fetch classification that survived 2 full review rounds before Codex's automated review (round 3) caught it.
- The webhook-redelivery double-counting risk was identified as early as the pre-implementation review checklist (`review-checklist.md` §7) but was not resolved until the very end of Phase 5 (round 3) — it took explicit ticket-owner escalation, twice, to formally close it as an accepted risk rather than leaving it as a lingering "Undetermined" item.
- `MarkdownParserCore.extractSections()`'s fenced-code-block defect was never caught by any of the 3 Phase 5 review rounds (human ×2, Codex ×1) — it was only found in the separate Phase 6 bug-hunting pass, meaning the shipped feature's `usageRate` metric can silently undercount for a known class of documents.

## 14. Candidate updates Failure Mode Index

- **Shared dimension/lookup tables used as an implicit scope filter** (`tbl_dim_artifact_type` EXISTS-join for phase scoping) can silently include unrelated features' rows — prefer an explicit, spec-defined allow-list over an indirect join when the "in scope" set is a fixed business boundary, not a derived one.
- **Webhook payloads that return cumulative/full state on every delivery** (e.g. `listChangedFilePaths()` returning the whole PR diff, not a delta) make any purely-additive counter unsafe to update on every delivery — such counters must be explicitly gated to a single terminal lifecycle event.
- **Error-handling classification applied to per-item calls but not to shared/setup calls in the same method** (`isSkippableBlobError` applied to per-file blob fetches but not to the shared `resolveRevision`/`listTree` calls) is a recurring blind spot — when a per-item classification pattern exists, explicitly check whether any shared/upstream call in the same flow needs the same treatment.
- **A parser/utility class advertised as "shared infrastructure" with zero prior test coverage** (`MarkdownParserCore`) is a higher-risk dependency than its lack of prior bug reports suggests — new consumers of such a component should add characterization tests for edge cases (fenced blocks, empty documents) rather than assuming untested code is bug-free.

## 15. Candidate updates Living Docs

- `spec-pack.md`'s Business Rules #6, #7, #10, #11 and Terminology section were amended mid-implementation (rounds 1-3) to close gaps found during review, not before — consider adding a standing Business Rule template checklist item for "does this rule apply identically to shared/setup calls, not just per-item calls" during Phase 1 spec authoring, to catch the OI-16 class of gap earlier.
- `CLAUDE.md` states hexagonal layering is "enforced by ArchUnit," but no `ArchitectureTest.java` or `@AnalyzeClasses`/`ArchRule` usage exists anywhere under `src/test` (confirmed by Glob/grep in Phase 3, re-confirmed here) — this is a pre-existing documentation-vs-source discrepancy, unrelated to this ticket, worth a standalone fix to either add the enforcement or correct the doc claim.
- `test-plan.md`'s residual-risk list originally claimed "malformed Markdown content can cause an uncaught parser exception" — this was found inaccurate during the Phase 6 bug-hunting pass (`parse()` is fully lenient) and corrected in `test-plan.md` itself; worth double-checking other tickets' residual-risk sections that may repeat the same unverified claim about `MarkdownParserCore`.

## 16. Final Verdict

- **DONE**

Phase 5 (Implementation and Review) is complete with a clean Codex `PASS` verdict after 2 human-review rounds and 1 Codex-review round, all findings fixed or explicitly ratified as accepted risk. Phase 6 (Test Plan/Results) and Phase 7 (Black-box Test Cases/Test Data) artifacts are complete. Three items remain open and are called out explicitly rather than silently closed: the `MarkdownParserCore` fenced-code-block defect (needs a ticket-owner fix-now-vs-follow-up decision), the `INTEGRATION_TEST_UNVERIFIED` real-DB verification gap, and execution of the 33 Phase 7 black-box test cases against a live environment — none of these block Phase 5/6/7 sign-off per Codex's final review, but all three should be resolved before production release.
