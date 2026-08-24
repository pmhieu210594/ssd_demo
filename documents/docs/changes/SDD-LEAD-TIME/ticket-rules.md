# Ticket Rules:

**Ticket ID**: SDD-LEAD-TIME
**Create date**: 2026-08-21 15:30:00
**Author**: nvt_dung
**Update date**: 2026-08-21 15:30:00

## Must Follow

- Implement only what is defined in `spec-pack.md`; any unclear point must become an Open Issue in `impl-plan.md`, not a silent assumption.
- Read the "created time" of `spec-pack.md` from its `**Create date**` header field, and the "updated time" of `report.md` from its `**Update date**` header field — via `MarkdownParserCore.extractHeaderMetadata()` / `ParsedArtifact.headerMetadata()` only (BR-SDD-LEAD-TIME-1, BR-SDD-LEAD-TIME-2). No GitHub commit-history lookup.
- Accept both `YYYY-MM-DD HH:mm:ss` and bare `YYYY-MM-DD` date values; normalize a bare date to `YYYY-MM-DD 00:00:00` (BR-SDD-LEAD-TIME-3). Implement this normalization **once**, in a shared utility used by both `SpecPackMarkdownParser` and `ReportMarkdownParser`.
- Persist `spec-pack.md`'s created time into `tbl_dim_ticket.started_at` (existing column) and `report.md`'s updated time into `tbl_dim_ticket.completed_at` (new column via Flyway migration `V513`, mirroring `started_at`) — BR-SDD-LEAD-TIME-4, BR-SDD-LEAD-TIME-5.
- Values are (re)written every time Artifact Scanner scans the ticket's folder, using the existing scan/parse/persist call sites in `ArtifactScannerService` (`persistSpecPackParse`, `persistReportParse`) — no separate recompute API, no dedicated backfill job (BR-SDD-LEAD-TIME-9, H-SDD-LEAD-TIME-3).
- If a source file is missing, not yet scanned, or its date field is missing/unparseable, set the corresponding DB column to `NULL` — never preserve a previously-valid value (BR-SDD-LEAD-TIME-6, H-SDD-LEAD-TIME-4). This must not fail the overall Artifact Scanner run for that ticket.
- Extend `PmDashboardJdbcAdapter`'s ticket-detail query/DTO to return raw `started_at`/`completed_at` only — no BE-computed duration field (spec-pack §11).
- Render created time, updated time, and duration inside `TicketInformationCard` in `TicketDetailDrawer.tsx`, following the existing `t("Pages.PmDashboard.<key>", { defaultValue })` + `formatDateTime(...)` pattern used by `createAt`/`updatedAt`/`mergedAt` (BR-SDD-LEAD-TIME-7).
- Compute duration client-side (FE) only, from `started_at`/`completed_at`; display `-` when either is null or when `completed_at < started_at` (BR-SDD-LEAD-TIME-8, BR-SDD-LEAD-TIME-10, OI-SDD-LEAD-TIME-1, OI-SDD-LEAD-TIME-3). Localize the `"1d 8h"`-style format via the existing i18n system (OI-SDD-LEAD-TIME-2).
- Add new i18n keys to **all three** locale files (`en`, `vi`, `ja`) under `Pages.PmDashboard.*`.
- Before implementation, verify whether `ArtifactScannerPersistencePort` already has a ticket-date-update method; if not, add one rather than writing raw SQL inline in the service.
- Check full-width/half-width digits, empty strings, whitespace-only values, and non-date text in the header field — all must be treated as unparseable → `NULL`/`-`.

## Must Not Do

