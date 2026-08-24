# Test Data

**Ticket ID**: PROMPT_TEMPLATE_REUSE_RATE
**Create date**: 2026-08-17
**Author**: QA Designer (Phase 6 — Black-box Test Spec)
**Update date**: 2026-08-17

## Data Policy

- Toàn bộ dữ liệu bên dưới là dữ liệu giả lập, tạo riêng cho việc kiểm thử ticket này — không dùng dữ liệu dự án/repository/tài khoản thật của khách hàng.
- Không dùng ảnh chụp/bản sao dữ liệu production.
- Không lưu bất kỳ thông tin định danh cá nhân (PII), mật khẩu, token thật vào tài liệu test hay log kết quả test.
- Sau mỗi lần chạy test có merge PR / ghi số liệu, phải dọn lại số liệu test (xem "Data Cleanup Procedure") để không làm sai lệch số liệu thật của các dự án khác trên cùng môi trường test.

## Master Data

| name | value | purpose |
|---|---|---|
| Dự án test | `TEST-PROJ-01` (tên hiển thị: "Test Project - Template Usage") | Dự án dùng riêng cho bộ test này, tách biệt dữ liệu với các ticket khác |
| Repository test | `test-org/test-repo-template-usage` | Repository GitHub test gắn với dự án trên |
| Mã ticket giả lập trong repo test | `DEMO-TPL-001` | Dùng làm `{mã-ticket}` trong đường dẫn `documents/docs/changes/DEMO-TPL-001/...` khi tạo PR test |
| 8 giai đoạn (phase) trong phạm vi | `'1'` Spec Pack, `'2'` Working Files Initialization, `'3'` Implementation Plan, `'4'` Review Checklist, `'5'` Implementation and Review, `'6'` Test Plan and Results, `'7'` Blackbox Test, `'8'` Report | Danh sách 8 phase phải luôn xuất hiện đủ trên Dashboard |
| Phase/luồng ngoài phạm vi (để test loại trừ) | `0-A` (rà soát an toàn AI) | Dùng cho BB-022 — xác nhận không bị rò rỉ vào kết quả |
| Vị trí mẫu ưu tiên 1 | `documents/docs/standards/templates/{tên-file}` | |
| Vị trí mẫu ưu tiên 2 | `documents/docs/standards/templates/_ticket-template/{tên-file}` | |
| Vị trí mẫu ưu tiên 3 | `documents/docs/standards/templates/_light-ticket-template/{tên-file}` | |

## User / Permission Data

| user | role | permission | purpose |
|---|---|---|---|
| `qa.pm.projA` | PM (được gán vào `TEST-PROJ-01`) | Xem được thống kê của `TEST-PROJ-01` | BB-008, BB-029 |
| `qa.pm.projB` | PM (chỉ được gán vào dự án khác, KHÔNG phải `TEST-PROJ-01`) | Không được xem thống kê của `TEST-PROJ-01` | BB-030 |
| `qa.member.noPm` | Thành viên thường, đăng nhập hợp lệ, không có vai trò PM ở bất kỳ dự án nào | Không được xem thống kê của `TEST-PROJ-01` | BB-030 (biến thể) |
| `qa.admin` | Quản trị hệ thống (ADMIN), không gán riêng vai trò PM ở `TEST-PROJ-01` | Vẫn xem được thống kê mọi dự án | BB-031 |

## Normal Data

| ID | data | purpose |
|---|---|---|
| ND-01 | File `documents/docs/changes/DEMO-TPL-001/spec-pack.md` với đúng các tiêu đề: `# Spec Pack`, `## 1. Overview`, `## 2. Scope`, ..., theo đúng thứ tự và cấp độ như file mẫu `_ticket-template/spec-pack.md` hiện có trong repo | BB-001, BB-003, BB-006 |
| ND-02 | Cùng file trên nhưng phần nội dung bên trong mỗi mục được viết lại hoàn toàn khác (đổi hết câu chữ, số liệu ví dụ) trong khi giữ nguyên toàn bộ tiêu đề | BB-006 |
| ND-03 | PR webhook payload mẫu với `action: "closed"`, `pull_request.merged: true`, danh sách file thay đổi gồm ND-01 | BB-001, BB-027 |
| ND-04 | 1 loại tài liệu mới, ví dụ `retro-notes.md`, được cấu hình (chỉ bằng dữ liệu, không sửa code) để thuộc phase `'5'` (Implementation and Review) | BB-007 |

## Error Data

