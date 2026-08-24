# Kế hoạch kiểm thử — SDD-LEAD-TIME (Spec-pack created time / Report updated time / Duration)

**Ticket ID**: SDD-LEAD-TIME
**Create date**: 2026-08-21 15:30:00
**Author**: nvt_dung
**Update date**: 2026-08-21 15:30:00

> Mỗi AC phải được bao phủ bởi ít nhất một loại kiểm thử.

---

## 1. Ma trận bao phủ

| #   | AC                     | FE UT | BE UT | API IT | E2E | Black-box |
| --- | ------------------------ | ----- | ----- | ------ | --- | --------- |
| 1   | AC-SDD-LEAD-TIME-1/v1    |       | x     | x      |     |           |
| 2   | AC-SDD-LEAD-TIME-2/v1    |       | x     | x      |     |           |
| 3   | AC-SDD-LEAD-TIME-3/v1    |       |       | x      |     | x         |
| 4   | AC-SDD-LEAD-TIME-4/v1    |       |       | x      |     | x         |
| 5   | AC-SDD-LEAD-TIME-5/v1    |       | x     |        |     |           |
| 6   | AC-SDD-LEAD-TIME-6/v1    |       | x     | x      |     | x         |
| 7   | AC-SDD-LEAD-TIME-7/v1    | x     |       |        |     | x         |
| 8   | AC-SDD-LEAD-TIME-8/v1    |       |       | x      |     |           |
| 9   | AC-SDD-LEAD-TIME-9/v1    | x     |       |        |     | x         |
| 10  | AC-SDD-LEAD-TIME-10/v1   |       |       | x      |     |           |
| 11  | AC-SDD-LEAD-TIME-11/v1   | x     |       |        |     |           |
| 12  | AC-SDD-LEAD-TIME-12/v1   |       |       | x      |     |           |

## 2. Unit test FE

| #   | Tệp kiểm thử | Nội dung kiểm thử | AC  |
| --- | -------------- | -------------------- | --- |
| 1   | `EDCAP_FE/src/__ tests __/pm-dashboard/leadTime.test.ts` (thực tế — đổi tên từ `formatDuration.test.ts` dự kiến, khớp `src/utils/leadTime.ts`) | well-formed pair → `"1d 8h"`-style; `startedAt`/`completedAt` null → `-`; `completedAt < startedAt` → `-`; input không parse được → `-` (5 test case) | AC-9, AC-11 |
| 2   | `EDCAP_FE/src/__ tests __/pm-dashboard/TicketDetailDrawer.test.tsx` (thực tế — gộp vào file test có sẵn của component cha thay vì tách `TicketInformationCard.test.tsx` riêng, vì `TicketInformationCard` không phải file/export riêng) | render 3 field mới (created/updated/duration), đúng format + fallback `-`, dùng i18n key không hardcode | AC-7 |

**Các trọng tâm:** null-safety của duration, render có điều kiện, không hardcode label.

## 3. Unit test BE

