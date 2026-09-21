# Kế hoạch triển khai — REVIEW-FINDING-DENSITY (Review Finding Density)

- **Ticket:** REVIEW-FINDING-DENSITY
- **Trạng thái:** Draft (Phase 3 — hoàn chỉnh)
- **Tạo ngày:** 2026-09-10 15:30:00
- **Cập nhật ngày:** 2026-09-10 23:15:00

> Nguồn tham chiếu chính: `docs/changes/REVIEW-FINDING-DENSITY/spec-pack.md`, `impact-analysis.md`, `source-availability.md`, `source-inventory.md`.

---

## 0. Nguyên tắc sử dụng template

Template này dùng cho Phase 3: **Implementation Plan + Impact Analysis**.

Khi điền tài liệu này:

1. Chỉ lập kế hoạch dựa trên nội dung trong `spec-pack.md`, đặc biệt là AC / NFR / Open Issues.
2. Không tự thêm scope, behavior, API, DB field, UI flow hoặc rule nghiệp vụ nếu không có trong spec.
3. Nếu thiếu thông tin để chốt implementation, ghi vào **Open Issues / Cần xác nhận trước khi implementation**.
4. Trước khi đọc code, phải liệt kê rõ **existing code cần đọc** ở mục 3.
5. Mỗi implementation step phải đủ nhỏ để review được trong một PR nhỏ hoặc một commit logic.
6. Group implementation steps theo feature tương ứng với nhóm AC trong `spec-pack.md`.
7. Cuối tài liệu bắt buộc có **AC mapping table**: mỗi AC được đáp ứng ở đâu và verify thế nào.

> **Ghi chú Phase 3:** Tài liệu này là bản hoàn chỉnh, thay thế bản đầu Phase 2. Mọi `[TBD ở Phase 3]` đã được điền dựa trên việc đọc source code thật (xem `source-availability.md`, `source-inventory.md`) và quyết định đã xác nhận với người dùng về xung đột FK/delete-reinsert (mục 1.3). Impact analysis đầy đủ 17 mục nằm ở `impact-analysis.md` — tài liệu này tập trung vào implementation steps.

---

## 1. Implementation policy

### 1.1. Tóm tắt policy

Ticket REVIEW-FINDING-DENSITY là **xây mới hoàn toàn** một luồng tính chỉ số (finding classification + density calculation) trên nền dữ liệu ingestion PR đã có, không phải refactor. Ba phần chính:

- **BE — Finding classification**: thêm bước phân loại sau khi `GitPrMetadataCollectorService.persistPullRequest` đồng bộ review/review comment, ghi vào `tbl_fact_finding` (bảng đã tồn tại, chưa có writer).
- **BE — Infrastructure mới**: GitHub GraphQL client (thread identity + `isResolved`) cô lập trong infrastructure layer; migration thêm `head_sha` vào `tbl_fact_pull_request_changed_file` và đổi khóa upsert.
- **BE + FE — Density calculation & hiển thị**: service tính density (mới) + API mới (route do Phase 3 quyết định) + hiển thị trên `TicketDetailDrawer.tsx` (FE).

Phần phải giữ nguyên: toàn bộ hành vi hiện có của `persistPullRequest` (upsert PR/commit/changed-file, xóa-chèn lại review/review comment) — chỉ được **thêm** bước mới, không sửa logic cũ ngoài phạm vi đổi khóa `tbl_fact_pull_request_changed_file` (OI-002).

Nguyên tắc tổng quát:

1. **Bám sát spec-pack**: chỉ triển khai các behavior có AC/NFR/source trong `docs/changes/REVIEW-FINDING-DENSITY/spec-pack.md`.
2. **Không mở rộng ngoài đặc tả**: mọi điểm chưa rõ hoặc có nhiều cách hiểu được chuyển sang Open Issue.
3. **Giới hạn blast radius**: ưu tiên thay đổi tại module ingestion (`application/usecase/ingestion`, `infrastructure/github`, `infrastructure/persistence/adapter`) và `pm-dashboard` FE; tránh refactor framework hoặc shared module nếu không bắt buộc.
4. **Compatibility**: giữ nguyên behavior hiện tại cho các flow không được ticket chạm tới (AC-REG-1).
5. **Reviewability**: chia step nhỏ, mỗi step có file thay đổi và cách verify riêng.

### 1.2. Non-goals / ngoài phạm vi

| #   | Nội dung không làm | Lý do / căn cứ |
| --- | ------------------- | -------------- |
| 1   | Dùng `tbl_fact_ai_finding_stat` hoặc bất kỳ AI-finding KPI nào làm nguồn | Spec §2.2 — bảng KPI riêng cho AI review, cấm dùng |
| 2   | Ranking cá nhân reviewer / đánh giá hiệu suất cá nhân dựa trên density | Spec §2.2 |
| 3   | Redesign toàn bộ PM Dashboard Ticket Detail | Spec §2.2 — chỉ thêm chỉ số vào màn hình hiện có |
| 4   | Dùng NLP/phân tích nội dung comment để phân loại finding | OI-003 (Closed) — quyết định metadata-only |

### 1.3. Phương án đã so sánh

| Vấn đề | Phương án A | Phương án B | Phương án chọn | Lý do chọn |
| --- | --- | --- | --- | --- |
| GraphQL client mới: gọi trực tiếp qua `WebClient` hiện có (POST tới `/graphql` với body query string) vs. thêm thư viện GraphQL client chuyên dụng (ví dụ Spring `HttpGraphQlClient` hoặc thư viện GraphQL Java client bên thứ 3) | Thêm dependency GraphQL client mới | Tái dùng `WebClient` (theo đúng pattern `WebClientConfig` hiện có — 1 `WebClient`/external API), POST JSON `{query, variables}` tới GitHub GraphQL endpoint, parse `JsonNode` giống REST adapter hiện tại | **Phương án B** | Không thêm dependency mới, giữ nhất quán với cách `GithubPullRequestMetadataAdapter` đang gọi REST (`.retrieve().bodyToMono(JsonNode.class).block()`); NFR §6.7 chỉ yêu cầu cô lập chi tiết GraphQL trong infra, không bắt buộc dùng client chuyên dụng |
| Vị trí gọi Finding writer trong `persistPullRequest`: gọi đồng bộ (block) ngay trong luồng `persistPullRequest` vs. gọi qua bean riêng `REQUIRES_NEW` như `AiFindingStatWriter` | Gọi trực tiếp trong service (method nội bộ) | Bean `@Component` riêng (`FindingWriter`), `@Transactional(REQUIRES_NEW)`, gọi từ `persistPullRequest` như một dependency injected | **Phương án B** | Self-invocation trong `GitPrMetadataCollectorService` sẽ bypass Spring AOP proxy, `REQUIRES_NEW` sẽ không có hiệu lực (đúng javadoc cảnh báo tại `AiFindingStatWriter.java:9-15`) |

### 1.5. Quyết định thiết kế bổ sung (phát hiện ngoài spec-pack, đã xác nhận với user)

Trong lúc đọc code thật ở Phase 3, phát hiện xung đột thiết kế nêu ở bảng 1.3 (dòng 1) — **không được spec-pack/context.md đề cập**. Theo `ticket-rules.md` ("Ambiguous points must be returned as Open Issues"), điểm này đã được đưa ra hỏi người dùng trước khi chốt plan. Quyết định: **Phương án B** — Finding writer tự quản lý vòng đời finding của nó theo `(pr_id, head_sha, thread_id)`, không sửa schema FK, không đổi flow review hiện có. Chi tiết thứ tự tác vụ cụ thể nằm ở Part B, step B.2.

### 1.4. Ràng buộc từ spec-pack

