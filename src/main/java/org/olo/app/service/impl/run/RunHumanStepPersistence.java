/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.service.impl.run;

import org.olo.app.domain.NodeStatus;
import org.olo.app.domain.NodeType;
import org.olo.app.domain.OloExecutionEvent;
import org.olo.app.service.ChatRedisPersistence;
import org.olo.app.store.ChatMessageStore;
import org.olo.app.store.ChatRunStore;
import org.olo.app.store.ExecutionEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists human-step prompts and human decision messages into conversation history.
 */
@Component
public class RunHumanStepPersistence {

    private static final Logger log = LoggerFactory.getLogger(RunHumanStepPersistence.class);

    private final ChatRunStore runStore;
    private final ExecutionEventStore eventStore;
    private final ChatRedisPersistence redisPersistence;
    private final ChatMessageStore messageStore;
    /** RunIds for which we have already persisted the HUMAN decision message (one per run). */
    private final Set<String> humanDecisionPersistedRunIds = ConcurrentHashMap.newKeySet();
    /** Keys runId/nodeId/sequence for which we persisted the HUMAN WAITING prompt into the conversation. */
    private final Set<String> humanStepPromptPersistedKeys = ConcurrentHashMap.newKeySet();

    public RunHumanStepPersistence(ChatRunStore runStore,
                                   ExecutionEventStore eventStore,
                                   @Autowired(required = false) ChatRedisPersistence redisPersistence,
                                   @Autowired(required = false) ChatMessageStore messageStore) {
        this.runStore = runStore;
        this.eventStore = eventStore;
        this.redisPersistence = redisPersistence;
        this.messageStore = messageStore;
    }

    /**
     * Persists the human-step question when a HUMAN node enters WAITING, and the user's decision on COMPLETED.
     */
    public void handleHumanStepEvent(String runId, OloExecutionEvent event) {
        if (NodeType.HUMAN.equals(event.getNodeType()) && NodeStatus.WAITING.equals(event.getStatus())) {
            handleHumanWaiting(runId, event);
        }
        if (NodeType.HUMAN.equals(event.getNodeType())
                && NodeStatus.COMPLETED.equals(event.getStatus())
                && !humanDecisionPersistedRunIds.contains(runId)) {
            handleHumanCompleted(runId, event);
        }
    }

    private void handleHumanWaiting(String runId, OloExecutionEvent event) {
        String promptText = RunHumanStepTextUtils.extractHumanStepPromptText(event.getInput(), event.getMetadata());
        if (promptText == null || promptText.isBlank()) {
            return;
        }
        long seq = event.getSequenceNumber() != null ? event.getSequenceNumber() : 0L;
        String node = event.getNodeId() != null ? event.getNodeId() : "";
        String dedupeKey = runId + "/" + node + "/" + seq;
        if (humanStepPromptPersistedKeys.contains(dedupeKey)) {
            return;
        }
        List<String> optionLines = RunHumanStepTextUtils.extractHumanStepOptionLines(
                event.getInput(), event.getMetadata(), event.getOutput());
        persistHumanStepPromptMessage(runId, promptText.trim(), optionLines);
        humanStepPromptPersistedKeys.add(dedupeKey);
    }

    private void handleHumanCompleted(String runId, OloExecutionEvent event) {
        String humanDecisionText = RunHumanStepTextUtils.extractHumanDecisionText(event.getOutput());
        if (humanDecisionText == null || humanDecisionText.isBlank()) {
            return;
        }
        persistUserConversationMessage(runId, humanDecisionText);
        humanDecisionPersistedRunIds.add(runId);
    }

    /** Persists the human-step question as an assistant line so it appears in GET .../messages history. */
    private void persistHumanStepPromptMessage(String runId, String promptText, List<String> optionLines) {
        if (promptText == null || promptText.isBlank()) {
            return;
        }
        String body = RunHumanStepTextUtils.formatHumanStepPromptForConversation(
                promptText, optionLines != null ? optionLines : List.of());
        RunChatContext context = resolveRunChatContext(runId);
        if (context == null || context.sessionId == null || context.sessionId.isBlank()) {
            return;
        }
        persistMessage(runId, context, UUID.randomUUID().toString(), "assistant", body);
    }

    private void persistUserConversationMessage(String runId, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        RunChatContext context = resolveRunChatContext(runId);
        if (context == null || context.sessionId == null || context.sessionId.isBlank()) {
            return;
        }
        persistMessage(runId, context, UUID.randomUUID().toString(), "user", content);
    }

    private void persistMessage(String runId, RunChatContext context, String messageId, String role, String body) {
        long createdAt = System.currentTimeMillis();
        if (redisPersistence != null) {
            try {
                redisPersistence.touchSession(context.tenantId, context.sessionId);
                redisPersistence.appendMessage(context.tenantId, context.sessionId, messageId,
                        role, body, runId, createdAt);
            } catch (Exception e) {
                log.warn("Failed to persist {} message to Redis runId={}", role, runId, e);
            }
        }
        if (messageStore != null) {
            messageStore.put(new ChatMessageStore.MessageRecord(
                    messageId, context.sessionId, role, body, runId));
        }
    }

    private RunChatContext resolveRunChatContext(String runId) {
        ChatRunStore.RunRecord run = runStore.get(runId);
        if (run != null && run.sessionId != null && !run.sessionId.isBlank()) {
            return new RunChatContext(run.tenantId, run.sessionId);
        }
        List<OloExecutionEvent> events = eventStore.getEvents(runId);
        if (events == null || events.isEmpty()) {
            return null;
        }
        for (OloExecutionEvent e : events) {
            Map<String, Object> metadata = e.getMetadata();
            if (metadata == null || metadata.isEmpty()) {
                continue;
            }
            Object sessionIdObj = metadata.get("sessionId");
            String sessionId = sessionIdObj != null ? sessionIdObj.toString() : "";
            if (sessionId.isBlank()) {
                continue;
            }
            Object tenantIdObj = metadata.get("tenantId");
            String tenantId = tenantIdObj != null ? tenantIdObj.toString() : null;
            return new RunChatContext(tenantId, sessionId);
        }
        return null;
    }

    private record RunChatContext(String tenantId, String sessionId) {}
}
