# Ticket Rules:

**Ticket ID**: AI-QUALITY
**Create date**: 2026-08-17 07:43:48
**Author**: BRYCENVN\nvt_dung
**Update date**: 2026-08-17 07:48:18

## Must Follow

- Use `AdminAuditLogService` for AI-QUALITY create/update/delete, mirroring
  `TicketBugMetricsService` (`MODULE = "AI_QUALITY"`, `ENTITY_TYPE = "AI_QUALITY"`), including
  `logCrudFailure` on the failure path — **user-confirmed 2026-08-17** (no longer an open question).
- The new AI-QUALITY Flyway migration must add `'AI_QUALITY'` to the
  `tbl_fact_access_log.ck_access_log_module` CHECK constraint, following the same pattern
  `V509__ticket_bug_metrics.sql` used to add `'TICKET_BUG_METRICS'`/`'SCORE_THRESHOLD'`.

- Do not add specifications not included in `spec-pack.md`.
- Ambiguous points must be returned as Open Issues.
- Before implementation, read the target source and existing tests — in particular
  `TicketBugMetricsService.java`, `V509__ticket_bug_metrics.sql`, and `UserAccountAdminController.java`
  as named in `context.md`.
- Follow existing patterns — reuse the BUG-DASHBOARD (`TicketBugMetricsService`) RBAC/soft-delete
  pattern and the `UserAccountAdminController` thin-controller/hexagonal-layering pattern, per
  `context.md`.
- Do not reuse obvious errors, vulnerabilities, or existing SonarLint violations.
- Check for full-width numbers, half-width numbers, empty strings, nulls, digits, and precision —
  in particular `ai_quality_rate` scale/range boundaries (BR-6, spec-pack §6.5).
- Business code values should be in enum/constant/master format instead of magic numbers — e.g.
  `record_status`, the `Access` tier enum.
- Do not export secrets/PII to logs.

## Must Not Do

- Do not introduce a new `ApiResponse<T>` envelope — none exists in this codebase (§17 A-AI-QUALITY-1).
- Do not introduce Spring `MessageSource` / `.properties`-based i18n — none exists (§17 A-AI-QUALITY-2).
- Do not use or add `@PreAuthorize` anywhere — zero usages exist in `EDCAP_BE` (§17 A-AI-QUALITY-3).
- Do not use `AppUser.Role`/`UserAccountAdminService.requireAdmin`'s single-global-role RBAC model
  for this ticket — it cannot express `QA`/`DEV` per-project roles required by BR-5.
- Do not add `QA`/`DEV` values to the `AppUser.Role` enum — per-project roles come from
  `AuthUserContext` + the existing per-project role lookup mechanism instead.
- Do not implement bulk import/export of AI Quality rates (§2.2).
- Do not implement automatic AI Quality computation from CI/AI-review tooling (§2.2) — manual CRUD
  only.
- Do not add a historical audit trail / version history of rate changes beyond standard
  `updated_at`/`updated_by` (§2.2).
- Do not modify `tbl_dim_project`, `tbl_dim_repository`, or `tbl_dim_ticket` schemas (§2.2, §12).
- Do not implement a hard/physical `DELETE` on `tbl_dim_ai_quality` — soft delete only (BR-4).
- Do not add a new "available tickets" filtered-lookup endpoint that excludes already-tracked
  tickets — Option A (unfiltered dropdown) was explicitly user-confirmed (§17 A-AI-QUALITY-11).
- Do not add Playwright E2E tests for this ticket by default (§17 A-AI-QUALITY-12) unless later
  requested.

## Stop / Ask Conditions

- Stop and ask if the per-project role lookup mechanism needed for `resolveAccess()`
  (`TicketBugMetricsService` uses `qaDashboardRepository.findProjectRole(caller, projectId)`) is
  not directly reusable as-is for AI-QUALITY once implementation begins — do not fork or
  reimplement a parallel lookup without confirming.
- Stop and ask if BR-3's FK-parentage validation (repository belongs to project; ticket belongs to
  repository) needs a new reusable validation helper versus inline checks — this logic does not
  exist in the BUG-DASHBOARD analog and its placement (service vs. shared validator) is an
  implementation-phase decision, not yet specified.
- Stop and ask if the exact Flyway migration version number conflicts with any concurrently
  in-progress migration (check `EDCAP_BE/src/main/resources/db/migration/` for the latest `V*`
  before assigning one in impl-plan).

## Review Focus

- BR-1/BR-2: partial unique index correctly scoped to `delete_flag = FALSE AND deleted_at IS NULL
  AND status = 'ACTIVE'`; create against an existing active ticket_id returns 409, not 500 or a
  silent overwrite.
- BR-3: repository↔project and ticket↔repository parentage validated before insert/update, with
  400 (not 500) on mismatch, and not silently auto-corrected.
- BR-4: soft delete never physically removes the row; soft-deleted rows excluded from get/list and
  from the uniqueness constraint (re-creation after soft delete must succeed, AC-AI-QUALITY-8).
- BR-5: `resolveAccess()` correctly distinguishes `NONE`/`VIEW_ONLY`/`MUTATE` per AC-AI-QUALITY-10,
  -11, -12; enforcement happens server-side in the service layer, not only in the FE.
- BR-6: `ai_quality_rate` boundary handling — 0.00 and 100.00 accepted; -0.01, 100.01, null, and
  scale-3 values (e.g. 50.555) rejected with 400, not 500.
- Error responses use the standard `ErrorResponse(timestamp, status, errorCode, message, traceId)`
  shape everywhere; no ad-hoc `ResponseEntity` status codes in the controller.
- Hexagonal layering respected (`ArchitectureTest` stays green); no new `allowedPackage` exceptions.

## Test Focus

- Every AC in spec-pack §7 (AC-AI-QUALITY-1 through -12) must map to at least one test case.
- Backend: unit tests mock the new `AiQualityRepositoryPort` (Mockito), not domain entities;
  `@WebMvcTest` slice for the controller; one test class per new production class.
- Boundary tests for `ai_quality_rate`: 0.00, 100.00, -0.01, 100.01, null, scale > 2 (50.555).
- RBAC test matrix: `ADMIN` (global) → MUTATE regardless of project role; `PM`/`QA` (per-project)
  → MUTATE; `DEV` (per-project) → VIEW_ONLY (403 on write, 200 on read); no role/`NONE` → 403 on
  every endpoint including read.
- Soft-delete lifecycle: create → soft-delete → verify absent from get/list → re-create same
  ticket_id succeeds (no false 409).
- Frontend: Vitest + Testing Library for the new page/hooks/components; no Playwright E2E required
  by default (§17 A-AI-QUALITY-12).
