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
 * Persists assistant responses from MODEL/SYSTEM completion events and resolves run output text.
 */
@Component
public class RunAssistantPersistence {

    private static final Logger log = LoggerFactory.getLogger(RunAssistantPersistence.class);

    private final ChatRunStore runStore;
    private final ChatRedisPersistence redisPersistence;
    private final ChatMessageStore messageStore;
    private final ExecutionEventStore eventStore;
    /** RunIds for which we have already persisted the assistant message (one per run). */
    private final Set<String> assistantPersistedRunIds = ConcurrentHashMap.newKeySet();

    public RunAssistantPersistence(ChatRunStore runStore,
                                   @Autowired(required = false) ChatRedisPersistence redisPersistence,
                                   @Autowired(required = false) ChatMessageStore messageStore,
                                   ExecutionEventStore eventStore) {
        this.runStore = runStore;
        this.redisPersistence = redisPersistence;
        this.messageStore = messageStore;
        this.eventStore = eventStore;
    }

    /**
     * Persists the first assistant response for a run when a MODEL or SYSTEM node completes.
     */
    public void maybePersistAssistantResponse(String runId, OloExecutionEvent event) {
        if (assistantPersistedRunIds.contains(runId)) {
            return;
        }
        Map<String, Object> output = event.getOutput();
        String responseText = null;
        if (NodeType.MODEL.equals(event.getNodeType()) && NodeStatus.COMPLETED.equals(event.getStatus())) {
            responseText = extractResponseFromOutput(output);
        }
        if (NodeType.SYSTEM.equals(event.getNodeType()) && NodeStatus.COMPLETED.equals(event.getStatus())
                && responseText == null) {
            responseText = extractResponseFromOutput(output);
        }
        if (responseText == null || responseText.isBlank()) {
            return;
        }
        ChatRunStore.RunRecord run = runStore.get(runId);
        if (run == null || run.sessionId == null || run.sessionId.isBlank()) {
            return;
        }
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

    /** Current assistant response for the run from event store (last MODEL or SYSTEM COMPLETED with output). */
    public String getRunResponse(String runId) {
        List<OloExecutionEvent> events = eventStore.getEvents(runId);
        if (events == null || events.isEmpty()) {
            return null;
        }

        for (int i = events.size() - 1; i >= 0; i--) {
            OloExecutionEvent e = events.get(i);
            if (e.getOutput() == null || e.getOutput().isEmpty()) {
                continue;
            }
            if (!"WORKFLOW_RESULT".equals(e.getOutput().get("status"))) {
                continue;
            }
            String text = extractResponseFromOutput(e.getOutput());
            if (text != null) {
                return text;
            }
        }

        for (int i = events.size() - 1; i >= 0; i--) {
            OloExecutionEvent e = events.get(i);
            if (e.getOutput() == null || e.getOutput().isEmpty()) {
                continue;
            }
            if (e.getNodeType() == NodeType.MODEL && e.getStatus() == NodeStatus.COMPLETED) {
                String text = extractResponseFromOutput(e.getOutput());
                if (text != null) {
                    return text;
                }
            }
            if (e.getNodeType() == NodeType.SYSTEM && e.getStatus() == NodeStatus.COMPLETED) {
                if (isMetadataOnlyWorkflowOutput(e.getOutput())) {
                    continue;
                }
                String text = extractResponseFromOutput(e.getOutput());
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    static boolean isWorkflowFinishedEvent(OloExecutionEvent event) {
        if (event == null || event.getOutput() == null || event.getOutput().isEmpty()) {
            return false;
        }
        Map<String, Object> output = event.getOutput();
        if ("WORKFLOW_RESULT".equals(output.get("status"))) {
            return true;
        }
        if ("temporal".equals(output.get("source"))) {
            return true;
        }
        Map<String, Object> metadata = event.getMetadata();
        if (metadata != null && "kernel-result".equals(metadata.get("phase"))) {
            return true;
        }
        return extractResponseFromOutput(output) != null;
    }

    static String extractResponseFromOutput(Map<String, Object> output) {
        if (output == null) {
            return null;
        }
        for (String key : List.of("response", "content", "text", "result", "message")) {
            Object value = output.get(key);
            if (value instanceof String s) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    return normalizeResponseText(trimmed);
                }
            }
        }
        Object summary = output.get("summary");
        if (summary instanceof String s) {
            String formatted = formatConversationSummary(s);
            if (formatted != null) {
                return formatted;
            }
        }
        Object returnValue = output.get("returnValue");
        if (returnValue != null) {
            if (returnValue instanceof Map<?, ?> map) {
                Object nestedSummary = map.get("summary");
                if (nestedSummary instanceof String s) {
                    String formatted = formatConversationSummary(s);
                    if (formatted != null) {
                        return formatted;
                    }
                }
            }
            String s = normalizeResponseText(String.valueOf(returnValue).trim());
            if (!s.isEmpty() && !"null".equals(s) && !"undefined".equals(s)) {
                return s;
            }
        }
        return null;
    }

    private static String normalizeResponseText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalizedMap = normalizeJavaMapText(text.trim());
        return normalizedMap != null ? normalizedMap : text.trim();
    }

    private static String normalizeJavaMapText(String text) {
        if (text == null || !text.startsWith("{") || !text.endsWith("}") || !text.contains("=")) {
            return null;
        }
        String summary = extractJavaMapValue(text, "summary");
        if (summary != null) {
            return formatConversationSummary(summary);
        }
        String response = extractJavaMapValue(text, "response");
        if (response == null) {
            response = extractJavaMapValue(text, "message");
        }
        return response == null || response.isBlank() ? null : response.trim();
    }

    private static String extractJavaMapValue(String text, String key) {
        String marker = key + "=";
        int start = text.indexOf(marker);
        if (start < 0) {
            return null;
        }
        int valueStart = start + marker.length();
        String tail = text.substring(valueStart);
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile(",\\s*[A-Za-z][A-Za-z0-9_]*=")
                .matcher(tail);
        int end = matcher.find() ? valueStart + matcher.start() : text.length();
        String value = text.substring(valueStart, end).replaceFirst("}$", "").trim();
        return value.isBlank() ? null : value;
    }

    private static String formatConversationSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            return null;
        }
        String formatted = summary.trim()
                .replaceAll("(?i)\\s+assistant:\\s*", "\n\nAssistant: ")
                .replaceAll("(?i)^user:\\s*", "User: ")
                .replaceAll("(?i)\\s+user:\\s*", "\n\nUser: ")
                .trim();
        return formatted.isBlank() ? null : formatted;
    }

    private static boolean isMetadataOnlyWorkflowOutput(Map<String, Object> output) {
        if (output == null || output.isEmpty()) {
            return true;
        }
        for (String key : output.keySet()) {
            if (!Set.of("source", "status", "phase", "queue", "graphReady", "variables",
                    "usedAdminFallback", "returnVariable").contains(key)) {
                return false;
            }
        }
        return true;
    }
}
