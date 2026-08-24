# test-data

**Ticket ID**: AI-REVIEW-KPI-IMPROVEMENT
**Create date**: 2026-08-20
**Author**: QA Designer (Claude)
**Update date**: 2026-08-20

> Dữ liệu mẫu dùng chung cho các test case trong `blackbox-testcases.md`. Tên project/repository/
> ticket/tài khoản dưới đây là tên gợi ý cho môi trường test — thay bằng tên thật tương ứng khi thực
> thi, miễn giữ đúng đặc điểm mô tả (vai trò, phạm vi, trạng thái dữ liệu).

## Dữ liệu chung

- **Project test**: 1 project riêng cho việc test, ví dụ `QA-AIRKI-PROJECT`.
- **Repository test**: 1 repository thuộc project trên, đã cấu hình sẵn kết nối GitHub (webhook trỏ
  đúng về hệ thống), ví dụ `qa-org/airki-test-repo`.
- **Ticket test**: nhiều ticket con trong repository trên, mỗi ticket có sẵn thư mục
  `docs/changes/{TICKET_ID}/` để chứa file `ai-review.md`, ví dụ: `T1`, `T2`, `T3`, `T4`, `T5`, `T6`,
  `T7`, `T8`, `T9`, `T10`, `T11`.
- **Bảng mẫu chuẩn `ai-review.md` §8 (Normal — mẫu chuẩn)**, dùng làm nền cho các biến thể abnormal/
  boundary bên dưới (chỉ thay đổi đúng 1 dòng mỗi lần, giữ nguyên các dòng khác):

  | Chỉ số | Giá trị |
  |---|---|
  | Tổng số finding | 10 |
  | Số finding đã fix | 8/10 (80%) |
  | Blocker/Major Resolution Rate | 4/5 (80%) |
  | AI Review Adoption Rate | 9/10 (90%) |
  | AI Review Valid Finding Rate | 9/10 (90%) |
  | AI False Positive Rate | 1/10 (10%) |
  | AI Finding Resolution Rate | 8/10 (80%) |

- **Fixture thật**: lấy nguyên văn 1 file `ai-review.md` của một ticket đã hoàn thành trước đó trong
  hệ thống thật (không tự soạn tay) để dùng cho TC-AIRKI-04 — đảm bảo nhãn dòng đúng như cách một
  ticket thật từng viết (có thể khác chữ so với mẫu chuẩn ở trên), nhưng vẫn đủ 7 dòng đúng thứ tự
  mục §8.

## Dữ liệu normal

| Biến thể | Áp dụng cho | Nội dung khác mẫu chuẩn |
|---|---|---|
| Mẫu chuẩn (tiếng Việt) | TC-AIRKI-01 | Giữ nguyên bảng mẫu chuẩn ở trên |
| Heading tiếng Anh ("## 8. Statistics") | TC-AIRKI-02 | Chỉ đổi dòng heading mục 8, giữ nguyên bảng số liệu |
| Heading tiếng Nhật ("## 8. 統計データ") | TC-AIRKI-03 | Chỉ đổi dòng heading mục 8, giữ nguyên bảng số liệu |
| Fixture thật (nhãn diễn đạt khác) | TC-AIRKI-04 | Toàn bộ nội dung lấy từ ticket thật đã hoàn thành |
| Phiên bản 2 (số liệu mới hơn) | TC-AIRKI-08 | Đổi cả 5 chỉ số sang giá trị khác mẫu chuẩn, ví dụ Blocker 5/5 (100%), Adoption 5/10 (50%) |

## Dữ liệu abnormal

