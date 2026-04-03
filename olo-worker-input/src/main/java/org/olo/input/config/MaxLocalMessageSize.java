/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.input.config;

/**
 * Reads the max allowed size for inline LOCAL string values from the environment.
 * Values larger than this should be stored in cache (e.g. Redis) and the key shared in the payload.
 */
public final class MaxLocalMessageSize {

    private static final String ENV_MAX_LOCAL_MESSAGE_SIZE = "OLO_MAX_LOCAL_MESSAGE_SIZE";
    private static final int DEFAULT = 50;

    private MaxLocalMessageSize() {
    }

    /**
     * Returns the max local message size from {@code OLO_MAX_LOCAL_MESSAGE_SIZE}, or 50 if unset or invalid.
     */
    public static int fromEnvironment() {
        String v = System.getenv(ENV_MAX_LOCAL_MESSAGE_SIZE);
        if (v == null || v.isBlank()) {
            return DEFAULT;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return DEFAULT;
        }
    }

    /**
     * Returns the default max local message size (50).
     */
    public static int getDefault() {
        return DEFAULT;
    }
}
