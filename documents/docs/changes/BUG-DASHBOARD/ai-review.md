# AI Review — Ticket BUG-DASHBOARD (Ticket Bug Metrics)

- **Diff review:** BE `077ae6d` (HEAD, `main`) + working-tree changes (tracked diff + new untracked files); FE `c9da80d` (HEAD, `main`) + working-tree changes (tracked diff + new untracked files). No prior commit exists for this ticket in either repo yet — the entire feature is uncommitted working-tree state.
- **Branch:** `main` (chưa merge; chưa có commit riêng cho ticket)
- **Nguồn đối chiếu:** [docs/changes/BUG-DASHBOARD/spec-pack.md](spec-pack.md)
- **Reviewer:** AI review — _người review cần điền tên và xác nhận lại_
- **Ngày review:** 2026-08-07
- **Cách kiểm chứng:** đọc code tĩnh (BE + FE, full read của mọi file mới/thay đổi) + `git status`/`git diff` trên 2 repo con (`EDCAP_BE/`, `EDCAP_FE/`) + đối chiếu tên test case trong `self-review.md` với source test thật. Chưa chạy `mvn test`/`vitest` lại trong session này (tin vào kết quả `self-review.md` §4: 22 BE + 14 FE test pass), chưa chạy E2E, chưa chạy app thật.
- **Phạm vi:** chỉ ghi kết quả review, không sửa source code.

## Mục lục

