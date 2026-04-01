# Olo SDK – Architecture

This document describes the complete architecture of the **olo-sdk**: a Java library that wraps the Temporal Java SDK and exposes a simplified API for the Olo backend and other Java clients.

---

## 1. Overview

### 1.1 Scope and intent (what the SDK is and is not)

**olo-sdk abstracts connection and lifecycle only. It does not abstract workflow semantics.**

- The SDK provides: configured `WorkflowServiceStubs`, `WorkflowClient`, target, namespace, and `close()`. The backend uses `getWorkflowClient()` and then works directly with the Temporal SDK: it builds `WorkflowOptions`, uses untyped workflow stubs, and knows workflow type names (e.g. `"OloChatWorkflow"`).
- So the backend remains **tightly coupled to Temporal** for workflow operations. The SDK is a **connection factory**, not a workflow abstraction.
- This is **intentional**. If that remains the permanent intent, no further abstraction is required. If later you introduce typed workflow APIs or stub factories in the SDK, that would reduce backend–Temporal coupling; the docs would then be updated to reflect the new scope.

Documenting this explicitly avoids architectural drift: future contributors know the current boundary and can decide consciously whether to extend it.

### 1.2 Purpose

The Olo SDK centralizes:

- **Temporal connection management** — Service stubs, namespace, and client lifecycle (target, namespace, `close()`).
- **Configuration** — Connection details supplied by the host application via the builder; no hard-coded addresses or namespaces in application code.

The SDK does encapsulate the **workflow type name** for the chat workflow: `newChatWorkflowStub(WorkflowOptions)` returns a stub for the Olo chat workflow so the backend never uses the string `"OloChatWorkflow"`; renaming the workflow only requires changing the SDK. The backend still uses Temporal SDK types for `WorkflowOptions`, stub usage (start/signal), and for signaling by workflow id.

### 1.3 Positioning in the System

```
┌─────────────────────────────────────────────────────────────────┐
│  Olo Backend (Spring Boot)                                       │
│  ┌─────────────────┐    ┌─────────────────────────────────────┐ │
│  │  RunService     │───▶│  olo-sdk (TemporalClient)            │ │
│  │  DemoConfig     │    │  - WorkflowServiceStubs              │ │
│  └─────────────────┘    │  - WorkflowClient                    │ │
│                         └──────────────────┬───────────────────┘ │
└────────────────────────────────────────────│─────────────────────┘
                                             │
                                             ▼
                              ┌──────────────────────────────┐
                              │  Temporal Server             │
                              │  (e.g. localhost:7233)       │
                              └──────────────────────────────┘
                                             ▲
                                             │
┌────────────────────────────────────────────│─────────────────────┐
│  Olo Executor (separate process)           │                     │
│  - OloChatWorkflow / OloChatActivities     │                     │
└────────────────────────────────────────────┴─────────────────────┘
```

The backend uses the SDK to **start** and **signal** workflows; the worker (separate process) executes workflow and activity code and communicates back to the backend via HTTP callbacks.

---

## 2. Module Structure

### 2.1 Artifact

| Attribute   | Value                          |
|------------|---------------------------------|
| GroupId    | `org.olo`                       |
| ArtifactId | `olo-sdk`                       |
| Version    | `0.0.1-SNAPSHOT`                |
| Packaging  | `jar`                           |
| Java       | 21                              |

### 2.2 Dependencies

- **io.temporal:temporal-sdk** (1.26.0) — Temporal Java SDK. The SDK does not depend on Spring; the backend wires the client via Spring configuration.

### 2.3 Package Layout

```
org.olo.sdk
└── TemporalClient.java    — Single public entry point: connection + WorkflowClient access
```

There are no workflow or activity definitions inside the SDK; those live in **olo-executor** (the separate process that runs workflows). The SDK provides the **client** side only (start workflow, signal workflow). Typed workflow interfaces (e.g. `ChatWorkflow`) can be added later in this package or a sub-package.

---

## 3. Core Component: TemporalClient

### 3.1 Responsibility

`TemporalClient` is the only public API of the SDK. It:

1. **Owns** `WorkflowServiceStubs` and `WorkflowClient` (Temporal SDK types).
2. **Constructs** them from a connection target (e.g. `localhost:7233`).
3. **Exposes** `WorkflowClient` so the host can create workflow stubs and execute start/signal/query.
4. **Cleans up** via `close()` (shuts down service stubs).

### 3.2 API

`TemporalClient` is constructed via a **builder** (no public constructor). All configuration is applied at build time.

| Method | Description |
|--------|-------------|
| `static Builder newBuilder()` | Returns a builder with defaults: target `localhost:7233`, namespace `default`. |
| `WorkflowClient getWorkflowClient()` | Returns the Temporal `WorkflowClient` for creating workflow stubs (e.g. for signaling by workflow id). |
| `WorkflowStub newChatWorkflowStub(WorkflowOptions options)` | Returns an untyped stub for the Olo chat workflow. Encapsulates the workflow type name so the backend does not depend on it; renaming the workflow only requires changing the SDK. |
| `void close()` | Shuts down `WorkflowServiceStubs`; should be called on application shutdown. |

