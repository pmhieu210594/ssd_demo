# Raw Input

**Ticket ID**: SDD-LEAD-TIME
**Create date**: 2026-08-21
**Author**: nvt_dung
**Update date**: 2026-08-21

## Ticket Body

Lấy thời gian tạo (created time) của file spec-pack.md và thời gian cập nhật (updated time) của file report.md để hiển thị tại khu vực Chi tiết Ticket (Ticket Details) trên màn hình Dashboard Quản lý dự án (pm-dashboard).

## Requirement Notes

- **Màn hình tác động**: `pm-dashboard` (Màn hình Dashboard Quản lý dự án).
- **Vị trí hiển thị**: Khung chi tiết ticket (Ticket Detail drawer).
- **Luồng dữ liệu & Xuất xứ thông tin**:
  - File `spec-pack.md`: Lấy thuộc tính *Create Date*.
  - File `report.md`: Lấy thuộc tính *Update Date*.
- **Lưu dữ liệu vào database**: 
  - *Created Date* ở file `spec-pack.md` được lưu vào `tbl_dim_ticket.started_at` (cột này đã có nên không cần tạo)
  - *Update Date* ở file `report.md` được lưu vào `tbl_dim_ticket.completed_at` (cột này chưa có nên tạo thêm tương tự như cột `tbl_dim_ticket.started_at`, tham khảo hệ thống để tạo migration)
- **Hiển thị UI**: Hiển thị rõ ràng định dạng ngày giờ (ví dụ: `YYYY-MM-DD HH:mm:ss`) tương ứng với từng mốc thời gian của hai file trên tại màn hình thông tin ticket.
  - hiển thị 2 giá trị ngày ở TicketInformationCard của file TicketDetailDrawer.tsx
  - tham khảo hệ thống cũ để áp dụng đa ngôn ngữ i18n
- **Xử lý ngoại lệ**:
  - Nếu một trong hai file chưa tồn tại hoặc chưa được khởi tạo, hiển thị giá trị mặc định (ví dụ: `-`).

## Meeting Notes

## Customer Comments

- Cần nhìn thấy chính xác mốc thời gian file spec được tạo và báo cáo cập nhật gần nhất ngay tại màn hình PM Dashboard để tiện theo dõi tiến độ công việc mà không cần mở trực tiếp kho lưu trữ file.

## Raw References

- Tham khảo kiến trúc và luồng xử lý của hệ thống hiện tại về việc quản lý file, API đọc metadata file cũng như component hiển thị chi tiết ticket trên màn hình `pm-dashboard`.
- File liên quan: `spec-pack.md`, `report.md`.

## Notes
