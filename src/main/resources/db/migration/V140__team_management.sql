ALTER TABLE tbl_dim_team
    ADD COLUMN IF NOT EXISTS team_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

UPDATE tbl_dim_team
SET team_code = COALESCE(
    team_code,
    LEFT(COALESCE(team_name, 'TEAM'), 41) || '-' || SUBSTRING(team_id::text FROM 1 FOR 8)
)
WHERE team_code IS NULL;

ALTER TABLE tbl_dim_team
    ALTER COLUMN team_code SET NOT NULL;

DROP INDEX IF EXISTS idx_team_project;

ALTER TABLE tbl_dim_team
    DROP COLUMN IF EXISTS project_id;

CREATE UNIQUE INDEX IF NOT EXISTS uq_tbl_dim_team_code_active
    ON tbl_dim_team (LOWER(team_code))
    WHERE deleted_at IS NULL AND status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_tbl_dim_team_status_updated
    ON tbl_dim_team (status, updated_at DESC);

CREATE TABLE IF NOT EXISTS tbl_team_member (
    team_member_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES tbl_dim_team(team_id),
    member_key UUID NOT NULL REFERENCES tbl_dim_member_pseudonym(member_key),
    role_id UUID NOT NULL REFERENCES tbl_dim_role(role_id),
    status record_status NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    deleted_by VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tbl_team_member_active
    ON tbl_team_member (team_id, member_key)
    WHERE deleted_at IS NULL AND status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_tbl_team_member_team_status
    ON tbl_team_member (team_id, status);

CREATE INDEX IF NOT EXISTS idx_tbl_team_member_member_status
    ON tbl_team_member (member_key, status);

CREATE INDEX IF NOT EXISTS idx_tbl_team_member_role
    ON tbl_team_member (role_id);
