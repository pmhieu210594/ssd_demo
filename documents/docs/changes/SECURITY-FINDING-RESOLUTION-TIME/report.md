# Report — SECURITY-FINDING-RESOLUTION-TIME (Cleaned)

**Ticket ID**: SECURITY-FINDING-RESOLUTION-TIME
**Create date**: 2026-08-18
**Author**: Claude (SDD Reporter)
**Update date**: 2026-08-18 (bản dựng lại sau khi `human-review.md` được APPROVED và `ai-review.md` chạy STEP F1)

---

## 1. Modification Summary

- Bổ sung field mới **`resolutionTime`** vào response của API `GET /api/v1/security/dashboard/tickets/{ticketId}`.  
- Hiển thị trong `SecurityTicketDetailDrawer.tsx`.  
- Công thức mới:  
  - Lấy lịch sử từ `tbl_fact_security_scan` với `scan_status='FAIL'`.  
  - Sắp xếp theo `collected_at ASC`.  
  - Nếu không có FAIL → `"-"`.  
  - Nếu chỉ có 1 FAIL → `00:00:00`.  
  - Nếu có ≥2 FAIL → `last_fail.collected_at − first_fail.collected_at`.  
- Định dạng `HH:mm:ss`, HH không giới hạn 24.  
- Additive change: không migration DB, không endpoint mới, không đổi contract khác.  

---

## 2. Impact Analysis

**Files changed:**  
- BE: `SecurityDashboardModels.java`, `SecurityFindingResolutionTimeCalculator.java` (mới), `SecurityDashboardJdbcAdapter.java`, `SecurityDashboardDtos.java`.  
- FE: `types.ts`, `SecurityTicketDetailDrawer.tsx`, 3 file `locale.json`.  
- Tests: `SecurityFindingResolutionTimeCalculatorTest.java`, `SecurityDashboardPage.test.tsx`.  

**Database:**  
- Chỉ đọc từ `tbl_fact_security_scan`.  
- Không migration mới.  
- Index `(ticket_id, scan_status, collected_at)` chưa xác nhận (OI-INDEX).  

**API:**  
- Mở rộng response JSON của endpoint hiện có.  
- Không có route mới.  

**Settings / Logs / Permissions:**  
- Không thêm config mới.  
- Không thêm log mới.  
- Permission gate giữ nguyên (`requireSecurityAccess`).  

---

## 3. Review Results

- **Self-review:** Điền đầy đủ, ghi nhận vấn đề môi trường test ban đầu.  
- **Independent AI Review:** 0 Blocker, 2 Major đã xử lý, 3 Minor còn mở (F3 defensive check, F4 checklist query, F5 fallback undefined). Verdict: Approve with comments.  
- **Human Review:** APPROVED (2026-08-18). Tất cả Human Decisions đã Resolved.  

---

## 4. Test Results

- **BE Unit:**  
  - `SecurityFindingResolutionTimeCalculatorTest`: PASS (8/8).  
  - `SecurityDashboardServiceTest`: PASS (12/12).  
- **FE Unit:**  
  - `SecurityDashboardPage.test.tsx`: PASS (6/6).  
  - Bổ sung test hiển thị `"-"`: PASS.  
- **Integration Test:** Không có (Accepted Risk).  
- **Black-box:** Test cases thiết kế sẵn (BB-001→BB-016), chưa có log thực thi.  
- **E2E:** Không cần, flow đơn giản đã cover bằng FE UT.  

---

## 5. Remaining Issues & Next Actions

- **Open Issues:**  
  - F3 defensive check khi input không sort.  
  - F4 checklist query pre-existing.  
  - F5 thiếu test fallback `undefined`.  
  - `review-checklist.md` chưa điền Result.  
- **Accepted Risks:**  
  - Không có integration test cho SQL mới.  
  - Index chưa xác nhận.  
  - Data retention chưa xác minh.  
  - Tie-break khi `collected_at` trùng nhau.  
  - Chưa có test tự động đổi locale vi/ja.  
  - `ArchitectureTest` không tồn tại.  

**Next actions:**  
1. Điền `review-checklist.md` retroactive.  
2. Thực hiện `EXPLAIN` cho OI-INDEX, xác minh staging cho OI-DATA-RETENTION.  
3. Bổ sung test tự động cho đổi ngôn ngữ vi/ja.  
4. Mở ticket riêng cho F4 (checklist query).  

---

## 6. Rollback Procedure

- Rollback = revert commit/PR.  
- Không có migration DB, không có dữ liệu cần backfill.  
- Không ảnh hưởng permission hoặc config.  

---

## Final Verdict

**APPROVED** — tất cả AC PASS, không còn Blocker.  
Các rủi ro còn lại đã được ký nhận là Accepted Risk.  
Follow-up Minor findings và thủ tục sẽ được xử lý sau merge.

---

## References

- [spec-pack.md]  
- [impl-plan.md]  
- [review-checklist.md]  
- [self-review.md]  
- [test-plan.md]  
- [test-results.md]  
- [blackbox-testcases.md]  
- [test-data.md]  
