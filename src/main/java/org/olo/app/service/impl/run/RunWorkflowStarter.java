/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.service.impl.run;

import org.olo.app.domain.EventType;
import org.olo.app.workflow.WorkflowRunCompletion;
import org.olo.app.workflow.WorkflowRunner;
import org.olo.input.model.WorkflowInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.olo.app.store.ChatRunStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Starts Temporal chat workflow runs and records completion/failure as SYSTEM execution events.
 */
@Component
public class RunWorkflowStarter {

    private static final Logger log = LoggerFactory.getLogger(RunWorkflowStarter.class);

    private final WorkflowRunner workflowRunner;
    private final String callbackBaseUrl;
    private final RunEventHandler eventHandler;
    private final RunAssistantPersistence assistantPersistence;
    private final ChatRunStore runStore;

    public RunWorkflowStarter(WorkflowRunner workflowRunner,
                              @Qualifier("oloCallbackBaseUrl") String callbackBaseUrl,
                              RunEventHandler eventHandler,
                              RunAssistantPersistence assistantPersistence,
                              ChatRunStore runStore) {
        this.workflowRunner = workflowRunner;
        this.callbackBaseUrl = callbackBaseUrl;
        this.eventHandler = eventHandler;
        this.assistantPersistence = assistantPersistence;
        this.runStore = runStore;
    }

    public void startWorkflow(String runId, WorkflowInput workflowInput, String taskQueueFromFrontend) {
        log.info("Starting workflow runId={} callbackBaseUrl={}", runId, callbackBaseUrl);
        log.info("Workflow input payload (JSON): {}", workflowInput != null ? workflowInput.toJson() : "null");

        try {
            workflowRunner.startChatRun(runId, workflowInput, taskQueueFromFrontend, new WorkflowRunCompletion() {
                @Override
                public void onCompleted(String workflowResult) {
                    String correlationId = eventHandler.getCorrelationIdFromRun(runId);
                    String effectiveResult = workflowResult;
                    if (effectiveResult == null || effectiveResult.isBlank()) {
                        effectiveResult = assistantPersistence.getRunResponse(runId);
                    }
                    boolean hasResponse = effectiveResult != null && !effectiveResult.isBlank();
                    if (hasResponse) {
                        log.info("[BE SSE] Workflow completed runId={} responseLen={} preview={}", runId,
                                effectiveResult.length(),
                                effectiveResult.substring(0, Math.min(80, effectiveResult.length()))
                                        + (effectiveResult.length() > 80 ? "..." : ""));
                    } else {
                        log.info("[BE SSE] Workflow completed runId={} hasResponse=false", runId);
                    }
                    Map<String, Object> output = hasResponse
                            ? Map.of("source", "temporal", "response", effectiveResult)
                            : Map.of("source", "temporal");
                    eventHandler.appendEvent(runId, "root", null, "SYSTEM", "COMPLETED",
                            null, output, null,
                            null, null, EventType.NODE_COMPLETED, correlationId);
                }

                @Override
                public void onFailed(String errorMessage) {
                    ChatRunStore.RunRecord run = runStore.get(runId);
                    if (run != null && RunCanceller.isCancelledStatus(run.status)) {
                        log.info("[BE SSE] Workflow failure ignored for cancelled runId={}", runId);
                        return;
                    }
                    String correlationId = eventHandler.getCorrelationIdFromRun(runId);
                    log.warn("[BE SSE] Workflow failed runId={}: {}", runId, errorMessage);
                    eventHandler.appendEvent(runId, "root", null, "SYSTEM", "FAILED",
                            null, Map.of("error", errorMessage != null ? errorMessage : "Workflow failed"), null,
                            null, null, EventType.NODE_FAILED, correlationId);
                }
            });
            log.info("Workflow start requested successfully for runId={}", runId);
        } catch (Exception e) {
            log.error("Failed to start workflow for runId={}: {}", runId, e.getMessage(), e);
            throw e;
        }
    }
}
