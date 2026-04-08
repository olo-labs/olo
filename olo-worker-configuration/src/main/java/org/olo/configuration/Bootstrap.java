/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration;

import org.olo.configuration.impl.config.DefaultConfiguration;
import org.olo.configuration.impl.connection.ConnectionConfig;
import org.olo.configuration.impl.refresh.ConfigRefreshScheduler;
import org.olo.configuration.impl.refresh.RedisSnapshotLoader;
import org.olo.configuration.impl.snapshot.ConfigSnapshotBuilder;
import org.olo.configuration.impl.snapshot.PipelineSectionBuilder;
import org.olo.configuration.impl.snapshot.TenantOverridesSectionBuilder;
import org.olo.configuration.port.CacheConnectionSettings;
import org.olo.configuration.port.ConfigChangeSubscriber;
import org.olo.configuration.port.ConfigurationPortRegistry;
import org.olo.configuration.port.DbConnectionSettings;
import org.olo.configuration.impl.source.DefaultsConfigurationSource;
import org.olo.configuration.impl.source.EnvironmentConfigurationSource;
import org.olo.configuration.impl.region.TenantRegionRefreshScheduler;
import org.olo.configuration.region.TenantRegionResolver;
import org.olo.configuration.snapshot.CompositeConfigurationSnapshot;
import org.olo.configuration.snapshot.ConfigurationSnapshot;
import org.olo.configuration.snapshot.ConfigurationSnapshotStore;
import org.olo.configuration.snapshot.SnapshotMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.olo.configuration.source.ConfigurationSource;

/**
 * Central bootstrap. Workers: defaults → env → Redis snapshot.
 * <p><strong>Startup:</strong> When Redis is configured, worker waits until the configuration store (Redis) is available
 * and a snapshot can be loaded. If Redis is up but no snapshot exists for the region and DB is configured,
 * the worker builds the snapshot from DB and writes it to Redis (standard use case when data is not yet in Redis).
 * When Redis is not configured, worker uses defaults + env only.
 * <strong>Runtime:</strong> If Redis is unavailable during refresh, the worker keeps the current snapshot and retries next cycle.
 */
public final class Bootstrap {

