# Source Inventory

**Ticket ID**: BUG-DASHBOARD
**Create date**: 2026-08-06 08:15:00
**Author**: Claude
**Update date**: 2026-08-06 08:15:00

## 1. BE — Existing files to reuse as pattern (read-only reference)

| file | role |
|---|---|
| `EDCAP_BE/src/main/java/com/sdd/platform/web/rest/RepositoryController.java` | Route shape template: `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `PUT /{id}/delete` |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/governance/RepositoryService.java` | Service-method template: `@Transactional` boundaries, audit-log sequencing, before-snapshot-before-mutate pattern |
| `EDCAP_BE/src/main/java/com/sdd/platform/web/dto/RepositoryDtos.java` | DTO record template: `Dto`, `PageDto(items,page,size,totalElements,totalPages)`, `Create...Request`/`Update...Request` with `@NoXssFields` |
| `EDCAP_BE/src/main/java/com/sdd/platform/domain/model/RepositoryModel.java` | Domain model template: Lombok annotation set, `isDeleted()` helper |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/persistence/RepositoryRepositoryPort.java` | Persistence port template |
| `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/RepositoryRepositoryAdapter.java` | Adapter template |
| `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/mapper/RepositoryMapper.java` + `EDCAP_BE/src/main/resources/mapper/RepositoryMapper.xml` | MyBatis method-shape + XML SQL template (`@Param` usage, resultMap, `#{status}::record_status` cast, WHERE-scoped guarded update/softDelete) |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/qadashboard/QaDashboardService.java` (L47-74) | Role-check shape template: ADMIN bypass + `findProjectRole` compare |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/persistence/QaDashboardRepositoryPort.java` (L38) | `findProjectRole(AuthUserContext, UUID)` port-method signature template |
| `EDCAP_BE/src/main/java/.../DashboardProjectAccessJdbcAdapter.java` (L229) | Shared query implementation that all dashboard ports delegate to — new port's adapter should delegate here too |
| `EDCAP_BE/src/main/java/com/sdd/platform/web/security/CurrentAppUserResolver.java` | Confirms `AuthUserContext` vs `AppUser` parameter resolution — use `AuthUserContext caller` |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/service/AdminAuditLogService.java` | `snapshot/logCreate/logUpdate/logDelete/logCrudFailure/logRead` signatures, best-effort-write pattern |
| `EDCAP_BE/src/main/java/com/sdd/platform/web/exception/ErrorResponse.java`, `GlobalExceptionHandler.java` | Error envelope + exception→status mapping |
| `EDCAP_BE/src/test/UnitTest/java/.../governance/ProjectServiceTest.java`, `.../web/rest/ProjectControllerTest.java` | Closest available test-pattern template (Repository itself has no tests) — Mockito + AssertJ, `MockMvcBuilders.standaloneSetup` (no `@WebMvcTest`) |

## 2. BE — New files to create

| file | purpose |
|---|---|
| `EDCAP_BE/src/main/resources/db/migration/V509__ticket_bug_metrics.sql` | New table `tbl_ticket_bug_metrics` |
| `EDCAP_BE/src/main/java/com/sdd/platform/domain/model/TicketBugMetricsModel.java` | Domain model |
| `EDCAP_BE/src/main/java/com/sdd/platform/web/dto/TicketBugMetricsDtos.java` | `TicketBugMetricsDto`, `TicketBugMetricsPageDto`, `CreateTicketBugMetricsRequest`, `UpdateTicketBugMetricsRequest` |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/persistence/TicketBugMetricsRepositoryPort.java` | Persistence port (CRUD methods) |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/persistence/TicketBugMetricsAccessPort.java` (or added to an existing dashboard-style port) | `findProjectRole`-equivalent access for the new PM/QA/DEV role model — naming/placement decided in Phase 4 |
| `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/TicketBugMetricsRepositoryAdapter.java` | Adapter implementation |
| `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/mapper/TicketBugMetricsMapper.java` + `EDCAP_BE/src/main/resources/mapper/TicketBugMetricsMapper.xml` | MyBatis mapper |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/.../TicketBugMetricsService.java` | Service: validation, role-check method (new, modeled on but not reusing `QaDashboardService`), CRUD |
| `EDCAP_BE/src/main/java/com/sdd/platform/web/rest/TicketBugMetricsController.java` | REST routes |
| `EDCAP_BE/src/test/UnitTest/java/.../TicketBugMetricsServiceTest.java`, `.../TicketBugMetricsControllerTest.java` | New tests (required per `.claude/rules/40-testing.md` even though Repository has none) |

