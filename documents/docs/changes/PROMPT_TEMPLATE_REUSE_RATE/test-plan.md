# Test Plan

**Ticket ID**: PROMPT_TEMPLATE_REUSE_RATE
**Create date**: 2026-08-17
**Author**: SDD Test Strategist
**Update date**: 2026-08-17

## 1. Purpose

- Map each of the 11 ACs in `spec-pack.md` §7 to a concrete test decision: which test type(s) actually guarantee it, and whether that guarantee already exists or needs new work.
- Phase 5 (3 review rounds: 2 human, 1 Codex automated) already drove substantial test-writing alongside each fix. This plan's job is to verify that coverage is genuine and complete against the ACs — not to mechanically add one test per test-type per AC.
- Explicitly check the cross-cutting concerns that AC wording alone tends to hide: auth/permission, tenant scoping, idempotency/duplicate submit, rollback, race conditions, timeout, boundary values, malformed/null/empty input, and full-width numeric input.
- Record what is intentionally left untested this phase, with the reasoning and residual risk for each.

## 2. AC Matrix ↔ Test Type

| AC ID | FE UT | FE Component | BE UT | API/Web slice | DB/Migration | E2E | Decision |
|---|---|---|---|---|---|---|---|
| AC-1 — only files under the ticket's working-dir prefix are considered | — | — | `GithubWebhookServiceTest` (`template_usage_records_match_when_header_structure_equals_template`, path-prefix filtering via `ticketKeyFromPath`) | — | — | Skip | **Guaranteed by existing test.** Reuses the pre-existing `ticketKeyFromPath` path-scoping already proven by non-template-usage webhook tests in the same class. |
| AC-2 — 3-tier template resolution, first match wins | — | — | `GithubWebhookServiceTest` (`template_usage_skips_silently_when_file_not_mapped_to_a_phase`, `resolveTemplatePath` exercised implicitly via match/no-match cases) | — | — | Skip | **Guaranteed by existing test.** Resolution logic is DB-driven (`findArtifactTypes()`), not new priority code invented for this ticket — see AC-2 note in `spec-pack.md`. |
| AC-3 — no template found → skip silently, no counters change | — | — | `template_usage_skips_silently_when_no_matching_template_found`, `template_usage_skips_silently_on_401_403_404_blob_fetch_errors` (both assert `verify(templateUsageStatWriter, never()).recordIndependently(...)`) | — | — | Skip | **Guaranteed by existing test.** |
| AC-4 — header name/level/order only, body content ignored | — | — | `template_usage_records_match_when_header_structure_equals_template` (identical headers, differing body prose → `matched=true`), `template_usage_records_no_match_when_header_structure_differs` | — | — | Skip | **Guaranteed by existing test.** |
| AC-5 — counter increments, exactly-once-per-PR gating, best-effort write | — | — | `TemplateUsageStatJdbcAdapterTest` (both increment branches), `template_usage_skips_when_action_is_opened_synchronize_or_reopened_even_if_merged_true`, `template_usage_skips_when_pull_request_is_closed_but_not_merged`, `template_usage_continues_processing_remaining_files_when_counter_write_fails` | — | `V510__add_template_usage_tracking.sql` reviewed (not executed — see §6) | Skip | **Mostly guaranteed by existing test.** Real transactional isolation of `REQUIRES_NEW` (does the counter write genuinely commit independently of a rolled-back parent transaction under real Postgres) is **not** verified end-to-end — see Residual Risks. Webhook-redelivery duplicate-submit is an accepted risk (OI-15), intentionally untested — see §6. |
| AC-6 — file→phase mapping is 100% data-driven, no hardcoded phase list | — | — | Confirmed by code reading (no switch/hardcoded list in `validateTemplateUsage`); indirectly exercised by every `template_usage_*` test using DB-row-driven `findArtifactTypes()` mocks | — | — | Skip | **Guaranteed by existing test + code inspection.** No dedicated "add a new phase via data only" test was written — low value given the mapping is a single `Map` built from a port call already mocked generically in every test. |
| AC-7 — API returns exactly the 8 in-scope phases, project+repo scoped | — | — | — | `PmDashboardControllerTest.templateUsage_returnsDtoListWithComputedUsageRate`, `PmDashboardJdbcAdapterFindTemplateUsageTest.findTemplateUsage_scopesToInScopePhaseCodesOnly` (asserts literal `phase_code IN ('1'..'8')`, absence of `0-A`/`EXISTS`, and that `projectId`/`repositoryId` are bound as SQL params) | Schema/query reviewed, not executed against real DB | Skip | **Guaranteed by existing test.** Tenant scoping is verified at the param-binding level (mocked JDBC template); real cross-project/cross-repository row isolation under Postgres is covered only by code review, not a live query — see Residual Risks. |
| AC-8 — `usageRate` rounding + `null`/`-` on zero count | `utils.test.ts` (`formatUsageRate`: `null` → `"-"`, numeric → `"N%"`) | — | `PmDashboardControllerTest` (`usageRate=50.0` and zero-count row `usageRate` absent/null) | (same as BE UT column) | — | Skip | **Guaranteed by existing test.** Boundary case `totalCheckCount==0` explicitly asserted both server- and client-side. |
| AC-9 — "Template Usage by Phase" section mounted after `AllTicketsTable`, per-phase fields shown | `TemplateUsageByPhase.test.tsx` (loading/empty/populated states) | `PMDashboardPage.test.tsx` (`compareDocumentPosition` mount-order assertion) | — | — | — | Skip | **Guaranteed by existing test.** Satisfies the `40-testing.md` AC-closure rule: the section is verified mounted on its composing page, not only as an isolated component test. |
| AC-10 — FE query key convention, enabled only when project+repo selected | — | `PMDashboardPage.test.tsx` (asserts `endpoints.templateUsage.statistics` called with `{projectId, repositoryId}`) | — | — | — | Skip | **Guaranteed by existing test.** No dedicated test asserts the query is *disabled* when `projectId`/`repositoryId` are empty — see §6 (low value: identical `enabled` pattern already covered for `summaryQuery`/`ticketsQuery` in `PM-DASHBOARD`'s own test-plan). |
| AC-11 — PM-role/ADMIN-only, 403 otherwise | — | — | `PmDashboardServiceTest` (`templateUsage_requiresPmRole`, `templateUsage_allowsAdminRoleWithoutProjectRoleLookup`, `templateUsage_allowsProjectRolePmEvenWhenSystemRoleIsDifferent`) | `PmDashboardControllerTest.templateUsage_mapsForbidden` (403 `Component.Permission.Denied`) | — | Skip | **Guaranteed by existing test.** Full permission matrix (PM project role, ADMIN system-role bypass, non-PM viewer, no project role) already exercised. |

### Cross-cutting checks (not 1:1 with a single AC)

| concern | status | evidence / reasoning |
|---|---|---|
| Auth / permission | Covered | AC-11 row above; 403 mapped consistently with other PM Dashboard endpoints. |
| Tenant scoping (project/repository) | Covered (param-binding level) | `findTemplateUsage_scopesToInScopePhaseCodesOnly` asserts `projectId`/`repositoryId` bound as SQL params; row-level isolation under a real DB is not separately verified — folded into the existing `INTEGRATION_TEST_UNVERIFIED` risk (§6/§7). |
| Idempotency / duplicate submit (webhook redelivery) | **Intentionally not tested** | This is exactly Finding 1 from Codex round 3 — ticket owner explicitly declined a dedup mechanism (`OI-15`, Accepted Risk). Writing a test would only encode the absence of a guarantee, not verify one; see §6. |
| Rollback | Partially covered | `template_usage_continues_processing_remaining_files_when_counter_write_fails` proves a `DataAccessException` on one file's counter write does not abort the PR's remaining files or the webhook (application-level "rollback" of just that one write). Real Postgres-level transaction rollback under `REQUIRES_NEW` is not exercised — see Residual Risks. |
| Race (concurrent webhook deliveries for the same PR) | **Not tested** | No test simulates two concurrent `closed`+`merged` deliveries racing on the same counter row. Same root cause as the idempotency gap (OI-15) — see §6. |
| Timeout / non-HTTP transport errors | Covered | `template_usage_propagates_when_blob_fetch_fails_with_non_skippable_error` and `template_usage_propagates_when_tree_fetch_fails_with_non_skippable_error` prove any error other than 401/403/404 (including a genuine timeout, which surfaces as a non-`WebClientResponseException` and simply isn't caught by the classifying `catch` block at all) propagates and fails the webhook — the same outcome either way. |
| Boundary values | Covered | `totalCheckCount==0` → `usageRate=null`/`"-"` (AC-8); zero eligible files → early return, no validation triggered (`GithubWebhookService.java:497-503`, exercised implicitly by tests with no matching-path files). |
| Malformed / null / empty input | Mostly covered; one inherited gap noted, one prior claim corrected | Malformed webhook JSON is covered by pre-existing `rejects_malformed_json_after_signature_check` (not template-usage-specific, reused as-is). `changedFilePaths == null` is explicitly handled (`List.of()` fallback, line 497). **Correction (Phase 6 bug-hunting pass, verified by reading `MarkdownParserCore.java` source directly):** the earlier claim in this row that `markdownParserCore.parse(...)` could throw an uncaught exception on malformed content was **inaccurate** — `parse()` is fully lenient (null-safe normalization + regex + the `commonmark` library, which never throws on malformed Markdown) and represents anomalies as `errors()`/`warnings()` data, not thrown exceptions. The real, verified gap is narrower: `headerStructureMatches` never reads `.errors()`/`.warnings()`, so a blank/header-less document's anomaly signal is silently discarded (observability gap, not a crash risk). Separately, a genuinely real defect *was* found in this same parser — see §6/§7 item 3 and `test-results.md` §5/§6. |
| Full-width / non-ASCII numeric input | **N/A** | This feature has no free-text or user-typed numeric input anywhere in its surface (all counts/percentages are server-computed from webhook/DB data, never parsed from user-entered strings), unlike tickets with numeric form fields. No test needed. |

## 3. Priority

| test item | priority | reason |
|---|---|---|
| Counter correctness (increment + gating on closed+merged) | P0 | Core business value of the ticket; a bug here silently corrupts a metric with no user-facing error |
| Permission gating (AC-11) | P0 | Security boundary, same pattern as all other PM Dashboard endpoints |
| Best-effort write / non-abort on counter-write failure (AC-5, OI-17) | P0 | Availability — a transient DB blip must not fail unrelated webhook processing (artifact scanning, ticket status) |
| Skippable vs. non-skippable fetch-error classification (AC-3, OI-16) | P0 | Prevents both false "success" on real outages and false "failure" on routine 404s |
| Phase scoping (AC-7) | P0 | Directly caused 2 prior review rejections (OI-11, OI-13); highest historical defect density in this ticket |
| Usage-rate rounding/null handling (AC-8) | P1 | User-visible display correctness, low complexity |
| FE mount/query wiring (AC-9, AC-10) | P1 | Required for the AC-closure rule; already Codex-verified |
| Redelivery dedup (OI-15) | Deliberately deprioritized to P3 / Accepted Risk | Ticket-owner decision — no existing precedent for webhook dedup anywhere in the codebase |
| Real-DB migration/transaction verification (`INTEGRATION_TEST_UNVERIFIED`) | P2, deferred | Blocked on Docker/Testcontainers availability in this environment, not on missing test design |

## 4. Reuse Existing Tests

| existing test | path | covers | gap |
|---|---|---|---|
| `GithubWebhookServiceTest` (11 `template_usage_*` cases) | `EDCAP_BE/src/test/java/.../ingestion/GithubWebhookServiceTest.java` | AC-1..AC-6; skippable/non-skippable fetch-error classification (OI-16); once-per-PR gating; best-effort counter write (OI-17) | None for these ACs; redelivery-dedup and concurrent-delivery race are out of scope by design (OI-15) |
| `TemplateUsageStatJdbcAdapterTest` | `EDCAP_BE/src/test/java/.../persistence/adapter/TemplateUsageStatJdbcAdapterTest.java` | AC-5 upsert SQL/param shape for both match branches | Real upsert execution against Postgres (`ON CONFLICT`) unverified — Testcontainers gap |
| `TemplateUsageStatWriterTest` | `EDCAP_BE/src/test/java/.../ingestion/TemplateUsageStatWriterTest.java` | Delegation from writer bean to port | `@Transactional(REQUIRES_NEW)` propagation itself is a Spring-container behavior, not unit-testable without an integration context — Testcontainers gap |
| `PmDashboardJdbcAdapterFindTemplateUsageTest` | `EDCAP_BE/src/test/UnitTest/java/.../PmDashboardJdbcAdapterFindTemplateUsageTest.java` | AC-7 literal phase-code scoping, projectId/repositoryId param binding | Real cross-tenant row isolation under Postgres unverified — Testcontainers gap |
| `PmDashboardServiceTest` (3 `templateUsage_*` cases) | `EDCAP_BE/src/test/UnitTest/java/.../pmdashboard/PmDashboardServiceTest.java` | AC-11 full permission matrix | None |
| `PmDashboardControllerTest` (2 `templateUsage_*` cases) | `EDCAP_BE/src/test/UnitTest/java/.../web/rest/PmDashboardControllerTest.java` | AC-7, AC-8, AC-11 at the DTO/HTTP-mapping level | None |
| `utils.test.ts` (`formatUsageRate`) | `EDCAP_FE/src/__ tests __/pm-dashboard/utils.test.ts` | AC-8 client-side formatting | None |
| `TemplateUsageByPhase.test.tsx` | `EDCAP_FE/src/__ tests __/pm-dashboard/TemplateUsageByPhase.test.tsx` | AC-9 loading/empty/populated states | None |
| `PMDashboardPage.test.tsx` | `EDCAP_FE/src/__ tests __/pm-dashboard/PMDashboardPage.test.tsx` | AC-9 mount order, AC-10 query invocation | Does not assert the query is *disabled* pre-selection (low value, identical pattern already proven for `summaryQuery`/`ticketsQuery`) |
| `GithubWebhookControllerTest` (pre-existing, not template-usage-specific) | `EDCAP_BE/src/test/java/.../web/webhook/GithubWebhookControllerTest.java` | HMAC signature verification, malformed-JSON rejection at the HTTP boundary | Reused as-is; no template-usage-specific HTTP-layer test was added, since the service-layer propagation test is the established boundary for this class |

**Conclusion: for all 11 ACs, existing tests already provide the guarantee.** No AC requires new test-writing in Phase 6.

## 5. Tests Added/Updated This Phase

None of the 11 ACs required new test-writing to close — all test coverage needed for AC closure was already written and verified across Phase 5's three revision rounds (see `self-review.md` §2/§3/§4 and `codex-review.md`'s final `PASS`).

A **separate bug-hunting pass** (Principal Test Engineer / Bug Hunter role, run after this test-plan was first written) added 3 tests targeting high-signal gaps found by reading the target production code directly, not by re-deriving AC coverage:

| test | file | protects against |
|---|---|---|
| `template_usage_skips_file_when_tree_entry_has_blank_sha` | `GithubWebhookServiceTest.java` | A tree entry present but with a blank/null `sha` (distinct branch from "path absent from tree", already covered) silently corrupting a comparison instead of being treated as content-unavailable |
| `findTemplateUsage_scopesTenantInsideLeftJoinOnClause_notInWhereClause` | `PmDashboardJdbcAdapterFindTemplateUsageTest.java` | A future refactor moving project/repository scoping into `WHERE`, which would silently drop zero-activity phases from the response instead of showing them at `totalCheckCount=0` |
| `sections_should_ignore_heading_like_lines_inside_fenced_code_blocks` | `MarkdownParserCoreTest.java` (new file — no test previously existed for this class) | **Left intentionally failing** — reproduction of a real, previously-undetected defect (see §6/§7 item 3 and `test-results.md` §5/§6), not fixed in this pass |

Full detail, commands run, and results: `test-results.md`.

## 6. Areas Intentionally Left Untested This Time

| area | reason | residual risk |
|---|---|---|
| Webhook redelivery / duplicate-submit dedup (OI-15) | Explicit ticket-owner decision: no dedup/idempotency mechanism exists anywhere else in the codebase for GitHub webhook redelivery; adding one solely for this feature was judged unwarranted. Writing a test here would only pin down the *absence* of a guarantee, not verify a real one. | If GitHub redelivers the identical `closed`+`merged` event, `total_check_count`/`template_match_count` will be double-counted. Accepted, tracked as `OI-15` / `REDELIVERY_DEDUPE_GAP`, owner: Product/Tech Lead. |
| Concurrent webhook deliveries racing on the same counter row | Same root cause as above — no dedup/locking exists to race against; a dedicated concurrency test would only prove the counter's known non-idempotent behavior under a second independent trigger (concurrency), not surface new information. | Same class of risk as OI-15 — bounded by the same accepted-risk decision. |
| Real Postgres execution of `V510__add_template_usage_tracking.sql` and the `REQUIRES_NEW` transaction boundary | No Docker/Testcontainers runtime available in this development environment; tracked since Phase 5 round 1 as `INTEGRATION_TEST_UNVERIFIED`, re-surfaced (not newly found) by Codex round 3. | Migration syntax/constraint errors, or `REQUIRES_NEW` not actually isolating the transaction as intended, would only surface at deploy time or in a CI environment with real Postgres. |
| ~~Markdown-parser exception on genuinely malformed file content~~ **(superseded — see below)** | Corrected in the Phase 6 bug-hunting pass after reading `MarkdownParserCore.java` directly: `parse()` never throws on malformed/blank/header-less content; it is fully lenient. The "uncaught exception" risk previously recorded here did not exist. | N/A — claim retracted. |
| A `changed`-or-`template` file containing a fenced code block (` ``` `) with a heading-like line inside it | `MarkdownParserCore.extractSections()` is a plain per-line regex scan with no fenced-code-block awareness — confirmed a **real defect**, not a hypothetical, via a new failing reproduction test (`MarkdownParserCoreTest.sections_should_ignore_heading_like_lines_inside_fenced_code_blocks`, intentionally left failing, not fixed — see `test-results.md` §5/§6 for root cause/impact). Not fixed in this pass because `MarkdownParserCore` is shared infrastructure beyond this ticket's scope; disposition needs a ticket-owner decision. | A document that is genuinely template-compliant but includes an example Markdown snippet in a fenced code block gets phantom extra sections, causing `headerStructureMatches` to report a false mismatch — a silent false-negative that undercounts `templateMatchCount`/`usageRate`, the exact metric this ticket exists to produce. |
| FE `enabled: false` pre-selection state for `templateUsageQuery` | Identical `enabled` gating pattern already proven correct for `summaryQuery`/`ticketsQuery` in the `PM-DASHBOARD` ticket's own test suite; duplicating that assertion here adds cost with no new information. | Negligible — shared hook usage, not new logic. |
| E2E (Playwright) coverage of the Template Usage section | The existing `pm-dashboard.spec.ts` E2E suite (from the `PM-DASHBOARD` ticket) predates this feature and was not extended; AC-9's mount-order requirement is already satisfied by `PMDashboardPage.test.tsx` per the `40-testing.md` AC-closure rule, and the added value of a full E2E pass (mock-routed, no live backend) over the existing component-level mount assertion is judged low relative to the cost of adding and maintaining a new Playwright spec. | If `PMDashboardPage`'s real router/query-client wiring diverges from what the component test mocks, a live-page rendering issue could go undetected until manual QA. |

## 7. Residual Risks

1. **OI-15 — webhook-redelivery double-counting (Accepted Risk, no mitigation planned).** Ratified by the ticket owner; only closes if/when a system-wide webhook-dedup mechanism is ever introduced, at which point this feature would need to opt in.
2. **INTEGRATION_TEST_UNVERIFIED — real-DB migration/transaction behavior unverified.** Depends on Docker/Testcontainers becoming available in this or a CI environment; recommend running the full migration + a live redelivery scenario against a real Postgres instance before production rollout, even though it is not a Phase 6 blocker per Codex's final `PASS` verdict.
3. **Fenced-code-block false-heading defect in `MarkdownParserCore.extractSections()` (newly found, Phase 6 bug-hunting pass) — not fixed, reproduction test left failing on purpose.** Supersedes the earlier (incorrect) "parser exception on malformed content" claim, which was retracted after reading the parser source directly and confirmed to never apply (`parse()` never throws). The real defect: heading-like lines inside ` ``` ` fences are miscounted as real sections, causing false-negative template-usage comparisons. Needs a ticket-owner decision on whether to fix within this ticket or track as a follow-up against the shared `MarkdownParserCore` component — see `test-results.md` §9.
4. **No dedicated E2E for the Template Usage section.** Acceptable given the AC-closure rule is already satisfied at the component level; if the PM Dashboard's routing/query-client setup changes in a future ticket, consider adding an E2E scenario at that time.

None of the above are classified as Phase 6 blockers — Codex's round-3 re-review (`codex-review.md`, final verdict `PASS` after the sole Minor was fixed) already independently confirmed OI-15's accepted-risk status and did not flag the migration/parser gaps as release-blocking.
