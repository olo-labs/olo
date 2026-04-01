# Invoking the Olo Chat Workflow

This document describes how to invoke the Olo chat workflow so it runs correctly: **workflow type**, **task queue**, **namespace**, **input format**, and **schema**. It also covers optional signals (e.g. human input) and how the executor (olo-executor) must be configured to pick up the work.

---

## 1. Workflow Type

| Item | Value |
|------|--------|
| **Workflow type name** | `OloChatWorkflow` |
| **Implementation** | `OloChatWorkflowImpl` (registered in olo-executor) |
| **Entrypoint method** | `execute(String runId, String callbackBaseUrl, String sessionId, String messageId, String userMessage)` |

The client (e.g. Chat backend) uses an **untyped** workflow stub with workflow type (e.g. `OloKernelWorkflow`). The SDK hides the type name so only the executor and SDK need to change if the type is renamed.

---

## 2. Task Queue

The **task queue** is what connects the client (who starts the workflow) to the executor (who runs it). Both must use the **same task queue name**.

| Where | How it's set | Default |
|-------|----------------|--------|
| **Executor (olo-executor)** | Env: `OLO_TASK_QUEUE` | `olo-chat` |
| **Chat backend (client)** | Config: `olo.temporal.task-queue` | `olo-chat` |

- **Executor:** `OloExecutorMain` reads `OLO_TASK_QUEUE` and creates a worker on that queue.
- **Client:** When using WorkflowInput, the task queue is taken from **`routing.pipeline`** (e.g. `olo-chat-queue-oolama-debug`). The executor must be started with the same value.

**Important:** The value of **`routing.pipeline`** in the input must match the worker’s task queue (e.g. `OLO_TASK_QUEUE`). Otherwise the workflow will never be picked up.

---

## 3. Namespace

| Where | How it's set | Default |
|-------|----------------|--------|
| **Executor** | Env: `OLO_TEMPORAL_NAMESPACE` | `default` |
| **Client (SDK)** | Builder: `TemporalClient.newBuilder().namespace(...)` | In app: `olo.temporal.namespace` → `default` |

Client and executor must use the **same Temporal namespace**. Typically both use `default` unless you override via env or config.

---

## 4. Input Format: WorkflowInput (JSON)

The workflow expects a **single JSON payload** in **WorkflowInput version 1.0** format. The client serializes this payload and passes it as the workflow input when starting the workflow.

### 4.1 Example payload

```json
{
  "version": "1.0",
  "inputs": [
    {
      "name": "userQuery",
      "displayName": "User query",
      "type": "STRING",
      "storage": {
        "mode": "LOCAL"
      },
      "value": "Create a detailed investment report for Tesla."
    }
  ],
  "context": {
    "tenantId": "2a2a91fb-f5b4-4cf0-b917-524d242b2e3d",
    "groupId": "",
    "roles": [
      "PUBLIC"
    ],
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

### 4.2 Schema: top-level fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| **version** | string | Yes | Schema version; use `"1.0"`. |
| **inputs** | array | Yes | List of named input items (see below). |
| **context** | object | Yes | Tenant, session, roles, permissions (see below). |
| **routing** | object | Yes | Pipeline (task queue), transaction type, transaction ID (see below). |
| **metadata** | object | No | Optional `ragTag`, `timestamp`, etc. |

### 4.3 inputs[] — each item

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| **name** | string | Yes | Unique key to read the value (e.g. `userQuery`). |
| **displayName** | string | No | Human-readable label. |
| **type** | string | Yes | e.g. `STRING`. |
| **storage** | object | Yes | How the value is provided: `{ "mode": "LOCAL" }` for inline value; other modes (e.g. CACHE, FILE) for external storage. |
| **value** | (any) | When LOCAL | Inline value when `storage.mode` is `LOCAL`. |

For chat, the user message is typically an input with `name` `"userQuery"` (or similar), `type` `"STRING"`, `storage.mode` `"LOCAL"`, and `value` set to the message text.

### 4.4 context

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| **tenantId** | string | Yes | Tenant identifier. |
| **groupId** | string | No | Group identifier; can be `""`. |
| **roles** | array of string | No | e.g. `["PUBLIC"]`. |
| **permissions** | array | No | Can be `[]`. |
| **sessionId** | string | Yes | Chat/session ID for correlation. |

### 4.5 routing

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| **pipeline** | string | Yes | **Task queue name** the executor listens on (e.g. `olo-chat-queue-oolama-debug` or `olo-chat`). Must match the worker’s task queue. |
| **transactionType** | string | No | e.g. `QUESTION_ANSWER`. |
| **transactionId** | string | Yes | Unique ID for this workflow run; often used as workflow ID (e.g. `run-{transactionId}`). |

### 4.6 metadata

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| **ragTag** | string / null | No | Optional RAG tag. |
| **timestamp** | number | No | Optional timestamp. |

### 4.7 What is required in the input

- **version** – Must be `"1.0"`.
- **inputs** – At least one input; for chat, include the user message (e.g. name `userQuery`, type `STRING`, storage `LOCAL`, value = message text).
- **context** – Must include **tenantId** and **sessionId**; other fields as needed.
- **routing** – Must include **pipeline** (task queue name) and **transactionId** (run/workflow identifier).
- **metadata** – Optional.

The executor deserializes this JSON with `WorkflowInput.fromJson(rawInput)` (or receives it as a WorkflowInput object) and reads values by name (e.g. via `WorkflowInputValues`). Storage mode (LOCAL vs CACHE vs FILE) is handled by the input library.

---

## 5. Input schema (summary)

Workflow input is a **single string** containing the WorkflowInput JSON:

```
workflow input (single argument): String = JSON serialization of:
{
  "version": "1.0",
  "inputs": [ { "name", "displayName", "type", "storage", "value"? } ],
  "context": { "tenantId", "groupId"? , "roles"? , "permissions"? , "sessionId" },
  "routing": { "pipeline", "transactionType"? , "transactionId" },
  "metadata"? : { "ragTag"? , "timestamp"? }
}
```

- **Task queue** should match **`routing.pipeline`** so the executor that listens on that queue runs the workflow.
- **Workflow ID** is typically derived from **`routing.transactionId`** (e.g. `run-{transactionId}`) so signals and queries can target the same run.

---

## 6. How the client invokes the workflow

Conceptually, the client:

1. Builds a **WorkflowInput** object (or equivalent) with `version`, `inputs`, `context`, `routing`, and optionally `metadata`.
2. Serializes it to JSON (e.g. `input.toJson()` or your JSON mapper).
3. Resolves **task queue** from **`routing.pipeline`** (and **namespace** from config/env).
4. Builds **WorkflowOptions** with:
   - `workflowId("run-" + routing.transactionId)` (or similar)
   - `taskQueue(routing.pipeline)`
5. Gets an untyped stub for workflow type **`OloChatWorkflow`** with those options.
6. Calls **`stub.start(workflowInputJson)`** — one string argument: the JSON.

Example:

```java
WorkflowInput input = WorkflowInputProducer.create(...)
    .context(context)
    .routing(routing)
    .addStringInput("userQuery", "User query", userMessage)
    .build();
