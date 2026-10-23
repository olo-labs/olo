/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.security.callback;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * The worker → backend callback signature, version 1. It must match {@code org.olo.kernel.context.callback.CallbackSigner}
 * in olo-mono.
 *
 * <pre>
 * X-OLO-Callback-Key:       key id
 * X-OLO-Callback-Timestamp: unix seconds
 * X-OLO-Callback-Signature: v1=hex(HMAC-SHA256(key, "v1\n" + timestamp + "\n" + METHOD + "\n" + path + "\n" + hex(sha256(body))))
 * </pre>
 *
 * The path excludes the query string. Keys are configuration only and never travel in workflow input.
 */
public final class CallbackSignature {

    public static final String HEADER_KEY = "X-OLO-Callback-Key";
    public static final String HEADER_TIMESTAMP = "X-OLO-Callback-Timestamp";
    public static final String HEADER_SIGNATURE = "X-OLO-Callback-Signature";
    public static final String VERSION = "v1";

    private CallbackSignature() {
    }

    public static String sign(byte[] key, long timestamp, String method, String path, byte[] body) {
        try {
            String canonical = VERSION + "\n" + timestamp + "\n" + method.toUpperCase() + "\n" + path + "\n"
                    + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return VERSION + "=" + HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    /** Constant-time comparison of an expected and a presented signature. */
    public static boolean matches(String expected, String presented) {
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), presented.trim().getBytes(StandardCharsets.UTF_8));
    }
}
