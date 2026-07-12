/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.port;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Canvas side where a port handle is rendered in Studio.
 */
public enum PortUiPosition {

    LEFT,
    RIGHT,
    TOP,
    BOTTOM;

    public static PortUiPosition defaultFor(PortDirection direction) {
        return direction == PortDirection.INPUT ? LEFT : RIGHT;
    }

    @JsonValue
    public String value() {
        return name();
    }

    @JsonCreator
    public static PortUiPosition fromValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return valueOf(raw.trim().toUpperCase());
    }
}
