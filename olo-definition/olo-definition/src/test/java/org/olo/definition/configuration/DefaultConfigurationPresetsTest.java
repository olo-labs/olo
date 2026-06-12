package org.olo.definition.configuration;

import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.definition.validation.WorkflowValidator;
import org.olo.definition.workflow.WorkflowDefinition;
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
 * Ensures on-disk presets under {@code olo-configuration/default/} match in-code definitions and
 * pass structural validation.
 */
class DefaultConfigurationPresetsTest {

    private final JsonWorkflowSerializer json = new JsonWorkflowSerializer();

    @ParameterizedTest(name = "{0}")
    @MethodSource("presetEntries")
    void onDiskPresetMatchesDefinition(String fileName, Supplier<WorkflowDefinition> factory) throws IOException {
        Path configurationRoot = DefaultConfigurationPaths.resolveConfigurationRoot();
        Path file = configurationRoot.resolve(fileName + ".json");
        assertThat(file).as("expected preset file %s", file).exists();

        WorkflowDefinition onDisk = json.deserialize(Files.readString(file));
        WorkflowDefinition expected = factory.get();

        assertThat(WorkflowValidator.validate(onDisk).valid())
                .as("validation errors for %s", file)
                .isTrue();
        assertThat(onDisk).isEqualTo(expected);
    }

    static Stream<Arguments> presetEntries() {
        return DefaultConfigurationGenerator.PRESET_ENTRIES.stream()
                .map(entry -> Arguments.of(entry.fileName(), entry.factory()));
    }
}
