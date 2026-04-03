/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.port;

import org.olo.configuration.region.TenantRegionCache;

/**
 * Creates cache adapter used by tenant-region resolution.
 */
public interface TenantRegionCacheFactory {

  TenantRegionCache create(CacheConnectionSettings cacheSettings);
}
