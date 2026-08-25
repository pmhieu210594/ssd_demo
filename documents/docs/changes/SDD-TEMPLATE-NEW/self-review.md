# Tự review — [CHANGE_ID] ([CHANGE_TITLE])

- **Ticket:** {TICKET_ID}
- **Trạng thái:** Draft
- **Tạo ngày:** {DATE_YYYY_MM_DD} {TIME_HH_MM}
- **Cập nhật ngày:** {DATE_YYYY_MM_DD} {TIME_HH_MM}

> Nguồn tham chiếu chính: `{SPEC_FILE}`, `{IMPL_FILE}` và `{REVIEW_CHECKLIST_FILE}`.
> Được developer/agent điền sau implementation, trước khi review thủ công.
> Mọi checkbox đều phải có bằng chứng: kết quả lệnh, hoặc tham chiếu tệp + số dòng.

<!--
Cách dùng:
1. Copy template này thành `docs/changes/[CHANGE_ID]/self-review.md`.
2. Thay toàn bộ placeholder dạng [LIKE_THIS].
3. Thêm/xóa nhóm AC theo spec thực tế.
4. Copy danh sách RC từ `review-checklist.md`; không tự ý bỏ RC Blocker/Major.
5. Với mọi mục ✅/🔶, bắt buộc có bằng chứng file:line hoặc output lệnh/test.
-->

---

## 1. Trạng thái hoàn thành AC

> ✅ = đạt | ❌ = chưa đạt | 🔶 = một phần (kèm ghi chú lý do)

### 1.1. [AC_GROUP_1] — spec [SPEC_SECTION]

| #   | AC           | Trạng thái   | Bằng chứng (file:line, hoặc kết quả test) |
| --- | ------------ | ------------ | ----------------------------------------- |
| 1   | [AC-id-1/v1] | ✅ / ❌ / 🔶 |                                           |
| 2   | [AC-id-2/v1] | ✅ / ❌ / 🔶 |                                           |
| 3   | [AC-id-3/v1] | ✅ / ❌ / 🔶 |                                           |

### 1.2. [AC_GROUP_2] — spec [SPEC_SECTION]

| #   | AC           | Trạng thái   | Bằng chứng |
| --- | ------------ | ------------ | ---------- |
| 1   | [AC-id-1/v1] | ✅ / ❌ / 🔶 |            |
| 2   | [AC-id-2/v1] | ✅ / ❌ / 🔶 |            |
| 3   | [AC-id-3/v1] | ✅ / ❌ / 🔶 |            |

### 1.3. [AC_GROUP_3] — spec [SPEC_SECTION]

| #   | AC           | Trạng thái   | Bằng chứng |
| --- | ------------ | ------------ | ---------- |
| 1   | [AC-id-1/v1] | ✅ / ❌ / 🔶 |            |
| 2   | [AC-id-2/v1] | ✅ / ❌ / 🔶 |            |
| 3   | [AC-id-3/v1] | ✅ / ❌ / 🔶 |            |

### 1.4. [AC_GROUP_4] — spec [SPEC_SECTION]

| #   | AC           | Trạng thái   | Bằng chứng |
| --- | ------------ | ------------ | ---------- |
| 1   | [AC-id-1/v1] | ✅ / ❌ / 🔶 |            |
| 2   | [AC-id-2/v1] | ✅ / ❌ / 🔶 |            |
| 3   | [AC-id-3/v1] | ✅ / ❌ / 🔶 |            |

---

## 2. Các hạng mục checklist (từ `review-checklist.md`)

<!--
Copy RC từ review-checklist thực tế. Bảng dưới đây giả định review-checklist có RC-01..RC-43.
Thêm/xóa dòng để khớp checklist thật.
-->

| RC#   | Trạng thái | Bằng chứng / Ghi chú |
| ----- | ---------- | -------------------- |
| RC-01 | [ ]        |                      |
| RC-02 | [ ]        |                      |
| RC-03 | [ ]        |                      |
| RC-04 | [ ]        |                      |
| RC-05 | [ ]        |                      |
| RC-06 | [ ]        |                      |
| RC-07 | [ ]        |                      |
| RC-08 | [ ]        |                      |
| RC-09 | [ ]        |                      |
| RC-10 | [ ]        |                      |
| RC-11 | [ ]        |                      |
| RC-12 | [ ]        |                      |
| RC-13 | [ ]        |                      |
| RC-14 | [ ]        |                      |
| RC-15 | [ ]        |                      |
| RC-16 | [ ]        |                      |
| RC-17 | [ ]        |                      |
| RC-18 | [ ]        |                      |
| RC-19 | [ ]        |                      |
| RC-20 | [ ]        |                      |
| RC-21 | [ ]        |                      |
| RC-22 | [ ]        |                      |
| RC-23 | [ ]        |                      |
| RC-24 | [ ]        |                      |
| RC-25 | [ ]        |                      |
| RC-26 | [ ]        |                      |
| RC-27 | [ ]        |                      |
| RC-28 | [ ]        |                      |
| RC-29 | [ ]        |                      |
| RC-30 | [ ]        |                      |
| RC-31 | [ ]        |                      |
| RC-32 | [ ]        |                      |
| RC-33 | [ ]        |                      |
| RC-34 | [ ]        |                      |
| RC-35 | [ ]        |                      |
| RC-36 | [ ]        |                      |
| RC-37 | [ ]        |                      |
| RC-38 | [ ]        |                      |
| RC-39 | [ ]        |                      |
| RC-40 | [ ]        |                      |
| RC-41 | [ ]        |                      |
| RC-42 | [ ]        |                      |
| RC-43 | [ ]        |                      |

