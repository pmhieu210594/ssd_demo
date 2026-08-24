# Spec Pack

**Ticket ID**: SDD-LEAD-TIME
**Create date**: 2026-08-21 13:10:56
**Author**: nvt_dung
**Update date**: 2026-08-21 13:44:23

## 1. Context / Purpose

The `pm-dashboard` screen shows a Ticket Detail drawer (`TicketDetailDrawer.tsx`) summarizing each ticket's evidence and status. Project managers currently must open the repository directly to see when a ticket's `spec-pack.md` was created and when its `report.md` was last updated — there is no in-app visibility into these two evidence-file timestamps.

This ticket adds three pieces of visibility to the existing Ticket Information card:

- The **created time** of `spec-pack.md` (read from its `**Create date**` header field).
- The **updated time** of `report.md` (read from its `**Update date**` header field).
- A **computed duration** between `started_at` and `completed_at`, displayed alongside the two timestamps.

All values are produced as a side effect of the existing Artifact Scanner content-parse pass (`SpecPackMarkdownParser` / `ReportMarkdownParser`) and are persisted to `tbl_dim_ticket.started_at` / `tbl_dim_ticket.completed_at` respectively, then surfaced through the existing PM Dashboard ticket-detail read path. The duration is derived from those two columns, not separately parsed or stored.

This `spec-pack.md` is the single source of truth for Phase 1 output of the `SDD-LEAD-TIME` ticket. **All Human Decisions (§16) are now closed** — the ticket does compute and display a duration (confirmed 2026-08-21), which is narrower than a full DORA lead-time metric but aligns the feature with its name.

## 2. Scope

### 2.1. Within range

- Extract a "created time" value from `spec-pack.md`'s own content, specifically the header field labeled `**Create date**`, during the existing Artifact Scanner parse pass.
- Extract an "updated time" value from `report.md`'s own content, specifically the header field labeled `**Update date**`, during the same parse pass. No new label is added to the template — the existing `Update date` field is reused as-is.
- Persist the `spec-pack.md` created-time value into the existing `tbl_dim_ticket.started_at` column.
- Persist the `report.md` updated-time value into a new `tbl_dim_ticket.completed_at` column (Flyway migration, modeled after `started_at`).
- Normalize parsed date values so both `YYYY-MM-DD HH:mm:ss` and bare `YYYY-MM-DD` inputs are accepted; a bare date is stored/displayed as `YYYY-MM-DD 00:00:00`.
- Read and persist both values using the same scan/parse mechanism the system already uses for other artifact metadata (`ArtifactScannerService` + the two markdown parsers) — no separate/special-case pipeline, no dedicated backfill job.
- Compute a **duration** between `started_at` and `completed_at` and display it together with the two timestamps in `TicketInformationCard`. The duration is derived at read/display time from the two persisted columns, not independently parsed or stored as its own column.
- Display all three values (created time, updated time, computed duration) in `TicketInformationCard` (inside `TicketDetailDrawer.tsx`); the two timestamps formatted as `YYYY-MM-DD HH:mm:ss`.
- Apply the existing i18n pattern (`Pages.PmDashboard.<key>`) used by sibling fields (`createAt`, `updatedAt`, `mergedAt`) for the new labels.
- Show a default fallback value `-` for a timestamp when the source file does not exist, has not been scanned yet, or its date field is missing/unparseable; show `-` for the duration when either `started_at` or `completed_at` is null.
- Extend the BE contract (ticket-detail DTO/query) so the FE can receive `started_at`, `completed_at`, and (per Phase 3 detail) either the computed duration or enough data for the FE to compute it consistently with formatting used elsewhere.

### 2.2. Out of range

- Any GitHub commit-history API call (per-file first/last commit lookup) — explicitly rejected in favor of reading dates from file content (see §17, A-SDD-LEAD-TIME-2).
- Changes to any artifact parser other than `SpecPackMarkdownParser` and `ReportMarkdownParser`.
- Adding a new/dedicated "updated" header field to the `report.md` template — confirmed not needed; the existing `Update date` field is reused (H-SDD-LEAD-TIME-2, closed).
- A separate backfill job/migration script for historical tickets — confirmed out of scope; values populate through the normal scan cadence like any other artifact-derived field (H-SDD-LEAD-TIME-3, closed).
- Preserving a previously-valid timestamp when a later scan finds the date field missing/malformed — confirmed behavior is to null it out, not preserve the last known value (H-SDD-LEAD-TIME-4, closed).
- Changing the meaning or behavior of `tbl_dim_ticket.started_at` for any consumer other than this display (e.g. any existing query already ordering by `started_at` in `PmDashboardJdbcAdapter` must keep working).
- Any new manual/admin API endpoint. Values are populated purely as a byproduct of the existing scan flow.
- Any broader DORA-style lead-time metric beyond the single per-ticket `completed_at - started_at` duration described above (e.g. team/project aggregates, trend charts) — not requested by the ticket.

