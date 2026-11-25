# Product Architecture

## Architecture decision

Start as a modular monolith. Keep domain modules and provider boundaries explicit; do not introduce microservices before scale or independent deployment needs justify them.

## Runtime boundaries

- Customer frontend: account, invitation editor, live preview, checkout initiation, invitation management.
- Admin frontend: configuration, categories, templates, pricing, consent versions, provider health, support, audit review.
- Backend API: authentication, authorization, domain rules, lifecycle, payment orchestration, storage orchestration, public rendering.
- PostgreSQL: users, configuration and versions, templates and versions, drafts, immutable publications, orders, payments, consent, audit.
- Object storage: template assets and customer media through a replaceable provider adapter.
- External providers: payment gateway, FileServer, email delivery.

## Critical invariants

1. All private operations are owner-scoped in repository queries, not just hidden in the UI.
2. Public routes resolve only a published snapshot whose status and expiry are valid.
3. Draft edits do not mutate an already published snapshot; republishing creates a new publication revision.
4. Payment success is determined only by verified provider evidence, never by a browser redirect.
5. Webhook events are unique by provider and provider event identifier and are processed idempotently.
6. Consent is tied to a user, invitation content revision, exact declaration version, and policy version.
7. Invitation expiry is calculated from the last function date using the timezone/calendar policy captured at purchase.
8. Configuration changes are validated, audited, versioned, and scoped to future purchases unless a controlled migration is approved.
9. Customer content is data. It is escaped and never interpreted as executable markup or script.
10. Secrets are externalized and cannot be read back through ordinary admin settings APIs.

## Publication and expiry

A paid order moves an invitation to a state eligible for publication only after verified payment and required consent. The customer must explicitly publish. Publication captures the exact template version and rendered content snapshot. The public resolver checks revocation and expiry on every request. A scheduled worker is a secondary mechanism to mark expired records and trigger cleanup; it is not the only expiry control.

The initial configured default is seven calendar days after the last scheduled function date. The setting must be read from versioned policy storage, not compiled into application code. Persist the policy snapshot and resolved expiry timestamp per invitation.

## Data protection

- Use DTO allowlists for all API input/output.
- Use UUIDs for internal IDs and cryptographically random public slugs.
- Keep uploads private until an explicit publication action.
- Use signed, short-lived URLs or authenticated proxy delivery for private objects.
- Redact tokens, passwords, payment credentials, and sensitive invitation content from logs.
- Apply retention schedules and legal holds to account, payment, audit, and media data.
- Admin access is role-scoped, audited, and purpose-limited.

## Provider contracts

Storage provider contract: upload, read/stream, delete, and metadata operations without leaking provider-specific SDK types. Payment provider contract: checkout creation, verification, webhook verification, refund, and normalized status. Each adapter owns provider-specific payloads and error mapping.

## Deployment topology

A reverse proxy terminates TLS and forwards to the frontend/backend. PostgreSQL is private to the application network and never exposed publicly in production. Persistent backups are encrypted and restore-tested. Object storage credentials are least-privilege. CI runs tests and dependency checks before deployment.
