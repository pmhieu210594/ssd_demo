# Ticket Rules:

**Ticket ID**: PROMPT_TEMPLATE_REUSE_RATE
**Create date**: 2026-08-10
**Author**: Tech Lead (SDD Assistant)
**Update date**: 2026-08-10

## Must Follow

- Do not add specifications not included in `spec-pack.md`. `spec-pack.md` is the single source of truth; this file and `context.md`/`source-map.md` only restate it for implementation safety.
- Ambiguous points must be returned as Open Issues in `open-issues.md`, not silently resolved by the implementer.
- Before implementation, read the target source and existing tests (see `context.md` → Files Read, `source-map.md`).
- Follow existing patterns — see `context.md` → Patterns To Follow. Do not invent a new abstraction where an existing one (`ArtifactScannerSourcePort`, `MarkdownParserCore`, `PmDashboardService.requirePm`, `endpoints.pmDashboard`) already fits.
- Do not reuse obvious errors, vulnerabilities, or existing SonarLint violations found while reading the target source.
- Check for full-width numbers, half-width numbers, empty strings, nulls, digits, and precision at every boundary (see Test Focus).
- Business code values (phase codes, artifact type codes) must come from `tbl_dim_phase`/`tbl_dim_artifact_type` (enum/constant/master), never a magic string/number embedded in Java or TypeScript.
- Do not export secrets/PII to logs. Warn-level logs for skipped files must contain only file path + status code, matching the existing `isSkippableBlobError` log shape.
- Reuse `changedFilePaths` and `revision` already computed in `GithubWebhookService.handlePullRequest()` — do not add a second GitHub API round trip.
- Reuse `PmDashboardService.requirePm(caller, projectId)` unchanged, and its exact exception message `"Component.Permission.Denied"`, for the new endpoint's authorization gate.
- New migration file must be named `V510__*.sql` (confirmed next number after `V508__add_submitted_by_to_review.sql`) and must match the DDL already specified in `spec-pack.md` §12.
- New FE query/endpoint code must live inside the existing `endpoints.pmDashboard` object in `EDCAP_FE/src/lib/api.ts`, and the new DTO interface must be declared in that same file, not in `pages/pm-dashboard/types.ts`.

## Must Not Do

- Do not call `GithubWebhookService.ticketKeyFromPath(...)` or `GithubSecurityEvidenceSnapshotService.isSkippableBlobError(...)` from a new/different class — both are `private` in their respective classes (the latter is a private **instance** method, not static) and are not accessible as-is. Either implement inside the owning class, or explicitly promote/duplicate the logic as a recorded impl-plan decision.
- Do not create a second GitHub HTTP client/adapter. Use `ArtifactScannerSourcePort` (`resolveRevision`/`listTree`/`readBlob`) exclusively.
- Do not write a new markdown/header parser. Use `MarkdownParserCore.parse(content, sourcePath).sections()` exclusively for both the changed file and its template.
- Do not hardcode the 8 phases or their file lists in a Java `switch`/enum/if-chain. All phase/file mapping must be data-driven via `tbl_dim_phase`/`tbl_dim_artifact_type` (raw requirement §5, spec-pack §5 — extensibility requirement).
- Do not invent a new API path such as `GET /api/template-usage/statistics` (the literal text from the raw requirement) — the approved path is `GET /api/v1/pm/dashboard/template-usage` on the existing `PmDashboardController`.
- Do not add a new `ResponseEntity` status code directly in `PmDashboardController` for error cases — all exception→HTTP mapping goes through `GlobalExceptionHandler`.
- Do not call `fetch()` directly anywhere in FE code outside `EDCAP_FE/src/lib/api.ts`.
- Do not introduce a new Redux slice or Zustand store for this feature's data — it is server data and must go through TanStack Query.
- Do not reuse or extend `formatScore` (`pm-dashboard/utils.ts`) for the new `usageRate` display — it is a null-guard formatter only, with no zero-total guard. Write a new small formatter instead.
- Do not open a DB transaction that spans the GitHub blob-read loop. External HTTP calls must complete outside any open `TransactionTemplate`, per `docs/.claude/rules/20-architecture.md`.
- Do not use `@Data`, or field injection (`@Autowired` on a field), on any new class — constructor injection + `@Builder @Getter @Setter @NoArgsConstructor @AllArgsConstructor` only, per `docs/.claude/rules/10-style.md`.
- Do not display the raw percentage (or `0`/`NaN`) when `totalCheckCount == 0` — must render `-`.
- Do not treat a file under `documents/standards/templates/**` itself as a validation *target* — it is only ever the comparison *source*.

