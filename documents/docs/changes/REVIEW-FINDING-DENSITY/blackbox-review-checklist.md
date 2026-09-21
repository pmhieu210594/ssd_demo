# Black-box Review Checklist

**Ticket ID**: REVIEW-FINDING-DENSITY
**Create date**: 2026-09-15 16:48:01
**Author**: nvt_dung
**Update date**: 2026-09-15 16:48:01

---

## How to use

- Each reviewer marks PASS / FAIL / SKIP (with justification).
- Any FAIL P0 item blocks release.
- P1/P2 gaps must have a documented follow-up ticket or risk acceptance note.

---

## Category 1 — Boundary & Edge Combinations

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 1.1 | Tổng changed lines của các PR liên kết = 0 (kể cả chưa có PR liên kết) → hiển thị `N/A`, không hiển thị `0` | AC-DENSITY-CALC-3 | P0 | [ ] | Xem `BB-RFD-DENSITY-03` |
| 1.2 | 0 finding hợp lệ nhưng changed lines > 0 → hiển thị `0 findings/KLOC`, không lẫn với N/A | AC-DENSITY-CALC-4 | P0 | [ ] | Xem `BB-RFD-DENSITY-04` |
| 1.3 | Rounding đúng biên `HALF_UP` (5.25→5.3, 5.24→5.2, và biên `.x50` khác) | AC-DENSITY-CALC-6 | P1 | [ ] | Xem `BB-RFD-DENSITY-05`, `BD-6` |

---

## Category 2 — Permission & Access Control

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 2.1 | PM đọc được `GET /tickets/{ticketId}/detail` với field `reviewFindingDensity` mới, không bị chặn quyền | Spec §6.3 (Bảo mật/quyền hiện có, không tạo cơ chế mới) | P0 | [ ] | Smoke only — không kiểm thử toàn bộ ma trận role |
| 2.2 | QA đọc được tương tự PM (cùng permission gate hiện có `AuthUserContext`) | Spec §6.3 | P1 | [ ] | Smoke only |
| 2.3 | Không có role mới hoặc quyền mở rộng nào được tạo ra ngoài phạm vi ticket này | Spec §2.2 (không redesign phân quyền) | P1 | [ ] | Xác nhận qua review code review/response contract, không phải test case riêng |

---

## Category 3 — Compatibility & Contract Stability

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 3.1 | Response `GET /tickets/{ticketId}/detail` cũ (client cũ không biết field mới) vẫn hoạt động bình thường — field mới chỉ được thêm, field cũ không đổi/xóa | AC-REG-1, Spec §7 (API Contract Impact) | P0 | [ ] | Xem `BB-RFD-REG-01` |
| 3.2 | Shape JSON `reviewFindingDensity.{density, unit, totalFindings, totalChangedLines, breakdown.{open,resolved}}` đúng như spec, `density` là số hoặc `null` (không phải chuỗi `"N/A"`) | AC-DENSITY-CALC-2, -3 | P0 | [ ] | FE tự format N/A, không phải BE trả chuỗi |
| 3.3 | Dashboard list / ticket row không bị vỡ sau khi response `/detail` thay đổi shape | AC-REG-1 | P0 | [ ] | Xem `BB-RFD-REG-01` |

---

## Category 4 — Exception Handling & Resilience

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 4.1 | Một PR trong ticket nhiều PR không đọc được finding/changed-lines → hệ thống trả kết quả một phần hoặc `N/A` cho phần đó, không lỗi toàn bộ Ticket Detail | Spec NFR §6.3 | P0 | [ ] | Dữ liệu `ED-3`; cần môi trường giả lập lỗi nguồn dữ liệu |
| 4.2 | Review state trống/không nhận diện không gây lỗi hệ thống, chỉ đơn giản không tạo finding | AC-FINDING-CLASSIFY-2 | P1 | [ ] | Xem `BB-RFD-CLASSIFY-02` |
| 4.3 | Ticket chưa có PR liên kết vẫn hiển thị `N/A` rõ ràng, không phải màn hình lỗi/trắng | AC-DENSITY-CALC-3 | P1 | [ ] | Xem `BB-RFD-DENSITY-03` biến thể B |

---

## Category 5 — Performance / Degradation Signals (Black-box observable)

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 5.1 | Mở lại Ticket Detail nhiều lần không có độ trễ rõ rệt do gọi trực tiếp GitHub API mỗi lần mở màn hình | Spec NFR §6.1 | P1 | [ ] | Quan sát gián tiếp qua thời gian phản hồi `/detail`; không đo benchmark hạ tầng chi tiết |
| 5.2 | Ticket có nhiều PR liên kết (aggregation) vẫn trả kết quả trong thời gian hợp lý, không timeout | Spec NFR §6.1 | P1 | [ ] | Dữ liệu `TCK-2` |
| 5.3 | Không có hiện tượng đơ/loop khi mở Ticket Detail lặp lại sau nhiều lần đồng bộ PR liên tiếp | Spec §5.4 (Recalculation) | P1 | [ ] | Quan sát qua UI, không assert log nội bộ |

---

## Category 6 — Business Rule Integrity

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 6.1 | Công thức density = tổng finding / tổng changed lines × 1000, không lấy trung bình per-PR | AC-DENSITY-CALC-2 | P0 | [ ] | Xem `BB-RFD-DENSITY-02`, dữ liệu `ND-2b` (cố ý chọn để 2 cách tính ra kết quả khác nhau) |
| 6.2 | Rule metadata-only: thread nội dung câu hỏi/LGTM dưới review state hợp lệ vẫn tính là finding, không lọc theo nội dung | AC-FINDING-CLASSIFY-3 | P0 | [ ] | Xem `BB-RFD-CLASSIFY-03` |
| 6.3 | Dedupe đúng theo thread identity: nhiều comment cùng thread → 1 finding; nhiều thread → nhiều finding | AC-FINDING-CLASSIFY-4, -5 | P0 | [ ] | Xem `BB-RFD-CLASSIFY-01`, `BB-RFD-CLASSIFY-04` |

---

## Category 7 — i18n / Messaging / Operational Observability

| # | Check item | AC references | Priority | Status | Notes |
|---|---|---|---|---|---|
| 7.1 | Giá trị `"N/A"` và `"0 findings/KLOC"` hiển thị rõ ràng, không gây hiểu nhầm lẫn nhau | AC-DENSITY-CALC-3, -4 | P1 | [ ] | Xem `BB-RFD-DENSITY-03`, `BB-RFD-DENSITY-04` |
| 7.2 | Log "Finding created" được ghi khi review comment/thread được phân loại là finding, đủ để audit (`pr_id`, `head_sha`, thời điểm) | Spec §5.X, NFR §6.4 | P2 | [ ] | Quan sát gián tiếp qua log/telemetry nếu có công cụ xem log trong phạm vi test; nếu không có công cụ, đánh SKIP kèm lý do (giới hạn hạ tầng, theo `test-plan.md` mục 8 #1) |
| 7.3 | Log "Density recalculated" được ghi khi PR có commit mới và density tính lại | Spec §5.X, NFR §6.4 | P2 | [ ] | Tương tự 7.2 — SKIP nếu không có công cụ xem log, ghi rõ lý do |

---

## Sign-off

| Role | Name | Date | Result |
|---|---|---|---|
| QA Lead |  |  |  |
| Developer |  |  |  |
| PM/BA |  |  |  |

> Release gate rule: all P0 checklist items must be checked.
