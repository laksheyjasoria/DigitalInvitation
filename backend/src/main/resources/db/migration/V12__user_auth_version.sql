ALTER TABLE app_user
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;
CREATE INDEX ix_app_user_auth_status ON app_user(id, status, auth_version);
