# DigitalInvitation

A configurable, privacy-first digital invitation platform for weddings, birthdays, anniversaries, and other events. Customers select a design, customize optional sections, preview the invitation, pay securely, publish a privacy-safe public URL, and manage its lifecycle.

> **Project status:** Repository foundation. This is not yet a production-ready deployed service. Production readiness requires implementation, automated tests, security review, provider integration, deployment, monitoring, backups, and operational verification.

## Product principles

1. **Configuration first:** Changeable business rules, durations, limits, pricing, feature availability, categories, and section behavior are managed through validated configuration. Do not hardcode business policies.
2. **Privacy by default:** Drafts and account data are private. Only an explicit, reviewed publication snapshot is public.
3. **Provider independence:** Storage and payment integrations are behind interfaces. The existing FileServer is the initial storage integration target; payment providers are independently replaceable.
4. **Template independence:** Trusted, versioned templates are data-driven packages. Customer content is data, never executable template code.
5. **Accessible and inclusive:** Templates support optional, reorderable sections, custom labels/events, and multiple languages. No religious ceremony or tradition is mandatory.
6. **Auditable lifecycle:** Payment, consent, publication, expiry, extension, administrative access, and deletion actions are recorded.
7. **Secure by design:** Server-side authorization, ownership checks, payment verification, webhook idempotency, input validation, output encoding, and least privilege are mandatory.

## Initial technology direction

- Java 21
- Spring Boot 3.x and Maven
- PostgreSQL with versioned database migrations
- Spring Security; authentication/session strategy to be finalized before implementation
- Static frontend initially, with a modular JavaScript builder
- Docker for local and production packaging
- Existing FileServer through a storage-provider adapter
- Payment gateway through a payment-provider adapter

Dependency versions must be pinned and checked for security updates when implementation begins.

## Core customer journey

1. Browse event categories and published templates.
2. Select a template and a specific immutable template version.
3. Fill in invitation details and configure sections (enable, disable, reorder, rename, and add supported custom sections).
4. Preview on mobile and desktop; preview is available before payment.
5. Review the final content and explicitly accept applicable Terms, Privacy Policy, content-rights declarations, and publication disclosures.
6. Create an order and complete payment through the configured provider.
7. Verify payment on the server using provider signatures/API and verified webhooks.
8. Explicitly publish an immutable invitation snapshot and issue an unguessable public slug/URL and QR code.
9. Serve only the published fields and media explicitly approved for public display.
10. Automatically expire access based on the invitation's stored policy snapshot and configured lifecycle rules.

## Invitation lifecycle and configurable expiry

The initial business default discussed is seven calendar days after the **last scheduled function date**. This is a configurable default, not a hardcoded constant.

- Admin-configurable validity duration and unit, including an explicit policy for zero-day expiry.
- Store the effective expiry policy/version and calculated expiry timestamp on each purchased/published invitation.
- Later settings changes apply to new invitations unless an explicit, audited migration policy is approved. Never silently change an existing customer's agreed validity.
- Enforce expiry at request time as well as with a recoverable scheduled background job.
- Expired URLs show a neutral expired page and must not expose invitation content.
- Extension/reactivation is optional and controlled by configuration and pricing.
- Define timezone and calendar semantics explicitly; calculate from the last scheduled function date.

## Template and section model

Templates are trusted packages with immutable versions. Each version declares its category, assets, supported sections, field schemas, localization keys, and preview assets. The builder is generated from those declarations.

Customers can hide optional sections, reorder them, provide custom event names, and omit sections they do not need. Empty optional sections are hidden. The platform must not assume a religion, ceremony, family structure, language, calendar, or naming convention.

Never execute customer-supplied HTML, JavaScript, CSS, or template archives. Template uploads require authorization, package validation, malware/security scanning, versioning, and publication approval. Render customer data with contextual output encoding.

## Privacy and publication model

