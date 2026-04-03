/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.region;

import java.util.Map;

/**
 * Loads and updates tenant ID → region mapping from DB table {@code olo_configuration_region}.
 */
public interface TenantRegionRepository {

  /**
   * Returns all tenant_id → region pairs. Empty map on error.
   */
  Map<String, String> findAll();

  /**
   * Updates the region for a tenant. Sets {@code updated_at = CURRENT_TIMESTAMP}.
   * Use after admin changes; then update Redis cache and trigger worker config refresh.
   *
   * @param tenantId tenant ID (max 64 chars)
   * @param region   region (max 64 chars, e.g. default, us-east, eu-west)
   */
  void updateRegion(String tenantId, String region);
}
