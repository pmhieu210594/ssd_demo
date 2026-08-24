# report

**Ticket ID**: AI-REVIEW-KPI-IMPROVEMENT
**Create date**: 2026-08-20
**Author**: SDD Reporter (Claude)
**Update date**: 2026-08-20

> Tài liệu này tổng hợp toàn bộ vòng đời SDD của ticket (spec-pack → impact-analysis → impl-plan →
> review-checklist → self-review → codex-review → test-plan → test-results → blackbox test) thành 1
> báo cáo cuối, phục vụ hậu công đoạn (người review sau, ticket kế tiếp) và audit. Mọi khẳng định
> "đã test/đã xử lý" trong tài liệu này chỉ được ghi khi có bằng chứng trực tiếp trong nguồn đã đọc
> (lệnh chạy thật, kết quả build, hoặc xác nhận trực tiếp của user trong hội thoại) — phần nào chưa
> có bằng chứng như vậy được ghi rõ là "chưa xác nhận" hoặc "đề xuất, chưa thực thi".

## Tổng quan sửa đổi

Ticket bổ sung 5 chỉ số KPI AI Review (AI Review Adoption Rate, AI Review Valid Finding Rate, AI
False Positive Rate, AI Finding Resolution Rate — dùng chung 1 mẫu số
`ai_review_finding_total_count`; và Blocker/Major Resolution Rate — dùng mẫu số riêng
`blocker_major_total_count`) lên PM Dashboard. Dữ liệu được đọc từ mục "## 8." của file
`ai-review.md` trong mỗi ticket/PR bằng parser vị trí-cấu trúc (`AiReviewStatsParser`, nhận diện
heading theo số thứ tự, không theo nhãn dịch), lưu vào bảng mới `tbl_fact_ai_finding_stat` theo cơ
chế upsert 1-dòng-1-ticket (`ON CONFLICT (ticket_id) DO UPDATE`), và hiển thị trên PM Dashboard bằng
component card-grid mới `AiFindingStatsCard.tsx`.

Toàn bộ 12 AC (AC-AIRKI-1..12) đã được implement và đã qua 2 vòng review (self-review + codex-review,
verdict cuối `PASS_WITH_MINOR`), phát hiện và fix 2 bug Major (1 correctness, 1 tenant-isolation —
chi tiết ở mục "Kết quả review"). Test tự động BE đã chạy full suite thành công (xem "Kết quả test").
QA thủ công 24 test case black-box đã được user xác nhận Đạt 24/24. Ticket còn 1 vài điểm chưa đóng
dứt điểm (2 gap test đề xuất trong test-plan.md chưa xác nhận đã thực thi) — liệt kê chi tiết ở
"Next actions".

## Đối ứng specification/AC

