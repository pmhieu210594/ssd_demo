# Review Checklist

**Ticket ID**: BUG-DASHBOARD
**Create date**: 2026-08-06 07:45:13
**Author**: Claude
**Update date**: 2026-08-06 09:15:00

## 1. Specification/AC Matching

| AC ID | Review Point | Severity | Result |
|---|---|---|---|
| AC-BUG-DASHBOARD-1 | List returns only non-deleted rows (`delete_flag=FALSE AND deleted_at IS NULL AND status='ACTIVE'`), filterable by `projectId`/`repositoryId`, includes ticket identifier, both bug counts, note, status | Blocker | Pending |
| AC-BUG-DASHBOARD-13 | Create/update accepted when `ticketId` belongs to a different Repository than submitted `repositoryId`, as long as each of `projectId`/`repositoryId`/`ticketId` independently exists and is active (no cross-validation, BR-12) | Blocker | Pending |
| AC-BUG-DASHBOARD-2 | FE shows defined empty state when no matching active rows, not a broken table | Major | Pending |
| AC-BUG-DASHBOARD-3 | Valid create for Ticket with no existing active row persists one active row, visible in subsequent list/detail reads | Blocker | Pending |
| AC-BUG-DASHBOARD-4 | Create for a Ticket with an existing active row is rejected 409, no duplicate row created (BR-6) | Blocker | Pending |
| AC-BUG-DASHBOARD-5 | Create/update with negative, non-integer, or missing `internalBugCount`/`customerBugCount` rejected, no data changed (BR-2) | Blocker | Pending |
| AC-BUG-DASHBOARD-6 | Create/update with `note` > 500 chars rejected, no data changed (BR-3) | Major | Pending |
| AC-BUG-DASHBOARD-7 | Update of bug counts/note returns latest data, reflected in subsequent reads, `updated_at`/`updated_by` refreshed (BR-11) | Blocker | Pending |
| AC-BUG-DASHBOARD-8 | Soft delete sets `delete_flag`/`deleted_at`/`deleted_by`/`status='DELETED'`, row no longer in default active list (BR-5) | Blocker | Pending |
| AC-BUG-DASHBOARD-9 | Detail/update/delete on nonexistent or already-deleted row ID returns 404, no mutation occurs | Blocker | Pending |
| AC-BUG-DASHBOARD-10 | DEV caller: create/update/delete rejected 403; list/detail succeed (BR-7/BR-8) | Blocker | Pending |
| AC-BUG-DASHBOARD-11 | Caller with role outside PM/QA/DEV/ADMIN rejected on every endpoint including list/detail (BR-9) | Blocker | Pending |
| AC-BUG-DASHBOARD-12 | Any backend error uses standard `ErrorResponse(timestamp, status, error, message, traceId)` envelope | Major | Pending |

> This table doubles as the AC↔review correspondence table required for this phase. Cross-reference: BR-1↔AC-3/AC-4/AC-8, BR-2↔AC-5, BR-3↔AC-6, BR-5↔AC-8, BR-6↔AC-4, BR-7/BR-8/BR-9↔AC-10/AC-11, BR-10↔§6, BR-11↔AC-7, BR-12↔AC-1/AC-13, BR-13↔§6.

## 2. General System Review

### 2.1. Number/Input Check
- [ ] `internalBugCount`/`customerBugCount` reject negative values (BR-2, AC-5)
- [ ] `internalBugCount`/`customerBugCount` reject non-integer input (e.g. `1.5`, `"abc"`)
- [ ] `internalBugCount`/`customerBugCount` reject missing/omitted value
- [ ] `0` is accepted as a valid value for both counts (boundary case, spec §8.3)
- [ ] No documented upper bound enforced beyond DB `INT` range — confirm no silent overflow/truncation
- [ ] `page`/`size` follow `RepositoryController` default/limit behavior (page=0 default)

### 2.2. Character Type / Encoding / Locale
- [ ] `note` accepts up to exactly 500 characters (boundary), rejects 501+ (BR-3, AC-6)
- [ ] Whitespace-only `note` handling is explicit and documented (OI-BUG-DASHBOARD-5 — currently unresolved; flag as Question if left unhandled)
- [ ] No mojibake in `note` free text across UTF-8 round-trip
- [ ] Locale message keys (`validation.bug_count.min`, `validation.note.max_length`) resolve correctly in en/ja/vi, not raw keys shown to user

