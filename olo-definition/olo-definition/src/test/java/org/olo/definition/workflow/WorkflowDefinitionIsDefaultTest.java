package org.olo.definition.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.junit.jupiter.api.Test;

class WorkflowDefinitionIsDefaultTest {

    private final JsonWorkflowSerializer json = new JsonWorkflowSerializer();

    @Test
    void serializesAndDeserializesIsDefaultAtRoot() throws Exception {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("fast")
                .enabled(true)
                .isDefault(true)
                .build();

        String serialized = json.serialize(workflow);
        assertThat(serialized).contains("\"isDefault\" : true");

        WorkflowDefinition roundTripped = json.deserialize(serialized);
        assertThat(roundTripped.isDefault()).isTrue();
    }
}
