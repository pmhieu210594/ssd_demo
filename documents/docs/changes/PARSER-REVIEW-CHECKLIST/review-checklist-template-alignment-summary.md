# REVIEW CHECKLIST TEMPLATE ALIGNMENT SUMMARY

**Ticket ID**: PARSER-REVIEW-CHECKLIST  
**Phạm vi**: đồng bộ hệ thống parse `review-checklist.md` với template chuẩn tại `docs/standards/templates/ticket-template/review-checklist.template.md`

## 1. Mục tiêu

Tài liệu này mô tả bức tranh hệ thống của luồng parse `review-checklist.md`, các thay đổi đã thực hiện khi bám theo template chuẩn mới, và ý nghĩa của từng thay đổi đối với parser, scanner, scoring, và kiểm tra downstream.

Nguyên tắc cốt lõi:

- `review-checklist.template.md` là nguồn chuẩn.
- Không sửa template để chiều theo parser.
- Nếu template thay đổi hợp lệ, parser và downstream service phải thích ứng.

## 2. Bức tranh hệ thống

Luồng xử lý `review-checklist.md` hiện đi qua các lớp chính sau:

1. `MarkdownParserCore`  
   Nhận markdown thô, tách heading, metadata đầu file, bảng và placeholder.

2. `ReviewChecklistMarkdownParser`  
   Áp rule riêng cho `review-checklist.md`, xác định section bắt buộc, trạng thái parse, thống kê checklist, và `parsedSummary`.

3. `ArtifactScannerService`  
   Gọi parser, cập nhật snapshot, persist parsed sections, ghi evidence event, và kích hoạt recalculation.

4. `EvidenceQualityScoreRepositoryAdapter`  
   Nạp lại các section/key đã persist để scoring service dùng đúng schema review-checklist.

5. `EvidenceQualityScoreService`  
   Đọc tín hiệu đã parse để chấm điểm phần review checklist, đặc biệt là góc nhìn security/test.

6. Test layer  
   Khóa hành vi bằng parser test, controller test, và scanner test để ngăn regression khi template tiếp tục evolve.

## 3. Các mục đã thay đổi và ý nghĩa

### 3.1 Danh sách section bắt buộc bám theo template chuẩn mới

Parser hiện dùng các section chuẩn:

- `SPEC_AC`
- `THIẾT_KẾ_PHỤ_THUỘC`
- `BẢO_MẬT`
- `HIỆU_NĂNG`
- `TƯƠNG_THÍCH`
- `LOGGING_AUDIT`
- `XỬ_LÝ_LỖI`
- `KIỂM_THỬ`
- `VẬN_HÀNH`
- `BẢNG_ÁNH_XẠ_AC_CHECKLIST_ITEMS`

Ý nghĩa:

- Hệ thống không còn bám vào mô hình cũ kiểu `SECURITY / TEST / PERFORMANCE`.
- Các warning thiếu section giờ phản ánh đúng cấu trúc checklist chuẩn.
- Traceability và persistence nhìn thấy đúng “shape” của artifact thật trong repo.

### 3.2 Tách section checklist và section bảng ánh xạ AC

Parser giữ `BẢNG_ÁNH_XẠ_AC_CHECKLIST_ITEMS` là required section, nhưng không gộp nó vào logic “section checklist đã check hết hay chưa”.

Ý nghĩa:

- Tránh trộn hai ý nghĩa khác nhau:
  - section checklist dùng để kiểm tra trạng thái review;
  - bảng ánh xạ AC dùng để kiểm tra traceability.
- Kết quả `all_checklist_sections_checked` giờ phản ánh đúng các nhóm review, không bị méo bởi bảng mapping.

### 3.3 Parse bảng checklist theo cột `Mức độ` và `Trạng thái`

Parser được mở rộng để đọc trực tiếp các bảng checklist trong từng section và xác định:

- số item trong section,
- số item đã check,
- số item chưa check,
- severity của các item còn mở.

Ý nghĩa:

- Parser hiểu đúng bản chất fillable checklist của template mới.
- Hệ thống không chỉ biết “section có mặt” mà còn biết “section đã được review tới đâu”.
- Dữ liệu này hữu ích cho scoring, dashboard, và review follow-up.

### 3.4 Bổ sung thống kê mới trong `parsedSummary`

`parsedSummary` hiện có thêm các nhóm dữ liệu như:

- `checklist_section_item_count`
- `checklist_section_checked_item_count`
- `checklist_section_unchecked_item_count`
- `checklist_total_item_count`
- `checklist_checked_item_count`
- `checklist_unchecked_item_count`
- `checklist_unchecked_severity_count`
- `ac_checklist_mapping_present`
- `ac_checklist_mapping_row_count`
- `ac_checklist_mapping_complete`

Ý nghĩa:

- Snapshot lưu được trạng thái review chi tiết hơn mà không cần parse lại file.
- Các service downstream có thể đọc metadata sẵn có thay vì suy luận lại từ markdown thô.
- Đây là bước tăng chất lượng dữ liệu nhưng vẫn giữ nguyên cấu trúc parser cũ.

