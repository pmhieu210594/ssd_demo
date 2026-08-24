# Context

**Ticket ID**: AI-QUALITY
**Create date**: 2026-08-17 07:43:48
**Author**: BRYCENVN\nvt_dung
**Update date**: 2026-08-17 07:48:18

## Screen / API / Batch / Related Job

| category | related item | current source status | Phase 2 decision / note |
|---|---|---|---|
| FE screen | AI Quality management screen | Not found (new) | New page under `EDCAP_FE/src/pages/` (folder TBD in impl-plan, e.g. `pages/ai-quality/AiQualityPage.tsx`), modeled directly on `pages/ticket-bug-metrics/TicketBugMetricsPage.tsx`. Project/Repository filter dropdowns + ticket table + create/edit drawer per spec-pack §2.1. |
| FE route guard | AI Quality route guard | Not found (new) | New `RequireAiQualityAccess.tsx` under `EDCAP_FE/src/components/auth/`, mirroring `RequireTicketBugMetricsAccess.tsx` exactly (calls a backend `/access` endpoint, blocks screen on 401/403). |
| BE API | `POST /api/v1/ai-qualities` | Not found (new) | Create. Mirrors `TicketBugMetricsController.create`. 409 on active-duplicate ticket_id (BR-2). |
| BE API | `PUT /api/v1/ai-qualities/{id}` | Not found (new) | Update `ai_quality_rate`. Mirrors `TicketBugMetricsController.update`. |
| BE API | `PUT /api/v1/ai-qualities/{id}/delete` | Not found (new) | Soft delete. Mirrors `TicketBugMetricsController.softDelete` path shape (`PUT .../delete`, not `DELETE`). |
| BE API | `GET /api/v1/ai-qualities/{id}` | Not found (new) | Get by id; 404 if missing/soft-deleted. |
| BE API | `GET /api/v1/ai-qualities` | Not found (new) | List with `page`/`size`/`projectId`/`repositoryId`/`ticketId` filters, `PageResult<T>`-shaped response. Mirrors `TicketBugMetricsController.list`. |
| BE API | `GET /api/v1/ai-qualities/access` | Not found (new) | FE route-guard check. Mirrors `TicketBugMetricsController.access` (204 on success, 401/403 otherwise). |
| Batch / Job | AI Quality batch/job | Not found | Out of scope per spec-pack §2.2 ("Automatic computation... is a future ticket"). Do not add one. |
| Dependent domain | `tbl_dim_project` / `tbl_dim_repository` / `tbl_dim_ticket` | Existing, FK targets only | No schema change to these tables (spec-pack §2.2, §12). |
| Dependent domain | `tbl_fact_access_log` audit module registry | Existing, affected (confirmed) | `ck_access_log_module` CHECK constraint currently allows a fixed list of module names (e.g. `'TICKET_BUG_METRICS'`, `'SCORE_THRESHOLD'`, `'MEMBER_USER'`). **User-confirmed 2026-08-17: AI-QUALITY must use `AdminAuditLogService` for create/update/delete**, mirroring `TicketBugMetricsService` (`MODULE = "AI_QUALITY"`, `ENTITY_TYPE = "AI_QUALITY"`). The new AI-QUALITY migration must add `'AI_QUALITY'` to `ck_access_log_module` (same pattern as `V509__ticket_bug_metrics.sql` adding `'TICKET_BUG_METRICS'`/`'SCORE_THRESHOLD'`). |

## Example of a correctly implemented code

