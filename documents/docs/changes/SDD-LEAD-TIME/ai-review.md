# AI Review — Ticket SDD-LEAD-TIME (Lead Time visibility)

- **Ticket:** SDD-LEAD-TIME
- **Trạng thái:** Request changes
- **Ngày review:** 2026-08-21
- **Nguồn chân lý (SSOT):** `docs/changes/SDD-LEAD-TIME/spec-pack.md`
- **Phạm vi:** Review diff BE/FE/DB/test liên quan ticket, không sửa source code

## 1) Tóm tắt diff (5 dòng)

1. BE thêm parse/normalize date header từ `spec-pack.md` (`create_date`) và `report.md` (`update_date`) trong `ArtifactScannerService`, rồi persist vào `tbl_dim_ticket.started_at/completed_at`.
2. BE mở rộng persistence port và JDBC adapter với 2 method `updateTicketStartedAt` và `updateTicketCompletedAt`.
3. DB thêm migration `V513__add_completed_at_to_tbl_dim_ticket.sql` để bổ sung cột `completed_at TIMESTAMPTZ`.
4. PM Dashboard BE contract mở rộng query/DTO/model để trả `startedAt/completedAt`; FE hiển thị thêm 3 dòng: started/completed/duration.
5. FE thêm helper `computeLeadTimeDuration`, thêm i18n keys ở `en/vi/ja`, và cập nhật một số test fixture/unit test liên quan.

## 2) Findings

### F1 — Blocker — Correctness/Regression
- **Vấn đề:** Khi file `spec-pack.md` hoặc `report.md` không tồn tại (`existsFlag=false`), flow hiện tại chỉ xóa parsed artifacts nhưng không null hóa `tbl_dim_ticket.started_at/completed_at`. Điều này có thể giữ stale timestamp, trái với AC-3/AC-4/AC-6.
- **AC liên quan:** AC-SDD-LEAD-TIME-3, AC-SDD-LEAD-TIME-4, AC-SDD-LEAD-TIME-6.
- **Evidence:**
  - `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/scanner/ArtifactScannerService.java:335-343`  
    Nhánh `!snapshot.existsFlag()` cho `spec-pack.md`/`report.md` chỉ delete parsed sections/issues/risks, không gọi `updateTicketStartedAt(ticketId, null)` hoặc `updateTicketCompletedAt(ticketId, null)`.
  - `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/scanner/ArtifactScannerService.java:529-535` và `916-922`  
    Việc persist started/completed chỉ diễn ra trong nhánh parse khi file tồn tại và được parse.

### F2 — Blocker — Correctness/Compatibility
- **Vấn đề:** `HeaderDateNormalizer` ép offset cố định `+07:00` như workaround. Điều này biến đổi ngữ nghĩa timestamp nguồn (header chỉ là literal datetime/date) thành timezone-specific value, tạo rủi ro lệch hiển thị/duration ở consumer khác timezone hoặc xử lý khác FE hiện tại.
- **AC liên quan:** BR-SDD-LEAD-TIME-3, NFR Correctness (`spec-pack.md` §6.6), AC-SDD-LEAD-TIME-5.
- **Evidence:**
  - `EDCAP_BE/src/main/java/com/sdd/platform/domain/service/markdown/core/HeaderDateNormalizer.java:23-31`  
    Comment workaround cố định `ZoneOffset.of("+07:00")`.
  - `EDCAP_BE/src/main/java/com/sdd/platform/domain/service/markdown/core/HeaderDateNormalizer.java:46-47,53-54`  
    Cả datetime và date-only đều được gắn offset `+07:00`.

### F3 — Major — Missing tests
- **Vấn đề:** Chưa có assert test rõ ràng cho case null hóa `started_at/completed_at` khi file bị thiếu (sau khi trước đó đã có giá trị hợp lệ). Test hiện chủ yếu thêm fake storage methods, nhưng chưa chứng minh AC-3/4/6 theo scenario này.
- **AC liên quan:** AC-SDD-LEAD-TIME-3, AC-SDD-LEAD-TIME-4, AC-SDD-LEAD-TIME-6.
- **Evidence:**
  - `EDCAP_BE/src/test/UnitTest/java/com/sdd/platform/application/usecase/scanner/ArtifactScannerServiceTest.java:1584-1598`  
    Có thêm in-memory capture method cho started/completed.
  - `EDCAP_BE/src/test/UnitTest/java/com/sdd/platform/application/usecase/scanner/ArtifactScannerServiceTest.java`  
    Không thấy assertion cụ thể cho scenario “file missing sau scan trước đó có giá trị” (không có test name/evidence assert tương ứng trong diff).

