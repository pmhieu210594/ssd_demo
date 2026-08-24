-- AC-FCI-9: add source_section provenance column to tbl_fact_exception.
-- Needed to disambiguate "self-review" vs "report" rows for the same
-- (ticket_id, exception_type) pair, enabling idempotent ON CONFLICT upsert.

ALTER TABLE tbl_fact_exception
    ADD COLUMN IF NOT EXISTS source_section VARCHAR(100);

-- Partial unique index: one exception row per (ticket, type, source_section)
CREATE UNIQUE INDEX IF NOT EXISTS uq_exception_ticket_type_section
    ON tbl_fact_exception (ticket_id, exception_type, source_section)
    WHERE ticket_id IS NOT NULL;

-- Supporting index for the First CI Pass query
CREATE INDEX IF NOT EXISTS idx_ci_run_pull_request_started_at
    ON tbl_fact_ci_run (pull_request_id, started_at ASC)
    WHERE pull_request_id IS NOT NULL;

-- Register REPORT artifact type if missing (needed for report.md scanning in ArtifactScannerService)
INSERT INTO tbl_dim_artifact_type
    (phase_id, artifact_type_code, artifact_name, default_file_name, required_flag, description)
SELECT phase_id, 'REPORT', 'Report', 'report.md', TRUE, 'Final ticket evidence report'
FROM tbl_dim_phase
WHERE phase_code = '8'
ON CONFLICT (artifact_type_code) DO NOTHING;
