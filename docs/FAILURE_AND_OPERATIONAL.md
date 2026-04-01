# Failure Handling and Operational Semantics

This document makes explicit: callback behavior, event ordering, human-step semantics, run ID ownership, replay vs projection, idempotency, and human-input race conditions. It addresses production-aware failure scenarios that are otherwise implicit.

---

## 1. Callback Model and Retry Semantics

**Current flow:** Executor (olo-executor) → HTTP POST → Chat BE (`POST /api/runs/{runId}/events`) → persist event → broadcast SSE.

**Risks:**

- Executor must know the callback URL (from workflow input `context.callbackBaseUrl`).
- Executor depends on Chat BE availability. If BE is down, the callback fails.
- **Current implementation:** The executor’s `reportEvent` activity performs a single HTTP POST. On failure it throws; Temporal will **retry the activity** according to activity retry policy (default or configured). There is **no application-level retry with backoff** in the executor today.

**Documented choice (Phase 1):**

- **Option A — Activity retry as callback retry:** Rely on **Temporal activity retry**. If the HTTP callback fails, the activity fails; Temporal retries the activity (with backoff if so configured). Thus “worker retries callback” is achieved via activity retry. **Recommendation:** Configure activity retry (e.g. exponential backoff, max attempts) so that transient BE unavailability is retried; document this in executor and BE so both sides expect at-least-once delivery and idempotent handling (see §5).
- **Option B (alternative):** Worker writes events into Temporal (e.g. signal or workflow return). Not implemented; would require workflow/worker to push into Temporal instead of HTTP.
- **Option C (long-term):** Executor → **message queue / event bus** → BE consumes. Loose coupling, better retry and backpressure, easier scaling. Not urgent for Phase 1 but **inevitable at scale**; document as the long-term callback direction when BE availability and coupling become a bottleneck.

**Action:** Document that **callback retry semantics are those of the activity retry policy**. BE must treat callback as **at-least-once** and implement idempotency (e.g. by sequenceNumber) to avoid duplicate events when the same callback is retried.

---

## 2. Event Ordering Guarantee (sequenceNumber enforced)

**Requirement:** Events are append-only; ordering must be deterministic for replay and diff.

**Implemented:**

- **UNIQUE(runId, sequenceNumber):** Backend enforces uniqueness. Duplicate sequence numbers are **rejected** (409 Conflict). Executor sends **sequenceNumber** (monotonically increasing per run) in each callback; BE requires it (400 if missing).
- **Executor:** Workflow maintains a counter (e.g. seq = 1, 2, 3, …; BE owns sequence 0 for the initial event). Every `reportEvent` includes `sequenceNumber`.
- **BE:** Persists **sequenceNumber**; **always serves events sorted by sequenceNumber**; rejects duplicate sequence with 409. Replay/diff correctness is guaranteed by this ordering.
- **Schema:** `OloExecutionEvent` includes **sequenceNumber** and **eventVersion** for schema evolution and ordering.
- **Event idempotency key:** **(runId, sequenceNumber)**. The unique constraint on (runId, sequenceNumber) is the idempotency key; duplicate callback delivery for the same sequence is rejected (409) and does not create a second event.

---

## 3. Human Step Semantics

**Clarification:** “Workflow emits HUMAN WAITING” means:

- The **workflow** (in olo-executor) is in a **workflow state** waiting on a **signal** (`Workflow.await(() -> humanInputReceived)`). It is **not** an activity that “emits” and then exits; the workflow thread is blocked until the signal arrives.
- An **activity** (`reportEvent`) is used to **notify** the BE (and thus the UI) that the run is in HUMAN WAITING. So: activity reports “HUMAN WAITING” to BE → BE persists and streams → UI shows “waiting for human” → user submits → BE signals workflow → workflow unblocks and continues.

**Summary:**

- **HUMAN WAITING** is a **workflow state** (waiting on signal), not a long-running activity. Temporal handles the wait; the BE only signals when the user submits.
- For replay: the event log has HUMAN WAITING and later HUMAN COMPLETED; the canonical “who was waiting” is the Temporal workflow history; the event store is a **projection** (see §6).

---

## 4. Run ID Ownership

