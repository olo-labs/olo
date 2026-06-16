/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Map;

/**
 * Reloads workflow definitions from {@code olo.configuration.dir} into the in-memory registry.
 */
@Service
public class ConfigurationReloadService {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationReloadService.class);

    private final RegionalConfigurationRegistry registry;
    private final String configurationDir;

    public ConfigurationReloadService(
            RegionalConfigurationRegistry registry,
            @Value("${olo.configuration.dir:}") String configurationDir) {
        this.registry = registry;
        this.configurationDir = configurationDir;
    }

    public ConfigurationReloadResult reload() {
        Path root = FilesystemConfigurationLoader.resolveConfigurationRoot(configurationDir);
        if (root == null) {
            log.warn("Configuration reload skipped: olo.configuration.dir not found (configured={})",
                    configurationDir);
            return new ConfigurationReloadResult(false, 0, 0, "configuration directory not found");
        }

        Map<String, RegionalConfigurationSnapshot> snapshots = FilesystemConfigurationLoader.load(root);
        if (snapshots.isEmpty()) {
            log.warn("Configuration reload found no workflows under {}", root);
            return new ConfigurationReloadResult(false, 0, 0, "no workflow definitions found");
        }

        String defaultRegion = resolveDefaultRegion(snapshots);
        registry.replaceAll(snapshots, defaultRegion);
        int workflowCount = snapshots.values().stream()
                .mapToInt(snap -> snap.getWorkflows().size())
                .sum();
        log.info(
                "Configuration reloaded from {} regions={} defaultRegion={} workflows={}",
                root,
                snapshots.keySet(),
                defaultRegion,
                workflowCount);
        return new ConfigurationReloadResult(true, snapshots.size(), workflowCount, null);
    }

    private static String resolveDefaultRegion(Map<String, RegionalConfigurationSnapshot> snapshots) {
        if (snapshots.containsKey("current-active")) {
            return "current-active";
        }
        if (snapshots.containsKey("default")) {
            return "default";
        }
        return snapshots.keySet().iterator().next();
    }

    public record ConfigurationReloadResult(
            boolean ok,
            int regionCount,
            int workflowCount,
            String message) {}
}
