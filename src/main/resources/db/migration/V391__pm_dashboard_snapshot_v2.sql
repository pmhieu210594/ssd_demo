-- PM Dashboard read model (fixed)
-- Show all tickets in scope; status determines whether a ticket is closed/open.

DROP VIEW IF EXISTS tbl_fact_ticket_dashboard_snapshot;

CREATE OR REPLACE VIEW tbl_fact_ticket_dashboard_snapshot AS
WITH latest_phase AS (
    SELECT DISTINCT ON (ticket_id)
        ticket_id,
        phase_id,
        status::text AS status,
        blocked_flag,
        block_reason,
        dwell_time_minutes,
        updated_at,
        updated_by
    FROM tbl_fact_ticket_phase_status
    ORDER BY ticket_id, updated_at DESC, ticket_phase_status_id DESC
),
primary_repo AS (
    SELECT DISTINCT ON (project_id)
        project_id,
        repository_id,
        repo_name_masked
    FROM tbl_dim_repository
    ORDER BY project_id, created_at ASC, repository_id ASC
),
latest_score AS (
    SELECT DISTINCT ON (ticket_id)
        ticket_id,
        score,
        score_band::text AS score_band,
        score_rule_version,
        calculated_at
    FROM tbl_fact_evidence_quality_score
    ORDER BY ticket_id, calculated_at DESC, evidence_quality_score_id DESC
),
latest_pr AS (
    SELECT DISTINCT ON (ticket_id)
        ticket_id,
        author_member_key,
        opened_at,
        collected_at,
        updated_at,
        pr_id
    FROM tbl_fact_pull_request
    WHERE ticket_id IS NOT NULL
    ORDER BY ticket_id, opened_at DESC NULLS LAST, collected_at DESC, updated_at DESC, pr_id DESC
),
missing AS (
    SELECT
        a.ticket_id,
        COUNT(*) FILTER (WHERE a.exists_flag = FALSE AND at.required_flag = TRUE) AS missing_evidence_count
    FROM tbl_fact_artifact_snapshot a
    JOIN tbl_dim_artifact_type at ON at.artifact_type_id = a.artifact_type_id
    WHERE at.artifact_type_code IN (
        'SPEC_PACK',
        'IMPL_PLAN',
        'REVIEW_CHECKLIST',
        'SELF_REVIEW',
        'TEST_PLAN',
        'TEST_RESULTS',
        'REPORT'
    )
    GROUP BY a.ticket_id
),
risks AS (
    SELECT
        r.ticket_id,
        COUNT(*) FILTER (WHERE r.status = 'OPEN') AS risk_count,
        MAX(CASE WHEN r.status = 'OPEN' THEN r.severity::text END) AS highest_risk_severity
    FROM tbl_fact_risk r
    GROUP BY r.ticket_id
),
exceptions AS (
    SELECT
        e.ticket_id,
        COUNT(*) AS exception_count
    FROM tbl_fact_exception e
    GROUP BY e.ticket_id
),
open_issues AS (
    SELECT
        x.ticket_id,
        COALESCE(SUM(x.open_issue_open_count), 0) AS open_issue_count
    FROM (
        SELECT DISTINCT ON (a.ticket_id, at.artifact_type_code)
            a.ticket_id,
            at.artifact_type_code,
            COALESCE(NULLIF(BTRIM(a.parsed_summary->>'open_issue_open_count'), ''), '0')::int AS open_issue_open_count
        FROM tbl_fact_artifact_snapshot a
        JOIN tbl_dim_artifact_type at ON at.artifact_type_id = a.artifact_type_id
        WHERE at.artifact_type_code IN ('SPEC_PACK', 'REPORT')
        ORDER BY a.ticket_id, at.artifact_type_code, a.collected_at DESC, a.artifact_snapshot_id DESC
    ) x
    GROUP BY x.ticket_id
),
ci_runs AS (
    SELECT
        ticket_id,
        ci_run_id,
        status,
        first_run_flag,
        started_at,
        finished_at,
        collected_at
    FROM tbl_fact_ci_run
    WHERE ticket_id IS NOT NULL
),
latest_ci_run AS (
    SELECT DISTINCT ON (ticket_id)
        ticket_id,
        status,
        started_at,
        finished_at,
        collected_at,
        ci_run_id
    FROM ci_runs
    ORDER BY ticket_id, finished_at DESC NULLS LAST, collected_at DESC, ci_run_id DESC
),
first_ci_run AS (
    SELECT DISTINCT ON (ticket_id)
        ticket_id,
        status,
        started_at,
        finished_at,
        collected_at,
        ci_run_id
    FROM ci_runs
    ORDER BY
        ticket_id,
        CASE WHEN first_run_flag THEN 0 ELSE 1 END,
        started_at ASC NULLS LAST,
        finished_at ASC NULLS LAST,
        collected_at ASC,
        ci_run_id ASC
),
ci_metrics AS (
    SELECT
        fr.ticket_id,
        CASE
            WHEN lr.status::text IN ('FAILED', 'FAILURE') THEN 1
            ELSE 0
        END AS ci_failed_count,
        1 AS ticket_with_ci_count,
        CASE
            WHEN fr.status::text IN ('PASSED', 'SUCCESS') THEN 1
            ELSE 0
        END AS first_ci_pass_count
    FROM first_ci_run fr
    LEFT JOIN latest_ci_run lr
        ON lr.ticket_id = fr.ticket_id
)
SELECT
    t.ticket_id,
    t.project_id,
    p.project_alias,
    r.repository_id,
    r.repo_name_masked AS repository_name,
    t.external_ticket_key,
    t.title,
    t.status::text AS status,
    t.created_at AS ticket_created_at,
    lp.phase_id,
    ph.phase_code,
    ph.phase_name,
    ph.phase_order,
    CASE
        WHEN t.status::text NOT IN ('CLOSED', 'MERGED')
        AND (
            COALESCE(lp.blocked_flag, FALSE)
            OR COALESCE(t.status::text = 'IN_REVIEW', FALSE)
            OR COALESCE(m.missing_evidence_count, 0) > 0
            OR COALESCE(oi.open_issue_count, 0) > 0
            OR COALESCE(rs.risk_count, 0) > 0
            OR COALESCE(ci.ci_failed_count, 0) > 0
            OR COALESCE(lpr.opened_at < NOW() - INTERVAL '3 days', FALSE)
        )
        THEN TRUE
        ELSE FALSE
    END AS blocked_flag,
    COALESCE(t.status::text = 'IN_REVIEW', FALSE) AS waiting_review_flag,
    COALESCE(m.missing_evidence_count, 0) AS missing_evidence_count,
    COALESCE(oi.open_issue_count, 0) AS open_issue_count,
    COALESCE(rs.risk_count, 0) AS risk_count,
    COALESCE(ex.exception_count, 0) AS exception_count,
    COALESCE(ci.ticket_with_ci_count, 0) AS ticket_with_ci_count,
    COALESCE(ci.first_ci_pass_count, 0) AS first_ci_pass_count,
    COALESCE(ci.ci_failed_count, 0) AS ci_failed_count,
    rs.highest_risk_severity,
    ls.score AS evidence_quality_score,
    ls.score_band::score_band AS score_band,
    ls.score_rule_version,
    GREATEST(
        0,
        FLOOR(EXTRACT(EPOCH FROM (NOW() - COALESCE(lp.updated_at, t.updated_at, NOW()))) / 86400)::INT
    ) AS age_days,
    COALESCE(pr_mp.pseudonym, mp.pseudonym, 'Unknown') AS owner_display,
    TO_CHAR(COALESCE(ls.calculated_at, NOW()), 'YYYY-MM') AS period_key,
    COALESCE(lp.updated_at, t.updated_at, NOW()) AS updated_at_source,
    NOW() AS refreshed_at,
    LOWER(CONCAT_WS(
        ' ',
        t.external_ticket_key,
        COALESCE(t.title, ''),
        p.project_alias,
        r.repo_name_masked,
        COALESCE(pr_mp.pseudonym, mp.pseudonym, 'Unknown')
    )) AS search_text,
    NOW() AS read_model_created_at,
    'SYSTEM' AS created_by,
    NOW() AS updated_at,
    'SYSTEM' AS updated_by
FROM tbl_dim_ticket t
JOIN tbl_dim_project p ON p.project_id = t.project_id
JOIN primary_repo r ON r.project_id = p.project_id
LEFT JOIN latest_phase lp ON lp.ticket_id = t.ticket_id
LEFT JOIN tbl_dim_phase ph ON ph.phase_id = lp.phase_id
LEFT JOIN latest_score ls ON ls.ticket_id = t.ticket_id
LEFT JOIN missing m ON m.ticket_id = t.ticket_id
LEFT JOIN open_issues oi ON oi.ticket_id = t.ticket_id
LEFT JOIN risks rs ON rs.ticket_id = t.ticket_id
LEFT JOIN exceptions ex ON ex.ticket_id = t.ticket_id
LEFT JOIN ci_metrics ci ON ci.ticket_id = t.ticket_id
LEFT JOIN latest_pr lpr ON lpr.ticket_id = t.ticket_id
LEFT JOIN tbl_dim_member_pseudonym pr_mp ON pr_mp.member_key = lpr.author_member_key
LEFT JOIN tbl_dim_member_pseudonym mp ON mp.pseudonym = COALESCE(lp.updated_by, t.updated_by);