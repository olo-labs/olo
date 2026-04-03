/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.impl.region;

import org.olo.configuration.ConfigurationProvider;
import org.olo.configuration.impl.refresh.ConfigRefreshScheduler;
import org.olo.configuration.region.TenantRegionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically reloads tenant→region mapping from Redis/DB so that tenant region moves
 * are picked up without worker restart. Use when tenants might move regions.
 * <p>
 * Config key: {@value #REFRESH_INTERVAL_SECONDS_KEY}. If 0 or unset, refresh is disabled (bootstrap load only).
 * </p>
 * <p>
 * <strong>Explicit rule when a tenant changes region:</strong> (1) TenantRegionResolver refresh runs,
 * (2) ConfigRefreshScheduler is immediately triggered, (3) new region snapshot is loaded. Otherwise
 * temporary config mismatch can occur (tenant points to new region but worker still has old region's
 * config). This scheduler does (1) then calls {@code configRefreshScheduler.triggerRefresh()} for (2) and (3).
 * </p>
 */
public final class TenantRegionRefreshScheduler {

  private static final Logger log = LoggerFactory.getLogger(TenantRegionRefreshScheduler.class);

  /** Config key for refresh interval in seconds. 0 = disabled. */
  public static final String REFRESH_INTERVAL_SECONDS_KEY = "olo.tenant.region.refresh.interval.seconds";

  private final ScheduledExecutorService executor;
  private final int intervalSeconds;
  private final ConfigRefreshScheduler configRefreshScheduler;

  /**
   * @param intervalSeconds interval in seconds; must be &gt; 0 to run refresh
   */
  public TenantRegionRefreshScheduler(int intervalSeconds) {
    this(intervalSeconds, null);
  }

  /**
   * @param intervalSeconds interval in seconds; must be &gt; 0 to run refresh
   * @param configRefreshScheduler if non-null, config refresh is triggered after each successful region refresh (keeps region map and config in sync)
   */
  public TenantRegionRefreshScheduler(int intervalSeconds, ConfigRefreshScheduler configRefreshScheduler) {
    this.intervalSeconds = Math.max(1, intervalSeconds);
    this.configRefreshScheduler = configRefreshScheduler;
    this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "tenant-region-refresh");
      t.setDaemon(true);
      return t;
    });
  }

  public void start() {
    executor.scheduleAtFixedRate(this::runRefresh, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    log.info("Tenant region refresh scheduled every {} seconds", intervalSeconds);
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

  void runRefresh() {
    try {
      var config = ConfigurationProvider.get();
      if (config != null) {
        TenantRegionResolver.loadFrom(config);
        if (configRefreshScheduler != null) {
          configRefreshScheduler.triggerRefresh();
        }
      }
    } catch (Exception e) {
      log.warn("Tenant region refresh failed: {}", e.getMessage());
    }
  }
}
