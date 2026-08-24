# human-review

**Ticket ID**: PHASE-DWELL-TIME
**Nguồn tổng hợp**: `self-review.md` (Claude, vòng 3), `ai-review.md` (Claude independent review + STEP F1 fix, 2026-08-21), `codex-review.md` (template rỗng — xem ghi chú), `test-results.md` (vòng 2), `open-issues.md`, `review-checklist.md`, `blackbox-testcases.md`/`test-data.md` (cập nhật 2026-08-21).
**Ngày tổng hợp**: 2026-08-21
**Vai trò**: Review Coordinator (chỉ tổng hợp, không tự sửa code, không tự quyết định).

> **Ghi chú nguồn**: `codex-review.md` vẫn chỉ là template rỗng — không có review độc lập từ 1
> AI/tool thứ 2 nào khác ngoài Claude (self-review + independent review). Đây là gap quy trình đã
> ghi ở Failure Mode Candidates, không chặn merge.

---

## Executive Summary

Toàn bộ 5 finding của `ai-review.md` (F1-F5) đã được người dùng triage trong vòng này. Không còn
mục nào trong "Must Fix Before Merge" — điểm chặn merge duy nhất trước đó (F1) đã được xác nhận
đúng. Còn lại 1 action item đã duyệt nhưng **chưa implement** (F4 — batchUpdate), không chặn merge
vì là cải thiện hiệu năng, không phải lỗi chức năng. Các rủi ro Postgres/ArchUnit/flyway trước đó
vẫn còn nguyên, không đổi.

- **F1 (Major, đã xác nhận đúng)**: netting/ăn bớt số âm khi 1 phase có ≥2 file, 1 file lỗi ngày —
  đã fix (thêm điều kiện `>=` vào `ON` clause), người dùng đọc lại patch và xác nhận đúng.
- **F2 (Major, đã xác nhận PASS)**: `review-checklist.md` chưa điền thật — người dùng chấp nhận
  `self-review.md` làm nguồn duy nhất, đã ghi WAIVED trực tiếp trong `review-checklist.md`.
- **TC4 (đã bổ sung)**: thêm case black-box `BB-PHASE-DWELL-TIME-25` (1 phase, 2 hồ sơ: 1 hợp lệ +
  1 ngày bị đảo) vào `blackbox-testcases.md`, kèm bộ dữ liệu `AD-7`/`ND-10` trong `test-data.md`.
- **F3 (Minor, đã xác nhận bỏ qua)**: log `WARN` lặp lại khi đọc `/detail` — chấp nhận rủi ro.
- **F4 (Minor, đã xác nhận đề xuất sửa)**: `batchUpdate` thay vòng lặp `jdbc.update` — **đã duyệt,
  chưa implement**, còn là action item mở duy nhất.
- **F5 (Minor, đã xác nhận bỏ qua)**: log lỗi thiếu `ex.getMessage()` — chấp nhận rủi ro.

---

## Must Fix Before Merge

*(Trống — F1 là mục duy nhất từng nằm ở đây, đã được xác nhận đúng và chuyển sang trạng thái đã xử
lý, xem `ai-review.md §3`.)*

---

## Should Fix

| # | File | Căn cứ | Ảnh hưởng | Đề xuất sửa | Test |
|---|---|---|---|---|---|
| F4 | `EDCAP_BE/.../infrastructure/persistence/adapter/scanner/ArtifactScannerJdbcAdapter.java:1044-1071` | `ai-review.md` F4 — vòng lặp `jdbc.update` tuần tự (≤8 dòng/lần quét) thay vì `batchUpdate` | N+1 pattern, không cấp bách ở quy mô hiện tại nhưng là technical debt nếu `CHANGE_TARGET_FILES` tăng | **Đã duyệt đề xuất sửa (2026-08-21)** — đổi sang `NamedParameterJdbcTemplate.batchUpdate(...)`. **Chưa implement**, cần 1 lượt dev riêng | Chưa có — cần viết TC2 (`ai-review.md §7`) cùng lúc implement |

**→ Việc còn lại**: giao dev thực hiện fix F4 + viết test TC2, sau đó cập nhật `self-review.md`
(danh sách file thay đổi) và đổi trạng thái F4 trong `ai-review.md §3` từ "Đã duyệt đề xuất sửa"
sang "Đã xử lý".

---

## Can Follow Later

*(Trống — F3 và F5 đã được xác nhận bỏ qua, chuyển sang Accepted Risks bên dưới thay vì giữ ở đây
như 1 việc còn phải làm.)*

---

## False Positives

*(Không có finding nào bị xác nhận là false positive trong vòng triage này — cả 5 finding của
`ai-review.md` đều được công nhận là đúng, chỉ khác nhau ở quyết định fix/bỏ qua.)*

