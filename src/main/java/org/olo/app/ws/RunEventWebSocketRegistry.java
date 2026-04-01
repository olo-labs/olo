/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.ws;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Registers WebSocket sessions per runId. Used by the WebSocket handler (SUBSCRIBE_RUN)
 * and by the broadcaster to push events to subscribed sessions.
 * One session may subscribe to multiple runs; on close we remove the session from all runs.
 */
public class RunEventWebSocketRegistry {

    /** runId -> list of sessions subscribed to that run */
    private final ConcurrentHashMap<String, List<WebSocketSession>> sessionsByRunId = new ConcurrentHashMap<>();

    /** session id -> set of runIds (for cleanup on close when one connection subscribes to multiple runs) */
    private final ConcurrentHashMap<String, Set<String>> runIdsBySessionId = new ConcurrentHashMap<>();

    public void subscribe(String runId, WebSocketSession session) {
        if (runId == null || runId.isBlank() || session == null || !session.isOpen()) return;
        sessionsByRunId.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>()).add(session);
        runIdsBySessionId.computeIfAbsent(session.getId(), k -> new CopyOnWriteArraySet<>()).add(runId);
    }

    public void unsubscribe(WebSocketSession session) {
        if (session == null) return;
        Set<String> runIds = runIdsBySessionId.remove(session.getId());
        if (runIds != null) {
            for (String runId : runIds) {
                List<WebSocketSession> list = sessionsByRunId.get(runId);
                if (list != null) {
                    list.remove(session);
                    if (list.isEmpty()) sessionsByRunId.remove(runId);
                }
            }
        }
    }

    /** Returns sessions subscribed to this runId. Caller must not modify the list. */
    public List<WebSocketSession> getSessions(String runId) {
        List<WebSocketSession> list = sessionsByRunId.get(runId);
        return list != null ? list : List.of();
    }

    /**
     * Send text message to all sessions subscribed to runId. Unsubscribes closed/failed sessions.
     */
    public void sendToRun(String runId, String text) {
        List<WebSocketSession> list = sessionsByRunId.get(runId);
        if (list == null) return;
        for (WebSocketSession session : List.copyOf(list)) {
            if (!sendToOne(session, text)) unsubscribe(session);
        }
    }

    private static boolean sendToOne(WebSocketSession session, String text) {
        if (!session.isOpen()) return false;
        try {
            session.sendMessage(new TextMessage(text));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
