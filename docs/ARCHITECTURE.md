<!--
Copyright (c) 2026 Olo Labs
SPDX-License-Identifier: Apache-2.0
-->

# Architecture

Technical architecture of the **olo** chat backend: stack, modules, configuration, APIs, runtime flows, and deployment.

For Temporal client details, see **[olo-temporal-sdk/docs/ARCHITECTURE.md](../olo-temporal-sdk/docs/ARCHITECTURE.md)**.

---

## High-level stack

| Layer | Technology |
|-------|------------|
| **Runtime** | Java 21, Spring Boot |
| **Build** | Gradle (`olo-backend` root project) |
| **Workflow orchestration** | Temporal (via **olo-temporal-sdk**) |
| **Session cache** | Redis (Spring Data Redis) |
| **Configuration** | Filesystem `WorkflowDefinition` JSON under `olo.configuration.dir` |
| **API docs** | SpringDoc OpenAPI / Swagger UI |

The application package is `org.olo.app`. Entry point: `OloApplication`.

---

## System context

```
┌──────────────────┐     REST / SSE / WS      ┌─────────────────────────────────────┐
│  olo-chat (UI)   │ ───────────────────────▶ │  olo backend (Spring Boot, :7080)    │
│  Browser / nginx │                        │  Sessions, messages, runs, events     │
└──────────────────┘                        └───────────┬─────────────┬───────────┘
                                                          │             │
                    ┌─────────────────────────────────────┘             │
                    │ start / signal                                    │ POST /api/runs/{id}/events
                    ▼                                                   ▼
         ┌────────────────────┐                              ┌─────────────────────┐
         │  Temporal Server   │ ◀── task queues (ask, …) ─── │  Temporal worker(s) │
         │  olo.temporal.target│                              │  (separate process) │
         └────────────────────┘                              └─────────────────────┘
                    ▲
                    │ read presets
         ┌──────────┴──────────┐
         │  olo-configuration  │  default/*.json → chatProfiles, queues, workflowType
         └─────────────────────┘
                    ▲
                    │ session persistence
         ┌──────────┴──────────┐
         │  Redis              │  olo.cache.host:port
         └─────────────────────┘
```

Workers must reach **`OLO_CHAT_CALLBACK_BASE_URL`** to push execution events. The UI reaches the backend on the published HTTP port (or via **olo-chat** nginx proxy when both run in Docker).

---

## Module structure

The `olo` repo is self-contained. Gradle includes local builds of shared libraries:

| Path | Artifact | Role |
|------|----------|------|
| `src/main/java/org/olo/app/` | `olo-backend` JAR | Spring Boot chat API and orchestration |
| `olo-temporal-sdk/` | `org.olo:olo-temporal-sdk` | Temporal connection, workflow start, human-input signal |
| `olo-definition/` | `org.olo:olo-definition` | `WorkflowDefinition` model and JSON serializer |
| `olo-workflow-input/` | `org.olo:olo-workflow-input` | `WorkflowInput` payload for workflow starts |
| `olo-configuration/` | _(bundled data)_ | Regional workflow JSON; copied into Docker image |

`settings.gradle` uses `includeBuild` for `olo-definition` and `olo-workflow-input`; `olo-temporal-sdk` is an included subproject.

---

## Configuration model

### Regional filesystem layout

Property: **`olo.configuration.dir`** (env: `OLO_CONFIGURATION_DIR`).

```
olo-configuration/
└── default/           # region folder (= default tenant id)
    ├── ask.json
    ├── fast.json
    ├── workflow.json
    └── …
```

`FilesystemConfigurationLoader` scans region subfolders and parses each `*.json` into `WorkflowDefinition`. `RegionalConfigurationRegistry` exposes snapshots at startup.

`ResolvedOloRuntimeSettings` derives:

- Default **tenant id** (first region folder name, e.g. `default`)
- **Workflow type** from `workflow.json` → `workflowType` (e.g. `"olo"`)
- Default **task queue** from the same file when needed

### Chat profile mapping

`UiContextController` maps each regional workflow to `ChatProfileDto` for **`GET /api/ui/context`**:

- `role` → `displayName` (fallback: `name`, `id`)
- `shortDescription` → `displaySummary`
- `emoji`, `queue`, `id` → `pipeline`
- `runAgain` → `runAgain`

Tenant id: JWT `Authorization` header when present; otherwise default region folder name.

### Legacy config removed

The backend no longer uses `profiles.json`, `pipelines.json`, kernel Redis config, or env vars such as `OLO_TEMPORAL_TASK_QUEUE` / `OLO_DEFAULT_TENANT_ID`. All presets and queues come from workflow JSON.

---

## Application layers

### Controllers (REST)

