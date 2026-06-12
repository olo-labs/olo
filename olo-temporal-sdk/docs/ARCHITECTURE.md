# Olo Temporal SDK – Architecture

This document describes the architecture of **olo-temporal-sdk**: a Java library that wraps the Temporal Java SDK and exposes a configured client for the Olo backend and other Java clients.

---

## 1. Overview

### 1.1 Scope and intent (what the SDK is and is not)

**olo-temporal-sdk abstracts connection and lifecycle only. It does not abstract workflow semantics.**

- The SDK provides: configured Temporal connection, chat workflow **start**, **human-input signal**, async **result await**, and `close()`. The backend must **not** import `io.temporal.*`; it uses `TemporalClient` and the backend `WorkflowRunner` abstraction.
- The SDK encapsulates workflow id convention (`run-{runId}`), workflow type, task queue routing, and signal name (`humanInput`).
- This is **intentional**. Typed workflow APIs in the SDK would reduce coupling further but are not required today.

### 1.2 Purpose

The Olo Temporal SDK centralizes:

- **Temporal connection management** — Service stubs, namespace, client lifecycle (`target`, `namespace`, `close()`).
- **Workflow type for chat stubs** — `newChatWorkflowStub(WorkflowOptions)` uses the type configured on the builder (default **`olo`**).

The backend sets **`workflowType`** on the builder from **`workflow.json`** under **`olo.configuration.dir`** (`workflowType` field, e.g. `"olo"`). It must match the name registered by the Temporal worker.

### 1.3 Positioning in the system

```
┌─────────────────────────────────────────────────────────────────┐
│  Olo Backend (Spring Boot, olo/)                                 │
│  ┌─────────────────┐    ┌─────────────────────────────────────┐ │
│  │  RunServiceImpl │───▶│  olo-temporal-sdk (TemporalClient)   │ │
│  │  DemoConfig     │    │  - WorkflowServiceStubs              │ │
│  └─────────────────┘    │  - WorkflowClient                    │ │
│         ▲               └──────────────────┬───────────────────┘ │
│         │  ResolvedOloRuntimeSettings      │                     │
│         │  (olo.configuration.dir)         │                     │
└─────────┼──────────────────────────────────│─────────────────────┘
          │                                  ▼
          │                   ┌──────────────────────────────┐
          │                   │  Temporal Server             │
          │                   │  (olo.temporal.target)       │
          │                   └──────────────────────────────┘
          │                                  ▲
          │                                  │
┌─────────┴──────────────────────────────────┴─────────────────────┐
│  Temporal worker(s) — separate process(es)                         │
│  Workflow type: olo (from workflow.json workflowType)              │
│  Task queues: per profile (e.g. ask, fast) from workflow.json queue │
│  Callbacks: POST {olo.chat.callback-base-url}/api/runs/{id}/events │
└──────────────────────────────────────────────────────────────────┘
```

The backend **starts** and **signals** workflows via the SDK. Workers execute workflow/activity code and report execution events to the backend over HTTP.

---

## 2. Module structure

### 2.1 Artifact

| Attribute   | Value                 |
|------------|------------------------|
| GroupId    | `org.olo`              |
| ArtifactId | `olo-temporal-sdk`     |
| Version    | `0.0.1-SNAPSHOT`       |
| Packaging  | `jar`                  |
| Java       | 21                     |

### 2.2 Dependencies

- **io.temporal:temporal-sdk** (1.26.0) — Temporal Java SDK. No Spring dependency; the backend wires the client in `DemoConfig`.

### 2.3 Package layout

```
org.olo.temporal.sdk
└── TemporalClient.java    — Single public entry point
```

Workflow and activity implementations live in **worker processes**, not in olo-temporal-sdk. The SDK is **client-only** (start workflow, signal by workflow id).

---

## 3. Core component: TemporalClient

### 3.1 Responsibility

`TemporalClient`:

1. Owns `WorkflowServiceStubs` and `WorkflowClient`.
2. Constructs them from builder `target` and `namespace`.
3. Exposes `getWorkflowClient()` for signaling and other Temporal SDK usage.
4. Exposes `newChatWorkflowStub(WorkflowOptions)` using the configured **workflow type**.
5. Shuts down stubs via `close()`.

### 3.2 API

Constructed via **builder** only (no public constructor).

| Method | Description |
|--------|-------------|
| `static Builder newBuilder()` | Defaults: target `localhost:7233`, namespace `default`, workflow type `olo` if unset. |
| `ChatWorkflowHandle startChatWorkflow(String runId, String taskQueue, Object workflowInput)` | Start chat workflow with id `run-{runId}`. |
| `void signalHumanInput(String runId, boolean approved, String message)` | Signal `humanInput` on `run-{runId}`. |
| `static String runWorkflowId(String runId)` | Workflow id helper (`run-{runId}`). |
| `void close()` | Shuts down `WorkflowServiceStubs`. |

**`ChatWorkflowHandle`:** `awaitResult()`, `awaitResultAsync(executor, onSuccess, onFailure)`.

**Builder:**

