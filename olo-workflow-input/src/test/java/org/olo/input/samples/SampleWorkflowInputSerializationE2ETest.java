package org.olo.input.samples;

import org.olo.input.model.WorkflowInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end: build sample payloads in code, serialize, deserialize, validate equality.
 */
class SampleWorkflowInputSerializationE2ETest {

    @ParameterizedTest(name = "{0} JSON round-trip")
    @MethodSource("sampleInputs")
    void jsonRoundTripPreservesWorkflowInput(String sampleName, Supplier<WorkflowInput> factory) {
        WorkflowInput original = factory.get();
        String json = original.toJson();
        assertThat(json).isNotBlank();

        WorkflowInput restored = WorkflowInput.fromJson(json);
        assertThat(restored)
                .as("deserialized payload must match original for sample '%s'", sampleName)
                .isEqualTo(original);
    }

    @ParameterizedTest(name = "{0} Temporal string payload")
    @MethodSource("sampleInputs")
    void fromJsonStringPreservesWorkflowInput(String sampleName, Supplier<WorkflowInput> factory) {
        WorkflowInput original = factory.get();
        WorkflowInput restored = WorkflowInput.fromJsonString(original.toJson());
        assertThat(restored)
                .as("string delegating creator must match for sample '%s'", sampleName)
                .isEqualTo(original);
    }

    @Test
    void buildSerializeToFilesAndDeserialize() throws IOException {
        String samplesDir = System.getProperty("olo.samples.dir", "samples");
        Path generatedRoot = Path.of(samplesDir).resolve("generated");
        new SampleWorkflowInputGenerator().generate(generatedRoot);

        verifyRoundTrip(generatedRoot.resolve("minimal-local/workflow-input.json"), SampleWorkflowInputDefinitions::minimalLocal);
        verifyRoundTrip(generatedRoot.resolve("mixed-storage/workflow-input.json"), SampleWorkflowInputDefinitions::mixedStorage);
        verifyRoundTrip(generatedRoot.resolve("producer-offload/workflow-input.json"), SampleWorkflowInputDefinitions::producerOffload);
        verifyRoundTrip(generatedRoot.resolve("cache-in-memory/workflow-input.json"), SampleWorkflowInputDefinitions::cacheInMemory);
        verifyRoundTrip(generatedRoot.resolve("typed-inputs/workflow-input.json"), SampleWorkflowInputDefinitions::typedInputs);
        verifyRoundTrip(generatedRoot.resolve("agent-execution/workflow-input.json"), SampleWorkflowInputDefinitions::agentExecution);
        verifyRoundTrip(generatedRoot.resolve("workflow-run/workflow-input.json"), SampleWorkflowInputDefinitions::workflowRun);
        verifyRoundTrip(generatedRoot.resolve("storage-remote/workflow-input.json"), SampleWorkflowInputDefinitions::storageRemote);
        verifyRoundTrip(generatedRoot.resolve("rag-metadata/workflow-input.json"), SampleWorkflowInputDefinitions::ragMetadata);
    }

    private void verifyRoundTrip(Path jsonFile, Supplier<WorkflowInput> factory) throws IOException {
        WorkflowInput built = factory.get();
        WorkflowInput fromJson = WorkflowInput.fromJson(Files.readString(jsonFile));
        assertThat(fromJson).isEqualTo(built);
    }

    static Stream<Arguments> sampleInputs() {
        return Stream.of(
                Arguments.of("minimal-local", (Supplier<WorkflowInput>) SampleWorkflowInputDefinitions::minimalLocal),
                Arguments.of("mixed-storage", (Supplier<WorkflowInput>) SampleWorkflowInputDefinitions::mixedStorage),
                Arguments.of("producer-offload", (Supplier<WorkflowInput>) SampleWorkflowInputDefinitions::producerOffload),
                Arguments.of("cache-in-memory", (Supplier<WorkflowInput>) SampleWorkflowInputDefinitions::cacheInMemory),
                Arguments.of("typed-inputs", (Supplier<WorkflowInput>) SampleWorkflowInputDefinitions::typedInputs),
                Arguments.of("agent-execution", (Supplier<WorkflowInput>) SampleWorkflowInputDefinitions::agentExecution),
                Arguments.of("workflow-run", (Supplier<WorkflowInput>) SampleWorkflowInputDefinitions::workflowRun),
                Arguments.of("storage-remote", (Supplier<WorkflowInput>) SampleWorkflowInputDefinitions::storageRemote),
                Arguments.of("rag-metadata", (Supplier<WorkflowInput>) SampleWorkflowInputDefinitions::ragMetadata));
    }
}
