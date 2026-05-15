-- Ticket absorbed into another survives as MERGED; survivor keeps row and receives MEG-TCK-* reference.
ALTER TABLE tickets ADD COLUMN merged_into_ticket_id BIGINT;
