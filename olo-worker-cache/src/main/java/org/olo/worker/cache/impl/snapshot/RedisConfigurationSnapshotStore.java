/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.cache.impl.snapshot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.olo.configuration.ConfigurationProvider;
import org.olo.configuration.impl.refresh.RedisSnapshotLoader;
import org.olo.configuration.impl.snapshot.CorePipelinesConnections;
import org.olo.configuration.impl.snapshot.SectionedBatch;
import org.olo.configuration.impl.snapshot.SnapshotChecksum;
import org.olo.configuration.snapshot.BlockMetadata;
import org.olo.configuration.snapshot.ConfigurationSnapshot;
import org.olo.configuration.snapshot.ConfigurationSnapshotStore;
import org.olo.configuration.snapshot.SnapshotMetadata;

import java.nio.charset.StandardCharsets;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Redis-backed snapshot store. Sectioned layout only:
 * Recommended layout:
 * olo:config:meta (global meta for all regions),
 * olo:config:core, olo:config:pipelines:&lt;region&gt;, olo:config:connections:&lt;region&gt;,
 * olo:config:queues:&lt;region&gt;, olo:config:profiles:&lt;region&gt;,
 * olo:config:resources:&lt;region&gt;, olo:config:overrides:tenant:&lt;tenantId&gt;.
 * <p>
 * Backward compatible read support for legacy keys:
 * olo:configuration:core:&lt;region&gt; etc.
 * </p>
 */
