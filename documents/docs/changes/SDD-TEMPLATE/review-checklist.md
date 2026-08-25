# Danh sách kiểm tra review — [CHANGE_ID] ([CHANGE_TITLE])

- **Ticket:** {TICKET_ID}
- **Trạng thái:** Draft
- **Tạo ngày:** {DATE_YYYY_MM_DD} {TIME_HH_MM}
- **Cập nhật ngày:** {DATE_YYYY_MM_DD} {TIME_HH_MM}

> Nguồn tham chiếu chính: `{SPEC_FILE}` và `{IMPL_FILE}`.
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
| RC-01 | Tất cả AC ở `[SPEC_AC_SECTION]` của spec-pack đã được triển khai và có bằng chứng                                                | Blocker | [ ]        |
| RC-02 | Không thêm hành vi nào nằm ngoài phạm vi spec-pack mục `[SPEC_SCOPE_SECTION]`                                                    | Blocker | [ ]        |
| RC-03 | Các Open Issue ở impl-plan mục `[IMPL_OPEN_ISSUE_SECTION]` vẫn được liệt kê; không mục nào được implement khi chưa có quyết định | Blocker | [ ]        |
| RC-04 | Flow/model chính của change tuân thủ đúng định hướng spec; không tạo flow hoặc behavior thay thế ngoài spec                      | Blocker | [ ]        |

## 2. Thiết kế / Phụ thuộc

| #     | Hạng mục                                                                                                                       | Mức độ  | Trạng thái |
| ----- | ------------------------------------------------------------------------------------------------------------------------------ | ------- | ---------- |
| RC-05 | State/model chính được đặt ở đúng layer/module; không mutate rải rác trong UI/component                                        | Major   | [ ]        |
| RC-06 | Logic persist/hydration/cache được gom ở repository/service riêng; reducer/domain không phụ thuộc trực tiếp storage/API cụ thể | Major   | [ ]        |
| RC-07 | Không phát sinh phụ thuộc vòng tròn giữa store/UI/service/domain/infrastructure                                                | Major   | [ ]        |
| RC-08 | Contract API/request/response thay đổi khớp với phần đã chốt trong impl-plan và spec                                           | Blocker | [ ]        |
| RC-09 | Lifecycle của entity/feature chính được tách rõ; không trộn logic giữa các scope khác nhau                                     | Major   | [ ]        |
| RC-10 | Rendering/branching theo template/type/mode được tách rõ, dễ kiểm thử và không hard-code lan rộng                              | Major   | [ ]        |

## 3. Bảo mật

| #     | Hạng mục                                                                                                     | Mức độ  | Trạng thái |
| ----- | ------------------------------------------------------------------------------------------------------------ | ------- | ---------- |
| RC-11 | Dữ liệu input từ user được kiểm tra/làm sạch trước khi gọi API hoặc persist                                  | Blocker | [ ]        |
| RC-12 | Phân quyền/role/mode được enforce ở cả UI và BE/API; user không có quyền không thể sửa dữ liệu ngoài phạm vi | Blocker | [ ]        |
| RC-13 | Không log secret/PII/raw business data vượt quá quy chuẩn audit/logging                                      | Blocker | [ ]        |
| RC-14 | Không có rủi ro XSS/injection qua các field user nhập; escape/sanitize khi render                            | Blocker | [ ]        |
| RC-15 | Dữ liệu lưu local/cache/session không chứa thông tin nhạy cảm vượt phạm vi cần thiết                         | Major   | [ ]        |

## 4. Hiệu năng

| #     | Hạng mục                                                                                            | Mức độ | Trạng thái |
| ----- | --------------------------------------------------------------------------------------------------- | ------ | ---------- |
| RC-16 | Các thao tác UI không liên quan đến fetch không trigger query/refetch/recompute nặng ngoài ý muốn   | Major  | [ ]        |
| RC-17 | Các limit/guard quan trọng được áp dụng sớm ở FE/client khi phù hợp, không chờ BE phản hồi mới chặn | Major  | [ ]        |
| RC-18 | Không gây re-render toàn bộ shell/page khi chỉ đổi state cục bộ; dùng selector/memoization phù hợp  | Major  | [ ]        |
| RC-19 | Read/write storage, network, hoặc xử lý nặng chạy async đúng cách và không block main thread        | Major  | [ ]        |

## 5. Tương thích

| #     | Hạng mục                                                                        | Mức độ  | Trạng thái |
| ----- | ------------------------------------------------------------------------------- | ------- | ---------- |
| RC-20 | Dữ liệu/config cũ vẫn hoạt động; mapper/parser xử lý backward-compatible        | Blocker | [ ]        |
| RC-21 | Migration DB/schema có rollback/down migration tương ứng                        | Major   | [ ]        |
| RC-22 | Các template/flow/module ngoài phạm vi change không bị thay behavior            | Blocker | [ ]        |
| RC-23 | Rule default/priority/resolution sau upgrade được xác định rõ và hoạt động đúng | Major   | [ ]        |

