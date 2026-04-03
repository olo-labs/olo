/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.cache.factory;

import org.olo.configuration.port.CacheConnectionSettings;
import org.olo.configuration.port.ConfigChangeSubscriber;
import org.olo.configuration.port.ConfigChangeSubscriberFactory;
import org.olo.worker.cache.impl.refresh.RedisConfigChangeSubscriber;
import io.lettuce.core.RedisClient;

import java.util.Set;

/**
 * Cache factory for Redis config-change subscriber.
 */
public final class RedisConfigChangeSubscriberFactory implements ConfigChangeSubscriberFactory {

  @Override
  public ConfigChangeSubscriber create(
      CacheConnectionSettings cacheSettings,
      String channelPrefix,
      Set<String> servedRegions,
      Runnable onConfigChanged) {
    if (cacheSettings == null || !cacheSettings.isConfigured()) return null;
    return new RedisConfigChangeSubscriber(
        RedisClient.create(cacheSettings.redisUri()),
        channelPrefix,
        servedRegions,
        onConfigChanged);
  }
}
