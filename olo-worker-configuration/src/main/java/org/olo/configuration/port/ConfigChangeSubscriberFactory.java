/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.port;

import java.util.Set;

/**
 * Creates an event subscriber for config-change notifications.
 */
public interface ConfigChangeSubscriberFactory {

  ConfigChangeSubscriber create(
      CacheConnectionSettings cacheSettings,
      String channelPrefix,
      Set<String> servedRegions,
      Runnable onConfigChanged);
}
