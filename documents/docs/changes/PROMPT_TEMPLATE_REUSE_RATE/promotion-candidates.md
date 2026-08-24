# Promotion Candidates

**Ticket ID**: PROMPT_TEMPLATE_REUSE_RATE
**Create date**: 2026-08-17
**Author**: Claude (Phase 9)
**Update date**: 2026-08-17

## Candidates for Living Docs

| ID | candidate | target doc | reason | priority |
|---|---|---|---|---|
| LD-PTR-001 | `TemplateUsageStatWriter` as second confirmed implementer of the Best-Effort Write Pattern (`REQUIRES_NEW` + mandatory call-site `try/catch`) | `docs/architecture/service-layer-map.md` §7 | Already applied in this pass; keeps the pattern's "propagation alone does not swallow the exception" pitfall visible for the next implementer, not just `AdminAuditLogWriter`'s original write-up | High |
| LD-PTR-002 | Ground scope filters in a literal, ticket-owned set (`WHERE phase_code IN (...)`) instead of an indirect `EXISTS`/`JOIN` against a shared dimension/proxy table when that table is also used by unrelated features | `docs/architecture/service-layer-map.md` or `docs/standards/database.md` | `tbl_dim_artifact_type` is shared with an unrelated AI-safety-scanning feature; the indirect filter (OI-11) leaked phase `0-A` through and needed a second fix (OI-13) to actually close | Medium |
| LD-PTR-003 | Gate additive/cumulative counter recording on the specific terminal lifecycle condition (`closed && merged`), not on "this webhook handler ran" | `docs/architecture/service-layer-map.md` (webhook handling section, if one exists) | Prevents the same double/inflated-counting mistake (OI-14) recurring in any future webhook-triggered additive metric | Medium |

## Candidates for Rules

| ID | rule candidate | target | reason | risk of rule bloat |
|---|---|---|---|---|
| RL-PTR-001 | Any new external-fetch call site added near an existing status-aware skip/fail classification (e.g. `isSkippableFetchError`) must reuse that classification, not a new blanket `catch (Exception)` | `docs/standards/error-handling.md` | Codex round 3 (Finding 2 / OI-16) found exactly this: `resolveRevision`/`listTree` used a blanket catch while the adjacent per-file blob fetch already had the right classification | Low |
| RL-PTR-002 | A best-effort side-effect writer's `REQUIRES_NEW`/`NESTED` propagation annotation must always be paired with an explicit `try/catch` at the *call site*, verified as part of implementation review, not assumed from the annotation alone | `docs/standards/review.md` (Phase 4/5 checklist) | Codex round 3 (Finding 3 / OI-17) found the writer had the correct propagation but the call site had no try/catch, so a `DataAccessException` still aborted the whole webhook | Low |

## Candidates for Standards

| ID | standard candidate | target | reason |
|---|---|---|---|
| ST-PTR-001 | When a shared/dormant dimension or proxy table is reused to scope a new feature's query, explicitly verify (and document) that no other feature's rows can pass the same filter — do not rely on the table's name alone implying exclusivity | `docs/standards/database.md` | This ticket's `EXISTS`/`JOIN` fix (OI-11) looked correct in isolation but missed that `tbl_dim_artifact_type` also serves an unrelated feature; only became visible at Phase 5 human review round 1 |
| ST-PTR-002 | Before treating a webhook redelivery/idempotency gap as this ticket's problem to fix, search the codebase for any existing delivery-id/idempotency table across all webhook consumers; if none exists, escalate as a platform-level decision rather than building a one-off dedup mechanism | `docs/standards/backend.md` or `docs/standards/error-handling.md` | This ticket's OI-15 was correctly scoped as an accepted risk only because the codebase-wide search confirmed there is no existing precedent anywhere — worth codifying so future tickets don't skip this check |

## Candidates for Architecture Docs

| ID | update candidate | target | reason |
|---|---|---|---|
| AD-PTR-001 | Document `TemplateUsageStatWriter` alongside `AdminAuditLogWriter` in the Best-Effort Write Pattern section, including the round-3 pitfall (propagation without call-site try/catch) | `docs/architecture/service-layer-map.md` §7 | Already applied in this pass (see report) — recorded here for traceability |
| AD-PTR-002 | Document that `tbl_dim_artifact_type` is a shared dimension table used by both this ticket's phase-mapping and an unrelated AI-safety file-scanning feature, and that scope filters against it must be literal, not indirect | `docs/architecture/repository-db-map.md` | Makes the shared-table risk discoverable without re-reading `spec-pack.md`'s OI-11/OI-13 history |

