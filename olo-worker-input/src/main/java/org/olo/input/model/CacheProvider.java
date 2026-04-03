/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.input.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum CacheProvider {
    REDIS,
    IN_MEMORY;

    @JsonValue
    public String toValue() {
        return name();
    }
}
