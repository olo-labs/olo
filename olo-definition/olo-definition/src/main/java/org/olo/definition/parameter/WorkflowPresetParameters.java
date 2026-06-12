package org.olo.definition.parameter;

import org.olo.annotation.OloCatalogLocations;
import org.olo.annotation.catalog.ParameterDescriptor;
import org.olo.annotation.catalog.WorkflowPresetCatalogLoader;
import org.olo.annotation.catalog.WorkflowPresetDescriptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Materializes workflow {@code parameters} from a compiled {@link OloCatalogLocations#WORKFLOW_PRESETS_CATALOG}
 * entry ({@code @OloWorkflowPreset} in olo-core or extensions).
 * <p>
 * Keeps {@code agent.json} and Studio {@code workflow-presets.json} aligned — edit {@code AgentWorkflowPreset}
 * only; {@link AgentWorkflowParameters#defaults()} inherits via this loader.
 */
public final class WorkflowPresetParameters {

    public static final String AGENT_PRESET_ID = "agent";

    private WorkflowPresetParameters() {}

    public static Map<String, WorkflowParameterDefinition> materialize(String presetId) {
        return materialize(presetId, WorkflowPresetParameters.class.getClassLoader());
    }

    public static Map<String, WorkflowParameterDefinition> materialize(String presetId, ClassLoader classLoader) {
        if (presetId == null || presetId.isBlank()) {
            throw new IllegalArgumentException("preset id is required");
        }
        ClassLoader loader = classLoader != null ? classLoader : WorkflowPresetParameters.class.getClassLoader();
        List<WorkflowPresetDescriptor> presets = WorkflowPresetCatalogLoader.loadMerged(loader);
        WorkflowPresetDescriptor preset =
                presets.stream().filter(entry -> presetId.equals(entry.id)).findFirst().orElse(null);
        if (preset == null) {
            throw new IllegalStateException(
                    "Workflow preset '"
                            + presetId
                            + "' not on classpath — add olo-core-nodes (or an extension JAR emitting "
                            + OloCatalogLocations.WORKFLOW_PRESETS_CATALOG
                            + ")");
        }
        Map<String, WorkflowParameterDefinition> parameters = new LinkedHashMap<>();
        for (ParameterDescriptor descriptor : preset.parameters) {
            if (descriptor.id == null || descriptor.id.isBlank()) {
                throw new IllegalStateException("preset " + presetId + " parameter missing id");
            }
            parameters.put(descriptor.id, ParameterCatalogMapper.toWorkflowParameter(descriptor));
        }
        return Map.copyOf(parameters);
    }
}
