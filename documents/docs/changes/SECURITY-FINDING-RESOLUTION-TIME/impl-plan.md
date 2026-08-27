# impl-plan

**Ticket ID**: SECURITY-FINDING-RESOLUTION-TIME
**Create date**: 2026-08-17
**Author**: Claude (Principal Engineer prep)
**Update date**: 2026-08-17

---

## Phương châm implement

- Công thức tính mới: `resolutionTime = last_fail.collected_at − first_fail.collected_at`.  
- Điều kiện lọc: `scan_status = 'FAIL'` áp dụng cho mọi scanner.  
- Nếu chỉ có 1 bản ghi FAIL → `00:00:00`.  
- Nếu không có bản ghi FAIL → `"-"`.  
- Logic tính đặt tại tầng `application/usecase` (`SecurityDashboardService`), không viết trực tiếp trong SQL adapter.  
- Định dạng kết quả: `HH:mm:ss`, `HH` không giới hạn 24.  

---

## Danh sách file thay đổi

| file | thay đổi | lý do |
|---|---|---|
| `EDCAP_BE/.../securitydashboard/SecurityDashboardModels.java` | Thêm field `resolutionTime` (String) vào record `SecurityTicketDetail` | Cần field output mới |
| `EDCAP_BE/.../application/usecase/SecurityFindingResolutionTimeCalculator.java` (mới) | Class thuần Java, static method `compute(List<SecurityScanSnapshot>)` | Thuật toán tính first/last FAIL, dễ unit test |
| `EDCAP_BE/.../infrastructure/persistence/adapter/SecurityDashboardJdbcAdapter.java` | Trong `findTicketDetail(...)`: query raw history với `scan_status='FAIL'`, gọi calculator, ghép kết quả vào `SecurityTicketDetail` | Giữ nguyên pattern Adapter assemble |
| `EDCAP_BE/.../web/dto/SecurityDashboardDtos.java` | `SecurityTicketDetailDto` thêm field `resolutionTime` | Expose field qua JSON |
| `EDCAP_FE/src/pages/security-dashboard/types.ts` | Thêm `resolutionTime: string` | FE type khớp BE |
| `EDCAP_FE/.../components/SecurityTicketDetailDrawer.tsx` | Hiển thị `detail.resolutionTime`, dùng i18n | Hiển thị field |
| `EDCAP_FE/public/locales/en/locale.json` | Thêm key `Pages.SecurityDashboard.drawer.resolutionTime` = "Security Finding Resolution Time" | i18n EN |
| `EDCAP_FE/public/locales/vi/locale.json` | Thêm key tương ứng = "Thời gian xử lý finding bảo mật" | i18n VI |
| `EDCAP_FE/public/locales/ja/locale.json` | Thêm key tương ứng = "セキュリティ指摘解消時間" | i18n JA |
| `EDCAP_BE/.../test/.../SecurityFindingResolutionTimeCalculatorTest.java` (mới) | Unit test thuật toán | Test các case 0/1/≥2 FAIL |

**Không đổi**: `SecurityDashboardRepositoryPort.java`, `SecurityDashboardService.java`, `SecurityDashboardController.java`, migration DB.

---

## Các bước thay đổi

1. Thêm field `resolutionTime` vào `SecurityTicketDetail`.  
2. Tạo class `SecurityFindingResolutionTimeCalculator` + unit test.  
3. Sửa `SecurityDashboardJdbcAdapter.findTicketDetail(...)`: query raw history, gọi calculator, truyền kết quả vào record.  
4. Cập nhật DTO `SecurityTicketDetailDto`.  
5. Cập nhật FE type `types.ts`.  
6. Cập nhật `SecurityTicketDetailDrawer.tsx` để hiển thị field.  
7. Thêm key i18n vào 3 file locale.  
8. Thêm test FE để verify hiển thị đúng.  

---

## Phương châm FE/BE contract

- Additive change: thêm field `resolutionTime` vào response JSON.  
- Không có endpoint mới, không đổi route.  
- DTO key: `resolutionTime` (camelCase).  
- FE fallback: nếu BE chưa có field, hiển thị `"-"` hoặc ẩn dòng.  

---

## Phương châm Test

- **BE unit test**: các case 0 FAIL → `"-"`, 1 FAIL → `00:00:00`, ≥2 FAIL → chênh lệch first/last.  
- **Adapter test**: verify query với `scan_status='FAIL'`.  
- **FE test**: hiển thị đúng giá trị, đa ngôn ngữ.  
- **AC Closure**: verify field mounted trên page thật.  

---

## Rollout/Rollback

- Rollout: deploy BE trước FE.  
- Rollback: revert commit, không ảnh hưởng dữ liệu (read-only).  
- Không cần backfill dữ liệu.  

---

## Gate trước implementation

1. ✅ spec-pack.md đã clean, không còn blocking issue.  
2. ✅ Công thức mới đã chốt: first/last FAIL.  
3. ⚠️ Cần xác nhận index cho query `(ticket_id, scan_status, collected_at)`.  
4. ⚠️ Cần xác minh dữ liệu retention đầy đủ.  
5. Đọc lại source code mục tiêu trước khi implement.  