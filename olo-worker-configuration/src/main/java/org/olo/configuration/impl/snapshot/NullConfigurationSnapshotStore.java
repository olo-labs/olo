/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.impl.snapshot;

import org.olo.configuration.snapshot.ConfigurationSnapshot;
import org.olo.configuration.snapshot.ConfigurationSnapshotStore;
import org.olo.configuration.snapshot.SnapshotMetadata;

/**
 * No-op store when Redis is not configured. Always returns null; put is a no-op.
 */
public final class NullConfigurationSnapshotStore implements ConfigurationSnapshotStore {

  @Override
  public ConfigurationSnapshot getSnapshot(String region) {
    return null;
  }

  @Override
  public SnapshotMetadata getMeta(String region) {
    return null;
  }

  @Override
  public void put(String region, ConfigurationSnapshot snapshot) {}
}