| purpose | file/path | pattern to follow |
|---|---|---|
| RBAC + soft-delete service pattern (**primary analog for this ticket**) | `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/ticketbugmetrics/TicketBugMetricsService.java` | Local `enum Access { MUTATE, VIEW_ONLY, NONE }` (L215); `resolveAccess(AuthUserContext caller, UUID projectId)` (L217): global `ADMIN` → `MUTATE`, else per-project role via repository lookup, `PM`/`QA` → `MUTATE`, `DEV` → `VIEW_ONLY`, else → `NONE`; `requireViewAccess`/`requireMutateAccess` (L242, L248) throw `ForbiddenException`; `softDelete` (L192) sets `deleted_by`/`deleted_at` AND `updated_by`/`updated_at` in one repository call; `loadActiveOrThrow` (L266) treats `deleteFlag || deletedAt != null || status == DELETED` as not-found. **Do not** use `UserAccountAdminService.requireAdmin`'s single-global-role check — wrong model for this ticket (see Forbidden section below). |
| REST controller shape | `EDCAP_BE/src/main/java/com/sdd/platform/web/rest/TicketBugMetricsController.java` | `@RequestMapping("/api/v1/ticket-bug-metrics")` with `GET /`, `GET /{id}`, `POST /`, `PUT /{id}`, `PUT /{id}/delete`, `GET /access`. AI-QUALITY reuses this exact endpoint/verb shape under `/api/v1/ai-qualities` (spec-pack §11). No `ticket-options`/`options` endpoints are needed for AI-QUALITY (existing ticket-by-repository lookup is reused unfiltered, spec-pack §11/§17 A-AI-QUALITY-11). |
| Migration shape | `EDCAP_BE/src/main/resources/db/migration/V509__ticket_bug_metrics.sql` | Table + partial unique index `uq_ticket_bug_metrics_active_ticket` on `(ticket_id) WHERE delete_flag = FALSE AND deleted_at IS NULL AND status = 'ACTIVE'` + two supporting partial indexes. Spec-pack §12 already drafts the AI-QUALITY equivalent (`tbl_dim_ai_quality`, `uq_ai_quality_active_ticket`) mirroring this exactly — use it as-is. |
| Pagination wrapper | `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/common/PageResult.java` | `record PageResult<T>(items, page, size, totalElements, totalPages)` — reuse directly, do not invent a new envelope. |
| Per-feature page DTO pattern | `EDCAP_BE/src/main/java/com/sdd/platform/web/dto/TicketBugMetricsDtos.java` | `record TicketBugMetricsDto(...)` with static `from(TicketBugMetricsModel)`; `record TicketBugMetricsPageDto(items, page, size, totalElements, totalPages)` with static `from(PageResult<TicketBugMetricsModel>)`. AI-QUALITY's `AiQualityDto`/`AiQualityPageDto` should mirror this 1:1. |
| FE route guard | `EDCAP_FE/src/components/auth/RequireTicketBugMetricsAccess.tsx` | Reads `projectId` from `useSearchParams`; `useQuery(["ticket-bug-metrics-access", projectId], () => endpoints.ticketBugMetrics.access({projectId}).then(() => true), { enabled: isAuthenticated, retry: false })`; unauthenticated → redirect to login; 401/403 → force logout+redirect; other errors → retry UI. |
| FE mutate-button gating | `EDCAP_FE/src/pages/ticket-bug-metrics/TicketBugMetricsPage.tsx` (~L296-308 role resolution, ~L774-839 action-column gating) | `effectiveRole` = global `ADMIN` if global role is ADMIN, else the selected project's per-project role; `canMutate = effectiveRole in {ADMIN, PM, QA}`; table's add/edit/delete actions are conditionally rendered/disabled based on `canMutate` and row `status === "DELETED"`. This is UX-only — backend `resolveAccess` is authoritative (spec-pack §13). |
| FE API client endpoint group | `EDCAP_FE/src/lib/api.ts` (`ticketBugMetrics` group, ~L1150-1211; types ~L717-778) | `list(params)`, `get(id)`, `create(body)`, `update(id, body)`, `softDelete(id)`, `access(params)` all typed and returning typed promises. AI-QUALITY should add an `aiQuality` (or similarly named) group with the same method shapes; no direct `fetch` calls in page/components (per `20-architecture.md`). |

## Allowed common components
| component | path | usage note |
|---|---|---|
| `CServerTable` / `IServerTableColumn` | `EDCAP_FE/src/components/ui/server-table` | Paginated table w/ action column and `onAdd`; used as-is by `TicketBugMetricsPage`. |
| `CDrawerForm` | `EDCAP_FE/src/components/ui/drawer` | Create/edit/view drawer; use for the cascading Project→Repository→Ticket + rate form. |
| `CSearch` | `EDCAP_FE/src/components/ui/search` | Keyword/filter search input. |
| `CTooltip` | `EDCAP_FE/src/components/ui/tooltip` | Row/action tooltips. |
| `CSvgIcon` | `EDCAP_FE/src/components/ui/svg-icon` | Icon rendering, per `EIcon` enum. |
| `Badge` | `EDCAP_FE/src/components/ui/badge` | Status display (`ACTIVE`/`DELETED`). |
| `RoleTabs` | `EDCAP_FE/src/components/dashboard/RoleTabs` | Used by `TicketBugMetricsPage` for role-based tab display; reuse if the AI-QUALITY screen needs the same role-tab UX. |
| `useAuth` | `EDCAP_FE/src/hooks/useAuth` | Provides current user/global role for `effectiveRole`/`canMutate` resolution. |
| `api` / `endpoints` | `EDCAP_FE/src/lib/api.ts` | Required API access point; add a new `aiQuality` endpoint group here rather than a new file. |
| `formatDateTime` | `EDCAP_FE/src/lib/utils` | Audit timestamp formatting in the table/detail view. |

