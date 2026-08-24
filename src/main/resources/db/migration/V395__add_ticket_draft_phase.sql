-- Add draft phase for ticket phase evaluator fallback.

INSERT INTO tbl_dim_phase (phase_code, phase_name, phase_order, description)
VALUES ('10', 'Draft', 11, 'Fallback phase when no qualifying evidence artifact is available')
ON CONFLICT (phase_code) DO NOTHING;
