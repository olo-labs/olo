/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.cache.impl.region;

import org.olo.configuration.region.TenantRegionCache;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Redis-backed cache for tenant→region mapping.
 */
public final class RedisTenantRegionCache implements TenantRegionCache, AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(RedisTenantRegionCache.class);
  private final RedisClient redisClient;

  public RedisTenantRegionCache(RedisClient redisClient) {
    this.redisClient = redisClient;
  }

  @Override
  public Map<String, String> getAll() {
    try (StatefulRedisConnection<String, String> conn = redisClient.connect()) {
      RedisCommands<String, String> cmd = conn.sync();
      Map<String, String> map = cmd.hgetall(CACHE_KEY);
      return map == null ? Map.of() : new LinkedHashMap<>(map);
    } catch (Exception e) {
      log.warn("Failed to get tenant regions from Redis: {}", e.getMessage());
      return Map.of();
    }
  }

  @Override
  public void putAll(Map<String, String> tenantToRegion) {
    if (tenantToRegion == null || tenantToRegion.isEmpty()) return;
    try (StatefulRedisConnection<String, String> conn = redisClient.connect()) {
      conn.sync().hset(CACHE_KEY, tenantToRegion);
    } catch (Exception e) {
      log.warn("Failed to put tenant regions to Redis: {}", e.getMessage());
    }
  }

  @Override
  public String get(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) return null;
    try (StatefulRedisConnection<String, String> conn = redisClient.connect()) {
      return conn.sync().hget(CACHE_KEY, tenantId);
    } catch (Exception e) {
      log.warn("Failed to get tenant region from Redis: tenantId={} error={}", tenantId, e.getMessage());
      return null;
    }
  }

  @Override
  public void put(String tenantId, String region) {
    if (tenantId == null || tenantId.isBlank()) return;
    String r = (region == null || region.isBlank()) ? "default" : region;
    try (StatefulRedisConnection<String, String> conn = redisClient.connect()) {
      conn.sync().hset(CACHE_KEY, tenantId, r);
    } catch (Exception e) {
      log.warn("Failed to put tenant region to Redis: tenantId={} region={} error={}", tenantId, r, e.getMessage());
    }
  }

  @Override
  public void close() {
    try {
      redisClient.shutdown();
    } catch (Exception ignore) {
    }
  }
}
