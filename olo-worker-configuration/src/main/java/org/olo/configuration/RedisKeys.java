/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration;

/**
 * Centralized Redis key root for configuration/cache. The root prefix is configurable via:
 * <ul>
 *   <li>Property: {@code olo.cache.root-key}</li>
 *   <li>Env override: {@code OLO_CACHE_ROOT_KEY}</li>
 * </ul>
 *
 * Default root is {@code "olo"} so keys look like {@code olo:config:*}, {@code olo:worker:*}, etc.
 */
public final class RedisKeys {

  public static final String ROOT_KEY_PROP = "olo.cache.root-key";
  public static final String ROOT_KEY_ENV = "OLO_CACHE_ROOT_KEY";
  private static final String DEFAULT_ROOT = "olo";

  private RedisKeys() {}

  /** Returns the configured Redis root key (e.g. "olo"). */
  public static String root() {
    String fromEnv = System.getenv(ROOT_KEY_ENV);
    if (fromEnv != null && !fromEnv.isBlank()) {
      return fromEnv.trim();
    }
    String fromSys = System.getProperty(ROOT_KEY_PROP);
    if (fromSys != null && !fromSys.isBlank()) {
      return fromSys.trim();
    }
    Configuration c = ConfigurationProvider.get();
    if (c != null) {
      String v = c.get(ROOT_KEY_PROP, DEFAULT_ROOT);
      if (v != null && !v.isBlank()) {
        return v.trim();
      }
    }
    return DEFAULT_ROOT;
  }

  /** Returns the config namespace prefix, e.g. "olo:config". */
  public static String configPrefix() {
    return root() + ":config";
  }

  /** Returns the worker namespace prefix, e.g. "olo:worker". */
  public static String workerPrefix() {
    return root() + ":worker";
  }
}

