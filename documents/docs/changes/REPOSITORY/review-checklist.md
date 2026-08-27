# Review Checklist

**Ticket ID**: REPOSITORY-CRUD  
**Create date**: 2026-06-19  
**Author**: Codex  
**Update date**: 2026-06-19

## Sources Of Truth

Use these documents as the baseline for review:

- [`spec-pack.md`](spec-pack.md)
- [`context.md`](context.md)
- [`impact-analysis.md`](impact-analysis.md)
- [`impl-plan.md`](impl-plan.md)
- [`docs/standards/api-contract.md`](../../standards/api-contract.md)
- [`docs/standards/backend.md`](../../standards/backend.md)
- [`docs/standards/database.md`](../../standards/database.md)
- [`docs/standards/error-handling.md`](../../standards/error-handling.md)
- [`docs/standards/frontend.md`](../../standards/frontend.md)
- [`docs/standards/logging.md`](../../standards/logging.md)
- [`docs/standards/maintenance.md`](../../standards/maintenance.md)
- [`docs/standards/review.md`](../../standards/review.md)
- [`docs/standards/security.md`](../../standards/security.md)
- [`docs/standards/testing.md`](../../standards/testing.md)
- [`.claude/rules/00-safety.md`](../../../.claude/rules/00-safety.md)
- [`.claude/rules/10-style.md`](../../../.claude/rules/10-style.md)
- [`.claude/rules/20-architecture.md`](../../../.claude/rules/20-architecture.md)
- [`.claude/rules/30-security.md`](../../../.claude/rules/30-security.md)
- [`.claude/rules/40-testing.md`](../../../.claude/rules/40-testing.md)

## How To Use

- Mark every item that applies to the final implementation.
- Record any failure as a concrete finding with severity.
- Prefer evidence from code, tests, migration files, or runtime output.
- If a decision remains unresolved in the spec, call it out instead of guessing.

## Severity

- `Blocker`: release-breaking, security-breaking, data-loss risk, or contract breakage that must be fixed before merge.
- `Major`: correctness, maintainability, or supportability issue that materially weakens the feature.
- `Minor`: polish, documentation, or low-risk improvement that can be scheduled later.

## 1. Spec / AC Alignment

- [ ] Implemented behavior matches `spec-pack.md` scope and out-of-scope boundaries.
- [ ] Acceptance criteria `AC-REPOSITORY-1` through `AC-REPOSITORY-8` are all traceable in code or tests.
- [ ] Repository list excludes soft-deleted rows by default.
- [ ] Repository detail behavior for soft-deleted rows matches the confirmed business rule in the spec.
- [ ] Repository name normalization, validation, and duplicate scope match the spec.
- [ ] Any unresolved item from `spec-pack.md` is surfaced explicitly, not silently assumed.

## 2. General System Review

- [ ] End-to-end data flow is consistent across FE, BE/API, and DB.
- [ ] Naming and API shape match existing platform conventions.
- [ ] No unrelated feature behavior changed as a side effect.
- [ ] Error handling follows the platform contract and preserves traceability.
- [ ] The implementation stays within the documented architecture boundaries.

## 3. FE Review

- [ ] Route, page, drawer, list, and mutation flows match the existing CRUD patterns.
- [ ] FE uses `src/lib/api.ts` helpers and does not call `fetch` directly.
- [ ] User-visible strings are localized and no hardcoded English was introduced.
- [ ] Validation is present in the UI but does not replace backend validation.
- [ ] Deleted-row UI behavior is read-only and does not expose disallowed actions.
- [ ] Success/error refresh behavior matches the user workflow in the spec.

## 4. BE/API Review

- [ ] Controller endpoints are under `/api/v1/repositories`.
- [ ] Success responses remain raw DTOs or raw DTO lists.
- [ ] Error responses use `ErrorResponse` and the global exception flow.
- [ ] Authorization is enforced on every mutating action.
- [ ] Duplicate-name and not-found behavior matches the spec.
- [ ] Soft delete is implemented without physical deletion.

