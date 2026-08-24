# Prompt Template Reuse Rate — Reusable Pattern

**Source ticket:** PROMPT_TEMPLATE_REUSE_RATE (2026-08-10 → 2026-08-17)

## What this is

A per-phase template-compliance metric (`totalCheckCount`/`templateMatchCount` → `usageRate`) computed
by comparing a changed ticket document's Markdown header structure against its template on every
merged PR, recorded via a best-effort counter write and surfaced read-only on the PM dashboard. This
doc is the quick-reference entry point; see the linked living docs and `docs/changes/PROMPT_TEMPLATE_REUSE_RATE/`
artifacts for the full write-up.

## Key decisions and where they're documented

| Decision | Detail | Living doc |
|---|---|---|
| Recording trigger | Gate the counter write on `action == "closed" && merged == true` only — never on `opened`/`synchronize`/`reopened` — so a cumulative additive counter isn't inflated by every intermediate delivery of the same PR | `spec-pack.md` Business Rule #11; `docs/maintenance/failure-mode-index.md` `FMI-PTR-003` |
| Phase scope filter | Literal `WHERE ph.phase_code IN ('1'..'8')`, not an indirect `EXISTS`/`JOIN` against the shared `tbl_dim_artifact_type` proxy table (that table also carries unrelated-feature rows and leaks phase `0-A` through) | `spec-pack.md` Terminology/Business Rule #7; `docs/maintenance/failure-mode-index.md` `FMI-PTR-004` |
| Skippable vs. fatal fetch errors | 401/403/404 on any GitHub blob/tree/revision fetch (per-file **and** shared PR-level calls) → skip that validation pass, log warn, do not fail the webhook; any other status or non-HTTP error → propagate and fail the webhook | `spec-pack.md` §6.4 error table (Business Rule #10); `docs/maintenance/failure-mode-index.md` `FMI-PTR-002` |
| Best-effort counter write | `TemplateUsageStatWriter.recordIndependently(...)` uses `@Transactional(REQUIRES_NEW)`, **and** the call site in `GithubWebhookService`'s per-file loop wraps the call in an explicit `try/catch (DataAccessException)` — propagation alone does not swallow the exception | `docs/architecture/service-layer-map.md` §7 "Best-Effort Audit Write Pattern"; `docs/maintenance/failure-mode-index.md` `FMI-PTR-001` |
| Rounding | Round `usageRate` to exactly 1 decimal place server-side; `null` (not `0`) when `totalCheckCount == 0`; FE renders `"-"` for `null` and appends `%` without re-rounding | `spec-pack.md` Terminology, §6.3 Output, AC-8 |
| Access control | Endpoint restricted to PM role assigned to the project, plus ADMIN (any project) — not open to all authenticated dashboard users | `spec-pack.md` §9 Security/Privacy Impact (OI-9) |
| Redelivery dedup | Explicitly **not** implemented — GitHub webhook redelivery of the same `closed`+`merged` event can double-count. Accepted as a platform-level gap: no `webhook_event`/`webhook_delivery` table or persisted `deliveryId` exists anywhere in the codebase to dedupe against, and no other webhook consumer in the platform does this either | `open-issues.md` OI-15 (Accepted Risk); `docs/maintenance/failure-mode-index.md` `FMI-PTR-005` |

## Components (for future template-compliance or usage-metric tickets)

- `GithubWebhookService.validateTemplateUsage(...)` — per-file header-structure comparison against the
  template, gated on the merged-PR terminal condition, with status-aware skip/fail classification on
  every external fetch
- `MarkdownParserCore.extractSections()` — shared line-by-line heading scanner used for the comparison
  (see Known gaps below — it has a real defect, not specific to this ticket)
- `TemplateUsageStatWriter` — best-effort counter write (`REQUIRES_NEW` + call-site `try/catch`)
- `TemplateUsageStatPort` / `TemplateUsageStatJdbcAdapter` — dedicated write-side port/adapter, additive
  upsert on `(project_id, repository_id, phase_id)`
- `PmDashboardRepositoryPort.findTemplateUsage` / `PmDashboardJdbcAdapter` — read-side query, existing
  dashboard port extended with one new read method rather than a new controller/service class
- `TemplateUsageByPhase.tsx` (FE) — read-only table on `PMDashboardPage.tsx`, uses shared `CServerTable`

## Known gaps at time of writing (do not treat as resolved)

- `MarkdownParserCore.extractSections()` has a real, confirmed defect: it has no fenced-code-block
  (` ``` `) awareness, so a heading-like line shown as an example *inside* a code fence is counted as a
  real section. This causes a silent false-negative in `headerStructureMatches` for any otherwise
  template-compliant document that contains such an example (including several of this ticket's own
  template files). Left as an intentionally-failing reproduction test (`MarkdownParserCoreTest.sections_should_ignore_heading_like_lines_inside_fenced_code_blocks`),
  not fixed in this ticket — `MarkdownParserCore` is shared infrastructure used by other document-parsing
  features, so the fix needs its own review. See `docs/maintenance/failure-mode-index.md` `FMI-PARSER-009`.
- No redelivery-dedup mechanism exists (see Redelivery dedup row above) — accepted risk, not a bug.
- The `(project_id, repository_id, phase_id)` concurrent-upsert race has only been verified by SQL-text
  inspection (additive `+1`, not `=1`), not by a real concurrent-transaction test against Postgres — no
  Testcontainers/Docker available in this environment (`INTEGRATION_TEST_UNVERIFIED`, pre-existing gap
  shared with other tickets in this codebase).
- See `docs/changes/PROMPT_TEMPLATE_REUSE_RATE/report.md` and `test-results.md` for the authoritative,
  current status — this file will drift as time passes; treat it as the pattern reference, not the live
  status.
