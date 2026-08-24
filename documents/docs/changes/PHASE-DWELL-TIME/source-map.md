# Source Map

**Ticket ID**: PHASE-DWELL-TIME
**Create date**: 2026-08-21
**Author**: Claude (Tech Lead prep)
**Update date**: 2026-08-21

## Target Area

Bổ sung field `phaseDwellTime` vào chi tiết ticket của PM Dashboard, tính từ
`create_date`/`update_date` tự khai báo trong header mỗi file `.md` thuộc 7
phase trong scope: `1, 3, 4, 5, 6, 7, 8` (phase `0-A, 0-B, 2, 9` ngoài phạm
vi). Vùng tác động: 1 bảng DB mới (lưu date đã parse), 1 service mới dùng
chung (đọc header qua `MarkdownParserCore`), domain hexagonal `pmdashboard`,
1 component FE (`PhaseCard`), 3 file i18n. Không có bảng hiện có bị
ALTER/DROP, không có endpoint/màn hình mới.

## Entry Points

| entry | path | note |
|---|---|---|
| REST endpoint (đã có, mở rộng response) | `GET /api/v1/pm/dashboard/tickets/{ticketId}/detail` — `PmDashboardController.detail(...)` (`web/rest/PmDashboardController.java:80-86`) | Không đổi route, không đổi permission gate |
| FE hiển thị | `PhaseCard` (`pages/pm-dashboard/components/TicketDetailDrawer.tsx:115-157`) | Nhận `detail: PmDashboardTicketDetail` qua props |
| FE fetch | `endpoints.pmDashboard.detail(ticketId)` (`lib/api.ts:1912-1915`) | Không cần sửa — generic type |
| Nguồn parse header | `MarkdownParserCore.parse(content, sourcePath)` (`domain/service/markdown/core/MarkdownParserCore.java:54-90`) | Trả `MarkdownDocument.headerMetadata()` — dùng trực tiếp cho service mới, độc lập với 4 parser chuyên biệt |

## Call Flow

| caller | callee | note |
|---|---|---|
| `PmDashboardController.detail` | `PmDashboardService.detail(ticketId, caller)` | Trả `DashboardTicketDetail`, `NotFoundException` nếu ticket không tồn tại |
| `PmDashboardService.detail` | `service.requirePm(caller, detail.row().projectId())` | Permission gate |
| `PmDashboardService.detail` | `PmDashboardRepositoryPort.findDetail(ticketId)` | Implement bởi `PmDashboardJdbcAdapter` |
| `PmDashboardJdbcAdapter.findDetail` | Subquery mới đọc bảng lưu `create_date`/`update_date`, group theo `phase_id` | Cần thêm — chưa tồn tại |
| (mới) Service dùng chung trích header | `MarkdownParserCore.parse(blobContent, sourcePath).headerMetadata()` | Chạy cho cả 7 file mục tiêu, ghi kết quả vào bảng mới riêng |
| (mới) Adapter method mới | `upsertArtifactDocumentDates(...)` (tên đề xuất) | Ghi vào bảng mới — không dùng `updateSnapshotParsedSummary` |
| `PmDashboardController.detail` | `PmDashboardDtos.PmDashboardTicketDetailDto.from(DashboardTicketDetail)` | Map model → DTO trả JSON |
| FE: `PhaseCard` | `t("Pages.PmDashboard.xxx", { defaultValue })` | Đúng pattern label hiện có |

## Data Flow

```
Mỗi file .md thuộc 7 phase (spec-pack.md, impl-plan.md, review-checklist.md,
  self-review.md, test-plan.md, test-results.md, report.md, blackbox-testcases.md)
        │  source.readBlob(...)  (đã có sẵn trong luồng scan)
        ▼
MarkdownParserCore.parse(content, sourcePath).headerMetadata()
        │  lấy "create_date", "update_date" (key đã normalize)
        ▼
Bảng mới (đề xuất tbl_fact_artifact_document_date):
  artifact_snapshot_id, document_create_at, document_update_at
        │  JOIN tbl_dim_artifact_type (phase_id) + tbl_dim_phase (phase_order)
        ▼
Dwell Time 1 file = update_date − create_date
Dwell Time 1 phase = Σ (Dwell Time mọi file thuộc phase, cộng dồn)
        │
        ▼
phaseDwellTime: List<{ phaseCode, phaseOrder, dwellTime: String|null }>
        │
        ▼
PmDashboardModels.DashboardTicketDetail (record, application layer)
        │  PmDashboardDtos.PmDashboardTicketDetailDto.from(...)
        ▼
JSON response  GET /api/v1/pm/dashboard/tickets/{ticketId}/detail
        │  api.get<PmDashboardTicketDetail>(...)
        ▼
FE type PmDashboardTicketDetail → props.detail.phaseDwellTime
        │
        ▼
PhaseCard (TicketDetailDrawer.tsx) → hiển thị giá trị hoặc "-"
        │
        ▼
public/locales/{en,ja,vi}/locale.json → tên field 3 ngôn ngữ
```

**Cột KHÔNG dùng cho Dwell Time**: `created_at`, `collected_at`,
`source_updated_at`, `source_created_at`, `parsed_summary` của
`tbl_fact_artifact_snapshot` — không phản ánh đúng thời điểm file xuất
hiện/hoàn thành, hoặc đã bị 4 parser khác dùng.

## Test Map

