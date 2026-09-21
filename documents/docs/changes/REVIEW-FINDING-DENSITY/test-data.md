# Test Data

**Ticket ID**: REVIEW-FINDING-DENSITY
**Create date**: 2026-09-15 16:48:01
**Author**: nvt_dung
**Update date**: 2026-09-15 16:48:01

## Data Policy

- Không dùng dữ liệu production thật; không dùng GitHub token/username thật (theo `.claude/rules/30-security.md`, `docs/standards/security.md`).
- Định danh reviewer chỉ dùng giá trị giả lập (pseudonym/`member_key`), không dùng raw user id — theo NFR §6.2 spec-pack.
- Mọi giá trị `pr_id`, `ticket_id`, `head_sha`, thread id trong bảng dưới đây là ký hiệu tham chiếu (`PR-A`, `TCK-1`, `SHA-1`...), không phải giá trị thật cần tồn tại sẵn trong DB — người thực thi test map sang dữ liệu seed cụ thể của môi trường test.

## Master Data

| name | value | purpose |
|---|---|---|
| `TCK-1` | Ticket liên kết 1 PR (`PR-A`) | Case density 1-PR (BB-RFD-DENSITY-01) |
| `TCK-2` | Ticket liên kết 2 PR (`PR-B`, `PR-C`) | Case aggregation nhiều PR (BB-RFD-DENSITY-02) |
| `TCK-3` | Ticket chưa liên kết PR nào | Case N/A do không có dữ liệu (BB-RFD-DENSITY-03) |
| `PR-A` | PR với review state `APPROVED`, 3 thread hợp lệ, `head_sha = SHA-1` | Case classify nhiều thread (BB-RFD-CLASSIFY-01) |
| `PR-B` | PR với review state `CHANGES_REQUESTED`, 1 thread có nhiều comment qua lại | Case dedupe theo thread (BB-RFD-CLASSIFY-04) |
| `PR-C` | PR với review state `REVIEW_REQUIRED`, changed lines > 0, 0 finding | Case density = 0 (BB-RFD-DENSITY-04) |
| `PR-D` | PR với review state `UNKNOWN` (hoặc review comment không gắn state) | Case không tạo finding (BB-RFD-CLASSIFY-02) |
| `PR-E` | PR chỉ đổi file binary (changed lines = 0) | Case N/A dù có PR liên kết (BB-RFD-DENSITY-03) |
| `THREAD-LGTM-1` | Thread dưới `PR-A`, nội dung comment chỉ là "LGTM"/câu hỏi làm rõ | Case metadata-only, không NLP (BB-RFD-CLASSIFY-03) |
| `SHA-1`, `SHA-2` | Hai `head_sha` khác nhau của cùng `PR-A` sau khi có commit mới | Case regression sync 2 lần không vỡ dữ liệu (BB-RFD-REG-01) |

## User / Permission Data

| user | role | permission | purpose |
|---|---|---|---|
| `pm-user-1` | PM | Đọc `GET /tickets/{ticketId}/detail` (đã có sẵn) | Smoke xác nhận PM xem được `reviewFindingDensity` — không mở rộng ma trận quyền (theo spec §2.2, không redesign phân quyền) |
| `qa-user-1` | QA | Đọc `GET /tickets/{ticketId}/detail` (đã có sẵn) | Smoke tương tự cho vai trò QA |

## Normal Data

| ID | data | purpose |
|---|---|---|
| ND-1 | `PR-A`: 3 finding hợp lệ (mỗi thread 1 finding), 500 changed lines | AC-DENSITY-CALC-1: density = 3/500×1000 = 6.0 findings/KLOC |
| ND-2 | `TCK-2`: `PR-B` có 3 finding/500 changed lines, `PR-C` có 2 finding/500 changed lines | AC-DENSITY-CALC-2: density tổng = (3+2)/(500+500)×1000 = 5.0 findings/KLOC (không lấy trung bình per-PR: (6+4)/2=5 trùng ngẫu nhiên với ví dụ này — dùng thêm ND-2b để phân biệt rõ khi 2 cách tính ra kết quả khác nhau) |
| ND-2b | `TCK-2` biến thể: `PR-B` có 1 finding/100 changed lines (=10/KLOC), `PR-C` có 1 finding/900 changed lines (≈1.1/KLOC) | Trung bình per-PR sai = (10+1.1)/2 ≈ 5.6; đúng theo spec = (1+1)/(100+900)×1000 = 2.0/KLOC — dùng để khẳng định hệ thống không lấy trung bình |
| ND-3 | `PR-A` có review `APPROVED` + 3 review thread riêng biệt (khác file/vị trí dòng) | AC-FINDING-CLASSIFY-1, -4: mỗi thread → đúng 1 finding |
| ND-4 | `THREAD-LGTM-1`: 1 thread, nội dung 2 comment qua lại chỉ là hỏi đáp/LGTM, thuộc review `CHANGES_REQUESTED` | AC-FINDING-CLASSIFY-3: vẫn tính là finding (metadata-only) |
| ND-5 | `PR-B`: 1 thread có 4 comment nối tiếp nhau (cùng thread id qua GraphQL `reviewThreads`) | AC-FINDING-CLASSIFY-5: gộp thành 1 finding duy nhất |

