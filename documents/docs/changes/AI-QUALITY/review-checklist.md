# Review Checklist

**Ticket ID**: AI-QUALITY
**Create date**: 2026-08-17 08:06:13
**Author**: Claude
**Update date**: 2026-08-17 08:06:13

## 1. Specification/AC Matching

| AC ID | Review Point | Severity | Result |
|---|---|---|---|
| AC-AI-QUALITY-1 | Create with valid project/repository/ticket IDs and rate in [0,100] succeeds, returns created row with `ticket_ai_quality_id`, `status=ACTIVE`, audit fields (BR-1, BR-6) | Blocker | Pending |
| AC-AI-QUALITY-2 | Create with `ticket_id` already having an active row returns 409 Conflict, no duplicate row created (BR-2) | Blocker | Pending |
| AC-AI-QUALITY-3 | Create where `repository_id` does not belong to `project_id` returns 400 validation error (BR-3) | Blocker | Pending |
| AC-AI-QUALITY-4 | Create where `ticket_id` does not belong to `repository_id` returns 400 validation error (BR-3) | Blocker | Pending |
| AC-AI-QUALITY-5 | `ai_quality_rate = 100.00` succeeds; `100.01`/`-0.01` fail validation (BR-6, §6.5) | Blocker | Pending |
| AC-AI-QUALITY-6 | Update persists new `ai_quality_rate`, refreshes `updated_at`/`updated_by` (BR-1) | Blocker | Pending |
| AC-AI-QUALITY-7 | Soft delete removes row from get-by-id/list results without a physical DELETE (BR-4) | Blocker | Pending |
| AC-AI-QUALITY-8 | After soft delete, creating a new row for the same ticket succeeds (no false 409) (BR-4) | Blocker | Pending |
| AC-AI-QUALITY-9 | List filters correctly by project_id/repository_id/ticket_id (AND-combined), returns `PageResult`-shaped response (§6.2, §6.3) | Major | Pending |
| AC-AI-QUALITY-10 | `VIEW_ONLY` caller (DEV project role) gets 403 on create/update/delete, 200 on get/list (BR-5) | Blocker | Pending |
| AC-AI-QUALITY-11 | `NONE` caller (no project role, not global ADMIN) gets 403 on every endpoint incl. get/list (BR-5) | Blocker | Pending |
| AC-AI-QUALITY-12 | Global `ADMIN` caller gets `MUTATE` regardless of per-project role (BR-5) | Blocker | Pending |

> This table doubles as the AC↔review correspondence table required for this phase.
> Cross-reference: BR-1↔AC-1/AC-6, BR-2↔AC-2/AC-8, BR-3↔AC-3/AC-4, BR-4↔AC-7/AC-8, BR-5↔AC-10/AC-11/AC-12, BR-6↔AC-1/AC-5.

## 2. General System Review

### 2.1. Number/Input Check
- [ ] `ai_quality_rate` rejects `null` (BR-6, §6.5)
- [ ] `ai_quality_rate` rejects values outside `[0.00, 100.00]` (`-0.01`, `100.01`)
- [ ] `ai_quality_rate` rejects scale > 2 (e.g. `50.555`)
- [ ] `ai_quality_rate = 0.00` and `= 100.00` (inclusive bounds) are both accepted
- [ ] `page`/`size` query params follow the existing `PageResult<T>` default/limit convention (page=0 default)
- [ ] Malformed UUID for `project_id`/`repository_id`/`ticket_id` returns 400, not 500 (§6.5)

### 2.2. Character Type / Encoding / Locale
- [ ] No free-text field on `tbl_dim_ai_quality` beyond audit actor identifiers — confirm no unplanned text column was added
- [ ] `Pages.AiQuality.*` locale keys present and consistent across `en`/`ja`/`vi` `locale.json` (no missing-key fallback shown to user)
- [ ] No mojibake / encoding corruption introduced in new Java/TypeScript/JSON files (UTF-8 preserved)
- [ ] Validation-error codes are stable strings the FE maps via i18next — no raw backend message text shown directly to the user (§17 A-AI-QUALITY-8)

