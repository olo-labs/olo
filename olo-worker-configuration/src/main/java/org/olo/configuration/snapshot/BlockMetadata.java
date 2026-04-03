/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.snapshot;

import java.time.Instant;

/**
 * Version and lastUpdated for one metadata block (e.g. core, pipelines, connections).
 * Stored under {@code olo:configuration:meta:<region>} as multiple named blocks.
 */
public final class BlockMetadata {

  private final long version;
  private final Instant lastUpdated;

  public BlockMetadata(long version, Instant lastUpdated) {
    this.version = version;
    this.lastUpdated = lastUpdated == null ? Instant.EPOCH : lastUpdated;
  }

  public long getVersion() {
    return version;
  }

  public Instant getLastUpdated() {
    return lastUpdated;
  }
}
