/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.impl.refresh;

import org.olo.configuration.ConfigurationProvider;
import org.olo.configuration.impl.snapshot.SnapshotChecksum;
import org.olo.configuration.snapshot.BlockMetadata;
import org.olo.configuration.snapshot.CompositeConfigurationSnapshot;
import org.olo.configuration.snapshot.ConfigurationSnapshot;
import org.olo.configuration.snapshot.ConfigurationSnapshotStore;
import org.olo.configuration.snapshot.SnapshotMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * Worker path: defaults → env → Redis sectioned config only. Workers never touch DB.
 * Redis layout (recommended): olo:config:meta, olo:config:core:&lt;region&gt;, olo:config:pipelines:&lt;region&gt;, olo:config:connections:&lt;region&gt;.
 * Legacy layout is still supported for reads: olo:configuration:*:&lt;region&gt;.
 */
public final class RedisSnapshotLoader {

  private static final Logger log = LoggerFactory.getLogger(RedisSnapshotLoader.class);

  /** Config key: max pipelines per region (0 = no check). When set and exceeded, a warning is logged. See docs/architecture/configuration/olo-worker-configuration.md. */
  public static final String CONFIG_SNAPSHOT_MAX_PIPELINES_KEY = "olo.config.snapshot.max.pipelines";
  /** Config key: max connections per region (0 = no check). When set and exceeded, a warning is logged. */
  public static final String CONFIG_SNAPSHOT_MAX_CONNECTIONS_KEY = "olo.config.snapshot.max.connections";
  /** Config key: max size in bytes for a single Redis value (core, pipelines, connections, meta). 0 = no check. Example: 8388608 (8MB) to protect Redis from very large values. */
  public static final String CONFIG_SNAPSHOT_MAX_REDIS_VALUE_BYTES_KEY = "olo.config.snapshot.max.redis.value.bytes";
  /** Config key: enable snapshot checksum validation. Env override: OLO_CONFIGURATION_CHECKSUM=true/false. */
  public static final String CONFIGURATION_CHECKSUM_KEY = "olo.configuration.checksum";

  private RedisSnapshotLoader() {}

  /**
   * Loads configuration for the region from sectioned Redis keys (core required; pipelines and connections optional).
   * Sets the single composite in ConfigurationProvider (for bootstrap phase: worker region only).
   * On miss or missing core this method returns null; the caller (Bootstrap) fails.
   */
  public static ConfigurationSnapshot load(String region, ConfigurationSnapshotStore store) {
    CompositeConfigurationSnapshot composite = loadComposite(region, store);
    if (composite != null) {
      SnapshotMetadata meta = store.getMeta(region);
      ConfigurationProvider.setComposite(composite);
      log.info("Loaded configuration snapshot region={} generation={} snapshot={}",
          region, meta != null ? meta.getGeneration() : 0L, composite.getSnapshotId());
      return composite.getCore();
    }
    log.warn("No config in Redis for region={} (missing meta or core); not setting snapshot (caller should fail)", region);
    return null;
  }

  /**
   * Loads the composite for the region from Redis without updating ConfigurationProvider.
   * Used to build the multi-region map (e.g. load all served regions).
   */
  public static CompositeConfigurationSnapshot loadComposite(String region, ConfigurationSnapshotStore store) {
    if (region == null || region.isEmpty()) {
      region = "default";
    }
    if (store == null) return null;
    SnapshotMetadata meta = store.getMeta(region);
    if (meta == null || meta.getBlocks().isEmpty()) return null;
    return loadSectioned(region, store, meta);
  }

  private static CompositeConfigurationSnapshot loadSectioned(String region, ConfigurationSnapshotStore store, SnapshotMetadata meta) {
    ConfigurationSnapshot core = store.getCore(region);
    if (core == null) return null;

    CompositeConfigurationSnapshot composite = new CompositeConfigurationSnapshot(region);
    BlockMetadata coreBlock = meta.getBlocks().get(CompositeConfigurationSnapshot.SECTION_CORE);
    long coreVer = coreBlock != null ? coreBlock.getVersion() : 0L;
    composite.setCore(core, coreVer);

    Map<String, Object> pipelines = store.getPipelines(region);
    if (pipelines != null) {
      BlockMetadata pipBlock = meta.getBlocks().get(CompositeConfigurationSnapshot.SECTION_PIPELINES);
      composite.setPipelines(pipelines, pipBlock != null ? pipBlock.getVersion() : 0L);
    }
    Map<String, Object> connections = store.getConnections(region);
    if (connections != null) {
      BlockMetadata connBlock = meta.getBlocks().get(CompositeConfigurationSnapshot.SECTION_CONNECTIONS);
      composite.setConnections(connections, connBlock != null ? connBlock.getVersion() : 0L);
    }
    BlockMetadata regionalBlock = meta.getBlocks().get(CompositeConfigurationSnapshot.SECTION_REGIONAL_SETTINGS);
    composite.setRegionalSettingsVersion(regionalBlock != null ? regionalBlock.getVersion() : 0L);
    if (!validateChecksum(region, meta, core, pipelines, connections, "startup")) {
      return null;
    }
    warnIfOverLimits(region, pipelines != null ? pipelines.size() : 0, connections != null ? connections.size() : 0);
    return composite;
  }

