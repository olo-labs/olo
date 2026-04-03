# Olo – Architecture

High-level architecture of the **Olo** system: chat backend, SDK, Temporal, and workers. For detailed design (API contracts, persistence, scaling), see [DESIGN.md](DESIGN.md).

---

## 1. System Overview

Olo provides a **chat flow** with planner, optional tool calls, model, and optional human-in-the-loop steps. Execution is durable and observable via a stream of execution events.

**Phase 1 (current):** Live path only: **olo-chat** (UI) → **Chat BE** → **olo-sdk** → **Temporal**; the executor (olo-executor) runs workflows and activities and calls back to the backend. A simple chat database holds sessions, messages, runs, and the execution event log.

**Later:** An Admin BE and UI (olo-ui, olo-ui-be) will **read** the same execution store for inspection, replay, and diff. Chat BE remains the only writer and the only component that talks to Temporal.

---

## 2. Architecture Diagram

```
                    ┌─────────────────┐
                    │   olo-chat      │
                    │   (end users)   │
                    └────────┬────────┘
                             │ REST + SSE
                             ▼
                    ┌─────────────────┐
                    │   Chat BE       │
                    │   (olo / Spring) │
                    │                 │
                    │  • Sessions     │
                    │  • Messages     │
                    │  • Runs         │
                    │  • SSE / WS     │
                    │  • Tenants      │
                    │  • Queues       │
                    │  • Human input  │
                    └────────┬────────┘
                             │
                             │  olo-sdk
                             ▼
                    ┌─────────────────┐
                    │  Temporal       │
                    │  Server         │
                    │  (workflows)    │
                    └────────┬────────┘
                             │ task queue
                             ▼
                    ┌─────────────────┐
                    │  Olo Executor   │
                    │  • Workflow     │
                    │  • Activities   │
                    │  (planner,      │
                    │   tool, model,  │
                    │   human)        │
                    └────────┬────────┘
                             │ HTTP callback
                             ▼
                    ┌─────────────────┐
                    │   Chat BE       │
                    │  (append event,  │
                    │   broadcast SSE)│
                    └─────────────────┘
```

- **Chat BE** is the only service that uses **olo-sdk** and talks to **Temporal**.
- **Workers** execute workflow and activities; they report events back to the backend via HTTP (e.g. `POST .../api/runs/{runId}/events` or equivalent).
- **Execution events** are written by Chat BE (when starting a run; when receiving callbacks from the worker) and streamed to the UI via SSE.

---

## 3. Components

| Component      | Role |
|----------------|------|
| **olo-chat**   | Frontend; calls Chat BE REST APIs; consumes SSE and optional WebSocket for run events and liveness; tenant dropdown (GET /api/tenants); queues under Chat/RAG; queue config for pipeline dropdown. |
| **Chat BE**    | REST + SSE + WebSocket API; sessions, messages, runs; **tenants** (GET /api/tenants — default tenant from config, optional Redis-discovered); **queues** (GET /api/tenants/{id}/queues, GET .../queues/{name}/config from Redis keys `<tenantId>:olo:kernel:config:*`); starts/signals workflows via **olo-sdk**; writes execution events; streams events to UI; accepts human input and signals workflow. When Redis is unavailable, tenant and queue APIs return safe defaults (no 500). |
| **olo-sdk**    | Java library wrapping Temporal SDK; `TemporalClient` owns connection and `WorkflowClient`; used only by Chat BE. |
| **Temporal**   | Durable workflow execution; task queues; only Chat BE (via olo-sdk) connects. |
| **Temporal worker** | Separate process: polls the configured task queue, runs the registered workflow type and activities, and reports events to Chat BE via HTTP. **Two options in this ecosystem:** (1) **olo-executor** (Maven) — `OloChatWorkflow` / `OloChatWorkflowImpl`, simple chat path; (2) **olo-worker** (Gradle, often sibling repo **olo-labs**) — **`OloKernelWorkflow`** / `OloKernelWorkflowImpl`, Redis-backed configuration, execution trees, queues such as `olo.<region>.<pipeline>`. The backend workflow type must match the worker (`olo.temporal.workflow-type` / `OLO_WORKFLOW_TYPE`). |
| **Chat DB**    | Phase 1: sessions, messages, runs, execution event log. Single writer (Chat BE). Later becomes shared store when Admin BE is added. |
| **Redis** (optional) | Kernel config: keys `<tenantId>:olo:kernel:config:<queueName>` hold queue config JSON. Used by **KernelConfigQueueService** to list queues and return queue config (e.g. pipelines). If Redis is disabled or failing, GET /api/tenants still returns the default tenant; GET .../queues returns []. No 500. |

---

## 4. Data Flow (Live Chat)

1. **User sends message** → olo-chat `POST /api/sessions/{sessionId}/messages`.
2. **Chat BE** creates message and run, starts Temporal workflow via olo-sdk (workflow id `run-{runId}`).
3. **Worker** picks up the workflow; executes planner → optional tool → model → optional human; for each step it **calls back** to Chat BE to append an execution event.
4. **Chat BE** persists each event and **broadcasts** it on the SSE stream for that run.
5. **Human step:** Workflow emits HUMAN WAITING; UI shows approval; user submits; Chat BE receives `POST /api/runs/{runId}/human-input`, **signals** the workflow; worker continues.
6. When the workflow completes, Chat BE has the full event log and run status (e.g. completed).

