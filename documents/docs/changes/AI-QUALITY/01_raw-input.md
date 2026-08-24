# Tài liệu Thiết kế Chi tiết (Technical Design Document)
## Màn hình Quản lý % chất lượng AI theo từng ticket

---

### 1. Giới thiệu (Overview)
Tài liệu này mô tả chi tiết thiết kế kỹ thuật cho tính năng **Quản lý & Nhập số % chất lượng của AI theo Ticket**. Mỗi bản ghi (row) đại diện duy nhất cho **1 Ticket** thuộc một **Repository** và **Project**, đồng thời lưu trữ trực tiếp chỉ số: **Chất lượng AI (AI Quality)**.
Có các chức năng thêm, sửa, xem chi tiết, xóa logic(soft delete)

### 1.1. Phạm vi & Quy chuẩn Kế thừa (Architecture Guidelines)
> **LƯU Ý:** Màn hình này được phát triển dựa trên Hạ tầng & Quy chuẩn hiện có của hệ thống.
> 
> - **Cơ sở dữ liệu (Database):** Tái sử dụng các Bảng Danh mục (`tbl_dim_project`, `tbl_dim_repository`, `tbl_dim_ticket`), Kiểu dữ liệu (`UUID`, `TIMESTAMPTZ`), Enum (`record_status`) và các Trường Audit mặc định (`created_at`, `created_by`, `updated_at`, `updated_by`).
> - **Chuẩn Restful API & Response Format:** Các Endpoint, Payload Request/Response và Error Code trong tài liệu này **mang tính chất THAM KHẢO cấu trúc dữ liệu nghiệp vụ**. Khi triển khai thực tế, BẮT BUỘC tuân thủ theo Base Response Standard và Convention API hiện có của hệ thống (như wrappers `ApiResponse<T>`, Phân trang `Pageable`, v.v.).
> - **i18n:** Cơ chế Đa ngôn ngữ (i18n Message Source / LocaleResolver) đang chạy trên hệ thống.

---

### 2. Yêu cầu Hỗ trợ Đa ngôn ngữ (i18n Requirement)

 - Tham khảo hệ thống hiện tại

### 3. Yêu cầu Giao diện & Form Nhập liệu (UI/UX & Validation Rules)

#### 3.1. Các trường dữ liệu trên màn hình chính

1. **Dự án (Project ID)**: Dropdown chọn dự án.
2. **Kho lưu trữ (Repository ID)**: Dropdown chọn repository thuộc dự án đã chọn.
3. **Danh sách ticket**: hiển thị bảng gồm các trường: Action, Ticket, AI Quality

#### 3.2 Drawer tạo mới/chỉnh sửa
1. **Dự án (Project ID)**: Dropdown chọn dự án.
2. **Kho lưu trữ (Repository ID)**: Dropdown chọn repository thuộc dự án đã chọn.
3. **Ticket (Ticket ID)**: Dropdown chọn Ticket thuộc repository đã chọn.
4. **Chất lượng AI (AI Quality)**: Ô nhập % chất lượng AI.


| STT | Trường (Field Name) | Nhãn tiếng Việt (`vi`) | Nhãn tiếng Anh (`en`) | Nhãn tiếng Nhật (`ja`) |
| :-: | :--- | :--- | :--- | :--- |
| 1 | `project_id` | Dự án | Project | プロジェクト |
| 2 | `repository_id` | Kho lưu trữ | Repository | リポジトリ |
| 3 | `ticket_id` | Mã Ticket | Ticket Code | チケット |
| 4 | `ai_quality_rate` | Chất lượng AI | AI Quality | AIの品質 |


