# Kế hoạch triển khai — {{TICKET}} ({{CHANGE_TITLE}})

- **Ticket:** {TICKET_ID}
- **Trạng thái:** Draft
- **Tạo ngày:** {DATE_YYYY_MM_DD} {TIME_HH_MM}
- **Cập nhật ngày:** {DATE_YYYY_MM_DD} {TIME_HH_MM}

> Nguồn tham chiếu chính: `{SPEC_FILE}`.

---

## 0. Nguyên tắc sử dụng template

Template này dùng cho Phase 3: **Implementation Plan + Impact Analysis**.

Khi điền tài liệu này:

1. Chỉ lập kế hoạch dựa trên nội dung trong `spec-pack.md`, đặc biệt là AC / NFR / Open Issues.
2. Không tự thêm scope, behavior, API, DB field, UI flow hoặc rule nghiệp vụ nếu không có trong spec.
3. Nếu thiếu thông tin để chốt implementation, ghi vào **Open Issues / Cần xác nhận trước khi implementation**.
4. Trước khi đọc code, phải liệt kê rõ **existing code cần đọc** ở mục 3.
5. Mỗi implementation step phải đủ nhỏ để review được trong một PR nhỏ hoặc một commit logic.
6. Group implementation steps theo feature tương ứng với nhóm AC trong `spec-pack.md`.
7. Cuối tài liệu bắt buộc có **AC mapping table**: mỗi AC được đáp ứng ở đâu và verify thế nào.

---

## 1. Implementation policy

### 1.1. Tóm tắt policy

<!--
Viết 1–3 đoạn ngắn mô tả chiến lược triển khai chính.

Gợi ý nội dung:
- Đây là build mới, mở rộng behavior cũ, refactor có kiểm soát, hay chỉ wiring/config?
- Phần nào FE / BE / DB / batch / permission / logging?
- Phần nào phải giữ nguyên behavior hiện tại?
- Phần nào không được làm vì spec không yêu cầu?
-->

Ticket {{TICKET}} sẽ được triển khai theo hướng **{{IMPLEMENTATION_POLICY_SUMMARY}}**.

Nguyên tắc tổng quát:

1. **Bám sát spec-pack**: chỉ triển khai các behavior có AC/NFR/source trong `docs/changes/{{TICKET}}/spec-pack.md`.
2. **Không mở rộng ngoài đặc tả**: mọi điểm chưa rõ hoặc có nhiều cách hiểu được chuyển sang Open Issue.
3. **Giới hạn blast radius**: ưu tiên thay đổi tại module liên quan trực tiếp; tránh refactor framework hoặc shared module nếu không bắt buộc.
4. **Compatibility**: giữ nguyên behavior hiện tại cho các flow không được ticket chạm tới.
5. **Reviewability**: chia step nhỏ, mỗi step có file thay đổi và cách verify riêng.

### 1.2. Non-goals / ngoài phạm vi

<!--
Liệt kê rõ những việc KHÔNG làm trong ticket này.
Mỗi item nên có lý do: không nằm trong spec, chờ ticket khác, hoặc cần PO xác nhận.
-->

| #   | Nội dung không làm | Lý do / căn cứ |
| --- | ------------------ | -------------- |
| 1   | {{NON_GOAL_1}}     | {{REASON_1}}   |
| 2   | {{NON_GOAL_2}}     | {{REASON_2}}   |

### 1.3. Phương án đã so sánh

<!--
Nếu có nhiều phương án implementation, so sánh ở đây.
Nếu chỉ có một phương án hợp lý, vẫn ghi rõ "Không có phương án thay thế đáng kể" và lý do.
-->