| #   | Ràng buộc             | Ảnh hưởng tới implementation |
| --- | --------------------- | ---------------------------- |
| 1   | Finding eligibility chỉ dựa metadata (review state hợp lệ sau chuẩn hóa + thread tồn tại qua GraphQL), không NLP (OI-003, Closed) | Classifier không được implement bất kỳ logic đọc hiểu nội dung comment nào; loại `UNKNOWN`/trống |
| 2   | Density = tổng finding / tổng changed lines × 1000, không phải trung bình per-PR (spec §5.3) | Aggregation phải thực hiện ở mức tổng tất cả PR liên kết ticket, không tính rồi average |
| 3   | `head_sha` bắt buộc trong khóa `tbl_fact_pull_request_changed_file` (OI-002, Closed) | Mọi truy vấn changed-lines phải truyền `head_sha` tường minh; cần migration + backfill |
| 4   | Rounding 1 chữ số thập phân, `RoundingMode.HALF_UP` (OI-004, Closed) | Không dùng scale=2 như `EvidenceQualityScoreService.scoreByRatio` — phải override scale |
| 5   | GraphQL client cô lập trong infrastructure, không rò rỉ ra ngoài (NFR §6.7) | DTO/response GraphQL không được dùng trực tiếp ở application/web layer |
| 6   | External HTTP call chạy ngoài DB transaction, mỗi upsert dùng `TransactionTemplate` riêng (`.claude/rules/20-architecture.md`) | GraphQL call + finding write phải tách transaction, theo pattern `AiFindingStatWriter` |
| 7   | Không tính lại từ GitHub API trực tiếp mỗi lần mở Ticket Detail (NFR §6.1) | Density API đọc từ dữ liệu đã lưu, không gọi GitHub runtime |
| 8   | Reviewer identity phải join `tbl_dim_member_pseudonym`, không raw user id (NFR §6.2) | Mọi bảng/log mới liên quan reviewer phải dùng `member_key` |

---

## 2. Impact analysis

> Bản đầy đủ (17 mục theo template `impact-analysis.md`) nằm ở `docs/changes/REVIEW-FINDING-DENSITY/impact-analysis.md`. Mục 2.x dưới đây là bản tóm tắt phục vụ implementation, đồng bộ nội dung với file đó.

### 2.1. Files / modules có thể bị ảnh hưởng

| #   | File / module        | Loại thay đổi                 | Lý do ảnh hưởng | AC liên quan | Ghi chú  |
| --- | -------------------- | ----------------------------- | --------------- | ------------ | -------- |
| 1   | `GitPrMetadataCollectorService.java` | Sửa (thêm bước gọi classifier) | Gắn finding classification sau khi review/comment được đồng bộ | AC-FINDING-CLASSIFY-* | Không sửa logic cũ |
| 2   | `GitPrMetadataCollectorJdbcAdapter.java` | Sửa (`upsertPullRequestChangedFile`) | Đổi khóa upsert theo `head_sha` | AC-RECALC-1 | OI-002 |
| 3   | `GithubPullRequestMetadataAdapter.java` / `GithubPullRequestMetadataPort.java` | Thêm method mới | Cần GraphQL fetch `reviewThreads`/`isResolved` | AC-FINDING-CLASSIFY-1, AC-STATUS-1 | OI-001; không sửa method REST hiện có |
| 4   | `TicketDetailDrawer.tsx` | Sửa (thêm hiển thị) | Hiển thị density + breakdown | AC-DENSITY-CALC-* | Theo pattern `formatFirstCiPassRate` |
| 5   | Finding writer mới (tên class do Phase 3 đặt) | Thêm mới | Ghi `tbl_fact_finding` | AC-FINDING-CLASSIFY-* | Pattern `AiFindingStatWriter` |
| 6   | Density service mới (tên class do Phase 3 đặt) | Thêm mới | Tính density cho ticket | AC-DENSITY-CALC-* | Pattern `EvidenceQualityScoreService.scoreByRatio` |

### 2.2. API contract có thể bị ảnh hưởng

**Quyết định (IMPL-OI-3, Resolved — xác nhận với user):** **không tạo endpoint riêng.** Density được nhúng trực tiếp vào response hiện có của `GET /api/v1/pm/dashboard/tickets/{ticketId}/detail` (`PmDashboardController.detail`, `PmDashboardDtos.PmDashboardTicketDetailDto`). Đây là thay đổi **response contract của endpoint hiện có** — khác với đánh giá "không có backward-compatibility risk" ở bản nháp trước; cần đối chiếu lại mục 14/16 impact-analysis.md.

Trường mới thêm vào `PmDashboardTicketDetailDto` (tên field cụ thể do implementation đặt theo convention camelCase hiện có của DTO):

```json
{
  "...": "...(các field hiện có của PmDashboardTicketDetailDto không đổi)",
  "reviewFindingDensity": {
    "density": 5.0,
    "unit": "findings/KLOC",
    "totalFindings": 5,
    "totalChangedLines": 1000,
    "breakdown": { "open": 2, "resolved": 2 }
  }
}
```

- `density` = `null` khi `totalChangedLines = 0` (FE hiển thị `"N/A"`, BE trả số thô/null, không trả string `"N/A"` — nhất quán với cách `firstCiPassTicketCount`/`ticketWithCiCount` hoạt động hiện tại).
- **Backward compatible**: có, vì đây là **thêm field mới** (`reviewFindingDensity`) vào object JSON hiện có, không đổi/xóa field cũ nào — client cũ (nếu có) bỏ qua field lạ theo hành vi JSON parser chuẩn, không có breaking change.
- Field mới đặt trong `PmDashboardTicketDetailDto.from(...)` — service `PmDashboardService.detail(ticketId, caller)` cần gọi thêm `ReviewFindingDensityService.calculate(ticketId)` trước khi build DTO.

### 2.3. DB / migration có thể bị ảnh hưởng

| #   | Bảng / collection / migration | Loại thay đổi             | Field / index / constraint | Data migration     | Rollback DB     | AC liên quan | Trạng thái             |
| --- | ------------------------------ | ------------------------- | --------------------------- | ------------------- | ---------------- | ------------- | ----------------------- |
| 1   | `tbl_fact_pull_request_changed_file` — migration **`V520__add_head_sha_to_pull_request_changed_file.sql`** (V518 là version cao nhất hiện có) | Alter | Thêm cột `head_sha VARCHAR(64)`, drop `uq_pr_changed_file`, thêm `UNIQUE (pr_id, head_sha, file_path)` | **Resolved (IMPL-OI-2, IMPL-OI-8)**: đọc `V4__init_shema_v2.sql:535-558` xác nhận `tbl_fact_pull_request` **không có** cột `head_sha`/head-commit nào để backfill chính xác → backfill row cũ bằng placeholder sentinel `'legacy'` (hoặc giá trị tương đương do implementation đặt tên, miễn nhất quán và phân biệt được với `head_sha` thật) | Down: drop cột `head_sha`, khôi phục `UNIQUE (pr_id, file_path_hash)` | AC-RECALC-1 | **Confirmed** |
| 2   | `tbl_fact_finding` | Không đổi schema (bảng đã tồn tại) | Ghi writer mới, không đổi cột. **FK `review_id`/`review_comment_id` giữ nguyên `NO ACTION`, không đổi sang `CASCADE`** (quyết định §1.3/§1.5) | Không cần | Không áp dụng (không đổi schema) | AC-FINDING-CLASSIFY-* | **Confirmed** — enum DB dùng chung được giữ nguyên; feature này chỉ ghi `OPEN`/`RESOLVED` |

### 2.4. Settings / config / feature flags

