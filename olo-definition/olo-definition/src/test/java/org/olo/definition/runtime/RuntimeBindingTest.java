/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.runtime;

import org.olo.definition.agent.AgentDefinition;
import org.olo.definition.execution.ExecutionModel;
import org.olo.definition.capability.CapabilityDefinition;
import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.definition.tool.ToolDefinition;
import org.olo.definition.validation.WorkflowValidator;
import org.olo.definition.workflow.WorkflowBuilder;
import org.olo.definition.workflow.WorkflowDefinition;
import org.olo.definition.workflow.WorkflowReferenceDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeBindingTest {

    private final JsonWorkflowSerializer json = new JsonWorkflowSerializer();

    @Test
    void resolvesImplementationClassBeforeId() {
        RuntimeBindingDefinition binding = RuntimeBindingDefinition.builder()
                .implementationClass("com.company.ai.CustomAgentExecutor")
                .implementationId("default-agent-runner")
                .build();

        assertThat(RuntimeBindingResolver.resolve(binding))
                .isEqualTo(RuntimeBindingResolution.IMPLEMENTATION_CLASS);
        assertThat(RuntimeBindingResolver.effectiveRuntime(binding)).isEqualTo(RuntimeKind.JAVA);
    }

    @Test
    void resolvesRegistryIdWhenNoClass() {
        RuntimeBindingDefinition binding = RuntimeBindingDefinition.builder()
                .implementationId("default-agent-runner")
                .build();

        assertThat(RuntimeBindingResolver.resolve(binding))
                .isEqualTo(RuntimeBindingResolution.IMPLEMENTATION_ID);
    }

    @Test
    void resolvesHttpServiceBinding() {
        RuntimeBindingDefinition binding = RuntimeBindingDefinition.builder()
                .runtime(RuntimeKind.HTTP)
                .implementationId("risk-agent-api")
                .build();

        assertThat(RuntimeBindingResolver.resolve(binding))
                .isEqualTo(RuntimeBindingResolution.IMPLEMENTATION_ID);
    }

    @Test
    void roundTripsAgentWithWorkflowRefAlias() throws Exception {
        WorkflowDefinition workflow = WorkflowBuilder.create("Agent Binding")
                .id("technical-analysis")
                .capability(CapabilityDefinition.builder()
                        .name("Technical Analysis")
                        .description("Indicators")
                        .addInput("symbol")
                        .addOutput("analysis")
                        .build())
                .agent(AgentDefinition.builder()
                        .id("technical-analysis")
                        .capability(CapabilityDefinition.builder()
                                .name("Technical Analysis Agent")
                                .description("Analyze trends")
                                .build())
                        .workflow(WorkflowReferenceDefinition.builder()
                                .workflowId("technical-analysis-v1")
                                .build())
                        .runtimeBinding(RuntimeBindingDefinition.builder()
                                .implementationId("default-agent-runner")
                                .build())
                        .executionModel(ExecutionModel.CHILD_WORKFLOW)
                        .systemPrompt("You are a technical analysis expert.")
                        .build())
                .inputNode("input")
                .outputNode("output")
                .connect("input", "output")
                .build();

        WorkflowValidator.validateOrThrow(workflow);

        String serialized = json.serialize(workflow);
        assertThat(serialized).contains("workflowRef");
        assertThat(serialized).contains("default-agent-runner");

        WorkflowDefinition restored = json.deserialize(serialized);
        AgentDefinition agent = restored.getAgents().get(0);
        assertThat(agent.getWorkflow().getWorkflowId()).isEqualTo("technical-analysis-v1");
        assertThat(agent.getRuntimeBinding().getImplementationId()).isEqualTo("default-agent-runner");
    }

    @Test
    void roundTripsCustomToolClassBinding() throws Exception {
        WorkflowDefinition workflow = WorkflowBuilder.create("Custom Tool")
                .id("custom-tool")
                .capability(CapabilityDefinition.builder()
                        .name("Custom Tool Flow")
                        .description("Test")
                        .addInput("in")
                        .addOutput("out")
                        .build())
                .tool(ToolDefinition.builder()
                        .id("screener")
                        .capability(CapabilityDefinition.builder()
                                .name("Screener")
                                .description("Screen stocks")
                                .build())
                        .runtimeBinding(RuntimeBindingDefinition.builder()
                                .runtime(RuntimeKind.JAVA)
                                .implementationClass("com.company.trading.AdvancedStockScreener")
                                .build())
                        .build())
                .inputNode("input")
                .outputNode("output")
                .connect("input", "output")
                .build();

        WorkflowValidator.validateOrThrow(workflow);
        WorkflowDefinition restored = json.deserialize(json.serialize(workflow));
        assertThat(restored.getTools().get(0).getRuntimeBinding().getImplementationClass())
                .isEqualTo("com.company.trading.AdvancedStockScreener");
    }

    @Test
    void rejectsEmptyRuntimeBinding() {
        WorkflowDefinition workflow = WorkflowBuilder.create("Bad Binding")
                .id("bad")
                .capability(CapabilityDefinition.builder()
                        .name("Bad")
                        .description("Bad")
                        .addInput("in")
                        .addOutput("out")
                        .build())
                .tool(ToolDefinition.builder()
                        .id("t")
                        .capability(CapabilityDefinition.builder()
                                .name("T")
                                .description("T")
                                .build())
                        .runtimeBinding(RuntimeBindingDefinition.builder().build())
                        .build())
                .inputNode("input")
                .outputNode("output")
                .connect("input", "output")
                .build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isFalse();
    }
}
