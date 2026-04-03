/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.snapshot;

/**
 * Loads full configuration snapshot from DB (source of truth). Global + per-tenant.
 */
public interface ConfigurationSnapshotRepository {

  /**
   * Loads snapshot for the given region. Region is used for logging/filtering; global and tenant data come from DB.
   */
  ConfigurationSnapshot load(String region);
}
