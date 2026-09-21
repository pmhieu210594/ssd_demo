# 00_brainstorm

**Ticket ID**: REVIEW-FINDING-DENSITY
**Create date**: 2026-09-10
**Author**: nvt_dung
**Update date**: 2026-09-10

## Purpose

Decide how to compute and surface `Review Finding Density = total review findings / total changed lines × 1,000` (`findings / KLOC`) on the PM Dashboard ticket-detail screen, using human GitHub PR review data only (never `tbl_fact_ai_finding_stat`), and identify what must be built vs. what is genuinely undecided before implementation can start.

## Known Information

- Formula and unit are fixed by the ticket: `findings / changed_lines × 1000`, displayed as `findings / KLOC`.
- `changed_lines = additions + deletions`, computed from the PR's current diff (`head_sha` or last commit), never accumulated across commits.
- Review threads belonging to a review with a known normalized state (`APPROVED`, `CHANGES_REQUESTED`, or `REVIEW_REQUIRED`) count as findings; `UNKNOWN`, blank, and unrecognized states do not count.
- Multiple threads under one review each count once; multiple comments in the same thread must be deduplicated to one finding by thread/finding identity.
- Finding status (`OPEN`/`RESOLVED`) does not affect the density total; these are the statuses produced by this feature and shown in its breakdown.
- A PR getting a new commit does not itself resolve findings — resolution requires the GitHub thread being resolved on GitHub.
- For a ticket with multiple linked PRs, density is computed as one ratio over the sum of findings and sum of changed lines across all PRs — never averaged per-PR.
- If total changed lines = 0, display `N/A`, not `0`.
- On a new commit, the current diff must be re-read and findings re-derived for the new version; data should be identified by `(pr_id, head_sha)` to avoid mixing versions; latest-only snapshot is acceptable unless history is required.
- Schema already has `tbl_fact_review`, `tbl_fact_review_comment`, `tbl_fact_finding`; `tbl_fact_pull_request_changed_file` (V181) already holds `additions`/`deletions` per file per PR but has no `head_sha` column.
- No code currently writes to `tbl_fact_finding`, and no code fetches GitHub's thread-resolved status (GitHub REST doesn't expose it; only GraphQL `reviewThreads.isResolved` does, which isn't implemented anywhere in this codebase).

## Undetermined Points

- ~~**Classification/resolved-status source**~~ — **Decided**: use GitHub GraphQL (`reviewThreads`, `isResolved`) as the source of true thread identity and resolved status, not a REST-only heuristic.
- ~~**Changed-lines versioning**~~ — **Decided**: add a `head_sha` column to `tbl_fact_pull_request_changed_file` and store rows keyed by `(pr_id, head_sha, file_path)`, giving per-version history by construction (old `head_sha` rows are not overwritten).
- ~~**Finding eligibility heuristic**~~ — **Decided**: eligibility is metadata-only (known normalized review state + thread exists via GraphQL `reviewThreads`); no NLP/text-content analysis to detect LGTM/ACK/questions. Accepted tradeoff: any thread under a known review state counts as a finding.
- ~~**Density rounding**~~ — **Decided**: round to 1 decimal place using `RoundingMode.HALF_UP`.

All four Open Issues (`OI-REVIEW-FINDING-DENSITY-001..004`) are now Closed. See `spec-pack.md` §11 for the final readiness verdict.

## Expected Risks

- GitHub GraphQL API has separate rate limits/quota from REST, which may affect PR sync frequency; needs evaluation in Phase 2.
- Adding `head_sha` to `tbl_fact_pull_request_changed_file` and changing its key to `(pr_id, head_sha, file_path)` requires a backfill/migration decision for existing rows that currently have no `head_sha` value.
- Even with true GraphQL thread identity, the remaining open finding-eligibility heuristic (`OI-003`) could still misclassify findings if not carefully defined before implementation.

## What AI Needs to Investigate

- Confirm current `GithubPullRequestMetadataAdapter` REST calls and their response shape for `/reviews` and `/comments` in enough detail to design a concrete finding-eligibility rule (done — see `sources.md`).
- Confirm exact `tbl_fact_pull_request_changed_file` upsert key and absence of `head_sha` (done — see `sources.md`).
- Confirm no existing "density"/ratio-with-N/A pattern exists elsewhere in FE or BE that should be reused (done — none found beyond `EvidenceQualityScoreService`'s general ratio style).

## What Humans Need to Ask

- ~~Is GitHub GraphQL-based thread/resolved-status ingestion in scope?~~ **Confirmed 2026-09-10**: yes, use GitHub GraphQL.
- ~~Should `tbl_fact_pull_request_changed_file` gain a `head_sha` column?~~ **Confirmed 2026-09-10**: yes, add `head_sha` and key by `(pr_id, head_sha, file_path)`.
- ~~What exact criteria determine a thread "expresses a concrete change request"?~~ **Confirmed 2026-09-10**: metadata-only (review state + thread existence); no NLP.
- ~~Density display rounding/precision rule?~~ **Confirmed 2026-09-10**: 1 decimal place, `RoundingMode.HALF_UP`.

All questions for this phase are answered; no further human confirmation is blocking Phase 3.

## Conditions Under Which Implementation Is Not Permitted

- Do not use `tbl_fact_ai_finding_stat` as a source under any circumstance, per the ticket's explicit instruction.
- GraphQL ingestion and the `head_sha` schema change (both approved) still need a Phase 2 impl-plan to work out concrete migration/backfill details — this spec pack defines the contract, not the implementation steps.