## 5. DB/Migration Review

- [ ] Storage uses `tbl_dim_repository` and V5+ naming conventions.
- [ ] Migration changes are additive only and do not edit committed migration files.
- [ ] Soft-delete columns and active-row filtering are present and correct.
- [ ] Uniqueness enforcement matches the active-row project-scoped rule.
- [ ] Any DB constraint or index change is covered by tests or migration evidence.

## 6. Security/Privacy Review

- [ ] No secrets, tokens, or credentials were added to code, docs, logs, or tests.
- [ ] Permission checks are backend-enforced and not replaced by FE-only hiding.
- [ ] Error messages do not leak stack traces or sensitive internal state.
- [ ] Trace/correlation handling is preserved without exposing unnecessary PII.
- [ ] Any repository URL handling follows the documented raw-display / encoded-storage rule.

## 7. Operation/Maintenance Review

- [ ] Soft delete preserves history and does not create maintenance surprises.
- [ ] Logging and traceability are sufficient for support and incident diagnosis.
- [ ] The implementation aligns with existing admin CRUD maintenance patterns.
- [ ] Rollback/recovery considerations are documented or evident from the implementation.
- [ ] Future backfill or cleanup work would still respect active-row uniqueness.

## 8. Test Review

- [ ] FE unit tests cover API helper behavior, validation, and screen interactions.
- [ ] BE unit tests cover core business rules and duplicate detection.
- [ ] BE web tests cover permission, validation, not-found, and error-shape behavior.
- [ ] DB or migration tests cover schema and uniqueness behavior where applicable.
- [ ] E2E tests cover the main CRUD journey and deleted-row behavior.
- [ ] Test names and assertions map back to the AC table below.

## 9. Documentation / Traceability Review

- [ ] The implementation links back to the ticket docs and current standards.
- [ ] File names, paths, and route names are documented consistently.
- [ ] Decisions and assumptions are separated from confirmed facts.
- [ ] Any gap between spec and implementation is called out explicitly.
- [ ] The self-review template can be completed from the actual work artifacts.

## 10. Release / Rollback Review

- [ ] The change can be released incrementally without breaking unrelated features.
- [ ] Rollback steps are feasible for FE, BE, and DB separately.
- [ ] No committed migration was edited in place.
- [ ] Any deployment or feature-flag dependency is identified if applicable.
- [ ] The release path preserves current platform behavior for unaffected routes.

## 11. AC Mapping

| AC | What to verify | Evidence to look for | Severity if missing |
|---|---|---|---|
| AC-REPOSITORY-1 | `GET /api/v1/repositories` returns only active rows in the expected page shape | List API response, FE list view, list test | Blocker |
| AC-REPOSITORY-2 | Detail API returns repository payload including parent project name | Detail API, FE drawer/detail view, detail test | Major |
| AC-REPOSITORY-3 | Valid create request persists a repository and returns created payload | Create form, API response, create test | Blocker |
| AC-REPOSITORY-4 | Valid update request persists changes and rejects duplicates in the same project | Update flow, duplicate rule test | Blocker |
| AC-REPOSITORY-5 | Soft delete only, with removal from default list results | Delete endpoint, list refresh, delete test | Blocker |
| AC-REPOSITORY-6 | Invalid input, missing ID, or missing permission returns platform-standard error shape | Web/API tests, error handling evidence | Blocker |
| AC-REPOSITORY-7 | FE refreshes list/detail state and shows expected messaging after mutation | FE mutation tests, E2E journey | Major |
| AC-REPOSITORY-8 | Name trimming is applied before validation and persistence | Form normalization, backend trim/validation test | Major |

## 12. Review Findings Log

| ID | Area | Severity | Finding | Evidence | Recommended fix |
|---|---|---|---|---|---|
| F-001 |  |  |  |  |  |
| F-002 |  |  |  |  |  |
| F-003 |  |  |  |  |  |
