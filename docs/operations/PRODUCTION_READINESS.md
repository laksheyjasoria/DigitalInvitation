# Production Readiness Gate

This implementation is an integration milestone. Do not accept live customers until every applicable gate is completed and signed off.

## Code and automated verification
- [x] Maven verify, frontend JavaScript syntax checks, PostgreSQL 16 Flyway migration smoke test, Nginx configuration validation and Docker Compose validation passed in recent GitHub Actions runs; the latest commit is being rechecked.
- [x] Add a PostgreSQL-backed Spring Boot smoke test that executes Flyway migrations and verifies core tables.
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
- [x] Add owner-scoped media deletion and no-store cache headers for private/public media.
- [ ] Add upload malware/content scanning, decompression bomb limits, image dimension limits and storage cleanup tests.
- [ ] Run SAST, dependency, container and secret scans; perform independent penetration testing.

## Integrations
- [ ] Verify actual FileServer multipart upload, stream, delete, auth and error response contracts.
- [ ] Test FileServer high concurrency, Telegram rate limits, range streaming and failure recovery.
- [ ] Configure SMTP and test deliverability, bounce handling, retries and outbox alerting.
- [ ] Configure Razorpay live keys, webhook endpoint and webhook secret; test captures, failures, refunds and reconciliation.
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
- Email outbox sends verification/reset messages when SMTP is enabled, but bounce/dead-letter operations need admin tooling.
- FileServer adapter paths are configurable but not verified against the deployed upload/delete contract. Customer media now has owner-scoped deletion; orphan reconciliation and storage deletion retries remain.
- Razorpay is the first concrete payment adapter; other providers and provider selection are not implemented. Checkout retry/idempotency and captured-payment settlement are implemented but still require provider sandbox and concurrency tests.
- Configurable percentage tax, extension orders/settlement and purchase-time pricing/expiry snapshots are implemented in the current branch; coupons, invoice generation, refund administration and payment reconciliation UI are not complete.
- Basic configurable RSVP capture, owner-only response listing, per-publication/client rate limiting and owner-only CSV export are implemented; full attendee management, invitation analytics and calendar/localization rendering remain.
- Admin dashboard is a first control surface; full user/support/role management, admin MFA and audit search UI remain.
- UI end-to-end, accessibility, browser, mobile, load and security testing remain.

Do not describe this milestone as production-ready until this checklist is completed and CI/security/integration tests are green.
