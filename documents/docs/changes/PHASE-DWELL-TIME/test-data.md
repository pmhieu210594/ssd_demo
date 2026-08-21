# test-data

**Ticket ID**: PHASE-DWELL-TIME
**Create date**: 2026-08-19
**Author**: QA Designer (Claude)
**Update date**: 2026-08-21

> Dữ liệu dưới đây phục vụ chạy các test case ở `blackbox-testcases.md` (cùng thư mục). Tất cả
> ticket đều là **ticket test riêng**, không dùng ticket khách hàng/ticket thật đang vận hành.

## Dữ liệu chung

**7 giai đoạn (phase) được Card "Phase Dwell Time" hiển thị, đúng theo thứ tự:**

| Thứ tự hiển thị | Tên giai đoạn hiển thị trên màn hình |
|---|---|
| 1 | Spec Pack |
| 2 | Implementation Plan |
| 3 | Review Checklist |
| 4 | Self Review |
| 5 | Test Plan |
| 6 | Test Results |
| 7 | Report |

**Các giai đoạn KHÔNG được hiển thị trong Card này** (dùng để kiểm tra ở BB-02): giai đoạn chuẩn
bị an toàn/bối cảnh ban đầu (0-A), giai đoạn kiểm tra AI nội bộ (0-B, nếu có), giai đoạn phát triển
mã nguồn (giai đoạn 2), giai đoạn đóng/hoàn tất ticket (giai đoạn 9).

**Danh sách ticket test dùng chung:**

| Ticket ID | Mục đích | Ghi chú |
|---|---|---|
| `DEMO-DWELL-01` | Ticket chính, dùng cho nhiều test case (BB-01, 02, 03, 10, 12, 13, 14, 15, 19, 21) | Có dữ liệu đầy đủ cho cả 7 giai đoạn |
| `DEMO-DWELL-02` | Kiểm tra cộng dồn nhiều hồ sơ trong 1 giai đoạn (BB-04) | |
| `DEMO-DWELL-03` | Kiểm tra thời gian vượt 24 giờ (BB-05) | |
| `DEMO-DWELL-04` | Kiểm tra trường hợp thời gian bằng 0 (BB-06) | |
| `DEMO-DWELL-05` | Kiểm tra giai đoạn chưa có dữ liệu (BB-07) | Ticket mới tạo |
| `DEMO-DWELL-06` | Kiểm tra hồ sơ thiếu ngày cập nhật (BB-08) | |
| `DEMO-DWELL-07` | Kiểm tra trộn hồ sơ đủ/thiếu cặp trong cùng giai đoạn (BB-09) | |
| `DEMO-DWELL-08` | Kiểm tra cập nhật dữ liệu thật rồi quét lại (BB-11) | |
| `DEMO-DWELL-09` | Kiểm tra dữ liệu ngày bị nhập sai thứ tự (BB-17) | |
| `DEMO-DWELL-10` | Kiểm tra lỗi kết nối nguồn khi quét (BB-18) | |
| `DEMO-DWELL-11` | Kiểm tra định dạng số/ký tự hiển thị (BB-23) | |
| `DEMO-DWELL-12` | Kiểm tra 1 giai đoạn có 2 hồ sơ, 1 hợp lệ + 1 hồ sơ ngày bị đảo (BB-25) | Bổ sung 2026-08-21 theo `ai-review.md` F1/TC4 |
| `LEGACY-DWELL-01` | Ticket cũ mô phỏng chưa quét lại sau khi có tính năng (BB-20) | Không thao tác gì thêm lên ticket này |

## Dữ liệu normal

