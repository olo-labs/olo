/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.impl.snapshot;

import org.olo.configuration.port.ConfigurationPortRegistry;
import org.olo.configuration.port.DbConnectionSettings;
import org.olo.configuration.snapshot.ConfigurationSnapshot;
import org.olo.configuration.snapshot.ConfigurationSnapshotRepository;
import org.olo.configuration.snapshot.ConfigurationSnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Admin service only: builds a region snapshot from DB table {@code olo_config_resource}, then stores it in Redis.
 * Flow: DB → build snapshot → write Redis snapshot → increment version.
 * Workers never touch DB; they only read defaults → env → Redis snapshot. Use this after Admin API updates DB.
 */
public final class ConfigSnapshotBuilder {

  private static final Logger log = LoggerFactory.getLogger(ConfigSnapshotBuilder.class);

  private final ConfigurationSnapshotRepository repository;
  private final ConfigurationSnapshotStore store;

  public ConfigSnapshotBuilder(ConfigurationSnapshotRepository repository, ConfigurationSnapshotStore store) {
    this.repository = repository;
    this.store = store;
  }

  /**
   * Uses registered DB factory from {@link ConfigurationPortRegistry} to create repository.
   */
  public ConfigSnapshotBuilder(DbConnectionSettings dbSettings, ConfigurationSnapshotStore store) {
    var factory = ConfigurationPortRegistry.snapshotRepositoryFactory();
    if (factory == null) {
      throw new IllegalStateException("Snapshot repository factory is not registered");
    }
    this.repository = factory.create(dbSettings);
    this.store = store;
  }

  /**
   * Loads snapshot for the region from DB, stores in Redis. Returns the snapshot (or null on error).
   */
  public ConfigurationSnapshot buildAndStore(String region) {
    ConfigurationSnapshot snapshot = repository.load(region);
    if (snapshot != null) {
      store.put(region, snapshot);
      log.info("Built and stored config snapshot for region={} version={}", region, snapshot.getVersion());
    }
    return snapshot;
  }
}
