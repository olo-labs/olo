/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.config;

import org.olo.executiontree.load.ConfigSource;
import org.olo.executiontree.load.ConfigSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Objects;
import java.util.Optional;

/**
 * Redis-backed {@link ConfigSource} and {@link ConfigSink} for pipeline configuration.
 * When config is loaded from file, the loader writes it to Redis via this sink so other containers can use it.
 * {@link #getFromDb} returns empty; {@link #putInDb} is a no-op (override or add DB later if needed).
 */
public final class RedisPipelineConfigSourceSink implements ConfigSource, ConfigSink {

    private static final Logger log = LoggerFactory.getLogger(RedisPipelineConfigSourceSink.class);

    private final JedisPool pool;

    public RedisPipelineConfigSourceSink(OloConfig config) {
        this(Objects.requireNonNull(config, "config").getCacheHost(), config.getCachePort());
    }

    public RedisPipelineConfigSourceSink(String cacheHost, int cachePort) {
        this.pool = new JedisPool(new JedisPoolConfig(), cacheHost, cachePort);
        log.debug("RedisPipelineConfigSourceSink connected to {}:{}", cacheHost, cachePort);
    }

    @Override
    public Optional<String> getFromCache(String key) {
        try (var jedis = pool.getResource()) {
            String value = jedis.get(key);
            return Optional.ofNullable(value);
        }
    }

    @Override
    public Optional<String> getFromDb(String queueName, String version) {
        return Optional.empty();
    }

    @Override
    public void putInCache(String key, String json) {
        try (var jedis = pool.getResource()) {
            jedis.set(key, json);
            log.debug("Wrote pipeline config to Redis key={}", key);
        }
    }

    @Override
    public void putInDb(String queueName, String version, String json) {
        // No-op unless DB persistence is added later
        log.trace("putInDb not implemented: queue={} version={}", queueName, version);
    }

    @Override
    public void putInDb(String tenantId, String queueName, String version, String json) {
        // No-op unless DB persistence with tenant_id is added later
        log.trace("putInDb not implemented: tenant={} queue={} version={}", tenantId, queueName, version);
    }
}