## Candidates for Failure Mode Index

| ID | failure mode | trigger | prevention | detection |
|---|---|---|---|---|
| FMI-PTR-001 | Best-effort side-effect write aborts the primary operation because the call site has no try/catch | Propagation annotation (`REQUIRES_NEW`/`NESTED`) used without a call-site `try/catch` | Always pair the propagation annotation with an explicit `try/catch` at the call site | Injected exception mid-loop aborts the whole operation instead of skipping one item |
| FMI-PTR-002 | Blanket `catch (Exception ex)` around a shared/PR-level external call masks non-skippable errors as a silent skip | New call site near existing status-aware skip logic reuses a broad catch instead | Reuse the existing skippable-error classification for any call touching the same external resource | Test with 401/403/404 (skip) vs 500 (should fail) on the new call site |
| FMI-PTR-003 | Cumulative counter recording runs on every lifecycle webhook delivery instead of once per logical event | Recording call not gated on the terminal condition defining "once" | Gate additive recording explicitly on the terminal condition, not "handler ran" | Multiple deliveries before the terminal event show the counter incremented more than once |
| FMI-PTR-004 | Query scoped through an indirect/shared proxy table leaks unrelated data | Filter derived indirectly against a table also used by another feature | Ground scope filters in a literal, ticket-owned definition | Response includes an out-of-scope value traced to unrelated-feature rows |
| FMI-PTR-005 | Additive webhook-triggered counter has no redelivery dedup, and the platform has no delivery-id table to build one against | New additive write triggered from a webhook handler on an at-least-once transport | Search for an existing delivery/idempotency table before assuming this is a per-ticket fix; escalate if none exists | Repo-wide search for `webhook_event`/`webhook_delivery`/persisted `deliveryId` returns nothing |

(All 5 rows above are already recorded in `docs/maintenance/failure-mode-index.md` under "PROMPT_TEMPLATE_REUSE_RATE-Derived Failure Modes" as part of this same Phase 9 pass — listed here per the standard promotion-candidates format for traceability, not as pending promotions.)

Also already recorded in the same pass: `FMI-PARSER-009` (fenced-code-block heading-like lines counted as real sections in `MarkdownParserCore.extractSections()`), under "Parser Failure Modes" — see Not Promoted below for why the underlying defect itself is not fixed.

## Not Promoted

| item | reason |
|---|---|
| A fix for `MarkdownParserCore.extractSections()`'s fenced-code-block false-heading defect | Real, confirmed defect (see `test-results.md` §5/§6), but `MarkdownParserCore` is shared infrastructure used by other document-parsing features outside this ticket's scope — fixing it here would carry blast radius this ticket didn't review. Only the failure mode (`FMI-PARSER-009`) and a reproduction test are promoted; the fix itself needs its own ticket/review |
| Redelivery-dedup mechanism (idempotency key / `webhook_delivery` table) | Explicitly declined by the ticket owner as out of scope — no existing precedent anywhere in the platform for any webhook consumer; treated as a platform-level Accepted Risk (OI-15), not something to solve unilaterally inside this ticket |
| A dedicated migration correcting `INTEGRATION_TEST_UNVERIFIED` (real concurrent-upsert test against Postgres) | Pre-existing, cross-ticket environment gap (no Docker/Testcontainers available in this session) — not specific to this ticket's implementation |

## Human Approval Required

- Whether to promote RL-PTR-001 (reuse existing skip/fail classification for new external-fetch call sites) and RL-PTR-002 (propagation-annotation + call-site-try/catch pairing) as binding rules enforced at review time, or keep them as informal guidance, is a Tech Lead decision — not yet approved.
- Whether `MarkdownParserCore.extractSections()`'s fenced-code-block defect should be fixed now as a fast-follow ticket or deferred — flagged in `report.md` §8/§9 as needing a ticket-owner decision, still open at Phase 9.