### 2.3. Literal / Magic Number
- [ ] Role string comparisons (`ADMIN`/`PM`/`QA`/`DEV`) are case-insensitive and consistent with `TicketBugMetricsService`'s convention, not ad-hoc per-file literals
- [ ] `status` column values use the `record_status` enum (`ACTIVE`/`DELETED` only for this feature) consistently, never raw strings
- [ ] No hard-coded UUIDs or environment-specific IDs in code or tests
- [ ] `MODULE`/`ENTITY_TYPE` audit constants are literally `"AI_QUALITY"` (matches the new `ck_access_log_module` allow-list entry) — no typo/case mismatch between the Java constant and the SQL CHECK value

### 2.4. Operation / Maintainability
- [ ] Sufficient logs for incident investigation (audit log calls on every create/update/delete + failure path)
- [ ] `traceId` present on every error response for correlation
- [ ] Duplicate/double-submit on create does not create two active rows (BR-2 conflict + partial unique index as DB-level backstop)
- [ ] Rollback path documented: new corrective migration only, never edit `V510` in place after merge
- [ ] No hard-coded config (route prefix, page size limits) that should be externalized

## 3. FE Review

- [ ] Uses `CServerTable` for the ticket list and `CDrawerForm` for create/edit, mirroring `TicketBugMetricsPage.tsx` — no new Ant Design `Table`/`Form` invented
- [ ] Create/edit drawer implements cascading Project→Repository→Ticket dropdowns; Ticket dropdown shows **all** tickets in the selected repository, unfiltered by existing AI Quality rows (Option A, §17 A-AI-QUALITY-11) — no pre-filtering, no new "available tickets" endpoint
- [ ] Submitting a duplicate/already-tracked `ticket_id` surfaces the BR-2 409 via the existing localized toast/global error notification (no silent failure)
- [ ] `RequireAiQualityAccess.tsx` mirrors `RequireTicketBugMetricsAccess.tsx` exactly: reads `projectId` from `useSearchParams`, calls `/api/v1/ai-qualities/access`, unauthenticated → redirect to login, 401/403 → force logout+redirect, other errors → retry UI
- [ ] `effectiveRole`/`canMutate` gating mirrors `TicketBugMetricsPage`: global `ADMIN` always mutates; otherwise derives from the caller's per-project role for the selected project — action-column buttons hidden/disabled accordingly, and disabled for rows with `status === "DELETED"`
- [ ] Empty state rendered when no matching active rows, not a broken/empty table shell
- [ ] All API calls go through `endpoints.aiQuality.*` in `lib/api.ts`; no direct `fetch` calls anywhere in the page/components (`.claude/rules/20-architecture.md`)
- [ ] Async server state goes through TanStack Query (`useQuery`/`useMutation`); mutations invalidate the list query key and update/remove the detail query key on success
- [ ] No new Zustand/Redux state introduced for server-synced data — only ephemeral UI state (if any) uses Zustand
- [ ] New reusable components (if any) use `React.forwardRef` + `displayName`, CVA for variants, `cn()` for class merging (`10-style.md`)
- [ ] No `any` types; no unchecked `as` cast without an inline `// reason:` comment
- [ ] Named exports only, no default exports
- [ ] Route registered under the existing `:lang`-prefixed router in `App.tsx`, as a sibling to `ticket-bug-metrics`, wrapped in `RequireAiQualityAccess`

## 4. BE/API Review

