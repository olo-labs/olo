/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.worker.workflow.impl;

import org.olo.input.model.WorkflowInput;
import org.olo.worker.activities.OloChatActivities;
import org.olo.worker.workflow.OloChatWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Workflow: SYSTEM STARTED → [PLANNER → TOOL|MODEL|HUMAN] loop → SYSTEM COMPLETED.
 * HUMAN step waits for signal humanInput(approved, message).
 */
public class OloChatWorkflowImpl implements OloChatWorkflow {

    private boolean humanInputReceived;
    private boolean humanApproved;
    private String humanMessage = "";

    private final OloChatActivities activities = Workflow.newActivityStub(OloChatActivities.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(30)).build());

    @Override
    public String execute(WorkflowInput input) {
        if (input == null) return "";
        String runId = input.getContext() != null ? input.getContext().getRunId() : "";
        String callbackBaseUrl = input.getContext() != null ? input.getContext().getCallbackBaseUrl() : "";
        String correlationId = input.getContext() != null ? input.getContext().getCorrelationId() : null;
        String userMessage = extractUserQueryMessage(input);
        String sessionId = input.getContext() != null ? input.getContext().getSessionId() : "";
        String messageId = "";
        long seq = 1; // BE owns sequence 0 (root); executor uses 1, 2, 3, ...

        activities.reportEvent(runId, callbackBaseUrl, seq++, "NODE_STARTED", correlationId, "root", null, "SYSTEM", "STARTED",
                Map.of("message", userMessage, "type", "chat"),
                null, Map.of("sessionId", sessionId != null ? sessionId : "", "messageId", messageId != null ? messageId : ""));

        List<String> stepsDone = new ArrayList<>();
        String contextForModel = null;
        String lastModelResponse = "";

        while (true) {
            String next = activities.planner(userMessage, String.join(",", stepsDone));

            activities.reportEvent(runId, callbackBaseUrl, seq++, "NODE_COMPLETED", correlationId, "planner-" + stepsDone.size(), "root", "PLANNER", "COMPLETED",
                    Map.of("userMessage", userMessage != null ? userMessage : "", "stepsDone", stepsDone),
                    Map.of("nextStep", next), null);

            switch (next) {
                case "TOOL":
                    Map<String, Object> toolParams = Map.of("query", userMessage != null ? userMessage : "");
                    Map<String, Object> toolResult = activities.runTool("search_news", toolParams);
                    activities.reportEvent(runId, callbackBaseUrl, seq++, "NODE_COMPLETED", correlationId, "tool-1", "planner-" + stepsDone.size(), "TOOL", "COMPLETED",
                            toolParams, toolResult, null);
                    stepsDone.add("TOOL");
                    contextForModel = toolResult != null && toolResult.get("result") != null ? toolResult.get("result").toString() : null;
                    break;

                case "MODEL":
                    String modelResponse = activities.runModel(userMessage, contextForModel);
                    lastModelResponse = modelResponse != null ? modelResponse : "";
                    activities.reportEvent(runId, callbackBaseUrl, seq++, "NODE_COMPLETED", correlationId, "model-1", "planner-" + stepsDone.size(), "MODEL", "COMPLETED",
                            Map.of("context", contextForModel != null ? contextForModel : ""),
                            Map.of("response", lastModelResponse), null);
                    stepsDone.add("MODEL");
                    contextForModel = null;
                    break;

                case "HUMAN":
                    activities.reportEvent(runId, callbackBaseUrl, seq++, "NODE_WAITING", correlationId, "human-1", "planner-" + stepsDone.size(), "HUMAN", "WAITING",
                            Map.of("prompt", "Please approve or provide input."), null, null);
                    Workflow.await(() -> humanInputReceived);
                    activities.reportEvent(runId, callbackBaseUrl, seq++, "NODE_COMPLETED", correlationId, "human-1", "planner-" + stepsDone.size(), "HUMAN", "COMPLETED",
                            null, Map.of("approved", humanApproved, "message", humanMessage != null ? humanMessage : ""), null);
                    stepsDone.add("HUMAN");
                    humanInputReceived = false;
                    break;

                case "DONE":
                default:
                    activities.reportEvent(runId, callbackBaseUrl, seq++, "NODE_COMPLETED", correlationId, "root", null, "SYSTEM", "COMPLETED",
                            null, Map.of("steps", stepsDone, "response", lastModelResponse), null);
                    return lastModelResponse;
            }
        }
    }

    @Override
    public void humanInput(boolean approved, String message) {
        humanApproved = approved;
        humanMessage = message != null ? message : "";
        humanInputReceived = true;
    }

    private static Optional<String> getInputValue(WorkflowInput input, String name) {
        if (input == null || input.getInputs() == null) return Optional.empty();
        return input.getInputs().stream()
                .filter(i -> name.equals(i.getName()))
                .findFirst()
                .map(i -> i.getValue() != null ? i.getValue() : "");
    }

    /** Extracts user message from userQuery input (STRING type: value is the plain message). */
    private static String extractUserQueryMessage(WorkflowInput input) {
        return getInputValue(input, "userQuery").orElse("");
    }
}
