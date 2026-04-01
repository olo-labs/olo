# Olo SDK – Design

## 1. Overview

The Olo SDK (`olo-sdk`) is a Java library that wraps the Temporal Java SDK and exposes a configured client to the Olo backend and other Java clients. Its purpose is to centralize Temporal **configuration and connection lifecycle** (target, namespace, stubs, `WorkflowClient`, `close()`).

**Explicit scope:** **olo-sdk abstracts connection and lifecycle only. It does not abstract workflow semantics.** The backend still imports the Temporal SDK, builds `WorkflowOptions`, and uses untyped stubs. The SDK **does** encapsulate the workflow type name via `newChatWorkflowStub(WorkflowOptions)`, so the backend never uses the workflow type string. So the SDK is **in-between**: connection factory + workflow-type encapsulation, but not a full workflow abstraction (no typed interfaces, no ownership of task queue or payload). This is intentional for v1. See [ARCHITECTURE.md](ARCHITECTURE.md) §1.1.

## 2. Goals and Non-goals

- **Goals (current)**
  - Encapsulate Temporal connection details (service stubs, target, namespace) and client lifecycle.
  - Expose a single entry point (`TemporalClient`) with a builder; no fake or unused configuration.
  - Encapsulate the workflow type name so the backend does not depend on it (`newChatWorkflowStub`).
  - Allow versioned evolution of the SDK independently of the backend.

- **Goals (planned / optional)**
  - Typed workflow interfaces and stub factories would reduce backend–Temporal coupling further; not required if current scope is permanent.

- **Non-goals (explicit)**
  - Does **not** define the workflow interface or workflow/activity implementations (those live in olo-executor).
  - Does **not** own or configure task queue (backend owns it; passed in `WorkflowOptions`).
  - Does **not** define or own the workflow payload (WorkflowInput is olo-worker-input; backend builds it).
  - Does **not** abstract signals (backend uses Temporal SDK for signal method name and parameters).
  - Does **not** provide a fully generic Temporal wrapper suitable for all domains.
  - Does **not** manage persistence or domain models (those stay in the backend or other services).
  - Does **not** hide Temporal SDK types from the backend for workflow operations (backend still uses `WorkflowOptions`, `WorkflowStub`, etc.).

## 3. High-level Architecture

- **Core client**
  - `TemporalClient` is responsible for creating and managing `WorkflowServiceStubs` and `WorkflowClient` from target and namespace (builder). It does not expose workflow-level APIs; the backend uses `getWorkflowClient()` and then the Temporal SDK directly.

- **Workflow APIs (optional / planned)**
  - If scope is extended later: typed interfaces (e.g. `ChatWorkflow`) and stub factories would reduce backend–Temporal coupling. Not part of current scope.

- **Configuration**
  - Connection and namespace supplied via `TemporalClient.newBuilder().target(...).namespace(...)`; designed to be wired from Spring Boot configuration in the backend.

## 4. TemporalClient Responsibilities

- Manage lifecycle of `WorkflowServiceStubs` and `WorkflowClient` (build from target and namespace, provide `close()`).
- Expose `getWorkflowClient()` so the backend can create workflow stubs and invoke start/signal/query **using the Temporal SDK directly** (the SDK does not provide typed workflow APIs today).
- No workflow-level helpers in the current scope; the backend constructs `WorkflowOptions`, untyped stubs, and workflow type names itself.

## 5. Usage Pattern (from Backend)

1. Backend initializes `TemporalClient` via `TemporalClient.newBuilder().target(...).namespace(...).workflowType(...).build()` with configuration (and optional env `OLO_WORKFLOW_TYPE`).
2. Backend obtains `WorkflowClient` from `temporalClient.getWorkflowClient()` for signaling by workflow id.
3. To start the chat workflow: backend builds **WorkflowInput** (olo-worker-input, e.g. via WorkflowInputSerializer), builds `WorkflowOptions` (workflow id, task queue), gets a stub via **`temporalClient.newChatWorkflowStub(options)`**, and calls **`stub.start(workflowInput)`**. The workflow type name is encapsulated in the SDK. The payload is the WorkflowInput object; Temporal serializes it as JSON for the executor (olo-executor).
4. To signal: backend gets untyped stub by workflow id and calls `stub.signal("humanInput", ...)`.

See **docs/SDK_AND_BACKEND.md** (repo root) for the full backend–SDK flow and config.

## 6. Error Handling and Resilience

- Wrap Temporal-specific exceptions into SDK-level exceptions that are easier for backend services to interpret.
- Provide helpers for common retry and timeout configurations.
- Ensure that connection failures are surfaced clearly, with actionable messages.

## 7. Testing Strategy

- **Unit tests**
  - Test configuration classes and small utility helpers without requiring a Temporal server.
- **Integration tests (later)**
  - Use a Temporal test environment or Docker-based Temporal to ensure compatibility.

## 8. Versioning and Compatibility

- Follow semantic versioning for the SDK.
- Keep Temporal SDK version compatibility documented and regularly updated.

## 9. Future Extensions

- High-level abstractions for common workflow patterns (sagas, long-running sessions, etc.).
- Metrics and tracing hooks that plug into the backend’s observability stack.
- Optional adapters for non-Spring environments.

