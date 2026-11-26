# Production Readiness Gate

This implementation is an integration milestone. Do not accept live customers until every applicable gate is completed and signed off.

## Code and automated verification
- [x] The implementation commit `5d5f7af0e618e2e6a8155f1288575bad26f3c47d` passed GitHub Actions run [45749457788](https://github.com/laksheyjasoria/DigitalInvitation/actions/runs/45749137693): Java 21 Maven verify, frontend JavaScript syntax checks, PostgreSQL 16 Flyway migration smoke tests, Nginx configuration validation and Docker Compose validation.
- [x] Add a PostgreSQL-backed Spring Boot smoke test that executes Flyway migrations and verifies core tables.
- [x] Seed twelve versioned sample invitation templates across wedding, birthday, anniversary, family, milestone and other categories, with unique palettes and section definitions.
- [x] Verify the sample catalogue and nested design palettes against PostgreSQL in CI.
- [x] Add Eclipse Java 21/Maven project metadata, launch configuration, local-only PostgreSQL Compose override and local frontend API proxy.
- [ ] Complete a manual Eclipse startup and browser acceptance run on the owner's workstation.
- [ ] Add PostgreSQL integration tests for all repository queries, ownership filters and transactional workflows.
- [x] Add configurable per-email/client login throttling, rotating HttpOnly refresh-token sessions, password-reset revocation and auth-version invalidation after account/role changes.
- [x] Add PostgreSQL-backed test proving an access JWT is rejected immediately after the user's auth version changes.
- [x] Add PostgreSQL-backed test for refresh-token rotation, HttpOnly cookie issuance and replay-triggered session-family revocation.
- [ ] Add browser multi-tab rotation, suspended-account and password-reset invalidation tests.
- [x] Add public RSVP throttling per publication/client and scheduled cleanup of expired throttle buckets.
- [x] Add PostgreSQL-backed cross-user invitation detail test and verify the publication row-lock query.
- [ ] Extend cross-user IDOR tests to every order, media and payment endpoint.
- [x] Add unit tests for valid/tampered Razorpay checkout signatures and exact raw-body webhook signatures.
- [ ] Add payment tests for wrong amount/currency/order, duplicate webhook, replay, late webhook, concurrent checkout and refund.
- [x] Serialize invitation checkout and payment settlement with database row locks; reject mismatched Razorpay captures, audit rejected webhook events, and enforce unique provider order IDs.
- [x] Add expiry calculation tests for zero grace, seven calendar days, timezone conversion and a DST transition.
- [ ] Add lifecycle tests for leap days, midnight boundaries, last-function date changes and purchase-time configuration snapshots.
- [ ] Add tests for policy version changes and consent invalidation after content changes.
- [x] Add owner-scoped media deletion and no-store cache headers for private/public media delivery.
- [ ] Add upload malware/content scanning, decompression bomb limits, image dimension limits and storage cleanup tests.
- [ ] Add automated regression tests for custom section/field round-tripping and repeatable function entries across draft reopen, reorder, publish and public rendering.
- [ ] Run SAST, dependency, container and secret scans; perform independent penetration testing.

## Integrations

- [x] Implement DigitalInvitation's FileServer Master Drive adapter using the `X-Master-Key` header, configurable root drive ID/name, asynchronous upload completion polling, download and soft-delete routes.
- [x] Add admin category listing/enablement, template listing/status changes and immutable template version creation APIs with audit events.
- [x] Add customer-side private image gallery upload/list/delete/visibility controls and English/Hindi locale selection with Hindi RSVP labels.
- [x] Defer payment requirement by default with migration V13; checkout remains disabled until provider setup and an explicit admin configuration change.
- [ ] Deploy/merge FileServer's `feature/master-drive-storage-api` branch into the actual FileServer environment used by DigitalInvitation.
- [ ] Create/configure the dedicated Master Drive root (default name: `DigitalInvitation`) and set its ID or exact name in protected environment variables.
- [ ] Verify actual Master Drive upload, status polling, download, byte-range streaming, soft-delete, auth, concurrency and failure recovery against the deployed instance.
- [ ] Test FileServer high concurrency, Telegram rate limits, storage retries, orphan cleanup and recovery after FileServer downtime.
- [ ] Configure SMTP and test deliverability, bounce handling, automated retry behavior and outbox alerting. Admin can inspect metadata and manually retry FAILED items.
- [ ] Payment provider setup is intentionally deferred. Keep Razorpay disabled until credentials, webhook URL/signature verification and sandbox capture/failure/replay/refund tests are completed.
- [ ] Configure provider secrets using a secret manager.

## Operations

- [x] Add frontend CSP, HSTS, clickjacking, MIME-sniffing and referrer-policy headers, API request throttling, upload size ceiling and Nginx configuration validation.
- [ ] Verify TLS termination, strict CORS allowlist, proxy trust configuration and production security headers.
- [ ] Database encryption/backups, point-in-time recovery and restore drill.
- [ ] Storage retention, deletion and orphan cleanup.
- [ ] Monitoring, alerting, structured logs, correlation IDs and error tracking.
- [ ] Admin MFA, role assignment/revocation workflow and privileged-access review.
- [ ] Incident response, disaster recovery, support and legal contact information.
- [ ] Load test public invitation pages and media delivery.
- [ ] Legal review of Terms, Privacy Policy, consent language, tax invoices and jurisdiction-specific requirements.

## Known implementation gaps

- Access tokens are short-lived; refresh tokens rotate in HttpOnly SameSite cookies and password reset revokes refresh sessions. Automated replay/expiry and browser multi-tab tests remain.
- Email outbox sends verification/reset messages when SMTP is enabled. Admins can inspect delivery metadata and manually retry FAILED messages; provider bounce webhooks, dead-letter alerting and delivery analytics remain.
- DigitalInvitation now targets the FileServer Master Drive API using the server-side `X-Master-Key`. Its upload operation waits for FileServer's asynchronous upload job and persists the returned Master Drive file ID. This code is not proof of a live integration: the compatible FileServer API branch must be deployed, a dedicated root drive configured, and upload/read/range/delete/auth/concurrency/recovery tests completed.
- Razorpay remains the first payment adapter but is intentionally disabled until later configuration. Never mark an order paid or publish a paid invitation without a verified provider settlement. Provider sandbox and concurrency tests remain.
- Configurable percentage tax, extension orders/settlement and purchase-time pricing/expiry snapshots are implemented in the current branch; coupons, invoice generation, refund administration and payment reconciliation UI are not complete.
- Basic configurable RSVP capture, owner-only response listing, per-publication/client rate limiting and owner-only CSV export are implemented; full attendee management, invitation analytics and calendar support, additional translated UI languages, and localized date formatting remain.
- Admin dashboard includes customer activation/suspension, category/template management, runtime settings, orders, audit events and email outbox inspection/retry. Full support tooling, admin MFA, role management UX and advanced audit search remain.
- UI end-to-end, accessibility, browser, mobile, load and security testing remain.

Do not describe this milestone as production-ready until this checklist is completed and CI/security/integration tests are green.
