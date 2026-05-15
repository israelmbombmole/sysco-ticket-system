ALTER TABLE users ADD COLUMN IF NOT EXISTS chat_last_seen_at TIMESTAMP;

UPDATE users SET chat_last_seen_at = CURRENT_TIMESTAMP WHERE chat_last_seen_at IS NULL;
