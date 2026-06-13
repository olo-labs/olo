package org.olo.definition.port;

import org.olo.definition.node.NodeDefinition;
import org.olo.definition.node.NodeType;
import org.olo.definition.port.PortDirection;
import org.olo.definition.port.PortUiDefinition;
import org.olo.definition.port.PortUiPosition;
import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.definition.capability.CapabilityDefinition;
import org.olo.definition.validation.ValidationTestFixtures;
import org.olo.definition.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortDefinitionSerializationTest {

    private final JsonWorkflowSerializer json = new JsonWorkflowSerializer();

    @Test
    void roundTripsPortsOnNode() throws Exception {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("ports")
                .capability(CapabilityDefinition.builder()
                        .name("Ports")
                        .description("Port round-trip test")
                        .addInput("stocks")
                        .addOutput("stockList")
                        .build())
                .addNode(ValidationTestFixtures.node("tool-b", NodeType.TOOL)
                        .addPort(PortDefinition.inputPort("stocks", "Stock[]"))
                        .addPort(PortDefinition.outputPort("stockList", "Stock[]"))
                        .build())
                .build();

        WorkflowDefinition restored = json.deserialize(json.serialize(workflow));
        NodeDefinition tool = restored.getNodes().get(0);

        assertThat(tool.getPorts()).hasSize(4);
        assertThat(tool.getPorts().stream()
                        .filter(port -> port.getDirection() == PortDirection.INPUT)
                        .map(PortDefinition::getId))
                .containsExactlyInAnyOrder("in", "stocks");
        assertThat(tool.getPorts().stream()
                        .filter(port -> port.getDirection() == PortDirection.OUTPUT)
                        .map(PortDefinition::getId))
                .containsExactlyInAnyOrder("out", "stockList");
        assertThat(tool.getPorts().stream()
                        .filter(port -> "stocks".equals(port.getId()))
                        .findFirst())
                .isPresent()
                .get()
                .extracting(PortDefinition::getSchema)
                .isEqualTo("Stock[]");
    }

    @Test
    void roundTripsPortUiPosition() throws Exception {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("port-ui")
                .capability(CapabilityDefinition.builder()
                        .name("Port UI")
                        .description("Port UI round-trip test")
                        .addInput("input")
                        .addOutput("output")
                        .build())
                .addNode(ValidationTestFixtures.node("start", NodeType.START)
                        .addPort(PortDefinition.builder()
                                .id("out")
                                .name("out")
                                .schema("any")
                                .direction(PortDirection.OUTPUT)
                                .ui(PortUiDefinition.builder().position(PortUiPosition.RIGHT).build())
                                .build())
                        .build())
                .build();

        String serialized = json.serialize(workflow);
        assertThat(serialized).contains("\"ui\" : {").contains("\"position\" : \"RIGHT\"");

        WorkflowDefinition restored = json.deserialize(serialized);
        PortDefinition outPort = restored.getNodes().get(0).getPorts().get(0);
        assertThat(outPort.getUi().getPosition()).isEqualTo(PortUiPosition.RIGHT);
    }
}
