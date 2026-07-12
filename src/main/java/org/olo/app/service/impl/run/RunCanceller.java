/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.service.impl.run;

import org.olo.app.domain.EventType;
import org.olo.app.store.ChatRunStore;
import org.olo.app.workflow.WorkflowRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Cancels in-flight chat workflow runs via Temporal and records a terminal cancellation event.
 */
@Component
public class RunCanceller {

    private static final Logger log = LoggerFactory.getLogger(RunCanceller.class);
    private static final Set<String> CANCELLABLE_STATUSES = Set.of("running", "waiting_human");

    private final WorkflowRunner workflowRunner;
    private final RunEventHandler eventHandler;
    private final ChatRunStore runStore;

    public RunCanceller(WorkflowRunner workflowRunner, RunEventHandler eventHandler, ChatRunStore runStore) {
        this.workflowRunner = workflowRunner;
        this.eventHandler = eventHandler;
        this.runStore = runStore;
    }

    public void cancelRun(String runId) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId is required");
        }
        ChatRunStore.RunRecord run = runStore.get(runId.trim());
        if (run == null) {
            throw new RunNotFoundException(runId);
        }
        String status = run.status != null ? run.status.trim() : "running";
        if ("cancelled".equals(status)) {
            return;
        }
        if (!CANCELLABLE_STATUSES.contains(status)) {
            throw new RunNotCancellableException(runId, status);
        }

        log.info("Cancelling chat workflow runId={} status={}", runId, status);
        workflowRunner.cancelChatRun(runId);

        String correlationId = eventHandler.getCorrelationIdFromRun(runId);
        eventHandler.appendEvent(
                runId,
                "root",
                null,
                "SYSTEM",
                "FAILED",
                null,
                Map.of("status", "CANCELLED", "message", "Run cancelled by user"),
                null,
                null,
                null,
                EventType.NODE_FAILED,
                correlationId);
        runStore.setStatus(runId, "cancelled");
    }

    public static boolean isCancelledStatus(String status) {
        return status != null && "cancelled".equalsIgnoreCase(status.trim());
    }

    public static final class RunNotFoundException extends RuntimeException {
        public RunNotFoundException(String runId) {
            super("Run not found: " + runId);
        }
    }

    public static final class RunNotCancellableException extends RuntimeException {
        public RunNotCancellableException(String runId, String status) {
            super("Run " + runId + " is not cancellable (status=" + status + ")");
        }
    }
}
