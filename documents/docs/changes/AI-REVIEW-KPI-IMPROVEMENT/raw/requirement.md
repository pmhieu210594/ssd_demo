# Feature: Extract AI Review KPI Statistics by Repository

## Objective

`ai-review.md` (produced per ticket under `docs/changes/{ticket-id}/ai-review.md`) already contains
a statistics table in section `## 8. Số liệu thống kê` with the KPI values the business needs.
Today nothing reads this table automatically — numbers only exist inside the Markdown document.

The following functionality should be added:

- Automatically parse the KPI statistics table from `ai-review.md` when processing GitHub Pull Requests.
- Do so **independently of the document's language** (input is Vietnamese today, may become English or
  Japanese later — column headers and row labels are translated text, not stable keys).
- Store the parsed values in the database, scoped by ticket / repository / project.
- Display aggregated KPI statistics by repository on the PM Dashboard.
- Guarantee no double-counting when the same ticket's `ai-review.md` is merged more than once
  (re-review rounds, status updates, edits after human confirmation).

## Target metrics

| Metric (EN)                        | Tiêu đề cột "Số liệu" (VI, hiện tại)              |
| ----------------------------------- | -------------------------------------------------- |
| Blocker/Major Resolution Rate       | Tỷ lệ xử lý finding nghiêm trọng (Blocker)          |
| AI Review Adoption Rate             | Tỷ lệ AI finding được con người chấp nhận           |
| AI Review Valid Finding Rate        | Tỷ lệ AI review finding hữu ích                     |
| AI False Positive Rate              | Tỷ lệ AI finding bị đánh giá là false positive      |
| AI Finding Resolution Rate          | Tỷ lệ AI finding đã được xử lý (fix hoặc quyết định chính thức) |

## 1. Processing in `GithubWebhookService.handlePullRequest`

### Trigger

Same gate already used for template-usage validation: `pull_request` event, action `closed`,
`pull_request.merged == true` (see existing `validateTemplateUsage(...)` call site).

### Scope

Only process a changed file when:

```text
docs/changes/{ticket-id}/ai-review.md
```

is present in the PR's changed file list. Resolve `{ticket-id}` the same way the webhook already does
for other artifacts (existing `ticketKeyFromPath(path)` helper), then resolve the ticket's UUID via the
already-built ticket scope map for this PR.

If the file is not in the diff, skip — do nothing for that ticket on this delivery.

## 2. Locating the statistics table without depending on language

Column headers ("Số liệu" / "Metric", "Giá trị" / "Value") and row labels are translated text and
**must not** be used as the lookup key — a future English/Japanese `ai-review.md` would break any
parser keyed on Vietnamese strings.

Instead, rely on structural/positional anchors guaranteed by the ticket template
(`docs/standards/templates/_ticket-template/ai-review.md`, section `## 8. Số liệu thống kê`):

1. Locate the target section by its **numeric heading prefix** `## 8.` (heading numbers are not
   translated), not by matching the heading title text.
2. Within that section, extract the single Markdown table using purely structural parsing
   (pipe-delimited rows + separator row) — no text matching involved.
3. Read values by **fixed row position** (0-indexed, per the template's row order):

   ```text
   row 0: Tổng số finding                              (not needed)
   row 1: Tổng số finding đã fix                        (not needed)
   row 2: Blocker/Major Resolution Rate
   row 3: AI Review Adoption Rate
   row 4: AI Review Valid Finding Rate
   row 5: AI False Positive Rate
   row 6: AI Finding Resolution Rate
   ```

   Column index 1 ("Giá trị hiện tại" / "Value") holds the value for each row.

4. Extract the numeric value with a structural regex over the value cell (e.g. `n / m (x%)`,
   `x%`), not by matching placeholder text such as "không áp dụng" / "chưa có dữ liệu" / "N/A".
   If no numeric pattern is found, treat the metric as not-applicable (`null` numerator/denominator).

### Known limitation (must be documented, not silently assumed)

This approach depends on the template's row order/count staying stable across languages and future
revisions. If the table is ever hand-edited out of order, or a translated template reorders rows,
positional mapping can silently attribute the wrong value to the wrong metric. A more robust
long-term option — out of scope for this ticket — is to add a machine-readable key per row in the
template (e.g. an HTML comment `<!-- metric: ai_review_adoption_rate -->`), which would remove the
positional dependency entirely.

## 3. Data model

### Do not reuse `tbl_fact_template_usage_stat`'s increment pattern

`tbl_fact_template_usage_stat` accumulates `total_check_count += 1` per webhook delivery — correct
there because each merged PR is an independent structural check event. The `§8` statistics table is
different: it is a **running snapshot of the ticket's current state** (the file itself states the
numbers already include all prior review rounds). Incrementing on every delivery would double-count.

### New table

Propose a new fact table, one row per ticket:

- Natural key: `ticket_id` (`UNIQUE`).
- Carries `project_id` and `repository_id` directly (denormalized, same approach as
  `tbl_fact_template_usage_stat`) so repository-level aggregation does not require a join.
