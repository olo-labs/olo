/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration;

import java.util.Collections;
import java.util.Map;

/**
 * Read-only view of configuration loaded at bootstrap (defaults + ENV overrides).
 * Served from memory when needed. Never mutate: replace snapshot atomically on refresh.
 */
public interface Configuration {

  /**
   * Returns a snapshot of all entries (e.g. for refresh merge). Default: empty map.
   */
  default Map<String, String> asMap() {
    return Collections.emptyMap();
  }

  /**
   * Returns the value for the key, or null if missing.
   */
  String get(String key);

  /**
   * Returns the value for the key, or the given default if missing.
   */
  String get(String key, String defaultValue);

  /**
   * Returns the value as an integer, or null if missing or not a number.
   */
  Integer getInteger(String key);

  /**
   * Returns the value as an integer, or the default if missing or not a number.
   */
  int getInteger(String key, int defaultValue);

  /**
   * Returns the value as a long, or null if missing or not a number.
   */
  Long getLong(String key);

  /**
   * Returns the value as a long, or the default if missing or not a number.
   */
  long getLong(String key, long defaultValue);

  /**
   * Returns the value as boolean: "true"/"1"/"yes" (case-insensitive) → true; otherwise false.
   */
  boolean getBoolean(String key);

  /**
   * Returns the value as boolean, or the default if missing.
   */
  boolean getBoolean(String key, boolean defaultValue);

  /**
   * Returns a configuration view for the given tenant: global + tenant overrides (tenant wins).
   * Same as {@code forContext(tenantId, null)}.
   */
  Configuration forTenant(String tenantId);

  /**
   * Returns a configuration view for the given context: merged from global → region → tenant → resource.
   * Usage: {@code ConfigurationProvider.require().forContext(tenantId, resourceId)}.
   * For pipeline/model level use formalized resource IDs: e.g. {@code forContext("tenantA", "pipeline:chat")}, {@code "connection:openai"}, {@code "model:gpt4"}.
   */
  Configuration forContext(String tenantId, String resourceId);
}
