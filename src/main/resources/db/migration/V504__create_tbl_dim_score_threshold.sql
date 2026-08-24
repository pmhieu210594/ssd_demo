CREATE TABLE IF NOT EXISTS tbl_dim_score_threshold (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(100) NOT NULL,
    label VARCHAR(255) NOT NULL,
    min_score INTEGER NOT NULL,
    max_score INTEGER NOT NULL,
    color VARCHAR(20) NOT NULL,
    delete_flag VARCHAR(1) NOT NULL DEFAULT '0' CHECK (delete_flag IN ('0', '1')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_tbl_dim_score_threshold_code_active
    ON tbl_dim_score_threshold (code)
    WHERE delete_flag = '0';

CREATE INDEX IF NOT EXISTS idx_tbl_dim_score_threshold_active_min_score
    ON tbl_dim_score_threshold (min_score)
    WHERE delete_flag = '0';

INSERT INTO tbl_dim_score_threshold (code, label, min_score, max_score, color)
VALUES
    ('CRITICAL', 'Critical', 0, 39, '#F43F5E'),
    ('RISKY', 'Risky', 40, 59, '#F97316'),
    ('WARNING', 'Warning', 60, 74, '#F59E0B'),
    ('GOOD', 'Good', 75, 89, '#0EA5E9'),
    ('EXCELLENT', 'Excellent', 90, 100, '#10B981');
