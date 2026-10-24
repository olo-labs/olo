/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.security.store;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-process {@link IdentityStore} for tests (`olo.security.store=memory`). Not for deployments: state is lost on
 * restart and isn't shared between instances.
 */
public final class InMemoryIdentityStore implements IdentityStore {

    private final Map<String, String> users = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> memberships = new ConcurrentHashMap<>();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> revocations = new ConcurrentHashMap<>();

    private record Session(String userId, long epoch, boolean revoked, java.time.Instant authenticatedUntil) {
    }

    @Override
    public String userIdFor(String issuer, String subject) {
        return users.computeIfAbsent(IdentityKeys.subjectKey(issuer, subject), k -> UUID.randomUUID().toString());
    }

    @Override
    public Optional<String> findUserId(String issuer, String subject) {
        return Optional.ofNullable(users.get(IdentityKeys.subjectKey(issuer, subject)));
    }

    @Override
    public Set<String> tenantsOf(String userId) {
        return Set.copyOf(memberships.getOrDefault(userId, Set.of()));
    }

    @Override
    public void addMembership(String userId, String tenantId) {
        memberships.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(tenantId);
    }

    @Override
    public void removeMembership(String userId, String tenantId) {
        memberships.getOrDefault(userId, new HashSet<>()).remove(tenantId);
    }

    @Override
    public SessionState session(String issuer, String sessionId, String userId, java.time.Instant authenticatedUntil) {
        java.util.Objects.requireNonNull(authenticatedUntil, "authenticatedUntil");
        Session session = sessions.compute(IdentityKeys.sessionKey(issuer, sessionId), (k, old) -> {
            if (old == null) return new Session(userId, 1, false, authenticatedUntil);
            if (old.userId().equals(userId) && !old.revoked() && authenticatedUntil.isAfter(old.authenticatedUntil())) {
                return new Session(userId, old.epoch(), false, authenticatedUntil);
            }
            return old;
        });
        if (!session.userId().equals(userId)) {
            return SessionState.foreignSession();
        }
        return new SessionState(session.epoch(), session.revoked(), false);
    }

    @Override
    public Optional<SessionInfo> findSession(String issuer, String sessionId) {
        Session session = sessions.get(IdentityKeys.sessionKey(issuer, sessionId));
        return session == null ? Optional.empty() : Optional.of(new SessionInfo(session.userId(), session.epoch(), session.revoked(), session.authenticatedUntil()));
    }

    @Override
    public void revokeSession(String issuer, String sessionId) {
        sessions.computeIfPresent(IdentityKeys.sessionKey(issuer, sessionId),
                (k, s) -> new Session(s.userId(), s.epoch() + 1, true, s.authenticatedUntil()));
    }

    @Override
    public int revokeAllSessions(String userId) {
        int[] count = {0};
        sessions.replaceAll((k, s) -> {
            if (s.userId().equals(userId) && !s.revoked()) {
                count[0]++;
                return new Session(s.userId(), s.epoch() + 1, true, s.authenticatedUntil());
            }
            return s;
        });
        return count[0];
    }

    @Override public void enqueueRevocation(String userId, String eventId) { revocations.put(userId, eventId); }
    @Override public Map<String, String> pendingRevocations() { return Map.copyOf(revocations); }
    @Override public void acknowledgeRevocation(String userId, String eventId) { revocations.remove(userId, eventId); }
}
