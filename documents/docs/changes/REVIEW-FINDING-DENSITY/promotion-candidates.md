# Promotion Candidates

**Ticket ID**: REVIEW-FINDING-DENSITY
**Create date**: 2026-09-15 17:04:00
**Author**: nvt_dung
**Update date**: 2026-09-15 17:06:10

## Candidates for Living Docs

| ID | candidate | target doc | reason | priority |
|---|---|---|---|---|
| PC-01 | Bổ sung `GitPrMetadataCollectorService`, `FindingClassifier`, `FindingWriter`, `ReviewFindingDensityService` vào bản đồ service layer | `docs/architecture/service-layer-map.md` | Doc hiện tại không mô tả các service này (đã stale từ trước ticket, ticket này làm tăng thêm độ lệch — `impact-analysis.md` §3) | Medium |
| PC-02 | Bổ sung `tbl_fact_finding` (writer mới) và cột `head_sha` mới của `tbl_fact_pull_request_changed_file` | `docs/architecture/repository-db-map.md` | Bảng đã tồn tại từ V4 nhưng chưa từng có writer; giờ đã có, cần cập nhật map để tránh nhầm là bảng chết | Medium |
| PC-03 | Bổ sung field `reviewFindingDensity` vào mô tả response của `GET /tickets/{ticketId}/detail` | `docs/architecture/route-api-map.md` | Route/response đã đổi (thêm field) nhưng doc route hiện có không phản ánh | Low |
| PC-04 | Bổ sung GraphQL client mới (`GithubReviewThreadAdapter`, endpoint `/graphql`) như một entry point ra hệ thống ngoài | `docs/architecture/entrypoint-map.md` | Đây là entry point mới hoàn toàn ra GitHub GraphQL, khác REST hiện có | Low |
| PC-05 | Bổ sung contract field `reviewFindingDensity` giữa FE/BE | `docs/architecture/fe-be-contract-map.md` | Contract mới chưa được ghi nhận, có thể gây nhầm lẫn khi BE đổi shape sau này mà không biết FE đang đọc field nào | Low |

## Candidates for Rules

| ID | rule candidate | target | reason | risk of rule bloat |
|---|---|---|---|---|
| — | Không có ứng viên | — | Không phát hiện quy tắc mới đủ tổng quát để đưa vào `.claude/rules/` — các quyết định của ticket này (metadata-only, backfill sentinel, embed API thay vì tạo mới) đều mang tính đặc thù ticket, không phải nguyên tắc lặp lại xuyên dự án | — |

## Candidates for Standards

| ID | standard candidate | target | reason |
|---|---|---|---|
| — | Không có ứng viên | — | Pattern rounding (`scale=1`, `HALF_UP`, N/A qua `Optional.empty()`) chỉ là biến thể theo yêu cầu riêng (OI-004) của `scoreByRatio` đã có sẵn — chưa đủ số lượng use case lặp lại để tổng quát hóa thành standard riêng |

## Candidates for Architecture Docs

| ID | update candidate | target | reason |
|---|---|---|---|
| — | Xem mục "Candidates for Living Docs" ở trên (PC-01..05) | `docs/architecture/*-map.md` | Không tách riêng — 5 mục Living Docs ở trên đã là các cập nhật kiến trúc cụ thể |

## Candidates for Failure Mode Index

