# Source Availability

**Ticket ID**: PHASE-DWELL-TIME
**Create date**: 2026-08-20
**Author**: TBD
**Update date**: 2026-08-20

| source | path | read_status | trust_level | owner | purpose | risk | action |
|---|---|---|---|---|---|---|---|
| Raw input (SRS người dùng cung cấp) | `docs/changes/PHASE-DWELL-TIME/01_raw-input.md` | READ (full) | High (nguồn yêu cầu duy nhất) | Người dùng | Định nghĩa yêu cầu nghiệp vụ, vị trí UI, công thức Dwell Time, format thời gian | §3.2 mô tả cách xác định danh sách phase mơ hồ ("phụ thuộc vào các file .md nào tồn tại") — đã làm rõ qua khảo sát source (source-map.md) | Không cần đọc lại; đã trích xuất đủ vào spec-pack.md |
| FE component `TicketDetailDrawer.tsx` | `EDCAP_FE/src/pages/pm-dashboard/components/TicketDetailDrawer.tsx` (L106-157 `PhaseCard`, L668-799 parent) | READ (đoạn liên quan) | High (source code hiện hành) | BE/FE team | Xác định vị trí chèn UI Dwell Time | Thấp — đã đọc đủ đoạn cần | Không cần đọc thêm cho spec-pack; đọc lại phần khác nếu cần khi impl |
| FE type `api.ts` | `EDCAP_FE/src/lib/api.ts` (L549-641) | READ (đoạn liên quan) | High | FE team | Xác nhận `PmDashboardTicketDetail`/`PmDashboardTicketRow` hiện không có field phase-list/dwell-time | Thấp | Cần đọc thêm khi impl để tìm toàn bộ consumer của DTO (A-3) |
| FE i18n `en/locale.json` | `EDCAP_FE/public/locales/en/locale.json` (L261-265) | READ (một phần) | Medium (chỉ đoạn liên quan `Pages.PmDashboard.*`) | FE team | Xác nhận namespace i18n hiện có cho phase | Thấp | Đủ dùng cho spec-pack; cần đọc toàn bộ namespace khi viết impl-plan để tránh trùng key |
| FE i18n `ja/locale.json`, `vi/locale.json` | `EDCAP_FE/public/locales/{ja,vi}/locale.json` | KHÔNG ĐỌC (chỉ xác nhận tồn tại qua `find`) | Không xác định | FE team | Cần để biết bản dịch hiện có trước khi thêm key mới | Trung bình — có thể trùng/thiếu key khi thêm bản dịch mới | Phải đọc trước khi soạn `impl-plan.md`/thêm i18n key |
| FE util `utils.ts` | `EDCAP_FE/src/lib/utils.ts` | READ (toàn bộ) | High | FE team | Xác nhận chưa có helper format khoảng thời gian (duration) | Thấp | Không cần đọc thêm |
| FE test `TicketDetailDrawer.test.tsx` | `EDCAP_FE/src/__tests__/pm-dashboard/TicketDetailDrawer.test.tsx` | KHÔNG ĐỌC (chỉ biết tồn tại qua grep) | Không xác định | FE team | Đo regression risk khi mở rộng DTO/UI | Cao — có thể có assertion cứng (snapshot/props) vỡ khi thêm field/UI mới | **Phải đọc trước khi implement** (blocker cho impl, không block spec-pack) |
| BE service `TicketPhaseEvaluatorService.java` | `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/phase/TicketPhaseEvaluatorService.java` | READ (toàn bộ) | High | BE team | Xác nhận service hiện tại chỉ tính "current phase", không có lịch sử/dwell time; xác nhận mapping hardcode khác DB (OI-PHASE-DWELL-TIME-7) | Thấp | Không sửa file này (ngoài phạm vi); không cần đọc thêm |
| BE test `TicketPhaseEvaluatorServiceTest.java` | `EDCAP_BE/src/test/UnitTest/java/com/sdd/platform/application/usecase/phase/TicketPhaseEvaluatorServiceTest.java` | KHÔNG ĐỌC | Không xác định | BE team | Xác nhận service không cần đổi test (vì không sửa service) | Thấp — service không bị sửa nên rủi ro thấp hơn test FE | Nên đọc trước impl để xác nhận không có coupling ẩn |
| BE adapter `ArtifactScannerJdbcAdapter.java` | `EDCAP_BE/.../scanner/ArtifactScannerJdbcAdapter.java` (L277-359 `insertSnapshot`, L492-560 `upsertTicketPhaseStatus`) | READ (đoạn liên quan) | High | BE team | Xác nhận `created_at` bất biến khi rescan trùng `content_hash` (A-1); xác nhận upsert phase hiện tại ghi đè toàn bộ dòng | Thấp | Đủ dùng cho spec-pack |
| BE service `ArtifactScannerService.java` | `EDCAP_BE/.../usecase/scanner/ArtifactScannerService.java` (`CHANGE_TARGET_FILES` L60-68, `PHASE0_TARGET_FILES`+`scanPhase0` L70-77, L1453-1493) | READ (đoạn liên quan) | High | BE team | Xác nhận danh sách file được quét theo ticket vs. theo repository (Phase 0); cơ sở loại `0-B/2/9` khỏi phạm vi | Thấp | Đủ dùng cho spec-pack |
| DB migration `V4__init_shema_v2.sql` | `EDCAP_BE/.../db/migration/V4__init_shema_v2.sql` (đoạn `tbl_dim_phase`, `tbl_dim_artifact_type`, `tbl_fact_ticket_phase_status`, `tbl_fact_artifact_snapshot`, seed L1377-1438) | READ (đoạn liên quan) | High (migration là nguồn schema tĩnh, chưa xác minh với DB thật) | BE/DBA | Schema gốc của toàn bộ bảng liên quan | Trung bình — chưa xác minh khớp DB thực tế đang chạy (schema drift) | Không đọc lại; nhưng cần xác minh với DBA/DB thật trước khi viết migration mới nếu nghi ngờ drift |
| DB migration `V160__artifact_scanner.sql` | `EDCAP_BE/.../db/migration/V160__artifact_scanner.sql` | READ (toàn bộ) | High | BE/DBA | Thêm cột snapshot; định nghĩa `vw_artifact_inventory_current` (chỉ lấy bản mới nhất, không dùng được trực tiếp cho lịch sử) | Thấp | Không cần đọc thêm |
| DB migration `V330`, `V391` | `V330__pm_dashboard_snapshot.sql`, `V391__pm_dashboard_snapshot_v2.sql` | READ (toàn bộ) | High | BE/DBA | Xác nhận view dashboard chỉ lấy "latest phase"; cơ sở giả định `primary_repo` theo `project_id` (A-2) | Trung bình — giả định 1 ticket ↔ 1 repository chính chưa xác minh đầy đủ | Cần đọc thêm `tbl_dim_ticket`/`TicketScope` trước khi viết SQL VIEW mới cho Entry(0-A) (trùng OI-9) |
| DB migration `V395`, `V396` | `V395__add_ticket_draft_phase.sql`, `V396__ticket_phase_status_single_row_per_ticket.sql` | READ (toàn bộ) | High | BE/DBA | Xác nhận: hệ thống hiện tại chỉ lưu 1 dòng phase hiện tại/ticket, không có lịch sử; `dwell_time_minutes` luôn NULL | Thấp | Không cần đọc thêm |
| DB migration `V394`, `V501`, `V502`, `V503`, `V505` | `EDCAP_BE/.../db/migration/` | KHÔNG ĐỌC (chỉ biết tên file qua grep) | Không xác định | BE/DBA | Chưa biết có thay đổi liên quan `tbl_dim_phase`/`tbl_fact_ticket_phase_status`/`tbl_fact_artifact_snapshot` hay không | Trung bình — có thể chứa thay đổi ảnh hưởng đến giả định hiện tại | Phải đọc trước `impl-plan.md`, trước khi viết migration mới |
| DB migration `V510` | `EDCAP_BE/.../db/migration/V510__add_template_usage_tracking.sql` (đoạn seed artifact_type L20-40) | READ (một phần — chỉ đoạn seed liên quan) | Medium | BE/DBA | Xác nhận `CONTEXT` (phase `2`) đã đăng ký trong `tbl_dim_artifact_type` nhưng không được scanner quét theo ticket | Thấp | Đủ dùng cho spec-pack; đọc toàn bộ file nếu cần khi impl |
| DB thực tế (Postgres đang chạy) | (kết nối trực tiếp DB) | KHÔNG ĐỌC — không có quyền truy cập trong phiên này | Không xác định | DBA | Xác minh schema tĩnh (migration file) khớp với schema thực tế đang chạy | Cao — toàn bộ spec dựa trên suy luận từ file migration; nếu có migration/hotfix ngoài luồng, giả định có thể sai | Cần DBA xác minh trước khi chạy migration mới (theo `00-safety.md §3`, phải hỏi người dùng trước `flyway migrate`) |
| `.env`, `application-prod.yml`, file chứa `secret/credential/token` | (không áp dụng — không cần cho ticket này) | KHÔNG ĐỌC (chủ động loại trừ) | N/A | N/A | N/A | N/A — không liên quan đến tính năng | Không đọc theo `.claude/rules/00-safety.md §1` |

