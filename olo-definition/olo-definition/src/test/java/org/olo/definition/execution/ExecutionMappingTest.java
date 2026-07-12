/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.execution;

import org.olo.definition.node.NodeDefinition;
import org.olo.definition.node.NodeType;
import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.definition.validation.ValidationTestFixtures;
import org.olo.definition.validation.WorkflowValidator;
import org.olo.definition.workflow.WorkflowDefinition;
import org.olo.definition.workflow.WorkflowReferenceDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionMappingTest {

    private final JsonWorkflowSerializer json = new JsonWorkflowSerializer();

    @Test
    void roundTripsExecutionKindAndWorkflow() throws Exception {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("parent")
                .capability(ValidationTestFixtures.minimalCapability())
                .addNode(ValidationTestFixtures.node("technical-agent", NodeType.AGENT)
                        .executionKind(ExecutionKind.SUBWORKFLOW)
                        .executionModel(ExecutionModel.CHILD_WORKFLOW)
                        .workflow(WorkflowReferenceDefinition.builder()
                                .workflowId("technical-analysis")
                                .version("v1")
                                .build())
                        .build())
                .build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isTrue();

        WorkflowDefinition restored = json.deserialize(json.serialize(workflow));
        NodeDefinition agent = restored.getNodes().get(0);
        assertThat(agent.getExecutionKind()).isEqualTo(ExecutionKind.SUBWORKFLOW);
        assertThat(agent.getExecutionModel()).isEqualTo(ExecutionModel.CHILD_WORKFLOW);
        assertThat(agent.getWorkflow().getWorkflowId()).isEqualTo("technical-analysis");
        assertThat(agent.getWorkflow().getVersion()).isEqualTo("v1");
        assertThat(agent.getExecution()).isNotNull();
        assertThat(json.serialize(workflow)).contains("\"execution\"");
        assertThat(json.serialize(workflow)).contains("\"workflowRef\"");
    }

    @Test
    void rejectsAgentWithoutWorkflow() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("bad")
                .capability(ValidationTestFixtures.minimalCapability())
                .addNode(ValidationTestFixtures.node("agent", NodeType.AGENT).build())
                .build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isFalse();
        assertThat(WorkflowValidator.validate(workflow).errors())
                .anyMatch(e -> e.contains("requires a workflow reference"));
    }

    @Test
    void rejectsWorkflowOnToolNode() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("bad")
                .capability(ValidationTestFixtures.minimalCapability())
                .addNode(ValidationTestFixtures.node("tool", NodeType.TOOL)
                        .workflow(WorkflowReferenceDefinition.builder().workflowId("x").build())
                        .build())
                .build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isFalse();
    }
}
