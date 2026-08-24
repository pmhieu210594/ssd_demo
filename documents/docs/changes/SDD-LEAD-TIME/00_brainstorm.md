# Brainstorm — SDD-LEAD-TIME

## 1. What the ticket is actually asking for

Despite the ticket name "SDD-LEAD-TIME" (which suggests a DORA-style lead-time duration metric), the raw input is much narrower: surface two **existing artifact timestamps** on the `pm-dashboard` Ticket Detail drawer —

- `spec-pack.md` → its own "created" timestamp → persist into `tbl_dim_ticket.started_at` (already exists)
- `report.md` → its own "updated" timestamp → persist into `tbl_dim_ticket.completed_at` (new column)

No duration/interval computation (e.g. `completed_at - started_at`) is requested by the raw input. This is flagged as an Open Issue (naming vs. scope mismatch) rather than assumed as future scope.

## 2. Candidate sources for the two timestamps

| candidate | description | verdict |
|---|---|---|
| Filesystem `stat()` (mtime/ctime) | Read OS-level file timestamps | **Ruled out.** `ArtifactScannerService` never reads local disk — files are fetched via `GithubArtifactScannerSourceAdapter` from the GitHub Trees/Blobs API. There is no local filesystem in the deployed scan path. |
| GitHub per-file commit history (`GET /repos/{owner}/{repo}/commits?path=<file>`) | First commit touching the path = created; last commit = updated | Technically most "correct" for a Git-backed created/updated notion, but requires a **new** adapter capability (none exists today — the only existing helper, `resolveCommitDate`, resolves a commit date for a whole revision SHA, not a per-path history) and an extra API call per file per scan. **Ruled out per user decision** — user explicitly chose to avoid a separate API call. |
| Artifact Scanner's own first/most-recent scan-run timestamp | Use `tbl_artifact_snapshot`-style scan metadata (when the scanner first saw the file / last saw a content-hash change) | Would not require new API calls, but conflates "when we happened to scan it" with "when the file was actually created/updated" — inaccurate if scans are infrequent or backfilled. Not selected. |
| **Timestamp embedded in the file's own content** (header metadata line, e.g. `- **Tạo ngày:** {DATE}`) | Parse the value already written into the markdown file itself during the existing Artifact Scanner content-parse pass | **Selected per user decision.** No new external API call; reuses `MarkdownParserCore.extractHeaderMetadata()`, which already turns pre-`##` lines like `- **Tạo ngày:** ...` into a normalized-key map. Confirmed both `spec-pack.md` and `report.md` templates carry a `Tạo ngày` header line. |

## 3. Why "timestamp from file content" still leaves a gap for report.md

- `spec-pack.md` template has `Tạo ngày` (created date) in its header block — directly usable for "created time of spec-pack.md".
- `report.md` template **only** has the same `Tạo ngày` field (its own creation date) plus an unrelated `**Ngày:**` line buried inside section 1's body (not part of header metadata, since `extractHeaderMetadata()` stops scanning at the first `##` heading).
- There is **no existing field in report.md that represents "last updated"**. Two options surface here, both requiring a human decision (see spec-pack §16):
  1. Add a new header-metadata field to the `report.md` template (e.g. `- **Cập nhật lần cuối:** {DATE}`) that authors must fill in going forward — accurate, but requires a template change and means historical `report.md` files won't have the field until re-authored.
  2. Reuse `report.md`'s existing `Tạo ngày` value as a proxy for "updated time" — no template change needed, but semantically it's the report's *creation* date, not necessarily its latest update, and could be materially wrong if a report is revised after initial authoring.

## 4. Format/precision question (resolved)

User confirmed: source date values are expected in `YYYY-MM-DD HH:mm:ss`; if a file only supplies `YYYY-MM-DD`, the value should be normalized/displayed as `YYYY-MM-DD 00:00:00`. This removes the earlier ambiguity about storing a date-only value into a `TIMESTAMPTZ` column.

## 5. Where each piece plugs into the existing system

- **Parsing**: extend `SpecPackMarkdownParser`/`ReportMarkdownParser`'s `ParsedArtifact` to expose the relevant header-metadata date field (already available via `MarkdownParserCore`), formatted/normalized per §4.
- **Persistence**: `ArtifactScannerService`'s existing `persistSpecPackParse`/`persistReportParse` steps are the natural place to write the parsed date into `tbl_dim_ticket.started_at`/`completed_at` — no new job/scheduler needed, this rides the existing scan cadence.
- **Migration**: new `V513__...sql` adds `tbl_dim_ticket.completed_at TIMESTAMPTZ`, modeled on the existing `started_at` column (`V4__init_shema_v2.sql:184`).
- **BE read side**: `PmDashboardJdbcAdapter`'s ticket-detail query already selects `started_at` — extend it to also select `completed_at` and surface both in the DTO returned to the FE.
- **FE**: `TicketDetailDrawer.tsx`'s `TicketInformationCard` (~line 236) already has the exact rendering pattern needed (`t("Pages.PmDashboard.<key>", { defaultValue })` + `formatDateTime(...)`, with `mergedAt` as a precedent for conditional/fallback rendering) — add two new fields following that pattern, defaulting to `-` when the value is null.

## 6. Items intentionally deferred to Open Issues / Human Decision (not resolved here)

- Exact header-metadata key/label to standardize on for `spec-pack.md`'s created-time field (assumed `Tạo ngày` — needs confirmation of the normalized key name the parser will use).
- report.md's "updated time" source (new template field vs. proxy reuse of `Tạo ngày`) — **blocking** for Phase 3.
- Whether historical tickets' `spec-pack.md`/`report.md` files already contain parseable dates, or whether a backfill/re-scan is needed after this ships.
- Whether the ticket name "SDD-LEAD-TIME" implies future duration-metric work beyond this narrower scope (out of scope for this ticket unless confirmed otherwise).