| # | Key / config | Loại thay đổi | Default | Environment impact | AC liên quan | Ghi chú |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | GraphQL endpoint config (tên biến cụ thể do implementation đặt, ví dụ mở rộng `AppProperties.connectors().github()` với `graphqlApiBaseUrl`) | Add — base URL GraphQL (`https://api.github.com/graphql`) khác REST (`https://api.github.com`), cần thêm 1 trường base URL mới trong `AppProperties`/config, dù cùng token | `https://api.github.com/graphql` (giá trị mặc định GitHub công khai, không phải secret) | Dev/Prod đều cần | AC-FINDING-CLASSIFY-1, AC-STATUS-1 | **Resolved (IMPL-OI-4)** — dùng chung `props.connectors().github().apiToken()` hiện có (cùng PAT với REST client), không cần credential riêng. Không đọc `.env` thật, chỉ tham chiếu tên biến theo `.claude/rules/00-safety.md §1` |

Không có feature flag mới theo yêu cầu spec — nếu cần giảm rủi ro rollout (rủi ro GraphQL rate limit, spec §9), có thể cân nhắc thêm flag bật/tắt Finding classification ở implementation, nhưng đây không phải yêu cầu bắt buộc từ spec-pack nên chỉ ghi nhận như một tùy chọn, không đưa vào step bắt buộc.

### 2.5. Logs / audit / monitoring

| #   | Event / log / metric | Khi nào ghi | Payload chính | Privacy / permission concern | AC/NFR liên quan  | Ghi chú  |
| --- | --------------------- | ------------ | --------------- | ------------------------------ | ------------------- | -------- |
| 1   | Finding created | Khi thread được phân loại thành finding | `pr_id`, `head_sha`, review/comment gốc, thời điểm | Không lộ raw user id | NFR §6.4, spec §5.X | |
| 2   | Finding status changed | Khi finding đổi trạng thái | trạng thái cũ/mới, nguồn thay đổi | Không lộ raw user id | AC-STATUS-* | |
| 3   | Density recalculated | Khi PR có commit mới | `pr_id`, `head_sha` cũ/mới, density trước/sau | Không lộ nội dung diff | AC-RECALC-1 | |

### 2.6. Permissions / roles

| # | Role / permission | Behavior được phép | Behavior bị chặn | UI impact | API/BE enforcement | AC liên quan |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | PM/QA (đúng theo spec §10 bảng truy vết: `PM/QA (read)`) | Xem density + breakdown trên Ticket Detail | Không có thao tác ghi nào ở phạm vi Part A-D (density hiển thị là read-only) | Card/badge mới chỉ hiển thị nếu user đã có quyền xem Ticket Detail (không có gate riêng bổ sung) | **Resolved (IMPL-OI-3)**: density nhúng vào `GET /tickets/{ticketId}/detail` hiện có → tự động thừa hưởng permission gate hiện có của endpoint đó (`PmDashboardService.detail(ticketId, caller)`), không cần thêm enforcement mới | Tất cả AC hiển thị |


### 2.7. Backward compatibility / data compatibility

| #   | Compatibility point | Rủi ro   | Cách xử lý     | AC/NFR liên quan  |
| --- | -------------------- | -------- | --------------- | ------------------- |
| 1   | Dữ liệu `tbl_fact_pull_request_changed_file` cũ không có `head_sha` | Query theo khóa mới có thể miss dữ liệu cũ nếu không backfill | **Resolved**: backfill placeholder sentinel `'legacy'` (IMPL-OI-2/8 — không có nguồn `head_sha` thật để backfill chính xác vì `tbl_fact_pull_request` không lưu cột này); row cũ mang `head_sha='legacy'` được coi là snapshot không xác định phiên bản, density tính trên dữ liệu này vẫn đúng cộng dồn nhưng không đối chiếu được với PR version cụ thể — chấp nhận được vì đây là dữ liệu lịch sử trước khi ticket này tồn tại | AC-RECALC-1, rủi ro #2 spec §9 |
| 2   | Finding writer ghi dữ liệu tham chiếu `review_id`/`review_comment_id` của lần đồng bộ trước, trong khi review sẽ bị xóa-chèn-lại ở lần đồng bộ sau | Vi phạm FK nếu thứ tự tác vụ sai (finding cleanup phải chạy trước `deleteReviewsByPrId`) | Finding writer tự dọn theo `pr_id` trước bước review reinsert (quyết định §1.3/§1.5); step B.4 phải implement đúng thứ tự và có test integration xác nhận | AC-FINDING-CLASSIFY-*, AC-REG-1 |
| 3   | Response của `GET /tickets/{ticketId}/detail` thay đổi (thêm field `reviewFindingDensity`) do quyết định IMPL-OI-3 (nhúng thay vì endpoint riêng) | FE client cũ (nếu có) không đọc field mới — an toàn; nhưng bất kỳ test snapshot nào assert full JSON response của endpoint này sẽ cần cập nhật | Cập nhật test snapshot/contract test của `PmDashboardTicketDetailDto` khi thêm field (step C.3); field mới không bắt buộc (`reviewFindingDensity` có thể `null`-safe object) | AC-DENSITY-CALC-*, AC-REG-1 |

---

## 3. Existing code cần đọc trước

### 3.1. Danh sách code cần đọc

| #   | File / module cần đọc | Mục đích đọc     | Câu hỏi cần trả lời trước khi sửa | Liên quan AC |
| --- | ---------------------- | ------------------ | ------------------------------------ | -------------- |
| 1   | `GitPrMetadataCollectorService.java` (toàn bộ, không chỉ 228-392) | Xác định chính xác điểm gắn bước classification, transaction boundary hiện tại | Bước classification gắn trước/sau `recalculateFromSourceChange`? Có transaction bao ngoài không? | AC-FINDING-CLASSIFY-*, AC-REG-1 |
| 2   | `GitPrMetadataCollectorJdbcAdapter.java` (toàn bộ) | Xác định đầy đủ SQL hiện tại của `upsertPullRequestChangedFile`, `insertReview`, `insertReviewComment` | Cần sửa SQL nào, giữ nguyên cột nào | AC-RECALC-1 |
| 3   | `GithubPullRequestMetadataAdapter.java` + `GithubPullRequestMetadataPort.java` | Xác định `WebClient` config hiện có để tái dùng cho GraphQL call | Có thể tái dùng cùng `WebClient`/auth hiện tại cho GraphQL endpoint không? | AC-FINDING-CLASSIFY-1, AC-STATUS-1 |
| 4   | `AiFindingStatWriter.java` + nơi gọi nó trong `GithubWebhookService.java` (~580-632) | Xác định chính xác pattern isolated writer + cách gọi tránh self-invocation | Finding writer mới nên được gọi từ đâu để tránh cùng lỗi proxy bypass? | AC-FINDING-CLASSIFY-* |
| 5   | `EvidenceQualityScoreService.java` (toàn bộ) | Xác định pattern rounding đầy đủ và nơi service này được gọi (`recalculateFromSourceChange`) | Density service có nên theo cùng entrypoint trigger (event-based) hay chỉ tính on-read? | AC-DENSITY-CALC-* |
| 6   | Migration mới nhất trong `db/migration/` (tìm version cao nhất hiện có) | Xác định số version tiếp theo cho migration `head_sha` | Version tiếp theo là bao nhiêu? | AC-RECALC-1 |
| 7   | `TicketDetailDrawer.tsx` + `SummaryCards.tsx` (toàn bộ) | Xác định cách các chỉ số khác được fetch/hiển thị (TanStack Query hook nào) | Dùng hook nào để fetch density, đặt ở đâu trong drawer? | AC-DENSITY-CALC-* |
| 8   | `tbl_fact_finding` enum `finding_status` definition thật (tìm trong migration) | Xác nhận `OPEN`/`RESOLVED` có sẵn | Không cần migration thêm; các enum value khác không thuộc write flow của feature | AC-STATUS-* |

### 3.2. Thứ tự đọc đề xuất

