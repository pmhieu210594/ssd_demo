# Impact Analysis

**Ticket ID**: SDD-LEAD-TIME
**Create date**: 2026-08-21 16:00:00
**Author**: nvt_dung
**Update date**: 2026-08-21 16:00:00

## 1. Change Content

- Extract `spec-pack.md`'s `**Create date**` header value (already available via `ParsedArtifact.headerMetadata().get("create_date")`, zero parser change) and persist it into the existing `tbl_dim_ticket.started_at` column during the existing Artifact Scanner persist step.
- Extract `report.md`'s `**Update date**` header value (`headerMetadata().get("update_date")`) and persist it into a **new** `tbl_dim_ticket.completed_at` column (Flyway `V513`), during the existing persist step for report parses.
- Add one shared date-normalization utility (`YYYY-MM-DD` → `YYYY-MM-DD 00:00:00`; malformed/empty → `null`) reused by both persist call sites.
- Extend `PmDashboardJdbcAdapter`'s ticket-detail query/DTO to return `startedAt`/`completedAt` (raw, nullable) — no BE-computed duration.
- Add 3 new rows (created time, updated time, duration) to `TicketInformationCard` in `TicketDetailDrawer.tsx`; duration computed client-side, `"1d 8h"`-style, i18n-localized, `-` fallback.
- Add 3 new i18n keys to `en`/`vi`/`ja` locale files under `Pages.PmDashboard.*`.

## 2. Directly Affected Files

| file | reason | change type |
|---|---|---|
| `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/scanner/ArtifactScannerService.java` | Read `headerMetadata`, normalize, call new persistence-port method in `persistSpecPackParse` (515-621) and `persistReportParse` (893-1027) | Sửa |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/persistence/ArtifactScannerPersistencePort.java` | Add `updateTicketLeadTimeDates(UUID, OffsetDateTime, OffsetDateTime)` — no existing method covers this (confirmed, 32 methods read in full) | Thêm |
| JDBC implementation of `ArtifactScannerPersistencePort` (adapter class backing the port, path to confirm at Phase 4 start per `ArtifactScannerPersistencePort`'s `@Repository`/impl annotation) | Implement the new port method with its own `TransactionTemplate`, per `.claude/rules/20-architecture.md` | Thêm |
| `EDCAP_BE/src/main/resources/db/migration/V513__add_completed_at_to_tbl_dim_ticket.sql` (new file) | `ALTER TABLE tbl_dim_ticket ADD COLUMN completed_at TIMESTAMPTZ;` nullable, no default | Thêm |
| A new shared date-normalization utility, e.g. `EDCAP_BE/.../domain/service/markdown/core/HeaderDateNormalizer.java` (exact name/location to confirm at Phase 4 start; must stay under `domain/service/markdown` to respect hexagonal boundaries) | Single implementation of BR-SDD-LEAD-TIME-3, reused by both call sites in `ArtifactScannerService` | Thêm |
| `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/PmDashboardJdbcAdapter.java` | Add `t.started_at`, `t.completed_at` to `findDetail`'s inline SQL (216-271), `baseTicketQuery()` SQL (399-437), and `mapTicketRow` (477-512) | Sửa |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/pmdashboard/PmDashboardModels.java` | Add `startedAt`/`completedAt` (`OffsetDateTime`, nullable) to `DashboardTicketRow`'s canonical record (117-150) **and** its compact secondary constructor (151-215) | Sửa |
| `EDCAP_FE/src/lib/api.ts` | Add `startedAt: string \| null`, `completedAt: string \| null` to `PmDashboardTicketRow` (549-583) | Sửa |
| `EDCAP_FE/src/pages/pm-dashboard/components/TicketDetailDrawer.tsx` | Add 3 rows to `TicketInformationCard` (236-291): created time, updated time, duration (conditional render pattern copied from `mergedAt`) | Sửa |
| A new pure FE helper, e.g. `EDCAP_FE/src/utils/leadTime.ts` (exact path to confirm at Phase 4; must be a pure function per `20-architecture.md` — not Redux/Zustand) | `computeLeadTimeDuration(startedAt, completedAt)` — null-safe, order-checked, i18n-localized `"1d 8h"` output | Thêm |
| `EDCAP_FE/public/locales/en/locale.json`, `vi/locale.json`, `ja/locale.json` | Add 3 new keys under `Pages.PmDashboard.*` (e.g. `startedAt`/`completedAt`/`leadTimeDuration` — final names in impl-plan) | Sửa |

