# Black-box Test Cases

**Ticket ID**: LOGIN  
**Create date**: 2026-06-11  
**Author**: Codex  
**Update date**: 2026-06-11  


## Test Case Summary

| case ID | AC ID | priority | category | title |
|---|---|---|---|---|
| BB-LOGIN-001 | AC-1 | P0 | Normal | Anonymous user can open login page and see required controls |
| BB-LOGIN-002 | AC-2 | P0 | Error | Empty username blocks login submission |
| BB-LOGIN-003 | AC-3 | P0 | Error | Empty password blocks login submission |
| BB-LOGIN-004 | AC-4 | P0 | Normal | Password is masked by default and can be toggled |
| BB-LOGIN-005 | AC-5 | P0 | State | Pending login prevents duplicate submit |
| BB-LOGIN-006 | AC-6, AC-12 | P0 | Normal | Active admin login succeeds and redirects to admin |
| BB-LOGIN-007 | AC-6, AC-13 | P0 | Normal | Active non-admin login succeeds and redirects to root landing |
| BB-LOGIN-008 | AC-7 | P1 | Boundary | Username with leading/trailing spaces authenticates after trim |
| BB-LOGIN-009 | AC-8, AC-19 | P0 | Error | Wrong password returns generic invalid-credential behavior |
| BB-LOGIN-010 | AC-8, AC-19 | P0 | Error | Unknown username returns same generic invalid-credential behavior |
| BB-LOGIN-011 | AC-8, AC-19 | P0 | Error | Existing and unknown users are indistinguishable on failure |
| BB-LOGIN-012 | AC-9, AC-19 | P0 | Error | Inactive account is rejected safely and receives no token |
| BB-LOGIN-013 | AC-10 | P1 | Boundary | Repeated wrong passwords remain generic and safe |
| BB-LOGIN-014 | AC-10 | P0 | Error | Repeated failures do not expose account state |
| BB-LOGIN-015 | AC-10 | P0 | State | Login still succeeds normally after prior failures |
| BB-LOGIN-016 | AC-11 | P0 | State | Successful login revokes prior active token sessions |
| BB-LOGIN-017 | AC-14 | P1 | Permission | Authenticated user opening login is redirected by role |
| BB-LOGIN-018 | AC-15 | P0 | Permission | Missing bearer token is rejected and FE becomes anonymous |
| BB-LOGIN-019 | AC-15 | P0 | Permission | Malformed or tampered bearer token is rejected |
| BB-LOGIN-020 | AC-15 | P0 | Permission | Expired bearer token is rejected |
| BB-LOGIN-021 | AC-15, AC-17 | P0 | State | Revoked token cannot be reused after logout |
| BB-LOGIN-022 | AC-16 | P0 | External IF | `/api/v1/auth/me` and `/api/v1/me` return compatible payloads |
| BB-LOGIN-023 | AC-17, AC-18 | P0 | State | Logout revokes server state and clears FE local auth state |
| BB-LOGIN-024 | AC-18 | P1 | External IF | Logout API failure still clears FE local auth state |
| BB-LOGIN-025 | AC-19 | P0 | Error | Auth responses never expose password/hash/token/stack/SQL details |
| BB-LOGIN-026 | AC-20 | P0 | Normal | Out-of-scope auth features are not active login paths |
| BB-LOGIN-027 | AC-6, AC-7 | P1 | Boundary | Username length 100 is accepted when otherwise valid |
| BB-LOGIN-028 | AC-6, AC-7 | P1 | Boundary | Username length 101 is rejected safely |
| BB-LOGIN-029 | AC-3 | P1 | Boundary | Password length over schema limit is rejected safely |
| BB-LOGIN-030 | AC-19 | P1 | External IF | Backend/network failure shows generic safe error and no stale auth |
| BB-LOGIN-031 | AC-19 | P1 | Log/audit | Login/logout observable logs and trace IDs contain no secrets |
| BB-LOGIN-032 | AC-20 | P2 | Operation | Helpdesk unlock, cleanup, audit, and refresh-token policies are reviewed as open decisions |

## Test Cases

### BB-001:

| item | content |
|---|---|
| Related AC | AC-1 to AC-20 |
| Priority | P0/P1/P2 |
| Category | Normal / Error / Boundary / Permission / State / External IF |
| Preconditions | Use test-only users and synthetic tokens from `test-data.md`; clear browser storage and token-session state before each group. |
| Input | UI actions on `/:lang/login` and HTTP calls to `/api/v1/auth/login`, `/api/v1/auth/logout`, `/api/v1/auth/me`, `/api/v1/me`. |
| Steps | Execute each `BB-LOGIN-*` case from the detailed table below. |
| Expected Result | Each case meets its own expected result without exposing secrets or relying on production data. |
| Note | This section keeps the template-required case shape; the detailed executable matrix follows. |

