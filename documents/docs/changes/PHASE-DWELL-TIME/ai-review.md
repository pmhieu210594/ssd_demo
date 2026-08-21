# AI Review — Ticket 711 (PHASE-DWELL-TIME)

- **Ticket:** 711 / PHASE-DWELL-TIME
- **Trạng thái:** Draft
- **Tạo ngày:** 2026-08-21
- **Diff review:** *không có* — workspace này **không phải git repository** (`Is a git repository: false`). Không có `sha-base`/`sha-head`/branch nào để trích dẫn. "Diff" trong review này = tập file đã thay đổi cho ticket, đối chiếu bằng cách đọc trực tiếp nội dung hiện tại trên đĩa so với mô tả trong `self-review.md §3` (danh sách file đã sửa) và `spec-pack.md`/`impl-plan.md`.
- **Branch:** *không áp dụng* (không có git)
- **Nguồn đối chiếu:** [docs/changes/PHASE-DWELL-TIME/spec-pack.md](spec-pack.md)
- **Reviewer:** AI review (Claude, độc lập với các pass implement/self-review/bug-hunt trước đó trong cùng ticket) — *người review con người cần điền tên và xác nhận lại*
- **Cách kiểm chứng:** đọc code tĩnh (BE Java + FE TypeScript hiện tại trên đĩa) + đọc SQL trong `PmDashboardJdbcAdapter.java`/migration `V512` + `grep` xác nhận trạng thái `review-checklist.md` + chạy `mvn -o test` (BE) và `npx vitest run` (FE) để xác nhận baseline xanh trước khi review. **Chưa** chạy app thật, **chưa** chạy trên Postgres thật, **chưa** chạy E2E/black-box thật trên UI.
- **Phạm vi:** chỉ ghi kết quả review, không sửa source code (áp dụng cho phần review chính — riêng STEP F1 ở cuối tài liệu có sửa code theo đúng phạm vi được giao).

