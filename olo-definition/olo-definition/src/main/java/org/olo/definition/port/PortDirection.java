/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.port;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Whether a node port accepts data ({@link #INPUT}) or emits data ({@link #OUTPUT}).
 */
public enum PortDirection {

    INPUT("INPUT"),
    OUTPUT("OUTPUT");

    private final String value;

    PortDirection(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static PortDirection fromValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (PortDirection direction : values()) {
            if (direction.value.equalsIgnoreCase(raw.trim())) {
                return direction;
            }
        }
        throw new IllegalArgumentException("Unknown port direction: " + raw);
    }
}
