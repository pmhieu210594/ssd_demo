# AI Review — Ticket BLACKBOX-COVERAGE (Blackbox Design/Execution/Overall Coverage)

- **Ticket:** BLACKBOX-COVERAGE
- **Trạng thái:** Draft
- **Tạo ngày:** 2026-09-07 00:00
- **Cập nhật ngày:** 2026-09-07 00:00
- **Diff review:** không xác định — `.git/` tồn tại nhưng rỗng (không có `HEAD`, không có object database) trong working copy này, không lấy được lịch sử commit/diff.
- **Branch:** không xác định (không có git history khả dụng)
- **Nguồn đối chiếu:** [docs/changes/BLACKBOX-COVERAGE/spec-pack.md](spec-pack.md)
- **Reviewer:** AI review — pm_hieu (người duyệt của tất cả finding, xác nhận lại)
- **Cách kiểm chứng:** đọc code tĩnh + `grep`/`Read` toàn bộ file implementation được liệt kê trong `impl-plan.md` §4, đối chiếu trực tiếp với công thức ở `spec-pack.md` §5/§6; đã thử chạy test mục tiêu bằng Maven ở cả repo root và thư mục `EDCAP_BE`, nhưng đều fail do cấu hình/module command cũ và dependency/plugin resolution trong môi trường hiện tại. Chưa chạy migration, chưa chạy E2E, chưa chạy app.
- **Phạm vi:** ghi kết quả review và trạng thái xử lý finding; không mở rộng sang yêu cầu ngoài F1-F5.

