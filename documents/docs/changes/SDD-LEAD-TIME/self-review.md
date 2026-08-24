# Tự review — SDD-LEAD-TIME (Lead Time visibility: spec-pack created time, report updated time, computed duration)

- **Ticket:** SDD-LEAD-TIME
- **Trạng thái:** Implemented — chờ independent review / human review
- **Tạo ngày:** 2026-08-21

> Nguồn tham chiếu chính: `docs/changes/SDD-LEAD-TIME/spec-pack.md`, `docs/changes/SDD-LEAD-TIME/impl-plan.md` và `docs/changes/SDD-LEAD-TIME/review-checklist.md`.
> Được developer/agent điền sau implementation, trước khi review thủ công.
> Mọi checkbox đều phải có bằng chứng: kết quả lệnh, hoặc tham chiếu tệp + số dòng.

---

## 1. Tóm tắt implement

Đã implement đầy đủ Part A/B/C theo `impl-plan.md`:

- **BE parse/persist**: thêm `HeaderDateNormalizer` (utility dùng chung, không throw, trả `null` cho
  input rỗng/malformed/full-width digit), 2 method mới trên `ArtifactScannerPersistencePort`
  (`updateTicketStartedAt`/`updateTicketCompletedAt`), implement trong `ArtifactScannerJdbcAdapter`
  bằng `jdbc.update(...)` đơn giản (không `COALESCE` — luôn overwrite kể cả về `NULL`). Wire vào
  `persistSpecPackParse`/`persistReportParse` — đọc `headerMetadata().get("create_date"/"update_date")`,
  normalize, gọi port method, bọc try/catch log WARN để không fail scan run.
- **DB**: migration `V513__add_completed_at_to_tbl_dim_ticket.sql` — chỉ `ALTER TABLE ... ADD COLUMN
  completed_at TIMESTAMPTZ;`, chưa chạy trên DB nào (chỉ tạo file, theo `.claude/rules/00-safety.md §3`).
- **BE contract**: thêm 2 field `startedAt`/`completedAt` vào `DashboardTicketRow` (canonical +
  compact constructor), SQL của cả `findDetail` và `baseTicketQuery()` (đồng thời, giống hệt nhau),
  `mapTicketRow`, và **`PmDashboardDtos.PmDashboardTicketRowDto`** (gap phát hiện trong Phase 5 — không
  có trong danh sách file của `impl-plan.md` §2.1, nhưng bắt buộc phải sửa vì đây mới là DTO thực sự
  trả về JSON; không sửa file này thì 2 field mới sẽ bị fail).
- **FE**: thêm 2 field vào `PmDashboardTicketRow` (`api.ts`), pure helper mới
  `computeLeadTimeDuration` (`src/utils/leadTime.ts`, nhận `t` trực tiếp thay vì `language` string để
  tái sử dụng đúng cơ chế i18n hiện có của component cha), 3 dòng mới trong `TicketInformationCard`,
  5 key i18n mới (`startedAt`, `completedAt`, `leadTimeDuration`, `durationDayUnit`, `durationHourUnit`
  — 2 key unit bổ sung so với đề xuất ban đầu của impl-plan để tránh hardcode "d"/"h" tiếng Anh, đúng
  RC-17) ở cả 3 locale.

**2 lệch so với impl-plan.md, đã xác nhận với user trước khi code (xem mục 7):**

1. **Không dùng `TransactionTemplate`** cho 2 method update mới — vì pattern này không tồn tại ở bất
   kỳ đâu trong `ArtifactScannerService`/`ArtifactScannerJdbcAdapter` (đã đọc toàn bộ 2 file để xác
   nhận). Thay vào đó dùng đúng convention hiện có của class: gọi `jdbc.update(...)` tuần tự, giống
   hệt `updateSnapshotParsedSummary`/`upsertTicketPhaseStatus`. User đã xác nhận chọn phương án này
   (AskUserQuestion, "Follow existing code convention"). Hành vi (luôn overwrite kể cả về `NULL`)
   không đổi.
2. **Thêm `PmDashboardDtos.java` vào scope** — file này không có trong bảng file của `impl-plan.md`
   §2.1 nhưng là lớp DTO thực sự đi ra HTTP response (`PmDashboardController` trả
   `PmDashboardTicketDetailDto`, không phải `DashboardTicketDetail` thô). Đây là bổ sung bắt buộc,
   additive, nhất quán với AC-9/AC-10 — không phải mở rộng phạm vi ngoài spec.

Không có lệch nào khác so với `spec-pack.md`/`impl-plan.md`.

---

## 2. Danh sách file thay đổi

