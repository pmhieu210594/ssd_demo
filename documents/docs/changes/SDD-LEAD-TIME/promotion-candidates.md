# Promotion Candidates

**Ticket ID**: SDD-LEAD-TIME
**Create date**: 2026-08-21
**Author**: nvt_dung
**Update date**: 2026-08-21

> Nguồn: `report.md` §14/§15, `self-review.md`, `test-plan.md` §8, `impl-plan.md` §9 (IMPL-OI-4).

## Candidates for Living Docs

| ID | candidate | target doc | reason | priority |
|---|---|---|---|---|
| PC-1 | Cập nhật `docs/architecture/test-map.md` để bỏ tham chiếu tới `EDCAP_BE/src/test/java/com/sdd/platform/architecture/LayerEnforcementTest.java` (file không tồn tại) hoặc ghi rõ trạng thái "chưa implement" | `docs/architecture/test-map.md` | Tài liệu hiện đang trỏ tới một file không tồn tại — gây hiểu lầm cho ticket sau rằng có ArchUnit test tự động khi thực tế không có (phát hiện ở IMPL-OI-4) | Medium |

## Candidates for Rules

| ID | rule candidate | target | reason | risk of rule bloat |
|---|---|---|---|---|
| PC-2 | "Khi mở rộng field trên response API, phải trace tới tận DTO thực sự serialize ra JSON (không dừng ở application-layer model record)" | `.claude/rules/20-architecture.md` hoặc `docs/standards/coding.md` | Ticket này bỏ sót `PmDashboardDtos.java` ở Phase 2/3 vì chỉ đọc `DashboardTicketRow` (application model); chỉ phát hiện lúc code. Nếu lặp lại ở ticket khác, đây sẽ là ứng viên rule mạnh | Thấp — hiện mới xảy ra 1 lần, chưa đủ để khẳng định là pattern lặp lại; **khuyến nghị ghi nhận nhưng chưa promote thành rule chính thức, chờ xác nhận thêm 1-2 lần lặp lại ở ticket khác trước khi thêm rule** |

## Candidates for Standards

| ID | standard candidate | target | reason |
|---|---|---|---|

Không có ứng viên — không phát sinh coding-style pattern mới cần chuẩn hóa trong ticket này.

## Candidates for Architecture Docs

| ID | update candidate | target | reason |
|---|---|---|---|

Không có ứng viên — không có thay đổi kiến trúc (chỉ additive extension trong layer đã có).

## Candidates for Failure Mode Index

| ID | failure mode | trigger | prevention | detection |
|---|---|---|---|---|
| PC-3 | Impact-analysis/context liệt kê application-layer model nhưng bỏ sót lớp DTO/mapping riêng biệt map ra HTTP response | Codebase có model nội bộ (`DashboardTicketRow`) tách biệt khỏi DTO response (`PmDashboardDtos`) — chỉ đọc/liệt kê model không đủ để xác định toàn bộ điểm cần sửa cho field additive | Khi mở rộng field trên response API, trace từ `@RestController` method xuống tận điểm trả JSON thực tế trước khi chốt danh sách file bị ảnh hưởng ở Phase 2/3 | Compile error hoặc test fail nếu field thiếu ở tầng DTO nhưng có ở tầng model — chỉ phát hiện được nếu có test xác nhận response JSON thực tế, không chỉ mock ở tầng Service |

## Not Promoted

| item | reason |
|---|---|
| Đề xuất thêm hạ tầng Testcontainers cho DB integration test | Đây là follow-up ở mức hạ tầng/ticket riêng, không phải "living doc" hay "rule" — chuyển thành mục cần Human Approval bên dưới thay vì promote trực tiếp |
| Rule "luôn trace tới DTO thực sự" (PC-2) thành rule chính thức ngay | Mới xảy ra 1 lần trong 1 ticket — chưa đủ bằng chứng là pattern lặp lại; thêm rule quá sớm có nguy cơ rule bloat mà không rõ giá trị lâu dài |
| Backfill 3 key i18n `createAt`/`mergedAt`/`ticketInformation` được cho là thiếu trong `ja/locale.json` | Xác nhận lại (Phase 3) rằng 3 key này đã tồn tại sẵn — claim ban đầu trong `context.md` là sai/stale, không phải gap thật, không cần hành động |

## Human Approval Required

| # | Đề xuất | Lý do cần approval |
|---|---|---|
| 1 | Mở một ticket/sáng kiến riêng (ngoài SDD-LEAD-TIME) để xây dựng Testcontainers-based DB integration test harness dùng chung cho toàn repo | Đây là gap có sẵn của hệ thống (dependency `testcontainers`/`postgresql` đã khai báo trong `pom.xml` nhưng chưa từng được dùng ở bất kỳ đâu), đã bị nhiều ticket (bao gồm ticket này) ghi nhận là "test đã bỏ qua có chủ đích" — cần quyết định của maintainer về ưu tiên và phạm vi đầu tư, không phải quyết định kỹ thuật đơn thuần của 1 ticket |
