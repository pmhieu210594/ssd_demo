# Failure Mode Index

## Purpose

Record failure modes that are worth keeping because they help prevent repeat incidents across tickets.
Keep entries concise and prefer reusable prevention/detection guidance over ticket-specific prose.

---

## Organization-Derived Failure Modes

| ID | Failure mode | Trigger | Prevention | Detection |
|---|---|---|---|---|
| FMI-ORG-001 | Migration IT fails on a runner without Docker/Testcontainers | Running `OrganizationMigrationIntegrationTest` where Docker socket access is unavailable | Require a Docker-enabled runner for DB migration IT | Test fails before PostgreSQL container startup |
| FMI-ORG-002 | Raw translation keys appear in the UI | Missing or mistyped `Pages.Organization.*` locale entry | Keep FE translations in `public/locales/{en,ja,vi}/locale.json` and test translated labels | UI shows untranslated key text instead of a localized label |
| FMI-ORG-003 | Stale update/delete overwrites newer organization data | `version` is omitted or not checked atomically in update/delete SQL | Require `version` on mutating requests and check affected row count | Concurrent edit/delete unexpectedly succeeds |
| FMI-ORG-004 | Soft-deleted organization code/name cannot be reused | Partial unique index is defined without `deleted_at IS NULL` | Scope uniqueness to active rows only | Create/update fails for a code/name that exists only in Deleted |
| FMI-ORG-005 | Non-admin users can reach Organization UI but get poor feedback | FE route guard is missing or backend returns 200 + error string instead of a real forbidden response | Keep FE admin gate and backend forbidden handling aligned | Non-admin sees the page but a mutation appears to succeed or fails silently |

---

## Customer-Derived Failure Modes

| ID | Failure mode | Trigger | Prevention | Detection |
|---|---|---|---|---|
| FMI-CUS-001 | Playwright locator strict-mode collision on duplicated labels | The same visible label appears in the list row, the open drawer, and the option popup | Scope option clicks to the active popup/container or use a stable `data-testid` | Playwright reports a strict-mode violation when clicking the option |
| FMI-CUS-002 | Soft-deleted master data cannot be reused | Partial unique index is defined without `deleted_at IS NULL` for code/alias reuse | Keep uniqueness scoped to active rows only | Reusing a value from a soft-deleted row fails unexpectedly |
| FMI-CUS-003 | Stale update/delete overwrites newer master data | `version` is omitted from the mutating `WHERE` clause | Require `version` and verify affected row count | Concurrent edit/delete unexpectedly succeeds |
| FMI-CUS-004 | Raw translation keys appear in the UI | Missing or mismatched locale key for a Customer/Organization message | Keep FE translation keys aligned with the backend message contract | UI shows untranslated `Pages.*` keys or fallback text |

---

## Project-Derived Failure Modes

| ID | Failure mode | Trigger | Prevention | Detection |
|---|---|---|---|---|
| FMI-PROJ-001 | Authorization is treated as fully proven even though only coarse denial behavior was tested | Ticket intent depends on role-derived action semantics, but the evidence only proves a coarse runtime gate | Require an explicit auth-semantic review note whenever product intent references role tables or role-derived action policy | Final report or test-results show auth as `PARTIAL` with pending human review |
| FMI-PROJ-002 | Exception tickets drift back toward neighboring module defaults | The feature intentionally deviates from nearby contracts, but implementation or review copies the default pattern back in | Repeat approved deviations in spec, tests, review checklist, and final report | Hidden `version`, wrong delete verb, or borrowed default contract shape appears in evidence |
| FMI-PROJ-003 | Final closure implies PR or CI proof that is not locally available | Local-only evidence is summarized as if external PR comment or hosted CI evidence exists | Require closure docs to state "not found locally" when external review or CI artifacts are absent | Final report claims review/CI confidence without a local artifact or limitation note |
| FMI-PROJ-004 | Read-only dashboard silently becomes writable or persistent | A dashboard implementation adds POST/PUT endpoints or a snapshot table instead of staying read-only | Keep dashboard endpoints GET-only and aggregate from existing tables through the service/repository layer | API review or migration review catches a write surface or new table |

---

## Repository-Derived Failure Modes

