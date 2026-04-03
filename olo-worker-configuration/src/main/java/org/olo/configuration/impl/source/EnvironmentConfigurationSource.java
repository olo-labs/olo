/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.impl.source;

import org.olo.configuration.source.ConfigurationSource;

import org.olo.configuration.ConfigurationConstants;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads OLO_* environment variables. Key mapping: OLO_DB_HOST → olo.db.host (prefix "olo." + lowercase, underscore → dot).
 */
public final class EnvironmentConfigurationSource implements ConfigurationSource {

  private final String envPrefix;

  /** Uses prefix {@link ConfigurationConstants#ENV_PREFIX}. */
  public EnvironmentConfigurationSource() {
    this(ConfigurationConstants.ENV_PREFIX);
  }

  public EnvironmentConfigurationSource(String envPrefix) {
    this.envPrefix = envPrefix != null ? envPrefix : "";
  }

  @Override
  public Map<String, String> load(Map<String, String> current) {
    Map<String, String> map = new HashMap<>();
    if (envPrefix.isEmpty()) return map;
    for (Map.Entry<String, String> e : System.getenv().entrySet()) {
      String key = e.getKey();
      if (key != null && key.startsWith(envPrefix)) {
        String configKey = envKeyToConfigKey(key.substring(envPrefix.length()));
        if (configKey != null) map.put(configKey, e.getValue());
      }
    }
    return map;
  }

  /** Converts env key to config key: "olo." + lowercase, underscore → dot (e.g. DB_HOST → olo.db.host). */
  public static String envKeyToConfigKey(String envKey) {
    if (envKey == null) return null;
    return "olo." + envKey.toLowerCase().replace('_', '.');
  }
}
