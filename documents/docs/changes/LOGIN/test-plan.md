# Test Plan

**Ticket ID**: LOGIN  
**Create date**: 2026-06-11  
**Author**: Codex  
**Update date**: 2026-06-15  

## 1. Purpose

Define the verification scope for LOGIN MVP using the shared ticket-template structure.

This plan maps each acceptance criterion to the test types used in the LOGIN package, separates reused coverage from new test code, and records runtime prerequisites and test-data rules. The document reflects the current source/test intent and keeps runtime evidence distinct from documentation work.

## 2. AC Matrix -> Test Type

| AC ID | FE UT | BE UT | API IT | Contract Test | DB/Migration | E2E | Black-box |
|---|---|---|---|---|---|---|---|
| AC-1 | X |  |  |  |  | X | X |
| AC-2 | X | X | X | X |  | X | X |
| AC-3 | X | X | X | X |  | X | X |
| AC-4 | X |  |  |  |  | X | X |
| AC-5 | X |  |  |  |  | X | X |
| AC-6 | X | X | X | X | X | X | X |
| AC-7 |  | X |  |  |  |  | X |
| AC-8 | X | X | X | X |  | X | X |
| AC-9 |  | X | X | X |  |  | X |
| AC-10 |  | X |  |  | X |  | X |
| AC-11 |  | X |  |  | X |  | X |
| AC-12 | X | X | X | X |  | X | X |
| AC-13 | X | X | X | X |  | X | X |
| AC-14 | X |  |  |  |  |  | X |
| AC-15 | X | X | X | X | X | X | X |
| AC-16 |  |  | X |  |  | X | X |
| AC-17 |  | X | X | X | X |  | X |
| AC-18 | X |  | X |  |  | X | X |
| AC-19 | X | X | X | X |  | X | X |
| AC-20 |  |  |  |  |  |  | X |

### Status Legend

| Status | Meaning |
|---|---|
| X | Test coverage exists in the current package or generated test set. |
| Planned | Manual black-box verification remains planned because it needs a browser/session. |
| NOT_RUN | Runtime execution is not performed in this documentation-oriented update. |
| N/A | This test type is not applicable for the AC. |

## 3. Priority

| test item | priority | reason |
|---|---|---|
| Login success / redirect / auth state | P0 | Core access flow. |
| Invalid credential handling | P0 | Security and UX. |
| Token validation and logout behavior | P0 | Session security boundary. |
| `me` compatibility and auth contract | P0 | FE/BE compatibility. |
| Password masking and pending submit | P1 | UI polish and safety. |
| Non-admin/admin route handling | P0 | Access control. |
| Error key localization | P1 | FE message display. |

## 4. Reuse Existing Test

| existing test | path | covers | gap |
|---|---|---|---|
| LoginPage tests | `EDCAP_FE/src/__tests__/auth/LoginPage.test.tsx` | Form render, required fields, backend error localization, admin/non-admin redirects, authenticated redirect | Password mask/toggle and pending submit coverage. |
| useAuth tests | `EDCAP_FE/src/__tests__/auth/useAuth.test.tsx` | 401 current user as anonymous, successful current user, normal logout clear | Logout API-failure clear coverage. |
| API helper tests | `EDCAP_FE/src/__tests__/lib/api.test.ts` | Bearer header, JSON body, 204, error code/trace, token helper persistence | No new gap for current scope. |
| Playwright login spec | `EDCAP_FE/e2e_tests/tests/login.spec.ts` | Mocked standard login, admin login, invalid credential journey | Does not cover logout browser flow. |
| AuthService tests | `EDCAP_BE/src/test/java/com/sdd/platform/application/usecase/governance/AuthServiceTest.java` | Success, username trim, invalid credential failure, inactive account, reset/revoke | Phase 6 added account-state coverage. |
| AuthTokenService tests | `EDCAP_BE/src/test/java/com/sdd/platform/application/usecase/governance/AuthTokenServiceTest.java` | Issue/parse, expired token, missing identity | Tampered token rejection coverage. |
| AuthApiIntegrationTest | `EDCAP_BE/src/test/java/com/sdd/platform/web/rest/AuthApiIntegrationTest.java` | Login response, username 101 validation, no-token me 401, `/auth/me`, `/me`, logout 204 | Full revoked-token-with-real-filter path remains a deeper integration gap. |

