ALTER TABLE automated_jobs ADD COLUMN ticket_id BIGINT;
ALTER TABLE automated_jobs ADD COLUMN priority VARCHAR(32) DEFAULT 'MEDIUM';
ALTER TABLE automated_jobs ADD COLUMN attachment_paths VARCHAR(4000);
ALTER TABLE automated_jobs ADD COLUMN status VARCHAR(32) DEFAULT 'OPEN';
ALTER TABLE automated_jobs ADD COLUMN started_at TIMESTAMP;
ALTER TABLE automated_jobs ADD COLUMN closed_at TIMESTAMP;

ALTER TABLE automated_jobs
    ADD CONSTRAINT fk_aj_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE SET NULL;

UPDATE automated_jobs
SET status = CASE
    WHEN active = 1 THEN 'OPEN'
    ELSE 'CLOSED'
END
WHERE status IS NULL;

CREATE INDEX idx_aj_ticket ON automated_jobs(ticket_id);
CREATE INDEX idx_aj_status ON automated_jobs(status);