| Vấn đề               | Phương án A  | Phương án B  | Phương án chọn      | Lý do chọn    |
| -------------------- | ------------ | ------------ | ------------------- | ------------- |
| {{DECISION_TOPIC_1}} | {{OPTION_A}} | {{OPTION_B}} | {{SELECTED_OPTION}} | {{RATIONALE}} |
| {{DECISION_TOPIC_2}} | {{OPTION_A}} | {{OPTION_B}} | {{SELECTED_OPTION}} | {{RATIONALE}} |

### 1.4. Ràng buộc từ spec-pack

<!--
Tóm tắt các ràng buộc quan trọng từ spec-pack.
Không copy toàn bộ spec; chỉ nêu các constraint làm thay đổi implementation.
-->

| #   | Ràng buộc             | Ảnh hưởng tới implementation |
| --- | --------------------- | ---------------------------- |
| 1   | {{SPEC_CONSTRAINT_1}} | {{IMPLEMENTATION_IMPACT_1}}  |
| 2   | {{SPEC_CONSTRAINT_2}} | {{IMPLEMENTATION_IMPACT_2}}  |

---

## 2. Impact analysis

### 2.1. Files / modules có thể bị ảnh hưởng

<!--
Liệt kê file/module dự kiến bị ảnh hưởng.
Nếu chưa chắc file path, ghi module/area và đánh dấu "cần xác nhận sau khi đọc code".
-->

| #   | File / module        | Loại thay đổi                 | Lý do ảnh hưởng | AC liên quan | Ghi chú  |
| --- | -------------------- | ----------------------------- | --------------- | ------------ | -------- |
| 1   | `{{PATH_OR_MODULE}}` | Sửa / Thêm / Xóa / Không chắc | {{WHY}}         | {{AC_IDS}}   | {{NOTE}} |
| 2   | `{{PATH_OR_MODULE}}` | Sửa / Thêm / Xóa / Không chắc | {{WHY}}         | {{AC_IDS}}   | {{NOTE}} |

### 2.2. API contract có thể bị ảnh hưởng

<!--
Chỉ ghi API nếu spec yêu cầu hoặc implementation bắt buộc.
Nếu API chưa có contract, ghi Open Issue.
-->

| #   | API / endpoint / service   | Loại thay đổi                    | Request impact     | Response impact     | Error handling | AC liên quan | Trạng thái             |
| --- | -------------------------- | -------------------------------- | ------------------ | ------------------- | -------------- | ------------ | ---------------------- |
| 1   | `{{API_NAME_OR_ENDPOINT}}` | New / Update / Deprecated / None | {{REQUEST_CHANGE}} | {{RESPONSE_CHANGE}} | {{ERRORS}}     | {{AC_IDS}}   | Confirmed / Open Issue |
| 2   | `{{API_NAME_OR_ENDPOINT}}` | New / Update / Deprecated / None | {{REQUEST_CHANGE}} | {{RESPONSE_CHANGE}} | {{ERRORS}}     | {{AC_IDS}}   | Confirmed / Open Issue |

### 2.3. DB / migration có thể bị ảnh hưởng

<!--
Nếu không có DB impact, ghi rõ "Không có DB impact theo spec-pack".
Nếu có nhưng thiếu tên bảng/cột, ghi Open Issue.
-->

| #   | Bảng / collection / migration | Loại thay đổi             | Field / index / constraint | Data migration     | Rollback DB     | AC liên quan | Trạng thái             |
| --- | ----------------------------- | ------------------------- | -------------------------- | ------------------ | --------------- | ------------ | ---------------------- |
| 1   | `{{TABLE_OR_COLLECTION}}`     | Add / Alter / Drop / None | {{FIELD_CHANGE}}           | {{DATA_MIGRATION}} | {{DB_ROLLBACK}} | {{AC_IDS}}   | Confirmed / Open Issue |

### 2.4. Settings / config / feature flags

| #   | Key / config     | Loại thay đổi       | Default           | Environment impact | AC liên quan | Ghi chú  |
| --- | ---------------- | ------------------- | ----------------- | ------------------ | ------------ | -------- |
| 1   | `{{CONFIG_KEY}}` | Add / Update / None | {{DEFAULT_VALUE}} | {{ENV_IMPACT}}     | {{AC_IDS}}   | {{NOTE}} |

