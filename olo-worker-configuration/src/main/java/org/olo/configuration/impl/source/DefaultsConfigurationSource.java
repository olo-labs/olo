/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.impl.source;

import org.olo.configuration.ConfigurationConstants;
import org.olo.configuration.source.ConfigurationSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Loads olo-defaults.properties (classpath resource or provided stream).
 * Pipeline order: run first so env/redis/db can override.
 */
public final class DefaultsConfigurationSource implements ConfigurationSource {

  private static final Logger log = LoggerFactory.getLogger(DefaultsConfigurationSource.class);

  private final String resourceName;
  private final InputStream inputStream;

  /** Loads from classpath {@link ConfigurationConstants#DEFAULT_RESOURCE}. */
  public DefaultsConfigurationSource() {
    this(ConfigurationConstants.DEFAULT_RESOURCE);
  }

  /** Loads from the given classpath resource name. */
  public DefaultsConfigurationSource(String resourceName) {
    this.resourceName = resourceName;
    this.inputStream = null;
  }

  /** Loads from the given stream (consumed on first load). */
  public DefaultsConfigurationSource(InputStream inputStream) {
    this.resourceName = null;
    this.inputStream = inputStream;
  }

  @Override
  public Map<String, String> load(Map<String, String> current) {
    Map<String, String> map = new HashMap<>();
    if (inputStream != null) {
      loadFromStream(inputStream, map);
    } else if (resourceName != null && !resourceName.isEmpty()) {
      try (InputStream in = DefaultsConfigurationSource.class.getClassLoader().getResourceAsStream(resourceName)) {
        if (in != null) {
          loadFromStream(in, map);
        }
      } catch (Exception e) {
        log.warn("Could not load defaults from {}: {}", resourceName, e.getMessage());
      }
    }
    return map;
  }

  private static void loadFromStream(InputStream in, Map<String, String> map) {
    try {
      Properties props = new Properties();
      props.load(in);
      for (String name : props.stringPropertyNames()) {
        map.put(name, props.getProperty(name));
      }
    } catch (Exception e) {
      log.warn("Failed to load defaults from stream: {}", e.getMessage());
    }
  }
}