## Forbidden common components
| component | reason |
|---|---|
| Direct `fetch` in AI-QUALITY page/components | Architecture rule (`20-architecture.md`): all API calls must go through `lib/api.ts`. |
| `UserAccountAdminService.requireAdmin` / `AppUser.Role`-based single-global-role check | Wrong RBAC model for this ticket. BR-5 requires per-project `PM`/`QA`/`DEV` differentiation, which `AppUser.Role` (VIEWER/EDITOR/ADMIN/PM — no QA/DEV) cannot express. Must use `AuthUserContext` + `TicketBugMetricsService.resolveAccess()`-style per-project lookup instead. |
| `@PreAuthorize` | Does not exist anywhere in `EDCAP_BE` (spec-pack §17 A-AI-QUALITY-3); RBAC is manual in the service layer. |
| New `ApiResponse<T>` envelope | Does not exist in this codebase; spec-pack explicitly excludes introducing one (§2.2, §17 A-AI-QUALITY-1). Use `ResponseEntity<T>`/`PageResult<T>` directly. |
| Spring `MessageSource` / `.properties` i18n | Does not exist in this codebase (§17 A-AI-QUALITY-2); i18n is FE-only i18next JSON. |
| New Ant Design `Table`/`Form` | Not the established pattern for this feature family; reuse `CServerTable`/`CDrawerForm` instead. |
| Hard `DELETE` | Forbidden; soft delete only (BR-4, spec-pack §2.1). |
| `ScoreThresholdConfigRepositoryAdapter`-style char-flag-only soft delete (`delete_flag = '1'`) | Divergent minority pattern in this codebase, explicitly not chosen for this ticket (sources.md L38) — use the boolean `delete_flag` + `deleted_at`/`deleted_by` + `status` convention instead. |

## List of methods that actually exist
| method/class | path | usage |
|---|---|---|
| `TicketBugMetricsRepositoryPort.findById(UUID)` | `application/port/out/persistence/TicketBugMetricsRepositoryPort.java` | Pattern for `AiQualityRepositoryPort.findById`. |
| `TicketBugMetricsRepositoryPort.findPage(projectId, repositoryId, offset, limit)` | same | Pattern for list endpoint's paged query — note AI-QUALITY needs an additional `ticketId` filter per spec-pack §6.2, so the port signature must be extended accordingly (e.g. add a `ticketId` param), not copied verbatim. |
| `TicketBugMetricsRepositoryPort.count(projectId, repositoryId)` | same | Pattern for total-count query; same `ticketId`-filter extension note applies. |
| `TicketBugMetricsRepositoryPort.existsActiveByTicketId(UUID)` | same | Directly reusable pattern for BR-2's create-conflict check. |
| `TicketBugMetricsRepositoryPort.insert(TicketBugMetricsModel)` | same | Pattern for `AiQualityRepositoryPort.insert`. |
| `TicketBugMetricsRepositoryPort.update(TicketBugMetricsModel)` | same | Pattern for `AiQualityRepositoryPort.update`. |
| `TicketBugMetricsRepositoryPort.softDelete(id, deletedBy, deletedAt, updatedBy, updatedAt)` | same | Pattern for `AiQualityRepositoryPort.softDelete` — sets both deleted-* and updated-* columns in one call. |
| `PageResult<T>(items, page, size, totalElements, totalPages)` | `application/usecase/common/PageResult.java` | Reuse directly; do not create a competing pagination type. |
| `GlobalExceptionHandler` mappings | `web/exception/GlobalExceptionHandler.java` | `NotFoundException`→404, `ForbiddenException`→403, `ApplicationException`(parent of conflict-style exceptions)→409, `DomainException`→400, `MethodArgumentNotValidException`→400 (`VALIDATION_ERROR`/`UNSAFE_INPUT`), catch-all→500. AI-QUALITY's exceptions must extend these existing hierarchies, not introduce new ad-hoc `ResponseEntity` status codes in the controller (per `30-security.md`). |
| `ErrorResponse(timestamp, status, error, message, traceId)` | `web/exception/ErrorResponse.java` | The only error response shape; `traceId` comes from `MDC.get("traceId")`. |
| `AuthUserContext` fields: `userAccountId, username, displayName, email, role (String), accessScopes (List<String>)` | `domain/model/AuthUserContext.java` | `role` is a free-form string (not an enum) compared case-insensitively (`"ADMIN"`, `"PM"`, `"QA"`, `"DEV"`) — this is the caller-identity type AI-QUALITY's service must accept, matching `TicketBugMetricsService`, not `AppUser`. |
| `@NoXssFields` annotation | seen on `CreateTicketBugMetricsRequest`/`UpdateTicketBugMetricsRequest` in `TicketBugMetricsDtos.java` | Apply to AI-QUALITY's create/update request DTOs for XSS-field validation, matching the reference pattern. |