### 2.5. Logs / audit / monitoring

| #   | Event / log / metric | Khi nào ghi | Payload chính | Privacy / permission concern | AC/NFR liên quan  | Ghi chú  |
| --- | -------------------- | ----------- | ------------- | ---------------------------- | ----------------- | -------- |
| 1   | `{{EVENT_NAME}}`     | {{WHEN}}    | {{PAYLOAD}}   | {{CONCERN}}                  | {{AC_OR_NFR_IDS}} | {{NOTE}} |

### 2.6. Permissions / roles

| #   | Role / permission        | Behavior được phép | Behavior bị chặn | UI impact     | API/BE enforcement | AC liên quan |
| --- | ------------------------ | ------------------ | ---------------- | ------------- | ------------------ | ------------ |
| 1   | `{{ROLE_OR_PERMISSION}}` | {{ALLOWED}}        | {{DENIED}}       | {{UI_IMPACT}} | {{BE_ENFORCEMENT}} | {{AC_IDS}}   |

### 2.7. Backward compatibility / data compatibility

| #   | Compatibility point | Rủi ro   | Cách xử lý     | AC/NFR liên quan  |
| --- | ------------------- | -------- | -------------- | ----------------- |
| 1   | {{COMPAT_POINT}}    | {{RISK}} | {{MITIGATION}} | {{AC_OR_NFR_IDS}} |

---

## 3. Existing code cần đọc trước

> Quy tắc: **liệt kê trước, rồi mới đọc**. Không đọc bừa ngoài danh sách này trừ khi có phát hiện mới và phải cập nhật thêm vào danh sách.

### 3.1. Danh sách code cần đọc

| #   | File / module cần đọc | Mục đích đọc     | Câu hỏi cần trả lời trước khi sửa | Liên quan AC |
| --- | --------------------- | ---------------- | --------------------------------- | ------------ |
| 1   | `{{PATH_OR_MODULE}}`  | {{READ_PURPOSE}} | {{QUESTION_TO_ANSWER}}            | {{AC_IDS}}   |
| 2   | `{{PATH_OR_MODULE}}`  | {{READ_PURPOSE}} | {{QUESTION_TO_ANSWER}}            | {{AC_IDS}}   |

### 3.2. Thứ tự đọc đề xuất

<!--
Đặt thứ tự đọc để giảm đọc lan man.
Ví dụ: entry point → state/store → API/service → component detail → tests.
-->

1. `{{ENTRY_POINT_FILE}}` — xác định flow chính và boundary.
2. `{{STATE_OR_SERVICE_FILE}}` — xác định data model/state/API hiện tại.
3. `{{UI_OR_DOMAIN_FILE}}` — xác định behavior cần thay đổi.
4. `{{TEST_FILE}}` — xác định test hiện có và regression risk.

### 3.3. Điều kiện để mở rộng phạm vi đọc

Chỉ thêm file/module vào danh sách đọc nếu thỏa ít nhất một điều kiện:

- File được import trực tiếp bởi file đã đọc.
- File chứa type/API/constant được dùng bởi AC liên quan.
- Test hiện có fail hoặc mô tả behavior liên quan.
- Spec yêu cầu kiểm tra permission/log/config/DB ở area đó.

Khi thêm, cập nhật bảng 3.1 trước khi tiếp tục.

---

## 4. Changes / thiết kế thay đổi

<!--
Mô tả thay đổi ở mức thiết kế, không đi quá sâu vào code.
Nên group theo feature hoặc AC group trong spec-pack.

