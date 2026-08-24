-- Backfill PR author pseudonym support for existing databases that were
-- already migrated before the column was added to the base schema.

ALTER TABLE tbl_fact_pull_request
    ADD COLUMN IF NOT EXISTS author_pseudonym VARCHAR(255);
