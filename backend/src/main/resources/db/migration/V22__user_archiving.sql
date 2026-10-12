ALTER TABLE app_user
    ADD COLUMN archived_at DATETIME NULL,
    ADD COLUMN archived_by VARCHAR(120) NULL;

CREATE INDEX idx_app_user_archived_at ON app_user (archived_at);
