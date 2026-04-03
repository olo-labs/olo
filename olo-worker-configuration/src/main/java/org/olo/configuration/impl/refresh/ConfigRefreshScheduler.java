/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.impl.refresh;

import org.olo.configuration.ConfigurationProvider;
import org.olo.configuration.port.CacheConnectionSettings;
import org.olo.configuration.port.ConfigurationPortRegistry;
import org.olo.configuration.snapshot.ConfigurationSnapshot;
import org.olo.configuration.snapshot.ConfigurationSnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Periodically refreshes config from Redis. When a shared store is provided (worker bootstrap path),
 * refresh uses the same connection; do not create a new RedisClient per refresh.
 * <p>
 * <strong>Refresh duration guard:</strong> The next run is scheduled only after the current run completes
 * (fixed-delay semantics). If Redis hangs (e.g. 30 seconds), the next cycle does not start until the current
 * one finishes, so runs never overlap. This avoids overlapping refreshes and duplicate work.
 * </p>
 * <p>
 * <strong>Pub/Sub debounce:</strong> If refresh is already running, an incoming Pub/Sub trigger is ignored
 * (no duplicate run). Optionally, Pub/Sub triggers can be debounced (e.g. 1–2 s delay) so that when admin
 * updates pipeline, connection, and core in quick succession, workers run at most one refresh after the burst.
 * See {@value #REFRESH_PUBSUB_DEBOUNCE_MS_KEY}.
 * </p>
 */
public final class ConfigRefreshScheduler {

  private static final Logger log = LoggerFactory.getLogger(ConfigRefreshScheduler.class);

  public static final String REFRESH_INTERVAL_SECONDS_KEY = "olo.config.refresh.interval.seconds";
  /** Max jitter (seconds) added to interval to spread load across workers. Example: 10 → interval ± random(0,10)s. */
  public static final String REFRESH_INTERVAL_JITTER_SECONDS_KEY = "olo.config.refresh.interval.jitter.seconds";
  /** Debounce (ms) for Pub/Sub-triggered refresh. When admin updates pipeline + connection + core, multiple events can fire; a short delay coalesces them into one refresh. 0 = no debounce. */
  public static final String REFRESH_PUBSUB_DEBOUNCE_MS_KEY = "olo.config.refresh.pubsub.debounce.ms";
  private static final int DEFAULT_INTERVAL_SECONDS = 60;
  private static final int DEFAULT_JITTER_SECONDS = 10;
  private static final int DEFAULT_PUBSUB_DEBOUNCE_MS = 1500;

  private final ScheduledExecutorService executor;
  private final int intervalSeconds;
  private final int jitterSeconds;
  private final ConfigurationSnapshotStore store;
  private final AtomicReference<ScheduledFuture<?>> nextRun = new AtomicReference<>();
  private final AtomicReference<ScheduledFuture<?>> debouncedPubSubRun = new AtomicReference<>();
  private final AtomicBoolean refreshRunning = new AtomicBoolean(false);

  public ConfigRefreshScheduler() {
    this(null);
  }

  public ConfigRefreshScheduler(ConfigurationSnapshotStore store) {
    this(DEFAULT_INTERVAL_SECONDS, DEFAULT_JITTER_SECONDS, store);
  }

  public ConfigRefreshScheduler(int intervalSeconds) {
    this(intervalSeconds, DEFAULT_JITTER_SECONDS, null);
  }

  public ConfigRefreshScheduler(int intervalSeconds, int jitterSeconds) {
    this(intervalSeconds, jitterSeconds, null);
  }

  /**
   * @param store shared snapshot store (e.g. from bootstrap with shared Redis connection); when non-null, refresh uses it instead of creating a new RedisClient per cycle
   */
  public ConfigRefreshScheduler(int intervalSeconds, int jitterSeconds, ConfigurationSnapshotStore store) {
    this.intervalSeconds = Math.max(1, intervalSeconds);
    this.jitterSeconds = Math.max(0, jitterSeconds);
    this.store = store;
    this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "config-refresh-thread");
      t.setDaemon(true);
      return t;
    });
  }

  public void start() {
    int period = intervalSeconds;
    int jitter = jitterSeconds;
    org.olo.configuration.Configuration c = ConfigurationProvider.get();
    if (c != null) {
      period = c.getInteger(REFRESH_INTERVAL_SECONDS_KEY, intervalSeconds);
      period = Math.max(1, period);
      jitter = c.getInteger(REFRESH_INTERVAL_JITTER_SECONDS_KEY, jitterSeconds);
      jitter = Math.max(0, jitter);
    }
    scheduleFirstRun(period, jitter);
    log.info("Config refresh scheduled on config-refresh-thread every {}s (jitter ±{}s, initial offset 0..{}s)", period, jitter, period);
  }

  /** Initial delay = random(0, refreshInterval) to prevent thundering herd when many workers start together. */
  private void scheduleFirstRun(int periodSeconds, int jitterSeconds) {
    long delayMs = (long) (Math.random() * periodSeconds * 1000);
    nextRun.set(executor.schedule(this::runRefreshAndReschedule, delayMs, TimeUnit.MILLISECONDS));
  }

  /** Subsequent runs: interval + jitter so refreshes stay spread. */
  private void scheduleNext(int periodSeconds, int jitterSeconds) {
    long delayMs = periodSeconds * 1000L;
    if (jitterSeconds > 0) {
      delayMs += (long) (Math.random() * jitterSeconds * 1000);
    }
    nextRun.set(executor.schedule(this::runRefreshAndReschedule, delayMs, TimeUnit.MILLISECONDS));
  }

  /** Run refresh, then schedule next run only after this one completes (fixed-delay; no overlap). */
  private void runRefreshAndReschedule() {
    runRefreshSafely("scheduled");
    int period = intervalSeconds;
    int jitter = jitterSeconds;
    org.olo.configuration.Configuration c = ConfigurationProvider.get();
    if (c != null) {
      period = c.getInteger(REFRESH_INTERVAL_SECONDS_KEY, intervalSeconds);
      period = Math.max(1, period);
      jitter = c.getInteger(REFRESH_INTERVAL_JITTER_SECONDS_KEY, jitterSeconds);
      jitter = Math.max(0, jitter);
    }
    scheduleNext(period, jitter);
  }

  public void stop() {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  /** Triggers an immediate config refresh (e.g. after tenant region mapping changes). Safe to call from other threads. */
  public void triggerRefresh() {
    triggerRefresh("event");
  }

  /**
   * Triggers refresh and annotates the trigger source (e.g. pubsub).
   * Debounce: if trigger is "pubsub" and {@value #REFRESH_PUBSUB_DEBOUNCE_MS_KEY} &gt; 0, the refresh is delayed
   * so multiple events in quick succession coalesce into one run. If a refresh is already running, this trigger is ignored.
   */
  public void triggerRefresh(String trigger) {
    if (executor.isShutdown()) return;
    String reason = (trigger == null || trigger.isBlank()) ? "event" : trigger.trim();

    if ("pubsub".equalsIgnoreCase(reason)) {
      int debounceMs = getPubSubDebounceMs();
      if (debounceMs > 0) {
        scheduleDebouncedPubSubRefresh(debounceMs);
        return;
      }
    }

    executor.execute(() -> runRefreshSafely(reason));
  }

  private int getPubSubDebounceMs() {
    org.olo.configuration.Configuration c = ConfigurationProvider.get();
    if (c == null) return DEFAULT_PUBSUB_DEBOUNCE_MS;
    return c.getInteger(REFRESH_PUBSUB_DEBOUNCE_MS_KEY, DEFAULT_PUBSUB_DEBOUNCE_MS);
  }

  /** Coalesce multiple Pub/Sub events: cancel any pending debounced run and schedule one after delay. */
  private void scheduleDebouncedPubSubRefresh(int delayMs) {
    ScheduledFuture<?> prev = debouncedPubSubRun.getAndSet(null);
    if (prev != null) prev.cancel(false);
    ScheduledFuture<?> f = executor.schedule(() -> {
      try {
        runRefreshSafely("pubsub");
      } finally {
        debouncedPubSubRun.set(null);
      }
    }, delayMs, TimeUnit.MILLISECONDS);
    if (!debouncedPubSubRun.compareAndSet(null, f)) {
      f.cancel(false);
    }
  }

  private void runRefreshSafely(String reason) {
    if (!refreshRunning.compareAndSet(false, true)) {
      log.debug("Skipping config refresh trigger (already running): reason={}", reason);
      return;
    }
    try {
      runRefresh();
    } finally {
      refreshRunning.set(false);
    }
  }

  void runRefresh() {
    if (store != null) {
      RedisSnapshotLoader.refreshIfNeeded(store);
      return;
    }
    ConfigurationSnapshot currentSnapshot = ConfigurationProvider.getSnapshot();
    if (currentSnapshot != null) {
      refreshSnapshot(currentSnapshot);
    }
  }

  /** Fallback when no shared store: create client per refresh (avoid in worker; use shared store from bootstrap). */
  private void refreshSnapshot(ConfigurationSnapshot current) {
    String redisUri = buildRedisUriFromSnapshot(current);
    if (redisUri.isEmpty()) return;
    var factory = ConfigurationPortRegistry.snapshotStoreFactory();
    if (factory == null) {
      log.warn("Snapshot store factory is not registered; skipping fallback refresh");
      return;
    }
    try {
      ConfigurationSnapshotStore fallbackStore = factory.create(new CacheConnectionSettings(redisUri));
      if (fallbackStore == null) return;
      RedisSnapshotLoader.refreshIfNeeded(fallbackStore);
      closeIfNeeded(fallbackStore);
    } catch (Exception e) {
      log.warn("Config snapshot fallback refresh failed: {}", e.getMessage());
    }
  }

  private static void closeIfNeeded(Object maybeCloseable) {
    if (maybeCloseable instanceof AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Exception ignore) {
        // best-effort close
      }
    }
  }

  private static String buildRedisUriFromSnapshot(ConfigurationSnapshot s) {
    Map<String, String> g = s.getGlobalConfig();
    String uri = g.get("olo.redis.uri");
    if (uri != null && !uri.isBlank()) return uri.trim();
    String host = g.get("olo.redis.host");
    if (host == null || host.isBlank()) return "";
    int port = parseInt(g.get("olo.redis.port"), 6379);
    String password = g.get("olo.redis.password");
    if (password == null || password.isBlank()) return "redis://" + host.trim() + ":" + port;
    return "redis://:" + password + "@" + host.trim() + ":" + port;
  }

  private static int parseInt(String s, int def) {
    if (s == null || s.isBlank()) return def;
    try {
      return Integer.parseInt(s.trim());
    } catch (NumberFormatException e) {
      return def;
    }
  }
}
