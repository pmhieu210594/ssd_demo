# Báo cáo thay đổi — SDD-LEAD-TIME (Lead Time visibility: spec-pack created time, report updated time, computed duration)

- **Ticket:** SDD-LEAD-TIME
- **Trạng thái:** Implemented — chờ DB migration approval + manual/E2E verification
- **Tạo ngày:** 2026-08-21

> Đối tượng đọc: kỹ sư tiếp theo, người review, người trực on-call.
> Nguồn tham chiếu: `spec-pack.md`, `context.md`, `impact-analysis.md`, `impl-plan.md`,
> `review-checklist.md`, `self-review.md`, `test-plan.md`, `test-results.md`,
> `blackbox-testcases.md`, `test-data.md`, `sources.md`.

---

## 1. Tóm tắt thay đổi

**Ticket:** SDD-LEAD-TIME
**Ngày:** 2026-08-21
**Tác giả:** nvt_dung (có hỗ trợ từ Claude)

### Đã thay đổi gì

- PM Dashboard's Ticket Detail drawer (`TicketInformationCard`) hiển thị thêm 3 thông tin mới:
  spec-pack's created time (`**Create date**` header), report.md's updated time (`**Update date**`
  header), và một duration được tính giữa hai mốc đó (`"1d 8h"`-style, localize theo i18n hiện tại).
- Cả hai timestamp được sinh ra như side-effect của pass parse hiện có của Artifact Scanner
  (`SpecPackMarkdownParser`/`ReportMarkdownParser` → `ArtifactScannerService`) và persist vào
  `tbl_dim_ticket.started_at` (cột cũ, nay được populate) / `tbl_dim_ticket.completed_at` (cột mới,
  migration `V513`). Duration được tính hoàn toàn ở FE, không lưu DB, không có API riêng.

### Lý do

- Project manager hiện phải mở repository trực tiếp để biết `spec-pack.md` được tạo khi nào và
  `report.md` được cập nhật lần cuối khi nào — không có visibility trong app. Ticket bổ sung 2
  timestamp + 1 duration vào UI đã có, không xây pipeline/endpoint mới.

---

## 2. Tương ứng specification / AC

| AC | Nội dung tóm tắt | Bằng chứng implementation | Bằng chứng test | Trạng thái |
|---|---|---|---|---|
| AC-SDD-LEAD-TIME-1 | `spec-pack.md` hợp lệ → `started_at` populated sau scan | `ArtifactScannerService.persistSpecPackParse` đọc `headerMetadata().get("create_date")`, normalize qua `HeaderDateNormalizer`, gọi `updateTicketStartedAt` | `HeaderDateNormalizerTest` (7 case), `ArtifactScannerServiceTest` (24 case, fake port) | PASS (unit-level; xem §9 về DB thật) |
| AC-SDD-LEAD-TIME-2 | `report.md` hợp lệ → `completed_at` populated sau scan | `persistReportParse` đọc `update_date`, gọi `updateTicketCompletedAt`; migration `V513` thêm cột | Như trên | PASS (unit-level) |
| AC-SDD-LEAD-TIME-3 | `spec-pack.md` không tồn tại → `started_at` NULL, UI `-` | `formatDateTime` trả `-` cho null; không throw khi field thiếu | `ArtifactScannerServiceTest`, `TicketDetailDrawer.test.tsx` | PASS |
| AC-SDD-LEAD-TIME-4 | `report.md` không tồn tại → `completed_at` NULL, UI `-` | Như trên, phía `completed_at` | Như trên | PASS |
| AC-SDD-LEAD-TIME-5 | Bare `YYYY-MM-DD` → hiển thị `YYYY-MM-DD 00:00:00` | `HeaderDateNormalizer.normalize` | `HeaderDateNormalizerTest` | PASS |
| AC-SDD-LEAD-TIME-6 | Malformed → NULL, không fail scan | try/catch nội bộ quanh normalize+persist trong cả 2 persist method, log WARN | `HeaderDateNormalizerTest`, `ArtifactScannerServiceTest` | PASS |
| AC-SDD-LEAD-TIME-7 | 3 field hiển thị i18n, không hardcode | `TicketDetailDrawer.tsx` dùng `t("Pages.PmDashboard.<key>", {defaultValue})`; 5 key mới × 3 locale | `TicketDetailDrawer.test.tsx` | PASS |
| AC-SDD-LEAD-TIME-8 | Re-scan sau khi `report.md` đổi → `completed_at` cập nhật | Luôn overwrite (không `COALESCE`) trong `updateTicketCompletedAt` | `ArtifactScannerServiceTest` (re-scan case) | PASS |
| AC-SDD-LEAD-TIME-9 | Duration tính từ `completed_at - started_at`, hiển thị cùng 2 timestamp | `computeLeadTimeDuration` (FE, `src/utils/leadTime.ts`), gọi trong `TicketInformationCard` | `leadTime.test.ts` (5 case), `TicketDetailDrawer.test.tsx` | PASS |
| AC-SDD-LEAD-TIME-10 | `PmDashboardJdbcAdapter` hành vi hiện có với `started_at` không regress | Không đổi logic order/filter, chỉ thêm cột SELECT | Toàn bộ 637 BE test pass, không sửa assertion order/filter cũ | PASS (unit/mock-level; xem §9) |
| AC-SDD-LEAD-TIME-11 | Duration `-` khi null hoặc `completed_at < started_at` | Guard `if` sớm trong `computeLeadTimeDuration`, không try/catch | `leadTime.test.ts` | PASS |
| AC-SDD-LEAD-TIME-12 | Ticket cũ tự populate ở lần scan kế tiếp, không cần backfill | Cùng code path persist mỗi lần scan — không có job/API riêng | `ArtifactScannerServiceTest` (re-scan ticket cũ) | PASS (thiết kế xác nhận qua unit test; chưa chạy trên ticket cũ thật trong môi trường dev) |

