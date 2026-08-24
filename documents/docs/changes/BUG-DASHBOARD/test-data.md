# Test Data

**Ticket ID**: BUG-DASHBOARD
**Create date**: 2026-08-06 07:45:13
**Author**: Claude
**Update date**: 2026-08-07 09:45:00

## Data Policy

All identifiers below are synthetic placeholders for black-box test execution only — no production
data, real customer names, or real credentials are used or should be substituted in. UUIDs shown as
`<PROJ-A>`-style tokens must be replaced with actual UUIDs generated at fixture-setup time. Per
`.claude/rules/00-safety.md`, no `.env`/secrets files are referenced or required to run these cases.

## Master Data

| name | value | purpose |
|---|---|---|
| Project `<PROJ-A>` | active Project, `project_id = <uuid>`, e.g. name "QA Sandbox Project A" | Primary project for normal-flow cases |
| Project `<PROJ-B>` | active Project, different `project_id` | Cross-project filter isolation (BB-002) |
| Repository `<REPO-A1>` | active Repository under `<PROJ-A>` | Primary repository for create/list |
| Repository `<REPO-A2>` | active Repository under `<PROJ-A>`, unrelated to any specific Ticket | Repository-filter case (BB-003) and BR-12 independence case (BB-004) |
| Repository `<REPO-A-INACTIVE>` | inactive/soft-deleted Repository under `<PROJ-A>` | Not-found/inactive negative case (BB-024) |
| Ticket `<TICKET-A1>` | active Ticket under `<PROJ-A>`, no existing active Ticket Bug Metrics row | Create happy-path target (BB-006) |
| Ticket `<TICKET-A2>` | active Ticket under `<PROJ-A>`, unrelated to `<REPO-A2>` | BR-12 independence case (BB-004) |
| Ticket `<TICKET-A-EXISTING>` | active Ticket under `<PROJ-A>` with one pre-existing active Ticket Bug Metrics row | Duplicate-conflict case (BB-007) |
| Ticket `<TICKET-A-INACTIVE>` | inactive Ticket under `<PROJ-A>` | Not-found/inactive negative case (BB-024) |
| Ticket Bug Metrics row `<TBM-ACTIVE-1>` | active row for `<TICKET-A-EXISTING>` | Update (BB-015), delete (BB-016), duplicate-conflict (BB-007) |
| Ticket Bug Metrics row `<TBM-DELETED-1>` | soft-deleted row (`status=DELETED`) under `<PROJ-A>` | Deleted-data detail/update/delete case (BB-018), list-exclusion case (BB-001) |

## User / Permission Data

| user | role | permission | purpose |
|---|---|---|---|
| `user.admin` | ADMIN (global) | Full access to all projects, bypasses per-project role check | Sanity/baseline caller for setup steps |
| `user.pm` | PM (project role on `<PROJ-A>`) | Create/update/delete + view on `<PROJ-A>` | Mutation-path normal/error cases (BB-004, 006, 007, 008-014, 015, 016, 023, 024, 025) |
| `user.qa` | QA (project role on `<PROJ-A>`) | Create/update/delete + view on `<PROJ-A>` | Interchangeable with `user.pm` for mutation cases per BR-7 |
| `user.dev` | DEV (project role on `<PROJ-A>`) | View-only on `<PROJ-A>`; blocked from create/update/delete | View-allowed case (BB-019), mutate-blocked case (BB-020) |
| `user.other` | Role outside {PM, QA, DEV, ADMIN} (e.g. `VIEWER`, or no project-role grant at all) on `<PROJ-A>` | No access to the screen at all | Blocked-role case (BB-021) |
| *(none)* | Unauthenticated / expired session | No access | Unauthenticated-blocked case (BB-022) |

## Normal Data

| ID | data | purpose |
|---|---|---|
| ND-1 | `{projectId: <PROJ-A>, repositoryId: <REPO-A1>, ticketId: <TICKET-A1>, internalBugCount: 12, customerBugCount: 2, note: "regression from sprint 4"}` | Create happy path (BB-006) |
| ND-2 | `{internalBugCount: 15, customerBugCount: 3, note: "updated after retest"}` | Update payload for `<TBM-ACTIVE-1>` (BB-015) |
| ND-3 | `{projectId: <PROJ-A>, repositoryId: <REPO-A2>, ticketId: <TICKET-A2>, internalBugCount: 3, customerBugCount: 1}` | BR-12 independence create (BB-004) |

## Error Data

