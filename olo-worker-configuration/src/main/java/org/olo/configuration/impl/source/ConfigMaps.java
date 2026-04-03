/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.impl.source;

import java.util.Map;

/** Helper to read values from a config map (for sources that need db.port, cache.port, etc.). */
final class ConfigMaps {

  private ConfigMaps() {}

  static String get(Map<String, String> m, String key, String defaultValue) {
    if (m == null) return defaultValue;
    String v = m.get(key);
    return v != null ? v.trim() : defaultValue;
  }

  static int getInt(Map<String, String> m, String key, int defaultValue) {
    String v = get(m, key, null);
    if (v == null || v.isEmpty()) return defaultValue;
    try {
      return Integer.parseInt(v);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
