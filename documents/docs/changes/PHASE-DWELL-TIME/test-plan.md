# test-plan

**Ticket ID**: PHASE-DWELL-TIME
**Create date**: 2026-08-21
**Author**: Test Strategist (Claude)
**Update date**: 2026-08-21

## Mục tiêu

Đưa 9 AC hiện có ở `spec-pack.md §6` (AC-1, AC-2, AC-3, AC-4, AC-6, AC-7, AC-8, AC-9, AC-10 —
không có AC-5) vào một chiến lược test cụ thể: loại test nào bảo vệ AC nào, test nào đã đủ
(tái dùng), test nào cần thêm, và vùng nào cố ý bỏ qua kèm rủi ro còn lại. Ưu tiên theo giá trị/chi
phí — không tạo đủ 7 loại test cho mọi AC. Repo hiện **không có hạ tầng DB thật trong test** (không
`Testcontainers`, không `@JdbcTest` chạy Postgres thật — xác nhận bằng grep `Testcontainers`/`@Container`
= 0 kết quả); mọi "BE UT" cho SQL dùng pattern mock `NamedParameterJdbcTemplate` + `ArgumentCaptor` bắt
chuỗi SQL, đúng pattern đã có sẵn ở `PmDashboardJdbcAdapterFindTemplateUsageTest.java`. Đây là giới hạn
thật của môi trường, không phải lựa chọn tuỳ tiện — được ghi rõ như một rủi ro còn lại (§ Rủi ro còn lại),
không phải một "gap được bỏ qua âm thầm".

## Ma trận AC ↔ loại test

| AC ID | Mô tả ngắn | FE UT | BE UT | API IT | Contract | DB/Migration | E2E | Black-box |
|---|---|---|---|---|---|---|---|---|
| AC-1 | Hiển thị đúng 7 phase `1,3,4,5,6,7,8` theo `phase_order`, loại `0-A,0-B,2,9` | ✅ (sort/order render) | ✅ (SQL chứa đúng 7 phase_code, loại trừ 4 code kia) | — | — | — | — | ✅ (BB, xem `blackbox-testcases.md`) |
| AC-2 | Σ dwell time các file đủ cặp trong 1 phase, format `hh:mm:ss` không giới hạn 24h | — | ✅ (service parse — đã có; SQL SUM/EXTRACT — mock guard mới) | — | — | ⚠️ residual (cần Postgres thật, xem Rủi ro) | — | ✅ |
| AC-3 | Phase không có file nào có `create_date` → `"-"` | ✅ (render null → `-`) | ✅ (mock: LEFT JOIN giữ dòng phase dù không match) | — | — | ⚠️ residual | — | ✅ |
| AC-4 | File thiếu `update_date` không đóng góp vào tổng; không còn file đủ cặp → `"-"` | ✅ (đã có, mix có/không giá trị) | ✅ (service: đã có; SQL: mock guard mới cho điều kiện NOT NULL trong ON) | — | — | ⚠️ residual | — | ✅ |
| AC-6 | Rescan cùng `content_hash` không đổi giá trị đã tính | — | ✅ (mock: SQL dùng đúng `ON CONFLICT (artifact_snapshot_id) DO NOTHING`) | — | — | ⚠️ residual (cần chạy scan 2 lần thật trên Postgres) | — | — |
| AC-7 | Không đổi "current phase" hiện tại (`PhaseCard`/`TicketPhaseEvaluatorService`) | được bảo đảm bởi test hiện có | được bảo đảm bởi test hiện có | — | — | — | — | được bảo đảm bởi test hiện có |
| AC-8 | Tên feature đúng 3 locale EN/JA/VI | ✅ (mới: đọc JSON, không cần render) | — | — | — | — | — | ✅ |
| AC-9 | Không ALTER/DROP bảng hiện có, chỉ thêm mới | — | ✅ (mới: đọc nội dung file migration, đúng pattern `CiRunMetadataMigrationTest`) | — | — | ✅ (cùng 1 test, phân loại kép) | — | — |
| AC-10 | Lỗi/timeout khi lấy dwell time → `"-"`, không throw, không retry | — | ✅ (mới: ép `jdbc.query` throw, assert catch trả `[]`, không lan lên `findDetail`) | được bảo đảm bởi test hiện có (Controller không đổi hành vi lỗi) | — | — | — | ✅ |