## 3. Indirectly Affected Files

| file | reason | risk |
|---|---|---|
| Any other caller of `DashboardTicketRow`'s compact secondary constructor (117-216) besides `PmDashboardJdbcAdapter` | Compact constructor currently defaults trailing fields (`refreshedAt`→`updatedAt`, `artifactVersion`/`mergedAt`→`null`); adding 2 more trailing fields with a default risks silently returning `null` for `startedAt`/`completedAt` for any unverified caller | Medium — must grep for all call sites before finalizing the constructor signature |
| ArchUnit `ArchitectureTest` (exact path not read in full this phase — flagged in `source-inventory.md` Missing Files) | New port method (application) + new JDBC impl (infrastructure) + new domain utility must respect existing layer dependency rules | Low — additive only, no new package crossing expected, but must be run to confirm |
| `PMDashboardPage.tsx` (inline `useQuery` for ticket-detail, per context.md) | Consumes the extended `PmDashboardTicketDetail`/`PmDashboardTicketRow` type; no code change expected since new fields are additive/optional | Low |

## 4. Caller / Callee

| caller | callee | impact |
|---|---|---|
| `ArtifactScannerService.scanTicketDirectory` | `persistSpecPackParse` / `persistReportParse` | Each now also calls the new `updateTicketLeadTimeDates` port method after normalizing the relevant header value |
| `persistSpecPackParse` / `persistReportParse` | new `ArtifactScannerPersistencePort.updateTicketLeadTimeDates(ticketId, startedAt, completedAt)` | New call; must be invoked with its own `TransactionTemplate`, independent of other persist calls in the same method, per existing per-upsert transaction convention |
| `updateTicketLeadTimeDates` (port) | New JDBC adapter implementation → `UPDATE tbl_dim_ticket SET started_at = ?, completed_at = ? WHERE ticket_id = ?` | New SQL write path |
| `PMDashboardPage.tsx` (TanStack Query) | `endpoints.pmDashboard.detail(ticketId)` → `GET /api/v1/pm/dashboard/tickets/{ticketId}/detail` | No new endpoint; response payload gains 2 fields |
| `PmDashboardJdbcAdapter.findDetail` | `mapTicketRow` → `DashboardTicketRow` → `DashboardTicketDetail` | Row mapper reads 2 new columns |
| `TicketDetailDrawer.tsx` (`TicketInformationCard`) | `formatDateTime(...)` (existing, unchanged) + new `computeLeadTimeDuration(...)` (new, pure) | 3 new render rows |

## 5. FE Impact

