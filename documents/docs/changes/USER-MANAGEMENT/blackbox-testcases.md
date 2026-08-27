# Black-box Test Cases

**Ticket ID**: USER-MANAGEMENT
**Create date**: 2026-06-12
**Author**: ChatGPT
**Update date**: 2026-06-15

## Test Case Summary

| case ID | AC ID | priority | category | title |
|---|---|---|---|---|
| BB-UM-001 | AC-USER-MANAGEMENT-1 | P0 | Permission / Normal | ADMIN opens User Management screen |
| BB-UM-002 | AC-USER-MANAGEMENT-2 | P0 | Permission | Authenticated non-ADMIN is redirected away from screen |
| BB-UM-003 | AC-USER-MANAGEMENT-2 | P0 | Permission | Non-ADMIN direct API call is rejected |
| BB-UM-004 | AC-USER-MANAGEMENT-3 | P1 | Normal | List user accounts with key columns |
| BB-UM-005 | AC-USER-MANAGEMENT-4 | P1 | Normal | Search accounts by keyword |
| BB-UM-006 | AC-USER-MANAGEMENT-4 | P1 | State | Filter accounts by status |
| BB-UM-007 | AC-USER-MANAGEMENT-4 | P1 | State / Scope | Filter accounts by role and confirm no team filter |
| BB-UM-008 | AC-USER-MANAGEMENT-5 | P0 | Normal | Create valid account |
| BB-UM-009 | AC-USER-MANAGEMENT-5, AC-USER-MANAGEMENT-9 | P1 | Boundary | Username trim and length behavior on create |
| BB-UM-010 | AC-USER-MANAGEMENT-9 | P0 | Duplicate | Create rejects duplicate username |
| BB-UM-011 | AC-USER-MANAGEMENT-10 | P0 | Error / Boundary | Create rejects password mismatch or weak password |
| BB-UM-012 | AC-USER-MANAGEMENT-6 | P0 | Payload / State | Create payload does not include teamId and backend stores team_id NULL |
| BB-UM-013 | AC-USER-MANAGEMENT-7, AC-USER-MANAGEMENT-8 | P0 | Normal / Error | Create writes both tables and rejects invalid role |
| BB-UM-014 | AC-USER-MANAGEMENT-11, AC-USER-MANAGEMENT-12 | P0 | Security | Create and detail responses never expose sensitive fields |
| BB-UM-015 | AC-USER-MANAGEMENT-14 | P0 | Normal / State | Update fullname, email, roleId and isActive succeeds |
| BB-UM-016 | AC-USER-MANAGEMENT-14 | P0 | Rule | Username is not editable through update flow |
| BB-UM-017 | AC-USER-MANAGEMENT-15 | P0 | State | Updating role keeps member pseudonym team_id NULL |
| BB-UM-018 | AC-USER-MANAGEMENT-16 | P0 | State | Deactivate and reactivate account without hard delete |
| BB-UM-019 | AC-USER-MANAGEMENT-17 | P0 | Conflict / State | Last active ADMIN guard blocks deactivation or role downgrade |
| BB-UM-020 | AC-USER-MANAGEMENT-18 | P0 | Security / Normal | Reset password replaces hash and does not expose raw password |
| BB-UM-021 | AC-USER-MANAGEMENT-19 | P0 | Regression / State | Active account can login and inactive account cannot login |
| BB-UM-022 | AC-USER-MANAGEMENT-20 | P1 | Boundary / Performance | Pagination normalizes invalid page and size values |
| BB-UM-023 | AC-USER-MANAGEMENT-5, AC-USER-MANAGEMENT-14, AC-USER-MANAGEMENT-18 | P1 | Double submit | Double submit does not create duplicate writes |
| BB-UM-024 | AC-USER-MANAGEMENT-21 | P2 | Operation / Log | Errors stay safe and traceId-compatible |
| BB-UM-025 | AC-USER-MANAGEMENT-1, AC-USER-MANAGEMENT-2, AC-USER-MANAGEMENT-3 | P2 | Back/reload | Reload and back navigation keep safe list behavior |
| BB-UM-026 | AC-USER-MANAGEMENT-22, AC-USER-MANAGEMENT-23 | P2 | Review / Out of scope | No batch/job/event or out-of-scope feature is introduced |

