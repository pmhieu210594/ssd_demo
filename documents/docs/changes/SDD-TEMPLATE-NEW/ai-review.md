# AI Review — Ticket ⬜<số ticket> (⬜<tên feature ngắn>)

- **Ticket:** {TICKET_ID}
- **Trạng thái:** Draft
- **Tạo ngày:** {DATE_YYYY_MM_DD} {TIME_HH_MM}
- **Cập nhật ngày:** {DATE_YYYY_MM_DD} {TIME_HH_MM}
- **Diff review:** `⬜<sha-base>...⬜<sha-head>`
- **Branch:** `⬜<tên branch>` (⬜đã/chưa merge `develop`)
- **Nguồn đối chiếu:** [⬜docs/changes/<ticket>/spec-pack.md](⬜docs/changes/<ticket>/spec-pack.md)
- **Reviewer:** AI review — _người review cần điền tên và xác nhận lại_
- **Cách kiểm chứng:** ⬜<ví dụ: đọc code tĩnh + `git show` + `grep` + kết quả UT hiện có. Chưa chạy E2E, chưa chạy app.>
- **Phạm vi:** chỉ ghi kết quả review, không sửa source code.

> **Cách dùng template này:** thay mọi chỗ ⬜ bằng nội dung thật, xoá các dòng ví dụ/ghi chú trong ngoặc `<...>`. Nhân bản block `### F<n>` cho mỗi finding, block `### TC <n>` cho mỗi test case đề xuất. Giữ nguyên §9 (quy ước) để người đọc hiểu severity/loại/ký hiệu. Ô ⬜ còn lại sau khi viết xong = phần cố ý để trống cho người review điền.

