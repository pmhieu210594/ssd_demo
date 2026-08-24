# test-results

**Ticket ID**: AI-REVIEW-KPI-IMPROVEMENT
**Create date**: 2026-08-20
**Author**: Principal Test Engineer / Bug Hunter (Claude)
**Update date**: 2026-08-20

> Ghi lại command đã chạy và kết quả cho các test mới/cập nhật trong `test-plan.md`. Không thêm test
> chỉ để tăng coverage — mỗi test bảo vệ một failure mode cụ thể (xem `self-review.md` §Test và mô tả
> trong từng file test qua `.as(...)`/comment).

## 1. Test mới/cập nhật trong lượt này

| File | Test method | Failure mode được bảo vệ |
| --- | --- | --- |
| `AiReviewStatsParserTest.java` | `parse_realAiReviewMdFixture_mapsByPositionNotLabelTextAndIgnoresOtherTables` | AC-AIRKI-3: parser phải định vị bảng §8 bằng **số heading**, không phải text label, giữa nhiều bảng markdown khác (§3/§5/§7) trong một fixture thật (`raw/ai-review.md` của ticket liền kề, copy verbatim vào `test-fixtures/PARSER-AI-REVIEW-STATS/ai-review-real-world.md`) — thay cho literal hand-typed cũ vốn có nguy cơ "test literal drift" khỏi input thật. |
| `AiReviewStatsParserTest.java` | `parse_fullWidthDigitsInBlockerRow_treatedAsNotApplicableNotThrowOrMisparse` | AC-AIRKI-4: giá trị full-width digit (`５／１０`) không khớp regex ASCII-only `(\d+)\s*/\s*(\d+)` → phải fallback `null` cho đúng 2 field Blocker/Major, KHÔNG throw, KHÔNG đọc nhầm thành 0, và KHÔNG ảnh hưởng nhóm shared-denominator (adoption/valid/...) độc lập. |
| `AiReviewStatsParserTest.java` | `parse_mixedFullAndHalfWidthDigitsInAdoptionRow_nullsWholeSharedGroupNotPartialParse` | AC-AIRKI-4: giá trị trộn nửa/toàn chiều rộng (`1２/15`) trong dòng Adoption phải fail toàn bộ (không match numerator một phần thành "1"), và theo A-AIRKI-3 phải null **toàn bộ nhóm shared-denominator** (adoption/valid/falsePositive/resolved), trong khi Blocker/Major — denominator độc lập — không bị ảnh hưởng. |
| `GithubWebhookServiceTest.java` | `ai_finding_stats_secondDeliveryForSameMergedPr_recordsFreshCountsNotSkippedOrStale` | AC-AIRKI-5: re-merge một ticket với `ai-review.md` mới (nội dung khác) phải ghi nhận **counts mới**, không bị skip do lầm tưởng "đã xử lý ticket này rồi" và không bị stale/cache từ lần ghi trước — service không có dedup theo `deliveryId` (chỉ dùng để log) nên counts phải luôn tính lại từ content mới nhất. |
| `GithubWebhookServiceTest.java` | `ai_finding_stats_exactRedeliveryOfSamePayload_recordsIdenticalCountsBothTimesNotAccumulated` | AC-AIRKI-6: GitHub "Redeliver" gửi lại đúng payload cũ (cùng `deliveryId`, cùng nội dung) — service phải ghi nhận **cùng giá trị y hệt** ở cả hai lần, không cộng dồn/nhân đôi. Idempotency thật sự (không tạo row trùng) nằm ở tầng DB (`ON CONFLICT (ticket_id)`), test này chỉ khóa hành vi tầng service. |
| `PmDashboardDtosTest.java` (mới, `web/dto`) | `from_zeroDenominatorForAllFiveKpis_producesNullRatesNotDivideByZeroErrorOrNaN` | AC-AIRKI-9: `rate()` trước đây chưa từng được unit-test trực tiếp — denominator=0 phải trả `null` (hiển thị "-" trên FE theo OI-AIRKI-7), không NaN/Infinity/ArithmeticException. |
| `PmDashboardDtosTest.java` | `from_zeroDenominatorOnOnlyTheIndependentBlockerColumn_nullsOnlyThatOneRateNotTheOtherFour` | A-AIRKI-3: `blocker_major_total_count` là denominator độc lập — 0/0 ở cột này không được kéo null 4 rate còn lại (đang dùng denominator chung `ai_review_finding_total_count`). |
| `PmDashboardDtosTest.java` | `from_nonTerminatingRatio_roundsToOneDecimalPlaceNotTruncatedOrMultiDecimal` | Tỷ lệ không chia hết (1/3, 2/3) phải làm tròn đúng 1 chữ số thập phân (33.3/66.7), không truncate (33.0/66.6) và không lỗi biểu diễn floating point. |
| `PmDashboardDtosTest.java` | `from_exactDivision_producesWholeNumberRate` | Regression bảo vệ case chia hết cơ bản (75.0/100.0/0.0) không bị phá bởi thay đổi công thức làm tròn trong tương lai. |
| `PmDashboardDtosTest.java` | `from_mapsRepositoryIdAndNameThrough` | Đảm bảo `repositoryId`/`repositoryName` được map qua đúng, không bị hoán đổi/bỏ sót khi thêm field rate. |
| `PmDashboardServiceTest.java` | `aiFindingStats_allowsProjectRolePmEvenWhenSystemRoleIsDifferent` | AC-AIRKI-10: user có system role khác (`VIEWER`) nhưng có **project role PM** vẫn phải được phép gọi `getAiFindingStats` — bảo vệ đúng cơ chế phân quyền theo project-role, không phải theo system-role, khớp pattern đã có ở `templateUsage_allowsProjectRolePmEvenWhenSystemRoleIsDifferent`. |

