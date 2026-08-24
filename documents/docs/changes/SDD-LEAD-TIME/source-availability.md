# Source Availability

**Ticket ID**: SDD-LEAD-TIME
**Create date**: 2026-08-21 16:00:00
**Author**: nvt_dung
**Update date**: 2026-08-21 16:00:00

| source | path | read_status | trust_level | owner | purpose | risk | action |
|---|---|---|---|---|---|---|---|
| Spec Pack | `docs/changes/SDD-LEAD-TIME/spec-pack.md` | read (full) | high | PM | Source of truth for AC/BR/scope | Low | Use as sole authority for scope |
| Context | `docs/changes/SDD-LEAD-TIME/context.md` | read (full) | high | PM | Screen/API/job map, allowed/forbidden patterns, DTO mapping | Medium | One item (ja locale gap) contradicted by live read — see Risk section |
| Ticket Rules | `docs/changes/SDD-LEAD-TIME/ticket-rules.md` | read (full) | high | PM | Must/Must-not/Stop conditions | Low | Use as constraint checklist |
| `MarkdownParserCore.java` | `EDCAP_BE/src/main/java/com/sdd/platform/domain/service/markdown/core/MarkdownParserCore.java` | read (full) | high | BE | Confirms `extractHeaderMetadata()`/`TOP_META_PATTERN` behavior | Low | Reuse as-is, no change |
| `SpecPackMarkdownParser.java` | `EDCAP_BE/src/main/java/com/sdd/platform/domain/service/markdown/specpack/SpecPackMarkdownParser.java` | read (full) | high | BE | Confirms `headerMetadata` already on `ParsedArtifact` (8th field), unused downstream | Low | No parser rewrite needed |
| `ReportMarkdownParser.java` | `EDCAP_BE/src/main/java/com/sdd/platform/domain/service/markdown/report/ReportMarkdownParser.java` | read (full) | high | BE | Same as above, report-side | Low | No parser rewrite needed |
| `ArtifactScannerService.java` | `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/scanner/ArtifactScannerService.java` | read (excerpt: persistSpecPackParse 515-621, persistReportParse 893-1027, ticket resolution 280-453) | high | BE | Confirms scan/parse/persist integration points; confirms no existing ticket-date write | Low | Extend both persist methods |
| `ArtifactScannerPersistencePort.java` | `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/persistence/ArtifactScannerPersistencePort.java` | read (full, 32 methods) | high | BE | Confirms no existing method updates `started_at`/`completed_at` | Low | Add new port method |
| `V4__init_shema_v2.sql` | `EDCAP_BE/src/main/resources/db/migration/V4__init_shema_v2.sql` (lines 174-193) | read (excerpt) | high | BE/DBA | Confirms `tbl_dim_ticket` schema, `started_at` column, `ck_ticket_date_order` scope (only `closed_at`/`created_at`) | Low | New column additive, no constraint conflict |
| Migration directory listing | `EDCAP_BE/src/main/resources/db/migration/` | read (listing) | high | BE/DBA | Confirms latest version is `V512__add_ai_finding_stat_tracking.sql` | Low | New file is `V513__add_completed_at_to_tbl_dim_ticket.sql` |
| `PmDashboardJdbcAdapter.java` | `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/PmDashboardJdbcAdapter.java` | read (excerpt: findDetail 216-271, baseTicketQuery 399-437, mapTicketRow 477-512) | high | BE | Confirms duplicated SQL column list in two places | Medium | Must update both places identically |
| `PmDashboardModels.java` | `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/pmdashboard/PmDashboardModels.java` | read (excerpt: `DashboardTicketRow` 117-216, `DashboardTicketDetail` 278-289) | high | BE | Confirms canonical + compact constructor both need new fields | Medium | Compact constructor default must not silently drop new fields |
| `TicketDetailDrawer.tsx` | `EDCAP_FE/src/pages/pm-dashboard/components/TicketDetailDrawer.tsx` (lines 236-291) | read (excerpt) | high | FE | Confirms `TicketInformationCard` render pattern (`mergedAt` conditional block) | Low | Copy exact pattern for 3 new rows |
| `api.ts` | `EDCAP_FE/src/lib/api.ts` (lines 549-597, 1842-1931) | read (excerpt) | high | FE | Confirms `PmDashboardTicketRow`/`PmDashboardTicketDetail` types and `endpoints.pmDashboard.detail` | Low | Additive fields only |
| `utils.ts` | `EDCAP_FE/src/lib/utils.ts` (lines 11-31+) | read (excerpt) | high | FE | Confirms `formatDateTime()` already handles null → `-` | Low | Reuse, no new formatter |
| Locale files | `EDCAP_FE/public/locales/{en,vi,ja}/locale.json` (`Pages.PmDashboard.*`) | read (excerpt, targeted keys) | high | FE | Confirms `createAt`/`updatedAt`/`mergedAt`/`ticketInformation` exist in **all three** locales | Low | Only add 3 net-new keys to all three files |
| Ticket templates | `docs/standards/templates/_ticket-template/{impact-analysis,impl-plan,source-availability,source-inventory}.md` | read (full) | high | PM | Defines the exact structure this Phase 3 output must follow | Low | Used verbatim as document skeletons |
| `docs/architecture/*` | `docs/architecture/` (listing only) | read (listing) | medium | Arch | Confirms directory contents; not read in full — spec-pack/context.md already cover the relevant flow in enough detail | Low | No conflicting architecture doc found during this phase |
| `docs/standards/*` (coding, backend, database, testing, security, api-contract) | `docs/standards/` (listing only) | read (listing) | medium | Std | Confirms standards exist; detailed rules already summarized in `.claude/rules/*` | Low | Rely on `.claude/rules/*` as the operative summary |
| `.claude/rules/*` | `.claude/rules/{00-safety,10-style,20-architecture,30-security,40-testing}.md` | read (full) | high | Platform | Hexagonal layering, style, security, testing constraints | Low | Directly enforced in impl-plan steps |

