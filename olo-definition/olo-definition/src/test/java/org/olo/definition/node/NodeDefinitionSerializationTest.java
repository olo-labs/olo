package org.olo.definition.node;

import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.definition.validation.ValidationTestFixtures;
import org.olo.definition.validation.WorkflowValidator;
import org.olo.definition.workflow.WorkflowBuilder;
import org.olo.definition.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NodeDefinitionSerializationTest {

    private final JsonWorkflowSerializer json = new JsonWorkflowSerializer();

    @Test
    void roundTripsVersionAndRoutersOnNode() throws Exception {
        WorkflowDefinition workflow = WorkflowBuilder.create("Router Test")
                .id("router-test")
                .capability(ValidationTestFixtures.minimalCapability())
                .inputNode("input")
                .addNode(ValidationTestFixtures.node("router", NodeType.ROUTER)
                        .version("2.1.0")
                        .addRouter(NodeRouterDefinition.builder()
                                .id("route-a")
                                .targetPort("a")
                                .targetNodeId("output")
                                .providerId("openai-default")
                                .match(java.util.Map.of("priority", 1))
                                .build())
                        .build())
                .outputNode("output")
                .connect("input", "router")
                .connect("router", "output")
                .build();

        WorkflowValidator.validateOrThrow(workflow);

        WorkflowDefinition restored = json.deserialize(json.serialize(workflow));
        assertThat(restored).isEqualTo(workflow);

        NodeDefinition router = restored.getNodes().stream()
                .filter(n -> "router".equals(n.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(router.getVersion()).isEqualTo("2.1.0");
        assertThat(router.getRouters()).hasSize(1);
        assertThat(router.getRouters().get(0).getTargetPort()).isEqualTo("a");
    }
}