## Test Cases

### BB-UM-001: ADMIN opens User Management screen

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-1 |
| Priority | P0 |
| Category | Permission / Normal |
| Preconditions | ADMIN user is logged in. |
| Input | Open User Management screen for the current language, for example `/:lang/admin/user-accounts`. |
| Steps | 1. Login as ADMIN. 2. Open User Management. 3. Observe the initial page state. |
| Expected Result | User Accounts page is displayed and the list API is called. |
| Note | Route name may follow final FE routing, but behavior must remain ADMIN-only. |

### BB-UM-002: Authenticated non-ADMIN is redirected away from screen

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-2 |
| Priority | P0 |
| Category | Permission |
| Preconditions | Authenticated non-ADMIN user exists, for example USER or VIEWER. |
| Input | Open User Management URL directly as non-ADMIN. |
| Steps | 1. Login as non-ADMIN. 2. Enter User Management URL directly. |
| Expected Result | FE blocks access and redirects away, typically to `/:lang/login`. User Management data is not displayed. |
| Note | Preserve current language when possible. |

### BB-UM-003: Non-ADMIN direct API call is rejected

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-2 |
| Priority | P0 |
| Category | Permission |
| Preconditions | Authenticated non-ADMIN user/session exists or no valid session exists. |
| Input | Direct calls to User Management APIs. |
| Steps | 1. Call list/detail/create/update/activate/deactivate/reset-password endpoints as non-ADMIN or unauthenticated user. |
| Expected Result | BE rejects unauthorized access, normally with 403 for non-ADMIN and 401 for unauthenticated requests. No account data is returned and no write occurs. |
| Note | This must hold even if FE route guard is bypassed. |

### BB-UM-004: List user accounts with key columns

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-3 |
| Priority | P1 |
| Category | Normal |
| Preconditions | ADMIN user is logged in. At least one active account exists. |
| Input | Open the list page. |
| Steps | 1. Open User Management. 2. Observe list columns and action area. |
| Expected Result | List shows username, member/fullname, role, status, updated time and actions. |
| Note | Verify by UI-visible columns only. |

### BB-UM-005: Search accounts by keyword

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-4 |
| Priority | P1 |
| Category | Normal |
| Preconditions | ADMIN user is logged in. Search target accounts exist. |
| Input | Keyword matching username, fullname, email, or pseudonym. |
| Steps | 1. Open list. 2. Enter keyword. 3. Execute search. |
| Expected Result | Matching accounts are displayed and non-matching accounts are excluded. |
| Note | Search must remain black-box observable. |

### BB-UM-006: Filter accounts by status

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-4 |
| Priority | P1 |
| Category | State |
| Preconditions | ADMIN user is logged in. At least one active and one inactive account exist. |
| Input | Status filter values such as Active and Inactive. |
| Steps | 1. Open list. 2. Select Active. 3. Select Inactive. |
| Expected Result | Active filter shows only active accounts. Inactive filter shows only inactive accounts. |
| Note | Status filter must not modify persisted account state. |

### BB-UM-007: Filter accounts by role and confirm no team filter

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-4 |
| Priority | P1 |
| Category | State / Scope |
| Preconditions | ADMIN user is logged in. Accounts with different roles exist. |
| Input | Role filter values. |
| Steps | 1. Open list. 2. Change role filter. 3. Confirm whether any team filter exists. |
| Expected Result | Role filter works for available roles. No team filter is shown or used in the MVP. |
| Note | Team handling is intentionally out of scope. |

### BB-UM-008: Create valid account

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-5 |
| Priority | P0 |
| Category | Normal |
| Preconditions | ADMIN user is logged in. Username is unique. Role exists. |
| Input | Valid username, fullname, email, password, confirmPassword, roleId, and isActive. |
| Steps | 1. Open create form. 2. Fill all required fields. 3. Save. |
| Expected Result | Account is created successfully. New account appears in list and can login if active. |
| Note | Use only fields allowed by the spec. |

