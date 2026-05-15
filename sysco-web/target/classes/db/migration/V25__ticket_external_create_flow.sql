-- Extra fields for Créer un ticket (reporter context, issue preset, DSTI handling direction).
ALTER TABLE tickets ADD COLUMN reporter_direction_id BIGINT;
ALTER TABLE tickets ADD COLUMN reporter_sous_direction_id BIGINT;
ALTER TABLE tickets ADD COLUMN reporting_office VARCHAR(512);
ALTER TABLE tickets ADD COLUMN issue_preset_code VARCHAR(64);
ALTER TABLE tickets ADD COLUMN handling_direction_id BIGINT;