### 2.3. Literal / Magic Number
- [ ] Role code comparisons (`PM`/`QA`/`DEV`/`ADMIN`) use consistent case-handling (`equalsIgnoreCase` per `QaDashboardService` precedent), not ad-hoc string literals scattered across files
- [ ] Seeded "DEV" role code verified against actual `tbl_dim_role` data before merge (OI-BUG-DASHBOARD-4)
- [ ] `status` column values use the `record_status` enum (`ACTIVE`/`DELETED`) consistently, not raw strings
- [ ] No hard-coded UUIDs or environment-specific IDs in code/tests

### 2.4. Operation / Maintainability
- [ ] Sufficient logs for incident investigation (audit log calls on every mutation + failure path)
- [ ] `traceId` present on every error response for correlation (AC-12)
- [ ] Retry/double-submit on create does not create duplicate active rows (relies on BR-6 conflict + partial unique index as backstop)
- [ ] Rollback path documented (new corrective migration only, never edit `V509` in place)
- [ ] No hard-coded config (route prefix, page size limits) that should be externalized

## 3. FE Review

- [ ] Uses `CDrawerForm` for create/edit, mirroring `RepositoryPage.tsx` pattern (no react-hook-form/zod)
- [ ] Delete confirmation uses antd `Popconfirm` imported directly from `antd` (no invented `CPopconfirm`)
- [ ] Project→Repository→Ticket cascading filter: Repository/Ticket queries `enabled`-gated on parent selection; self-healing `useEffect` resets invalid selection when option list changes (mirrors `DevFilterBar.tsx`)
- [ ] Repository filter narrows list by stored `repositoryId` only — FE does not attempt any client-side cross-validation against `ticketId` (BR-12/AC-13)
- [ ] Empty state rendered when no active rows match filter (AC-2), not a broken/empty table shell
- [ ] Role-based UI hiding: DEV sees list/detail but mutate actions (create/edit/delete buttons) hidden via inline `useAuth().user.role` check (AC-10); backend 403 is still the actual enforcement (BR-10)
- [ ] No-access roles cannot reach the screen at all — route guard decision (extend `RequireDashboardAccess` vs bespoke guard) implemented and enforced, not just visually hidden
- [ ] All API calls go through `lib/api.ts` (`endpoints.ticketBugMetrics.*`); no direct `fetch` calls elsewhere (`.claude/rules/20-architecture.md`)
- [ ] Async server state goes through TanStack Query (`useQuery`/`useMutation`); mutations invalidate list query key and update/remove detail query key on success
- [ ] New reusable components (if any) use `React.forwardRef` + `displayName`, CVA for variants, `cn()` for class merging (`.claude/rules/10-style.md`)
- [ ] No `any` types; no unchecked `as` casts without `// reason:` comment
- [ ] Named exports only, no default exports

## 4. BE/API Review

