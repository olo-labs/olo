/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.input.samples;

import org.olo.input.model.WorkflowInput;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensures repository samples under {@code samples/} deserialize to valid {@link WorkflowInput} payloads.
 */
class SampleWorkflowInputsTest {

    private static Path resolveSamplesRoot() {
        String property = System.getProperty("olo.samples.dir");
        if (property != null) {
            return Path.of(property);
        }
        for (String candidate : new String[] {"samples", "../samples"}) {
            Path path = Path.of(candidate).normalize().toAbsolutePath();
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        return Path.of("samples").toAbsolutePath();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("jsonSamples")
    void loadsJsonSample(String label, Path file) throws IOException {
        WorkflowInput input = WorkflowInput.fromJson(Files.readString(file));
        assertThat(input.getVersion()).isNotBlank();
        assertThat(input.getRouting()).isNotNull();
        assertThat(input.getRouting().getTransactionId()).isNotBlank();
    }

    static Stream<Arguments> jsonSamples() {
        return sampleFiles();
    }

    private static Stream<Arguments> sampleFiles() {
        Path samplesRoot = resolveSamplesRoot();
        return Stream.of(
                        "minimal-local/workflow-input.json",
                        "mixed-storage/workflow-input.json",
                        "producer-offload/workflow-input.json",
                        "cache-in-memory/workflow-input.json",
                        "typed-inputs/workflow-input.json",
                        "agent-execution/workflow-input.json",
                        "workflow-run/workflow-input.json",
                        "storage-remote/workflow-input.json",
                        "rag-metadata/workflow-input.json")
                .map(relative -> samplesRoot.resolve(relative))
                .filter(Files::exists)
                .map(path -> Arguments.of(path.toString().replace('\\', '/'), path));
    }
}
