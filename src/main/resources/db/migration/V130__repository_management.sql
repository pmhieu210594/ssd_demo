ALTER TABLE tbl_dim_repository
    ADD COLUMN IF NOT EXISTS delete_flag BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(100);

UPDATE tbl_dim_repository
SET delete_flag = CASE
    WHEN status = 'DELETED' OR deleted_at IS NOT NULL THEN TRUE
    ELSE FALSE
END
WHERE delete_flag IS DISTINCT FROM CASE
    WHEN status = 'DELETED' OR deleted_at IS NOT NULL THEN TRUE
    ELSE FALSE
END;

ALTER TABLE tbl_dim_repository
    ALTER COLUMN delete_flag SET DEFAULT FALSE,
    ALTER COLUMN delete_flag SET NOT NULL;

ALTER TABLE tbl_dim_repository
    DROP CONSTRAINT IF EXISTS uq_repo_per_project;

CREATE UNIQUE INDEX IF NOT EXISTS uq_tbl_dim_repository_project_repo_name_active
    ON tbl_dim_repository (project_id, LOWER(BTRIM(repo_name_masked)))
    WHERE delete_flag = FALSE
      AND deleted_at IS NULL
      AND status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_tbl_dim_repository_project_status_updated
    ON tbl_dim_repository (project_id, status, updated_at DESC)
    WHERE delete_flag = FALSE
      AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_tbl_dim_repository_status_created
    ON tbl_dim_repository (status, created_at DESC)
    WHERE delete_flag = FALSE
      AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_tbl_dim_repository_deleted_at
    ON tbl_dim_repository (deleted_at)
    WHERE delete_flag = TRUE OR deleted_at IS NOT NULL;