## 3. Terminology

| terms | meaning | notes |
|---|---|---|
| Artifact Scanner | Existing MVP function that scans `docs/changes/<TICKET>/` evidence files and parses their content. | Owns the parse pass this ticket reuses. |
| Header metadata | Top-of-file `**Key**: Value` lines appearing before the first `##` heading in a ticket markdown file. | Parsed by `MarkdownParserCore.extractHeaderMetadata()` via `TOP_META_PATTERN`. |
| `spec-pack.md` created time | The date/time value found in `spec-pack.md`'s `**Create date**` header field. | Confirmed label (H-SDD-LEAD-TIME-1, closed). Source for `tbl_dim_ticket.started_at`. |
| `report.md` updated time | The date/time value found in `report.md`'s `**Update date**` header field. | Confirmed label, no new field needed (H-SDD-LEAD-TIME-2, closed). Source for `tbl_dim_ticket.completed_at`. |
| `tbl_dim_ticket.started_at` | Existing nullable `TIMESTAMPTZ` column on the ticket dimension table. | Already read by `PmDashboardJdbcAdapter`. |
| `tbl_dim_ticket.completed_at` | New nullable `TIMESTAMPTZ` column to be added via Flyway migration `V513`. | Modeled directly on `started_at`. |
| Duration | The computed interval between `started_at` and `completed_at`, shown alongside them in the UI. | Confirmed in scope (H-SDD-LEAD-TIME-5, closed). Not persisted as its own column. |
| Ticket Detail drawer | FE component `TicketDetailDrawer.tsx` on `pm-dashboard` showing per-ticket details. | Contains `TicketInformationCard`, the render target for this ticket. |
| `-` fallback | Literal dash character shown in place of a date/duration when the value is unavailable. | Per ticket's explicit exception-handling requirement. |

## 4. As-Is

- `tbl_dim_ticket` already has `started_at TIMESTAMPTZ` (nullable) and `updated_at TIMESTAMPTZ`, but no `completed_at` column (`V4__init_shema_v2.sql:174-193`).
- `PmDashboardJdbcAdapter`'s ticket-detail query already selects/orders by `started_at`, but no field currently surfaces a "report updated" timestamp.
- `SpecPackMarkdownParser` and `ReportMarkdownParser` parse `spec-pack.md`/`report.md` content (sections, tables, front matter, header metadata) but do not currently extract or expose a dedicated created/updated date field on `ParsedArtifact`.
- `MarkdownParserCore.extractHeaderMetadata()` already parses top-of-file `**Key**: Value` lines into a normalized-key map — this capability exists today but its output is not wired into a created/updated date concept anywhere.
- `TicketDetailDrawer.tsx`'s `TicketInformationCard` renders `ticketId`, `repository`, `project`, `ownerDisplay`, `createAt`, `updatedAt`, and conditionally `mergedAt`, using `t("Pages.PmDashboard.<key>", { defaultValue })` + `formatDateTime(...)`. It has no fields for spec-pack created time, report updated time, or a computed duration.
- No component today computes or displays an interval derived from `started_at`/`completed_at`.

## 5. To-Be

- `SpecPackMarkdownParser` exposes a normalized created-time value read from `spec-pack.md`'s `**Create date**` header field.
- `ReportMarkdownParser` exposes a normalized updated-time value read from `report.md`'s `**Update date**` header field.
- `ArtifactScannerService`'s existing persist step for spec-pack/report parses also writes the corresponding value into `tbl_dim_ticket.started_at` / `tbl_dim_ticket.completed_at` for the ticket being scanned — the same scan/parse/persist mechanism already used for other artifact-derived data, no new pipeline.
- `tbl_dim_ticket.completed_at TIMESTAMPTZ` exists (new migration), nullable, no default, mirroring `started_at`.
- `PmDashboardJdbcAdapter`'s ticket-detail query and its result DTO include both `started_at` and `completed_at`.
- `TicketInformationCard` renders three new pieces of information — spec-pack created time, report updated time, and the computed duration between them — timestamps formatted `YYYY-MM-DD HH:mm:ss`, all i18n-labeled, falling back to `-` when the underlying value(s) are null.
- If either `started_at` or `completed_at` is null on a later scan (e.g. date field became malformed), the corresponding column is set back to `NULL` rather than retaining its previous value, and the duration falls back to `-`.

