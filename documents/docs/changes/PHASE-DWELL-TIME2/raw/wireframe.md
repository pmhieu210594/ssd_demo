# Wireframe — Phase Dwell Time

**Ticket ID**: PHASE-DWELL-TIME
**Vai trò**: Senior UX/UI Designer & Business Analyst
**Create date**: 2026-08-20
**Update date**: 2026-08-20

---

## 0. Phát hiện quan trọng khi đối chiếu với code thực tế

Khi đối chiếu yêu cầu với code thực tế của `PhaseCard`
(`TicketDetailDrawer.tsx:115-157`), phát hiện: **`PhaseCard` hiện tại
KHÔNG hiển thị danh sách nhiều Phase.** Code thực tế chỉ render **1 card
duy nhất** cho "phase hiện tại" của ticket
(`detail.row.phaseCode/phaseName/phaseDescription/phaseCreatedAt`), khác
với giả định ban đầu trong `01_raw-input.md`/`impl-plan.md` rằng đã có
sẵn 1 danh sách 7 phase trên UI. Đây là lý do phạm vi UI thực sự cần làm
lớn hơn mô tả ngắn gọn "thêm 1 dòng dưới Created at" trong raw-input —
cần thêm hẳn 1 Card mới (xem mục 2-3 dưới đây).

---

## 1. Bối cảnh UI

- Component: `TicketDetailDrawer.tsx` → `Drawer` (antd) mở từ phải màn
  hình khi user click 1 ticket trong bảng danh sách.
- Vị trí chèn: 1 `<Card>` mới, cùng cấp với `PhaseCard`/`OpenIssuesCard`
  và các card khác xếp dọc trong Drawer.
- Đối tượng xem: Manager, Support Lead, Operation Admin, Support Agent
  (đã có quyền xem ticket detail — không có role mới).
- Tính năng thuần đọc — không có control tương tác nào (không nút bấm,
  không filter, không sort).

---

## 2. As-Is — layout hiện tại của `PhaseCard`

```text
┌─ Drawer: Ticket Detail ───────────────────────────────────────────────┐
│  ...(các card khác phía trên: Ticket Meta, Score, Risk...)            │
│                                                                        │
│  ┌─ Card: Phase ──────────────────────────────────────────────────┐  │
│  │  Phase                                                          │  │
│  │  ────────────────────────────────────────────────────────────  │  │
│  │  Phase 5: Implementation           │  Created at: 2026-08-10    │  │
│  │  Description: ...mô tả phase 5...  │  15:32:10                  │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                        │
│  ...(các card khác phía dưới: Open Issues, ...)                       │
└────────────────────────────────────────────────────────────────────┘
```

- Chỉ có **1 phase duy nhất** hiển thị: phase hiện tại của ticket
  (`row.phaseCode/phaseName/phaseDescription/phaseCreatedAt`).
- Không có khái niệm "danh sách 7 phase" trên UI hôm nay.

---

## 3. To-Be — Phương án A (đã chọn): giữ nguyên Card hiện tại, thêm mới Card "Phase Dwell Time" dạng danh sách

Giữ nguyên 100% Card "Phase" hiện tại (không đổi, đúng AC-7 — không đụng
hiển thị "current phase"). Thêm 1 Card mới ngay bên dưới, hiển thị danh
sách 7 phase (`1,3,4,5,6,7,8`) theo đúng `phase_order`, mỗi phase chỉ có
đúng 1 dòng giá trị Dwell Time.

```text
┌─ Drawer: Ticket Detail ───────────────────────────────────────────────┐
│  ...(các card khác phía trên)                                         │
│                                                                        │
│  ┌─ Card: Phase (KHÔNG ĐỔI — as-is) ─────────────────────────────┐  │
│  │  Phase 5: Implementation           │  Created at: 2026-08-10    │  │
│  │  Description: ...mô tả phase 5...  │  15:32:10                  │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                        │
│  ┌─ Card: Phase Dwell Time (MỚI) ─────────────────────────────────┐  │
│  │  Phase Dwell Time                                               │  │
│  │  ──────────────────────────────────────────────────────────    │  │
│  │  Phase 1 · Requirement Analysis ............... 03:20:00        │  │
│  │  Phase 3 · Design ............................. 27:20:05        │  │
│  │  Phase 4 · Implementation Plan ................. 12:05:40        │  │
│  │  Phase 5 · Implementation ...................... "-"             │  │
│  │  Phase 6 · Testing ............................. "-"             │  │
│  │  Phase 7 · Review ............................... "-"             │  │
│  │  Phase 8 · Release ............................... "-"             │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                        │
│  ...(các card khác phía dưới: Open Issues, ...)                       │
└────────────────────────────────────────────────────────────────────┘
```

### Zoom 1 dòng phase

```text
Phase 3 · Design ................................... 27:20:05
└─ label: "Phase {phaseCode} · {phaseName}"     └─ value: dwellTime | "-"
```

---

## 4. To-Be — Phương án B (không chọn, giữ để đối chiếu)

Nếu không bổ sung `phaseName` vào DTO, danh sách hiển thị theo số thứ tự
phase, dùng label chung "Phase {phaseCode}" (không tên riêng):