Toàn bộ 12 AC đều có ít nhất 1 test tự động pass. Điểm cần lưu ý: coverage ở mức unit/mock (fake
port, mock JDBC) — chưa có xác nhận round-trip trên Postgres thật hoặc UI thật (xem §6, §9).

---

## 3. Phạm vi ảnh hưởng

| # | Khu vực | Chi tiết |
|---|---|---|
| 1 | Các tệp đã thay đổi | 14 file sửa/thêm ở BE (7), DB (1 migration), FE (6) — danh sách đầy đủ ở `self-review.md` §2 (mục 1–14); cộng 7 file test (mục 15–21) |
| 2 | Lược đồ DB | `tbl_dim_ticket` +1 cột: `completed_at TIMESTAMPTZ` nullable, không default (migration `V513__add_completed_at_to_tbl_dim_ticket.sql`, **chưa chạy trên bất kỳ DB nào**) |
| 3 | Hợp đồng API | `GET /api/v1/pm/dashboard/tickets/{ticketId}/detail` — thêm 2 field nullable (`startedAt`, `completedAt`) vào response, additive/backward-compatible, không có endpoint mới |
| 4 | Cấu hình | Không có config/feature flag mới |
| 5 | Nhật ký | 1 log WARN mới khi header date field missing/malformed (chỉ chứa `ticketId`, `sourcePath`, message lỗi — không PII) |
| 6 | Quyền/Vai trò | Không đổi — vẫn session-based access hiện có trên view read-only |

Chi tiết đầy đủ: `impact-analysis.md`.

---

## 4. Nội dung implement

- **BE parse/persist**: `HeaderDateNormalizer` (utility dùng chung, `domain/service/markdown/core`,
  không throw); 2 method mới trên `ArtifactScannerPersistencePort`
  (`updateTicketStartedAt`/`updateTicketCompletedAt`) — quyết định dùng 2 method riêng thay vì 1
  method chung để loại rủi ro ghi đè nhầm cột (IMPL-OI-1); implement trong
  `ArtifactScannerJdbcAdapter` bằng `jdbc.update(...)` tuần tự, luôn overwrite kể cả về `NULL`.