| AC | Nội dung | Trạng thái | Ghi chú lệch |
|---|---|---|---|
| AC-AIRKI-1 | Parse mục 8 theo vị trí cấu trúc, không theo nhãn text | Implemented | — |
| AC-AIRKI-2 | Nhận diện heading mục 8 độc lập ngôn ngữ (regex số thứ tự) | Implemented | Xác nhận qua TC-AIRKI-02/03 (heading tiếng Anh/Nhật) — Đạt |
| AC-AIRKI-3 | Parse đúng 5 chỉ số theo đúng thứ tự dòng | Implemented | Yêu cầu "dùng fixture thật" (review-checklist §8) đã đóng ở lượt test bổ sung sau (`test-results.md`: `parse_realAiReviewMdFixture_...`, dùng file `raw/ai-review.md` thật copy verbatim) — `self-review.md`'s ghi chú "chưa xử lý" là bản chụp trước lượt bổ sung này |
| AC-AIRKI-4 | 5 KPI dùng shared-denominator theo nhóm (A-AIRKI-3) | Implemented + Fixed | Bug Major #1 (firstNonNull gán nhầm mẫu số) đã fix bằng `resolveSharedFindingTotal`; cách "null cả nhóm nếu 4 mẫu số không khớp/thiếu" là 1 lựa chọn diễn giải, xem Accepted Risk #4 |
| AC-AIRKI-5/6 | Upsert idempotent theo ticket_id, ghi đè khi merge PR mới | Implemented | Test cấp webhook riêng cho redelivery/re-merge đã được bổ sung ở lượt sau (`test-results.md`: `ai_finding_stats_secondDeliveryForSameMergedPr_...`, `ai_finding_stats_exactRedeliveryOfSamePayload_...`) |
| AC-AIRKI-7 | Rate = SUM tử số / SUM mẫu số toàn phạm vi, không phải trung bình từng ticket | Implemented | Có test |
| AC-AIRKI-8 | Mẫu số = 0 → hiển thị "-" ở FE (rate null) | Implemented | — |
| AC-AIRKI-9 | Công thức làm tròn `rate()` | Implemented | Có test riêng (`PmDashboardDtosTest` mới) |
| AC-AIRKI-10 | Phân quyền: PM theo project, ADMIN full quyền | Implemented + Fixed | Bug Major #2 (rò rỉ định danh repository chéo project) đã fix bằng bổ sung `AND r.project_id = :projectId` vào WHERE; có test riêng |
| AC-AIRKI-11 | FE hiển thị 5 KPI | Implemented (lệch thiết kế, đã chốt) | Thực tế dùng card-grid (`AiFindingStatsCard.tsx`, mẫu `SummaryCards.tsx`), KHÔNG phải `CDataTable` như spec-pack.md §6/§9 mô tả ban đầu — đã đóng bằng quyết định trực tiếp của user ("dùng SummaryCards", OI-AIRKI-13/14) |
| AC-AIRKI-12 | Các yêu cầu còn lại theo spec-pack.md | Implemented | Không có ghi chú lệch riêng theo self-review/codex-review |

## Phạm vi ảnh hưởng

Theo `impact-analysis.md` (Principal Engineer, additive-only impact map):

- **BE**: thêm entity/repository/service mới cho `tbl_fact_ai_finding_stat`, parser mới
  (`AiReviewStatsParser`), sửa `GithubWebhookService` (gọi parser + upsert khi merge PR),
  sửa `PmDashboardService`/`PmDashboardJdbcAdapter` (query + DTO trả về Dashboard).
- **FE**: thêm component mới `AiFindingStatsCard.tsx`, gắn vào PM Dashboard hiện có.
- **DB**: 1 bảng mới hoàn toàn (`tbl_fact_ai_finding_stat`), không sửa schema bảng cũ nào.
- **Ops/Rollout**: không cần thao tác vận hành đặc biệt ngoài chạy migration; rollout kiểu "Light"
  (không cần feature flag, không có rollback phức tạp — drop bảng mới là đủ để revert).
- **Vùng xác nhận không ảnh hưởng**: các card KPI khác trên Dashboard, các luồng webhook khác
  (không đụng tới logic xử lý webhook hiện có ngoài điểm gọi thêm parser mới).
- 7 "điểm chưa rõ" nêu trong impact-analysis.md đều đã được resolve hoặc skip trước khi implement
  (xác nhận qua open-issues.md).

## Nội dung implementation

Theo `impl-plan.md` (Principal Engineer) và `self-review.md` (Senior Engineer, bảng file-change đầy
đủ):

- **BE sửa**: `PmDashboardJdbcAdapter` (query JOIN thêm bảng fact mới + bảng dimension repository),
  `PmDashboardService`, `GithubWebhookService` (gọi parser sau khi merge PR).
- **BE mới**: `AiReviewStatsParser`, entity/DTO cho `tbl_fact_ai_finding_stat`, migration script.
- **FE mới**: `AiFindingStatsCard.tsx`.
- **Test mới/sửa** (tổng hợp cả impl gốc lẫn Bug Hunter phase): `AiReviewStatsParserTest`,
  `GithubWebhookServiceTest`, `PmDashboardServiceTest`, `PmDashboardDtosTest` (file mới).

Thứ tự implement theo 10 bước trong impl-plan.md, 8 gate đều đã đóng/skip theo xác nhận trong ticket
folder trước khi bắt đầu code.