Cấu trúc khuyến nghị (combo overview table + diff block + state diagram):
  - 4.0: bảng tổng quan 1 hàng / nhóm AC để reviewer scan nhanh toàn section.
  - 4.x: mỗi sub-section dùng diff block ```diff để thể hiện "trước → sau" của state shape
    hoặc behavior, kèm bullet "Design notes" ngắn.
  - Nếu có state machine / lifecycle rõ ràng → thêm Mermaid `stateDiagram-v2`.

Quy tắc dùng diff block:
  - Dòng `-` = code/behavior hiện tại sẽ bị thay/bỏ.
  - Dòng `+` = code/behavior mới.
  - Comment trong block (`+ //`) dùng cho rule/policy không phải shape thuần.
  - Giữ diff ngắn, không paste cả file; chỉ field/behavior thay đổi.

Bỏ các H4 lặp `Current behavior / Target behavior / Data-state / API-DB` —
thông tin đó đã nằm trong bảng 4.0 và diff block.
-->

### 4.0. Tổng quan thay đổi

<!--
Mỗi hàng = một nhóm AC = một sub-section 4.x bên dưới.
Cột "State mới / chỉnh" liệt kê tên field/state, không cần shape đầy đủ (shape ở diff block).
Cột "API/DB" ghi `none` nếu không đổi; nếu có thì ghi endpoint/key ngắn gọn.
-->

| #   | Nhóm AC        | Hiện trạng          | Thay đổi FE        | State mới / chỉnh | API/DB             |
| --- | -------------- | ------------------- | ------------------ | ----------------- | ------------------ |
| 4.1 | {{AC_GROUP_1}} | {{CURRENT_SUMMARY}} | {{TARGET_SUMMARY}} | {{STATE_FIELDS}}  | {{API_DB_OR_NONE}} |
| 4.2 | {{AC_GROUP_2}} | {{CURRENT_SUMMARY}} | {{TARGET_SUMMARY}} | {{STATE_FIELDS}}  | {{API_DB_OR_NONE}} |

---

### 4.1. {{FEATURE_GROUP_1_NAME}} — {{RELATED_AC_GROUP}}

**State shape / behavior diff:**

```diff
- {{CURRENT_STATE_OR_BEHAVIOR}}
+ {{NEW_STATE_OR_BEHAVIOR}}
+ // rule / policy comment nếu cần (vd: reset sau search, gate theo permission)
```

<!--
Tùy nhóm AC, thêm Mermaid state diagram nếu có lifecycle / state machine rõ ràng.
Ví dụ panel cycle, tab lifecycle, mode transition. Bỏ block này nếu không áp dụng.
-->

**{{STATE_MACHINE_TITLE_OR_REMOVE}}:**

```mermaid
stateDiagram-v2
    [*] --> {{INITIAL_STATE}}
    {{STATE_A}} --> {{STATE_B}}: {{TRIGGER}}
    {{STATE_B}} --> {{STATE_A}}: {{TRIGGER}}
```

**Design notes:**

- {{DESIGN_NOTE_1}}
- {{DESIGN_NOTE_2}}

---

### 4.2. {{FEATURE_GROUP_2_NAME}} — {{RELATED_AC_GROUP}}

**Diff hành vi:**

```diff
- {{CURRENT_BEHAVIOR}}
+ {{NEW_BEHAVIOR}}
```

**Design notes:**

- {{DESIGN_NOTE_1}}
- {{DESIGN_NOTE_2}}

---

## 5. Implementation steps

> Mỗi step phải nhỏ tới mức review được. Group theo feature tương ứng với nhóm AC trong `spec-pack.md`.

### Part A — {{FEATURE_GROUP_A_NAME}} ({{AC_GROUP_A_IDS}})

| #   | Step nhỏ, review được | Files dự kiến chỉnh | Output mong đợi     | Cách xác minh     | AC đáp ứng |
| --- | --------------------- | ------------------- | ------------------- | ----------------- | ---------- |
| A.1 | {{STEP_DESCRIPTION}}  | `{{FILE_PATH}}`     | {{EXPECTED_OUTPUT}} | {{VERIFY_METHOD}} | {{AC_IDS}} |
| A.2 | {{STEP_DESCRIPTION}}  | `{{FILE_PATH}}`     | {{EXPECTED_OUTPUT}} | {{VERIFY_METHOD}} | {{AC_IDS}} |

