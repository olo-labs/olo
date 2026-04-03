/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.impl.config;

import org.olo.configuration.Configuration;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory configuration backed by a map. Thread-safe for reads.
 */
public final class DefaultConfiguration implements Configuration {

  private final Map<String, String> map;

  public DefaultConfiguration(Map<String, String> source) {
    this.map = source == null ? Map.of() : new ConcurrentHashMap<>(source);
  }

  @Override
  public String get(String key) {
    return key == null ? null : map.get(key);
  }

  @Override
  public String get(String key, String defaultValue) {
    String v = get(key);
    return (v == null || v.isBlank()) ? defaultValue : v;
  }

  @Override
  public Integer getInteger(String key) {
    String v = get(key);
    if (v == null || v.isBlank()) return null;
    try {
      return Integer.parseInt(v.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public int getInteger(String key, int defaultValue) {
    Integer v = getInteger(key);
    return v == null ? defaultValue : v;
  }

  @Override
  public Long getLong(String key) {
    String v = get(key);
    if (v == null || v.isBlank()) return null;
    try {
      return Long.parseLong(v.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public long getLong(String key, long defaultValue) {
    Long v = getLong(key);
    return v == null ? defaultValue : v;
  }

  @Override
  public boolean getBoolean(String key) {
    return getBoolean(key, false);
  }

  @Override
  public boolean getBoolean(String key, boolean defaultValue) {
    String v = get(key);
    if (v == null || v.isBlank()) return defaultValue;
    return "true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim()) || "yes".equalsIgnoreCase(v.trim());
  }

  public Map<String, String> asMap() {
    return Collections.unmodifiableMap(map);
  }

  @Override
  public Configuration forTenant(String tenantId) {
    return this;
  }

  @Override
  public Configuration forContext(String tenantId, String resourceId) {
    return this;
  }
}
