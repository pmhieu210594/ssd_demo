# Implementation Plan - BLACKBOX-COVERAGE

**Ticket ID**: BLACKBOX-COVERAGE  
**Create date**: 2026-09-04  
**Author**: OpenAI (AI-assisted)  
**Update date**: 2026-09-07  

## 1. Implementation Principle

- Reuse the existing artifact-scanner and parser flow, then persist Blackbox Coverage data into a dedicated storage path instead of mixing it with AC-Test Coverage.
- Treat `blackbox-testcases.md` as the design-side source of truth and `test-results.md` as the execution-side source of truth.
- Compute `blackboxCoveragePercent` on the backend from structured counts, while keeping the API field name and DTO contract unchanged.
- Preserve spec rules exactly in service logic: zero denominator returns `0.0`, no weight redistribution, and every percentage is rounded to `1` decimal place.
- Keep the change isolated to BE ingestion, persistence, and dashboard aggregation; do not add a manual UI flow or FE-side recomputation.

## 2. Alternative Plan
| option | summary | pros | cons | decision |
|---|---|---|---|---|
| A | Add dedicated Blackbox Coverage tables, scanner persistence, and QA Dashboard aggregation | Clean separation from AC-Test Coverage, queryable counts, idempotent ingestion, dashboard-ready | Requires additive migration and more moving parts | Selected |
| B | Derive coverage directly from parsed markdown sections on every dashboard request | Lower upfront schema work | Harder SQL, weaker traceability, expensive recomputation, fragile section parsing dependency | Rejected |
| C | Reuse existing AC-Test Coverage tables / fixed viewpoints | Less new storage | Violates spec because observation point != viewpoint and Blackbox Coverage must not change AC-Test Coverage logic | Rejected |

## 3. Reason for Choosing the Alternative Plan

- The implemented source already follows option A end to end: migration `V516`, dedicated persistence port/adapter, scanner persistence, test-result mapping, and QA Dashboard aggregation.
- This keeps Blackbox Coverage isolated from `tbl_fact_test_case` and `tbl_fact_ac_test_coverage`, which matches the ticket rule that AC-Test Coverage behavior must not be changed.
- Structured storage is also the simplest way to support the three formulas (`Design`, `Execution`, `Overall`) without reparsing documents inside dashboard queries.

