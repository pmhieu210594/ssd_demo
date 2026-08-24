# ticket-rules.md

**Ticket ID**: AI-REVIEW-KPI-IMPROVEMENT
**Create date**: 2026-08-18
**Author**: Tech Lead (Claude)

Tài liệu này là bộ rule bắt buộc tuân thủ khi implement ticket này, rút ra từ bằng chứng đã xác
minh trong `context.md`. Xem `context.md` để biết chi tiết nguồn/vị trí code cho từng rule.

---

## 1. Rule bắt buộc (MUST)

1. **MUST** tạo writer mới (`AiFindingStatWriter` hoặc tên tương đương) như một `@Component` Spring
   riêng biệt với `GithubWebhookService`, có method `@Transactional(propagation = Propagation.REQUIRES_NEW)`.
   Lý do: Spring AOP self-invocation — annotation `@Transactional` trên method gọi từ `this` trong
   cùng class bị bỏ qua âm thầm. Mẫu: `TemplateUsageStatWriter`.
2. **MUST** chèn logic gọi writer mới vào `GithubWebhookService.handlePullRequest` **trước** dòng
   `if (!shouldTriggerScan(action))` (khoảng dòng 298 trong file gốc 608 dòng), sau vòng lặp build
   `ticketScopes` (kết thúc ~dòng 296). Đặt sau điểm early-return sẽ khiến tính năng không bao giờ
   chạy với PR merged (`action == "closed"` luôn khiến `shouldTriggerScan` trả `false` → early return).
3. **MUST** dùng guard điều kiện `"closed".equals(action) && pullRequest.path("merged").asBoolean(false)`
   (đã có sẵn ở dòng ~253-256, cùng điều kiện `validateTemplateUsage` đang dùng) làm điều kiện kích
   hoạt đọc §8 — không phát minh điều kiện mới.
4. **MUST** viết SQL upsert theo kiểu **overwrite snapshot**:
   `INSERT ... ON CONFLICT (ticket_id) DO UPDATE SET col = EXCLUDED.col, updated_at = now()`.
   Không được dùng `col = table.col + EXCLUDED.col` (increment) — §8 là số liệu tổng hợp tại thời
   điểm đọc, không phải delta.
5. **MUST** bọc lời gọi writer mới trong try/catch bắt `DataAccessException` (hoặc superclass phù
   hợp), log WARN, không rethrow — theo đúng mẫu `validateTemplateUsage` xử lý lỗi ghi
   `templateUsageStatWriter`.
6. **MUST** thêm method đọc mới vào `PmDashboardRepositoryPort` (interface) +
   `PmDashboardJdbcAdapter` (implementation) dùng `NamedParameterJdbcTemplate` + text block SQL,
   theo đúng mẫu `findTemplateUsage`.
7. **MUST** nếu truy vấn đọc có `JOIN` giữa scope ticket và `tbl_fact_ai_finding_stat`, đặt điều
   kiện tenant/repository scoping bên trong mệnh đề `ON` của `LEFT JOIN`, không đặt trong `WHERE`
   — tránh biến `LEFT JOIN` thành `INNER JOIN` hiệu quả, làm mất ticket chưa có dữ liệu (phải hiển
   thị denominator = 0, không phải bị loại khỏi tổng).
8. **MUST** tính rate theo công thức SUM-trước-rồi-chia trên toàn bộ ticket trong scope (không phải
   trung bình cộng rate từng ticket), và trả `null` **chỉ khi** SUM(denominator) = 0 trên toàn scope.
   Công thức tham chiếu (`TemplateUsageDto.from`):
   `denominator == 0 ? null : BigDecimal.valueOf(Math.round(numerator * 1000.0 / denominator) / 10.0)`.
9. **MUST** khai báo endpoint mới với `@RequestParam UUID repositoryId` (và `projectId`) **không**
   có `required = false` — cả hai đều bắt buộc theo OI-AIRKI-2.
10. **MUST** endpoint mới trả về **1 object duy nhất**, không phải `List<...>`.
11. **MUST** gọi `PmDashboardService.requirePm(caller, projectId)` (hoặc method permission tương
    đương) ở đầu service method mới, trước khi truy vấn dữ liệu — giống hệt `getTemplateUsage`.
12. **MUST** model mới (`AiFindingStatsRow`/tương đương) thêm vào `PmDashboardModels`, DTO thêm vào
    `PmDashboardDtos` với static factory `from(...)`, theo đúng khuôn class hiện có (không tạo class
    file riêng ngoài 2 file tổng hợp này).
13. **MUST** FE gọi API qua object `pmDashboard` trong `EDCAP_FE/src/lib/api.ts` (thêm method mới
    cạnh `templateUsage`), không gọi `fetch` trực tiếp trong component.
14. **MUST** FE hiển thị `-` khi rate `null` — tái dùng `formatUsageRate` trong
    `EDCAP_FE/src/pages/pm-dashboard/utils.ts` nếu định dạng % giống hệt, hoặc viết hàm mới với
    logic `null → "-"` giống hệt nếu định dạng khác.
15. **MUST** đọc toàn bộ 33 test case trong `GithubWebhookServiceTest.java` và 3 file test PM
    Dashboard (`PmDashboardServiceTest`, `PmDashboardJdbcAdapterFindTemplateUsageTest`,
    `PmDashboardControllerTest`) trước khi viết code/test mới (OI-AIRKI-6, đã ủy quyền nhưng vẫn
    bắt buộc thực thi ở bước impl).
