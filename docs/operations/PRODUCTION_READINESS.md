# Production Readiness Gate

This implementation is an integration milestone. Do not accept live customers until every applicable gate is completed and signed off.

## Code and automated verification
- [x] Maven verify, frontend JavaScript syntax checks, PostgreSQL 16 Flyway migration smoke test and Docker Compose validation passed in GitHub Actions run #234 on the current stabilization changes.
- [x] Add a PostgreSQL-backed Spring Boot smoke test that executes Flyway migrations and verifies core tables.
- [ ] Add PostgreSQL integration tests for all repository queries, ownership filters and transactional workflows.
- [x] Add persistent per-email/client login throttling and rotating HttpOnly refresh-token sessions; password reset revokes all refresh sessions.
- [ ] Add integration tests for refresh-token expiry/replay, suspended accounts, password-reset invalidation and multi-tab rotation.
- [ ] Add cross-user IDOR tests for every invitation, order, media and payment endpoint.
- [x] Add unit tests for valid/tampered Razorpay checkout signatures and exact raw-body webhook signatures.
- [ ] Add payment tests for wrong amount/currency/order, duplicate webhook, replay, late webhook, concurrent checkout and refund.
- [x] Serialize invitation checkout and payment settlement with database row locks; reject mismatched Razorpay captures, audit rejected webhook events, and enforce unique provider order IDs.
- [x] Add expiry calculation tests for zero grace, seven calendar days, timezone conversion and a DST transition.
- [ ] Add lifecycle tests for leap days, midnight boundaries, last-function date changes and purchase-time configuration snapshots.
- [ ] Add tests for policy version changes and consent invalidation after content changes.
- [ ] Add upload malware/content scanning, decompression bomb limits, image dimension limits and storage cleanup tests.
- [ ] Run SAST, dependency, container and secret scans; perform independent penetration testing.

## Integrations
- [ ] Verify actual FileServer multipart upload, stream, delete, auth and error response contracts.
- [ ] Test FileServer high concurrency, Telegram rate limits, range streaming and failure recovery.
- [ ] Configure SMTP and test deliverability, bounce handling, retries and outbox alerting.
- [ ] Configure Razorpay live keys, webhook endpoint and webhook secret; test captures, failures, refunds and reconciliation.
- [ ] Configure provider secrets using a secret manager.

## Operations
- [ ] TLS, HSTS, CSP, strict CORS, rate limiting and request-size limits.
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
- FileServer adapter paths are configurable but not verified against the deployed upload/delete contract.
- Razorpay is the first concrete payment adapter; other providers and provider selection are not implemented. Checkout retry/idempotency and captured-payment settlement are implemented but still require provider sandbox and concurrency tests.
- Configurable percentage tax, extension orders/settlement and purchase-time pricing/expiry snapshots are implemented in the current branch; coupons, invoice generation, refund administration and payment reconciliation UI are not complete.
- Basic configurable RSVP capture and owner-only response listing are implemented; RSVP rate limiting/spam controls, attendee management, invitation analytics and full calendar/localization rendering remain.
- Admin dashboard is a first control surface; full user/support/role management, admin MFA and audit search UI remain.
- UI end-to-end, accessibility, browser, mobile, load and security testing remain.

Do not describe this milestone as production-ready until this checklist is completed and CI/security/integration tests are green.
