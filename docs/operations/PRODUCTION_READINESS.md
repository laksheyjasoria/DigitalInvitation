# Production Readiness Gate

This implementation is an integration milestone. Do not accept live customers until every applicable gate is completed and signed off.

## Code and automated verification
- [ ] Maven verify passes on Java 21.
- [ ] Add PostgreSQL integration tests for every Flyway migration and all repository queries.
- [ ] Add tests for auth token expiry, replay, brute-force limits, suspended accounts and password reset.
- [ ] Add cross-user IDOR tests for every invitation, order, media and payment endpoint.
- [ ] Add payment tests for bad signatures, wrong amount/currency/order, duplicate webhook, replay, late webhook and refund.
- [ ] Add lifecycle tests for timezones, leap days, DST, midnight boundaries, last-function date, grace=0 and grace changes after purchase.
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
- No refresh-token rotation/revocation or login rate limiting yet.
- Email outbox sends verification/reset messages when SMTP is enabled, but bounce/dead-letter operations need admin tooling.
- FileServer adapter paths are configurable but not verified against the deployed upload/delete contract.
- Razorpay is the first concrete payment adapter; other providers and provider selection are not implemented.
- Tax calculation, coupons, extension purchases, refund administration and payment reconciliation UI are not complete.
- RSVP capture, attendee management, invitation analytics and full calendar/localization rendering remain.
- Admin dashboard is a first control surface; full user/support/role management, admin MFA and audit search UI remain.
- UI end-to-end, accessibility, browser, mobile, load and security testing remain.

Do not describe this milestone as production-ready until this checklist is completed and CI/security/integration tests are green.
