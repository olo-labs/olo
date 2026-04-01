# SDK and Backend – Complete Guide

This document explains how the **olo-sdk** and the **Chat Backend (BE)** work together: configuration, workflow start, payload format, and where each responsibility lives.

---

## 1. Overview

| Component | Role |
|-----------|------|
| **Chat BE** | Spring Boot REST + SSE API. Owns sessions, messages, runs, execution events. **Only** service that uses olo-sdk and talks to Temporal. Builds workflow input and starts/signals workflows. |
| **olo-sdk** | Java library that wraps the Temporal Java SDK. Provides **connection and lifecycle only**: target, namespace, workflow type, `WorkflowClient`, and `newChatWorkflowStub(WorkflowOptions)`. Does **not** define workflow semantics; those live in **olo-worker**. |
| **olo-worker-input** | Library (used by BE and executor) that defines the **WorkflowInput** model (version 1.0): inputs, context, routing, metadata. BE **serializes** it when building the start payload; executor **deserializes** it in the workflow. |
| **olo-executor** | Separate process: runs the workflow and activities, calls back to BE via HTTP to append events. Not part of the backend. |

**Flow:** Frontend → Chat BE → **olo-sdk** (start workflow with **WorkflowInput**) → Temporal → **olo-executor** (executes, callbacks to BE).

---

## 2. olo-sdk – What It Does

### 2.1 Scope (intentional)

- **olo-sdk abstracts connection and lifecycle only. It does not abstract workflow semantics.**
- The SDK provides: configured `WorkflowServiceStubs`, `WorkflowClient`, and a single workflow stub factory. The backend uses `getWorkflowClient()` for signaling by workflow id and `newChatWorkflowStub(WorkflowOptions)` to start the chat workflow. The backend does **not** use workflow type strings; the SDK encapsulates the workflow type name (e.g. `OloKernelWorkflow`).
- Workflow and activity **logic** live in **olo-executor** (separate process); the SDK is a **connection factory** used only by Chat BE.

### 2.2 TemporalClient API

| Method | Description |
|--------|-------------|
| `static Builder newBuilder()` | Builder with defaults: target `localhost:7233`, namespace `default`, workflow type `OloKernelWorkflow`. |
| `WorkflowClient getWorkflowClient()` | Temporal `WorkflowClient` for creating stubs (e.g. signal by workflow id). |
| `WorkflowStub newChatWorkflowStub(WorkflowOptions options)` | Untyped stub for the Olo chat workflow. Backend uses this to start the workflow; workflow type name is hidden inside the SDK. |
| `void close()` | Shuts down service stubs; call on application shutdown. |

**Builder:**

| Method | Description |
|--------|-------------|
| `target(String target)` | gRPC target (e.g. `localhost:7233`). |
| `namespace(String namespace)` | Temporal namespace (e.g. `default`). |
| `workflowType(String workflowType)` | Workflow type name (e.g. `OloKernelWorkflow`). Backend sets from env `OLO_WORKFLOW_TYPE` when present. |
| `build()` | Builds the client. |

### 2.3 What the SDK Does *Not* Do

- Does not define or execute workflows or activities (those are in olo-executor).
- Does not know about WorkflowInput structure; it just passes the object through to Temporal.
- Does not handle task queue or callback URL; the backend owns those and passes task queue when building `WorkflowOptions`.

---

## 3. Backend – How It Uses the SDK

### 3.1 Configuration (Spring)

The backend creates a single `TemporalClient` bean in **DemoConfig** and injects it into **RunService**. Configuration comes from properties (or env):

| Property | Default | Description |
|----------|---------|-------------|
| `olo.temporal.target` | `localhost:7233` | Temporal server gRPC address. |
| `olo.temporal.namespace` | `default` | Temporal namespace. |
| `olo.temporal.workflow-type` | `OloKernelWorkflow` | Workflow type name (can be overridden by env `OLO_WORKFLOW_TYPE`). |
| `olo.temporal.task-queue` | `olo-chat` | Default task queue when the client does not send one. |
| `olo.chat.callback-base-url` | `http://localhost:7080` | Base URL for worker callbacks (append event, etc.). |

`TemporalClient` is built with:

- `target`, `namespace`, `workflowType` (from config / `OLO_WORKFLOW_TYPE`).

Task queue and callback URL are **not** part of the SDK; they are backend config and request values.

### 3.2 Starting a Workflow (RunService)

