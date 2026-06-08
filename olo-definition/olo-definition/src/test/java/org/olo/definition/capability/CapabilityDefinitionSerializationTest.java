package org.olo.definition.capability;

import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.definition.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityDefinitionSerializationTest {

    private final JsonWorkflowSerializer json = new JsonWorkflowSerializer();

    @Test
    void serializesRequiredInputsAndOutputs() throws Exception {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("cap-serialize")
                .capability(CapabilityDefinition.builder()
                        .name("Example")
                        .description("Example capability")
                        .addRequiredInput("query")
                        .addRequiredOutput("summary")
                        .build())
                .build();

        String serialized = json.serialize(workflow);
        assertThat(serialized).contains("required_inputs");
        assertThat(serialized).contains("required_outputs");
        assertThat(serialized).contains("\"required_inputs\" : [ \"query\" ]");
    }

    @Test
    void deserializesLegacyInputsAlias() throws Exception {
        String yaml = """
                {
                  "id": "legacy-cap",
                  "capability": {
                    "name": "Legacy",
                    "description": "Legacy capability shape",
                    "inputs": ["query"],
                    "outputs": ["summary"]
                  }
                }
                """;

        WorkflowDefinition workflow = json.deserialize(yaml);
        assertThat(workflow.getCapability().getRequiredInputs()).containsExactly("query");
        assertThat(workflow.getCapability().getRequiredOutputs()).containsExactly("summary");
    }
}
