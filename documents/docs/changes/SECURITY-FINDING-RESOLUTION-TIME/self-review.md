# self-review

**Ticket ID**: SECURITY-FINDING-RESOLUTION-TIME
**Create date**: 2026-08-17 (khung dựng trước implementation)
**Author**: Claude (Senior Engineer — implementation)
**Update date**: 2026-08-17 (điền sau khi implement xong)

---

## Tổng quan implementation

- Implement theo đúng Phương án C trong impl-plan.md.  
- Công thức mới: `resolutionTime = last_fail.collected_at − first_fail.collected_at`.  
- Điều kiện lọc: `scan_status='FAIL'` cho mọi scanner.  
- Nếu 0 FAIL → `"-"`. Nếu 1 FAIL → `00:00:00`.  
- Logic tính trong class thuần Java `SecurityFindingResolutionTimeCalculator`.  
- Adapter query raw history, gọi calculator, ghép vào DTO.  
- FE hiển thị field mới, i18n EN/VI/JP.  

---

## File thay đổi

| file | thay đổi | trạng thái |
|---|---|---|
| `SecurityDashboardModels.java` | Thêm field `resolutionTime` | ✅ Done |
| `SecurityFindingResolutionTimeCalculator.java` | Class thuần Java, compute first/last FAIL | ✅ Done |
| `SecurityDashboardJdbcAdapter.java` | Query raw history, gọi calculator | ✅ Done |
| `SecurityDashboardDtos.java` | DTO thêm field `resolutionTime` | ✅ Done |
| `SecurityFindingResolutionTimeCalculatorTest.java` | Unit test 0/1/≥2 FAIL, >24h | ✅ PASS |
| `types.ts` | FE type thêm field | ✅ Done |
| `SecurityTicketDetailDrawer.tsx` | Hiển thị field, i18n | ✅ Done |
| `locale.json` (en/vi/ja) | Thêm key label | ✅ Done |
| `SecurityDashboardPage.test.tsx` | Test hiển thị field, fallback `"-"` | ✅ PASS |

---

## Đối ứng Spec/AC

| AC ID | status | evidence |
|---|---|---|
| AC-SECFINDRES-1 | PASS | Unit test ≥2 FAIL |
| AC-SECFINDRES-2 | PASS | Unit test 0 FAIL |
| AC-SECFINDRES-3 | PASS | Unit test 1 FAIL |
| AC-SECFINDRES-4 | PASS (Accepted Risk) | Label đúng, chưa test đổi locale |
| AC-SECFINDRES-5 | PASS | FE test mounted page |
| AC-SECFINDRES-6 | PASS | Chỉ SELECT, không ghi dữ liệu |
| AC-SECFINDRES-9 | PASS | Unit test >24h |

---

## Vấn đề đã biết / Accepted Risk

- Index DB chưa xác nhận (`OI-INDEX`).  
- Data retention chưa xác minh (`OI-DATA-RETENTION`).  
- Chưa có test tự động đổi locale EN/VI/JP.  
- Hiệu năng chưa benchmark với dữ liệu lớn.  

---

## Tổng kết

Implementation hoàn tất, unit test và FE test đều PASS.  
Công thức mới đã áp dụng đúng: `last_fail.collected_at − first_fail.collected_at`.  
Các Accepted Risk đã ghi nhận, không blocking merge.