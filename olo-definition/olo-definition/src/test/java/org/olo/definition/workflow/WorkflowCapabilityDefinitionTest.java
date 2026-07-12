/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.workflow;

import org.olo.definition.capability.CapabilityDefinition;
import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.definition.validation.WorkflowValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowCapabilityDefinitionTest {

    private final JsonWorkflowSerializer json = new JsonWorkflowSerializer();

    @Test
    void roundTripsResearchAgentCapability() throws Exception {
        WorkflowDefinition workflow = WorkflowBuilder.create("Research Agent")
                .id("research-agent")
                .version("2.1.0")
                .capability(CapabilityDefinition.builder()
                        .id("research-agent")
                        .name("Research Agent")
                        .description(
                                "Performs web, news and document research, summarizes findings and produces citations.")
                        .addTag("research")
                        .addTag("news")
                        .addInput("query")
                        .addOutput("summary")
                        .addOutput("citations")
                        .addExample("Research Nvidia earnings")
                        .cost(0.15)
                        .latency(45_000.0)
                        .confidence(0.85)
                        .build())
                .inputNode("input")
                .modelNode("research", "CHAT")
                .outputNode("output")
                .connect("input", "research")
                .connect("research", "output")
                .build();

        WorkflowValidator.validateOrThrow(workflow);

        WorkflowDefinition restored = json.deserialize(json.serialize(workflow));
        CapabilityDefinition capability = restored.getCapability();

        assertThat(capability.getName()).isEqualTo("Research Agent");
        assertThat(capability.getDescription()).contains("citations");
        assertThat(capability.getTags()).containsExactly("research", "news");
        assertThat(capability.getRequiredInputs()).containsExactly("query");
        assertThat(capability.getOutputs()).containsExactly("summary", "citations");
        assertThat(capability.getExamples()).containsExactly("Research Nvidia earnings");
        assertThat(capability.getCost()).isEqualTo(0.15);
        assertThat(capability.getLatency()).isEqualTo(45_000.0);
        assertThat(capability.getConfidence()).isEqualTo(0.85);
    }

    @Test
    void rejectsWorkflowWithoutCapability() {
        WorkflowDefinition workflow =
                WorkflowDefinition.builder().id("no-capability").build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isFalse();
        assertThat(WorkflowValidator.validate(workflow).errors())
                .anyMatch(e -> e.contains("capability is required"));
    }
}