## Summary

All sources needed to plan SDD-LEAD-TIME Phase 3 were read directly (not inferred). The codebase
facts fully corroborate spec-pack.md's stated As-Is/To-Be, with one correction: `ArtifactScannerPersistencePort`
has zero existing ticket-date-update method (context.md/ticket-rules.md already anticipated this
as "verify before Phase 3" — now confirmed absent, so a new port method is required, not optional).

## Unavailable / Partial Sources

- Full content of `EDCAP_FE/src/lib/api.ts`'s `PmDashboardTicketDetail` interface beyond line ~597 was not read in full (tail fields `scoreBreakdown`/`traceabilityUrl` not needed for this ticket's scope).
- Full `docs/architecture/*` and `docs/standards/*` documents were only listed, not read in full — not required since `.claude/rules/*` already summarizes the operative constraints and spec-pack/context.md already cite the exact integration points.
- Historical `spec-pack.md`/`report.md` files across all existing tickets were not scanned for header-field format variance (already accepted as out of scope per spec-pack §9 — Phase 1 decision, not a Phase 3 gap).

## Risk Before Implementation

- **Locale discrepancy (resolved)**: `context.md` states `ja/locale.json` is missing `createAt`/`mergedAt`/`ticketInformation` under `Pages.PmDashboard`. A direct read of `ja/locale.json` during Phase 3 exploration shows all three keys **already present** at line offsets parallel to `en`/`vi`. This is documented as a correction in `impact-analysis.md` §14; per user decision (2026-08-21), no ja backfill is performed — only the 3 new lead-time keys are added to all three locale files, matching ticket-rules.md's original instruction either way.
- Medium risk: `PmDashboardJdbcAdapter`'s ticket-detail SQL is duplicated between `findDetail` and `baseTicketQuery()` — a missed update to one of the two would cause the ticket-detail endpoint and the ticket-list endpoint to disagree on `started_at`/`completed_at` presence. Mitigated by an explicit implementation step + regression test (impl-plan §5, §8.3).

## Required Human Decision

None outstanding — all Human Decisions (spec-pack §16) and Open Issues (spec-pack §18) are closed. The one Phase-3-local ambiguity (ja locale gap) was resolved with the user during this phase (see Risk section above); no further decision is pending before implementation.
