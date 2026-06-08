/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.olo.app.store.ChatSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persists chat sessions and messages to Redis so they survive restarts.
 * Keys: <tenantId>:olo:chat:session:<sessionId> (session JSON), <tenantId>:olo:chat:messages:<sessionId> (list of message JSON).
 * List sessions returns by lastActivityAt descending (most recent first).
 */
@Service
public class ChatRedisPersistence {

    private static final Logger log = LoggerFactory.getLogger(ChatRedisPersistence.class);
    private static final String SESSION_PREFIX = ":olo:chat:session:";
    private static final String MESSAGES_PREFIX = ":olo:chat:messages:";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StringRedisTemplate redisTemplate;
    private final String defaultTenantId;

    public ChatRedisPersistence(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            @Qualifier("oloDefaultTenantId") String defaultTenantId) {
        this.redisTemplate = redisTemplate;
        this.defaultTenantId = (defaultTenantId != null && !defaultTenantId.isBlank())
                ? defaultTenantId.trim()
                : "default";
    }

    private static String effectiveTenantId(String tenantId, String configuredDefault) {
        if (tenantId == null || tenantId.isBlank()) {
            return configuredDefault;
        }
        return tenantId.trim();
    }

    public void saveSession(String tenantId, String sessionId, long createdAt, long lastActivityAt, String queueName, String pipelineId) {
        if (redisTemplate == null) return;
        String key = effectiveTenantId(tenantId, defaultTenantId) + SESSION_PREFIX + sessionId;
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sessionId", sessionId);
            m.put("tenantId", tenantId != null ? tenantId : "");
            m.put("createdAt", createdAt);
            m.put("lastActivityAt", lastActivityAt);
            m.put("queueName", queueName != null ? queueName : "");
            m.put("pipelineId", pipelineId != null ? pipelineId : "");
            redisTemplate.opsForValue().set(key, MAPPER.writeValueAsString(m));
        } catch (Exception e) {
            log.debug("Redis saveSession failed for sessionId={}", sessionId, e);
        }
    }

    public void touchSession(String tenantId, String sessionId) {
        if (redisTemplate == null) return;
        String key = effectiveTenantId(tenantId, defaultTenantId) + SESSION_PREFIX + sessionId;
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null) return;
            @SuppressWarnings("unchecked")
            Map<String, Object> m = MAPPER.readValue(raw, Map.class);
            m.put("lastActivityAt", System.currentTimeMillis());
            redisTemplate.opsForValue().set(key, MAPPER.writeValueAsString(m));
        } catch (Exception e) {
            log.debug("Redis touchSession failed for sessionId={}", sessionId, e);
        }
    }

    /** List sessions for tenant from Redis, optionally filtered by queue and pipeline, sorted by lastActivityAt descending. */
    public List<ChatSessionStore.SessionRecord> listSessionsByTenant(String tenantId, String queueName, String pipelineId) {
        if (redisTemplate == null) return null;
        String prefix = effectiveTenantId(tenantId, defaultTenantId) + SESSION_PREFIX;
        try {
            Set<String> keys = redisTemplate.keys(prefix + "*");
            if (keys == null || keys.isEmpty()) return new ArrayList<>();
            List<ChatSessionStore.SessionRecord> out = new ArrayList<>();
            for (String key : keys) {
                String raw = redisTemplate.opsForValue().get(key);
                if (raw == null) continue;
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = MAPPER.readValue(raw, Map.class);
                    String sid = (String) m.get("sessionId");
                    String tid = (String) m.get("tenantId");
                    Number createdAt = (Number) m.get("createdAt");
                    Number lastActivityAt = (Number) m.get("lastActivityAt");
                    String q = (String) m.get("queueName");
                    String p = (String) m.get("pipelineId");
                    if (queueName != null && !queueName.isBlank() && !queueName.equals(q)) continue;
                    if (pipelineId != null && !pipelineId.isBlank() && !pipelineId.equals(p)) continue;
                    if (sid != null && createdAt != null && lastActivityAt != null) {
                        out.add(new ChatSessionStore.SessionRecord(sid, tid != null ? tid : "",
                                createdAt.longValue(), lastActivityAt.longValue(),
                                q != null ? q : null, p != null ? p : null));
                    }
                } catch (Exception e) {
                    log.trace("Parse session JSON failed for key={}", key, e);
                }
            }
            out.sort(Comparator.comparingLong((ChatSessionStore.SessionRecord r) -> r.lastActivityAt).reversed());
            return out;
        } catch (Exception e) {
            log.debug("Redis listSessions failed for tenantId={}", tenantId, e);
            return null;
        }
    }

    public void appendMessage(String tenantId, String sessionId, String messageId, String role, String content, String runId, long createdAt) {
        if (redisTemplate == null) return;
        String key = effectiveTenantId(tenantId, defaultTenantId) + MESSAGES_PREFIX + sessionId;
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("messageId", messageId);
            m.put("sessionId", sessionId);
            m.put("role", role != null ? role : "user");
            m.put("content", content != null ? content : "");
            m.put("runId", runId != null ? runId : "");
            m.put("createdAt", createdAt);
            redisTemplate.opsForList().rightPush(key, MAPPER.writeValueAsString(m));
        } catch (Exception e) {
            log.debug("Redis appendMessage failed for sessionId={}", sessionId, e);
        }
    }

    /** List messages for a session from Redis. Returns null if Redis unavailable or error. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listMessages(String tenantId, String sessionId) {
        if (redisTemplate == null) return null;
        String key = effectiveTenantId(tenantId, defaultTenantId) + MESSAGES_PREFIX + sessionId;
        try {
            List<String> rawList = redisTemplate.opsForList().range(key, 0, -1);
            if (rawList == null || rawList.isEmpty()) return new ArrayList<>();
            List<Map<String, Object>> out = new ArrayList<>();
            for (String raw : rawList) {
                try {
                    out.add(MAPPER.readValue(raw, Map.class));
                } catch (Exception e) {
                    log.trace("Parse message JSON failed", e);
                }
            }
            return out;
        } catch (Exception e) {
            log.debug("Redis listMessages failed for sessionId={}", sessionId, e);
            return null;
        }
    }

    public void deleteSession(String tenantId, String sessionId) {
        if (redisTemplate == null) return;
        String prefix = effectiveTenantId(tenantId, defaultTenantId);
        try {
            redisTemplate.delete(prefix + SESSION_PREFIX + sessionId);
            redisTemplate.delete(prefix + MESSAGES_PREFIX + sessionId);
        } catch (Exception e) {
            log.debug("Redis deleteSession failed for sessionId={}", sessionId, e);
        }
    }

    /** Delete all sessions for tenant, optionally limited to queue+pipeline. */
    public void deleteAllForTenant(String tenantId, String queueName, String pipelineId) {
        if (redisTemplate == null) return;
        String prefix = effectiveTenantId(tenantId, defaultTenantId);
        try {
            Set<String> sessionKeys = redisTemplate.keys(prefix + SESSION_PREFIX + "*");
            if (sessionKeys == null) return;
            for (String key : sessionKeys) {
                if (queueName != null && !queueName.isBlank() || pipelineId != null && !pipelineId.isBlank()) {
                    String raw = redisTemplate.opsForValue().get(key);
                    if (raw != null) {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> m = MAPPER.readValue(raw, Map.class);
                            String q = (String) m.get("queueName");
                            String p = (String) m.get("pipelineId");
                            if (queueName != null && !queueName.isBlank() && !queueName.equals(q)) continue;
                            if (pipelineId != null && !pipelineId.isBlank() && !pipelineId.equals(p)) continue;
                        } catch (Exception e) {
                            continue;
                        }
                    }
                }
                String sessionId = key.substring((prefix + SESSION_PREFIX).length());
                redisTemplate.delete(key);
                redisTemplate.delete(prefix + MESSAGES_PREFIX + sessionId);
            }
        } catch (Exception e) {
            log.debug("Redis deleteAllForTenant failed for tenantId={}", tenantId, e);
        }
    }
}
