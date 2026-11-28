CREATE TABLE auth_refresh_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user(id),
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by UUID REFERENCES auth_refresh_session(id),
    user_agent VARCHAR(500),
    ip_address INET,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_auth_refresh_session_user_active
    ON auth_refresh_session(user_id, expires_at DESC)
    WHERE revoked_at IS NULL;
CREATE INDEX ix_auth_refresh_session_expiry
    ON auth_refresh_session(expires_at);
