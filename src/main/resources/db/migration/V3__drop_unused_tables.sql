-- =============================================================================
-- V3: Drop tables not used by the slim demo build
-- Demo scope: SSO login + connect github/jira/circleci + parse markdown
-- =============================================================================
-- Order: children first, but these 5 tables are all leaves (no other table FK
-- references them), so DROP order is safe in any sequence.

DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS traceability_link;
DROP TABLE IF EXISTS evidence_quality_score;
DROP TABLE IF EXISTS exception_log;
DROP TABLE IF EXISTS finding;