- **DB**: migration `V513` — chỉ `ALTER TABLE tbl_dim_ticket ADD COLUMN completed_at TIMESTAMPTZ;`,
  không backfill, không constraint mới. **File đã tạo, chưa chạy** trên bất kỳ môi trường nào.
- **BE contract**: `PmDashboardJdbcAdapter` (SQL của cả `findDetail` và `baseTicketQuery()` sửa
  đồng thời để tránh lệch dữ liệu), `PmDashboardModels.DashboardTicketRow` (2 field mới trên cả
  canonical + compact constructor, đã grep xác nhận không có caller khác bị ảnh hưởng), và
  `PmDashboardDtos.PmDashboardTicketRowDto` — **file này phát sinh ngoài danh sách ban đầu của
  `impact-analysis.md`/`impl-plan.md` §2.1**, phát hiện trong lúc code vì đây mới là DTO thực sự đi
  ra HTTP response (không phải `DashboardTicketDetail` thô).
- **FE**: `PmDashboardTicketRow` (`api.ts`) +2 field; pure helper mới `computeLeadTimeDuration`
  (`src/utils/leadTime.ts`) — nhận `t` (translate function) trực tiếp thay vì `language` string như
  impl-plan đề xuất ban đầu, để tái dùng đúng instance `t` của component cha và dễ test hơn; 3 dòng
  mới trong `TicketInformationCard`; 5 key i18n (`startedAt`, `completedAt`, `leadTimeDuration`,
  `durationDayUnit`, `durationHourUnit` — 2 key unit bổ sung so với đề xuất ban đầu để tránh
  hardcode "d"/"h") ở cả `en`/`vi`/`ja`.

**2 lệch có chủ đích so với `impl-plan.md`, đã xác nhận với user trước khi code** (chi tiết:
`self-review.md` §1, §7):

1. Không dùng `TransactionTemplate` riêng cho 2 method update mới — pattern này không tồn tại ở bất
   kỳ đâu trong `ArtifactScannerService`/`ArtifactScannerJdbcAdapter`; dùng đúng convention hiện có
   của class (`jdbc.update(...)` tuần tự, giống `updateSnapshotParsedSummary`). Hành vi
   (always-overwrite kể cả về NULL) không đổi.
2. Thêm `PmDashboardDtos.java` vào scope — bắt buộc, additive, không phải mở rộng phạm vi spec.

---

## 5. Kết quả review

### Tự kiểm tra (`self-review.md`)

- Tất cả 59 hạng mục Review Checklist (`review-checklist.md`) được self-check; toàn bộ mức
  **Blocker** đã ✅ ngoại trừ 2 mục thuộc phạm vi vận hành (không phải code):
  - **RC-56** — Migration `V513` chưa chạy trên bất kỳ DB nào (cần user/DBA xác nhận theo
    `.claude/rules/00-safety.md §3` trước khi deploy code phụ thuộc cột `completed_at`).
  - **RC-59** — Rollback DB (`DROP COLUMN completed_at`) chưa có ai approve/thực hiện — đúng như kỳ
    vọng ở giai đoạn implementation.
- 2 mục đánh dấu `[~]` (có bằng chứng nhưng cần reviewer xác nhận thêm):
  - **RC-10** — Không dùng `TransactionTemplate` riêng (lệch có chủ đích, xem §4 mục 1).
  - **RC-14** — `startedAt`/`completedAt`/duration luôn render (dùng `formatDateTime`'s built-in
    `-` fallback) thay vì conditional truthy-guard như `mergedAt` — khớp với cách `createAt`/
    `updatedAt` đã làm, và khớp đúng ý spec-pack §2.1 ("Show a default fallback value `-`"), nhưng
    cần reviewer xác nhận cách hiểu này đúng.
- Tất cả AC được đáp ứng: **Có** (12/12, xem §2 ở trên).

### Review bởi Codex

- Không chạy trong phiên này.

### Các phát hiện từ review thủ công (trong phiên làm việc)