## Summary

- Toàn bộ yêu cầu nghiệp vụ dựa trên 1 nguồn duy nhất: `01_raw-input.md` (SRS người dùng cung cấp, có bổ sung §3.5 cùng ngày). Không có tài liệu thiết kế/UX/Office/PDF nào khác kèm theo.
- Source code liên quan (FE component/type/util, BE service/adapter, DB migration schema) đã đọc đủ các đoạn cần thiết để xác định As-Is, gap dữ liệu, và giải pháp kỹ thuật (Phương án A). Độ tin cậy cao vì đọc trực tiếp source, không suy đoán từ mô tả gián tiếp.
- Rủi ro lớn nhất nằm ở phần **chưa đọc**: test hiện có (FE + BE), 5 file migration chưa mở nội dung (`V394, V501, V502, V503, V505`), locale `ja/vi`, và không có quyền truy cập DB thực tế để xác minh schema drift.

## Unavailable / Partial Sources

| source | trạng thái | ảnh hưởng | phải xử lý trước |
|---|---|---|---|
| `EDCAP_FE/src/__tests__/pm-dashboard/TicketDetailDrawer.test.tsx` | Chưa đọc | Không đo được regression risk cụ thể khi mở rộng DTO/UI `PhaseCard` | Bắt buộc đọc trước khi implement (không block spec-pack) |
| `EDCAP_BE/src/test/UnitTest/java/.../TicketPhaseEvaluatorServiceTest.java` | Chưa đọc | Rủi ro thấp hơn (service không bị sửa) nhưng nên xác nhận không có coupling ẩn | Nên đọc trước khi implement |
| `EDCAP_FE/public/locales/ja/locale.json`, `vi/locale.json` | Chưa đọc nội dung (chỉ xác nhận tồn tại) | Chưa biết bản dịch hiện có cho namespace `Pages.PmDashboard.*`, có thể trùng/thiếu key | Đọc trước khi soạn `impl-plan.md`/thêm i18n key |
| Migration `V394`, `V501`, `V502`, `V503`, `V505` | Chưa đọc nội dung | Có thể chứa thay đổi ảnh hưởng đến `tbl_dim_phase`/`tbl_fact_ticket_phase_status`/`tbl_fact_artifact_snapshot` chưa được tính đến trong spec-pack | Bắt buộc đọc trước `impl-plan.md`, trước khi viết migration mới |
| DB Postgres thực tế | Không có quyền truy cập | Không xác minh được schema tĩnh (file migration) khớp với schema đang chạy thực tế | Cần DBA xác minh trước khi migrate |
| `tbl_dim_ticket` / `TicketScope` (đầy đủ) | Chưa đọc đủ | Chưa xác nhận cơ chế ticket↔repository (ảnh hưởng Entry Phase `0-A`, OI-PHASE-DWELL-TIME-9) | Bắt buộc đọc trước khi viết SQL VIEW cho Entry(0-A) |
| Controller/BE ghép `PmDashboardTicketDetail` đầy đủ | Chưa xác định | Chưa biết chính xác điểm cần sửa để trả field mới `phaseTimeline` | Xác định ở `impl-plan.md` |

