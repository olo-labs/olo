/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.api;

import org.olo.app.domain.OloExecutionEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Strips internal fields from execution events before exposing via API or SSE (product-level only).
 * Chat BE exposes stable product-level fields only; workflow ID, task queue, Temporal namespace,
 * worker identity, and internal planner metadata must not leak to clients.
 */
public final class ExecutionEventSanitizer {

    private static final Set<String> INTERNAL_KEYS = Set.of(
            "workflowId", "workflow_id", "taskQueue", "task_queue", "taskQueueName",
            "namespace", "temporalNamespace", "temporal_namespace",
            "workerId", "worker_id", "workerIdentity", "worker_identity",
            "temporalWorkflowId", "temporal_workflow_id"
    );

    private ExecutionEventSanitizer() {}

    /**
     * Returns a copy of the event with metadata, input, and output stripped of internal keys.
     * Use this for SSE and any REST response that returns events to clients.
     */
    public static OloExecutionEvent forApi(OloExecutionEvent event) {
        if (event == null) return null;
        OloExecutionEvent out = new OloExecutionEvent();
        out.setEventVersion(event.getEventVersion());
        out.setRunId(event.getRunId());
        out.setNodeId(event.getNodeId());
        out.setParentNodeId(event.getParentNodeId());
        out.setNodeType(event.getNodeType());
        out.setStatus(event.getStatus());
        out.setEventType(event.getEventType());
        out.setTimestamp(event.getTimestamp());
        out.setSequenceNumber(event.getSequenceNumber());
        out.setCorrelationId(event.getCorrelationId());
        out.setInput(sanitizeMap(event.getInput()));
        out.setOutput(sanitizeMap(event.getOutput()));
        out.setMetadata(sanitizeMap(event.getMetadata()));
        return out;
    }

    private static Map<String, Object> sanitizeMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return map;
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!isInternalKey(e.getKey())) out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    private static boolean isInternalKey(String key) {
        if (key == null) return true;
        if (key.startsWith("_")) return true;
        return INTERNAL_KEYS.contains(key);
    }
}
