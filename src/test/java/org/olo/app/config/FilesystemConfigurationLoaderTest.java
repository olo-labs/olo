/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.olo.definition.workflow.WorkflowDefinition;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FilesystemConfigurationLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void resolveReturnsNullForMissingPath() {
        assertThat(FilesystemConfigurationLoader.resolveConfigurationRoot("missing-configuration-" + System.nanoTime()))
                .isNull();
    }

    @Test
    void hasLoadableWorkflowsDetectsFlatFolder() throws Exception {
        Files.writeString(tempDir.resolve("agent.json"), "{\"id\":\"agent\"}");
        assertThat(FilesystemConfigurationLoader.hasLoadableWorkflows(tempDir)).isTrue();
    }

    @Test
    void hasLoadableWorkflowsDetectsNestedFolder() throws Exception {
        Path nested = tempDir.resolve("agents");
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("agent.json"), "{\"id\":\"agent\"}");
        assertThat(FilesystemConfigurationLoader.hasLoadableWorkflows(tempDir)).isTrue();
    }

    @Test
    void loadRegionIncludesNestedWorkflowJson() throws Exception {
        Path regionDir = tempDir.resolve("current-active");
        Path nested = regionDir.resolve("agents");
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("agent.json"), "{\"id\":\"agent\",\"nodes\":[]}");

        var snapshots = FilesystemConfigurationLoader.load(tempDir);

        assertThat(snapshots).containsKey("current-active");
        assertThat(snapshots.get("current-active").getWorkflows())
                .extracting(WorkflowDefinition::getId)
                .containsExactly("agent");
    }

    @Test
    void hasLoadableWorkflowsRejectsEmptyFolder() {
        assertThat(FilesystemConfigurationLoader.hasLoadableWorkflows(tempDir)).isFalse();
    }

    @Test
    void resolveFallsBackToMonorepoWhenConfiguredFolderIsEmpty(@TempDir Path workspace) throws Exception {
        Path emptyActive = workspace.resolve("olo-definition/olo-configuration/current-active");
        Files.createDirectories(emptyActive);
        Path monorepoActive =
                workspace.resolve("olo-mono/olo-definition/olo-configuration/current-active");
        Files.createDirectories(monorepoActive);
        Files.writeString(monorepoActive.resolve("agent.json"), "{\"id\":\"agent\"}");

        String previousUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", workspace.toString());
            Path resolved = FilesystemConfigurationLoader.resolveConfigurationRoot(
                    "olo-definition/olo-configuration/current-active");
            assertThat(resolved).isEqualTo(monorepoActive.normalize());
        } finally {
            if (previousUserDir != null) {
                System.setProperty("user.dir", previousUserDir);
            }
        }
    }
}
