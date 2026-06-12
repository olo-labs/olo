package org.olo.definition.planner;

import java.util.List;

/**
 * Default {@code availableAgents} references for the agent workflow preset — planner-visible agent
 * workflows the runtime may delegate to, without hard-wiring child-workflow composition.
 */
public final class AgentAvailableAgents {

    public static final String PLANNER = "planner";
    public static final String REVIEWER = "reviewer";
    public static final String ARCHITECT = "architect";

    private AgentAvailableAgents() {
    }

    public static List<AgentReferenceDefinition> agentPresetDefaults() {
        return List.of(
                AgentReferenceDefinition.of(PLANNER),
                AgentReferenceDefinition.of(REVIEWER),
                AgentReferenceDefinition.of(ARCHITECT));
    }
}
