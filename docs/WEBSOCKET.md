# WebSocket — Run Events

Chat BE exposes a **native WebSocket** at `/ws` so clients can subscribe to run events with full control over one long-lived connection. Same event stream as SSE, different transport.

---

## Endpoint

| Environment | URL |
|-------------|-----|
| Local       | `ws://localhost:7080/ws` |
| Production  | `wss://your-host/ws`     |

Default port is **7080** (see `server.port` in `application.properties`).

---

## Authentication (handshake)

When JWT is **required** (`olo.ws.jwt.required=true`): the client must send a valid JWT; missing/invalid token → 401.

When JWT is **not required** (`olo.ws.jwt.required=false`, default for local/Swagger testing): the client may connect without `Authorization`. The backend assigns the session the **default tenant** (`OLO_DEFAULT_TENANT_ID` / `olo.default-tenant-id`, e.g. `2a2a91fb-f5b4-4cf0-b917-524d242b2e3d`), which is always available. Use this tenantId when creating sessions/runs in Swagger so you can subscribe over WebSocket without a token.

- **Client:** send a valid JWT when required. The backend accepts the token in either form:
  - **Header:** `Authorization: Bearer <JWT>` (preferred when the client can set headers).
  - **Query param:** `?accessToken=<JWT>` or `?token=<JWT>` (for browser clients; the `WebSocket` API cannot set custom headers). The value is URL-decoded.
- If a token is sent, the backend validates it, extracts `tenantId` (from `tenantId` or `sub` claim), and stores it in the WebSocket session. When JWT is required and missing/invalid, handshake is **rejected** (401). When JWT is not required, missing/invalid token causes the backend to assign the **default tenant**.

Demo/development: the default implementation **decodes** the JWT payload only (no signature verification). Production should set `olo.ws.jwt.required=true` and use proper JWT verification.

### Token expiry during active connection

- **JWT is validated only at handshake.** The backend does not re-validate the token while the WebSocket is open.
- **The backend does not automatically close the WebSocket when the token expires.** If a token expires 1 hour after connect and the connection stays open for 5 hours, the connection continues to receive events for its subscriptions; the session’s tenantId remains whatever was set at handshake.
- **On reconnect, a valid (non-expired) token is required** (when `olo.ws.jwt.required=true`). The client must open a new connection with a fresh JWT to continue; the previous connection’s lifetime is independent of token expiry.

This avoids ambiguity: long-lived connections are not torn down due to expiry; reconnects always require a valid token.

---

## Flow

