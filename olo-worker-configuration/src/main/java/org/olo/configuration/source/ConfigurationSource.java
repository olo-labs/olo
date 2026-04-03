/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.source;

import java.util.Map;

/**
 * Plugin for loading configuration. Each source returns a map of key-value pairs.
 * The loader runs sources in order and merges: {@code config.putAll(source.load(config));}
 * Later sources override earlier ones. Adding a new config source is trivial.
 */
public interface ConfigurationSource {

  /**
   * Loads configuration entries. {@code current} is the merged map from all sources run so far
   * (so a source can read e.g. db.url or redis.uri from it).
   *
   * @param current current merged configuration (may be empty for the first source)
   * @return map of config key → value; never null (use empty map if nothing to add)
   */
  Map<String, String> load(Map<String, String> current);
}
