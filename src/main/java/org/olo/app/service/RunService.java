/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.service;

import org.olo.app.domain.EventType;
import org.olo.app.store.ChatRunStore;
import org.olo.app.store.ExecutionEventStore;
import org.olo.app.store.RunEventBroadcaster;
import org.olo.input.model.WorkflowInput;

import java.util.Map;

/**
 * Service for run lifecycle: start workflow, signal human input, append and broadcast events.
 */
public interface RunService {

    void startWorkflow(String runId, WorkflowInput workflowInput, String taskQueueFromFrontend);

    void signalHumanInput(String runId, boolean approved, String message);

    void cancelRun(String runId);

    /** Idempotency key: (runId, sequenceNumber). eventType/correlationId optional; correlationId falls back to run's. */
    void appendEvent(String runId, String nodeId, String parentNodeId,
                    String nodeType, String status,
                    Map<String, Object> input, Map<String, Object> output, Map<String, Object> metadata,
                    Long sequenceNumber, Integer eventVersion, EventType eventType, String correlationId);

    RunEventBroadcaster getBroadcaster();

    ExecutionEventStore getEventStore();

    ChatRunStore getRunStore();

    /** Current assistant response for the run from event store (last MODEL or SYSTEM COMPLETED with output). */
    String getRunResponse(String runId);
}
