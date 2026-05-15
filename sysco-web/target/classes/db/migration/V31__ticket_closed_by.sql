-- User who last closed the ticket (for reopen eligibility vs role hierarchy).
ALTER TABLE tickets ADD COLUMN closed_by BIGINT;
