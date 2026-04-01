# Chat BE — Example Payloads for All APIs

Simple request/response examples in one place. Use these in Swagger, curl, or tests.

---

## Tenants and queues

### GET /api/tenants

No request body. Used by the UI to populate the tenant dropdown.

**Response (200)** — list of tenants (default from config, then Redis-discovered when Redis is available). If Redis is unavailable, returns at least the default tenant; no 500.

```json
[
  { "id": "2a2a91fb-f5b4-4cf0-b917-524d242b2e3d", "name": "Default" }
]
```

Default tenant id comes from **`OLO_DEFAULT_TENANT_ID`** / `olo.default-tenant-id` (e.g. `2a2a91fb-f5b4-4cf0-b917-524d242b2e3d`). Additional tenant ids from **`OLO_TENANT_IDS`** and from Redis keys matching `*:olo:kernel:config:*` (Redis via **`OLO_CACHE_HOST`** / **`OLO_CACHE_PORT`**). See [.env.example](../.env.example) for all env vars.

---

### GET /api/tenants/{tenantId}/queues

No request body. **Path:** `tenantId`. Returns queue names for Redis keys `<tenantId>:olo:kernel:config:*`. Shown under Chat and RAG in the UI.

**Response (200)** — array of queue name strings. Empty if Redis is unavailable or no keys for this tenant.

```json
[
  "olo-chat-queue-oolama:1.0",
  "olo-rag-queue-oolama:1.0"
]
```

---

### GET /api/tenants/{tenantId}/queues/{queueName}/config

No request body. **Path:** `tenantId`, `queueName`. Returns the JSON value stored at Redis key `<tenantId>:olo:kernel:config:<queueName>`. Used for the Conversation pipeline dropdown (e.g. `pipelines` array).

**Response (200)** — JSON object (queue config). Empty object if key missing or Redis unavailable.

```json
{
  "pipelines": ["default", "rag"],
  "model": "gpt-4"
}
```

---

## Sessions

### POST /api/sessions

**Request**

```json
{
  "tenantId": "2a2a91fb-f5b4-4cf0-b917-524d242b2e3d"
}
```

**Response (200)**

