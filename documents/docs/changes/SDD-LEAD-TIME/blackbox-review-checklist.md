# Black-box Review Checklist

**Ticket ID**: SDD-LEAD-TIME
**Create date**: 2026-08-21
**Author**: nvt_dung
**Update date**: 2026-08-21

---

## How to use

- Each reviewer marks PASS / FAIL / SKIP (with justification).
- Any FAIL P0 item blocks release.
- P1/P2 gaps must have a documented follow-up ticket or risk acceptance note.

---

## Category 1 — Boundary & Edge Combinations

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 1.1 | Bare `YYYY-MM-DD` date (no time-of-day) normalizes to `YYYY-MM-DD 00:00:00` in both storage and UI | AC-SDD-LEAD-TIME-5 | P1 | [ ] | See TC-SDD-LEAD-TIME-6 |
| 1.2 | File-presence combinations (0/1/2 files) each populate only the corresponding field, other stays `-` | AC-SDD-LEAD-TIME-3, AC-SDD-LEAD-TIME-4 | P0 | [ ] | See TC-SDD-LEAD-TIME-2, BD-4 in test-data.md |
| 1.3 | `completed_at` earlier than `started_at` (out-of-order timestamps) shows duration `-` while both raw timestamps still display | AC-SDD-LEAD-TIME-11 | P1 | [ ] | See TC-SDD-LEAD-TIME-4, BD-3 |

---

## Category 2 — Permission & Access Control

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 2.1 | No new role/permission is introduced; feature is read-only under existing session-based access | N/A | P0 | [x] SKIP | Confirmed in test-data.md "User / Permission Data" — feature has no new role; no permission matrix to test |
| 2.2 | Ticket-detail endpoint access control unchanged (no new auth gate added or removed) | N/A | P0 | [x] SKIP | Spec-pack §13 — no auth/authz change |
| 2.3 | No role-based UI guard added around the 3 new fields (visible to all existing viewers of the drawer) | AC-SDD-LEAD-TIME-7 | P1 | [ ] | Confirm via manual review of `TicketInformationCard` — fields render unconditionally like `createAt`/`updatedAt` |

---

## Category 3 — Compatibility & Contract Stability

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 3.1 | Existing `PmDashboardJdbcAdapter` behavior/ordering referencing `started_at` is unaffected once the column becomes generally populated | AC-SDD-LEAD-TIME-10 | P0 | [ ] | See TC-SDD-LEAD-TIME-7 |
| 3.2 | Ticket-detail response payload shape stays additive — existing consumers/fields unaffected by the 2 new nullable fields (`startedAt`, `completedAt`) | AC-SDD-LEAD-TIME-1, AC-SDD-LEAD-TIME-2 | P0 | [ ] | Confirm via API response diff (existing fields unchanged, new fields nullable) |
| 3.3 | Ticket-detail API called before any scan has run returns null for both fields; UI shows `-` for all three rows (no error/exception contract change) | N/A (spec-pack §6.4) | P0 | [ ] | Matches existing nullable-field convention |

---

## Category 4 — Exception Handling & Resilience

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 4.1 | Malformed/unparseable date field in either file nulls out the corresponding column without failing the whole Artifact Scanner run | AC-SDD-LEAD-TIME-6 | P0 | [ ] | See TC-SDD-LEAD-TIME-3, ED-1/ED-3 |
| 4.2 | Missing `spec-pack.md`/`report.md` leaves the corresponding column `NULL` without failing the scan for other artifacts | AC-SDD-LEAD-TIME-3, AC-SDD-LEAD-TIME-4 | P0 | [ ] | See TC-SDD-LEAD-TIME-2, ED-2 |
| 4.3 | Re-scan that regresses a previously-valid date field to missing/malformed clears the column to `NULL` (does not preserve the prior value) | AC-SDD-LEAD-TIME-6 | P1 | [ ] | See TC-SDD-LEAD-TIME-3 |

---

## Category 5 — Performance / Degradation Signals (Black-box observable)

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 5.1 | N/A — no new parallel/lazy-loaded data path introduced by this ticket | N/A | P1 | [x] SKIP | Additive single-column read, reuses existing ticket-detail query/response |
| 5.2 | N/A — no large-dataset rendering change (3 additional scalar fields on an existing drawer) | N/A | P1 | [x] SKIP | Spec-pack §10 Complexity: Medium, no new data volume concern |
| 5.3 | No visible freeze/reload loop introduced when opening Ticket Detail drawer with the 3 new fields | N/A | P1 | [ ] | Manual smoke check recommended if E2E is unavailable (see test-plan.md §5, §8) |

---

## Category 6 — Business Rule Integrity

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 6.1 | Duration is computed as `completed_at - started_at`, null-safe (returns `-` when either input is null) | AC-SDD-LEAD-TIME-9, AC-SDD-LEAD-TIME-11 | P0 | [ ] | See TC-SDD-LEAD-TIME-1, TC-SDD-LEAD-TIME-2, TC-SDD-LEAD-TIME-4 |
| 6.2 | Duration displays `-` when `completed_at < started_at`, while both raw timestamps still display normally | AC-SDD-LEAD-TIME-11 | P0 | [ ] | See TC-SDD-LEAD-TIME-4 |
| 6.3 | Re-scanning after a source file's date value changes overwrites the corresponding column with the latest value (no history/append) | AC-SDD-LEAD-TIME-8 | P0 | [ ] | See TC-SDD-LEAD-TIME-5 |
| 6.4 | Historical tickets scanned before this feature existed populate `started_at`/`completed_at` on their next normal scan, with no separate backfill/trigger | AC-SDD-LEAD-TIME-12 | P1 | [ ] | See TC-SDD-LEAD-TIME-8 |

---

## Category 7 — i18n / Messaging / Operational Observability

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 7.1 | No success/internal message is leaked to the user in place of the new fields (only date/duration text or `-`) | N/A | P1 | [x] SKIP | Fields are pure data display, no message/toast introduced |
| 7.2 | All 3 new labels (created time, updated time, duration) are i18n-translated via `Pages.PmDashboard.<key>`, present in `en`/`vi`/`ja`, no hardcoded text | AC-SDD-LEAD-TIME-7 | P0 | [ ] | See TC-SDD-LEAD-TIME-1; verify key presence in all 3 locale files |
| 7.3 | Malformed/missing date-field cases log at most a warning server-side, never escalate to full scan failure or a client-facing error | AC-SDD-LEAD-TIME-6 | P0 | [ ] | See TC-SDD-LEAD-TIME-3; verify via scan run outcome, not internal log content |

---

## Sign-off

| Role | Name | Date | Result |
|---|---|---|---|
| QA Lead |  |  |  |
| Developer |  |  |  |
| PM/BA |  |  |  |

> Release gate rule: all P0 checklist items must be checked.
