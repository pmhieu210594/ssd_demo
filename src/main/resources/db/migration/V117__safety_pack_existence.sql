-- =========================================================
-- Safety / Security Evidence MVP additive columns
-- =========================================================

ALTER TABLE tbl_fact_safety_pack_status
    ADD COLUMN IF NOT EXISTS repository_name_masked VARCHAR(255),
    ADD COLUMN IF NOT EXISTS branch_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS commit_sha VARCHAR(128),
    ADD COLUMN IF NOT EXISTS scan_status VARCHAR(50) NOT NULL DEFAULT 'MISSING',
    ADD COLUMN IF NOT EXISTS settings_parse_status VARCHAR(50) NOT NULL DEFAULT 'MISSING',
    ADD COLUMN IF NOT EXISTS content_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS missing_items_summary TEXT;

ALTER TABLE tbl_fact_security_scan
    ADD COLUMN IF NOT EXISTS repository_name_masked VARCHAR(255),
    ADD COLUMN IF NOT EXISTS branch_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS commit_sha VARCHAR(128),
    ADD COLUMN IF NOT EXISTS pull_request_number INT,
    ADD COLUMN IF NOT EXISTS workflow_run_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS workflow_job_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS scan_tool VARCHAR(100),
    ADD COLUMN IF NOT EXISTS scan_status VARCHAR(50),
    ADD COLUMN IF NOT EXISTS critical_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS high_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS medium_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS low_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS info_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS scan_counts_json TEXT;

CREATE INDEX IF NOT EXISTS idx_safety_pack_repo_collected
    ON tbl_fact_safety_pack_status(repository_id, collected_at DESC);

CREATE INDEX IF NOT EXISTS idx_security_scan_repo_workflow
    ON tbl_fact_security_scan(repository_id, workflow_run_id, scanner_type, collected_at DESC);