### Part B — {{FEATURE_GROUP_B_NAME}} ({{AC_GROUP_B_IDS}})

| #   | Step nhỏ, review được | Files dự kiến chỉnh | Output mong đợi     | Cách xác minh     | AC đáp ứng |
| --- | --------------------- | ------------------- | ------------------- | ----------------- | ---------- |
| B.1 | {{STEP_DESCRIPTION}}  | `{{FILE_PATH}}`     | {{EXPECTED_OUTPUT}} | {{VERIFY_METHOD}} | {{AC_IDS}} |
| B.2 | {{STEP_DESCRIPTION}}  | `{{FILE_PATH}}`     | {{EXPECTED_OUTPUT}} | {{VERIFY_METHOD}} | {{AC_IDS}} |

### Part C — {{FEATURE_GROUP_C_NAME}} ({{AC_GROUP_C_IDS}})

| #   | Step nhỏ, review được | Files dự kiến chỉnh | Output mong đợi     | Cách xác minh     | AC đáp ứng |
| --- | --------------------- | ------------------- | ------------------- | ----------------- | ---------- |
| C.1 | {{STEP_DESCRIPTION}}  | `{{FILE_PATH}}`     | {{EXPECTED_OUTPUT}} | {{VERIFY_METHOD}} | {{AC_IDS}} |
| C.2 | {{STEP_DESCRIPTION}}  | `{{FILE_PATH}}`     | {{EXPECTED_OUTPUT}} | {{VERIFY_METHOD}} | {{AC_IDS}} |

---

## 6. Risks & mitigation

| #   | Rủi ro     | Tác động            | Khả năng            | Biện pháp giảm thiểu | Cách phát hiện sớm | Owner     |
| --- | ---------- | ------------------- | ------------------- | -------------------- | ------------------ | --------- |
| 1   | {{RISK_1}} | High / Medium / Low | High / Medium / Low | {{MITIGATION_1}}     | {{EARLY_SIGNAL_1}} | {{OWNER}} |
| 2   | {{RISK_2}} | High / Medium / Low | High / Medium / Low | {{MITIGATION_2}}     | {{EARLY_SIGNAL_2}} | {{OWNER}} |

---

## 7. Rollback plan

### 7.1. Code rollback

1. Revert theo PR/step độc lập, ưu tiên thứ tự ngược lại với implementation: 3 → 2 → 1.
2. Nếu có feature flag, tắt flag trước khi revert code để giảm user impact.
3. Clear cache/store/session nếu thay đổi state shape hoặc persisted data.

### 7.2. DB rollback

<!--
Nếu không có DB impact, ghi "Không áp dụng".
Nếu có DB impact, mô tả migration down và data preservation.
-->

| #   | DB change     | Rollback action     | Data loss risk     | Owner     |
| --- | ------------- | ------------------- | ------------------ | --------- |
| 1   | {{DB_CHANGE}} | {{ROLLBACK_ACTION}} | {{DATA_LOSS_RISK}} | {{OWNER}} |

### 7.3. Config / feature flag rollback

| #   | Config / flag    | Rollback value       | Khi nào dùng    | Ghi chú  |
| --- | ---------------- | -------------------- | --------------- | -------- |
| 1   | `{{CONFIG_KEY}}` | `{{ROLLBACK_VALUE}}` | {{WHEN_TO_USE}} | {{NOTE}} |

---

## 8. Verification procedure

### 8.1. Automated verification

```bash
{{LINT_COMMAND}}
{{TYPECHECK_COMMAND}}
{{UNIT_TEST_COMMAND}}
{{BUILD_COMMAND}}
{{E2E_TEST_COMMAND}}
```

