/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.config.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Detects whether a folder tree contains workflow JSON suitable for loading. */
public final class FilesystemWorkflowDiscovery {

    static final List<String> MONOREPO_CONFIGURATION_CANDIDATES = List.of(
            "olo-mono/olo-definition/olo-configuration/current-active",
            "olo-mono/olo-definition/olo-configuration/default");

    private FilesystemWorkflowDiscovery() {
    }

    public static boolean hasLoadableWorkflows(Path rootDir) {
        if (rootDir == null || !Files.isDirectory(rootDir)) {
            return false;
        }
        if (containsWorkflowJson(rootDir)) {
            return true;
        }
        try (Stream<Path> children = Files.list(rootDir)) {
            return children
                    .filter(Files::isDirectory)
                    .anyMatch(FilesystemWorkflowDiscovery::containsWorkflowJson);
        } catch (IOException e) {
            return false;
        }
    }

    static boolean containsWorkflowJson(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.anyMatch(
                    path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json"));
        } catch (IOException e) {
            return false;
        }
    }
}
