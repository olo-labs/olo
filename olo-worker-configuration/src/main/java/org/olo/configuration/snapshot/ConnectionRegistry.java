/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.snapshot;

import java.util.Map;

/**
 * Registry of connection configs: connection id → config. Section of {@link CompositeConfigurationSnapshot};
 * loaded from Redis key {@value ConfigurationSnapshotStore#CONNECTIONS_KEY_PREFIX}&lt;region&gt;.
 */
public interface ConnectionRegistry extends Map<String, Object> {

  /** Wraps a map as a ConnectionRegistry (immutable copy via Map.copyOf). */
  static ConnectionRegistry of(Map<String, Object> map) {
    Map<String, Object> m = (map == null || map.isEmpty()) ? Map.of() : Map.copyOf(map);
    return new ConnectionRegistryView(m);
  }

  final class ConnectionRegistryView extends java.util.AbstractMap<String, Object> implements ConnectionRegistry {
    private final Map<String, Object> delegate;
    ConnectionRegistryView(Map<String, Object> delegate) { this.delegate = delegate; }
    @Override public java.util.Set<Entry<String, Object>> entrySet() { return delegate.entrySet(); }
    @Override public Object get(Object key) { return delegate.get(key); }
  }
}
