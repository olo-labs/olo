// Copyright 2026 OLO Labs
// SPDX-License-Identifier: Apache-2.0
package org.olo.app.toolgate;

import org.olo.app.security.store.IdentityStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;

/**
 * Passes every session revocation on to ToolGate, so outstanding ToolGate user credentials stop at Control's next
 * authorization instead of at expiry (the 30-second revocation bound). Local revocation always happens first and never
 * Failed notifications remain in the identity store's durable outbox and are retried. User credentials live at most
 * fifteen seconds, bounding stale authority even when the notification channel is unavailable.
 */
public final class RevocationPropagatingIdentityStore implements IdentityStore {

    private static final Logger log = LoggerFactory.getLogger(RevocationPropagatingIdentityStore.class);

    private final IdentityStore delegate;
    private final ToolGateClient toolGate;

    public RevocationPropagatingIdentityStore(IdentityStore delegate, ToolGateClient toolGate) {
        this.delegate = delegate;
        this.toolGate = toolGate;
    }

    @Override
    public String userIdFor(String issuer, String subject) {
        return delegate.userIdFor(issuer, subject);
    }

    @Override
    public Optional<String> findUserId(String issuer, String subject) {
        return delegate.findUserId(issuer, subject);
    }

    @Override
    public Set<String> tenantsOf(String userId) {
        return delegate.tenantsOf(userId);
    }

    @Override
    public void addMembership(String userId, String tenantId) {
        delegate.addMembership(userId, tenantId);
    }

    @Override
    public void removeMembership(String userId, String tenantId) {
        delegate.removeMembership(userId, tenantId);
        // ToolGate knows users, not OLO tenants: ending its sessions makes every new credential pass the membership check here.
        propagate(userId);
    }

    @Override
    public SessionState session(String issuer, String sessionId, String userId, java.time.Instant authenticatedUntil) {
        return delegate.session(issuer, sessionId, userId, authenticatedUntil);
    }

    @Override
    public Optional<SessionInfo> findSession(String issuer, String sessionId) {
        return delegate.findSession(issuer, sessionId);
    }

    @Override
    public void revokeSession(String issuer, String sessionId) {
        delegate.revokeSession(issuer, sessionId);
        delegate.findSession(issuer, sessionId).ifPresent(session -> propagate(session.userId()));
    }

    @Override
    public int revokeAllSessions(String userId) {
        int revoked = delegate.revokeAllSessions(userId);
        propagate(userId);
        return revoked;
    }

    private void propagate(String userId) {
        String eventId = java.util.UUID.randomUUID().toString();
        delegate.enqueueRevocation(userId, eventId);
        deliver(userId, eventId);
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 5000)
    public void retryPendingRevocations() {
        delegate.pendingRevocations().forEach(this::deliver);
    }

    private void deliver(String userId, String eventId) {
        try {
            toolGate.revoke(userId);
            delegate.acknowledgeRevocation(userId, eventId);
        } catch (RuntimeException e) {
            log.warn("ToolGate revocation pending for user {}; retrying from durable outbox", userId);
        }
    }

    @Override public void enqueueRevocation(String userId, String eventId) { delegate.enqueueRevocation(userId, eventId); }
    @Override public java.util.Map<String, String> pendingRevocations() { return delegate.pendingRevocations(); }
    @Override public void acknowledgeRevocation(String userId, String eventId) { delegate.acknowledgeRevocation(userId, eventId); }
}