String json = input.toJson();

WorkflowOptions options = WorkflowOptions.newBuilder()
        .setWorkflowId("run-" + input.getRouting().getTransactionId())
        .setTaskQueue(input.getRouting().getPipeline())
        .build();
WorkflowStub stub = temporalClient.newChatWorkflowStub(options);
stub.start(json);
```

The **namespace** is set when building the Temporal client (e.g. via `olo.temporal.namespace`), not per start.

---

## 7. Optional: Human Input Signal

When the workflow reaches a **HUMAN** step, it waits for a signal before continuing.

| Signal name | Method (executor) | Parameters |
|-------------|------------------|------------|
| `humanInput` | `OloChatWorkflow.humanInput(boolean, String)` | `approved` (boolean), `message` (String) |

The client signals the same workflow by workflow ID `run-{runId}` (or `run-{transactionId}`):

```java
WorkflowStub stub = workflowClient.newUntypedWorkflowStub("run-" + runId);
stub.signal("humanInput", approved, message != null ? message : "");
```

---

## 8. Checklist for "Making It Work"

1. **Workflow type** – Client starts a workflow with type **`OloChatWorkflow`** (handled by SDK).
2. **Task queue** – Use **`routing.pipeline`** as the task queue when starting the workflow. The executor must be started with the same task queue (e.g. `OLO_TASK_QUEUE=olo-chat-queue-oolama-debug` or match your pipeline value).
3. **Namespace** – Same on both sides (e.g. **`default`**). Worker: `OLO_TEMPORAL_NAMESPACE`; Backend: `olo.temporal.namespace`.
4. **Input** – Single **WorkflowInput 1.0 JSON**: `version`, `inputs` (e.g. `userQuery` for the message), `context` (tenantId, sessionId), `routing` (pipeline, transactionId), and optional `metadata`.
5. **Workflow ID** – Use **`run-` + routing.transactionId** (or equivalent run ID) so signals and queries can find the run.
6. **Callback URL** – If the workflow reports events to a backend, that URL must be reachable by the executor (e.g. from config or from input).
7. **Temporal server** – Worker and client must point at the same Temporal server (e.g. `localhost:7233`). Backend uses `olo.temporal.target`.

---

## 9. References

- **Executor:** `OloExecutorMain.java` – task queue, namespace, workflow/activity registration.
- **Workflow interface:** `org.olo.worker.workflow.OloChatWorkflow` – `execute(...)` and `humanInput(...)`.
- **Workflow implementation:** `org.olo.worker.workflow.impl.OloChatWorkflowImpl` – execution and signal handling.
- **Activities interface:** `org.olo.worker.activities.OloChatActivities`; **impl:** `org.olo.worker.activities.impl.OloChatActivitiesImpl`.
- **Client start:** Backend `RunService` (interface) / `RunServiceImpl` (in `service.impl`) – `startWorkflow(...)`, `signalHumanInput(...)`.
- **SDK:** `TemporalClient.java` – workflow type name, `newChatWorkflowStub(...)`.
- **Config:** `DemoConfig.java` – `olo.temporal.task-queue`, `olo.temporal.namespace`, `olo.chat.callback-base-url`.
- **Input model:** `olo-worker-input` – `WorkflowInput`, `WorkflowInput.builder()` (returns `WorkflowInputBuilder`), `WorkflowInput.fromJson()`, `WorkflowInputValues`; see **docs/WORKFLOW_INPUT.md**.
