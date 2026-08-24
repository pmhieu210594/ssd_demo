-- =========================================================
-- SDD Evidence Data Collection & Analysis Platform
-- Full PostgreSQL DDL
-- Version: 1.1 - fixed PostgreSQL syntax
-- Table naming: all table names use tbl_ prefix
-- Target DB: PostgreSQL 14+
-- Notes:
--   - Repo / PR / CI / artifact files are SSOT.
--   - This DB stores metadata, hashes, links, summaries, score and KPI.
--   - Do NOT store raw AI chat, raw prompt, full source code, secrets or raw CI logs.
-- =========================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =========================================================
-- 0. CLEANUP SECTION, OPTIONAL
-- Uncomment when recreating schema from scratch.
-- =========================================================

-- DROP SCHEMA public CASCADE;
-- CREATE SCHEMA public;
-- CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =========================================================
-- 1. ENUM TYPES
-- =========================================================

DO $$ BEGIN
    CREATE TYPE record_status AS ENUM ('ACTIVE', 'INACTIVE', 'ARCHIVED', 'DELETED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE ticket_status AS ENUM ('OPEN', 'IN_PROGRESS', 'IN_REVIEW', 'DONE', 'CLOSED', 'CANCELLED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE pr_status AS ENUM ('OPEN', 'MERGED', 'CLOSED', 'DRAFT');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE run_status AS ENUM ('SUCCESS', 'FAILED', 'CANCELLED', 'SKIPPED', 'RUNNING', 'PENDING', 'UNKNOWN', 'QUEUED', 'IN_PROGRESS', 'FAILURE');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE review_state AS ENUM ('REQUESTED', 'COMMENTED', 'CHANGES_REQUESTED', 'APPROVED', 'DISMISSED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE finding_status AS ENUM ('OPEN', 'ACCEPTED', 'REJECTED', 'RESOLVED', 'FALSE_POSITIVE', 'WONT_FIX');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE severity_level AS ENUM ('INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE score_band AS ENUM ('EXCELLENT', 'GOOD', 'WARNING', 'RISKY', 'CRITICAL');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE link_confidence_level AS ENUM ('HIGH', 'MEDIUM', 'LOW');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE access_result AS ENUM ('SUCCESS', 'DENIED', 'FAILED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- =========================================================
-- 2. UPDATED_AT TRIGGER FUNCTION
-- =========================================================

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =========================================================
-- 3. DIMENSION / MASTER TABLES
-- =========================================================

CREATE TABLE IF NOT EXISTS tbl_dim_organization (
    organization_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name_masked VARCHAR(255) NOT NULL,
    status record_status NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);

CREATE TABLE IF NOT EXISTS tbl_dim_customer (
    customer_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES tbl_dim_organization(organization_id),
    customer_alias VARCHAR(255) NOT NULL,
    classification VARCHAR(50) NOT NULL DEFAULT 'INTERNAL',
    status record_status NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT uq_customer_alias_per_org UNIQUE (organization_id, customer_alias)
);

CREATE TABLE IF NOT EXISTS tbl_dim_project (
    project_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL REFERENCES tbl_dim_customer(customer_id),
    project_alias VARCHAR(255) NOT NULL,
    project_type VARCHAR(100),
    risk_level severity_level DEFAULT 'MEDIUM',
    status record_status NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT uq_project_alias_per_customer UNIQUE (customer_id, project_alias)
);

CREATE TABLE IF NOT EXISTS tbl_dim_repository (
    repository_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES tbl_dim_project(project_id),
    repo_name_masked VARCHAR(255) NOT NULL,
    host_type VARCHAR(50) NOT NULL,
    default_branch VARCHAR(255),
    repo_url_hash VARCHAR(128),
    status record_status NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT uq_repo_per_project UNIQUE (project_id, repo_name_masked)
);

CREATE TABLE IF NOT EXISTS tbl_dim_team (
    team_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES tbl_dim_project(project_id),
    team_name VARCHAR(255) NOT NULL,
    status record_status NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT uq_team_per_project UNIQUE (project_id, team_name)
);

CREATE TABLE IF NOT EXISTS tbl_dim_role (
    role_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);

CREATE TABLE IF NOT EXISTS tbl_dim_member_pseudonym (
    member_key UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id UUID REFERENCES tbl_dim_role(role_id),
    team_id UUID REFERENCES tbl_dim_team(team_id),
    pseudonym VARCHAR(255) NOT NULL,
    external_user_hash VARCHAR(128),
    active_from DATE NOT NULL DEFAULT CURRENT_DATE,
    active_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT uq_member_pseudonym UNIQUE (pseudonym),
    CONSTRAINT ck_member_active_period CHECK (active_to IS NULL OR active_to >= active_from)
);

CREATE TABLE IF NOT EXISTS tbl_dim_ticket (
    ticket_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES tbl_dim_project(project_id),
    external_ticket_key VARCHAR(100) NOT NULL,
    ticket_type VARCHAR(50),
    priority VARCHAR(50),
    status ticket_status NOT NULL DEFAULT 'OPEN',
    title TEXT,
    created_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    started_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    closed_by VARCHAR(100),
    merged_at TIMESTAMPTZ,
    merged_by VARCHAR(100),
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(100),
    CONSTRAINT uq_ticket_per_project UNIQUE (project_id, external_ticket_key),
    CONSTRAINT ck_ticket_date_order CHECK (closed_at IS NULL OR created_at IS NULL OR closed_at >= created_at)
);

CREATE TABLE IF NOT EXISTS tbl_dim_phase (
    phase_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phase_code VARCHAR(20) NOT NULL UNIQUE,
    phase_name VARCHAR(255) NOT NULL,
    phase_order INT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);

CREATE TABLE IF NOT EXISTS tbl_dim_artifact_type (
    artifact_type_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phase_id UUID REFERENCES tbl_dim_phase(phase_id),
    artifact_type_code VARCHAR(100) NOT NULL UNIQUE,
    artifact_name VARCHAR(255) NOT NULL,
    default_file_name VARCHAR(255),
    required_flag BOOLEAN NOT NULL DEFAULT FALSE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);

CREATE TABLE IF NOT EXISTS tbl_dim_actor_type (
    actor_type_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_type_code VARCHAR(100) NOT NULL UNIQUE,
    actor_type_name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);

CREATE TABLE IF NOT EXISTS tbl_dim_finding_category (
    category_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_code VARCHAR(100) NOT NULL UNIQUE,
    category_name VARCHAR(255) NOT NULL,
    severity_default severity_level NOT NULL DEFAULT 'MEDIUM',
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);

CREATE TABLE IF NOT EXISTS tbl_dim_security_classification (
    classification_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    classification_code VARCHAR(100) NOT NULL UNIQUE,
    classification_name VARCHAR(255) NOT NULL,
    handling_policy TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);

CREATE TABLE IF NOT EXISTS tbl_dim_metric_definition (
    metric_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_code VARCHAR(100) NOT NULL,
    metric_name VARCHAR(255) NOT NULL,
    formula TEXT NOT NULL,
    version VARCHAR(50) NOT NULL,
    owner_role_id UUID REFERENCES tbl_dim_role(role_id),
    valid_from DATE NOT NULL,
    valid_to DATE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT uq_metric_version UNIQUE (metric_code, version),
    CONSTRAINT ck_metric_valid_period CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE TABLE IF NOT EXISTS tbl_dim_prompt_template (
    prompt_template_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_code VARCHAR(100) NOT NULL,
    template_version VARCHAR(50) NOT NULL,
    phase_id UUID REFERENCES tbl_dim_phase(phase_id),
    template_hash VARCHAR(128) NOT NULL,
    description TEXT,
    valid_from DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT uq_prompt_template_version UNIQUE (template_code, template_version),
    CONSTRAINT ck_prompt_template_period CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

-- =========================================================
-- 4. SOURCE / CONNECTOR MANAGEMENT
-- =========================================================

CREATE TABLE IF NOT EXISTS tbl_source_connector (
    connector_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID REFERENCES tbl_dim_project(project_id),
    repository_id UUID REFERENCES tbl_dim_repository(repository_id),
    connector_type VARCHAR(100) NOT NULL,
    connector_name VARCHAR(255) NOT NULL,
    config_hash VARCHAR(128),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);

CREATE TABLE IF NOT EXISTS tbl_connector_source_mapping (
    connector_source_mapping_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connector_id UUID NOT NULL REFERENCES tbl_source_connector(connector_id) ON DELETE CASCADE,
    source_system VARCHAR(100) NOT NULL,
    source_scope VARCHAR(255),
    api_scope_hash VARCHAR(128),
    active_flag BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT uq_connector_source_mapping UNIQUE (connector_id, source_system, source_scope)
);

CREATE TABLE IF NOT EXISTS tbl_connector_run (
    connector_run_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connector_id UUID NOT NULL REFERENCES tbl_source_connector(connector_id),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    status run_status NOT NULL DEFAULT 'RUNNING',
    records_read INT NOT NULL DEFAULT 0,
    records_written INT NOT NULL DEFAULT 0,
    error_message TEXT,
    trace_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT ck_connector_run_records CHECK (records_read >= 0 AND records_written >= 0),
    CONSTRAINT ck_connector_run_time CHECK (finished_at IS NULL OR finished_at >= started_at)
);

-- =========================================================
-- 5. TICKET PHASE / ARTIFACT RULES / ARTIFACT SNAPSHOT
-- =========================================================

CREATE TABLE IF NOT EXISTS tbl_fact_ticket_phase_status (
    ticket_phase_status_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES tbl_dim_ticket(ticket_id) ON DELETE CASCADE,
    phase_id UUID NOT NULL REFERENCES tbl_dim_phase(phase_id),
    status VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED',
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    dwell_time_minutes INT,
    blocked_flag BOOLEAN NOT NULL DEFAULT FALSE,
    block_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT uq_ticket_phase UNIQUE (ticket_id, phase_id),
    CONSTRAINT ck_ticket_phase_time CHECK (completed_at IS NULL OR started_at IS NULL OR completed_at >= started_at),
    CONSTRAINT ck_ticket_phase_dwell CHECK (dwell_time_minutes IS NULL OR dwell_time_minutes >= 0)
);

CREATE TABLE IF NOT EXISTS tbl_artifact_required_field_rule (
    rule_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    artifact_type_id UUID NOT NULL REFERENCES tbl_dim_artifact_type(artifact_type_id),
    schema_version VARCHAR(100) NOT NULL,
    field_code VARCHAR(100) NOT NULL,
    field_name VARCHAR(255) NOT NULL,
    section_type VARCHAR(100),
    required_flag BOOLEAN NOT NULL DEFAULT TRUE,
    score_weight NUMERIC(5,2) DEFAULT 0,
    valid_from DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT uq_required_field_rule UNIQUE (artifact_type_id, schema_version, field_code),
    CONSTRAINT ck_required_field_period CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT ck_required_field_weight CHECK (score_weight IS NULL OR score_weight >= 0)
);

CREATE TABLE IF NOT EXISTS tbl_fact_artifact_snapshot (
    artifact_snapshot_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID REFERENCES tbl_dim_ticket(ticket_id),
    repository_id UUID NOT NULL REFERENCES tbl_dim_repository(repository_id),
    artifact_type_id UUID NOT NULL REFERENCES tbl_dim_artifact_type(artifact_type_id),
    phase_id UUID REFERENCES tbl_dim_phase(phase_id),
    source_path TEXT NOT NULL,
    source_url_hash VARCHAR(128),
    exists_flag BOOLEAN NOT NULL DEFAULT TRUE,
    content_hash VARCHAR(128),
    schema_version VARCHAR(100),
    schema_valid BOOLEAN,
    template_empty_flag BOOLEAN DEFAULT FALSE,
    required_fields_missing JSONB NOT NULL DEFAULT '[]'::jsonb,
    parsed_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    privacy_classification VARCHAR(50),
    contains_customer_data BOOLEAN DEFAULT FALSE,
    contains_personal_data BOOLEAN DEFAULT FALSE,
    contains_secret_detected BOOLEAN DEFAULT FALSE,
    source_created_at TIMESTAMPTZ,
    source_updated_at TIMESTAMPTZ,
    collected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    parser_version VARCHAR(100),
    connector_run_id UUID REFERENCES tbl_connector_run(connector_run_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT uq_artifact_snapshot UNIQUE (repository_id, source_path, content_hash)
);

CREATE TABLE IF NOT EXISTS tbl_fact_artifact_parsed_section (
    parsed_section_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    artifact_snapshot_id UUID NOT NULL REFERENCES tbl_fact_artifact_snapshot(artifact_snapshot_id) ON DELETE CASCADE,
    ticket_id UUID REFERENCES tbl_dim_ticket(ticket_id),
    section_type VARCHAR(100) NOT NULL,
    section_key VARCHAR(100),
    section_text_hash VARCHAR(128),
    section_summary TEXT,
    required_flag BOOLEAN NOT NULL DEFAULT FALSE,
    present_flag BOOLEAN NOT NULL DEFAULT TRUE,
    valid_flag BOOLEAN,
    parse_warning TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);

CREATE TABLE IF NOT EXISTS tbl_fact_acceptance_criteria (
    ac_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES tbl_dim_ticket(ticket_id) ON DELETE CASCADE,
    artifact_snapshot_id UUID REFERENCES tbl_fact_artifact_snapshot(artifact_snapshot_id),
    ac_key VARCHAR(100) NOT NULL,
    ac_text_hash VARCHAR(128),
    ac_summary TEXT,
    ambiguous_flag BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT uq_ac_per_ticket UNIQUE (ticket_id, ac_key)
);

CREATE TABLE IF NOT EXISTS tbl_fact_evidence_event (
    evidence_event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID REFERENCES tbl_dim_ticket(ticket_id),
    repository_id UUID REFERENCES tbl_dim_repository(repository_id),
    artifact_snapshot_id UUID REFERENCES tbl_fact_artifact_snapshot(artifact_snapshot_id),
    event_type VARCHAR(100) NOT NULL,
    actor_type_id UUID REFERENCES tbl_dim_actor_type(actor_type_id),
    actor_member_key UUID REFERENCES tbl_dim_member_pseudonym(member_key),
    source_type VARCHAR(100),
    source_ref_id VARCHAR(255),
    event_timestamp TIMESTAMPTZ NOT NULL,
    result VARCHAR(100),
    summary TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);

-- =========================================================
-- 6. TRACEABILITY
-- =========================================================

CREATE TABLE IF NOT EXISTS tbl_fact_traceability_link (
    traceability_link_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID REFERENCES tbl_dim_ticket(ticket_id),
    source_type VARCHAR(100) NOT NULL,
    source_id VARCHAR(255) NOT NULL,
    target_type VARCHAR(100) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    confidence NUMERIC(5,2) NOT NULL DEFAULT 100.00,
    confidence_level link_confidence_level NOT NULL DEFAULT 'HIGH',
    created_by_source VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    rule_name VARCHAR(255),
    evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT ck_traceability_confidence CHECK (confidence >= 0 AND confidence <= 100),
    CONSTRAINT uq_traceability UNIQUE (source_type, source_id, target_type, target_id)
);

-- =========================================================
-- 7. GIT / PULL REQUEST / REVIEW / FINDING
-- =========================================================

CREATE TABLE IF NOT EXISTS tbl_fact_commit (
    commit_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id UUID NOT NULL REFERENCES tbl_dim_repository(repository_id),
    ticket_id UUID REFERENCES tbl_dim_ticket(ticket_id),
    commit_hash VARCHAR(128) NOT NULL,
    author_member_key UUID REFERENCES tbl_dim_member_pseudonym(member_key),
    author_pseudonym VARCHAR(255),
    committed_at TIMESTAMPTZ,
    branch_name VARCHAR(255),
    tag_name VARCHAR(255),
    message_hash VARCHAR(128),
    changed_file_count INT DEFAULT 0,
    added_lines INT DEFAULT 0,
    deleted_lines INT DEFAULT 0,
    collected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT uq_commit_per_repo UNIQUE (repository_id, commit_hash),
    CONSTRAINT ck_commit_stats CHECK (changed_file_count >= 0 AND added_lines >= 0 AND deleted_lines >= 0)
);

CREATE TABLE IF NOT EXISTS tbl_fact_commit_changed_file (
    commit_changed_file_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    commit_id UUID NOT NULL REFERENCES tbl_fact_commit(commit_id) ON DELETE CASCADE,
    repository_id UUID NOT NULL REFERENCES tbl_dim_repository(repository_id),
    file_path_hash VARCHAR(128) NOT NULL,
    file_extension VARCHAR(50),
    change_type VARCHAR(50),
    added_lines INT DEFAULT 0,
    deleted_lines INT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT ck_changed_file_stats CHECK (added_lines >= 0 AND deleted_lines >= 0)
);

CREATE TABLE IF NOT EXISTS tbl_fact_pull_request (
    pr_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id UUID NOT NULL REFERENCES tbl_dim_repository(repository_id),
    ticket_id UUID REFERENCES tbl_dim_ticket(ticket_id),
    external_pr_id VARCHAR(100) NOT NULL,
    title TEXT,
    description_hash VARCHAR(128),
    status pr_status NOT NULL,
    source_branch VARCHAR(255),
    target_branch VARCHAR(255),
    opened_at TIMESTAMPTZ,
    merged_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    author_member_key UUID REFERENCES tbl_dim_member_pseudonym(member_key),
    labels JSONB NOT NULL DEFAULT '[]'::jsonb,
    linked_issue_key VARCHAR(100),
    collected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT uq_pr_per_repo UNIQUE (repository_id, external_pr_id),
    CONSTRAINT ck_pr_time CHECK (merged_at IS NULL OR opened_at IS NULL OR merged_at >= opened_at)
);

CREATE TABLE IF NOT EXISTS tbl_fact_pull_request_commit (
    pr_id UUID NOT NULL REFERENCES tbl_fact_pull_request(pr_id) ON DELETE CASCADE,
    commit_id UUID NOT NULL REFERENCES tbl_fact_commit(commit_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    PRIMARY KEY (pr_id, commit_id)
);

CREATE TABLE IF NOT EXISTS tbl_fact_review (
    review_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pr_id UUID NOT NULL REFERENCES tbl_fact_pull_request(pr_id) ON DELETE CASCADE,
    ticket_id UUID REFERENCES tbl_dim_ticket(ticket_id),
    reviewer_member_key UUID REFERENCES tbl_dim_member_pseudonym(member_key),
    reviewer_role_id UUID REFERENCES tbl_dim_role(role_id),
    state review_state NOT NULL,
    source_actor_type_id UUID REFERENCES tbl_dim_actor_type(actor_type_id),
    started_at TIMESTAMPTZ,
    submitted_at TIMESTAMPTZ,
    comment_count INT NOT NULL DEFAULT 0,
    collected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT ck_review_comment_count CHECK (comment_count >= 0),
    CONSTRAINT ck_review_time CHECK (submitted_at IS NULL OR started_at IS NULL OR submitted_at >= started_at)
);

CREATE TABLE IF NOT EXISTS tbl_fact_review_comment (
    review_comment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id UUID REFERENCES tbl_fact_review(review_id) ON DELETE CASCADE,
    pr_id UUID NOT NULL REFERENCES tbl_fact_pull_request(pr_id) ON DELETE CASCADE,
    ticket_id UUID REFERENCES tbl_dim_ticket(ticket_id),
    commenter_member_key UUID REFERENCES tbl_dim_member_pseudonym(member_key),
    actor_type_id UUID REFERENCES tbl_dim_actor_type(actor_type_id),
    comment_hash VARCHAR(128),
    comment_summary TEXT,
    file_path_hash VARCHAR(128),
    line_number INT,
    category_id UUID REFERENCES tbl_dim_finding_category(category_id),
    severity severity_level,
    resolved_flag BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);

CREATE TABLE IF NOT EXISTS tbl_fact_finding (
    finding_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID REFERENCES tbl_dim_ticket(ticket_id),
    pr_id UUID REFERENCES tbl_fact_pull_request(pr_id),
    review_id UUID REFERENCES tbl_fact_review(review_id),
    review_comment_id UUID REFERENCES tbl_fact_review_comment(review_comment_id),
    source_actor_type_id UUID REFERENCES tbl_dim_actor_type(actor_type_id),
    category_id UUID REFERENCES tbl_dim_finding_category(category_id),
    severity severity_level NOT NULL DEFAULT 'MEDIUM',
    status finding_status NOT NULL DEFAULT 'OPEN',
    accepted_flag BOOLEAN,
    false_positive_reason TEXT,
    finding_summary TEXT,
    source_location_hash VARCHAR(128),
    detected_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT ck_finding_time CHECK (resolved_at IS NULL OR detected_at IS NULL OR resolved_at >= detected_at)
);

-- =========================================================
-- 8. CI / TEST / AC COVERAGE
-- =========================================================

CREATE TABLE IF NOT EXISTS tbl_fact_ci_run (
    ci_run_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id UUID NOT NULL REFERENCES tbl_dim_repository(repository_id),
    ticket_id UUID REFERENCES tbl_dim_ticket(ticket_id),
    pr_id UUID REFERENCES tbl_fact_pull_request(pr_id),
    external_ci_run_id VARCHAR(255) NOT NULL,
    workflow_name VARCHAR(255),
    status run_status NOT NULL DEFAULT 'UNKNOWN',
    duration_seconds INT,
    first_run_flag BOOLEAN DEFAULT FALSE,
    rerun_count INT DEFAULT 0,
    failure_category VARCHAR(100),
    artifact_link_hash VARCHAR(128),
    summary TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    collected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_run_per_repo UNIQUE (repository_id, external_ci_run_id),
    CONSTRAINT ck_ci_duration CHECK (duration_seconds IS NULL OR duration_seconds >= 0),
    CONSTRAINT ck_ci_time CHECK (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at),
    CONSTRAINT ck_ci_rerun CHECK (rerun_count >= 0)
);

CREATE TABLE IF NOT EXISTS tbl_fact_ci_job (
    ci_job_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ci_run_id UUID NOT NULL REFERENCES tbl_fact_ci_run(ci_run_id) ON DELETE CASCADE,
    external_job_id VARCHAR(255),
    job_name VARCHAR(255),
    status run_status NOT NULL DEFAULT 'UNKNOWN',
    duration_seconds INT,
    failure_category VARCHAR(100),
    failure_summary TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    CONSTRAINT uq_ci_job_per_run UNIQUE (ci_run_id, external_job_id),
    CONSTRAINT ck_ci_job_duration CHECK (duration_seconds IS NULL OR duration_seconds >= 0),
    CONSTRAINT ck_ci_job_time CHECK (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at)
);

CREATE TABLE IF NOT EXISTS tbl_fact_test_run (
    test_run_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id UUID NOT NULL REFERENCES tbl_dim_repository(repository_id),
    ticket_id UUID REFERENCES tbl_dim_ticket(ticket_id),
    pr_id UUID REFERENCES tbl_fact_pull_request(pr_id),
    ci_run_id UUID REFERENCES tbl_fact_ci_run(ci_run_id),
    external_test_run_id VARCHAR(255),
    test_type VARCHAR(100),
    status run_status NOT NULL DEFAULT 'UNKNOWN',
    test_count INT DEFAULT 0,
    passed_count INT DEFAULT 0,
    failed_count INT DEFAULT 0,
    skipped_count INT DEFAULT 0,
    duration_seconds INT,
    coverage_percent NUMERIC(5,2),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    collected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_test_count_non_negative CHECK (test_count >= 0 AND passed_count >= 0 AND failed_count >= 0 AND skipped_count >= 0),
    CONSTRAINT ck_test_count_sum CHECK (test_count >= passed_count + failed_count + skipped_count OR test_count = 0),
    CONSTRAINT ck_coverage_percent CHECK (coverage_percent IS NULL OR coverage_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_test_time CHECK (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at)
);

CREATE TABLE IF NOT EXISTS tbl_fact_test_case (
    test_case_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    test_run_id UUID NOT NULL REFERENCES tbl_fact_test_run(test_run_id) ON DELETE CASCADE,
    ticket_id UUID REFERENCES tbl_dim_ticket(ticket_id),
    test_case_key VARCHAR(255),
    test_case_name_hash VARCHAR(128),
    ac_reference VARCHAR(100),
    status run_status NOT NULL DEFAULT 'UNKNOWN',
    duration_ms INT,
    failure_summary TEXT,
    flaky_candidate_flag BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT ck_test_case_duration CHECK (duration_ms IS NULL OR duration_ms >= 0)
);

CREATE TABLE IF NOT EXISTS tbl_fact_ac_test_coverage (
    ac_test_coverage_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES tbl_dim_ticket(ticket_id),
    ac_id UUID REFERENCES tbl_fact_acceptance_criteria(ac_id),
    artifact_snapshot_id UUID REFERENCES tbl_fact_artifact_snapshot(artifact_snapshot_id),
    ac_key VARCHAR(100) NOT NULL,
    ac_text_hash VARCHAR(128),
    test_case_id UUID REFERENCES tbl_fact_test_case(test_case_id),
    test_run_id UUID REFERENCES tbl_fact_test_run(test_run_id),
    coverage_status VARCHAR(50) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT uq_ac_test_coverage UNIQUE (ticket_id, ac_key, test_case_id)
);

-- =========================================================
-- 9. SECURITY / SAFETY / EXCEPTION
-- =========================================================

CREATE TABLE IF NOT EXISTS tbl_fact_security_scan (
    security_scan_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id UUID NOT NULL REFERENCES tbl_dim_repository(repository_id),
    ticket_id UUID REFERENCES tbl_dim_ticket(ticket_id),
    pr_id UUID REFERENCES tbl_fact_pull_request(pr_id),
    ci_run_id UUID REFERENCES tbl_fact_ci_run(ci_run_id),
    scanner_type VARCHAR(100) NOT NULL,
    scanner_name VARCHAR(255),
    status run_status NOT NULL DEFAULT 'UNKNOWN',
    severity severity_level,
    finding_count INT NOT NULL DEFAULT 0,
    unresolved_count INT NOT NULL DEFAULT 0,
    summary TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    collected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_security_counts CHECK (finding_count >= 0 AND unresolved_count >= 0 AND unresolved_count <= finding_count),
    CONSTRAINT ck_security_scan_time CHECK (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at)
);

CREATE TABLE IF NOT EXISTS tbl_fact_security_finding (
    security_finding_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    security_scan_id UUID NOT NULL REFERENCES tbl_fact_security_scan(security_scan_id) ON DELETE CASCADE,
    ticket_id UUID REFERENCES tbl_dim_ticket(ticket_id),
    pr_id UUID REFERENCES tbl_fact_pull_request(pr_id),
    rule_id VARCHAR(255),
    scanner_type VARCHAR(100) NOT NULL,
    severity severity_level NOT NULL,
    status finding_status NOT NULL DEFAULT 'OPEN',
    finding_summary TEXT,
    affected_path_hash VARCHAR(128),
    detected_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    false_positive_flag BOOLEAN DEFAULT FALSE,
    resolution_summary TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT ck_security_finding_time CHECK (resolved_at IS NULL OR detected_at IS NULL OR resolved_at >= detected_at)
);

CREATE TABLE IF NOT EXISTS tbl_fact_safety_pack_status (
    safety_pack_status_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id UUID NOT NULL REFERENCES tbl_dim_repository(repository_id),
    claude_md_exists BOOLEAN NOT NULL DEFAULT FALSE,
    settings_json_exists BOOLEAN NOT NULL DEFAULT FALSE,
    rules_exists BOOLEAN NOT NULL DEFAULT FALSE,
    deny_rule_count INT DEFAULT 0,
    ask_rule_count INT DEFAULT 0,
    allow_rule_count INT DEFAULT 0,
    reviewed_flag BOOLEAN DEFAULT FALSE,
    reviewed_by_role_id UUID REFERENCES tbl_dim_role(role_id),
    last_updated_at TIMESTAMPTZ,
    collected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT uq_safety_pack_repo_time UNIQUE (repository_id, collected_at),
    CONSTRAINT ck_safety_rule_counts CHECK (deny_rule_count >= 0 AND ask_rule_count >= 0 AND allow_rule_count >= 0)
);

CREATE TABLE IF NOT EXISTS tbl_fact_exception (
    exception_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID REFERENCES tbl_dim_ticket(ticket_id),
    repository_id UUID REFERENCES tbl_dim_repository(repository_id),
    pr_id UUID REFERENCES tbl_fact_pull_request(pr_id),
    ci_run_id UUID REFERENCES tbl_fact_ci_run(ci_run_id),
    exception_type VARCHAR(100) NOT NULL,
    reason_present BOOLEAN NOT NULL DEFAULT FALSE,
    reason TEXT,
    alternative_check TEXT,
    approved BOOLEAN NOT NULL DEFAULT FALSE,
    approved_by_role_id UUID REFERENCES tbl_dim_role(role_id),
    approved_at TIMESTAMPTZ,
    expiry_date DATE,
    follow_up_status VARCHAR(100) DEFAULT 'OPEN',
    linked_report_path TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT ck_exception_expiry CHECK (expiry_date IS NULL OR expiry_date >= DATE '2000-01-01')
);

-- =========================================================
-- 10. AI USAGE METADATA, NO RAW PROMPT / RAW CHAT
-- =========================================================

CREATE TABLE IF NOT EXISTS tbl_fact_ai_usage (
    ai_usage_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID REFERENCES tbl_dim_ticket(ticket_id),
    repository_id UUID REFERENCES tbl_dim_repository(repository_id),
    phase_id UUID REFERENCES tbl_dim_phase(phase_id),
    actor_type_id UUID REFERENCES tbl_dim_actor_type(actor_type_id),
    prompt_template_id UUID REFERENCES tbl_dim_prompt_template(prompt_template_id),
    model_name_hash VARCHAR(128),
    input_tokens INT,
    output_tokens INT,
    read_file_count INT,
    cache_hit BOOLEAN,
    prompt_hash VARCHAR(128),
    output_summary_hash VARCHAR(128),
    used_at TIMESTAMPTZ,
    collected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT ck_ai_usage_non_negative CHECK (COALESCE(input_tokens, 0) >= 0 AND COALESCE(output_tokens, 0) >= 0 AND COALESCE(read_file_count, 0) >= 0)
);

-- =========================================================
-- 11. DECISION / RISK / REWORK / QUALITY GATE
-- =========================================================

CREATE TABLE IF NOT EXISTS tbl_fact_decision (
    decision_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID REFERENCES tbl_dim_ticket(ticket_id),
    repository_id UUID REFERENCES tbl_dim_repository(repository_id),
    artifact_snapshot_id UUID REFERENCES tbl_fact_artifact_snapshot(artifact_snapshot_id),
    decision_key VARCHAR(100),
    decision_summary TEXT,
    reason_present BOOLEAN NOT NULL DEFAULT FALSE,
    impact_summary TEXT,
    decided_by_role_id UUID REFERENCES tbl_dim_role(role_id),
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);

CREATE TABLE IF NOT EXISTS tbl_fact_risk (
    risk_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID REFERENCES tbl_dim_ticket(ticket_id),
    repository_id UUID REFERENCES tbl_dim_repository(repository_id),
    artifact_snapshot_id UUID REFERENCES tbl_fact_artifact_snapshot(artifact_snapshot_id),
    risk_key VARCHAR(100),
    risk_summary TEXT,
    severity severity_level NOT NULL DEFAULT 'MEDIUM',
    mitigation_present BOOLEAN NOT NULL DEFAULT FALSE,
    mitigation_summary TEXT,
    status VARCHAR(100) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    resolved_at TIMESTAMPTZ,
    CONSTRAINT ck_risk_time CHECK (resolved_at IS NULL OR resolved_at >= created_at)
);

CREATE TABLE IF NOT EXISTS tbl_fact_rework_event (
    rework_event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES tbl_dim_ticket(ticket_id),
    pr_id UUID REFERENCES tbl_fact_pull_request(pr_id),
    commit_id UUID REFERENCES tbl_fact_commit(commit_id),
    rework_type VARCHAR(100) NOT NULL,
    reason_summary TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);

CREATE TABLE IF NOT EXISTS tbl_fact_quality_gate (
    quality_gate_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID REFERENCES tbl_dim_ticket(ticket_id),
    pr_id UUID REFERENCES tbl_fact_pull_request(pr_id),
    ci_run_id UUID REFERENCES tbl_fact_ci_run(ci_run_id),
    gate_type VARCHAR(100) NOT NULL,
    gate_status run_status NOT NULL DEFAULT 'UNKNOWN',
    required_flag BOOLEAN NOT NULL DEFAULT TRUE,
    passed_flag BOOLEAN,
    checked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    summary TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);

-- =========================================================
-- 12. METRIC / SCORE / LINEAGE
-- =========================================================

CREATE TABLE IF NOT EXISTS tbl_fact_data_lineage (
    lineage_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_name VARCHAR(255) NOT NULL,
    job_run_id VARCHAR(255),
    connector_run_id UUID REFERENCES tbl_connector_run(connector_run_id),
    source_type VARCHAR(100) NOT NULL,
    source_ref_id VARCHAR(255),
    source_hash VARCHAR(128),
    target_table VARCHAR(255) NOT NULL,
    target_record_id VARCHAR(255),
    parser_version VARCHAR(100),
    metric_definition_version VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);

CREATE TABLE IF NOT EXISTS tbl_fact_metric_value (
    metric_value_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_id UUID NOT NULL REFERENCES tbl_dim_metric_definition(metric_id),
    metric_code VARCHAR(100) NOT NULL,
    definition_version VARCHAR(50) NOT NULL,
    organization_id UUID REFERENCES tbl_dim_organization(organization_id),
    customer_id UUID REFERENCES tbl_dim_customer(customer_id),
    project_id UUID REFERENCES tbl_dim_project(project_id),
    repository_id UUID REFERENCES tbl_dim_repository(repository_id),
    ticket_id UUID REFERENCES tbl_dim_ticket(ticket_id),
    team_id UUID REFERENCES tbl_dim_team(team_id),
    period_type VARCHAR(50) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    value NUMERIC(18,4) NOT NULL,
    score_band score_band,
    breakdown JSONB NOT NULL DEFAULT '{}'::jsonb,
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    lineage_id UUID REFERENCES tbl_fact_data_lineage(lineage_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT ck_metric_period CHECK (period_end >= period_start)
);

CREATE TABLE IF NOT EXISTS tbl_fact_metric_input_lineage (
    metric_input_lineage_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_value_id UUID NOT NULL REFERENCES tbl_fact_metric_value(metric_value_id) ON DELETE CASCADE,
    input_table VARCHAR(255) NOT NULL,
    input_record_id VARCHAR(255) NOT NULL,
    input_hash VARCHAR(128),
    contribution_type VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);

CREATE TABLE IF NOT EXISTS tbl_fact_evidence_quality_score (
    evidence_quality_score_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES tbl_dim_ticket(ticket_id),
    metric_value_id UUID REFERENCES tbl_fact_metric_value(metric_value_id),
    score NUMERIC(5,2) NOT NULL,
    score_band score_band NOT NULL,
    score_rule_version VARCHAR(50) NOT NULL,
    spec_score NUMERIC(5,2) DEFAULT 0,
    plan_score NUMERIC(5,2) DEFAULT 0,
    review_score NUMERIC(5,2) DEFAULT 0,
    self_review_score NUMERIC(5,2) DEFAULT 0,
    test_score NUMERIC(5,2) DEFAULT 0,
    ci_score NUMERIC(5,2) DEFAULT 0,
    blackbox_score NUMERIC(5,2) DEFAULT 0,
    report_score NUMERIC(5,2) DEFAULT 0,
    missing_items JSONB NOT NULL DEFAULT '[]'::jsonb,
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT ck_evidence_score CHECK (score BETWEEN 0 AND 100)
);

CREATE TABLE IF NOT EXISTS tbl_metric_definition_change_log (
    change_log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_id UUID NOT NULL REFERENCES tbl_dim_metric_definition(metric_id),
    changed_by_member_key UUID REFERENCES tbl_dim_member_pseudonym(member_key),
    old_version VARCHAR(50),
    new_version VARCHAR(50),
    change_reason TEXT NOT NULL,
    impact_summary TEXT,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);

-- =========================================================
-- 13. DATA QUALITY / AUDIT / EXPORT / RETENTION
-- =========================================================

CREATE TABLE IF NOT EXISTS tbl_fact_data_quality (
    data_quality_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connector_run_id UUID REFERENCES tbl_connector_run(connector_run_id),
    project_id UUID REFERENCES tbl_dim_project(project_id),
    repository_id UUID REFERENCES tbl_dim_repository(repository_id),
    source_type VARCHAR(100) NOT NULL,
    source_ref VARCHAR(255),
    missing_count INT NOT NULL DEFAULT 0,
    parse_error_count INT NOT NULL DEFAULT 0,
    schema_violation_count INT NOT NULL DEFAULT 0,
    freshness_delay_minutes INT,
    error_summary TEXT,
    checked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT ck_data_quality_counts CHECK (missing_count >= 0 AND parse_error_count >= 0 AND schema_violation_count >= 0),
    CONSTRAINT ck_freshness_delay CHECK (freshness_delay_minutes IS NULL OR freshness_delay_minutes >= 0)
);

CREATE TABLE IF NOT EXISTS tbl_fact_access_log (
    access_log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_member_key UUID REFERENCES tbl_dim_member_pseudonym(member_key),
    actor_role_id UUID REFERENCES tbl_dim_role(role_id),
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(100) NOT NULL,
    target_id VARCHAR(255),
    project_id UUID REFERENCES tbl_dim_project(project_id),
    repository_id UUID REFERENCES tbl_dim_repository(repository_id),
    purpose TEXT,
    result access_result NOT NULL,
    trace_id VARCHAR(100),
    ip_hash VARCHAR(128),
    user_agent_hash VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);

CREATE TABLE IF NOT EXISTS tbl_fact_export_log (
    export_log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_member_key UUID REFERENCES tbl_dim_member_pseudonym(member_key),
    actor_role_id UUID REFERENCES tbl_dim_role(role_id),
    export_type VARCHAR(100) NOT NULL,
    project_id UUID REFERENCES tbl_dim_project(project_id),
    ticket_id UUID REFERENCES tbl_dim_ticket(ticket_id),
    purpose TEXT NOT NULL,
    approved_by_role_id UUID REFERENCES tbl_dim_role(role_id),
    file_hash VARCHAR(128),
    result access_result NOT NULL DEFAULT 'SUCCESS',
    trace_id VARCHAR(100),
    exported_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);

CREATE TABLE IF NOT EXISTS tbl_export_approval (
    export_approval_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    export_log_id UUID REFERENCES tbl_fact_export_log(export_log_id) ON DELETE CASCADE,
    requested_by_member_key UUID REFERENCES tbl_dim_member_pseudonym(member_key),
    approved_by_member_key UUID REFERENCES tbl_dim_member_pseudonym(member_key),
    approved_by_role_id UUID REFERENCES tbl_dim_role(role_id),
    approval_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_at TIMESTAMPTZ,
    reason TEXT,
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT ck_export_approval_time CHECK (approved_at IS NULL OR approved_at >= requested_at)
);

CREATE TABLE IF NOT EXISTS tbl_data_retention_policy (
    retention_policy_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    data_category VARCHAR(100) NOT NULL,
    classification_id UUID REFERENCES tbl_dim_security_classification(classification_id),
    retention_days INT NOT NULL,
    deletion_method VARCHAR(100) NOT NULL DEFAULT 'SOFT_DELETE',
    description TEXT,
    valid_from DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT ck_retention_days CHECK (retention_days > 0),
    CONSTRAINT ck_retention_period CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT uq_retention_policy UNIQUE (data_category, classification_id, valid_from)
);

-- =========================================================
-- 14. RBAC / ACCESS SCOPE
-- =========================================================

CREATE TABLE IF NOT EXISTS tbl_auth_permission (
    permission_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    permission_code VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);

CREATE TABLE IF NOT EXISTS tbl_auth_role_permission (
    role_id UUID NOT NULL REFERENCES tbl_dim_role(role_id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES tbl_auth_permission(permission_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS tbl_auth_member_project_role (
    member_key UUID NOT NULL REFERENCES tbl_dim_member_pseudonym(member_key) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES tbl_dim_project(project_id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES tbl_dim_role(role_id),
    active_flag BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    PRIMARY KEY (member_key, project_id, role_id)
);

CREATE TABLE IF NOT EXISTS tbl_auth_member_access_scope (
    access_scope_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_key UUID NOT NULL REFERENCES tbl_dim_member_pseudonym(member_key) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES tbl_dim_role(role_id),
    organization_id UUID REFERENCES tbl_dim_organization(organization_id),
    customer_id UUID REFERENCES tbl_dim_customer(customer_id),
    project_id UUID REFERENCES tbl_dim_project(project_id),
    repository_id UUID REFERENCES tbl_dim_repository(repository_id),
    active_flag BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT ck_access_scope_at_least_one CHECK (organization_id IS NOT NULL OR customer_id IS NOT NULL OR project_id IS NOT NULL OR repository_id IS NOT NULL)
);

-- =========================================================
-- 15. NOTIFICATION / OPERATION SUPPORT
-- =========================================================

CREATE TABLE IF NOT EXISTS tbl_notification_event (
    notification_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID REFERENCES tbl_dim_project(project_id),
    repository_id UUID REFERENCES tbl_dim_repository(repository_id),
    ticket_id UUID REFERENCES tbl_dim_ticket(ticket_id),
    target_role_id UUID REFERENCES tbl_dim_role(role_id),
    target_member_key UUID REFERENCES tbl_dim_member_pseudonym(member_key),
    notification_type VARCHAR(100) NOT NULL,
    severity severity_level NOT NULL DEFAULT 'MEDIUM',
    title TEXT NOT NULL,
    message TEXT,
    read_flag BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    read_at TIMESTAMPTZ,
    CONSTRAINT ck_notification_read_time CHECK (read_at IS NULL OR read_at >= created_at)
);

-- =========================================================
-- 16. OPTIONAL REPORT OUTPUT METADATA
-- =========================================================

CREATE TABLE IF NOT EXISTS tbl_fact_evidence_report (
    evidence_report_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES tbl_dim_ticket(ticket_id),
    project_id UUID REFERENCES tbl_dim_project(project_id),
    report_type VARCHAR(100) NOT NULL DEFAULT 'TICKET_EVIDENCE_REPORT',
    generated_by_member_key UUID REFERENCES tbl_dim_member_pseudonym(member_key),
    file_hash VARCHAR(128),
    artifact_snapshot_id UUID REFERENCES tbl_fact_artifact_snapshot(artifact_snapshot_id),
    metric_value_id UUID REFERENCES tbl_fact_metric_value(metric_value_id),
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    summary TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);


-- =========================================================
-- Login by account
-- =========================================================
CREATE TABLE IF NOT EXISTS tbl_auth_user_account (
    user_account_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_key UUID NOT NULL REFERENCES tbl_dim_member_pseudonym(member_key)
        ON DELETE CASCADE,
    username VARCHAR(100) NOT NULL UNIQUE,
    fullname VARCHAR(250) NOT NULL,
    email VARCHAR(255),
    email_hash VARCHAR(128) UNIQUE,
    password_hash TEXT NOT NULL,
    password_algo VARCHAR(50) NOT NULL DEFAULT 'bcrypt',
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);


-- =========================================================
-- 17. INDEXES
-- =========================================================

CREATE INDEX IF NOT EXISTS idx_customer_org ON tbl_dim_customer(organization_id);
CREATE INDEX IF NOT EXISTS idx_project_customer ON tbl_dim_project(customer_id);
CREATE INDEX IF NOT EXISTS idx_repo_project ON tbl_dim_repository(project_id);
CREATE INDEX IF NOT EXISTS idx_team_project ON tbl_dim_team(project_id);
CREATE INDEX IF NOT EXISTS idx_member_team_role ON tbl_dim_member_pseudonym(team_id, role_id);
CREATE INDEX IF NOT EXISTS idx_ticket_project_status ON tbl_dim_ticket(project_id, status);
CREATE INDEX IF NOT EXISTS idx_ticket_external_key ON tbl_dim_ticket(external_ticket_key);

CREATE INDEX IF NOT EXISTS idx_connector_project_repo ON tbl_source_connector(project_id, repository_id, connector_type);
CREATE INDEX IF NOT EXISTS idx_connector_run_connector_time ON tbl_connector_run(connector_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_ticket_phase_ticket ON tbl_fact_ticket_phase_status(ticket_id, phase_id);
CREATE INDEX IF NOT EXISTS idx_artifact_ticket ON tbl_fact_artifact_snapshot(ticket_id);
CREATE INDEX IF NOT EXISTS idx_artifact_repo_path ON tbl_fact_artifact_snapshot(repository_id, source_path);
CREATE INDEX IF NOT EXISTS idx_artifact_type ON tbl_fact_artifact_snapshot(artifact_type_id);
CREATE INDEX IF NOT EXISTS idx_artifact_collected_at ON tbl_fact_artifact_snapshot(collected_at DESC);
CREATE INDEX IF NOT EXISTS idx_artifact_required_missing_gin ON tbl_fact_artifact_snapshot USING GIN (required_fields_missing);
CREATE INDEX IF NOT EXISTS idx_artifact_parsed_ticket_type ON tbl_fact_artifact_parsed_section(ticket_id, section_type);
CREATE INDEX IF NOT EXISTS idx_ac_ticket ON tbl_fact_acceptance_criteria(ticket_id, ac_key);
CREATE INDEX IF NOT EXISTS idx_evidence_event_ticket_time ON tbl_fact_evidence_event(ticket_id, event_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_evidence_event_source ON tbl_fact_evidence_event(source_type, source_ref_id);

CREATE INDEX IF NOT EXISTS idx_trace_ticket ON tbl_fact_traceability_link(ticket_id);
CREATE INDEX IF NOT EXISTS idx_trace_source ON tbl_fact_traceability_link(source_type, source_id);
CREATE INDEX IF NOT EXISTS idx_trace_target ON tbl_fact_traceability_link(target_type, target_id);

CREATE INDEX IF NOT EXISTS idx_commit_ticket ON tbl_fact_commit(ticket_id);
CREATE INDEX IF NOT EXISTS idx_commit_repo_time ON tbl_fact_commit(repository_id, committed_at DESC);
CREATE INDEX IF NOT EXISTS idx_pr_ticket_status ON tbl_fact_pull_request(ticket_id, status);
CREATE INDEX IF NOT EXISTS idx_pr_repo_status ON tbl_fact_pull_request(repository_id, status);
CREATE INDEX IF NOT EXISTS idx_review_pr ON tbl_fact_review(pr_id);
CREATE INDEX IF NOT EXISTS idx_review_comment_pr ON tbl_fact_review_comment(pr_id);
CREATE INDEX IF NOT EXISTS idx_finding_ticket_status ON tbl_fact_finding(ticket_id, status);
CREATE INDEX IF NOT EXISTS idx_finding_severity_status ON tbl_fact_finding(severity, status);

CREATE INDEX IF NOT EXISTS idx_ci_ticket_status ON tbl_fact_ci_run(ticket_id, status);
CREATE INDEX IF NOT EXISTS idx_ci_pr_status ON tbl_fact_ci_run(pr_id, status);
CREATE INDEX IF NOT EXISTS idx_ci_run_time ON tbl_fact_ci_run(started_at DESC);
CREATE INDEX IF NOT EXISTS idx_ci_job_run ON tbl_fact_ci_job(ci_run_id, status);
CREATE INDEX IF NOT EXISTS idx_test_ticket ON tbl_fact_test_run(ticket_id);
CREATE INDEX IF NOT EXISTS idx_test_ci_run ON tbl_fact_test_run(ci_run_id);
CREATE INDEX IF NOT EXISTS idx_test_case_run ON tbl_fact_test_case(test_run_id, status);
CREATE INDEX IF NOT EXISTS idx_ac_test_ticket_status ON tbl_fact_ac_test_coverage(ticket_id, coverage_status);

CREATE INDEX IF NOT EXISTS idx_security_ticket ON tbl_fact_security_scan(ticket_id);
CREATE INDEX IF NOT EXISTS idx_security_scan_repo ON tbl_fact_security_scan(repository_id, scanner_type, status);
CREATE INDEX IF NOT EXISTS idx_security_finding_status ON tbl_fact_security_finding(ticket_id, severity, status);
CREATE INDEX IF NOT EXISTS idx_exception_ticket ON tbl_fact_exception(ticket_id);
CREATE INDEX IF NOT EXISTS idx_exception_expiry ON tbl_fact_exception(expiry_date, follow_up_status);
CREATE INDEX IF NOT EXISTS idx_ai_usage_ticket ON tbl_fact_ai_usage(ticket_id);
CREATE INDEX IF NOT EXISTS idx_ai_usage_template ON tbl_fact_ai_usage(prompt_template_id);

CREATE INDEX IF NOT EXISTS idx_decision_ticket ON tbl_fact_decision(ticket_id);
CREATE INDEX IF NOT EXISTS idx_risk_ticket_status ON tbl_fact_risk(ticket_id, status);
CREATE INDEX IF NOT EXISTS idx_rework_ticket_time ON tbl_fact_rework_event(ticket_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_quality_gate_ticket ON tbl_fact_quality_gate(ticket_id, gate_type, gate_status);

CREATE INDEX IF NOT EXISTS idx_metric_scope_period ON tbl_fact_metric_value(project_id, repository_id, ticket_id, period_start, period_end);
CREATE INDEX IF NOT EXISTS idx_metric_code_period ON tbl_fact_metric_value(metric_code, period_start, period_end);
CREATE INDEX IF NOT EXISTS idx_metric_breakdown_gin ON tbl_fact_metric_value USING GIN (breakdown);
CREATE INDEX IF NOT EXISTS idx_metric_lineage_value ON tbl_fact_metric_input_lineage(metric_value_id);
CREATE INDEX IF NOT EXISTS idx_evidence_score_ticket ON tbl_fact_evidence_quality_score(ticket_id, calculated_at DESC);
CREATE INDEX IF NOT EXISTS idx_data_lineage_target ON tbl_fact_data_lineage(target_table, target_record_id);
CREATE INDEX IF NOT EXISTS idx_data_quality_repo_time ON tbl_fact_data_quality(repository_id, checked_at DESC);

CREATE INDEX IF NOT EXISTS idx_access_log_time ON tbl_fact_access_log(occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_access_log_actor ON tbl_fact_access_log(actor_member_key, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_export_log_time ON tbl_fact_export_log(exported_at DESC);
CREATE INDEX IF NOT EXISTS idx_access_scope_member ON tbl_auth_member_access_scope(member_key, active_flag);
CREATE INDEX IF NOT EXISTS idx_notification_target ON tbl_notification_event(target_member_key, target_role_id, read_flag);
CREATE INDEX IF NOT EXISTS idx_notification_ticket ON tbl_notification_event(ticket_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_tbl_auth_user_account_member_key ON tbl_auth_user_account(member_key);
CREATE INDEX IF NOT EXISTS idx_tbl_auth_user_account_username ON tbl_auth_user_account(username);
CREATE INDEX IF NOT EXISTS idx_tbl_auth_user_account_is_active ON tbl_auth_user_account(is_active);
-- =========================================================
-- 18. UPDATED_AT TRIGGERS
-- =========================================================

DROP TRIGGER IF EXISTS trg_dim_organization_updated_at ON tbl_dim_organization;
CREATE TRIGGER trg_dim_organization_updated_at BEFORE UPDATE ON tbl_dim_organization FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_dim_customer_updated_at ON tbl_dim_customer;
CREATE TRIGGER trg_dim_customer_updated_at BEFORE UPDATE ON tbl_dim_customer FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_dim_project_updated_at ON tbl_dim_project;
CREATE TRIGGER trg_dim_project_updated_at BEFORE UPDATE ON tbl_dim_project FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_dim_repository_updated_at ON tbl_dim_repository;
CREATE TRIGGER trg_dim_repository_updated_at BEFORE UPDATE ON tbl_dim_repository FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_dim_team_updated_at ON tbl_dim_team;
CREATE TRIGGER trg_dim_team_updated_at BEFORE UPDATE ON tbl_dim_team FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_dim_role_updated_at ON tbl_dim_role;
CREATE TRIGGER trg_dim_role_updated_at BEFORE UPDATE ON tbl_dim_role FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_dim_member_pseudonym_updated_at ON tbl_dim_member_pseudonym;
CREATE TRIGGER trg_dim_member_pseudonym_updated_at BEFORE UPDATE ON tbl_dim_member_pseudonym FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_dim_ticket_updated_at ON tbl_dim_ticket;
CREATE TRIGGER trg_dim_ticket_updated_at BEFORE UPDATE ON tbl_dim_ticket FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_dim_metric_definition_updated_at ON tbl_dim_metric_definition;
CREATE TRIGGER trg_dim_metric_definition_updated_at BEFORE UPDATE ON tbl_dim_metric_definition FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_source_connector_updated_at ON tbl_source_connector;
CREATE TRIGGER trg_source_connector_updated_at BEFORE UPDATE ON tbl_source_connector FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =========================================================
-- 19. SEED MASTER DATA
-- =========================================================

INSERT INTO tbl_dim_role (role_name, description) VALUES
('PM', 'Project Manager'),
('DEV', 'Developer'),
('QA', 'Quality Assurance'),
('ADMIN', 'Administrator')
ON CONFLICT (role_name) DO NOTHING;

INSERT INTO tbl_dim_actor_type (actor_type_code, actor_type_name, description) VALUES
('HUMAN', 'Human', 'Human executor or reviewer'),
('AI_GENERATOR', 'AI Generator', 'AI supports generation'),
('AI_REVIEWER', 'AI Reviewer', 'AI supports review'),
('CI', 'CI/CD', 'CI/CD pipeline'),
('SECURITY_TOOL', 'Security Tool', 'SAST/SCA/Secret scanner'),
('DATA_PIPELINE', 'Data Pipeline', 'Connector/parser/batch job')
ON CONFLICT (actor_type_code) DO NOTHING;

INSERT INTO tbl_dim_phase (phase_code, phase_name, phase_order, description) VALUES
('0-A', 'Safety Pack / Evidence Pack Preparation', 0, 'Prepare safety guardrails and evidence base'),
('0-B', 'Common Rules / Templates Preparation', 1, 'Prepare rules, standards and templates'),
('1', 'Spec Pack', 2, 'Create specification, AC, scope and risk'),
('2', 'Working Files Initialization', 3, 'Initialize working files'),
('3', 'Implementation Plan', 4, 'Implementation plan, impact scope and rollback'),
('4', 'Review Checklist', 5, 'Review viewpoints'),
('5', 'Implementation and Review', 6, 'Implementation, AI review and human review'),
('6', 'Test Plan and Results', 7, 'Test plan, test results and CI run'),
('7', 'Blackbox Test', 8, 'Blackbox testcase and acceptance'),
('8', 'Report', 9, 'Final report'),
('9', 'Continuous Improvement', 10, 'Improve rules, templates and standards')
ON CONFLICT (phase_code) DO NOTHING;

INSERT INTO tbl_dim_artifact_type (phase_id, artifact_type_code, artifact_name, default_file_name, required_flag, description)
SELECT phase_id, 'SPEC_PACK', 'Spec Pack', 'spec-pack.md', TRUE, 'Specification, context, scope, AC and risk'
FROM tbl_dim_phase WHERE phase_code = '1'
ON CONFLICT (artifact_type_code) DO NOTHING;

INSERT INTO tbl_dim_artifact_type (phase_id, artifact_type_code, artifact_name, default_file_name, required_flag, description)
SELECT phase_id, 'SOURCES', 'Sources', 'sources.md', FALSE, 'Referenced sources'
FROM tbl_dim_phase WHERE phase_code = '1'
ON CONFLICT (artifact_type_code) DO NOTHING;

INSERT INTO tbl_dim_artifact_type (phase_id, artifact_type_code, artifact_name, default_file_name, required_flag, description)
SELECT phase_id, 'IMPL_PLAN', 'Implementation Plan', 'impl-plan.md', TRUE, 'Implementation plan, impact and rollback'
FROM tbl_dim_phase WHERE phase_code = '3'
ON CONFLICT (artifact_type_code) DO NOTHING;

INSERT INTO tbl_dim_artifact_type (phase_id, artifact_type_code, artifact_name, default_file_name, required_flag, description)
SELECT phase_id, 'REVIEW_CHECKLIST', 'Review Checklist', 'review-checklist.md', TRUE, 'Review viewpoints'
FROM tbl_dim_phase WHERE phase_code = '4'
ON CONFLICT (artifact_type_code) DO NOTHING;

INSERT INTO tbl_dim_artifact_type (phase_id, artifact_type_code, artifact_name, default_file_name, required_flag, description)
SELECT phase_id, 'SELF_REVIEW', 'Self Review', 'self-review.md', TRUE, 'Commands, results and concerns'
FROM tbl_dim_phase WHERE phase_code = '5'
ON CONFLICT (artifact_type_code) DO NOTHING;

INSERT INTO tbl_dim_artifact_type (phase_id, artifact_type_code, artifact_name, default_file_name, required_flag, description)
SELECT phase_id, 'TEST_PLAN', 'Test Plan', 'test-plan.md', TRUE, 'Test plan and AC mapping'
FROM tbl_dim_phase WHERE phase_code = '6'
ON CONFLICT (artifact_type_code) DO NOTHING;

INSERT INTO tbl_dim_artifact_type (phase_id, artifact_type_code, artifact_name, default_file_name, required_flag, description)
SELECT phase_id, 'TEST_RESULTS', 'Test Results', 'test-results.md', TRUE, 'Test result, CI run and failures'
FROM tbl_dim_phase WHERE phase_code = '6'
ON CONFLICT (artifact_type_code) DO NOTHING;

INSERT INTO tbl_dim_artifact_type (phase_id, artifact_type_code, artifact_name, default_file_name, required_flag, description)
SELECT phase_id, 'BLACKBOX_TESTCASES', 'Blackbox Testcases', 'blackbox-testcases.md', FALSE, 'Blackbox acceptance testcases'
FROM tbl_dim_phase WHERE phase_code = '7'
ON CONFLICT (artifact_type_code) DO NOTHING;

INSERT INTO tbl_dim_artifact_type (phase_id, artifact_type_code, artifact_name, default_file_name, required_flag, description)
SELECT phase_id, 'TEST_DATA', 'Test Data', 'test-data.md', FALSE, 'Test data and masking policy'
FROM tbl_dim_phase WHERE phase_code = '7'
ON CONFLICT (artifact_type_code) DO NOTHING;

INSERT INTO tbl_dim_artifact_type (phase_id, artifact_type_code, artifact_name, default_file_name, required_flag, description)
SELECT phase_id, 'REPORT', 'Report', 'report.md', TRUE, 'Final report, review, test, risk and remaining issues'
FROM tbl_dim_phase WHERE phase_code = '8'
ON CONFLICT (artifact_type_code) DO NOTHING;

INSERT INTO tbl_dim_artifact_type (phase_id, artifact_type_code, artifact_name, default_file_name, required_flag, description)
SELECT phase_id, 'CLAUDE_MD', 'Claude Instruction', '.claude/CLAUDE.md', TRUE, 'AI safety instruction'
FROM tbl_dim_phase WHERE phase_code = '0-A'
ON CONFLICT (artifact_type_code) DO NOTHING;

INSERT INTO tbl_dim_artifact_type (phase_id, artifact_type_code, artifact_name, default_file_name, required_flag, description)
SELECT phase_id, 'CLAUDE_SETTINGS', 'Claude Settings', '.claude/settings.json', TRUE, 'AI tool deny/ask/allow settings'
FROM tbl_dim_phase WHERE phase_code = '0-A'
ON CONFLICT (artifact_type_code) DO NOTHING;

INSERT INTO tbl_dim_artifact_type (phase_id, artifact_type_code, artifact_name, default_file_name, required_flag, description)
SELECT phase_id, 'CLAUDE_RULE', 'Claude Rule', '.claude/rules/*.md', TRUE, 'AI safety and project rules'
FROM tbl_dim_phase WHERE phase_code = '0-A'
ON CONFLICT (artifact_type_code) DO NOTHING;

INSERT INTO tbl_dim_finding_category (category_code, category_name, severity_default, description) VALUES
('SECURITY', 'Security', 'HIGH', 'Security issue'),
('TEST', 'Test', 'MEDIUM', 'Test gap or test failure'),
('PERFORMANCE', 'Performance', 'MEDIUM', 'Performance issue'),
('MAINTAINABILITY', 'Maintainability', 'LOW', 'Maintainability issue'),
('SPEC_GAP', 'Spec Gap', 'MEDIUM', 'Missing or ambiguous specification'),
('RISK', 'Risk', 'MEDIUM', 'Implementation or operation risk'),
('AI_FALSE_POSITIVE', 'AI False Positive', 'LOW', 'Non-valid AI finding'),
('CI_FAILURE', 'CI Failure', 'MEDIUM', 'CI failure category')
ON CONFLICT (category_code) DO NOTHING;

INSERT INTO tbl_dim_security_classification (classification_code, classification_name, handling_policy) VALUES
('PUBLIC', 'Public', 'Public information'),
('INTERNAL', 'Internal', 'Internal use only'),
('CONFIDENTIAL', 'Confidential', 'Project/customer permission required'),
('RESTRICTED', 'Restricted', 'Do not collect by default or requires special review'),
('SECRET', 'Secret', 'Never collect')
ON CONFLICT (classification_code) DO NOTHING;

INSERT INTO tbl_dim_metric_definition (metric_code, metric_name, formula, version, valid_from, description) VALUES
('EVIDENCE_QUALITY_SCORE', 'Evidence Quality Score', 'Weighted score from artifact completeness, AC, risk, rollback, CI link, test and report', 'v0', CURRENT_DATE, 'Evidence quality score from 0 to 100'),
('AC_TEST_COVERAGE', 'AC-Test Coverage', 'Covered AC count / total AC count * 100', 'v0', CURRENT_DATE, 'Rate of AC covered by tests'),
('TRACEABILITY_COMPLETENESS', 'Traceability Completeness', 'Completed traceability links / required traceability links * 100', 'v0', CURRENT_DATE, 'Completeness of Ticket-Spec-PR-CI-Test-Report links'),
('FIRST_CI_PASS_RATE', 'First CI Pass Rate', 'First successful CI runs / total PR CI runs * 100', 'v0', CURRENT_DATE, 'Rate of first CI success'),
('EXCEPTION_RATE', 'Exception Rate', 'Exception count / ticket count', 'v0', CURRENT_DATE, 'Rate of operational exceptions'),
('SAFETY_PACK_COVERAGE', 'Safety Pack Coverage', 'Existing safety pack items / required safety pack items * 100', 'v0', CURRENT_DATE, 'Readiness of safety pack'),
('AI_VALID_FINDING_RATE', 'AI Valid Finding Rate', 'Accepted AI findings / total AI findings * 100', 'v0', CURRENT_DATE, 'Effectiveness of AI review findings'),
('REWORK_COUNT', 'Rework Count', 'Count of rework events by ticket/project/period', 'v0', CURRENT_DATE, 'Rework count')
ON CONFLICT (metric_code, version) DO NOTHING;

INSERT INTO tbl_auth_permission (permission_code, description) VALUES
('PROJECT_READ', 'Read project scoped data'),
('PROJECT_ADMIN', 'Administer project settings'),
('REPOSITORY_READ', 'Read repository scoped metadata'),
('TICKET_READ', 'Read ticket evidence and metrics'),
('METRIC_READ', 'Read metric values and definitions'),
('EXPORT_CREATE', 'Create exports'),
('AUDIT_READ', 'Read audit logs'),
('CONNECTOR_RUN', 'Run connectors'),
('SECURITY_READ', 'Read security scan summaries'),
('RBAC_ADMIN', 'Manage roles and access scopes')
ON CONFLICT (permission_code) DO NOTHING;

-- =========================================================
-- Add Member
-- =========================================================

INSERT INTO tbl_dim_member_pseudonym (
    role_id,
    team_id,
    pseudonym,
    external_user_hash,
    active_from
)
SELECT
    r.role_id,
    NULL,
    v.username,
    encode(digest(v.email, 'sha256'), 'hex'),
    CURRENT_DATE
FROM tbl_dim_role r
CROSS JOIN (
    VALUES
        ('nk_trung', 'Nguyễn Khắc Trung', 'nk_trung@brycen.com.vn'),
        ('pd_khoa', 'Phạm Đăng Khoa', 'pd_khoa@brycen.com.vn'),
        ('nvt_dung', 'Nguyễn Võ Tiến Dũng', 'nvt_dung@brycen.com.vn'),
        ('lx_loc', 'Lê Xuân Lộc', 'lx_loc@brycen.com.vn')
) AS v(username, fullname, email)
WHERE r.role_name = 'Admin'
ON CONFLICT (pseudonym) DO NOTHING;

INSERT INTO tbl_auth_user_account (
    member_key,
    username,
    fullname,
    email,
    email_hash,
    password_hash,
    password_algo,
    is_active,
    created_by,
    updated_by
)
SELECT
    m.member_key,
    v.username,
    v.fullname,
    v.email,
    encode(digest(v.email, 'sha256'), 'hex'),
    crypt('Admin@123456', gen_salt('bf')),
    'bcrypt',
    TRUE,
    'SYSTEM',
    'SYSTEM'
FROM (
    VALUES
        ('nk_trung', 'Nguyễn Khắc Trung', 'nk_trung@brycen.com.vn'),
        ('pd_khoa', 'Phạm Đăng Khoa', 'pd_khoa@brycen.com.vn'),
        ('nvt_dung', 'Nguyễn Võ Tiến Dũng', 'nvt_dung@brycen.com.vn'),
        ('lx_loc', 'Lê Xuân Lộc', 'lx_loc@brycen.com.vn')
) AS v(username, fullname, email)
JOIN tbl_dim_member_pseudonym m
    ON m.pseudonym = v.username
ON CONFLICT (username) DO NOTHING;

-- =========================================================
-- Add Organization
-- =========================================================
INSERT INTO tbl_dim_organization (
    name_masked,
    status,
    created_by,
    updated_by
)
VALUES (
    'Brycen Vietnam',
    'ACTIVE',
    'SYSTEM',
    'SYSTEM'
)
ON CONFLICT DO NOTHING;

-- =========================================================
-- Add Customer off Organization
-- =========================================================
INSERT INTO tbl_dim_customer (
    organization_id,
    customer_alias,
    classification,
    status,
    created_by,
    updated_by
)
SELECT
    o.organization_id,
    'Customer Demo',
    'INTERNAL',
    'ACTIVE',
    'SYSTEM',
    'SYSTEM'
FROM tbl_dim_organization o
WHERE o.name_masked = 'Brycen Vietnam'
ON CONFLICT DO NOTHING;

-- =========================================================
-- Add Project belonging to Customer
-- =========================================================
INSERT INTO tbl_dim_project (
    customer_id,
    project_alias,
    project_type,
    risk_level,
    status,
    created_by,
    updated_by
)
SELECT
    c.customer_id,
    'SDD Evidence Platform',
    'Internal Platform',
    'MEDIUM',
    'ACTIVE',
    'SYSTEM',
    'SYSTEM'
FROM tbl_dim_customer c
JOIN tbl_dim_organization o
    ON o.organization_id = c.organization_id
WHERE o.name_masked = 'Brycen Vietnam'
  AND c.customer_alias = 'Customer Demo'
ON CONFLICT DO NOTHING;

-- =========================================================
-- Add 2 Repository belonging to Project
-- =========================================================
INSERT INTO tbl_dim_repository (
    project_id,
    repo_name_masked,
    host_type,
    default_branch,
    repo_url_hash,
    status,
    created_by,
    updated_by
)
SELECT
    p.project_id,
    v.repo_name_masked,
    v.host_type,
    v.default_branch,
    encode(digest(v.repo_url, 'sha256'), 'hex'),
    'ACTIVE',
    'SYSTEM',
    'SYSTEM'
FROM tbl_dim_project p
JOIN tbl_dim_customer c
    ON c.customer_id = p.customer_id
JOIN tbl_dim_organization o
    ON o.organization_id = c.organization_id
CROSS JOIN (
    VALUES
        (
            '30061989/EDCAP_FE',
            'github',
            'main',
            'https://github.com/30061989/EDCAP_FE'
        ),
        (
            '30061989/EDCAP_BE',
            'github',
            'main',
            'https://github.com/30061989/EDCAP_BE'
        )
) AS v(repo_name_masked, host_type, default_branch, repo_url)
WHERE o.name_masked = 'Brycen Vietnam'
  AND c.customer_alias = 'Customer Demo'
  AND p.project_alias = 'SDD Evidence Platform'
ON CONFLICT DO NOTHING;

-- Required field rules v0 for main artifacts.
INSERT INTO tbl_artifact_required_field_rule (artifact_type_id, schema_version, field_code, field_name, section_type, required_flag, score_weight)
SELECT artifact_type_id, 'sdd-artifact-v1', 'AC', 'Acceptance Criteria', 'AC', TRUE, 15
FROM tbl_dim_artifact_type WHERE artifact_type_code = 'SPEC_PACK'
ON CONFLICT (artifact_type_id, schema_version, field_code) DO NOTHING;

INSERT INTO tbl_artifact_required_field_rule (artifact_type_id, schema_version, field_code, field_name, section_type, required_flag, score_weight)
SELECT artifact_type_id, 'sdd-artifact-v1', 'SCOPE', 'Scope', 'SCOPE', TRUE, 3
FROM tbl_dim_artifact_type WHERE artifact_type_code = 'SPEC_PACK'
ON CONFLICT (artifact_type_id, schema_version, field_code) DO NOTHING;

INSERT INTO tbl_artifact_required_field_rule (artifact_type_id, schema_version, field_code, field_name, section_type, required_flag, score_weight)
SELECT artifact_type_id, 'sdd-artifact-v1', 'NON_SCOPE', 'Non-scope', 'NON_SCOPE', TRUE, 2
FROM tbl_dim_artifact_type WHERE artifact_type_code = 'SPEC_PACK'
ON CONFLICT (artifact_type_id, schema_version, field_code) DO NOTHING;

INSERT INTO tbl_artifact_required_field_rule (artifact_type_id, schema_version, field_code, field_name, section_type, required_flag, score_weight)
SELECT artifact_type_id, 'sdd-artifact-v1', 'RISK', 'Risk', 'RISK', TRUE, 3
FROM tbl_dim_artifact_type WHERE artifact_type_code = 'SPEC_PACK'
ON CONFLICT (artifact_type_id, schema_version, field_code) DO NOTHING;

INSERT INTO tbl_artifact_required_field_rule (artifact_type_id, schema_version, field_code, field_name, section_type, required_flag, score_weight)
SELECT artifact_type_id, 'sdd-artifact-v1', 'OPEN_ISSUES', 'Open Issues', 'OPEN_ISSUE', TRUE, 2
FROM tbl_dim_artifact_type WHERE artifact_type_code = 'SPEC_PACK'
ON CONFLICT (artifact_type_id, schema_version, field_code) DO NOTHING;

INSERT INTO tbl_artifact_required_field_rule (artifact_type_id, schema_version, field_code, field_name, section_type, required_flag, score_weight)
SELECT artifact_type_id, 'sdd-artifact-v1', 'IMPACT_SCOPE', 'Impact Scope', 'IMPACT_SCOPE', TRUE, 4
FROM tbl_dim_artifact_type WHERE artifact_type_code = 'IMPL_PLAN'
ON CONFLICT (artifact_type_id, schema_version, field_code) DO NOTHING;

INSERT INTO tbl_artifact_required_field_rule (artifact_type_id, schema_version, field_code, field_name, section_type, required_flag, score_weight)
SELECT artifact_type_id, 'sdd-artifact-v1', 'ROLLBACK', 'Rollback', 'ROLLBACK', TRUE, 3
FROM tbl_dim_artifact_type WHERE artifact_type_code = 'IMPL_PLAN'
ON CONFLICT (artifact_type_id, schema_version, field_code) DO NOTHING;

INSERT INTO tbl_artifact_required_field_rule (artifact_type_id, schema_version, field_code, field_name, section_type, required_flag, score_weight)
SELECT artifact_type_id, 'sdd-artifact-v1', 'AC_MAPPING', 'AC Mapping', 'AC_MAPPING', TRUE, 3
FROM tbl_dim_artifact_type WHERE artifact_type_code = 'IMPL_PLAN'
ON CONFLICT (artifact_type_id, schema_version, field_code) DO NOTHING;

INSERT INTO tbl_artifact_required_field_rule (artifact_type_id, schema_version, field_code, field_name, section_type, required_flag, score_weight)
SELECT artifact_type_id, 'sdd-artifact-v1', 'SECURITY_VIEWPOINT', 'Security Viewpoint', 'SECURITY', TRUE, 5
FROM tbl_dim_artifact_type WHERE artifact_type_code = 'REVIEW_CHECKLIST'
ON CONFLICT (artifact_type_id, schema_version, field_code) DO NOTHING;

INSERT INTO tbl_artifact_required_field_rule (artifact_type_id, schema_version, field_code, field_name, section_type, required_flag, score_weight)
SELECT artifact_type_id, 'sdd-artifact-v1', 'TEST_VIEWPOINT', 'Test Viewpoint', 'TEST', TRUE, 5
FROM tbl_dim_artifact_type WHERE artifact_type_code = 'REVIEW_CHECKLIST'
ON CONFLICT (artifact_type_id, schema_version, field_code) DO NOTHING;

INSERT INTO tbl_artifact_required_field_rule (artifact_type_id, schema_version, field_code, field_name, section_type, required_flag, score_weight)
SELECT artifact_type_id, 'sdd-artifact-v1', 'COMMAND_RESULT', 'Command and Result', 'COMMAND', TRUE, 10
FROM tbl_dim_artifact_type WHERE artifact_type_code = 'SELF_REVIEW'
ON CONFLICT (artifact_type_id, schema_version, field_code) DO NOTHING;

INSERT INTO tbl_artifact_required_field_rule (artifact_type_id, schema_version, field_code, field_name, section_type, required_flag, score_weight)
SELECT artifact_type_id, 'sdd-artifact-v1', 'CONCERN', 'Concern', 'CONCERN', TRUE, 5
FROM tbl_dim_artifact_type WHERE artifact_type_code = 'SELF_REVIEW'
ON CONFLICT (artifact_type_id, schema_version, field_code) DO NOTHING;

INSERT INTO tbl_artifact_required_field_rule (artifact_type_id, schema_version, field_code, field_name, section_type, required_flag, score_weight)
SELECT artifact_type_id, 'sdd-artifact-v1', 'AC_TEST_MAPPING', 'AC-Test Mapping', 'AC_TEST_MAPPING', TRUE, 10
FROM tbl_dim_artifact_type WHERE artifact_type_code = 'TEST_PLAN'
ON CONFLICT (artifact_type_id, schema_version, field_code) DO NOTHING;

INSERT INTO tbl_artifact_required_field_rule (artifact_type_id, schema_version, field_code, field_name, section_type, required_flag, score_weight)
SELECT artifact_type_id, 'sdd-artifact-v1', 'TEST_RESULT', 'Test Result', 'TEST_RESULT', TRUE, 7
FROM tbl_dim_artifact_type WHERE artifact_type_code = 'TEST_RESULTS'
ON CONFLICT (artifact_type_id, schema_version, field_code) DO NOTHING;

INSERT INTO tbl_artifact_required_field_rule (artifact_type_id, schema_version, field_code, field_name, section_type, required_flag, score_weight)
SELECT artifact_type_id, 'sdd-artifact-v1', 'CI_RUN_ID', 'CI Run ID or Link', 'CI_LINK', TRUE, 3
FROM tbl_dim_artifact_type WHERE artifact_type_code = 'TEST_RESULTS'
ON CONFLICT (artifact_type_id, schema_version, field_code) DO NOTHING;

INSERT INTO tbl_artifact_required_field_rule (artifact_type_id, schema_version, field_code, field_name, section_type, required_flag, score_weight)
SELECT artifact_type_id, 'sdd-artifact-v1', 'REPORT_SUMMARY', 'Report Summary', 'REPORT_SUMMARY', TRUE, 2
FROM tbl_dim_artifact_type WHERE artifact_type_code = 'REPORT'
ON CONFLICT (artifact_type_id, schema_version, field_code) DO NOTHING;

INSERT INTO tbl_artifact_required_field_rule (artifact_type_id, schema_version, field_code, field_name, section_type, required_flag, score_weight)
SELECT artifact_type_id, 'sdd-artifact-v1', 'REVIEW_RESULT', 'Review Result', 'REVIEW_RESULT', TRUE, 2
FROM tbl_dim_artifact_type WHERE artifact_type_code = 'REPORT'
ON CONFLICT (artifact_type_id, schema_version, field_code) DO NOTHING;

INSERT INTO tbl_artifact_required_field_rule (artifact_type_id, schema_version, field_code, field_name, section_type, required_flag, score_weight)
SELECT artifact_type_id, 'sdd-artifact-v1', 'TEST_RESULT', 'Test Result', 'TEST_RESULT', TRUE, 2
FROM tbl_dim_artifact_type WHERE artifact_type_code = 'REPORT'
ON CONFLICT (artifact_type_id, schema_version, field_code) DO NOTHING;

INSERT INTO tbl_artifact_required_field_rule (artifact_type_id, schema_version, field_code, field_name, section_type, required_flag, score_weight)
SELECT artifact_type_id, 'sdd-artifact-v1', 'RISK_REMAINING_ISSUES', 'Risk and Remaining Issues', 'RISK', TRUE, 4
FROM tbl_dim_artifact_type WHERE artifact_type_code = 'REPORT'
ON CONFLICT (artifact_type_id, schema_version, field_code) DO NOTHING;

-- Ensure retention policy table exists before seed data.
-- This protects partial execution in SQL clients.
CREATE TABLE IF NOT EXISTS tbl_data_retention_policy (
    retention_policy_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    data_category VARCHAR(100) NOT NULL,
    classification_id UUID REFERENCES tbl_dim_security_classification(classification_id),
    retention_days INT NOT NULL,
    deletion_method VARCHAR(100) NOT NULL DEFAULT 'SOFT_DELETE',
    description TEXT,
    valid_from DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT ck_retention_days CHECK (retention_days > 0),
    CONSTRAINT ck_retention_period CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT uq_retention_policy UNIQUE (data_category, classification_id, valid_from)
);

-- Retention policy seeds.
INSERT INTO tbl_data_retention_policy (data_category, classification_id, retention_days, deletion_method, description)
SELECT 'artifact_metadata', classification_id, 3650, 'SOFT_DELETE', 'Artifact metadata can be retained long-term according to contract'
FROM tbl_dim_security_classification WHERE classification_code = 'INTERNAL'
ON CONFLICT DO NOTHING;

INSERT INTO tbl_data_retention_policy (data_category, classification_id, retention_days, deletion_method, description)
SELECT 'metric_value', classification_id, 3650, 'SOFT_DELETE', 'Metric values can be retained long-term if aggregated or pseudonymized'
FROM tbl_dim_security_classification WHERE classification_code = 'INTERNAL'
ON CONFLICT DO NOTHING;

INSERT INTO tbl_data_retention_policy (data_category, classification_id, retention_days, deletion_method, description)
SELECT 'ai_usage_metadata', classification_id, 365, 'SOFT_DELETE', 'AI usage metadata without raw prompt/chat'
FROM tbl_dim_security_classification WHERE classification_code = 'INTERNAL'
ON CONFLICT DO NOTHING;

INSERT INTO tbl_data_retention_policy (data_category, classification_id, retention_days, deletion_method, description)
SELECT 'access_log', classification_id, 1095, 'APPEND_ONLY_ARCHIVE', 'Access log for audit, default three years'
FROM tbl_dim_security_classification WHERE classification_code = 'CONFIDENTIAL'
ON CONFLICT DO NOTHING;


-- =========================================================
-- END OF FILE
-- =========================================================
