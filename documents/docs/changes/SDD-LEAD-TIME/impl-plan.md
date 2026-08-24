# Kế hoạch triển khai — SDD-LEAD-TIME (Lead Time visibility: spec-pack created time, report updated time, computed duration)

- **Ticket:** SDD-LEAD-TIME
- **Trạng thái:** Draft
- **Tạo ngày:** 2026-08-21

> Nguồn tham chiếu chính: `docs/changes/SDD-LEAD-TIME/spec-pack.md`.

---

## 0. Nguyên tắc sử dụng template

Template này dùng cho Phase 3: **Implementation Plan + Impact Analysis**. Toàn bộ nội dung dưới đây
chỉ dựa trên `spec-pack.md`, `context.md`, `ticket-rules.md` (tất cả Human Decision/Open Issue đã
closed) và các file source đã đọc trực tiếp trong Phase 3 (xem `source-inventory.md`).

---

## 1. Implementation policy

### 1.1. Tóm tắt policy

Ticket SDD-LEAD-TIME là một **mở rộng cộng thêm (additive extension)** của pipeline scan/parse/persist
đã tồn tại — không phải build mới, không phải refactor. Toàn bộ 3 giá trị hiển thị (created time,
updated time, duration) được sinh ra như một side-effect của Artifact Scanner content-parse pass đã
có sẵn (`SpecPackMarkdownParser`/`ReportMarkdownParser` → `ArtifactScannerService`), không thêm pipeline
mới, không thêm job/backfill mới, không thêm endpoint mới.

- **BE**: thêm 1 port method mới (`ArtifactScannerPersistencePort`) + implementation JDBC, 1 utility
  chuẩn hoá ngày dùng chung, mở rộng 2 điểm gọi persist đã có, mở rộng SQL/DTO của
  `PmDashboardJdbcAdapter`.
- **DB**: 1 migration cộng thêm (`V513`, cột nullable, không default, không backfill).
- **FE**: 3 dòng hiển thị mới trong `TicketInformationCard`, 1 hàm thuần tính duration (client-side),
  3 key i18n mới × 3 locale.
- **Giữ nguyên**: hành vi hiện tại của `started_at` cho các consumer khác (thứ tự sort/filter trong
  `PmDashboardJdbcAdapter`), `report.md` template (không thêm field mới), `tbl_dim_ticket.closed_at`
  và `ck_ticket_date_order`.
- **Không làm**: gọi GitHub commit-history API, tính/trả duration ở BE, thêm endpoint mới, thêm
  backfill job, giữ lại giá trị cũ khi field bị malformed (phải null hoá).

### 1.2. Non-goals / ngoài phạm vi

| #   | Nội dung không làm | Lý do / căn cứ |
| --- | ------------------ | -------------- |
| 1   | Gọi GitHub commit-history API (first/last commit theo file) | Explicitly rejected — spec-pack §2.2, A-SDD-LEAD-TIME-2 |
| 2   | Thêm field header mới vào `report.md` template | H-SDD-LEAD-TIME-2, closed — dùng lại `**Update date**` sẵn có |
| 3   | Backfill job/script riêng cho ticket cũ | H-SDD-LEAD-TIME-3, closed — populate qua scan cadence bình thường |
| 4   | Giữ lại timestamp cũ khi field sau này bị malformed/missing | H-SDD-LEAD-TIME-4, closed — phải null hoá, không giữ giá trị cũ |
| 5   | Tính/trả duration ở BE hoặc lưu duration thành cột DB riêng | OI-SDD-LEAD-TIME-1, closed — duration chỉ tính ở FE, không persist |
| 6   | Thêm REST/admin endpoint mới | Spec-pack §2.2 — chỉ mở rộng response của endpoint ticket-detail sẵn có |
| 7   | Backfill 3 key i18n `createAt`/`mergedAt`/`ticketInformation` còn thiếu trong `ja` theo context.md | Live-read xác nhận 3 key này **đã có sẵn** trong `ja` — không phải gap thật; xem `impact-analysis.md` §14. Chỉ thêm 3 key mới của ticket này |
| 8   | Repurpose `tbl_dim_ticket.closed_at` cho "report updated" | Ngữ nghĩa khác biệt, xung đột với `ck_ticket_date_order` (context.md Forbidden Methods) |

### 1.3. Phương án đã so sánh

| Vấn đề | Phương án A | Phương án B | Phương án chọn | Lý do chọn |
| --- | --- | --- | --- | --- |
| Nguồn timestamp | GitHub commit-history API (per-file first/last commit) | Đọc header field trong nội dung file (`**Create date**`/`**Update date**`) qua `extractHeaderMetadata()` sẵn có | **B** | User decision đã closed (H-1, H-2); A yêu cầu API call mới, tăng surface bên ngoài, không cần thiết |
| Nơi tính duration | BE tính và trả field riêng trên DTO | FE tính client-side từ `startedAt`/`completedAt` đã trả về | **B (FE)** | OI-SDD-LEAD-TIME-1, closed — tránh thêm logic tính toán/định dạng phía BE, giữ DTO chỉ chứa raw data |
| Cơ chế ghi/persist | Pipeline/API riêng để cập nhật `started_at`/`completed_at` theo yêu cầu (on-demand) | Ghi trong đúng persist step hiện có của Artifact Scanner, không thêm pipeline | **B** | BR-SDD-LEAD-TIME-9, H-SDD-LEAD-TIME-3, closed — tránh trùng lặp cơ chế, giữ nhất quán với các field khác đã có |
| Nơi đặt logic chuẩn hoá ngày | Viết riêng trong từng parser (`SpecPackMarkdownParser`, `ReportMarkdownParser`) | 1 utility dùng chung, gọi tại điểm tích hợp trong `ArtifactScannerService` | **B (shared utility)** | Spec-pack §6.6 Maintainability — tránh copy-paste logic; `headerMetadata` đã sẵn có trên `ParsedArtifact`, không cần sửa parser |

