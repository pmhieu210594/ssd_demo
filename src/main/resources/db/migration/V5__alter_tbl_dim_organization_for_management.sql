-- Organization Management schema expansion

ALTER TABLE tbl_dim_organization
    ADD COLUMN IF NOT EXISTS organization_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM tbl_dim_organization
        WHERE organization_code IS NULL
    ) THEN
        WITH base_codes AS (
            SELECT
                organization_id,
                COALESCE(
                    NULLIF(
                        UPPER(REGEXP_REPLACE(TRIM(name_masked), '[^A-Za-z0-9]+', '_', 'g')),
                        ''
                    ),
                    'ORG_' || SUBSTRING(REPLACE(organization_id::text, '-', ''), 1, 8)
                ) AS base_code
            FROM tbl_dim_organization
            WHERE organization_code IS NULL
        ),
        numbered AS (
            SELECT
                organization_id,
                base_code,
                ROW_NUMBER() OVER (PARTITION BY base_code ORDER BY organization_id) AS rn
            FROM base_codes
        )
        UPDATE tbl_dim_organization o
        SET organization_code = CASE
            WHEN numbered.rn = 1 THEN LEFT(numbered.base_code, 50)
            ELSE LEFT(numbered.base_code, GREATEST(1, 50 - LENGTH('_' || numbered.rn::text))) || '_' || numbered.rn::text
        END
        FROM numbered
        WHERE o.organization_id = numbered.organization_id;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT LOWER(organization_code)
        FROM tbl_dim_organization
        WHERE deleted_at IS NULL
        GROUP BY LOWER(organization_code)
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate active organization_code values exist before unique index creation';
    END IF;

    IF EXISTS (
        SELECT LOWER(name_masked)
        FROM tbl_dim_organization
        WHERE deleted_at IS NULL
        GROUP BY LOWER(name_masked)
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate active organization name values exist before unique index creation';
    END IF;
END $$;

ALTER TABLE tbl_dim_organization
    ALTER COLUMN organization_code SET NOT NULL,
    ADD CONSTRAINT ck_tbl_dim_organization_description_len
        CHECK (description IS NULL OR CHAR_LENGTH(description) <= 500);

CREATE INDEX IF NOT EXISTS idx_tbl_dim_organization_status
    ON tbl_dim_organization (status);

CREATE INDEX IF NOT EXISTS idx_tbl_dim_organization_updated_at
    ON tbl_dim_organization (updated_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS ux_tbl_dim_organization_code_active
    ON tbl_dim_organization (LOWER(organization_code))
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_tbl_dim_organization_name_active
    ON tbl_dim_organization (LOWER(name_masked))
    WHERE deleted_at IS NULL;