## Risk Before Implementation

1. **Schema drift**: hiểu biết schema 100% dựa trên file migration tĩnh, chưa xác minh với DB thực tế đang chạy — rủi ro cao nếu có hotfix/migration ngoài luồng.
2. **Regression risk chưa đo được**: chưa đọc `TicketDetailDrawer.test.tsx` — có thể có snapshot test/assertion cứng vỡ khi mở rộng DTO hoặc thêm UI mới trong `PhaseCard`.
3. **Ticket→repository mapping chưa xác nhận** (trùng OI-PHASE-DWELL-TIME-9): giả định 1 ticket ↔ 1 repository chính (dựa trên pattern `primary_repo`/`project_id` ở `V330/V391`) chưa được xác minh đầy đủ qua `tbl_dim_ticket`/`TicketScope` — là điều kiện bắt buộc để viết SQL Entry(Phase `0-A`).
4. **5 migration chưa đọc** (`V394, V501, V502, V503, V505`) có thể chứa thay đổi ảnh hưởng đến các bảng cốt lõi của tính năng này.
5. **Thiếu bản dịch EN/JA cho nhãn trạng thái** (Pending/Completed/InProgress) — chỉ có tiếng Việt từ raw-input.

## Required Human Decision

- Xác nhận nguồn/cơ chế xác định "repository chính của 1 ticket" cho Entry(Phase `0-A`) — hoặc chấp thuận cho AI tự đọc thêm `tbl_dim_ticket`/`TicketScope` để tự kết luận kỹ thuật (không cần quyết định nghiệp vụ, xem OI-PHASE-DWELL-TIME-9 ở `open-issues.md`).
- Xác nhận hành vi UI khi API lấy dữ liệu phase/dwell time bị lỗi/timeout (OI-PHASE-DWELL-TIME-11).
- Xác nhận nhãn i18n cho trạng thái "InProgress" (có Entry nhưng chưa có Dwell) + bổ sung bản dịch EN/JA cho toàn bộ nhãn trạng thái (OI-PHASE-DWELL-TIME-12, OI-PHASE-DWELL-TIME-4 phần còn lại).
