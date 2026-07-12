/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.serializer;

import org.olo.definition.node.NodeType;
import org.olo.definition.validation.ValidationTestFixtures;
import org.olo.definition.workflow.ChildWorkflowDefinition;
import org.olo.definition.workflow.WorkflowBuilder;
import org.olo.definition.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonWorkflowSerializerTest {

  private final JsonWorkflowSerializer serializer = new JsonWorkflowSerializer();

  @Test
  void roundTripsWorkflow() throws Exception {
    WorkflowDefinition original =
        WorkflowBuilder.create("StockAnalysis")
            .id("stock-analysis")
            .capability(ValidationTestFixtures.minimalCapability())
            .addNode(
                ValidationTestFixtures.node("llm1", NodeType.MODEL)
                    .subtype("CHAT")
                    .build())
            .build();

    String json = serializer.serialize(original);
    assertThat(json).contains("\"id\" : \"stock-analysis\"");
    assertThat(json).contains("\"label\" : \"StockAnalysis\"");
    assertThat(json).doesNotContain("\"name\" : \"StockAnalysis\"");
    assertThat(json).contains("\"type\" : \"MODEL\"");

    WorkflowDefinition restored = serializer.deserialize(json);
    assertThat(restored).isEqualTo(original);
  }

  @Test
  void roundTripsWorkflowRoleAndDescriptions() throws Exception {
    WorkflowDefinition original =
        WorkflowBuilder.create("Research Agent")
            .id("research-agent")
            .role("agent")
            .shortDescription("Web and document research with citations")
            .longDescription(
                "Performs web, news and document research, summarizes findings and produces citations.")
            .capability(ValidationTestFixtures.minimalCapability())
            .inputNode("input")
            .outputNode("output")
            .connect("input", "output")
            .build();

    String json = serializer.serialize(original);
    assertThat(json).contains("\"role\" : \"agent\"");
    assertThat(json).contains("\"shortDescription\" : \"Web and document research with citations\"");

    WorkflowDefinition restored = serializer.deserialize(json);
    assertThat(restored.getRole()).isEqualTo("agent");
    assertThat(restored.getShortDescription()).isEqualTo("Web and document research with citations");
    assertThat(restored.getLongDescription())
        .isEqualTo("Performs web, news and document research, summarizes findings and produces citations.");
    assertThat(restored).isEqualTo(original);
  }

  @Test
  void roundTripsWorkflowClassificationFlags() throws Exception {
    WorkflowDefinition original =
        WorkflowBuilder.create("External Child Workflow")
            .id("external-child")
            .isExternalWorkflow(true)
            .isChildWorkflow(true)
            .capability(ValidationTestFixtures.minimalCapability())
            .inputNode("input")
            .outputNode("output")
            .connect("input", "output")
            .build();

    String json = serializer.serialize(original);
    assertThat(json).contains("\"isExternalWorkflow\" : true");
    assertThat(json).contains("\"isChildWorkflow\" : true");

    WorkflowDefinition restored = serializer.deserialize(json);
    assertThat(restored.isExternalWorkflow()).isTrue();
    assertThat(restored.isChildWorkflow()).isTrue();
    assertThat(restored).isEqualTo(original);
  }

  @Test
  void roundTripsChildWorkflows() throws Exception {
    WorkflowDefinition original =
        WorkflowBuilder.create("Parent Orchestration")
            .id("parent-orchestration")
            .childWorkflow(
                ChildWorkflowDefinition.builder()
                    .workflowId("research-agent")
                    .workflowVersion("2.1.0")
                    .build())
            .childWorkflow(
                ChildWorkflowDefinition.builder()
                    .workflowId("risk-agent")
                    .workflowVersion("1.0.0")
                    .build())
            .capability(ValidationTestFixtures.minimalCapability())
            .inputNode("input")
            .outputNode("output")
            .connect("input", "output")
            .build();

    String json = serializer.serialize(original);
    assertThat(json).contains("\"childWorkflows\"");
    assertThat(json).contains("\"workflowId\" : \"research-agent\"");
    assertThat(json).contains("\"workflowVersion\" : \"2.1.0\"");

    WorkflowDefinition restored = serializer.deserialize(json);
    assertThat(restored.getChildWorkflows()).hasSize(2);
    assertThat(restored.getChildWorkflows().get(0).getWorkflowId()).isEqualTo("research-agent");
    assertThat(restored.getChildWorkflows().get(0).getWorkflowVersion()).isEqualTo("2.1.0");
    assertThat(restored).isEqualTo(original);
  }

  @Test
  void deserializesLegacyWorkflowNameAsLabel() throws Exception {
    WorkflowDefinition workflow =
        serializer.deserialize(
            """
            {
              "id": "agent",
              "name": "Agent",
              "capability": {
                "name": "Agent",
                "description": "test",
                "required_inputs": ["input"],
                "required_outputs": ["output"]
              },
              "nodes": []
            }
            """);

    assertThat(workflow.getLabel()).isEqualTo("Agent");
  }

  @Test
  void serializesEmojiAsUtf8Literal() throws Exception {
    WorkflowDefinition original =
        WorkflowBuilder.create("Architect")
            .id("architect")
            .emoji("🏗️")
            .capability(ValidationTestFixtures.minimalCapability())
            .build();

    String json = serializer.serialize(original);
    assertThat(json).contains("\"emoji\" : \"🏗️\"");
    assertThat(json).doesNotContain("\\uD83C");

    WorkflowDefinition restored = serializer.deserialize(json);
    assertThat(restored.getEmoji()).isEqualTo("🏗️");
  }
}
