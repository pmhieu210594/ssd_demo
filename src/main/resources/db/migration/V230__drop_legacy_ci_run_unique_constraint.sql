-- Remove the legacy one-run-per-repository constraint so workflow_job ingestion
-- can store one row per job (repository + run + job identity).

ALTER TABLE tbl_fact_ci_run
    DROP CONSTRAINT IF EXISTS uq_ci_run_per_repo;

