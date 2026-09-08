# Báo cáo thay đổi — PARSER-REPORT (Markdown Parser Alignment)

- **Ticket:** PARSER-REPORT
- **Trạng thái:** Draft
- **Tạo ngày:** 2026-08-25 10:00
- **Cập nhật ngày:** 2026-08-25 10:15

## 1. Tóm tắt thay đổi

**Ticket:** PARSER-REPORT
**Nhánh:** feature/parser-report
**Ngày:** 2026-08-25
**Tác giả:** Codex

### Đã thay đổi gì

- Đồng bộ parser report với heading tiếng Việt của template chuẩn.
- Giữ tương thích ngược cho alias section cũ để dữ liệu lịch sử không bị gãy.

### Lý do

- Template chuẩn là nguồn chân lý; parser phải thích ứng với template thay vì yêu cầu sửa template.

## 2. Phạm vi ảnh hưởng

| # | Khu vực | Chi tiết |
| --- | --- | --- |
| 1 | Các tệp đã thay đổi | `ReportMarkdownParser.java`, `MarkdownParserCore.java`, `EvidenceQualityScoreService.java` |
| 2 | Lược đồ DB | Không thay đổi |
| 3 | Hợp đồng API | `parsedSummary` bổ sung key chuẩn mới và giữ alias cũ |
| 4 | Cấu hình | Không thay đổi |
| 5 | Nhật ký | Scanner sẽ đọc đúng metadata ngày tạo/cập nhật |
| 6 | Quyền/Vai trò | Không thay đổi |

## 3. Kết quả review

### Tự kiểm tra của Claude (`docs/changes/PARSER-REPORT/self-review.md`)

- Blocker phát hiện: Không
- Vấn đề mức nghiêm trọng cao phát hiện: Không
- Tất cả AC đã được đáp ứng: Có

### Review bởi Codex (nếu có chạy)

- Kết luận tổng thể: Phê duyệt
- Các phát hiện chính: Parser cũ còn phụ thuộc alias tiếng Anh trong `parsedSummary`.

### Các phát hiện từ review thủ công

| # | Phát hiện | Mức độ nghiêm trọng | Hành động đã thực hiện |
| --- | --- | --- | --- |
| 1 | Metadata bullet list chưa được parser core nhận diện | Major | Bổ sung hỗ trợ `- **Key:** value` |

## 4. Kết quả kiểm thử

| Loại kiểm thử | Lệnh | Kết quả | Ghi chú |
| --- | --- | --- | --- |
| FE UT | `npm test` | PASS | Không áp dụng trực tiếp |
| BE UT | `./gradlew test` | PASS | Parser report và review-checklist |
| API IT | `./gradlew test` | PASS | Controller parser |
| E2E | `npx playwright test` | PASS | Không áp dụng trực tiếp |
| Black-box | thủ công | PASS | Đọc đúng heading và metadata chuẩn |

Chi tiết đầy đủ: `docs/changes/PARSER-REPORT/test-results.md`

## 5. Công việc còn lại / Hành động tiếp theo

| # | Công việc | Người phụ trách | Hạn chót |
| --- | --- | --- | --- |
| 1 | Rà soát tài liệu cũ còn dùng heading tiếng Anh | Team parser | 2026-08-30 |

## 6. Quy trình hoàn tác

1. Khôi phục parser version trước đó nếu phát hiện dashboard không đọc được dữ liệu lịch sử.
2. Chạy lại scanner cho ticket bị ảnh hưởng sau khi rollback.

## 7. Danh mục đầu ra

| # | Tệp | Mục đích |
| --- | --- | --- |
| 1 | `docs/changes/PARSER-REPORT/spec-pack.md` | Nguồn tham chiếu duy nhất cho spec & AC |
| 2 | `docs/changes/PARSER-REPORT/impl-plan.md` | Cách tiếp cận triển khai & các bước thực hiện |
| 3 | `docs/changes/PARSER-REPORT/review-checklist.md` | Các góc nhìn review |
| 4 | `docs/changes/PARSER-REPORT/self-review.md` | Kết quả tự kiểm tra của Claude |
| 5 | `docs/changes/PARSER-REPORT/test-plan.md` | Kế hoạch bao phủ kiểm thử |
| 6 | `docs/changes/PARSER-REPORT/test-results.md` | Kết quả thực thi kiểm thử |
| 7 | `docs/changes/PARSER-REPORT/blackbox-testcases.md` | Các test case black-box |
