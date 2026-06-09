<!--
Copyright (c) 2026 Olo Labs
SPDX-License-Identifier: Apache-2.0
-->

# Failure and recovery

How the **olo** backend and **olo-chat** UI behave when dependencies fail, processes restart, or messages are duplicated. This complements the happy-path flows in [ARCHITECTURE.md](./ARCHITECTURE.md).

**Current posture:** demo-oriented in-memory run/event stores with best-effort Redis persistence. Production deployments should treat the gaps called out below as operational requirements.

---

## Summary matrix

| Scenario | Backend survives? | Chat transcript | Run / events | UI recovery |
|----------|-------------------|-----------------|--------------|-------------|
| Temporal unavailable at **start** | Yes | User message saved | Run + `SYSTEM STARTED` created; workflow not started | `POST .../messages` → **500**; orphan run |
| Temporal unavailable at **runtime** | Yes | Yes | `SYSTEM FAILED` via async callback | SSE/WS event + `getRun` poll |
| Redis unavailable | Yes (in-memory) | In-memory only; lost on restart | Unchanged | Sessions list may differ after restart |
| Worker crash / no callbacks | Yes | Yes | Run stays `running`; no new events | Stuck UI unless poll/timeouts added |
| Backend restart mid-run | Yes (after boot) | Redis: survives; memory: lost | **Runs and events lost** | Health poll reconnects; run 404; localStorage partial |
| Duplicate worker callback | Yes | Yes | **409** on duplicate `sequenceNumber` | Deduped in UI store |
| WebSocket disconnect | Yes | Yes | Unchanged | Auto-reconnect; **re-subscribe gap** (see below) |

---

## Temporal unavailable

### At workflow start (`POST /api/sessions/{sessionId}/messages`)

**Order of operations today:**

1. User **Message** and **Run** are written to memory (and Redis when configured).
2. `SYSTEM` / `STARTED` **ExecutionEvent** is appended.
3. `RunService.startWorkflow` → `TemporalClient.startChatWorkflow(...)`.

If step 3 throws (Temporal down, wrong target, no worker on queue), the exception propagates and Spring returns **500**. The client still has a persisted user message and run id in the backend, but **no Temporal workflow** was started.

```
Client                    Backend                         Temporal
  |-- POST message ------>|                                 |
  |                       | save Message, Run, STARTED event |
  |                       |-- start workflow --------------X| (connection refused)
  |<-- 500 ----------------|                                 |
```

**User impact:** olo-chat shows send error (`setSending(false)`). The optimistic user bubble may remain; `listMessages` may show the real user message with a `runId`.

**Recovery today:** User sends again (new run) or refreshes and inspects session. There is no automatic rollback of the orphan run.

**Production recommendations:**

- Monitor Temporal target (`OLO_TEMPORAL_TARGET`) and worker task-queue health.
- Consider transactional outbox or start-before-persist ordering so a failed start does not leave orphan runs.
- Alert on 500 rate for `POST .../messages`.

### At workflow completion (async)

`SdkWorkflowRunner` awaits the Temporal result on a background executor. Failure invokes `WorkflowRunCompletion.onFailed`, which appends:

- `nodeType=SYSTEM`, `status=FAILED`, `output.error`

That event is broadcast on SSE/WebSocket like any other event.

### Human-input signal (`POST /api/runs/{runId}/human-input`)

`signalHumanInput` has no local try/catch. If Temporal is unreachable, the call fails with **500** after the controller already set run status back to `running`.

**Production recommendations:** Retry with backoff on signal; surface a clear UI error; verify workflow still exists in Temporal before signaling.

---

## Redis unavailable

### Startup

Spring Boot auto-configures Redis from `olo.cache.host` / `port`. If Redis is required at startup and is down, the process may fail to boot depending on connection settings. To run **without Redis**, uncomment in `application.properties`:

```properties
# spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,...
```

### Runtime

`ChatRedisPersistence` treats Redis as optional:

- `redisTemplate == null` → all methods no-op or return `null`.
- Redis errors are caught, logged at **debug**, and **do not fail** the request.

