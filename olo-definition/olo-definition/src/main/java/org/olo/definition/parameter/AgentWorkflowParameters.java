package org.olo.definition.parameter;

import java.util.Map;

/**
 * Agent workflow preset parameters ({@code agent.json} {@code parameters} block).
 * <p>
 * <strong>Single source:</strong> {@code org.olo.core.preset.AgentWorkflowPreset} ({@code @OloWorkflowPreset})
 * compiles to {@link org.olo.annotation.OloCatalogLocations#WORKFLOW_PRESETS_CATALOG}; this type materializes
 * the same schema for workflow JSON via {@link WorkflowPresetParameters}.
 */
public final class AgentWorkflowParameters {

    public static final String MAX_ITERATIONS = "maxIterations";
    public static final String SYSTEM_PROMPT = "systemPrompt";
    public static final String MODEL = "model";
    public static final String TEMPERATURE = "temperature";

    private AgentWorkflowParameters() {}

    public static Map<String, WorkflowParameterDefinition> defaults() {
        return WorkflowPresetParameters.materialize(WorkflowPresetParameters.AGENT_PRESET_ID);
    }

    public static Map<String, WorkflowParameterDefinition> defaults(ClassLoader classLoader) {
        return WorkflowPresetParameters.materialize(WorkflowPresetParameters.AGENT_PRESET_ID, classLoader);
    }
}
