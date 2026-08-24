-- PROMPT_TEMPLATE_REUSE_RATE: track per-(project, repository, phase) template
-- reuse counters for PR-changed ticket documents, and register the 3 missing
-- artifact-type rows needed to scan open-issues.md/context.md/codex-review.md.

CREATE TABLE IF NOT EXISTS tbl_fact_template_usage_stat (
    template_usage_stat_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES tbl_dim_project(project_id),
    repository_id UUID NOT NULL REFERENCES tbl_dim_repository(repository_id),
    phase_id UUID NOT NULL REFERENCES tbl_dim_phase(phase_id),
    total_check_count BIGINT NOT NULL DEFAULT 0,
    template_match_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_template_usage_counts_non_negative
        CHECK (total_check_count >= 0 AND template_match_count >= 0),
    CONSTRAINT ck_template_usage_match_le_total
        CHECK (template_match_count <= total_check_count),
    CONSTRAINT uq_template_usage_scope
        UNIQUE (project_id, repository_id, phase_id)
);

-- Register OPEN_ISSUES artifact type (phase '1' — Spec Pack)
INSERT INTO tbl_dim_artifact_type
    (phase_id, artifact_type_code, artifact_name, default_file_name, required_flag, description)
SELECT phase_id, 'OPEN_ISSUES', 'Open Issues', 'open-issues.md', TRUE, 'Pending judgements and human decisions log'
FROM tbl_dim_phase
WHERE phase_code = '1'
ON CONFLICT (artifact_type_code) DO NOTHING;

-- Register CONTEXT artifact type (phase '2' — Working Files Initialization)
INSERT INTO tbl_dim_artifact_type
    (phase_id, artifact_type_code, artifact_name, default_file_name, required_flag, description)
SELECT phase_id, 'CONTEXT', 'Context', 'context.md', TRUE, 'Source-verified working context'
FROM tbl_dim_phase
WHERE phase_code = '2'
ON CONFLICT (artifact_type_code) DO NOTHING;

-- Register CODEX_REVIEW artifact type (phase '5' — Implementation Review)
INSERT INTO tbl_dim_artifact_type
    (phase_id, artifact_type_code, artifact_name, default_file_name, required_flag, description)
SELECT phase_id, 'CODEX_REVIEW', 'Codex Review', 'codex-review.md', FALSE, 'AI-assisted implementation review notes'
FROM tbl_dim_phase
WHERE phase_code = '5'
ON CONFLICT (artifact_type_code) DO NOTHING;
