# Runtime Configuration

Business settings are stored in system_setting and changed through the admin API. Seeded values are defaults, not immutable code constants. Changes are audited and versioned. An invitation publication records its expiry policy; an order records its price/currency snapshot.

## Seeded settings

| Key | Initial value | Meaning |
|---|---:|---|
| invitation.expiry.grace-days | 7 | Calendar days after last function date |
| invitation.default-timezone | Asia/Kolkata | Default event timezone |
| invitation.publication.payment-required | false (V13 rollout) | Require a paid order before publish; enable only after payment provider setup |
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
- FileServer Master Drive: APP_FILESERVER_ENABLED, APP_FILESERVER_BASE_URL, APP_FILESERVER_MASTER_KEY, and either APP_FILESERVER_MASTER_DRIVE_ID or APP_FILESERVER_MASTER_DRIVE_NAME
- Email: APP_MAIL_ENABLED, APP_MAIL_FROM, SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD
- Razorpay (later): APP_RAZORPAY_ENABLED, APP_RAZORPAY_KEY_ID, APP_RAZORPAY_KEY_SECRET, APP_RAZORPAY_WEBHOOK_SECRET

Never put real secrets in Git or ordinary system settings. Use a secret manager or protected deployment environment. The FileServer master key is sent only as the server-to-server `X-Master-Key` header and must never be exposed to the frontend, public URLs, logs, or invitation metadata.

## FileServer Master Drive contract

DigitalInvitation integrates with the FileServer Master Drive API, not the user's personal drive. The adapter:

1. Resolves the configured root drive by `APP_FILESERVER_MASTER_DRIVE_ID`, or by exact root name `APP_FILESERVER_MASTER_DRIVE_NAME` (default `DigitalInvitation`).
2. Uploads using `POST /master-drive/{driveId}/files/upload` with multipart field `file`.
3. Polls `GET /master-drive/uploads/{uploadId}/status` until the upload is `COMPLETED`, then stores the returned Master Drive `fileId`.
4. Reads using `GET /master-drive/files/{fileId}/download`, which supports byte-range responses at the FileServer layer.
5. Deletes using `DELETE /master-drive/files/{fileId}` (FileServer soft-delete semantics).

The adapter defaults to a 300-second upload completion timeout and polls every 1,000 ms; both are configurable with `APP_FILESERVER_UPLOAD_TIMEOUT_SECONDS` and `APP_FILESERVER_POLL_INTERVAL_MILLIS`. Endpoint paths are also configurable with APP_FILESERVER_UPLOAD_PATH, APP_FILESERVER_STATUS_PATH, APP_FILESERVER_DOWNLOAD_PATH and APP_FILESERVER_DELETE_PATH.

**Important:** these routes are implemented on the FileServer `feature/master-drive-storage-api` branch. Deploy that branch (or merge its API into the deployed FileServer) before enabling this adapter. The DigitalInvitation adapter must not be enabled until the target FileServer has the Master Drive routes and the configured drive exists. Verify upload, status polling, download/range, delete, auth, concurrency and recovery against the actual deployed instance.

## Payment configuration (deferred)

Payment is intentionally not required in the initial rollout: migration V13 sets `invitation.publication.payment-required=false` and `payment.provider=NONE`. Customers can complete consent and publish without checkout. Razorpay remains disabled by default. Do not set `APP_RAZORPAY_ENABLED=true` or switch the payment-required setting to true until credentials, webhook URL/signature verification and sandbox capture/failure/replay tests are completed. Once enabled, all payment settlement must be provider-verified; do not mark an order paid manually.

## Local Docker

1. Copy .env.example to .env.
2. Set strong local secrets.
3. Configure FileServer Master Drive only after deploying the compatible FileServer API branch and creating the target root drive.
4. Keep Razorpay disabled until sandbox configuration is ready.
5. Configure SMTP before expecting verification and reset emails to be delivered.
6. Run docker compose up --build -d.
7. Open http://localhost:8088.

Do not expose PostgreSQL publicly. Put the application behind TLS termination before real customer traffic.