## 4. Expected Change File
| file | change summary | reason | related AC |
|---|---|---|---|
| `EDCAP_BE/src/main/resources/db/migration/V516__blackbox_coverage_schema.sql` | Add dedicated Blackbox Coverage tables and indexes | Persist structured blackbox design/execution evidence | AC-BLACKBOX-COVERAGE-1,2,4,7 |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/persistence/BlackboxCoveragePersistencePort.java` | Define write-side contract for blackbox cases, mappings, observation points, and results | Keep scanner/parser decoupled from JDBC details | AC-BLACKBOX-COVERAGE-1,2,7 |
| `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/BlackboxCoverageJdbcAdapter.java` | Implement delete-replace and update persistence policy | Idempotent ticket-scoped ingestion | AC-BLACKBOX-COVERAGE-1,2,6,7 |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/scanner/ArtifactScannerService.java` | Parse and persist `blackbox-testcases.md`; clear stale evidence when source disappears | Keep scanner as orchestration entrypoint | AC-BLACKBOX-COVERAGE-1,6,7 |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/docparse/TestResultsParseService.java` | Map test result rows into blackbox execution statuses and persist them | Feed execution coverage from `test-results.md` | AC-BLACKBOX-COVERAGE-2,6 |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/qadashboard/QaDashboardModels.java` | Add `BlackboxCoverageCounts` read model | Carry raw counts into service formula | AC-BLACKBOX-COVERAGE-3,4,5 |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/persistence/QaDashboardRepositoryPort.java` | Add `findBlackboxCoverageCounts` read-side contract | Separate dashboard use case from SQL details | AC-BLACKBOX-COVERAGE-3,4,5 |
| `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/QaDashboardJdbcAdapter.java` | Aggregate AC, observation-point, and priority counts from dedicated tables | Provide raw inputs for design/execution/overall formulas | AC-BLACKBOX-COVERAGE-3,4,5,7 |
| `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/qadashboard/QaDashboardService.java` | Compute final `blackboxCoveragePercent` using weighted formulas and rounding rules | Surface overall coverage in dashboard summary | AC-BLACKBOX-COVERAGE-3,4,5,6 |
| `EDCAP_BE/src/test/java/com/sdd/platform/application/usecase/qadashboard/QaDashboardServiceTest.java` | Add formula and zero-denominator tests | Lock the spec example and edge cases | AC-BLACKBOX-COVERAGE-5,6 |
| `EDCAP_BE/src/test/java/com/sdd/platform/web/rest/QaDashboardControllerTest.java` | Keep controller mapping contract verified | Field remains in response DTO without contract change | AC-BLACKBOX-COVERAGE-8 |

## 5. Class / Function / Method to Add or Modify
| target | action | input | output | note |
|---|---|---|---|---|
| `BlackboxCoveragePersistencePort.replaceBlackboxCases(...)` | Add | ticket, snapshot, case key, priority | persisted case rows | Design-side case source |
| `BlackboxCoveragePersistencePort.replaceBlackboxCaseAcMappings(...)` | Add | ticket, case-to-AC pairs | persisted M:N mapping | Supports AC coverage |
| `BlackboxCoveragePersistencePort.replaceBlackboxObservationPoints(...)` | Add | ticket, snapshot, observation points | persisted observation rows | Observation denominator source |
| `BlackboxCoveragePersistencePort.replaceBlackboxCaseObservationLinks(...)` | Add | ticket, case-to-observation links | persisted DIRECT / RELATED links | Only `DIRECT` counts for observation coverage |
| `BlackboxCoveragePersistencePort.upsertBlackboxCaseResults(...)` | Add | ticket, case result rows, result snapshot | updated execution statuses | Unmatched keys are intentionally ignored |
| `ArtifactScannerService.persistBlackboxTestcasesParse(...)` | Modify / add | parsed blackbox-testcases document | persisted cases, mappings, observation points, links | Scanner owns design-side persistence orchestration |
| `TestResultsParseService.toBlackboxResults(...)` | Add | generic test result rows | blackbox execution result rows | `SUCCESS -> PASS`, `FAILED -> FAIL`, `SKIPPED -> SKIP`, else `NOT_RUN` |
| `QaDashboardRepositoryPort.findBlackboxCoverageCounts(...)` | Add | `QaFilter` | `BlackboxCoverageCounts` | Read-side boundary |
| `QaDashboardJdbcAdapter.findBlackboxCoverageCounts(...)` | Add | `QaFilter` | raw AC / observation / priority counts | Uses dedicated blackbox tables only; AC execution requires at least one mapped case and all mapped cases `PASS` |
| `QaDashboardService.computeBlackboxOverallCoverage(...)` | Add | `BlackboxCoverageCounts` | final `double` percent | Applies spec formulas and rounding |

## 6. SQL / Query / Repository Policy

- Use dedicated blackbox tables introduced by `V516__blackbox_coverage_schema.sql`:
  - `tbl_fact_blackbox_case`
  - `tbl_fact_blackbox_case_ac`
  - `tbl_fact_blackbox_observation_point`
  - `tbl_fact_blackbox_case_observation`
- Write-side policy is ticket-scoped and idempotent:
  - design-side entities use delete-then-reinsert per ticket
  - execution-side results update existing blackbox case rows only
- Query-side policy for dashboard:
  - `AC Coverage (Design)` = count active AC with at least one mapped blackbox case
  - `AC Coverage (Execution)` = count active AC that have at least one mapped blackbox case and no mapped case with status other than `PASS`
  - `Observation Coverage` uses only `coverage_type = 'DIRECT'`
  - `Priority Coverage` uses `P0 = 5`, `P1 = 3`, `P2 = 1`
- Do not read from `tbl_fact_test_case`, `tbl_fact_ac_test_coverage`, or fixed test viewpoints when computing Blackbox Coverage.
- Keep all SQL parameterized and filterable through the existing `QaFilter`.

## 7. Validation / Error / Logging Policy

- Ignore blank or null case keys / AC keys / observation keys during persistence to avoid creating invalid rows.
- Normalize priority and execution status to uppercase before writing.
- When `blackbox-testcases.md` is removed, delete blackbox evidence for that ticket to avoid stale dashboard data.
- When `test-results.md` contains keys that do not match blackbox cases, leave blackbox rows untouched instead of overwriting unrelated AC-Test Coverage evidence.
- Keep parser warnings in logs and parse failures in evidence events; do not introduce a separate logging channel just for this metric.
- Service-level formula rules must stay stable:
  - zero denominator returns `0.0`
  - weights are not redistributed
  - each percentage is rounded to `1` decimal place before the next weighted sum

## 8. Migration / Rollback Policy

- Migration policy: additive only. The implemented source adds `V516__blackbox_coverage_schema.sql` and does not alter existing AC-Test Coverage tables.
- Runtime rollback:
  - revert scanner persistence and dashboard aggregation code
  - restore `blackboxCoveragePercent` to placeholder behavior if needed
- DB rollback:
  - only drop the four dedicated blackbox tables if the whole feature is being fully reverted
  - do not touch existing `tbl_fact_test_case`, `tbl_fact_ac_test_coverage`, or ticket master tables

## 9. Step Implementation
| step | action | target file | verification | stop condition |
|---|---|---|---|---|
| 1 | Add additive schema for blackbox cases, AC mapping, observation points, and observation links | `V516__blackbox_coverage_schema.sql` | Migration applies cleanly and tables/indexes exist | Stop if schema must overwrite existing AC-Test Coverage tables |
| 2 | Introduce dedicated write-side persistence contract and JDBC adapter | `BlackboxCoveragePersistencePort.java`, `BlackboxCoverageJdbcAdapter.java` | Compile and repository wiring succeed | Stop if ingestion cannot stay ticket-scoped and idempotent |
| 3 | Persist design-side data from `blackbox-testcases.md` through scanner orchestration | `ArtifactScannerService.java` | Scanner parse populates cases, AC mappings, observation points, and DIRECT/RELATED links | Stop if parser output cannot distinguish observation points from viewpoints |
| 4 | Persist execution-side statuses from `test-results.md` into blackbox cases | `TestResultsParseService.java` | PASS / FAIL / SKIP rows update matching blackbox cases | Stop if test result keys are incompatible with blackbox case keys |
| 5 | Add dashboard read-side raw count aggregation | `QaDashboardModels.java`, `QaDashboardRepositoryPort.java`, `QaDashboardJdbcAdapter.java` | Repository returns counts for AC, observation, and priority components | Stop if query needs to fall back to viewpoint-based logic |
| 6 | Compute overall blackbox coverage in dashboard service and keep DTO contract unchanged | `QaDashboardService.java` | Spec example returns `89.4`; zero denominators return `0.0` | Stop if API contract must change or null/NaN would be returned |
| 7 | Lock behavior with focused unit/controller tests | `QaDashboardServiceTest.java`, `QaDashboardControllerTest.java` | Targeted tests pass | Stop if tests reveal formula drift from the spec |

## 10. How to Verify Each Step

- Step 1: run migration on a local/dev database and confirm the four blackbox tables plus indexes are created.
- Step 2: compile backend and verify Spring wiring resolves the new port/adapter.
- Step 3: run an artifact scan on a ticket containing `blackbox-testcases.md`, then inspect blackbox tables for case, AC-mapping, and observation-point rows.
- Step 4: parse a `test-results.md` sample and confirm matching blackbox cases receive `PASS`, `FAIL`, `SKIP`, or remain `NOT_RUN`.
- Step 5: run repository-level verification against seeded data and confirm returned counts match ticket fixtures.
- Step 6: run `QaDashboardServiceTest` and confirm the worked example `10,8,9,5,5,5,22,18 -> 89.4`.
- Step 7: run `QaDashboardControllerTest` and confirm the summary DTO still exposes `blackboxCoveragePercent`.

Suggested backend verification commands:

```bash
mvn "-Dtest=QaDashboardServiceTest,QaDashboardControllerTest,QaDashboardJdbcAdapterTest" test
```

## 11. Corresponding AC Table
| AC ID | implementation point | verification |
|---|---|---|
| AC-BLACKBOX-COVERAGE-1 | `ArtifactScannerService` + `BlackboxCoverageJdbcAdapter` persist case, AC-mapping, and observation data from `blackbox-testcases.md` | Scanner run + table inspection |
| AC-BLACKBOX-COVERAGE-2 | `TestResultsParseService` maps `test-results.md` statuses into blackbox execution statuses | Parse test data and inspect `execution_status` |
| AC-BLACKBOX-COVERAGE-3 | `QaDashboardJdbcAdapter.findBlackboxCoverageCounts(...)` supplies design/execution component counts | Repository verification with seeded data |
| AC-BLACKBOX-COVERAGE-4 | `QaDashboardJdbcAdapter` applies priority weights and DIRECT observation rules only | SQL verification against fixture dataset |
| AC-BLACKBOX-COVERAGE-5 | `QaDashboardService.computeBlackboxOverallCoverage(...)` returns the final `blackboxCoveragePercent` | `QaDashboardServiceTest` worked-example assertion |
| AC-BLACKBOX-COVERAGE-6 | Zero-denominator, status semantics, and rounding rules stay aligned with the spec | `QaDashboardServiceTest` zero-state assertions |
| AC-BLACKBOX-COVERAGE-7 | Blackbox Coverage remains isolated from AC-Test Coverage tables and formulas | Code review of ports/adapters/queries |
| AC-BLACKBOX-COVERAGE-8 | Dashboard response contract keeps existing `blackboxCoveragePercent` field | `QaDashboardControllerTest` DTO mapping assertion |

## 12. Stop / Ask Condition

- Stop if the team does not approve additive schema for dedicated blackbox storage.
- Stop if `test-results.md` test case identifiers do not match blackbox case keys in practice.
- Stop if product direction changes and requires exposing `Design Coverage` / `Execution Coverage` separately in the API instead of only `blackboxCoveragePercent`.
- Stop if implementation must reuse fixed dashboard viewpoints, because that would conflict with the ticket rule that observation point and viewpoint are different concepts.

## 13. Do Not Do This Ticket

- Do not compute Blackbox Coverage from fixed test viewpoints.
- Do not merge Blackbox Coverage into AC-Test Coverage tables or formulas.
- Do not return `null`, `NaN`, or redistributed weights for zero-denominator cases.
- Do not add a manual UI or FE-side formula engine for this metric.
- Do not use `Overall Coverage` as release-gate logic in runtime behavior.
- Do not overwrite unmatched non-blackbox test results when persisting execution status.

## 14. Open Related Issues

- The implemented source uses additive schema migration even though the early spec wording said “không thay đổi schema DB”; that document should be aligned with the implemented runtime design.
- End-to-end scanner integration tests for `blackbox-testcases.md -> test-results.md -> dashboard summary` would strengthen confidence beyond the current service/controller tests.
- If the dashboard later needs to show `Design Coverage`, `Execution Coverage`, or component percentages separately, a follow-up ticket should extend the read model/API explicitly instead of deriving them on the FE side.
