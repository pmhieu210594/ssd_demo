-- CI Run Metadata: additive reconciliation for job-level GitHub Actions evidence.

INSERT INTO tbl_source_connector (
    project_id,
    repository_id,
    connector_type,
    connector_name,
    config_hash,
    enabled,
    created_by,
    updated_by
)
SELECT NULL, NULL, 'CI_RUN_METADATA', 'CI Run Metadata', NULL, TRUE, 'SYSTEM', 'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1
    FROM tbl_source_connector
    WHERE connector_type = 'CI_RUN_METADATA'
);

ALTER TABLE tbl_fact_ci_run
    ADD COLUMN IF NOT EXISTS project_id UUID,
    ADD COLUMN IF NOT EXISTS pull_request_id UUID,
    ADD COLUMN IF NOT EXISTS connector_run_id UUID,
    ADD COLUMN IF NOT EXISTS ci_provider VARCHAR(50) DEFAULT 'GITHUB_ACTIONS',
    ADD COLUMN IF NOT EXISTS external_run_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS external_job_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS workflow_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS job_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS ci_url TEXT,
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE tbl_fact_ci_run
    ALTER COLUMN status TYPE VARCHAR(50) USING status::text,
    ALTER COLUMN status SET DEFAULT 'UNKNOWN';

UPDATE tbl_fact_ci_run
SET status = 'IN_PROGRESS'
WHERE status = 'RUNNING';

UPDATE tbl_fact_ci_run
SET status = 'QUEUED'
WHERE status = 'PENDING';

UPDATE tbl_fact_ci_run c
SET project_id = COALESCE(c.project_id, r.project_id),
    ci_provider = COALESCE(c.ci_provider, 'GITHUB_ACTIONS'),
    external_run_id = COALESCE(c.external_run_id, c.external_ci_run_id),
    external_job_id = COALESCE(c.external_job_id, c.external_ci_run_id),
    workflow_name = COALESCE(c.workflow_name, 'UNKNOWN'),
    job_name = COALESCE(c.job_name, 'UNKNOWN'),
    ci_url = COALESCE(
        c.ci_url,
        CASE
            WHEN r.repo_name_masked IS NOT NULL AND c.external_ci_run_id IS NOT NULL THEN
                'https://github.com/' || r.repo_name_masked || '/actions/runs/' || c.external_ci_run_id
            ELSE NULL
        END
    ),
    completed_at = COALESCE(c.completed_at, c.finished_at),
    created_at = COALESCE(c.created_at, c.collected_at, now()),
    updated_at = COALESCE(c.updated_at, c.collected_at, now())
FROM tbl_dim_repository r
WHERE c.repository_id = r.repository_id
  AND (
        c.ci_provider IS NULL
     OR c.external_run_id IS NULL
     OR c.external_job_id IS NULL
     OR c.workflow_name IS NULL
     OR c.job_name IS NULL
     OR c.ci_url IS NULL
     OR c.completed_at IS NULL
     OR c.created_at IS NULL
     OR c.updated_at IS NULL
  );

ALTER TABLE tbl_fact_ci_run
    ALTER COLUMN ci_provider SET DEFAULT 'GITHUB_ACTIONS',
    ALTER COLUMN ci_provider SET NOT NULL,
    ALTER COLUMN external_run_id SET NOT NULL,
    ALTER COLUMN external_job_id SET NOT NULL,
    ALTER COLUMN workflow_name SET NOT NULL,
    ALTER COLUMN job_name SET NOT NULL;

ALTER TABLE tbl_fact_ci_run
    ADD CONSTRAINT ck_ci_run_status_text
        CHECK (status IN ('SUCCESS', 'FAILURE', 'CANCELLED', 'SKIPPED', 'IN_PROGRESS', 'QUEUED', 'UNKNOWN'));

ALTER TABLE tbl_fact_ci_run
    ADD CONSTRAINT ck_ci_run_time_order_text
        CHECK (completed_at IS NULL OR started_at IS NULL OR started_at <= completed_at);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tbl_fact_ci_run_provider_repo_run_job
    ON tbl_fact_ci_run (ci_provider, repository_id, external_run_id, external_job_id);

CREATE INDEX IF NOT EXISTS idx_tbl_fact_ci_run_repository
    ON tbl_fact_ci_run(repository_id);

CREATE INDEX IF NOT EXISTS idx_tbl_fact_ci_run_ticket
    ON tbl_fact_ci_run(ticket_id);

CREATE INDEX IF NOT EXISTS idx_tbl_fact_ci_run_pull_request
    ON tbl_fact_ci_run(pull_request_id);

CREATE INDEX IF NOT EXISTS idx_tbl_fact_ci_run_external_run_id
    ON tbl_fact_ci_run(external_run_id);

CREATE INDEX IF NOT EXISTS idx_tbl_fact_ci_run_external_job_id
    ON tbl_fact_ci_run(external_job_id);

CREATE INDEX IF NOT EXISTS idx_tbl_fact_ci_run_status_text
    ON tbl_fact_ci_run(status);

CREATE INDEX IF NOT EXISTS idx_tbl_fact_ci_run_started_at_text
    ON tbl_fact_ci_run(started_at DESC);

CREATE INDEX IF NOT EXISTS idx_tbl_fact_ci_run_completed_at_text
    ON tbl_fact_ci_run(completed_at DESC);

ALTER TABLE tbl_connector_run
    ADD COLUMN IF NOT EXISTS provider VARCHAR(50),
    ADD COLUMN IF NOT EXISTS repository_id UUID REFERENCES tbl_dim_repository(repository_id),
    ADD COLUMN IF NOT EXISTS records_received INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS records_inserted INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS records_updated INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS records_skipped INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS records_error INT NOT NULL DEFAULT 0;

UPDATE tbl_connector_run
SET records_received = COALESCE(records_received, records_read, 0),
    records_inserted = COALESCE(records_inserted, records_written, 0),
    records_updated = COALESCE(records_updated, 0),
    records_skipped = COALESCE(records_skipped, 0),
    records_error = COALESCE(records_error, 0)
WHERE records_received IS NULL
   OR records_inserted IS NULL
   OR records_updated IS NULL
   OR records_skipped IS NULL
   OR records_error IS NULL;
