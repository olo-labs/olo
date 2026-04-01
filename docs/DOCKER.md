# Docker

## Build and push (GitHub Actions)

On **push to `main` or `master`**, the workflow [`.github/workflows/docker-build.yml`](../.github/workflows/docker-build.yml) builds the app and pushes the image:

- **GitHub Container Registry (ghcr.io):** always — `ghcr.io/<owner>/<repo>/olo:latest` and `:sha-<short-sha>`
- **Docker Hub:** only when Docker Hub secrets are set — `<DOCKERHUB_USERNAME>/olo:latest` and `:sha-<short-sha>`

### Credentials

**Repo:** **Settings → Secrets and variables → Actions**

| Secret | When | Description |
|--------|------|-------------|
| `GHCR_USERNAME` | Optional | GitHub username for ghcr.io (default: workflow actor) |
| `GHCR_TOKEN` | Optional | Token for ghcr.io (default: `GITHUB_TOKEN`) |
| **`DOCKERHUB_USERNAME`** | **Required for Docker Hub** | Your Docker Hub username |
| **`DOCKERHUB_TOKEN`** | **Required for Docker Hub** | Docker Hub access token ([Create](https://hub.docker.com/settings/security)) with Read & Write permissions |

If `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` are set, the workflow also pushes to Docker Hub. If they are not set, only ghcr.io is updated.

Ensure **Settings → Actions → General → Workflow permissions** allows “Read and write permissions” for the default token when using `GITHUB_TOKEN`.

You can also trigger the workflow manually: **Actions → Build and push Docker image → Run workflow**.

## Run the image locally

Build locally:

```bash
docker build -t olo:local .
docker run -p 7080:7080 olo:local
```

Pull and run from a registry (after the workflow has run at least once):

**GitHub Container Registry:**
```bash
docker pull ghcr.io/<owner>/<repo>/olo:latest
docker run -p 7080:7080 ghcr.io/<owner>/<repo>/olo:latest
```

**Docker Hub** (when you’ve set `DOCKERHUB_USERNAME` / `DOCKERHUB_TOKEN`):
```bash
docker pull <your-dockerhub-username>/olo:latest
docker run -p 7080:7080 <your-dockerhub-username>/olo:latest
```

The app listens on **port 7080**. When running the image alone, set env as needed (e.g. `-e OLO_TEMPORAL_TARGET=host.docker.internal:7233`, `-e OLO_CHAT_CALLBACK_BASE_URL=...`, `-e OLO_CACHE_HOST=...`). Swagger UI is enabled by default; set **`OLO_SWAGGER_UI_ENABLED=false`** to disable in production. See [.env.example](../.env.example) for all variables.

**Image name:** **olo** (e.g. `openllmorchestrator/olo` on Docker Hub). For **docker-compose** (dev, demo, production), see [DOCKER_COMPOSE.md](DOCKER_COMPOSE.md).