- Every private resource is owned by a user/tenant and every read/write path enforces authorization in the service layer and repository query.
- Use opaque UUIDs for internal identifiers and cryptographically random, non-sequential public slugs.
- Draft data, payment records, account details, unpublished media, and consent records are never returned by public endpoints.
- Public endpoints resolve only an active publication snapshot and its explicitly public media.
- Customer media is private by default; use short-lived signed access or a controlled delivery endpoint where appropriate.
- Administrative access is least-privilege, purpose-limited, and audited.
- Define retention/deletion schedules, legal holds, backup expiry, and processor disclosures in the Privacy Policy and operational controls.
- Do not store card PAN/CVV. Use hosted/tokenized gateway checkout.

## Provider abstractions

### Storage

The platform depends on a `StorageProvider` interface, not Telegram, OCI, or an S3 SDK directly. The first adapter is intended to call the existing FileServer API. A future OCI/S3-compatible adapter can be added without changing invitation business logic.

Store object keys and metadata in PostgreSQL, not file bytes. Keep private customer media separate from public, immutable template assets. Do not assume Telegram is a CDN; benchmark concurrent reads, range requests, rate limits, and availability before production use.

### Payments

The platform depends on a `PaymentProvider` interface. Provider-specific checkout creation, signature verification, webhook verification, refunds, and status mapping stay inside adapters. Orders and payments are separate records. Verify payment server-side, use idempotency keys, and make webhook processing replay-safe.

## Configuration policy

All changeable business policies belong in validated, typed configuration with defaults, scopes, audit history, and effective dates. Examples include:

- Invitation validity, expiry behavior, extensions, and grace periods
- Category/template/section feature flags
- Prices, taxes, coupons, and refund rules
- Upload count, file-size, MIME-type, and gallery limits
- Payment and storage provider selection
- Locale, time zone, date/calendar formats, and supported languages
- Email/notification behavior and public URL settings
- Terms/Privacy versions and required consent declarations

Security invariants (authorization, signature verification, safe output encoding, secret protection) must not be disableable through ordinary business settings. Secrets belong in a secret manager/environment, never in Git or plaintext database fields.

## Planned modules

- auth, user, role, admin
- category, template, template-version, template-section, template-field
- invitation, invitation-draft, invitation-publication, invitation-media
- order, payment, payment-provider, webhook, refund
- storage-provider, media-delivery
- consent, terms, privacy, audit
- settings, feature-flags, localization
- notification, analytics, public-invitation
- common validation, exception handling, API response, observability

## Target repository structure

    DigitalInvitation/
    ├── backend/
    │   ├── pom.xml
    │   └── src/
    │       ├── main/java/com/app/
    │       └── main/resources/
    ├── frontend/
    │   ├── public/
    │   └── src/
    ├── templates/
    ├── docs/
    │   ├── architecture/
    │   ├── api/
    │   ├── database/
    │   ├── security/
    │   ├── operations/
    │   └── product/
    ├── docker/
    ├── scripts/
    ├── .github/workflows/
    ├── .gitignore
    ├── docker-compose.yml
    └── README.md

## Production-readiness gates

The product is not production-ready until all relevant gates are completed:

- Database migrations, constraints, indexes, transaction boundaries, and tested backup/restore
- Authentication, authorization, account recovery, rate limiting, CSRF/CORS policy, secure cookies/tokens, and session revocation
- Automated unit, integration, API contract, browser, and end-to-end tests
- Ownership/IDOR tests across every private resource
- Payment signature, webhook replay, duplicate-event, timeout, refund, and reconciliation tests
- Storage upload/download authorization, MIME validation, size limits, malware scanning, and provider outage handling
- Template package validation, CSP, output encoding, and dependency/security scanning
- Consent versioning and evidence, Terms/Privacy review for applicable jurisdictions
- Expiry boundary/time-zone tests and background-job recovery
- Structured logs with sensitive-data redaction, metrics, tracing, alerting, and audit logs
- TLS, secrets management, least-privilege deployment, network restrictions, and security headers
- Load/performance tests, concurrency tests, disaster recovery, and operational runbooks
- Domain, email deliverability, monitoring, incident response, and legal/business review

## Development rule

Implement in small, reviewable milestones. Keep complete files consistent, add tests with each feature, document configuration, and never claim a feature is production-ready until its tests and operational checks pass.
