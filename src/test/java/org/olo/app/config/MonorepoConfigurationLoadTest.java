/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.config;

import org.junit.jupiter.api.Test;
import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.definition.workflow.WorkflowDefinition;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MonorepoConfigurationLoadTest {

  private static final Path MONOREPO_ACTIVE =
      Path.of("..", "olo-mono", "olo-definition", "olo-configuration", "current-active")
          .normalize()
          .toAbsolutePath();

  @Test
  void loadsCurrentActiveWorkflowsFromMonorepo() throws Exception {
    assumeMonorepoPresent();
    Map<String, RegionalConfigurationSnapshot> snapshots =
        FilesystemConfigurationLoader.load(MONOREPO_ACTIVE);
    assertThat(snapshots).isNotEmpty();
    assertThat(snapshots.get("current-active").getWorkflows()).isNotEmpty();
  }

  @Test
  void deserializesEachCurrentActiveWorkflowJson() throws Exception {
    assumeMonorepoPresent();
    JsonWorkflowSerializer serializer = new JsonWorkflowSerializer();
    List<String> failures = new ArrayList<>();
    try (var files = Files.list(MONOREPO_ACTIVE)) {
      files.filter(path -> path.toString().endsWith(".json")).forEach(path -> {
        try {
          WorkflowDefinition workflow = serializer.deserialize(Files.readString(path));
          if (workflow.getId() == null || workflow.getId().isBlank()) {
            failures.add(path.getFileName() + ": missing id");
          }
        } catch (Exception e) {
          failures.add(path.getFileName() + ": " + e.getMessage());
        }
      });
    }
    assertThat(failures).as("workflow JSON deserialization failures").isEmpty();
  }

  private static void assumeMonorepoPresent() {
    assumeTrue(Files.isDirectory(MONOREPO_ACTIVE), "monorepo current-active folder not found");
  }

  private static void assumeTrue(boolean condition, String message) {
    org.junit.jupiter.api.Assumptions.assumeTrue(condition, message);
  }
}
