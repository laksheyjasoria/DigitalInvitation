# DigitalInvitation

A configurable digital invitation platform for weddings, birthdays, anniversaries, family celebrations and other occasions. Customers can choose a design, customize optional sections, preview the invitation, review policies, pay, publish a unique link and QR code, and manage its validity.

## Current implementation branch

The current implementation is on the draft pull request:

https://github.com/laksheyjasoria/DigitalInvitation/pull/1

This branch contains the Java/Spring Boot foundation, PostgreSQL migrations, authentication and account email workflows, catalogue/template APIs, owner-scoped invitation drafts, consent/policy versioning, a deferred Razorpay adapter, FileServer Master Drive storage adapter, image media APIs, publication snapshots, QR generation, expiry processing, customer editor, admin settings dashboard, Docker and CI configuration.

**This is not yet a production-ready release.** The PR is still draft. GitHub Actions verifies Maven compilation/tests, frontend JavaScript syntax, PostgreSQL 16 Flyway migrations, Nginx configuration and Docker Compose. Recent hardening adds rotating HttpOnly refresh sessions, immediate JWT invalidation on account/role changes, login and RSVP throttling, owner-scoped media deletion, and customer RSVP CSV export. Full browser end-to-end, storage integration against the deployed FileServer, SMTP deliverability, security, load and backup-restore tests remain.

## Stack

- Java 21, Spring Boot 3.5.x, Maven
- PostgreSQL 16, Flyway
- Spring Security, BCrypt, JWT
- Razorpay provider adapter (disabled until later configuration and sandbox verification)
- FileServer Master Drive adapter using server-side `X-Master-Key` authentication
- Responsive static frontend served by Nginx
- Docker Compose and GitHub Actions

## Local setup

1. Copy .env.example to .env.
2. Set POSTGRES_PASSWORD and APP_JWT_SECRET. APP_JWT_SECRET must be a random value of at least 32 bytes.
3. Set APP_PUBLIC_BASE_URL=http://localhost:8088.
4. Set APP_BOOTSTRAP_ADMIN_EMAIL and APP_BOOTSTRAP_ADMIN_PASSWORD (at least 16 characters) if you need an initial SUPER_ADMIN. The bootstrap account is inserted only if that email does not already exist.
5. Configure SMTP before expecting email verification and password-reset links to be delivered. Set APP_MAIL_ENABLED=true and SMTP values.
6. To use Master Drive, deploy the FileServer `feature/master-drive-storage-api` API (or merge it into the deployed FileServer), create a root drive named `DigitalInvitation` or set its ID, then configure APP_FILESERVER_ENABLED, APP_FILESERVER_BASE_URL and APP_FILESERVER_MASTER_KEY. Keep the master key only in the backend environment.
7. Keep Razorpay disabled until provider credentials, webhook configuration and sandbox tests are complete.
8. Run docker compose up --build -d.
9. Open http://localhost:8088. Customer workspace: /app.html. Admin dashboard: /admin.html.

Health endpoint: http://localhost:8088/actuator/health

PostgreSQL is private to the Compose network and is not published to the host by default. Do not expose it publicly.

## Product principles

1. Configuration first: pricing, expiry duration, timezone, media quotas and feature flags are runtime settings with audited changes.
2. Private by default: drafts, account data, payment metadata and media are owner-scoped. Only explicitly published snapshots and public media are accessible by link.
3. Inclusive templates: optional sections can be hidden, renamed, reordered and extended. Templates are versioned and do not require any specific religious tradition.
4. Provider boundaries: payment and storage integrations are replaceable adapters.
5. Paid publication: payment requirement is configurable; when enabled, backend-verified captured payment is required. Publication also requires the current declarations and explicit publish action.
6. Lifecycle: expiry is computed from the last function date plus the effective configured grace period and timezone. The policy is snapshotted with publication. The QR encodes a stable URL; after expiry, the URL shows an expired state.

## Documentation

- docs/product/REQUIREMENTS.md
- docs/architecture/PRODUCT_ARCHITECTURE.md
- docs/security/THREAT_MODEL.md
- docs/api/API_REFERENCE.md
- docs/operations/CONFIGURATION.md
- docs/operations/PRODUCTION_READINESS.md

## Before launch

Complete the production readiness checklist, verify Master Drive upload/status/download/range/delete against the deployed FileServer, run PostgreSQL and browser end-to-end tests, configure SMTP, configure and test the payment provider when ready, perform security and load testing, test backup restoration, and obtain legal review of Terms and Privacy Policy. Do not use the seeded policy drafts as legal advice.
