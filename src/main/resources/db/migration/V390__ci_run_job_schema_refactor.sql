-- V235: Refactor tbl_fact_ci_run to store one row per workflow run (not per job).
-- Individual job details move to tbl_fact_ci_job (already has schema from V4).
--
-- REASON: tbl_fact_ci_run was storing one row per GitHub Actions JOB because each
-- webhook event is job-level. This migration separates concerns:
--   tbl_fact_ci_run  → overall workflow run (1 row per run, aggregate status)
--   tbl_fact_ci_job  → individual job results (FK → ci_run_id)

-- Step 1: Drop job-level columns from tbl_fact_ci_run
ALTER TABLE tbl_fact_ci_run
    DROP COLUMN IF EXISTS external_job_id,
    DROP COLUMN IF EXISTS job_name;

-- Step 2: Data migration — keep ONE row per workflow run (most recently updated)
-- Required before creating the new unique index; without this the CREATE INDEX fails.
DELETE FROM tbl_fact_ci_run
WHERE ci_run_id NOT IN (
    SELECT DISTINCT ON (ci_provider, repository_id, external_run_id) ci_run_id
    FROM tbl_fact_ci_run
    ORDER BY ci_provider, repository_id, external_run_id,
             COALESCE(updated_at, created_at) DESC
);

-- Step 3: Drop old per-job unique index
DROP INDEX IF EXISTS uq_tbl_fact_ci_run_provider_repo_run_job;

-- Step 4: New unique index — 1 row per workflow run
CREATE UNIQUE INDEX uq_ci_run_per_provider_repo_run
    ON tbl_fact_ci_run (ci_provider, repository_id, external_run_id);
