package org.olo.definition.workflow;

import org.olo.definition.human.HumanApprovalDefinition;
import org.olo.definition.node.NodeDefinition;
import org.olo.definition.node.NodeType;
import org.olo.definition.validation.ValidationTestFixtures;
import org.olo.definition.validation.WorkflowValidator;
import org.olo.definition.workflow.WorkflowReferenceDefinition;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowBuilderTest {

    @Test
    void buildsStockAnalysisWorkflow() {
        WorkflowDefinition workflow = WorkflowBuilder.create("Stock Workflow")
                .id("stock-analysis")
                .version("1.0.0")
                .capability(ValidationTestFixtures.minimalCapability())
                .inputNode("request")
                .modelNode("analysis", "CHAT")
                .toolNode("screener")
                .outputNode("response")
                .connect("request", "analysis")
                .connect("analysis", "screener")
                .connect("screener", "response")
                .build();

        assertThat(workflow.getId()).isEqualTo("stock-analysis");
        assertThat(workflow.getName()).isEqualTo("Stock Workflow");
        assertThat(workflow.getNodes()).hasSize(4);
        assertThat(workflow.getEdges()).hasSize(3);
        assertThat(WorkflowValidator.validate(workflow).valid()).isTrue();
    }

    @Test
    void copyProducesIndependentImmutableWorkflow() {
        WorkflowDefinition original = WorkflowBuilder.create("Stock Workflow")
                .id("stock-analysis")
                .version("1.0.0")
                .capability(ValidationTestFixtures.minimalCapability())
                .inputNode("request")
                .modelNode("analysis", "CHAT")
                .outputNode("response")
                .connect("request", "analysis")
                .connect("analysis", "response")
                .build();

        WorkflowDefinition copied = original.copy();
        WorkflowDefinition copiedViaStatic = WorkflowDefinition.copyOf(original);

        assertThat(copied).isEqualTo(original).isNotSameAs(original);
        assertThat(copiedViaStatic).isEqualTo(original).isNotSameAs(original);
        assertThat(copied.getRole()).isEqualTo(original.getRole());

        WorkflowDefinition modified = original.toBuilder().id("stock-analysis-v2").build();
        assertThat(original.getId()).isEqualTo("stock-analysis");
        assertThat(modified.getId()).isEqualTo("stock-analysis-v2");
        assertThat(modified.getNodes()).isEqualTo(original.getNodes());
    }

    @Test
    void extendsExistingWorkflow() {
        WorkflowDefinition base = WorkflowBuilder.create("Analysis")
                .id("analysis")
                .capability(ValidationTestFixtures.minimalCapability())
                .inputNode("input")
                .modelNode("llm1", "CHAT")
                .outputNode("output")
                .connect("input", "llm1")
                .connect("llm1", "output")
                .build();

        NodeDefinition ragNode = NodeDefinition.builder()
                .id("rag1")
                .type(NodeType.VECTOR_SEARCH)
                .build();

        WorkflowDefinition enhanced = WorkflowBuilder.from(base)
                .addNode(ragNode)
                .connect("input", "rag1")
                .connect("rag1", "llm1")
                .build();

        assertThat(enhanced.getNodes()).hasSize(4);
        assertThat(enhanced.getEdges()).hasSize(4);
        assertThat(enhanced.getId()).isEqualTo("analysis");
    }

    @Test
    void buildsHumanApprovalTradeWorkflow() {
        WorkflowDefinition workflow = WorkflowBuilder.create("Trade Approval")
                .id("human-approval-trade")
                .capability(ValidationTestFixtures.minimalCapability())
                .inputNode("input")
                .modelNode("recommendation", "CHAT")
                .humanNode(
                        "trade-approval",
                        HumanApprovalDefinition.builder()
                                .title("Approve trade execution?")
                                .approvers(List.of("trading-desk"))
                                .build())
                .toolNode("execute-trade")
                .outputNode("output")
                .connect("input", "recommendation")
                .connect("recommendation", "trade-approval")
                .connect("trade-approval", "execute-trade")
                .connect("execute-trade", "output")
                .build();

        assertThat(workflow.getNodes()).anyMatch(
                n -> "trade-approval".equals(n.getId()) && NodeType.HUMAN.value().equals(n.getType()));
        assertThat(WorkflowValidator.validate(workflow).valid()).isTrue();
    }

    @Test
    void buildsAgentWorkflow() {
        WorkflowDefinition workflow = WorkflowBuilder.create("Agent Handoff")
                .id("agent-handoff")
                .capability(ValidationTestFixtures.minimalCapability())
                .inputNode("input")
                .agentNode(
                        "support-agent",
                        "HANDOFF",
                        WorkflowReferenceDefinition.builder().workflowId("support-agent").build())
                .outputNode("output")
                .connect("input", "support-agent")
                .connect("support-agent", "output")
                .build();

        assertThat(workflow.getNodes()).anyMatch(
                n -> "support-agent".equals(n.getId()) && NodeType.AGENT.value().equals(n.getType()));
        assertThat(workflow.getNodes().stream()
                        .filter(n -> "support-agent".equals(n.getId()))
                        .findFirst()
                        .orElseThrow()
                        .getSubtype())
                .isEqualTo("HANDOFF");
        assertThat(WorkflowValidator.validate(workflow).valid()).isTrue();
    }

    @Test
    void rejectsDuplicateNodeIds() {
        assertThatThrownBy(() -> WorkflowBuilder.create("Dup")
                .inputNode("same")
                .addNode(NodeDefinition.builder().id("same").type(NodeType.TOOL).build())
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate node id");
    }
}
