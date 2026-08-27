# Source Map

**Ticket ID**: SECURITY-FINDING-RESOLUTION-TIME
**Create date**: 2026-08-17
**Author**: Claude (Tech Lead prep)
**Update date**: 2026-08-17

## Target Area

Bổ sung field `resolutionTime` vào chi tiết ticket của Security Dashboard, tính từ lịch sử `tbl_fact_security_scan`. Vùng tác động: 1 bảng DB (chỉ đọc), 1 domain hexagonal đầy đủ (`securitydashboard`), 1 component FE, 3 file i18n. Không có bảng/endpoint/màn hình mới.

## Entry Points

| entry | path | note |
|---|---|---|
| REST endpoint | `GET /api/v1/security/dashboard/tickets/{ticketId}` — `SecurityDashboardController.ticketDetail(...)` | Không đổi route, không đổi permission |
| FE hiển thị | `SecurityTicketDetailDrawer.tsx` | Nhận `detail: SecurityTicketDetail` qua props |
| FE fetch | `lib/api.ts` | Không cần sửa — generic type |

## Call Flow

| caller | callee | note |
|---|---|---|
| Controller | Service.getTicketDetail(ticketId) | Trả `SecurityTicketDetail` |
| Service | RepositoryPort.findTicketDetail(ticketId) | Adapter implement |
| Adapter | Query raw history từ `tbl_fact_security_scan` với `scan_status='FAIL' ORDER BY collected_at ASC` | Mới thêm |
| Adapter | Gọi `SecurityFindingResolutionTimeCalculator.compute(...)` | Tính `last_fail − first_fail` |
| DTO | Map model → JSON | Additive change |
| FE Drawer | Hiển thị field qua i18n | Label EN/VI/JP |

## Data Flow

