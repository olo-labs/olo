/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.input.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum StorageMode {
    LOCAL,
    CACHE,
    S3,
    DB;

    @JsonValue
    public String toValue() {
        return name();
    }
}
