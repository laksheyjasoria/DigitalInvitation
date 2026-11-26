ALTER TABLE app_user DROP CONSTRAINT IF EXISTS ck_user_role;
ALTER TABLE app_user ADD CONSTRAINT ck_user_role
    CHECK (role IN ('CUSTOMER','ADMIN','SUPER_ADMIN','SUPPORT'));

CREATE TABLE admin_invite (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(320) NOT NULL,
    role VARCHAR(30) NOT NULL CHECK (role IN ('ADMIN','SUPPORT')),
    token_hash CHAR(64) NOT NULL UNIQUE,
    invited_by UUID NOT NULL REFERENCES app_user(id),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_admin_invite_email ON admin_invite(lower(email), expires_at DESC);