All execution visibility (planner, tool, model, human) is through **OloExecutionEvent** records written by Chat BE and streamed via SSE.

---

## 5. Execution Model (Summary)

- **Run** = one Temporal workflow execution (id `run-{runId}`).
- **Nodes** form a tree: types **SYSTEM**, **PLANNER**, **MODEL**, **TOOL**, **HUMAN**; statuses **STARTED**, **COMPLETED**, **FAILED**, **WAITING**.
- Events are **append-only**; ordering is strict for replay and diff (post–Phase 1).

---

## 6. Repo Layout

| Path              | Description |
|-------------------|-------------|
| **src/**          | Chat BE (Spring Boot): controllers, services, stores, config; uses olo-sdk. Built with Gradle (root `build.gradle`). |
| **olo-sdk/**      | Temporal client library; `TemporalClient`, see `olo-sdk/docs/ARCHITECTURE.md`. Gradle (`olo-sdk/build.gradle`); included as subproject, built with root `./gradlew build`. |
| **olo-executor/** | Workflow executor: separate process running workflows and activities; callbacks to Chat BE. Maven-based. Not part of the backend. |
| **olo-worker-input/** | Serialize and deserialize workflow input (WorkflowInput JSON); model, cache/file handling for large payloads. Gradle subproject; publish to Maven local for workers. |
| **docs/**         | DESIGN.md (detailed design), DEMO.md (run instructions), this file. |

In a full **olo-labs** workspace, **olo-worker** (Temporal kernel worker) and **olo** (Chat BE) are often built together; see **`olo-worker/README.md`** and **`olo-worker/docs/architecture/`** for worker-specific bootstrap and configuration.

### Backend package layout (org.olo.app)

- **api.request** / **api.response** — DTOs for REST (e.g. CreateRunRequest, CreateRunResponse).
- **config** — Spring configuration (DemoConfig, WebConfig, etc.).
- **controller** — REST controllers (RunsController, SessionsController, HealthController, TenantsController, TenantQueuesController).
- **domain** — Execution model (NodeType, NodeStatus, OloExecutionEvent).
- **filter** — Request logging filter.
- **service** — RunService interface; KernelConfigQueueService (queue names and queue config from Redis; optional bean).
- **service.impl** — RunServiceImpl (workflow start, signal, append event).
- **store** — In-memory stores (ChatRunStore, ExecutionEventStore, etc.).
- **workflow.impl** — WorkflowInputSerializer (builds WorkflowInput for the executor).

### olo-executor package layout (org.olo.worker)

- **workflow** — OloChatWorkflow interface.
- **workflow.impl** — OloChatWorkflowImpl.
- **activities** — OloChatActivities interface.
- **activities.impl** — OloChatActivitiesImpl.
- **OloExecutorMain** — Bootstrap (registers workflow and activities, starts worker).

---

## 7. SDK and Backend (Summary)

- **Chat BE** is the only component that uses **olo-sdk** and talks to **Temporal**. It builds the workflow payload (**WorkflowInput**, via **olo-worker-input** and **WorkflowInputSerializer**), starts the workflow with `stub.start(workflowInput)`, and signals it for human input.
- **olo-sdk** provides connection and lifecycle only: target, namespace, workflow type, `WorkflowClient`, and `newChatWorkflowStub(WorkflowOptions)`. It does not define workflow semantics; the worker does.
- **Task queue** can come from the request (e.g. `taskQueue` in CreateRunRequest) or from backend config (`olo.temporal.task-queue`). **Workflow type** must match the worker: default property **`olo.temporal.workflow-type`** is **`OloChatWorkflowImpl`** (olo-executor); for **olo-worker** use **`OloKernelWorkflow`** (interface name). Override with **`OLO_WORKFLOW_TYPE`**.

For a complete explanation of how the SDK and backend work together (config, workflow start, payload format), see **[SDK_AND_BACKEND.md](SDK_AND_BACKEND.md)**.

---

## 8. Related Documents

- **[SDK_AND_BACKEND.md](SDK_AND_BACKEND.md)** — Complete guide: SDK scope, backend usage, WorkflowInput, config, and responsibilities.
- **[DESIGN.md](DESIGN.md)** — Detailed design: boundaries, responsibilities, SDK/backend architecture, domain objects, event model, APIs, persistence, scaling, replay/diff.
- **[WORKFLOW_INPUT.md](WORKFLOW_INPUT.md)** — WorkflowInput schema (version 1.0), example payload, builder (WorkflowInputBuilder), and relation to backend/executor.
- **[FAILURE_AND_OPERATIONAL.md](FAILURE_AND_OPERATIONAL.md)** — Callback retry semantics, event ordering, human step semantics, run ID ownership, failure scenarios, replay vs projection, idempotency, human signal race.
- **[DEMO.md](DEMO.md)** — How to build and run backend, Temporal, worker, and exercise the chat flow.
- **olo-sdk/docs/ARCHITECTURE.md** — SDK-specific architecture (TemporalClient, backend integration).
