# Ticket Rules:

**Ticket ID**: REVIEW-FINDING-DENSITY
**Create date**: 2026-09-10 15:30:00
**Author**: nvt_dung
**Update date**: 2026-09-10 15:30:00

## Must Follow

- Do not add specifications not included in the spec-pack.
- Ambiguous points must be returned as Open Issues.
- Before implementation, read the target source and existing tests.
- Follow existing patterns.
- Do not reuse obvious errors, vulnerabilities, or existing SonarLint violations.
- Check for full-width numbers, half-width numbers, empty strings, nulls, digits, and precision.
- Business code values ​​should be in enum/constant/master format instead of magic numbers.
- Do not export secrets/PII to logs

## Must Not Do

- Không dùng `tbl_fact_ai_finding_stat` hoặc `AiFindingStatJdbcAdapter` làm nguồn đọc/ghi cho Review Finding Density — đây là KPI riêng cho AI review, spec-pack §2.2 cấm rõ.
- Không dùng NLP/phân tích ngữ nghĩa nội dung comment để suy đoán một thread có phải LGTM/ACK/câu hỏi hay không — OI-REVIEW-FINDING-DENSITY-003 đã Closed, quyết định là metadata-only (review state + thread tồn tại qua GraphQL).
- Không tính density bằng trung bình cộng per-PR (`avg(N_i/M_i)`) khi ticket có nhiều PR liên kết — công thức bắt buộc là tổng finding / tổng changed lines của tất cả PR (spec §5.3, AC-DENSITY-CALC-2).
- Không tự động chuyển finding sang `RESOLVED` chỉ vì PR có commit mới — chỉ GitHub thread `isResolved` qua GraphQL được dùng để đổi trạng thái (spec §5.2 `T-NEW-COMMIT-NO-AUTO-RESOLVE`, AC-STATUS-5).
- Không hiển thị `0` khi tổng changed lines = 0 — bắt buộc hiển thị `N/A` (spec §5.3, AC-DENSITY-CALC-3).
- Không cộng dồn additions/deletions theo từng commit trung gian — chỉ tính theo diff hiện tại của `head_sha` mới nhất (spec §5.3, §5.4, AC-RECALC-1).
- Không đọc/ghi nội dung diff hoặc source code thô của PR (NFR §6.2).
- Không dùng raw GitHub user id để định danh reviewer trong DB/log — phải join qua `tbl_dim_member_pseudonym` (NFR §6.2, `docs/standards/security.md`).
- Không gọi GitHub API (REST hoặc GraphQL) trực tiếp mỗi lần mở Ticket Detail — density phải đọc từ dữ liệu đã lưu/tính sẵn (NFR §6.1).
- Không rò rỉ chi tiết GraphQL (query, response shape) ra khỏi lớp `infrastructure` của GitHub adapter (NFR §6.7).
- Không chạy external HTTP call (GitHub REST/GraphQL) bên trong cùng DB transaction với ghi dữ liệu (`.claude/rules/20-architecture.md`).

## Stop / Ask Conditions

- Nếu cần thêm cột nào khác ngoài `head_sha` vào `tbl_fact_pull_request_changed_file` hoặc đổi khóa khác với `(pr_id, head_sha, file_path)` đã chốt ở OI-002.
- Nếu GraphQL rate limit thực sự chặn tần suất đồng bộ PR hiện tại và cần thêm cơ chế cache/batch mới ngoài những gì spec đã mô tả (rủi ro #1, spec §9) — dừng lại và hỏi trước khi tự thiết kế cơ chế cache.
- Nếu chưa có chính sách backfill rõ ràng cho dữ liệu `tbl_fact_pull_request_changed_file` cũ (không có `head_sha`) trước khi đổi khóa unique (rủi ro #2, spec §9).
- Nếu luồng feature cố tạo status ngoài `OPEN/RESOLVED`; các giá trị lịch sử khác của enum DB không thuộc write flow của feature này.
- Nếu cần xác định route/contract API cụ thể cho endpoint density mới mà spec-pack không mô tả chi tiết (spec §2.2 nói rõ chi tiết API cụ thể thuộc Phase 2/impl-plan).
- Nếu phát hiện `tbl_fact_finding`/`tbl_fact_review`/`tbl_fact_review_comment` có dữ liệu runtime thật khác với giả định "bảng đang trống về flow ghi" trong sources.md.

## Review Focus

- Đúng công thức density: tổng finding hợp lệ / tổng changed lines × 1000, không phải trung bình per-PR (AC-DENSITY-CALC-1, -2).
- Rule `N/A` khi tổng changed lines = 0, phân biệt rõ với density = 0 hợp lệ khi có finding = 0 nhưng changed lines > 0 (AC-DENSITY-CALC-3, -4).
- Rounding đúng 1 chữ số thập phân, `RoundingMode.HALF_UP` (AC-DENSITY-CALC-6, OI-004).
- Finding eligibility chỉ dựa metadata (review state hợp lệ + thread tồn tại qua GraphQL `reviewThreads`); `UNKNOWN`/trống bị loại, không có logic đọc nội dung comment (AC-FINDING-CLASSIFY-1..5).
- Dedupe đúng theo thread identity thật từ GraphQL, không dedupe theo heuristic file+line tự chế (spec §5.1).
- GraphQL client cô lập trong infrastructure layer, không expose kiểu dữ liệu GraphQL ra ngoài (NFR §6.7).
- Recalculation theo `head_sha` mới không làm mất lịch sử snapshot cũ theo `head_sha` trước đó (spec §5.4).
- Reviewer identity trong mọi bảng/log mới đều join qua `tbl_dim_member_pseudonym` (NFR §6.2).
- Log created/status-changed/recalculated đủ để truy vết theo `pr_id`/`head_sha` (NFR §6.4).
- Hành vi đồng bộ PR/commit/changed-file/review hiện có không bị thay đổi ngoài phạm vi OI-002 (AC-REG-1).

## Test Focus

- `AC-FINDING-CLASSIFY-1..5`: review state hợp lệ với nhiều thread → nhiều finding; `UNKNOWN`/trống → không finding; thread chỉ là câu hỏi/LGTM vẫn tính là finding; nhiều comment cùng thread → gộp một finding.
- `AC-DENSITY-CALC-1..6`: 1 PR N finding/M dòng → N/M×1000; nhiều PR → tổng/tổng không phải trung bình; denominator=0 → N/A; 0 finding + changed lines>0 → 0 (không phải N/A); breakdown theo status không đổi tổng; rounding HALF_UP đúng ví dụ 5.25→5.3, 5.24→5.2.
- `AC-RECALC-1..2`: commit mới → tính lại changed lines theo `head_sha` mới, không cộng dồn theo commit trung gian; finding cũ không tự resolve khi có commit mới.
- `AC-STATUS-1,5`: GitHub thread resolve (GraphQL `isResolved`) → `RESOLVED`; finding `RESOLVED` giữ nguyên khi có commit mới không liên quan.
- `AC-REG-1`: luồng đồng bộ PR/commit/changed-file/review hiện có (trước khi thêm finding classification) không bị regress — cần snapshot test trước/sau nếu có thể.
