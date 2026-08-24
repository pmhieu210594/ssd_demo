# Black-box Review Checklist

**Ticket ID**: PHASE-DWELL-TIME
**Create date**: 2026-08-21
**Author**: TBD  
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
| 1.1 | {{default-state-or-threshold-boundary}} | AC-PHASE-DWELL-TIME-... | P0 | [ ] | |
| 1.2 | {{reset-behavior-or-state-combination}} | AC-PHASE-DWELL-TIME-... | P0 | [ ] | |
| 1.3 | {{exact-threshold-equality-boundary}} | AC-PHASE-DWELL-TIME-... | P1 | [ ] | |

---

## Category 2 — Permission & Access Control

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 2.1 | {{forbidden-access-behavior}} | AC-PHASE-DWELL-TIME-... | P0 | [ ] | |
| 2.2 | {{unauthenticated-access-behavior}} | AC-PHASE-DWELL-TIME-... | P0 | [ ] | |
| 2.3 | {{role-based-ui-or-api-guard}} | AC-PHASE-DWELL-TIME-... | P0 | [ ] | |

---

## Category 3 — Compatibility & Contract Stability

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 3.1 | {{legacy-contract-remains-compatible}} | AC-PHASE-DWELL-TIME-... | P0 | [ ] | |
| 3.2 | {{success-envelope-or-payload-shape-stable}} | AC-PHASE-DWELL-TIME-... | P0 | [ ] | |
| 3.3 | {{validation-error-contract-stable}} | AC-PHASE-DWELL-TIME-... | P0 | [ ] | |

---

## Category 4 — Exception Handling & Resilience

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 4.1 | {{partial-failure-isolated-correctly}} | AC-PHASE-DWELL-TIME-... | P0 | [ ] | |
| 4.2 | {{retry-or-recovery-path-works}} | AC-PHASE-DWELL-TIME-... | P0 | [ ] | |
| 4.3 | {{empty-state-or-no-data-handled-clearly}} | AC-PHASE-DWELL-TIME-... | P1 | [ ] | |

---

## Category 5 — Performance / Degradation Signals (Black-box observable)

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 5.1 | {{parallel-load-or-lazy-load-observable}} | AC-PHASE-DWELL-TIME-... | P1 | [ ] | |
| 5.2 | {{large-data-remains-usable}} | AC-PHASE-DWELL-TIME-... | P1 | [ ] | |
| 5.3 | {{no-visible-freeze-or-reload-loop}} | AC-PHASE-DWELL-TIME-... | P1 | [ ] | |

---

## Category 6 — Business Rule Integrity

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 6.1 | {{core-counting-or-calculation-rule}} | AC-PHASE-DWELL-TIME-... | P0 | [ ] | |
| 6.2 | {{filtering-or-exclusion-rule}} | AC-PHASE-DWELL-TIME-... | P0 | [ ] | |
| 6.3 | {{derived-status-or-state-rule}} | AC-PHASE-DWELL-TIME-... | P0 | [ ] | |

---

## Category 7 — i18n / Messaging / Operational Observability

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 7.1 | {{success-message-not-leaked-to-user}} | AC-PHASE-DWELL-TIME-... | P1 | [ ] | |
| 7.2 | {{error-message-localized-or-fallback-defined}} | AC-PHASE-DWELL-TIME-... | P1 | [ ] | |
| 7.3 | {{machine-readable-error-for-fe}} | AC-PHASE-DWELL-TIME-... | P0 | [ ] | |

---

## Sign-off

| Role | Name | Date | Result |
|---|---|---|---|
| QA Lead |  |  |  |
| Developer |  |  |  |
| PM/BA |  |  |  |

> Release gate rule: all P0 checklist items must be checked.