- [ ] Routes match the confirmed shape exactly: `GET /api/v1/ai-qualities`, `GET /{id}`, `POST /`, `PUT /{id}`, `PUT /{id}/delete`, `GET /access` — soft delete is `PUT .../delete`, never a hard `DELETE` (§11)
- [ ] No `ticket-options`/`options` endpoints added — the ticket dropdown reuses the existing unfiltered ticket-by-repository lookup (§11, §17 A-AI-QUALITY-11)
- [ ] Controller resolves the caller as `AuthUserContext`, never `AppUser` — `AppUser.Role` cannot express `QA`/`DEV` and must not be used for access decisions
- [ ] `AiQualityService` implements local `Access { MUTATE, VIEW_ONLY, NONE }` enum and `resolveAccess`/`requireViewAccess`/`requireMutateAccess`, mirroring `TicketBugMetricsService.resolveAccess()` exactly: global `ADMIN` → `MUTATE`; per-project `PM`/`QA` → `MUTATE`; per-project `DEV` → `VIEW_ONLY`; else → `NONE`, denied read **and** write (BR-5)
- [ ] Per-project role lookup reuses the same mechanism as `TicketBugMetricsService` (e.g. `qaDashboardRepository.findProjectRole`) — no forked/duplicated lookup query without a recorded Stop/Ask resolution (impl-plan §12, Option A)
- [ ] **New** BR-3 parentage validation exists and is exercised: `repository_id` must belong to `project_id`; `ticket_id` must belong to `repository_id` — mismatches return 400, never a silent auto-correction. This has no BUG-DASHBOARD precedent — verify it was actually implemented, not assumed inherited
- [ ] `existsActiveByTicketId`-equivalent check powers the BR-2 409 conflict on create
- [ ] `ai_quality_rate` validation (BR-6: reject null, outside [0,100], scale > 2) happens server-side, not only in FE
- [ ] Not-found conditions (row missing/already soft-deleted) return 404 via the existing `NotFoundException` hierarchy
- [ ] Role check failures return 403 via `ForbiddenException` (existing hierarchy) — applies to write endpoints for `VIEW_ONLY` and to every endpoint (incl. read) for `NONE`
- [ ] All error responses flow through `GlobalExceptionHandler` into the standard `ErrorResponse(timestamp, status, errorCode, message, traceId)` — no ad-hoc `ResponseEntity` status codes hand-built in the controller
- [ ] `AiQualityRepositoryPort.findPage`/`count` are extended with a `ticketId` filter parameter beyond the `TicketBugMetricsRepositoryPort` template (§6.2) — confirm this extension was actually made, not copied verbatim
- [ ] `CreateAiQualityRequest`/`UpdateAiQualityRequest` annotated `@NoXssFields`
- [ ] Response DTOs (`AiQualityDto`, `AiQualityPageDto`) mirror `TicketBugMetricsDtos` shape (`items/page/size/totalElements/totalPages` for the page DTO); no new `ApiResponse<T>` envelope introduced anywhere
- [ ] `AiQualityModel` uses Lombok `@Builder @Getter @Setter @NoArgsConstructor @AllArgsConstructor` — never `@Data` (`10-style.md`)
- [ ] Constructor injection only in new service/controller/adapter classes — no `@Autowired` field injection
- [ ] Domain→DTO mapping happens in the service/mapper layer, not in the controller
- [ ] Each service method wrapped in its own `@Transactional` (`readOnly=true` for reads); no shared cross-item transaction
- [ ] `AdminAuditLogService` wired on create/update/soft-delete: `snapshot()` before mutation on update, `logCreate`/`logUpdate`/`logDelete` on success, `logCrudFailure` in the catch-and-rethrow, `MODULE = "AI_QUALITY"`, `ENTITY_TYPE = "AI_QUALITY"` — required for this ticket (context.md, user-confirmed 2026-08-17), not optional

## 5. DB/Migration Review

