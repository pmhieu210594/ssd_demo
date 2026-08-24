# 00_brainstorm

**Ticket ID**: AI-QUALITY
**Create date**: 2026-08-17 07:10:27
**Author**: BRYCENVN\nvt_dung
**Update date**: 2026-08-17 07:34:25

## Purpose

Turn `01_raw-input.md` (a Vietnamese technical design doc for an "AI Quality % per Ticket"
management screen) into a single, testable, unambiguous `spec-pack.md` that Phase 3 can implement
from directly, without re-reading or re-interpreting the original doc. The raw input explicitly
marks its API/DTO shapes as "reference only," so the spec must replace those with the repo's actual
conventions (verified against source, not assumed).

## Known Information

- One row in a new table represents one Ticket's AI Quality metric, scoped under a Repository
  which is scoped under a Project. CRUD + soft delete required; 1 ticket = 1 active row enforced by
  a partial unique index on `(repository_id, ticket_id) WHERE delete_flag = 0`.
- New table `tbl_dim_ai_quality`: PK `ticket_ai_quality_id UUID`, FKs to `tbl_dim_project`,
  `tbl_dim_repository`, `tbl_dim_ticket`; `ai_quality_rate DECIMAL(5,2) DEFAULT 0` (0–100 inclusive);
  `delete_flag`, `status record_status`, standard audit columns.
- UI: main screen has Project/Repository filter dropdowns + table (Action/Ticket/AI Quality).
  Create/Edit drawer: cascading Project → Repository → Ticket dropdowns + AI Quality % input.
- Business rule: POST with a `ticket_id` that already has an active row → `409 Conflict` (caller
  must use PUT instead).
- Validation: `project_id`/`repository_id`/`ticket_id` required + must exist + must belong to their
  parent; `ai_quality_rate` required, 0–100 inclusive.
- RBAC intent: ADMIN/QA/PM can create/edit/delete; DEV is read-only; other roles have no access to
  the screen. **Resolved** by adopting BUG-DASHBOARD's `TicketBugMetricsService.resolveAccess()`
  model verbatim: global `ADMIN` or per-project `PM`/`QA` → mutate; per-project `DEV` → view-only;
  anything else → no access at all (including read). No new global roles needed since this uses
  per-project role strings, not `AppUser.Role`.
- Verified against real source (see `sources.md`):
  - `tbl_dim_project`/`tbl_dim_repository` exist with UUID PK + standard audit columns +
    boolean `delete_flag`+`deleted_at`/`deleted_by` (added later via migration). `tbl_dim_ticket`
    exists but does **not** follow the standard audit/soft-delete convention (nullable audit cols,
    no delete_flag, own `ticket_status` enum) — it is FK target only, not a pattern source.
  - `record_status` Postgres enum exists: `ACTIVE, INACTIVE, ARCHIVED, DELETED`.
  - No `ApiResponse<T>` envelope exists; real convention is `ResponseEntity<T>` for single items and
    `PageResult<T>` / per-feature `*PageDto` (built via `from(PageResult<X>)`) for lists.
  - No Spring `MessageSource`/`LocaleResolver`/`.properties` i18n exists; i18n is FE-only via
    i18next JSON locale files (`EDCAP_FE/public/locales/{en,vi,ja}/locale.json`).
  - No `@PreAuthorize` usage anywhere in the backend; RBAC is manual role-equality checks in service
    code against `AppUser.Role { VIEWER, EDITOR, ADMIN, PM }` — no QA or DEV role exists today.
    A separate `AuthUserContext` model (raw string role + access scopes) exists per
    [[20-architecture.md]] for per-project role tiers but is not what current controllers use.
  - Two soft-delete conventions coexist: boolean `delete_flag`+`deleted_at`/`deleted_by`
    (project/repository) vs. char `delete_flag` only (`ScoreThresholdConfigRepositoryAdapter`).
    **Resolved**: BUG-DASHBOARD's `tbl_ticket_bug_metrics` (same FK triplet as AI-QUALITY) uses the
    boolean `delete_flag`+`deleted_at`/`deleted_by`+`status` convention with a partial unique
    active-row index — adopted verbatim for `tbl_dim_ai_quality`.
  - `UserAccountAdminController` → `UserAccountAdminService` → `UserAccountAdminRepositoryPort` →
    `UserAccountAdminRepositoryAdapter` → `UserAccountAdminMapper` is a confirmed, real hexagonal
    CRUD reference to mirror for layering; `TicketBugMetricsService`/`TicketBugMetricsController`/
    `V509__ticket_bug_metrics.sql` (BUG-DASHBOARD) is the confirmed reference for RBAC access
    resolution and soft-delete/DDL shape specifically, since it shares the same
    Project/Repository/Ticket structure and PM/QA/DEV access narrative as AI-QUALITY.

