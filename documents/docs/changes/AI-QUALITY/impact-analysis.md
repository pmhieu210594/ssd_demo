# Impact Analysis

**Ticket ID**: AI-QUALITY
**Create date**: 2026-08-17 07:59:00
**Author**: BRYCENVN\nvt_dung
**Update date**: 2026-08-17 07:59:00

## 1. Content Change Summary

- Add a new governance-style CRUD module, "AI Quality", allowing ADMIN (global) and PM/QA
  (per-project role) to record and manage an AI Quality % score per Ticket, DEV (per-project role)
  to view-only, and all other callers to be denied access to the screen and API entirely.
- Introduce a new DB table (`tbl_dim_ai_quality`) and a full BE/FE CRUD surface, following the
  `TicketBugMetricsService`/`TicketBugMetricsController` hexagonal CRUD pattern exactly for RBAC and
  soft-delete, with one new piece of logic (BR-3 FK-parentage validation) that has no precedent in
  that reference implementation.
- No existing production BE code is modified except one narrow, additive touch point: the
  `ck_access_log_module` CHECK constraint on `tbl_fact_access_log` (adding `'AI_QUALITY'` to the
  allow-list, required because `AdminAuditLogService` is used for create/update/delete).
- No existing production FE code is modified except two narrow, additive touch points: `lib/api.ts`
  (new `endpoints.aiQuality` group) and `App.tsx` (new `<Route path="ai-quality">`), plus locale
  JSON files gaining new keys (`Pages.AiQuality.*`) — no existing keys are changed.

## 2. Directly Affected Files

- `docs/changes/AI-QUALITY/source-availability.md` (new)
- `docs/changes/AI-QUALITY/source-inventory.md` (new)
- `docs/changes/AI-QUALITY/impact-analysis.md` (new)
- `docs/changes/AI-QUALITY/impl-plan.md` (filled in from existing empty skeleton)

## 3. Indirectly Affected Files

- `EDCAP_BE/src/main/resources/db/migration/V510__ai_quality.sql` (new)
- `EDCAP_BE/src/main/java/com/sdd/platform/domain/model/AiQualityModel.java` (new)
- `EDCAP_BE/src/main/java/com/sdd/platform/web/dto/AiQualityDtos.java` (new)
- `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/persistence/AiQualityRepositoryPort.java` (new)
- `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/AiQualityRepositoryAdapter.java` (new)
- `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/mapper/AiQualityMapper.java` + `.xml` (new)
- `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/aiquality/AiQualityService.java` (new)
- `EDCAP_BE/src/main/java/com/sdd/platform/web/rest/AiQualityController.java` (new)
- `EDCAP_BE/src/test/UnitTest/java/.../AiQualityServiceTest.java`, `AiQualityControllerTest.java` (new)
- `EDCAP_FE/src/pages/ai-quality/AiQualityPage.tsx`, `types.ts`, `components/AiQualityFilterBar.tsx` (new)
- `EDCAP_FE/src/components/auth/RequireAiQualityAccess.tsx` (new)
- `EDCAP_FE/src/lib/api.ts` (edit — add `endpoints.aiQuality` group + types)
- `EDCAP_FE/src/App.tsx` (edit — add new `<Route path="ai-quality">`)
- `EDCAP_FE/public/locales/en/locale.json`, `ja/locale.json`, `vi/locale.json` (edit — add
  `Pages.AiQuality.*` keys)
- `EDCAP_FE/src/__ tests __/ai-quality/*.test.ts(x)` (new)

## 4. Caller / Callee Map