| # | Phát hiện | Mức độ nghiêm trọng | Hành động đã thực hiện |
|---|---|---|---|
| 1 | `PmDashboardDtos.java` là DTO thực sự trả JSON, không nằm trong danh sách file ban đầu của impact-analysis/impl-plan | Blocker (nếu bỏ sót thì response FE nhận thiếu field) | Đã bổ sung vào scope, cập nhật `self-review.md` mục 1/7 |
| 2 | Không có `TransactionTemplate` pattern để tái sử dụng như impl-plan giả định | Major | Xác nhận với user, đổi sang convention hiện có (`jdbc.update` tuần tự), ghi nhận RC-10 |

---

## 6. Kết quả kiểm thử

| Loại kiểm thử | Lệnh | Kết quả | Ghi chú |
|---|---|---|---|
| BE UT | `mvn -o test` | PASS (637/637) | Offline mode, tương đương phần test của `mvn clean verify` |
| FE UT/Component | `npm run test:unit` | PASS (348/348) | Bao gồm `leadTime.test.ts` (5) và `TicketDetailDrawer.test.tsx` (+1) |
| FE typecheck | `npx tsc --noEmit` | PASS (0 lỗi) | Không có script `typecheck` riêng trong `package.json` |
| FE build (bundle) | `npm run build` | NOT RUN | `tsc --noEmit` đã xác nhận phần compile-check |
| DB-level Integration | — | NOT RUN | Không tồn tại Testcontainers/DB test harness nào trong repo (dependency khai báo, không dùng ở đâu) — gap có sẵn của hệ thống |
| E2E (Playwright) | `npx playwright test` | NOT RUN | Không có dev server (BE+FE+DB) sống trong phiên này |
| Black-box (manual) | thủ công | NOT RUN | 4 kịch bản trong `blackbox-testcases.md`/`impl-plan.md` §8.2 chưa chạy trên UI thật |
| DB migration | `mvn flyway:migrate` (V513) | NOT RUN | Yêu cầu xác nhận người dùng/DBA riêng theo `.claude/rules/00-safety.md §3` |

**Verdict tổng thể: PARTIAL** — 985/985 automated test pass, bao phủ đủ 12/12 AC, nhưng chưa có xác
nhận DB thật (Postgres) và chưa có xác nhận UI/E2E trực quan. Không có test nào fail.

Chi tiết đầy đủ: `docs/changes/SDD-LEAD-TIME/test-results.md`.

---

## 7. Quan điểm security / operation

- **Security**: Không có data class mới nhạy cảm (3 giá trị đều là ngày tháng nội bộ từ evidence
  file, không phải PII/secret). Không có external API call mới (đã loại bỏ phương án GitHub
  commit-history). Không đổi authentication/authorization — vẫn session-based access hiện có.
- **Operation**: Không có scheduled job/background worker mới — ghi diễn ra trong scan cadence hiện
  có. Field malformed/missing chỉ log WARN (`ticketId`, `sourcePath`, message lỗi — không PII),
  không escalate thành lỗi toàn bộ scan run. Rollback code là revert chuẩn (không có feature flag).
  Rollback DB là thao tác thủ công (`ALTER TABLE ... DROP COLUMN completed_at;`), cần DBA/user
  approve — không tự động qua Flyway.

---

## 8. Accepted risk

| # | Rủi ro | Nguồn | Ghi chú |
|---|---|---|---|
| 1 | Một timestamp/duration đã hiển thị trước đó có thể biến mất khỏi dashboard sau khi file nguồn bị sửa khiến field malformed | H-SDD-LEAD-TIME-4, closed | Xác nhận là hành vi mong muốn, không phải bug — cột luôn bị null hóa lại thay vì giữ giá trị cũ |
| 2 | Không có ArchUnit test thực sự chạy được để tự động verify layer boundary cho port/adapter mới | IMPL-OI-4, closed | Gap có sẵn của hệ thống (`docs/architecture/test-map.md` trỏ tới `LayerEnforcementTest.java` — file không tồn tại); đã review layer boundary thủ công thay thế |
| 3 | Không có DB-level integration test (Postgres thật) cho 2 method persist mới và SQL mới của `PmDashboardJdbcAdapter` | Test-plan §8 mục 1 | Không tồn tại Testcontainers harness trong repo; SQL correctness chỉ xác nhận qua fake-port/mock unit test + review diff thủ công |