| ID           | Failure mode                                                | Trigger                                                                             | Prevention                                                                                      | Detection                                                              |
| ------------ | ----------------------------------------------------------- | ----------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| FMI-REPO-001 | Soft delete behavior inconsistent between list and detail   | List query or UI does not exclude deleted rows OR detail API blocks deleted records | Enforce rule: list = active-only, detail = allow deleted; align FE/BE behavior                  | E2E: delete → verify not in list but accessible in detail              |
| FMI-REPO-002 | Duplicate validation scope mismatch (per project vs global) | Uniqueness enforced globally or inconsistently between DB and BE                    | Define uniqueness scope explicitly (per project, active-only); align DB index and BE validation | Integration: duplicate same project → 409; different project → success |
| FMI-REPO-003 | Missing trim before validation causes duplicate bypass      | Input is not trimmed before validation or duplicate check                           | Always trim before validation, duplicate check, and persistence                                 | Unit + integration: `"  repo-A  "` conflicts with `"repo-A"`           |
| FMI-REPO-004 | FE-only validation allows invalid or unauthorized requests  | Backend does not enforce validation or permission                                   | Backend must validate all inputs and enforce authorization                                      | Integration: invalid payload → 400; unauthorized → 403                 |

---

## PM-Dashboard-Derived Failure Modes

| ID | Failure mode | Trigger | Prevention | Detection |
|---|---|---|---|---|
| FMI-PM-001 | Numeric URL param clamp silently ignores `0` — `Number(x) \|\| default` treats `0` as absent | Any numeric URL param where `0` is a valid value and code uses the `\|\| default` idiom | Use explicit `raw === null` guard before coercion: `if (raw === null) return default; const n = Number(raw); return isNaN(n) ? default : clamp(n)` | Unit test with `?param=0` input; caught in `parseFilters` size clamp (PM-DASHBOARD test phase) |
| FMI-PM-002 | Stale raw DB design doc drives a migration that duplicates or conflicts with existing V4 schema | Developer reads `raw/database_design.md` and creates a migration without cross-checking V4 | Spec-pack must document an explicit proposed-table → V4-table mapping; pre-migration checklist: "verified against V4 authoritative schema?" | Code review sees a new migration creating a table that already exists in V4 |
| FMI-PM-003 | Auth doc inconsistency — `architecture.md` JWT wording misleads a new developer into adding Bearer token headers | New developer reads `overview.md` and adds `Authorization: Bearer` to a new endpoint | Keep a cross-reference note in `overview.md` pointing to `security.md` as the canonical auth source; update stale wording when confirmed | Code review: new endpoint uses Bearer token header instead of the session cookie confirmed in `security.md` |
| FMI-PM-004 | Owner display PII leak — snapshot query falls back to raw `updated_by`/`created_by` instead of joining the pseudonym table | New snapshot or report query uses `updated_by` as the display value because the pseudonym join is omitted or falls through | Never use `updated_by`/`created_by` as a display value; always join `tbl_dim_member_pseudonym.pseudonym`; add a unit test asserting `ownerDisplay` does not match an email pattern | Unit test asserting pseudonym-only value; code review of any new snapshot query that touches owner/author columns |
| FMI-PM-005 | Impact-analysis/context lists the application-layer model (`DashboardTicketRow`) but misses the separate DTO/mapping class that actually serializes the HTTP response (`PmDashboardDtos`) | Codebase has one internal model separate from one response DTO — reading/listing only the model is not enough to identify every file that needs a field added for an additive response-API change | When extending a field on a response API, trace from the `@RestController` method down to the actual JSON-serializing type (grep the response type) before finalizing the affected-files list in Phase 2/3 | Compile error or test failure if the field is missing on the DTO but present on the model — only caught if a test asserts the actual response JSON, not just Service-layer mocks |

## THRESHOLD-CONFIG-Derived Failure Modes