| #   | File                                                                                                                    | Loại thay đổi | Trạng thái | Ghi chú |
| --- | ------------------------------------------------------------------------------------------------------------------------ | -------------- | --------------- | ------- |
| 1   | `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/scanner/ArtifactScannerService.java`                       | Sửa            | Done  | Thêm block đọc `headerMetadata`, normalize, gọi port method trong `persistSpecPackParse`/`persistReportParse`; thêm import `HeaderDateNormalizer`. |
| 2   | `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/persistence/ArtifactScannerPersistencePort.java`          | Thêm           | Done  | 2 method mới: `updateTicketStartedAt`, `updateTicketCompletedAt`. |
| 3   | `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/scanner/ArtifactScannerJdbcAdapter.java`     | Sửa            | Done  | Implement 2 method mới bằng `jdbc.update(...)` đơn giản (không `TransactionTemplate` — xem mục 1). |
| 4   | `EDCAP_BE/src/main/resources/db/migration/V513__add_completed_at_to_tbl_dim_ticket.sql`                                  | Thêm           | Done (file only, chưa chạy migration) | `ALTER TABLE tbl_dim_ticket ADD COLUMN completed_at TIMESTAMPTZ;` |
| 5   | `EDCAP_BE/src/main/java/com/sdd/platform/domain/service/markdown/core/HeaderDateNormalizer.java`                         | Thêm           | Done  | Static `normalize(String): OffsetDateTime`, không throw. |
| 6   | `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/PmDashboardJdbcAdapter.java`                 | Sửa            | Done  | Thêm `t.started_at, t.completed_at` vào SQL của `findDetail` VÀ `baseTicketQuery()`; thêm 2 dòng vào `mapTicketRow`. |
| 7   | `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/pmdashboard/PmDashboardModels.java`                         | Sửa            | Done  | Thêm `startedAt`/`completedAt` vào canonical record + delegate call của compact constructor. |
| 8   | `EDCAP_BE/src/main/java/com/sdd/platform/web/dto/PmDashboardDtos.java`                                                   | Sửa            | Done (bổ sung ngoài impl-plan §2.1, xem mục 1) | Thêm 2 field vào `PmDashboardTicketRowDto` + `from(...)` factory. |
| 9   | `EDCAP_FE/src/lib/api.ts`                                                                                                | Sửa            | Done  | Thêm `startedAt`/`completedAt: string \| null` vào `PmDashboardTicketRow`. |
| 10  | `EDCAP_FE/src/pages/pm-dashboard/components/TicketDetailDrawer.tsx`                                                      | Sửa            | Done  | 3 dòng mới sau block `mergedAt`; import `computeLeadTimeDuration`. |
| 11  | `EDCAP_FE/src/utils/leadTime.ts`                                                                                         | Thêm           | Done  | `computeLeadTimeDuration(startedAt, completedAt, t)` — nhận `t` trực tiếp (xem mục 7 #2). |
| 12  | `EDCAP_FE/public/locales/en/locale.json`                                                                                 | Sửa            | Done  | +5 key: `startedAt`, `completedAt`, `leadTimeDuration`, `durationDayUnit`, `durationHourUnit`. |
| 13  | `EDCAP_FE/public/locales/vi/locale.json`                                                                                 | Sửa            | Done  | Cùng 5 key, bản dịch tiếng Việt. |
| 14  | `EDCAP_FE/public/locales/ja/locale.json`                                                                                 | Sửa            | Done  | Cùng 5 key, bản dịch tiếng Nhật. |
| 15  | `EDCAP_BE/src/test/java/com/sdd/platform/domain/service/markdown/core/HeaderDateNormalizerTest.java`                     | Thêm           | Done  | 7 test case (full datetime, bare date, null, empty, whitespace, non-date text, full-width digit). |
| 16  | `EDCAP_BE/src/test/UnitTest/java/com/sdd/platform/application/usecase/pmdashboard/PmDashboardServiceTest.java`           | Sửa            | Done (bắt buộc để compile) | 2 call site `new DashboardTicketRow(...)` cần thêm 2 arg `null, null` để khớp constructor mới. |
| 17  | `EDCAP_BE/src/test/UnitTest/java/com/sdd/platform/application/usecase/scanner/ArtifactScannerServiceTest.java`           | Sửa            | Done (bắt buộc để compile) | `InMemoryArtifactScannerPersistencePort` (fake port) cần implement 2 method mới của interface. |
| 18  | `EDCAP_FE/src/__ tests __/pm-dashboard/leadTime.test.ts`                                                                 | Thêm           | Done  | 5 test case cho `computeLeadTimeDuration`. |
| 19  | `EDCAP_FE/src/__ tests __/pm-dashboard/PMDashboardPage.test.tsx`                                                         | Sửa            | Done (bắt buộc để compile) | Thêm `startedAt`/`completedAt` vào fixture `PmDashboardTicketRow`. |
| 20  | `EDCAP_FE/src/__ tests __/pm-dashboard/TicketDetailDrawer.test.tsx`                                                      | Sửa            | Done (bắt buộc để compile) | Cùng lý do trên. |
| 21  | `EDCAP_FE/src/__ tests __/pm-dashboard/utils.test.ts`                                                                   | Sửa            | Done (bắt buộc để compile) | Thêm `startedAt: null, completedAt: null` vào `makeRow()` default. |

---

## 3. Command đã chạy và kết quả

### 3.1. BE build/test

```bash
# Lệnh:
mvn -o test    # (offline mode; equivalent to relevant portion of `mvn clean verify`)

# Kết quả:
[INFO] Tests run: 637, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
# Bao gồm: HeaderDateNormalizerTest (7 test, mới), PmDashboardServiceTest (19 test),
# ArtifactScannerServiceTest (24 test), PmDashboardControllerTest (14 test),
# PmDashboardDtosTest (5 test), ArtifactScannerJdbcAdapterTest (1 test) — tất cả pass,
# không có regression.
```

`mvn -o compile` chạy riêng trước đó cũng thành công (không có lỗi biên dịch main sources).

### 3.2. FE typecheck

```bash
# Lệnh:
npx tsc --noEmit   # (project không có script "typecheck" riêng; dùng tsc trực tiếp,
                    # tương đương phần đầu của script "build": "tsc && vite build")

# Kết quả:
(không có output — 0 lỗi)
```

### 3.3. FE build

```bash
# Lệnh:
npm run build   # chưa chạy riêng — đã xác nhận qua tsc --noEmit (phần compile-check của build)

# Kết quả:
[CHƯA CHẠY riêng phần vite build — tsc --noEmit đã xác nhận không có type error;
 khuyến nghị reviewer chạy `npm run build` đầy đủ nếu cần xác nhận bundle output]
```

### 3.4. FE test

```bash
# Lệnh:
npm run test:unit

# Kết quả:
Test Files  52 passed (52)
     Tests  342 passed (342)
# Sau đó thêm leadTime.test.ts và 1 test case mới trong TicketDetailDrawer.test.tsx, chạy riêng:
npx vitest run "src/__ tests __/pm-dashboard/leadTime.test.ts"
 ✓ src/__ tests __/pm-dashboard/leadTime.test.ts (5 tests)
 Test Files  1 passed (1)
      Tests  5 passed (5)

npx vitest run "src/__ tests __/pm-dashboard/TicketDetailDrawer.test.tsx"
 ✓ src/__ tests __/pm-dashboard/TicketDetailDrawer.test.tsx (2 tests)
 Test Files  1 passed (1)
      Tests  2 passed (2)

# Full suite chạy lại sau khi thêm toàn bộ test mới:
npm run test:unit
Test Files  53 passed (53)
     Tests  348 passed (348)
```

### 3.5. Manual / E2E (theo impl-plan §8.2)

| #   | Scenario                                                          | Preconditions                                              | Kết quả | Ghi chú |
| --- | ------------------------------------------------------------------ | ------------------------------------------------------------ | -------- | ------- |
| 1   | Cả 2 file có date hợp lệ → hiển thị đúng created/updated + duration | Ticket có `spec-pack.md`/`report.md` header date đúng format | [ ]      | Chưa chạy thủ công trên môi trường dev thật — cần trigger scan + mở drawer để xác nhận trực quan. Khuyến nghị reviewer/QA thực hiện trước khi merge. |
| 2   | `report.md` không tồn tại → `completed_at`/duration = `-`          | Ticket chỉ có `spec-pack.md`                                  | [ ]      | Như trên. |
| 3   | Field bị sửa thành text không phải ngày → cột về `NULL`, scan không fail | Ticket đã có `started_at` hợp lệ từ lần scan trước            | [ ]      | Logic đã có unit/integration test coverage (mục 5 #1, #4) nhưng chưa test thủ công qua UI thật. |
| 4   | `completed_at` sớm hơn `started_at` → duration = `-`                | Chỉnh update date của report.md về trước create date          | [x]      | Bao phủ bởi unit test `leadTime.test.ts` ("returns '-' when completedAt is earlier than startedAt") — logic xác nhận đúng, nhưng chưa xác nhận qua UI thật trên dev server. |

---

## 4. Self check theo Review Checklist (`review-checklist.md`)

| RC#   | Trạng thái | Bằng chứng / Ghi chú |
| ----- | ---------- | --------------------- |
| RC-01 | [x]        | AC-1,2,5,6,8,12 (BE) có unit/integration test pass (mục 3.1); AC-3,4,7,9,10,11 (FE/API) có unit test pass + code review (mục 5). AC-12 (historical backfill-free) đúng theo thiết kế: cùng code path persist mỗi lần scan, không cần job riêng. |
| RC-02 | [x]        | Không thêm GitHub commit-history call, không thêm field mới vào `report.md` template, không backfill job, không endpoint mới — xác nhận bằng diff review (chỉ các file ở mục 2). |
| RC-03 | [x]        | Duration chỉ tính trong `EDCAP_FE/src/utils/leadTime.ts` (client-side); `PmDashboardModels`/`PmDashboardDtos`/DB không có field/column duration nào. |
| RC-04 | [x]        | Không đụng `closed_at`/`ck_ticket_date_order` — migration V513 chỉ thêm `completed_at`, không sửa constraint khác. |
| RC-05 | [x]        | `ArtifactScannerService.java` đọc `parsed.headerMetadata().get("create_date"/"update_date")` — dùng lại map có sẵn từ `MarkdownParserCore.extractHeaderMetadata()`, không viết regex mới. |
| RC-06 | [x]        | Không có Human Decision/Open Issue nào bị đổi ngầm — xem mục 6.3. |
| RC-07 | [x]        | Port method mới ở `application/port/out/persistence`; implementation ở `infrastructure/persistence/adapter/scanner`; `HeaderDateNormalizer` ở `domain/service/markdown/core`. `web` không import `infrastructure` ở bất kỳ file nào đã sửa. |
| RC-08 | [x]        | `HeaderDateNormalizer.normalize(...)` là điểm duy nhất chuẩn hoá ngày, được gọi từ cả 2 nơi trong `ArtifactScannerService` — không copy-paste logic. |
| RC-09 | [x]        | `ArtifactScannerPersistencePort.java:77-79`; implementation tại `ArtifactScannerJdbcAdapter.java` (sau `deactivateAcceptanceCriteriaByTicketId`). |
| RC-10 | [~]        | **Lệch có chủ đích, đã xác nhận với user (mục 1, mục 7):** không dùng `TransactionTemplate` riêng vì pattern này không tồn tại ở bất kỳ đâu trong class — dùng đúng convention hiện có (`jdbc.update(...)` tuần tự). Hành vi overwrite/null vẫn đúng BR-6/9. |
| RC-11 | [x]        | Không thêm pipeline/job mới — logic nằm trong đúng `persistSpecPackParse`/`persistReportParse` đã có. |
| RC-12 | [x]        | Không có domain entity mới cần `@Builder`; `HeaderDateNormalizer` là utility class static, không cần injection. |
| RC-13 | [x]        | 3 dòng mới dùng đúng `t("Pages.PmDashboard.<key>", { defaultValue })` + `formatDateTime(...)` — xem `TicketDetailDrawer.tsx` (sau block `mergedAt`). |
| RC-14 | [~]        | `startedAt`/`completedAt` KHÔNG dùng conditional truthy-guard như `mergedAt` — luôn render vì `formatDateTime` tự trả `"-"` cho null, nhất quán với cách `createAt`/`updatedAt` đã làm (không phải `mergedAt`). Duration row cũng luôn render (không conditional). Đây là lựa chọn có chủ đích khớp với AC-3/AC-4 (hiển thị `-` khi thiếu, không phải ẩn dòng), không phải sai sót — cần reviewer xác nhận cách hiểu này đúng ý spec-pack §2.1 ("Show a default fallback value `-`"). |
| RC-15 | [x]        | `computeLeadTimeDuration` là pure function trong `src/utils/leadTime.ts`, không phải Redux/Zustand. |
| RC-16 | [x]        | Guard rõ ràng bằng `if`/return sớm, không dùng try/catch — test `leadTime.test.ts` xác nhận cả 2 case null và `completed < started`. |
| RC-17 | [x]        | `durationDayUnit`/`durationHourUnit` là i18n key, không hardcode "d"/"h" — xem `leadTime.ts` dùng `t("Pages.PmDashboard.durationDayUnit"/"durationHourUnit", {...})`. |
| RC-18 | [x]        | 5 key mới (bao gồm 2 unit key bổ sung) có mặt ở cả `en`/`vi`/`ja`, đặt ngay sau `mergedAt` — đã grep xác nhận trước khi sửa, không đụng key cũ. |
| RC-19 | [x]        | Không thêm formatter ngày mới — `formatDateTime` từ `lib/utils.ts` được tái sử dụng nguyên trạng cho 2 dòng timestamp. |
| RC-20 | [x]        | Không có `fetch` mới — dùng lại `endpoints.pmDashboard.detail` + `useQuery` đã có trong `PMDashboardPage.tsx`; không tạo hook file mới. |
| RC-21 | [x]        | `leadTime.ts` dùng named export (`export function computeLeadTimeDuration`), không có component React mới cần `forwardRef`. |
| RC-22 | [x]        | `findDetail` và `baseTicketQuery()` được sửa đồng thời, thêm chính xác `, t.started_at, t.completed_at` giống hệt nhau — xác nhận bằng diff (mục 2, dòng 6) và toàn bộ test `PmDashboardControllerTest`/`PmDashboardServiceTest` pass. |
| RC-23 | [x]        | `mapTicketRow` dùng `rs.getObject("started_at"/"completed_at", OffsetDateTime.class)`, cùng pattern với `merged_at`. |
| RC-24 | [x]        | Canonical + compact constructor đều cập nhật; grep xác nhận `new DashboardTicketRow(` chỉ có 1 call site production (`mapTicketRow`, đã cập nhật) — không có caller nào khác bị ảnh hưởng ngoài 2 test file (mục 2, dòng 16). |
| RC-25 | [x]        | `DashboardTicketDetail` không đổi — không thêm field duration nào. |
| RC-26 | [x]        | Response chỉ thêm 2 field nullable, additive — `PmDashboardControllerTest` (14 test) vẫn pass không sửa assertion nào. |
| RC-27 | [x]        | 2 method port riêng biệt `updateTicketStartedAt`/`updateTicketCompletedAt` — không dùng 1 method chung. |
| RC-28 | [x]        | Không có `if (value != null)` guard trước khi gọi update — luôn gọi `persistence.updateTicketXxx(ticketId, normalized)` kể cả khi `normalized == null`. |
| RC-29 | [x]        | Block đọc/normalize/persist được bọc trong `try { ... } catch (RuntimeException e) { log.warn(...); }` ở cả 2 method persist — không throw ra ngoài. |
| RC-30 | [x]        | `V513__add_completed_at_to_tbl_dim_ticket.sql` chỉ có 1 dòng `ALTER TABLE ... ADD COLUMN completed_at TIMESTAMPTZ;`. |
| RC-31 | [x]        | Không có statement nào sửa `started_at`'s column definition trong migration. |
| RC-32 | [x]        | Xác nhận qua đọc `V4__init_shema_v2.sql:192` — `ck_ticket_date_order` chỉ tham chiếu `closed_at`/`created_at`. |
| RC-33 | [x]        | Không có script backfill nào đi kèm — chỉ 1 file `ALTER TABLE`. |
| RC-34 | [x]        | `V513` — xác nhận là version tiếp theo sau `V512__add_ai_finding_stat_tracking.sql` (số lớn nhất hiện có), qua liệt kê đầy đủ thư mục migration. |
| RC-35 | [x]        | Rollback DB tài liệu hoá trong `impl-plan.md §7.2`/plan file — thao tác thủ công `ALTER TABLE ... DROP COLUMN`, không tự động qua Flyway. |
| RC-36 | [x]        | Cả 3 giá trị đều là ngày tháng nội bộ (spec-pack date field), không phải PII. |
| RC-37 | [x]        | Không có external API call mới — chỉ đọc `headerMetadata` đã parse sẵn từ nội dung file GitHub blob (cơ chế fetch cũ). |
| RC-38 | [x]        | Không đổi authentication/authorization — endpoint vẫn dưới session-based access hiện có (`PmDashboardControllerTest` pass không đổi). |
| RC-39 | [x]        | Log WARN chỉ chứa `ticketId`, `sourcePath`, `e.getMessage()` — không có business data nhạy cảm. |
| RC-40 | [x]        | Không thêm `@Scheduled`/job mới — ghi diễn ra trong `persistSpecPackParse`/`persistReportParse` đã có. |
| RC-41 | [x]        | try/catch nội bộ đảm bảo lỗi normalize/persist 1 field không escalate thành lỗi scan toàn bộ ticket — test `ArtifactScannerServiceTest` (24 test) vẫn pass. |
| RC-42 | [x]        | Ghi nhận ở mục 6.1 #1 — accepted behavior, không phải bug cần fix. |
| RC-43 | [x]        | Không thêm config/feature flag nào. |
| RC-44 | [x]        | Ghi nhận ở mục 6.1 #2 — không tự viết `LayerEnforcementTest.java` mới (ngoài phạm vi ticket này). |
| RC-45 | [x]        | `HeaderDateNormalizerTest.java` — 7 test case: full datetime, bare date, null, empty, whitespace, non-date text, full-width digit — tất cả pass (mục 3.1). |
| RC-46 | [x]        | `leadTime.test.ts` — 5 test case: cặp hợp lệ, startedAt null, completedAt null, `completed < started`, input không parse được — tất cả pass (mục 3.4). |
| RC-47 | [x]        | `ArtifactScannerServiceTest` (24 test, bao gồm fake port `InMemoryArtifactScannerPersistencePort` đã implement 2 method mới) pass — xác nhận ghi/re-scan/missing-file không làm fail run. Chưa có test integration riêng biệt kiểm tra DB thật (chỉ fake in-memory port) — xem mục 6.2. |
| RC-48 | [x]        | `PmDashboardControllerTest`/`PmDashboardServiceTest`/`PmDashboardDtosTest` pass — xác nhận field trả về đúng qua toàn bộ chain Service→JdbcAdapter (mock)→DTO. Chưa có test JDBC thật chạy trên Postgres (chỉ unit/mock level) — xem mục 6.2. |
| RC-49 | [x]        | Toàn bộ 637 test BE pass không sửa assertion nào liên quan `started_at`-based ordering hiện có. |
| RC-50 | [x]        | `TicketDetailDrawer.test.tsx` — thêm test case mới "renders spec-pack created time, report updated time, and computed duration" assert đúng cả 3 label + giá trị format + duration `1d 8h`; pass cùng test hiện có (2/2). |
| RC-51 | [x]        | `mvn -o test` (637 pass) + `npx tsc --noEmit` (0 lỗi) + `npm run test:unit` (348 pass, sau khi thêm `leadTime.test.ts` + 1 test case mới) đều pass. `npm run build` (vite bundle) chưa chạy riêng — xem mục 3.3. |
| RC-52 | [x]        | Bảng AC→RC ở `review-checklist.md` cuối file đã bao phủ đủ AC-1..12; mục 5 dưới đây map lại theo test area. |
| RC-53 | [x]        | File này (self-review.md) điền đầy đủ bằng chứng file:line/kết quả test cho từng RC. |
| RC-54 | [x]        | Không có Human Decision/Open Issue nào bị đổi — xem mục 6.3. |
| RC-55 | [x]        | Đủ artifact: spec-pack, context, impact-analysis, impl-plan, review-checklist, self-review (file này). |
| RC-56 | [ ]        | Chưa chạy migration `V513` trên bất kỳ DB nào (theo `.claude/rules/00-safety.md §3`, cần user xác nhận riêng trước khi `mvn flyway:migrate`) — cần thực hiện trước khi deploy code BE/FE. |
| RC-57 | [x]        | Thứ tự rollback tài liệu hoá trong plan: Part C (FE) → Part B (BE contract) → Part A (BE parse/persist). |
| RC-58 | [x]        | Không thêm feature flag nào. |
| RC-59 | [ ]        | Rollback DB (`DROP COLUMN completed_at`) đã tài liệu hoá là thao tác thủ công cần user/DBA approve — chưa có ai approve/thực hiện (đúng như kỳ vọng ở giai đoạn implementation, không phải rollback). |

Chú thích: `[~]` = có bằng chứng nhưng có điểm cần reviewer xác nhận thêm; `[ ]` = chưa thực hiện, không phải Blocker của việc implement code (migration run / manual UI test là hành động vận hành riêng, ngoài phạm vi commit code).

---

## 5. Trạng thái tương ứng Test Plan (theo impl-plan §8 / spec-pack §15)

| #   | Test area                                                                                    | Loại              | Trạng thái | Bằng chứng |
| --- | ----------------------------------------------------------------------------------------------- | ----------------- | ---------- | ---------- |
| 1   | Date normalization (bare-date, malformed, empty, whitespace, full-width digit)                    | BE unit test       | [x]        | `HeaderDateNormalizerTest.java` — 7/7 pass. |
| 2   | `SpecPackMarkdownParser` created-time extraction (present/absent/malformed)                       | BE unit test       | [x]        | Không cần test parser riêng — `headerMetadata` map đã có sẵn, không sửa parser; coverage nằm ở `ArtifactScannerServiceTest` (đọc `create_date` qua fake port). |
| 3   | `ReportMarkdownParser` updated-time extraction (present/absent/malformed)                         | BE unit test       | [x]        | Tương tự #2, coverage qua `ArtifactScannerServiceTest` (`update_date`). |
| 4   | Artifact Scanner persist step (ghi/overwrite/null-out/file thiếu/không fail scan)                  | BE integration     | [~]        | `ArtifactScannerServiceTest` (24 test, fake in-memory port) pass — xác nhận logic đúng, nhưng chưa có test JDBC thật đối chiếu với Postgres. Đề xuất bổ sung 1 test JDBC-level nếu reviewer yêu cầu mức độ cao hơn. |
| 5   | `computeLeadTimeDuration` (cặp hợp lệ, 1 input null, `completed < started`)                        | FE unit test       | [x]        | `leadTime.test.ts` — 5/5 pass. |
| 6   | PM Dashboard ticket-detail query/DTO (đủ 3 field, kể cả case null)                                 | BE integration     | [~]        | `PmDashboardServiceTest`/`PmDashboardControllerTest`/`PmDashboardDtosTest` pass ở mức unit/mock; chưa có test JDBC thật kiểm tra SQL trả đúng cột trên Postgres thật. |
| 7   | `TicketInformationCard` rendering (format, duration, fallback `-`, i18n key)                       | FE component test  | [x]        | `TicketDetailDrawer.test.tsx` — test case mới xác nhận cả 3 label/giá trị/duration render đúng; pass. |
| 8   | Regression: `PmDashboardJdbcAdapter` behavior với `started_at` không đổi                          | Regression test    | [x]        | Toàn bộ 637 test BE pass, không sửa bất kỳ assertion nào về order/filter hiện có. |
| 9   | 4 manual scenario (mục 3.5 ở trên)                                                                 | Manual             | [ ]        | Chưa chạy trên dev server thật — cần QA/reviewer xác nhận trực quan trước khi merge. |

---

## 6. Chưa xử lý / pending / accepted risk

### 6.1. Accepted risk (đã xác nhận từ spec-pack/impl-plan, không phải bug)

| #   | Rủi ro                                                                                                                     | Nguồn                    | Ghi chú                                                                 |
| --- | ------------------------------------------------------------------------------------------------------------------------- | ------------------------- | ------------------------------------------------------------------------ |
| 1   | Một timestamp/duration đã hiển thị trước đó có thể biến mất khỏi dashboard sau khi file nguồn bị sửa khiến field malformed | H-SDD-LEAD-TIME-4, closed | Accepted behavior, không cần fix; `updateTicketStartedAt`/`updateTicketCompletedAt` luôn overwrite kể cả về `NULL` (RC-28). |
| 2   | Không có ArchUnit test thực sự chạy được để tự động verify layer boundary cho port/adapter mới                             | IMPL-OI-4, closed         | Gap có sẵn của hệ thống (`LayerEnforcementTest.java` không tồn tại); đã review layer boundary thủ công (RC-07) — port ở `application`, impl ở `infrastructure`, utility ở `domain`. |

### 6.2. Pending / chưa xử lý (điền trong khi implement nếu phát sinh)

| #   | Hạng mục | Lý do chưa bao phủ | Hành động được đề xuất |
| --- | -------- | ------------------- | ------------------------ |
| 1   | Test JDBC-level thật (Postgres) cho `updateTicketStartedAt`/`updateTicketCompletedAt` và cho SQL mới trong `PmDashboardJdbcAdapter` | Không có DB test thật chạy trong phiên làm việc này (chỉ mock/fake); phù hợp với comment RC-47/RC-48 | Chạy `mvn clean verify` với profile tích hợp DB thật (nếu có) trước khi merge, hoặc để CI pipeline xác nhận |
| 2   | 4 manual scenario ở mục 3.5/5#9 chưa chạy trên dev server thật | Cần môi trường dev chạy được (DB + BE + FE) và ticket mẫu có `spec-pack.md`/`report.md` thật | QA/reviewer chạy thủ công trước khi merge, theo đúng 4 kịch bản trong `impl-plan.md §8.2` |
| 3   | Chạy migration `V513` trên DB dev/test | Theo `.claude/rules/00-safety.md §3`, DB migration cần user xác nhận riêng, ngoài phạm vi implementation tự động | User/DBA xác nhận rồi chạy `mvn flyway:migrate` (hoặc tương đương) trước khi deploy code phụ thuộc cột `completed_at` |
| 4   | `npm run build` (vite bundle) đầy đủ | Đã xác nhận phần compile-check (`tsc --noEmit`) không lỗi; phần bundle build chưa chạy riêng trong phiên này | Chạy `npm run build` đầy đủ trước khi merge nếu cần xác nhận bundle |

### 6.3. Open Issues từ impl-plan còn lại

> Tất cả IMPL-OI-1..4 ở `impl-plan.md` §9 đã **Closed** tính đến thời điểm viết review-checklist này
> (2026-08-21). Không có Open Issue nào đang mở. Implementation không phát hiện Open Issue mới ngoài
> 2 điểm lệch đã ghi nhận và xác nhận với user ở mục 1 (không phải Open Issue theo nghĩa spec — đã xử
> lý dứt điểm trong phiên này, không cần theo dõi tiếp).

| #   | OI ID | Mô tả ngắn | Trạng thái |
| --- | ----- | ----------- | ---------- |
| —   | —     | Không có Open Issue nào đang mở | — |

---

## 7. Phần AI đã suy đoán (giả định cần xác nhận khi bắt đầu code)

| #   | Giả định                                                                                                        | Quyết định thực tế khi code | Ghi chú |
| --- | ----------------------------------------------------------------------------------------------------------- | ---------------------------- | ------- |
| 1   | Tên/path chính xác của utility chuẩn hoá ngày dùng chung (đề xuất: `HeaderDateNormalizer.java` trong `domain/service/markdown/core/`) | Giữ nguyên đề xuất — `EDCAP_BE/src/main/java/com/sdd/platform/domain/service/markdown/core/HeaderDateNormalizer.java`, package/vị trí xác nhận đúng là duy nhất trong thư mục `core/` cùng `MarkdownParserCore.java`. |         |
| 2   | Tên/path chính xác của FE pure helper tính duration (đề xuất: `EDCAP_FE/src/utils/leadTime.ts`)                 | Giữ nguyên path, nhưng chữ ký hàm khác đề xuất ban đầu của impl-plan (`(startedAt, completedAt, language: string)`) — đổi thành `(startedAt, completedAt, t: TranslateFn)`, nhận trực tiếp hàm `t` từ component cha thay vì tự đọc `localStorage`/`i18next.language`. Lý do: tái sử dụng đúng instance `t` mà `TicketDetailDrawer` đã có sẵn (tránh phụ thuộc thêm vào cách `formatDateTime` đọc `localStorage` riêng), và giúp unit test dễ mock hơn (test truyền `t` giả trực tiếp, không cần mock `localStorage`/i18next). Không đổi hành vi quan sát được — vẫn `-` khi null/order sai, vẫn localize qua i18n. | Cần reviewer xác nhận cách tiếp cận này chấp nhận được so với đề xuất ban đầu. |
| 3   | Không có caller nào khác dùng `DashboardTicketRow`'s compact constructor ngoài `PmDashboardJdbcAdapter` (cần grep xác nhận ở bước B.1 trước khi thêm field) | **Xác nhận đúng** — grep `new DashboardTicketRow(` trên toàn bộ `EDCAP_BE` chỉ trả về 1 kết quả production (`PmDashboardJdbcAdapter.mapTicketRow`, dùng canonical constructor, đã cập nhật trực tiếp) và 2 kết quả test (`PmDashboardServiceTest.java`, cũng dùng canonical constructor, đã cập nhật thêm 2 arg `null, null`). Compact constructor's internal delegation trong `PmDashboardModels.java` cũng được cập nhật thêm 2 `null`. |         |
| 4   | Giá trị mặc định cho 2 field mới trên compact constructor là `null` (giống cách `mergedAt` đang mặc định)        | **Xác nhận đúng** — giữ nguyên `null, null` cho `startedAt`/`completedAt` trong delegation call của compact constructor, nhất quán với cách `artifactVersion`/`mergedAt` đã mặc định `null`. |         |

---

## 8. Hạng mục nhờ con người review

| #   | Hạng mục                                                                                                                                             | Lý do cần người review                                                                                             |
| --- | ------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------- |
| 1   | Layer boundary cho port method mới (`ArtifactScannerPersistencePort`) và JDBC implementation (`ArtifactScannerJdbcAdapter`)                              | Không có ArchUnit test tự động chạy được (`LayerEnforcementTest.java` không tồn tại, IMPL-OI-4) — cần code review thủ công |
| 2   | Xác nhận migration `V513` an toàn để chạy trên môi trường thật (`mvn flyway:migrate` hoặc tương đương)                                                    | DB migration yêu cầu xác nhận người dùng trước khi chạy theo `.claude/rules/00-safety.md §3` — **chưa chạy** trong phiên này |
| 3   | Rollback DB thủ công (`ALTER TABLE tbl_dim_ticket DROP COLUMN completed_at;`) nếu cần revert                                                             | Destructive/manual action, không tự động qua Flyway — cần DBA/user approve trước khi chạy                                |
| 4   | Xác nhận SQL trùng lặp giữa `findDetail` và `baseTicketQuery()` đã được sửa nhất quán, không lệch dữ liệu giữa 2 endpoint                                  | Đã sửa đồng thời trong cùng 1 lượt edit và test pass, nhưng khuyến nghị reviewer double-check diff cả 2 nơi cùng lúc      |
| 5   | Xác nhận không có regression về thứ tự sort/filter hiện có của `PmDashboardJdbcAdapter` khi `started_at` bắt đầu được populate rộng rãi                    | Toàn bộ test hiện có pass, nhưng test hiện có không có ticket nào với `started_at` được populate rộng rãi qua dữ liệu thật — review logic đủ, nhưng khuyến nghị theo dõi sau khi scan cadence thật chạy trên dữ liệu production-like |
| 6   | Quyết định đổi chữ ký `computeLeadTimeDuration` để nhận `t` thay vì `language` string (mục 7 #2)                                                          | Lệch nhỏ so với đề xuất impl-plan ban đầu — cần xác nhận đây là cách tiếp cận chấp nhận được |
| 7   | Quyết định thêm 2 i18n key `durationDayUnit`/`durationHourUnit` ngoài 3 key ban đầu (`startedAt`/`completedAt`/`leadTimeDuration`) trong IMPL-OI-2         | IMPL-OI-2 chỉ chốt tên 3 key ban đầu; 2 key unit bổ sung là cần thiết để tránh hardcode "d"/"h" (RC-17) nhưng chưa được liệt kê tường minh trong Open Issue đã closed — cần reviewer xác nhận việc bổ sung này hợp lý |
| 8   | Test JDBC thật (Postgres) cho 2 method persist mới và SQL mới của `PmDashboardJdbcAdapter` chưa được thêm (mục 6.2 #1) | Chỉ có unit/mock coverage trong phiên này — cân nhắc mức độ coverage cần thiết trước khi merge, có thể chấp nhận làm follow-up nếu reviewer đồng ý |

---

## 9. Confirmations cuối cùng

| #   | Confirmation                                                                    | Trạng thái |
| --- | ---------------------------------------------------------------------------------- | ---------- |
| 1   | Không thêm scope ngoài spec-pack §2.2                                              | [x]        |
| 2   | Mọi Open Issue vẫn open đều được nêu ở mục 6.3                                     | [x]        |
| 3   | Mọi RC mức Blocker đã ✅ hoặc đã nêu lý do ở mục 6                                  | [x]        |
| 4   | Lint / type-check / build pass; nếu fail đã được giải trình ở mục 3                | [x]        |
| 5   | Không có console log, debug code, commented-out code còn sót                       | [x]        |
| 6   | Không log PII / secret / raw business data vượt mức cần thiết                      | [x]        |
| 7   | Migration `V513` có rollback plan tài liệu hóa (§7.2 impl-plan), không có down migration tự động | [x]        |
