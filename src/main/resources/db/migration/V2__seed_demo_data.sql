-- =============================================================================
-- V2: Seed minimal demo data so the platform shows something on first boot.
-- Safe to keep in production — uses fixed keys, idempotent via ON CONFLICT.
-- =============================================================================

INSERT INTO project (project_key, name, description, risk_level)
VALUES
    ('DEMO', 'Demo Project',
     'Sample project for first-time platform setup. Sync git_local connector to see real data.',
     'NORMAL')
ON CONFLICT (project_key) DO NOTHING;

-- Register a local repository so the git_local connector knows where to scan.
INSERT INTO repository (project_id, repo_key, host_type, default_branch)
SELECT id, 'sample-repo', 'LOCAL', 'main'
FROM project
WHERE project_key = 'DEMO'
ON CONFLICT (project_id, repo_key) DO NOTHING;
