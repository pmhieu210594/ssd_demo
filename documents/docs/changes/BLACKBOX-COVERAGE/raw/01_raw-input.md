# Raw Input

**Ticket ID**: <TICKET>
**Create date**: <Create_date>  
**Author**:  <Author>
**Update date**: <Update_date>  

## Ticket Body

## Requirement Notes

## Meeting Notes

## Customer Comments

## Raw References

## Notes

## Suggested Task Inputs

### Task: cập nhật cách tính Blackbox Coverage cho báo cáo nội bộ / dashboard

**Mục tiêu**

- Chuẩn hóa cách tính `Blackbox Coverage` theo hướng tách riêng:
- `Blackbox Design Coverage`: đo lúc viết tài liệu test.
- `Blackbox Execution Coverage`: đo sau khi chạy test.
- `Blackbox Overall Coverage`: chỉ số tổng hợp để theo dõi nội bộ / dashboard.

**Công thức mong muốn**

- `AC Coverage (Design) = Covered AC / Total AC × 100`
- `Observation Coverage (Design) = Covered observation points / Total observation points × 100`
- `Blackbox Design Coverage = 0.75 × AC Coverage (Design) + 0.25 × Observation Coverage (Design)`

- `AC Coverage (Execution) = Passed AC / Total AC × 100`
- `Observation Coverage (Execution) = Passed observation points / Total observation points × 100`
- `Priority Coverage = Passed priority score / Total priority score × 100`
- `Blackbox Execution Coverage = 0.60 × AC Coverage (Execution) + 0.25 × Observation Coverage (Execution) + 0.15 × Priority Coverage`

- `Blackbox Overall Coverage = 0.30 × Blackbox Design Coverage + 0.70 × Blackbox Execution Coverage`

**Nguồn dữ liệu chuẩn**

- `spec-pack.md`: mẫu số AC trong scope
- `blackbox-testcases.md` mục 3: mẫu số screen / flow / observation point
- `blackbox-testcases.md` mục 5: planned black-box cases + priority
- `blackbox-testcases.md` mục 7: mapping `AC ↔ case`
- `test-results.md`: PASS / FAIL / SKIP / NOT_RUN

**File tham chiếu cần đọc**

- `docs/changes/<TICKET>/spec-pack.md`
- `docs/changes/<TICKET>/blackbox-testcases.md`
- `docs/changes/<TICKET>/test-results.md`

**Mục tham chiếu trong từng file**

- `blackbox-testcases.template.md`
- Mục `3. Backlink theo màn hình / điểm quan sát`
- Mục `5. Các black-box test case`
- Mục `7. Traceability: AC ↔ black-box cases`

- `blackbox-coverage.template.md`
- Mục `2. Nguồn dữ liệu đầu vào`
- Mục `4. Chỉ số thành phần`
- Mục `5. Công thức Blackbox Coverage tổng hợp`
- Mục `7. Bảng tính kết quả`
- Mục `8. Release gate khuyến nghị`

- `spec-pack.md`
- Danh sách `Acceptance Criteria` trong scope release

- `blackbox-testcases.md`
- Danh sách `screen / flow / outcome`
- Danh sách `case ID`, `priority`, `status`
- Bảng `AC ↔ case`

- `test-results.md`
- Kết quả thực thi thực tế dùng để tính `Execution Coverage`

**Quy tắc nghiệp vụ**

- `Overall Coverage` chỉ dùng cho báo cáo nội bộ / dashboard.
- Quyết định release không dùng `Overall Coverage` một mình.
- `P0 PASS rate` vẫn phải đạt `100%`.
- `Blackbox Execution Coverage` mới là chỉ số chính để phản ánh bằng chứng chạy test thực tế.

**Đầu ra kỳ vọng**

- Cập nhật tài liệu mô tả metric `Blackbox Coverage`.
- Nếu có dashboard hoặc summary card, hiển thị riêng:
- `Design Coverage`
- `Execution Coverage`
- `Overall Coverage`
- Ghi rõ rule: `Overall` không thay thế release gate.