---

## Accepted Risks

| Risk | Impact | Owner | Deadline | Nguồn |
|---|---|---|---|---|
| F3 — WARN log cho tổng âm bị tính lại/log lại mỗi lần mở Ticket Detail (không chỉ lúc quét) | Có thể gây log noise nếu ticket lỗi dữ liệu bị xem nhiều lần; giảm nhẹ đáng kể sau khi F1 fix (nhánh này hiếm khi còn kích hoạt thật) | — | — | `ai-review.md` F3, xác nhận bỏ qua 2026-08-21 |
| F5 — Exception khi đọc/parse 1 file bị nuốt, log không có `ex.getMessage()` | Khó điều tra khi 1 phase luôn hiển thị `"-"` không rõ nguyên nhân (mạng vs parse) | — | — | `ai-review.md` F5, xác nhận bỏ qua 2026-08-21 |
| Chưa chạy integration test thật trên Postgres cho AC-2 (cộng dồn)/AC-6 (rescan) + điều kiện `>=` mới của F1 | Trung bình — rủi ro lệch cú pháp/ngữ nghĩa SQL Postgres cụ thể chỉ lộ ra khi chạy DB thật | Người review/QA trước merge/deploy | Trước khi deploy lên môi trường có DB | `self-review.md §8`, `test-results.md §8`, `ai-review.md §7 TC1` |
| ArchUnit `ArchitectureTest` không tồn tại trong repo để verify tự động hexagonal layering | Thấp — đã tự rà layer boundary bằng tay | Team (gap tiền nhiệm, không phát sinh từ ticket này) | Không áp dụng riêng ticket này | `self-review.md §8`, `test-results.md §8` |
| Data quality: `create_date`/`update_date` tự khai báo trong header, phụ thuộc kỷ luật cập nhật — ticket cũ có thể hiển thị `"-"` dù đã hoàn thành | Chấp nhận theo AC-4 (đã duyệt 2026-08-19/20) | — | — | `open-issues.md` "Accepted but Unresolved Items" |
| Race condition giữa 2 lần scan đồng thời chưa test được (cần DB thật + đa luồng) | Chưa đo được, ghi nhận từ trước | — | — | `test-results.md §9` |

---

## Open Questions

| # | Câu hỏi cần human quyết định | Liên quan | Options |
|---|---|---|---|
| Q1 | Có cần chờ chạy Postgres thật (integration test AC-2/AC-6 + điều kiện `>=` mới của F1) trước khi merge, hay chấp nhận merge với rủi ro đã ghi ở Accepted Risks và verify sau? | F1, AC-2, AC-6 | (a) Chặn merge chờ Postgres (b) Merge, verify sau, theo dõi owner/deadline |
| Q2 | Xác nhận thời điểm chạy `flyway migrate` trên môi trường chia sẻ (theo `00-safety.md §3`, cần hỏi người dùng trước) | Migration `V512` | Người dùng chỉ định thời điểm |
| Q3 | Ai nhận việc implement F4 (`batchUpdate`) và deadline là khi nào? | F4 | Người dùng/Tech Lead chỉ định owner + deadline |

*(Q1/Q2 của vòng trước về F1/F2 đã được trả lời trong vòng triage này — xem Executive Summary,
không còn là câu hỏi mở.)*

---

## Spec Updates Required