- Store **numerator/denominator pairs** per metric, not a pre-computed percentage — needed so that
  repository-level aggregation can compute `SUM(numerator) / SUM(denominator)` correctly instead of
  averaging percentages across tickets with different sample sizes:

  - `blocker_major_resolved_count` / `blocker_major_total_count`
  - `ai_review_adopted_count` / `ai_review_finding_total_count`
  - `ai_review_valid_count` / `ai_review_finding_total_count`
  - `ai_review_false_positive_count` / `ai_review_finding_total_count`
  - `ai_review_resolved_count` / `ai_review_finding_total_count`

  Each numerator/denominator pair is nullable independently — a metric marked "not applicable" /
  "no data yet" in the document must be stored as `NULL`, not `0`, so it is excluded from aggregation
  rather than counted as 0%.

- `created_at` / `updated_at` timestamps, following existing convention.

## 4. Write logic — dedup / idempotency (must not double count)

### Rule

Write via **UPSERT keyed by `ticket_id`** (`ON CONFLICT (ticket_id) DO UPDATE SET ... = EXCLUDED...`),
replacing the row's values with the freshly parsed snapshot — never incrementing counters.

### Why this prevents duplicates

- Same ticket merges a second PR that further edits `ai-review.md` (e.g. status changes from
  "Chưa xử lý" to "Đã xử lý" after human confirmation, as seen in the sample input) → the new parse
  overwrites the old row. No second row, no inflated totals.
- GitHub redelivers the same webhook (network retry, no dedup by `delivery_id` exists anywhere else
  in `GithubWebhookService` today either) → upsert is naturally idempotent, so no separate
  `delivery_id` tracking table is required for correctness.

### Failure isolation

Follow the same best-effort pattern already used for template-usage validation:

- A dedicated writer component with its own `@Transactional(REQUIRES_NEW)` boundary (mirrors
  `TemplateUsageStatWriter`), so a write failure here never rolls back the primary webhook/PR
  ingestion flow.
- Wrap the write in try/catch at the call site; log and continue on `DataAccessException`.
- Reuse the existing skippable-fetch-error classification (401/403/404 → skip + warn, anything else
  rethrows) for the blob fetch of `ai-review.md` itself. Only one blob fetch is needed here (the file
  content) — no template blob fetch/comparison is required, unlike `validateTemplateUsage`.

## 5. API for Frontend

Example, following the existing PM Dashboard endpoint convention
(`GET /api/v1/pm/dashboard/template-usage`):

```http
GET /api/v1/pm/dashboard/ai-review-stats?projectId={projectId}&repositoryId={repositoryId}
```

Response — aggregated by repository, rate computed from summed numerator/denominator across tickets
in scope:

```json
[
  {
    "repositoryId": "…",
    "repositoryName": "…",
    "blockerMajorResolutionRate": 87.5,
    "aiReviewAdoptionRate": 100,
    "aiReviewValidFindingRate": 100,
    "aiFalsePositiveRate": 0,
    "aiFindingResolutionRate": 100
  }
]
```

If a metric's summed denominator is 0 across all tickets in scope, return `null` for that rate
(same convention as `TemplateUsageDto.usageRate`).

## 6. Display on PM Dashboard

Screen:

```text
EDCAP_FE/src/pages/pm-dashboard/PMDashboardPage.tsx
```

Add a new section following the existing `TemplateUsageByPhase` pattern (same page, rendered
alongside it):

- New component `AiReviewStatsByRepository.tsx` (or similar name), built on the shared `CDataTable`
  component — do not introduce a different table component.
- New `useQuery` with its own query key (e.g. `["pm-dashboard", "ai-review-stats", projectId,
  repositoryId]`), `enabled` gated on `projectId`/`repositoryId` same as the template-usage query.
- New `endpoints.pmDashboard.aiReviewStats(...)` entry, nested inside the existing `pmDashboard`
  namespace in `lib/api.ts` (matching the convention already established for `templateUsage` —
  see `docs/changes/PROMPT_TEMPLATE_REUSE_RATE/spec-pack.md` §11 decision log for why nested-in-domain
  won over a sibling top-level namespace).

### Displayed columns

- Repository Name
- Blocker/Major Resolution Rate
- AI Review Adoption Rate
- AI Review Valid Finding Rate
- AI False Positive Rate
- AI Finding Resolution Rate

Show `-` when a rate is `null` (no data / not applicable), same convention as template usage rate.

## Acceptance Criteria

### Backend

- When a merged PR's diff includes `docs/changes/{ticket-id}/ai-review.md`, parse the `## 8.`
  statistics table for that ticket.
- Section lookup must work regardless of the document's language (locate by heading number, not by
  title text or column header text).
- Row-to-metric mapping must work regardless of the document's language (locate by fixed row
  position per the ticket template, not by row label text).
- Value extraction must work regardless of the document's language (extract via numeric/percentage
  pattern, not by matching placeholder text).
- Store one row per ticket (upsert by `ticket_id`), never an additional row or an incremented counter
  for repeat merges of the same ticket.
- A parse or write failure for this feature must never fail or roll back the rest of
  `handlePullRequest`.
- Re-processing the same ticket's `ai-review.md` (repeat merge, or duplicate webhook delivery) must
  leave the stored row identical to a single processing pass — no double counting.

### Frontend

- New section on `PMDashboardPage.tsx` showing the 5 KPI rates per repository.
- Data retrieved from the new backend API, gated on `projectId`/`repositoryId` filters already present
  on the page.
- Displays `-` instead of a percentage when the underlying denominator is 0/no data.
