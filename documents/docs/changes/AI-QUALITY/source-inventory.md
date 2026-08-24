# Source Inventory

**Ticket ID**: AI-QUALITY
**Create date**: 2026-08-17 07:58:00
**Author**: BRYCENVN\nvt_dung
**Update date**: 2026-08-17 07:58:00

## 1. BE — Existing files to reuse as pattern (read-only reference)

| file | role |
|---|---|
| `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/ticketbugmetrics/TicketBugMetricsService.java` | Primary analog: `Access` enum, `resolveAccess`/`requireViewAccess`/`requireMutateAccess`, create/update/softDelete/get/search, `toAppUser` shim, `AdminAuditLogService` call sequencing |
| `EDCAP_BE/src/main/java/com/sdd/platform/web/rest/TicketBugMetricsController.java` | Route shape template: `GET /`, `GET /{id}`, `POST /`, `PUT /{id}`, `PUT /{id}/delete`, `GET /access` |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/persistence/TicketBugMetricsRepositoryPort.java` | Persistence port template |
| `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/TicketBugMetricsRepositoryAdapter.java` | Adapter template — 1:1 delegation to mapper |
| `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/mapper/TicketBugMetricsMapper.java` + `EDCAP_BE/src/main/resources/mapper/TicketBugMetricsMapper.xml` | MyBatis method-shape + XML SQL template (`@Param`, resultMap, `#{status}::record_status` cast, WHERE-scoped guarded update/softDelete) |
| `EDCAP_BE/src/main/java/com/sdd/platform/web/dto/TicketBugMetricsDtos.java` | DTO record template: `Dto`, `PageDto(items,page,size,totalElements,totalPages)`, `Create...Request`/`Update...Request` with `@NoXssFields` |
| `EDCAP_BE/src/main/resources/db/migration/V509__ticket_bug_metrics.sql` | Migration template: table + soft-delete columns + partial unique index + `ck_access_log_module` ALTER |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/governance/AdminAuditLogService.java` | `logCreate/logUpdate/logDelete/logCrudFailure/snapshot` signatures, best-effort-write pattern |
| `EDCAP_BE/src/main/java/com/sdd/platform/domain/model/AuthUserContext.java` | Caller-identity type to accept (never `AppUser`) |
| `EDCAP_BE/src/main/java/com/sdd/platform/web/exception/GlobalExceptionHandler.java`, `ErrorResponse.java` | Error envelope + exception→status mapping, reused as-is |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/common/PageResult.java` | Pagination wrapper, reused directly |
| `EDCAP_BE/src/test/UnitTest/java/.../ticketbugmetrics/TicketBugMetricsServiceTest.java`, `.../web/rest/TicketBugMetricsControllerTest.java` | Test-pattern template: Mockito mocks, parameterized RBAC matrix, boundary tests, `MockMvcBuilders.standaloneSetup` (no `@WebMvcTest`) |

## 2. BE — New files to create

