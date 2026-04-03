/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.region;

import org.olo.configuration.Configuration;
import org.olo.configuration.Regions;
import org.olo.configuration.port.CacheConnectionSettings;
import org.olo.configuration.port.ConfigurationPortRegistry;
import org.olo.configuration.port.DbConnectionSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves tenant ID to region (in-memory map populated at bootstrap).
 * Call {@link #loadFrom(Configuration)} once at bootstrap (loader only; touches Redis/DB).
 * At runtime use only {@link #getRegion(String)} — never call loadFrom during execution.
 */
public final class TenantRegionResolver {

  private static final Logger log = LoggerFactory.getLogger(TenantRegionResolver.class);

  private static volatile Map<String, String> tenantToRegion = new ConcurrentHashMap<>();

  private TenantRegionResolver() {}

  /**
   * Loads tenant→region mapping from Redis (key {@value TenantRegionCache#CACHE_KEY}) or, on miss, from DB and caches to Redis.
   * Uses db.* and cache.* / redis.uri from the given config. Call once at bootstrap.
   */
  public static void loadFrom(Configuration config) {
    if (config == null) {
      return;
    }
    String redisUri = buildRedisUri(config);
    String jdbcUrl = config.get("olo.db.url", "").trim();
    if (jdbcUrl.isEmpty()) {
      jdbcUrl = buildJdbcUrl(config);
    }
    if (redisUri.isEmpty() && jdbcUrl.isEmpty()) {
      return;
    }

    Map<String, String> map = Map.of();

    if (!redisUri.isEmpty()) {
      try {
        var factory = ConfigurationPortRegistry.tenantRegionCacheFactory();
        if (factory == null) {
          log.warn("Tenant region cache factory not registered; skipping Redis tenant-region cache load");
          map = !jdbcUrl.isEmpty() ? loadFromDbOnly(jdbcUrl, config) : Map.of();
        } else {
          TenantRegionCache cache = factory.create(new CacheConnectionSettings(redisUri));
          if (cache == null) {
            map = !jdbcUrl.isEmpty() ? loadFromDbOnly(jdbcUrl, config) : Map.of();
          } else {
            map = cache.getAll();
            if (map.isEmpty() && !jdbcUrl.isEmpty()) {
              map = loadFromDbAndCache(jdbcUrl, config, cache);
            }
            closeIfNeeded(cache);
          }
        }
      } catch (Exception e) {
        log.warn("Tenant region Redis load failed: {}", e.getMessage());
        if (!jdbcUrl.isEmpty()) {
          map = loadFromDbOnly(jdbcUrl, config);
        }
      }
    } else if (!jdbcUrl.isEmpty()) {
      map = loadFromDbOnly(jdbcUrl, config);
    }

    if (!map.isEmpty()) {
      tenantToRegion = new ConcurrentHashMap<>(map);
      log.info("Loaded {} tenant→region entries", map.size());
    }
  }

  /**
   * Returns the region for the given tenant ID, or {@link Regions#DEFAULT_REGION} if unknown.
   * Ensure {@link #loadFrom(Configuration)} has been called first (e.g. at bootstrap).
   */
  public static String getRegion(String tenantId) {
    if (tenantId == null || tenantId.isEmpty()) {
      return Regions.DEFAULT_REGION;
    }
    return tenantToRegion.getOrDefault(tenantId, Regions.DEFAULT_REGION);
  }

  /**
   * Returns the current in-memory tenant→region map (read-only). May be empty if loadFrom was not called or DB/Redis had no data.
   */
  public static Map<String, String> getTenantToRegionMap() {
    return Collections.unmodifiableMap(tenantToRegion);
  }

  private static Map<String, String> loadFromDbAndCache(String jdbcUrl, Configuration config, TenantRegionCache cache) {
    Map<String, String> map = loadFromDbOnly(jdbcUrl, config);
    if (!map.isEmpty()) {
      cache.putAll(map);
    }
    return map;
  }

  private static Map<String, String> loadFromDbOnly(String jdbcUrl, Configuration config) {
    if (jdbcUrl == null || jdbcUrl.isBlank()) {
      return Map.of();
    }
    var factory = ConfigurationPortRegistry.tenantRegionRepositoryFactory();
    if (factory == null) {
      log.warn("Tenant region DB factory not registered; skipping DB tenant-region load");
      return Map.of();
    }
    try {
      DbConnectionSettings db = new DbConnectionSettings(
          jdbcUrl,
          config.get("olo.db.username", config.get("olo.db.user", "")).trim(),
          config.get("olo.db.password", ""),
          config.getInteger("olo.db.pool.size", 5));
      TenantRegionRepository repo = factory.create(db);
      if (repo == null) return Map.of();
      return repo.findAll();
    } catch (Exception e) {
      log.warn("Tenant region DB load failed: {}", e.getMessage());
      return Map.of();
    }
  }

  private static String buildRedisUri(Configuration c) {
    String uri = c.get("olo.redis.uri", "").trim();
    if (!uri.isEmpty()) {
      return uri;
    }
    String host = c.get("olo.redis.host", "").trim();
    if (host.isEmpty()) {
      return "";
    }
    int port = c.getInteger("olo.redis.port", 6379);
    String password = c.get("olo.redis.password", "").trim();
    if (password.isEmpty()) {
      return "redis://" + host + ":" + port;
    }
    return "redis://:" + password + "@" + host + ":" + port;
  }

  private static String buildJdbcUrl(Configuration c) {
    String host = c.get("olo.db.host", "").trim();
    if (host.isEmpty()) {
      return "";
    }
    int port = c.getInteger("olo.db.port", 5432);
    String name = c.get("olo.db.name", "olo").trim();
    return "jdbc:postgresql://" + host + ":" + port + "/" + name;
  }

  private static void closeIfNeeded(Object maybeCloseable) {
    if (maybeCloseable instanceof AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Exception ignore) {
        // best-effort close
      }
    }
  }
}