1. `GitPrMetadataCollectorService.java` — xác định flow chính và boundary.
2. `GitPrMetadataCollectorJdbcAdapter.java` — xác định data model/API hiện tại.
3. `GithubPullRequestMetadataAdapter.java`/`Port.java` — xác định khả năng mở rộng GraphQL.
4. `AiFindingStatWriter.java`, `EvidenceQualityScoreService.java` — xác định pattern tái dùng.
5. Migration mới nhất + definition `finding_status` — xác định version và enum thật.
6. `TicketDetailDrawer.tsx`, `SummaryCards.tsx` — xác định behavior cần thay đổi FE.
7. Test hiện có liên quan (nếu có) cho các file trên — xác định regression risk.

### 3.3. Điều kiện để mở rộng phạm vi đọc

Chỉ thêm file/module vào danh sách đọc nếu thỏa ít nhất một điều kiện:

- File được import trực tiếp bởi file đã đọc.
- File chứa type/API/constant được dùng bởi AC liên quan.
- Test hiện có fail hoặc mô tả behavior liên quan.
- Spec yêu cầu kiểm tra permission/log/config/DB ở area đó.

Khi thêm, cập nhật bảng 3.1 trước khi tiếp tục.

---

## 4. Changes / thiết kế thay đổi

### 4.0. Tổng quan thay đổi

| #   | Nhóm AC        | Hiện trạng          | Thay đổi        | State mới / chỉnh | API/DB             |
| --- | -------------- | -------------------- | ------------------ | ------------------- | -------------------- |
| 4.1 | AC-RECALC-* | `tbl_fact_pull_request_changed_file` không có `head_sha`, unique key `(pr_id, file_path_hash)` | Migration V519 thêm cột + đổi unique key; truyền `headSha` từ `ChangedFileSnapshot`/`PullRequestGraph` xuống adapter | cột `head_sha` | `tbl_fact_pull_request_changed_file` (alter) |
| 4.2 | AC-FINDING-CLASSIFY-*, AC-STATUS-* | Không có writer cho `tbl_fact_finding`; không có GraphQL client | GraphQL client mới (infra) + Finding classifier/writer mới (metadata-only, `REQUIRES_NEW`), tự dọn theo `pr_id`/`head_sha` trước bước review reinsert | `finding_id`, `status` (OPEN mặc định, RESOLVED khi `isResolved`) | `tbl_fact_finding` (ghi), GraphQL client mới (đọc GitHub) |
| 4.3 | AC-DENSITY-CALC-* | Không có logic density | Density service (tổng/tổng, N/A rule, HALF_UP scale=1) + endpoint `GET .../review-finding-density` mới | response `{density, totalFindings, totalChangedLines, breakdown}` | Endpoint mới (đọc `tbl_fact_finding` + `tbl_fact_pull_request_changed_file`) |
| 4.4 | Hiển thị FE (tất cả AC hiển thị) | `TicketDetailDrawer.tsx` chưa có chỉ số này | Thêm `useQuery` + `formatReviewFindingDensity` + card/badge mới | không có state Redux/Zustand mới (chỉ TanStack Query cache) | API mới ở 4.3 |

---

### 4.1. Migration & changed-lines versioning — `AC-RECALC-*`

**Diff SQL (V519, dựa trên `V181__git_pr_metadata_collector_schema.sql`):**

```diff
 CREATE TABLE IF NOT EXISTS tbl_fact_pull_request_changed_file (
     ...
     file_path TEXT NOT NULL,
     file_path_hash VARCHAR(128) NOT NULL,
+    head_sha VARCHAR(64),
     file_extension VARCHAR(50),
     ...
-    CONSTRAINT uq_pr_changed_file UNIQUE (pr_id, file_path_hash),
+    CONSTRAINT uq_pr_changed_file UNIQUE (pr_id, head_sha, file_path),
     CONSTRAINT ck_pr_changed_file_stats CHECK (additions >= 0 AND deletions >= 0)
 );
+-- backfill cho row cũ: policy cụ thể = IMPL-OI-2 (mục 9), chạy trong cùng migration hoặc migration riêng liền sau
```

**Diff Java (`PullRequestChangedFileUpsert`, `GitPrMetadataCollectorJdbcAdapter.upsertPullRequestChangedFile`):**

```diff
- record PullRequestChangedFileUpsert(UUID prId, UUID repositoryId, String filePath, String filePathHash,
-         String fileExtension, String changeType, int additions, int deletions, OffsetDateTime collectedAt) {}
+ record PullRequestChangedFileUpsert(UUID prId, UUID repositoryId, String headSha, String filePath, String filePathHash,
+         String fileExtension, String changeType, int additions, int deletions, OffsetDateTime collectedAt) {}
```

```diff
- ON CONFLICT (pr_id, file_path_hash) DO UPDATE
+ ON CONFLICT (pr_id, head_sha, file_path) DO UPDATE
```

**Design notes:**

- `headSha` lấy từ `PullRequestGraph.headSha()` (đã fetch trong `GithubPullRequestMetadataAdapter`, hiện bị bỏ qua ở `persistPullRequest` dòng 320-331) — truyền thẳng xuống, không cần fetch thêm.
- Không đổi cột `file_path_hash` — vẫn giữ để tương thích ngược với các query khác có thể dựa vào cột này (chỉ đổi unique constraint, không xóa cột).

---

### 4.2. GraphQL client + Finding classification & writer — `AC-FINDING-CLASSIFY-*`, `AC-STATUS-*`

**Diff kiến trúc (thêm mới, không sửa file cũ ngoài 1 điểm gọi trong `persistPullRequest`):**

```diff
  infrastructure/github/
    GithubPullRequestMetadataAdapter.java   (không đổi)
+   GithubReviewThreadAdapter.java          (mới — @Component, GraphQL client)

  application/port/out/integration/
    GithubPullRequestMetadataPort.java      (không đổi)
+   GithubReviewThreadPort.java             (mới — fetchReviewThreads(prId): List<ReviewThread{threadId, isResolved}>)

  application/usecase/ingestion/
    GitPrMetadataCollectorService.java
+     // sau bước review/comment reinsert (dòng ~389):
+     findingSyncCoordinator.syncFindings(prId, ticketId, headSha, graph.reviews(), reviewThreads);
+   FindingClassifier.java                  (mới — pure logic, metadata-only)
+   FindingWriter.java                      (mới — @Component, REQUIRES_NEW, theo pattern AiFindingStatWriter)
```

**Thứ tự tác vụ trong `persistPullRequest` (quyết định §1.3/§1.5 — quan trọng, tránh vi phạm FK):**

```diff
  // trong persistPullRequest, TRƯỚC dòng 342 (persistence.deleteReviewsByPrId(prId)):
+ findingWriter.deleteFindingsByPrId(prId);   // dọn finding cũ của PR này trước khi review cha bị xóa
  persistence.deleteReviewsByPrId(prId);
  // ... reinsert review/comment như hiện tại (dòng 344-389) ...
+ // SAU khi review/comment reinsert xong, có review_id mới:
+ List<ReviewThread> threads = githubReviewThreadPort.fetchReviewThreads(graph.externalPrId()); // GraphQL, ngoài DB transaction
+ List<FindingCandidate> findings = findingClassifier.classify(graph.reviews(), threads, externalReviewIdToDbId);
+ findingWriter.writeFindings(prId, ticketId, headSha, findings); // REQUIRES_NEW, transaction riêng
```

**Design notes:**

