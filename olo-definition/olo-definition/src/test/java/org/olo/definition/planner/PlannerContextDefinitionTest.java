package org.olo.definition.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlannerContextDefinitionTest {

    @Test
    void agentDefaultsExposeDelegationAgentsOnly() {
        Map<String, Object> defaults = PlannerContextDefinition.agentDefaults();

        @SuppressWarnings("unchecked")
        List<String> selectedTools = (List<String>) defaults.get(PlannerContextDefinition.SELECTED_TOOLS);
        @SuppressWarnings("unchecked")
        List<String> selectedAgents = (List<String>) defaults.get(PlannerContextDefinition.SELECTED_AGENTS);
        @SuppressWarnings("unchecked")
        List<String> selectedVariables = (List<String>) defaults.get(PlannerContextDefinition.SELECTED_VARIABLES);

        assertTrue(selectedTools.isEmpty());
        assertTrue(selectedAgents.containsAll(List.of("planner", "reviewer", "architect")));
        assertEquals(false, defaults.get(PlannerContextDefinition.INJECT_CAPABILITIES));
        assertEquals(true, defaults.get(PlannerContextDefinition.INJECT_AGENTS));
        assertEquals(List.of("message"), selectedVariables);
    }

    @Test
    void presetDefaultsIncludeMessageVariableSelection() {
        Map<String, Object> planner = PlannerContextDefinition.presetDefaults("planner");
        assertEquals(List.of("message"), planner.get(PlannerContextDefinition.SELECTED_VARIABLES));
        assertEquals(false, planner.get(PlannerContextDefinition.INJECT_AGENTS));
    }

    @Test
    void agentDefaultPromptLivesAtWorkflowRoot() {
        WorkflowPlannerPromptDefinition prompt = WorkflowPlannerPromptDefinition.agentDefault();
        assertEquals(WorkflowPlannerPromptDefinition.DEFAULT_PROMPT_ID, prompt.getId());
        assertTrue(prompt.getPromptTemplate().contains("{message}"));
    }

    @Test
    void presetPromptsReferenceMessagePlaceholder() {
        assertTrue(WorkflowPlannerPromptDefinition.forPreset("architect").getPromptTemplate().contains("{message}"));
        assertTrue(WorkflowPlannerPromptDefinition.forPreset("teacher").getPromptTemplate().contains("{message}"));
    }
}