  /**
   * If config limits are set and pipeline/connection counts exceed them, logs a warning. Snapshot is still applied.
   */
  static void warnIfOverLimits(String region, int pipelineCount, int connectionCount) {
    org.olo.configuration.Configuration c = ConfigurationProvider.get();
    if (c == null) return;
    int maxPipelines = c.getInteger(CONFIG_SNAPSHOT_MAX_PIPELINES_KEY, 0);
    int maxConnections = c.getInteger(CONFIG_SNAPSHOT_MAX_CONNECTIONS_KEY, 0);
    if (maxPipelines > 0 && pipelineCount > maxPipelines) {
      log.warn("Snapshot size limit: region={} pipelines={} exceeds olo.config.snapshot.max.pipelines={}; may cause GC pressure or slow refresh (see docs)", region, pipelineCount, maxPipelines);
    }
    if (maxConnections > 0 && connectionCount > maxConnections) {
      log.warn("Snapshot size limit: region={} connections={} exceeds olo.config.snapshot.max.connections={}; may cause GC pressure or slow refresh (see docs)", region, connectionCount, maxConnections);
    }
  }

  /**
   * Returns true if any section has a newer version than the local composite (per-section comparison).
   */
  public static boolean isNewerVersionAvailable(
      CompositeConfigurationSnapshot composite,
      ConfigurationSnapshotStore store) {
    if (composite == null || store == null) return false;
    SnapshotMetadata meta = store.getMeta(composite.getRegion());
    if (meta == null || meta.getBlocks().isEmpty()) return false;
    BlockMetadata regionalBlock = meta.getBlocks().get(CompositeConfigurationSnapshot.SECTION_REGIONAL_SETTINGS);
    if (regionalBlock != null && regionalBlock.getVersion() != composite.getRegionalSettingsVersion()) return true;
    BlockMetadata coreBlock = meta.getBlocks().get(CompositeConfigurationSnapshot.SECTION_CORE);
    if (coreBlock != null && coreBlock.getVersion() != composite.getCoreVersion()) return true;
    BlockMetadata pipBlock = meta.getBlocks().get(CompositeConfigurationSnapshot.SECTION_PIPELINES);
    if (pipBlock != null && pipBlock.getVersion() != composite.getPipelinesVersion()) return true;
    BlockMetadata connBlock = meta.getBlocks().get(CompositeConfigurationSnapshot.SECTION_CONNECTIONS);
    if (connBlock != null && connBlock.getVersion() != composite.getConnectionsVersion()) return true;
    return false;
  }

  /**
   * Refresh: for each region in the snapshot map, GET meta; if any version changed, fetch and update that region.
   * If Redis is unreachable or any load fails, keep current snapshot for that region and retry next cycle.
   */
  public static void refreshIfNeeded(ConfigurationSnapshotStore store) {
    Map<String, CompositeConfigurationSnapshot> map = ConfigurationProvider.getSnapshotMap();
    if (map == null || map.isEmpty() || store == null) return;
    Set<String> regions = map.keySet();
    for (String region : regions) {
      refreshRegion(region, store);
    }
  }

