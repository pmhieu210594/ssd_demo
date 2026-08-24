-- Artifact Scanner: V4-only additive migration.

ALTER TABLE tbl_fact_artifact_snapshot
    ADD COLUMN IF NOT EXISTS size_bytes BIGINT,
    ADD COLUMN IF NOT EXISTS scan_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS scan_message TEXT,
    ADD COLUMN IF NOT EXISTS need_parse BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE tbl_dim_ticket
    ADD COLUMN IF NOT EXISTS pr_status pr_status NOT NULL DEFAULT 'OPEN',
    ADD COLUMN IF NOT EXISTS last_commit_at TIMESTAMPTZ;

UPDATE tbl_dim_artifact_type
SET required_flag = TRUE,
    updated_at = now()
WHERE artifact_type_code = 'BLACKBOX_TESTCASES'
  AND required_flag IS DISTINCT FROM TRUE;

INSERT INTO tbl_dim_artifact_type (
    phase_id,
    artifact_type_code,
    artifact_name,
    default_file_name,
    required_flag,
    description
)
SELECT phase.phase_id, seed.artifact_type_code, seed.artifact_name, seed.default_file_name, seed.required_flag, seed.description
FROM (
    VALUES
        ('0-A', 'PHASE0_README', 'Phase 0 README', 'README.md', FALSE, 'Phase 0 maintenance overview and completion gate'),
        ('0-A', 'PHASE0_PLAN', 'Phase 0 Plan', 'phase0-plan.md', FALSE, 'Phase 0 approved plan'),
        ('0-A', 'PHASE0_EXECUTION_LOG', 'Phase 0 Execution Log', 'phase0-execution-log.md', FALSE, 'Phase 0 step-by-step execution log'),
        ('0-A', 'PHASE0_DECISIONS', 'Phase 0 Decisions', 'phase0-decisions.md', FALSE, 'Phase 0 architecture decisions'),
        ('0-A', 'PHASE0_RISK_REGISTER', 'Phase 0 Risk Register', 'phase0-risk-register.md', FALSE, 'Phase 0 risk register'),
        ('0-A', 'PHASE0_REVIEW', 'Phase 0 Review', 'phase0-review.md', FALSE, 'Phase 0 self-judgement gate'),
        ('0-A', 'PHASE0_SOURCE_AVAILABILITY', 'Phase 0 Source Availability', 'source-availability.md', FALSE, 'Phase 0 source availability inventory'),
        ('7', 'BLACKBOX_TESTCASES', 'Blackbox Testcases', 'blackbox-testcases.md', TRUE, 'Blackbox acceptance testcases')
) AS seed(phase_code, artifact_type_code, artifact_name, default_file_name, required_flag, description)
JOIN tbl_dim_phase phase ON phase.phase_code = seed.phase_code
ON CONFLICT (artifact_type_code) DO UPDATE
SET phase_id = EXCLUDED.phase_id,
    artifact_name = EXCLUDED.artifact_name,
    default_file_name = EXCLUDED.default_file_name,
    required_flag = EXCLUDED.required_flag,
    description = EXCLUDED.description,
    updated_at = now();

INSERT INTO tbl_source_connector (
    project_id,
    repository_id,
    connector_type,
    connector_name,
    config_hash,
    enabled,
    created_by,
    updated_by
)
SELECT NULL, NULL, 'ARTIFACT_SCANNER', 'Artifact Scanner', NULL, TRUE, 'SYSTEM', 'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1
    FROM tbl_source_connector
    WHERE connector_type = 'ARTIFACT_SCANNER'
);

CREATE OR REPLACE VIEW vw_artifact_inventory_current AS
SELECT DISTINCT ON (snapshot.repository_id, snapshot.source_path, snapshot.artifact_type_id)
    snapshot.artifact_snapshot_id,
    snapshot.ticket_id,
    ticket.external_ticket_key,
    ticket.pr_status,
    ticket.last_commit_at,
    snapshot.repository_id,
    repository.repo_name_masked,
    snapshot.artifact_type_id,
    snapshot.phase_id,
    artifact_type.artifact_type_code,
    artifact_type.artifact_name,
    artifact_type.default_file_name,
    artifact_type.required_flag,
    phase.phase_code,
    snapshot.source_path,
    snapshot.exists_flag,
    snapshot.content_hash,
    snapshot.size_bytes,
    snapshot.source_updated_at,
    snapshot.template_empty_flag,
    snapshot.need_parse,
    snapshot.scan_status,
    snapshot.scan_message,
    snapshot.connector_run_id,
    snapshot.collected_at,
    snapshot.created_at,
    snapshot.updated_at
FROM tbl_fact_artifact_snapshot snapshot
JOIN tbl_dim_repository repository ON repository.repository_id = snapshot.repository_id
JOIN tbl_dim_artifact_type artifact_type ON artifact_type.artifact_type_id = snapshot.artifact_type_id
LEFT JOIN tbl_dim_phase phase ON phase.phase_id = artifact_type.phase_id
LEFT JOIN tbl_dim_ticket ticket ON ticket.ticket_id = snapshot.ticket_id
ORDER BY snapshot.repository_id, snapshot.source_path, snapshot.artifact_type_id, snapshot.collected_at DESC, snapshot.artifact_snapshot_id DESC;