| Controller | Base path | Responsibility |
|------------|-----------|----------------|
| `HealthController` | `/api/health` | Liveness |
| `UiContextController` | `/api/ui` | UI context and **chatProfiles** |
| `TenantsController` | `/api/tenants` | Tenant list for UI |
| `SessionsController` | `/api/sessions` | Create session, send message |
| `TenantSessionsController` | `/api/tenants/{tenantId}/sessions` | List/delete sessions |
| `RunsController` | `/api/runs` | SSE events, human-input, worker callback |
| `ResourceUploadController` | `/api/resource` | Multipart file upload |

### WebSocket

| Component | Path | Responsibility |
|-----------|------|----------------|
| `WebSocketConfig` | `/ws` | Endpoint registration |
| `RunEventWebSocketHandler` | — | `SUBSCRIBE_RUN`, PING/PONG, catch-up events |
| `WebSocketAuthHandshakeHandler` | — | Optional JWT validation (`olo.ws.jwt.required`) |
| `RunEventWebSocketRegistry` | — | Sessions per `runId` |
| `RunEventBroadcaster` | — | Fan-out to SSE and WebSocket subscribers |

### Services and workflow boundary

| Type | Implementation | Role |
|------|----------------|------|
| `RunService` | `RunServiceImpl` | Start runs, handle worker callbacks, persist assistant/human messages |
| `WorkflowRunner` | `SdkWorkflowRunner` | Abstraction over Temporal; backend does **not** import `io.temporal.*` |
| `ChatRedisPersistence` | — | Optional Redis-backed session/message persistence |
| `ResourceUploadService` | — | Store uploaded files under `olo.resource.upload.base-dir` |

`RunServiceImpl` calls `workflowRunner.startChatRun(...)` with a `WorkflowRunCompletion` callback. On completion it emits a `SYSTEM` execution event and broadcasts via SSE/WebSocket.

### Data model

Chat entities form a simple hierarchy. **Tenant** comes from configuration (region folder), not a dedicated store.

```
Tenant
 └── Session
      ├── Message
      └── Run
           └── ExecutionEvent
```

| Relationship | Cardinality | Link |
|--------------|-------------|------|
| Tenant → Session | 1 : N | `Session.tenantId` |
| Session → Message | 1 : N | `Message.sessionId` |
| Session → Run | 1 : N | `Run.sessionId` |
| Run → Message (trigger) | 1 : 1 | `Run.messageId` (user send) |
| Run → ExecutionEvent | 1 : N | `ExecutionEvent.runId` + `sequenceNumber` |

Each user send creates one **Run** and one user **Message**; the run may later add assistant/human **Messages** and many **ExecutionEvents**. Full field lists, persistence, and ER diagram: **[DATA_MODEL.md](./DATA_MODEL.md)**.

### In-memory stores

| Store | Entity | Purpose |
|-------|--------|---------|
| `ChatSessionStore` | Session | Session metadata |
| `ChatMessageStore` | Message | Conversation transcript |
| `ChatRunStore` | Run | Run metadata (tenant, status, correlation) |
| `ExecutionEventStore` | ExecutionEvent | Append-only run event log |

Redis persists **sessions and messages** only (`ChatRedisPersistence`). Runs and execution events are in-memory and are lost on backend restart.

---

## Chat and run data flow

### 1. UI bootstrap

1. Frontend calls **`GET /api/ui/context`** → `tenantId`, `chatProfiles`, footer labels, `oloVersion`.
2. Frontend calls **`GET /api/tenants`** and **`GET /api/tenants/{tenantId}/sessions`** for the session list.

### 2. New message

1. **`POST /api/sessions/{sessionId}/messages`** with `taskQueue` from the selected preset (`queue` field, e.g. `ask`).
2. Backend creates a `runId`, stores the user message, builds `WorkflowInput`, calls `RunService.startWorkflow`.
3. `SdkWorkflowRunner` → `TemporalClient.startChatWorkflow(...)` on the requested task queue.
4. Response includes `runId` for SSE/WebSocket subscription.

### 3. Live events

| Channel | Mechanism |
|---------|-----------|
| **SSE** | `GET /api/runs/{runId}/events` — catch-up then live stream |
| **WebSocket** | Connect to `/ws`, send `{ "type": "SUBSCRIBE_RUN", "runId": "…" }` |
| **Worker → BE** | `POST /api/runs/{runId}/events` with execution payload |

`RunEventBroadcaster` pushes new events to all SSE emitters and subscribed WebSocket sessions for that run.

### 4. Human input

When a workflow waits for human approval, the UI calls **`POST /api/runs/{runId}/human-input`**. `RunService` signals the workflow via `WorkflowRunner.signalHumanInput(...)`.

### 5. Workflow completion