- FE page (`AiQualityPage.tsx`) → `endpoints.aiQuality.*` → `api.*` → `/api/v1/ai-qualities`.
- FE route guard (`RequireAiQualityAccess`) → `endpoints.aiQuality.access` → `useAuth()`.
- BE controller (`AiQualityController`) → `AiQualityService` → `resolveAccess`/`requireViewAccess`/
  `requireMutateAccess` (mirrors `TicketBugMetricsService`, reuses the same per-project role lookup
  mechanism the QA dashboard's `findProjectRole` provides, pending Stop/Ask confirmation) →
  `AiQualityRepositoryPort` → `AiQualityRepositoryAdapter` → `AiQualityMapper` → `tbl_dim_ai_quality`.
- `AiQualityService` → **new** FK-parentage checks against `tbl_dim_project`/`tbl_dim_repository`/
  `tbl_dim_ticket` (BR-3: repository must belong to project, ticket must belong to repository —
  no equivalent check exists in `TicketBugMetricsService`, which validates each independently
  with no cross-validation between `repositoryId`/`ticketId`).
- All mutation/error flows → `GlobalExceptionHandler` → `ErrorResponse`.
- `AiQualityService` → `AdminAuditLogService` (`snapshot`/`logCreate`/`logUpdate`/`logDelete`/
  `logCrudFailure`, `MODULE`/`ENTITY_TYPE = "AI_QUALITY"`, best-effort, never blocks the business
  transaction) — required per `context.md`/`ticket-rules.md` (user-confirmed 2026-08-17).

## 5. FE Impact

- Add a new AI Quality management page under `pages/ai-quality/` with Project/Repository filter
  dropdowns, a ticket table, and a create/edit drawer with cascading Project→Repository→Ticket
  dropdowns and an AI Quality % input — mirrors `TicketBugMetricsPage.tsx` structurally.
- Ticket dropdown in the drawer shows all tickets in the selected repository, unfiltered by
  existing AI Quality rows (Option A, spec-pack §17 A-AI-QUALITY-11); selecting an already-tracked
  ticket surfaces the standard BR-2 409 on submit rather than being pre-filtered out.
- `effectiveRole`/`canMutate` derived the same way as `TicketBugMetricsPage`: global `ADMIN` always
  mutates; otherwise capability derives from the caller's **per-project role** for the currently
  selected project, not the global role.
- Add `endpoints.aiQuality` to `lib/api.ts` — a **smaller** group than `ticketBugMetrics`
  (`list/get/create/update/softDelete/access` only; no `options`/`ticket-options` endpoints, since
  spec-pack/context.md explicitly reject adding a new "available tickets" lookup and instead reuse
  the existing ticket-by-repository lookup unfiltered).
- Add a new `RequireAiQualityAccess.tsx` route guard mirroring `RequireTicketBugMetricsAccess.tsx`
  exactly (reads `projectId` from `useSearchParams`, calls `/access`, 401/403 → force logout,
  other errors → retry UI).
- Add route registration under the existing `:lang`-prefixed router in `App.tsx`, as a sibling to
  the `ticket-bug-metrics` route inside the same parent `<Route>` block.
- Add locale keys under `Pages.AiQuality.*` in `en`/`ja`/`vi`, mirroring the flat per-field/
  per-error-code naming convention already used by `Pages.TicketBugMetrics.*`.
- Add FE unit tests (Vitest + Testing Library, mirroring `ticket-bug-metrics-api.test.ts`/
  `ticket-bug-metrics.test.tsx`); no Playwright E2E required for this ticket by default
  (spec-pack §17 A-AI-QUALITY-12).
- **Behavioral note (superseded post-implementation)**: this document originally called for
  mirroring `TicketBugMetricsPage`'s decorative-only `CSearch`/`keyword` state (tracked but never
  wired into the list query). The actual implementation instead wires `search` end-to-end
  (`AiQualityPage.tsx` → `endpoints.aiQuality.list` → `AiQualityController` → `AiQualityService` →
  `AiQualityRepositoryPort`/`AiQualityMapper.xml`) as a working filter on ticket external
  key/title. This is a deliberate improvement over the BUG-DASHBOARD reference, not an oversight —
  confirmed and retroactively reflected in spec-pack §6.2/§7 (AC-AI-QUALITY-13) and impl-plan §5.

## 6. BE Impact

- Add `AiQualityController` under route prefix `/api/v1/ai-qualities` (spec-pack §11, §17
  A-AI-QUALITY-9), following `TicketBugMetricsController`'s route shape: `GET /`, `GET /{id}`,
  `POST /`, `PUT /{id}`, `PUT /{id}/delete` (soft delete only — never a hard `DELETE`), plus
  `GET /access` for the FE route guard. No `ticket-options`/`options` endpoints are added (spec-pack
  §11/§17 A-AI-QUALITY-11 — unlike BUG-DASHBOARD, which does expose these).
