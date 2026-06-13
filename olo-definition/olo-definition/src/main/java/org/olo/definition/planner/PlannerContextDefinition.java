package org.olo.definition.planner;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Well-known {@code metadata.plannerContext} shape for agent workflow presets.
 * <p>
 * Prompt templates live at workflow root ({@code prompts[]}, {@code defaultPromptId}).
 * This metadata block holds planner exposure settings only.
 */
public final class PlannerContextDefinition {

    public static final String METADATA_KEY = "plannerContext";

    public static final String SELECTED_TOOLS = "selectedTools";
    public static final String SELECTED_AGENTS = "selectedAgents";
    public static final String INJECT_CAPABILITIES = "injectCapabilities";
    public static final String INJECT_AGENTS = "injectAgents";
    public static final String SELECTED_VARIABLES = "selectedVariables";

    public static final String MACRO_CAPABILITIES = "CAPABILITIES";
    public static final String MACRO_AGENTS = "AGENTS";

    private PlannerContextDefinition() {
    }

    public static Map<String, Object> agentDefaults() {
        return presetDefaults("agent");
    }

    public static Map<String, Object> presetDefaults(String presetId) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put(SELECTED_VARIABLES, List.of("message"));
        if ("agent".equals(presetId)) {
            context.put(SELECTED_TOOLS, List.of());
            context.put(SELECTED_AGENTS, List.of("planner", "reviewer", "architect"));
            context.put(INJECT_CAPABILITIES, false);
            context.put(INJECT_AGENTS, true);
        } else {
            context.put(SELECTED_TOOLS, List.of());
            context.put(SELECTED_AGENTS, List.of());
            context.put(INJECT_CAPABILITIES, false);
            context.put(INJECT_AGENTS, false);
        }
        return Map.copyOf(context);
    }
}
