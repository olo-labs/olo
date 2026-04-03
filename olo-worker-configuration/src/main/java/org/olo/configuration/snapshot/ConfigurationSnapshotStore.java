/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.snapshot;

import java.util.Map;

/**
 * Distributed snapshot cache (e.g. Redis). Sectioned layout only.
 *
 * Implementations typically build keys using a configurable root + {@code ":config"} prefix, e.g.:
 * {@code &lt;root&gt;:config:meta}, {@code &lt;root&gt;:config:core}, {@code &lt;root&gt;:config:pipelines:&lt;region&gt;}, etc.
 */
public interface ConfigurationSnapshotStore {

  String NAMESPACE = "olo:config";

  /** Global meta key (all regions): {@value}. */
  String META_KEY = NAMESPACE + ":meta";
  /** Core config (rarely changes). Key: {@value}. */
  String CORE_KEY = NAMESPACE + ":core";
  /** Pipelines (frequent). Key: {@value #PIPELINES_KEY_PREFIX}&lt;region&gt; */
  String PIPELINES_KEY_PREFIX = NAMESPACE + ":pipelines:";
  /** Connections (medium). Key: {@value #CONNECTIONS_KEY_PREFIX}&lt;region&gt; */
  String CONNECTIONS_KEY_PREFIX = NAMESPACE + ":connections:";
  /** Resources (frequent-ish). Key: {@value #RESOURCES_KEY_PREFIX}&lt;region&gt; */
  String RESOURCES_KEY_PREFIX = NAMESPACE + ":resources:";
  /** Tenant overrides. Key: {@value #TENANT_OVERRIDES_KEY_PREFIX}&lt;tenantId&gt; */
  String TENANT_OVERRIDES_KEY_PREFIX = NAMESPACE + ":overrides:tenant:";

  /**
   * Returns the core snapshot for the region (same as {@link #getCore(String)} in sectioned layout). Null if missing.
   */
  ConfigurationSnapshot getSnapshot(String region);

  /**
   * Returns metadata only (lightweight check). Null if missing.
   */
  SnapshotMetadata getMeta(String region);

  /**
   * Stores the core snapshot and its metadata for the region (writes to core key + meta).
   */
  void put(String region, ConfigurationSnapshot snapshot);

  /**
   * Stores pipelines section for the region and updates snapshot metadata accordingly.
   * Implementations should write the pipelines key first, then update meta last.
   */
  default void putPipelines(String region, Map<String, Object> pipelines) {
    // Default no-op (non-Redis implementations may not support pipelines section).
  }

  /**
   * Returns core config snapshot for the region. Required for sectioned load.
   */
  default ConfigurationSnapshot getCore(String region) {
    return null;
  }

  /**
   * Returns pipelines map for the region (sectioned key). Default: null.
   */
  default Map<String, Object> getPipelines(String region) {
    return null;
  }

  /**
   * Returns connections map for the region (sectioned key). Default: null.
   */
  default Map<String, Object> getConnections(String region) {
    return null;
  }

  /**
   * Returns resources for the region (sectioned key: {@value #RESOURCES_KEY_PREFIX}&lt;region&gt;).
   * Default: null.
   */
  default Map<String, Object> getResources(String region) {
    return null;
  }

  /**
   * Returns tenant overrides (key: {@value #TENANT_OVERRIDES_KEY_PREFIX}&lt;tenantId&gt;).
   * Default: null.
   */
  default Map<String, Object> getTenantOverrides(String tenantId) {
    return null;
  }

  /**
   * Stores tenant overrides for the given tenant ID into the distributed cache.
   * Implementations should write to {@link #TENANT_OVERRIDES_KEY_PREFIX} + tenantId.
   * Default: no-op.
   */
  default void putTenantOverrides(String tenantId, Map<String, Object> overrides) {
    // Default no-op.
  }
}
