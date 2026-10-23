/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.security.callback;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CallbackSignatureTest {

    /**
     * Known-answer vector, computed independently with openssl. olo-mono's CallbackSignerTest (the worker side) asserts the
     * same value, so worker and backend provably agree.
     */
    @Test
    void matchesTheSharedReferenceVector() {
        String signature = CallbackSignature.sign("test-callback-secret-0123456789-abcdef".getBytes(StandardCharsets.UTF_8),
                1_700_000_000L, "POST", "/api/runs/run-1/events", "{\"sequenceNumber\":1}".getBytes(StandardCharsets.UTF_8));

        assertThat(signature).isEqualTo("v1=645247651af31e47409bdff025823954bea6b6940df1295f955d324e43416fe8");
    }

    @Test
    void comparisonIsExact() {
        assertThat(CallbackSignature.matches("v1=ab", "v1=ab")).isTrue();
        assertThat(CallbackSignature.matches("v1=ab", "v1=ac")).isFalse();
        assertThat(CallbackSignature.matches("v1=ab", null)).isFalse();
    }
}
