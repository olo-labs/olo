<!--
Copyright (c) 2026 Olo Labs
SPDX-License-Identifier: Apache-2.0
-->

# olo

Spring Boot **chat backend** for the Olo platform. It exposes REST, SSE, and WebSocket APIs for **olo-chat**, starts Temporal workflows via **olo-temporal-sdk**, and loads regional workflow presets from **`olo.configuration.dir`**.

Default port: **7080**. Swagger UI: **http://localhost:7080/swagger-ui.html**

---

## Documentation index

| Doc | Description |
|-----|-------------|
| **[ARCHITECTURE.md](./ARCHITECTURE.md)** | Stack, modules, configuration model, API surface, chat/run data flow, Temporal integration, storage, Docker. |
| **[DATA_MODEL.md](./DATA_MODEL.md)** | Tenant → Session → Message / Run → ExecutionEvent relationships, fields, persistence. |
| **[FAILURE_RECOVERY.md](./FAILURE_RECOVERY.md)** | Temporal/Redis/worker failures, backend restart, duplicate callbacks, WebSocket reconnect. |
| **[../olo-temporal-sdk/docs/ARCHITECTURE.md](../olo-temporal-sdk/docs/ARCHITECTURE.md)** | Temporal SDK: connection, workflow start/signal, backend boundary. |
| **[../olo-temporal-sdk/docs/DESIGN.md](../olo-temporal-sdk/docs/DESIGN.md)** | Workflow start and human-input signal design. |

---

## Overview

### Role in the system

| Component | Role |
|-----------|------|
| **olo** (this repo) | Chat backend: sessions, messages, runs, execution events, UI context, resource upload. |
| **olo-chat** | React frontend. Calls this backend for REST/SSE/WebSocket. |
| **olo-configuration** | Active `WorkflowDefinition` JSON in `olo-mono/olo-definition/olo-configuration/current-active/`. Drives chat presets and Temporal task queues. |
| **Temporal worker** | Separate process. Executes workflows; POSTs run events to `OLO_CHAT_CALLBACK_BASE_URL`. |

The backend is the **source of truth** for chat sessions, messages, run state, and live execution events. Workflow execution happens in Temporal workers; the backend orchestrates starts, signals, and event fan-out to the UI.

### Chat profiles (required for olo-chat)

**`GET /api/ui/context`** returns `chatProfiles` built from **`olo.configuration.dir/<region>/*.json`**. Each file is a `WorkflowDefinition`; fields map to the UI preset model:

| Workflow JSON | API / UI field |
|---------------|----------------|
| `id` | `id`, `pipeline` |
| `role` | `displayName` |
| `shortDescription` | `displaySummary` |
| `emoji` | `emoji` |
| `queue` | `queue` (typically same as `id`, e.g. `ask`) |
| `runAgain` | `runAgain` |

Place workflow JSON in `olo-mono/olo-definition/olo-configuration/current-active/` (copy from `default/` presets if needed). Example presets in `default/`: `ask`, `fast`, `detailed`, `summary`, `debug`, `agent`, `reviewer`, `architect`, `teacher`, `planner`, `strict`, plus `workflow.json` (minimal echo).

There is **no** legacy Redis/kernel profile config or Queue/Pipeline picker API.

---

## Run locally

### Prerequisites

- **JDK 21** (Gradle toolchain can auto-download)
- **Redis** (session persistence; default `localhost:46379`)
- **Temporal** (default `localhost:7233`)
- **Temporal worker** for the task queues defined in workflow JSON (e.g. `ask`, `fast`)

### Start the backend

From the `olo` directory:

```bat
start.bat
```

or:

```bash
./gradlew bootRun
```

`start.bat` sets `OLO_CONFIGURATION_DIR` to `olo-mono/olo-definition/olo-configuration/current-active` and loads optional `.env` overrides.

### Environment

Copy `.env.example` to `.env` for local overrides. Key variables:

| Variable | Default | Purpose |
|----------|---------|---------|
| `SERVER_PORT` | `7080` | HTTP port |
| `OLO_CONFIGURATION_DIR` | `../olo-mono/olo-definition/olo-configuration/current-active` | Active workflow JSON folder |
| `OLO_TEMPORAL_TARGET` | `localhost:7233` | Temporal gRPC target |
| `OLO_CACHE_HOST` / `OLO_CACHE_PORT` | `localhost` / `46379` | Redis for session persistence |
| `OLO_CHAT_CALLBACK_BASE_URL` | `http://192.168.0.3:7080` for `bootRun`; app fallback `http://localhost:7080` | URL workers use to POST run events. When workers run in Docker and backend runs on this PC, use this PC's LAN IP. |
| `OLO_WS_JWT_REQUIRED` | `false` | WebSocket JWT validation (set `true` in production) |

See `.env.example` for the full list.

### Verify

```bash
curl http://localhost:7080/api/health
curl http://localhost:7080/api/ui/context
```

---

## Docker

### Build image

```bash
docker build -t ololab/olo:latest .
```

CI (`.github/workflows/docker-build.yml`) publishes **library version** and **`latest`** tags to Docker Hub and GHCR.

### Compose (dev stack)

```bash
docker compose -f docker-compose.dev.yml up -d
```

Starts Redis, Postgres, Temporal, and **olo-backend** on port **7080**. Workers still run on the host (or a separate compose stack).

| File | Purpose |
|------|---------|
| `docker-compose.dev.yml` | Backend + Redis + Temporal for development |
| `docker-compose.demo.yml` | Demo-oriented stack |
| `docker-compose.prod.yml` | Production-oriented backend service |

Image includes vendored **`olo-configuration`** at `/app/olo-configuration`.

---

## Repo layout

```
olo/
├── docs/                    # This documentation
├── src/main/java/org/olo/app/   # Spring Boot application
├── olo-temporal-sdk/        # Temporal client library (vendored)
├── olo-definition/          # WorkflowDefinition model (vendored)
├── olo-workflow-input/      # WorkflowInput model (vendored)
├── olo-configuration/       # Regional workflow JSON presets
├── Dockerfile
├── docker-compose.*.yml
├── start.bat / stop.bat
└── .env.example
```

---

## Related projects

| Project | Link |
|---------|------|
| **olo-chat** | Frontend; see `olo-chat/docs/README.md` |
| **olo-docker** | Multi-service dev stacks (Temporal, Redis, olo, olo-chat) |

---

## API quick reference

| Endpoint | Purpose |
|----------|---------|
| `GET /api/health` | Liveness |
| `GET /api/ui/context` | Tenant, labels, version, **chatProfiles** |
| `GET /api/tenants` | Tenant list for UI |
| `POST /api/sessions` | Create chat session |
| `POST /api/sessions/{id}/messages` | Send message; starts run |
| `GET /api/runs/{runId}/events` | SSE run events |
| `POST /api/runs/{runId}/human-input` | Human approval / input |
| `POST /api/runs/{runId}/events` | Worker callback (execution events) |
| `POST /api/resource/upload` | Document upload |
| `WS /ws` | Live run events (`SUBSCRIBE_RUN`) |

Full OpenAPI docs: **http://localhost:7080/swagger-ui.html**