### F4 — Minor — Readability/Spec wording drift
- **Vấn đề:** Label locale mới dùng “SDD start/completion time” thay vì diễn đạt sát đặc tả “spec-pack created time / report updated time”. Không sai chức năng nhưng có độ lệch ngữ nghĩa nghiệp vụ.
- **AC liên quan:** AC-SDD-LEAD-TIME-7 (i18n labels), phần To-Be/Terminology trong spec.
- **Evidence:**
  - `EDCAP_FE/public/locales/en/locale.json` thêm:
    - `"startedAt": "SDD start time"`
    - `"completedAt": "SDD completion time"`
  - Spec mô tả hiển thị theo nguồn file cụ thể: `spec-pack` create date và `report` update date.

## 3) AC chưa đạt hoặc khác đặc tả

1. **AC-3/AC-4/AC-6 có nguy cơ chưa đạt**: flow không đảm bảo null hóa `started_at/completed_at` khi file không tồn tại ở lần scan kế tiếp.
2. **NFR Correctness/AC-5 có nguy cơ lệch ngữ nghĩa dữ liệu**: offset `+07:00` hardcoded làm timestamp phụ thuộc workaround FE, không bền vững cho consumer khác.
3. **AC-7 về semantics label**: i18n key đã có, nhưng wording có độ lệch nhỏ so với diễn giải nghiệp vụ trong spec.

## 4) Đề xuất test cases bổ sung

### BE UT/IT
1. **Missing spec-pack after previous valid scan**  
   - Arrange: scan 1 có `create_date` hợp lệ -> `started_at` set.  
   - Act: scan 2 `spec-pack.md` absent (`existsFlag=false`).  
   - Assert: `started_at == NULL`.
2. **Missing report after previous valid scan**  
   - Arrange: scan 1 có `update_date` hợp lệ -> `completed_at` set.  
   - Act: scan 2 `report.md` absent.  
   - Assert: `completed_at == NULL`.
3. **Malformed date does not fail run and nulls value**  
   - Arrange: header date malformed (`TBD`).  
   - Assert: update cột tương ứng về `NULL`, scan run vẫn success.
4. **Rescan overwrite behavior**  
   - Arrange: scan 1 date A, scan 2 date B.  
   - Assert: giá trị DB cuối cùng là B (overwrite), không giữ A.

### FE UT/Component
1. `computeLeadTimeDuration` trả `"-"` khi `startedAt` hoặc `completedAt` là null.
2. `computeLeadTimeDuration` trả `"-"` khi `completedAt < startedAt`.
3. Component label/i18n assertions cho `en/vi/ja` để tránh hardcode và đảm bảo key mapping đúng semantics.

## 5) Số liệu thống kê

- **Tổng số finding:** 4  
  - Blocker: 2  
  - Major: 1  
  - Minor: 1
- **Tổng các finding đã fix:** 0 (review-only pass)
- **Tỷ lệ xử lý finding nghiêm trọng (Blocker):** 0/2 = **0%**
- **Tỷ lệ AI finding được con người chấp nhận:** Chưa có dữ liệu (pending human triage)
- **Tỷ lệ AI review finding hữu ích:** Chưa có dữ liệu (pending human triage)
- **Tỷ lệ AI finding bị đánh giá là false positive:** Chưa có dữ liệu (pending human triage)
- **Tỷ lệ AI finding đã được xử lý (fix hoặc quyết định chính thức):** 0/4 = **0%**

## 6) Kết luận review

- **Verdict:** `Request changes`
- **Điều kiện merge tối thiểu:**
1. Sửa logic null hóa `started_at/completed_at` cho nhánh file missing để đáp ứng AC-3/4/6.
2. Gỡ hardcoded timezone workaround `+07:00` hoặc thay bằng cách biểu diễn không làm đổi ngữ nghĩa timestamp nguồn.
3. Bổ sung test coverage cho các case missing/malformed/rescan overwrite nêu trên.

## 7) STEP F1 implementation update (2026-08-21)

- Scope: Only addressed `F1` (stale `started_at/completed_at` when `spec-pack.md` or `report.md` is missing). No changes for F2/F3/F4.
- STEP status: `Done`.

### Evidence

- Files changed:
  - `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/scanner/ArtifactScannerService.java`
  - `EDCAP_BE/src/test/UnitTest/java/com/sdd/platform/application/usecase/scanner/ArtifactScannerServiceTest.java`