## Forbidden methods / methods that do not exist
| method/API | reason | alternative |
|---|---|---|
| `ApiResponse<T>` (any method returning it) | Class does not exist anywhere in `EDCAP_BE` (verified, spec-pack §17 A-AI-QUALITY-1) | Return `ResponseEntity<T>` / `PageResult<T>` directly. |
| `MessageSource.getMessage(...)` / `.properties`-key resolution for validation messages | No `LocaleResolver`/`MessageSource`/`.properties` files exist (§17 A-AI-QUALITY-2) | BE returns a stable error code via `ErrorResponse`; FE localizes via i18next. |
| `@PreAuthorize(...)` on any AI-QUALITY controller/service method | Zero usages anywhere in `EDCAP_BE` (§17 A-AI-QUALITY-3) | Manual `resolveAccess`/`requireViewAccess`/`requireMutateAccess` checks in the service layer. |
| `AppUser.Role.QA` / `AppUser.Role.DEV` | These enum values do not exist on `AppUser.Role` (`VIEWER, EDITOR, ADMIN, PM` only) | Use `AuthUserContext.role` (free-form string) with the per-project role lookup mechanism, matching `TicketBugMetricsService.resolveAccess()`. |
| Any physical `DELETE FROM tbl_dim_ai_quality` | Hard delete is out of scope (BR-4, spec-pack §2.1) | `softDelete` sets `delete_flag`/`deleted_at`/`deleted_by`/`status='DELETED'`. |
| A new bulk import/export endpoint | Out of scope (spec-pack §2.2) | None — not needed for this ticket. |
| An "available tickets" filtered-lookup endpoint (excluding already-tracked tickets) | Explicitly rejected; Option A confirmed by user (spec-pack §17 A-AI-QUALITY-11) | Reuse the existing unfiltered ticket-by-repository lookup; rely on BR-2's 409 for duplicates. |

