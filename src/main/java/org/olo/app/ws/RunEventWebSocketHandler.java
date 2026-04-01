/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.olo.app.api.ExecutionEventSanitizer;
import org.olo.app.domain.OloExecutionEvent;
import org.olo.app.store.ChatRunStore;
import org.olo.app.store.ExecutionEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;

/**
 * Handles WebSocket at /ws. Client sends { "type": "SUBSCRIBE_RUN", "runId": "abc-123" }
 * to subscribe; backend registers session → runId and sends catch-up then live events.
 * Unknown or invalid messages get an ERROR frame (never silently ignored).
 */
public class RunEventWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RunEventWebSocketHandler.class);
    private static final String SUBSCRIBE_RUN = "SUBSCRIBE_RUN";
    private static final String PING = "PING";
    private static final String ERROR_TYPE = "ERROR";
    private static final String RUN_EVENT_TYPE = "RUN_EVENT";
    private static final String PONG_TYPE = "PONG";
    private static final String CODE_INVALID_MESSAGE = "INVALID_MESSAGE";
    private static final String CODE_MISSING_RUN_ID = "MISSING_RUN_ID";
    private static final String CODE_FORBIDDEN = "FORBIDDEN";
    private static final String CODE_NOT_FOUND = "NOT_FOUND";

    private final ExecutionEventStore eventStore;
    private final RunEventWebSocketRegistry registry;
    private final ChatRunStore runStore;
    private final ObjectMapper objectMapper;

    public RunEventWebSocketHandler(ExecutionEventStore eventStore,
                                    RunEventWebSocketRegistry registry,
                                    ChatRunStore runStore,
                                    ObjectMapper objectMapper) {
        this.eventStore = eventStore;
        this.registry = registry;
        this.runStore = runStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.debug("WebSocket connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if (payload == null || payload.isBlank()) {
            sendError(session, CODE_INVALID_MESSAGE, "Empty message");
            return;
        }
        Map<String, Object> map;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(payload, Map.class);
            map = parsed;
        } catch (Exception e) {
            log.warn("WebSocket message parse error: {}", e.getMessage());
            sendError(session, CODE_INVALID_MESSAGE, "Invalid JSON: " + e.getMessage());
            return;
        }
        if (map == null) {
            sendError(session, CODE_INVALID_MESSAGE, "Message must be a JSON object");
            return;
        }
        String type = map.get("type") != null ? map.get("type").toString() : null;
        if (SUBSCRIBE_RUN.equals(type)) {
            String runId = map.get("runId") != null ? map.get("runId").toString() : null;
            if (runId == null || runId.isBlank()) {
                log.info("[WS] SUBSCRIBE_RUN rejected: missing runId");
                sendError(session, CODE_MISSING_RUN_ID, "SUBSCRIBE_RUN requires runId");
                return;
            }
            ChatRunStore.RunRecord run = runStore.get(runId);
            if (run == null) {
                log.info("[WS] SUBSCRIBE_RUN rejected: run not found runId={}", runId);
                sendError(session, CODE_NOT_FOUND, "Run not found: " + runId);
                return;
            }
            String sessionTenantId = (String) session.getAttributes().get(WebSocketAuthHandshakeHandler.ATTR_TENANT_ID);
            boolean tenantOk = run.tenantId == null || run.tenantId.isBlank()
                    || sessionTenantId == null || sessionTenantId.equals(run.tenantId);
            if (!tenantOk) {
                log.warn("[WS] SUBSCRIBE_RUN rejected: tenant mismatch runId={} runTenant={} sessionTenant={}",
                        runId, run.tenantId, sessionTenantId);
                sendError(session, CODE_FORBIDDEN, "Tenant mismatch: cannot subscribe to this run");
                return;
            }
            log.info("[WS] SUBSCRIBE_RUN ok runId={} sessionTenant={} runTenant={}", runId, sessionTenantId, run.tenantId);
            registry.subscribe(runId, session);
            sendCatchUp(session, runId);
        } else if (PING.equals(type)) {
            sendPong(session);
        } else {
            sendError(session, CODE_INVALID_MESSAGE, "Unknown message type: " + (type != null ? type : "(missing type)"));
        }
    }

    private void sendPong(WebSocketSession session) {
        if (session == null || !session.isOpen()) return;
        try {
            String json = objectMapper.writeValueAsString(Map.of("type", PONG_TYPE));
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.warn("Failed to send PONG: {}", e.getMessage());
        }
    }

    private void sendError(WebSocketSession session, String code, String message) {
        if (session == null || !session.isOpen()) return;
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "type", ERROR_TYPE,
                    "code", code,
                    "message", message != null ? message : ""
            ));
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.warn("Failed to send error frame: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.unsubscribe(session);
        log.debug("WebSocket closed: {} status={}", session.getId(), status);
    }

    private void sendCatchUp(WebSocketSession session, String runId) throws IOException {
        if (!session.isOpen()) return;
        for (OloExecutionEvent event : eventStore.getEvents(runId)) {
            if (!session.isOpen()) return;
            session.sendMessage(new TextMessage(envelopeRunEvent(event)));
        }
    }

    /** Envelope for future extensibility: RUN_EVENT, ERROR, PONG, etc. */
    String envelopeRunEvent(OloExecutionEvent event) throws IOException {
        return objectMapper.writeValueAsString(Map.of(
                "type", RUN_EVENT_TYPE,
                "payload", ExecutionEventSanitizer.forApi(event)
        ));
    }
}
