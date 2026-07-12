/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.service.impl;

import org.olo.app.domain.EventType;
import org.olo.app.service.RunService;
import org.olo.app.service.impl.run.RunAssistantPersistence;
import org.olo.app.service.impl.run.RunHumanStepPersistence;
import org.olo.app.service.impl.run.RunCanceller;
import org.olo.app.service.impl.run.RunEventHandler;
import org.olo.app.service.impl.run.RunWorkflowStarter;
import org.olo.app.store.ChatRunStore;
import org.olo.app.store.ExecutionEventStore;
import org.olo.app.store.RunEventBroadcaster;
import org.olo.app.workflow.WorkflowRunner;
import org.olo.input.model.WorkflowInput;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Thin {@link RunService} facade delegating to focused run lifecycle components.
 */
@Service
public class RunServiceImpl implements RunService {

    private final RunWorkflowStarter workflowStarter;
    private final RunEventHandler eventHandler;
    private final RunAssistantPersistence assistantPersistence;
    private final RunHumanStepPersistence humanStepPersistence;
    private final RunCanceller runCanceller;
    private final WorkflowRunner workflowRunner;
    private final RunEventBroadcaster broadcaster;
    private final ExecutionEventStore eventStore;
    private final ChatRunStore runStore;

    public RunServiceImpl(RunWorkflowStarter workflowStarter,
                          RunEventHandler eventHandler,
                          RunAssistantPersistence assistantPersistence,
                          RunHumanStepPersistence humanStepPersistence,
                          RunCanceller runCanceller,
                          WorkflowRunner workflowRunner,
                          RunEventBroadcaster broadcaster,
                          ExecutionEventStore eventStore,
                          ChatRunStore runStore) {
        this.workflowStarter = workflowStarter;
        this.eventHandler = eventHandler;
        this.assistantPersistence = assistantPersistence;
        this.humanStepPersistence = humanStepPersistence;
        this.runCanceller = runCanceller;
        this.workflowRunner = workflowRunner;
        this.broadcaster = broadcaster;
        this.eventStore = eventStore;
        this.runStore = runStore;
    }

    @Override
    public void startWorkflow(String runId, WorkflowInput workflowInput, String taskQueueFromFrontend) {
        workflowStarter.startWorkflow(runId, workflowInput, taskQueueFromFrontend);
    }

    @Override
    public void signalHumanInput(String runId, boolean approved, String message, String historyText) {
        humanStepPersistence.persistOperatorReply(runId, approved, message, historyText);
        workflowRunner.signalHumanInput(runId, approved, message);
    }

    @Override
    public void cancelRun(String runId) {
        runCanceller.cancelRun(runId);
    }

    @Override
    public void appendEvent(String runId, String nodeId, String parentNodeId,
                            String nodeType, String status,
                            Map<String, Object> input, Map<String, Object> output, Map<String, Object> metadata,
                            Long sequenceNumber, Integer eventVersion, EventType eventType, String correlationId) {
        eventHandler.appendEvent(runId, nodeId, parentNodeId, nodeType, status,
                input, output, metadata, sequenceNumber, eventVersion, eventType, correlationId);
    }

    @Override
    public RunEventBroadcaster getBroadcaster() {
        return broadcaster;
    }

    @Override
    public ExecutionEventStore getEventStore() {
        return eventStore;
    }

    @Override
    public ChatRunStore getRunStore() {
        return runStore;
    }

    @Override
    public String getRunResponse(String runId) {
        return assistantPersistence.getRunResponse(runId);
    }
}
