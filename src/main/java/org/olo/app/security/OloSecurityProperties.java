/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * {@code olo.security.*}: how OLO verifies sign-in tokens and resolves identity.
 *
 * <p>{@code issuer} and {@code audience} are required, plus {@code jwkSetUri} (or the issuer's OIDC discovery). The
 * application refuses to start without them: there is no unauthenticated or demo mode.
 *
 * @param issuer               exact expected {@code iss}
 * @param jwkSetUri            JWKS endpoint; blank uses OIDC discovery from {@code issuer}
 * @param audience             value that must appear in {@code aud}
 * @param algorithms           accepted JWS algorithms
 * @param clockSkew            allowed clock skew for {@code exp} and {@code nbf}
 * @param sessionClaim         claim that identifies the IdP session (OIDC {@code sid})
 * @param tenantHeader         header a caller uses to pick one of their tenants
 * @param rolesClaim           claim holding the caller's roles
 * @param adminRole            role that grants OLO administration (membership and session management)
 * @param store                {@code redis} (default) or {@code memory} (single-process tests only)
 * @param publicPaths          paths served without a token (health, API docs)
 * @param bootstrapMemberships memberships applied at startup, so a fresh installation has its first administrators
 */
@ConfigurationProperties(prefix = "olo.security")
public record OloSecurityProperties(
        String issuer,
        String jwkSetUri,
        String audience,
        List<String> algorithms,
        Duration clockSkew,
        String sessionClaim,
        String tenantHeader,
        String rolesClaim,
        String adminRole,
        String store,
        List<String> publicPaths,
        List<BootstrapMembership> bootstrapMemberships) {

    /** Tenant ids: 1-128 letters, digits, '.', '_' or '-', starting with a letter or digit. */
    private static final java.util.regex.Pattern TENANT_ID = java.util.regex.Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    public OloSecurityProperties {
        algorithms = algorithms == null || algorithms.isEmpty() ? List.of("RS256", "ES256") : List.copyOf(algorithms);
        clockSkew = clockSkew == null ? Duration.ofSeconds(30) : clockSkew;
        sessionClaim = blankToDefault(sessionClaim, "sid");
        tenantHeader = blankToDefault(tenantHeader, "X-OLO-Tenant");
        rolesClaim = blankToDefault(rolesClaim, "roles");
        adminRole = blankToDefault(adminRole, "olo-admin");
        store = blankToDefault(store, "redis");
        publicPaths = publicPaths == null ? List.of("/api/health") : List.copyOf(publicPaths);
        bootstrapMemberships = bootstrapMemberships == null ? List.of() : List.copyOf(bootstrapMemberships);
    }

    /** One startup membership: the user with this verified {@code (issuer, subject)} is a member of {@code tenants}. */
    public record BootstrapMembership(String issuer, String subject, List<String> tenants) {
        public BootstrapMembership {
            tenants = tenants == null ? List.of() : List.copyOf(tenants);
        }
    }

    /** Throws when required settings are missing, so the application fails to start instead of running open. */
    public void validate() {
        if (isBlank(issuer)) {
            throw new IllegalStateException("olo.security.issuer (OLO_SECURITY_ISSUER) is required");
        }
        if (isBlank(audience)) {
            throw new IllegalStateException("olo.security.audience (OLO_SECURITY_AUDIENCE) is required");
        }
        if (!"redis".equals(store) && !"memory".equals(store)) {
            throw new IllegalStateException("olo.security.store must be redis or memory, got: " + store);
        }
        for (BootstrapMembership m : bootstrapMemberships) {
            if (isBlank(m.issuer()) || isBlank(m.subject()) || m.tenants().isEmpty()) {
                throw new IllegalStateException("olo.security.bootstrap-memberships entries need issuer, subject and tenants");
            }
            for (String tenant : m.tenants()) {
                if (tenant == null || !TENANT_ID.matcher(tenant).matches()) {
                    throw new IllegalStateException("olo.security.bootstrap-memberships: invalid tenant id " + tenant);
                }
            }
        }
    }

    private static String blankToDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
