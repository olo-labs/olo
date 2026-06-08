package org.olo.definition.human;

import org.olo.definition.node.NodeDefinition;
import org.olo.definition.node.NodeType;
import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.definition.validation.ValidationTestFixtures;
import org.olo.definition.validation.WorkflowValidator;
import org.olo.definition.workflow.WorkflowBuilder;
import org.olo.definition.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HumanApprovalSerializationTest {

    private final JsonWorkflowSerializer json = new JsonWorkflowSerializer();

    @Test
    void roundTripsHumanApprovalOnNode() throws Exception {
        WorkflowDefinition workflow = WorkflowBuilder.create("HITL")
                .id("hitl")
                .capability(ValidationTestFixtures.minimalCapability())
                .inputNode("input")
                .humanNode(
                        "trade-approval",
                        HumanApprovalDefinition.builder()
                                .title("Approve trade?")
                                .approvers(List.of("trading-desk"))
                                .build())
                .outputNode("output")
                .connect("input", "trade-approval")
                .connect("trade-approval", "output")
                .build();

        WorkflowValidator.validateOrThrow(workflow);

        WorkflowDefinition restored = json.deserialize(json.serialize(workflow));
        NodeDefinition human = restored.getNodes().stream()
                .filter(n -> "trade-approval".equals(n.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(human.getType()).isEqualTo(NodeType.HUMAN.value());
        assertThat(human.getSubtype()).isEqualTo("APPROVAL");
        assertThat(human.getApproval().getTitle()).isEqualTo("Approve trade?");
        assertThat(human.getApproval().getApprovers()).containsExactly("trading-desk");
        assertThat(restored).isEqualTo(workflow);
    }
}
