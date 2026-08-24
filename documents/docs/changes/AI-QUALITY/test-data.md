# Test Data

**Ticket ID**: AI-QUALITY
**Create date**: 2026-08-18 08:10:54
**Author**: BRYCENVN\nvt_dung
**Update date**: 2026-08-18 08:10:54

## Data Policy

- All IDs below are fixed, clearly-fake fixture values — no real/production-like project,
  repository, or ticket IDs are used, consistent with the fixture convention already established
  in this ticket's BE test suite (`test-plan.md` §7).
- Data is defined at the black-box/API-contract level (request payloads and expected responses),
  not at the DB-row level, since this artifact does not assume knowledge of implementation internals.
- Each data row traces back to a specific line in `spec-pack.md` §6.2/§6.4/§6.5 — no invented
  values beyond what the spec already enumerates.

## Master Data

| name | value | purpose |
|---|---|---|
| Project P-1 | `project_id = 20000000-0000-0000-0000-000000000001`, active | Parent project for normal-path fixtures |
| Project P-2 | `project_id = 20000000-0000-0000-0000-000000000002`, active | Second project, used for BR-3 mismatch cases |
| Repository R-1 | `repository_id = 30000000-0000-0000-0000-000000000001`, belongs to P-1 | Parent repository for normal-path fixtures |
| Repository R-2 | `repository_id = 30000000-0000-0000-0000-000000000002`, belongs to P-2 | Cross-project repository, used for BR-3 mismatch (BB-003) |
| Ticket T-1 | `ticket_id = 40000000-0000-0000-0000-000000000001`, belongs to R-1 | Primary ticket for create/update/delete lifecycle |
| Ticket T-9 | `ticket_id = 40000000-0000-0000-0000-000000000009`, belongs to a repository other than R-1 | Cross-repository ticket, used for BR-3 mismatch (BB-004) |
| Ticket with searchable key | `ticket_id = 40000000-0000-0000-0000-000000000002`, external key `PROJ-123` | Used for `search` filter case (BB-020) |

## User / Permission Data

| user | role | permission | purpose |
|---|---|---|---|
| admin-user | Global `ADMIN` | MUTATE on every project regardless of per-project role | AC-12 (BB-019) |
| pm-user | Per-project `PM` on P-1 | MUTATE on P-1 | AC-1, AC-6 normal-path create/update |
| qa-user | Per-project `QA` on P-1 | MUTATE on P-1 | AC-1 normal-path alternative mutate role |
| dev-user | Per-project `DEV` on P-1 | VIEW_ONLY on P-1 (403 on write, 200 on read) | AC-10 (BB-016, BB-017) |
| no-role-user | No role on P-1, not global ADMIN | NONE (403 on every endpoint incl. read) | AC-11 (BB-018) |

## Normal Data

| ID | data | purpose |
|---|---|---|
| ND-1 | `{project_id: P-1, repository_id: R-1, ticket_id: T-1, ai_quality_rate: 92.50}` | Create happy path (BB-001, §8.1) |
| ND-2 | `{ai_quality_rate: 75.25}` (update payload for existing row) | Update happy path (BB-011) |
| ND-3 | `{ai_quality_rate: 60.00}` on `ticket_id: T-1` after prior soft-delete | Re-create after soft-delete (BB-013) |
| ND-4 | List query `?projectId=P-1&repositoryId=R-1&ticketId=T-1` | Filter AND-combination (BB-014) |
| ND-5 | List query `?projectId=P-1&search=PROJ-123` | Search filter AND-combined (BB-020) |

## Error Data

| ID | data | expected error |
|---|---|---|
| ED-1 | Create repeated with same still-active `ticket_id: T-1` | 409 Conflict (BR-2, BB-002) |
| ED-2 | Create with `{project_id: P-1, repository_id: R-2 (belongs to P-2), ticket_id: T-1}` | 400 Bad Request, validation error (BR-3, BB-003) |
| ED-3 | Create with `{project_id: P-1, repository_id: R-1, ticket_id: T-9 (belongs to different repo)}` | 400 Bad Request, validation error (BR-3, BB-004) |
| ED-4 | Create with `project_id = "not-a-uuid"` | 400 Bad Request, malformed input (§6.5, BB-021) |
| ED-5 | Create with a well-formed but non-existent `project_id` | 400 Bad Request, validation error, not 404/500 (§6.5, BB-021) |
| ED-6 | Create with `repository_id = ""` (empty string) | 400 Bad Request (§6.5, BB-021) |
| ED-7 | Get/Update/Delete on an id that never existed | 404 Not Found (§6.4, BB-022) |
| ED-8 | Get/Update/Delete on an id whose row was already soft-deleted | 404 Not Found (BR-4, BB-022) |
| ED-9 | Create/update/delete as `dev-user` (VIEW_ONLY) | 403 Forbidden (BR-5, BB-016) |
| ED-10 | Any endpoint as `no-role-user` (NONE), including get/list | 403 Forbidden (BR-5, BB-018) |

## Boundary Data

| ID | item | value | expected |
|---|---|---|---|
| BD-1 | ai_quality_rate | `100.00` | Accepted (upper inclusive bound, BB-005) |
| BD-2 | ai_quality_rate | `0.00` | Accepted (lower inclusive bound, BB-006) |
| BD-3 | ai_quality_rate | `100.01` | Rejected, 400 (BB-007) |
| BD-4 | ai_quality_rate | `-0.01` | Rejected, 400 (BB-008) |
| BD-5 | ai_quality_rate | `null` | Rejected, 400 (BB-009) |
| BD-6 | ai_quality_rate | `50.555` (scale 3) | Rejected, 400 (BB-010) |

## Existing Data Compatibility

`tbl_dim_ai_quality` is a wholly new table with no prior rows and no migration from a legacy
schema (spec-pack §2.2, impact-analysis §14) — there is no existing-data compatibility concern to
test for this ticket. Fixture `Project`/`Repository`/`Ticket` rows referenced above are read-only
FK targets in already-existing tables (`tbl_dim_project`/`tbl_dim_repository`/`tbl_dim_ticket`);
this ticket does not alter their schema or data.

## Data Setup Procedure

1. Ensure fixture Project P-1/P-2, Repository R-1/R-2, and Ticket T-1/T-9/searchable-ticket rows
   exist in the target test environment (pre-seeded or created via existing Project/Repository/
   Ticket APIs — out of scope for this ticket to create).
2. Assign per-project roles to `pm-user`/`qa-user`/`dev-user` on P-1 via the existing project-role
   assignment mechanism; leave `no-role-user` unassigned on P-1.
3. For each test case requiring a pre-existing active row (BB-002, BB-011, BB-012, BB-017, BB-021
   get/update/delete variants), create that row first via `POST /api/v1/ai-qualities` as a
   MUTATE-access user, then proceed with the case's own steps.
4. No live DB harness is used to seed data directly — all setup goes through the API surface
   itself, consistent with `test-plan.md` §7 ("No live database is used by any BE test").

## Data Cleanup Procedure

1. Soft-delete any rows created during test execution via `PUT /api/v1/ai-qualities/{id}/delete`
   so they do not leak into other test runs' list/filter results.
2. Do not attempt a physical delete — soft-deleted rows remaining in the table is expected and
   consistent with BR-4; no additional archival/cleanup process is required (spec-pack §14).

## Sensitive Data Handling

- Do not use original production data.
- Do not save PII/secrets to artifacts.
