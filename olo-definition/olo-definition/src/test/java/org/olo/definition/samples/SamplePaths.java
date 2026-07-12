/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.samples;

import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves the on-disk {@code samples/} root for generator and validation tests. */
final class SamplePaths {

    private SamplePaths() {
    }

    static Path resolveSamplesRoot() {
        String property = System.getProperty("olo.samples.dir");
        if (property != null && !property.isBlank()) {
            return Path.of(property).normalize().toAbsolutePath();
        }
        for (String candidate : new String[] {"samples", "../samples"}) {
            Path path = Path.of(candidate).normalize().toAbsolutePath();
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        return Path.of("samples").toAbsolutePath();
    }
}
