# Promotion Candidates

**Ticket ID**: BUG-DASHBOARD
**Create date**: 2026-08-07 10:00:00
**Author**: Claude
**Update date**: 2026-08-07 10:00:00

## Purpose

Candidates surfaced during this ticket that are worth generalizing into shared, cross-ticket artifacts
(Failure Mode Index, Living Docs) rather than staying buried in this ticket's own `report.md`. Each item
below is a proposal for a human/maintainer to review and promote — nothing in this file has been applied
to the target document yet.

## Failure Mode Index candidates

| ID | candidate rule | target index | rationale | status |
|---|---|---|---|---|
| FMI-BUG-DASHBOARD-1 | A test file that mocks an entire third-party UI library module (e.g. `vi.mock('antd', ...)`) can silently crash or no-op unrelated components that import a different export from that same module. Stub only the specific exports actually used; never replace the whole module blindly. | Failure Mode Index (FE testing) | Discovered when `ticket-bug-metrics.test.tsx`'s `antd` mock omitted `Input`, crashing at import time via `field-info.tsx`'s `Input.OTP` reference — silently, until this test phase | Merged into `FMI-TC-002` (same root-cause pattern: hand-rolled mock desyncs from real import surface; this is a second occurrence, not a new failure mode) |
| FMI-BUG-DASHBOARD-2 | A hoisted mock object for a multi-endpoint API client that is missing one endpoint key resolves to `undefined` at call time and fails silently (e.g. a gated dependent query just never fires) rather than throwing. When a dependent-query chain "passes" with zero assertions on the gating data, verify the upstream mock actually returns the shape the component expects before trusting the green result. | Failure Mode Index (FE testing) | The hoisted `ticketBugMetricsApiMocks` object had no `options` mock, so `optionsQuery` silently never resolved, preventing the filter auto-select effect (and everything gated behind it) from ever firing — tests still reported green | Merged into `FMI-TC-002` (same underlying pattern as FMI-BUG-DASHBOARD-1, just a different mock shape) |
| FMI-BUG-DASHBOARD-3 | A production TypeScript build (`npm run build`) passing in one implementation session and failing with unused-symbol errors (`TS6133`) in a later session, on the same committed files, means build-step state should not be assumed stable across sessions. Re-run `tsc --noEmit` at the start of any later phase (review/test/report) that depends on "the build passes." | Failure Mode Index (build/CI hygiene) | `self-review.md §4` recorded `npm run build` as SUCCESS; this test phase's `test-results.md §2` recorded the same command as FAIL on the same files, with no intervening production edit recorded | Rejected — one-off session/cache-state observation, not a recurring code pattern; no durable prevention rule beyond re-running `tsc`, which the existing testing process already implies |

## Living Docs candidates

| ID | candidate update | target doc | rationale | status |
|---|---|---|---|---|
| LD-BUG-DASHBOARD-1 | Document the `QaDashboardService.requireQaAccess`-style role-check shape (ADMIN bypass + `findProjectRole(caller, projectId)` + role-string compare) as a named, reusable pattern, with the PM/QA-mutate + DEV-view + others-blocked variant this ticket introduces as a worked example. | `docs/standards/coding.md` | This is now the second implementation of a near-identical per-project role tier pattern; future tickets needing a mutate/view/blocked role matrix should reference a named pattern instead of re-deriving it from a prior ticket's `context.md` | Promoted — added as "Per-Project Role-Tier Access Check" in `docs/standards/backend.md` |
| LD-BUG-DASHBOARD-2 | Refresh or explicitly retire `docs/architecture/route-api-map.md`, `service-layer-map.md`, and `repository-db-map.md` — confirmed stale against the current governance-CRUD domain; they describe a legacy webhook/ingestion `repository` table, not the module they appear to document. | `docs/architecture/route-api-map.md`, `service-layer-map.md`, `repository-db-map.md` | Flagged as stale and excluded from evidence during this ticket's `impact-analysis.md §15`; leaving them in place risks a future ticket citing them without rediscovering the same caveat | Deferred — refreshing 3 architecture maps is a content-authoring task requiring a full re-survey, out of scope for a minimal Phase 9 pass; needs a dedicated follow-up ticket |
| LD-BUG-DASHBOARD-3 | Document the `AuthUserContext` (raw role string) vs. `AppUser` (role-collapsing enum, all non-ADMIN → EDITOR) distinction directly in a shared standards doc, with an explicit rule: use `AuthUserContext` for any PM/QA/DEV-level role decision. | `docs/standards/coding.md` or `docs/architecture/overview.md` | This ticket had to explicitly forbid `AppUser` for role checks (`context.md` "Forbidden methods"); the pitfall is currently only written down inside this one ticket's docs, not anywhere a future ticket would find it by default | Promoted — one-line rule added to `.claude/rules/20-architecture.md`, full rationale in `docs/standards/backend.md` |

## Next action

A maintainer should review each candidate above and either (a) merge it into the named target doc/index
verbatim or reworded, (b) reject it with a recorded reason, or (c) defer it to a follow-up ticket. No
target document has been edited by this ticket — this file is the handoff artifact only.
