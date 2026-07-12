/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.path;

/**
 * Root namespace for a {@link DataPath}.
 */
public enum PathRoot {
    /** Shared mutable workflow state ({@code state.symbol}, {@code state.analysis.score}). */
    STATE("state"),
    /** Invocation inputs ({@code input.symbol}). */
    INPUT("input"),
    /** Runtime parameters ({@code parameter.temperature}). */
    PARAMETER("parameter");

    private final String prefix;

    PathRoot(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }

    public static PathRoot fromPrefix(String prefix) {
        if (prefix == null) {
            return null;
        }
        for (PathRoot root : values()) {
            if (root.prefix.equalsIgnoreCase(prefix)) {
                return root;
            }
        }
        return null;
    }
}