public final class RedisConfigurationSnapshotStore implements ConfigurationSnapshotStore, AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(RedisConfigurationSnapshotStore.class);
  private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

  private final RedisClient redisClient;
  private final StatefulRedisConnection<String, String> sharedConnection;

  /** Uses a new connection per operation. Prefer shared-connection constructor for workers. */
  public RedisConfigurationSnapshotStore(RedisClient redisClient) {
    this.redisClient = redisClient;
    this.sharedConnection = null;
  }

  /** Uses same connection for all operations (worker bootstrap path). */
  public RedisConfigurationSnapshotStore(StatefulRedisConnection<String, String> sharedConnection) {
    this.redisClient = null;
    this.sharedConnection = sharedConnection;
  }

  /** Runs action with a connection: shared if set, otherwise new connection (closed after use). */
  private void withConnection(Consumer<StatefulRedisConnection<String, String>> action) {
    if (sharedConnection != null) {
      action.accept(sharedConnection);
      return;
    }
    try (StatefulRedisConnection<String, String> conn = redisClient.connect()) {
      action.accept(conn);
    }
  }

  @Override
  public ConfigurationSnapshot getSnapshot(String region) {
    return getCore(region);
  }

  @Override
  public SnapshotMetadata getMeta(String region) {
    if (region == null || region.isEmpty()) return null;
    try {
      final String[] out = new String[1];
      withConnection(conn -> {
        RedisCommands<String, String> cmd = conn.sync();
        String globalMetaKey = org.olo.configuration.RedisKeys.configPrefix() + ":meta";
        out[0] = cmd.get(globalMetaKey);
      });
      String json = out[0];
      if (json == null || json.isBlank()) return null;
      if (isOverLimit(json, org.olo.configuration.RedisKeys.configPrefix() + ":meta")) return null;
      SnapshotMetadata m = metaFromJsonNewGlobal(json, region);
      return m != null ? m : metaFromJson(json);
    } catch (Exception e) {
      log.error("Failed to get meta from Redis for region={} (invalid or corrupted data); keeping current snapshot, will retry next cycle: {}", region, e.getMessage());
      return null;
    }
  }

  @Override
  public void put(String region, ConfigurationSnapshot snapshot) {
    if (region == null || region.isEmpty() || snapshot == null) return;
    String coreKey = org.olo.configuration.RedisKeys.configPrefix() + ":core";
    try {
      String coreJson = toJson(snapshot);
      // Preserve other section blocks (pipelines/connections) if they already exist so we don't wipe them
      // when updating core.
      SnapshotMetadata existingMeta = getMeta(region);
      Map<String, Object> pipelines = getPipelines(region);
      if (pipelines == null) pipelines = Map.of();
      Map<String, Object> connections = getConnections(region);
      if (connections == null) connections = Map.of();
      String existingGlobalMetaJson = getString(org.olo.configuration.RedisKeys.configPrefix() + ":meta");
      long pipelinesVer = getSectionVersion(existingMeta, META_BLOCK_PIPELINES);
      long connectionsVer = getSectionVersion(existingMeta, META_BLOCK_CONNECTIONS);
      long queuesVer = getSectionVersion(existingMeta, META_BLOCK_QUEUES);
      long profilesVer = getSectionVersion(existingMeta, META_BLOCK_PROFILES);
      String globalMetaJson = metaToJsonNewGlobalMerged(existingGlobalMetaJson, region,
          snapshot.getVersion(), pipelinesVer, connectionsVer, queuesVer, profilesVer, snapshot.getVersion());
      if (isOverLimit(coreJson, coreKey)
          || isOverLimit(globalMetaJson, org.olo.configuration.RedisKeys.configPrefix() + ":meta")) {
        log.warn("Skipping put to Redis: core or meta value exceeds olo.config.snapshot.max.redis.value.bytes; region={}", region);
        return;
      }

      withConnection(conn -> {
        RedisCommands<String, String> cmd = conn.sync();
        cmd.multi();
        try {
          cmd.set(coreKey, coreJson);
          // Global meta last (publish rule).
          cmd.set(org.olo.configuration.RedisKeys.configPrefix() + ":meta", globalMetaJson);
          cmd.exec();
        } catch (Exception e) {
          cmd.discard();
          throw new RuntimeException(e);
        }
      });
    } catch (Exception e) {
      log.warn("Failed to put config to Redis: {}", e.getMessage());
    }
  }

  @Override
  public void putPipelines(String region, Map<String, Object> pipelines) {
    if (region == null || region.isEmpty() || pipelines == null) return;
    String pipelinesKey = org.olo.configuration.RedisKeys.configPrefix() + ":pipelines:" + region;
    try {
      // Pipelines updates should always be consistent with the current core snapshot.
      ConfigurationSnapshot core = getCore(region);
      if (core == null) {
        log.warn("Skipping pipelines put: core snapshot missing for region={}", region);
        return;
      }
      Map<String, Object> connections = getConnections(region);
      if (connections == null) connections = Map.of();

      long now = System.currentTimeMillis();
      Instant updatedAt = Instant.now();

      String pipelinesJson = MAPPER.writeValueAsString(pipelines);
      SnapshotMetadata existingMeta = getMeta(region);
      String existingGlobalMetaJson = getString(org.olo.configuration.RedisKeys.configPrefix() + ":meta");
      long coreVer = core.getVersion();
      long connectionsVer = getSectionVersion(existingMeta, META_BLOCK_CONNECTIONS);
      long queuesVer = getSectionVersion(existingMeta, META_BLOCK_QUEUES);
      long profilesVer = getSectionVersion(existingMeta, META_BLOCK_PROFILES);
      String globalMetaJson = metaToJsonNewGlobalMerged(existingGlobalMetaJson, region,
          coreVer, now, connectionsVer, queuesVer, profilesVer, now);

      if (isOverLimit(pipelinesJson, pipelinesKey)
          || isOverLimit(globalMetaJson, org.olo.configuration.RedisKeys.configPrefix() + ":meta")) {
        log.warn("Skipping putPipelines to Redis: pipelines or meta exceeds olo.config.snapshot.max.redis.value.bytes; region={}", region);
        return;
      }

      withConnection(conn -> {
        RedisCommands<String, String> cmd = conn.sync();
        cmd.multi();
        try {
          cmd.set(pipelinesKey, pipelinesJson);
          // Global meta last (publish rule).
          cmd.set(org.olo.configuration.RedisKeys.configPrefix() + ":meta", globalMetaJson);
          cmd.exec();
        } catch (Exception e) {
          cmd.discard();
          throw new RuntimeException(e);
        }
      });
    } catch (Exception e) {
      log.warn("Failed to put pipelines to Redis: {}", e.getMessage());
    }
  }

  @Override
  public void putQueues(String region, Map<String, Object> queues) {
    if (region == null || region.isEmpty() || queues == null) return;
    String queuesKey = org.olo.configuration.RedisKeys.configPrefix() + ":queues:" + region;
    try {
      ConfigurationSnapshot core = getCore(region);
      if (core == null) {
        log.warn("Skipping queues put: core snapshot missing for region={}", region);
        return;
      }
      long now = System.currentTimeMillis();
      String queuesJson = MAPPER.writeValueAsString(queues);
      SnapshotMetadata existingMeta = getMeta(region);
      String existingGlobalMetaJson = getString(org.olo.configuration.RedisKeys.configPrefix() + ":meta");
      long coreVer = core.getVersion();
      long pipelinesVer = getSectionVersion(existingMeta, META_BLOCK_PIPELINES);
      long connectionsVer = getSectionVersion(existingMeta, META_BLOCK_CONNECTIONS);
      long profilesVer = getSectionVersion(existingMeta, META_BLOCK_PROFILES);
      String globalMetaJson = metaToJsonNewGlobalMerged(existingGlobalMetaJson, region,
          coreVer, pipelinesVer, connectionsVer, now, profilesVer, now);

      if (isOverLimit(queuesJson, queuesKey)
          || isOverLimit(globalMetaJson, org.olo.configuration.RedisKeys.configPrefix() + ":meta")) {
        log.warn("Skipping putQueues to Redis: queues or meta exceeds olo.config.snapshot.max.redis.value.bytes; region={}", region);
        return;
      }

      withConnection(conn -> {
        RedisCommands<String, String> cmd = conn.sync();
        cmd.multi();
        try {
          cmd.set(queuesKey, queuesJson);
          cmd.set(org.olo.configuration.RedisKeys.configPrefix() + ":meta", globalMetaJson);
          cmd.exec();
        } catch (Exception e) {
          cmd.discard();
          throw new RuntimeException(e);
        }
      });
    } catch (Exception e) {
      log.warn("Failed to put queues to Redis: {}", e.getMessage());
    }
  }

  @Override
  public void putProfiles(String region, Map<String, Object> profiles) {
    if (region == null || region.isEmpty() || profiles == null) return;
    String profilesKey = org.olo.configuration.RedisKeys.configPrefix() + ":profiles:" + region;
    try {
      ConfigurationSnapshot core = getCore(region);
      if (core == null) {
        log.warn("Skipping profiles put: core snapshot missing for region={}", region);
        return;
      }
      long now = System.currentTimeMillis();
      String profilesJson = MAPPER.writeValueAsString(profiles);
      SnapshotMetadata existingMeta = getMeta(region);
      String existingGlobalMetaJson = getString(org.olo.configuration.RedisKeys.configPrefix() + ":meta");
      long coreVer = core.getVersion();
      long pipelinesVer = getSectionVersion(existingMeta, META_BLOCK_PIPELINES);
      long connectionsVer = getSectionVersion(existingMeta, META_BLOCK_CONNECTIONS);
      long queuesVer = getSectionVersion(existingMeta, META_BLOCK_QUEUES);
      String globalMetaJson = metaToJsonNewGlobalMerged(existingGlobalMetaJson, region,
          coreVer, pipelinesVer, connectionsVer, queuesVer, now, now);

      if (isOverLimit(profilesJson, profilesKey)
          || isOverLimit(globalMetaJson, org.olo.configuration.RedisKeys.configPrefix() + ":meta")) {
        log.warn("Skipping putProfiles to Redis: profiles or meta exceeds olo.config.snapshot.max.redis.value.bytes; region={}", region);
        return;
      }

      withConnection(conn -> {
        RedisCommands<String, String> cmd = conn.sync();
        cmd.multi();
        try {
          cmd.set(profilesKey, profilesJson);
          cmd.set(org.olo.configuration.RedisKeys.configPrefix() + ":meta", globalMetaJson);
          cmd.exec();
        } catch (Exception e) {
          cmd.discard();
          throw new RuntimeException(e);
        }
      });
    } catch (Exception e) {
      log.warn("Failed to put profiles to Redis: {}", e.getMessage());
    }
  }

  @Override
  public ConfigurationSnapshot getCore(String region) {
    if (region == null || region.isEmpty()) return null;
    try {
      final String[] out = new String[1];
      String key = org.olo.configuration.RedisKeys.configPrefix() + ":core";
      withConnection(conn -> {
        RedisCommands<String, String> cmd = conn.sync();
        out[0] = cmd.get(key);
      });
      String json = out[0];
      if (json == null || json.isBlank()) return null;
      if (isOverLimit(json, key)) return null;
      return coreFromJson(json, region);
    } catch (Exception e) {
      log.warn("Failed to get core from Redis for region={}: {}", region, e.getMessage());
      return null;
    }
  }

  @Override
  public Map<String, Object> getPipelines(String region) {
    if (region == null || region.isEmpty()) return null;
    try {
      final String[] out = new String[1];
      String key = org.olo.configuration.RedisKeys.configPrefix() + ":pipelines:" + region;
      withConnection(conn -> {
        RedisCommands<String, String> cmd = conn.sync();
        out[0] = cmd.get(key);
      });
      String json = out[0];
      if (json == null || json.isBlank()) return null;
      if (isOverLimit(json, key)) return null;
      return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      log.error("Failed to get pipelines from Redis for region={} (invalid or corrupted data); keeping current snapshot, will retry next cycle: {}", region, e.getMessage());
      return null;
    }
  }

  @Override
  public Map<String, Object> getConnections(String region) {
    if (region == null || region.isEmpty()) return null;
    try {
      final String[] out = new String[1];
      String key = org.olo.configuration.RedisKeys.configPrefix() + ":connections:" + region;
      withConnection(conn -> {
        RedisCommands<String, String> cmd = conn.sync();
        out[0] = cmd.get(key);
      });
      String json = out[0];
      if (json == null || json.isBlank()) return null;
      if (isOverLimit(json, key)) return null;
      return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      log.error("Failed to get connections from Redis for region={} (invalid or corrupted data); keeping current snapshot, will retry next cycle: {}", region, e.getMessage());
      return null;
    }
  }

  @Override
  public Map<String, Object> getQueues(String region) {
    if (region == null || region.isEmpty()) return null;
    try {
      final String[] out = new String[1];
      String key = org.olo.configuration.RedisKeys.configPrefix() + ":queues:" + region;
      withConnection(conn -> {
        RedisCommands<String, String> cmd = conn.sync();
        out[0] = cmd.get(key);
      });
      String json = out[0];
      if (json == null || json.isBlank()) return null;
      if (isOverLimit(json, key)) return null;
      return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      log.error("Failed to get queues from Redis for region={} (invalid or corrupted data); keeping current snapshot, will retry next cycle: {}", region, e.getMessage());
      return null;
    }
  }

  @Override
  public Map<String, Object> getProfiles(String region) {
    if (region == null || region.isEmpty()) return null;
    try {
      final String[] out = new String[1];
      String key = org.olo.configuration.RedisKeys.configPrefix() + ":profiles:" + region;
      withConnection(conn -> {
        RedisCommands<String, String> cmd = conn.sync();
        out[0] = cmd.get(key);
      });
      String json = out[0];
      if (json == null || json.isBlank()) return null;
      if (isOverLimit(json, key)) return null;
      return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      log.error("Failed to get profiles from Redis for region={} (invalid or corrupted data); keeping current snapshot, will retry next cycle: {}", region, e.getMessage());
      return null;
    }
  }

  @Override
  public Map<String, Object> getResources(String region) {
    if (region == null || region.isEmpty()) return null;
    String key = org.olo.configuration.RedisKeys.configPrefix() + ":resources:" + region;
    try {
      final String[] out = new String[1];
      withConnection(conn -> out[0] = conn.sync().get(key));
      String json = out[0];
      if (json == null || json.isBlank()) return null;
      if (isOverLimit(json, key)) return null;
      return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      log.error("Failed to get resources from Redis for region={} (invalid or corrupted data); keeping current snapshot, will retry next cycle: {}", region, e.getMessage());
      return null;
    }
  }

  @Override
  public Map<String, Object> getTenantOverrides(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) return null;
    String key = org.olo.configuration.RedisKeys.configPrefix() + ":overrides:tenant:" + tenantId;
    try {
      final String[] out = new String[1];
      withConnection(conn -> out[0] = conn.sync().get(key));
      String json = out[0];
      if (json == null || json.isBlank()) return null;
      if (isOverLimit(json, key)) return null;
      return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      log.error("Failed to get tenant overrides from Redis for tenantId={} (invalid or corrupted data); keeping current snapshot, will retry next cycle: {}", tenantId, e.getMessage());
      return null;
    }
  }

  @Override
  public void putTenantOverrides(String tenantId, Map<String, Object> overrides) {
    if (tenantId == null || tenantId.isBlank() || overrides == null || overrides.isEmpty()) return;
    String key = org.olo.configuration.RedisKeys.configPrefix() + ":overrides:tenant:" + tenantId;
    try {
      String json = MAPPER.writeValueAsString(overrides);
      if (isOverLimit(json, key)) {
        log.warn("Skipping tenant overrides put: value exceeds olo.config.snapshot.max.redis.value.bytes; tenantId={}", tenantId);
        return;
      }
      withConnection(conn -> conn.sync().set(key, json));
    } catch (Exception e) {
      log.warn("Failed to put tenant overrides to Redis for tenantId={}: {}", tenantId, e.getMessage());
    }
  }

  /** Optional pipelined read: GET core, pipelines, connections, queues, profiles in one round trip (no meta). */
  public CorePipelinesConnections getCorePipelinesConnectionsBatch(String region) {
    if (region == null || region.isEmpty()) return null;
    String coreKey = org.olo.configuration.RedisKeys.configPrefix() + ":core";
    String pipelinesKey = org.olo.configuration.RedisKeys.configPrefix() + ":pipelines:" + region;
    String connectionsKey = org.olo.configuration.RedisKeys.configPrefix() + ":connections:" + region;
    String queuesKey = org.olo.configuration.RedisKeys.configPrefix() + ":queues:" + region;
    String profilesKey = org.olo.configuration.RedisKeys.configPrefix() + ":profiles:" + region;
    try {
      final String[] coreJson = new String[1];
      final String[] pipelinesJson = new String[1];
      final String[] connectionsJson = new String[1];
      final String[] queuesJson = new String[1];
      final String[] profilesJson = new String[1];
      withConnection(conn -> {
        RedisAsyncCommands<String, String> async = conn.async();
        async.setAutoFlushCommands(false);
        var fCore = async.get(coreKey);
        var fPipelines = async.get(pipelinesKey);
        var fConnections = async.get(connectionsKey);
        var fQueues = async.get(queuesKey);
        var fProfiles = async.get(profilesKey);
        async.flushCommands();
        async.setAutoFlushCommands(true);
        try {
          coreJson[0] = fCore.get();
          pipelinesJson[0] = fPipelines.get();
          connectionsJson[0] = fConnections.get();
          queuesJson[0] = fQueues.get();
          profilesJson[0] = fProfiles.get();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
      if (coreJson[0] != null && isOverLimit(coreJson[0], coreKey)) coreJson[0] = null;
      if (pipelinesJson[0] != null && isOverLimit(pipelinesJson[0], pipelinesKey)) pipelinesJson[0] = null;
      if (connectionsJson[0] != null && isOverLimit(connectionsJson[0], connectionsKey)) connectionsJson[0] = null;
      if (queuesJson[0] != null && isOverLimit(queuesJson[0], queuesKey)) queuesJson[0] = null;
      if (profilesJson[0] != null && isOverLimit(profilesJson[0], profilesKey)) profilesJson[0] = null;
      ConfigurationSnapshot core = (coreJson[0] != null && !coreJson[0].isBlank()) ? coreFromJson(coreJson[0], region) : null;
      Map<String, Object> pipelines = (pipelinesJson[0] != null && !pipelinesJson[0].isBlank())
          ? MAPPER.readValue(pipelinesJson[0], new TypeReference<Map<String, Object>>() {}) : null;
      Map<String, Object> connections = (connectionsJson[0] != null && !connectionsJson[0].isBlank())
          ? MAPPER.readValue(connectionsJson[0], new TypeReference<Map<String, Object>>() {}) : null;
      Map<String, Object> queues = (queuesJson[0] != null && !queuesJson[0].isBlank())
          ? MAPPER.readValue(queuesJson[0], new TypeReference<Map<String, Object>>() {}) : null;
      Map<String, Object> profiles = (profilesJson[0] != null && !profilesJson[0].isBlank())
          ? MAPPER.readValue(profilesJson[0], new TypeReference<Map<String, Object>>() {}) : null;
      return new CorePipelinesConnections(core, pipelines, connections, queues, profiles);
    } catch (ExecutionException e) {
      log.error("Failed to get core/pipelines/connections from Redis; keeping current snapshot, will retry next cycle: {}", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
      return null;
    } catch (Exception e) {
      log.error("Failed to get core/pipelines/connections from Redis (invalid or corrupted JSON); keeping current snapshot, will retry next cycle: {}", e.getMessage());
      return null;
    }
  }

  /** Optional pipelined read: GET meta, core, pipelines, connections, queues, profiles in one round trip. */
  public SectionedBatch getSectionedBatch(String region) {
    if (region == null || region.isEmpty()) return null;
    String metaKey = org.olo.configuration.RedisKeys.configPrefix() + ":meta";
    String coreKey = org.olo.configuration.RedisKeys.configPrefix() + ":core";
    String pipelinesKey = org.olo.configuration.RedisKeys.configPrefix() + ":pipelines:" + region;
    String connectionsKey = org.olo.configuration.RedisKeys.configPrefix() + ":connections:" + region;
    String queuesKey = org.olo.configuration.RedisKeys.configPrefix() + ":queues:" + region;
    String profilesKey = org.olo.configuration.RedisKeys.configPrefix() + ":profiles:" + region;
    try {
      final String[] metaJson = new String[1];
      final String[] coreJson = new String[1];
      final String[] pipelinesJson = new String[1];
      final String[] connectionsJson = new String[1];
      final String[] queuesJson = new String[1];
      final String[] profilesJson = new String[1];
      withConnection(conn -> {
        RedisAsyncCommands<String, String> async = conn.async();
        async.setAutoFlushCommands(false);
        var fMeta = async.get(metaKey);
        var fCore = async.get(coreKey);
        var fPipelines = async.get(pipelinesKey);
        var fConnections = async.get(connectionsKey);
        var fQueues = async.get(queuesKey);
        var fProfiles = async.get(profilesKey);
        async.flushCommands();
        async.setAutoFlushCommands(true);
        try {
          metaJson[0] = fMeta.get();
          coreJson[0] = fCore.get();
          pipelinesJson[0] = fPipelines.get();
          connectionsJson[0] = fConnections.get();
          queuesJson[0] = fQueues.get();
          profilesJson[0] = fProfiles.get();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
      if (metaJson[0] != null && isOverLimit(metaJson[0], metaKey)) metaJson[0] = null;
      if (coreJson[0] != null && isOverLimit(coreJson[0], coreKey)) coreJson[0] = null;
      if (pipelinesJson[0] != null && isOverLimit(pipelinesJson[0], pipelinesKey)) pipelinesJson[0] = null;
      if (connectionsJson[0] != null && isOverLimit(connectionsJson[0], connectionsKey)) connectionsJson[0] = null;
      if (queuesJson[0] != null && isOverLimit(queuesJson[0], queuesKey)) queuesJson[0] = null;
      if (profilesJson[0] != null && isOverLimit(profilesJson[0], profilesKey)) profilesJson[0] = null;
      SnapshotMetadata meta = null;
      if (metaJson[0] != null && !metaJson[0].isBlank()) {
        meta = metaFromJsonNewGlobal(metaJson[0], region);
        if (meta == null) meta = metaFromJson(metaJson[0]);
      }
      ConfigurationSnapshot core = (coreJson[0] != null && !coreJson[0].isBlank()) ? coreFromJson(coreJson[0], region) : null;
      Map<String, Object> pipelines = (pipelinesJson[0] != null && !pipelinesJson[0].isBlank())
          ? MAPPER.readValue(pipelinesJson[0], new TypeReference<Map<String, Object>>() {}) : null;
      Map<String, Object> connections = (connectionsJson[0] != null && !connectionsJson[0].isBlank())
          ? MAPPER.readValue(connectionsJson[0], new TypeReference<Map<String, Object>>() {}) : null;
      Map<String, Object> queues = (queuesJson[0] != null && !queuesJson[0].isBlank())
          ? MAPPER.readValue(queuesJson[0], new TypeReference<Map<String, Object>>() {}) : null;
      Map<String, Object> profiles = (profilesJson[0] != null && !profilesJson[0].isBlank())
          ? MAPPER.readValue(profilesJson[0], new TypeReference<Map<String, Object>>() {}) : null;
      return new SectionedBatch(meta, core, pipelines, connections, queues, profiles);
    } catch (ExecutionException e) {
      log.error("Failed to get sectioned batch from Redis (connection or I/O); keeping current snapshot, will retry next cycle: {}", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
      return null;
    } catch (Exception e) {
      log.error("Failed to get sectioned batch from Redis (invalid or corrupted JSON); keeping current snapshot, will retry next cycle: {}", e.getMessage());
      return null;
    }
  }

  /** Returns max allowed Redis value size in bytes (0 = no check). Read from olo.config.snapshot.max.redis.value.bytes. */
  private static int getMaxRedisValueBytes() {
    org.olo.configuration.Configuration c = ConfigurationProvider.get();
    if (c == null) return 0;
    return c.getInteger(RedisSnapshotLoader.CONFIG_SNAPSHOT_MAX_REDIS_VALUE_BYTES_KEY, 0);
  }

  /** True if value byte size exceeds olo.config.snapshot.max.redis.value.bytes. Logs warning and returns true when over. */
  private boolean isOverLimit(String json, String key) {
    if (json == null) return false;
    int limit = getMaxRedisValueBytes();
    if (limit <= 0) return false;
    int bytes = json.getBytes(StandardCharsets.UTF_8).length;
    if (bytes > limit) {
      log.warn("Redis value exceeds olo.config.snapshot.max.redis.value.bytes: key={} bytes={} limit={}; rejecting to protect Redis", key, bytes, limit);
      return true;
    }
    return false;
  }

  private static final String JSON_KEY_TENANT_OVERRIDES = "tenantOverrides";
  private static final String JSON_KEY_REGION = "region";
  private static final String JSON_KEY_RESOURCE_OVERRIDES = "resourceOverrides";
  private static final String META_BLOCK_CORE = "core";
  private static final String META_BLOCK_PIPELINES = "pipelines";
  private static final String META_BLOCK_CONNECTIONS = "connections";
  private static final String META_BLOCK_QUEUES = "queues";
  private static final String META_BLOCK_PROFILES = "profiles";
  private static final String META_BLOCK_REGIONAL_SETTINGS = "regionalSettings";
  private static final String META_KEY_GENERATION = "generation";
  private static final String META_KEY_CHECKSUM = "checksum";
  private static final String META_KEY_SNAPSHOTS_BY_REGION = "snapshotsByRegion";

  /** Core key can be flat { "k": "v" } or full snapshot shape; parse to ConfigurationSnapshot. */
  private static ConfigurationSnapshot coreFromJson(String json, String region) throws Exception {
    Map<String, Object> root = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    if (root.containsKey("global")) {
      return fromJson(json, region);
    }
    Map<String, String> flat = MAPPER.convertValue(root, new TypeReference<Map<String, String>>() {});
    return new ConfigurationSnapshot(region, 0L, Instant.EPOCH, flat, Map.of());
  }

  private static String toJson(ConfigurationSnapshot s) throws Exception {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("version", s.getVersion());
    root.put("lastUpdated", s.getLastUpdated().toString());
    root.put("global", s.getGlobalConfig());
    if (!s.getRegionConfig().isEmpty()) root.put(JSON_KEY_REGION, s.getRegionConfig());
    root.put(JSON_KEY_TENANT_OVERRIDES, s.getTenantConfig());
    if (!s.getResourceConfig().isEmpty()) root.put(JSON_KEY_RESOURCE_OVERRIDES, s.getResourceConfig());
    return MAPPER.writeValueAsString(root);
  }

  private static ConfigurationSnapshot fromJson(String json, String region) throws Exception {
    Map<String, Object> root = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    long version = root.get("version") instanceof Number ? ((Number) root.get("version")).longValue() : 0L;
    Instant lastUpdated = parseInstant(root.get("lastUpdated"));
    Map<String, String> global = root.get("global") != null
        ? MAPPER.convertValue(root.get("global"), new TypeReference<Map<String, String>>() {})
        : Map.of();
    Map<String, String> regionConfig = root.get(JSON_KEY_REGION) != null
        ? MAPPER.convertValue(root.get(JSON_KEY_REGION), new TypeReference<Map<String, String>>() {})
        : Map.of();
    Object overrides = root.get(JSON_KEY_TENANT_OVERRIDES) != null ? root.get(JSON_KEY_TENANT_OVERRIDES) : root.get("tenants");
    Map<String, Map<String, String>> tenantOverrides = overrides != null
        ? MAPPER.convertValue(overrides, new TypeReference<Map<String, Map<String, String>>>() {})
        : Map.of();
    Object resOverrides = root.get(JSON_KEY_RESOURCE_OVERRIDES);
    Map<String, Map<String, String>> resourceOverrides = resOverrides != null
        ? MAPPER.convertValue(resOverrides, new TypeReference<Map<String, Map<String, String>>>() {})
        : Map.of();
    return new ConfigurationSnapshot(region, version, lastUpdated, global, regionConfig, tenantOverrides, resourceOverrides);
  }

  private static String metaToJson(ConfigurationSnapshot s) throws Exception {
    Map<String, Object> core = new LinkedHashMap<>();
    core.put("version", s.getVersion());
    core.put("lastUpdated", s.getLastUpdated().toString());
    Map<String, Object> regionalSettings = new LinkedHashMap<>();
    regionalSettings.put("version", s.getVersion());
    regionalSettings.put("lastUpdated", s.getLastUpdated().toString());
    Map<String, Object> root = new LinkedHashMap<>();
    root.put(META_KEY_GENERATION, s.getVersion());
    root.put(META_KEY_CHECKSUM, SnapshotChecksum.compute(s, Map.of(), Map.of(), Map.of(), Map.of()));
    root.put(META_BLOCK_CORE, core);
    root.put(META_BLOCK_REGIONAL_SETTINGS, regionalSettings);
    return MAPPER.writeValueAsString(root);
  }

  private static String metaToJsonForCore(
      SnapshotMetadata existing,
      ConfigurationSnapshot coreSnapshot,
      Map<String, Object> pipelines,
      Map<String, Object> connections) throws Exception {
    long ver = coreSnapshot.getVersion();
    Instant updatedAt = coreSnapshot.getLastUpdated();

    Map<String, Object> root = new LinkedHashMap<>();
    root.put(META_KEY_GENERATION, ver);
    root.put(META_KEY_CHECKSUM, SnapshotChecksum.compute(coreSnapshot, pipelines, connections, Map.of(), Map.of()));

    // Core block always updated to match the core snapshot.
    Map<String, Object> core = new LinkedHashMap<>();
    core.put("version", ver);
    core.put("lastUpdated", updatedAt.toString());
    root.put(META_BLOCK_CORE, core);

    // Preserve pipelines/connections blocks if present.
    Map<String, BlockMetadata> blocks = existing != null ? existing.getBlocks() : Collections.emptyMap();
    BlockMetadata pipBlock = blocks.get(META_BLOCK_PIPELINES);
    if (pipBlock != null) {
      Map<String, Object> pipObj = new LinkedHashMap<>();
      pipObj.put("version", pipBlock.getVersion());
      pipObj.put("lastUpdated", pipBlock.getLastUpdated().toString());
      root.put(META_BLOCK_PIPELINES, pipObj);
    }
    BlockMetadata connBlock = blocks.get(META_BLOCK_CONNECTIONS);
    if (connBlock != null) {
      Map<String, Object> connObj = new LinkedHashMap<>();
      connObj.put("version", connBlock.getVersion());
      connObj.put("lastUpdated", connBlock.getLastUpdated().toString());
      root.put(META_BLOCK_CONNECTIONS, connObj);
    }

    // Regional settings tracks the overall snapshot identity; bump it on core change.
    Map<String, Object> regionalSettings = new LinkedHashMap<>();
    regionalSettings.put("version", ver);
    regionalSettings.put("lastUpdated", updatedAt.toString());
    root.put(META_BLOCK_REGIONAL_SETTINGS, regionalSettings);

    return MAPPER.writeValueAsString(root);
  }

  private static SnapshotMetadata metaFromJson(String json) throws Exception {
    Map<String, Object> root = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    if (root.containsKey("version") && !root.containsKey("core")) {
      return metaFromJsonFlat(root);
    }
    long generation = root.get(META_KEY_GENERATION) instanceof Number ? ((Number) root.get(META_KEY_GENERATION)).longValue() : 0L;
    String checksum = root.get(META_KEY_CHECKSUM) instanceof String ? (String) root.get(META_KEY_CHECKSUM) : null;
    Map<String, BlockMetadata> blocks = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : root.entrySet()) {
      if (e.getValue() instanceof Map) {
        Map<String, Object> block = (Map<String, Object>) e.getValue();
        long version = block.get("version") instanceof Number ? ((Number) block.get("version")).longValue() : 0L;
        Instant lastUpdated = parseInstant(block.get("lastUpdated"));
        blocks.put(e.getKey(), new BlockMetadata(version, lastUpdated));
      }
    }
    return new SnapshotMetadata(generation, checksum, blocks);
  }

  /**
   * Parses new global meta shape stored at {@link ConfigurationSnapshotStore#META_KEY}:
   * <pre>
   * {
   *   "primaryRegion": "...",
   *   "servedRegions": ["..."],
   *   "snapshotsByRegion": {
   *     "us-east": {
   *       "snapshotId": "us-east:123",
   *       "coreVersion": 1,
   *       "pipelinesVersion": 2,
   *       "connectionsVersion": 3,
   *       "queuesVersion": 0,
   *       "profilesVersion": 0,
   *       "regionalSettingsVersion": 2
   *     }
   *   }
   * }
   * </pre>
   * Returns null when the JSON is not in this shape.
   */
  private static SnapshotMetadata metaFromJsonNewGlobal(String json, String region) throws Exception {
    Map<String, Object> root = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    Object sbr = root.get(META_KEY_SNAPSHOTS_BY_REGION);
    if (!(sbr instanceof Map)) return null;
    Map<String, Object> snapshotsByRegion = (Map<String, Object>) sbr;
    Object entryObj = snapshotsByRegion.get(region);
    if (!(entryObj instanceof Map)) return null;
    Map<String, Object> entry = (Map<String, Object>) entryObj;

    long coreV = entry.get("coreVersion") instanceof Number ? ((Number) entry.get("coreVersion")).longValue() : 0L;
    long pipelinesV = entry.get("pipelinesVersion") instanceof Number ? ((Number) entry.get("pipelinesVersion")).longValue() : 0L;
    long connectionsV = entry.get("connectionsVersion") instanceof Number ? ((Number) entry.get("connectionsVersion")).longValue() : 0L;
    long queuesV = entry.get("queuesVersion") instanceof Number ? ((Number) entry.get("queuesVersion")).longValue() : 0L;
    long profilesV = entry.get("profilesVersion") instanceof Number ? ((Number) entry.get("profilesVersion")).longValue() : 0L;
    long regionalV = entry.get("regionalSettingsVersion") instanceof Number ? ((Number) entry.get("regionalSettingsVersion")).longValue() : 0L;

    Map<String, BlockMetadata> blocks = new LinkedHashMap<>();
    // lastUpdated not carried in global meta; use EPOCH.
    blocks.put(META_BLOCK_CORE, new BlockMetadata(coreV, Instant.EPOCH));
    if (pipelinesV != 0L) blocks.put(META_BLOCK_PIPELINES, new BlockMetadata(pipelinesV, Instant.EPOCH));
    if (connectionsV != 0L) blocks.put(META_BLOCK_CONNECTIONS, new BlockMetadata(connectionsV, Instant.EPOCH));
    if (queuesV != 0L) blocks.put(META_BLOCK_QUEUES, new BlockMetadata(queuesV, Instant.EPOCH));
    if (profilesV != 0L) blocks.put(META_BLOCK_PROFILES, new BlockMetadata(profilesV, Instant.EPOCH));
    blocks.put(META_BLOCK_REGIONAL_SETTINGS, new BlockMetadata(regionalV, Instant.EPOCH));

    long generation = coreV;
    generation = Math.max(generation, pipelinesV);
    generation = Math.max(generation, connectionsV);
    generation = Math.max(generation, queuesV);
    generation = Math.max(generation, profilesV);
    generation = Math.max(generation, regionalV);
    return new SnapshotMetadata(generation, null, blocks);
  }

  private static long getSectionVersion(SnapshotMetadata existing, String section) {
    if (existing == null) return 0L;
    BlockMetadata b = existing.getBlocks().get(section);
    return b != null ? b.getVersion() : 0L;
  }

  private static String metaToJsonNewGlobalMerged(
      String existingGlobalMetaJson,
      String region,
      long coreVersion,
      long pipelinesVersion,
      long connectionsVersion,
      long queuesVersion,
      long profilesVersion,
      long regionalSettingsVersion) throws Exception {
    Map<String, Object> root;
    try {
      root = (existingGlobalMetaJson != null && !existingGlobalMetaJson.isBlank())
          ? MAPPER.readValue(existingGlobalMetaJson, new TypeReference<Map<String, Object>>() {})
          : new LinkedHashMap<>();
    } catch (Exception ignore) {
      // If existing meta is corrupted, start fresh rather than failing the write.
      root = new LinkedHashMap<>();
    }

    Map<String, Object> snapshotsByRegion;
    Object sbr = root.get(META_KEY_SNAPSHOTS_BY_REGION);
    if (sbr instanceof Map) {
      snapshotsByRegion = new LinkedHashMap<>((Map<String, Object>) sbr);
    } else {
      snapshotsByRegion = new LinkedHashMap<>();
    }

    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("snapshotId", region + ":" + regionalSettingsVersion);
    entry.put("coreVersion", coreVersion);
    entry.put("pipelinesVersion", pipelinesVersion);
    entry.put("connectionsVersion", connectionsVersion);
    entry.put("queuesVersion", queuesVersion);
    entry.put("profilesVersion", profilesVersion);
    entry.put("regionalSettingsVersion", regionalSettingsVersion);
    snapshotsByRegion.put(region, entry);
    root.put(META_KEY_SNAPSHOTS_BY_REGION, snapshotsByRegion);
    return MAPPER.writeValueAsString(root);
  }

  private String getString(String key) {
    final String[] out = new String[1];
    withConnection(conn -> out[0] = conn.sync().get(key));
    return out[0];
  }

  private static String metaToJsonForSections(
      SnapshotMetadata existing,
      ConfigurationSnapshot core,
      Map<String, Object> pipelines,
      Map<String, Object> connections,
      long version,
      Instant updatedAt) throws Exception {
    // Build a sectioned meta structure. We always update pipelines + regionalSettings together so snapshotId changes.
    Map<String, Object> root = new LinkedHashMap<>();
    root.put(META_KEY_GENERATION, version);
    root.put(META_KEY_CHECKSUM, SnapshotChecksum.compute(core, pipelines, connections, Map.of(), Map.of()));

    // Preserve existing core/connection blocks if present (avoid rewriting their versions on pipelines update).
    Map<String, BlockMetadata> blocks = existing != null ? existing.getBlocks() : Collections.emptyMap();

    BlockMetadata coreBlock = blocks.getOrDefault(META_BLOCK_CORE, new BlockMetadata(core.getVersion(), core.getLastUpdated()));
    Map<String, Object> coreObj = new LinkedHashMap<>();
    coreObj.put("version", coreBlock.getVersion());
    coreObj.put("lastUpdated", coreBlock.getLastUpdated().toString());
    root.put(META_BLOCK_CORE, coreObj);

    BlockMetadata connBlock = blocks.get(META_BLOCK_CONNECTIONS);
    if (connBlock != null) {
      Map<String, Object> connObj = new LinkedHashMap<>();
      connObj.put("version", connBlock.getVersion());
      connObj.put("lastUpdated", connBlock.getLastUpdated().toString());
      root.put(META_BLOCK_CONNECTIONS, connObj);
    }

    Map<String, Object> pipObj = new LinkedHashMap<>();
    pipObj.put("version", version);
    pipObj.put("lastUpdated", updatedAt.toString());
    root.put(META_BLOCK_PIPELINES, pipObj);

    Map<String, Object> regionalSettings = new LinkedHashMap<>();
    regionalSettings.put("version", version);
    regionalSettings.put("lastUpdated", updatedAt.toString());
    root.put(META_BLOCK_REGIONAL_SETTINGS, regionalSettings);

    return MAPPER.writeValueAsString(root);
  }

  private static SnapshotMetadata metaFromJsonFlat(Map<String, Object> m) {
    long version = m.get("version") instanceof Number ? ((Number) m.get("version")).longValue() : 0L;
    Instant lastUpdated = parseInstant(m.get("lastUpdated"));
    String checksum = m.get(META_KEY_CHECKSUM) instanceof String ? (String) m.get(META_KEY_CHECKSUM) : null;
    return new SnapshotMetadata(version, checksum, Map.of(META_BLOCK_CORE, new BlockMetadata(version, lastUpdated)));
  }

  private static Instant parseInstant(Object v) {
    if (v == null) return Instant.EPOCH;
    if (v instanceof String) return Instant.parse((String) v);
    return Instant.EPOCH;
  }

  @Override
  public void close() {
    if (sharedConnection != null) {
      try {
        sharedConnection.close();
      } catch (Exception ignore) {
      }
    }
    if (redisClient != null) {
      try {
        redisClient.shutdown();
      } catch (Exception ignore) {
      }
    }
  }
}
