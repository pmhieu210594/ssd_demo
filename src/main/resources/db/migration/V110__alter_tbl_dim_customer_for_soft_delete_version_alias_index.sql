ALTER TABLE tbl_dim_customer
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM tbl_dim_customer
        WHERE deleted_at IS NULL
        GROUP BY organization_id, LOWER(customer_alias)
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate active customer aliases exist before unique index creation';
    END IF;
END $$;

ALTER TABLE tbl_dim_customer
    DROP CONSTRAINT IF EXISTS uq_customer_alias_per_org;

CREATE INDEX IF NOT EXISTS idx_tbl_dim_customer_status
    ON tbl_dim_customer (status);

CREATE INDEX IF NOT EXISTS idx_tbl_dim_customer_updated_at
    ON tbl_dim_customer (updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_tbl_dim_customer_organization_deleted
    ON tbl_dim_customer (organization_id, deleted_at);

CREATE UNIQUE INDEX IF NOT EXISTS ux_tbl_dim_customer_alias_active
    ON tbl_dim_customer (organization_id, LOWER(customer_alias))
    WHERE deleted_at IS NULL;
