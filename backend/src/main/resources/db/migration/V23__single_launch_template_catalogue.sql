-- Launch the customer catalogue with exactly one published wedding template.
-- Older demo definitions are archived; no customer invitation data is deleted.
UPDATE invitation_template
SET status = CASE WHEN template_key = 'wedding-floral-garden' THEN 'PUBLISHED' ELSE 'ARCHIVED' END,
    updated_at = now();

-- Keep only the category containing the launch template visible to customers.
UPDATE event_category
SET enabled = (category_key = 'WEDDING'), updated_at = now();

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM invitation_template WHERE template_key = 'wedding-floral-garden' AND status = 'PUBLISHED') THEN
        RAISE EXCEPTION 'Launch template wedding-floral-garden is missing; refusing to start with an empty catalogue';
    END IF;
    IF (SELECT count(*) FROM invitation_template WHERE status = 'PUBLISHED') <> 1 THEN
        RAISE EXCEPTION 'Launch catalogue must contain exactly one published template';
    END IF;
END $$;
