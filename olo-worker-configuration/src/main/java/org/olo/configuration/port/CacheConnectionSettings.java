/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.port;

/**
 * Cache connection settings used by factory ports.
 */
public record CacheConnectionSettings(String redisUri) {

  public CacheConnectionSettings {
    redisUri = redisUri == null ? "" : redisUri.trim();
  }

  public boolean isConfigured() {
    return !redisUri.isEmpty();
  }
}
