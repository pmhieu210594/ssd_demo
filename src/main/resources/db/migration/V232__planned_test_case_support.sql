-- Allow planned test cases (seeded from test-plan.md) to exist without a test run
ALTER TABLE tbl_fact_test_case
    ALTER COLUMN test_run_id DROP NOT NULL;

-- Enable upsert by TC ID within a ticket (plan seeds first, results update in-place)
ALTER TABLE tbl_fact_test_case
    ADD CONSTRAINT uq_test_case_per_ticket UNIQUE (ticket_id, test_case_key);