> **Ghi chú quan trọng về input:** Không có git repo khả dụng để lấy diff/commit range (`.git` rỗng). Review này đối chiếu trực tiếp code hiện có trên đĩa (các file liệt kê ở `impl-plan.md` §4, đều tồn tại) với `spec-pack.md`, thay vì đọc diff theo commit. `self-review.md` tồn tại nhưng **toàn bộ checkbox và bảng đều để trống** (không AC nào được tick, không có evidence, không có command output, không có verdict) — coi đây là gap về evidence/process, xem [§5](#5-độ-phủ-review) và Next action.

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

**Verdict: Pending verification.**

0 Blocker mở · 0 Major mở · 0 Minor mở.

Điều kiện để pass:

1. [F1](#f1) (Blocker) — **đã fix trong code, chờ dev/reviewer con người xác nhận lại**, xem [Cập nhật xử lý F1](#f1-fix).
2. [F2](#f2) (Major) — đã bổ sung repository integration test chạy SQL thật trên Postgres test container, cover trực tiếp case AC không map case nào và case mapped FAIL/PASS. Xem [Cập nhật xử lý F2](#f2-fix).
3. [F3](#f3) (Major) — đã bổ sung test zero-denominator riêng cho AC / Observation / Priority, và assert các thành phần còn lại vẫn giữ nguyên. Xem [Cập nhật xử lý F3](#f3-fix).
4. [F4](#f4) (Major) — đã thêm log `warn` tại toàn bộ điểm bỏ qua record invalid trong `BlackboxCoverageJdbcAdapter`, kèm log-capture tests. Xem [Cập nhật xử lý F4](#f4-fix).
5. [F5](#f5) (Minor) — đã thêm ghi chú lý do dạng server log khi từng mẫu số blackbox bằng `0`, không đổi DTO/API contract. Xem [Cập nhật xử lý F5](#f5-fix).

<a id="next-action"></a>

### Next action

| Việc | Nội dung | Người nhận | Deadline |
| --- | --- | --- | --- |
| Người review con người xác nhận fix [F1](#f1) | Đọc [Cập nhật xử lý F1](#f1-fix), xác nhận verdict, đổi cột "Trạng thái" ở [§3](#3-tổng-hợp-findings) từ "Đã fix (chờ review)" sang "Đã xử lý" | reviewer con người | cần điền |
| Người review con người xác nhận fix [F2](#f2) đến [F5](#f5) | Đọc các mục cập nhật xử lý, xác nhận verdict và đổi cột "Trạng thái" ở [§3](#3-tổng-hợp-findings) từ "Đã fix (chờ review)" sang "Đã xử lý" nếu đồng ý | reviewer con người | cần điền |
| Điền `self-review.md` | Tick AC matching, liệt kê file đã đổi, chạy thật từ thư mục `EDCAP_BE`: `mvn "-Dtest=QaDashboardServiceTest,QaDashboardControllerTest,QaDashboardJdbcAdapterTest" test` và dán kết quả | dev | cần điền |
| Chạy lại targeted tests khi môi trường Maven ổn định | Ưu tiên `QaDashboardServiceTest`, `QaDashboardJdbcAdapterTest`, `BlackboxCoverageJdbcAdapterTest`; nếu có Docker thì chạy thêm `QaDashboardJdbcAdapterIntegrationTest` | dev | cần điền |

## 2. Tóm tắt diff

- **Migration** (`EDCAP_BE/src/main/resources/db/migration/V515__blackbox_coverage_schema.sql`) thêm mới 4 bảng blackbox (`tbl_fact_blackbox_case`, `_case_ac`, `_observation_point`, `_case_observation`), tách biệt hoàn toàn khỏi bảng AC-Test Coverage cũ.
- **Persistence layer mới** (`BlackboxCoveragePersistencePort` + `BlackboxCoverageJdbcAdapter`) thực hiện delete-then-reinsert cho design-side (case/mapping/observation) và update-only cho execution-side (`upsertBlackboxCaseResults`).
- **Scanner** (`ArtifactScannerService.persistBlackboxTestcasesParse`) parse `blackbox-testcases.md` và ghi vào 4 bảng trên; xoá evidence khi file bị gỡ.
- **Test-results parser** (`TestResultsParseService.toBlackboxResults`) map `SUCCESS/FAILED/SKIPPED/khác` → `PASS/FAIL/SKIP/NOT_RUN` và gọi `upsertBlackboxCaseResults`.
- **Dashboard** (`QaDashboardJdbcAdapter.findBlackboxCoverageCounts` + `QaDashboardService.computeBlackboxOverallCoverage`) tính `blackboxCoveragePercent` theo công thức weighted 0.30 Design + 0.70 Execution, giữ nguyên tên field DTO.

## 3. Tổng hợp findings

| # | Severity | Loại | Tóm tắt | AC liên quan | Người duyệt | Trạng thái |
| --- | --- | --- | --- | --- | --- | --- |
| F1 | Blocker | Correctness | [`AC Coverage (Execution)` đếm AC không có case P0 nào là "đã pass" (vacuous truth), và scope P0 = "required" không có trong spec](#f1) | AC-4, AC-5, AC-6 | pm_hieu | Đã fix (chờ review) — xem [Cập nhật xử lý F1](#f1-fix) |
| F2 | Major | Missing tests | [Không có test tầng repository/IT cho `findBlackboxCoverageCounts` — bug F1 không thể bị bắt bởi test hiện có](#f2) | AC-3, AC-4 | pm_hieu | Đã fix (chờ review) — xem [Cập nhật xử lý F2](#f2-fix) |
| F3 | Major | Missing tests | [Zero-denominator chỉ test dạng "tất cả về 0" cùng lúc, không test riêng từng thành phần (AC/Observation/Priority)](#f3) | AC-6 | pm_hieu | Đã fix (chờ review) — xem [Cập nhật xử lý F3](#f3-fix) |
| F4 | Major | Robustness | [`BlackboxCoverageJdbcAdapter` bỏ qua case/AC/observation key rỗng nhưng không log cảnh báo](#f4) | AC-1 | pm_hieu | Đã fix (chờ review) — xem [Cập nhật xử lý F4](#f4-fix) |
| F5 | Minor | Robustness | [Zero-denominator không ghi chú lý do (`no AC in scope` / `no observation points` / `no case planned`) như spec khuyến nghị](#f5) | AC-6 | pm_hieu | Đã fix (chờ review) — xem [Cập nhật xử lý F5](#f5-fix) |

## 4. Chi tiết findings

<a id="f1"></a>

### F1 — `AC Coverage (Execution)` tính sai khi AC không có case bắt buộc nào được map

**Severity:** Blocker · **Loại:** Correctness · **AC:** AC-BLACKBOX-COVERAGE-4, AC-BLACKBOX-COVERAGE-5, AC-BLACKBOX-COVERAGE-6

`spec-pack.md` định nghĩa: `AC Coverage (Execution) = Số AC có toàn bộ case bắt buộc PASS / Tổng AC × 100`, nhưng không định nghĩa "case bắt buộc" là gì. Code tự diễn giải "case bắt buộc" = case có `priority = 'P0'`, và dùng `NOT EXISTS` để đếm:

```sql
SUM(CASE WHEN NOT EXISTS (
    SELECT 1
    FROM tbl_fact_blackbox_case_ac bca
    JOIN tbl_fact_blackbox_case bc ON bc.blackbox_case_id = bca.blackbox_case_id
    WHERE bca.ac_key = ac.ac_key AND bca.ticket_id = ac.ticket_id
      AND bc.priority = 'P0' AND bc.execution_status <> 'PASS'
) THEN 1 ELSE 0 END) AS ac_with_required_cases_passed
```

- Nếu một AC **không có case P0 nào được map** (kể cả không có case nào cả), điều kiện con `EXISTS ... P0 AND status <> 'PASS'` luôn `FALSE` → `NOT EXISTS` luôn `TRUE` → AC đó vẫn được tính là "case bắt buộc đã PASS", dù thực tế chưa có bằng chứng test nào.
- Hệ quả: `AC Coverage (Execution)` bị thổi phồng — một AC hoàn toàn chưa được test (0 case map) vẫn cộng vào tử số, ngược với mục đích của Execution Coverage là "đánh giá bằng chứng chạy test thực tế" (`spec-pack.md` §7).
- Việc chọn P0 làm "case bắt buộc" cũng là suy đoán của implementer, không truy ngược được về câu chữ nào trong `spec-pack.md` — cần BA xác nhận đây có đúng định nghĩa nghiệp vụ hay không (ví dụ: có thể ý định thật là "toàn bộ case đã map, không phân biệt priority").

**Evidence:**

- [QaDashboardJdbcAdapter.java:102-108](../../../EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/QaDashboardJdbcAdapter.java#L102-L108) — khối SQL `NOT EXISTS` gây vacuous truth.
- [QaDashboardJdbcAdapter.java:86-88](../../../EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/QaDashboardJdbcAdapter.java#L86-L88) — comment tự ghi chú "Required" case = Priority P0", xác nhận đây là suy đoán riêng của code, không phải rule từ spec.
- [spec-pack.md:98](spec-pack.md#L98) — công thức gốc, không có định nghĩa "case bắt buộc".

**Đề xuất fix:** Sửa điều kiện đếm để một AC chỉ được tính "passed" khi nó **có ít nhất một case bắt buộc được map và tất cả case bắt buộc đó đều PASS** (thêm `EXISTS (... case bắt buộc nào đó ...)` làm điều kiện AND, không chỉ dựa vào `NOT EXISTS` một mình). Đồng thời chốt định nghĩa "case bắt buộc" với BA trước khi fix (P0-only hay tất cả case đã map).

<a id="f1-fix"></a>

#### Cập nhật xử lý F1 (2026-09-07)

**Trạng thái:** Đã fix trong code, chờ dev/reviewer con người xác nhận lại (chưa merge; đã thử chạy test mục tiêu nhưng môi trường hiện tại chưa resolve được plugin/dependency để vào pha chạy test).

BA đã chốt: bỏ khái niệm "case bắt buộc = P0", đổi sang "AC Coverage (Execution) = AC có **ít nhất 1 case map** và **toàn bộ case đã map** đều PASS" — không phân biệt priority. `spec-pack.md` §5.2 đã được cập nhật đồng bộ với code (đúng nguyên tắc Single Source of Truth).

**Đã thay đổi:**

- [spec-pack.md:98](spec-pack.md#L98) — công thức đổi thành `AC Coverage (Execution) = Số AC có ít nhất 1 case map và toàn bộ case map cho AC đó đều PASS / Tổng AC × 100`, xoá hẳn khái niệm "case bắt buộc" mơ hồ ban đầu.
- [QaDashboardJdbcAdapter.java:100-113](../../../EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/QaDashboardJdbcAdapter.java#L100-L113) — cột `ac_with_all_mapped_cases_passed` giờ thêm điều kiện `EXISTS (case nào đó map cho AC)` **AND** `NOT EXISTS (case map có status <> PASS)`, thay cho `NOT EXISTS` đứng một mình. Đây đúng là điểm chặn cho vacuous-truth bug: AC không map case nào giờ có `EXISTS = false` → không còn được tính là "passed". Điều kiện cũng bỏ hẳn `bc.priority = 'P0'`, đúng theo định nghĩa BA mới chốt.
- [QaDashboardModels.java:81-89](../../../EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/qadashboard/QaDashboardModels.java#L81-L89) — field `acWithRequiredCasesPassed` đổi tên thành `acWithAllMappedCasesPassed`, khớp với ngữ nghĩa mới.
- [QaDashboardService.java:113-114](../../../EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/qadashboard/QaDashboardService.java#L113-L114) — dùng đúng field mới, công thức tính không đổi.
- Test mới: [QaDashboardJdbcAdapterTest.java](../../../EDCAP_BE/src/test/java/com/sdd/platform/infrastructure/persistence/adapter/QaDashboardJdbcAdapterTest.java) — assert chuỗi SQL sinh ra chứa `EXISTS (` + `AND NOT EXISTS (` + `bc.execution_status <> 'PASS'` và **không còn** chứa `bc.priority = 'P0'` / `ac_with_required_cases_passed`.

**Ảnh hưởng:**

- Observation Coverage và Priority Coverage không đổi (không nằm trong phạm vi fix này).
- Case hợp lệ trước đây (AC có case P0 map và PASS) vẫn được tính đúng như cũ; case biên (AC không map case nào, hoặc chỉ map case P1/P2 mà FAIL) giờ đã bị loại đúng khỏi tử số.
- Không thay đổi contract API/DTO và không mở rộng sang refactor ngoài phạm vi F2-F5.

**Commands/evidence:**

- Từ repo root: `mvn -pl EDCAP_BE "-Dtest=QaDashboardJdbcAdapterTest,QaDashboardServiceTest,QaDashboardControllerTest" test` — fail ngay ở Maven project selection: `The requested required projects EDCAP_BE do not exist.` Điều này cho thấy command mẫu cũ kiểu `-pl EDCAP_BE` không phù hợp với repo hiện tại (repo chỉ có `EDCAP_BE/pom.xml`, không phải root reactor có module `EDCAP_BE`).
- Từ thư mục `EDCAP_BE`: `mvn "-Dtest=QaDashboardJdbcAdapterTest,QaDashboardServiceTest,QaDashboardControllerTest" test` — fail trước khi vào test do `.m2` lock trên `spring-boot-starter-parent` (`Could not open file channel ... .lock`).
- Từ thư mục `EDCAP_BE` với local repo trong workspace: `mvn "-Dmaven.repo.local=D:\EDCAP\Source\EDCAP_FULL\.tmp-m2" "-Dtest=QaDashboardJdbcAdapterTest,QaDashboardServiceTest,QaDashboardControllerTest" test` — đi qua `resources` / `compile` nhưng fail ở `build-helper-maven-plugin:3.6.0:add-test-source` vì không resolve được dependency/plugin trong môi trường hiện tại; chưa có test nào được thực thi.
- Code inspection bổ sung: `rg -n "acWithRequiredCasesPassed\\(|ac_with_required_cases_passed|Required case = Priority P0" ...` — xác nhận code production không còn giữ tên field/comment/SQL cũ; phần còn sót chỉ nằm trong mô tả finding lịch sử của `ai-review.md`.

**Remaining issues (liên quan F1):**

- `QaDashboardJdbcAdapterTest` vẫn là lớp bảo vệ mức unit/mock-string cho regression SQL shape; khoảng trống repository-level đã được bù thêm bằng `QaDashboardJdbcAdapterIntegrationTest` ở [F2](#f2).
- [F2](#f2), [F3](#f3), [F4](#f4), [F5](#f5) đã có thay đổi tương ứng trong code/test; còn thiếu bước verify thực tế bằng Maven khi môi trường resolve được dependency/plugin ổn định.
- Người review con người cần đọc lại diff trên và xác nhận verdict trước khi đổi cột "Trạng thái" ở [§3](#3-tổng-hợp-findings) từ "Đã fix (chờ review)" sang "Đã xử lý".

**Next action:** Dev chạy thật bộ test liệt kê ở trên từ thư mục `EDCAP_BE` và dán kết quả vào `self-review.md`; sau đó reviewer con người xác nhận verdict cho F1. Song song, nên nâng `QaDashboardJdbcAdapterTest` thành IT thật (chạy SQL trên DB test) để đóng luôn [F2](#f2).

---

<a id="f2"></a>

### F2 — Không có test tầng repository/IT cho `findBlackboxCoverageCounts`

**Severity:** Major · **Loại:** Missing tests · **AC:** AC-BLACKBOX-COVERAGE-3, AC-BLACKBOX-COVERAGE-4

`QaDashboardServiceTest` mock toàn bộ `QaDashboardRepositoryPort` (`repository = Mockito.mock(...)`), nên chỉ verify công thức toán học ở tầng service, không verify SQL thật ở `QaDashboardJdbcAdapter`. Hiện đã có `QaDashboardJdbcAdapterTest`, nhưng test này vẫn mock `NamedParameterJdbcTemplate` và chỉ assert nội dung chuỗi SQL, chưa thực thi SQL thật trên DB test. Vì vậy bug kiểu [F1](#f1) giờ đã được chặn ở mức regression-về-chuỗi-SQL, nhưng chưa có bằng chứng hành vi trên dữ liệu thật.

**Evidence:**

- [QaDashboardServiceTest.java:28](../../../EDCAP_BE/src/test/java/com/sdd/platform/application/usecase/qadashboard/QaDashboardServiceTest.java#L28) — `repository = Mockito.mock(QaDashboardRepositoryPort.class)`.
- [QaDashboardJdbcAdapterTest.java](../../../EDCAP_BE/src/test/java/com/sdd/platform/infrastructure/persistence/adapter/QaDashboardJdbcAdapterTest.java) — test mới chỉ assert nội dung SQL (`contains(...)`), chưa seed/execute DB thật.
- `impl-plan.md` §10 vẫn yêu cầu verify repository-level bằng seeded data / DB thật, chưa được tự động hóa.

**Đề xuất fix:** Thêm test tích hợp (`@SpringBootTest` hoặc test JDBC với DB test container/H2-Postgres) seed dữ liệu theo đúng ví dụ `spec-pack.md` §8 và verify `findBlackboxCoverageCounts` trả đúng từng field, đặc biệt case AC có mapped case `FAIL` hoặc AC không map case nào.

<a id="f2-fix"></a>

#### Cập nhật xử lý F2 (2026-09-07)

**Trạng thái:** Đã fix trong code, chờ dev/reviewer con người xác nhận lại.

**Đã thay đổi:**

- Thêm test mới: [QaDashboardJdbcAdapterIntegrationTest.java](../../../EDCAP_BE/src/test/IntegrationTest/java/com/sdd/platform/infrastructure/persistence/adapter/QaDashboardJdbcAdapterIntegrationTest.java)
- Test này dựng Postgres test container tối thiểu, tạo đúng các bảng/query cần cho `findBlackboxCoverageCounts`, seed dữ liệu thật cho 3 AC:
  - `AC-1` có mapped case `PASS`
  - `AC-2` có mapped case `FAIL`
  - `AC-3` không map case nào
- Assert kết quả thật qua JDBC:
  - `totalAc = 3`
  - `acWithCaseMapped = 2`
  - `acWithAllMappedCasesPassed = 1`
  - các count observation/priority cũng trả đúng theo seed data

**Ảnh hưởng:**

- F2 giờ không còn phụ thuộc hoàn toàn vào mock-string assertion của `QaDashboardJdbcAdapterTest`.
- Regression kiểu vacuous-truth ở tầng SQL đã có một bài test dữ liệu thật để chặn lại khi môi trường có Docker/Testcontainers.

**Commands/evidence:**

- File đã sửa/thêm: `EDCAP_BE/src/test/IntegrationTest/java/com/sdd/platform/infrastructure/persistence/adapter/QaDashboardJdbcAdapterIntegrationTest.java`
- Đã chạy: `mvn "-Dmaven.repo.local=D:\EDCAP\Source\EDCAP_FULL\.tmp-m2" "-Dit.test=QaDashboardJdbcAdapterIntegrationTest" verify` từ thư mục `EDCAP_BE`
- Kết quả: Maven dừng trước pha test với `PluginResolutionException` khi resolve `org.apache.maven.plugins:maven-jar-plugin:3.4.2`; integration test chưa được thực thi trong môi trường hiện tại.

---

<a id="f3"></a>

### F3 — Zero-denominator chỉ test gộp, chưa test riêng từng thành phần

**Severity:** Major · **Loại:** Missing tests · **AC:** AC-BLACKBOX-COVERAGE-6

`review-checklist.md` TEST-2 (Blocker) và `self-review.md` mục 6 đều yêu cầu test riêng: "không AC trong scope", "không observation point", "không case planned" là 3 case độc lập. Test hiện tại (`getSummary_blackboxCoverage_zeroDenominators`) chỉ dùng `stubZeroState()` khiến **tất cả** mẫu số về 0 cùng lúc, không cô lập được từng nhánh `c.totalAc() == 0`, `c.totalObservationPoints() == 0`, `c.plannedPriorityScore() == 0` trong `computeBlackboxOverallCoverage`.

**Evidence:**

- [QaDashboardServiceTest.java:114-121](../../../EDCAP_BE/src/test/java/com/sdd/platform/application/usecase/qadashboard/QaDashboardServiceTest.java#L114-L121) — test duy nhất cho zero-denominator, dùng toàn bộ counts = 0.
- [QaDashboardService.java:107-120](../../../EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/qadashboard/QaDashboardService.java#L107-L120) — 3 nhánh zero-check độc lập chưa từng được test riêng lẻ.
- `self-review.md:75-80` — checklist yêu cầu 3 case riêng, hiện để trống, chưa tick.

**Đề xuất fix:** Thêm 3 test case: (1) `totalAc=0` nhưng `totalObservationPoints>0` và `plannedPriorityScore>0`; (2) `totalObservationPoints=0` nhưng 2 thành phần còn lại > 0; (3) `plannedPriorityScore=0` nhưng 2 thành phần còn lại > 0. Assert từng % thành phần = 0% mà không kéo các thành phần khác về 0.

<a id="f3-fix"></a>

#### Cập nhật xử lý F3 (2026-09-07)

**Trạng thái:** Đã fix trong code, chờ dev/reviewer con người xác nhận lại.

**Đã thay đổi:**

- Cập nhật [QaDashboardServiceTest.java](../../../EDCAP_BE/src/test/java/com/sdd/platform/application/usecase/qadashboard/QaDashboardServiceTest.java)
- Thêm 3 test riêng:
  - zero AC denominator nhưng Observation/Priority vẫn có dữ liệu
  - zero Observation denominator nhưng AC/Priority vẫn có dữ liệu
  - zero Priority denominator nhưng AC/Observation vẫn có dữ liệu
- Mỗi test đều assert `blackboxCoveragePercent` cuối cùng và đồng thời kiểm tra log lý do tương ứng để chứng minh nhánh zero-denominator được đi qua độc lập.

**Ảnh hưởng:**

- 3 nhánh `totalAc == 0`, `totalObservationPoints == 0`, `plannedPriorityScore == 0` trong `computeBlackboxOverallCoverage` giờ có coverage riêng, không còn bị “che” bởi một test all-zero duy nhất.

**Commands/evidence:**

- File đã sửa: `EDCAP_BE/src/test/java/com/sdd/platform/application/usecase/qadashboard/QaDashboardServiceTest.java`
- Đã chạy: `mvn "-Dmaven.repo.local=D:\EDCAP\Source\EDCAP_FULL\.tmp-m2" "-Dtest=QaDashboardServiceTest,QaDashboardJdbcAdapterTest,BlackboxCoverageJdbcAdapterTest" test` từ thư mục `EDCAP_BE`
- Kết quả: Maven đi qua `resources` / `compile` nhưng dừng ở `build-helper-maven-plugin:3.6.0:add-test-source` do không resolve được dependency plugin (`org.apache.maven.shared:file-management:3.1.0`, `org.codehaus.plexus:plexus-utils:4.0.1`, `org.apache-extras.beanshell:bsh:2.0b6`); chưa có test result thực thi.

---

<a id="f4"></a>

### F4 — Bỏ qua key rỗng khi persist nhưng không log cảnh báo

**Severity:** Major · **Loại:** Robustness · **AC:** AC-BLACKBOX-COVERAGE-1

`review-checklist.md` LOG-3 (Major) yêu cầu: "Case key/AC key/observation key rỗng hoặc null bị bỏ qua **và có log cảnh báo**". `BlackboxCoverageJdbcAdapter` bỏ qua đúng (silently `continue`) nhưng không import `Logger`, không có bất kỳ dòng log nào trong toàn bộ class.

**Evidence:**

- [BlackboxCoverageJdbcAdapter.java:39-42](../../../EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/BlackboxCoverageJdbcAdapter.java#L39-L42) — bỏ qua case key rỗng, không log.
- [BlackboxCoverageJdbcAdapter.java:79-82](../../../EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/BlackboxCoverageJdbcAdapter.java#L79-L82) — bỏ qua AC-mapping rỗng, không log.
- [BlackboxCoverageJdbcAdapter.java:112-115](../../../EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/BlackboxCoverageJdbcAdapter.java#L112-L115) — bỏ qua observation key rỗng, không log.
- Toàn file không có `import org.slf4j.Logger` — xác nhận không có channel log nào ở class này.

**Đề xuất fix:** Thêm `Logger` và log `warn` (kèm `ticketId`, key bị bỏ qua) tại mỗi điểm `continue` do key rỗng/null, theo đúng pattern đã dùng ở `ArtifactScannerService` (`log.warn("Blackbox-testcases parse warnings ...")`).

<a id="f4-fix"></a>

#### Cập nhật xử lý F4 (2026-09-07)

**Trạng thái:** Đã fix trong code, chờ dev/reviewer con người xác nhận lại.

**Đã thay đổi:**

- Cập nhật [BlackboxCoverageJdbcAdapter.java](../../../EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/BlackboxCoverageJdbcAdapter.java)
- Thêm `Logger` và `warn` log ở toàn bộ điểm skip record invalid:
  - `replaceBlackboxCases`
  - `replaceBlackboxCaseAcMappings`
  - `replaceBlackboxObservationPoints`
  - `replaceBlackboxCaseObservationLinks`
  - `upsertBlackboxCaseResults`
- Thêm test mới: [BlackboxCoverageJdbcAdapterTest.java](../../../EDCAP_BE/src/test/java/com/sdd/platform/infrastructure/persistence/adapter/BlackboxCoverageJdbcAdapterTest.java)
- Các test log-capture xác nhận record invalid bị bỏ qua và không phát sinh thêm câu lệnh insert/update ngoài số lượng kỳ vọng.

**Ảnh hưởng:**

- Parser/persistence path giữ nguyên hành vi bỏ qua dữ liệu bẩn, nhưng giờ có dấu vết điều tra (`ticketId`, key/value invalid) để đối soát parser input.
- Không đổi schema, không đổi contract API, không mở rộng sang validation cứng.

**Commands/evidence:**

- File đã sửa/thêm:
  - `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/BlackboxCoverageJdbcAdapter.java`
  - `EDCAP_BE/src/test/java/com/sdd/platform/infrastructure/persistence/adapter/BlackboxCoverageJdbcAdapterTest.java`
- Đã chạy: `mvn "-Dmaven.repo.local=D:\EDCAP\Source\EDCAP_FULL\.tmp-m2" "-Dtest=QaDashboardServiceTest,QaDashboardJdbcAdapterTest,BlackboxCoverageJdbcAdapterTest" test` từ thư mục `EDCAP_BE`
- Kết quả: cùng failure ở `build-helper-maven-plugin:3.6.0:add-test-source`, nên log-capture tests chưa được Maven thực thi trong môi trường hiện tại.

---

<a id="f5"></a>

### F5 — Zero-denominator không ghi chú lý do

**Severity:** Minor · **Loại:** Robustness · **AC:** AC-BLACKBOX-COVERAGE-6

`spec-pack.md` §6.2 khuyến nghị ("Nên ghi chú rõ lý do") khi mẫu số = 0, ví dụ `0% (no AC in scope)`. Đây là "nên", không phải "phải", nên chỉ ở mức Minor. `computeBlackboxOverallCoverage` trả thẳng `0.0` cho từng nhánh mà không log hay đính kèm lý do nào (server-side), khác với yêu cầu ví dụ ở spec.

**Evidence:**

- [QaDashboardService.java:107,109,113,115,117](../../../EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/qadashboard/QaDashboardService.java#L107) — 5 nhánh zero-check trả `0.0` không kèm lý do.
- [spec-pack.md:122-131](spec-pack.md#L122-L131) — quy tắc mẫu số bằng 0.

**Đề xuất fix:** Vì DTO không được thêm field (COMPAT-2), có thể chỉ cần log `debug`/`info` phía server khi một nhánh về 0 kèm lý do, không cần thay đổi contract API. Có thể để lại nếu team chấp nhận rủi ro (đây chỉ là "nên", không chặn merge).

<a id="f5-fix"></a>

#### Cập nhật xử lý F5 (2026-09-07)

**Trạng thái:** Đã fix trong code, chờ dev/reviewer con người xác nhận lại.

**Đã thay đổi:**

- Cập nhật [QaDashboardService.java](../../../EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/qadashboard/QaDashboardService.java)
- Tách helper `componentPercent(...)` để log lý do khi mẫu số bằng `0`:
  - `no AC in scope`
  - `no observation points defined`
  - `no case planned`
- Không thêm field mới vào DTO/API; chỉ bổ sung server-side log như spec khuyến nghị.
- `QaDashboardServiceTest.java` đồng thời capture các log này ở từng zero-case riêng, nên F5 được bảo vệ cùng F3.

**Ảnh hưởng:**

- Dashboard response shape giữ nguyên.
- Khi số liệu blackbox về `0` do thiếu mẫu số, phía server có thêm context để debug thay vì chỉ thấy `0.0` trần.

**Commands/evidence:**

- File đã sửa:
  - `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/qadashboard/QaDashboardService.java`
  - `EDCAP_BE/src/test/java/com/sdd/platform/application/usecase/qadashboard/QaDashboardServiceTest.java`
- Đã chạy: `mvn "-Dmaven.repo.local=D:\EDCAP\Source\EDCAP_FULL\.tmp-m2" "-Dtest=QaDashboardServiceTest,QaDashboardJdbcAdapterTest,BlackboxCoverageJdbcAdapterTest" test` từ thư mục `EDCAP_BE`
- Kết quả: cùng failure ở `build-helper-maven-plugin:3.6.0:add-test-source`, nên zero-denominator logging tests chưa được Maven thực thi trong môi trường hiện tại.

## 5. Độ phủ review

| Vùng code | Cách kiểm chứng | Kết quả |
| --- | --- | --- |
| `V515__blackbox_coverage_schema.sql` | Đọc code tĩnh | Đạt — chỉ thêm bảng mới, có index theo ticket_id, không đụng bảng AC-Test Coverage cũ |
| `BlackboxCoveragePersistencePort` / `BlackboxCoverageJdbcAdapter` | Đọc code tĩnh + test log-capture | Delete-then-reinsert (design) / update-only (execution) đúng theo plan; các điểm skip invalid record giờ đã có `warn` log và unit test tương ứng |
| `ArtifactScannerService.persistBlackboxTestcasesParse` + xử lý file bị xoá | Đọc code tĩnh + grep | Đạt — parse, persist, và xoá evidence khi `blackbox-testcases.md` biến mất đều đúng |
| `TestResultsParseService.toBlackboxResults` | Đọc code tĩnh | Đạt — map đúng `SUCCESS/FAILED/SKIPPED/khác` → `PASS/FAIL/SKIP/NOT_RUN`; unmatched key không ghi đè (đúng ERR-1) |
| `QaDashboardJdbcAdapter.findBlackboxCoverageCounts` | Đọc code tĩnh, đối chiếu công thức spec §5.2 (đã cập nhật) | **Đã fix trong code** — không còn vacuous-truth, không còn scope P0 — xem [Cập nhật xử lý F1](#f1-fix); chờ IT thật xác nhận hành vi trên dữ liệu thật |
| `QaDashboardService.computeBlackboxOverallCoverage` | Đọc code tĩnh + chạy nhẩm ví dụ spec §8 | Đạt về công thức/rounding/zero-denominator response shape; input đầu vào (từ adapter) nay đã đúng theo fix [F1](#f1) |
| `QaDashboardServiceTest`, `QaDashboardJdbcAdapterTest`, `BlackboxCoverageJdbcAdapterTest`, `QaDashboardJdbcAdapterIntegrationTest` | Đọc code test | Đã có thêm UT zero-denominator riêng từng thành phần, UT log-capture cho skip invalid record, và IT JDBC thật cho `findBlackboxCoverageCounts`; DTO field `blackboxCoveragePercent` vẫn giữ nguyên |
| `mvn -pl EDCAP_BE "-Dtest=QaDashboardServiceTest,QaDashboardControllerTest,QaDashboardJdbcAdapterTest" test` (repo root) và `mvn "-Dtest=QaDashboardServiceTest,QaDashboardControllerTest,QaDashboardJdbcAdapterTest" test` (trong `EDCAP_BE`) | thử chạy targeted tests | fail trước khi có test result: command root không đúng module layout; command trong `EDCAP_BE` tiếp tục vấp `.m2` lock và plugin/dependency resolution trong môi trường hiện tại |
| `self-review.md` | Đọc file | Toàn bộ để trống, không có evidence — cần điền thật |

## 6. Traceability — AC chưa đạt

| AC | Dòng trong spec | Verdict | Finding |
| --- | --- | --- | --- |
| AC-BLACKBOX-COVERAGE-4 | [spec-pack.md:104](spec-pack.md#L104) | Đã fix trong code (chờ review con người) — priority weight đúng, phần AC/Execution đã sửa theo [Cập nhật xử lý F1](#f1-fix) | [F1](#f1) |
| AC-BLACKBOX-COVERAGE-5 | [spec-pack.md:98](spec-pack.md#L98) | Đã fix trong code (chờ review con người) — công thức spec đã cập nhật đồng bộ, case biên (AC không map case) đã được loại đúng | [F1](#f1) |
| AC-BLACKBOX-COVERAGE-6 | [spec-pack.md:122-131](spec-pack.md#L122-L131) | Đã fix trong code (chờ review con người) — đã có test riêng từng thành phần và server-side zero-denominator reason log | [F3](#f3), [F5](#f5) |
| AC-BLACKBOX-COVERAGE-1 | [spec-pack.md:88-94](spec-pack.md#L88-L94) | Đã fix trong code (chờ review con người) — skip invalid row giờ có log cảnh báo theo checklist | [F4](#f4) |

## 7. Đề xuất test cases bổ sung

| Test case | Lớp test | Liên quan | Ưu tiên |
| --- | --- | --- | --- |
| [TC 1](#tc-1) | IT/Repository | F1 (Blocker) | **Chặn merge** |
| [TC 2](#tc-2) | UT | F3 (Major) | Bắt buộc nếu fix F3 |
| [TC 3](#tc-3) | IT/Repository | F2 (Major) | Đã có bản tối thiểu |
| [TC 4](#tc-4) | UT/Log-capture | F4 (Major) | Đã có |

<a id="tc-1"></a>

### TC 1 — IT: `QaDashboardJdbcAdapter.findBlackboxCoverageCounts` với AC không map case nào

*Liên quan [F1](#f1) · AC-BLACKBOX-COVERAGE-4,5,6 · **Chặn merge***

- Seed 1 AC không có bất kỳ blackbox case nào map → kỳ vọng `acWithAllMappedCasesPassed = 0`. Bug cũ từng trả về `1` do vacuous truth.
- Seed 1 AC có 1 case P1 FAIL → kỳ vọng `acWithAllMappedCasesPassed = 0`.
- Seed 1 AC có 1 case P0 PASS hoặc P1 PASS → kỳ vọng `acWithAllMappedCasesPassed` cộng đúng `1`, vì execution coverage không còn phân biệt theo priority.

<a id="tc-2"></a>

### TC 2 — UT: `computeBlackboxOverallCoverage` zero-denominator từng thành phần riêng lẻ

*Liên quan [F3](#f3) · AC-BLACKBOX-COVERAGE-6 · Bắt buộc nếu fix F3*

- `totalAc=0`, `totalObservationPoints=5`, `plannedPriorityScore=10` (giá trị passed khác 0) → kỳ vọng `AC Coverage (Design/Execution) = 0%`, nhưng Observation/Priority coverage vẫn tính bình thường theo dữ liệu.
- `totalObservationPoints=0` với AC/Priority > 0 → kỳ vọng riêng `Observation Coverage = 0%`.
- `plannedPriorityScore=0` với AC/Observation > 0 → kỳ vọng riêng `Priority Coverage = 0%`.

<a id="tc-3"></a>

### TC 3 — IT: seed dữ liệu thật cho `findBlackboxCoverageCounts` qua tầng JDBC

*Liên quan [F2](#f2) · AC-BLACKBOX-COVERAGE-3,4 · Đã có bản tối thiểu*

- Dựng dữ liệu thật trong 4 bảng blackbox (10 AC, 8 map case, 9 pass, 5 observation point, 5 direct-pass, planned score 22, passed 18) → gọi `findBlackboxCoverageCounts` thật qua `NamedParameterJdbcTemplate`, xác nhận counts trả về đúng như input, không chỉ verify ở tầng service (mock).

<a id="tc-4"></a>

### TC 4 — UT: log cảnh báo khi bỏ qua key rỗng

*Liên quan [F4](#f4) · AC-BLACKBOX-COVERAGE-1 · Đã có*

- Gọi `replaceBlackboxCases`/`replaceBlackboxCaseAcMappings`/`replaceBlackboxObservationPoints`/`replaceBlackboxCaseObservationLinks` với 1 record có key rỗng/null trong danh sách → xác nhận record đó bị bỏ qua (không insert) **và** có 1 log warn được ghi (dùng log-capture appender hoặc test appender).

## 8. Số liệu thống kê

> Số liệu dưới đây phản ánh pass review này + 2 vòng fix (2026-09-07: F1, sau đó F2-F5). Các tỷ lệ yêu cầu dữ liệu xử lý sau merge (false-positive / human-accepted / hữu ích) vẫn cần người review điền.

| Số liệu | Giá trị hiện tại | Ghi chú |
| --- | --- | --- |
| Tổng số finding | 5 (1 Blocker, 3 Major, 1 Minor) | Xem [§3](#3-tổng-hợp-findings) |
| Tổng số finding đã fix | 5 / 5 (đều chờ review con người) | F1-F5 đều đã có thay đổi tương ứng trong code/test/docs |
| Tỷ lệ xử lý finding nghiêm trọng (Blocker + Major) | 4 / 4 (100%, chờ review con người xác nhận) | F1-F4 đều đã fix trong code; còn thiếu xác nhận người review và kết quả chạy test thật trong môi trường ổn định |
| Tỷ lệ AI finding được con người chấp nhận | chưa có dữ liệu | Cần người review điền sau khi xác nhận từng finding |
| Tỷ lệ AI review finding hữu ích | chưa có dữ liệu | Cần đo sau khi dev xác nhận mức độ hữu ích của từng finding |
| Tỷ lệ AI finding bị đánh giá là false positive | chưa có dữ liệu | Mọi finding đều có file/line xác minh trên bản hiện tại; tỷ lệ false-positive thực tế cần người review xác nhận |
| Tỷ lệ AI finding đã được xử lý (fix hoặc quyết định chính thức) | 5 / 5 (100%, đều ở trạng thái fix chờ review) | Cập nhật cột "Trạng thái" ở [§3](#3-tổng-hợp-findings) sang "Đã xử lý" khi có xác nhận cuối |

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
- **Số dòng trong Evidence:** chính xác tại thời điểm review (không có git SHA để đối chiếu do `.git` rỗng).
- **Ký hiệu:** không dùng `⬜` placeholder trong tài liệu này — mọi mục đã được điền dựa trên bằng chứng đọc được; các ô "chưa có dữ liệu" là do phụ thuộc hoạt động sau merge (human review), không phải do thiếu sót khi viết tài liệu.