| Bộ dữ liệu | Ticket | Giai đoạn | Ngày tạo hồ sơ | Ngày cập nhật hồ sơ | Kết quả mong đợi hiển thị | Dùng cho |
|---|---|---|---|---|---|---|
| ND-1 | `DEMO-DWELL-01` | Spec Pack | 2026-08-19 09:00:00 | 2026-08-19 12:20:00 | `03:20:00` | BB-01, BB-02, BB-03 |
| ND-1 | `DEMO-DWELL-01` | Implementation Plan | 2026-08-19 13:00:00 | 2026-08-19 15:00:00 | `02:00:00` | BB-01, BB-12 |
| ND-1 | `DEMO-DWELL-01` | Review Checklist | 2026-08-19 15:30:00 | 2026-08-19 16:00:00 | `00:30:00` | BB-01 |
| ND-1 | `DEMO-DWELL-01` | Self Review | 2026-08-20 09:00:00 | 2026-08-20 10:00:00 | `01:00:00` | BB-01 |
| ND-1 | `DEMO-DWELL-01` | Test Plan | 2026-08-20 10:30:00 | 2026-08-20 11:00:00 | `00:30:00` | BB-01 |
| ND-1 | `DEMO-DWELL-01` | Test Results | 2026-08-20 14:00:00 | 2026-08-20 15:00:00 | `01:00:00` | BB-01 |
| ND-1 | `DEMO-DWELL-01` | Report | 2026-08-20 16:00:00 | 2026-08-20 16:45:00 | `00:45:00` | BB-01 |
| ND-2 | `DEMO-DWELL-02` | Test Plan — hồ sơ A | 2026-08-19 09:00:00 | 2026-08-19 10:00:00 | (đóng góp 1 giờ) | BB-04 |
| ND-2 | `DEMO-DWELL-02` | Test Plan — hồ sơ B | 2026-08-19 10:00:00 | 2026-08-19 12:30:00 | (đóng góp 2 giờ 30 phút) | BB-04 |
| ND-2 | `DEMO-DWELL-02` | → Tổng hiển thị | | | `03:30:00` | BB-04 |
| ND-9 | `DEMO-DWELL-11` | Test Results | 2026-08-19 09:00:00 | 2026-08-19 16:02:05 | `07:02:05` | BB-23 |

## Dữ liệu abnormal

| Bộ dữ liệu | Ticket | Giai đoạn | Tình huống | Ngày tạo | Ngày cập nhật | Kết quả mong đợi hiển thị | Dùng cho |
|---|---|---|---|---|---|---|---|
| AD-1 (= ND-5) | `DEMO-DWELL-05` | Test Results | Chưa từng có hồ sơ nào ghi nhận | (không có) | (không có) | `-` | BB-07 |
| AD-2 (= ND-6) | `DEMO-DWELL-06` | Review Checklist | Có 1 hồ sơ, chưa hoàn tất | 2026-08-19 09:00:00 | (chưa có) | `-` | BB-08 |
| AD-3 (= ND-7) | `DEMO-DWELL-07` | Implementation Plan — hồ sơ A | Đủ cặp | 2026-08-19 09:00:00 | 2026-08-19 10:00:00 | (đóng góp 1 giờ) | BB-09 |
| AD-3 (= ND-7) | `DEMO-DWELL-07` | Implementation Plan — hồ sơ B | Thiếu ngày cập nhật | 2026-08-19 11:00:00 | (chưa có) | (không đóng góp) | BB-09 |
| AD-3 | `DEMO-DWELL-07` | → Tổng hiển thị | | | | `01:00:00` (chỉ tính hồ sơ A) | BB-09 |
| AD-4 (= ND-8) | `DEMO-DWELL-09` | Spec Pack | **Ngày cập nhật sớm hơn ngày tạo** (dữ liệu nhập sai) | 2026-08-20 09:00:00 | 2026-08-19 09:00:00 | `-` (không hiển thị số âm) | BB-17 |
| AD-7 (= ND-10) | `DEMO-DWELL-12` | Review Checklist — hồ sơ A | Đủ cặp, hợp lệ | 2026-08-19 09:00:00 | 2026-08-19 12:00:00 | (đóng góp 3 giờ) | BB-25 |
| AD-7 (= ND-10) | `DEMO-DWELL-12` | Review Checklist — hồ sơ B | **Ngày cập nhật sớm hơn ngày tạo** (dữ liệu nhập sai, cùng phase với hồ sơ A hợp lệ) | 2026-08-20 09:00:00 | 2026-08-19 09:00:00 | (bị loại khỏi tổng, không "ăn bớt" số âm) | BB-25 |
| AD-7 | `DEMO-DWELL-12` | → Tổng hiển thị | | | | `03:00:00` (chỉ tính hồ sơ A, không phải `-` và không bị hồ sơ B làm sai lệch) | BB-25 |
| AD-5 | `DEMO-DWELL-10` | (giai đoạn bất kỳ) | Lỗi kết nối tới kho mã nguồn trong lúc quét | — | — | `-` cho tới khi quét lại thành công | BB-18 |
| AD-6 | (ticket bất kỳ) | (toàn bộ) | Backend gặp sự cố/quá thời gian chờ khi tải dữ liệu Phase Dwell Time | — | — | `-` cho các giai đoạn bị ảnh hưởng; màn hình vẫn mở bình thường | BB-16 |

