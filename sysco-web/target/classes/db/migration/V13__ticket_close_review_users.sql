-- Task creators who must review closure (JSON array of user ids); null = use close_review_role workflow

ALTER TABLE tickets ADD COLUMN close_review_user_ids VARCHAR(2000);
