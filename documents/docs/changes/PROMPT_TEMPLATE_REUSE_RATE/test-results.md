# Test Results

**Ticket ID**: PROMPT_TEMPLATE_REUSE_RATE
**Create date**: 2026-08-17
**Author**: Principal Test Engineer / Bug Hunter (Phase 6 bug-hunting pass)
**Update date**: 2026-08-17

## 1. Execution Environment

| item | value |
|---|---|
| OS | Windows 11 Pro |
| Backend | Java 21, Spring Boot 3.4.1, Maven (offline, `-o`) |
| DB | None — unit tests only, `NamedParameterJdbcTemplate`/ports mocked (Mockito) |
| Scope of this pass | New/updated tests only; no production code was modified |

## 2. Executed Command

| command | result | log/evidence | note |
|---|---|---|---|
| `mvn -q -o -Dtest=MarkdownParserCoreTest test` | **FAIL (expected)** | `Tests run: 1, Failures: 1` — `expected: <3> but was: <4>` | Intentional reproduction test for a real defect found this pass (see §5, §6) |
| `mvn -q -o -Dtest=GithubWebhookServiceTest test` | PASS | full class incl. new `template_usage_skips_file_when_tree_entry_has_blank_sha` ran clean, exit 0 | Confirms the silent-null-sha branch in `fetchTemplateUsageBlob` behaves as specced |
| `mvn -q -o -Dtest=PmDashboardJdbcAdapterFindTemplateUsageTest test` | PASS | new `findTemplateUsage_scopesTenantInsideLeftJoinOnClause_notInWhereClause` ran clean, exit 0 | Confirms tenant scoping lives in the `LEFT JOIN ... ON` clause, not `WHERE` |
| `mvn -q -o test-compile` | PASS | exit 0 | Confirms the new test files do not break compilation of the wider test source tree |

## 3. Summary of Results

This was a **bug-hunting pass** (Principal Test Engineer / Bug Hunter role), not a coverage-padding pass. Goal was high-signal tests that catch regressions, boundary bugs, and data-inconsistency the existing suite did not exercise. Three genuinely untested code paths were identified by reading the target production code directly (`GithubWebhookService.java`, `MarkdownParserCore.java`, `PmDashboardJdbcAdapter.java`) against the existing test files, rather than by guessing:

1. **A real, previously-undetected defect** in `MarkdownParserCore.extractSections()` — confirmed via a new failing reproduction test. Not fixed in this pass (see rule "don't paper over a bug — leave a reproduction test, report cause and impact").
2. Two genuine **test gaps** (not defects) closed with new passing tests — both protect against a plausible future regression that no existing test would catch.

No existing test was weakened, deleted, or had its assertions loosened to make anything pass. No new tests are restatements of existing ones.

## 4. List of Passes

| TC ID | test | result | note |
|---|---|---|---|
| TC-PROMPT_TEMPLATE_REUSE_RATE-1 | `GithubWebhookServiceTest.template_usage_skips_file_when_tree_entry_has_blank_sha` | PASS | New. Protects: a tree entry that exists but carries a blank/null `sha` (distinct branch from "path absent from tree", already covered) is treated as content-unavailable and the file is skipped — not an NPE, not a crash, not a false match/mismatch record. |
| TC-PROMPT_TEMPLATE_REUSE_RATE-2 | `PmDashboardJdbcAdapterFindTemplateUsageTest.findTemplateUsage_scopesTenantInsideLeftJoinOnClause_notInWhereClause` | PASS | New. Protects: project/repository scoping stays inside the `LEFT JOIN ... ON` clause. Guards against a future refactor moving that predicate into `WHERE`, which would silently convert the join to an inner join and make phases with zero activity for a project/repo disappear from the response instead of showing `totalCheckCount=0` (spec-pack.md boundary example, phase `"7"`). |

## 5. List of Fails

