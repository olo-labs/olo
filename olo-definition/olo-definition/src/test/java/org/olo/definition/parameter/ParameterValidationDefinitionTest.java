package org.olo.definition.parameter;

import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.definition.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParameterValidationDefinitionTest {

    private final JsonWorkflowSerializer json = new JsonWorkflowSerializer();

    @Test
    void deserializesValidationObject() throws Exception {
        String raw = """
                {
                  "id": "agent",
                  "capability": {
                    "name": "Agent",
                    "description": "test"
                  },
                  "parameters": {
                    "model": {
                      "type": "string",
                      "required": true,
                      "validation": {
                        "minLength": 3,
                        "maxLength": 100
                      }
                    }
                  }
                }
                """;

        WorkflowDefinition workflow = json.deserialize(raw);
        WorkflowParameterDefinition model = workflow.getParameters().get("model");
        assertThat(model.getRequired()).isTrue();
        assertThat(model.getValidation().getMinLength()).isEqualTo(3);
        assertThat(model.getValidation().getMaxLength()).isEqualTo(100);
    }

    @Test
    void migratesLegacyNumericBoundsOnDeserialize() throws Exception {
        String raw = """
                {
                  "id": "agent",
                  "capability": {
                    "name": "Agent",
                    "description": "test"
                  },
                  "parameters": {
                    "temperature": {
                      "type": "number",
                      "minimum": 0.0,
                      "maximum": 2.0,
                      "step": 0.1
                    }
                  }
                }
                """;

        WorkflowDefinition workflow = json.deserialize(raw);
        WorkflowParameterDefinition temperature = workflow.getParameters().get("temperature");
        assertThat(temperature.getValidation().getMinimum()).isEqualTo(0.0);
        assertThat(temperature.getValidation().getMaximum()).isEqualTo(2.0);
        assertThat(temperature.getValidation().getStep()).isEqualTo(0.1);
    }
}