| ID | Failure mode | Trigger | Prevention | Detection |
|---|---|---|---|---|
| FMI-TC-001 | Migration slot planned in an earlier phase collides with one merged later | A planning doc (`impl-plan.md`) names a specific `V<N>` migration number without re-checking the migration directory at implementation time | Always re-list the migration directory immediately before creating a new migration file, even if a plan already named a slot | New migration filename collides with an already-merged one; caught only by `ls`/build failure |
| FMI-TC-002 | Hand-rolled library mock silently desyncs from a shared component's growing import surface | A test file's `vi.mock("antd", ...)` (or similar) is authored before a shared component (`ThresholdTable.tsx`) later starts importing a new export (`Tooltip`) the mock never provided — same root cause seen again in BUG-DASHBOARD as (a) a whole-module `vi.mock("antd", ...)` missing the `Input` export a different component needed, and (b) a hoisted multi-endpoint API mock object missing one endpoint key, which resolves to `undefined` at call time | Prefer real component render or a mock factory kept next to the shared component, not duplicated per test file; when adding a new import to a widely-shared UI primitive or a new endpoint to a shared API client mock, grep for existing hand-rolled mocks of that module | Test passes in isolation at authorship time but fails (or worse, silently no-ops) once the shared surface changes — failure surfaces late, not at the point of the real breaking change; a dependent-query chain reporting green with zero assertions on the gating data is a red flag — verify the mock's returned shape before trusting the green result |

---

## Parser Failure Modes

| ID | Failure Mode | Trigger | Prevention | Detection |
|----|-------------|--------|------------|-----------|
| FMI-PARSER-001 | Missing required section leads to partial parsing | Markdown thiếu section bắt buộc | Define required section list + optional strict mode validation | Parser trả PARTIAL/WARNING + missing fields |
| FMI-PARSER-002 | Malformed header prevents section detection | Header sai format (`##`, typo, level sai) | Header normalization + fallback matching | Section không detect → missing field |
| FMI-PARSER-003 | Silent data loss due to malformed structure | Markdown không đúng format nhưng parser vẫn chạy | Add validation layer + warning/error state | Output thiếu dữ liệu nhưng không fail rõ ràng |
| FMI-PARSER-004 | Inconsistent section naming breaks mapping | Naming khác nhau giữa document | Central naming registry + alias mapping | Mapping fail hoặc field missing |
| FMI-PARSER-005 | Partial document ingested as valid | Document chưa hoàn chỉnh | Completeness score + draft detection | Output PARTIAL nhưng vẫn downstream |
| FMI-PARSER-006 | Spec and test drift undetected | Spec define nhưng test không cover | Cross-document validator | Không có signal cảnh báo |
| FMI-PARSER-007 | Implementation diverges from spec | Impl không match spec | Spec–Impl traceability check | Sai behavior nhưng không detect |
| FMI-PARSER-008 | Lowercase key lookup silently misses all sections | Parser gọi `sections.get(field)` với lowercase key nhưng `MarkdownParserCore.sectionMap()` trả về UPPERCASE canonical keys (e.g. `"SUMMARY"`, `"OPEN_ISSUES"`) | Dùng `sections.get(field.toUpperCase())`; verify key format bằng diagnostic test trước khi implement section lookup | `detectMissingFields()` báo all-missing; `parsedSummary` section fields đều null; `parseStatus` luôn `PARTIAL` với mọi non-empty content |
| FMI-PARSER-009 | Fenced-code-block heading-like lines counted as real sections | `extractSections()` scans every line with `HEADING_PATTERN` with no state tracking for whether the current line is inside a ` ``` ` fenced code block, so an example heading shown inside a code fence (common in doc/template files) is parsed as a real section | Track fenced-code-block state (toggle on ` ``` ` delimiter lines) and skip heading-regex matching while inside a fence | A document containing an example section heading inside a code fence reports more sections than it actually has; any structural comparison built on section count/level (e.g. template-match checks) produces a silent false-negative |

---

## QA-Dashboard-Derived Failure Modes

| ID | Failure mode | Trigger | Prevention | Detection |
|---|---|---|---|---|
| FMI-QA-DASH-01 | New KPI always returns 0% — parser-dependent table `section_type` value not confirmed from parser source before adapter SQL was written | Writing SQL that queries `tbl_fact_artifact_parsed_section` (or any parser-dependent fact table) for a new artifact type without first reading the parser source; parser not deployed or constant in adapter does not match what parser writes | impl-plan Step 1 Stop Condition: read parser source → record confirmed `section_type` as a named constant → write SQL. Do not write SQL before confirmation. (Rule R-QA-DASH-01) | KPI card stuck at 0% regardless of test data; `SELECT COUNT(*) FROM tbl_fact_artifact_parsed_section WHERE section_type = 'xxx'` returns 0 rows |
| FMI-QA-DASH-02 | PoC `= 0` placeholder persists to production — Deferred formula has no deadline, no escalation path | Formula Deferred in spec-pack without a deadline; team proceeds through Phase 3–5 without Product decision; placeholder value ships | Set a deadline alongside every Deferred label in spec-pack; treat no-deadline Deferred as a Phase 3 go/no-go blocker. (Rule R-QA-DASH-02) | Numeric KPI field always returns `0` across all environments; unit test only verifies placeholder returns `0`; user incident report that a card "never changes" |