---

## 3. Các lệnh đã chạy

### 3.1. Lint

```bash
# Lệnh:
[COMMAND_LINT]

# Kết quả:
[PASTE_OUTPUT_OR_SUMMARY]
```

### 3.2. Type-check

```bash
# Lệnh:
[COMMAND_TYPE_CHECK]

# Kết quả:
[PASTE_OUTPUT_OR_SUMMARY]
```

### 3.3. Unit test

```bash
# Lệnh:
[COMMAND_UNIT_TEST]

# Kết quả:
[PASTE_OUTPUT_OR_SUMMARY: số test pass/fail, coverage nếu có]
```

### 3.4. Build

```bash
# Lệnh:
[COMMAND_BUILD]

# Kết quả:
[PASTE_OUTPUT_OR_SUMMARY, warning/error nếu có]
```

### 3.5. Manual / E2E (nếu có)

| #   | Scenario                      | Mode        | Kết quả      | Ghi chú |
| --- | ----------------------------- | ----------- | ------------ | ------- |
| 1   | [MAIN_FLOW_SCENARIO]          | [USER_MODE] | ✅ / ❌ / 🔶 |         |
| 2   | [EDGE_CASE_OR_GUARD_SCENARIO] | [USER_MODE] | ✅ / ❌ / 🔶 |         |
| 3   | [PERMISSION_OR_ROLE_SCENARIO] | [USER_MODE] | ✅ / ❌ / 🔶 |         |
| 4   | [ERROR_HANDLING_SCENARIO]     | [USER_MODE] | ✅ / ❌ / 🔶 |         |
| 5   | [VISUAL_OR_UX_SCENARIO]       | [USER_MODE] | ✅ / ❌ / 🔶 |         |

---

## 4. Tổng quan diff

<!--
Điền sau khi implementation:
- Số file thay đổi / số dòng thêm / số dòng xóa.
- Liệt kê các file/module có thay đổi lớn nhất, kèm 1 dòng mô tả vai trò.
- Các file mới được tạo.
- Migration đã được tạo: tên file, mô tả schema thay đổi.
-->

| Loại      | File / module       | Vai trò chính trong PR     |
| --------- | ------------------- | -------------------------- |
| Sửa lớn   | [path/to/file]      | [Mô tả]                    |
| Thêm mới  | [path/to/file]      | [Mô tả]                    |
| Sửa nhỏ   | [path/to/file]      | [Mô tả]                    |
| Xóa       | [path/to/file]      | [Mô tả]                    |
| Migration | [path/to/migration] | [Mô tả schema/data change] |

---

## 5. Rủi ro đã biết / Chưa bao phủ / Công việc còn lại

### 5.1. Known risks

| #   | Rủi ro             | Mức độ ảnh hưởng  | Cách giảm thiểu / Theo dõi |
| --- | ------------------ | ----------------- | -------------------------- |
| 1   | [RISK_DESCRIPTION] | [Low/Medium/High] | [MITIGATION]               |

### 5.2. Not handled yet

| #   | Hạng mục | Lý do chưa bao phủ | Hành động được đề xuất |
| --- | -------- | ------------------ | ---------------------- |
| 1   | [ITEM]   | [REASON]           | [ACTION]               |

### 5.3. Remaining issues / nợ kỹ thuật

| #   | Issue   | Liên quan AC / RC | Đề xuất xử lý (ticket follow-up, ...) |
| --- | ------- | ----------------- | ------------------------------------- |
| 1   | [ISSUE] | [AC/RC]           | [ACTION]                              |

### 5.4. Open Issues từ impl-plan vẫn còn

> Liệt kê các IMPL-OI-\* ở `docs/changes/[CHANGE_ID]/impl-plan.md` mục `[IMPL_OPEN_ISSUE_SECTION]` vẫn chưa được đóng.

| #   | OI ID        | Mô tả ngắn    | Trạng thái                 |
| --- | ------------ | ------------- | -------------------------- |
| 1   | IMPL-OI-[NN] | [DESCRIPTION] | Open / Resolved / Deferred |

---

## 6. Confirmations cuối cùng

| #   | Confirmation                                                              | Trạng thái |
| --- | ------------------------------------------------------------------------- | ---------- |
| 1   | Không thêm scope ngoài spec-pack mục `[SPEC_SCOPE_SECTION]`               | [ ]        |
| 2   | Mọi Open Issue vẫn open đều được nêu ở mục 5.4                            | [ ]        |
| 3   | Mọi RC mức Blocker đã ✅ hoặc đã nêu lý do ở mục 5                        | [ ]        |
| 4   | Lint / type-check / build pass; nếu fail đã được giải trình ở mục 3       | [ ]        |
| 5   | Không có console log, debug code, commented-out code còn sót              | [ ]        |
| 6   | Không log PII / secret / raw business data vượt mức cần thiết             | [ ]        |
| 7   | Migration/schema/data change có rollback hoặc có giải trình nếu không cần | [ ]        |
