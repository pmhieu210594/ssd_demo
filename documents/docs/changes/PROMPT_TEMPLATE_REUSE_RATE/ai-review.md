# AI Review — Ticket PROMPT_TEMPLATE_REUSE_RATE (Template reuse rate theo phase trên PM Dashboard)

- **Ticket:** PROMPT_TEMPLATE_REUSE_RATE
- **Trạng thái:** Draft
- **Tạo ngày:** 2026-08-18
- **Diff review:** không xác định được (repo không phải git repository — xem "Ghi chú quan trọng về input")
- **Branch:** không xác định được (không có git repo trong working directory)
- **Nguồn đối chiếu:** [docs/changes/PROMPT_TEMPLATE_REUSE_RATE/spec-pack.md](spec-pack.md)
- **Reviewer:** AI review — người cần điền tên và xác nhận lại
- **Cách kiểm chứng:** đọc code tĩnh trực tiếp trên HEAD hiện tại (Read/Grep từng file) + đối chiếu với `impl-plan.md`/`self-review.md`/`review-checklist.md` + đọc danh sách test đã có (`GithubWebhookServiceTest.java`). Chưa chạy `mvn test`/`npx vitest`/lint trong pass này (chỉ review tĩnh, không sửa/chạy build theo đúng phạm vi Block 1). Chưa chạy E2E, chưa chạy app.
- **Phạm vi:** chỉ ghi kết quả review, không sửa source code.

## Ghi chú quan trọng về input

- **Working directory không phải git repository.** Không thể chạy `git diff`/`git show`/`git log` để lấy diff thật hoặc xác nhận dòng đã xoá. Toàn bộ review này dựa trên **snapshot HEAD hiện tại** (đọc trực tiếp từng file nguồn) đối chiếu với `spec-pack.md`, `impl-plan.md`, `review-checklist.md`, `self-review.md` — không đối chiếu được với lịch sử commit hay ranh giới diff chính xác của PR. Đây là một **gap về độ phủ bằng chứng**, không phải giả định là không có thay đổi ngoài phạm vi.
- Vì không có diff, "Tóm tắt diff" ở mục 2 dưới đây được suy ra từ bảng "Expected Change File" trong `impl-plan.md` §4/§9 và xác nhận chéo bằng cách đọc từng file đó trên HEAD — không phải đọc trực tiếp diff.
- Ticket này đã qua **3 vòng review trước đó** (round 1: human, 4 finding đã fix; round 2: human, 2 Blocker đã fix; round 3: Codex automated, 3 finding — 1 chấp nhận rủi ro, 2 đã fix), đầy đủ bằng chứng trong `self-review.md` §7/§9/§11. Pass review này là vòng kiểm tra độc lập thứ 4, và theo đúng nguyên tắc "mọi finding phải có bằng chứng", các khẳng định trong `self-review.md`/`impl-plan.md` đã được **xác minh lại trực tiếp trên source hiện tại**, không nhận đúng theo lời tự khai.
- Kết quả xác minh lại: các fix của round 2 (literal phase filter, gate `closed && merged`) và round 3 (`isSkippableFetchError` áp dụng cho `resolveRevision`/`listTree`, try/catch quanh `recordIndependently(...)`) đều **đã có mặt đúng như self-review.md mô tả** trên HEAD hiện tại — không phát sinh finding mới ở các khu vực đó. Tuy nhiên, phát hiện được 1 điểm lệch spec/implementation (F1) và 1 lỗi tài liệu tự-review (F2) mà bản thân `self-review.md`/`impl-plan.md` không tự phát hiện ra.

## 2. Tóm tắt diff