- Add `AiQualityService` enforcing: BR-1/BR-2 (one active row per ticket, 409 on duplicate-active
  create), **BR-3 (new)** repository↔project and ticket↔repository parentage validation with no
  precedent in `TicketBugMetricsService`, BR-4 (soft delete never physical), BR-5 (RBAC via
  `resolveAccess`, identical shape to `TicketBugMetricsService.resolveAccess`), BR-6
  (`ai_quality_rate` range/scale validation, 0.00–100.00 inclusive, scale ≤ 2).
- Use `AuthUserContext caller` exclusively for role checks (never `AppUser caller`), per
  `ticket-rules.md` "Must Not Do" and spec-pack §17 A-AI-QUALITY-6.
- Add `AiQualityRepositoryPort` + adapter + `AiQualityMapper`/`.xml`, following
  `TicketBugMetricsMapper`'s `@Param` usage, resultMap, `#{status}::record_status` cast, and
  WHERE-scoped guarded update/softDelete queries — **extended** with a `ticketId` filter parameter
  on `findPage`/`count` that the BUG-DASHBOARD analog does not have (spec-pack §6.2).
- Add `AiQualityDtos` (`AiQualityDto`, `AiQualityPageDto`, `CreateAiQualityRequest`/
  `UpdateAiQualityRequest` annotated `@NoXssFields`).
- Wire `AdminAuditLogService` exactly as `TicketBugMetricsService` does: snapshot before
  update-mutation, `logCreate`/`logUpdate`/`logDelete` after success, `logCrudFailure` in
  catch-and-rethrow, `MODULE = "AI_QUALITY"`, `ENTITY_TYPE = "AI_QUALITY"` — this is a **required**
  behavior for this ticket (user-confirmed 2026-08-17), not merely optional consistency.
- Add BE unit tests (service validation incl. BR-3 parentage cases, RBAC matrix, duplicate
  conflict, boundary values) and controller tests (route/status/error-envelope mapping), per
  `.claude/rules/40-testing.md`.

## 7. API Contract Impact

- Introduce new endpoints for list, detail, create, update, and soft delete under a brand-new
  route prefix `/api/v1/ai-qualities`, plus `/api/v1/ai-qualities/access` for the FE route guard.
- List response wrapper is `items/page/size/totalElements/totalPages` (`PageResult<T>` /
  `AiQualityPageDto`), matching every other `PageResult<T>`-based list endpoint — never a new
  `ApiResponse<T>` envelope (spec-pack §2.2, §17 A-AI-QUALITY-1).
- Success responses are raw DTOs (or the page wrapper); failure responses are the standard
  `ErrorResponse(timestamp, status, errorCode, message, traceId)` envelope — never an ad-hoc
  `ResponseEntity` status in the controller.
- This is a wholly new endpoint surface — no existing FE/BE contract is changed or made
  backward-incompatible.

## 8. DTO / Schema / Validation Impact

- New request DTOs accept `projectId`, `repositoryId`, `ticketId`, `ai_quality_rate` (create only
  also requires all three IDs; update accepts only `ai_quality_rate` per the immutable-after-create
  convention BUG-DASHBOARD also follows for its own count fields).
- `ai_quality_rate`: reject null, reject outside [0.00, 100.00], reject scale > 2 (e.g. 50.555) —
  BR-6, spec-pack §6.5 boundary table.
- `repository_id` must belong to `project_id`; `ticket_id` must belong to `repository_id` — BR-3,
  validation error (400), never a silent auto-correction.
- Response DTOs model soft-delete fields/status values consistent with the `record_status` enum
  convention (`ACTIVE`/`DELETED` only for this feature).

## 9. DB / Migration Impact

- New migration `V510__ai_quality.sql` (confirmed free — current highest is `V509`, re-verified
  directly against the migration directory listing during this phase).
- New table `tbl_dim_ai_quality`: UUID PK (`ticket_ai_quality_id`, `gen_random_uuid()` default),
  FKs to `project_id`, `repository_id`, `ticket_id`, `ai_quality_rate DECIMAL(5,2) NOT NULL DEFAULT
  0` with a `CHECK (ai_quality_rate >= 0 AND ai_quality_rate <= 100)`, soft-delete-from-creation
  columns (`delete_flag`, `deleted_at`, `deleted_by`, `status record_status NOT NULL DEFAULT
  'ACTIVE'`), standard audit columns — following the `V509` convention exactly (full DDL already
  drafted in spec-pack §12).
