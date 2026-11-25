# DigitalInvitation

A configurable, privacy-first digital invitation platform for weddings, birthdays, anniversaries, and other events.

## Current implementation status

This repository now contains the initial project foundation: Java 21 / Spring Boot backend, PostgreSQL schema migration, locked-down security defaults, health API, responsive landing page, Docker Compose topology, and a backend CI workflow.

**This is an early foundation build, not a complete or production-ready SaaS yet.** Customer authentication, invitation editing, template management, payment gateway, FileServer storage adapter, consent capture, publication, expiry worker, admin dashboard, and operational hardening remain to be implemented and tested. The API intentionally denies all non-public routes until their authentication and authorization are implemented.

## Technology

- Java 21, Spring Boot 3.5.x, Maven
- PostgreSQL 16 and Flyway migrations
- Spring Security with deny-by-default API policy
- Responsive static frontend served by Nginx
- Docker Compose for local development
- GitHub Actions backend verification

## Run locally with Docker

1. Install Docker Engine/Desktop and Docker Compose.
2. Copy `.env.example` to `.env`.
3. Set a strong local PostgreSQL password in `.env`.
4. Run:

       docker compose up --build -d

5. Open the frontend at `http://localhost:8088`.
6. Check backend health at `http://localhost:8088/actuator/health` and API status at `http://localhost:8088/api/v1/public/status`.

The PostgreSQL service is not published to the host in the default Compose topology. Do not expose it publicly.

## Product principles

1. **Configuration first:** Business durations, limits, prices, feature availability, categories, and section behavior are validated settings, not hardcoded rules.
2. **Privacy by default:** Drafts and account data are private. Only an explicit, reviewed publication snapshot is public.
3. **Provider independence:** Storage and payment integrations are behind replaceable interfaces. The existing FileServer is the initial storage integration target.
4. **Inclusive templates:** Optional, reorderable sections, custom event labels, and multiple languages. No religious ceremony or tradition is mandatory.
5. **Auditable lifecycle:** Payment, consent, publication, expiry, extension, administrative access, and deletion actions are recorded.
6. **Secure by design:** Server-side authorization, ownership checks, verified payments, idempotent webhooks, safe output encoding, and least privilege are mandatory.

## Invitation lifecycle

The initial business default discussed is seven calendar days after the **last scheduled function date**. This is a configurable default, never a hardcoded constant. Each paid invitation must retain the effective policy/version and calculated expiry agreed at purchase. Changes to defaults apply to new invitations unless an explicit, audited migration is approved. Expiry must be checked on every public request; scheduled processing is supplementary.

## Planned customer journey

Browse categories and templates → customize sections and content → preview on mobile/desktop → review details and consent → pay through configured provider → server verifies payment → customer explicitly publishes → receive unique URL and QR code → access ends according to the stored expiry policy.

## Architecture and security decisions

See:
- `docs/architecture/PRODUCT_ARCHITECTURE.md`
- `docs/product/REQUIREMENTS.md`
- `docs/security/THREAT_MODEL.md`

## Production-readiness gates

Before public launch, implement and verify authentication and account recovery; owner-scoped APIs and IDOR tests; template editor and preview; storage and upload safety; payment signature/webhook replay protection; versioned consent and policy capture; immutable publication snapshots; request-time expiry enforcement and recovery-safe jobs; admin controls and audit; monitoring, backups and restore drills; load/security testing; TLS, secret management, deployment and incident runbooks; and legal review.

A feature is not production-ready until its tests and operational checks pass.