### 1.4. Ràng buộc từ spec-pack

| #   | Ràng buộc | Ảnh hưởng tới implementation |
| --- | --- | --- |
| 1   | BR-SDD-LEAD-TIME-3: chấp nhận `YYYY-MM-DD HH:mm:ss` và `YYYY-MM-DD` (bare) → chuẩn hoá về `00:00:00` | Cần 1 utility test kỹ boundary: full-width/half-width digit, chuỗi rỗng, whitespace-only, text không phải ngày |
| 2   | BR-SDD-LEAD-TIME-6: file/field thiếu hoặc malformed → cột về `NULL`, không throw lỗi, không fail scan run | Logic normalize phải trả `Optional`/`null` thay vì throw; catch tại điểm gọi trong `ArtifactScannerService` |
| 3   | BR-SDD-LEAD-TIME-9: ghi lại mỗi lần scan, đè giá trị cũ (kể cả về `NULL`) | Port method luôn `UPDATE` (không `COALESCE`/giữ giá trị cũ) |
| 4   | OI-SDD-LEAD-TIME-3: duration hiển thị `-` khi 1 trong 2 mốc null hoặc `completed_at < started_at` | FE helper phải có 2 nhánh guard rõ ràng, không dựa vào exception |
| 5   | Spec-pack §12: không đổi định nghĩa cột `started_at`, không đụng `closed_at`/`ck_ticket_date_order` | Migration `V513` chỉ thêm cột mới, không ALTER cột khác, không thêm constraint mới |

---

## 2. Impact analysis

### 2.1. Files / modules có thể bị ảnh hưởng

| #   | File / module | Loại thay đổi | Lý do ảnh hưởng | AC liên quan | Ghi chú |
| --- | --- | --- | --- | --- | --- |
| 1   | `EDCAP_BE/.../scanner/ArtifactScannerService.java` (persistSpecPackParse 515-621, persistReportParse 893-1027) | Sửa | Đọc `headerMetadata`, chuẩn hoá, gọi port method mới | AC-1, AC-2, AC-6, AC-8, AC-12 | Không đổi transaction hiện có; thêm block mới, dùng `TransactionTemplate` riêng cho update ticket-date |
| 2   | `EDCAP_BE/.../port/out/persistence/ArtifactScannerPersistencePort.java` | Thêm | Chưa có method update `started_at`/`completed_at` (xác nhận, đọc đủ 32 method) | AC-1, AC-2 | `updateTicketLeadTimeDates(UUID ticketId, OffsetDateTime startedAt, OffsetDateTime completedAt)` |
| 3   | `ArtifactScannerJdbcAdapter.java` (`EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/scanner/ArtifactScannerJdbcAdapter.java`) | Sửa | Implement 2 UPDATE method riêng (`updateTicketStartedAt`/`updateTicketCompletedAt`) — IMPL-OI-1, closed | AC-1, AC-2, AC-6 | 1 `TransactionTemplate` riêng cho mỗi lần gọi, theo `.claude/rules/20-architecture.md` |
| 4   | `EDCAP_BE/src/main/resources/db/migration/V513__add_completed_at_to_tbl_dim_ticket.sql` (mới) | Thêm | Cột `completed_at` chưa tồn tại | AC-2 | `ALTER TABLE tbl_dim_ticket ADD COLUMN completed_at TIMESTAMPTZ;` nullable, không default |
| 5   | Utility chuẩn hoá ngày mới, ví dụ `EDCAP_BE/.../domain/service/markdown/core/HeaderDateNormalizer.java` (tên/path xác nhận đầu Phase 4) | Thêm | Cần 1 nơi duy nhất implement BR-3 | AC-5, AC-6 | Nằm trong `domain/service/markdown` để không phá hexagonal boundary |
| 6   | `EDCAP_BE/.../persistence/adapter/PmDashboardJdbcAdapter.java` (findDetail 216-271, baseTicketQuery 399-437, mapTicketRow 477-512) | Sửa | Cần trả `started_at`/`completed_at` cho FE | AC-9, AC-10 | SQL trùng lặp ở 2 nơi — phải sửa cả hai, xác minh bằng test regression |
| 7   | `EDCAP_BE/.../pmdashboard/PmDashboardModels.java` (`DashboardTicketRow` 117-216) | Sửa | Thêm 2 field `startedAt`/`completedAt` vào cả canonical và compact constructor | AC-9 | Trước khi sửa, grep tất cả call site dùng compact constructor để tránh default sai |
| 8   | `EDCAP_FE/src/lib/api.ts` (`PmDashboardTicketRow` 549-583) | Sửa | FE type cần 2 field mới | AC-9 | `startedAt: string \| null`, `completedAt: string \| null`, theo style `mergedAt` |
| 9   | `EDCAP_FE/src/pages/pm-dashboard/components/TicketDetailDrawer.tsx` (`TicketInformationCard` 236-291) | Sửa | Hiển thị 3 field mới | AC-3, AC-4, AC-7, AC-9, AC-11 | Copy pattern `mergedAt` cho conditional render |
| 10  | Utility FE thuần mới, ví dụ `EDCAP_FE/src/utils/leadTime.ts` (path xác nhận đầu Phase 4) | Thêm | `computeLeadTimeDuration(startedAt, completedAt)` | AC-9, AC-11 | Pure function — không đặt trong Redux/Zustand |
| 11  | `EDCAP_FE/public/locales/{en,vi,ja}/locale.json` | Sửa | 3 key i18n mới dưới `Pages.PmDashboard.*` | AC-7 | Không backfill 3 key cũ trong `ja` (không phải gap thật — xem §1.2 mục 7) |

