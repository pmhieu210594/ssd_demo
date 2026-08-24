-- =========================================================
-- LOGIN MVP: access / refresh token session storage
-- =========================================================

CREATE TABLE IF NOT EXISTS tbl_auth_token_session (
    session_id UUID PRIMARY KEY,
    user_account_id UUID NOT NULL REFERENCES tbl_auth_user_account(user_account_id) ON DELETE CASCADE,
    username VARCHAR(100) NOT NULL,
    access_token_hash CHAR(64) NOT NULL,
    refresh_token_hash CHAR(64) NOT NULL,
    access_token_expires_at TIMESTAMPTZ NOT NULL,
    refresh_token_expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_auth_token_session_updated_at ON tbl_auth_token_session;
CREATE TRIGGER trg_auth_token_session_updated_at
    BEFORE UPDATE ON tbl_auth_token_session
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX IF NOT EXISTS idx_auth_token_session_username_active
    ON tbl_auth_token_session(username)
    WHERE revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_auth_token_session_user_account_active
    ON tbl_auth_token_session(user_account_id)
    WHERE revoked_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_auth_token_session_access_hash_active
    ON tbl_auth_token_session(access_token_hash)
    WHERE revoked_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_auth_token_session_refresh_hash_active
    ON tbl_auth_token_session(refresh_token_hash)
    WHERE revoked_at IS NULL;
