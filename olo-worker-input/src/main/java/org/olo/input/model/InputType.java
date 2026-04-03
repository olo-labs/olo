/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.input.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum InputType {
    STRING,
    NUMBER,
    BOOLEAN,
    FILE,
    JSON,
    OBJECT;

    @JsonValue
    public String toValue() {
        return name();
    }
}
