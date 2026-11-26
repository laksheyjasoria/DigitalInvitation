# Docker image release and same-host deployment

## Current automated release boundary

The Docker image release GitHub Actions workflow verifies the backend and publishes an immutable
backend image to GitHub Container Registry (GHCR). It runs for version tags (v*) or when manually
dispatched with an image tag. The workflow does not deploy a production server.

Image name: ghcr.io/laksheyjasoria/digitalinvitation:<tag>

## Required one-time server setup before deployment can be enabled

1. Install and register a dedicated GitHub Actions self-hosted runner on the Docker host.
2. Give the runner the labels self-hosted, linux, x64, and digitalinvitation.
3. Run the runner under a dedicated non-root account. Grant only the minimum Docker access required.
4. Restrict the runner group to this repository and protected deployment workflow.
5. Configure the host's GHCR pull credentials and production .env outside the repository.
6. Ensure PostgreSQL data and uploaded media are persistent volumes/services and are never inside the replaceable application image.
7. Configure a reverse proxy capable of switching between two application instances before enabling rolling deployment.
8. Protect main, require CI checks, and restrict release tags to authorized maintainers.

Do not add a self-hosted runner token, PAT, SSH private key, database password, JWT secret, or FileServer
Master Key to the repository. Never run arbitrary uploaded template JavaScript or ZIP contents on the
host runner.

## Why deployment is not enabled by this workflow yet

The current Compose file defines one backend service and the existing Nginx frontend proxies to that
single service. Replacing that container in place would introduce downtime and is not a rolling
deployment. A blue/green backend service pair and a controlled proxy switch must be implemented and
tested before the runner is allowed to modify production containers.