| #   | Lớp kiểm thử | Nội dung kiểm thử | AC  |
| --- | -------------- | -------------------- | --- |
| 1   | `EDCAP_BE/src/test/java/com/sdd/platform/domain/service/markdown/core/HeaderDateNormalizerTest.java` (thực tế) | full datetime passthrough, bare date → `00:00:00`, null/empty/whitespace/text không phải ngày/full-width digit → `null` (7 test case) | AC-5, AC-6 |
| 2   | ~~`SpecPackMarkdownParserTest`~~ / ~~`ReportMarkdownParserTest`~~ — **không tạo riêng** | `headerMetadata().get("create_date"/"update_date")` không cần sửa parser (đã sẵn có, xác nhận impact-analysis.md §6) → coverage cho việc đọc đúng key gộp vào `ArtifactScannerServiceTest` (dòng #3 dưới), không cần test riêng ở cấp parser | AC-1, AC-2, AC-6 |
| 3   | `EDCAP_BE/src/test/UnitTest/java/com/sdd/platform/application/usecase/scanner/ArtifactScannerServiceTest.java` (bổ sung mới so với kế hoạch ban đầu) | scan với `create_date`/`update_date` hợp lệ/thiếu/malformed → gọi đúng port method với giá trị đã normalize; re-scan overwrite kể cả về `null`; exception không thoát khỏi persist block (24 test case, dùng fake in-memory `ArtifactScannerPersistencePort`) | AC-1, AC-2, AC-3, AC-4, AC-6, AC-8, AC-12 |

**Các trọng tâm:** giá trị biên định dạng ngày, luồng exception khi malformed (không throw ra ngoài), không có logic phân quyền liên quan.

## 4. Integration test API

| #   | Endpoint / flow | Kịch bản | Loại thực tế | AC  |
| --- | ---------------- | ---------- | -------------- | --- |
| 1   | Artifact Scanner scan flow (nội bộ, không phải REST) | scan ticket với cả 2 file hợp lệ → `started_at`/`completed_at` populated | Fake-port unit test (`ArtifactScannerServiceTest`) — **không phải DB thật**, xem §8 mục 1 | AC-1, AC-2 |
| 2   | Artifact Scanner scan flow | scan ticket thiếu 1 hoặc cả 2 file → cột tương ứng NULL | Fake-port unit test — không phải DB thật | AC-3, AC-4 |
| 3   | Artifact Scanner scan flow | re-scan sau khi `report.md` đổi giá trị → `completed_at` cập nhật | Fake-port unit test — không phải DB thật | AC-8 |
| 4   | Artifact Scanner scan flow | re-scan ticket cũ (trước ticket này tồn tại) → populate không cần backfill riêng | Fake-port unit test — không phải DB thật | AC-12 |
| 5   | `GET /api/v1/pm/dashboard/tickets/{ticketId}/detail` | response trả đúng `startedAt`/`completedAt`, kể cả trường hợp null | `PmDashboardServiceTest`/`PmDashboardControllerTest`/`PmDashboardDtosTest` — mock/`@MockBean` ở mức Service/Controller/DTO, **không phải DB thật** | AC-10 |
| 6   | `ArtifactScannerJdbcAdapter.updateTicketStartedAt/updateTicketCompletedAt` + `PmDashboardJdbcAdapter` SQL mới (`findDetail`/`baseTicketQuery`) trên Postgres thật | **Không có** — đã grep toàn repo: không tồn tại Testcontainers/DB test harness nào (dependency có trong `pom.xml` nhưng không dùng ở đâu); `ArtifactScannerPersistenceIntegrationTest.java` chỉ đọc nội dung file bằng `Files.readString`/`String.contains`, không kết nối DB | Bị bỏ qua có chủ đích — xem §8 mục 1 | AC-1, AC-2, AC-10 |

## 5. Kiểm thử E2E (Playwright)

| #   | Kịch bản | Các bước | Kết quả mong đợi | AC  | Trạng thái |
| --- | ---------- | ---------- | ------------------- | --- | ---------- |
| 1   | Luồng chính (bình thường) | Mở PM Dashboard → mở Ticket Detail của ticket đã scan đầy đủ | Hiển thị đúng created/updated time + duration | AC-7, AC-9 | **Chưa chạy** — không có dev server thật chạy trong phiên làm việc này; xem `self-review.md` §3.5 (mục 1) |
| 2   | Luồng thiếu dữ liệu | Mở Ticket Detail của ticket chưa scan hoặc thiếu file | Hiển thị `-` cho cả 3 field, không lỗi UI | AC-3, AC-4, AC-11 | **Chưa chạy** — như trên; xem `self-review.md` §3.5 (mục 2) |

## 6. Các lệnh chạy kiểm thử

```bash
# BE unit + integration tests
cd EDCAP_BE && mvn test
# (đã chạy thực tế ở chế độ offline: mvn -o test — 637 pass, xem test-results.md)

# FE unit tests
cd EDCAP_FE && npm test
# (đã chạy thực tế: npm run test:unit — 348 pass, xem test-results.md)

# FE typecheck (đã chạy thay cho "npm run typecheck" — script này không tồn tại trong package.json)
cd EDCAP_FE && npx tsc --noEmit

# E2E — chưa chạy trong phiên này (xem §5, §8)
cd EDCAP_FE && npx playwright test
```

## 7. Ghi chú / Ràng buộc

- Không mock domain entity/value type theo `.claude/rules/40-testing.md` — dùng dữ liệu thật cho `ParsedArtifact`, `TicketScope`.
- Integration test cho Artifact Scanner cần DB test riêng (không dùng production DB), theo `.claude/rules/40-testing.md` — **hiện chưa có hạ tầng này trong repo** (xem §8 mục 1).
- FE component test dùng Vitest + Testing Library; không cần Playwright cho case chi tiết field-level.
- Case dễ flaky dự kiến: so sánh duration string phụ thuộc `Date.now()` tại thời điểm test — thực tế `computeLeadTimeDuration` chỉ tính diff giữa 2 timestamp cố định (`startedAt`/`completedAt`) chứ không so với `Date.now()`, nên rủi ro flaky này không phát sinh trong implementation thực tế.

## 8. Test đã bỏ qua có chủ đích

| # | Hạng mục bị bỏ qua | Lý do | Rủi ro còn lại | Bằng chứng thay thế |
|---|---|---|---|---|
| 1 | DB-level integration test cho `ArtifactScannerJdbcAdapter.updateTicketStartedAt/updateTicketCompletedAt` và cho SQL mới (`t.started_at`, `t.completed_at`) trong `PmDashboardJdbcAdapter.findDetail`/`baseTicketQuery()`, chạy trên Postgres thật | Không tồn tại Testcontainers/DB test harness nào trong repo hiện tại (dependency `testcontainers`/`postgresql` khai báo trong `pom.xml` nhưng không có `@Container`/`@Testcontainers` nào được dùng ở bất kỳ đâu; không có base class/test profile DB). Xây dựng hạ tầng này là thay đổi lớn, ngoài phạm vi ticket bổ sung (additive) này — quyết định người dùng xác nhận khi lập kế hoạch Phase 6 | Trung bình — SQL mới chỉ được xác nhận đúng qua review thủ công (RC-22/RC-23 `review-checklist.md`) + unit test dùng fake port/mock, chưa có xác nhận round-trip DB thật | `ArtifactScannerServiceTest` (fake port bắt đúng tham số gọi), `PmDashboardServiceTest`/`PmDashboardControllerTest`/`PmDashboardDtosTest` (mock ở tầng Service/Controller/DTO), review diff SQL thủ công |
| 2 | 2 kịch bản E2E Playwright (§5) | Không có dev server (BE+FE+DB) chạy được trong phiên làm việc này | Thấp — logic hiển thị đã có FE component test (`TicketDetailDrawer.test.tsx`) xác nhận render đúng; chỉ thiếu xác nhận trực quan trên trình duyệt thật | `TicketDetailDrawer.test.tsx`, `self-review.md` §3.5 |
| 3 | `npm run build` (đóng gói vite đầy đủ) | `npx tsc --noEmit` đã xác nhận không có lỗi type — bước bundle không cần thiết để xác nhận tính đúng đắn của AC | Thấp — build bundle lỗi (nếu có) thường không liên quan tới logic type-safe đã qua `tsc` | `npx tsc --noEmit` (0 lỗi) |

## 9. Chính sách dữ liệu kiểm thử

- BE: không mock domain object/value type (`ParsedArtifact`, `HeaderDateNormalizer` input) theo `.claude/rules/40-testing.md` — dùng giá trị thật; chỉ mock port interface (`ArtifactScannerPersistencePort` dùng fake in-memory implementation, không phải Mockito mock, để bắt được chuỗi lời gọi thực tế trên nhiều lần scan).
- Bộ dữ liệu ngày dùng trong test bao phủ: datetime đầy đủ hợp lệ, bare-date hợp lệ, `null`, chuỗi rỗng, chuỗi chỉ có khoảng trắng, text không phải ngày (`"TBD"`), và ký tự số full-width (theo đúng boundary value trong `spec-pack.md` §6.5).
- FE: `leadTime.test.ts` truyền trực tiếp một hàm `t` giả (fake translate function) thay vì dựng `I18nextProvider` thật, vì `computeLeadTimeDuration(startedAt, completedAt, t)` nhận `t` như tham số thuần (xem `EDCAP_FE/src/utils/leadTime.ts`); `TicketDetailDrawer.test.tsx` dùng `I18nextProvider` với resource bundle tối thiểu theo đúng pattern trong `docs/standards/testing.md` (mục "i18n Testing").
- Không dùng dữ liệu production thật ở bất kỳ test nào; không có PII/secret trong bất kỳ fixture ngày tháng nào (chỉ là ngày tháng nội bộ của evidence file).
