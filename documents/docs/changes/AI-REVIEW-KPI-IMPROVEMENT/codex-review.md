# codex-review

## Verdict

PASS_WITH_MINOR

**Re-confirmation (2026-08-20):** The two Major findings were independently re-read and are fixed.
`AiReviewStatsParser` now fails closed for the four metrics that share one denominator: it persists
the whole group as null unless all four denominators are present and equal. This is the approved
interpretation for the existing V511 schema; no schema expansion is required. `PmDashboardJdbcAdapter`
now scopes both the fact rows and `tbl_dim_repository` by `projectId`, so an out-of-project repository
does not disclose its name. The remaining condition before merging this ticket is to remove/split the
unrelated `spring-dotenv` dependency from `EDCAP_BE/pom.xml`.

## Coverage

- Reviewed files: all unstaged BE/FE files listed by `git status`, including the new migration, parser, persistence adapter/writer, PM Dashboard API, FE component/API/i18n, and their directly related tests. Also reviewed `documents/docs/changes/AI-REVIEW-KPI-IMPROVEMENT/{spec-pack,impl-plan,review-checklist,self-review,context,impact-analysis,source-map}.md`, `documents/docs/AGENTS.md`, `documents/docs/architecture/*`, and `documents/docs/standards/*`.
- Not reviewed: `.claude/CLAUDE.md` and `.claude/rules/*` do not exist at the workspace root; no root `AGENTS.md` exists. I did not run a real PostgreSQL/Flyway migration, browser smoke test, or the full BE/FE suites.
- Commands run: `git -C EDCAP_BE/EDCAP_FE status --short`, tracked diffs plus all new-file contents; `mvn test -Dtest=AiReviewStatsParserTest,AiFindingStatWriterTest,AiFindingStatJdbcAdapterTest,GithubWebhookServiceTest,PmDashboardServiceTest,PmDashboardControllerTest,PmDashboardJdbcAdapterFindAiFindingStatsTest` (80 passed); `npx vitest run ...AiFindingStatsCard.test.tsx ...PMDashboardPage.test.tsx` (4 passed; existing jsdom `getComputedStyle` stderr warning); re-confirmation: `mvn -o test -Dtest=AiReviewStatsParserTest,PmDashboardJdbcAdapterFindAiFindingStatsTest` (13 passed).
- Constraints: the requested paths are actually rooted at `documents/docs/...`, and the workspace root is not itself a Git repository; BE and FE are separate worktrees. The full-suite results reported in self-review were not accepted as evidence because they were not rerun in this review.

## Findings

### [Major] Correctness: A not-applicable shared-denominator KPI is reported as 0% instead of null

**Status: Fixed and confirmed on 2026-08-20.**

`resolveSharedFindingTotal(...)` now requires all four shared denominators to be present and equal;
otherwise the parser nulls all counts in that shared group. Four regression tests cover one unavailable
row at each position. This is the approved fail-closed behavior under the current shared-denominator
schema.

- Evidence: `EDCAP_BE/src/main/java/com/sdd/platform/domain/service/markdown/aireviewstats/AiReviewStatsParser.java:59-69` selects the first non-null denominator from adoption/valid/false-positive/resolution, then persists it even when the numerator of another KPI is null. `PmDashboardJdbcAdapter.java:672-678` coalesces and sums that shared denominator, while `PmDashboardDtos.java` subsequently divides the null-derived numerator total by it. AC-AIRKI-4 requires the numerator/denominator of the KPI whose cell is not numeric to be `NULL` (`spec-pack.md:62`).
- Why: The schema has only one denominator for these four rates, so `firstNonNull(...)` loses which KPI supplied it. For example, with adoption = `không áp dụng`, valid = `2 / 2`, false-positive = `0 / 2`, resolution = `2 / 2`, the writer stores `ai_review_adopted_count = NULL` and `ai_review_finding_total_count = 2`; the aggregate becomes `SUM(NULL)` coerced to `0` / `2` = `0.0`, not null.
- Impact: PM Dashboard presents absence of data as an actual 0% adoption/valid/false-positive/resolution rate, corrupting the KPI used for management reporting.
- Fix: Resolve the representation before merge. Either require all four rows to share a valid denominator and skip the snapshot when any one is unavailable, or change the fact schema/model to retain an independent denominator/nullability for each of the four rates. Do not derive one metric's denominator from another unless the spec explicitly permits it.
- Test: Add parser + end-to-end writer/DTO tests for each of the four shared-denominator rows being `không áp dụng` while other rows remain numeric; assert the affected API rate is null, not 0.0.
- Confidence: High
- Release gate: Must fix before merge

