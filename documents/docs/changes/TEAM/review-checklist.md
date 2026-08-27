# Review Checklist

**Ticket ID**: TEAM  
**Create date**: 2026-06-15  
**Author**: nk_trung  
**Update date**: 2026-06-15  
**Phase**: Phase 4 - Review Checklist / Self Review Skeleton  
**Status**: Prepared for post-implementation review

## 0. Review Scope

This checklist is for reviewing the TEAM implementation after code changes are completed. It is based on:

- `docs/changes/TEAM/spec-pack.md`
- `docs/changes/TEAM/context.md`
- `docs/changes/TEAM/impact-analysis.md`
- `docs/changes/TEAM/impl-plan.md`
- `docs/standards/`
- `.claude/rules/`

Reviewers must verify implementation evidence before marking any item as passed. Do not mark `PASS` based only on this checklist.

## 1. Specification/AC Matching

| AC ID | Review Point | Severity | Result |
|---|---|---|---|
| AC-TEAM-1 | Only users with `ADMIN` role can view/use the Teams list/screen/API. Non-admin and unauthenticated users are blocked. | Blocker | TODO |
| AC-TEAM-2 | Team search supports Team Code and Team Name, trims input, and does not allow unsafe sort/filter SQL. | Major | TODO |
| AC-TEAM-3 | ADMIN can create a Team with valid Team Code and Team Name. Create response uses the selected contract, planned as `201 Created`. | Blocker | TODO |
| AC-TEAM-4 | Team Code can be updated after creation. | Major | TODO |
| AC-TEAM-5 | Duplicate Team Code create/update is rejected according to the defined active unique scope. | Blocker | TODO |
| AC-TEAM-6 | Team detail returns/displays basic Team information and active Team members. | Major | TODO |
| AC-TEAM-7 | ADMIN can update basic Team information. | Blocker | TODO |
| AC-TEAM-8 | ADMIN can soft-delete/inactive a Team. No user-facing hard delete is implemented. | Blocker | TODO |
| AC-TEAM-9 | Deleting/inactivating a Team also inactivates all active memberships in the same consistent operation. | Blocker | TODO |
| AC-TEAM-10 | User can add an existing selectable member to a Team from Team detail. | Blocker | TODO |
| AC-TEAM-11 | Adding a member requires selecting a role from existing `tbl_dim_role`; invalid/missing role is rejected. | Blocker | TODO |
| AC-TEAM-12 | The same member can belong to multiple different Teams. | Major | TODO |
| AC-TEAM-13 | In the same Team, one member has at most one active membership and one active role. | Blocker | TODO |
| AC-TEAM-14 | Adding a duplicate active member to the same Team is rejected with an appropriate business error. | Blocker | TODO |
| AC-TEAM-15 | Updating a Team member role updates the existing active membership and does not create another active membership. | Blocker | TODO |
| AC-TEAM-16 | Removing a member from a Team inactivates the membership and does not delete the member master record. | Blocker | TODO |
| AC-TEAM-17 | TEAM implementation does not add Team-to-Project assignment UI/API/business logic. | Major | TODO |
| AC-TEAM-18 | Teams UI labels, buttons, placeholders, validation messages, and business error messages support `ja`, `vi`, and `en`. | Major | TODO |
| AC-TEAM-19 | No dedicated Team audit log module/table is added. Only existing standard metadata/logging is used if available. | Minor | TODO |
| AC-TEAM-20 | No migration/backfill is implemented from `tbl_dim_member_pseudonym.team_id/role_id`. | Major | TODO |

## 2. General System Review

### 2.1. Number/Input Check

- [ ] [Major] Clear numeric validation exists for `page`, `size`, optional `version`, and any numeric request field.
- [ ] [Major] Full-width numbers are either processed consistently by existing frontend/backend policy or clearly rejected with validation.
- [ ] [Major] Half-width/full-width mixed numbers are considered for user-facing input where applicable.
- [ ] [Blocker] Empty string/null/blank-after-trim for required fields such as `teamCode`, `teamName`, `memberKey`, and `roleId` is rejected.
- [ ] [Major] Length limits for `teamCode`, `teamName`, and `description` follow DB/API/frontend standards and avoid truncation.
- [ ] [Major] Pagination and sort inputs cannot overflow, underflow, or bypass allowlists.

### 2.2. Character Type / Encoding / Locale