| Biến thể | Áp dụng cho | Nội dung khác mẫu chuẩn |
|---|---|---|
| Giá trị chữ "Không áp dụng" ở dòng Adoption | TC-AIRKI-05 | Dòng "AI Review Adoption Rate" = "Không áp dụng" |
| Số full-width ở dòng Blocker/Major | TC-AIRKI-06 | Dòng "Blocker/Major Resolution Rate" = "５／１０" (toàn bộ ký tự số/dấu gạch chéo là full-width Unicode) |
| Số trộn nửa/toàn chiều rộng ở dòng Adoption | TC-AIRKI-07 | Dòng "AI Review Adoption Rate" = "1２/15" (chữ số "2" viết dạng full-width "２", còn lại bình thường) |
| Giả lập lỗi ghi dữ liệu thống kê | TC-AIRKI-10 | Không phải nội dung file — là trạng thái môi trường test do đội kỹ thuật tạm thời chặn ghi dữ liệu thống kê, cần phối hợp chuẩn bị trước |
| File `ai-review.md` không truy cập được | TC-AIRKI-11 | Không phải nội dung file — là trạng thái: file bị xoá quyền đọc / xoá khỏi PR / token hết hạn, khiến hệ thống không lấy được nội dung |
| Request thiếu tham số bắt buộc | TC-AIRKI-20 | Gọi trực tiếp bằng công cụ test (ví dụ Postman) vào địa chỉ dữ liệu KPI, cố tình bỏ trống project hoặc repository |

## Dữ liệu boundary value

| Biến thể | Áp dụng cho | Mô tả |
|---|---|---|
| Repository chưa có ticket nào có dữ liệu | TC-AIRKI-13 | Project/repository test mới tạo, chưa từng merge PR có `ai-review.md` nào |
| Repository có dữ liệu lệch nhóm (chỉ Blocker có dữ liệu) | TC-AIRKI-14 | Tất cả ticket trong repository đều ghi "Không áp dụng" cho Adoption/Valid/False Positive/Resolution, chỉ Blocker/Major có số hợp lệ |
| Tỷ lệ tổng hợp không tròn số (1/3) | TC-AIRKI-15 | Tổng tử số/mẫu số của toàn repository cho ra đúng 1/3 (ví dụ tổng 10 ticket, 1 phần tử đạt / 3 phần tử tổng mẫu số quy đổi ra 1/3) — kỳ vọng hiển thị 33.3% |

## Dữ liệu theo permission

| Tài khoản test | Vai trò hệ thống | Vai trò PM theo project | Áp dụng cho |
|---|---|---|---|
| `user-no-pm` | Thành viên thường (không ADMIN) | Không có PM ở bất kỳ project nào | TC-AIRKI-16 |
| `user-admin` | ADMIN hệ thống | Không cần gán PM riêng | TC-AIRKI-17 |
| `user-pm-scoped` | Viewer (không ADMIN) | PM đúng project test (`QA-AIRKI-PROJECT`) | TC-AIRKI-18 |
| `user-pm-other-project` | Thành viên thường (không ADMIN) | PM ở 1 project khác, không phải project test | TC-AIRKI-19 |

## Cleanup

- Sau khi test xong, xoá các PR test đã tạo trên repository test (`qa-org/airki-test-repo`) hoặc để
  nguyên nếu repository này chỉ dùng riêng cho QA (không ảnh hưởng dữ liệu thật).
- Không cần thao tác xoá dữ liệu KPI thủ công cho ticket test: theo thiết kế, mỗi ticket chỉ giữ
  đúng 1 bộ số liệu hiện hành và số liệu này tự động được ghi đè ở lần merge PR kế tiếp — muốn "làm
  sạch" một ticket test, chỉ cần merge thêm 1 PR mới với `ai-review.md` rỗng/khác để ghi đè, hoặc đơn
  giản là không tái sử dụng ticket đó cho lượt test sau.
- Nếu môi trường test đã được giả lập lỗi ghi dữ liệu thống kê (TC-AIRKI-10) hoặc giả lập lỗi truy
  cập file (TC-AIRKI-11), phải khôi phục lại trạng thái bình thường của môi trường trước khi chạy
  các test case khác, tránh ảnh hưởng chéo giữa các test case.
- Xoá các tài khoản test được tạo riêng cho permission (`user-no-pm`, `user-admin`, `user-pm-scoped`,
  `user-pm-other-project`) nếu môi trường test không dùng lại cho lượt kiểm thử sau; nếu dùng lại,
  giữ nguyên để tránh phải cấu hình lại vai trò/phạm vi PM.
