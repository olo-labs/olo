package org.olo.definition.preset;

import java.util.Map;
import org.olo.definition.model.ModelProviderDefinition;
import org.olo.definition.model.ModelRoutingDefinition;
import org.olo.definition.variable.VariableDefinition;
import org.olo.definition.variable.VariableScope;

/**
 * Shared Studio defaults for workflow presets ({@code agent.json}, {@code planner.json}, …).
 */
public final class WorkflowPresetInfrastructure {

    public static final String MESSAGE_VARIABLE = "message";
    public static final String MODEL_PROVIDER_ID = "model-provider";
    public static final String DEFAULT_ROUTING_ID = "default-routing";

    private WorkflowPresetInfrastructure() {
    }

    public static VariableDefinition messageVariable() {
        return VariableDefinition.builder()
                .name(MESSAGE_VARIABLE)
                .type("string")
                .description("")
                .required(true)
                .scope(VariableScope.READONLY_EXTERNAL)
                .metadata(Map.of())
                .build();
    }

    public static ModelProviderDefinition defaultLocalModelProvider() {
        return ModelProviderDefinition.builder()
                .id(MODEL_PROVIDER_ID)
                .provider("local")
                .model("llama3.2:latest")
                .putConfiguration("baseUrl", "http://localhost:51435")
                .build();
    }

    public static ModelRoutingDefinition defaultModelRouting() {
        return ModelRoutingDefinition.builder()
                .id(DEFAULT_ROUTING_ID)
                .defaultProviderId(MODEL_PROVIDER_ID)
                .build();
    }
}
