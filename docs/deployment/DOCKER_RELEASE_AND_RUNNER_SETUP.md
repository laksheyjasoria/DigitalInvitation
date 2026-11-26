# Template publishing, Docker releases and blue-green deployment

## Administrator template publishing

Template Studio stores uploads in the private staging table. Upload and preview do not publish anything.
When an authorized administrator chooses a category and clicks **Publish**:

1. The backend revalidates the ZIP and maps its XML definition into an immutable invitation template version.
2. The backend commits the ZIP as `backend/src/main/resources/templates/packages/<template-id>/<package-version>.zip`
   on the dedicated `template-releases` branch using the GitHub Git Data API.
3. The catalogue version stores the classpath resource path and SHA-256. Existing invitations keep their original
   template-version ID and are not changed by later template versions.
4. A push to `template-releases` starts the Docker release workflow. It runs Maven verification and builds
   `ghcr.io/laksheyjasoria/digitalinvitation` with an immutable commit-SHA tag and the moving
   `template-latest` tag.

The GitHub credential is used only by the backend and must never be sent to the browser or stored in the database.
For local development, set these variables in the backend process environment or the protected `.env` file:

- `APP_GITHUB_TEMPLATE_RELEASES_ENABLED=true`
- `APP_GITHUB_TOKEN=<fine-grained-token>`
- `APP_GITHUB_REPOSITORY=laksheyjasoria/DigitalInvitation`
- `APP_GITHUB_TEMPLATE_RELEASE_BRANCH=template-releases`
- `APP_GITHUB_TEMPLATE_BASE_BRANCH=main`

The fine-grained token needs repository Contents read/write permission. The first publish creates the release branch
from the configured base branch if it does not already exist. Before each later package commit, the backend merges the latest
base-branch changes into the release branch so application fixes and prior template ZIPs are retained. Do not use a personal token with broader access than
required. Keep `.env` out of Git.

## Blue-green Docker deployment

The Compose deployment uses a stable blue backend as its initial active instance. The Nginx upstream is stored in
`deploy/nginx/upstream.conf`. The deployment script:

1. Pulls the immutable commit-SHA image.
2. Starts the inactive backend container (blue or green) with the production Compose environment.
3. Waits for its Docker health check.
4. Validates the Nginx configuration and atomically switches the upstream.
5. Reloads Nginx and keeps the previous backend running for rollback.

If candidate health, Nginx validation or reload fails, the script leaves or restores the previous upstream and removes
the failed candidate. The previous backend is intentionally retained until the next deployment reuses that slot.

## One-time host configuration required for automatic deployment

The repository can build the image automatically after an administrator publishes a template. Automatic host deployment
requires a self-hosted GitHub Actions runner because the workflow must access the Docker host and its protected
production configuration.

1. Register a Linux self-hosted runner on the Docker host and add the labels `self-hosted` and `digitalinvitation`.
2. Run it under a dedicated non-root account with the minimum Docker permissions required.
3. Set the runner service environment variable `DI_ENV_FILE` to the absolute path of the host's protected production
   `.env` file. Optionally set `DI_COMPOSE_PROJECT=digitalinvitation` if the Compose project uses another name.
4. Give the runner GHCR pull access. The workflow uses its scoped `GITHUB_TOKEN` to authenticate.
5. In repository Actions variables, set `DI_DEPLOY_ENABLED=true` only after the runner and production Compose stack
   have been prepared and tested.

The deploy job runs only for pushes to `template-releases`. Without the runner and the repository variable, image
builds still complete but no production host is modified.

Never add a runner registration token, PAT, SSH private key, database password, JWT secret, FileServer Master Key or
production `.env` file to the repository. Uploaded template JavaScript runs only inside the sandboxed iframe; it is
never executed by the runner or on the Docker host.
