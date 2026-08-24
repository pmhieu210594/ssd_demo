# test-plan

> **Ticket:** AI-REVIEW-KPI-IMPROVEMENT · **Vai trò:** Test Strategist · **Ngày:** 2026-08-20
> **Nguồn AC:** [spec-pack.md §6](spec-pack.md) (AC-AIRKI-1 → AC-AIRKI-12) · **Bằng chứng:** đọc trực tiếp source + test hiện có trên HEAD (không suy diễn từ self-review.md/codex-review.md).

## Mục tiêu

Với từng AC-AIRKI-1..12, xác định: (a) đã có test bảo đảm hay chưa, (b) nếu có — test nào, ở tầng nào; (c) nếu chưa/chưa đủ — cần thêm test loại gì, giá trị/chi phí ra sao. Không tạo test theo kiểu máy móc (không phải AC nào cũng cần cả 7 loại) — chỉ thêm ở chỗ có gap thật, đã xác minh bằng cách đọc code, không suy diễn theo tên hàm. Đồng thời rà 12 trục cross-cutting theo yêu cầu (auth/permission/tenant/idempotency/duplicate submit/rollback/race/timeout/boundary/malformed/null/empty/full-width number) và gắn chúng vào đúng AC liên quan thay vì tạo mục riêng.

## Ma trận AC ↔ loại test