### [Major] Security / data isolation: repository dimension row is not scoped to the authorized project

**Status: Fixed and confirmed on 2026-08-20.**

The query now includes `AND r.project_id = :projectId`. The revised adapter test asserts project
scope for both the dimension row and left-joined facts, and the new cross-project test expects no row.
Returning the existing no-data fallback for an out-of-project repository is accepted because it does
not reveal whether that repository exists.

- Evidence: `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/PmDashboardJdbcAdapter.java:679-684` filters only `r.repository_id = :repositoryId`; `:projectId` restricts the left-joined fact rows only. The new regression test explicitly asserts that `projectId` is absent from the `WHERE` clause (`PmDashboardJdbcAdapterFindAiFindingStatsTest.java:42-49`). AC-AIRKI-10 says PM access is scoped to the requested project (`spec-pack.md:68`), and the contract is a repository selected within that project.
- Why: A PM authorized for project A can call the endpoint with a repository UUID belonging to project B. The query still returns project B's `repositoryId` and `repo_name_masked`; it merely joins no project-B fact rows because `s.project_id = A` is in the join condition.
- Impact: This leaks a cross-project repository identity/name and returns a misleading all-null KPI object instead of treating the repository as out of scope. It also leaves a tenant-boundary gap that the test currently preserves.
- Fix: Scope the dimension row as well, e.g. add `AND r.project_id = :projectId` to the repository predicate (keeping fact predicates in `ON` as appropriate for the left join). Decide and document the response for an out-of-scope/nonexistent repository, then update the service/controller behavior consistently.
- Test: Add an adapter test with repository B and requested project A that asserts the query includes dimension-level project scoping; add a service/controller authorization test proving a PM in A cannot obtain B's repository name or stats.
- Confidence: High
- Release gate: Must fix before merge

### [Question] Scope / supply chain: unrelated `spring-dotenv` dependency is included in the review diff

**Status: Decision recorded on 2026-08-20 — remove it from this ticket/PR (or split it into its own
specified change).** This is the only remaining merge hygiene action from this review.

- Evidence: `EDCAP_BE/pom.xml:72-76` adds `me.paulschwarz:spring-dotenv:5.0.1`, while it is absent from the ticket's planned-change list. `self-review.md` itself identifies it as unrelated and uncommitted.
- Why: The ticket does not need or reference this runtime dependency, and no changed source uses it.
- Impact: It expands the production dependency graph and makes the ticket diff non-atomic without an approved purpose, configuration assessment, or tests.
- Fix: Remove it from this change set or split it into a separately specified/reviewed change before merge.
- Test: N/A for this ticket; the separate change should demonstrate its intended configuration behavior and dependency-security review.
- Confidence: High
- Release gate: Must fix before merge

## Missed tests

- AC-AIRKI-3 explicitly requires the real `documents/docs/changes/AI-REVIEW-KPI-IMPROVEMENT/raw/ai-review.md` fixture. `AiReviewStatsParserTest.java:50-63` instead uses a manually recreated text block; it will not detect drift from the real artifact.
- No redelivery test invokes the webhook flow twice and verifies the persisted snapshot remains one overwrite. The current unit test only verifies SQL contains `ON CONFLICT`; this is weaker than AC-AIRKI-6's behavior-level assertion.
- No test covers the per-KPI unavailable-value case described in the first Major finding.
- No database-backed Flyway test verifies V511's FK, unique constraint, trigger, and real Postgres `ON CONFLICT` behavior.

## Questions for human

- For the four rates sharing `ai_review_finding_total_count`, what is the required API result if exactly one rate is not applicable? The current schema cannot encode null denominator independently for that rate; the answer determines whether schema expansion or snapshot skipping is correct.
- Should an existing repository belonging to another project return 403, 404, or the same no-data object? It must not expose the cross-project repository name.

## False positive candidates

- FE renders cards rather than the `CDataTable` named in AC-AIRKI-11. This is a documented, pre-approved deviation in `impl-plan.md`; I did not report it as a defect.
- Missing-param behavior appears inconsistent between the spec's expected 400 and existing application behavior. The implementation plan documents an approved decision to retain existing behavior, so this is not reported as a ticket defect.

## Good decisions worth keeping

- The webhook hook is placed after `ticketScopes` are built and before the closed-PR early return.
- The write path uses a separate `REQUIRES_NEW` bean and overwrite-style `ON CONFLICT (ticket_id)` update, preserving the primary webhook flow and redelivery idempotency design.
- The FE query key includes both project and repository IDs, is gated until both exist, and locale keys were added for en/ja/vi.