### 2.2. API contract có thể bị ảnh hưởng

| #   | API / endpoint / service | Loại thay đổi | Request impact | Response impact | Error handling | AC liên quan | Trạng thái |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1   | `GET /api/v1/pm/dashboard/tickets/{ticketId}/detail` | Update | Không đổi | Thêm `startedAt`, `completedAt` (nullable ISO-8601 string) vào ticket-row object | Không có case lỗi mới — cả hai field luôn có thể null | AC-1, AC-2, AC-3, AC-4, AC-9, AC-10 | Confirmed |

### 2.3. DB / migration có thể bị ảnh hưởng

| #   | Bảng / collection / migration | Loại thay đổi | Field / index / constraint | Data migration | Rollback DB | AC liên quan | Trạng thái |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1   | `tbl_dim_ticket` / `V513__add_completed_at_to_tbl_dim_ticket.sql` | Add | `completed_at TIMESTAMPTZ` nullable, không default, không index mới | Không có — populate qua scan cadence bình thường (H-3, closed) | Manual `ALTER TABLE tbl_dim_ticket DROP COLUMN completed_at;` (không tự động qua Flyway `migrate`) | AC-2, AC-4, AC-6, AC-12 | Confirmed |

### 2.4. Settings / config / feature flags

Không có config/feature flag mới theo spec-pack — tính năng luôn bật, không cần rollout theo flag
(đủ an toàn vì additive/nullable).

### 2.5. Logs / audit / monitoring

| #   | Event / log / metric | Khi nào ghi | Payload chính | Privacy / permission concern | AC/NFR liên quan | Ghi chú |
| --- | --- | --- | --- | --- | --- | --- |
| 1   | WARN log khi header date field missing/malformed | Trong `persistSpecPackParse`/`persistReportParse`, khi normalize trả `null` cho field có file tồn tại nhưng giá trị không parse được | `ticketId`, `sourcePath`, raw giá trị field | Không có — chỉ là ngày tháng nội bộ, không phải PII | BR-SDD-LEAD-TIME-6 | Theo đúng convention log hiện có của Artifact Scanner cho case thiếu artifact khác — không escalate thành lỗi |

### 2.6. Permissions / roles

Không đổi — tính năng chỉ mở rộng 1 view read-only đã có, dùng session-based auth hiện tại
(spec-pack §13). Không có role/permission mới.

### 2.7. Backward compatibility / data compatibility

| #   | Compatibility point | Rủi ro | Cách xử lý | AC/NFR liên quan |
| --- | --- | --- | --- | --- |
| 1   | `started_at` bắt đầu được populate rộng rãi (trước đây thường unset) | Consumer khác của `started_at` (vd: order/sort trong `PmDashboardJdbcAdapter`) có thể thay đổi kết quả hiển thị dù logic không đổi | Regression test xác nhận hành vi order/filter hiện có không đổi cách xử lý, chỉ dữ liệu đầu vào đổi | Spec-pack §2.2, §6.6 Compatibility, AC-10 |
| 2   | `DashboardTicketRow` compact constructor thêm 2 field mới | Caller cũ dùng compact constructor có thể nhận `null` không mong muốn nếu không update đúng | Grep toàn bộ call site trước khi sửa constructor (bước A.5 bên dưới) | — |

---

## 3. Existing code cần đọc trước

> Đã đọc đầy đủ trong Phase 3 exploration; liệt kê lại để làm checklist trước khi code.

### 3.1. Danh sách code cần đọc

