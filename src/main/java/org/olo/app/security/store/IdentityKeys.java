/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.security.store;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Store keys derived from verified claims. Hashing keeps issuer URLs and subjects out of key names and gives a fixed
 * length; the newline separator can't appear in either value, so no two pairs collide.
 */
final class IdentityKeys {

    static final String PREFIX = "olo:sec:";

    private IdentityKeys() {
    }

    static String subjectKey(String issuer, String subject) {
        return PREFIX + "identity:" + sha256(issuer + "\n" + subject);
    }

    static String sessionKey(String issuer, String sessionId) {
        return PREFIX + "session:" + sha256(issuer + "\n" + sessionId);
    }

    static String userKey(String userId) {
        return PREFIX + "user:" + userId;
    }

    static String membershipKey(String userId) {
        return PREFIX + "members:" + userId;
    }

    static String userSessionsKey(String userId) {
        return PREFIX + "user-sessions:" + userId;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
