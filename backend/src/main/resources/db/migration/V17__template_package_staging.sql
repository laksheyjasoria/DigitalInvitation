CREATE TABLE template_package_stage (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id VARCHAR(11) NOT NULL,
    template_name VARCHAR(160) NOT NULL,
    package_version VARCHAR(40) NOT NULL,
    archive_bytes BYTEA NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'STAGED',
    file_count INTEGER NOT NULL CHECK (file_count > 0),
    expanded_bytes INTEGER NOT NULL CHECK (expanded_bytes > 0),
    sha256 CHAR(64) NOT NULL,
    uploaded_by UUID NOT NULL REFERENCES app_user(id),
    validation_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    validated_at TIMESTAMPTZ,
    released_at TIMESTAMPTZ,
    CONSTRAINT ck_template_package_status CHECK (status IN ('STAGED','VALIDATED','VALIDATION_FAILED','QUEUED_FOR_RELEASE','RELEASED','RELEASE_FAILED'))
);
CREATE INDEX ix_template_package_stage_created ON template_package_stage(created_at DESC);
CREATE INDEX ix_template_package_stage_template ON template_package_stage(template_id, package_version);
