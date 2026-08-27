# Sources

**Ticket ID**: SECURITY-FINDING-RESOLUTION-TIME
**Create date**: 2026-08-17
**Author**: Claude (SDD analyst)
**Update date**: 2026-08-17

## Ticket / Issue

| source | link/path | status | note |
|---|---|---|---|
| SECURITY-FINDING-RESOLUTION-TIME | `docs/changes/SECURITY-FINDING-RESOLUTION-TIME/` | Read/Write | Ticket working directory |
| Yêu cầu nghiệp vụ | `docs/changes/SECURITY-FINDING-RESOLUTION-TIME/raw/requirement.md` | Read | Ghi lại quyết định người dùng |

## Requirement / Design Documents

| source | path | status | trust level | note |
|---|---|---|---|---|
| 00_brainstorm | `docs/changes/SECURITY-FINDING-RESOLUTION-TIME/00_brainstorm.md` | Read/Write | High | Lịch sử quyết định |
| Open Issues | `docs/changes/SECURITY-FINDING-RESOLUTION-TIME/open-issues.md` | Read/Write | High | Theo dõi điểm mơ hồ |
| spec-pack | `docs/changes/SECURITY-FINDING-RESOLUTION-TIME/spec-pack.md` | Read | High | Đã clean, công thức mới |
| Wireframe/mockup | — | Không tồn tại | — | Vị trí field do impl-plan quyết định |
| Ticket nền: SECURITY-DASHBOARD | `docs/changes/SECURITY-DASHBOARD/spec-pack.md` | Partial | High | Field bổ sung vào dashboard |
| Template spec-pack chuẩn | `docs/standards/templates/_ticket-template/spec-pack.md` | Read | High | Tham chiếu cấu trúc |
| Template sources | `docs/standards/templates/_ticket-template/sources.md` | Read | High | Tham chiếu cho file này |
| Template 00_brainstorm | `docs/standards/templates/_ticket-template/00_brainstorm.md` | Read | High | Tham chiếu cho brainstorm |
| Kiến trúc hệ thống | `docs/architecture/overview.md`, `service-layer-map.md`, `repository-db-map.md`, `route-api-map.md` | Partial | High | Ưu tiên source code thực tế |
| Coding/testing/security standards | `docs/standards/coding.md`, `backend.md`, `testing.md`, `security.md`, `api-contract.md` | Partial | High | Áp dụng Validation/Security/Test |
| `.claude/rules/*` | `.claude/rules/00-safety.md`, `10-style.md`, `20-architecture.md`, `30-security.md`, `40-testing.md` | Read | High | Ràng buộc bắt buộc |

## Existing Source Code

| area | path | status | purpose |
|---|---|---|---|
| Schema `tbl_fact_security_scan` | `EDCAP_BE/src/main/resources/db/migration/V4__init_shema_v2.sql:740-758` | Read | Nguồn dữ liệu duy nhất |
| Security Dashboard service | `SecurityDashboardService.java` | Read | Service pass-through |
| Security Dashboard models | `SecurityDashboardModels.java` | Read | Mở rộng field `resolutionTime` |
| Security Dashboard adapter | `SecurityDashboardJdbcAdapter.java` | Read | Query raw history, gọi calculator |
| Security Dashboard controller | `SecurityDashboardController.java` | Read | Endpoint GET ticket detail |
| Security Dashboard DTO | `SecurityDashboardDtos.java` | Read | Mở rộng DTO |
| FE Drawer | `SecurityTicketDetailDrawer.tsx` | Read | Vị trí hiển thị field |
| FE types | `types.ts` | Read | Mở rộng type |
| Artifact Scanner flow | `ArtifactScannerService.java` | Read | Tham chiếu kiến trúc |
| Evidence Quality Score flow | `EvidenceQualityScoreService.java` | Read | Tham chiếu kiến trúc |

## Existing Tests

| test type | path | status | note |
|---|---|---|---|
| BE unit test | `SecurityFindingResolutionTimeCalculatorTest.java` | New | Test công thức mới |
| BE adapter test | `SecurityDashboardServiceTest.java` | Read | Không đổi |
| FE test | `SecurityDashboardPage.test.tsx` | Read | Mở rộng test hiển thị |

## External References

Không có. Toàn bộ input là quyết định trực tiếp của người dùng + source code nội bộ.

## Excluded Sources

| source/path | reason |
|---|---|
| `tbl_fact_security_finding` | Bảng orphaned, không dùng |
| `tbl_connector_run` | Không có ticket_id, đo ingestion |
| `tbl_fact_finding` category SECURITY | Bị loại |
| Legacy `finding` | Không liên quan |

## Source Limitations

- Không có wireframe/mockup cho vị trí field.  
- Tài liệu route-api-map lỗi thời, xác nhận endpoint từ source code.  
- Chưa xác nhận index phù hợp cho query `(ticket_id, scan_status, collected_at)`.

## Assumptions

- `tbl_fact_security_scan` tích lũy nhiều bản ghi lịch sử cho cùng ticket theo thời gian.  

