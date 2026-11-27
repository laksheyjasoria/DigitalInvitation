# DigitalInvitation

A configurable digital invitation platform for weddings, birthdays, anniversaries, family celebrations, milestones and other occasions. Customers can select a design, customize optional sections, preview, accept the required declarations, publish a unique URL and QR code, and manage invitation validity. The QR code encodes the event's HTTPS map/location URL; the invitation URL remains a separate shareable link. Customer photo uploads are switched off while the existing backend media APIs and storage adapter are retained for a future enablement.

## Project status

The implementation is available on the draft pull request:

https://github.com/laksheyjasoria/DigitalInvitation/pull/1

This is a test-ready development milestone, not a declaration of production launch approval. The project includes the Java/Spring Boot backend, PostgreSQL/Flyway schema, responsive frontend, authentication, invitation builder, publication snapshots, QR generation, configurable expiry, RSVP, admin catalogue/settings, private media, FileServer Master Drive adapter, Docker deployment files and CI.

**Payment is deliberately disabled by default.** Do not enable it until you configure the provider and complete sandbox/webhook tests. External credentials and deployment values are intentionally left for the project owner to supply.

## Starter template catalogue

Migration `V14` automatically seeds twelve sample designs across all current categories; migration `V15` safely repairs missing sample version rows if an earlier draft of the seed migration was already applied:

| Category | Sample designs |
|---|---|
| Wedding | Floral Garden, Royal Heritage, Modern Minimal, Sunset Romance |
| Birthday | Confetti Pop, Golden Hour |
| Anniversary | Ever After, Modern Milestone |
| Family | Family Gathering |
| Milestone | Big Achievement |
| Other | Festival Lights, Clean Canvas |

Each template is a versioned JSON definition with its own layout, colour palette, typography choice and editable section list. The public invitation captures the selected design in its immutable publication snapshot, so later catalogue changes do not silently restyle invitations already published. Templates are religion-neutral and sections can be hidden, renamed, reordered or extended.

See `docs/product/SAMPLE_TEMPLATES.md` for the catalogue and customization model.

## Stack

- Java 21, Spring Boot 3.5.x, Maven
- PostgreSQL 16, Flyway
- Spring Security, BCrypt, JWT and rotating HttpOnly refresh sessions
- Responsive HTML/CSS/JavaScript frontend
- FileServer Master Drive adapter using server-side `X-Master-Key`
- Docker Compose, Nginx and GitHub Actions
- Razorpay checkout supports sandbox/live modes; sandbox credentials are required for customer payment testing, while ADMIN and SUPER_ADMIN accounts publish free

## Run in Eclipse IDE (recommended for backend development)

### Prerequisites

- Eclipse IDE for Java Developers or Enterprise Java and Web Developers with Maven Integration (m2e)
- JDK 21 (not just a JRE)
- Docker Desktop with Docker Compose
- Node.js 18+ (only for the local frontend proxy)
- Git

### 1. Clone and prepare local settings

```bash
git clone https://github.com/laksheyjasoria/DigitalInvitation.git
cd DigitalInvitation
git checkout feature/platform-implementation
```

Copy `.env.example` to `.env`. For the Eclipse local profile, use this local-only PostgreSQL password so it matches the local Spring profile default:

```properties
POSTGRES_PASSWORD=digital_invitation_local_only
APP_JWT_SECRET=replace-this-with-a-random-local-secret-at-least-32-bytes
APP_PUBLIC_BASE_URL=http://localhost:8088
APP_REFRESH_COOKIE_SECURE=false
APP_RAZORPAY_ENABLED=true
APP_RAZORPAY_MODE=test
APP_RAZORPAY_KEY_ID=rzp_test_your_test_key_id
APP_RAZORPAY_KEY_SECRET=your_test_key_secret
APP_RAZORPAY_WEBHOOK_SECRET=your_test_webhook_secret
APP_FILESERVER_ENABLED=false
APP_MAIL_ENABLED=false
```

Use Razorpay Dashboard Test Mode credentials for all three payment values above. The backend rejects live keys while `APP_RAZORPAY_MODE=test`. Admin and Super Admin accounts bypass checkout and are never charged. Keep `.env` local. Never commit real credentials.

### 2. Start only the local PostgreSQL database

From the repository root:

```bash
docker compose -f docker-compose.yml -f docker-compose.eclipse.yml up -d postgres
```

The Eclipse override publishes PostgreSQL only on `127.0.0.1:5432`; it does not expose the database to your LAN or the internet.

### 3. Import the backend into Eclipse

1. Open Eclipse and select a workspace.
2. Choose **File → Import → Maven → Existing Maven Projects**.
3. Set the root directory to `DigitalInvitation/backend`.
4. Select `pom.xml` and finish the import.
5. Confirm **Project → Properties → Java Compiler** uses Java 21.
6. If dependencies are not resolved, right-click the project and choose **Maven → Update Project**.
7. The repository includes Eclipse project metadata, UTF-8 settings, Maven classpath configuration and a launch configuration named **DigitalInvitation Local**.

### 4. Configure the local admin account in Eclipse

Open **Run → Run Configurations → DigitalInvitation Local → Environment** and add:

| Name | Example value |
|---|---|
| `SPRING_DATASOURCE_PASSWORD` | `digital_invitation_local_only` |
| `APP_BOOTSTRAP_ADMIN_EMAIL` | `admin@localhost` |
| `APP_BOOTSTRAP_ADMIN_PASSWORD` | A unique local password of at least 16 characters |
| `APP_PUBLIC_BASE_URL` | `http://localhost:8088` |

