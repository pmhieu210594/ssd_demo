-- Replace PR author pseudonym with a readable display name.
-- The collector now stores the GitHub member's full name when it can be resolved.

ALTER TABLE tbl_fact_pull_request
    ADD COLUMN IF NOT EXISTS author_display_name VARCHAR(255);

UPDATE tbl_fact_pull_request pr
SET author_display_name = COALESCE(
        NULLIF(BTRIM(pr.author_display_name), ''),
        NULLIF(BTRIM(a.fullname), ''),
        NULLIF(BTRIM(pr.created_by), ''),
        NULLIF(BTRIM(pr.author_pseudonym), ''),
        'Unknown'
    )
FROM tbl_auth_user_account a
WHERE a.member_key = pr.author_member_key
  AND (pr.author_display_name IS NULL OR BTRIM(pr.author_display_name) = '');

UPDATE tbl_fact_pull_request
SET author_display_name = COALESCE(
        NULLIF(BTRIM(author_display_name), ''),
        NULLIF(BTRIM(created_by), ''),
        'Unknown'
    )
WHERE author_display_name IS NULL OR BTRIM(author_display_name) = '';

ALTER TABLE tbl_fact_pull_request
    DROP COLUMN IF EXISTS author_pseudonym;
