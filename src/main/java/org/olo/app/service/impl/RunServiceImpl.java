/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.service.impl;

import org.olo.app.domain.EventType;
import org.olo.app.domain.NodeStatus;
import org.olo.app.domain.NodeType;
import org.olo.app.domain.OloExecutionEvent;
import org.olo.app.service.ChatRedisPersistence;
import org.olo.app.service.RunService;
import org.olo.app.store.*;
import org.olo.input.model.WorkflowInput;
import org.olo.sdk.TemporalClient;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.client.WorkflowFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class RunServiceImpl implements RunService {

    private static final Logger log = LoggerFactory.getLogger(RunServiceImpl.class);

    private final ExecutionEventStore eventStore;
    private final RunEventBroadcaster broadcaster;
    private final ChatRunStore runStore;
    private final TemporalClient temporalClient;
    private final WorkflowClient workflowClient;
    private final String callbackBaseUrl;
    private final String taskQueue;
    private final Executor workflowCompletionExecutor;
    private final ChatRedisPersistence redisPersistence;
    private final ChatMessageStore messageStore;
    /** RunIds for which we have already persisted the assistant message (Redis and/or in-memory) (one per run). */
    private final Set<String> assistantPersistedRunIds = ConcurrentHashMap.newKeySet();
    /** RunIds for which we have already persisted the HUMAN decision message (one per run). */
    private final Set<String> humanDecisionPersistedRunIds = ConcurrentHashMap.newKeySet();
    /** Keys runId/nodeId/sequence for which we persisted the HUMAN WAITING prompt into the conversation. */
    private final Set<String> humanStepPromptPersistedKeys = ConcurrentHashMap.newKeySet();

    public RunServiceImpl(ExecutionEventStore eventStore,
                           RunEventBroadcaster broadcaster,
                           ChatRunStore runStore,
                           TemporalClient temporalClient,
                           @Qualifier("oloCallbackBaseUrl") String callbackBaseUrl,
                           @Qualifier("oloTaskQueue") String taskQueue,
                           @Qualifier("workflowCompletionExecutor") Executor workflowCompletionExecutor,
                           @Autowired(required = false) ChatRedisPersistence redisPersistence,
                           @Autowired(required = false) ChatMessageStore messageStore) {
        this.eventStore = eventStore;
        this.broadcaster = broadcaster;
        this.runStore = runStore;
        this.temporalClient = temporalClient;
        this.workflowClient = temporalClient.getWorkflowClient();
        this.callbackBaseUrl = callbackBaseUrl;
        this.taskQueue = taskQueue;
        this.workflowCompletionExecutor = workflowCompletionExecutor;
        this.redisPersistence = redisPersistence;
        this.messageStore = messageStore;
    }

    @Override
    public void startWorkflow(String runId, WorkflowInput workflowInput, String taskQueueFromFrontend) {
        String effectiveTaskQueue = (taskQueueFromFrontend != null && !taskQueueFromFrontend.isBlank())
                ? taskQueueFromFrontend.trim()
                : taskQueue;
        log.info("Starting workflow runId={} taskQueue={} callbackBaseUrl={}", runId, effectiveTaskQueue, callbackBaseUrl);
        log.info("Workflow input payload (JSON): {}", workflowInput != null ? workflowInput.toJson() : "null");

        try {
            WorkflowOptions options = WorkflowOptions.newBuilder()
                    .setWorkflowId("run-" + runId)
                    .setTaskQueue(effectiveTaskQueue)
                    .build();
            WorkflowStub stub = temporalClient.newChatWorkflowStub(options);
            stub.start(workflowInput);
            log.info("Workflow start requested successfully for runId={}", runId);

            // Two sources of events to UI (both forwarded via SSE):
            // 1. Worker: POST /api/runs/{runId}/events — human approval, MODEL output, PLANNER/TOOL steps (appendEvent + broadcast).
            // 2. Temporal: await getResult() then append SYSTEM COMPLETED/FAILED — final run status so UI can un-gray.
            workflowCompletionExecutor.execute(() -> {
                try {
                    String workflowResult = stub.getResult(String.class);
                    String correlationId = getCorrelationIdFromRun(runId);
                    boolean hasResponse = workflowResult != null && !workflowResult.isBlank();
                    if (hasResponse) {
                        log.info("[BE SSE] Temporal workflow completed runId={} responseLen={} preview={}", runId,
                                workflowResult.length(), workflowResult.substring(0, Math.min(80, workflowResult.length())) + (workflowResult.length() > 80 ? "..." : ""));
                    } else {
                        log.info("[BE SSE] Temporal workflow completed runId={} hasResponse=false (workflow returned null/empty — ensure worker is OloChatWorkflowImpl and returns String)", runId);
                    }
                    Map<String, Object> output = hasResponse
                            ? Map.of("source", "temporal", "response", workflowResult)
                            : Map.of("source", "temporal");
                    appendEvent(runId, "root", null, "SYSTEM", "COMPLETED",
                            null, output, null,
                            null, null, EventType.NODE_COMPLETED, correlationId);
                } catch (WorkflowFailedException e) {
                    String correlationId = getCorrelationIdFromRun(runId);
                    log.warn("[BE SSE] Temporal workflow failed runId={} — appending SYSTEM FAILED: {}", runId, e.getMessage());
                    appendEvent(runId, "root", null, "SYSTEM", "FAILED",
                            null, Map.of("error", e.getCause() != null ? e.getCause().getMessage() : e.getMessage()), null,
                            null, null, EventType.NODE_FAILED, correlationId);
                } catch (Exception e) {
                    String correlationId = getCorrelationIdFromRun(runId);
                    log.error("[BE SSE] Error awaiting workflow result runId={}: {}", runId, e.getMessage(), e);
                    appendEvent(runId, "root", null, "SYSTEM", "FAILED",
                            null, Map.of("error", e.getMessage()), null,
                            null, null, EventType.NODE_FAILED, correlationId);
                }
            });
        } catch (Exception e) {
            log.error("Failed to start workflow for runId={}: {}", runId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void signalHumanInput(String runId, boolean approved, String message) {
        WorkflowStub stub = workflowClient.newUntypedWorkflowStub("run-" + runId);
        stub.signal("humanInput", approved, message != null ? message : "");
    }

    @Override
    public void appendEvent(String runId, String nodeId, String parentNodeId,
                            String nodeType, String status,
                            Map<String, Object> input, Map<String, Object> output, Map<String, Object> metadata,
                            Long sequenceNumber, Integer eventVersion, EventType eventType, String correlationId) {
        OloExecutionEvent event = new OloExecutionEvent();
        event.setRunId(runId);
        event.setNodeId(nodeId);
        event.setParentNodeId(parentNodeId);
        event.setNodeType(NodeType.valueOf(nodeType));
        event.setStatus(NodeStatus.valueOf(status));
        event.setEventType(eventType != null ? eventType : eventTypeFromStatus(NodeStatus.valueOf(status)));
        event.setTimestamp(System.currentTimeMillis());
        if (sequenceNumber != null) event.setSequenceNumber(sequenceNumber);
        if (eventVersion != null) event.setEventVersion(eventVersion);
        String effectiveCorrelationId = correlationId != null ? correlationId : getCorrelationIdFromRun(runId);
        event.setCorrelationId(effectiveCorrelationId);
        event.setInput(input);
        event.setOutput(output);
        event.setMetadata(metadata);
        boolean hasOutput = output != null && !output.isEmpty();
        log.info("[BE SSE] RunServiceImpl.appendEvent: runId={} nodeType={} status={} hasOutput={}", runId, nodeType, status, hasOutput);
        eventStore.append(runId, event);
        String derivedStatus = deriveRunStatus(eventStore.getEvents(runId));
        runStore.setStatus(runId, derivedStatus);

        // Persist human-step prompt when worker reports HUMAN WAITING (same text as the human card in the UI).
        if (NodeType.HUMAN.equals(event.getNodeType()) && NodeStatus.WAITING.equals(event.getStatus())) {
            String promptText = extractHumanStepPromptText(event.getInput(), event.getMetadata());
            if (promptText != null && !promptText.isBlank()) {
                long seq = event.getSequenceNumber() != null ? event.getSequenceNumber() : 0L;
                String node = event.getNodeId() != null ? event.getNodeId() : "";
                String dedupeKey = runId + "/" + node + "/" + seq;
                if (!humanStepPromptPersistedKeys.contains(dedupeKey)) {
                    List<String> optionLines = extractHumanStepOptionLines(
                            event.getInput(), event.getMetadata(), event.getOutput());
                    persistHumanStepPromptMessage(runId, promptText.trim(), optionLines);
                    humanStepPromptPersistedKeys.add(dedupeKey);
                }
            }
        }

        // Persist human decision to conversation history when worker confirms HUMAN COMPLETED.
        if (NodeType.HUMAN.equals(event.getNodeType())
                && NodeStatus.COMPLETED.equals(event.getStatus())
                && !humanDecisionPersistedRunIds.contains(runId)) {
            String humanDecisionText = extractHumanDecisionText(output);
            if (humanDecisionText != null && !humanDecisionText.isBlank()) {
                persistUserConversationMessage(runId, humanDecisionText);
                humanDecisionPersistedRunIds.add(runId);
            }
        }

        // Persist assistant response BEFORE broadcast so client refetch sees it when event arrives
        if (!assistantPersistedRunIds.contains(runId)) {
            String responseText = null;
            if (NodeType.MODEL.equals(event.getNodeType()) && NodeStatus.COMPLETED.equals(event.getStatus()))
                responseText = extractResponseFromOutput(output);
            if (NodeType.SYSTEM.equals(event.getNodeType()) && NodeStatus.COMPLETED.equals(event.getStatus()) && responseText == null)
                responseText = extractResponseFromOutput(output);
            if (responseText != null && !responseText.isBlank()) {
                ChatRunStore.RunRecord run = runStore.get(runId);
                if (run != null && run.sessionId != null && !run.sessionId.isBlank()) {
                    String assistantMessageId = UUID.randomUUID().toString();
                    long createdAt = System.currentTimeMillis();
                    if (redisPersistence != null && run.tenantId != null) {
                        try {
                            redisPersistence.touchSession(run.tenantId, run.sessionId);
                            redisPersistence.appendMessage(run.tenantId, run.sessionId, assistantMessageId,
                                    "assistant", responseText, runId, createdAt);
                            log.debug("Persisted assistant response to Redis for runId={} sessionId={}", runId, run.sessionId);
                        } catch (Exception e) {
                            log.warn("Failed to persist assistant response to Redis runId={}", runId, e);
                        }
                    }
                    if (messageStore != null) {
                        messageStore.put(new ChatMessageStore.MessageRecord(
                                assistantMessageId, run.sessionId, "assistant", responseText, runId));
                        log.debug("Added assistant message to in-memory store for runId={} sessionId={}", runId, run.sessionId);
                    }
                    assistantPersistedRunIds.add(runId);
                }
            }
        }

        broadcaster.broadcast(runId, event);
    }

    private static EventType eventTypeFromStatus(NodeStatus status) {
        if (status == null) return EventType.NODE_STARTED;
        return switch (status) {
            case STARTED -> EventType.NODE_STARTED;
            case COMPLETED -> EventType.NODE_COMPLETED;
            case FAILED -> EventType.NODE_FAILED;
            case WAITING -> EventType.NODE_WAITING;
        };
    }

    private String getCorrelationIdFromRun(String runId) {
        ChatRunStore.RunRecord run = runStore.get(runId);
        return run != null ? run.correlationId : null;
    }

    /** Minimal run status derivation from event stream. Nothing more. */
    private static String deriveRunStatus(List<OloExecutionEvent> events) {
        if (events == null || events.isEmpty()) return "running";
        for (OloExecutionEvent e : events) {
            if (e.getStatus() == NodeStatus.FAILED) return "failed";
        }
        OloExecutionEvent last = events.get(events.size() - 1);
        if (last.getNodeType() == NodeType.SYSTEM && last.getStatus() == NodeStatus.COMPLETED) return "completed";
        if (last.getNodeType() == NodeType.HUMAN && last.getStatus() == NodeStatus.WAITING) return "waiting_human";
        return "running";
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
        List<OloExecutionEvent> events = eventStore.getEvents(runId);
        if (events == null || events.isEmpty()) return null;
        for (int i = events.size() - 1; i >= 0; i--) {
            OloExecutionEvent e = events.get(i);
            if (e.getOutput() == null || e.getOutput().isEmpty()) continue;
            if (e.getNodeType() == NodeType.MODEL && e.getStatus() == NodeStatus.COMPLETED) {
                String text = extractResponseFromOutput(e.getOutput());
                if (text != null) return text;
            }
            if (e.getNodeType() == NodeType.SYSTEM && e.getStatus() == NodeStatus.COMPLETED) {
                String text = extractResponseFromOutput(e.getOutput());
                if (text != null) return text;
            }
        }
        return null;
    }

    private static String extractResponseFromOutput(Map<String, Object> output) {
        if (output == null) return null;
        Object r = output.get("response");
        if (r instanceof String) {
            String s = ((String) r).trim();
            if (!s.isEmpty()) return s;
        }
        Object c = output.get("content");
        if (c instanceof String) {
            String s = ((String) c).trim();
            if (!s.isEmpty()) return s;
        }
        Object res = output.get("result");
        if (res instanceof String) {
            String s = ((String) res).trim();
            if (!s.isEmpty()) return s;
        }
        return null;
    }

    /** Text shown when a HUMAN node is waiting: {@code input}/{@code metadata} keys message, prompt, text, question. */
    private static String extractHumanStepPromptText(Map<String, Object> input, Map<String, Object> metadata) {
        String s = firstNonBlankString(input, "message", "prompt", "text", "question");
        if (s != null) return s;
        return firstNonBlankString(metadata, "message", "prompt", "text", "question");
    }

    private static String firstNonBlankString(Map<String, Object> map, String... keys) {
        if (map == null || map.isEmpty()) return null;
        for (String k : keys) {
            Object v = map.get(k);
            if (v instanceof String str) {
                String t = str.trim();
                if (!t.isEmpty()) return t;
            }
        }
        return null;
    }

    /**
     * Worker sends {@code options} on {@code input} (preferred), or {@code metadata} / {@code output}:
     * list of strings, or list of maps with {@code label} or {@code text}.
     */
    private static List<String> extractHumanStepOptionLines(
            Map<String, Object> input, Map<String, Object> metadata, Map<String, Object> output) {
        List<?> raw = firstOptionsList(input, metadata, output);
        return normalizeOptionsToLines(raw);
    }

    private static List<?> firstOptionsList(
            Map<String, Object> input, Map<String, Object> metadata, Map<String, Object> output) {
        Object o = getOptionsRaw(input);
        if (o == null) o = getOptionsRaw(metadata);
        if (o == null) o = getOptionsRaw(output);
        return o instanceof List<?> list ? list : null;
    }

    private static Object getOptionsRaw(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;
        return map.get("options");
    }

    private static List<String> normalizeOptionsToLines(List<?> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        List<String> lines = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof String s && !s.isBlank()) {
                lines.add(s.trim());
            } else if (item instanceof Map<?, ?> m) {
                Object label = m.get("label");
                if (label == null) label = m.get("text");
                if (label != null) {
                    String t = label.toString().trim();
                    if (!t.isEmpty()) lines.add(t);
                }
            }
        }
        return List.copyOf(lines);
    }

    private static String extractHumanDecisionText(Map<String, Object> output) {
        if (output == null || output.isEmpty()) return null;
        Object message = output.get("message");
        if (message instanceof String msg && !msg.trim().isEmpty()) return msg.trim();
        Object response = output.get("response");
        if (response instanceof String r && !r.trim().isEmpty()) return r.trim();
        // Submitted label/text is in message/response from the worker signal; do not synthesize from approved alone.
        return null;
    }

    /**
     * Conversation text: {@code User Input Step: …} on the first line, then one line per worker option
     * (no {@code <Options>} marker or blank line between).
     */
    static String formatHumanStepPromptForConversation(String promptText, List<String> optionLines) {
        String q = promptText != null ? promptText.trim() : "";
        StringBuilder sb = new StringBuilder();
        if (q.isEmpty()) {
            sb.append("User Input Step:");
        } else {
            sb.append("User Input Step: ").append(q);
        }
        if (optionLines != null) {
            for (String line : optionLines) {
                if (line != null && !line.isBlank()) {
                    sb.append("\n").append(line.trim());
                }
            }
        }
        return sb.toString();
    }

    /** Persists the human-step question as an assistant line so it appears in GET .../messages history. */
    private void persistHumanStepPromptMessage(String runId, String promptText, List<String> optionLines) {
        if (promptText == null || promptText.isBlank()) return;
        String body = formatHumanStepPromptForConversation(promptText, optionLines != null ? optionLines : List.of());
        RunChatContext context = resolveRunChatContext(runId);
        if (context == null || context.sessionId == null || context.sessionId.isBlank()) return;
        String messageId = UUID.randomUUID().toString();
        long createdAt = System.currentTimeMillis();
        if (redisPersistence != null && context.tenantId != null) {
            try {
                redisPersistence.touchSession(context.tenantId, context.sessionId);
                redisPersistence.appendMessage(context.tenantId, context.sessionId, messageId,
                        "assistant", body, runId, createdAt);
            } catch (Exception e) {
                log.warn("Failed to persist human-step prompt to Redis runId={}", runId, e);
            }
        }
        if (messageStore != null) {
            messageStore.put(new ChatMessageStore.MessageRecord(
                    messageId, context.sessionId, "assistant", body, runId));
        }
    }

    private void persistUserConversationMessage(String runId, String content) {
        if (content == null || content.isBlank()) return;
        RunChatContext context = resolveRunChatContext(runId);
        if (context == null || context.sessionId == null || context.sessionId.isBlank()) return;
        String userMessageId = UUID.randomUUID().toString();
        long createdAt = System.currentTimeMillis();
        if (redisPersistence != null) {
            try {
                redisPersistence.touchSession(context.tenantId, context.sessionId);
                redisPersistence.appendMessage(context.tenantId, context.sessionId, userMessageId,
                        "user", content, runId, createdAt);
            } catch (Exception e) {
                log.warn("Failed to persist human decision to Redis runId={}", runId, e);
            }
        }
        if (messageStore != null) {
            messageStore.put(new ChatMessageStore.MessageRecord(
                    userMessageId, context.sessionId, "user", content, runId));
        }
    }

    private RunChatContext resolveRunChatContext(String runId) {
        ChatRunStore.RunRecord run = runStore.get(runId);
        if (run != null && run.sessionId != null && !run.sessionId.isBlank()) {
            return new RunChatContext(run.tenantId, run.sessionId);
        }
        List<OloExecutionEvent> events = eventStore.getEvents(runId);
        if (events == null || events.isEmpty()) return null;
        for (OloExecutionEvent e : events) {
            Map<String, Object> metadata = e.getMetadata();
            if (metadata == null || metadata.isEmpty()) continue;
            Object sessionIdObj = metadata.get("sessionId");
            String sessionId = sessionIdObj != null ? sessionIdObj.toString() : "";
            if (sessionId.isBlank()) continue;
            Object tenantIdObj = metadata.get("tenantId");
            String tenantId = tenantIdObj != null ? tenantIdObj.toString() : null;
            return new RunChatContext(tenantId, sessionId);
        }
        return null;
    }

    private record RunChatContext(String tenantId, String sessionId) {}
}
