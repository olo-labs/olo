/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.snapshot;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Immutable core configuration snapshot with multilayered scopes (global → region → tenant → resource).
 * In the sectioned model this is the <em>CoreConfigurationSnapshot</em> inside {@link CompositeConfigurationSnapshot}.
 * Merge order: global → region → tenant → resource (each layer overrides the previous).
 * Workers execute only with this; no DB/Redis during workflow execution.
 */
public final class ConfigurationSnapshot {

  private final String region;
  private final long version;
  private final Instant lastUpdated;
  private final Map<String, String> globalConfig;
  private final Map<String, String> regionConfig;
  private final Map<String, Map<String, String>> tenantConfig;
  private final Map<String, Map<String, String>> resourceConfig;

  /** Constructor with global + tenant only (backward compatible). */
  public ConfigurationSnapshot(
      String region,
      long version,
      Instant lastUpdated,
      Map<String, String> globalConfig,
      Map<String, Map<String, String>> tenantConfig) {
    this(region, version, lastUpdated, globalConfig, null, tenantConfig, null);
  }

  /** Full constructor: global, region, tenant, resource layers. */
  public ConfigurationSnapshot(
      String region,
      long version,
      Instant lastUpdated,
      Map<String, String> globalConfig,
      Map<String, String> regionConfig,
      Map<String, Map<String, String>> tenantConfig,
      Map<String, Map<String, String>> resourceConfig) {
    this.region = region == null ? "default" : region;
    this.version = version;
    this.lastUpdated = lastUpdated == null ? Instant.EPOCH : lastUpdated;
    this.globalConfig = globalConfig == null || globalConfig.isEmpty() ? Map.of() : Map.copyOf(globalConfig);
    this.regionConfig = regionConfig == null || regionConfig.isEmpty() ? Map.of() : Map.copyOf(regionConfig);
    this.tenantConfig = tenantConfig == null ? Map.of() : copyNested(tenantConfig);
    this.resourceConfig = resourceConfig == null ? Map.of() : copyNested(resourceConfig);
  }

  public String getRegion() {
    return region;
  }

  public long getVersion() {
    return version;
  }

  public Instant getLastUpdated() {
    return lastUpdated;
  }

  /** Global config (worker-level defaults). */
  public Map<String, String> getGlobalConfig() {
    return globalConfig;
  }

  /** Region-specific overrides for this snapshot's region. */
  public Map<String, String> getRegionConfig() {
    return regionConfig;
  }

  /** Per-tenant overrides: tenantId → (key → value). */
  public Map<String, Map<String, String>> getTenantConfig() {
    return tenantConfig;
  }

  /** Per-resource overrides: resourceId in type:name form (e.g. pipeline:chat, connection:openai, model:gpt4, plugin:search) → (key → value). */
  public Map<String, Map<String, String>> getResourceConfig() {
    return resourceConfig;
  }

  /** Effective config for a tenant: global + tenant overrides (tenant wins). */
  public Map<String, String> getEffectiveConfig(String tenantId) {
    return getEffectiveConfig(tenantId, null);
  }

  /**
   * Effective config merged from all layers: global → region → tenant → resource.
   * Later scopes override earlier. Use forContext(tenantId, resourceId) at runtime.
   */
  public Map<String, String> getEffectiveConfig(String tenantId, String resourceId) {
    Map<String, String> out = new java.util.HashMap<>(globalConfig);
    out.putAll(regionConfig);
    if (tenantId != null) {
      Map<String, String> tenant = tenantConfig.get(tenantId);
      if (tenant != null) out.putAll(tenant);
    }
    if (resourceId != null) {
      Map<String, String> resource = resourceConfig.get(resourceId);
      if (resource != null) out.putAll(resource);
    }
    return Map.copyOf(out);
  }

  /** Deep copy so inner maps are immutable (Map.copyOf does not allow null values). */
  private static Map<String, Map<String, String>> copyNested(Map<String, Map<String, String>> map) {
    if (map.isEmpty()) return Map.of();
    return map.entrySet().stream()
        .collect(Collectors.toUnmodifiableMap(
            Map.Entry::getKey,
            e -> e.getValue() == null || e.getValue().isEmpty() ? Map.of() : Map.copyOf(e.getValue())));
  }
}
