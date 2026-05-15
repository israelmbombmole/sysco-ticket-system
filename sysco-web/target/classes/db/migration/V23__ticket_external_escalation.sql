-- Cross-direction escalation inbox: ticket routed to a direction pool (no assignee yet).

ALTER TABLE tickets ADD COLUMN external_escalation_source_direction_id BIGINT;
ALTER TABLE tickets ADD COLUMN external_escalation_target_direction_id BIGINT;
