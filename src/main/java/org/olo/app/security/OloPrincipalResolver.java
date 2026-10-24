/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.security;

import org.olo.app.security.store.IdentityStore;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * Builds the caller's {@link OloPrincipal} from an already-verified JWT plus OLO's own records.
 *
 * <ol>
 *   <li>The stable user id comes from {@code (iss, sub)}.</li>
 *   <li>The session comes from the {@code sid} claim. A revoked session, or one that belongs to another user, is refused.</li>
 *   <li>The tenant must be one of the user's memberships. A requested tenant (header) must be a membership. With no
 *       request, a single membership is used, and several are ambiguous. <strong>There is no fallback to {@code sub} or to
 *       a default tenant.</strong></li>
 * </ol>
 */
public final class OloPrincipalResolver {

    private final IdentityStore store;
    private final OloSecurityProperties properties;

    public OloPrincipalResolver(IdentityStore store, OloSecurityProperties properties) {
        this.store = Objects.requireNonNull(store, "store");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * @param jwt             a token that already passed signature, issuer, audience and time validation
     * @param requestedTenant the tenant the caller asked for, or {@code null}
     */
    public OloPrincipal resolve(Jwt jwt, String requestedTenant) {
        String issuer = jwt.getClaimAsString("iss");
        String subject = jwt.getSubject();
        String sessionId = jwt.getClaimAsString(properties.sessionClaim());
        if (isBlank(issuer) || isBlank(subject) || isBlank(sessionId)) {
            throw OloIdentityException.unauthorized("incomplete_token",
                    "token must carry iss, sub and " + properties.sessionClaim());
        }

        String userId = store.userIdFor(issuer, subject);

        IdentityStore.SessionState session = store.session(issuer, sessionId, userId, jwt.getExpiresAt());
        if (session.foreign()) {
            throw OloIdentityException.unauthorized("session_mismatch", "session belongs to another user");
        }
        if (session.revoked()) {
            throw OloIdentityException.unauthorized("session_revoked", "session has been revoked; sign in again");
        }

        String tenantId = selectTenant(store.tenantsOf(userId), requestedTenant);
        return new OloPrincipal(tenantId, userId, issuer, subject, sessionId, session.epoch(), jwt.getExpiresAt(),
                hasAdminRole(jwt));
    }

    private static String selectTenant(Set<String> memberships, String requestedTenant) {
        if (memberships.isEmpty()) {
            throw OloIdentityException.forbidden("no_membership", "user is not a member of any tenant");
        }
        if (!isBlank(requestedTenant)) {
            String requested = requestedTenant.trim();
            if (!memberships.contains(requested)) {
                throw OloIdentityException.forbidden("not_a_member", "user is not a member of the requested tenant");
            }
            return requested;
        }
        if (memberships.size() == 1) {
            return memberships.iterator().next();
        }
        throw OloIdentityException.forbidden("tenant_required",
                "user belongs to several tenants; choose one with the tenant header");
    }

    private boolean hasAdminRole(Jwt jwt) {
        Object roles = jwt.getClaims().get(properties.rolesClaim());
        if (roles instanceof Collection<?> collection) {
            return collection.stream().anyMatch(r -> properties.adminRole().equals(String.valueOf(r)));
        }
        if (roles instanceof String text) {
            for (String role : text.split("[\\s,]+")) {
                if (properties.adminRole().equals(role)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