---

## 9. Open Issues

Không còn Open Issue nào ở cấp spec-pack/impl-plan — toàn bộ H-1..5 (spec-pack §16), OI-1..3
(spec-pack §18), và IMPL-OI-1..4 (impl-plan §9) đã **Closed**.

Việc còn mở ở cấp vận hành/kiểm thử (không phải quyết định thiết kế còn treo):

| # | Việc còn mở | Ai chốt | Block gì |
|---|---|---|---|
| 1 | Chạy migration `V513` trên DB dev/test/production | User/DBA | Deploy code BE/FE phụ thuộc cột `completed_at` (RC-56) |
| 2 | Chạy 4 kịch bản manual/black-box trên dev server thật | QA/reviewer | Xác nhận trực quan trước khi merge (không block vì logic đã có test coverage) |
| 3 | Thêm DB-level integration test (Testcontainers) cho `updateTicketStartedAt`/`updateTicketCompletedAt` và SQL mới | BE lead (follow-up, ngoài scope ticket này) | Không block merge — accepted risk #3 ở §8 |

---

## 10. Human Decisions

| ID | Quyết định | Nguồn | Trạng thái |
|---|---|---|---|
| H-SDD-LEAD-TIME-1 | Đọc "created time" từ header `**Create date**` của `spec-pack.md` | spec-pack §16 | Closed |
| H-SDD-LEAD-TIME-2 | Đọc "updated time" từ header `**Update date**` sẵn có của `report.md`, không thêm field mới | spec-pack §16 | Closed |
| H-SDD-LEAD-TIME-3 | Dùng scan/parse mechanism hiện có, không backfill job riêng | spec-pack §16 | Closed |
| H-SDD-LEAD-TIME-4 | Field malformed/missing sau này → cột về `NULL`, không giữ giá trị cũ | spec-pack §16 | Closed |
| H-SDD-LEAD-TIME-5 | Có tính và hiển thị duration | spec-pack §16 | Closed |
| IMPL-OI-1 | Dùng 2 method port riêng (không 1 method chung) để tránh ghi đè nhầm cột | impl-plan §9 | Closed |
| IMPL-OI-2 | Tên 3 key i18n chính thức (`startedAt`/`completedAt`/`leadTimeDuration`) | impl-plan §9 | Closed |
| IMPL-OI-3 | Xác nhận `ArtifactScannerJdbcAdapter` là adapter duy nhất implement port | impl-plan §9 | Closed |
| IMPL-OI-4 | Không có ArchUnit test thực chạy được — review layer boundary thủ công thay thế | impl-plan §9 | Closed (ghi nhận thành rủi ro tồn đọng, §8 mục 2) |
| (không có ID) | Không dùng `TransactionTemplate` — theo convention hiện có của class | self-review §1, xác nhận qua AskUserQuestion trong phiên implementation | Closed |
| (không có ID) | Bổ sung `PmDashboardDtos.java` vào scope | self-review §1 | Closed |

---

## 11. Source Analysis Limitations

- **Chưa đọc đầy đủ trong Phase 1** (theo `sources.md`): toàn bộ phần còn lại của
  `ArtifactScannerService.java` ngoài scan/parse/persist call site; toàn bộ `PmDashboardJdbcAdapter`
  ngoài phần liên quan `started_at`; GitHub webhook signature code (không liên quan); các parser
  khác (`selfreview`, `reviewchecklist`, `aireviewstats`) — không bị ảnh hưởng bởi ticket này.
- **Correction phát hiện ở Phase 3**: `context.md` (Phase 2) khẳng định `ja/locale.json` **thiếu**
  key `createAt`/`mergedAt`/`ticketInformation`. Đọc trực tiếp file ở Phase 3 xác nhận **3 key này
  đã tồn tại sẵn** — claim ban đầu là stale/sai. Đã sửa trong `impact-analysis.md` §14; không backfill
  gì thêm vì hành vi dự kiến của ticket (chỉ thêm 5 key mới của chính nó) không đổi dù đúng hay sai.
