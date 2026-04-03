/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.snapshot;

import java.util.Map;

/**
 * Sectioned configuration with clear separation of three parts:
 * <pre>
 * CompositeConfigurationSnapshot
 *  ├─ CoreConfigurationSnapshot (ConfigurationSnapshot) — core config, rarely changes
 *  ├─ PipelineRegistry — pipelines, frequent updates
 *  └─ ConnectionRegistry — connections, medium frequency
 * </pre>
 * <p><strong>Fully immutable:</strong> This type must be fully immutable once published. Section maps (pipelines,
 * connections) must be {@link java.util.Collections#unmodifiableMap unmodifiable} or otherwise immutable
 * (e.g. {@code Map.of()}). Otherwise accidental mutation is possible and can cause inconsistent state across
 * workers. The loader wraps all section maps in {@code Collections.unmodifiableMap(...)} before setting.
 * </p>
 * <p><strong>Immutable replacement:</strong> The loader always builds a <em>new</em> composite, populates it
 * (reusing unchanged section references when possible), then publishes it in <em>one</em> volatile write.
 * Never mutate or replace sections individually on a composite that is already published; workers must never see
 * mixed sections (e.g. new pipelines + old core).
 * </p>
 * <p><strong>Snapshot identity:</strong> {@link #getSnapshotId()} returns {@code region:regionalSettingsVersion}
 * for logging and debugging (e.g. "Replacing snapshot us-east:7 → us-east:8").
 * </p>
 */
public final class CompositeConfigurationSnapshot {

  public static final String SECTION_CORE = "core";
  public static final String SECTION_PIPELINES = "pipelines";
  public static final String SECTION_CONNECTIONS = "connections";
  /** When this version changes, worker must do full snapshot reload (not per-section). */
  public static final String SECTION_REGIONAL_SETTINGS = "regionalSettings";

  private final String region;

  /** Core config (also referred to as CoreConfigurationSnapshot; type is ConfigurationSnapshot). */
  private volatile ConfigurationSnapshot core;
  private volatile Map<String, Object> pipelines;
  private volatile Map<String, Object> connections;

  private volatile long coreVersion;
  private volatile long pipelinesVersion;
  private volatile long connectionsVersion;
  /** Global region version; if meta.regionalSettings.version != this → full reload. */
  private volatile long regionalSettingsVersion;

  public CompositeConfigurationSnapshot(String region) {
    this.region = region == null || region.isEmpty() ? "default" : region;
    this.core = null;
    this.pipelines = Map.of();
    this.connections = Map.of();
  }

  public String getRegion() {
    return region;
  }

  public ConfigurationSnapshot getCore() {
    return core;
  }

  /** Returns the pipeline registry (id → config). */
  public PipelineRegistry getPipelines() {
    return PipelineRegistry.of(pipelines);
  }

  /** Returns the connection registry (id → config). */
  public ConnectionRegistry getConnections() {
    return ConnectionRegistry.of(connections);
  }

  public long getCoreVersion() {
    return coreVersion;
  }

  public long getPipelinesVersion() {
    return pipelinesVersion;
  }

  public long getConnectionsVersion() {
    return connectionsVersion;
  }

  public long getRegionalSettingsVersion() {
    return regionalSettingsVersion;
  }

  /**
   * Single snapshot identity for workers: {@code region:regionalSettingsVersion} (e.g. {@code us-east:7}).
   * Workers expose this one identity for debugging; per-section versions (core, pipelines, connections) are internal.
   * Log on load: "Loaded configuration snapshot region=us-east snapshot=us-east:7".
   * Log on refresh: "Replacing snapshot us-east:7 → us-east:8".
   */
  public String getSnapshotId() {
    return region + ":" + regionalSettingsVersion;
  }

  /**
   * Returns the backing pipelines map for reuse during refresh when version unchanged (reduces GC).
   * Do not modify the returned map.
   */
  public Map<String, Object> getPipelinesForReuse() {
    return pipelines;
  }

  /**
   * Returns the backing connections map for reuse during refresh when version unchanged (reduces GC).
   * Do not modify the returned map.
   */
  public Map<String, Object> getConnectionsForReuse() {
    return connections;
  }

  /** Sets core and version. For loader use only when building a new composite before publish; never call on a published snapshot. */
  public void setCore(ConfigurationSnapshot core, long version) {
    this.core = core;
    this.coreVersion = version;
  }

  /** Sets pipelines and version. For loader use only when building a new composite before publish. */
  public void setPipelines(Map<String, Object> pipelines, long version) {
    this.pipelines = pipelines == null || pipelines.isEmpty() ? Map.of() : Map.copyOf(pipelines);
    this.pipelinesVersion = version;
  }

  /** Sets connections and version. For loader use only when building a new composite before publish. */
  public void setConnections(Map<String, Object> connections, long version) {
    this.connections = connections == null || connections.isEmpty() ? Map.of() : Map.copyOf(connections);
    this.connectionsVersion = version;
  }

  /** Sets the regional settings version. For loader use only when building a new composite before publish. */
  public void setRegionalSettingsVersion(long regionalSettingsVersion) {
    this.regionalSettingsVersion = regionalSettingsVersion;
  }
}