**Builder:**

| Method | Description |
|--------|-------------|
| `Builder target(String target)` | gRPC target (e.g. `localhost:7233`). Used to create `WorkflowServiceStubs` via `WorkflowServiceStubsOptions`. |
| `Builder namespace(String namespace)` | Temporal namespace. Used to create `WorkflowClient` via `WorkflowClientOptions`. |
| `Builder workflowType(String workflowType)` | Workflow type name (e.g. `OloKernelWorkflow`). Backend sets from env `OLO_WORKFLOW_TYPE` when present. |
| `TemporalClient build()` | Builds the client; stubs and workflow client are configured with the builder values. |

### 3.3 Lifecycle

- **Creation**: Backend creates one instance (e.g. a Spring `@Bean`) via `TemporalClient.newBuilder().target(...).namespace(...).build()`.
- **Usage**: Services obtain `WorkflowClient` from `getWorkflowClient()` and create untyped or (future) typed workflow stubs.
- **Shutdown**: Backend should call `close()` when the application stops to release gRPC resources.

---

## 4. Backend Integration

### 4.1 Configuration (Spring)

The backend supplies SDK-related configuration and constructs the client:

- **olo.temporal.target** — Temporal server gRPC address (default `localhost:7233`); passed to SDK builder.
- **olo.temporal.namespace** — Temporal namespace (default `default`); passed to SDK builder.
- **olo.temporal.task-queue** — Task queue name (default `olo-chat`); used by backend when starting workflows.
- **olo.chat.callback-base-url** — Base URL for worker callbacks (default `http://localhost:7080`).

`TemporalClient` is created in `DemoConfig` and injected into services (e.g. `RunService`).

### 4.2 Usage in RunService

1. **Inject** `TemporalClient` and task queue / callback URL (from config).
2. **Obtain** `WorkflowClient**: `workflowClient = temporalClient.getWorkflowClient()`.
3. **Start workflow**: Build **WorkflowInput** (via **WorkflowInputSerializer** in the backend, using olo-worker-input model). Build `WorkflowOptions` (workflow id, task queue). Get stub: `temporalClient.newChatWorkflowStub(options)`. Call **`stub.start(workflowInput)`** — the argument is the **WorkflowInput object**; Temporal serializes it as JSON for the worker.
4. **Signal workflow**: Get untyped stub by workflow id (`"run-" + runId`), call `stub.signal("humanInput", approved, message)`.

Workflow and activity types are defined in **olo-executor**; the SDK only provides the client used to start and signal them. Payload shape (WorkflowInput) is defined in **olo-worker-input** and built by the backend; the SDK does not depend on it.

---

## 5. Configuration Model

- **Target**: Set via `Builder.target(String)`. Used to build `WorkflowServiceStubsOptions` and create `WorkflowServiceStubs`; the gRPC connection uses this address. Future TLS can be added via the same options builder.
- **Namespace**: Set via `Builder.namespace(String)`. Used to build `WorkflowClientOptions` and create `WorkflowClient`; all workflows run in this namespace.
- **Task queue and callback URL**: Not part of the SDK; the backend holds them and passes them when starting workflows. The SDK owns only connection and namespace.

---

## 6. Error Handling and Resilience

- **Current**: No custom exception wrapping; Temporal SDK exceptions propagate to the caller.
- **Planned**: Wrap Temporal-specific exceptions in SDK-level exceptions; optional helpers for retry and timeout; clear messages for connection failures.

---

## 7. Versioning and Compatibility

- **SDK**: Semantic versioning (e.g. 0.0.1-SNAPSHOT).
- **Temporal SDK**: Document the tested/compatible Temporal Java SDK version (e.g. 1.26.0) and update as the SDK evolves.

---

## 8. Future Extensions

- **Typed workflow APIs** — e.g. `ChatWorkflow` interface and stub factories in the SDK so the backend uses typed methods instead of untyped `WorkflowStub`.
- **Configuration** — TLS and optional retry/timeout via `WorkflowServiceStubsOptions` / builder.
- **Error handling** — SDK exceptions and retry/timeout helpers.
- **Observability** — Metrics and tracing hooks that plug into the backend’s stack.
- **Testing** — Unit tests for config and helpers; integration tests against Temporal (test env or Docker).

---

## 9. Related Documents

- **DESIGN.md** (this folder) — Goals, non-goals, and design notes for the SDK.
- **docs/SDK_AND_BACKEND.md** (repo root) — Complete guide to how the backend uses the SDK, WorkflowInput, config, and task queue.
- **docs/DEMO.md** (repo root) — How to run the backend, worker, and exercise the chat flow (which uses this SDK).
