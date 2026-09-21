# Sources

**Ticket ID**: REVIEW-FINDING-DENSITY
**Create date**: 2026-09-10
**Author**: nvt_dung
**Update date**: 2026-09-10

## Ticket / Issue

| source | link/path | status | note |
|---|---|---|---|
| Raw input | `docs/changes/REVIEW-FINDING-DENSITY/01_raw-input.md` | available | Primary requirement source; 6 numbered requirement sections + "current implementation status" section explicitly stating the flow is incomplete |

## Requirement / Design Documents

| source | path | status | trust level | note |
|---|---|---|---|---|
| Architecture overview | `docs/architecture/overview.md` | available | primary | Hexagonal layering, GitHub/Jira/CircleCI adapter placement |
| Coding standards | `docs/standards/coding.md` | available | primary | Package layout, no dead code / silent failure conventions |
| Testing standards | `docs/standards/testing.md` | available | primary | ArchUnit gate, mock-port-only rule, FE Vitest/Playwright conventions |
| Security standards | `docs/standards/security.md` | available | primary | Reviewer identity must join `tbl_dim_member_pseudonym`, never raw user id |
| EVIDENCE-QUALITY-SCORE spec-pack | `docs/changes/EVIDENCE-QUALITY-SCORE/spec-pack.md` | available | supporting | Closest structural precedent: BE-engine derived metric reusing existing `tbl_fact_*` tables, feeding a dashboard; used only as a content-quality reference, not as the section-structure template |
| Ticket template | `docs/standards/templates/_ticket-template/spec-pack.md` (+ `00_brainstorm.md`, `sources.md`) | available | primary | Authoritative section structure for this phase's artifacts |

## Existing Source Code

| area | path | status | purpose |
|---|---|---|---|
| DB schema | `EDCAP_BE/src/main/resources/db/migration/V4__init_shema_v2.sql:570-631` | available | `tbl_fact_review`, `tbl_fact_review_comment`, `tbl_fact_finding` definitions |
| DB schema | `EDCAP_BE/src/main/resources/db/migration/V181__git_pr_metadata_collector_schema.sql` | available | Adds `tbl_fact_pull_request_changed_file` (additions/deletions per file, no `head_sha` column); adds `external_pr_number/url`, `review_state` to `tbl_fact_pull_request` |
| DB schema | `V512__add_ai_finding_stat_tracking.sql` (`tbl_fact_ai_finding_stat`) | available | AI-only KPI snapshot table — explicitly excluded as a source per ticket Notes |
| Ingestion service | `GitPrMetadataCollectorService.java` (`persistPullRequest`, lines ~228-392) | available | Upserts PR/commits/changed files; deletes+re-inserts reviews & review comments per PR; normalizes review state to `APPROVED\|CHANGES_REQUESTED\|REVIEW_REQUIRED\|UNKNOWN`. No writer to `tbl_fact_finding` exists anywhere. |
| Ingestion adapter | `GitPrMetadataCollectorJdbcAdapter.java` | available | JDBC upsert/delete-insert implementation backing the above |
| GitHub adapter | `GithubPullRequestMetadataAdapter.java` + `GithubPullRequestMetadataPort` | available | REST-only: `/pulls/{number}`, `/commits`, `/files`, `/reviews`, `/comments`. No GraphQL client; no thread/`isResolved` fetch capability exists in this codebase. |
| AI finding pattern (reference only) | `AiFindingStatWriter.java`, `GithubWebhookService.java` (~line 580-632) | available | Structural pattern only (isolated writer bean + `@Transactional(REQUIRES_NEW)`); parses a markdown doc, not GitHub API — not reusable as classification logic per ticket Notes |
| Ratio-metric pattern (reference only) | `EvidenceQualityScoreService` (`scoreByRatio`) | available | Only existing ratio/BigDecimal-rounding style pattern in the codebase; no existing "density"/KLOC or "N/A on zero denominator" convention exists elsewhere |

## Existing Tests

| test type | path | status | note |
|---|---|---|---|
| Unit/integration tests for finding classification or density calc | — | not found | No dedicated test suite exists because no implementation exists yet |

## External / Office / PDF / Web References

| source | type | handling policy | note |
|---|---|---|---|
| GitHub REST API docs (reviews/comments) | web (already known from adapter code) | reference only, not fetched live in this phase | Confirms REST has no thread/resolved concept |
| GitHub GraphQL API (`reviewThreads.isResolved`) | web | not fetched; flagged as an Open Issue for feasibility decision | Required capability does not exist in this codebase today |

## Excluded Sources

| source/path | reason |
|---|---|
| `tbl_fact_ai_finding_stat` (V512) | Ticket explicitly states human Review Finding Density must not use this table; it is a separate AI-only KPI group |
| `docs/standards/templates/_ticket-template.zip` (raw zip) | Superseded by its already-extracted folder `_ticket-template/`; zip itself not read directly |

## Source Limitations

- No runtime data exists yet for `tbl_fact_finding` (zero writers in the codebase), so no real example rows could be inspected to validate the classification rule empirically.
- GitHub GraphQL thread-resolved capability is undocumented in this codebase; its feasibility (rate limits, permissions, schema shape) is not verified locally and needs a follow-up spike or human decision.
- No FE ticket-detail component reading similar ratio metrics was found to confirm exact display conventions (e.g., existing "N/A" badge pattern) — this needs confirmation against actual FE component library during Phase 2/3.

## Assumptions from Sources

- `tbl_fact_pull_request_changed_file` (upserted by `pr_id` + `file_path_hash`, no `head_sha`) is assumed to be the correct source for `additions + deletions`, since it's the only table matching the ticket's Raw Reference list for changed-lines data.
- The ticket's own "current implementation status" section is taken as accurate and independently confirmed by code search (no `tbl_fact_finding` writer, no GraphQL thread client).

## Human Confirmation Required

- ~~Whether GraphQL-based review-thread/resolved-status ingestion is in scope~~ — **Confirmed 2026-09-10**: use GitHub GraphQL (`reviewThreads`, `isResolved`).
- ~~Whether `tbl_fact_pull_request_changed_file` needs a `head_sha` column~~ — **Confirmed 2026-09-10**: add `head_sha`, key rows by `(pr_id, head_sha, file_path)`.
- ~~Finding-eligibility heuristic~~ — **Confirmed 2026-09-10**: metadata-only (review state + GraphQL thread existence); explicitly no NLP-based content interpretation.
- ~~Density rounding rule~~ — **Confirmed 2026-09-10**: 1 decimal place, `RoundingMode.HALF_UP`.

All four Open Issues are Closed; see `spec-pack.md` §8/§11/§12 for the final record.