- `FindingClassifier.classify` xét state sau `normalizeReviewState`: nhận `APPROVED`, `CHANGES_REQUESTED`, `REVIEW_REQUIRED`, loại `UNKNOWN` — **không đọc `comment.body()`/`review.body()` để suy đoán ý nghĩa** (đúng OI-003).
- Dedupe: 1 GraphQL thread id → 1 finding, bất kể số comment trong thread (AC-FINDING-CLASSIFY-5).
- `FindingWriter.writeFindings` tự xóa finding cũ theo `pr_id` (đã làm ở bước trên) rồi insert lại theo `(pr_id, head_sha, thread_id)` — tương tự pattern delete-then-reinsert của review, nhưng scoped trong `REQUIRES_NEW` riêng để không phá transaction chính nếu GraphQL call chậm/lỗi (khi đó finding sync có thể fail độc lập mà không rollback PR/commit/review đã ghi).
- GraphQL call (`fetchReviewThreads`) phải nằm ngoài transaction ghi DB chính, đúng `.claude/rules/20-architecture.md` — gọi trước khi mở transaction `REQUIRES_NEW` của `FindingWriter`.

---

### 4.3. Density service + API — `AC-DENSITY-CALC-*`

**Diff logic (tham chiếu `EvidenceQualityScoreService.scoreByRatio`, khác ở scale và N/A rule):**

```diff
- private BigDecimal scoreByRatio(int numerator, int denominator, BigDecimal maxScore) {
-     if (denominator <= 0 || numerator <= 0) {
-         return ZERO;
-     }
-     BigDecimal ratio = BigDecimal.valueOf((double) numerator / (double) denominator);
-     return maxScore.multiply(ratio.min(BigDecimal.ONE)).setScale(2, RoundingMode.HALF_UP);
- }
+ private Optional<BigDecimal> calculateDensity(int totalFindings, int totalChangedLines) {
+     if (totalChangedLines <= 0) {
+         return Optional.empty(); // N/A — khác evidence quality: 0 finding hợp lệ khi denominator=0, vẫn phải phân biệt N/A
+     }
+     BigDecimal ratio = BigDecimal.valueOf(totalFindings)
+             .divide(BigDecimal.valueOf(totalChangedLines), 10, RoundingMode.HALF_UP)
+             .multiply(BigDecimal.valueOf(1000));
+     return Optional.of(ratio.setScale(1, RoundingMode.HALF_UP)); // OI-004: scale=1, không phải scale=2
+ }
```

**Design notes:**

- Aggregation phải SUM tất cả PR liên kết ticket **trước** khi chia (tổng finding / tổng changed lines), không tính ratio từng PR rồi average (AC-DENSITY-CALC-2) — query SQL nên `SUM(...)` ở tầng DB hoặc aggregate ở service sau khi lấy list, không map-then-average.
- `totalFindings = 0` nhưng `totalChangedLines > 0` → trả `0.0`, không phải N/A (AC-DENSITY-CALC-4) — code phải phân biệt rõ `Optional.empty()` (N/A) vs `Optional.of(ZERO)` (0 hợp lệ).
- Breakdown theo status không ảnh hưởng tổng (AC-DENSITY-CALC-5) — tổng luôn đếm tất cả finding bất kể status, breakdown là group-by riêng.
- Đọc từ dữ liệu đã lưu (`tbl_fact_finding` + `tbl_fact_pull_request_changed_file`), không gọi GitHub API runtime (NFR §6.1).
- **Quyết định (IMPL-OI-3, Resolved)**: không tạo controller/endpoint riêng. `ReviewFindingDensityService.calculate(ticketId)` được gọi từ `PmDashboardService.detail(ticketId, caller)` (service hiện có), kết quả nhúng vào `PmDashboardDtos.PmDashboardTicketDetailDto` dưới field `reviewFindingDensity` — xem contract đề xuất ở §2.2.

---

### 4.4. FE display — tất cả AC hiển thị

**Diff (theo pattern `formatFirstCiPassRate`, `SummaryCards.tsx:121-126`):**

```diff
+ function formatReviewFindingDensity(density: number | null): string {
+   if (density === null) return "N/A";
+   return `${density.toFixed(1)} findings/KLOC`;
+ }
```

```diff
  // TicketDetailDrawer.tsx, sau EqsSummaryCard (~line 544):
  // KHÔNG thêm useQuery mới (IMPL-OI-3 Resolved: không có endpoint riêng) —
  // `detail` prop hiện có của TicketDetailDrawer đã chứa `reviewFindingDensity` sau khi BE Part C hoàn tất.
+ <ReviewFindingDensityCard data={detail.reviewFindingDensity} />
```

**Design notes:**

- `density` từ BE là `number | null` (không phải string `"N/A"`) — FE tự format, nhất quán với cách `firstCiPassTicketCount`/`ticketWithCiCount` hoạt động hiện tại.
- Breakdown 2 trạng thái render dạng badge/list nhỏ, tương tự cách `SummaryBadges` hiển thị `aiQualityRate`/`defectLeakageRate` (dòng 439-446 theo Explore).
- **Không cần API client method mới** (IMPL-OI-3 Resolved) — field `reviewFindingDensity` đã có sẵn trong `detail` (fetch qua endpoint `.../detail` hiện có), chỉ cần cập nhật TypeScript type của `PmDashboardTicketDetail` để thêm field mới.

---

## 5. Implementation steps

### Part A — Migration & changed-lines `head_sha` (AC-RECALC-1, hạ tầng cho AC-DENSITY-CALC-*)

| #   | Step nhỏ, review được | Files dự kiến chỉnh | Output mong đợi     | Cách xác minh     | AC đáp ứng |
| --- | ----------------------- | ---------------------- | --------------------- | -------------------- | ------------ |
| A.1 | Thêm test JDBC adapter còn thiếu cho `upsertPullRequestChangedFile` với khóa cũ (bảo vệ trước khi đổi) — **IMPL-OI-7, Resolved: bổ sung test này là bắt buộc, không bỏ qua** | `GitPrMetadataCollectorJdbcAdapterTest.java` | Test pass với behavior hiện tại (khóa `(pr_id, file_path_hash)`) trước khi sửa, rồi cập nhật lại test cho khóa mới ở A.4 | `mvn test -Dtest=GitPrMetadataCollectorJdbcAdapterTest` | Gap-fill, không map AC trực tiếp |
| A.2 | Backfill row cũ bằng placeholder sentinel `'legacy'` — **IMPL-OI-2/IMPL-OI-8, Resolved**: đã đọc `V4__init_shema_v2.sql:535-558`, xác nhận `tbl_fact_pull_request` không có cột `head_sha`/head-commit nào để backfill chính xác, không cần đọc thêm | Không đổi file, quyết định đã chốt | Không cần bước đọc thêm — dùng thẳng placeholder ở migration A.3 | Đối chiếu lại migration A.3 khi review | AC-RECALC-1 |
| A.3 | Viết migration `V520__add_head_sha_to_pull_request_changed_file.sql`: thêm cột `head_sha VARCHAR(64)`, `UPDATE ... SET head_sha = 'legacy' WHERE head_sha IS NULL`, drop+tạo lại unique constraint `(pr_id, head_sha, file_path)` | `EDCAP_BE/src/main/resources/db/migration/V520__add_head_sha_to_pull_request_changed_file.sql` | Migration chạy sạch trên DB dev có dữ liệu cũ | `mvn flyway:info` (không tự chạy `flyway:migrate` — theo `.claude/rules/00-safety.md §3`, cần user xác nhận trước khi migrate) | AC-RECALC-1 |
| A.4 | Thêm `headSha` vào `PullRequestChangedFileUpsert`, sửa `upsertPullRequestChangedFile` đổi `ON CONFLICT` target | `GitPrMetadataCollectorModels.java`, `GitPrMetadataCollectorJdbcAdapter.java` | SQL upsert theo khóa mới | Test A.1 cập nhật lại + test mới cho khóa `(pr_id, head_sha, file_path)` | AC-RECALC-1 |
| A.5 | Truyền `headSha` từ `PullRequestGraph.headSha()` xuống `persistPullRequest` khi gọi `upsertPullRequestChangedFile` | `GitPrMetadataCollectorService.java` (dòng 320-331) | `headSha` không còn bị bỏ qua | Unit test `GitPrMetadataCollectorServiceTest` mới cho case PR có commit mới → `head_sha` đổi | AC-RECALC-1, AC-REG-1 |