  /**
   * Refresh a single region: GET meta; if newer, fetch and put composite for that region.
   */
  private static void refreshRegion(String region, ConfigurationSnapshotStore store) {
    try {
      SnapshotMetadata meta = store.getMeta(region);
      if (meta == null) {
        log.warn("Redis metadata unavailable for region={}, keeping current snapshot; will retry next cycle", region);
        return;
      }

      CompositeConfigurationSnapshot current = ConfigurationProvider.getComposite(region);
      if (current != null && !meta.getBlocks().isEmpty()) {
        if (!isNewerVersionAvailable(current, meta)) {
          return;
        }
        // Full snapshot reload only (no partial/section-wise reload). See docs: reload entire snapshot when version changed.
        CompositeConfigurationSnapshot fresh = fetchAndBuildFullComposite(region, store, meta, current);
        if (fresh == null) {
          log.error("Snapshot load failed for region={} (e.g. missing section or invalid data), keeping current snapshot; will retry next cycle", region);
          return;
        }
        if (!validateChecksum(region, meta, fresh.getCore(), fresh.getPipelinesForReuse(), fresh.getConnectionsForReuse(), "refresh")) {
          return;
        }
        warnIfOverLimits(region, fresh.getPipelines().size(), fresh.getConnections().size());
        ConfigurationProvider.putComposite(region, fresh);
        log.info("Refreshed snapshot region={} generation={} snapshot={}", region, meta.getGeneration(), fresh.getSnapshotId());
        return;
      }

      if (current == null && !meta.getBlocks().isEmpty()) {
        CompositeConfigurationSnapshot recovered = fetchAndBuildFullComposite(region, store, meta, null);
        if (recovered == null) {
          log.error("Snapshot load failed for region={} (recovery; e.g. missing section), keeping current snapshot; will retry next cycle", region);
          return;
        }
        if (!validateChecksum(region, meta, recovered.getCore(), recovered.getPipelinesForReuse(), recovered.getConnectionsForReuse(), "recovery")) {
          return;
        }
        warnIfOverLimits(region, recovered.getPipelines().size(), recovered.getConnections().size());
        ConfigurationProvider.putComposite(region, recovered);
        log.info("Loaded configuration snapshot region={} generation={} snapshot={} (recovery)",
            region, meta.getGeneration(), recovered.getSnapshotId());
      }
    } catch (Exception e) {
      log.warn("Redis unavailable during config refresh for region={}, keeping current snapshot: {}", region, e.getMessage());
    }
  }

  /** True if any section version in meta is different from current. */
  private static boolean isNewerVersionAvailable(CompositeConfigurationSnapshot current, SnapshotMetadata meta) {
    BlockMetadata regionalBlock = meta.getBlocks().get(CompositeConfigurationSnapshot.SECTION_REGIONAL_SETTINGS);
    if (regionalBlock != null && regionalBlock.getVersion() != current.getRegionalSettingsVersion()) return true;
    BlockMetadata coreBlock = meta.getBlocks().get(CompositeConfigurationSnapshot.SECTION_CORE);
    if (coreBlock != null && coreBlock.getVersion() != current.getCoreVersion()) return true;
    BlockMetadata pipBlock = meta.getBlocks().get(CompositeConfigurationSnapshot.SECTION_PIPELINES);
    if (pipBlock != null && pipBlock.getVersion() != current.getPipelinesVersion()) return true;
    BlockMetadata connBlock = meta.getBlocks().get(CompositeConfigurationSnapshot.SECTION_CONNECTIONS);
    if (connBlock != null && connBlock.getVersion() != current.getConnectionsVersion()) return true;
    return false;
  }

  private static CompositeConfigurationSnapshot fetchAndBuildFullComposite(
      String region, ConfigurationSnapshotStore store, SnapshotMetadata meta, CompositeConfigurationSnapshot current) {
    return loadSectioned(region, store, meta);
  }

  private static boolean validateChecksum(
      String region,
      SnapshotMetadata meta,
      ConfigurationSnapshot core,
      Map<String, Object> pipelines,
      Map<String, Object> connections,
      String phase) {
    if (meta == null || core == null) return true;
    org.olo.configuration.Configuration c = ConfigurationProvider.get();
    if (c == null || !c.getBoolean(CONFIGURATION_CHECKSUM_KEY, false)) return true;
    String expected = meta.getChecksum();
    if (expected == null || expected.isBlank()) {
      log.error("Checksum validation enabled but metadata checksum is missing: region={} phase={} generation={}",
          region, phase, meta.getGeneration());
      return false;
    }
    String actual = SnapshotChecksum.compute(core, pipelines, connections);
    if (actual == null) {
      log.error("Failed to compute snapshot checksum: region={} phase={} generation={}",
          region, phase, meta.getGeneration());
      return false;
    }
    if (!expected.equalsIgnoreCase(actual)) {
      log.error("Snapshot checksum mismatch: region={} phase={} generation={} expected={} actual={}",
          region, phase, meta.getGeneration(), shortChecksum(expected), shortChecksum(actual));
      return false;
    }
    return true;
  }

  private static String shortChecksum(String checksum) {
    if (checksum == null) return "";
    return checksum.length() <= 8 ? checksum : checksum.substring(0, 8);
  }
}
