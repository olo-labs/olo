/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.configuration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regenerates preset workflows under {@code olo-configuration/default/} from
 * {@link DefaultConfigurationDefinitions}. Executed by the {@code generateConfiguration} Gradle task
 * before the main {@code test} suite on every build.
 */
class DefaultConfigurationRegenerationTest {

    @Test
    void regeneratesConfigurationPresetsFromDefinitions() throws IOException {
        Path configurationRoot = DefaultConfigurationPaths.resolveConfigurationRoot();
        new DefaultConfigurationGenerator().generate(configurationRoot);

        assertThat(configurationRoot.resolve("agent.json")).exists();
        assertThat(configurationRoot.resolve("workflow.json")).exists();
        try (var files = Files.list(configurationRoot)) {
            assertThat(files.filter(path -> path.toString().endsWith(".json")).count()).isEqualTo(12);
        }
    }
}