### Part B — GraphQL client + Finding classifier/writer (AC-FINDING-CLASSIFY-*, AC-STATUS-*, AC-RECALC-2)

| #   | Step nhỏ, review được | Files dự kiến chỉnh | Output mong đợi     | Cách xác minh     | AC đáp ứng |
| --- | ----------------------- | ---------------------- | --------------------- | -------------------- | ------------ |
| B.1 | Thêm `GithubReviewThreadPort` (interface) + `GithubReviewThreadAdapter` (`@Component`); thêm bean `WebClient` mới trong `WebClientConfig` cho base URL `https://api.github.com/graphql` (khác REST), **dùng chung `props.connectors().github().apiToken()`** (IMPL-OI-4, Resolved — không cần credential riêng) | `application/port/out/integration/GithubReviewThreadPort.java`, `infrastructure/github/GithubReviewThreadAdapter.java`, `config/WebClientConfig.java` (thêm bean `githubGraphqlWebClient`) | Method `fetchReviewThreads(prExternalId)` trả `List<ReviewThread{threadId, isResolved}>` | Unit test mock `WebClient`/`ExchangeFunction`, test parse JSON response mẫu | AC-FINDING-CLASSIFY-1, AC-STATUS-1 |
| B.2 | Thêm `FindingClassifier` (pure logic, metadata-only): nhận review state hợp lệ, map mỗi GraphQL thread → 1 finding candidate, loại `UNKNOWN`/trống và dedupe theo thread id | `application/usecase/ingestion/FindingClassifier.java` | Danh sách `FindingCandidate` đúng theo AC-FINDING-CLASSIFY-1..5 | Unit test cho state hợp lệ và state không xác định | AC-FINDING-CLASSIFY-1..5 |
| B.4 | Gắn `findingWriter.deleteFindingsByPrId(prId)` **trước** `persistence.deleteReviewsByPrId(prId)` (dòng 342) và gọi classify+write **sau** khi review/comment reinsert xong (sau dòng 389) trong `persistPullRequest` | `GitPrMetadataCollectorService.java` | Thứ tự tác vụ đúng theo thiết kế §4.2, không đổi logic cũ | Integration test AC-REG-1 (snapshot behavior trước/sau) + test mới cho finding sync | AC-FINDING-CLASSIFY-*, AC-REG-1 |
| B.5 | Set `status = RESOLVED` khi `thread.isResolved()==true` tại thời điểm ghi (AC-STATUS-1); giữ `RESOLVED` khi PR có commit mới không liên quan (AC-STATUS-5) | `FindingWriter.java`/`FindingClassifier.java` | Status transition đúng theo bảng spec §5.2 | Unit test AC-STATUS-1, AC-STATUS-5 | AC-STATUS-1, AC-STATUS-5 |
| B.6 | Log 2 event: Finding created, Finding status changed (payload theo spec §5.X, không lộ raw user id) | `FindingWriter.java` (hoặc logger riêng) | Log xuất hiện đúng khi finding tạo/đổi trạng thái | Kiểm tra log output trong integration test | NFR §6.4 |

### Part C — Density service & API (AC-DENSITY-CALC-*)

| #   | Step nhỏ, review được | Files dự kiến chỉnh | Output mong đợi     | Cách xác minh     | AC đáp ứng |
| --- | ----------------------- | ---------------------- | --------------------- | -------------------- | ------------ |
| C.1 | Thêm port đọc `ReviewFindingDensityReadPort` (hoặc mở rộng port hiện có): query SUM finding + SUM changed lines theo tất cả PR liên kết ticket | `application/port/out/persistence/...`, `infrastructure/persistence/adapter/...` | Query trả `totalFindings`, `totalChangedLines`, breakdown theo status | Integration test với dữ liệu mẫu nhiều PR | AC-DENSITY-CALC-2 |
| C.2 | Thêm `ReviewFindingDensityService.calculate(ticketId)`: N/A khi denominator=0, rounding scale=1 `HALF_UP`, tổng/tổng (không average) | `application/usecase/.../ReviewFindingDensityService.java` | Kết quả đúng theo spec §5.3 ví dụ (5.25→5.3, 5.24→5.2) | Unit test AC-DENSITY-CALC-1,3,4,6 | AC-DENSITY-CALC-1,2,3,4,5,6 |
| C.3 | **IMPL-OI-3, Resolved: không tạo controller/DTO riêng.** Gọi `ReviewFindingDensityService.calculate(ticketId)` từ `PmDashboardService.detail(...)` hiện có, thêm field `reviewFindingDensity` vào `PmDashboardDtos.PmDashboardTicketDetailDto` (static factory `.from(...)`) | `application/usecase/pmdashboard/PmDashboardService.java`, `web/dto/PmDashboardDtos.java` | `GET /tickets/{ticketId}/detail` trả thêm field `reviewFindingDensity`, các field cũ không đổi | IT: gọi endpoint `/detail` với ticket có/không có PR liên kết, assert field mới + field cũ không đổi (contract test) | AC-DENSITY-CALC-2,3 |
| C.4 | Log event Density recalculated khi PR có commit mới trigger tính lại (nối tiếp Part B) | Service liên quan (Part B/C) | Log đúng payload spec §5.X | Kiểm tra log trong integration test | AC-RECALC-1, NFR §6.4 |

### Part D — FE display (tất cả AC hiển thị, không map AC riêng)

| #   | Step nhỏ, review được | Files dự kiến chỉnh | Output mong đợi     | Cách xác minh     | AC đáp ứng |
| --- | ----------------------- | ---------------------- | --------------------- | -------------------- | ------------ |
| D.1 | **IMPL-OI-3, Resolved: không cần API client method mới** — chỉ cập nhật TypeScript type `PmDashboardTicketDetail` để thêm field `reviewFindingDensity` (khớp response C.3) | File type definition tương ứng `PmDashboardTicketDetail` (interfaces/) | Type mới khớp response BE | `npm run typecheck` | — |
| D.2 | Thêm `formatReviewFindingDensity` (pure function, theo pattern `formatFirstCiPassRate`) + Vitest test | File cùng khu vực `formatXxxRate` khác (hoặc file utils mới cạnh `SummaryCards.tsx`) | `null → "N/A"`, số → `"X.X findings/KLOC"` | `npm test` | AC-DENSITY-CALC-3,4,6 (hiển thị) |
| D.3 | Thêm card/badge mới trong `TicketDetailDrawer.tsx` đọc trực tiếp từ `detail.reviewFindingDensity` (prop có sẵn, không thêm `useQuery` mới) (sau `EqsSummaryCard`, trước `TraceabilityIssuesSection`) | `TicketDetailDrawer.tsx` | Card hiển thị density + breakdown 2 trạng thái | Chạy dev server, mở Ticket Detail có PR liên kết, kiểm tra hiển thị | Tất cả AC hiển thị |

## 6. Risks & mitigation

