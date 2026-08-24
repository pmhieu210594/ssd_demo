# Danh sách kiểm tra review — SDD-LEAD-TIME (Lead Time visibility: spec-pack created time, report updated time, computed duration)

- **Ticket:** SDD-LEAD-TIME
- **Trạng thái:** Draft
- **Tạo ngày:** 2026-08-21

> Nguồn tham chiếu chính: `docs/changes/SDD-LEAD-TIME/spec-pack.md` và `docs/changes/SDD-LEAD-TIME/impl-plan.md` (kèm `context.md`, `impact-analysis.md`).
> Mức độ nghiêm trọng: **Blocker** = bắt buộc sửa trước khi merge | **Major** = phải sửa trong PR này | **Minor** = sửa hoặc ghi nhận là nợ kỹ thuật

---

## 1. Khớp specification / AC

| #     | Hạng mục                                                                                                                                                            | Mức độ  | Trạng thái |
| ----- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------- | ---------- |
| RC-01 | Tất cả AC-SDD-LEAD-TIME-1..12 ở spec-pack §7 đã được triển khai và có bằng chứng (test/log/diff)                                                                    | Blocker | [ ]        |
| RC-02 | Không thêm hành vi ngoài phạm vi spec-pack §2.2 (không gọi GitHub commit-history API, không thêm field mới vào `report.md`, không backfill job, không endpoint mới) | Blocker | [ ]        |
| RC-03 | Duration chỉ tính ở FE (client-side), không có field duration nào được BE trả về hoặc lưu thành cột DB (OI-SDD-LEAD-TIME-1)                                          | Blocker | [ ]        |
| RC-04 | Không repurpose `tbl_dim_ticket.closed_at` / `ck_ticket_date_order` cho mục đích "report updated"                                                                   | Blocker | [ ]        |
| RC-05 | Header label đọc đúng `**Create date**` (spec-pack.md) và `**Update date**` (report.md), qua `MarkdownParserCore.extractHeaderMetadata()` có sẵn — không viết regex thứ hai | Blocker | [ ]        |
| RC-06 | Toàn bộ 5 Human Decision (H-1..H-5) và 3 Open Issue (OI-1..OI-3) ở spec-pack vẫn được tôn trọng đúng như đã chốt, không bị đổi ngầm khi code                        | Blocker | [ ]        |

## 2. General System Review

| #     | Hạng mục                                                                                                                                          | Mức độ | Trạng thái |
| ----- | -------------------------------------------------------------------------------------------------------------------------------------------------- | ------ | ---------- |
| RC-07 | Layer dependency đúng thứ tự `domain ← application ← infrastructure/web`; `web` không import `infrastructure` (`.claude/rules/20-architecture.md`) | Blocker | [ ]        |
| RC-08 | `HeaderDateNormalizer` (hoặc tên tương đương) là **1 utility dùng chung duy nhất**, được gọi tại `ArtifactScannerService`, không copy-paste logic normalize vào từng parser | Major  | [ ]        |
| RC-09 | 2 port method mới (`updateTicketStartedAt`/`updateTicketCompletedAt`) nằm ở `application/port/out/persistence`; implementation nằm ở `infrastructure` (`ArtifactScannerJdbcAdapter`) | Blocker | [ ]        |
| RC-10 | Mỗi lần gọi update dùng `TransactionTemplate` riêng, độc lập với các persist call khác trong cùng method, đúng theo convention "mỗi external upsert 1 transaction" | Major  | [ ]        |
| RC-11 | Không thêm pipeline/job/schedule mới — toàn bộ ghi diễn ra trong đúng persist step hiện có (`persistSpecPackParse`/`persistReportParse`)            | Blocker | [ ]        |
| RC-12 | Domain models tuân `@Builder @Getter @Setter @NoArgsConstructor @AllArgsConstructor` nếu có entity mới liên quan; injection qua constructor, không `@Autowired` field | Major  | [ ]        |

## 3. FE Review