1. **Client** opens a WebSocket connection to `/ws` with `Authorization: Bearer <JWT>`.
2. **Backend** validates the token, extracts `tenantId`, stores it in the session; handshake succeeds or returns 401.
3. **Client** sends a subscription message (JSON text frame): `{ "type": "SUBSCRIBE_RUN", "runId": "<runId>" }`.
4. **Backend** checks that the run exists and that `run.tenantId` matches the session’s tenantId; if not, sends an ERROR frame (see below) and does not subscribe.
5. If the check passes, the backend registers this session for that `runId`, sends all **existing** events (catch-up), then pushes each **new** event as it is appended.
6. Each event is one **text frame** (JSON object). Same shape as [SSE events](API_PAYLOADS.md#get-apirunsrunidevents).

---

## Subscription model

### Subscribe to a run

Send a **text message** (JSON) on the open WebSocket:

```json
{
  "type": "SUBSCRIBE_RUN",
  "runId": "c3d4e5f6-a7b8-9012-cdef-123456789012"
}
```

- **Backend** looks up the run; if not found, sends ERROR `NOT_FOUND` and does not subscribe.
- **Backend** checks tenant: if `run.tenantId` does not match the tenantId stored in the WebSocket session at handshake, sends ERROR `FORBIDDEN` and does not subscribe.
- Otherwise **backend** stores: this session is subscribed to `runId`, immediately sends every event already stored for that run (ordered by `sequenceNumber`), then sends each new event as it is appended (same connection).

You can send **multiple** `SUBSCRIBE_RUN` messages on the same connection (e.g. different `runId`s). Each subscription is subject to the same run-exists and tenant checks; each valid subscription gets its own catch-up + live stream.

### Session isolation (per connection)

Subscriptions are **isolated per connection**. One connection’s subscriptions do not affect another’s; disconnecting one connection does not affect others.

- **Example:** Connection 1 subscribes to run A and run B. Connection 2 subscribes to run A. When Connection 1 disconnects, Connection 2 continues to receive events for run A. When Connection 2 disconnects, Connection 1 (if still open) continues to receive events for runs A and B.
- The backend does not share subscription state between connections. Each WebSocket session has its own set of subscribed runIds; closing that session removes only that session from the registry.

Stating this explicitly removes assumptions and prevents confusion in multi-tab or multi-client scenarios.

### Ordering guarantee

- Events are sent **strictly ordered by `sequenceNumber`** **per run**. The backend guarantees in-order delivery per run on a single connection (catch-up then live, in sequence).
- **No global ordering guarantee exists across multiple run subscriptions on the same connection.** If you are subscribed to run A and run B, events for A (e.g. sequence 1, 2, 3) and for B (1, 2, 3) may **interleave** in the stream. Frames might appear as A1, B1, A2, B2, A3, B3 or any other interleaving. Clients must order and deduplicate **per run** using `(runId, sequenceNumber)`.
- Delivery is **at-least-once**; clients **must deduplicate by `(runId, sequenceNumber)`**.

**Why this matters:** UI and consumer code that assume a single global order across runs can introduce race conditions. Defining per-run ordering and no cross-run order removes that risk. WebSocket also does not protect you from duplicate sends, retry scenarios, or cluster replays; deduplicating on the client avoids double-processing.

### Unsubscribe

Close the WebSocket, or simply stop sending. On connection close, the backend removes the session from all run subscriptions.

### Reconnection behavior

- **On reconnect, the client must re-send `SUBSCRIBE_RUN`** for each run it cares about.
- The backend **does not maintain subscription state across connections**; subscriptions are per connection.
- **Catch-up ensures no event loss:** after reconnecting and re-subscribing, the client receives all events for that run (in order), then live events.

### Backpressure and slow clients

WebSocket flooding is a real concern when many runs or high event rates are involved. Documented expectations (even if not all are implemented yet):

- **The backend does not buffer unbounded events per session.** Outgoing frames are subject to natural TCP/WebSocket flow control; the backend is not required to queue an unlimited number of events for a single slow consumer.
- **Slow clients may be disconnected.** If a client cannot consume events promptly, the server may close the connection (e.g. due to write timeouts, buffer limits, or policy). Clients should not assume indefinite buffering.
- **Clients must process events promptly.** To avoid backpressure and disconnection, clients should drain the WebSocket read path and handle (or queue and process) RUN_EVENT frames without undue delay. Subscribing to many high-volume runs on one connection increases the risk of falling behind.

Setting this expectation in the spec avoids surprises and encourages clients to design for prompt consumption and reconnection.

---

## Horizontal scaling

**Current implementation assumes single-instance deployment.**

If Chat BE runs **multiple instances**:

- WebSocket sessions are **local to each instance** (no shared session state).
- An **event publisher** must broadcast via **Redis Pub/Sub** (or equivalent) so that when one instance appends an event, every instance receives it.
- Each instance delivers events **only to its local sessions** subscribed to that runId.
- Without this, events can be “lost” when the instance that appended the event is not the instance holding the client’s WebSocket.

Even if Phase 1 does not implement Redis Pub/Sub, the assumption is documented: **single instance**. Multi-instance requires adding a pub/sub layer and having the broadcaster publish to it and subscribe to it so local sessions still receive events for runs they subscribed to.

---

## Server → client: envelope and frame types

All server→client frames are JSON objects with a **`type`** field for future extensibility (RUN_EVENT, ERROR, PONG, HEARTBEAT, SYSTEM, etc.) without breaking the schema.

### RUN_EVENT

Event payloads are wrapped in an envelope:

```json
{
  "type": "RUN_EVENT",
  "payload": { ... }
}
```

**`payload`** is the execution event object. Fields are product-level only (no workflow ID, task queue, or other internal keys).

| Field (inside `payload`) | Type   | Description |
|--------------------------|--------|-------------|
| eventVersion             | number | Schema version (e.g. 1) |
| runId                    | string | Run this event belongs to |
| nodeId                   | string | Node identifier (e.g. `root`, `planner-0`) |
| parentNodeId             | string \| null | Parent node; null for root |
| nodeType                 | string | `SYSTEM`, `PLANNER`, `MODEL`, `TOOL`, `HUMAN` |
| status                   | string | `STARTED`, `COMPLETED`, `FAILED`, `WAITING` |
| eventType                | string | `NODE_STARTED`, `NODE_COMPLETED`, `NODE_FAILED`, `NODE_WAITING` |
| timestamp                | number | Epoch millis |
| sequenceNumber           | number | Order within run |
| correlationId            | string | Cross-service tracing |
| input                    | object | Node input |
| output                   | object \| null | Node output |
| metadata                 | object | Tenant, etc. |

**Example RUN_EVENT frame:**

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

---

## Client → server messages

| type           | Payload | Effect |
|----------------|---------|--------|
| `SUBSCRIBE_RUN` | `{ "type": "SUBSCRIBE_RUN", "runId": "<runId>" }` | Subscribe this connection to events for `runId`; receive catch-up then live. |
| `PING`         | `{ "type": "PING" }` | Server responds with `{ "type": "PONG" }`. Use to keep connection alive or detect liveness. |

Unknown or invalid messages are **not** ignored. The server sends an **error frame** so the client gets feedback.

### Server → client: ERROR frame

When the message is unknown, invalid JSON, or missing required fields (e.g. `runId` for `SUBSCRIBE_RUN`), the server sends one text frame:

```json
{
  "type": "ERROR",
  "code": "INVALID_MESSAGE",
  "message": "Unknown message type"
}
```

| code              | When |
|-------------------|------|
| `INVALID_MESSAGE` | Unknown `type`, invalid JSON, or message not a JSON object. |
| `MISSING_RUN_ID`  | `SUBSCRIBE_RUN` sent without a non-empty `runId`. |
| `NOT_FOUND`       | `SUBSCRIBE_RUN` with a `runId` that does not exist. |
| `FORBIDDEN`       | `SUBSCRIBE_RUN` for a run whose `tenantId` does not match the tenantId in the WebSocket session (handshake JWT). |

### Server → client: PONG frame

When the client sends `{ "type": "PING" }`, the server responds with:

```json
{
  "type": "PONG"
}
```

Optional: the backend may also send periodic PING (or HEARTBEAT) frames; clients can respond with PONG to avoid idle timeouts.

---

## Testing

### 1. Create a run and get runId

Use Swagger or curl:

- **POST /api/runs** with body `{ "tenantId": "2a2a91fb-f5b4-4cf0-b917-524d242b2e3d", "input": { "type": "chat", "message": "Hello" } }`  
  → response: `{ "runId": "..." }`

Or **POST /api/sessions** then **POST /api/sessions/{sessionId}/messages** → response includes `runId`.

### 2. Connect and subscribe (browser)

The handshake requires `Authorization: Bearer <JWT>`. The JWT payload should include a tenant identifier (e.g. `tenantId` or `sub`). For local testing you can use a small JWT that decodes to `{"tenantId":"2a2a91fb-f5b4-4cf0-b917-524d242b2e3d"}` (or use your auth provider’s token).

**Browser:** The `WebSocket` API cannot set custom headers. Use the query param: `ws://localhost:7080/ws?accessToken=YOUR_JWT` (or `?token=YOUR_JWT`). For local testing with JWT not required, you can use `ws://localhost:7080/ws` without a token (backend assigns the default tenant).

Open DevTools → Console:

```javascript
// With token (required in production): ws://localhost:7080/ws?accessToken=YOUR_JWT
const ws = new WebSocket('ws://localhost:7080/ws');
ws.onopen = () => {
  ws.send(JSON.stringify({ type: 'SUBSCRIBE_RUN', runId: 'YOUR_RUN_ID' }));
};
ws.onmessage = (e) => {
  const msg = JSON.parse(e.data);
  if (msg.type === 'RUN_EVENT') console.log('Event:', msg.payload);
  else if (msg.type === 'PONG') console.log('PONG');
  else if (msg.type === 'ERROR') console.error('Error:', msg);
  else console.log('Message:', msg);
};
ws.onerror = (e) => console.error(e);
ws.onclose = () => console.log('Closed');
```

Replace `YOUR_RUN_ID` with the runId from step 1. You should see catch-up events, then new events as the workflow runs.

### 3. Command-line (e.g. websocat)

If you have [websocat](https://github.com/vi/websocat), you can pass the JWT on the handshake:

```bash
echo '{"type":"SUBSCRIBE_RUN","runId":"YOUR_RUN_ID"}' | websocat -H "Authorization: Bearer YOUR_JWT" ws://localhost:7080/ws
```

Replace `YOUR_JWT` with a JWT whose payload includes `tenantId` (or `sub`) matching the run’s tenant, and `YOUR_RUN_ID` with the runId from step 1.

---

## Comparison with SSE

| | SSE | WebSocket |
|--|-----|-----------|
| **Endpoint** | `GET /api/runs/{runId}/events` | `ws://host/ws` |
| **Subscription** | One run per request (runId in URL) | One connection; send `SUBSCRIBE_RUN` per run |
| **Catch-up** | Yes (then live) | Yes (then live) |
| **Transport** | HTTP long-lived, server-sent only | Full-duplex WebSocket |
| **Multiple runs** | One connection per run | One connection, multiple subscriptions |

Use WebSocket when you want one connection for multiple runs or full control over the client socket. Use SSE when you want a simple GET per run.

---

## Architectural note

The WebSocket layer does **not** interpret execution, simulate workflow, or add replay logic. It only **streams the projection** (events as stored). Chat BE remains a thin orchestration boundary.

---

## Related

- [.env.example](../.env.example) — Env vars (default tenant, JWT, cache). Copy to `.env` to override.
- [API_PAYLOADS.md](API_PAYLOADS.md) — Example payloads (including WebSocket subscribe).
- [DOCKER_COMPOSE.md](DOCKER_COMPOSE.md) — Run backend + Redis + Temporal with Docker.
- [TESTING_WITH_SWAGGER.md](TESTING_WITH_SWAGGER.md) — How to run the backend and test flows.
