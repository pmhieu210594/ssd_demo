ALTER TABLE tbl_dim_customer
    ADD COLUMN IF NOT EXISTS customer_code VARCHAR(50);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM tbl_dim_customer
        WHERE customer_code IS NULL
    ) THEN
        WITH base_codes AS (
            SELECT
                customer_id,
                organization_id,
                COALESCE(
                    NULLIF(
                        UPPER(REGEXP_REPLACE(TRIM(customer_alias), '[^A-Za-z0-9]+', '_', 'g')),
                        ''
                    ),
                    'CUS_' || SUBSTRING(REPLACE(customer_id::text, '-', ''), 1, 8)
                ) AS base_code
            FROM tbl_dim_customer
            WHERE customer_code IS NULL
        ),
        numbered AS (
            SELECT
                customer_id,
                organization_id,
                base_code,
                ROW_NUMBER() OVER (PARTITION BY organization_id, base_code ORDER BY customer_id) AS rn
            FROM base_codes
        )
        UPDATE tbl_dim_customer c
        SET customer_code = CASE
            WHEN numbered.rn = 1 THEN LEFT(numbered.base_code, 50)
            ELSE LEFT(numbered.base_code, GREATEST(1, 50 - LENGTH('_' || numbered.rn::text))) || '_' || numbered.rn::text
        END
        FROM numbered
        WHERE c.customer_id = numbered.customer_id;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT LOWER(customer_code)
        FROM tbl_dim_customer
        WHERE deleted_at IS NULL
        GROUP BY organization_id, LOWER(customer_code)
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate active customer_code values exist before unique index creation';
    END IF;
END $$;

ALTER TABLE tbl_dim_customer
    ALTER COLUMN customer_code SET NOT NULL,
    ADD CONSTRAINT ck_tbl_dim_customer_code_len
        CHECK (CHAR_LENGTH(customer_code) <= 50);

CREATE UNIQUE INDEX IF NOT EXISTS ux_tbl_dim_customer_code_active
    ON tbl_dim_customer (organization_id, LOWER(customer_code))
    WHERE deleted_at IS NULL;