| #   | Rủi ro     | Tác động            | Khả năng            | Biện pháp giảm thiểu | Cách phát hiện sớm | Owner     |
| --- | ---------- | --------------------- | --------------------- | ----------------------- | ---------------------- | ----------- |
| 1   | GitHub GraphQL rate limit/quota khác REST, ảnh hưởng tần suất đồng bộ PR | Medium | Medium | Đánh giá rate limit ở Phase 3; cân nhắc cache/batch theo PR | Theo dõi header rate-limit response từ GraphQL API | Tech lead |
| 2   | Migration thêm `head_sha` cần backfill dữ liệu cũ | Medium | Medium | Thiết kế migration/backfill cụ thể ở Phase 3, xác định giá trị mặc định | Review migration script trước khi chạy | DB owner |
| 3   | Rule metadata-only tính cả thread chỉ là câu hỏi/LGTM là finding, làm density cao hơn thực tế | High | Medium | Chấp nhận giới hạn đã confirm; không dùng NLP; cân nhắc thêm tiêu chí metadata khác (không phải NLP) ở phiên bản sau | Theo dõi phản hồi PM/QA sau khi release | PO/QA lead |
| 4   | Finding writer ghi finding tham chiếu `review_id` của lần đồng bộ trước trong khi review sắp bị xóa-chèn-lại → vi phạm FK nếu thứ tự tác vụ sai (phát hiện mới, không có trong spec-pack) | High (nếu implement sai thứ tự) | Medium | Step B.4 bắt buộc gọi `deleteFindingsByPrId` trước `deleteReviewsByPrId`; có integration test đồng bộ PR 2 lần liên tiếp để phát hiện sớm | FK violation exception khi chạy integration test B.3/B.4 | BE dev thực hiện Part B |
| 5   | `GitPrMetadataCollectorJdbcAdapterTest` hiện chỉ có 2 test, không cover `upsertPullRequestChangedFile`/review CRUD | Medium | High (nếu bỏ qua) | Step A.1 bắt buộc thêm test trước khi đổi unique key ở A.3/A.4 | Review code trước khi merge Part A | BE dev thực hiện Part A |
| 6   | `docs/architecture/*.md` (service-layer-map, repository-db-map, route-api-map, entrypoint-map, fe-be-contract-map) không mô tả `GitPrMetadataCollectorService`/`tbl_fact_finding`/`tbl_fact_review` — doc drift có sẵn, ticket này làm tăng thêm | Low (không ảnh hưởng chức năng) | Medium (nợ tài liệu) | Không thuộc scope ticket này; ghi nhận backlog cập nhật doc riêng, không block implementation | — | Tech lead (backlog, ngoài ticket) |

---

## 7. Rollback plan

### 7.1. Code rollback

1. Revert theo PR/step độc lập, ưu tiên thứ tự ngược lại với implementation: D → C → B → A.
2. Không có feature flag bắt buộc theo spec (xem §2.4) — nếu implementation quyết định thêm flag tùy chọn để giảm rủi ro GraphQL rate limit, tắt flag trước khi revert code Part B.
3. Không có state FE persisted phức tạp cần clear (chỉ hiển thị read-only, TanStack Query cache tự invalidate khi component unmount).
4. Nếu revert Part B sau khi Part A đã merge, `head_sha` vẫn giữ nguyên trong DB (không cần rollback riêng) — chỉ finding writer/GraphQL client bị revert.

### 7.2. DB rollback

| #   | DB change     | Rollback action     | Data loss risk     | Owner     |
| --- | ------------- | ---------------------- | --------------------- | ----------- |
| 1   | Thêm cột `head_sha` vào `tbl_fact_pull_request_changed_file` + đổi unique key | Down migration: drop cột, khôi phục unique key cũ | Có thể mất dữ liệu `head_sha` đã backfill nếu rollback sau khi có dữ liệu mới ghi theo khóa mới | DB owner |

### 7.3. Config / feature flag rollback

Không áp dụng — spec không yêu cầu feature flag (xem §2.4). Nếu implementation tự thêm flag tùy chọn cho GraphQL/Finding classification, ghi bổ sung ở đây trước khi merge.

---

## 8. Verification procedure

### 8.1. Automated verification

```bash
# BE (chạy trong EDCAP_BE/)
mvn clean verify

# FE (chạy trong EDCAP_FE/)
npm run typecheck
npm run build
npm test
```

### 8.2. Manual verification matrix

| #   | Scenario          | Preconditions     | Steps            | Expected result     | AC/NFR     |
| --- | ----------------- | ----------------- | ---------------- | ------------------- | ---------- |
| 1   | Ticket có 1 PR, review thuộc các state hợp lệ với 3 thread | PR đã sync qua webhook/poll | Mở Ticket Detail | 3 finding hiển thị trong breakdown OPEN, density = 3/M×1000 | AC-FINDING-CLASSIFY-1, AC-DENSITY-CALC-1 |
| 2   | Ticket có 2 PR liên kết | Cả 2 PR đã sync, có finding + changed lines khác nhau | Mở Ticket Detail | Density = tổng finding / tổng changed lines × 1000, không phải average | AC-DENSITY-CALC-2 |
| 3   | Ticket có PR nhưng changed lines = 0 (chỉ đổi file binary) | PR đã sync | Mở Ticket Detail | Hiển thị `N/A`, không hiển thị `0` | AC-DENSITY-CALC-3 |
| 4   | PR có commit mới sau khi đã có finding từ `head_sha` cũ | PR đã sync 2 lần với `head_sha` khác nhau | Mở Ticket Detail sau lần sync thứ 2 | Changed lines tính theo `head_sha` mới nhất; finding cũ không tự resolve | AC-RECALC-1, AC-RECALC-2 |
| 5   | GitHub thread được resolve trên GitHub (qua GraphQL `isResolved`) | Finding đã tồn tại ở trạng thái OPEN | Đồng bộ lại PR | Finding chuyển sang RESOLVED trong breakdown | AC-STATUS-1 |

### 8.3. Regression checks

| #   | Existing behavior cần giữ | Check            | Expected result     |
| --- | --------------------------- | ------------------ | ---------------------- |
| 1   | `persistPullRequest` upsert PR/commit/changed-file/review/review-comment | Chạy lại `GitPrMetadataCollectorServiceTest` hiện có + integration test thủ công với PR mẫu | Hành vi giống hệt trước khi thêm finding classification (AC-REG-1) |
| 2   | `GET /api/v1/pm/dashboard/tickets/{ticketId}/detail` (endpoint hiện có) | Gọi lại endpoint sau khi thêm endpoint density mới | Response không đổi, không có field mới nào bị nhúng vào (density là endpoint riêng) | AC-REG-1 |
| 3   | Đồng bộ PR không có review nào (review list rỗng) | Chạy `persistPullRequest` với PR không có review | Không lỗi, không tạo finding nào, `deleteFindingsByPrId` chạy an toàn kể cả khi chưa có finding | AC-REG-1 |

---

## 9. Open Issues / Cần xác nhận trước khi implementation

