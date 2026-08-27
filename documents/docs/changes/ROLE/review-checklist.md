# Review Checklist - ROLE

**Ticket ID**: ROLE  
**Feature**: CRUD Role Management  
**Phase**: Phase 4 - Review Checklist / Self Review Skeleton  
**Updated**: 2026-06-12  
**Status**: Ready for post-implementation review

## 1. Severity

| severity | definition | required action |
|---|---|---|
| Blocker | Release or merge is unsafe; spec/security/data contract is violated | Must fix before merge |
| Major | Likely user-visible bug, contract drift, missing required test, or operational risk | Fix or record accepted risk with owner/deadline |
| Minor | Low-risk quality, maintainability, copy, or polish issue | Fix if practical; may defer |
| Question | Needs human/spec confirmation | Move to open issue or Human Decision |
| False Positive | Finding is not valid | Record evidence |
| Accepted Risk | Known issue explicitly accepted | Record owner, impact, deadline, approver |

## 2. Specification / AC Alignment

| item | severity | check |
|---|---|---|
| Canonical spec | Blocker | Implementation matches `spec-pack.md`; no behavior is taken only from raw/reference input |
| Route placement | Blocker | Role screen is reachable at `/roles` through current runtime routing in `App.tsx` under `main.tsx` `HashRouter` |
| Delete endpoint | Blocker | Delete action uses `PUT /api/v1/roles/{role_id}/delete`, not HTTP `DELETE` |
| Logical delete | Blocker | Delete sets `delete_flag = 1`, updates `updated_at` / `updated_by`, and does not physically delete rows |
| Authorization matrix | Blocker | BE enforces `ADMIN` view/create/update/delete; `EDITOR` view/create/update; `VIEWER` view only |
| Restore | Blocker | No restore UI/API/service/mapper behavior is implemented |
| Permission-based RBAC | Blocker | No permission field/API/DB/UI mapping is added |
| List contract | Major | BE returns raw list; FE paginates client-side; no `totalCount` response field |
| Error contract | Major | ROLE APIs use standard `ErrorResponse`; duplicate role name returns HTTP 400 |
| DTO naming | Major | FE role DTO typing follows `RoleDto` contract and does not collide with auth `Role` |
| Timestamp | Major | FE displays `createdAt`/`updatedAt` as `DD/MM/YYYY HH:mm:ss`; both values are present |
| Description | Major | `description` is trimmed; empty string remains empty string |

## 3. General System Review

- [ ] Implementation is scoped to ROLE and does not change unrelated features.
- [ ] Existing conventions are followed before introducing new patterns.
- [ ] No direct updates to living docs/rules were made without promotion approval.
- [ ] No generated/build/dependency artifacts are committed accidentally.
- [ ] Stop/Ask items from `impl-plan.md` were resolved or recorded as accepted risks.
- [ ] `VIEW` was not introduced as a new role distinct from existing `VIEWER`.

## 4. FE Review

- [ ] Route `/roles` is added in `App.tsx`, not only in unmounted `router.tsx`.
- [ ] Role route remains language-aware under the existing `HashRouter`/`App` route structure.
- [ ] Role page uses the FE API layer in `src/lib/api.ts`; no direct `fetch` in components/hooks.
- [ ] Logical delete helper uses `api.put` to call `PUT /api/v1/roles/{role_id}/delete`.
- [ ] FE uses `RoleDto`-aligned typing for role master/list/detail data and does not collide with auth `Role`.
- [ ] FE display control follows the role matrix but is treated as UX only.
- [ ] `ADMIN` sees create/update/delete controls; `EDITOR` sees create/update only; `VIEWER` sees view only.
- [ ] Delete API is not called until the user confirms.
- [ ] List uses raw BE list and performs pagination client-side.
- [ ] Search/sort behavior matches the approved minimum and implementation decisions.
- [ ] Loading, empty, error, success, create, edit, detail, and delete-confirmation states exist.
- [ ] User-visible labels use i18n keys, not hardcoded UI strings.
- [ ] Timestamps display as `DD/MM/YYYY HH:mm:ss`.
- [ ] No restore UI is present.

## 5. BE / API Review

- [ ] Controller is thin and delegates business logic to service/use case.
- [ ] Current user is passed to service and used for BE authorization enforcement.
- [ ] BE does not rely on FE display control for security.
- [ ] Endpoints follow the accepted contract:
  - `GET /api/v1/roles`
  - `GET /api/v1/roles/{role_id}`
  - `POST /api/v1/roles`
  - `PUT /api/v1/roles/{role_id}`
  - `PUT /api/v1/roles/{role_id}/delete`
- [ ] List endpoint returns raw `RoleDto[]` or equivalent DTO list; no pagination wrapper.
- [ ] Duplicate role name is trim + case-insensitive, ignores rows with `delete_flag = 1`, and returns HTTP 400 `ErrorResponse`.
- [ ] Validation errors, not found, unauthorized/forbidden, and unexpected errors follow existing project error handling.
- [ ] `roleName`, `description`, `createdAt`, and `updatedAt` DTO fields match contract.
- [ ] No permission DTO/API/DB mapping is introduced.

## 6. DB / Migration Review

