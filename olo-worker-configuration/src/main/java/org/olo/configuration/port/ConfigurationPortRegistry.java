/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.port;

/**
 * Registry for infrastructure factories used by configuration module.
 * Runtime assembly (for example in olo-worker) can register implementations
 * from separate modules such as olo-worker-db and olo-worker-cache.
 */
public final class ConfigurationPortRegistry {

  private static volatile ConfigurationSnapshotStoreFactory snapshotStoreFactory;
  private static volatile TenantRegionCacheFactory tenantRegionCacheFactory;
  private static volatile TenantRegionRepositoryFactory tenantRegionRepositoryFactory;
  private static volatile ConfigurationSnapshotRepositoryFactory snapshotRepositoryFactory;
  private static volatile ConfigChangeSubscriberFactory configChangeSubscriberFactory;
  private static volatile DbClientInitializer dbClientInitializer;

  private ConfigurationPortRegistry() {}

  public static void registerSnapshotStoreFactory(ConfigurationSnapshotStoreFactory factory) {
    snapshotStoreFactory = factory;
  }

  public static void registerTenantRegionCacheFactory(TenantRegionCacheFactory factory) {
    tenantRegionCacheFactory = factory;
  }

  public static void registerTenantRegionRepositoryFactory(TenantRegionRepositoryFactory factory) {
    tenantRegionRepositoryFactory = factory;
  }

  public static void registerSnapshotRepositoryFactory(ConfigurationSnapshotRepositoryFactory factory) {
    snapshotRepositoryFactory = factory;
  }

  public static void registerConfigChangeSubscriberFactory(ConfigChangeSubscriberFactory factory) {
    configChangeSubscriberFactory = factory;
  }

  public static void registerDbClientInitializer(DbClientInitializer initializer) {
    dbClientInitializer = initializer;
  }

  public static ConfigurationSnapshotStoreFactory snapshotStoreFactory() {
    return snapshotStoreFactory;
  }

  public static TenantRegionCacheFactory tenantRegionCacheFactory() {
    return tenantRegionCacheFactory;
  }

  public static TenantRegionRepositoryFactory tenantRegionRepositoryFactory() {
    return tenantRegionRepositoryFactory;
  }

  public static ConfigurationSnapshotRepositoryFactory snapshotRepositoryFactory() {
    return snapshotRepositoryFactory;
  }

  public static ConfigChangeSubscriberFactory configChangeSubscriberFactory() {
    return configChangeSubscriberFactory;
  }

  public static DbClientInitializer dbClientInitializer() {
    return dbClientInitializer;
  }
}
