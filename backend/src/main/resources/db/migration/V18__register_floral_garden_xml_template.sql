-- Register the first classpath XML template in the existing customer catalogue.
DO $$
DECLARE
    category_uuid UUID;
    template_uuid UUID;
BEGIN
    SELECT id INTO category_uuid FROM event_category WHERE category_key='WEDDING' LIMIT 1;
    IF category_uuid IS NULL THEN
        RAISE EXCEPTION 'WEDDING category must exist before registering XML template packages';
    END IF;

    INSERT INTO invitation_template(category_id,template_key,display_name,description,status,current_version)
    VALUES(category_uuid,'temp0000001','Floral Garden',
           'A romantic botanical wedding invitation with its own animated HTML/CSS/JavaScript package.',
           'PUBLISHED',1)
    ON CONFLICT(template_key) DO NOTHING;

    SELECT id INTO template_uuid FROM invitation_template WHERE template_key='temp0000001';

    INSERT INTO invitation_template_version(template_id,version_number,definition,definition_format,package_path)
    VALUES(template_uuid,1,
    $definition$
    {
      "schemaVersion":2,
      "layout":"floral",
      "design":{"style":"floral","font":"serif","palette":{"background":"#fbf7f1","surface":"#fffdf9","text":"#394238","accent":"#77866d","muted":"#77776e"}},
      "customSectionsAllowed":false,
      "sections":[
        {"key":"hero","label":"The invitation","required":true,"enabled":true,"allowRename":false,"requiredFields":["headline","hostNames"],"fields":[
          {"key":"headline","label":"Invitation heading","type":"text","required":true,"removable":false},
          {"key":"hostNames","label":"Names","type":"text","required":true,"removable":false},
          {"key":"intro","label":"Invitation message","type":"textarea","required":false,"removable":true}]},
        {"key":"events","label":"Wedding celebrations","required":false,"enabled":true,"repeatable":true,"allowRename":false,"requiredFields":["eventName","date","venue"],"fields":[
          {"key":"eventName","label":"Function name","type":"text","required":true,"removable":false},
          {"key":"date","label":"Date","type":"date","required":true,"removable":false},
          {"key":"time","label":"Time","type":"time","required":false,"removable":true},
          {"key":"venue","label":"Venue","type":"address","required":true,"removable":false},
          {"key":"description","label":"Function details","type":"textarea","required":false,"removable":true}]},
        {"key":"family","label":"With blessings from","required":false,"enabled":false,"allowRename":false,"requiredFields":[],"fields":[
          {"key":"familyNames","label":"Family names","type":"text","required":false,"removable":true}]},
        {"key":"footer","label":"A note for our guests","required":false,"enabled":true,"allowRename":false,"requiredFields":[],"fields":[
          {"key":"note","label":"Closing note","type":"textarea","required":false,"removable":true},
          {"key":"contact","label":"Contact number","type":"phone","required":false,"removable":true}]}
      ]
    }
    $definition$::jsonb,'XML_PACKAGE','templates/TEMP0000001')
    ON CONFLICT(template_id,version_number) DO NOTHING;

    UPDATE invitation_template_version
       SET definition_format='XML_PACKAGE',package_path='templates/TEMP0000001'
     WHERE template_id=template_uuid AND version_number=1;
END $$;