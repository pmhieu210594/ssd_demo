# phase9-update

**Ticket ID**: AI-REVIEW-KPI-IMPROVEMENT
**Create date**: 2026-08-20
**Author**: Continuous Improvement Officer (Claude)
**Update date**: 2026-08-20

> Đọc: `report.md`, `codex-review.md`, `test-results.md` (thay cho `human-review.md` — file này không
> tồn tại trong ticket này), `self-review.md`, `open-issues.md`, `spec-pack.md`,
> `docs/maintenance/failure-mode-index.md`, `docs/architecture/*`, `docs/standards/*`,
> `.claude/rules/*`, `documents/docs/AGENTS.md`, `.claude/settings.json`.

## Living Docs đã cập nhật

1. **`spec-pack.md`** (chính ticket này) — đưa 5 thay đổi/decision đã xác nhận trong quá trình impl
   quay lại spec gốc, để spec-pack phản ánh đúng implementation thật thay vì bản nháp ban đầu:
   - AC-AIRKI-11 (§6) và §9 (FE): `CDataTable` → card-grid kiểu `SummaryCards`, đúng tên file thật
     `AiFindingStatsCard.tsx` (theo `OI-AIRKI-14`, 2026-08-20).
   - §9: shape lỗi API sửa lại đúng 5-field thật của `ErrorResponse` (bỏ `errorCode` không tồn tại).
   - §10: hành vi thiếu `projectId`/`repositoryId` sửa từ "400 tự động" (giả định sai) thành "500
     `INTERNAL_ERROR`" đúng hành vi thật đã xác nhận (`OI-AIRKI-9`, 2026-08-19).
   - §16/§18: `A-AIRKI-3` không còn là "suy đoán AI chưa hỏi" — thêm `H-AIRKI-9` vào Decision Log
     (xác nhận `OI-AIRKI-10`, 2026-08-19) và `H-AIRKI-10` (xác nhận `OI-AIRKI-14`, 2026-08-20).
   - Lý do đưa vào spec-pack thay vì chỉ ghi trong report: đây là spec gốc của ticket — nếu không
     sửa, một người đọc sau này chỉ mở `spec-pack.md` (không đọc `open-issues.md`) sẽ tin nhầm vào
     `CDataTable`/400/"suy đoán chưa hỏi" đã lỗi thời.

2. **`docs/standards/security.md`, `docs/standards/api-contract.md`, `docs/standards/error-handling.md`,
   `.claude/rules/30-security.md`** — sửa trực tiếp (không chỉ đề xuất) shape `ErrorResponse` sai đã
   tồn tại từ trước ở cả 4 file: cả 4 đều mô tả 6 field (`error` + `errorCode` riêng biệt), trong khi
   `web/exception/ErrorResponse.java` (đọc trực tiếp, xác nhận lại lần nữa trong phiên này) chỉ có 5
   field `(timestamp, status, error, message, traceId)` — không có `errorCode`; giá trị thật ở field
   `error` chính là mã lỗi nội bộ (`"NOT_FOUND"`, `"VALIDATION_ERROR"`...), không phải cụm từ HTTP
   status. Đây là lỗi tài liệu có sẵn từ trước ticket này (đã được `open-issues.md` của
   AI-REVIEW-KPI-IMPROVEMENT ghi nhận nhưng cố ý không sửa trong phạm vi ticket, vì không liên quan
   trực tiếp), nay được sửa vì đây chính xác là loại "rule phổ quát" mà nhiệm vụ Continuous
   Improvement yêu cầu đưa vào chuẩn chung — không phải quyết định thiết kế cần hỏi người, mà là sự
   thật đã bị chép sai nhiều lần, sửa 1 lần tại nguồn.

## Failure Mode Index thêm/cập nhật

Thêm section mới **"AI-REVIEW-KPI-IMPROVEMENT-Derived Failure Modes"** vào
`docs/maintenance/failure-mode-index.md` với 4 entry mới:

- **`FMI-AIRKI-001`** — Tenant-scope filter chỉ nằm trong `ON` của `LEFT JOIN` (scope bảng fact),
  thiếu ở `WHERE` cho chính dòng dimension/anchor (Major #2 của `codex-review.md`). Đã ghi chú rõ
  đây là failure mode **liên quan nhưng khác** `FMI-PTR-004` (không phải trùng lặp — `FMI-PTR-004`
  là suy ra scope gián tiếp qua bảng proxy; `FMI-AIRKI-001` là scope biết rõ trực tiếp nhưng chỉ áp
  dụng cho 1 phía của `LEFT JOIN`).
- **`FMI-AIRKI-002`** — Suy ra giá trị mẫu số dùng chung cho nhiều KPI bằng `firstNonNull` thay vì
  yêu cầu cả nhóm đồng nhất, khiến "không áp dụng" bị ép thành `0.0` (Major #1 của `codex-review.md`).
- **`FMI-AIRKI-003`** — Tài liệu chuẩn khẳng định "Confirmed"/"must stay green" cho một cơ chế
  (test class ArchUnit) chỉ dựa trên dependency + comment tham chiếu, không phải file test thật tồn
  tại — xem chi tiết xác minh dứt điểm ở mục "Ứng viên nâng cấp Rules/Standards" bên dưới.
- **`FMI-AIRKI-004`** — Danh sách "vấn đề chưa xử lý" của một artifact review giai đoạn sớm
  (`self-review.md`) không được cập nhật sau khi giai đoạn sau (`test-results.md`, Bug Hunter) đã
  đóng đúng các mục đó, khiến người đọc `report.md` sau này tưởng có mâu thuẫn thật giữa 2 tài liệu.

Cũng thêm 1 dòng ghi chú vào `FMI-PTR-005` (không phải entry mới): ticket này **tránh được** vấn đề
redelivery-dedup của `FMI-PTR-005` bằng thiết kế (upsert theo `ticket_id`, không phải additive
counter) thay vì phải tự xây bảng delivery-id — xem `pattern-library.md`.

Không thêm entry mới cho `FMI-AAL-001` (UI mount) và `FMI-DEV-DASH-002` (PASS scope) vì ticket này
**tuân thủ đúng** 2 rule đó (AC-AIRKI-11 có test mount `PMDashboardPage.test.tsx`;
`test-results.md`/`blackbox-test-results.md` tách rõ scope automated vs black-box) — đây là ví dụ
tuân thủ tốt, không phải recurrence, nên không cần dòng "Note: recurrence of..." như 2 trường hợp
kia trong file.

## Pattern Library thêm/cập nhật

Tạo mới `docs/maintenance/pattern-library.md` (file này **chưa từng tồn tại** trong repo trước phiên
này — xác nhận qua Glob 2 lần, không tìm thấy) với 5 pattern rút ra được, mỗi pattern có link tới
failure mode liên quan và tới source file gốc:

1. **Positional/structural markdown table extraction** — `AiReviewStatsParser`/`MarkdownParserCore`
   (định vị theo thứ tự dòng/cột thật, không theo label text đã dịch).
2. **Shared-denominator KPI group, fail-closed nulling** — `resolveSharedFindingTotal` (yêu cầu cả
   nhóm mẫu số đồng nhất trước khi tin, thay vì `firstNonNull`).
3. **Snapshot-upsert idempotency** — `AiFindingStatWriter` (`ON CONFLICT (ticket_id) DO UPDATE`,
   né được vấn đề của `FMI-PTR-005` bằng thiết kế thay vì bảng delivery-id riêng).
4. **Best-effort write cô lập bằng writer bean `REQUIRES_NEW` riêng + try/catch tại call site** —
   tái dùng nguyên mẫu `TemplateUsageStatWriter`.
5. **Card-grid vs row-table, chọn theo cardinality của response** — `AiFindingStatsCard.tsx`
   (response 1 object → card-grid kiểu `SummaryCards`, không phải `CDataTable`).

## Ứng viên nâng cấp Rules/Standards

- **[Ứng viên — ưu tiên cao]** `ArchitectureTest`/`LayerEnforcementTest` (ArchUnit): xác minh dứt
  điểm trong phiên này bằng `Glob` cho cả 2 tên (`**/*ArchitectureTest*.java`,
  `**/*LayerEnforcementTest*.java`) trên toàn bộ `EDCAP_BE` — **không có file nào tồn tại**, dù
  `pom.xml` đã khai báo dependency `com.tngtech.archunit:archunit-junit5` và 4 file
  `package-info.java` (`domain`/`application`/`infrastructure`/`web`) đều ghi chú
  "Enforced by `LayerEnforcementTest` (ArchUnit)". 7 file tài liệu hiện khẳng định sai đây là
  "Confirmed"/"must stay green": `documents/.claude/CLAUDE.md`, `.claude/rules/20-architecture.md`,
  `.claude/rules/40-testing.md`, `docs/architecture/overview.md`, `docs/standards/testing.md`,
  `docs/standards/backend.md`, `docs/standards/review.md`. Đề xuất 1 trong 2 hướng (cần BE lead
  quyết định, không tự sửa vì đây là quyết định có ảnh hưởng rộng hơn 1 ticket):
  (a) tạo thật `LayerEnforcementTest.java` (rẻ vì dependency đã có sẵn), hoặc
  (b) sửa cả 7 file trên từ "Confirmed"/"must stay green" thành "Chưa xác minh tồn tại — xem
  `OI-AIRKI-11`" cho tới khi (a) được làm. Đã đăng ký làm `FMI-AIRKI-003`.
- **[Ứng viên]** `docs/standards/database.md` mục Candidate Rules đã có sẵn dòng
  "Testcontainers: integration tests use PostgreSQL 16 container to verify migrations" — ticket này
  (`OI-AIRKI-16`, accepted risk) là thêm 1 bằng chứng cụ thể cho nhu cầu này (constraint
  `UNIQUE(ticket_id)`/FK của `V512` (đổi tên từ `V511` ngày 2026-08-20 sau khi merge `main` va version
  với `V511__ai_quality.sql` của ticket khác) chỉ được test gián tiếp qua SQL string assertion, không chạy
  migration thật). Đề xuất nâng dòng này từ Candidate lên ưu tiên xem xét thật ở lần review kế
  hoạch kiểm thử kế tiếp, tham chiếu thêm `OI-AIRKI-16` làm ví dụ.
- **ErrorResponse shape** — đã sửa trực tiếp (xem "Living Docs đã cập nhật"), không còn là ứng viên.

## Ứng viên cập nhật AGENTS.md

`documents/docs/AGENTS.md` hiện có "Core Rules" chung cho mọi AI tool (Claude/Codex/Cursor/Copilot).
Đề xuất 2 dòng mới (khái quát hoá từ `FMI-AIRKI-003`/`FMI-AIRKI-004`, không sửa trực tiếp vì đây là
rule ngang ảnh hưởng mọi ticket, cần đồng thuận trước khi thêm):

- "Trước khi tin một khẳng định 'Confirmed'/'enforced' trong standards/architecture doc về một cơ
  chế cụ thể (test class, config file), xác minh trực tiếp bằng tìm kiếm trên source thật — dependency
  hoặc comment tham chiếu không phải bằng chứng cơ chế đó đã được implement."
- "Khi một artifact review/test giai đoạn sau đóng lại đúng mục mà artifact giai đoạn trước liệt kê
  là 'chưa xử lý', cập nhật lại artifact giai đoạn trước trong cùng lượt thay đổi (hoặc thêm con trỏ
  rõ ràng) — không để mâu thuẫn đó lại cho người đọc sau tự đối chiếu."

## Kiểm kê hooks/MCP/settings/rules

- **`.claude/settings.json`**: có cấu hình đầy đủ permission allow/deny/ask, và 7 nhóm hook
  (`UserPromptSubmit`, `PreToolUse`, `PostToolUse`, `PreCompact`, `Stop`, `SessionEnd`,
  `SessionStart`) cộng 1 `statusLine`, tổng cộng tham chiếu **10 script Python** dưới
  `.claude/hooks/*.py` và `.claude/statusline/brc_statusline.py`.
- **Phát hiện quan trọng**: `Glob(".claude/**")` xác nhận thư mục `.claude/` **chỉ chứa**
  `CLAUDE.md`, `settings.json`, và `rules/{00,10,20,30,40}-*.md` — **không có thư mục
  `.claude/hooks/` và không có `.claude/statusline/` nào tồn tại**. Toàn bộ 10 script được tham
  chiếu trong `settings.json` (bao gồm `pii_secret_redactor.py`, `pre_compact_guard.py`,
  `file_size_guard.py` — đều là safety-net quan trọng) hiện **không tồn tại trên đĩa**, nghĩa là các
  hook này hiện không thực sự chạy được (hoặc lỗi âm thầm) trong workspace này.
- **Không thuộc phạm vi sửa của ticket này** (không liên quan business logic AI-REVIEW-KPI-IMPROVEMENT)
  nhưng đủ nghiêm trọng để ghi nhận riêng — đây là gap về hạ tầng vận hành AI tool, không phải gap
  sản phẩm. Đề xuất người quản lý workspace xác nhận: có ý định tạo các script này sau, hay nên xoá
  cấu hình hook không có tác dụng khỏi `settings.json` để tránh nhầm lẫn có safety-net đang chạy?
- **MCP**: không tìm thấy `.mcp.json` ở project root — không có MCP server nào được khai báo ở cấp
  project cho ticket/workspace này.
- **Rules**: 4 file `.claude/rules/*.md` (00-safety, 10-style, 20-architecture, 30-security,
  40-testing — đã đọc đầy đủ). 1 trong số đó (`30-security.md`) có lỗi `ErrorResponse` đã sửa ở
  mục Living Docs.

## Những thứ không cập nhật và lý do

- **Không tự tạo `LayerEnforcementTest.java`** — đây là code test thật ảnh hưởng build, không phải
  tài liệu; cần BE lead quyết định hướng (a) hay (b) ở mục Rules/Standards trên, ngoài phạm vi vai
  trò Continuous Improvement (đọc/tổng hợp tài liệu) của nhiệm vụ này.
- **Không sửa 7 file "Confirmed" sai về ArchitectureTest** — cùng lý do: sửa cả 7 file cùng lúc mà
  chưa có quyết định (a)/(b) ở trên có thể tạo mâu thuẫn ngược (nếu BE lead sau đó chọn (a), lại
  phải sửa lại 7 file lần nữa). Chỉ đăng ký làm ứng viên chờ quyết định.
- **Không sửa `documents/docs/AGENTS.md` trực tiếp** — đây là rule ngang cho mọi AI tool, ảnh hưởng
  toàn bộ ticket tương lai chứ không riêng ticket này; nhiệm vụ yêu cầu "đưa làm ứng viên", không
  yêu cầu commit thẳng.
- **Không xoá/sửa cấu hình hook trong `settings.json`** — phát hiện thuộc phạm vi hạ tầng workspace,
  không phải phạm vi ticket AI-REVIEW-KPI-IMPROVEMENT; chỉ ghi nhận kiểm kê, để người quản lý
  workspace quyết định hướng xử lý.
- **Không đọc lại toàn bộ `docs/standards/{README,frontend,git-workflow,logging}.md` và các file
  còn lại của `docs/architecture/*`** trong phiên này — 8 file cốt lõi đã đọc
  (`failure-mode-index.md`, `AGENTS.md`, `testing.md`, `security.md`, `api-contract.md`,
  `error-handling.md`, `database.md`, `overview.md`) cộng `backend.md`/`review.md`/`maintenance.md`
  đã đủ để bao phủ toàn bộ finding thật của ticket này (KPI/DB/parser/security/test); các file chưa
  đọc (`frontend.md`, `git-workflow.md`, `logging.md`, các map/inventory còn lại của
  `docs/architecture/*`) không có liên hệ trực tiếp tới bất kỳ finding nào trong `report.md`/
  `codex-review.md`/`test-results.md` của ticket này.

## Lưu ý cho lần sau

- Khi 1 ticket phát hiện 1 tài liệu chuẩn (`docs/standards/*`, `.claude/rules/*`) mô tả sai lệch so
  với source thật, và sự sai lệch đó là **sự thật khách quan** (grep xác nhận được, không phải
  quyết định thiết kế cần hỏi người) — nên sửa thẳng tài liệu chuẩn đó trong cùng lượt Continuous
  Improvement, không chỉ ghi "known inconsistency, out of scope" rồi để nguyên qua nhiều ticket (như
  đã xảy ra với `ErrorResponse` — bị phát hiện từ trước, cố ý không sửa, tồn tại sai tiếp cho tới
  ticket này).
- Khi 1 tài liệu khẳng định "Confirmed" cho một cơ chế thực thi tự động (ArchUnit, CI gate, migration
  test) — cần phân biệt rõ "dependency/comment tồn tại" với "cơ chế đó thực sự chạy được"; nên có 1
  lượt kiểm kê định kỳ (không chỉ khi có ticket vô tình phát hiện) quét các khẳng định "Confirmed"/
  "enforced" trong `docs/standards/*` và xác minh lại bằng tìm kiếm trực tiếp trên source.
- `self-review.md` nên được xem là **snapshot đóng băng tại thời điểm viết**, không tự động cập nhật
  khi có phase sau (`test-results.md`, Bug Hunter) — nếu muốn nó luôn phản ánh trạng thái mới nhất,
  cần thêm 1 bước thủ tục "quay lại cập nhật self-review.md" vào cuối mỗi phase sau, thay vì để
  report.md phải tự đối chiếu và giải thích lại sự khác biệt (như đã phải làm ở phiên này).
- Việc `.claude/hooks/`/`.claude/statusline/` không tồn tại dù được tham chiếu đầy đủ trong
  `settings.json` nên được xác minh và xử lý sớm — nếu đây là oversight (quên copy thư mục khi setup
  workspace) thì mọi phiên làm việc từ trước tới nay đều đang chạy **không có** các safety-net
  (redact PII/secret, cost guard, file size guard) mà tài liệu vận hành ngỡ là đang bật.
