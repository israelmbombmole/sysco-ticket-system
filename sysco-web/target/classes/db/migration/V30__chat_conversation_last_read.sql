ALTER TABLE chat_conversations ADD COLUMN user_a_last_read_at TIMESTAMP NULL;
ALTER TABLE chat_conversations ADD COLUMN user_b_last_read_at TIMESTAMP NULL;

-- Treat existing threads as read up to last activity so users are not flooded with false "nouveau" rows.
UPDATE chat_conversations
SET user_a_last_read_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP),
    user_b_last_read_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP);
