# Danh sách kiểm tra review — REVIEW-FINDING-DENSITY (Review Finding Density)

- **Ticket:** REVIEW-FINDING-DENSITY
- **Trạng thái:** Draft
- **Tạo ngày:** 2026-09-11 16:41:02
- **Cập nhật ngày:** 2026-09-11 16:41:02

> Nguồn tham chiếu chính: `docs/changes/REVIEW-FINDING-DENSITY/spec-pack.md` và `docs/changes/REVIEW-FINDING-DENSITY/impl-plan.md`.
> Mức độ nghiêm trọng: **Blocker** = bắt buộc sửa trước khi merge | **Major** = phải sửa trong PR này | **Minor** = sửa hoặc ghi nhận là nợ kỹ thuật

<!--
Cách dùng:
1. Copy template này thành `docs/changes/[CHANGE_ID]/review-checklist.md`.
2. Thay toàn bộ placeholder dạng [LIKE_THIS].
3. Giữ cách đánh số RC liên tục, không trùng số.
4. Mỗi hạng mục nên có thể kiểm chứng bằng code, test, log, diff, hoặc tài liệu.
5. Thêm/xóa hạng mục theo scope thực tế của change, nhưng không xóa các nhóm kiểm tra quan trọng nếu change có liên quan.
-->

---

## 1. Spec / AC

| #     | Hạng mục                                                                                                                         | Mức độ  | Trạng thái |
| ----- | -------------------------------------------------------------------------------------------------------------------------------- | ------- | ---------- |
| RC-01 | Toàn bộ 16 AC ở `spec-pack.md §7` (`AC-FINDING-CLASSIFY-1..5`, `AC-DENSITY-CALC-1..6`, `AC-RECALC-1..2`, `AC-STATUS-1,5`, `AC-REG-1`) đã được triển khai và có bằng chứng | Blocker | [ ]        |
| RC-02 | Không thêm hành vi nào nằm ngoài phạm vi spec-pack mục `§2.2` (không dùng NLP để phân tích nội dung comment, không dùng `tbl_fact_ai_finding_stat`/AI-finding KPI làm nguồn, không ranking cá nhân reviewer, không redesign toàn bộ Ticket Detail) | Blocker | [ ]        |
| RC-04 | Flow chính (finding classification là bước bắt buộc sau khi review/review comment được đồng bộ, density tính từ dữ liệu đã lưu — không gọi GitHub runtime mỗi lần mở Ticket Detail) tuân đúng spec §4.2/NFR §6.1; không tạo flow hoặc behavior thay thế ngoài spec | Blocker | [ ]        |

## 2. Thiết kế / Phụ thuộc

| #     | Hạng mục                                                                                                                       | Mức độ  | Trạng thái |
| ----- | ------------------------------------------------------------------------------------------------------------------------------ | ------- | ---------- |
| RC-05 | `FindingClassifier` (pure logic, metadata-only) và `ReviewFindingDensityService` được đặt ở đúng layer `application/usecase`; không mutate `tbl_fact_finding` rải rác ở nhiều nơi | Major   | [ ]        |
| RC-06 | Logic đọc/ghi `tbl_fact_finding`, `tbl_fact_pull_request_changed_file` được gom ở adapter/writer riêng (`FindingWriter`, `GitPrMetadataCollectorJdbcAdapter`); `application` không phụ thuộc trực tiếp SQL/GraphQL response cụ thể | Major   | [ ]        |
| RC-07 | Không phát sinh phụ thuộc vòng tròn giữa `domain`/`application`/`infrastructure`/`web`; GraphQL client mới (`GithubReviewThreadAdapter`) cô lập trong `infrastructure/github`, không rò rỉ DTO GraphQL ra `application`/`web` (NFR §6.7) | Blocker | [ ]        |
| RC-08 | Contract API `GET /api/v1/pm/dashboard/tickets/{ticketId}/detail` chỉ **thêm field mới** `reviewFindingDensity`, không đổi/xóa field cũ, khớp đúng shape đã chốt ở impl-plan §2.2/§4.3 (IMPL-OI-3, Resolved) | Blocker | [ ]        |
| RC-09 | Vòng đời finding (`OPEN → RESOLVED`) được tách rõ trong `FindingWriter`/`FindingClassifier`; không trộn logic finding lifecycle với logic đồng bộ PR/commit/changed-file hiện có | Major   | [ ]        |
| RC-10 | Thứ tự tác vụ trong `persistPullRequest` đúng theo quyết định §1.3/1.5 impl-plan: `findingWriter.deleteFindingsByPrId(prId)` chạy **trước** `persistence.deleteReviewsByPrId(prId)`, GraphQL fetch + classify + write chạy **sau** khi review/comment reinsert xong | Blocker | [ ]        |