**Một giả định trong impl-plan.md đã sai khi thực thi**: phần thiết kế query DB nêu rằng quy tắc
"scope tenant phải đặt trong `ON`, không chỉ trong `WHERE`" không áp dụng trực tiếp vì không có kế
hoạch LEFT JOIN cho phần aggregate chính. Trên thực tế, việc lấy thêm `repositoryName` để hiển thị
đã cần thêm 1 LEFT JOIN vào `tbl_dim_repository`, và quy tắc scoping đó lẽ ra phải áp dụng ngay từ
đầu cho JOIN mới này — đây chính là nguyên nhân trực tiếp của bug Major #2 (xem "Kết quả review").

## Kết quả review

- **review-checklist.md** (Principal Reviewer, viết TRƯỚC implementation): 10 chương checklist theo
  mức Severity. Theo tự đánh giá trong self-review.md, Chương 10 (Release/Rollback) **chưa được thực
  hiện** tại thời điểm self-review. Yêu cầu ở §8 "fixture (2) dùng CHÍNH file `raw/ai-review.md`
  thật" là điểm lặp lại nhiều vòng review sau (xem AC-AIRKI-3, Source Analysis Limitations).
- **self-review.md** (Senior Engineer, Update 2026-08-20 — bản tự đánh giá gần nhất và chi tiết
  nhất): AC↔evidence table đánh dấu cả 12 AC "Implemented" (4 AC có caveat: AC-3/4/10/11). Mục
  "Vấn đề đã biết chưa xử lý" liệt kê 9 điểm còn tồn đọng tại thời điểm viết, bao gồm gap fixture
  thật (AC-3) và thiếu test redelivery cấp webhook (AC-5/6) — xem chi tiết ở "Kết quả test".
- **codex-review.md** (verdict **PASS_WITH_MINOR**, re-confirm 2026-08-20):
  - **Major #1 (Correctness, AC-4)**: `firstNonNull(...)` trong `AiReviewStatsParser` có thể gán
    nhầm mẫu số thật của 1 KPI cho KPI khác có tử số null (do dòng "Không áp dụng"), ra kết quả sai
    `0.0%` thay vì `null` đúng. **Đã fix** bằng `resolveSharedFindingTotal` (yêu cầu cả 4 mẫu số dùng
    chung phải có mặt và khớp nhau, nếu không thì null toàn bộ nhóm 5 trường) — đã re-confirm.
  - **Major #2 (Security/Tenant isolation, AC-10)**: `PmDashboardJdbcAdapter.findAiFindingStats` chỉ
    scope `projectId` trong mệnh đề `ON` của LEFT JOIN (cho dòng fact), không scope ở `WHERE` cho
    dòng dimension `tbl_dim_repository` — PM được phân quyền ở project A có thể biết được
    `repositoryId`/`repositoryName` thật của 1 repository thuộc project B (rò rỉ định danh chéo
    tenant), dù các trường KPI trả về null/0. **Đã fix** bằng thêm `AND r.project_id = :projectId`
    vào `WHERE`; test cũ (assert hành vi lỗi) đã được viết lại, test mới xác nhận trả về
    `Optional.empty()` cho repository ngoài phạm vi project.
  - 1 "Question" khác trong codex-review.md liên quan đến 1 dependency trong `pom.xml` — đã xác nhận
    đây là thay đổi local của user cho mục đích khác, không thuộc phạm vi ticket này và sẽ không được
    commit, nên không đưa vào phạm vi review/report của ticket.
  - 2 "False positive candidates" (FE card-grid thay CDataTable, HTTP 500 thay 400) được xác định rõ
    là KHÔNG phải defect, đã đóng qua quyết định user.

## Kết quả test

**Test tự động (BE) — đã xác nhận chạy thật:**

- Bug Hunter phase (`test-results.md`, Principal Test Engineer): thêm test cho
  `AiReviewStatsParserTest` (+2), `GithubWebhookServiceTest` (+2), `PmDashboardDtosTest` (file mới,
  5 test), `PmDashboardServiceTest` (+1). Chạy `mvn -o clean verify`: **582 unit + 89 integration
  test, BUILD SUCCESS**.