Use your own local admin password. Do not reuse a production password. The local profile disables email, payment and remote FileServer storage by default; it has a development-only JWT secret fallback. Production does not use these local defaults.

Open the **DigitalInvitation Local.launch** file from the backend project and click **Run**. The backend listens on `http://localhost:8080`.

Flyway runs automatically at startup and seeds the sample catalogue. The first start can take a little longer while the database is initialized.

### 5. Start the frontend for Eclipse testing

In a terminal from the repository root:

```bash
node frontend/dev-server.js
```

Open:

- Customer workspace: http://localhost:8088/app.html
- Public landing page: http://localhost:8088/
- Health: http://localhost:8088/actuator/health

The local frontend server serves static assets and proxies `/api` and the health endpoint to the Eclipse backend. It uses only Node's built-in modules; no `npm install` is required. It binds to loopback and is strictly for local development, not production hosting.

Sign in with the local admin credentials configured above. You can then create invitations and browse the seeded template catalogue. For local-only testing, email verification delivery is disabled; use the configured bootstrap admin rather than relying on email workflows.

### 6. Run tests from Eclipse or Maven

From Eclipse: right-click the backend project → **Run As → Maven test** or **Maven build...** with goal `verify`.

From a terminal:

```bash
cd backend
mvn clean verify
```

The PostgreSQL migration smoke test is enabled in CI. For the full CI-equivalent database checks, use the GitHub Actions workflow or the documented CI PostgreSQL setup.

## Run the complete stack with Docker Compose

For an integrated local container test instead of debugging the backend in Eclipse:

1. Copy `.env.example` to `.env`.
2. Set `POSTGRES_PASSWORD`, `APP_JWT_SECRET` (at least 32 random bytes), and a local bootstrap admin email/password.
3. Keep `APP_RAZORPAY_ENABLED=false`, `APP_FILESERVER_ENABLED=false`, and `APP_MAIL_ENABLED=false` until those services are configured.
4. Run:

```bash
docker compose up --build -d
docker compose ps
docker compose logs -f backend
```

5. Open http://localhost:8088.

PostgreSQL is private to the Compose network in the standard stack. Do not publish it on a public interface.

## Production integrations (configure later)

### FileServer Master Drive

DigitalInvitation uses FileServer Master Drive, not a user's personal drive. Before enabling it:

1. Deploy the compatible FileServer Master Drive API branch or merge the API into your FileServer deployment.
2. Use a compatible, already-provisioned FileServer database schema.
3. Create a dedicated root drive named `DigitalInvitation` or set its ID.
4. Set `APP_FILESERVER_ENABLED=true`, `APP_FILESERVER_BASE_URL` and `APP_FILESERVER_MASTER_KEY` in the backend's protected environment.
5. Verify upload, status polling, download/range, delete, authentication, concurrency and interrupted-upload recovery against the real Telegram-backed service.

The Master Key is backend-to-backend only. Never expose it to browser JavaScript, public URLs, logs or invitation metadata.

### Email

Configure `APP_MAIL_ENABLED`, `APP_MAIL_FROM`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME` and `SMTP_PASSWORD` in the protected backend environment. Verify actual verification and reset email delivery before opening registration to customers.

### Payments

Payment is intentionally disabled in the initial rollout. Configure the provider and webhook secrets only when you are ready to enable paid publication. Test captured/failed payments, wrong amount/currency, duplicate and replayed webhooks, concurrency, refunds and reconciliation before changing the payment-required setting.

### Production security and operations

- Use HTTPS/TLS and set `APP_REFRESH_COOKIE_SECURE=true`.
- Use a secret manager for database, JWT, SMTP and Master Key credentials.
- Keep PostgreSQL private; configure encrypted backups, point-in-time recovery and a tested restore procedure.
- Configure monitoring, alerts, structured logs, retention and incident response.
- Run authorization/IDOR tests, dependency/container/secret scans, penetration testing and load tests.
- Configure media malware scanning and image dimension/decompression limits before allowing untrusted uploads at scale.
- Obtain legal review of Terms, Privacy Policy, consent language, retention and applicable tax requirements.

## Product principles

1. **Configurable lifecycle:** grace period, timezone, pricing, media quotas and feature flags are runtime settings, not hardcoded business rules.
2. **Private by default:** drafts, account data, orders and media are owner-scoped. Only explicitly published snapshots and public media are available by link.
7. **Location QR:** the published QR encodes the event location URL, never the invitation page URL. The customer photo-upload UI is disabled by default; backend media capabilities are retained.
3. **Inclusive templates:** templates are not tied to a particular religion. Optional sections can be hidden, renamed, reordered or extended.
4. **Immutable publication:** a publication captures the selected template design and content snapshot. Changes require an explicit new publication.
5. **Explicit consent:** declarations are versioned and accepted before publishing.
6. **Provider boundaries:** storage and payment are replaceable adapters.

## Documentation

- `docs/product/REQUIREMENTS.md`
- `docs/product/SAMPLE_TEMPLATES.md`
- `docs/architecture/PRODUCT_ARCHITECTURE.md`
- `docs/security/THREAT_MODEL.md`
- `docs/api/API_REFERENCE.md`
- `docs/operations/CONFIGURATION.md`
- `docs/operations/PRODUCTION_READINESS.md`

**Readiness note:** CI and the local test workflow help validate the code, but they do not replace real FileServer/Telegram, SMTP, browser, security, legal, backup/restore and load acceptance. Keep the PR in draft until those launch gates are completed.