#### 3.3. Quy tắc Kiểm tra Dữ liệu & i18n Message Keys

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Quy tắc Validation | i18n Message Key | Thông báo mẫu (Tiếng Việt) |
| :--- | :--- | :---: | :--- | :--- | :--- |
| `project_id` | `String` | **Có** | - Không được trống.<br>- Tồn tại trong hệ thống. | `validation.project.required` | *Vui lòng chọn dự án.* |
| `repository_id` | `String` | **Có** | - Không được trống.<br>- Phải thuộc về `project_id`. | `validation.repository.invalid` | *Kho lưu trữ không hợp lệ hoặc không thuộc dự án.* |
| `ticket_id` | `String` | **Có** | - Không được trống.<br>- Phải thuộc về `repository_id` đã chọn. | `validation.ticket.invalid` | *ticket không hợp lệ hoặc không thuộc repository đã chọn* |
| `ai_quality_rate` | `BigDecimal` | **Có** | - Nằm trong khoảng 0-100 | `validation.ai_quality.invalid` | *tỉ lệ chất lượng phải nằm trong phạm vi 0-100%* |

---

### 4. Thiết kế Cấu trúc Bảng Database (Database Schema)

Bảng `tbl_dim_ai_quality` được thiết kế sao cho **mỗi Ticket là 1 Row duy nhất** chứa `ai_quality_rate`.

#### 4.1. Bảng `tbl_dim_ai_quality`

Tên bảng: `tbl_dim_ai_quality`

| STT | Tên trường (Column Name) | Kiểu dữ liệu (Data Type) | Nullable | Khóa (Key) | Mặc định (Default) | Mô tả (Description) |
| :-: | :--- | :--- | :-: | :-: | :--- | :--- |
| 1 | `ticket_ai_quality_id` | `UUID` | NO | PK | gen_random_uuid() | ID duy nhất của bản ghi |
| 2 | `project_id` | `UUID` | NO | FK | | ID của dự án (Tham chiếu `tbl_dim_project.project_id`) |
| 3 | `repository_id` | `UUID` | NO | FK | | ID của repository (Tham chiếu `tbl_dim_repository.repository_id`) |
| 4 | `ticket_id` | `UUID` | NO | FK | | ID của Ticket (Tham chiếu `tbl_dim_ticket.ticket_id`) |
| 6 | `ai_quality_rate` | `DECIMAL(5, 2)` | NO | | `0` | Tỉ lệ chất lượng AI |
| 8 | `delete_flag` | `INT` | NO | | `0` | Cờ xóa |
| 9 | `status` | `record_status` | NO | | `ACTIVE` | Cờ xóa |
| 11 | `created_at` | `TIMESTAMPTZ` | NO | | `CURRENT_TIMESTAMP` | Trường log 1: Thời gian tạo bản ghi |
| 12 | `created_by` | `VARCHAR(100)` | NO | | `SYSTEM` | Trường log 2: User ID người tạo |
| 13 | `updated_at` | `TIMESTAMPTZ` | NO | | `CURRENT_TIMESTAMP` | Trường log 3: Thời gian cập nhật gần nhất |
| 14 | `updated_by` | `VARCHAR(100)` | NO | | `SYSTEM` | Trường log 4: User ID người cập nhật gần nhất |

#### 4.2. Script DDL

```sql
CREATE TABLE tbl_dim_ai_quality (
    ticket_ai_quality_id UUID gen_random_uuid() PRIMARY KEY,
    project_id UUID NOT NULL,
    repository_id UUID NOT NULL,
    ticket_id UUID NOT NULL,
    ai_quality_rate DECIMAL(5, 2) NOT NULL DEFAULT 0,
    delete_flag INT NOT NULL DEFAULT 0,
    status record_status NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',

    -- Khóa ngoại (Foreign Keys) kiểu UUID
    CONSTRAINT fk_ai_quality_project FOREIGN KEY (project_id) REFERENCES tbl_dim_project (project_id),
    CONSTRAINT fk_ai_quality_repository FOREIGN KEY (repository_id) REFERENCES tbl_dim_repository (repository_id),
    CONSTRAINT fk_ai_quality_ticket FOREIGN KEY (ticket_id) REFERENCES tbl_dim_ticket (ticket_id)
);

    -- Ràng buộc duy nhất (Unique Constraint) cho 1 Ticket trong 1 Repo
    -- CONSTRAINT uk_repo_ticket UNIQUE (repository_id, ticket_id)
    CREATE UNIQUE INDEX uk_repo_ticket_active 
    ON tbl_dim_ai_quality (repository_id, ticket_id) 
    WHERE delete_flag = 0;
```