## 3. Bảo mật

| #     | Hạng mục                                                                                                     | Mức độ  | Trạng thái |
| ----- | ------------------------------------------------------------------------------------------------------------ | ------- | ---------- |
| RC-11 | `pr_id`/`ticket_id`/`head_sha` dùng để truy vấn changed-lines/finding được validate đúng kiểu, không nhận input tự do từ client cho các trường này | Blocker | [ ]        |
| RC-12 | Reviewer identity trong finding/log join qua `tbl_dim_member_pseudonym` (`member_key`), không dùng raw GitHub user id, đúng `docs/standards/security.md` và `.claude/rules/30-security.md` | Blocker | [ ]        |
| RC-13 | Log "Finding created"/"Finding status changed"/"Density recalculated" không log secret/PII/nội dung diff hoặc source code thô (spec §6.2, NFR §6.4) | Blocker | [ ]        |
| RC-14 | Không có rủi ro injection khi build GraphQL query (`{query, variables}` gửi tới `/graphql`) — dùng tham số hóa qua `variables`, không nối chuỗi trực tiếp giá trị động vào query string | Blocker | [ ]        |
| RC-15 | GraphQL token dùng chung `props.connectors().github().apiToken()` hiện có (IMPL-OI-4), không hardcode credential mới, không đọc `.env` trực tiếp trong code review (`.claude/rules/00-safety.md §1`) | Major   | [ ]        |

## 4. Hiệu năng

| #     | Hạng mục                                                                                            | Mức độ | Trạng thái |
| ----- | ----------------------------------------------------------------------------------------------------- | ------ | ---------- |
| RC-16 | Density được tính từ dữ liệu đã lưu (`tbl_fact_finding` + `tbl_fact_pull_request_changed_file`), không gọi GitHub API (REST/GraphQL) trực tiếp mỗi lần mở Ticket Detail (NFR §6.1) | Blocker | [ ]        |
| RC-17 | Aggregation density SUM tổng finding/tổng changed-lines ở tầng DB hoặc service, không N+1 query theo từng PR liên kết ticket | Major  | [ ]        |
| RC-18 | Việc thêm field `reviewFindingDensity` vào `PmDashboardService.detail(...)` không làm tăng đáng kể số round-trip DB so với hiện tại (không query lặp lại không cần thiết) | Major  | [ ]        |
| RC-19 | GraphQL call (`fetchReviewThreads`) và mọi external HTTP call chạy async/ngoài DB transaction chính, không block main transaction của `persistPullRequest` (`.claude/rules/20-architecture.md`) | Major  | [ ]        |

## 5. Tương thích

| #     | Hạng mục                                                                        | Mức độ  | Trạng thái |
| ----- | --------------------------------------------------------------------------------- | ------- | ---------- |
| RC-20 | Dữ liệu `tbl_fact_pull_request_changed_file` cũ (backfill `head_sha = 'legacy'`) vẫn được cộng đúng vào tổng changed lines khi tính density, không bị loại bỏ silent | Blocker | [ ]        |
| RC-21 | Migration `V520__add_head_sha_to_pull_request_changed_file.sql` có down migration tương ứng (drop cột `head_sha`, khôi phục `UNIQUE (pr_id, file_path_hash)`) | Major   | [ ]        |
| RC-22 | Hành vi hiện có của `persistPullRequest` (upsert PR/commit/changed-file, xóa-chèn-lại review/review comment) không đổi ngoài phạm vi đổi khóa `head_sha` (AC-REG-1) | Blocker | [ ]        |

## 6. Logging / Audit