| #   | File / module cần đọc | Mục đích đọc | Câu hỏi cần trả lời trước khi sửa | Liên quan AC |
| --- | --- | --- | --- | --- |
| 1   | `MarkdownParserCore.java` (103-117) | Xác nhận `extractHeaderMetadata()` hoạt động đúng như mô tả | Format key đã lowercase + underscore đúng chưa (`create_date`, `update_date`)? | AC-1, AC-2 |
| 2   | `SpecPackMarkdownParser.java` / `ReportMarkdownParser.java` (ParsedArtifact record) | Xác nhận `headerMetadata` đã có sẵn, không cần sửa parser | `headerMetadata` có null-safe (`Map` rỗng thay vì null) không? | AC-1, AC-2, AC-6 |
| 3   | `ArtifactScannerService.java` (persistSpecPackParse, persistReportParse) | Tìm đúng điểm chèn logic mới | `ticket.ticketId()` có sẵn tại đây để gọi port method không? | AC-1, AC-2, AC-8 |
| 4   | `ArtifactScannerPersistencePort.java` (full, 32 method) | Xác nhận chưa có method phù hợp | Có method tên gần giống cần tái sử dụng thay vì thêm mới không? (Đã xác nhận: không) | AC-1, AC-2 |
| 5   | `V4__init_shema_v2.sql` (174-193) | Xác nhận schema `tbl_dim_ticket`, `ck_ticket_date_order` | Constraint có tham chiếu `started_at`/`completed_at` không? (Đã xác nhận: không) | AC-2 |
| 6   | `PmDashboardJdbcAdapter.java` (findDetail, baseTicketQuery, mapTicketRow) | Xác nhận 2 nơi SQL cần sửa đồng thời | Có nơi thứ 3 nào cũng SELECT ticket columns cần sửa không? | AC-9, AC-10 |
| 7   | `PmDashboardModels.java` (DashboardTicketRow, DashboardTicketDetail) | Xác nhận vị trí thêm field, tất cả constructor | Có caller nào khác dùng compact constructor ngoài `PmDashboardJdbcAdapter`? | AC-9 |
| 8   | `TicketDetailDrawer.tsx` (236-291) | Xác nhận pattern render chính xác để copy | `mergedAt` conditional block có applicable trực tiếp cho duration không (3 điều kiện thay vì 1)? | AC-3, AC-4, AC-7, AC-9, AC-11 |
| 9   | `EDCAP_FE/src/lib/utils.ts` (formatDateTime) | Xác nhận không cần viết formatter mới | Hàm đã handle `null`/`undefined` → `-` chưa? (Đã xác nhận: có) | AC-3, AC-4 |
| 10  | `EDCAP_FE/public/locales/{en,vi,ja}/locale.json` (Pages.PmDashboard) | Xác nhận key hiện có, tránh trùng key mới | 3 key mới đặt tên gì để không đụng key cũ? | AC-7 |

### 3.2. Thứ tự đọc đề xuất

1. `ArtifactScannerService.java` — xác định flow chính và 2 điểm tích hợp.
2. `ArtifactScannerPersistencePort.java` + JDBC impl — xác định state/persistence hiện tại.
3. `PmDashboardJdbcAdapter.java` + `PmDashboardModels.java` — xác định API/DTO hiện tại.
4. `TicketDetailDrawer.tsx` — xác định behavior UI cần thay đổi.
5. Test hiện có cho `ArtifactScannerService`/`PmDashboardJdbcAdapter`/`TicketDetailDrawer` (path xác nhận đầu Phase 4) — xác định regression risk.

### 3.3. Điều kiện để mở rộng phạm vi đọc

Giữ nguyên điều kiện chuẩn: chỉ thêm file nếu được import trực tiếp bởi file đã đọc, chứa
type/API/constant liên quan AC, có test fail/liên quan, hoặc spec yêu cầu kiểm tra
permission/log/config/DB ở khu vực đó. Cập nhật bảng 3.1 nếu phát sinh.

---

## 4. Changes / thiết kế thay đổi

### 4.0. Tổng quan thay đổi

| #   | Nhóm AC | Hiện trạng | Thay đổi | State mới / chỉnh | API/DB |
| --- | --- | --- | --- | --- | --- |
| 4.1 | AC-1, AC-2, AC-5, AC-6, AC-8, AC-12 (BE parse/persist) | `headerMetadata` được parse nhưng không dùng; `started_at` thường unset, `completed_at` chưa tồn tại | Đọc `headerMetadata`, chuẩn hoá, ghi vào `tbl_dim_ticket` mỗi lần scan | `ArtifactScannerPersistencePort.updateTicketLeadTimeDates` | `tbl_dim_ticket.started_at`/`completed_at`; migration V513 |
| 4.2 | AC-9, AC-10 (BE contract) | Ticket-detail response không có `startedAt`/`completedAt` | Thêm 2 field nullable vào response | `DashboardTicketRow.startedAt/completedAt` | `GET .../tickets/{id}/detail` |
| 4.3 | AC-3, AC-4, AC-7, AC-9, AC-11 (FE display) | `TicketInformationCard` không có 3 field này | Thêm 3 row + 1 pure helper tính duration | `PmDashboardTicketRow.startedAt/completedAt` (FE type) | none (đọc từ API ở 4.2) |

---

### 4.1. BE parse/persist — AC-1, AC-2, AC-5, AC-6, AC-8, AC-12

**Diff hành vi (ArtifactScannerService):**

```diff
  // persistSpecPackParse(...)
  updateSnapshotParsedSummary(...);
  deactivateAcceptanceCriteriaByTicketId(ticket.ticketId());
  ...
+ OffsetDateTime startedAt = HeaderDateNormalizer.normalize(
+     parsed.headerMetadata().get("create_date"));
+ persistence.updateTicketStartedAt(ticket.ticketId(), startedAt);

  // persistReportParse(...)
  ...
+ OffsetDateTime completedAt = HeaderDateNormalizer.normalize(
+     parsed.headerMetadata().get("update_date"));
+ persistence.updateTicketCompletedAt(ticket.ticketId(), completedAt);
```

**Design notes:**

