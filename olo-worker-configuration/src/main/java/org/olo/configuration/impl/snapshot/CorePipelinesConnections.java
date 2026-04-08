/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.impl.snapshot;

import org.olo.configuration.snapshot.ConfigurationSnapshot;

import java.util.Map;

/**
 * Result of a pipelined Redis read: core, pipelines, connections, queues, profiles (no meta).
 * Used when meta was already fetched and versions changed; avoids re-fetching meta.
 */
public final class CorePipelinesConnections {

  private final ConfigurationSnapshot core;
  private final Map<String, Object> pipelines;
  private final Map<String, Object> connections;
  private final Map<String, Object> queues;
  private final Map<String, Object> profiles;

  public CorePipelinesConnections(ConfigurationSnapshot core,
                                  Map<String, Object> pipelines,
                                  Map<String, Object> connections,
                                  Map<String, Object> queues,
                                  Map<String, Object> profiles) {
    this.core = core;
    this.pipelines = pipelines;
    this.connections = connections;
    this.queues = queues;
    this.profiles = profiles;
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
