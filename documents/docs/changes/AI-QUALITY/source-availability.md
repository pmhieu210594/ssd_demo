# Source Availability

**Ticket ID**: AI-QUALITY
**Create date**: 2026-08-17 07:57:00
**Author**: BRYCENVN\nvt_dung
**Update date**: 2026-08-17 07:57:00

## 1. Strong Evidence (implementation-ready)

- **Specs fully resolved**: `spec-pack.md`, `context.md`, `ticket-rules.md` read in full. All
  Human Decisions and Open Issues are resolved (spec-pack §16/§18) — no ambiguity blocking Phase 3.
- **RBAC/access-resolution pattern**: `TicketBugMetricsService.java` read in full
  (`application/usecase/ticketbugmetrics/TicketBugMetricsService.java`, 384 lines) —
  `Access` enum (`MUTATE`/`VIEW_ONLY`/`NONE`), `resolveAccess(AuthUserContext, projectId)`
  (L217-233: global `ADMIN`→MUTATE; else `qaDashboardRepository.findProjectRole` — `PM`/`QA`→MUTATE,
  `DEV`→VIEW_ONLY, else→NONE), `requireViewAccess`/`requireMutateAccess` (L235-248).
- **CRUD + soft-delete + audit-log sequencing**: `create`/`update`/`softDelete` methods
  (L122-211) read in full — access check first, `normalize()` validation, existence/active
  checks, conflict pre-check + DB-level `DataIntegrityViolationException` fallback on create,
  before-mutation `snapshot()` on update, `AdminAuditLogService.logCreate/logUpdate/logDelete`
  on success and `logCrudFailure` in the catch block, `toAppUser(caller)` shim (L347-366) because
  `AdminAuditLogService` only accepts legacy `AppUser` for logging (never for access decisions).
- **Controller route shape**: `TicketBugMetricsController.java` read in full (116 lines) —
  `@RequestMapping("/api/v1/ticket-bug-metrics")`, `GET /`, `GET /{id}`, `POST /`, `PUT /{id}`,
  `PUT /{id}/delete` (soft delete, never a hard `DELETE` verb — confirmed by test name
  `softDelete_usesPutDeleteEndpoint_neverHardDelete`), `GET /access` (204/401/403 route guard).
- **Persistence layer**: `TicketBugMetricsRepositoryPort.java`, `TicketBugMetricsRepositoryAdapter.java`,
  `TicketBugMetricsMapper.java`/`.xml` read in full — method signatures
  (`findById`, `findPage(projectId, repositoryId, offset, limit)`, `count`, `existsActiveByTicketId`,
  `insert`, `update`, `softDelete`), WHERE-scoped guarded update/softDelete queries (0-row →
  `NotFoundException` in the service), `#{status}::record_status` cast, active-row filter fragment.
- **Migration shape**: `V509__ticket_bug_metrics.sql` read in full — table DDL, partial unique
  index `uq_ticket_bug_metrics_active_ticket` (`WHERE delete_flag = FALSE AND deleted_at IS NULL
  AND status = 'ACTIVE'`), 2 supporting partial indexes, and the `ck_access_log_module`
  drop/recreate ALTER on `tbl_fact_access_log` adding `'TICKET_BUG_METRICS'`/`'SCORE_THRESHOLD'`.
  Spec-pack §12 has already drafted the AI-QUALITY equivalent DDL 1:1 from this pattern.
- **Migration numbering**: directory listing of `EDCAP_BE/src/main/resources/db/migration/`
  confirms `V509__ticket_bug_metrics.sql` is the current highest version; **`V510` is free**
  (re-verified directly against the filesystem during this phase, not just from BUG-DASHBOARD's
  earlier evidence).
- **DTO shape**: `TicketBugMetricsDtos.java` read in full — `Dto`/`PageDto(items,page,size,
  totalElements,totalPages)`/`Create...Request`/`Update...Request` record pattern, `@NoXssFields`
  on request records, no Jakarta bean-validation annotations (validation lives in the service).
- **Audit logging contract**: `AdminAuditLogService.java` read in full — `logCreate/logUpdate/
  logDelete/logCrudFailure/logRead/snapshot` signatures (all take `AppUser`, not `AuthUserContext`),
  best-effort write (a logging failure never fails the business transaction, per its class javadoc).
- **Caller-identity model**: `AuthUserContext.java` read in full — `role` is a plain `String`
  (not an enum), compared case-insensitively; this is the type AI-QUALITY's service must accept,
  matching `TicketBugMetricsService`, never `AppUser`/`AppUser.Role` (which collapses non-ADMIN
  roles to `EDITOR` and cannot express `QA`/`DEV`).