- Partial unique index `uq_ai_quality_active_ticket` on `ticket_id` filtered `WHERE delete_flag =
  FALSE AND deleted_at IS NULL AND status = 'ACTIVE'` enforces BR-1/BR-2 (one active row per
  ticket); two supporting partial indexes for project/status and repository filtering.
- One existing-table ALTER: `ck_access_log_module` on `tbl_fact_access_log` drop+recreate, adding
  `'AI_QUALITY'` to the allow-list — required because `AdminAuditLogService` writes to that table
  and the CHECK constraint currently only allows a fixed module list.
- No changes to `tbl_dim_project`, `tbl_dim_repository`, or `tbl_dim_ticket` schemas (spec-pack
  §2.2) — this is a purely additive forward migration otherwise.

## 10. Batch / Job / Event Impact

- No batch, job, queue, webhook, or event processing is in scope. The feature is purely synchronous
  request/response CRUD (spec-pack §2.2 — automatic computation from CI/AI-review tooling is
  explicitly a future ticket, not this one).
- No webhook or connector paths (GitHub/Jira/CircleCI ingestion) are touched.

## 11. Test Impact

- BE unit tests: `ai_quality_rate` boundary handling (0.00, 100.00, -0.01, 100.01, null, scale-3
  value 50.555), BR-3 parentage-mismatch validation (400, not 500, not silently corrected), BR-2
  duplicate-active conflict (409), RBAC matrix (ADMIN global-bypass, PM/QA per-project mutate,
  DEV per-project view-only with 403 on write, NONE with 403 on every endpoint including read),
  soft-delete lifecycle (create → soft-delete → absent from get/list → re-create same ticket_id
  succeeds with no false 409).
- BE controller tests: route/status/error-envelope mapping for every endpoint, `@WebMvcTest` slice
  (not full `@SpringBootTest`) per `.claude/rules/40-testing.md`.
- FE unit tests: empty/loading/error states, cascading dropdown behavior, create/edit/delete flows,
  role-based UI hiding (DEV vs PM/QA/ADMIN), API helper query-string construction and error
  handling — mirroring the `ticket-bug-metrics` Vitest test files.
- No Playwright E2E required for this ticket by default (spec-pack §17 A-AI-QUALITY-12); may be
  added later if QA requests it.
- Every AC in spec-pack §7 (AC-AI-QUALITY-1 through -12) must map to at least one test case
  (enforced via the AC table in `impl-plan.md` §11).

## 12. Operation / Monitoring Impact

- Preserve `traceId` propagation through `ErrorResponse` for debugging failures.
- Use the existing `AdminAuditLogService` best-effort pattern — an audit-write failure must never
  block or roll back the business transaction.
- New table requires no special operational runbook beyond standard Flyway migration deployment
  (spec-pack §14); soft-deleted rows accumulate like other soft-deleted dim-adjacent tables, no
  additional archival process in scope.
- No PII is stored beyond existing `created_by`/`updated_by`/`deleted_by` actor identifiers.

## 13. Rollout / Rollback Impact

- Rollout is purely additive: new API routes, new page, new migration — no existing feature is
  modified beyond the narrow touch points listed in §3 (`ck_access_log_module` on BE;
  `lib/api.ts`/`App.tsx`/locale JSON on FE).
- FE rollback: remove the new route/page/guard wiring and the `endpoints.aiQuality` group.
- BE rollback: remove the new controller/service/DTOs/ports/adapter/mapper in a single revert set.
- DB rollback: never edit the committed `V510` migration once merged — add a new corrective
  migration if the schema needs to change again (per `.claude/rules/00-safety.md` destructive-
  command policy and the `V509`/BUG-DASHBOARD precedent). Reverting the `ck_access_log_module`
  widening would require a follow-up migration re-narrowing the CHECK list, only if no
  `'AI_QUALITY'`-tagged audit rows exist yet.
- This phase is documentation-only; no production rollback action is required yet.

## 14. Explicit Non-Impact Zones

