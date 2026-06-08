/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.Map;

/**
 * Loads {@code olo.configuration.dir} eagerly so Temporal routing and tenant id are available before beans wire up.
 */
@Configuration
public class OloFilesystemConfigurationConfig {

    private static final Logger log = LoggerFactory.getLogger(OloFilesystemConfigurationConfig.class);

    @Bean
    RegionalConfigurationRegistry regionalConfigurationRegistry(
            @Value("${olo.configuration.dir:}") String configurationDir) {
        RegionalConfigurationRegistry registry = new RegionalConfigurationRegistry();
        Path root = FilesystemConfigurationLoader.resolveConfigurationRoot(configurationDir);
        if (root == null) {
            log.warn("olo.configuration.dir is not set or not found (configured={}); using runtime fallbacks",
                    configurationDir);
            return registry;
        }
        Map<String, RegionalConfigurationSnapshot> snapshots = FilesystemConfigurationLoader.load(root);
        if (snapshots.isEmpty()) {
            log.warn("No workflow configuration found under {}", root);
            return registry;
        }
        String defaultRegion = resolveDefaultRegion(snapshots);
        registry.replaceAll(snapshots, defaultRegion);
        log.info("Filesystem configuration loaded from {} regions={} defaultRegion={}",
                root, snapshots.keySet(), defaultRegion);
        return registry;
    }

    @Bean
    ResolvedOloRuntimeSettings resolvedOloRuntimeSettings(RegionalConfigurationRegistry registry) {
        ResolvedOloRuntimeSettings settings = ResolvedOloRuntimeSettings.resolve(registry);
        log.info("Resolved runtime from configuration: tenant={} taskQueue={} workflowType={}",
                settings.tenantId(), settings.taskQueue(), settings.workflowType());
        return settings;
    }

    private static String resolveDefaultRegion(Map<String, RegionalConfigurationSnapshot> snapshots) {
        if (snapshots.containsKey("default")) {
            return "default";
        }
        return snapshots.keySet().iterator().next();
    }
}