| AC | Tóm tắt | Loại test | Trạng thái | Test/bằng chứng cụ thể |
| --- | --- | --- | --- | --- |
| AC-AIRKI-1 | Parse thành công, upsert đúng 1 row khi PR merge | BE UT + API IT (webhook) | **Được bảo đảm bởi test hiện có** | `AiReviewStatsParserTest.parse_templateExactLabels_extractsPositionalPairs`; `GithubWebhookServiceTest.ai_finding_stats_recorded_when_ai_review_md_changed_and_pr_merged` |
| AC-AIRKI-2 | Độc lập ngôn ngữ — heading `## 8.` | BE UT | **Được bảo đảm bởi test hiện có** | `AiReviewStatsParserTest.parse_englishHeadingAndLabels_locatesByHeadingNumberNotTranslatedText` |
| AC-AIRKI-3 | Độc lập ngôn ngữ — label dòng, map theo vị trí | BE UT | **Có test nhưng chưa đạt đúng yêu cầu AC** — gap | `parse_realWorldRewordedLabelsWithNotApplicableRow_mapsByPositionNotLabelText` dùng text block gõ tay mô phỏng `raw/ai-review.md`, KHÔNG đọc trực tiếp file thật qua `Files.readString`. AC yêu cầu tường minh "dùng chính file mẫu thực tế `raw/ai-review.md` làm test fixture" → xem Test mới/cập nhật #1 |
| AC-AIRKI-4 | Giá trị not-applicable → NULL toàn nhóm chia sẻ mẫu số | BE UT | **Được bảo đảm bởi test hiện có** | 4 test `parse_*RowNotApplicableWhileSiblingsNumeric_nullsWholeSharedGroup` (thêm trong phiên fix codex-review Finding 1) |
| AC-AIRKI-5 | Chống double-count khi ticket merge lại (PR khác) | BE UT (SQL-text) | **Chỉ được bảo đảm gián tiếp** — gap | `AiFindingStatJdbcAdapterTest.upsert_writesAllCountsAndUsesTicketIdConflictTarget` xác nhận `ON CONFLICT (ticket_id) DO UPDATE`, nhưng không có test hành vi ở tầng `GithubWebhookService` cho kịch bản "merge lại ticket X lần 2" → xem Test mới/cập nhật #2 |
| AC-AIRKI-6 | Chống double-count khi webhook redeliver | BE UT (SQL-text) | **Chỉ được bảo đảm gián tiếp** — gap | Cùng cơ chế `ON CONFLICT` như AC-5, nhưng không có test riêng cho redelivery ở tầng webhook service (khác 5 test `ai_finding_stats_*` hiện có, không test nào giả lập gọi `handle()` 2 lần với cùng `delivery-id`) → gộp chung với Test mới/cập nhật #2 |
| AC-AIRKI-7 | Cô lập lỗi ghi — không fail PR-merge flow | BE UT | **Được bảo đảm bởi test hiện có** | `ai_finding_stats_write_failure_does_not_fail_pr_merge_flow` (giả lập `QueryTimeoutException`, xác nhận `result.handled()` vẫn `pull_request:closed`) |
| AC-AIRKI-8 | Fetch lỗi 401/403/404 → skip, không fail webhook | BE UT | **Được bảo đảm bởi test hiện có** | `ai_finding_stats_skipped_silently_on_401_403_404_blob_fetch_errors` |
| AC-AIRKI-9 | API tổng hợp `SUM/SUM`, `null` khi tổng mẫu số = 0 | BE UT (2 tầng) | **Tầng SUM có test, tầng rate/ROUND/null-khi-0 KHÔNG có test trực tiếp** — gap | `PmDashboardJdbcAdapterFindAiFindingStatsTest` xác nhận SQL `SUM(...)`/tenant scope; nhưng `PmDashboardDtos.AiFindingStatsDto.rate()` (dòng 76-80, `Math.round(numerator*1000.0/denominator)/10.0`, `null` khi `denominator == 0`) **không có test nào gọi trực tiếp** — test controller hiện có (`aiFindingStats_returnsComputedRates`) chỉ tình cờ đi qua `rate()` với đúng 1 tổ hợp số (1/1, 2/2, 0/2), chưa từng exercised trường hợp mẫu số = 0 thật hay số lẻ (rounding boundary) → xem Test mới/cập nhật #3 |
| AC-AIRKI-10 | Permission — chỉ PM (đúng project) hoặc ADMIN | BE UT | **Có test nhưng thiếu 1 nhánh so với pattern sibling** — gap nhỏ | `aiFindingStats_requiresPmRole` (VIEWER → 403), `aiFindingStats_allowsAdminRoleAndReturnsRowFromRepository` (ADMIN), `PmDashboardControllerTest.aiFindingStats_mapsForbidden`. Thiếu: test kiểu `templateUsage_allowsProjectRolePmEvenWhenSystemRoleIsDifferent`/`summary_allowsProjectRolePmEvenWhenSystemRoleIsDifferent` đã có cho các endpoint anh em cùng file — tức "role hệ thống khác nhưng có role PM đúng project vẫn được phép" chưa có bản tương ứng cho `aiFindingStats` → xem Test mới/cập nhật #4 |
| AC-AIRKI-11 | FE hiển thị 5 rate, `-` khi null | FE UT | **Được bảo đảm bởi test hiện có (có 1 điểm lệch tài liệu cần note)** | `AiFindingStatsCard.test.tsx`: loading state, render đủ 5 rate, `-` khi `null`. **Lưu ý:** AC-AIRKI-11 ghi rõ "dùng `CDataTable`", nhưng code thực tế (`AiFindingStatsCard.tsx`) dùng lưới card (`div grid`), không phải `CDataTable` — xem Rủi ro còn lại |
| AC-AIRKI-12 | AC Closure — mount thật trong `PMDashboardPage.tsx` | FE IT (component mount) | **Được bảo đảm bởi test hiện có** | `PMDashboardPage.test.tsx`: assert `AiFindingStatsCard` heading xuất hiện đúng vị trí DOM (trước `AllTicketsTable`) + `endpoints.pmDashboard.getAiFindingStats` được gọi đúng `projectId`/`repositoryId` |

Không có AC nào cần Contract test riêng (không có consumer bên ngoài ngoài chính FE/BE trong repo này — contract giữa FE/BE được phủ bằng API IT + FE UT ở trên) và không AC nào cần E2E/Black-box thật sự mới (rủi ro đủ thấp, đã phủ bằng UT/IT theo tầng — xem Vùng cố ý không test lần này).

## Ưu tiên

- **P0 (phải làm trước khi đóng ticket):**
  - Test mới/cập nhật #1 (AC-3 dùng fixture thật) — chi phí thấp (đổi 1 chỗ đọc file), giá trị cao (đúng nguyên văn AC, đây là gap do chính codex-review đã chỉ ra).
  - Test mới/cập nhật #3 (AC-9 rate()/null-khi-0/rounding) — chi phí thấp, giá trị cao nhất trong danh sách: đây là công thức tính KPI hiển thị trực tiếp cho PM, chưa từng được test với mẫu số = 0 thật.