| #     | Hạng mục                                                                                                 | Mức độ  | Trạng thái |
| ----- | ---------------------------------------------------------------------------------------------------------- | ------- | ---------- |
| RC-24 | Log "Finding created" ghi đủ payload tối thiểu: `pr_id`, `head_sha`, review/comment gốc, thời điểm phát hiện (spec §5.X) | Major   | [ ]        |
| RC-26 | Log/audit không chứa PII, secret, hoặc nội dung diff/source code thô vượt mức cần thiết (spec §6.2) | Blocker | [ ]        |

## 7. Xử lý lỗi

| #     | Hạng mục                                                                                              | Mức độ  | Trạng thái |
| ----- | ------------------------------------------------------------------------------------------------------- | ------- | ---------- |
| RC-27 | Nếu dữ liệu finding/changed-lines của một PR không đọc được, hệ thống trả kết quả một phần hoặc `N/A` thay vì lỗi toàn bộ Ticket Detail (NFR §6.3) | Blocker | [ ]        |
| RC-28 | Tổng changed lines = 0 → hiển thị `N/A`, không hiển thị `0`; 0 finding hợp lệ với changed lines > 0 → hiển thị `0`, không hiển thị `N/A` (AC-DENSITY-CALC-3, AC-DENSITY-CALC-4) — hai trường hợp phải phân biệt rõ (`Optional.empty()` vs `Optional.of(ZERO)`), không trộn lẫn | Blocker | [ ]        |
| RC-29 | `FindingWriter.deleteFindingsByPrId(prId)` chạy an toàn kể cả khi PR chưa có finding nào (không lỗi khi danh sách rỗng) | Major   | [ ]        |
| RC-30 | Không xảy ra vi phạm FK (`tbl_fact_finding.review_id`/`review_comment_id`) khi review bị xóa-chèn-lại ở lần đồng bộ PR kế tiếp — finding writer phải dọn dẹp theo `pr_id` trước bước xóa review (quyết định §1.3/1.5 impl-plan), không dựa vào `ON DELETE CASCADE` | Blocker | [ ]        |
| RC-31 | Nếu GraphQL call thất bại/timeout, việc ghi PR/commit/review/review-comment ở transaction chính của `persistPullRequest` không bị rollback theo lỗi finding sync (finding sync là `REQUIRES_NEW` riêng, lỗi độc lập) | Major   | [ ]        |
| RC-32 | Client (FE) không nhận stack trace nội bộ khi endpoint `/detail` có lỗi liên quan density; lỗi tuân theo `GlobalExceptionHandler`/`ErrorResponse` hiện có (`docs/standards/security.md`) | Major   | [ ]        |

## 8. Kiểm thử

| #     | Hạng mục                                                                                            | Mức độ | Trạng thái |
| ----- | ------------------------------------------------------------------------------------------------------- | ------ | ---------- |
| RC-33 | Test JDBC adapter cho `upsertPullRequestChangedFile` với khóa cũ đã được bổ sung **trước khi** đổi unique key sang `(pr_id, head_sha, file_path)` (impl-plan step A.1, IMPL-OI-7) | Blocker | [ ]        |
| RC-34 | Unit test bao phủ đủ kịch bản `AC-FINDING-CLASSIFY-1..5` (mọi review state hợp lệ → finding, `UNKNOWN`/trống → không finding, thread chỉ là câu hỏi/LGTM vẫn tính finding, nhiều thread → nhiều finding, nhiều comment cùng thread → dedupe) | Major  | [ ]        |
| RC-35 | Unit test bao phủ đủ 6 kịch bản `AC-DENSITY-CALC-1..6` (N/M×1000, tổng nhiều PR không average, N/A khi denominator=0, 0 finding hợp lệ khi denominator>0, breakdown không đổi tổng, rounding HALF_UP scale=1) | Major  | [ ]        |
| RC-36 | Unit/Integration test bao phủ `AC-RECALC-1,2` (commit mới → tính lại theo `head_sha` mới, không cộng dồn theo từng commit trung gian, finding cũ không tự resolve) và `AC-STATUS-1,5` (GraphQL `isResolved` → `RESOLVED`, không bị reset khi có commit mới không liên quan) | Major  | [ ]        |
| RC-37 | Regression test (`AC-REG-1`): `GitPrMetadataCollectorServiceTest` hiện có tiếp tục pass sau khi thêm bước finding classification; test integration đồng bộ PR 2 lần liên tiếp xác nhận không lỗi FK | Blocker | [ ]        |
| RC-38 | Contract/Integration test cho `GET /tickets/{ticketId}/detail` xác nhận field `reviewFindingDensity` đúng cho ticket có/không có PR liên kết, và các field cũ không đổi | Major  | [ ]        |
| RC-39 | FE: Vitest test cho `formatReviewFindingDensity` (case `null → "N/A"`, số → `"X.X findings/KLOC"`); visual check card/breakdown hiển thị đúng trên `TicketDetailDrawer.tsx` (empty/N/A state) | Major  | [ ]        |

