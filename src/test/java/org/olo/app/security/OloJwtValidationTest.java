/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M3.1: only correctly signed tokens from the configured issuer, for the configured audience, still valid, are accepted.
 */
class OloJwtValidationTest {

    private final JwtDecoder decoder = TestTokens.decoder(TestTokens.properties());

    @Test
    void acceptsAValidToken() {
        Jwt jwt = decoder.decode(TestTokens.token("alice", "s1"));

        assertThat(jwt.getSubject()).isEqualTo("alice");
        assertThat(jwt.getClaimAsString("sid")).isEqualTo("s1");
    }

    @Test
    void refusesAnUntrustedSignature() {
        assertThatThrownBy(() -> decoder.decode(TestTokens.tokenSignedByOtherKey("alice", "s1")))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void refusesHmacAndUnsignedTokens() {
        assertThatThrownBy(() -> decoder.decode(TestTokens.hmacToken("alice", "s1"))).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(TestTokens.unsignedToken("alice", "s1"))).isInstanceOf(JwtException.class);
    }

    @Test
    void refusesAnotherIssuer() {
        assertThatThrownBy(() -> decoder.decode(TestTokens.token("alice", "s1", b -> b.issuer("https://evil.test"))))
                .isInstanceOf(JwtException.class).hasMessageContaining("iss");
    }

    @Test
    void refusesAnotherAudience() {
        assertThatThrownBy(() -> decoder.decode(TestTokens.token("alice", "s1", b -> b.audience(List.of("other-api")))))
                .isInstanceOf(JwtException.class).hasMessageContaining("audience");
    }

    @Test
    void refusesExpiredAndNotYetValidTokens() {
        Date past = Date.from(Instant.now().minusSeconds(120));
        Date future = Date.from(Instant.now().plusSeconds(120));

        assertThatThrownBy(() -> decoder.decode(TestTokens.token("alice", "s1", b -> b.expirationTime(past))))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(TestTokens.token("alice", "s1", b -> b.notBeforeTime(future))))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void refusesTokensWithoutExpiryOrSession() {
        assertThatThrownBy(() -> decoder.decode(TestTokens.token("alice", "s1", b -> b.expirationTime(null))))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(TestTokens.token("alice", null)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void startupFailsWithoutIssuerOrAudience() {
        OloSecurityProperties noIssuer = new OloSecurityProperties(null, null, "olo-api", null, null, null, null, null,
                null, "memory", null, null);
        OloSecurityProperties noAudience = new OloSecurityProperties(TestTokens.ISSUER, null, " ", null, null, null, null,
                null, null, "memory", null, null);

        assertThatThrownBy(noIssuer::validate).hasMessageContaining("olo.security.issuer");
        assertThatThrownBy(noAudience::validate).hasMessageContaining("olo.security.audience");
    }
}
