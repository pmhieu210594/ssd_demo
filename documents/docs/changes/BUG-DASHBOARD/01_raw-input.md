# Tài liệu Thiết kế Chi tiết (Technical Design Document)
## Màn hình Quản lý Bug theo Ticket (Internal Bug & Customer Bug)

---

### 1. Giới thiệu (Overview)
Tài liệu này mô tả chi tiết thiết kế kỹ thuật cho tính năng **Quản lý & Nhập số lượng Bug theo Ticket**. Mỗi bản ghi (row) đại diện duy nhất cho **1 Ticket** thuộc một **Repository** và **Project**, đồng thời lưu trữ trực tiếp cả 2 chỉ số: **Số bug nội bộ (Internal Bug Count)** và **Số bug khách hàng bắt được (Customer Bug Count)**.
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
3. **Danh sách ticket**: hiển thị bảng gồm các trường: Action, Ticket, Internal Bug, Customer Bug, Note

#### 3.2 Drawer tạo mới/chỉnh sửa
1. **Dự án (Project ID)**: Dropdown chọn dự án.
2. **Kho lưu trữ (Repository ID)**: Dropdown chọn repository thuộc dự án đã chọn.
3. **Ticket (Ticket ID)**: Dropdown chọn Ticket thuộc repository đã chọn.
4. **Số Bug Nội bộ (Internal Bug Count)**: Ô nhập số lượng bug do đội QA phát hiện.
5. **Số Bug Khách hàng (Customer Bug Count)**: Ô nhập số lượng bug do Khách hàng/UAT phản hồi.
6. **Ghi chú (Note)**: Thông tin bổ sung (optional).

| STT | Trường (Field Name) | Nhãn tiếng Việt (`vi`) | Nhãn tiếng Anh (`en`) | Nhãn tiếng Nhật (`ja`) |
| :-: | :--- | :--- | :--- | :--- |
| 1 | `project_id` | Dự án | Project | プロジェクト |
| 2 | `repository_id` | Kho lưu trữ | Repository | リポジトリ |
| 3 | `ticket_id` | Mã Ticket | Ticket Code | チケット |
| 4 | `internal_bug_count` | Bug Nội bộ | Internal Bug Count | 内部バグ数 |
| 5 | `customer_bug_count` | Bug Khách hàng | Customer Bug Count | 顧客バグ数 |
| 6 | `note` | Ghi chú | Note | 備考 |

#### 3.3. Quy tắc Kiểm tra Dữ liệu & i18n Message Keys

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Quy tắc Validation | i18n Message Key | Thông báo mẫu (Tiếng Việt) |
| :--- | :--- | :---: | :--- | :--- | :--- |
| `project_id` | `String` | **Có** | - Không được trống.<br>- Tồn tại trong hệ thống. | `validation.project.required` | *Vui lòng chọn dự án.* |
| `repository_id` | `String` | **Có** | - Không được trống.<br>- Phải thuộc về `project_id`. | `validation.repository.invalid` | *Kho lưu trữ không hợp lệ hoặc không thuộc dự án.* |
| `ticket_id` | `String` | **Có** | - Không được trống.<br>- Phải thuộc về `repository_id` đã chọn. | `validation.ticket.invalid` | *ticket không hợp lệ hoặc không thuộc repository đã chọn* |
| `internal_bug_count` | `Integer` | **Có** | - Số nguyên không âm (`>= 0`). | `validation.bug_count.min` | *Số bug nội bộ phải là số nguyên lớn hơn hoặc bằng 0.* |
| `customer_bug_count` | `Integer` | **Có** | - Số nguyên không âm (`>= 0`). | `validation.bug_count.min` | *Số bug khách hàng phải là số nguyên lớn hơn hoặc bằng 0.* |
| `note` | `String` | Không | - Độ dài tối đa 500 ký tự. | `validation.note.max_length` | *Ghi chú không được vượt quá 500 ký tự.* |

---

### 4. Thiết kế Cấu trúc Bảng Database (Database Schema)

Bảng `tbl_ticket_bug_metrics` được thiết kế sao cho **mỗi Ticket là 1 Row duy nhất**, chứa đồng thời `internal_bug_count` và `customer_bug_count`.

#### 4.1. Bảng `tbl_ticket_bug_metrics`

Tên bảng: `tbl_ticket_bug_metrics`

