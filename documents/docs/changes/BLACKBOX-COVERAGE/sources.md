# Sources — BLACKBOX-COVERAGE

**Ticket ID**: BLACKBOX-COVERAGE  
**Create date**: 2026-09-04  
**Author**: AI-assisted  
**Update date**: 2026-09-04

## Core Sources

| Source | Path | Role |
| --- | --- | --- |
| Raw input | `docs/changes/BLACKBOX-COVERAGE/raw/01_raw-input.md` | Input trực tiếp cho ticket này |
| Blackbox coverage template | `docs/standards/templates/ticket-template/blackbox-coverage.template.md` | Template chuẩn để mô tả cách tính coverage |
| Blackbox testcases template | `docs/standards/templates/ticket-template/blackbox-testcases.template.md` | Định nghĩa observation point, case, traceability |
| QA Dashboard spec | `docs/changes/QA-DASHBOARD/spec-pack.md` | Nguồn tham chiếu cho chỉ số `blackboxCoveragePercent` hiện có |
| QA Dashboard report | `docs/changes/QA-DASHBOARD/report.md` | Xác nhận trạng thái triển khai hiện tại |
| QA Dashboard review checklist | `docs/changes/QA-DASHBOARD/review-checklist.md` | Xác nhận `blackboxCoveragePercent` hiện chưa tính thật |

## Confirmed Facts From Sources

- `blackbox-testcases.md` mục `3` là nguồn của `observation point`.
- `blackbox-testcases.md` mục `5` là nguồn của `black-box case` và `Priority`.
- `blackbox-testcases.md` mục `7` là nguồn của mapping `AC ↔ case`.
- `test-results.md` là nguồn của trạng thái execution như `PASS`, `FAIL`, `SKIP`, `NOT_RUN`.
- `Observation point` và `test viewpoint` là hai khái niệm khác nhau.
- `test viewpoint` trong QA Dashboard là tập góc nhìn cố định như `Normal`, `Error`, `Boundary`, `Permission`, `State Transition`, `Operation`, `Audit`, `Compatibility`.
- `blackboxCoveragePercent` trên QA Dashboard hiện là field có thật trong contract, nhưng trạng thái triển khai hiện tại vẫn là placeholder `0.0`.
- Repo đang dùng `SKIP` và `NOT_RUN` với ý nghĩa khác nhau:
  - `NOT_RUN`: chưa chạy / không có điều kiện chạy.
  - `SKIP`: chủ động bỏ qua, phải có lý do.

## Working Rules Used By This Ticket

- `Blackbox Design Coverage = 0.75 × AC Coverage (Design) + 0.25 × Observation Coverage (Design)`
- `Blackbox Execution Coverage = 0.60 × AC Coverage (Execution) + 0.25 × Observation Coverage (Execution) + 0.15 × Priority Coverage`
- `Blackbox Overall Coverage = 0.30 × Design + 0.70 × Execution`
- Priority weight dùng trong ticket này: `P0 = 5`, `P1 = 3`, `P2 = 1`
- Nếu mẫu số của một chỉ số thành phần bằng `0`, chỉ số đó = `0%`, không chia lại trọng số
- `SKIP` và `NOT_RUN` đều không được tính là `PASS` khi xét coverage execution
- Phần trăm được làm tròn đến `1` chữ số thập phân

## Current-System Note

- Ticket này chuẩn hóa cách hiểu và cách tính coverage trong tài liệu.
- Hệ thống QA Dashboard hiện tại vẫn đang có `blackboxCoveragePercent` riêng và đang trả placeholder `0.0`.
- Việc thay đổi cách tính thật trên BE/FE nên được xem là bước implementation riêng sau tài liệu này.