### 8.2. Manual verification matrix

| #   | Scenario          | Preconditions     | Steps            | Expected result     | AC/NFR     |
| --- | ----------------- | ----------------- | ---------------- | ------------------- | ---------- |
| 1   | {{SCENARIO_NAME}} | {{PRECONDITIONS}} | {{MANUAL_STEPS}} | {{EXPECTED_RESULT}} | {{AC_IDS}} |
| 2   | {{SCENARIO_NAME}} | {{PRECONDITIONS}} | {{MANUAL_STEPS}} | {{EXPECTED_RESULT}} | {{AC_IDS}} |

### 8.3. Regression checks

| #   | Existing behavior cần giữ | Check            | Expected result     |
| --- | ------------------------- | ---------------- | ------------------- |
| 1   | {{EXISTING_BEHAVIOR}}     | {{CHECK_METHOD}} | {{EXPECTED_RESULT}} |

---

## 9. Open Issues / Cần xác nhận trước khi implementation

> Chỉ dùng mục này cho thông tin còn thiếu hoặc cần quyết định. Không được tự quyết nếu spec-pack chưa đủ căn cứ.

| ID        | Chủ đề    | Câu hỏi cần chốt | Ai chốt   | Block bước   | Mức độ block            | Trạng thái |
| --------- | --------- | ---------------- | --------- | ------------ | ----------------------- | ---------- |
| IMPL-OI-1 | {{TOPIC}} | {{QUESTION}}     | {{OWNER}} | {{STEP_IDS}} | Blocking / Non-blocking | Open       |
| IMPL-OI-2 | {{TOPIC}} | {{QUESTION}}     | {{OWNER}} | {{STEP_IDS}} | Blocking / Non-blocking | Open       |

### Checklist xác nhận trước khi bắt đầu implementation

- [ ] Tất cả AC trong `spec-pack.md` đã được đưa vào bảng mapping mục 10.
- [ ] Không có implementation step nào nằm ngoài spec-pack.
- [ ] Existing code cần đọc đã được liệt kê trước ở mục 3.
- [ ] Các Open Issue blocking đã có owner và bước bị block rõ ràng.
- [ ] API contract đã chốt nếu có API impact.
- [ ] DB migration / rollback đã chốt nếu có DB impact.
- [ ] Permission / role behavior đã chốt nếu có phân quyền.
- [ ] Log / audit / monitoring đã chốt nếu spec hoặc NFR yêu cầu.
- [ ] Test plan đủ cover AC chính và regression quan trọng.

---

## 10. AC mapping table

> Bắt buộc: mỗi AC trong `spec-pack.md` phải có ít nhất một dòng mapping. Nếu AC chưa thể đáp ứng vì thiếu thông tin, map tới Open Issue tương ứng.

| #   | AC ID       | Nội dung AC tóm tắt | Implementation step đáp ứng | Files/modules liên quan | Verification      | Open Issue nếu có  |
| --- | ----------- | ------------------- | --------------------------- | ----------------------- | ----------------- | ------------------ |
| 1   | {{AC_ID_1}} | {{AC_SUMMARY_1}}    | {{STEP_IDS}}                | `{{FILES_OR_MODULES}}`  | {{VERIFY_METHOD}} | {{OI_ID_OR_EMPTY}} |
| 2   | {{AC_ID_2}} | {{AC_SUMMARY_2}}    | {{STEP_IDS}}                | `{{FILES_OR_MODULES}}`  | {{VERIFY_METHOD}} | {{OI_ID_OR_EMPTY}} |

---

## 11. Output

Sau khi hoàn tất Phase 3, output bắt buộc:

- `docs/changes/{{TICKET}}/impl-plan.md`
- Checklist các điều cần xác nhận trước khi implementation bắt đầu, nằm trong mục 9
