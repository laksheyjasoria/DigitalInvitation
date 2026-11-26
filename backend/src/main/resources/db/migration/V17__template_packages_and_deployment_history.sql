-- XML classpath template packages are immutable release assets.
ALTER TABLE invitation_template_version
    ADD COLUMN definition_format VARCHAR(20) NOT NULL DEFAULT 'JSON',
    ADD COLUMN package_path VARCHAR(300);
ALTER TABLE invitation_template_version
    ADD CONSTRAINT ck_template_definition_format CHECK (definition_format IN ('JSON','XML_PACKAGE'));

CREATE TABLE template_release (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_version_id UUID NOT NULL REFERENCES invitation_template_version(id),
    package_id VARCHAR(20) NOT NULL,
    release_branch VARCHAR(180) NOT NULL,
    commit_sha VARCHAR(64),
    image_tag VARCHAR(180),
    status VARCHAR(30) NOT NULL DEFAULT 'STAGED',
    created_by UUID REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_template_release_version UNIQUE (template_version_id),
    CONSTRAINT uq_template_release_branch UNIQUE (release_branch),
    CONSTRAINT ck_template_release_status CHECK (status IN ('STAGED','COMMITTED','MERGED','BUILDING','DEPLOYING','LIVE','FAILED','ROLLED_BACK'))
);

CREATE TABLE template_deployment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    release_id UUID NOT NULL REFERENCES template_release(id),
    image_tag VARCHAR(180) NOT NULL,
    container_name VARCHAR(120),
    status VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
    triggered_by UUID REFERENCES app_user(id),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    health_check_details JSONB NOT NULL DEFAULT '{}'::jsonb,
    failure_reason VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_template_deployment_status CHECK (status IN ('QUEUED','BUILDING','DEPLOYING','VERIFYING','LIVE','FAILED','ROLLING_BACK','ROLLED_BACK'))
);
CREATE INDEX ix_template_deployment_created ON template_deployment(created_at DESC);
CREATE INDEX ix_template_deployment_status ON template_deployment(status,created_at DESC);