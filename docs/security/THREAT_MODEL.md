# Security Threat Model

## Assets

Account credentials and sessions; private drafts; personal details and media; payment/order references; consent evidence; admin credentials; provider secrets; publication slugs; audit records.

## Threats and controls

- Broken object-level authorization / IDOR: owner-scoped repository queries, service authorization, negative cross-user tests.
- Public data leakage: explicit publication DTO/snapshot allowlist; never serialize persistence entities.
- Guessable URLs: cryptographically secure random slugs, rate limiting, revocation, expiry checks.
- Payment spoofing/replay: server-side signature/API verification, webhook signature verification, unique event IDs, idempotent state transitions.
- Malicious uploads: size/type allowlists, content inspection, malware scanning, private quarantine, safe download headers.
- Stored XSS/template injection: no customer HTML/JS execution, contextual escaping, CSP, trusted template package review.
- Credential theft: strong password hashing, rate limits, short-lived access tokens, refresh rotation/revocation, secure cookies or protected token storage.
- Admin abuse: least privilege, MFA, purpose-limited access, immutable audit trail.
- Secret exposure: secret manager/environment, no secrets in Git/logs/settings read APIs.
- Resource exhaustion: configurable quotas, request limits, upload limits, rate limits, bounded queues.
- Data loss: encrypted backups, retention policy, periodic restore drills.

## Security verification checklist

Run dependency and container scans; test authorization for every owner-scoped endpoint; test expired/revoked publication behavior; fuzz validation boundaries; verify CORS/CSRF and security headers; inspect logs for sensitive data; verify backup restoration; perform independent penetration testing before public launch.
