-- Git/PR Metadata Collector: schema objects that depend on newly added enum values.

ALTER TABLE tbl_connector_run
    ADD COLUMN IF NOT EXISTS failure_count INT NOT NULL DEFAULT 0;

ALTER TABLE tbl_fact_pull_request
    ADD COLUMN IF NOT EXISTS external_pr_number INT,
    ADD COLUMN IF NOT EXISTS external_pr_url TEXT,
    ADD COLUMN IF NOT EXISTS external_updated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS review_state review_state NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE tbl_fact_commit
    ADD COLUMN IF NOT EXISTS commit_url TEXT;

CREATE TABLE IF NOT EXISTS tbl_fact_pull_request_changed_file (
    pull_request_changed_file_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pr_id UUID NOT NULL REFERENCES tbl_fact_pull_request(pr_id) ON DELETE CASCADE,
    repository_id UUID NOT NULL REFERENCES tbl_dim_repository(repository_id),
    file_path TEXT NOT NULL,
    file_path_hash VARCHAR(128) NOT NULL,
    file_extension VARCHAR(50),
    change_type VARCHAR(50),
    additions INT NOT NULL DEFAULT 0,
    deletions INT NOT NULL DEFAULT 0,
    collected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT uq_pr_changed_file UNIQUE (pr_id, file_path_hash),
    CONSTRAINT ck_pr_changed_file_stats CHECK (additions >= 0 AND deletions >= 0)
);

CREATE INDEX IF NOT EXISTS idx_pr_changed_file_repo ON tbl_fact_pull_request_changed_file(repository_id);
CREATE INDEX IF NOT EXISTS idx_pr_changed_file_pr ON tbl_fact_pull_request_changed_file(pr_id);
