-- AI-REVIEW-KPI-IMPROVEMENT: store the §8 "Số liệu thống kê" snapshot parsed from
-- each ticket's ai-review.md at PR-merge time, 1 row/ticket, upsert-overwrite by ticket_id.

CREATE TABLE IF NOT EXISTS tbl_fact_ai_finding_stat (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES tbl_dim_project(project_id),
    repository_id UUID NOT NULL REFERENCES tbl_dim_repository(repository_id),
    ticket_id UUID NOT NULL UNIQUE REFERENCES tbl_dim_ticket(ticket_id),
    blocker_major_resolved_count INTEGER NULL,
    blocker_major_total_count INTEGER NULL,
    ai_review_adopted_count INTEGER NULL,
    ai_review_finding_total_count INTEGER NULL,
    ai_review_valid_count INTEGER NULL,
    ai_review_false_positive_count INTEGER NULL,
    ai_review_resolved_count INTEGER NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_ai_finding_stat_updated_at ON tbl_fact_ai_finding_stat;
CREATE TRIGGER trg_ai_finding_stat_updated_at
    BEFORE UPDATE ON tbl_fact_ai_finding_stat
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX IF NOT EXISTS idx_ai_finding_stat_project_repository
    ON tbl_fact_ai_finding_stat(project_id, repository_id);