- [x] Routes match governance shape exactly: `GET /api/v1/ticket-bug-metrics`, `GET /{id}`, `POST`, `PUT /{id}`, `PUT /{id}/delete` (implemented resource segment is `ticket-bug-metrics`, not `ticket-bugs`; plus `ticket-options`/`options`/`access` — see spec-pack.md §20.1) — soft delete is `PUT .../delete`, never a hard `DELETE`
- [ ] Controller resolves caller via `@CurrentUser`/`CurrentAppUserResolver` returning `AuthUserContext`, never `AppUser` (AppUser collapses non-ADMIN roles to EDITOR and cannot distinguish PM/QA/DEV)
- [ ] New role-check method (e.g. `requireMutateAccess`/`requireViewAccess`) implements: ADMIN bypass, PM/QA → mutate+view, DEV → view only, all others → blocked entirely including list/detail (BR-7/8/9, AC-10/AC-11)
- [ ] Role-check method is new code on `TicketBugMetricsService` (or equivalent) — `QaDashboardService` itself is NOT modified (impl-plan Option B rejected)
- [ ] `projectId`/`repositoryId`/`ticketId` existence/active checks are independent queries against `tbl_dim_project`/`tbl_dim_repository`/`tbl_dim_ticket` — no join/cross-check between `repository_id` and `ticket_id` anywhere (BR-12/AC-13)
- [ ] "Active Ticket" check reads `tbl_dim_ticket.status` (a `ticket_status` value) — does NOT reference `delete_flag`/`deleted_at`/`deleted_by` on `tbl_dim_ticket` (those columns don't exist on that table)
- [ ] Duplicate active row for same `ticketId` on create rejected 409 via `ApplicationException`-style conflict (BR-6/AC-4)
- [ ] Bug-count and note validation rejects invalid input with HTTP 400 (BR-2/BR-3, AC-5/AC-6)
- [ ] Not-found conditions (project/repo/ticket missing/inactive, row missing/deleted) return 404 via `NotFoundException` (AC-9)
- [ ] Role check failures return 403 via `ForbiddenException` from `application.exception` package (not a `domain.exception` variant, which doesn't exist)
- [ ] All error responses flow through `GlobalExceptionHandler` into the real `ErrorResponse(timestamp, status, error, message, traceId)` — no ad-hoc `ResponseEntity` status codes hand-built in the controller (AC-12)
- [ ] Each service method wrapped in its own `@Transactional` (`readOnly=true` for reads); no `TransactionTemplate`-per-item pattern (that's scoped to external ingestion only)
- [ ] `Create.../Update...Request` DTOs annotated `@NoXssFields`
- [ ] Response DTOs follow `RepositoryDto`/`RepositoryPageDto` shape (`items/page/size/totalElements/totalPages`); no `version`/optimistic-locking field
- [ ] Domain model (`TicketBugMetricsModel`) mirrors `RepositoryModel` Lombok annotations (`@Builder @Getter @Setter @NoArgsConstructor @AllArgsConstructor`, never `@Data`) and includes an `isDeleted()` helper
- [ ] Constructor injection only in new service/controller classes; no `@Autowired` field injection
- [ ] DTO mapping (domain → DTO) happens in service/mapper layer, not in the controller

## 5. DB/Migration Review

- [ ] Migration file is `V509__ticket_bug_metrics.sql` (or next free version confirmed at implementation start) — purely additive `CREATE TABLE`, no edits to any existing migration (`V4` through `V508`)
- [ ] PK `ticket_bug_id UUID` with `DEFAULT gen_random_uuid()` — no missing `DEFAULT` (raw input's original DDL bug, per resolved H-3)
- [ ] `internal_bug_count INT NOT NULL DEFAULT 0`, `customer_bug_count INT NOT NULL DEFAULT 0`, `note VARCHAR(500)` (nullable)
- [ ] Soft-delete-from-creation columns present at table creation: `delete_flag BOOLEAN NOT NULL DEFAULT FALSE`, `deleted_at TIMESTAMPTZ`, `deleted_by VARCHAR(100)`, `status record_status NOT NULL DEFAULT 'ACTIVE'` — matches `V120`/`V130` convention, not a bare `delete_flag INT`
- [ ] FKs to `project_id`, `repository_id`, `ticket_id` each declared independently — no FK or check constraint attempting to cross-validate `repository_id` against `ticket_id` (BR-12, resolved H-1)
- [ ] Partial unique index on `ticket_id` filtered `WHERE delete_flag = FALSE AND deleted_at IS NULL AND status = 'ACTIVE'` enforces one-active-row-per-Ticket (BR-1/BR-6) — is a top-level `CREATE UNIQUE INDEX` statement, not illegally nested inside `CREATE TABLE` (raw input's original DDL bug, per resolved H-3)
- [ ] Standard audit columns (`created_at`, `created_by`, `updated_at`, `updated_by`) present
- [ ] `#{status}::record_status` explicit cast used in MyBatis XML, matching `RepositoryMapper.xml` pattern
- [ ] Migration applies cleanly against dev DB via Flyway with no manual intervention
- [ ] Mutation queries (`update`, `softDelete`) in mapper XML scope `WHERE` defensively to `delete_flag = FALSE AND deleted_at IS NULL AND status = 'ACTIVE'`; 0-row result maps to `NotFoundException` in service (race-condition safety)

## 6. Security/Privacy Review

- [ ] All endpoints require authenticated session; no `permitAll` (this is not a webhook)
- [ ] Backend enforces role-gating on every action independent of FE — BR-10 confirmed: FE hiding a button is never the only enforcement mechanism
- [ ] Per-project role check reuses `findProjectRole(caller, projectId)` shape correctly (BR-13); ADMIN bypass present
- [ ] List/detail (read-only) endpoints are also gated — not just mutation endpoints (AC-11; a common miss when copying Repository's ADMIN-only precedent)
- [ ] No PII exposed; audit fields (`createdBy`/`updatedBy`/`deletedBy`) populated from the authenticated caller only, never client-supplied
- [ ] Error responses never leak stack traces or internal exception messages to the client — only `ErrorResponse` fields
- [ ] CORS/session/cookie handling unchanged — feature reuses existing authenticated-session infrastructure, no new auth bootstrap code
- [ ] No secrets, tokens, or credentials introduced in migration/config/test fixtures

## 7. Operation/Maintenance Review

- [ ] `AdminAuditLogService` calls are best-effort — a broken audit write logs a WARN and never rolls back or blocks the business transaction
- [ ] `snapshot()` captured before mutation for update (matches `RepositoryService` pattern), `logCreate`/`logUpdate`/`logDelete` called after successful mutation, `logCrudFailure` called in catch-and-rethrow on failure
- [ ] Soft delete treated as logical retention in any operational runbook — no physical delete path exists anywhere in this feature
- [ ] Rollback plan for BE: revert controller/service/DTOs/ports/adapter/mapper as one set; for FE: remove route/page wiring and `endpoints.ticketBugMetrics` calls; for DB: new corrective migration only, never edit committed `V509`
- [ ] No new monitoring/alerting required, but `traceId` propagation confirmed sufficient for debugging failures without new tooling

## 8. Test Review

- [ ] BE unit tests cover: bug-count non-negative/integer validation, note length boundary (500/501 chars), duplicate-ticket 409 conflict, full role matrix (ADMIN/PM/QA/DEV/other) for every action including list/detail, Repository↔Ticket independence (AC-13)
- [ ] BE web/integration tests cover: all 5 routes, error envelope/status mapping for every error case in spec §6.4, migration/index behavior for soft delete and the new uniqueness constraint
- [ ] FE tests cover: empty/loading/error states (AC-2), cascading dropdown `enabled`-gating and self-healing reset behavior, create/edit/delete flows, role-based UI hiding (DEV vs PM/QA, AC-10), API helper query-string construction and error handling
- [ ] E2E test covers full CRUD journey plus deleted-row-is-inaccessible behavior, mirroring `repository.spec.ts`
- [ ] Test coverage exists despite the Repository module precedent having none — `.claude/rules/40-testing.md` "minimum one test class per production class" is honored for all new BE classes
- [ ] Unit tests mock port interfaces only (Mockito), never domain entities/value types (`.claude/rules/40-testing.md`)
- [ ] Web layer tests use `@WebMvcTest` slice, not full `@SpringBootTest`
- [ ] Every AC (1–13) maps to at least one test per impl-plan §11 — confirm no AC is untested

## 9. Documentation/Traceability Review

- [ ] `spec-pack.md`/`context.md`/`impact-analysis.md`/`impl-plan.md` remain internally consistent with final implementation (no drift introduced during coding without updating these docs)
- [ ] All Open Issues (OI-BUG-DASHBOARD-1..5) resolved or explicitly carried forward with updated status before release
- [ ] Resolved Human Decisions (H-BUG-DASHBOARD-1/2/3) correctly reflected in code (Repository informational-only, new role-check method not touching `QaDashboardService`, corrected DDL/migration version)
- [ ] FE type placement decision (inline in `lib/api.ts` vs page-local `types.ts`) documented in impl-plan/context, and implementation matches whichever was chosen
- [ ] FE guard decision (extend `RequireDashboardAccess` vs bespoke guard) documented and implementation matches whichever was chosen
- [ ] i18n key locations (`validation.bug_count.min`, `validation.note.max_length`, `Pages.TicketBugMetrics.*`) confirmed against actual `locale.json`/message properties files (OI-BUG-DASHBOARD-3)

## 10. Release/Rollback Review

- [ ] Rollout is purely additive — confirm no existing Repository/QaDashboard/Project/Organization/Customer/Team module files were modified beyond the three flagged touch points (`lib/api.ts`, `App.tsx`, optionally `RequireDashboardAccess.tsx`)
- [ ] `QaDashboardService`, `AppUser.Role` enum, and all migrations `V4`–`V508` remain untouched (explicit non-impact zones per impact-analysis §14)
- [ ] FE rollback path verified: removing route + endpoint calls does not break any other page
- [ ] BE rollback path verified: removing the new module's files does not break any other controller/service via shared dependency
- [ ] DB rollback path is a new corrective migration, never an edit to `V509` post-merge, per `.claude/rules/00-safety.md`
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
