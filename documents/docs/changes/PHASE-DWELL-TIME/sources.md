# Sources

**Ticket ID**: PHASE-DWELL-TIME
**Create date**: 2026-08-21
**Author**: TBD  
**Update date**: 2026-08-21

## Ticket / Issue

| source | link/path | status | note |
|---|---|---|---|
| Raw input do người dùng cung cấp | `docs/changes/PHASE-DWELL-TIME/01_raw-input.md` | READ | "TÀI LIỆU YÊU CẦU CHỨC NĂNG (SRS) — PHASE DWELL TIME"; nguồn duy nhất cho yêu cầu nghiệp vụ tính đến nay. |

## Requirement / Design Documents

| source | path | status | trust level | note |
|---|---|---|---|---|
| (không có) | — | — | — | Chưa có tài liệu thiết kế/UX bổ sung ngoài raw input. |

## Existing Source Code

| area | path | status | purpose |
|---|---|---|---|
| FE component | `EDCAP_FE/src/pages/pm-dashboard/components/TicketDetailDrawer.tsx` (L106-157, L668-799) | READ | Vị trí `PhaseCard` hiện tại; điểm chèn Dwell Time theo raw-input. |
| FE type | `EDCAP_FE/src/lib/api.ts` (L549-641) | READ | `PmDashboardTicketRow` / `PmDashboardTicketDetail` — xác nhận không có field phase-list/dwell-time. |
| FE i18n (en) | `EDCAP_FE/public/locales/en/locale.json` (L261-265) | READ (một phần) | Namespace `Pages.PmDashboard.*` chứa `phaseName`, `phaseDescription`, `phaseCreatedAt`. |
| FE i18n (ja, vi) | `EDCAP_FE/public/locales/ja/locale.json`, `EDCAP_FE/public/locales/vi/locale.json` | KHÔNG ĐỌC | Tồn tại (xác nhận qua `find`) nhưng chưa mở nội dung. |
| FE util | `EDCAP_FE/src/lib/utils.ts` | READ (toàn bộ) | Chỉ có `formatDateTime`; không có helper format khoảng thời gian (duration) kiểu "Xh Ym"/"Xd Yh". |
| BE service | `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/phase/TicketPhaseEvaluatorService.java` | READ (toàn bộ) | Suy phase hiện tại từ evidence artifact; không tính dwell time. |
| BE adapter | `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/scanner/ArtifactScannerJdbcAdapter.java` (L492-560) | READ (một phần — chỉ đoạn liên quan phase) | Xác nhận upsert ghi đè 1 dòng/ticket. |
| DB migration | `V4__init_shema_v2.sql` (đoạn `tbl_dim_phase`, `tbl_fact_ticket_phase_status`) | READ (đoạn liên quan) | Schema gốc. |
| DB migration | `V330__pm_dashboard_snapshot.sql`, `V391__pm_dashboard_snapshot_v2.sql` | READ (toàn bộ) | View read-model chỉ lấy phase mới nhất. |
| DB migration | `V395__add_ticket_draft_phase.sql`, `V396__ticket_phase_status_single_row_per_ticket.sql` | READ (toàn bộ) | Xác nhận: không lưu lịch sử phase; thêm Draft phase. |
| DB migration | `V394`, `V501`, `V502`, `V503`, `V505`, `V510` | KHÔNG ĐỌC | Chỉ biết tên file qua grep; nội dung chưa xem. |

## Existing Tests

| test type | path | status | note |
|---|---|---|---|
| FE unit | `EDCAP_FE/src/__ tests __/pm-dashboard/TicketDetailDrawer.test.tsx` | KHÔNG ĐỌC (chỉ biết tồn tại qua grep) | Cần đọc trước khi implement để tránh phá test hiện có. |
| BE unit | `EDCAP_BE/src/test/UnitTest/java/com/sdd/platform/application/usecase/phase/TicketPhaseEvaluatorServiceTest.java` | KHÔNG ĐỌC | Idem. |

## External / Office / PDF / Web References

| source | type | handling policy | note |
|---|---|---|---|
| (không có) | — | — | Raw input là văn bản Markdown thuần, không có file Office/PDF đính kèm. |

## Excluded Sources

| source/path | reason |
|---|---|
| `.env`, `application-prod.yml`, mọi file chứa "secret/credential/token" | Theo `.claude/rules/00-safety.md` — không đọc. |
| DB thực tế (query trực tiếp Postgres) | Không có kết nối/quyền truy cập trong phiên này; chỉ đọc file migration SQL. |

## Source Limitations

- Hiểu biết về schema DB dựa 100% trên file migration tĩnh, chưa xác nhận với DB đã chạy thực tế (có thể có drift).
- Chưa đọc file test hiện có (FE + BE) — chưa biết mock data/coverage hiện tại của `PhaseCard`/`TicketPhaseEvaluatorService`.
- Chưa đọc nội dung `ja`/`vi` locale.json — chưa biết bản dịch hiện tại của các key `Pages.PmDashboard.*`.

## Assumptions from Sources

(Không có — mọi suy đoán được chuyển vào `spec-pack.md §17` và `open-issues.md` thay vì giả định ở đây.)

## Human Confirmation Required

- Xem `open-issues.md` (OI-2 → OI-6) và `spec-pack.md §16`.