### BB-UM-009: Username trim and length behavior on create

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-5, AC-USER-MANAGEMENT-9 |
| Priority | P1 |
| Category | Boundary |
| Preconditions | ADMIN user is logged in. |
| Input | Username with leading/trailing spaces and boundary length values. |
| Steps | 1. Open create form. 2. Enter username with spaces. 3. Repeat with max-length and over-max-length values. 4. Save. |
| Expected Result | Username is trimmed before validation/write. Max-length value is accepted. Over-max-length value is rejected. |
| Note | Record observed maximum if the implementation defines one. |

### BB-UM-010: Create rejects duplicate username

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-9 |
| Priority | P0 |
| Category | Duplicate |
| Preconditions | ADMIN user is logged in. Existing account has target username. |
| Input | Duplicate username with unique other fields. |
| Steps | 1. Open create form. 2. Enter duplicate username. 3. Save. |
| Expected Result | Create is rejected with a safe duplicate-username message. Existing account remains unchanged. |
| Note | Duplicate check should be case-insensitive if that is the implemented behavior. |

### BB-UM-011: Create rejects password mismatch or weak password

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-10 |
| Priority | P0 |
| Category | Error / Boundary |
| Preconditions | ADMIN user is logged in. |
| Input | Password mismatch and weak password values. |
| Steps | 1. Open create form. 2. Enter mismatched password and confirmPassword. 3. Repeat with weak password. 4. Save. |
| Expected Result | Create is rejected before DB write. Password validation message is shown and no account is created. |
| Note | FE and BE password policy alignment should be consistent with the current contract. |

### BB-UM-012: Create payload does not include teamId and backend stores team_id NULL

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-6 |
| Priority | P0 |
| Category | Payload / State |
| Preconditions | ADMIN user is logged in. |
| Input | Valid create payload from FE. |
| Steps | 1. Inspect create request payload from FE. 2. Submit account create. 3. Inspect result or follow-up detail if available. |
| Expected Result | FE request does not include `teamId`. Backend stores `tbl_dim_member_pseudonym.team_id = NULL`. |
| Note | Team input is intentionally absent in this ticket. |

### BB-UM-013: Create writes both tables and rejects invalid role

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-7, AC-USER-MANAGEMENT-8 |
| Priority | P0 |
| Category | Normal / Error |
| Preconditions | ADMIN user is logged in. |
| Input | Valid create payload for the success case, then invalid or missing roleId for the error case. |
| Steps | 1. Create a valid account. 2. Confirm account and member pseudonym are both created. 3. Try create with invalid roleId. |
| Expected Result | Valid create writes both `tbl_auth_user_account` and `tbl_dim_member_pseudonym`. Invalid role is rejected. |
| Note | Role is required for this screen. |

### BB-UM-014: Create and detail responses never expose sensitive fields

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-11, AC-USER-MANAGEMENT-12, AC-USER-MANAGEMENT-13 |
| Priority | P0 |
| Category | Security |
| Preconditions | ADMIN user is logged in. Account exists or can be created. |
| Input | Create response, list response, and detail response. |
| Steps | 1. Create account. 2. Inspect network response and UI state. 3. Open detail page. |
| Expected Result | Responses and UI never expose password, passwordHash, token, secret, or private key. Password is stored as bcrypt hash only. |
| Note | Raw password must never appear in response or logs. |

### BB-UM-015: Update fullname, email, roleId and isActive succeeds

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-14 |
| Priority | P0 |
| Category | Normal / State |
| Preconditions | ADMIN user is logged in. Account exists and is editable. |
| Input | New fullname, email, roleId and isActive. |
| Steps | 1. Open edit form. 2. Change editable fields. 3. Save. |
| Expected Result | Update succeeds and changes are reflected in list/detail. |
| Note | Username is not part of the edit flow. |

