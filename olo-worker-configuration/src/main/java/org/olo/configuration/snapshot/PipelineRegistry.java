/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.snapshot;

import java.util.Map;

/**
 * Registry of pipeline configs: pipeline id → config. Section of {@link CompositeConfigurationSnapshot};
 * loaded from Redis key {@value ConfigurationSnapshotStore#PIPELINES_KEY_PREFIX}&lt;region&gt;.
 */
public interface PipelineRegistry extends Map<String, Object> {

  /** Wraps a map as a PipelineRegistry (immutable copy via Map.copyOf). */
  static PipelineRegistry of(Map<String, Object> map) {
    Map<String, Object> m = (map == null || map.isEmpty()) ? Map.of() : Map.copyOf(map);
    return new PipelineRegistryView(m);
  }

  final class PipelineRegistryView extends java.util.AbstractMap<String, Object> implements PipelineRegistry {
    private final Map<String, Object> delegate;
    PipelineRegistryView(Map<String, Object> delegate) { this.delegate = delegate; }
    @Override public java.util.Set<Entry<String, Object>> entrySet() { return delegate.entrySet(); }
    @Override public Object get(Object key) { return delegate.get(key); }
  }
}