> **Ghi chú quan trọng về input:** ⬜<Chỉ giữ block này nếu có input dự kiến bị thiếu. Ghi rõ file nào **không tồn tại** trong repo (kèm danh sách file thực tế đã kiểm tra trong `docs/changes/<ticket>/`), review này thực tế dựa trên nguồn nào, và ghi nhận đây là gap về evidence/process cần bổ sung (dẫn tới [§5](#5-độ-phủ-review)). Nếu đủ input, xoá cả block này.>

## Mục lục

1. [Kết luận](#1-kết-luận) — [Next action](#next-action)
2. [Tóm tắt diff](#2-tóm-tắt-diff)
3. [Tổng hợp findings](#3-tổng-hợp-findings)
4. [Chi tiết findings](#4-chi-tiết-findings) — [F1](#f1) · [F2](#f2) · ⬜<thêm anchor cho từng finding>
5. [Độ phủ review](#5-độ-phủ-review)
6. [Traceability — AC chưa đạt](#6-traceability--ac-chưa-đạt)
7. [Đề xuất test cases bổ sung](#7-đề-xuất-test-cases-bổ-sung)
8. [Số liệu thống kê](#8-số-liệu-thống-kê)
9. [Phụ lục — Quy ước](#9-phụ-lục--quy-ước)

## 1. Kết luận

**Verdict: ⬜Request changes / Approve with comments / Approve.**

⬜<n> Blocker · ⬜<n> Major · ⬜<n> Minor. (Định nghĩa severity: xem [§9 Phụ lục](#9-phụ-lục--quy-ước).)

Điều kiện để pass:

1. ⬜<Finding Blocker> phải được fix và có test kèm theo.
2. ⬜<Finding Major> fix hoặc có quyết định chính thức từ BA/nghiệp vụ nếu chấp nhận lệch spec.
3. ⬜<Điều kiện khác — ví dụ mâu thuẫn nội tại giữa test và code thật cần được xử lý>.
4. ⬜<AC id> trong `self-review.md` hiện để trống — cần được verify thật, không chỉ tick.

<a id="next-action"></a>

### Next action

| Việc                                                                                                   | Nội dung                                                                                                                                                                           | Người nhận  | Deadline    |
| ------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------- | ----------- |
| Fix [F1](#f1), [F2](#f2) + test kèm theo (điều kiện merge)                                             | ⬜<mô tả ngắn. Nếu một finding đã fix trong pass sau, ghi rõ "**đã fix trong pass F<n> (⬜<ngày>), chờ review con người**" và link tới block cập nhật, phần còn lại ghi rõ còn mở> | ⬜ cần điền | ⬜ cần điền |
| Quyết định BA cho [F3](#f3)                                                                            | ⬜<mô tả ngắn>                                                                                                                                                                     | ⬜ cần điền | ⬜ cần điền |
| ⬜<Tạo các file evidence còn thiếu — nếu block "Ghi chú quan trọng về input" ở trên có ghi file thiếu> | ⬜<bổ sung theo `.claude/rules/50-sdd-docs.md`>                                                                                                                                    | ⬜ cần điền | ⬜ cần điền |
| Điền phần còn trống ở [§5 Độ phủ review](#5-độ-phủ-review)                                             | ⬜<n> ô ⬜ — các vùng chưa đối chiếu với spec                                                                                                                                      | ⬜ cần điền | ⬜ cần điền |

## 2. Tóm tắt diff

⬜<3-5 bullet, mỗi bullet một vùng code/module chính bị thay đổi. Nêu tên file/thư mục dạng path và mục đích thay đổi, không copy diff.>

- **⬜<module>** (`⬜<path>`) được ⬜<mở rộng/thêm mới/refactor> để ⬜<mục đích>.
- **⬜<component mới>** mới (⬜<n> dòng) cho phép ⬜<hành vi>.
- **Store `⬜<tên store>`** được bổ sung ⬜<state> để ⬜<mục đích>.
- **Commit `⬜<sha>` "⬜<message>"** ⬜<mô tả thay đổi đáng chú ý nhất, đặc biệt nếu là gỡ bỏ hành vi cũ mà không thay thế> — ⬜<vì sao đáng chú ý trong review này>.

## 3. Tổng hợp findings

Cột **Trạng thái** dùng đúng một trong: `Chưa xử lý` · `Đã fix (chờ review)` (kèm link tới block cập nhật, ví dụ [Cập nhật xử lý F1](#f1-fix)) · `Đã xử lý` (người review đã xác nhận) · `Không fix` (có quyết định chính thức chấp nhận rủi ro).

| #   | Severity  | Loại              | Tóm tắt                                      | AC liên quan | Người duyệt | Trạng thái                                               |
| --- | --------- | ----------------- | -------------------------------------------- | ------------ | ----------- | -------------------------------------------------------- |
| F1  | ⬜Blocker | ⬜Correctness     | [⬜<một câu, link tới anchor chi tiết>](#f1) | ⬜<AC id>    |             | ⬜Đã fix (chờ review) — xem [Cập nhật xử lý F1](#f1-fix) |
| F2  | ⬜Major   | ⬜Regression risk | [⬜<một câu>](#f2)                           | ⬜<AC id>    |             | ⬜Chưa xử lý                                             |
| F3  | ⬜Minor   | ⬜Missing tests   | [⬜<một câu>](#f3)                           | ⬜<AC id>    |             | ⬜Không fix                                              |

## 4. Chi tiết findings

<a id="f1"></a>

### F1 — ⬜<tiêu đề ngắn, nêu đúng vấn đề>

**Severity:** ⬜Blocker · **Loại:** ⬜Correctness · **AC:** ⬜<AC id>

⬜<Mô tả vấn đề: bối cảnh → code thực tế → hệ quả. Trích đoạn code ngắn nhất đủ để thấy vấn đề.>

```ts
⬜<đoạn code liên quan>
```

⬜<Giải thích vì sao đoạn code trên lệch spec/sai. Nếu là hệ quả nhiều bước, liệt kê bullet.>

- ⬜<hệ quả 1 — mô tả theo góc nhìn người dùng/dữ liệu>
- ⬜<hệ quả 2>

**Evidence:**

- [⬜file.ts:⬜<dòng>](⬜path#L⬜) — ⬜<đoạn code/hàm nào, đóng vai trò gì>.
- [⬜spec-pack.md:⬜<dòng>](⬜path#L⬜) — ⬜<yêu cầu spec tương ứng>.
- ⬜<Nếu code đã bị xoá: `[file](path) — <hành động> tại commit \`<sha>\` (code đã xoá: không có range dòng, tra bằng `git show <sha>`)`.>

**Đề xuất fix:** ⬜<hướng fix cụ thể, nêu hàm/file cần đổi và AC được đáp ứng sau khi fix. Không viết patch, chỉ nêu hướng.>

<a id="f1-fix"></a>

#### Cập nhật xử lý F1 (⬜<ngày YYYY-MM-DD của pass fix>)

⬜<Block này chỉ thêm khi finding đã được fix trong một pass sau. Giữ nguyên phần mô tả finding gốc ở trên — không sửa lại nó — để đọc được lịch sử. Nếu chưa fix, xoá cả block này. Nhân bản với anchor `f<n>-fix` cho từng finding được fix.>

**Trạng thái:** ⬜Đã fix trong code, chờ dev/reviewer con người xác nhận lại (⬜<nêu rõ chưa merge / chưa chạy BB-E2E thật>).

**Đã thay đổi:**

- [⬜file.ts](⬜path) — ⬜<thay đổi gì, và **vì sao chỗ này là điểm chặn đúng** so với đề xuất fix ban đầu>.
- [⬜file khác](⬜path) — ⬜<thay đổi gì, nêu hành vi cũ → hành vi mới>.
- Test mới: [⬜test.ts](⬜path) — ⬜<test xác nhận điều gì>.

**Ảnh hưởng:**

- ⬜<các đường consume khác được dọn theo fix này mà không cần sửa riêng, hoặc ngược lại>.
- ⬜<hành vi có/không đổi ở case hợp lệ, kèm cách đọc AC tương ứng>.
- ⬜<khẳng định không mở rộng scope sang các finding khác>.

**Commands/evidence:**

- `⬜<lệnh lint phạm vi hẹp>` — ⬜<kết quả>.
- `⬜<lệnh type-check>` — ⬜<kết quả, phân biệt lỗi pre-existing>.
- `⬜<lệnh test>` — ⬜<số test file / số test pass>.
- `⬜<gate chưa chạy>` — **chưa chạy** trong pass này (⬜<lý do>).

**Remaining issues (liên quan F1):**

- ⬜<phần BB/manual chưa thực hiện>.
- ⬜<case trong TC chưa được cover bởi fix này, kèm lý do kỹ thuật và câu hỏi cần BA xác nhận>.
- Người review con người cần đọc lại diff trên và xác nhận verdict trước khi đổi cột "Trạng thái" ở [§3](#3-tổng-hợp-findings) từ "Đã fix (chờ review)" sang "Đã xử lý".

**Next action:** ⬜<ai làm gì tiếp, và cần cập nhật mục nào của tài liệu này>.

---

<a id="f2"></a>

### F2 — ⬜<tiêu đề ngắn>

**Severity:** ⬜Major · **Loại:** ⬜Regression risk · **AC:** ⬜<AC id>

⬜<mô tả>

**Evidence:**

- ⬜<...>

**Đề xuất fix:** ⬜<...>

---

⬜<Nhân bản block trên cho F3, F4, ... Giữ thứ tự Blocker → Major → Minor.>

## 5. Độ phủ review

Mục này ghi lại **những gì đã được xem**, để phân biệt "đã kiểm tra và không thấy lệch" với "chưa ai xem". Pass review này đi theo diff, nên chỉ phủ các AC mà diff có chạm tới. Ô đánh ⬜ là phần chưa hoàn tất, cần người review điền.

| Vùng code                                                                                       | Cách kiểm chứng                               | Kết quả                                                                                                             |
| ----------------------------------------------------------------------------------------------- | --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| ⬜<hàm/file đã xem> ([⬜file.ts](⬜path))                                                       | ⬜<đọc code tĩnh / chạy UT / grep / git show> | ⬜<kết luận + link finding nếu có ([F1](#f1))>                                                                      |
| ⬜<vùng code khác>                                                                              | ⬜<cách kiểm chứng>                           | ⬜<kết luận>                                                                                                        |
| ⬜<vùng chưa verify>                                                                            | —                                             | ⬜ chưa verify trong pass này — cần ⬜<E2E / BA confirm>                                                            |
| Toàn bộ AC còn lại trong bảng traceability `spec-pack.md`                                       | —                                             | ⬜ chưa đối chiếu; cần lấy danh sách đầy đủ từ [spec-pack.md](⬜path) và điền vào bảng này                          |
| ⬜<các gate app — ví dụ `npm run lint` / `npm run build` / `npm run test` / `npm run test:e2e`> | ⬜<— nếu chưa chạy, hoặc lệnh đã chạy>        | ⬜<kết quả, hoặc "chưa chạy trong pass này — cần chạy các gate theo `.claude/rules/40-testing.md` trước khi merge"> |

## 6. Traceability — AC chưa đạt

Bảng này là [§3 Tổng hợp findings](#3-tổng-hợp-findings) đọc theo chiều AC, **không phải danh sách finding mới** — mọi dòng ở đây đều trỏ về một finding đã nêu ở §4.

| AC        | Dòng trong spec        | Verdict                                                                                            | Finding     |
| --------- | ---------------------- | -------------------------------------------------------------------------------------------------- | ----------- |
| ⬜<AC id> | [⬜<dòng>](⬜path#L⬜) | ⬜Chưa đạt / Chưa đạt đầy đủ (⬜<phần nào đạt>) / Có nguy cơ chưa đạt ở case biên / Cần verify lại | [⬜F1](#f1) |

## 7. Đề xuất test cases bổ sung

| Test case       | Lớp test | Liên quan      | Ưu tiên                                   |
| --------------- | -------- | -------------- | ----------------------------------------- |
| [⬜TC 1](#tc-1) | ⬜UT     | ⬜F1 (Blocker) | **Chặn merge**                            |
| [⬜TC 2](#tc-2) | ⬜E2E/BB | ⬜F2 (Major)   | ⬜Bắt buộc nếu fix F2 / Nên có / Theo dõi |

<a id="tc-1"></a>

### TC 1 — ⬜<lớp test>: ⬜<đối tượng test>

\*Liên quan [⬜F1](#f1) · ⬜<AC id> · **⬜Chặn merge\***

- ⬜<input cụ thể> → kỳ vọng ⬜<output cụ thể>. Hiện tại (bug) ra ⬜<output thực tế>.
- ⬜<case biên> → kỳ vọng ⬜<...>.

<a id="tc-2"></a>

### TC 2 — ⬜<lớp test>: ⬜<đối tượng test>

_Liên quan [⬜F2](#f2) · ⬜<AC id> · ⬜<ưu tiên>_

- ⬜<mô tả scenario theo bước: dựng dữ liệu → hành động → kỳ vọng>. Hiện tại (bug) ⬜<hành vi thực tế>.

## 8. Số liệu thống kê

> Số liệu dưới đây phản ánh ⬜<phạm vi: pass review này, hoặc pass review này + ⬜<n> vòng fix (⬜<ngày>)>. Các tỷ lệ yêu cầu dữ liệu về xử lý sau merge (false-positive / human-accepted / hữu ích) vẫn cần người review điền.

| Số liệu                                                         | Giá trị hiện tại                                | Ghi chú                                                                                                                |
| --------------------------------------------------------------- | ----------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| Tổng số finding                                                 | ⬜<n> (⬜<n> Blocker, ⬜<n> Major, ⬜<n> Minor) | Xem [§3](#3-tổng-hợp-findings)                                                                                         |
| Tổng số finding đã fix                                          | ⬜<n> / ⬜<n> (⬜<F id>)                        | ⬜<finding nào đã fix, đang chờ xác nhận verdict; finding nào chưa>                                                    |
| Tỷ lệ xử lý finding nghiêm trọng (Blocker)                      | ⬜<n> / ⬜<n> (⬜<%>)                           | ⬜<Blocker nào còn mở và còn chặn merge theo [§1](#1-kết-luận)>                                                        |
| Tỷ lệ AI finding được con người chấp nhận                       | ⬜ chưa có dữ liệu                              | Cần người review điền sau khi xác nhận từng finding (đồng ý / không đồng ý / cần điều chỉnh)                           |
| Tỷ lệ AI review finding hữu ích                                 | ⬜ chưa có dữ liệu                              | Cần đo sau khi dev xác nhận mức độ hữu ích của từng finding                                                            |
| Tỷ lệ AI finding bị đánh giá là false positive                  | ⬜ chưa có dữ liệu                              | ⬜<nêu mức evidence hiện có: mọi finding đều có file/line xác minh trên HEAD; tỷ lệ thực tế cần người review xác nhận> |
| Tỷ lệ AI finding đã được xử lý (fix hoặc quyết định chính thức) | ⬜<n> / ⬜<n> (⬜<%>)                           | Cập nhật cột "Trạng thái" ở [§3](#3-tổng-hợp-findings) khi có tiến triển thêm                                          |

## 9. Phụ lục — Quy ước

- **Severity** (theo quy ước nội bộ — điều chỉnh nếu team đã có định nghĩa khác):
  - **Blocker** — lệch spec/sai dữ liệu, không được merge cho tới khi fix.
  - **Major** — lệch spec hoặc thiếu bảo vệ ở mức cần fix hoặc cần quyết định chính thức từ BA.
  - **Minor** — cần sửa nhưng không chặn merge.
- **Loại** (enum cố định, mỗi finding chọn đúng một giá trị):
  - **Correctness** — code chạy ra kết quả sai so với spec.
  - **Robustness** — code đúng ở happy path nhưng vỡ ở case biên / dựa vào giả định không được bảo đảm.
  - **Regression risk** — thay đổi gỡ bỏ một bảo vệ đang có, chưa quan sát được lỗi trực tiếp.
  - **Missing tests** — thiếu hoặc sai lớp kiểm thử mà bảng truy vết yêu cầu.
- **Số dòng trong `Evidence`:** range không có tiền tố là chính xác tại thời điểm review (HEAD `⬜<sha-head>`); range có tiền tố `≈` là xấp xỉ, cần xác nhận lại khi apply fix.
- **Evidence dạng commit:** ghi `[file](path) — <hành động> tại commit \`<sha>\`` để vừa mở được file hiện tại, vừa tra lại được commit gốc.
- **Evidence cho code đã bị xoá:** không có range số dòng — đây là chủ đích (dòng không còn tồn tại trong file hiện tại), không phải thiếu sót; tra bằng `git show <sha>` / `git diff`.
- **Ký hiệu ⬜:** phần chưa hoàn tất, cần người review điền hoặc bổ sung — dùng để scan nhanh các chỗ còn trống.
