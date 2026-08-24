-- =============================================================================
-- Drop legacy non-tbl_ tables after removing their call sites.
-- =============================================================================

DROP TABLE IF EXISTS acceptance_criterion CASCADE;
DROP TABLE IF EXISTS app_user CASCADE;
DROP TABLE IF EXISTS project CASCADE;
DROP TABLE IF EXISTS repository CASCADE;
DROP TABLE IF EXISTS ticket CASCADE;
DROP TABLE IF EXISTS artifact CASCADE;
DROP TABLE IF EXISTS pull_request CASCADE;
DROP TABLE IF EXISTS ci_run CASCADE;
DROP TABLE IF EXISTS connector_run CASCADE;

DROP TABLE IF EXISTS audit_log CASCADE;
DROP TABLE IF EXISTS traceability_link CASCADE;
DROP TABLE IF EXISTS evidence_quality_score CASCADE;
DROP TABLE IF EXISTS exception_log CASCADE;
DROP TABLE IF EXISTS finding CASCADE;
