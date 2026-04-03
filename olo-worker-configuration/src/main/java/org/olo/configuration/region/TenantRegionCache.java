/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.region;

import java.util.Map;

/**
 * Cache for tenant ID → region mapping. Redis structure: hash {@value #CACHE_KEY}
 * (field = tenant ID, value = region). Use HGET for single lookup, HGETALL for full map, HSET to update.
 */
public interface TenantRegionCache {

  /** Redis hash key for tenant→region mapping. Uses {@code <root>:worker:tenant:region}. */
  String CACHE_KEY = org.olo.configuration.RedisKeys.workerPrefix() + ":tenant:region";

  /** Returns the full map (HGETALL). */
  Map<String, String> getAll();

  /** Writes the full map (HSET key, map). */
  void putAll(Map<String, String> tenantToRegion);

  /** Single lookup (HGET). Returns null if absent. */
  String get(String tenantId);

  /** Single update (HSET). Use after DB update so workers see the new region on next refresh. */
  void put(String tenantId, String region);
}
