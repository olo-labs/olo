# Olo SDK + Backend – Detailed Design Document

This document defines:

- **Phase 1 goal** (working chat flow, real-time UI, simple DB)
- System boundaries
- Responsibilities
- SDK architecture
- Backend architecture
- **Chat BE detailed design** (core domain objects, live flow)
- Execution model
- Event model
- Human step handling
- Persistence model
- API contracts
- Scaling strategy
- Future-proofing for replay & diff
- **Failure and operational semantics** (callback retry, event ordering, idempotency, human signal race): see [FAILURE_AND_OPERATIONAL.md](FAILURE_AND_OPERATIONAL.md).

---

## 1. System Overview

### Final Architecture Model (Refined)

**Live execution path (end users):**

```
              ┌───────────────┐
              │   olo-chat    │
              │  (end users)  │
              └───────┬───────┘
                      │ REST + SSE
                      ▼
              ┌───────────────┐
              │      olo      │
              │  Chat BE      │
              └───────┬───────┘
                      │
                      ▼
                olo-sdk
                      │
                      ▼
                  Temporal
```

**Inspection path (admin):**

```
              ┌───────────────┐
              │   olo-ui      │
              │  (admin)      │
              └───────┬───────┘
                      │ REST
                      ▼
              ┌───────────────┐
              │   olo-ui-be   │
              │  Admin BE     │
              └───────┬───────┘
                      │
                      ▼
              Shared Database
```

**Critical principle:**

- **Chat BE** handles **live execution** (sessions, runs, streaming, persistence into the shared store).
- **Admin BE** handles **inspection, replay, and diff** (read-only over the same execution data).
- Both backends share the **same execution data store**; no duplicate event storage.

**Key philosophy:**

- **Olo owns the execution intelligence model.** Workflow structure, node types (planner, model, tool, human, system), execution events, and business semantics are defined and controlled by Olo.
- **Temporal is the durable execution engine.** Chat BE (via olo-sdk) is the only component that talks to Temporal; Admin BE never touches Temporal.

**Phase 1 vs later:** Phase 1 implements only the **live path** (olo-chat → Chat BE → olo-sdk → Temporal) with a **simple chat database**. The inspection path (olo-ui, Admin BE, shared store) is post–Phase 1; the Phase 1 chat DB becomes that shared store when Admin BE is introduced.

---

## 1A. Phase 1 Goal

**Working system that does:**

1. **User sends chat message** — olo-chat sends message; Chat BE creates ChatMessage + ChatRun, starts workflow.
2. **Planner decides next step** — Workflow runs planner activity; next action (model / tool / human / done) is chosen.
3. **Optional tool call** — If planner chooses tool, tool activity runs; result fed back into workflow.
4. **Model generates response** — Model activity produces assistant text (or stream).
5. **Optional human approval** — If planner chooses human step, workflow waits; user approves or provides input in UI; Chat BE signals workflow; execution continues.
6. **Final answer returned** — Workflow completes; final assistant message is persisted and available to the UI.
7. **UI shows planner + steps in real time** — olo-chat consumes SSE stream of ExecutionEvents; UI renders planner decisions, tool calls, model output, and human steps as they happen.

**Infrastructure for Phase 1:**

- **Separate simple DB for chat** — Chat BE uses a dedicated, simple database for chat data (sessions, messages, runs, execution events). No Admin BE or shared “admin” store required for Phase 1; the same DB can later be promoted to the shared execution store when Admin BE is introduced.

Phase 1 delivers end-to-end chat with planner → optional tool → model → optional human → final answer, with real-time visibility in the UI and a single, simple chat database.

**Demo story (implemented):** User types “Search news about Tesla.” → UI shows PLANNER → TOOL → MODEL → Assistant responds. User types “Send this for approval.” → HUMAN step appears → User clicks approve → Workflow resumes → Final message appears. See [DEMO.md](DEMO.md) for run instructions.

---

## 2. System Boundaries

| Component | In scope | Out of scope |
|-----------|----------|--------------|
| **olo-chat (end users)** | REST/SSE consumer, run creation, event stream consumption | Workflow logic, persistence, Temporal |
| **olo (Chat BE)** | REST + SSE API, validation, auth, olo-sdk, **write** to execution store, live streaming | Admin UI, replay/diff execution logic |
| **olo-ui (admin)** | REST consumer for inspection, replay, diff UIs | Chat sessions, live execution, Temporal |
| **olo-ui-be (Admin BE)** | REST API for admin, **read** from shared DB, replay/diff queries | Temporal, live event streaming, chat APIs |
| **Shared Database** | Execution events, run/session data (written by Chat BE, read by both) | Temporal history, worker state |
| **olo-sdk** | Temporal client abstraction, workflow/activity stubs, connection/config | Domain models, UI, persistence |
| **Temporal** | Workflow execution, durability, task queues, retries | Olo domain rules, event schema |
| **Workers** | Execute activities (model, tools, human steps, RAG), read workflow input | REST API, client connectivity |

