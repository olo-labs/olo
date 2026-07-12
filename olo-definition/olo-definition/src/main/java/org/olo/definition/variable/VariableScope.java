/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.variable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;
import java.util.Optional;

/**
 * Visibility and mutability of a workflow variable at runtime.
 */
public enum VariableScope {
    LOCAL,
    READONLY_EXTERNAL,
    EXTERNAL,
    GLOBAL,
    CREDENTIAL;

    @JsonValue
    public String wireName() {
        return name();
    }

    @JsonCreator
    public static VariableScope fromValue(String value) {
        if (value == null || value.isBlank()) {
            return LOCAL;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (VariableScope scope : values()) {
            if (scope.name().equals(normalized)) {
                return scope;
            }
        }
        throw new IllegalArgumentException(
                "Unknown variable scope: " + value + " (allowed: LOCAL, READONLY_EXTERNAL, EXTERNAL, GLOBAL, CREDENTIAL)");
    }

    public static Optional<VariableScope> tryParse(String value) {
        try {
            return Optional.of(fromValue(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
