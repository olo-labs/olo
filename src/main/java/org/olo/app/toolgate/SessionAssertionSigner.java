// Copyright 2026 OLO Labs
// SPDX-License-Identifier: Apache-2.0
package org.olo.app.toolgate;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Signs OLO session assertions: RS256 with the exact header ToolGate accepts,
 * {@code {"alg":"RS256","typ":"olo-session-assertion+jwt","kid":...}}, and claims {@code iss, aud, sub, iat, exp}.
 * The subject is the stable OLO user id, never an email or a display name.
 */
public final class SessionAssertionSigner {

    public static final String TYPE = "olo-session-assertion+jwt";

    private final RSAPrivateCrtKey key;
    private final String header;
    private final String issuer;
    private final String audience;
    private final Duration lifetime;
    private final ObjectMapper json;

    public SessionAssertionSigner(RSAPrivateCrtKey key, String keyId, String issuer, String audience, Duration lifetime,
                                  ObjectMapper json) {
        this.key = Objects.requireNonNull(key, "key");
        if (key.getModulus().bitLength() < 2048) {
            throw new IllegalArgumentException("session assertion key must be at least 2048 bits");
        }
        this.header = encode("{\"alg\":\"RS256\",\"typ\":\"" + TYPE + "\",\"kid\":\"" + keyId + "\"}");
        this.issuer = issuer;
        this.audience = audience;
        this.lifetime = lifetime;
        if (lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(Duration.ofSeconds(15)) > 0) {
            throw new IllegalArgumentException("assertion lifetime must be 1-15 seconds");
        }
        this.json = json;
    }

    /** Parses a PKCS#8 PEM RSA private key. */
    public static RSAPrivateCrtKey privateKey(String pem) throws GeneralSecurityException {
        String begin = "-----BEGIN " + "PRIVATE KEY-----";
        String end = "-----END " + "PRIVATE KEY-----";
        if (!pem.contains(begin)) {
            throw new GeneralSecurityException("PKCS#8 PEM private key required");
        }
        byte[] der = Base64.getMimeDecoder().decode(pem.substring(pem.indexOf(begin) + begin.length(), pem.indexOf(end)));
        return (RSAPrivateCrtKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    public String sign(String tenantId, String userId, Instant now, Instant authenticatedUntil) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", issuer);
        claims.put("aud", audience);
        claims.put("sub", userId);
        claims.put("tid", tenantId);
        claims.put("iat", now.getEpochSecond());
        Instant expiry = now.plus(lifetime).isBefore(authenticatedUntil) ? now.plus(lifetime) : authenticatedUntil;
        if (expiry.getEpochSecond() <= now.getEpochSecond()) throw new IllegalArgumentException("session has expired");
        claims.put("exp", expiry.getEpochSecond());
        try {
            String input = header + "." + encode(json.writeValueAsString(claims));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key);
            signature.update(input.getBytes(StandardCharsets.US_ASCII));
            return input + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
        } catch (GeneralSecurityException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("session assertion signing failed", e);
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