| ID | failure mode | trigger | prevention | detection |
|---|---|---|---|---|
| FM-01 | FK `NO ACTION` từ bảng con trỏ tới bảng cha bị xóa-chèn-lại (delete-then-reinsert) định kỳ, nhưng bảng con không được dọn trước → vi phạm FK ở lần sync tiếp theo | Thêm writer mới cho bảng con tham chiếu tới bảng cha có flow "xóa toàn bộ rồi chèn lại" (ví dụ `tbl_fact_review` bị xóa-chèn-lại mỗi lần đồng bộ PR) mà không kiểm tra thứ tự dọn dẹp | Bắt buộc: bảng con phải tự dọn theo cùng khóa (`pr_id`/`ticket_id`) **trước** bước xóa bảng cha trong cùng flow; viết test `InOrder`/tương đương xác nhận thứ tự gọi | FK violation exception khi chạy integration test đồng bộ 2 lần liên tiếp (đã áp dụng: `GitPrMetadataCollectorServiceTest...deletes_findings_before_deleting_reviews_to_avoid_fk_violation`) |
| FM-02 | External API call (không transaction hóa, ví dụ GraphQL/REST bên thứ 3) được chèn giữa một flow ghi DB chính mà không cô lập lỗi → lỗi/timeout của API ngoài lan ra làm fail toàn bộ flow ghi, kể cả các bước không liên quan đã thành công trước đó | Tích hợp một API bên ngoài mới vào giữa một use case ghi DB đã có sẵn (ví dụ thêm bước "sync finding qua GraphQL" vào giữa `persistPullRequest`) mà không đánh giá blast radius khi API đó lỗi | Đánh giá rõ: bước gọi API ngoài có nên fail toàn bộ flow cha hay chỉ fail-soft (log + tiếp tục)? Nếu chọn fail-soft, bọc try/catch quanh lời gọi + log warning, không để exception lan lên tầng trên | Chưa có detection tự động trong ticket này — RC-31 còn là nợ kỹ thuật treo (self-review.md §5.3 #2); nếu để nguyên, phát hiện sẽ đến từ production incident khi GraphQL rate-limit/lỗi |

## Not Promoted

| item | reason |
|---|---|
| Rule metadata-only (không dùng NLP để phân loại finding) như một "rule chuẩn" chung | Đây là quyết định nghiệp vụ đặc thù của ticket này (OI-003, Closed), không phải pattern kỹ thuật tái dùng được cho ticket khác — không phù hợp để promote thành rule/standard chung |
| Backfill `head_sha` bằng sentinel `'legacy'` như một "chuẩn backfill" chung | Giá trị và ngữ nghĩa sentinel gắn chặt với ngữ cảnh cụ thể (không có nguồn dữ liệu head-sha lịch sử); các ticket backfill khác có thể cần chiến lược khác tùy dữ liệu sẵn có, không nên áp cứng thành chuẩn |
| Quyết định "nhúng field mới vào API hiện có thay vì tạo endpoint riêng" (IMPL-OI-3) như một rule kiến trúc chung | Đây là quyết định đánh đổi theo ngữ cảnh cụ thể (tránh thêm round-trip DB, tái dùng permission gate có sẵn) — không phải nguyên tắc luôn đúng cho mọi ticket; một số ticket khác có thể cần endpoint riêng vì lý do phân quyền/versioning khác |

## Human Approval Required

- [x] Xác nhận có nên cập nhật ngay 5 file `docs/architecture/*-map.md` (PC-01..05) trong một ticket riêng, hay tiếp tục để backlog dồn lại và cập nhật theo đợt. **Quyết định (2026-09-15, Phase 9): cập nhật ngay** — đã thêm additive vào `service-layer-map.md`, `repository-db-map.md`, `route-api-map.md`, `entrypoint-map.md`, `fe-be-contract-map.md`.
- [x] Xác nhận có promote FM-01 và FM-02 vào Failure Mode Index chính thức của dự án. **Quyết định (2026-09-15, Phase 9): FM-01 promoted thành `FMI-RFD-001` (mới, không trùng entry nào có sẵn); FM-02 không tạo ID mới — chỉ thêm note tham chiếu chéo tới `FMI-PTR-001` đã có sẵn (cùng root cause: thiếu try/catch tại call-site quanh lời gọi có `@Transactional(REQUIRES_NEW)`).** Xem `docs/maintenance/failure-mode-index.md` §"REVIEW-FINDING-DENSITY-Derived Failure Modes" và `docs/maintenance/pattern-library.md` §"Referential Integrity".
- [ ] Xác nhận quyết định còn treo ở `report.md` mục "Human Decisions" (có cần try/catch/circuit-breaker cho RC-31 ngay bây giờ hay không) trước khi đóng ticket hoàn toàn. **Chưa xử lý ở Phase 9** — đây là quyết định sản phẩm/vận hành, không phải tri thức để thường trực hóa; giữ nguyên trạng thái pending.