## Dữ liệu boundary value

| Bộ dữ liệu | Ticket | Giai đoạn | Ngày tạo | Ngày cập nhật | Khoảng cách thời gian | Kết quả mong đợi hiển thị | Dùng cho |
|---|---|---|---|---|---|---|---|
| BD-1 (= ND-3) | `DEMO-DWELL-03` | Self Review | 2026-08-18 08:00:00 | 2026-08-19 11:20:05 | 27 giờ 20 phút 5 giây (vượt 1 ngày) | `27:20:05` | BB-05 |
| BD-2 (= ND-4) | `DEMO-DWELL-04` | Report | 2026-08-20 10:00:00 | 2026-08-20 10:00:00 | 0 giây (2 mốc bằng nhau) | `00:00:00` (khác với `-`) | BB-06 |
| BD-3 | `DEMO-DWELL-11` | Test Results | 2026-08-19 09:00:00 | 2026-08-19 16:02:05 | 7 giờ 2 phút 5 giây (số có 1 chữ số ở phút/giây) | `07:02:05` (luôn đủ 2 chữ số, không phải `7:2:5`) | BB-23 |
| BD-4 | `LEGACY-DWELL-01` | Cả 7 giai đoạn | Ticket cũ, chưa quét lại lần nào | (không áp dụng) | (không áp dụng) | Cả 7 dòng đều `-` | BB-20 |
| BD-5 | `DEMO-DWELL-01` | (mở lại nhiều lần liên tiếp) | Không áp dụng ngày giờ — kiểm tra thao tác lặp nhanh | — | — | Dữ liệu hiển thị ổn định, không đổi giữa các lần mở | BB-19 |

## Dữ liệu theo permission

| Loại tài khoản test | Vai trò (role) | Được xem PM Dashboard / Ticket Detail? | Dùng cho |
|---|---|---|---|
| `qa_manager_01` | Manager | Có | BB-21 |
| `qa_supportlead_01` | Support Lead | Có | BB-21 |
| `qa_opadmin_01` | Operation Admin | Có | BB-21 |
| `qa_supportagent_01` | Support Agent | Có | BB-21 |
| `qa_noaccess_01` | Vai trò không có quyền xem PM Dashboard (vd chỉ được cấp quyền xem module khác) | Không | BB-22 |

> Ghi chú: tính năng "Phase Dwell Time" **không có quyền riêng** — dùng chung đúng quyền hiện có
> của màn hình Ticket Detail/PM Dashboard. Không cần tạo thêm vai trò mới nào để test tính năng này.

## Cleanup

- Toàn bộ ticket test (`DEMO-DWELL-01` đến `DEMO-DWELL-11`, `LEGACY-DWELL-01`) chỉ tạo trong
  **môi trường test/staging riêng**, không tạo trên môi trường có dữ liệu khách hàng thật.
- Sau khi hoàn tất toàn bộ test case ở `blackbox-testcases.md`:
  1. Xoá hoặc gắn cờ "test data — do not use" cho toàn bộ ticket test kể trên để tránh nhầm lẫn với
     dữ liệu thật khi tra cứu/báo cáo.
  2. Xoá các tài khoản test tạo riêng cho mục Permission (`qa_manager_01`, `qa_noaccess_01`, ...)
     nếu không phải tài khoản dùng chung lâu dài của đội QA.
  3. Nếu đã yêu cầu đội kỹ thuật mô phỏng lỗi kết nối/sự cố backend (BB-16, BB-18), xác nhận với
     đội kỹ thuật đã khôi phục lại trạng thái bình thường, không để cấu hình mô phỏng lỗi tồn tại
     lâu dài trên môi trường test.
  4. Không lưu lại dữ liệu cá nhân hoặc thông tin nhạy cảm nào trong ticket test — toàn bộ nội
     dung hồ sơ dùng để test (spec-pack.md, impl-plan.md, ...) chỉ chứa nội dung giả lập tối thiểu
     đủ để có ngày tạo/ngày cập nhật hợp lệ, không copy nội dung thật từ ticket khách hàng.