| STT | Tên trường (Column Name) | Kiểu dữ liệu (Data Type) | Nullable | Khóa (Key) | Mặc định (Default) | Mô tả (Description) |
| :-: | :--- | :--- | :-: | :-: | :--- | :--- |
| 1 | `id` | `UUID` | NO | PK | gen_random_uuid() | ID duy nhất của bản ghi |
| 2 | `project_id` | `UUID` | NO | FK | | ID của dự án (Tham chiếu `projects.id`) |
| 3 | `repository_id` | `UUID` | NO | FK | | ID của repository (Tham chiếu `repositories.id`) |
| 4 | `ticket_id` | `UUID` | NO | FK | | ID của Ticket |
| 6 | `internal_bug_count` | `INT` | NO | | `0` | Số lượng bug nội bộ bắt được |
| 7 | `customer_bug_count` | `INT` | NO | | `0` | Số lượng bug khách hàng bắt được |
| 8 | `delete_flag` | `INT` | NO | | `0` | Cờ xóa |
| 9 | `status` | `record_status` | NO | | `ACTIVE` | Cờ xóa |
| 10 | `note` | `VARCHAR(500)` | YES | | `NULL` | Ghi chú thêm |
| 11 | `created_at` | `TIMESTAMPTZ` | NO | | `CURRENT_TIMESTAMP` | Trường log 1: Thời gian tạo bản ghi |
| 12 | `created_by` | `VARCHAR(100)` | NO | | `SYSTEM` | Trường log 2: User ID người tạo |
| 13 | `updated_at` | `TIMESTAMPTZ` | NO | | `CURRENT_TIMESTAMP` | Trường log 3: Thời gian cập nhật gần nhất |
| 14 | `updated_by` | `VARCHAR(100)` | NO | | `SYSTEM` | Trường log 4: User ID người cập nhật gần nhất |

#### 4.2. Script DDL

```sql
CREATE TABLE tbl_ticket_bug_metrics (
    ticket_bug_id UUID gen_random_uuid() PRIMARY KEY,
    project_id UUID NOT NULL,
    repository_id UUID NOT NULL,
    ticket_id UUID NOT NULL,
    internal_bug_count INT NOT NULL DEFAULT 0,
    customer_bug_count INT NOT NULL DEFAULT 0,
    delete_flag INT NOT NULL DEFAULT 0,
    status record_status NOT NULL DEFAULT 'ACTIVE',
    note VARCHAR(500) DEFAULT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',

    -- Khóa ngoại (Foreign Keys) kiểu UUID
    CONSTRAINT fk_tbm_project FOREIGN KEY (project_id) REFERENCES tbl_dim_project (project_id),
    CONSTRAINT fk_tbm_repository FOREIGN KEY (repository_id) REFERENCES tbl_dim_repository (repository_id),
    CONSTRAINT fk_tbm_ticket FOREIGN KEY (ticket_id) REFERENCES tbl_dim_ticket (ticket_id),

    -- Ràng buộc duy nhất (Unique Constraint) cho 1 Ticket trong 1 Repo
    -- CONSTRAINT uk_repo_ticket UNIQUE (repository_id, ticket_id)
    CREATE UNIQUE INDEX uk_repo_ticket_active 
    ON tbl_ticket_bug_metrics (repository_id, ticket_id) 
    WHERE delete_flag = 0;
    );
```

---

### 5. Thiết kế API Endpoints

*Ghi chú: Cấu trúc Request/Response dưới đây mô tả các trường dữ liệu cần thiết cho nghiệp vụ. Lập trình viên chủ động map theo Base DTO / Base Response Wrapper của hệ thống hiện tại.*

