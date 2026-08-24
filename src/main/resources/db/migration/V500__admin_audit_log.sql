-- =============================================================================
-- V500: Admin Audit Log (ADMIN-AUDIT-LOG)
-- Extends the existing, previously unused tbl_fact_access_log table instead of
-- creating a new table. Does not modify V4__init_shema_v2.sql.
-- =============================================================================

-- Rename existing generic columns to the audit-specific names used by this
-- feature. Table has zero Java references today, so this is safe.
ALTER TABLE tbl_fact_access_log RENAME COLUMN target_type TO entity_type;
ALTER TABLE tbl_fact_access_log RENAME COLUMN target_id TO entity_id;
ALTER TABLE tbl_fact_access_log RENAME COLUMN action TO operation_type;

-- New columns required by the audit spec. actor_member_key / ip_hash /
-- user_agent_hash / purpose / project_id / repository_id are left in place,
-- unused by this feature.
ALTER TABLE tbl_fact_access_log
    ADD COLUMN IF NOT EXISTS actor_user_id UUID REFERENCES tbl_auth_user_account(user_account_id),
    ADD COLUMN IF NOT EXISTS actor_username VARCHAR(128),
    ADD COLUMN IF NOT EXISTS actor_role_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS module VARCHAR(30),
    ADD COLUMN IF NOT EXISTS changed_fields TEXT,
    ADD COLUMN IF NOT EXISTS before_value JSONB,
    ADD COLUMN IF NOT EXISTS after_value JSONB,
    ADD COLUMN IF NOT EXISTS user_agent VARCHAR(256),
    ADD COLUMN IF NOT EXISTS error_message TEXT;

ALTER TABLE tbl_fact_access_log
    ADD CONSTRAINT ck_access_log_module
        CHECK (module IS NULL OR module IN (
            'LOGIN', 'ROLE', 'ORGANIZATION', 'CUSTOMER', 'PROJECT', 'REPOSITORY', 'TEAM', 'MEMBER_USER'
        ));

ALTER TABLE tbl_fact_access_log
    ADD CONSTRAINT ck_access_log_operation_type
        CHECK (operation_type IN (
            'CREATE', 'READ', 'UPDATE', 'DELETE', 'LOGIN_SUCCESS', 'LOGIN_FAILED', 'LOGOUT'
        ));

-- Indexes for the admin audit-log list/filter screen (occurred_at index
-- already exists as idx_access_log_time from V4).
CREATE INDEX IF NOT EXISTS idx_access_log_actor_user ON tbl_fact_access_log(actor_user_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_access_log_entity ON tbl_fact_access_log(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_access_log_module_operation ON tbl_fact_access_log(module, operation_type);

-- Append-only enforcement. REVOKE UPDATE/DELETE has no effect here because
-- the single application DB role owns this table and Postgres table owners
-- bypass REVOKE; a row-level trigger applies regardless of ownership.
CREATE OR REPLACE FUNCTION fn_access_log_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'tbl_fact_access_log is append-only: % not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_access_log_append_only ON tbl_fact_access_log;
CREATE TRIGGER trg_access_log_append_only
    BEFORE UPDATE OR DELETE ON tbl_fact_access_log
    FOR EACH ROW EXECUTE FUNCTION fn_access_log_append_only();
