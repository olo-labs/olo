/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.impl.snapshot;

import org.olo.configuration.snapshot.ConfigurationSnapshot;
import org.olo.configuration.snapshot.SnapshotMetadata;

import java.util.Map;

/**
 * Result of a single pipelined Redis read: meta, core, pipelines, connections.
 * Used during refresh to reduce round trips (one pipeline instead of four sequential GETs).
 */
public final class SectionedBatch {

  private final SnapshotMetadata meta;
  private final ConfigurationSnapshot core;
  private final Map<String, Object> pipelines;
  private final Map<String, Object> connections;

  public SectionedBatch(SnapshotMetadata meta, ConfigurationSnapshot core,
                        Map<String, Object> pipelines, Map<String, Object> connections) {
    this.meta = meta;
    this.core = core;
    this.pipelines = pipelines;
    this.connections = connections;
  }

  public SnapshotMetadata getMeta() {
    return meta;
  }

  public ConfigurationSnapshot getCore() {
    return core;
  }

  public Map<String, Object> getPipelines() {
    return pipelines;
  }

  public Map<String, Object> getConnections() {
    return connections;
  }
}