## 5. Additional Test This Time

| test | type | target | related AC |
|---|---|---|---|
| Password masked by default and visibility toggle works | FE UT | `LoginPage` password input | AC-4 |
| Submit is disabled while login mutation is pending | FE UT | `LoginPage` mutation state | AC-5 |
| Logout clears client state even if backend logout fails | FE UT | `logout()` in `useAuth.ts` | AC-18 |
| Failed attempt below threshold records failure without lock | BE UT | `AuthService.registerFailure` behavior through `login` | AC-10 |
| Tampered JWT payload is rejected | BE UT | `AuthTokenService.parseAndValidate` | AC-15, AC-19 |

### E2E Step-by-step Scenarios

| scenario | precondition | steps | expected | related AC |
|---|---|---|---|---|
| Admin login | Admin account exists | Login as ADMIN | Redirect to admin landing page | AC-1, AC-12 |
| Non-admin login | Non-admin account exists | Login as USER/VIEWER/EDITOR | Redirect to non-admin landing page | AC-1, AC-2, AC-13 |
| Invalid password | Valid username exists | Submit wrong password | Generic invalid-credential error | AC-8, AC-19 |
| Logout | Logged-in session exists | Click logout | Session cleared and redirected | AC-17, AC-18 |

## 6. Areas intentionally left untested this time

| area | reason | risk |
|---|---|---|
| Active refresh-token flow | Explicitly out of scope/open decision in spec. | Medium due current response field and storage. |
| OAuth/SSO/MFA/registration/password reset | Explicitly out of scope. | Low if UI/API remain inactive. |
| Real DB E2E | Requires seeded database and environment coordination beyond mocked login spec. | Medium; BE unit and migration review cover core logic. |
| Real revoked-token API call through running BE | Existing integration tests mock authentication; full server/session test is heavier. | Medium; bearer filter/source review and token tests provide partial evidence. |
| Production secret/log inspection | Production data/secrets are forbidden. | Medium; source review and safe error tests provide partial evidence. |
| Formal audit/operation flow | Human decision still open. | Medium compliance/ops risk. |

## 7. Data testing principles

Use only synthetic test users and dummy passwords. Do not use production usernames, real passwords, real bearer/refresh tokens, JWT signing secrets, password hashes, database dumps, or production logs. API and E2E tests must mock or seed deterministic test data; black-box tests must record outcomes without copying secrets.

## 8. Execution command

| command | purpose |
|---|---|
| `npx vitest run src/__tests__/auth src/__tests__/lib/api.test.ts --reporter=dot` | FE auth/API unit tests. |
| `mvn test '-Dtest=AuthServiceTest,AuthTokenServiceTest,AuthApiIntegrationTest'` | BE auth/token/API targeted tests. |
| `npm run -s build` | FE typecheck and production build. |
| `npx playwright test e2e_tests/tests/login.spec.ts --reporter=line` | Mocked browser E2E login journeys. |

## 9. Stop Condition

- A test requires production credentials, production data, production secrets, or raw token/hash capture.
- A test assumes an open human decision is already resolved.
- A test adds OAuth, SSO, MFA, registration, password reset, active refresh-token rotation, `/home`, or `returnUrl` behavior.
- A test requires broad framework/config refactoring unrelated to LOGIN AC.

## 10. Required Human Decision

| decision | current status | required before release? |
|---|---|---|
| Keep, remove, or defer the current refresh-token field/session storage in MVP | Open | Yes |
| Keep non-admin landing at `/:lang/` or add `/:lang/home` | Open | Yes |
| Permit or remove `email` in auth response/JWT | Open | Yes |
| Whether to implement `returnUrl`, and with which allowlist | Open | Yes |
| Username matching case-sensitive or case-insensitive | Open | Yes |
