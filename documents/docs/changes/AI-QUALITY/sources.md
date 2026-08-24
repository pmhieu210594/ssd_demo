# Sources

**Ticket ID**: AI-QUALITY
**Create date**: 2026-08-17 07:10:27
**Author**: BRYCENVN\nvt_dung
**Update date**: 2026-08-17 07:34:25

## Ticket / Issue

| source | link/path | status | note |
|---|---|---|---|
| Raw input doc | `docs/changes/AI-QUALITY/01_raw-input.md` | read | Vietnamese technical design doc: "Màn hình Quản lý % chất lượng AI theo từng ticket". Sole source of requirements; treated as reference-only for API/DTO shapes per its own disclaimer. |

## Requirement / Design Documents

| source | path | status | trust level | note |
|---|---|---|---|---|
| Safety rules | `.claude/rules/00-safety.md` | read | high | Phase scope / destructive-action constraints |
| Style rules | `.claude/rules/10-style.md` | read | high | BE/FE coding conventions to reflect in spec |
| Architecture rules | `.claude/rules/20-architecture.md` | read | high | Hexagonal layering, `AuthUserContext` per-project role-tier note |
| Security rules | `.claude/rules/30-security.md` | read | high | Webhook/error/session rules (mostly N/A here, no webhook) |
| Testing rules | `.claude/rules/40-testing.md` | read | high | Test strategy conventions |
| Ticket-template skeletons | `docs/standards/templates/_ticket-template/{spec-pack,sources,00_brainstorm,ticket-rules}.md` | read | high | Authoritative structure for this ticket's artifacts |
| USER-MANAGEMENT source-map | `docs/changes/USER-MANAGEMENT/source-map.md` | read (via agent) | high | Confirms mapping from a change folder to real source, used as structural precedent |

## Existing Source Code

| area | path | status | purpose |
|---|---|---|---|
| DB schema (dim tables) | `EDCAP_BE/src/main/resources/db/migration/V4__init_shema_v2.sql` | read (via agent) | Original `CREATE TABLE` for `tbl_dim_project` (L107-119), `tbl_dim_repository` (L121-134), `tbl_dim_ticket` (L174-193); `record_status` enum (L28-30); audit-column convention example `tbl_dim_organization` (L84-92) |
| Project soft-delete migration | `EDCAP_BE/src/main/resources/db/migration/V120__project_management.sql` | read (via agent) | Adds boolean `delete_flag` + `deleted_at`/`deleted_by` to `tbl_dim_project` |
| Repository soft-delete migration | `EDCAP_BE/src/main/resources/db/migration/V130__repository_management.sql` | read (via agent) | Adds boolean `delete_flag` + `deleted_at`/`deleted_by` to `tbl_dim_repository` |
| Pagination wrapper | `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/common/PageResult.java` | read (via agent) | Generic `PageResult<T>(items, page, size, totalElements, totalPages)` — actual list-response convention (no `ApiResponse<T>` envelope exists) |
| Reference PageDto | `EDCAP_BE/src/main/java/com/sdd/platform/web/dto/UserAccountAdminDtos.java` | read (via agent) | Per-feature `*PageDto` pattern built via static `from(PageResult<X>)` |
| RBAC role model | `EDCAP_BE/src/main/java/com/sdd/platform/domain/model/AppUser.java` | read (via agent) | `enum Role { VIEWER, EDITOR, ADMIN, PM }` — actively used by `@CurrentUser AppUser` in controllers; no QA/DEV role present here |
| Alternate auth model | `EDCAP_BE/src/main/java/com/sdd/platform/domain/model/AuthUserContext.java` | read (via agent) | Raw-string `role` + `accessScopes`; per [[20-architecture.md]] intended for per-project role-tier checks, but not what current controllers use |
| RBAC enforcement example | `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/governance/UserAccountAdminService.java` (L295, L352) | read (via agent) | Manual role-equality checks in service code; no `@PreAuthorize` usage found anywhere in `EDCAP_BE` |
| Soft-delete pattern (char flag) | `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/ScoreThresholdConfigRepositoryAdapter.java` (L143-152) | read (via agent) | `UPDATE ... SET delete_flag = '1' ... WHERE delete_flag = '0'`; divergent minority soft-delete convention (char flag only, no `deleted_at`/`deleted_by`) — not chosen, see BUG-DASHBOARD row below |
| BUG-DASHBOARD RBAC reference | `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/ticketbugmetrics/TicketBugMetricsService.java` (L213-262) | read (via agent) | User-directed reference for AI-QUALITY's RBAC model: local `enum Access {MUTATE, VIEW_ONLY, NONE}`; global `ADMIN` → MUTATE; per-project role `PM`/`QA` → MUTATE, `DEV` → VIEW_ONLY, else → NONE. Adopted as-is for AI-QUALITY (resolves former H-AI-QUALITY-1) |
| BUG-DASHBOARD soft-delete/table reference | `EDCAP_BE/src/main/resources/db/migration/V509__ticket_bug_metrics.sql` | read (via agent) | `tbl_ticket_bug_metrics`: same Project/Repository/Ticket FK triplet as AI-QUALITY; boolean `delete_flag`+`deleted_at`/`deleted_by`+`status` soft-delete convention; partial unique index `uq_ticket_bug_metrics_active_ticket`. Adopted as-is for AI-QUALITY (resolves former H-AI-QUALITY-2) |
| BUG-DASHBOARD FE route guard reference | `EDCAP_FE/src/components/auth/RequireTicketBugMetricsAccess.tsx` | read (via agent) | Pattern for AI-QUALITY's FE route guard: calls a backend `/access` endpoint, blocks screen on 401/403 |
| BUG-DASHBOARD FE mutate-gating reference | `EDCAP_FE/src/pages/ticket-bug-metrics/TicketBugMetricsPage.tsx` (L300-308, L778-824) | read (via agent) | Pattern for AI-QUALITY's FE `canMutate` button-gating logic (UX-only; backend is authoritative) |
| Reference CRUD controller | `EDCAP_BE/src/main/java/com/sdd/platform/web/rest/UserAccountAdminController.java` | read (via agent) | Thin controller pattern, `/api/v1/admin/user-accounts`, returns DTO/`ResponseEntity<T>` directly (no envelope) |
| Reference CRUD service | `.../application/usecase/governance/UserAccountAdminService.java` | read (via agent) | `@Service` use-case + manual RBAC guard pattern |
| Reference CRUD port | `.../application/port/out/persistence/UserAccountAdminRepositoryPort.java` | read (via agent) | Outbound port interface convention |
| Reference CRUD adapter | `.../infrastructure/persistence/adapter/UserAccountAdminRepositoryAdapter.java` | read (via agent) | JDBC adapter implementation convention |
| Reference CRUD mapper | `.../infrastructure/persistence/mapper/UserAccountAdminMapper.java` | read (via agent) | Row-to-domain mapping convention |
| FE i18n config | `EDCAP_FE/src/i18n.ts` | read (via agent) | i18next config, `supportedLanguages = ["en","vi","ja"]`, JSON locale files (not Spring `.properties`) |
| FE locale files | `EDCAP_FE/public/locales/{en,vi,ja}/locale.json` | read (via agent) | Example keys (`Layout.userAccounts`, `Layout.admin`, etc.) |

