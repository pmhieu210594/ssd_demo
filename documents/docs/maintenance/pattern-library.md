# Pattern Library

## Purpose

This file collects **reusable implementation knowledge** — designs, mechanisms, and decisions that
worked (or were deliberately chosen for a documented reason) on one ticket and are worth reaching
for again on a future ticket with a similar shape. It is the positive counterpart to
`docs/maintenance/failure-mode-index.md`: that file tracks what goes wrong and how to prevent it;
this file tracks what to *copy* when starting a similar problem, rather than re-deriving it from
scratch.

Each entry names the pattern, the problem it solves, the concrete shape of the solution, and where
it was first used — so a future implementer can jump straight to the real source file as a
reference instead of reimplementing blind.

---

## Parsing

### Positional/structural markdown table extraction (not label-text matching)

**Problem**: A markdown document produced by a template (e.g. `ai-review.md`) needs its data
extracted reliably, but the human-facing labels in that document can be translated, reworded, or
otherwise vary across tickets (English/Vietnamese/Japanese headings, reworded row labels) while the
row/column *order* stays fixed by the template.

**Pattern**: Locate the target section by a stable structural anchor (e.g. the numeric heading
`"8."`, not the translated heading text), then read each row by its fixed position/order in the
table rather than by matching its label text. This makes the parser immune to translation/rewording
drift, at the cost of requiring the template's row order to stay fixed (if the template's row order
changes, the parser must change with it — track this as an explicit assumption, not a silent
dependency).

