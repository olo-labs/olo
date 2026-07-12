/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.planner;

import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.definition.validation.ValidationTestFixtures;
import org.olo.definition.workflow.WorkflowBuilder;
import org.olo.definition.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentReferenceDefinitionTest {

    private final JsonWorkflowSerializer json = new JsonWorkflowSerializer();

    @Test
    void serializesAvailableAgentsAsObjectReferences() throws Exception {
        WorkflowDefinition workflow =
                WorkflowBuilder.create("Agent")
                        .id("agent")
                        .capability(ValidationTestFixtures.minimalCapability())
                        .agentAvailableAgents()
                        .build();

        String serialized = json.serialize(workflow);
        assertThat(serialized).contains("\"availableAgents\"");
        assertThat(serialized).containsPattern("\"id\"\\s*:\\s*\"planner\"");
        assertThat(serialized).doesNotContain("\"availableAgents\" : [ \"planner\"");
        assertThat(serialized).doesNotContain("availableAgentIds");

        WorkflowDefinition restored = json.deserialize(serialized);
        assertThat(restored.getAvailableAgents()).isEqualTo(AgentAvailableAgents.agentPresetDefaults());
    }

    @Test
    void deserializesLegacyStringShorthand() throws Exception {
        WorkflowDefinition workflow =
                json.deserialize(
                        """
                        {
                          "id": "agent",
                          "capability": {
                            "name": "Agent",
                            "description": "Agent",
                            "required_inputs": ["input"],
                            "required_outputs": ["output"]
                          },
                          "availableAgents": ["planner", "reviewer"],
                          "nodes": []
                        }
                        """);

        assertThat(workflow.getAvailableAgentIds()).containsExactly("planner", "reviewer");
    }
}
