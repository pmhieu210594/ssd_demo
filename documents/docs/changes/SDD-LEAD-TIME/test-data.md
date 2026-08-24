# Test Data

**Ticket ID**: SDD-LEAD-TIME
**Create date**: 2026-08-21 15:30:00
**Author**: nvt_dung
**Update date**: 2026-08-21 15:30:00

## Data Policy

Test data mô phỏng nội dung header metadata của `spec-pack.md`/`report.md`; không dùng dữ liệu ticket production thật.

## Master Data

| name | value | purpose |
|---|---|---|
| N/A | N/A | Ticket không có master-data/code-value mapping (xem `context.md`) |

## User / Permission Data

| user | role | permission | purpose |
|---|---|---|---|
| N/A | N/A | N/A | Feature đọc-only, dùng session-based auth hiện có, không có role mới |

## Normal Data

| ID | data | purpose |
|---|---|---|
| ND-1 | `spec-pack.md` header: `**Create date**: 2026-08-21 09:30:00` | Xác nhận datetime đầy đủ được parse/persist đúng vào `started_at` |
| ND-2 | `report.md` header: `**Update date**: 2026-08-22 17:45:10` | Xác nhận datetime đầy đủ được parse/persist đúng vào `completed_at` |
| ND-3 | `spec-pack.md` header: `**Create date**: 2026-08-21` (bare date) | Xác nhận normalize thành `2026-08-21 00:00:00` |

## Error Data

| ID | data | expected error |
|---|---|---|
| ED-1 | `spec-pack.md` header: `**Create date**: TBD` | `started_at` = NULL, UI hiển thị `-`, scan không fail |
| ED-2 | `report.md` không tồn tại trong ticket folder | `completed_at` = NULL, UI hiển thị `-` cho updated time và duration |
| ED-3 | `spec-pack.md` header: `**Create date**:   ` (whitespace-only) | Treated như malformed → NULL/`-` |

## Boundary Data

| ID | item | value | expected |
|---|---|---|---|
| BD-1 | Độ dài giá trị date | `YYYY-MM-DD` (10 ký tự) | Parse thành công, normalize `+00:00:00` |
| BD-2 | Độ dài giá trị date | `YYYY-MM-DD HH:mm:ss` (19 ký tự) | Parse thành công, passthrough |
| BD-3 | Thứ tự 2 timestamp | `completed_at` < `started_at` (report.md `Update date` sớm hơn spec-pack.md `Create date`) | Duration hiển thị `-`, cả 2 timestamp vẫn hiển thị bình thường |
| BD-4 | Số file có mặt | 0 file / 1 file / 2 file | Chỉ cột tương ứng file có mặt được populate; còn lại NULL/`-` |

## Existing Data Compatibility

Ticket cũ (được scan trước khi ticket này triển khai) sẽ tự động populate `started_at`/`completed_at` ở lần scan tiếp theo theo cadence hiện có — không cần backfill riêng (H-SDD-LEAD-TIME-3).

## Data Setup Procedure

1. Tạo/chỉnh `docs/changes/<TEST_TICKET>/spec-pack.md` và `report.md` với header field tương ứng theo bảng dữ liệu ở trên.
2. Trigger Artifact Scanner scan cho ticket đó (qua flow hiện có).
3. Kiểm tra giá trị trong `tbl_dim_ticket.started_at`/`completed_at` và qua UI Ticket Detail drawer.

## Data Cleanup Procedure

1. Revert nội dung test file về trạng thái ban đầu (hoặc xóa ticket test nếu tạo riêng cho mục đích test).
2. Re-scan để đưa `started_at`/`completed_at` về trạng thái trước test nếu cần.

## Sensitive Data Handling

- Do not use original production data.
- Do not save PII/secrets to artifacts.