- Code evidence:
  - Added `persistence.updateTicketStartedAt(ticket.ticketId(), null);` in `!snapshot.existsFlag()` branch for `spec-pack.md`.
  - Added `persistence.updateTicketCompletedAt(ticket.ticketId(), null);` in `!snapshot.existsFlag()` branch for `report.md`.
  - Added/updated tests to assert value is set on first scan, then cleared to `null` after source file deletion.
- Commands run:
  - `mvn -Dtest=ArtifactScannerServiceTest test`
- Command result:
  - `BUILD SUCCESS`
  - `Tests run: 25, Failures: 0, Errors: 0, Skipped: 0`

### Updated stats after STEP F1

- Total findings: `4`
- Total fixed findings: `1` (F1)
- Blocker resolution rate: `1/2 = 50%`
- AI findings accepted by human: `Pending human triage`
- Useful AI review finding rate: `Pending human triage`
- False positive rate: `Pending human triage`
- AI findings processed (fixed or officially dispositioned): `1/4 = 25%`

### Remaining findings

- `F2` Blocker: timezone semantics in `HeaderDateNormalizer` (`+07:00` hardcoded)
- `F3` Major: missing test coverage scenarios listed in this review
- `F4` Minor: wording drift in i18n labels

## 8) STEP F2 implementation update (2026-08-21)

- Scope: Only addressed `F2` (remove hardcoded `+07:00` timezone workaround in `HeaderDateNormalizer`). No changes for F3/F4.
- STEP status: `Done`.

### Evidence

- Files changed:
  - `EDCAP_BE/src/main/java/com/sdd/platform/domain/service/markdown/core/HeaderDateNormalizer.java`
  - `EDCAP_BE/src/test/java/com/sdd/platform/domain/service/markdown/core/HeaderDateNormalizerTest.java`
- Code evidence:
  - Replaced hardcoded offset `ZoneOffset.of("+07:00")` with neutral canonical offset `ZoneOffset.UTC`.
  - Updated normalization branches (datetime and date-only) to use the canonical offset consistently.
  - Updated unit test expectations from `+07:00` to `UTC`.
- Commands run:
  - `codegraph explore "HeaderDateNormalizer ArtifactScannerService started_at completed_at"`
  - `codegraph explore "EDCAP_BE/src/main/java/com/sdd/platform/domain/service/markdown/core/HeaderDateNormalizer.java HeaderDateNormalizerTest"`
  - `mvn -Dtest=HeaderDateNormalizerTest test`
- Command result:
  - `BUILD SUCCESS`
  - `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`

### Updated stats after STEP F2

- Total findings: `4`
- Total fixed findings: `2` (F1, F2)
- Blocker resolution rate: `2/2 = 100%`
- AI findings accepted by human: `Pending human triage`
- Useful AI review finding rate: `Pending human triage`
- False positive rate: `Pending human triage`
- AI findings processed (fixed or officially dispositioned): `2/4 = 50%`

### Remaining findings

- `F3` Major: missing test coverage scenarios listed in this review
- `F4` Minor: wording drift in i18n labels

## 9) STEP F3 implementation update (2026-08-21)

- Scope: Only addressed `F3` (missing test coverage for malformed-date nulling and rescan overwrite). No changes for F4.
- STEP status: `Done`.

### Evidence

- Files changed:
  - `EDCAP_BE/src/test/UnitTest/java/com/sdd/platform/application/usecase/scanner/ArtifactScannerServiceTest.java`
- Code evidence:
  - Added test `ticket_scoped_scan_sets_ticket_dates_to_null_when_header_dates_are_malformed`:
    - First scan with valid `Create date`/`Update date` asserts `started_at` and `completed_at` are set.
    - Second scan with malformed `TBD` values asserts both fields are cleared to `null`.
    - Asserts scan status remains `SUCCESS`.
  - Added test `ticket_scoped_scan_overwrites_ticket_dates_on_rescan_when_header_dates_change`:
    - First scan captures initial `started_at`/`completed_at`.
    - Second scan with changed header dates asserts both values are overwritten (not preserved).
- Commands run:
  - `mvn -Dtest=ArtifactScannerServiceTest test`
- Command result:
  - `BUILD SUCCESS`
  - `Tests run: 27, Failures: 0, Errors: 0, Skipped: 0`

### Updated stats after STEP F3

- Total findings: `4`
- Total fixed findings: `3` (F1, F2, F3)
- Blocker resolution rate: `2/2 = 100%`
- AI findings accepted by human: `Pending human triage`
- Useful AI review finding rate: `Pending human triage`
- False positive rate: `Pending human triage`
- AI findings processed (fixed or officially dispositioned): `3/4 = 75%`

### Remaining findings

- `F4` Minor: wording drift in i18n labels