**Boundary rules:**

- **Chat BE** is the only service that uses **olo-sdk** and talks to **Temporal**. Admin BE never connects to Temporal.
- **Execution data store** is written by Chat BE (and workers via Chat BE or a shared write path); Admin BE only reads for inspection, replay, and diff (post–Phase 1).
- Clients never see raw Temporal workflow IDs or task queue names; stable API identifiers (e.g. `runId`) are used.
- Workers are started and polled independently of both backends.

**Phase 1:** Only olo-chat and Chat BE are in scope. Admin BE (olo-ui-be, olo-ui) and “Shared Database” as a second reader are out of scope; Chat BE uses a single **simple chat DB** that later becomes the shared store.

---

## 3. Responsibilities

### 3.1 olo (Chat Backend) — Live Execution

**Scope:**

- **Chat sessions** – create, list, get session state.
- **Chat messages** – send message, list messages (within a session).
- **Chat runs** – create run, get run status; each run is one workflow execution.
- **RAG** – configuration and invocation as part of run (via workers; Chat BE orchestrates).
- **Tool calls** – exposed as part of run execution; Chat BE streams tool activity via events.
- **Human steps** – accept human input (e.g. `POST .../human-input`), signal workflow, stream human step events.
- **Live event streaming** – SSE (or equivalent) for execution events to olo-chat.
- **Run persistence** – write all execution events (e.g. `OloExecutionEvent`) into the **shared execution data store**.

**Responsibilities:**

- Expose versioned REST + SSE APIs for the above.
- Validate and authorize requests (tenant, identity).
- Use **olo-sdk** to start workflows, signal (e.g. human input), and query.
- Persist every execution event to the shared store so Admin BE and replay/diff can read them.
- Map SDK/Temporal errors to stable HTTP and problem-details responses.
- Own configuration (Temporal target, namespace, task queue) and pass it to the SDK.

### 3.2 olo-ui-be (Admin Backend) — Inspection, Replay, Diff

**Scope:**

- **Inspection** – list runs, get run details, get event stream for a run (read from shared DB).
- **Replay** – trigger or serve replay of a run (same events, possibly different code version); read from shared store.
- **Diff** – compare two runs (e.g. same input, different code); read from shared store.

**Responsibilities:**

- Expose REST APIs for olo-ui (admin) only; no SSE for live chat.
- **Read-only** access to the shared execution data store; no Temporal, no olo-sdk.
- Serve run history, event lists, and derived data (replay result, diff result) from the shared store.

### 3.3 Olo SDK

- Used **only by Chat BE**. Manage Temporal connection lifecycle (`WorkflowServiceStubs`, `WorkflowClient`).
- Expose a simple, opinionated API: start workflow, signal, query (no raw Temporal types in backend).
- Provide typed workflow/activity interfaces and stub factories for Olo use cases.
- Wrap Temporal exceptions into SDK-level exceptions with clear, actionable messages.
- Centralize retry/timeout and naming (workflow ID, task queue) so Chat BE stays decoupled from Temporal.

### 3.4 Temporal

- Execute workflows durably and schedule activities on task queues.
- Only Chat BE (via olo-sdk) communicates with Temporal; no Olo domain logic inside Temporal.

### 3.5 Workers (Model / Tools / Human / RAG)

- Poll task queues and execute activities.
- Read workflow input (e.g. via `olo-worker-input`: cache/file, `WorkflowInput`).
- Report results and failures back through Temporal; human steps interact with external systems and complete via signals. The executor (**olo-executor**) is a separate process, not part of the backend.

---

## 4. SDK Architecture

### 4.1 Core Component: TemporalClient

- **Role:** Single entry point for Temporal access. Owns `WorkflowServiceStubs` and `WorkflowClient`. **Scope: connection and lifecycle only**; the SDK does not abstract workflow semantics (see olo-sdk/docs/ARCHITECTURE.md).
- **Configuration:** Target, namespace, and workflow type; supplied via builder. Spring Boot (DemoConfig) wires from properties and env (e.g. `OLO_WORKFLOW_TYPE`).
- **API:** `getWorkflowClient()`, `newChatWorkflowStub(WorkflowOptions)` (encapsulates workflow type name so backend never uses it), `close()` for shutdown. Backend builds `WorkflowOptions` (workflow id, task queue) and passes a **WorkflowInput** object to `stub.start(workflowInput)`; Temporal serializes it as JSON for the worker.

