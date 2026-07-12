/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.path;

import java.util.Optional;

/**
 * Result of {@link DataPathParser#parse(String)}.
 */
public final class DataPathParseResult {

    private final DataPath path;
    private final String error;

    private DataPathParseResult(DataPath path, String error) {
        this.path = path;
        this.error = error;
    }

    public static DataPathParseResult success(DataPath path) {
        return new DataPathParseResult(path, null);
    }

    public static DataPathParseResult failure(String error) {
        return new DataPathParseResult(null, error);
    }

    public boolean isSuccess() {
        return path != null;
    }

    public Optional<DataPath> path() {
        return Optional.ofNullable(path);
    }

    public Optional<String> error() {
        return Optional.ofNullable(error);
    }
}
