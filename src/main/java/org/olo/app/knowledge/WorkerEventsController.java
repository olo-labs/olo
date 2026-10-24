// Copyright 2026 OLO Labs; SPDX-License-Identifier: Apache-2.0
package org.olo.app.knowledge;

import org.olo.app.api.request.AppendEventRequest;
import org.olo.app.service.RunService;
import org.olo.app.store.ChatRunStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authenticated by the worker callback signature filter. Content is never logged or persisted as
 * backend history.
 */
@RestController
public final class WorkerEventsController {
    private final RunService runs;
    private final ChatRunStore store;

    public WorkerEventsController(RunService runs, ChatRunStore store) {
        this.runs = runs;
        this.store = store;
    }

    @PostMapping("/api/runs/{runId}/events")
    public ResponseEntity<Void> event(
            @PathVariable String runId, @RequestBody AppendEventRequest body) {
        if (body.getSequenceNumber() == null) return ResponseEntity.badRequest().build();
        var run = store.get(runId);
        // A restarted backend may have lost transient diagnostics. Acknowledge and discard; history
        // remains durable in memory.
        if (run == null) return ResponseEntity.noContent().build();
        try {
            runs.appendEvent(
                    runId,
                    body.getNodeId(),
                    body.getParentNodeId(),
                    body.getNodeType() != null ? body.getNodeType().name() : "SYSTEM",
                    body.getStatus() != null ? body.getStatus().name() : "STARTED",
                    null,
                    body.getOutput(),
                    body.getMetadata(),
                    body.getSequenceNumber(),
                    body.getEventVersion(),
                    body.getEventType(),
                    body.getCorrelationId() != null ? body.getCorrelationId() : run.correlationId);
            return ResponseEntity.noContent().build();
        } catch (org.olo.app.store.DuplicateSequenceException duplicate) {
            return ResponseEntity.status(409).build();
        }
    }
}
