/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.port;

import org.olo.configuration.snapshot.ConfigurationSnapshotRepository;

/**
 * Creates DB-backed snapshot repository used by admin snapshot building.
 */
public interface ConfigurationSnapshotRepositoryFactory {

  ConfigurationSnapshotRepository create(DbConnectionSettings dbSettings);
}
