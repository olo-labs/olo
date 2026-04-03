/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.input.producer;

/**
 * Abstraction for writing a value to cache (e.g. Redis) by key.
 * The producer provides an implementation; the input module uses this when a value exceeds {@code OLO_MAX_LOCAL_MESSAGE_SIZE}.
 */
public interface CacheWriter {

    /**
     * Stores the value under the given cache key.
     *
     * @param key   cache key (e.g. from {@link InputStorageKeys#cacheKey(String, String)})
     * @param value the string value to store
     */
    void put(String key, String value);
}