| ID        | Chủ đề    | Câu hỏi cần chốt | Ai chốt   | Block bước   | Mức độ block            | Trạng thái |
| --------- | --------- | ------------------- | ----------- | -------------- | -------------------------- | ------------ |
| IMPL-OI-1 | Migration version | Số version migration mới cho `head_sha` là bao nhiêu? | Tech lead/DB owner | Part A | — | **Resolved** — đọc `db/migration/` thực tế (numeric sort), version cao nhất hiện có là **V518** → migration mới là **V519** |
| IMPL-OI-2 | Backfill policy | Dữ liệu `tbl_fact_pull_request_changed_file` cũ (không có `head_sha`) backfill bằng giá trị gì? | User (xác nhận) | Part A (step A.2, A.3) | — | **Resolved** — user xác nhận `tbl_fact_pull_request` chưa có cột head-sha (khớp với IMPL-OI-8) → backfill bằng placeholder sentinel `'legacy'` ở migration V519 |
| IMPL-OI-3 | API route | Route/method cụ thể cho density endpoint mới là gì? | User (xác nhận) | Part C (step C.3) | — | **Resolved** — không tạo API riêng; nhúng `reviewFindingDensity` vào response hiện có của `GET /api/v1/pm/dashboard/tickets/{ticketId}/detail` (xem §2.2, §4.3) |
| IMPL-OI-4 | GraphQL auth | GraphQL client dùng chung credential/token với REST client hiện tại hay cần cấu hình riêng? | User (xác nhận) | Part B (step B.1) | — | **Resolved** — dùng chung `props.connectors().github().apiToken()` hiện có; chỉ cần thêm base URL GraphQL riêng (`/graphql`), không cần credential mới |
| IMPL-OI-5 | `finding_status` enum thật | Enum DB có `OPEN`/`RESOLVED` để feature sử dụng không? | Tech lead | Part B | — | **Resolved** — đọc `V4__init_shema_v2.sql:49`: enum dùng chung có `OPEN` và `RESOLVED`; feature không tạo các status triage khác |
| IMPL-OI-6 | FK/delete-reinsert conflict (phát hiện mới) | `tbl_fact_finding` không có `ON DELETE CASCADE` từ `review_id`; `persistPullRequest` xóa-chèn-lại review mỗi lần sync | User (product/tech) | Part B (step B.4) | — | **Resolved** — quyết định: Finding writer tự dọn theo `pr_id` trước bước xóa review (xem §1.3/§1.5) |
| IMPL-OI-7 | Test gap | `GitPrMetadataCollectorJdbcAdapterTest` không cover `upsertPullRequestChangedFile`/review CRUD trước khi đổi unique key | User (xác nhận) | Part A (step A.1) | — | **Resolved** — user xác nhận: bổ sung test cần thiết là bắt buộc ở step A.1 trước khi sửa A.3/A.4, không bỏ qua |
| IMPL-OI-8 | `tbl_fact_pull_request` head-sha column | Bảng `tbl_fact_pull_request` có sẵn cột lưu head commit sha để backfill chính xác không? | User (xác nhận qua đọc migration) | Part A (step A.2) | — | **Resolved** — đọc `V4__init_shema_v2.sql:535-558` (`tbl_fact_pull_request`): không có cột `head_sha`/head-commit nào → xác nhận phải dùng placeholder (IMPL-OI-2) |

**Tất cả Open Issues (IMPL-OI-1..9) đã Resolved — không còn điểm Blocking nào cho Part A-E.**

### Checklist xác nhận trước khi bắt đầu implementation

- [x] Tất cả AC trong `spec-pack.md` đã được đưa vào bảng mapping mục 10.
- [x] Existing code cần đọc đã được liệt kê trước ở mục 3.
- [x] Các Open Issue blocking đã có owner và bước bị block rõ ràng — **tất cả 8 Open Issue đã Resolved** (mục 9).
- [x] API contract đã chốt nếu có API impact — nhúng vào `GET .../detail` hiện có (IMPL-OI-3).
- [x] DB migration / rollback đã chốt nếu có DB impact — V519, backfill `'legacy'` (IMPL-OI-2/8).
- [x] Permission / role behavior đã chốt nếu có phân quyền — thừa hưởng từ endpoint `/detail` hiện có.
- [x] Log / audit / monitoring đã chốt nếu spec hoặc NFR yêu cầu — 3 event ở §2.5.
- [ ] Test plan đủ cover AC chính và regression quan trọng.

---

## 10. AC mapping table

| #   | AC ID       | Nội dung AC tóm tắt | Implementation step đáp ứng | Files/modules liên quan | Verification      | Open Issue nếu có  |
| --- | ----------- | --------------------- | ------------------------------ | -------------------------- | -------------------- | ---------------------- |
| 1   | AC-FINDING-CLASSIFY-1/v1 | Review state hợp lệ có thread → mỗi thread thành 1 finding | B.1, B.2, B.3 | `GithubReviewThreadAdapter`, `FindingClassifier`, `FindingWriter`, `tbl_fact_finding` | UT, IT, BB | IMPL-OI-4 |
| 2   | AC-FINDING-CLASSIFY-2/v1 | Review `UNKNOWN`/trống/không nhận diện → không tạo finding | B.2 | `FindingClassifier` | UT, IT, BB | |
| 3   | AC-FINDING-CLASSIFY-3/v1 | Thread chỉ là câu hỏi/LGTM vẫn tính là finding (metadata-only) | B.2 | `FindingClassifier` | UT, BB | |
| 4   | AC-FINDING-CLASSIFY-4/v1 | Nhiều thread trong 1 review → mỗi thread 1 finding | B.2 | `FindingClassifier` | UT, IT, BB | |
| 5   | AC-FINDING-CLASSIFY-5/v1 | Nhiều comment cùng thread → gộp 1 finding (dedupe theo thread identity GraphQL) | B.1, B.2 | `GithubReviewThreadAdapter`, `FindingClassifier` | UT, IT, BB | (IMPL-OI-4 Resolved) |
| 6   | AC-DENSITY-CALC-1/v1 | N finding/M dòng (M>0) → N/M×1000 | C.1, C.2 | `ReviewFindingDensityService` | UT, IT, BB | |
| 7   | AC-DENSITY-CALC-2/v1 | Nhiều PR → tổng/tổng, không trung bình | C.1, C.2, C.3 | `ReviewFindingDensityService`, read port SUM query, `PmDashboardService.detail` | UT, IT, E2E, BB | |
| 8   | AC-DENSITY-CALC-3/v1 | Tổng changed lines=0 → N/A | C.2 | `ReviewFindingDensityService` | UT, IT, BB | |
| 9   | AC-DENSITY-CALC-4/v1 | 0 finding + changed lines>0 → 0 (không N/A) | C.2 | `ReviewFindingDensityService` | UT | |
| 10  | AC-DENSITY-CALC-5/v1 | Breakdown theo status không đổi tổng density | C.1, C.2 | `ReviewFindingDensityService` | UT, IT | |
| 11  | AC-DENSITY-CALC-6/v1 | Rounding 1 chữ số HALF_UP | C.2 | `ReviewFindingDensityService` | UT, BB | |
| 12  | AC-RECALC-1/v1 | Commit mới → tính lại changed lines theo `head_sha` mới | A.3, A.4, A.5 | Migration V519, `upsertPullRequestChangedFile`, `GitPrMetadataCollectorService` | UT, IT | (IMPL-OI-2/8 Resolved) |
| 13  | AC-RECALC-2/v1 | Commit mới không tự resolve finding cũ | B.3, B.5 | `FindingWriter` | UT, IT | |
| 14  | AC-STATUS-1/v1 | GitHub thread resolve (GraphQL) → finding `RESOLVED` | B.1, B.5 | `GithubReviewThreadAdapter`, `FindingWriter` | UT, IT | (IMPL-OI-4 Resolved) |
| 18  | AC-STATUS-5/v1 | Finding `RESOLVED` giữ nguyên khi có commit mới không liên quan | B.5 | `FindingWriter`, `FindingClassifier` | UT | |
| 19  | AC-REG-1/v1 | Hành vi đồng bộ PR hiện có không đổi | A.1, A.5, B.4 | `GitPrMetadataCollectorService`, `GitPrMetadataCollectorJdbcAdapter` | UT, IT, E2E, BB | |

---

## 11. Output

Sau khi hoàn tất Phase 3, output bắt buộc:

- `docs/changes/REVIEW-FINDING-DENSITY/impl-plan.md` (tài liệu này)
- `docs/changes/REVIEW-FINDING-DENSITY/impact-analysis.md` (17 mục đầy đủ)
- `docs/changes/REVIEW-FINDING-DENSITY/source-availability.md`
- `docs/changes/REVIEW-FINDING-DENSITY/source-inventory.md`
- Checklist các điều cần xác nhận trước khi implementation bắt đầu, nằm trong mục 9 — còn 5 Open Issue chưa Resolved (IMPL-OI-2, 3, 4, 7, 8), cần chốt trước khi bắt đầu Part tương ứng
