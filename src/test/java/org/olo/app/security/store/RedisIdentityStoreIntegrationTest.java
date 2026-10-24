/* Copyright (c) 2026 Olo Labs; SPDX-License-Identifier: Apache-2.0 */
package org.olo.app.security.store;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs only against an explicitly supplied isolated Redis; never flushes or deletes unrelated keys. */
@EnabledIfEnvironmentVariable(named = "OLO_TEST_REDIS_PORT", matches = "[0-9]+")
class RedisIdentityStoreIntegrationTest {
    private LettuceConnectionFactory connection;
    private StringRedisTemplate redis;
    private RedisIdentityStore store;
    private final String issuer = "https://test-" + UUID.randomUUID() + ".invalid";

    @BeforeEach void connect() {
        connection = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getenv("OLO_TEST_REDIS_PORT")));
        connection.afterPropertiesSet();
        redis = new StringRedisTemplate(connection);
        redis.afterPropertiesSet();
        store = new RedisIdentityStore(redis);
    }

    @AfterEach void disconnect() { connection.destroy(); }

    @Test void verifiedAuthenticationExpirySurvivesStoreRecreationAndNeverRevivesARevokedSession() {
        String user = store.userIdFor(issuer, "alice");
        Instant until = Instant.now().plusSeconds(100);
        store.session(issuer, "sid", user, until);
        assertThat(new RedisIdentityStore(redis).findSession(issuer, "sid").orElseThrow().authenticatedUntil())
                .isEqualTo(Instant.ofEpochMilli(until.toEpochMilli()));
        store.revokeSession(issuer, "sid");
        assertThat(store.session(issuer, "sid", user, until.plusSeconds(100)).revoked()).isTrue();
        assertThat(store.findSession(issuer, "sid").orElseThrow().authenticatedUntil())
                .isEqualTo(Instant.ofEpochMilli(until.toEpochMilli()));
    }

    @Test void concurrentRegistrationsHaveOneOwnerAndKeepTheLatestVerifiedExpiry() throws Exception {
        String user = store.userIdFor(issuer, "alice");
        Instant now = Instant.now();
        try (var pool = Executors.newFixedThreadPool(8)) {
            var results = new ArrayList<java.util.concurrent.Future<IdentityStore.SessionState>>();
            for (int i = 1; i <= 32; i++) {
                Instant until = now.plusSeconds(i);
                results.add(pool.submit(() -> store.session(issuer, "sid", user, until)));
            }
            for (var result : results) assertThat(result.get()).isEqualTo(new IdentityStore.SessionState(1, false, false));
        }
        assertThat(store.findSession(issuer, "sid").orElseThrow().authenticatedUntil())
                .isEqualTo(Instant.ofEpochMilli(now.plusSeconds(32).toEpochMilli()));
        assertThat(store.session(issuer, "sid", "foreign", now.plusSeconds(100)).foreign()).isTrue();
    }

    @Test void revocationOutboxSurvivesRecreationAndOldAcknowledgementsCannotEraseNewEvents() {
        String user = store.userIdFor(issuer, "alice");
        store.enqueueRevocation(user, "event-1");
        var restarted = new RedisIdentityStore(redis);
        assertThat(restarted.pendingRevocations()).containsEntry(user, "event-1");
        restarted.enqueueRevocation(user, "event-2");
        store.acknowledgeRevocation(user, "event-1");
        assertThat(restarted.pendingRevocations()).containsEntry(user, "event-2");
        restarted.acknowledgeRevocation(user, "event-2");
        assertThat(restarted.pendingRevocations()).doesNotContainKey(user);
    }
}
