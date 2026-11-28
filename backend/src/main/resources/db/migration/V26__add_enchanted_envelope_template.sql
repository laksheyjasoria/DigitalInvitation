-- Add Enchanted Envelope as a second selectable wedding design.
-- Existing templates and invitation publication snapshots are left unchanged.
INSERT INTO invitation_template
    (category_id, template_key, display_name, description, status, current_version)
SELECT id,
       'wedding-enchanted-envelope',
       'Enchanted Envelope',
       'An elegant full-page wedding invitation with an animated envelope opening, falling petals, a live countdown and editable celebration schedule.',
       'PUBLISHED',
       1
FROM event_category
WHERE category_key = 'WEDDING'
ON CONFLICT (template_key) DO NOTHING;

INSERT INTO invitation_template_version
    (template_id, version_number, definition, package_resource_path, package_sha256, package_version)
SELECT id,
       1,
       $definition{
  "schemaVersion": "1",
  "templateId": "TEMP0000002",
  "name": "Enchanted Envelope",
  "version": "1.0.0",
  "packageVersion": "1.0.0",
  "layout": "envelope",
  "locales": [
    "en",
    "hi"
  ],
  "customSectionsAllowed": false,
  "design": {
    "font": "serif",
    "style": "envelope",
    "ornament": "wax-seal",
    "palette": {
      "text": "#2f3d35",
      "muted": "#6b7a71",
      "accent": "#b99358",
      "surface": "#fffdf9",
      "background": "#fbf7f0"
    }
  },
  "sections": [
    {
      "key": "hero",
      "label": "Couple details",
      "enabled": true,
      "required": true,
      "optional": false,
      "repeatable": false,
      "fields": [
        {
          "key": "brideName",
          "label": "Bride name",
          "type": "text",
          "required": true,
          "optional": false,
          "placeholder": ""
        },
        {
          "key": "groomName",
          "label": "Groom name",
          "type": "text",
          "required": true,
          "optional": false,
          "placeholder": ""
        },
        {
          "key": "coupleMonogram",
          "label": "Couple initials",
          "type": "text",
          "required": false,
          "optional": true,
          "placeholder": ""
        }
      ],
      "requiredFields": [
        "brideName",
        "groomName"
      ]
    },
    {
      "key": "event",
      "label": "Main event details",
      "enabled": true,
      "required": true,
      "optional": false,
      "repeatable": false,
      "fields": [
        {
          "key": "eventDate",
          "label": "Event date",
          "type": "date",
          "required": true,
          "optional": false,
          "placeholder": ""
        },
        {
          "key": "eventTime",
          "label": "Event time",
          "type": "time",
          "required": false,
          "optional": true,
          "placeholder": ""
        },
        {
          "key": "venue",
          "label": "Venue",
          "type": "address",
          "required": true,
          "optional": false,
          "placeholder": ""
        }
      ],
      "requiredFields": [
        "eventDate",
        "venue"
      ]
    },
    {
      "key": "story",
      "label": "Our story",
      "enabled": true,
      "required": false,
      "optional": true,
      "repeatable": false,
      "fields": [
        {
          "key": "storyText",
          "label": "Our story",
          "type": "textarea",
          "required": false,
          "optional": true,
          "placeholder": ""
        }
      ],
      "requiredFields": []
    },
    {
      "key": "functions",
      "label": "Celebration schedule",
      "enabled": true,
      "required": false,
      "optional": true,
      "repeatable": true,
      "fields": [
        {
          "key": "functionName",
          "label": "Function name",
          "type": "text",
          "required": true,
          "optional": false,
          "placeholder": ""
        },
        {
          "key": "functionTime",
          "label": "Function time",
          "type": "time",
          "required": false,
          "optional": true,
          "placeholder": ""
        },
        {
          "key": "functionDescription",
          "label": "Function details",
          "type": "textarea",
          "required": false,
          "optional": true,
          "placeholder": ""
        },
        {
          "key": "functionVenue",
          "label": "Function venue",
          "type": "address",
          "required": false,
          "optional": true,
          "placeholder": ""
        }
      ],
      "requiredFields": [
        "functionName"
      ]
    },
    {
      "key": "details",
      "label": "Additional information",
      "enabled": true,
      "required": false,
      "optional": true,
      "repeatable": false,
      "fields": [
        {
          "key": "dressCode",
          "label": "Dress code or guest note",
          "type": "textarea",
          "required": false,
          "optional": true,
          "placeholder": ""
        }
      ],
      "requiredFields": []
    }
  ]
}$::jsonb,
       'templates/packages/TEMP0000002/1.0.0.zip',
       '47179db6d2389f3c874b08d91d6e25dc5e5228048bb9d4992b6b88cad762a94b',
       '1.0.0'
FROM invitation_template
WHERE template_key = 'wedding-enchanted-envelope'
ON CONFLICT (template_id, version_number) DO NOTHING;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM invitation_template t
        JOIN invitation_template_version v ON v.template_id = t.id
        WHERE t.template_key = 'wedding-enchanted-envelope'
          AND t.status = 'PUBLISHED'
          AND t.current_version = 1
          AND v.package_resource_path = 'templates/packages/TEMP0000002/1.0.0.zip'
    ) THEN
        RAISE EXCEPTION 'Enchanted Envelope template could not be registered';
    END IF;
END $$;