- [ ] [Major] Team Code/Name trim policy is implemented consistently in FE and BE.
- [ ] [Major] Full-width/half-width/emoji/surrogate pair behavior is considered for Team Name and Description.
- [ ] [Minor] Unicode normalization is considered or explicitly not applied based on existing project policy.
- [ ] [Blocker] No mojibake or invalid encoding in `ja`, `vi`, `en` locale resources.
- [ ] [Major] Japanese/Vietnamese/English messages are not missing or obviously mistranslated.

### 2.3. Literal / Magic Number

- [ ] [Major] Business status values such as `ACTIVE`/`INACTIVE` use existing enum/constant/code value style.
- [ ] [Major] No hard-coded business code values are scattered across FE/BE when constants or DB/master lookup should be used.
- [ ] [Major] Role display/internal values are clearly separated: role option value uses role id/key, display uses localized or DB display name as designed.
- [ ] [Minor] Default page size, max page size, and sort fields are not magic numbers without explanation.

### 2.4. Operation / Maintainability

- [ ] [Major] Code follows the existing Organization/Customer CRUD style where applicable.
- [ ] [Blocker] Backend respects hexagonal layering: web -> application -> domain; infrastructure implements outbound ports.
- [ ] [Major] Frontend uses existing API layer and page/component patterns; no unnecessary new global state pattern is introduced.
- [ ] [Major] Implementation avoids duplicate business logic between controller/service/mapper or FE/BE.
- [ ] [Major] Configuration is not hard-coded if environment/configuration should be used.
- [ ] [Major] Existing Organization/Customer/Auth behavior is not regressed.

## 3. FE Review

- [ ] [Blocker] Team screen route is protected so only ADMIN can access it.
- [ ] [Major] Team navigation/menu entry is visible only when appropriate and does not break existing layout.
- [ ] [Major] Team list supports keyword search by Team Code/Name and handles loading/empty/error states.
- [ ] [Major] Team create/update form validates required fields before submit and displays server validation/business errors.
- [ ] [Major] Team detail displays basic Team fields and active member list.
- [ ] [Blocker] Add-member form uses minimal lookup endpoints for selectable active members and active roles; it does not create member/role master data.
- [ ] [Blocker] Add-member form requires role selection.
- [ ] [Blocker] Update role action changes only the selected existing membership.
- [ ] [Blocker] Remove member action inactivates membership and does not remove member master from selectable source.
- [ ] [Major] Delete Team action uses confirmation and clearly communicates soft-delete/inactive behavior.
- [ ] [Major] FE API helper/types in `src/lib/api.ts` match BE request/response contract.
- [ ] [Major] TanStack Query/cache invalidation or existing fetch pattern refreshes list/detail/member data after mutations.
- [ ] [Major] All visible text, placeholders, validation messages, and business error messages use i18n keys for `ja`, `vi`, and `en`.
- [ ] [Major] No out-of-scope Project selector or Team-Project assignment UI is introduced.
- [ ] [Major] No legacy/inactive router/global CRUD pattern is used unless the current app wiring proves it is required.

## 4. BE/API Review

- [ ] [Blocker] Team REST endpoints require authentication and ADMIN authorization.
- [ ] [Blocker] Controller delegates business logic to service/usecase; no SQL or core business rule is implemented in controller.
- [ ] [Blocker] Team create/update/list/detail/delete APIs follow the existing API base path and response wrapper/error convention.
- [ ] [Major] Team create uses `201 Created` unless a documented owner decision chooses another status.
- [ ] [Blocker] Duplicate Team Code validation is enforced in service and supported by DB constraint/index where possible.
- [ ] [Blocker] Delete Team and inactive memberships happen in one transactionally consistent service operation.
- [ ] [Blocker] Add member validates active Team, existing selectable member, existing role, and non-duplicate active membership.
- [ ] [Blocker] Update member role validates active Team and active membership, and updates existing relation only.
- [ ] [Blocker] Remove member performs soft delete/inactive on the membership only.
- [ ] [Major] Minimal member/role lookup endpoints are read-only and do not become member/role master management APIs.
- [ ] [Major] API does not expose unnecessary member personal data; returns only fields needed for selection/management.
- [ ] [Major] Error codes include or map correctly to expected cases such as `TEAM_CODE_DUPLICATED`, `TEAM_MEMBER_ALREADY_EXISTS`, `TEAM_NOT_FOUND`, `ROLE_NOT_FOUND`, and `FORBIDDEN`.
- [ ] [Major] Mapper SQL uses safe parameters and sort allowlists; no string concatenation for untrusted input.
- [ ] [Major] Backend compile and tests confirm mapper/DTO/type consistency.

## 5. DB/Migration Review