- `spec-pack.md §5` (định nghĩa Σ per-file) nên được làm rõ tường minh: **file có `update_date <
  create_date` bị coi là không hợp lệ và bị loại hoàn toàn khỏi phép cộng** (cùng nhánh với "thiếu
  update_date"), không phải "cộng số âm rồi kiểm tra dấu tổng". Hiện điều này chỉ được suy ra từ
  code fix F1 (đã xác nhận đúng) và Resolution Log `open-issues.md`, chưa có câu tường minh trong
  spec gốc.

---

## Test Updates Required

- ~~Thêm black-box test case mới cho case "1 phase, 2 hồ sơ, 1 hợp lệ + 1 ngày bị đảo"~~ — **Đã
  làm**: `BB-PHASE-DWELL-TIME-25` trong `blackbox-testcases.md` + bộ dữ liệu `AD-7`/`ND-10` trong
  `test-data.md`. Còn cần QA thao tác tay trên UI thật để verify (chưa chạy).
- Thêm Integration Test trên Postgres thật (khi có hạ tầng) cho: (a) AC-2 cộng dồn ≥2 file/1 phase,
  (b) AC-6 rescan giữ nguyên giá trị, (c) điều kiện `>=` mới của F1 chạy đúng ngữ nghĩa server-side.
- TC2 (chưa viết, cần làm cùng lúc implement F4): `upsertArtifactDocumentDates` dùng `batchUpdate`
  — assert gọi đúng 1 lần với N phần tử.
- ~~TC3 (F5)~~ — không cần viết, F5 đã được xác nhận bỏ qua.

---

## Failure Mode Candidates

*(Đề xuất rule hóa cho lần sau — không phải finding của ticket này, giữ nguyên từ vòng review trước)*

1. **AI review thứ 2 (Codex/tool khác) bị bỏ trống mà không ai phát hiện** — `codex-review.md` chỉ
   là template rỗng nhưng vẫn nằm trong bộ tài liệu ticket như thể đã hoàn thành. → Rule: trước khi
   coi ticket "đã qua đủ vòng AI review", kiểm tra mỗi file review có tối thiểu 1 finding/Final
   Verdict thực (không phải placeholder `TBD`/bảng rỗng) trước khi đưa vào tổng hợp cuối.
2. **Self-review tự chấm PASS theo 1 checklist nhưng checklist đích chưa được điền** (F2) — không
   có cơ chế đối chiếu chéo. → Rule: `self-review.md §5` chỉ được ghi "PASS" cho 1 mục nếu trỏ được
   tới dòng/checkbox cụ thể đã tick trong `review-checklist.md`; nếu không, ghi "NOT VERIFIED VIA
   CHECKLIST" thay vì PASS — trừ khi có quyết định waiver chính thức như trường hợp này.
3. **Aggregate SQL (SUM) chỉ được validate ở tầng "dấu của tổng cuối", không validate từng dòng
   trước khi cộng** (F1 — lỗi tinh vi hơn bản gốc BUG-1 vì không lộ ra ngoài) → Rule: khi thiết kế 1
   công thức Σ per-item có thể có item không hợp lệ, luôn lọc item không hợp lệ **trước khi** đưa
   vào phép cộng (trong `WHERE`/`ON`), không chỉ kiểm tra hậu-kiểm kết quả tổng.
4. **Vòng lặp ghi DB từng dòng (N ≤ vài chục) thay vì batch** xuất hiện lặp lại như 1 pattern nhỏ dễ
   bị bỏ qua ở review đầu (F4) → Rule: coding standard cho `infrastructure/adapter/*Jdbc*` nên mặc
   định dùng `batchUpdate` khi ghi nhiều dòng cùng lúc trong 1 vòng lặp, trừ khi có lý do cụ thể.
5. **catch (RuntimeException ex) log hard-code chuỗi cố định, bỏ qua `ex.getMessage()`** dù message
   an toàn theo chính sách log — dễ tái diễn ở các service khác đọc/parse file (F5) → Rule bổ sung
   vào `30-security.md`/`40-testing.md`: mọi `catch` log `WARN`/`ERROR` phải bao gồm nguyên nhân kỹ
   thuật (`ex.getMessage()` hoặc `ex.getClass().getSimpleName()`), miễn là không chứa nội dung dữ
   liệu người dùng/file.

---

## AI Review Finding Triage

| finding ID | decision | reason | action |
|---|---|---|---|
| F1 | Đã xác nhận đúng | Đọc lại patch SQL (`ON d.document_update_at >= d.document_create_at`) — đúng ý đồ, đúng công thức Σ per-file | Đóng — trạng thái "Đã xử lý" trong `ai-review.md §3` |
| F2 | Xác nhận PASS | Chấp nhận `self-review.md` làm nguồn duy nhất, waive `review-checklist.md` cho ticket này | Đóng — đã ghi WAIVED trong `review-checklist.md` |
| F3 | Xác nhận bỏ qua | Chấp nhận rủi ro log noise, đặc biệt sau khi F1 đã giảm tần suất kích hoạt nhánh này | Đóng — không fix, chuyển vào Accepted Risks |
| F4 | Xác nhận đề xuất sửa | Đồng ý hướng `batchUpdate`, nhưng đây là cải thiện kỹ thuật, không chặn merge | Mở — cần dev implement + viết TC2, theo dõi ở Should Fix/Q3 |
| F5 | Xác nhận bỏ qua | Chấp nhận rủi ro, không cấp bách so với phạm vi ticket | Đóng — không fix, chuyển vào Accepted Risks |

## Final Human Verdict

- **APPROVED (có điều kiện theo dõi)** — Không còn mục nào ở "Must Fix Before Merge". F1 (Major)
  đã xác nhận đúng, F2 (Major) đã waive chính thức. F3/F5 (Minor) chấp nhận rủi ro. F4 (Minor) đã
  duyệt hướng sửa nhưng chưa implement — không chặn merge, theo dõi như 1 việc mở (xem Q3). Trước
  khi coi tính năng "hoàn thiện toàn phần" ở mức vận hành thật: cần trả lời Q1 (chạy Postgres thật
  trước hay sau merge) và Q2 (thời điểm `flyway migrate`).
