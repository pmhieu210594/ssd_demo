# Review Checklist

**Ticket ID**: PHASE-DWELL-TIME
**Create date**: 2026-08-21
**Author**: TBD
**Update date**: 2026-08-21

> Nguồn đối chiếu: `spec-pack.md` (§6 Acceptance Criteria), `impl-plan.md`,
> `impact-analysis.md`, `wireframe.md`. Công thức Dwell Time chính thức:
> Σ per-file `(document_update_at − document_create_at)` trong cùng 1
> phase, đọc từ bảng `tbl_fact_artifact_document_date`.
>
> **Toàn bộ 7 Gate kỹ thuật của `impl-plan.md` đã RESOLVED 2026-08-20**
> (người dùng đã duyệt "Áp dụng tất cả đề xuất" — xem `impl-plan.md` §
> "Quyết định Gate"). Review sau implementation phải đối chiếu code thực
> tế **khớp đúng với quyết định đã chốt**, không phải đối chiếu với
> "phương án khuyến nghị chưa duyệt" nữa:
> - AC-6 (rescan): bảng `tbl_fact_artifact_document_date` phải có
>   `UNIQUE (artifact_snapshot_id)`, ghi bằng
>   `INSERT ... ON CONFLICT (artifact_snapshot_id) DO NOTHING` (Gate #1).
> - Service trích header phải đọc header của **cả 8 file** trong
>   `CHANGE_TARGET_FILES` (gồm `blackbox-testcases.md`, Gate #8), tự
>   `readBlob` lại theo `source_path` (Gate #7) — không phải tái sử dụng
>   blob content dùng chung (không tồn tại sẵn trong
>   `scanTicketDirectory`).
> - Nếu code thực tế lệch với các quyết định này mà không có lý do ghi
>   lại rõ ràng trong `self-review.md`, đánh Blocker/Major tương ứng,
>   không tự cho là "AI đã tự chọn hướng khác hợp lý hơn".

> **WAIVED (2026-08-21)**: người dùng đã xác nhận PASS cho finding F2 của
> `ai-review.md` — chấp nhận `self-review.md` làm nguồn duy nhất cho việc
> đối chiếu review của ticket này. File này **không bắt buộc phải điền
> đầy đủ** trước khi merge cho ticket `PHASE-DWELL-TIME`. Giữ nguyên các
> dòng `TODO`/checkbox chưa tick bên dưới làm hồ sơ lưu trữ, không tick
> khống để hợp thức hoá.

---

## 1. Đối chiếu specification / AC

| AC ID | Review Point | Severity | Result |
|---|---|---|---|
| AC-PHASE-DWELL-TIME-1 | Card "Phase Dwell Time" hiển thị đúng 7 phase theo `phase_order`: `1,3,4,5,6,7,8`; không hiển thị `0-A,0-B,2,9`. Đối chiếu danh sách/thứ tự với `tbl_dim_phase.phase_order` thực tế, không hardcode danh sách 7 phase rời rạc ở tầng khác với nguồn DB. **Lưu ý riêng**: số phase hiển thị (7) độc lập với số file scan (`CHANGE_TARGET_FILES` có 8 file, xem ghi chú đầu file) — không nhầm lẫn 2 con số này khi review. | Blocker | TODO |
| AC-PHASE-DWELL-TIME-2 | Với ticket có ≥1 file thuộc phase N đủ cặp `document_create_at`/`document_update_at`, Dwell Time = Σ per-file `(document_update_at − document_create_at)`, format `hh:mm:ss`, `hh` không giới hạn 24 (vd `27:20:05`). | Blocker | TODO |
| AC-PHASE-DWELL-TIME-3 | Ticket chưa có file nào thuộc phase N (không có bản ghi `document_create_at`) → hiển thị `"-"`, không hiển thị Dwell Time hay giá trị rác (0, `00:00:00`, `null` chuỗi). | Major | TODO |
| AC-PHASE-DWELL-TIME-4 | File có `document_create_at` nhưng chưa có `document_update_at` không đóng góp vào tổng; nếu phase N không còn file nào đủ cặp → hiển thị `"-"`. Đối chiếu implementation thực tế đúng 2 case: (a) 1 file duy nhất thiếu `document_update_at` → `"-"`; (b) 1 phase có ≥2 file, chỉ 1 file đủ cặp → Dwell Time chỉ tính từ file đủ cặp, không lẫn file thiếu. | Blocker | TODO |
| AC-PHASE-DWELL-TIME-6 | Rescan cùng `content_hash` không ghi đè `document_create_at`/`document_update_at` đã tính trước đó trên bảng `tbl_fact_artifact_document_date` → Dwell Time không đổi sau rescan lặp lại. **Phụ thuộc Gate #1 của `impl-plan.md`** (UNIQUE trên `artifact_snapshot_id`, upsert vs insert-only) — đối chiếu hành vi thực tế, không giả định. | Major | TODO |
| AC-PHASE-DWELL-TIME-7 | "Current phase" hiện tại của Card "Phase"/`TicketPhaseEvaluatorService` không đổi giá trị trước/sau khi thêm tính năng (regression test `phaseCode` hiện tại). | Blocker | TODO |
| AC-PHASE-DWELL-TIME-8 | Tên tính năng đúng 3 locale: EN "Phase Dwell Time", JA "各フェーズの滞留時間", VI "Thời gian kẹt ở từng phase" — đối chiếu ký tự chính xác (không lệch dấu, không lệch Kanji/Kana) ở cả 3 file `locale.json`. | Major | TODO |
| AC-PHASE-DWELL-TIME-9 | Migration mới chỉ `CREATE`, không `ALTER`/`DROP` lên `tbl_fact_ticket_phase_status`, `tbl_fact_artifact_snapshot`, hay bảng hiện có nào khác. | Blocker | TODO |
| AC-PHASE-DWELL-TIME-10 | API lỗi/timeout khi lấy dữ liệu phase/dwell time → hiển thị `"-"`, không throw lỗi UI, không tự động retry, không làm hỏng phần còn lại của response `/detail`. | Blocker | TODO |

**Lưu ý phạm vi AC**: `spec-pack.md` không có `AC-PHASE-DWELL-TIME-5` — không tự
suy đoán thêm AC thứ 5, giữ nguyên đúng 9 AC đã liệt kê.

---

## 2. General System Review

### 2.1. Số, số full-width, số chữ số, độ chính xác

- [ ] `hh` trong `hh:mm:ss` không bị giới hạn/ép về 24 (không dùng kiểu dữ liệu/format tự động mod 24, vd không dùng `LocalTime`/`java.time.LocalTime` cho phần giờ vì loại này giới hạn 0-23).
- [ ] `mm`, `ss` luôn có 2 chữ số (zero-padded), không hiển thị `7:2:5` thay vì `07:02:05`.
- [ ] Giá trị Dwell Time âm (do lỗi dữ liệu, `update_date < create_date` cho 1 file) được xử lý rõ ràng — không hiển thị `hh:mm:ss` âm hoặc giá trị vô nghĩa cho người dùng; xác nhận hành vi khi gặp case này (không suy đoán, ghi nhận là điểm cần chốt nếu implementation chưa xử lý).
- [ ] Không có input số nào từ người dùng cho tính năng này (thuần đọc) — xác nhận không có ô nhập liệu số nào bị bỏ sót cần validate.
- [ ] Số full-width (toàn giác, vd `１２：３０：００`) không phát sinh trong output — giá trị Dwell Time là do BE format, không phải do người dùng nhập/copy từ nguồn khác.
- [ ] Không overflow khi Dwell Time rất lớn (ticket tồn tại lâu, vd hàng nghìn giờ) — xác nhận kiểu dữ liệu lưu trung gian (giây/millisecond) đủ lớn, không tràn `int`.
- [ ] Trường hợp cộng dồn nhiều file trong cùng 1 phase (`SUM(document_update_at - document_create_at)` nhiều dòng): xác nhận phép cộng không có sai số làm tròn/mất giây khi tổng hợp nhiều khoảng thời gian.

### 2.2. Loại ký tự, encoding, locale

- [ ] Tên tính năng 3 locale (EN/JA/VI, xem §5 `spec-pack.md`) không bị mojibake khi đọc/ghi UTF-8 trong `locale.json`.
- [ ] Ký tự Kana/Kanji trong bản dịch JA ("各フェーズの滞留時間") hiển thị đúng, không bị thay bằng ký tự thay thế (tofu/`?`).
- [ ] Dấu tiếng Việt trong bản dịch VI ("Thời gian kẹt ở từng phase") không bị mất dấu/sai encoding.
- [ ] Định dạng `hh:mm:ss` dùng ký tự dấu hai chấm nửa giác (half-width `:`), không lẫn dấu toàn giác (`：`).
- [ ] Không có bước trim/normalize nào làm sai lệch giá trị số trong chuỗi `hh:mm:ss` (vd trim nhầm số 0 ở đầu).

### 2.3. Literal / Magic Number / Master Data

- [ ] Danh sách 7 `phase_code` (`1,3,4,5,6,7,8`) không hardcode rời rạc ở nhiều nơi (FE + BE) — xác nhận có 1 nguồn duy nhất (query lọc theo `tbl_dim_phase`, hoặc constant dùng chung) thay vì mỗi tầng tự liệt kê literal khác nhau, tránh lệch danh sách khi `tbl_dim_phase` thay đổi.
- [ ] Không hardcode `phase_id`/`artifact_type_id` dạng số nguyên literal trong code Java/SQL — dùng `phase_code` hoặc join qua bảng master (`tbl_dim_phase`, `tbl_dim_artifact_type`).
- [ ] Tên bảng mới (`tbl_fact_artifact_document_date` theo `impl-plan.md`, hoặc tên khác nếu Gate quyết định lại) không bị viết cứng nhiều biến thể tên khác nhau giữa migration/entity/query.
- [ ] Chuỗi ký tự trạng thái/label không dùng cho tính năng này (đã chốt bỏ nhãn Pending/InProgress) — xác nhận không còn sót literal nhãn trạng thái nào trong code hoặc locale key thừa.
- [ ] Logic format `hh:mm:ss` (nếu FE giữ helper) không hardcode hệ số quy đổi (giây/phút = 60, phút/giờ = 60) rải rác nhiều chỗ.
- [ ] Danh sách 7 `artifact_type_code`/`phase_code` dùng trong WHERE clause SQL không lệch với danh sách hiển thị FE (đối chiếu cả 2 phía).
- [ ] Số phần tử thực tế của `CHANGE_TARGET_FILES` (8, gồm `blackbox-testcases.md`) không bị hardcode nhầm thành `7` ở service trích header mới — service phải tự liệt kê đúng danh sách file cần đọc header, không copy-paste literal `7` từ tài liệu spec (vốn nói về số **phase** hiển thị, không phải số **file** scan).
- [ ] Migration version mới (`V512` theo xác nhận tại thời điểm viết `impl-plan.md`) không hardcode "ăn theo" tài liệu cũ nếu tại thời điểm code đã có migration mới hơn được merge — review phải kiểm tra lại version cao nhất thực tế trong `db/migration/` ngay trước khi merge, không tin số `V512` một cách mù quáng.
- [ ] Root package `com.sdd.platform` dùng nhất quán cho mọi class/service mới — không có class nào bị đặt nhầm sang package giả định khác (`com.edcap`, ...).

### 2.4. Chuyển trạng thái, boundary value, exception

- [ ] Boundary: phase N không có file nào (không có `document_create_at`) → `"-"` (AC-3).
- [ ] Boundary: phase N có đúng 1 file, file đó thiếu `document_update_at` → `"-"` (AC-4).
- [ ] Boundary: phase N có ≥2 file, chỉ 1 file đủ cặp → Dwell Time chỉ tính từ file đủ cặp, không lẫn file thiếu (AC-4).
- [ ] Boundary: `document_create_at` == `document_update_at` cho 1 file (Dwell Time đóng góp = `00:00:00`) — xác nhận hiển thị đúng, không nhầm thành `"-"` khi cộng dồn vào tổng của phase.
- [ ] Exception: lỗi/timeout khi truy vấn dữ liệu Dwell Time cho 1 phase không làm sập/lỗi toàn bộ response `/detail` (AC-10) — xác nhận try/catch nội bộ đúng như `impl-plan.md` "Phương châm implement" mục 5, không để exception lan tới `GlobalExceptionHandler` một cách không cần thiết.
- [ ] Exception: parse header `create_date`/`update_date` lỗi định dạng không throw ra ngoài luồng scan, trả `null` cho field đó.
- [ ] Rescan (AC-6): xác nhận `ON CONFLICT` không update timestamp gốc khi `content_hash` không đổi — có test cụ thể chứng minh 2 lần scan liên tiếp không đổi Dwell Time.

---

## 3. FE Review

- [ ] Card mới "Phase Dwell Time" render đúng vị trí: ngay dưới Card "Phase" hiện tại (không chèn vào bên trong `PhaseCard`).
- [ ] FE không tự tính lại công thức Dwell Time — chỉ render giá trị `dwellTime` BE trả về, tránh lệch công thức 2 phía.
- [ ] FE render `"-"` khi `dwellTime` là `null`/thiếu/`undefined` — kiểm tra cả 3 trường hợp field thiếu hoàn toàn trong response (không chỉ `null`).
- [ ] Không có nhãn trạng thái (Pending/InProgress/Completed) nào bị thêm nhầm vào UI.
- [ ] `EDCAP_FE/src/__ tests __/pm-dashboard/TicketDetailDrawer.test.tsx` (chú ý tên thư mục có khoảng trắng literal `__ tests __`, dễ gõ nhầm thành `__tests__`) — object `detail satisfies PmDashboardTicketDetail` giả lập được cập nhật để khớp type mới `phaseDwellTime`, không làm vỡ test hiện có.
- [ ] Named export, `React.forwardRef` + `displayName` nếu Card mới là component tái sử dụng được tách riêng (theo `10-style.md`).
- [ ] Nếu có helper format duration ở FE: không dùng `any`, không có `as` cast không giải thích; đặt tại `lib/utils.ts` theo pattern hiện có cạnh `formatDateTime`.
- [ ] Không gọi `fetch` trực tiếp ở component — dữ liệu `phaseDwellTime` đi qua `lib/api.ts`/TanStack Query hiện có của `PmDashboardTicketDetail` (theo `20-architecture.md`).
- [ ] i18n: chỉ cần 1 key tên tính năng theo 3 locale (không phát sinh key thừa cho nhãn trạng thái đã bị loại bỏ).

---

## 4. BE/API Review

- [ ] Mở rộng `PmDashboardTicketDetail`/`PmDashboardTicketDetailDto`/`DashboardTicketDetail` bằng field mới `phaseDwellTime`, không đổi field hiện có (`phaseCode/phaseName/phaseDescription/phaseCreatedAt/phaseOrder`) — đối chiếu đúng additive-only.
- [ ] Không tạo endpoint mới; không đổi request shape (`ticketId` path variable duy nhất).
- [ ] Không sửa `TicketPhaseEvaluatorService`, `upsertTicketPhaseStatus`, hoặc bất kỳ logic xác định "current phase" hiện tại (AC-7) — đối chiếu diff, đảm bảo class này không nằm trong danh sách file bị sửa.
- [ ] Layer boundary: `web`/Controller không import trực tiếp từ `infrastructure`; logic tính Dwell Time nằm ở Service/Adapter theo đúng pattern hexagonal hiện có (`20-architecture.md`).
- [ ] Constructor injection (không field injection `@Autowired`) cho service/port mới (`10-style.md`).
- [ ] `@Transactional` (nếu có) chỉ đặt ở tầng `@Service`, không đặt ở adapter/controller.
- [ ] Nếu `ArtifactScannerService.scanTicketDirectory` bị sửa để gọi service mới: xác nhận không có HTTP call GitHub nào bị đưa vào trong transaction DB (đúng `20-architecture.md` — external HTTP calls chạy ngoài transaction). **Lưu ý đã xác minh**: code hiện tại của `ArtifactScannerService`/`ArtifactScannerJdbcAdapter` không dùng `TransactionTemplate`/`@Transactional` ở đâu cả (sai lệch có sẵn từ trước so với `20-architecture.md`) — không yêu cầu implementation mới phải "sửa luôn" sai lệch cũ này ngoài phạm vi ticket, nhưng cũng không được thêm 1 cách xử lý transaction mới không nhất quán với phần còn lại của class.
- [ ] Constructor `ArtifactScannerService` (12 dependency ở bản chính) nếu được thêm dependency mới: chỉ thêm vào constructor chính, giữ nguyên 2 overload "backward-compatible" (2-arg, 11-arg) mà `ArtifactScannerServiceTest.java` đang dùng — đối chiếu test vẫn compile và pass không cần sửa mock.
- [ ] Nếu service mới trích header tự gọi lại `source.readBlob(...)` (do không có blob content dùng chung sẵn trong `scanTicketDirectory`) — xác nhận số lượng API call thêm ra thực tế (tối đa +8/lần scan/ticket) đã được chấp nhận, không âm thầm vượt quá do đọc lặp lại nhiều lần cho cùng 1 file.
- [ ] Lỗi tính Dwell Time cho 1 phase không được throw ra Controller — bọc try/catch nội bộ ở Service/Adapter, log `WARN`, trả `null` cho field liên quan.
- [ ] Không thêm `ResponseEntity` ad-hoc trong Controller cho case lỗi Dwell Time — giữ nguyên `GlobalExceptionHandler` làm điểm map exception → HTTP status duy nhất (`30-security.md`).
- [ ] Domain/DTO builder pattern đúng chuẩn: `@Builder @Getter @Setter @NoArgsConstructor @AllArgsConstructor` (nếu áp dụng cho model liên quan), không dùng `@Data`.
- [ ] Nguồn dữ liệu Dwell Time thực tế trong code khớp đúng `spec-pack.md`/`impl-plan.md`: bảng `tbl_fact_artifact_document_date` (`document_create_at`/`document_update_at` parse từ header), **không phải** `tbl_fact_artifact_snapshot.created_at`.

---

## 5. DB/Migration Review

- [ ] Migration mới chỉ `CREATE` (bảng mới), tuyệt đối không có `ALTER`/`DROP` lên bảng hiện có (AC-9) — đọc toàn văn file migration, không chỉ đọc tên file.
- [ ] Tên bảng mới theo đúng convention hiện có của dự án (tiền tố `tbl_fact_`); không trùng tên với đối tượng DB đã tồn tại.
- [ ] Cột timestamp mới nullable đúng như thiết kế (parse có thể fail/thiếu) — không đặt `NOT NULL` sai chỗ gây lỗi insert khi header thiếu field.
- [ ] Khóa ngoại/tham chiếu tới `tbl_fact_artifact_snapshot(artifact_snapshot_id)` được định nghĩa đúng, có index phù hợp cho truy vấn JOIN.
- [ ] Xác nhận có/không UNIQUE constraint trên `artifact_snapshot_id` — quyết định này ảnh hưởng trực tiếp tới hành vi `ON CONFLICT` cho AC-6 (rescan không đổi giá trị) — đối chiếu migration thực tế với quyết định đã chốt ở Gate. **Lưu ý đã xác minh**: không có tiền lệ schema nào ép buộc hướng này — `tbl_fact_ticket_issue` (V397) và `tbl_fact_artifact_parsed_section` (V4) là 2 bảng FK gần nhất tới `artifact_snapshot_id`, cả 2 đều KHÔNG UNIQUE (1:nhiều) — không tự suy ra UNIQUE là "theo pattern có sẵn" nếu Gate chưa xác nhận rõ.
- [ ] Cột chuẩn `created_at/created_by/updated_at/updated_by` (nếu bảng mới) tuân theo `database.md`.
- [ ] Không có thay đổi nào lên `tbl_fact_ticket_phase_status` (kể cả không cố ý qua cascade/trigger).
- [ ] Flyway version number không trùng với version đã tồn tại; không tự ý chạy `flyway migrate` trên môi trường chia sẻ mà chưa xin xác nhận người dùng (`00-safety.md §3`).
- [ ] Query tính Dwell Time chỉ join theo `ticket_id` (không rò rỉ dữ liệu chéo ticket khác) — đối chiếu WHERE/JOIN clause thực tế.
- [ ] Đánh giá hiệu năng JOIN 3 bảng (bảng mới + `tbl_fact_artifact_snapshot` + `tbl_dim_artifact_type` + `tbl_dim_phase`) cho 1 lần xem chi tiết ticket — xác nhận có index cần thiết trên cột join.

---

## 6. Security/Privacy Review

- [ ] Tính năng thuần đọc, không có input mới từ người dùng — xác nhận không có tham số nào từ FE được đưa thẳng vào SQL không qua parameter binding (không có nguy cơ SQL injection mới).
- [ ] Không lộ PII mới: Dwell Time chỉ là khoảng thời gian tính từ timestamp nội bộ, không kèm theo nội dung nhạy cảm nào khác trong response.
- [ ] Permission: dùng đúng permission hiện có của `TicketDetailDrawer`/`PmDashboardTicketDetail` (session-based cookie theo `30-security.md`), không mở rộng quyền truy cập cho role nào chưa từng có quyền xem ticket detail.
- [ ] Không log toàn bộ nội dung file `.md` khi parse header lỗi — chỉ log `sourcePath` + tên field thiếu/sai, tuân `30-security.md` § Log/Audit Sanitization.
- [ ] Log lỗi Dwell Time (nếu có) không chứa secret/token/password/apiKey/credential — đối chiếu message log thực tế.
- [ ] CORS/session/transport không bị ảnh hưởng bởi thay đổi này (không có endpoint mới, không đổi cấu hình `SecurityConfig`).
- [ ] Stack trace không lộ ra client khi lỗi tính Dwell Time — luôn map qua `GlobalExceptionHandler` nếu lỗi phải throw lên tầng trên.

---

## 7. Operation/Maintenance Review

- [ ] Đủ log để điều tra khi Dwell Time hiển thị `"-"` bất thường (log mức `WARN` khi tính lỗi, kèm `ticketId`/`phaseCode`/`sourcePath` liên quan) — không log mức `ERROR` cho case suy giảm dữ liệu cục bộ.
- [ ] Không có batch/job mới cần vận hành thêm — xác nhận đúng như `spec-pack.md §12`.
- [ ] Cấu hình không hardcode (nếu có timeout/threshold nào cho việc lấy dữ liệu phase, xác nhận đặt trong config, không hardcode số giây trong code).
- [ ] Rollback: xác nhận kế hoạch rollback code (git revert, additive-safe) và rollback DB (giữ bảng mới ẩn field, hoặc `DROP TABLE` bằng migration riêng nếu cần dọn dẹp) đã được ghi nhận, không chỉ nói miệng.
- [ ] Backfill cho ticket cũ: xác nhận có cần trigger `FULL` scan sau deploy hay không đã được quyết định (Gate #4 của `impl-plan.md`).
- [ ] Retry/double-execution: rescan lặp lại (scanner chạy lại nhiều lần) không tạo dòng dữ liệu trùng/mâu thuẫn trong bảng mới (liên quan trực tiếp AC-6).
- [ ] Correlation/traceId: nếu lỗi Dwell Time được log, xác nhận log có đủ ngữ cảnh (`ticketId`, `phaseCode`) để tra cứu.

---

## 8. Test Review

- [ ] Unit BE: test case 1 phase có ≥1 file đủ cặp `create_date`/`update_date` → có Dwell Time đúng công thức Σ per-file (AC-2).
- [ ] Unit BE: test case phase không có file nào → `"-"` (AC-3).
- [ ] Unit BE: test case file duy nhất thiếu `update_date` → `"-"`; ≥2 file, 1 file đủ cặp → chỉ tính file đủ cặp (AC-4).
- [ ] Unit BE: test rescan cùng `content_hash` → timestamp không đổi (AC-6).
- [ ] Unit BE: test lỗi/timeout khi tính Dwell Time cho 1 phase → response `/detail` vẫn trả về, field liên quan là `null` (AC-10).
- [ ] Regression BE: `TicketPhaseEvaluatorServiceTest.java` không cần đổi và vẫn pass (AC-7) — xác nhận đã chạy lại, không chỉ giả định "không đổi thì không cần chạy".
- [ ] Regression BE: `ArtifactScannerServiceTest.java` cập nhật mock nếu constructor `ArtifactScannerService` đổi, chạy lại pass.
- [ ] ArchUnit: `ArchitectureTest` chạy lại xanh sau khi thêm class/package mới (không có `allowedPackage` exception mới không qua team review).
- [ ] Unit FE: Card mới render đúng 2 case — có Dwell Time (`hh:mm:ss`) và `"-"`.
- [ ] Regression FE: `TicketDetailDrawer.test.tsx` pass sau khi mở rộng object `detail` giả lập với `phaseDwellTime`.
- [ ] AC Closure: có test mount `TicketDetailDrawer` thật (không chỉ Card cô lập) để xác nhận field thực sự hiển thị trên trang (`40-testing.md` § AC Closure) — bắt buộc.
- [ ] Integration/backfill: có test/case xác nhận trên ticket cũ có sẵn snapshot lịch sử, Dwell Time tính đúng không cần dữ liệu mới.
- [ ] Không mock domain entity/value object trong unit test BE — chỉ mock port interface (`40-testing.md`).
- [ ] Test boundary `hh` > 24 (vd `27:20:05`) có case cụ thể, không chỉ test case trong ngày.

---

## 9. Documentation/Traceability Review

- [ ] `spec-pack.md` và `impl-plan.md` khớp nhau về nguồn dữ liệu Dwell Time (bảng `tbl_fact_artifact_document_date`, công thức Σ per-file).
- [ ] Tên bảng/service/field thực tế trong code khớp với tên cuối cùng ghi trong `impl-plan.md` (không còn để ở dạng "tên đề xuất"/"tên tạm").
- [ ] `review-checklist.md` (file này) và `self-review.md` khớp với phạm vi implementation cuối cùng.
- [ ] Assumption `A-PHASE-DWELL-TIME-3`, `A-PHASE-DWELL-TIME-4` (spec-pack §16) được xác nhận đúng/sai sau khi implement, ghi lại kết quả thay vì để nguyên giả định.
- [ ] Mọi Gate còn OPEN trong `impl-plan.md` (schema bảng mới, UNIQUE constraint, vị trí service, cách xử lý lỗi per-phase, backfill FULL scan, chính sách log) đã được ghi lại kết quả quyết định cụ thể ở đâu đó — không được để trống sau khi implement xong.

---

## 10. Release/Rollback Review

- [ ] Rollout additive-only, không breaking — xác nhận không có bước nào yêu cầu downtime hoặc thay đổi đồng thời nhiều service.
- [ ] Rollback code: `git revert` an toàn (đối chiếu thực tế không có thay đổi phá vỡ ở file khác ngoài phạm vi ticket).
- [ ] Rollback DB: có phương án cụ thể (giữ bảng mới + ẩn field ở BE/FE, hoặc migration `DROP TABLE` riêng) — xác nhận phương án nào được chọn thực tế, không chỉ liệt kê tuỳ chọn.
- [ ] Trước khi `flyway migrate` trên môi trường chia sẻ: đã xin xác nhận người dùng (`00-safety.md §3`) — ghi nhận bằng chứng đã hỏi/được duyệt.
- [ ] Backfill (nếu cần trigger `FULL` scan sau deploy): có kế hoạch cụ thể về thời điểm chạy, không chạy tự động không kiểm soát trên toàn bộ ticket.
- [ ] Không có consumer nào khác của `PmDashboardTicketDetail` (ngoài Card "Phase"/Card "Phase Dwell Time") bị ảnh hưởng bởi field mới — đối chiếu Assumption A-PHASE-DWELL-TIME-3 đã được rà soát thực tế.

---

## Severity Definition

| severity | meaning | required action |
|---|---|---|
| Blocker | Cannot be released | Must fix |
| Major | High probability of becoming a bug | Fix or accepted risk |
| Minor | Minor improvement | Optional |
| Question | Spec confirmation required | Open Issue |
| False Positive | Incorrect Report | Record Reason for Rejection |
| Accepted Risk | Accepted Risk | Record Impact/Owner/Deadline |