### 3.5 Parse riêng bảng `AC -> Checklist items`

Parser hiện phân tích bảng ánh xạ AC để biết:

- section mapping có hiện diện hay không,
- có bao nhiêu dòng mapping,
- các dòng có điền đủ AC và checklist items hay không.

Ý nghĩa:

- Hệ thống phân biệt rõ “có bảng mapping” với “bảng mapping đầy đủ”.
- Traceability giữa spec và review checklist trở nên machine-readable hơn.
- Nếu AC mapping bị bỏ trống, đó là lỗi dữ liệu có thể phát hiện sớm.

### 3.6 Giữ logic scoring cũ nhưng nạp section presence đúng schema mới

`EvidenceQualityScoreService` vẫn chấm điểm review checklist chủ yếu qua security/test signals, nhưng `EvidenceQualityScoreRepositoryAdapter` giờ nạp section presence theo danh sách section chuẩn của parser.

Ý nghĩa:

- Thay đổi là tối thiểu, không viết lại rule scoring hiện tại.
- Hệ thống đã có nền dữ liệu đúng để sau này nâng scoring chi tiết hơn nếu cần.
- Đây là bước “align data contract before changing scoring policy”.

### 3.7 Cập nhật test fixture từ format legacy sang format template chuẩn

Các test liên quan đã được đổi từ fixture kiểu:

- `# Security`
- `# Test`

sang format có bảng checklist theo template hiện tại.

Ý nghĩa:

- Test không còn bảo vệ behavior cũ đã lệch template.
- Regression test giờ phản ánh đúng tài liệu chuẩn mà người dùng thực sự đang copy.
- Giảm rủi ro parser pass test nhưng fail trên artifact chuẩn trong thực tế.

## 4. Ý nghĩa nghiệp vụ của các mục chính trong template

### `## 1. Spec / AC`

Nhóm này xác nhận implementation có bám spec, AC, scope và decision gốc hay không.

Ý nghĩa:

- Đây là lớp review chống việc implement lệch yêu cầu.
- Nếu nhóm này còn item mở, merge risk thường ở mức cao.

### `## 2. Thiết kế / Phụ thuộc`

Nhóm này kiểm tra placement của state/model, dependency direction, contract và architecture boundary.

Ý nghĩa:

- Dùng để phát hiện debt kiến trúc ngay trong PR thay vì đợi hậu kiểm.
- Giúp reviewer nhìn được “tính đúng cấu trúc”, không chỉ “chạy được”.

### `## 3. Bảo mật`

Nhóm này xác nhận validation, authorization, logging safety và injection/XSS risks.

Ý nghĩa:

- Đây là một trong các tín hiệu hiện đang được scoring sử dụng trực tiếp.
- Nếu parser hiểu sai nhóm này, điểm evidence quality cũng sai theo.

### `## 8. Kiểm thử`

Nhóm này xác nhận độ phủ test quan trọng nhất cho change.

Ý nghĩa:

- Đây là nhóm còn lại đang được scoring dùng trực tiếp.
- Nó liên kết chặt với test-plan, test-results và khả năng release an toàn.

### `## Bảng ánh xạ AC -> Checklist items`

Bảng này nối yêu cầu trong spec với các hạng mục review cụ thể.

Ý nghĩa:

- Tăng traceability giữa “cần làm gì” và “đã review cái gì”.
- Nếu bảng này thiếu hoặc không đầy đủ, review checklist có thể tồn tại nhưng không đủ khả năng audit.

## 5. Kết luận

Điểm quan trọng nhất của đợt đồng bộ này là:

- hệ thống đã chuyển sang hiểu `review-checklist.md` như một artifact checklist theo template chuẩn mới;
- parser được sửa tối thiểu, chủ yếu ở danh sách section và logic parse bảng;
- downstream đã được nối lại để đọc đúng metadata mới mà không cần thay đổi lớn kiến trúc;
- template chuẩn vẫn được giữ nguyên vai trò nguồn sự thật.

## 6. File kỹ thuật liên quan

- `docs/standards/templates/ticket-template/review-checklist.template.md`
- `EDCAP_BE/src/main/java/com/sdd/platform/domain/service/markdown/core/MarkdownParserCore.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/domain/service/markdown/reviewchecklist/ReviewChecklistMarkdownParser.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/scanner/ArtifactScannerService.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/EvidenceQualityScoreRepositoryAdapter.java`
- `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/quality/EvidenceQualityScoreService.java`
- `EDCAP_BE/src/test/java/com/sdd/platform/domain/service/ReviewChecklistMarkdownParserTest.java`
- `EDCAP_BE/src/test/java/com/sdd/platform/web/rest/ReviewChecklistMarkdownParserControllerTest.java`
- `EDCAP_BE/src/test/UnitTest/java/com/sdd/platform/application/usecase/scanner/ArtifactScannerServiceTest.java`