- **P1 (nên làm, không chặn release):**
  - Test mới/cập nhật #2 (AC-5/AC-6 hành vi re-merge/redelivery ở tầng webhook service) — chi phí trung bình (cần dựng lại 2 lần gọi `service.handle(...)` với cùng ticket/cùng delivery-id), giá trị trung bình-cao vì đây là 2 AC "chống double-count" — rủi ro nghiệp vụ nếu sai (đếm trùng KPI).
  - Test mới/cập nhật #4 (AC-10 nhánh project-role-PM) — chi phí thấp (copy pattern có sẵn), giá trị trung bình (đóng khoảng trống nhất quán với 3 endpoint anh em).
- **P2 (cân nhắc, không bắt buộc):**
  - Test full-width number cho `extractCell` (xem Test mới/cập nhật #5) — chi phí rất thấp nhưng giá trị thấp vì hành vi hiện tại (fallback về `null`) đã đúng theo AC-4 dù chưa có test tường minh riêng cho input full-width.

## Tái sử dụng test hiện có

| AC | File test | Test method |
| --- | --- | --- |
| AC-1 | `AiReviewStatsParserTest.java` | `parse_templateExactLabels_extractsPositionalPairs` |
| AC-1 | `GithubWebhookServiceTest.java` | `ai_finding_stats_recorded_when_ai_review_md_changed_and_pr_merged` |
| AC-1 | `GithubWebhookServiceTest.java` | `ai_finding_stats_skipped_when_no_ai_review_md_in_diff` |
| AC-2 | `AiReviewStatsParserTest.java` | `parse_englishHeadingAndLabels_locatesByHeadingNumberNotTranslatedText` |
| AC-4 | `AiReviewStatsParserTest.java` | 4× `parse_*RowNotApplicableWhileSiblingsNumeric_nullsWholeSharedGroup` |
| AC-4 | `AiReviewStatsParserTest.java` | `parse_noSection8_returnsEmpty`, `parse_section8WithoutTable_returnsEmpty` (empty/malformed) |
| AC-5/AC-6 (một phần) | `AiFindingStatJdbcAdapterTest.java` | `upsert_writesAllCountsAndUsesTicketIdConflictTarget` |
| AC-7 | `GithubWebhookServiceTest.java` | `ai_finding_stats_write_failure_does_not_fail_pr_merge_flow` |
| AC-8 | `GithubWebhookServiceTest.java` | `ai_finding_stats_skipped_silently_on_401_403_404_blob_fetch_errors`, `ai_finding_stats_skipped_silently_when_section_8_missing` |
| AC-9 (một phần — SUM + tenant scope) | `PmDashboardJdbcAdapterFindAiFindingStatsTest.java` | tất cả 4 test (bao gồm 2 test tenant-isolation thêm trong phiên fix codex-review Finding 2) |
| AC-10 | `PmDashboardServiceTest.java` | `aiFindingStats_requiresPmRole`, `aiFindingStats_allowsAdminRoleAndReturnsRowFromRepository`, `aiFindingStats_returnsZeroedRowWhenRepositoryNotFound` |
| AC-10 | `PmDashboardControllerTest.java` | `aiFindingStats_returnsComputedRates`, `aiFindingStats_mapsForbidden` |
| AC-11 | `AiFindingStatsCard.test.tsx` | cả 3 test (loading/render/`-` khi null) |
| AC-12 | `PMDashboardPage.test.tsx` | đoạn assert DOM ordering + `getAiFindingStats` params (dòng ~237-248) |

## Test mới/cập nhật

1. **AC-3 — dùng fixture thật thay vì text gõ tay** (BE UT, sửa test hiện có)
   - File: `AiReviewStatsParserTest.java`, test `parse_realWorldRewordedLabelsWithNotApplicableRow_mapsByPositionNotLabelText`.
   - Đổi input từ text block gõ tay sang `Files.readString(Path.of("../documents/docs/changes/AI-REVIEW-KPI-IMPROVEMENT/raw/ai-review.md"))` (hoặc copy file vào `src/test/resources` nếu muốn tránh phụ thuộc đường dẫn tương đối ra ngoài module — ưu tiên copy vào resources để test không phụ thuộc cấu trúc `documents/` bên ngoài `EDCAP_BE/`).
   - Lý do: đúng nguyên văn AC-AIRKI-3 ("dùng chính file mẫu thực tế `raw/ai-review.md` làm test fixture"), và đây là gap `codex-review.md` đã nêu, tôi xác minh lại độc lập là đúng (đọc trực tiếp dòng 50-80 của test file).

2. **AC-5/AC-6 — test hành vi chống double-count ở tầng webhook service** (BE UT mới, thêm 2 test vào `GithubWebhookServiceTest.java` cùng nhóm `ai_finding_stats_*`)
   - `ai_finding_stats_reMergeSameTicket_overwritesNotDuplicates`: gọi `service.handle(...)` 2 lần với cùng ticket nhưng payload PR khác nhau (2 PR number khác nhau của cùng ticket X), verify `aiFindingStatWriter.recordStat(...)` được gọi 2 lần nhưng với `AiFindingStatRecord` có cùng `ticketId` — kết hợp với test SQL-text `ON CONFLICT (ticket_id)` đã có ở tầng adapter, chứng minh đầy đủ chuỗi "gọi lại → upsert đè, không insert thêm row".
   - `ai_finding_stats_webhookRedelivery_isIdempotent`: gọi `service.handle(raw, sig, "pull_request", sameDeliveryId)` 2 lần với **cùng** raw payload + cùng delivery-id, verify `recordStat` được gọi với `AiFindingStatRecord` giống hệt cả 2 lần (idempotent ở input, không cần dedupe delivery-id vì upsert theo `ticket_id` đã tự nhiên idempotent).
   - Lý do: đây là 2 AC "chống double-count" — mức độ nghiêm trọng nếu sai là đếm trùng KPI hiển thị cho PM, nhưng hiện chỉ được suy ra gián tiếp từ 1 test SQL-text ở tầng khác.

3. **AC-9 — test trực tiếp `AiFindingStatsDto.rate()`** (BE UT mới, file mới `PmDashboardDtosTest.java` hoặc thêm vào `PmDashboardControllerTest.java` nếu muốn giữ nguyên số file)
   - `rate_returnsNullWhenDenominatorIsZero`: `AiFindingStatsDto.from(new AiFindingStatsRow(id, "x", 0, 0, 0, 0, 0, 0, 0))` → tất cả 5 field rate phải là `null` (đúng H-AIRKI-7: null khi tổng mẫu số toàn scope = 0).
   - `rate_roundsToOneDecimalPlace`: dùng cặp không chia hết, ví dụ `numerator=1, denominator=3` → kỳ vọng `33.3`, và `numerator=2, denominator=3` → kỳ vọng `66.7`, xác nhận công thức `Math.round(x*1000.0/y)/10.0` làm tròn đúng 1 chữ số thập phân như spec `ROUND(...,1)` yêu cầu.
   - Lý do: đây là công thức tính KPI hiển thị trực tiếp, hiện **chưa từng được gọi với mẫu số = 0 thật** trong bất kỳ test nào — rủi ro cao nhất nếu có regression, chi phí test thấp nhất (pure function, không cần mock).

4. **AC-10 — nhánh "PM đúng project dù role hệ thống khác"** (BE UT mới, thêm vào `PmDashboardServiceTest.java`, theo đúng pattern `templateUsage_allowsProjectRolePmEvenWhenSystemRoleIsDifferent` đã có)
   - `aiFindingStats_allowsProjectRolePmEvenWhenSystemRoleIsDifferent`: caller có `role` hệ thống khác `PM`/`ADMIN` nhưng `repository.findProjectRole(caller, projectId)` trả về `"PM"` → verify `getAiFindingStats` không throw và trả đúng row.
   - Lý do: đóng khoảng trống nhất quán — 3 endpoint anh em (`summary`, `detail`, `templateUsage`) đều có test này, `aiFindingStats` thì chưa, dù dùng chung `requirePm(...)`.

5. **(P2, tùy chọn) Full-width number cho `extractCell`** (BE UT mới, thêm vào `AiReviewStatsParserTest.java`)
   - `parse_fullWidthDigitsInCell_treatedAsNotApplicable`: dòng có giá trị dùng số full-width Unicode (ví dụ `"５／１０"`) thay vì ASCII — verify KPI đó trả `null`/`null` giống hành vi not-applicable, không throw, không âm thầm parse sai.
   - Lý do: `NUMERATOR_DENOMINATOR_PATTERN = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)")` không có cờ `UNICODE_CHARACTER_CLASS`, nên `\d` chỉ khớp ASCII 0-9 — số full-width sẽ không khớp regex, `extractCell` trả `NumeratorDenominator(null, null)`, tức tự nhiên rơi vào nhánh not-applicable đã đúng theo AC-4. Test này không sửa hành vi, chỉ ghi nhận tường minh hành vi hiện có (đã an toàn) thay vì để nó chỉ là tác dụng phụ chưa được assert.

## Phương châm test data

- Không dùng dữ liệu thật/production; toàn bộ UUID test dùng literal cố định dễ đọc theo pattern đã có trong repo (`00000000-0000-0000-0000-00000000XXXX`), không dùng `UUID.randomUUID()` cho case cần so sánh giá trị cụ thể (chỉ dùng random cho case chỉ cần "một UUID hợp lệ, giá trị không quan trọng", theo đúng convention hiện có trong `PmDashboardJdbcAdapterFindAiFindingStatsTest`/`AiFindingStatJdbcAdapterTest`).
- AC-3 là fixture duy nhất bắt buộc phải là **file thật** (`raw/ai-review.md`), không phải data gõ tay — đây chính là ý nghĩa cốt lõi của AC đó (label bị diễn đạt lại "trong thực tế", không phải một biến thể tưởng tượng).
- Test rate()/rounding (mục 3) dùng cặp số cố ý không chia hết (1/3, 2/3) để lộ sai số làm tròn nếu có, không dùng toàn cặp chia hết (dễ che giấu bug rounding).
- Không mock domain record/value object (`AiFindingStatRecord`, `AiFindingStatsRow`, `AiReviewStats`) — chỉ mock port/adapter (`AiFindingStatPort`, `NamedParameterJdbcTemplate`), đúng rule `40-testing.md`.

## Command thực thi

```bash
# BE — toàn bộ (bắt buộc trước khi đóng ticket)
cd EDCAP_BE && mvn -q -o clean verify

# BE — chỉ các test liên quan ticket này (vòng lặp nhanh khi sửa)
cd EDCAP_BE && mvn -q -o test -Dtest=AiReviewStatsParserTest,AiFindingStatWriterTest,AiFindingStatJdbcAdapterTest,PmDashboardJdbcAdapterFindAiFindingStatsTest,PmDashboardServiceTest,PmDashboardControllerTest,GithubWebhookServiceTest

# FE — toàn bộ
cd EDCAP_FE && npm run typecheck && npx vitest run

# FE — chỉ file liên quan
cd EDCAP_FE && npx vitest run "src/__ tests __/pm-dashboard/AiFindingStatsCard.test.tsx" "src/__ tests __/pm-dashboard/PMDashboardPage.test.tsx"
```

## Vùng cố ý không test lần này

- **Xác minh ràng buộc DB thật của `V511__add_ai_finding_stat_tracking.sql`** (FK `project_id`/`repository_id`/`ticket_id`, `UNIQUE(ticket_id)`, trigger `trg_ai_finding_stat_updated_at`, `ON CONFLICT` chạy thật trên Postgres). **Lý do bỏ qua:** đã xác minh toàn bộ codebase không có bất kỳ migration test nào (kể cả những test đặt trong thư mục tên `IntegrationTest/`) thực sự chạy SQL thật — `CiRunMetadataMigrationTest`, `TeamMigrationIntegrationTest`, `OrganizationMigrationIntegrationTest` đều chỉ là `Files.readString(...)` + `assertThat(...).contains(...)` trên nội dung file `.sql`, không có Testcontainers/DB thật. Đây là khoảng trống chung của toàn repo, không phải riêng ticket này — không hợp lý để ticket này tự dựng hạ tầng Testcontainers mới chỉ để phủ 1 migration. **Residual risk:** thấp-trung bình — cấu trúc SQL đã được đọc tĩnh và khớp spec §8 (additive-only, đúng kiểu, đúng FK), rủi ro chính là lỗi cú pháp SQL chỉ lộ ra khi migrate thật lần đầu trên môi trường có dữ liệu (ví dụ FK trỏ sai thứ tự tạo bảng) — giảm thiểu bằng cách chạy `mvn flyway:migrate` thủ công trên môi trường dev trước khi merge (**cần user xác nhận trước khi thực thi**, theo rule an toàn — không tự động chạy).
- **Race condition ghi đồng thời cùng `ticket_id`** (2 webhook cùng lúc cho cùng ticket, từ 2 instance app khác nhau). **Lý do bỏ qua:** an toàn tại tầng DB (`UNIQUE(ticket_id)` + `ON CONFLICT (ticket_id) DO UPDATE` là atomic ở Postgres bất kể có race ở tầng ứng dụng hay không), test race thật cần hạ tầng DB thật (cùng gap Testcontainers ở trên) hoặc giả lập bằng 2 thread gọi thẳng adapter — chi phí cao, giá trị thấp vì đã có atomicity ở DB. **Residual risk:** thấp — atomicity đến từ Postgres, không phụ thuộc code ứng dụng.
- **E2E/Playwright cho luồng PM Dashboard xem AI Finding Stats.** **Lý do bỏ qua:** đã có FE component test (loading/render/null) + FE integration test (mount đúng vị trí, gọi đúng tham số) + BE API IT (`PmDashboardControllerTest`) phủ đủ chuỗi request→response→render mà không cần trình duyệt thật; rủi ro UI-only (CSS, responsive) không phải trọng tâm của ticket này. **Residual risk:** thấp.
- **Contract test riêng biệt (Pact/schema).** **Lý do bỏ qua:** FE/BE trong cùng repo, cùng release cùng lúc, không có consumer bên thứ 3 — API IT (`PmDashboardControllerTest`) + FE test đã đóng vai trò contract test tại chỗ. **Residual risk:** không đáng kể trong bối cảnh monorepo hiện tại.
- **Malformed/null query param ở tầng HTTP** (ví dụ `projectId=not-a-uuid`, thiếu `repositoryId`). **Lý do bỏ qua:** cả 2 tham số khai `@RequestParam UUID` không có `required=false`/default, nên Spring MVC tự trả `400` qua cơ chế type-conversion chung (`GlobalExceptionHandler`), đây là hành vi framework-level áp dụng đồng nhất cho mọi endpoint khác trong `PmDashboardController` (không riêng `ai-finding-stats`), không cần test riêng cho từng endpoint. **Residual risk:** thấp — nhất quán với toàn bộ controller layer.

## Rủi ro còn lại

- **AC-AIRKI-11 lệch giữa văn bản spec và code thực tế:** `spec-pack.md` §6 ghi rõ "dùng `CDataTable`", nhưng `AiFindingStatsCard.tsx` thực tế dùng lưới card (`div` + `grid`), không import `CDataTable` ở đâu cả. Test FE hiện có (`AiFindingStatsCard.test.tsx`) test đúng theo code thực tế nên **không phải lỗi runtime** — nhưng đúng mẫu hình lệch tài liệu/code giống hệt finding F1 đã từng xảy ra ở ticket `PROMPT_TEMPLATE_REUSE_RATE` liền trước (đọc thấy trong `raw/ai-review.md` của ticket đó). Đề xuất: **cần user quyết định** — (a) sửa `spec-pack.md` AC-AIRKI-11 cho khớp code (giữ card-grid, không phải bug), hoặc (b) xác nhận đây là spec đúng và code cần đổi sang `CDataTable`. Tôi không tự ý sửa vì đây là quyết định về UI/spec, không phải bug logic.
- **2 câu hỏi mở còn tồn đọng từ vòng fix codex-review trước đó** (chưa liên quan trực tiếp tới test-plan này nhưng ảnh hưởng phạm vi build): interpretation của Finding 1 (null toàn nhóm chia sẻ mẫu số) đã chốt và có test, nhưng quyết định gỡ `spring-dotenv` (Finding 3, không liên quan) vẫn chờ user xác nhận.
- **Gap DB thật (V511) và race-condition** ở mục "Vùng cố ý không test lần này" — rủi ro thấp nhưng không phải zero, đặc biệt nếu tương lai có > 1 instance backend chạy song song xử lý webhook.
- **Sau khi thêm 5 test mới (mục Test mới/cập nhật), cần chạy lại toàn bộ `mvn -q -o clean verify` để xác nhận không có regression** trước khi coi ticket là test-complete — chưa thực hiện việc này vì test-plan này mới ở bước lập kế hoạch, chưa viết code test thật.
