/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.service.impl.run;

import org.olo.app.domain.EventType;
import org.olo.app.domain.NodeStatus;
import org.olo.app.domain.NodeType;
import org.olo.app.domain.OloExecutionEvent;
import org.olo.app.store.ChatRunStore;
import org.olo.app.store.ExecutionEventStore;
import org.olo.app.store.RunEventBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Appends execution events, derives run status, and triggers conversation persistence before broadcast.
 */
@Component
public class RunEventHandler {

    private static final Logger log = LoggerFactory.getLogger(RunEventHandler.class);

    private final ExecutionEventStore eventStore;
    private final RunEventBroadcaster broadcaster;
    private final ChatRunStore runStore;
    private final RunAssistantPersistence assistantPersistence;
    private final RunHumanStepPersistence humanStepPersistence;

    public RunEventHandler(ExecutionEventStore eventStore,
                           RunEventBroadcaster broadcaster,
                           ChatRunStore runStore,
                           RunAssistantPersistence assistantPersistence,
                           RunHumanStepPersistence humanStepPersistence) {
        this.eventStore = eventStore;
        this.broadcaster = broadcaster;
        this.runStore = runStore;
        this.assistantPersistence = assistantPersistence;
        this.humanStepPersistence = humanStepPersistence;
    }

    public void appendEvent(String runId, String nodeId, String parentNodeId,
                            String nodeType, String status,
                            Map<String, Object> input, Map<String, Object> output, Map<String, Object> metadata,
                            Long sequenceNumber, Integer eventVersion, EventType eventType, String correlationId) {
        OloExecutionEvent event = buildEvent(runId, nodeId, parentNodeId, nodeType, status,
                input, output, metadata, sequenceNumber, eventVersion, eventType, correlationId);

        boolean hasOutput = output != null && !output.isEmpty();
        log.info("[BE SSE] RunEventHandler.appendEvent: runId={} nodeType={} status={} hasOutput={}",
                runId, nodeType, status, hasOutput);

        eventStore.append(runId, event);
        String derivedStatus = deriveRunStatus(eventStore.getEvents(runId));
        runStore.setStatus(runId, derivedStatus);

        humanStepPersistence.handleHumanStepEvent(runId, event);
        // Persist assistant response BEFORE broadcast so client refetch sees it when event arrives.
        assistantPersistence.maybePersistAssistantResponse(runId, event);

        broadcaster.broadcast(runId, event);
    }

    String getCorrelationIdFromRun(String runId) {
        ChatRunStore.RunRecord run = runStore.get(runId);
        return run != null ? run.correlationId : null;
    }

    private OloExecutionEvent buildEvent(String runId, String nodeId, String parentNodeId,
                                         String nodeType, String status,
                                         Map<String, Object> input, Map<String, Object> output,
                                         Map<String, Object> metadata,
                                         Long sequenceNumber, Integer eventVersion,
                                         EventType eventType, String correlationId) {
        OloExecutionEvent event = new OloExecutionEvent();
        event.setRunId(runId);
        event.setNodeId(nodeId);
        event.setParentNodeId(parentNodeId);
        event.setNodeType(NodeType.valueOf(nodeType));
        event.setStatus(NodeStatus.valueOf(status));
        event.setEventType(eventType != null ? eventType : eventTypeFromStatus(NodeStatus.valueOf(status)));
        event.setTimestamp(System.currentTimeMillis());
        if (sequenceNumber != null) {
            event.setSequenceNumber(sequenceNumber);
        }
        if (eventVersion != null) {
            event.setEventVersion(eventVersion);
        }
        String effectiveCorrelationId = correlationId != null ? correlationId : getCorrelationIdFromRun(runId);
        event.setCorrelationId(effectiveCorrelationId);
        event.setInput(input);
        event.setOutput(output);
        event.setMetadata(metadata);
        return event;
    }

    private static EventType eventTypeFromStatus(NodeStatus status) {
        if (status == null) {
            return EventType.NODE_STARTED;
        }
        return switch (status) {
            case STARTED -> EventType.NODE_STARTED;
            case COMPLETED -> EventType.NODE_COMPLETED;
            case FAILED -> EventType.NODE_FAILED;
            case WAITING -> EventType.NODE_WAITING;
        };
    }

    /** Run is completed only after workflow result or Temporal completion — not CONTEXT_READY alone. */
    static String deriveRunStatus(List<OloExecutionEvent> events) {
        if (events == null || events.isEmpty()) {
            return "running";
        }
        for (OloExecutionEvent e : events) {
            if (e.getStatus() == NodeStatus.FAILED) {
                if (isCancelledEvent(e)) {
                    return "cancelled";
                }
                return "failed";
            }
        }
        for (int i = events.size() - 1; i >= 0; i--) {
            OloExecutionEvent e = events.get(i);
            if (e.getNodeType() == NodeType.HUMAN && e.getStatus() == NodeStatus.WAITING) {
                return "waiting_human";
            }
            if (e.getNodeType() == NodeType.SYSTEM && e.getStatus() == NodeStatus.COMPLETED) {
                if (RunAssistantPersistence.isWorkflowFinishedEvent(e)) {
                    return "completed";
                }
            }
        }
        return "running";
    }

    private static boolean isCancelledEvent(OloExecutionEvent event) {
        if (event.getNodeType() != NodeType.SYSTEM || event.getStatus() != NodeStatus.FAILED) {
            return false;
        }
        Map<String, Object> output = event.getOutput();
        return output != null && "CANCELLED".equalsIgnoreCase(String.valueOf(output.get("status")));
    }
}
