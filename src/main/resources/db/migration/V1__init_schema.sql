-- =============================================================================
-- V1: Initial schema for SDD Evidence Platform
-- =============================================================================
-- Naming convention:
--   - snake_case for tables/columns
--   - PK is always `id BIGSERIAL`
--   - business keys use explicit name (ticket_key, repo_key, etc.) + UNIQUE
--   - timestamps in UTC: created_at / updated_at
-- =============================================================================

-- ---------- Users (OAuth2 identities) ----------
CREATE TABLE app_user (
    id              BIGSERIAL PRIMARY KEY,
    provider        VARCHAR(32)  NOT NULL,           -- 'github' | 'google'
    provider_uid    VARCHAR(128) NOT NULL,           -- subject from OAuth provider
    email           VARCHAR(255),
    display_name    VARCHAR(255),
    avatar_url      VARCHAR(512),
    role            VARCHAR(32)  NOT NULL DEFAULT 'VIEWER', -- VIEWER | EDITOR | ADMIN
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (provider, provider_uid)
);

CREATE INDEX idx_user_email ON app_user(email);

-- ---------- Projects & Repositories ----------
CREATE TABLE project (
    id              BIGSERIAL PRIMARY KEY,
    project_key     VARCHAR(64)  NOT NULL UNIQUE,    -- e.g. 'PROJ-A'
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    risk_level      VARCHAR(16)  NOT NULL DEFAULT 'NORMAL', -- LOW | NORMAL | HIGH
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE repository (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT       NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    repo_key        VARCHAR(255) NOT NULL,           -- 'owner/repo' on GitHub
    host_type       VARCHAR(32)  NOT NULL,           -- 'github' | 'gitlab' | 'local'
    default_branch  VARCHAR(128) DEFAULT 'main',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (project_id, repo_key)
);

CREATE INDEX idx_repo_host ON repository(host_type);

-- ---------- Tickets ----------
CREATE TABLE ticket (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT       NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    ticket_key      VARCHAR(64)  NOT NULL,           -- 'ABC-123' from Jira / issue#
    title           VARCHAR(512) NOT NULL,
    ticket_type     VARCHAR(32)  NOT NULL DEFAULT 'TASK', -- FEATURE | BUG | TASK
    status          VARCHAR(32)  NOT NULL DEFAULT 'OPEN',  -- OPEN | IN_PROGRESS | DONE | CLOSED
    priority        VARCHAR(16),
    sdd_phase       SMALLINT     NOT NULL DEFAULT 0,      -- 0..9 (mirrors phase index)
    ac_count        INTEGER      NOT NULL DEFAULT 0,
    opened_at       TIMESTAMPTZ,
    closed_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (project_id, ticket_key)
);

CREATE INDEX idx_ticket_status ON ticket(status);
CREATE INDEX idx_ticket_phase ON ticket(sdd_phase);

-- ---------- Artifacts (the SDD evidence files themselves) ----------
CREATE TABLE artifact (
    id                     BIGSERIAL PRIMARY KEY,
    ticket_id              BIGINT       NOT NULL REFERENCES ticket(id) ON DELETE CASCADE,
    artifact_type          VARCHAR(64)  NOT NULL,    -- spec_pack | impl_plan | self_review | test_plan | test_results | report | blackbox | review_checklist | sources | test_data
    file_path              VARCHAR(1024) NOT NULL,
    content_hash           VARCHAR(128),             -- sha256
    schema_version         VARCHAR(32),
    is_template_only       BOOLEAN      NOT NULL DEFAULT FALSE,
    required_fields_missing TEXT,                    -- JSON array as text, e.g. '["scope","ac"]'
    last_collected_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (ticket_id, artifact_type, file_path)
);

CREATE INDEX idx_artifact_type ON artifact(artifact_type);
CREATE INDEX idx_artifact_ticket ON artifact(ticket_id);

-- ---------- Pull Requests ----------
CREATE TABLE pull_request (
    id                  BIGSERIAL PRIMARY KEY,
    repository_id       BIGINT       NOT NULL REFERENCES repository(id) ON DELETE CASCADE,
    ticket_id           BIGINT       REFERENCES ticket(id) ON DELETE SET NULL,
    external_pr_number  INTEGER      NOT NULL,
    title               VARCHAR(1024),
    state               VARCHAR(32)  NOT NULL,       -- open | merged | closed
    author_login        VARCHAR(128),
    base_branch         VARCHAR(255),
    head_branch         VARCHAR(255),
    opened_at           TIMESTAMPTZ,
    merged_at           TIMESTAMPTZ,
    closed_at           TIMESTAMPTZ,
    review_count        INTEGER      NOT NULL DEFAULT 0,
    comments_count      INTEGER      NOT NULL DEFAULT 0,
    additions           INTEGER,
    deletions           INTEGER,
    changed_files       INTEGER,
    link_url            VARCHAR(1024),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (repository_id, external_pr_number)
);

CREATE INDEX idx_pr_ticket ON pull_request(ticket_id);
CREATE INDEX idx_pr_state ON pull_request(state);

-- ---------- CI Runs ----------
CREATE TABLE ci_run (
    id                  BIGSERIAL PRIMARY KEY,
    repository_id       BIGINT       NOT NULL REFERENCES repository(id) ON DELETE CASCADE,
    pull_request_id     BIGINT       REFERENCES pull_request(id) ON DELETE SET NULL,
    external_run_id     VARCHAR(128) NOT NULL,       -- circle / github actions ID
    provider            VARCHAR(32)  NOT NULL,       -- 'circleci' | 'github_actions'
    workflow_name       VARCHAR(255),
    status              VARCHAR(32)  NOT NULL,       -- success | failed | running | cancelled
    started_at          TIMESTAMPTZ,
    finished_at         TIMESTAMPTZ,
    duration_seconds    INTEGER,
    failure_category    VARCHAR(64),                 -- lint | test | build | security | flaky | other
    is_first_attempt    BOOLEAN      NOT NULL DEFAULT TRUE,
    link_url            VARCHAR(1024),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (provider, external_run_id)
);

CREATE INDEX idx_ci_status ON ci_run(status);
CREATE INDEX idx_ci_pr ON ci_run(pull_request_id);

-- ---------- Findings (review comments / scanner results) ----------
CREATE TABLE finding (
    id                  BIGSERIAL PRIMARY KEY,
    ticket_id           BIGINT       REFERENCES ticket(id) ON DELETE SET NULL,
    pull_request_id     BIGINT       REFERENCES pull_request(id) ON DELETE SET NULL,
    source_actor        VARCHAR(32)  NOT NULL,       -- 'human' | 'ai_reviewer' | 'sast' | 'secret_scan' | 'sca'
    severity            VARCHAR(16)  NOT NULL,       -- BLOCKER | MAJOR | MINOR | INFO
    category            VARCHAR(64),                 -- security | test | performance | style | logic
    status              VARCHAR(16)  NOT NULL DEFAULT 'OPEN', -- OPEN | ACCEPTED | REJECTED | RESOLVED | FALSE_POSITIVE
    summary             VARCHAR(2048),
    raised_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    resolved_at         TIMESTAMPTZ,
    resolved_by_user_id BIGINT       REFERENCES app_user(id) ON DELETE SET NULL
);

CREATE INDEX idx_finding_severity ON finding(severity);
CREATE INDEX idx_finding_status ON finding(status);

-- ---------- Exceptions (--no-verify, skip, emergency merge…) ----------
CREATE TABLE exception_log (
    id                  BIGSERIAL PRIMARY KEY,
    ticket_id           BIGINT       REFERENCES ticket(id) ON DELETE SET NULL,
    exception_type      VARCHAR(64)  NOT NULL,       -- CI_SKIP | NO_VERIFY | TEST_SKIP | EMERGENCY_MERGE | SECURITY_GATE_SKIP
    reason              TEXT,
    alternative_check   TEXT,
    approved_by_role    VARCHAR(64),
    expiry              DATE,
    follow_up_status    VARCHAR(32)  NOT NULL DEFAULT 'OPEN', -- OPEN | RESOLVED | EXPIRED
    linked_report_path  VARCHAR(1024),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by_user_id  BIGINT       REFERENCES app_user(id) ON DELETE SET NULL
);

CREATE INDEX idx_exception_status ON exception_log(follow_up_status);

-- ---------- Acceptance Criteria (extracted from spec-pack) ----------
CREATE TABLE acceptance_criterion (
    id              BIGSERIAL PRIMARY KEY,
    ticket_id       BIGINT       NOT NULL REFERENCES ticket(id) ON DELETE CASCADE,
    ac_number       VARCHAR(16)  NOT NULL,           -- 'AC-1', 'AC-2'
    description     TEXT         NOT NULL,
    is_tested       BOOLEAN      NOT NULL DEFAULT FALSE,
    test_reference  VARCHAR(512),
    UNIQUE (ticket_id, ac_number)
);

-- ---------- Evidence Quality Scores (cached) ----------
CREATE TABLE evidence_quality_score (
    id              BIGSERIAL PRIMARY KEY,
    ticket_id       BIGINT       NOT NULL UNIQUE REFERENCES ticket(id) ON DELETE CASCADE,
    score           SMALLINT     NOT NULL,           -- 0..100
    score_band      VARCHAR(16)  NOT NULL,           -- Excellent | Good | Warning | Risky | Critical
    breakdown_json  TEXT         NOT NULL,           -- JSON detail of each rubric item
    formula_version VARCHAR(16)  NOT NULL,
    calculated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_eqs_score_band ON evidence_quality_score(score_band);

-- ---------- Traceability Links ----------
CREATE TABLE traceability_link (
    id              BIGSERIAL PRIMARY KEY,
    source_type     VARCHAR(32)  NOT NULL,           -- ticket | spec | pr | commit | ci_run | finding | report
    source_id       BIGINT       NOT NULL,
    target_type     VARCHAR(32)  NOT NULL,
    target_id       BIGINT       NOT NULL,
    confidence      SMALLINT     NOT NULL DEFAULT 100, -- 0..100 (heuristic match strength)
    created_by      VARCHAR(32)  NOT NULL DEFAULT 'system', -- 'system' | 'user'
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (source_type, source_id, target_type, target_id)
);

CREATE INDEX idx_trace_source ON traceability_link(source_type, source_id);
CREATE INDEX idx_trace_target ON traceability_link(target_type, target_id);

-- ---------- Connector Run History ----------
CREATE TABLE connector_run (
    id              BIGSERIAL PRIMARY KEY,
    connector_name  VARCHAR(64)  NOT NULL,           -- 'git_local' | 'github' | 'jira' | 'circleci'
    project_id      BIGINT       REFERENCES project(id) ON DELETE CASCADE,
    started_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    finished_at     TIMESTAMPTZ,
    status          VARCHAR(16)  NOT NULL,           -- RUNNING | SUCCESS | FAILED
    records_ingested INTEGER     NOT NULL DEFAULT 0,
    error_message   TEXT,
    trace_id        VARCHAR(64)
);

CREATE INDEX idx_connector_run_name ON connector_run(connector_name);
CREATE INDEX idx_connector_run_status ON connector_run(status);

-- ---------- Audit Log ----------
CREATE TABLE audit_log (
    id              BIGSERIAL PRIMARY KEY,
    actor_user_id   BIGINT       REFERENCES app_user(id) ON DELETE SET NULL,
    actor_type      VARCHAR(32)  NOT NULL DEFAULT 'user', -- user | system | connector
    action          VARCHAR(64)  NOT NULL,
    target_type     VARCHAR(64),
    target_id       BIGINT,
    result          VARCHAR(16)  NOT NULL,
    trace_id        VARCHAR(64),
    details_json    TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_actor ON audit_log(actor_user_id);
CREATE INDEX idx_audit_action ON audit_log(action);
CREATE INDEX idx_audit_created ON audit_log(created_at);