- **Who generates runId?** The **Chat BE** (e.g. in `RunsController` or `SessionsController`) when creating a run. It is a **UUID** (e.g. `UUID.randomUUID().toString()`).
- **Uniqueness:** UUID is globally unique in practice. For multi-tenant: runId is **not** scoped by tenant in the identifier itself; tenant is stored in context (e.g. `tenantId` in session/run/event). So runIds are globally unique; tenant isolation is by data (tenantId) and access control.
- **Temporal workflow id:** `run-{runId}`. One workflow execution per run.

---

## 5. Failure Scenarios (Explicit)

| Scenario | Current / Documented behavior |
|----------|-------------------------------|
| **Executor callback fails** | Activity fails; Temporal retries activity (if retry policy configured). No application-level retry in executor beyond that. |
| **Chat BE unavailable** | Callbacks fail; activity retries until success or max attempts. Configure activity retry (backoff, max attempts). |
| **Duplicate callback delivery** | **Idempotency key (runId, sequenceNumber):** duplicate sequence is rejected (409). No duplicate events. |
| **Out-of-order events** | sequenceNumber enforced; events always served sorted by sequenceNumber. Replay/diff deterministic. |
| **Workflow retry** | Temporal may retry workflows/activities. Idempotent callback handling prevents duplicate events when the same event is reported again. |
| **Human input submitted twice** | See §7. |

---

## 6. Replay and Source of Truth

**Canonical execution history:** **Temporal** holds the canonical workflow and activity history.

**Execution event store (Chat BE):** The **execution_event** table (or in-memory store in Phase 1) is a **projection**: it is populated by **callbacks** from the executor. It is **not** the source of truth for “what actually ran”; Temporal is.

**Implications:**

- For **true replay** (re-run with same decisions), you must either (a) replay from **Temporal history**, or (b) ensure the event log is a **deterministic, ordered projection** (e.g. with sequenceNumber and idempotent append) so that replay from the event log is equivalent.
- Document that the **DB event log is a projection** for UI, audit, and diff; for strict replay determinism, Temporal history (or a log that is explicitly ordered and idempotent) is required.

---

## 7. Human Signal Race (Double Submit)

**Scenario:** User submits human input twice (e.g. double-click or two tabs).

**Options:**

- **A) Reject second:** BE returns 409 or 400 if the run is no longer in `waiting_human` (e.g. after first signal). **Recommended:** Check run status before signaling; if not `waiting_human`, return an error and do not signal again.
- **B) Signal twice:** Both signals are sent. Temporal delivers the signal; the workflow may have already continued. Second signal may be a no-op or may be queued. Can cause confusion; not recommended without idempotent handling in the workflow.
- **C) Ignore after completion:** BE treats “already completed” as success (idempotent). User sees success even on second submit; no second signal. **Alternative:** Same as A but return 200 with “already processed” if you want idempotent semantics.

**Recommendation:** **Option A** — Reject second submit if run is not in `waiting_human`. Return 409 Conflict (or 400) with a clear message. Option C is acceptable if you prefer idempotent “submit human input” (accept once, ignore subsequent).

---

## 8. Multi-Executor Scaling and Idempotency

**Scenario:** Multiple olo-executor instances poll the same task queue. The same run might be executed only once (one workflow), but **activities** can be retried, and **callbacks** can be duplicated.

**Requirements:**

- **Callback endpoint** (`POST /api/runs/{runId}/events`) should be **idempotent**. Use a key such as `(runId, nodeId, status)` or `(runId, sequenceNumber)` to deduplicate. If the same event is posted again (e.g. activity retry), overwrite or ignore instead of appending a duplicate.
- **Event insert** in the store should be **idempotent** so that duplicate activity retries do not double-append the same logical event.
- Document that **at-least-once delivery** from executor to BE is expected; BE is responsible for idempotent handling.

---

## 9. Event Schema Versioning

- **OloExecutionEvent** includes **eventVersion** (e.g. 1). Increment when the event schema or semantics change.
- Use **eventVersion** for backward compatibility: readers can branch on version when parsing or when doing replay/diff. New optional fields can be added without breaking old consumers if they ignore unknown fields.

---

## 10. Related Documents

- **[DESIGN.md](DESIGN.md)** — Event model (§7), human step (§8), persistence, API.
- **[ARCHITECTURE.md](ARCHITECTURE.md)** — High-level flow and components.
- **[SDK_AND_BACKEND.md](SDK_AND_BACKEND.md)** — How BE and executor use SDK and callback URL.