| Method | Description |
|--------|-------------|
| `Builder target(String target)` | gRPC target (e.g. `localhost:7233`). |
| `Builder namespace(String namespace)` | Temporal namespace (backend uses `default`). |
| `Builder workflowType(String workflowType)` | Workflow type name (e.g. `olo`). From `workflow.json` in the backend. |
| `TemporalClient build()` | Builds client with configured values. |

### 3.3 Lifecycle

- **Creation** — One Spring `@Bean` in `DemoConfig`: `TemporalClient.newBuilder().target(...).namespace(...).workflowType(...).build()`.
- **Usage** — `SdkWorkflowRunner` injects `TemporalClient`; `RunServiceImpl` injects `WorkflowRunner` only (no Temporal types).
- **Shutdown** — Call `close()` on application stop (not wired today; recommended for clean gRPC teardown).

---

## 4. Backend integration

### 4.1 Configuration (Spring)

| Property / source | Role |
|-------------------|------|
| **olo.temporal.target** | Temporal gRPC address → `Builder.target()`. Env: `OLO_TEMPORAL_TARGET`. |
| **olo.configuration.dir** | Regional folders with `*.json` `WorkflowDefinition` files. Env: `OLO_CONFIGURATION_DIR`. Drives tenant id, default task queue, and **workflow type** via `ResolvedOloRuntimeSettings`. |
| **olo.chat.callback-base-url** | URL workers use for run event callbacks. Env: `OLO_CHAT_CALLBACK_BASE_URL`. |

`DemoConfig` builds `TemporalClient` from `olo.temporal.target` and `ResolvedOloRuntimeSettings` (namespace, workflow type). It also exposes beans `oloTaskQueue`, `oloDefaultTenantId`, and `oloCallbackBaseUrl`.

**Workflow JSON** (e.g. `olo-mono/olo-definition/olo-configuration/default/ask.json`):

| Field | Backend use |
|-------|-------------|
| `workflowType` | SDK builder → `newChatWorkflowStub` (e.g. `olo`) |
| `queue` | Temporal task queue; default bean + per-message override from chat UI preset |
| `id` | Chat profile / pipeline id in `GET /api/ui/context` |
| `role`, `shortDescription`, `emoji`, `runAgain` | Chat UI presets (not used by SDK) |

`ResolvedOloRuntimeSettings` reads the **first** workflow (sorted by `id`) in the default region for default `taskQueue` and `workflowType`. Each chat message can pass a different **`taskQueue`** (from the selected preset’s `queue`, e.g. `ask`, `fast`).

### 4.2 Usage in backend

| Layer | Class | Role |
|-------|-------|------|
| Application | `RunServiceImpl` | `WorkflowRunner.startChatRun` / `signalHumanInput` |
| Abstraction | `WorkflowRunner` → `SdkWorkflowRunner` | Maps completion to domain events |
| SDK | `TemporalClient` | Temporal start, signal, await result |

1. **Start** — `WorkflowInput` built in backend; `SdkWorkflowRunner` calls `temporalClient.startChatWorkflow(runId, taskQueue, input)` and awaits result on `workflowCompletionExecutor`.
2. **Signal** — `temporalClient.signalHumanInput(runId, approved, message)`.
3. **Completion** — `RunServiceImpl` appends SYSTEM COMPLETED/FAILED from `WorkflowRunCompletion` callbacks.

Payload shape (`WorkflowInput`) is defined in **olo-workflow-input**; the SDK does not depend on it.

---

## 5. Configuration model (SDK vs backend)

| Concern | Owner |
|---------|--------|
| Temporal target, namespace | SDK builder (values from Spring config + `ResolvedOloRuntimeSettings`) |
| Workflow type for chat stub | SDK builder (`workflowType` from `workflow.json`) |
| Task queue per run | Backend `WorkflowOptions` (preset `queue` or default) |
| Callback URL, tenant id | Backend beans; not in SDK |
| Chat profiles / UI | Backend `UiContextController`; not in SDK |

---

## 6. Error handling and resilience

- **Current** — Temporal SDK exceptions propagate to callers.
- **Planned** — SDK-level exception wrapping; optional retry/timeout helpers.

---

## 7. Versioning and compatibility

- **SDK** — Semantic versioning (`0.0.1-SNAPSHOT`).
- **Temporal SDK** — Documented compatible version: **1.26.0** (`olo-temporal-sdk/build.gradle`).

---

## 8. Future extensions

- Typed workflow interfaces (e.g. `ChatWorkflow`) and stub factories.
- TLS and retry/timeout on `WorkflowServiceStubsOptions`.
- SDK exception types and observability hooks.
- Integration tests against Temporal test environment.

---

## 9. Related documents

| Document | Content |
|----------|---------|
| [DESIGN.md](./DESIGN.md) | Goals, non-goals, usage patterns. |
| [olo/.env.example](../../.env.example) | Backend env vars (`OLO_TEMPORAL_TARGET`, `OLO_CONFIGURATION_DIR`, …). |
| [olo-chat/docs/CHAT_UI.md](../../../olo-chat/docs/CHAT_UI.md) | Frontend presets, task queues per profile. |
| [olo-mono/olo-definition/olo-configuration/default/](../../../olo-mono/olo-definition/olo-configuration/default/) | Example workflow JSON files. |
