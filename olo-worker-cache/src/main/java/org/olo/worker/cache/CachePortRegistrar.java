/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.cache;

import org.olo.configuration.port.ConfigurationPortRegistry;
import org.olo.worker.cache.factory.RedisConfigChangeSubscriberFactory;
import org.olo.worker.cache.factory.RedisConfigurationSnapshotStoreFactory;
import org.olo.worker.cache.factory.RedisTenantRegionCacheFactory;

/**
 * Registers cache factory implementations into configuration port registry.
 */
public final class CachePortRegistrar {

  private CachePortRegistrar() {}

  public static void registerDefaults() {
    ConfigurationPortRegistry.registerSnapshotStoreFactory(new RedisConfigurationSnapshotStoreFactory());
    ConfigurationPortRegistry.registerTenantRegionCacheFactory(new RedisTenantRegionCacheFactory());
    ConfigurationPortRegistry.registerConfigChangeSubscriberFactory(new RedisConfigChangeSubscriberFactory());
  }
}
