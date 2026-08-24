-- =============================================================================
-- V395: Seed Security role for the Safety Pack (security) dashboard access.
-- Idempotent so existing databases can apply it safely.
-- =============================================================================

INSERT INTO tbl_dim_role (role_name, description)
SELECT 'SECURITY', 'Security'
WHERE NOT EXISTS (
    SELECT 1 FROM tbl_dim_role WHERE role_name = 'SECURITY'
);