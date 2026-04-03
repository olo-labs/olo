/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.port;

import org.olo.configuration.snapshot.ConfigurationSnapshotStore;

/**
 * Creates a distributed snapshot store adapter from cache connection settings.
 */
public interface ConfigurationSnapshotStoreFactory {

  ConfigurationSnapshotStore create(CacheConnectionSettings cacheSettings);
}
