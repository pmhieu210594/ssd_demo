# Test Results

**Ticket ID**: PHASE-DWELL-TIME
**Create date**: 2026-08-21
**Author**: Principal Test Engineer / Bug Hunter (Claude)
**Update date**: 2026-08-21

## 1. Execution Environment

| item | value |
|---|---|
| BE | Java 21, Maven (offline `-o`), JUnit 5 + Mockito, no real Postgres available in this session |
| FE | Node/npm, Vitest 3.2.6, `@testing-library/react`, jsdom |
| OS | Windows 10, PowerShell/Git Bash |
| DB | Not available in this session — all new BE tests are mock-`NamedParameterJdbcTemplate` unit tests, no Testcontainers/real Postgres (see `test-plan.md` § Mục tiêu) |

## 2. Executed Command

| command | result | log/evidence | note |
|---|---|---|---|
| `mvn -o -Dtest=PmDashboardJdbcAdapterPhaseDwellTimeTest test` (EDCAP_BE) | PASS — 4/4 | `Tests run: 4, Failures: 0, Errors: 0` | New: AC-1/AC-3/AC-4/AC-10 SQL-shape tests + negative-duration bug reproduction |
| `mvn -o -Dtest=ArtifactScannerJdbcAdapterTest,ArtifactDocumentDateMigrationTest,PmDashboardJdbcAdapterPhaseDwellTimeTest test` (EDCAP_BE) | PASS — 8/8 | `Tests run: 8, Failures: 0, Errors: 0` | New tests together (AC-6, AC-9) |
| `mvn -o test` (EDCAP_BE, full suite) | PASS — 603/603 | `Tests run: 603, Failures: 0, Errors: 0` `BUILD SUCCESS` | +8 vs previous baseline of 596 (see `self-review.md` prior entry) — zero regressions |
| `npx vitest run "TicketDetailDrawer"` (EDCAP_FE) | PASS — 3/3 (in this file) | `✓ TicketDetailDrawer.test.tsx (3 tests)` | Includes 1 `it.fails` bug-reproduction test (counts as passing because it failed as expected) |
| `npx vitest run "phaseDwellTimeLocale"` (EDCAP_FE) | PASS — 3/3 | `✓ phaseDwellTimeLocale.test.ts (3 tests)` | New: reads real `en/ja/vi` locale.json files directly, no mocked `t()` |
| `npx tsc --noEmit` (EDCAP_FE) | PASS, exit 0 | — | Full typecheck after all new tests added |
| `npx vitest run` (EDCAP_FE, full suite) | PASS — 344/344, 52 files | `Test Files 52 passed (52)` `Tests 344 passed (344)` | +5 vs previous baseline of 339/51 files — zero regressions |
| `mvn -o test` (EDCAP_BE, full suite, sau fix vòng 2) | PASS — 603/603 | `Tests run: 603, Failures: 0, Errors: 0` `BUILD SUCCESS` | Cùng 603 test như vòng 1 — không thêm test mới, chỉ flip 1 test từ pin-known-defect sang assert hành vi đã fix |
| `npx tsc --noEmit` (EDCAP_FE, sau fix vòng 2) | PASS, exit 0 | — | — |
| `npx vitest run` (EDCAP_FE, full suite, sau fix vòng 2) | PASS — 344/344, 52 files | `Test Files 52 passed (52)` `Tests 344 passed (344)` | Cùng 344 test như vòng 1 — chỉ flip `it.fails(...)` sang `it(...)` bình thường |

## 3. Summary of Results

**Vòng 1 (bug-hunting)**: đã thêm **13 test mới** (8 BE + 5 FE), tất cả PASS, không có regression
trên toàn bộ test suite hiện có (BE 603/603, FE 344/344). Trong số 13 test mới, **2 test là
bug-reproduction test** cố ý pin lại hành vi hiện tại (một phần bị lỗi/thiếu robustness) thay vì
assert "đúng" — theo đúng chỉ thị "không làm xanh bug bằng cách lấp lỗi". Cả 2 phát hiện đã được
đưa vào `open-issues.md` (`OI-PHASE-DWELL-TIME-14`, `OI-PHASE-DWELL-TIME-15`) để chờ quyết định.

**Vòng 2 (fix, cùng phiên)**: người dùng đã duyệt phương án khuyến nghị cho cả 2 bug (xem
`open-issues.md` Resolution Log, mục 2026-08-21). Đã fix cả 2, và **flip 2 reproduction test đã
viết ở vòng 1 thành regression test bình thường** xác nhận đúng hành vi mới — không viết test mới,
không đổi cấu trúc/số lượng test khác. Toàn bộ suite vẫn PASS 100% (BE 603/603, FE 344/344).

