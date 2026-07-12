/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.configuration;

import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.definition.validation.WorkflowValidator;
import org.olo.definition.workflow.WorkflowDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/**
 * Writes canonical preset workflow JSON under {@code olo-configuration/default/}.
 * Invoked by {@link DefaultConfigurationRegenerationTest} ({@code generateConfiguration} Gradle task).
 */
public final class DefaultConfigurationGenerator {

    private final JsonWorkflowSerializer json = new JsonWorkflowSerializer();

    static final List<PresetEntry> PRESET_ENTRIES = List.of(
            new PresetEntry("agent", DefaultConfigurationDefinitions::agent),
            new PresetEntry("architect", DefaultConfigurationDefinitions::architect),
            new PresetEntry("ask", DefaultConfigurationDefinitions::ask),
            new PresetEntry("debug", DefaultConfigurationDefinitions::debug),
            new PresetEntry("detailed", DefaultConfigurationDefinitions::detailed),
            new PresetEntry("fast", DefaultConfigurationDefinitions::fast),
            new PresetEntry("planner", DefaultConfigurationDefinitions::planner),
            new PresetEntry("reviewer", DefaultConfigurationDefinitions::reviewer),
            new PresetEntry("strict", DefaultConfigurationDefinitions::strict),
            new PresetEntry("summary", DefaultConfigurationDefinitions::summary),
            new PresetEntry("teacher", DefaultConfigurationDefinitions::teacher),
            new PresetEntry("workflow", DefaultConfigurationDefinitions::workflow));

    public static void main(String[] args) throws IOException {
        Path configurationRoot = args.length > 0
                ? Path.of(args[0])
                : DefaultConfigurationPaths.resolveConfigurationRoot();
        new DefaultConfigurationGenerator().generate(configurationRoot);
    }

    public void generate(Path configurationRoot) throws IOException {
        Files.createDirectories(configurationRoot);
        for (PresetEntry entry : PRESET_ENTRIES) {
            writePreset(configurationRoot, entry.fileName(), entry.factory());
        }
    }

    private void writePreset(Path configurationRoot, String fileName, Supplier<WorkflowDefinition> factory)
            throws IOException {
        WorkflowDefinition workflow = factory.get();
        WorkflowValidator.validateOrThrow(workflow);
        Path target = configurationRoot.resolve(fileName + ".json");
        Files.writeString(target, json.serialize(workflow));
    }

    record PresetEntry(String fileName, Supplier<WorkflowDefinition> factory) {
    }
}
