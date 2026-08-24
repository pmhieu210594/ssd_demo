-- =============================================================================
-- V91: Seed Data Ops role for dashboard access
-- Idempotent so existing databases can apply it safely.
-- =============================================================================

INSERT INTO tbl_dim_role (role_name, description) VALUES
    ('DATA_OPS', 'Data Operations')
ON CONFLICT (role_name) DO NOTHING;
