/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.configuration;

import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves the on-disk {@code olo-configuration/default/} root for generator and validation tests. */
final class DefaultConfigurationPaths {

    private DefaultConfigurationPaths() {
    }

    static Path resolveConfigurationRoot() {
        String property = System.getProperty("olo.configuration.dir");
        if (property != null && !property.isBlank()) {
            return Path.of(property).normalize().toAbsolutePath();
        }
        for (String candidate : new String[] {"olo-configuration/default", "../olo-configuration/default"}) {
            Path path = Path.of(candidate).normalize().toAbsolutePath();
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        return Path.of("olo-configuration/default").toAbsolutePath();
    }
}
