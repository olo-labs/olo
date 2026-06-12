package org.olo.definition.planner;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Well-known {@code metadata} keys for planner routing ({@code role}, {@code agentType},
 * {@code planningStrategy}, {@code agentSelectionStrategy}) on
 * {@link org.olo.definition.workflow.WorkflowDefinition}.
 */
public final class WorkflowPlannerMetadata {

    public static final String ROLE = "role";
    public static final String AGENT_TYPE = "agentType";
    public static final String PLANNING_STRATEGY = "planningStrategy";
    public static final String AGENT_SELECTION_STRATEGY = "agentSelectionStrategy";

    public static final String ROLE_AGENT = "agent";
    public static final String ROLE_PLANNER = "planner";
    public static final String ROLE_EXECUTOR = "executor";

    public static final String AGENT_TYPE_AUTONOMOUS = "autonomous";

    public static final String PLANNING_STRATEGY_REACT = "react";

    public static final String AGENT_SELECTION_STRATEGY_DYNAMIC =
            AgentSelectionStrategy.DYNAMIC.wireName();
    public static final String AGENT_SELECTION_STRATEGY_STATIC =
            AgentSelectionStrategy.STATIC.wireName();

    private WorkflowPlannerMetadata() {
    }

    public static Map<String, Object> agentDefaults() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(ROLE, ROLE_AGENT);
        metadata.put(AGENT_TYPE, AGENT_TYPE_AUTONOMOUS);
        metadata.put(PLANNING_STRATEGY, PLANNING_STRATEGY_REACT);
        metadata.put(AGENT_SELECTION_STRATEGY, AGENT_SELECTION_STRATEGY_DYNAMIC);
        return Map.copyOf(metadata);
    }

    public static void validateAgentSelectionStrategy(
            String workflowId, Map<String, Object> metadata, List<String> errors) {
        if (metadata == null) {
            return;
        }
        Object raw = metadata.get(AGENT_SELECTION_STRATEGY);
        if (raw == null) {
            return;
        }
        if (!(raw instanceof String value) || AgentSelectionStrategy.fromWire(value).isEmpty()) {
            errors.add("workflow " + workflowId + ": metadata.agentSelectionStrategy must be "
                    + AGENT_SELECTION_STRATEGY_DYNAMIC + " or " + AGENT_SELECTION_STRATEGY_STATIC);
        }
    }

    public static Map<String, Object> plannerDefaults() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(ROLE, ROLE_PLANNER);
        return Map.copyOf(metadata);
    }

    public static Map<String, Object> executorDefaults() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(ROLE, ROLE_EXECUTOR);
        return Map.copyOf(metadata);
    }
}