## Error Data

| ID | data | expected error |
|---|---|---|
| ED-1 | `PR-D`: review `state = UNKNOWN` | Không tạo finding nào từ review này (AC-FINDING-CLASSIFY-2); density của ticket chứa `PR-D` không tăng |
| ED-2 | Review trống (không có state, hoặc state không nhận diện được trong tập `APPROVED/CHANGES_REQUESTED/REVIEW_REQUIRED`) | Không tạo finding; không có lỗi hệ thống (log nội bộ nếu cần, không phá luồng Ticket Detail) |
| ED-3 | Một PR liên kết ticket không đọc được finding/changed-lines (giả lập lỗi nguồn dữ liệu cho 1 PR trong ticket nhiều PR) | Hệ thống trả kết quả một phần hoặc `N/A` cho phần đó, không lỗi toàn bộ Ticket Detail (NFR §6.3) |

## Boundary Data

| ID | item | value | expected |
|---|---|---|---|
| BD-1 | Tổng changed lines của các PR liên kết | 0 (ví dụ `PR-E` chỉ đổi file binary) | Hiển thị `N/A`, không hiển thị `0` (AC-DENSITY-CALC-3) |
| BD-2 | Ticket chưa có PR liên kết | `TCK-3`, 0 PR | Hiển thị `N/A` (AC-DENSITY-CALC-3) |
| BD-3 | Finding count = 0, changed lines > 0 | `PR-C`: 0 finding, 500 changed lines | Hiển thị `0 findings/KLOC` — phân biệt rõ với N/A (AC-DENSITY-CALC-4) |
| BD-4 | Giá trị density thô trước khi làm tròn | 5.25 | Hiển thị `5.3` (HALF_UP, AC-DENSITY-CALC-6) |
| BD-5 | Giá trị density thô trước khi làm tròn | 5.24 | Hiển thị `5.2` (AC-DENSITY-CALC-6) |
| BD-6 | Giá trị density thô đúng biên `.x50` khác | 0.05 (ví dụ 1 finding / 20000 changed lines × 1000 = 0.05) | Hiển thị `0.1` (HALF_UP làm tròn lên ở biên chẵn/lẻ) |

## Existing Data Compatibility

- Dữ liệu `tbl_fact_pull_request_changed_file` được backfill với `head_sha = 'legacy'` (theo impact-analysis mục 9) phải vẫn được cộng dồn đúng vào tổng changed lines khi tính density — không bị loại bỏ chỉ vì mang giá trị sentinel này.
- Case dùng để verify (quan sát qua response `/detail`, không qua SQL trực tiếp): ticket có 1 PR với dữ liệu changed-lines cũ mang `head_sha='legacy'` và không có sync mới nào sau khi nâng cấp — density vẫn tính ra giá trị khác `N/A`/`0` một cách hợp lý dựa trên changed lines đã backfill.

## Data Setup Procedure

1. Seed ticket và PR liên kết theo bảng Master Data (qua fixture/test harness hiện có của môi trường test — không tạo hạ tầng seed mới).
2. Seed review + review thread/comment theo state tương ứng cho từng PR (ND-1..ND-5, ED-1..ED-3).
3. Trigger đồng bộ PR (webhook/poll giả lập) để hệ thống chạy flow classification + density calculation.
4. Gọi `GET /tickets/{ticketId}/detail` hoặc mở Ticket Detail trên UI để quan sát kết quả.

## Data Cleanup Procedure

- Xóa/reset ticket, PR, review, finding test data sau khi chạy xong theo cơ chế dọn dẹp môi trường test hiện có (transaction rollback hoặc script cleanup theo test suite hiện tại).
- Không để lại dữ liệu test mang tính định danh thật trong môi trường chia sẻ.

## Sensitive Data Handling

- Do not use original production data.
- Do not save PII/secrets to artifacts.
- Không dùng GitHub token/username thật trong bất kỳ bước setup nào ở trên.
