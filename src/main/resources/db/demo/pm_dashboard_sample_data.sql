-- PM Dashboard sample data
-- Run this only on a dev database if you want the dashboard to show realistic rows.
-- Assumes the base schema, roles, phases, artifact types and demo repositories already exist.

INSERT INTO tbl_dim_ticket (
    ticket_id,
    project_id,
    external_ticket_key,
    ticket_type,
    priority,
    status,
    title,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    '11111111-1111-4111-8111-111111111111'::uuid,
    p.project_id,
    'PM-001',
    'TASK',
    'HIGH',
    'OPEN',
    'Checkout page missing acceptance criteria',
    now() - interval '12 day',
    'pd_khoa',
    now() - interval '2 day',
    'pd_khoa'
FROM tbl_dim_project p
WHERE p.project_alias = 'SDD Evidence Platform'
ON CONFLICT (ticket_id) DO NOTHING;

INSERT INTO tbl_dim_ticket (
    ticket_id,
    project_id,
    external_ticket_key,
    ticket_type,
    priority,
    status,
    title,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    '22222222-2222-4222-8222-222222222222'::uuid,
    p.project_id,
    'PM-002',
    'BUG',
    'MEDIUM',
    'IN_REVIEW',
    'API response shape mismatch in summary panel',
    now() - interval '8 day',
    'nk_trung',
    now() - interval '1 day',
    'nk_trung'
FROM tbl_dim_project p
WHERE p.project_alias = 'SDD Evidence Platform'
ON CONFLICT (ticket_id) DO NOTHING;

INSERT INTO tbl_dim_ticket (
    ticket_id,
    project_id,
    external_ticket_key,
    ticket_type,
    priority,
    status,
    title,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    '33333333-3333-4333-8333-333333333333'::uuid,
    p.project_id,
    'PM-003',
    'TASK',
    'LOW',
    'OPEN',
    'Test plan is complete but CI is failing',
    now() - interval '21 day',
    'nvt_dung',
    now() - interval '4 day',
    'nvt_dung'
FROM tbl_dim_project p
WHERE p.project_alias = 'SDD Evidence Platform'
ON CONFLICT (ticket_id) DO NOTHING;

INSERT INTO tbl_dim_ticket (
    ticket_id,
    project_id,
    external_ticket_key,
    ticket_type,
    priority,
    status,
    title,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    '44444444-4444-4444-8444-444444444444'::uuid,
    p.project_id,
    'PM-004',
    'TASK',
    'MEDIUM',
    'DONE',
    'Release report is ready for review',
    now() - interval '5 day',
    'lx_loc',
    now() - interval '1 day',
    'lx_loc'
FROM tbl_dim_project p
WHERE p.project_alias = 'SDD Evidence Platform'
ON CONFLICT (ticket_id) DO NOTHING;

INSERT INTO tbl_fact_ticket_phase_status (
    ticket_phase_status_id,
    ticket_id,
    phase_id,
    status,
    started_at,
    completed_at,
    dwell_time_minutes,
    blocked_flag,
    block_reason,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    '11111111-1111-4111-8111-111111111112'::uuid,
    t.ticket_id,
    ph.phase_id,
    'IN_PROGRESS',
    now() - interval '11 day',
    NULL,
    1620,
    TRUE,
    'Spec pack missing AC section',
    now() - interval '2 day',
    'pd_khoa',
    now() - interval '2 day',
    'pd_khoa'
FROM tbl_dim_ticket t
JOIN tbl_dim_phase ph ON ph.phase_code = '1'
WHERE t.external_ticket_key = 'PM-001'
ON CONFLICT (ticket_id) DO NOTHING;

INSERT INTO tbl_fact_ticket_phase_status (
    ticket_phase_status_id,
    ticket_id,
    phase_id,
    status,
    started_at,
    completed_at,
    dwell_time_minutes,
    blocked_flag,
    block_reason,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    '22222222-2222-4222-8222-222222222223'::uuid,
    t.ticket_id,
    ph.phase_id,
    'IN_REVIEW',
    now() - interval '6 day',
    NULL,
    720,
    FALSE,
    NULL,
    now() - interval '1 day',
    'nk_trung',
    now() - interval '1 day',
    'nk_trung'
FROM tbl_dim_ticket t
JOIN tbl_dim_phase ph ON ph.phase_code = '3'
WHERE t.external_ticket_key = 'PM-002'
ON CONFLICT (ticket_id) DO NOTHING;

INSERT INTO tbl_fact_ticket_phase_status (
    ticket_phase_status_id,
    ticket_id,
    phase_id,
    status,
    started_at,
    completed_at,
    dwell_time_minutes,
    blocked_flag,
    block_reason,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    '33333333-3333-4333-8333-333333333334'::uuid,
    t.ticket_id,
    ph.phase_id,
    'FAILED',
    now() - interval '19 day',
    NULL,
    2640,
    TRUE,
    'CI jobs are red',
    now() - interval '4 day',
    'nvt_dung',
    now() - interval '4 day',
    'nvt_dung'
FROM tbl_dim_ticket t
JOIN tbl_dim_phase ph ON ph.phase_code = '6'
WHERE t.external_ticket_key = 'PM-003'
ON CONFLICT (ticket_id) DO NOTHING;

INSERT INTO tbl_fact_ticket_phase_status (
    ticket_phase_status_id,
    ticket_id,
    phase_id,
    status,
    started_at,
    completed_at,
    dwell_time_minutes,
    blocked_flag,
    block_reason,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    '44444444-4444-4444-8444-444444444445'::uuid,
    t.ticket_id,
    ph.phase_id,
    'DONE',
    now() - interval '4 day',
    now() - interval '1 day',
    720,
    FALSE,
    NULL,
    now() - interval '1 day',
    'lx_loc',
    now() - interval '1 day',
    'lx_loc'
FROM tbl_dim_ticket t
JOIN tbl_dim_phase ph ON ph.phase_code = '8'
WHERE t.external_ticket_key = 'PM-004'
ON CONFLICT (ticket_id) DO NOTHING;

INSERT INTO tbl_fact_artifact_snapshot (
    artifact_snapshot_id,
    ticket_id,
    repository_id,
    artifact_type_id,
    phase_id,
    source_path,
    source_url_hash,
    exists_flag,
    content_hash,
    schema_version,
    schema_valid,
    template_empty_flag,
    required_fields_missing,
    parsed_summary,
    privacy_classification,
    contains_customer_data,
    contains_personal_data,
    contains_secret_detected,
    source_created_at,
    source_updated_at,
    collected_at,
    parser_version,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1'::uuid,
    t.ticket_id,
    r.repository_id,
    at.artifact_type_id,
    ph.phase_id,
    'docs/spec-pack.md',
    'spec-pack-001',
    FALSE,
    'hash-spec-pack-001',
    1,
    TRUE,
    FALSE,
    '[]'::jsonb,
    '{}'::jsonb,
    'INTERNAL',
    FALSE,
    FALSE,
    FALSE,
    now() - interval '12 day',
    now() - interval '2 day',
    now() - interval '2 day',
    'demo-parser-v1',
    now() - interval '2 day',
    'pd_khoa',
    now() - interval '2 day',
    'pd_khoa'
FROM tbl_dim_ticket t
JOIN tbl_dim_repository r ON r.repo_name_masked = '30061989/EDCAP_FE'
JOIN tbl_dim_phase ph ON ph.phase_code = '1'
JOIN tbl_dim_artifact_type at ON at.artifact_type_code = 'SPEC_PACK'
WHERE t.external_ticket_key = 'PM-001'
ON CONFLICT (artifact_snapshot_id) DO NOTHING;

INSERT INTO tbl_fact_artifact_snapshot (
    artifact_snapshot_id,
    ticket_id,
    repository_id,
    artifact_type_id,
    phase_id,
    source_path,
    source_url_hash,
    exists_flag,
    content_hash,
    schema_version,
    schema_valid,
    template_empty_flag,
    required_fields_missing,
    parsed_summary,
    privacy_classification,
    contains_customer_data,
    contains_personal_data,
    contains_secret_detected,
    source_created_at,
    source_updated_at,
    collected_at,
    parser_version,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2'::uuid,
    t.ticket_id,
    r.repository_id,
    at.artifact_type_id,
    ph.phase_id,
    'docs/sources.md',
    'sources-001',
    TRUE,
    'hash-sources-001',
    1,
    TRUE,
    FALSE,
    '[]'::jsonb,
    '{}'::jsonb,
    'INTERNAL',
    FALSE,
    FALSE,
    FALSE,
    now() - interval '12 day',
    now() - interval '2 day',
    now() - interval '2 day',
    'demo-parser-v1',
    now() - interval '2 day',
    'pd_khoa',
    now() - interval '2 day',
    'pd_khoa'
FROM tbl_dim_ticket t
JOIN tbl_dim_repository r ON r.repo_name_masked = '30061989/EDCAP_FE'
JOIN tbl_dim_phase ph ON ph.phase_code = '1'
JOIN tbl_dim_artifact_type at ON at.artifact_type_code = 'SOURCES'
WHERE t.external_ticket_key = 'PM-001'
ON CONFLICT (artifact_snapshot_id) DO NOTHING;

INSERT INTO tbl_fact_artifact_snapshot (
    artifact_snapshot_id,
    ticket_id,
    repository_id,
    artifact_type_id,
    phase_id,
    source_path,
    source_url_hash,
    exists_flag,
    content_hash,
    schema_version,
    schema_valid,
    template_empty_flag,
    required_fields_missing,
    parsed_summary,
    privacy_classification,
    contains_customer_data,
    contains_personal_data,
    contains_secret_detected,
    source_created_at,
    source_updated_at,
    collected_at,
    parser_version,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1'::uuid,
    t.ticket_id,
    r.repository_id,
    at.artifact_type_id,
    ph.phase_id,
    'docs/impl-plan.md',
    'impl-plan-002',
    TRUE,
    'hash-impl-plan-002',
    1,
    TRUE,
    FALSE,
    '[]'::jsonb,
    '{}'::jsonb,
    'INTERNAL',
    FALSE,
    FALSE,
    FALSE,
    now() - interval '8 day',
    now() - interval '1 day',
    now() - interval '1 day',
    'demo-parser-v1',
    now() - interval '1 day',
    'nk_trung',
    now() - interval '1 day',
    'nk_trung'
FROM tbl_dim_ticket t
JOIN tbl_dim_repository r ON r.repo_name_masked = '30061989/EDCAP_FE'
JOIN tbl_dim_phase ph ON ph.phase_code = '3'
JOIN tbl_dim_artifact_type at ON at.artifact_type_code = 'IMPL_PLAN'
WHERE t.external_ticket_key = 'PM-002'
ON CONFLICT (artifact_snapshot_id) DO NOTHING;

INSERT INTO tbl_fact_artifact_snapshot (
    artifact_snapshot_id,
    ticket_id,
    repository_id,
    artifact_type_id,
    phase_id,
    source_path,
    source_url_hash,
    exists_flag,
    content_hash,
    schema_version,
    schema_valid,
    template_empty_flag,
    required_fields_missing,
    parsed_summary,
    privacy_classification,
    contains_customer_data,
    contains_personal_data,
    contains_secret_detected,
    source_created_at,
    source_updated_at,
    collected_at,
    parser_version,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    'cccccccc-cccc-4ccc-8ccc-ccccccccccc1'::uuid,
    t.ticket_id,
    r.repository_id,
    at.artifact_type_id,
    ph.phase_id,
    'docs/test-plan.md',
    'test-plan-003',
    TRUE,
    'hash-test-plan-003',
    1,
    TRUE,
    FALSE,
    '[]'::jsonb,
    '{}'::jsonb,
    'INTERNAL',
    FALSE,
    FALSE,
    FALSE,
    now() - interval '21 day',
    now() - interval '4 day',
    now() - interval '4 day',
    'demo-parser-v1',
    now() - interval '4 day',
    'nvt_dung',
    now() - interval '4 day',
    'nvt_dung'
FROM tbl_dim_ticket t
JOIN tbl_dim_repository r ON r.repo_name_masked = '30061989/EDCAP_BE'
JOIN tbl_dim_phase ph ON ph.phase_code = '6'
JOIN tbl_dim_artifact_type at ON at.artifact_type_code = 'TEST_PLAN'
WHERE t.external_ticket_key = 'PM-003'
ON CONFLICT (artifact_snapshot_id) DO NOTHING;

INSERT INTO tbl_fact_artifact_snapshot (
    artifact_snapshot_id,
    ticket_id,
    repository_id,
    artifact_type_id,
    phase_id,
    source_path,
    source_url_hash,
    exists_flag,
    content_hash,
    schema_version,
    schema_valid,
    template_empty_flag,
    required_fields_missing,
    parsed_summary,
    privacy_classification,
    contains_customer_data,
    contains_personal_data,
    contains_secret_detected,
    source_created_at,
    source_updated_at,
    collected_at,
    parser_version,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    'cccccccc-cccc-4ccc-8ccc-ccccccccccc2'::uuid,
    t.ticket_id,
    r.repository_id,
    at.artifact_type_id,
    ph.phase_id,
    'docs/test-results.md',
    'test-results-003',
    FALSE,
    'hash-test-results-003',
    1,
    TRUE,
    FALSE,
    '[]'::jsonb,
    '{}'::jsonb,
    'INTERNAL',
    FALSE,
    FALSE,
    FALSE,
    now() - interval '21 day',
    now() - interval '4 day',
    now() - interval '4 day',
    'demo-parser-v1',
    now() - interval '4 day',
    'nvt_dung',
    now() - interval '4 day',
    'nvt_dung'
FROM tbl_dim_ticket t
JOIN tbl_dim_repository r ON r.repo_name_masked = '30061989/EDCAP_BE'
JOIN tbl_dim_phase ph ON ph.phase_code = '6'
JOIN tbl_dim_artifact_type at ON at.artifact_type_code = 'TEST_RESULTS'
WHERE t.external_ticket_key = 'PM-003'
ON CONFLICT (artifact_snapshot_id) DO NOTHING;

INSERT INTO tbl_fact_artifact_snapshot (
    artifact_snapshot_id,
    ticket_id,
    repository_id,
    artifact_type_id,
    phase_id,
    source_path,
    source_url_hash,
    exists_flag,
    content_hash,
    schema_version,
    schema_valid,
    template_empty_flag,
    required_fields_missing,
    parsed_summary,
    privacy_classification,
    contains_customer_data,
    contains_personal_data,
    contains_secret_detected,
    source_created_at,
    source_updated_at,
    collected_at,
    parser_version,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    'dddddddd-dddd-4ddd-8ddd-ddddddddddd1'::uuid,
    t.ticket_id,
    r.repository_id,
    at.artifact_type_id,
    ph.phase_id,
    'docs/report.md',
    'report-004',
    TRUE,
    'hash-report-004',
    1,
    TRUE,
    FALSE,
    '[]'::jsonb,
    '{}'::jsonb,
    'INTERNAL',
    FALSE,
    FALSE,
    FALSE,
    now() - interval '5 day',
    now() - interval '1 day',
    now() - interval '1 day',
    'demo-parser-v1',
    now() - interval '1 day',
    'lx_loc',
    now() - interval '1 day',
    'lx_loc'
FROM tbl_dim_ticket t
JOIN tbl_dim_repository r ON r.repo_name_masked = '30061989/EDCAP_BE'
JOIN tbl_dim_phase ph ON ph.phase_code = '8'
JOIN tbl_dim_artifact_type at ON at.artifact_type_code = 'REPORT'
WHERE t.external_ticket_key = 'PM-004'
ON CONFLICT (artifact_snapshot_id) DO NOTHING;

INSERT INTO tbl_fact_evidence_quality_score (
    evidence_quality_score_id,
    ticket_id,
    metric_value_id,
    score,
    score_band,
    score_rule_version,
    spec_score,
    plan_score,
    review_score,
    self_review_score,
    test_score,
    ci_score,
    blackbox_score,
    report_score,
    missing_items,
    calculated_at,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee1'::uuid,
    t.ticket_id,
    NULL,
    38.50,
    'RISKY',
    'pm-dashboard-demo-v1',
    10.00,
    5.00,
    8.00,
    2.00,
    3.00,
    4.00,
    0.00,
    6.00,
    '[]'::jsonb,
    now() - interval '2 day',
    now() - interval '2 day',
    'pd_khoa',
    now() - interval '2 day',
    'pd_khoa'
FROM tbl_dim_ticket t
WHERE t.external_ticket_key = 'PM-001'
ON CONFLICT (evidence_quality_score_id) DO NOTHING;

INSERT INTO tbl_fact_evidence_quality_score (
    evidence_quality_score_id,
    ticket_id,
    metric_value_id,
    score,
    score_band,
    score_rule_version,
    spec_score,
    plan_score,
    review_score,
    self_review_score,
    test_score,
    ci_score,
    blackbox_score,
    report_score,
    missing_items,
    calculated_at,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee2'::uuid,
    t.ticket_id,
    NULL,
    86.00,
    'GOOD',
    'pm-dashboard-demo-v1',
    18.00,
    21.00,
    14.00,
    7.00,
    9.00,
    10.00,
    3.00,
    4.00,
    '[]'::jsonb,
    now() - interval '1 day',
    now() - interval '1 day',
    'nk_trung',
    now() - interval '1 day',
    'nk_trung'
FROM tbl_dim_ticket t
WHERE t.external_ticket_key = 'PM-002'
ON CONFLICT (evidence_quality_score_id) DO NOTHING;

INSERT INTO tbl_fact_evidence_quality_score (
    evidence_quality_score_id,
    ticket_id,
    metric_value_id,
    score,
    score_band,
    score_rule_version,
    spec_score,
    plan_score,
    review_score,
    self_review_score,
    test_score,
    ci_score,
    blackbox_score,
    report_score,
    missing_items,
    calculated_at,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee3'::uuid,
    t.ticket_id,
    NULL,
    55.00,
    'WARNING',
    'pm-dashboard-demo-v1',
    12.00,
    10.00,
    8.00,
    4.00,
    8.00,
    5.00,
    2.00,
    6.00,
    '[]'::jsonb,
    now() - interval '4 day',
    now() - interval '4 day',
    'nvt_dung',
    now() - interval '4 day',
    'nvt_dung'
FROM tbl_dim_ticket t
WHERE t.external_ticket_key = 'PM-003'
ON CONFLICT (evidence_quality_score_id) DO NOTHING;

INSERT INTO tbl_fact_evidence_quality_score (
    evidence_quality_score_id,
    ticket_id,
    metric_value_id,
    score,
    score_band,
    score_rule_version,
    spec_score,
    plan_score,
    review_score,
    self_review_score,
    test_score,
    ci_score,
    blackbox_score,
    report_score,
    missing_items,
    calculated_at,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee4'::uuid,
    t.ticket_id,
    NULL,
    95.00,
    'EXCELLENT',
    'pm-dashboard-demo-v1',
    20.00,
    20.00,
    18.00,
    12.00,
    12.00,
    10.00,
    1.00,
    2.00,
    '[]'::jsonb,
    now() - interval '1 day',
    now() - interval '1 day',
    'lx_loc',
    now() - interval '1 day',
    'lx_loc'
FROM tbl_dim_ticket t
WHERE t.external_ticket_key = 'PM-004'
ON CONFLICT (evidence_quality_score_id) DO NOTHING;

INSERT INTO tbl_fact_risk (
    risk_id,
    ticket_id,
    repository_id,
    artifact_snapshot_id,
    risk_key,
    risk_summary,
    severity,
    mitigation_present,
    mitigation_summary,
    status,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    'f1111111-1111-4111-8111-111111111111'::uuid,
    t.ticket_id,
    r.repository_id,
    NULL,
    'RISK-001',
    'Acceptance criteria is incomplete',
    'HIGH',
    TRUE,
    'Add missing AC section to spec pack',
    'OPEN',
    now() - interval '2 day',
    'pd_khoa',
    now() - interval '2 day',
    'pd_khoa'
FROM tbl_dim_ticket t
JOIN tbl_dim_repository r ON r.repo_name_masked = '30061989/EDCAP_FE'
WHERE t.external_ticket_key = 'PM-001'
ON CONFLICT (risk_id) DO NOTHING;

INSERT INTO tbl_fact_risk (
    risk_id,
    ticket_id,
    repository_id,
    artifact_snapshot_id,
    risk_key,
    risk_summary,
    severity,
    mitigation_present,
    mitigation_summary,
    status,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    'f1111111-1111-4111-8111-111111111112'::uuid,
    t.ticket_id,
    r.repository_id,
    NULL,
    'RISK-002',
    'CI pipeline failure blocks release',
    'CRITICAL',
    FALSE,
    'Need investigation from BE pipeline',
    'OPEN',
    now() - interval '4 day',
    'nvt_dung',
    now() - interval '4 day',
    'nvt_dung'
FROM tbl_dim_ticket t
JOIN tbl_dim_repository r ON r.repo_name_masked = '30061989/EDCAP_BE'
WHERE t.external_ticket_key = 'PM-003'
ON CONFLICT (risk_id) DO NOTHING;

INSERT INTO tbl_fact_risk (
    risk_id,
    ticket_id,
    repository_id,
    artifact_snapshot_id,
    risk_key,
    risk_summary,
    severity,
    mitigation_present,
    mitigation_summary,
    status,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    'f1111111-1111-4111-8111-111111111113'::uuid,
    t.ticket_id,
    r.repository_id,
    NULL,
    'RISK-003',
    'Test evidence is incomplete',
    'MEDIUM',
    TRUE,
    'Upload missing test result artifact',
    'OPEN',
    now() - interval '4 day',
    'nvt_dung',
    now() - interval '4 day',
    'nvt_dung'
FROM tbl_dim_ticket t
JOIN tbl_dim_repository r ON r.repo_name_masked = '30061989/EDCAP_BE'
WHERE t.external_ticket_key = 'PM-003'
ON CONFLICT (risk_id) DO NOTHING;

INSERT INTO tbl_fact_exception (
    exception_id,
    ticket_id,
    repository_id,
    pr_id,
    ci_run_id,
    exception_type,
    reason_present,
    reason,
    alternative_check,
    approved,
    approved_by_role_id,
    approved_at,
    expiry_date,
    follow_up_status,
    status,
    linked_report_path,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    '51111111-1111-4111-8111-111111111111'::uuid,
    t.ticket_id,
    r.repository_id,
    NULL,
    NULL,
    'Missing AC section accepted temporarily',
    TRUE,
    'Manual PM sign-off is needed before release',
    'Alternative check: PM review of the spec pack',
    FALSE,
    rr.role_id,
    NULL,
    NULL,
    'OPEN',
    'OPEN',
    '/reports/pm-001-report.md',
    now() - interval '2 day',
    'pd_khoa',
    now() - interval '2 day',
    'pd_khoa'
FROM tbl_dim_ticket t
JOIN tbl_dim_repository r ON r.repo_name_masked = '30061989/EDCAP_FE'
LEFT JOIN tbl_dim_role rr ON rr.role_name = 'PM'
WHERE t.external_ticket_key = 'PM-001'
ON CONFLICT (exception_id) DO NOTHING;

INSERT INTO tbl_fact_exception (
    exception_id,
    ticket_id,
    repository_id,
    pr_id,
    ci_run_id,
    exception_type,
    reason_present,
    reason,
    alternative_check,
    approved,
    approved_by_role_id,
    approved_at,
    expiry_date,
    follow_up_status,
    status,
    linked_report_path,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    '51111111-1111-4111-8111-111111111112'::uuid,
    t.ticket_id,
    r.repository_id,
    NULL,
    NULL,
    'Release note needs small follow-up',
    TRUE,
    'Release note should mention the new PM dashboard route',
    'Alternative check: update release note after merge',
    TRUE,
    rr.role_id,
    now() - interval '1 day',
    NULL,
    'CLOSED',
    '/reports/pm-004-report.md',
    now() - interval '1 day',
    'lx_loc',
    now() - interval '1 day',
    'lx_loc'
FROM tbl_dim_ticket t
JOIN tbl_dim_repository r ON r.repo_name_masked = '30061989/EDCAP_BE'
LEFT JOIN tbl_dim_role rr ON rr.role_name = 'PM'
WHERE t.external_ticket_key = 'PM-004'
ON CONFLICT (exception_id) DO NOTHING;

INSERT INTO tbl_fact_quality_gate (
    quality_gate_id,
    ticket_id,
    pr_id,
    ci_run_id,
    gate_type,
    gate_status,
    required_flag,
    passed_flag,
    checked_at,
    summary,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    '61111111-1111-4111-8111-111111111111'::uuid,
    t.ticket_id,
    NULL,
    NULL,
    'CI',
    'FAILED',
    TRUE,
    FALSE,
    now() - interval '2 day',
    'CI pipeline failed on unit tests',
    now() - interval '2 day',
    'pd_khoa',
    now() - interval '2 day',
    'pd_khoa'
FROM tbl_dim_ticket t
WHERE t.external_ticket_key = 'PM-001'
ON CONFLICT (quality_gate_id) DO NOTHING;

INSERT INTO tbl_fact_quality_gate (
    quality_gate_id,
    ticket_id,
    pr_id,
    ci_run_id,
    gate_type,
    gate_status,
    required_flag,
    passed_flag,
    checked_at,
    summary,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    '61111111-1111-4111-8111-111111111112'::uuid,
    t.ticket_id,
    NULL,
    NULL,
    'CI',
    'SUCCESS',
    TRUE,
    TRUE,
    now() - interval '1 day',
    'CI passed with warnings',
    now() - interval '1 day',
    'nk_trung',
    now() - interval '1 day',
    'nk_trung'
FROM tbl_dim_ticket t
WHERE t.external_ticket_key = 'PM-002'
ON CONFLICT (quality_gate_id) DO NOTHING;

INSERT INTO tbl_fact_quality_gate (
    quality_gate_id,
    ticket_id,
    pr_id,
    ci_run_id,
    gate_type,
    gate_status,
    required_flag,
    passed_flag,
    checked_at,
    summary,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    '61111111-1111-4111-8111-111111111113'::uuid,
    t.ticket_id,
    NULL,
    NULL,
    'CI',
    'FAILED',
    TRUE,
    FALSE,
    now() - interval '4 day',
    'CI failed because test results are missing',
    now() - interval '4 day',
    'nvt_dung',
    now() - interval '4 day',
    'nvt_dung'
FROM tbl_dim_ticket t
WHERE t.external_ticket_key = 'PM-003'
ON CONFLICT (quality_gate_id) DO NOTHING;

INSERT INTO tbl_fact_quality_gate (
    quality_gate_id,
    ticket_id,
    pr_id,
    ci_run_id,
    gate_type,
    gate_status,
    required_flag,
    passed_flag,
    checked_at,
    summary,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    '61111111-1111-4111-8111-111111111114'::uuid,
    t.ticket_id,
    NULL,
    NULL,
    'CI',
    'SUCCESS',
    TRUE,
    TRUE,
    now() - interval '1 day',
    'CI passed',
    now() - interval '1 day',
    'lx_loc',
    now() - interval '1 day',
    'lx_loc'
FROM tbl_dim_ticket t
WHERE t.external_ticket_key = 'PM-004'
ON CONFLICT (quality_gate_id) DO NOTHING;

-- Quick query to inspect the view after inserting the sample data:
-- SELECT ticket_id, external_ticket_key, title, blocked_flag, waiting_review_flag,
--        missing_evidence_count, risk_count, exception_count, ci_failed_count,
--        evidence_quality_score, score_band, age_days, owner_display, period_key
-- FROM tbl_fact_ticket_dashboard_snapshot
-- ORDER BY blocked_flag DESC, missing_evidence_count DESC, evidence_quality_score ASC NULLS LAST;
