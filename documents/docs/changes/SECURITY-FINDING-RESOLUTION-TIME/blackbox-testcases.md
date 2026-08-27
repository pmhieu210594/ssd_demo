# blackbox-testcases

**Ticket ID**: SECURITY-FINDING-RESOLUTION-TIME
**Create date**: 2026-08-18
**Author**: Claude (QA Designer)
**Update date**: 2026-08-18

**Cách đọc bảng**: Mỗi test case mô tả thao tác người dùng thật trên màn hình **Security Dashboard → mở chi tiết 1 ticket**. Trường mới được kiểm tra là **"Security Finding Resolution Time"** — hiển thị trong khung chi tiết ticket (drawer).

---

| ID | AC | Viewpoint | Dữ liệu tiền đề | Thủ tục | Expected result | Priority | Note |
|---|---|---|---|---|---|---|---|
| BB-001 | AC-SECFINDRES-1 | Normal | TD-TICKET-A (≥2 FAIL) | Mở chi tiết ticket TD-TICKET-A | Hiển thị đúng `last_fail − first_fail` dạng `HH:mm:ss` (vd `25:00:00`) | P0 | Case cơ bản nhất |
| BB-002 | AC-SECFINDRES-2 | Empty data | TD-TICKET-B (0 FAIL) | Mở chi tiết ticket TD-TICKET-B | Hiển thị `"-"` | P0 | Không nhầm thành `00:00:00` |
| BB-003 | AC-SECFINDRES-3 | Boundary | TD-TICKET-C (1 FAIL duy nhất) | Mở chi tiết ticket TD-TICKET-C | Hiển thị `00:00:00` | P0 | Đúng quy tắc 1 FAIL |
| BB-004 | AC-SECFINDRES-4 | Đa ngôn ngữ | TD-TICKET-A | Đổi ngôn ngữ EN/VI/JP, mở ticket | EN: "Security Finding Resolution Time"; VI: "Thời gian xử lý finding bảo mật"; JP: "セキュリティ指摘解消時間" | P1 | Giá trị số giữ nguyên dạng Latin |
| BB-005 | AC-SECFINDRES-5 | Regression | TD-TICKET-H (đầy đủ 3 khối cũ) | Mở chi tiết ticket TD-TICKET-H | Hiển thị đủ 4 khối: Resolution Time + Scans (bao gồm SAST) + Checklist + Exceptions | P0 | Đã phát hiện lỗi thật: SAST bị ẩn khỏi Scans |
| BB-006 | AC-SECFINDRES-6 | Data integrity | TD-TICKET-A | Mở chi tiết nhiều lần | Dữ liệu quét không thay đổi, chỉ đọc | P0 | Không ghi dữ liệu |
| BB-007 | AC-SECFINDRES-9 | Boundary >24h | TD-TICKET-F (48h) | Mở chi tiết ticket TD-TICKET-F | Hiển thị `48:00:00` | P1 | HH không reset |
| BB-008 | AC-SECFINDRES-9 | Boundary ≥100h | TD-TICKET-G (120h) | Mở chi tiết ticket TD-TICKET-G | Hiển thị `120:00:00` | P2 | HH hiển thị đủ 3 chữ số |
| BB-009 | Boundary nhỏ nhất | TD-TICKET-I (chênh vài giây) | Mở chi tiết ticket TD-TICKET-I | Hiển thị `00:00:03` | P2 | Padding số 0 đúng |
| BB-010 | Permission | TD-USER-NOACCESS | Đăng nhập bằng user không có quyền | Bị từ chối truy cập, không hiển thị dữ liệu | P1 | Không lộ dữ liệu |
| BB-011 | Non-existing ID | Ticket không tồn tại | Mở chi tiết ticket không tồn tại | Hiển thị thông báo "không tìm thấy" | P1 | Không crash, không stack trace |
| BB-012 | External failure | TD-TICKET-A, backend lỗi | Mở chi tiết ticket | Hiển thị thông báo lỗi chung, không lộ thông tin kỹ thuật | P1 | Giống hành vi lỗi hiện có |

---

## Ghi chú

- Công thức mới: `last_fail.collected_at − first_fail.collected_at`.  
- Nếu không có FAIL → `"-"`. Nếu chỉ có 1 FAIL → `00:00:00`.  
- Trạng thái `SUCCESS` không ảnh hưởng.  
- Các case cũ liên quan đến “cycle” đã loại bỏ.  
- Vấn đề tie-break khi `collected_at` trùng nhau vẫn là Open Issue, không tạo test case khẳng định.  
