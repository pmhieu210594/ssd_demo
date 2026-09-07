# Black-box Review Checklist — BLACKBOX-COVERAGE

- **Ticket:** BLACKBOX-COVERAGE
- **Trạng thái:** Draft
- **Tạo ngày:** 2026-09-07 00:00
- **Cập nhật ngày:** 2026-09-07 00:00

> Checklist này nhắm vào các perspective black-box dễ bỏ sót, đối chiếu với `blackbox-testcases.md` và `test-data.md`. Không lặp lại nội dung đã có ở `review-checklist.md` (review implementation-level).

---

## 1. Boundaries

- [ ] Mẫu số = 0 cho từng thành phần riêng lẻ (AC, observation, case) đều được thử độc lập, không chỉ thử trường hợp tất cả bằng 0 cùng lúc. (`BBC-04`)
- [ ] Có thử trường hợp tử số = mẫu số (100%) và tử số = 0 nhưng mẫu số > 0 (0%).
- [ ] Có thử rounding ở giá trị thập phân dài (`81.818...` → `81.8`), không chỉ số tròn. (`BBC-14`)
- [ ] Có thử ranh giới giữa "có case map nhưng không PASS hết" và "có case map và PASS hết" cho `AC Coverage (Execution)`. (`BBC-01`, `BBC-05`)
- [ ] Có thử observation point chỉ có case `RELATED`, không có case `DIRECT` nào. (`BBC-08`)

## 2. Permissions

- [ ] Không có token → 401. (`BBC-09`)
- [ ] Có token nhưng sai role trên đúng project → 403. (`BBC-10`)
- [ ] Có role đúng nhưng ở project khác → 403.
- [ ] `ADMIN` bypass vẫn xem đúng dữ liệu, không rò rỉ hoặc sai lệch giá trị. (`BBC-11`)
- [ ] Không có test nào giả định chi tiết cách kiểm tra quyền nội bộ (chỉ kiểm HTTP status + body).

## 3. Compatibility

- [ ] Field `blackboxCoveragePercent` giữ nguyên tên, kiểu dữ liệu, vị trí so với trước khi đổi. (`BBC-12`)
- [ ] Không phát sinh field API mới riêng cho Design/Execution/Priority Coverage. (`BBC-12`)
- [ ] Field/API của AC-Test Coverage hiện hữu không đổi giá trị khi Blackbox Coverage được thêm vào. (`BBC-13`)
- [ ] Nhiều ticket trong cùng project không lẫn dữ liệu khi truy vấn tuần tự hoặc đổi filter. (`BBC-15`, `BBC-16`)
- [ ] Format lỗi (401/403) vẫn theo đúng `ErrorResponse` hiện có của hệ thống, không có status code ad-hoc.

## 4. Exceptions

- [ ] Ticket chưa có `blackbox-testcases.md` → trả `0.0`, không lỗi 500. (`BBC-02`)
- [ ] Ticket có design nhưng chưa từng chạy test → không lỗi, giá trị hợp lý theo trọng số Execution = 0. (`BBC-03`)
- [ ] Case có trạng thái không xác định trong nguồn thô (khác `SUCCESS/FAILED/SKIPPED`) không làm vỡ tính toán, được xử lý như `NOT_RUN`.
- [ ] `SKIP` và `NOT_RUN` đều không được tính là PASS, và được thử riêng biệt (không gộp chung một test). (`BBC-05`, `BBC-06`)
- [ ] Giá trị trả về không bao giờ là `NaN` hoặc `null` trong bất kỳ biến thể mẫu số = 0 nào.

## 5. Performance degradation

- [ ] Không có black-box case nào giả định số lần query cụ thể (đây là chi tiết implementation) — chỉ quan sát: nhiều ticket/nhiều lần gọi liên tiếp không làm chậm response tới mức đổi hành vi quan sát được (timeout, response rỗng).
- [ ] Nếu team cần bằng chứng runtime cho PERF-1/PERF-3 (`review-checklist.md` mục 4), việc đó nằm ngoài phạm vi tài liệu black-box này — ghi nhận là gap, không tự thêm case đo performance nội bộ ở đây.
- [ ] Trường hợp project có nhiều ticket (`BBC-16`) chỉ dùng để xác nhận tính đúng đắn dữ liệu, không dùng để đo performance.

## 6. Ghi chú giới hạn phạm vi

- Checklist này không thay thế `review-checklist.md` (implementation-level) hay `test-plan.md` (ma trận AC × test type).
- Checklist này chỉ xác nhận các case trong `blackbox-testcases.md` đã phủ đủ góc nhìn dễ bỏ sót; không tự ý mở rộng thêm case mới nếu không có mục nào ở trên bị thiếu bằng chứng.