## 2. Command đã chạy

```
mvn -o -Dtest=AiReviewStatsParserTest,GithubWebhookServiceTest,PmDashboardDtosTest,PmDashboardServiceTest -DfailIfNoTests=false test
```
Kết quả:
```
Tests run: 39, Failures: 0, Errors: 0, Skipped: 0 -- GithubWebhookServiceTest
Tests run: 19, Failures: 0, Errors: 0, Skipped: 0 -- PmDashboardServiceTest
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0 -- AiReviewStatsParserTest
Tests run: 5,  Failures: 0, Errors: 0, Skipped: 0 -- PmDashboardDtosTest
Tests run: 74, Failures: 0, Errors: 0, Skipped: 0 (tổng)
BUILD SUCCESS
```

```
mvn -o clean verify
```
Kết quả:
```
Surefire (unit):   Tests run: 582, Failures: 0, Errors: 0, Skipped: 0
Failsafe (integration): Tests run: 89, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS (Total time: 47.384 s)
```
Không có class `ArchitectureTest` nào được tìm thấy trong output (grep "architecture" trên toàn bộ log không match) — nhất quán với OI-AIRKI-11 (đã "skip", chưa từng xác nhận sự tồn tại của `ArchitectureTest`, rủi ro đã được user chấp nhận trước đó).

## 3. Test thiếu / rủi ro còn lại (chưa viết test, KHÔNG papering over)

- **Numerator > denominator anomaly** (review-checklist.md, mục "chưa xác định"): parser hiện không validate numerator ≤ denominator (vd. `"3/2"`). Chưa rõ spec mong muốn gì (reject → null? chấp nhận > 100%? clamp?) — không tự ý chọn, đề xuất mở Open Issue mới (xem self-review/open-issues đề xuất bên dưới).
- **Migration `V512__add_ai_finding_stat_tracking.sql`** (đổi tên từ `V511` ngày 2026-08-20 sau khi merge `main` — xem `open-issues.md` Resolution Log): `UNIQUE(ticket_id)` và FK constraint chưa được test với DB thật (theo rule "No production DB in unit tests" + rule không tự chạy migration mà chưa hỏi). Idempotency ở AC-5/AC-6 hiện chỉ được khóa ở tầng service (mock writer); tầng DB constraint vẫn là rủi ro chưa kiểm chứng bằng test tự động.
- **`AiFindingStatWriter`'s `@Transactional(REQUIRES_NEW)`** (claim từ impl-plan.md): chưa verify lại source trong lượt này; proxy self-invocation là mối quan tâm cấp Spring-context, có thể ngoài phạm vi unit test thuần Mockito.
- **"rate null → field absent in JSON"** (claim từ impl-plan.md, ngụ ý `@JsonInclude(NON_NULL)` hoặc tương đương): chưa kiểm tra lại cấu hình Jackson thực tế trong lượt này; các test `PmDashboardDtosTest.java` mới chỉ verify ở tầng DTO (Java object), không verify tầng serialize-to-JSON — nếu AC-9 yêu cầu cụ thể field bị ẩn khi null, cần thêm test riêng ở tầng controller/serialization.
- **AC-AIRKI-11 (CDataTable vs card-grid)**: mâu thuẫn tài liệu (`spec-pack.md` §6/§9 vs `impl-plan.md`) vẫn chưa được đồng bộ ở cấp AC, và `open-issues.md` hiện KHÔNG có entry nào theo dõi việc này — đề xuất mở `OI-AIRKI-14` (xem đề xuất trong self-review/open-issues), không tự ý viết FE test giả định một trong hai cấu trúc là đúng.