- [ ] [Blocker] Migration creates or updates only TEAM-scope schema such as `tbl_team_member` and safe Team metadata/indexes.
- [ ] [Blocker] `tbl_team_member` has a primary key and references Team, member source, and role source according to current schema conventions.
- [ ] [Blocker] Active uniqueness for `(team_id, member_key)` is enforced or equivalent logic is clearly justified and tested.
- [ ] [Major] Indexes support Team list/detail/member queries such as `team_id,status` and duplicate checks.
- [ ] [Blocker] No hard delete path or destructive migration is introduced without explicit approval.
- [ ] [Blocker] `tbl_dim_team.project_id` is not dropped unless dependency check and approval are documented; default Phase 3 action is ignore/deprecate.
- [ ] [Major] No migration/backfill from `tbl_dim_member_pseudonym.team_id/role_id` is included.
- [ ] [Major] Migration version is correct and does not conflict with existing Flyway versions.
- [ ] [Major] Rollback/manual recovery notes exist for any schema change.
- [ ] [Major] DB naming follows `docs/standards/database.md` for table, PK, FK, indexes, timestamps, and enum/status columns.

## 6. Security/Privacy Review

- [ ] [Blocker] Non-ADMIN users cannot access Team APIs even if they call endpoints directly.
- [ ] [Blocker] Unauthenticated users receive expected authentication failure, not Team data.
- [ ] [Blocker] No token, password, session, refresh token, or secret is logged or exposed.
- [ ] [Major] Lookup responses expose minimum member information needed; no unnecessary personal/profile fields are returned.
- [ ] [Major] Error responses do not leak sensitive DB/internal details.
- [ ] [Major] Authorization is checked server-side, not only in FE route/menu.
- [ ] [Major] No global auth redesign is made in TEAM; existing bearer-token source behavior is followed and documentation drift is only recorded.
- [ ] [Major] CSRF/session-cookie assumptions from older docs are not introduced into this bearer-token implementation.

## 7. Operation/Maintenance Review

- [ ] [Major] Logs are sufficient to investigate failed Team create/update/delete/member operations without logging sensitive data.
- [ ] [Major] Parameterized logging is used; no string concatenation with sensitive/user input in logs.
- [ ] [Major] Delete Team + inactive memberships can be investigated by operation staff through DB state and standard metadata.
- [ ] [Major] No connector, batch, webhook, or external sync behavior is changed by this ticket.
- [ ] [Minor] Any new configuration, if added, has default and operational notes.
- [ ] [Major] Incident recovery/manual correction path is documented for migration or data consistency issues.

## 8. Test Review

- [ ] [Blocker] Tests cover ADMIN allowed and non-ADMIN/unauthenticated forbidden behavior for Team screen/API.
- [ ] [Blocker] Tests cover create, search, detail, update, soft delete Team.
- [ ] [Blocker] Tests cover delete Team inactivates active memberships.
- [ ] [Blocker] Tests cover add member, duplicate member rejection, multi-Team membership allowed, update role, and remove member.
- [ ] [Major] Tests cover missing/invalid role and missing/invalid member behavior.
- [ ] [Major] Tests cover minimal member/role lookup endpoints.
- [ ] [Major] Tests cover i18n key existence/locale switch where FE test environment supports it.
- [ ] [Major] Tests cover DB migration constraints/indexes at least through integration/migration verification.
- [ ] [Major] Test commands and exact results are recorded in `test-results.md`; do not claim pass without rerun.
- [ ] [Major] Existing Organization/Customer/Auth regression risk is checked by targeted tests or documented if environment blocks execution.

## 9. Documentation/Traceability Review

- [ ] [Blocker] Implementation is traceable to AC-TEAM-1 through AC-TEAM-20.
- [ ] [Major] `impact-analysis.md` remains accurate after actual files are changed.
- [ ] [Major] `impl-plan.md` is updated if implementation deviates from planned steps.
- [ ] [Major] `test-plan.md`, `test-results.md`, `blackbox-testcases.md`, and `report.md` are updated in later phases with evidence.
- [ ] [Major] `self-review.md` lists all changed files and commands run.
- [ ] [Major] Open issues and accepted risks have status/owner or clear follow-up.
- [ ] [Major] Documentation does not claim `PASS`, `DONE`, or production readiness without evidence.

## 10. Release/Rollback Review

- [ ] [Blocker] Rollout plan accounts for DB migration before deploying BE/FE features that depend on it.
- [ ] [Blocker] Rollback plan covers app rollback and DB handling for `tbl_team_member` after writes occur.
- [ ] [Major] Feature can be hidden/route disabled if needed without breaking existing screens.
- [ ] [Major] If migration is additive, rollback/data preservation policy is documented.
- [ ] [Blocker] Any destructive change, especially around `tbl_dim_team.project_id`, has approval and dependency evidence.
- [ ] [Major] Release notes mention ADMIN-only Team management and no Team-Project assignment in this ticket.

