ALTER TABLE tbl_dim_role
    ADD COLUMN IF NOT EXISTS status record_status NOT NULL DEFAULT 'ACTIVE';

UPDATE tbl_dim_role SET status = 'DELETED' WHERE delete_flag = 1;