## 9. Vận hành

| #     | Hạng mục                                                                                  | Mức độ | Trạng thái |
| ----- | ------------------------------------------------------------------------------------------- | ------ | ---------- |
| RC-40 | Quy trình rollback migration `V519` (drop cột `head_sha`, khôi phục unique key cũ) được tài liệu hóa ở impl-plan §7.2, kèm cảnh báo mất dữ liệu `head_sha` đã backfill nếu rollback sau khi có dữ liệu mới | Major  | [ ]        |
| RC-41 | Thứ tự rollback code theo impl-plan §7.1 (Part D → C → B → A) được tài liệu hóa; không có state FE persisted phức tạp cần clear thủ công | Minor  | [ ]        |
| RC-42 | Tên biến cấu hình GraphQL endpoint (`graphqlApiBaseUrl` hoặc tương đương) đặt ở `AppProperties`/config tập trung, không hardcode rải rác trong code | Minor  | [ ]        |
| RC-43 | Không thêm feature flag/config switch nào ngoài yêu cầu spec (spec không yêu cầu feature flag — nếu implementation tự thêm để giảm rủi ro GraphQL rate limit, phải ghi rõ lý do và cách rollback ở self-review) | Minor  | [ ]        |

---

## Bảng ánh xạ AC → Checklist items

<!--
Cách dùng:
- Mỗi AC trong spec phải xuất hiện ít nhất một lần.
- Một RC có thể map tới nhiều AC.
- Nếu AC không có checklist item tương ứng, thêm RC mới.
-->

| #   | AC                          | Các hạng mục checklist xác nhận                    |
| --- | ---------------------------- | --------------------------------------------------- |
| 1   | AC-FINDING-CLASSIFY-1/v1     | RC-01, RC-07, RC-10, RC-34                          |
| 2   | AC-FINDING-CLASSIFY-2/v1     | RC-01, RC-34                                        |
| 3   | AC-FINDING-CLASSIFY-3/v1     | RC-01, RC-02, RC-34                                 |
| 4   | AC-FINDING-CLASSIFY-4/v1     | RC-01, RC-34                                        |
| 5   | AC-FINDING-CLASSIFY-5/v1     | RC-01, RC-34                                        |
| 6   | AC-DENSITY-CALC-1/v1         | RC-01, RC-16, RC-35                                 |
| 7   | AC-DENSITY-CALC-2/v1         | RC-01, RC-17, RC-35, RC-38                          |
| 8   | AC-DENSITY-CALC-3/v1         | RC-01, RC-28, RC-35                                 |
| 9   | AC-DENSITY-CALC-4/v1         | RC-01, RC-28, RC-35                                 |
| 10  | AC-DENSITY-CALC-5/v1         | RC-01, RC-35                                        |
| 11  | AC-DENSITY-CALC-6/v1         | RC-01, RC-35                                        |
| 12  | AC-RECALC-1/v1               | RC-01, RC-20, RC-21, RC-36                          |
| 13  | AC-RECALC-2/v1               | RC-01, RC-23, RC-36                                 |
| 14  | AC-STATUS-1/v1                | RC-01, RC-25, RC-36                                 |
| 18  | AC-STATUS-5/v1                | RC-01, RC-23, RC-36                                 |
| 17  | AC-REG-1/v1                   | RC-01, RC-04, RC-22, RC-30, RC-37                   |