| file | purpose |
|---|---|
| `EDCAP_BE/src/main/resources/db/migration/V510__ai_quality.sql` | New table `tbl_dim_ai_quality` + partial unique/supporting indexes + `ck_access_log_module` ALTER adding `'AI_QUALITY'` |
| `EDCAP_BE/src/main/java/com/sdd/platform/domain/model/AiQualityModel.java` | Domain model — mirrors `TicketBugMetricsModel` (Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`, `isDeleted()` helper) |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/persistence/AiQualityRepositoryPort.java` | Persistence port — same method set as `TicketBugMetricsRepositoryPort`, extended with a `ticketId` filter param on `findPage`/`count` |
| `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/AiQualityRepositoryAdapter.java` | Adapter implementation |
| `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/mapper/AiQualityMapper.java` + `EDCAP_BE/src/main/resources/mapper/AiQualityMapper.xml` | MyBatis mapper |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/aiquality/AiQualityService.java` | Service: `resolveAccess`/`requireViewAccess`/`requireMutateAccess` (mirrors `TicketBugMetricsService`), create/update/softDelete/get/list, **new** BR-3 FK-parentage validation (repository↔project, ticket↔repository — no BUG-DASHBOARD precedent), `AdminAuditLogService` wiring (`MODULE`/`ENTITY_TYPE = "AI_QUALITY"`) |
| `EDCAP_BE/src/main/java/com/sdd/platform/web/dto/AiQualityDtos.java` | `AiQualityDto`, `AiQualityPageDto`, `CreateAiQualityRequest`, `UpdateAiQualityRequest` (`@NoXssFields`) |
| `EDCAP_BE/src/main/java/com/sdd/platform/web/rest/AiQualityController.java` | REST routes under `/api/v1/ai-qualities`: `GET /`, `GET /{id}`, `POST /`, `PUT /{id}`, `PUT /{id}/delete`, `GET /access` |
| `EDCAP_BE/src/test/UnitTest/java/.../aiquality/AiQualityServiceTest.java`, `.../web/rest/AiQualityControllerTest.java` | New tests — RBAC matrix, BR-1..BR-6 boundary/conflict/soft-delete-lifecycle cases, controller route/status/error-envelope mapping |

## 3. FE — Existing files to reuse as pattern (read-only reference)

| file | role |
|---|---|
| `EDCAP_FE/src/pages/ticket-bug-metrics/TicketBugMetricsPage.tsx` | Page template: filters, `CDrawerForm` cascading create/edit/view, `CServerTable`, `canMutate`/`effectiveRole` gating, action-column rendering |
| `EDCAP_FE/src/pages/ticket-bug-metrics/types.ts` | `TbmFilters`/`TbmDashboardOption(s)` type-shape template |
| `EDCAP_FE/src/pages/ticket-bug-metrics/components/TbmFilterBar.tsx` | Filter-bar presentational template (native `<select>`s, `data-testid` convention, project-change resets repository) |
| `EDCAP_FE/src/components/auth/RequireTicketBugMetricsAccess.tsx` | Route-guard template: reads `projectId` from `useSearchParams`, calls `/access`, 401/403→force logout, other errors→retry UI |
| `EDCAP_FE/src/lib/api.ts` — `ticketBugMetrics` group (L1150-1211) + types (L717-778) | Endpoint-group and type template (`list/get/create/update/softDelete/ticketOptions/options/access`) |
| `EDCAP_FE/src/hooks/useAuth.ts` | `AuthUser.role` (global-role) source; per-project role comes from each feature's own `options` endpoint |
| `EDCAP_FE/src/App.tsx` (`:lang` parent `<Route>` block, L286-294) | Route registration point |
| `EDCAP_FE/public/locales/en/locale.json` — `Pages.TicketBugMetrics` block (L518-580) | i18n key-naming convention: flat per-field/per-error-code keys (e.g. `Project.Required`, `Ticket.AlreadyExists`), not nested `validation.*` |
| `EDCAP_FE/src/__ tests __/ticket-bug-metrics/ticket-bug-metrics-api.test.ts`, `ticket-bug-metrics.test.tsx` | FE test-pattern template: `vi.spyOn(fetch)` API-layer test, mocked-dependency component test |
| `EDCAP_FE/src/components/ui/server-table`, `.../drawer`, `.../search` | `CServerTable`/`CDrawerForm`/`CSearch` prop shapes, reused as-is |

## 4. FE — New files to create

| file | purpose |
|---|---|
| `EDCAP_FE/src/pages/ai-quality/AiQualityPage.tsx` | Main page component (filters, drawer, table) |
| `EDCAP_FE/src/pages/ai-quality/types.ts` | Filter/option types, mirrors `ticket-bug-metrics/types.ts` |
| `EDCAP_FE/src/pages/ai-quality/components/AiQualityFilterBar.tsx` | Filter-bar component, mirrors `TbmFilterBar.tsx` (check at implementation time whether a shared filter-bar component already generalizes this instead of a new file) |
| `EDCAP_FE/src/components/auth/RequireAiQualityAccess.tsx` | Route guard, mirrors `RequireTicketBugMetricsAccess.tsx` exactly |
| `EDCAP_FE/src/lib/api.ts` — add `endpoints.aiQuality` group + types | Edit to existing file — `list/get/create/update/softDelete/access` only (no `options`/`ticket-options`; reuses existing unfiltered ticket-by-repository lookup per spec-pack §11/§17 A-AI-QUALITY-11) |
| `EDCAP_FE/src/App.tsx` — add new `<Route path="ai-quality">` | Edit to existing file, inside the same `:lang` parent block |
| `EDCAP_FE/public/locales/{en,vi,ja}/locale.json` — add `Pages.AiQuality.*` keys | Edits to existing locale files, mirroring `Pages.TicketBugMetrics` key-naming convention |
| `EDCAP_FE/src/__ tests __/ai-quality/ai-quality-api.test.ts`, `ai-quality.test.tsx` | New Vitest + Testing Library tests, mirroring the ticket-bug-metrics test files |

## 5. DB — Existing (read-only reference)

| table/file | role |
|---|---|
| `tbl_dim_project`, `tbl_dim_repository`, `tbl_dim_ticket` | FK targets only — no schema change (spec-pack §2.2) |
| `tbl_fact_access_log` / `ck_access_log_module` CHECK constraint | Altered (not replaced) — new migration adds `'AI_QUALITY'` to the allow-list, same pattern `V509` used for `'TICKET_BUG_METRICS'`/`'SCORE_THRESHOLD'` |
| `record_status` enum (`ACTIVE, INACTIVE, ARCHIVED, DELETED`) | Reused as-is for the new `status` column; AI-QUALITY only ever sets `ACTIVE`/`DELETED` |
| `V509__ticket_bug_metrics.sql` | Direct DDL/index/ALTER template for the new migration |

## 6. DB — New

| file | purpose |
|---|---|
| `EDCAP_BE/src/main/resources/db/migration/V510__ai_quality.sql` | New `tbl_dim_ai_quality` table (drafted in full in spec-pack §12): PK, 3 FKs, `ai_quality_rate DECIMAL(5,2)` + range CHECK, soft-delete + audit columns, `uq_ai_quality_active_ticket` partial unique index, `idx_ai_quality_project_status` + `idx_ai_quality_repository` partial indexes, plus the `ck_access_log_module` ALTER |
