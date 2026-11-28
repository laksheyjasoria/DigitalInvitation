CREATE OR REPLACE FUNCTION reject_immutable_template_version_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Invitation template versions are immutable; create a new version instead'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_immutable_template_version
BEFORE UPDATE OR DELETE ON invitation_template_version
FOR EACH ROW EXECUTE FUNCTION reject_immutable_template_version_mutation();

CREATE OR REPLACE FUNCTION protect_published_invitation_snapshot()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Published invitation snapshots cannot be deleted'
            USING ERRCODE = '55000';
    END IF;

    IF NEW.invitation_id IS DISTINCT FROM OLD.invitation_id
       OR NEW.publication_number IS DISTINCT FROM OLD.publication_number
       OR NEW.public_slug IS DISTINCT FROM OLD.public_slug
       OR NEW.published_snapshot IS DISTINCT FROM OLD.published_snapshot
       OR NEW.template_version_id IS DISTINCT FROM OLD.template_version_id
       OR NEW.published_at IS DISTINCT FROM OLD.published_at
       OR NEW.expires_at IS DISTINCT FROM OLD.expires_at THEN
        RAISE EXCEPTION 'Published invitation snapshots are immutable'
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_immutable_invitation_publication
BEFORE UPDATE OR DELETE ON invitation_publication
FOR EACH ROW EXECUTE FUNCTION protect_published_invitation_snapshot();
