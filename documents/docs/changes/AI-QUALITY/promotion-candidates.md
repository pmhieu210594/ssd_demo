# Promotion Candidates

**Ticket ID**: AI-QUALITY
**Create date**: 2026-08-18 09:00:00
**Author**: BRYCENVN\nvt_dung
**Update date**: 2026-08-18 09:00:00

## Candidates for Living Docs

| ID | candidate | target doc | reason | priority |
|---|---|---|---|---|
| PC-AI-QUALITY-1 | ~~Document the `AuthUserContext` + per-project role `Access{MUTATE,VIEW_ONLY,NONE}` / `resolveAccess()` pattern as a fully worked example (not just a footnote)~~ **RESOLVED 2026-08-18 (Phase 9)**: `docs/standards/backend.md` already had a fully-worked "Per-Project Role-Tier Access Check (Confirmed)" section (not just a footnote); only a one-sentence addition naming `AiQualityService` as a second adopter was needed | `docs/standards/backend.md` (§"Per-Project Role-Tier Access Check (Confirmed)", ~line 211) | Was based on a stale assumption about the target doc's current state; cross-checked and corrected during Phase 9 | Done |
| PC-AI-QUALITY-2 | Document an `ensureXBelongsToY`-style inline FK-parentage validation pattern (with its 400-not-404 status convention) for dim-hierarchy features (Project→Repository→Ticket) | `docs/standards/backend.md` | BR-3 (repository↔project, ticket↔repository/project membership) had no existing precedent when this ticket needed it and had to be designed fresh; likely to recur for any future feature built on the same hierarchy | Low — defer until a second real consumer appears (see Not Promoted) |

## Candidates for Rules

| ID | rule candidate | target | reason | risk of rule bloat |
|---|---|---|---|---|

None proposed this ticket — no new durable constraint emerged that isn't already covered by
existing `.claude/rules/*.md` files.

## Candidates for Standards

| ID | standard candidate | target | reason |
|---|---|---|---|

None proposed this ticket beyond the Living Docs candidates above.

## Candidates for Architecture Docs

| ID | update candidate | target | reason |
|---|---|---|---|

None proposed — `20-architecture.md`'s existing per-project role-tier footnote already covers
the architectural rule; PC-AI-QUALITY-1 above is a documentation depth upgrade, not a new
architectural decision.

## Candidates for Failure Mode Index

| ID | failure mode | trigger | prevention | detection |
|---|---|---|---|---|
| PC-AI-QUALITY-3 | Diverging from a mirrored reference implementation's exact edge-case/fallback branch to apply a "stricter" reading of the spec, without first identifying what real caller depends on the reference's existing behavior | Implementing a "mirror pattern X" ticket and spotting what looks like an inconsistency or under-implementation in the reference (e.g. a javadoc claiming a check the code doesn't perform) | Before diverging from a mirrored reference's control flow, trace why the branch exists — check for a caller (e.g. a route guard, a first-render effect) that depends on the exact fallback behavior — rather than assuming it was an oversight | Regression surfaces as a permission/auth failure (403 / force-logout) for a caller state the new stricter code no longer tolerates, typically via a navigation path that doesn't carry a parameter the new code newly requires |

## Not Promoted

| item | reason |
|---|---|
| `RoleTabs.tsx` type-widening edit (adding an `"ai-quality"` entry to the `DashboardRoleView` union) | Too narrow/one-off to generalize into a rule or standard — a small, user-requested UX-parity follow-up specific to this pair of screens, not a recurring pattern |
| BR-3 shared validator (Option D: `HierarchyValidationHelper`) | Explicitly deferred, not rejected — tracked in PC-AI-QUALITY-2 above as "revisit once a second consumer appears," not promoted now since AiQualityService remains the single consumer |
| The `/access` regression itself (as a standalone doc item) | Captured instead as PC-AI-QUALITY-3 in the Failure Mode Index, which is the more appropriate home for a specific incident-derived lesson than a Living Docs entry |

## Human Approval Required

- PC-AI-QUALITY-1 and PC-AI-QUALITY-2 (Living Docs additions to `docs/standards/backend.md`)
  and PC-AI-QUALITY-3 (Failure Mode Index entry) should be reviewed and approved by a human
  maintainer before being merged into their target docs — this ticket only proposes them.