- `TicketInformationCard` gains 3 new rows: spec-pack created time, report updated time, computed duration — following the exact `t("Pages.PmDashboard.<key>", { defaultValue })` + `formatDateTime(...)` pattern already used by `createAt`/`updatedAt`, and the exact null-guarded conditional-render pattern already used by `mergedAt` (`detail.row.mergedAt ? (...) : null`).
- One new pure helper function computes the duration client-side from `row.startedAt`/`row.completedAt`; it is not stored in Redux or Zustand (per `20-architecture.md`, ephemeral/derived render values don't belong in either store).
- `formatDateTime` (`EDCAP_FE/src/lib/utils.ts`) is reused unchanged for the two timestamp rows.
- No new API call path is introduced — the existing inlined `useQuery` in `PMDashboardPage.tsx` and `endpoints.pmDashboard.detail` are reused unchanged; only the response's TypeScript type gains 2 optional fields.
- 3 new i18n keys must be added to all three locale files (`en`, `vi`, `ja`) under `Pages.PmDashboard.*`; no existing key is renamed or removed.

## 6. BE Impact

- `SpecPackMarkdownParser`/`ReportMarkdownParser` — **no code change** required; `headerMetadata` is already exposed on `ParsedArtifact` today.
- `ArtifactScannerService` — both `persistSpecPackParse` and `persistReportParse` gain a small block: read the relevant `headerMetadata` key, normalize via the new shared utility, call the new port method — always overwriting (including to `NULL`), matching BR-SDD-LEAD-TIME-6/9.
- New `ArtifactScannerPersistencePort` method + JDBC implementation, using its own `TransactionTemplate` (one per external upsert, per `20-architecture.md`), independent from the other persist calls in the same method so a failure in one does not roll back the other.
- `PmDashboardJdbcAdapter` — both `findDetail`'s inline SQL and `baseTicketQuery()`'s SQL gain 2 columns each; `mapTicketRow` gains 2 `rs.getObject(..., OffsetDateTime.class)` reads, matching the existing pattern used for `merged_at`.
- `DashboardTicketRow`/`DashboardTicketDetail` — `DashboardTicketRow` gains 2 fields on both its canonical and compact constructors; `DashboardTicketDetail` needs **no change** (per spec-pack §11, the 2 raw fields live on `row`, no duration field on the DTO).

## 7. API Contract Impact

| endpoint | request impact | response impact | backward compatible? |
|---|---|---|---|
| `GET /api/v1/pm/dashboard/tickets/{ticketId}/detail` | None | Response payload's ticket-row object gains 2 new nullable fields: `startedAt`, `completedAt` (ISO-8601 string or null) | Yes — purely additive, existing consumers unaffected |

No new endpoint is introduced (confirmed absence of any manual/admin trigger requirement, spec-pack §2.2).

## 8. DTO / Schema / Validation Impact

- BE: `DashboardTicketRow` record — 2 new nullable `OffsetDateTime` fields, added to both the 31-field canonical constructor and the compact secondary constructor (must not silently default to `null` for existing legitimate values — verify no caller expects a different default).
- FE: `PmDashboardTicketRow` interface — 2 new `string | null` fields, mirroring `mergedAt`'s style.
- No validation rule changes — both fields are read-only, scanner-populated, never user-submitted.

## 9. DB / Migration Impact

- New migration `V513__add_completed_at_to_tbl_dim_ticket.sql`: `ALTER TABLE tbl_dim_ticket ADD COLUMN completed_at TIMESTAMPTZ;` — nullable, no default, no backfill script (H-SDD-LEAD-TIME-3, closed).
- `started_at` column definition is unchanged; only its population source changes (from generally-unset to `spec-pack.md`-derived).
- `ck_ticket_date_order` constraint (`closed_at >= created_at`) is **not** affected — confirmed it does not reference `started_at`/`completed_at`; no constraint change needed and none should be added (`closed_at` must not be repurposed, per ticket-rules.md).
- No index changes — neither column is queried by a new filter/sort path in this ticket's scope (existing `started_at`-based ordering in `PmDashboardJdbcAdapter`, if any, already has whatever index it needs today).

## 10. Batch / Job / Event Impact

- No new scheduled job, worker, or event is introduced. Both new writes happen inline within the existing Artifact Scanner scan pass (`persistSpecPackParse`/`persistReportParse`), on the existing scan cadence (BR-SDD-LEAD-TIME-9, H-SDD-LEAD-TIME-3, closed).
- No dedicated backfill job/script — historical tickets populate `started_at`/`completed_at` the next time they're scanned by the normal cadence (AC-SDD-LEAD-TIME-12).

## 11. Test Impact

- New BE unit tests: shared date-normalization utility (full datetime passthrough / bare-date normalization / malformed-empty-whitespace → null); `SpecPackMarkdownParser`/header-extraction present/absent/malformed cases (via existing `headerMetadata` map, no parser-level test change needed beyond confirming the map already contains the key).
- New BE integration test: Artifact Scanner persist step — `started_at`/`completed_at` written on scan; re-scan overwrites (including regressing to `NULL` per H-4); missing file leaves column `NULL`; malformed field does not fail the overall scan run (BR-SDD-LEAD-TIME-6).
- New BE integration test: `PmDashboardJdbcAdapter` ticket-detail query returns both new fields correctly including null cases.
- New BE regression test: existing `PmDashboardJdbcAdapter` behavior referencing `started_at` (e.g. any existing ordering) is unaffected once `started_at` starts being generally populated.
- New FE unit test: `computeLeadTimeDuration` — correct `"1d 8h"`-style output for a well-formed pair; `-` when either input null; `-` when `completed_at < started_at`.
- New FE component test: `TicketInformationCard` renders formatted dates, duration, `-` fallback, and uses i18n keys (no hardcoded text) — for all three locales' key presence.

## 12. Operation / Monitoring Impact

- No new scheduled job or alerting requirement. Malformed/missing date-field cases must log at most a warning, consistent with existing missing-artifact handling in `ArtifactScannerService`; they must never escalate to a full scan-run failure (BR-SDD-LEAD-TIME-6).
- Accepted operational behavior (H-SDD-LEAD-TIME-4, closed, not a regression to guard against): a previously-visible date/duration can disappear from the dashboard after an unrelated edit to the source markdown file nulls out the corresponding header field on a later scan.

## 13. Rollout / Rollback Impact

- Rollout: migration `V513` is purely additive (nullable column, no default) — safe to deploy ahead of, alongside, or independent of the BE/FE code changes; old code ignores the new column, new code degrades gracefully if the column doesn't exist yet only in the window between migration and code deploy is avoided by deploying migration first.
- Rollback: code revert is standard (no feature flag introduced, per context.md's Forbidden Common Components — no flag needed for this additive read-model change). DB rollback for `V513` is a manual `ALTER TABLE tbl_dim_ticket DROP COLUMN completed_at;` — not automated by Flyway's default `migrate` command; treat as a deliberate, separately-approved DBA action, not part of the standard app rollback path, since the column is fully derived (safe to drop with no data-loss concern beyond losing already-scanned values, which will simply repopulate on next scan).

## 14. Areas Determined to be Unaffected and Based on

| area | judgment | evidence |
|---|---|---|
| GitHub commit-history API / `GithubArtifactScannerSourceAdapter.resolveCommitDate` | Not touched | Explicitly rejected approach (spec-pack §2.2, A-SDD-LEAD-TIME-2); `resolveCommitDate` only handles whole-revision dates and is not extended for per-file history |
| `report.md` template | No new header field added | H-SDD-LEAD-TIME-2, closed — existing `**Update date**` field is reused as-is |
| Historical backfill job/migration script | Not created | H-SDD-LEAD-TIME-3, closed — values populate via normal scan cadence |
| `tbl_dim_ticket.closed_at` / `ck_ticket_date_order` | Not repurposed or altered | Semantically distinct from "report updated"; reusing it would conflict with the existing check constraint (context.md Forbidden Methods table) |
| New REST/admin endpoint | Not added | Spec-pack §2.2 explicitly rules this out; only the existing ticket-detail response is extended |
| BE-computed/returned duration field | Not added to any DTO | OI-SDD-LEAD-TIME-1, closed — duration is FE-only |
| `ja/locale.json` pre-existing "gap" (`createAt`/`mergedAt`/`ticketInformation`) | **No backfill performed; correction noted** | context.md's context claimed these keys were missing in `ja`. Live read of `ja/locale.json` during Phase 3 exploration confirms all three keys are already present at line offsets parallel to `en`/`vi`. Per user decision (2026-08-21), this correction is stated here and no backfill is performed — only this ticket's 3 new keys are added to all three locale files, which was the planned behavior regardless of whether the gap existed |
| `PmDashboardJdbcAdapter`'s existing `started_at`-referencing ordering/behavior | Column semantics/type unchanged, only unaffected by data becoming populated where it was previously mostly null — verified via required regression test, not just inspection | `started_at`'s column type and existing SQL usage are untouched; only its population source changes (spec-pack §2.2, §6.6 Compatibility) |
| Any other artifact parser besides `SpecPackMarkdownParser`/`ReportMarkdownParser` | Not touched | Spec-pack §2.2 explicitly scopes the change to these two parsers only |

## 15. Required Options

- BE Contract (additive DTO/response fields) — required, per spec-pack §10 Complexity Classification.
- DB Migration (`V513`, additive nullable column) — required.
- i18n Review (3 new keys × 3 locales) — required.

## 16. Human Decision Required

None. All Human Decisions (spec-pack §16) and Open Issues (spec-pack §18) are closed. The single Phase-3-local discrepancy (ja locale note) was resolved with the user during this phase — see §14 above; no outstanding decision blocks Phase 4.

## 17. Risk Summary

- **Low risk overall** — this is an additive, single-column, two-file-read, three-locale change reusing an existing pipeline end-to-end; no new external integration, no new endpoint, no BE-computed duration.
- **Primary risk (Medium)**: `PmDashboardJdbcAdapter`'s duplicated SQL (`findDetail` vs. `baseTicketQuery()`) — must be updated identically in both places or the ticket-detail and ticket-list responses will disagree; mitigated by an explicit implementation step and a review-focus item.
- **Secondary risk (Medium)**: `DashboardTicketRow`'s compact secondary constructor defaults trailing fields for unverified callers — must grep all call sites before finalizing the new field defaults, to avoid silently returning `null` for `startedAt`/`completedAt` where a real value should exist.
- **Accepted, non-blocking risk**: a previously-visible date/duration can disappear from the dashboard after an unrelated file edit nulls out a header field on the next scan — this is explicitly confirmed, intended behavior (H-SDD-LEAD-TIME-4), not a defect.