## 4. List of Passes

| TC ID | test | result | note |
|---|---|---|---|
| TC-PHASE-DWELL-TIME-1 | `findPhaseDwellTime_query_isAnchoredOnAllPhasesAndFiltersToTheSevenDisplayedOnes` | PASS | AC-1 — SQL anchored on `tbl_dim_phase`, đúng 7 `phase_code`, loại `0-A/0-B/2/9`, `ORDER BY phase_order ASC` |
| TC-PHASE-DWELL-TIME-2 | `findPhaseDwellTime_query_excludesUnpairedFilesInsideTheJoinOnClause_notInWhere` | PASS | AC-3/AC-4 — điều kiện `IS NOT NULL` nằm trong `ON`, không phải `WHERE` (bảo vệ LEFT JOIN không bị biến thành INNER JOIN ngầm) |
| TC-PHASE-DWELL-TIME-3 | `findDetail_whenPhaseDwellTimeQueryThrows_stillReturnsDetailWithEmptyPhaseDwellTime` | PASS | AC-10 — lỗi/timeout ở subquery dwell-time không làm hỏng `/detail`, trả `phaseDwellTime=[]` |
| TC-PHASE-DWELL-TIME-4 | `upsertArtifactDocumentDates_usesOnConflictDoNothing_notDoUpdate` | PASS | AC-6 — ghi đúng `ON CONFLICT (artifact_snapshot_id) DO NOTHING`, không phải `DO UPDATE` |
| TC-PHASE-DWELL-TIME-4b | `upsertArtifactDocumentDates_withEmptyList_doesNotIssueAnyUpdate` | PASS | Boundary — danh sách rỗng không phát sinh câu lệnh SQL nào (double-submit/empty-input an toàn) |
| TC-PHASE-DWELL-TIME-5 | `v512_migration_isAdditiveOnly_andHasRescanIdempotencyConstraint` | PASS | AC-9 — không có `ALTER TABLE`/`DROP TABLE`/`DROP COLUMN`; có `UNIQUE(artifact_snapshot_id)` (điều kiện cần cho AC-6) |
| TC-PHASE-DWELL-TIME-6 | `renders phase dwell time in ascending phaseOrder even when the API returns entries out of order` | PASS | AC-1 (FE) — input cố ý xáo trộn thứ tự, assert DOM render đúng thứ tự tăng dần qua `compareDocumentPosition` (không phụ thuộc CSS class nội bộ) |
| TC-PHASE-DWELL-TIME-7 | `phaseDwellTimeLocale.test.ts` × 3 (en/ja/vi) | PASS | AC-8 — đọc trực tiếp file `locale.json` thật, so khớp byte-for-byte với chuỗi trong `spec-pack.md §5`, phát hiện được vấn đề BOM (đã xử lý trong test, không phải bug) |
| — | `findPhaseDwellTime_rowMapper_negativeSumRendersAsDash_notAMalformedNegativeString` (đã flip từ reproduction test) | PASS | Xác nhận fix BUG-PHASE-DWELL-TIME-1 — tổng âm nay render `null`/`"-"`, xem mục 6 |
| — | `degrades gracefully instead of crashing the whole drawer when phaseDwellTime is missing from the API response` (đã flip từ `it.fails`) | PASS | Xác nhận fix BUG-PHASE-DWELL-TIME-2 — thiếu field không còn crash Drawer, xem mục 6 |

## 5. List of Fails

*(Không có test nào fail ngoài dự kiến. 2 mục ở mục 6/7 là bug-hunt cố ý — bản thân các test đó
đều ở trạng thái PASS theo đúng thiết kế của chúng, không nằm trong danh sách Fail.)*

| TC ID | test | cause | action | status |
|---|---|---|---|---|

## 6. Bugs Fixed

Người dùng đã duyệt phương án khuyến nghị cho cả 2 bug tìm thấy ở vòng 1 (xem
`open-issues.md` Resolution Log, mục 2026-08-21). Đã fix cả 2 trong cùng phiên làm việc.