| #     | Hạng mục                                                                                                                                                | Mức độ | Trạng thái |
| ----- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- | ------ | ---------- |
| RC-13 | 3 dòng mới (created time, updated time, duration) trong `TicketInformationCard` dùng đúng pattern `t("Pages.PmDashboard.<key>", { defaultValue })` + `formatDateTime(...)` như `createAt`/`updatedAt` | Major  | [ ]        |
| RC-14 | Conditional render / fallback `-` copy đúng pattern `mergedAt` (`detail.row.mergedAt ? (...) : null`) cho cả 3 field mới                                    | Major  | [ ]        |
| RC-15 | `computeLeadTimeDuration` là pure function, đặt ở `utils/` (không phải Redux slice, không phải Zustand store) theo `.claude/rules/20-architecture.md`      | Blocker | [ ]        |
| RC-16 | Duration hiển thị `-` khi `startedAt`/`completedAt` null HOẶC `completedAt < startedAt` (OI-SDD-LEAD-TIME-3), không dựa vào exception/try-catch để xử lý case này | Blocker | [ ]        |
| RC-17 | Format duration kiểu `"1d 8h"` được localize qua i18n hiện có theo ngôn ngữ hiện tại — không hardcode đơn vị tiếng Anh (OI-SDD-LEAD-TIME-2)                 | Major  | [ ]        |
| RC-18 | 3 key i18n mới (`Pages.PmDashboard.startedAt`/`completedAt`/`leadTimeDuration`) có mặt đầy đủ ở cả 3 file `en`/`vi`/`ja` locale.json, không đụng key cũ    | Blocker | [ ]        |
| RC-19 | Không thêm formatter ngày mới — tái sử dụng `formatDateTime` từ `lib/utils.ts` cho 2 dòng timestamp                                                        | Major  | [ ]        |
| RC-20 | Không gọi `fetch` trực tiếp — dùng lại `endpoints.pmDashboard.detail` + TanStack Query hiện có, không thêm hook file mới không cần thiết                    | Blocker | [ ]        |
| RC-21 | Named export, `React.forwardRef` + `displayName` (nếu có component mới), `cn()` cho classnames, CVA cho variant (theo `.claude/rules/10-style.md`)          | Minor  | [ ]        |

## 4. BE/API Review