### 4.2 Workflow and Activity Abstractions (Current)

- The SDK provides **newChatWorkflowStub(WorkflowOptions)** only. Typed workflow interfaces (e.g. start, signal, query) are optional future work; today the backend uses untyped stubs and Temporal SDK types for start/signal.

### 4.3 Error Handling

- Current: Temporal SDK exceptions propagate to the caller. Planned: wrap into SDK-level exceptions and map to HTTP in the backend.

### 4.4 Module Layout

- **olo-sdk:** Java library; dependency: Temporal Java SDK. Used **only by Chat BE**. No HTTP or persistence; configuration is injectable.

---

## 5. Backend Architecture

### 5.1 Two Backends, One Execution Store (Post–Phase 1)

- **olo (Chat BE):** Spring Boot; REST + SSE; uses **olo-sdk** → Temporal. **Writes** execution events (and session/run metadata) to the execution store.
- **olo-ui-be (Admin BE):** Spring Boot; REST only. **Reads** from the **shared execution data store**; no Temporal, no olo-sdk. Serves inspection, replay, and diff to olo-ui.

**Phase 1:** Only Chat BE exists. It uses a **simple chat database** (sessions, messages, runs, ExecutionEvent). No Admin BE; that same DB is the only store. When Admin BE is added later, this chat DB becomes the **shared execution data store** (Chat BE writer, Admin BE reader).

Shared database (post–Phase 1) holds: runs, sessions, messages, and the canonical **event log**. Chat BE is the writer; Admin BE is read-only.

### 5.2 Chat BE (olo) — Layers

- **Controller:** REST under `/api/**`, SSE for event stream; DTOs in **api.request** / **api.response** (e.g. `CreateRunRequest`, `CreateRunResponse`), `@Valid` validation, optional `taskQueue` in request body.
- **Service:** **RunService** (interface) and **RunServiceImpl** (in **service.impl**) handle run lifecycle: append events, start workflow, signal human input. Builds **WorkflowInput** via **WorkflowInputSerializer** (in **workflow.impl**; olo-worker-input model and `WorkflowInput.builder()`), then calls **olo-sdk**: `temporalClient.newChatWorkflowStub(options)` and `stub.start(workflowInput)`. Task queue comes from request or config (`olo.temporal.task-queue`). Writes events to the chat DB and streams via SSE.
- **SDK usage:** RunService receives `TemporalClient` from config; all Temporal traffic goes through olo-sdk. Workflow type is set in SDK builder (config or `OLO_WORKFLOW_TYPE`).
- **Config:** DemoConfig provides Temporal target, namespace, workflow type, task queue, callback base URL; `TemporalClient` bean is built with target, namespace, workflowType.

### 5.3 Admin BE (olo-ui-be) — Layers

- **Controller:** REST under `/api/**` (admin-scoped paths or host). No SSE for live chat.
- **Service:** Read runs, event streams, and derived data from shared store; replay/diff logic (replay engine consumes stored events; diff compares two event streams).
- **No SDK:** No Temporal client; no olo-sdk dependency. Data access is to shared DB only.

### 5.4 Run Creation Flow (Chat BE, Current)

1. `POST /api/runs` with `CreateRunRequest` (tenantId, input type/message; optional `taskQueue`). Alternatively `POST /api/sessions/{sessionId}/messages` for session-bound flow.
2. Controller validates; generates **runId** (UUID from Chat BE; globally unique; tenant is in session/context, not in the id), creates run record, builds initial `OloExecutionEvent` (root, SYSTEM, STARTED) and appends it. Run ID ownership: see [FAILURE_AND_OPERATIONAL.md](FAILURE_AND_OPERATIONAL.md) §4.
3. **WorkflowInput** is built via **WorkflowInputSerializer.build(...)** (tenantId, sessionId, messageId, user message as plain string, pipeline/task queue, transactionId, runId, callbackBaseUrl). Input type is **STRING**, value is the plain message; storage is LOCAL only.
4. Service starts the Temporal workflow: `WorkflowOptions` with workflow id `run-{runId}` and effective task queue (from request or config), then `stub.start(workflowInput)`. The **WorkflowInput object** is passed so Temporal serializes it as JSON; the executor receives it as `WorkflowInput`.
5. Service returns `CreateRunResponse(runId)`. Clients use runId for SSE (`GET /api/runs/{runId}/events`) or polling.

