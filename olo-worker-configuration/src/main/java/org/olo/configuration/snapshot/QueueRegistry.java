/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.snapshot;

import java.util.Map;

/**
 * Registry of queue configs: queue id → config. Section of {@link CompositeConfigurationSnapshot};
 * loaded from Redis key {@value ConfigurationSnapshotStore#QUEUES_KEY_PREFIX}&lt;region&gt;.
 */
public interface QueueRegistry extends Map<String, Object> {

  /** Wraps a map as a QueueRegistry (immutable copy via Map.copyOf). */
  static QueueRegistry of(Map<String, Object> map) {
    Map<String, Object> m = (map == null || map.isEmpty()) ? Map.of() : Map.copyOf(map);
    return new QueueRegistryView(m);
  }

  final class QueueRegistryView extends java.util.AbstractMap<String, Object> implements QueueRegistry {
    private final Map<String, Object> delegate;
    QueueRegistryView(Map<String, Object> delegate) { this.delegate = delegate; }
    @Override public java.util.Set<Entry<String, Object>> entrySet() { return delegate.entrySet(); }
    @Override public Object get(Object key) { return delegate.get(key); }
  }
}
