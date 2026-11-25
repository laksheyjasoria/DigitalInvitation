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

The backend refuses to guess missing required price, timezone or expiry settings. Safety limits remain enforced.

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
