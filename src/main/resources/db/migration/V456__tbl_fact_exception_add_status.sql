-- Add `status` column to tbl_fact_exception to store exception status parsed from self-review
ALTER TABLE tbl_fact_exception
    ADD COLUMN IF NOT EXISTS status VARCHAR(100) DEFAULT 'OPEN';

-- Optional: keep existing follow_up_status index unchanged
CREATE INDEX IF NOT EXISTS idx_exception_status ON tbl_fact_exception(status);
