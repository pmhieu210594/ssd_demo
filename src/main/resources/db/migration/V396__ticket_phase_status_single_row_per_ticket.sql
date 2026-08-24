-- Make ticket phase status a single current-state row per ticket.

WITH ranked AS (
    SELECT
        ticket_phase_status_id,
        ROW_NUMBER() OVER (
            PARTITION BY ticket_id
            ORDER BY updated_at DESC, ticket_phase_status_id DESC
        ) AS rn
    FROM tbl_fact_ticket_phase_status
)
DELETE FROM tbl_fact_ticket_phase_status t
USING ranked r
WHERE t.ticket_phase_status_id = r.ticket_phase_status_id
  AND r.rn > 1;

ALTER TABLE tbl_fact_ticket_phase_status
    DROP CONSTRAINT IF EXISTS uq_ticket_phase;

ALTER TABLE tbl_fact_ticket_phase_status
    ADD CONSTRAINT uq_ticket_phase_ticket UNIQUE (ticket_id);
