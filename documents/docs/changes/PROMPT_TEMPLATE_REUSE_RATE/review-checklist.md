# Review Checklist

**Ticket ID**: PROMPT_TEMPLATE_REUSE_RATE
**Create date**: 2026-08-11
**Author**: Principal Reviewer (pre-implementation review viewpoints)
**Update date**: 2026-08-11

> Scope note: this checklist documents **review viewpoints to apply after implementation** — it is written before any source code changes exist. The `Result` column is intentionally left blank; it is filled in during the actual code review (see `self-review.md` for the implementer's own pass first). Items that are genuinely undecided as of this writing are explicitly marked **Undetermined** rather than given an assumed answer — they must be resolved (via `open-issues.md` if they turn out to be specification gaps) before being marked with a real result.

## 1. Specification/AC Matching

| AC ID | Review Point | Severity | Result |
|---|---|---|---|
| AC-1 | Only files under the ticket's working-directory scope (`/changes/` path) are considered for template validation; files outside that scope are never validated or counted | Blocker | |
| AC-2 | The 3-tier template-resolution priority order is applied in order, and validation uses the **first** match found (not the most specific/last) | Blocker | |
| AC-3 | When no template is found for a file: no exception is thrown, no counter (`total_check_count`/`template_match_count`) changes, and no error is surfaced to the webhook caller | Blocker | |
| AC-4 | Structure comparison uses only header name, level, and order; a body-content-only diff never produces a FAIL | Blocker | |
| AC-5 | `total_check_count` increments by exactly 1 per validated file; `template_match_count` increments by exactly 1 only on PASS — verify against multi-file PRs (N files → N total, M passes → M match) | Blocker | |
| AC-6 | Adding a new phase or file→phase mapping via a DB row only (no code deploy) is picked up by both validation and statistics on the next webhook event | Major | |
| AC-7 | `GET /api/v1/pm/dashboard/template-usage?projectId=...&repositoryId=...` returns exactly one entry per phase for the given project+repository, with fields `phaseCode, phaseName, totalCheckCount, templateMatchCount, usageRate` | Blocker | |
| AC-8 | `usageRate = templateMatchCount/totalCheckCount*100`, rounded to exactly 1 decimal place; value is `null` (FE renders `-`) when `totalCheckCount == 0`; never `0`, `0.0`, or `NaN` for the zero-total case | Blocker | |
| AC-9 | `PMDashboardPage.tsx` renders a "Template Usage by Phase" section immediately after `AllTicketsTable`, showing phase name, total check count, template match count, usage rate (or `-`) per phase | Major | |
| AC-10 | The FE query is a `useQuery` following the `["pm-dashboard", ...]` key convention, `enabled` only when both `filters.projectId` and `filters.repositoryId` are set | Major | |
| AC-11 | `GET /api/v1/pm/dashboard/template-usage` is restricted to PM-role users for the given `projectId` (ADMIN also allowed); a non-authorized caller receives `403 Component.Permission.Denied`, identical in shape to other PM Dashboard endpoints | Blocker | |

## 2. General System Review

### Số, số full-width, số chữ số, độ chính xác (Numbers, Full-Width Numbers, Digit Count, Precision)

- [ ] `usageRate` rounding is implemented as exactly `Math.round(templateMatchCount * 1000.0 / totalCheckCount) / 10.0` (or an equivalently exact fixed-point method) — not `String.format`/locale-dependent formatting, which could silently apply grouping separators or locale-specific decimal marks
- [ ] `usageRate` is never re-rounded on the FE; the FE only appends the `%` sign to the value already rounded by the BE
- [ ] `usageRate == null` is rendered as `-` on FE — verify it is never `0`, `0.0`, `NaN`, or an empty string for the `totalCheckCount == 0` case
- [ ] `total_check_count` / `template_match_count` are non-negative integers with no realistic overflow path (webhook-driven increments only, `BIGINT` column type) — confirm DB `CHECK` constraints (`>= 0`, `template_match_count <= total_check_count`) are present and match the increment logic exactly
- [ ] No user-typed numeric input exists in this ticket's scope (counts are server-computed from GitHub file lists, not typed by a human) — full-width/half-width digit input handling is **not applicable**; confirm this assumption still holds for the actual implementation (i.e., no new free-text numeric filter/field was added to the FE that would need full-width normalization)
- [ ] Repeating-decimal case is explicitly covered (e.g. `2/3 * 100 = 66.666...` → must round to `66.7`, not truncate to `66.6` or `66`)

### Loại ký tự, encoding, locale (Character Type / Encoding / Locale)

- [ ] Markdown header text comparison (AC-4) compares header **name** as extracted by `MarkdownParserCore` — confirm no mojibake when reading GitHub blob content (base64-decoded, then decoded as UTF-8) for both the changed file and the resolved template file
- [ ] Header name comparison behavior on trailing/leading whitespace, full-width space (`　`), and case sensitivity is deterministic and documented somewhere in code (not accidental exact-`equals()` behavior that happens to work on today's templates)
- [ ] `phaseName` returned by the new endpoint and rendered on FE reflects `tbl_dim_phase.phase_name` as stored — confirm the locale/language of these values (Vietnamese/English) is consistent with the rest of `PMDashboardPage.tsx`'s existing labels — **Undetermined**: no explicit locale requirement was found in `spec-pack.md` for this field; flag for confirmation rather than assuming
- [ ] No new user-facing string in `TemplateUsageByPhase.tsx` is misspelled or left in a placeholder/lorem state

### Literal / Magic Number / Master Data

- [ ] Phase and artifact-type identifiers are resolved via `tbl_dim_phase` / `tbl_dim_artifact_type` (master data), never as hardcoded string/UUID literals in Java or TypeScript
- [ ] The 3 new `tbl_dim_artifact_type` seed rows (`OPEN_ISSUES`, `CONTEXT`, `CODEX_REVIEW`) use the same `ON CONFLICT DO NOTHING` idempotent insert pattern as the existing `V234` seed, not a plain `INSERT`
- [ ] HTTP status codes used for the skippable-fetch-error classification (401/403/404) are compared against named constants or the actual exception type, not raw magic-number literals scattered in the new code
- [ ] The `1` (decimal-place count) and `100` (percentage multiplier) in the rounding formula are self-evidently business-rule constants tied to a single documented spec point (`spec-pack.md` Terminology / AC-8) — not duplicated as separate unexplained literals in more than one place (BE service vs. any FE fallback formatter)
- [ ] Column names used in the `ON CONFLICT (project_id, repository_id, phase_id) DO UPDATE` clause exactly match the migration's `UNIQUE` constraint definition — a mismatch here fails silently as a full-insert-conflict error only at runtime, not at compile time

### Chuyển trạng thái, boundary value, exception (State Transition, Boundary Value, Exception)

- [ ] First-ever webhook event for a given `(project_id, repository_id, phase_id)` triple takes the `INSERT` branch of the upsert (row does not yet exist) — verify this path is tested, not just the `DO UPDATE` branch
- [ ] `totalCheckCount == 0` boundary (no file for this project/repository/phase has ever been validated) → `usageRate: null`, verified at both the SQL/service layer and the FE render layer
- [ ] `templateMatchCount == totalCheckCount` boundary (100% usage) → `usageRate: 100.0`, not `100` (int) or omitted decimal
- [ ] A skippable blob/tree fetch error (401/403/404) for one file in a multi-file PR does not abort processing of the remaining files in the same webhook event
- [ ] A non-skippable fetch error (any other status) for one file: confirm the documented behavior (propagate/fail the webhook step) does not leave `total_check_count` in a partially-incremented state for files already processed earlier in the same loop iteration
- [ ] Concurrent webhook deliveries for the same `(project_id, repository_id, phase_id)` (e.g., two PRs merged near-simultaneously touching the same phase) rely on Postgres's atomic `ON CONFLICT ... DO UPDATE` for correctness — no application-level lock is assumed to be needed; confirm this holds under the actual write pattern used

## 3. FE Review

- [ ] `TemplateUsageByPhase.tsx` is rendered in `PMDashboardPage.tsx` in the exact position required by AC-9: immediately after `<AllTicketsTable>`, before `<TicketDetailDrawer>`
- [ ] The new component is a named export, uses `React.forwardRef` + `displayName` if it is meant to be reusable/composable like other section components (per `10-style.md`); if it is a page-local, non-forwarded section, confirm that choice is intentional and consistent with sibling components in the same directory
- [ ] All Tailwind class composition goes through `cn()`, not raw string concatenation
- [ ] No `any` type is introduced for the new response DTO or query hook; no unchecked `as` cast without an inline `// reason:` comment
- [ ] `templateUsageQuery`'s `enabled` condition exactly mirrors the page's real gating logic (both `filters.projectId` and `filters.repositoryId` set) — matching `summaryQuery`/`ticketsQuery`, not a looser or stricter condition
- [ ] Loading and error states for the new query are handled consistently with sibling sections on the same page (no unhandled promise rejection, no silently-blank section on error)
- [ ] `formatUsageRate` in `pm-dashboard/utils.ts` takes the already-rounded `usageRate` value and only appends `%` (or renders `-` for `null`) — it must not recompute or re-round from raw counts
- [ ] `formatScore` (pre-existing) is untouched by this change

## 4. BE/API Review

- [ ] `PmDashboardController`'s new `@GetMapping("/template-usage")` handler stays thin — no business logic, no manual `ResponseEntity` status codes (per `30-security.md`); delegates directly to `PmDashboardService.getTemplateUsage(...)`
- [ ] `PmDashboardService.getTemplateUsage(...)` calls `requirePm(caller, projectId)` as its first statement, mirroring every other read method on this service (AC-11)
- [ ] `usageRate` rounding logic lives in exactly one place in the BE (the service layer, per `impl-plan.md` §5/§7) — not duplicated in the JDBC adapter or the controller
- [ ] The new per-file validation loop is added inside `GithubWebhookService.handlePullRequest()` per the recorded design decision (ID-1(a)); confirm no promotion of `ticketKeyFromPath`/`isSkippableBlobError` to non-private visibility occurred as a side effect
- [ ] The counter-write call (`TemplateUsageStatPort.recordCheck(...)`) executes strictly after all GitHub HTTP calls for the PR complete, using its own `TransactionTemplate` (Best-Effort Audit/Stat Write Pattern) — a stats-write failure must never roll back or block the primary webhook flow
- [ ] `TemplateUsageStatJdbcAdapter` follows the existing `NamedParameterJdbcTemplate` pattern used elsewhere in `infrastructure/persistence/adapter`; no raw JDBC `Connection`/`Statement` usage
- [ ] Constructor injection only for all new/modified Spring components; no field `@Autowired`
- [ ] Domain/DTO conversion (if any new DTO is introduced for the response) happens in the service layer, not in the controller
- [ ] Skippable-fetch-error handling (401/403/404 → skip + `warn` log + continue) replicates the exact classification used by `isSkippableBlobError`, applied consistently to both the changed-file fetch and the template-file fetch
- [ ] `ArchitectureTest`/ArchUnit layering enforcement — **Undetermined**: no `ArchitectureTest.java` or `@AnalyzeClasses`/`ArchRule` usage was found under `src/test` as of Phase 3 (`impact-analysis.md` §14), despite `CLAUDE.md` describing hexagonal layering as "enforced by ArchUnit." This ticket's new port/adapter placement (`application/port/out/persistence` ← `infrastructure/persistence/adapter`) should be manually verified against `20-architecture.md` since no automated layering check exists to catch a violation

## 5. DB/Migration Review

- [ ] `V510__add_template_usage_tracking.sql` `CHECK` constraints enforce `total_check_count >= 0`, `template_match_count >= 0`, and `template_match_count <= total_check_count` exactly as specified in `spec-pack.md` §12
- [ ] `UNIQUE` constraint `uq_template_usage_scope` covers exactly `(project_id, repository_id, phase_id)` — matches the `ON CONFLICT` target column list used by `TemplateUsageStatJdbcAdapter` verbatim
- [ ] Foreign keys on `project_id`, `repository_id`, `phase_id` reference the correct existing dimension/fact tables with the correct `ON DELETE` semantics (verify against sibling fact tables' FK style, not assumed)
- [ ] `findTemplateUsage(projectId, repositoryId)` (new read method) — confirm the query pattern is efficiently servable by the existing `uq_template_usage_scope` composite index (leading columns `project_id, repository_id` are a valid prefix) rather than requiring a full scan; if not, consider whether a supporting index is needed — this was not explicitly addressed in `impact-analysis.md`/`impl-plan.md` and should be checked against the actual query plan
- [ ] Migration is purely additive (new table + 3 new master-data rows) — no `ALTER`/`DROP` on any existing table or column
- [ ] Migration is idempotent/re-runnable in a clean-DB CI pipeline (standard Flyway versioned-migration guarantee — confirm no manual data assumption breaks a fresh apply)
- [ ] `created_at`/`updated_at` audit columns follow the same convention (naming, default, update-trigger-or-application-set) as other fact tables in the schema, not a new ad-hoc pattern

## 6. Security/Privacy Review

- [ ] `GET /api/v1/pm/dashboard/template-usage` is gated by `requirePm(caller, projectId)` — a caller without the PM project role and without ADMIN system role receives `403` with `errorCode: "Component.Permission.Denied"`, identical to other PM Dashboard endpoints (AC-11)
- [ ] The new endpoint is **not** added to any `permitAll` Spring Security matcher list — it must go through the same session-based authentication as the rest of the PM Dashboard, unlike webhook endpoints
- [ ] The existing webhook HMAC-SHA256 signature validation gate is unaffected by the new per-file validation loop — the new logic executes only after signature verification has already passed in the existing call chain (`GithubWebhookController.receive` → `GithubWebhookService.handle`)
- [ ] No new secret, token, or credential is introduced by this ticket — the GitHub blob/tree fetches for template comparison reuse the existing GitHub App token via `ArtifactScannerSourcePort`, not a new credential
- [ ] New log lines added for the validation loop and for skippable/non-skippable fetch errors do not include raw file content, tokens, or credentials — only identifiers (`projectId`, `repositoryId`, `phaseId`, file path, HTTP status) per `30-security.md`'s log-sanitization rule
- [ ] `project_id`/`repository_id`/`phase_id` exposed in the new API response are UUIDs/codes with no PII; confirm no other field leaking internal-only data (e.g., raw file paths, internal template IDs) was added to the response beyond what `spec-pack.md` §6.3 specifies
- [ ] CORS allow-list is unaffected — the new endpoint is served from the existing `PmDashboardController` base path, no new origin/method needs to be added

## 7. Operation/Maintenance Review

- [ ] Sufficient `warn`-level logging exists for: skippable fetch error (file/path/status), non-skippable fetch error before it propagates, and counter-write failure inside the Best-Effort write path — each log line should carry enough identifiers (`projectId`, `repositoryId`, `phaseId`, PR/commit reference) to investigate an incident without re-running the webhook
- [ ] Existing webhook delivery/correlation identifier (if any is already logged by `GithubWebhookService`) is present on the new log lines too, so a single incident can be traced across old and new log statements
- [ ] **GitHub webhook redelivery / duplicate delivery risk — Undetermined**: GitHub's webhook delivery is at-least-once; no dedupe key (e.g., delivery ID) was found in scope during Phase 3 review of `GithubWebhookService`. If the same `pull_request` event is redelivered, the new validation loop would re-increment `total_check_count`/`template_match_count` for files already counted, double-counting statistics. This was not raised as a resolved Open Issue and is not addressed in `spec-pack.md`/`impact-analysis.md` — it must be explicitly confirmed as either (a) an accepted risk (record in `self-review.md` §9 Exception Record / `open-issues.md` if raised), or (b) already mitigated by an existing dedupe mechanism this review did not find
- [ ] Rollback of the migration (per `impl-plan.md` §8) is documented and does not require a manual data-fix script under normal circumstances; if manual recovery is ever needed (e.g., corrupted counters from the redelivery risk above), confirm a documented recovery procedure exists
- [ ] No new runtime configuration value introduced by this ticket is hardcoded where it should be externalized (e.g., any timeout/retry setting reused from `ArtifactScannerSourcePort` should already be configuration-driven, not newly hardcoded)

## 8. Test Review

- [ ] `GithubWebhookServiceTest` covers all 6 cases identified in `impact-analysis.md` §11: template found+PASS, template found+FAIL, template not found (skip), file maps to unknown dimension (excluded), blob fetch 404 (skip+continue), blob fetch 500 (fail)
- [ ] New structure-comparison logic built on `MarkdownParserCore` has full test coverage from scratch (exact match, level mismatch, order mismatch, missing/extra headers, zero-header document) — `MarkdownParserCore` itself had no prior test coverage per `source-map.md`
- [ ] `TemplateUsageStatPort`/`TemplateUsageStatJdbcAdapter` unit tests mock `NamedParameterJdbcTemplate` — no production DB in unit tests, per `40-testing.md`
- [ ] `PmDashboardService.getTemplateUsage` has a `templateUsage_requiresPmRole()` test mirroring `summary_requiresPmRole()`, plus a happy-path test for the project-role-allow case
- [ ] `PmDashboardController` has a `@WebMvcTest`-slice test for the new route (not a full `@SpringBootTest`)
- [ ] Rounding edge cases are covered explicitly in both BE and FE tests: `2/3 → 66.7` (repeating decimal), `100/100 → 100.0`, `0/5 → 0.0`, `0/0 → null`/`-`
- [ ] FE test confirms `TemplateUsageByPhase` renders after `AllTicketsTable`, before `TicketDetailDrawer`, and confirms the query `enabled` gating behavior (AC-10)
- [ ] Flyway migration `V510` is verified to apply cleanly against a clean dev DB (integration test asserting counters after a fixture webhook, per `spec-pack.md` §15)
- [ ] `ArchitectureTest` coverage for this change — **Undetermined**, see §4 above; no such test exists to add a case to

## 9. Documentation/Traceability Review

- [ ] Every AC (AC-1..AC-11) is traceable to a specific implementation point and a specific verification method — cross-check the actual code against the mapping already recorded in `impl-plan.md` §11; update that table if the real implementation diverges from the plan
- [ ] `spec-pack.md`, `open-issues.md`, `impact-analysis.md`, and `impl-plan.md` remain mutually consistent after implementation — if any class/file name changes during implementation from what was planned, the relevant table rows are updated rather than left stale
- [ ] Code comments (if any) only explain non-obvious WHY (e.g., the private-method duplication rationale for `isSkippableBlobError`, per ID-1(a)) — no comments restating WHAT the code does
- [ ] `self-review.md` is filled in by the implementer before requesting human review (see that file's own checklist)

## 10. Release/Rollback Review

- [ ] Migration `V510` is purely additive; rolling back the BE code deployment without reverting the migration leaves the new table/rows unused but harmless (no existing code path depends on them)
- [ ] Deployment order dependency: the new FE query (`templateUsage.statistics`) will 404 if the FE is deployed before the BE endpoint exists — confirm the actual rollout sequencing (BE-first or coordinated single deploy) and record it, since neither `spec-pack.md` nor `impl-plan.md` explicitly fixes a deployment order — **Undetermined** if not decided elsewhere
- [ ] No feature flag is planned for this ticket (additive UI section, additive endpoint) — confirm this is still an acceptable release strategy at actual release time, not silently assumed
- [ ] Rollback plan for the BE webhook-side change (new validation loop) is a plain code revert — confirm no in-flight webhook processing assumes the new counters exist once written (i.e., reverting BE code does not error out reading a table that no longer has a caller)

## Severity Definition

| severity | meaning | required action |
|---|---|---|
| Blocker | Cannot be released | Must fix |
| Major | High probability of becoming a bug | Fix or accepted risk |
| Minor | Minor improvement | Optional |
| Question | Spec confirmation required | Open Issue |
| False Positive | Incorrect Report | Record Reason for Rejection |
| Accepted Risk | Accepted Risk | Record Impact/Owner/Deadline |