- self-review.md (lần chạy cuối, sau khi fix 2 bug Major từ codex-review): **661 test (572 unit + 89
  integration), 0 failure** — đây là kết quả full-suite gần nhất và có giá trị tham chiếu cao nhất.

**test-plan.md (Test Strategist) đề xuất 5 test mới/cập nhật (P0/P1/P2) — trạng thái thực thi (đã đối
chiếu trực tiếp `test-results.md` vs `self-review.md`, xem giải thích chronology bên dưới):**

| # | Đề xuất | Priority | Trạng thái |
|---|---|---|---|
| 1 | AC-3: dùng fixture thật (copy nguyên văn từ ticket đã hoàn thành) | P0 | **Đã chạy** — `AiReviewStatsParserTest.parse_realAiReviewMdFixture_mapsByPositionNotLabelTextAndIgnoresOtherTables`, dùng file `raw/ai-review.md` thật copy verbatim vào `test-fixtures/PARSER-AI-REVIEW-STATS/ai-review-real-world.md` |
| 2 | AC-9: test null/rounding cho `rate()` | P0 | **Đã chạy** — `PmDashboardDtosTest` mới |
| 3 | AC-5/6: test cấp webhook cho redelivery/re-merge | P1 | **Đã chạy** — `GithubWebhookServiceTest.ai_finding_stats_secondDeliveryForSameMergedPr_...` (re-merge, counts mới) và `.ai_finding_stats_exactRedeliveryOfSamePayload_...` (redelivery y hệt payload, không cộng dồn) |
| 4 | AC-10: test nhánh PM theo project-role | P1 | **Đã chạy** — `PmDashboardServiceTest` +1 |
| 5 | Test số full-width | P2 | **Đã chạy** — `AiReviewStatsParserTest.parse_fullWidthDigitsInBlockerRow_...` và `.parse_mixedFullAndHalfWidthDigitsInAdoptionRow_...` (ngoài phạm vi tự động, còn có xác nhận thủ công qua TC-AIRKI-06/07) |

Cả 5 mục đề xuất trong `test-plan.md` đều đã được thực thi thật, xác nhận qua lượt chạy
`mvn -o clean verify` cuối trong `test-results.md` (582 unit + 89 integration, BUILD SUCCESS).
`self-review.md`'s mục "Vấn đề đã biết chưa xử lý" (item 1: fixture, item 2: redelivery test) là bản
chụp **trước** lượt Bug Hunter này — không phải mâu thuẫn thật giữa 2 nguồn, chỉ là tài liệu chưa được
cập nhật lại sau khi gap được đóng (xem "What worked"/"Source Analysis Limitations" và Next actions).

**QA thủ công (black-box) — đã xác nhận qua user, KHÔNG phải Claude tự chạy/quan sát:**

- `blackbox-testcases.md`: 24 test case (TC-AIRKI-01..24), phủ đủ 12 AC + các viewpoint
  normal/abnormal/boundary/permission/state-transition/full-width/double-submit/external-IF.
