/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.cache.factory;

import org.olo.configuration.port.CacheConnectionSettings;
import org.olo.configuration.port.ConfigurationSnapshotStoreFactory;
import org.olo.configuration.snapshot.ConfigurationSnapshotStore;
import org.olo.worker.cache.impl.snapshot.RedisConfigurationSnapshotStore;
import io.lettuce.core.RedisClient;

/**
 * Cache factory for Redis snapshot store.
 */
public final class RedisConfigurationSnapshotStoreFactory implements ConfigurationSnapshotStoreFactory {

  @Override
  public ConfigurationSnapshotStore create(CacheConnectionSettings cacheSettings) {
    if (cacheSettings == null || !cacheSettings.isConfigured()) return null;
    RedisClient client = RedisClient.create(cacheSettings.redisUri());
    return new RedisConfigurationSnapshotStore(client);
  }
}
