# Source Inventory — SECURITY-FINDING-RESOLUTION-TIME

Tất cả mục dưới đây là **quan sát trực tiếp từ source code hiện có**, kèm đường dẫn
và số dòng. Không có suy đoán nghiệp vụ trong tài liệu này.

## 1. Bảng dữ liệu

### 1.1 `tbl_fact_security_finding` (ứng viên chính cho "security finding")

- Định nghĩa: `EDCAP_BE/src/main/resources/db/migration/V4__init_shema_v2.sql:760-780`
- Cột liên quan: `ticket_id`, `scanner_type`, `scan_status`, `finding_count`, `unresolved_count`, `collected_at`.  
- Đây là bảng duy nhất được dùng để tính **resolutionTime**.  
- Công thức mới:  
  - Lọc theo `scan_status = 'FAIL'` và `ticket_id`.  
  - Sắp xếp theo `collected_at ASC`.  
  - Nếu không có bản ghi FAIL → `"-"`.  
  - Nếu chỉ có 1 bản ghi FAIL → `00:00:00`.  
  - Nếu có ≥2 bản ghi FAIL → `last_fail.collected_at − first_fail.collected_at`.  
- Có entity: `SecurityScan.java` (map field-by-field).  
- Có adapter: `SecurityDashboardJdbcAdapter.java`.  
- Có model tổng hợp: `SecurityDashboardModels.SecurityScanResult` — chứa số đếm, không có thời gian.  

### 1.2 Các bảng khác

- `tbl_fact_security_finding`: orphaned, không có code nào dùng.  
- `tbl_fact_finding`: finding tổng quát từ review, không liên quan đến security scanner.  
- Bảng `finding` legacy: đã drop, không liên quan.  

## 2. Enum liên quan

- `scan_status`: `PASS` / `FAIL` / `WARNING`.  
- `severity_level`: dùng chung nhiều bảng.  

## 3. Frontend

- `types.ts`: có `SecurityTicketDetail`, chưa có field `resolutionTime` → cần bổ sung.  
- `SecurityTicketDetailDrawer.tsx`: vị trí hiển thị field mới.  

## 4. Ticket liên quan trong `docs/changes/`

- `SECURITY-DASHBOARD`: ticket nền, field này bổ sung vào dashboard hiện có.  

## 5. Kết luận rút ra (fact)

1. Bảng duy nhất dùng để tính resolution time là `tbl_fact_security_scan`.  
2. Công thức mới: `last_fail.collected_at − first_fail.collected_at`.  
3. Không có API/DTO/UI nào hiện tại phơi bày resolution time → đây là tính năng mới.  
4. Các bảng khác (`tbl_fact_security_finding`, `tbl_fact_finding`, legacy finding`) không liên quan.  