Target flow:

```text
spec-pack.md (header: Create date)  --Artifact Scanner parse-->  tbl_dim_ticket.started_at   --\
                                                                                                  >-- Ticket Detail query --> TicketInformationCard (created / updated / duration)
report.md (header: Update date)     --Artifact Scanner parse-->  tbl_dim_ticket.completed_at --/
```

## 6. Detailed specification

### 6.1. Business Rules

| ID | rule | source / note |
|---|---|---|
| BR-SDD-LEAD-TIME-1 | The "created time" of `spec-pack.md` is read from its `**Create date**` header field during the existing Artifact Scanner parse pass, not from Git commit history or filesystem metadata. | User decision, confirmed 2026-08-21 (H-1). |
| BR-SDD-LEAD-TIME-2 | The "updated time" of `report.md` is read from its `**Update date**` header field during the same parse pass; no new template field is introduced. | User decision, confirmed 2026-08-21 (H-2). |
| BR-SDD-LEAD-TIME-3 | Parsed date values are accepted in `YYYY-MM-DD HH:mm:ss` or `YYYY-MM-DD` format; a bare date is normalized to `YYYY-MM-DD 00:00:00`. | User decision, confirmed 2026-08-21. |
| BR-SDD-LEAD-TIME-4 | `spec-pack.md`'s created time is persisted into `tbl_dim_ticket.started_at`. | Ticket requirement. |
| BR-SDD-LEAD-TIME-5 | `report.md`'s updated time is persisted into `tbl_dim_ticket.completed_at`, a new column added via Flyway migration modeled on `started_at`. | Ticket requirement. |
| BR-SDD-LEAD-TIME-6 | If either source file does not exist, has not been scanned, or its date field cannot be parsed, the corresponding DB column is set to `NULL` and the UI shows `-`; a previously-valid value is not preserved. | User decision, confirmed 2026-08-21 (H-4). |
| BR-SDD-LEAD-TIME-7 | The created time, updated time, and computed duration are displayed inside `TicketInformationCard` in `TicketDetailDrawer.tsx`, following the existing i18n key convention `Pages.PmDashboard.<key>`. | Ticket requirement. |
| BR-SDD-LEAD-TIME-8 | A duration is computed from `completed_at - started_at` and displayed alongside the two timestamps; it is derived at read/display time and is not persisted as its own column. | User decision, confirmed 2026-08-21 (H-5). |
| BR-SDD-LEAD-TIME-9 | Values are (re)written each time Artifact Scanner scans the ticket's evidence folder, using the same scan/parse mechanism as other artifact-derived fields; there is no separate on-demand recompute API and no dedicated backfill job. | User decision, confirmed 2026-08-21 (H-3). |
| BR-SDD-LEAD-TIME-10 | The duration shows `-` whenever either `started_at` or `completed_at` is null. | Derived from BR-6 and BR-8. |

### 6.2. Input

| item | type | required | validation | notes |
|---|---|---|---|---|
| `spec-pack.md` file content (from GitHub blob via existing scan) | Markdown text | Implicit (file may be absent) | Must contain a parseable `**Create date**` header value to populate `started_at`; otherwise treated as missing. | Fetched by existing `ArtifactScannerSourcePort`, no new fetch mechanism. |
| `report.md` file content (from GitHub blob via existing scan) | Markdown text | Implicit (file may be absent) | Must contain a parseable `**Update date**` header value to populate `completed_at`; otherwise treated as missing. | Same fetch mechanism as above. |
| `ticketId` (scan context) | String | Yes | Must resolve to an existing `tbl_dim_ticket` row (Artifact Scanner ticket-creation flow already guarantees this). | Used to target the `UPDATE` on `tbl_dim_ticket`. |

### 6.3. Output

| item | type | format | notes |
|---|---|---|---|
| `tbl_dim_ticket.started_at` | TIMESTAMPTZ, nullable | ISO-8601 in storage | Existing column, now populated from `spec-pack.md` content. |
| `tbl_dim_ticket.completed_at` | TIMESTAMPTZ, nullable | ISO-8601 in storage | New column, populated from `report.md` content. |
| Ticket-detail API response field for spec-pack created time | String/date field on DTO | `YYYY-MM-DD HH:mm:ss` or null | Consumed by `TicketDetailDrawer.tsx`. |
| Ticket-detail API response field for report updated time | String/date field on DTO | `YYYY-MM-DD HH:mm:ss` or null | Consumed by `TicketDetailDrawer.tsx`. |
| `TicketInformationCard` UI row: spec-pack created time | Rendered text | `YYYY-MM-DD HH:mm:ss` or `-` | i18n label + `formatDateTime`. |
| `TicketInformationCard` UI row: report updated time | Rendered text | `YYYY-MM-DD HH:mm:ss` or `-` | i18n label + `formatDateTime`. |
| `TicketInformationCard` UI row: duration | Rendered text | `"1d 8h"`-style, localized via i18n, or `-` | Computed client-side (FE) from `started_at`/`completed_at`; not returned by BE (OI-1). `-` when either input is null or `completed_at < started_at` (OI-3). |

