# Olo Temporal SDK – Design

## 1. Overview

The Olo Temporal SDK (`olo-temporal-sdk`) is a Java library that wraps the Temporal Java SDK and exposes a configured client to the Olo backend and other Java clients. It centralizes Temporal **connection lifecycle** (target, namespace, stubs, `WorkflowClient`, `close()`) and **encapsulates the chat workflow type name** for `newChatWorkflowStub`.

**Explicit scope:** **olo-temporal-sdk is the only module that imports `io.temporal.*`.** The backend uses `WorkflowRunner` (application abstraction) → `SdkWorkflowRunner` → `TemporalClient`. The SDK owns workflow type, workflow id prefix, task queue on start, and human-input signal name. `WorkflowInput` payload and callback URL stay in the backend. See [ARCHITECTURE.md](ARCHITECTURE.md) §1.1.

## 2. Goals and non-goals

### Goals (current)

- Encapsulate Temporal connection (service stubs, target, namespace) and client lifecycle.
- Expose a single entry point (`TemporalClient`) with a builder.
- Encapsulate the chat workflow type name (`Builder.workflowType`, default **`olo`**).
- Allow the SDK to evolve independently of the backend.

### Goals (optional / later)

- Typed workflow interfaces and stub factories to reduce backend–Temporal coupling.

### Non-goals (explicit)

- Does **not** define workflow or activity implementations (those live in Temporal **worker** processes).
- Does **not** own task queue selection (backend sets `WorkflowOptions.setTaskQueue`; per-run queue from chat preset or default from config).
- Does **not** define or own `WorkflowInput` (**olo-workflow-input**; backend builds it via `WorkflowInputSerializer`).
- Does **not** abstract signal method names or parameters (backend uses `WorkflowStub.signal("humanInput", …)`).
- Does **not** load regional workflow JSON (backend **`olo.configuration.dir`** + `ResolvedOloRuntimeSettings`).
- Does **not** manage persistence, sessions, or SSE/WebSocket (backend stores and broadcast).

## 3. High-level architecture

- **`TemporalClient`** — Start chat workflow, signal human input, await result (`ChatWorkflowHandle`).
- **`WorkflowRunner`** / **`SdkWorkflowRunner`** — Backend abstraction over the SDK.
- **`RunServiceImpl`** — Domain events only; no Temporal imports.

## 4. TemporalClient responsibilities

- Build and hold `WorkflowServiceStubs` and `WorkflowClient`.
- Apply **workflow type** when creating chat stubs (`newChatWorkflowStub`).
- Provide `close()` for gRPC cleanup.
- **Not responsible for:** task queue, tenant, callback URL, or payload construction.

## 5. Usage pattern (from backend)

1. Load regional config from **`olo.configuration.dir`** (e.g. `olo-mono/olo-definition/olo-configuration/default/*.json`).
2. Resolve defaults with **`ResolvedOloRuntimeSettings`**: tenant id (region folder name), default task queue and workflow type (first workflow by `id`).
3. Create the SDK client:
   ```java
   import org.olo.temporal.sdk.TemporalClient;

   TemporalClient.newBuilder()
       .target(temporalTarget)                    // olo.temporal.target
       .namespace(runtimeSettings.temporalNamespace())
       .workflowType(runtimeSettings.workflowType())  // workflow.json, e.g. "olo"
       .build();
   ```
4. **Start chat workflow** — `workflowRunner.startChatRun(runId, workflowInput, taskQueue, completion)` → `temporalClient.startChatWorkflow(...)`.
5. **Signal** — `workflowRunner.signalHumanInput(runId, approved, message)` → `temporalClient.signalHumanInput(...)`.

Workers must register the same **workflow type** (`olo`) and listen on the **task queues** defined in workflow JSON (`queue` field, e.g. `ask`, `fast`).

## 6. Error handling and resilience

- **Today** — Temporal exceptions propagate unchanged.
- **Planned** — SDK-level wrappers; clearer connection-failure messages; optional retry helpers.

## 7. Testing strategy

- **Unit tests** — Builder defaults, stub creation (with mocks where possible).
- **Integration tests** — Against Temporal test environment or Docker (future).

## 8. Versioning and compatibility

- Semantic versioning for olo-temporal-sdk.
- Pin and document compatible **temporal-sdk** version (currently 1.26.0).

## 9. Future extensions

- Typed `ChatWorkflow` API in the SDK.
- TLS and connection retry configuration on the builder.
- Metrics/tracing hooks for backend observability stacks.

## 10. Related documents

| Document | Content |
|----------|---------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Module layout, API tables, backend wiring. |
| [olo/start.bat](../../start.bat) | Local backend start (sets `OLO_CONFIGURATION_DIR`). |
| [olo-chat/docs/README.md](../../../olo-chat/docs/README.md) | Chat UI and preset/task-queue behavior. |