### 5.5 Event Streaming (Chat BE)

- SSE endpoint e.g. `GET /api/runs/{runId}/events` streams **flat, ordered** `OloExecutionEvent` records (sorted by sequenceNumber). Catch-up sends existing events in order; then new events are pushed as they are written. Phase 1: only Chat BE and olo-chat use it. Later: same event log is read by Admin BE for inspection, replay, and diff.

### 5.6 Chat BE: flat events only — no tree reconstruction

**Chat BE must not** rebuild or expose:

- Node trees  
- Parent-child graphs  
- Execution DAG / visualization models  

Chat BE **only** stores and returns **flat ordered events** (list/stream sorted by sequenceNumber). Each event may carry `nodeId` and `parentNodeId` as payload fields (from the executor); Chat BE does **not** use those to build a tree or graph.

**Where tree/DAG logic belongs:**

- **Admin BE** — inspection, run details, event stream for a run (read-only; can build tree for admin UI if needed).  
- **Replay service** — consumes flat event stream; any tree view is internal to replay.  
- **UI transformation layer** — olo-chat or olo-ui builds parent-child / DAG views from the flat event list if needed.

**Replay/diff are not Chat BE:** Chat BE must **not** expose replay or diff endpoints (e.g. no `POST /api/runs/{id}/replay`, `POST /api/diff`, `GET /api/runs/{id}/replay`). Replay is not live execution; it belongs in Admin BE, replay service, or tooling. Chat BE only does live execution and serves flat ordered events.

**Stable product-level fields only:** Chat BE must **not** expose debug or internal state. Strip (do not return) workflow ID, task queue, Temporal namespace, worker identity, and internal planner metadata. Events sent over SSE are sanitized so that `metadata`, `input`, and `output` maps do not contain these internal keys. Run responses (GET run) do not include task queue or Temporal identifiers.

**No execution interpretation:** Chat BE must **not** decide next node type, compute execution branches, simulate transitions, or validate node transition legality. **Workflow owns execution semantics.** BE owns **projection only**: append events from the executor, store them, derive run status from the event stream, and serve flat ordered events. Any logic that interprets “what should happen next” or “is this transition allowed” belongs in the workflow/executor, not in Chat BE.

**No complex aggregation over event history:** Chat BE must **not** summarize tool performance, compute execution depth, count planner steps, or calculate diff candidates. BE should not analyze history beyond **current status** (e.g. one derived status field per run). Aggregations and analytics over the event log belong in Admin BE, analytics pipelines, or replay/diff tooling.

### 5.7 Mental model: projection + command gateway

**Chat BE is:** A **projection** (store and serve events; derive current run status) and a **command gateway** (start workflow, signal human input, accept executor callbacks). Nothing more.

**Chat BE is NOT:** A workflow engine, a state machine interpreter, a replay engine, a debugging tool, or an analytics service.

**Run status derivation — keep minimal:** Only: if any event is FAILED → failed; else if last event is SYSTEM + COMPLETED → completed; else if last event is HUMAN + WAITING → waiting_human; else → running. Nothing more.

**SSE:** Filtering by runId is fine. No transformation logic (beyond stripping internal keys for API safety).

**Layers:** controller → service (RunService) → temporalClient (SDK) / executionEventStore. Nothing else. No extra domain logic layer interpreting execution.

**Self-audit checklist (Chat BE = thin orchestration boundary):** No class named *Replay*; no tree reconstruction; no diff logic; no planner simulation; no historical analytics; no Temporal history reading. No replay hooks, debug utilities, tree-building logic, deep execution interpretation, or cross-run comparison. If any appear → remove. Keep run/message/event storage and minimal status derivation only.

---

## 5A. Chat Backend Detailed Design (Focused)

This section defines **olo (Chat BE)** cleanly: core domain objects and the live chat flow. All live behavior is handled here.

### 5A.1 Core Domain Objects

**ChatSession**

| Field       | Description                    |
|------------|--------------------------------|
| sessionId  | Unique session identifier      |
| tenantId   | Tenant (org) owning the session |
| createdAt  | Creation timestamp             |

**ChatMessage**

| Field       | Description                              |
|------------|------------------------------------------|
| messageId  | Unique message identifier                |
| sessionId  | Session this message belongs to          |
| role       | user \| assistant \| system               |
| content    | Message body                             |
| runId      | Run triggered by this message (if any)   |
| createdAt  | Creation timestamp                       |

**ChatRun**

