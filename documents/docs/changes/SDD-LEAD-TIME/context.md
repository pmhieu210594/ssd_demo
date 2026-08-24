# Context

**Ticket ID**: SDD-LEAD-TIME
**Create date**: 2026-08-21 15:30:00
**Author**: nvt_dung
**Update date**: 2026-08-21 15:30:00

## Screen / API / Batch / Related Job

| type | name | path / endpoint | current state | note |
|---|---|---|---|---|
| Screen | PM Dashboard — Ticket Detail drawer | `EDCAP_FE/src/pages/pm-dashboard/components/TicketDetailDrawer.tsx` (`TicketInformationCard`, lines 236-291) | Existing | Render target for the 3 new fields (created time, updated time, duration). |
| API | PM Dashboard ticket detail | `GET /api/v1/pm/dashboard/tickets/{ticketId}/detail` (`endpoints.pmDashboard.detail`, `EDCAP_FE/src/lib/api.ts:1928-1931`) | Existing, to extend | Backed by `PmDashboardJdbcAdapter.findDetail(UUID ticketId)` (lines 216-271); response DTO is `DashboardTicketDetail` wrapping `DashboardTicketRow`. No new endpoint is introduced — only the existing response payload is extended (per spec-pack §11). |
| Batch/Job | Artifact Scanner scan pass | `ArtifactScannerService.scan(...)` → `scanTicketDirectory(...)` (`EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/scanner/ArtifactScannerService.java`) | Existing, to extend | Owns the parse pass this ticket reuses; parses `spec-pack.md` (lines 390-401) and `report.md` (lines 435-446), then calls `persistSpecPackParse` (515-621) / `persistReportParse` (893-1027). No new job/schedule is introduced — values are written on the existing scan cadence (BR-SDD-LEAD-TIME-9). |

## Example of a correctly implemented code

| purpose | file/path | pattern to follow |
|---|---|---|
| Header-metadata extraction already exists and needs no parser rewrite | `EDCAP_BE/src/main/java/com/sdd/platform/domain/service/markdown/core/MarkdownParserCore.java:37,103-117` | `TOP_META_PATTERN` (`^\*\*([^*]+)\*\*:\s*(.+?)\s*$`) matches `**Create date**: ...` lines appearing before the first `## ` heading; `extractHeaderMetadata(content)` returns a normalized-key map (keys lowercased, spaces→underscore, so `**Create date**` → `create_date`, `**Update date**` → `update_date`). Reuse this map as-is; do not write a second regex. |
| `headerMetadata` already flows into `ParsedArtifact` | `EDCAP_BE/.../markdown/specpack/SpecPackMarkdownParser.java:106-115,604-633`; `EDCAP_BE/.../markdown/report/ReportMarkdownParser.java:61-70,441-460` | Both parsers already copy `document.headerMetadata()` into a local `LinkedHashMap` and carry it as the 8th field of their `ParsedArtifact` record. `parsed.headerMetadata().get("create_date")` / `get("update_date")` already work today with **zero parser change** to read the raw string; only the normalization (bare-date → `00:00:00`) and NULL-on-malformed logic needs to be added. |
| Conditional null-guarded field render in the Ticket Information card | `EDCAP_FE/src/pages/pm-dashboard/components/TicketDetailDrawer.tsx:279-286` (the `detail.row.mergedAt ? (...) : null` block for `mergedAt`) | Use this exact conditional pattern (render nothing / `-` fallback) for the new duration row when `startedAt`/`completedAt`/order-validity make the duration unavailable. For created/updated time rows use `createAt`'s pattern (`formatDateTime(detail.createdAt ?? detail.row.createdAt)`), which already reads from two possible sources. |
| Existing scan/persist-per-ticket-folder pattern to extend, not replace | `EDCAP_BE/.../ArtifactScannerService.java:280-453` (ticket resolution) + `515-621` / `893-1027` (persist steps) | `TicketScope ticket` is already resolved once per ticket folder (`ticket.ticketId()` available) and passed into both persist methods — this is the correct integration point for a new `persistence.updateTicketLeadTimeDates(...)`-style call; do not add a separate pipeline. |
| Existing ticket-detail SQL/DTO extension point | `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/PmDashboardJdbcAdapter.java:216-271` (`findDetail`), `399-437` (`baseTicketQuery`), `477-512` (`mapTicketRow`) | Both `findDetail`'s SQL and `baseTicketQuery()`'s SQL currently select `t.created_at AS ticket_created_at`, `t.updated_at AS updated_at_source`, etc. from `tbl_dim_ticket t` — add `t.started_at`, `t.completed_at` to both SQL strings and to `mapTicketRow` in the same way existing columns are mapped. |