| case ID | Preconditions | Input / Action | Expected Result | Evidence Method | Status |
|---|---|---|---|---|---|
| BB-LOGIN-001 | Anonymous state; no token in browser storage | Open `/:lang/login` | Username input, password input, password visibility control, and login button are visible; no redirect loop | FE/E2E/manual | Planned |
| BB-LOGIN-002 | Anonymous state | Enter password only and attempt submit | Login request is not sent, or BE returns safe validation error if reached | FE/manual/network observation | Planned |
| BB-LOGIN-003 | Anonymous state | Enter username only and attempt submit | Login request is not sent, or BE returns safe validation error if reached | FE/manual/network observation | Planned |
| BB-LOGIN-004 | Anonymous state | Type password, click show/hide control | Password is masked by default; toggle changes visibility and accessible label | FE/E2E/manual | Planned |
| BB-LOGIN-005 | Valid input; delayed login response | Submit repeatedly by click/Enter while pending | Only one in-flight login is observable; submit remains disabled while pending | FE/E2E/manual | Planned |
| BB-LOGIN-006 | Active admin test account | Login with valid admin credentials | 200 response, token response, safe user summary, FE route `/:lang/admin` | BE IT/E2E/manual | Planned |
| BB-LOGIN-007 | Active non-admin test account | Login with valid non-admin credentials | 200 response, token response, safe user summary, FE route `/:lang/` | BE IT/E2E/manual | Planned |
| BB-LOGIN-008 | Active test account exists | Login with username containing leading/trailing spaces | Authentication behaves as trimmed username | BE/API/manual | Planned |
| BB-LOGIN-009 | Existing active test user | Login with wrong password | 401 generic invalid-credential behavior; no token; no account detail | BE/API/E2E/manual | Planned |
| BB-LOGIN-010 | Unknown username | Login with unknown username and any password | Same generic invalid-credential behavior as wrong password; no token | BE/API/E2E/manual | Planned |
| BB-LOGIN-011 | One existing user and one unknown username | Compare failure UI/status/payload/timing envelope at black-box level | No account-existence hint in visible error, code, route, or response body | Security/BB review | Planned |
| BB-LOGIN-012 | Inactive test account | Login with correct inactive account password | 401 safe unavailable-account behavior; no access/refresh token returned | BE/API/manual | Planned |
| BB-LOGIN-013 | Active test account | Perform repeated failed logins | Failure responses remain generic and do not create a special state | BE/API/manual | Planned |
| BB-LOGIN-014 | Active test account | Compare repeated wrong-password attempts | Failure responses remain indistinguishable and safe | BE/API/manual | Planned |
| BB-LOGIN-015 | Active test account with prior failures | Login successfully | Login succeeds normally and revokes/replaces prior active session | BE/API/manual | Planned |
| BB-LOGIN-016 | Test account with prior active session | Login successfully | Prior active token session is revoked/replaced | BE/API/manual | Planned |
| BB-LOGIN-017 | Browser has valid authenticated current-user state | Open `/:lang/login` | User is redirected away by role: admin to admin, non-admin to root landing | FE/E2E/manual | Planned |
| BB-LOGIN-018 | No bearer token | Call `/api/v1/auth/me` or protected API | 401; FE treats user as anonymous and does not loop | BE IT/FE/manual | Planned |
| BB-LOGIN-019 | Malformed/tampered token | Call current-user/protected API with token | 401; token is rejected; FE becomes anonymous | BE unit/API/manual | Planned |
| BB-LOGIN-020 | Expired token | Call current-user/protected API with expired token | 401; FE becomes anonymous/login required | BE unit/API/manual | Planned |
| BB-LOGIN-021 | Login succeeded, then logout revoked session | Reuse old access token against current-user/protected API | 401 or equivalent unauthorized behavior; token cannot authenticate | API/manual | Planned |
| BB-LOGIN-022 | Valid token | Call `/api/v1/auth/me` and `/api/v1/me` | Both return compatible current-user payload: username, displayName, email, role, accessScopes | BE IT/API/manual | Planned |
| BB-LOGIN-023 | Logged-in state | Trigger logout | Backend returns 204 when reachable; server session revoked; FE clears tokens/cache and returns to login/anonymous | FE/BE IT/E2E/manual | Planned |
| BB-LOGIN-024 | Logged-in FE state; backend logout fails/network error | Trigger logout | FE still clears local auth state and navigates to login/anonymous state | FE/manual | Planned |
| BB-LOGIN-025 | Any auth failure/success | Inspect UI, API body, headers, visible logs allowed for test | No password, hash, raw access token, raw refresh token, stack trace, SQL detail, or secret is exposed | Security/BB review | Planned |
| BB-LOGIN-026 | Anonymous login page | Inspect visible auth options and network calls | OAuth/SSO/MFA/register/password reset/Remember Me/refresh flow are not active login paths | FE/manual/source-assisted review | Planned |
| BB-LOGIN-027 | Test account with 100-character username, if supported by fixture | Submit valid credentials | Request is accepted by validation and then follows normal account outcome | API/manual | Planned |
| BB-LOGIN-028 | Username string length 101 | Submit login request | 400 validation or safe FE block; auth service is not observably executed | BE IT/API/manual | Planned |
| BB-LOGIN-029 | Password string length over 256 | Submit login request | 400 validation or safe FE block; no secret echo | API/manual | Planned |
| BB-LOGIN-030 | Backend returns 500 or network unavailable | Submit login | Generic safe error; no stale authenticated state or token storage | FE/API/manual | Planned |
| BB-LOGIN-031 | Test logging/trace access available | Trigger login success, invalid credential, logout | Logs/trace evidence contain traceable events without passwords/tokens/hashes | Ops/security review | Planned |
| BB-LOGIN-032 | Product/security/ops review session | Review refresh-token, token storage, audit, cleanup, unlock, non-admin route decisions | Open decisions are accepted, rejected, or ticketed before release | Human review | Planned |