### BB-UM-016: Username is not editable through update flow

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-14 |
| Priority | P0 |
| Category | Rule |
| Preconditions | ADMIN user is logged in. |
| Input | Attempt to modify username in edit flow or direct payload manipulation. |
| Steps | 1. Open edit form. 2. Confirm username cannot be changed in UI. 3. Attempt payload tampering if the test harness allows it. |
| Expected Result | Username cannot be changed through update flow. |
| Note | Any username mutation should be rejected or ignored according to contract. |

### BB-UM-017: Updating role keeps member pseudonym team_id NULL

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-15 |
| Priority | P0 |
| Category | State |
| Preconditions | ADMIN user is logged in. Account exists. |
| Input | New roleId. |
| Steps | 1. Open edit form. 2. Change roleId. 3. Save. 4. Inspect resulting linked member pseudonym record if available. |
| Expected Result | Role changes are saved and `team_id` remains NULL. |
| Note | Team assignment remains out of scope. |

### BB-UM-018: Deactivate and reactivate account without hard delete

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-16 |
| Priority | P0 |
| Category | State |
| Preconditions | ADMIN user is logged in. Active account exists. |
| Input | Deactivate then reactivate the same account. |
| Steps | 1. Deactivate account. 2. Confirm account is inactive. 3. Reactivate account. |
| Expected Result | Status changes as expected and the account is not hard-deleted. |
| Note | Verify login behavior separately. |

### BB-UM-019: Last active ADMIN guard blocks deactivation or role downgrade

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-17 |
| Priority | P0 |
| Category | Conflict / State |
| Preconditions | Only one active ADMIN account exists. ADMIN user is logged in. |
| Input | Attempt to deactivate or downgrade the only active ADMIN account. |
| Steps | 1. Ensure only one active ADMIN exists. 2. Try to deactivate it or change its role away from ADMIN. |
| Expected Result | Operation is rejected and at least one active ADMIN remains. |
| Note | This is a release-blocking guard. |

### BB-UM-020: Reset password replaces hash and does not expose raw password

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-18 |
| Priority | P0 |
| Category | Security / Normal |
| Preconditions | ADMIN user is logged in. Target account exists. |
| Input | New password and confirmPassword for reset. |
| Steps | 1. Open reset-password action. 2. Enter new password. 3. Confirm reset. 4. Inspect response and, if possible, confirm login with new password. |
| Expected Result | Password hash is replaced, raw password is not returned or logged, and reset succeeds. |
| Note | Old password should no longer work after reset. |

### BB-UM-021: Active account can login and inactive account cannot login

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-19 |
| Priority | P0 |
| Category | Regression / State |
| Preconditions | One active account and one inactive account exist. LOGIN flow is available. |
| Input | Valid credentials for both accounts. |
| Steps | 1. Login with active account credentials. 2. Logout. 3. Login with inactive account credentials. |
| Expected Result | Active account can login. Inactive/deactivated account cannot login. |
| Note | This protects compatibility with LOGIN. |

### BB-UM-022: Pagination normalizes invalid page and size values

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-20 |
| Priority | P1 |
| Category | Boundary / Performance |
| Preconditions | ADMIN user is logged in. Several accounts exist. |
| Input | Invalid page or size values such as zero, negative, or over-max size. |
| Steps | 1. Open list. 2. Set invalid pagination values through UI or API. 3. Execute request. |
| Expected Result | List API remains usable. Invalid page/size does not break the page and is normalized or rejected safely according to contract. |
| Note | Maximum size behavior should follow the implemented contract. |

### BB-UM-023: Double submit does not create duplicate writes

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-5, AC-USER-MANAGEMENT-14, AC-USER-MANAGEMENT-18 |
| Priority | P1 |
| Category | Double submit |
| Preconditions | ADMIN user is logged in. Unique create/update/reset input is prepared. |
| Input | Rapid double click on Save or two near-simultaneous identical submissions. |
| Steps | 1. Open create, update, or reset-password form. 2. Submit twice rapidly. 3. Check resulting list/detail and response/errors. |
| Expected Result | At most one successful write is applied. Duplicate active data is not created. |
| Note | Black-box expected result is no duplicate active write. |

