# spec-pack

**Ticket ID**: SECURITY-FINDING-RESOLUTION-TIME
**Create date**: 2026-08-17
**Author**: Claude
**Update date**: 2026-08-17

---

## 1. Tổng quan
Thêm field **"Security Finding Resolution Time"** vào `SecurityTicketDetailDrawer.tsx`, hiển thị tổng thời gian xử lý các security finding từ dữ liệu `tbl_fact_security_scan`. Không tạo bảng/pipeline mới, không có endpoint mới.

---

## 2. Bối cảnh / mục tiêu
- **As-Is:** Dashboard hiện chưa hiển thị thời gian xử lý finding.  
- **To-Be:**  
  1. Lấy lịch sử `tbl_fact_security_scan` với `scan_status = 'FAIL'`.  
  2. Tính `resolutionTime = last_fail.collected_at − first_fail.collected_at`.  
  3. Hiển thị dạng `HH:mm:ss`, hoặc `"-"` nếu không có bản ghi FAIL.  

Logic đặt ở tầng `application/usecase` (`SecurityDashboardService`), không đặt trong SQL adapter.

---

## 3. Phạm vi
- FE: thêm field hiển thị trong `SecurityTicketDetailDrawer.tsx`.  
- BE: mở rộng `SecurityTicketDetail` và response API `/tickets/{ticketId}` với field `resolutionTime`.  
- Định dạng `HH:mm:ss`, đa ngôn ngữ (EN/VI/JP).  
- Không ghi/sửa dữ liệu.

---

## 5. Thuật ngữ / Business Rules
- **Nguồn dữ liệu:** `tbl_fact_security_scan` với `scan_status = 'FAIL'`.  
- **First FAIL scan:** bản ghi FAIL sớm nhất.  
- **Last FAIL scan:** bản ghi FAIL muộn nhất.  
- **Công thức:** `last_fail − first_fail`.  
- Nếu chỉ có 1 FAIL → `00:00:00`.  
- Nếu không có FAIL → `"-"`.  
- Định dạng `HH:mm:ss`, `HH` không giới hạn 24.  
- Read-only.

---

## 6. Acceptance Criteria
- **AC-SECFINDRES-1:** ≥2 FAIL → hiển thị đúng chênh lệch.  
- **AC-SECFINDRES-2:** 0 FAIL → hiển thị `"-"`.  
- **AC-SECFINDRES-3:** 1 FAIL → hiển thị `00:00:00`.  
- **AC-SECFINDRES-4:** Đa ngôn ngữ EN/VI/JP.  
- **AC-SECFINDRES-5:** Field xuất hiện trong Drawer, không phá layout.  
- **AC-SECFINDRES-6:** Không ghi dữ liệu.  
- **AC-SECFINDRES-9:** HH hiển thị không giới hạn 24.

---

## 7. Input / Output
- **Input:** ticketId (UUID).  
- **Output:** `resolutionTime` (string `HH:mm:ss` hoặc `"-"`).

---

## 8. Ảnh hưởng
- **FE:** thêm field hiển thị, đa ngôn ngữ.  
- **BE:** mở rộng DTO và service logic.  
- **DB:** chỉ SELECT từ `tbl_fact_security_scan`.  
- Không có batch/event mới.

---

## 9. FE/BE contract
- BE: thêm field `resolutionTime` vào DTO.  
- Logic tính ở `SecurityDashboardService`.  
- FE: hiển thị field, dùng i18n cho label.  
- Additive change, không breaking.

---

## 10. Validation
- 0 FAIL → `"-"`.  
- 1 FAIL → `00:00:00`.  
- ≥2 FAIL → chênh lệch first/last.  
- DB unavailable → 500 theo handler hiện có.

---

## 11. Security / Privacy
- Read-only, không thêm quyền mới.  
- Không dữ liệu nhạy cảm mới.  
- Tuân thủ security guideline hiện có.

---

## 12. Operation / Logging
- Tái sử dụng logging/monitoring hiện có.  
- Không có job/cron mới.

---

## 13. Test Strategy
- Unit test BE: các case 0/1/≥2 FAIL.  
- Adapter test: truy vấn đúng điều kiện.  
- FE test: hiển thị đúng giá trị và label.  
- Black-box test: tạo sau khi có impl-plan.

---

## 16. Assumptions
- DTO key: `resolutionTime`.  
- Định dạng `HH:mm:ss`, HH không giới hạn.  
- Logic ở application layer.  
- `scan_status = 'FAIL'` áp dụng cho mọi scanner.  
- Lịch sử scan đầy đủ (cần xác minh).

---

## 17. Open Issues
- **OI-INDEX:** cần xác nhận index phù hợp cho truy vấn.  
- **OI-DATA-RETENTION:** cần xác minh dữ liệu không bị archive.  
- **OI-6:** SLA/cảnh báo severity (deferred).

---

## 18. Human Decisions
- **H-SECFINDRES-1:** DTO key casing → `resolutionTime`.  
- **H-SECFINDRES-2:** HH không giới hạn 24.  
- **H-SECFINDRES-5:** Giữ nguyên code ẩn dòng SAST trong Scans.  
- **H-SECFINDRES-6:** BE trả về string đã format.  
- **H-SECFINDRES-7:** Tie-break khi collected_at trùng → chấp nhận rủi ro.  
- **H-SECFINDRES-8:** Đổi công thức sang first/last FAIL, bỏ multi-cycle.

---

## Trạng thái
Hoàn tất — tất cả quyết định đã được chốt. Còn lại một số open issue kỹ thuật (index, data retention) và SLA deferred.