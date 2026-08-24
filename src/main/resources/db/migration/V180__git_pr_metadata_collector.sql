-- Git/PR Metadata Collector: additive schema for PR-level collection.

ALTER TYPE run_status ADD VALUE IF NOT EXISTS 'PARTIAL_SUCCESS';
ALTER TYPE pr_status ADD VALUE IF NOT EXISTS 'UNKNOWN';
ALTER TYPE review_state ADD VALUE IF NOT EXISTS 'UNKNOWN';