- [ ] Migration filename/version re-checked as still free immediately before merge (impl-plan §12 Stop/Ask #3 — `V510` was free at Phase 3 time, but must be re-verified, not assumed)
- [ ] `tbl_dim_ai_quality` DDL matches spec-pack §12 exactly: PK `ticket_ai_quality_id UUID DEFAULT gen_random_uuid()`, 3 FKs (`project_id`, `repository_id`, `ticket_id`), `ai_quality_rate DECIMAL(5,2) NOT NULL DEFAULT 0` with `CHECK (ai_quality_rate >= 0 AND ai_quality_rate <= 100)`
- [ ] Soft-delete-from-creation columns present at table creation: `delete_flag BOOLEAN NOT NULL DEFAULT FALSE`, `deleted_at TIMESTAMPTZ` (nullable), `deleted_by VARCHAR(100)` (nullable), `status record_status NOT NULL DEFAULT 'ACTIVE'`
- [ ] Standard audit columns present (`created_at`, `created_by`, `updated_at`, `updated_by`)
- [ ] Partial unique index `uq_ai_quality_active_ticket` on `(ticket_id)` filtered `WHERE delete_flag = FALSE AND deleted_at IS NULL AND status = 'ACTIVE'` enforces BR-1/BR-2 — is a top-level `CREATE UNIQUE INDEX`, not nested inside `CREATE TABLE`
- [ ] Supporting partial indexes `idx_ai_quality_project_status` and `idx_ai_quality_repository` present, both scoped `WHERE delete_flag = FALSE AND deleted_at IS NULL`
- [ ] `ck_access_log_module` CHECK constraint on `tbl_fact_access_log` is dropped and recreated to include `'AI_QUALITY'` in the allow-list, mirroring `V509`'s handling of the same constraint — this is a **required** additive ALTER, not optional
- [ ] No changes to `tbl_dim_project`, `tbl_dim_repository`, or `tbl_dim_ticket` schemas (spec-pack §2.2)
- [ ] No existing migration (`V1`–`V509`) edited — the change is purely additive except the one flagged `ck_access_log_module` ALTER
- [ ] Migration applies cleanly against dev DB via Flyway with no manual intervention
- [ ] Mutation queries (`update`, `softDelete`) in the mapper XML scope `WHERE` defensively to `delete_flag = FALSE AND deleted_at IS NULL AND status = 'ACTIVE'`; a 0-row result maps to `NotFoundException` in the service

## 6. Security/Privacy Review

- [ ] All AI-QUALITY endpoints require an authenticated session; none are `permitAll` (this is not a webhook)
- [ ] Backend enforces RBAC on every action independent of the FE — FE hiding a button/route is never the sole enforcement mechanism (BR-5)
- [ ] Read endpoints (`GET /{id}`, `GET /`) are also gated, not just mutation endpoints — this is a common miss when copying an ADMIN-only precedent; explicitly verify `NONE` callers are blocked on list/detail too (AC-AI-QUALITY-11)
- [ ] Per-project role check reuses the existing lookup mechanism correctly; `ADMIN` global bypass present and takes precedence over any per-project role (AC-AI-QUALITY-12)
- [ ] No PII exposed beyond existing `created_by`/`updated_by`/`deleted_by` actor identifiers; these are populated from the authenticated caller only, never client-supplied
- [ ] Error responses never leak stack traces or internal exception messages — only `ErrorResponse` fields reach the client
- [ ] No new `ApiResponse<T>` envelope, Spring `MessageSource`/`.properties` i18n, or `@PreAuthorize` usage introduced anywhere (spec-pack §2.2, §17)
- [ ] CORS/session/cookie handling unchanged — feature reuses existing authenticated-session infrastructure
- [ ] No secrets, tokens, or credentials introduced in migration/config/test fixtures

## 7. Operation/Maintenance Review

- [ ] `AdminAuditLogService` calls are best-effort — a broken audit write logs a WARN and never rolls back or blocks the business transaction
- [ ] `snapshot()` captured before mutation on update (matches `TicketBugMetricsService` pattern); `logCreate`/`logUpdate`/`logDelete` called after successful mutation; `logCrudFailure` called in the catch-and-rethrow on failure
- [ ] Soft delete treated as logical retention — no physical delete path exists anywhere in this feature (BR-4)
- [ ] Rollback plan documented and matches impact-analysis §13: BE = revert controller/service/DTOs/ports/adapter/mapper as one set; FE = remove route/page/guard wiring and `endpoints.aiQuality` calls; DB = new corrective migration only, never edit committed `V510`
- [ ] No new monitoring/alerting required; `traceId` propagation confirmed sufficient for debugging failures without new tooling
- [ ] No batch/job/event processing introduced (spec-pack §2.2 — out of scope for this ticket)

## 8. Test Review

- [ ] BE unit tests cover `ai_quality_rate` boundary values: `0.00`, `100.00` (accepted), `-0.01`, `100.01`, `null`, scale-3 `50.555` (all rejected) — BR-6, §6.5
- [ ] BE unit tests cover BR-3 parentage mismatches: repository not belonging to project (400), ticket not belonging to repository (400) — no BUG-DASHBOARD precedent, verify these tests actually exist
- [ ] BE unit tests cover BR-2 duplicate-active-row conflict (409) and BR-4 soft-delete lifecycle (create → soft-delete → absent from get/list → re-create same `ticket_id` succeeds with no false 409)
- [ ] BE unit tests cover the full RBAC matrix: `ADMIN` (global bypass), `PM`/`QA` (per-project mutate), `DEV` (per-project view-only, 403 on write), `NONE`/no-role (403 on every endpoint incl. read)
- [ ] BE controller tests (`@WebMvcTest` slice, not full `@SpringBootTest`) cover route/status/error-envelope mapping for every endpoint
- [ ] Unit tests mock port interfaces only (Mockito), never domain entities/value types (`.claude/rules/40-testing.md`)
- [ ] Minimum one test class per new production class (controller/service/adapter/mapper)
- [ ] FE Vitest+Testing Library tests cover: empty/loading/error states, cascading dropdown behavior, create/edit/delete flows, role-based UI hiding (DEV vs PM/QA/ADMIN), API helper query-string construction and error handling
- [ ] No Playwright E2E test added for this ticket — confirmed as an accepted scope decision (spec-pack §17 A-AI-QUALITY-12), not an oversight
- [ ] Every AC (AC-AI-QUALITY-1 through -12) maps to at least one passing test per impl-plan §11 — confirm no AC is untested

## 9. Documentation/Traceability Review

- [ ] `spec-pack.md`/`context.md`/`impact-analysis.md`/`impl-plan.md` remain internally consistent with the final implementation — no drift introduced during coding without updating these docs
- [ ] All three Stop/Ask conditions from impl-plan §12 (per-project role lookup reusability, BR-3 validator placement, migration version freshness) are resolved and their resolutions recorded in `self-review.md`
- [ ] `Pages.AiQuality.*` i18n key locations confirmed against actual `locale.json` files in `en`/`ja`/`vi` — no missing or orphaned keys
- [ ] FE endpoint group naming/shape in `lib/api.ts` matches what impact-analysis §5/context.md describe (`list/get/create/update/softDelete/access` only, no extra endpoints)
- [ ] No accidental modification of `TicketBugMetricsController`/`Service`/`Dtos`/`Model`/`Mapper` (read-only pattern references, not touched)

## 10. Release/Rollback Review

- [ ] Rollout is purely additive — confirm no existing BUG-DASHBOARD, Project, Organization, Customer, Team, or other governance module files were modified beyond the flagged touch points (`lib/api.ts`, `App.tsx`, locale JSON, `ck_access_log_module`)
- [ ] `AppUser`/`AppUser.Role` enum untouched; `TicketBugMetricsService` and migrations `V1`–`V509` remain untouched (impact-analysis §14 non-impact zones)
- [ ] FE rollback path verified: removing the new route/page/guard/`endpoints.aiQuality` wiring does not break any other page
- [ ] BE rollback path verified: removing the new module's files does not break any other controller/service via a shared dependency
- [ ] DB rollback path is a new corrective migration, never an edit to `V510` post-merge, per `.claude/rules/00-safety.md`; reverting the `ck_access_log_module` widening (if ever needed) requires confirming no `'AI_QUALITY'`-tagged audit rows exist first
- [ ] No production data migration/backfill required (brand-new table, no prior data)

## Severity Definition
| severity | meaning | required action |
|---|---|---|
| Blocker | Cannot be released | Must fix |
| Major | High probability of becoming a bug | Fix or accepted risk |
| Minor | Minor improvement | Optional |
| Question | Spec confirmation required | Open Issue |
| False Positive | Incorrect Report | Record Reason for Rejection |
| Accepted Risk | Accepted Risk | Record Impact/Owner/Deadline |
