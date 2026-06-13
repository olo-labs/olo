package org.olo.definition.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.olo.definition.planner.PlannerContextDefinition;
import org.olo.definition.planner.WorkflowPlannerPromptDefinition;
import org.olo.definition.preset.WorkflowPresetInfrastructure;
import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.definition.workflow.WorkflowDefinition;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Ensures agent-style presets expose message variables, planner prompts, model infrastructure,
 * and the START → AGENT → END canvas pipeline.
 */
class DefaultConfigurationPipelineTest {

    private final JsonWorkflowSerializer json = new JsonWorkflowSerializer();

    @ParameterizedTest(name = "{0}")
    @MethodSource("pipelinePresets")
    void presetHasPipelinePlannerAndModelInfrastructure(
            String fileName, Supplier<WorkflowDefinition> factory) throws IOException {
        Path configurationRoot = DefaultConfigurationPaths.resolveConfigurationRoot();
        WorkflowDefinition onDisk =
                json.deserialize(Files.readString(configurationRoot.resolve(fileName + ".json")));
        if (!"architect".equals(fileName)) {
            assertThat(onDisk.isEnabled()).isNotEqualTo(Boolean.FALSE);
        }
        assertThat(onDisk.isDefault()).isTrue();
        assertThat(onDisk.getVariables())
                .anyMatch(variable -> WorkflowPresetInfrastructure.MESSAGE_VARIABLE.equals(variable.getName()));
        assertThat(onDisk.getCapability().getRequiredContext())
                .contains(WorkflowPresetInfrastructure.MESSAGE_VARIABLE);
        assertThat(onDisk.getPrompts()).hasSize(1);
        assertThat(onDisk.getDefaultPromptId()).isEqualTo(WorkflowPlannerPromptDefinition.DEFAULT_PROMPT_ID);
        assertThat(onDisk.getPrompts().getFirst().getPromptTemplate())
                .contains("{" + WorkflowPresetInfrastructure.MESSAGE_VARIABLE + "}");
        assertThat(onDisk.getModelProviders()).isNotEmpty();
        assertThat(onDisk.getModelRouting()).isNotEmpty();
        assertThat(onDisk.getNodes().stream().map(node -> node.getType()).toList())
                .containsExactly("START", "AGENT", "END");

        @SuppressWarnings("unchecked")
        var plannerContext = (java.util.Map<String, Object>) onDisk.getMetadata().get(PlannerContextDefinition.METADATA_KEY);
        assertThat(plannerContext)
                .containsEntry(PlannerContextDefinition.SELECTED_VARIABLES, java.util.List.of("message"));
    }

    static Stream<Arguments> pipelinePresets() {
        return DefaultConfigurationGenerator.PRESET_ENTRIES.stream()
                .map(entry -> Arguments.of(entry.fileName(), entry.factory()));
    }
}
