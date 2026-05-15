-- Tickets created by the task planner (AUTO-TCK-*) stay hidden from operational lists until this instant (scheduled start).
ALTER TABLE tickets ADD COLUMN planner_visible_after TIMESTAMP NULL;
