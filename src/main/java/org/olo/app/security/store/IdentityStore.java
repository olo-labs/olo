/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.security.store;

import java.util.Optional;
import java.util.Set;

/**
 * OLO's own identity records: stable user ids, tenant membership, and the state of sign-in sessions.
 *
 * <p>Every key is derived from verified token claims; nothing here is ever looked up from request input.
 */
public interface IdentityStore {

    /** The stable internal user id for a verified {@code (issuer, subject)}, created on first sight. Never changes. */
    String userIdFor(String issuer, String subject);

    /** The user id already mapped to {@code (issuer, subject)}, without creating one. */
    Optional<String> findUserId(String issuer, String subject);

    Set<String> tenantsOf(String userId);

    void addMembership(String userId, String tenantId);

    void removeMembership(String userId, String tenantId);

    /**
     * Registers the session on first sight and returns its state. A session id seen first for another user is reported
     * as {@link SessionState#foreign()} and must be refused.
     */
    SessionState session(String issuer, String sessionId, String userId, java.time.Instant authenticatedUntil);

    /** The recorded session, without registering it. */
    Optional<SessionInfo> findSession(String issuer, String sessionId);

    /** Revokes one session: it is refused from now on, and its epoch increases. */
    void revokeSession(String issuer, String sessionId);

    /** Revokes every session recorded for the user; returns how many were revoked. */
    int revokeAllSessions(String userId);

    /**
     * @param epoch   increases on every revocation; 1 for a new session
     * @param revoked whether the session may no longer be used
     * @param foreign whether the session belongs to a different user than the caller's token
     */
    /** A recorded session: its owner, epoch and whether it was revoked. */
    record SessionInfo(String userId, long epoch, boolean revoked, java.time.Instant authenticatedUntil) {
    }

    /** Durable notification outbox. Acknowledgement must not remove a newer event for the same user. */
    void enqueueRevocation(String userId, String eventId);
    java.util.Map<String, String> pendingRevocations();
    void acknowledgeRevocation(String userId, String eventId);

    record SessionState(long epoch, boolean revoked, boolean foreign) {
        public static SessionState foreignSession() {
            return new SessionState(0, true, true);
        }
    }
}