| ID | data | expected error |
|---|---|---|
| ED-1 | `internalBugCount: -1, customerBugCount: 2` (create/update) | 400 — negative bug count rejected (BB-008) |
| ED-2 | `internalBugCount: 1.5` / `internalBugCount: "abc"` | 400 — non-integer bug count rejected (BB-009) |
| ED-3 | payload omitting `internalBugCount` / omitting `customerBugCount` | 400 — missing bug count rejected (BB-010) |
| ED-4 | `note` = string of 501 characters | 400 — note too long rejected (BB-013) |
| ED-5 | create payload targeting `<TICKET-A-EXISTING>` (already has `<TBM-ACTIVE-1>`) | 409 — duplicate active row conflict (BB-007) |
| ED-6 | detail/update/delete on a random UUID with no matching row | 404 — not found (BB-017) |
| ED-7 | detail/update/delete on `<TBM-DELETED-1>` | 404 — not found (already soft-deleted) (BB-018) |
| ED-8 | create with `projectId` = random nonexistent UUID | 404 — Project not found (BB-024) |
| ED-9 | create with `repositoryId = <REPO-A-INACTIVE>` | 404 — Repository not found/inactive (BB-024) |
| ED-10 | create with `ticketId = <TICKET-A-INACTIVE>` | 404 — Ticket not found/inactive (BB-024) |
| ED-11 | create payload omitting `projectId` / `repositoryId` / `ticketId` (each independently) | 400 — required field missing (BB-025) |
| ED-12 | any mutation attempted by `user.dev` | 403 — DEV blocked from mutation (BB-020) |
| ED-13 | any action attempted by `user.other` or unauthenticated caller | 403/401 — blocked from entire screen (BB-021, BB-022) |

## Boundary Data

| ID | item | value | expected |
|---|---|---|---|
| BD-1 | `internalBugCount` / `customerBugCount` | `0` | Accepted — zero is a valid, meaningful state (BB-011) |
| BD-2 | `note` | exactly 500 characters | Accepted, persisted in full (BB-012) |
| BD-3 | `note` | 501 characters | Rejected (BB-013) |
| BD-4 | `note` | omitted (not provided) | Accepted, stored as null/empty (optional field) |
| BD-5 | `note` | whitespace-only (`"   "`) | Accepted; trimmed to null per OI-BUG-DASHBOARD-5 resolution (BB-014) |
| BD-6 | `page` | omitted | Defaults to `0` |
| BD-7 | `size` | omitted / `<= 0` / `> 100` | Clamped to governance default/limit (see `test-plan.md` §5 `search_clampsPageSizeToBounds`) |

## Existing Data Compatibility

Not applicable. `tbl_ticket_bug_metrics` is a wholly new table introduced by `V509__ticket_bug_metrics.sql`
with no prior rows and no legacy contract to preserve (per `impact-analysis.md` §14). No pre-existing
Ticket Bug Metrics data exists to test compatibility against; Project/Repository/Ticket master data
(`tbl_dim_project`/`tbl_dim_repository`/`tbl_dim_ticket`) are read-only dependencies, not migrated.

## Data Setup Procedure

1. Ensure the target environment has run migration `V509__ticket_bug_metrics.sql` (schema present).
2. Seed or reuse existing active Project/Repository/Ticket rows matching the Master Data table above
   (via existing Project/Repository/Ticket management screens or direct API calls — no direct SQL
   insert needed for these dimension tables).
3. Seed the five project-scoped users (`user.pm`, `user.qa`, `user.dev`, `user.other`, plus reuse an
   existing `user.admin`) with the role grants described in User / Permission Data, scoped to
   `<PROJ-A>`, via the existing role-assignment mechanism (mirrors `QaDashboardService`'s
   `findProjectRole` lookup — no new permission model).
4. Create `<TBM-ACTIVE-1>` via a normal `POST` as `user.pm`/`user.qa` before running BB-007/BB-015/BB-016.
5. Soft-delete a second row to obtain `<TBM-DELETED-1>` via `PUT /{id}/delete` before running BB-001/BB-018.

## Data Cleanup Procedure

1. Soft-delete any Ticket Bug Metrics rows created during test execution that are still active
   (via `PUT /{id}/delete`) so re-runs start from a known state.
2. Do not hard-delete rows directly in the database — soft delete is the only sanctioned removal
   path (per BR-5); a full data reset for the feature is a DB-level truncate of
   `tbl_ticket_bug_metrics` only, done by an operator outside this test run, never via `rm -rf`/
   `DROP TABLE` executed by the test agent (per `.claude/rules/00-safety.md`).
3. Leave Project/Repository/Ticket master data untouched — they are shared fixtures, not owned by
   this feature's cleanup.

## Sensitive Data Handling

- Do not use original production data.
- Do not save PII/secrets to artifacts.