| bug | fix | evidence |
|---|---|---|
| BUG-PHASE-DWELL-TIME-1 (`OI-14`): tổng dwell time âm hiển thị `"-1:00:00"` (không zero-pad, âm) thay vì `"-"` | `PmDashboardJdbcAdapter.formatDwellTimeSeconds` thêm nhánh `if (dwellSeconds < 0)`: trả `null` (→ FE render `"-"`) + log `WARN` kèm `ticketId`/`phaseCode`/`dwellSeconds` để phát hiện dữ liệu header bị đảo | Test đã flip: `PmDashboardJdbcAdapterPhaseDwellTimeTest#findPhaseDwellTime_rowMapper_negativeSumRendersAsDash_notAMalformedNegativeString` — PASS, assert `item.dwellTime() == null` cho tổng `-3600` giây |
| BUG-PHASE-DWELL-TIME-2 (`OI-15`): thiếu field `phaseDwellTime` trong response làm crash toàn bộ `TicketDetailDrawer` | `PhaseDwellTimeCard` (`TicketDetailDrawer.tsx`): `[...detail.phaseDwellTime]` → `[...(detail.phaseDwellTime ?? [])]` | Test đã flip: `TicketDetailDrawer.test.tsx` → `"degrades gracefully instead of crashing the whole drawer when phaseDwellTime is missing from the API response"` — PASS, xác nhận Card mount rỗng (không phase nào) và các Card khác (`"Open issues"`) vẫn còn nguyên |

## 7. Not yet fixed / Pending

*(Không còn — cả 2 bug tìm thấy ở vòng 1 (`BUG-PHASE-DWELL-TIME-1`, `BUG-PHASE-DWELL-TIME-2`) đã
được fix ở vòng 2, xem mục 6. `open-issues.md` `OI-PHASE-DWELL-TIME-14`/`15` đã RESOLVED.)*

## 8. Test cannot be executed and reason

| TC ID | reason | risk | alternative evidence |
|---|---|---|---|
| DB IT thật cho AC-2 (Σ nhiều file/1 phase) và AC-6 (rescan) trên Postgres | Không có Testcontainers/Postgres thật trong môi trường làm việc phiên này (đã xác nhận `grep Testcontainers` = 0 kết quả trong repo, xem `test-plan.md`) | Trung bình — cú pháp SQL đúng theo mock nhưng ngữ nghĩa runtime thật (`EXTRACT(EPOCH...)`, `ON CONFLICT`) chưa được xác nhận trên Postgres thật | BE UT mock-SQL (TC-1, TC-2, TC-4, TC-5) bảo vệ cấu trúc câu lệnh; khuyến nghị chạy tay 1 lần trên dev trước release rộng (đã ghi ở `test-plan.md` § Rủi ro còn lại) |
| ArchUnit `ArchitectureTest` | File không tồn tại trong repo (đã xác nhận bằng `Glob`/`find`, không phải do phiên làm việc này xoá) | Thấp — đã tự rà layer boundary bằng tay khi implement (`self-review.md`) | Không có test tự động thay thế trong phiên này; đây là gap tiền nhiệm, không phát sinh từ ticket |

## 9. Remaining risk

- **BUG-PHASE-DWELL-TIME-1** và **BUG-PHASE-DWELL-TIME-2** — **ĐÃ FIX** (xem mục 6), không còn
  là rủi ro mở. `open-issues.md` `OI-PHASE-DWELL-TIME-14`/`15` đã RESOLVED.
- Race condition thật giữa 2 scan run đồng thời — vẫn chưa test được (cần DB thật + đa luồng),
  đã ghi nhận từ `test-plan.md`, không lặp lại chi tiết ở đây.
- Gap tiền nhiệm (API IT qua Spring Security filter chain thật cho PM Dashboard, ArchUnit không
  tồn tại) — không phát sinh từ ticket này, đã ghi ở `test-plan.md`.
- Vẫn chưa chạy được integration test thật trên Postgres cho AC-2 (cộng dồn)/AC-6 (rescan runtime
  semantics) — xem mục 8, không đổi so với vòng 1.

## 10. Final Test Verdict

- **PASS** (nâng từ PARTIAL lên PASS sau khi fix cả 2 bug ở vòng 2) — Toàn bộ test mới + test
  hiện có đều PASS, không regression (BE 603/603, FE 344/344). Cả 2 defect thật tìm được ở vòng 1
  bug-hunting (negative dwell time formatting; FE crash khi thiếu field) đã được người dùng duyệt
  phương án và fix xong, 2 reproduction test tương ứng đã được flip thành regression test bình
  thường xác nhận đúng hành vi mới. Rủi ro còn lại (integration test Postgres thật cho AC-2/AC-6,
  ArchUnit không tồn tại, race condition scan đồng thời) là rủi ro đã biết từ trước, không chặn
  merge, đã ghi ở mục 8/9 và `test-plan.md`.