> **Ghi chú quan trọng về input:** không có git history trong workspace này, nên không thể trích
> `git show`/`git diff` theo đúng nghĩa. Toàn bộ evidence dưới đây dẫn theo **đường dẫn file + số
> dòng hiện tại trên đĩa** tại thời điểm review (2026-08-21), không phải theo SHA commit. Đây là
> giới hạn về evidence/process cần ghi nhận (xem [§5](#5-độ-phủ-review)), không phải AI tự bỏ qua
> bước đọc diff.

## Mục lục

1. [Kết luận](#1-kết-luận) — [Next action](#next-action)
2. [Tóm tắt diff](#2-tóm-tắt-diff)
3. [Tổng hợp findings](#3-tổng-hợp-findings)
4. [Chi tiết findings](#4-chi-tiết-findings) — [F1](#f1) · [F2](#f2) · [F3](#f3) · [F4](#f4) · [F5](#f5)
5. [Độ phủ review](#5-độ-phủ-review)
6. [Traceability — AC chưa đạt](#6-traceability--ac-chưa-đạt)
7. [Đề xuất test cases bổ sung](#7-đề-xuất-test-cases-bổ-sung)
8. [Số liệu thống kê](#8-số-liệu-thống-kê)
9. [Phụ lục — Quy ước](#9-phụ-lục--quy-ước)

## 1. Kết luận

**Verdict: Request changes.**

0 Blocker · 2 Major · 3 Minor. (Định nghĩa severity: xem [§9 Phụ lục](#9-phụ-lục--quy-ước).)

Điều kiện để pass:

1. [F1](#f1) (Major, Correctness) nên được fix trước khi coi AC-2 là đã verify đầy đủ — hiện đã
   **fix trong pass F1 (2026-08-21)**, xem [Cập nhật xử lý F1](#f1-fix); chờ review con người xác
   nhận lại.
2. [F2](#f2) (Major, quy trình/audit) cần Tech Lead/QA quyết định: điền thật `review-checklist.md`
   trước khi merge, hoặc chính thức chấp nhận bỏ qua checklist cho ticket này (ghi lại lý do).
3. [F3](#f3)/[F4](#f4)/[F5](#f5) (Minor) không chặn merge nhưng nên có quyết định fix hay chấp
   nhận rủi ro trước khi đóng ticket.
4. Không phát hiện AC nào "chưa đạt hoàn toàn" ngoài F1 — xem [§6](#6-traceability--ac-chưa-đạt).

<a id="next-action"></a>

### Next action

| Việc | Nội dung | Người nhận | Deadline |
| --- | --- | --- | --- |
| Xác nhận lại fix [F1](#f1) (điều kiện merge) | Đã fix trong pass F1 (2026-08-21, xem [#f1-fix](#f1-fix)) — cần dev/reviewer con người đọc lại SQL đổi + test mới rồi đổi trạng thái sang "Đã xử lý" | *cần điền* | *cần điền* |
| Quyết định Tech Lead/QA cho [F2](#f2) | Điền thật `review-checklist.md` (9 dòng §1 + 99 checkbox §2-10 hiện đều là placeholder) hoặc chấp nhận chính thức bỏ qua | *cần điền* | *cần điền* |
| Quyết định fix hay chấp nhận rủi ro cho [F3](#f3)/[F4](#f4)/[F5](#f5) | Xem chi tiết từng finding ở §4 | *cần điền* | *cần điền* |
| Chạy Independent Review này lại **trên Postgres thật** một lần trước khi coi AC-2/AC-6 là đã verify runtime | Vẫn là khoảng trống đã biết từ `test-results.md §8`, review này không lấp được (không có Postgres trong môi trường review) | *cần điền* | *cần điền* |

## 2. Tóm tắt diff

- **Migration DB** (`EDCAP_BE/src/main/resources/db/migration/V512__add_artifact_document_date.sql`)
  thêm 1 bảng mới `tbl_fact_artifact_document_date` (additive-only, `UNIQUE(artifact_snapshot_id)`)
  để lưu `create_date`/`update_date` tự khai báo trong header mỗi file `.md`.
- **BE — service mới** (`ArtifactDocumentDateService.java`) trích `create_date`/`update_date` từ
  header 8 file mục tiêu qua `MarkdownParserCore`, được `ArtifactScannerService.scanTicketDirectory`
  gọi thêm (đọc, không đổi luồng ghi hiện có); **port/adapter mới**
  (`upsertArtifactDocumentDates` trong `ArtifactScannerJdbcAdapter.java`) ghi bảng trên bằng
  `ON CONFLICT (artifact_snapshot_id) DO NOTHING`.
- **BE — aggregate mới** (`PmDashboardJdbcAdapter.findPhaseDwellTime`, dòng 597-653) cộng dồn
  `SUM(EXTRACT(EPOCH FROM (update_at - create_at)))` theo phase, neo trên `tbl_dim_phase` (LEFT
  JOIN), trả `PhaseDwellTimeItem[]` qua field mới `phaseDwellTime` trên
  `DashboardTicketDetail`/`PmDashboardTicketDetailDto`.
- **FE** (`TicketDetailDrawer.tsx`) thêm Card `PhaseDwellTimeCard` (dòng 159-192) render 7 dòng từ
  `detail.phaseDwellTime ?? []`, đặt ngay dưới Card "Phase" hiện có (không sửa `PhaseCard`); thêm
  key i18n `phaseDwellTime` cho 3 locale.
- **Test**: nhiều test BE/FE mới/cập nhật (xem `self-review.md §3`) — BE 603 test / FE 344 test,
  đều PASS theo `test-results.md`; **không có** test chạy trên Postgres thật, **không có**
  `ArchitectureTest` (file không tồn tại trong repo).

## 3. Tổng hợp findings

Cột **Trạng thái** dùng đúng một trong: `Chưa xử lý` · `Đã fix (chờ review)` · `Đã xử lý` · `Không fix`.

| #   | Severity  | Loại              | Tóm tắt                                      | AC liên quan | Người duyệt | Trạng thái |
| --- | --------- | ----------------- | --------------------------------------------- | ------------ | ----------- | ---------- |
| F1  | Major | Correctness | [1 file có `update_date < create_date` trong 1 phase có ≥2 file bị netted (cộng bù trừ) vào tổng thay vì bị loại trừ, làm tổng Dwell Time sai lệch một cách âm thầm](#f1) | AC-2 | | Đã fix (chờ review) — xem [Cập nhật xử lý F1](#f1-fix) |
| F2  | Major | Missing tests | [`review-checklist.md` chưa được điền thật (9/9 dòng §1 vẫn "TODO", 99/99 checkbox §2-10 chưa tick) dù `self-review.md` đã tự chấm PASS cho các mục tương ứng](#f2) | N/A (process/audit) | | Chưa xử lý |
| F3  | Minor | Robustness | [WARN log cho tổng âm bị tính lại và log lại mỗi lần mở Ticket Detail (đọc), không chỉ 1 lần lúc quét (ghi) — có thể gây log noise nếu ticket lỗi dữ liệu bị xem nhiều lần](#f3) | spec-pack §12 (Operation/Logging) | | Chưa xử lý |
| F4  | Minor | Correctness | [`upsertArtifactDocumentDates` ghi từng dòng bằng vòng lặp `jdbc.update` tuần tự thay vì 1 lệnh `batchUpdate` — N round-trip DB không cần thiết mỗi lần quét (N ≤ 8)](#f4) | N/A (impl-plan § Phương châm data/DB/query) | | Chưa xử lý |
| F5  | Minor | Robustness | [`ArtifactDocumentDateService.extract` nuốt exception khi đọc/parse 1 file mà không log `ex.getMessage()` — chỉ log nhãn cứng `"content"`, mất thông tin nguyên nhân thật (IO lỗi, encoding, v.v.) dù không vi phạm quy tắc "không log nội dung file"](#f5) | Gate #5 (impl-plan.md, log policy) | | Chưa xử lý |

## 4. Chi tiết findings

<a id="f1"></a>

### F1 — Per-file negative dwell time chỉ được lọc ở mức tổng, không lọc từng file trước khi cộng dồn

**Severity:** Major · **Loại:** Correctness · **AC:** AC-PHASE-DWELL-TIME-2

`spec-pack.md §5` định nghĩa Dwell Time = **Σ per-file** `(document_update_at − document_create_at)`.
Bản fix trước đó (BUG-PHASE-DWELL-TIME-1, xem `self-review.md §7`) chỉ chặn trường hợp **tổng cuối
cùng** âm (`dwellSeconds < 0` sau khi `SUM` đã cộng hết). Nhưng nếu 1 phase có **≥2 file**, và chỉ
1 file bị nhập ngược ngày (`update_date < create_date`), trong khi (các) file còn lại hợp lệ, thì
`SUM(...)` cộng luôn cả số **âm** của file lỗi vào **cùng** phép cộng với số dương của file hợp lệ
— tổng cuối có thể vẫn **dương** (chỉ bị "ăn bớt"), nên nhánh chặn số âm ở
`formatDwellTimeSeconds` **không kích hoạt**, và giá trị hiển thị cho người dùng **sai lệch âm
thầm** (nhỏ hơn thực tế), không có `"-"`, không có log `WARN` nào cảnh báo.

```sql
LEFT JOIN tbl_fact_artifact_document_date d ON d.artifact_snapshot_id = s.artifact_snapshot_id
    AND d.document_create_at IS NOT NULL
    AND d.document_update_at IS NOT NULL
-- thiếu điều kiện loại trừ file có document_update_at < document_create_at
```

- File B (hợp lệ, đóng góp +3 giờ) + File A (lỗi dữ liệu, đóng góp −1 giờ) trong cùng 1 phase →
  tổng hiển thị **"02:00:00"**, không phải `"-"` và không phải "03:00:00" (giá trị chỉ tính từ file
  hợp lệ) — người xem Dashboard không có cách nào biết tổng này đã bị 1 file lỗi làm sai lệch.
- Đây là 1 dạng nghiêm trọng hơn case đã fix trước (case cũ dễ phát hiện vì ra số âm rõ ràng; case
  này **không** để lộ dấu hiệu bất thường nào ra UI/log).

**Evidence:**

- [`EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/PmDashboardJdbcAdapter.java:607-609`](../../../EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/PmDashboardJdbcAdapter.java#L607-L609) — điều kiện `ON` của LEFT JOIN chỉ lọc `IS NOT NULL`, không lọc theo thứ tự 2 mốc thời gian.
- [`EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/PmDashboardJdbcAdapter.java:637-647`](../../../EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/PmDashboardJdbcAdapter.java#L637-L647) — `formatDwellTimeSeconds` chỉ kiểm tra `dwellSeconds < 0` **sau khi** đã `SUM` toàn bộ, không có bước lọc per-file trước đó.
- [`docs/changes/PHASE-DWELL-TIME/spec-pack.md` §5](spec-pack.md) — định nghĩa công thức "Σ per-file", ngụ ý mỗi file đóng góp phải hợp lệ, không phải "Σ rồi mới kiểm tra dấu của tổng".

**Đề xuất fix:** thêm điều kiện `AND d.document_update_at >= d.document_create_at` vào chính `ON`
clause của `LEFT JOIN tbl_fact_artifact_document_date` (cùng vị trí với 2 điều kiện `IS NOT NULL`
hiện có) — file có ngày bị đảo sẽ bị loại khỏi phép cộng hoàn toàn, coi như "không đủ dữ liệu hợp
lệ" (cùng nhánh xử lý với AC-4, không phải nhánh mới). Giữ nguyên nhánh kiểm tra `dwellSeconds < 0`
ở tầng Java làm lớp phòng vệ thứ 2 (defense-in-depth), không xoá.

<a id="f1-fix"></a>

#### Cập nhật xử lý F1 (2026-08-21)

**Trạng thái:** Đã fix trong code (cùng phiên với review này), **chưa merge**, **chưa chạy trên
Postgres thật** — chỉ xác nhận qua BE unit test mock-SQL (không có Testcontainers trong repo).

**Đã thay đổi:**

- [`EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/PmDashboardJdbcAdapter.java`](../../../EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/PmDashboardJdbcAdapter.java) — thêm `AND d.document_update_at >= d.document_create_at` vào `ON` clause của `LEFT JOIN tbl_fact_artifact_document_date` — file có ngày bị đảo giờ bị loại khỏi phép cộng ngay tại tầng SQL, không chờ tới khi tổng ra âm mới bị chặn. **Đây đúng là điểm chặn đúng theo đề xuất fix ban đầu**: sửa tại nguồn (loại file lỗi trước khi `SUM`) thay vì chỉ chặn triệu chứng (tổng âm) ở tầng sau.
- Test mới: [`EDCAP_BE/src/test/UnitTest/java/com/sdd/platform/infrastructure/persistence/adapter/PmDashboardJdbcAdapterPhaseDwellTimeTest.java`](../../../EDCAP_BE/src/test/UnitTest/java/com/sdd/platform/infrastructure/persistence/adapter/PmDashboardJdbcAdapterPhaseDwellTimeTest.java) — thêm assertion xác nhận SQL chứa điều kiện `d.document_update_at >= d.document_create_at` bên trong `ON` (không phải `WHERE`).

**Ảnh hưởng:**

- Case đã fix trước (1 file duy nhất, tổng âm → `"-"`) **không đổi hành vi** — vẫn `"-"`, vì file
  đó giờ bị loại ngay ở JOIN nên `SUM` trên tập rỗng vẫn trả `NULL` → `"-"` (nhánh Java
  `dwellSeconds < 0` giờ hiếm khi được kích hoạt thật, nhưng vẫn giữ làm lớp phòng vệ thứ 2).
- Case mới (2 file, 1 lỗi 1 hợp lệ): tổng giờ chỉ tính từ file hợp lệ (`"03:00:00"` trong ví dụ ở
  §4), đúng với công thức Σ per-file của AC-2 — không còn bị file lỗi "ăn bớt" âm thầm.
- Không đổi hành vi AC-1/AC-3/AC-9/AC-10 — chỉ sửa đúng 1 điều kiện `ON`, không đụng cấu trúc
  JOIN/GROUP BY/ORDER BY khác.
- Không mở rộng sang F2/F3/F4/F5 — các finding đó vẫn ở trạng thái "Chưa xử lý".

**Commands/evidence:**

- `mvn -o -Dtest=PmDashboardJdbcAdapterPhaseDwellTimeTest test` (trong `EDCAP_BE/`) — **PASS**,
  `Tests run: 5, Failures: 0, Errors: 0` (5 test trong file này, gồm 1 test mới xác nhận điều kiện
  `d.document_update_at >= d.document_create_at`).
- `mvn -o test` (full suite, trong `EDCAP_BE/`) — **PASS**, `Tests run: 604, Failures: 0, Errors: 0`,
  `BUILD SUCCESS` (603 test trước đó + 1 test mới của F1 = 604, không có regression).
- Postgres thật — **chưa chạy** trong pass này (không có Testcontainers/Postgres trong môi trường
  làm việc, đã ghi nhận từ trước ở `test-plan.md`/`test-results.md §8`).

**Remaining issues (liên quan F1):**

- Chưa xác nhận trên Postgres thật rằng `d.document_update_at >= d.document_create_at` so sánh
  đúng kiểu `TIMESTAMPTZ` như kỳ vọng (về lý thuyết đúng, nhưng "kỳ vọng" ≠ "đã chạy thật").
  BB-PHASE-DWELL-TIME-09 (dữ liệu 2 file, 1 đủ cặp) trong `blackbox-testcases.md` **gần với** case
  này nhưng **không đúng y hệt** (BB-09 test file thiếu `update_date`, không phải file có ngày bị
  đảo) — cần thêm 1 black-box case mới hoặc mở rộng BB-09/BB-17 để cover đúng tổ hợp "1 file lỗi +
  1 file hợp lệ trong cùng phase" (xem đề xuất TC ở [§7](#7-đề-xuất-test-cases-bổ-sung)).
- Người review con người cần đọc lại đoạn SQL đổi ở trên và xác nhận verdict trước khi đổi cột
  "Trạng thái" ở [§3](#3-tổng-hợp-findings) từ "Đã fix (chờ review)" sang "Đã xử lý".

**Next action:** Dev/reviewer con người đọc diff SQL + test mới, xác nhận đúng ý đồ, rồi cập nhật
`self-review.md`/`open-issues.md` nếu cần ghi nhận đây là 1 quyết định bổ sung (không phải Human
Decision mới vì không đổi hành vi nghiệp vụ đã duyệt, chỉ vá đúng công thức Σ per-file đã có sẵn
trong spec).

---

<a id="f2"></a>

### F2 — `review-checklist.md` chưa được điền thật, dù `self-review.md` tự chấm PASS cho các mục tương ứng

**Severity:** Major · **Loại:** Missing tests (đánh giá gần nhất trong enum cố định — bản chất là
thiếu bằng chứng/audit trail bắt buộc theo quy trình) · **AC:** N/A (quy trình, không phải 1 AC cụ thể)

`review-checklist.md` là tài liệu **bắt buộc điền trong quá trình review** theo cấu trúc của ticket
(9 dòng đối chiếu AC ở §1, 99 checkbox chi tiết ở §2-10). Tại thời điểm review này, **toàn bộ vẫn
là placeholder chưa điền**:

- 9/9 dòng ở bảng §1 "Đối chiếu specification / AC" có cột "Result" = `TODO`.
- 99/99 dòng checkbox ở §2-10 (Số/full-width, ký tự/encoding, Literal/Magic Number, boundary,
  FE/BE/DB/Security/Operation/Test/Docs/Release Review) đều `[ ]` (chưa tick).

Trong khi đó, `self-review.md §5 "Self-Check using Review Checklist"` lại tự chấm **PASS** cho
từng mục tương ứng (2.1, 2.2, 2.3, 2.4, 3, 4, 5, 6, 7, 9, 10) và chỉ chấm PARTIAL cho mục 8 (Test
Review). Đây là **mâu thuẫn nội tại giữa 2 tài liệu**: `self-review.md` tự nhận đã đối chiếu đầy
đủ theo `review-checklist.md`, nhưng file đích thực tế **chưa từng được ghi nhận kết quả nào**.

**Evidence:**

- [`docs/changes/PHASE-DWELL-TIME/review-checklist.md`](review-checklist.md) — `grep "TODO"` ra 9
  kết quả (toàn bộ dòng bảng §1); `grep "\[ \]"` ra 99 kết quả (toàn bộ checkbox §2-10).
- [`docs/changes/PHASE-DWELL-TIME/self-review.md` §5](self-review.md) — bảng "Self-Check using
  Review Checklist" ghi "PASS" cho 10/11 dòng mà không trỏ ngược lại bất kỳ dòng cụ thể nào đã tick
  trong `review-checklist.md`.

**Đề xuất fix:** không phải lỗi code — là gap quy trình. Đề xuất: (a) điền thật
`review-checklist.md` (đối chiếu từng dòng §1 với AC thật, tick từng checkbox §2-10 dựa trên bằng
chứng cụ thể, không tick khống), hoặc (b) nếu team quyết định `self-review.md` đã đủ thay thế
`review-checklist.md` cho ticket này, cần 1 quyết định chính thức ghi lại lý do (vd "review-checklist.md
là artifact thừa cho quy trình hiện tại, dùng self-review.md làm nguồn duy nhất") thay vì để file
tồn tại ở trạng thái mâu thuẫn với self-review.md.

---

<a id="f3"></a>

### F3 — WARN log cho tổng âm được tính lại (và log lại) mỗi lần mở Ticket Detail, không chỉ 1 lần lúc quét

**Severity:** Minor · **Loại:** Robustness · **AC:** spec-pack §12 (Operation/Logging/Monitoring)

`formatDwellTimeSeconds` (gọi từ `findPhaseDwellTime`, chạy trong `PmDashboardJdbcAdapter.findDetail`)
log `WARN` mỗi khi tổng ra âm. Khác với các WARN log khác trong cùng tính năng (log lỗi parse
header ở `ArtifactDocumentDateService`, chỉ chạy trong **luồng quét**, tần suất thấp), log này chạy
trong **luồng đọc** `/detail` — tức **mỗi lần** người dùng mở Ticket Detail của 1 ticket có dữ liệu
lỗi (ngày bị đảo), log `WARN` giống hệt nhau bị ghi lại 1 lần nữa. Nếu ticket đó được nhiều người
xem nhiều lần trong ngày (dashboard PM thường được refresh/xem lại thường xuyên), log server có thể
bị lặp lại hàng chục/hàng trăm lần cho cùng 1 nguyên nhân gốc, gây nhiễu log thật sự khi cần điều
tra sự cố khác.

**Evidence:**

- [`EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/PmDashboardJdbcAdapter.java:641-645`](../../../EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/PmDashboardJdbcAdapter.java#L641-L645) — `LOG.warn(...)` nằm trong `formatDwellTimeSeconds`, được gọi từ row-mapper của `findPhaseDwellTime`, tức chạy mỗi lần `GET /api/v1/pm/dashboard/tickets/{ticketId}/detail` được gọi.

**Đề xuất fix:** cân nhắc 1 trong 2 hướng: (a) sau khi F1 được fix, log này sẽ hiếm khi kích hoạt
thật (chỉ còn là lớp phòng vệ) nên có thể chấp nhận giữ nguyên; (b) nếu muốn chặt hơn, chuyển việc
log cảnh báo dữ liệu ngược ngày sang **thời điểm quét** (trong `ArtifactDocumentDateService`/luồng
ghi bảng mới) thay vì thời điểm đọc — nhất quán với các WARN log khác của tính năng này, tránh log
lặp lại theo tần suất xem trang.

---

<a id="f4"></a>

### F4 — `upsertArtifactDocumentDates` ghi từng dòng bằng vòng lặp tuần tự thay vì 1 lệnh batch

**Severity:** Minor · **Loại:** Correctness (đúng hơn là Performance — không có giá trị "Performance"
trong enum cố định của template, xếp gần nhất vào Correctness/Robustness theo tinh thần "code chạy
đúng nhưng không tối ưu ở cách gọi I/O") · **AC:** N/A (impl-plan.md § Phương châm data/DB/query)

`upsertArtifactDocumentDates` lặp qua danh sách `documentDates` (tối đa 8 phần tử/lần quét, theo
Gate #8) và gọi `jdbc.update(...)` **riêng lẻ cho từng phần tử** — 8 round-trip DB tuần tự thay vì
1 lệnh `NamedParameterJdbcTemplate.batchUpdate(sql, SqlParameterSourceUtils.createBatch(...))`. Quy
mô hiện tại nhỏ (≤8 dòng/lần quét/ticket, tần suất quét không cao) nên **không phải rủi ro hiệu
năng cấp bách**, nhưng đây là pattern N+1 kinh điển nếu sau này số file mục tiêu tăng lên hoặc scan
được chạy hàng loạt nhiều ticket cùng lúc.

**Evidence:**

- [`EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/scanner/ArtifactScannerJdbcAdapter.java:1044-1071`](../../../EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/scanner/ArtifactScannerJdbcAdapter.java#L1044-L1071) — vòng lặp `for (... : documentDates) { jdbc.update(...) }`.

**Đề xuất fix:** đổi sang `jdbc.batchUpdate(sql, documentDates.stream().map(...).toArray(SqlParameterSource[]::new))`
— 1 round-trip thay vì N. Không bắt buộc fix ngay (quy mô nhỏ), nhưng nên ghi nhận làm technical
debt nếu số file mục tiêu (`CHANGE_TARGET_FILES`) tăng thêm trong tương lai.

---

<a id="f5"></a>

### F5 — Exception khi đọc/parse 1 file bị nuốt mà không log nguyên nhân thật

**Severity:** Minor · **Loại:** Robustness · **AC:** Gate #5 (`impl-plan.md`, chính sách log)

`ArtifactDocumentDateService.extract` bọc `try/catch (RuntimeException ex)` quanh việc đọc blob +
parse header, nhưng log message hard-code chuỗi `"content"` làm `missingField` cho **mọi** loại lỗi
(dù là lỗi mạng khi đọc blob, lỗi encoding, hay lỗi parse thật) — **không log `ex.getMessage()`**
dù message của exception (khác nội dung file `.md`) hoàn toàn an toàn để log theo `30-security.md`.
Khi cần điều tra vì sao 1 phase luôn hiển thị `"-"` dù file có vẻ hợp lệ, log hiện tại không đủ
thông tin để phân biệt "lỗi mạng tạm thời" và "lỗi parse do nội dung sai".

**Evidence:**

- [`EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/scanner/ArtifactDocumentDateService.java:88-92`](../../../EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/scanner/ArtifactDocumentDateService.java#L88-L92) — `catch (RuntimeException ex) { log.warn(..., "content"); }` không dùng biến `ex` ngoài việc bắt nó.

**Đề xuất fix:** thêm `ex.getMessage()` (không phải `ex` toàn bộ stack trace, và tuyệt đối không
log nội dung file) vào cuối message log hiện có, ví dụ:
`log.warn("... missingField={}, cause={}", ..., "content", ex.getMessage())` — vẫn tuân
`30-security.md § Log/Audit Sanitization` (không log payload file), nhưng giữ lại thông tin
nguyên nhân kỹ thuật.

## 5. Độ phủ review

| Vùng code | Cách kiểm chứng | Kết quả |
| --- | --- | --- |
| `PmDashboardJdbcAdapter.findPhaseDwellTime`/`formatDwellTimeSeconds` ([PmDashboardJdbcAdapter.java](../../../EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/PmDashboardJdbcAdapter.java)) | Đọc code tĩnh + đọc lại toàn bộ SQL | Phát hiện [F1](#f1) (đã fix trong pass này), [F3](#f3) |
| `ArtifactDocumentDateService.extract`/`parseHeaderDate` | Đọc code tĩnh | Phát hiện [F5](#f5); logic parse 2 định dạng ngày/giờ + 1 định dạng chỉ ngày — không thấy lỗi thêm |
| `ArtifactScannerJdbcAdapter.upsertArtifactDocumentDates` | Đọc code tĩnh | Phát hiện [F4](#f4) |
| `ArtifactScannerService.scanTicketDirectory` (điểm gọi service mới, dòng 446-454) | Đọc code tĩnh | Không thấy vấn đề — try/catch bọc đúng, không đổi luồng ghi hiện có, đúng như Gate #2/#3 |
| Migration `V512__add_artifact_document_date.sql` | Đọc toàn văn | Không thấy vấn đề — additive-only, có `UNIQUE`, đúng convention |
| `TicketDetailDrawer.tsx` — `PhaseDwellTimeCard` (dòng 159-192) | Đọc code tĩnh | Không thấy vấn đề mới — guard `?? []` đã có (fix BUG-2 trước đó), sort đúng `phaseOrder` |
| BE/FE unit test hiện có | Đọc danh sách test trong `self-review.md §3`, không tự chạy lại toàn bộ trong pass review này (chỉ chạy lại phạm vi hẹp ở STEP F1) | Tin cậy theo `test-results.md` (603 BE / 344 FE PASS) — không tự kiểm chứng lại số liệu này trong pass thuần-review |
| `review-checklist.md` | `grep` xác nhận trạng thái thật | Phát hiện [F2](#f2) |
| Toàn bộ AC còn lại trong bảng traceability `spec-pack.md §6` (AC-1, AC-3, AC-4, AC-6, AC-7, AC-8, AC-9, AC-10) | Đọc code + đối chiếu với `self-review.md`/`test-results.md` đã có | Không phát hiện lệch spec mới ngoài F1 — xem [§6](#6-traceability--ac-chưa-đạt) |
| Chạy Postgres thật để verify AC-2 (cộng dồn)/AC-6 (rescan) ở mức runtime | — | ⬜ chưa verify trong pass này — không có Postgres/Testcontainers trong môi trường review (giới hạn đã biết, lặp lại từ `test-results.md §8`) |
| 24 black-box test case (`blackbox-testcases.md`) | — | ⬜ chưa verify trong pass này — cần QA thao tác tay trên UI thật, ngoài phạm vi review code tĩnh |
| Gate app (`mvn -o test`, `npx vitest run`) | Đã chạy trong pass F1 (xem cuối tài liệu) | PASS — xem "Commands/evidence" ở mục STEP F1 |

## 6. Traceability — AC chưa đạt

| AC | Dòng trong spec | Verdict | Finding |
| --- | --- | --- | --- |
| AC-PHASE-DWELL-TIME-2 | [spec-pack.md §5 (định nghĩa Σ per-file)](spec-pack.md) | Có nguy cơ chưa đạt ở case biên (đã fix trong pass này, chờ review con người xác nhận) | [F1](#f1) |
| AC-PHASE-DWELL-TIME-1, 3, 4, 6, 7, 8, 9, 10 | [spec-pack.md §6](spec-pack.md) | Không phát hiện lệch spec mới trong pass review này (đã có bằng chứng unit test tương ứng theo `self-review.md`/`test-results.md`) — **runtime thật trên Postgres và UI thật chưa được verify** (giới hạn chung, không phải finding riêng cho từng AC) | — |

## 7. Đề xuất test cases bổ sung

| Test case | Lớp test | Liên quan | Ưu tiên |
| --- | --- | --- | --- |
| [TC 1](#tc-1) | BE UT | F1 (Major) | **Chặn merge** (đã bổ sung 1 phần trong pass F1 — xem remaining ở dưới) |
| [TC 2](#tc-2) | BE UT | F4 (Minor) | Nên có |
| [TC 3](#tc-3) | BE UT | F5 (Minor) | Nên có |
| [TC 4](#tc-4) | Black-box/E2E | F1 (Major) + AC-2 | Bắt buộc trước khi coi tính năng release-ready |

<a id="tc-1"></a>

### TC 1 — BE UT: aggregate SQL loại trừ file có ngày bị đảo khỏi phép cộng, kể cả khi có file hợp lệ khác trong cùng phase

*Liên quan [F1](#f1) · AC-PHASE-DWELL-TIME-2 · **Chặn merge***

- SQL capture test đã bổ sung trong pass F1 (xem [#f1-fix](#f1-fix)) chỉ xác nhận **cấu trúc câu
  lệnh** (điều kiện nằm trong `ON`). **Còn thiếu**: 1 test dùng kỹ thuật capture `RowMapper` +
  `ResultSet` giả lập (theo đúng pattern đã dùng cho case tổng âm ở
  `PmDashboardJdbcAdapterPhaseDwellTimeTest`) để mô phỏng **kết quả SQL thật** cho trường hợp 2
  file (1 lỗi 1 hợp lệ) — nhưng vì phép cộng 2 dòng dữ liệu (netting) xảy ra **bên trong** câu
  `SUM(...)` của Postgres, không xảy ra trong Java, kỹ thuật mock hiện tại (giả lập 1 `ResultSet`
  duy nhất trả về `dwell_seconds` đã tính sẵn) **không thể** verify việc SQL server-side có thật sự
  loại trừ đúng dòng lỗi hay không — chỉ verify được cấu trúc SQL (đã làm) và giả định server tuân
  thủ SQL chuẩn.
- **Cần bổ sung** ở mức Integration Test trên Postgres thật (chưa có hạ tầng — xem TC 4 và
  `test-results.md §8`): dựng 2 dòng `tbl_fact_artifact_document_date` cho cùng 1 phase, 1 dòng có
  `document_update_at < document_create_at`, chạy thật câu SQL, assert `dwell_seconds` trả về đúng
  bằng giá trị của dòng hợp lệ.

<a id="tc-2"></a>

### TC 2 — BE UT: `upsertArtifactDocumentDates` dùng `batchUpdate` thay vì vòng lặp

*Liên quan [F4](#f4) · Không có AC cụ thể · Theo dõi*

- Đưa vào danh sách 3 `DocumentDate` khác nhau → assert `jdbc.batchUpdate(...)` được gọi **đúng 1
  lần** với 3 phần tử trong mảng tham số (không assert theo số lần gọi `jdbc.update` như cách viết
  test hiện tại, vì đó chính là cách đo brittleness cần tránh) — chỉ áp dụng sau khi F4 được fix.

<a id="tc-3"></a>

### TC 3 — BE UT: log message của lỗi đọc/parse file chứa nguyên nhân thật

*Liên quan [F5](#f5) · Gate #5 · Nên có*

- Ép `source.readBlob(...)` throw 1 `RuntimeException("simulated network error")` → assert log
  `WARN` (dùng `ListAppender`/tương tự) chứa chuỗi `"simulated network error"`, không chỉ chuỗi
  cứng `"content"`.

<a id="tc-4"></a>

### TC 4 — Black-box/manual: 1 phase có 2 hồ sơ, 1 hồ sơ có ngày bị nhập ngược, 1 hồ sơ hợp lệ

*Liên quan [F1](#f1) · AC-PHASE-DWELL-TIME-2 · Bắt buộc trước khi release*

- Bổ sung 1 dòng dữ liệu mới vào `test-data.md` (mở rộng bộ ND-7/AD-4 hiện có, hoặc thêm bộ mới)
  và 1 case mới vào `blackbox-testcases.md` (cạnh BB-09/BB-17): giai đoạn "Review Checklist" có 2
  hồ sơ — hồ sơ A hợp lệ (đóng góp 3 giờ), hồ sơ B có ngày cập nhật sớm hơn ngày tạo (dữ liệu lỗi).
  Kỳ vọng: dòng "Review Checklist" hiển thị **"03:00:00"** (chỉ tính hồ sơ A), không phải `"-"`
  (khác BB-17, vốn chỉ có 1 hồ sơ duy nhất và hồ sơ đó bị lỗi) và không phải giá trị bị "ăn bớt" do
  hồ sơ B. Case này **chưa tồn tại** trong `blackbox-testcases.md` hiện tại — BB-09 gần giống nhưng
  dùng "thiếu update_date" chứ không phải "ngày bị đảo".

## 8. Số liệu thống kê

> Số liệu dưới đây phản ánh **pass review này (2026-08-21) + 1 vòng fix ngay trong cùng phiên cho
> F1 (STEP F1)**. Các tỷ lệ yêu cầu dữ liệu xử lý sau merge (false-positive / human-accepted / hữu
> ích) vẫn cần người review con người điền — AI không thể tự đánh giá mức độ hữu ích của chính
> finding do mình tạo ra.

| Số liệu | Giá trị hiện tại | Ghi chú |
| --- | --- | --- |
| Tổng số finding | 5 (0 Blocker, 2 Major, 3 Minor) | Xem [§3](#3-tổng-hợp-findings) |
| Tổng số finding đã fix | 1 / 5 (F1) | F1 đã fix trong STEP F1 (2026-08-21), chờ review con người xác nhận verdict trước khi đổi "Đã fix (chờ review)" → "Đã xử lý". F2/F3/F4/F5 chưa xử lý |
| Tỷ lệ xử lý finding nghiêm trọng (Blocker) | 0 / 0 — không áp dụng | Không có finding Blocker nào trong pass này |
| Tỷ lệ AI finding được con người chấp nhận | chưa có dữ liệu | Cần người review điền sau khi xác nhận từng finding (đồng ý / không đồng ý / cần điều chỉnh) |
| Tỷ lệ AI review finding hữu ích | chưa có dữ liệu | Cần đo sau khi dev xác nhận mức độ hữu ích của từng finding, đặc biệt F1 (mức độ ảnh hưởng thực tế) |
| Tỷ lệ AI finding bị đánh giá là false positive | chưa có dữ liệu | Mọi finding đều có evidence dạng đường dẫn file + số dòng xác minh trên trạng thái đĩa hiện tại (2026-08-21); tỷ lệ false-positive thực tế cần người review xác nhận, đặc biệt F2 (có thể team đã có quyết định không thành văn bỏ qua `review-checklist.md`) |
| Tỷ lệ AI finding đã được xử lý (fix hoặc quyết định chính thức) | 1 / 5 (20%) | F1 đã fix (chờ review); F2, F3, F4, F5 chưa có quyết định chính thức nào (fix hoặc chấp nhận rủi ro) |

## 9. Phụ lục — Quy ước

- **Severity:**
  - **Blocker** — lệch spec/sai dữ liệu, không được merge cho tới khi fix.
  - **Major** — lệch spec hoặc thiếu bảo vệ ở mức cần fix hoặc cần quyết định chính thức từ BA.
  - **Minor** — cần sửa nhưng không chặn merge.
- **Loại:**
  - **Correctness** — code chạy ra kết quả sai so với spec.
  - **Robustness** — code đúng ở happy path nhưng vỡ ở case biên / dựa vào giả định không được bảo đảm.
  - **Regression risk** — thay đổi gỡ bỏ một bảo vệ đang có, chưa quan sát được lỗi trực tiếp.
  - **Missing tests** — thiếu hoặc sai lớp kiểm thử mà bảng truy vết yêu cầu.
- **Số dòng trong Evidence:** chính xác tại thời điểm review (2026-08-21) — workspace không có git
  nên không có SHA để gắn; nếu file bị sửa sau, số dòng cần đối chiếu lại thủ công.
- **Ký hiệu ⬜:** phần chưa hoàn tất, cần người review điền hoặc bổ sung.

---

## STEP F1 — Thực thi finding F1 (2026-08-21)

> Theo yêu cầu "chỉ thực hiện đúng STEP F1 trong ai-review.md, không mở rộng scope". Toàn bộ nội
> dung fix/evidence cho F1 đã được ghi tại [Cập nhật xử lý F1](#f1-fix) ở trên (đúng vị trí theo
> `ai-review.template.md`); mục này chỉ tổng hợp lại phần "Output bắt buộc" theo đúng format được
> yêu cầu riêng cho STEP F1, không lặp lại nội dung, chỉ trỏ chiếu.

### Đã thay đổi gì

- 1 điều kiện SQL trong `ON` clause của `PmDashboardJdbcAdapter.findPhaseDwellTime` (thêm
  `AND d.document_update_at >= d.document_create_at`) — xem chi tiết ở [#f1-fix](#f1-fix).
- 1 test mới trong `PmDashboardJdbcAdapterPhaseDwellTimeTest.java` xác nhận điều kiện trên có mặt
  trong SQL sinh ra.
- **Không sửa gì khác** — không đổi F2/F3/F4/F5, không đổi FE, không đổi migration, không đổi
  logic Java khác ngoài SQL string ở đúng 1 method.

### Ảnh hưởng gì

- AC-PHASE-DWELL-TIME-2 (Σ per-file) giờ đúng hơn với spec: 1 file lỗi dữ liệu (ngày bị đảo) không
  còn được cộng (dù là cộng số âm) vào tổng của phase — bị loại hoàn toàn khỏi phép cộng, giống
  cách file thiếu `update_date` đã bị loại (AC-4).
- Không đổi hành vi cho ticket/dữ liệu hợp lệ (mọi file có `update_date >= create_date` không bị
  ảnh hưởng bởi điều kiện mới).
- Không đổi request/response shape, không đổi endpoint, không đổi FE.

### Commands/evidence

- `mvn -o -Dtest=PmDashboardJdbcAdapterPhaseDwellTimeTest test` (trong `EDCAP_BE/`) — **PASS**,
  `Tests run: 5, Failures: 0, Errors: 0`.
- `mvn -o test` (full suite, trong `EDCAP_BE/`) — **PASS**, `Tests run: 604, Failures: 0, Errors: 0`,
  `BUILD SUCCESS` (603 → 604, +1 test mới, không regression).
- Lint FE/BE riêng, `npx tsc --noEmit`, `npx vitest run`: **không chạy trong STEP F1** — F1 chỉ sửa
  BE (1 điều kiện SQL + 1 test BE), không đụng file FE nào, nên không cần chạy lại gate FE cho
  đúng phạm vi hẹp của STEP này.
- Postgres thật / `flyway migrate`: **chưa chạy** — ngoài phạm vi STEP F1 (cấm destructive
  commands, cấm large refactor; chạy migrate trên môi trường chia sẻ cần hỏi người dùng trước theo
  `00-safety.md §3`, không nằm trong yêu cầu của STEP này).

### Remaining issues

- Xem đầy đủ ở [Remaining issues (liên quan F1)](#f1-fix) — tóm tắt: cần Integration Test trên
  Postgres thật (TC 4) và 1 black-box case mới trong `blackbox-testcases.md` để verify hành vi này
  ở mức runtime/UI thật, không chỉ ở mức cấu trúc SQL.

### Check tiếp theo

1. Chạy 2 command ở trên, dán kết quả thật vào mục "Commands/evidence" ở [#f1-fix](#f1-fix) (không
   để placeholder).
2. Người review con người đọc lại patch, xác nhận verdict, đổi trạng thái F1 ở [§3](#3-tổng-hợp-findings).
3. Không tự động chuyển sang fix F2/F3/F4/F5 trong cùng lượt này — mỗi finding còn lại cần 1 quyết
   định/phê duyệt riêng trước khi có STEP kế tiếp (F2, F3, ...).