Ghi chú ký hiệu: ✅ = có/sẽ có test cụ thể; "được bảo đảm bởi test hiện có" = không cần thêm gì;
⚠️ residual = cố ý không test lần này, có lý do + rủi ro ở 2 mục cuối; "—" = không áp dụng/không có
giá trị đủ lớn để tạo loại test đó cho AC này (xem lý do theo từng dòng bên dưới).

**Vì sao không có Contract test / E2E riêng cho ticket này**: không có endpoint mới (chỉ mở rộng field
trên `PmDashboardTicketDetail` đã tồn tại — không đổi shape cũ, không đổi request), nên không có hợp
đồng API mới cần pin lại bằng test riêng; E2E (Playwright) cho toàn bộ `TicketDetailDrawer` đã tồn tại ở
cấp trang khác (nếu có) và không có giá trị tăng thêm khi field mới chỉ là 1 Card đọc-only hiển thị chuỗi
tĩnh — FE UT mount thật `TicketDetailDrawer` (không phải Card cô lập) đã đóng vai trò "AC Closure" theo
`40-testing.md`, tương đương giá trị một E2E hẹp mà rẻ hơn nhiều.

## Ưu tiên

| test item | priority | reason |
|---|---|---|
| BE UT mới: `findPhaseDwellTime` SQL — đúng 7 phase_code, loại 0-A/0-B/2/9, ORDER BY phase_order | P0 | AC-1 là điều kiện tiên quyết hiển thị đúng — sai ở đây làm hỏng toàn bộ card, và đã có tiền lệ lỗi loại này ở `findTemplateUsage` (comment trong test hiện có cảnh báo đúng rủi ro tương tự: JOIN sai điều kiện có thể để lọt phase ngoài phạm vi) |
| BE UT mới: ép lỗi SQL → catch → `[]`, không throw (AC-10) | P0 | Đây là hợp đồng fail-soft cốt lõi của toàn bộ tính năng (Gate #3 đã RESOLVED) — nếu vỡ, 1 subquery phụ có thể làm sập cả `/detail` |
| BE UT mới: SQL dùng đúng điều kiện `document_create_at IS NOT NULL AND document_update_at IS NOT NULL` trong `ON` (AC-3/AC-4) | P0 | Đây là quy tắc nghiệp vụ dễ bị "tối ưu nhầm" khi refactor sau này (ai đó chuyển điều kiện ra `WHERE` sẽ biến LEFT JOIN thành INNER JOIN ngầm, giống cảnh báo đã ghi trong `findTemplateUsage_scopesTenantInsideLeftJoinOnClause...`) |
| BE UT mới: SQL dùng đúng `ON CONFLICT (artifact_snapshot_id) DO NOTHING` (AC-6, phần ghi) | P1 | Bảo vệ cơ chế idempotency ở mức "đúng câu lệnh được sinh ra"; không thay được test DB thật nhưng là rào chắn rẻ, phát hiện ngay nếu ai đó đổi thành `DO UPDATE` |
| BE UT mới: migration `V512` không chứa `ALTER TABLE`/`DROP TABLE`/`DROP COLUMN` (AC-9) | P1 | Rẻ, tự động hoá một AC business quan trọng (không phá schema hiện có) thay vì chỉ dựa vào review bằng mắt |
| FE UT mới: `PhaseDwellTimeCard` tự sắp xếp lại theo `phaseOrder` khi input không theo thứ tự (AC-1) | P1 | Input hiện tại trong fixture test đã đúng thứ tự sẵn — chưa test được nhánh sort thực sự chạy; nếu BE trả sai thứ tự (do lỗi tương lai), FE phải tự vệ đúng như code đã viết |
| FE UT mới: đọc trực tiếp 3 file `locale.json` (en/ja/vi), assert đúng giá trị `phaseDwellTime` (AC-8) | P2 | Rẻ (không cần render), nhưng giá trị thấp hơn các mục P0/P1 vì lỗi copy-paste text hiển thị ít gây hư hại nghiêm trọng — vẫn đáng làm vì test hiện có mock `t()` trả về `defaultValue`, nên 3 chuỗi JA/VI thật **chưa từng được assert bởi bất kỳ test tự động nào** |
| DB IT thật (Testcontainers) cho AC-2/AC-6 | Không làm lần này (xem Rủi ro còn lại) | Chi phí hạ tầng cao (thêm dependency, container, CI job mới) so với 1 ticket phạm vi nhỏ, additive-only; rủi ro được chấp nhận có kiểm soát, không phải bỏ qua vô căn cứ |

## Tái sử dụng test hiện có

| existing test | path | covers | gap |
|---|---|---|---|
| `PmDashboardServiceTest` (`detail_requiresPmRole`, `detail_allowsProjectRolePmEvenWhenSystemRoleIsDifferent`) | `EDCAP_BE/src/test/UnitTest/.../pmdashboard/PmDashboardServiceTest.java` | Auth/permission cho endpoint `/detail` (role PM bắt buộc) — field `phaseDwellTime` đi qua cùng endpoint này, không có auth riêng | Không có gap cho AC này — permission không đổi vì không có endpoint mới |
| `TicketPhaseEvaluatorServiceTest` | `EDCAP_BE/src/test/UnitTest/.../phase/TicketPhaseEvaluatorServiceTest.java` | Logic xác định "current phase" hoàn toàn tách biệt, không bị sửa | Đảm bảo AC-7 — không cần thêm gì, chỉ cần test này tiếp tục PASS (đã xác nhận PASS ở `self-review.md §4`) |
| `PmDashboardJdbcAdapterFindTemplateUsageTest` | `EDCAP_BE/src/test/UnitTest/.../adapter/PmDashboardJdbcAdapterFindTemplateUsageTest.java` | Không cover trực tiếp AC nào của ticket này, nhưng là **pattern mẫu** để viết 3 test BE UT mới ở mục dưới (mock `NamedParameterJdbcTemplate` + `ArgumentCaptor<String>` bắt SQL) | Không phải gap — là template tái dùng |
| `ArtifactDocumentDateServiceTest` (5 case: đủ cặp, chỉ ngày, thiếu `update_date`, parse fail, file không tồn tại) | `EDCAP_BE/src/test/UnitTest/.../scanner/ArtifactDocumentDateServiceTest.java` | Toàn bộ nhánh parse header ở tầng service cho AC-2/AC-4 | Không cover tầng SQL aggregate (đó là gap được lấp ở mục "Test mới" bên dưới, không phải gap bỏ ngỏ) |
| `TicketDetailDrawer.test.tsx` (đã mở rộng, có fixture `phaseDwellTime` mix giá trị thật + `null`) | `EDCAP_FE/src/__ tests __/pm-dashboard/TicketDetailDrawer.test.tsx` | AC-1 (card hiển thị đúng vị trí), AC-3/AC-4 (render `"03:20:00"` và `"-"` cùng lúc), regression cho card `Phase` cũ không đổi | Chưa test nhánh input KHÔNG theo thứ tự `phaseOrder` (được bảo đảm bởi test mới ở mục dưới) |
| `PmDashboardControllerTest` | `EDCAP_BE/src/test/UnitTest/.../rest/PmDashboardControllerTest.java` | Controller không có `try/catch` ad-hoc, response shape `PmDashboardTicketDetailDto` đúng chuẩn `ErrorResponse` khi lỗi hệ thống thật | Không cover riêng nhánh lỗi của subquery dwell-time (đã lấp ở BE UT mới cho AC-10, vì lỗi được nuốt ở tầng Adapter — Controller không bao giờ thấy exception này) |

## Test mới/cập nhật

| TC ID | test | type | target | related AC |
|---|---|---|---|---|
| TC-PHASE-DWELL-TIME-1 | Mock `jdbc`, gọi `findPhaseDwellTime`/`findDetail`, capture SQL: assert `phase_code IN` chứa đúng `'1','3','4','5','6','7','8'`, không chứa `'0-A'`/`'0-B'`/`'2'`/`'9'`, có `ORDER BY ph.phase_order` | BE UT | `PmDashboardJdbcAdapter` | AC-1 |
| TC-PHASE-DWELL-TIME-2 | Capture SQL, assert điều kiện `d.document_create_at IS NOT NULL AND d.document_update_at IS NOT NULL` nằm trong `ON` của `LEFT JOIN ... tbl_fact_artifact_document_date`, không nằm sau `WHERE` (tương tự cách `findTemplateUsage_scopesTenantInsideLeftJoinOnClause...` đã bảo vệ pattern LEFT JOIN) | BE UT | `PmDashboardJdbcAdapter` | AC-3, AC-4 |
| TC-PHASE-DWELL-TIME-3 | Mock `jdbc.query(...)` (cho subquery dwell-time) ném `RuntimeException`; gọi `findDetail`; assert kết quả trả về bình thường với `phaseDwellTime = List.of()` (hoặc từng phần tử `dwellTime=null`), không có exception nào thoát ra khỏi `findDetail` | BE UT | `PmDashboardJdbcAdapter` | AC-10 |
| TC-PHASE-DWELL-TIME-4 | Capture SQL của `upsertArtifactDocumentDates`; assert chứa đúng `ON CONFLICT (artifact_snapshot_id) DO NOTHING` (không phải `DO UPDATE`) | BE UT | `ArtifactScannerJdbcAdapter` | AC-6 |
| TC-PHASE-DWELL-TIME-5 | Đọc nội dung `V512__add_artifact_document_date.sql` từ `src/main/resources`; assert **không** chứa `ALTER TABLE`, `DROP TABLE`, `DROP COLUMN`; assert **có** `CREATE TABLE IF NOT EXISTS tbl_fact_artifact_document_date` và `UNIQUE (artifact_snapshot_id)` | BE UT / DB-Migration | `V512__add_artifact_document_date.sql` | AC-9 |
| TC-PHASE-DWELL-TIME-6 | Render `PhaseDwellTimeCard` với mảng `phaseDwellTime` cố ý xáo trộn thứ tự (`phaseOrder` không tăng dần trong input); assert DOM render đúng thứ tự tăng dần theo `phaseOrder` | FE UT | `TicketDetailDrawer.tsx` (`PhaseDwellTimeCard`) | AC-1 |
| TC-PHASE-DWELL-TIME-7 | Đọc trực tiếp `public/locales/{en,ja,vi}/locale.json` (không render component); assert `Pages.PmDashboard.phaseDwellTime` === `"Phase Dwell Time"` / `"各フェーズの滞留時間"` / `"Thời gian kẹt ở từng phase"` cho từng file tương ứng | FE UT | 3 file `locale.json` | AC-8 |

### E2E Step-by-step Scenarios

Không có kịch bản E2E (Playwright) riêng cho ticket này — lý do đã nêu ở cuối "Ma trận AC ↔ loại
test". Nếu về sau có nhu cầu (vd. khi thêm nhiều tương tác hơn cho card này), có thể mở rộng kịch bản
E2E "Mở PM Dashboard → click 1 ticket → mở Ticket Detail Drawer" đã có sẵn (nếu tồn tại ở cấp trang
khác) để thêm 1 assertion `expect(page.getByText('Phase Dwell Time')).toBeVisible()`, không cần kịch
bản mới hoàn toàn.

| scenario | precondition | steps | expected | related AC |
|---|---|---|---|---|
| *(không có — xem ghi chú trên)* | | | | |

## Phương châm test data

- **Không mock domain/value object** (theo `40-testing.md`): `ArtifactSnapshot`, `PhaseDwellTimeItem`
  luôn dùng constructor thật với dữ liệu giả lập cụ thể; chỉ mock **port interface**
  (`ArtifactScannerSourcePort`) và **`NamedParameterJdbcTemplate`** (hạ tầng, không phải domain).
- **Biên giá trị bắt buộc cover** (theo yêu cầu của Test Strategist task):
  - *Boundary*: dwell time `00:00:00` (tạo=cập nhật cùng lúc) và dwell time vượt 24h, ví dụ
    `27:20:05` (đã có sẵn ví dụ trong spec — `spec-pack.md §6/§7.2`); test phải dùng `Math.round`
    trên giây, không dùng modulo 24 cho `hours` (đã implement đúng, xem `formatDwellTimeSeconds`).
  - *Malformed*: giá trị header không parse được (`"not-a-date"`) → đã cover ở
    `ArtifactDocumentDateServiceTest.unparsableHeaderValueYieldsNullWithoutThrowing`.
  - *Null/Empty*: header thiếu hẳn field `update_date` → đã cover; phase không có snapshot nào
    (map rỗng) → cover ở TC-PHASE-DWELL-TIME-1/2 (LEFT JOIN vẫn trả dòng phase).
  - *Full-width number*: **không áp dụng** — không có input số do người dùng nhập ở tính năng này
    (đọc-only, giá trị số duy nhất là dwell time do BE tính và format sẵn dạng chuỗi `hh:mm:ss`,
    không có ô nhập liệu số nào ở FE/BE cho tính năng này); ghi rõ ở đây để không bỏ sót viewpoint
    theo yêu cầu, không phải bỏ qua ngầm.
  - *Duplicate/Double submit*: **không áp dụng** — tính năng đọc-only, không có action "submit" nào
    của người dùng; hành vi "duplicate" tương đương duy nhất là rescan trùng nội dung, đã map vào AC-6.
  - *Race condition*: 2 scan chạy đồng thời cùng 1 ticket có thể tạo tranh chấp ghi vào
    `tbl_fact_artifact_document_date` — `ON CONFLICT ... DO NOTHING` giảm thiểu lỗi duplicate-key,
    nhưng **race thật giữa 2 transaction đồng thời chưa được test** (cần DB thật + test đa luồng) —
    xem Rủi ro còn lại.
  - *Timeout*: mô phỏng bằng cách mock `jdbc.query` ném exception (TC-PHASE-DWELL-TIME-3) — coi
    timeout là 1 dạng cụ thể của "lỗi khi query", đã đủ để verify hợp đồng fail-soft (AC-10) mà
    không cần dựng timeout thật.
  - *Auth/Permission/Tenant*: không có auth/tenant riêng cho field mới — dùng chung endpoint
    `/api/v1/pm/dashboard/tickets/{ticketId}/detail` đã có `role=PM` check, đã test đầy đủ ở
    `PmDashboardServiceTest`/`PmDashboardControllerTest` hiện có (tái dùng, không cần thêm).
  - *Rollback*: rollback code = revert Git (thuần additive); rollback DB = giữ bảng, không cần
    `DROP` (`impl-plan.md § Rollout/Rollback`) — không có kịch bản rollback tự động cần test, đây
    là quy trình vận hành thủ công, không phải logic code.
- **Fixture chuẩn dùng lại giữa các test**: 8 file `CHANGE_TARGET_FILES`, 7 phase code hiển thị
  (`'1','3','4','5','6','7','8'`), UUID cố định kiểu `00000000-0000-0000-0000-00000000eXXX` theo
  đúng convention đã thấy ở `PmDashboardJdbcAdapterFindTemplateUsageTest`.

## Command thực thi

| command | purpose |
|---|---|
| `mvn -o -Dtest=PmDashboardJdbcAdapter*Test,ArtifactScannerJdbcAdapterTest test` (trong `EDCAP_BE/`) | Chạy các BE UT mới/liên quan (TC-1 → TC-5) |
| `mvn -o -Dtest=ArtifactDocumentDateServiceTest test` (trong `EDCAP_BE/`) | Regression cho phần parse header (đã có, tái dùng) |
| `mvn -o test` (trong `EDCAP_BE/`) | Full BE suite — bắt buộc trước khi merge, mục tiêu tiếp tục 100% PASS như đã ghi ở `self-review.md §4` (596 test) |
| `npx vitest run "TicketDetailDrawer"` (trong `EDCAP_FE/`) | Chạy FE UT liên quan trực tiếp (TC-6, regression card cũ) |
| `npx vitest run "locale"` hoặc chỉ định đúng path file test mới (TC-7) | Chạy test đọc locale JSON mới |
| `npx tsc --noEmit` (trong `EDCAP_FE/`) | Typecheck toàn bộ sau khi thêm test mới |
| `npx vitest run` (trong `EDCAP_FE/`) | Full FE suite — mục tiêu tiếp tục 100% PASS như đã ghi ở `self-review.md §4` (339 test) |

## Vùng cố ý không test lần này

| area | reason | risk |
|---|---|---|
| DB Integration test thật (Postgres, qua Testcontainers hoặc tương đương) cho AC-2 (Σ nhiều file/1 phase) và AC-6 (rescan giữ nguyên giá trị) | Repo hiện chưa có hạ tầng test kết nối Postgres thật (0 kết quả grep `Testcontainers`/`@Container`); dựng hạ tầng này là công việc hạ tầng CI riêng, vượt phạm vi 1 ticket additive nhỏ. Đã bù đắp một phần bằng BE UT mock-SQL (TC-1, TC-2, TC-4) để bắt lỗi cấu trúc câu lệnh, nhưng không thể verify ngữ nghĩa runtime thật của `SUM(EXTRACT(EPOCH FROM ...))` hay hành vi `ON CONFLICT` trên Postgres thật | Trung bình — lỗi kiểu "cú pháp đúng nhưng kết quả tính sai" (vd. `EXTRACT(EPOCH ...)` trả đơn vị không như kỳ vọng trên phiên bản Postgres cụ thể) chỉ lộ ra khi chạy trên môi trường có DB thật (dev/staging) |
| Race condition thật giữa 2 scan run đồng thời trên cùng ticket (ghi đồng thời vào `tbl_fact_artifact_document_date`) | Cần môi trường đa luồng + DB thật để tái hiện; tần suất xảy ra thấp trong vận hành thực tế (scan theo ticket, không phải theo request tần suất cao — đã ghi nhận ở `impl-plan.md` Gate #7) | Thấp — `UNIQUE + ON CONFLICT DO NOTHING` đã giảm thiểu lỗi duplicate-key crash; kịch bản xấu nhất là 1 trong 2 lần ghi bị bỏ qua (không sai dữ liệu, chỉ là "chưa cập nhật mới nhất"), chấp nhận được cho tính năng đọc-only |
| API IT qua Spring Security filter chain thật (`@SpringBootTest` + `MockMvc` giống `DataOpsDashboardApiIntegrationTest`) cho endpoint `/pm/dashboard/tickets/{ticketId}/detail` | Đây là gap **có từ trước** ticket này (endpoint `/detail` chưa từng có IT qua filter chain thật, chỉ có `PmDashboardControllerTest` dạng standalone MockMvc) — không phải do PHASE-DWELL-TIME gây ra, và field mới không mở thêm bề mặt auth nào (dùng chung check `role=PM` đã có) | Thấp cho riêng ticket này — nếu muốn đóng gap này nên làm ở ticket riêng cho toàn bộ PM Dashboard, không phải chỉ vì 1 field mới |
| E2E (Playwright) riêng cho Phase Dwell Time Card | FE UT mount thật `TicketDetailDrawer` đã đóng vai trò AC Closure tương đương (theo `40-testing.md`) với chi phí thấp hơn nhiều; card chỉ hiển thị text tĩnh, không có tương tác (click/filter/submit) nào cần E2E mới xác nhận | Rất thấp |
| Full-width number / ký tự đặc biệt trong input số | Tính năng không có ô nhập số nào từ người dùng (đọc-only; giá trị số duy nhất do BE tính và format sẵn) | Không áp dụng — không có rủi ro tương ứng |

## Rủi ro còn lại

- **Cao nhất**: chưa verify được ngữ nghĩa SQL thật (`SUM(EXTRACT(EPOCH FROM ...))`, `ON CONFLICT DO
  NOTHING`) trên Postgres thật — chỉ có mock-SQL-structure test + BE UT ở tầng service (đã PASS).
  Khuyến nghị: chạy tay 1 lần trên môi trường dev có DB trước khi release rộng (scan 1 ticket thật có
  header `create_date`/`update_date`, xác nhận Card hiển thị đúng `hh:mm:ss`), ghi log kết quả vào
  `test-results.md`.
- **Trung bình**: `flyway migrate` cho `V512` chưa từng chạy trên môi trường chia sẻ nào (theo
  `00-safety.md §3`, cần xác nhận người dùng trước) — chưa biết chắc migration chạy sạch trên schema
  thật (dù cú pháp đã tự rà bằng `TC-PHASE-DWELL-TIME-5`).
- **Thấp**: race condition khi 2 scan chạy đồng thời (đã phân tích ở bảng trên) — chấp nhận được cho
  tính năng đọc-only, mất mát nếu có chỉ là "chưa cập nhật mới nhất", không phải sai dữ liệu.
- **Thấp**: gap tiền nhiệm về API IT qua security filter chain thật cho toàn bộ PM Dashboard — không
  phát sinh mới từ ticket này, không chặn merge ticket này.
- **Rất thấp**: nếu format `create_date`/`update_date` thực tế trong header các ticket cũ khác với
  3 format đã hỗ trợ (`yyyy-MM-dd`, `yyyy-MM-dd HH:mm:ss`, `yyyy-MM-dd'T'HH:mm:ss`), phase đó sẽ hiển
  thị `"-"` thay vì giá trị thật — đã được `spec-pack.md §12`/`§16 (A-PHASE-DWELL-TIME-4)` chấp nhận
  trước như một rủi ro dữ liệu tự khai báo, không phải lỗi code.
