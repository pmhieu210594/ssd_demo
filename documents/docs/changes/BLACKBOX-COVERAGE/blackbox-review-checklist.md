# Black-box Review Checklist — BLACKBOX-COVERAGE

**Ticket ID**: BLACKBOX-COVERAGE  
**Create date**: 2026-09-07  
**Author**: Claude  
**Update date**: 2026-09-07  

---

## How to use
- Reviewer đánh dấu PASS / FAIL / SKIP (có lý do).  
- Mọi FAIL ở P0 chặn release.  
- P1/P2 gap phải có ticket follow-up hoặc risk acceptance note.  

---

## Category 1 — Boundary & Edge Combinations

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 1.1 | Mẫu số = 0 riêng lẻ (AC/obs/case) | AC-BLACKBOX-COVERAGE-3/v1 | P0 | [ ] | |
| 1.2 | Tử số = mẫu số (100%) và tử số = 0 (0%) | AC-BLACKBOX-COVERAGE-6/v1 | P0 | [ ] | |
| 1.3 | Rounding giá trị thập phân dài | AC-BLACKBOX-COVERAGE-6/v1 | P1 | [ ] | |
| 1.4 | Ranh giới case map PASS hết vs không PASS hết | AC-BLACKBOX-COVERAGE-1/v1 | P1 | [ ] | |
| 1.5 | Obs chỉ có RELATED, không có DIRECT | AC-BLACKBOX-COVERAGE-4/v1 | P1 | [ ] | |

---

## Category 2 — Permission & Access Control

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 2.1 | Không có token → 401 | AC-BLACKBOX-COVERAGE-8/v1 | P0 | [ ] | |
| 2.2 | Sai role trên đúng project → 403 | AC-BLACKBOX-COVERAGE-8/v1 | P0 | [ ] | |
| 2.3 | Role đúng nhưng project khác → 403 | AC-BLACKBOX-COVERAGE-8/v1 | P1 | [ ] | |
| 2.4 | ADMIN bypass vẫn đúng dữ liệu | AC-BLACKBOX-COVERAGE-8/v1 | P1 | [ ] | |
| 2.5 | Chỉ kiểm HTTP status + body, không giả định logic nội bộ | AC-BLACKBOX-COVERAGE-8/v1 | P0 | [ ] | |

---

## Category 3 — Compatibility & Contract Stability

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 3.1 | Field `blackboxCoveragePercent` giữ nguyên tên/kiểu/vị trí | AC-BLACKBOX-COVERAGE-8/v1 | P0 | [ ] | |
| 3.2 | Không phát sinh field API mới riêng | AC-BLACKBOX-COVERAGE-8/v1 | P0 | [ ] | |
| 3.3 | Field AC-Test Coverage không đổi | AC-BLACKBOX-COVERAGE-7/v1 | P0 | [ ] | |
| 3.4 | Nhiều ticket cùng project không lẫn dữ liệu | AC-BLACKBOX-COVERAGE-3/v1 | P2 | [ ] | |
| 3.5 | Format lỗi 401/403 theo ErrorResponse hiện có | AC-BLACKBOX-COVERAGE-8/v1 | P0 | [ ] | |

---

## Category 4 — Exception Handling & Resilience

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 4.1 | Ticket chưa có testcases → trả 0.0, không lỗi | AC-BLACKBOX-COVERAGE-1/v1 | P0 | [ ] | |
| 4.2 | Ticket có design nhưng chưa chạy test → Execution=0, giá trị hợp lý | AC-BLACKBOX-COVERAGE-2/v1 | P0 | [ ] | |
| 4.3 | Case trạng thái không xác định xử lý như NOT_RUN | AC-BLACKBOX-COVERAGE-2/v1 | P1 | [ ] | |
| 4.4 | SKIP và NOT_RUN không tính PASS, thử riêng biệt | AC-BLACKBOX-COVERAGE-2/v1 | P1 | [ ] | |
| 4.5 | Giá trị trả về không bao giờ NaN/null | AC-BLACKBOX-COVERAGE-6/v1 | P0 | [ ] | |

---

## Category 5 — Performance / Degradation Signals (Black-box observable)

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 5.1 | Không giả định số lần query | AC-BLACKBOX-COVERAGE-3/v1 | P2 | [ ] | |
| 5.2 | Nhiều ticket/nhiều lần gọi liên tiếp không làm chậm response | AC-BLACKBOX-COVERAGE-3/v1 | P2 | [ ] | |
| 5.3 | Case nhiều ticket chỉ xác nhận đúng dữ liệu, không đo perf | AC-BLACKBOX-COVERAGE-7/v1 | P2 | [ ] | |

---

## Category 6 — Business Rule Integrity

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 6.1 | Công thức coverage tính đúng | AC-BLACKBOX-COVERAGE-5/v1 | P0 | [ ] | |
| 6.2 | Priority weight áp dụng đúng | AC-BLACKBOX-COVERAGE-4/v1 | P1 | [ ] | |
| 6.3 | Obs DIRECT mới được tính | AC-BLACKBOX-COVERAGE-4/v1 | P1 | [ ] | |

---

## Category 7 — i18n / Messaging / Operational Observability

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 7.1 | Error message theo format chuẩn, không leak | AC-BLACKBOX-COVERAGE-8/v1 | P0 | [ ] | |
| 7.2 | Error message có fallback/localization | AC-BLACKBOX-COVERAGE-8/v1 | P1 | [ ] | |
| 7.3 | Error machine-readable cho FE | AC-BLACKBOX-COVERAGE-8/v1 | P0 | [ ] | |

---

## Sign-off

| Role | Name | Date | Result |
|---|---|---|---|
| QA Lead |  |  |  |
| Developer |  |  |  |
| PM/BA |  |  |  |

> **Release gate rule**: tất cả checklist P0 phải PASS trước khi release.