## Allowed common components

| component | path | usage note |
|---|---|---|
| `Card` / `CardHeader` / `CardTitle` / `CardContent` (shadcn) | already imported in `TicketDetailDrawer.tsx` | Reuse the existing `TicketInformationCard` card; do not create a new card/section component for the 3 new fields. |
| `t(key, { defaultValue })` i18n hook | used throughout `TicketDetailDrawer.tsx` (e.g. lines 253, 262, 269, 280) | Follow the exact call shape already used by `createAt`/`updatedAt`/`mergedAt`. |
| `formatDateTime(iso: string \| null \| undefined): string` | `EDCAP_FE/src/lib/utils.ts` | Already returns `"-"` for falsy input and formats via dayjs/native `Date` fallback with locale-aware `LLL`/`YYYY-MM-DD HH:mm:ss`. Reuse for both the created-time and updated-time rows; do not write a new date formatter. |
| `MarkdownParserCore.extractHeaderMetadata()` / `ParsedArtifact.headerMetadata()` | `MarkdownParserCore.java`, `SpecPackMarkdownParser.java`, `ReportMarkdownParser.java` | Reuse as the sole source of the two raw date strings; do not add a second parsing mechanism. |
| `endpoints.pmDashboard.detail` + inline `useQuery` in `PMDashboardPage.tsx:93-97` | `EDCAP_FE/src/lib/api.ts:1928-1931` | Reuse the existing TanStack Query call; no new hook file needs to be created (there is no dedicated `useTicketDetail.ts` in this codebase — the query is inlined in the page component). |

## Forbidden common components

| component | reason |
|---|---|
| Any GitHub commit-history / per-file "first commit" / "last commit" API call | Explicitly rejected by the user (spec-pack §2.2, §17 A-SDD-LEAD-TIME-2); `GithubArtifactScannerSourceAdapter`'s `resolveCommitDate` only handles whole-revision dates and must not be extended for per-file history for this ticket. |
| New/duplicate date-formatting utility in FE | `formatDateTime` already exists in `EDCAP_FE/src/lib/utils.ts` and must be the single source of date-string formatting; do not add a second formatter. |
| Raw `fetch` calls in the FE for the ticket-detail response | Violates `20-architecture.md` — all API calls must go through `lib/api.ts` / TanStack Query. |
| New Redux slice for the duration/timestamps | Per `20-architecture.md`, Redux Toolkit is for global server-synced state (session, language) — the duration is a purely local, derived render value; compute it inline/in a small pure helper, not in Redux or Zustand. |

## List of methods that actually exist

| method/class | path | usage |
|---|---|---|
| `MarkdownParserCore.extractHeaderMetadata(String content)` (private) | `EDCAP_BE/.../markdown/core/MarkdownParserCore.java:103-117` | Called internally by `MarkdownParserCore.parse(...)`; result exposed via `MarkdownDocument.headerMetadata()`. |
| `MarkdownDocument.headerMetadata()` (record accessor) | `MarkdownParserCore.java:448-466` | 5th field of the `MarkdownDocument` record; `Map<String,String>`, unmodifiable. |
| `SpecPackMarkdownParser.parse(String content, String sourcePath, String parseMode)` | `EDCAP_BE/.../markdown/specpack/SpecPackMarkdownParser.java:106` | Builds `headerMetadata` at line 115 from `document.headerMetadata()`; returns `ParsedArtifact` (record, `headerMetadata` is the 8th field, lines 604-633). |
| `ReportMarkdownParser.parse(String content, String sourcePath, String parseMode)` | `EDCAP_BE/.../markdown/report/ReportMarkdownParser.java:61` | Same shape; `headerMetadata` built at line 70; `ParsedArtifact` record at 441-460. |
| `ArtifactScannerService.persistSpecPackParse(...)` | `EDCAP_BE/.../scanner/ArtifactScannerService.java:515-621` | Current persist step for spec-pack parses; does not yet touch `tbl_dim_ticket`. |
| `ArtifactScannerService.persistReportParse(...)` | `EDCAP_BE/.../scanner/ArtifactScannerService.java:893-1027` | Current persist step for report parses; does not yet touch `tbl_dim_ticket`. |
| `TicketScope.ticketId()` | resolved at `ArtifactScannerService.java:291-296` via `persistence.findTicketByProjectIdAndExternalKey(...)` / `persistence.upsertMinimalTicket(...)` | Already available inside `scanTicketDirectory` and passed into both persist methods — use this to target the `UPDATE` on `tbl_dim_ticket`. |
| `PmDashboardJdbcAdapter.findDetail(UUID ticketId)` | `EDCAP_BE/.../persistence/adapter/PmDashboardJdbcAdapter.java:216-271` | Existing ticket-detail query; needs `started_at`/`completed_at` added to its SQL (lines 218-255) and to the `DashboardTicketDetail` construction (259-270). |
| `PmDashboardJdbcAdapter.mapTicketRow(ResultSet rs, int rowNum)` | `PmDashboardJdbcAdapter.java:477-512` | Existing row mapper for `DashboardTicketRow`; needs 2 new column reads added. |
| `formatDateTime(iso)` | `EDCAP_FE/src/lib/utils.ts` | Existing FE date formatter; reuse for both new timestamp rows. |

