DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM tbl_dim_customer
        WHERE deleted_at IS NULL
        GROUP BY LOWER(customer_code)
        HAVING COUNT(*) > 1
    ) THEN
        WITH duplicate_rows AS (
            SELECT
                customer_id,
                customer_code,
                ROW_NUMBER() OVER (
                    PARTITION BY LOWER(customer_code)
                    ORDER BY customer_id
                ) AS rn
            FROM tbl_dim_customer
            WHERE deleted_at IS NULL
        )
        UPDATE tbl_dim_customer c
        SET customer_code = LEFT(
                duplicate_rows.customer_code,
                GREATEST(
                    1,
                    50 - LENGTH('_' || SUBSTRING(REPLACE(c.customer_id::text, '-', ''), 1, 8))
                )
            ) || '_' || SUBSTRING(REPLACE(c.customer_id::text, '-', ''), 1, 8)
        FROM duplicate_rows
        WHERE c.customer_id = duplicate_rows.customer_id
          AND duplicate_rows.rn > 1;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM tbl_dim_customer
        WHERE deleted_at IS NULL
        GROUP BY LOWER(customer_code)
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate active customer_code values still exist after remediation';
    END IF;
END $$;

DROP INDEX IF EXISTS ux_tbl_dim_customer_code_active;

CREATE UNIQUE INDEX IF NOT EXISTS ux_tbl_dim_customer_code_active
    ON tbl_dim_customer (LOWER(customer_code))
    WHERE deleted_at IS NULL;
