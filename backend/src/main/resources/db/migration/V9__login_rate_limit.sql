CREATE TABLE auth_login_attempt_bucket (
    bucket_key CHAR(64) PRIMARY KEY,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    window_started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    blocked_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_auth_login_attempt_blocked ON auth_login_attempt_bucket(blocked_until)
    WHERE blocked_until IS NOT NULL;

-- Remove stale counters periodically; the scheduled job is safe to run repeatedly.