## Existing Tests

| test type | path | status | note |
|---|---|---|---|
| — | — | not read | Deferred to Phase 3; Phase 1 does not require reading test files for a not-yet-implemented feature. No existing AI-Quality tests exist since the feature is new. |

## External / Office / PDF / Web References

| source | type | handling policy | note |
|---|---|---|---|
| — | — | — | None provided beyond the raw-input markdown doc; no meeting memo, Jira ticket link, or external design file was supplied. |

## Excluded Sources

| source/path | reason |
|---|---|
| Full `EDCAP_BE/src/**` and `EDCAP_FE/src/**` trees | Out of scope for Phase 1; only targeted lookups needed to verify specific conventions were performed (see Existing Source Code) |
| `EDCAP_BE/docker-compose.yml`, `.env*` | Not relevant to spec content; `.env` excluded per [[00-safety.md]] §1 regardless |
| `docs/changes/ARTIFACT-SCANNER/spec-pack.md` (initially reviewed) | Used only as a general quality-bar reference before the real `_ticket-template/` skeleton was located; superseded by the actual template, not cited as a source of AI-QUALITY requirements |

## Source Limitations

- The raw input is a single technical design document with no accompanying meeting memo, Jira ticket, or basic-design doc — some UI/behavioral details (list filters, ticket-dropdown scoping) are underspecified and recorded as Open Issues rather than inferred.
- The raw input's own API/DB reference sections explicitly state they are "reference only," so several concrete conventions it names (`ApiResponse<T>`, Spring `MessageSource`/`.properties` i18n) were checked against real source and found **not to match current conventions** — see Assumptions from Sources below and spec-pack §17.
- No design mockups/wireframes were provided beyond the textual UI description in the raw input.

## Assumptions from Sources

- The raw input's mention of `ApiResponse<T>` is aspirational/generic wording, not a real class; actual convention is `ResponseEntity<T>` / `PageResult<T>` per `UserAccountAdminController`/`PageResult.java`. Spec will follow the real convention, not the raw input's literal wording.
- The raw input's i18n description (`LocaleResolver`, `MessageSource`, `messages_{vi,en,ja}.properties`) describes a Spring backend i18n pattern that does not exist in this codebase; actual i18n is frontend-only via i18next JSON locale files. Validation error messages therefore cannot rely on Spring `{validation.key}` message interpolation as described — this is escalated as a Human Decision (see spec-pack §16), since it changes where/how validation messages are localized.
- RBAC and soft-delete conventions: per user direction, resolved by adopting BUG-DASHBOARD's
  (`TicketBugMetricsService`/`tbl_ticket_bug_metrics`) conventions exactly — global `ADMIN` or
  per-project `PM`/`QA` → mutate access, per-project `DEV` → view-only, else → no access at all; and
  boolean `delete_flag`+`deleted_at`/`deleted_by`+`status` soft delete with a partial unique active
  index. This resolves both the RBAC role-set gap and the soft-delete convention ambiguity noted
  above, and also resolves the raw input's internal contradiction about DEV access (DEV is
  view-only, not "no access").

## Human Confirmation Required

None outstanding. Remaining low-risk items (validation-message localization approach, REST path
prefix, list/filter pagination shape, E2E test requirement) were resolved by applying the only
existing codebase convention as the default. The one genuine UX trade-off — whether the ticket
dropdown in the create/edit drawer excludes already-tracked tickets — was presented to the user with
both options and their consequences; user confirmed **Option A** (show all tickets in the
repository; a duplicate selection surfaces the standard BR-2 409 on submit). See spec-pack.md §17
A-AI-QUALITY-8 through A-AI-QUALITY-12 for all resolutions.