1. [Kết luận](#1-kết-luận) — [Next action](#next-action)
2. [Tóm tắt diff](#2-tóm-tắt-diff)
3. [Tổng hợp findings](#3-tổng-hợp-findings)
4. [Chi tiết findings](#4-chi-tiết-findings)
5. [Độ phủ review](#5-độ-phủ-review)
6. [Traceability — AC chưa đạt](#6-traceability--ac-chưa-đạt)
7. [Đề xuất test cases bổ sung](#7-đề-xuất-test-cases-bổ-sung)
8. [Phụ lục — Quy ước](#8-phụ-lục--quy-ước)

## 1. Kết luận

**Verdict: Request changes.**

2 Blocker · 2 Major · 5 Minor.

Điều kiện để pass:

1. [F1](#f1) (BE `/options` route bỏ qua role-check theo project) phải fix + có test theo case "other role"/"null caller" gọi `/options`.
2. [F2](#f2) (FE route guard bị comment-out trong `App.tsx`, và guard tự thân không bao giờ trigger vì trang không đưa `projectId` lên URL) phải fix — đây là lỗ hổng kép làm BR-9 (chặn hoàn toàn role ngoài PM/QA/DEV/ADMIN khỏi màn hình) không được thực thi ở FE dù BE đã đúng cho các route còn lại.
3. [F3](#f3) (race giữa check-duplicate và insert trả về 500 thay vì 409 khi 2 request tạo cùng lúc) nên fix hoặc được BA/Tech Lead chấp nhận là Accepted Risk có ghi chú rõ (tần suất race thấp nhưng vi phạm đúng "hợp đồng" AC-12/AC-4).
4. Các file không liên quan ticket đang bị gộp vào diff BE ([F4](#f4): đổi tên DB dev, xoá `DEFAULT`/thêm code chết trong `SddPlatformApplication.java`) cần được tách ra khỏi commit trước khi merge.
5. `self-review.md` §5 và §11 tự chấm "Pass" cho toàn bộ role-gating — cần cập nhật lại tài liệu này để phản ánh đúng Finding 1/2 trước khi coi review này là đã đóng.

<a id="next-action"></a>

### Next action

| Việc | Nội dung | Người nhận | Deadline |
| --- | --- | --- | --- |
| Fix [F1](#f1) + test | Thêm `requireViewAccess`/`resolveAccess` vào `TicketBugMetricsService.options()`, có test "other role"/null caller | BE dev | Trước merge |
| Fix [F2](#f2) + test | DONE (2026-08-07): route da duoc boc guard o `App.tsx`; guard da duoc sua de check access ngay ca khi URL khong co `projectId` | FE dev | Closed |
| Fix [F3](#f3) + test | DONE (2026-08-07): map duplicate-key race ? create t? DB exception sang `ConflictException` d? tr? 409 theo AC-4/AC-12 | BE dev | Closed |
| Tách [F4](#f4) khỏi commit | Revert `application.yml`/`SddPlatformApplication.java` về trạng thái trước khi làm ticket này, hoặc xác nhận đây là thay đổi hợp lệ có chủ đích | BE dev | Trước merge |
| Cập nhật `self-review.md` | Phản ánh F1/F2 thay vì giữ nguyên "Pass" toàn bộ ở §5/§11 | Author | Trước khi đóng review |
| Xác nhận Minor F5–F8 | Review nhanh, quyết định fix ngay hay backlog | Team | Không chặn merge |

### F1 status update (2026-08-07)

- **Status:** Done (code + test đã cập nhật, unit test pass).
- **Files changed:**
  - `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/ticketbugmetrics/TicketBugMetricsService.java`
  - `EDCAP_BE/src/test/UnitTest/java/com/sdd/platform/application/usecase/ticketbugmetrics/TicketBugMetricsServiceTest.java`
- **Implementation evidence:**
  - `TicketBugMetricsService.options(...)` đổi gate từ `requireAuthenticated(caller)` sang `requireViewAccess(caller, projectId)`.
  - `TicketBugMetricsServiceTest.otherRoles_blockedFromEveryAction(...)` bổ sung assert `service.options(PROJECT_ID, null, caller)` ném `ForbiddenException`.
  - `TicketBugMetricsServiceTest.unauthenticatedCaller_blockedFromEveryAction(...)` bổ sung assert `service.options(PROJECT_ID, null, null)` ném `ForbiddenException`.
- **Commands run:**
  - Baseline (pre-change): `mvn -f EDCAP_BE/pom.xml -Dtest=TicketBugMetricsServiceTest test` -> `BUILD SUCCESS`, `Tests run: 19, Failures: 0, Errors: 0`.
  - Post-change: `mvn -f EDCAP_BE/pom.xml -Dtest=TicketBugMetricsServiceTest test` -> `BUILD SUCCESS`, `Tests run: 19, Failures: 0, Errors: 0`.
### F2 status update (2026-08-07)

- **Status:** Done (scope F2 completed on FE guard + dedicated guard test).
- **Authoritative source for mismatch:** Current code state (not stale review wording). `App.tsx` route already wraps `TicketBugMetricsPage` with `<RequireTicketBugMetricsAccess>`.
- **Files changed:**
  - `EDCAP_FE/src/components/auth/RequireTicketBugMetricsAccess.tsx`
  - `EDCAP_FE/src/__ tests __/auth/RequireTicketBugMetricsAccess.test.tsx` (new)
- **Implementation evidence:**
  - `RequireTicketBugMetricsAccess` now runs access-check whenever `isAuthenticated` is true (not gated by URL `projectId`).
  - Loading/error handling no longer depends on `projectId` existing in query-string.
  - `401/403` still routes through `ForceLogoutAndRedirect`; non-auth errors still show retry UI.
  - New unit tests cover:
    - authenticated user without `projectId` still calls `endpoints.ticketBugMetrics.access({ projectId: undefined })`,
    - `403` returns force-logout path (children not rendered),
    - success path renders protected children.
- **Commands run:**
  - `npm run test:unit -- --run "src/__ tests __/auth/RequireTicketBugMetricsAccess.test.tsx"` -> PASS.
  - `npm run test:unit -- --run "src/__ tests __/ticket-bug-metrics/ticket-bug-metrics.test.tsx"` -> PASS.
  - `npm run build` -> FAIL (outside F2 scope): `TS6133` unused symbols in `TicketBugMetricsPage.tsx` (`CButton`, `filterOpen`, `setFilterOpen`).


### F3 status update (2026-08-07)

- **Status:** Done (scope F3 completed on BE conflict-mapping + unit test).
- **Authoritative source for mismatch:** `spec-pack.md` �20 (Implementation Addendum) + AC-4/AC-12 contract (duplicate active row must return 409).
- **Files changed:**
  - `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/ticketbugmetrics/TicketBugMetricsService.java`
  - `EDCAP_BE/src/test/UnitTest/java/com/sdd/platform/application/usecase/ticketbugmetrics/TicketBugMetricsServiceTest.java`
- **Implementation evidence:**
  - `TicketBugMetricsService.create(...)` keeps pre-check `existsActiveByTicketId(...)` unchanged.
  - Added `try/catch` around `repository.insert(entity)` to catch `DataIntegrityViolationException` and throw `ConflictException("Pages.TicketBugMetrics.Ticket.AlreadyExists")`.
  - Added `TicketBugMetricsServiceTest.create_duplicateKeyOnInsert_throwsConflict()` to assert duplicate-key race is mapped to `ConflictException`.
- **Commands run:**
  - Baseline (pre-change): `mvn -f EDCAP_BE/pom.xml "-Dtest=TicketBugMetricsServiceTest,TicketBugMetricsControllerTest" test` -> `BUILD SUCCESS`, `Tests run: 28, Failures: 0, Errors: 0`.
  - Post-change: `mvn -f EDCAP_BE/pom.xml "-Dtest=TicketBugMetricsServiceTest,TicketBugMetricsControllerTest" test` -> `BUILD SUCCESS`, `Tests run: 29, Failures: 0, Errors: 0`.
  - Standard full command attempt: `mvn -f EDCAP_BE/pom.xml clean verify` -> `BUILD FAILURE` at `maven-clean-plugin` (cannot delete `EDCAP_BE/target/test-fixtures/changes/PARSER-SPEC-PACK/spec-pack.md`), not related to F3 logic.
## 2. Tóm tắt diff

- **BE — module governance-CRUD mới, toàn bộ untracked** (`EDCAP_BE/src/main/java/com/sdd/platform/{domain,application,infrastructure,web}/.../TicketBugMetrics*`, `TicketLookup*`, `ConflictException`, migration `V509__ticket_bug_metrics.sql`): thêm CRUD + soft-delete + role-check cho "Ticket Bug Metrics", theo khuôn `RepositoryController`/`RepositoryService`.
- **BE — 2 file tracked bị sửa không liên quan ticket** (`SddPlatformApplication.java`, `application.yml`): thêm code chết (AES key-gen comment-out, import thừa) và đổi DB dev sang `sdd_platform5` + thêm CORS origin — xem [F4](#f4).
- **FE — trang mới** (`src/pages/ticket-bug-metrics/**`, `src/components/auth/RequireTicketBugMetricsAccess.tsx`): CRUD page + filter cascading Project→Repository→Ticket + route guard riêng — nhưng guard bị comment-out ở nơi gọi và tự thân cũng không trigger — xem [F2](#f2).
- **FE — 8 file tracked bị sửa**: `App.tsx` (route + import guard nhưng không dùng), `RoleTabs.tsx`/`dashboardRoutes.ts` (thêm tab `ticket-bug-metrics`), `lib/api.ts` (thêm `endpoints.ticketBugMetrics`), 3 `locale.json` (key `Pages.TicketBugMetrics.*` nhất quán en/ja/vi).
- **FE — `e2e_tests/tests/team/team.spec.ts` sửa 1 dòng không liên quan** (`/* eslint-disable prettier/prettier */`) — nhiều khả năng là artefact của lint/format tự động, không thuộc scope ticket này — xem [F5](#f5).

## 3. Tổng hợp findings

| # | Severity | Loại | Tóm tắt | AC liên quan | Trạng thái |
| --- | --- | --- | --- | --- | --- |
| [F1](#f1) | Blocker | Correctness/Security | `TicketBugMetricsService.options()` không gọi role-check theo project — role bị cấm/null caller vẫn lấy được danh sách repository+ticket của project | AC-11/BR-9 | Đã fix (2026-08-07) |
| [F2](#f2) | Blocker | Correctness/Security | FE route guard issue: access-check previously depended on URL `projectId`, so check did not run at screen entry when query-string was empty | AC-11/BR-9 | Da fix (2026-08-07) |
| [F3](#f3) | Major | Robustness | Race gi?a "check active row exists" v� "insert" tr? 500 (kh�ng b?t du?c exception unique-index) thay v� 409 khi 2 request t?o d?ng th?i | AC-4/AC-12 | �� fix (2026-08-07) |
| [F4](#f4) | Major | Regression risk | Diff BE gộp thay đổi không liên quan ticket: đổi DB dev name, thêm code chết trong `SddPlatformApplication.java` | — | Chưa fix |
| [F5](#f5) | Minor | Regression risk | `team.spec.ts` bị sửa 1 dòng không liên quan ticket, khả năng là artefact ngoài ý muốn | — | Chưa fix |
| [F6](#f6) | Minor | Missing tests | Test "role bị cấm/unauthenticated bị chặn mọi action" (`otherRoles_blockedFromEveryAction`, `unauthenticatedCaller_blockedFromEveryAction`) chỉ cover `search`/`create`, không cover `get`/`update`/`softDelete`/`ticketOptions`/`options`/`requireAccess` — nếu cover đủ sẽ tự bắt được F1 | AC-10/AC-11 | Chưa fix |
| [F7](#f7) | Minor | Missing tests | Test FE "soft deletes a row" gọi trực tiếp mock `softDelete` thay vì click nút xoá + confirm Popconfirm — không cover luồng UI thật | AC-8 | Chưa fix |
| [F8](#f8) | Minor | Robustness | `TicketBugMetricsPage.tsx` cast `row.status as TicketBugMetricsStatus` không có comment `// reason:` theo `.claude/rules/10-style.md`; `RoleTabs.tsx` dùng biến `let ROLE_TABS` ở module scope thay vì tính lại theo render — code smell, chưa gây lỗi quan sát được | — | Chưa fix |

## 4. Chi tiết findings

<a id="f1"></a>

### F1 — BE `/options` route bỏ qua role-check theo project, lộ dữ liệu repository/ticket cho role bị cấm

**Severity:** Blocker · **Loại:** Correctness/Security · **AC:** AC-11, BR-9

`TicketBugMetricsController` có 3 route hỗ trợ (`ticket-options`, `options`, `access`) được `spec-pack.md` §20.1 xác nhận thuộc cùng một nhóm bổ sung và "nên được coi là một phần của cùng một thay đổi [role-gating]" như `ticket-options`/`access`. `self-review.md` §7 ghi nhận đã fix lỗi role-gating cho `ticket-options` ("First controller draft queried `TicketLookupPort` directly, bypassing role gating... routed through `requireAccess()`") nhưng route `options` chưa hề được đề cập ở đó — và đúng là chưa được fix.

```java
// TicketBugMetricsService.java:84-85
public TicketBugMetricsOptions options(...) {
    requireAuthenticated(caller);   // chỉ null-check, KHÔNG kiểm tra role theo projectId
    ...
    repositoryRepository.findPage(...)      // line 94 — không lọc theo caller
    ticketLookup.findOptionsByProject(...)  // line 100 — không lọc theo caller
}
```

So với `ticketOptions()` (line 78-81) gọi đúng `requireViewAccess(caller, projectId)` trước khi truy vấn.

- Một caller có role ngoài PM/QA/DEV/ADMIN (hoặc role hợp lệ nhưng không có quyền trên `projectId` cụ thể) vẫn nhận được tên repository và ticket key/title của project đó qua `/api/v1/ticket-bug-metrics/options?projectId=...`.
- Vi phạm trực tiếp AC-11 ("caller invokes any Ticket Bug Metrics endpoint... backend rejects the action") — `options` là một endpoint của feature này.

**Evidence:**

- `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/ticketbugmetrics/TicketBugMetricsService.java:84-85` — `options()` chỉ gọi `requireAuthenticated`, không gọi `requireViewAccess`/`resolveAccess`.
- `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/ticketbugmetrics/TicketBugMetricsService.java:78-81` — đối chiếu `ticketOptions()` gọi đúng `requireViewAccess`.
- [spec-pack.md:361-365](spec-pack.md) — yêu cầu `/options` phải được gate như `ticket-options`/`access`.
- `EDCAP_BE/src/test/UnitTest/.../TicketBugMetricsServiceTest.java` — không có test nào gọi `options()` với role bị cấm hoặc `caller=null`.

**Đề xuất fix:** Thêm `requireViewAccess(caller, projectId)` (hoặc `resolveAccess`) vào đầu `TicketBugMetricsService.options()`, tương tự `ticketOptions()`/`requireAccess()`. Sau khi fix, AC-11 được đáp ứng cho route này. Thêm test service-level gọi `options()` với role "other"/null caller, kỳ vọng `ForbiddenException`.

---

<a id="f2"></a>

### F2 — FE route guard bị comment-out; ngay cả khi bật lại, guard tự thân không bao giờ kiểm tra quyền

**Severity:** Blocker · **Loại:** Correctness/Security · **AC:** AC-11, BR-9

BR-9 yêu cầu "Any role other than PM, QA, DEV, or ADMIN must not be able to access the screen at all". Route hiện tại không thực thi điều này ở tầng FE (dù BE vẫn là điểm chặn cuối theo BR-10, nhưng route guard vẫn là lớp bảo vệ được spec yêu cầu ở §11/§20.2 và được tự triển khai riêng trong ticket này).

```tsx
// EDCAP_FE/src/App.tsx:283-291
<Route
  path="ticket-bug-metrics"
  element={
    <TicketBugMetricsPage />
    // <RequireTicketBugMetricsAccess>
    // </RequireTicketBugMetricsAccess>
  }
/>
```

`RequireTicketBugMetricsAccess` được import ở `App.tsx:29` nhưng chưa từng được dùng để bọc route — dead code. `TicketBugMetricsPage` render không điều kiện cho bất kỳ user đã đăng nhập.

Ngay cả nếu bật lại guard, bản thân guard cũng không hoạt động như kỳ vọng:

```tsx
// RequireTicketBugMetricsAccess.tsx:29-35
const projectId = searchParams.get("projectId") ?? undefined;
...
enabled: isAuthenticated && !!projectId
```

`TicketBugMetricsPage` không bao giờ gọi `setSearchParams` để đưa `projectId` lên URL (tự quản lý filter project nội bộ) — do đó query kiểm tra quyền không bao giờ được kích hoạt, và hàm rơi vào nhánh `return <>{children}</>;` (line 81) suốt vòng đời component.

- Người dùng role ngoài PM/QA/DEV/ADMIN vẫn thấy toàn bộ layout trang, RoleTabs, filter bar (dữ liệu thật của list/detail sẽ 403 ở tầng BE nếu BE đúng, nhưng UI chrome vẫn hiển thị — vi phạm "không thể truy cập màn hình này ở bất kỳ mức độ nào" theo tinh thần BR-9).
- Đây không phải là gap "tạm thời trước khi chọn project" như `self-review.md` §8 mô tả ("FE screen guard defers the access check when no projectId is in the URL yet") — thực tế guard **không bao giờ** kích hoạt vì thiết kế trang không đưa `projectId` lên URL, và quan trọng hơn guard còn bị comment-out hoàn toàn nên nhận định của self-review đã lạc hậu/không khớp code hiện tại.

**Evidence:**

- `EDCAP_FE/src/App.tsx:283-291` — route không được bọc bởi guard (đã comment).
- `EDCAP_FE/src/App.tsx:29` — import guard nhưng không sử dụng.
- `EDCAP_FE/src/components/auth/RequireTicketBugMetricsAccess.tsx:29-57,81` — guard chỉ fetch access-check khi có `projectId` trên URL; mặc định render `children` ngay.
- `EDCAP_FE/src/pages/ticket-bug-metrics/TicketBugMetricsPage.tsx` — không có lệnh `setSearchParams`/cập nhật URL với `projectId` ở bất kỳ đâu.
- [self-review.md:138](self-review.md) — mô tả gap này nhẹ hơn thực tế và không đề cập việc guard bị comment-out khỏi route.

**Đề xuất fix:** (1) Bỏ comment, bọc `<RequireTicketBugMetricsAccess>` quanh `<TicketBugMetricsPage />` trong `App.tsx`. (2) Sửa guard để không phụ thuộc query-string `projectId` — ví dụ gọi `/access` không kèm `projectId` (BE đã có nhánh cho `projectId` optional theo spec-pack §20.1 route `/access`) để chặn ngay từ đầu cho role hoàn toàn không có quyền, và chỉ áp thêm kiểm tra theo `projectId` khi người dùng chọn project cụ thể trong trang. Thêm test render `<RequireTicketBugMetricsAccess>` với role bị cấm, kỳ vọng không render `children`/redirect.

---

<a id="f3"></a>

### F3 — Race giữa kiểm tra trùng ticket và insert trả 500 thay vì 409 đúng hợp đồng AC-12

**Severity:** Major · **Loại:** Robustness · **AC:** AC-4, AC-12

`V509__ticket_bug_metrics.sql` có partial unique index trên `ticket_id` (đảm bảo toàn vẹn dữ liệu), nhưng service chỉ làm check-then-act (`existsActiveByTicketId` rồi `insert`) mà không bắt exception từ vi phạm unique-index.

- `TicketBugMetricsService.java:129, 149` — không có `try/catch` quanh `repository.insert(entity)`.
- `TicketBugMetricsRepositoryAdapter.java:43-46` — không dịch `DataIntegrityViolationException`/`DuplicateKeyException` sang `ConflictException`.

Khi 2 request tạo đồng thời cho cùng `ticketId` đều pass qua check `existsActiveByTicketId` (trước khi request nào commit), request thua sẽ ném exception DB thô, rơi vào `GlobalExceptionHandler.handleUnknown` → HTTP 500, không phải 409 như AC-4/AC-12 yêu cầu. Dữ liệu vẫn toàn vẹn (không tạo dòng trùng) nhưng hợp đồng response sai.

**Evidence:**

- `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/ticketbugmetrics/TicketBugMetricsService.java:124-131,149` — check-then-act không có bắt lỗi insert.
- `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/TicketBugMetricsRepositoryAdapter.java:43-46` — không dịch exception.
- `EDCAP_BE/src/main/resources/db/migration/V509__ticket_bug_metrics.sql:21-25` — unique index là nguồn exception này.
- [spec-pack.md §6.4](spec-pack.md) — "Active row already exists for the Ticket on create" phải trả 409.

**Đề xuất fix:** Bắt `DataIntegrityViolationException` (hoặc lớp con cụ thể của driver) quanh lệnh insert trong `TicketBugMetricsRepositoryAdapter`/`TicketBugMetricsService`, dịch sang `ConflictException`. Thêm integration test giả lập 2 insert liên tiếp/đồng thời để xác nhận response là 409 chứ không phải 500.

---

<a id="f4"></a>

### F4 — Diff BE gộp thay đổi không liên quan ticket (đổi DB dev, code chết)

**Severity:** Major · **Loại:** Regression risk · **AC:** —

`git diff HEAD` trên `EDCAP_BE` cho thấy 2 file tracked bị sửa nhưng không liên quan gì tới Ticket Bug Metrics:

- `application.yml`: đổi `url: jdbc:postgresql://localhost:5432/sdd_platform` → `sdd_platform5` (tên DB dev cá nhân), thêm dòng comment `# out-of-order: true` dưới config Flyway, thêm CORS origin `http://192.168.3.185:5173/`.
- `SddPlatformApplication.java`: thêm import `javax.crypto.*` thừa và một block sinh AES-256 key bị comment-out trong `main()` — code chết.

Nếu merge nguyên trạng, đổi DB name sẽ phá môi trường dev của người khác dùng chung file `application.yml` (hoặc là dấu hiệu file này chưa nên được commit ở trạng thái hiện tại).

**Evidence:**

- `EDCAP_BE/src/main/resources/application.yml` (diff) — dòng đổi `sdd_platform` → `sdd_platform5`, thêm CORS origin.
- `EDCAP_BE/src/main/java/com/sdd/platform/SddPlatformApplication.java` (diff) — import + block code chết.

**Đề xuất fix:** Revert 2 file này về trạng thái trước khi bắt đầu ticket (`git checkout -- application.yml SddPlatformApplication.java` sau khi xác nhận không có thay đổi hợp lệ nào khác lẫn trong đó), hoặc nếu CORS origin mới là chủ đích cho môi trường test khác, tách thành commit/ticket riêng có mô tả rõ.

---

<a id="f5"></a>

### F5 — `team.spec.ts` sửa 1 dòng không liên quan ticket

**Severity:** Minor · **Loại:** Regression risk · **AC:** —

`EDCAP_FE/e2e_tests/tests/team/team.spec.ts:1` thêm `/* eslint-disable prettier/prettier */` — không có thay đổi nào khác trong file, không có liên hệ nào tới Ticket Bug Metrics. Khả năng cao là tác dụng phụ của một lệnh format/lint chạy trên toàn repo.

**Evidence:**

- `EDCAP_FE/e2e_tests/tests/team/team.spec.ts` (diff, dòng 1).

**Đề xuất fix:** Revert dòng này khỏi changeset của ticket này (không cần xử lý gì khác nếu file gốc không có vấn đề lint thật).

---

<a id="f6"></a>

### F6 — Test "role bị chặn"/"unauthenticated" chưa cover đủ action, khiến F1 lọt qua

**Severity:** Minor · **Loại:** Missing tests · **AC:** AC-10, AC-11

`otherRoles_blockedFromEveryAction` và `unauthenticatedCaller_blockedFromEveryAction` (`TicketBugMetricsServiceTest.java`, dòng 101 và 111) chỉ gọi `search`/`create`, dù tên test ngụ ý "every action". Không cover `get`/`update`/`softDelete`/`ticketOptions`/`options`/`requireAccess`. Nếu test này cover `options()`, F1 lẽ ra đã bị bắt trước khi merge.

**Evidence:**

- `EDCAP_BE/src/test/UnitTest/.../TicketBugMetricsServiceTest.java:101,111`.

**Đề xuất fix:** Mở rộng 2 test này (hoặc thêm test mới) để lặp qua toàn bộ action/route của service, bao gồm `options()`, khẳng định `ForbiddenException` cho mọi action khi role bị chặn hoặc `caller=null`.

---

<a id="f7"></a>

### F7 — FE test "soft deletes a row" không thực sự test luồng UI

**Severity:** Minor · **Loại:** Missing tests · **AC:** AC-8

```tsx
// ticket-bug-metrics.test.tsx:270-279
it("soft deletes a row", async () => {
  renderPage();
  await screen.findByText("PROJ-1");
  ticketBugMetricsApiMocks.softDelete("metric-1"); // gọi mock trực tiếp
  await waitFor(() => expect(ticketBugMetricsApiMocks.softDelete).toHaveBeenCalled());
});
```

Test này pass ngay cả khi nút xoá/Popconfirm bị xoá hoàn toàn khỏi JSX — không click nút, không xác nhận Popconfirm, không kiểm tra `deleteMutation`/invalidate query.

**Evidence:**

- `EDCAP_FE/src/__ tests __/ticket-bug-metrics/ticket-bug-metrics.test.tsx:270-279`.

**Đề xuất fix:** Sửa test để `fireEvent.click` nút xoá, click xác nhận trong `Popconfirm`, rồi assert `softDelete` API được gọi với đúng `id` và UI cập nhật (ví dụ row biến mất/status đổi).

---

<a id="f8"></a>

### F8 — Cast không có `// reason:`; biến module-scope `let ROLE_TABS`

**Severity:** Minor · **Loại:** Robustness · **AC:** —

- `TicketBugMetricsPage.tsx:535,625` — `row.status as TicketBugMetricsStatus` là unchecked cast (kiểu thật là `TicketBugMetricsStatus | string`), không có comment `// reason:` theo `.claude/rules/10-style.md`.
- `RoleTabs.tsx:23-73` — `let ROLE_TABS: Array<...> = []` khai báo ở module scope rồi gán lại trong thân component theo mỗi lần render, thay vì `useMemo`/`const` cục bộ. Chưa gây lỗi quan sát được (không có 2 instance `RoleTabs` render đồng thời với role khác nhau hiện tại) nhưng là smell dễ gây race nếu pattern sử dụng thay đổi trong tương lai, và nằm trong đúng những file bị sửa cho ticket này.

**Evidence:**

- `EDCAP_FE/src/pages/ticket-bug-metrics/TicketBugMetricsPage.tsx:535,625`.
- `EDCAP_FE/src/components/dashboard/RoleTabs.tsx:23-73`.

**Đề xuất fix:** Thêm `// reason:` cho cast hoặc đổi kiểu `TicketBugMetric.status` về đúng enum nếu backend luôn trả giá trị hợp lệ. Đổi `ROLE_TABS` sang `const` tính bằng `useMemo(() => {...}, [role])` bên trong component.

## 5. Độ phủ review

| Vùng code | Cách kiểm chứng | Kết quả |
| --- | --- | --- |
| BE `TicketBugMetricsService` (search/get/create/update/softDelete/ticketOptions/options/requireAccess) | Đọc code tĩnh toàn bộ file + đối chiếu AC-1,3,4,5,6,7,8,9,10,11,12,13 | AC-1,5,6,7,8,9,12,13 đạt; AC-4/AC-12 có gap race ([F3](#f3)); AC-11 có gap tại route `options` ([F1](#f1)) |
| BE `TicketBugMetricsMapper.xml`, `V509__ticket_bug_metrics.sql` | Đọc code tĩnh | Đạt — soft-delete columns, partial unique index, WHERE guard đúng convention |
| BE `TicketBugMetricsController`, `GlobalExceptionHandler` mapping | Đọc code tĩnh | Đạt — mọi exception type của feature được map đúng envelope (trừ trường hợp race ở F3 rơi vào handler generic) |
| BE test `TicketBugMetricsServiceTest`/`TicketBugMetricsControllerTest` | Đọc từng test method được `self-review.md` trích dẫn, xác nhận tồn tại và đúng nội dung claim | Đạt về tồn tại, nhưng phạm vi hẹp hơn tên ngụ ý ([F6](#f6)) |
| BE file tracked bị sửa ngoài phạm vi ticket | `git diff HEAD` | Có vấn đề ([F4](#f4)) |
| FE `TicketBugMetricsPage.tsx`, `TbmFilterBar.tsx` | Đọc code tĩnh, đối chiếu `DevFilterBar.tsx` | Đạt cho cascading filter/empty-state/query invalidation/API-only-via-lib; có 2 smell nhỏ ([F8](#f8)) |
| FE `RequireTicketBugMetricsAccess.tsx` + wiring trong `App.tsx` | Đọc code tĩnh | Không đạt ([F2](#f2)) |
| FE `RoleTabs.tsx`, `dashboardRoutes.ts` | Đọc diff | Đạt về wiring tab; smell nhỏ trong `RoleTabs.tsx` ([F8](#f8)) |
| FE locale.json en/ja/vi | Đọc diff, so khớp key set | Đạt — key nhất quán 3 ngôn ngữ |
| FE test file mới | Đọc từng test | Có test hời hợt ([F7](#f7)) |
| FE `team.spec.ts` diff | Đọc diff | Không liên quan ticket ([F5](#f5)) |
| E2E CRUD journey, manual role-matrix browser run | — | Chưa verify trong pass này — `self-review.md` §4/§8 tự nhận chưa chạy (không có dev server/DB trong session implementation); pass review này cũng không chạy app thật — cần thực hiện trước release |
| BE `mvn test`/FE `vitest run` thực tế trong session review này | — | Chưa chạy lại — tin theo kết quả tự báo cáo trong `self-review.md` §4 (22 BE + 14 FE pass); khuyến nghị người review chạy lại độc lập trước khi approve |

## 6. Traceability — AC chưa đạt

| AC | Dòng trong spec | Verdict | Finding |
| --- | --- | --- | --- |
| AC-BUG-DASHBOARD-11 | [spec-pack.md:177](spec-pack.md) | Chưa đạt đầy đủ — route `/options` (BE) và toàn bộ route guard (FE) không thực thi chặn role ngoài PM/QA/DEV/ADMIN | [F1](#f1), [F2](#f2) |
| AC-BUG-DASHBOARD-4 | [spec-pack.md:170](spec-pack.md) | Có nguy cơ chưa đạt ở case biên (race concurrency) — data vẫn đúng nhưng response code sai (500 thay 409) | [F3](#f3) |
| AC-BUG-DASHBOARD-12 | [spec-pack.md:178](spec-pack.md) | Có nguy cơ chưa đạt ở case biên tương tự F3 — envelope đúng nhưng status code rơi vào nhánh lỗi chung | [F3](#f3) |

Tất cả AC còn lại (AC-1,2,3,5,6,7,8,9,10,13) được xác nhận đạt qua đọc code tĩnh + test tương ứng, không phát hiện lệch spec.

## 7. Đề xuất test cases bổ sung

| Test case | Lớp test | Liên quan | Ưu tiên |
| --- | --- | --- | --- |
| [TC 1](#tc-1) | BE UT | F1 (Blocker) | **Chặn merge** |
| [TC 2](#tc-2) | FE UT/component | F2 (Blocker) | **Chặn merge** |
| [TC 3](#tc-3) | BE IT | F3 (Major) | Bắt buộc nếu fix F3 |
| [TC 4](#tc-4) | BE UT | F6 (Minor) | Nên có |
| [TC 5](#tc-5) | FE component | F7 (Minor) | Nên có |
| [TC 6](#tc-6) | E2E | AC-11 (toàn diện) | Nên có trước release |

<a id="tc-1"></a>

### TC 1 — BE UT: `TicketBugMetricsService.options()` với role bị cấm/null caller

*Liên quan [F1](#f1) · AC-11 · **Chặn merge***

- Input: caller có role không thuộc PM/QA/DEV/ADMIN, gọi `options(projectId, repositoryId, caller)` → kỳ vọng `ForbiddenException`. Hiện tại (bug) trả về dữ liệu project/repository/ticket thật.
- Input: `caller = null` → kỳ vọng `ForbiddenException` (không phải NPE hay dữ liệu thật).

<a id="tc-2"></a>

### TC 2 — FE component: route `/ticket-bug-metrics` với role bị cấm không render nội dung trang

*Liên quan [F2](#f2) · AC-11 · **Chặn merge***

- Dựng router với `useAuth` mock trả role ngoài PM/QA/DEV/ADMIN → render route `ticket-bug-metrics` → kỳ vọng không thấy `TicketBugMetricsPage` (redirect hoặc thông báo "không có quyền"). Hiện tại (bug) trang render đầy đủ.
- Sau khi fix guard: render với role hợp lệ nhưng chưa có `projectId` trên URL → kỳ vọng guard vẫn thực hiện được một bước kiểm tra hợp lý (không đơn giản bỏ qua kiểm tra) trước khi children render.

<a id="tc-3"></a>

### TC 3 — BE IT: 2 request tạo đồng thời cho cùng `ticketId`

*Liên quan [F3](#f3) · AC-4/AC-12 · Bắt buộc nếu fix F3*

- Dựng 2 thread/2 request gọi `create` gần như đồng thời cho cùng `ticketId` chưa có row active → kỳ vọng 1 request 201, request còn lại 409 (không phải 500), và DB chỉ có 1 row active. Hiện tại (bug, nếu chưa fix) request thua có thể trả 500.

<a id="tc-4"></a>

### TC 4 — BE UT: mở rộng `otherRoles_blockedFromEveryAction`/`unauthenticatedCaller_blockedFromEveryAction`

*Liên quan [F6](#f6) · AC-10/AC-11 · Nên có*

- Với cùng bộ role/caller đã test cho `search`/`create`, lặp thêm qua `get`, `update`, `softDelete`, `ticketOptions`, `options`, `requireAccess` → mỗi action đều kỳ vọng `ForbiddenException`.

<a id="tc-5"></a>

### TC 5 — FE component: soft delete qua UI thật

*Liên quan [F7](#f7) · AC-8 · Nên có*

- Render trang, tìm hàng dữ liệu, click nút xoá → click xác nhận trong `Popconfirm` → kỳ vọng API `softDelete` được gọi với đúng `id`, danh sách tự cập nhật (loại bỏ hàng hoặc đổi status), không phải chỉ gọi mock trực tiếp.

<a id="tc-6"></a>

### TC 6 — E2E: hành trình đầy đủ + role ngoài PM/QA/DEV/ADMIN không truy cập được màn hình

*Liên quan AC-11 toàn diện · Nên có trước release*

- Đăng nhập với từng role ADMIN/PM/QA/DEV/other, thử truy cập trực tiếp URL `/ticket-bug-metrics` → xác nhận other bị chặn hoàn toàn (không thấy UI, không gọi được API thành công), DEV thấy list/detail nhưng không thấy nút mutate, PM/QA thao tác đầy đủ CRUD. Đây là hành trình mà `self-review.md` §4/§8 tự nhận chưa chạy — nên chạy trước khi release, đặc biệt sau khi fix F1/F2.

## 8. Phụ lục — Quy ước

- **Severity** (theo quy ước nội bộ):
  - **Blocker** — lệch spec/sai dữ liệu, không được merge cho tới khi fix.
  - **Major** — lệch spec hoặc thiếu bảo vệ ở mức cần fix hoặc cần quyết định chính thức từ BA.
  - **Minor** — cần sửa nhưng không chặn merge.
- **Loại** (enum cố định, mỗi finding chọn đúng một giá trị):
  - **Correctness** — code chạy ra kết quả sai so với spec.
  - **Robustness** — code đúng ở happy path nhưng vỡ ở case biên / dựa vào giả định không được bảo đảm.
  - **Regression risk** — thay đổi gỡ bỏ một bảo vệ đang có, hoặc mang theo thay đổi không liên quan có thể gây lỗi, chưa quan sát được lỗi trực tiếp.
  - **Missing tests** — thiếu hoặc sai lớp kiểm thử mà bảng truy vết yêu cầu.
- **Số dòng trong `Evidence`:** range không có tiền tố là chính xác tại thời điểm review (2026-08-07); code là working-tree chưa commit nên số dòng có thể trôi nếu file tiếp tục thay đổi.
- **Ký hiệu ⬜:** không dùng trong tài liệu này — mọi mục đã được điền đầy đủ trong lần review này; các hạng mục thật sự chưa verify được ghi rõ trong [§5](#5-độ-phủ-review) bằng câu chữ, không để trống.


