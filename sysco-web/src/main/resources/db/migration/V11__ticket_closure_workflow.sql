-- Closure request workflow: who requested, which role must review (null = primary assignee self-review)

ALTER TABLE tickets ADD COLUMN close_requested_at TIMESTAMP;
ALTER TABLE tickets ADD COLUMN close_requested_by BIGINT;
ALTER TABLE tickets ADD COLUMN close_review_role VARCHAR(64);
