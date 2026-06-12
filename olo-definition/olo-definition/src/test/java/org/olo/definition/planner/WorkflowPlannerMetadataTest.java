package org.olo.definition.planner;

import org.olo.definition.runtime.AgentDelegationPolicy;
import org.olo.definition.runtime.WorkflowRuntimeDefinition;
import org.olo.definition.validation.ValidationTestFixtures;
import org.olo.definition.validation.WorkflowValidator;
import org.olo.definition.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowPlannerMetadataTest {

    @Test
    void rejectsInvalidAgentSelectionStrategy() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("agent")
                .capability(ValidationTestFixtures.minimalCapability())
                .addAvailableAgent("planner")
                .runtime(WorkflowRuntimeDefinition.builder()
                        .delegation(AgentDelegationPolicy.agentPresetDefaults())
                        .build())
                .metadata(Map.of(
                        WorkflowPlannerMetadata.AGENT_SELECTION_STRATEGY, "best-effort"))
                .build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isFalse();
        assertThat(WorkflowValidator.validate(workflow).errors())
                .anyMatch(e -> e.contains("agentSelectionStrategy"));
    }

    @Test
    void rejectsAvailableAgentsWithoutAgentSelectionStrategy() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("agent")
                .capability(ValidationTestFixtures.minimalCapability())
                .addAvailableAgent("planner")
                .runtime(WorkflowRuntimeDefinition.builder()
                        .delegation(AgentDelegationPolicy.agentPresetDefaults())
                        .build())
                .metadata(Map.of(
                        WorkflowPlannerMetadata.ROLE, WorkflowPlannerMetadata.ROLE_AGENT))
                .build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isFalse();
        assertThat(WorkflowValidator.validate(workflow).errors())
                .anyMatch(e -> e.contains("metadata.agentSelectionStrategy is required"));
    }
}
