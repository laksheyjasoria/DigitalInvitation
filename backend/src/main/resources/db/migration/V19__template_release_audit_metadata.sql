ALTER TABLE template_package_stage
    ADD COLUMN release_commit_sha VARCHAR(40),
    ADD COLUMN release_resource_path VARCHAR(500),
    ADD COLUMN released_template_version_id UUID REFERENCES invitation_template_version(id);

CREATE INDEX ix_template_package_release_commit
    ON template_package_stage(release_commit_sha)
    WHERE release_commit_sha IS NOT NULL;
