# Raw Input

**Ticket ID**: REVIEW-FINDING-DENSITY  
**Create date**: 2026-09-10  
**Author**: nvt_dung
**Update date**: 2026-09-10  

## Ticket Body

Thêm chỉ số **Review Finding Density** vào màn hình chi tiết ticket của PM Dashboard.

Chỉ số đo số review finding do human reviewer tạo theo quy mô thay đổi của các Pull Request liên kết với ticket.

```text
Review Finding Density =
    Tổng review finding / Tổng changed lines × 1.000
```

Đơn vị hiển thị: `findings / KLOC` (số finding trên 1.000 dòng code thay đổi).

## Requirement Notes

### 1. Nguồn review finding

- Nguồn chính là GitHub Pull Request review thread/comment do human reviewer tạo.
- Không tính mọi review comment là finding.
- Tính mọi review thread thuộc review có state hợp lệ sau chuẩn hóa: `APPROVED`, `CHANGES_REQUESTED` hoặc `REVIEW_REQUIRED`.
- Review có state `UNKNOWN`, trống hoặc không nhận diện không được tính vào Review Finding Density.
- Không đọc nội dung comment để quyết định eligibility; comment trao đổi, `LGTM`, `ACK` hoặc câu hỏi trong thread hợp lệ vẫn được tính.
- Một review hợp lệ với nhiều thread thì mỗi thread finding được tính một lần.
- Nhiều comment trong cùng một thread phải được deduplicate theo thread/finding identity.

### 2. Trạng thái finding

Finding có thể có các trạng thái:

- `OPEN`
- `RESOLVED`
Tổng density tính tất cả finding đã được xác định hợp lệ, không phụ thuộc finding đang `OPEN` hay đã `RESOLVED`. Hai trạng thái này được dùng để hiển thị breakdown.

Không được coi finding là resolved chỉ vì Pull Request có commit mới. Finding được xem là resolved khi review thread trên GitHub được resolve qua GraphQL.

### 3. Quy mô thay đổi

Changed lines của một Pull Request được tính bằng:

```text
changed lines = additions + deletions
```

Nguồn dữ liệu là các file thay đổi của Pull Request. Không cộng dồn changed lines của từng commit; phải dùng diff của `head_sha` hiện tại hoặc commit cuối cùng của Pull Request.

### 4. Công thức

Với một Pull Request:

```text
Review Finding Density =
    số review finding / tổng changed lines × 1.000
```

Với ticket có nhiều Pull Request liên kết:

```text
Review Finding Density =
    tổng finding của các Pull Request liên kết
    / tổng changed lines của các Pull Request liên kết
    × 1.000
```

Không lấy trung bình đơn giản density của từng Pull Request.

Nếu `changed lines = 0`, hiển thị `N/A`, không hiển thị `0`.

### 5. Cập nhật khi Pull Request có commit mới

- Khi Pull Request có commit mới, phải đọc lại diff hiện tại và tính lại `additions + deletions`.
- Phải cập nhật hoặc xác định lại các finding của phiên bản mới.
- Dữ liệu nên được định danh bằng `pr_id` và `head_sha` để tránh cộng dồn dữ liệu giữa các phiên bản.
- Nếu chỉ cần giá trị hiện tại, có thể giữ snapshot mới nhất.
- Nếu cần lịch sử, lưu snapshot theo từng `head_sha`.

### 6. Trạng thái triển khai hiện tại

- Schema đã có `tbl_fact_review`, `tbl_fact_review_comment` và `tbl_fact_finding`.
- Chưa có flow hoàn chỉnh để phân loại GitHub review thread/comment thành finding và insert vào `tbl_fact_finding`.
- Chưa có flow lấy trạng thái resolved của GitHub review thread.
- Vì vậy hiện tại chưa thể tính Review Finding Density từ dữ liệu runtime một cách đầy đủ.

## Meeting Notes

## Customer Comments

## Raw References

- `tbl_fact_review`
- `tbl_fact_review_comment`
- `tbl_fact_finding`
- `tbl_fact_pull_request_changed_file`
- `additions`, `deletions`
- `pr_id`, `head_sha`
- [V4__init_shema_v2.sql](D:/DataAnalyticsProject/source/EDCAP/EDCAP_BE/src/main/resources/db/migration/V4__init_shema_v2.sql:590)
- [V181__git_pr_metadata_collector_schema.sql](D:/DataAnalyticsProject/source/EDCAP/EDCAP_BE/src/main/resources/db/migration/V181__git_pr_metadata_collector_schema.sql:15)
- [GitPrMetadataCollectorService.java](D:/DataAnalyticsProject/source/EDCAP/EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/ingestion/GitPrMetadataCollectorService.java)
- [GitPrMetadataCollectorJdbcAdapter.java](D:/DataAnalyticsProject/source/EDCAP/EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/GitPrMetadataCollectorJdbcAdapter.java)

## Notes

`Review Finding Density` nên được tính từ human review finding, không dùng `tbl_fact_ai_finding_stat` làm nguồn chính. AI finding là nhóm dữ liệu riêng, phù hợp với các KPI AI Review Adoption, AI Valid Finding Rate, AI False Positive Rate và AI Finding Resolution Rate.

Review finding không cần lưu sẵn dưới dạng một cột density trong database. Có thể lưu dữ liệu gốc (`tbl_fact_finding` và `tbl_fact_pull_request_changed_file`) rồi tính density khi mở Ticket Detail. Chỉ cần thêm bảng/cột snapshot nếu muốn lưu lịch sử density theo `head_sha`.
