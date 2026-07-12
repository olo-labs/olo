/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.config.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Resolves {@code olo.configuration.dir} with monorepo discovery fallbacks. */
public final class FilesystemConfigurationRootResolver {

    private static final Logger log = LoggerFactory.getLogger(FilesystemConfigurationRootResolver.class);

    private FilesystemConfigurationRootResolver() {
    }

    public static Path resolveConfigurationRoot(String configuredPath) {
        boolean configuredProvided = configuredPath != null && !configuredPath.isBlank();
        Path configured = configuredProvided ? resolveConfiguredPath(configuredPath) : null;
        if (configured != null && FilesystemWorkflowDiscovery.hasLoadableWorkflows(configured)) {
            return configured;
        }
        if (shouldDiscoverFallback(configuredPath, configured)) {
            Path discovered = discoverMonorepoConfigurationRoot();
            if (discovered != null) {
                if (configured != null && Files.isDirectory(configured)) {
                    log.warn(
                            "Configured olo.configuration.dir has no workflow JSON ({}); using {}",
                            configured,
                            discovered);
                } else if (configuredProvided) {
                    log.warn(
                            "Configured olo.configuration.dir not found ({}); using {}",
                            configuredPath.trim(),
                            discovered);
                } else {
                    log.info("Discovered olo.configuration.dir at {}", discovered);
                }
                return discovered;
            }
        }
        if (configured != null && Files.isDirectory(configured)) {
            return configured;
        }
        return null;
    }

    private static boolean shouldDiscoverFallback(String configuredPath, Path configured) {
        if (configured != null && Files.isDirectory(configured)) {
            return true;
        }
        if (configuredPath == null || configuredPath.isBlank()) {
            return true;
        }
        String normalized = configuredPath.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.contains("olo-configuration")
                || normalized.contains("olo-mono")
                || normalized.contains("current-active")
                || normalized.contains("default");
    }

    private static Path resolveConfiguredPath(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return null;
        }
        Path path = Path.of(configuredPath.trim());
        if (path.isAbsolute()) {
            return Files.isDirectory(path) ? path.normalize() : null;
        }
        Path fromCwd = Path.of(System.getProperty("user.dir", ".")).resolve(path).normalize();
        if (Files.isDirectory(fromCwd)) {
            return fromCwd;
        }
        Path fromParent = Path.of(System.getProperty("user.dir", ".")).getParent();
        if (fromParent != null) {
            Path sibling = fromParent.resolve(path).normalize();
            if (Files.isDirectory(sibling)) {
                return sibling;
            }
        }
        return Files.isDirectory(path) ? path.normalize() : null;
    }

    private static Path discoverMonorepoConfigurationRoot() {
        Path cursor = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        while (cursor != null) {
            for (String relative : FilesystemWorkflowDiscovery.MONOREPO_CONFIGURATION_CANDIDATES) {
                Path candidate = cursor.resolve(relative).normalize();
                if (FilesystemWorkflowDiscovery.hasLoadableWorkflows(candidate)) {
                    return candidate;
                }
            }
            Path nestedMono = cursor.resolve("olo-mono");
            if (Files.isDirectory(nestedMono)) {
                for (String relative : FilesystemWorkflowDiscovery.MONOREPO_CONFIGURATION_CANDIDATES) {
                    Path candidate = nestedMono.resolve(relative.substring("olo-mono/".length())).normalize();
                    if (FilesystemWorkflowDiscovery.hasLoadableWorkflows(candidate)) {
                        return candidate;
                    }
                }
            }
            cursor = cursor.getParent();
        }
        return null;
    }
}