- IMPL-OI-1 closed: dùng **2 method port riêng** (`updateTicketStartedAt(UUID, OffsetDateTime)` /
  `updateTicketCompletedAt(UUID, OffsetDateTime)`) thay vì 1 method chung nhận cả 2 tham số — mỗi
  method chỉ `UPDATE` đúng 1 cột, loại bỏ hoàn toàn rủi ro ghi đè nhầm cột còn lại (Rủi ro #3, mục 6).
  Cả 2 implement trong `ArtifactScannerJdbcAdapter.java` (IMPL-OI-3, closed).
- Luôn gọi normalize + update kể cả khi kết quả là `null` (BR-SDD-LEAD-TIME-6, H-4) — không skip câu
  UPDATE khi giá trị null.
- Không throw exception ra khỏi block này — bọc bằng try/catch nội bộ nếu cần, log WARN, tiếp tục scan.

---

### 4.2. BE contract — AC-9, AC-10

**Diff hành vi (PmDashboardJdbcAdapter + PmDashboardModels):**

```diff
  SELECT ..., t.started_at, t.updated_at AS updated_at_source, ... FROM tbl_dim_ticket t
+ , t.completed_at
  -- áp dụng đồng thời cho findDetail's inline SQL và baseTicketQuery()

  record DashboardTicketRow(
      ...,
      OffsetDateTime updatedAt,
+     OffsetDateTime startedAt,
+     OffsetDateTime completedAt,
      String mergedAt-or-existing-trailing-fields...)
```

**Design notes:**

- Sửa cả `findDetail`'s inline SQL và `baseTicketQuery()` trong cùng 1 commit để tránh lệch dữ liệu
  giữa 2 endpoint dùng chung logic.
- `mapTicketRow` thêm đúng 2 dòng `rs.getObject("started_at"/"completed_at", OffsetDateTime.class)`
  theo pattern đã dùng cho `merged_at`.
- Compact constructor của `DashboardTicketRow`: xác nhận call site trước khi quyết định giá trị mặc
  định cho 2 field mới (khuyến nghị mặc định `null`, giống cách `mergedAt` hiện đang mặc định `null`).

---

### 4.3. FE display — AC-3, AC-4, AC-7, AC-9, AC-11

**Diff hành vi (TicketDetailDrawer.tsx):**

```diff
  {detail.row.mergedAt ? (
    <InfoRow label={t("Pages.PmDashboard.mergedAt", {defaultValue: "Merged at"})}
              value={formatDateTime(detail.row.mergedAt)} />
  ) : null}
+ <InfoRow label={t("Pages.PmDashboard.startedAt", {defaultValue: "Spec-pack created"})}
+           value={formatDateTime(detail.row.startedAt)} />
+ <InfoRow label={t("Pages.PmDashboard.completedAt", {defaultValue: "Report updated"})}
+           value={formatDateTime(detail.row.completedAt)} />
+ <InfoRow label={t("Pages.PmDashboard.leadTimeDuration", {defaultValue: "Duration"})}
+           value={computeLeadTimeDuration(detail.row.startedAt, detail.row.completedAt, i18n.language)} />
```

**Design notes:**

- `computeLeadTimeDuration` trả `"-"` (literal, không qua i18n) khi 1 trong 2 input null hoặc
  `completedAt < startedAt`, theo đúng OI-SDD-LEAD-TIME-3.
- Format `"1d 8h"`-style phải localize qua i18n hiện có (OI-SDD-LEAD-TIME-2) — không hardcode đơn vị
  tiếng Anh; tận dụng cùng cơ chế ngôn ngữ mà `formatDateTime` đang dùng (đọc `i18nextLng` từ
  localStorage) để nhất quán.
- 3 key i18n chính thức (IMPL-OI-2, closed): `Pages.PmDashboard.startedAt`, `Pages.PmDashboard.completedAt`,
  `Pages.PmDashboard.leadTimeDuration` — thêm vào cả 3 file locale, không đụng key cũ.

---

## 5. Implementation steps

### Part A — BE parse/persist (AC-1, AC-2, AC-5, AC-6, AC-8, AC-12)

| #   | Step nhỏ, review được | Files dự kiến chỉnh | Output mong đợi | Cách xác minh | AC đáp ứng |
| --- | --- | --- | --- | --- | --- |
| A.1 | Thêm shared date-normalization utility (bare-date → `00:00:00`; malformed/empty/whitespace/full-width digit → `null`) | `EDCAP_BE/.../domain/service/markdown/core/HeaderDateNormalizer.java` (mới) | Utility method `normalize(String raw): OffsetDateTime` | Unit test: full datetime passthrough, bare-date, malformed, empty, whitespace, full-width digit | AC-5, AC-6 |
| A.2 | Thêm 2 method mới vào `ArtifactScannerPersistencePort` (IMPL-OI-1, closed — 2 method riêng, không 1 method chung) | `ArtifactScannerPersistencePort.java` | 2 method interface: `updateTicketStartedAt(UUID, OffsetDateTime)`, `updateTicketCompletedAt(UUID, OffsetDateTime)` | Compile + review | AC-1, AC-2 |
| A.3 | Implement 2 method ở A.2 trong `ArtifactScannerJdbcAdapter` (IMPL-OI-3, closed), dùng `TransactionTemplate` riêng cho mỗi method | `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/scanner/ArtifactScannerJdbcAdapter.java` | 2 câu `UPDATE tbl_dim_ticket SET started_at = ? WHERE ticket_id = ?` / `... SET completed_at = ? ...` riêng biệt | Integration test: update thành công, update về `NULL` thành công, cột còn lại không đổi | AC-1, AC-2, AC-6 |
| A.4 | Migration `V513` | `V513__add_completed_at_to_tbl_dim_ticket.sql` (mới) | Cột `completed_at` tồn tại, nullable, không default | `mvn flyway:info`/test migration chạy sạch trên DB test (chỉ chạy sau khi user xác nhận theo `.claude/rules/00-safety.md §3`) | AC-2 |
| A.5 | Gọi A.1 + A.3 tại `persistSpecPackParse`/`persistReportParse`, luôn overwrite kể cả về null, không throw ra ngoài | `ArtifactScannerService.java` | `started_at`/`completed_at` được ghi mỗi lần scan | Integration test: scan lần 1 có giá trị, scan lần 2 field bị xoá → cột về `NULL`; file thiếu → cột `NULL`; run không fail | AC-1, AC-2, AC-6, AC-8, AC-12 |

### Part B — BE contract (AC-9, AC-10)

| #   | Step nhỏ, review được | Files dự kiến chỉnh | Output mong đợi | Cách xác minh | AC đáp ứng |
| --- | --- | --- | --- | --- | --- |
| B.1 | Grep toàn bộ call site của `DashboardTicketRow`'s compact constructor | (đọc, không sửa) | Danh sách caller xác nhận an toàn khi thêm 2 field mặc định `null` | Review kết quả grep | — (pre-check cho B.2) |
| B.2 | Thêm `startedAt`/`completedAt` vào `DashboardTicketRow` (cả 2 constructor) | `PmDashboardModels.java` | Record có 2 field mới, compile sạch | Unit test dựng `DashboardTicketRow` qua cả 2 constructor | AC-9 |
| B.3 | Thêm cột vào SQL của `findDetail` và `baseTicketQuery()` + `mapTicketRow` | `PmDashboardJdbcAdapter.java` | Cả 2 endpoint trả `startedAt`/`completedAt` nhất quán | Integration test: gọi cả `findDetail` và `findTickets`, so sánh giá trị cho cùng 1 ticket | AC-9, AC-10 |
| B.4 | Regression test cho hành vi `started_at`-order hiện có | Test file tương ứng `PmDashboardJdbcAdapter` (path xác nhận Phase 4) | Không có thay đổi hành vi sort/filter | Test pass, so sánh trước/sau | AC-10 |

### Part C — FE display (AC-3, AC-4, AC-7, AC-9, AC-11)

| #   | Step nhỏ, review được | Files dự kiến chỉnh | Output mong đợi | Cách xác minh | AC đáp ứng |
| --- | --- | --- | --- | --- | --- |
| C.1 | Thêm `startedAt`/`completedAt` vào `PmDashboardTicketRow` | `EDCAP_FE/src/lib/api.ts` | Type có 2 field mới, nullable | Typecheck (`npm run typecheck`) | AC-9 |
| C.2 | Thêm pure helper `computeLeadTimeDuration` | `EDCAP_FE/src/utils/leadTime.ts` (mới) | Hàm null-safe, order-safe, localized `"1d 8h"`-style | Unit test: cặp hợp lệ, 1 input null, `completed < started` | AC-9, AC-11 |
| C.3 | Thêm 3 row vào `TicketInformationCard` | `TicketDetailDrawer.tsx` | 3 row hiển thị đúng format + fallback `-` | Component test + manual UI check | AC-3, AC-4, AC-7, AC-9 |
| C.4 | Thêm 3 key i18n vào `en`/`vi`/`ja` | `EDCAP_FE/public/locales/{en,vi,ja}/locale.json` | Không hardcode text, không đụng key cũ | i18n review + snapshot test | AC-7 |

---

## 6. Risks & mitigation

| #   | Rủi ro | Tác động | Khả năng | Biện pháp giảm thiểu | Cách phát hiện sớm | Owner |
| --- | --- | --- | --- | --- | --- | --- |
| 1   | SQL trùng lặp giữa `findDetail` và `baseTicketQuery()` chỉ sửa 1 nơi | Medium | Medium | Step B.3 sửa đồng thời cả 2, step B.4 test so sánh 2 endpoint | Integration test B.3/B.4 fail nếu lệch | BE |
| 2   | Compact constructor của `DashboardTicketRow` default sai cho caller chưa biết | Medium | Low | Step B.1 grep trước khi sửa | Compile error hoặc unit test B.2 fail | BE |
| 3   | Ghi UPDATE nhầm cột (ghi đè `started_at` khi chỉ định cập nhật `completed_at` hoặc ngược lại) | High | Low | Chọn 2 method port riêng biệt (A.2) thay vì 1 method dùng chung tham số nullable dễ nhầm | Integration test A.3/A.5 kiểm tra cột còn lại không đổi | BE |
| 4   | `ck_ticket_date_order` hoặc constraint khác chặn migration V513 | Low | Low | Đã xác nhận constraint chỉ tham chiếu `closed_at`/`created_at`, không liên quan | Migration test A.4 chạy sạch | BE/DBA |
| 5   | Không có ArchUnit test (`LayerEnforcementTest.java`) thực sự tồn tại để tự động verify layer boundary cho port/adapter mới (IMPL-OI-4, closed — gap có sẵn của hệ thống, tài liệu `test-map.md` bị stale) | Medium | Medium | Review thủ công layer boundary (application vs infrastructure) cho A.2/A.3 khi PR review, theo `.claude/rules/20-architecture.md`; không tự viết ArchUnit test mới trong ticket này (ngoài phạm vi) | Code review checklist tại PR | BE reviewer |

---

## 7. Rollback plan

### 7.1. Code rollback

1. Revert theo thứ tự ngược: Part C (FE) → Part B (BE contract) → Part A (BE parse/persist).
2. Không có feature flag — revert code là đủ, không cần bước tắt flag riêng.
3. Không có state/cache client cần clear — dữ liệu chỉ đọc từ API mỗi lần mở drawer.

### 7.2. DB rollback

| #   | DB change | Rollback action | Data loss risk | Owner |
| --- | --- | --- | --- | --- |
| 1   | `V513` thêm cột `completed_at` | Manual `ALTER TABLE tbl_dim_ticket DROP COLUMN completed_at;` (không tự động qua Flyway) | Thấp — cột hoàn toàn derived, sẽ tự populate lại ở lần scan tiếp theo nếu thêm lại cột | DBA (yêu cầu xác nhận người dùng trước khi chạy, theo `.claude/rules/00-safety.md §3`) |

### 7.3. Config / feature flag rollback

Không áp dụng — không có config/feature flag mới trong ticket này.

---

## 8. Verification procedure

### 8.1. Automated verification

```bash
# BE (chạy trong EDCAP_BE/)
mvn clean verify

# FE (chạy trong EDCAP_FE/)
npm run typecheck
npm run build
npm test
```

### 8.2. Manual verification matrix

| #   | Scenario | Preconditions | Steps | Expected result | AC/NFR |
| --- | --- | --- | --- | --- | --- |
| 1   | Cả 2 file có date hợp lệ | Ticket có `spec-pack.md`/`report.md` với header date đúng format | Trigger scan, mở Ticket Detail drawer | Hiển thị đúng created/updated time + duration `"1d 8h"`-style | AC-1, AC-2, AC-9 |
| 2   | `report.md` không tồn tại | Ticket chỉ có `spec-pack.md` | Trigger scan, mở drawer | `completed_at` = `-`, duration = `-`, `started_at` vẫn hiển thị | AC-4, AC-11 |
| 3   | Field bị sửa thành text không phải ngày | Ticket đã có `started_at` hợp lệ từ lần scan trước | Sửa `**Create date**: TBD`, trigger scan lại | `started_at` = `NULL`, UI hiển thị `-`, scan không fail | AC-6 |
| 4   | `completed_at` sớm hơn `started_at` | Chỉnh `report.md`'s update date về trước `spec-pack.md`'s create date | Trigger scan, mở drawer | Cả 2 timestamp hiển thị bình thường, duration = `-` | AC-11 |

### 8.3. Regression checks

| #   | Existing behavior cần giữ | Check | Expected result |
| --- | --- | --- | --- |
| 1   | Thứ tự/behavior hiện có của `PmDashboardJdbcAdapter` dựa trên `started_at` | Chạy lại test/query hiện có trước và sau khi `started_at` được populate rộng rãi | Logic sort/filter không đổi, chỉ dữ liệu đầu vào thay đổi |
| 2   | `ArtifactScannerService` scan run không bị fail bởi field mới | Chạy scan trên fixture có field malformed | Scan hoàn tất thành công cho toàn bộ ticket, chỉ field liên quan bị null |

---

## 9. Open Issues / Cần xác nhận trước khi implementation

| ID        | Chủ đề | Câu hỏi cần chốt | Ai chốt | Block bước | Mức độ block | Trạng thái |
| --------- | --------- | ---------------- | --------- | ------------ | ----------------------- | ---------- |
| IMPL-OI-1 | Port method shape | Dùng 2 method riêng (`updateTicketStartedAt`/`updateTicketCompletedAt`) hay 1 method chung nhận cả 2 tham số nullable? | BE lead | A.2, A.3, A.5 | Non-blocking | **Closed** — user xác nhận 2026-08-21: dùng **2 method riêng** (`updateTicketStartedAt(UUID, OffsetDateTime)` / `updateTicketCompletedAt(UUID, OffsetDateTime)`), tránh UPDATE ghi đè nhầm cột (Rủi ro #3, mục 6) |
| IMPL-OI-2 | Tên key i18n cuối cùng | Tên chính xác cho 3 key mới | FE lead | C.3, C.4 | Non-blocking | **Closed** — user xác nhận 2026-08-21: tên key chính thức là `Pages.PmDashboard.startedAt`, `Pages.PmDashboard.completedAt`, `Pages.PmDashboard.leadTimeDuration` |
| IMPL-OI-3 | JDBC adapter path cho port implementation | Xác nhận đúng class implement `ArtifactScannerPersistencePort` trước khi thêm method | BE lead | A.3 | Blocking cho A.3 | **Closed** — đọc code xác nhận: `ArtifactScannerJdbcAdapter` (`EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/scanner/ArtifactScannerJdbcAdapter.java:31`, `implements ArtifactScannerPersistencePort`) là adapter duy nhất — 2 method mới ở IMPL-OI-1 được implement tại đây |
| IMPL-OI-4 | ArchUnit test path | Xác nhận path/tên `ArchitectureTest` để chạy verify trước khi merge | BE lead | A.2, A.3 (verify) | Blocking cho merge | **Closed, với phát hiện quan trọng** — đọc `pom.xml` xác nhận dependency `com.tngtech.archunit:archunit-junit5:1.3.0` (test scope) tồn tại, nhưng **không có file test ArchUnit nào thực sự tồn tại** trong `EDCAP_BE/src/test/**` (đã glob toàn bộ `src/test/java/com/sdd/platform/**`, không có package `architecture` hay class nào chứa `Arch`/`Layer`). Tài liệu `docs/architecture/test-map.md:46,235` trỏ tới `EDCAP_BE/src/test/java/com/sdd/platform/architecture/LayerEnforcementTest.java` — **file này không tồn tại**, tài liệu bị stale. Hệ quả cho ticket này: không có ArchUnit test tự động để chạy verify layer boundary; review layer boundary (application vs infrastructure cho port/adapter mới) phải làm bằng **code review thủ công** thay vì `mvn test` tự động. Đây là gap có sẵn của hệ thống, không phải lỗi phát sinh từ ticket này — không mở rộng scope để tự viết `LayerEnforcementTest.java` mới (ngoài phạm vi SDD-LEAD-TIME); ghi nhận thành rủi ro tồn đọng ở mục 6. |

### Checklist xác nhận trước khi bắt đầu implementation

- [x] Tất cả AC trong `spec-pack.md` đã được đưa vào bảng mapping mục 10.
- [x] Không có implementation step nào nằm ngoài spec-pack.
- [x] Existing code cần đọc đã được liệt kê trước ở mục 3.
- [x] Các Open Issue blocking đã có owner và bước bị block rõ ràng.
- [x] API contract đã chốt (additive, spec-pack §11).
- [x] DB migration / rollback đã chốt (§2.3, §7.2).
- [x] Permission / role behavior đã chốt (không đổi).
- [x] Log / audit / monitoring đã chốt (§2.5).
- [x] Test plan đủ cover AC chính và regression quan trọng (§8).

---

## 10. AC mapping table

| #   | AC ID | Nội dung AC tóm tắt | Implementation step đáp ứng | Files/modules liên quan | Verification | Open Issue nếu có |
| --- | --- | --- | --- | --- | --- | --- |
| 1   | AC-SDD-LEAD-TIME-1 | `spec-pack.md` hợp lệ → `started_at` populated sau scan | A.1, A.2, A.3, A.5 | `ArtifactScannerService.java`, port + JDBC impl | DB integration test | IMPL-OI-1, IMPL-OI-3 |
| 2   | AC-SDD-LEAD-TIME-2 | `report.md` hợp lệ → `completed_at` populated sau scan | A.1, A.2, A.3, A.4, A.5 | Như trên + migration V513 | DB integration test | IMPL-OI-1, IMPL-OI-3 |
| 3   | AC-SDD-LEAD-TIME-3 | `spec-pack.md` không tồn tại → `started_at` NULL, UI `-` | A.5, C.3 | `ArtifactScannerService.java`, `TicketDetailDrawer.tsx` | Integration + UI test | — |
| 4   | AC-SDD-LEAD-TIME-4 | `report.md` không tồn tại → `completed_at` NULL, UI `-` | A.5, C.3 | Như trên | Integration + UI test | — |
| 5   | AC-SDD-LEAD-TIME-5 | Bare date → `00:00:00` | A.1 | `HeaderDateNormalizer` | Unit test | — |
| 6   | AC-SDD-LEAD-TIME-6 | Malformed → NULL, scan không fail | A.1, A.3, A.5 | Như A.1/A.3/A.5 | Unit + integration test (fixture malformed) | — |
| 7   | AC-SDD-LEAD-TIME-7 | 3 field hiển thị với i18n label, không hardcode | C.3, C.4 | `TicketDetailDrawer.tsx`, locale files | UI/i18n review + snapshot test | IMPL-OI-2 |
| 8   | AC-SDD-LEAD-TIME-8 | Re-scan sau khi `report.md` đổi → `completed_at` cập nhật | A.5 | `ArtifactScannerService.java` | Integration test (2 lần scan) | — |
| 9   | AC-SDD-LEAD-TIME-9 | Duration tính từ `completed_at - started_at`, hiển thị cùng 2 timestamp | B.2, B.3, C.1, C.2, C.3 | `PmDashboardModels.java`, `PmDashboardJdbcAdapter.java`, `api.ts`, `leadTime.ts`, `TicketDetailDrawer.tsx` | UI + unit test | IMPL-OI-2 |
| 10  | AC-SDD-LEAD-TIME-10 | `PmDashboardJdbcAdapter` behavior hiện có với `started_at` không regress | B.4 | `PmDashboardJdbcAdapter.java` | Regression test | — |
| 11  | AC-SDD-LEAD-TIME-11 | Duration `-` khi null hoặc `completed_at < started_at` | C.2 | `leadTime.ts` | FE unit test (null-safety, ordering) | — |
| 12  | AC-SDD-LEAD-TIME-12 | Ticket cũ populate qua scan cadence bình thường, không cần backfill riêng | A.5 | `ArtifactScannerService.java` | Integration test (re-scan ticket có sẵn) | — |

---

## 11. Output

Sau khi hoàn tất Phase 3, output đã tạo:

- `docs/changes/SDD-LEAD-TIME/source-availability.md`
- `docs/changes/SDD-LEAD-TIME/source-inventory.md`
- `docs/changes/SDD-LEAD-TIME/impact-analysis.md`
- `docs/changes/SDD-LEAD-TIME/impl-plan.md` (file này)
- Checklist các điều cần xác nhận trước khi implementation bắt đầu — mục 9 ở trên; tất cả 4 Open
  Issue (IMPL-OI-1 đến IMPL-OI-4) đã được user xác nhận và **đóng** ngày 2026-08-21. Không còn Open
  Issue nào chặn Phase 4; điểm cần lưu ý duy nhất là gap có sẵn của hệ thống (không có ArchUnit test
  chạy được, IMPL-OI-4) được ghi nhận thành rủi ro theo dõi ở mục 6, không phải điều kiện chặn.
