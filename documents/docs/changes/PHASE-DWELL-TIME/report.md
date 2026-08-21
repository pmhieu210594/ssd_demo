# report

**Ticket ID**: PHASE-DWELL-TIME
**Create date**: 2026-08-21 08:33:12 
**Author**: SDD Reporter (Claude)
**Update date**: 2026-08-21 08:51:35

> Nguồn tổng hợp: `spec-pack.md`, `impact-analysis.md`, `impl-plan.md`, `review-checklist.md`,
> `self-review.md`, `human-review.md`, `test-plan.md`, `test-results.md`, `blackbox-testcases.md`,
> `open-issues.md`. Báo cáo này chỉ tổng hợp — không tự suy đoán trạng thái nào chưa có bằng chứng
> trong các tài liệu trên.

## Tổng quan sửa đổi

Thêm 1 Card mới **"Phase Dwell Time"** vào màn hình Ticket Detail (PM Dashboard), hiển thị thời
gian ticket "lưu lại" ở từng giai đoạn (7 giai đoạn: Spec Pack, Implementation Plan, Review
Checklist, Self Review, Test Plan, Test Results, Report), tính từ `create_date`/`update_date` tự
khai báo trong header mỗi file `.md` của ticket. Đặc điểm kỹ thuật chính:

- **Thuần đọc-only, additive-only**: không sửa `TicketPhaseEvaluatorService`/`tbl_fact_ticket_phase_status`
  (logic xác định "giai đoạn hiện tại" hiện có), không tạo endpoint mới, không ALTER/DROP bảng nào
  hiện có — chỉ thêm 1 bảng mới (`tbl_fact_artifact_document_date`), 1 field mới trên DTO/response
  đã có (`phaseDwellTime`), 1 Card mới trên UI.
- Đã trải qua **3 vòng làm việc trong cùng phiên**: (1) implement theo `impl-plan.md` đã duyệt Gate,
  (2) bug-hunting bổ sung test có tín hiệu cao → phát hiện 2 defect thật, (3) fix 2 defect đó sau
  khi người dùng duyệt phương án qua trao đổi trực tiếp (không phải qua `human-review.md` chính
  thức — xem mục "Kết quả review").
- Tại thời điểm viết báo cáo này: **code đã implement và fix xong, build/test tự động đều xanh**,
  nhưng **chưa qua Independent AI Review (Codex) và chưa qua Human Review chính thức**, và **chưa
  chạy `flyway migrate`** trên môi trường chia sẻ nào.

## Đối ứng specification/AC

| ACID | status | evidence |
|---|---|---|
| AC-PHASE-DWELL-TIME-1 | PASS | `PmDashboardJdbcAdapterPhaseDwellTimeTest#findPhaseDwellTime_query_isAnchoredOnAllPhasesAndFiltersToTheSevenDisplayedOnes` (BE, mock-SQL); `TicketDetailDrawer.test.tsx` (FE, render + thứ tự `phaseOrder`); black-box `BB-PHASE-DWELL-TIME-01/02` |
| AC-PHASE-DWELL-TIME-2 | PASS (đơn vị/service) — **PARTIAL ở mức tích hợp DB thật** | `ArtifactDocumentDateServiceTest` (parse header, 5 case); `PmDashboardJdbcAdapterPhaseDwellTimeTest` (cấu trúc SQL `SUM`/`EXTRACT`); **chưa** chạy trên Postgres thật để xác nhận ngữ nghĩa runtime của `SUM(EXTRACT(EPOCH FROM ...))` — xem `test-results.md §8` |
| AC-PHASE-DWELL-TIME-3 | PASS | Cùng test ở AC-1 (LEFT JOIN giữ dòng phase dù không match); `BB-07` |
| AC-PHASE-DWELL-TIME-4 | PASS | `ArtifactDocumentDateServiceTest.missingUpdateDateFieldYieldsNullWithoutThrowing`; `PmDashboardJdbcAdapterPhaseDwellTimeTest#findPhaseDwellTime_query_excludesUnpairedFilesInsideTheJoinOnClause_notInWhere`; `BB-08/09` |
| AC-PHASE-DWELL-TIME-6 | PASS (đơn vị/cấu trúc SQL) — **PARTIAL ở mức tích hợp DB thật** | `ArtifactScannerJdbcAdapterTest#upsertArtifactDocumentDates_usesOnConflictDoNothing_notDoUpdate`; `ArtifactDocumentDateMigrationTest` (xác nhận `UNIQUE`); **chưa** chạy quét thật 2 lần trên DB thật để đo runtime — xem `test-results.md §8`, `BB-10/11` (black-box, cũng chưa thực thi thật) |
| AC-PHASE-DWELL-TIME-7 | PASS | `TicketPhaseEvaluatorServiceTest` (4 test, không đổi, PASS); regression `PmDashboardServiceTest`; `BB-12` |
| AC-PHASE-DWELL-TIME-8 | PASS | `phaseDwellTimeLocale.test.ts` (đọc trực tiếp 3 file locale.json thật, so khớp byte-for-byte — trước đây KHÔNG có test nào làm việc này); `BB-13/14/15` |
| AC-PHASE-DWELL-TIME-9 | PASS | `ArtifactDocumentDateMigrationTest` (đọc nội dung `V512` thật, xác nhận không `ALTER`/`DROP`) |
| AC-PHASE-DWELL-TIME-10 | PASS | `PmDashboardJdbcAdapterPhaseDwellTimeTest#findDetail_whenPhaseDwellTimeQueryThrows_stillReturnsDetailWithEmptyPhaseDwellTime`; `BB-16` |

