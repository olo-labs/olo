/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.runtime;

import org.olo.definition.orchestration.MemoryScope;
import org.olo.definition.orchestration.ResultAggregation;
import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.definition.validation.ValidationTestFixtures;
import org.olo.definition.validation.WorkflowValidator;
import org.olo.definition.workflow.WorkflowBuilder;
import org.olo.definition.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeDelegationDefinitionTest {

    private final JsonWorkflowSerializer json = new JsonWorkflowSerializer();

    @Test
    void agentDelegationRoundTripsUnderRuntime() throws Exception {
        WorkflowDefinition workflow = WorkflowBuilder.create("Agent")
                .id("agent")
                .capability(ValidationTestFixtures.minimalCapability())
                .agentPlannerMetadata()
                .agentAvailableAgents()
                .agentWorkflowRuntime()
                .agentDelegation()
                .build();

        RuntimeDelegationDefinition delegation = workflow.getRuntime().getDelegation();
        assertThat(delegation.getEnabled()).isTrue();
        assertThat(delegation.getParallelEnabled()).isTrue();
        assertThat(delegation.getMaxDepth()).isEqualTo(AgentDelegationPolicy.DEFAULT_MAX_DEPTH);
        assertThat(delegation.getMaxDelegations()).isEqualTo(AgentDelegationPolicy.DEFAULT_MAX_DELEGATIONS);
        assertThat(delegation.getResultAggregation()).isEqualTo(AgentDelegationPolicy.DEFAULT_RESULT_AGGREGATION);
        assertThat(delegation.getMemoryScope()).isEqualTo(AgentDelegationPolicy.DEFAULT_MEMORY_SCOPE);
        assertThat(WorkflowValidator.validate(workflow).valid()).isTrue();

        WorkflowDefinition restored = json.deserialize(json.serialize(workflow));
        assertThat(restored.getRuntime().getDelegation()).isEqualTo(delegation);
    }

    @Test
    void legacyOrchestrationDeserializesIntoRuntimeDelegation() throws Exception {
        String raw = """
                {
                  "id": "agent",
                  "capability": {
                    "name": "Agent",
                    "description": "test",
                    "tags": [],
                    "examples": [],
                    "required_inputs": [],
                    "required_outputs": [],
                    "tool_requirements": [],
                    "required_context": []
                  },
                  "availableAgents": ["planner"],
                  "metadata": {
                    "agentSelectionStrategy": "dynamic"
                  },
                  "orchestration": {
                    "allowDelegation": true,
                    "allowParallelDelegation": true,
                    "maxDelegationDepth": 3,
                    "maxDelegations": 10,
                    "resultAggregation": "MERGE",
                    "memoryScope": "SHARED"
                  },
                  "runtime": {
                    "contractVersion": "1.0",
                    "executionModel": "CHILD_WORKFLOW"
                  }
                }
                """;

        WorkflowDefinition workflow = json.deserialize(raw);
        assertThat(workflow.getRuntime().getDelegation()).isEqualTo(AgentDelegationPolicy.agentPresetDefaults());
    }

    @Test
    void rejectsDelegationWithoutMaxDepth() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("agent")
                .capability(ValidationTestFixtures.minimalCapability())
                .addAvailableAgent("planner")
                .metadata(java.util.Map.of("agentSelectionStrategy", "dynamic"))
                .runtime(WorkflowRuntimeDefinition.builder()
                        .executionModel(org.olo.definition.execution.ExecutionModel.CHILD_WORKFLOW)
                        .delegation(RuntimeDelegationDefinition.builder()
                                .enabled(true)
                                .maxDelegations(10)
                                .resultAggregation(ResultAggregation.MERGE)
                                .memoryScope(MemoryScope.PRIVATE)
                                .build())
                        .build())
                .build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isFalse();
        assertThat(WorkflowValidator.validate(workflow).errors())
                .anyMatch(e -> e.contains("runtime.delegation.maxDepth is required"));
    }

    @Test
    void rejectsDelegationWithoutMaxDelegations() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("agent")
                .capability(ValidationTestFixtures.minimalCapability())
                .addAvailableAgent("planner")
                .metadata(java.util.Map.of("agentSelectionStrategy", "dynamic"))
                .runtime(WorkflowRuntimeDefinition.builder()
                        .executionModel(org.olo.definition.execution.ExecutionModel.CHILD_WORKFLOW)
                        .delegation(RuntimeDelegationDefinition.builder()
                                .enabled(true)
                                .maxDepth(3)
                                .resultAggregation(ResultAggregation.MERGE)
                                .memoryScope(MemoryScope.PRIVATE)
                                .build())
                        .build())
                .build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isFalse();
        assertThat(WorkflowValidator.validate(workflow).errors())
                .anyMatch(e -> e.contains("runtime.delegation.maxDelegations is required"));
    }

    @Test
    void rejectsDelegationWithoutResultAggregation() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("agent")
                .capability(ValidationTestFixtures.minimalCapability())
                .addAvailableAgent("planner")
                .metadata(java.util.Map.of("agentSelectionStrategy", "dynamic"))
                .runtime(WorkflowRuntimeDefinition.builder()
                        .executionModel(org.olo.definition.execution.ExecutionModel.CHILD_WORKFLOW)
                        .delegation(RuntimeDelegationDefinition.builder()
                                .enabled(true)
                                .maxDepth(3)
                                .maxDelegations(10)
                                .memoryScope(MemoryScope.PRIVATE)
                                .build())
                        .build())
                .build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isFalse();
        assertThat(WorkflowValidator.validate(workflow).errors())
                .anyMatch(e -> e.contains("runtime.delegation.resultAggregation is required"));
    }

    @Test
    void bestResponseAggregationRoundTrips() throws Exception {
        WorkflowDefinition workflow = WorkflowBuilder.create("Agent")
                .id("agent")
                .capability(ValidationTestFixtures.minimalCapability())
                .agentPlannerMetadata()
                .availableAgent("architect")
                .agentWorkflowRuntime()
                .delegation(RuntimeDelegationDefinition.builder()
                        .enabled(true)
                        .parallelEnabled(true)
                        .maxDepth(2)
                        .maxDelegations(5)
                        .resultAggregation(ResultAggregation.BEST_RESPONSE)
                        .memoryScope(MemoryScope.SHARED)
                        .build())
                .build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isTrue();
        WorkflowDefinition restored = json.deserialize(json.serialize(workflow));
        assertThat(restored.getRuntime().getDelegation().getResultAggregation())
                .isEqualTo(ResultAggregation.BEST_RESPONSE);
    }

    @Test
    void rejectsParallelDelegationWithoutEnabled() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("agent")
                .capability(ValidationTestFixtures.minimalCapability())
                .runtime(WorkflowRuntimeDefinition.builder()
                        .delegation(RuntimeDelegationDefinition.builder()
                                .enabled(false)
                                .parallelEnabled(true)
                                .memoryScope(MemoryScope.SHARED)
                                .build())
                        .build())
                .build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isFalse();
        assertThat(WorkflowValidator.validate(workflow).errors())
                .anyMatch(e -> e.contains("parallelEnabled requires delegation.enabled"));
    }

    @Test
    void rejectsAvailableAgentsWithoutRuntimeDelegation() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("agent")
                .capability(ValidationTestFixtures.minimalCapability())
                .addAvailableAgent("planner")
                .build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isFalse();
        assertThat(WorkflowValidator.validate(workflow).errors())
                .anyMatch(e -> e.contains("runtime.delegation is required when availableAgents is set"));
    }
}
