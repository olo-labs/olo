/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.snapshot;

import java.util.Map;

/**
 * Standalone chat profiles document (root JSON from Redis key
 * {@value ConfigurationSnapshotStore#PROFILES_KEY_PREFIX}&lt;region&gt;), typically including
 * {@code profiles} and {@code profileOrder}. Section of {@link CompositeConfigurationSnapshot}.
 */
public interface ProfileRegistry extends Map<String, Object> {

  /** Wraps a map as a ProfileRegistry (immutable copy via Map.copyOf). */
  static ProfileRegistry of(Map<String, Object> map) {
    Map<String, Object> m = (map == null || map.isEmpty()) ? Map.of() : Map.copyOf(map);
    return new ProfileRegistryView(m);
  }

  final class ProfileRegistryView extends java.util.AbstractMap<String, Object> implements ProfileRegistry {
    private final Map<String, Object> delegate;
    ProfileRegistryView(Map<String, Object> delegate) { this.delegate = delegate; }
    @Override public java.util.Set<Entry<String, Object>> entrySet() { return delegate.entrySet(); }
    @Override public Object get(Object key) { return delegate.get(key); }
  }
}