- **Chưa xác minh trên dữ liệu thật**: nội dung `spec-pack.md`/`report.md` hiện có của các ticket
  khác trong repo không được kiểm tra toàn bộ trong Phase 1 (không cần thiết — H-3 xác nhận không
  cần backfill riêng).
- **Phát hiện muộn ở Phase 4 (implementation)**: `PmDashboardDtos.java` không nằm trong bất kỳ danh
  sách "file cần đọc"/"file bị ảnh hưởng" nào của Phase 2/3 (`context.md`, `impact-analysis.md`,
  `impl-plan.md`) dù đây là lớp DTO thực sự đi ra HTTP response — xem §4, §13 (Failure Mode Index).

---

## 12. What worked

- `headerMetadata()`/`extractHeaderMetadata()` đã tồn tại sẵn và không cần sửa bất kỳ parser nào —
  giảm đáng kể bề mặt thay đổi so với dự kiến ban đầu.
- Đóng toàn bộ Human Decision và Open Issue ngay ở Phase 1-3 (trước khi code) giúp Phase 4 không có
  điểm mơ hồ nào cần dừng lại hỏi — chỉ phát sinh 2 lệch nhỏ, cả hai đều được xác nhận nhanh qua
  `AskUserQuestion` trong lúc code thay vì đoán.
- Quyết định dùng 2 method port riêng (`updateTicketStartedAt`/`updateTicketCompletedAt`) thay vì 1
  method chung (IMPL-OI-1) loại bỏ hẳn một lớp rủi ro (ghi đè nhầm cột) trước khi có dòng code nào.
- Test-plan chủ động liệt kê "Test đã bỏ qua có chủ đích" (§8) với lý do rõ ràng — giúp reviewer
  phân biệt được gap có chủ đích với gap bị bỏ sót.

## 13. What failed

- Danh sách file bị ảnh hưởng ở Phase 2/3 (`context.md`, `impact-analysis.md`, `impl-plan.md`) bỏ
  sót `PmDashboardDtos.java` — lớp DTO thực sự map ra HTTP JSON response, khác với
  `DashboardTicketRow`/`DashboardTicketDetail` (application-layer model) mà các phase trước đã đọc
  và liệt kê. Nếu không phát hiện lúc code, 2 field mới sẽ không bao giờ tới được FE dù toàn bộ BE
  logic phía sau đều đúng.
- Repo hoàn toàn không có hạ tầng test tích hợp DB thật (Testcontainers dependency có khai báo
  nhưng không dùng ở đâu) — đây không phải lỗi của ticket này nhưng khiến AC-1/2/9/10 chỉ được xác
  nhận ở mức mock/fake, không phải round-trip thật.
- `context.md` (Phase 2) đưa ra một nhận định sai (gap của `ja/locale.json`) mà không được double
  -check bằng cách đọc trực tiếp file cho tới tận Phase 3.

## 14. Ứng viên cập nhật Failure Mode Index

- **Failure mode**: "Impact-analysis/context liệt kê application-layer model (`DashboardTicketRow`)
  nhưng bỏ sót lớp DTO/mapping riêng biệt map ra HTTP response (`PmDashboardDtos`)."
  - **Trigger**: Codebase có 1 lớp record/model nội bộ + 1 lớp DTO riêng cho response — chỉ đọc lớp
    model không đủ để xác định toàn bộ điểm cần sửa cho một thay đổi field additive.
  - **Prevention**: Khi mở rộng field trên response API, luôn trace từ Controller xuống tận điểm
    trả JSON thực tế (grep response type của `@RestController` method), không dừng lại ở
    application-layer record khi chưa xác nhận nó có được serialize trực tiếp hay không.
  - **Detection**: Compile error/test fail nếu field bị thiếu ở tầng DTO nhưng có ở tầng model —
    nhưng chỉ phát hiện được nếu có test xác nhận response JSON thực tế (không chỉ mock ở tầng
    Service).

## 15. Ứng viên cập nhật Living Docs

