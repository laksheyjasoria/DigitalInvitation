# Sample Invitation Catalogue

Migration `V14__seed_sample_invitation_templates.sql` installs twelve demonstration templates into the normal catalogue. Migration `V15__repair_sample_template_versions.sql` is an idempotent compatibility repair for databases that may have applied the initial V14 draft before its version-row insert was corrected. They are published sample definitions, not one-off UI mocks, so they can be selected through the same category/template APIs used by customer-created invitations.

## Included designs

| Category | Template | Visual direction | Typical use |
|---|---|---|---|
| Wedding | Floral Garden | Botanical, warm neutral, sage accent | Garden and daytime weddings |
| Wedding | Royal Heritage | Heritage, formal serif, antique gold | Traditional and formal celebrations |
| Wedding | Modern Minimal | Contemporary, clean sans-serif, cool neutral | Minimal modern weddings |
| Wedding | Sunset Romance | Romantic, warm rose and terracotta | Intimate ceremonies and receptions |
| Birthday | Confetti Pop | Playful, bright coral accent | All-age birthday parties |
| Birthday | Golden Hour | Elegant, understated gold accent | Milestone birthdays |
| Anniversary | Ever After | Romantic, soft rose palette | Wedding anniversaries |
| Anniversary | Modern Milestone | Modern, teal accent | Silver/golden and other milestones |
| Family | Family Gathering | Botanical, welcoming green palette | Reunions and family gatherings |
| Milestone | Big Achievement | Bold, blue accent | Graduations and achievements |
| Other | Festival Lights | Festive, warm amber palette | Community and cultural events |
| Other | Clean Canvas | Neutral, adaptable layout | Any event without a dedicated category |

## Template definition

Every versioned definition stores:

- A schema version and layout key.
- A design object with a style identifier, font preference and palette.
- Its own ordered section list and default visibility.
- A field list for each section; field entries may be simple keys or objects with a key, label, type, placeholder and required flag.
- `requiredFields` per section. These rules are versioned with the template and are enforced by both the browser and the API. A required section cannot be hidden; a required field cannot be removed.
- Optional fields and sections can be hidden and restored only when the template marks them optional. Required fields and sections cannot be removed. Repeatable sections support multiple event or schedule entries.
- Customers edit invitation values only. Template-defined field keys, labels, order, layout, colours, fonts, animations and design code remain controlled by the selected template. Customer-created custom fields or sections are not part of the standard editor.

The initial sections cover the invitation hero, schedule/functions, story or event description, family details, gallery, RSVP, travel/dress-code information and footer notes. Templates can omit sections that are not relevant to the event. Customers can hide, rename and reorder sections and add custom fields/sections through the existing builder.

## Public rendering and version safety

At publish time the backend copies the selected template's design values into the immutable publication snapshot. Public rendering reads only the snapshot; later admin edits or new template versions do not restyle an already-published invitation. Design colours are validated as six-digit hex values before being assigned to CSS custom properties, and style names are selected from a fixed allowlist. Customer content is rendered as escaped text, never as arbitrary HTML or JavaScript.

The builder generates its form from the selected template definition rather than a shared hard-coded field list. The same definition drives required-field validation and the renderer. Removed optional fields are recorded in the invitation content, so they stay hidden in previews and published pages. The editor preview and public page use the same renderer.

## Demonstration content

The templates are blank starting designs. The owner supplies names, event dates, locations, wording and media. This avoids publishing fake people, fake addresses or misleading event information in sample customer pages.
