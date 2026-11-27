# Launch Template Catalogue

The customer catalogue intentionally starts with exactly one published template:

- **Wedding — Floral Garden** (`wedding-floral-garden`)

The other sample/demo definitions from the original catalogue are archived. They are not shown to customers. Non-wedding categories are disabled until a corresponding template is deliberately introduced and enabled by an administrator.

Migration `V23__single_launch_template_catalogue.sql` enforces this state on existing databases as well as fresh installations. It does not delete customer invitation records or rewrite published invitation snapshots. There are no legacy customer invitations to migrate for this launch.

## Adding future templates

Administrators upload a validated XML template package through Template Studio, preview it in isolation, and explicitly publish it. A newly uploaded or staged package is never automatically visible to customers. Additional categories and templates should be enabled only when they are ready for customers.

## Template definition

The template definition controls its sections, fields, required values, optional sections and repeatable event/function sections. Customers edit invitation values only; layout, colours, fonts, animation, section order and template code remain admin-controlled.

## Published invitation safety

At publication, the selected template version and customer content are captured in an immutable snapshot. Later template changes do not alter invitations already published.
