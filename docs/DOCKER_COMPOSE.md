# Docker Compose — How to Use

This project includes three Compose files for different environments. All assume **Docker** and **Docker Compose v2** (`docker compose`) are installed.

---

## Environment (.env and .env.example)

- **`.env.example`** (committed) lists all overridable settings with safe defaults. It is used as the default env file for the backend service so that defaults are in one place.
- **`.env`** (gitignored) is for local overrides. Copy `.env.example` to `.env` and edit; then run Compose. Compose automatically loads `.env` from the project directory for **variable substitution** in the compose file (e.g. `${OLO_TEMPORAL_TARGET}` in prod). The backend container also receives env vars from `env_file: .env.example` (and any `environment:` overrides in the compose file).
- **Override for a single run:** `docker compose --env-file .env -f docker-compose.prod.yml up -d` (uses your `.env` for substitution and does not require renaming).

The same variable names are used by **Spring Boot** when you run `./gradlew bootRun`; Spring reads the process environment, so exporting from `.env` (e.g. `set -a && source .env && ./gradlew bootRun`) or setting them in your IDE applies the same overrides.

---

## Files Overview

| File | Purpose | Services |
|------|--------|----------|
| **docker-compose.dev.yml** | Local development with backend, Redis, and Temporal in containers | Redis, Postgres, Temporal, olo-backend |
| **docker-compose.demo.yml** | One-command demo stack (same as dev; use for demos/tests) | Redis, Postgres, Temporal, olo-backend |
| **docker-compose.prod.yml** | Production-style: backend only; Redis/Temporal via env or external | olo-backend |

---

## 1. Development (`docker-compose.dev.yml`)

**When to use:** Day-to-day development when you want backend, Redis, and Temporal in Docker and run the **executor/worker on your host**.

### Start the stack

From the repo root:

```bash
docker compose -f docker-compose.dev.yml up -d
```

First run builds `olo:local` from the Dockerfile. Later runs reuse the image unless you pass `--build`.

### URLs

- **Backend API:** http://localhost:7080  
- **Swagger UI:** http://localhost:7080/swagger-ui.html (enabled when **`OLO_SWAGGER_UI_ENABLED=true`**, default in dev/demo)  
- **Health:** http://localhost:7080/api/health  
- **Redis:** localhost:6379 (for CLI or other tools)  
- **Temporal:** localhost:7233 (for tctl or worker)

### Run the executor on your host

The workflow **worker/executor** is not in the Compose file. Run it on your machine so it can reach Temporal (localhost:7233) and POST events to the backend (localhost:7080):

```bash
# From repo root (after publishing olo-worker-input and building executor)
./gradlew :olo-worker-input:publishToMavenLocal
cd olo-executor && mvn exec:java -Dexec.mainClass="org.olo.worker.OloExecutorMain"
```

The backend is configured with `OLO_CHAT_CALLBACK_BASE_URL=http://localhost:7080`, so the worker on the host POSTs to localhost:7080 and reaches the backend in Docker.

### Stop

```bash
docker compose -f docker-compose.dev.yml down
```

To remove volumes (Temporal/Postgres data):

```bash
docker compose -f docker-compose.dev.yml down -v
```

---

## 2. Demo (`docker-compose.demo.yml`)

**When to use:** Quick demo or testing with the same stack as dev. Same services; image is tagged `olo:demo`.

### Start

```bash
docker compose -f docker-compose.demo.yml up -d
```

### Run the executor on your host

Same as dev: run the executor locally so workflows run and events are sent to the backend at http://localhost:7080.

### Test the flow

1. Open **http://localhost:7080/swagger-ui.html**  
2. Create a session (e.g. `tenantId`: `2a2a91fb-f5b4-4cf0-b917-524d242b2e3d`)  
3. Send a message and subscribe to run events (SSE or WebSocket)

See [DEMO.md](DEMO.md) and [TESTING_WITH_SWAGGER.md](TESTING_WITH_SWAGGER.md) for step-by-step flows.

### Stop

