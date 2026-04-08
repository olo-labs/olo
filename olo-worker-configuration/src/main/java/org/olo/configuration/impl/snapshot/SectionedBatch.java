/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.impl.snapshot;

import org.olo.configuration.snapshot.ConfigurationSnapshot;
import org.olo.configuration.snapshot.SnapshotMetadata;

import java.util.Map;

/**
 * Result of a single pipelined Redis read: meta, core, pipelines, connections, queues, profiles.
 * Used during refresh to reduce round trips (one pipeline instead of many sequential GETs).
 */
public final class SectionedBatch {

  private final SnapshotMetadata meta;
  private final ConfigurationSnapshot core;
  private final Map<String, Object> pipelines;
  private final Map<String, Object> connections;
  private final Map<String, Object> queues;
  private final Map<String, Object> profiles;

  public SectionedBatch(SnapshotMetadata meta, ConfigurationSnapshot core,
                        Map<String, Object> pipelines, Map<String, Object> connections,
                        Map<String, Object> queues, Map<String, Object> profiles) {
    this.meta = meta;
    this.core = core;
    this.pipelines = pipelines;
    this.connections = connections;
    this.queues = queues;
    this.profiles = profiles;
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

  public Map<String, Object> getQueues() {
    return queues;
  }

  public Map<String, Object> getProfiles() {
    return profiles;
  }
}
