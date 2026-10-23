/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.security.callback;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code olo.worker-callback.*}: the keys workers sign run-event callbacks with.
 *
 * <p>List the current key, and during rotation also the previous one. With no keys, every callback is refused.
 *
 * @param keys         signing keys by id; each secret must be at least 32 bytes
 * @param maxSkew      accepted difference between the callback timestamp and the backend clock
 * @param maxBodyBytes largest accepted callback body
 */
@ConfigurationProperties(prefix = "olo.worker-callback")
public record WorkerCallbackProperties(List<Key> keys, Duration maxSkew, Integer maxBodyBytes) {

    public static final int MIN_SECRET_BYTES = 32;

    public WorkerCallbackProperties {
        keys = keys == null ? List.of() : keys.stream().filter(k -> k != null && !isBlank(k.id()) && !isBlank(k.secret())).toList();
        maxSkew = maxSkew == null ? Duration.ofMinutes(5) : maxSkew;
        maxBodyBytes = maxBodyBytes == null ? 1_048_576 : maxBodyBytes;
    }

    /** One signing key. */
    public record Key(String id, String secret) {
    }

    /** Key id → secret bytes. Throws if a configured secret is too short. */
    public Map<String, byte[]> keyMap() {
        Map<String, byte[]> map = new LinkedHashMap<>();
        for (Key key : keys) {
            byte[] secret = key.secret().getBytes(StandardCharsets.UTF_8);
            if (secret.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException("olo.worker-callback key '" + key.id() + "' must be at least "
                        + MIN_SECRET_BYTES + " bytes");
            }
            map.put(key.id().trim(), secret);
        }
        return map;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
