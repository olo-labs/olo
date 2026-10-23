/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.security;

import java.time.Instant;
import java.util.Objects;

/**
 * The verified identity of the caller, built only from a JWT whose signature, issuer, audience and expiry were checked,
 * plus OLO's own records. A tenant or user id in a request path, body or header is never identity.
 *
 * @param tenantId       a tenant the user is a member of (from OLO's membership store; never the token's {@code sub})
 * @param userId         stable internal user id, mapped once from {@code (issuer, subject)}
 * @param issuer         verified {@code iss}
 * @param subject        verified {@code sub}
 * @param sessionId      the IdP session ({@code sid}) this token belongs to
 * @param sessionEpoch   OLO's epoch for that session; it increases when the session is revoked
 * @param authExpiresAt  the token's {@code exp}
 * @param admin          whether the verified token carries the configured OLO admin role
 */
public record OloPrincipal(
        String tenantId,
        String userId,
        String issuer,
        String subject,
        String sessionId,
        long sessionEpoch,
        Instant authExpiresAt,
        boolean admin) {

    public OloPrincipal {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(sessionId, "sessionId");
    }

    /** True when {@code tenantId} is exactly this principal's tenant. */
    public boolean ownsTenant(String tenantId) {
        return this.tenantId.equals(tenantId);
    }
}
