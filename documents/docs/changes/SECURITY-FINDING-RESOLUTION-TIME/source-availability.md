# Source Availability

**Ticket ID**: SECURITY-FINDING-RESOLUTION-TIME
**Create date**: 2026-08-17
**Author**: Claude
**Update date**: 2026-08-17

| source | path | read_status | trust_level | purpose | risk | action |
|---|---|---|---|---|---|---|
| Business requirement | spec-pack.md (cleaned) | Read | High | Định nghĩa công thức mới `last_fail − first_fail` | Low | reference |
| Wireframe / UI spec | — | Missing | — | Vị trí field trong Drawer | Medium | quyết định ở impl-plan |
| `tbl_fact_security_scan` schema | `V4__init_shema_v2.sql:740-758` | Read | High | Bảng dữ liệu duy nhất dùng | Low | always-read |
| Application code | `SecurityDashboardJdbcAdapter.java`, `SecurityDashboardModels.java`, `SecurityDashboardDtos.java` | Read | High | Nơi bổ sung query, field, DTO | Low | implement |
| FE code | `types.ts`, `SecurityTicketDetailDrawer.tsx` | Read | High | Nơi hiển thị field mới | Low | implement |
| Ticket nền: SECURITY-DASHBOARD | `docs/changes/SECURITY-DASHBOARD/` | Partial | High | Dashboard hiện có | Low | reference |
| Standards | `docs/standards/*`, `.claude/rules/*` | Read | High | Ràng buộc coding/testing/security | Low | apply |

---

## Summary

- **Business requirement:** đã có trong spec-pack.md (cleaned). Công thức mới: `last_fail.collected_at − first_fail.collected_at`.  
- **Database design:** dùng `tbl_fact_security_scan`. Không cần bảng khác.  
- **Wireframe:** không có, vị trí field quyết định ở impl-plan.  
- **Ticket nền:** SECURITY-DASHBOARD, field bổ sung vào dashboard hiện có.  
- **Risk:** cần xác nhận index `(ticket_id, scan_status, collected_at)` để đảm bảo hiệu năng.  