**First used**: `AiReviewStatsParser` (AI-REVIEW-KPI-IMPROVEMENT, `MarkdownParserCore`, confirmed
design decision `A-AIRKI-1`/Gate #4, 2026-08-19). Same family as the parser referenced in
`FMI-PARSER-008`/`FMI-PARSER-009` (`failure-mode-index.md`) — read those failure modes before
reusing this pattern, they describe what breaks it.

**Watch for**: covering only the "clean" fixture and not a real, previously-shipped instance of the
document (see `FMI-AIRKI-` fixture-drift lesson in this ticket's `test-results.md` — the real fixture
test caught label wording the hand-typed fixture didn't).

---

## Data Modeling

### Shared-denominator KPI group with fail-closed nulling

**Problem**: Several KPIs are conceptually computed over the same total (e.g. "% of findings
adopted", "% valid", "% false positive", "% resolved" all divide by the same total-finding count),
so the schema stores one shared denominator column instead of one per KPI. But "not applicable" for
one ticket's source row must not silently become a false `0` for the whole group when aggregated
across many tickets.

**Pattern**: When persisting/aggregating a value that is intentionally shared across several
sibling output fields, require every contributing row to present that shared value consistently
before trusting it for *any* sibling field; if the requirement isn't met, null the entire sibling
group rather than picking whichever row's value happened to be seen first (`firstNonNull`/naive
`COALESCE` is the anti-pattern here — see `FMI-AIRKI-002`).

**First used**: `resolveSharedFindingTotal` in the AI Finding Stats writer/aggregation path
(AI-REVIEW-KPI-IMPROVEMENT, fixing a Major finding from `codex-review.md` where `firstNonNull`
coerced a "không áp dụng" row into a numeric `0` for sibling KPIs). Confirmed design: `A-AIRKI-3` /
`H-AIRKI-9` in `spec-pack.md`.

---

## Idempotency

### Snapshot-upsert idempotency (natural-key overwrite instead of delivery-id dedup)

**Problem**: A webhook-triggered write needs to be safe against at-least-once redelivery (GitHub
can redeliver the same PR event), but the platform has no delivery-id/idempotency table anywhere to
build a dedup mechanism against (see `FMI-PTR-005`), and building one just for this write would be
a one-off, unscoped platform change.

**Pattern**: When the write represents "the current state of X" rather than "an event that
happened", key the upsert on the domain entity's natural key (e.g. `ticket_id`) with
`ON CONFLICT (ticket_id) DO UPDATE` / overwrite-latest-snapshot semantics, instead of an additive
counter or an event-log row. Redelivery of the same source event then naturally produces the same
final state — idempotent by construction, with no delivery-id tracking needed at all. This only
works when the data is a *snapshot of current truth* (safe to overwrite) — it does not apply to
genuinely additive/cumulative counters, which still need real dedup (`FMI-PTR-003`, `FMI-PTR-005`).

**First used**: `AiFindingStatWriter` upsert into `tbl_fact_ai_finding_stat`, keyed on `ticket_id`
(AI-REVIEW-KPI-IMPROVEMENT). Verified by a dedicated redelivery test
(`ai_finding_stats_secondDeliveryForSameMergedPr_recordsFreshCountsNotSkippedOrStale` and
`ai_finding_stats_exactRedeliveryOfSamePayload_recordsIdenticalCountsBothTimesNotAccumulated` in
`test-results.md`).

### Best-effort side-effect write isolated via its own `REQUIRES_NEW` writer bean + call-site try/catch

**Problem**: A side-effect write (e.g. recording a stat/usage snapshot) must never abort the
primary transaction/operation it's attached to (e.g. webhook PR processing) if the write itself
fails.

**Pattern**: Put the write in its own `@Service` bean method annotated
`@Transactional(propagation = Propagation.REQUIRES_NEW)`, **and** wrap the call site in an explicit
`try/catch` — the propagation annotation only isolates the transaction boundary, it does not by
itself stop an exception from escaping to the caller (see `FMI-PTR-001`, which is exactly this
mistake: propagation without a call-site `try/catch`).

**First used**: `TemplateUsageStatWriter` (prior ticket, `PROMPT_TEMPLATE_REUSE_RATE`), reused as
the direct template for `AiFindingStatWriter` (AI-REVIEW-KPI-IMPROVEMENT).

---

## Frontend Display

### Card-grid vs. row-table, chosen by response cardinality

**Problem**: A new PM Dashboard section needs to render backend data, and there are two existing
display components to potentially reuse (`CDataTable`, `TemplateUsageByPhase.tsx`) or a card-grid
one (`SummaryCards.tsx`) — picking the wrong one produces a table shell around what is really a
single object, or vice versa.

**Pattern**: Let the response shape decide the component family — a **list of rows** (one row per
entity, e.g. per-phase or per-repository breakdown) fits `CDataTable`; a **single object** with
several named scalar/rate fields (no natural "row" concept) fits a card-grid (`SummaryCards`-style:
`{title, description, value}` per card). Do not default to whichever component the most recent
similar ticket used without checking whether that ticket's response was actually a list.

**First used**: `AiFindingStatsCard.tsx` (AI-REVIEW-KPI-IMPROVEMENT) — the ticket's own spec
(`spec-pack.md` §6/§9, original draft) assumed `CDataTable` by analogy with `template-usage`, but the
actual response is a single object per repository; corrected to a `SummaryCards`-style card-grid
after direct comparison of `TemplateUsageByPhase.tsx` (list props) vs `SummaryCards.tsx` (single-object
props) in `OI-AIRKI-13`/`OI-AIRKI-14` (2026-08-19/20). See `FMI-AAL-001` for the related "component
built but never mounted" failure mode — this pattern only covers *which* component to build, not
whether it got composed onto the page.

---

## Notes

- Add an entry here when a design choice from a completed ticket would save real re-derivation time
  on a future ticket with a similar shape — not for anything already obvious from reading the
  source (e.g. "we use MyBatis" is not an entry; a specific non-obvious shape decision is).
- Link the entry to the failure mode(s) it avoids or that motivate it (`FMI-*` IDs), and to the
  first real source file that implements it, so a future reader can go straight to working code.
- Prefer updating an existing entry's "First used" list (add a second usage) over duplicating a
  near-identical entry when the same pattern is reused on a later ticket.
