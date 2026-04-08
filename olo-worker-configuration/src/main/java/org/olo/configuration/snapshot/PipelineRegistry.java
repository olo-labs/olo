/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.snapshot;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of pipeline configs: pipeline id → config. Section of {@link CompositeConfigurationSnapshot};
 * loaded from Redis key {@value ConfigurationSnapshotStore#PIPELINES_KEY_PREFIX}&lt;region&gt;.
 * <p>
 * Redis may store the v2 envelope {@code { "pipelines": { "id": document } }} (logic-only layer); the registry
 * exposes the inner map for runtime. Legacy flat {@code { "id": document }} is still supported.
 * </p>
 */
public interface PipelineRegistry extends Map<String, Object> {

  /**
   * If {@code map} is the v2 shape {@code { "pipelines": { id → json } }}, returns the inner map; otherwise returns
   * {@code map} (legacy id → document at top level).
   */
  static Map<String, Object> unwrapPipelinesRoot(Map<String, Object> map) {
    if (map == null) {
      return Map.of();
    }
    if (map.size() == 1 && map.containsKey("pipelines")) {
      Object inner = map.get("pipelines");
      if (inner instanceof Map<?, ?> im) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : im.entrySet()) {
          if (e.getKey() != null) {
            out.put(String.valueOf(e.getKey()), e.getValue());
          }
        }
        return out;
      }
    }
    return map;
  }

  /** Wraps a map as a PipelineRegistry (immutable copy via Map.copyOf). Unwraps v2 Redis envelope when present. */
  static PipelineRegistry of(Map<String, Object> map) {
    Map<String, Object> effective = unwrapPipelinesRoot(map);
    Map<String, Object> m = (effective == null || effective.isEmpty()) ? Map.of() : Map.copyOf(effective);
    return new PipelineRegistryView(m);
  }

  final class PipelineRegistryView extends java.util.AbstractMap<String, Object> implements PipelineRegistry {
    private final Map<String, Object> delegate;
    PipelineRegistryView(Map<String, Object> delegate) { this.delegate = delegate; }
    @Override public java.util.Set<Entry<String, Object>> entrySet() { return delegate.entrySet(); }
    @Override public Object get(Object key) { return delegate.get(key); }
  }
}
