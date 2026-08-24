-- =========================================================
-- Deduplicate and upsert security evidence snapshots
-- =========================================================

WITH ranked_security_scan AS (
    SELECT ctid,
           ROW_NUMBER() OVER (
               PARTITION BY repository_id, commit_sha, scanner_type
               ORDER BY collected_at DESC, security_scan_id DESC
           ) AS rn
    FROM tbl_fact_security_scan
)
DELETE FROM tbl_fact_security_scan s
USING ranked_security_scan r
WHERE s.ctid = r.ctid
  AND r.rn > 1;

WITH ranked_safety_pack AS (
    SELECT ctid,
           ROW_NUMBER() OVER (
               PARTITION BY repository_id, commit_sha
               ORDER BY collected_at DESC, safety_pack_status_id DESC
           ) AS rn
    FROM tbl_fact_safety_pack_status
)
DELETE FROM tbl_fact_safety_pack_status s
USING ranked_safety_pack r
WHERE s.ctid = r.ctid
  AND r.rn > 1;

ALTER TABLE tbl_fact_security_scan
    ADD CONSTRAINT uq_tbl_fact_security_scan_repo_commit_scanner
        UNIQUE (repository_id, commit_sha, scanner_type);

ALTER TABLE tbl_fact_safety_pack_status
    DROP CONSTRAINT IF EXISTS uq_safety_pack_repo_time;

ALTER TABLE tbl_fact_safety_pack_status
    ADD CONSTRAINT uq_tbl_fact_safety_pack_repo_commit
        UNIQUE (repository_id, commit_sha);
