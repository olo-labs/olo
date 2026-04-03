/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.cache.factory;

import org.olo.configuration.port.CacheConnectionSettings;
import org.olo.configuration.port.TenantRegionCacheFactory;
import org.olo.configuration.region.TenantRegionCache;
import org.olo.worker.cache.impl.region.RedisTenantRegionCache;
import io.lettuce.core.RedisClient;

/**
 * Cache factory for tenant-region cache.
 */
public final class RedisTenantRegionCacheFactory implements TenantRegionCacheFactory {

  @Override
  public TenantRegionCache create(CacheConnectionSettings cacheSettings) {
    if (cacheSettings == null || !cacheSettings.isConfigured()) return null;
    return new RedisTenantRegionCache(RedisClient.create(cacheSettings.redisUri()));
  }
}
