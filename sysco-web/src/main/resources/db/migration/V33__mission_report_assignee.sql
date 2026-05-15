-- Who may edit the mission report: null = only the lead (responsable); non-null = that participant only.
ALTER TABLE field_missions ADD COLUMN report_assignee_user_id BIGINT;

ALTER TABLE field_missions ADD CONSTRAINT fk_fm_report_assignee FOREIGN KEY (report_assignee_user_id) REFERENCES users(id);

UPDATE field_missions SET report_assignee_user_id = report_author_id WHERE report_author_id IS NOT NULL;
