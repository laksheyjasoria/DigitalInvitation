# Product Requirements

## Customer capabilities

- Sign up, verify email, sign in, recover account access, and manage profile.
- Browse enabled event categories and published templates.
- Create and edit private invitation drafts.
- Configure optional sections, custom labels, ordering, visibility, and supported custom fields.
- Preview in responsive mobile and desktop layouts before payment.
- Upload media within configurable limits and inspect public/private visibility.
- Review all details and provide required explicit declarations and policy consent.
- Pay securely, see order/payment status, and retry safe failed attempts.
- Explicitly publish, copy a unique URL, download/share a QR code, and manage the invitation.
- View expiry date, policy, extension options, and expired status.

## Admin capabilities

- Manage roles and access permissions.
- Configure categories, templates, immutable template versions, section library, localization, and availability.
- Configure validity duration, expiry semantics, extension pricing, order pricing, taxes, coupons, and feature flags.
- Configure storage/payment/email provider selection without exposing secrets.
- Manage Terms, Privacy Policy, declaration versions, and required consent sets.
- Inspect orders, verified payment events, refunds, audit events, and invitation lifecycle.
- Suspend or revoke publications with reason and audit trail.
- Configure operational limits and public base URL.

## Non-functional requirements

- Tenant isolation and object-level authorization on every private resource.
- No public exposure of drafts, unpublished media, personal account data, payment metadata, or consent evidence.
- Accessibility and responsive design; support multiple locales and time zones.
- Idempotent payment/webhook processing and retry-safe background work.
- Observable service with redacted logs, metrics, health checks, alerting, and trace correlation.
- Tested backup/restore and documented incident/recovery procedures.
- Configuration changes validated and audited, with historical versions.
- No customer-provided executable templates or scripts.

## Acceptance criteria

A feature is complete only when its API contract, validation, authorization, migration, tests, observability, failure handling, and user-facing behavior are implemented. A feature is not production-ready merely because its happy path works locally.
