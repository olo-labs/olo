# Queue and pipeline dropdowns (Chat UI)

For the **recommended split** between pipeline definitions, execution queues, and UI **profiles**, see [PIPELINE_QUEUE_PROFILE_LAYERS.md](./PIPELINE_QUEUE_PROFILE_LAYERS.md).

The **olo** backend reads the **pipelines section** from Redis (same layout as **olo-worker-configuration**):

| Purpose | Redis key | Value |
|--------|-----------|-------|
| Region pipelines | `<olo.cache.root-key>:config:pipelines:<region>` | JSON **object**: pipeline id → pipeline definition (from DB / admin) |

- **region** — `olo.ui.config-region`, or `olo.region`, or `default`.
- **Queue** dropdown = sorted keys (pipeline ids / task queues). **Pipeline** sub-dropdown uses nested `pipelines` in that definition when present.

If the key is missing or empty, **GET /api/tenants/{tenantId}/queues** returns `[]`.

## Prerequisites

1. **Redis** running where **olo** points (`OLO_CACHE_HOST` / `OLO_CACHE_PORT`, default `localhost:46379`).
2. **olo** must **not** exclude Redis autoconfiguration (see `application.properties`).

## Pipeline JSON shapes (stored as the Redis string value)

The backend merges **`pipelines`** for the UI as an array of `{ "id", "name" }`.

### A) Array of pipeline ids (simplest)

```json
{
  "version": "1.0",
  "pipelines": ["default", "research", "rag"]
}
```

### B) Map of id → metadata (friendly labels)

```json
{
  "version": "1.0",
  "pipelines": {
    "default": { "name": "Default chat" },
    "research": { "name": "Research" },
    "rag": { "name": "RAG" }
  }
}
```

Use **pipeline ids** that match your workflow / **olo-worker** routing (`routing.pipeline`, etc.).

## Example: inspect pipelines section (redis-cli)

```bash
redis-cli GET "olo:config:pipelines:default"
```

Must match the JSON produced when the worker (or DB backfill) writes the **pipelines** section for that region.

## Verify from the API

```http
GET /api/tenants/{tenantId}/queues
GET /api/tenants/{tenantId}/queues/{queueName}/config
```

You should see queue names and a **`pipelines`** array with **`id`** / **`name`** objects.

## olo-worker

The worker (or **PipelineSectionBuilder** from DB) writes **`olo:config:pipelines:<region>`**. The chat UI lists whatever pipeline ids appear in that object.
