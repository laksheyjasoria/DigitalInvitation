CREATE TABLE public_submission_bucket (
    bucket_key CHAR(64) PRIMARY KEY,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    window_started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_public_submission_bucket_updated
    ON public_submission_bucket(updated_at);