### 6.4. Error / Exception

| case | expected behavior | message/code | notes |
|---|---|---|---|
| `spec-pack.md` does not exist for the ticket | `started_at` remains/becomes `NULL`; UI shows `-`. | No error surfaced to user. | Matches ticket's explicit exception-handling requirement. |
| `report.md` does not exist for the ticket | `completed_at` remains/becomes `NULL`; UI shows `-`. | No error surfaced to user. | Same as above. |
| File exists but the expected date field/header-metadata line is missing or malformed | Treated the same as "file does not exist" for that value: column stays `NULL`, UI shows `-`. | No fatal error; scan continues. | Must not fail the whole Artifact Scanner run for one ticket's date field. |
| File exists with a bare `YYYY-MM-DD` value (no time-of-day) | Normalize to `YYYY-MM-DD 00:00:00` and persist/display normally. | N/A | Confirmed user decision. |
| Ticket-detail API called before any scan has run for the ticket | Both fields return null; UI shows `-` for all three (created, updated, duration). | N/A | Consistent with other nullable ticket-detail fields today. |
| `started_at` or `completed_at` is null when computing the duration | Duration shows `-`. | N/A | BR-SDD-LEAD-TIME-10. |

### 6.5. Boundary Value

| item | min | max | special cases | expected |
|---|---|---|---|---|
| Date value length/format | `YYYY-MM-DD` (10 chars) | `YYYY-MM-DD HH:mm:ss` (19 chars) | Empty string, whitespace-only, non-date text in the field | Treated as unparseable → `NULL`/`-`. |
| Number of files present for a ticket | 0 (neither file) | 2 (both files) | Only one of the two files present | Only the corresponding column/UI field is populated; the other stays `-`. |
| Re-scan of an already-scanned ticket | N/A | N/A | Date field value changes between scans (e.g. report.md content edited) | Latest scan's value overwrites the previously stored column value (BR-SDD-LEAD-TIME-9). |
| Re-scan with date field removed from a previously-populated file | N/A | N/A | Value regresses from a valid date to missing | Column is cleared to `NULL` (H-SDD-LEAD-TIME-4, closed); duration falls back to `-`. |
| `completed_at` earlier than `started_at` | N/A | N/A | `report.md`'s `Update date` predates `spec-pack.md`'s `Create date` (e.g. authoring out of order) | Duration displays `-` (OI-SDD-LEAD-TIME-3, closed); both raw timestamps are still displayed normally. |

### 6.6. Non-functional

| item | requirement | target / threshold | verification | notes |
|---|---|---|---|---|
| Correctness | Displayed value must exactly reflect the corresponding file's own date field, normalized per BR-3. | 100% match for well-formed input. | Unit test on parser + DB integration test. | No timezone conversion beyond existing `TIMESTAMPTZ`/`formatDateTime` conventions. |
| Robustness | Missing/malformed date field must never fail the Artifact Scanner run or the ticket-detail API call. | 0 run failures attributable to this feature. | Unit + integration test with malformed fixtures. | Matches ticket's explicit exception-handling requirement. |
| i18n | All three new labels (created time, updated time, duration) must be translatable via the existing i18n system, not hardcoded strings. | 100% of new user-facing labels use `t(...)`. | Code review. | Follow `Pages.PmDashboard.<key>` convention already used by `createAt`/`updatedAt`/`mergedAt`. |
| Compatibility | Existing consumers of `tbl_dim_ticket.started_at` (e.g. `PmDashboardJdbcAdapter` ordering) must continue to work unchanged. | No regression in existing ordering/queries. | Regression test. | `started_at`'s value now originates from `spec-pack.md` content instead of being unset; must confirm this doesn't change existing sort/filter results in a way considered a break. |
| Maintainability | Date-parsing/normalization logic (BR-3) should be implemented once and reused for both parsers, not duplicated. | Single shared utility. | Code review. | Avoid copy-pasted date-parsing logic across `SpecPackMarkdownParser`/`ReportMarkdownParser`. |