| test file | path | liên quan |
|---|---|---|
| `TicketDetailDrawer.test.tsx` | `EDCAP_FE/src/__ tests __/pm-dashboard/` | Pattern chuẩn FE test — mock `react-i18next`, `MemoryRouter` + `Routes/Route`, `QueryClientProvider`, dựng `detail` bằng `satisfies PmDashboardTicketDetail`. Phải mở rộng object `detail` giả lập khi thêm field `phaseDwellTime`. |
| `TicketPhaseEvaluatorServiceTest.java` | `EDCAP_BE/src/test/UnitTest/.../phase/` | Test cho service khác domain (current phase) — xác nhận không cần sửa. |
| (mới, cần tạo) test cho service trích header | Vị trí do `impl-plan.md` quyết định | Case: header đầy đủ giờ, header chỉ có ngày, header thiếu field, cả 7 loại file |
| (mới, cần tạo) test cộng dồn Dwell Time theo phase | Vị trí do `impl-plan.md` quyết định | Case: phase 1 file, phase ≥2 file, phase chưa đủ dữ liệu (`"-"`) |
| (mới, cần tạo) test FE hiển thị field | Mở rộng `TicketDetailDrawer.test.tsx` | Giá trị thật, giá trị `"-"`, label 3 ngôn ngữ |

## Phát hiện quan trọng (đã xác minh bằng code)

- `ArtifactScannerService.scanTicketDirectory` (280-355) tạo dòng
  `tbl_fact_artifact_snapshot` cho **cả 8 file mục tiêu ngay từ lần scan
  đầu tiên** của ticket, kể cả file chưa tồn tại (`exists_flag=false`).
  Cột `created_at` chỉ set 1 lần tại INSERT đầu, không đổi sau đó dù file
  xuất hiện muộn hơn (`updateSnapshot` không có `created_at` trong SET
  clause) → **không dùng được** làm mốc Entry.
- `MarkdownParserCore.extractHeaderMetadata(content)`
  (`domain/service/markdown/core/MarkdownParserCore.java:103-117`, regex
  `TOP_META_PATTERN` dòng 37) đã sẵn có cơ chế generic trích mọi dòng
  `**Key**: Value` ở đầu file thành `headerMetadata`, key normalize thành
  `create_date`/`update_date` (`normalizeMetadataKey`, dòng 298-300).
- **4 parser đã dùng `MarkdownParserCore`** (có `headerMetadata` sẵn):
  `SpecPackMarkdownParser` (phase `1`), `ReviewChecklistMarkdownParser`
  (phase `4`), `SelfReviewMarkdownParser` (phase `5`),
  `ReportMarkdownParser` (phase `8`).
- **3 service KHÔNG dùng `MarkdownParserCore`** (`application/usecase/docparse/`):
  `ImplPlanParseService` (phase `3`), `TestPlanParseService`,
  `TestResultsParseService` (phase `6`) — dùng kiến trúc parse khác.
- **`blackbox-testcases.md` (phase `7`) — hoàn toàn chưa có parser nào**
  (grep toàn bộ codebase xác nhận), dù template có đủ header
  `Create date`/`Update date`.
- **Quyết định đã chốt**: 1 service mới dùng chung gọi trực tiếp
  `MarkdownParserCore.parse(blobContent, sourcePath).headerMetadata()` cho
  cả 7 file — không phụ thuộc 4 parser chuyên biệt lẫn 3 service
  `docparse`, giải quyết đồng thời gap phase `3`/`6`/`7`.
- **Nơi lưu**: 1 bảng mới riêng (đề xuất `tbl_fact_artifact_document_date`)
  — không dùng `parsed_summary` (đã bị 4 parser khác ghi, dễ xung đột do
  `updateSnapshotParsedSummary` ghi đè toàn bộ cột, không merge).
- **Công thức đã chốt**: Dwell Time phase = cộng dồn `Σ(update−create)`
  của tất cả file trong phase (không phải khoảng bao trùm MIN/MAX).

## Unknown Source Areas

- **Chưa xác nhận trên Postgres thực tế** hành vi ghi `created_at` mô tả ở
  trên — phân tích dựa trên đọc code Java + DDL, chưa `EXPLAIN`/query thử.
- **Chưa đọc toàn bộ** `PmDashboardRepositoryPort` interface (chỉ xác nhận
  `findDetail` qua grep).
- **Chưa xác nhận** migration nào là bản cuối cùng thực sự áp dụng của
  `vw_artifact_inventory_current` (đã tìm thấy 3 lần định nghĩa: `V160`,
  `V161`, `V503`) — không dùng view này cho Dwell Time nên không blocking,
  chỉ lưu ý nếu sau này cần tham chiếu.
- **Chưa đọc nội dung** `ImplPlanParseService`/`TestPlanParseService`/
  `TestResultsParseService` — chỉ xác nhận qua grep là không dùng
  `MarkdownParserCore`; không cần đọc sâu vì service mới không phụ thuộc
  chúng, nhưng nên đọc nếu `impl-plan.md` muốn tái sử dụng blob content đã
  đọc sẵn trong luồng của các service này thay vì đọc lại.
- **Chưa grep toàn bộ package `util/`** của `EDCAP_BE` để xác nhận 100%
  không có sẵn helper format duration `hh:mm:ss` không giới hạn giờ.
- **Chưa đọc** `ja/vi locale.json` phần `Pages.PmDashboard` đầy đủ (chỉ xác
  nhận namespace tồn tại và ký tự tiếng Nhật không bị escape).
- **Chưa chốt tên/schema chính xác** của bảng mới lưu
  `create_date`/`update_date` — cần quyết định ở `impl-plan.md`.
