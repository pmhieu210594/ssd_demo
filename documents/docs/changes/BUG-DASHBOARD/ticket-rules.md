# Ticket Rules:

**Ticket ID**: BUG-DASHBOARD
**Create date**: 2026-08-06 07:45:13
**Author**: Claude
**Update date**: 2026-08-06 07:45:13

## Must Follow

- Do not add specifications not included in the spec-pack.
- Ambiguous points must be returned as Open Issues.
- Before implementation, read the target source and existing tests.
- Follow existing patterns.
- Do not reuse obvious errors, vulnerabilities, or existing SonarLint violations.
- Check for full-width numbers, half-width numbers, empty strings, nulls, digits, and precision.
- Business code values ​​should be in enum/constant/master format instead of magic numbers.
- Do not export secrets/PII to logs
- Use `AuthUserContext caller` (raw role string) for every role check on this feature, never `AppUser caller` (`context.md` — `AppUser`'s role resolution cannot distinguish QA/DEV).
- Soft delete via `PUT {id}/delete`; never a hard `DELETE`.
- List response wrapper shape must be `items/page/size/totalElements/totalPages` (real `RepositoryPageDto` convention), not the raw input's illustrative `content/...` shape.
- Error responses must use the real `ErrorResponse(timestamp, status, error, message, traceId)` envelope via `GlobalExceptionHandler`, never an ad-hoc `ResponseEntity` status in a controller.
- New migration must follow the `V120`/`V130` soft-delete-from-creation column convention (`delete_flag`, `deleted_at`, `deleted_by`, `status`) plus a partial unique index on `ticket_id` (`WHERE delete_flag = FALSE AND deleted_at IS NULL AND status = 'ACTIVE'`) for BR-1/BR-6.
- Use `@Transactional` per service method (standard governance pattern), not `TransactionTemplate`-per-item (that rule is scoped to external ingestion only, per `.claude/rules/20-architecture.md` and `key-flows.md`).
- Constructor injection only; domain models use `@Builder @Getter @Setter @NoArgsConstructor @AllArgsConstructor`, never `@Data` (`.claude/rules/10-style.md`).
- All FE async server operations go through TanStack Query via `lib/api.ts`; never call `fetch` directly.

## Must Not Do

- Do not add an FK or any cross-validation between `repositoryId` and `ticketId` — `tbl_dim_ticket` has no `repository_id` column and this was explicitly resolved (H-BUG-DASHBOARD-1, BR-12): Repository is filter/informational only.
- Do not use the `AppUser.Role` enum (`ADMIN, EDITOR, VIEWER, PM`) to distinguish PM/QA/DEV — it cannot represent QA or DEV.
- Do not call `RepositoryService.requireAdmin`-style ADMIN-only gating for this feature's role model.
- Do not assume `com.sdd.platform.domain.exception.ForbiddenException` or `.OptimisticLockingException` exist — they live only under `com.sdd.platform.application.exception`.
- Do not silently extend `RequireDashboardAccess`'s `DashboardAccessKey` union without explicitly flagging it as a shared-file change in `impl-plan.md`.
- Do not invent a `CPopconfirm` component or use react-hook-form/zod — neither exists in this codebase.
- Do not let an audit-log write failure block or roll back a CRUD operation (audit logging is best-effort by existing convention).
- Do not place new FE entity DTOs under `src/interfaces/` — that directory is generic UI-plumbing types only.

## Stop / Ask Conditions

- Stop and ask if `V509` is no longer the next free migration version at implementation start (re-check the migration directory).
- Stop and ask if the seeded role code returned by `findProjectRole`/`tbl_dim_role` for "DEV" does not match the expected string (Open Issue OI-BUG-DASHBOARD-4) — do not guess and hard-code an unverified value.
- Stop and ask (or log as Open Issue) if whitespace-only `note` handling (trim/reject/accept) matters for a concrete test case — not specified by raw input (OI-BUG-DASHBOARD-5).
- Stop and ask before deciding whether to extend `RequireDashboardAccess`'s union or build a bespoke FE guard — this is an open implementation decision, not resolved in Phase 1/2.
- Stop and ask before choosing FE type placement (`lib/api.ts` inline vs. page-local `types.ts`) if the team has a strong existing convention preference beyond what's documented in `context.md`.

## Review Focus

- Role-gating enforced at the backend for every action (PM/QA mutate, DEV view-only, all other roles blocked from the entire screen including list/detail) — FE hiding alone is explicitly insufficient (BR-10).
- Duplicate-active-row conflict on create returns 409, not a silent overwrite or a 400 (BR-6, AC-BUG-DASHBOARD-4).
- Repository↔Ticket independence: create/update accepted even when `ticketId` and `repositoryId` are unrelated, as long as each independently exists and is active (BR-12, AC-BUG-DASHBOARD-13).
- Migration DDL executes cleanly and matches the soft-delete-from-creation + partial unique index convention exactly.
- No layer-boundary violations (web importing infrastructure, controllers doing DB-to-DTO mapping instead of the service/mapper layer).

## Test Focus

- `internalBugCount`/`customerBugCount`: negative, non-integer, omitted, exactly `0` (valid boundary).
- `note`: omitted (valid), exactly 500 chars (valid), 501 chars (invalid), whitespace-only (behavior TBD — log as risk if untested).
- Duplicate active row for the same `ticketId` on create → 409.
- Detail/update/delete against a nonexistent or already-soft-deleted row ID → 404.
- Role matrix: PM/QA mutate succeeds; DEV mutate → 403 but DEV list/detail succeeds; any other role → blocked from all actions including list/detail.
- Error envelope shape on every error case matches `ErrorResponse(timestamp, status, error, message, traceId)`.
