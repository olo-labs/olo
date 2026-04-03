/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.port;

import org.olo.configuration.region.TenantRegionRepository;

/**
 * Creates DB adapter used by tenant-region resolution.
 */
public interface TenantRegionRepositoryFactory {

  TenantRegionRepository create(DbConnectionSettings dbSettings);
}
