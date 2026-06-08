package org.olo.definition.error;

import org.olo.definition.node.NodeDefinition;
import org.olo.definition.node.NodeType;
import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.definition.validation.ValidationTestFixtures;
import org.olo.definition.validation.WorkflowValidator;
import org.olo.definition.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OnFailureSerializationTest {

    private final JsonWorkflowSerializer json = new JsonWorkflowSerializer();

    @Test
    void roundTripsOnFailureOnNode() throws Exception {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("retry-fallback")
                .capability(ValidationTestFixtures.minimalCapability())
                .addNode(ValidationTestFixtures.node("openai", NodeType.MODEL)
                        .onFailure(OnFailureDefinition.builder()
                                .retry(RetryPolicy.builder().attempts(3).build())
                                .route(ErrorRoute.builder().targetNodeId("fallback-model").build())
                                .build())
                        .build())
                .addNode(ValidationTestFixtures.node("fallback-model", NodeType.MODEL).build())
                .build();

        WorkflowValidator.validateOrThrow(workflow);

        WorkflowDefinition restored = json.deserialize(json.serialize(workflow));
        NodeDefinition openai = restored.getNodes().stream()
                .filter(n -> "openai".equals(n.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(openai.getOnFailure()).isNotNull();
        assertThat(openai.getOnFailure().getRetry().getAttempts()).isEqualTo(3);
        assertThat(openai.getOnFailure().getRoute().getTargetNodeId()).isEqualTo("fallback-model");
        assertThat(restored).isEqualTo(workflow);
    }
}