Temporal worker completes; SDK callback in `RunServiceImpl` records final response, emits completion event, and may persist the assistant message to Redis.

---

## Temporal integration

The backend depends on **olo-temporal-sdk** only through:

- `TemporalClient` (wired in `DemoConfig`)
- `WorkflowRunner` / `SdkWorkflowRunner`
- `WorkflowRunCompletion`

Workflow **type** and default **queue** come from `workflow.json` in the active region. Per-message **task queue** comes from the chat preset (`ask`, `fast`, etc.) sent by the frontend.

Workers (separate deployment) must:

1. Register workflow type `olo` (or value from `workflowType`)
2. Poll task queues matching preset `queue` fields
3. POST events to `{OLO_CHAT_CALLBACK_BASE_URL}/api/runs/{runId}/events`

See **olo-temporal-sdk/docs** for client API and design rationale.

---

## Failure and recovery

Happy-path flows above assume Temporal, Redis, workers, and the network are healthy. For production, see **[FAILURE_RECOVERY.md](./FAILURE_RECOVERY.md)**:

- Temporal unavailable at start or signal
- Redis down or backend restart (runs/events are in-memory only)
- Worker crash / missing callbacks
- Duplicate worker callbacks (**409** idempotency)
- WebSocket reconnect and SSE catch-up behavior

---

## Security and tenancy

| Mechanism | Behavior |
|-----------|----------|
| **JWT (REST)** | `JwtTenantIdDecoder` reads `tenantId` from `Authorization: Bearer` on UI context and related calls |
| **JWT (WebSocket)** | Handshake validates Bearer or `accessToken` query param when `olo.ws.jwt.required=true` |
| **Default tenant** | When no JWT, uses first region under `olo.configuration.dir` |
| **WS subscribe** | `SUBSCRIBE_RUN` checks run tenant against session tenant |

Dev/demo default: `olo.ws.jwt.required=false`.

---

## Docker and deployment

### Image build

Multi-stage **Dockerfile**:

1. **Builder** — Gradle `bootJar` with vendored `olo-definition`, `olo-workflow-input`, `olo-temporal-sdk`
2. **Runtime** — JRE 21 Alpine, `app.jar`, `/app/olo-configuration`

Default env: `OLO_CONFIGURATION_DIR=/app/olo-configuration`, port **7080**.

### Compose variants

| File | Services |
|------|----------|
| `docker-compose.dev.yml` | redis, postgres, temporal, olo-backend |
| `docker-compose.demo.yml` | Demo stack |
| `docker-compose.prod.yml` | Production backend |

### CI publish

`.github/workflows/docker-build.yml` builds and pushes:

- `docker.io/<user>/olo:<version>` and `:latest`
- `ghcr.io/<repo>/olo:<version>` and `:latest`

Version is read from `build.gradle`.

### Pairing with olo-chat in Docker

When frontend and backend run in **separate containers**, **olo-chat** nginx proxies `/api` and `/ws` to the backend using runtime **`OLO_BACKEND_URL`** (e.g. `http://olo:7080` on the Docker network). The browser only talks to the frontend port.

---

## Key configuration properties

| Property | Env override | Purpose |
|----------|--------------|---------|
| `server.port` | `SERVER_PORT` | HTTP port (7080) |
| `olo.configuration.dir` | `OLO_CONFIGURATION_DIR` | Workflow JSON root |
| `olo.temporal.target` | `OLO_TEMPORAL_TARGET` | Temporal gRPC address |
| `olo.chat.callback-base-url` | `OLO_CHAT_CALLBACK_BASE_URL` | Worker event callback base |
| `olo.cache.host` / `port` | `OLO_CACHE_*` | Redis |
| `olo.ws.jwt.required` | `OLO_WS_JWT_REQUIRED` | WebSocket auth |
| `olo.version` | `OLO_VERSION` | Shown in UI context |
| `olo.resource.upload.base-dir` | `OLO_RESOURCE_UPLOAD_BASE_DIR` | Upload storage path |

Full defaults: `src/main/resources/application.properties` and `.env.example`.

---

## Related docs

- [DATA_MODEL.md](./DATA_MODEL.md) — Entity relationships, fields, persistence
- [FAILURE_RECOVERY.md](./FAILURE_RECOVERY.md) — Degraded behavior and production gaps
- [README.md](./README.md) — Overview, quick start, Docker
- [olo-temporal-sdk/docs/ARCHITECTURE.md](../olo-temporal-sdk/docs/ARCHITECTURE.md)
- [olo-temporal-sdk/docs/DESIGN.md](../olo-temporal-sdk/docs/DESIGN.md)
- [olo-chat/docs/ARCHITECTURE.md](../../olo-chat/docs/ARCHITECTURE.md) — Frontend architecture