```json
{
  "sessionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

---

### GET /api/sessions/{sessionId}

No request body. **Path:** `sessionId` (UUID from create session).

**Response (200)**

```json
{
  "sessionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "tenantId": "2a2a91fb-f5b4-4cf0-b917-524d242b2e3d",
  "createdAt": 1709123456789
}
```

---

### POST /api/sessions/{sessionId}/messages

**Path:** `sessionId` (from create session).

**Request**

```json
{
  "content": "Search news about Tesla",
  "model": "gpt-4",
  "temperature": 0.7,
  "ragEnabled": false,
  "taskQueue": "olo-chat-queue-oolama-debug"
}
```

**Response (200)**

```json
{
  "messageId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "runId": "c3d4e5f6-a7b8-9012-cdef-123456789012"
}
```

---

### GET /api/sessions/{sessionId}/messages

No request body. **Path:** `sessionId`.

**Response (200)** — array of messages

```json
[
  {
    "messageId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
    "sessionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "role": "user",
    "content": "Search news about Tesla",
    "runId": "c3d4e5f6-a7b8-9012-cdef-123456789012",
    "createdAt": 1709123456790
  }
]
```

---

## Runs

### POST /api/runs

**Request**

```json
{
  "tenantId": "2a2a91fb-f5b4-4cf0-b917-524d242b2e3d",
  "input": {
    "type": "chat",
    "message": "Create a detailed investment report for Tesla."
  },
  "taskQueue": "olo-chat-queue-oolama-debug",
  "workflowVersion": "1.0",
  "modelVersion": "gpt-4",
  "plannerVersion": "1.0"
}
```

**Response (200)**

```json
{
  "runId": "c3d4e5f6-a7b8-9012-cdef-123456789012"
}
```

---

### GET /api/runs/{runId}

No request body. **Path:** `runId` (from create run or send message).

**Response (200)**

```json
{
  "runId": "c3d4e5f6-a7b8-9012-cdef-123456789012",
  "sessionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "messageId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "status": "running",
  "createdAt": 1709123456789,
  "correlationId": "d4e5f6a7-b8c9-0123-def0-234567890123",
  "workflowVersion": "1.0",
  "modelVersion": "gpt-4",
  "plannerVersion": "1.0"
}
```

---

### GET /api/runs/{runId}/events

**SSE stream.** No request body. **Path:** `runId`. Response is `text/event-stream`; each event is one `OloExecutionEvent` (JSON).

**Example event (one of many in the stream)**

```json
{
  "eventVersion": 1,
  "runId": "c3d4e5f6-a7b8-9012-cdef-123456789012",
  "nodeId": "root",
  "parentNodeId": null,
  "nodeType": "SYSTEM",
  "status": "STARTED",
  "eventType": "NODE_STARTED",
  "timestamp": 1709123456790,
  "sequenceNumber": 0,
  "correlationId": "d4e5f6a7-b8c9-0123-def0-234567890123",
  "input": { "type": "chat", "message": "Search news about Tesla" },
  "output": null,
  "metadata": { "tenantId": "2a2a91fb-f5b4-4cf0-b917-524d242b2e3d" }
}
```

---

### POST /api/runs/{runId}/events

**Executor callback only.** **Path:** `runId`. `sequenceNumber` required; duplicate returns 409.

**Request**

```json
{
  "sequenceNumber": 1,
  "eventVersion": 1,
  "eventType": "NODE_STARTED",
  "correlationId": "d4e5f6a7-b8c9-0123-def0-234567890123",
  "nodeId": "root",
  "parentNodeId": null,
  "nodeType": "SYSTEM",
  "status": "STARTED",
  "input": { "message": "Search news about Tesla", "type": "chat" },
  "output": null,
  "metadata": {}
}
```

**Response:** 204 No Content (success), 400 (missing sequenceNumber), 409 (duplicate sequence).

---

### POST /api/runs/{runId}/human-input

**Path:** `runId` (run must be in `waiting_human`).

**Request**

```json
{
  "approved": true,
  "message": "Looks good, approved."
}
```

**Response:** 204 No Content.

---

## WebSocket (run events)

Full doc: **[WEBSOCKET.md](WEBSOCKET.md)**.

**Endpoint:** `ws://localhost:7080/ws` (or `wss://` in production).

**Authentication:** Send `Authorization: Bearer <JWT>` on the handshake. The backend validates the token, extracts `tenantId` (e.g. from `tenantId` or `sub` claim), and stores it in the session. Missing or invalid token → 401, connection not established. On `SUBSCRIBE_RUN`, the backend rejects with ERROR `NOT_FOUND` if the run does not exist, and `FORBIDDEN` if the run’s tenantId does not match the session’s tenantId.

Connect once, then subscribe to one or more runs by sending a text message (JSON).

### Subscribe to a run

**Client sends (text frame):**

```json
{
  "type": "SUBSCRIBE_RUN",
  "runId": "c3d4e5f6-a7b8-9012-cdef-123456789012"
}
```

**Backend:** Registers this session → runId. Sends all existing events for that run (catch-up), then pushes each new event as it is appended. Each event is one JSON text frame (same shape as SSE event).

**Example RUN_EVENT frame (envelope):**

```json
{
  "type": "RUN_EVENT",
  "payload": {
    "eventVersion": 1,
    "runId": "c3d4e5f6-a7b8-9012-cdef-123456789012",
    "nodeId": "root",
    "parentNodeId": null,
    "nodeType": "SYSTEM",
    "status": "STARTED",
    "eventType": "NODE_STARTED",
    "timestamp": 1709123456790,
    "sequenceNumber": 0,
    "correlationId": "d4e5f6a7-b8c9-0123-def0-234567890123",
    "input": { "type": "chat", "message": "Search news about Tesla" },
    "output": null,
    "metadata": { "tenantId": "2a2a91fb-f5b4-4cf0-b917-524d242b2e3d" }
  }
}
```

Optional: send `{ "type": "PING" }`; server responds with `{ "type": "PONG" }`. You can send multiple `SUBSCRIBE_RUN` messages (e.g. different runIds) on the same connection; each subscription gets catch-up + live events for that run. See [WEBSOCKET.md](WEBSOCKET.md) for reconnection, ordering, and horizontal scaling.

---

## Health

### GET /api/health

No request body. **Response (200):** `"OK"` (plain text).