16. **MUST** trước khi viết code đọc nội dung file `ai-review.md` từ Git, đọc đầy đủ
    `ArtifactScannerSourcePort` để xác nhận đúng chuỗi gọi (`resolveRevision → listTree → readBlob`
    theo bằng chứng từ `validateTemplateUsage`, chưa xác minh 100% có method đọc trực tiếp theo path
    hay không) — không giả định tên method.
17. **MUST** migration mới đặt tên `V511__<snake_case>.sql` (tiếp theo sau `V510`, đã xác nhận là
    file mới nhất qua glob `**/V51*.sql`) và dùng schema tối giản (chỉ `created_at`/`updated_at`,
    không audit/soft-delete) theo quyết định OI-AIRKI-4.
18. **MUST** khóa bảng mới theo `ticket_id` (unique/PK), FK về `tbl_dim_ticket`, không khóa theo
    `(project_id, repository_id, phase_id)` như `tbl_fact_template_usage_stat` — đây là bảng khác
    cấu trúc (per-ticket, không phải per-phase-aggregate).

## 2. Rule cấm (MUST NOT)

1. **MUST NOT** thêm `@Transactional(propagation = Propagation.REQUIRES_NEW)` trực tiếp lên method
   của `GithubWebhookService` — sẽ bị Spring bỏ qua âm thầm do self-invocation.
2. **MUST NOT** copy nguyên SQL `ON CONFLICT ... DO UPDATE SET col = table.col + EXCLUDED.col` từ
   `TemplateUsageStatJdbcAdapter` — đây là pattern increment, sai bản chất dữ liệu cho tính năng này.
3. **MUST NOT** đặt logic mới sau dòng `if (!shouldTriggerScan(action)) { ... return ...; }` trong
   `handlePullRequest`.
4. **MUST NOT** trả `List<...>` từ endpoint `ai-finding-stats`.
5. **MUST NOT** dùng `@RequestParam(required = false)` cho `repositoryId`/`projectId` trên endpoint mới.
6. **MUST NOT** dùng MyBatis XML mapper cho bảng `tbl_fact_ai_finding_stat` — dùng JDBC thuần qua
   `NamedParameterJdbcTemplate` như mọi adapter PM Dashboard/Template Usage khác.
7. **MUST NOT** đặt điều kiện tenant/repository scoping trong `WHERE` khi truy vấn dùng `LEFT JOIN`
   tới `tbl_fact_ai_finding_stat` — phải đặt trong `ON`.
8. **MUST NOT** giả định `MarkdownParserCore.canonicalSectionKey(...)` đã hỗ trợ đa ngôn ngữ chỉ vì
   thêm 1 case tiếng Việt mới vào switch — bản chất switch này là mapping cứng theo từng ngôn ngữ cụ
   thể (hiện tại toàn tiếng Anh), thêm case tiếng Việt không làm nó "language-independent"; đây vẫn
   sẽ gãy khi tài liệu chuyển sang tiếng Nhật hoặc đổi cách diễn đạt tiêu đề. Phải dùng cách định vị
   theo **cấu trúc** (heading level + thứ tự bảng, hoặc số thứ tự heading trước khi bị
   `stripNumericPrefix` xóa — cần quyết định kỹ thuật cụ thể trước khi code, xem "Chú ý khi implement"
   trong `context.md`), không dựa vào so khớp text tiêu đề đã dịch.
9. **MUST NOT** dùng field-default-instantiation (`= new Xxx()`) cho dependency mới trong
   `GithubWebhookService` — mọi collaborator mới phải qua constructor injection.
10. **MUST NOT** thêm cột/tính năng backfill cho ticket merged trước khi tính năng deploy (OI-AIRKI-8
    đã chốt: không backfill).
11. **MUST NOT** tách riêng cột đếm Blocker và Major (OI-AIRKI-1 đã chốt: gộp 1 chỉ số).
12. **MUST NOT** thêm filter `periodKey` vào endpoint mới ở v1 (OI-AIRKI-3 đã chốt: không cần).
13. **MUST NOT** tạo/sửa file locale JSON cho các key i18n mới trừ khi phát hiện yêu cầu mới — hiện
    tại xác nhận Template Usage cũng không có locale JSON tương ứng, chỉ dùng `defaultValue` inline.
14. **MUST NOT** tạo rule hoặc code liên quan tới `formItemNm`/`SEQNO`/full-width/half-width/bảo
    toàn tiếng Nhật — đã xác minh không áp dụng cho ticket này (xem `context.md` mục "Rule nghiệp
    vụ đặc thù").
15. **MUST NOT** sửa `EDCAP_BE/src` hoặc `EDCAP_FE/src` trong giai đoạn tài liệu hiện tại — chỉ được
    tạo/sửa file trong `documents/docs/changes/AI-REVIEW-KPI-IMPROVEMENT/`, cho tới khi user xác nhận
    rõ ràng chuyển sang giai đoạn implement.

## 3. Method tồn tại — whitelist (xem `context.md` bảng đầy đủ)

Chỉ được coi là tồn tại và gọi trực tiếp nếu nằm trong danh sách "Method tồn tại" của `context.md`.
Mọi method khác PHẢI được xác minh lại bằng cách đọc source trước khi dùng trong `impl-plan.md`
hoặc code thật — không suy đoán theo tên "nghe hợp lý".

## 4. Method/API cấm giả định tồn tại (xem `context.md` bảng đầy đủ)

Danh sách các tên method dễ bị AI "bịa" (không tồn tại trong codebase hiện tại): xem bảng "Method
KHÔNG TỒN TẠI" trong `context.md`. Trước khi gọi bất kỳ method nào không có trong whitelist mục 3,
bắt buộc đọc lại source file liên quan để xác nhận.
