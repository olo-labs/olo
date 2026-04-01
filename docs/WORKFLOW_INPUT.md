# Temporal Workflow Input Format

**olo-worker-input** is the library used to **serialize and deserialize** workflow input. It defines the **WorkflowInput** payload (version 1.0): a single JSON blob that carries inputs, context, routing, and metadata. Producers (e.g. backend) serialize it when starting a workflow; the executor (olo-executor) deserializes it and reads values by name.

---

## Example payload

```json
{
  "version": "1.0",
  "inputs": [
    {
      "name": "userQuery",
      "displayName": "User query",
      "type": "STRING",
      "storage": { "mode": "LOCAL" },
      "value": "Create a detailed investment report for Tesla."
    }
  ],
  "context": {
    "tenantId": "2a2a91fb-f5b4-4cf0-b917-524d242b2e3d",
    "groupId": "",
    "roles": ["PUBLIC"],
    "permissions": [],
    "sessionId": "your-session-id-here"
  },
  "routing": {
    "pipeline": "olo-chat-queue-oolama-debug",
    "transactionType": "QUESTION_ANSWER",
    "transactionId": "wf-001"
  },
  "metadata": {
    "ragTag": null,
    "timestamp": 0
  }
}
```

---

## Structure

| Field      | Description |
|-----------|-------------|
| **version** | Schema version (e.g. `"1.0"`). |
| **inputs**  | List of named inputs. Each has `name`, `displayName`, `type` (e.g. `STRING`), `storage` (e.g. `{ "mode": "LOCAL" }` for inline value, or CACHE/FILE for external storage), and optional `value` when mode is LOCAL. |
| **context** | `tenantId`, `groupId`, `roles`, `permissions`, `sessionId`. |
| **routing** | `pipeline` (task queue / pipeline name), `transactionType` (e.g. `QUESTION_ANSWER`), `transactionId`. |
| **metadata** | Optional `ragTag`, `timestamp`. |

---

## Serialization and deserialization (olo-worker-input)

- **Purpose**: **olo-worker-input** defines the model and (de)serialization. The shape above is implemented in `org.olo.input.model`: `WorkflowInput`, `WorkflowInputBuilder` (fluent builder; `WorkflowInput.builder()` returns it), `InputItem`, `Context`, `Routing`, `Metadata`, `Storage`. **Storage** uses `@JsonInclude(NON_NULL)` so null cache/file are omitted (e.g. `{"mode":"LOCAL"}` only).
- **Producer (backend):** Build `WorkflowInput` (e.g. via **WorkflowInputSerializer** in the chat backend) and pass the **object** to the workflow start. Temporal serializes it to JSON. No need to call `toJson()` for the start call—the SDK sends the object.
- **Consumer (olo-executor):** The workflow receives the payload as a **WorkflowInput** instance (Temporal deserializes JSON into it). Use `WorkflowInputValues` (e.g. `DefaultWorkflowInputValues`) to read values by name; storage (LOCAL vs CACHE vs FILE) is handled by the library. For string inputs like `userQuery`, the value is the plain message.

See **olo-worker-input/README.md** for producer/consumer contracts, cache keys, and `OLO_MAX_LOCAL_MESSAGE_SIZE`.

---

## Relation to Olo chat backend

The backend uses **olo-worker-input** to **build** the workflow input and passes it as an **object** when starting the workflow:

- **WorkflowInputSerializer.build(...)** creates a `WorkflowInput` (version 1.0) with: **userQuery** input of **type STRING**, **storage** `{ "mode": "LOCAL" }`, and **value** = plain user message (no JSON wrapper). **Context** includes tenantId, sessionId, **runId**, and **callbackBaseUrl** so the executor can call back to the backend. **Routing** includes pipeline (task queue), transactionType, and transactionId (e.g. runId).
- The backend passes this **WorkflowInput object** to `stub.start(workflowInput)`. Temporal serializes it as a **JSON object** (not a string); the executor’s workflow method is **`void execute(WorkflowInput workflowInput)`**, so Temporal deserializes the payload into that type.
- Workflow type is **OloKernelWorkflow** (default; override with env `OLO_WORKFLOW_TYPE`). Task queue can come from the request body or backend config. See **[SDK_AND_BACKEND.md](SDK_AND_BACKEND.md)** for how the backend and SDK work together.