---

### 5. Thiết kế API Endpoints

*Ghi chú: Cấu trúc Request/Response dưới đây mô tả các trường dữ liệu cần thiết cho nghiệp vụ. Lập trình viên chủ động map theo Base DTO / Base Response Wrapper của hệ thống hiện tại.*

#### Summary of Endpoints
| Method | Endpoint | Description | Permission |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/ai-qualities` | Tạo mới bản ghi 1 Ticket chứa tỉ lệ chất lượng AI | `ADMIN`, `QA`, `PM` |
| `PUT` | `/api/v1/ai-qualities/{id}` | Cập nhật tỉ lệ chất lượng AI của Ticket | `ADMIN`, `QA`, `PM` |
| `PUT` | `/api/v1/ai-qualities/{id}/delete` | Xóa logic(Soft delete) bản ghi 1 Ticket chứa tỉ lệ chất lượng AI | `ADMIN`, `QA`, `PM` |
| `GET` | `/api/v1/ai-qualities/{id}` | Xem chi tiết tỉ lệ chất lượng AI của 1 Ticket | `ALL` |
| `GET` | `/api/v1/ai-qualities` | Danh sách Ticket & tỉ lệ chất lượng AI (lọc theo Project, Repo, Ticket) | `ALL` |

#### Create/Update Request Body Sample
```
{
  "project_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "repository_id": "7ca85f64-5717-4562-b3fc-2c963f66afa6",
  "ticket_id": "9ba85f64-5717-4562-b3fc-2c963f66afa6",
  "ai_quality_rate": 85.50
}
```

---



### 6. Xử lý Logic Nghiệp vụ & Luồng hệ thống (Business Logic & Flow)

1. **Đảm bảo 1 Ticket = 1 Row duy nhất**:
   * Áp dụng Unique Constraint `UNIQUE KEY uk_repo_ticket (repository_id, ticket_id)`.
   * Nếu thực hiện API `POST` với mã `ticket_id` đã tồn tại trong repository đó, Backend sẽ báo lỗi `409 Conflict` (yêu cầu người dùng dùng API `PUT` để cập nhật tỉ lệ chất lượng AI).

2. **Ghi log 4 trường Audit tự động**:
   * `created_at` & `created_by`: Tự động ghi nhận thời gian và ID user tạo bản ghi ở lần đầu tiên.
   * `updated_at` & `updated_by`: Tự động cập nhật thời gian và ID user mỗi khi thay đổi tỉ lệ chất lượng AI

3. **Cơ chế i18n trên Backend (Spring Boot / Node.js)**:
   - Cấu hình `LocaleResolver` đọc từ `Accept-Language` header.
   - Sử dụng `MessageSource` kết hợp file tài nguyên `messages_vi.properties`, `messages_en.properties`, `messages_ja.properties`.
   - Tất cả DTO Validation Annotations (VD: `@Min`, `@NotNull`, `@Size`) đều gán `message = "{validation.key}"` để tự động hóa dịch thông báo lỗi.

4. **Soft delete** 
    - thực hiện xóa logic, cập nhật delete_flag = 1, status='DELETED' và updated_at, updated_by

5. **Phân quyền**
    - Chỉ ADMIN, PM, QA mới có quyền chỉnh sửa, tạo mới, xóa
    - DEV có thể truy cập màn hình nhưng chỉ có thể xem không có quyền tác động hay chỉnh sửa
    - Nhưng quyền khác không được truy cập vào màn hình