- Do not call any GitHub commit-history API (per-file first/last commit lookup) for either timestamp — explicitly rejected (spec-pack §2.2, A-SDD-LEAD-TIME-2).
- Do not add a new/dedicated "updated" header field to the `report.md` template — reuse the existing `Update date` field as-is (H-SDD-LEAD-TIME-2, closed).
- Do not add a separate backfill job or migration script for historical tickets (H-SDD-LEAD-TIME-3, closed).
- Do not preserve a previously-valid timestamp when a later scan finds the date field missing/malformed (H-SDD-LEAD-TIME-4, closed) — null it out instead.
- Do not repurpose `tbl_dim_ticket.closed_at` for the "report updated" value — its semantics and `ck_ticket_date_order` check constraint are unrelated.
- Do not add any new REST/admin endpoint for this feature — extend the existing ticket-detail response only.
- Do not compute or return the duration from the BE — it is FE-only (OI-SDD-LEAD-TIME-1, closed).
- Do not add a new FE date-formatting utility — reuse `formatDateTime` in `EDCAP_FE/src/lib/utils.ts`.
- Do not store the duration as its own DB column.
- Do not change the meaning/behavior of `tbl_dim_ticket.started_at` for other existing consumers (e.g. `PmDashboardJdbcAdapter` ordering) — only its population source changes.
- Do not add `@Transactional` at the wrong layer — follow the existing convention of transaction handling in the persistence-adapter layer, one `TransactionTemplate` per external upsert (per `.claude/rules/20-architecture.md`).

## Stop / Ask Conditions

- `ArtifactScannerPersistencePort` has no existing method suitable for updating `tbl_dim_ticket.started_at`/`completed_at`, and adding one would require a broader port-interface change than expected.
- The shared date-normalization utility would need to live outside `domain/service/markdown` in a way that crosses hexagonal layer boundaries unexpectedly.
- `PmDashboardJdbcAdapter`'s existing `started_at`-dependent ordering/behavior appears to change semantics once `started_at` starts being populated from `spec-pack.md` content instead of being generally unset.
- The `ja` locale gap (missing `createAt`/`mergedAt`/`ticketInformation` keys) is judged to block adding the 3 new keys cleanly.
- Any requirement surfaces to compute duration BE-side, add a new endpoint, or add a backfill job — all explicitly out of scope per the closed spec-pack decisions.

## Review Focus

- ArchUnit `ArchitectureTest` stays green: `web` must not import `infrastructure`; new persistence-port method lives in `application`, implementation in `infrastructure`.
- Date-normalization logic exists exactly once and is shared by both parsers, not duplicated (spec-pack §6.6 Maintainability).
- Malformed/missing date field never fails the Artifact Scanner run (BR-SDD-LEAD-TIME-6) — confirm via a fixture-based test, not just code reading.
- Duration null-safety and `completed_at < started_at` ordering check are both covered (OI-SDD-LEAD-TIME-3).
- i18n keys added consistently to `en`, `vi`, `ja`, following the `Pages.PmDashboard.<key>` convention with no hardcoded strings.
- Existing `PmDashboardJdbcAdapter` queries/behavior referencing `started_at` are unaffected (regression check).
- Migration `V513` is additive only (nullable column, no default, no data migration).

## Test Focus

- Date normalization: full datetime passthrough, bare-date → `00:00:00`, malformed/empty/whitespace → unparseable.
- `SpecPackMarkdownParser` created-time extraction: header present / absent / malformed.
- `ReportMarkdownParser` updated-time extraction: header present / absent / malformed.
- Artifact Scanner persist step: `started_at`/`completed_at` written on scan; re-scan overwrites (including regressing to `NULL`); missing file leaves column `NULL`; run does not fail on a malformed field.
- FE duration computation: correct `"1d 8h"`-style localized output for a well-formed pair; `-` when either input is null; `-` when `completed_at < started_at`.
- `PmDashboardJdbcAdapter` ticket-detail query/DTO: all three fields (`startedAt`, `completedAt`, and FE-derived duration) correct including null cases.
- `TicketInformationCard` rendering: formatted date, duration, `-` fallback, i18n keys used (no hardcoded text) for all three locales.
- Regression: existing `PmDashboardJdbcAdapter` behavior referencing `started_at` (e.g. ordering) unaffected.