```bash
docker compose -f docker-compose.demo.yml down
```

---

## 3. Production-style (`docker-compose.prod.yml`)

**When to use:** Deploying the backend in production. Redis and Temporal are **external** (your own infra or managed services). The file runs only the backend container.

### Configure via environment

Copy `.env.example` to `.env` in the project root and set production values. Compose will substitute `${VAR}` in `docker-compose.prod.yml` from `.env` or the shell. The backend container also loads `env_file: .env.example` so any unset variable falls back to that default.

Set at least these in `.env` (or your orchestration):

| Variable | Description | Example |
|----------|-------------|---------|
| `OLO_CACHE_HOST` | Redis/cache host | `redis.prod.svc` or `my-redis.example.com` |
| `OLO_CACHE_PORT` | Redis/cache port | `6379` |
| `OLO_TEMPORAL_TARGET` | Temporal gRPC address | `temporal.prod.svc:7233` |
| `OLO_TEMPORAL_NAMESPACE` | Temporal namespace | `default` or `production` |
| `OLO_CHAT_CALLBACK_BASE_URL` | Base URL the worker uses to POST events | `https://api.example.com` |
| `OLO_WS_JWT_REQUIRED` | Require JWT for WebSocket handshake | `true` |
| `OLO_DEFAULT_TENANT_ID` | Default tenant (WebSocket + UI) | `2a2a91fb-f5b4-4cf0-b917-524d242b2e3d` |

### Use a pre-built image

Point the service at your registry image instead of building:

Edit `docker-compose.prod.yml` and set:

```yaml
services:
  olo-backend:
    image: ghcr.io/myorg/olo:latest
    # image: your-dockerhub-username/olo:latest
```

Comment out or remove the `build` block if present.

### Start

```bash
# With a .env file in the same directory
docker compose -f docker-compose.prod.yml up -d

# Or inline
OLO_CHAT_CALLBACK_BASE_URL=https://api.example.com OLO_TEMPORAL_TARGET=temporal:7233 \
  docker compose -f docker-compose.prod.yml up -d
```

### Swagger UI

Production compose defaults to **`OLO_SWAGGER_UI_ENABLED=false`** so `/swagger-ui.html` and `/api-docs` are disabled. Set `OLO_SWAGGER_UI_ENABLED=true` in `.env` only if you need to expose docs in that environment.

### Healthcheck

The prod file includes an optional healthcheck (commented out) that would hit `http://localhost:7080/api/health`. Uncomment and adjust if your image provides wget/curl.

### Stop

```bash
docker compose -f docker-compose.prod.yml down
```

---

## Common tasks

### Rebuild the backend image (dev/demo)

```bash
docker compose -f docker-compose.dev.yml up -d --build olo-backend
```

### View logs

```bash
docker compose -f docker-compose.dev.yml logs -f olo-backend
docker compose -f docker-compose.dev.yml logs -f temporal
```

### Use a different Compose file by default

```bash
export COMPOSE_FILE=docker-compose.dev.yml
docker compose up -d
docker compose down
```

### Combine files (e.g. prod + local Redis)

Create `docker-compose.override.yml` or run:

```bash
docker compose -f docker-compose.prod.yml -f docker-compose.override.yml up -d
```

---

## Ports summary

| Service | Port | Purpose |
|---------|------|---------|
| olo-backend | 7080 | HTTP API, Swagger, WebSocket, SSE |
| Redis | 6379 | Sessions, queues (kernel config) |
| Temporal | 7233 | gRPC for workflow client/worker |
| Postgres (dev/demo) | 5432 | Temporal persistence |

---

## Related

- **`.env.example`** (repo root) — List of all env vars and defaults; copy to `.env` to override.
- [DOCKER.md](DOCKER.md) — Building the image and pushing to GHCR/Docker Hub  
- [DEMO.md](DEMO.md) — End-to-end chat flow  
- [TESTING_WITH_SWAGGER.md](TESTING_WITH_SWAGGER.md) — API testing with Swagger