## 3. FE — Existing files to reuse as pattern (read-only reference)

| file | role |
|---|---|
| `EDCAP_FE/src/pages/RepositoryPage.tsx` | Page template: `CDrawerForm` create/edit/view, filter drawer, `CServerTable`, `Popconfirm` delete |
| `EDCAP_FE/src/lib/api.ts` — `endpoints.repositories` (L1057-1086) | Governance-style endpoint template |
| `EDCAP_FE/src/lib/api.ts` — `endpoints.devDashboard` (L1409-1506) | Page-local-types endpoint style template (alternative) |
| `EDCAP_FE/src/pages/development-dashboard/DevelopmentDashboardPage.tsx`, `.../components/DevFilterBar.tsx` | Cascading Project→Repository filter + `enabled` gating + self-healing `useEffect` pattern |
| `EDCAP_FE/src/hooks/useAuth.ts` | `AuthUser.role` flat-string source for inline role checks |
| `EDCAP_FE/src/components/auth/RequireDashboardAccess.tsx` | `DashboardAccessKey` union + `ACCESS_QUERY_BY_KEY` map — extend-or-bespoke decision point |
| `EDCAP_FE/src/App.tsx` (`:lang` parent `<Route>` block) | Route registration point |
| `EDCAP_FE/src/pages/RolePage.tsx` (L242-243, 592-599) | Inline `user?.role === "..."` role-hiding convention |
| `EDCAP_FE/src/interfaces/index.ts` (L161-293) | `IForm`/`IFormItem`/`IFormItemRule` field-config shape for `CForm` |
| `EDCAP_FE/src/__ tests __/repository/repository.test.tsx`, `repository-api.test.ts` | FE test-pattern template (Vitest + Testing Library, `vi.hoisted` API mocks) |
| `EDCAP_FE/e2e_tests/tests/repository/repository.spec.ts` | E2E test-pattern template |

## 4. FE — New files to create

| file | purpose |
|---|---|
| `EDCAP_FE/src/pages/TicketBugMetricsPage.tsx` (exact name/path TBD Phase 4) | Main page component |
| `EDCAP_FE/src/lib/api.ts` — add `endpoints.ticketBugMetrics` | New endpoint group (edit to existing file) |
| Type placement — either inline in `lib/api.ts` (Repository-style) or `EDCAP_FE/src/pages/ticket-bug-metrics/types.ts` (devDashboard-style) | Decision flagged, not resolved (context.md open decision) |
| `EDCAP_FE/src/App.tsx` — add new `<Route>` | Edit to existing file, route registration |
| Possibly `EDCAP_FE/src/components/auth/RequireDashboardAccess.tsx` — add new `DashboardAccessKey` union member | Only if this guard is chosen over a bespoke one — explicit shared-file-edit flag |
| `EDCAP_FE/src/__ tests __/ticket-bug-metrics/*.test.tsx` (folder-naming mirrors existing literal `__ tests __`) | New FE unit tests |
| `EDCAP_FE/e2e_tests/tests/ticket-bug-metrics/*.spec.ts` | New E2E tests |
| `EDCAP_FE/public/locales/{en,ja,vi}/locale.json` — add `Pages.TicketBugMetrics.*` keys | Edits to existing locale files |

## 5. DB — Existing (read-only reference)

| table/file | role |
|---|---|
| `tbl_dim_project`, `tbl_dim_repository`, `tbl_dim_ticket` (`V4__init_shema_v2.sql` L107-193) | Parent dimension tables; independent existence/active checks only |
| `V120__project_management.sql`, `V130__repository_management.sql` | Soft-delete-from-creation column + partial unique index convention to replicate |
| `V508__add_submitted_by_to_review.sql` | Current highest migration version (confirms `V509` free) |

## 6. DB — New

| file | purpose |
|---|---|
| `EDCAP_BE/src/main/resources/db/migration/V509__ticket_bug_metrics.sql` | New `tbl_ticket_bug_metrics` table with soft-delete-from-creation columns and partial unique index on `ticket_id` |