- **Error/exception chain**: `GlobalExceptionHandler.java`/`ErrorResponse.java` read in full —
  full status-mapping table (`NotFoundException`→404, `ForbiddenException`→403,
  `ApplicationException`→409, `DomainException`/`MethodArgumentNotValidException`→400, catch-all→500).
- **Existing BE tests as pattern**: `TicketBugMetricsServiceTest.java` (302 lines) and
  `TicketBugMetricsControllerTest.java` (245 lines) read in full — Mockito-mocked collaborators,
  parameterized RBAC-matrix tests, boundary tests, `MockMvcBuilders.standaloneSetup` +
  `GlobalExceptionHandler` (not `@WebMvcTest`), inline `FixedCallerResolver` for `AuthUserContext`.
- **FE page/guard/api pattern**: `TicketBugMetricsPage.tsx` (856 lines),
  `RequireTicketBugMetricsAccess.tsx` (81 lines), the `ticketBugMetrics` endpoint group + types
  in `lib/api.ts` (L717-778, L1150-1211), and the `App.tsx` route registration all read in full —
  cascading Project→Repository→Ticket drawer, `effectiveRole`/`canMutate` derived from
  **per-project role** (not global role) except when global role is ADMIN, `CServerTable`/
  `CDrawerForm`/`CSearch` prop shapes, `useAuth()` shape, existing Vitest test files
  (`ticket-bug-metrics-api.test.ts`, `ticket-bug-metrics.test.tsx`) as the FE test template.
- **Doc structure precedent**: raw blank templates
  (`docs/standards/templates/_ticket-template/{impact-analysis,impl-plan,source-availability,
  source-inventory}.md`) and BUG-DASHBOARD's **completed** versions of all four documents read
  in full, establishing this repo's actual (template-deviating) documentation style for
  `impact-analysis.md`/`source-availability.md`/`source-inventory.md`, and the verbatim-header
  style actually used for `impl-plan.md`.

## 2. Gaps / Unverified Items (carried as Stop/Ask conditions, not guessed)

- **`qaDashboardRepository.findProjectRole` reuse for AI-QUALITY**: `ticket-rules.md` explicitly
  requires stopping to ask if this lookup is not directly reusable as-is once implementation
  begins, rather than forking a parallel lookup. Not yet exercised against AI-QUALITY's own
  service wiring — carried forward as Stop/Ask condition, not resolved in this phase.
- **BR-3 FK-parentage validation placement**: repository↔project / ticket↔repository membership
  checks have **no BUG-DASHBOARD precedent** (BUG-DASHBOARD's own evidence log confirms it does
  no repository↔ticket cross-validation at all — "H-BUG-DASHBOARD-1"). Whether this becomes an
  inline service check or a new shared validator is an implementation-phase decision per
  `ticket-rules.md` Stop/Ask condition #2 — not resolved here.
- **No ArchUnit / `ArchitectureTest` exists anywhere in `EDCAP_BE`** despite
  `.claude/rules/40-testing.md` and `docs/standards/testing.md` referencing one — confirmed via
  exhaustive search (no matches for `ArchRule`/`@AnalyzeClasses`/`classes().that()`). Hexagonal
  layering for AI-QUALITY is enforced by directory-structure convention and manual review only,
  not by an automated gate. This is a pre-existing documentation/reality gap, not something
  introduced by this ticket.
- **Architecture maps are stale for this domain**: `docs/architecture/repository-db-map.md`,
  `route-api-map.md`, `service-layer-map.md` document the legacy V1 webhook/ingestion schema, not
  `tbl_dim_*` governance tables (same staleness BUG-DASHBOARD's evidence log already flagged).
  Not used as evidence for AI-QUALITY; direct source reading substitutes for them.
- **`vi`/`ja` locale files not directly inspected this phase** — only `en/locale.json`'s
  `Pages.TicketBugMetrics` block was read as the naming-convention example. Assumed (not verified)
  that `vi`/`ja` mirror the same key structure with translated values, per standard repo practice.
- **FE `CSearch`/`keyword` inertness**: `TicketBugMetricsPage.tsx`'s search input state is tracked
  but never wired into the list query (cosmetic-only in the reference implementation). Whether
  AI-QUALITY should replicate this exact (non-functional) behavior or intentionally omit search is
  an implementation-phase decision — spec-pack does not call for search filtering either way, so
  the safer default (per ticket-rules "do not add specs not in spec-pack") is to mirror the
  reference's inert affordance rather than invent working search behavior.

## 3. Conclusion

All evidence needed to write `impact-analysis.md`, `source-inventory.md`, and `impl-plan.md` is
available, either as strong direct-source evidence or as explicitly logged gaps carried forward as
Stop/Ask conditions per `ticket-rules.md`. No blocking source gap prevents Phase 3 from proceeding.
