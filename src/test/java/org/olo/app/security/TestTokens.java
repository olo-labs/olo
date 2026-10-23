/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * Signs test JWTs with an in-memory RSA key, and builds a decoder that trusts only that key plus OLO's validators.
 */
public final class TestTokens {

    public static final String ISSUER = "https://idp.test/realms/olo";
    public static final String AUDIENCE = "olo-api";

    private static final KeyPair KEYS = generate();
    private static final KeyPair OTHER_KEYS = generate();

    private TestTokens() {
    }

    public static OloSecurityProperties properties() {
        return new OloSecurityProperties(ISSUER, "http://localhost:1/jwks", AUDIENCE, null, Duration.ofSeconds(30),
                null, null, null, null, "memory", null, List.of());
    }

    public static JwtDecoder decoder(OloSecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
        decoder.setJwtValidator(OloJwtDecoders.validator(properties));
        return decoder;
    }

    /** A valid token for {@code subject} with session {@code sid}; {@code customize} may change any claim. */
    public static String token(String subject, String sid, Consumer<JWTClaimsSet.Builder> customize) {
        return sign(claims(subject, sid, customize), KEYS);
    }

    public static String token(String subject, String sid) {
        return token(subject, sid, b -> { });
    }

    public static String adminToken(String subject, String sid) {
        return token(subject, sid, b -> b.claim("roles", List.of("olo-admin")));
    }

    /** Correct claims, but signed by a key the decoder doesn't trust. */
    public static String tokenSignedByOtherKey(String subject, String sid) {
        return sign(claims(subject, sid, b -> { }), OTHER_KEYS);
    }

    /** Correct claims, but HMAC-signed: must be refused (no symmetric algorithms). */
    public static String hmacToken(String subject, String sid) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims(subject, sid, b -> { }));
            jwt.sign(new MACSigner("0123456789abcdef0123456789abcdef".getBytes()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Correct claims, unsigned ({@code alg: none}): must be refused. */
    public static String unsignedToken(String subject, String sid) {
        return new PlainJWT(claims(subject, sid, b -> { })).serialize();
    }

    private static JWTClaimsSet claims(String subject, String sid, Consumer<JWTClaimsSet.Builder> customize) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(subject)
                .audience(AUDIENCE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("sid", sid);
        customize.accept(builder);
        return builder.build();
    }

    private static String sign(JWTClaimsSet claims, KeyPair keys) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner((RSAPrivateKey) keys.getPrivate()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
