# Black-box Review Checklist

**Ticket ID**: AI-QUALITY
**Create date**: 2026-08-18 08:10:54
**Author**: BRYCENVN\nvt_dung
**Update date**: 2026-08-18 08:10:54

---

## How to use

- Each reviewer marks PASS / FAIL / SKIP (with justification).
- Any FAIL P0 item blocks release.
- P1/P2 gaps must have a documented follow-up ticket or risk acceptance note.

---

## Category 1 — Boundary & Edge Combinations

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 1.1 | `ai_quality_rate` accepts both inclusive bounds 0.00 and 100.00 | AC-AI-QUALITY-5 | P0 | [ ] | BB-005, BB-006 |
| 1.2 | `ai_quality_rate` rejects values just outside the range (-0.01, 100.01) and null | AC-AI-QUALITY-5 | P0 | [ ] | BB-007, BB-008, BB-009 |
| 1.3 | `ai_quality_rate` rejects scale > 2 (e.g. 50.555) rather than silently rounding | AC-AI-QUALITY-5 | P1 | [ ] | BB-010 |

---

## Category 2 — Permission & Access Control

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 2.1 | NONE (no project role, not ADMIN) is forbidden on every endpoint, including read | AC-AI-QUALITY-11 | P0 | [ ] | BB-018 |
| 2.2 | VIEW_ONLY (DEV project role) is forbidden on write (create/update/delete) but allowed on read | AC-AI-QUALITY-10 | P0 | [ ] | BB-016, BB-017 |
| 2.3 | Global ADMIN receives MUTATE regardless of per-project role; RBAC is enforced server-side, not only via FE gating | AC-AI-QUALITY-12 | P0 | [ ] | BB-019; per 30-security.md, backend is authoritative |

---

## Category 3 — Compatibility & Contract Stability

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 3.1 | No existing endpoint contract (e.g. Ticket Bug Metrics, other `PageResult<T>` list endpoints) is changed by this feature | — (impact-analysis §14) | P0 | [ ] | Purely additive route surface under `/api/v1/ai-qualities` |
| 3.2 | Success list response matches `PageResult<T>` shape: `items/page/size/totalElements/totalPages` | AC-AI-QUALITY-9 | P0 | [ ] | BB-015 |
| 3.3 | Validation/error responses match `ErrorResponse(timestamp, status, errorCode, message, traceId)` exactly, for every error case (400/403/404/409) | — (§6.4, §13) | P0 | [ ] | BB-023 |

---

## Category 4 — Exception Handling & Resilience

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 4.1 | 400 (validation), 403 (forbidden), 404 (not found), and 409 (conflict) are each returned in their own distinct, non-overlapping scenarios (no case incorrectly returns 500) | AC-AI-QUALITY-2, -3, -4, -10, -11 | P0 | [ ] | BB-002, BB-003, BB-004, BB-016, BB-018, BB-021, BB-022 |
| 4.2 | Get/Update/Delete on a non-existent id and on an already-soft-deleted id both return 404 with no distinguishable information leak between the two | — (§6.4, BR-4) | P0 | [ ] | BB-022 |
| 4.3 | List endpoint returns a well-formed empty `PageResult` (not an error) when no rows match the filters | AC-AI-QUALITY-9 | P1 | [ ] | Not yet covered by a dedicated BB case — recommend adding if gap confirmed during execution |

---

## Category 5 — Performance / Degradation Signals (Black-box observable)

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 5.1 | List endpoint responds within existing `PageResult<T>` platform norms for typical page/size values | — (§6.6 Non-functional) | P1 | [ ] | No new performance requirement beyond existing conventions per spec-pack §6.6 |
| 5.2 | Large result sets remain paginated correctly (no unbounded `items` array regardless of `size` requested) | AC-AI-QUALITY-9 | P1 | [ ] | Verify `size` is respected/clamped as in `search_clampsPageSizeToBounds` (test-plan.md §4) |
| 5.3 | N/A — no client-facing polling/lazy-load/infinite-loop surface exists in this feature | — | P1 | [ ] SKIP | This ticket is synchronous CRUD only, no batch/job/event processing (impact-analysis §10) |

---

## Category 6 — Business Rule Integrity

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 6.1 | Exactly one active row per ticket is enforced — a second create for the same active ticket never succeeds | AC-AI-QUALITY-2 | P0 | [ ] | BR-1/BR-2; BB-002 |
| 6.2 | `repository_id` must belong to `project_id`, and `ticket_id` must belong to `repository_id` — mismatches are rejected, never silently corrected | AC-AI-QUALITY-3, AC-AI-QUALITY-4 | P0 | [ ] | BR-3; BB-003, BB-004 |
| 6.3 | Soft-deleted rows are excluded from get-by-id and list results, and a ticket may be re-created after its prior row was soft-deleted | AC-AI-QUALITY-7, AC-AI-QUALITY-8 | P0 | [ ] | BR-4; BB-012, BB-013 |

---

## Category 7 — i18n / Messaging / Operational Observability

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 7.1 | Success responses never leak an internal/technical success message to the end user (raw DTOs only, per A-AI-QUALITY-1) | — (spec-pack §2.2, §17) | P1 | [ ] | No `ApiResponse<T>` envelope with embedded human-readable messages is introduced |
| 7.2 | Every validation/conflict/forbidden/not-found error carries a stable machine-readable `errorCode`, which the FE is expected to localize (no Spring `MessageSource` server-side i18n) | — (§17 A-AI-QUALITY-2/8) | P1 | [ ] | BB-023 |
| 7.3 | Error responses always include a `traceId` and never a stack trace or internal exception class name | — (§13) | P0 | [ ] | BB-023 |

---

## Sign-off

| Role | Name | Date | Result |
|---|---|---|---|
| QA Lead | | | |
| Developer | | | |
| PM/BA | | | |

> Release gate rule: all P0 checklist items must be checked.