## 11. Severity Definition

| severity | meaning | required action |
|---|---|---|
| Blocker | Cannot be released or merged safely if failing. Usually security, data integrity, AC-critical, destructive migration, or compile/runtime blocker. | Must fix before merge/release. |
| Major | High probability of becoming a defect, maintenance problem, or user-visible inconsistency. | Fix or record accepted risk with owner/reason. |
| Minor | Improvement, cleanup, or low-risk consistency issue. | Optional, but record decision if not fixed. |
| Question | Specification confirmation required. | Open or update an issue/decision record. |
| False Positive | Review finding is not applicable or incorrect. | Record reason for rejection. |
| Accepted Risk | Known issue accepted by owner. | Record impact, owner, and follow-up/deadline where appropriate. |

## 12. AC Traceability Matrix

| AC ID | Spec summary | Design / implementation area | Required review evidence | Severity |
|---|---|---|---|---|
| AC-TEAM-1 | ADMIN only can view Teams list | FE route/menu guard; BE authorization | Auth tests/manual API evidence | Blocker |
| AC-TEAM-2 | Search by Team Code/Name | FE search form; `GET /api/v1/teams`; mapper filter | API/FE tests for both fields | Major |
| AC-TEAM-3 | ADMIN can create Team | Create DTO/service/controller/form | Create success/validation test; `201 Created` evidence | Blocker |
| AC-TEAM-4 | Team Code editable | Update DTO/service/form | Update Team Code test | Major |
| AC-TEAM-5 | Duplicate Team Code rejected | Service duplicate check; DB unique active rule | Duplicate create/update tests | Blocker |
| AC-TEAM-6 | Team detail includes members | Detail endpoint/page/member query | Detail API/UI test | Major |
| AC-TEAM-7 | ADMIN can update basic info | Update endpoint/service/form | Update success/authorization test | Blocker |
| AC-TEAM-8 | Soft delete Team | Delete/inactive endpoint/service | Soft delete test; no hard delete evidence | Blocker |
| AC-TEAM-9 | Delete Team inactive memberships | Transaction/service/mapper | Integration test after Team delete | Blocker |
| AC-TEAM-10 | Add existing member | Member lookup; add-member endpoint/form | Add member test using existing member | Blocker |
| AC-TEAM-11 | Role required from `tbl_dim_role` | Role lookup; validation | Missing/invalid role test | Blocker |
| AC-TEAM-12 | Member can be in multiple Teams | Membership uniqueness scoped by Team | Same member in two Teams test | Major |
| AC-TEAM-13 | One active membership/role in same Team | `tbl_team_member` rule/constraint/service | Duplicate active membership test | Blocker |
| AC-TEAM-14 | Duplicate active member rejected | Service/business error mapping | `TEAM_MEMBER_ALREADY_EXISTS` test | Blocker |
| AC-TEAM-15 | Update role without new membership | Update membership endpoint/service | Relation id unchanged test | Blocker |
| AC-TEAM-16 | Remove member inactive only | Remove membership endpoint/service | Member master remains test | Blocker |
| AC-TEAM-17 | No Team-Project assignment | No project route/form/mapper behavior | Code review/grep/manual check | Major |
| AC-TEAM-18 | i18n `ja`/`vi`/`en` | Locale files and error mapping | Locale key/build/i18n tests | Major |
| AC-TEAM-19 | No dedicated Team audit log | Migration/source diff | Review evidence no audit table/module | Minor |
| AC-TEAM-20 | No legacy member migration | Migration scripts | Migration grep/review evidence | Major |

## 13. Open Issue / Risk Review

| ID | expected review handling | severity |
|---|---|---|
| OI-TEAM-P3-001 | Verify `tbl_dim_team.project_id` is ignored/deprecated by default, or dropped only with documented dependency check and approval. | Blocker if destructive |
| OI-TEAM-P3-002 | Verify minimal read-only member/role lookup endpoints are implemented and remain in TEAM scope only. | Major |
| OI-TEAM-P3-003 | Verify Team create status is `201 Created` unless project owner decides otherwise. | Major |
| OI-TEAM-P3-004 | Verify TEAM does not redesign auth and follows current bearer-token source behavior. | Major |
| OI-TEAM-P3-005 | Verify test environment failures, if any, are recorded exactly in `test-results.md`. | Major |
