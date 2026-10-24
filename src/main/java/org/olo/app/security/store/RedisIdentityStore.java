/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.security.store;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Redis-backed {@link IdentityStore}, shared by every OLO backend instance.
 *
 * <ul>
 *   <li>{@code olo:sec:identity:<sha256(iss,sub)>} → user id, created with SETNX so concurrent first sign-ins agree</li>
 *   <li>{@code olo:sec:members:<userId>} → set of tenant ids</li>
 *   <li>{@code olo:sec:session:<sha256(iss,sid)>} → hash {userId, epoch, revoked}; the user id is set with HSETNX</li>
 * </ul>
 */
public final class RedisIdentityStore implements IdentityStore {

    /** Kept well beyond any token lifetime, so a revoked session id stays refused. */
    static final Duration SESSION_RETENTION = Duration.ofDays(30);

    private final StringRedisTemplate redis;

    public RedisIdentityStore(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis");
    }

    @Override
    public String userIdFor(String issuer, String subject) {
        String key = IdentityKeys.subjectKey(issuer, subject);
        String candidate = UUID.randomUUID().toString();
        Boolean created = redis.opsForValue().setIfAbsent(key, candidate);
        if (Boolean.TRUE.equals(created)) {
            redis.opsForHash().putAll(IdentityKeys.userKey(candidate), Map.of("issuer", issuer, "subject", subject));
            return candidate;
        }
        return Objects.requireNonNull(redis.opsForValue().get(key), "identity mapping vanished for " + key);
    }

    @Override
    public Optional<String> findUserId(String issuer, String subject) {
        return Optional.ofNullable(redis.opsForValue().get(IdentityKeys.subjectKey(issuer, subject)));
    }

    @Override
    public Set<String> tenantsOf(String userId) {
        Set<String> tenants = redis.opsForSet().members(IdentityKeys.membershipKey(userId));
        return tenants == null ? Set.of() : Set.copyOf(tenants);
    }

    @Override
    public void addMembership(String userId, String tenantId) {
        redis.opsForSet().add(IdentityKeys.membershipKey(userId), tenantId);
    }

    @Override
    public void removeMembership(String userId, String tenantId) {
        redis.opsForSet().remove(IdentityKeys.membershipKey(userId), tenantId);
    }

    @Override
    public SessionState session(String issuer, String sessionId, String userId, java.time.Instant authenticatedUntil) {
        String key = IdentityKeys.sessionKey(issuer, sessionId);
        var script = new org.springframework.data.redis.core.script.DefaultRedisScript<java.util.List>("""
                local owner = redis.call('HGET', KEYS[1], 'userId')
                if owner and owner ~= ARGV[1] then return {0, 1, 1} end
                if not owner then
                  redis.call('HSET', KEYS[1], 'userId', ARGV[1], 'epoch', 1, 'revoked', 'false', 'authenticatedUntil', ARGV[2])
                elseif redis.call('HGET', KEYS[1], 'revoked') ~= 'true' then
                  local old = tonumber(redis.call('HGET', KEYS[1], 'authenticatedUntil') or '0')
                  if tonumber(ARGV[2]) > old then redis.call('HSET', KEYS[1], 'authenticatedUntil', ARGV[2]) end
                end
                redis.call('PEXPIRE', KEYS[1], ARGV[3])
                redis.call('SADD', KEYS[2], KEYS[1])
                local revoked = 0
                if redis.call('HGET', KEYS[1], 'revoked') == 'true' then revoked = 1 end
                return {tonumber(redis.call('HGET', KEYS[1], 'epoch')), revoked, 0}
                """, java.util.List.class);
        var result = redis.execute(script, java.util.List.of(key, IdentityKeys.userSessionsKey(userId)), userId,
                Long.toString(authenticatedUntil.toEpochMilli()), Long.toString(Math.max(SESSION_RETENTION.toMillis(),
                        authenticatedUntil.toEpochMilli() - System.currentTimeMillis() + SESSION_RETENTION.toMillis())));
        if (result == null || result.size() != 3) throw new IllegalStateException("session update failed");
        return new SessionState(((Number) result.get(0)).longValue(), ((Number) result.get(1)).intValue() != 0,
                ((Number) result.get(2)).intValue() != 0);
    }

    @Override
    public Optional<SessionInfo> findSession(String issuer, String sessionId) {
        Map<Object, Object> fields = redis.opsForHash().entries(IdentityKeys.sessionKey(issuer, sessionId));
        if (fields == null || !(fields.get("userId") instanceof String userId)) {
            return Optional.empty();
        }
        return Optional.of(new SessionInfo(userId, Long.parseLong(String.valueOf(fields.getOrDefault("epoch", "1"))),
                Boolean.parseBoolean(String.valueOf(fields.getOrDefault("revoked", "false"))),
                java.time.Instant.ofEpochMilli(Long.parseLong(String.valueOf(fields.getOrDefault("authenticatedUntil", "0"))))));
    }

    @Override
    public void revokeSession(String issuer, String sessionId) {
        revokeKey(IdentityKeys.sessionKey(issuer, sessionId));
    }

    @Override
    public int revokeAllSessions(String userId) {
        Set<String> keys = redis.opsForSet().members(IdentityKeys.userSessionsKey(userId));
        if (keys == null) {
            return 0;
        }
        int count = 0;
        for (String key : keys) {
            if (revokeKey(key)) {
                count++;
            }
        }
        return count;
    }

    private boolean revokeKey(String key) {
        if (!Boolean.TRUE.equals(redis.hasKey(key))) {
            return false;
        }
        boolean wasRevoked = Boolean.parseBoolean(String.valueOf(redis.opsForHash().get(key, "revoked")));
        redis.opsForHash().increment(key, "epoch", 1);
        redis.opsForHash().put(key, "revoked", "true");
        redis.expire(key, SESSION_RETENTION);
        return !wasRevoked;
    }

    private static final String REVOCATIONS = "olo:sec:toolgate-revocations";
    @Override public void enqueueRevocation(String userId, String eventId) {
        redis.opsForHash().put(REVOCATIONS, userId, eventId);
    }
    @Override public Map<String, String> pendingRevocations() {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        redis.opsForHash().entries(REVOCATIONS).forEach((user, event) -> result.put(user.toString(), event.toString()));
        return result;
    }
    @Override public void acknowledgeRevocation(String userId, String eventId) {
        var script = new org.springframework.data.redis.core.script.DefaultRedisScript<Long>(
                "if redis.call('HGET', KEYS[1], ARGV[1]) == ARGV[2] then return redis.call('HDEL', KEYS[1], ARGV[1]) end return 0", Long.class);
        redis.execute(script, java.util.List.of(REVOCATIONS), userId, eventId);
    }
}