### BB-UM-024: Errors stay safe and traceId-compatible

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-21 |
| Priority | P2 |
| Category | Operation / Log |
| Preconditions | Test environment can trigger a controlled account error safely, for example validation error, duplicate, not-found, or conflict. |
| Input | Invalid or conflicting User Management request. |
| Steps | 1. Trigger a controlled error. 2. Inspect response and application log/trace output if available to tester. |
| Expected Result | Error response stays safe and compatible with existing traceId/message behavior. No secrets or stack traces are exposed. |
| Note | This is black-box observable only. |

### BB-UM-025: Reload and back navigation keep safe list behavior

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-1, AC-USER-MANAGEMENT-2, AC-USER-MANAGEMENT-3 |
| Priority | P2 |
| Category | Back/reload |
| Preconditions | ADMIN user is logged in. Active and inactive accounts exist. |
| Input | Browser reload/back after list/search/filter operations. |
| Steps | 1. Open list. 2. Apply search and status/role filters. 3. Reload page and/or navigate back/forward. |
| Expected Result | Screen remains stable and authorized. It either preserves or safely resets search/filter state according to FE design. No unauthorized data exposure occurs. |
| Note | Record actual state preservation behavior if not specified. |

### BB-UM-026: No batch/job/event or out-of-scope feature is introduced

| item | content |
|---|---|
| Related AC | AC-USER-MANAGEMENT-22, AC-USER-MANAGEMENT-23 |
| Priority | P2 |
| Category | Review / Out of scope |
| Preconditions | Release scope and test harness are available. |
| Input | Create, update, deactivate, and reset-password operations. |
| Steps | 1. Execute the supported flows. 2. Confirm there is no visible batch/job/event behavior. 3. Confirm excluded features remain absent. |
| Expected Result | No batch/job/event behavior is introduced. Formal audit, forgot password, self-service reset, SSO/OAuth/MFA, and team assignment remain out of scope. |
| Note | This is primarily a scope and regression check. |

## AC to Black-box Case Mapping

