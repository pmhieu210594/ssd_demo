-- Bước 1: remove constraint old
ALTER TABLE tbl_fact_access_log
DROP CONSTRAINT ck_access_log_module;

-- Bước 2: add constraint new - add 'SCORE_THRESHOLD'
ALTER TABLE tbl_fact_access_log
ADD CONSTRAINT ck_access_log_module
        CHECK (module IS NULL OR module IN (
            'LOGIN', 'ROLE', 'ORGANIZATION', 'CUSTOMER', 'PROJECT', 'REPOSITORY', 'TEAM', 'MEMBER_USER', 'SCORE_THRESHOLD'
        ));