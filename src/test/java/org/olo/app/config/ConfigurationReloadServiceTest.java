/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationReloadServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void reloadReplacesRegistryFromDisk() throws Exception {
        Files.writeString(
                tempDir.resolve("agent.json"),
                """
                {
                  "id": "agent",
                  "label": "Agent",
                  "queue": "agent",
                  "workflowType": "olo",
                  "version": "1.0.0",
                  "nodes": [],
                  "edges": []
                }
                """);

        RegionalConfigurationRegistry registry = new RegionalConfigurationRegistry();
        ConfigurationReloadService service = new ConfigurationReloadService(registry, tempDir.toString());

        ConfigurationReloadService.ConfigurationReloadResult result = service.reload();

        assertThat(result.ok()).isTrue();
        assertThat(result.workflowCount()).isEqualTo(1);
        assertThat(registry.isLoaded()).isTrue();
        assertThat(registry.getDefaultRegion()).isEqualTo("current-active");
        assertThat(registry.get("current-active").getWorkflows()).hasSize(1);
        assertThat(registry.get("current-active").getWorkflows().get(0).getId()).isEqualTo("agent");
    }
}
