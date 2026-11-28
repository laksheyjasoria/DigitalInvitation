-- Publish the uploaded envelope invitation as the next version of the sole launch template.
-- Existing template versions and published invitation snapshots remain immutable.
DO $$
DECLARE
    launch_template_id UUID;
    next_version INTEGER;
BEGIN
    SELECT id, current_version + 1
      INTO launch_template_id, next_version
      FROM invitation_template
     WHERE template_key = 'wedding-floral-garden'
       AND status = 'PUBLISHED'
     FOR UPDATE;

    IF launch_template_id IS NULL THEN
        RAISE EXCEPTION 'Published launch template wedding-floral-garden was not found';
    END IF;

    INSERT INTO invitation_template_version
        (template_id, version_number, definition, package_resource_path, package_sha256, package_version)
    VALUES
        (launch_template_id, next_version,
         '{
           "schemaVersion":"1",
           "templateId":"TEMP0000001",
           "name":"The Grand Invitation",
           "version":"2.0.0",
           "packageVersion":"2.0.0",
           "sections":[
             {"key":"hero","label":"Couple details","enabled":true,"required":true,"optional":false,"repeatable":false,
              "fields":[
                {"key":"brideName","label":"Bride name","type":"text","required":true,"optional":false,"placeholder":""},
                {"key":"groomName","label":"Groom name","type":"text","required":true,"optional":false,"placeholder":""},
                {"key":"coupleMonogram","label":"Couple initials","type":"text","required":false,"optional":true,"placeholder":""}
              ],"requiredFields":["brideName","groomName"]},
             {"key":"event","label":"Main event details","enabled":true,"required":true,"optional":false,"repeatable":false,
              "fields":[
                {"key":"eventDate","label":"Event date","type":"date","required":true,"optional":false,"placeholder":""},
                {"key":"eventTime","label":"Event time","type":"time","required":false,"optional":true,"placeholder":""},
                {"key":"venue","label":"Venue","type":"address","required":true,"optional":false,"placeholder":""}
              ],"requiredFields":["eventDate","venue"]},
             {"key":"functions","label":"Celebration schedule","enabled":true,"required":false,"optional":true,"repeatable":true,
              "fields":[
                {"key":"functionName","label":"Function name","type":"text","required":true,"optional":false,"placeholder":""},
                {"key":"functionTime","label":"Function time","type":"time","required":false,"optional":true,"placeholder":""},
                {"key":"functionDescription","label":"Function details","type":"textarea","required":false,"optional":true,"placeholder":""}
              ],"requiredFields":["functionName"]}
           ]
         }'::jsonb,
         'templates/packages/TEMP0000001/2.0.0.zip',
         '19adb1978ab2f928d98f38a844e5ac01fc15d88b0b181da02292a8717997c401',
         '2.0.0');

    UPDATE invitation_template
       SET current_version = next_version,
           display_name = 'The Grand Invitation',
           description = 'An elegant full-page wedding invitation with an animated envelope opening, falling petals, countdown and editable celebration schedule.',
           updated_at = now()
     WHERE id = launch_template_id;
END $$;