| Field        | Description                          |
|-------------|--------------------------------------|
| runId       | Unique run identifier (Temporal workflow) |
| sessionId   | Session this run belongs to          |
| messageId   | User message that triggered this run |
| status      | running \| completed \| failed \| waiting_human — **derived from event stream** (see below), not set by executor |
| correlationId | Cross-service tracing; set at run creation, propagated to every event |
| workflowVersion, modelVersion, plannerVersion | Execution versioning for diff (optional at creation) |
| model       | Model identifier used for this run   |
| temperature | Model temperature                    |
| ragEnabled  | Whether RAG was enabled              |
| createdAt   | Creation timestamp                  |

**Run status derivation:** Run status is **derived from the latest ExecutionEvent stream** (single source of truth). After each append, the backend derives status from the event log (e.g. last HUMAN WAITING → `waiting_human`, last SYSTEM COMPLETED → `completed`, any FAILED → `failed`, else `running`). This avoids dual state and keeps status consistent with the event stream.

**ExecutionEvent (event store as product)**

This table is **not just logging** — it is a **core domain object** and the foundation for replay, diff, admin UI, audit, observability, model evaluation, regression testing, and experiment comparison. Stored in the chat DB (Phase 1); Chat BE writes. Later, when Admin BE exists, the same table is the shared execution store; Admin BE reads for inspection, replay, and diff. **Source of truth for execution is Temporal;** this store is a **projection** from executor callbacks (see [FAILURE_AND_OPERATIONAL.md](FAILURE_AND_OPERATIONAL.md)).

**Treat it like gold:** The store must be **append-only**; indexed by **(runId, sequenceNumber)** and by **tenantId**; efficient to **stream** (SSE, replay) and to **diff** (compare runs). No dual state: run status is **derived from the event stream** (see Run status derivation below), not stored separately.

| Field          | Description                                |
|----------------|--------------------------------------------|
| eventVersion   | Schema version (e.g. 1) for evolution     |
| eventId        | Unique event identifier (e.g. sequence)   |
| runId          | Run this event belongs to                  |
| sequenceNumber | Order within run (for deterministic replay)|
| stepId         | Step (node) identifier within the run      |
| parentStepId   | Parent step in the execution tree; null for root |
| stepType       | SYSTEM \| PLANNER \| MODEL \| TOOL \| HUMAN |
| status         | STARTED \| COMPLETED \| FAILED \| WAITING |
| inputJson      | Step input (JSON)                         |
| outputJson     | Step output (JSON)                        |
| metadataJson   | Tenant, correlation IDs, errors (JSON)    |
| timestamp      | Event time (epoch millis)                 |

*Mapping:* `stepId` / `parentStepId` / `stepType` correspond to `nodeId` / `parentNodeId` / `nodeType` in the in-memory execution model (`OloExecutionEvent`); the persisted schema uses the above names for clarity in the shared store.

### 5A.2 Live Flow (Chat)

1. **User sends message**
   - olo-chat sends the message to Chat BE (e.g. `POST /api/sessions/{sessionId}/messages` or equivalent).

2. **Chat BE: create message and run**
   - Creates **ChatMessage** (role=user, content, sessionId).
   - Creates **ChatRun** (sessionId, messageId, status=running, model/temperature/ragEnabled from request or defaults).
   - Starts Temporal workflow via **olo-sdk** (workflow ID e.g. `run-{runId}`), passing runId, sessionId, messageId, and input payload.

3. **SDK / workflow emits structured events**
   - As the workflow and activities execute, they produce events (SYSTEM start, PLANNER, MODEL, TOOL, HUMAN, etc.).

4. **Chat BE: persist and stream**
   - For each event: **persists** it as **ExecutionEvent** in the chat DB (eventId, runId, stepId, parentStepId, stepType, status, inputJson, outputJson, metadataJson, timestamp).
   - **Streams** the same event to olo-chat via **SSE** (e.g. `GET /api/runs/{runId}/events`).

5. **If HUMAN step**
   - The **workflow** enters a **workflow state** waiting on a signal (`Workflow.await`). An activity reports stepType=HUMAN, status=WAITING to the BE (so the UI can show “waiting for human”). This is **not** a long-running activity; the workflow thread is blocked until the signal arrives.
   - Chat BE persists the **WAITING** event and streams it. UI shows approval / input form; user responds in olo-chat.
   - User response is sent to Chat BE (e.g. `POST /api/runs/{runId}/human-input`). Chat BE **signals** the Temporal workflow with the human response. Workflow unblocks and continues; Chat BE persists HUMAN COMPLETED event and streams it. See [FAILURE_AND_OPERATIONAL.md](FAILURE_AND_OPERATIONAL.md) for human signal race (double submit) behavior.