| ID | data | expected error |
|---|---|---|
| ED-01 | File `documents/docs/changes/DEMO-TPL-001/unknown-doc-type.md` — loại tài liệu không khớp tên bất kỳ file mẫu nào ở cả 3 vị trí ưu tiên | Không có lỗi hiển thị; file bị bỏ qua âm thầm (BB-009) |
| ED-02 | File `review-checklist.md` với thứ tự các mục `## 2. Scope` và `## 1. Overview` bị đảo ngược so với mẫu (tên mục giữ nguyên) | FAIL (BB-010) |
| ED-03 | Request gửi tới webhook endpoint với header chữ ký (signature) bị sửa 1 ký tự so với chữ ký hợp lệ | Toàn bộ request bị từ chối, không xử lý gì (BB-011) |
| ED-04 | Giả lập GitHub trả lỗi "không tìm thấy" (404) khi tải nội dung 1 trong 2 file của cùng 1 PR | File đó bị bỏ qua, file còn lại vẫn được xử lý (BB-012) |
| ED-05 | Giả lập GitHub trả lỗi hệ thống tạm thời (5xx) khi tải nội dung file | Toàn bộ webhook cho PR đó thất bại thật sự (BB-013) |
| ED-06 | Giả lập GitHub trả lỗi "không có quyền" (403) ngay ở bước xác định phiên bản/nội dung tổng thể PR (trước khi xét từng file) | Toàn bộ việc kiểm tra mẫu cho PR bị bỏ qua, không ảnh hưởng phần xử lý PR khác (BB-014) |
| ED-07 | Giả lập GitHub trả lỗi hệ thống tạm thời (5xx) ngay ở bước xác định phiên bản/nội dung tổng thể PR | Toàn bộ webhook cho PR đó thất bại thật sự (BB-015) |
| ED-08 | Giả lập lỗi cơ sở dữ liệu tạm thời (mất kết nối) khi ghi số liệu cho file thứ nhất trong PR có 2 file hợp lệ | File thứ nhất không được cộng số liệu; file thứ hai vẫn được ghi nhận; webhook hoàn tất bình thường (BB-016) |
| ED-09 | File thuộc loại tài liệu không được cấu hình gắn với bất kỳ phase nào | File bị loại khỏi thống kê, không có lỗi (BB-017) |
| ED-10 | File tiêu đề dùng số toàn chiều rộng: `１. Tổng quan` (so với mẫu `1. Tổng quan`) | FAIL (BB-023) |
| ED-11 | File tiêu đề khác biệt hoa/thường: `Tổng Quan` (so với mẫu `Tổng quan`) | FAIL (BB-024) |
| ED-12 | Sự kiện webhook `action: "closed", merged: true` của cùng 1 PR (đã xử lý trước đó) được gửi lại lần 2 với cùng nội dung | Số liệu bị cộng thêm 1 lần nữa — hành vi đã biết, được chấp nhận (BB-028) |

## Boundary Data

| ID | item | value | expected |
|---|---|---|---|
| BD-01 | Số lượt kiểm tra của 1 phase | `totalCheckCount = 0` (phase chưa từng có file nào được kiểm tra) | Hiển thị dấu "-" thay vì "0%" (BB-018) |
| BD-02 | Số lượt đạt so với tổng số lượt | `templateMatchCount = totalCheckCount` (tất cả đều PASS) | Hiển thị tỷ lệ 100% (BB-019) |
| BD-03 | Số tiêu đề trong file thay đổi | `0` tiêu đề (toàn văn bản thường), mẫu có `≥ 1` tiêu đề | FAIL (BB-020) |
| BD-04 | Số file hợp lệ trong 1 PR | `0` (PR chỉ sửa file ngoài thư mục ticket) | Không có phase nào thay đổi số liệu (BB-021) |
| BD-05 | Số phase hiển thị trên Dashboard | Đúng `8` phase trong phạm vi (`'1'..'8'`), có dữ liệu song song ở phase ngoài phạm vi (`0-A`) | Chỉ 8 phase trong phạm vi xuất hiện, `0-A` không xuất hiện (BB-022) |

## Existing Data Compatibility

- Trước khi test BB-004/BB-005 (mẫu tồn tại song song ở 2 vị trí ưu tiên khác nhau), cần xác nhận các file mẫu hiện có trong `documents/docs/standards/templates/` và `.../_ticket-template/` không bị thay đổi ngoài kế hoạch — chỉ thêm file test tạm thời cho mục đích kiểm thử, không sửa file mẫu dùng chung cho các ticket khác.
- Dữ liệu số liệu (`total_check_count`, `template_match_count`) của các dự án/repository thật đang chạy song song trên cùng môi trường test không được bị ảnh hưởng bởi các PR test — luôn dùng riêng `TEST-PROJ-01` / `test-org/test-repo-template-usage` (xem Master Data).

## Data Setup Procedure

1. Tạo dự án test `TEST-PROJ-01` và gắn với repository test `test-org/test-repo-template-usage` trên môi trường test.
2. Gán vai trò PM cho `qa.pm.projA` vào `TEST-PROJ-01`; đảm bảo `qa.pm.projB`/`qa.member.noPm` KHÔNG được gán vai trò PM ở dự án này; đảm bảo `qa.admin` có vai trò ADMIN hệ thống.
3. Chuẩn bị sẵn các file tài liệu mẫu (ND-01, ND-02, ED-01 đến ED-11) trong một nhánh làm việc riêng của repository test, chưa merge.
4. Với các trường hợp cần giả lập lỗi từ GitHub/cơ sở dữ liệu (ED-04 đến ED-08), chuẩn bị trước với đội phát triển cách bật/tắt giả lập lỗi (ví dụ qua môi trường test riêng có công tắc giả lập lỗi), không thực hiện trên kết nối GitHub/DB thật.

## Data Cleanup Procedure

1. Sau mỗi PR test đã merge, ghi lại số liệu trước/sau tại các phase liên quan để đối chiếu đúng kỳ vọng.
2. Xoá/đóng các PR, nhánh test đã tạo trên repository test sau khi hoàn tất bộ test.
3. Dọn (reset) số liệu `total_check_count`/`template_match_count` của `TEST-PROJ-01` về trạng thái ban đầu (hoặc xoá toàn bộ dữ liệu test của dự án này) sau khi kết thúc đợt kiểm thử, để lần chạy test sau không bị cộng dồn sai lệch.
4. Không xoá dữ liệu của bất kỳ dự án/repository nào khác ngoài `TEST-PROJ-01`.

## Sensitive Data Handling

- Do not use original production data.
- Do not save PII/secrets to artifacts.
- Không dùng token/thông tin đăng nhập GitHub thật của bất kỳ ai trong quá trình test — dùng tài khoản/App test riêng theo đúng quy trình bảo mật hiện có của môi trường test.
