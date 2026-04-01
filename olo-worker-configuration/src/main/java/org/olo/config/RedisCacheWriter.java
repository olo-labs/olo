/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.config;

import org.olo.input.producer.CacheWriter;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Objects;

/**
 * Redis-backed {@link CacheWriter} used by {@link OloSessionCache} to push
 * serialized workflow input to the session key during the initialize phase.
 */
final class RedisCacheWriter implements CacheWriter {

    private final JedisPool pool;

    RedisCacheWriter(String host, int port) {
        this(host, port, new JedisPoolConfig());
    }

    RedisCacheWriter(String host, int port, JedisPoolConfig poolConfig) {
        Objects.requireNonNull(host, "host");
        this.pool = new JedisPool(poolConfig, host, port);
    }

    @Override
    public void put(String key, String value) {
        try (var jedis = pool.getResource()) {
            jedis.set(key, value);
        }
    }

    /**
     * Gets the current value of a key (e.g. for active workflow count). Returns 0 if key is missing or not a number.
     */
    public long getLong(String key) {
        try (var jedis = pool.getResource()) {
            String v = jedis.get(key);
            if (v == null || v.isBlank()) return 0L;
            try {
                return Long.parseLong(v.trim());
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
    }

    /**
     * Increments the Redis key (e.g. for active workflow quota). Key is created with value 0 if missing before INCR.
     */
    public long incr(String key) {
        try (var jedis = pool.getResource()) {
            return jedis.incr(key);
        }
    }

    /**
     * Decrements the Redis key (e.g. for active workflow quota).
     */
    public long decr(String key) {
        try (var jedis = pool.getResource()) {
            return jedis.decr(key);
        }
    }
}
