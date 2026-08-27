# Test Results

**Ticket ID**: LOGIN  
**Create date**: 2026-06-11  
**Author**: Codex  
**Update date**: 2026-06-11  

## 1. Execution Environment

| item | value |
|---|---|
| Scope | Phase 6 LOGIN test plan, test code additions, execution evidence, and Phase 8 final roll-up |
| Date/time | 2026-06-11 Asia/Bangkok |
| FE runtime | Node/npm project under `EDCAP_FE`; Vitest 3.2.6 observed in output |
| BE runtime | Maven project under `EDCAP_BE`; Java 21.0.10 and Spring Boot 3.4.1 observed in output |
| Browser/E2E | Playwright targeted login spec with mocked auth API |
| Rules availability | `EDCAP_FE/.claude/rules` was not present in the readable workspace |

## 2. Executed Command

| command | result | log/evidence | note |
|---|---|---|---|
| `npx vitest run src/__tests__/auth src/__tests__/lib/api.test.ts --reporter=dot` | PASS | 3 test files, 17 tests passed, duration 5.57s | Ran outside sandbox because Vite config loading is blocked inside sandbox. |
| `mvn test '-Dtest=AuthServiceTest,AuthTokenServiceTest,AuthApiIntegrationTest'` | PASS | Final run: 15 tests, 0 failures, 0 errors, build success, total time 8.172s | Ran outside sandbox because Maven/network access is required. Mockito dynamic-agent warning observed but non-failing. |
| `npm run -s build` | PASS | Vite built successfully, 3259 modules transformed, built in 4.48s | Includes `tsc && vite build`; ran outside sandbox because Vite config loading is blocked inside sandbox. |
| `npx playwright test e2e_tests/tests/login.spec.ts --reporter=line` | PASS | 3 tests passed in 4.0s | Starts local Vite server and uses mocked API routes. |

## 3. Summary of Results

Phase 6 added five focused tests and all targeted verification passed. FE auth/API unit coverage increased from 14 to 17 passing tests. BE auth/token/API targeted coverage increased from 13 to 15 passing tests. FE build/typecheck passed. Mocked Playwright login E2E passed for standard user, admin user, and invalid credentials. Phase 8 did not rerun tests because no production code or test code changed after Phase 6; this file is retained as the final test evidence roll-up.

## 4. List of Passes

| test | result | note |
|---|---|---|
| `LoginPage` required fields, password mask/toggle, pending submit, localized error, redirects | PASS | Covers AC-1 to AC-5, AC-8, AC-12 to AC-14. |
| `useAuth` current user and logout behavior | PASS | Covers AC-15 and AC-18, including API-failure logout local clear. |
| FE API helper token/error behavior | PASS | Covers bearer header, 204 handling, JSON body, error code/trace, token helper persistence. |
| `AuthServiceTest` | PASS | 5 tests passed; covers success, trim, invalid credential, inactive account, failure handling, reset/revoke. |
| `AuthTokenServiceTest` | PASS | 4 tests passed; covers issue/parse, expired token, tampered token, missing identity. |
| `AuthApiIntegrationTest` | PASS | 6 tests passed; covers login response, username 101 validation, no-token `/auth/me`, `/auth/me`, `/me`, logout 204. |
| FE build/typecheck | PASS | `npm run -s build` completed successfully. |
| Playwright login E2E | PASS | Standard login, admin login, invalid credential journey passed with mocked API. |

## 5. List of Fails

| test | cause | action | status |
|---|---|---|---|
| Initial sandbox Vitest/build attempts | Sandbox could not read paths required by Vite config loading. | Re-ran with approved escalation. | Resolved |
| Initial sandbox Maven attempt in Phase 5 | Maven Central access was blocked by sandbox/network restriction. | Re-ran with approved escalation. | Resolved |

## 6. Bugs Fixed

| bug | fix | evidence |
|---|---|---|
| No production bug found in Phase 6 | Added test coverage only; production source was not changed. | FE tests, BE tests, FE build, and Playwright all passed. |

## 7. Not yet fixed / Pending

- Full real BE revoked-token request through running server/session repository is not covered by the current targeted integration test.
- Full real DB/browser account-state flow is not covered by E2E; BE unit test covers login behavior.
- Black-box/manual security review for secret leakage, browser storage posture, and operation procedures remains pending.
- Refresh-token policy, email exposure, non-admin landing route, `returnUrl`, username case-sensitivity, audit logging, and production token storage remain human decisions.

## 8. Test cannot be executed and reason

| test/command | reason | risk | alternative evidence |
|---|---|---|---|
| Production-data login test | Production credentials/data are forbidden. | Real environment drift may remain undiscovered. | Synthetic unit/integration/E2E tests and human QA in a safe staging environment. |
| Full real revoked-token E2E against BE | Requires controlled seeded BE session state beyond current mocked FE E2E. | Revoked-token integration risk. | `AuthTokenServiceTest`, bearer filter source review, token-session repository review, and API no-token test. |
| Real browser account-state flow | Requires stable seeded DB user and consistent cleanup. | Browser/DB behavior drift. | `AuthServiceTest` plus migration review. |
| Audit/unlock/cleanup tests | Behavior is not specified for MVP and remains open. | Operation/compliance risk. | Track in human decisions/open issues. |

## 9. Remaining risk

Remaining risk is concentrated in production security and operations policy, not in the targeted LOGIN code paths tested this phase. The largest unresolved items are refresh-token policy, localStorage token posture, email exposure, real revoked-token server integration, and audit/cleanup operations.

## 10. Final Test Verdict

- PASS_WITH_PENDING_HUMAN_SECURITY_AND_OPERATION_DECISIONS