- `blackbox-test-results.md`: **24/24 Đạt**, theo xác nhận trực tiếp của user trong hội thoại ("Tôi
  đã test ổn tất cả"). Riêng TC-AIRKI-05 được user báo cáo và xác nhận riêng: chỉ Blocker/Major có
  giá trị, 4 chỉ số còn lại null — đúng theo thiết kế nhóm mẫu số dùng chung (A-AIRKI-3), không phải
  lỗi.
- 2 điểm hành vi thực tế khác mô tả cũ trong spec-pack.md (TC-AIRKI-20 status 500, TC-AIRKI-21 FE
  card-grid) đã được ghi nhận là "theo đúng hành vi thực tế đã chốt", không phải sai lệch mới.

## Viewpoint security / operation

- **Security**: bug Major #2 (rò rỉ định danh repository chéo project qua JOIN thiếu scope ở WHERE)
  đã được phát hiện và fix, có test xác nhận. Mô hình phân quyền (`requirePm`: ADMIN full quyền,
  role khác cần PM theo project) đã được kiểm tra cả tự động (`PmDashboardServiceTest`) lẫn thủ công
  (TC-AIRKI-16..19, 4 tài khoản test theo 4 vai trò khác nhau, đều Đạt).
- **Operation**: cơ chế upsert theo `ticket_id` giúp an toàn khi webhook bị gửi lại (redelivery),
  nhưng chưa có test riêng xác nhận hành vi này ở cấp webhook (xem "Kết quả test" mục #3). Rollout
  kiểu Light (1 bảng mới độc lập, không sửa bảng cũ) → rollback đơn giản (drop bảng/revert
  migration). Không có `ArchitectureTest` (ArchUnit) để tự động chặn vi phạm layering hexagonal —
  file này được `docs/standards/testing.md` mô tả là "Confirmed" tồn tại nhưng thực tế **không tồn
  tại trong repo** (xác nhận qua nhiều lần grep, cả trong session này lẫn phiên trước) — rủi ro drift
  kiến trúc không được test tự động chặn, xem Accepted Risk #1.

## Accepted risk

| # | Rủi ro | Ảnh hưởng | Deadline | Owner |
|---|---|---|---|---|
| 1 | `ArchitectureTest.java` (ArchUnit) không tồn tại trong repo dù standards docs mô tả "Confirmed" | Vi phạm layering hexagonal (domain/application/infra/web) ở các PR sau có thể lọt qua mà không có cảnh báo tự động | Chưa xác định — cần Tech Lead/BE Lead lên lịch | BE Lead / Tech Lead |
| 2 | Giả định numerator ≤ denominator không được validate hay có test riêng (OI-AIRKI-15, chấp nhận theo quyết định user "giả định data đầu vào không có chuyện 3/2") | Nếu dữ liệu input sai lệch (numerator > denominator, ví dụ do file `ai-review.md` bị sửa tay sai), rate có thể vượt 100% mà không có cảnh báo | Chưa xác định | BE dev phụ trách `AiReviewStatsParser` |
| 3 | Không có test migration chạy trên DB thật (OI-AIRKI-16, gap chung toàn repo — không có migration test nào trong repo chạy với DB thật, không riêng ticket này) | Lỗi migration script (FK/UNIQUE constraint) chỉ phát hiện được khi deploy vào môi trường thật | Chưa xác định — khuyến nghị đưa vào backlog cải thiện hạ tầng test chung | BE Lead / DevOps |
| 4 | Cách xử lý "null cả nhóm nếu thiếu/không khớp đủ 4 mẫu số dùng chung" (fix Major #1) là 1 lựa chọn diễn giải của người fix, chưa được chốt chính thức thành yêu cầu trong spec-pack.md | Nếu sau này có yêu cầu mỗi KPI có mẫu số độc lập (đổi schema), logic hiện tại cần sửa lại từ đầu | Trước lần thay đổi schema tiếp theo có đụng tới `tbl_fact_ai_finding_stat` | Spec/Product owner của ticket AI-REVIEW-KPI-IMPROVEMENT |
| 5 | Repository ngoài phạm vi project trả về cùng dạng "object rỗng/không dữ liệu" thay vì lỗi rõ ràng phân biệt "không tồn tại" và "không có quyền" (câu hỏi mở của codex-review, tạm chấp nhận) | Khó debug/khó phân biệt 2 tình huống này ở phía client khi có sự cố | Chưa xác định | BE dev + FE dev (nếu cần làm rõ UX sau này) |

## Open Issues

Theo `open-issues.md` (SDD Analyst, Update 2026-08-20): tổng cộng **16 Open Issue**, tất cả đã
**Resolved hoặc Skipped**, **0 blocker còn treo** tại thời điểm viết report. Các quyết định đáng chú ý:

- OI-AIRKI-9: giữ nguyên HTTP 500 `INTERNAL_ERROR` khi thiếu tham số bắt buộc (hành vi nhất quán toàn
  hệ thống, không sửa riêng cho ticket này).
- OI-AIRKI-10: xác nhận thiết kế shared-denominator (A-AIRKI-3) là đúng chủ đích, không phải lỗi.
- OI-AIRKI-11: skip việc bổ sung `ArchitectureTest` — chấp nhận là rủi ro (xem Accepted Risk #1).
- OI-AIRKI-13/14: chốt FE dùng card-grid (`AiFindingStatsCard.tsx` theo mẫu `SummaryCards.tsx`) thay
  vì `CDataTable` như spec-pack.md mô tả ban đầu.
- OI-AIRKI-15: chấp nhận giả định numerator ≤ denominator, không cần validate/test riêng.
- OI-AIRKI-16: chấp nhận gap thiếu test migration DB thật.

## Human Decisions

`{{HUMAN_REVIEW_PATH}}` (`human-review.md`) **không tồn tại** trong thư mục ticket. Mục này dùng
`open-issues.md` (Resolution Log — có trích dẫn trực tiếp lời user cho từng quyết định) và
`spec-pack.md` §18 (Human Decisions Log) làm nguồn thay thế. Các quyết định trực tiếp của user quan
trọng nhất:

- *"dùng SummaryCards"* → chốt FE hiển thị bằng card-grid thay vì `CDataTable` (AC-AIRKI-11).
- *"giả định data đầu vào không có chuyện 3/2"* → chấp nhận giả định numerator ≤ denominator, không
  cần validate/test riêng (OI-AIRKI-15, Accepted Risk #2).
- *"Chấp nhận gap này"* → chấp nhận thiếu test migration chạy trên DB thật (OI-AIRKI-16, Accepted
  Risk #3).
- Quyết định giữ nguyên HTTP 500 (không đổi sang 400) cho thiếu tham số bắt buộc (OI-AIRKI-9).
- Xác nhận thiết kế shared-denominator A-AIRKI-3 (OI-AIRKI-10).
- Quyết định skip bổ sung `ArchitectureTest` (OI-AIRKI-11, Accepted Risk #1).
- Xác nhận riêng kết quả TC-AIRKI-05 (chỉ Blocker/Major có giá trị, 4 chỉ số còn lại null) là đúng
  thiết kế, không phải lỗi.
- Xác nhận cuối cùng: *"Tôi đã test ổn tất cả"* — QA sign-off cho toàn bộ 24 test case black-box.

## Source Analysis Limitations

- `{{HUMAN_REVIEW_PATH}}` không tồn tại — đã thay thế bằng `open-issues.md` Resolution Log +
  `spec-pack.md` §18 như nêu ở mục "Human Decisions" trên.
- **Đã đối chiếu trực tiếp 2 tài liệu cùng ngày 2026-08-20** (`test-results.md` và `self-review.md`)
  để xử lý chênh lệch trạng thái ban đầu tưởng là mâu thuẫn: `self-review.md`'s "Vấn đề đã biết chưa
  xử lý" liệt kê gap AC-3 (fixture thật) và AC-5/6 (redelivery test) là chưa xử lý, nhưng
  `test-results.md` (Bug Hunter phase, cùng ngày nhưng muộn hơn theo nội dung — bổ sung đúng 2 test
  khớp với 2 gap này, dùng đúng thuật ngữ "thay cho literal hand-typed cũ") xác nhận cả 2 đã được
  đóng bằng test thật, chạy thật (`mvn -o clean verify`, 582 unit + 89 integration, BUILD SUCCESS).
  Kết luận: đây không phải mâu thuẫn nguồn, mà là `self-review.md` chưa được cập nhật lại sau lượt
  Bug Hunter — report này dùng `test-results.md` làm nguồn có hiệu lực cao hơn cho 2 mục này.
- Report này tổng hợp từ 7 tài liệu (impact-analysis, impl-plan, self-review, codex-review,
  review-checklist, open-issues, test-plan) cộng với spec-pack.md, test-results.md,
  blackbox-testcases.md/test-data.md/blackbox-test-results.md. Report **không tự chạy lại test hay
  đọc lại toàn bộ source code ứng dụng** — mọi khẳng định "pass/fail" dựa trên nội dung đã ghi trong
  các tài liệu nguồn, không phải do Claude tự chạy lại trong phiên viết report này.

## What worked

- Thiết kế parser theo vị trí cấu trúc (số thứ tự heading, không theo nhãn dịch) chịu được đa ngôn
  ngữ — xác nhận qua TC-AIRKI-02/03 (heading tiếng Anh/Nhật) Đạt.
- Thiết kế shared-denominator giúp lộ ra đúng 1 bug thật (Major #1) trước khi lên production nhờ
  codex-review, thay vì phát hiện sau khi deploy.
- Quy trình review 2 lớp (self-review + codex-review) bắt được 2 bug Major thật (1 correctness, 1
  security/tenant-isolation) và fix trước khi release, kèm test xác nhận lại.
- Black-box QA thủ công (24 test case, đủ 12 AC + các viewpoint permission/boundary/full-width/
  double-submit/external-IF) không phát sinh sai lệch mới ngoài 2 điểm đã biết trước.
- `open-issues.md`'s Resolution Log (trích dẫn trực tiếp lời user cho từng quyết định) đủ chi tiết để
  tái dựng mục "Human Decisions" dù thiếu file `human-review.md` riêng.

## What failed

- Giả định trong impl-plan.md ("tenant scope không cần đặt trong ON vì không có LEFT JOIN") sai khi
  thực thi thêm JOIN cho `repositoryName` → dẫn trực tiếp tới bug Major #2. Bài học: mọi JOIN thêm
  sau khi viết impl-plan cần được re-check lại với rule tenant-scoping, không dựa vào giả định ban
  đầu của kế hoạch.
- Yêu cầu "fixture dùng chính file thật" đã được nêu từ review-checklist.md (viết TRƯỚC
  implementation), lặp lại qua self-review/codex-review/test-plan trước khi thực sự được đóng ở lượt
  Bug Hunter cuối cùng — một yêu cầu review nêu sớm nhưng mất nhiều vòng mới chốt dứt điểm, cho thấy
  cần rút ngắn khoảng cách giữa lúc phát hiện gap và lúc đóng gap.
- `self-review.md` không được cập nhật lại sau khi `test-results.md` (Bug Hunter phase, cùng ngày
  nhưng muộn hơn) đóng 2 gap mà chính self-review.md liệt kê là "chưa xử lý" (fixture AC-3, redelivery
  test AC-5/6) — khiến tài liệu tự-đánh-giá bị lạc hậu so với trạng thái thực tế, dễ gây hiểu lầm cho
  người đọc sau nếu chỉ đọc self-review.md mà không đối chiếu test-results.md.

## Ứng viên cập nhật Failure Mode Index

1. **Shared/derived-denominator column — nhầm NULL thành 0**: khi nhiều chỉ số dùng chung 1 cột mẫu
   số, dùng `firstNonNull`/lấy giá trị không-null đầu tiên có thể gán nhầm mẫu số thật của 1 dòng cho
   1 KPI khác có tử số null (do "không áp dụng"), ra kết quả sai 0% thay vì null đúng. Tổng quát hoá
   từ Major #1.
2. **Tenant/project scope chỉ đặt trong JOIN...ON, thiếu ở WHERE cho dòng dimension**: khi LEFT JOIN
   thêm 1 bảng dimension (vd. repository) để lấy thông tin hiển thị (tên/id), nếu chỉ scope theo
   project ở `ON` cho bảng fact mà không scope luôn ở `WHERE` cho bảng dimension, định danh (tên/id)
   của record thuộc project khác vẫn có thể lộ ra dù các trường số liệu là null/0. Tổng quát hoá từ
   Major #2.
3. **Fixture tự soạn tay trôi dần khỏi format nguồn thật**: test dùng fixture tự viết tay (không
   phải copy nguyên văn 1 file nguồn đã qua hệ thống thật) có nguy cơ không phát hiện được lệch định
   dạng thực tế (nhãn diễn đạt khác, thứ tự cột khác) mà file thật có thể có. Tổng quát hoá từ gap
   AC-3 — dù gap này trong ticket này đã được đóng ở lượt Bug Hunter cuối, nó vẫn mất nhiều vòng review
   mới chốt, nên đáng đưa vào Failure Mode Index để phát hiện sớm hơn ở ticket sau.
4. **Quyết định review được ghi nhận nhưng hành động thực thi không được theo dõi**: khi review
   (codex hoặc người) ghi "quyết định: làm X" mà không gắn với 1 action-item/checklist ràng buộc,
   hành động thực tế có thể không bao giờ được thực hiện. Đề xuất: mọi dòng "Status: Decision
   recorded" trong review cần có 1 mục tương ứng trong self-review's "chưa xử lý" cho tới khi xác
   nhận đã thực thi trong code.

## Ứng viên cập nhật Living Docs

- `spec-pack.md` §6/§9 (AC-AIRKI-11): đang mô tả FE dùng `CDataTable` — cần sửa thành card-grid
  (theo mẫu `AiFindingStatsCard.tsx`/`SummaryCards.tsx`) để khớp quyết định thực tế đã chốt.
- `spec-pack.md` §10: đang mô tả hành vi trả về 400 khi thiếu tham số bắt buộc — cần sửa/chú thích
  lại thành 500 `INTERNAL_ERROR` (hành vi nhất quán toàn hệ thống, không phải đặc thù ticket này),
  tránh gây hiểu lầm cho ticket sau.
- `spec-pack.md` §16/§18: nên nâng A-AIRKI-3 (thiết kế shared-denominator) từ khung "giả định AI" lên
  thành 1 mục chính thức trong Human Decisions Log §18, vì đã được user xác nhận trực tiếp
  (OI-AIRKI-10).
- `docs/standards/api-contract.md` và `docs/standards/error-handling.md`: mô tả `ErrorResponse` có 6
  field (kèm `errorCode` riêng) — thực tế source chỉ có 5 field (`timestamp, status, error, message,
  traceId`) — cần sửa lại cho khớp thực tế; đây là stale doc có sẵn từ trước, không phải lỗi phát
  sinh từ ticket này.
- `docs/standards/testing.md`: khẳng định `ArchitectureTest` là "Confirmed" tồn tại — thực tế file
  này không tồn tại trong repo (xác nhận qua nhiều lần grep, nhiều phiên) — cần sửa lại thành "chưa
  có" hoặc thực sự tạo file này, tránh gây hiểu lầm nghiêm trọng cho các ticket sau tưởng rằng
  layering hexagonal luôn được ArchUnit enforce tự động.
- `review-checklist.md` §8 (yêu cầu fixture thật): nên giữ nguyên yêu cầu nhưng thêm ghi chú "đã lặp
  lại chưa đóng qua nhiều vòng review (review-checklist → self-review → codex-review → test-plan)"
  để ticket sau nhận biết đây là item có nguy cơ bị trôi nếu không có bước đối chiếu chéo.

## Next actions

1. **[Trung bình]** Cập nhật lại `self-review.md`'s "Vấn đề đã biết chưa xử lý" (item 1, 2) để phản
   ánh đúng: 2 gap AC-3 (fixture thật) và AC-5/6 (redelivery test) đã được đóng bởi lượt Bug Hunter
   (`test-results.md`) — tránh người đọc sau bị hiểu nhầm nếu chỉ đọc self-review.md.
2. **[Trung bình]** Đưa các mục cập nhật Living Docs ở trên vào backlog tài liệu (`spec-pack.md`,
   `api-contract.md`, `error-handling.md`, `testing.md`, `review-checklist.md`).
3. **[Trung bình]** Chốt deadline + owner cụ thể cho các Accepted Risk hiện đang ghi "chưa xác định"
   (ArchitectureTest, validate numerator/denominator, migration test trên DB thật) — cần Tech
   Lead/Product owner xác nhận lịch.
4. **[Thấp]** Đưa 4 candidate ở mục "Ứng viên cập nhật Failure Mode Index" vào hệ thống Failure Mode
   Index chính thức (nếu có), để các ticket sau tự động được nhắc kiểm tra các pattern này.
5. **[Thấp]** Thực hiện cleanup dữ liệu test theo `test-data.md` (mục Cleanup) nếu môi trường QA dùng
   chung với môi trường khác.
