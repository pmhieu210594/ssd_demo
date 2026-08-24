CREATE TABLE IF NOT EXISTS tbl_dim_ai_quality (
    ticket_ai_quality_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES tbl_dim_project(project_id),
    repository_id UUID NOT NULL REFERENCES tbl_dim_repository(repository_id),
    ticket_id UUID NOT NULL REFERENCES tbl_dim_ticket(ticket_id),
    ai_quality_rate DECIMAL(5,2) NOT NULL DEFAULT 0,
    delete_flag BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by VARCHAR(100),
    status record_status NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT ck_ai_quality_rate_range CHECK (ai_quality_rate >= 0 AND ai_quality_rate <= 100)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_quality_active_ticket
    ON tbl_dim_ai_quality (ticket_id)
    WHERE delete_flag = FALSE
      AND deleted_at IS NULL
      AND status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_ai_quality_project_status
    ON tbl_dim_ai_quality (project_id, status)
    WHERE delete_flag = FALSE
      AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_ai_quality_repository
    ON tbl_dim_ai_quality (repository_id)
    WHERE delete_flag = FALSE
      AND deleted_at IS NULL;

-- Bước 1: remove constraint old
ALTER TABLE tbl_fact_access_log
DROP CONSTRAINT ck_access_log_module;

-- Bước 2: add constraint new - add 'AI_QUALITY'
ALTER TABLE tbl_fact_access_log
ADD CONSTRAINT ck_access_log_module
        CHECK (module IS NULL OR module IN (
            'LOGIN', 'ROLE', 'ORGANIZATION', 'CUSTOMER', 'PROJECT', 'REPOSITORY', 'TEAM', 'MEMBER_USER', 'SCORE_THRESHOLD', 'TICKET_BUG_METRICS', 'AI_QUALITY'
        ));