## Undetermined Points

- ~~RBAC contradiction (DEV read-only vs. "other roles no access")~~ — **Resolved**: DEV is
  view-only (per-project role), not "no access"; "other roles" means anyone without an ADMIN
  global role or a PM/QA/DEV per-project role.
- ~~RBAC roles named (QA, DEV) don't exist in `AppUser.Role`~~ — **Resolved**: no new global roles
  needed; QA/PM/DEV are resolved from the caller's per-project role string, mirroring BUG-DASHBOARD.
- ~~List/filter endpoint's exact pagination and filter-parameter shape~~ — **Resolved**: standard
  `page`/`size` (`PageResult<T>` convention) + optional `projectId`/`repositoryId`/`ticketId` filters.
- ~~Whether the Ticket dropdown should exclude tickets with an active row~~ — **Resolved by user**:
  Option A — show all tickets in the selected repository; duplicate selection surfaces the standard
  BR-2 409 on submit (not pre-filtered).
- ~~Whether validation-message localization should move to the FE-only i18next convention~~ —
  **Resolved**: yes, FE-only i18next is the only real mechanism in this codebase; BE returns stable
  error codes.
- ~~Which soft-delete column convention to apply to the new table~~ — **Resolved**: boolean
  `delete_flag`+`deleted_at`/`deleted_by`+`status`, matching BUG-DASHBOARD's `tbl_ticket_bug_metrics`.
- ~~Whether re-creating a row for a ticket whose previous row was soft-deleted should succeed~~ —
  **Resolved**: yes, confirmed by BUG-DASHBOARD's identical partial-unique-index pattern.
- ~~Final REST path prefix~~ — **Resolved**: `/api/v1/ai-qualities` (top-level, matching
  BUG-DASHBOARD's own top-level `/api/v1/ticket-bug-metrics`).
- ~~Whether E2E (Playwright) coverage is required~~ — **Resolved**: no, default to Vitest/component
  tests only for this ticket.

## Expected Risks

- Partial unique index + soft delete interaction: if soft-deleted rows aren't excluded correctly, a
  soft-deleted ticket may falsely block a new create (or incorrectly allow duplicates if the index
  is misdefined).
- RBAC role mismatch could block usable role-checks entirely if left unresolved — a placeholder that
  silently fails open/closed would be a security risk.
- DECIMAL(5,2) precision/boundary handling for `ai_quality_rate` (100.00 vs 100.01, negative values,
  scale beyond 2 decimals) needs explicit boundary-value coverage.
- Copying the raw input's API/i18n conventions verbatim (as it suggests) would introduce a class and
  a message pattern that don't exist in the codebase — a real implementation risk if not corrected
  in the spec.

## What AI Needs to Investigate

- (Done — see `sources.md`) Confirm dim tables, `record_status`, pagination wrapper, i18n mechanism,
  RBAC enforcement pattern, one reference CRUD feature, and soft-delete pattern all exist as claimed
  or identify the real equivalent.
- Confirm during Phase 3 kickoff (not blocking Phase 1) exact naming conventions for new
  `web/dto`, `application/usecase`, `infrastructure/persistence/adapter` classes for `AiQuality`.

## What Humans Need to Ask

None outstanding. All items are resolved — see Undetermined Points above.

## Conditions Under Which Implementation Is Not Permitted

- Do not implement Spring `MessageSource`-based i18n as literally described in the raw input; it
  does not match this codebase's architecture and would introduce an inconsistent, unused pattern.
