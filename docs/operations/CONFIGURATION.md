# Runtime Configuration

Business settings are stored in system_setting and changed through the admin API. Seeded values are defaults, not immutable code constants. Changes are audited and versioned. An invitation publication records its expiry policy; an order records its price/currency snapshot.

## Seeded settings

| Key | Initial value | Meaning |
|---|---:|---|
| invitation.expiry.grace-days | 7 | Calendar days after last function date |
| invitation.default-timezone | Asia/Kolkata | Default event timezone |
| invitation.publication.payment-required | true | Require paid order before publish |
| invitation.preview.enabled | true | Preview feature flag |
| invitation.custom-sections.enabled | true | Customer custom sections |
| invitation.rsvp.enabled | false | RSVP feature flag |
| invitation.media.max-file-bytes | 10485760 | Per-file image limit |
| invitation.media.max-files | 30 | Media count per invitation |
| pricing.base-price-minor | 49900 | Base price in minor currency units |
| pricing.currency | INR | Currency |
| pricing.tax-percent | 0 | Tax percentage |
| invitation.expiry.extension-enabled | false | Extension feature flag |
| invitation.expiry.extension-days | 7 | Extension duration |
| invitation.expiry.extension-price-minor | 19900 | Extension price |

The backend refuses to guess missing required price, timezone or expiry settings. Admin setting updates validate value types, reject generic edits to sensitive settings, and write version history and audit records in the same transaction. Safety limits remain enforced.

## Authentication and abuse-control settings

| Environment variable | Default | Meaning |
|---|---:|---|
| APP_JWT_ACCESS_TOKEN_MINUTES | 15 | Access JWT lifetime |
| APP_JWT_REFRESH_TOKEN_DAYS | 30 | Rotating refresh-session lifetime; 1–365 days |
| APP_REFRESH_COOKIE_SECURE | true | Set true behind HTTPS; local HTTP example sets false |
| APP_LOGIN_MAX_ATTEMPTS | 8 | Failed sign-ins before temporary block |
| APP_LOGIN_WINDOW_MINUTES | 15 | Failed-login counting window |
| APP_LOGIN_BLOCK_MINUTES | 15 | Login block duration |
| APP_PUBLIC_RSVP_MAX_PER_HOUR | 5 | RSVP submissions per publication/client per hour |

Refresh credentials are stored as hashes in PostgreSQL and sent in an HttpOnly, SameSite=Strict cookie. Password reset, user suspension and role changes invalidate existing access credentials. Login and public RSVP throttling buckets are periodically cleaned.

## Required environment secrets
- POSTGRES_PASSWORD
- APP_JWT_SECRET — random secret, at least 32 bytes
- APP_BOOTSTRAP_ADMIN_EMAIL and APP_BOOTSTRAP_ADMIN_PASSWORD — initial super-admin; password must be at least 16 characters
- Razorpay: APP_RAZORPAY_ENABLED, APP_RAZORPAY_KEY_ID, APP_RAZORPAY_KEY_SECRET, APP_RAZORPAY_WEBHOOK_SECRET
- FileServer: APP_FILESERVER_ENABLED, APP_FILESERVER_BASE_URL, APP_FILESERVER_SERVICE_TOKEN and endpoint paths
- Email: APP_MAIL_ENABLED, APP_MAIL_FROM, SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD

Never put real secrets in Git or ordinary system settings. Use a secret manager or protected deployment environment.

## FileServer adapter contract

The adapter expects a multipart upload field named file, a JSON response containing fileId or id, and configurable stream/delete paths. The stream path default matches the FileServer stream route previously discussed. The deployed upload and delete contracts must be checked before enabling this adapter. Keep APP_FILESERVER_ENABLED=false until a real integration test verifies upload, read, range behavior, delete, authentication and concurrency.

## Local Docker
1. Copy .env.example to .env.
2. Set strong local secrets.
3. Configure SMTP and payment only when testing those integrations.
4. Configure FileServer only after verifying its exact API contract.
5. Run docker compose up --build -d.
6. Open http://localhost:8088.

Do not expose PostgreSQL publicly. Put the application behind TLS termination before real customer traffic.
