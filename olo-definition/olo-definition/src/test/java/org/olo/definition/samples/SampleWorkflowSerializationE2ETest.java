package org.olo.definition.samples;

import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.definition.serializer.WorkflowSerializer;
import org.olo.definition.serializer.YamlWorkflowSerializer;
import org.olo.definition.validation.WorkflowValidator;
import org.olo.definition.workflow.WorkflowDefinition;
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
 * End-to-end: build sample workflows in code, serialize, deserialize, validate equality.
 */
class SampleWorkflowSerializationE2ETest {

    private final JsonWorkflowSerializer jsonSerializer = new JsonWorkflowSerializer();
    private final YamlWorkflowSerializer yamlSerializer = new YamlWorkflowSerializer();

    @ParameterizedTest(name = "{0} via JSON")
    @MethodSource("sampleWorkflows")
    void jsonRoundTripPreservesWorkflow(String sampleName, Supplier<WorkflowDefinition> factory)
            throws IOException {
        roundTrip(sampleName, factory, jsonSerializer);
    }

    @ParameterizedTest(name = "{0} via YAML")
    @MethodSource("sampleWorkflows")
    void yamlRoundTripPreservesWorkflow(String sampleName, Supplier<WorkflowDefinition> factory)
            throws IOException {
        roundTrip(sampleName, factory, yamlSerializer);
    }

    @ParameterizedTest(name = "{0} JSON bytes")
    @MethodSource("sampleWorkflows")
    void jsonBytesRoundTripPreservesWorkflow(String sampleName, Supplier<WorkflowDefinition> factory)
            throws IOException {
        WorkflowDefinition original = buildAndValidate(sampleName, factory);

        byte[] bytes = jsonSerializer.serializeToBytes(original);
        assertThat(bytes).isNotEmpty();

        WorkflowDefinition restored = jsonSerializer.deserialize(bytes);
        assertThat(restored).isEqualTo(original);
        assertThat(WorkflowValidator.validate(restored).valid()).isTrue();
    }

    /**
     * Builds each sample in code, serializes to {@code samples/generated/}, then round-trips from disk.
     */
    @Test
    void buildSerializeToFilesAndDeserialize() throws IOException {
        String samplesDir = System.getProperty("olo.samples.dir", "samples");
        Path generatedRoot = Path.of(samplesDir).resolve("generated");
        new SampleWorkflowGenerator().generate(generatedRoot);
        verifyRoundTrip(generatedRoot.resolve("minimal-echo/workflow.json"), SampleWorkflowDefinitions::minimalEcho);
        verifyRoundTrip(generatedRoot.resolve("stock-analysis/workflow.json"), SampleWorkflowDefinitions::stockAnalysis);
        verifyRoundTrip(generatedRoot.resolve("rag-chat/workflow.json"), SampleWorkflowDefinitions::ragChat);
        verifyRoundTrip(generatedRoot.resolve("condition-branch/workflow.json"), SampleWorkflowDefinitions::conditionBranch);
        verifyRoundTrip(
                generatedRoot.resolve("human-approval-trade/workflow.json"),
                SampleWorkflowDefinitions::humanApprovalTrade);
        verifyRoundTrip(
                generatedRoot.resolve("analysis-with-rag-extension/workflow-base.json"),
                SampleWorkflowDefinitions::analysisBase);
        verifyRoundTrip(
                generatedRoot.resolve("analysis-with-rag-extension/workflow-extended.json"),
                SampleWorkflowDefinitions::analysisExtended);
    }

    private void verifyRoundTrip(Path jsonFile, Supplier<WorkflowDefinition> factory) throws IOException {
        WorkflowDefinition built = factory.get();
        WorkflowValidator.validateOrThrow(built);

        WorkflowDefinition fromJson = jsonSerializer.deserialize(Files.readString(jsonFile));
        Path yamlFile = jsonFile.getParent().resolve(
                jsonFile.getFileName().toString().replace(".json", ".yaml"));
        WorkflowDefinition fromYaml = yamlSerializer.deserialize(Files.readString(yamlFile));

        assertThat(fromJson).isEqualTo(built);
        assertThat(fromYaml).isEqualTo(built);
        assertThat(WorkflowValidator.validate(fromJson).valid()).isTrue();
    }

    @ParameterizedTest(name = "{0} cross-format JSON→YAML")
    @MethodSource("sampleWorkflows")
    void jsonToYamlCrossFormatPreservesWorkflow(String sampleName, Supplier<WorkflowDefinition> factory)
            throws IOException {
        WorkflowDefinition original = buildAndValidate(sampleName, factory);

        String json = jsonSerializer.serialize(original);
        WorkflowDefinition fromJson = jsonSerializer.deserialize(json);

        String yaml = yamlSerializer.serialize(fromJson);
        WorkflowDefinition fromYaml = yamlSerializer.deserialize(yaml);

        assertThat(fromYaml).isEqualTo(original);
    }

    private void roundTrip(
            String sampleName,
            Supplier<WorkflowDefinition> factory,
            WorkflowSerializer serializer)
            throws IOException {
        WorkflowDefinition original = buildAndValidate(sampleName, factory);

        String serialized = serializer.serialize(original);
        assertThat(serialized).isNotBlank();

        WorkflowDefinition restored = serializer.deserialize(serialized);

        assertThat(restored)
                .as("deserialized workflow must match original for sample '%s'", sampleName)
                .isEqualTo(original);
        assertThat(WorkflowValidator.validate(restored).valid())
                .as("restored workflow must pass validation for sample '%s'", sampleName)
                .isTrue();
    }

    private static WorkflowDefinition buildAndValidate(
            String sampleName, Supplier<WorkflowDefinition> factory) {
        WorkflowDefinition original = factory.get();
        assertThat(WorkflowValidator.validate(original).valid())
                .as("built workflow must be valid for sample '%s'", sampleName)
                .isTrue();
        return original;
    }

    static Stream<Arguments> sampleWorkflows() {
        return Stream.of(
                Arguments.of("minimal-echo", (Supplier<WorkflowDefinition>) SampleWorkflowDefinitions::minimalEcho),
                Arguments.of(
                        "stock-analysis",
                        (Supplier<WorkflowDefinition>) SampleWorkflowDefinitions::stockAnalysis),
                Arguments.of("rag-chat", (Supplier<WorkflowDefinition>) SampleWorkflowDefinitions::ragChat),
                Arguments.of(
                        "analysis-base",
                        (Supplier<WorkflowDefinition>) SampleWorkflowDefinitions::analysisBase),
                Arguments.of(
                        "analysis-extended",
                        (Supplier<WorkflowDefinition>) SampleWorkflowDefinitions::analysisExtended),
                Arguments.of(
                        "condition-branch",
                        (Supplier<WorkflowDefinition>) SampleWorkflowDefinitions::conditionBranch),
                Arguments.of(
                        "human-approval-trade",
                        (Supplier<WorkflowDefinition>) SampleWorkflowDefinitions::humanApprovalTrade));
    }
}
