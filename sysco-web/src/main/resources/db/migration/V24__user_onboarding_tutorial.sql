ALTER TABLE users ADD COLUMN IF NOT EXISTS onboarding_tutorial_completed INTEGER NOT NULL DEFAULT 0;

UPDATE users SET onboarding_tutorial_completed = 1;