1. **BE — migration:** thêm bảng `tbl_fact_template_usage_stat` (unique theo `project_id, repository_id, phase_id`, 2 CHECK constraint) + đăng ký 3 artifact-type mới (`OPEN_ISSUES`, `CONTEXT`, `CODEX_REVIEW`) trong `V510__add_template_usage_tracking.sql`.
2. **BE — webhook flow:** `GithubWebhookService` thêm `validateTemplateUsage(...)` chạy khi PR `closed && merged`, so khớp cấu trúc heading giữa file đã đổi và template chuẩn (`headerStructureMatches`), phân loại lỗi fetch skippable (401/403/404 → skip+warn) cho cả per-file blob fetch lẫn `resolveRevision`/`listTree` dùng chung cho cả PR, và ghi nhận kết quả qua `TemplateUsageStatWriter.recordIndependently(...)` (best-effort, bọc try/catch tại call site).
3. **BE — đọc dữ liệu:** `PmDashboardService.getTemplateUsage(...)` (gate `requirePm`) → `PmDashboardJdbcAdapter.findTemplateUsage(...)` (LEFT JOIN theo phase cố định `'1'..'8'`, không mất phase có 0 hoạt động) → `PmDashboardController` thêm `GET /api/v1/pm/dashboard/template-usage` → `PmDashboardDtos.TemplateUsageDto` (usageRate null khi `totalCheckCount == 0`, làm tròn 1 chữ số thập phân).
4. **FE:** thêm hàm gọi API và section "Template Usage by Phase" trên `PMDashboardPage.tsx` (render `TemplateUsageByPhase.tsx` ngay sau `AllTicketsTable`), dùng `useQuery` với key `["pm-dashboard", "template-usage", ...]`, `enabled` khi có cả `projectId` và `repositoryId`; cập nhật i18n `en`/`vi`/`ja`.
5. **Test:** bổ sung ~15 test case `template_usage_*` trong `GithubWebhookServiceTest.java` (match/mismatch/no-template/unmapped-phase/skippable & non-skippable error trên cả blob và tree fetch/counter-write-failure-continuation/action-gating), cùng test BE (`PmDashboardServiceTest`, `PmDashboardJdbcAdapterFindTemplateUsageTest`, `PmDashboardControllerTest` — theo `self-review.md` §3, chưa tự đọc lại toàn bộ nội dung các file test này trong pass review này) và test FE (`PMDashboardPage.test.tsx`, `TemplateUsageByPhase.test.tsx`).

## 3. Tổng hợp findings