| AC ID | AC summary | Black-box case IDs | Coverage note |
|---|---|---|---|
| AC-USER-MANAGEMENT-1 | ADMIN user can open the User Management screen at `/:lang/admin/user-accounts`. | BB-UM-001, BB-UM-025 | Covers normal and reload/back behavior. |
| AC-USER-MANAGEMENT-2 | Non-admin user cannot access the screen or ADMIN user-account APIs. | BB-UM-002, BB-UM-003 | Covers FE route guard and BE access control. |
| AC-USER-MANAGEMENT-3 | ADMIN can list user accounts with username, member/fullname, role, status, updated time and actions. | BB-UM-004, BB-UM-025 | Covers list display and safe navigation behavior. |
| AC-USER-MANAGEMENT-4 | ADMIN can search accounts by keyword and filter by status and role. Team filter is not in MVP. | BB-UM-005, BB-UM-006, BB-UM-007 | Covers search, status filter, role filter, and no team filter. |
| AC-USER-MANAGEMENT-5 | ADMIN can create an account with username, fullname, email, password, confirmPassword, roleId and isActive. | BB-UM-008, BB-UM-009, BB-UM-023 | Covers create success, boundary, and double submit. |
| AC-USER-MANAGEMENT-6 | Create request and FE form must not include `teamId`; system stores `tbl_dim_member_pseudonym.team_id = NULL`. | BB-UM-012, BB-UM-017 | Covers no teamId payload and persisted NULL team_id. |
| AC-USER-MANAGEMENT-7 | Create account writes `tbl_auth_user_account` and linked `tbl_dim_member_pseudonym`. | BB-UM-013 | Covers dual-table write. |
| AC-USER-MANAGEMENT-8 | `role_id` is required and saved on `tbl_dim_member_pseudonym`; invalid role is rejected. | BB-UM-013 | Covers required and invalid role behavior. |
| AC-USER-MANAGEMENT-9 | Username is trimmed, required, max 100 characters and duplicate username is rejected case-insensitively. | BB-UM-009, BB-UM-010 | Covers trim, length, and duplicate. |
| AC-USER-MANAGEMENT-10 | Password is required for create/reset, confirmPassword must match, and weak passwords are rejected before DB write. | BB-UM-011, BB-UM-020 | Covers create and reset password validation. |
| AC-USER-MANAGEMENT-11 | Password is saved as bcrypt hash with `password_algo = bcrypt`; raw password is never stored. | BB-UM-014, BB-UM-020 | Covers hashing and reset safety. |
| AC-USER-MANAGEMENT-12 | API responses and UI never expose password, passwordHash, token, secret or private key. | BB-UM-014, BB-UM-024 | Covers sensitive-field protection and safe errors. |
| AC-USER-MANAGEMENT-13 | ADMIN can view account detail without sensitive fields. | BB-UM-014 | Covers detail safety. |
| AC-USER-MANAGEMENT-14 | ADMIN can update fullname, email, roleId and isActive; username is not updated through edit flow. | BB-UM-015, BB-UM-016, BB-UM-023 | Covers update, immutable username, and double submit. |
| AC-USER-MANAGEMENT-15 | Updating role changes member pseudonym `role_id` and keeps `team_id = NULL`. | BB-UM-017 | Covers role update behavior. |
| AC-USER-MANAGEMENT-16 | ADMIN can deactivate and reactivate accounts; system does not hard delete accounts. | BB-UM-018 | Covers status transition. |
| AC-USER-MANAGEMENT-17 | System rejects deactivation or role downgrade if it would leave no active ADMIN account. | BB-UM-019 | Covers last-Admin guard. |
| AC-USER-MANAGEMENT-18 | ADMIN can reset password; reset replaces password hash and does not return/log raw password. | BB-UM-020, BB-UM-023 | Covers reset safety and double submit. |
| AC-USER-MANAGEMENT-19 | Active account created/updated by this screen can be used by LOGIN; inactive/deactivated account cannot login. | BB-UM-021 | Covers login regression. |
| AC-USER-MANAGEMENT-20 | Pagination uses normalized page/size; invalid page/size does not break list API. | BB-UM-022 | Covers pagination normalization. |
| AC-USER-MANAGEMENT-21 | Errors use safe message keys/traceId style where available and do not leak sensitive values. | BB-UM-024 | Covers safe error behavior. |
| AC-USER-MANAGEMENT-22 | No batch/job/event is introduced by this ticket. | BB-UM-026 | Covers release scope. |
| AC-USER-MANAGEMENT-23 | Formal audit, forgot password, self-service reset, SSO/OAuth/MFA and team assignment remain out of scope. | BB-UM-007, BB-UM-026 | Covers out-of-scope guardrails. |

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
- [ ] External IF failure
- [ ] Timeout/retry
- [x] Double submit
- [x] Back/reload
- [x] Session expired
- [x] Existing data compatibility
- [x] Log/audit/notification/report output

## Out of Scope / Not Covered by Black-box Cases

| area | reason | follow-up |
|---|---|---|
| External IF failure | User Management does not require GitHub/Jira/CircleCI calls per current spec. | Cover in connector/webhook tickets. |
| Timeout/retry | No explicit retry/timeout requirement is defined for User Management CRUD. | Add if operation SLA or retry UX is specified. |
| Dedicated audit-log persistence | Explicitly out of scope by AC-USER-MANAGEMENT-22 and AC-USER-MANAGEMENT-23. | Cover in a future audit-log ticket if introduced. |
| Team assignment flow | The ticket intentionally does not manage team assignment. | Add in a future scope if team management is reintroduced. |
| Forgot password / self-service reset | Explicitly out of scope by AC-USER-MANAGEMENT-23. | Add in a separate self-service ticket. |
| SSO / OAuth / MFA | Explicitly out of scope by AC-USER-MANAGEMENT-23. | Add in an auth-expansion ticket. |