## Forbidden methods / methods that do not exist

| method/API | reason | alternative |
|---|---|---|
| Any "get file commit history" method on `GithubArtifactScannerSourceAdapter` | Does not exist today (only `resolveCommitDate` for whole-revision dates) and is explicitly out of scope (A-SDD-LEAD-TIME-2) | Read the date from parsed file content only (`headerMetadata`). |
| Any existing "update ticket dates" persistence-port method (e.g. on `ArtifactScannerPersistencePort`) | Not found in the portion of `ArtifactScannerService.java` read (only `findTicketByProjectIdAndExternalKey` / `upsertMinimalTicket` are used) — **do not assume one exists; verify against the actual port interface before Phase 3 coding, and add a new port method if absent.** | Add a new port method, e.g. `updateTicketLeadTimeDates(UUID ticketId, OffsetDateTime startedAt, OffsetDateTime completedAt)`, implemented in the JDBC adapter behind the port. |
| Any new REST endpoint for lead-time / duration | Not requested; spec-pack §2.2 explicitly rules out any new manual/admin API | Extend the existing `GET /api/v1/pm/dashboard/tickets/{ticketId}/detail` response only. |
| A BE-computed/returned duration field on the DTO | Explicitly rejected — duration is FE-computed only (OI-SDD-LEAD-TIME-1, closed) | Compute duration client-side from `startedAt`/`completedAt` already on the DTO. |
| Repurposing `tbl_dim_ticket.closed_at` for "report updated" | Semantically distinct (ticket-closed vs. report-updated) and would conflict with the existing `ck_ticket_date_order` check constraint | Add the new `completed_at` column instead (spec-pack §12). |

## DTO / Entity / Table / Migration mapping

| layer | name | path | Note |
|---|---|---|---|
| DB table | `tbl_dim_ticket` | `EDCAP_BE/src/main/resources/db/migration/V4__init_shema_v2.sql:174-193` | Already has `started_at TIMESTAMPTZ` (nullable, line 184) and `closed_at`/`ck_ticket_date_order` (do not touch); no `completed_at` column yet. |
| DB migration (new) | `V513__add_completed_at_to_tbl_dim_ticket.sql` | `EDCAP_BE/src/main/resources/db/migration/` (next after `V512__add_ai_finding_stat_tracking.sql`, the current latest) | `ALTER TABLE tbl_dim_ticket ADD COLUMN completed_at TIMESTAMPTZ;` — nullable, no default, mirrors `started_at`; no backfill script (H-SDD-LEAD-TIME-3, closed). |
| BE application/domain record | `ParsedArtifact` (both `SpecPackMarkdownParser` and `ReportMarkdownParser` variants) | `SpecPackMarkdownParser.java:604-633`, `ReportMarkdownParser.java:441-460` | `headerMetadata` field (8th) already present; no new field needed to carry the raw date string — normalization happens at the call site or in a new shared utility. |
| BE application record | `DashboardTicketRow` | `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/pmdashboard/PmDashboardModels.java:117-150` (plus a secondary compact constructor at line 151) | Needs `startedAt` / `completedAt` fields added (both `OffsetDateTime`, nullable); update **both** constructor forms. |
| BE application record | `DashboardTicketDetail` | `PmDashboardModels.java:278-289` | Wraps `DashboardTicketRow row`; no change needed here if the two new fields live on `row`, per spec-pack §11 ("extend whatever DTO backs the FE's ticket-detail response with `started_at` and `completed_at` only"). |
| FE type | `PmDashboardTicketRow` | `EDCAP_FE/src/lib/api.ts:549-583` | Needs `startedAt: string | null` and `completedAt: string | null` added, mirroring existing `mergedAt: string | null` field style. |
| FE type | `PmDashboardTicketDetail` | `EDCAP_FE/src/lib/api.ts:585-597+` | No new top-level field required — duration is computed from `row.startedAt`/`row.completedAt` client-side. |

