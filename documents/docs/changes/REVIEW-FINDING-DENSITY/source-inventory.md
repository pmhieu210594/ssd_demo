# Source Inventory

**Ticket ID**: REVIEW-FINDING-DENSITY
**Create date**: 2026-09-10 22:30:00
**Author**: nvt_dung
**Update date**: 2026-09-10 22:30:00

| area | path | type | owner | read status | note |
|---|---|---|---|---|---|
| BE ingestion service | `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/ingestion/GitPrMetadataCollectorService.java` | Java class | BE | Read (228-392, 683-693) | `persistPullRequest` — entry point cho finding classification; xóa-chèn-lại review/comment dòng 342 |
| BE ingestion adapter | `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/GitPrMetadataCollectorJdbcAdapter.java` | Java class | BE | Read (208-236, 386-519) | `upsertPullRequestChangedFile`, `deleteReviewsByPrId`, `insertReview`, `insertReviewComment`, member lookup |
| BE GitHub REST adapter | `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/github/GithubPullRequestMetadataAdapter.java` | Java class | BE | Read (import, `githubWebClient` usage, `ChangedFileSnapshot`) | REST-only; no GraphQL method exists |
| BE GitHub port | `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/integration/GithubPullRequestMetadataPort.java` | Java interface | BE | Read (full — 2 methods) | Confirmed: không có method GraphQL nào |
| BE WebClient config | `EDCAP_BE/src/main/java/com/sdd/platform/config/WebClientConfig.java` | Java `@Configuration` | BE | Read (full, 73 dòng) | 1 `WebClient` bean / external API (`githubWebClient`, `jiraWebClient`, `circleciWebClient`); GraphQL client mới nên tái dùng cùng bean `githubWebClient` (cùng base URL family, cùng token) hoặc thêm bean `githubGraphqlWebClient` riêng nếu base URL khác (`/graphql` khác `/repos/...`) |
| BE rounding pattern | `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/quality/EvidenceQualityScoreService.java` | Java class | BE | Read (480-486) | `scoreByRatio` — pattern BigDecimal rounding tham chiếu |
| BE isolated writer pattern | `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/ingestion/AiFindingStatWriter.java` | Java class | BE | Read (full, 30 dòng) | Pattern `@Component` + `REQUIRES_NEW` cho Finding writer mới |
| BE PM Dashboard controller | `EDCAP_BE/src/main/java/com/sdd/platform/web/rest/PmDashboardController.java` | Java `@RestController` | BE | Read (1-100) | Pattern route/DTO cho endpoint density mới; dùng `@CurrentUser AuthUserContext caller` |
| BE exception handling | `EDCAP_BE/src/main/java/com/sdd/platform/web/exception/GlobalExceptionHandler.java` | Java `@RestControllerAdvice` | BE | Read (mapping table, qua Explore) | Endpoint mới phải dùng exception có sẵn (`NotFoundException`, v.v.) |
| DB migration — schema hiện tại | `EDCAP_BE/src/main/resources/db/migration/V4__init_shema_v2.sql` | SQL migration | DB | Read (dòng 49, 560-631) | Enum dùng chung gồm `OPEN, ACCEPTED, REJECTED, RESOLVED, FALSE_POSITIVE, WONT_FIX`; feature này chỉ sử dụng `OPEN` và `RESOLVED` |
| DB migration — changed file | `EDCAP_BE/src/main/resources/db/migration/V181__git_pr_metadata_collector_schema.sql` | SQL migration | DB | Read (full) | `tbl_fact_pull_request_changed_file`, unique key hiện tại `(pr_id, file_path_hash)` |
| DB migration — thư mục | `EDCAP_BE/src/main/resources/db/migration/` | Thư mục | DB | Read (danh sách + numeric sort) | Version cao nhất hiện có: **V518** → migration mới của ticket này là **V519** |
| FE display pattern | `EDCAP_FE/src/pages/pm-dashboard/components/SummaryCards.tsx` | React TSX | FE | Read (79-83, 121-126, qua Explore) | `formatFirstCiPassRate` — pattern N/A + rounding tham chiếu |
| FE Ticket Detail | `EDCAP_FE/src/pages/pm-dashboard/components/TicketDetailDrawer.tsx` | React TSX | FE | Read (355-450, 452-544, 746-882, qua Explore) | Điểm chèn thêm density card/badge + `useQuery` mới |
| Test — ingestion service | `EDCAP_BE/src/test/.../GitPrMetadataCollectorServiceTest.java` | JUnit | BE | Read (qua Explore, danh sách test) | 10 test methods, cover ticket resolution/review state normalize |
| Test — ingestion adapter | `EDCAP_BE/src/test/.../GitPrMetadataCollectorJdbcAdapterTest.java` | JUnit | BE | Read (full, 104 dòng) | Chỉ 2 test, **không cover** `upsertPullRequestChangedFile`/review CRUD — gap cần vá trước khi đổi unique key |
| Test — writer pattern | `EDCAP_BE/src/test/.../AiFindingStatWriterTest.java` | JUnit | BE | Read (qua Explore, full 27 dòng) | 1 test delegation — mẫu test cho Finding writer mới |
| Test — rounding pattern | `EDCAP_BE/src/test/.../EvidenceQualityScoreServiceTest.java` | JUnit | BE | Read (qua Explore, tổng quan) | Test suite lớn cho ratio/rounding — mẫu cho Density service test |
| Architecture docs | `docs/architecture/service-layer-map.md`, `repository-db-map.md`, `route-api-map.md`, `entrypoint-map.md`, `fe-be-contract-map.md` | Markdown | Docs | Read (qua Explore, grep symbol) | **Stale** — không đề cập `GitPrMetadataCollectorService`, `tbl_fact_finding`, `tbl_fact_review`, `tbl_fact_pull_request_changed_file`, `EvidenceQualityScoreService`, `AiFindingStatWriter` |
| Ticket template | `docs/standards/templates/_ticket-template/impact-analysis.md`, `impl-plan.md`, `source-availability.md`, `source-inventory.md` | Markdown template | Docs | Read (full) | Khung bắt buộc dùng cho 4 artifact Phase 3 |

## Important Files

- `GitPrMetadataCollectorService.java` — nơi duy nhất cần gắn thêm bước finding classification vào flow ingestion hiện có.
- `GitPrMetadataCollectorJdbcAdapter.java` — nơi duy nhất cần sửa SQL cho `head_sha` và thêm SQL mới cho Finding writer.
- `V181__git_pr_metadata_collector_schema.sql` — tham chiếu bắt buộc để viết migration V519 (giữ đúng convention cột/index/constraint).
- `PmDashboardController.java` — tham chiếu bắt buộc cho route/DTO convention của endpoint density mới.

## Generated / Excluded Files

- Không có file generated/build output nào liên quan trực tiếp (không đọc `target/`, `dist/`, `node_modules/`).

## Missing Files

- Chưa có file nào cho: GraphQL client (`infrastructure/github/*ReviewThread*`), Finding classifier/writer, Density service, Density controller/DTO, migration `V519__*.sql` — tất cả là **file mới cần tạo ở implementation**, không phải "missing" do lỗi đọc.
- Chưa có test file nào cho các thành phần mới ở trên (đương nhiên, vì code chưa tồn tại).
