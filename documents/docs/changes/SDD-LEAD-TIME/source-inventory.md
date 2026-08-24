# Source Inventory

**Ticket ID**: SDD-LEAD-TIME
**Create date**: 2026-08-21 16:00:00
**Author**: nvt_dung
**Update date**: 2026-08-21 16:00:00

| area | path | type | owner | read status | note |
|---|---|---|---|---|---|
| BE domain / markdown parsing | `EDCAP_BE/src/main/java/com/sdd/platform/domain/service/markdown/core/MarkdownParserCore.java` | source | BE | full | `extractHeaderMetadata()` / `TOP_META_PATTERN`, lines 103-117 |
| BE domain / markdown parsing | `EDCAP_BE/src/main/java/com/sdd/platform/domain/service/markdown/specpack/SpecPackMarkdownParser.java` | source | BE | full | `ParsedArtifact.headerMetadata` field, lines 106-115, 604-633 |
| BE domain / markdown parsing | `EDCAP_BE/src/main/java/com/sdd/platform/domain/service/markdown/report/ReportMarkdownParser.java` | source | BE | full | `ParsedArtifact.headerMetadata` field, lines 61-70, 441-460 |
| BE application / scanner | `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/scanner/ArtifactScannerService.java` | source | BE | excerpt | `persistSpecPackParse` 515-621, `persistReportParse` 893-1027, ticket resolution 280-453 |
| BE application / port | `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/persistence/ArtifactScannerPersistencePort.java` | source | BE | full | 32 existing methods, none touch `started_at`/`completed_at` |
| BE infrastructure / persistence | `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/PmDashboardJdbcAdapter.java` | source | BE | excerpt | `findDetail` 216-271, `baseTicketQuery` 399-437, `mapTicketRow` 477-512 |
| BE application / models | `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/pmdashboard/PmDashboardModels.java` | source | BE | excerpt | `DashboardTicketRow` 117-216 (canonical + compact ctor), `DashboardTicketDetail` 278-289 |
| DB migration | `EDCAP_BE/src/main/resources/db/migration/V4__init_shema_v2.sql` | migration | BE/DBA | excerpt | `tbl_dim_ticket` table def, lines 174-193 |
| DB migration | `EDCAP_BE/src/main/resources/db/migration/` (directory) | migration listing | BE/DBA | listing | Latest = `V512__add_ai_finding_stat_tracking.sql` |
| FE component | `EDCAP_FE/src/pages/pm-dashboard/components/TicketDetailDrawer.tsx` | source | FE | excerpt | `TicketInformationCard`, lines 236-291 |
| FE lib | `EDCAP_FE/src/lib/api.ts` | source | FE | excerpt | `PmDashboardTicketRow` 549-583, `PmDashboardTicketDetail` 585-597+, `endpoints.pmDashboard` 1842-1931 |
| FE lib | `EDCAP_FE/src/lib/utils.ts` | source | FE | excerpt | `formatDateTime()`, lines 11-31+ |
| FE locale | `EDCAP_FE/public/locales/en/locale.json` | i18n | FE | excerpt (targeted keys) | `Pages.PmDashboard.{createAt,updatedAt,mergedAt,ticketInformation}` present |
| FE locale | `EDCAP_FE/public/locales/vi/locale.json` | i18n | FE | excerpt (targeted keys) | Same keys present, parallel offsets |
| FE locale | `EDCAP_FE/public/locales/ja/locale.json` | i18n | FE | excerpt (targeted keys) | Same keys present — corrects context.md's "known gap" note |
| Ticket docs | `docs/changes/SDD-LEAD-TIME/spec-pack.md` | ticket doc | PM | full | Phase 1 output, source of truth |
| Ticket docs | `docs/changes/SDD-LEAD-TIME/context.md` | ticket doc | PM | full | Screen/API/job map, allowed/forbidden patterns |
| Ticket docs | `docs/changes/SDD-LEAD-TIME/ticket-rules.md` | ticket doc | PM | full | Must/Must-not/Stop conditions |
| Templates | `docs/standards/templates/_ticket-template/impact-analysis.md` | template | Std | full | Structure for this ticket's `impact-analysis.md` |
| Templates | `docs/standards/templates/_ticket-template/impl-plan.md` | template | Std | full | Structure for this ticket's `impl-plan.md` |
| Templates | `docs/standards/templates/_ticket-template/source-availability.md` | template | Std | full | Structure for this file |
| Templates | `docs/standards/templates/_ticket-template/source-inventory.md` | template | Std | full | Structure for this file |
| Rules | `.claude/rules/00-safety.md`, `10-style.md`, `20-architecture.md`, `30-security.md`, `40-testing.md` | project rules | Platform | full | Layering, style, security, testing constraints applied throughout impl-plan |

## Important Files

- `EDCAP_BE/.../markdown/core/MarkdownParserCore.java` — do not modify the regex/parse logic; only consume its existing output.
- `EDCAP_BE/.../port/out/persistence/ArtifactScannerPersistencePort.java` — new method must be added here (application layer), implemented in the infrastructure JDBC adapter, per `.claude/rules/20-architecture.md`.
- `EDCAP_BE/src/main/resources/db/migration/V4__init_shema_v2.sql` — read-only reference; `ck_ticket_date_order` constraint must not be touched.
- `EDCAP_FE/src/lib/utils.ts` — `formatDateTime` must be reused, not duplicated.
- `EDCAP_BE/.../persistence/adapter/PmDashboardJdbcAdapter.java` — SQL duplicated in two places; both must change together.

## Generated / Excluded Files

- No generated/build artifacts are in scope. `EDCAP_FE/public/assets/library/echarts/echarts.min.js` (vendored third-party bundle, surfaced incidentally during codebase indexing) is unrelated to this ticket and excluded.

## Missing Files

- No dedicated `useTicketDetail.ts` hook file exists (confirmed in context.md) — the ticket-detail query is inlined in `PMDashboardPage.tsx`; not a missing file, just a pattern to be aware of when locating the FE fetch call.
- No existing ArchUnit test file path was read in full during this phase; its existence and rule set are assumed from `.claude/rules/20-architecture.md` and must be re-verified as a Phase 4 pre-check (see impl-plan §9 Open Issues).
