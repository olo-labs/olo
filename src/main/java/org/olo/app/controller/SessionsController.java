/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.controller;

import org.olo.app.api.request.CreateSessionRequest;
import org.olo.app.api.request.SendMessageRequest;
import org.olo.app.api.response.CreateSessionResponse;
import org.olo.app.api.response.SendMessageResponse;
import org.olo.app.domain.EventType;
import org.olo.app.domain.NodeStatus;
import org.olo.app.domain.NodeType;
import org.olo.app.domain.OloExecutionEvent;
import org.olo.app.service.ChatRedisPersistence;
import org.olo.app.service.RunService;
import org.olo.app.store.*;
import org.olo.app.workflow.impl.WorkflowInputSerializer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sessions")
@Tag(name = "Sessions", description = "Chat sessions and messages")
public class SessionsController {

    private final ChatSessionStore sessionStore;
    private final ChatMessageStore messageStore;
    private final ChatRunStore runStore;
    private final ExecutionEventStore eventStore;
    private final RunService runService;
    private final ChatRedisPersistence redisPersistence;
    private final String taskQueue;
    private final String callbackBaseUrl;

    public SessionsController(ChatSessionStore sessionStore,
                              ChatMessageStore messageStore,
                              ChatRunStore runStore,
                              ExecutionEventStore eventStore,
                              RunService runService,
                              @Autowired(required = false) ChatRedisPersistence redisPersistence,
                              @Qualifier("oloTaskQueue") String taskQueue,
                              @Qualifier("oloCallbackBaseUrl") String callbackBaseUrl) {
        this.sessionStore = sessionStore;
        this.messageStore = messageStore;
        this.runStore = runStore;
        this.eventStore = eventStore;
        this.runService = runService;
        this.redisPersistence = redisPersistence;
        this.taskQueue = taskQueue;
        this.callbackBaseUrl = callbackBaseUrl;
    }

    @Operation(summary = "Create session", description = "Create a new chat session for a tenant, optionally scoped by queue and pipeline.")
    @PostMapping
    public ResponseEntity<CreateSessionResponse> createSession(@Valid @RequestBody CreateSessionRequest request) {
        String sessionId = UUID.randomUUID().toString();
        ChatSessionStore.SessionRecord rec = new ChatSessionStore.SessionRecord(
                sessionId, request.getTenantId(), System.currentTimeMillis(), System.currentTimeMillis(),
                request.getQueueName(), request.getPipelineId());
        sessionStore.put(rec);
        if (redisPersistence != null) {
            redisPersistence.saveSession(request.getTenantId(), sessionId, rec.createdAt, rec.lastActivityAt, request.getQueueName(), request.getPipelineId());
        }
        return ResponseEntity.ok(new CreateSessionResponse(sessionId));
    }

    @Operation(summary = "Get session", description = "Get session by ID")
    @GetMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable String sessionId) {
        ChatSessionStore.SessionRecord session = sessionStore.get(sessionId);
        if (session == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of(
                "sessionId", session.sessionId,
                "tenantId", session.tenantId,
                "createdAt", session.createdAt
        ));
    }

    @Operation(summary = "Delete session", description = "Delete one chat session and its messages. Used by Conversation per-conversation delete button.")
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        ChatSessionStore.SessionRecord session = sessionStore.get(sessionId);
        if (session == null) return ResponseEntity.notFound().build();
        messageStore.removeBySessionIds(List.of(sessionId));
        sessionStore.delete(sessionId);
        if (redisPersistence != null) {
            redisPersistence.deleteSession(session.tenantId, sessionId);
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Send message", description = "Send user message: creates ChatMessage and ChatRun, starts workflow, returns runId for SSE event stream")
    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<SendMessageResponse> sendMessage(@PathVariable String sessionId,
                                                          @Valid @RequestBody SendMessageRequest request) {
        ChatSessionStore.SessionRecord session = sessionStore.get(sessionId);
        if (session == null) return ResponseEntity.notFound().build();

        String messageId = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        ChatMessageStore.MessageRecord msgRec = new ChatMessageStore.MessageRecord(
                messageId, sessionId, "user", request.getContent(), runId);
        messageStore.put(msgRec);
        sessionStore.touch(sessionId);
        if (redisPersistence != null) {
            redisPersistence.touchSession(session.tenantId, sessionId);
            redisPersistence.appendMessage(session.tenantId, sessionId, messageId, "user", request.getContent(), runId, msgRec.createdAt);
        }
        runStore.put(new ChatRunStore.RunRecord(runId, sessionId, messageId, session.tenantId, correlationId, null, null, null));

        runService.appendEvent(runId, "root", null, "SYSTEM", "STARTED",
                Map.of("message", request.getContent(), "type", "chat"), null,
                Map.of("tenantId", session.tenantId, "sessionId", sessionId, "messageId", messageId),
                null, null, EventType.NODE_STARTED, correlationId);

        String pipeline = (request.getTaskQueue() != null && !request.getTaskQueue().isBlank())
                ? request.getTaskQueue().trim()
                : taskQueue;
        org.olo.input.model.WorkflowInput workflowInput = WorkflowInputSerializer.build(
                session.tenantId, sessionId, messageId, request.getContent(), pipeline, runId, runId, callbackBaseUrl, correlationId);
        runService.startWorkflow(runId, workflowInput, request.getTaskQueue());

        return ResponseEntity.ok(new SendMessageResponse(messageId, runId));
    }

    @Operation(summary = "List messages", description = "List all messages in the session")
    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<List<Map<String, Object>>> listMessages(@PathVariable String sessionId) {
        ChatSessionStore.SessionRecord session = sessionStore.get(sessionId);
        if (session == null) return ResponseEntity.notFound().build();
        if (redisPersistence != null) {
            List<Map<String, Object>> fromRedis = redisPersistence.listMessages(session.tenantId, sessionId);
            if (fromRedis != null) {
                List<Map<String, Object>> out = new ArrayList<>();
                for (Map<String, Object> m : fromRedis) {
                    out.add(Map.of(
                            "messageId", m.getOrDefault("messageId", ""),
                            "sessionId", m.getOrDefault("sessionId", sessionId),
                            "role", m.getOrDefault("role", "user"),
                            "content", m.getOrDefault("content", ""),
                            "runId", m.get("runId") != null ? m.get("runId").toString() : "",
                            "createdAt", ((Number) m.getOrDefault("createdAt", 0L)).longValue()
                    ));
                }
                return ResponseEntity.ok(out);
            }
        }
        List<ChatMessageStore.MessageRecord> list = messageStore.listBySession(sessionId);
        return ResponseEntity.ok(list.stream()
                .map(m -> Map.<String, Object>of(
                        "messageId", m.messageId,
                        "sessionId", m.sessionId,
                        "role", m.role,
                        "content", m.content,
                        "runId", m.runId != null ? m.runId : "",
                        "createdAt", m.createdAt
                ))
                .collect(Collectors.toList()));
    }
}
