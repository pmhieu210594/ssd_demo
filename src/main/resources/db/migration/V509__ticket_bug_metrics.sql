CREATE TABLE IF NOT EXISTS tbl_ticket_bug_metrics (
    ticket_bug_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES tbl_dim_project(project_id),
    repository_id UUID NOT NULL REFERENCES tbl_dim_repository(repository_id),
    ticket_id UUID NOT NULL REFERENCES tbl_dim_ticket(ticket_id),
    internal_bug_count INT NOT NULL DEFAULT 0,
    customer_bug_count INT NOT NULL DEFAULT 0,
    note VARCHAR(500),
    delete_flag BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by VARCHAR(100),
    status record_status NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT ck_ticket_bug_metrics_internal_count_nonneg CHECK (internal_bug_count >= 0),
    CONSTRAINT ck_ticket_bug_metrics_customer_count_nonneg CHECK (customer_bug_count >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ticket_bug_metrics_active_ticket
    ON tbl_ticket_bug_metrics (ticket_id)
    WHERE delete_flag = FALSE
      AND deleted_at IS NULL
      AND status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_ticket_bug_metrics_project_status
    ON tbl_ticket_bug_metrics (project_id, status)
    WHERE delete_flag = FALSE
      AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_ticket_bug_metrics_repository
    ON tbl_ticket_bug_metrics (repository_id)
    WHERE delete_flag = FALSE
      AND deleted_at IS NULL;

-- Bước 1: remove constraint old
ALTER TABLE tbl_fact_access_log
DROP CONSTRAINT ck_access_log_module;

-- Bước 2: add constraint new - add 'SCORE_THRESHOLD'
ALTER TABLE tbl_fact_access_log
ADD CONSTRAINT ck_access_log_module
        CHECK (module IS NULL OR module IN (
            'LOGIN', 'ROLE', 'ORGANIZATION', 'CUSTOMER', 'PROJECT', 'REPOSITORY', 'TEAM', 'MEMBER_USER', 'SCORE_THRESHOLD', 'TICKET_BUG_METRICS'
        ));
