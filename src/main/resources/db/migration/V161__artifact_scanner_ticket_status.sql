-- Artifact Scanner: move PR status into tbl_dim_ticket.status and drop pr_status.

ALTER TYPE ticket_status ADD VALUE IF NOT EXISTS 'MERGED';
ALTER TYPE ticket_status ADD VALUE IF NOT EXISTS 'DRAFT';

UPDATE tbl_dim_ticket
SET status = pr_status::text::ticket_status,
    updated_at = now()
WHERE pr_status IS NOT NULL;

DROP VIEW IF EXISTS vw_artifact_inventory_current;

ALTER TABLE tbl_dim_ticket
    DROP COLUMN IF EXISTS pr_status;

CREATE VIEW vw_artifact_inventory_current AS
SELECT DISTINCT ON (snapshot.repository_id, snapshot.source_path, snapshot.artifact_type_id)
    snapshot.artifact_snapshot_id,
    snapshot.ticket_id,
    ticket.external_ticket_key,
    ticket.status AS pr_status,
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
