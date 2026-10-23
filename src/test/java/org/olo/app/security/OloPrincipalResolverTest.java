/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.security;

import org.junit.jupiter.api.Test;
import org.olo.app.security.store.InMemoryIdentityStore;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M3.1–3.2: stable user ids, tenant only from membership (never {@code sub}), and session revocation.
 */
class OloPrincipalResolverTest {

    private final OloSecurityProperties properties = TestTokens.properties();
    private final JwtDecoder decoder = TestTokens.decoder(properties);
    private final InMemoryIdentityStore store = new InMemoryIdentityStore();
    private final OloPrincipalResolver resolver = new OloPrincipalResolver(store, properties);

    private Jwt jwt(String subject, String sid) {
        return decoder.decode(TestTokens.token(subject, sid));
    }

    private void member(String subject, String... tenants) {
        String userId = store.userIdFor(TestTokens.ISSUER, subject);
        for (String tenant : tenants) {
            store.addMembership(userId, tenant);
        }
    }

    @Test
    void userIdIsStableAcrossSessionsAndNotTheSubject() {
        member("alice", "t1");

        OloPrincipal first = resolver.resolve(jwt("alice", "s1"), null);
        OloPrincipal second = resolver.resolve(jwt("alice", "s2"), null);

        assertThat(first.userId()).isEqualTo(second.userId()).isNotEqualTo("alice");
        assertThat(first.sessionId()).isEqualTo("s1");
        assertThat(first.sessionEpoch()).isEqualTo(1);
    }

    @Test
    void tenantComesOnlyFromMembershipNeverFromSub() {
        // A token whose sub happens to look like a tenant id grants nothing.
        assertThatThrownBy(() -> resolver.resolve(jwt("t1", "s1"), null))
                .isInstanceOfSatisfying(OloIdentityException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.code()).isEqualTo("no_membership");
                });
    }

    @Test
    void tenantClaimInTheTokenIsIgnored() {
        member("alice", "t1");
        Jwt withTenantClaim = decoder.decode(TestTokens.token("alice", "s1", b -> b.claim("tenantId", "t2")));

        assertThat(resolver.resolve(withTenantClaim, null).tenantId()).isEqualTo("t1");
    }

    @Test
    void requestedTenantMustBeAMembership() {
        member("bob", "t1", "t2");

        assertThat(resolver.resolve(jwt("bob", "s1"), "t2").tenantId()).isEqualTo("t2");
        assertThatThrownBy(() -> resolver.resolve(jwt("bob", "s1"), "t3"))
                .isInstanceOfSatisfying(OloIdentityException.class, e -> assertThat(e.code()).isEqualTo("not_a_member"));
        assertThatThrownBy(() -> resolver.resolve(jwt("bob", "s1"), null))
                .isInstanceOfSatisfying(OloIdentityException.class, e -> assertThat(e.code()).isEqualTo("tenant_required"));
    }

    @Test
    void removedMembershipTakesEffectOnTheNextRequest() {
        member("alice", "t1");
        resolver.resolve(jwt("alice", "s1"), null);

        store.removeMembership(store.userIdFor(TestTokens.ISSUER, "alice"), "t1");

        assertThatThrownBy(() -> resolver.resolve(jwt("alice", "s1"), null)).isInstanceOf(OloIdentityException.class);
    }

    @Test
    void revokedSessionIsRefusedAndOtherSessionsStillWork() {
        member("alice", "t1");
        resolver.resolve(jwt("alice", "s1"), null);
        resolver.resolve(jwt("alice", "s2"), null);

        store.revokeSession(TestTokens.ISSUER, "s1");

        assertThatThrownBy(() -> resolver.resolve(jwt("alice", "s1"), null))
                .isInstanceOfSatisfying(OloIdentityException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.code()).isEqualTo("session_revoked");
                });
        assertThat(resolver.resolve(jwt("alice", "s2"), null).sessionId()).isEqualTo("s2");
    }

    @Test
    void revokeAllSessionsRefusesEveryKnownSession() {
        member("alice", "t1");
        resolver.resolve(jwt("alice", "s1"), null);
        resolver.resolve(jwt("alice", "s2"), null);

        assertThat(store.revokeAllSessions(store.userIdFor(TestTokens.ISSUER, "alice"))).isEqualTo(2);

        assertThatThrownBy(() -> resolver.resolve(jwt("alice", "s1"), null)).isInstanceOf(OloIdentityException.class);
        assertThatThrownBy(() -> resolver.resolve(jwt("alice", "s2"), null)).isInstanceOf(OloIdentityException.class);
    }

    @Test
    void aSessionIdCanNotBeReusedByAnotherUser() {
        member("alice", "t1");
        member("mallory", "t1");
        resolver.resolve(jwt("alice", "shared"), null);

        assertThatThrownBy(() -> resolver.resolve(jwt("mallory", "shared"), null))
                .isInstanceOfSatisfying(OloIdentityException.class, e -> assertThat(e.code()).isEqualTo("session_mismatch"));
    }

    @Test
    void adminRoleComesFromTheVerifiedRolesClaim() {
        member("root", "t1");
        Jwt admin = decoder.decode(TestTokens.adminToken("root", "s1"));
        Jwt notAdmin = decoder.decode(TestTokens.token("root", "s2", b -> b.claim("roles", List.of("viewer"))));

        assertThat(resolver.resolve(admin, null).admin()).isTrue();
        assertThat(resolver.resolve(notAdmin, null).admin()).isFalse();
    }
}