**Summary:** All live behavior (create message, create run, start workflow, persist events, stream via SSE, handle human steps and signal) is handled in **Chat BE**. Phase 1: one simple chat DB. Later: Admin BE reads the same ExecutionEvent table for inspection, replay, and diff.

---

## 6. Execution Model

### 6.1 Run and Nodes

- A **run** is one execution of an Olo workflow (one Temporal workflow execution keyed by runId).
- Execution is modeled as a **tree of nodes**; each node has a type and status and can have children.

### 6.2 Node Types

Defined in the backend/domain (e.g. `NodeType` enum):

- **SYSTEM** – Workflow lifecycle (e.g. root start/end).
- **PLANNER** – Planning step (e.g. next action).
- **MODEL** – LLM call.
- **TOOL** – Tool invocation.
- **HUMAN** – Human-in-the-loop step (wait for input or approval).

### 6.3 Node Status

- **STARTED** – Node has begun.
- **COMPLETED** – Node finished successfully.
- **FAILED** – Node failed (with error info in output/metadata).
- **WAITING** – Node is blocked (e.g. human step waiting for input).

### 6.4 Flow

- Backend (or workflow) emits an event when a node starts; workers (activities) perform work and report completion or failure; workflow advances and may spawn child nodes (e.g. model → tool → human). All of this is reflected as a sequence of `OloExecutionEvent` records.

---

## 7. Event Model

### 7.1 OloExecutionEvent (Core Structure)

Used for: workflow start, planner decision, model call, tool call, human wait/completion, retry, failure. Required for replay and diff. Schema is versioned for evolution.

**Fields:**

- **eventVersion** – Schema version (e.g. 1). Increment when fields or semantics change; enables backward compatibility and replay.
- **runId** – Identifies the run.
- **nodeId** – Unique node identifier within the run (e.g. `"root"`, `"n1"`, `"n2"`).
- **parentNodeId** – Parent in the node tree; null for root.
- **nodeType** – SYSTEM | PLANNER | MODEL | TOOL | HUMAN.
- **status** – STARTED | COMPLETED | FAILED | WAITING.
- **eventType** – Explicit type for analytics: NODE_STARTED | NODE_COMPLETED | NODE_FAILED | NODE_WAITING. Cleaner than deriving from nodeType + status.
- **timestamp** – Epoch millis.
- **sequenceNumber** – **Required** for executor callbacks; idempotency key is (runId, sequenceNumber). Events always served sorted by sequenceNumber (see [FAILURE_AND_OPERATIONAL.md](FAILURE_AND_OPERATIONAL.md)).
- **correlationId** – Cross-service tracing; set at run creation and propagated to every event.
- **input** – Node input (e.g. prompt, tool name, human question).
- **output** – Node output (e.g. model response, tool result, human reply).
- **metadata** – Tenant, error details, etc.

### 7.2 Semantics

- One event per state transition (e.g. node STARTED then COMPLETED or FAILED).
- **sequenceNumber** is enforced strictly: required for callbacks; duplicate rejected (409); events always sorted by sequenceNumber for replay/diff.
- **Event idempotency key:** (runId, sequenceNumber) — unique constraint; retries do not create duplicate events.
- No removal or mutation of past events; corrections via new events if needed.

### 7.3 Execution versioning (run-level)

Store **workflowVersion**, **modelVersion**, **plannerVersion** on the run (e.g. in CreateRunRequest / RunRecord). So diff actually means something: when comparing two runs, you know which workflow/model/planner versions produced each. Optional at creation; returned in GET run and available for admin/diff.

---

## 8. Human Step Handling

### 8.1 Lifecycle

1. Workflow decides a human step is required. The **workflow** enters a **workflow state** waiting on a signal (`Workflow.await`); it is **not** a long-running activity. An activity **reports** an `OloExecutionEvent` with `NodeType.HUMAN`, status `WAITING` to the BE so the UI can show "waiting for human."
2. Backend persists the WAITING event and streams it via SSE.
3. Client is notified via SSE (or poll); UI shows “waiting for human” and where to provide input.
4. User submits input via REST (e.g. `POST /api/runs/{runId}/human-input`). Backend should reject a second submit if the run is no longer in `waiting_human` (see [FAILURE_AND_OPERATIONAL.md](FAILURE_AND_OPERATIONAL.md) §7).
5. Backend signals the workflow with the human response; workflow unblocks and continues.
6. New event is emitted: HUMAN node COMPLETED with output containing the user’s response.

