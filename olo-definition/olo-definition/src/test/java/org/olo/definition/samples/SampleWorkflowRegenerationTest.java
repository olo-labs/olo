/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.samples;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regenerates canonical workflow samples under {@code samples/} from {@link SampleWorkflowDefinitions}.
 * Executed by the {@code generateSamples} Gradle task before the main {@code test} suite on every build.
 */
class SampleWorkflowRegenerationTest {

    @Test
    void regeneratesSamplesFromDefinitions() throws IOException {
        Path samplesRoot = SamplePaths.resolveSamplesRoot();
        new SampleWorkflowGenerator().generate(samplesRoot);

        assertThat(samplesRoot.resolve("minimal-echo/workflow.json")).exists();
        assertThat(samplesRoot.resolve("minimal-echo/workflow.yaml")).exists();
        assertThat(Files.list(samplesRoot)).isNotEmpty();
    }
}
