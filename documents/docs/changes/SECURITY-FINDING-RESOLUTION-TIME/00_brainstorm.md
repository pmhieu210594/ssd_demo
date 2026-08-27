# 00_brainstorm

**Ticket ID**: SECURITY-FINDING-RESOLUTION-TIME
**Create date**: 2026-08-17
**Author**: Claude (SDD analyst)
**Update date**: 2026-08-17

## Purpose

Bổ sung field mới **"Security Finding Resolution Time"** vào màn hình chi tiết ticket của Security Dashboard (`SecurityTicketDetailDrawer`), tính thời gian xử lý (resolution time) của các security finding dựa trên dữ liệu đã có sẵn — không cần pipeline ghi dữ liệu mới.

## Known Information

- Ticket là field/metric mới bổ sung vào `docs/changes/SECURITY-DASHBOARD/`, không phải màn hình độc lập.  
- **Nguồn dữ liệu duy nhất:** bảng `tbl_fact_security_scan` — lưu trữ kết quả tổng hợp của các lần quét bảo mật (Secret Scan, SAST, SCA) từ GitHub Actions mỗi lần có push.  
- Công thức mới:  
  - Lấy lịch sử scan theo `ticket_id` với điều kiện `scan_status = 'FAIL'`.  
  - Sắp xếp theo `collected_at` tăng dần.  
  - Nếu không có bản ghi `FAIL` nào → hiển thị `"-"`.  
  - Nếu chỉ có đúng 1 bản ghi `FAIL` → hiển thị `00:00:00`.  
  - Nếu có từ 2 bản ghi `FAIL` trở lên → tính `resolutionTime = last_fail.collected_at − first_fail.collected_at`.  
  - Định dạng `HH:mm:ss`, không giới hạn HH ở 24 (dạng duration).  
- DTO key: `resolutionTime` (camelCase, khớp convention hiện có của `SecurityTicketDetail`).  
- Label: EN "Security Finding Resolution Time", VI "Thời gian xử lý finding bảo mật", JP "セキュリティ指摘解消時間".  
- Vị trí hiển thị: `EDCAP_FE/src/pages/security-dashboard/components/SecurityTicketDetailDrawer.tsx`.  
- Endpoint BE liên quan: `GET /api/v1/security/dashboard/tickets/{ticketId}` (`SecurityDashboardController.java`), trả về `SecurityTicketDetail`.  
- Không tạo bảng/migration/pipeline ghi dữ liệu mới — chỉ đọc dữ liệu đã có.  

## Undetermined Points

- Ngưỡng SLA/cảnh báo theo severity — deferred, ngoài phạm vi phiên bản này.  
- `tbl_fact_security_scan` có index phù hợp cho truy vấn `(ticket_id, scan_status, collected_at)` hay chưa — cần kiểm tra khi viết `impl-plan.md`.  

## Expected Risks

- **Rủi ro business-logic:** nếu dữ liệu `tbl_fact_security_scan` không tích lũy đầy đủ lịch sử theo thời gian (ví dụ bị dọn dẹp/archive), kết quả tính toán sẽ sai lệch.  
- **Rủi ro performance:** truy vấn toàn bộ lịch sử scan theo ticket có thể chậm nếu ticket có nhiều bản ghi và thiếu index phù hợp (`OI-INDEX`).  
- **Rủi ro kiến trúc:** nếu logic tính toán đặt trực tiếp trong SQL adapter sẽ khó unit test — nên đặt ở tầng `application/usecase`.  

## What AI Needs to Investigate

- Đã hoàn tất: xác nhận schema `tbl_fact_security_scan`, loại bỏ các phương án nguồn dữ liệu sai, xác nhận endpoint BE hiện có.  
- Còn lại cho `impl-plan.md`: xác nhận index hiện có trên `tbl_fact_security_scan`, đọc test hiện có để theo đúng pattern, xác định vị trí thêm field trong DTO.  

## What Humans Need to Ask

- Xác nhận có cần bổ sung ngưỡng SLA/cảnh báo (`OI-6`, hiện đang deferred) cho phiên bản này hoặc phiên bản sau.  

## Conditions Under Which Implementation Is Not Permitted

- Không được thêm ngưỡng SLA/cảnh báo màu sắc nếu chưa có quyết định mới.  
- Không được tạo bảng/migration DB mới, không được dùng lại các bảng đã bị loại (`tbl_fact_security_finding`, `tbl_connector_run`, `tbl_fact_finding`).  
- Không được đặt logic tính toán trong tầng `web`/`infrastructure` thuần SQL nếu không có unit test tương ứng ở tầng `application`.  

---
