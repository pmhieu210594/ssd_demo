-- Junction table: M:N between test cases and acceptance criteria
CREATE TABLE IF NOT EXISTS tbl_fact_test_case_ac (
    test_case_ac_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    test_case_id     UUID NOT NULL REFERENCES tbl_fact_test_case(test_case_id) ON DELETE CASCADE,
    ticket_id        UUID NOT NULL REFERENCES tbl_dim_ticket(ticket_id),
    ac_key           VARCHAR(100) NOT NULL,
    ac_id            UUID REFERENCES tbl_fact_acceptance_criteria(ac_id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT uq_test_case_ac UNIQUE (test_case_id, ac_key)
);

CREATE INDEX IF NOT EXISTS idx_test_case_ac_ticket ON tbl_fact_test_case_ac(ticket_id, ac_key);

-- ac_reference is superseded by tbl_fact_test_case_ac
ALTER TABLE tbl_fact_test_case DROP COLUMN IF EXISTS ac_reference;
