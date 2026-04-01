/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.store;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for run metadata. Demo only.
 */
public class ChatRunStore {

    public static class RunRecord {
        public final String runId;
        public final String sessionId;
        public final String messageId;
        public volatile String status; // running | completed | failed | waiting_human
        public final long createdAt;
        /** Tenant that owns this run; used for WebSocket SUBSCRIBE_RUN tenant check. */
        public final String tenantId;
        /** Cross-service tracing; set at run creation, propagated to every event. */
        public final String correlationId;
        /** Execution versioning for diff: which workflow/model/planner produced this run. */
        public final String workflowVersion;
        public final String modelVersion;
        public final String plannerVersion;

        public RunRecord(String runId, String sessionId, String messageId) {
            this(runId, sessionId, messageId, null, null, null, null, null);
        }

        public RunRecord(String runId, String sessionId, String messageId,
                         String correlationId, String workflowVersion, String modelVersion, String plannerVersion) {
            this(runId, sessionId, messageId, null, correlationId, workflowVersion, modelVersion, plannerVersion);
        }

        public RunRecord(String runId, String sessionId, String messageId, String tenantId,
                         String correlationId, String workflowVersion, String modelVersion, String plannerVersion) {
            this.runId = runId;
            this.sessionId = sessionId;
            this.messageId = messageId;
            this.tenantId = tenantId;
            this.status = "running";
            this.createdAt = System.currentTimeMillis();
            this.correlationId = correlationId;
            this.workflowVersion = workflowVersion;
            this.modelVersion = modelVersion;
            this.plannerVersion = plannerVersion;
        }
    }

    private final Map<String, RunRecord> runs = new ConcurrentHashMap<>();

    public void put(RunRecord run) {
        runs.put(run.runId, run);
    }

    public RunRecord get(String runId) {
        return runs.get(runId);
    }

    public void setStatus(String runId, String status) {
        RunRecord r = runs.get(runId);
        if (r != null) r.status = status;
    }
}
