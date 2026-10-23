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
    public SessionState session(String issuer, String sessionId, String userId) {
        String key = IdentityKeys.sessionKey(issuer, sessionId);
        Boolean created = redis.opsForHash().putIfAbsent(key, "userId", userId);
        if (Boolean.TRUE.equals(created)) {
            redis.opsForHash().putAll(key, Map.of("epoch", "1", "revoked", "false"));
            redis.expire(key, SESSION_RETENTION);
            redis.opsForSet().add(IdentityKeys.userSessionsKey(userId), key);
            return new SessionState(1, false, false);
        }
        Map<Object, Object> fields = redis.opsForHash().entries(key);
        if (!userId.equals(fields.get("userId"))) {
            return SessionState.foreignSession();
        }
        long epoch = Long.parseLong(String.valueOf(fields.getOrDefault("epoch", "1")));
        boolean revoked = Boolean.parseBoolean(String.valueOf(fields.getOrDefault("revoked", "false")));
        return new SessionState(epoch, revoked, false);
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
}