```text
┌─ Card: Phase Dwell Time (MỚI) ─────────────────────────────────────┐
│  Phase Dwell Time                                                   │
│  ──────────────────────────────────────────────────────────────    │
│  Phase 1 ........................................... 03:20:00       │
│  Phase 3 ........................................... 27:20:05       │
│  Phase 4 ........................................... 12:05:40       │
│  Phase 5 ........................................... "-"             │
│  Phase 6 ........................................... "-"             │
│  Phase 7 ........................................... "-"             │
│  Phase 8 ........................................... "-"             │
└──────────────────────────────────────────────────────────────────┘
```

Không chọn vì trải nghiệm kém hơn (người dùng phải tự nhớ `phase_code`
tương ứng nghiệp vụ gì).

---

## 5. To-Be — Phương án C (không chọn)

Gắn trực tiếp Dwell Time vào bên trong Card "Phase" hiện tại, biến Card
"Phase" từ hiển thị 1 phase thành hiển thị 7 phase, xoá bố cục 2 cột
(`phaseCode/phaseName` | `Created at`) hiện tại.

Không chọn vì phá vỡ layout/props hiện có của Card "Phase" (rủi ro
regression cao hơn), và làm mất rõ ràng khái niệm "current phase" so với
"lịch sử dwell time của 7 phase" — 2 khái niệm này vốn đã không khớp
nhau về mặt dữ liệu (`spec-pack.md` §4).

---

## 6. Chi tiết trạng thái hiển thị theo từng dòng phase

| Trạng thái dữ liệu | Hiển thị | AC liên quan |
|---|---|---|
| Có ≥1 file thuộc phase N đủ cặp `create_date`/`update_date` | `hh:mm:ss` (hh không giới hạn 24, vd `27:20:05`) — tổng Σ per-file | AC-PHASE-DWELL-TIME-2 |
| Phase N không có file nào | `"-"` | AC-PHASE-DWELL-TIME-3 |
| Phase N không còn file nào đủ cặp `create_date`/`update_date` (file có `create_date` nhưng thiếu `update_date` không tính) | `"-"` | AC-PHASE-DWELL-TIME-4 |
| API lỗi/timeout khi lấy dữ liệu phase | `"-"` (không hiện toast lỗi, không spinner retry) | AC-PHASE-DWELL-TIME-10 |
| Giá trị Dwell Time = 0 giây tuyệt đối cho 1 file (`create_date` = `update_date`) | `00:00:00` (không nhầm thành `"-"`, vẫn cộng dồn vào tổng phase) | Boundary — xem `review-checklist.md` §2.4 |

**Không có** trạng thái Pending/InProgress/Completed nào được hiển thị.

### Zoom trạng thái lỗi/chưa có dữ liệu

```text
Phase 6 · Testing ................................... "-"
Phase 7 · Review ..................................... "-"
```

Không phân biệt trực quan giữa "chưa có dữ liệu" và "lỗi khi lấy dữ
liệu" — cả 2 case đều render `"-"` giống hệt nhau (thiết kế fail-silent,
không lộ trạng thái lỗi kỹ thuật ra UI).

---

## 7. Responsive

- **Desktop (≥ md)**: Card "Phase" hiện tại giữ nguyên layout 2 cột
  (`md:grid-cols-2`, không đổi). Card "Phase Dwell Time" mới xếp danh
  sách dọc 1 cột, mỗi dòng 1 phase.
- **Mobile/narrow (< md)**: giữ nguyên cách Card hiện tại tự động rơi về
  1 cột dọc; danh sách 7 phase mới cũng xếp dọc, không cần thay đổi gì
  thêm cho responsive.
- Không cần scroll ngang; nếu tên phase dài, áp dụng `break-words` giống
  pattern đã dùng trong `PhaseCard` hiện tại.

---

## 8. i18n — nhãn hiển thị

| Vị trí | Key đề xuất | EN | JA | VI |
|---|---|---|---|---|
| Tiêu đề Card mới | `Pages.PmDashboard.phaseDwellTime` | Phase Dwell Time | 各フェーズの滞留時間 | Thời gian kẹt ở từng phase |
| Giá trị khi không tính được | *(không cần key riêng)* | `-` | `-` | `-` |

- Không phát sinh key mới cho nhãn trạng thái.
- Tên phase (`phaseName`) lấy nguyên văn `tbl_dim_phase.phase_name`
  (tiếng Anh) — không có bản dịch riêng theo locale trong phạm vi ticket.

---

## 9. Bảng ánh xạ phần tử UI → nguồn dữ liệu → AC

| Phần tử UI | Nguồn dữ liệu | AC |
|---|---|---|
| Card "Phase" (as-is, không đổi) | `detail.row.phaseCode/phaseName/phaseDescription/phaseCreatedAt` | AC-PHASE-DWELL-TIME-7 (không đổi) |
| Card "Phase Dwell Time" — tiêu đề | i18n key `Pages.PmDashboard.phaseDwellTime` | AC-PHASE-DWELL-TIME-8 |
| Danh sách 7 dòng phase, đúng thứ tự | `detail.phaseDwellTime[].phaseOrder` (sort tăng dần) | AC-PHASE-DWELL-TIME-1 |
| Nhãn từng dòng | `phaseDwellTime[].phaseCode` + `phaseDwellTime[].phaseName` | AC-PHASE-DWELL-TIME-1 |
| Giá trị Dwell Time từng dòng | `phaseDwellTime[].dwellTime` → render `dwellTime ?? "-"` | AC-PHASE-DWELL-TIME-2/3/4/10 |