### 8.2 Design Choices

- Human steps are first-class node types and events; they participate in the same replay/diff model.
- Timeouts and escalation (e.g. remind or expire) can be implemented as workflow logic and/or backend policies, emitting further events as needed.

---

## 9. Persistence Model

### 9.1 Chat DB (Phase 1) and Shared Execution Store (Later)

- **Phase 1:** Chat BE uses a **separate, simple database for chat**: tables (or equivalent) for **ChatSession**, **ChatMessage**, **ChatRun**, and **ExecutionEvent**. Single writer (Chat BE). No Admin BE.
- **Later:** When Admin BE is introduced, this same database becomes the **shared execution data store**: Chat BE remains the only writer; Admin BE reads for inspection, replay, and diff.
- **Single source of truth** for execution data: runs, sessions, messages, and the **event log** (ExecutionEvent / `OloExecutionEvent` per run).
- **Written by:** **Chat BE only** (when starting a run; when workflow/activities report node start/complete/fail).
- **Read by:** **Chat BE** for SSE, “get run status,” and run/session APIs; **Admin BE** (later) for inspection, replay, and diff (read-only).

### 9.2 Execution Events (event store as product)

- **Store:** Append-only log of ExecutionEvent (same shape as `OloExecutionEvent`) per run. **Indexed by (runId, sequenceNumber)** and by **tenantId**; efficient to stream and to diff. In Phase 1: in-memory or simple DB; later: shared execution data store.
- Events are **never deleted or mutated**. This store powers replay, diff, admin UI, audit, observability, evaluation, and regression testing — treat it as a core product asset.
- **UNIQUE(runId, sequenceNumber):** Backend rejects duplicate sequence numbers (e.g. 409 Conflict). Events are always served **sorted by sequenceNumber** for replay/diff correctness.

### 9.3 Workflow Input (Large Payloads)

- **olo-worker-input** handles large or sensitive input: producer side writes to cache or file; consumer side (executor) reads via `WorkflowInput` (version, inputs, context, routing, metadata). Model includes `WorkflowInput`, `WorkflowInputBuilder` (use `WorkflowInput.builder()`), and DTOs in `org.olo.input.model`.
- Temporal payload size limits are avoided by storing payloads externally and passing references in workflow args; SDK/workers use the same input model for consistency.

### 9.4 What Temporal Stores

- Temporal keeps workflow and activity history (its own event log). Olo does not rely on Temporal history as the primary audit trail; the canonical execution view is Olo’s event log, which is designed for replay and diff.

---

## 10. API Contracts

### 10.1 Chat BE — Phase 1

**Sessions (planned)**

- **POST /api/sessions** – Create chat session (tenantId, optional metadata). Returns sessionId.
- **GET /api/sessions/{sessionId}** – Get session (and optionally list messages).

**Messages and runs (Phase 1 flow)**

- **POST /api/sessions/{sessionId}/messages** – User sends chat message. Request: role=user, content; optional model, temperature, ragEnabled. Chat BE: creates ChatMessage, creates ChatRun, starts Temporal workflow via olo-sdk, returns runId (and optionally messageId). This is the main Phase 1 entry point.
- **GET /api/runs/{runId}** – Run status/summary (e.g. last event or aggregated state).
- **GET /api/runs/{runId}/events** – SSE stream of ExecutionEvent for the run (planner, tool, model, human steps in real time for UI).
- **POST /api/runs/{runId}/human-input** – Submit human response for a waiting HUMAN node (body: user approval or input). Chat BE signals workflow.

**Legacy / alternate**

- **POST /api/runs** – Create run without session/message (e.g. `CreateRunRequest`: tenantId, input.type, input.message). Validates, creates initial event, starts workflow, returns runId. Use when session/message model is not yet implemented or for backward compatibility.

### 10.2 Chat BE — Tenants and queues (implemented)

- **GET /api/tenants** – List tenants for UI dropdown. Returns default tenant (from `olo.default-tenant-id`) and optionally config/Redis-discovered ids. If Redis is unavailable, returns at least default; no 500.
- **GET /api/tenants/{tenantId}/queues** – List queue names for keys `<tenantId>:olo:kernel:config:*` in Redis. Used by olo-chat under Chat/RAG. Returns [] when Redis unavailable.
- **GET /api/tenants/{tenantId}/queues/{queueName}/config** – Queue config JSON from Redis (e.g. `pipelines` for Conversation pipeline dropdown). Returns {} when key missing or Redis unavailable.