## 7. Acceptance Criteria

| ACID | description | testable? | notes |
|---|---|---|---|
| AC-SDD-LEAD-TIME-1 | When `spec-pack.md` exists with a well-formed `**Create date**` header field, `tbl_dim_ticket.started_at` is populated with that value after a scan. | Yes | DB integration test. |
| AC-SDD-LEAD-TIME-2 | When `report.md` exists with a well-formed `**Update date**` header field, `tbl_dim_ticket.completed_at` is populated with that value after a scan. | Yes | DB integration test. |
| AC-SDD-LEAD-TIME-3 | When `spec-pack.md` does not exist for a ticket, `started_at` stays `NULL` and the UI shows `-` for that field. | Yes | Integration + UI test. |
| AC-SDD-LEAD-TIME-4 | When `report.md` does not exist for a ticket, `completed_at` stays `NULL` and the UI shows `-` for that field. | Yes | Integration + UI test. |
| AC-SDD-LEAD-TIME-5 | A bare `YYYY-MM-DD` value in either file is displayed as `YYYY-MM-DD 00:00:00`. | Yes | Unit test on normalization logic. |
| AC-SDD-LEAD-TIME-6 | A malformed or unparseable date field sets the corresponding column to `NULL` (does not preserve a prior value) and does not fail the Artifact Scanner run for that ticket; UI shows `-`. | Yes | Unit + integration test with malformed fixture. |
| AC-SDD-LEAD-TIME-7 | `TicketInformationCard` displays created time, updated time, and duration with i18n-translated labels (not hardcoded text). | Yes | UI/i18n review + snapshot test. |
| AC-SDD-LEAD-TIME-8 | Re-scanning a ticket after its `report.md` content date field changes updates `completed_at` to the new value. | Yes | Integration test (two scans, differing content). |
| AC-SDD-LEAD-TIME-9 | A duration is computed from `completed_at - started_at` and displayed alongside the two timestamps in `TicketInformationCard`. | Yes | UI + unit test on duration computation. |
| AC-SDD-LEAD-TIME-10 | Existing `PmDashboardJdbcAdapter` queries and behavior that already reference `started_at` continue to function without regression. | Yes | Regression test. |
| AC-SDD-LEAD-TIME-11 | When either `started_at` or `completed_at` is `NULL`, or when `completed_at < started_at`, the FE-computed duration displays `-` instead of a computed value. | Yes | FE unit test on duration computation (null-safety and ordering). |
| AC-SDD-LEAD-TIME-12 | Historical tickets whose `spec-pack.md`/`report.md` were authored before this change populate `started_at`/`completed_at` the next time they are scanned by the existing Artifact Scanner cadence, with no separate backfill job required. | Yes | Integration test: re-scan a pre-existing ticket folder and confirm columns populate. |

## 8. Examples

### 8.1. Normal Case

#### Example 1: Both files present and well-formed

```text
spec-pack.md header: "**Create date**: 2026-08-21 09:30:00"
report.md header: "**Update date**: 2026-08-22 17:45:10"
```

Expected:

- `tbl_dim_ticket.started_at` = `2026-08-21 09:30:00`.
- `tbl_dim_ticket.completed_at` = `2026-08-22 17:45:10`.
- `TicketInformationCard` shows both values formatted `YYYY-MM-DD HH:mm:ss`, correctly labeled and translated, plus an FE-computed duration displayed as `1d 8h` (localized per the current i18n language).

#### Example 2: Date-only value normalized

```text
spec-pack.md header: "**Create date**: 2026-08-21"
```

Expected:

- `tbl_dim_ticket.started_at` = `2026-08-21 00:00:00`.
- UI shows `2026-08-21 00:00:00`.

### 8.2. Error Case

#### Example 1: report.md does not exist

Input:

```text
docs/changes/<TICKET>/report.md is absent
```

Expected:

- `tbl_dim_ticket.completed_at` remains `NULL`.
- `TicketInformationCard` shows `-` for the report-updated-time field and `-` for the duration.
- Artifact Scanner run for the ticket still completes successfully for other artifacts.

#### Example 2: spec-pack.md exists but the date field is malformed

Input:

```text
spec-pack.md header: "**Create date**: TBD"
```

Expected:

- `tbl_dim_ticket.started_at` is set to `NULL` (a previous valid value, if any, is not kept — confirmed behavior per H-SDD-LEAD-TIME-4).
- UI shows `-` for created time and `-` for duration.
- No exception propagates out of the scan.

### 8.3. Boundary Case

#### Example 1: Only one of the two files exists

