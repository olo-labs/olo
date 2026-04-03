/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.input.consumer;

import java.util.Optional;

/**
 * Abstraction for reading a value from cache (e.g. Redis) by key.
 * The consumer (worker) provides an implementation; the input module only depends on this contract.
 */
public interface CacheReader {

    /**
     * Fetches the value for the given cache key.
     *
     * @param key cache key (e.g. "olo:worker:transactionId:input:input2")
     * @return the value, or empty if not found or on error
     */
    Optional<String> get(String key);
}
