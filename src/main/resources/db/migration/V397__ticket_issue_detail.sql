-- Persist parsed issue details from spec-pack and report artifacts.

CREATE TABLE IF NOT EXISTS tbl_fact_ticket_issue (
    ticket_issue_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES tbl_dim_ticket(ticket_id) ON DELETE CASCADE,
    repository_id UUID NOT NULL REFERENCES tbl_dim_repository(repository_id),
    artifact_snapshot_id UUID NOT NULL REFERENCES tbl_fact_artifact_snapshot(artifact_snapshot_id) ON DELETE CASCADE,
    source_type VARCHAR(50) NOT NULL,
    issue_order INT NOT NULL,
    issue_key VARCHAR(255),
    issue_title TEXT,
    issue_impact TEXT,
    issue_owner TEXT,
    issue_status TEXT,
    issue_summary TEXT NOT NULL,
    source_path TEXT NOT NULL,
    collected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);

CREATE INDEX IF NOT EXISTS idx_ticket_issue_ticket_source_order
    ON tbl_fact_ticket_issue(ticket_id, source_type, issue_order, ticket_issue_id);
