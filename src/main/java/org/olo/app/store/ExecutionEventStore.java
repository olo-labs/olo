/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.store;

import org.olo.app.domain.OloExecutionEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * In-memory store for execution events per run.
 * <ul>
 *   <li><b>Idempotency key:</b> (runId, sequenceNumber) — unique constraint; duplicate sequence returns {@link DuplicateSequenceException}.</li>
 *   <li>Events are append-only and always returned sorted by sequenceNumber (deterministic ordering; replay/diff are done by other systems).</li>
 * </ul>
 * Demo only; replace with DB for production (index by runId, sequenceNumber; append-only).
 */
public class ExecutionEventStore {

    /** runId -> (sequenceNumber -> event). Skip-list ordering ensures getEvents returns sorted by sequenceNumber. */
    private final Map<String, ConcurrentSkipListMap<Long, OloExecutionEvent>> eventsByRunId = new ConcurrentHashMap<>();

    /**
     * Appends an event. If event.getSequenceNumber() is null, assigns the next sequence for that run.
     * @throws DuplicateSequenceException if sequenceNumber is already present for this runId
     */
    public void append(String runId, OloExecutionEvent event) {
        event.setRunId(runId);
        ConcurrentSkipListMap<Long, OloExecutionEvent> runEvents = eventsByRunId.computeIfAbsent(runId, k -> new ConcurrentSkipListMap<>());

        Long seq = event.getSequenceNumber();
        if (seq == null) {
            seq = runEvents.isEmpty() ? 0L : runEvents.lastKey() + 1;
            event.setSequenceNumber(seq);
        }
        if (runEvents.putIfAbsent(seq, event) != null) {
            throw new DuplicateSequenceException(runId, seq);
        }
    }

    /** Returns events for the run, sorted by sequenceNumber. */
    public List<OloExecutionEvent> getEvents(String runId) {
        ConcurrentSkipListMap<Long, OloExecutionEvent> runEvents = eventsByRunId.get(runId);
        return runEvents != null ? new ArrayList<>(runEvents.values()) : List.of();
    }
}