- Repo-wide gap: không có Testcontainers-based DB integration test harness nào tồn tại, dù
  dependency đã khai báo trong `pom.xml`. Đây là gap lặp lại (không riêng ticket này) — candidate
  để mở một ticket/doc riêng đề xuất base class + test profile cho DB integration test, thay vì mỗi
  ticket tự ghi nhận "chưa có hạ tầng" trong `test-plan.md` §8 của riêng nó. Xem
  `promotion-candidates.md` mục "Human Approval Required".
- `docs/architecture/test-map.md` trỏ tới `EDCAP_BE/src/test/java/com/sdd/platform/architecture/
  LayerEnforcementTest.java` — file này không tồn tại (xác nhận qua glob toàn bộ `src/test/java`).
  Tài liệu bị stale — candidate cập nhật `test-map.md` để phản ánh đúng trạng thái (không có ArchUnit
  test tự động, chỉ có dependency khai báo).

---

## 16. Công việc còn lại / Hành động tiếp theo

| # | Công việc | Người phụ trách | Hạn chót |
|---|---|---|---|
| 1 | Xác nhận và chạy migration `V513` trên DB dev/test trước khi deploy code BE/FE phụ thuộc | User/DBA | Trước deploy |
| 2 | Chạy 4 kịch bản manual/black-box trên dev server thật (`blackbox-testcases.md` TC-1..4 ưu tiên P0/P1) | QA/reviewer | Trước merge (khuyến nghị) |
| 3 | Xác nhận layer boundary thủ công cho port method mới + JDBC adapter (không có ArchUnit tự động) | BE reviewer | Trước merge |
| 4 | (Follow-up, ngoài ticket này) Đề xuất hạ tầng Testcontainers cho DB integration test | BE lead | Chưa xác định |

---

## 17. Quy trình hoàn tác

1. Revert code theo thứ tự ngược: Part C (FE) → Part B (BE contract) → Part A (BE parse/persist),
   theo `impl-plan.md` §7.1. Không có feature flag cần tắt, không có client cache cần clear.
2. Nếu migration `V513` đã chạy và cần rollback: chạy thủ công
   `ALTER TABLE tbl_dim_ticket DROP COLUMN completed_at;` (không tự động qua Flyway) — cần DBA/user
   approve trước, rủi ro mất dữ liệu thấp vì cột hoàn toàn derived, sẽ tự populate lại ở lần scan kế
   tiếp nếu thêm lại cột.

---

## 18. Danh mục đầu ra

| # | Tệp | Mục đích |
|---|---|---|
| 1 | `docs/changes/SDD-LEAD-TIME/sources.md` | Nguồn Phase 1 |
| 2 | `docs/changes/SDD-LEAD-TIME/spec-pack.md` | Nguồn tham chiếu duy nhất cho spec & AC |
| 3 | `docs/changes/SDD-LEAD-TIME/context.md` | Bối cảnh code hiện có, pattern cần theo |
| 4 | `docs/changes/SDD-LEAD-TIME/impact-analysis.md` | Phân tích phạm vi ảnh hưởng |
| 5 | `docs/changes/SDD-LEAD-TIME/impl-plan.md` | Cách tiếp cận triển khai & các bước thực hiện |
| 6 | `docs/changes/SDD-LEAD-TIME/review-checklist.md` | Các góc nhìn review |
| 7 | `docs/changes/SDD-LEAD-TIME/self-review.md` | Kết quả tự kiểm tra sau implementation |
| 8 | `docs/changes/SDD-LEAD-TIME/test-plan.md` | Kế hoạch bao phủ kiểm thử |
| 9 | `docs/changes/SDD-LEAD-TIME/test-results.md` | Kết quả thực thi kiểm thử |
| 10 | `docs/changes/SDD-LEAD-TIME/blackbox-testcases.md` | Các test case black-box |
| 11 | `docs/changes/SDD-LEAD-TIME/test-data.md` | Bộ dữ liệu kiểm thử |
| 12 | `docs/changes/SDD-LEAD-TIME/report.md` | Báo cáo tổng hợp (file này) |
| 13 | `docs/changes/SDD-LEAD-TIME/promotion-candidates.md` | Ứng viên cập nhật Living Docs/Failure Mode Index |
