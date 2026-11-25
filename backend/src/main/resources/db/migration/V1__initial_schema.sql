CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE app_user (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 email VARCHAR(320) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL,
 display_name VARCHAR(120) NOT NULL,
 role VARCHAR(30) NOT NULL DEFAULT 'CUSTOMER',
 status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
 email_verified_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_user_role CHECK (role IN ('CUSTOMER','ADMIN','SUPER_ADMIN')),
 CONSTRAINT ck_user_status CHECK (status IN ('PENDING_VERIFICATION','ACTIVE','SUSPENDED','DELETED'))
);

CREATE TABLE system_setting (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 setting_key VARCHAR(160) NOT NULL UNIQUE,
 setting_value JSONB NOT NULL,
 value_type VARCHAR(30) NOT NULL,
 description VARCHAR(500),
 is_sensitive BOOLEAN NOT NULL DEFAULT false,
 updated_by UUID REFERENCES app_user(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE system_setting_version (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 setting_id UUID NOT NULL REFERENCES system_setting(id),
 setting_key VARCHAR(160) NOT NULL,
 setting_value JSONB NOT NULL,
 changed_by UUID REFERENCES app_user(id),
 changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 change_reason VARCHAR(500)
);

CREATE TABLE event_category (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 category_key VARCHAR(80) NOT NULL UNIQUE,
 display_name VARCHAR(120) NOT NULL,
 description VARCHAR(500),
 enabled BOOLEAN NOT NULL DEFAULT true,
 sort_order INTEGER NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE invitation_template (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 category_id UUID NOT NULL REFERENCES event_category(id),
 template_key VARCHAR(100) NOT NULL UNIQUE,
 display_name VARCHAR(160) NOT NULL,
 description VARCHAR(1000),
 status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
 current_version INTEGER NOT NULL DEFAULT 1,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT ck_template_status CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED'))
);
CREATE TABLE invitation_template_version (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 template_id UUID NOT NULL REFERENCES invitation_template(id),
 version_number INTEGER NOT NULL,
 definition JSONB NOT NULL,
 created_by UUID REFERENCES app_user(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE (template_id, version_number)
);

CREATE TABLE invitation (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 owner_user_id UUID NOT NULL REFERENCES app_user(id),
 template_version_id UUID NOT NULL REFERENCES invitation_template_version(id),
 title VARCHAR(180) NOT NULL,
 status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
 content JSONB NOT NULL DEFAULT '{}'::jsonb,
 last_function_date DATE,
 timezone VARCHAR(80) NOT NULL DEFAULT 'Asia/Kolkata',
 public_slug VARCHAR(80) UNIQUE,
 expires_at TIMESTAMPTZ,
 expiry_policy_snapshot JSONB,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_invitation_status CHECK (status IN ('DRAFT','PENDING_PAYMENT','READY_TO_PUBLISH','PUBLISHED','EXPIRED','SUSPENDED','DELETED'))
);
CREATE INDEX ix_invitation_owner_updated ON invitation(owner_user_id, updated_at DESC);
CREATE INDEX ix_invitation_status_expiry ON invitation(status, expires_at);

CREATE TABLE invitation_publication (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 invitation_id UUID NOT NULL REFERENCES invitation(id),
 publication_number INTEGER NOT NULL,
 public_slug VARCHAR(80) NOT NULL UNIQUE,
 published_snapshot JSONB NOT NULL,
 template_version_id UUID NOT NULL REFERENCES invitation_template_version(id),
 published_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 expires_at TIMESTAMPTZ NOT NULL,
 revoked_at TIMESTAMPTZ,
 UNIQUE (invitation_id, publication_number)
);
CREATE TABLE invitation_consent (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id UUID NOT NULL REFERENCES app_user(id),
 invitation_id UUID NOT NULL REFERENCES invitation(id),
 consent_key VARCHAR(100) NOT NULL,
 consent_version VARCHAR(80) NOT NULL,
 accepted BOOLEAN NOT NULL,
 accepted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 source_ip INET,
 user_agent VARCHAR(500),
 content_version BIGINT NOT NULL,
 UNIQUE (invitation_id, content_version, consent_key, consent_version)
);
CREATE TABLE customer_media (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 owner_user_id UUID NOT NULL REFERENCES app_user(id),
 invitation_id UUID NOT NULL REFERENCES invitation(id),
 storage_provider VARCHAR(50) NOT NULL,
 storage_key VARCHAR(500) NOT NULL,
 original_filename VARCHAR(255) NOT NULL,
 content_type VARCHAR(120) NOT NULL,
 size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
 visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE' CHECK (visibility IN ('PRIVATE','PUBLIC')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_customer_media_owner ON customer_media(owner_user_id, invitation_id);

CREATE TABLE customer_order (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id UUID NOT NULL REFERENCES app_user(id),
 invitation_id UUID NOT NULL REFERENCES invitation(id),
 order_number VARCHAR(40) NOT NULL UNIQUE,
 currency CHAR(3) NOT NULL,
 amount_minor BIGINT NOT NULL CHECK (amount_minor >= 0),
 status VARCHAR(30) NOT NULL CHECK (status IN ('CREATED','PAYMENT_PENDING','PAID','FAILED','CANCELLED','REFUNDED','PARTIALLY_REFUNDED')),
 pricing_snapshot JSONB NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE payment_transaction (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 order_id UUID NOT NULL REFERENCES customer_order(id),
 provider VARCHAR(50) NOT NULL,
 provider_payment_id VARCHAR(180),
 provider_order_id VARCHAR(180),
 status VARCHAR(30) NOT NULL CHECK (status IN ('INITIATED','AUTHORIZED','CAPTURED','FAILED','REFUNDED')),
 amount_minor BIGINT NOT NULL,
 currency CHAR(3) NOT NULL,
 verified_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_payment_provider_payment ON payment_transaction(provider,provider_payment_id) WHERE provider_payment_id IS NOT NULL;
CREATE TABLE payment_webhook_event (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 provider VARCHAR(50) NOT NULL,
 provider_event_id VARCHAR(180) NOT NULL,
 event_type VARCHAR(120) NOT NULL,
 payload JSONB NOT NULL,
 received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 processed_at TIMESTAMPTZ,
 processing_status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
 UNIQUE (provider,provider_event_id)
);
CREATE TABLE audit_log (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 actor_user_id UUID REFERENCES app_user(id),
 action VARCHAR(120) NOT NULL,
 resource_type VARCHAR(100) NOT NULL,
 resource_id VARCHAR(120),
 outcome VARCHAR(30) NOT NULL,
 metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_log_resource ON audit_log(resource_type,resource_id,created_at DESC);
