# Source Availability

**Ticket ID**: REVIEW-FINDING-DENSITY
**Create date**: 2026-09-10 22:30:00
**Author**: nvt_dung
**Update date**: 2026-09-10 22:30:00

| source | path | read_status | trust_level | owner | purpose | risk | action |
|---|---|---|---|---|---|---|---|
| GitPrMetadataCollectorService | `EDCAP_BE/.../ingestion/GitPrMetadataCollectorService.java` | Read | High (source code thật) | BE | Xác định điểm gắn finding classification | Delete-then-reinsert review pattern (dòng 342) có thể xung đột FK với finding writer mới | Đã chốt: finding writer tự dọn theo `pr_id` trước bước review reinsert (xem impl-plan §1.3) |
| GitPrMetadataCollectorJdbcAdapter | `EDCAP_BE/.../adapter/GitPrMetadataCollectorJdbcAdapter.java` | Read | High | BE | Xác định SQL cần sửa/thêm | Không có test cover `upsertPullRequestChangedFile`/review CRUD hiện nay | Thêm test trước khi đổi unique key (Part A) |
| GithubPullRequestMetadataAdapter / Port | `EDCAP_BE/.../infrastructure/github/...`, `.../port/out/integration/GithubPullRequestMetadataPort.java` | Read | High | BE | Xác nhận REST-only, không có GraphQL | Rủi ro rate-limit GraphQL khác REST (spec §9 rủi ro #1) | Đánh giá rate limit ở Part B, dùng lại `githubWebClient`/token pattern nếu base URL cho phép |
| WebClientConfig | `EDCAP_BE/.../config/WebClientConfig.java` | Read | High | BE | Xác nhận pattern 1 WebClient/external API | Không có | Follow pattern — thêm bean mới nếu base URL GraphQL khác REST |
| EvidenceQualityScoreService.scoreByRatio | `EDCAP_BE/.../quality/EvidenceQualityScoreService.java` | Read | High | BE | Pattern rounding BigDecimal | Scale mặc định là 2, cần override thành 1 cho density (OI-004) | Không copy nguyên hàm — viết method riêng scale=1, N/A khi denominator=0 (khác `ZERO` fallback của evidence quality) |
| AiFindingStatWriter | `EDCAP_BE/.../ingestion/AiFindingStatWriter.java` | Read | High | BE | Pattern isolated writer `REQUIRES_NEW` | Không có | Copy pattern nguyên vẹn cho Finding writer |
| PmDashboardController | `EDCAP_BE/.../web/rest/PmDashboardController.java` | Read | High | BE | Pattern route/DTO cho endpoint mới | Route cụ thể cho density chưa được spec chốt | Đề xuất route ở impl-plan, đánh dấu Open Issue non-blocking nếu cần review thêm |
| Migration V4, V181, thư mục migration | `EDCAP_BE/src/main/resources/db/migration/` | Read | High | DB | Xác nhận schema thật + version tiếp theo | `head_sha` chưa tồn tại; cần backfill cho dữ liệu cũ | Migration `V519` + backfill policy (xem impl-plan Part A) |
| SummaryCards.tsx, TicketDetailDrawer.tsx | `EDCAP_FE/src/pages/pm-dashboard/components/` | Read (qua Explore agent) | Medium (excerpt, chưa đọc dòng 1-880 trực tiếp) | FE | Pattern hiển thị N/A + điểm chèn UI mới | Chưa xác nhận i18n key convention (context.md §Multilingual Note) | Đọc full file trước khi code FE (liệt kê ở impl-plan §3), xác nhận literal string vs i18n key |
| docs/architecture/*.md | `docs/architecture/` (5 file liên quan) | Read (qua Explore, grep symbol) | Low (xác nhận stale) | Docs | Kiểm tra tài liệu kiến trúc có mô tả đúng flow hiện tại không | Tài liệu **không đề cập** đến `GitPrMetadataCollectorService`/`tbl_fact_finding`/`tbl_fact_review`/`tbl_fact_pull_request_changed_file`/`EvidenceQualityScoreService`/`AiFindingStatWriter` — mô tả flow legacy V1 | Không dùng các doc này làm căn cứ thiết kế; chỉ dùng source code thật đã đọc trực tiếp ở trên |
| docs/standards/templates/_ticket-template/*.md | `docs/standards/templates/_ticket-template/` | Read (full, 4 file template) | High | Docs | Khung bắt buộc cho artifact Phase 3 | Không có | Dùng verbatim làm skeleton |

## Summary

Toàn bộ source code liên quan trực tiếp đến ticket (BE service/adapter/port/config, migration hiện có, pattern rounding/writer/controller tham chiếu) đã được đọc trực tiếp và xác nhận nội dung thật, không dựa trên suy đoán. Riêng 2 file FE (`SummaryCards.tsx`, `TicketDetailDrawer.tsx`) mới đọc qua excerpt của Explore agent — đủ để lập kế hoạch nhưng cần đọc full trước khi code thật (đã đưa vào danh sách "existing code cần đọc" ở impl-plan §3).

## Unavailable / Partial Sources

- `docs/architecture/*.md` (service-layer-map, repository-db-map, route-api-map, entrypoint-map, fe-be-contract-map): tồn tại nhưng **nội dung stale**, không phản ánh đúng hiện trạng `GitPrMetadataCollectorService`/`tbl_fact_finding`/`tbl_fact_review`. Không dùng làm nguồn quyết định thiết kế cho ticket này.
- `EDCAP_FE/.../SummaryCards.tsx`, `TicketDetailDrawer.tsx`: mới đọc qua excerpt (dòng cụ thể), chưa đọc toàn bộ 152/882 dòng — cần đọc full ở bước implementation trước khi sửa.
- `.env` / secret files: **không đọc** theo `.claude/rules/00-safety.md §1` — mọi tham chiếu credential GraphQL trong impl-plan chỉ nêu tên biến cấu hình, không nêu giá trị thật.

## Risk Before Implementation

1. Dựa nhầm vào `docs/architecture/*.md` (stale) thay vì source code thật — đã giảm thiểu bằng cách chỉ dùng source code đã đọc trực tiếp làm căn cứ.
2. Chưa đọc full 2 file FE trước khi code — cần đọc lại theo đúng thứ tự ở impl-plan §3 trước khi sửa.
3. Chưa xác nhận `WebClientConfig` có hỗ trợ base URL GraphQL riêng (`https://api.github.com/graphql` khác REST `https://api.github.com`) hay cần bean mới — cần xác nhận khi code Part B, không phải điểm chặn Phase 3.

## Required Human Decision

- Đã xác nhận với user (Phase 3): Finding writer tự quản lý vòng đời finding theo `(pr_id, head_sha, thread_id)`, tự dọn dẹp trước bước review xóa-chèn-lại, không sửa schema FK `tbl_fact_finding.review_id`/`review_comment_id`. Xem impl-plan §1.3.
- Còn lại: route API cụ thể, giá trị backfill `head_sha` cụ thể, cấu hình GraphQL auth — liệt kê ở impl-plan §9 Open Issues (non-blocking, cần chốt trước khi bắt đầu Part tương ứng).
