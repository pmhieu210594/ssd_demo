ALTER TABLE tbl_dim_project
    ADD COLUMN IF NOT EXISTS delete_flag BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(100);

UPDATE tbl_dim_project
SET delete_flag = CASE
    WHEN status = 'DELETED' OR deleted_at IS NOT NULL THEN TRUE
    ELSE FALSE
END
WHERE delete_flag IS DISTINCT FROM CASE
    WHEN status = 'DELETED' OR deleted_at IS NOT NULL THEN TRUE
    ELSE FALSE
END;

ALTER TABLE tbl_dim_project
    ALTER COLUMN delete_flag SET DEFAULT FALSE,
    ALTER COLUMN delete_flag SET NOT NULL;

ALTER TABLE tbl_dim_project
    DROP CONSTRAINT IF EXISTS uq_project_alias_per_customer;

CREATE UNIQUE INDEX IF NOT EXISTS uq_tbl_dim_project_customer_alias_active
    ON tbl_dim_project (customer_id, LOWER(BTRIM(project_alias)))
    WHERE delete_flag = FALSE
      AND deleted_at IS NULL
      AND status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_tbl_dim_project_customer_status_updated
    ON tbl_dim_project (customer_id, status, updated_at DESC)
    WHERE delete_flag = FALSE
      AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_tbl_dim_project_status_created
    ON tbl_dim_project (status, created_at DESC)
    WHERE delete_flag = FALSE
      AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_tbl_dim_project_deleted_at
    ON tbl_dim_project (deleted_at)
    WHERE delete_flag = TRUE OR deleted_at IS NOT NULL;


CREATE TABLE IF NOT EXISTS tbl_project_team (
    project_team_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES tbl_dim_project(project_id),
    team_id UUID NOT NULL REFERENCES tbl_dim_team(team_id),
    status record_status NOT NULL DEFAULT 'ACTIVE',
    deleted_at TIMESTAMPTZ,
    deleted_by VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tbl_project_team_active
    ON tbl_project_team (project_id, team_id)
    WHERE deleted_at IS NULL AND status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_tbl_project_team_project_status
    ON tbl_project_team (project_id, status);

CREATE INDEX IF NOT EXISTS idx_tbl_project_team_team_status
    ON tbl_project_team (team_id, status);

CREATE INDEX IF NOT EXISTS idx_tbl_project_team_updated_at
    ON tbl_project_team (updated_at DESC);