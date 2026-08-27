# Test Plan

**Ticket ID**: SECURITY-FINDING-RESOLUTION-TIME
**Create date**: 2026-08-18
**Author**: Claude (Test Strategist)
**Update date**: 2026-08-18

---

## 1. Mục tiêu

Ánh xạ các AC của `spec-pack.md` (§6) sang loại test cụ thể, dựa trên code đã implement theo `impl-plan.md`: thuật toán tính toán resolution time tách riêng thành `SecurityFindingResolutionTimeCalculator` (pure Java, không Spring/DB) để unit test trực tiếp; Adapter query raw history rồi gọi calculator; FE hiển thị field trong `SecurityTicketDetailDrawer.tsx`.

---

## 2. Ma trận AC ↔ loại test

| AC ID | FE UT | BE UT | API IT | Black-box |
|---|---|---|---|---|
| AC-SECFINDRES-1 (≥2 FAIL → `last_fail − first_fail`) | – | ✅ | – | – |
| AC-SECFINDRES-2 (0 FAIL → `"-"`) | ⚠️ thiếu | ✅ | – | – |
| AC-SECFINDRES-3 (1 FAIL → `00:00:00`) | ⚠️ thiếu | ✅ | – | – |
| AC-SECFINDRES-4 (label EN/VI/JP) | ⚠️ thiếu | – | – | ✅ manual |
| AC-SECFINDRES-5 (field mounted trên Drawer, không phá layout) | ✅ | – | – | – |
| AC-SECFINDRES-6 (read-only, không ghi dữ liệu) | – | ✅ (review SQL) | – | – |
| AC-SECFINDRES-9 (>24h, HH không giới hạn) | – | ✅ | – | – |

---

## 3. Ưu tiên

- **P0:** BE UT cho calculator (đã có).  
- **P0:** API/Adapter IT cho query raw history (cần bổ sung).  
- **P1:** FE UT hiển thị `"-"` khi không có FAIL hoặc chỉ 1 FAIL.  
- **P2:** Black-box kiểm tra label VI/JP thật.  

---

## 4. Tái sử dụng test hiện có

- `SecurityFindingResolutionTimeCalculatorTest`: cover AC-1,2,3,9.  
- `SecurityDashboardPage.test.tsx`: cover AC-5.  
- `SecurityDashboardServiceTest`: xác nhận không regression ở tầng Service.  

---

## 5. Test mới/cập nhật

| TC ID | test | type | target | related AC |
|---|---|---|---|---|
| TC-SECFINDRES-1 | Adapter/IT: query raw history trả đúng resolutionTime | API IT | JdbcAdapter | AC-1,2,3,9 |
| TC-SECFINDRES-2 | FE UT: hiển thị `"-"` khi không có FAIL hoặc chỉ 1 FAIL | FE UT | Drawer | AC-2,3 |
| TC-SECFINDRES-3 | Black-box: label VI/JP hiển thị đúng | Manual | Drawer | AC-4 |

---

## 6. Vùng không test

- Không có E2E Playwright — flow đơn giản đã cover bằng FE UT.  
- Không có auth/permission riêng cho field mới.  
- Không có rollback dữ liệu — read-only.  

---

## 7. Test data

- Boundary: 0 FAIL, 1 FAIL, ≥2 FAIL, >24h.  
- FE mock: `"27:30:00"` và `"-"`.  
- Timezone: test với offset khác `Z`.  

---

## 8. Command thực thi

- `mvn test -Dtest=SecurityFindingResolutionTimeCalculatorTest`  
- `mvn test -Dtest=SecurityDashboardServiceTest`  
- `npm run test -- security-dashboard`  

---

## 9. Rủi ro còn lại

- SQL query chưa có test tự động.  
- Index chưa xác nhận.  
- Data retention chưa xác minh.  
- Label VI/JP chưa có test tự động.  

