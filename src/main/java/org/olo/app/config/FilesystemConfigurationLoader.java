/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.config;

import org.olo.app.config.impl.FilesystemConfigurationRegionLoader;
import org.olo.app.config.impl.FilesystemConfigurationRootResolver;
import org.olo.app.config.impl.FilesystemWorkflowDiscovery;

import java.nio.file.Path;
import java.util.Map;

/**
 * Scans {@code olo.configuration.dir} for workflow JSON: regional subfolders or a flat folder
 * (treated as region {@code current-active}). Within each region, {@code *.json} files are
 * discovered recursively (e.g. {@code current-active/agents/agent.json}).
 */
public final class FilesystemConfigurationLoader {

    private FilesystemConfigurationLoader() {
    }

    public static Map<String, RegionalConfigurationSnapshot> load(Path rootDir) {
        return FilesystemConfigurationRegionLoader.load(rootDir);
    }

    public static RegionalConfigurationSnapshot loadRegion(String region, Path regionDir) throws java.io.IOException {
        return FilesystemConfigurationRegionLoader.loadRegion(region, regionDir);
    }

    public static Path resolveConfigurationRoot(String configuredPath) {
        return FilesystemConfigurationRootResolver.resolveConfigurationRoot(configuredPath);
    }

    public static boolean hasLoadableWorkflows(Path rootDir) {
        return FilesystemWorkflowDiscovery.hasLoadableWorkflows(rootDir);
    }
}
