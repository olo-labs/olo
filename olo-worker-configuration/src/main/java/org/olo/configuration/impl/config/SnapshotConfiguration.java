/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.impl.config;

import org.olo.configuration.Configuration;
import org.olo.configuration.snapshot.ConfigurationSnapshot;

import java.util.Map;

/**
 * Configuration view over a {@link ConfigurationSnapshot}. Global only, or global + tenant overrides.
 * Use {@link Configuration#forTenant(String)} for merge(global, tenantOverrides) without handling overrides manually.
 */
public final class SnapshotConfiguration implements Configuration {

  private final ConfigurationSnapshot snapshot;
  private final Map<String, String> effective;

  /** View over global config only (global + region merged; no tenant/resource). */
  public static Configuration global(ConfigurationSnapshot snapshot) {
    if (snapshot == null) return new SnapshotConfiguration(Map.of());
    return new SnapshotConfiguration(snapshot, null, null);
  }

  /** View over global + tenant overrides. */
  public static Configuration forTenant(ConfigurationSnapshot snapshot, String tenantId) {
    return forContext(snapshot, tenantId, null);
  }

  /** View over global → region → tenant → resource (merged). */
  public static Configuration forContext(ConfigurationSnapshot snapshot, String tenantId, String resourceId) {
    if (snapshot == null) return new SnapshotConfiguration(Map.of());
    return new SnapshotConfiguration(snapshot, tenantId, resourceId);
  }

  private SnapshotConfiguration(ConfigurationSnapshot snapshot, String tenantId, String resourceId) {
    this.snapshot = snapshot;
    this.effective = snapshot == null ? Map.of() : Map.copyOf(snapshot.getEffectiveConfig(tenantId, resourceId));
  }

  private SnapshotConfiguration(Map<String, String> effective) {
    this.snapshot = null;
    this.effective = effective == null || effective.isEmpty() ? Map.of() : Map.copyOf(effective);
  }

  @Override
  public Configuration forTenant(String tenantId) {
    return forContext(tenantId, null);
  }

  @Override
  public Configuration forContext(String tenantId, String resourceId) {
    if (snapshot != null) {
      return new SnapshotConfiguration(snapshot, tenantId, resourceId);
    }
    return this;
  }

  @Override
  public Map<String, String> asMap() {
    return effective;
  }

  @Override
  public String get(String key) {
    return key == null ? null : effective.get(key);
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
}