See [API_PAYLOADS.md](API_PAYLOADS.md) for request/response examples. Redis is optional; KernelConfigQueueService is optional bean.

### 10.3 Chat BE — Additional (Planned)

- **GET /api/sessions/{sessionId}/messages** – List messages in session.
- Idempotency (e.g. idempotency key) for message or run creation can be added later.

### 10.4 Admin BE (olo-ui-be) — Inspection, Replay, Diff (Planned)

- **GET /api/admin/runs** (or similar) – List runs (filter by tenant, time range, etc.); read from shared store.
- **GET /api/admin/runs/{runId}** – Run details and full event list; read from shared store.
- **POST /api/admin/replay** (or **GET /api/admin/runs/{runId}/replay**) – Trigger or retrieve replay result; replay engine reads event stream from shared store.
- **POST /api/admin/diff** (or **GET /api/admin/runs/{runIdA}/diff/{runIdB})** – Compare two runs; read both event streams from shared store.

All Admin BE endpoints are read-only on the shared execution data store; no Temporal.

### 10.5 Conventions

- REST only; no raw Temporal types in request/response.
- Errors: problem-details style JSON with correlation ID where possible.
- Versioning: URL prefix (e.g. `/api/v1/runs`) or header when needed.

---

## 11. Scaling Strategy

### 11.1 Chat BE and Admin BE

- **Chat BE:** Stateless; scale horizontally. No in-memory workflow state; state in Temporal and in the shared execution store. Connection pooling to Temporal (olo-sdk); one `TemporalClient` per process or config scope.
- **Admin BE:** Stateless; scale horizontally. Read-only access to shared store; no Temporal. Can scale independently for heavy inspection/replay/diff load.

### 11.2 Workers

- Scale activity workers by task queue: add more worker processes/pods that poll the same queue.
- Separate task queues per activity type (e.g. model vs tool vs human) if needed for isolation and scaling.

### 11.3 Chat DB / Shared Store and Event Streaming

- **Phase 1 (chat DB):** Simple DB with high write throughput (Chat BE appends events) and ordered read by runId for SSE and get-run. E.g. relational table with runId + sequence index.
- **Later (shared store):** Same store; Admin BE adds read load for inspection, replay, diff. No schema change required.
- SSE: Chat BE reads from the chat DB (or from a message bus that workers publish to); avoid blocking workflow completion on slow clients.

---

## 12. Future-Proofing for Replay and Diff

### 12.1 Replay

- Replay is “re-run” of a run’s logic using the same event stream as input. Events are immutable and ordered; a replay engine can replay from event 1..N and compare with a new execution (e.g. after a code change).
- Keep `OloExecutionEvent` self-contained enough (input, output, nodeType, status) that a replayer does not need Temporal history; optional: store Temporal runId for correlation.

### 12.2 Diff

- Diff compares two runs (e.g. same inputs, different code or model): load both event streams, align by nodeId or sequence, compare input/output and status.
- Event schema should remain extensible (e.g. optional fields, metadata) without breaking ordering or core fields (runId, nodeId, parentNodeId, nodeType, status, timestamp, input, output).

### 12.3 Schema Evolution

- Add optional fields or new node types/statuses without removing or redefining existing ones.
- Version the event schema (e.g. in metadata or a top-level `eventVersion`) if format changes are needed later.

---

## Document History

- **Phase 1 goal:** Working chat flow (user message → planner → optional tool → model → optional human → final answer); UI shows planner + steps in real time via SSE; **separate simple DB for chat** (ChatSession, ChatMessage, ChatRun, ExecutionEvent). No Admin BE in Phase 1; same DB becomes shared store when Admin BE is added.
- **Chat BE detailed design:** Core domain objects (ChatSession, ChatMessage, ChatRun, ExecutionEvent); live flow (create message/run, persist and stream events, human step signal). API: sessions, POST message (create message + run, start workflow), GET run, SSE events, POST human-input.
- **Refined architecture:** Two backends—**olo (Chat BE)** for live execution (olo-chat → REST + SSE → olo-sdk → Temporal); **olo-ui-be (Admin BE)** for inspection, replay, diff (olo-ui → REST → shared DB). Shared execution data store; Chat BE writes, Admin BE reads. Phase 1 implements only Chat BE + simple chat DB.
- Single unified design for Olo SDK + Backend; aligns with existing backend/SDK design docs and current code: `RunsController`, `CreateRunRequest`/`CreateRunResponse`, `OloExecutionEvent`, `NodeType`, `NodeStatus`, `TemporalClient`, and `olo-worker-input` workflow input model.