## 6. Logging / Audit

| #     | Hạng mục                                                                                                 | Mức độ  | Trạng thái |
| ----- | -------------------------------------------------------------------------------------------------------- | ------- | ---------- |
| RC-24 | Các thao tác tạo/sửa/xóa/default/publish/reset hoặc mutation tương đương có audit theo user và thời điểm | Major   | [ ]        |
| RC-25 | Log/audit không chứa PII, secret, hoặc raw value vượt mức cần thiết                                      | Blocker | [ ]        |
| RC-26 | Diagnostic/user-facing error dùng cơ chế hiện hữu; không thêm log/noise ngoài phạm vi spec               | Minor   | [ ]        |

## 7. Xử lý lỗi

| #     | Hạng mục                                                                                              | Mức độ  | Trạng thái |
| ----- | ----------------------------------------------------------------------------------------------------- | ------- | ---------- |
| RC-27 | Khi chạm limit/guard, UI hiển thị lỗi rõ ràng và không làm thay đổi state hiện tại                    | Major   | [ ]        |
| RC-28 | Thao tác overwrite/destructive có warning/confirm phù hợp; Cancel giữ nguyên dữ liệu cũ               | Blocker | [ ]        |
| RC-29 | Thao tác delete/reset có confirm dialog trước khi thực hiện                                           | Major   | [ ]        |
| RC-30 | Trùng tên/collision/conflict được báo lỗi rõ ràng; không tự rename hoặc ghi đè nếu spec không yêu cầu | Blocker | [ ]        |
| RC-31 | Save/overwrite/delete/publish/reset hoặc mutation thất bại không làm thay đổi state cũ                | Blocker | [ ]        |
| RC-32 | Client không nhận stack trace nội bộ; lỗi user-facing dùng wording đã chốt                            | Major   | [ ]        |

## 8. Kiểm thử

| #     | Hạng mục                                                                                            | Mức độ | Trạng thái |
| ----- | --------------------------------------------------------------------------------------------------- | ------ | ---------- |
| RC-33 | Unit test bao phủ guard/limit/rule quan trọng nhất của change                                       | Major  | [ ]        |
| RC-34 | Unit test bao phủ resolver/default/priority/business rule chính                                     | Major  | [ ]        |
| RC-35 | Unit test bao phủ state được dùng tại thời điểm submit/apply/save                                   | Major  | [ ]        |
| RC-36 | Unit test bao phủ naming/suffix/duplicate/conflict rule nếu có                                      | Major  | [ ]        |
| RC-37 | Manual/E2E bao phủ main user flow, update current state, navigation/switching, và integration chính | Major  | [ ]        |
| RC-38 | Manual/E2E bao phủ role/mode/admin/permission lifecycle nếu có                                      | Major  | [ ]        |
| RC-39 | Visual/UX check bao phủ layout, label, empty/loading/error state, truncation/ellipsis nếu có        | Major  | [ ]        |

## 9. Vận hành

| #     | Hạng mục                                                                                  | Mức độ | Trạng thái |
| ----- | ----------------------------------------------------------------------------------------- | ------ | ---------- |
| RC-40 | Quy trình rollback migration/schema/data change được tài liệu hóa                         | Major  | [ ]        |
| RC-41 | Quy trình clear cache/local storage/debug data cho user/support khi cần được tài liệu hóa | Minor  | [ ]        |
| RC-42 | Constant/config quan trọng đặt ở vị trí dễ tìm; không scatter magic number                | Minor  | [ ]        |
| RC-43 | Không thêm feature flag/config switch nếu spec không yêu cầu                              | Minor  | [ ]        |

---

## Bảng ánh xạ AC → Checklist items

<!--
Cách dùng:
- Mỗi AC trong spec phải xuất hiện ít nhất một lần.
- Một RC có thể map tới nhiều AC.
- Nếu AC không có checklist item tương ứng, thêm RC mới.
-->

| #   | AC                                 | Các hạng mục checklist xác nhận |
| --- | ---------------------------------- | ------------------------------- |
| 1   | [AC-group-1/v1 hoặc AC-id-1..N/v1] | RC-01, RC-[NN]                  |
| 2   | [AC-group-2/v1 hoặc AC-id-1..N/v1] | RC-[NN], RC-[NN]                |
| 3   | [AC-group-3/v1 hoặc AC-id-1..N/v1] | RC-[NN]                         |