---

## Developer-Dashboard-Derived Failure Modes

| ID | Failure mode | Trigger | Prevention | Detection |
|---|---|---|---|---|
| FMI-DEV-DASH-001 | FE E2E fixture uses a role value the route guard doesn't recognize | Test fixture set `role: "DEVELOPER"` while `RequireDashboard` (`App.tsx:83`) only accepts `PM \| QA \| DEV \| ADMIN` | Before writing a fixture/mock user, confirm the exact role enum values accepted by the FE route guard; reuse `"DEV"` for developer-role fixtures | E2E suite fails at the first step — app redirects to `/login` instead of rendering the dashboard |
| FMI-DEV-DASH-002 | Final verdict reads as an unscoped PASS while a later test round is still pending | Test plan splits FE round and BE round across phases; the FE-only round finishes green and the verdict line doesn't state its scope | State the round scope explicitly in the Final Verdict line (e.g. "PASS — FE round only"); don't let an unscoped PASS stand until the deferred round (e.g. BE Service/Repository unit test) also completes | test-results.md lists BE aggregation as an unverified remaining risk (§9) while §10 states a plain "PASS" |

Note: the strict-mode Playwright violation hit in this ticket (duplicated "Review comments" label) is a recurrence of `FMI-CUS-001`, not a new failure mode — no new row added for it.

---

## Admin-Audit-Log-Derived Failure Modes

