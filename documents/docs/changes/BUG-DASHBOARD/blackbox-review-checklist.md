# Black-box Review Checklist

**Ticket ID**: BUG-DASHBOARD
**Create date**: 2026-08-07 09:45:00
**Author**: Claude
**Update date**: 2026-08-07 09:45:00

---

## How to use

- Each reviewer marks ✅ pass / ❌ fail / ⏭ skip (with justification).
- Any ❌ fail P0 item blocks release.
- P1/P2 gaps must have a documented follow-up ticket or risk acceptance note.

---

## Category 1 — Boundary & Edge Combinations

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 1.1 | `internalBugCount`/`customerBugCount` = 0 is accepted as a valid, meaningful state | AC-BUG-DASHBOARD-5 | P0 | [ ] | BB-011 |
| 1.2 | `note` at exactly 500 chars is accepted; at 501 chars is rejected | AC-BUG-DASHBOARD-6 | P0 | [ ] | BB-012/BB-013 |
| 1.3 | Whitespace-only `note` is trimmed to null/empty, not stored as literal whitespace | AC-BUG-DASHBOARD-6 | P1 | [ ] | BB-014, OI-BUG-DASHBOARD-5 |

---

## Category 2 — Permission & Access Control

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 2.1 | Non-PM/QA/DEV/ADMIN role is blocked from list/detail, not only mutation | AC-BUG-DASHBOARD-11 | P0 | [ ] | BB-021 |
| 2.2 | Unauthenticated/expired-session caller is blocked from every action | AC-BUG-DASHBOARD-11 | P0 | [ ] | BB-022 |
| 2.3 | DEV can view list/detail but is rejected (403) on create/update/delete | AC-BUG-DASHBOARD-10 | P0 | [ ] | BB-019/BB-020 |

---

## Category 3 — Compatibility & Contract Stability

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 3.1 | N/A — wholly new feature/table, no legacy contract to preserve | — | — | [x] N/A | Per `impact-analysis.md` §14 |
| 3.2 | List/detail success payload matches governance page-DTO shape (`items, page, size, totalElements, totalPages`), not the raw input's illustrative shape | AC-BUG-DASHBOARD-1 | P0 | [ ] | BB-001 |
| 3.3 | Validation error contract is the standard `ErrorResponse` envelope (`timestamp, status, error, message, traceId`) | AC-BUG-DASHBOARD-12 | P0 | [ ] | BB-023 |

---

## Category 4 — Exception Handling & Resilience

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 4.1 | Duplicate active-row create is isolated as a 409 conflict with no partial write | AC-BUG-DASHBOARD-4 | P0 | [ ] | BB-007 |
| 4.2 | Detail/update/delete on a nonexistent or already-deleted ID consistently returns 404 with no mutation | AC-BUG-DASHBOARD-9 | P0 | [ ] | BB-017/BB-018 |
| 4.3 | Empty list state (no active rows) renders a defined empty state, not a broken table | AC-BUG-DASHBOARD-2 | P1 | [ ] | BB-005 |

---

## Category 5 — Performance / Degradation Signals (Black-box observable)

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 5.1 | Cascading Project→Repository→Ticket filter/select loads without visible lag or double-fetch | AC-BUG-DASHBOARD-1 | P1 | [ ] | Low risk — no heavy load path in this feature |
| 5.2 | List with a larger dataset (e.g. 50+ rows) paginates correctly and remains usable | AC-BUG-DASHBOARD-1 | P2 | [ ] | |
| 5.3 | Invalid/omitted `page`/`size` values are clamped, not causing a visible error or freeze | AC-BUG-DASHBOARD-1 | P1 | [ ] | BD-6/BD-7 |

---

## Category 6 — Business Rule Integrity

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 6.1 | Exactly one active row can exist per Ticket at any time (create rejects a second) | AC-BUG-DASHBOARD-4 | P0 | [ ] | BR-1/BR-6, BB-007 |
| 6.2 | Soft-deleted rows are excluded from the default list and normal detail/update flow | AC-BUG-DASHBOARD-8 | P0 | [ ] | BR-4/BR-5, BB-001/BB-016 |
| 6.3 | Repository is never cross-validated against the selected Ticket on create/update | AC-BUG-DASHBOARD-13 | P0 | [ ] | BR-12, BB-004 |

---

## Category 7 — i18n / Messaging / Operational Observability

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 7.1 | Success messages (create/update/delete) are not leaked into error-path UI | AC-BUG-DASHBOARD-3/7/8 | P1 | [ ] | |
| 7.2 | Validation error messages resolve to localized text under `Pages.TicketBugMetrics.*` keys (no raw key/placeholder shown to user) | AC-BUG-DASHBOARD-5/6 | P1 | [ ] | spec-pack.md §20.3 |
| 7.3 | Every rejected request returns a machine-readable error (`status`/`error`/`traceId`), never a raw stack trace | AC-BUG-DASHBOARD-12 | P0 | [ ] | BB-023 |

---

## Sign-off

| Role | Name | Date | Result |
|---|---|---|---|
| QA Lead |  |  |  |
| Developer |  |  |  |
| PM/BA |  |  |  |

> Release gate rule: all P0 checklist items must be checked.