| #     | Hạng mục                                                                                                                                               | Mức độ  | Trạng thái |
| ----- | --------------------------------------------------------------------------------------------------------------------------------------------------------- | ------- | ---------- |
| RC-22 | `findDetail`'s inline SQL và `baseTicketQuery()`'s SQL được sửa **đồng thời và giống nhau** để thêm `started_at`/`completed_at` (rủi ro #1 impl-plan)     | Blocker | [ ]        |
| RC-23 | `mapTicketRow` đọc đúng 2 cột mới bằng `rs.getObject(..., OffsetDateTime.class)`, cùng pattern với `merged_at`                                            | Major   | [ ]        |
| RC-24 | `DashboardTicketRow`: cả canonical constructor và compact constructor đều được cập nhật với 2 field mới; đã grep toàn bộ call site compact constructor trước khi sửa (bước B.1) | Blocker | [ ]        |
| RC-25 | `DashboardTicketDetail` không có field duration nào được thêm (chỉ `row.startedAt`/`row.completedAt` raw, theo spec-pack §11)                             | Blocker | [ ]        |
| RC-26 | Response API `GET /api/v1/pm/dashboard/tickets/{ticketId}/detail` là **additive/backward-compatible** — không đổi field cũ, không có case lỗi mới        | Blocker | [ ]        |
| RC-27 | 2 method port UPDATE riêng biệt (`updateTicketStartedAt`/`updateTicketCompletedAt`) — không dùng 1 method chung dễ gây ghi đè nhầm cột (rủi ro #3 impl-plan, IMPL-OI-1) | Blocker | [ ]        |
| RC-28 | Update luôn thực thi kể cả khi giá trị là `null` (không skip câu UPDATE khi null) — đúng BR-SDD-LEAD-TIME-6/9                                             | Blocker | [ ]        |
| RC-29 | Exception trong parse/normalize không thoát ra khỏi block persist — bọc try/catch nội bộ, log WARN, scan tiếp tục cho ticket/artifact khác                | Blocker | [ ]        |

## 5. DB/Migration Review

| #     | Hạng mục                                                                                                                                       | Mức độ  | Trạng thái |
| ----- | --------------------------------------------------------------------------------------------------------------------------------------------- | ------- | ---------- |
| RC-30 | Migration `V513__add_completed_at_to_tbl_dim_ticket.sql` chỉ thêm cột `completed_at TIMESTAMPTZ` nullable, không default, không index/constraint mới | Blocker | [ ]        |
| RC-31 | Không đổi định nghĩa cột `started_at` hiện có — chỉ đổi nguồn ghi dữ liệu                                                                        | Blocker | [ ]        |
| RC-32 | Không đụng `closed_at` hoặc `ck_ticket_date_order` — xác nhận constraint không tham chiếu `started_at`/`completed_at`                            | Blocker | [ ]        |
| RC-33 | Không có script backfill riêng đi kèm migration — dữ liệu populate qua scan cadence bình thường (H-SDD-LEAD-TIME-3)                              | Blocker | [ ]        |
| RC-34 | Version migration đúng thứ tự tiếp theo sau `V512` (không trùng version với migration khác đang phát triển song song)                            | Blocker | [ ]        |
| RC-35 | Rollback DB được tài liệu hóa là thao tác thủ công (`ALTER TABLE ... DROP COLUMN completed_at;`), không tự động qua Flyway migrate               | Major   | [ ]        |

## 6. Security/Privacy Review

| #     | Hạng mục                                                                                                                     | Mức độ | Trạng thái |
| ----- | ------------------------------------------------------------------------------------------------------------------------------ | ------ | ---------- |
| RC-36 | Không có data class mới nhạy cảm — cả 3 giá trị (2 timestamp + 1 duration) là ngày tháng, không phải PII/secret               | Major  | [ ]        |
| RC-37 | Không có external API call mới, không phát sinh token/credential exposure mới (đã loại bỏ phương án GitHub commit-history)    | Blocker | [ ]        |
| RC-38 | Không đổi authentication/authorization — view vẫn dưới session-based access hiện có, không có role/permission mới             | Blocker | [ ]        |
| RC-39 | Log WARN khi field malformed/missing không chứa PII — chỉ `ticketId`, `sourcePath`, raw date string (không phải business data nhạy cảm) | Major  | [ ]        |

## 7. Operation/Maintenance Review

| #     | Hạng mục                                                                                                                                                       | Mức độ | Trạng thái |
| ----- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------ | ---------- |
| RC-40 | Không thêm scheduled job/background worker mới — ghi diễn ra trong scan cadence hiện có                                                                        | Blocker | [ ]        |
| RC-41 | Field malformed/missing không escalate thành lỗi toàn bộ Artifact Scanner run (BR-SDD-LEAD-TIME-6) — chỉ log WARN, scan tiếp tục cho các artifact/ticket khác | Blocker | [ ]        |
| RC-42 | Hành vi "giá trị cũ bị null hóa khi field sau này malformed" (H-SDD-LEAD-TIME-4) được ghi nhận là **behavior chấp nhận**, không phải bug cần fix                | Major  | [ ]        |
| RC-43 | Không có config/feature flag mới được thêm không cần thiết (spec-pack không yêu cầu)                                                                            | Minor  | [ ]        |
| RC-44 | Gap hệ thống về ArchUnit test không tồn tại (`LayerEnforcementTest.java`, IMPL-OI-4) được ghi nhận là rủi ro tồn đọng, không tự ý viết test mới ngoài phạm vi ticket | Minor  | [ ]        |

## 8. Test Review

| #     | Hạng mục                                                                                                                                         | Mức độ | Trạng thái |
| ----- | ----------------------------------------------------------------------------------------------------------------------------------------------- | ------ | ---------- |
| RC-45 | Unit test cho utility chuẩn hoá ngày: full datetime passthrough, bare-date → `00:00:00`, malformed/empty/whitespace/full-width digit → `null`     | Major  | [ ]        |
| RC-46 | Unit test `computeLeadTimeDuration`: cặp hợp lệ, 1 input null, `completed < started`                                                              | Major  | [ ]        |
| RC-47 | Integration test Artifact Scanner persist: ghi đúng giá trị, re-scan overwrite (kể cả regressing về `NULL`), file thiếu → cột `NULL`, scan không fail | Blocker | [ ]        |
| RC-48 | Integration test `PmDashboardJdbcAdapter.findDetail`: trả đúng `startedAt`/`completedAt` bao gồm case null                                        | Major  | [ ]        |
| RC-49 | Regression test: hành vi order/filter hiện có của `PmDashboardJdbcAdapter` dựa trên `started_at` không đổi (chỉ dữ liệu đầu vào đổi)                | Blocker | [ ]        |
| RC-50 | Component test FE: `TicketInformationCard` render đúng format, đúng fallback `-`, dùng i18n key (không hardcode text)                              | Major  | [ ]        |
| RC-51 | `mvn clean verify` (BE) và `npm run typecheck && npm run build && npm test` (FE) đều pass trước khi merge                                          | Blocker | [ ]        |

## 9. Documentation/Traceability Review

| #     | Hạng mục                                                                                                                         | Mức độ | Trạng thái |
| ----- | ------------------------------------------------------------------------------------------------------------------------------- | ------ | ---------- |
| RC-52 | Bảng ánh xạ AC → RC ở cuối file này bao phủ đầy đủ AC-SDD-LEAD-TIME-1..12                                                        | Blocker | [ ]        |
| RC-53 | `self-review.md` được điền đầy đủ với bằng chứng file:line hoặc kết quả test cho từng RC Blocker/Major                            | Blocker | [ ]        |
| RC-54 | Không còn Human Decision hoặc Open Issue nào (spec-pack §16/§18, impl-plan §9) bị bỏ sót hoặc bị đổi ngầm so với trạng thái Closed | Blocker | [ ]        |
| RC-55 | `docs/changes/SDD-LEAD-TIME/` có đủ artifact theo pipeline chuẩn (spec-pack, context, impact-analysis, impl-plan, review-checklist, self-review) | Minor  | [ ]        |

## 10. Release/Rollback Review

| #     | Hạng mục                                                                                                                                        | Mức độ | Trạng thái |
| ----- | ------------------------------------------------------------------------------------------------------------------------------------------------ | ------ | ---------- |
| RC-56 | Migration `V513` được deploy trước hoặc cùng lúc với code BE/FE — tránh cửa sổ code mới chạy khi cột chưa tồn tại                                   | Blocker | [ ]        |
| RC-57 | Thứ tự rollback code đúng: Part C (FE) → Part B (BE contract) → Part A (BE parse/persist)                                                          | Major  | [ ]        |
| RC-58 | Không cần feature flag để rollout/rollback (additive, nullable, xác nhận không cần) — không có flag "thừa" nào được thêm vào                       | Minor  | [ ]        |
| RC-59 | Rollback DB (`DROP COLUMN completed_at`) được xác nhận là hành động thủ công, cần user/DBA approve trước khi chạy (`.claude/rules/00-safety.md §3`) | Blocker | [ ]        |

---

## Bảng ánh xạ AC → Checklist items

| #   | AC                        | Các hạng mục checklist xác nhận            |
| --- | -------------------------- | ------------------------------------------- |
| 1   | AC-SDD-LEAD-TIME-1        | RC-01, RC-05, RC-27, RC-28, RC-47            |
| 2   | AC-SDD-LEAD-TIME-2        | RC-01, RC-05, RC-27, RC-28, RC-30, RC-47     |
| 3   | AC-SDD-LEAD-TIME-3        | RC-01, RC-13, RC-14, RC-29, RC-47            |
| 4   | AC-SDD-LEAD-TIME-4        | RC-01, RC-13, RC-14, RC-29, RC-47            |
| 5   | AC-SDD-LEAD-TIME-5        | RC-01, RC-08, RC-45                          |
| 6   | AC-SDD-LEAD-TIME-6        | RC-01, RC-08, RC-29, RC-41, RC-45, RC-47     |
| 7   | AC-SDD-LEAD-TIME-7        | RC-01, RC-13, RC-17, RC-18, RC-50            |
| 8   | AC-SDD-LEAD-TIME-8        | RC-01, RC-11, RC-28, RC-47                   |
| 9   | AC-SDD-LEAD-TIME-9        | RC-01, RC-03, RC-15, RC-16, RC-24, RC-46     |
| 10  | AC-SDD-LEAD-TIME-10       | RC-01, RC-22, RC-49                          |
| 11  | AC-SDD-LEAD-TIME-11       | RC-01, RC-16, RC-46                          |
| 12  | AC-SDD-LEAD-TIME-12       | RC-01, RC-11, RC-33, RC-47                   |