| Operation | If Redis fails |
|-----------|----------------|
| `saveSession` / `appendMessage` | Logged; in-memory store still updated |
| `listSessionsByTenant` | Returns `null` → controller falls back to in-memory |
| `listMessages` | Returns `null` → controller falls back to in-memory |

**User impact:** Chat works for the lifetime of the backend process. After **backend restart**, sessions and messages that were never written to Redis are **gone**.

**Recovery:** Restore Redis; new sessions persist again. No automatic backfill from memory.

**Production recommendations:**

- Run Redis with persistence (AOF/RDB) and health checks.
- Treat Redis as required for any deployment that must survive backend rolling restarts.
- Monitor Redis latency and connection errors.

---

## Worker crashes or stops sending callbacks

Workers POST execution events to:

`POST {OLO_CHAT_CALLBACK_BASE_URL}/api/runs/{runId}/events`

If a worker crashes mid-run:

- Temporal may retry activities/workflows per its own policies (outside this backend).
- The backend only learns progress through **callbacks** and the **Temporal result future**.
- If callbacks stop but Temporal still runs, `ChatRunStore` status can remain **`running`** indefinitely.
- No backend-side worker heartbeat or stall detection exists today.

**User impact:** olo-chat may show a stuck “sending” state. Mitigations already in the UI:

- Poll **`GET /api/runs/{runId}`** on each event (and after restore) for `completed` / `failed`.
- Poll **`GET /api/runs/{runId}/response`** for assistant text.
- Fall back to **SSE** when WebSocket is not open.

**Recovery:** Restart worker; ensure `OLO_CHAT_CALLBACK_BASE_URL` is reachable from the worker network. Temporal may replay or continue the workflow; duplicate callbacks are handled (see below).

**Production recommendations:**

- Run workers with restart policy and queue depth alerts.
- Add run timeout / stale-run sweep in the backend for production.
- Verify callback URL from inside the worker container (`http://olo:7080` vs host-mapped port).

---

## Backend restart during an active run

See also [DATA_MODEL.md](./DATA_MODEL.md) persistence table.

| Data | After restart |
|------|----------------|
| **Sessions / messages** (Redis) | Still available |
| **Sessions / messages** (memory only) | Lost |
| **Runs** (`ChatRunStore`) | **Lost** → `GET /api/runs/{runId}` **404** |
| **Execution events** (`ExecutionEventStore`) | **Lost** → SSE/WS catch-up empty |
| **Temporal workflow** | May still be running (`run-{runId}`) |

```
Before restart          After restart
─────────────          ─────────────
Run + events in RAM    GET /runs/{id} → 404
Redis: messages OK     listMessages → OK (transcript)
Temporal: running      Backend no longer tracks run
```

**UI behavior (olo-chat):**

- **`useBackendReachable`** polls `GET /api/health` every ~12s; UI recovers when backend is back without full page reload.
- **`localStorage`** keeps up to **200** workflow events per `runId` (client-side); restored on refresh via `hydrate`.
- **`sessionStorage`** stores last active `runId` per session for restore attempt.
- After restart, backend catch-up for that `runId` is empty; user sees cached events only until they send a new message.

**Recovery today:** User starts a **new message** (new run). Old Temporal workflows may complete orphaned (callbacks hit backend but run record is gone unless worker recreates state).

**Production recommendations:**

- Persist runs and execution events to a database.
- On startup, optionally reconcile with Temporal workflow visibility API.
- Use sticky sessions or externalize event log for SSE/WS catch-up after restart.

---

## Duplicate worker callbacks

Idempotency key: **`(runId, sequenceNumber)`** in `ExecutionEventStore`.

| Request | Result |
|---------|--------|
| First `POST .../events` with sequence `N` | **204**; event stored and broadcast |
| Duplicate same `N` | **`DuplicateSequenceException`** → **409 Conflict** |
| Missing `sequenceNumber` | **400 Bad Request** |

Workers should treat **409 as success** (safe retry). The event is not stored twice; SSE/WebSocket clients are not double-fed from the backend.

**UI dedupe (olo-chat):** `runEventsStore.addEvent` uses `workflowEventDedupeKey` (`runId:seq:N`) so hydrate + WebSocket catch-up replay does not duplicate timeline entries.

