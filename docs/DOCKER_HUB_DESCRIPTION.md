# Docker Hub — Repository Description (Copy-Paste)

Use the text below in **Docker Hub** for the image repository **olo** (e.g. `openllmorchestrator/olo`).  
Go to your repo on Docker Hub → **Edit** (or **Repository description**) and paste.

---

## Short description (max ~100 chars for list view)

```
Olo Chat Backend — Spring Boot API for chat sessions, runs, and run events (SSE/WebSocket). Temporal + Redis.
```

---

## Full description (markdown for the repo page)

Copy everything below the line into the **Full Description** field (supports Markdown).

---

**Olo Chat Backend** — HTTP API and WebSocket server for the Olo chat flow: sessions, messages, runs, and run events (SSE and WebSocket). Built with Spring Boot 3 and Java 21. Connects to **Temporal** for workflows and **Redis** for session/queue data.

### Quick start

```bash
docker pull openllmorchestrator/olo:latest
docker run -p 7080:7080 \
  -e OLO_TEMPORAL_TARGET=host.docker.internal:7233 \
  -e OLO_CHAT_CALLBACK_BASE_URL=http://host.docker.internal:7080 \
  -e OLO_CACHE_HOST=host.docker.internal \
  openllmorchestrator/olo:latest
```

- **API:** http://localhost:7080  
- **Swagger UI:** http://localhost:7080/swagger-ui.html  
- **Health:** http://localhost:7080/api/health  

### Environment variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | HTTP port | `7080` |
| `OLO_CACHE_HOST` | Redis/cache host | `localhost` |
| `OLO_CACHE_PORT` | Redis/cache port | `6379` |
| `OLO_CACHE_DATABASE` | Redis database index | `0` |
| `OLO_TEMPORAL_TARGET` | Temporal gRPC address | `localhost:7233` |
| `OLO_TEMPORAL_NAMESPACE` | Temporal namespace | `default` |
| `OLO_TEMPORAL_TASK_QUEUE` | Task queue name | `olo-chat` |
| `OLO_CHAT_CALLBACK_BASE_URL` | Base URL for worker callbacks | `http://localhost:7080` |
| `OLO_WS_JWT_REQUIRED` | Require JWT for WebSocket | `false` (set `true` in production) |
| `OLO_DEFAULT_TENANT_ID` | Default tenant (WebSocket + UI) | `2a2a91fb-f5b4-4cf0-b917-524d242b2e3d` |
| `OLO_SWAGGER_UI_ENABLED` | Enable Swagger UI at /swagger-ui.html | `true` (set `false` in production to hide docs) |

### Docker Compose

The project includes Compose files for **dev**, **demo**, and **production**. See the repo docs (e.g. `docs/DOCKER_COMPOSE.md`).

**Example (dev/demo stack with Redis + Temporal):**

```yaml
services:
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
  temporal:
    image: temporalio/auto-setup:1.24.2
    ports: ["7233:7233"]
  olo-backend:
    image: openllmorchestrator/olo:latest
    ports: ["7080:7080"]
    environment:
      OLO_CACHE_HOST: redis
      OLO_TEMPORAL_TARGET: temporal:7233
      OLO_CHAT_CALLBACK_BASE_URL: http://localhost:7080
    depends_on: [redis, temporal]
```

### Docs and source

- **Source:** [https://github.com/OpenLLMOrchestrator/olo](https://github.com/OpenLLMOrchestrator/olo)
- **API / Swagger:** Run the container and open http://localhost:7080/swagger-ui.html (when `OLO_SWAGGER_UI_ENABLED=true`)
- **License:** Apache License 2.0 — see [LICENSE](https://github.com/OpenLLMOrchestrator/olo/blob/main/LICENSE)
