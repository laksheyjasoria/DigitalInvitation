-- Publish a new schema version for template-specific required fields.
-- Existing invitation versions and immutable publication snapshots remain untouched.
CREATE TEMPORARY TABLE di_template_v2 AS
SELECT t.id AS template_id,
       t.current_version + 1 AS version_number,
       jsonb_set(
         v.definition,
         '{sections}',
         COALESCE((
           SELECT jsonb_agg(
             CASE
               WHEN section->>'key'='hero' THEN jsonb_set(section,'{requiredFields}','["headline","hostNames"]'::jsonb,true)
               WHEN section->>'key'='events' THEN jsonb_set(section,'{requiredFields}','["eventName","date","venue"]'::jsonb,true)
               ELSE jsonb_set(section,'{requiredFields}','[]'::jsonb,true)
             END
             ORDER BY ord
           )
           FROM jsonb_array_elements(v.definition->'sections') WITH ORDINALITY AS x(section,ord)
         ),'[]'::jsonb),
         true
       ) AS definition
FROM invitation_template t
JOIN invitation_template_version v ON v.template_id=t.id AND v.version_number=t.current_version
WHERE t.template_key IN (
 'wedding-floral-garden','wedding-royal-heritage','wedding-minimal-modern','wedding-sunset-romance',
 'birthday-confetti-pop','birthday-golden-hour','anniversary-ever-after','anniversary-modern-milestone',
 'family-gathering-bloom','milestone-achievement','other-festival-lights','other-clean-canvas'
);

INSERT INTO invitation_template_version(template_id,version_number,definition)
SELECT template_id,version_number,definition FROM di_template_v2
ON CONFLICT(template_id,version_number) DO NOTHING;

UPDATE invitation_template t
SET current_version=v.version_number,updated_at=now()
FROM di_template_v2 v
WHERE t.id=v.template_id AND t.current_version < v.version_number;