#### Summary of Endpoints
| Method | Endpoint | Description | Permission |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/ticket-bugs` | Tạo mới bản ghi 1 Ticket chứa 2 count bug | `QA`, `PM` |
| `PUT` | `/api/v1/ticket-bugs/{id}` | Cập nhật số liệu bug của Ticket | `QA`, `PM` |
| `PUT` | `/api/v1/ticket-bugs/{id}/delete` | Cập nhật số liệu bug của Ticket | `QA`, `PM` |
| `GET` | `/api/v1/ticket-bugs/{id}` | Xem chi tiết số liệu bug của 1 Ticket | `ALL` |
| `GET` | `/api/v1/ticket-bugs` | Danh sách Ticket & số bug (lọc theo Project, Repo, Ticket) | `ALL` |

---

#### 5.1. Endpoint 1: Tạo mới Ticket Bug Record (`POST /api/v1/ticket-bugs`)

* **Request Body:**
```json
{
  "projectId": "101",
  "repositoryId": "501",
  "ticketId": "PRJ-2048",
  "internalBugCount": 12,
  "customerBugCount": 2,
  "note": "Khách hàng bắt được 2 bug ở đợt UAT lần 2"
}
```

* **Response Success (201 Created):**
```json
{
  "code": 201,
  "status": "SUCCESS",
  "message": "Ghi nhận số liệu bug cho ticket thành công",
  "data": {
    "ticketBugId": "88",
    "projectId": "101",
    "repositoryId": "501",
    "ticketId": "PRJ-2048",
    "ticketTitle": "Tính năng thanh toán VNPay",
    "internalBugCount": 12,
    "customerBugCount": 2,
    "note": "Khách hàng bắt được 2 bug ở đợt UAT lần 2",
    "createdAt": "2026-08-06T10:00:00Z",
    "createdBy": "1005",
    "updatedAt": "2026-08-06T10:00:00Z",
    "updatedBy": "1005"
  }
}
```

---

#### 5.2. Endpoint 2: Cập nhật số bug của Ticket (`PUT /api/v1/ticket-bugs/{id}`)

* **Path Variable:** `id` (String) - ID bản ghi bug.
* **Request Body:**
```json
{
  "internalBugCount": 15,
  "customerBugCount": 3,
  "note": "Cập nhật thêm 3 bug nội bộ và 1 bug khách hàng mới phát hiện"
}
```

* **Response Success (200 OK):**
```json
{
  "code": 200,
  "status": "SUCCESS",
  "message": "Cập nhật số liệu bug thành công",
  "data": {
    "ticketBugId": "88",
    "projectId": "101",
    "repositoryId": "501",
    "ticketId": "PRJ-2048",
    "ticketTitle": "Tính năng thanh toán VNPay (Updated)",
    "internalBugCount": 15,
    "customerBugCount": 3,
    "note": "Cập nhật thêm 3 bug nội bộ và 1 bug khách hàng mới phát hiện",
    "createdAt": "2026-08-06T10:00:00Z",
    "createdBy": "1005",
    "updatedAt": "2026-08-06T10:15:00Z",
    "updatedBy": "1008"
  }
}
```

---

#### 5.3. Endpoint 3: Truy vấn danh sách Ticket Bug (`GET /api/v1/ticket-bugs`)

* **Query Parameters:**
  * `projectId` (String, Optional): Lọc theo dự án.
  * `repositoryId` (String, Optional): Lọc theo repository.
  * `ticketId` (String, Optional): Tìm theo ticket id.
  * `page` (Int, Default: 0) & `size` (Int, Default: 20).

* **Response Success (200 OK):**
```json
{
  "code": 200,
  "status": "SUCCESS",
  "data": {
    "content": [
      {
        "ticketBugId": "88",
        "projectId": "101",
        "repositoryId": "501",
        "ticketId": "PRJ-2048",
        "ticketTitle": "Tính năng thanh toán VNPay (Updated)",
        "internalBugCount": 15,
        "customerBugCount": 3,
        "note": "Cập nhật thêm 3 bug nội bộ và 1 bug khách hàng mới phát hiện",
        "status": "ACTIVE",
        "createdAt": "2026-08-06T10:00:00Z",
        "createdBy": "1005",
        "updatedAt": "2026-08-06T10:15:00Z",
        "updatedBy": "1008"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

#### 5.4. Endpoint 4: Soft delete Ticket Bug (`PUT /api/v1/ticket-bugs/{id}/delete`)

* **Path Variable:** `id` (String) - ID bản ghi bug.
* **Response Success (200 OK):**
```json
{
  "code": 200,
  "status": "SUCCESS",
  "message": "Xóa số liệu bug của ticket thành công",
  "data": {
    "ticketBugId": "88",
    "status": "DELETED",
    "deleteFlag": 1
  }
}
```
---

### 6. Xử lý Logic Nghiệp vụ & Luồng hệ thống (Business Logic & Flow)

1. **Đảm bảo 1 Ticket = 1 Row duy nhất**:
   * Áp dụng Unique Constraint `UNIQUE KEY uk_repo_ticket (repository_id, ticket_id)`.
   * Nếu thực hiện API `POST` với mã `ticket_id` đã tồn tại trong repository đó, Backend sẽ báo lỗi `409 Conflict` (yêu cầu người dùng dùng API `PUT` để cập nhật số lượng count).

2. **Ghi log 4 trường Audit tự động**:
   * `created_at` & `created_by`: Tự động ghi nhận thời gian và ID user tạo bản ghi ở lần đầu tiên.
   * `updated_at` & `updated_by`: Tự động cập nhật thời gian và ID user mỗi khi thay đổi số bug nội bộ/khách hàng.

3. **Cơ chế i18n trên Backend (Spring Boot / Node.js)**:
   - Cấu hình `LocaleResolver` đọc từ `Accept-Language` header.
   - Sử dụng `MessageSource` kết hợp file tài nguyên `messages_vi.properties`, `messages_en.properties`, `messages_ja.properties`.
   - Tất cả DTO Validation Annotations (VD: `@Min`, `@NotNull`, `@Size`) đều gán `message = "{validation.key}"` để tự động hóa dịch thông báo lỗi.

4. **Soft delete** 
    - thực hiện xóa logic, cập nhật delete_flag = 1, status='DELETED' và updated_at, updated_by

5. **Phân quyền**
    - Chỉ PM, QA mới có quyền chỉnh sửa, tạo mới, xóa bug
    - DEV có thể truy cập màn hình nhưng chỉ có thể xem không có quyền tác động hay chỉnh sửa bug
    - Nhưng quyền khác không được truy cập vào màn hình