| # | Severity | Loại | Tóm tắt | AC liên quan | Người duyệt | Trạng thái |
| --- | --------- | ----------------- | -------------------------------------------- | ------------ | ----------- | ---------- |
| F1 | Major | Correctness | [FE gọi `endpoints.pmDashboard.templateUsage(...)` thay vì namespace `templateUsage.statistics(...)` mà spec-pack.md §11 yêu cầu](#f1) | §11 FE/BE Contract Impact (liên quan AC-10) | Nhóm dev (ticket owner) | Đã xử lý — xem [Cập nhật xử lý F1](#f1-fix) |
| F2 | Minor | Readability/maintainability | [`self-review.md` khẳng định sai rằng `TemplateUsageByPhase.tsx` dùng component `CServerTable` — thực tế dùng `CDataTable`, `CServerTable` không tồn tại trong `pm-dashboard/`](#f2) | AC-9 (tài liệu, không phải code) | Nhóm dev (ticket owner) | Đã xử lý — xem [Cập nhật xử lý F2](#f2-fix) |

## 4. Chi tiết findings

<a id="f1"></a>

### F1 FE dùng sai namespace API so với quyết định đã chốt trong spec-pack.md §11

**Severity:** Major · **Loại:** Correctness · **AC:** §11 FE/BE Contract Impact (liên quan AC-10)

**Bối cảnh:** `spec-pack.md` chốt tường minh namespace FE mới:

```
docs/changes/PROMPT_TEMPLATE_REUSE_RATE/spec-pack.md:188
- New FE API namespace in `lib/api.ts`: `templateUsage.statistics({ projectId, repositoryId })`.

docs/changes/PROMPT_TEMPLATE_REUSE_RATE/spec-pack.md:244
...following the existing `TicketDetailDrawer.test.tsx` convention ..., `vi.spyOn(endpoints.templateUsage, "statistics")`...
```

`impl-plan.md` (§2, quyết định ID-3) và `self-review.md` (§2 bảng AC Matching dòng AC-10, §3 bảng Changed Files) đều khẳng định đã implement đúng namespace này:

```
docs/changes/PROMPT_TEMPLATE_REUSE_RATE/impl-plan.md — §9 step 6:
"Add `templateUsage.statistics(...)` + DTO interface to `api.ts` | `api.ts` | Matches `spec-pack.md` §11/§15 naming exactly"

docs/changes/PROMPT_TEMPLATE_REUSE_RATE/self-review.md:60
| `EDCAP_FE/src/lib/api.ts` | Added `endpoints.templateUsage.statistics(...)` namespace + response TS interface | AC-10 |

docs/changes/PROMPT_TEMPLATE_REUSE_RATE/self-review.md:35
| AC-10 | Implemented | ... `PMDashboardPage.test.tsx` asserts `endpoints.templateUsage.statistics` is called with `{ projectId, repositoryId }` |
```

**Thực tế trên HEAD:** `templateUsage` là một **method lồng bên trong namespace `pmDashboard`** đã có sẵn, không phải một namespace sibling mới `templateUsage.statistics`:

```ts
// EDCAP_FE/src/lib/api.ts:1741  (mở namespace pmDashboard)
pmDashboard: {
  ...
  // EDCAP_FE/src/lib/api.ts:1857-1863
  templateUsage: (params: { projectId: string; repositoryId: string }) => {
    const searchParams = new URLSearchParams();
    searchParams.set("projectId", params.projectId);
    searchParams.set("repositoryId", params.repositoryId);
    return api.get<TemplateUsageByPhase[]>(
      `/api/v1/pm/dashboard/template-usage?${searchParams.toString()}`,
    );
  },
  // EDCAP_FE/src/lib/api.ts:1867 (namespace kế tiếp = adminAuditLogs — xác nhận templateUsage không phải sibling)
```

Không tồn tại namespace `templateUsage` (viết hoa/thường bất kỳ) ở cấp top-level của object `endpoints` trong `api.ts`. Cách gọi thực tế trên toàn bộ codebase là `endpoints.pmDashboard.templateUsage(...)`:

```
EDCAP_FE/src/pages/pm-dashboard/PMDashboardPage.tsx:~106
queryFn: () => endpoints.pmDashboard.templateUsage({ projectId: filters.projectId, repositoryId: filters.repositoryId })

EDCAP_FE/src/__ tests __/pm-dashboard/PMDashboardPage.test.tsx:219
expect(endpoints.pmDashboard.templateUsage).toHaveBeenCalledWith({...})
```

**Hệ quả:**
- Code và test FE **nhất quán với nhau** (cả hai đều dùng `endpoints.pmDashboard.templateUsage`), nên đây không phải một bug runtime — feature hoạt động đúng chức năng.
- Nhưng nó là **lệch tường minh với quyết định đã chốt trong spec-pack.md §11** (không phải suy luận ngầm — dòng 188/244 nêu chính xác tên `templateUsage.statistics`), và **`impl-plan.md`/`self-review.md` tự khai sai** rằng đã tuân theo đúng namespace đó. Đây là vấn đề traceability: người đọc `self-review.md` để verify AC-10 sẽ tin nhầm rằng test đang assert đúng như spec, trong khi thực tế assert một API shape khác.
- Vì contract này chỉ dùng nội bộ FE (không phải public API cho bên ngoài), rely đổi tên không có breaking-change risk cho consumer khác — nhưng vẫn cần quyết định chính thức để tài liệu và code khớp nhau (xem mục 6).

**Đề xuất fix:** xem STEP=F1 (Block 2) — không tự ý chọn hướng giải quyết ở đây, vì đây là lệch giữa spec và implementation (task yêu cầu dừng lại và nêu rõ hướng nào là nguồn chân lý thay vì tự quyết).

<a id="f1-fix"></a>

**Cập nhật STEP=F1 (2026-08-18):** đã điều tra thêm để chuẩn bị cho quyết định, **không sửa code/spec ở bước này** — theo đúng yêu cầu "nếu spec và implementation lệch nhau thì dừng lại, nêu rõ lệch gì, và đề xuất nguồn nào nên là authoritative, không tự ý resolve".

- Xác nhận thêm: mọi endpoint khác cùng domain PM Dashboard trong `api.ts` (`summary`, `insights`, `tickets`, `options`, `export`) đều được khai báo **lồng bên trong** namespace `pmDashboard: { ... }` (mở tại `EDCAP_FE/src/lib/api.ts:1741`, đóng trước namespace kế tiếp `adminAuditLogs` tại dòng 1867). `templateUsage` được đặt cùng vị trí, cùng convention với các endpoint anh em của nó — namespace sibling `templateUsage.statistics` như spec-pack.md §11 đề xuất sẽ là **ngoại lệ duy nhất** phá vỡ convention nhóm-theo-domain đang có trong file này.
- Hai hướng resolve khả thi:
  1. **Sửa spec-pack.md §11/§244 để khớp code** (đổi `templateUsage.statistics({...})` → `pmDashboard.templateUsage({...})`, và câu ví dụ test `vi.spyOn(endpoints.templateUsage, "statistics")` → `vi.spyOn(endpoints.pmDashboard, "templateUsage")`). Rủi ro thấp nhất: không đổi code đã chạy qua 3 vòng review + test đã pass; đồng thời làm spec phản ánh đúng convention nhóm-theo-domain đã có sẵn trong `api.ts`.
  2. **Sửa code để khớp spec** (tạo namespace sibling `templateUsage.statistics` trong `api.ts`, cập nhật `PMDashboardPage.tsx` và `PMDashboardPage.test.tsx`). Rủi ro cao hơn: phá vỡ convention nhóm-theo-domain hiện có (đây sẽ là namespace duy nhất tách rời khỏi domain cha của nó dù chỉ phục vụ đúng 1 trang PM Dashboard), tốn công sửa lại 3 file + rerun test, không có lợi ích chức năng nào (contract này chỉ dùng nội bộ FE, không phải public API).
- **Đề xuất (không tự quyết):** nguồn authoritative nên là **implementation hiện tại** (hướng 1 — sửa spec-pack.md) vì lý do convention nêu trên, nhưng đây là quyết định thuộc về BA/tech lead của ticket (người đã chốt §11 ban đầu), không phải quyết định kỹ thuật thuần tuý — cần xác nhận chính thức trước khi đóng finding này.

**Quyết định chính thức (2026-08-18, ticket owner):** xác nhận hướng 1 — giữ nguyên code (`endpoints.pmDashboard.templateUsage`), vì nằm trong `pmDashboard` "hợp lý hơn" (nhất quán với các endpoint anh em cùng domain). `spec-pack.md` được cập nhật lại để khớp implementation, thay vì sửa code.

**Đã thay đổi (tài liệu, không đổi code):**

- [spec-pack.md](spec-pack.md) dòng 188 (§11 FE/BE Contract Impact): đổi mô tả từ "New FE API namespace... `templateUsage.statistics({ projectId, repositoryId })`" thành "New FE API method... nested inside the existing `pmDashboard` namespace... `pmDashboard.templateUsage({ projectId, repositoryId })`", có giải thích lý do convention (nhất quán với `summary`/`insights`/`tickets`/`options`/`export`).
- [spec-pack.md](spec-pack.md) dòng ~244 (Test Strategy): đổi `vi.spyOn(endpoints.templateUsage, "statistics")` thành `vi.spyOn(endpoints.pmDashboard, "templateUsage")`.
- [self-review.md](self-review.md): thay toàn bộ 4 chỗ trích dẫn sai `endpoints.templateUsage.statistics` (dòng 35, 60, 64, 139) thành `endpoints.pmDashboard.templateUsage`; dòng 60 (bảng Changed Files) diễn giải lại cho khớp cách đặt tên thật (method lồng trong namespace `pmDashboard`, không phải namespace riêng).

**Ảnh hưởng:**

- Không có thay đổi hành vi runtime — `api.ts`, `PMDashboardPage.tsx`, test FE đều giữ nguyên như trước khi review (đã đúng chức năng từ đầu, chỉ tài liệu spec/self-review sai).
- Không phát sinh nợ kỹ thuật mới: sau khi sửa, `spec-pack.md` phản ánh đúng convention nhóm-theo-domain hiện có trong `api.ts`, không còn ngoại lệ nào cần giải thích thêm cho người đọc sau này.
- Không cần chạy lại `mvn test`/`npx vitest`/lint vì không có thay đổi source code — chỉ thay đổi 2 file tài liệu (`spec-pack.md`, `self-review.md`).

**Trạng thái:** `Đã xử lý` (người review đã xác nhận) — quyết định chính thức đã có, tài liệu đã cập nhật khớp code.

---

<a id="f2"></a>

### F2 `self-review.md` khẳng định sai tên component UI dùng cho `TemplateUsageByPhase.tsx`

**Severity:** Minor · **Loại:** Readability/maintainability (documentation-traceability) · **AC:** AC-9 (chỉ ảnh hưởng tài liệu, không ảnh hưởng code)

**Bối cảnh:** `self-review.md` nhiều lần khẳng định `TemplateUsageByPhase.tsx` đã được "rewritten" để dùng chung component bảng `CServerTable`, giống `AllTicketsTable`:

```
docs/changes/PROMPT_TEMPLATE_REUSE_RATE/self-review.md:219
| 1 | `TemplateUsageByPhase` table did not share the common table style/component used by other tables on the PM Dashboard page | Major | Rewritten to use the shared `CServerTable` component (basic mode), matching `AllTicketsTable` |

docs/changes/PROMPT_TEMPLATE_REUSE_RATE/self-review.md:114
"...now also renders through the same shared `CServerTable` component as `AllTicketsTable`..."

docs/changes/PROMPT_TEMPLATE_REUSE_RATE/self-review.md:68
(mô tả test mới) "...no `QueryClientProvider`/`MemoryRouter` needed — `CServerTable` self-provides its own contexts..."
```

**Thực tế trên HEAD:** `TemplateUsageByPhase.tsx` import và dùng `CDataTable`, không phải `CServerTable`:

```tsx
// EDCAP_FE/src/pages/pm-dashboard/components/TemplateUsageByPhase.tsx:4
import { CDataTable } from "@/components/ui/data-table";
```

Không có bất kỳ tham chiếu nào tới `CServerTable` trong toàn bộ thư mục `pm-dashboard/` (grep không ra kết quả ngoài chính các dòng trong `self-review.md`) — component này dường như không tồn tại trong codebase hiện tại, hoặc self-review đang nhầm tên với component khác.

**Hệ quả:** không ảnh hưởng hành vi runtime hay bất kỳ AC nào (component `CDataTable` vẫn hiển thị đúng dữ liệu theo AC-9/AC-10). Đây thuần là lỗi đặt tên trong tài liệu tự-review, nhưng làm giảm độ tin cậy của `self-review.md` như một nguồn bằng chứng — người review sau sẽ tốn thời gian đi tìm một component không tồn tại.

**Đề xuất fix:** sửa `self-review.md` (các dòng 68, 114, 161, 219, 222, 225 — mọi chỗ nhắc `CServerTable` trong ngữ cảnh `TemplateUsageByPhase`) đổi thành `CDataTable`. Không nằm trong STEP=F1 (chỉ xử lý F1 theo yêu cầu), nhưng nên track như một finding riêng chờ pass fix kế tiếp hoặc gộp cùng lúc dọn tài liệu.

<a id="f2-fix"></a>

**Cập nhật xử lý F2 (2026-08-18, ticket owner):** xác nhận "để đơn giản hơn" giữ implementation là `CDataTable` (không đổi sang `CServerTable`); sửa lại tài liệu tự-review cho khớp thực tế thay vì sửa code.

**Đã thay đổi (tài liệu, không đổi code):**

- [self-review.md](self-review.md): thay toàn bộ các chỗ trích dẫn sai tên component `CServerTable` (dòng 62, 68, 114, 158, 161, 219, 222, 225) thành `CDataTable`, khớp với `TemplateUsageByPhase.tsx:4` (`import { CDataTable } from "@/components/ui/data-table";`).

**Ảnh hưởng:**

- Không có thay đổi hành vi runtime — component thực tế không đổi, chỉ sửa tên bị ghi sai trong tài liệu.
- `self-review.md` giờ có thể dùng làm nguồn bằng chứng đáng tin cậy cho tên component thật, không còn trỏ tới một component không tồn tại trong codebase.
- Không cần chạy lại lint/test vì không có thay đổi source code.

**Trạng thái:** `Đã xử lý` (người review đã xác nhận) — quyết định chính thức đã có, tài liệu đã cập nhật khớp code.

## 5. Độ phủ review

| Vùng code | Cách kiểm chứng | Kết quả |
| --------------------------------------------------------- | --------------------------------------------- | ------------------------------------------------------------------------------------------ |
| `V510__add_template_usage_tracking.sql` | Đọc code tĩnh toàn bộ file | Khớp spec §12 (additive-only, UNIQUE + 2 CHECK, upsert-friendly). Không có finding. |
| `GithubWebhookService.handlePullRequest`/`validateTemplateUsage` (gate closed+merged, skippable-error classification, best-effort counter write) | Đọc code tĩnh toàn bộ vùng liên quan, đối chiếu với self-review.md round 2/round 3 | Các fix round 2 (OI-14 gate) và round 3 (isSkippableFetchError cho resolveRevision/listTree; try/catch quanh recordIndependently) đều đã có mặt đúng như khai báo. Không có finding mới. |
| `headerStructureMatches` | Đọc code tĩnh | Đúng AC-4 (so sánh level + title theo thứ tự, bỏ qua nội dung body). Không có finding. |
| `TemplateUsageStatWriter` / `TemplateUsageStatJdbcAdapter` / `TemplateUsageStatPort` | Đọc code tĩnh toàn bộ 3 file | Upsert đúng theo spec (cộng dồn `total_check_count`/`template_match_count`, unique theo project+repo+phase). Không có finding. |
| `PmDashboardService.getTemplateUsage` / `requirePm` | Đọc code tĩnh toàn bộ file | Đúng AC-11 (ADMIN bypass, PM phải đúng projectId). Không có finding. |
| `PmDashboardJdbcAdapter.findTemplateUsage` | Đọc code tĩnh | Đúng AC-6/AC-7 (literal phase filter `'1'..'8'` trong WHERE trên `tbl_dim_phase`, scoping project/repository nằm trong `LEFT JOIN ... ON` nên phase không hoạt động vẫn xuất hiện với count=0, không rò dữ liệu project khác). Không có finding. |
| `PmDashboardController.templateUsage` / `PmDashboardDtos.TemplateUsageDto` | Đọc code tĩnh toàn bộ file | Đúng AC-8 (usageRate null khi total=0, làm tròn 1 chữ số) và convention controller mỏng. Không có finding. |
| `PMDashboardPage.tsx` (wiring `templateUsageQuery`) | Grep + đọc đoạn liên quan | Đúng AC-9/AC-10 về vị trí render, query-key convention, `enabled` gating — **ngoại trừ tên namespace API thực tế lệch với spec-pack.md §11** ([F1](#f1)). |
| `TemplateUsageByPhase.tsx` | Đọc toàn bộ 96 dòng | Render đúng cột, i18n có `defaultValue` (trừ 1 dòng empty-state không có `defaultValue`, key vẫn tồn tại ở cả 3 locale nên không phải lỗi chức năng — không tạo finding riêng vì không có tác động thực tế). Component thực tế là `CDataTable`, không phải `CServerTable` như self-review.md khai ([F2](#f2)). |
| `EDCAP_FE/src/lib/api.ts` — hàm `templateUsage` | Grep + đọc context xác nhận vị trí lồng trong namespace nào | Xác nhận `templateUsage` lồng trong `pmDashboard` (mở tại dòng 1741, namespace kế tiếp `adminAuditLogs` tại dòng 1867), không phải sibling namespace `templateUsage.statistics` như spec yêu cầu ([F1](#f1)). |
| `GithubWebhookServiceTest.java` | Grep danh sách `@Test` methods | ~15 test `template_usage_*` đã bao phủ match/mismatch/no-template/unmapped-phase/skippable & non-skippable error (cả blob và tree fetch)/counter-write-failure-continuation/action-gating/blank-sha. Độ phủ AC-1..AC-5 tốt; xem thêm đề xuất bổ sung ở mục 7. |
| `PmDashboardServiceTest.java`, `PmDashboardControllerTest.java`, `PmDashboardJdbcAdapterFindTemplateUsageTest.java`, `TemplateUsageStatWriterTest.java`, `TemplateUsageStatJdbcAdapterTest.java` | — chưa đọc trực tiếp trong pass này | Chưa verify — dựa trên khai báo trong `self-review.md` §3/§4 (test suite 514/514 pass), chưa tự đọc nội dung từng file test để xác nhận assertion cụ thể. Cần verify thêm nếu muốn nâng độ tin cậy. |
| `PMDashboardPage.test.tsx`, `TemplateUsageByPhase.test.tsx` | Grep 1 dòng (line 219) | Xác nhận nhất quán với code thực tế (`endpoints.pmDashboard.templateUsage`), nhưng đồng nghĩa test **không** assert đúng theo spec-pack.md §11/§244 ([F1](#f1)). Chưa đọc toàn bộ 2 file test này. |
| `review-checklist.md` | Đã đọc ở phiên trước (không re-đọc lại trong pass này) | Không phát hiện mâu thuẫn mới với các finding F1/F2. |
| Diff/git history thật (`git diff`, `git show`, `git log`) | — không khả dụng — working directory không phải git repository | Không kiểm chứng được ranh giới diff chính xác hay các thay đổi ngoài phạm vi tài liệu ghi nhận. Xem "Ghi chú quan trọng về input". |
| Chạy `mvn test` / `npx vitest` / lint thật trong pass review này | — không chạy — | Dựa trên log lệnh đã chạy và record trong `self-review.md` §4 (PASS), chưa tự chạy lại để xác nhận độc lập trong pass này (nằm ngoài phạm vi Block 1 — review-only, không build). |

## 6. Traceability — AC chưa đạt / lệch spec

- Không có AC nào trong AC-1 → AC-11 (`spec-pack.md` §7) bị đánh giá "Not Implemented" — toàn bộ 11 AC đều có bằng chứng implement đúng khi kiểm tra trực tiếp code.
- Tuy nhiên có **1 điểm lệch giữa quyết định đã chốt trong §11 (FE/BE Contract Impact) và code thực tế**: §11 dòng 188/244 chốt namespace FE `templateUsage.statistics(...)`; code thực tế và test dùng `endpoints.pmDashboard.templateUsage(...)`. AC-10 (`spec-pack.md` §7) tự thân không hard-code tên namespace này ("FE statistics section retrieves data... via `useQuery`... following the existing `["pm-dashboard", ...]` query-key convention") nên **AC-10 xét theo đúng văn bản của nó vẫn coi là đạt** (query-key convention, enabled-gating đều đúng) — nhưng quyết định cụ thể hơn ở §11 thì không được tuân theo, và tài liệu tự-review (`impl-plan.md`, `self-review.md`) khẳng định sai rằng nó được tuân theo. Đây chính là [F1](#f1).
- Không phát hiện thêm AC nào khác bị diễn giải sai hoặc bị bỏ sót trong phạm vi các file đã review ở mục 5.

## 7. Đề xuất test cases bổ sung

| Test case | Lớp test | Liên quan | Ưu tiên |
| --------------- | -------- | -------------- | ----------------------------------------- |
| [TC 1](#tc-1) | UT (FE, Vitest) | F1 | Bắt buộc nếu chọn hướng đổi code theo spec ở STEP=F1 |
| [TC 2](#tc-2) | UT (BE, JUnit) | AC-4 (header structure match) — không phải bug, chỉ là gap phủ test | Nên có |
| [TC 3](#tc-3) | UT (BE, JUnit) | AC-3/AC-6 (mapping file → phase khi 1 PR đổi nhiều file thuộc nhiều phase khác nhau cùng lúc) | Nên có |

<a id="tc-1"></a>

### TC 1 UT (FE): assert đúng namespace API theo spec-pack.md §11

*Liên quan [F1](#f1) · §11 FE/BE Contract Impact · **Không cần thiết sau quyết định 2026-08-18***

**Đã đóng, không cần thực hiện:** quyết định chính thức (2026-08-18) là giữ nguyên code (`endpoints.pmDashboard.templateUsage`) và sửa `spec-pack.md` cho khớp — xem [Cập nhật xử lý F1](#f1-fix). `PMDashboardPage.test.tsx` hiện tại (`vi.spyOn`/assert trên `endpoints.pmDashboard.templateUsage`) đã đúng theo spec đã cập nhật, không cần đổi test.

<a id="tc-2"></a>

### TC 2 UT (BE): `headerStructureMatches` — đổi thứ tự heading nhưng giữ nguyên tập title

*Liên quan AC-4 · Nên có*

Input: file đã đổi có các heading `["# A", "## B", "## C"]`, template chuẩn có `["# A", "## C", "## B"]` (cùng tập title/level nhưng đảo thứ tự 2 heading con). Kỳ vọng: `headerStructureMatches` trả về `false` (không match) vì so sánh là positional theo thứ tự, không phải theo tập hợp — hiện chưa thấy test nào trong `GithubWebhookServiceTest.java` phủ riêng trường hợp "cùng tập heading nhưng khác thứ tự" (các test hiện có tập trung vào match/mismatch tổng thể và no-template/unmapped-phase, chưa thấy tên test nào nêu rõ "reorder"/"same-set-different-order").

<a id="tc-3"></a>

### TC 3 UT (BE): 1 PR merged đổi nhiều file thuộc nhiều phase khác nhau

*Liên quan AC-3/AC-6 · Nên có*

Input: 1 PR `closed && merged` đổi đồng thời `open-issues.md` (phase 1), `context.md` (phase 2), `codex-review.md` (phase 5), trong đó 1 file khớp template và 2 file không khớp. Kỳ vọng: `recordIndependently(...)` được gọi đúng 3 lần với `phaseId` tương ứng từng file, `matched=true/false` đúng theo từng file độc lập (không bị lẫn giữa các phase trong cùng 1 lần webhook). Việc này giúp phát hiện sớm nếu có lỗi state bị chia sẻ nhầm giữa các vòng lặp file trong `validateTemplateUsage`.

## 8. Số liệu thống kê

Số liệu dưới đây phản ánh **kết quả của pass AI review này (vòng độc lập thứ 4)**, cộng dồn với 3 vòng review trước đã ghi trong `self-review.md` §7/§9/§11 (round 1: 4 finding, đã fix; round 2: 2 Blocker, đã fix; round 3 Codex: 3 finding — 1 accepted risk, 2 đã fix).

| Chỉ số | Giá trị | Ghi chú |
| --- | --- | --- |
| Tổng số finding (pass AI review này) | 2 (F1 Major, F2 Minor) | Không tính lại các finding của 3 round trước (đã đóng, có bằng chứng riêng trong self-review.md) |
| Tổng số finding đã fix (pass này) | 2 / 2 | F1: quyết định giữ code, sửa `spec-pack.md`/`self-review.md`. F2: quyết định giữ code, sửa `self-review.md`. Cả 2 đều là fix tài liệu, không đổi source code. |
| Tỷ lệ Blocker được xử lý | không áp dụng | Không có finding Blocker trong pass này |
| Tỷ lệ AI finding được con người chấp nhận | 2 / 2 (100%) | Ticket owner xác nhận cả F1 và F2 là đúng (2026-08-18) và ra quyết định chính thức cho từng finding |
| Tỷ lệ review finding hữu ích | 2 / 2 (100%) | Cả 2 finding đều dẫn tới quyết định/sửa tài liệu cụ thể, không bị bỏ qua |
| Tỷ lệ finding bị đánh giá là false positive | 0 / 2 (0%) | Cả 2 finding đều có bằng chứng file/line xác minh trực tiếp trên HEAD hiện tại và được ticket owner xác nhận là đúng |
| Tỷ lệ đã được xử lý (fix hoặc quyết định chính thức) | 2 / 2 (100%) | Xem [Cập nhật xử lý F1](#f1-fix) và [Cập nhật xử lý F2](#f2-fix) |

## 9. Phụ lục — Quy ước

- **Severity** (theo quy ước nội bộ):
  - **Blocker** — lệch spec/sai dữ liệu, không được merge cho tới khi fix.
  - **Major** — lệch spec hoặc thiếu bảo vệ ở mức cần fix hoặc cần quyết định chính thức từ BA.
  - **Minor** — cần sửa nhưng không chặn merge (bao gồm mọi vấn đề thuần comment/tài liệu).
- **Loại:** Correctness / Robustness / Regression risk / Security / Performance / Readability-maintainability / Missing tests.
- **Trạng thái:** `Chưa xử lý` / `Đã fix (chờ review)` / `Đã xử lý` (người review đã xác nhận) / `Không fix` (có quyết định chính thức chấp nhận rủi ro).
- **Không có range số dòng ở một số trích dẫn:** khi trích từ `self-review.md`/`impl-plan.md` (tài liệu, không phải diff), số dòng là số dòng thật trong file đó tại thời điểm đọc (2026-08-18), không phải số dòng trong một diff.
- **Ký hiệu:** phần nào ghi "chưa có dữ liệu" nghĩa là cần người review điền/xác nhận sau khi có phản hồi con người — không tự suy diễn số liệu.