| ID | Failure mode | Trigger | Prevention | Detection |
|---|---|---|---|---|
| FMI-AAL-001 | UI component implemented and unit-tested but never mounted on the page it was built for | A new component (e.g. summary cards) is added and tested in isolation, but the page-composition file is not updated in the same change | Require a page-level render/composition check for any AC that specifies a visible UI element; see `docs/standards/testing.md` "AC Closure / Release Gating" | Human/browser review of the actual composed screen catches the missing element (this ticket's `human-review.md` finding M1) |
| FMI-AAL-002 | Error-message sanitizer only truncates, does not redact secret-pattern substrings, before writing to an audit/log payload | A caught exception's message is capped by length only and passed into a log/audit field; the raw message may embed `password`/`token`/`secret`/`apiKey`-style substrings | Redact known secret-pattern substrings before truncating, not truncation alone; see `docs/standards/security.md` masking section | Unit test asserting sanitized output for a message containing a secret-like substring |
| FMI-AAL-003 | Leftover artifact from a prior ticket silently treated as current-ticket review/promotion evidence | A ticket's doc folder carries a file (e.g. `promotion-candidates.md`) whose content/header still belongs to a different ticket, and a later phase reads it without checking | Check each artifact's own Ticket ID header against the current ticket before treating it as input; see `docs/standards/maintenance.md` | A downstream phase (or reviewer) notices content referencing a different ticket ID or unrelated feature |

Note: the Phase 6/7 gap in this ticket where automated-suite `PASS` and black-box-case completion could have been read as the same gate is a recurrence of `FMI-DEV-DASH-002`, not a new failure mode — this ticket's `test-results.md` correctly kept the two scopes separate, so no new row was added for it.

---

## PROMPT_TEMPLATE_REUSE_RATE-Derived Failure Modes

| ID | Failure mode | Trigger | Prevention | Detection |
|---|---|---|---|---|
| FMI-PTR-001 | Best-effort side-effect write aborts the primary operation because the call site has no try/catch | A writer method uses `@Transactional(REQUIRES_NEW)`/`NESTED` (isolates the transaction boundary) but the caller invokes it without wrapping the call in `try/catch`; propagation alone does not swallow the exception | Always pair the propagation annotation with an explicit `try/catch` at the *call site*, not only inside the writer bean; see `docs/architecture/service-layer-map.md` §7 | Injecting a `DataAccessException` mid-loop over multiple items shows the whole operation abort instead of just that one item being skipped |
| FMI-PTR-002 | Blanket `catch (Exception ex)` around a shared/PR-level external call masks non-skippable errors (5xx, timeout) as a silent skip | A new call site touching the same external resource (e.g. GitHub tree/revision fetch) is added near existing status-aware skip logic (e.g. per-file blob fetch) but reuses a broad catch instead of the existing classification | Reuse the existing skippable-error classification (e.g. `isSkippableFetchError`/`isSkippableBlobError`, 401/403/404 → skip, others → propagate) for any new call against the same resource rather than writing an ad hoc broad catch | Test with a 401/403/404 (should skip silently) vs a 500/non-HTTP error (should propagate and fail the request) on the new call site |
| FMI-PTR-003 | Cumulative counter recording runs on every lifecycle webhook delivery instead of once per logical event, inflating counts | The recording call is not gated on the specific terminal condition that defines "once" (e.g. `action == "closed" && merged == true`); each `opened`/`synchronize`/`reopened`/`closed` delivery independently re-scans the same cumulative diff through a purely additive counter | Gate any additive side-effect recording explicitly on the terminal condition that defines "once", not on "this handler executed" | Invoking the webhook multiple times with `opened`/`synchronize`/`reopened` before the terminal `closed`+`merged` event shows the counter incremented more than once |
| FMI-PTR-004 | A query scoped through an indirect/shared proxy table leaks unrelated data because that table is also used by another feature | A filter is derived indirectly (e.g. `EXISTS`/`JOIN` against a shared dimension table) instead of from the ticket's own explicitly-defined scope set, and the shared table happens to carry rows for both features | Ground scope filters in a literal, ticket-owned definition (e.g. an explicit `IN (...)` list) when a shared dimension/proxy table cannot guarantee exclusivity to this feature | Response includes an out-of-scope value traced back to unrelated-feature rows in the shared proxy table |
| FMI-PTR-005 | Additive webhook-triggered counter has no redelivery dedup, and the platform itself has no delivery-id/idempotency table anywhere to build one against | A new additive counter/audit write is triggered from a webhook handler; the webhook transport is at-least-once (GitHub can redeliver the same event) but `deliveryId` is only logged, never persisted, across the whole codebase | Before assuming redelivery-dedup is this ticket's problem to solve, search the codebase for any existing delivery/idempotency table; if none exists, escalate as a platform-level decision to Product/Tech Lead rather than building a one-off fix | Repo-wide search for `webhook_event`/`webhook_delivery` tables or a persisted `deliveryId` column returns nothing, while an additive counter write exists on a webhook path |

Note: this ticket's (AI-REVIEW-KPI-IMPROVEMENT) webhook-triggered write avoided the `FMI-PTR-005`
problem entirely by design rather than recurring into it — it upserts a snapshot keyed by
`ticket_id` (`ON CONFLICT (ticket_id) DO UPDATE`, overwrite-latest-state semantics) instead of an
additive counter, so redelivery of the same event is naturally idempotent without needing a
delivery-id table. See `pattern-library.md` "Snapshot-upsert idempotency" for the reusable version
of this design.

---

## AI-REVIEW-KPI-IMPROVEMENT-Derived Failure Modes

| ID | Failure mode | Trigger | Prevention | Detection |
|---|---|---|---|---|
| FMI-AIRKI-001 | Tenant/project scope filter is placed only in a `LEFT JOIN`'s `ON` clause (scoping the joined table) but is missing from `WHERE` for the anchor/dimension row itself, so the anchor row's own directly-owned scope column is never checked | A query joins a fact table (already correctly scoped in `ON`) to a dimension table for display fields (e.g. repository name); the dimension row is looked up by its own primary key/param without also filtering its own `project_id`/tenant column in `WHERE` | Any dimension/anchor row returned to the client must be scoped in `WHERE` using its own directly-owned tenant column, even when a joined fact table is already scoped in `ON` — the two are independent checks, not substitutes for each other | Requesting a resource ID that belongs to a different tenant/project returns that resource's real identifying fields (e.g. name) instead of a 403/404/empty result |
| FMI-AIRKI-002 | A value shared by multiple independently-computed rate/KPI fields is derived with "first non-null wins" (`firstNonNull`/`COALESCE` across rows) instead of requiring the whole group of sources to agree, silently coercing "not applicable" into a numeric `0`/wrong value for the other fields in the group | Several sibling KPIs share one denominator column by design; the aggregation reads whichever row happens to report that denominator first, then reuses it for every sibling KPI even when other sibling rows reported "not applicable" (null) for that same conceptual denominator | When several fields share one designed-common source value, require all contributing rows to present that value consistently (and be equal) before trusting it for any of them; if any required row is absent, the whole shared-value group must resolve to null, not a coerced default | Constructing one row where the shared field is "not applicable" (null) while sibling rows are numeric shows the group's rate as `0.0`/wrong value instead of `null` |
| FMI-AIRKI-003 | A standards/architecture doc marks a mechanism as "Confirmed" or "must stay green" (e.g. a named ArchUnit test class) based on a dependency being added and a code comment referencing it, without a corresponding test file ever having been committed — the claim silently drifts from reality across every doc that copies it | A build dependency (e.g. `archunit-junit5`) is added and `package-info.java`-style comments reference an enforcement test by name, creating the appearance of an enforced safety net; the actual test class is never created, but the doc claim propagates unchanged into every standards file that describes the architecture | Before asserting a mechanism is "Confirmed"/enforced in a standards doc, verify the actual enforcing artifact (test class, config file) exists via a direct search, not via a dependency or comment reference alone; re-verify periodically since docs do not re-check themselves | `Glob`/`Grep` for the named test class (by every name variant referenced in comments/docs) across the whole source tree returns nothing, while multiple docs still assert it is "Confirmed"/"must stay green" |
| FMI-AIRKI-004 | An early review-phase artifact's "known unresolved issues" list is treated as still-current after a later phase (e.g. a dedicated bug-hunt/test-hardening pass) has already closed those exact items, creating an apparent contradiction for whoever reads the report next | A ticket produces multiple sequential review/test artifacts (e.g. `self-review.md` then `test-results.md`); the earlier artifact's unresolved-items section is never updated once the later artifact closes those items, so a downstream reader sees two documents disagreeing about whether an item is done | When a later-phase artifact closes an item an earlier artifact listed as unresolved, update the earlier artifact's own unresolved-items section (or add an explicit forward pointer) in the same change, rather than leaving the contradiction for the next reader to reconcile by hand | A reader compares test-method names and total test counts between two same-ticket artifacts and finds the "unresolved" item is actually covered by a specific test added later |

Note: `FMI-AIRKI-001` is a related but distinct failure mode from `FMI-PTR-004` — `FMI-PTR-004` is
about deriving a scope filter *indirectly* through a shared proxy/dimension table instead of an
explicit owned scope set; `FMI-AIRKI-001` is about a scope filter that *is* correctly and directly
known, but is only applied to one side of a `LEFT JOIN` (the joined table) and omitted from the
anchor row's own `WHERE` clause. Both belong to the same broader family (cross-tenant leak via a
joined/shared table) and are worth reading together, but neither subsumes the other.

---
## AI-Quality-Derived Failure Modes

| ID | Failure mode | Trigger | Prevention | Detection |
|---|---|---|---|---|
| FMI-AIQ-001 | Diverging from a mirrored reference implementation's exact fallback/edge-case branch to apply a "stricter" spec reading, without tracing why the reference's branch exists | Implementing a "mirror pattern X" ticket and spotting an apparent inconsistency or under-implementation in the reference (e.g. a javadoc claiming a check the code doesn't perform) | Before diverging from a mirrored reference's control flow, trace *why* the branch exists — check for a caller (route guard, first-render effect) that depends on the exact fallback behavior — rather than assuming it was an oversight | Regression surfaces as a permission/auth failure (403 / force-logout) for a caller state the new stricter code no longer tolerates, typically via a navigation path that doesn't carry a parameter the new code newly requires |

---

## Notes

- Add a new row only when the failure mode has a real chance to recur.
- Avoid turning one-off process issues into permanent general rules.
- Prefer a short link to a living doc when the prevention pattern is already documented elsewhere.