---

## WebSocket disconnect and reconnect

### Backend (`/ws`)

| Behavior | Detail |
|----------|--------|
| Connection drop | `RunEventWebSocketRegistry` removes session on close |
| Reconnect | New TCP connection; no server-side subscription memory |
| `SUBSCRIBE_RUN` | Required on each new connection; triggers **catch-up** from `ExecutionEventStore` then live events |
| PING/PONG | Client `PING` → server `PONG` (liveness only) |
| Closed session send | Registry unsubscribes failed sessions on `IOException` |

### Frontend (olo-chat)

| Component | Behavior |
|-----------|----------|
| `wsSingleton` | Single shared socket; creates new `WebSocket` when URL/token changes |
| `useWebSocketLiveness` | On `onclose`, schedules reconnect after `VITE_WS_PING_INTERVAL_SEC` (default **10s**) |
| `subscribeToRun` | Sent after **sendMessage** or session restore when socket is open |
| `runEventsStore` | Dedupes events by `sequenceNumber` on replay |

**Important gap:** Reconnect logic reopens the socket and resumes PING/PONG but does **not** automatically re-send `SUBSCRIBE_RUN` for the active run. If WS drops mid-run:

- Live events may stop arriving over WS.
- SSE is used only when WS was not open at send time.
- User may need **refresh** (restore path re-subscribes) or rely on **`getRun` / `getRunResponse` polling** triggered from existing callbacks.

**Recovery paths today:**

1. Refresh page → `sessionStorage` active run + `localStorage` events + `SUBSCRIBE_RUN` on restore.
2. Wait for `getRun` poll to mark completed/failed (if events still arrive via SSE path — only if SSE subscription active).
3. Send a new message (new run).

**Production recommendations:**

- Re-send `SUBSCRIBE_RUN` for `lastOutboundRunId` on every WS `onopen`.
- Or prefer SSE for run events with reconnect wrapper.
- Terminate TLS and proxy timeouts (`proxy_read_timeout` on nginx) suitable for long runs.

### SSE disconnect

`GET /api/runs/{runId}/events` uses `SseEmitter(0L)` (no timeout). On disconnect, the emitter is unregistered. Client must open a **new** SSE fetch to resume; `subscribeWithCatchUp` replays all in-memory events for that run.

If the backend restarted, catch-up is **empty** for that run.

---

## Health and degraded-mode UX

| Signal | Endpoint | Used by |
|--------|----------|---------|
| Backend up | `GET /api/health` → `OK` | olo-chat `useBackendReachable` (12s poll) |
| Run status | `GET /api/runs/{runId}` | ChatView poll fallback |
| Assistant text | `GET /api/runs/{runId}/response` | ChatView when events omit MODEL output |
| Transcript | `GET /api/sessions/{id}/messages` | Reload messages on `SYSTEM COMPLETED` |

When health fails, Chat shows a **disconnected** placeholder until the next successful poll.

---

## Operational checklist (production)

1. **Temporal** — HA cluster; workers per task queue; alert on start/signal error rate.
2. **Redis** — Required for session survival across backend restarts; persistence enabled.
3. **Callback URL** — `OLO_CHAT_CALLBACK_BASE_URL` must resolve from worker containers.
4. **Workers** — Auto-restart; idempotent callbacks with stable `sequenceNumber`; handle **409**.
5. **Backend** — Plan DB-backed runs/events; rolling restart runbook; optional Temporal reconciliation.
6. **Frontend** — Fix WS re-subscribe on reconnect; consider run timeout UX.
7. **Load balancer** — WebSocket and SSE sticky behavior or stateless catch-up from durable event log.

---

## Related docs

- [ARCHITECTURE.md](./ARCHITECTURE.md) — Happy-path flows
- [DATA_MODEL.md](./DATA_MODEL.md) — What is persisted where
- [README.md](./README.md) — Configuration and Docker
- [olo-temporal-sdk/docs/ARCHITECTURE.md](../olo-temporal-sdk/docs/ARCHITECTURE.md) — Temporal client boundary