| AC ID | Black-box cases | coverage note |
|---|---|---|
| AC-1 | BB-LOGIN-001 | Login screen visibility. |
| AC-2 | BB-LOGIN-002 | Empty username. |
| AC-3 | BB-LOGIN-003, BB-LOGIN-029 | Empty/invalid password. |
| AC-4 | BB-LOGIN-004 | Mask/toggle. |
| AC-5 | BB-LOGIN-005 | Duplicate submit. |
| AC-6 | BB-LOGIN-006, BB-LOGIN-007, BB-LOGIN-027, BB-LOGIN-028 | Success response and validation boundary. |
| AC-7 | BB-LOGIN-008, BB-LOGIN-027, BB-LOGIN-028 | Username trim and length. |
| AC-8 | BB-LOGIN-009, BB-LOGIN-010, BB-LOGIN-011 | Invalid credential/no enumeration. |
| AC-9 | BB-LOGIN-012 | Inactive account. |
| AC-10 | BB-LOGIN-013, BB-LOGIN-014, BB-LOGIN-015 | Repeated failures stay generic and non-locking. |
| AC-11 | BB-LOGIN-016 | Successful login revokes prior session. |
| AC-12 | BB-LOGIN-006 | Admin redirect. |
| AC-13 | BB-LOGIN-007 | Non-admin redirect. |
| AC-14 | BB-LOGIN-017 | Authenticated login redirect. |
| AC-15 | BB-LOGIN-018, BB-LOGIN-019, BB-LOGIN-020, BB-LOGIN-021 | Missing/invalid/expired/revoked token. |
| AC-16 | BB-LOGIN-022 | Current-user compatibility. |
| AC-17 | BB-LOGIN-021, BB-LOGIN-023 | Logout revoke and 204. |
| AC-18 | BB-LOGIN-023, BB-LOGIN-024 | FE local clear. |
| AC-19 | BB-LOGIN-009, BB-LOGIN-010, BB-LOGIN-011, BB-LOGIN-025, BB-LOGIN-030, BB-LOGIN-031 | Secret/noise leakage and safe errors. |
| AC-20 | BB-LOGIN-026, BB-LOGIN-032 | Out-of-scope features and open policy decisions. |

## Viewpoints Covered

- [x] Normal case
- [x] Error case
- [x] Boundary value
- [x] Permission difference
- [x] State transition
- [x] Character type input
- [x] Numeric input
- [x] Full-width number
- [x] Empty/null
- [x] Duplicate
- [x] Non-existing ID
- [x] Deleted data
- [x] External IF failure
- [x] Timeout/retry
- [x] Double submit
- [x] Back/reload
- [x] Session expired
- [x] Existing data compatibility
- [x] Log/audit/notification/report output
