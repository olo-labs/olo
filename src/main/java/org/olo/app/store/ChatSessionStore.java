/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.store;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for chat sessions. Demo only.
 */
public class ChatSessionStore {

    public static class SessionRecord {
        public final String sessionId;
        public final String tenantId;
        public final long createdAt;
        public final long lastActivityAt;
        public final String queueName;
        public final String pipelineId;

        public SessionRecord(String sessionId, String tenantId) {
            this(sessionId, tenantId, System.currentTimeMillis(), System.currentTimeMillis(), null, null);
        }

        public SessionRecord(String sessionId, String tenantId, long createdAt, long lastActivityAt) {
            this(sessionId, tenantId, createdAt, lastActivityAt, null, null);
        }

        public SessionRecord(String sessionId, String tenantId, long createdAt, long lastActivityAt, String queueName, String pipelineId) {
            this.sessionId = sessionId;
            this.tenantId = tenantId;
            this.createdAt = createdAt;
            this.lastActivityAt = lastActivityAt;
            this.queueName = queueName;
            this.pipelineId = pipelineId;
        }
    }

    private final Map<String, SessionRecord> sessions = new ConcurrentHashMap<>();

    public void put(SessionRecord session) {
        sessions.put(session.sessionId, session);
    }

    public SessionRecord get(String sessionId) {
        return sessions.get(sessionId);
    }

    /** Delete one session. Returns the removed record (with tenantId for cleanup), or null if not found. */
    public SessionRecord delete(String sessionId) {
        return sessions.remove(sessionId);
    }

    /** List sessions for a tenant, optionally filtered by queue and pipeline, most recently active first. */
    public List<SessionRecord> listByTenant(String tenantId, String queueName, String pipelineId) {
        if (tenantId == null || tenantId.isBlank()) return List.of();
        List<SessionRecord> out = new ArrayList<>();
        for (SessionRecord r : sessions.values()) {
            if (!tenantId.equals(r.tenantId)) continue;
            if (queueName != null && !queueName.isBlank() && !queueName.equals(r.queueName)) continue;
            if (pipelineId != null && !pipelineId.isBlank() && !pipelineId.equals(r.pipelineId)) continue;
            out.add(r);
        }
        out.sort(Comparator.comparingLong((SessionRecord r) -> r.lastActivityAt).reversed());
        return out;
    }

    /** Update lastActivityAt for a session (e.g. when a message is sent). */
    public void touch(String sessionId) {
        SessionRecord r = sessions.get(sessionId);
        if (r != null) {
            long now = System.currentTimeMillis();
            sessions.put(sessionId, new SessionRecord(r.sessionId, r.tenantId, r.createdAt, now));
        }
    }

    /** Delete all sessions for a tenant, optionally limited to queue+pipeline. Returns the list of removed session ids. */
    public List<String> deleteAllForTenant(String tenantId, String queueName, String pipelineId) {
        if (tenantId == null || tenantId.isBlank()) return List.of();
        List<String> removed = new ArrayList<>();
        for (SessionRecord r : new ArrayList<>(sessions.values())) {
            if (!tenantId.equals(r.tenantId)) continue;
            if (queueName != null && !queueName.isBlank() && !queueName.equals(r.queueName)) continue;
            if (pipelineId != null && !pipelineId.isBlank() && !pipelineId.equals(r.pipelineId)) continue;
            sessions.remove(r.sessionId);
            removed.add(r.sessionId);
        }
        return removed;
    }
}
