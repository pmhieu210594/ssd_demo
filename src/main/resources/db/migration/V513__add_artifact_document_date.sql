-- PHASE-DWELL-TIME: additive-only table storing per-file create_date/update_date
-- parsed from each artifact .md header (MarkdownParserCore.headerMetadata()).
-- Read-only for the Phase Dwell Time feature; does not alter/drop any existing table.
CREATE TABLE IF NOT EXISTS tbl_fact_artifact_document_date (
    artifact_document_date_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    artifact_snapshot_id UUID NOT NULL REFERENCES tbl_fact_artifact_snapshot(artifact_snapshot_id) ON DELETE CASCADE,
    document_create_at TIMESTAMPTZ,
    document_update_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT uq_artifact_document_date_snapshot UNIQUE (artifact_snapshot_id)
);
