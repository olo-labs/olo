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
import org.olo.app.security.CurrentPrincipal;
import org.olo.app.security.OloPrincipal;
import org.olo.input.model.IdentityBinding;
import org.olo.app.tenant.TenantScopedName;
import org.olo.app.service.ChatRedisPersistence;
import org.olo.app.service.RunService;
import org.olo.app.store.*;
import org.olo.app.workflow.impl.WorkflowInputSerializer;
import io.swagger.v3.oas.annotations.Operation;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// Fresh-install contract: this legacy controller is deliberately not registered. Removed in M9.
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
    private final String ragChatPipeline;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SessionsController(ChatSessionStore sessionStore,
                              ChatMessageStore messageStore,
                              ChatRunStore runStore,
                              ExecutionEventStore eventStore,
                              RunService runService,
                              @Autowired(required = false) ChatRedisPersistence redisPersistence,
                              @Qualifier("oloTaskQueue") String taskQueue,
                              @Qualifier("oloCallbackBaseUrl") String callbackBaseUrl,
                              @Value("${olo.chat.rag.pipeline:rag-chat}") String ragChatPipeline) {
        this.sessionStore = sessionStore;
        this.messageStore = messageStore;
        this.runStore = runStore;
        this.eventStore = eventStore;
        this.runService = runService;
        this.redisPersistence = redisPersistence;
        this.taskQueue = taskQueue;
        this.callbackBaseUrl = callbackBaseUrl;
        this.ragChatPipeline = ragChatPipeline == null ? "rag-chat" : ragChatPipeline.trim();
    }

    @Operation(summary = "Create session", description = "Create a new chat session for a tenant, optionally scoped by queue and pipeline.")
    @PostMapping
    public ResponseEntity<CreateSessionResponse> createSession(@Valid @RequestBody CreateSessionRequest request) {
        String tenantId = CurrentPrincipal.requireOwnTenant(request.getTenantId());
        String sessionId = UUID.randomUUID().toString();
        ChatSessionStore.SessionRecord rec = new ChatSessionStore.SessionRecord(
                sessionId, tenantId, System.currentTimeMillis(), System.currentTimeMillis(),
                request.getQueueName(), request.getPipelineId());
        sessionStore.put(rec);
        if (redisPersistence != null) {
            redisPersistence.saveSession(tenantId, sessionId, rec.createdAt, rec.lastActivityAt, request.getQueueName(), request.getPipelineId());
        }
        return ResponseEntity.ok(new CreateSessionResponse(sessionId));
    }

    @Operation(summary = "Delete session", description = "Delete one chat session and its messages. Used by Conversation per-conversation delete button.")
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        ChatSessionStore.SessionRecord session = ownedSession(sessionId);
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
        ChatSessionStore.SessionRecord session = ownedSession(sessionId);
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

        String requestedSource = request.getCapabilitySource() == null ? "" : request.getCapabilitySource().trim();
        boolean ragSelected = !requestedSource.isEmpty();
        // Retrieval can only ever search the session tenant's own sources (M3.11).
        String capabilitySource = ragSelected ? TenantScopedName.qualify(session.tenantId, requestedSource) : "";

        String pipeline = (session.pipelineId != null && !session.pipelineId.isBlank())
                ? session.pipelineId.trim()
                : (ragSelected
                        ? ragChatPipeline
                        : ((request.getTaskQueue() != null && !request.getTaskQueue().isBlank())
                                ? request.getTaskQueue().trim()
                                : taskQueue));
        String effectiveQueue = (request.getTaskQueue() != null && !request.getTaskQueue().isBlank())
                ? request.getTaskQueue().trim()
                : (session.queueName != null && !session.queueName.isBlank() ? session.queueName : taskQueue);

        String workflowMessage = request.getContent();
        if (ragSelected) {
            try {
                workflowMessage = objectMapper.writeValueAsString(Map.of(
                        "message", request.getContent(),
                        "capabilitySource", capabilitySource,
                        "ragTag", capabilitySource));
            } catch (Exception ignored) {
                workflowMessage = request.getContent();
            }
        }

        // M3.3: the run carries who it acts for, from the verified principal; never a token, never request fields.
        OloPrincipal principal = CurrentPrincipal.require();
        IdentityBinding identity = new IdentityBinding(session.tenantId, principal.userId(), principal.issuer(),
                principal.sessionId(), principal.sessionEpoch());
        org.olo.input.model.WorkflowInput workflowInput = WorkflowInputSerializer.build(
                session.tenantId,
                sessionId,
                messageId,
                workflowMessage,
                pipeline,
                runId,
                runId,
                callbackBaseUrl,
                correlationId,
                ragSelected ? capabilitySource : null,
                identity);
        runService.startWorkflow(runId, workflowInput, effectiveQueue);

        return ResponseEntity.ok(new SendMessageResponse(messageId, runId));
    }

    @Operation(summary = "List messages", description = "List all messages in the session")
    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<List<Map<String, Object>>> listMessages(@PathVariable String sessionId) {
        ChatSessionStore.SessionRecord session = ownedSession(sessionId);
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

    /**
     * The session, only if it belongs to the verified caller's tenant. Another tenant's session is reported as not found,
     * so its existence isn't revealed.
     */
    private ChatSessionStore.SessionRecord ownedSession(String sessionId) {
        ChatSessionStore.SessionRecord session = sessionStore.get(sessionId);
        if (session == null || !CurrentPrincipal.require().ownsTenant(session.tenantId)) {
            return null;
        }
        return session;
    }
}
