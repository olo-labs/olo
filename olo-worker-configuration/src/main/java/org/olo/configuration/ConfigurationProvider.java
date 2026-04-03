/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration;

import org.olo.configuration.impl.config.SnapshotConfiguration;
import org.olo.configuration.region.TenantRegionResolver;
import org.olo.configuration.snapshot.CompositeConfigurationSnapshot;
import org.olo.configuration.snapshot.ConfigurationSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Holder for the current configuration. Runtime path: multiple region snapshots
 * ({@code Map<Region, CompositeConfigurationSnapshot>}). Resolution uses tenant region:
 * {@code tenantRegion = TenantRegionResolver.getRegion(tenantId); snapshot = snapshotMap.get(tenantRegion)}.
 * When Redis is not configured, a {@link Configuration} is set directly (defaults + env).
 * <p><strong>Immutable replacement:</strong> The loader always builds new composites and assigns the map in one volatile write.
 * Workers never see half-old section combinations.
 * </p>
 */
public final class ConfigurationProvider {

  /** Volatile: region → sectioned snapshot. Null when using defaults-only (no Redis). */
  private static volatile Map<String, CompositeConfigurationSnapshot> snapshotByRegion;
  /** Primary (worker) region used for {@link #get()} when no tenant context. */
  private static volatile String primaryRegion;
  /** Volatile: defaults + env only; set when Redis is not configured. Null when snapshot map is set. */
  private static volatile Configuration defaultConfiguration;

  /** Regions configured at bootstrap from olo.region (defaults + env). Used for dump and execution tree scope. */
  private static volatile List<String> configuredRegions;

  /** Listeners notified when a region's composite is installed or removed (e.g. so worker can rebuild execution tree). */
  private static final CopyOnWriteArrayList<BiConsumer<String, CompositeConfigurationSnapshot>> snapshotChangeListeners = new CopyOnWriteArrayList<>();

  private static final Logger log = LoggerFactory.getLogger(ConfigurationProvider.class);

  private ConfigurationProvider() {}

  /**
   * Registers a listener invoked whenever a region's snapshot is set or removed.
   * Invoked with (region, composite); composite is null when the region is removed.
   * Called after {@link #putComposite} and for each region when {@link #setSnapshotMap} is called.
   */
  public static void addSnapshotChangeListener(BiConsumer<String, CompositeConfigurationSnapshot> listener) {
    if (listener != null) snapshotChangeListeners.add(listener);
  }

  /** Sets the flat configuration (defaults + env, no Redis). */
  public static void set(Configuration configuration) {
    defaultConfiguration = configuration;
    snapshotByRegion = null;
    primaryRegion = null;
  }

  /** Sets the list of regions this worker was configured to serve (from olo.region at bootstrap). */
  public static void setConfiguredRegions(List<String> regions) {
    configuredRegions = regions == null ? null : Collections.unmodifiableList(regions);
  }

  /** Returns the bootstrap-configured region list; empty if never set. */
  public static List<String> getConfiguredRegions() {
    List<String> r = configuredRegions;
    return r == null ? List.of() : r;
  }

  /**
   * Sets the snapshot by wrapping it in a composite (core only). Use after first load of worker region so {@link #get()} works.
   */
  public static void setSnapshot(ConfigurationSnapshot s) {
    defaultConfiguration = null;
    if (s == null) {
      snapshotByRegion = null;
      primaryRegion = null;
      return;
    }
    CompositeConfigurationSnapshot c = new CompositeConfigurationSnapshot(s.getRegion());
    c.setCore(s, s.getVersion());
    snapshotByRegion = Collections.singletonMap(s.getRegion(), c);
    primaryRegion = s.getRegion();
    notifySnapshotChange(c.getRegion(), c);
  }

  /** Sets the sectioned snapshot for a single region (used during bootstrap before multi-region load). */
  public static void setComposite(CompositeConfigurationSnapshot c) {
    defaultConfiguration = null;
    if (c == null) {
      snapshotByRegion = null;
      primaryRegion = null;
      return;
    }
    snapshotByRegion = Collections.singletonMap(c.getRegion(), c);
    primaryRegion = c.getRegion();
    notifySnapshotChange(c.getRegion(), c);
  }