- [ ] Uses official table `tbl_dim_role`; does not reintroduce superseded `dim_role`.
- [ ] Old committed migrations are not edited.
- [ ] Any new migration is reviewed and named according to project convention.
- [ ] `delete_flag` exists with approved default/nullability before logical delete code depends on it.
- [ ] Delete updates `delete_flag = 1`.
- [ ] Delete updates `updated_by`; `updated_at` is updated explicitly or by verified trigger behavior.
- [ ] List/detail/search queries exclude `delete_flag = 1` by default.
- [ ] Duplicate-check queries exclude rows with `delete_flag = 1`.
- [ ] No `DELETE FROM tbl_dim_role` is used for product delete behavior.
- [ ] No cascade delete is introduced for ROLE.
- [ ] Existing FK references to `tbl_dim_role.role_id` are preserved.
- [ ] Case-insensitive uniqueness strategy is implemented and tested.
- [ ] SQL uses parameter binding; dynamic sort fields are whitelisted.

## 7. Security / Privacy Review

- [ ] BE authorization matrix cannot be bypassed by direct API calls.
- [ ] Unauthorized delete by `EDITOR`/`VIEWER` is rejected.
- [ ] Unauthorized create/update by `VIEWER` is rejected.
- [ ] Error responses do not expose SQL, stack traces, internal class names, secrets, or credentials.
- [ ] Logs do not contain secrets, credentials, tokens, raw production data, or unnecessary PII.
- [ ] TraceId or equivalent correlation remains available for API errors.
- [ ] No `.env*`, keys, credentials, or raw production logs were read/copied into artifacts or tests.

## 8. Operation / Maintenance Review

- [ ] Logical delete operational behavior is documented in ticket artifacts.
- [ ] Rollback notes preserve existing role rows and FK references.
- [ ] Any DB migration has a clear forward/rollback consideration.
- [ ] Implementation is maintainable within the existing FE/BE patterns.
- [ ] Reviewers can trace decisions to `spec-pack.md`, `sources.md`, and Pack 26 artifacts.

## 9. Test Review

- [ ] Tests cover allowed/denied role matrix.
- [ ] Tests cover raw list response and FE-side pagination.
- [ ] Tests cover create/update validation, trimming, and duplicate HTTP 400.
- [ ] Tests cover `PUT /api/v1/roles/{role_id}/delete`.
- [ ] Tests verify `delete_flag = 1`, `updated_at`, `updated_by`, and list/search exclusion.
- [ ] Tests verify row is not physically deleted and FK references are preserved.
- [ ] Tests cover `RoleDto` shape.
- [ ] Tests cover `ErrorResponse` shape.
- [ ] Tests cover timestamp display as `DD/MM/YYYY HH:mm:ss`.
- [ ] FE tests cover loading, empty, error, dialog, and confirmation behavior.
- [ ] Test results are recorded in `test-results.md`.
- [ ] Any skipped required test has reason, owner, and follow-up.

## 10. Documentation / Traceability Review

- [ ] `spec-pack.md` remains the canonical specification.
- [ ] `context.md`, `impact-analysis.md`, `impl-plan.md`, `test-plan.md`, and Pack 26 artifacts are aligned.
- [ ] `open-issues.md` is updated for remaining implementation issues.
- [ ] `promotion-candidates.md` contains candidates for living docs/rules; living docs are not updated directly.
- [ ] Any deviation from the plan is recorded in `self-review.md`.

## 11. Release / Rollback Review

- [ ] No release/merge while Blocker findings remain.
- [ ] Major findings are fixed or recorded as accepted risks.
- [ ] DB migration rollback/data preservation is documented if migration is added.
- [ ] Rollback does not physically delete `tbl_dim_role` rows.
- [ ] Tech Lead, QA, and Security Reviewer review signoff areas are clear.

## 12. AC Correspondence Table

| AC | review focus | severity |
|---|---|---|
| AC-1 | Allowed users can open `/roles`, raw list loads, FE paginates | Blocker |
| AC-2 | Unauthorized view is denied by BE using `ErrorResponse` | Blocker |
| AC-3 | `ADMIN`/`EDITOR` can create valid unique role | Blocker |
| AC-4 | Duplicate create returns HTTP 400 `ErrorResponse` | Major |
| AC-5 | Blank/whitespace role name is rejected | Major |
| AC-6 | `ADMIN`/`EDITOR` can update name/description | Blocker |
| AC-7 | Duplicate update returns HTTP 400 `ErrorResponse` | Major |
| AC-8 | `ADMIN` delete via PUT logical delete sets flag/audit and keeps row | Blocker |
| AC-9 | Deleted roles are excluded from normal list/search/query | Blocker |
| AC-10 | `EDITOR`/`VIEWER` direct delete API calls are rejected | Blocker |
| AC-11 | `VIEWER` direct create/update API calls are rejected | Blocker |
| AC-12 | Search by `role_name` works and excludes deleted records | Major |
| AC-13 | Sort by `role_name` works with safe whitelist | Major |
| AC-14 | FE pagination slices raw list; no BE pagination metadata required | Major |
| AC-15 | Screen has search/table/pagination/permitted actions | Major |
| AC-16 | Detail dialog shows role data | Major |
| AC-17 | Edit/update dialog exposes allowed fields/actions | Major |
| AC-18 | Delete confirmation appears before API call | Major |
| AC-19 | Delete confirmation copy includes target role name and logical-delete aligned wording | Minor |
| AC-20 | Create dialog has role name/description and controls | Major |
| AC-21 | `createdAt`/`updatedAt` render as `DD/MM/YYYY HH:mm:ss` | Major |
| AC-22 | Role Management labels use i18n convention | Minor |