| TC ID | test | cause | action | status |
|---|---|---|---|---|
| TC-PROMPT_TEMPLATE_REUSE_RATE-3 | `MarkdownParserCoreTest.sections_should_ignore_heading_like_lines_inside_fenced_code_blocks` | **Real product defect**, not a test bug: `extractSections()` scans every line with `HEADING_PATTERN` and has no fenced-code-block (` ``` `) awareness. A line that merely *looks* like a heading (`^#{1,6}\s+...$`) inside a fenced code block is counted as a real section. | **Left as a failing reproduction test on purpose** — not fixed, not disabled, not asserted-to-the-buggy-value. Root cause and impact reported in §6. Disposition (fix now vs. track as a follow-up) needs a ticket-owner decision — see §9. | OPEN |

## 6. Bugs Fixed

_None._ Per this pass's rules, a discovered bug is documented with a reproduction test and a root-cause/impact report, not silently patched. No bug was fixed in this pass.

**Bug found — root cause and impact (for the fail in §5):**
- **Root cause**: `MarkdownParserCore.extractSections()` (`EDCAP_BE/src/main/java/.../markdown/core/MarkdownParserCore.java`) is a pure line-by-line regex scan. It has no state tracking whether the current line is inside a ` ``` ` fenced code block, so any line matching `^#{1,6}\s+(.+?)\s*$` is treated as a real Markdown section heading — including lines that are example text *inside* a code fence.
- **Impact on this ticket**: `GithubWebhookService.headerStructureMatches(...)` — the method AC-4 depends on — compares `changed.sections().size()` against `template.sections().size()` and each section's `level()`/`title()`. Any changed-or-template document that includes an example Markdown snippet inside a fenced code block (a normal thing to do in documentation — including this very ticket's own template files, several of which show example section headers inside ` ``` ` fences) gets one or more phantom extra sections. The section counts then diverge from a genuinely template-compliant document, and `headerStructureMatches` reports `false` — a silent **false-negative** that undercounts `templateMatchCount`, corrupting the `usageRate` metric this feature exists to produce. This is a **contract violation** of AC-4's intent ("so sánh cấu trúc header ... FAIL nếu khác thực sự"), not an intentional design boundary.
- **Severity**: Major (silently wrong data in the metric that is this ticket's entire purpose), but scope-limited to documents containing fenced code blocks with heading-like lines inside them — not every document is affected.

## 7. Not yet fixed / Pending

- The `extractSections()` fenced-code-block false-heading defect above (§5/§6) is not fixed. `MarkdownParserCore` is shared infrastructure (used by other document-parsing features, per its own Javadoc: "Generic Markdown parser core shared by document adapters"), so a fix here has blast radius beyond this ticket and should go through its own review rather than be patched incidentally inside a test-authoring pass.

## 8. Test cannot be executed and reason

| TC ID | reason | risk | alternative evidence |
|---|---|---|---|
| TC-PROMPT_TEMPLATE_REUSE_RATE-4 | Real-concurrency test for the `(project_id, repository_id, phase_id)` atomic upsert (review-checklist.md §2 race-condition item) requires two genuinely concurrent transactions against a real PostgreSQL instance; Testcontainers/Docker is unavailable in this environment (pre-existing `INTEGRATION_TEST_UNVERIFIED` gap, unchanged by this pass). | Low — the upsert relies on Postgres's own row-level locking for `ON CONFLICT ... DO UPDATE`, a standard, well-understood DB guarantee, not custom application logic. | `TemplateUsageStatJdbcAdapterTest.recordCheck_upsertsWithMatchIncrementOfOneWhenMatched` already asserts the literal SQL text uses `total_check_count = tbl_fact_template_usage_stat.total_check_count + 1` (additive, not `= 1`), which is the property that makes concurrent upserts safe. Full proof still requires a real-DB integration test tracked under the existing gap. |

## 9. Remaining risk

1. **Open defect** (§5/§6): `MarkdownParserCore.extractSections()` misparses heading-like lines inside fenced code blocks as real sections, causing false-negative template-usage comparisons for otherwise-compliant documents. **Needs a ticket-owner decision**: fix within this ticket's scope now, or track as a follow-up issue against the shared `MarkdownParserCore` component (recommended, given its "shared by document adapters" blast radius) — this ambiguity is being surfaced rather than decided unilaterally, per this pass's rules.
2. Concurrent-webhook-delivery real-DB behavior remains unverified beyond SQL-text inspection (§8, `INTEGRATION_TEST_UNVERIFIED`, pre-existing and unchanged by this pass).
3. `test-plan.md`'s prior residual-risk entry claiming "malformed Markdown content can cause an uncaught parser exception" was checked against `MarkdownParserCore.java`'s actual source this pass and found to be **inaccurate** — `parse()` is fully lenient (null-safe normalization + regex + the `commonmark` library, which never throws on malformed input) and represents anomalies as `errors()`/`warnings()` data that `headerStructureMatches` never reads, not as thrown exceptions. This claim is corrected in `test-plan.md` (see accompanying diff) and replaced with the real, verified risk: a blank/header-less document's `errors()`/`warnings()` signal is silently discarded, which is an observability/diagnosability gap, not a crash risk — and, separately, the fenced-code-block false-heading defect in item 1 above, which is the genuinely real risk in this area.
4. No idempotency/dedupe test exists for GitHub webhook redelivery (`OI-15`, already an accepted risk per `codex-review.md`/`open-issues.md` — unchanged by this pass).

## 10. Final Test Verdict

- **PARTIAL** — 2 new tests added and passing (close 2 real gaps); 1 new test intentionally failing as a documented reproduction of a real, unfixed defect discovered during this pass. No regressions introduced (`mvn -q -o test-compile` clean; all pre-existing template-usage tests in `GithubWebhookServiceTest` and `PmDashboardJdbcAdapterFindTemplateUsageTest` still pass).