  /**
   * Sets the multi-region snapshot map. Worker can process tenants from any of these regions;
   * resolution is by {@link TenantRegionResolver#getRegion(String)}.
   */
  public static void setSnapshotMap(Map<String, CompositeConfigurationSnapshot> map, String primaryRegionValue) {
    defaultConfiguration = null;
    if (map == null || map.isEmpty()) {
      snapshotByRegion = null;
      primaryRegion = null;
      return;
    }
    snapshotByRegion = new ConcurrentHashMap<>(map);
    primaryRegion = primaryRegionValue != null && !primaryRegionValue.isBlank()
        ? primaryRegionValue.trim()
        : map.keySet().iterator().next();
    for (Map.Entry<String, CompositeConfigurationSnapshot> e : map.entrySet()) {
      notifySnapshotChange(e.getKey(), e.getValue());
    }
  }

  /** Returns the sectioned snapshot for the primary (worker) region, or null if using defaults-only. */
  public static CompositeConfigurationSnapshot getComposite() {
    return getComposite(primaryRegion);
  }

  /** Returns the sectioned snapshot for the given region, or null if absent. */
  public static CompositeConfigurationSnapshot getComposite(String region) {
    Map<String, CompositeConfigurationSnapshot> map = snapshotByRegion;
    if (map == null || region == null) return null;
    return map.get(region);
  }

  /** Returns an immutable copy of the current region → composite map; null if using defaults-only. */
  public static Map<String, CompositeConfigurationSnapshot> getSnapshotMap() {
    Map<String, CompositeConfigurationSnapshot> map = snapshotByRegion;
    return map == null ? null : Map.copyOf(map);
  }

  /** Returns the current configuration view (global) from the primary region. Never null after bootstrap. */
  public static Configuration get() {
    Map<String, CompositeConfigurationSnapshot> map = snapshotByRegion;
    if (map != null && primaryRegion != null) {
      CompositeConfigurationSnapshot s = map.get(primaryRegion);
      if (s != null) {
        return SnapshotConfiguration.global(s.getCore());
      }
    }
    return defaultConfiguration;
  }

  /** Returns the current configuration; throws if not set. */
  public static Configuration require() {
    Configuration c = get();
    if (c == null) {
      throw new IllegalStateException("Configuration not set. Call Bootstrap.run() or loadAndSetDefault() at bootstrap.");
    }
    return c;
  }

  /** Returns the current core snapshot for the primary region, or null if using defaults-only. */
  public static ConfigurationSnapshot getSnapshot() {
    return getSnapshot(primaryRegion);
  }

  /** Returns the core snapshot for the given region, or null if absent. */
  public static ConfigurationSnapshot getSnapshot(String region) {
    CompositeConfigurationSnapshot c = getComposite(region);
    return c != null ? c.getCore() : null;
  }

  /**
   * Configuration for a tenant. Resolves tenant's region via {@link TenantRegionResolver#getRegion(String)}
   * and returns config from that region's snapshot. Use {@code ConfigurationProvider.forTenant(tenantId)}.
   */
  public static Configuration forTenant(String tenantId) {
    return forContext(tenantId, null);
  }

  /**
   * Configuration for context (global → region → tenant → resource). Resolves by tenant region, then
   * uses resource IDs in type:name form (e.g. pipeline:chat, connection:openai).
   */
  public static Configuration forContext(String tenantId, String resourceId) {
    if (tenantId == null || tenantId.isBlank()) {
      return get();
    }
    String tenantRegion = TenantRegionResolver.getRegion(tenantId);
    if (tenantRegion == null || tenantRegion.isBlank()) {
      tenantRegion = Regions.DEFAULT_REGION;
    }
    CompositeConfigurationSnapshot composite = getComposite(tenantRegion);
    if (composite == null) {
      return get();
    }
    return SnapshotConfiguration.forContext(composite.getCore(), tenantId, resourceId);
  }

  /** Updates the composite for a single region (used by refresh). Replaces the map with a copy containing the update. */
  public static void putComposite(String region, CompositeConfigurationSnapshot composite) {
    Map<String, CompositeConfigurationSnapshot> map = snapshotByRegion;
    if (map == null || region == null) return;
    Map<String, CompositeConfigurationSnapshot> next = new ConcurrentHashMap<>(map);
    if (composite != null) {
      next.put(region, composite);
    } else {
      next.remove(region);
    }
    snapshotByRegion = next;
    notifySnapshotChange(region, composite);
  }

  private static void notifySnapshotChange(String region, CompositeConfigurationSnapshot composite) {
    for (BiConsumer<String, CompositeConfigurationSnapshot> listener : snapshotChangeListeners) {
      try {
        listener.accept(region, composite);
      } catch (Exception e) {
        log.warn("Snapshot change listener threw for region={}: {}", region, e.getMessage());
      }
    }
  }
}