- `TicketBugMetricsController`/`Service`/`Dtos`/`Model`/`Mapper` are not modified — they are
  read-only pattern references for the new module.
- `tbl_dim_project`/`tbl_dim_repository`/`tbl_dim_ticket` schemas themselves are not altered —
  only read from, via BR-3's existence/parentage checks (no new FK added to these tables).
- `AppUser`/`AppUser.Role` enum is not touched and is not used for access decisions in this
  feature (only for the `toAppUser` audit-logging shim, mirroring the existing pattern).
- Existing migrations (`V1` through `V509`) are not edited — the change is purely additive
  (`V510`), except the one explicit `ck_access_log_module` ALTER captured in §9/§13.
- Webhook handlers, batch/job processing, and connector sync/ingestion flows are not impacted —
  this feature is not event-driven (spec-pack §2.2).
- No new `ApiResponse<T>` envelope, Spring `MessageSource`/`.properties` i18n, or `@PreAuthorize`
  usage is introduced anywhere (spec-pack §2.2, §17 A-AI-QUALITY-1/2/3) — none of these exist in
  the codebase today and none are added by this ticket.
- Bulk import/export, automatic AI-quality computation, and historical audit trail beyond
  `updated_at`/`updated_by` are all explicitly out of scope (spec-pack §2.2).
- Project, Organization, Customer, Team, Role, and other unrelated governance/dashboard modules
  are not impacted except as read-only comparison baselines.

## 15. Evidence Used

- `docs/changes/AI-QUALITY/spec-pack.md`, `context.md`, `ticket-rules.md`
- `docs/standards/templates/_ticket-template/impact-analysis.md`, `impl-plan.md`,
  `source-availability.md`, `source-inventory.md`
- `docs/changes/BUG-DASHBOARD/impact-analysis.md`, `impl-plan.md`, `source-availability.md`,
  `source-inventory.md` (style/format precedent)
- `.claude/rules/00-safety.md`, `10-style.md`, `20-architecture.md`, `30-security.md`, `40-testing.md`
- `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/ticketbugmetrics/TicketBugMetricsService.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/web/rest/TicketBugMetricsController.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/persistence/TicketBugMetricsRepositoryPort.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/TicketBugMetricsRepositoryAdapter.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/mapper/TicketBugMetricsMapper.java` + `.xml`
- `EDCAP_BE/src/main/java/com/sdd/platform/web/dto/TicketBugMetricsDtos.java`
- `EDCAP_BE/src/main/resources/db/migration/V509__ticket_bug_metrics.sql`
- `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/governance/AdminAuditLogService.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/domain/model/AuthUserContext.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/web/exception/ErrorResponse.java`, `GlobalExceptionHandler.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/common/PageResult.java`
- `EDCAP_BE/src/test/UnitTest/java/.../ticketbugmetrics/TicketBugMetricsServiceTest.java`,
  `.../web/rest/TicketBugMetricsControllerTest.java`
- `EDCAP_FE/src/pages/ticket-bug-metrics/TicketBugMetricsPage.tsx`, `types.ts`,
  `components/TbmFilterBar.tsx`
- `EDCAP_FE/src/components/auth/RequireTicketBugMetricsAccess.tsx`
- `EDCAP_FE/src/lib/api.ts` (`ticketBugMetrics` group + types)
- `EDCAP_FE/src/hooks/useAuth.ts`
- `EDCAP_FE/src/App.tsx`
- `EDCAP_FE/public/locales/en/locale.json` (`Pages.TicketBugMetrics` block)
- `EDCAP_FE/src/__ tests __/ticket-bug-metrics/ticket-bug-metrics-api.test.ts`,
  `ticket-bug-metrics.test.tsx`
- `EDCAP_FE/src/components/ui/server-table`, `.../drawer`, `.../search` (prop shapes)
- Directory listing of `EDCAP_BE/src/main/resources/db/migration/` (confirms `V510` free)
- Note: `docs/architecture/repository-db-map.md`, `route-api-map.md`, `service-layer-map.md` were
  checked but found stale relative to this domain (they document a legacy webhook/ingestion
  schema, not the `tbl_dim_*` governance tables actually being mirrored) and are **not** used as
  evidence above.