## DTO / Entity / Table / Migration mapping
| layer | name | path | Note |
|---|---|---|---|
| DB table (new) | `tbl_dim_ai_quality` | new Flyway migration (path/version TBD in impl-plan, e.g. `V5xx__ai_quality.sql`) | Full DDL already drafted in spec-pack §12, mirrors `tbl_ticket_bug_metrics` shape 1:1 (PK, 3 FKs, rate column, soft-delete columns, audit columns, range CHECK, partial unique index, 2 supporting partial indexes). |
| DB constraint (existing, to be altered) | `ck_access_log_module` on `tbl_fact_access_log` | migration that introduced it (per BUG-DASHBOARD's `V509__ticket_bug_metrics.sql`, which added `'TICKET_BUG_METRICS'`/`'SCORE_THRESHOLD'` to it) | **Confirmed (user, 2026-08-17)**: the new AI-QUALITY migration must add an `ALTER TABLE ... DROP CONSTRAINT ck_access_log_module / ADD CONSTRAINT ck_access_log_module CHECK (... IN (..., 'AI_QUALITY'))` statement (or equivalent), since `AdminAuditLogService` will be used for create/update/delete. |
| BE audit logging (new usage) | `AdminAuditLogService.logCreate/logUpdate/logDelete/logCrudFailure` | existing service, `application/usecase/.../AdminAuditLogService.java` (per `TicketBugMetricsService` usage) | AI-QUALITY's `AiQualityService` must call this on every create/update/soft-delete (success and failure paths), with `MODULE = "AI_QUALITY"`, `ENTITY_TYPE = "AI_QUALITY"`, mirroring `TicketBugMetricsService`'s try/catch-and-log pattern (incl. `toAppUser(caller)` shim if `AdminAuditLogService`'s signature still requires an `AppUser`, per `TicketBugMetricsService.toAppUser`). |
| BE domain model (new) | `AiQualityModel` | new, e.g. `domain/model/AiQualityModel.java` | Mirrors `TicketBugMetricsModel`: Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` (never `@Data`, per `10-style.md`), `isDeleted()` helper, `enum Status { ACTIVE, DELETED }` or reuse shared `record_status`-backed status type if one exists. |
| BE port (new) | `AiQualityRepositoryPort` | new, e.g. `application/port/out/persistence/AiQualityRepositoryPort.java` | Mirrors `TicketBugMetricsRepositoryPort` method set, extended with a `ticketId` list-filter parameter (spec-pack §6.2). |
| BE adapter (new) | `AiQualityRepositoryAdapter` | new, e.g. `infrastructure/persistence/adapter/AiQualityRepositoryAdapter.java` | 1:1 delegation to a new MyBatis mapper, mirroring `TicketBugMetricsRepositoryAdapter`. |
| BE mapper (new) | `AiQualityMapper` (+ XML) | new, e.g. `infrastructure/persistence/mapper/AiQualityMapper.java` / `resources/mapper/AiQualityMapper.xml` | Mirrors `TicketBugMetricsMapper`/`.xml`. |
| BE service (new) | `AiQualityService` | new, e.g. `application/usecase/aiquality/AiQualityService.java` | Mirrors `TicketBugMetricsService`'s `Access` enum, `resolveAccess`, `requireViewAccess`/`requireMutateAccess`, create/update/softDelete/get/list methods, and BR-3 FK-parentage validation (project↔repository↔ticket) not present in the BUG-DASHBOARD analog and must be added new. |
| BE controller (new) | `AiQualityController` | new, e.g. `web/rest/AiQualityController.java` | Mirrors `TicketBugMetricsController`'s `@RequestMapping("/api/v1/ai-qualities")` shape and verb/path set (§11). |
| BE DTOs (new) | `AiQualityDto`, `AiQualityPageDto`, `CreateAiQualityRequest`, `UpdateAiQualityRequest` | new, e.g. `web/dto/AiQualityDtos.java` | Mirrors `TicketBugMetricsDtos.java` record shapes; request DTOs annotated `@NoXssFields`. |
| FE route (new) | AI Quality management route | `EDCAP_FE/src/App.tsx` (route registration) | Path/segment TBD in impl-plan; must keep `/:lang/...` first-segment convention per `20-architecture.md`. |
| FE guard (new) | `RequireAiQualityAccess.tsx` | new, `EDCAP_FE/src/components/auth/` | Mirrors `RequireTicketBugMetricsAccess.tsx`. |
| FE page (new) | `AiQualityPage.tsx` (name/folder TBD) | new, `EDCAP_FE/src/pages/` | Mirrors `TicketBugMetricsPage.tsx` structure (filters, `CServerTable`, `CDrawerForm`, `canMutate` gating). |
| FE api group (new) | `endpoints.aiQuality.*` (name TBD) | `EDCAP_FE/src/lib/api.ts` | Mirrors `ticketBugMetrics` group: `list`, `get`, `create`, `update`, `softDelete`, `access`. |

## formItemNm / SEQNO / Master Data / Code Value Mapping
| display/item | internal value | source | note |
|---|---|---|---|
| AI Quality Rate | `ai_quality_rate` | spec-pack §6.2/§12 | `DECIMAL(5,2)`, range 0.00–100.00 inclusive, scale ≤ 2 (BR-6). |
| Status | `status` | existing `record_status` enum (`ACTIVE, INACTIVE, ARCHIVED, DELETED`) | Reused as-is; AI-QUALITY only ever sets `ACTIVE` (create) or `DELETED` (soft delete) — `INACTIVE`/`ARCHIVED` are not used by this feature's business rules. |
| Access tier | `MUTATE` / `VIEW_ONLY` / `NONE` | local enum pattern mirroring `TicketBugMetricsService.Access` | Not persisted; computed per-request from `AuthUserContext` + per-project role lookup (BR-5). |
| `formItemNm` | N/A | source inspection (no matches found in `EDCAP_BE`/`EDCAP_FE`) | Concept does not exist in this codebase; do not invent one. |
| `SEQNO` | N/A | source inspection (no matches found) | Concept does not exist in this codebase; do not invent one. |
| Master data | `tbl_dim_project`, `tbl_dim_repository`, `tbl_dim_ticket` | existing tables | FK targets only; AI-QUALITY does not modify or duplicate their data. |

## Multilingual Note

- FE i18n is entirely i18next-based, JSON locale files at `EDCAP_FE/public/locales/{en,vi,ja}/locale.json` (per `i18n.ts`, `supportedLanguages = ["en","vi","ja"]`). Add AI-QUALITY-specific UI/label/validation keys to all three files consistently.
- No Spring `MessageSource`/`.properties` backend i18n exists (§17 A-AI-QUALITY-2) — do not attempt to add one. BE returns stable error codes only; FE maps codes to localized strings.
- Do not hard-code English/Vietnamese/Japanese text directly in components.

## Encoding / Mojibake Note

- Preserve UTF-8 across all new Markdown, Java, TypeScript, and locale JSON files.
- No Shift-JIS or other encoding conversions.
- Keep locale JSON valid (no trailing commas, no duplicate keys) when adding new keys.

## Log / Audit / Operation Note

- No PII is stored beyond existing `created_by`/`updated_by`/`deleted_by` actor identifiers, consistent with other soft-deleted dim-adjacent tables (spec-pack §13).
- Errors must be logged server-side with `traceId` for correlation; no stack traces reaching the client (`30-security.md`, spec-pack §6.6 Observability row).
- Do not log secrets or unnecessary personal data (per `.claude/rules/00-safety.md` §1 and ticket-template's baseline "Must Follow").
- **Confirmed (user, 2026-08-17)**: AI-QUALITY create/update/delete must call `AdminAuditLogService`, mirroring `TicketBugMetricsService`'s pattern (`MODULE = "AI_QUALITY"`, `ENTITY_TYPE = "AI_QUALITY"`, log on both success and failure via `logCrudFailure` in the catch block). This is not merely optional consistency with BUG-DASHBOARD — it is now a requirement for this ticket. The new migration must add `'AI_QUALITY'` to `tbl_fact_access_log.ck_access_log_module`.
- No batch/job/event output is expected for this ticket (spec-pack §2.2).

## Ticket-Specific Constraints

- RBAC must use `AuthUserContext` + per-project role resolution (`TicketBugMetricsService.resolveAccess()` pattern) — **not** `AppUser.Role`/`UserAccountAdminService.requireAdmin`'s single-global-role model, since `AppUser.Role` cannot express `QA`/`DEV`.
- Soft delete only; never a physical `DELETE` (BR-4).
- No new `ApiResponse<T>` envelope; no Spring `MessageSource` i18n (§2.2, §17).
- No schema changes to `tbl_dim_project`, `tbl_dim_repository`, or `tbl_dim_ticket` (§2.2, §12).
- No bulk import/export; no automatic AI-quality computation from CI/AI-review tooling; no historical audit trail beyond `updated_at`/`updated_by` (§2.2).
- BR-3 (repository belongs to project; ticket belongs to repository) is a validation concern **not present** in the BUG-DASHBOARD analog and must be implemented new in `AiQualityService` — do not assume it's inherited "for free" from copying `TicketBugMetricsService`.
- Ticket dropdown in the create/edit drawer shows all tickets in the selected repository, unfiltered by existing AI Quality rows (Option A, §17 A-AI-QUALITY-11); duplicate selection surfaces the standard BR-2 409 on submit — do not pre-filter the dropdown.
- Audit logging via `AdminAuditLogService` is required for create/update/delete (user-confirmed 2026-08-17) — this extends spec-pack §14 ("no special operational runbook") with a concrete migration requirement (`ck_access_log_module` update) not originally listed in spec-pack §12.