*(Không có AC-PHASE-DWELL-TIME-5 — đúng theo `spec-pack.md §6`, không tự suy đoán thêm.)*

**24 black-box test case** (`blackbox-testcases.md`) đã được **thiết kế đầy đủ, kèm dữ liệu cụ thể
ở `test-data.md`** để đối chiếu 9 AC + các viewpoint bổ sung (permission, double submit, external
IF failure, ký tự/số) — nhưng **chưa có bằng chứng đã thực thi thật trên môi trường/UI thật**
(không có ai cập nhật cột "status" trong `blackbox-testcases.md § Test Case Summary`, và không có
`test-results.md` nào ghi nhận kết quả chạy tay các case này). Đây là khoảng trống thật, không phải
"coi như đã pass".

## Phạm vi ảnh hưởng

Theo `impact-analysis.md` (đã đọc lại toàn văn source thật ở vòng verify 2, 2026-08-20):

- **Trực tiếp**: 1 bảng DB mới; 1 service BE mới; port/adapter mở rộng; field mới trên
  `DashboardTicketDetail`/`PmDashboardTicketDetailDto`/FE `PmDashboardTicketDetail`; 1 Card FE mới;
  3 file locale.
- **Gián tiếp**: mọi consumer khác của `PmDashboardTicketDetail` (additive nên rủi ro thấp — xem
  Assumption A-PHASE-DWELL-TIME-3, chưa rà soát 100% nơi consume); luồng scan hiện có
  (`ArtifactScannerService.scanTicketDirectory`, thêm bước đọc, không đổi bước ghi hiện có); số
  lượng GitHub API call mỗi lần quét ticket (**+ tối đa 8 call/ticket/lần quét**, đã được duyệt ở
  Gate #7); hiệu năng scan.
- **Không ảnh hưởng** (đã xác nhận bằng đọc source, không suy đoán): `TicketPhaseEvaluatorService`,
  `PmDashboardController`, cơ chế phân quyền `requirePm`, `ArtifactScannerSourcePort`,
  `DevDashboard` (module riêng biệt), `vw_artifact_inventory_current`, 4 parser chuyên biệt hiện có
  (`SpecPackMarkdownParser`, `ReviewChecklistMarkdownParser`, `SelfReviewMarkdownParser`,
  `ReportMarkdownParser`).
- **Rollout**: additive-only, không cần feature flag, không breaking change. **Backfill**: dữ liệu
  chỉ xuất hiện sau khi ticket được quét lại — ticket cũ hiển thị `"-"` cho tới lần quét kế tiếp,
  **không** trigger `FULL` scan tự động sau deploy (Gate #4, quyết định vận hành đã duyệt).

## Nội dung implementation

| file | summary | reason |
|---|---|---|
| `EDCAP_BE/.../db/migration/V512__add_artifact_document_date.sql` | Bảng mới `tbl_fact_artifact_document_date` (additive, `UNIQUE(artifact_snapshot_id)`) | Gate #1 |
| `EDCAP_BE/.../usecase/scanner/ArtifactDocumentDateService.java` | Service mới trích `create_date`/`update_date` từ header 8 file, đọc lại blob theo `source_path` | Gate #2/#7/#8 |
| `EDCAP_BE/.../port/out/persistence/ArtifactScannerPersistencePort.java` | + method `upsertArtifactDocumentDates` | Ghi bảng mới |
| `EDCAP_BE/.../adapter/scanner/ArtifactScannerJdbcAdapter.java` | + implement bằng `ON CONFLICT (artifact_snapshot_id) DO NOTHING` | Gate #1 |
| `EDCAP_BE/.../usecase/scanner/ArtifactScannerService.java` | + dependency `ArtifactDocumentDateService` (constructor 12-arg chính, giữ nguyên 2 overload cũ), gọi sau vòng lặp scan | Wiring, không đổi luồng ghi hiện có |
| `EDCAP_BE/.../usecase/pmdashboard/PmDashboardModels.java` | + record `PhaseDwellTimeItem`, + field `phaseDwellTime` trong `DashboardTicketDetail` | Model |
| `EDCAP_BE/.../adapter/PmDashboardJdbcAdapter.java` | + `findPhaseDwellTime(ticketId)` (try/catch nội bộ, log `WARN`); + xử lý tổng âm → `"-"` (fix BUG-1) | Gate #3, AC-2/3/4/10 |
| `EDCAP_BE/.../web/dto/PmDashboardDtos.java` | + `PhaseDwellTimeItemDto` + cập nhật `from(...)` | DTO mở rộng |
| `EDCAP_FE/src/lib/api.ts` | + field `phaseDwellTime` trong `PmDashboardTicketDetail` | FE type |
| `EDCAP_FE/src/pages/pm-dashboard/components/TicketDetailDrawer.tsx` | + component `PhaseDwellTimeCard`; + guard `?? []` (fix BUG-2) | UI Card mới, không sửa `PhaseCard` cũ |
| `EDCAP_FE/public/locales/{en,ja,vi}/locale.json` | + key `phaseDwellTime` | i18n, 3 locale |
| 3 file test BE mới (`ArtifactDocumentDateServiceTest`, `PmDashboardJdbcAdapterPhaseDwellTimeTest`, `ArtifactDocumentDateMigrationTest`) + 2 file test BE cập nhật (`ArtifactScannerServiceTest`, `ArtifactScannerJdbcAdapterTest`) + 2 file test BE cập nhật cho compile (`PmDashboardServiceTest`, `PmDashboardControllerTest`) | Unit test mới/mở rộng | Bảo vệ AC-1/3/4/6/9/10, không mock domain entity |
| 2 file test FE mới (`phaseDwellTimeLocale.test.ts`) + cập nhật (`TicketDetailDrawer.test.tsx`, `PMDashboardPage.test.tsx`) | Unit test mới/mở rộng + fix DOM-leak test-infra (`afterEach(cleanup)`) | Bảo vệ AC-1/8, AC Closure |
| `docs/changes/PHASE-DWELL-TIME/{test-plan,test-results,self-review,open-issues,blackbox-testcases,test-data}.md` | Tài liệu quy trình | Theo `ticket-rules.md` |

Chi tiết đầy đủ hơn (dòng code cụ thể) xem `self-review.md §3`.

## Kết quả review

| review type | result | notes |
|---|---|---|
| Self Review (AI, Claude) | **PASS** (`self-review.md §12`, cập nhật lần cuối 2026-08-21 vòng 3) | Đã tự đối chiếu AC, review checklist 10 mục, đối chiếu Gate đã RESOLVED với code thực tế. Không phải review độc lập — cùng 1 AI thực hiện implementation. |
| Independent AI Review (Codex) | **CHƯA THỰC HIỆN** | `codex-review.md` vẫn là template rỗng (không có finding nào được điền) — **không được coi là "đã review và pass"**, chỉ là chưa chạy |
| Human Review | **CHƯA THỰC HIỆN CHÍNH THỨC** | `human-review.md` vẫn là template rỗng (không có Reviewer/Review Date/Verdict nào được điền). **Có 1 quyết định phạm vi được người dùng xác nhận trực tiếp qua trao đổi** (chọn phương án fix cho BUG-1/BUG-2, ghi ở `open-issues.md` Resolution Log 2026-08-21) — đây là 1 Human Decision cụ thể, **không tương đương** với 1 vòng Human Review đầy đủ theo checklist |

## Kết quả test

| test type | result | evidence |
|---|---|---|
| BE unit test (Maven, `mvn -o test`, JUnit 5 + Mockito) | **PASS — 603/603**, đã chạy thật | `test-results.md §2/§4`; không dùng Postgres/Testcontainers thật (không có trong repo) |
| FE unit test (Vitest) | **PASS — 344/344 (52 file)**, đã chạy thật | `test-results.md §2/§4` |
| FE typecheck (`npx tsc --noEmit`) | **PASS**, đã chạy thật | `test-results.md §2` |
| FE build (`npm run build`) | **PASS**, đã chạy thật (ở vòng implement đầu) | `self-review.md §4` |
| ArchUnit `ArchitectureTest` | **KHÔNG CHẠY ĐƯỢC** | File không tồn tại trong repo (`Glob`/`find` xác nhận) — không phải do phiên làm việc này xoá, là gap tiền nhiệm |
| Integration test trên Postgres thật (AC-2 cộng dồn, AC-6 rescan) | **CHƯA CHẠY** | Không có Testcontainers/Postgres thật trong môi trường làm việc (`test-plan.md § Mục tiêu`) — chỉ có mock-SQL-shape test thay thế một phần |
| `flyway migrate` trên môi trường chia sẻ | **CHƯA CHẠY** | Theo `00-safety.md §3`, cần hỏi người dùng trước — chưa được yêu cầu trong toàn bộ phiên làm việc |
| 24 black-box test case (`blackbox-testcases.md`) | **CHƯA THỰC THI TRÊN MÀN HÌNH THẬT** | Đã thiết kế đầy đủ kèm dữ liệu (`test-data.md`), nhưng **không có bằng chứng ai đã thao tác tay/tự động hoá chạy qua UI thật** — cột trạng thái trong bảng Test Case Summary chưa được điền |

**Không viết "đã test AC-2/AC-6 đầy đủ"** — chỉ đúng ở mức unit/mock, còn thiếu bằng chứng runtime
thật trên Postgres và thao tác tay trên UI thật.

## Viewpoint security / operation

- **Security**: tính năng thuần đọc, không có input mới từ người dùng → không có bề mặt injection
  mới; không thêm PII mới (Dwell Time chỉ là khoảng thời gian tính từ timestamp nội bộ); dùng
  chung quyền hiện có của `TicketDetailDrawer`/`PmDashboardTicketDetail` (session-based cookie),
  không mở rộng quyền cho role nào chưa từng có quyền xem ticket detail. Log lỗi (`WARN`) không
  chứa nội dung file `.md`, chỉ chứa `ticketId`/`sourcePath`/tên field thiếu — tuân
  `30-security.md § Log/Audit Sanitization`.
- **Operation**: không có batch/job vận hành mới — service mới chạy trong luồng scan hiện có.
  Backfill dữ liệu cho ticket cũ diễn ra tự nhiên qua lần quét tiếp theo, **không** trigger `FULL`
  scan hàng loạt sau deploy (quyết định vận hành đã duyệt, Gate #4 — tránh tải đột biến GitHub
  API). Log `WARN` khi: (a) parse header lỗi/thiếu field, (b) lỗi/timeout khi tính Dwell Time cho
  1 phase, (c) tổng Dwell Time âm (dữ liệu header bị đảo — dấu hiệu dữ liệu ticket có vấn đề, đáng
  để vận hành theo dõi định kỳ dù không phải lỗi hệ thống).
- **Rollback**: additive-only → `git revert` an toàn cho code; DB không có down-migration (Flyway
  Community) — rollback DB thực chất là giữ bảng mới (khuyến nghị) hoặc migration `DROP TABLE`
  riêng nếu cần dọn dẹp, chưa có quyết định chính thức nào chọn phương án nào sẽ dùng nếu cần
  rollback thật.

## Accepted risk

| risk | impact | owner | deadline | status | approver |
|---|---|---|---|---|---|
| Chưa chạy integration test trên Postgres thật cho AC-2 (cộng dồn nhiều file/1 phase) và AC-6 (rescan giữ nguyên giá trị) — chỉ có mock-SQL-shape test | Trung bình — lỗi kiểu "cú pháp đúng nhưng ngữ nghĩa runtime sai" (vd đơn vị trả về của `EXTRACT(EPOCH...)`) chỉ lộ khi chạy trên DB thật | QA/người review trước khi merge | Trước khi deploy lên môi trường có DB thật | OPEN | *(chưa có approver ký nhận — cần Tech Lead/PM)* |
| ArchUnit `ArchitectureTest` không tồn tại trong repo | Thấp — đã tự rà layer boundary bằng tay; không tự động hoá được | Team (gap tiền nhiệm, không phát sinh từ ticket này) | Không áp dụng riêng cho ticket này | ACCEPTED | *(chưa có approver ký nhận)* |
| Race condition khi 2 lượt quét chạy đồng thời trên cùng ticket (ghi đồng thời vào bảng mới) | Thấp — `UNIQUE + ON CONFLICT DO NOTHING` giảm thiểu duplicate-key; kịch bản xấu nhất là 1 trong 2 lần ghi bị bỏ qua (không sai dữ liệu, chỉ chưa cập nhật mới nhất) | Team vận hành | Không áp dụng riêng cho ticket này | ACCEPTED | *(chưa có approver ký nhận)* |
| Dữ liệu header cũ (ticket có từ trước) có thể chưa từng cập nhật `update_date` theo quy ước mới → hiển thị `"-"` dù thực tế đã hoàn thành | Thấp — đã được `spec-pack.md §12`/`§16 A-4` chấp nhận trước như rủi ro dữ liệu tự khai báo, không phải lỗi code | PM/Product | Không có deadline cụ thể — chấp nhận vĩnh viễn trừ khi có quy ước bắt buộc cập nhật header mới | ACCEPTED | Người dùng (đã xác nhận ở `spec-pack.md`) |
| `flyway migrate` chưa chạy trên môi trường chia sẻ | Không có ảnh hưởng hiện tại — chỉ là bước tiếp theo cần làm | Người dùng | Trước khi merge/deploy | OPEN | *(chờ người dùng tự quyết định thời điểm)* |
| Gap tiền nhiệm: chưa có Integration Test qua Spring Security filter chain thật cho endpoint PM Dashboard `/detail` (chỉ có MockMvc standalone) | Thấp cho riêng ticket này — field mới dùng chung `role=PM` check đã có, không mở thêm bề mặt auth | Team (nên làm ở ticket riêng cho toàn bộ PM Dashboard) | Không áp dụng riêng cho ticket này | ACCEPTED | *(chưa có approver ký nhận)* |
| 24 black-box test case đã thiết kế nhưng **chưa thực thi thật trên UI** | Trung bình — rủi ro có defect UI/nghiệp vụ chưa được người dùng thật xác nhận bằng mắt (vd lỗi hiển thị tiếng Nhật thật trên trình duyệt, hành vi thật khi bấm nhanh nhiều lần) | QA | Trước khi coi tính năng "release-ready" | OPEN | *(chờ QA thực thi và ký nhận)* |

## Open Issues

| issue | impact | next action |
|---|---|---|
| *(Không còn Open Issue nào đang mở ở cấp spec/business — `OI-PHASE-DWELL-TIME-14`/`15` đã RESOLVED 2026-08-21, xem `open-issues.md` Resolution Log)* | — | — |
| Chưa chạy 24 black-box test case thật trên UI (không phải "issue" theo nghĩa lỗi, mà là công việc còn thiếu) | Chưa có xác nhận cuối cùng từ góc nhìn người dùng thật | QA lên lịch chạy tay theo `blackbox-testcases.md` + `test-data.md`, điền kết quả vào `blackbox-review-checklist.md`/`test-results.md` |
| Chưa có Independent AI Review (Codex) và Human Review chính thức | Chưa có "con mắt thứ 2" xác nhận độc lập, đặc biệt cho phần migration DB và SQL aggregate mới | Chạy Codex review trước khi yêu cầu Human Review; điền `human-review.md` đầy đủ (Reviewer, Review Date, Verdict) trước khi coi ticket DONE |

## Human Decisions

| decision | owner | result |
|---|---|---|
| Chọn Phương án A (suy lịch sử phase gián tiếp, đọc-only) thay vì Phương án B (bảng log lịch sử mới) | Người dùng | Đã áp dụng |
| Nguồn dữ liệu chính thức là `create_date`/`update_date` tự khai báo trong header, không phải `tbl_fact_artifact_snapshot.created_at` | Người dùng | Đã áp dụng |
| 7 Gate kỹ thuật (schema bảng mới + UNIQUE, vị trí service, xử lý lỗi per-phase, không trigger FULL scan backfill, log policy, chiến lược đọc blob, phạm vi 8 file) | Người dùng | Đã áp dụng — chi tiết `impl-plan.md § Quyết định Gate` |
| BUG-PHASE-DWELL-TIME-1: khi tổng Dwell Time âm, hiển thị `"-"` (không phải giá trị tuyệt đối, không chấp nhận rủi ro giữ nguyên) | Người dùng | Đã áp dụng — đã fix, test xác nhận |
| BUG-PHASE-DWELL-TIME-2: thêm guard mặc định `?? []` khi `phaseDwellTime` bị thiếu khỏi response | Người dùng | Đã áp dụng — đã fix, test xác nhận |

*(Toàn bộ Human Decision trên được xác nhận qua trao đổi trực tiếp trong phiên làm việc, ghi lại ở
`open-issues.md § Resolution Log` — không phải qua văn bản `human-review.md` chính thức. Ghi rõ ở
đây để tránh hiểu nhầm "đã có Human Review" khi thực chất chỉ là các quyết định phạm vi rời rạc.)*

## Source Analysis Limitations

- Không có quyền truy cập Postgres thật trong bất kỳ phiên làm việc nào của ticket này — mọi xác
  nhận về hành vi SQL (`SUM`, `EXTRACT(EPOCH...)`, `ON CONFLICT`) đều dừng ở mức đọc code + mock
  test, không phải chạy thật.
- `ArchitectureTest`/ArchUnit được nhắc tới trong `impl-plan.md`/`CLAUDE.md` nhưng xác nhận **không
  tồn tại** trong repo hiện tại — không thể verify tự động hexagonal layering; đã rà bằng tay thay
  thế.
- `codex-review.md`/`human-review.md` chưa được ai điền — báo cáo này **không thể** khẳng định các
  góc nhìn review đó đã được thực hiện, kể cả gián tiếp.
- 24 black-box test case đã thiết kế lý thuyết đầy đủ nhưng **chưa ai thao tác tay qua UI thật** để
  xác nhận — không có ảnh chụp màn hình, không có log thao tác thật nào được đính kèm.
- `impact-analysis.md` (viết 2026-08-20) từng flag `spec-pack.md §5/§8/§16` mô tả thiết kế cũ
  (`created_at` làm proxy) mâu thuẫn với thiết kế mới đã chốt — tại thời điểm đọc `spec-pack.md` ở
  phiên làm việc hiện tại (2026-08-21), nội dung đã khớp đúng thiết kế mới (Σ per-file
  `document_update_at − document_create_at`, bảng `tbl_fact_artifact_document_date`) — **mâu thuẫn
  này đã được ai đó cập nhật lại `spec-pack.md` giữa 2 thời điểm**, không phải do phiên làm việc
  này sửa; không có log nào xác nhận chính xác ai/khi nào đã đồng bộ lại.

## What worked

- Mô hình LEFT JOIN neo trên `tbl_dim_phase` (universe cố định 7 giai đoạn) thay vì neo trên bảng
  dữ liệu thực tế — giúp AC-3 ("chưa có dữ liệu → `-`") đúng tự nhiên mà không cần logic riêng.
- Tái dùng đúng pattern mock-`NamedParameterJdbcTemplate` + `ArgumentCaptor<String>` đã có sẵn
  trong `PmDashboardJdbcAdapterFindTemplateUsageTest` để viết test SQL-shape mới — không cần dựng
  hạ tầng test mới, tái sử dụng kiến thức đã kiểm chứng trong repo.
- Việc chủ động "bug-hunting" bằng test có tín hiệu cao (không chỉ viết lại test đã có) đã phát
  hiện 2 defect thật (negative dwell time, FE crash khi thiếu field) mà self-review ban đầu không
  bắt được — cả 2 đều liên quan đến các nhánh biên hiếm khi được nghĩ tới lúc viết implementation
  đầu tiên.
- Kỹ thuật capture `RowMapper` qua `ArgumentCaptor` rồi tự invoke với `ResultSet` giả lập cho phép
  test logic format số (kể cả case âm) mà không cần Postgres thật và không vi phạm quy tắc "không
  test private method" (vì `RowMapper` được truyền như tham số công khai cho collaborator).
- Dùng `it.fails(...)` (Vitest) để pin lại 1 bug FE có thật mà không làm CI đỏ vô nghĩa — sau khi
  fix, chỉ cần flip sang `it(...)` bình thường, không phải viết lại test từ đầu.

## What failed

- Vòng implement đầu tiên (self-review lần 1) **bỏ sót** cả 2 defect (negative dwell time, FE
  crash khi thiếu field) — chỉ được phát hiện ở vòng bug-hunting riêng biệt sau đó. Nguyên nhân:
  self-review tập trung đối chiếu đúng theo AC/Gate đã liệt kê, trong khi cả 2 defect này đều là
  case biên **không có trong bất kỳ AC nào** (dữ liệu header bị đảo, field bị thiếu khỏi response)
  — chỉ lộ ra khi chủ động đặt câu hỏi "nếu dữ liệu/response không đúng như kỳ vọng thì sao".
- Phát hiện giữa chừng: file test FE (`TicketDetailDrawer.test.tsx`) DOM bị rò rỉ giữa các `it()`
  trong cùng file do `vite.config.ts` không bật `test.globals` (nên
  `@testing-library/react`'s tự động `afterEach(cleanup)` không được đăng ký) — lỗi này không liên
  quan tới tính năng, chỉ lộ ra khi thêm test thứ 2 vào cùng file test vốn trước giờ chỉ có 1 test.
  Đây là 1 bẫy hạ tầng test có thể lặp lại ở bất kỳ file test FE nào khác nếu ai đó thêm test thứ 2
  vào 1 file trước giờ chỉ có 1 `it()`.
- Không có hạ tầng Postgres/Testcontainers thật trong repo để verify AC-2/AC-6 ở mức runtime — đây
  không phải lỗi của ticket này nhưng là khoảng trống lặp lại nhiều lần trong báo cáo (test-plan,
  test-results, report) mà chưa có ai quyết định có đầu tư hạ tầng này hay chấp nhận vĩnh viễn.

## Ứng viên cập nhật Failure Mode Index

*(Đề xuất thêm vào `docs/maintenance/failure-mode-index.md` § "PM-Dashboard-Derived Failure
Modes" — chưa tự ý ghi vào file đó, chỉ đề xuất ở đây để người phụ trách maintenance duyệt.)*

| ID đề xuất | Failure mode | Trigger | Prevention | Detection |
|---|---|---|---|---|
| FMI-PM-005 (đề xuất) | Duration/khoảng thời gian tính từ 2 mốc thời gian tự khai báo (không phải hệ thống tự ghi) sinh ra chuỗi âm/sai định dạng khi mốc "kết thúc" sớm hơn mốc "bắt đầu" | Trừ 2 timestamp tự khai báo (người/AI tự ghi trong header/form) rồi format trực tiếp thành `hh:mm:ss` mà không kiểm tra dấu trước khi `String.format` | Luôn kiểm tra `duration < 0` trước khi format bất kỳ khoảng thời gian nào tính từ 2 mốc **không do hệ thống tự sinh** (self-declared/tự khai báo); coi số âm là "không tính được" (cùng nhánh với thiếu dữ liệu), không hiển thị số âm cho người dùng | Unit test ép giá trị tổng/khoảng cách âm, assert output không chứa dấu `-` ở đầu chuỗi thời gian hiển thị |
| FMI-PM-006 (đề xuất) | Field mới additive trên DTO/response dùng chung bị FE tiêu thụ bằng cú pháp không chịu được `undefined` (vd spread `[...x]`, `.map`, `.sort` trực tiếp), crash toàn bộ component cha khi field vắng mặt do cache cũ/lệch rollout BE-FE | Thêm field mới (kể cả bắt buộc theo TypeScript type) vào 1 response đã tồn tại từ trước, rồi FE tiêu thụ trực tiếp không có `?? []`/`?? defaultValue`, trong khi TanStack Query (hoặc cache tương tự) có thể vẫn giữ response cũ chưa có field này | Với mọi field mới thêm vào response/DTO đã tồn tại, FE phải tiêu thụ qua giá trị mặc định an toàn (`field ?? []`/`?? null`) thay vì giả định TypeScript "required" đảm bảo runtime luôn có — TypeScript type không bảo vệ được dữ liệu cache cũ | Test dựng `detail` giả lập **thiếu hẳn** field mới (không phải `null`/mảng rỗng) rồi render component cha, assert không throw và các phần khác của màn hình vẫn hiển thị |
| FMI-PM-007 (đề xuất) | Test file FE chỉ có 1 `it()` không cần `afterEach(cleanup)` — thêm `it()` thứ 2 vào cùng file gây lỗi "multiple elements" do DOM của lần render trước rò rỉ sang, nếu `vite.config.ts` không bật `test.globals` | Bất kỳ ai thêm `it()`/`test()` thứ 2 gọi `render(...)` vào 1 file test FE trước giờ chỉ có 1 test, trong 1 repo không bật `test.globals: true` | Repo không bật `test.globals` phải tự `import { afterEach } from "vitest"` + `import { cleanup } from "@testing-library/react"` và gọi `afterEach(() => cleanup())` ở đầu mỗi file test có khả năng gọi `render()` nhiều lần — hoặc bật `test.globals: true` một lần cho toàn repo (thay đổi rộng hơn, cần xác nhận riêng) | Thêm test thứ 2 gọi `render()` vào 1 file test cũ, chạy `vitest run <file>` — lỗi `TestingLibraryElementError: Found multiple elements` xuất hiện ngay |
| FMI-PM-008 (đề xuất) | Locale/i18n unit test mock `t()` để luôn trả về `options.defaultValue`, khiến nội dung thật của `locale.json` (đặc biệt locale không phải tiếng Anh) không bao giờ được bất kỳ test tự động nào đọc/so khớp | Pattern `vi.mock("react-i18next", () => ({ useTranslation: () => ({ t: (key, options) => options?.defaultValue ?? key }) }))` được dùng trong mọi test component — an toàn cho việc test logic UI nhưng vô tình che khuất toàn bộ nội dung thật của JA/VI/... | Với bất kỳ string hiển thị nào có bản dịch đa ngôn ngữ, cần **thêm riêng** 1 test đọc trực tiếp `public/locales/{locale}/locale.json` (không qua mock `t()`) để so khớp giá trị thật với spec, độc lập với test render component | Không có test nào đọc trực tiếp file `locale.json` thật cho 1 key mới thêm — grep file test tương ứng, nếu chỉ thấy assertion qua mock `t()` thì coi như chưa được bảo vệ |

## Ứng viên cập nhật Living Docs

- **`docs/standards/testing.md`**: nên ghi rõ pattern "mock `useTranslation` trả về `defaultValue`"
  chỉ đủ để test logic hiển thị, **không** thay thế được việc verify nội dung thật của
  `locale.json` — khuyến nghị luôn có 1 test riêng đọc trực tiếp file JSON cho bất kỳ string đa
  ngôn ngữ mới nào (liên quan FMI-PM-008 đề xuất ở trên).
- **`docs/standards/testing.md`** (hoặc file cấu hình Vitest liên quan): nên ghi chú rõ
  `EDCAP_FE/vite.config.ts` hiện **không** bật `test.globals: true`, nên bất kỳ file test nào gọi
  `render()` nhiều hơn 1 lần phải tự thêm `afterEach(cleanup)` — tránh việc mỗi ticket phải tự phát
  hiện lại lỗi "multiple elements" như trong ticket này (liên quan FMI-PM-007 đề xuất).
- **`docs/architecture/` hoặc `docs/standards/database.md`**: nên ghi nhận rõ ràng, ở mức toàn dự
  án (không riêng ticket này), rằng repo **chưa có hạ tầng Testcontainers/Postgres thật** trong bất
  kỳ test suite nào — hiện mỗi ticket (bao gồm ticket này) đều phải tự phát hiện lại giới hạn này
  và tự quyết định "chấp nhận rủi ro" riêng lẻ. Nên có 1 quyết định ở cấp dự án: đầu tư hạ tầng này
  hay chính thức ghi nhận là giới hạn vĩnh viễn của quy trình test hiện tại.
- **`spec-pack.md`**: không cần sửa gì thêm tại thời điểm báo cáo này — đã được đồng bộ đúng thiết
  kế mới (xem "Source Analysis Limitations"). Ghi nhận để tránh việc review sau này lại lật lại
  mâu thuẫn cũ đã lỗi thời từ `impact-analysis.md`.
- **`docs/maintenance/failure-mode-index.md`**: áp dụng 4 candidate ở mục trên (FMI-PM-005 → 008)
  sau khi người phụ trách maintenance xác nhận đúng format và không trùng lặp entry đã có.

## Next actions

1. **Chạy Independent AI Review (Codex)** cho toàn bộ diff của ticket (BE + FE + migration),
   trọng tâm: câu SQL aggregate mới (`EXTRACT(EPOCH FROM ...)`, LEFT JOIN chain), migration
   `V512`, và 2 chỗ vừa fix (BUG-1/BUG-2) — điền `codex-review.md`.
2. **Tổ chức Human Review chính thức** (điền `human-review.md`: Reviewer, Review Date, Review
   Scope, Verdict) — hiện chỉ có quyết định phạm vi rời rạc qua trao đổi, chưa có 1 vòng review
   đầy đủ theo checklist.
3. **QA thực thi 24 black-box test case** theo `blackbox-testcases.md` + `test-data.md` trên môi
   trường test/staging thật, điền kết quả vào bảng "Test Case Summary" và `test-results.md`.
4. **Chạy thử 1 lần thật trên môi trường có Postgres** (dev/staging): quét 1 ticket test có header
   `create_date`/`update_date` hợp lệ, xác nhận Card hiển thị đúng `hh:mm:ss`; quét lại lần 2 xác
   nhận giá trị không đổi (AC-6 runtime thật) — ghi kết quả vào `test-results.md`.
5. **Xin xác nhận người dùng và lên lịch chạy `flyway migrate`** cho `V512` trên môi trường chia sẻ
   theo đúng `00-safety.md §3`.
6. **Quyết định phương án rollback DB chính thức** (giữ bảng vs `DROP TABLE` riêng) — hiện chỉ có
   khuyến nghị, chưa có quyết định chốt.
7. Người phụ trách maintenance xem xét đưa 4 candidate FMI-PM-005 → 008 vào
   `docs/maintenance/failure-mode-index.md`, và 4 gợi ý cập nhật Living Docs ở mục trên vào đúng
   file tương ứng.

## Final Verdict

- **NEEDS_UPDATE** — Implementation đã hoàn tất, build/test tự động (BE 603/603, FE 344/344) đều
  xanh, 2 defect thật phát hiện qua bug-hunting đã được fix và có test xác nhận. Tuy nhiên ticket
  **chưa đủ điều kiện DONE** vì còn thiếu: (1) Independent AI Review và Human Review chính thức
  chưa thực hiện, (2) 24 black-box test case chưa được thực thi thật trên UI, (3) chưa xác nhận
  hành vi runtime thật trên Postgres cho AC-2/AC-6, (4) chưa chạy `flyway migrate` trên môi trường
  chia sẻ. Không có Blocker nào được phát hiện tính tới thời điểm báo cáo — các mục còn thiếu đều
  là bước tiếp theo cần hoàn tất, không phải phát hiện lỗi mới.