  private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);

  private static volatile ConfigRefreshScheduler refreshScheduler;
  private static volatile ConfigChangeSubscriber configRefreshPubSubSubscriber;
  private static volatile TenantRegionRefreshScheduler tenantRegionRefreshScheduler;
  private static volatile ConfigurationSnapshotStore configSnapshotStore;

  /** Retry interval (ms) when waiting for Redis at startup. */
  public static final String CONFIG_STORE_RETRY_INTERVAL_MS_KEY = "olo.bootstrap.config.store.retry.interval.ms";
  /** Max time (seconds) to wait for configuration store at startup; 0 = wait forever. On timeout the process exits so orchestration (e.g. Kubernetes) can restart. */
  public static final String CONFIG_STORE_WAIT_TIMEOUT_SECONDS_KEY = "olo.bootstrap.config.wait.timeout.seconds";
  private static final long DEFAULT_CONFIG_STORE_RETRY_INTERVAL_MS = 5_000L;
  private static final long DEFAULT_CONFIG_STORE_WAIT_TIMEOUT_SECONDS = 0L;

  private Bootstrap() {}

  public static final String CONFIG_REFRESH_ENABLED_KEY = "olo.config.refresh.enabled";
  public static final String CONFIG_REFRESH_PUBSUB_ENABLED_KEY = "olo.config.refresh.pubsub.enabled";
  public static final String CONFIG_REFRESH_PUBSUB_CHANNEL_PREFIX_KEY = "olo.config.refresh.pubsub.channel.prefix";
  public static final String DEFAULT_CONFIG_REFRESH_PUBSUB_CHANNEL_PREFIX = "";

  /**
   * Runs full bootstrap. Sequence:
   * <ol>
   *   <li>Tenant/region: try Redis first; if not available, build Redis cache from DB config, then consume (so tenant/region data exists in Redis before use).</li>
   *   <li>Config snapshot: when Redis is configured, wait for Redis and load snapshot; if snapshot is missing and DB is configured, build from DB and store in Redis, then load. When Redis is not configured: load defaults + env only.</li>
   *   <li>Optional config refresh and tenant-region refresh schedulers.</li>
   * </ol>
   */
  public static void run(boolean startRefreshScheduler) {
    Map<String, String> minimal = loadDefaultsAndEnvOnly();
    Regions.enforceSingleRegion(new DefaultConfiguration(minimal));
    ConnectionConfig conn = buildConnectionConfig(minimal);

    if (conn.hasDb()) {
      var initializer = ConfigurationPortRegistry.dbClientInitializer();
      if (initializer != null) {
        initializer.initializeDbClient(minimal);
      }
    }

    // Tenant/region: try Redis first; if not available, build Redis cache from DB config, then consume. Do this before config snapshot so Redis has tenant/region data before use.
    if (conn.hasRedis() || conn.hasDb()) {
      TenantRegionResolver.loadFrom(new DefaultConfiguration(minimal));
    }

    if (conn.hasRedis()) {
      runWithSnapshot(conn, minimal);
    } else {
      ConfigurationProvider.set(new DefaultConfiguration(minimal));
      ConfigurationProvider.setConfiguredRegions(new ArrayList<>(Regions.getRegions(new DefaultConfiguration(minimal))));
    }

    Configuration config = ConfigurationProvider.require();

    boolean pubSubEnabled = conn.hasRedis() && config.getBoolean(CONFIG_REFRESH_PUBSUB_ENABLED_KEY, true);
    boolean periodicRefreshEnabled = startRefreshScheduler || config.getBoolean(CONFIG_REFRESH_ENABLED_KEY, false);
    if (periodicRefreshEnabled || pubSubEnabled) {
      refreshScheduler = new ConfigRefreshScheduler(configSnapshotStore);
      refreshScheduler.start();
      log.info("Config refresh scheduler started (periodicEnabled={} pubSubEnabled={})", periodicRefreshEnabled, pubSubEnabled);
    }

    if (pubSubEnabled && refreshScheduler != null) {
      String channelPrefix = config.get(CONFIG_REFRESH_PUBSUB_CHANNEL_PREFIX_KEY, DEFAULT_CONFIG_REFRESH_PUBSUB_CHANNEL_PREFIX);
      if (channelPrefix == null || channelPrefix.isBlank()) {
        channelPrefix = RedisKeys.configPrefix();
      }
      Set<String> servedRegions = new HashSet<>(Regions.getRegions(config));
      var subFactory = ConfigurationPortRegistry.configChangeSubscriberFactory();
      if (subFactory != null) {
        configRefreshPubSubSubscriber = subFactory.create(
            new CacheConnectionSettings(conn.getRedisUri()),
            channelPrefix,
            servedRegions,
            () -> refreshScheduler.triggerRefresh("pubsub"));
        if (configRefreshPubSubSubscriber != null) {
          configRefreshPubSubSubscriber.start();
        }
      } else {
        log.warn("Config change subscriber factory is not registered; Pub/Sub refresh disabled");
      }
    }

    int tenantRegionInterval = config.getInteger(TenantRegionRefreshScheduler.REFRESH_INTERVAL_SECONDS_KEY, 0);
    if (tenantRegionInterval > 0) {
      tenantRegionRefreshScheduler = new TenantRegionRefreshScheduler(tenantRegionInterval, refreshScheduler);
      tenantRegionRefreshScheduler.start();
    }
  }

  public static void run() {
    run(false);
  }

  /** Loads only defaults + env (no Redis/DB). */
  static Map<String, String> loadDefaultsAndEnvOnly() {
    Map<String, String> config = new HashMap<>();
    config.putAll(new DefaultsConfigurationSource().load(config));
    config.putAll(new EnvironmentConfigurationSource().load(config));
    // Expose cache root key as a system property so RedisKeys can resolve it
    // even before ConfigurationProvider is initialized.
    String rootFromDefaults = config.get(org.olo.configuration.RedisKeys.ROOT_KEY_PROP);
    if (rootFromDefaults != null && !rootFromDefaults.isBlank()) {
      System.setProperty(org.olo.configuration.RedisKeys.ROOT_KEY_PROP, rootFromDefaults.trim());
      log.info("Bootstrap: using Redis root key from config {}={}", org.olo.configuration.RedisKeys.ROOT_KEY_PROP, rootFromDefaults.trim());
    } else {
      String effective = org.olo.configuration.RedisKeys.root();
      System.setProperty(org.olo.configuration.RedisKeys.ROOT_KEY_PROP, effective);
      log.info("Bootstrap: Redis root key not set in config; effective root={}", effective);
    }
    return config;
  }

  /**
   * Loads configuration from defaults + env. For use when not running full bootstrap (e.g. tests).
   * Equivalent to former ConfigurationLoader.load().
   */
  public static Configuration loadConfiguration() {
    return new DefaultConfiguration(loadDefaultsAndEnvOnly());
  }

  /**
   * Loads configuration from the given defaults stream then env. For use when not running full bootstrap (e.g. tests).
   * Equivalent to former ConfigurationLoader.load(InputStream).
   */
  public static Configuration loadConfiguration(InputStream defaultsStream) {
    List<ConfigurationSource> sources = new ArrayList<>();
    if (defaultsStream != null) {
      sources.add(new DefaultsConfigurationSource(defaultsStream));
    }
    sources.add(new EnvironmentConfigurationSource());
    Map<String, String> config = new HashMap<>();
    for (ConfigurationSource source : sources) {
      Map<String, String> next = source.load(config);
      if (next != null && !next.isEmpty()) {
        config.putAll(next);
      }
    }
    return new DefaultConfiguration(config);
  }

  static ConnectionConfig buildConnectionConfig(Map<String, String> m) {
    // Region: single value expected (Bootstrap.run enforces via Regions.enforceSingleRegion).
    String region = get(m, "olo.region", "").trim();
    if (region.isEmpty()) {
      region = Regions.DEFAULT_REGION;
    } else {
      int comma = region.indexOf(',');
      region = comma > 0 ? region.substring(0, comma).trim() : region;
      if (region.isEmpty()) region = Regions.DEFAULT_REGION;
    }
    if (region.isEmpty()) region = Regions.DEFAULT_REGION;
    String redisUri = get(m, "olo.redis.uri", "");
    if (redisUri.isEmpty()) {
      String host = get(m, "olo.redis.host", "");
      if (!host.isEmpty()) {
        int port = getInt(m, "olo.redis.port", 6379);
        String pw = get(m, "olo.redis.password", "");
        redisUri = pw.isEmpty() ? "redis://" + host + ":" + port : "redis://:" + pw + "@" + host + ":" + port;
      }
    }
    String jdbcUrl = get(m, "olo.db.url", "");
    if (jdbcUrl.isEmpty()) {
      String host = get(m, "olo.db.host", "");
      if (!host.isEmpty()) {
        int port = getInt(m, "olo.db.port", 5432);
        String name = get(m, "olo.db.name", "olo");
        jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + name;
      }
    }
    String dbUser = get(m, "olo.db.username", "");
    if (dbUser.isEmpty()) dbUser = get(m, "olo.db.user", "");
    return new ConnectionConfig(region, redisUri, jdbcUrl, dbUser, get(m, "olo.db.password", ""), getInt(m, "olo.db.pool.size", 5));
  }

  private static void runWithSnapshot(ConnectionConfig conn, Map<String, String> minimal) {
    long retryIntervalMs = getLong(minimal, CONFIG_STORE_RETRY_INTERVAL_MS_KEY, DEFAULT_CONFIG_STORE_RETRY_INTERVAL_MS);
    retryIntervalMs = Math.max(1_000L, retryIntervalMs);
    long timeoutSeconds = getLong(minimal, CONFIG_STORE_WAIT_TIMEOUT_SECONDS_KEY, DEFAULT_CONFIG_STORE_WAIT_TIMEOUT_SECONDS);

    var snapshotStoreFactory = ConfigurationPortRegistry.snapshotStoreFactory();
    if (snapshotStoreFactory == null) {
      throw new IllegalStateException("Snapshot store factory is not registered");
    }
    CacheConnectionSettings cacheSettings = new CacheConnectionSettings(conn.getRedisUri());

    try {
      waitForConfigurationStore(cacheSettings, conn, retryIntervalMs, timeoutSeconds);
      ConfigurationSnapshotStore store = snapshotStoreFactory.create(cacheSettings);
      if (store == null) {
        throw new IllegalStateException("Snapshot store factory returned null store");
      }
      configSnapshotStore = store;
      // Served regions from worker config only (defaults + env), so execution trees are maintained for configured regions only.
      List<String> servedRegionsList = Regions.getRegions(new DefaultConfiguration(minimal));
      ConfigurationProvider.setConfiguredRegions(servedRegionsList);
      Set<String> servedRegions = new HashSet<>(servedRegionsList);
      // Load worker region first so we have config for runtime
      RedisSnapshotLoader.load(conn.getRegion(), store);
      Configuration config = ConfigurationProvider.get();
      if (config == null) {
        throw new IllegalStateException("Configuration not set after loading worker region");
      }
      // Backfill pipelines from DB when missing; then read-through for queues/profiles (DB olo_config_section or derived from pipeline JSON).
      PipelineSectionBuilder sectionBuilder = new PipelineSectionBuilder(
          conn.hasDb() ? conn.getJdbcUrl() : "",
          conn.hasDb() ? conn.getDbUsername() : "",
          conn.hasDb() ? conn.getDbPassword() : "",
          store);
      if (conn.hasDb()) {
        for (String region : servedRegions) {
          if (store.getPipelines(region) == null) {
            sectionBuilder.buildAndStore(region);
          }
        }
      }
      for (String region : servedRegions) {
        sectionBuilder.ensureQueuesAndProfiles(region);
      }
      Map<String, CompositeConfigurationSnapshot> snapshotMap = new LinkedHashMap<>();
      CompositeConfigurationSnapshot workerComposite = ConfigurationProvider.getComposite();
      if (workerComposite != null) {
        snapshotMap.put(conn.getRegion(), workerComposite);
      }
      for (String region : servedRegions) {
        if (snapshotMap.containsKey(region)) continue;
        CompositeConfigurationSnapshot composite = RedisSnapshotLoader.loadComposite(region, store);
        if (composite != null) {
          snapshotMap.put(region, composite);
          log.info("Loaded configuration snapshot region={} snapshot={}", region, composite.getSnapshotId());
        } else {
          log.warn("No config in Redis for served region={}; pipeline lookup for that region may fail until admin pushes config", region);
        }
      }
      ConfigurationProvider.setSnapshotMap(snapshotMap, conn.getRegion());
    } catch (Exception e) {
      closeRedis();
      log.error("Failed to load configuration snapshot after store became available: {}", e.getMessage());
      throw new IllegalStateException("Configuration store was reachable but snapshot load failed", e);
    }

    if (ConfigurationProvider.getSnapshotMap() == null || ConfigurationProvider.getSnapshotMap().isEmpty()) {
      closeRedis();
      log.error("Configuration snapshot is null after load; admin may not have pushed config for region={}", conn.getRegion());
      throw new IllegalStateException("No configuration snapshot loaded for region=" + conn.getRegion());
    }
  }

  /**
   * Blocks until Redis is reachable and snapshot (meta + core) is present for the region.
   * Uses registered cache snapshot store factory.
   * Exit conditions: (1) both available → return; (2) timeout exceeded (if bootstrap.config.wait.timeout.seconds &gt; 0) → exit process so orchestrator can restart.
   */
  private static void waitForConfigurationStore(CacheConnectionSettings cacheSettings, ConnectionConfig conn, long retryIntervalMs, long timeoutSeconds) {
    String region = conn.getRegion();
    if (region == null || region.isEmpty()) region = "default";
    long deadlineMs = timeoutSeconds > 0 ? System.currentTimeMillis() + timeoutSeconds * 1000L : Long.MAX_VALUE;
    var snapshotStoreFactory = ConfigurationPortRegistry.snapshotStoreFactory();
    if (snapshotStoreFactory == null) {
      throw new IllegalStateException("Snapshot store factory is not registered");
    }
    for (; ; ) {
      if (System.currentTimeMillis() >= deadlineMs) {
        log.error("Configuration store not available within {}s; exiting so orchestration can restart", timeoutSeconds);
        Runtime.getRuntime().halt(1);
      }
      try {
        ConfigurationSnapshotStore store = snapshotStoreFactory.create(cacheSettings);
        if (store == null) throw new IllegalStateException("Snapshot store factory returned null store");
        SnapshotMetadata meta = store.getMeta(region);
        ConfigurationSnapshot core = store.getCore(region);
        if (meta != null && core != null) {
          // Pipelines must be consumed from Redis. If missing, backfill from DB then proceed.
          Map<String, Object> pipelines = store.getPipelines(region);
          if (pipelines != null) {
            PipelineSectionBuilder pb = new PipelineSectionBuilder(
                conn.hasDb() ? conn.getJdbcUrl() : "",
                conn.hasDb() ? conn.getDbUsername() : "",
                conn.hasDb() ? conn.getDbPassword() : "",
                store);
            pb.ensureQueuesAndProfiles(region);
            closeIfNeeded(store);
            log.info("Configuration store available for region={} (core+meta+pipes)", region);
            return;
          }
          if (conn.hasDb()) {
            PipelineSectionBuilder pb = new PipelineSectionBuilder(
                conn.getJdbcUrl(), conn.getDbUsername(), conn.getDbPassword(), store);
            if (pb.buildAndStore(region) != null && store.getPipelines(region) != null) {
              closeIfNeeded(store);
              log.info("Pipelines for region={} built from DB and stored in Redis", region);
              return;
            }
          }
          closeIfNeeded(store);
          log.info("Configuration store available for region={} (core+meta; pipelines missing)", region);
          return;
        }
        // Redis is up but snapshot missing: populate from DB when configured (standard use case).
        if (conn.hasDb()) {
          var repoFactory = ConfigurationPortRegistry.snapshotRepositoryFactory();
          if (repoFactory != null) {
            DbConnectionSettings dbSettings = new DbConnectionSettings(
                conn.getJdbcUrl(), conn.getDbUsername(), conn.getDbPassword(), conn.getDbPoolSize());
            ConfigSnapshotBuilder builder = new ConfigSnapshotBuilder(dbSettings, store);
            if (builder.buildAndStore(region) != null) {
              // After core snapshot is built, ensure pipelines section and tenant overrides cache are also present in Redis.
              // Pipelines must be consumed from Redis; tenant overrides are cached for workers.
              PipelineSectionBuilder pb = new PipelineSectionBuilder(
                  conn.getJdbcUrl(), conn.getDbUsername(), conn.getDbPassword(), store);
              pb.buildAndStore(region);
              TenantOverridesSectionBuilder tob = new TenantOverridesSectionBuilder(
                  conn.getJdbcUrl(), conn.getDbUsername(), conn.getDbPassword(), store);
              tob.buildAndStoreAllTenants();
              closeIfNeeded(store);
              log.info("Configuration snapshot for region={} built from DB and stored in Redis", region);
              return;
            }
          }
        }
        closeIfNeeded(store);
      } catch (Exception e) {
        log.warn("Waiting for configuration store (Redis): {}", e.getMessage());
      }
      try {
        Thread.sleep(retryIntervalMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for configuration store", e);
      }
    }
  }

  private static void closeRedis() {
    ConfigurationSnapshotStore s = configSnapshotStore;
    configSnapshotStore = null;
    closeIfNeeded(s);
  }

  private static void closeIfNeeded(Object maybeCloseable) {
    if (maybeCloseable instanceof AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Exception e) {
        log.warn("Error closing resource: {}", e.getMessage());
      }
    }
  }

  private static long getLong(Map<String, String> m, String key, long def) {
    String v = m.get(key);
    if (v == null || v.isBlank()) return def;
    try {
      return Long.parseLong(v.trim());
    } catch (NumberFormatException e) {
      return def;
    }
  }

  private static String get(Map<String, String> m, String key, String def) {
    String v = m.get(key);
    return v != null ? v.trim() : def;
  }

  private static int getInt(Map<String, String> m, String key, int def) {
    String v = m.get(key);
    if (v == null || v.isBlank()) return def;
    try {
      return Integer.parseInt(v.trim());
    } catch (NumberFormatException e) {
      return def;
    }
  }

  /**
   * Stops the config refresh scheduler and closes the shared Redis connection/client if they were created at bootstrap. Call on shutdown.
   */
  public static void stopRefreshScheduler() {
    ConfigChangeSubscriber sub = configRefreshPubSubSubscriber;
    if (sub != null) {
      sub.stop();
      configRefreshPubSubSubscriber = null;
      log.info("Config refresh Pub/Sub subscriber stopped");
    }
    ConfigRefreshScheduler s = refreshScheduler;
    if (s != null) {
      s.stop();
      refreshScheduler = null;
      log.info("Config refresh scheduler stopped");
    }
    closeRedis();
  }

  /**
   * Stops the tenant-region refresh scheduler if it was started. Call on shutdown.
   */
  public static void stopTenantRegionRefreshScheduler() {
    TenantRegionRefreshScheduler s = tenantRegionRefreshScheduler;
    if (s != null) {
      s.stop();
      tenantRegionRefreshScheduler = null;
      log.info("Tenant region refresh scheduler stopped");
    }
  }
}
