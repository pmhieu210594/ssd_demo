# Raw Input

**Ticket ID**: PHASE-DWELL-TIME
**Create date**: 2026-08-21
**Author**: TBD  
**Update date**: 2026-08-21

## Ticket Body

# TÀI LIỆU YÊU CẦU CHỨC NĂNG (SRS)
## TÍNH NĂNG: PHASE DWELL TIME (THỜI GIAN TỒN ĐỌNG / LƯU LẠI TẠI TỪNG PHASE)

### 1. TỔNG QUAN TÍNH NĂNG (FEATURE OVERVIEW)

- **Tên tính năng:** Phase Dwell Time (Thời gian tồn đọng / lưu lại tại từng Phase).
- **Mục tiêu:** Hiển thị thêm field `Phase Dwell Time` cho từng phase.
- **Vị trí hiển thị:**
  - Components: `TicketDetailDrawer.tsx`
  - Function: `PhaseCard`
  - Vị trí cụ thể: Đặt bên dưới thông tin field `Created at` trong danh sách các Phase.

### 2. USER STORY & ĐỐI TƯỢNG SỬ DỤNG

- **Đối tượng:** Manager, Support Lead, Operation Admin, Support Agent.
- **User Story:**
  > Là một **Manager / Support Lead**,  
  > Tôi muốn **xem tổng thời gian ticket đã lưu lại ở từng Phase** ngay trong giao diện chi tiết ticket,  
  > Để tôi **xác định nguyên nhân chậm trễ, theo dõi SLA từng bước và đánh giá chính xác hiệu suất giải quyết công việc**.

## Requirement Notes

### 3. PHẠM VI VÀ PHẦN TỬ HIỂN THỊ (UI/UX SPECIFICATIONS)

#### 3.1. Vị trí & Cấu trúc Bố cục
- Component cha: `TicketDetailDrawer.tsx`
- Component mục tiêu: `PhaseCard`
- Thứ tự hiển thị trong PhaseCard:
  1. Ticket Meta Info (Ticket ID, Created at)
  2. Danh sách tất cả các Phase trong Workflow
  3. Thông tin `Phase Dwell Time` tương ứng của từng Phase (ngay phía dưới Created at của Phase)

#### 3.2. Hiển thị danh sách Phase
- Hiển thị đầy đủ tất cả các Phase(phụ thuộc vào các file .md nào tồn tại dựa vào các bảng `tbl_dim_phase`, `tbl_dim_artifact_type`).
- Thứ tự danh sách giữ nguyên theo thứ tự cấu hình Workflow (Workflow Sequence).

#### 3.3. Cách tính Dwell Time (bổ sung theo yêu cầu người dùng, 2026-08-19)

- Phase Dwell Time: Đọc thông tin thời gian bắt đầu, thời gian kết thúc từ scanner các file .md cho từng phase.
- 
- Logic: Thời gian kết thúc - thời gian bắt đầu.

#### 3.5. Chuẩn hóa đơn vị thời gian (Time Format Standard)

- Ví dụ: `03:20:00` (3 giờ 20 phút 0 giây), `27:20:05` (hơn 1 ngày).

## Meeting Notes

(Không có)

## Customer Comments

(Không có)

## Raw References

(Không có)

## Notes

(Không có)