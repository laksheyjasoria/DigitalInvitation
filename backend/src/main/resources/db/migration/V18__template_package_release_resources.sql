ALTER TABLE invitation_template_version
    ADD COLUMN package_resource_path VARCHAR(500),
    ADD COLUMN package_sha256 CHAR(64),
    ADD COLUMN package_version VARCHAR(40);

CREATE UNIQUE INDEX uq_template_version_package_resource
    ON invitation_template_version(package_resource_path)
    WHERE package_resource_path IS NOT NULL;

CREATE INDEX ix_template_version_package_sha
    ON invitation_template_version(package_sha256)
    WHERE package_sha256 IS NOT NULL;
