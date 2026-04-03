/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.impl.snapshot;

import org.olo.configuration.snapshot.ConfigurationSnapshot;

import java.util.Map;

/**
 * Result of a pipelined Redis read: core, pipelines, connections (no meta).
 * Used when meta was already fetched and versions changed; avoids re-fetching meta.
 */
public final class CorePipelinesConnections {

  private final ConfigurationSnapshot core;
  private final Map<String, Object> pipelines;
  private final Map<String, Object> connections;

  public CorePipelinesConnections(ConfigurationSnapshot core,
                                  Map<String, Object> pipelines,
                                  Map<String, Object> connections) {
    this.core = core;
    this.pipelines = pipelines;
    this.connections = connections;
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
