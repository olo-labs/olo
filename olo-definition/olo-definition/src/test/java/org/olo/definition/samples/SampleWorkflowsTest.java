package org.olo.definition.samples;

import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.definition.serializer.YamlWorkflowSerializer;
import org.olo.definition.validation.WorkflowValidator;
import org.olo.definition.workflow.WorkflowDefinition;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensures repository samples under {@code samples/} deserialize and pass structural validation.
 */
class SampleWorkflowsTest {

    private final JsonWorkflowSerializer json = new JsonWorkflowSerializer();
    private final YamlWorkflowSerializer yaml = new YamlWorkflowSerializer();

    @ParameterizedTest(name = "{0}")
    @MethodSource("jsonSamples")
    void loadsAndValidatesJsonSample(String label, Path file) throws IOException {
        WorkflowDefinition workflow = json.deserialize(Files.readString(file));
        assertThat(WorkflowValidator.validate(workflow).valid())
                .as("validation errors for %s", file)
                .isTrue();
        assertThat(workflow.getId()).isNotBlank();
    }

    @org.junit.jupiter.api.Test
    void loadsAndValidatesYamlSamples() throws IOException {
        var files = sampleFiles("yaml").map(args -> (Path) args.get()[1]).toList();
        assertThat(files).as("expected at least one YAML sample under samples/").isNotEmpty();
        for (Path file : files) {
            WorkflowDefinition workflow = yaml.deserialize(Files.readString(file));
            assertThat(WorkflowValidator.validate(workflow).valid())
                    .as("validation errors for %s", file)
                    .isTrue();
        }
    }

    static Stream<Arguments> jsonSamples() {
        return sampleFiles("json");
    }

    private static Stream<Arguments> sampleFiles(String extension) {
        Path samplesRoot = SamplePaths.resolveSamplesRoot();
        return Stream.of(
                        "minimal-echo/workflow." + extension,
                        "stock-analysis/workflow." + extension,
                        "rag-chat/workflow." + extension,
                        "condition-branch/workflow." + extension,
                        "human-approval-trade/workflow." + extension,
                        "multi-agent-orchestration/workflow." + extension,
                        "parallel-agent-fan-out/workflow." + extension,
                        "analysis-with-rag-extension/workflow-base." + extension,
                        "analysis-with-rag-extension/workflow-extended." + extension)
                .map(relative -> samplesRoot.resolve(relative))
                .filter(Files::exists)
                .map(path -> Arguments.of(path.toString().replace('\\', '/'), path));
    }
}