Input:

```text
docs/changes/<TICKET>/spec-pack.md present with valid date
docs/changes/<TICKET>/report.md absent
```

Expected:

- `started_at` populated, `completed_at` stays `NULL`.
- UI shows a real date for spec-pack created time, `-` for report updated time, and `-` for duration.

#### Example 2: Re-scan changes report.md's date value

Input:

```text
Scan 1: report.md field = 2026-08-20 10:00:00
Scan 2 (after report.md edited): report.md field = 2026-08-25 08:00:00
```

Expected:

- After scan 2, `completed_at` = `2026-08-25 08:00:00` (overwritten, not appended/history-tracked); duration recalculates against the new value on next render.

## 9. Source Availability Summary

| source | status | trust level | summary |
|---|---|---|---|
| Ticket raw input | available/read | high | Defines scope, DB target columns, UI location, i18n requirement, and `-` fallback rule. |
| Ticket templates (`spec-pack.md`, `report.md`) | available/read | high | Confirms both carry `**Create date**` / `**Update date**` header fields (see also `GIT-PR-METADATA-COLLECTOR/spec-pack.md` and this ticket's own `01_raw-input.md`, which use the same English labels). |
| Markdown parser core/spec-pack/report parser source | available/read (core full, parsers excerpt) | high | Confirms `extractHeaderMetadata()` mechanism (`TOP_META_PATTERN` matches `**Key**: Value` lines) and current `ParsedArtifact` shape (no date field yet). |
| Artifact Scanner service source | available/read (excerpt: constants + persist call sites) | high | Confirms scan/parse/persist flow and the natural integration point for writing `started_at`/`completed_at`. |
| DB migration source (`V4__init_shema_v2.sql`, migration directory) | available/read | high | Confirms `tbl_dim_ticket` schema and next available migration version (`V513`). |
| PM Dashboard read-side adapter (`PmDashboardJdbcAdapter`) | available/read (excerpt) | high | Confirms `started_at` is already selected/used; full ticket-detail query/DTO not read in full. |
| Frontend `TicketDetailDrawer.tsx` | available/read (excerpt: `TicketInformationCard`) | high | Confirms exact rendering/i18n pattern to replicate. |
| GitHub adapter source (ruled-out approach) | available/read | high | Confirms no per-file commit-history capability exists today; not needed since content-based approach was chosen and confirmed. |
| Historical `spec-pack.md`/`report.md` files across existing tickets | not verified in Phase 1 | low | Not a blocker — confirmed (H-3) that values populate via the normal scan cadence with no dedicated backfill; exact current field values across all tickets remain unverified but are not required for Phase 1. |

## 10. Complexity Classification

```text
- Complexity: Medium
- System shape: BE (parser + scan/persist) + DB (1 column migration) + FE (3 display fields, i18n)
- Primary risk: Contract (DTO extension) / Correctness (duration computation, null-safety)
- Review mode: Standard
- Required options: BE Contract / DB Migration / i18n Review
```

Reason:

- No new external integration or webhook; reuses the existing Artifact Scanner scan/parse/persist pipeline end to end.
- All Human Decisions are closed, removing the earlier source-ambiguity risk; remaining complexity is ordinary additive BE+DB+FE work plus one small derived-value calculation (duration) that must be null-safe.

## 11. FE/BE Contract Impact

- BE impact required: extend `SpecPackMarkdownParser`/`ReportMarkdownParser` output to expose the `**Create date**`/`**Update date**` header values, extend `ArtifactScannerService` persist step, extend `PmDashboardJdbcAdapter`'s ticket-detail query, extend whatever DTO backs the FE's ticket-detail response with `started_at` and `completed_at` only (raw timestamps; no duration field on the DTO, per OI-SDD-LEAD-TIME-1, closed).
- FE impact required: `TicketDetailDrawer.tsx`'s `TicketInformationCard` gains three new rows (created time, updated time, duration). The duration row is computed client-side from the two returned timestamps — not returned by the BE — using the existing i18n system to localize the `"1d 8h"`-style output (OI-SDD-LEAD-TIME-2, closed). Created/updated time rows follow the existing `createAt`/`updatedAt`/`mergedAt` pattern (`t("Pages.PmDashboard.<key>", { defaultValue })` + `formatDateTime(...)`), with `-` fallback when the underlying value(s) are null; the duration row shows `-` when either timestamp is null or when `completed_at < started_at` (OI-SDD-LEAD-TIME-3, closed).
- No new REST endpoint is introduced; the existing ticket-detail endpoint response payload is extended (exact endpoint path to be confirmed in Phase 3 by reading `PmDashboardJdbcAdapter`'s full query/DTO mapping, not read in full during Phase 1).
- No webhook or manual-trigger API changes.

## 12. DB/Migration Impact

Existing table to extend:

| table | expected use |
|---|---|
| `tbl_dim_ticket` | Add `completed_at TIMESTAMPTZ` (nullable), mirroring the existing `started_at` column defined in `V4__init_shema_v2.sql:184`. No new table needed. |

Expected migration direction:

- New file `V513__add_completed_at_to_ticket.sql` (next available version after `V512__add_ai_finding_stat_tracking.sql`).
- `ALTER TABLE tbl_dim_ticket ADD COLUMN completed_at TIMESTAMPTZ;` — nullable, no default. No backfill migration/script is required (H-SDD-LEAD-TIME-3, closed): values populate through the normal Artifact Scanner scan cadence like any other artifact-derived field.
- Do not repurpose the existing `closed_at` column (also present on `tbl_dim_ticket`) — its semantics (ticket closed) are distinct from "report.md updated," and reusing it would conflict with its existing `ck_ticket_date_order` check constraint semantics.
- No change to `started_at`'s column definition — only its population source changes (now driven by `spec-pack.md` content instead of being generally unset).
- The duration is not persisted as a column and is not computed BE-side; it is computed client-side (FE) from `started_at`/`completed_at` (OI-SDD-LEAD-TIME-1, closed).

## 13. Security/Privacy Impact

- No new data classes are introduced; all three values (two timestamps, one derived duration) are dates already present in existing SDD process markdown files, not user PII or secrets.
- No new external API call is introduced (per user decision to avoid GitHub commit-history lookups), so no new token/credential exposure surface.
- No change to authentication/authorization — this extends an existing read-only dashboard view under existing session-based access.

## 14. Operation/Maintenance Impact

- No new scheduled job or background worker — values are written as part of the existing Artifact Scanner scan cadence, using the same mechanism as other artifact-derived fields (H-SDD-LEAD-TIME-3, closed).
- If a previously-valid date field becomes malformed on a later scan, the confirmed behavior is to null out the column rather than preserve the last known good value (H-SDD-LEAD-TIME-4, closed) — operationally this means a previously-visible date (and the duration) can disappear from the dashboard after an unrelated edit to the file; this is accepted behavior, not an Open Issue.
- No new log/alerting requirement beyond the existing Artifact Scanner scan-failure handling; parsing failures for this specific field must not escalate to full scan failure (BR-SDD-LEAD-TIME-6).

## 15. Test Strategy Summary

| test area | test type | target |
|---|---|---|
| Date normalization (`YYYY-MM-DD` → `YYYY-MM-DD 00:00:00`, full datetime passthrough, malformed input) | BE unit test | Shared date-parsing utility used by both parsers. |
| `SpecPackMarkdownParser` created-time extraction | BE unit test | `**Create date**` header present / absent / malformed. |
| `ReportMarkdownParser` updated-time extraction | BE unit test | `**Update date**` header present / absent / malformed. |
| Artifact Scanner persist step | BE integration test | `started_at`/`completed_at` written correctly on scan; re-scan overwrites values (including regressing to `NULL`); missing file leaves column `NULL`. |
| Duration computation | FE unit test | Correct `"1d 8h"`-style localized interval for well-formed pair; `-` when either input is null; `-` when `completed_at < started_at`. |
| PM Dashboard ticket-detail query/DTO | BE integration test | All three fields returned correctly, including null cases. |
| `TicketInformationCard` rendering | FE component test | Renders formatted date, renders duration, renders `-` fallback, uses i18n keys (no hardcoded text). |
| Regression | Regression test | Existing `PmDashboardJdbcAdapter` behavior referencing `started_at` unaffected. |

## 16. Human Decision Required

| ID | decision item | reasons | owner | status |
|---|---|---|---|---|
| H-SDD-LEAD-TIME-1 | The header label to read from `spec-pack.md` for "created time" is `**Create date**`. | Confirmed by user 2026-08-21. | PM | Closed |
| H-SDD-LEAD-TIME-2 | `report.md`'s "updated time" is read from its existing `**Update date**` header field; no new template field is added. | Confirmed by user 2026-08-21. | PM | Closed |
| H-SDD-LEAD-TIME-3 | Values are read and persisted using the same scan/parse mechanism the system already uses (existing Artifact Scanner flow); no separate backfill job. | Confirmed by user 2026-08-21. | PM | Closed |
| H-SDD-LEAD-TIME-4 | When a date field becomes malformed/missing on a later scan, the corresponding column is set to `NULL` (not preserved). | Confirmed by user 2026-08-21. | PM | Closed |
| H-SDD-LEAD-TIME-5 | A computed duration based on `started_at` and `completed_at` is displayed alongside them in the UI. | Confirmed by user 2026-08-21 — ticket scope now includes this duration, consistent with the "Lead Time" ticket name. | PM | Closed |

## 17. Assumptions and Inference Log

| ID | assumption | basis | risk | need confirmation? |
|---|---|---|---|---|
| A-SDD-LEAD-TIME-1 | `spec-pack.md`'s created-time value is read from its `**Create date**` header field. | User decision, confirmed 2026-08-21 (H-1, closed). | Low. | No |
| A-SDD-LEAD-TIME-2 | Timestamps are sourced from file content parsed during the existing Artifact Scanner pass, not from GitHub commit history or filesystem metadata. | Explicit user decision, confirmed 2026-08-21. | Low. | No |
| A-SDD-LEAD-TIME-3 | Both `started_at` and `completed_at` are overwritten on every scan (no "set once" semantics), and a malformed value on a later scan nulls out the column rather than preserving the prior value. | Explicit user decision, confirmed 2026-08-21 (H-3, H-4, closed). | Low — an unrelated file edit can make a previously-visible date/duration disappear; accepted as intended behavior. | No |
| A-SDD-LEAD-TIME-4 | Bare `YYYY-MM-DD` values are normalized to `YYYY-MM-DD 00:00:00` for both storage and display. | Explicit user decision, confirmed 2026-08-21. | Low. | No |
| A-SDD-LEAD-TIME-5 | `tbl_dim_ticket.completed_at` should be a plain nullable `TIMESTAMPTZ` with no default and no new check constraint beyond mirroring `started_at`. | Ticket instruction to create it "similar to `started_at`." | Low. | No |
| A-SDD-LEAD-TIME-6 | This ticket does not require a new REST endpoint; the existing ticket-detail response is simply extended. | Ticket only asks for display in an existing drawer; no new UI flow or trigger described. | Low. | No |
| A-SDD-LEAD-TIME-7 | The duration is computed client-side (FE) at render time from `started_at`/`completed_at` and is not persisted as its own DB column, not computed BE-side. | User decision, confirmed 2026-08-21 (OI-1, closed). | Low. | No |
| A-SDD-LEAD-TIME-8 | Duration display format is `"1d 8h"`-style, localized via the existing i18n system according to the current language setting. | User decision, confirmed 2026-08-21 (OI-2, closed). | Low. | No |
| A-SDD-LEAD-TIME-9 | Duration shows `-` when either `started_at`/`completed_at` is null, or when `completed_at < started_at`. | User decision, confirmed 2026-08-21 (OI-3, closed). | Low. | No |

## 18. Open Issues

| ID | issue | impact | owner | status |
|---|---|---|---|---|
| OI-SDD-LEAD-TIME-1 | Duration is computed on the FE from `started_at`/`completed_at` returned by the ticket-detail API; BE does not compute or return a pre-formatted duration. | Confirmed by user 2026-08-21. | FE | Closed |
| OI-SDD-LEAD-TIME-2 | Duration display format is `"1d 8h"`-style (compact unit-abbreviated), localized according to the system's current i18n language setting rather than hardcoded to one locale's wording. | Confirmed by user 2026-08-21. | FE | Closed |
| OI-SDD-LEAD-TIME-3 | If `completed_at < started_at`, or if either `started_at`/`completed_at` is null, the duration displays `-`. | Confirmed by user 2026-08-21. | FE | Closed |

All Open Issues are closed; none remain outstanding for this ticket.

## Phase 1 Output Summary

1. **Can Phase 3 proceed from this Spec Pack alone?** **Yes.** All five Human Decisions (§16) and all three Open Issues (§18) are now closed: header labels (`**Create date**` / `**Update date**`), the read/persist mechanism, null-on-malformed behavior, in-scope duration display, FE-side duration computation, the `"1d 8h"`-style localized display format, and the `-` fallback for null/out-of-order inputs are all confirmed.
2. **What information is missing?** None identified as blocking. The only item left for Phase 3 to finalize is the exact BE contract field names/endpoint shape for `started_at`/`completed_at` in the ticket-detail response — mechanical wiring detail, not a decision requiring further human input.
3. **Questions to confirm with a human:** None outstanding.
4. **Specialist input needed?** None — no new external integration, security review, or specialized infrastructure expertise is required for this narrowly-scoped, now fully-decided ticket.
