# Pipelines, queues, and profiles (recommended layers)

This document describes the **recommended separation of concerns** between **pipeline definitions** (what to run), **queues** (where work executes), and **profiles** (what the chat UI offers as presets). It complements [QUEUE_PIPELINE_REDIS.md](./QUEUE_PIPELINE_REDIS.md).

---

## Why three layers?

| Layer | Role | Contains |
|--------|------|----------|
| **A. Pipelines** | Pure **workflow logic** — steps, tools, models, human gates | Pipeline ids and their **DAG / step definitions** (no Temporal queue names here). |
| **B. Queues** | **Execution / worker routing** — Temporal task queues, worker affinity | Queue ids and **how** work is executed (`workerType`, capacity hints, etc.). |
| **C. Profiles** | **UI binding** — one preset = one `(pipeline, queue)` pair + display strings | What **olo-chat** shows in the composer; **this is the UI contract**. |

Coupling pipeline JSON to queue names inside the same blob makes it hard to reuse one pipeline on several queues or swap workers without editing graph logic. Splitting config keeps **logic**, **execution**, and **presentation** independent.

---

## Recommended Redis layout (per region)

Prefix: `<root>:config:...:<region>` where `<root>` is `olo.cache.root-key` (often `olo`). **region** matches `olo.region` / tenant resolution (see [QUEUE_PIPELINE_REDIS.md](./QUEUE_PIPELINE_REDIS.md)).

### A. Pipelines — pure logic

**Key:** `<root>:config:pipelines:{region}`

**Intent:** Map pipeline id → definition (steps, defaults, **no** queue field required here).

```json
{
  "pipelines": {
    "chat-basic": { },
    "chat-rag": { },
    "debug-trace": { }
  }
}
```

👉 **No queue here** — only workflow shape and parameters the worker interprets.

### B. Queues — execution layer

**Key:** `<root>:config:queues:{region}` *(recommended; not yet a first-class merged snapshot in all deployments)*

**Intent:** Map queue id → worker / Temporal routing metadata.

```json
{
  "queues": {
    "olo-fast": {
      "workerType": "fast-model"
    },
    "olo-smart": {
      "workerType": "gpt4"
    },
    "olo-rag": {
      "workerType": "rag-engine"
    }
  }
}
```

Temporal **task queue names** used at start-workflow time should align with these ids (or a mapping documented next to your worker).

### C. Profiles — binding layer (UI contract)

**Key:** `<root>:config:profiles:{region}` *(recommended target; see “Current implementation” below)*

**Intent:** Named presets the user picks in **olo-chat**. Each profile references **one pipeline id** and **one queue id**.

```json
{
  "profiles": {
    "fast": {
      "displayName": "Fast",
      "pipeline": "chat-basic",
      "queue": "olo-fast"
    },
    "smart": {
      "displayName": "Smart",
      "pipeline": "chat-basic",
      "queue": "olo-smart"
    },
    "rag": {
      "displayName": "RAG",
      "pipeline": "chat-rag",
      "queue": "olo-rag"
    }
  }
}
```

👉 **This becomes your UI contract:** `GET /api/ui/context` should expose these as **`chatProfiles`**: each entry includes **`queue`**, **`pipeline`**, and display fields (`displayName`, `displaySummary`, `emoji`, `runAgain`, etc.).

---

## Current implementation (olo Chat BE today)

As of the current **olo** codebase:

1. **Pipelines snapshot** is loaded from **`<root>:config:pipelines:<region>`** (see `KernelConfigQueueService` / composite configuration).
2. **Chat presets** for the UI are resolved from the **first pipeline entry** in that snapshot that defines a **`chatProfiles`** section (embedded JSON), and exposed as **`chatProfiles`** on **`GET /api/ui/context`** (`UiContextController`). Each preset still carries **`queue`** and **`pipeline`** — matching the **profile binding** concept above, but **physically colocated** inside pipeline config rather than a separate `profiles` Redis key.
3. **Queue listing** for admin/legacy flows uses **`GET /api/tenants/{tenantId}/queues`** and queue **config** (pipelines nested under queue config) — execution-oriented, not the same as the profile document.

So: **logically**, think **Pipelines / Queues / Profiles**; **physically** today, **profiles are often embedded** under `chatProfiles` on a regional pipeline document. Moving to **separate** `queues` and `profiles` keys is an incremental evolution path, not a requirement for the conceptual model.

---

## Frontend (olo-chat)

- **olo-chat** only needs **`GET /api/ui/context` → `chatProfiles`** (each with `queue` + `pipeline`). It does **not** read raw pipeline DAGs or queue worker metadata.
- Presets in the composer and **Run again** semantics are driven entirely by that API — see [CHAT_UI.md](../../olo-chat/docs/CHAT_UI.md) in **olo-chat**.

---

## Related

- [QUEUE_PIPELINE_REDIS.md](./QUEUE_PIPELINE_REDIS.md) — Redis key for pipelines snapshot and queue dropdown origins.
- [API_PAYLOADS.md](./API_PAYLOADS.md) — `chatProfiles` shape on UI context.
- [DESIGN.md](./DESIGN.md) — execution model and Chat BE responsibilities.