## formItemNm / SEQNO / Master Data / Code Value Mapping

Not applicable to this ticket — there is no form input, SEQNO, master-data table, or code-value mapping involved. The feature only surfaces two existing/new timestamp columns and a client-computed duration; no enum/master-data lookup is introduced.

## Multilingual Note

- New i18n keys are required under the existing `Pages.PmDashboard.*` namespace (e.g. candidate names `startedAt` / `completedAt` / `leadTimeDuration` — exact key names to be finalized in Phase 3 impl-plan) in **all three** locale files: `EDCAP_FE/public/locales/en/locale.json`, `EDCAP_FE/public/locales/vi/locale.json`, `EDCAP_FE/public/locales/ja/locale.json`.
- **Known gap**: `ja/locale.json` currently has **no** `createAt` / `mergedAt` / `ticketInformation` keys under `Pages.PmDashboard` (a grep for these exact keys returned no hits in that file, unlike `en`/`vi`). This ticket only needs to add its **own** 3 new keys to `ja` (do not silently also backfill the pre-existing `createAt`/`mergedAt`/`ticketInformation` gap — that's pre-existing scope creep; flag it in the impl-plan Open Issues instead).
- The duration display format (`"1d 8h"`-style) must be localized per the current i18n language (OI-SDD-LEAD-TIME-2, closed) — do not hardcode English unit abbreviations.

## Encoding / Mojibake Note

- `spec-pack.md` / `report.md` content is fetched as GitHub blob content (base64-decoded) by the existing `ArtifactScannerSourceAdapter` — no new fetch/decoding path is introduced; reuse the existing UTF-8 decode path.
- Header-metadata values (`**Create date**`, `**Update date**`) are expected to be plain ASCII date strings (`YYYY-MM-DD[ HH:mm:ss]`); no locale-specific character normalization is needed for the date value itself.

## Log / Audit / Operation Note

- No new scheduled job or background worker is introduced — values are written as part of the existing Artifact Scanner scan cadence (H-SDD-LEAD-TIME-3, closed).
- Malformed/missing date fields must **not** escalate to a full Artifact Scanner run failure for the ticket (BR-SDD-LEAD-TIME-6); log at most a warning-level message per ticket/file, consistent with how other missing-artifact cases are already handled in `ArtifactScannerService`.
- No new audit-log entry is required — this is a read-only display feature on an already-authenticated, session-based dashboard view (spec-pack §13/§14).
- Operationally, a previously-visible date/duration can disappear from the dashboard after an unrelated edit to the source file nulls out the field — this is accepted, confirmed behavior (H-SDD-LEAD-TIME-4), not a regression to guard against.

## Ticket-Specific Constraints

- Read/persist mechanism must reuse the existing Artifact Scanner scan/parse/persist pipeline exactly — no separate pipeline, no dedicated backfill job, no new manual/admin API (BR-SDD-LEAD-TIME-9; spec-pack §2.2).
- Date normalization (`YYYY-MM-DD` → `YYYY-MM-DD 00:00:00`, full-datetime passthrough, malformed → `NULL`) must be implemented **once** in a shared utility reused by both `SpecPackMarkdownParser` and `ReportMarkdownParser` (spec-pack §6.6 Maintainability) — do not duplicate parsing logic.
- A malformed/missing date field on a later scan must **null out** the corresponding column, not preserve a previously-valid value (BR-SDD-LEAD-TIME-6, H-SDD-LEAD-TIME-4, closed).
- Duration is computed **client-side only**, never persisted as a DB column and never computed/returned by the BE (OI-SDD-LEAD-TIME-1, closed); it must show `-` when either timestamp is null or when `completed_at < started_at` (OI-SDD-LEAD-TIME-3, closed).
- Existing `PmDashboardJdbcAdapter` behavior that already references `started_at` (e.g. any existing ordering) must keep working unchanged (spec-pack §2.2, §6.6 Compatibility) — verify with a regression test, not just visual inspection.
