/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.input.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Cache storage details when {@link Storage#getMode()} is {@link StorageMode#CACHE}.
 */
public final class CacheStorage {

    private final CacheProvider provider;
    private final String key;

    @JsonCreator
    public CacheStorage(
            @JsonProperty("provider") CacheProvider provider,
            @JsonProperty("key") String key) {
        this.provider = provider;
        this.key = key;
    }

    public CacheProvider getProvider() {
        return provider;
    }

    public String getKey() {
        return key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CacheStorage that = (CacheStorage) o;
        return provider == that.provider && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, key);
    }
}