## Stop / Ask Conditions

- If `TARGET_SCOPE` (left empty in `raw/rules.md`) needs to be narrower or wider than the scope inferred in `context.md` (webhook validation, `V510` migration, new `template-usage` endpoint, new `PMDashboardPage.tsx` section) — stop and confirm with the human before Phase 3 implementation planning proceeds further.
- If implementing the new validation logic requires promoting `ticketKeyFromPath` or `isSkippableBlobError` out of their current private scope (a small refactor of existing production code, not purely additive) — flag this explicitly in `impl-plan.md` and get confirmation before touching `GithubWebhookService.java`/`GithubSecurityEvidenceSnapshotService.java`'s existing method signatures.
- If any changed file in a PR maps to a phase/artifact-type combination with no `tbl_dim_artifact_type` row other than the already-accepted `IMPACT_ANALYSIS` case — stop and ask whether this is a new gap requiring a V510 addition, rather than silently excluding it.
- If a database migration, `git commit`, or `git push` is about to be run — stop and get explicit human approval first (per `docs/.claude/rules/00-safety.md` §3).
- If test coverage conventions conflict (which of the two `src/test/**` roots to use) for a class that has no existing sibling test yet — ask rather than guessing.

## Review Focus

- New validation loop reuses `changedFilePaths`/`revision` from the existing `handlePullRequest` flow (no duplicate GitHub calls).
- Per-file fetch failure handling matches the `isSkippableBlobError` classification (401/403/404 → skip+warn+continue; else → fail), even though the method itself is not called directly.
- `requirePm(caller, projectId)` is the first line of the new service method; exception message is exactly `"Component.Permission.Denied"`.
- No hardcoded phase/file switch anywhere; all mapping flows through `tbl_dim_phase`/`tbl_dim_artifact_type`.
- DB counter writes are isolated in their own `TransactionTemplate`, outside/after GitHub HTTP calls.
- FE `usageRate` renders `-` (not `0`/`NaN`) when `totalCheckCount === 0`, via a new dedicated formatter.
- FE new query's `enabled` matches `PMDashboardPage.tsx`'s real 4-flag gating condition, not a simplified version.
- New FE types/endpoint method live in `api.ts` next to their siblings, not in `pages/pm-dashboard/types.ts`.
- V510 migration DDL matches `spec-pack.md` §12 exactly — no unapproved column/constraint changes.

## Test Focus

- Boundary values: `totalCheckCount == 0` (API/FE must show `-`); PASS vs FAIL binary determination; header order/level mismatch cases (structure differs but content is identical — must FAIL); identical structure with entirely different body content (must PASS).
- Error paths: template not found at any of the 3 priority locations → skip, no exception; 401/403/404 on blob/tree fetch → skip that file, continue others; any other HTTP error on blob/tree fetch → propagate/fail.
- Permission: non-PM caller on the target project → `ForbiddenException("Component.Permission.Denied")`; PM role on a *different* project → still denied; ADMIN system role → always allowed regardless of project-level role.
- Dimension-mapping gap: a changed file resolving to a phase/artifact-type combination with no `tbl_dim_artifact_type` row → excluded from statistics, not counted as FAIL, not an error.
- Revision correctness: PR that itself edits the template file → template content must still be read from the same PR head revision (not `main`/default branch, not a stale cached copy).
- Follow `GithubWebhookServiceTest.java`'s manual-mock style for new webhook-side tests; follow `PmDashboardServiceTest.java`'s `@Mock`/AssertJ style for new PM-dashboard-side tests (see `context.md` → Test Notes for exact conventions).
- No existing unit test exists for `MarkdownParserCore` itself — the new structure-comparison logic needs test coverage written from scratch, including both PASS and FAIL header-structure cases.
