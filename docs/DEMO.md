# Olo Demo: Full Chat Flow (Planner → Tool → Model → Human → Final)

This gets the **demo story** working end-to-end:

1. User types: **"Search news about Tesla."** → UI shows **PLANNER** → **TOOL** → **MODEL** → Assistant responds.
2. User types: **"Send this for approval."** → **HUMAN** step appears → User clicks approve → Workflow resumes → Final message appears.

---

## Prerequisites

- **Java 21**
- **Gradle** (wrapper in repo: `gradlew` / `gradlew.bat`)
- **Maven** (only for the executor: `olo-executor`)
- **Temporal Server** running locally (default `localhost:7233`).  
  Example with Docker:  
  `docker run -d -p 7233:7233 temporalio/auto-setup`

---

## 1. Build

From repo root:

```bash
# SDK + Backend (Gradle): builds olo-sdk and backend in one go
./gradlew build
# Or run: ./gradlew bootRun

# olo-worker-input (used by executor) — publish to Maven local
./gradlew :olo-worker-input:publishToMavenLocal

# Executor (separate process, Maven — runs workflows; not part of backend)
cd olo-executor && mvn compile && cd ..
```

---

## 2. Run Temporal Server

If not already running:

```bash
docker run -d -p 7233:7233 temporalio/auto-setup
```

---

## 3. Run Chat Backend

```bash
./gradlew bootRun
```

Backend will be at **http://localhost:7080**.

- **Task queue**: Comes from the frontend (request body `taskQueue`). If omitted, backend uses `OLO_TEMPORAL_TASK_QUEUE` (default `olo-chat`). Executor must use the same task queue.
- **Workflow type**: Default is **`OloKernelWorkflow`**. Override with env **`OLO_WORKFLOW_TYPE`**. The worker must register the workflow under the same type name.
- **Swagger UI**: **http://localhost:7080/swagger-ui.html** (all chat APIs). Disable in production with **`OLO_SWAGGER_UI_ENABLED=false`** (see [.env.example](../.env.example)).
- **OpenAPI JSON**: http://localhost:7080/v3/api-docs
- **Tenants / queues**: GET /api/tenants returns the default tenant (`OLO_DEFAULT_TENANT_ID`) and optionally Redis-discovered tenants. GET /api/tenants/{tenantId}/queues requires Redis (`OLO_CACHE_HOST` / `OLO_CACHE_PORT`). If Redis is not configured, those endpoints return [] or {}; no 500.
- All requests to `/api/*` are logged (method, URI, and body for POST/PUT/PATCH).

**Configuration:** Key env vars (or [.env.example](../.env.example)): `OLO_CACHE_HOST`, `OLO_CACHE_PORT`, `OLO_TEMPORAL_TARGET`, `OLO_CHAT_CALLBACK_BASE_URL`, `OLO_DEFAULT_TENANT_ID`, `OLO_SWAGGER_UI_ENABLED`. For Docker Compose (backend + Redis + Temporal in one stack), see [DOCKER_COMPOSE.md](DOCKER_COMPOSE.md).

---

## 4. Run Executor (separate terminal)

```bash
cd olo-executor
mvn exec:java -Dexec.mainClass="org.olo.worker.OloExecutorMain"
```

The executor connects to Temporal and polls task queue **olo-chat**. It runs workflows and activities and POSTs events back to the backend at `olo.chat.callback-base-url` (default http://localhost:7080). It is a separate process, not part of the backend.

---

## 5. Run the Demo (API)

### Create session

```bash
curl -s -X POST http://localhost:7080/api/sessions -H "Content-Type: application/json" -d "{\"tenantId\":\"2a2a91fb-f5b4-4cf0-b917-524d242b2e3d\"}"
# → {"sessionId":"<SESSION_ID>"}
```

### First message: "Search news about Tesla"

```bash
curl -s -X POST http://localhost:7080/api/sessions/<SESSION_ID>/messages \
  -H "Content-Type: application/json" \
  -d "{\"content\":\"Search news about Tesla.\"}"
# → {"messageId":"...","runId":"<RUN_ID>"}
```

**Stream events (SSE)** in another terminal:

```bash
curl -N http://localhost:7080/api/runs/<RUN_ID>/events
```

You should see events: **SYSTEM** STARTED → **PLANNER** COMPLETED → **TOOL** COMPLETED → **MODEL** COMPLETED → **SYSTEM** COMPLETED.

### Second message: "Send this for approval"

```bash
curl -s -X POST http://localhost:7080/api/sessions/<SESSION_ID>/messages \
  -H "Content-Type: application/json" \
  -d "{\"content\":\"Send this for approval.\"}"
# → {"messageId":"...","runId":"<RUN_ID_2>"}
```

Stream events for this run:

```bash
curl -N http://localhost:7080/api/runs/<RUN_ID_2>/events
```

You will see: **PLANNER** → **HUMAN** WAITING. Then **approve**:

```bash
curl -s -X POST http://localhost:7080/api/runs/<RUN_ID_2>/human-input \
  -H "Content-Type: application/json" \
  -d "{\"approved\":true}"
```

SSE stream will then show **HUMAN** COMPLETED → **MODEL** COMPLETED → **SYSTEM** COMPLETED.

---

## 6. UI (olo-chat)

When the frontend is wired to these APIs:

- **POST /api/sessions** → create session.
- **POST /api/sessions/{sessionId}/messages** with `content` → get `runId`.
- **GET /api/runs/{runId}/events** (SSE) → show **PLANNER**, **TOOL**, **MODEL**, **HUMAN** steps in real time.
- **POST /api/runs/{runId}/human-input** with `{"approved":true}` when user clicks approve.

That gives you the full demo: planner + tool + model + optional human approval, with live steps in the UI.
