# Source Availability

**Ticket ID**: BUG-DASHBOARD
**Create date**: 2026-08-06 08:00:00
**Author**: Claude
**Update date**: 2026-08-06 08:00:00

## 1. Strong Evidence (implementation-ready)

- **DB schema/FK shape**: `tbl_dim_project`, `tbl_dim_repository`, `tbl_dim_ticket` confirmed in
  `V4__init_shema_v2.sql` (L107-193+). `tbl_dim_ticket` has no `repository_id` column — confirmed
  absent from L174-193 and no later migration adds it (resolved H-BUG-DASHBOARD-1).
- **Soft-delete-from-creation convention**: `V120__project_management.sql`, `V130__repository_management.sql`
  give the exact column set (`delete_flag BOOLEAN NOT NULL DEFAULT FALSE`, `deleted_at TIMESTAMPTZ`,
  `deleted_by VARCHAR(100)`) and partial unique index syntax to replicate for `tbl_ticket_bug_metrics`.
- **Governance CRUD pattern**: `RepositoryController.java`, `RepositoryService.java`,
  `RepositoryDtos.java`, `RepositoryModel.java`, `RepositoryMapper.java`/`.xml` all read in full —
  routes, transaction boundaries, audit-log call sequencing, exception types, MyBatis `#{status}::record_status`
  cast pattern, defensive WHERE-scoped update/softDelete queries.
- **Error/exception chain**: `ErrorResponse.java`, `GlobalExceptionHandler.java` full mapping table
  read (`NotFoundException`→404, `ForbiddenException`→403, `ApplicationException`→409,
  `DomainException`/`MethodArgumentNotValidException`→400).
- **Per-project role-gating precedent**: `QaDashboardService.requireAnyAccess`/`requireQaAccess`
  full body read (L47-74); `findProjectRole` traced to `QaDashboardRepositoryPort` →
  `QaDashboardJdbcAdapter` → shared `DashboardProjectAccessJdbcAdapter.findProjectRole` (L229),
  confirmed the same shared adapter backs `PmDashboardRepositoryPort`/`DevDashboardRepositoryPort`/etc.
  — i.e. each dashboard module owns its own port copy delegating to one shared query, which is the
  established convention for a new per-project role check.
- **Role-string values in use**: test evidence (`PmDashboardServiceTest.java:289`,
  `DataOpsDashboardServiceTest.java`) confirms free-form role strings `"PM"`, `"DEV"`, `"DATA_OPS"`,
  `"QA"` are returned by `findProjectRole` — no enum. This substantially de-risks (but does not
  fully resolve — see §2) Open Issue OI-BUG-DASHBOARD-4.
- **Caller-context distinction**: `CurrentAppUserResolver.java` confirms `AppUser.Role` collapses
  any non-"ADMIN" role string to `EDITOR`, and `AuthUserContext` (raw role string) is the only
  parameter type that can distinguish PM/QA/DEV — matches `ticket-rules.md`'s mandate.
- **FE governance CRUD page**: `RepositoryPage.tsx` read in full (747 lines) — `CDrawerForm`
  create/edit/view modes, filter drawer, `CServerTable` pagination, `Popconfirm` delete, TanStack
  Query key/invalidate patterns, error handling via `handleError`.
- **FE cascading filter pattern**: `DevelopmentDashboardPage.tsx`/`DevFilterBar.tsx` — `enabled`
  gating, self-healing `useEffect` auto-select/reset logic.
- **FE API layer**: `lib/api.ts` `endpoints.repositories` (governance style) and `endpoints.devDashboard`
  (page-local types style) both read in full, giving two concrete templates to choose between.
- **FE role-hiding convention**: `RolePage.tsx` L242-243/592-599 — inline `user?.role === "..."`
  booleans gating JSX, distinct from route-level guards.
- **Route registration mechanism**: `App.tsx`'s `:lang` parent `<Route>` block confirmed hand-edited,
  no file-based routing; `RequireAdmin`/`RequireAuthenticated`/`RequireRoleViewer`/`RequireDashboardAccess`
  all read to understand available guard shapes.
- **Migration numbering**: directory listing of `EDCAP_BE/src/main/resources/db/migration/` confirms
  `V508__add_submitted_by_to_review.sql` is the current highest version; **`V509` is free**.
- **Templates and sibling example**: `docs/standards/templates/_ticket-template/impact-analysis.md`
  and `impl-plan.md` read verbatim; `docs/changes/REPOSITORY/impact-analysis.md`/`impl-plan.md` read
  in full as the closest completed sibling ticket, establishing this repo's actual (template-deviating)
  documentation style.

## 2. Gaps / Unverified Items (carried as Open Issues, not guessed)

- **OI-BUG-DASHBOARD-4 (exact seeded "DEV" role code)**: test-double evidence shows `"DEV"` used as
  a mock return value in `PmDashboardServiceTest.java`, but the actual seeded value in `tbl_dim_role`
  / role-assignment data was not queried against a live DB this phase. Treat `"DEV"` as the working
  assumption but re-verify against seed data before writing the role-check branch (per
  `ticket-rules.md` Stop/Ask condition).
- **OI-BUG-DASHBOARD-3 (i18n keys)**: `EDCAP_FE/public/locales/{en,ja,vi}/locale.json` inspected —
  no generic `field.ruleType` keys exist anywhere (e.g. no `validation.bug_count.min`); confirmed
  convention is per-page descriptive keys (`Pages.<Feature>.validation.<descriptiveKey>`, per
  `Pages.UserAccounts.validation.*`). Exact key names for this feature are not decided — implementation
  detail, not a blocker.
- **OI-BUG-DASHBOARD-2 (FE route/menu placement)**: `App.tsx` route-registration mechanism is fully
  understood, but the specific path segment/menu entry for Ticket Bug Metrics is not decided in
  spec-pack/context — implementation decision, not a blocker.
- **RequireDashboardAccess union decision**: `RequireDashboardAccess.tsx`'s `DashboardAccessKey` union
  (`"pm"|"qa"|"dev"|"dataOps"|"security"`) and `ACCESS_QUERY_BY_KEY` map read in full — extending it
  vs. building a bespoke guard is an explicit open implementation decision per `context.md`/`ticket-rules.md`,
  not resolved here.
- **Test coverage precedent gap**: no `RepositoryServiceTest.java`/`RepositoryControllerTest.java`
  exist for the pattern being mirrored (Repository module has zero unit/web tests). Closest analog
  with real tests is `ProjectServiceTest.java`/`ProjectControllerTest.java` (Mockito + AssertJ,
  `standaloneSetup` MockMvc, no `@WebMvcTest`). No ArchUnit/`ArchitectureTest` exists anywhere in
  this repo (confirmed via exhaustive grep) — no architecture-governance test to keep green, contrary
  to `.claude/rules/40-testing.md`'s mention of `ArchitectureTest`.
- **Architecture docs are stale for this domain**: `docs/architecture/route-api-map.md`,
  `service-layer-map.md`, `repository-db-map.md` were checked and found to document an older/legacy
  schema generation (webhook/ingestion `repository` table), not the governance CRUD module
  (`RepositoryController`/`tbl_dim_repository`) actually being mirrored. These three docs are
  **not used as evidence** in `impact-analysis.md`; direct source reading substitutes for them.

## 3. Conclusion

All items needed to write `impact-analysis.md` and `impl-plan.md` are available either as strong
evidence or as explicitly logged gaps carried forward as Open Issues / Stop-Ask conditions (per
`ticket-rules.md`). No blocking source gap prevents Phase 3 from proceeding.