1. **Controller** (e.g. `POST /api/runs` or `POST /api/sessions/{sessionId}/messages`) validates the request, generates `runId`, creates run record and initial SYSTEM STARTED event.
2. **WorkflowInput** is built via **WorkflowInputSerializer.build(...)** with: tenantId, sessionId, messageId, user message (plain string), pipeline (task queue), transactionId (e.g. runId), runId, callbackBaseUrl.
3. **Task queue**: If the request provides `taskQueue` (e.g. in `CreateRunRequest` or `SendMessageRequest`), that is used; otherwise the backend default `olo.temporal.task-queue` is used.
4. **RunService.startWorkflow(runId, workflowInput, taskQueueFromFrontend)**:
   - Builds `WorkflowOptions` with `workflowId = "run-" + runId` and the effective task queue.
   - Gets a stub: `temporalClient.newChatWorkflowStub(options)`.
   - Calls **`stub.start(workflowInput)`**. The argument is the **WorkflowInput object**; Temporal serializes it as a **JSON object** (not a string). The executor receives the same structure.
5. Executor polls the same task queue, receives the workflow, and executes `execute(WorkflowInput workflowInput)`.

### 3.3 Signaling (Human Input)

- Backend gets an untyped stub by workflow id: `workflowClient.newUntypedWorkflowStub("run-" + runId)`.
- Signals with: `stub.signal("humanInput", approved, message)`.

No workflow type is needed for signaling; only the workflow id is used.

---

## 4. Workflow Input (Payload) – Who Builds What

### 4.1 Format (version 1.0)

The payload is a **WorkflowInput** object (from **olo-worker-input**): `version`, `inputs`, `context`, `routing`, `metadata`.

- **inputs**: List of named inputs. For chat, a single input **userQuery** with:
  - **type**: `STRING`
  - **storage**: `{ "mode": "LOCAL" }` (nulls omitted in JSON via `@JsonInclude(NON_NULL)` on Storage)
  - **value**: Plain user message string (e.g. `"Create a detailed investment report for Tesla."`)
- **context**: tenantId, groupId, roles, permissions, sessionId, **runId**, **callbackBaseUrl** (so the worker can call back to BE).
- **routing**: pipeline (task queue), transactionType (e.g. QUESTION_ANSWER), transactionId (e.g. runId).
- **metadata**: ragTag, timestamp (optional).

See **[WORKFLOW_INPUT.md](WORKFLOW_INPUT.md)** for the full schema and example JSON.

### 4.2 Backend: Building the Payload

- **WorkflowInputSerializer** (in the backend) builds the **WorkflowInput** object using **olo-worker-input** types: `InputItem`, `Context`, `Routing`, `Metadata`, `Storage`, and `WorkflowInput.builder()` (which returns `WorkflowInputBuilder`).
- The backend passes this **object** to `stub.start(workflowInput)`. It does **not** pass a JSON string; Temporal’s serialization produces the JSON that the executor receives.
- So: **Backend = producer** of WorkflowInput; **Executor = consumer** that deserializes and reads inputs/context.

### 4.3 Worker: Consuming the Payload

- The workflow method signature is **`void execute(WorkflowInput workflowInput)`**. Temporal deserializes the JSON payload into this type (olo-worker-input model).
- The executor reads the user message from the **userQuery** input (plain string) and uses **context** (runId, callbackBaseUrl, sessionId) for callbacks and correlation.

---

## 5. Summary Table

| Concern | Owned by | Where |
|--------|----------|--------|
| Temporal connection (target, namespace) | olo-sdk | `TemporalClient` builder |
| Workflow type name | olo-sdk | `TemporalClient` builder; backend never uses the string |
| Task queue | Backend (config + request) | `WorkflowOptions`, `routing.pipeline` in WorkflowInput |
| Callback base URL | Backend | Injected into RunService; put into `context.callbackBaseUrl` |
| WorkflowInput shape (inputs, context, routing, metadata) | olo-worker-input | Model classes; backend builds via WorkflowInputSerializer |
| Building WorkflowInput for chat | Backend | `WorkflowInputSerializer.build(...)` |
| Starting workflow | Backend | `RunService.startWorkflow` → `stub.start(workflowInput)` |
| Signaling workflow | Backend | `RunService.signalHumanInput` → untyped stub by workflow id |
| Executing workflow and activities | olo-executor | Workflow + activities; callbacks to BE (separate process) |

---

## 6. Related Documents

- **[ARCHITECTURE.md](ARCHITECTURE.md)** – System overview and data flow.
- **[DESIGN.md](DESIGN.md)** – Detailed design (boundaries, APIs, persistence).
- **[WORKFLOW_INPUT.md](WORKFLOW_INPUT.md)** – WorkflowInput schema, example, builder (WorkflowInputBuilder), and serialization.
- **[DEMO.md](DEMO.md)** – How to build and run backend, executor, and exercise the flow.
- **olo-sdk/docs/ARCHITECTURE.md** – SDK architecture (TemporalClient, backend integration).
- **olo-sdk/docs/DESIGN.md** – SDK goals, non-goals, and design notes.
