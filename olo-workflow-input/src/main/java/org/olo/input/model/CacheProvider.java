/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
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
