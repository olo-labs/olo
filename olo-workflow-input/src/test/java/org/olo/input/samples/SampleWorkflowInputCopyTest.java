/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.input.samples;

import org.olo.input.model.WorkflowInput;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generates canonical samples and {@code -copy} sidecars via {@link WorkflowInput#copy()},
 * then verifies on-disk files are byte-identical.
 */
class SampleWorkflowInputCopyTest {

    @Test
    void copySerializesIdenticalSampleFilesSideBySide() throws IOException {
        Path samplesRoot = resolveSamplesRoot();
        SampleWorkflowInputGenerator generator = new SampleWorkflowInputGenerator();
        generator.generate(samplesRoot);
        generator.generateCopies(samplesRoot);

        for (SampleWorkflowInputGenerator.SampleEntry entry : SampleWorkflowInputGenerator.SAMPLE_ENTRIES) {
            Path dir = samplesRoot.resolve(entry.folder());
            Path original = dir.resolve("workflow-input.json");
            Path copy = dir.resolve(SampleWorkflowInputGenerator.copyBaseName() + ".json");

            assertThat(Files.exists(original)).as("expected sample file %s", original).isTrue();
            assertThat(Files.exists(copy)).as("expected copy file %s", copy).isTrue();

            String originalText = Files.readString(original);
            String copyText = Files.readString(copy);
            assertThat(copyText)
                    .as("copy file must be identical to original for %s", original.getFileName())
                    .isEqualTo(originalText);

            WorkflowInput originalInput = WorkflowInput.fromJson(originalText);
            WorkflowInput copiedInput = WorkflowInput.fromJson(copyText);
            assertThat(copiedInput).isEqualTo(originalInput);
        }
    }

    private static Path resolveSamplesRoot() {
        String property = System.getProperty("olo.samples.dir");
        if (property != null) {
            return Path.of(property);
        }
        return Path.of("samples").toAbsolutePath();
    }
}
